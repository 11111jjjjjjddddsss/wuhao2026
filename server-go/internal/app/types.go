package app

type Tier string

const (
	TierFree Tier = "free"
	TierPlus Tier = "plus"
	TierPro  Tier = "pro"
)

type ChatStreamRequest struct {
	UserID            string   `json:"user_id,omitempty"`
	ClientMsgID       string   `json:"client_msg_id"`
	Text              string   `json:"text"`
	Images            []string `json:"images,omitempty"`
	SessionGeneration *int     `json:"session_generation,omitempty"`
	Region            string   `json:"region,omitempty"`
	RegionSource      string   `json:"region_source,omitempty"`
	RegionReliability string   `json:"region_reliability,omitempty"`
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
	ClientMsgID       string            `json:"client_msg_id,omitempty"`
	User              string            `json:"user"`
	UserImages        []string          `json:"user_images,omitempty"`
	Assistant         string            `json:"assistant"`
	CreatedAt         int64             `json:"created_at,omitempty"`
	Region            string            `json:"region,omitempty"`
	RegionSource      RegionSource      `json:"region_source,omitempty"`
	RegionReliability RegionReliability `json:"region_reliability,omitempty"`
}

type SessionSnapshot struct {
	UserID            string         `json:"user_id"`
	ARoundsFull       []SessionRound `json:"a_rounds_full"`
	MemoryDocument    string         `json:"memory_document"`
	PendingMemory     bool           `json:"pending_memory"`
	RoundTotal        int            `json:"round_total"`
	UpdatedAt         int64          `json:"updated_at"`
	SessionGeneration int            `json:"session_generation"`
}

type SessionGenerationState struct {
	Generation int
	ClearedAt  int64
}

type SessionRoundCompletion struct {
	Completed   bool
	CreatedAt   int64
	RequestHash string
}

type AuthMode string

const (
	AuthModeToken        AuthMode = "token"
	AuthModeHeader       AuthMode = "header"
	AuthModeUnauthorized AuthMode = "unauthorized"
)

type AuthInfo struct {
	UserID    string
	SessionID string
	AuthMode  AuthMode
	MaskedIP  string
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

type PromptProbeResult struct {
	OK    bool
	Path  string
	Chars int
	Error string
}

type DailyAgriCardItem struct {
	Title         string `json:"title"`
	Summary       string `json:"summary"`
	URL           string `json:"url"`
	Source        string `json:"source"`
	PublishedDate string `json:"published_date,omitempty"`
}

type DailyAgriCard struct {
	DateCN      string              `json:"date_cn"`
	Title       string              `json:"title"`
	Items       []DailyAgriCardItem `json:"items"`
	GeneratedAt int64               `json:"generated_at,omitempty"`
}

type DailyAgriSearchSource struct {
	Index    int    `json:"index,omitempty"`
	Title    string `json:"title,omitempty"`
	URL      string `json:"url,omitempty"`
	SiteName string `json:"site_name,omitempty"`
}
