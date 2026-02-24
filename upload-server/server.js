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
const TIER_ORDER = { free: 0, plus: 1, pro: 2 };
const TIER_QUOTA = { free: { chat: 6 }, plus: { chat: 20 }, pro: { chat: 35 } };
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
        if (typeof console !== 'undefined' && console.log) {
          console.log('[ENT] expiry_thaw', best.tier, 'paused->active', 'remaining_seconds->end_at');
        }
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
    chat_remaining: quota.chat
  });
});

app.post('/api/subscription/apply', (req, res) => {
  const { user_id, order_id, tier, duration_days } = req.body || {};
  if (!user_id || !order_id || !tier || TIER_ORDER[tier] == null) {
    return res.status(400).json({ error: '缺少 user_id/order_id/tier 或 tier 无效' });
  }
  const list = subscriptionsByUser[user_id] || [];
  if (list.some(s => s.order_id === order_id)) {
    console.log('[ENT] apply idempotent', user_id, order_id, tier);
    return res.status(200).json({ ok: true, idempotent: true });
  }
  const now = Date.now();
  const listCopy = JSON.parse(JSON.stringify(list));
  const ent = computeEntitlement(listCopy, now);
  const effectiveLevel = getTierLevel(ent.effective_tier);
  const reqLevel = getTierLevel(tier);
  if (reqLevel < effectiveLevel) {
    console.log('[ENT] apply no_downgrade', user_id, order_id, tier, 'effective=' + ent.effective_tier);
    return res.status(400).json({ error: 'no_downgrade' });
  }
  const days = Math.max(1, parseInt(duration_days, 10) || 30);
  const end_at = now + days * 86400000;
  if (reqLevel === effectiveLevel && listCopy.some(s => s.status === 'active' && s.tier === tier && s.end_at > now)) {
    const sub = listCopy.find(s => s.status === 'active' && s.tier === tier && s.end_at > now);
    sub.end_at += days * 86400000;
    console.log('[ENT] apply same_tier_renew', user_id, order_id, tier, 'end_at_extended');
  } else {
    listCopy.push({ user_id, tier, start_at: now, end_at, status: 'active', order_id, created_at: now });
    console.log('[ENT] apply upgrade_freeze', user_id, order_id, tier, 'new_sub lower_tiers_paused');
  }
  const list2 = JSON.parse(JSON.stringify(listCopy));
  const entAfter = computeEntitlement(list2, now);
  subscriptionsByUser[user_id] = list2;
  console.log('[ENT] apply result', user_id, order_id, tier, '->', JSON.stringify(entAfter));
  return res.status(200).json({ ok: true });
});

// ---------- 会话两条线：A/B 落库（PolarDB/MySQL），禁止内存为真相；重启后数据不丢 ----------
const A_ROUNDS_UI_MAX = 24;
let dbPool = null;
const dbConfig = {
  host: process.env.DB_HOST || process.env.POLARDB_HOST,
  user: process.env.DB_USER || process.env.POLARDB_USER,
  password: process.env.DB_PASSWORD || process.env.POLARDB_PASSWORD,
  database: process.env.DB_DATABASE || process.env.DB_NAME || process.env.POLARDB_DATABASE,
  waitForConnections: true,
  connectionLimit: 10,
};
if (dbConfig.host && dbConfig.user && dbConfig.database) {
  const mysql = require('mysql2/promise');
  dbPool = mysql.createPool(dbConfig);
  dbPool.getConnection().then(() => {
    console.log('[SESSION] DB connected, session_ab 落库生效');
  }).catch((err) => {
    console.error('[SESSION] DB connect failed', err.message);
    dbPool = null;
  });
} else {
  console.warn('[SESSION] DB_* / POLARDB_* 未配置，session 接口将返回 503');
}

function ensureSessionTable(conn) {
  return conn.execute(
    `CREATE TABLE IF NOT EXISTS session_ab (
      user_id VARCHAR(128) NOT NULL,
      session_id VARCHAR(128) NOT NULL,
      b_summary TEXT NOT NULL DEFAULT '',
      a_rounds_json LONGTEXT NOT NULL DEFAULT '[]',
      updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
      PRIMARY KEY (user_id, session_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`
  ).catch(() => {});
}

app.get('/api/session/snapshot', (req, res) => {
  if (!dbPool) {
    return res.status(503).json({ error: 'session 未配置数据库，请设置 DB_HOST/DB_USER/DB_PASSWORD/DB_DATABASE' });
  }
  const user_id = (req.query.user_id || '').slice(0, 128);
  const session_id = (req.query.session_id || '').slice(0, 128);
  dbPool.getConnection()
    .then(conn => {
      return ensureSessionTable(conn)
        .then(() => conn.execute('SELECT b_summary, a_rounds_json FROM session_ab WHERE user_id = ? AND session_id = ?', [user_id, session_id]))
        .then(([rows]) => {
          conn.release();
          let b_summary = '';
          let a_rounds = [];
          if (Array.isArray(rows) && rows.length > 0) {
            b_summary = rows[0].b_summary != null ? String(rows[0].b_summary) : '';
            try {
              const raw = rows[0].a_rounds_json;
              a_rounds = raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : [];
            } catch (_) { a_rounds = []; }
          }
          if (!Array.isArray(a_rounds)) a_rounds = [];
          const a_rounds_full = a_rounds;
          const a_rounds_for_ui = a_rounds_full.slice(-A_ROUNDS_UI_MAX);
          console.log('[SESSION] GET snapshot', user_id, session_id, 'b_len=' + b_summary.length, 'a_rounds_full=' + a_rounds_full.length, 'a_rounds_for_ui=' + a_rounds_for_ui.length);
          return res.status(200).json({ b_summary, a_rounds_full, a_rounds_for_ui });
        });
    })
    .catch(err => {
      console.error('[SESSION] GET snapshot error', err);
      res.status(500).json({ error: '数据库错误' });
    });
});

app.post('/api/session/append-a', (req, res) => {
  if (!dbPool) {
    return res.status(503).json({ error: 'session 未配置数据库' });
  }
  const { user_id: uid, session_id: sid, user_message, assistant_message } = req.body || {};
  const user_id = (uid || '').slice(0, 128);
  const session_id = (sid || '').slice(0, 128);
  if (!user_id || !session_id) {
    return res.status(400).json({ error: '缺少 user_id 或 session_id' });
  }
  const round = { user: String(user_message || ''), assistant: String(assistant_message || '') };
  dbPool.getConnection()
    .then(conn => {
      return ensureSessionTable(conn)
        .then(() => conn.execute('SELECT a_rounds_json FROM session_ab WHERE user_id = ? AND session_id = ?', [user_id, session_id]))
        .then(([rows]) => {
          let a_rounds = [];
          if (Array.isArray(rows) && rows.length > 0) {
            try {
              const raw = rows[0].a_rounds_json;
              a_rounds = raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : [];
            } catch (_) {}
          }
          if (!Array.isArray(a_rounds)) a_rounds = [];
          a_rounds.push(round);
          const a_rounds_json = JSON.stringify(a_rounds);
          return conn.execute(
            'INSERT INTO session_ab (user_id, session_id, b_summary, a_rounds_json) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE a_rounds_json = ?, updated_at = CURRENT_TIMESTAMP(3)',
            [user_id, session_id, '', a_rounds_json, a_rounds_json]
          ).then(() => {
            conn.release();
            console.log('[SESSION] POST append-a', user_id, session_id, 'a_rounds=' + a_rounds.length);
            return res.status(200).json({ ok: true, a_rounds_count: a_rounds.length });
          });
        });
    })
    .catch(err => {
      console.error('[SESSION] POST append-a error', err);
      res.status(500).json({ error: '数据库错误' });
    });
});

// 仅当 update-b 成功时清空 A；B 失败/超时/写入失败不调此接口，故不清 A（A 为累计队列，不固定轮数）
app.post('/api/session/update-b', (req, res) => {
  if (!dbPool) {
    return res.status(503).json({ error: 'session 未配置数据库' });
  }
  const { user_id: uid, session_id: sid, b_summary: bSum } = req.body || {};
  const user_id = (uid || '').slice(0, 128);
  const session_id = (sid || '').slice(0, 128);
  if (!user_id || !session_id) {
    return res.status(400).json({ error: '缺少 user_id 或 session_id' });
  }
  const b_summary = typeof bSum === 'string' ? bSum : '';
  dbPool.getConnection()
    .then(conn => {
      return ensureSessionTable(conn)
        .then(() => conn.execute(
          'INSERT INTO session_ab (user_id, session_id, b_summary, a_rounds_json) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE b_summary = ?, a_rounds_json = ?',
          [user_id, session_id, b_summary, '[]', b_summary, '[]']
        ))
        .then(() => {
          conn.release();
          console.log('[SESSION] POST update-b', user_id, session_id, 'b_len=' + b_summary.length, 'a_cleared');
          return res.status(200).json({ ok: true });
        });
    })
    .catch(err => {
      console.error('[SESSION] POST update-b error', err);
      res.status(500).json({ error: '数据库错误' });
    });
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
