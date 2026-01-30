/**
 * 图片上传后端（冻结协议）
 * POST /upload  multipart/form-data 字段名 file
 * 成功: 200 + {"url":"https://..."}
 * 失败: 非200 + {"error":"..."}
 * 实现：本地存储 + BASE_PUBLIC_URL（推荐 ngrok/部署域名），无需 OSS
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

const UPLOADS_DIR = path.join(__dirname, 'uploads');
if (!fs.existsSync(UPLOADS_DIR)) fs.mkdirSync(UPLOADS_DIR, { recursive: true });

const upload = multer({
  storage: multer.diskStorage({
    destination: (_, __, cb) => cb(null, UPLOADS_DIR),
    filename: (_, file, cb) => cb(null, `${randomUUID()}${path.extname(file.originalname) || '.jpg'}`),
  }),
  limits: { fileSize: 10 * 1024 * 1024 }, // 10MB
});

const BASE_PUBLIC_URL = (process.env.BASE_PUBLIC_URL || process.env.UPLOAD_BASE_URL || '').replace(/\/$/, '');

app.post('/upload', upload.single('file'), (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '缺少 file 字段' });
    }
    if (!BASE_PUBLIC_URL || !BASE_PUBLIC_URL.startsWith('http')) {
      return res.status(503).json({ error: '请设置环境变量 BASE_PUBLIC_URL（公网 https 地址，如 ngrok 或部署域名）' });
    }
    const url = `${BASE_PUBLIC_URL}/uploads/${req.file.filename}`;
    return res.status(200).json({ url });
  } catch (e) {
    return res.status(500).json({ error: String(e.message || '上传失败') });
  }
});

app.use('/uploads', express.static(UPLOADS_DIR));

app.get('/health', (_, res) => res.status(200).send('ok'));

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Upload server: http://0.0.0.0:${PORT}/upload`);
  if (!BASE_PUBLIC_URL) console.log('WARN: 未设置 BASE_PUBLIC_URL，上传将返回 503');
});
