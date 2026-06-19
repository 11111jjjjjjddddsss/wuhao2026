#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const repo = process.cwd();
const read = (relative) => fs.readFileSync(path.join(repo, relative), "utf8");

const adminMain = read("admin/src/main.ts");
const adminTypes = read("admin/src/types.ts");
const serverRoutes = read("server-go/internal/app/server.go");
const adminAPI = read("server-go/internal/app/admin_api.go");
const giftCards = read("server-go/internal/app/gift_cards.go");
const supportGo = read("server-go/internal/app/support.go");
const uploadGo = read("server-go/internal/app/upload.go");
const serverSurface = `${adminAPI}\n${giftCards}\n${supportGo}\n${uploadGo}`;

const fail = [];

function unique(values) {
  return [...new Set(values)].sort();
}

function stringsFrom(regex, text) {
  return [...text.matchAll(regex)].map((match) => match[1]);
}

function findBlock(text, startPattern) {
  const start = text.search(startPattern);
  if (start < 0) return "";
  let brace = text.indexOf("{", start);
  if (brace < 0) return "";
  let depth = 0;
  for (let i = brace; i < text.length; i += 1) {
    const ch = text[i];
    if (ch === "{") depth += 1;
    if (ch === "}") {
      depth -= 1;
      if (depth === 0) return text.slice(brace + 1, i);
    }
  }
  return "";
}

function normalizeAdminPath(raw) {
  const queryIndex = raw.indexOf("?");
  return queryIndex >= 0 ? raw.slice(0, queryIndex) : raw;
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function expectAdminPattern(name, pattern) {
  if (!pattern.test(adminMain)) {
    fail.push(`${name}: missing expected admin UI contract`);
  }
}

function rejectAdminPattern(name, pattern) {
  if (pattern.test(adminMain)) {
    fail.push(`${name}: forbidden admin UI contract present`);
  }
}

function expectServerPattern(name, pattern) {
  if (!pattern.test(serverSurface)) {
    fail.push(`${name}: missing expected admin API contract`);
  }
}

function rejectServerPattern(name, pattern) {
  if (pattern.test(serverSurface)) {
    fail.push(`${name}: forbidden admin API contract present`);
  }
}

function expectAppLogEventFilter(event) {
  expectAdminPattern(
    `app log event filter ${event}`,
    new RegExp(`filterButton\\("[^"]+",\\s*\\{\\s*event:\\s*"${escapeRegex(event)}"`),
  );
}

function expectAppLogPrefixFilter(prefix) {
  expectAdminPattern(
    `app log prefix filter ${prefix}`,
    new RegExp(`filterButton\\("[^"]+",\\s*\\{\\s*eventPrefix:\\s*"${escapeRegex(prefix)}"`),
  );
}

const typeBlock = adminTypes.match(/export type AdminRouteKey =([\s\S]*?);/)?.[1] || "";
const typedRoutes = unique(stringsFrom(/"([^"]+)"/g, typeBlock));

const routesBlock = adminMain.match(/const routes: RouteItem\[\] = \[([\s\S]*?)\];/)?.[1] || "";
const navRoutes = unique(stringsFrom(/key:\s*"([^"]+)"/g, routesBlock));

const routeContentBlock = findBlock(adminMain, /async function routeContent/);
const switchRoutes = unique(stringsFrom(/case\s+"([^"]+)"/g, routeContentBlock));
const compactLogsBlock = findBlock(adminMain, /function compactLogs/);

const knownRouteBlock = findBlock(adminAPI, /func adminRouteAllowed/);
const backendRouteCases = unique(
  stringsFrom(/case\s+([^:]+):/g, knownRouteBlock)
    .flatMap((raw) => stringsFrom(/"([^"]*)"/g, raw))
    .filter((route) => route !== ""),
);

const adminPathPattern = /(\/admin-api\/v1\/[A-Za-z0-9/_-]+(?:\?[A-Za-z0-9_=&.-]+)?)/g;
const frontendAPIPaths = unique(
  stringsFrom(adminPathPattern, adminMain)
    .concat(stringsFrom(adminPathPattern, read("admin/src/api.ts")))
    .map(normalizeAdminPath),
);
const registeredAPIPaths = unique(
  stringsFrom(/HandleFunc\("(?:GET|POST|PUT|PATCH|DELETE)\s+(\/admin-api\/v1\/[A-Za-z0-9/_-]+)"/g, serverRoutes),
);

const actionMarkup = unique(stringsFrom(/data-action="([^"]+)"/g, adminMain));
const actionHandlers = unique(stringsFrom(/if\s*\(\s*action\s*===\s*"([^"]+)"/g, adminMain));

const routeStringCandidates = unique([
  ...stringsFrom(/routeActionButton\("([^"]+)"/g, adminMain),
  ...stringsFrom(/shortcutButton\("([^"]+)"/g, adminMain),
  ...stringsFrom(/route:\s*"([^"]+)"/g, adminMain),
  ...stringsFrom(/activeRoute\s*!==\s*"([^"]+)"/g, adminMain),
  ...stringsFrom(/activeRoute\s*===\s*"([^"]+)"/g, adminMain),
]);
const unknownRouteStrings = routeStringCandidates.filter((route) => !typedRoutes.includes(route));

function expectSame(name, left, right) {
  const missing = left.filter((item) => !right.includes(item));
  const extra = right.filter((item) => !left.includes(item));
  if (missing.length || extra.length) {
    fail.push(`${name}: missing=[${missing.join(", ")}] extra=[${extra.join(", ")}]`);
  }
}

expectSame("types vs nav routes", typedRoutes, navRoutes);
expectSame("nav routes vs routeContent cases", navRoutes, switchRoutes);
expectSame("typed routes vs backend adminRouteAllowed cases", typedRoutes, backendRouteCases);

const missingActionHandlers = actionMarkup.filter((action) => !actionHandlers.includes(action));
const deadActionHandlers = actionHandlers.filter((action) => !actionMarkup.includes(action));
if (missingActionHandlers.length || deadActionHandlers.length) {
  fail.push(`actions: missingHandlers=[${missingActionHandlers.join(", ")}] handlersWithoutMarkup=[${deadActionHandlers.join(", ")}]`);
}

const unregisteredAPI = frontendAPIPaths.filter((apiPath) => !registeredAPIPaths.includes(apiPath));
if (unregisteredAPI.length) {
  fail.push(`frontend admin API paths not registered by server.go: [${unregisteredAPI.join(", ")}]`);
}

if (unknownRouteStrings.length) {
  fail.push(`frontend route string literals not in AdminRouteKey: [${unknownRouteStrings.join(", ")}]`);
}

expectAppLogPrefixFilter("auth.");
expectAppLogPrefixFilter("auth.fusion_");
expectAdminPattern("preauth app log filter", /filterButton\("[^"]+",\s*\{\s*userID:\s*"preauth"/);
if (!/redactSensitiveDisplayText\(row\.message \|\| ""\)/.test(compactLogsBlock)) {
  fail.push("user detail compact app logs: message must be redacted before rendering");
}
if (/\battrs\b|\bmasked_ip\b|device_model|os_version/.test(compactLogsBlock)) {
  fail.push("user detail compact app logs: must not render attrs, masked_ip, or device details");
}
expectAdminPattern("readiness separates program attention", /程序需处理/);
expectAdminPattern("monitoring shows program action strip", /程序需处理项/);
expectAdminPattern("monitoring program strip excludes manual and launch-only items", /filter\(\(row\)\s*=>\s*!row\.manual\s*&&\s*!row\.launch_only\s*&&\s*row\.status\s*!==\s*"ready"\)/);
expectAdminPattern("monitoring program strip explains actionable work", /代码、配置、部署或后台操作/);
expectAdminPattern("launch readiness cards show item owner type", /class="launch-kind \$\{row\.manual \? "manual" : "program"\}"/);
expectAdminPattern("launch readiness cards distinguish manual items", /row\.manual \? "人工确认" : "程序处理"/);
expectAdminPattern("launch readiness renders confirmation hints", /确认方式：/);
expectAdminPattern("launch readiness confirmation hints come from backend", /row\.confirm_hint/);
expectAdminPattern("readiness separates manual confirmation", /人工确认/);
expectAdminPattern("readiness keeps launch blockers visible", /上架阻塞/);
expectServerPattern("launch readiness includes cost confirmation", /费用 \/ 套餐成本/);
expectServerPattern("launch readiness cost item points to cost script", /check-aliyun-costs\.ps1/);
expectAdminPattern("support handled action is not phrased as already replied", /已处理\/无需回复/);
expectAdminPattern("support handled and closed actions require note", /\(status === "replied" \|\| status === "closed"\) && !note/);
rejectAdminPattern("support action must not say 标已回复", /标已回复/);
expectServerPattern("support handled status has note-required API guard", /support_status_note_required/);
expectServerPattern("readonly support message details are redacted by role", /adminCanViewSupportMessageBody/);
expectAdminPattern("readonly support messages explain body redaction", /完整正文只对客服处理角色开放/);
expectAdminPattern("readonly support images explain image redaction", /原图只对客服处理角色开放/);
rejectAdminPattern("admin auth storage must not persist CSRF token", /localStorage\.setItem\(AUTH_STORAGE_KEY,\s*JSON\.stringify\(payload\)\)/);
rejectAdminPattern("admin csrf header must not read token from stored auth", /getStoredAuth\(\)\?\.csrf_token/);
expectAdminPattern("gift card generation emphasizes real entitlement", /生成后将产生真实可兑换权益/);
expectAdminPattern("gift card generation builds quantity tier days confirmation", /confirmationText\s*=\s*`\$\{quantity\} \$\{tierLabel\} \$\{durationDays\}`/);
expectAdminPattern("gift card generation requires typed quantity tier days confirmation", /请输入 \$\{confirmationText\} 确认生成真实礼品卡/);
expectAdminPattern("gift card generation sends typed confirmation to API", /confirmation:\s*typedConfirmation\.trim\(\)/);
expectServerPattern("gift card create API enforces typed confirmation", /adminGiftCardBatchConfirmationError\(body,\s*input\.Quantity,\s*input\.Tier,\s*input\.DurationDays\)/);
expectServerPattern("gift card void API enforces typed confirmation", /adminGiftCardVoidConfirmationError/);
expectServerPattern("gift card create validation failures are audited", /recordAdminGiftCardBatchValidationFailure/);
expectServerPattern("gift card void validation failures are audited", /recordAdminGiftCardVoidValidationFailure/);
expectAdminPattern("gift card page states immediate redemption policy", /生成后就是 active 可兑换卡/);
expectAdminPattern("gift card redeemed KPI avoids activation wording", /权益已发放/);
expectAdminPattern("gift card table uses redeem account label", /兑换账号ID/);
expectAdminPattern("gift card note placeholder avoids sensitive identifiers", /用途备注，不写手机号、完整卡码或密钥/);
expectAdminPattern("gift card status active is localized", /active:\s*\{\s*label:\s*"未兑换"/);
expectAdminPattern("gift card attempt status is localized", /statusPill\("成功",\s*"ok"\)/);
expectAdminPattern("gift card create button makes production effect clear", /生成真实可兑换卡/);
expectAdminPattern("gift card monitoring shortcut is trace entry", /礼品卡追溯/);
expectAdminPattern("gift card void requires typed keyword", /请输入 作废 确认作废这张礼品卡/);
rejectAdminPattern("gift card page must not imply future activation", /已经生效且未过期/);
rejectServerPattern("admin gift card monitoring must not gate on future valid_from", /valid_from\s*<=/);
rejectServerPattern("admin gift card monitoring text must not imply activation gate", /生效且未过期/);
expectAdminPattern("orders page marks amount as development record", /开发期记录金额/);
rejectAdminPattern("orders page must not imply actual income", /金额合计/);
expectAdminPattern("app update separates switch from delivery", /是否会下发/);
expectAdminPattern("app update labels switch as switch", /发布开关/);
expectAdminPattern("app update enable requires typed versionCode and release command", /请输入 \$\{latestVersionCode\} 确认已获正式发版口令，并对外启用这次更新/);
expectAdminPattern("app update disable requires typed confirmation", /请输入 停更 确认关闭当前更新/);
expectAdminPattern("app update enable confirm shows sha256", /SHA-256: \$\{apkSHA256\}/);
expectAdminPattern("app update enable confirm shows file size", /文件大小: \$\{fileSizeBytes\} bytes/);
expectAdminPattern("app update enable confirm points to release-match script", /check-app-update-release-match\.ps1/);
expectAdminPattern("app update rejects internal test apk url", /内部测试包链接，不能配置到正式检查更新/);
expectAdminPattern("app update rejects escaped internal test apk url", /decodeURIComponent\(value\)/);
expectAdminPattern("app update requires official download domain", /download\.nongjiqiancha\.cn 下的稳定正式 \.apk 链接/);
expectAdminPattern("app update official url checks release path", /\/android\/releases\//);
expectAdminPattern("app update official url checks download host", /download\.nongjiqiancha\.cn/);
expectAdminPattern("app update rejects short-lived signed apk url", /短期签名参数的临时链接，不能配置到正式检查更新/);
expectServerPattern("app update API rejects internal test apk url", /isOfficialAndroidAPKURL/);
expectAdminPattern("support reply button says production user send", /发送给用户（生产）/);
expectAdminPattern("support long history has truncation notice", /仅显示最近消息/);
expectServerPattern("admin support messages use admin list limit plus search matches", /ListSupportMessagesWithSearchMatches\(ctx,\s*userID,\s*adminSupportMessageListLimit/);
expectServerPattern("admin support message search matches stay role-gated", /if adminCanSearchSupportBody\(admin\.User\.Role\)/);
expectServerPattern("admin support message limit is explicit", /adminSupportMessageListLimit\s*=\s*200/);
expectServerPattern("admin support images are normalized to same-origin paths", /adminSupportImageURLs/);
expectServerPattern("support uploads are not publicly cached", /Cache-Control",\s*"private,\s*no-store"/);
rejectAdminPattern("admin empty states should not say backend did not return", /后端未返回/);
expectAdminPattern("launch readiness tracks launch-only attention", /launchOnlyAttention/);
expectAdminPattern("program strip excludes launch-only items", /!row\.manual && !row\.launch_only && row\.status !== "ready"/);
expectServerPattern("launch readiness exposes launch_only flag", /LaunchOnly\s+bool\s+`json:"launch_only,omitempty"`/);
[
  "auth.login_network_failed",
  "auth.sms_send_failed",
  "auth.sms_login_failed",
  "auth.sms_login_success",
  "auth.app_crash",
  "app.crash",
].forEach(expectAppLogEventFilter);

expectAppLogPrefixFilter("app_update.");
[
  "app_update.check_started",
  "app_update.available",
  "app_update.no_update",
  "app_update.check_failed",
  "app_update.install_permission_required",
  "app_update.download_started",
  "app_update.download_failed",
  "app_update.install_intent_failed",
  "app_update.install_started",
  "app_update.install_completed",
  "app_update.install_not_completed",
].forEach(expectAppLogEventFilter);

if (fail.length) {
  console.error("[admin-surface] failed");
  for (const item of fail) console.error(`- ${item}`);
  process.exit(1);
}

console.log("[admin-surface] ok");
console.log(`routes=${typedRoutes.length} actions=${actionMarkup.length} api_paths=${frontendAPIPaths.length}`);
