package app

type Tier string

const (
	TierFree Tier = "free"
	TierPlus Tier = "plus"
	TierPro  Tier = "pro"
)

type ChatStreamRequest struct {
	UserID      string   `json:"user_id,omitempty"`
	ClientMsgID string   `json:"client_msg_id"`
	Text        string   `json:"text"`
	Images      []string `json:"images,omitempty"`
}

type BailianMessage struct {
	Role    string `json:"role"`
	Content any    `json:"content"`
}

type DailyQuotaStatus struct {
	DayCN     string `json:"day_cn"`
	Tier      Tier   `json:"tier"`
	Used      int    `json:"used"`
	Limit     int    `json:"limit"`
	Remaining int    `json:"remaining"`
}

type QuotaSource string

const (
	QuotaSourceDaily   QuotaSource = "daily"
	QuotaSourceTopup   QuotaSource = "topup"
	QuotaSourceUpgrade QuotaSource = "upgrade"
)

type ConsumeResult struct {
	Deducted bool             `json:"deducted"`
	Source   *QuotaSource     `json:"source,omitempty"`
	Status   DailyQuotaStatus `json:"status"`
}

type SessionRound struct {
	ClientMsgID string   `json:"client_msg_id,omitempty"`
	User        string   `json:"user"`
	UserImages  []string `json:"user_images,omitempty"`
	Assistant   string   `json:"assistant"`
}

type SessionSnapshot struct {
	UserID        string         `json:"user_id"`
	ARoundsFull   []SessionRound `json:"a_rounds_full"`
	BSummary      string         `json:"b_summary"`
	CSummary      string         `json:"c_summary"`
	PendingRetryB bool           `json:"pending_retry_b"`
	PendingRetryC bool           `json:"pending_retry_c"`
	RoundTotal    int            `json:"round_total"`
	UpdatedAt     int64          `json:"updated_at"`
}

type AuthMode string

const (
	AuthModeToken        AuthMode = "token"
	AuthModeHeader       AuthMode = "header"
	AuthModeUnauthorized AuthMode = "unauthorized"
)

type AuthInfo struct {
	UserID   string
	AuthMode AuthMode
	MaskedIP string
}

type RegionSource string

const (
	RegionSourceGPS  RegionSource = "gps"
	RegionSourceIP   RegionSource = "ip"
	RegionSourceNone RegionSource = "none"
)

type RegionReliability string

const (
	RegionReliable   RegionReliability = "reliable"
	RegionUnreliable RegionReliability = "unreliable"
)

type RegionContext struct {
	Region      string
	Source      RegionSource
	Reliability RegionReliability
}

type SummaryLayer string

const (
	SummaryLayerB SummaryLayer = "B"
	SummaryLayerC SummaryLayer = "C"
)

type PromptProbeResult struct {
	OK    bool
	Path  string
	Chars int
	Error string
}
