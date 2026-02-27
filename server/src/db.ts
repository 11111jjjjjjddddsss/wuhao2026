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
  round_total: number;
  updated_at: number;
};

type SessionRoundLedgerRow = RowDataPacket & {
  id: number;
};

export async function appendSessionRoundComplete(
  userId: string,
  sessionId: string,
  clientMsgId: string,
  round: SessionRound,
  aWindowRounds: number,
): Promise<{ replay: boolean; snapshot: SessionSnapshot }> {
  return withConnection(async (conn) => {
    await conn.beginTransaction();
    try {
      const [ledgerRows] = await conn.execute<SessionRoundLedgerRow[]>(
        'SELECT id FROM session_round_ledger WHERE user_id = ? AND session_id = ? AND client_msg_id = ? LIMIT 1 FOR UPDATE',
        [userId, sessionId, clientMsgId],
      );
      if (ledgerRows.length > 0) {
        const snapshot = await readSnapshotForUpdate(conn, userId, sessionId);
        await conn.commit();
        return { replay: true, snapshot };
      }

      await conn.execute(
        `INSERT INTO session_round_ledger(user_id, session_id, client_msg_id, created_at)
         VALUES (?, ?, ?, ?)`,
        [userId, sessionId, clientMsgId, nowTs()],
      );

      const snapshot = await appendRoundAndUpsertSnapshot(conn, userId, sessionId, round, aWindowRounds);
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
  sessionId: string,
  round: SessionRound,
  aWindowRounds: number,
): Promise<SessionSnapshot> {
  const [rows] = await conn.execute<SessionRow[]>(
    'SELECT a_json, b_summary, round_total, updated_at FROM session_ab WHERE user_id = ? AND session_id = ? LIMIT 1 FOR UPDATE',
    [userId, sessionId],
  );
  const existing = rows[0];
  const rounds: SessionRound[] = existing?.a_json ? JSON.parse(existing.a_json) : [];
  rounds.push(round);
  while (rounds.length > aWindowRounds) rounds.shift();
  const roundTotal = (existing?.round_total ?? 0) + 1;
  const updatedAt = nowTs();
  const bSummary = existing?.b_summary ?? '';

  await conn.execute(
    `INSERT INTO session_ab(user_id, session_id, a_json, b_summary, round_total, updated_at)
     VALUES (?, ?, ?, ?, ?, ?)
     ON DUPLICATE KEY UPDATE
       a_json = VALUES(a_json),
       round_total = VALUES(round_total),
       updated_at = VALUES(updated_at)`,
    [userId, sessionId, JSON.stringify(rounds), bSummary, roundTotal, updatedAt],
  );

  return {
    user_id: userId,
    session_id: sessionId,
    a_rounds_full: rounds,
    b_summary: bSummary,
    round_total: roundTotal,
    updated_at: updatedAt,
  };
}

export async function writeSessionBSummary(userId: string, sessionId: string, summary: string): Promise<void> {
  const normalized = summary.trim();
  if (!normalized) {
    throw new Error('b_summary empty');
  }
  await withConnection(async (conn) => {
    await conn.execute(
      `INSERT INTO session_ab(user_id, session_id, a_json, b_summary, round_total, updated_at)
       VALUES (?, ?, ?, ?, 0, ?)
       ON DUPLICATE KEY UPDATE b_summary = VALUES(b_summary), updated_at = VALUES(updated_at)`,
      [userId, sessionId, JSON.stringify([]), normalized, nowTs()],
    );
  });
}

export async function getSessionSnapshot(userId: string, sessionId: string): Promise<SessionSnapshot | null> {
  return withConnection(async (conn) => {
    return readSnapshotOptional(conn, userId, sessionId);
  });
}

export async function touchSessionContext(
  userId: string,
  sessionId: string,
  region: string,
  source: RegionSource,
  reliability: RegionReliability,
  seenAt: number,
): Promise<void> {
  await withConnection(async (conn) => {
    await conn.execute(
      `INSERT INTO session_ab(user_id, session_id, a_json, b_summary, round_total, updated_at, last_region, last_region_source, last_region_reliability, last_seen_at)
       VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         last_region = VALUES(last_region),
         last_region_source = VALUES(last_region_source),
         last_region_reliability = VALUES(last_region_reliability),
         last_seen_at = VALUES(last_seen_at),
         updated_at = VALUES(updated_at)`,
      [userId, sessionId, JSON.stringify([]), '', seenAt, region, source, reliability, seenAt],
    );
  });
}

async function readSnapshotOptional(
  conn: PoolConnection,
  userId: string,
  sessionId: string,
): Promise<SessionSnapshot | null> {
  const [rows] = await conn.execute<SessionRow[]>(
    'SELECT a_json, b_summary, round_total, updated_at FROM session_ab WHERE user_id = ? AND session_id = ? LIMIT 1',
    [userId, sessionId],
  );
  if (rows.length === 0) return null;
  const row = rows[0];
  const rounds: SessionRound[] = row.a_json ? JSON.parse(row.a_json) : [];
  return {
    user_id: userId,
    session_id: sessionId,
    a_rounds_full: rounds,
    b_summary: row.b_summary ?? '',
    round_total: row.round_total ?? 0,
    updated_at: row.updated_at ?? 0,
  };
}

async function readSnapshotForUpdate(
  conn: PoolConnection,
  userId: string,
  sessionId: string,
): Promise<SessionSnapshot> {
  const snapshot = await readSnapshotOptional(conn, userId, sessionId);
  if (snapshot) return snapshot;
  const empty: SessionSnapshot = {
    user_id: userId,
    session_id: sessionId,
    a_rounds_full: [],
    b_summary: '',
    round_total: 0,
    updated_at: nowTs(),
  };
  await conn.execute(
    `INSERT INTO session_ab(user_id, session_id, a_json, b_summary, round_total, updated_at)
     VALUES (?, ?, ?, ?, 0, ?)
     ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
    [userId, sessionId, JSON.stringify([]), '', empty.updated_at],
  );
  return empty;
}
