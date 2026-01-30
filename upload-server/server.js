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
