import type { PoolConnection, RowDataPacket } from 'mysql2/promise';
import { withConnection } from './db/mysql.js';
import type { ConsumeResult, DailyQuotaStatus, QuotaSource, Tier } from './types.js';

const TIER_LIMITS: Record<Tier, number> = {
  free: 6,
  plus: 20,
  pro: 35,
};

type EntitlementRow = RowDataPacket & {
  tier: Tier;
  tier_expire_at: number | null;
};

type UsageRow = RowDataPacket & {
  used: number;
};

type LedgerRow = RowDataPacket & {
  id: number;
};

type TopupRow = RowDataPacket & {
  pack_id: string;
  remaining: number;
  expire_at: number;
  status: 'active' | 'used_up' | 'expired';
};

type UpgradeRow = RowDataPacket & {
  remaining: number;
  expire_at: number;
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

export async function ensureUser(userId: string, tierHint: Tier): Promise<void> {
  await withConnection(async (conn) => {
    const now = nowTs();
    await conn.execute(
      `INSERT INTO user_entitlement(user_id, tier, tier_expire_at, updated_at)
       VALUES (?, ?, NULL, ?)
       ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at), tier = COALESCE(user_entitlement.tier, VALUES(tier))`,
      [userId, tierHint, now],
    );
  });
}

export async function getTierForUser(userId: string, fallback: Tier): Promise<{ tier: Tier; tier_expire_at: number | null }> {
  return withConnection(async (conn) => {
    const [rows] = await conn.execute<EntitlementRow[]>(
      'SELECT tier, tier_expire_at FROM user_entitlement WHERE user_id = ? LIMIT 1',
      [userId],
    );
    if (rows.length === 0) return { tier: fallback, tier_expire_at: null };
    return { tier: rows[0].tier ?? fallback, tier_expire_at: rows[0].tier_expire_at ?? null };
  });
}

async function getOrCreateDailyUsage(conn: PoolConnection, userId: string, dayCN: string): Promise<number> {
  await conn.execute(
    `INSERT INTO daily_usage(user_id, day_cn, used) VALUES (?, ?, 0)
     ON DUPLICATE KEY UPDATE user_id = user_id`,
    [userId, dayCN],
  );
  const [rows] = await conn.execute<UsageRow[]>(
    'SELECT used FROM daily_usage WHERE user_id = ? AND day_cn = ? LIMIT 1',
    [userId, dayCN],
  );
  return rows[0]?.used ?? 0;
}

export async function getDailyStatus(userId: string, tier: Tier, dayCN = getTodayKeyCN()): Promise<DailyQuotaStatus> {
  return withConnection(async (conn) => {
    const used = await getOrCreateDailyUsage(conn, userId, dayCN);
    const limit = TIER_LIMITS[tier];
    return {
      day_cn: dayCN,
      tier,
      used,
      limit,
      remaining: Math.max(0, limit - used),
    };
  });
}

export async function wasProcessed(userId: string, clientMsgId: string): Promise<boolean> {
  return withConnection(async (conn) => {
    const [rows] = await conn.execute<LedgerRow[]>(
      'SELECT id FROM quota_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1',
      [userId, clientMsgId],
    );
    return rows.length > 0;
  });
}

export async function consumeOnDone(params: {
  userId: string;
  tier: Tier;
  clientMsgId: string;
  dayCN?: string;
}): Promise<ConsumeResult> {
  return withConnection(async (conn) => {
    const dayCN = params.dayCN ?? getTodayKeyCN();
    const tierLimit = TIER_LIMITS[params.tier];
    const now = nowTs();

    await conn.beginTransaction();
    try {
      const [existsRows] = await conn.execute<LedgerRow[]>(
        'SELECT id FROM quota_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE',
        [params.userId, params.clientMsgId],
      );
      if (existsRows.length > 0) {
        await conn.commit();
        const status = await getDailyStatus(params.userId, params.tier, dayCN);
        return { deducted: false, status };
      }

      const used = await getOrCreateDailyUsage(conn, params.userId, dayCN);
      let source: QuotaSource | null = null;

      if (used < tierLimit) {
        await conn.execute(
          'UPDATE daily_usage SET used = used + 1 WHERE user_id = ? AND day_cn = ?',
          [params.userId, dayCN],
        );
        source = 'daily';
      } else {
        const [topupRows] = await conn.execute<TopupRow[]>(
          `SELECT pack_id, remaining, expire_at, status
           FROM topup_packs
           WHERE user_id = ? AND status = 'active' AND remaining > 0 AND expire_at > ?
           ORDER BY expire_at ASC, created_at ASC
           LIMIT 1
           FOR UPDATE`,
          [params.userId, now],
        );
        if (topupRows.length > 0) {
          const pack = topupRows[0];
          await conn.execute(
            `UPDATE topup_packs
             SET remaining = remaining - 1,
                 status = CASE WHEN remaining - 1 <= 0 THEN 'used_up' ELSE status END
             WHERE pack_id = ?`,
            [pack.pack_id],
          );
          source = 'topup';
        } else {
          const [upgradeRows] = await conn.execute<UpgradeRow[]>(
            `SELECT remaining, expire_at
             FROM upgrade_credits
             WHERE user_id = ? AND remaining > 0 AND expire_at > ?
             LIMIT 1
             FOR UPDATE`,
            [params.userId, now],
          );
          if (upgradeRows.length > 0) {
            await conn.execute(
              'UPDATE upgrade_credits SET remaining = remaining - 1, updated_at = ? WHERE user_id = ?',
              [now, params.userId],
            );
            source = 'upgrade';
          }
        }
      }

      if (!source) {
        throw new Error('QUOTA_EXHAUSTED');
      }

      await conn.execute(
        `INSERT INTO quota_ledger(user_id, client_msg_id, day_cn, source, delta, created_at)
         VALUES (?, ?, ?, ?, 1, ?)`,
        [params.userId, params.clientMsgId, dayCN, source, now],
      );
      await conn.commit();
      const status = await getDailyStatus(params.userId, params.tier, dayCN);
      return { deducted: true, source, status };
    } catch (error) {
      await conn.rollback();
      throw error;
    }
  });
}

export async function getTopupStatus(userId: string): Promise<{ remaining: number; earliestExpireAt: number | null }> {
  return withConnection(async (conn) => {
    const now = nowTs();
    const [rows] = await conn.execute<RowDataPacket[]>(
      `SELECT COALESCE(SUM(remaining),0) AS total_remaining, MIN(expire_at) AS earliest_expire_at
       FROM topup_packs
       WHERE user_id = ? AND status = 'active' AND remaining > 0 AND expire_at > ?`,
      [userId, now],
    );
    const first = rows[0] as RowDataPacket | undefined;
    return {
      remaining: Number(first?.total_remaining ?? 0),
      earliestExpireAt: first?.earliest_expire_at == null ? null : Number(first.earliest_expire_at),
    };
  });
}

export async function getUpgradeRemaining(userId: string): Promise<number> {
  return withConnection(async (conn) => {
    const now = nowTs();
    const [rows] = await conn.execute<UpgradeRow[]>(
      'SELECT remaining, expire_at FROM upgrade_credits WHERE user_id = ? LIMIT 1',
      [userId],
    );
    if (rows.length === 0) return 0;
    if (rows[0].expire_at <= now) return 0;
    return Math.max(0, rows[0].remaining);
  });
}

