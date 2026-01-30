/**
 * 第3步验收：用上传返回的 url 验证模型侧能拉取
 * 用法: node verify-model-url.js <IMAGE_URL>
 * 环境变量: DASHSCOPE_API_KEY 或 API_KEY（不写进代码，脱敏）
 */
const https = require('https');
const url = process.argv[2];
if (!url || !url.startsWith('https://')) {
  console.error('用法: node verify-model-url.js <IMAGE_URL>');
  process.exit(1);
}
const apiKey = process.env.DASHSCOPE_API_KEY || process.env.API_KEY || '';
if (!apiKey) {
  console.error('请设置环境变量 DASHSCOPE_API_KEY 或 API_KEY');
  process.exit(1);
}

const payload = {
  model: 'qwen3-vl-flash',
  stream: false,
  messages: [
    {
      role: 'user',
      content: [
        { type: 'image_url', image_url: { url } },
        { type: 'text', text: '描述图片内容' },
      ],
    },
  ],
};

const body = JSON.stringify(payload);
console.log('--- 请求 payload（脱敏，无 key）---');
console.log(JSON.stringify({ ...payload, _note: 'key 已省略' }, null, 2));
console.log('');

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
    res.on('data', (ch) => (data += ch));
    res.on('end', () => {
      console.log('--- 响应 HTTP', res.statusCode, '---');
      try {
        const j = JSON.parse(data);
        const content = j.choices?.[0]?.message?.content;
        if (content != null) {
          console.log('choices[0].message.content:', typeof content === 'string' ? content : JSON.stringify(content));
        } else {
          console.log('body:', data.slice(0, 500));
        }
      } catch (_) {
        console.log('body:', data.slice(0, 500));
      }
    });
  }
);
req.on('error', (e) => console.error(e.message));
req.write(body);
req.end();
