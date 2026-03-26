import type { FastifyBaseLogger } from 'fastify';
import { hasBailianKeyConfigured, openBailianCompletion } from './bailian.js';
import { setUserSummaryPending, writeUserBSummary, writeUserCSummary } from './db.js';
import { getSummaryPrompt, type SummaryLayer } from './prompt-loader.js';
import type { SessionSnapshot, Tier } from './types.js';

interface SummaryIntervals {
  bEveryRounds: number;
  cEveryRounds: number;
}

function buildDialogueText(rounds: Array<{ user: string; assistant: string }>): string {
  return rounds.map((round) => `user: ${round.user}\nassistant: ${round.assistant}`).join('\n\n').trim();
}

async function extractSummary(layer: SummaryLayer, oldSummary: string, dialogueText: string): Promise<string> {
  const prompt = getSummaryPrompt(layer);
  if (!prompt) throw new Error(`${layer}_PROMPT_MISSING`);
  const userContent = oldSummary
    ? `[历史摘要]\n${oldSummary}\n\n[对话]\n${dialogueText}`
    : `[对话]\n${dialogueText}`;
  const response = await openBailianCompletion({
    body: {
      model: 'qwen-flash',
      stream: false,
      messages: [
        { role: 'system', content: prompt },
        { role: 'user', content: userContent },
      ],
    },
  });
  if (!response.ok) {
    const body = await response.text().catch(() => '');
    throw new Error(`${layer}_EXTRACT_HTTP_${response.status}:${body}`);
  }
  const payload = await response.json().catch(() => null) as Record<string, unknown> | null;
  const choices = Array.isArray(payload?.choices) ? payload.choices : [];
  const choice0 = (choices[0] ?? null) as Record<string, unknown> | null;
  const message = (choice0?.message ?? null) as Record<string, unknown> | null;
  const content = typeof message?.content === 'string' ? message.content.trim() : '';
  if (!content) throw new Error(`${layer}_EXTRACT_EMPTY`);
  return content;
}

async function processLayer(
  layer: SummaryLayer,
  userId: string,
  snapshot: SessionSnapshot,
  logger: FastifyBaseLogger,
): Promise<void> {
  const pending = layer === 'B' ? snapshot.pending_retry_b : snapshot.pending_retry_c;
  if (!pending) return;
  if (!hasBailianKeyConfigured()) {
    logger.warn({ userId, layer }, 'summary extraction skipped: model backend unavailable');
    return;
  }
  const dialogueText = buildDialogueText(snapshot.a_rounds_full);
  if (!dialogueText) {
    logger.warn({ userId, layer }, 'summary extraction skipped: empty dialogue');
    return;
  }
  try {
    const nextSummary = await extractSummary(layer, layer === 'B' ? snapshot.b_summary : snapshot.c_summary, dialogueText);
    if (layer === 'B') {
      await writeUserBSummary(userId, nextSummary);
      snapshot.b_summary = nextSummary;
      snapshot.pending_retry_b = false;
    } else {
      await writeUserCSummary(userId, nextSummary);
      snapshot.c_summary = nextSummary;
      snapshot.pending_retry_c = false;
    }
    logger.info({ userId, layer, chars: nextSummary.length }, 'summary extraction success');
  } catch (error) {
    await setUserSummaryPending(userId, layer, true);
    logger.error({ userId, layer, error }, 'summary extraction failed');
  }
}

export function getSummaryIntervals(tier: Tier): SummaryIntervals {
  return tier === 'pro'
    ? { bEveryRounds: 9, cEveryRounds: 25 }
    : { bEveryRounds: 6, cEveryRounds: 25 };
}

export async function processSessionSummaries(
  userId: string,
  snapshot: SessionSnapshot,
  logger: FastifyBaseLogger,
): Promise<void> {
  await processLayer('B', userId, snapshot, logger);
  await processLayer('C', userId, snapshot, logger);
}
