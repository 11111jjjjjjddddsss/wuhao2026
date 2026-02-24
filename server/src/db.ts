import { DatabaseSync } from 'node:sqlite';

let db: DatabaseSync | null = null;

export function getDb(): DatabaseSync {
  if (!db) {
    const sqlitePath = process.env.SQLITE_PATH || './data.db';
    db = new DatabaseSync(sqlitePath);
    db.exec('PRAGMA journal_mode = WAL;');
    db.exec('PRAGMA foreign_keys = ON;');
    initSchema(db);
  }
  return db;
}

function initSchema(database: DatabaseSync): void {
  database.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      created_at INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS quota_daily (
      user_id TEXT NOT NULL,
      yyyymmdd TEXT NOT NULL,
      tier TEXT NOT NULL,
      used INTEGER NOT NULL,
      limit INTEGER NOT NULL,
      PRIMARY KEY(user_id, yyyymmdd)
    );

    CREATE TABLE IF NOT EXISTS quota_ledger (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id TEXT NOT NULL,
      client_msg_id TEXT NOT NULL UNIQUE,
      yyyymmdd TEXT NOT NULL,
      tier TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );

    CREATE INDEX IF NOT EXISTS idx_quota_daily_user_day ON quota_daily(user_id, yyyymmdd);
    CREATE INDEX IF NOT EXISTS idx_quota_ledger_user_day ON quota_ledger(user_id, yyyymmdd);
  `);
}