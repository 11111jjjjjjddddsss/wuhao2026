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

if (fail.length) {
  console.error("[admin-surface] failed");
  for (const item of fail) console.error(`- ${item}`);
  process.exit(1);
}

console.log("[admin-surface] ok");
console.log(`routes=${typedRoutes.length} actions=${actionMarkup.length} api_paths=${frontendAPIPaths.length}`);
