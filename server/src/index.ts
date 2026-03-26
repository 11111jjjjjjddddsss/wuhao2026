import 'dotenv/config';
import crypto from 'node:crypto';
import Fastify from 'fastify';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { getClientIp, isAuthStrict, resolveAuthUserId } from './auth.js';
import { openBailianStream } from './bailian.js';
import { appendSessionRoundComplete, getSessionSnapshot, touchSessionContext, writeUserBSummary, writeUserCSummary } from './db.js';
import { initMySql } from './db/mysql.js';
import { formatShanghaiNowToSecond, parseRegionFromHeaders, resolveRegionByIp } from './geo.js';
import { getSystemAnchor, getSystemAnchorPath, probeSummaryPrompt } from './prompt-loader.js';
import { getSummaryIntervals, processSessionSummaries } from './summary.js';
import { registerUploadRoutes } from './upload.js';
import {
  buyTopupPack,
  consumeOnDone,
  ensureUser,
  getDailyStatus,
  getTierForUser,
  getTodayKeyCN,
  getTopupStatus,
  getUpgradeRemaining,
  renewPlus,
  renewPro,
  upgradePlusToPro,
  wasProcessed,
} from './quota.js';
import type { BailianMessage, ChatStreamRequest, SessionRound, Tier } from './types.js';

if (!process.env.TZ) {
  process.env.TZ = 'Asia/Shanghai';
}

function hasBailianKey(): boolean {
  const single = (process.env.DASHSCOPE_API_KEY || '').trim();
  const pool = (process.env.DASHSCOPE_API_KEYS || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  return Boolean(single) || pool.length > 0;
}

function getAWindowByTier(tier: Tier): number {
  return tier === 'pro' ? 9 : 6;
}

const app = Fastify({ logger: true, trustProxy: true });
const SSE_HEARTBEAT_MS = 20_000;
const CHAT_RATE_LIMIT_WINDOW_MS = 60_000;
const CHAT_RATE_LIMIT_MAX_REQUESTS = 20;
const UPSTREAM_STREAM_MAX_ATTEMPTS = 2;
const UPSTREAM_STREAM_RETRY_DELAY_MS = 350;
const chatRateLimitBuckets = new Map<string, number[]>();

class UpstreamStreamOpenError extends Error {
  constructor(
    message: string,
    readonly kind: 'request' | 'http' | 'protocol',
    readonly statusCode?: number,
    readonly responseBody?: string,
    readonly contentType?: string,
  ) {
    super(message);
  }
}

function isRetryableUpstreamStatus(status: number): boolean {
  return status === 408 || status === 429 || (status >= 500 && status <= 599);
}

function isAbortLikeError(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError';
}

async function waitForRetryDelay(ms: number, signal?: AbortSignal): Promise<void> {
  if (ms <= 0) return;
  await new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    const onAbort = () => {
      clearTimeout(timer);
      signal?.removeEventListener('abort', onAbort);
      reject(new Error('retry delay aborted'));
    };
    if (signal) {
      signal.addEventListener('abort', onAbort, { once: true });
    }
  });
}

async function openValidatedBailianStreamWithRetry(
  request: FastifyRequest,
  payload: ChatStreamRequest,
  messages: BailianMessage[],
  signal: AbortSignal,
): Promise<Response> {
  let lastError: UpstreamStreamOpenError | null = null;
  for (let attempt = 1; attempt <= UPSTREAM_STREAM_MAX_ATTEMPTS; attempt++) {
    try {
      const upstream = await openBailianStream({ payload, messages, signal });
      if (!upstream.ok) {
        const errorBody = await upstream.text().catch(() => '');
        const error = new UpstreamStreamOpenError(
          `upstream http ${upstream.status}`,
          'http',
          upstream.status,
          errorBody,
        );
        lastError = error;
        if (attempt < UPSTREAM_STREAM_MAX_ATTEMPTS && isRetryableUpstreamStatus(upstream.status) && !signal.aborted) {
          request.log.warn(
            { attempt, maxAttempts: UPSTREAM_STREAM_MAX_ATTEMPTS, status: upstream.status, errorBody },
            'upstream open retry scheduled after non-200 response',
          );
          await waitForRetryDelay(UPSTREAM_STREAM_RETRY_DELAY_MS * attempt, signal);
          continue;
        }
        throw error;
      }

      const contentType = upstream.headers.get('content-type') || '';
      if (!contentType.includes('text/event-stream')) {
        const fallbackBody = await upstream.text().catch(() => '');
        const error = new UpstreamStreamOpenError(
          'upstream not SSE',
          'protocol',
          502,
          fallbackBody,
          contentType,
        );
        lastError = error;
        if (attempt < UPSTREAM_STREAM_MAX_ATTEMPTS && !signal.aborted) {
          request.log.warn(
            { attempt, maxAttempts: UPSTREAM_STREAM_MAX_ATTEMPTS, contentType, fallbackBody },
            'upstream open retry scheduled after non-SSE response',
          );
          await waitForRetryDelay(UPSTREAM_STREAM_RETRY_DELAY_MS * attempt, signal);
          continue;
        }
        throw error;
      }

      if (attempt > 1) {
        request.log.info({ attempt, maxAttempts: UPSTREAM_STREAM_MAX_ATTEMPTS }, 'upstream open recovered after retry');
      }
      return upstream;
    } catch (error) {
      if (error instanceof UpstreamStreamOpenError) {
        throw error;
      }
      if (isAbortLikeError(error) || signal.aborted) {
        throw error;
      }
      const errorMessage = error instanceof Error ? error.message : String(error);
      lastError = new UpstreamStreamOpenError('upstream request failed', 'request', 502, errorMessage);
      if (attempt < UPSTREAM_STREAM_MAX_ATTEMPTS) {
        request.log.warn(
          { attempt, maxAttempts: UPSTREAM_STREAM_MAX_ATTEMPTS, error },
          'upstream open retry scheduled after request failure',
        );
        await waitForRetryDelay(UPSTREAM_STREAM_RETRY_DELAY_MS * attempt, signal);
        continue;
      }
      throw lastError;
    }
  }
  throw lastError ?? new UpstreamStreamOpenError('upstream request failed', 'request', 502);
}

function consumeChatRateLimit(userId: string, now = Date.now()): { allowed: boolean; retryAfterSec: number } {
  const bucket = chatRateLimitBuckets.get(userId) ?? [];
  const validHits = bucket.filter((ts) => now - ts < CHAT_RATE_LIMIT_WINDOW_MS);
  if (validHits.length == 0) {
    chatRateLimitBuckets.delete(userId);
  }
  if (validHits.length >= CHAT_RATE_LIMIT_MAX_REQUESTS) {
    const retryAfterMs = CHAT_RATE_LIMIT_WINDOW_MS - (now - validHits[0]);
    chatRateLimitBuckets.set(userId, validHits);
    return {
      allowed: false,
      retryAfterSec: Math.max(1, Math.ceil(retryAfterMs / 1000)),
    };
  }
  validHits.push(now);
  chatRateLimitBuckets.set(userId, validHits);
  return { allowed: true, retryAfterSec: 0 };
}

const SYSTEM_ANCHOR = getSystemAnchor();
app.log.info({ anchor_source: 'file', anchor_path: getSystemAnchorPath(), anchor_chars: SYSTEM_ANCHOR.length }, 'system anchor loaded');

for (const layer of ['B', 'C'] as const) {
  const result = probeSummaryPrompt(layer);
  if (result.ok) {
    app.log.info({ layer, prompt_path: result.path, prompt_chars: result.chars }, 'summary prompt precheck ok');
  } else {
    app.log.warn({ layer, prompt_path: result.path, error: result.error }, 'summary prompt precheck failed');
  }
}

function buildVisionUserContent(text: string, images: string[]): Array<Record<string, unknown>> {
  const content: Array<Record<string, unknown>> = [{ type: 'text', text }];
  for (const image of images) {
    content.push({
      type: 'image_url',
      image_url: { url: image },
    });
  }
  return content;
}

function roundToUserContent(round: SessionRound, includeImages: boolean): string | Array<Record<string, unknown>> {
  const images = includeImages ? (round.user_images ?? []).filter((url) => typeof url === 'string' && url.trim()) : [];
  return images.length > 0 ? buildVisionUserContent(round.user, images) : round.user;
}

function buildPromptMessages(
  snapshot: Awaited<ReturnType<typeof getSessionSnapshot>>,
  aWindowRounds: number,
  currentText: string,
  currentImages: string[],
  contextHeader: string,
): { messages: BailianMessage[]; usedARoundsCount: number; hasBSummary: boolean; hasCSummary: boolean } {
  const rounds = (snapshot?.a_rounds_full || []).slice(-aWindowRounds);
  const hasBSummary = Boolean(snapshot?.b_summary?.trim());
  const hasCSummary = Boolean(snapshot?.c_summary?.trim());
  const messages: BailianMessage[] = [{ role: 'system', content: SYSTEM_ANCHOR }];
  messages.push({ role: 'system', content: contextHeader });
  if (hasBSummary) {
    messages.push({ role: 'system', content: `【B层累计摘要（仅供参考）】\n${snapshot!.b_summary.trim()}` });
  }
  if (hasCSummary) {
    messages.push({ role: 'system', content: `【C层长期记忆（仅供参考）】\n${snapshot!.c_summary.trim()}` });
  }
  const previousRoundIndex = rounds.length - 1;
  for (const [index, round] of rounds.entries()) {
    messages.push({ role: 'user', content: roundToUserContent(round, index == previousRoundIndex) });
    messages.push({ role: 'assistant', content: round.assistant });
  }
  messages.push({ role: 'user', content: buildVisionUserContent(currentText, currentImages) });
  return { messages, usedARoundsCount: rounds.length, hasBSummary, hasCSummary };
}

app.get('/healthz', async () => ({
  ok: true,
  bailian: hasBailianKey() ? 'ok' : 'missing_key',
  auth_strict: isAuthStrict(),
}));

function requireAuthOrReply(request: FastifyRequest, reply: FastifyReply) {
  const auth = resolveAuthUserId(request);
  if (auth.authMode === 'unauthorized') {
    request.log.warn({ masked_ip: auth.maskedIp }, 'auth unauthorized');
    reply.code(401).send({ error: 'unauthorized' });
    return null;
  }
  return auth;
}

app.get('/api/me', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;

  await ensureUser(userId, 'free');
  const entitlement = await getTierForUser(userId, 'free');
  const status = await getDailyStatus(userId, entitlement.tier, getTodayKeyCN());
  const topup = await getTopupStatus(userId);
  const upgradeRemaining = await getUpgradeRemaining(userId);

  return reply.send({
    tier: entitlement.tier,
    tier_expire_at: entitlement.tier_expire_at,
    daily_remaining: status.remaining,
    topup_remaining: topup.remaining,
    topup_earliest_expire_at: topup.earliestExpireAt,
    upgrade_remaining: upgradeRemaining,
  });
});

app.post('/api/session/b', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, string>;
  const bSummary = (body.b_summary || '').trim();
  if (!bSummary) return reply.code(400).send({ error: 'b_summary required' });
  await writeUserBSummary(userId, bSummary);
  return reply.send({ ok: true });
});

app.post('/api/session/c', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, string>;
  const cSummary = (body.c_summary || '').trim();
  if (!cSummary) return reply.code(400).send({ error: 'c_summary required' });
  await writeUserCSummary(userId, cSummary);
  return reply.send({ ok: true });
});

app.get('/api/session/snapshot', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const snapshot = await getSessionSnapshot(userId);
  const safe = snapshot ?? { user_id: userId, a_rounds_full: [], b_summary: '', c_summary: '', round_total: 0, updated_at: Date.now() };
  return reply.send({
    user_id: safe.user_id,
    a_json: safe.a_rounds_full,
    b_summary: safe.b_summary,
    c_summary: safe.c_summary,
    round_total: safe.round_total,
    updated_at: safe.updated_at,
  });
});

app.post('/api/session/round_complete', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, unknown>;
  const clientMsgId = String(body.client_msg_id || '').trim();
  const userText = String(body.user_text || '').trim();
  const assistantText = String(body.assistant_text || '').trim();
  const userImages: string[] = [];

  if (!clientMsgId) {
    return reply.code(400).send({ error: 'client_msg_id required' });
  }
  if (!userText || !assistantText) {
    return reply.code(400).send({ error: 'user_text/assistant_text required' });
  }

  await ensureUser(userId, 'free');
  const entitlement = await getTierForUser(userId, 'free');
  const aWindowRounds = getAWindowByTier(entitlement.tier);
  const summaryIntervals = getSummaryIntervals(entitlement.tier);

  const result = await appendSessionRoundComplete(
    userId,
    clientMsgId,
    { user: userText, user_images: userImages, assistant: assistantText },
    aWindowRounds,
    summaryIntervals.bEveryRounds,
    summaryIntervals.cEveryRounds,
  );
  void processSessionSummaries(userId, result.snapshot, request.log);

  request.log.info(
    {
      userId,
      clientMsgId,
      replay: result.replay,
      tier: entitlement.tier,
      a_size: result.snapshot.a_rounds_full.length,
      round_total: result.snapshot.round_total,
    },
    'session round_complete',
  );

  return reply.send({
    ok: true,
    replay: result.replay,
    a_json: result.snapshot.a_rounds_full,
    round_total: result.snapshot.round_total,
    updated_at: result.snapshot.updated_at,
  });
});

app.post('/api/topup/buy', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, unknown>;
  const orderId = String(body.order_id || '').trim();
  if (!orderId) return reply.code(400).send({ error: 'order_id required' });
  await ensureUser(userId, 'free');
  try {
    const result = await buyTopupPack(userId, orderId);
    request.log.info({ userId, orderId, replay: result.replay, packId: result.pack_id }, 'topup buy');
    return reply.send({ ok: true, replay: result.replay, pack_id: result.pack_id, expire_at: result.expire_at, remaining: result.remaining });
  } catch (error) {
    const code = error instanceof Error && error.message === 'FORBIDDEN_TIER' ? 403 : error instanceof Error && error.message === 'TOPUP_LIMIT_REACHED' ? 409 : 500;
    const msg = error instanceof Error ? error.message : 'internal_error';
    return reply.code(code).send({ error: msg });
  }
});

app.post('/api/tier/renew_plus', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, unknown>;
  const orderId = String(body.order_id || '').trim();
  if (!orderId) return reply.code(400).send({ error: 'order_id required' });
  await ensureUser(userId, 'free');
  try {
    const result = await renewPlus(userId, orderId);
    request.log.info({ userId, orderId, replay: result.replay, tierExpireAt: result.tier_expire_at }, 'tier renew plus');
    return reply.send({ ok: true, replay: result.replay, tier: result.tier, tier_expire_at: result.tier_expire_at });
  } catch (error) {
    const msg = error instanceof Error ? error.message : 'internal_error';
    const code = msg === 'FORBIDDEN_TIER' ? 403 : 500;
    return reply.code(code).send({ error: msg });
  }
});

app.post('/api/tier/renew_pro', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, unknown>;
  const orderId = String(body.order_id || '').trim();
  if (!orderId) return reply.code(400).send({ error: 'order_id required' });
  await ensureUser(userId, 'free');
  try {
    const result = await renewPro(userId, orderId);
    request.log.info({ userId, orderId, replay: result.replay, tierExpireAt: result.tier_expire_at }, 'tier renew pro');
    return reply.send({ ok: true, replay: result.replay, tier: result.tier, tier_expire_at: result.tier_expire_at });
  } catch (error) {
    const msg = error instanceof Error ? error.message : 'internal_error';
    const code = msg === 'FORBIDDEN_TIER' ? 403 : msg === 'USE_UPGRADE_PLUS_TO_PRO' ? 409 : 500;
    return reply.code(code).send({ error: msg });
  }
});

app.post('/api/tier/upgrade_plus_to_pro', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, unknown>;
  const orderId = String(body.order_id || '').trim();
  if (!orderId) return reply.code(400).send({ error: 'order_id required' });
  await ensureUser(userId, 'free');
  try {
    const result = await upgradePlusToPro(userId, orderId);
    request.log.info({ userId, orderId, replay: result.replay, compensation: result.compensation }, 'tier upgrade plus->pro');
    return reply.send({
      ok: true,
      replay: result.replay,
      compensation: result.compensation,
      tier: result.tier,
      tier_expire_at: result.tier_expire_at,
      upgrade_remaining: result.upgrade_remaining,
    });
  } catch (error) {
    const msg = error instanceof Error ? error.message : 'internal_error';
    const code = msg === 'ALREADY_PRO' ? 409 : msg === 'FORBIDDEN_TIER' ? 403 : 500;
    return reply.code(code).send({ error: msg });
  }
});

app.post('/api/chat/stream', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Partial<ChatStreamRequest>;
  const clientMsgId = (body.client_msg_id || '').trim();
  const text = (body.text || '').trim();
  const images = Array.isArray(body.images) ? body.images.filter((url) => typeof url === 'string') : [];

  if (!clientMsgId) return reply.code(400).send({ error: 'client_msg_id required' });
  if (images.length > 4) return reply.code(400).send({ error: 'single request supports up to 4 images' });
  if (!text) return reply.code(400).send({ error: 'text required' });
  if (images.length > 0 && text.trim().length === 0) return reply.code(400).send({ error: 'images require text' });

  await ensureUser(userId, 'free');
  const entitlement = await getTierForUser(userId, 'free');
  const tier = entitlement.tier;
  const aWindowRounds = getAWindowByTier(tier);
  const summaryIntervals = getSummaryIntervals(tier);
  const snapshot = await getSessionSnapshot(userId);
  const clientIp = getClientIp(request);
  const regionFromHeaders = parseRegionFromHeaders(request.headers as Record<string, unknown>);
  const resolvedRegion = regionFromHeaders ?? resolveRegionByIp(clientIp);
  const injectedTime = formatShanghaiNowToSecond();
  const contextHeader = `当前时间：${injectedTime}（Asia/Shanghai）；用户地点：${resolvedRegion.region}；地点可信度：${resolvedRegion.reliability}`;
  const prompt = buildPromptMessages(snapshot, aWindowRounds, text, images, contextHeader);
  const dayCN = getTodayKeyCN();

  await touchSessionContext(userId, resolvedRegion.region, resolvedRegion.source, resolvedRegion.reliability, Date.now());

  request.log.info(
    {
      userId,
      auth_mode: auth.authMode,
      masked_ip: auth.maskedIp,
      tier,
      used_a_rounds_count: prompt.usedARoundsCount,
      has_b_summary: prompt.hasBSummary,
      has_c_summary: prompt.hasCSummary,
      injected_time: injectedTime,
      region: resolvedRegion.region,
      region_source: resolvedRegion.source,
      region_reliability: resolvedRegion.reliability,
    },
    'chat prompt assembly',
  );

  if (!hasBailianKey()) {
    return reply.code(503).send({
      error: 'MODEL_BACKEND_NOT_CONFIGURED',
      message: '后端未配置大模型服务，当前无法使用真实流式对话',
    });
  }

  if (await wasProcessed(userId, clientMsgId)) {
    reply.hijack();
    reply.raw.statusCode = 200;
    reply.raw.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
    reply.raw.setHeader('Cache-Control', 'no-cache');
    reply.raw.setHeader('Connection', 'keep-alive');
    reply.raw.flushHeaders?.();
    reply.raw.write(`data: ${JSON.stringify({ ok: true, replay: true, client_msg_id: clientMsgId })}\n\n`);
    reply.raw.write('data: [DONE]\n\n');
    reply.raw.end();
    return;
  }

  const rateLimit = consumeChatRateLimit(userId);
  if (!rateLimit.allowed) {
    reply.header('Retry-After', String(rateLimit.retryAfterSec));
    return reply.code(429).send({
      error: 'RATE_LIMITED',
      message: '请求过于频繁，请稍后再试',
      retry_after_sec: rateLimit.retryAfterSec,
    });
  }

  const before = await getDailyStatus(userId, tier, dayCN);
  const topupBefore = await getTopupStatus(userId);
  const upgradeBefore = await getUpgradeRemaining(userId);
  if (before.remaining <= 0 && topupBefore.remaining <= 0 && upgradeBefore <= 0) {
    return reply.code(402).send({ error: '今日次数用完' });
  }

  const abortController = new AbortController();
  let upstream: Response;
  try {
    upstream = await openValidatedBailianStreamWithRetry(
      request,
      { user_id: userId, client_msg_id: clientMsgId, text, images },
      prompt.messages,
      abortController.signal,
    );
  } catch (error) {
    if (error instanceof UpstreamStreamOpenError) {
      if (error.kind === 'http') {
        request.log.error({ status: error.statusCode, errorBody: error.responseBody }, 'upstream non-200 after retry');
        return reply.code(error.statusCode ?? 502).send({ error: error.responseBody || 'upstream error' });
      }
      if (error.kind === 'protocol') {
        request.log.error({ contentType: error.contentType, fallbackBody: error.responseBody }, 'upstream is not SSE after retry');
        return reply.code(502).send({ error: 'upstream not SSE' });
      }
      request.log.error({ error: error.responseBody || error.message }, 'upstream request failed after retry');
      return reply.code(502).send({ error: 'upstream request failed' });
    }
    request.log.error({ error }, 'upstream request failed');
    return reply.code(502).send({ error: 'upstream request failed' });
  }

  reply.hijack();
  reply.raw.statusCode = 200;
  reply.raw.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
  reply.raw.setHeader('Cache-Control', 'no-cache');
  reply.raw.setHeader('Connection', 'keep-alive');
  reply.raw.flushHeaders?.();
  const upstreamRequestId = upstream.headers.get('x-request-id') || upstream.headers.get('request-id') || '';

  let clientDisconnected = false;
  let doneReceived = false;
  let heartbeatTimer: NodeJS.Timeout | null = null;
  let hasCitations = false;
  let hasSources = false;
  let assistantText = '';

  request.log.info(
    {
      request_id: upstreamRequestId,
      enable_search: true,
      strategy: 'turbo',
      forced_search: false,
      single_call_search: true,
    },
    'bailian search config',
  );

  const onClientClose = () => {
    clientDisconnected = true;
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  };
  request.raw.on('close', onClientClose);

  try {
    heartbeatTimer = setInterval(() => {
      if (clientDisconnected || reply.raw.destroyed || reply.raw.writableEnded) return;
      try {
        reply.raw.write(': ping\n\n');
      } catch {
        // ignore heartbeat write errors; normal relay path handles disconnect/abort.
      }
    }, SSE_HEARTBEAT_MS);

    const reader = upstream.body?.getReader();
    if (!reader) throw new Error('empty upstream body');

    const decoder = new TextDecoder();
    let lineBuffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      lineBuffer += decoder.decode(value, { stream: true });
      const lines = lineBuffer.split(/\r?\n/);
      lineBuffer = lines.pop() ?? '';

      for (const rawLine of lines) {
        const line = rawLine.trimEnd();
        if (!line || line.startsWith(':') || line.startsWith('event:') || !line.startsWith('data:')) continue;
        const data = line.slice(5).trimStart();
        if (data !== '[DONE]') {
          try {
            const parsed = JSON.parse(data) as Record<string, unknown>;
            if ('citations' in parsed) hasCitations = true;
            if ('sources' in parsed) hasSources = true;
            const choices = Array.isArray(parsed.choices) ? parsed.choices : [];
            if (choices.length > 0 && typeof choices[0] === 'object' && choices[0] !== null) {
              const first = choices[0] as Record<string, unknown>;
              const delta = typeof first.delta === 'object' && first.delta !== null ? (first.delta as Record<string, unknown>) : null;
              const message = typeof first.message === 'object' && first.message !== null ? (first.message as Record<string, unknown>) : null;
              if (delta && ('citations' in delta || 'sources' in delta)) {
                hasCitations = hasCitations || 'citations' in delta;
                hasSources = hasSources || 'sources' in delta;
              }
              if (message && ('citations' in message || 'sources' in message)) {
                hasCitations = hasCitations || 'citations' in message;
                hasSources = hasSources || 'sources' in message;
              }
              const deltaPiece = typeof delta?.content === 'string' ? delta.content : '';
              const messagePiece = typeof message?.content === 'string' ? message.content : '';
              if (deltaPiece) {
                assistantText += deltaPiece;
              } else if (messagePiece) {
                assistantText = messagePiece.startsWith(assistantText) ? messagePiece : `${assistantText}${messagePiece}`;
              }
            }
          } catch {
            // ignore chunk parse failure; relay should continue.
          }
        }
        if (!clientDisconnected && !reply.raw.destroyed && !reply.raw.writableEnded) {
          if (!reply.raw.write(`data: ${data}\n\n`)) {
            await new Promise<void>((resolve) => reply.raw.once('drain', resolve));
          }
        }
        if (data === '[DONE]') {
          doneReceived = true;
          break;
        }
      }

      if (doneReceived) break;
    }

    if (doneReceived) {
      const consume = await consumeOnDone({ userId, tier, clientMsgId, dayCN });
      request.log.info({ userId, clientMsgId, deducted: consume.deducted, source: consume.source }, 'quota consume on DONE');
      if (assistantText.trim()) {
        const result = await appendSessionRoundComplete(
          userId,
          clientMsgId,
          { user: text, user_images: images, assistant: assistantText.trim() },
          aWindowRounds,
          summaryIntervals.bEveryRounds,
          summaryIntervals.cEveryRounds,
        );
        void processSessionSummaries(userId, result.snapshot, request.log);
      }
    }
  } catch (error) {
    if (!clientDisconnected) {
      request.log.error({ error }, 'sse relay failed');
    } else {
      request.log.warn({ error }, 'background stream continuation failed after client disconnect');
    }
  } finally {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
    request.raw.off('close', onClientClose);
    abortController.abort();
    request.log.info(
      {
        request_id: upstreamRequestId,
        enable_search: true,
        strategy: 'turbo',
        forced_search: false,
        has_citations: hasCitations,
        has_sources: hasSources,
        done_received: doneReceived,
        client_disconnected: clientDisconnected,
      },
      'bailian stream finished',
    );
    if (!clientDisconnected && !reply.raw.destroyed && !reply.raw.writableEnded) {
      try {
        reply.raw.end();
      } catch {
        // ignore
      }
    }
  }
});

async function bootstrap() {
  await registerUploadRoutes(app, __dirname);
  await initMySql();
  const port = Number(process.env.PORT || 3000);
  await app.listen({ port, host: '0.0.0.0' });
}

bootstrap().catch((error) => {
  app.log.error(error);
  process.exit(1);
});
