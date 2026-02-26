import type { ChatStreamRequest } from './types.js';

interface OpenBailianStreamArgs {
  payload: ChatStreamRequest;
  signal?: AbortSignal;
}

function buildUserContent(text: string, images: string[]): Array<Record<string, unknown>> {
  const content: Array<Record<string, unknown>> = [{ type: 'text', text }];
  for (const image of images) {
    content.push({
      type: 'image_url',
      image_url: { url: image },
    });
  }
  return content;
}

export async function openBailianStream({ payload, signal }: OpenBailianStreamArgs): Promise<Response> {
  const apiKey = process.env.DASHSCOPE_API_KEY;
  if (!apiKey) {
    throw new Error('DASHSCOPE_API_KEY is missing');
  }

  const baseUrl = process.env.BAILIAN_BASE_URL || 'https://dashscope.aliyuncs.com/compatible-mode/v1';
  const url = `${baseUrl.replace(/\/$/, '')}/chat/completions`;

  const body = {
    model: 'qwen3.5-plus',
    stream: true,
    extra_body: {
      enable_search: true,
      search_options: {
        search_strategy: 'turbo',
        forced_search: false,
      },
    },
    messages: [
      {
        role: 'user',
        content: buildUserContent(payload.text, payload.images ?? []),
      },
    ],
  };

  return fetch(url, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiKey}`,
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'Cache-Control': 'no-cache',
    },
    body: JSON.stringify(body),
    signal,
  });
}
