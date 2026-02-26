import 'dotenv/config';
import Fastify from 'fastify';
import { openBailianStream } from './bailian.js';
import { ensureUser, getDailyStatus, getTodayKeyCN, parseTier, wasProcessed, consumeOnDone } from './quota.js';
import type { ChatStreamRequest } from './types.js';

const app = Fastify({ logger: true });

app.get('/health', async () => ({ ok: true }));

app.get('/api/me', async (request, reply) => {
  const query = request.query as Record<string, string | undefined>;
  const userId = (query.user_id || '').trim();
  const tier = parseTier(query.tier);

  if (!userId) {
    return reply.code(400).send({ error: 'user_id required' });
  }
  if (!tier) {
    return reply.code(400).send({ error: 'tier invalid, expected free|plus|pro' });
  }

  ensureUser(userId);
  const status = getDailyStatus(userId, tier, getTodayKeyCN());
  return reply.send({
    tier,
    yyyymmdd: status.yyyymmdd,
    daily_limit: status.limit,
    daily_used: status.used,
    daily_remaining: status.remaining,
  });
});

app.post('/api/chat/stream', async (request, reply) => {
  const body = (request.body || {}) as Partial<ChatStreamRequest>;
  const userId = (body.user_id || '').trim();
  const tier = parseTier(body.tier);
  const clientMsgId = (body.client_msg_id || '').trim();
  const text = (body.text || '').trim();
  const images = Array.isArray(body.images) ? body.images.filter((url) => typeof url === 'string') : [];

  if (!userId) return reply.code(400).send({ error: 'user_id required' });
  if (!tier) return reply.code(400).send({ error: 'tier invalid, expected free|plus|pro' });
  if (!clientMsgId) return reply.code(400).send({ error: 'client_msg_id required' });
  if (images.length > 4) return reply.code(400).send({ error: 'images up to 4' });
  if (!text) return reply.code(400).send({ error: 'text required' });
  if (images.length > 0 && !text) return reply.code(400).send({ error: 'images require text' });

  ensureUser(userId);

  if (wasProcessed(clientMsgId)) {
    return reply.code(409).send({ error: 'duplicate client_msg_id' });
  }

  const todayKey = getTodayKeyCN();
  const quota = getDailyStatus(userId, tier, todayKey);
  if (quota.used >= quota.limit) {
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

  const requestId =
    upstream.headers.get('x-request-id') ||
    upstream.headers.get('x-dashscope-request-id') ||
    '';

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
  let hasCitation = false;
  let hasSource = false;

  const onClientClose = () => {
    clientDisconnected = true;
    abortController.abort();
  };
  request.raw.on('close', onClientClose);

  try {
    const reader = upstream.body?.getReader();
    if (!reader) {
      throw new Error('empty upstream body');
    }

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
        if (!line) continue;
        if (line.startsWith(':')) continue;
        if (line.startsWith('event:')) continue;
        if (!line.startsWith('data:')) continue;

        const data = line.slice(5).trimStart();
        if (!hasCitation && /\[\d+\]/.test(data)) hasCitation = true;
        if (!hasSource && /"source"|sources|reference|references/i.test(data)) hasSource = true;
        reply.raw.write(`data: ${data}\n\n`);

        if (data === '[DONE]') {
          doneReceived = true;
          break;
        }
      }

      if (doneReceived) break;
    }

    if (!clientDisconnected && doneReceived) {
      consumeOnDone({ userId, tier, clientMsgId, yyyymmdd: todayKey });
    }
    request.log.info(
      {
        request_id: requestId,
        enable_search: true,
        strategy: 'turbo',
        forced_search: false,
        has_citations_or_source: hasCitation || hasSource,
      },
      'search turbo telemetry',
    );
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

const port = Number(process.env.PORT || 3000);
app.listen({ port, host: '0.0.0.0' }).catch((error) => {
  app.log.error(error);
  process.exit(1);
});
