import "./styles.css";
import { ApiError, apiFetch, getStoredAuth, setStoredAuth, toQuery } from "./api";
import type {
  AccountDeletionRequest,
  AdminAppUpdateConfig,
  AdminAppUpdateEvent,
  AdminAuditLogEntry,
  AdminDailyAgriEntry,
  AdminEntitlementSummary,
  AdminGiftCardAttempt,
  AdminGiftCardBatch,
  AdminGiftCardCreatedCode,
  AdminGiftCardEntry,
  AdminGiftCardSummary,
  AdminInsightBreakdown,
  AdminInsights,
  AdminMonitoring,
  AdminOrderEntry,
  AdminOrdersResponse,
  AdminOverview,
  AdminQuotaConsumeOutboxEntry,
  AdminQuotaLedgerEntry,
  AdminRegionMetric,
  AdminSupportConversation,
  AdminSupportMessage,
  AdminSupportMessagesResponse,
  AdminTopupPackEntry,
  AdminRouteKey,
  AdminRole,
  AdminUserDetail,
  AdminUserListEntry,
  AdminUpgradeCredit,
  AuthPayload,
  ClientAppLogEntry,
  ClientAppLogSummaryEntry,
  JsonObject,
  JsonValue,
} from "./types";

type RouteKey = AdminRouteKey;

interface RouteItem {
  key: RouteKey;
  label: string;
  section: string;
  hint: string;
  roles?: AdminRole[];
}

const simpleRouteKeys: RouteKey[] = ["monitoring", "users", "support", "app-logs", "gift-cards"];
const adminModeStorageKey = "nongji_admin_simple_mode";

const routes: RouteItem[] = [
  { key: "monitoring", label: "首页 · 今天先看", section: "工作台", hint: "先看" },
  { key: "overview", label: "数据总览", section: "工作台", hint: "指标" },
  { key: "users", label: "用户管理", section: "用户与增长", hint: "可查", roles: ["ops_readonly", "support", "finance_ops"] },
  { key: "insights", label: "产品洞察", section: "用户与增长", hint: "聚合报表" },
  { key: "entitlements", label: "会员额度", section: "权益与交易", hint: "用户级只读", roles: ["ops_readonly", "support", "finance_ops", "auditor"] },
  { key: "orders", label: "订单", section: "权益与交易", hint: "核查/补发", roles: ["ops_readonly", "support", "finance_ops"] },
  { key: "gift-cards", label: "礼品卡", section: "权益与交易", hint: "发卡/追溯", roles: ["finance_ops", "ops_readonly", "auditor"] },
  { key: "account-deletion", label: "注销申请", section: "运营工作台", hint: "待处理/核验", roles: ["support", "ops_readonly", "auditor", "finance_ops"] },
  { key: "support", label: "帮助反馈", section: "运营工作台", hint: "回复/查看", roles: ["support", "ops_readonly", "auditor"] },
  { key: "today-agri", label: "今日农情", section: "运营工作台", hint: "人工/补跑", roles: ["content_ops", "ops_readonly", "auditor"] },
  { key: "app-update", label: "检查更新", section: "运营工作台", hint: "发布/停更", roles: ["release_ops", "ops_readonly", "auditor"] },
  { key: "app-logs", label: "App日志", section: "排障与安全", hint: "可查", roles: ["ops_readonly", "support", "auditor"] },
  { key: "audit", label: "审计", section: "排障与安全", hint: "可查", roles: ["auditor", "ops_readonly"] },
  { key: "account", label: "账号安全", section: "排障与安全", hint: "改密" },
  { key: "health", label: "服务健康", section: "排障与安全", hint: "可查" },
];

const appNode = document.querySelector<HTMLDivElement>("#app");
if (!appNode) throw new Error("missing #app");
const app = appNode;

let auth: AuthPayload | null = getStoredAuth();
let activeRoute: RouteKey = routeFromHash();
let lastGiftCardCodes: AdminGiftCardCreatedCode[] = [];
let sidebarScrollTop = 0;
let pendingScrollTarget = "";
let privacyMaskEnabled = false;
let adminSimpleMode = loadAdminSimpleMode();
const pageState = {
  userQuery: "",
  userDetailID: "",
  supportUserID: "",
  supportStatus: "open",
  supportQuery: "",
  supportMessageQuery: "",
  supportSinceRange: "30d",
  entitlementUserID: "",
  orderUserID: "",
  giftCardBatchID: "",
  giftCardStatus: "",
  giftCardUserID: "",
  giftCardCodeSuffix: "",
  giftCardAttemptSuccess: "",
  giftCardAttemptReason: "",
  accountDeletionUserID: "",
  accountDeletionStatus: "pending",
  appLogWindow: "24h",
  auditWindow: "24h",
};

window.addEventListener("hashchange", () => {
  activeRoute = routeFromHash();
  if (activeRoute !== "gift-cards") lastGiftCardCodes = [];
  void render();
});

function clearSensitiveAdminState(): void {
  lastGiftCardCodes = [];
}

function loadAdminSimpleMode(): boolean {
  try {
    return localStorage.getItem(adminModeStorageKey) !== "full";
  } catch {
    return true;
  }
}

function saveAdminSimpleMode(enabled: boolean): void {
  try {
    localStorage.setItem(adminModeStorageKey, enabled ? "simple" : "full");
  } catch {
    // Browser storage can be disabled; the current session still switches mode.
  }
}

window.addEventListener("admin:unauthorized", () => {
  auth = null;
  clearSensitiveAdminState();
  renderLogin("登录状态已失效，请重新登录。");
});

document.addEventListener("submit", (event) => {
  const form = event.target;
  if (!(form instanceof HTMLFormElement)) return;
  event.preventDefault();
  void handleSubmit(form);
});

document.addEventListener("click", (event) => {
  const target = event.target as HTMLElement | null;
  const button = target?.closest<HTMLElement>("[data-action]");
  if (!button) return;
  void handleAction(button);
});

void boot();

async function boot(): Promise<void> {
  if (!auth) {
    renderLogin();
    return;
  }
  renderLoadingShell("校验后台会话");
  try {
    auth = await apiFetch<AuthPayload>("/admin-api/v1/auth/me");
    setStoredAuth(auth);
    await render();
  } catch {
    auth = null;
    setStoredAuth(null);
    clearSensitiveAdminState();
    renderLogin();
  }
}

async function render(): Promise<void> {
  if (!auth) {
    renderLogin();
    return;
  }
  if (auth.admin_user.must_change_password) {
    activeRoute = "account";
  }
  if (!isRouteVisible(activeRoute)) {
    activeRoute = defaultRoute();
  }
  renderShell(loadingBlock("加载模块"));
  const main = document.querySelector<HTMLElement>("#main-content");
  if (!main) return;
  try {
    main.innerHTML = await routeContent(activeRoute);
  } catch (error) {
    main.innerHTML = errorBlock(error);
  }
  if (pendingScrollTarget) {
    const target = pendingScrollTarget;
    pendingScrollTarget = "";
    scrollElementIntoViewOnMobile(target);
  }
}

function renderLogin(message = ""): void {
  app.innerHTML = `
    <main class="login-screen">
      <section class="login-panel">
        <div class="login-card">
          <div class="brand-row">
            ${brandMarkHTML()}
            <div>
              <h1>农技千查管理后台</h1>
              <div class="muted small">后台账号由后端初始化，前端不保存密码或密钥。</div>
            </div>
          </div>
          ${message ? `<div class="notice warn" style="margin-top:16px">${escapeHTML(message)}</div>` : ""}
          <form id="login-form" class="login-form" autocomplete="on">
            <label class="field">
              <span>账号</span>
              <input class="input" name="username" autocomplete="username" required />
            </label>
            <label class="field">
              <span>密码</span>
              <input class="input" name="password" type="password" autocomplete="current-password" required />
            </label>
            <button class="button primary" type="submit">登录</button>
          </form>
        </div>
      </section>
      <section class="login-aside">
        <h2>运营、排障、审计集中在一个正式工作台。</h2>
        <p class="muted" style="max-width:540px;line-height:1.8">
          所有业务数据以后台服务记录为准；用户、会员、订单、反馈、日志和今日农情只在授权后展示或执行受控操作。
          未开放模块会明确标注当前状态，不展示模拟成功数据。
        </p>
      </section>
    </main>
  `;
}

function renderLoadingShell(label: string): void {
  captureSidebarScroll();
  app.innerHTML = `
    <div class="app-shell${privacyMaskEnabled ? " privacy-mask" : ""}">
      ${sidebarHTML()}
      <section class="workspace">
        ${topbarHTML()}
        <main class="content">${loadingBlock(label)}</main>
      </section>
    </div>
  `;
  restoreSidebarScroll();
}

function renderShell(content: string): void {
  captureSidebarScroll();
  app.innerHTML = `
    <div class="app-shell${privacyMaskEnabled ? " privacy-mask" : ""}">
      ${sidebarHTML()}
      <section class="workspace">
        ${topbarHTML()}
        <main id="main-content" class="content">${content}</main>
      </section>
    </div>
  `;
  restoreSidebarScroll();
}

function sidebarHTML(): string {
  const navRoutes = visibleRoutes();
  const primaryRoutes = simpleSidebarRoutes(navRoutes);
  const advancedRoutes = navRoutes.filter((route) => !primaryRoutes.includes(route));
  if (adminSimpleMode) {
    return `
      <aside class="sidebar simple-sidebar">
        <div class="sidebar-head">
          <div class="brand-row">
            ${brandMarkHTML()}
            <div>
              <div class="sidebar-title">农技千查</div>
              <div class="small" style="color:#9ba6b2">简单后台</div>
            </div>
          </div>
        </div>
        <div class="nav-section">
          <div class="nav-section-title">常用</div>
          ${primaryRoutes.map((route) => sidebarRouteButton(route)).join("")}
        </div>
        <div class="nav-more">
          <div class="nav-more-title">
            <span>更多工具</span>
            <span class="small">${advancedRoutes.length} 项</span>
          </div>
          <div class="nav-more-body">
            ${advancedRoutes
              .map(
                (route) => `
                  <div class="nav-more-row">
                    <span class="nav-more-section">${escapeHTML(route.section)}</span>
                    ${sidebarRouteButton(route)}
                  </div>
                `,
              )
              .join("")}
          </div>
        </div>
      </aside>
    `;
  }
  const sections = [...new Set(navRoutes.map((route) => route.section))];
  return `
    <aside class="sidebar">
      <div class="sidebar-head">
        <div class="brand-row">
          ${brandMarkHTML()}
          <div>
            <div class="sidebar-title">农技千查</div>
            <div class="small" style="color:#9ba6b2">管理后台</div>
          </div>
        </div>
      </div>
      ${sections
        .map(
          (section) => `
            <div class="nav-section">
              <div class="nav-section-title">${escapeHTML(section)}</div>
              ${navRoutes
                .filter((route) => route.section === section)
                .map((route) => sidebarRouteButton(route))
                .join("")}
            </div>
          `,
        )
        .join("")}
    </aside>
  `;
}

function sidebarRouteButton(route: RouteItem): string {
  return `
    <button class="nav-item ${route.key === activeRoute ? "active" : ""}" data-action="route" data-route="${route.key}">
      <span class="nav-label">${escapeHTML(route.label)}</span>
      <span class="small">${escapeHTML(route.hint)}</span>
    </button>
  `;
}

function simpleSidebarRoutes(navRoutes: RouteItem[]): RouteItem[] {
  const selected = simpleRouteKeys
    .map((key) => navRoutes.find((route) => route.key === key))
    .filter((route): route is RouteItem => Boolean(route));
  return selected.length ? selected : navRoutes.slice(0, 5);
}

function topbarHTML(): string {
  const user = auth?.admin_user;
  const currentRoute = routes.find((item) => item.key === activeRoute);
  return `
    <header class="topbar">
      <div class="topbar-left">
        <span class="env-badge">生产后台</span>
        <span class="muted small topbar-api">${escapeHTML(currentRoute?.label || "总览")}</span>
      </div>
      <div class="topbar-right">
        ${isRouteVisible("users") ? `<form id="global-search-form" class="global-search-form">
          <input class="input" name="query" value="" placeholder="账号ID / 手机号" aria-label="账号ID或手机号" />
          <button class="button" type="submit">查</button>
        </form>` : ""}
        <div class="mobile-quick-actions" aria-label="常用入口">
          ${quickRouteButton("monitoring", "看监控")}
          ${quickRouteButton("users", "查用户")}
          ${quickRouteButton("support", "看反馈")}
        </div>
        <span class="role-badge">${escapeHTML(roleLabel(user?.role))}</span>
        <span class="small">${escapeHTML(user?.display_name || user?.username || "")}</span>
        ${user?.must_change_password ? `<span class="pill warn">需要改密</span>` : ""}
        <button class="button mode-toggle" type="button" data-action="toggle-admin-mode">${adminSimpleMode ? "全部功能" : "简单模式"}</button>
        <button class="button" type="button" data-action="toggle-privacy-mask">${privacyMaskEnabled ? "显示敏感" : "截图模式"}</button>
        <button class="button topbar-account-button" data-action="route" data-route="account">账号安全</button>
        <button class="button" data-action="refresh">刷新</button>
        <button class="button" data-action="logout">退出</button>
      </div>
    </header>
  `;
}

function quickRouteButton(route: RouteKey, label: string): string {
  if (!isRouteVisible(route)) return "";
  return `<button class="button quick-button" data-action="route" data-route="${escapeAttr(route)}">${escapeHTML(label)}</button>`;
}

async function routeContent(route: RouteKey): Promise<string> {
  switch (route) {
    case "overview":
      return overviewPage();
    case "monitoring":
      return monitoringPage();
    case "users":
      return usersPage();
    case "entitlements":
      return entitlementsPage();
    case "orders":
      return ordersPage();
    case "gift-cards":
      return giftCardsPage();
    case "account-deletion":
      return accountDeletionPage();
    case "support":
      return supportPage();
    case "app-logs":
      return appLogsPage();
    case "today-agri":
      return todayAgriPage();
    case "app-update":
      return appUpdatePage();
    case "audit":
      return auditPage();
    case "account":
      return accountSecurityPage();
    case "insights":
      return insightsPage();
    case "health":
      return healthPage();
  }
}

function brandMarkHTML(): string {
  return `<div class="brand-mark"><img src="/brand-mark.png" alt="" /></div>`;
}

function captureSidebarScroll(): void {
  const sidebar = document.querySelector<HTMLElement>(".sidebar");
  if (sidebar) sidebarScrollTop = sidebar.scrollTop;
}

function restoreSidebarScroll(): void {
  requestAnimationFrame(() => {
    const sidebar = document.querySelector<HTMLElement>(".sidebar");
    if (sidebar) sidebar.scrollTop = sidebarScrollTop;
  });
}

async function overviewPage(): Promise<string> {
  const overview = await apiFetch<AdminOverview>("/admin-api/v1/overview");
  const today = overview.today;
  return `
    ${pageHead("总览", "服务状态、业务量、待处理队列和关键风险。", "overview")}
    <section class="grid kpi">
      ${kpi("服务健康", healthSummary(overview.health), `API ${overview.health.api || "unknown"}`)}
      ${kpi("今日问诊", today.chat_rounds, `${today.chat_users} 位去重用户`)}
      ${kpi("图片问诊", today.image_chat_rounds, "本日包含图片轮次")}
      ${kpi("App异常", today.app_errors, "用户端错误日志")}
      ${kpi("未回复反馈", today.support_needs_reply, `近30天 ${today.support_conversations} 个会话`)}
      ${kpi("今日农情", today.daily_agri_status || "unknown", "当天内容状态")}
    </section>
    ${overviewActionStrip()}
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head">
          <div class="card-title">今日业务指标</div>
          <span class="small muted">${formatTime(overview.now_ms)}</span>
        </div>
        <div class="card-body">
          <table class="table">
            <tbody>
              ${metricRow("注册用户", today.registered_users)}
              ${metricRow("活跃登录设备/会话", today.active_auth_sessions)}
              ${metricRow("问诊去重用户", today.chat_users)}
              ${metricRow("额度扣减", today.quota_deductions)}
              ${metricRow("系统补扣中", today.quota_consume_pending ?? 0)}
              ${metricRow("近30天反馈会话", today.support_conversations)}
            </tbody>
          </table>
        </div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">依赖健康</div></div>
        <div class="card-body">${healthGrid(overview.health)}</div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">App错误 Top</div></div>
        <div class="card-body">${appErrorTopTable(overview.queues.app_error_top)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">状态备注</div></div>
        <div class="card-body stack">
          ${
            overview.notes?.length
              ? overview.notes.map((note) => notice(note.title, note.body, note.level)).join("")
              : emptyState("没有备注", "暂无额外状态说明。")
          }
        </div>
      </section>
    </div>
  `;
}

function overviewActionStrip(): string {
  return `
    <div class="overview-action-strip">
      ${routeActionButton("monitoring", "回首页看重点")}
      ${routeActionButton("support", "处理反馈")}
      ${routeActionButton("app-logs", "看 App 日志")}
      ${routeActionButton("users", "查用户")}
    </div>
  `;
}

function monitoringSimpleActions(report: AdminMonitoring): string {
  const supportCount = report.queues.support_needs_reply || 0;
  const appErrorCount = report.queues.app_errors || 0;
  const giftFailures = report.queues.gift_card_failed_attempts || 0;
  const updateTrouble =
    (report.app_update_logs?.check_failures || 0) +
    (report.app_update_logs?.download_failures || 0) +
    (report.app_update_logs?.install_failures || 0);
  const healthTrouble = report.queues.unready_dependency_count || 0;
  const cards = [
    { route: "users" as RouteKey, title: "查用户", body: "输入手机号或账号ID，直接看手机号、会员、问诊、反馈和 App 日志。", level: "info" as const },
    { route: "support" as RouteKey, title: "回反馈", body: supportCount > 0 ? `还有 ${supportCount} 条待回复，先处理这里。` : "当前没有待回复，也可以查看历史会话。", level: supportCount > 0 ? "warn" as const : "ok" as const },
    { route: "app-logs" as RouteKey, title: "看异常", body: appErrorCount > 0 ? `最近有 ${appErrorCount} 条 App 异常，点进去看阶段。` : "最近 App 异常不多，排障时从这里查。", level: appErrorCount > 0 ? "warn" as const : "ok" as const },
    { route: "gift-cards" as RouteKey, title: "礼品卡", body: giftFailures > 0 ? `兑换失败 ${giftFailures} 次，先看尾号和账号。` : "生成、复制、追溯和作废礼品卡都在这里。", level: giftFailures > 0 ? "warn" as const : "info" as const },
    { route: "app-update" as RouteKey, title: "检查更新", body: updateTrouble > 0 ? `更新链路有 ${updateTrouble} 条异常日志。` : "查看正式包配置、停更和发布记录。", level: updateTrouble > 0 ? "warn" as const : "info" as const },
    { route: "health" as RouteKey, title: "系统状态", body: healthTrouble > 0 ? `有 ${healthTrouble} 个关键依赖需要看。` : "API、短信、Redis、OSS 等关键项。", level: healthTrouble > 0 ? "bad" as const : "ok" as const },
  ];
  return `
    <section class="simple-actions" aria-label="常用操作">
      ${cards
        .filter((card) => isRouteVisible(card.route))
        .map(
          (card) => `
            <article class="simple-action-card ${card.level}">
              <div>
                <strong>${escapeHTML(card.title)}</strong>
                <p>${escapeHTML(card.body)}</p>
              </div>
              ${routeActionButton(card.route, "打开")}
            </article>
          `,
        )
        .join("")}
    </section>
  `;
}

function monitoringAdvancedDetails(
  report: AdminMonitoring,
  today: AdminMonitoring["windows"][number] | undefined,
  day24: AdminMonitoring["windows"][number] | undefined,
): string {
  return `
    <details class="advanced-panel">
      <summary>
        <span>更多监控和上线检查</span>
        <span class="small">高级排障、趋势、地区、能力接入</span>
      </summary>
      <div class="advanced-panel-body">
        ${monitoringOperatorGuide(report)}
        ${monitoringProgramCheckStrip(report)}
        ${monitoringManualCheckStrip(report)}
        <section class="card" style="margin-bottom:12px">
          <div class="card-head"><div class="card-title">AI 问诊状态</div><span class="small muted">当前后台服务调用智能分析能力的规则</span></div>
          <div class="card-body">${modelUsagePolicyBlock(report.model_usage_policy || [])}</div>
        </section>
        <section class="card" style="margin-bottom:12px">
          <div class="card-head"><div class="card-title">手机实测清单</div><span class="small muted">登录、问诊、图片、礼品卡、更新和反馈</span></div>
          <div class="card-body">${monitoringRegressionChecklist(report)}</div>
        </section>
        <section class="card" style="margin-bottom:12px">
          <div class="card-head"><div class="card-title">上线前检查</div><span class="small muted">可继续 / 需确认 / 暂缓上架</span></div>
          <div class="card-body">${launchReadinessGrid(report.launch_readiness || [])}</div>
        </section>
        ${monitoringShortcutBar()}
        <section class="grid kpi">
          ${kpi("核心服务问题", report.queues.unready_dependency_count, "AI 问诊 / 登录 / 缓存 / 图片存储")}
          ${kpi("App异常", day24?.app_errors ?? 0, `最近24小时，警告 ${day24?.app_warns ?? 0} 条`)}
          ${kpi("登录问题", report.queues.auth_failures ?? 0, `最近24小时，闪退补报 ${report.queues.crash_reports ?? 0} 条`)}
          ${kpi("待回复反馈", report.queues.support_needs_reply, report.queues.support_oldest_pending_at ? `最早 ${formatTime(report.queues.support_oldest_pending_at)}` : "当前无等待")}
          ${kpi("今日问诊", today?.chat_rounds ?? 0, `${today?.chat_users ?? 0} 位去重用户`)}
          ${kpi("礼品卡异常", report.queues.gift_card_failed_attempts, "最近24小时兑换失败")}
          ${kpi("今日农情", dailyAgriStatusText(report.queues.daily_agri_status), report.queues.daily_agri_updated_at ? formatTime(report.queues.daily_agri_updated_at) : "未返回更新时间")}
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">运营队列</div><span class="small muted">反馈、注销、农情、更新、礼品卡</span></div>
          <div class="card-body">${monitoringQueueCards(report)}</div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">登录排障</div><span class="small muted">短信验证码 / 登录 / 闪退补报</span></div>
          <div class="card-body">${authTroubleshootingBlock(report.auth_logs)}</div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">检查更新排障</div><span class="small muted">请求 / 下载 / 校验 / 安装权限</span></div>
          <div class="card-body">${appUpdateTroubleshootingBlock(report.app_update_logs)}</div>
        </section>
        <div class="grid two" style="margin-top:12px">
          <section class="card">
            <div class="card-head"><div class="card-title">核心服务状态</div></div>
            <div class="card-body">${healthChipGrid(report.health)}</div>
          </section>
          <section class="card">
            <div class="card-head"><div class="card-title">使用量趋势</div><span class="small muted">${formatTime(report.now_ms)}</span></div>
            <div class="table-wrap">${monitoringWindowTable(report.windows)}</div>
          </section>
        </div>
        <div class="grid two" style="margin-top:12px">
          <section class="card">
            <div class="card-head"><div class="card-title">App异常 Top</div><span class="small muted">最近24小时</span></div>
            <div class="card-body">${appErrorTopTable(report.top_app_errors)}</div>
          </section>
          <section class="card">
            <div class="card-head"><div class="card-title">问诊地区分布</div><span class="small muted">最近30天问诊</span></div>
            <div class="table-wrap">${regionMetricsTable(report.top_regions)}</div>
          </section>
        </div>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">用户地区概览</div><span class="small muted">按账号最近已识别地区，大概看注册和会员分布</span></div>
          <div class="card-body">${userRegionOverviewBlock(report.user_regions)}</div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">隐私边界</div></div>
          <div class="card-body stack">
            ${report.notes?.length ? report.notes.map((note) => notice(note.title, note.body, note.level)).join("") : emptyState("没有备注", "暂无监控备注。")}
          </div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">后台功能接入情况</div><span class="small muted">哪些已能真实使用，哪些还只是规划</span></div>
          <div class="card-body">${capabilityGrid(report.capabilities || [])}</div>
        </section>
      </div>
    </details>
  `;
}

async function monitoringPage(): Promise<string> {
  const report = await apiFetch<AdminMonitoring>("/admin-api/v1/monitoring");
  const today = report.windows.find((item) => item.key === "today") || report.windows[0];
  const day24 = report.windows.find((item) => item.key === "24h") || report.windows[0];
  if (adminSimpleMode) {
    return `
      ${pageHead("首页", "先看今天有没有事，再点入口处理。", "monitoring")}
      ${monitoringHero(report)}
      ${monitoringReadinessSummary(report)}
      <section class="card" style="margin-bottom:12px">
        <div class="card-head"><div class="card-title">今天先处理</div><span class="small muted">${report.action_items?.length || 0} 项</span></div>
        <div class="card-body">${actionItemList(report.action_items || [])}</div>
      </section>
      ${monitoringSimpleActions(report)}
      ${monitoringDecisionGrid(report, today, day24)}
      ${monitoringAdvancedDetails(report, today, day24)}
    `;
  }
  return `
    ${pageHead("监控面板", "查看上线准备状态、待处理事项和对应处理入口。", "monitoring")}
    ${monitoringHero(report)}
    ${monitoringReadinessSummary(report)}
    <section class="card" style="margin-bottom:12px">
      <div class="card-head"><div class="card-title">先处理事项</div><span class="small muted">${report.action_items?.length || 0} 项</span></div>
      <div class="card-body">${actionItemList(report.action_items || [])}</div>
    </section>
    ${monitoringOperatorGuide(report)}
    ${monitoringProgramCheckStrip(report)}
    ${monitoringManualCheckStrip(report)}
    ${monitoringDecisionGrid(report, today, day24)}
    <section class="card" style="margin-bottom:12px">
      <div class="card-head"><div class="card-title">智能问诊链路</div><span class="small muted">当前后台服务调用智能分析能力的规则</span></div>
      <div class="card-body">${modelUsagePolicyBlock(report.model_usage_policy || [])}</div>
    </section>
    <section class="card" style="margin-bottom:12px">
      <div class="card-head"><div class="card-title">上线前真机回归清单</div><span class="small muted">按登录、问诊、图片和运营主链看后台信号</span></div>
      <div class="card-body">${monitoringRegressionChecklist(report)}</div>
    </section>
    <section class="card" style="margin-bottom:12px">
      <div class="card-head"><div class="card-title">正式上架检查</div><span class="small muted">可继续 / 需确认 / 暂缓上架</span></div>
      <div class="card-body">${launchReadinessGrid(report.launch_readiness || [])}</div>
    </section>
    ${monitoringShortcutBar()}
    <section class="grid kpi">
      ${kpi("核心服务问题", report.queues.unready_dependency_count, "智能问诊 / 登录 / 缓存 / 图片存储")}
      ${kpi("App异常", day24?.app_errors ?? 0, `最近24小时，警告 ${day24?.app_warns ?? 0} 条`)}
      ${kpi("登录问题", report.queues.auth_failures ?? 0, `最近24小时，闪退补报 ${report.queues.crash_reports ?? 0} 条`)}
      ${kpi("待回复反馈", report.queues.support_needs_reply, report.queues.support_oldest_pending_at ? `最早 ${formatTime(report.queues.support_oldest_pending_at)}` : "当前无等待")}
      ${kpi("今日问诊", today?.chat_rounds ?? 0, `${today?.chat_users ?? 0} 位去重用户`)}
      ${kpi("礼品卡异常", report.queues.gift_card_failed_attempts, "最近24小时兑换失败")}
      ${kpi("今日农情", dailyAgriStatusText(report.queues.daily_agri_status), report.queues.daily_agri_updated_at ? formatTime(report.queues.daily_agri_updated_at) : "未返回更新时间")}
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">运营队列</div><span class="small muted">反馈、注销、农情、更新、礼品卡</span></div>
      <div class="card-body">${monitoringQueueCards(report)}</div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">登录排障</div><span class="small muted">短信验证码 / 登录 / 闪退补报</span></div>
      <div class="card-body">${authTroubleshootingBlock(report.auth_logs)}</div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">检查更新排障</div><span class="small muted">请求 / 下载 / 校验 / 安装权限</span></div>
      <div class="card-body">${appUpdateTroubleshootingBlock(report.app_update_logs)}</div>
    </section>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">核心服务状态</div></div>
        <div class="card-body">${healthChipGrid(report.health)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">使用量趋势</div><span class="small muted">${formatTime(report.now_ms)}</span></div>
        <div class="table-wrap">${monitoringWindowTable(report.windows)}</div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">App异常 Top</div><span class="small muted">最近24小时</span></div>
        <div class="card-body">${appErrorTopTable(report.top_app_errors)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">问诊地区分布</div><span class="small muted">最近30天问诊</span></div>
        <div class="table-wrap">${regionMetricsTable(report.top_regions)}</div>
      </section>
    </div>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">用户地区概览</div><span class="small muted">按账号最近已识别地区，大概看注册和会员分布</span></div>
      <div class="card-body">${userRegionOverviewBlock(report.user_regions)}</div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">隐私边界</div></div>
      <div class="card-body stack">
        ${report.notes?.length ? report.notes.map((note) => notice(note.title, note.body, note.level)).join("") : emptyState("没有备注", "暂无监控备注。")}
      </div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">后台功能接入情况</div><span class="small muted">哪些已能真实使用，哪些还只是规划</span></div>
      <div class="card-body">${capabilityGrid(report.capabilities || [])}</div>
    </section>
  `;
}

async function usersPage(): Promise<string> {
  const query = pageState.userQuery;
  const response = await apiFetch<{ users: AdminUserListEntry[]; filter: unknown }>(
    `/admin-api/v1/users${toQuery({ query, limit: 50 })}`,
  );
  return `
    ${pageHead("用户管理", "按账号ID查询用户；主账号也可用完整手机号精确查询。", "users")}
    <form class="filters" id="users-filter-form">
      <label class="field wide">
        <span>账号查询</span>
        <input class="input" name="query" value="${escapeAttr(query)}" placeholder="账号ID / 手机号" />
      </label>
      <button class="button primary" type="submit">查询</button>
      ${query ? `<button class="button" type="button" data-action="clear-user-filter">清空</button>` : ""}
    </form>
    <div class="users-workspace">
      <section id="users-list-card" class="card">
        <div class="card-head"><div class="card-title">用户列表</div><span class="small muted">${response.users.length} 条</span></div>
        <div class="table-wrap">${usersTable(response.users)}</div>
      </section>
      <section id="user-detail-drawer" class="user-detail-panel">
        ${pageState.userDetailID ? await userDetailCard(pageState.userDetailID) : emptyState("选择用户", "点击用户行或“详情”查看会员、额度、日志、反馈和订单。")}
      </section>
    </div>
  `;
}

async function entitlementsPage(): Promise<string> {
  const summary = await apiFetch<AdminEntitlementSummary>("/admin-api/v1/entitlements/summary");
  const summaryBlock = entitlementOverviewBlock(summary);
  const quotaOutboxBlock = await quotaConsumeOutboxBlock();
  if (currentAdminRole() === "auditor") {
    return `
      ${pageHead("会员额度", "查看会员总体情况和自动扣次对账队列。审计角色不开放单用户权益查询。", "entitlements")}
      ${summaryBlock}
      ${quotaOutboxBlock}
    `;
  }
  return userScopedPage({
    title: "会员额度",
    desc: "先看会员总体情况，再按账号ID查询单人权益、额度、加油包、补偿和自动扣次对账。",
    formID: "entitlements-form",
    inputName: "user_id",
    value: pageState.entitlementUserID,
    placeholder: "输入账号ID查询权益",
    content: async (userID) => {
      if (!userID) {
        return `${summaryBlock}${quotaOutboxBlock}`;
      }
      let detail: AdminUserDetail;
      try {
        detail = await fetchUserDetail(userID);
      } catch (error) {
        return `${summaryBlock}${quotaOutboxBlock}${errorBlock(error)}`;
      }
      return `
        ${summaryBlock}
        ${quotaOutboxBlock}
        <div class="grid two">
          <section class="card">
            <div class="card-head"><div class="card-title">权益概览</div></div>
            <div class="card-body">${entitlementSummary(detail)}</div>
          </section>
          <section class="card">
            <div class="card-head"><div class="card-title">升级补偿</div><span class="small muted">${detail.upgrade_credits.length} 条</span></div>
            <div class="table-wrap">${upgradeCreditsTable(detail.upgrade_credits)}</div>
          </section>
        </div>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">加油包明细</div><span class="small muted">${detail.topup_packs.length} 条</span></div>
          <div class="table-wrap">${topupPacksTable(detail.topup_packs)}</div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">订单 / 会员变更记录</div><span class="small muted">${detail.orders.length} 条</span></div>
          <div class="table-wrap">${ordersTable(detail.orders)}</div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">礼品卡兑换</div><span class="small muted">${detail.gift_cards.length} 张 / 尝试 ${detail.gift_card_attempts.length} 次</span></div>
          <div class="table-wrap">${giftCardTable(detail.gift_cards)}</div>
          <div class="table-wrap" style="margin-top:10px">${giftCardAttemptsTable(detail.gift_card_attempts)}</div>
        </section>
        <section class="card" style="margin-top:12px">
          <div class="card-head"><div class="card-title">扣次流水</div></div>
          <div class="table-wrap">${quotaLedgerTable(detail.quota_ledger)}</div>
        </section>
      `;
    },
  });
}

async function ordersPage(): Promise<string> {
  return userScopedPage({
    title: "订单",
    desc: "核查会员变更、支付订单和权益发放状态；只允许按已付款异常订单人工补发权益。",
    formID: "orders-form",
    inputName: "user_id",
    value: pageState.orderUserID,
    placeholder: "输入账号ID筛选；留空看最近订单",
    content: async (userID) => {
      const response = await fetchOrders(userID);
      const orders = response.orders || [];
      const summary = summarizeOrders(orders);
      return `
        <div class="grid kpi" style="margin-top:12px">
          ${kpi(userID ? "筛选订单" : "最近订单", orders.length, userID ? `账号 ${userID}` : "最近 50 条")}
          ${kpi("成功", summary.success, "只读统计")}
          ${kpi("失败", summary.failed, "只读统计")}
          ${kpi("正式支付金额", summary.amountText, "只统计非测试支付订单")}
        </div>
        <div class="grid two" style="margin-top:12px">
          ${notice("订单核查", "这里只按后端订单真相处理；不提供支付成功模拟、退款或随意改权益。已付款但权益未成功的订单，可由财务运营人工补发。", "info")}
          ${notice("测试单口径", "0.01 联调订单会保留为测试订单，不删除；后台汇总不把测试单计入正式支付金额。", "info")}
          ${notice("对账提醒", "生产放量前以支付宝账单和对账结果为准；看到 paid 但 grant_status 不是 success 时，先核对支付平台后台和服务端日志。", "warn")}
        </div>
        <section class="card">
          <div class="card-head"><div class="card-title">${userID ? "用户订单" : "最近订单"}</div><span class="small muted">${orders.length} 条</span></div>
          <div class="table-wrap">${ordersTable(orders)}</div>
        </section>
      `;
    },
  });
}

async function giftCardsPage(): Promise<string> {
  const canViewCodes = canViewGiftCardCodes();
  const cardParams = {
    limit: 100,
    batch_id: pageState.giftCardBatchID,
    status: pageState.giftCardStatus,
    user_id: pageState.giftCardUserID,
    code_suffix: pageState.giftCardCodeSuffix,
  };
  const attemptParams = {
    limit: 100,
    user_id: pageState.giftCardUserID,
    code_suffix: pageState.giftCardCodeSuffix,
    success: pageState.giftCardAttemptSuccess,
    failure_reason: pageState.giftCardAttemptReason,
  };
  const [summaryResult, batchesResult, cardsResult, attemptsResult] = await Promise.allSettled([
    apiFetch<{ summary: AdminGiftCardSummary }>("/admin-api/v1/gift-cards/summary"),
    apiFetch<{ batches: AdminGiftCardBatch[] }>("/admin-api/v1/gift-cards/batches?limit=50"),
    apiFetch<{ cards: AdminGiftCardEntry[] }>(`/admin-api/v1/gift-cards/cards${toQuery(cardParams)}`),
    apiFetch<{ attempts: AdminGiftCardAttempt[] }>(`/admin-api/v1/gift-cards/attempts${toQuery(attemptParams)}`),
  ]);
  rethrowGiftCardAuthError([summaryResult, batchesResult, cardsResult, attemptsResult]);
  const loadErrors = giftCardLoadErrors([
    ["汇总", summaryResult],
    ["批次", batchesResult],
    ["卡列表", cardsResult],
    ["兑换尝试", attemptsResult],
  ]);
  const summary = normalizeGiftCardSummary(summaryResult.status === "fulfilled" ? summaryResult.value.summary : undefined);
  const batches = batchesResult.status === "fulfilled" ? (batchesResult.value.batches ?? []) : [];
  const cards = cardsResult.status === "fulfilled" ? (cardsResult.value.cards ?? []) : [];
  const attempts = attemptsResult.status === "fulfilled" ? (attemptsResult.value.attempts ?? []) : [];
  return `
    ${pageHead("礼品卡", "礼品卡以后端批次、卡、兑换流水和审计为真相；主账号可查看和复制完整卡码。", "gift-cards")}
    ${loadErrors.length ? notice("部分数据暂时不可用", `以下区块加载失败：${loadErrors.join("、")}。页面已先展示可用数据，稍后刷新即可。`, "warn") : ""}
    <section class="grid kpi">
      ${kpi("可兑换卡", summary.redeemable_count, "当前可兑换")}
      ${kpi("已兑换", summary.redeemed_count, "权益已发放")}
      ${kpi("已作废", summary.void_count, "全量")}
      ${kpi("失败尝试", summary.failed_attempts_24h, "最近24小时")}
      ${kpi("批次数", summary.batch_count, "全量批次")}
      ${kpi("完整卡码", canViewCodes ? "可直接复制" : "未显示", lastGiftCardCodes.length ? "本次新生成在下方" : canViewCodes ? "列表可直接复制" : "当前账号只看尾号")}
    </section>
    <div class="grid two" style="margin-top:12px">
      ${notice("正式权益提醒", "生成后将产生真实可兑换权益。测试时请仅生成 1 张，并在 Android 设置里的“礼品卡”入口兑换后回到本页追溯。", "warn")}
      ${notice("当前口径", `礼品卡生成后就是 active 可兑换卡，用户兑换成功后权益立即发放；“可兑换卡”只排除已过期的 active 卡。全量 active 卡共 ${summary.active_count} 张。完整卡码不要写进备注、作废原因、审计说明或导出文件。`, "warn")}
    </div>
    <section class="card">
      <div class="card-head">
        <div class="card-title">生成礼品卡批次</div>
        <span class="small muted">完整卡码会加密保存，列表可查</span>
      </div>
      <div class="card-body">
        ${
          canManageGiftCards()
            ? `
              <form id="gift-card-create-form" class="filter-form">
                <label>批次名<input name="name" placeholder="例如：首批 Plus 月卡" /></label>
                <label>档位
                  <select name="tier">
                    <option value="plus">Plus</option>
                    <option value="pro">Pro</option>
                  </select>
                </label>
                <label>张数<input name="quantity" type="number" min="1" max="200" value="1" /></label>
                <label>天数<input name="duration_days" type="number" min="1" max="366" value="30" /></label>
                <label>备注<input name="note" placeholder="用途备注，不写手机号、完整卡码或密钥" /></label>
                <button class="button primary" type="submit">生成真实可兑换卡</button>
              </form>
              ${createdGiftCardCodesBlock(lastGiftCardCodes)}
            `
            : notice("只读追溯", "当前账号只能查看批次、卡尾号、兑换账号和失败原因，不开放生成或作废操作。", "info")
        }
      </div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">追溯筛选</div><span class="small muted">按批次、账号ID或卡尾号定位</span></div>
      <div class="card-body">${giftCardTraceFilterForm()}</div>
    </section>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head">
          <div class="card-title">批次</div><span class="small muted">${batches.length} 个</span>
        </div>
        <div class="table-wrap">${giftCardBatchesTable(batches)}</div>
      </section>
      <section class="card">
        <div class="card-head">
          <div class="card-title">卡与兑换</div><span class="small muted">${cards.length} 张</span>
        </div>
        <div class="table-wrap">${giftCardTable(cards)}</div>
      </section>
    </div>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">失败原因聚合</div><span class="small muted">最近7天</span></div>
      <div class="card-body">${giftCardFailureReasonsBlock(summary.failure_reasons)}</div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">兑换尝试</div><span class="small muted">${attempts.length} 条</span></div>
      <div class="table-wrap">${giftCardAttemptsTable(attempts)}</div>
    </section>
  `;
}

function rethrowGiftCardAuthError(results: PromiseSettledResult<unknown>[]): void {
  rethrowBlockingAdminError(results);
}

function giftCardLoadErrors(entries: [string, PromiseSettledResult<unknown>][]): string[] {
  return entries
    .filter(([, result]) => result.status === "rejected")
    .map(([label, result]) => {
      const reason = result.status === "rejected" ? errorMessage(result.reason) : "";
      return reason ? `${label}（${reason}）` : label;
    });
}

async function accountDeletionPage(): Promise<string> {
  const params = {
    limit: 100,
    status: pageState.accountDeletionStatus,
    user_id: pageState.accountDeletionUserID,
  };
  const response = await apiFetch<{ requests: AccountDeletionRequest[] }>(
    `/admin-api/v1/account-deletion-requests${toQuery(params)}`,
  );
  const requests = response.requests ?? [];
  const pendingCount = requests.filter((item) => item.status === "pending" || item.status === "processing").length;
  const overdueCount = requests.filter((item) => item.overdue).length;
  return `
    ${pageHead("注销申请", "用户在 App 内提交注销后会退出当前设备；后台按待处理队列核验并推进状态。", "account-deletion")}
    <section class="grid kpi">
      ${kpi("当前列表", requests.length, "最多最近 100 条")}
      ${kpi("当前列表待处理", pendingCount, "pending / processing")}
      ${kpi("当前列表超期", overdueCount, "超过 15 个工作日")}
      ${kpi("筛选状态", accountDeletionStatusLabel(pageState.accountDeletionStatus), pageState.accountDeletionUserID ? `账号 ${pageState.accountDeletionUserID}` : "全部账号")}
    </section>
    <div class="grid two" style="margin-top:12px">
      ${notice("处理口径", "App 端提交的是注销申请，并立即退出当前设备；后台处理前先核验会员、订单、礼品卡、反馈和合规留存要求。这里的“线下处理完成”表示人工核验和线下处理流程已完成，不代表系统已自动物理删除全部数据。", "warn")}
      ${notice("不要做什么", "处理备注按长度保存，不要主动写入密钥、礼品卡完整码或不必要的内部排障细节。正式物理删除 / 匿名化规则后续按合规方案细化。", "info")}
    </div>
    <section class="card">
      <div class="card-head"><div class="card-title">申请筛选</div><span class="small muted">默认看待处理</span></div>
      <div class="card-body">${accountDeletionFilterForm()}</div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">注销申请队列</div><span class="small muted">${requests.length} 条</span></div>
      <div class="table-wrap">${accountDeletionTable(requests)}</div>
    </section>
  `;
}

async function supportPage(): Promise<string> {
  const supportSinceMs = supportSinceRangeToMs(pageState.supportSinceRange);
  const params = {
    limit: 100,
    status: pageState.supportStatus,
    query: pageState.supportQuery,
    since_ms: supportSinceMs,
  };
  const response = await apiFetch<{ conversations: AdminSupportConversation[] }>(
    `/admin-api/v1/support/conversations${toQuery(params)}`,
  );
  if (pageState.supportUserID && !response.conversations.some((item) => item.user_id === pageState.supportUserID)) {
    pageState.supportUserID = "";
  }
  if (!pageState.supportUserID && response.conversations[0]) {
    pageState.supportUserID = response.conversations[0].user_id;
  }
  const selected = response.conversations.find((item) => item.user_id === pageState.supportUserID);
  const nextNeedsReply = response.conversations.find(
    (item) => item.user_id !== pageState.supportUserID && (item.needs_reply || item.status === "open"),
  );
  let messages: AdminSupportMessage[] = [];
  let searchMatchedMessages = 0;
  let messagesError = "";
  if (pageState.supportUserID) {
    try {
      const messagesResult = await fetchSupportMessages(pageState.supportUserID);
      messages = messagesResult.messages;
      searchMatchedMessages = messagesResult.search_matched_messages ?? 0;
    } catch (error) {
      messagesError = errorMessage(error);
    }
  }
  return `
    ${pageHead("帮助反馈", "按待回复、已处理、已关闭队列处理用户反馈；回复和状态动作都会写审计。", "support")}
    ${supportFilterForm()}
    <div class="split">
      <section id="support-list-card" class="card list-pane">
        <div class="card-head"><div class="card-title">会话队列</div><span class="small muted">${response.conversations.length} 条</span></div>
        ${supportConversationList(response.conversations)}
      </section>
      <section id="support-detail-card" class="card">
        <div class="card-head">
          <div class="card-title">会话详情</div>
          <span class="small muted">${escapeHTML(pageState.supportUserID || "未选择")}</span>
        </div>
        <div class="card-body">
          ${pageState.supportUserID ? supportMessagesBlock(pageState.supportUserID, messages, selected, messagesError, searchMatchedMessages, nextNeedsReply) : emptyState("未选择会话", "请先在左侧选择一条反馈会话。")}
        </div>
      </section>
    </div>
  `;
}

async function appLogsPage(): Promise<string> {
  const sinceMs = sinceFromWindow(pageState.appLogWindow);
  const params = readFilterState("app-log", { since_ms: sinceMs, limit: 100 });
  const response = await apiFetch<{ logs: ClientAppLogEntry[]; summary: ClientAppLogSummaryEntry[] }>(
    `/admin-api/v1/app-logs${toQuery(params)}`,
  );
  return `
    ${pageHead("App日志", "只展示后端清洗后的自动日志，不显示聊天正文、图片 URL、手机号或 token。", "app-logs")}
    ${logFilterForm("app-log-form", "app-log", pageState.appLogWindow)}
    ${appLogQuickFilters()}
    <div class="grid two">
      <section class="card">
        <div class="card-head"><div class="card-title">日志明细</div><span class="small muted">${response.logs.length} 条</span></div>
        <div class="table-wrap">${appLogsTable(response.logs)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">事件聚合</div></div>
        <div class="card-body">${appLogSummaryTable(response.summary)}</div>
      </section>
    </div>
  `;
}

async function todayAgriPage(): Promise<string> {
  const response = await apiFetch<{ cards: AdminDailyAgriEntry[] }>("/admin-api/v1/today-agri/cards?limit=14");
  const latestReady = response.cards.find(isPreviewableTodayAgriCard);
  return `
    ${pageHead("今日农情", "查看自动生成、人工发布和失败原因；人工发布优先，清晨自动生成兜底。", "today-agri")}
    ${latestReady ? todayAgriPreviewCard(latestReady) : notice("暂无可预览卡片", "最近记录里还没有可直接展示的 ready 卡片。可补跑当天，或查看下方失败原因。", "warn")}
    ${canManageTodayAgri() ? todayAgriManualPublishCard() : ""}
    <section class="card">
      <div class="card-head">
        <div class="card-title">最近卡片</div>
        ${canManageTodayAgri()
          ? `<button class="button primary" type="button" data-action="generate-today-agri">补跑今天</button>`
          : `<button class="button" type="button" disabled>只能查看</button>`}
      </div>
      <div class="table-wrap">${todayAgriTable(response.cards)}</div>
    </section>
  `;
}

function isPreviewableTodayAgriCard(row: AdminDailyAgriEntry): boolean {
  if (row.status !== "ready") return false;
  const error = (row.error || "").toLowerCase();
  if (error.includes("content_json_invalid") || error.includes("content_shape_invalid") || error.includes("sources_json_invalid") || error.includes("sources_shape_invalid")) {
    return false;
  }
  const itemCount = todayAgriItems(row.content).length;
  return itemCount >= 3;
}

async function appUpdatePage(): Promise<string> {
  const [configResult, eventsResult] = await Promise.allSettled([
    apiFetch<AdminAppUpdateConfig>("/admin-api/v1/app-update/android"),
    apiFetch<{ events: AdminAppUpdateEvent[] }>("/admin-api/v1/app-update/android/events?limit=20"),
  ]);
  rethrowBlockingAdminError([configResult, eventsResult]);
  if (configResult.status === "rejected") throw configResult.reason;
  const config = configResult.value;
  const events = eventsResult.status === "fulfilled" ? (eventsResult.value.events ?? []) : [];
  const eventsError = eventsResult.status === "rejected" ? errorMessage(eventsResult.reason) : "";
  return `
    ${pageHead("检查更新", "没有系统通知推送；App 启动会静默检查，用户也可手动检查。本页支持普通发布和停更。", "app-update")}
    ${eventsError ? notice("发布历史暂时不可用", `更新配置已正常展示，发布历史加载失败：${eventsError}。稍后刷新即可。`, "warn") : ""}
    <div class="grid two">
      <section class="card">
        <div class="card-head">
          <div class="card-title">Android 更新配置</div>
          ${statusPill(config.config_valid ? "valid" : "invalid")}
        </div>
        <div class="card-body">${appUpdateConfig(config)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">发布操作</div></div>
        <div class="card-body">
          ${canManageAppUpdate() ? appUpdateEditForm(config) : notice("只读配置", "当前账号只能看更新配置和物料状态，不开放发布或停更。", "info")}
        </div>
      </section>
    </div>
    <section class="card">
      <div class="card-head">
        <div class="card-title">发布历史</div>
        <span class="small muted">${events.length} 条</span>
      </div>
      <div class="table-wrap">${appUpdateEventsTable(events)}</div>
    </section>
  `;
}

function rethrowBlockingAdminError(results: PromiseSettledResult<unknown>[]): void {
  const blockingError = results.find(
    (result) =>
      result.status === "rejected" &&
      result.reason instanceof ApiError &&
      [401, 403, 428].includes(result.reason.status),
  ) as PromiseRejectedResult | undefined;
  if (blockingError) throw blockingError.reason;
}

async function auditPage(): Promise<string> {
  const sinceMs = sinceFromWindow(pageState.auditWindow);
  const params = readFilterState("audit", { since_ms: sinceMs, limit: 100 });
  const hasFilter = Object.values(filterState.get("audit") || {}).some((value) => value.trim() !== "") || pageState.auditWindow !== "24h";
  const response = await apiFetch<{ logs: AdminAuditLogEntry[] }>(
    `/admin-api/v1/audit-logs${toQuery(params)}`,
  );
  return `
    ${pageHead("审计", "查看后台登录、查询、回复和系统动作；详情字段由后端过滤敏感信息。", "audit")}
    <form class="filters" id="audit-form">
      ${timeWindowField("audit_window", pageState.auditWindow)}
      <label class="field"><span>action</span><input class="input" name="action" value="${escapeAttr(readInputValue("audit", "action"))}" /></label>
      <label class="field"><span>目标账号ID</span><input class="input" name="target_user_id" value="${escapeAttr(readInputValue("audit", "target_user_id"))}" /></label>
      <label class="field"><span>success</span>${selectHTML("success", readInputValue("audit", "success"), [["", "全部"], ["true", "成功"], ["false", "失败"]])}</label>
      <button class="button primary" type="submit">查询</button>
      ${hasFilter ? `<button class="button" type="button" data-action="clear-log-filter" data-filter-key="audit">清空</button>` : ""}
    </form>
    <section class="card">
      <div class="card-head"><div class="card-title">审计日志</div><span class="small muted">${response.logs.length} 条</span></div>
      <div class="table-wrap">${auditTable(response.logs)}</div>
    </section>
  `;
}

async function insightsPage(): Promise<string> {
  const report = await apiFetch<AdminInsights>("/admin-api/v1/insights");
  const day7 = report.windows.find((row) => row.key === "7d") || report.windows[0];
  const day30 = report.windows.find((row) => row.key === "30d") || report.windows[report.windows.length - 1];
  const quality = report.quality_signals;
  return `
    ${pageHead("产品洞察", "脱敏聚合用户增长、问诊、App质量、反馈主题和运营卡点。", "insights")}
    <section class="grid kpi">
      ${kpi("7天问诊", day7?.chat_rounds ?? 0, `${day7?.chat_users ?? 0} 位去重用户`)}
      ${kpi("7天图片问诊", day7?.image_chat_rounds ?? 0, `${formatPercent(day7?.image_chat_ratio ?? 0)} 占比`)}
      ${kpi("7天新增用户", day7?.new_users ?? 0, `${day7?.recent_auth_sessions ?? 0} 次新登录 session`)}
      ${kpi("7天 App 异常", (day7?.app_errors ?? 0) + (day7?.app_warns ?? 0), `error ${day7?.app_errors ?? 0} / warn ${day7?.app_warns ?? 0}`)}
      ${kpi("待回复反馈", quality.support_needs_reply ?? 0, `${quality.support_open ?? 0} 待回复 / ${quality.support_replied ?? 0} 已处理`)}
      ${kpi("可兑换礼品卡", quality.gift_card_redeemable ?? 0, `24h 失败 ${quality.gift_card_failed_attempts ?? 0}`)}
    </section>
    <div class="grid two">
      <section class="card">
        <div class="card-head">
          <div class="card-title">趋势窗口</div>
          <span class="small muted">${formatTime(report.now_ms)}</span>
        </div>
        <div class="card-body">
          <div class="table-wrap">${insightWindowTable(report.windows)}</div>
        </div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">质量信号</div></div>
        <div class="card-body">
          <table class="table">
            <tbody>
              ${metricRow("今日农情", dailyAgriStatusText(quality.daily_agri_status || "missing"))}
              ${metricRow("农情更新时间", formatTime(quality.daily_agri_updated_at))}
              ${metricRow("检查更新", quality.app_update_enabled ? (quality.app_update_ready ? "已启用，物料已齐" : "已启用，物料未齐") : "未下发")}
              ${metricRow("更新版本", quality.app_update_version_code ? `${quality.app_update_version_name || ""} (${quality.app_update_version_code})` : "未配置")}
              ${metricRow("反馈队列", `${quality.support_open ?? 0} 待回复 / ${quality.support_replied ?? 0} 已处理 / ${quality.support_closed ?? 0} 已关闭`)}
              ${metricRow("礼品卡异常", `${quality.gift_card_failed_attempts ?? 0} 次 24h 失败尝试`)}
            </tbody>
          </table>
          ${quality.daily_agri_error ? notice("今日农情错误", quality.daily_agri_error, "warn") : ""}
        </div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head">
          <div class="card-title">反馈主题命中</div>
          <span class="small muted">since ${formatTime(report.support_category_since_ms)}</span>
        </div>
        <div class="card-body">
          ${breakdownBars(report.support_categories, "最近30天用户反馈正文固定关键词命中；同一条反馈可能命中多个主题。")}
        </div>
      </section>
      <section class="card">
        <div class="card-head">
          <div class="card-title">App 事件分类</div>
          <span class="small muted">since ${formatTime(report.app_event_category_since_ms)}</span>
        </div>
        <div class="card-body">
          ${breakdownBars(report.app_event_categories, "最近7天自动日志 event 固定分类，不展示 message、attrs 或用户输入。")}
        </div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head">
          <div class="card-title">Top App 事件</div>
          <span class="small muted">since ${formatTime(report.app_event_category_since_ms)}</span>
        </div>
        <div class="card-body">${appLogSummaryTable(report.top_app_events || [])}</div>
      </section>
      <section class="card">
        <div class="card-head">
          <div class="card-title">礼品卡失败原因</div>
          <span class="small muted">since ${formatTime(report.gift_card_reason_since_ms)}</span>
        </div>
        <div class="card-body">${giftCardFailureReasonTable(report.gift_card_failure_reasons || [])}</div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">隐私边界</div></div>
        <div class="card-body">
          ${(report.notes || []).map((note) => notice(note.title, note.body, note.level)).join("")}
        </div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">下一步洞察能力</div></div>
        <div class="card-body">
          ${notice("后续增强", "再补产品洞察日报、人工标签、处理状态、代表短摘和来源数量；代表短摘也必须先脱敏，不直接铺完整聊天或反馈正文。", "info")}
          ${day30 ? notice("近30天概况", `最近30天 ${day30.chat_rounds} 轮问诊、${day30.support_messages} 条用户反馈、${day30.auth_failures} 条登录排障信号。`, "info") : ""}
        </div>
      </section>
    </div>
  `;
}

async function healthPage(): Promise<string> {
  const overview = await apiFetch<AdminOverview>("/admin-api/v1/overview");
  return `
    ${pageHead("服务健康", "通过后台 overview 返回的健康字段查看 API、智能分析、认证、Redis 和上传存储状态。", "health")}
    <div class="grid three">
      ${Object.entries(overview.health)
        .map(([key, value]) => `
          <section class="card kpi-card">
            <div class="kpi-label">${escapeHTML(labelFor(key))}</div>
            <div class="kpi-value" style="font-size:18px">${healthValuePill(key, value)}</div>
            <div class="kpi-foot">${healthHint(key, value)}</div>
          </section>
        `)
        .join("")}
    </div>
    <div style="margin-top:12px">${planningNotice("服务告警巡检", "服务 5xx、慢请求、网关异常、公网黑盒探测、今日农情失败、扣次补偿异常、记忆待补偿状态异常和智能问诊 / 认证配置错误按最近严格脚本巡检接入告警规则、邮件行动策略和最小仪表盘，资源水位另走云监控邮件；本页不实时读取阿里云告警规则，剩余重点是首封告警邮件送达确认和更细趋势。")}</div>
  `;
}

async function accountSecurityPage(): Promise<string> {
  const user = auth?.admin_user;
  if (!user) return errorBlock(new Error("missing_admin_user"));
  return `
    ${pageHead("账号安全", "查看当前后台账号并更新登录密码。", "account")}
    ${user.must_change_password ? notice("需要先更新后台密码", "当前账号被标记为必须改密。改完密码后，其他后台页面会恢复访问。", "warn") : ""}
    <div class="grid two">
      <section class="card">
        <div class="card-head"><div class="card-title">当前账号</div></div>
        <div class="card-body">
          <table class="table">
            <tbody>
              ${metricRow("账号", user.username)}
              ${metricRow("显示名", user.display_name || user.username)}
              ${metricRow("后台身份", roleLabel(user.role))}
              ${metricRow("状态", user.enabled ? "启用" : "停用")}
              ${metricRow("需要改密", user.must_change_password ? "是" : "否")}
              ${metricRow("上次登录", user.last_login_at ? formatTime(user.last_login_at) : "未返回")}
            </tbody>
          </table>
        </div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">修改密码</div></div>
        <div class="card-body">
          <form id="admin-password-form" class="login-form" autocomplete="on">
            <label class="field">
              <span>当前密码</span>
              <input class="input" name="current_password" type="password" autocomplete="current-password" required />
            </label>
            <label class="field">
              <span>新密码</span>
              <input class="input" name="new_password" type="password" autocomplete="new-password" minlength="8" required />
            </label>
            <label class="field">
              <span>确认新密码</span>
              <input class="input" name="confirm_password" type="password" autocomplete="new-password" minlength="8" required />
            </label>
            <div class="muted small">至少 8 个字符。密码不会写入日志、审计详情或前端持久化存储。</div>
            <button class="button primary" type="submit">更新密码</button>
          </form>
        </div>
      </section>
    </div>
  `;
}

async function handleSubmit(form: HTMLFormElement): Promise<void> {
  if (form.id === "login-form") {
    await submitLogin(form);
    return;
  }
  if (form.id === "admin-password-form") {
    await submitAdminPasswordChange(form);
    return;
  }
  if (form.id === "users-filter-form") {
    pageState.userQuery = formValue(form, "query");
    pageState.userDetailID = "";
    await render();
    return;
  }
  if (form.id === "global-search-form") {
    const query = formValue(form, "query");
    if (!query) return;
    pageState.userQuery = query;
    pageState.userDetailID = "";
    if (isRouteVisible("users") && activeRoute !== "users") {
      pendingScrollTarget = "users-list-card";
      location.hash = "users";
    } else {
      await render();
      scrollElementIntoViewOnMobile("users-list-card");
    }
    return;
  }
  if (form.id === "entitlements-form") {
    pageState.entitlementUserID = formValue(form, "user_id");
    await render();
    return;
  }
  if (form.id === "orders-form") {
    pageState.orderUserID = formValue(form, "user_id");
    await render();
    return;
  }
  if (form.id === "gift-card-create-form") {
    await submitGiftCardBatch(form);
    return;
  }
  if (form.id === "today-agri-manual-form") {
    await submitManualTodayAgriCard(form);
    return;
  }
  if (form.id === "gift-card-filter-form") {
    pageState.giftCardBatchID = formValue(form, "batch_id");
    pageState.giftCardStatus = formValue(form, "status");
    pageState.giftCardUserID = formValue(form, "user_id");
    pageState.giftCardCodeSuffix = formValue(form, "code_suffix");
    pageState.giftCardAttemptSuccess = formValue(form, "success");
    pageState.giftCardAttemptReason = formValue(form, "failure_reason");
    await render();
    return;
  }
  if (form.id === "account-deletion-filter-form") {
    pageState.accountDeletionStatus = formValue(form, "status");
    pageState.accountDeletionUserID = formValue(form, "user_id");
    await render();
    return;
  }
  if (form.id === "support-reply-form") {
    await submitSupportReply(form);
    return;
  }
  if (form.id === "support-filter-form") {
    pageState.supportQuery = formValue(form, "query");
    pageState.supportMessageQuery = pageState.supportQuery;
    pageState.supportStatus = pageState.supportQuery.trim() ? "" : formValue(form, "status");
    pageState.supportSinceRange = pageState.supportQuery.trim() ? "all" : formValue(form, "since_range") || "30d";
    pageState.supportUserID = "";
    await render();
    return;
  }
  if (form.id === "app-log-form") {
    captureFilterState(form, "app-log");
    pageState.appLogWindow = formValue(form, "app_log_window") || "24h";
    await render();
    return;
  }
  if (form.id === "app-update-form") {
    await submitAppUpdate(form);
    return;
  }
  if (form.id === "audit-form") {
    captureFilterState(form, "audit");
    pageState.auditWindow = formValue(form, "audit_window") || "24h";
    await render();
  }
}

async function handleAction(button: HTMLElement): Promise<void> {
  const action = button.dataset.action || "";
  if (action === "route") {
    const route = button.dataset.route as RouteKey;
    if (auth?.admin_user.must_change_password && route !== "account") {
      activeRoute = "account";
      location.hash = "account";
      await render();
      return;
    }
    if (isRouteVisible(route)) {
      location.hash = route;
    }
    return;
  }
  if (action === "open-app-log-filter") {
    openAppLogsWithFilter({
      userID: button.dataset.userId || "",
      event: button.dataset.event || "",
      eventPrefix: button.dataset.eventPrefix || "",
      level: button.dataset.level || "",
      window: button.dataset.window || "24h",
    });
    return;
  }
  if (action === "refresh") {
    if (activeRoute === "gift-cards") lastGiftCardCodes = [];
    await render();
    return;
  }
  if (action === "logout") {
    await logout();
    return;
  }
  if (action === "toggle-privacy-mask") {
    privacyMaskEnabled = !privacyMaskEnabled;
    await render();
    showTransientNotice(privacyMaskEnabled ? "已隐藏敏感信息" : "已显示敏感信息");
    return;
  }
  if (action === "toggle-admin-mode") {
    adminSimpleMode = !adminSimpleMode;
    saveAdminSimpleMode(adminSimpleMode);
    await render();
    showTransientNotice(adminSimpleMode ? "已切换到简单模式" : "已显示全部功能");
    return;
  }
  if (action === "load-user-detail") {
    const userID = button.dataset.userId || "";
    pageState.userDetailID = userID;
    if (userID && isRouteVisible("users") && activeRoute !== "users") {
      pageState.userQuery = userID;
      pendingScrollTarget = "user-detail-drawer";
      location.hash = "users";
    } else {
      await render();
      scrollElementIntoViewOnMobile("user-detail-drawer");
    }
    return;
  }
  if (action === "close-user-detail") {
    pageState.userDetailID = "";
    await render();
    scrollElementIntoViewOnMobile("users-list-card");
    return;
  }
  if (action === "clear-user-filter") {
    pageState.userQuery = "";
    pageState.userDetailID = "";
    await render();
    return;
  }
  if (action === "support-select") {
    pageState.supportUserID = button.dataset.userId || "";
    await render();
    scrollElementIntoViewOnMobile("support-detail-card");
    return;
  }
  if (action === "support-back-list") {
    scrollElementIntoViewOnMobile("support-list-card");
    return;
  }
  if (action === "open-support-user") {
    if (!isRouteVisible("support")) {
      showTransientNotice("当前账号不能查看帮助反馈");
      return;
    }
    const userID = button.dataset.userId || "";
    if (!userID) return;
    pageState.supportUserID = userID;
    pageState.supportQuery = userID;
    pageState.supportMessageQuery = "";
    pageState.supportStatus = "";
    pageState.supportSinceRange = "all";
    if (activeRoute === "support") {
      await render();
      scrollElementIntoViewOnMobile("support-detail-card");
    } else {
      pendingScrollTarget = "support-detail-card";
      location.hash = "support";
    }
    return;
  }
  if (action === "support-next-open") {
    const userID = button.dataset.userId || "";
    if (!userID) return;
    pageState.supportUserID = userID;
    await render();
    scrollElementIntoViewOnMobile("support-detail-card");
    return;
  }
  if (action === "support-template") {
    const text = button.dataset.template || "";
    const textarea = document.querySelector<HTMLTextAreaElement>("#support-reply-form textarea[name='body']");
    if (!textarea || !text) return;
    textarea.value = textarea.value.trim() ? `${textarea.value.trim()}\n${text}` : text;
    textarea.focus();
    return;
  }
  if (action === "clear-log-filter") {
    const key = button.dataset.filterKey || "";
    if (key === "app-log") {
      filterState.delete("app-log");
      pageState.appLogWindow = "24h";
      await render();
    }
    if (key === "audit") {
      filterState.delete("audit");
      pageState.auditWindow = "24h";
      await render();
    }
    return;
  }
  if (action === "clear-support-filter") {
    pageState.supportStatus = "open";
    pageState.supportQuery = "";
    pageState.supportMessageQuery = "";
    pageState.supportSinceRange = "30d";
    pageState.supportUserID = "";
    await render();
    return;
  }
  if (action === "support-status") {
    if (!canManageSupport()) return;
    await updateSupportConversationStatus(button.dataset.userId || "", button.dataset.status || "", button);
    return;
  }
  if (action === "clear-gift-card-codes") {
    lastGiftCardCodes = [];
    await render();
    return;
  }
  if (action === "copy-text") {
    await copyText(button.dataset.copy || "");
    return;
  }
  if (action === "generate-today-agri") {
    await generateTodayAgriCard(button);
    return;
  }
  if (action === "disable-app-update") {
    await disableAppUpdate(button);
    return;
  }
  if (action === "clear-gift-card-filter") {
    pageState.giftCardBatchID = "";
    pageState.giftCardStatus = "";
    pageState.giftCardUserID = "";
    pageState.giftCardCodeSuffix = "";
    pageState.giftCardAttemptSuccess = "";
    pageState.giftCardAttemptReason = "";
    await render();
    return;
  }
  if (action === "gift-card-reason-filter") {
    pageState.giftCardAttemptSuccess = "failed";
    pageState.giftCardAttemptReason = button.dataset.reason || "";
    await render();
    return;
  }
  if (action === "void-gift-card") {
    if (!canManageGiftCards()) return;
    await voidGiftCard(button.dataset.cardId || "", button);
    return;
  }
  if (action === "grant-payment-order") {
    if (!canManagePaymentGrants()) return;
    await grantPaymentOrder(button.dataset.outTradeNo || "", button);
    return;
  }
  if (action === "account-deletion-status") {
    if (!canManageAccountDeletion()) return;
    await updateAccountDeletionStatus(button.dataset.requestId || "", button.dataset.status || "", button);
  }
}

async function submitLogin(form: HTMLFormElement): Promise<void> {
  const username = formValue(form, "username");
  const password = formValue(form, "password");
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    auth = await apiFetch<AuthPayload>("/admin-api/v1/auth/login", {
      method: "POST",
      json: { username, password },
    });
    setStoredAuth(auth);
    activeRoute = auth.admin_user.must_change_password ? "account" : routeFromHash();
    await render();
  } catch (error) {
    renderLogin(`登录失败：${errorMessage(error)}`);
  } finally {
    if (button) button.disabled = false;
  }
}

async function submitAdminPasswordChange(form: HTMLFormElement): Promise<void> {
  const currentPassword = formValue(form, "current_password");
  const newPassword = formValue(form, "new_password");
  const confirmPassword = formValue(form, "confirm_password");
  if (Array.from(newPassword).length < 8) {
    window.alert("新密码至少 8 个字符。");
    return;
  }
  if (newPassword !== confirmPassword) {
    window.alert("两次输入的新密码不一致。");
    return;
  }
  if (newPassword === currentPassword) {
    window.alert("新密码不能和当前密码相同。");
    return;
  }
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    auth = await apiFetch<AuthPayload>("/admin-api/v1/auth/change-password", {
      method: "POST",
      json: { current_password: currentPassword, new_password: newPassword },
    });
    setStoredAuth(auth);
    activeRoute = "account";
    window.alert("密码已更新。");
    await render();
  } catch (error) {
    window.alert(`更新失败：${errorMessage(error)}`);
  } finally {
    if (button) button.disabled = false;
  }
}

async function logout(): Promise<void> {
  try {
    await apiFetch("/admin-api/v1/auth/logout", { method: "POST" });
  } catch {
    // Logout should still clear local state when the server session has already expired.
  }
  auth = null;
  setStoredAuth(null);
  clearSensitiveAdminState();
  renderLogin();
}

function scrollElementIntoViewOnMobile(elementID: string): void {
  if (!window.matchMedia("(max-width: 900px)").matches) return;
  requestAnimationFrame(() => {
    document.getElementById(elementID)?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
}

async function submitSupportReply(form: HTMLFormElement): Promise<void> {
  if (!canManageSupport()) return;
  const userID = formValue(form, "user_id");
  const body = formValue(form, "body");
  if (!userID || !body) return;
  if (
    !window.confirm(
      "确认发送这条客服回复？回复会展示在用户 App 的帮助与反馈里。手机号、订单号、礼品卡码等排障信息可以发送，但不要发送后台密钥、token 或内部排障细节。",
    )
  ) {
    return;
  }
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    await apiFetch("/admin-api/v1/support/messages", {
      method: "POST",
      json: { user_id: userID, body, images: [] },
    });
    pageState.supportUserID = userID;
    await render();
  } catch (error) {
    window.alert(`发送失败：${supportReplyErrorMessage(error)}`);
  } finally {
    if (button) button.disabled = false;
  }
}

async function submitGiftCardBatch(form: HTMLFormElement): Promise<void> {
  if (!canManageGiftCards()) return;
  const name = formValue(form, "name");
  const tier = formValue(form, "tier") || "plus";
  const quantity = Number(formValue(form, "quantity") || "1");
  const durationDays = Number(formValue(form, "duration_days") || "30");
  const note = formValue(form, "note");
  const tierLabel = tier === "pro" ? "Pro" : "Plus";
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 200) {
    window.alert("张数必须是 1 到 200 的整数。");
    return;
  }
  if (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 366) {
    window.alert("天数必须是 1 到 366 的整数。");
    return;
  }
  if (
    !window.confirm(
      `确认生成 ${quantity} 张 ${tierLabel} ${durationDays} 天礼品卡？\n\n这些卡码一旦发出即可兑换真实会员权益。后台会加密保存完整码并可直接查看，请确认数量和档位无误。`,
    )
  ) {
    return;
  }
  const confirmationText = `${quantity} ${tierLabel} ${durationDays}`;
  const typedConfirmation = window.prompt(`请输入 ${confirmationText} 确认生成真实礼品卡。`);
  if (typedConfirmation === null) return;
  if (typedConfirmation.trim().replace(/\s+/g, " ").toLowerCase() !== confirmationText.toLowerCase()) {
    window.alert("输入的确认内容不一致，已取消生成。");
    return;
  }
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    const response = await apiFetch<{ codes: AdminGiftCardCreatedCode[] }>("/admin-api/v1/gift-cards/batches", {
      method: "POST",
      json: { name, tier, quantity, duration_days: durationDays, note, confirmation: typedConfirmation.trim() },
    });
    lastGiftCardCodes = response.codes || [];
    await render();
  } catch (error) {
    lastGiftCardCodes = [];
    window.alert(`生成失败：${errorMessage(error)}`);
  } finally {
    if (button) button.disabled = false;
  }
}

async function submitAppUpdate(form: HTMLFormElement): Promise<void> {
  if (!canManageAppUpdate()) return;
  const latestVersionCode = Number(formValue(form, "latest_version_code") || "0");
  const latestVersionName = formValue(form, "latest_version_name");
  const apkURL = formValue(form, "apk_url");
  const apkSHA256 = formValue(form, "apk_sha256");
  const releaseNotes = formValue(form, "release_notes");
  const fileSizeBytes = Number(formValue(form, "file_size_bytes") || "0");
  const enabled = form.querySelector<HTMLInputElement>("input[name='enabled']")?.checked === true;
  const forceUpdate = false;
  if ((latestVersionCode || latestVersionName || apkURL || apkSHA256 || releaseNotes || fileSizeBytes || forceUpdate) && (!Number.isInteger(latestVersionCode) || latestVersionCode < 1)) {
    window.alert("versionCode 必须是大于 0 的整数。");
    return;
  }
  if (apkURL && !isOfficialApkURL(apkURL)) {
    window.alert("APK URL 必须是 download.nongjiqiancha.cn 下的稳定正式 .apk 链接。");
    return;
  }
  if (apkURL && isInternalTestApkURL(apkURL)) {
    window.alert("这是内部测试包链接，不能配置到正式检查更新。测试包请走临时下载链接。");
    return;
  }
  if (apkURL && isShortLivedSignedApkURL(apkURL)) {
    window.alert("这是带短期签名参数的临时链接，不能配置到正式检查更新。请使用稳定的正式包下载地址。");
    return;
  }
  if (apkSHA256 && !/^[0-9a-fA-F]{64}$/.test(apkSHA256.replace(/:/g, ""))) {
    window.alert("SHA-256 必须是 64 位十六进制。");
    return;
  }
  if (fileSizeBytes && (!Number.isInteger(fileSizeBytes) || fileSizeBytes < 1)) {
    window.alert("文件大小必须是正整数。");
    return;
  }
  if (enabled && (!apkURL || !apkSHA256 || !fileSizeBytes || latestVersionCode < 1)) {
    window.alert("启用更新前，必须补齐 versionCode、HTTPS APK、SHA-256 和文件大小。");
    return;
  }
  const confirmText = enabled
    ? `确认对外启用普通检查更新？\n\nversionCode: ${latestVersionCode}\nversionName: ${latestVersionName || "未填写"}\nAPK: ${apkURL}\nSHA-256: ${apkSHA256}\n文件大小: ${fileSizeBytes} bytes\n\n请确认已经获得业务负责人正式发版口令，并已跑 check-app-update-release-match.ps1 核对 APK 包名、签名、versionCode、SHA-256 和文件大小。保存后，旧版 App 启动时会静默检查，用户手动检查也会拿到这份配置。更新说明留空时默认显示“优化使用体验。”`
    : "确认保存为停更状态？停更后，App 启动静默检查和用户手动检查都不会拿到新包。";
  if (!window.confirm(confirmText)) {
    return;
  }
  const typedConfirmation = window.prompt(enabled ? `请输入 ${latestVersionCode} 确认已获正式发版口令，并对外启用这次更新。` : "请输入 停更 确认保存为停更状态。");
  if (typedConfirmation === null) return;
  if (typedConfirmation.trim() !== (enabled ? String(latestVersionCode) : "停更")) {
    window.alert(enabled ? "输入的 versionCode 不一致，已取消发布。" : "未输入“停更”，已取消保存。");
    return;
  }
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    await apiFetch<AdminAppUpdateConfig>("/admin-api/v1/app-update/android", {
      method: "POST",
      json: {
        enabled,
        latest_version_code: latestVersionCode,
        latest_version_name: latestVersionName,
        apk_url: apkURL,
        apk_sha256: apkSHA256,
        release_notes: releaseNotes,
        force_update: forceUpdate,
        file_size_bytes: fileSizeBytes,
        confirmation: typedConfirmation.trim(),
      },
    });
    await render();
  } catch (error) {
    window.alert(appUpdateSaveErrorMessage(error));
  } finally {
    if (button) button.disabled = false;
  }
}

async function disableAppUpdate(button: HTMLElement): Promise<void> {
  if (!canManageAppUpdate()) return;
  const latestVersionCode = Number(button.dataset.latestVersionCode || "0");
  const latestVersionName = button.dataset.latestVersionName || "";
  const apkURL = button.dataset.apkUrl || "";
  const apkSHA256 = button.dataset.apkSha256 || "";
  const releaseNotes = button.dataset.releaseNotes || "";
  const fileSizeBytes = Number(button.dataset.fileSizeBytes || "0");
  if (!window.confirm("确认停掉当前更新？停更后，用户点“检查更新”将不会再拿到这个新包。")) {
    return;
  }
  const typedConfirmation = window.prompt("请输入 停更 确认关闭当前更新。");
  if (typedConfirmation === null) return;
  if (typedConfirmation.trim() !== "停更") {
    window.alert("未输入“停更”，已取消操作。");
    return;
  }
  await withButtonBusy(button, "停更中", async () => {
    await apiFetch<AdminAppUpdateConfig>("/admin-api/v1/app-update/android", {
      method: "POST",
      json: {
        enabled: false,
        latest_version_code: latestVersionCode,
        latest_version_name: latestVersionName,
        apk_url: apkURL,
        apk_sha256: apkSHA256,
        release_notes: releaseNotes,
        force_update: false,
        file_size_bytes: fileSizeBytes,
        confirmation: typedConfirmation.trim(),
      },
    });
    await render();
  }, "停更失败");
}

async function generateTodayAgriCard(button?: HTMLElement): Promise<void> {
  if (!canManageTodayAgri()) return;
  if (!window.confirm("确认补跑今天的今日农情？已有 ready 或人工锁定内容时，系统会直接复用，不会覆盖。")) {
    return;
  }
  await withButtonBusy(button, "补跑中", async () => {
    const result = await apiFetch<{ status?: string; item_count?: number; has_card?: boolean }>("/admin-api/v1/today-agri/generate", {
      method: "POST",
    });
    const status = String(result.status || "").toLowerCase();
    const label = status || "unknown";
    const suffix = result.has_card ? `，当前 ${result.item_count || 0} 条` : "";
    window.alert(`今日农情处理完成：${label}${suffix}`);
    await render();
  }, "补跑失败");
}

async function submitManualTodayAgriCard(form: HTMLFormElement): Promise<void> {
  if (!canManageTodayAgri()) return;
  const dayCN = formValue(form, "day_cn").replaceAll("-", "");
  const items = [1, 2, 3].map((index) => ({
    title: formValue(form, `item_title_${index}`),
    summary: formValue(form, `item_summary_${index}`),
    source: formValue(form, `item_source_${index}`),
  }));
  const confirmation = formValue(form, "confirmation");
  const expectedConfirmation = `人工发布 ${dayCN}`;
  if (!/^\d{8}$/.test(dayCN)) {
    window.alert("日期请填写 8 位数字，例如 20260619。");
    return;
  }
  const missingIndex = items.findIndex((item) => !item.title || !item.summary);
  if (missingIndex >= 0) {
    window.alert(`第 ${missingIndex + 1} 条标题和摘要都要填写。`);
    return;
  }
  if (confirmation !== expectedConfirmation) {
    window.alert(`请在确认文字里输入：${expectedConfirmation}`);
    return;
  }
  const titles = items.map((item, index) => `${index + 1}. ${item.title}`).join("\n");
  if (!window.confirm(`确认人工发布 ${dayCN} 今日农情，并锁定不让自动任务覆盖？\n\n${titles}`)) {
    return;
  }
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  await withButtonBusy(button ?? undefined, "发布中", async () => {
    const result = await apiFetch<{ status?: string; item_count?: number; manual_locked?: boolean }>("/admin-api/v1/today-agri/manual", {
      method: "POST",
      json: { day_cn: dayCN, items, confirmation },
    });
    window.alert(`人工发布完成：${result.status || "ready"}，${result.item_count || 0} 条，已锁定。`);
    await render();
  }, "人工发布失败");
}

async function updateAccountDeletionStatus(requestID: string, status: string, button?: HTMLElement): Promise<void> {
  if (!requestID || !status) return;
  const labels: Record<string, string> = {
    processing: "标记为处理中",
    completed: "标记为线下处理完成",
    rejected: "驳回申请",
    cancelled: "取消申请",
  };
  let note = "";
  if (["completed", "rejected", "cancelled"].includes(status)) {
    const input = window.prompt("处理备注必填。可以写必要的手机号、订单号、礼品卡码等处理信息；不要写后台密钥、token 或内部敏感信息。");
    if (input === null) return;
    note = input.trim();
    if (!note) {
      window.alert("流程收口、驳回或取消注销申请时，处理备注不能为空。");
      return;
    }
  }
  const confirmText = [
    `确认${labels[status] || "更新状态"}？`,
    `申请ID：${requestID}`,
    `目标状态：${status}`,
  ].join("\n");
  if (!window.confirm(confirmText)) return;
  await withButtonBusy(button, "更新中", async () => {
    await apiFetch("/admin-api/v1/account-deletion-requests/status", {
      method: "POST",
      json: { request_id: requestID, status, note },
    });
    await render();
  }, "更新失败");
}

async function voidGiftCard(cardID: string, button?: HTMLElement): Promise<void> {
  if (!cardID) return;
  const reason = window.prompt("请输入作废原因。只写运营原因，不要写完整卡码、手机号或密钥。");
  if (reason === null) return;
  const trimmedReason = reason.trim();
  if (!trimmedReason) {
    window.alert("作废原因不能为空。");
    return;
  }
  if (!window.confirm("确认作废这张未兑换礼品卡？作废后不能再被用户兑换。")) {
    return;
  }
  const typedConfirmation = window.prompt("请输入 作废 确认作废这张礼品卡。");
  if (typedConfirmation === null) return;
  if (typedConfirmation.trim() !== "作废") {
    window.alert("未输入“作废”，已取消操作。");
    return;
  }
  await withButtonBusy(button, "作废中", async () => {
    await apiFetch("/admin-api/v1/gift-cards/void", {
      method: "POST",
      json: { card_id: cardID, reason: trimmedReason, confirmation: typedConfirmation.trim() },
    });
    lastGiftCardCodes = [];
    await render();
  }, "作废失败");
}

async function grantPaymentOrder(outTradeNo: string, button?: HTMLElement): Promise<void> {
  if (!outTradeNo) return;
  const suffix = outTradeNo.slice(-8).toUpperCase();
  const reason = window.prompt("请输入补发原因。只写必要处理说明，不要写完整手机号、密钥、token 或支付参数全文。");
  if (reason === null) return;
  const trimmedReason = reason.trim();
  if (!trimmedReason) {
    window.alert("补发原因不能为空。");
    return;
  }
  if (!window.confirm(`确认按已付款订单尾号 ${suffix} 补发权益？\n\n后台只会对 paid 且权益未成功的支付订单执行补发。`)) {
    return;
  }
  const typedConfirmation = window.prompt("请输入 补发 确认人工补发权益。");
  if (typedConfirmation === null) return;
  if (typedConfirmation.trim() !== "补发") {
    window.alert("未输入“补发”，已取消操作。");
    return;
  }
  await withButtonBusy(button, "补发中", async () => {
    await apiFetch("/admin-api/v1/orders/grant", {
      method: "POST",
      json: { out_trade_no: outTradeNo, reason: trimmedReason, confirmation: "补发" },
    });
    await render();
    showTransientNotice("已补发权益");
  }, "补发失败");
}

async function updateSupportConversationStatus(userID: string, status: string, button?: HTMLElement): Promise<void> {
  if (!userID || !status) return;
  const labels: Record<string, string> = {
    open: "重新打开为待回复",
    replied: "标记为已处理/无需回复",
    closed: "关闭会话",
  };
  let note = "";
  if (status === "replied" || status === "closed") {
    const input = window.prompt(
      status === "replied"
        ? "处理备注必填。可以写手机号、订单号、礼品卡码等排障信息；不要写后台密钥、token 或内部排障细节。"
        : "关闭备注必填。请写清无需继续回复或线下处理原因；可以写手机号、订单号、礼品卡码等排障信息，不要写后台密钥、token 或内部排障细节。",
    );
    if (input === null) return;
    note = input.trim();
    if ((status === "replied" || status === "closed") && !note) {
      window.alert(status === "closed" ? "关闭会话时，关闭备注不能为空。" : "标记为已处理/无需回复时，处理备注不能为空。");
      return;
    }
  }
  if (!window.confirm(`确认${labels[status] || "更新状态"}？`)) return;
  await withButtonBusy(button, "更新中", async () => {
    await apiFetch("/admin-api/v1/support/conversations/status", {
      method: "POST",
      json: { user_id: userID, status, note },
    });
    pageState.supportUserID = userID;
    await render();
  }, "更新失败");
}

async function copyText(text: string): Promise<void> {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    showTransientNotice("已复制");
  } catch {
    window.prompt("复制失败，请手动复制：", text);
  }
}

function showTransientNotice(message: string): void {
  const id = "admin-transient-notice";
  const existing = document.getElementById(id);
  if (existing) existing.remove();
  const node = document.createElement("div");
  node.id = id;
  node.className = "transient-notice";
  node.setAttribute("role", "status");
  node.textContent = message;
  document.body.appendChild(node);
  window.setTimeout(() => node.remove(), 1600);
}

async function withButtonBusy(
  target: HTMLElement | undefined,
  busyLabel: string,
  action: () => Promise<void>,
  failureTitle: string,
): Promise<void> {
  const button = target instanceof HTMLButtonElement ? target : undefined;
  if (button?.dataset.busy === "true") return;
  const previousText = button?.textContent || "";
  if (button) {
    button.dataset.busy = "true";
    button.disabled = true;
    button.textContent = busyLabel;
  }
  try {
    await action();
  } catch (error) {
    window.alert(`${failureTitle}：${actionErrorMessage(error)}`);
  } finally {
    if (button) {
      button.disabled = false;
      button.textContent = previousText;
      delete button.dataset.busy;
    }
  }
}

async function fetchUserDetail(userID: string): Promise<AdminUserDetail> {
  const detail = await apiFetch<AdminUserDetail>(`/admin-api/v1/users/detail${toQuery({ user_id: userID })}`);
  return normalizeUserDetail(detail);
}

async function fetchOrders(userID: string): Promise<AdminOrdersResponse> {
  const response = await apiFetch<AdminOrdersResponse>(`/admin-api/v1/orders${toQuery({ user_id: userID, limit: 50 })}`);
  return {
    ...response,
    orders: response.orders ?? [],
  };
}

function normalizeUserDetail(detail: AdminUserDetail): AdminUserDetail {
  return {
    ...detail,
    quota_ledger: detail.quota_ledger ?? [],
    topup_packs: detail.topup_packs ?? [],
    upgrade_credits: detail.upgrade_credits ?? [],
    recent_rounds: detail.recent_rounds ?? [],
    recent_app_logs: detail.recent_app_logs ?? [],
    support_messages: detail.support_messages ?? [],
    orders: detail.orders ?? [],
    gift_cards: detail.gift_cards ?? [],
    gift_card_attempts: detail.gift_card_attempts ?? [],
  };
}

async function userDetailCard(userID: string): Promise<string> {
  try {
    const detail = await fetchUserDetail(userID);
    return `
      <section class="card user-detail-card">
        <div class="card-head">
          <div class="card-title">用户详情</div>
          <div style="display:flex;gap:8px;align-items:center">
            <button class="button small-button" type="button" data-action="close-user-detail">返回列表</button>
            ${statusPill(detail.user.tier || "free")}
          </div>
        </div>
        <div class="card-body user-detail-layout">
          <div class="user-detail-section">
            ${userKV(detail.user)}
          </div>
          <div class="user-detail-section">
            <div class="card-title" style="margin-bottom:8px">权益、礼品卡和反馈</div>
            ${userOpsSummary(detail)}
          </div>
          <div class="user-detail-section">
            <div class="section-title-row" style="margin-bottom:8px">
              <div class="card-title">最近反馈</div>
              ${
                detail.support_messages.length && isRouteVisible("support")
                  ? `<button class="button small-button" type="button" data-action="open-support-user" data-user-id="${escapeAttr(detail.user.user_id)}">打开反馈会话</button>`
                  : ""
              }
            </div>
            ${supportMessagesMiniList(detail.support_messages)}
          </div>
          <div class="user-detail-section">
            <div class="card-title" style="margin-bottom:8px">订单 / 礼品卡兑换</div>
            ${userTradeSnapshot(detail)}
          </div>
          <div class="user-detail-section user-detail-wide">
            <div class="card-title" style="margin-bottom:8px">最近问诊</div>
            ${roundExcerptList(detail.recent_rounds)}
          </div>
          <div class="user-detail-section user-detail-wide">
            <div class="card-title" style="margin-bottom:8px">最近 App 日志</div>
            ${compactLogs(detail.recent_app_logs)}
          </div>
        </div>
      </section>
    `;
  } catch (error) {
    return `<section class="card"><div class="card-body">${errorBlock(error)}</div></section>`;
  }
}

async function fetchSupportMessages(userID: string): Promise<AdminSupportMessagesResponse> {
  const response = await apiFetch<AdminSupportMessagesResponse>(
    `/admin-api/v1/support/messages${toQuery({ user_id: userID, query: pageState.supportMessageQuery })}`,
  );
  return { messages: response.messages ?? [], search_matched_messages: response.search_matched_messages ?? 0 };
}

function routeFromHash(): RouteKey {
  const key = location.hash.replace(/^#\/?/, "") as RouteKey;
  return isRouteVisible(key) ? key : defaultRoute();
}

function pageHead(title: string, desc: string, route: RouteKey): string {
  return `
    <div class="page-head">
      <div>
        <h1>${escapeHTML(title)}</h1>
        <p class="muted">${escapeHTML(desc)}</p>
      </div>
      <div class="row-actions">
        <span class="pill info">${escapeHTML(routes.find((item) => item.key === route)?.section || "")}</span>
      </div>
    </div>
    ${pageGuide(route)}
  `;
}

interface PageGuideItem {
  label: string;
  value: string;
  level?: "ok" | "warn" | "bad" | "info";
}

const pageGuides: Partial<Record<RouteKey, PageGuideItem[]>> = {
  overview: [
    { label: "先看", value: "服务健康、未回复反馈、今日农情" },
    { label: "异常时", value: "去监控面板看当前结论和处理入口", level: "warn" },
    { label: "边界", value: "这里只做总览，不展示敏感明细" },
  ],
  monitoring: [
    { label: "先看", value: "当前结论、程序需处理项、人工确认项" },
    { label: "下一步", value: "程序问题交给技术处理，人工项按确认方式核验", level: "warn" },
    { label: "注意", value: "真机回归和告警邮件仍要线下确认" },
  ],
  users: [
    { label: "先查", value: "账号ID或手机号，列表里直接显示手机号" },
    { label: "详情里看", value: "会员、额度、问诊、反馈、App日志" },
    { label: "手机号", value: "能复制就直接复制；没有完整号才显示脱敏号", level: "warn" },
  ],
  entitlements: [
    { label: "先看", value: "会员总盘子和到期/额度分布" },
    { label: "单人排查", value: "输入账号ID看权益、加油包、扣次流水" },
    { label: "扣次", value: "自动对账为主，不要求人工天天盯" },
  ],
  orders: [
    { label: "当前用途", value: "核查支付订单，按已付款异常单补发权益" },
    { label: "不要误读", value: "生产放量前以支付平台账单和对账结果为准", level: "warn" },
    { label: "后续", value: "生产回调验收、对账、退款" },
  ],
  "gift-cards": [
    { label: "先看", value: "可兑换卡、失败尝试、按尾号/账号追溯" },
    { label: "高风险", value: "生成后是真实可兑换权益", level: "bad" },
    { label: "隐私", value: "完整卡码不要写进备注、审计或文档", level: "warn" },
  ],
  "account-deletion": [
    { label: "先看", value: "待处理和超期申请" },
    { label: "处理口径", value: "线下完成不等于自动物理删除", level: "warn" },
    { label: "备注", value: "只写必要处理信息，不写密钥或内部细节" },
  ],
  support: [
    { label: "先处理", value: "待回复队列，点击会话看详情" },
    { label: "回复", value: "发送会进用户 App，已处理/关闭要写备注", level: "warn" },
    { label: "详情", value: "点会话看正文、图片、手机号和处理按钮" },
  ],
  "app-logs": [
    { label: "先筛", value: "账号ID、事件名、事件前缀、时间范围" },
    { label: "常用入口", value: "监控页可一键带入登录/更新排障筛选" },
    { label: "隐私", value: "日志展示层会再次脱敏" },
  ],
  "today-agri": [
    { label: "先看", value: "最新 ready 卡片和失败原因" },
    { label: "人工发布", value: "发布后会锁定当天内容", level: "warn" },
    { label: "兜底", value: "清晨自动生成仍作为兜底" },
  ],
  "app-update": [
    { label: "先确认", value: "这不是应用商店，只控制 App 检查更新" },
    { label: "高风险", value: "启用后旧包会拿到更新配置", level: "bad" },
    { label: "门禁", value: "必须先有正式发版口令和 APK 对账" },
  ],
  audit: [
    { label: "用途", value: "追后台登录、查询、回复和系统动作" },
    { label: "查法", value: "按 action、目标账号ID、成功/失败过滤" },
    { label: "边界", value: "后端不写敏感原文，前端展示再兜底" },
  ],
  account: [
    { label: "用途", value: "查看当前后台账号并修改密码" },
    { label: "强制改密", value: "完成改密后其它页面恢复访问", level: "warn" },
    { label: "安全", value: "明文密码不写入日志、审计或前端存储" },
  ],
  insights: [
    { label: "先看", value: "7天/30天增长、问诊、反馈和异常趋势" },
    { label: "边界", value: "只看脱敏聚合，不展示正文和手机号" },
    { label: "后续", value: "日报、人工标签和处理状态还未接入" },
  ],
  health: [
    { label: "先看", value: "API、短信、Redis、上传存储、严格鉴权" },
    { label: "注意", value: "告警规则以 SLS/巡检脚本为准，本页不实时读云控制台", level: "warn" },
    { label: "异常时", value: "先跑 readiness，再查后端和 Nginx 日志" },
  ],
};

function pageGuide(route: RouteKey): string {
  const items = pageGuides[route] || [];
  if (!items.length) return "";
  return `
    <section class="page-guide" aria-label="页面操作重点">
      ${items
        .map(
          (item) => `
            <div class="guide-item ${item.level || "info"}">
              <span>${escapeHTML(item.label)}</span>
              <strong>${escapeHTML(item.value)}</strong>
            </div>
          `,
        )
        .join("")}
    </section>
  `;
}

function usersTable(users: AdminUserListEntry[]): string {
  if (!users.length) return emptyState("没有用户数据", "当前筛选条件下暂无匹配用户。");
  return `
    <table class="table users-table">
      <thead>
        <tr>
          <th>账号ID</th><th>会员</th><th>今日额度</th><th>最近问诊</th><th>地区</th><th>错误</th><th>反馈</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        ${users
          .map(
            (user) => `
              <tr class="clickable-row ${pageState.userDetailID === user.user_id ? "active" : ""}" data-action="load-user-detail" data-user-id="${escapeAttr(user.user_id)}">
                <td>
                  ${accountIDLink(user.user_id, true)}
                  <div class="small muted">${accountPhoneDisplay(user, "list")}</div>
                </td>
                <td>${statusPill(user.tier || "free")}</td>
                <td>${quotaText(user.daily)}</td>
                <td>${formatTime(user.last_seen_at)}<div class="small muted">${user.round_total} 轮</div></td>
                <td>${escapeHTML(user.last_region || "未知")}<div class="small muted">${escapeHTML([user.last_region_source, user.last_region_reliability].filter(Boolean).join(" / "))}</div></td>
                <td>${user.error_count_24h ? statusPill(String(user.error_count_24h), "bad") : statusPill("0", "ok")}</td>
                <td>${supportUserCell(user)}</td>
                <td><button class="button" data-action="load-user-detail" data-user-id="${escapeAttr(user.user_id)}">详情</button></td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function tableCell(label: string, content: string, className = ""): string {
  return `<td data-label="${escapeAttr(label)}"${className ? ` class="${escapeAttr(className)}"` : ""}>${content}</td>`;
}

function accountPhoneDisplay(user: AdminUserListEntry, context: "list" | "detail" = "detail"): string {
  return phoneDisplay(user.phone_number, user.phone_mask, context);
}

function accountIDLink(userID: string, includeCopy = false): string {
  if (!userID) return "未返回";
  return `
    <span class="inline-actions">
      <button class="link-button truncate" style="max-width:220px" type="button" data-action="load-user-detail" data-user-id="${escapeAttr(userID)}">${escapeHTML(userID)}</button>
      ${includeCopy ? `<button class="link-button" type="button" data-action="copy-text" data-copy="${escapeAttr(userID)}">复制</button>` : ""}
    </span>
  `;
}

function accountIDText(userID: string): string {
  if (!userID) return "未返回";
  return `${escapeHTML(userID)} <button class="link-button" type="button" data-action="copy-text" data-copy="${escapeAttr(userID)}">复制</button>`;
}

function phoneDisplay(phoneNumber?: string, phoneMask?: string, context: "list" | "detail" = "detail"): string {
  const fullPhone = (phoneNumber || "").trim();
  if (fullPhone && canViewAccountPhone()) {
    return `
      <span class="inline-actions">
        <span class="sensitive-text">${escapeHTML(fullPhone)}</span>
        <button class="link-button" type="button" data-action="copy-text" data-copy="${escapeAttr(fullPhone)}">复制</button>
        ${phoneDialLink(fullPhone)}
      </span>
    `;
  }
  if (fullPhone) {
    const masked = phoneMask ? escapeHTML(phoneMask) : "已绑定手机号";
    return `${masked} <span class="small muted">完整号未显示</span>`;
  }
  if (phoneMask) {
    if (context === "list" && canViewAccountPhone()) return escapeHTML(phoneMask);
    return `${escapeHTML(phoneMask)} <span class="small muted">完整号待下次登录补齐</span>`;
  }
  return "未返回";
}

function phoneDialLink(phone: string): string {
  const digits = phone.replace(/\D/g, "");
  if (!digits) return "";
  return `<a class="link-button" href="tel:${escapeAttr(digits)}">拨打</a>`;
}

function phoneDisplayMaskedInline(phoneNumber?: string, phoneMask?: string): string {
  const fullPhone = (phoneNumber || "").trim();
  if (fullPhone && canViewAccountPhone()) {
    return `<span class="sensitive-text">${escapeHTML(fullPhone)}</span>`;
  }
  if (phoneMask) {
    return escapeHTML(phoneMask);
  }
  if (fullPhone) {
    return "已绑定手机号";
  }
  return "暂无手机号";
}

function supportUserCell(user: AdminUserListEntry): string {
  const count = user.support_message_count || 0;
  if (!count && !user.support_needs_reply) return "0 条";
  const state = user.support_needs_reply ? statusPill("待回复", "warn") : `${count} 条`;
  if (!isRouteVisible("support")) return state;
  return `${state}<div class="small-action-line"><button class="link-button" type="button" data-action="open-support-user" data-user-id="${escapeAttr(user.user_id)}">看反馈</button></div>`;
}

function userKV(user: AdminUserListEntry): string {
  return `
    <dl class="kv">
      <dt>账号ID</dt><dd>${accountIDText(user.user_id)}</dd>
      <dt>手机号</dt><dd>${accountPhoneDisplay(user)}</dd>
      <dt>创建时间</dt><dd>${formatTime(user.created_at)}</dd>
      <dt>最近登录</dt><dd>${formatTime(user.last_login_at)}</dd>
      <dt>活跃 session</dt><dd>${user.active_sessions}</dd>
      <dt>会员</dt><dd>${escapeHTML(user.tier || "free")}</dd>
      <dt>今日额度</dt><dd>${quotaText(user.daily)}</dd>
      <dt>加油包</dt><dd>${user.topup_remaining || 0}</dd>
      <dt>升级补偿</dt><dd>${user.upgrade_remaining || 0}</dd>
      <dt>地区</dt><dd>${escapeHTML(user.last_region || "未知")}</dd>
    </dl>
  `;
}

function userOpsSummary(detail: AdminUserDetail): string {
  const user = detail.user;
  const redeemedCards = detail.gift_cards.filter((row) => row.status === "redeemed").length;
  const failedAttempts = detail.gift_card_attempts.filter((row) => !row.success).length;
  const latestSupport = detail.support_messages[detail.support_messages.length - 1];
  return `
    <dl class="kv">
      <dt>会员</dt><dd>${statusPill(user.tier || "free")} ${formatTime(user.tier_expire_at)}</dd>
      <dt>订单</dt><dd>${detail.orders.length} 条</dd>
      <dt>礼品卡兑换</dt><dd>已兑换 ${redeemedCards} 张，明细 ${detail.gift_cards.length} 条</dd>
      <dt>兑换尝试</dt><dd>${detail.gift_card_attempts.length} 次，失败 ${failedAttempts}</dd>
      <dt>最近反馈明细</dt><dd>${detail.support_messages.length} 条${latestSupport ? `，最新 ${formatTime(latestSupport.created_at)}` : ""}</dd>
      <dt>App日志</dt><dd>${detail.recent_app_logs.length} 条最近记录</dd>
    </dl>
  `;
}

function userTradeSnapshot(detail: AdminUserDetail): string {
  const parts: string[] = [];
  if (detail.orders.length) {
    parts.push(`<div class="table-wrap">${ordersTable(detail.orders.slice(0, 5))}</div>`);
  }
  if (detail.gift_cards.length) {
    parts.push(`<div class="table-wrap">${giftCardTable(detail.gift_cards.slice(0, 5))}</div>`);
  }
  if (detail.gift_card_attempts.length) {
    parts.push(`<div class="table-wrap">${giftCardAttemptsTable(detail.gift_card_attempts.slice(0, 5))}</div>`);
  }
  return parts.length
    ? parts.join(`<div style="height:10px"></div>`)
    : emptyState("没有交易记录", "当前没有订单、礼品卡兑换或兑换尝试。");
}

function supportMessagesMiniList(rows: AdminSupportMessage[]): string {
  if (!rows.length) return emptyState("没有反馈记录", "该用户当前暂无帮助与反馈消息。");
  return `
    <div class="message-list compact">
      ${rows
        .slice(-5)
        .map(
          (message) => `
            <article class="message ${escapeAttr(message.sender_type || "")}">
              <div class="message-head">
                <strong>${escapeHTML(supportSenderLabel(message.sender_type))}</strong>
                <span>${formatTime(message.created_at)}</span>
              </div>
              <div class="sensitive-body">${escapeHTML(supportMessageText(message))}</div>
              ${supportMessageImages(message)}
            </article>
          `,
        )
        .join("")}
    </div>
  `;
}

function entitlementSummary(detail: AdminUserDetail): string {
  const user = detail.user;
  return `
    <dl class="kv">
      <dt>账号ID</dt><dd>${accountIDText(user.user_id)}</dd>
      <dt>会员档位</dt><dd>${statusPill(user.tier || "free")}</dd>
      <dt>会员到期</dt><dd>${formatTime(user.tier_expire_at)}</dd>
      <dt>今日额度</dt><dd>${quotaText(user.daily)}</dd>
      <dt>加油包剩余</dt><dd>${user.topup_remaining || 0}</dd>
      <dt>加油包到期</dt><dd>${formatTime(user.topup_expire_at)}</dd>
      <dt>升级补偿</dt><dd>${user.upgrade_remaining || 0}</dd>
      <dt>总问诊轮次</dt><dd>${user.round_total || 0}</dd>
    </dl>
  `;
}

function entitlementOverviewBlock(summary: AdminEntitlementSummary): string {
  const accountMembers = summary.account_member_users || 0;
  const legacyMembers = summary.legacy_member_users || 0;
  const memberSubtitle =
    legacyMembers > 0
      ? `当前会员 ${summary.member_users}（账号 ${accountMembers} / 迁移期旧身份记录 ${legacyMembers}）`
      : `当前会员 ${summary.member_users}`;
  return `
    <section class="grid kpi" style="margin-top:12px">
      ${kpi("账号用户", summary.registered_users, memberSubtitle)}
      ${kpi("账号内 Free", summary.free_users, "仅按已收敛账号ID统计")}
      ${kpi("Plus", summary.plus_users, "当前有效 Plus")}
      ${kpi("Pro", summary.pro_users, "当前有效 Pro")}
      ${kpi("7天内到期", summary.expiring_in_7d, "当前 Plus / Pro")}
      ${kpi("30天内到期", summary.expiring_in_30d, "当前 Plus / Pro")}
    </section>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">会员经营概览</div><span class="small muted">${formatTime(summary.now_ms)}</span></div>
        <div class="card-body">
          <table class="table">
            <tbody>
              ${metricRow("当前会员总数", summary.member_users)}
              ${metricRow("已收敛账号会员", accountMembers)}
              ${metricRow("迁移期旧身份记录会员", legacyMembers)}
              ${metricRow("今日基础额度用满", summary.daily_limit_exhausted_users)}
              ${metricRow("有加油包余额", summary.topup_active_users)}
              ${metricRow("有升级补偿", summary.upgrade_credit_users)}
            </tbody>
          </table>
        </div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">当前口径</div></div>
        <div class="card-body stack">
          ${notice("会员有效期", "Plus / Pro 只统计当前仍在有效期内的用户；过期后自动按 Free 计算。", "info")}
          ${
            legacyMembers > 0
              ? notice("账号ID收敛中", `还有 ${legacyMembers} 个当前会员仍归属迁移期旧身份记录，因此“账号用户”“账号内会员”和“当前会员总数”暂时不会完全一一对应。`, "warn")
              : ""
          }
          ${notice("额度耗尽", "这里统计的是“今日基础额度用满”的账号数，不代表加油包和升级补偿也已经同时用完。", "info")}
        </div>
      </section>
    </div>
  `;
}

async function quotaConsumeOutboxBlock(): Promise<string> {
  if (!canViewQuotaOutbox()) return "";
  let entries: AdminQuotaConsumeOutboxEntry[] = [];
  let loadError = "";
  try {
    const response = await apiFetch<{ entries: AdminQuotaConsumeOutboxEntry[] }>(
      "/admin-api/v1/quota-consume-outbox?status=active&limit=50",
    );
    entries = response.entries || [];
  } catch (error) {
    loadError = errorMessage(error);
  }
  return `
    <section class="card" style="margin-top:12px">
      <div class="card-head">
        <div>
          <div class="card-title">扣次自动对账</div>
          <div class="small muted">完整回答已归档但扣次还没结清时，系统自动追账；极少数不能安全补扣的业务边界会自动终结为对账记录，不扣用户未来次数，不挡用户继续聊天。</div>
        </div>
        <span class="small muted">${entries.length} 条</span>
      </div>
      ${
        loadError
          ? `<div class="card-body">${notice("队列暂时不可用", `后台返回 ${loadError}，稍后刷新即可。`, "warn")}</div>`
          : `<div class="table-wrap">${quotaConsumeOutboxTable(entries)}</div>`
      }
    </section>
  `;
}

function quotaConsumeOutboxTable(rows: AdminQuotaConsumeOutboxEntry[]): string {
  if (!rows.length) return emptyState("自动对账正常", "后台没有未完成的扣次对账记录。");
  const canManage = canManageQuotaOutbox();
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>ID</th><th>状态</th><th>账号ID</th><th>日期/档位</th><th>尝试</th><th>下次处理</th><th>错误</th><th>操作</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("ID", String(row.id))}
                ${tableCell("状态", statusPill(quotaOutboxStatusText(row.status), quotaOutboxStatusLevel(row.status)))}
                ${tableCell("账号ID", `<button class="link-button" type="button" data-action="load-user-detail" data-user-id="${escapeAttr(row.user_id)}">${escapeHTML(row.user_id)}</button><div class="small muted">${escapeHTML(row.client_msg_id)}</div>`)}
                ${tableCell("日期/档位", `${escapeHTML(row.day_cn)}<div class="small muted">${escapeHTML(row.tier_at_completion || "free")} · ${formatTime(row.completion_at)}</div>`)}
                ${tableCell("尝试", String(row.attempts))}
                ${tableCell("下次处理", row.next_attempt_at ? formatTime(row.next_attempt_at) : "系统复核中")}
                ${tableCell("错误", escapeHTML(row.last_error || ""), "wrap")}
                ${tableCell("操作", canManage ? quotaOutboxActions(row) : `<span class="small muted">自动处理</span>`)}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function quotaOutboxActions(row: AdminQuotaConsumeOutboxEntry): string {
  if (row.status === "pending") {
    return `<span class="small muted">自动重试中</span>`;
  }
  if (row.status === "failed") {
    return `<span class="small muted">自动追账中</span>`;
  }
  if (row.status !== "needs_ops") {
    return `<span class="small muted">已结束</span>`;
  }
  return `<span class="small muted">低频追账中，长期失败自动终结</span>`;
}

function quotaOutboxStatusText(status: string): string {
  if (status === "pending") return "自动重试";
  if (status === "failed" || status === "needs_ops") return "自动追账";
  if (status === "done") return "已结清";
  if (status === "waived") return "已豁免";
  if (status === "uncollectable") return "自动终结";
  return status;
}

function quotaOutboxStatusLevel(status: string): "ok" | "warn" | "bad" | "info" {
  if (status === "pending") return "warn";
  if (status === "failed" || status === "needs_ops") return "warn";
  if (status === "done" || status === "waived" || status === "uncollectable") return "info";
  return "info";
}

function quotaLedgerTable(rows: AdminQuotaLedgerEntry[]): string {
  if (!rows.length) return emptyState("没有扣次流水", "该用户当前暂无额度流水。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>ID</th><th>日期</th><th>来源</th><th>delta</th><th>client_msg_id</th><th>时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("ID", String(row.id))}
                ${tableCell("日期", escapeHTML(row.day_cn))}
                ${tableCell("来源", escapeHTML(row.source))}
                ${tableCell("delta", String(row.delta))}
                ${tableCell("client_msg_id", escapeHTML(row.client_msg_id))}
                ${tableCell("时间", formatTime(row.created_at))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function ordersTable(rows: AdminOrderEntry[]): string {
  if (!rows.length) return emptyState("没有订单数据", "当前筛选范围内没有现有订单、支付记录或会员变更记录。");
  const showActions = canManagePaymentGrants();
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>订单</th><th>账号ID</th><th>来源</th><th>类型</th><th>金额</th><th>状态</th><th>权益</th><th>创建时间</th><th>结果</th>${showActions ? "<th>操作</th>" : ""}</tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("订单", escapeHTML(row.order_id))}
                ${tableCell("账号ID", `<div class="truncate" style="max-width:220px">${escapeHTML(row.user_id)}</div>`)}
                ${tableCell("来源", escapeHTML(orderSourceLabel(row)))}
                ${tableCell("类型", escapeHTML(row.type))}
                ${tableCell("金额", orderAmountCell(row))}
                ${tableCell("状态", statusPill(row.status))}
                ${tableCell("权益", row.grant_status ? statusPill(row.grant_status) : '<span class="muted">-</span>')}
                ${tableCell("创建时间", formatTime(row.created_at))}
                ${tableCell("结果", jsonInline(redactSensitiveDisplayValue(row.result)), "wrap")}
                ${showActions ? tableCell("操作", paymentGrantAction(row)) : ""}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function paymentGrantAction(row: AdminOrderEntry): string {
  if (!canManagePaymentGrants()) return "";
  if (row.source !== "payment") return '<span class="muted">-</span>';
  if ((row.status || "").toLowerCase() !== "paid") return '<span class="muted">未付款</span>';
  if ((row.grant_status || "").toLowerCase() === "success") return '<span class="muted">已到账</span>';
  return `<button class="button small-button" type="button" data-action="grant-payment-order" data-out-trade-no="${escapeAttr(row.order_id)}">补发权益</button>`;
}

function orderSourceLabel(row: AdminOrderEntry): string {
  const base = row.provider || (row.source === "payment" ? "payment" : row.source === "dev" ? "dev" : row.source || "-");
  if (row.is_test_order) return `${base} · 测试`;
  return base;
}

function orderAmountCell(row: AdminOrderEntry): string {
  const parts = [escapeHTML(row.amount)];
  if (row.is_test_order) {
    parts.push(` ${statusPill("测试", "warn")}`);
  }
  if (row.original_amount && row.original_amount !== row.amount) {
    parts.push(`<div class="small muted">原价 ${escapeHTML(row.original_amount)}</div>`);
  }
  if (row.client_build_type) {
    const version = row.client_version_code ? ` / v${row.client_version_code}` : "";
    parts.push(`<div class="small muted">${escapeHTML(row.client_build_type + version)}</div>`);
  }
  return parts.join("");
}

function orderCountsAsPaidRevenue(row: AdminOrderEntry): boolean {
  if (row.source !== "payment") return false;
  if (row.is_test_order) return false;
  return (row.status || "").toLowerCase() === "paid";
}

function summarizeOrders(rows: AdminOrderEntry[]): { success: number; failed: number; amountText: string } {
  let success = 0;
  let failed = 0;
  let amountTotal = 0;
  for (const row of rows) {
    const status = (row.status || "").toLowerCase();
    if (["success", "paid", "completed", "ok"].includes(status)) {
      success += 1;
    } else if (["failed", "error", "closed", "refunded", "canceled", "cancelled"].includes(status)) {
      failed += 1;
    }
    const amount = Number(row.amount);
    if (orderCountsAsPaidRevenue(row) && Number.isFinite(amount)) {
      amountTotal += amount;
    }
  }
  return {
    success,
    failed,
    amountText: new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY" }).format(amountTotal),
  };
}

function topupPacksTable(rows: AdminTopupPackEntry[]): string {
  if (!rows.length) return emptyState("没有加油包", "该用户没有加油包记录。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>包</th><th>订单</th><th>状态</th><th>初始</th><th>已用</th><th>剩余</th><th>到期</th><th>创建时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("包", escapeHTML(row.pack_id))}
                ${tableCell("订单", escapeHTML(row.order_id || ""))}
                ${tableCell("状态", statusPill(row.status))}
                ${tableCell("初始", String(row.initial))}
                ${tableCell("已用", String(row.used))}
                ${tableCell("剩余", String(row.remaining))}
                ${tableCell("到期", formatTime(row.expire_at))}
                ${tableCell("创建时间", formatTime(row.created_at))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function upgradeCreditsTable(rows: AdminUpgradeCredit[]): string {
  if (!rows.length) return emptyState("没有升级补偿", "该用户没有升级补偿次数记录。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>账号ID</th><th>剩余次数</th><th>到期</th><th>更新时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("账号ID", escapeHTML(row.user_id))}
                ${tableCell("剩余次数", String(row.remaining))}
                ${tableCell("到期", formatTime(row.expire_at))}
                ${tableCell("更新时间", formatTime(row.updated_at))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function createdGiftCardCodesBlock(rows: AdminGiftCardCreatedCode[]): string {
  if (!rows.length) return "";
  return `
    <div class="notice warn" style="margin-top:12px">
      <strong>新生成卡码</strong>
      <div class="muted" style="margin-top:6px">完整卡码已加密保存；本页可以直接复制，下方“卡与兑换”列表刷新后也能查看。不要写入备注、作废原因或公开文档。</div>
      <button class="button" type="button" data-action="clear-gift-card-codes" style="margin-top:10px">清除本次卡码</button>
      <div class="table-wrap" style="margin-top:10px">
        <table class="table mobile-card-table">
          <thead><tr><th>完整卡码</th><th>档位</th><th>天数</th><th>有效至</th><th>操作</th></tr></thead>
          <tbody>
            ${rows
              .map(
                (row) => `
                  <tr>
                    ${tableCell("完整卡码", escapeHTML(row.code), "mono code-cell")}
                    ${tableCell("档位", statusPill(row.tier))}
                    ${tableCell("天数", String(row.duration_days))}
                    ${tableCell("有效至", formatTime(row.valid_until))}
                    ${tableCell("操作", `<button class="button" type="button" data-action="copy-text" data-copy="${escapeAttr(row.code)}">复制</button>`)}
                  </tr>
                `,
              )
              .join("")}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function giftCardTraceFilterForm(): string {
  return `
    <form id="gift-card-filter-form" class="filter-form">
      <label>批次 ID<input name="batch_id" value="${escapeAttr(pageState.giftCardBatchID)}" placeholder="gcb_..." /></label>
      <label>状态
        <select name="status">
          ${selectOption("", "全部", pageState.giftCardStatus)}
          ${selectOption("active", "未兑换", pageState.giftCardStatus)}
          ${selectOption("redeemed", "已兑换", pageState.giftCardStatus)}
          ${selectOption("void", "已作废", pageState.giftCardStatus)}
        </select>
      </label>
      <label>账号ID<input name="user_id" value="${escapeAttr(pageState.giftCardUserID)}" placeholder="acct_..." /></label>
      <label>卡尾号<input name="code_suffix" value="${escapeAttr(pageState.giftCardCodeSuffix)}" placeholder="后4位" maxlength="12" /></label>
      <label>尝试结果
        <select name="success">
          ${selectOption("", "全部", pageState.giftCardAttemptSuccess)}
          ${selectOption("success", "成功", pageState.giftCardAttemptSuccess)}
          ${selectOption("failed", "失败", pageState.giftCardAttemptSuccess)}
        </select>
      </label>
      <label>失败原因<input name="failure_reason" value="${escapeAttr(pageState.giftCardAttemptReason)}" placeholder="卡码错误 / 不存在 / 过期 / 档位较低 / 已兑换 / 已作废" /></label>
      <button class="button primary" type="submit">查询</button>
      <button class="button" type="button" data-action="clear-gift-card-filter">清空</button>
    </form>
  `;
}

function accountDeletionFilterForm(): string {
  return `
    <form id="account-deletion-filter-form" class="filter-form">
      <label>状态
        <select name="status">
          ${selectOption("", "全部", pageState.accountDeletionStatus)}
          ${selectOption("pending", "待处理", pageState.accountDeletionStatus)}
          ${selectOption("processing", "处理中", pageState.accountDeletionStatus)}
          ${selectOption("completed", "线下处理完成", pageState.accountDeletionStatus)}
          ${selectOption("rejected", "已驳回", pageState.accountDeletionStatus)}
          ${selectOption("cancelled", "已取消", pageState.accountDeletionStatus)}
        </select>
      </label>
      <label>账号ID<input name="user_id" value="${escapeAttr(pageState.accountDeletionUserID)}" placeholder="acct_..." /></label>
      <button class="button primary" type="submit">查询</button>
    </form>
  `;
}

function accountDeletionTable(rows: AccountDeletionRequest[]): string {
  if (!rows.length) return emptyState("没有注销申请", "当前筛选条件下没有账号注销申请。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>申请</th><th>账号</th><th>状态</th><th>原因 / 留言</th><th>处理期限</th><th>处理</th><th>时间</th><th>操作</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("申请", `<div class="mono">${escapeHTML(row.request_id)}</div>`)}
                ${tableCell("账号", `
                  <button class="link-button" data-action="load-user-detail" data-user-id="${escapeAttr(row.user_id)}">${escapeHTML(row.user_id)}</button>
                  <div class="small muted">${escapeHTML(row.phone_mask || "未返回手机号")}</div>
                `)}
                ${tableCell("状态", statusPill(accountDeletionStatusLabel(row.status), accountDeletionStatusLevel(row)))}
                ${tableCell("原因 / 留言", `
                  <div>${escapeHTML(row.reason || "未填写")}</div>
                  ${row.user_message ? `<div class="small muted">${escapeHTML(row.user_message)}</div>` : ""}
                `, "wrap")}
                ${tableCell("处理期限", accountDeletionDeadlineBlock(row), "wrap")}
                ${tableCell("处理", `
                  <div>${escapeHTML(row.handled_by || "未处理")}</div>
                  ${row.handler_note ? `<div class="small muted">${escapeHTML(row.handler_note)}</div>` : ""}
                  ${row.handled_at ? `<div class="small muted">${formatTime(row.handled_at)}</div>` : ""}
                `, "wrap")}
                ${tableCell("时间", `${formatTime(row.created_at)}<div class="small muted">更新 ${formatTime(row.updated_at)}</div>`)}
                ${tableCell("操作", accountDeletionActions(row))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function accountDeletionActions(row: AccountDeletionRequest): string {
  if (!canManageAccountDeletion()) {
    return `<span class="small muted">只读</span>`;
  }
  if (row.status === "completed" || row.status === "rejected" || row.status === "cancelled") {
    return `<span class="small muted">已结束</span>`;
  }
  return `
    <div class="row-actions">
      ${row.status !== "processing" ? `<button class="button" data-action="account-deletion-status" data-request-id="${escapeAttr(row.request_id)}" data-status="processing">处理中</button>` : ""}
      <button class="button primary" data-action="account-deletion-status" data-request-id="${escapeAttr(row.request_id)}" data-status="completed">标记线下完成</button>
      <button class="button" data-action="account-deletion-status" data-request-id="${escapeAttr(row.request_id)}" data-status="rejected">驳回</button>
      <button class="button" data-action="account-deletion-status" data-request-id="${escapeAttr(row.request_id)}" data-status="cancelled">取消</button>
    </div>
  `;
}

function accountDeletionStatusLabel(status: string): string {
  switch ((status || "").toLowerCase()) {
    case "pending":
      return "待处理";
    case "processing":
      return "处理中";
    case "completed":
      return "线下处理完成";
    case "rejected":
      return "已驳回";
    case "cancelled":
      return "已取消";
    default:
      return status || "全部";
  }
}

function accountDeletionDeadlineBlock(row: AccountDeletionRequest): string {
  if (!row.due_at) return `<span class="small muted">未返回期限</span>`;
  const state = accountDeletionDeadlineState(row);
  const dueText = `期限 ${formatTime(row.due_at)}`;
  if (state.overdueDays > 0) {
    return `${statusPill(`超期 ${state.overdueDays} 天`, "bad")}<div class="small muted">${dueText}</div>`;
  }
  if (state.remainingDays >= 0 && (row.status === "pending" || row.status === "processing")) {
    return `${statusPill(`剩 ${state.remainingDays} 天`, state.remainingDays <= 2 ? "warn" : "info")}<div class="small muted">${dueText}</div>`;
  }
  return `<span class="small muted">${dueText}</span>`;
}

function accountDeletionDeadlineState(row: AccountDeletionRequest): { remainingDays: number; overdueDays: number } {
  if (!row.due_at) return { remainingDays: -1, overdueDays: 0 };
  const diffMs = row.due_at - Date.now();
  if (row.overdue || diffMs < 0) {
    return { remainingDays: -1, overdueDays: Math.max(1, Math.ceil(Math.abs(diffMs) / (24 * 60 * 60 * 1000))) };
  }
  return { remainingDays: Math.ceil(diffMs / (24 * 60 * 60 * 1000)), overdueDays: 0 };
}

function accountDeletionStatusLevel(row: AccountDeletionRequest): "ok" | "warn" | "bad" | "info" {
  if (row.overdue) return "bad";
  switch ((row.status || "").toLowerCase()) {
    case "pending":
    case "processing":
      return "warn";
    case "completed":
      return "ok";
    case "rejected":
    case "cancelled":
      return "info";
    default:
      return "info";
  }
}

function normalizeGiftCardSummary(summary: AdminGiftCardSummary | null | undefined): AdminGiftCardSummary {
  return {
    batch_count: summary?.batch_count ?? 0,
    active_count: summary?.active_count ?? 0,
    redeemable_count: summary?.redeemable_count ?? 0,
    redeemed_count: summary?.redeemed_count ?? 0,
    void_count: summary?.void_count ?? 0,
    failed_attempts_24h: summary?.failed_attempts_24h ?? 0,
    failure_reasons: summary?.failure_reasons ?? [],
  };
}

function giftCardFailureReasonsBlock(rows: AdminGiftCardSummary["failure_reasons"] | null | undefined): string {
  rows = rows ?? [];
  if (!rows.length) return emptyState("没有失败聚合", "最近 7 天没有失败兑换，或者还没有兑换尝试。");
  return `
    <div class="reason-grid">
      ${rows
        .map(
          (row) => `
            <button class="reason-chip" data-action="gift-card-reason-filter" data-reason="${escapeAttr(row.reason)}">
              <strong>${escapeHTML(row.reason)}</strong>
              <span>${row.count} 次</span>
            </button>
          `,
        )
        .join("")}
    </div>
  `;
}

function giftCardFailureReasonTable(rows: AdminGiftCardSummary["failure_reasons"] | null | undefined): string {
  rows = rows ?? [];
  if (!rows.length) return emptyState("没有失败原因", "最近 7 天没有失败兑换，或者还没有兑换尝试。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>原因</th><th>次数</th></tr></thead>
      <tbody>
        ${rows.map((row) => `<tr>${tableCell("原因", escapeHTML(row.reason))}${tableCell("次数", String(row.count))}</tr>`).join("")}
      </tbody>
    </table>
  `;
}

function insightWindowTable(rows: AdminInsights["windows"]): string {
  if (!rows.length) return emptyState("没有洞察窗口", "当前暂无产品洞察窗口数据。");
  return `
    <table class="table mobile-card-table">
      <thead>
        <tr>
          <th>范围</th><th>新增用户</th><th>登录</th><th>问诊</th><th>图片问诊</th><th>消耗</th><th>App异常</th><th>登录排障</th><th>反馈</th><th>礼品卡</th><th>农情失败</th>
        </tr>
      </thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("范围", `${escapeHTML(row.label)}<div class="small muted">since ${formatTime(row.since_ms)}</div>`)}
                ${tableCell("新增用户", String(row.new_users))}
                ${tableCell("登录", String(row.recent_auth_sessions))}
                ${tableCell("问诊", `${row.chat_rounds}<div class="small muted">${row.chat_users} 位用户</div>`)}
                ${tableCell("图片问诊", `${row.image_chat_rounds}<div class="small muted">${formatPercent(row.image_chat_ratio)}</div>`)}
                ${tableCell("消耗", String(row.quota_deductions))}
                ${tableCell("App异常", `${row.app_errors} / ${row.app_warns}`)}
                ${tableCell("登录排障", `${row.auth_failures}<div class="small muted">闪退 ${row.crash_reports}</div>`)}
                ${tableCell("反馈", `${row.support_messages}<div class="small muted">${row.support_users} 位用户</div>`)}
                ${tableCell("礼品卡", `${row.gift_card_redeems} 成功<div class="small muted">${row.gift_card_failures} 失败</div>`)}
                ${tableCell("农情失败", String(row.daily_agri_failed_cards))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function breakdownBars(rows: AdminInsightBreakdown[] | null | undefined, foot: string): string {
  rows = (rows || []).filter((row) => row.count >= 0);
  if (!rows.length) return emptyState("没有分类数据", "当前暂无分类聚合。");
  const max = Math.max(...rows.map((row) => row.count), 1);
  return `
    <div class="insight-breakdown">
      ${rows
        .map((row) => {
          const width = Math.max(3, Math.round((row.count / max) * 100));
          return `
            <div class="insight-bar-row">
              <div class="insight-bar-head">
                <span>${escapeHTML(row.label)}</span>
                <strong>${row.count}</strong>
              </div>
              <div class="insight-bar-track"><span style="width:${width}%"></span></div>
            </div>
          `;
        })
        .join("")}
      <div class="small muted">${escapeHTML(foot)}</div>
    </div>
  `;
}

function giftCardBatchesTable(rows: AdminGiftCardBatch[]): string {
  if (!rows.length) return emptyState("没有礼品卡批次", "还没有创建礼品卡批次。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>批次</th><th>档位</th><th>天数</th><th>总数</th><th>active（未兑）</th><th>已兑</th><th>作废</th><th>可兑换至</th><th>创建</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("批次", `<div>${escapeHTML(row.name || row.batch_id)}</div><div class="small muted">${escapeHTML(row.batch_id)}</div>`)}
                ${tableCell("档位", statusPill(row.tier))}
                ${tableCell("天数", String(row.duration_days))}
                ${tableCell("总数", String(row.quantity))}
                ${tableCell("active（未兑）", String(row.active_count))}
                ${tableCell("已兑", String(row.redeemed_count))}
                ${tableCell("作废", String(row.void_count))}
                ${tableCell("可兑换至", formatTime(row.valid_until))}
                ${tableCell("创建", formatTime(row.created_at))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function giftCardTable(rows: AdminGiftCardEntry[]): string {
  if (!rows.length) return emptyState("没有礼品卡", "当前暂无礼品卡记录。");
  const canVoid = canManageGiftCards();
  const canViewCodes = canViewGiftCardCodes();
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>卡</th><th>完整卡码</th><th>档位</th><th>状态</th><th>兑换账号ID</th><th>兑换时间</th><th>会员到期</th><th>地区</th><th>操作</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("卡", `<div class="mono">${escapeHTML(row.code_mask)}</div><div class="small muted">${escapeHTML(row.card_id)} / 尾号 ${escapeHTML(row.code_suffix || "")}</div><div class="small muted">${escapeHTML(row.batch_id)}</div>`)}
                ${tableCell("完整卡码", `<div class="mono code-cell">${canViewCodes && row.code ? escapeHTML(row.code) : canViewCodes ? "旧卡无完整码" : "未显示完整码"}</div>${canViewCodes && row.code ? `<button class="button small-button" type="button" data-action="copy-text" data-copy="${escapeAttr(row.code)}">复制</button>` : ""}`)}
                ${tableCell("档位", statusPill(row.tier))}
                ${tableCell("状态", giftCardStatusPill(row.status))}
                ${tableCell("兑换账号ID", `${row.redeemed_user_id ? `<button class="link-button" data-action="load-user-detail" data-user-id="${escapeAttr(row.redeemed_user_id)}">${escapeHTML(row.redeemed_user_id)}</button>` : ""}<div class="small muted">${escapeHTML(row.redeemed_phone_mask || "")}</div>`)}
                ${tableCell("兑换时间", formatTime(row.redeemed_at))}
                ${tableCell("会员到期", formatTime(row.membership_expire_at))}
                ${tableCell("地区", `${escapeHTML(row.redeemed_region || "")}<div class="small muted">${escapeHTML([row.redeemed_region_source, row.redeemed_region_reliability].filter(Boolean).join(" / "))}</div>`)}
                ${tableCell("操作", `${
                  row.status === "active" && canVoid
                    ? `<button class="button danger" data-action="void-gift-card" data-card-id="${escapeAttr(row.card_id)}">作废</button>`
                    : `<span class="small muted">${row.status === "void" ? `已作废 ${formatTime(row.voided_at)}` : canVoid ? "无操作" : "只读"}</span>`
                }`)}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function giftCardStatusPill(status: string): string {
  const normalized = (status || "").trim();
  const labels: Record<string, { label: string; level: "ok" | "warn" | "bad" | "info" }> = {
    active: { label: "未兑换", level: "ok" },
    redeemed: { label: "已兑换", level: "info" },
    void: { label: "已作废", level: "bad" },
  };
  const mapped = labels[normalized];
  return mapped ? statusPill(mapped.label, mapped.level) : statusPill(normalized || "未知", "warn");
}

function giftCardFailureReasonLabel(reason?: string): string {
  const normalized = (reason || "").trim();
  if (!normalized) return "";
  const labels: Record<string, string> = {
    invalid_code: "卡码格式错误",
    not_found: "卡码不存在",
    expired: "已过期",
    lower_tier: "档位较低",
    redeemed: "已兑换",
    void: "已作废",
  };
  const label = labels[normalized];
  return label ? `${label}（${normalized}）` : normalized;
}

function giftCardAttemptsTable(rows: AdminGiftCardAttempt[]): string {
  if (!rows.length) return emptyState("没有兑换尝试", "当前暂无兑换尝试记录。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>ID</th><th>卡尾号</th><th>账号ID</th><th>结果</th><th>原因</th><th>地区</th><th>IP</th><th>时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("ID", String(row.id))}
                ${tableCell("卡尾号", escapeHTML(row.code_suffix || ""), "mono")}
                ${tableCell("账号ID", row.user_id ? `<button class="link-button" data-action="load-user-detail" data-user-id="${escapeAttr(row.user_id)}">${escapeHTML(row.user_id)}</button>` : "")}
                ${tableCell("结果", row.success ? statusPill("成功", "ok") : statusPill("失败", "bad"))}
                ${tableCell("原因", escapeHTML(giftCardFailureReasonLabel(row.failure_reason)))}
                ${tableCell("地区", `${escapeHTML(row.region || "")}<div class="small muted">${escapeHTML([row.region_source, row.region_reliability].filter(Boolean).join(" / "))}</div>`)}
                ${tableCell("IP", escapeHTML(row.masked_ip || ""))}
                ${tableCell("时间", formatTime(row.created_at))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function supportFilterForm(): string {
  const hasFilter =
    pageState.supportStatus !== "open" || pageState.supportQuery.trim() !== "" || pageState.supportSinceRange !== "30d";
  return `
    <form id="support-filter-form" class="filters support-filters">
      <label class="field">
        <span>队列</span>
        ${selectHTML("status", pageState.supportStatus, [["", "全部"], ["open", "待回复"], ["replied", "已处理"], ["closed", "已关闭"]])}
      </label>
      <label class="field">
        <span>时间范围</span>
        ${selectHTML("since_range", pageState.supportSinceRange, [["30d", "近30天"], ["90d", "近90天"], ["all", "全部历史"]])}
      </label>
      <label class="field wide">
        <span>搜索</span>
        <input class="input" name="query" value="${escapeAttr(pageState.supportQuery)}" placeholder="账号ID / 手机号 / 会话消息" />
        <span class="small muted">输入账号ID、手机号或会话消息；有搜索词时自动查全部队列和全部历史。</span>
      </label>
      <button class="button primary" type="submit">筛选</button>
      ${hasFilter ? `<button class="button" type="button" data-action="clear-support-filter">清空</button>` : ""}
    </form>
  `;
}

function supportSinceRangeToMs(value: string): number {
  const now = Date.now();
  if (value === "all") return 0;
  if (value === "90d") return now - 90 * 24 * 60 * 60 * 1000;
  return now - 30 * 24 * 60 * 60 * 1000;
}

function supportConversationList(conversations: AdminSupportConversation[]): string {
  if (!conversations.length) return emptyState("暂无反馈会话", "当前没有需要处理的用户反馈。");
  return conversations
    .map(
      (item) => `
        <button class="selectable-row support-conversation-row ${item.user_id === pageState.supportUserID ? "active" : ""}" data-action="support-select" data-user-id="${escapeAttr(item.user_id)}">
          <strong class="truncate">${escapeHTML(item.user_id)}</strong>
          <span class="small muted support-row-meta">${phoneDisplayMaskedInline(item.phone_number, item.phone_mask)} · ${formatTime(item.latest_message.created_at)}</span>
          <span class="small muted support-row-message">${escapeHTML(supportMessageText(item.latest_message))}</span>
          <span class="support-row-foot">${supportStatusPill(item)} <span class="small muted">${item.message_count} 条</span></span>
        </button>
      `,
    )
    .join("");
}

function supportMessagesBlock(
  userID: string,
  messages: AdminSupportMessage[],
  conversation?: AdminSupportConversation,
  loadError = "",
  searchMatchedMessages = 0,
  nextNeedsReply?: AdminSupportConversation,
): string {
  const canManage = canManageSupport();
  const truncatedMessageNotice =
    conversation && messages.length > 0 && conversation.message_count > messages.length
      ? notice(
          "仅显示最近消息",
          `当前显示 ${messages.length} 条，共 ${conversation.message_count} 条${searchMatchedMessages > 0 ? `；已合并 ${searchMatchedMessages} 条搜索命中的较早消息。` : "；可用上方搜索查找更早线索。"}`,
          "info",
        )
      : "";
  return `
    ${conversation ? supportConversationMeta(conversation) : ""}
    <div class="support-detail-actions">
      <button class="button" type="button" data-action="support-back-list">返回会话队列</button>
      ${nextNeedsReply ? `<button class="button" type="button" data-action="support-next-open" data-user-id="${escapeAttr(nextNeedsReply.user_id)}">下一条待回复</button>` : ""}
      <button class="button" type="button" data-action="load-user-detail" data-user-id="${escapeAttr(userID)}">打开用户详情</button>
    </div>
    ${loadError ? errorBlock(new Error(loadError)) : ""}
    ${truncatedMessageNotice}
    <div class="message-list">
      ${
        !loadError && messages.length
          ? messages
              .map(
                (message) => `
                  <article class="message ${escapeAttr(message.sender_type)}">
                    <div class="message-head">
                      <strong>${escapeHTML(supportSenderLabel(message.sender_type))}</strong>
                      <span>${formatTime(message.created_at)}</span>
                    </div>
                    <div class="sensitive-body">${escapeHTML(supportMessageText(message))}</div>
                    ${message.body_redacted ? `<div class="small muted" style="margin-top:6px">这条正文未在当前后台显示。</div>` : ""}
                    ${supportMessageImages(message)}
                  </article>
                `,
              )
              .join("")
          : loadError
            ? ""
            : emptyState("没有消息", "当前会话暂无消息明细。")
      }
    </div>
    <div class="divider"></div>
    ${
      canManage
        ? `
          <form id="support-reply-form" class="stack">
            <input type="hidden" name="user_id" value="${escapeAttr(userID)}" />
            <label class="field">
              <span>后台回复</span>
              <textarea class="textarea" name="body" maxlength="2000" placeholder="只写必要的客服回复；手机号、订单号、礼品卡码等排障信息可以发送，不要发送后台密钥、token 或内部排障细节。"></textarea>
            </label>
            ${supportReplyTemplates()}
            <div class="row-actions">
              <button class="button primary" type="submit">发送给用户（生产）</button>
            </div>
          </form>
          <div class="row-actions" style="margin-top:12px">
            <button class="button" data-action="support-status" data-user-id="${escapeAttr(userID)}" data-status="open" type="button">重开待回复</button>
            <button class="button" data-action="support-status" data-user-id="${escapeAttr(userID)}" data-status="replied" type="button">已处理/无需回复</button>
            <button class="button danger" data-action="support-status" data-user-id="${escapeAttr(userID)}" data-status="closed" type="button">关闭</button>
          </div>
          <p class="small muted">无需给用户回复时，再使用“已处理/无需回复”或“关闭”，并填写处理备注。</p>
        `
        : notice("只读会话", "当前账号只能查看反馈队列和消息，不开放回复、关闭或重开。", "info")
    }
  `;
}

function supportReplyTemplates(): string {
  const templates = [
    "您好，已收到您的反馈。为了更快排查，请补充一下手机型号、App 版本，以及出现问题前后您点了哪些入口。",
    "您好，这个问题我先记录处理。后续如果需要核对账号、订单或礼品卡信息，我会继续在本页回复您。",
    "您好，您可以先回到主聊天页重新发送一次；如果仍失败，请把失败截图发在这里，我继续帮您查。",
  ];
  return `
    <div class="template-row" aria-label="常用回复">
      ${templates
        .map(
          (text) => `
            <button class="button template-button" type="button" data-action="support-template" data-template="${escapeAttr(text)}">${escapeHTML(templateButtonLabel(text))}</button>
          `,
        )
        .join("")}
    </div>
  `;
}

function templateButtonLabel(text: string): string {
  if (text.includes("手机型号")) return "让用户补充信息";
  if (text.includes("记录处理")) return "已记录继续跟进";
  return "请重试并发截图";
}

function supportMessageText(message: AdminSupportMessage): string {
  if (message.body_redacted) return "正文已隐藏";
  return message.body || message.body_excerpt || (message.has_images ? "仅图片" : "无正文");
}

function supportSenderLabel(senderType?: string): string {
  if (senderType === "user") return "用户";
  if (senderType === "admin") return "客服";
  if (senderType === "system") return "系统";
  return senderType || "消息";
}

function supportMessageImages(message: AdminSupportMessage): string {
  const urls = (message.image_urls ?? []).map((url) => safeAdminURL(url, true)).filter(Boolean);
  if (message.images_redacted) {
    return `<div class="small muted" style="margin-top:6px">包含 ${message.image_count} 张图片，图片未在当前后台显示。</div>`;
  }
  if (!message.has_images || !urls.length) {
    return message.has_images ? `<div class="small muted" style="margin-top:6px">包含 ${message.image_count} 张图片，图片地址未通过后台安全展示校验。</div>` : "";
  }
  return `
    <div style="margin-top:8px">
      <div class="small muted" style="margin-bottom:6px">附图 ${urls.length} 张</div>
      <div style="display:flex;gap:8px;flex-wrap:wrap">
        ${urls
          .map(
            (url, index) => `
              <a href="${escapeAttr(url)}" target="_blank" rel="noreferrer" title="查看图片 ${index + 1}" style="display:block">
                <img
                  src="${escapeAttr(url)}"
                  alt="反馈图片 ${index + 1}"
                  style="width:92px;height:92px;object-fit:cover;border-radius:8px;border:1px solid #d9dee7;background:#f6f8fb"
                />
              </a>
            `,
          )
          .join("")}
      </div>
    </div>
  `;
}

function supportConversationMeta(conversation: AdminSupportConversation): string {
  return `
    <dl class="kv support-meta">
      <dt>状态</dt><dd>${supportStatusPill(conversation)}</dd>
      <dt>账号ID</dt><dd>${accountIDLink(conversation.user_id, true)}</dd>
      <dt>手机号</dt><dd>${phoneDisplay(conversation.phone_number, conversation.phone_mask)}</dd>
      <dt>处理人</dt><dd>${escapeHTML(conversation.assigned_to || "未分配")}</dd>
      <dt>最新用户消息</dt><dd>${formatTime(conversation.latest_user_message_at)}</dd>
      <dt>最新后台回复</dt><dd>${formatTime(conversation.latest_admin_message_at)}</dd>
      <dt>关闭时间</dt><dd>${formatTime(conversation.closed_at)}</dd>
      ${conversation.note ? `<dt>备注</dt><dd>${escapeHTML(conversation.note)}</dd>` : ""}
    </dl>
    <div class="divider"></div>
  `;
}

function supportStatusPill(item: AdminSupportConversation): string {
  const status = item.status || (item.needs_reply ? "open" : "replied");
  if (status === "closed") return statusPill("已关闭", "info");
  if (status === "open" || item.needs_reply) return statusPill("待回复", "warn");
  return statusPill("已处理", "ok");
}

function appLogsTable(rows: ClientAppLogEntry[]): string {
  if (!rows.length) return emptyState("没有 App 日志", "当前筛选条件下暂无日志。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>时间</th><th>级别</th><th>事件</th><th>账号ID</th><th>版本</th><th>设备</th><th>消息</th><th>attrs</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("时间", formatTime(row.created_at))}
                ${tableCell("级别", statusPill(row.level))}
                ${tableCell("事件", escapeHTML(row.event))}
                ${tableCell("账号ID", escapeHTML(row.user_id))}
                ${tableCell("版本", escapeHTML([row.app_version_name || String(row.app_version_code || ""), row.build_type].filter(Boolean).join(" / ")))}
                ${tableCell("设备", escapeHTML([row.platform, row.os_version, row.device_model].filter(Boolean).join(" / ")))}
                ${tableCell("消息", escapeHTML(redactSensitiveDisplayText(row.message || "")), "wrap")}
                ${tableCell("attrs", jsonInline(redactSensitiveDisplayValue(row.attrs)), "wrap")}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function appLogSummaryTable(rows: ClientAppLogSummaryEntry[]): string {
  if (!rows?.length) return emptyState("没有聚合", "当前暂无事件聚合。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>事件</th><th>级别</th><th>数量</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("事件", escapeHTML(row.event))}
                ${tableCell("级别", statusPill(row.level))}
                ${tableCell("数量", String(row.count))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function appLogQuickFilters(): string {
  return `
    <div class="quick-filter-strip" aria-label="常用日志筛选">
      ${filterButton("短信发送失败", { event: "auth.sms_send_failed", window: "24h" })}
      ${filterButton("短信登录失败", { event: "auth.sms_login_failed", window: "24h" })}
      ${filterButton("登录闪退", { event: "auth.app_crash", window: "24h" })}
      ${filterButton("普通闪退", { event: "app.crash", window: "24h" })}
      ${filterButton("检查更新失败", { event: "app_update.check_failed", window: "24h" })}
      ${filterButton("下载失败", { event: "app_update.download_failed", window: "24h" })}
      ${filterButton("安装未完成", { event: "app_update.install_not_completed", window: "24h" })}
      ${filterButton("图片上传失败", { event: "image.upload_failed", level: "error", window: "24h" })}
    </div>
  `;
}

function todayAgriTable(rows: AdminDailyAgriEntry[]): string {
  if (!rows.length) return emptyState("没有今日农情记录", "当前暂无今日农情记录。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>日期</th><th>状态</th><th>来源</th><th>标题</th><th>条目/来源</th><th>生成链路</th><th>生成时间</th><th>错误</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("日期", escapeHTML(row.day_cn))}
                ${tableCell("状态", statusPill(row.status))}
                ${tableCell("来源", todayAgriSourcePill(row))}
                ${tableCell("标题", escapeHTML(row.title || "未返回"), "wrap")}
                ${tableCell("条目/来源", `${row.item_count} / ${row.source_count}`)}
                ${tableCell("生成链路", escapeHTML(todayAgriGenerationPathText(row)))}
                ${tableCell("生成时间", formatTime(row.generated_at || row.updated_at))}
                ${tableCell("错误", escapeHTML(row.error || ""), "wrap")}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function todayAgriGenerationPathText(row: AdminDailyAgriEntry): string {
  if (row.source_type === "manual" || row.search_strategy === "manual") return "人工发布";
  if (row.status === "failed") return "自动生成";
  return row.source_count > 0 ? "自动生成 / 带来源" : "自动生成";
}

function todayAgriPreviewCard(row: AdminDailyAgriEntry): string {
  const items = todayAgriItems(row.content);
  const sourceMeta = todayAgriSourceMeta(row);
  return `
    <section class="card today-agri-preview">
      <div class="card-head">
        <div>
          <div class="card-title">${escapeHTML(row.title || "今日农情")}</div>
          <div class="small muted">${escapeHTML(row.day_cn)} · ${sourceMeta} · ${row.item_count} 条 · 来源 ${row.source_count} 个 · ${formatTime(row.generated_at || row.updated_at)}</div>
        </div>
        <div class="pill-row">${statusPill(row.status)}${todayAgriSourcePill(row)}</div>
      </div>
      <div class="card-body">
        <div class="today-agri-items">
          ${items.map((item, index) => todayAgriItemCard(item, index)).join("")}
        </div>
      </div>
    </section>
  `;
}

function todayAgriManualPublishCard(): string {
  const dayCN = defaultManualTodayAgriDay();
  return `
    <section class="card today-agri-manual">
      <div class="card-head">
        <div>
          <div class="card-title">人工发布</div>
          <div class="small muted">默认按北京时间计算，18:00 后自动填次日；发布后会锁定这一天，清晨自动生成只做兜底，不覆盖人工内容。</div>
        </div>
      </div>
      <form id="today-agri-manual-form" class="today-agri-manual-form">
        <div class="form-grid compact">
          <label class="field"><span>日期 YYYYMMDD</span><input class="input" name="day_cn" value="${escapeAttr(dayCN)}" placeholder="20260619" maxlength="8" /></label>
          <label class="field"><span>确认文字</span><input class="input" name="confirmation" placeholder="人工发布 ${escapeAttr(dayCN)}" /></label>
        </div>
        <div class="today-agri-manual-grid">
          ${[1, 2, 3].map((index) => todayAgriManualItemEditor(index)).join("")}
        </div>
        <div class="form-actions">
          <button class="button primary" type="submit">人工发布并锁定</button>
          <span class="small muted">来源可填短来源名，不填也能发布；正文不会保存外部链接。</span>
        </div>
      </form>
    </section>
  `;
}

function todayAgriManualItemEditor(index: number): string {
  return `
    <div class="today-agri-manual-item">
      <div class="today-agri-item-index">${index}</div>
      <div class="today-agri-manual-fields">
        <label class="field"><span>标题 ${index}</span><input class="input" name="item_title_${index}" maxlength="96" placeholder="例如：黄淮海夏玉米抢墒播种进入关键期" /></label>
        <label class="field"><span>摘要 ${index}</span><textarea class="textarea" name="item_summary_${index}" rows="4" maxlength="420" placeholder="写清事实、影响和农事建议，避免夸张和营销话术。"></textarea></label>
        <label class="field"><span>来源 ${index}</span><input class="input" name="item_source_${index}" maxlength="64" placeholder="例如：全国农技中心" /></label>
      </div>
    </div>
  `;
}

function defaultManualTodayAgriDay(): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date());
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value || "";
  const yearNumber = Number(value("year"));
  const monthNumber = Number(value("month"));
  const dayNumber = Number(value("day"));
  const hourNumber = Number(value("hour"));
  const target = new Date(Date.UTC(yearNumber, monthNumber - 1, dayNumber));
  if (hourNumber >= 18) {
    target.setUTCDate(target.getUTCDate() + 1);
  }
  const year = target.getUTCFullYear();
  const month = String(target.getUTCMonth() + 1).padStart(2, "0");
  const day = String(target.getUTCDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

function todayAgriSourcePill(row: AdminDailyAgriEntry): string {
  if (row.source_type === "manual" || row.manual_locked) {
    return statusPill("人工锁定", "info");
  }
  return statusPill("自动", "ok");
}

function todayAgriSourceMeta(row: AdminDailyAgriEntry): string {
  if (row.source_type === "manual" || row.manual_locked) {
    const actor = row.manual_by ? ` · ${escapeHTML(row.manual_by)}` : "";
    const at = row.manual_at ? ` · ${formatTime(row.manual_at)}` : "";
    return `人工锁定${actor}${at}`;
  }
  return "自动生成";
}

function todayAgriItemCard(item: DailyAgriItem, index: number): string {
  const meta = [item.source, item.publishedDate].filter(Boolean).join(" · ");
  const sourceURL = safeAdminURL(item.url, false);
  return `
    <article class="today-agri-item">
      <div class="today-agri-item-index">${index + 1}</div>
      <div class="today-agri-item-body">
        <strong>${escapeHTML(item.title || "未返回标题")}</strong>
        <p>${escapeHTML(item.summary || "未返回摘要")}</p>
        <div class="today-agri-source">
          <span>${escapeHTML(meta || "未返回来源")}</span>
          ${sourceURL ? `<a href="${escapeAttr(sourceURL)}" target="_blank" rel="noreferrer">打开来源</a>` : ""}
        </div>
      </div>
    </article>
  `;
}

interface DailyAgriItem {
  title: string;
  summary: string;
  url: string;
  source: string;
  publishedDate: string;
}

function todayAgriItems(content: JsonValue | undefined): DailyAgriItem[] {
  const root = asJsonObject(content);
  const rawItems = Array.isArray(root?.items) ? root.items : [];
  return rawItems
    .map((item) => asJsonObject(item))
    .filter((item): item is JsonObject => item !== null)
    .map((item) => ({
      title: stringFromJson(item.title),
      summary: stringFromJson(item.summary),
      url: stringFromJson(item.url),
      source: stringFromJson(item.source),
      publishedDate: stringFromJson(item.published_date),
    }))
    .filter((item) => item.title || item.summary || item.url);
}

function auditTable(rows: AdminAuditLogEntry[]): string {
  if (!rows.length) return emptyState("没有审计日志", "当前筛选条件下暂无审计记录。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>时间</th><th>actor</th><th>action</th><th>目标</th><th>目标账号ID</th><th>结果</th><th>状态码</th><th>详情</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("时间", formatTime(row.created_at))}
                ${tableCell("actor", escapeHTML(row.actor))}
                ${tableCell("action", escapeHTML(row.action))}
                ${tableCell("目标", escapeHTML([row.target_type, row.target_id].filter(Boolean).join(" / ")))}
                ${tableCell("目标账号ID", escapeHTML(row.target_user_id || ""))}
                ${tableCell("结果", row.success ? statusPill("success", "ok") : statusPill("failed", "bad"))}
                ${tableCell("状态码", String(row.status_code || ""))}
                ${tableCell("详情", jsonInline(redactSensitiveDisplayValue(row.details)), "wrap")}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function appUpdateConfig(config: AdminAppUpdateConfig): string {
  const updateWillShip = config.enabled && config.config_valid && config.download_artifacts_complete;
  return `
    <dl class="kv">
      <dt>发布开关</dt><dd>${config.enabled ? statusPill("开关已开", "ok") : statusPill("开关已关", "warn")}</dd>
      <dt>是否会下发</dt><dd>${updateWillShip ? statusPill("会下发", "ok") : statusPill("不会下发", "warn")}</dd>
      <dt>配置来源</dt><dd>${escapeHTML(config.source || "env")}</dd>
      <dt>内部版本号 versionCode</dt><dd>${config.latest_version_code || "未配置"}</dd>
      <dt>展示版本 versionName</dt><dd>${escapeHTML(config.latest_version_name || "未配置")}</dd>
      <dt>APK URL</dt><dd>${escapeHTML(config.apk_url || "未配置")}</dd>
      <dt>SHA-256</dt><dd>${escapeHTML(config.apk_sha256 || "未配置")}</dd>
      <dt>文件大小</dt><dd>${formatBytes(config.file_size_bytes)}</dd>
      <dt>强制更新</dt><dd>${config.force_update ? statusPill("强更配置生效", "bad") : statusPill("普通更新", "ok")}</dd>
      <dt>配置合法</dt><dd>${config.config_valid ? statusPill("合法", "ok") : statusPill("异常", "warn")}</dd>
      <dt>正式下载物料</dt><dd>${config.download_artifacts_complete ? statusPill("已齐", "ok") : statusPill("未齐", "warn")}</dd>
      <dt>APK URL 状态</dt><dd>${config.has_apk_url ? statusPill("已配置", "ok") : statusPill("未配置", "warn")}</dd>
      <dt>SHA-256 状态</dt><dd>${config.has_sha256 ? statusPill("已配置", "ok") : statusPill("未配置", "warn")}</dd>
      <dt>文件大小状态</dt><dd>${config.has_file_size ? statusPill("已配置", "ok") : statusPill("未配置", "warn")}</dd>
      <dt>更新说明</dt><dd>${escapeHTML(config.release_notes || "未配置")}</dd>
      <dt>最后更新</dt><dd>${formatTime(config.updated_at)}${config.updated_by ? ` · ${escapeHTML(config.updated_by)}` : ""}</dd>
    </dl>
  `;
}

function appUpdateEditForm(config: AdminAppUpdateConfig): string {
  const disableButton = config.enabled ? `
        <button
          class="button"
          type="button"
          data-action="disable-app-update"
          data-latest-version-code="${escapeAttr(String(config.latest_version_code || 0))}"
          data-latest-version-name="${escapeAttr(config.latest_version_name || "")}"
          data-apk-url="${escapeAttr(config.apk_url || "")}"
          data-apk-sha256="${escapeAttr(config.apk_sha256 || "")}"
          data-release-notes="${escapeAttr(config.release_notes || "")}"
          data-file-size-bytes="${escapeAttr(String(config.file_size_bytes || 0))}"
        >停用当前更新</button>` : "";
  return `
    <form id="app-update-form" class="stack">
      ${notice("怎么发新包", "填内部版本号、安装包下载链接、安装包校验码和文件大小；勾上“对外启用更新”后保存，旧版 App 启动后会静默检查，用户也可手动检查。取消勾选并保存，就是停更。", "info")}
      ${notice("不是系统推送", "当前没有通知权限和推送服务；默认只做普通更新，每个版本最多自动提醒一次，不强制用户安装。更新说明可以留空，App 会显示默认文案。", "warn")}
      <label class="field">
        <span>内部版本号（versionCode）</span>
        <input class="input" name="latest_version_code" type="number" min="0" step="1" value="${escapeAttr(String(config.latest_version_code || ""))}" placeholder="例如 10023" />
      </label>
      <label class="field">
        <span>展示版本（versionName）</span>
        <input class="input" name="latest_version_name" value="${escapeAttr(config.latest_version_name || "")}" placeholder="例如 1.0.23" />
      </label>
      <label class="field">
        <span>安装包下载链接（APK URL）</span>
        <input class="input" name="apk_url" value="${escapeAttr(config.apk_url || "")}" placeholder="https://..." />
      </label>
      <label class="field">
        <span>安装包校验码（SHA-256）</span>
        <input class="input" name="apk_sha256" value="${escapeAttr(config.apk_sha256 || "")}" placeholder="64位十六进制" />
      </label>
      <label class="field">
        <span>文件大小（字节）</span>
        <input class="input" name="file_size_bytes" type="number" min="0" step="1" value="${escapeAttr(String(config.file_size_bytes || ""))}" placeholder="例如 81234567" />
      </label>
      <label class="field">
        <span>更新说明</span>
        <textarea class="textarea" name="release_notes" placeholder="留空默认：优化使用体验。">${escapeHTML(config.release_notes || "")}</textarea>
      </label>
      <label class="checkline"><input type="checkbox" name="enabled" ${config.enabled ? "checked" : ""} /> 对外启用更新</label>
      <div class="row-actions">
        <button class="button primary" type="submit">${config.enabled ? "保存并启用普通更新" : "保存更新配置"}</button>
        ${disableButton}
      </div>
    </form>
  `;
}

function appUpdateEventsTable(rows: AdminAppUpdateEvent[]): string {
  if (!rows.length) return emptyState("没有发布历史", "后台还没有保存过 Android 更新配置。");
  return `
    <table class="table mobile-card-table">
      <thead>
        <tr>
          <th>时间</th><th>动作</th><th>版本</th><th>状态</th><th>物料</th><th>操作人</th><th>说明</th>
        </tr>
      </thead>
      <tbody>
        ${rows
          .map((row) => {
            const version = `${row.latest_version_name || "未命名"} (${row.latest_version_code || 0})`;
            const artifactText = [
              row.apk_url ? "APK" : "缺APK",
              row.apk_sha256 ? "SHA" : "缺SHA",
              row.file_size_bytes ? formatBytes(row.file_size_bytes) : "缺大小",
            ].join(" / ");
            return `
              <tr>
                ${tableCell("时间", formatTime(row.created_at))}
                ${tableCell("动作", statusPill(appUpdateActionLabel(row.action), appUpdateActionLevel(row.action)))}
                ${tableCell("版本", escapeHTML(version))}
                ${tableCell("状态", row.enabled ? statusPill("启用", "ok") : statusPill("停更", "warn"))}
                ${tableCell("物料", `${statusPill(row.config_valid ? "配置合法" : "配置异常", row.config_valid ? "ok" : "warn")} ${statusPill(row.artifacts_complete ? "物料已齐" : "物料未齐", row.artifacts_complete ? "ok" : "warn")}<div class="small muted">${escapeHTML(artifactText)}</div>`)}
                ${tableCell("操作人", escapeHTML(row.actor || "未记录"))}
                ${tableCell("说明", escapeHTML(row.release_notes || ""), "wrap")}
              </tr>
            `;
          })
          .join("")}
      </tbody>
    </table>
  `;
}

function appUpdateActionLabel(action: string): string {
  switch (action) {
    case "force_publish":
      return "强更发布（历史）";
    case "publish":
      return "发布";
    case "disable":
      return "停更";
    default:
      return action || "未知";
  }
}

function appUpdateActionLevel(action: string): "ok" | "warn" | "bad" | "info" {
  switch (action) {
    case "force_publish":
    case "disable":
      return "warn";
    case "publish":
      return "ok";
    default:
      return "info";
  }
}

async function userScopedPage(options: {
  title: string;
  desc: string;
  formID: string;
  inputName: string;
  value: string;
  placeholder: string;
  content: (userID: string) => Promise<string>;
}): Promise<string> {
  let body = "";
  try {
    body = await options.content(options.value.trim());
  } catch (error) {
    body = errorBlock(error);
  }
  return `
    ${pageHead(options.title, options.desc, activeRoute)}
    <form class="inline-form" id="${options.formID}">
      <label class="field">
        <span>账号ID</span>
        <input class="input" name="${options.inputName}" value="${escapeAttr(options.value)}" placeholder="${escapeAttr(options.placeholder)}" />
      </label>
      <button class="button primary" type="submit">查询</button>
    </form>
    ${body}
  `;
}

function logFilterForm(formID: string, key: string, selectedWindow: string): string {
  const hasFilter = Object.values(filterState.get(key) || {}).some((value) => value.trim() !== "") || selectedWindow !== "24h";
  return `
    <form class="filters" id="${formID}">
      ${timeWindowField(key === "app-log" ? "app_log_window" : `${key}_window`, selectedWindow)}
      <label class="field"><span>账号ID</span><input class="input" name="user_id" value="${escapeAttr(readInputValue(key, "user_id"))}" /></label>
      <label class="field"><span>event</span><input class="input" name="event" value="${escapeAttr(readInputValue(key, "event"))}" /></label>
      <label class="field"><span>event前缀</span><input class="input" name="event_prefix" value="${escapeAttr(readInputValue(key, "event_prefix"))}" placeholder="auth. / app_update." /></label>
      <label class="field"><span>平台</span><input class="input" name="platform" value="${escapeAttr(readInputValue(key, "platform"))}" placeholder="android" /></label>
      <label class="field"><span>包类型</span>${selectHTML("build_type", readInputValue(key, "build_type"), [["", "全部"], ["debug", "debug"], ["release", "release"]])}</label>
      <label class="field"><span>版本号</span><input class="input" name="app_version_code" value="${escapeAttr(readInputValue(key, "app_version_code"))}" placeholder="versionCode" /></label>
      <label class="field"><span>版本名</span><input class="input" name="app_version_name" value="${escapeAttr(readInputValue(key, "app_version_name"))}" placeholder="1.0" /></label>
      <label class="field"><span>系统</span><input class="input" name="os_version" value="${escapeAttr(readInputValue(key, "os_version"))}" placeholder="Android 15" /></label>
      <label class="field"><span>设备</span><input class="input" name="device_model" value="${escapeAttr(readInputValue(key, "device_model"))}" placeholder="机型前缀" /></label>
      <label class="field"><span>level</span>${selectHTML("level", readInputValue(key, "level"), [["", "全部"], ["info", "info"], ["warn", "warn"], ["error", "error"]])}</label>
      <button class="button primary" type="submit">查询</button>
      ${hasFilter ? `<button class="button" type="button" data-action="clear-log-filter" data-filter-key="${escapeAttr(key)}">清空</button>` : ""}
    </form>
  `;
}

function timeWindowField(name: string, selected: string): string {
  return `
    <label class="field">
      <span>时间范围</span>
      ${selectHTML(name, selected, [["24h", "最近24小时"], ["7d", "最近7天"], ["30d", "最近30天"]])}
    </label>
  `;
}

function selectHTML(name: string, value: string, options: [string, string][]): string {
  return `
    <select class="select" name="${escapeAttr(name)}">
      ${options.map(([optionValue, label]) => `<option value="${escapeAttr(optionValue)}" ${optionValue === value ? "selected" : ""}>${escapeHTML(label)}</option>`).join("")}
    </select>
  `;
}

function selectOption(value: string, label: string, selected: string): string {
  return `<option value="${escapeAttr(value)}" ${value === selected ? "selected" : ""}>${escapeHTML(label)}</option>`;
}

const filterState = new Map<string, Record<string, string>>();

function captureFilterState(form: HTMLFormElement, key: string): void {
  const values: Record<string, string> = {};
  new FormData(form).forEach((value, name) => {
    if (String(name).endsWith("_window")) return;
    values[String(name)] = String(value).trim();
  });
  filterState.set(key, values);
}

function readFilterState(key: string, defaults: Record<string, string | number | boolean>): Record<string, string | number | boolean | undefined> {
  return { ...defaults, ...(filterState.get(key) || {}) };
}

function readInputValue(key: string, name: string): string {
  return filterState.get(key)?.[name] || "";
}

function openAppLogsWithFilter(filter: { userID?: string; event?: string; eventPrefix?: string; level?: string; window?: string }): void {
  const values: Record<string, string> = {};
  if (filter.userID) values.user_id = filter.userID;
  if (filter.event) values.event = filter.event;
  if (filter.eventPrefix) values.event_prefix = filter.eventPrefix;
  if (filter.level) values.level = filter.level;
  filterState.set("app-log", values);
  pageState.appLogWindow = filter.window || "24h";
  if (isRouteVisible("app-logs") && activeRoute !== "app-logs") {
    location.hash = "app-logs";
    return;
  }
  void render();
}

function formValue(form: HTMLFormElement, key: string): string {
  return String(new FormData(form).get(key) || "").trim();
}

function decodeURLText(value: string): string {
  let current = value;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const decoded = decodeURIComponent(current);
      if (decoded === current) {
        break;
      }
      current = decoded;
    } catch {
      break;
    }
  }
  return current;
}

function isInternalTestApkURL(value: string): boolean {
  const normalized = `${value.trim()} ${decodeURLText(value)}`.toLowerCase();
  return normalized.includes("test-apks") ||
    normalized.includes("debug") ||
    normalized.includes("internal") ||
    normalized.includes("staging");
}

function isOfficialApkURL(value: string): boolean {
  try {
    const url = new URL(value.trim());
    const rawPath = url.pathname.toLowerCase();
    const decodedPath = decodeURLText(url.pathname).toLowerCase();
    const normalized = `${value}\n${decodeURLText(value)}\n${rawPath}\n${decodedPath}`.toLowerCase();
    return url.protocol === "https:" &&
      url.username === "" &&
      url.password === "" &&
      url.search === "" &&
      url.hash === "" &&
      (url.port === "" || url.port === "443") &&
      url.hostname.toLowerCase() === "download.nongjiqiancha.cn" &&
      rawPath.startsWith("/android/releases/") &&
      decodedPath.startsWith("/android/releases/") &&
      rawPath.endsWith(".apk") &&
      decodedPath.endsWith(".apk") &&
      !rawPath.includes("..") &&
      !decodedPath.includes("..") &&
      !normalized.includes("test-apks") &&
      !normalized.includes("debug") &&
      !normalized.includes("internal") &&
      !normalized.includes("staging");
  } catch {
    return false;
  }
}

function isShortLivedSignedApkURL(value: string): boolean {
  try {
    const url = new URL(value.trim());
    const signedKeys = new Set([
      "expires",
      "signature",
      "ossaccesskeyid",
      "security-token",
      "x-oss-expires",
      "x-oss-signature",
      "x-oss-credential",
      "x-oss-security-token",
    ]);
    for (const key of url.searchParams.keys()) {
      if (signedKeys.has(key.toLowerCase())) {
        return true;
      }
    }
  } catch {
    return false;
  }
  return false;
}

function sinceFromWindow(value: string): number {
  const now = Date.now();
  if (value === "30d") return now - 30 * 24 * 60 * 60 * 1000;
  if (value === "7d") return now - 7 * 24 * 60 * 60 * 1000;
  return now - 24 * 60 * 60 * 1000;
}

function healthGrid(health: AdminOverview["health"]): string {
  return `
    <table class="table">
      <tbody>
        ${Object.entries(health).map(([key, value]) => metricRow(labelFor(key), typeof value === "boolean" ? (value ? "true" : "false") : String(value))).join("")}
      </tbody>
    </table>
  `;
}

function monitoringWindowTable(rows: AdminMonitoring["windows"]): string {
  if (!rows.length) return emptyState("没有窗口指标", "当前暂无监控窗口数据。");
  return `
    <table class="table mobile-card-table">
      <thead>
        <tr>
          <th>范围</th><th>新增用户</th><th>登录 session</th><th>问诊量</th><th>图片问诊</th><th>消耗次数</th><th>自动追账</th><th>App异常</th><th>登录排障</th><th>反馈消息</th><th>礼品卡兑换</th><th>后台失败</th>
        </tr>
      </thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("范围", `${escapeHTML(row.label)}<div class="small muted">since ${formatTime(row.since_ms)}</div>`)}
                ${tableCell("新增用户", String(row.new_users))}
                ${tableCell("登录 session", `${row.recent_auth_sessions}<div class="small muted">当前有效 ${row.active_sessions}</div>`)}
                ${tableCell("问诊量", `${row.chat_rounds} / ${row.chat_users}<div class="small muted">去重用户</div>`)}
                ${tableCell("图片问诊", String(row.image_chat_rounds))}
                ${tableCell("消耗次数", String(row.quota_deductions))}
                ${tableCell("自动追账", String(row.quota_consume_pending ?? 0))}
                ${tableCell("App异常", `${row.app_errors} / ${row.app_warns}`)}
                ${tableCell("登录排障", `${row.auth_failures ?? 0}<div class="small muted">闪退 ${row.crash_reports ?? 0}</div>`)}
                ${tableCell("反馈消息", `${row.support_messages}<div class="small muted">${row.support_users} 位去重用户</div>`)}
                ${tableCell("礼品卡兑换", `${row.gift_card_redeems} 成功<div class="small muted">${row.gift_card_failures} 失败</div>`)}
                ${tableCell("后台失败", `${row.audit_failures}<div class="small muted">${row.admin_actions} 次操作</div>`)}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function monitoringQueueCards(report: AdminMonitoring): string {
  const queues = report.queues;
  const update = queues.app_update;
  const giftReady = (queues.gift_card_batch_count ?? 0) > 0 && (queues.gift_card_active ?? 0) > 0;
  const giftLevel = queues.gift_card_failed_attempts ? "warn" : giftReady ? "ok" : "warn";
  const giftBody = `${queues.gift_card_batch_count ?? 0} 个批次 / ${queues.gift_card_total ?? 0} 张总卡；${queues.gift_card_redeemed} 张已兑换；24h 失败 ${queues.gift_card_failed_attempts} 次`;
  const supportBody = `${queues.support_open ?? queues.support_needs_reply} 待回复 / ${queues.support_replied ?? 0} 已处理 / ${queues.support_closed ?? 0} 已关闭`;
  const accountDeletionPending = queues.account_deletion_pending ?? 0;
  const accountDeletionOverdue = queues.account_deletion_overdue ?? 0;
  const accountDeletionBody = accountDeletionOverdue
    ? `${accountDeletionOverdue} 条已超过 15 个工作日；共 ${accountDeletionPending} 条待处理`
    : accountDeletionPending
      ? `${accountDeletionPending} 条待处理；按 15 个工作日内处理`
      : "当前无待处理注销申请";
  const authFailures = queues.auth_failures ?? 0;
  const crashReports = queues.crash_reports ?? 0;
  const quotaPending = queues.quota_consume_pending ?? 0;
  const quotaBody = quotaPending
    ? "系统会继续自动追账；长期无法安全追扣时自动终结为对账记录"
    : "当前无未完成扣次对账";
  const memoryPendingUsers = queues.memory_pending_users ?? 0;
  const memoryPendingJobs = queues.memory_pending_jobs ?? 0;
  const crashRecency = latestCrashText(report.auth_logs);
  const authLevel = crashReports > 0 || authFailures >= 10 ? "bad" : authFailures > 0 ? "warn" : "ok";
  return `
    <div class="queue-grid">
      ${queueCard("服务状态", queues.unready_dependency_count, queues.unready_dependency_count ? "智能分析、登录、Redis 或 OSS 有异常" : "关键服务正常", queues.unready_dependency_count ? "bad" : "ok")}
      ${queueCard("登录排障", authFailures, `最近24小时认证失败；闪退补报 ${crashReports} 条；${crashRecency}`, authLevel)}
      ${queueCard("客服反馈", queues.support_needs_reply, queues.support_oldest_pending_at ? `${supportBody}；最早 ${formatTime(queues.support_oldest_pending_at)}` : supportBody, queues.support_needs_reply ? "warn" : "ok")}
      ${queueCard("账号注销", accountDeletionPending, accountDeletionBody, accountDeletionOverdue ? "bad" : accountDeletionPending ? "warn" : "ok", "account-deletion")}
      ${queueCard("今日农情", dailyAgriStatusText(queues.daily_agri_status), queues.daily_agri_error || "查看最近生成状态", queues.daily_agri_status === "ready" ? "ok" : queues.daily_agri_status === "failed" ? "bad" : "warn")}
      ${queueCard("安装包下载", !update.enabled ? "未发布新版本" : update.download_artifacts_complete ? "物料已齐" : "未齐", updateStatusLine(update), !update.enabled ? "info" : update.config_valid && update.download_artifacts_complete ? "ok" : "warn")}
      ${queueCard("礼品卡兑换", `${queues.gift_card_active} 张可兑换`, giftBody, giftLevel)}
      ${queueCard("扣次自动对账", quotaPending, quotaBody, quotaPending ? "warn" : "ok", "entitlements")}
      ${queueCard("记忆补偿", memoryPendingJobs || memoryPendingUsers, memoryPendingJobs || memoryPendingUsers ? `${memoryPendingUsers} 个账号 / ${memoryPendingJobs} 个冻结任务自动补偿中` : "当前无积压", "ok")}
      ${queueCard("后台操作", queues.audit_failures, "最近24小时失败操作", queues.audit_failures ? "bad" : "ok")}
    </div>
  `;
}

function modelUsagePolicyBlock(rows: AdminMonitoring["model_usage_policy"]): string {
  if (!rows.length) return emptyState("没有智能链路口径", "当前暂无智能分析链路数据。");
  return `
    <div class="stack">
      ${notice("智能分析链路只在后端调用", "当前项目的智能分析能力只由后端统一调用和记录，Android 不保存服务密钥；后台这里只展示用途和联网边界，不展示具体模型名或供应商内部字段。", "info")}
      <div class="table-wrap">
        <table class="table mobile-card-table">
          <thead>
            <tr>
              <th>链路</th><th>后端链路</th><th>响应方式</th><th>触发时机</th><th>联网</th><th>说明</th>
            </tr>
          </thead>
          <tbody>
            ${rows.map((row) => `
              <tr>
                ${tableCell("链路", escapeHTML(row.title))}
                ${tableCell("后端链路", `<strong>${escapeHTML(modelPolicyDisplayText(row))}</strong>`)}
                ${tableCell("响应方式", escapeHTML(modelPolicyProtocolText(row)))}
                ${tableCell("触发时机", escapeHTML(row.trigger || "未返回"))}
                ${tableCell("联网", modelSearchPolicyText(row))}
                ${tableCell("说明", `${escapeHTML(modelPolicyNoteText(row))}<div class="small muted">增强推理：${row.thinking_disabled ? "未启用" : "已启用"}</div>`)}
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

function modelPolicyDisplayText(row: AdminMonitoring["model_usage_policy"][number]): string {
  if (row.title.includes("主聊天")) return "生产问诊链路";
  if (row.title.includes("记忆")) return "生产记忆摘要链路";
  if (row.title.includes("今日农情")) return "生产农情生成链路";
  return "后端生产链路";
}

function modelPolicyProtocolText(row: AdminMonitoring["model_usage_policy"][number]): string {
  const protocol = row.protocol || "";
  if (protocol.includes("流式")) return "流式响应";
  if (protocol.includes("非流式")) return "非流式响应";
  return "后端响应";
}

function modelPolicyNoteText(row: AdminMonitoring["model_usage_policy"][number]): string {
  if (row.title.includes("主聊天")) return "可联网但不强制搜索；Android 不保存服务密钥。";
  if (row.title.includes("记忆")) return "不联网，后端异步维护用户记忆文档。";
  if (row.title.includes("今日农情")) return "自动生成时联网检索并带来源；人工发布锁定当天内容；用户侧不点击外链，不扣问诊次数。";
  return "后端统一调用，前端不保存服务密钥。";
}

function modelSearchPolicyText(row: AdminMonitoring["model_usage_policy"][number]): string {
  if (!row.search_strategy) return row.forced_search ? "强制联网" : "不联网";
  return row.forced_search ? "强制联网" : "可联网";
}

function authTroubleshootingBlock(authLogs: AdminMonitoring["auth_logs"] | undefined): string {
  if (!authLogs) return emptyState("没有登录排障数据", "当前暂无登录排障数据。");
  const failures = authLogs.failures ?? 0;
  const crashReports = authLogs.crash_reports ?? 0;
  const envBlocked = authLogs.env_blocked ?? 0;
  const envWarnings = authLogs.env_warnings ?? 0;
  const loginNetworkFailures = authLogs.login_network_failures ?? 0;
  const crashRecency = latestCrashText(authLogs);
  const level = crashReports > 0 || failures >= 10 ? "bad" : failures > 0 ? "warn" : "ok";
  return `
    <div class="auth-debug-grid">
      <div class="auth-debug-summary ${level}">
        <div>
          <span class="small muted">最近24小时</span>
          <strong>${failures}</strong>
          <p>认证失败；短信 ${authLogs.sms_failures ?? 0}，旧包融合 ${authLogs.fusion_failures ?? 0}，登录前日志 ${authLogs.preauth_count ?? 0}，闪退补报 ${crashReports}。</p>
          <p class="small muted">${escapeHTML(crashRecency)}</p>
          <p class="small muted">新包只走短信验证码；WiFi 或代理环境下也应可用，只要生产 HTTPS、Redis 和短信服务正常。</p>
          <div class="auth-debug-metrics">
            ${authDebugMetric("旧包环境阻断", envBlocked, envBlocked ? "warn" : "ok")}
            ${authDebugMetric("旧包混合网络", envWarnings, envWarnings ? "warn" : "ok")}
            ${authDebugMetric("请求网络失败", loginNetworkFailures, loginNetworkFailures ? "warn" : "ok")}
          </div>
          <p class="small muted">最近出现：${authLogs.last_seen_at ? formatTime(authLogs.last_seen_at) : "暂无"}</p>
        </div>
        <div class="row-actions">
          ${filterButton("全部登录日志", { eventPrefix: "auth.", window: "24h" })}
          ${filterButton("登录前日志", { userID: "preauth", window: "24h" })}
          ${filterButton("请求网络失败", { event: "auth.login_network_failed", window: "24h" })}
          ${filterButton("短信发送失败", { event: "auth.sms_send_failed", window: "24h" })}
          ${filterButton("短信登录失败", { event: "auth.sms_login_failed", window: "24h" })}
          ${filterButton("登录成功", { event: "auth.sms_login_success", window: "24h" })}
          ${filterButton("旧包融合记录", { eventPrefix: "auth.fusion_", window: "24h" })}
          ${filterButton("登录闪退", { event: "auth.app_crash", window: "24h" })}
          ${filterButton("普通闪退", { event: "app.crash", window: "24h" })}
        </div>
      </div>
      <div class="auth-funnel-panel">${authFunnelTable(authLogs.funnel || [])}</div>
      <div class="table-wrap auth-debug-full">${authLogs.top_events?.length ? appLogSummaryTable(authLogs.top_events) : emptyState("暂无登录日志", "最近24小时没有 auth.* 或闪退补报。")}</div>
    </div>
  `;
}

function authFunnelTable(stages: AdminMonitoring["auth_logs"]["funnel"]): string {
  if (!stages?.length) return emptyState("暂无登录阶段漏斗", "当前暂无登录阶段聚合。");
  return `
    <div class="section-title-row">
      <div>
        <strong>登录阶段漏斗</strong>
        <div class="small muted">最近24小时按 App 自动日志归类；未识别的新事件仍会保留在下方 Top 事件。</div>
      </div>
    </div>
    <div class="table-wrap">
      <table class="table mobile-card-table">
        <thead>
          <tr>
            <th>阶段</th><th>状态</th><th>总数</th><th>明确成功</th><th>告警</th><th>错误</th><th>主要事件</th>
          </tr>
        </thead>
        <tbody>
          ${stages.map((stage) => `
            <tr>
              ${tableCell("阶段", `<strong>${escapeHTML(stage.label || stage.key)}</strong><div class="small muted">${escapeHTML(authFunnelStageHint(stage.key))}</div>`)}
              ${tableCell("状态", authFunnelStagePill(stage))}
              ${tableCell("总数", String(stage.total ?? 0))}
              ${tableCell("明确成功", String(stage.successes ?? 0))}
              ${tableCell("告警", String(stage.warnings ?? 0))}
              ${tableCell("错误", String(stage.errors ?? 0))}
              ${tableCell("主要事件", authFunnelEventButtons(stage.top_events || []), "wrap")}
            </tr>
          `).join("")}
        </tbody>
      </table>
    </div>
  `;
}

function authFunnelStageHint(key: string): string {
  const hints: Record<string, string> = {
    sms: "验证码发送和登录校验",
    server_login: "服务端签账号 session 和网络请求",
    crash: "登录页或运行期崩溃补报",
    legacy_fusion: "旧安装包融合认证事件，仅作历史排障",
  };
  return hints[key] || "未归类阶段";
}

function authFunnelStagePill(stage: AdminMonitoring["auth_logs"]["funnel"][number]): string {
  if ((stage.errors ?? 0) > 0) return statusPill("异常", "bad");
  if ((stage.warnings ?? 0) > 0) return statusPill("关注", "warn");
  if ((stage.total ?? 0) > 0) return statusPill("有记录", "ok");
  return statusPill("暂无", "info");
}

function authFunnelEventButtons(events: ClientAppLogSummaryEntry[]): string {
  if (!events.length) return `<span class="muted">暂无</span>`;
  return `
    <div class="row-actions compact">
      ${events.slice(0, 4).map((event) => filterButton(`${authEventLabel(event.event)} · ${event.count}`, { event: event.event, window: "24h" })).join("")}
    </div>
  `;
}

function authEventLabel(event: string): string {
  const labels: Record<string, string> = {
    "auth.fusion_start_requested": "旧包开始",
    "auth.fusion_env_blocked": "旧包环境阻断",
    "auth.fusion_env_warning": "旧包混合网络",
    "auth.fusion_permission_request": "旧包申请权限",
    "auth.fusion_permission_ready": "旧包权限可用",
    "auth.fusion_permission_denied": "旧包拒绝权限",
    "auth.fusion_token_failed": "旧包取 token 失败",
    "auth.fusion_token_refresh_failed": "旧包刷新 token 失败",
    "auth.fusion_token_refresh_skipped": "旧包跳过刷新",
    "auth.fusion_activity_unavailable": "旧包页面不可用",
    "auth.fusion_sdk_init_start": "旧包初始化开始",
    "auth.fusion_sdk_init_failed": "旧包初始化失败",
    "auth.fusion_ui_model_null": "旧包 UI 配置空",
    "auth.fusion_ui_config_failed": "旧包 UI 配置失败",
    "auth.fusion_scene_starting": "旧包拉授权页",
    "auth.fusion_scene_start_invoked": "旧包授权页已拉起",
    "auth.fusion_scene_start_failed": "旧包授权页失败",
    "auth.fusion_scene_cancelled": "旧包授权页取消",
    "auth.fusion_template_finished": "旧包授权页结束",
    "auth.fusion_verify_interrupt": "旧包取号中断",
    "auth.fusion_timeout": "旧包超时",
    "auth.fusion_auth_event": "旧包 SDK 事件",
    "auth.fusion_protocol_load_failed": "旧包协议页失败",
    "auth.fusion_sdk_token_auth_failed": "旧包 SDK 取号失败",
    "auth.fusion_empty_verify_token": "旧包空 verify token",
    "auth.fusion_verify_duplicate": "旧包重复 verify",
    "auth.fusion_halfway_unexpected": "旧包半程回调",
    "auth.fusion_verify_failed": "旧包最终取号失败",
    "auth.fusion_get_phone_for_verification": "旧包取号确认",
    "auth.fusion_callback_attach_failed": "旧包回调绑定失败",
    "auth.fusion_login_success": "旧包融合成功",
    "auth.fusion_login_failed": "旧包换号失败",
    "auth.login_network_failed": "请求网络失败",
    "auth.login_success": "登录成功",
    "auth.login_failed": "登录失败",
    "auth.sms_send_failed": "短信发送失败",
    "auth.sms_login_success": "短信成功",
    "auth.sms_login_failed": "短信校验失败",
    "auth.app_crash": "登录闪退",
    "app.crash": "运行闪退",
  };
  return labels[event] || event.replace(/^auth\./, "");
}

function appUpdateTroubleshootingBlock(updateLogs: AdminMonitoring["app_update_logs"] | undefined): string {
  if (!updateLogs) return emptyState("没有检查更新排障数据", "当前暂无检查更新排障数据。");
  const failures = (updateLogs.check_failures ?? 0) + (updateLogs.download_failures ?? 0) + (updateLogs.install_failures ?? 0);
  const permissionRequired = updateLogs.permission_required ?? 0;
  const level = updateLogs.download_failures || updateLogs.install_failures ? "bad" : failures || permissionRequired ? "warn" : "ok";
  return `
    <div class="auth-debug-grid">
      <div class="auth-debug-summary ${level}">
        <div>
          <span class="small muted">最近24小时</span>
          <strong>${failures}</strong>
          <p>检查失败 ${updateLogs.check_failures ?? 0}，下载失败 ${updateLogs.download_failures ?? 0}，安装未完成 ${updateLogs.install_failures ?? 0}，需要安装权限 ${permissionRequired}。</p>
          <div class="auth-debug-metrics">
            ${authDebugMetric("检查失败", updateLogs.check_failures ?? 0, updateLogs.check_failures ? "warn" : "ok")}
            ${authDebugMetric("下载失败", updateLogs.download_failures ?? 0, updateLogs.download_failures ? "bad" : "ok")}
            ${authDebugMetric("安装未完成", updateLogs.install_failures ?? 0, updateLogs.install_failures ? "bad" : "ok")}
            ${authDebugMetric("权限确认", permissionRequired, permissionRequired ? "warn" : "ok")}
          </div>
          <p class="small muted">最近出现：${updateLogs.last_seen_at ? formatTime(updateLogs.last_seen_at) : "暂无"}</p>
        </div>
        <div class="row-actions">
          ${filterButton("全部更新日志", { eventPrefix: "app_update.", window: "24h" })}
          ${filterButton("检查开始", { event: "app_update.check_started", window: "24h" })}
          ${filterButton("有新版本", { event: "app_update.available", window: "24h" })}
          ${filterButton("没有新版本", { event: "app_update.no_update", window: "24h" })}
          ${filterButton("检查失败", { event: "app_update.check_failed", window: "24h" })}
          ${filterButton("需要安装权限", { event: "app_update.install_permission_required", window: "24h" })}
          ${filterButton("开始下载", { event: "app_update.download_started", window: "24h" })}
          ${filterButton("下载失败", { event: "app_update.download_failed", window: "24h" })}
          ${filterButton("安装页失败", { event: "app_update.install_intent_failed", window: "24h" })}
          ${filterButton("已拉起安装", { event: "app_update.install_started", window: "24h" })}
          ${filterButton("安装完成", { event: "app_update.install_completed", window: "24h" })}
          ${filterButton("安装未完成", { event: "app_update.install_not_completed", window: "24h" })}
        </div>
      </div>
      <div class="table-wrap">${updateLogs.top_events?.length ? appLogSummaryTable(updateLogs.top_events) : emptyState("暂无更新日志", "最近24小时没有 app_update.* 自动日志。")}</div>
    </div>
  `;
}

function authDebugMetric(label: string, value: number, level: "ok" | "warn" | "bad"): string {
  return `
    <span class="auth-debug-metric ${level}">
      <span>${escapeHTML(label)}</span>
      <strong>${value}</strong>
    </span>
  `;
}

function latestCrashText(authLogs: AdminMonitoring["auth_logs"] | undefined): string {
  return authLogs?.latest_crash_at ? `最近闪退：${formatTime(authLogs.latest_crash_at)}` : "24h 无新闪退";
}

function filterButton(label: string, filter: { userID?: string; event?: string; eventPrefix?: string; level?: string; window?: string }): string {
  if (!isRouteVisible("app-logs")) {
    return `<span class="action-muted">${escapeHTML(label)} · 日志入口未开放</span>`;
  }
  return `<button class="button" data-action="open-app-log-filter" data-user-id="${escapeAttr(filter.userID || "")}" data-event="${escapeAttr(filter.event || "")}" data-event-prefix="${escapeAttr(filter.eventPrefix || "")}" data-level="${escapeAttr(filter.level || "")}" data-window="${escapeAttr(filter.window || "24h")}">${escapeHTML(label)}</button>`;
}

function queueCard(title: string, value: string | number, body: string, level: "ok" | "warn" | "bad" | "info", route?: RouteKey): string {
  return `
    <div class="queue-card ${level}">
      <div class="small muted">${escapeHTML(title)}</div>
      <strong>${escapeHTML(String(value))}</strong>
      <span>${escapeHTML(body)}</span>
      ${route ? `<div class="row-actions" style="margin-top:10px">${routeActionButton(route, "打开")}</div>` : ""}
    </div>
  `;
}

function updateStatusLine(update: AdminMonitoring["queues"]["app_update"]): string {
  const version = update.latest_version_code ? `v${update.latest_version_code}${update.latest_version_name ? ` / ${update.latest_version_name}` : ""}` : "未配置版本";
  return `${version}；发布 ${update.enabled ? "已启用" : "未下发"}；配置 ${update.config_valid ? "合法" : "异常"}；APK ${update.has_apk_url ? "已配置" : "未配置"}；SHA ${update.has_sha256 ? "已配" : "缺"}；大小 ${update.has_file_size ? "已配" : "缺"}`;
}

function dailyAgriStatusText(status: string): string {
  const normalized = String(status || "").toLowerCase();
  if (normalized === "ready") return "正常";
  if (normalized === "failed") return "失败";
  if (normalized === "pending") return "等待生成";
  if (normalized === "running") return "生成中";
  if (normalized === "missing") return "未生成";
  if (normalized === "invalid_content") return "内容异常";
  if (normalized === "disabled") return "已停用";
  return "未返回";
}

function regionMetricsTable(rows: AdminMonitoring["top_regions"]): string {
  if (!rows.length) return emptyState("没有地区数据", "最近30天问诊归档中没有可聚合地区。");
  return `
    <table class="table mobile-card-table">
      <thead><tr><th>地区</th><th>轮次</th><th>去重用户</th><th>来源</th><th>最近出现</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                ${tableCell("地区", escapeHTML(row.region))}
                ${tableCell("轮次", String(row.count))}
                ${tableCell("去重用户", String(row.user_count))}
                ${tableCell("来源", escapeHTML([row.source, row.reliability].filter(Boolean).join(" / ") || "未返回"))}
                ${tableCell("最近出现", formatTime(row.last_seen_at))}
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function userRegionOverviewBlock(overview: AdminMonitoring["user_regions"] | undefined): string {
  if (!overview) return emptyState("没有用户地区概览", "当前暂无注册用户和会员用户地区聚合。");
  return `
    <div class="monitor-region-grid">
      ${userRegionPanel(
        "注册用户地区",
        "按账号最近一次已识别地区聚合，用来看目前注册用户大致分布。",
        overview.registered_top || [],
        overview.registered_with_region ?? 0,
        overview.registered_total ?? 0,
      )}
      ${userRegionPanel(
        "当前会员地区",
        "只统计当前仍在有效期内的 Plus / Pro 会员，方便看付费用户主要在哪些地区。",
        overview.member_top || [],
        overview.member_with_region ?? 0,
        overview.member_total ?? 0,
      )}
    </div>
  `;
}

function userRegionPanel(
  title: string,
  desc: string,
  rows: AdminRegionMetric[],
  covered: number,
  total: number,
): string {
  const safeTotal = Math.max(0, total || 0);
  const safeCovered = Math.max(0, covered || 0);
  const coverage = safeTotal > 0 ? Math.round((safeCovered / safeTotal) * 100) : 0;
  return `
    <section class="monitor-region-panel">
      <div class="monitor-region-head">
        <div>
          <strong>${escapeHTML(title)}</strong>
          <p>${escapeHTML(desc)}</p>
        </div>
        <div class="monitor-region-meta">
          <span>${statusPill(`已识别 ${safeCovered}/${safeTotal}`, safeCovered > 0 ? "ok" : "info")}</span>
          <span class="small muted">覆盖 ${coverage}% </span>
        </div>
      </div>
      <div class="table-wrap">${rows.length ? regionMetricsTable(rows) : emptyState("暂时没有地区数据", "当前还没有可用于这组账号的地区聚合。")}</div>
    </section>
  `;
}

function monitoringHero(report: AdminMonitoring): string {
  const worst = monitoringWorstLevel(report);
  const readinessRows = report.launch_readiness || [];
  const readiness = launchReadinessCounts(readinessRows);
  const readinessBlocked = readiness.blocked;
  const readinessAttention = readiness.programAttention + readiness.manualAttention;
  const heroLevel = worst === "bad" || readinessBlocked > 0 ? "bad" : worst === "warn" || readinessAttention > 0 ? "warn" : "ok";
  const heroRoute = primaryMonitoringHeroRoute(report, worst);
  const title =
    worst === "bad"
      ? "需要马上处理"
      : readinessBlocked > 0
        ? "运行正常，上架仍有阻塞"
        : worst === "warn" || readinessAttention > 0
          ? "可推进但要盯"
          : "整体正常";
  const body =
    worst === "bad"
      ? "先处理红色事项，再看 App 错误 Top 和审计失败。"
      : readinessBlocked > 0
        ? `当前服务没有明显中断，但正式上架还有 ${readinessBlocked} 个阻塞项；程序需处理 ${readiness.programAttention} 项，人工确认 ${readiness.manualAttention} 项。`
      : worst === "warn"
        ? "当前没有明确服务中断，但有运营队列需要跟进。"
      : readinessAttention > 0
        ? `当前服务没有明显中断，但上线清单仍有 ${readiness.programAttention} 个程序项、${readiness.manualAttention} 个人工确认项需要跟进。`
        : "关键健康项、App 报错、反馈和礼品卡队列暂时没有明显异常。";
  return `
    <section class="monitor-hero ${heroLevel}">
      <div>
        <div class="monitor-eyebrow">运营监控</div>
        <h2>${escapeHTML(title)}</h2>
        <p>${escapeHTML(body)}</p>
      </div>
      <div class="monitor-hero-meta">
        <span>${statusPill(heroLevel === "ok" ? "正常" : readinessBlocked > 0 ? "上架阻塞" : heroLevel === "warn" ? "关注" : "处理", heroLevel)}</span>
        <span class="small muted">更新时间 ${formatTime(report.now_ms)}</span>
        ${routeActionButton(heroRoute, heroLevel === "ok" ? "继续查看" : "打开重点项")}
      </div>
    </section>
  `;
}

function launchReadinessCounts(rows: AdminMonitoring["launch_readiness"]): {
  ready: number;
  programAttention: number;
  manualAttention: number;
  launchOnlyAttention: number;
  blocked: number;
} {
  const dailyProgramRows = rows.filter((row) => !row.manual && !row.launch_only);
  return {
    ready: rows.filter((row) => row.status === "ready").length,
    programAttention: dailyProgramRows.filter((row) => row.status !== "ready" && row.status !== "blocked").length,
    manualAttention: rows.filter((row) => row.manual && row.status !== "ready" && row.status !== "blocked").length,
    launchOnlyAttention: rows.filter((row) => row.launch_only && row.status !== "ready" && row.status !== "blocked").length,
    blocked: dailyProgramRows.filter((row) => row.status === "blocked").length,
  };
}

function monitoringReadinessSummary(report: AdminMonitoring): string {
  const rows = report.launch_readiness || [];
  const readiness = launchReadinessCounts(rows);
  const next = rows.find((row) => row.status === "blocked") || rows.find((row) => row.status !== "ready");
  return `
    <section class="readiness-summary">
      <div class="readiness-count ok"><span>就绪</span><strong>${readiness.ready}</strong></div>
      <div class="readiness-count warn"><span>程序需处理</span><strong>${readiness.programAttention}</strong></div>
      <div class="readiness-count info"><span>人工确认</span><strong>${readiness.manualAttention}</strong></div>
      <div class="readiness-count bad"><span>上架阻塞</span><strong>${readiness.blocked}</strong></div>
      <div class="readiness-next">
        <span class="small muted">下一步</span>
        <strong>${escapeHTML(next?.title || "继续推进")}</strong>
        <p>${escapeHTML(next?.body || "当前可继续推进登录、礼品卡、智能问诊、图片问诊、反馈和更新配置。")}</p>
        ${routeActionButton(next?.route, "打开")}
      </div>
    </section>
  `;
}

function monitoringOperatorGuide(report: AdminMonitoring): string {
  const worst = monitoringWorstLevel(report);
  const actionCount = report.action_items?.length || 0;
  const readinessRows = report.launch_readiness || [];
  const readiness = launchReadinessCounts(readinessRows);
  const primaryRoute = primaryMonitoringActionRoute(report, worst) || (readiness.blocked > 0 ? "monitoring" : "app-logs");
  const firstText =
    worst === "bad"
      ? "红色表示需要优先处理，通常对应服务异常、App 异常或后台操作失败。"
      : worst === "warn" || readiness.programAttention > 0 || readiness.manualAttention > 0 || readiness.blocked > 0
        ? "黄色表示可以继续推进，但需要区分程序待处理项和人工确认项。"
        : "绿色表示当前后台未发现明显异常，可以继续按照上线清单推进。";
  return `
    <section class="operator-guide">
      <div class="operator-guide-copy">
        <span class="small muted">处理顺序</span>
        <strong>请先查看状态颜色，再进入对应处理入口。</strong>
          <p>${escapeHTML(firstText)} 当前还有 ${actionCount} 个先处理事项、${readiness.blocked} 个上架阻塞项、${readiness.programAttention} 个程序需处理、${readiness.manualAttention} 个人工确认、${readiness.launchOnlyAttention} 个上线准备提醒。</p>
      </div>
      <div class="operator-guide-steps">
        ${operatorGuideStep("1", "当前结论", "查看顶部状态和下一步建议；红色优先处理，黄色优先确认，绿色继续回归。", routeActionButton(primaryRoute, "打开重点项"))}
        ${operatorGuideStep("2", "待处理事项", "优先查看有数量的队列：登录、闪退、反馈、注销、礼品卡、检查更新。", "")}
        ${operatorGuideStep("3", "真机回归", "每完成一项回归后，复查 App 日志、服务健康和使用量是否出现新增异常。", `${routeActionButton("app-logs", "App 日志")}${routeActionButton("health", "服务健康")}`)}
      </div>
    </section>
  `;
}

function operatorGuideStep(index: string, title: string, body: string, actions: string): string {
  return `
    <article class="operator-step">
      <span>${escapeHTML(index)}</span>
      <div>
        <strong>${escapeHTML(title)}</strong>
        <p>${escapeHTML(body)}</p>
        ${actions ? `<div class="row-actions compact">${actions}</div>` : ""}
      </div>
    </article>
  `;
}

function monitoringManualCheckStrip(report: AdminMonitoring): string {
  const rows = (report.launch_readiness || []).filter((row) => row.manual && row.status !== "ready");
  if (!rows.length) return "";
  const visibleRows = rows.slice(0, 4);
  const remaining = rows.length - visibleRows.length;
  return `
    <section class="manual-check-strip">
      <div class="manual-check-head">
        <div>
          <strong>上线人工确认项</strong>
          <p>这些不是系统故障，而是正式上架前需要人工确认的事项。</p>
        </div>
        <span class="small muted">${rows.length} 项待确认</span>
      </div>
      <div class="manual-check-list">
        ${visibleRows
          .map((row) => {
            const level = launchStatusLevel(row.status);
            return `
              <article class="manual-check ${level}">
                <div class="manual-check-copy">
                  <span class="small muted">${escapeHTML(row.owner || "负责人待定")}</span>
                  <strong>${escapeHTML(row.title)}</strong>
                  <p>${escapeHTML(row.body)}</p>
                  ${launchConfirmHint(row)}
                </div>
                <div class="manual-check-actions">
                  ${statusPill(launchStatusText(row.status), level)}
                  ${routeActionButton(row.route, "打开")}
                </div>
              </article>
            `;
          })
          .join("")}
        ${remaining > 0 ? `<div class="manual-check-more">还有 ${remaining} 项在正式上架检查里</div>` : ""}
      </div>
    </section>
  `;
}

function monitoringProgramCheckStrip(report: AdminMonitoring): string {
  const rows = (report.launch_readiness || [])
    .filter((row) => !row.manual && !row.launch_only && row.status !== "ready")
    .sort((left, right) => (left.status === "blocked" ? 0 : 1) - (right.status === "blocked" ? 0 : 1));
  if (!rows.length) return "";
  const visibleRows = rows.slice(0, 4);
  const remaining = rows.length - visibleRows.length;
  return `
    <section class="manual-check-strip program-check-strip">
      <div class="manual-check-head">
        <div>
          <strong>程序需处理项</strong>
          <p>这些通常能通过代码、配置、部署或后台操作推进；正常未发布、未发卡、支付生产验收等上线准备项不混在这里。</p>
        </div>
        <span class="small muted">${rows.length} 项待处理</span>
      </div>
      <div class="manual-check-list">
        ${visibleRows
          .map((row) => {
            const level = launchStatusLevel(row.status);
            return `
              <article class="manual-check ${level}">
                <div class="manual-check-copy">
                  <span class="small muted">${escapeHTML(row.owner || "程序处理")}</span>
                  <strong>${escapeHTML(row.title)}</strong>
                  <p>${escapeHTML(row.body)}</p>
                  ${launchConfirmHint(row)}
                </div>
                <div class="manual-check-actions">
                  ${statusPill(launchStatusText(row.status), level)}
                  ${routeActionButton(row.route, "打开")}
                </div>
              </article>
            `;
          })
          .join("")}
        ${remaining > 0 ? `<div class="manual-check-more">还有 ${remaining} 项在正式上架检查里</div>` : ""}
      </div>
    </section>
  `;
}

function monitoringDecisionGrid(report: AdminMonitoring, today: AdminMonitoring["windows"][number] | undefined, day24: AdminMonitoring["windows"][number] | undefined): string {
  const worst = monitoringWorstLevel(report);
  const primaryRoute = primaryMonitoringActionRoute(report, worst);
  const readinessRows = report.launch_readiness || [];
  const readiness = launchReadinessCounts(readinessRows);
  const readinessBlocked = readiness.blocked;
  const readinessAttention = readiness.programAttention + readiness.manualAttention;
  const loginDepsOK = loginHealthOK(report.health);
  const giftReady = (report.queues.gift_card_batch_count ?? 0) > 0 && (report.queues.gift_card_active ?? 0) > 0;
  const giftWarn = report.queues.gift_card_failed_attempts > 0 || !giftReady;
  const appErrors = day24?.app_errors ?? 0;
  const authFailures = day24?.auth_failures ?? report.queues.auth_failures ?? 0;
  const crashReports = day24?.crash_reports ?? report.queues.crash_reports ?? 0;
  const recentLoginSessions = day24?.recent_auth_sessions ?? 0;
  const authTrouble =
    authFailures +
    (report.auth_logs?.crash_reports ?? 0) +
    (report.auth_logs?.env_blocked ?? 0) +
    (report.auth_logs?.env_warnings ?? 0) +
    (report.auth_logs?.login_network_failures ?? 0);
  const loginLevel: "ok" | "warn" | "bad" | "info" = !loginDepsOK ? "bad" : authTrouble > 0 ? "warn" : recentLoginSessions > 0 ? "ok" : "warn";
  const loginValue = !loginDepsOK ? "检查登录" : authTrouble > 0 ? "看日志" : recentLoginSessions > 0 ? "有登录记录" : "待真机";
  const loginBody = !loginDepsOK
    ? "登录依赖或严格鉴权异常，先打开服务健康。"
    : authTrouble > 0
      ? "已有短信、网络或闪退信号，先看 App 日志里的 auth.*。"
      : recentLoginSessions > 0
        ? "24 小时内已有新登录 session；仍要用真机确认短信收码和验证码登录。"
        : "短信配置正常不等于真机已过；真机测试时重点看验证码收码和生产 HTTPS 可达性。";
  const appQualityLevel = crashReports > 0 || appErrors >= 10 || authFailures >= 10 ? "bad" : appErrors > 0 || authFailures > 0 ? "warn" : "ok";
  const appCrashRecency = latestCrashText(report.auth_logs);
  return `
    <section class="decision-grid">
      ${decisionCard(
        "当前结论",
        worst === "bad" ? "先处理" : readinessBlocked > 0 ? "运行正常 / 上架有阻塞" : worst === "warn" || readinessAttention > 0 ? "可推进但要盯" : "可以继续推进",
        worst === "bad" ? "有红色事项，先点下面入口处理。" : readinessBlocked > 0 ? `服务运行信号暂稳，但正式上架还有 ${readinessBlocked} 个阻塞项；程序需处理 ${readiness.programAttention} 项，人工确认 ${readiness.manualAttention} 项。` : worst === "warn" ? "没有明确中断，但有队列或配置需要看。" : `今日 ${today?.chat_rounds ?? 0} 轮问诊，关键服务正常。`,
        worst === "bad" ? "bad" : readinessBlocked > 0 || readinessAttention > 0 ? "warn" : worst,
        primaryRoute,
      )}
      ${decisionCard(
        "登录与账号ID",
        loginValue,
        loginBody,
        loginLevel,
        authTrouble > 0 ? "app-logs" : "health",
      )}
      ${decisionCard(
        "礼品卡与权益",
        !giftReady ? "先生成卡" : giftWarn ? "看失败" : "可查可兑",
        `${report.queues.gift_card_active} 张可兑换；owner / finance_ops 可看完整码，已兑 ${report.queues.gift_card_redeemed} 张；24h 失败 ${report.queues.gift_card_failed_attempts} 次。`,
        giftWarn ? "warn" : "ok",
        "gift-cards",
      )}
      ${decisionCard(
        "客服反馈",
        report.queues.support_needs_reply ? `${report.queues.support_needs_reply} 待回复` : "队列正常",
        `${report.queues.support_open ?? report.queues.support_needs_reply} 待回复 / ${report.queues.support_replied ?? 0} 已处理 / ${report.queues.support_closed ?? 0} 已关闭。`,
        report.queues.support_needs_reply ? "warn" : "ok",
        "support",
      )}
      ${decisionCard(
        "App 质量",
        appErrors || authFailures ? `${appErrors} 个错误 / ${authFailures} 个登录失败` : "暂无错误",
        `最近24小时 warn ${day24?.app_warns ?? 0} 条，闪退补报 ${crashReports} 条；${appCrashRecency}。`,
        appQualityLevel,
        "app-logs",
      )}
    </section>
  `;
}

function monitoringRegressionChecklist(report: AdminMonitoring): string {
  const authTrouble =
    (report.auth_logs?.failures ?? report.queues.auth_failures ?? 0) +
    (report.auth_logs?.crash_reports ?? 0) +
    (report.auth_logs?.env_blocked ?? 0) +
    (report.auth_logs?.env_warnings ?? 0) +
    (report.auth_logs?.login_network_failures ?? 0);
  const updateTrouble =
    (report.app_update_logs?.check_failures ?? 0) +
    (report.app_update_logs?.download_failures ?? 0) +
    (report.app_update_logs?.install_failures ?? 0);
  const day24 = report.windows.find((item) => item.key === "24h");
  const recentLoginSessions = day24?.recent_auth_sessions ?? 0;
  const chatRounds = day24?.chat_rounds ?? 0;
  const imageRounds = day24?.image_chat_rounds ?? 0;
  const giftCardRedeems = day24?.gift_card_redeems ?? 0;
  const supportMessages = day24?.support_messages ?? 0;
  const updateLogCount = report.app_update_logs?.total ?? 0;
  const appUpdate = report.queues.app_update;
  const updateReady = appUpdate.enabled && appUpdate.config_valid && appUpdate.download_artifacts_complete;
  const updateStatus =
    updateTrouble > 0 ? "看日志" :
    !appUpdate.enabled ? "未下发" :
    !appUpdate.config_valid ? "配置异常" :
    !appUpdate.download_artifacts_complete ? "物料未齐" :
    updateLogCount > 0 ? "有检查" :
    "可测";
  const updateLevel: "ok" | "warn" | "bad" | "info" =
    updateTrouble > 0 ? "warn" :
    !appUpdate.enabled ? "info" :
    updateReady ? "info" :
    "warn";
  const updateBody =
    updateTrouble > 0 ? "已有检查、下载或安装页失败事件，先点 App 日志筛 app_update.*。" :
    !appUpdate.enabled ? "当前没有对外下发新包，这是未发版时的正常状态；发版前需要启用并完成真机覆盖安装验证。" :
    !appUpdate.config_valid ? "检查更新配置非法；至少需要合法版本号，APK 地址必须是 HTTPS。" :
    !appUpdate.download_artifacts_complete ? "正式下载物料未齐，后端不会下发新包；必须补 HTTPS APK、SHA-256 和文件大小。" :
    updateLogCount > 0 ? "24 小时内已有检查更新日志；继续看是否有下载、校验和安装页阶段信号，覆盖安装未跑完前不算正式验收。" :
    "点 App 内检查更新，重点看 HTTPS APK、SHA-256、大小、包名和安装未知应用权限。";
  const items: Array<{
    title: string;
    status: string;
    level: "ok" | "warn" | "bad" | "info";
    body: string;
    route: RouteKey;
    appLogFilter?: { userID?: string; event?: string; eventPrefix?: string; level?: string; window?: string };
  }> = [
    {
      title: "短信验证码登录",
      status: authTrouble > 0 ? "看日志" : recentLoginSessions > 0 ? "有登录" : "待真机",
      level: authTrouble > 0 ? "warn" : recentLoginSessions > 0 ? "ok" : "info",
      body: authTrouble > 0 ? "已有短信、网络或闪退信号，先点 App 日志看 auth.*。" : recentLoginSessions > 0 ? "24 小时内已有新登录 session；继续用真机回归验证码收码和登录。" : "短信配置正常不等于真机已过；测试时重点看验证码收码和生产 HTTPS 可达性。",
      route: "app-logs",
      appLogFilter: { eventPrefix: "auth.", window: "24h" },
    },
    {
      title: "主聊天文字问诊",
      status: chatRounds > 0 ? "有记录" : "待真机",
      level: report.queues.unready_dependency_count > 0 ? "bad" : chatRounds > 0 ? "ok" : "info",
      body: report.queues.unready_dependency_count > 0 ? "先看服务健康，智能分析、登录、Redis 或 OSS 任一异常都会影响主链。" : "发一条纯文字问诊，后台看今日问诊数、App 错误和服务健康。",
      route: "health",
    },
    {
      title: "图片问诊 / 弱网发送",
      status: imageRounds > 0 ? "有记录" : "待真机",
      level: imageRounds > 0 ? "ok" : "info",
      body: "拍照、相册、多图、切后台后回来都要测；失败先看 App 日志和 OSS / 上传存储健康。",
      route: "app-logs",
    },
    {
      title: "礼品卡兑换会员",
      status: giftCardRedeems > 0 ? "有兑换" : report.queues.gift_card_active > 0 ? "可测" : "先生成卡",
      level: giftCardRedeems > 0 || report.queues.gift_card_active > 0 ? "ok" : "warn",
      body: giftCardRedeems > 0 ? "24 小时内已有兑换记录；再回后台查账号ID、卡状态和尝试记录。" : report.queues.gift_card_active > 0 ? "用正式卡码在 Android 设置页兑换，再回后台查账号ID、卡状态和尝试记录。" : "当前没有可兑换 active 卡；先在礼品卡页生成一张，再测 Android 兑换。",
      route: "gift-cards",
    },
    {
      title: "今日农情显示",
      status: dailyAgriStatusText(report.queues.daily_agri_status),
      level: report.queues.daily_agri_status === "ready" ? "ok" : report.queues.daily_agri_status === "failed" ? "bad" : "warn",
      body: report.queues.daily_agri_status === "ready" ? "聊天页卡片和设置入口应能展示标题、摘要和来源；后台可看来源和 Raw JSON 状态。" : "未 ready 时先在今日农情页补跑，不让 App 打开时临时生成。",
      route: "today-agri",
    },
    {
      title: "检查更新",
      status: updateStatus,
      level: updateLevel,
      body: updateBody,
      route: updateTrouble > 0 ? "app-logs" : "app-update",
      appLogFilter: updateTrouble > 0 ? { eventPrefix: "app_update.", window: "24h" } : undefined,
    },
    {
      title: "帮助与反馈",
      status: report.queues.support_needs_reply > 0 ? "待回复" : supportMessages > 0 ? "有记录" : "可测",
      level: report.queues.support_needs_reply > 0 ? "warn" : "ok",
      body: supportMessages > 0 ? "24 小时内已有反馈消息；后台继续看会话、图片、手机号回访字段、回复、关闭和重开状态。" : "App 里发文字和截图反馈，后台看会话、图片、手机号回访字段、回复、关闭和重开状态。",
      route: "support",
    },
  ];
  return `
    <div class="regression-grid">
      ${items
        .map(
          (item) => `
            <article class="regression-item ${item.level}">
              <div class="regression-head">
                <strong>${escapeHTML(item.title)}</strong>
                ${statusPill(item.status, item.level)}
              </div>
              <p>${escapeHTML(item.body)}</p>
              ${item.appLogFilter ? filterButton("打开", item.appLogFilter) : routeActionButton(item.route, "打开")}
            </article>
          `,
        )
        .join("")}
    </div>
  `;
}

function decisionCard(title: string, value: string, body: string, level: "ok" | "warn" | "bad" | "info", route?: RouteKey): string {
  return `
    <article class="decision-card ${level}">
      <div class="decision-top">
        <span>${escapeHTML(title)}</span>
        ${statusPill(level === "ok" ? "正常" : level === "warn" ? "关注" : level === "bad" ? "处理" : "信息", level)}
      </div>
      <strong>${escapeHTML(value)}</strong>
      <p>${escapeHTML(body)}</p>
      ${route && route !== "monitoring" ? routeActionButton(route, "打开") : ""}
    </article>
  `;
}

function monitoringShortcutBar(): string {
  return `
    <div class="shortcut-bar">
      ${shortcutButton("gift-cards", "礼品卡追溯")}
      ${shortcutButton("users", "查用户")}
      ${shortcutButton("app-logs", "看 App 错误")}
      ${shortcutButton("support", "查看反馈队列")}
      ${shortcutButton("account-deletion", "看注销申请")}
      ${shortcutButton("today-agri", "看今日农情")}
      ${shortcutButton("app-update", "更新配置")}
      ${shortcutButton("health", "服务健康")}
    </div>
  `;
}

function shortcutButton(route: RouteKey, label: string): string {
  return routeActionButton(route, label);
}

function monitoringWorstLevel(report: AdminMonitoring): "ok" | "warn" | "bad" {
  const items = report.action_items || [];
  if (items.some((item) => normalizeLevel(item.level) === "bad")) return "bad";
  if (
    (report.queues?.unready_dependency_count ?? 0) > 0 ||
    (report.queues?.audit_failures ?? 0) > 0 ||
    (report.queues?.app_errors ?? 0) >= 10 ||
    String(report.queues?.daily_agri_status || "").toLowerCase() === "failed"
  ) {
    return "bad";
  }
  if (items.some((item) => normalizeLevel(item.level) === "warn")) return "warn";
  if (
    (report.queues?.app_errors ?? 0) > 0 ||
    (report.queues?.support_needs_reply ?? 0) > 0 ||
    (report.queues?.gift_card_failed_attempts ?? 0) > 0
  ) {
    return "warn";
  }
  return "ok";
}

function primaryMonitoringActionRoute(report: AdminMonitoring, level: "ok" | "warn" | "bad"): RouteKey | undefined {
  if (level === "ok") return undefined;
  const item = (report.action_items || []).find((entry) => normalizeLevel(entry.level) === level && isKnownRoute(entry.route));
  return item?.route;
}

function primaryMonitoringHeroRoute(report: AdminMonitoring, level: "ok" | "warn" | "bad"): RouteKey | undefined {
  const actionRoute =
    primaryMonitoringActionRoute(report, level) ||
    (report.action_items || []).find((entry) => normalizeLevel(entry.level) !== "ok" && isKnownRoute(entry.route))?.route;
  if (isRouteVisible(actionRoute) && actionRoute !== "monitoring") return actionRoute;
  const launchRoute = (report.launch_readiness || [])
    .find((row) => row.status !== "ready" && isKnownRoute(row.route) && row.route !== "monitoring")?.route;
  return isRouteVisible(launchRoute) ? launchRoute : undefined;
}

function loginHealthOK(health: AdminOverview["health"]): boolean {
  return health.auth_strict === true &&
    String(health.sms).toLowerCase() === "ok" &&
    String(health.redis).toLowerCase() === "ok";
}

function actionItemList(items: AdminMonitoring["action_items"]): string {
  if (!items.length) return emptyState("没有待处理事项", "当前没有需要马上处理的事项。");
  return `
    <div class="action-list">
      ${items
        .map((item) => {
          const level = normalizeLevel(item.level);
          return `
            <article class="action-item ${level}">
              <div>
                <div class="action-title">${escapeHTML(item.title)} ${item.count ? `<span class="count">${item.count}</span>` : ""}</div>
                <div class="muted small">${escapeHTML(item.body)}</div>
              </div>
              ${actionRouteControl(item.route, "去处理")}
            </article>
          `;
        })
        .join("")}
    </div>
  `;
}

function capabilityGrid(rows: AdminMonitoring["capabilities"]): string {
  if (!rows.length) return emptyState("没有能力状态", "当前暂无后台能力状态。");
  return `
    <div class="capability-grid">
      ${rows
        .map((row) => {
          const status = normalizeCapabilityStatus(row.status);
          return `
            <article class="capability-card ${status}">
              <div class="capability-head">
                <strong>${escapeHTML(row.title)}</strong>
                ${statusPill(capabilityStatusText(row.status), capabilityLevel(row.status))}
              </div>
              <p>${escapeHTML(row.body)}</p>
              ${status === "planned" ? `<span class="action-muted">待接入</span>` : routeActionButton(row.route, "打开")}
            </article>
          `;
        })
        .join("")}
    </div>
  `;
}

function launchReadinessGrid(rows: AdminMonitoring["launch_readiness"]): string {
  if (!rows.length) return emptyState("没有上线检查", "当前暂无上线检查结果。");
  return `
    <div class="launch-grid">
      ${rows
        .map((row) => {
          const level = launchStatusLevel(row.status);
          return `
            <article class="launch-card ${level}">
              <div class="launch-head">
                <strong>${escapeHTML(row.title)}</strong>
                <div class="launch-badges">
                  ${statusPill(launchStatusText(row.status), level)}
                  <span class="launch-kind ${row.launch_only ? "info" : row.manual ? "manual" : "program"}">${row.launch_only ? "上线准备" : row.manual ? "人工确认" : "程序处理"}</span>
                </div>
              </div>
              <p>${escapeHTML(row.body)}</p>
              ${launchConfirmHint(row)}
              <div class="launch-foot">
                <span>${escapeHTML(row.owner || "")}</span>
                ${routeActionButton(row.route, "打开")}
              </div>
            </article>
          `;
        })
        .join("")}
    </div>
  `;
}

function launchConfirmHint(row: AdminMonitoring["launch_readiness"][number]): string {
  if (!row.confirm_hint) return "";
  return `<p class="confirm-hint"><span>确认方式：</span>${escapeHTML(row.confirm_hint)}</p>`;
}

function launchStatusText(status: string): string {
  if (status === "ready") return "就绪";
  if (status === "blocked") return "阻塞";
  return "需处理";
}

function launchStatusLevel(status: string): "ok" | "warn" | "bad" {
  if (status === "ready") return "ok";
  if (status === "blocked") return "bad";
  return "warn";
}

function healthChipGrid(health: AdminOverview["health"]): string {
  return `
    <div class="health-chip-grid">
      ${Object.entries(health)
        .map(([key, value]) => {
          const bad = healthFieldNeedsAttention(key, value);
          return `
            <div class="health-chip ${bad ? "bad" : "ok"}">
              <span>${escapeHTML(labelFor(key))}</span>
              ${healthValuePill(key, value)}
            </div>
          `;
        })
        .join("")}
    </div>
  `;
}

function routeActionButton(route: RouteKey | undefined, label: string): string {
  if (!isRouteVisible(route)) return "";
  return `<button class="button" data-action="route" data-route="${escapeAttr(route)}">${escapeHTML(label)}</button>`;
}

function actionRouteControl(route: string | undefined, label: string): string {
  if (!route) return "";
  if (!isKnownRoute(route)) return `<span class="small muted">入口未配置</span>`;
  return routeActionButton(route, label);
}

function isKnownRoute(route: string | undefined): route is RouteKey {
  return !!route && routes.some((item) => item.key === route);
}

function visibleRoutes(): RouteItem[] {
  return routes.filter((route) => routeAllowedForRole(route, auth?.admin_user.role));
}

function isRouteVisible(route: string | undefined): route is RouteKey {
  if (!isKnownRoute(route)) return false;
  return routeAllowedForRole(routes.find((item) => item.key === route), auth?.admin_user.role);
}

function routeAllowedForRole(route: RouteItem | undefined, role: AdminRole | undefined): boolean {
  if (!route) return false;
  if (!role) return true;
  if (role === "owner") return true;
  return !route.roles || route.roles.includes(role);
}

function currentAdminRole(): AdminRole | undefined {
  return auth?.admin_user.role;
}

function canManageGiftCards(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "finance_ops";
}

function canManagePaymentGrants(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "finance_ops";
}

function canViewGiftCardCodes(): boolean {
  return canManageGiftCards();
}

function canViewAccountPhone(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "support" || role === "finance_ops";
}

function canViewQuotaOutbox(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "ops_readonly" || role === "finance_ops" || role === "auditor";
}

function canManageQuotaOutbox(): boolean {
  return currentAdminRole() === "owner";
}

function canManageSupport(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "support";
}

function canManageAccountDeletion(): boolean {
  return canManageSupport();
}

function canManageTodayAgri(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "content_ops";
}

function canManageAppUpdate(): boolean {
  const role = currentAdminRole();
  return role === "owner" || role === "release_ops";
}

function defaultRoute(): RouteKey {
  return visibleRoutes()[0]?.key || "overview";
}

function roleLabel(role: AdminRole | undefined): string {
  const labels: Record<AdminRole, string> = {
    owner: "主账号",
    ops_readonly: "运维只读",
    support: "客服",
    content_ops: "内容运营",
    release_ops: "发布运营",
    finance_ops: "财务运营",
    auditor: "审计只读",
  };
  return role ? labels[role] || role : "未知角色";
}

function normalizeLevel(level: string): "ok" | "warn" | "bad" | "info" {
  if (level === "bad" || level === "error") return "bad";
  if (level === "warn") return "warn";
  if (level === "ok") return "ok";
  return "info";
}

function normalizeCapabilityStatus(status: string): "ready" | "partial" | "planned" {
  if (status === "ready") return "ready";
  if (status === "partial") return "partial";
  return "planned";
}

function capabilityStatusText(status: string): string {
  if (status === "ready") return "已可用";
  if (status === "partial") return "部分可用";
  return "后续接";
}

function capabilityLevel(status: string): "ok" | "warn" | "bad" | "info" {
  if (status === "ready") return "ok";
  if (status === "partial") return "warn";
  return "info";
}

function healthFieldNeedsAttention(key: string, value: unknown): boolean {
  if (key === "dev_order_endpoints") return value === true;
  if (key === "auth_strict") return value !== true;
  if (key === "dypns" || key === "dypns_fusion" || key === "dypns_sms") return false;
  if (typeof value === "boolean") return false;
  const normalized = String(value).toLowerCase();
  return normalized !== "ok" && normalized !== "oss";
}

function healthSummary(health: AdminOverview["health"]): string {
  const bad = Object.entries(health).filter(([key, value]) => {
    return healthFieldNeedsAttention(key, value);
  });
  return bad.length ? `${bad.length} 项关注` : "正常";
}

function healthHint(key: string, value: unknown): string {
  if (key === "auth_strict") return value ? "公网业务入口应保持严格鉴权" : "生产风险：严格鉴权未开启";
  if (key === "dev_order_endpoints") return value ? "生产风险：开发订单接口开启" : "开发订单接口关闭";
  if (key === "upload_storage") return "当前上传存储后端";
  return "来自后台 overview health";
}

function healthValuePill(key: string, value: unknown): string {
  if (typeof value !== "boolean") return statusPill(String(value), healthFieldNeedsAttention(key, value) ? "bad" : undefined);
  if (key === "dev_order_endpoints") return statusPill(value ? "开启" : "关闭", value ? "bad" : "ok");
  if (key === "auth_strict") return statusPill(value ? "开启" : "关闭", value ? "ok" : "bad");
  return statusPill(value ? "true" : "false", value ? "ok" : "info");
}

function appErrorTopTable(rows: ClientAppLogSummaryEntry[]): string {
  return appLogSummaryTable(rows || []);
}

function roundExcerptList(rows: AdminUserDetail["recent_rounds"]): string {
  if (!rows.length) return emptyState("没有最近问诊", "当前暂无最近问诊摘录。");
  return rows
    .slice(0, 12)
    .map(
      (row) => {
        const userText = row.user_text || row.user_excerpt || "";
        const assistantText = row.assistant_text || row.assistant_excerpt || "";
        const title = `${row.has_images ? "图文问诊" : "文字问诊"} · ${formatTime(row.created_at)}`;
        return `
          <details class="message round-detail">
            <summary>
              <span>${escapeHTML(title)}</span>
              <span class="small muted">${escapeHTML(row.region || "未知地区")}${row.image_count ? ` / ${row.image_count} 图` : ""}</span>
            </summary>
            <div class="round-block">
              <div class="small muted">用户问题</div>
              <div class="round-text">${escapeHTML(userText || "无正文")}</div>
            </div>
            <div class="round-block">
              <div class="small muted">AI 回复</div>
              <div class="round-text muted">${escapeHTML(assistantText || "无回复正文")}</div>
            </div>
          </details>
        `;
      },
    )
    .join("");
}

function compactLogs(rows: ClientAppLogEntry[]): string {
  if (!rows.length) return emptyState("没有最近日志", "该用户当前暂无最近 App 日志。");
  return rows
    .slice(0, 4)
    .map(
      (row) => {
        const safeMessage = redactSensitiveDisplayText(row.message || "");
        return `
          <div class="message">
            <div class="message-head"><strong>${escapeHTML(row.event)}</strong><span>${formatTime(row.created_at)}</span></div>
            <div>${statusPill(row.level)} <span class="muted">${escapeHTML([row.build_type, safeMessage].filter(Boolean).join(" / "))}</span></div>
          </div>
        `;
      },
    )
    .join("");
}

function planningNotice(title: string, body: string): string {
  return notice(title, body, "warn");
}

function notice(title: string, body: string, level = "info"): string {
  const normalized = level === "error" || level === "bad" ? "bad" : level === "warn" ? "warn" : "info";
  return `<div class="notice ${normalized}"><strong>${escapeHTML(title)}</strong><div class="muted" style="margin-top:6px;line-height:1.6">${escapeHTML(body)}</div></div>`;
}

function kpi(label: string, value: string | number, foot: string): string {
  return `
    <section class="card kpi-card">
      <div class="kpi-label">${escapeHTML(label)}</div>
      <div class="kpi-value">${escapeHTML(String(value))}</div>
      <div class="kpi-foot">${escapeHTML(foot)}</div>
    </section>
  `;
}

function metricRow(label: string, value: string | number): string {
  return `<tr><th>${escapeHTML(label)}</th><td>${escapeHTML(String(value))}</td></tr>`;
}

function quotaText(daily: { used?: number; limit?: number; remaining?: number; day_cn?: string }): string {
  const used = daily?.used ?? 0;
  const limit = daily?.limit ?? 0;
  const remaining = daily?.remaining ?? Math.max(limit - used, 0);
  return `${remaining}/${limit} 剩余 <div class="small muted">${escapeHTML(daily?.day_cn || "")} 已用 ${used}</div>`;
}

function statusPill(value: string, forced?: "ok" | "warn" | "bad" | "info"): string {
  const normalized = value.toLowerCase();
  const level =
    forced ||
    (["ok", "ready", "valid", "success", "true", "oss", "free", "plus", "pro", "已配置"].includes(normalized)
      ? "ok"
      : ["warn", "planned", "pending", "false", "未配置"].includes(normalized)
        ? "warn"
        : ["error", "failed", "invalid", "bad"].includes(normalized)
          ? "bad"
          : "info");
  return `<span class="pill ${level}">${escapeHTML(value)}</span>`;
}

function emptyState(title: string, body: string): string {
  return `<div class="empty"><div><strong>${escapeHTML(title)}</strong><span>${escapeHTML(body)}</span></div></div>`;
}

function loadingBlock(label: string): string {
  return `<div class="loading">${escapeHTML(label)}...</div>`;
}

function errorBlock(error: unknown): string {
  const friendly = friendlyError(error);
  return `
    <div class="notice bad">
      <strong>${escapeHTML(friendly.title)}</strong>
      <div style="margin-top:6px;line-height:1.6">${escapeHTML(friendly.body)}</div>
      <div class="small muted" style="margin-top:6px">排障码：${escapeHTML(errorMessage(error))}</div>
    </div>
  `;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return `${error.status} ${error.code}`;
  if (error instanceof Error) return error.message;
  return "unknown_error";
}

function actionErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "csrf_required") return "安全校验已过期，请刷新页面或重新登录。";
    if (error.status === 401) return "登录状态已失效，请重新登录后台。";
    if (error.status === 403) return "当前账号不能执行这个操作。";
    if (error.status === 429) return "操作太频繁，请稍等一会儿再试。";
  }
  return errorMessage(error);
}

function supportReplyErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "body_too_long") return "回复内容太长，请控制在 2000 字以内。";
  }
  return actionErrorMessage(error);
}

function appUpdateSaveErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      invalid_apk_url: "保存失败：APK 地址不符合正式发布要求，请使用 download.nongjiqiancha.cn 下的稳定正式包链接。",
      missing_release_artifacts: "保存失败：启用更新前必须补齐 APK 地址、SHA-256 和文件大小。",
      release_apk_sha_mismatch: "保存失败：APK 的 SHA-256 与填写值不一致，请重新核对正式包。",
      release_apk_size_mismatch: "保存失败：APK 文件大小与填写值不一致，请重新核对正式包。",
      release_confirmation_required: "保存失败：确认文字不正确，启用更新请输入本次 versionCode，停更请输入“停更”。",
      invalid_release_confirmation: "保存失败：确认文字不正确，启用更新请输入本次 versionCode，停更请输入“停更”。",
      test_apk_url_not_allowed: "保存失败：内部测试包链接不能配置到正式检查更新。",
      signed_apk_url_not_allowed: "保存失败：短期签名链接不能配置到正式检查更新，请使用稳定正式包地址。",
    };
    if (messages[error.code]) return messages[error.code];
  }
  return `保存失败：${actionErrorMessage(error)}`;
}

function friendlyError(error: unknown): { title: string; body: string } {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return { title: "登录状态已失效", body: "请重新登录后台，再打开这个页面。" };
    }
    if (error.code === "csrf_required") {
      return { title: "安全校验已过期", body: "请刷新页面；如果仍然出现，请重新登录后台。" };
    }
    if (error.status === 403) {
      return { title: "当前账号不能访问", body: "请用主账号登录后台；如果已经是主账号，请刷新后再试。" };
    }
    if (error.status === 428) {
      return { title: "需要先更新后台密码", body: "请打开账号安全页修改密码，再继续使用其他后台页面。" };
    }
    if (error.status >= 500) {
      return { title: "后台服务暂时异常", body: "请先刷新页面；如果持续出现，再看服务健康和后端日志。" };
    }
    if (error.status === 429) {
      return { title: "操作太频繁", body: "请稍等一会儿再试。" };
    }
    return { title: "请求失败", body: "请稍后重试；如果反复出现，再按排障码查日志。" };
  }
  const text = error instanceof Error ? error.message : "";
  if (text.toLowerCase().includes("failed to fetch") || text.toLowerCase().includes("network")) {
    return { title: "网络连接失败", body: "后台暂时不可达，或当前网络不通。请检查网络后刷新。" };
  }
  return { title: "请求失败", body: "请稍后重试；如果反复出现，再按排障码查日志。" };
}

function labelFor(key: string): string {
  const labels: Record<string, string> = {
    api: "API",
    bailian: "智能分析服务",
    dypns: "旧DYPNS",
    dypns_fusion: "旧融合兼容",
    dypns_sms: "旧短信兼容",
    sms: "短信服务",
    redis: "Redis",
    upload_storage: "上传存储",
    auth_strict: "严格鉴权",
    dev_order_endpoints: "开发订单接口",
  };
  return labels[key] || key;
}

function formatTime(value?: number | null): string {
  if (value == null) return "未返回";
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatPercent(value: number): string {
  if (!Number.isFinite(value) || value <= 0) return "0%";
  return `${Math.round(value * 100)}%`;
}

function formatBytes(value?: number): string {
  if (!value) return "未配置";
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KiB`;
  return `${(value / 1024 / 1024).toFixed(1)} MiB`;
}

function jsonInline(value: JsonValue | unknown): string {
  if (value === undefined || value === null || value === "") return "";
  const text = typeof value === "string" ? value : JSON.stringify(value);
  return escapeHTML(text.length > 260 ? `${text.slice(0, 260)}...` : text);
}

function redactSensitiveDisplayValue(value: JsonValue | unknown): JsonValue | unknown {
  if (value === undefined || value === null) return value;
  if (typeof value === "string") return redactSensitiveDisplayText(value);
  if (typeof value === "number" || typeof value === "boolean") return value;
  if (Array.isArray(value)) return value.map((item) => redactSensitiveDisplayValue(item) as JsonValue);
  if (typeof value !== "object") return value;
  const out: JsonObject = {};
  Object.entries(value as Record<string, unknown>).forEach(([key, item]) => {
    if (isSensitiveDisplayKey(key)) {
      out[key] = "[redacted]";
      return;
    }
    out[key] = redactSensitiveDisplayValue(item) as JsonValue;
  });
  return out;
}

function redactSensitiveDisplayText(raw: string): string {
  let text = String(raw || "");
  text = text.replace(/\b1[3-9](?:[\s\-_.()（）]*\d){9}\b/g, "[phone]");
  text = text.replace(/(bearer\s+)[A-Za-z0-9._~+/=-]{12,}/gi, "$1[redacted]");
  text = text.replace(/\b(sk|ak|pk)-[A-Za-z0-9_-]{12,}\b/g, "[key]");
  text = text.replace(/((?:accesskey(?:id|secret)?|api[_-]?key|token|secret|password|signature(?:nonce)?)\s*[:=]\s*)[^,\s"']+/gi, "$1[redacted]");
  return text;
}

function isSensitiveDisplayKey(key: string): boolean {
  const normalized = key.toLowerCase();
  return (
    normalized.includes("token") ||
    normalized.includes("secret") ||
    normalized.includes("password") ||
    normalized.includes("authorization") ||
    normalized.includes("accesskey") ||
    normalized.includes("api_key") ||
    normalized.includes("apikey") ||
    normalized.includes("phone") ||
    normalized.includes("image_url") ||
    normalized.includes("imageurl") ||
    normalized.includes("url")
  );
}

function safeAdminURL(raw: string | undefined, allowSameOriginPath: boolean): string {
  const value = String(raw || "").trim();
  if (!value) return "";
  try {
    const parsed = new URL(value, window.location.origin);
    if (
      allowSameOriginPath &&
      parsed.origin === window.location.origin &&
      !parsed.search &&
      !parsed.hash &&
      isSafeSupportUploadPath(parsed.pathname)
    ) {
      return parsed.pathname;
    }
    if (allowSameOriginPath) return "";
    if (parsed.protocol !== "https:") return "";
    if (parsed.username || parsed.password) return "";
    return parsed.href;
  } catch {
    return "";
  }
}

function isSafeSupportUploadPath(pathname: string): boolean {
  return /^\/uploads\/support\/[^/\\]+\.jpg$/i.test(pathname) && !pathname.includes("..");
}

function asJsonObject(value: JsonValue | undefined): JsonObject | null {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value : null;
}

function stringFromJson(value: JsonValue | undefined): string {
  if (typeof value === "string") return value.trim();
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return "";
}

function escapeHTML(value: unknown): string {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeAttr(value: unknown): string {
  return escapeHTML(value);
}
