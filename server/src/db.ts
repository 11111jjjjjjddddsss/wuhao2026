import type { RowDataPacket } from 'mysql2/promise';
import { withConnection } from './db/mysql.js';
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

export async function appendSessionRound(
  userId: string,
  sessionId: string,
  round: SessionRound,
  aWindowRounds: number,
): Promise<SessionSnapshot> {
  return withConnection(async (conn) => {
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
  });
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
  });
}

