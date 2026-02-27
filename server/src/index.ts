import 'dotenv/config';
import Fastify from 'fastify';
import { openBailianStream } from './bailian.js';
import { appendSessionRound, getSessionSnapshot, writeSessionBSummary } from './db.js';
import { initMySql } from './db/mysql.js';
import {
  consumeOnDone,
  ensureUser,
  getDailyStatus,
  getTierForUser,
  getTodayKeyCN,
  getTopupStatus,
  getUpgradeRemaining,
  parseTier,
  wasProcessed,
} from './quota.js';
import type { ChatStreamRequest, Tier } from './types.js';

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

const app = Fastify({ logger: true });

app.get('/healthz', async () => ({
  ok: true,
  bailian: hasBailianKey() ? 'ok' : 'missing_key',
}));

app.get('/api/me', async (request, reply) => {
  const query = request.query as Record<string, string | undefined>;
  const userId = (query.user_id || '').trim();
  if (!userId) return reply.code(400).send({ error: 'user_id required' });

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

app.post('/api/session/round', async (request, reply) => {
  const body = (request.body || {}) as Record<string, string>;
  const userId = (body.user_id || '').trim();
  const sessionId = (body.session_id || '').trim();
  const tier = parseTier(body.tier);
  const user = (body.user || '').trim();
  const assistant = (body.assistant || '').trim();
  if (!userId || !sessionId || !tier) return reply.code(400).send({ error: 'user_id/session_id/tier required' });
  if (!user || !assistant) return reply.code(400).send({ error: 'user and assistant required' });
  const snapshot = await appendSessionRound(userId, sessionId, { user, assistant }, getAWindowByTier(tier));
  return reply.send(snapshot);
});

app.post('/api/session/b', async (request, reply) => {
  const body = (request.body || {}) as Record<string, string>;
  const userId = (body.user_id || '').trim();
  const sessionId = (body.session_id || '').trim();
  const bSummary = (body.b_summary || '').trim();
  if (!userId || !sessionId) return reply.code(400).send({ error: 'user_id/session_id required' });
  if (!bSummary) return reply.code(400).send({ error: 'b_summary required' });
  await writeSessionBSummary(userId, sessionId, bSummary);
  return reply.send({ ok: true });
});

app.get('/api/session/snapshot', async (request, reply) => {
  const query = request.query as Record<string, string | undefined>;
  const userId = (query.user_id || '').trim();
  const sessionId = (query.session_id || '').trim();
  if (!userId || !sessionId) return reply.code(400).send({ error: 'user_id/session_id required' });
  const snapshot = await getSessionSnapshot(userId, sessionId);
  return reply.send(snapshot ?? { user_id: userId, session_id: sessionId, a_rounds_full: [], b_summary: '', round_total: 0 });
});

app.post('/api/chat/stream', async (request, reply) => {
  const body = (request.body || {}) as Partial<ChatStreamRequest>;
  const userId = (body.user_id || '').trim();
  const clientMsgId = (body.client_msg_id || '').trim();
  const text = (body.text || '').trim();
  const images = Array.isArray(body.images) ? body.images.filter((url) => typeof url === 'string') : [];

  if (!userId) return reply.code(400).send({ error: 'user_id required' });
  if (!clientMsgId) return reply.code(400).send({ error: 'client_msg_id required' });
  if (!text) return reply.code(400).send({ error: 'text required' });
  if (images.length > 4) return reply.code(400).send({ error: 'single request supports up to 4 images' });
  if (images.length > 0 && !text) return reply.code(400).send({ error: 'images require text' });

  await ensureUser(userId, 'free');
  const entitlement = await getTierForUser(userId, 'free');
  const tier = entitlement.tier;
  const dayCN = getTodayKeyCN();

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
      payload: { user_id: userId, tier, client_msg_id: clientMsgId, text, images },
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
