export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = Record<string, JsonValue>;
export type AdminRouteKey =
  | "overview"
  | "monitoring"
  | "users"
  | "entitlements"
  | "orders"
  | "gift-cards"
  | "support"
  | "app-logs"
  | "today-agri"
  | "app-update"
  | "audit"
  | "insights"
  | "health";
export type AdminMonitoringLevel = "ok" | "warn" | "bad" | "error";
export type AdminMonitoringCapabilityStatus = "ready" | "partial" | "planned";

export interface AdminUser {
  id: number;
  username: string;
  display_name: string;
  role: string;
  enabled: boolean;
  must_change_password: boolean;
  created_at?: number;
  updated_at?: number;
  last_login_at?: number;
}

export interface AuthPayload {
  admin_user: AdminUser;
  expires_at: number;
  csrf_token: string;
}

export interface AdminOverview {
  health: AdminHealthStatus;
  today: AdminTodayMetrics;
  queues: AdminQueueMetrics;
  notes: AdminStatusNote[];
  now_ms: number;
}

export interface AdminMonitoring {
  health: AdminHealthStatus;
  windows: AdminMonitoringWindow[];
  queues: AdminMonitoringQueues;
  action_items: AdminMonitoringActionItem[];
  capabilities: AdminMonitoringCapability[];
  top_regions: AdminRegionMetric[];
  top_app_errors: ClientAppLogSummaryEntry[];
  notes: AdminStatusNote[];
  now_ms: number;
}

export interface AdminMonitoringWindow {
  key: string;
  label: string;
  since_ms: number;
  new_users: number;
  active_sessions: number;
  chat_rounds: number;
  chat_users: number;
  image_chat_rounds: number;
  quota_deductions: number;
  app_errors: number;
  app_warns: number;
  support_messages: number;
  support_users: number;
  gift_card_redeems: number;
  gift_card_failures: number;
  audit_failures: number;
  admin_actions: number;
  daily_agri_failed_cards: number;
}

export interface AdminMonitoringQueues {
  support_needs_reply: number;
  support_oldest_pending_at?: number;
  daily_agri_status: string;
  daily_agri_updated_at?: number;
  daily_agri_error?: string;
  app_update: AdminMonitoringAppUpdate;
  gift_card_active: number;
  gift_card_redeemed: number;
  gift_card_failed_attempts: number;
  audit_failures: number;
  app_errors: number;
  unready_dependency_count: number;
}

export interface AdminMonitoringAppUpdate {
  config_valid: boolean;
  download_artifacts_complete: boolean;
  has_apk_url: boolean;
  has_sha256: boolean;
  has_file_size: boolean;
  latest_version_code: number;
  latest_version_name?: string;
  force_update: boolean;
}

export interface AdminMonitoringActionItem {
  title: string;
  body: string;
  level: AdminMonitoringLevel;
  route?: AdminRouteKey;
  count?: number;
}

export interface AdminMonitoringCapability {
  title: string;
  status: AdminMonitoringCapabilityStatus;
  body: string;
  route?: AdminRouteKey;
}

export interface AdminRegionMetric {
  region: string;
  source?: string;
  reliability?: string;
  count: number;
  user_count: number;
  last_seen_at: number;
}

export interface AdminHealthStatus {
  api: string;
  bailian: string;
  dypns: string;
  dypns_fusion: string;
  dypns_sms: string;
  redis: string;
  upload_storage: string;
  auth_strict: boolean;
  dev_order_endpoints: boolean;
}

export interface AdminTodayMetrics {
  registered_users: number;
  active_auth_sessions: number;
  chat_rounds: number;
  chat_users: number;
  image_chat_rounds: number;
  quota_deductions: number;
  app_errors: number;
  support_conversations: number;
  support_needs_reply: number;
  daily_agri_status: string;
}

export interface AdminQueueMetrics {
  app_error_top: ClientAppLogSummaryEntry[];
}

export interface AdminStatusNote {
  title: string;
  body: string;
  level: string;
}

export interface DailyQuotaStatus {
  day_cn?: string;
  used?: number;
  limit?: number;
  remaining?: number;
}

export interface AdminUserListEntry {
  user_id: string;
  phone_mask?: string;
  created_at?: number;
  updated_at?: number;
  last_login_at?: number;
  tier: string;
  tier_expire_at?: number;
  daily: DailyQuotaStatus;
  topup_remaining: number;
  topup_expire_at?: number;
  upgrade_remaining: number;
  round_total: number;
  last_seen_at?: number;
  last_region?: string;
  last_region_source?: string;
  last_region_reliability?: string;
  active_sessions: number;
  error_count_24h: number;
  support_needs_reply: boolean;
  support_message_count: number;
}

export interface AdminUserDetail {
  user: AdminUserListEntry;
  quota_ledger: AdminQuotaLedgerEntry[];
  topup_packs: AdminTopupPackEntry[];
  upgrade_credits: AdminUpgradeCredit[];
  recent_rounds: AdminRoundExcerpt[];
  recent_app_logs: ClientAppLogEntry[];
  support_summary?: JsonObject;
  support_messages: AdminSupportMessage[];
  orders: AdminOrderEntry[];
  gift_cards: AdminGiftCardEntry[];
  gift_card_attempts: AdminGiftCardAttempt[];
}

export interface AdminQuotaLedgerEntry {
  id: number;
  client_msg_id: string;
  day_cn: string;
  source: string;
  delta: number;
  created_at: number;
}

export interface AdminTopupPackEntry {
  pack_id: string;
  user_id: string;
  order_id?: string;
  initial: number;
  remaining: number;
  used: number;
  expire_at?: number;
  status: string;
  created_at: number;
}

export interface AdminUpgradeCredit {
  user_id: string;
  remaining: number;
  expire_at?: number;
  updated_at: number;
}

export interface AdminRoundExcerpt {
  client_msg_id: string;
  user_excerpt: string;
  assistant_excerpt: string;
  has_images: boolean;
  image_count: number;
  region?: string;
  region_source?: string;
  region_reliability?: string;
  created_at: number;
}

export interface AdminSupportConversation {
  user_id: string;
  latest_message: AdminSupportMessage;
  message_count: number;
  unread_by_user_count: number;
  needs_reply: boolean;
}

export interface AdminSupportMessage {
  id: number;
  user_id?: string;
  sender_type: string;
  body?: string;
  body_excerpt: string;
  has_images: boolean;
  image_count: number;
  image_urls?: string[];
  created_at: number;
  read_by_user_at?: number;
}

export interface AdminOrderEntry {
  order_id: string;
  user_id: string;
  type: string;
  amount: string;
  created_at: number;
  status: string;
  result?: JsonValue;
}

export interface ClientAppLogEntry {
  id: number;
  user_id: string;
  level: string;
  event: string;
  message: string;
  attrs?: JsonValue;
  platform: string;
  app_version_code?: number;
  app_version_name?: string;
  os_version?: string;
  device_model?: string;
  client_time_ms?: number;
  created_at: number;
  masked_ip?: string;
}

export interface AdminGiftCardBatch {
  batch_id: string;
  name: string;
  tier: string;
  duration_days: number;
  quantity: number;
  active_count: number;
  redeemed_count: number;
  void_count: number;
  valid_from: number;
  valid_until?: number;
  created_by: string;
  note?: string;
  created_at: number;
}

export interface AdminGiftCardEntry {
  card_id: string;
  batch_id: string;
  code_mask: string;
  code_suffix: string;
  tier: string;
  duration_days: number;
  status: string;
  valid_from: number;
  valid_until?: number;
  created_by: string;
  note?: string;
  redeemed_user_id?: string;
  redeemed_phone_mask?: string;
  redeemed_region?: string;
  redeemed_region_source?: string;
  redeemed_region_reliability?: string;
  redeemed_at?: number;
  membership_expire_at?: number;
  voided_at?: number;
  created_at: number;
  updated_at: number;
}

export interface AdminGiftCardCreatedCode {
  card_id: string;
  code: string;
  code_mask: string;
  code_suffix: string;
  tier: string;
  duration_days: number;
  valid_until?: number;
}

export interface AdminGiftCardAttempt {
  id: number;
  code_suffix?: string;
  user_id?: string;
  success: boolean;
  failure_reason?: string;
  masked_ip?: string;
  region?: string;
  region_source?: string;
  region_reliability?: string;
  created_at: number;
}

export interface AdminGiftCardFailureReason {
  reason: string;
  count: number;
}

export interface AdminGiftCardSummary {
  batch_count: number;
  active_count: number;
  redeemed_count: number;
  void_count: number;
  failed_attempts_24h: number;
  failure_reasons: AdminGiftCardFailureReason[];
}

export interface ClientAppLogSummaryEntry {
  event: string;
  level: string;
  count: number;
}

export interface AdminAuditLogEntry {
  id: number;
  actor: string;
  action: string;
  target_type: string;
  target_id?: string;
  target_user_id?: string;
  success: boolean;
  status_code?: number;
  details?: JsonValue;
  masked_ip?: string;
  user_agent?: string;
  created_at: number;
}

export interface AdminDailyAgriEntry {
  day_cn: string;
  scope: string;
  status: string;
  title?: string;
  item_count: number;
  source_count: number;
  model?: string;
  search_strategy?: string;
  prompt_version?: string;
  content?: JsonValue;
  sources?: JsonValue;
  lease_until?: number;
  generated_at?: number;
  error?: string;
  created_at: number;
  updated_at: number;
}

export interface AdminAppUpdateConfig {
  latest_version_code: number;
  latest_version_name?: string;
  apk_url?: string;
  apk_sha256?: string;
  release_notes?: string;
  force_update: boolean;
  file_size_bytes?: number;
  config_valid: boolean;
  download_artifacts_complete: boolean;
  has_apk_url: boolean;
  has_sha256: boolean;
  has_file_size: boolean;
}

export interface ApiListResponse<T> {
  [key: string]: unknown;
  filter?: JsonObject;
  users?: T[];
  conversations?: T[];
  messages?: T[];
  logs?: T[];
  cards?: T[];
  batches?: T[];
  attempts?: T[];
  summary?: ClientAppLogSummaryEntry[];
}
