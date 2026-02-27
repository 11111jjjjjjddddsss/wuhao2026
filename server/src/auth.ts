import crypto from 'node:crypto';
import type { FastifyRequest } from 'fastify';

function isPrivateIpv4(ip: string): boolean {
  if (!/^\d+\.\d+\.\d+\.\d+$/.test(ip)) return false;
  const parts = ip.split('.').map((v) => Number(v));
  if (parts[0] === 10) return true;
  if (parts[0] === 127) return true;
  if (parts[0] === 192 && parts[1] === 168) return true;
  if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) return true;
  return false;
}

export function getClientIp(request: FastifyRequest): string {
  const xff = String(request.headers['x-forwarded-for'] || '')
    .split(',')
    .map((v) => v.trim().replace(/^::ffff:/, ''))
    .filter(Boolean);
  const publicIp = xff.find((ip) => !isPrivateIpv4(ip));
  if (publicIp) return publicIp;
  const first = xff[0];
  if (first) return first;
  return String(request.ip || '').replace(/^::ffff:/, '');
}

export function maskIp(ip: string): string {
  if (/^\d+\.\d+\.\d+\.\d+$/.test(ip)) {
    const p = ip.split('.');
    return `${p[0]}.${p[1]}.*.*`;
  }
  if (ip.includes(':')) {
    return ip.split(':').slice(0, 2).join(':') + ':*';
  }
  return 'unknown';
}

function verifyToken(token: string, appSecret: string): { ok: boolean; userId?: string } {
  try {
    const decoded = Buffer.from(token, 'base64').toString('utf8');
    const [userId, tsRaw, sig] = decoded.split(':');
    if (!userId || !tsRaw || !sig) return { ok: false };
    const ts = Number(tsRaw);
    if (!Number.isFinite(ts)) return { ok: false };
    const nowSec = Math.floor(Date.now() / 1000);
    const skew = Math.abs(nowSec - ts);
    if (skew > 7 * 24 * 60 * 60) return { ok: false };
    const expect = crypto.createHmac('sha256', appSecret).update(`${userId}:${tsRaw}`).digest('hex');
    if (expect !== sig) return { ok: false };
    return { ok: true, userId };
  } catch {
    return { ok: false };
  }
}

export function resolveAuthUserId(request: FastifyRequest): {
  userId: string;
  authMode: 'token' | 'guest';
  maskedIp: string;
} {
  const ip = getClientIp(request);
  const maskedIp = maskIp(ip);
  const authHeader = String(request.headers.authorization || '');
  const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : '';
  const secret = (process.env.APP_SECRET || '').trim();
  if (token && secret) {
    const verified = verifyToken(token, secret);
    if (verified.ok && verified.userId) {
      return { userId: verified.userId, authMode: 'token', maskedIp };
    }
  }
  return { userId: `guest:${ip || 'unknown'}`, authMode: 'guest', maskedIp };
}

