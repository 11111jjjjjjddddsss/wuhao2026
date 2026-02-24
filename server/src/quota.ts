import { getDb } from './db.js';
import type { DailyQuotaStatus, Tier } from './types.js';

const TIER_LIMITS: Record<Tier, number> = {
  free: 6,
  plus: 20,
  pro: 35,
};

function nowTs(): number {
  return Date.now();
}

export function getTodayKeyCN(date = new Date()): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(date).replaceAll('-', '');
}

export function parseTier(input: unknown): Tier | null {
  if (typeof input !== 'string') return null;
  const tier = input.toLowerCase().trim();
  if (tier === 'free' || tier === 'plus' || tier === 'pro') return tier;
  return null;
}

export function getTierLimit(tier: Tier): number {
  return TIER_LIMITS[tier];
}

export function ensureUser(userId: string): void {
  const db = getDb();
  db.prepare(`INSERT INTO users(id, created_at) VALUES (?, ?) ON CONFLICT(id) DO NOTHING`).run(userId, nowTs());
}

export function wasProcessed(clientMsgId: string): boolean {
  const db = getDb();
  const row = db.prepare(`SELECT 1 as hit FROM quota_ledger WHERE client_msg_id = ? LIMIT 1`).get(clientMsgId) as { hit?: number } | undefined;
  return Boolean(row?.hit);
}

export function getDailyStatus(userId: string, tier: Tier, yyyymmdd = getTodayKeyCN()): DailyQuotaStatus {
  const db = getDb();
  const limit = getTierLimit(tier);
  const row = db
    .prepare(`SELECT used, limit FROM quota_daily WHERE user_id = ? AND yyyymmdd = ? LIMIT 1`)
    .get(userId, yyyymmdd) as { used: number; limit: number } | undefined;

  const used = row?.used ?? 0;
  const effectiveLimit = row?.limit ?? limit;
  return {
    yyyymmdd,
    tier,
    used,
    limit: effectiveLimit,
    remaining: Math.max(0, effectiveLimit - used),
  };
}

export function consumeOnDone(params: {
  userId: string;
  tier: Tier;
  clientMsgId: string;
  yyyymmdd?: string;
}): { deducted: boolean; status: DailyQuotaStatus } {
  const db = getDb();
  const yyyymmdd = params.yyyymmdd ?? getTodayKeyCN();
  const limit = getTierLimit(params.tier);

  try {
    db.exec('BEGIN IMMEDIATE');

    const exists = db
      .prepare(`SELECT 1 as hit FROM quota_ledger WHERE client_msg_id = ? LIMIT 1`)
      .get(params.clientMsgId) as { hit?: number } | undefined;

    if (exists?.hit) {
      db.exec('COMMIT');
      const current = getDailyStatus(params.userId, params.tier, yyyymmdd);
      return { deducted: false, status: current };
    }

    db.prepare(
      `INSERT INTO quota_ledger(user_id, client_msg_id, yyyymmdd, tier, created_at) VALUES (?, ?, ?, ?, ?)`
    ).run(params.userId, params.clientMsgId, yyyymmdd, params.tier, nowTs());

    db.prepare(
      `INSERT INTO quota_daily(user_id, yyyymmdd, tier, used, limit)
       VALUES (?, ?, ?, 1, ?)
       ON CONFLICT(user_id, yyyymmdd)
       DO UPDATE SET used = quota_daily.used + 1, tier = excluded.tier, limit = excluded.limit`
    ).run(params.userId, yyyymmdd, params.tier, limit);

    db.exec('COMMIT');
    const updated = getDailyStatus(params.userId, params.tier, yyyymmdd);
    return { deducted: true, status: updated };
  } catch (error) {
    try {
      db.exec('ROLLBACK');
    } catch {
      // ignore
    }
    throw error;
  }
}