import geoip from 'geoip-lite';

export type RegionSource = 'gps' | 'ip' | 'none';
export type RegionReliability = 'reliable' | 'unreliable';

export interface RegionContext {
  region: string;
  source: RegionSource;
  reliability: RegionReliability;
}

function normalizeRegion(raw: string): string {
  const replaced = raw
    .replace(/[^\p{Script=Han}A-Za-z0-9\s\-\/]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  return replaced.slice(0, 64);
}

export function parseRegionFromHeaders(headers: Record<string, unknown>): RegionContext | null {
  const regionRaw = String(headers['x-user-region'] || '').trim();
  const sourceRaw = String(headers['x-region-source'] || '').trim().toLowerCase();
  const reliabilityRaw = String(headers['x-region-reliability'] || '').trim().toLowerCase();
  if (!regionRaw) return null;

  const source: RegionSource = sourceRaw === 'gps' || sourceRaw === 'ip' || sourceRaw === 'none' ? sourceRaw : 'none';
  let reliability: RegionReliability = reliabilityRaw === 'reliable' ? 'reliable' : 'unreliable';
  if (source === 'gps' && reliability !== 'reliable') reliability = 'unreliable';

  return {
    region: normalizeRegion(regionRaw),
    source,
    reliability,
  };
}

export function resolveRegionByIp(clientIp: string): RegionContext {
  const ip = String(clientIp || '').replace(/^::ffff:/, '');
  const hit = geoip.lookup(ip);
  if (!hit) {
    return { region: '未知', source: 'ip', reliability: 'unreliable' };
  }
  const pieces = [hit.region, hit.city].map((v) => String(v || '').trim()).filter(Boolean);
  const region = pieces.length > 0 ? pieces.join(' ') : '未知';
  return { region, source: 'ip', reliability: 'unreliable' };
}

export function formatShanghaiNowToSecond(date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date);
  const pick = (type: string) => parts.find((p) => p.type === type)?.value ?? '00';
  return `${pick('year')}-${pick('month')}-${pick('day')} ${pick('hour')}:${pick('minute')}:${pick('second')}`;
}
