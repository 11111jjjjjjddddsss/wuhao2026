import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export type SummaryLayer = 'B' | 'C';

interface PromptProbeResult {
  ok: boolean;
  path: string;
  chars?: number;
  error?: string;
}

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SYSTEM_ANCHOR_PATH = path.resolve(__dirname, '../assets/system_anchor.txt');
const B_PROMPT_PATH = path.resolve(__dirname, '../assets/b_extraction_prompt.txt');
const C_PROMPT_PATH = path.resolve(__dirname, '../assets/c_extraction_prompt.txt');

let cachedSystemAnchor: string | null = null;
const cachedSummaryPrompts: Partial<Record<SummaryLayer, string>> = {};

function resolveSummaryPromptPath(layer: SummaryLayer): string {
  return layer === 'B' ? B_PROMPT_PATH : C_PROMPT_PATH;
}

function readPromptFile(filePath: string, label: string): string {
  let raw = '';
  try {
    raw = fs.readFileSync(filePath, 'utf8');
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    throw new Error(`${label}_READ_FAILED:${message}`);
  }
  const prompt = raw.trim();
  if (!prompt) {
    throw new Error(`${label}_EMPTY`);
  }
  return prompt;
}

export function getSystemAnchorPath(): string {
  return SYSTEM_ANCHOR_PATH;
}

export function getSummaryPromptPath(layer: SummaryLayer): string {
  return resolveSummaryPromptPath(layer);
}

export function getSystemAnchor(): string {
  if (cachedSystemAnchor == null) {
    cachedSystemAnchor = readPromptFile(SYSTEM_ANCHOR_PATH, 'SYSTEM_ANCHOR');
  }
  return cachedSystemAnchor;
}

export function getSummaryPrompt(layer: SummaryLayer): string {
  const cached = cachedSummaryPrompts[layer];
  if (cached != null) return cached;
  const prompt = readPromptFile(resolveSummaryPromptPath(layer), `${layer}_SUMMARY_PROMPT`);
  cachedSummaryPrompts[layer] = prompt;
  return prompt;
}

export function probeSummaryPrompt(layer: SummaryLayer): PromptProbeResult {
  const filePath = resolveSummaryPromptPath(layer);
  try {
    const prompt = getSummaryPrompt(layer);
    return {
      ok: true,
      path: filePath,
      chars: prompt.length,
    };
  } catch (error) {
    return {
      ok: false,
      path: filePath,
      error: error instanceof Error ? error.message : String(error),
    };
  }
}
