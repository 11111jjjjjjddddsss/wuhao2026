const https = require('https');

const imageUrl = process.argv[2];
if (!imageUrl || !imageUrl.startsWith('https://')) {
  console.error('Usage: node scripts/verify_model_url.js <IMAGE_URL>');
  process.exit(1);
}

const apiKey = process.env.DASHSCOPE_API_KEY || process.env.API_KEY || '';
if (!apiKey) {
  console.error('Missing DASHSCOPE_API_KEY or API_KEY');
  process.exit(1);
}

const payload = {
  model: 'qwen3-vl-flash',
  stream: false,
  messages: [
    {
      role: 'user',
      content: [
        { type: 'image_url', image_url: { url: imageUrl } },
        { type: 'text', text: '描述图片内容' },
      ],
    },
  ],
};

const body = JSON.stringify(payload);
const req = https.request(
  {
    hostname: 'dashscope.aliyuncs.com',
    path: '/compatible-mode/v1/chat/completions',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${apiKey}`,
      'Content-Length': Buffer.byteLength(body),
    },
  },
  (res) => {
    let data = '';
    res.on('data', (chunk) => {
      data += chunk;
    });
    res.on('end', () => {
      console.log(`HTTP ${res.statusCode}`);
      try {
        const parsed = JSON.parse(data);
        const content = parsed.choices?.[0]?.message?.content;
        console.log(typeof content === 'string' ? content : JSON.stringify(content ?? parsed));
      } catch {
        console.log(data.slice(0, 500));
      }
    });
  },
);

req.on('error', (error) => {
  console.error(error.message);
  process.exit(1);
});

req.write(body);
req.end();
