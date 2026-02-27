import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import mysql, { type Pool, type PoolConnection } from 'mysql2/promise';

let pool: Pool | null = null;

function getMysqlUrl(): string {
  const url = (process.env.MYSQL_URL || '').trim();
  if (!url) {
    throw new Error('MYSQL_URL is missing');
  }
  return url;
}

function migrationSql(): string {
  const currentDir = path.dirname(fileURLToPath(import.meta.url));
  const file = path.resolve(currentDir, '../../migrations/001_init.sql');
  return fs.readFileSync(file, 'utf8');
}

export async function getPool(): Promise<Pool> {
  if (pool) return pool;
  pool = mysql.createPool({
    uri: getMysqlUrl(),
    connectionLimit: 10,
    waitForConnections: true,
    queueLimit: 0,
    enableKeepAlive: true,
    timezone: 'Z',
    namedPlaceholders: true,
  });
  return pool;
}

export async function withConnection<T>(fn: (conn: PoolConnection) => Promise<T>): Promise<T> {
  const db = await getPool();
  const conn = await db.getConnection();
  try {
    return await fn(conn);
  } finally {
    conn.release();
  }
}

export async function initMySql(): Promise<void> {
  const sql = migrationSql();
  await withConnection(async (conn) => {
    await conn.query(sql);
  });
}

