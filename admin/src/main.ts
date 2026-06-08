import "./styles.css";
import { ApiError, apiFetch, getStoredAuth, setStoredAuth, toQuery } from "./api";
import type {
  AdminAppUpdateConfig,
  AdminAuditLogEntry,
  AdminDailyAgriEntry,
  AdminGiftCardAttempt,
  AdminGiftCardBatch,
  AdminGiftCardCreatedCode,
  AdminGiftCardEntry,
  AdminGiftCardSummary,
  AdminMonitoring,
  AdminOrderEntry,
  AdminOverview,
  AdminQuotaLedgerEntry,
  AdminSupportConversation,
  AdminSupportMessage,
  AdminTopupPackEntry,
  AdminRouteKey,
  AdminRole,
  AdminUserDetail,
  AdminUserListEntry,
  AdminUpgradeCredit,
  AuthPayload,
  ClientAppLogEntry,
  ClientAppLogSummaryEntry,
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

const routes: RouteItem[] = [
  { key: "overview", label: "总览", section: "工作台", hint: "可用" },
  { key: "monitoring", label: "监控面板", section: "工作台", hint: "可用" },
  { key: "users", label: "用户管理", section: "用户与增长", hint: "可查", roles: ["ops_readonly", "support", "finance_ops"] },
  { key: "entitlements", label: "会员额度", section: "权益与交易", hint: "用户级只读", roles: ["ops_readonly", "support", "finance_ops"] },
  { key: "orders", label: "订单", section: "权益与交易", hint: "支付后接", roles: ["ops_readonly", "support", "finance_ops"] },
  { key: "gift-cards", label: "礼品卡", section: "权益与交易", hint: "可生成/可追溯", roles: ["finance_ops", "ops_readonly", "auditor"] },
  { key: "support", label: "帮助反馈", section: "运营工作台", hint: "可回复", roles: ["support", "ops_readonly", "auditor"] },
  { key: "app-logs", label: "App日志", section: "运营工作台", hint: "可查", roles: ["ops_readonly", "support", "auditor"] },
  { key: "today-agri", label: "今日农情", section: "运营工作台", hint: "只读状态", roles: ["content_ops", "ops_readonly", "auditor"] },
  { key: "app-update", label: "检查更新", section: "运营工作台", hint: "只读配置", roles: ["release_ops", "ops_readonly", "auditor"] },
  { key: "audit", label: "审计", section: "安全与系统", hint: "可查", roles: ["auditor", "ops_readonly"] },
  { key: "insights", label: "产品洞察", section: "安全与系统", hint: "后续报表" },
  { key: "health", label: "服务健康", section: "安全与系统", hint: "可查" },
];

const appNode = document.querySelector<HTMLDivElement>("#app");
if (!appNode) throw new Error("missing #app");
const app = appNode;

let auth: AuthPayload | null = getStoredAuth();
let activeRoute: RouteKey = routeFromHash();
let lastGiftCardCodes: AdminGiftCardCreatedCode[] = [];
let sidebarScrollTop = 0;
const pageState = {
  userQuery: "",
  userDetailID: "",
  supportUserID: "",
  entitlementUserID: "",
  orderUserID: "",
  giftCardBatchID: "",
  giftCardStatus: "",
  giftCardUserID: "",
  giftCardCodeSuffix: "",
  giftCardAttemptSuccess: "",
  giftCardAttemptReason: "",
  appLogWindow: "24h",
  auditWindow: "24h",
};

window.addEventListener("hashchange", () => {
  activeRoute = routeFromHash();
  if (activeRoute !== "gift-cards") lastGiftCardCodes = [];
  void render();
});

window.addEventListener("admin:unauthorized", () => {
  auth = null;
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
    renderLogin();
  }
}

async function render(): Promise<void> {
  if (!auth) {
    renderLogin();
    return;
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
          所有业务真相来自 Go 管理 API；用户、会员、订单、反馈、日志和今日农情只做后端授权后的只读或受控操作。
          未接入模块会明确展示规划态，不显示伪造成功数据。
        </p>
      </section>
    </main>
  `;
}

function renderLoadingShell(label: string): void {
  captureSidebarScroll();
  app.innerHTML = `
    <div class="app-shell">
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
    <div class="app-shell">
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
  const sections = [...new Set(navRoutes.map((route) => route.section))];
  return `
    <aside class="sidebar">
      <div class="sidebar-head">
        <div class="brand-row">
          ${brandMarkHTML()}
          <div>
            <div class="sidebar-title">农技千查</div>
            <div class="small" style="color:#9ba6b2">Admin Console</div>
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
                .map(
                  (route) => `
                    <button class="nav-item ${route.key === activeRoute ? "active" : ""}" data-action="route" data-route="${route.key}">
                      <span class="nav-label">${escapeHTML(route.label)}</span>
                      <span class="small">${escapeHTML(route.hint)}</span>
                    </button>
                  `,
                )
                .join("")}
            </div>
          `,
        )
        .join("")}
    </aside>
  `;
}

function topbarHTML(): string {
  const user = auth?.admin_user;
  return `
    <header class="topbar">
      <div class="topbar-left">
        <span class="env-badge">Production</span>
        <span class="muted small">管理 API：/admin-api/v1</span>
      </div>
      <div class="topbar-right">
        <span class="role-badge">${escapeHTML(user?.role || "unknown")}</span>
        <span class="small">${escapeHTML(user?.display_name || user?.username || "")}</span>
        <button class="button" data-action="refresh">刷新</button>
        <button class="button" data-action="logout">退出</button>
      </div>
    </header>
  `;
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
      ${kpi("今日问诊", today.chat_rounds, `${today.chat_users} 位用户`)}
      ${kpi("图片问诊", today.image_chat_rounds, "本日包含图片轮次")}
      ${kpi("App错误", today.app_errors, "自动日志 error")}
      ${kpi("未回复反馈", today.support_needs_reply, `${today.support_conversations} 个会话`)}
      ${kpi("今日农情", today.daily_agri_status || "unknown", "当天内容状态")}
    </section>
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
              ${metricRow("活跃 App 登录 session", today.active_auth_sessions)}
              ${metricRow("问诊用户", today.chat_users)}
              ${metricRow("额度扣减", today.quota_deductions)}
              ${metricRow("帮助反馈会话", today.support_conversations)}
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
              : emptyState("没有备注", "后端未返回额外状态说明。")
          }
        </div>
      </section>
    </div>
  `;
}

async function monitoringPage(): Promise<string> {
  const report = await apiFetch<AdminMonitoring>("/admin-api/v1/monitoring");
  const today = report.windows.find((item) => item.key === "today") || report.windows[0];
  const day24 = report.windows.find((item) => item.key === "24h") || report.windows[0];
  return `
    ${pageHead("监控面板", "先看能不能继续测，再处理红色事项；没接通的能力会明确标出来。", "monitoring")}
    ${monitoringHero(report)}
    ${monitoringDecisionGrid(report, today, day24)}
    ${monitoringShortcutBar()}
    <section class="grid kpi">
      ${kpi("服务异常", report.queues.unready_dependency_count, "模型 / 登录 / Redis / OSS")}
      ${kpi("App报错", day24?.app_errors ?? 0, `最近24小时，警告 ${day24?.app_warns ?? 0} 条`)}
      ${kpi("待回复反馈", report.queues.support_needs_reply, report.queues.support_oldest_pending_at ? `最早 ${formatTime(report.queues.support_oldest_pending_at)}` : "当前无等待")}
      ${kpi("今日问诊", today?.chat_rounds ?? 0, `${today?.chat_users ?? 0} 位用户`)}
      ${kpi("礼品卡异常", report.queues.gift_card_failed_attempts, "最近24小时兑换失败")}
      ${kpi("今日农情", dailyAgriStatusText(report.queues.daily_agri_status), report.queues.daily_agri_updated_at ? formatTime(report.queues.daily_agri_updated_at) : "未返回更新时间")}
    </section>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">先处理事项</div><span class="small muted">${report.action_items?.length || 0} 项</span></div>
        <div class="card-body">${actionItemList(report.action_items || [])}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">关键队列</div><span class="small muted">生产巡检</span></div>
        <div class="card-body">${monitoringQueueCards(report)}</div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">服务状态</div></div>
        <div class="card-body">${healthChipGrid(report.health)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">最近使用情况</div><span class="small muted">${formatTime(report.now_ms)}</span></div>
        <div class="table-wrap">${monitoringWindowTable(report.windows)}</div>
      </section>
    </div>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head"><div class="card-title">App错误 Top</div><span class="small muted">最近24小时</span></div>
        <div class="card-body">${appErrorTopTable(report.top_app_errors)}</div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">地区分布</div><span class="small muted">最近30天问诊</span></div>
        <div class="table-wrap">${regionMetricsTable(report.top_regions)}</div>
      </section>
    </div>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">这页不展示什么</div></div>
      <div class="card-body stack">
        ${report.notes?.length ? report.notes.map((note) => notice(note.title, note.body, note.level)).join("") : emptyState("没有备注", "后端未返回监控备注。")}
      </div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">后台能力状态</div><span class="small muted">真实接入情况</span></div>
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
    ${pageHead("用户管理", "按账号ID或脱敏手机号线索查询用户，只展示后端返回的运营字段。", "users")}
    <form class="filters" id="users-filter-form">
      <label class="field wide">
        <span>账号查询</span>
        <input class="input" name="query" value="${escapeAttr(query)}" placeholder="账号ID / 手机号脱敏线索" />
      </label>
      <button class="button primary" type="submit">查询</button>
    </form>
    <div class="detail-grid">
      <section class="card">
        <div class="card-head"><div class="card-title">用户列表</div><span class="small muted">${response.users.length} 条</span></div>
        <div class="table-wrap">${usersTable(response.users)}</div>
      </section>
      <aside id="user-detail-drawer" class="drawer">
        ${pageState.userDetailID ? await userDetailCard(pageState.userDetailID) : emptyState("选择用户", "点击列表中的“详情”查看会员、额度、日志、反馈和订单。")}
      </aside>
    </div>
  `;
}

async function entitlementsPage(): Promise<string> {
  return userScopedPage({
    title: "会员额度",
    desc: "第一版只读核查会员档位、每日额度、加油包、升级补偿和扣次流水。",
    formID: "entitlements-form",
    inputName: "user_id",
    value: pageState.entitlementUserID,
    placeholder: "输入账号ID查询权益",
    content: async (userID) => {
      if (!userID) {
        return planningNotice("全局会员统计尚未接入", "当前管理 API 只提供用户详情内的额度和扣次流水；全局 Free / Plus / Pro 分布、耗尽用户和补偿队列后续由聚合 API 提供。");
      }
      const detail = await fetchUserDetail(userID);
      return `
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
    desc: "支付链路未正式接入前，订单只做用户级只读核查，不提供补发或退款操作。",
    formID: "orders-form",
    inputName: "user_id",
    value: pageState.orderUserID,
    placeholder: "输入账号ID查询订单",
    content: async (userID) => {
      if (!userID) {
        return planningNotice("全局订单页未接入", "后端目前没有正式支付订单聚合接口；支付成功、回调、退款和权益发放异常不能在前端伪造。");
      }
      const detail = await fetchUserDetail(userID);
      return `
        <section class="card">
          <div class="card-head"><div class="card-title">用户订单</div><span class="small muted">${detail.orders.length} 条</span></div>
          <div class="table-wrap">${ordersTable(detail.orders)}</div>
        </section>
      `;
    },
  });
}

async function giftCardsPage(): Promise<string> {
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
  const [summaryResponse, batchesResponse, cardsResponse, attemptsResponse] = await Promise.all([
    apiFetch<{ summary: AdminGiftCardSummary }>("/admin-api/v1/gift-cards/summary"),
    apiFetch<{ batches: AdminGiftCardBatch[] }>("/admin-api/v1/gift-cards/batches?limit=50"),
    apiFetch<{ cards: AdminGiftCardEntry[] }>(`/admin-api/v1/gift-cards/cards${toQuery(cardParams)}`),
    apiFetch<{ attempts: AdminGiftCardAttempt[] }>(`/admin-api/v1/gift-cards/attempts${toQuery(attemptParams)}`),
  ]);
  const cards = cardsResponse.cards;
  const attempts = attemptsResponse.attempts;
  const summary = summaryResponse.summary;
  return `
    ${pageHead("礼品卡", "礼品卡以后端批次、卡、兑换流水和审计为真相；完整卡码只在生成当次展示。", "gift-cards")}
    <section class="grid kpi">
      ${kpi("可用卡", summary.active_count, "全量未兑换")}
      ${kpi("已兑换", summary.redeemed_count, "全量已激活")}
      ${kpi("已作废", summary.void_count, "全量")}
      ${kpi("失败尝试", summary.failed_attempts_24h, "最近24小时")}
      ${kpi("批次数", summary.batch_count, "全量批次")}
      ${kpi("完整卡码", lastGiftCardCodes.length ? "本次可见" : "不保存", lastGiftCardCodes.length ? "离开后不可恢复" : "历史只保留掩码")}
    </section>
    <div class="grid two" style="margin-top:12px">
      ${notice("现在可以怎么用", "这里生成 1 张正式礼品卡，复制完整卡码到 Android 设置里的“礼品卡”兑换；成功后本页按账号ID、批次或卡尾号都能追到激活记录。", "info")}
      ${notice("追溯边界", "后台不保存历史完整卡码，只能用卡掩码、尾号、批次、兑换账号ID、脱敏手机号、地区和兑换尝试流水追溯。", "warn")}
    </div>
    <section class="card">
      <div class="card-head">
        <div class="card-title">生成礼品卡批次</div>
        <span class="small muted">完整卡码只在生成成功当次展示</span>
      </div>
      <div class="card-body">
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
          <label>备注<input name="note" placeholder="发放对象或用途，可为空" /></label>
          <button class="button primary" type="submit">生成</button>
        </form>
        ${createdGiftCardCodesBlock(lastGiftCardCodes)}
      </div>
    </section>
    <section class="card" style="margin-top:12px">
      <div class="card-head"><div class="card-title">追溯筛选</div><span class="small muted">按批次、账号ID或卡尾号定位</span></div>
      <div class="card-body">${giftCardTraceFilterForm()}</div>
    </section>
    <div class="grid two" style="margin-top:12px">
      <section class="card">
        <div class="card-head">
          <div class="card-title">批次</div><span class="small muted">${batchesResponse.batches.length} 个</span>
        </div>
        <div class="table-wrap">${giftCardBatchesTable(batchesResponse.batches)}</div>
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

async function supportPage(): Promise<string> {
  const response = await apiFetch<{ conversations: AdminSupportConversation[] }>(
    "/admin-api/v1/support/conversations?limit=50",
  );
  if (!pageState.supportUserID && response.conversations[0]) {
    pageState.supportUserID = response.conversations[0].user_id;
  }
  const messages = pageState.supportUserID ? await fetchSupportMessages(pageState.supportUserID) : [];
  return `
    ${pageHead("帮助反馈", "按会话查看用户反馈并发送后台回复；回复动作会由后端写审计。", "support")}
    <div class="split">
      <section class="card list-pane">
        <div class="card-head"><div class="card-title">会话队列</div><span class="small muted">${response.conversations.length} 条</span></div>
        ${supportConversationList(response.conversations)}
      </section>
      <section class="card">
        <div class="card-head">
          <div class="card-title">会话详情</div>
          <span class="small muted">${escapeHTML(pageState.supportUserID || "未选择")}</span>
        </div>
        <div class="card-body">
          ${pageState.supportUserID ? supportMessagesBlock(pageState.supportUserID, messages) : emptyState("没有会话", "当前后端未返回帮助与反馈会话。")}
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
  return `
    ${pageHead("今日农情", "查看今日农情生成状态、来源数量和失败原因；补跑/停用操作暂不在前端开放。", "today-agri")}
    <section class="card">
      <div class="card-head">
        <div class="card-title">最近卡片</div>
        <button class="button" disabled>手动补跑未开放</button>
      </div>
      <div class="table-wrap">${todayAgriTable(response.cards)}</div>
    </section>
  `;
}

async function appUpdatePage(): Promise<string> {
  const config = await apiFetch<AdminAppUpdateConfig>("/admin-api/v1/app-update/android");
  return `
    ${pageHead("检查更新", "Android 自有 APK 更新配置只读展示；发布、回滚和强制更新必须走后端审计。", "app-update")}
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
          ${planningNotice("发布 / 回滚未开放", "当前后端读取环境变量配置。后续接发布历史表、二次确认和审计后，再开放修改动作。")}
        </div>
      </section>
    </div>
  `;
}

async function auditPage(): Promise<string> {
  const sinceMs = sinceFromWindow(pageState.auditWindow);
  const params = readFilterState("audit", { since_ms: sinceMs, limit: 100 });
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
    </form>
    <section class="card">
      <div class="card-head"><div class="card-title">审计日志</div><span class="small muted">${response.logs.length} 条</span></div>
      <div class="table-wrap">${auditTable(response.logs)}</div>
    </section>
  `;
}

async function insightsPage(): Promise<string> {
  return `
    ${pageHead("产品洞察", "面向后续脱敏聚合报表，不直接铺完整聊天或图片来源。", "insights")}
    <div class="grid two">
      <section class="card">
        <div class="card-head"><div class="card-title">当前状态</div></div>
        <div class="card-body">
          ${planningNotice("洞察报表未接入", "当前没有 product_insight_reports 聚合 API。后续只展示主题、严重度、影响人数、代表短摘和来源数量。")}
        </div>
      </section>
      <section class="card">
        <div class="card-head"><div class="card-title">候选来源</div></div>
        <div class="card-body">
          <table class="table">
            <tbody>
              ${metricRow("帮助与反馈", "已可通过后台会话查询")}
              ${metricRow("App 自动日志", "已可按 event / level 查询")}
              ${metricRow("问诊归档", "仅限后端脱敏聚合后接入")}
              ${metricRow("订单 / 礼品卡", "正式链路未完成")}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `;
}

async function healthPage(): Promise<string> {
  const overview = await apiFetch<AdminOverview>("/admin-api/v1/overview");
  return `
    ${pageHead("服务健康", "通过后台 overview 返回的健康字段查看 API、模型、认证、Redis 和上传存储状态。", "health")}
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
    <div style="margin-top:12px">${planningNotice("SLS 告警 / 仪表盘未接入此页", "当前页面只读管理 API 返回值。集中日志、告警和自动自愈需要后端聚合接口后再展示。")}</div>
  `;
}

async function handleSubmit(form: HTMLFormElement): Promise<void> {
  if (form.id === "login-form") {
    await submitLogin(form);
    return;
  }
  if (form.id === "users-filter-form") {
    pageState.userQuery = formValue(form, "query");
    pageState.userDetailID = "";
    await render();
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
  if (form.id === "support-reply-form") {
    await submitSupportReply(form);
    return;
  }
  if (form.id === "app-log-form") {
    captureFilterState(form, "app-log");
    pageState.appLogWindow = formValue(form, "app_log_window") || "24h";
    await render();
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
    if (isRouteVisible(route)) {
      location.hash = route;
    }
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
  if (action === "load-user-detail") {
    pageState.userDetailID = button.dataset.userId || "";
    await render();
    return;
  }
  if (action === "support-select") {
    pageState.supportUserID = button.dataset.userId || "";
    await render();
    return;
  }
  if (action === "clear-gift-card-codes") {
    lastGiftCardCodes = [];
    await render();
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
    await voidGiftCard(button.dataset.cardId || "");
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
    activeRoute = routeFromHash();
    await render();
  } catch (error) {
    renderLogin(`登录失败：${errorMessage(error)}`);
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
  renderLogin();
}

async function submitSupportReply(form: HTMLFormElement): Promise<void> {
  const userID = formValue(form, "user_id");
  const body = formValue(form, "body");
  if (!userID || !body) return;
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    await apiFetch("/admin-api/v1/support/messages", {
      method: "POST",
      json: { user_id: userID, body, images: [] },
    });
    pageState.supportUserID = userID;
    await render();
  } finally {
    if (button) button.disabled = false;
  }
}

async function submitGiftCardBatch(form: HTMLFormElement): Promise<void> {
  const name = formValue(form, "name");
  const tier = formValue(form, "tier") || "plus";
  const quantity = Number(formValue(form, "quantity") || "1");
  const durationDays = Number(formValue(form, "duration_days") || "30");
  const note = formValue(form, "note");
  const tierLabel = tier === "pro" ? "Pro" : "Plus";
  if (
    !window.confirm(
      `确认生成 ${quantity} 张 ${tierLabel} ${durationDays} 天礼品卡？\n\n这些卡码一旦发出即可兑换真实会员权益。完整卡码只会在本次生成结果中显示，请确认数量和档位无误。`,
    )
  ) {
    return;
  }
  const button = form.querySelector<HTMLButtonElement>("button[type='submit']");
  if (button) button.disabled = true;
  try {
    const response = await apiFetch<{ codes: AdminGiftCardCreatedCode[] }>("/admin-api/v1/gift-cards/batches", {
      method: "POST",
      json: { name, tier, quantity, duration_days: durationDays, note },
    });
    lastGiftCardCodes = response.codes || [];
    await render();
  } catch (error) {
    lastGiftCardCodes = [];
    app.insertAdjacentHTML("afterbegin", errorBlock(error));
  } finally {
    if (button) button.disabled = false;
  }
}

async function voidGiftCard(cardID: string): Promise<void> {
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
  try {
    await apiFetch("/admin-api/v1/gift-cards/void", {
      method: "POST",
      json: { card_id: cardID, reason: trimmedReason },
    });
    lastGiftCardCodes = [];
    await render();
  } catch (error) {
    window.alert(`作废失败：${errorMessage(error)}`);
  }
}

async function fetchUserDetail(userID: string): Promise<AdminUserDetail> {
  return apiFetch<AdminUserDetail>(`/admin-api/v1/users/detail${toQuery({ user_id: userID })}`);
}

async function userDetailCard(userID: string): Promise<string> {
  try {
    const detail = await fetchUserDetail(userID);
    return `
      <section class="card">
        <div class="card-head">
          <div class="card-title">用户详情</div>
          ${statusPill(detail.user.tier || "free")}
        </div>
        <div class="card-body stack">
          ${userKV(detail.user)}
          <div class="divider"></div>
          <div>
            <div class="card-title" style="margin-bottom:8px">最近问诊</div>
            ${roundExcerptList(detail.recent_rounds)}
          </div>
          <div>
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

async function fetchSupportMessages(userID: string): Promise<AdminSupportMessage[]> {
  const response = await apiFetch<{ messages: AdminSupportMessage[] }>(
    `/admin-api/v1/support/messages${toQuery({ user_id: userID })}`,
  );
  return response.messages;
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
  `;
}

function usersTable(users: AdminUserListEntry[]): string {
  if (!users.length) return emptyState("没有用户数据", "后端未返回匹配用户。");
  return `
    <table class="table">
      <thead>
        <tr>
          <th>账号ID</th><th>会员</th><th>今日额度</th><th>最近问诊</th><th>地区</th><th>错误</th><th>反馈</th><th>操作</th>
        </tr>
      </thead>
      <tbody>
        ${users
          .map(
            (user) => `
              <tr>
                <td>
                  <div class="truncate" style="max-width:220px">${escapeHTML(user.user_id)}</div>
                  <div class="small muted">${escapeHTML(user.phone_mask || "未返回手机号")}</div>
                </td>
                <td>${statusPill(user.tier || "free")}</td>
                <td>${quotaText(user.daily)}</td>
                <td>${formatTime(user.last_seen_at)}<div class="small muted">${user.round_total} 轮</div></td>
                <td>${escapeHTML(user.last_region || "未知")}<div class="small muted">${escapeHTML([user.last_region_source, user.last_region_reliability].filter(Boolean).join(" / "))}</div></td>
                <td>${user.error_count_24h ? statusPill(String(user.error_count_24h), "bad") : statusPill("0", "ok")}</td>
                <td>${user.support_needs_reply ? statusPill("待回复", "warn") : `${user.support_message_count || 0} 条`}</td>
                <td><button class="button" data-action="load-user-detail" data-user-id="${escapeAttr(user.user_id)}">详情</button></td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function userKV(user: AdminUserListEntry): string {
  return `
    <dl class="kv">
      <dt>账号ID</dt><dd>${escapeHTML(user.user_id)}</dd>
      <dt>手机号</dt><dd>${escapeHTML(user.phone_mask || "未返回")}</dd>
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

function entitlementSummary(detail: AdminUserDetail): string {
  const user = detail.user;
  return `
    <dl class="kv">
      <dt>账号ID</dt><dd>${escapeHTML(user.user_id)}</dd>
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

function quotaLedgerTable(rows: AdminQuotaLedgerEntry[]): string {
  if (!rows.length) return emptyState("没有扣次流水", "后端未返回该用户额度流水。");
  return `
    <table class="table">
      <thead><tr><th>ID</th><th>日期</th><th>来源</th><th>delta</th><th>client_msg_id</th><th>时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${row.id}</td><td>${escapeHTML(row.day_cn)}</td><td>${escapeHTML(row.source)}</td><td>${row.delta}</td>
                <td>${escapeHTML(row.client_msg_id)}</td><td>${formatTime(row.created_at)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function ordersTable(rows: AdminOrderEntry[]): string {
  if (!rows.length) return emptyState("没有订单数据", "支付未正式接入，或该用户没有订单记录。");
  return `
    <table class="table">
      <thead><tr><th>订单</th><th>类型</th><th>金额</th><th>状态</th><th>创建时间</th><th>结果</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${escapeHTML(row.order_id)}</td><td>${escapeHTML(row.type)}</td><td>${escapeHTML(row.amount)}</td>
                <td>${statusPill(row.status)}</td><td>${formatTime(row.created_at)}</td><td class="wrap">${jsonInline(row.result)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function topupPacksTable(rows: AdminTopupPackEntry[]): string {
  if (!rows.length) return emptyState("没有加油包", "该用户没有加油包记录。");
  return `
    <table class="table">
      <thead><tr><th>包</th><th>订单</th><th>状态</th><th>初始</th><th>已用</th><th>剩余</th><th>到期</th><th>创建时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${escapeHTML(row.pack_id)}</td><td>${escapeHTML(row.order_id || "")}</td><td>${statusPill(row.status)}</td>
                <td>${row.initial}</td><td>${row.used}</td><td>${row.remaining}</td><td>${formatTime(row.expire_at)}</td><td>${formatTime(row.created_at)}</td>
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
    <table class="table">
      <thead><tr><th>账号ID</th><th>剩余次数</th><th>到期</th><th>更新时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${escapeHTML(row.user_id)}</td><td>${row.remaining}</td><td>${formatTime(row.expire_at)}</td><td>${formatTime(row.updated_at)}</td>
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
      <div class="muted" style="margin-top:6px">完整卡码只显示在当前结果中；离开本页、刷新或清除后，后台只保留掩码和 hash。</div>
      <button class="button" type="button" data-action="clear-gift-card-codes" style="margin-top:10px">清除本次卡码</button>
      <div class="table-wrap" style="margin-top:10px">
        <table class="table">
          <thead><tr><th>完整卡码</th><th>档位</th><th>天数</th><th>有效至</th></tr></thead>
          <tbody>
            ${rows
              .map(
                (row) => `
                  <tr>
                    <td class="mono">${escapeHTML(row.code)}</td><td>${statusPill(row.tier)}</td><td>${row.duration_days}</td><td>${formatTime(row.valid_until)}</td>
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
      <label>失败原因<input name="failure_reason" value="${escapeAttr(pageState.giftCardAttemptReason)}" placeholder="gift_card_not_found" /></label>
      <button class="button primary" type="submit">查询</button>
      <button class="button" type="button" data-action="clear-gift-card-filter">清空</button>
    </form>
  `;
}

function giftCardFailureReasonsBlock(rows: AdminGiftCardSummary["failure_reasons"]): string {
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

function giftCardBatchesTable(rows: AdminGiftCardBatch[]): string {
  if (!rows.length) return emptyState("没有礼品卡批次", "还没有创建礼品卡批次。");
  return `
    <table class="table">
      <thead><tr><th>批次</th><th>档位</th><th>天数</th><th>总数</th><th>未用</th><th>已兑</th><th>作废</th><th>有效期</th><th>创建</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td><div>${escapeHTML(row.name || row.batch_id)}</div><div class="small muted">${escapeHTML(row.batch_id)}</div></td>
                <td>${statusPill(row.tier)}</td><td>${row.duration_days}</td><td>${row.quantity}</td><td>${row.active_count}</td><td>${row.redeemed_count}</td><td>${row.void_count}</td>
                <td>${formatTime(row.valid_from)}<div class="small muted">至 ${formatTime(row.valid_until)}</div></td><td>${formatTime(row.created_at)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function giftCardTable(rows: AdminGiftCardEntry[]): string {
  if (!rows.length) return emptyState("没有礼品卡", "后端未返回礼品卡记录。");
  return `
    <table class="table">
      <thead><tr><th>卡</th><th>档位</th><th>状态</th><th>激活账号ID</th><th>兑换时间</th><th>会员到期</th><th>地区</th><th>操作</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td><div class="mono">${escapeHTML(row.code_mask)}</div><div class="small muted">${escapeHTML(row.card_id)} / 尾号 ${escapeHTML(row.code_suffix || "")}</div><div class="small muted">${escapeHTML(row.batch_id)}</div></td>
                <td>${statusPill(row.tier)}</td><td>${statusPill(row.status)}</td>
                <td>${row.redeemed_user_id ? `<button class="link-button" data-action="load-user-detail" data-user-id="${escapeAttr(row.redeemed_user_id)}">${escapeHTML(row.redeemed_user_id)}</button>` : ""}<div class="small muted">${escapeHTML(row.redeemed_phone_mask || "")}</div></td>
                <td>${formatTime(row.redeemed_at)}</td><td>${formatTime(row.membership_expire_at)}</td>
                <td>${escapeHTML(row.redeemed_region || "")}<div class="small muted">${escapeHTML([row.redeemed_region_source, row.redeemed_region_reliability].filter(Boolean).join(" / "))}</div></td>
                <td>${row.status === "active" ? `<button class="button danger" data-action="void-gift-card" data-card-id="${escapeAttr(row.card_id)}">作废</button>` : `<span class="small muted">${row.status === "void" ? `已作废 ${formatTime(row.voided_at)}` : "无操作"}</span>`}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function giftCardAttemptsTable(rows: AdminGiftCardAttempt[]): string {
  if (!rows.length) return emptyState("没有兑换尝试", "后端未返回兑换尝试记录。");
  return `
    <table class="table">
      <thead><tr><th>ID</th><th>卡尾号</th><th>账号ID</th><th>结果</th><th>原因</th><th>地区</th><th>IP</th><th>时间</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${row.id}</td><td class="mono">${escapeHTML(row.code_suffix || "")}</td><td>${row.user_id ? `<button class="link-button" data-action="load-user-detail" data-user-id="${escapeAttr(row.user_id)}">${escapeHTML(row.user_id)}</button>` : ""}</td>
                <td>${row.success ? statusPill("success") : statusPill("failed")}</td><td>${escapeHTML(row.failure_reason || "")}</td>
                <td>${escapeHTML(row.region || "")}<div class="small muted">${escapeHTML([row.region_source, row.region_reliability].filter(Boolean).join(" / "))}</div></td>
                <td>${escapeHTML(row.masked_ip || "")}</td><td>${formatTime(row.created_at)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function supportConversationList(conversations: AdminSupportConversation[]): string {
  if (!conversations.length) return emptyState("没有反馈会话", "后端未返回帮助与反馈会话。");
  return conversations
    .map(
      (item) => `
        <button class="selectable-row ${item.user_id === pageState.supportUserID ? "active" : ""}" data-action="support-select" data-user-id="${escapeAttr(item.user_id)}">
          <strong class="truncate">${escapeHTML(item.user_id)}</strong>
          <span class="small muted truncate">${escapeHTML(item.latest_message.body_excerpt || item.latest_message.body || "无正文")}</span>
          <span>${item.needs_reply ? statusPill("待回复", "warn") : statusPill("已处理", "ok")} <span class="small muted">${item.message_count} 条</span></span>
        </button>
      `,
    )
    .join("");
}

function supportMessagesBlock(userID: string, messages: AdminSupportMessage[]): string {
  return `
    <div class="message-list">
      ${
        messages.length
          ? messages
              .map(
                (message) => `
                  <article class="message ${escapeAttr(message.sender_type)}">
                    <div class="message-head">
                      <strong>${escapeHTML(message.sender_type)}</strong>
                      <span>${formatTime(message.created_at)}</span>
                    </div>
                    <div>${escapeHTML(message.body || message.body_excerpt || "")}</div>
                    ${message.has_images ? `<div class="small muted" style="margin-top:6px">包含 ${message.image_count} 张图片，图片 URL 不在列表展开。</div>` : ""}
                  </article>
                `,
              )
              .join("")
          : emptyState("没有消息", "当前会话未返回消息明细。")
      }
    </div>
    <div class="divider"></div>
    <form id="support-reply-form" class="stack">
      <input type="hidden" name="user_id" value="${escapeAttr(userID)}" />
      <label class="field">
        <span>后台回复</span>
        <textarea class="textarea" name="body" placeholder="只写必要的客服回复，不包含密钥、手机号全文或内部排障细节。"></textarea>
      </label>
      <button class="button primary" type="submit">发送回复</button>
    </form>
  `;
}

function appLogsTable(rows: ClientAppLogEntry[]): string {
  if (!rows.length) return emptyState("没有 App 日志", "当前筛选条件下后端未返回日志。");
  return `
    <table class="table">
      <thead><tr><th>时间</th><th>级别</th><th>事件</th><th>账号ID</th><th>版本</th><th>设备</th><th>消息</th><th>attrs</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${formatTime(row.created_at)}</td><td>${statusPill(row.level)}</td><td>${escapeHTML(row.event)}</td>
                <td>${escapeHTML(row.user_id)}</td><td>${escapeHTML(row.app_version_name || String(row.app_version_code || ""))}</td>
                <td>${escapeHTML([row.platform, row.os_version, row.device_model].filter(Boolean).join(" / "))}</td>
                <td class="wrap">${escapeHTML(row.message || "")}</td><td class="wrap">${jsonInline(row.attrs)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function appLogSummaryTable(rows: ClientAppLogSummaryEntry[]): string {
  if (!rows?.length) return emptyState("没有聚合", "后端未返回事件聚合。");
  return `
    <table class="table">
      <thead><tr><th>事件</th><th>级别</th><th>数量</th></tr></thead>
      <tbody>${rows.map((row) => `<tr><td>${escapeHTML(row.event)}</td><td>${statusPill(row.level)}</td><td>${row.count}</td></tr>`).join("")}</tbody>
    </table>
  `;
}

function todayAgriTable(rows: AdminDailyAgriEntry[]): string {
  if (!rows.length) return emptyState("没有今日农情记录", "后端未返回 daily_agri_cards。");
  return `
    <table class="table">
      <thead><tr><th>日期</th><th>状态</th><th>标题</th><th>条目/来源</th><th>模型</th><th>生成时间</th><th>错误</th><th>内容</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${escapeHTML(row.day_cn)}</td><td>${statusPill(row.status)}</td><td class="wrap">${escapeHTML(row.title || "未返回")}</td>
                <td>${row.item_count} / ${row.source_count}</td><td>${escapeHTML([row.model, row.search_strategy].filter(Boolean).join(" / "))}</td>
                <td>${formatTime(row.generated_at || row.updated_at)}</td><td class="wrap">${escapeHTML(row.error || "")}</td>
                <td class="wrap">${jsonInline(row.content || row.sources)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function auditTable(rows: AdminAuditLogEntry[]): string {
  if (!rows.length) return emptyState("没有审计日志", "当前筛选条件下后端未返回审计记录。");
  return `
    <table class="table">
      <thead><tr><th>时间</th><th>actor</th><th>action</th><th>目标</th><th>目标账号ID</th><th>结果</th><th>状态码</th><th>详情</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${formatTime(row.created_at)}</td><td>${escapeHTML(row.actor)}</td><td>${escapeHTML(row.action)}</td>
                <td>${escapeHTML([row.target_type, row.target_id].filter(Boolean).join(" / "))}</td>
                <td>${escapeHTML(row.target_user_id || "")}</td><td>${row.success ? statusPill("success", "ok") : statusPill("failed", "bad")}</td>
                <td>${row.status_code || ""}</td><td class="wrap">${jsonInline(row.details)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function appUpdateConfig(config: AdminAppUpdateConfig): string {
  return `
    <dl class="kv">
      <dt>versionCode</dt><dd>${config.latest_version_code || "未配置"}</dd>
      <dt>versionName</dt><dd>${escapeHTML(config.latest_version_name || "未配置")}</dd>
      <dt>APK URL</dt><dd>${escapeHTML(config.apk_url || "未配置")}</dd>
      <dt>SHA-256</dt><dd>${escapeHTML(config.apk_sha256 || "未配置")}</dd>
      <dt>文件大小</dt><dd>${formatBytes(config.file_size_bytes)}</dd>
      <dt>强制更新</dt><dd>${config.force_update ? statusPill("true", "warn") : statusPill("false", "ok")}</dd>
      <dt>配置合法</dt><dd>${config.config_valid ? statusPill("合法", "ok") : statusPill("异常", "warn")}</dd>
      <dt>正式下载物料</dt><dd>${config.download_artifacts_complete ? statusPill("已齐", "ok") : statusPill("未齐", "warn")}</dd>
      <dt>APK URL 状态</dt><dd>${config.has_apk_url ? statusPill("已配置", "ok") : statusPill("未配置", "warn")}</dd>
      <dt>SHA-256 状态</dt><dd>${config.has_sha256 ? statusPill("已配置", "ok") : statusPill("未配置", "warn")}</dd>
      <dt>文件大小状态</dt><dd>${config.has_file_size ? statusPill("已配置", "ok") : statusPill("未配置", "warn")}</dd>
      <dt>release notes</dt><dd>${escapeHTML(config.release_notes || "未配置")}</dd>
    </dl>
  `;
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
  return `
    <form class="filters" id="${formID}">
      ${timeWindowField(key === "app-log" ? "app_log_window" : `${key}_window`, selectedWindow)}
      <label class="field"><span>账号ID</span><input class="input" name="user_id" value="${escapeAttr(readInputValue(key, "user_id"))}" /></label>
      <label class="field"><span>event</span><input class="input" name="event" value="${escapeAttr(readInputValue(key, "event"))}" /></label>
      <label class="field"><span>level</span>${selectHTML("level", readInputValue(key, "level"), [["", "全部"], ["info", "info"], ["warn", "warn"], ["error", "error"]])}</label>
      <button class="button primary" type="submit">查询</button>
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

function formValue(form: HTMLFormElement, key: string): string {
  return String(new FormData(form).get(key) || "").trim();
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
  if (!rows.length) return emptyState("没有窗口指标", "后端未返回监控窗口数据。");
  return `
    <table class="table">
      <thead>
        <tr>
          <th>范围</th><th>新增用户</th><th>登录 session</th><th>问诊量</th><th>图片问诊</th><th>消耗次数</th><th>App异常</th><th>反馈消息</th><th>礼品卡兑换</th><th>后台失败</th>
        </tr>
      </thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${escapeHTML(row.label)}<div class="small muted">since ${formatTime(row.since_ms)}</div></td>
                <td>${row.new_users}</td>
                <td>${row.recent_auth_sessions}<div class="small muted">当前有效 ${row.active_sessions}</div></td>
                <td>${row.chat_rounds} / ${row.chat_users}</td>
                <td>${row.image_chat_rounds}</td>
                <td>${row.quota_deductions}</td>
                <td>${row.app_errors} / ${row.app_warns}</td>
                <td>${row.support_messages}<div class="small muted">${row.support_users} 位用户</div></td>
                <td>${row.gift_card_redeems} 成功<div class="small muted">${row.gift_card_failures} 失败</div></td>
                <td>${row.audit_failures}<div class="small muted">${row.admin_actions} 次操作</div></td>
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
  return `
    <div class="queue-grid">
      ${queueCard("服务状态", queues.unready_dependency_count, queues.unready_dependency_count ? "模型、登录、Redis 或 OSS 有异常" : "关键服务正常", queues.unready_dependency_count ? "bad" : "ok")}
      ${queueCard("客服反馈", queues.support_needs_reply, queues.support_oldest_pending_at ? `最早待回复 ${formatTime(queues.support_oldest_pending_at)}` : "暂无待回复", queues.support_needs_reply ? "warn" : "ok")}
      ${queueCard("今日农情", dailyAgriStatusText(queues.daily_agri_status), queues.daily_agri_error || "查看最近生成状态", queues.daily_agri_status === "ready" ? "ok" : queues.daily_agri_status === "failed" ? "bad" : "warn")}
      ${queueCard("安装包下载", update.download_artifacts_complete ? "物料已齐" : "未齐", updateStatusLine(update), update.config_valid && update.download_artifacts_complete ? "ok" : "warn")}
      ${queueCard("礼品卡兑换", `${queues.gift_card_active} 张可用`, `${queues.gift_card_redeemed} 张已兑换；24h 失败 ${queues.gift_card_failed_attempts} 次`, queues.gift_card_failed_attempts ? "warn" : "ok")}
      ${queueCard("后台操作", queues.audit_failures, "最近24小时失败操作", queues.audit_failures ? "bad" : "ok")}
    </div>
  `;
}

function queueCard(title: string, value: string | number, body: string, level: "ok" | "warn" | "bad" | "info"): string {
  return `
    <div class="queue-card ${level}">
      <div class="small muted">${escapeHTML(title)}</div>
      <strong>${escapeHTML(String(value))}</strong>
      <span>${escapeHTML(body)}</span>
    </div>
  `;
}

function updateStatusLine(update: AdminMonitoring["queues"]["app_update"]): string {
  const version = update.latest_version_code ? `v${update.latest_version_code}${update.latest_version_name ? ` / ${update.latest_version_name}` : ""}` : "未配置版本";
  return `${version}；配置 ${update.config_valid ? "合法" : "异常"}；APK ${update.has_apk_url ? "已配置" : "未配置"}；SHA ${update.has_sha256 ? "已配" : "缺"}；大小 ${update.has_file_size ? "已配" : "缺"}；强制更新 ${update.force_update ? "开启" : "关闭"}`;
}

function dailyAgriStatusText(status: string): string {
  const normalized = String(status || "").toLowerCase();
  if (normalized === "ready") return "正常";
  if (normalized === "failed") return "失败";
  if (normalized === "pending") return "等待生成";
  if (normalized === "running") return "生成中";
  if (normalized === "missing") return "未生成";
  if (normalized === "disabled") return "已停用";
  return "未返回";
}

function regionMetricsTable(rows: AdminMonitoring["top_regions"]): string {
  if (!rows.length) return emptyState("没有地区数据", "最近30天问诊归档中没有可聚合地区。");
  return `
    <table class="table">
      <thead><tr><th>地区</th><th>轮次</th><th>用户</th><th>来源</th><th>最近出现</th></tr></thead>
      <tbody>
        ${rows
          .map(
            (row) => `
              <tr>
                <td>${escapeHTML(row.region)}</td>
                <td>${row.count}</td>
                <td>${row.user_count}</td>
                <td>${escapeHTML([row.source, row.reliability].filter(Boolean).join(" / ") || "未返回")}</td>
                <td>${formatTime(row.last_seen_at)}</td>
              </tr>
            `,
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function monitoringHero(report: AdminMonitoring): string {
  const worst = monitoringWorstLevel(report);
  const title = worst === "bad" ? "需要马上处理" : worst === "warn" ? "有事项要关注" : "整体正常";
  const body =
    worst === "bad"
      ? "先处理红色事项，再看 App 错误 Top 和审计失败。"
      : worst === "warn"
        ? "当前没有明确服务中断，但有运营队列需要跟进。"
        : "关键健康项、App 报错、反馈和礼品卡队列暂时没有明显异常。";
  return `
    <section class="monitor-hero ${worst}">
      <div>
        <div class="monitor-eyebrow">运营监控</div>
        <h2>${escapeHTML(title)}</h2>
        <p>${escapeHTML(body)}</p>
      </div>
      <div class="monitor-hero-meta">
        <span>${statusPill(worst === "ok" ? "正常" : worst === "warn" ? "关注" : "处理", worst)}</span>
        <span class="small muted">更新时间 ${formatTime(report.now_ms)}</span>
      </div>
    </section>
  `;
}

function monitoringDecisionGrid(report: AdminMonitoring, today: AdminMonitoring["windows"][number] | undefined, day24: AdminMonitoring["windows"][number] | undefined): string {
  const worst = monitoringWorstLevel(report);
  const primaryRoute = primaryMonitoringActionRoute(report, worst);
  const loginOK = loginHealthOK(report.health);
  const giftWarn = report.queues.gift_card_failed_attempts > 0;
  const appErrors = day24?.app_errors ?? 0;
  return `
    <section class="decision-grid">
      ${decisionCard(
        "当前结论",
        worst === "bad" ? "先处理" : worst === "warn" ? "可测但要盯" : "可以继续测",
        worst === "bad" ? "有红色事项，先点下面入口处理。" : worst === "warn" ? "没有明确中断，但有队列或配置需要看。" : `今日 ${today?.chat_rounds ?? 0} 轮问诊，关键服务正常。`,
        worst,
        primaryRoute,
      )}
      ${decisionCard(
        "登录与账号ID",
        loginOK ? "正常" : "检查登录",
        loginOK ? "严格鉴权、一键登录、短信登录和 Redis 状态正常；登录后主 ID 为账号ID。" : "登录依赖或严格鉴权异常，先打开服务健康。",
        loginOK ? "ok" : "bad",
        "health",
      )}
      ${decisionCard(
        "礼品卡与权益",
        giftWarn ? "看失败" : "可生成兑换",
        `${report.queues.gift_card_active} 张可用，${report.queues.gift_card_redeemed} 张已兑换；24h 失败 ${report.queues.gift_card_failed_attempts} 次。`,
        giftWarn ? "warn" : "ok",
        "gift-cards",
      )}
      ${decisionCard(
        "App 质量",
        appErrors ? `${appErrors} 个错误` : "暂无错误",
        appErrors ? `最近24小时 warn ${day24?.app_warns ?? 0} 条，先看 App 错误 Top。` : `最近24小时 warn ${day24?.app_warns ?? 0} 条。`,
        appErrors >= 10 ? "bad" : appErrors > 0 ? "warn" : "ok",
        "app-logs",
      )}
    </section>
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
      ${shortcutButton("gift-cards", "生成礼品卡")}
      ${shortcutButton("app-logs", "看 App 错误")}
      ${shortcutButton("support", "处理反馈")}
      ${shortcutButton("today-agri", "看今日农情")}
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
  if (items.some((item) => normalizeLevel(item.level) === "warn")) return "warn";
  return "ok";
}

function primaryMonitoringActionRoute(report: AdminMonitoring, level: "ok" | "warn" | "bad"): RouteKey | undefined {
  if (level === "ok") return undefined;
  const item = (report.action_items || []).find((entry) => normalizeLevel(entry.level) === level && isKnownRoute(entry.route));
  return item?.route;
}

function loginHealthOK(health: AdminOverview["health"]): boolean {
  return health.auth_strict === true &&
    String(health.dypns).toLowerCase() === "ok" &&
    String(health.dypns_fusion).toLowerCase() === "ok" &&
    String(health.dypns_sms).toLowerCase() === "ok" &&
    String(health.redis).toLowerCase() === "ok";
}

function actionItemList(items: AdminMonitoring["action_items"]): string {
  if (!items.length) return emptyState("没有待处理事项", "后端未返回 action_items。");
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
  if (!rows.length) return emptyState("没有能力状态", "后端未返回 capabilities。");
  return `
    <div class="capability-grid">
      ${rows
        .map(
          (row) => `
            <article class="capability-card ${normalizeCapabilityStatus(row.status)}">
              <div class="capability-head">
                <strong>${escapeHTML(row.title)}</strong>
                ${statusPill(capabilityStatusText(row.status), capabilityLevel(row.status))}
              </div>
              <p>${escapeHTML(row.body)}</p>
              ${routeActionButton(row.route, "打开")}
            </article>
          `,
        )
        .join("")}
    </div>
  `;
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

function defaultRoute(): RouteKey {
  return visibleRoutes()[0]?.key || "overview";
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
  if (!rows.length) return emptyState("没有最近问诊", "后端未返回最近问诊摘录。");
  return rows
    .slice(0, 4)
    .map(
      (row) => `
        <article class="message">
          <div class="message-head"><strong>${row.has_images ? "图文问诊" : "文字问诊"}</strong><span>${formatTime(row.created_at)}</span></div>
          <div class="small muted">${escapeHTML(row.region || "未知地区")} ${row.image_count ? ` / ${row.image_count} 图` : ""}</div>
          <div style="margin-top:6px">${escapeHTML(row.user_excerpt || "")}</div>
          <div class="muted" style="margin-top:6px">${escapeHTML(row.assistant_excerpt || "")}</div>
        </article>
      `,
    )
    .join("");
}

function compactLogs(rows: ClientAppLogEntry[]): string {
  if (!rows.length) return emptyState("没有最近日志", "后端未返回该用户最近 App 日志。");
  return rows
    .slice(0, 4)
    .map(
      (row) => `
        <div class="message">
          <div class="message-head"><strong>${escapeHTML(row.event)}</strong><span>${formatTime(row.created_at)}</span></div>
          <div>${statusPill(row.level)} <span class="muted">${escapeHTML(row.message || "")}</span></div>
        </div>
      `,
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
  return `<div class="notice bad"><strong>请求失败</strong><div style="margin-top:6px">${escapeHTML(errorMessage(error))}</div></div>`;
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return `${error.status} ${error.code}`;
  if (error instanceof Error) return error.message;
  return "unknown_error";
}

function labelFor(key: string): string {
  const labels: Record<string, string> = {
    api: "API",
    bailian: "百炼模型",
    dypns: "DYPNS",
    dypns_fusion: "一键登录",
    dypns_sms: "短信登录",
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
