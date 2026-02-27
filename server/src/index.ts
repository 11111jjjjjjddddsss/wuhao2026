import 'dotenv/config';
import Fastify from 'fastify';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { getClientIp, isAuthStrict, resolveAuthUserId } from './auth.js';
import { openBailianStream } from './bailian.js';
import { appendSessionRoundComplete, getSessionSnapshot, touchSessionContext, writeSessionBSummary } from './db.js';
import { initMySql } from './db/mysql.js';
import { formatShanghaiNowToSecond, parseRegionFromHeaders, resolveRegionByIp } from './geo.js';
import {
  buyTopupPack,
  consumeOnDone,
  ensureUser,
  getDailyStatus,
  getTierForUser,
  getTodayKeyCN,
  getTopupStatus,
  getUpgradeRemaining,
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
const SYSTEM_ANCHOR = process.env.SYSTEM_ANCHOR || '你是高级农业技术顾问，对外称呼“农技千问”，专注解决农业相关问题。';

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

function roundToUserContent(round: SessionRound): string | Array<Record<string, unknown>> {
  const images = Array.isArray(round.user_images) ? round.user_images.filter(Boolean) : [];
  if (images.length === 0) return round.user;
  const text = `${round.user}\n（历史图片数：${images.length}）`;
  return buildVisionUserContent(text, images);
}

function buildPromptMessages(
  snapshot: Awaited<ReturnType<typeof getSessionSnapshot>>,
  aWindowRounds: number,
  currentText: string,
  currentImages: string[],
  contextHeader: string,
): { messages: BailianMessage[]; usedARoundsCount: number; hasBSummary: boolean } {
  const rounds = (snapshot?.a_rounds_full || []).slice(-aWindowRounds);
  const hasBSummary = Boolean(snapshot?.b_summary?.trim());
  const messages: BailianMessage[] = [{ role: 'system', content: SYSTEM_ANCHOR }];
  messages.push({ role: 'system', content: contextHeader });
  if (hasBSummary) {
    messages.push({ role: 'system', content: snapshot!.b_summary.trim() });
  }
  for (const round of rounds) {
    messages.push({ role: 'user', content: roundToUserContent(round) });
    messages.push({ role: 'assistant', content: round.assistant });
  }
  messages.push({ role: 'user', content: buildVisionUserContent(currentText, currentImages) });
  return { messages, usedARoundsCount: rounds.length, hasBSummary };
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
  if (auth.authMode === 'guest') {
    request.log.warn({ masked_ip: auth.maskedIp }, 'auth fallback guest');
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
  const sessionId = (body.session_id || '').trim();
  const bSummary = (body.b_summary || '').trim();
  if (!sessionId) return reply.code(400).send({ error: 'session_id required' });
  if (!bSummary) return reply.code(400).send({ error: 'b_summary required' });
  await writeSessionBSummary(userId, sessionId, bSummary);
  return reply.send({ ok: true });
});

app.get('/api/session/snapshot', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const query = request.query as Record<string, string | undefined>;
  const sessionId = (query.session_id || '').trim();
  if (!sessionId) return reply.code(400).send({ error: 'session_id required' });
  const snapshot = await getSessionSnapshot(userId, sessionId);
  const safe = snapshot ?? { user_id: userId, session_id: sessionId, a_rounds_full: [], b_summary: '', round_total: 0, updated_at: Date.now() };
  return reply.send({
    user_id: safe.user_id,
    session_id: safe.session_id,
    a_json: safe.a_rounds_full,
    b_summary: safe.b_summary,
    round_total: safe.round_total,
    updated_at: safe.updated_at,
  });
});

app.post('/api/session/round_complete', async (request, reply) => {
  const auth = requireAuthOrReply(request, reply);
  if (!auth) return;
  const userId = auth.userId;
  const body = (request.body || {}) as Record<string, unknown>;
  const sessionId = String(body.session_id || '').trim();
  const clientMsgId = String(body.client_msg_id || '').trim();
  const userText = String(body.user_text || '').trim();
  const assistantText = String(body.assistant_text || '').trim();
  const userImages = Array.isArray(body.user_images) ? body.user_images.filter((item): item is string => typeof item === 'string') : [];

  if (!sessionId || !clientMsgId) {
    return reply.code(400).send({ error: 'session_id/client_msg_id required' });
  }
  if (!userText || !assistantText) {
    return reply.code(400).send({ error: 'user_text/assistant_text required' });
  }

  await ensureUser(userId, 'free');
  const entitlement = await getTierForUser(userId, 'free');
  const aWindowRounds = getAWindowByTier(entitlement.tier);

  const result = await appendSessionRoundComplete(
    userId,
    sessionId,
    clientMsgId,
    { user: userText, user_images: userImages, assistant: assistantText },
    aWindowRounds,
  );

  request.log.info(
    {
      userId,
      sessionId,
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
  const sessionId = (body.session_id || '').trim();
  const clientMsgId = (body.client_msg_id || '').trim();
  const text = (body.text || '').trim();
  const images = Array.isArray(body.images) ? body.images.filter((url) => typeof url === 'string') : [];

  if (!sessionId) return reply.code(400).send({ error: 'session_id required' });
  if (!clientMsgId) return reply.code(400).send({ error: 'client_msg_id required' });
  if (images.length > 4) return reply.code(400).send({ error: 'single request supports up to 4 images' });
  if (!text) return reply.code(400).send({ error: 'text required' });
  if (images.length > 0 && text.trim().length === 0) return reply.code(400).send({ error: 'images require text' });

  await ensureUser(userId, 'free');
  const entitlement = await getTierForUser(userId, 'free');
  const tier = entitlement.tier;
  const aWindowRounds = getAWindowByTier(tier);
  const snapshot = await getSessionSnapshot(userId, sessionId);
  const clientIp = getClientIp(request);
  const regionFromHeaders = parseRegionFromHeaders(request.headers as Record<string, unknown>);
  const resolvedRegion = regionFromHeaders ?? resolveRegionByIp(clientIp);
  const injectedTime = formatShanghaiNowToSecond();
  const contextHeader = `当前时间：${injectedTime}（Asia/Shanghai）；用户地点：${resolvedRegion.region}；地点可信度：${resolvedRegion.reliability}`;
  const prompt = buildPromptMessages(snapshot, aWindowRounds, text, images, contextHeader);
  const dayCN = getTodayKeyCN();

  await touchSessionContext(userId, sessionId, resolvedRegion.region, resolvedRegion.source, resolvedRegion.reliability, Date.now());

  request.log.info(
    {
      userId,
      sessionId,
      auth_mode: auth.authMode,
      masked_ip: auth.maskedIp,
      tier,
      used_a_rounds_count: prompt.usedARoundsCount,
      has_b_summary: prompt.hasBSummary,
      injected_time: injectedTime,
      region: resolvedRegion.region,
      region_source: resolvedRegion.source,
      region_reliability: resolvedRegion.reliability,
    },
    'chat prompt assembly',
  );

  if (await wasProcessed(userId, clientMsgId)) {
    return reply.code(200).send({ ok: true, replay: true, client_msg_id: clientMsgId });
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
    upstream = await openBailianStream({
      payload: { user_id: userId, session_id: sessionId, client_msg_id: clientMsgId, text, images },
      messages: prompt.messages,
      signal: abortController.signal,
    });
  } catch (error) {
    request.log.error({ error }, 'upstream request failed');
    return reply.code(502).send({ error: 'upstream request failed' });
  }

  if (!upstream.ok) {
    const errorBody = await upstream.text().catch(() => '');
    request.log.error({ status: upstream.status, errorBody }, 'upstream non-200');
    return reply.code(upstream.status).send({ error: errorBody || 'upstream error' });
  }

  const contentType = upstream.headers.get('content-type') || '';
  if (!contentType.includes('text/event-stream')) {
    const fallbackBody = await upstream.text().catch(() => '');
    request.log.error({ contentType, fallbackBody }, 'upstream is not SSE');
    return reply.code(502).send({ error: 'upstream not SSE' });
  }

  reply.hijack();
  reply.raw.statusCode = 200;
  reply.raw.setHeader('Content-Type', 'text/event-stream; charset=utf-8');
  reply.raw.setHeader('Cache-Control', 'no-cache');
  reply.raw.setHeader('Connection', 'keep-alive');
  reply.raw.flushHeaders?.();

  let clientDisconnected = false;
  let doneReceived = false;

  const onClientClose = () => {
    clientDisconnected = true;
    abortController.abort();
  };
  request.raw.on('close', onClientClose);

  try {
    const reader = upstream.body?.getReader();
    if (!reader) throw new Error('empty upstream body');

    const decoder = new TextDecoder();
    let lineBuffer = '';

    while (!clientDisconnected) {
      const { done, value } = await reader.read();
      if (done) break;

      lineBuffer += decoder.decode(value, { stream: true });
      const lines = lineBuffer.split(/\r?\n/);
      lineBuffer = lines.pop() ?? '';

      for (const rawLine of lines) {
        const line = rawLine.trimEnd();
        if (!line || line.startsWith(':') || line.startsWith('event:') || !line.startsWith('data:')) continue;
        const data = line.slice(5).trimStart();
        if (!reply.raw.write(`data: ${data}\n\n`)) {
          await new Promise<void>((resolve) => reply.raw.once('drain', resolve));
        }
        if (data === '[DONE]') {
          doneReceived = true;
          break;
        }
      }

      if (doneReceived) break;
    }

    if (!clientDisconnected && doneReceived) {
      const consume = await consumeOnDone({ userId, tier, clientMsgId, dayCN });
      request.log.info({ userId, clientMsgId, deducted: consume.deducted, source: consume.source }, 'quota consume on DONE');
    }
  } catch (error) {
    if (!clientDisconnected) {
      request.log.error({ error }, 'sse relay failed');
    }
  } finally {
    request.raw.off('close', onClientClose);
    abortController.abort();
    try {
      reply.raw.end();
    } catch {
      // ignore
    }
  }
});

async function bootstrap() {
  await initMySql();
  const port = Number(process.env.PORT || 3000);
  await app.listen({ port, host: '0.0.0.0' });
}

bootstrap().catch((error) => {
  app.log.error(error);
  process.exit(1);
});
