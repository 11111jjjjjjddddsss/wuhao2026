import type { PoolConnection, RowDataPacket } from 'mysql2/promise';
import { withConnection } from './db/mysql.js';
import type { RegionReliability, RegionSource } from './geo.js';
import type { SessionRound, SessionSnapshot } from './types.js';

function nowTs(): number {
  return Date.now();
}

type SessionRow = RowDataPacket & {
  a_json: string | null;
  b_summary: string | null;
  c_summary: string | null;
  pending_retry_b: number | null;
  pending_retry_c: number | null;
  round_total: number;
  updated_at: number;
};

type SessionRoundLedgerRow = RowDataPacket & {
  id: number;
};

export async function appendSessionRoundComplete(
  userId: string,
  clientMsgId: string,
  round: SessionRound,
  aWindowRounds: number,
  bEveryRounds: number,
  cEveryRounds: number,
): Promise<{ replay: boolean; snapshot: SessionSnapshot }> {
  return withConnection(async (conn) => {
    await conn.beginTransaction();
    try {
      const [ledgerRows] = await conn.execute<SessionRoundLedgerRow[]>(
        'SELECT id FROM session_round_ledger WHERE user_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE',
        [userId, clientMsgId],
      );
      if (ledgerRows.length > 0) {
        const snapshot = await readSnapshotForUpdate(conn, userId);
        await conn.commit();
        return { replay: true, snapshot };
      }

      await conn.execute(
        `INSERT INTO session_round_ledger(user_id, client_msg_id, created_at)
         VALUES (?, ?, ?)`,
        [userId, clientMsgId, nowTs()],
      );

      const snapshot = await appendRoundAndUpsertSnapshot(conn, userId, round, aWindowRounds, bEveryRounds, cEveryRounds);
      await conn.commit();
      return { replay: false, snapshot };
    } catch (error) {
      await conn.rollback();
      throw error;
    }
  });
}

async function appendRoundAndUpsertSnapshot(
  conn: PoolConnection,
  userId: string,
  round: SessionRound,
  aWindowRounds: number,
  bEveryRounds: number,
  cEveryRounds: number,
): Promise<SessionSnapshot> {
  const [rows] = await conn.execute<SessionRow[]>(
    'SELECT a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1 FOR UPDATE',
    [userId],
  );
  const existing = rows[0];
  const rounds: SessionRound[] = existing?.a_json ? JSON.parse(existing.a_json) : [];
  rounds.push(round);
  while (rounds.length > aWindowRounds) rounds.shift();
  const roundTotal = (existing?.round_total ?? 0) + 1;
  const updatedAt = nowTs();
  const bSummary = existing?.b_summary ?? '';
  const cSummary = existing?.c_summary ?? '';
  const pendingRetryB = Boolean(existing?.pending_retry_b) || (bEveryRounds > 0 && roundTotal % bEveryRounds === 0);
  const pendingRetryC = Boolean(existing?.pending_retry_c) || (cEveryRounds > 0 && roundTotal % cEveryRounds === 0);

  await conn.execute(
    `INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
       a_json = VALUES(a_json),
       pending_retry_b = VALUES(pending_retry_b),
       pending_retry_c = VALUES(pending_retry_c),
       round_total = VALUES(round_total),
       updated_at = VALUES(updated_at)`,
    [userId, JSON.stringify(rounds), bSummary, cSummary, pendingRetryB ? 1 : 0, pendingRetryC ? 1 : 0, roundTotal, updatedAt],
  );

  return {
    user_id: userId,
    a_rounds_full: rounds,
    b_summary: bSummary,
    c_summary: cSummary,
    pending_retry_b: pendingRetryB,
    pending_retry_c: pendingRetryC,
    round_total: roundTotal,
    updated_at: updatedAt,
  };
}

export async function writeUserBSummary(userId: string, summary: string): Promise<void> {
  const normalized = summary.trim();
  if (!normalized) {
    throw new Error('b_summary empty');
  }
  await withConnection(async (conn) => {
    await conn.execute(
      `INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, round_total, updated_at)
       VALUES (?, ?, ?, ?, 0, ?)
       ON DUPLICATE KEY UPDATE b_summary = VALUES(b_summary), pending_retry_b = 0, updated_at = VALUES(updated_at)`,
      [userId, JSON.stringify([]), normalized, '', nowTs()],
    );
  });
}

export async function writeUserCSummary(userId: string, summary: string): Promise<void> {
  const normalized = summary.trim();
  if (!normalized) {
    throw new Error('c_summary empty');
  }
  await withConnection(async (conn) => {
    await conn.execute(
      `INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, round_total, updated_at)
       VALUES (?, ?, ?, ?, 0, ?)
       ON DUPLICATE KEY UPDATE c_summary = VALUES(c_summary), pending_retry_c = 0, updated_at = VALUES(updated_at)`,
      [userId, JSON.stringify([]), '', normalized, nowTs()],
    );
  });
}

export async function setUserSummaryPending(userId: string, layer: 'B' | 'C', pending: boolean): Promise<void> {
  const pendingRetryB = layer === 'B' && pending ? 1 : 0;
  const pendingRetryC = layer === 'C' && pending ? 1 : 0;
  const column = layer === 'B' ? 'pending_retry_b' : 'pending_retry_c';
  await withConnection(async (conn) => {
    await conn.execute(
      `INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, 0, ?)
       ON DUPLICATE KEY UPDATE ${column} = VALUES(${column}), updated_at = VALUES(updated_at)`,
      [userId, JSON.stringify([]), '', '', pendingRetryB, pendingRetryC, nowTs()],
    );
  });
}

export async function getSessionSnapshot(userId: string): Promise<SessionSnapshot | null> {
  return withConnection(async (conn) => {
    return readSnapshotOptional(conn, userId);
  });
}

export async function touchSessionContext(
  userId: string,
  region: string,
  source: RegionSource,
  reliability: RegionReliability,
  seenAt: number,
): Promise<void> {
  await withConnection(async (conn) => {
    await conn.execute(
      `INSERT INTO session_ab(
         user_id,
         a_json,
         b_summary,
         c_summary,
         round_total,
         updated_at,
         last_region,
         last_region_source,
         last_region_reliability,
         last_seen_at
       )
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         last_region = VALUES(last_region),
         last_region_source = VALUES(last_region_source),
         last_region_reliability = VALUES(last_region_reliability),
         last_seen_at = VALUES(last_seen_at),
         updated_at = VALUES(updated_at)`,
      [userId, JSON.stringify([]), '', '', 0, seenAt, region, source, reliability, seenAt],
    );
  });
}

async function readSnapshotOptional(
  conn: PoolConnection,
  userId: string,
): Promise<SessionSnapshot | null> {
  const [rows] = await conn.execute<SessionRow[]>(
    'SELECT a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at FROM session_ab WHERE user_id = ? LIMIT 1',
    [userId],
  );
  if (rows.length === 0) return null;
  const row = rows[0];
  const rounds: SessionRound[] = row.a_json ? JSON.parse(row.a_json) : [];
  return {
    user_id: userId,
    a_rounds_full: rounds,
    b_summary: row.b_summary ?? '',
    c_summary: row.c_summary ?? '',
    pending_retry_b: Boolean(row.pending_retry_b),
    pending_retry_c: Boolean(row.pending_retry_c),
    round_total: row.round_total ?? 0,
    updated_at: row.updated_at ?? 0,
  };
}

async function readSnapshotForUpdate(
  conn: PoolConnection,
  userId: string,
): Promise<SessionSnapshot> {
  const snapshot = await readSnapshotOptional(conn, userId);
  if (snapshot) return snapshot;
  const empty: SessionSnapshot = {
    user_id: userId,
    a_rounds_full: [],
    b_summary: '',
    c_summary: '',
    pending_retry_b: false,
    pending_retry_c: false,
    round_total: 0,
    updated_at: nowTs(),
  };
  await conn.execute(
    `INSERT INTO session_ab(user_id, a_json, b_summary, c_summary, pending_retry_b, pending_retry_c, round_total, updated_at)
     VALUES (?, ?, ?, ?, 0, 0, 0, ?)
     ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
    [userId, JSON.stringify([]), '', '', empty.updated_at],
  );
  return empty;
}
