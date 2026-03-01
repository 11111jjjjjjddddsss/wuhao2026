import type { BailianMessage, ChatStreamRequest } from './types.js';

interface OpenBailianStreamArgs {
  payload: ChatStreamRequest;
  messages: BailianMessage[];
  signal?: AbortSignal;
}

let keyCursor = 0;

function getBailianKeys(): string[] {
  const pool = (process.env.DASHSCOPE_API_KEYS || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  const single = (process.env.DASHSCOPE_API_KEY || '').trim();
  if (single) {
    pool.unshift(single);
  }
  return Array.from(new Set(pool));
}

function pickNextKey(): string {
  const keys = getBailianKeys();
  if (keys.length === 0) {
    throw new Error('DASHSCOPE_API_KEY(S) is missing');
  }
  const index = keyCursor % keys.length;
  keyCursor = (keyCursor + 1) % keys.length;
  return keys[index];
}

export async function openBailianStream({ payload, messages, signal }: OpenBailianStreamArgs): Promise<Response> {
  const apiKey = pickNextKey();

  const baseUrl = process.env.BAILIAN_BASE_URL || 'https://dashscope.aliyuncs.com/compatible-mode/v1';
  const url = `${baseUrl.replace(/\/$/, '')}/chat/completions`;

  const body = {
    model: 'qwen3.5-plus',
    stream: true,
    extra_body: {
      enable_thinking: false,
      enable_search: true,
      search_options: {
        search_strategy: 'turbo',
        forced_search: false,
      },
    },
    messages,
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
