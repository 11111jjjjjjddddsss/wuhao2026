import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';

const MAX_FILE_SIZE = 10 * 1024 * 1024;
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const EXT_MAP: Record<string, string> = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp',
};

function resolvePublicBaseUrl(request: FastifyRequest): string {
  const configured = (process.env.BASE_PUBLIC_URL || process.env.UPLOAD_BASE_URL || '').trim().replace(/\/$/, '');
  if (configured.startsWith('http://') || configured.startsWith('https://')) {
    return configured;
  }

  const host = String(request.headers['x-forwarded-host'] || request.headers.host || '').trim();
  const proto = String(request.headers['x-forwarded-proto'] || request.protocol || '').trim();
  if (!host || !proto) return '';
  return `${proto}://${host}`.replace(/\/$/, '');
}

export async function registerUploadRoutes(app: FastifyInstance, rootDir: string) {
  const uploadsDir = path.resolve(rootDir, '../uploads');
  fs.mkdirSync(uploadsDir, { recursive: true });

  await app.register(multipart, {
    limits: {
      files: 1,
      fileSize: MAX_FILE_SIZE,
    },
  });

  await app.register(fastifyStatic, {
    root: uploadsDir,
    prefix: '/uploads/',
    index: false,
    decorateReply: false,
  });

  app.post('/upload', async (request, reply) => {
    const file = await request.file();
    if (!file) {
      return reply.code(400).send({ error: 'missing file field' });
    }

    if (!ALLOWED_TYPES.has(file.mimetype)) {
      await file.toBuffer().catch(() => null);
      return reply.code(400).send({ error: 'only jpeg/png/webp allowed' });
    }

    const publicBaseUrl = resolvePublicBaseUrl(request);
    if (!publicBaseUrl.startsWith('https://')) {
      await file.toBuffer().catch(() => null);
      return reply.code(503).send({ error: 'BASE_PUBLIC_URL must be configured as public https url' });
    }

    const extension = EXT_MAP[file.mimetype] || '.jpg';
    const filename = `${crypto.randomUUID()}${extension}`;
    const targetPath = path.join(uploadsDir, filename);

    try {
      await fs.promises.writeFile(targetPath, await file.toBuffer());
      return reply.send({ url: `${publicBaseUrl}/uploads/${filename}` });
    } catch (error) {
      request.log.error({ error }, 'upload write failed');
      return reply.code(500).send({ error: 'upload failed' });
    }
  });
}
