/**
 * 图片上传后端（冻结协议 + 最低安全）
 * POST /upload  multipart/form-data 字段名 file
 * 成功: 200 + {"url":"https://..."}
 * 失败: 非200 + {"error":"..."}
 * 安全：仅 jpeg/png/webp；单文件 10MB；文件名 uuid；/uploads 禁目录列表
 */
const express = require('express');
const multer = require('multer');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { randomUUID } = require('crypto');

const app = express();
app.use(cors());
app.use(express.json());

const MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
const EXT_MAP = { 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' };

const UPLOADS_DIR = path.join(__dirname, 'uploads');
if (!fs.existsSync(UPLOADS_DIR)) fs.mkdirSync(UPLOADS_DIR, { recursive: true });

const upload = multer({
  storage: multer.diskStorage({
    destination: (_, __, cb) => cb(null, UPLOADS_DIR),
    filename: (_, file, cb) => {
      const ext = EXT_MAP[file.mimetype] || '.jpg';
      cb(null, `${randomUUID()}${ext}`);
    },
  }),
  limits: { fileSize: MAX_FILE_SIZE },
  fileFilter: (_, file, cb) => {
    if (ALLOWED_TYPES.includes(file.mimetype)) return cb(null, true);
    cb(new Error('仅允许 jpeg/png/webp'));
  },
});

const BASE_PUBLIC_URL = (process.env.BASE_PUBLIC_URL || process.env.UPLOAD_BASE_URL || '').replace(/\/$/, '');

app.post('/upload', (req, res, next) => {
  upload.single('file')(req, res, (err) => {
    if (err) return next(err);
    next();
  });
}, (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '缺少 file 字段' });
    }
    if (!BASE_PUBLIC_URL || !BASE_PUBLIC_URL.startsWith('http')) {
      return res.status(503).json({ error: '请设置环境变量 BASE_PUBLIC_URL（公网 https 地址）' });
    }
    const url = `${BASE_PUBLIC_URL}/uploads/${req.file.filename}`;
    return res.status(200).json({ url });
  } catch (e) {
    return res.status(500).json({ error: String(e.message || '上传失败') });
  }
});

app.use('/uploads', express.static(UPLOADS_DIR, { index: false }));

// ---------- 会员 entitlement（与前端规则一致；后端为唯一真相时可替换前端 computeEntitlement） ----------
const TIER_ORDER = { free: 0, plus: 1, pro: 2, expert: 3 };
const TIER_QUOTA = { free: { chat: 10, img: 1 }, plus: { chat: 30, img: 5 }, pro: { chat: 60, img: 10 }, expert: { chat: 80, img: 20 } };
function getTierLevel(tier) { return TIER_ORDER[tier] != null ? TIER_ORDER[tier] : -1; }
const subscriptionsByUser = {}; // user_id -> [ { user_id, tier, start_at, end_at, status, pause_at, remaining_seconds, order_id, created_at } ]

function computeEntitlement(list, now) {
  now = now || Date.now();
  const subs = list.filter(s => (s.status === 'active' || s.status === 'paused') && (s.end_at > now || (s.remaining_seconds && s.remaining_seconds > 0)));
  for (const s of subs) {
    if (s.status === 'active' && s.end_at <= now) {
      s.status = 'expired';
      const paused = subs.filter(x => x.status === 'paused' && (x.remaining_seconds > 0 || (x.end_at && x.end_at > now)));
      const best = paused.length ? paused.reduce((a, b) => getTierLevel(b.tier) > getTierLevel(a.tier) ? b : a) : null;
      if (best) {
        best.status = 'active';
        best.resume_at = now;
        best.end_at = now + (best.remaining_seconds || 0) * 1000;
        best.remaining_seconds = 0;
      }
    }
  }
  const activeList = subs.filter(s => s.status === 'active' && s.end_at > now);
  const effective = activeList.length ? activeList.reduce((a, b) => getTierLevel(b.tier) > getTierLevel(a.tier) ? b : a) : null;
  for (const s of subs) {
    if (s.status !== 'active' && s.status !== 'paused') continue;
    if (effective && getTierLevel(s.tier) < getTierLevel(effective.tier)) {
      if (s.status !== 'paused') {
        s.status = 'paused';
        s.pause_at = now;
        s.remaining_seconds = Math.max(0, Math.floor((s.end_at - now) / 1000));
      }
    }
  }
  const pausedList = subs.filter(s => s.status === 'paused');
  const effectiveTier = effective ? effective.tier : 'free';
  const effectiveEndAt = effective ? effective.end_at : 0;
  const paused_list = pausedList.map(p => ({
    tier: p.tier,
    remaining_days: Math.ceil((p.remaining_seconds != null ? p.remaining_seconds : Math.max(0, (p.end_at - now) / 1000)) / 86400)
  }));
  return { effective_tier: effectiveTier, effective_end_at: effectiveEndAt, paused_list };
}

app.get('/api/entitlement', (req, res) => {
  const user_id = req.query.user_id || '';
  const list = subscriptionsByUser[user_id] || [];
  const now = Date.now();
  const ent = computeEntitlement(JSON.parse(JSON.stringify(list)), now);
  const quota = TIER_QUOTA[ent.effective_tier] || TIER_QUOTA.free;
  return res.status(200).json({
    effective_tier: ent.effective_tier,
    effective_end_at: ent.effective_end_at,
    paused_list: ent.paused_list,
    chat_remaining: quota.chat,
    img_remaining: quota.img
  });
});

app.post('/api/subscription/apply', (req, res) => {
  const { user_id, order_id, tier, duration_days } = req.body || {};
  if (!user_id || !order_id || !tier || TIER_ORDER[tier] == null) {
    return res.status(400).json({ error: '缺少 user_id/order_id/tier 或 tier 无效' });
  }
  const list = subscriptionsByUser[user_id] || [];
  if (list.some(s => s.order_id === order_id)) {
    return res.status(200).json({ ok: true, idempotent: true });
  }
  const now = Date.now();
  const listCopy = JSON.parse(JSON.stringify(list));
  const ent = computeEntitlement(listCopy, now);
  const effectiveLevel = getTierLevel(ent.effective_tier);
  const reqLevel = getTierLevel(tier);
  if (reqLevel < effectiveLevel) {
    return res.status(400).json({ error: 'no_downgrade' });
  }
  const days = Math.max(1, parseInt(duration_days, 10) || 30);
  const end_at = now + days * 86400000;
  if (reqLevel === effectiveLevel && listCopy.some(s => s.status === 'active' && s.tier === tier && s.end_at > now)) {
    const sub = listCopy.find(s => s.status === 'active' && s.tier === tier && s.end_at > now);
    sub.end_at += days * 86400000;
  } else {
    listCopy.push({ user_id, tier, start_at: now, end_at, status: 'active', order_id, created_at: now });
  }
  const list2 = JSON.parse(JSON.stringify(listCopy));
  const entAfter = computeEntitlement(list2, now);
  subscriptionsByUser[user_id] = list2;
  console.log('[ENT] apply', user_id, order_id, tier, '->', JSON.stringify(entAfter));
  return res.status(200).json({ ok: true });
});

app.get('/health', (_, res) => res.status(200).send('ok'));

app.use((err, req, res, next) => {
  if (err instanceof multer.MulterError && err.code === 'LIMIT_FILE_SIZE') {
    return res.status(413).json({ error: '单文件不超过 10MB' });
  }
  if (err.message && err.message.includes('jpeg/png/webp')) {
    return res.status(400).json({ error: '仅允许 jpeg/png/webp' });
  }
  return res.status(500).json({ error: String(err.message || '上传失败') });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Upload server: http://0.0.0.0:${PORT}/upload`);
  if (!BASE_PUBLIC_URL) console.log('WARN: 未设置 BASE_PUBLIC_URL，上传将返回 503');
});
