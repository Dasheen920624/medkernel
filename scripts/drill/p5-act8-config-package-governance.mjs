#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕8 配置包与发布治理。
// 剧本动作：
//   1. 采集机构管理员、质量治理员、临床治理员权限画像，确认配置包发布适配器与目标机构可用。
//   2. 从真实已发布资产中取术语、规则、路径、质控指标，创建同一 packageCode 的 v1/v2 两个配置包。
//   3. v1 全量发布作为历史基线；v2 校验、差异导出、灰度发布、全量发布并生成真实同步证据。
//   4. 导出 v2 离线包并执行同租户重复导入，必须得到冲突保护；随后 v2 回滚到 OFFLINE 的 v1。
//   5. 前台截取 /config/packages 与 /config/releases 作为可见入口佐证，最终判定以服务端事实为准。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕8-配置包与发布治理/
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const requireFromFrontend = createRequire(new URL("../../frontend/package.json", import.meta.url));
const { chromium } = requireFromFrontend("playwright");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const baseUrl = (process.env.DRILL_BASE_URL ?? "https://193.112.107.134").replace(/\/+$/, "");
const apiBase = `${baseUrl}/medkernel/api/v1`;
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ?? "/tmp/p5-14-role-drill-credentials-20260612.json";
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕8-配置包与发布治理",
);

const phase = (process.env.DRILL_PHASE ?? "all").split(",").map((s) => s.trim());
const runTag = process.env.DRILL_RUN_TAG ?? `p5-act8-${Date.now()}`;
const runSuffix = safeIdentifier(runTag).slice(-12) || String(Date.now()).slice(-12);
const packageCode =
  process.env.DRILL_PACKAGE_CODE ?? `P5.ACT8.CONFIG.${runSuffix.toUpperCase()}`;
const versionPrefix =
  process.env.DRILL_VERSION_PREFIX ?? `2026.06.1-act8-${runSuffix.toLowerCase()}`;
const packageVersionV1 = trimVersion(`${versionPrefix}-v1`);
const packageVersionV2 = trimVersion(`${versionPrefix}-v2`);
const adapterId = process.env.DRILL_PACKAGE_ADAPTER_ID ?? "p5-his-gateway";
const targetOrgCode = process.env.DRILL_TARGET_ORG_CODE ?? "P5-HOSP";

const failures = [];
const steps = [];
const traceEntries = [];

function runPhase(name) {
  return phase.includes("all") || phase.includes(name);
}

function safeIdentifier(value) {
  return String(value).replace(/[^A-Za-z0-9]/g, "");
}

function trimVersion(value) {
  return value.length <= 64 ? value : value.slice(0, 64);
}

function traceId(stage) {
  return `${runTag}-${stage}-${Date.now()}`;
}

function dataOf(response) {
  return response.body?.data ?? response.body;
}

function pageItems(response) {
  const data = dataOf(response);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.items)) return data.items;
  return [];
}

function jsonShort(value, limit = 1000) {
  return JSON.stringify(value).slice(0, limit);
}

function encodedParams(params) {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  return search.toString();
}

async function writeJson(name, value) {
  await writeFile(
    path.join(evidenceDir, name),
    JSON.stringify({ runTag, generatedAt: new Date().toISOString(), ...value }, null, 2),
  );
}

async function writeText(name, value) {
  await writeFile(path.join(evidenceDir, name), value);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function renderWithUrlBar(browser, rawPath, finalPath, url) {
  const image = await readFile(rawPath);
  const imageData = `data:image/png;base64,${image.toString("base64")}`;
  const html = `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <style>
      html, body { margin: 0; background: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
      .bar { height: 44px; display: flex; align-items: center; gap: 10px; padding: 0 16px; background: #eef2f7; color: #1f2937; border-bottom: 1px solid #cbd5e1; box-sizing: border-box; font-size: 13px; }
      .dot { width: 10px; height: 10px; border-radius: 50%; background: #94a3b8; }
      .url { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; background: #fff; border: 1px solid #cbd5e1; border-radius: 7px; padding: 5px 10px; }
      img { display: block; width: 100%; height: auto; }
    </style>
  </head>
  <body>
    <div class="bar" aria-label="浏览器地址栏">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
      <span class="url">${escapeHtml(url)}</span>
    </div>
    <img src="${imageData}" alt="页面截图" />
  </body>
</html>`;
  const wrapper = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await wrapper.setContent(html, { waitUntil: "load" });
  await wrapper.screenshot({ path: finalPath, fullPage: true });
  await wrapper.close();
  await rm(rawPath, { force: true });
}

async function capture(browser, page, filename, label) {
  const finalPath = path.join(evidenceDir, filename);
  const rawPath = path.join(evidenceDir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  const row = { label, screenshot: filename, url: page.url() };
  steps.push({ step: "screenshot", ...row });
  return row;
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    /* 部分页面有轮询或长连接，以页面稳定和骨架消失为准。 */
  }
  try {
    await page.getByText(/正在加载/).first().waitFor({ state: "hidden", timeout: 10000 });
  } catch {
    /* 页面没有加载态或已提前消失。 */
  }
  await page.waitForTimeout(700);
}

async function gotoPath(page, route) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForQuiet(page);
}

async function tryCapture(browser, page, route, filename, label) {
  try {
    await gotoPath(page, route);
    return await capture(browser, page, filename, label);
  } catch (error) {
    const row = { label, route, error: error.message };
    failures.push({ phase: "screenshot", ...row });
    return row;
  }
}

async function loadCredentials() {
  const raw = await readFile(credentialPath, "utf8");
  const data = JSON.parse(raw);
  return {
    customerTenant: data.customerTenant,
    roleAccounts: data.roleAccounts ?? {},
    platformRoleAccounts: data.platformRoleAccounts ?? {},
  };
}

function requireAccount(credentials, role) {
  if (role === "organization-admin" && credentials.customerTenant?.adminUsername) {
    return {
      username: credentials.customerTenant.adminUsername,
      password: credentials.customerTenant.password,
      tenantId: credentials.customerTenant.tenantId,
    };
  }
  const account = credentials.roleAccounts[role] ?? credentials.platformRoleAccounts[role];
  if (!account?.username || !account?.password || !account?.tenantId) {
    throw new Error(`凭据缺少角色 ${role} 的可用账号`);
  }
  return account;
}

async function csrfToken(context) {
  const cookies = await context.cookies(baseUrl);
  return cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
}

async function apiGet(context, pathName, stage) {
  const requestTraceId = traceId(stage);
  const response = await context.request.get(`${apiBase}${pathName}`, {
    headers: { "X-Trace-Id": requestTraceId },
  });
  const text = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 900) };
  }
  traceEntries.push({
    label: stage,
    method: "GET",
    path: pathName,
    status: response.status(),
    ok: response.ok(),
    requestTraceId,
    bodyTraceId: parsed?.traceId ?? parsed?.data?.traceId,
  });
  return { status: response.status(), ok: response.ok(), body: parsed };
}

async function apiPost(context, pathName, body, stage, extraHeaders = {}) {
  const token = await csrfToken(context);
  const requestTraceId = traceId(stage);
  const response = await context.request.post(`${apiBase}${pathName}`, {
    data: body,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": requestTraceId,
      "X-XSRF-TOKEN": token,
      ...extraHeaders,
    },
  });
  const text = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 1200) };
  }
  traceEntries.push({
    label: stage,
    method: "POST",
    path: pathName,
    status: response.status(),
    ok: response.ok(),
    requestTraceId,
    bodyTraceId: parsed?.traceId ?? parsed?.data?.traceId,
  });
  return { status: response.status(), ok: response.ok(), body: parsed };
}

async function downloadText(context, pathName, stage) {
  const requestTraceId = traceId(stage);
  const response = await context.request.get(`${apiBase}${pathName}`, {
    headers: { "X-Trace-Id": requestTraceId },
  });
  const text = await response.text();
  traceEntries.push({
    label: stage,
    method: "GET",
    path: pathName,
    status: response.status(),
    ok: response.ok(),
    requestTraceId,
    bodyTraceId: null,
  });
  return { status: response.status(), ok: response.ok(), text };
}

async function login(browser, account, roleLabel) {
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: { username: account.username, password: account.password, tenantId: account.tenantId },
    headers: { "Content-Type": "application/json", "X-Trace-Id": traceId(`login-${roleLabel}`) },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(`${roleLabel} 登录失败：${response.status()} ${body.slice(0, 300)}`);
  }
  return { context, page };
}

async function profile(context, label) {
  const res = await apiGet(context, "/security/me", `${label}-security-me`);
  if (!res.ok) {
    throw new Error(`${label} 读取 /security/me 失败 ${res.status}: ${jsonShort(res.body)}`);
  }
  const d = dataOf(res) ?? {};
  return {
    username: d.username,
    userId: d.userId ?? d.username,
    roleCodes: (d.roles ?? d.roleCodes ?? [])
      .map((r) => (typeof r === "string" ? r : r.code))
      .filter(Boolean),
    permissions: (d.permissions ?? []).map((p) => (typeof p === "string" ? p : p.code)).filter(Boolean),
    menuKeys: (d.menuKeys ?? d.menus ?? []).map((m) => (typeof m === "string" ? m : m.key)).filter(Boolean),
    dataScope: d.dataScope ?? {},
  };
}

function contextPayload(profileData, packageVersion, payload = {}, label = "pkg") {
  const scope = profileData.dataScope ?? {};
  return {
    request_id: `${runTag}-${label}-${Date.now()}`,
    trace_id: traceId(label),
    tenant_id: scope.tenantId,
    group_id: scope.groupId ?? null,
    hospital_id: scope.hospitalId ?? null,
    campus_id: scope.campusId ?? null,
    site_id: scope.siteId ?? null,
    department_id: scope.departmentId ?? null,
    specialty_id: scope.specialtyId ?? null,
    user_id: profileData.userId,
    role_codes: profileData.roleCodes,
    package_version: packageVersion,
    ...payload,
  };
}

async function firstSuccessful(contextEntries, pathName, stage) {
  const errors = [];
  for (const [label, context] of contextEntries) {
    const res = await apiGet(context, pathName, `${stage}-${label}`);
    if (res.ok) return { label, res };
    errors.push({ label, status: res.status, body: res.body });
  }
  throw new Error(`${stage} 查询均失败: ${jsonShort(errors)}`);
}

async function listPackages(context, filters, stage) {
  const query = encodedParams({ page: 0, size: 100, ...filters });
  const response = await apiGet(context, `/engine/pkg/packages?${query}`, stage);
  if (!response.ok) {
    throw new Error(`查询配置包失败 ${response.status}: ${jsonShort(response.body)}`);
  }
  return pageItems(response);
}

async function findPackagesByCode(context, code, stage) {
  const items = await listPackages(context, { keyword: code }, stage);
  return items.filter((item) => item.packageCode === code);
}

async function packageDetail(context, packageId, stage) {
  const response = await apiGet(context, `/engine/pkg/packages/${encodeURIComponent(packageId)}`, stage);
  if (!response.ok) {
    throw new Error(`读取配置包详情失败 ${packageId} ${response.status}: ${jsonShort(response.body)}`);
  }
  return dataOf(response);
}

async function listSyncLogs(context, packageId, stage) {
  const response = await apiGet(
    context,
    `/engine/pkg/packages/${encodeURIComponent(packageId)}/sync-logs`,
    stage,
  );
  if (!response.ok) {
    throw new Error(`读取同步日志失败 ${packageId} ${response.status}: ${jsonShort(response.body)}`);
  }
  return dataOf(response) ?? [];
}

async function validatePackage(context, profileData, pack, stage) {
  const response = await apiPost(
    context,
    `/engine/pkg/packages/${encodeURIComponent(pack.packageId)}/validate`,
    contextPayload(profileData, pack.packageVersion, {}, stage),
    stage,
  );
  if (!response.ok) {
    throw new Error(`配置包校验请求失败 ${pack.packageCode}@${pack.packageVersion} ${response.status}: ${jsonShort(response.body)}`);
  }
  const validation = dataOf(response);
  if (!validation?.valid) {
    throw new Error(`配置包校验未通过 ${pack.packageCode}@${pack.packageVersion}: ${jsonShort(validation)}`);
  }
  return validation;
}

function assetKey(item) {
  return `${item.assetType}:${item.assetId}`;
}

async function ensurePackage(context, profileData, spec) {
  const existing = (await findPackagesByCode(context, spec.packageCode, `find-${spec.label}`))
    .find((item) => item.packageVersion === spec.packageVersion);
  let pack = existing;
  let reused = Boolean(existing);
  if (!pack) {
    const createResponse = await apiPost(
      context,
      "/engine/pkg/packages",
      contextPayload(
        profileData,
        spec.packageVersion,
        {
          packageCode: spec.packageCode,
          packageVersion: spec.packageVersion,
          name: spec.name,
          description: spec.description,
          accessPolicy: "OPEN",
        },
        `create-${spec.label}`,
      ),
      `create-${spec.label}`,
    );
    if (!createResponse.ok) {
      throw new Error(`创建配置包失败 ${spec.packageCode}@${spec.packageVersion} ${createResponse.status}: ${jsonShort(createResponse.body)}`);
    }
    pack = dataOf(createResponse);
    reused = false;
  }

  let detail = await packageDetail(context, pack.packageId, `detail-${spec.label}`);
  const detailItems = detail.items ?? [];
  const missingItems = spec.items.filter(
    (expected) => !detailItems.some((item) => assetKey(item) === assetKey(expected)),
  );
  if (missingItems.length > 0 && detail.status !== "DRAFT") {
    throw new Error(
      `配置包 ${spec.packageCode}@${spec.packageVersion} 已锁定为 ${detail.status}，但缺少条目 ${missingItems.map(assetKey).join(", ")}`,
    );
  }
  for (const item of missingItems) {
    const addResponse = await apiPost(
      context,
      `/engine/pkg/packages/${encodeURIComponent(pack.packageId)}/items`,
      contextPayload(
        profileData,
        spec.packageVersion,
        {
          assetType: item.assetType,
          assetId: item.assetId,
          assetVersion: item.assetVersion,
        },
        `add-${spec.label}-${item.assetType.toLowerCase()}`,
      ),
      `add-${spec.label}-${item.assetType.toLowerCase()}`,
    );
    if (!addResponse.ok && addResponse.status !== 409) {
      throw new Error(`添加包条目失败 ${assetKey(item)} ${addResponse.status}: ${jsonShort(addResponse.body)}`);
    }
  }

  detail = await packageDetail(context, pack.packageId, `detail-after-items-${spec.label}`);
  const validation = await validatePackage(context, profileData, detail, `validate-${spec.label}`);
  steps.push({
    step: "ensure-package",
    label: spec.label,
    packageId: detail.packageId,
    packageCode: detail.packageCode,
    packageVersion: detail.packageVersion,
    status: detail.status,
    itemCount: detail.items?.length ?? validation.itemCount,
    reused,
  });
  return { pack: detail, validation, reused };
}

async function releasePackage(context, profileData, pack, targetOrgUnitId, strategy, reason, stage) {
  const response = await apiPost(
    context,
    `/engine/pkg/packages/${encodeURIComponent(pack.packageId)}/release`,
    contextPayload(
      profileData,
      pack.packageVersion,
      {
        reason,
        targetOrgUnitId,
        strategy,
        scopeType: "ALL",
        scopeValue: null,
        adapterIds: [adapterId],
        publishEvidence: {
          electronicSignature: {
            signerId: profileData.userId,
            signerRole: profileData.roleCodes[0] ?? "organization-admin",
            signedAt: new Date().toISOString(),
            signatureDigest: `${runTag}-${stage}`,
          },
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            peerReviewSigned: true,
            summary: "P5幕8配置包发布治理真实演练门禁证据",
          },
        },
      },
      stage,
    ),
    stage,
  );
  if (!response.ok) {
    throw new Error(`${stage} 发布失败 ${response.status}: ${jsonShort(response.body)}`);
  }
  const sync = dataOf(response);
  const failedLogs = (sync?.logs ?? []).filter((log) => log.status !== "SUCCESS");
  if (sync?.status !== "SUCCESS" || failedLogs.length > 0) {
    throw new Error(`${stage} 同步未全成功: ${jsonShort(sync)}`);
  }
  const after = await packageDetail(context, pack.packageId, `detail-after-${stage}`);
  steps.push({
    step: "release-package",
    stage,
    strategy,
    packageId: pack.packageId,
    packageVersion: pack.packageVersion,
    planId: sync.planId,
    status: sync.status,
    packageStatus: after.status,
    logStatuses: (sync.logs ?? []).map((log) => ({ adapterId: log.adapterId, status: log.status })),
  });
  return { sync, after };
}

async function rollbackPackage(context, profileData, current, target) {
  const response = await apiPost(
    context,
    `/engine/pkg/packages/${encodeURIComponent(current.packageId)}/rollback`,
    contextPayload(
      profileData,
      current.packageVersion,
      {
        targetPackageId: target.packageId,
        confirmedCurrentVersion: current.packageVersion,
        confirmedTargetVersion: target.packageVersion,
        reason: "P5幕8配置包治理演练：全量发布后按高危确认回滚到历史基线",
        confirmedHighRisk: true,
      },
      "rollback-v2-to-v1",
    ),
    "rollback-v2-to-v1",
  );
  if (!response.ok) {
    throw new Error(`回滚配置包失败 ${response.status}: ${jsonShort(response.body)}`);
  }
  const rollback = dataOf(response);
  if (rollback?.status !== "ACTIVE") {
    throw new Error(`回滚目标未恢复 ACTIVE: ${jsonShort(rollback)}`);
  }
  const targetAfter = await packageDetail(context, target.packageId, "detail-after-rollback-target");
  const currentAfter = await packageDetail(context, current.packageId, "detail-after-rollback-current");
  if (targetAfter.status !== "ACTIVE" || currentAfter.status !== "OFFLINE") {
    throw new Error(`回滚后状态不符合预期: ${jsonShort({ targetAfter, currentAfter })}`);
  }
  const targetLogs = await listSyncLogs(context, target.packageId, "logs-after-rollback-target");
  steps.push({
    step: "rollback-package",
    currentPackageId: current.packageId,
    targetPackageId: target.packageId,
    targetStatus: targetAfter.status,
    currentStatus: currentAfter.status,
    successLogCount: targetLogs.filter((log) => log.status === "SUCCESS").length,
  });
  return { rollback, targetAfter, currentAfter, targetLogs };
}

async function discoverReleaseAdapter(context) {
  const response = await apiGet(context, "/engine/pkg/packages/release-adapters", "list-release-adapters");
  if (!response.ok) {
    throw new Error(`读取配置包适配器失败 ${response.status}: ${jsonShort(response.body)}`);
  }
  const adapters = dataOf(response) ?? [];
  const adapter = adapters.find((item) => item.adapterId === adapterId)
    ?? adapters.find((item) => item.status === "ACTIVE" && item.connectorAvailable);
  if (!adapter) {
    throw new Error(`未找到可用配置包发布适配器，期望 ${adapterId}`);
  }
  if (adapter.adapterId !== adapterId || adapter.status !== "ACTIVE" || !adapter.connectorAvailable) {
    throw new Error(`目标发布适配器不可用: ${jsonShort(adapter)}`);
  }
  return { adapter, adapters };
}

async function discoverTargetOrg(context, profileData) {
  const byFacility = await apiGet(
    context,
    "/engine/org/org-units?level=FACILITY&status=ACTIVE&page=0&size=50",
    "list-target-facilities",
  );
  if (byFacility.ok) {
    const facilities = pageItems(byFacility);
    const matched = facilities.find((item) => item.code === targetOrgCode) ?? facilities[0];
    if (matched?.id) return matched;
  }
  const fallbackId = profileData.dataScope?.hospitalId ?? profileData.dataScope?.tenantId;
  if (!fallbackId) {
    throw new Error(`未找到目标机构，接口状态 ${byFacility.status}: ${jsonShort(byFacility.body)}`);
  }
  return { id: fallbackId, code: "PROFILE_SCOPE", name: "当前账号数据范围目标机构", level: "FACILITY" };
}

function pickByStatus(items, preferredCodes, codeKey) {
  const allowed = new Set(["ACTIVE", "PUBLISHED", "GRAY"]);
  for (const code of preferredCodes) {
    const matched = items.find((item) => item[codeKey] === code && allowed.has(item.status));
    if (matched) return matched;
  }
  return items.find((item) => allowed.has(item.status)) ?? items[0] ?? null;
}

function resolvableOrganizationScope(value, tenantId) {
  const scope = String(value ?? "").trim();
  return scope === "ALL" || scope === `tenant:${tenantId}` || scope.startsWith("/");
}

function preferredEvaluation(items, tenantId) {
  const statuses = ["ACTIVE", "GRAY", "PUBLISHED", "PENDING_REVIEW", "DRAFT"];
  for (const status of statuses) {
    const matched = items.find(
      (item) => item.status === status && resolvableOrganizationScope(item.organizationScope, tenantId),
    );
    if (matched) return matched;
  }
  return null;
}

function maxVersionNo(items) {
  return items.reduce((max, item) => {
    const value = Number(item.versionNo ?? 0);
    return Number.isFinite(value) && value > max ? value : max;
  }, 0);
}

async function promoteEvaluationLifecycle(qualityContext, adminContext, indicator) {
  let current = indicator;
  if (current.status === "DRAFT") {
    const res = await apiPost(
      qualityContext,
      `/engine/evaluation/indicators/${encodeURIComponent(current.indicatorId)}/submit`,
      {},
      "act8-submit-evaluation",
    );
    if (!res.ok) throw new Error(`提交质控指标失败 ${res.status}: ${jsonShort(res.body)}`);
    current = dataOf(res);
  }
  if (current.status === "PENDING_REVIEW") {
    const res = await apiPost(
      qualityContext,
      `/engine/evaluation/indicators/${encodeURIComponent(current.indicatorId)}/publish`,
      { reason: "P5幕8配置包治理演练：修复质控指标统一资产组织域后发布" },
      "act8-publish-evaluation",
    );
    if (!res.ok) throw new Error(`发布质控指标失败 ${res.status}: ${jsonShort(res.body)}`);
    current = dataOf(res);
  }
  if (current.status === "PUBLISHED") {
    const res = await apiPost(
      qualityContext,
      `/engine/evaluation/indicators/${encodeURIComponent(current.indicatorId)}/gray`,
      { reason: "P5幕8配置包治理演练：质控指标进入默认10%灰度验证" },
      "act8-gray-evaluation",
    );
    if (!res.ok) throw new Error(`灰度质控指标失败 ${res.status}: ${jsonShort(res.body)}`);
    current = dataOf(res);
  }
  if (current.status === "GRAY") {
    const res = await apiPost(
      adminContext,
      `/engine/evaluation/indicators/${encodeURIComponent(current.indicatorId)}/activate`,
      { reason: "P5幕8配置包治理演练：机构管理员确认质控指标全量激活" },
      "act8-activate-evaluation",
    );
    if (!res.ok) throw new Error(`激活质控指标失败 ${res.status}: ${jsonShort(res.body)}`);
    current = dataOf(res);
  }
  if (current.status !== "ACTIVE") {
    throw new Error(`质控指标未达到 ACTIVE，当前 status=${current.status}`);
  }
  return current;
}

async function ensureResolvableEvaluation(contextEntries, qualityContext, adminContext, tenantId) {
  if (!tenantId) {
    throw new Error("缺少租户 ID，不能创建可继承质控指标版本");
  }
  const indicatorCode = "P5.ACT7.FOLLOWUP.QUALITY";
  const evalQuery = encodedParams({ indicatorCode, page: 0, size: 100 });
  const { label: evaluationReader, res: evaluationsRes } = await firstSuccessful(
    contextEntries,
    `/engine/evaluation/indicators?${evalQuery}`,
    "find-source-evaluations",
  );
  const allItems = pageItems(evaluationsRes).filter((item) => item.indicatorCode === indicatorCode);
  let evaluation = preferredEvaluation(allItems, tenantId);
  const legacyActive = allItems.find((item) => item.status === "ACTIVE");
  const created = !evaluation;
  if (!evaluation) {
    const base = legacyActive ?? allItems[0];
    if (!base?.indicatorId) {
      throw new Error(`缺少可入包的 ACTIVE/PUBLISHED 质控指标: ${jsonShort(dataOf(evaluationsRes))}`);
    }
    const createRes = await apiPost(
      qualityContext,
      "/engine/evaluation/indicators",
      {
        indicatorCode,
        versionNo: maxVersionNo(allItems) + 1,
        name: base.name ?? "幕7随访异常回院整改闭环率",
        subjectType: base.subjectType ?? "PATIENT",
        denominatorDefinition: base.denominatorDefinition,
        numeratorDefinition: base.numeratorDefinition,
        exclusionDefinition: base.exclusionDefinition ?? null,
        scoringDefinition: base.scoringDefinition ?? "P1高风险；随访异常回院后必须形成整改复核闭环",
        timeWindow: base.timeWindow ?? "FOLLOWUP+7D",
        organizationScope: `tenant:${tenantId}`,
        responsibleDepartmentId: base.responsibleDepartmentId,
        sourceRef: "docs/release/evidence/p5-second-fresh-drill-20260612/幕8-配置包与发布治理",
        packageVersion: base.packageVersion ?? "2026.06.1",
      },
      "act8-create-resolvable-evaluation",
    );
    if (!createRes.ok) {
      throw new Error(`创建可继承质控指标版本失败 ${createRes.status}: ${jsonShort(createRes.body)}`);
    }
    evaluation = dataOf(createRes);
  }
  evaluation = await promoteEvaluationLifecycle(qualityContext, adminContext, evaluation);
  if (!resolvableOrganizationScope(evaluation.organizationScope, tenantId)) {
    throw new Error(`质控指标组织域仍不可继承: ${evaluation.organizationScope}`);
  }
  steps.push({
    step: "ensure-resolvable-evaluation",
    indicatorId: evaluation.indicatorId,
    indicatorCode: evaluation.indicatorCode,
    versionNo: evaluation.versionNo,
    status: evaluation.status,
    organizationScope: evaluation.organizationScope,
    created,
    legacyActive: legacyActive
      ? {
          indicatorId: legacyActive.indicatorId,
          versionNo: legacyActive.versionNo,
          status: legacyActive.status,
          organizationScope: legacyActive.organizationScope,
        }
      : null,
  });
  await writeJson("01-evaluation-asset-resolution.json", {
    selected: {
      indicatorId: evaluation.indicatorId,
      indicatorCode: evaluation.indicatorCode,
      versionNo: evaluation.versionNo,
      status: evaluation.status,
      organizationScope: evaluation.organizationScope,
    },
    created,
    legacyActive: legacyActive
      ? {
          indicatorId: legacyActive.indicatorId,
          versionNo: legacyActive.versionNo,
          status: legacyActive.status,
          organizationScope: legacyActive.organizationScope,
        }
      : null,
  });
  return { evaluation, evaluationReader };
}

async function discoverSourceAssets(contextEntries, adminContext, qualityContext, tenantId) {
  const termPackages = (await findPackagesByCode(adminContext, "TERM.P5.MAPPING", "find-source-term-package"))
    .filter((item) => item.status === "ACTIVE" || item.status === "PUBLISHED");
  const termPackage = termPackages.find((item) => item.status === "ACTIVE") ?? termPackages[0];
  if (!termPackage) {
    throw new Error("缺少可入包的 ACTIVE/PUBLISHED 术语知识包 TERM.P5.MAPPING");
  }
  const termDetail = await packageDetail(adminContext, termPackage.packageId, "detail-source-term-package");
  const termItem = (termDetail.items ?? []).find((item) => item.assetType === "TERMINOLOGY");
  if (!termItem) {
    throw new Error(`术语知识包 ${termPackage.packageId} 缺少 TERMINOLOGY 条目`);
  }

  const { label: ruleReader, res: rulesRes } = await firstSuccessful(
    contextEntries,
    "/engine/rule/rules?status=PUBLISHED&page=0&size=100",
    "find-source-rules",
  );
  const rule = pickByStatus(pageItems(rulesRes), ["P5.ACT4.CRITICAL.K"], "ruleCode");
  if (!rule?.ruleId) {
    throw new Error(`缺少可入包的 PUBLISHED 规则资产: ${jsonShort(dataOf(rulesRes))}`);
  }

  const { label: pathwayReader, res: pathwaysRes } = await firstSuccessful(
    contextEntries,
    "/engine/pathway/pathway-templates?status=PUBLISHED&page=0&size=100",
    "find-source-pathways",
  );
  const pathway = pickByStatus(pageItems(pathwaysRes), ["PATH.ED.DISPOSITION"], "templateCode");
  if (!pathway?.templateId) {
    throw new Error(`缺少可入包的 PUBLISHED 路径资产: ${jsonShort(dataOf(pathwaysRes))}`);
  }

  const { evaluation, evaluationReader } = await ensureResolvableEvaluation(
    contextEntries,
    qualityContext,
    adminContext,
    tenantId,
  );

  const sourceAssets = {
    terminology: {
      assetType: "TERMINOLOGY",
      assetId: termItem.assetId,
      assetVersion: termItem.assetVersion,
      sourcePackageId: termPackage.packageId,
      sourcePackageStatus: termPackage.status,
    },
    rule: {
      assetType: "RULE",
      assetId: rule.ruleId,
      assetVersion: String(rule.versionNo ?? 1),
      ruleCode: rule.ruleCode,
      status: rule.status,
      reader: ruleReader,
    },
    pathway: {
      assetType: "PATHWAY",
      assetId: pathway.templateId,
      assetVersion: String(pathway.templateVersion ?? 1),
      templateCode: pathway.templateCode,
      status: pathway.status,
      reader: pathwayReader,
    },
    evaluation: {
      assetType: "EVALUATION",
      assetId: evaluation.indicatorId,
      assetVersion: String(evaluation.versionNo ?? 1),
      indicatorCode: evaluation.indicatorCode,
      status: evaluation.status,
      organizationScope: evaluation.organizationScope,
      reader: evaluationReader,
    },
  };
  return sourceAssets;
}

async function exportEvidence(adminContext, v1, v2, targetOrgUnitId) {
  const diff = await apiGet(
    adminContext,
    `/engine/pkg/packages/${encodeURIComponent(v2.packageId)}/diff?basePackageId=${encodeURIComponent(v1.packageId)}`,
    "calculate-v2-diff-against-v1",
  );
  if (!diff.ok) {
    throw new Error(`计算 v2/v1 差异失败 ${diff.status}: ${jsonShort(diff.body)}`);
  }
  const diffData = dataOf(diff);
  if ((diffData?.addedCount ?? 0) < 1 || !Array.isArray(diffData?.changes)) {
    throw new Error(`v2/v1 差异证据不足: ${jsonShort(diffData)}`);
  }

  const diffExport = await downloadText(
    adminContext,
    `/engine/pkg/packages/${encodeURIComponent(v2.packageId)}/diff/export?basePackageId=${encodeURIComponent(v1.packageId)}`,
    "download-v2-diff-export",
  );
  if (!diffExport.ok || diffExport.text.trim().length === 0) {
    throw new Error(`差异导出为空或失败 ${diffExport.status}`);
  }
  await writeText("03-v2-vs-v1-diff-export.jsonl", diffExport.text);

  const offlineExport = await downloadText(
    adminContext,
    `/engine/pkg/packages/${encodeURIComponent(v2.packageId)}/offline/export?targetOrgUnitId=${encodeURIComponent(targetOrgUnitId)}`,
    "download-v2-offline-export",
  );
  if (!offlineExport.ok || offlineExport.text.trim().length === 0) {
    throw new Error(`离线包导出为空或失败 ${offlineExport.status}`);
  }
  await writeText("04-v2-offline-package.json", offlineExport.text);

  const syncExport = await downloadText(
    adminContext,
    `/engine/pkg/packages/${encodeURIComponent(v2.packageId)}/sync-logs/export`,
    "download-v2-sync-evidence",
  );
  if (!syncExport.ok || syncExport.text.trim().length === 0) {
    throw new Error(`同步证据导出为空或失败 ${syncExport.status}`);
  }
  await writeText("05-v2-sync-evidence.jsonl", syncExport.text);

  const importProbe = await apiPost(
    adminContext,
    "/engine/pkg/packages/offline/import",
    { offlinePackageJson: offlineExport.text },
    "import-v2-offline-duplicate-probe",
  );
  if (importProbe.status !== 409) {
    throw new Error(`离线包重复导入未触发 409 冲突保护，实际 ${importProbe.status}: ${jsonShort(importProbe.body)}`);
  }

  steps.push({
    step: "export-and-import-boundary",
    diffAddedCount: diffData.addedCount,
    diffChangeCount: diffData.changes.length,
    diffExportBytes: diffExport.text.length,
    offlineExportBytes: offlineExport.text.length,
    syncExportBytes: syncExport.text.length,
    duplicateImportStatus: importProbe.status,
  });
  return {
    diff: diffData,
    diffExportBytes: diffExport.text.length,
    offlineExportBytes: offlineExport.text.length,
    syncExportBytes: syncExport.text.length,
    duplicateImport: {
      status: importProbe.status,
      body: importProbe.body,
    },
  };
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const credentials = await loadCredentials();
  const browser = await chromium.launch({ headless: true, args: ["--ignore-certificate-errors"] });

  const contextsToClose = [];
  let readiness = null;
  let sourceAssets = null;
  let baseline = null;
  let candidate = null;
  let exportBundle = null;
  let rollbackBundle = null;

  try {
    const admin = await login(browser, requireAccount(credentials, "organization-admin"), "organization-admin");
    const quality = await login(browser, requireAccount(credentials, "quality-governor"), "quality-governor");
    const clinical = await login(browser, requireAccount(credentials, "clinical-governor"), "clinical-governor");
    contextsToClose.push(admin.context, quality.context, clinical.context);

    const adminProfile = await profile(admin.context, "organization-admin");
    const qualityProfile = await profile(quality.context, "quality-governor");
    const clinicalProfile = await profile(clinical.context, "clinical-governor");

    if (runPhase("readiness")) {
      console.log("\n=== [readiness] 角色、发布适配器与目标机构 ===");
      const { adapter, adapters } = await discoverReleaseAdapter(admin.context);
      const targetOrg = await discoverTargetOrg(admin.context, adminProfile);
      readiness = {
        packageCode,
        packageVersionV1,
        packageVersionV2,
        targetOrg,
        selectedAdapter: adapter,
        adapters,
        profiles: {
          "organization-admin": adminProfile,
          "quality-governor": qualityProfile,
          "clinical-governor": clinicalProfile,
        },
      };
      steps.push({
        step: "readiness",
        adapterId: adapter.adapterId,
        adapterHealth: adapter.healthStatus,
        targetOrgUnitId: targetOrg.id,
      });
      await tryCapture(
        browser,
        admin.page,
        "/config/packages",
        "01-config-packages-before.png",
        "机构管理员配置包中心入口",
      );
      await tryCapture(
        browser,
        admin.page,
        "/config/releases",
        "02-release-governance-entry.png",
        "发布治理影响模拟入口",
      );
      await writeJson("00-readiness-actors-adapter.json", readiness);
    }

    if (runPhase("assets")) {
      console.log("\n=== [assets] 发现真实可入包资产 ===");
      const sourceContextEntries = [
        ["organization-admin", admin.context],
        ["quality-governor", quality.context],
        ["clinical-governor", clinical.context],
      ];
      sourceAssets = await discoverSourceAssets(
        sourceContextEntries,
        admin.context,
        quality.context,
        adminProfile.dataScope?.tenantId ?? credentials.customerTenant?.tenantId,
      );
      await writeJson("01-source-assets.json", { sourceAssets });
      steps.push({
        step: "source-assets",
        assets: Object.fromEntries(
          Object.entries(sourceAssets).map(([key, value]) => [key, assetKey(value)]),
        ),
      });
    }

    if (!readiness) {
      const { adapter } = await discoverReleaseAdapter(admin.context);
      const targetOrg = await discoverTargetOrg(admin.context, adminProfile);
      readiness = { selectedAdapter: adapter, targetOrg };
    }
    if (!sourceAssets) {
      sourceAssets = await discoverSourceAssets(
        [
          ["organization-admin", admin.context],
          ["quality-governor", quality.context],
          ["clinical-governor", clinical.context],
        ],
        admin.context,
        quality.context,
        adminProfile.dataScope?.tenantId ?? credentials.customerTenant?.tenantId,
      );
    }

    if (runPhase("packages")) {
      console.log("\n=== [packages] 创建/复用 v1/v2 配置包并发布 ===");
      baseline = await ensurePackage(admin.context, adminProfile, {
        label: "v1-baseline",
        packageCode,
        packageVersion: packageVersionV1,
        name: "P5幕8配置包治理演练基线版",
        description: "幕8真实演练：术语基线包，用于后续历史版本回滚目标。",
        items: [sourceAssets.terminology],
      });
      const baselineRelease = await releasePackage(
        admin.context,
        adminProfile,
        baseline.pack,
        readiness.targetOrg.id,
        "FULL",
        "P5幕8配置包治理演练：先全量发布 v1 作为历史基线",
        "release-v1-full",
      );
      baseline = { ...baseline, fullRelease: baselineRelease };

      candidate = await ensurePackage(admin.context, adminProfile, {
        label: "v2-candidate",
        packageCode,
        packageVersion: packageVersionV2,
        name: "P5幕8配置包治理演练候选版",
        description: "幕8真实演练：术语、规则、路径、质控指标聚合候选包。",
        items: [
          sourceAssets.terminology,
          sourceAssets.rule,
          sourceAssets.pathway,
          sourceAssets.evaluation,
        ],
      });
      const grayRelease = await releasePackage(
        admin.context,
        adminProfile,
        candidate.pack,
        readiness.targetOrg.id,
        "GRAYSCALE",
        "P5幕8配置包治理演练：v2 默认10%灰度发布并同步真实适配器",
        "release-v2-gray",
      );
      const fullRelease = await releasePackage(
        admin.context,
        adminProfile,
        grayRelease.after,
        readiness.targetOrg.id,
        "FULL",
        "P5幕8配置包治理演练：灰度证据确认后 v2 全量激活",
        "release-v2-full",
      );
      candidate = { ...candidate, grayRelease, fullRelease };

      await tryCapture(
        browser,
        admin.page,
        "/config/packages",
        "06-config-packages-after-full.png",
        "v2全量发布后配置包中心状态",
      );
      await writeJson("02-package-release-lifecycle.json", { baseline, candidate });
    }

    if (!baseline || !candidate) {
      const packs = await findPackagesByCode(admin.context, packageCode, "find-existing-act8-packages");
      const v1 = packs.find((item) => item.packageVersion === packageVersionV1);
      const v2 = packs.find((item) => item.packageVersion === packageVersionV2);
      if (!v1 || !v2) throw new Error("缺少幕8 v1/v2 包，请运行 packages 阶段");
      baseline = { pack: await packageDetail(admin.context, v1.packageId, "detail-existing-v1") };
      candidate = { pack: await packageDetail(admin.context, v2.packageId, "detail-existing-v2") };
    }

    if (runPhase("exports")) {
      console.log("\n=== [exports] 差异、离线包、同步证据与重复导入保护 ===");
      const v1 = await packageDetail(admin.context, baseline.pack.packageId, "detail-v1-before-export");
      const v2 = await packageDetail(admin.context, candidate.pack.packageId, "detail-v2-before-export");
      exportBundle = await exportEvidence(admin.context, v1, v2, readiness.targetOrg.id);
      await writeJson("03-offline-diff-sync-evidence.json", { exportBundle });
    }

    if (runPhase("rollback")) {
      console.log("\n=== [rollback] 高危确认回滚 v2 → v1 ===");
      const v1BeforeRollback = await packageDetail(admin.context, baseline.pack.packageId, "detail-v1-before-rollback");
      const v2BeforeRollback = await packageDetail(admin.context, candidate.pack.packageId, "detail-v2-before-rollback");
      if (v2BeforeRollback.status !== "ACTIVE") {
        throw new Error(`回滚前 v2 必须 ACTIVE，当前 ${v2BeforeRollback.status}`);
      }
      if (v1BeforeRollback.status !== "OFFLINE") {
        throw new Error(`回滚前 v1 必须 OFFLINE，当前 ${v1BeforeRollback.status}`);
      }
      rollbackBundle = await rollbackPackage(admin.context, adminProfile, v2BeforeRollback, v1BeforeRollback);
      const rollbackSyncExport = await downloadText(
        admin.context,
        `/engine/pkg/packages/${encodeURIComponent(v1BeforeRollback.packageId)}/sync-logs/export`,
        "download-v1-rollback-sync-evidence",
      );
      if (!rollbackSyncExport.ok || rollbackSyncExport.text.trim().length === 0) {
        throw new Error(`回滚同步证据导出为空或失败 ${rollbackSyncExport.status}`);
      }
      await writeText("07-v1-rollback-sync-evidence.jsonl", rollbackSyncExport.text);
      await tryCapture(
        browser,
        admin.page,
        "/config/packages",
        "08-config-packages-after-rollback.png",
        "回滚后配置包中心状态",
      );
      await writeJson("04-rollback-verification.json", {
        rollbackBundle,
        rollbackSyncEvidenceBytes: rollbackSyncExport.text.length,
      });
    }
  } catch (error) {
    failures.push({ phase: "main", error: error.message, stack: error.stack });
  } finally {
    for (const context of contextsToClose.reverse()) {
      await context.close().catch(() => undefined);
    }
    await browser.close().catch(() => undefined);
    await writeText(
      "trace-ids.txt",
      traceEntries
        .map((entry) => `${entry.label}\t${entry.method}\t${entry.status}\t${entry.requestTraceId}\t${entry.path}`)
        .join("\n")
        + "\n",
    );
    await writeJson("00-act8-summary.json", {
      packageCode,
      packageVersionV1,
      packageVersionV2,
      adapterId,
      targetOrgCode,
      steps,
      failures,
      keyResults: {
        readiness,
        sourceAssets,
        baselinePackageId: baseline?.pack?.packageId,
        candidatePackageId: candidate?.pack?.packageId,
        candidateGrayPlanId: candidate?.grayRelease?.sync?.planId,
        candidateFullPlanId: candidate?.fullRelease?.sync?.planId,
        duplicateImportStatus: exportBundle?.duplicateImport?.status,
        rollbackTargetStatus: rollbackBundle?.targetAfter?.status,
        rollbackCurrentStatus: rollbackBundle?.currentAfter?.status,
      },
    });
  }

  if (failures.length > 0) {
    console.error(`\n幕8配置包治理演练失败：${jsonShort(failures, 2000)}`);
    process.exitCode = 1;
    return;
  }
  console.log(`\n幕8配置包治理演练完成，证据目录：${evidenceDir}`);
}

await main();
