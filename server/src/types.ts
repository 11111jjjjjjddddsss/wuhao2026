export type Tier = 'free' | 'plus' | 'pro';

export interface ChatStreamRequest {
  user_id: string;
  tier: Tier;
  client_msg_id: string;
  text: string;
  images?: string[];
}

export interface DailyQuotaStatus {
  day_cn: string;
  tier: Tier;
  used: number;
  limit: number;
  remaining: number;
}

export type QuotaSource = 'daily' | 'topup' | 'upgrade';

export interface ConsumeResult {
  deducted: boolean;
  source?: QuotaSource;
  status: DailyQuotaStatus;
}

export interface SessionRound {
  user: string;
  user_images?: string[];
  assistant: string;
}

export interface SessionSnapshot {
  user_id: string;
  session_id: string;
  a_rounds_full: SessionRound[];
  b_summary: string;
  round_total: number;
  updated_at: number;
}
