#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const repo = process.cwd();
const read = (relative) => fs.readFileSync(path.join(repo, relative), "utf8");

const adminMain = read("admin/src/main.ts");
const adminTypes = read("admin/src/types.ts");
const serverRoutes = read("server-go/internal/app/server.go");
const adminAPI = read("server-go/internal/app/admin_api.go");

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
  if (!pattern.test(adminAPI)) {
    fail.push(`${name}: missing expected admin API contract`);
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
expectAdminPattern("readiness separates program attention", /程序需处理/);
expectAdminPattern("monitoring shows program action strip", /程序需处理项/);
expectAdminPattern("monitoring program strip excludes manual items", /filter\(\(row\)\s*=>\s*!row\.manual\s*&&\s*row\.status\s*!==\s*"ready"\)/);
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
expectAdminPattern("support handled action requires note", /status === "replied" && !note/);
rejectAdminPattern("support action must not say 标已回复", /标已回复/);
expectServerPattern("support handled status has note-required API guard", /support_status_note_required/);
expectAdminPattern("gift card generation emphasizes real entitlement", /这里不是假测试/);
expectAdminPattern("gift card generation requires typed quantity confirmation", /请输入 \$\{quantity\} 确认生成真实礼品卡/);
expectAdminPattern("app update enable confirm shows sha256", /SHA-256: \$\{apkSHA256\}/);
expectAdminPattern("app update enable confirm shows file size", /文件大小: \$\{fileSizeBytes\} bytes/);
expectAdminPattern("app update enable confirm points to release-match script", /check-app-update-release-match\.ps1/);
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
].forEach(expectAppLogEventFilter);

if (fail.length) {
  console.error("[admin-surface] failed");
  for (const item of fail) console.error(`- ${item}`);
  process.exit(1);
}

console.log("[admin-surface] ok");
console.log(`routes=${typedRoutes.length} actions=${actionMarkup.length} api_paths=${frontendAPIPaths.length}`);
