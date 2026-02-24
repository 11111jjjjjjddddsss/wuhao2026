export type Tier = 'free' | 'plus' | 'pro';

export interface ChatStreamRequest {
  user_id: string;
  tier: Tier;
  client_msg_id: string;
  text: string;
  images?: string[];
}

export interface DailyQuotaStatus {
  yyyymmdd: string;
  tier: Tier;
  used: number;
  limit: number;
  remaining: number;
}

export interface SearchResult {
  title?: string;
  summary?: string;
  snippet?: string;
  url?: string;
  siteName?: string;
}