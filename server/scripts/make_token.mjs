import crypto from 'node:crypto';

const userId = process.argv[2];
const secret = process.env.APP_SECRET || '';

if (!userId) {
  console.error('usage: node scripts/make_token.mjs <user_id>');
  process.exit(1);
}
if (!secret) {
  console.error('APP_SECRET is required');
  process.exit(1);
}

const ts = Math.floor(Date.now() / 1000).toString();
const signature = crypto.createHmac('sha256', secret).update(`${userId}:${ts}`).digest('hex');
const token = Buffer.from(`${userId}:${ts}:${signature}`, 'utf8').toString('base64');
console.log(token);

