#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕9 系统接入正幕。
// 剧本动作：
//   1. 集成运维员在真实前台 /adapter/hub 新增 HIS 适配器，探活到 HEALTHY；另建 EMR 适配器保留
//      NOT_CONNECTED 诚实降级证据。
//   2. 创建回调通道、接入申请（ADAPTER + FHIR 双路径），按状态机推进到 ONLINE，并用服务端回查证明。
//   3. 登记区域协同来源：先验证未可信分级被 REGIONAL_SOURCE_UNGRADED 拒绝，再登记带可信证据的来源。
//   4. 生成数据质量报告，并制造/重放一条真实死信补偿链，保留原死信证据。
// 成功判定一律以服务端事实为准；截图只作为前台入口佐证。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕9-系统接入正幕/
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕9-系统接入正幕",
);

const receiverBaseUrl = process.env.DRILL_RECEIVER_BASE_URL ?? "http://127.0.0.1:9301";
const runTag = process.env.DRILL_RUN_TAG ?? `p5-act9-main-${Date.now()}`;
const runSuffix = safeIdentifier(runTag).slice(-12).toLowerCase() || String(Date.now()).slice(-12);

const hisAdapterId = process.env.DRILL_HIS_ADAPTER_ID ?? `p5-his-main-${runSuffix}`;
const emrAdapterId = process.env.DRILL_EMR_ADAPTER_ID ?? `p5-emr-main-${runSuffix}`;
const webhookId = process.env.DRILL_WEBHOOK_ID ?? `p5-callback-${runSuffix}`;
const adapterOnboardingId = process.env.DRILL_ADAPTER_ONBOARDING_ID ?? `p5-onb-his-${runSuffix}`;
const fhirOnboardingId = process.env.DRILL_FHIR_ONBOARDING_ID ?? `p5-onb-fhir-${runSuffix}`;
const regionalSourceId = process.env.DRILL_REGIONAL_SOURCE_ID ?? `p5-regional-lab-${runSuffix}`;
const outboundMessageId = process.env.DRILL_OUTBOUND_MESSAGE_ID ?? `p5-act9-dead-${runSuffix}`;

const failures = [];
const steps = [];
const traceEntries = [];

function safeIdentifier(value) {
  return String(value).replace(/[^A-Za-z0-9]/g, "");
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

function shortJson(value, limit = 1200) {
  return JSON.stringify(value).slice(0, limit);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function connectorConfig(base, outboundPath = "/messages") {
  return JSON.stringify({
    baseUrl: base,
    healthPath: "/health",
    outboundPath,
    connectTimeoutMs: 800,
    requestTimeoutMs: 1600,
    fieldMappings: [
      { sourcePath: "/patient/id", targetPath: "/patient/id" },
      { sourcePath: "/encounter/id", targetPath: "/encounter/id" },
    ],
  });
}

async function writeJson(name, value) {
  await writeFile(
    path.join(evidenceDir, name),
    `${JSON.stringify({ runTag, generatedAt: new Date().toISOString(), ...value }, null, 2)}\n`,
  );
}

async function writeText(name, value) {
  await writeFile(path.join(evidenceDir, name), value);
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
  await page.screenshot({ path: rawPath, fullPage: true });
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
    /* 部分页面存在轮询请求，以加载态消失和短暂稳定为准。 */
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
  const data = res.body?.data ?? {};
  return {
    username: data.username,
    userId: data.userId ?? data.username,
    permissions: (data.permissions ?? []).map((p) => (typeof p === "string" ? p : p.code)).filter(Boolean),
    menuKeys: (data.menuKeys ?? data.menus ?? []).map((m) => (typeof m === "string" ? m : m.key)).filter(Boolean),
    dataScope: data.dataScope ?? {},
  };
}

function orgPathFromProfile(me) {
  const scope = me.dataScope ?? {};
  return scope.orgPath ?? scope.path ?? `/tenant:${scope.tenantId ?? "p5-hospital"}/P5-HOSP`;
}

function assertOk(response, step, acceptStatuses = []) {
  if (!response.ok && !acceptStatuses.includes(response.status)) {
    failures.push({ step, detail: { status: response.status, body: response.body } });
    return false;
  }
  return true;
}

async function findAdapter(context, adapterId, stage) {
  const response = await apiGet(context, "/engine/integration/adapters", stage);
  const adapters = Array.isArray(dataOf(response)) ? dataOf(response) : [];
  return adapters.find((item) => item.adapterId === adapterId) ?? null;
}

async function findOnboarding(context, onboardingId, stage) {
  const response = await apiGet(context, "/engine/integration/onboardings", stage);
  const onboardings = Array.isArray(dataOf(response)) ? dataOf(response) : [];
  return onboardings.find((item) => item.onboardingId === onboardingId) ?? null;
}

async function createAdapterByApi(context, adapter) {
  const existing = await findAdapter(context, adapter.adapterId, `adapter-precheck-${adapter.adapterId}`);
  if (existing) return existing;
  const response = await apiPost(context, "/engine/integration/adapters", adapter, `create-${adapter.adapterId}`);
  assertOk(response, `创建适配器 ${adapter.adapterId}`);
  return dataOf(response);
}

async function ensureHisAdapterFromUi(browser, context, page, summary) {
  await gotoPath(page, "/adapter/hub");
  steps.push(await capture(browser, page, "01-adapter-hub-before.png", "系统接入页总览（正幕起跑）"));

  let adapter = await findAdapter(context, hisAdapterId, "his-adapter-before-ui");
  if (!adapter) {
    await page.getByRole("button", { name: "新增适配器" }).click();
    const modal = page.locator(".ant-modal-content", { hasText: "新增适配器" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByLabel("适配器标识").fill(hisAdapterId);
    await modal.getByLabel("系统名称").fill(`P5 HIS 正幕接入网关 ${runSuffix}`);
    await modal.getByLabel("服务地址").fill(receiverBaseUrl);
    await modal.getByLabel("来源字段路径").fill("/patient/id");
    await modal.getByLabel("标准字段路径").fill("/patient/id");
    steps.push(await capture(browser, page, "02-his-adapter-create-form.png", "HIS 适配器新增表单"));
    await modal.getByRole("button", { name: "提交适配器" }).click();
    await modal.waitFor({ state: "hidden", timeout: 15000 }).catch(() => undefined);
    await waitForQuiet(page);
  }

  adapter = await findAdapter(context, hisAdapterId, "his-adapter-after-create");
  if (!adapter) {
    failures.push({ step: "HIS 适配器创建后服务端回查缺失", detail: { hisAdapterId } });
    return null;
  }

  const row = page.getByRole("row", { name: new RegExp(hisAdapterId) });
  if ((await row.count()) > 0) {
    await row.first().getByRole("button", { name: "健康诊断" }).click();
    await page.waitForTimeout(2500);
    steps.push(await capture(browser, page, "03-his-adapter-health.png", "HIS 适配器真实探活结果"));
  } else {
    const health = await apiPost(
      context,
      `/engine/integration/adapters/${encodeURIComponent(hisAdapterId)}/health-check`,
      {},
      "his-health-api-fallback",
    );
    assertOk(health, "HIS 适配器健康诊断");
  }

  const healthy = await findAdapter(context, hisAdapterId, "his-adapter-after-health");
  summary.hisAdapter = {
    adapterId: healthy?.adapterId ?? hisAdapterId,
    healthStatus: healthy?.healthStatus ?? null,
    status: healthy?.status ?? null,
    mappedFieldCountExpected: 2,
  };
  if (healthy?.healthStatus !== "HEALTHY") {
    failures.push({ step: "HIS 适配器未达 HEALTHY", detail: healthy });
  }
  return healthy;
}

async function ensureEmrNotConnectedAdapter(context, summary) {
  const adapter = await createAdapterByApi(context, {
    adapterId: emrAdapterId,
    name: `P5 EMR 断连诚实降级网关 ${runSuffix}`,
    protocolType: "REST",
    configJson: connectorConfig("http://127.0.0.1:9399"),
  });
  if (!adapter) return null;
  const health = await apiPost(
    context,
    `/engine/integration/adapters/${encodeURIComponent(emrAdapterId)}/health-check`,
    {},
    "emr-not-connected-health",
  );
  assertOk(health, "EMR 断连适配器健康诊断");
  const checked = await findAdapter(context, emrAdapterId, "emr-adapter-after-health");
  summary.emrAdapter = {
    adapterId: checked?.adapterId ?? emrAdapterId,
    healthStatus: checked?.healthStatus ?? null,
    status: checked?.status ?? null,
    errorExpectation: "NOT_CONNECTED 必须诚实展示，不伪造 HEALTHY",
  };
  if (checked?.healthStatus !== "NOT_CONNECTED") {
    failures.push({ step: "EMR 断连适配器未返回 NOT_CONNECTED", detail: checked });
  }
  return checked;
}

async function ensureWebhook(context, summary) {
  const existing = await apiGet(context, "/engine/integration/webhooks", "webhook-before");
  const existingWebhook = (Array.isArray(dataOf(existing)) ? dataOf(existing) : []).find(
    (item) => item.webhookId === webhookId,
  );
  if (existingWebhook) {
    summary.webhook = {
      webhookId,
      created: false,
      sharedSecretWrittenToEvidence: false,
      status: existingWebhook.status,
    };
    return existingWebhook;
  }
  const created = await apiPost(
    context,
    "/engine/integration/webhooks",
    {
      webhookId,
      name: `P5 正幕回调通道 ${runSuffix}`,
      callbackUrl: `${receiverBaseUrl}/messages`,
      eventsSubscribed: "clinical.event.accepted,package.release.completed",
    },
    "webhook-create",
  );
  if (!assertOk(created, "创建回调通道")) return null;
  const data = dataOf(created);
  summary.webhook = {
    webhookId,
    created: true,
    status: data?.status ?? null,
    sharedSecretGeneratedOnce: Boolean(data?.sharedSecret),
    sharedSecretWrittenToEvidence: false,
  };
  return { ...data, sharedSecret: undefined };
}

async function testWebhookSignature(context, summary) {
  const response = await apiPost(
    context,
    "/engine/integration/webhooks/test",
    {
      webhookId,
      payload: JSON.stringify({ event: "clinical.event.accepted", runTag }),
    },
    "webhook-signature-preview",
  );
  if (!assertOk(response, "Webhook 签名预览")) return null;
  const data = dataOf(response);
  summary.webhookSignature = {
    webhookId: data.webhookId,
    status: data.status,
    connectionStatus: data.connectionStatus,
    signatureLength: data.signature?.length ?? 0,
    message: data.message,
  };
  return data;
}

async function createAndAdvanceOnboarding(context, payload, targetSummary) {
  let onboarding = await findOnboarding(context, payload.onboardingId, `onboarding-before-${payload.onboardingId}`);
  if (!onboarding) {
    const created = await apiPost(context, "/engine/integration/onboardings", payload, `create-${payload.onboardingId}`);
    if (!assertOk(created, `创建接入申请 ${payload.onboardingId}`)) return null;
    onboarding = dataOf(created);
  }
  const observed = [onboarding];
  for (const status of ["AUTH_CONFIGURED", "MAPPING_CONFIGURED", "ONLINE"]) {
    onboarding = await findOnboarding(context, payload.onboardingId, `onboarding-current-${payload.onboardingId}-${status}`);
    if (onboarding?.status === status || onboarding?.status === "ONLINE") {
      observed.push(onboarding);
      continue;
    }
    const advanced = await apiPost(
      context,
      `/engine/integration/onboardings/${encodeURIComponent(payload.onboardingId)}/advance`,
      {
        targetStatus: status,
        evidenceText: `P5 幕9正幕 ${payload.name} 阶段 ${status} 留证，runTag=${runTag}`,
      },
      `advance-${payload.onboardingId}-${status}`,
    );
    if (!assertOk(advanced, `推进接入申请 ${payload.onboardingId} 到 ${status}`)) return null;
    observed.push(dataOf(advanced));
  }
  const final = await findOnboarding(context, payload.onboardingId, `onboarding-final-${payload.onboardingId}`);
  targetSummary.onboardingId = payload.onboardingId;
  targetSummary.status = final?.status ?? null;
  targetSummary.routeType = final?.routeType ?? null;
  targetSummary.routeReference = final?.routeReference ?? null;
  targetSummary.healthStatus = final?.healthStatus ?? null;
  targetSummary.mappedFieldCount = final?.mappedFieldCount ?? null;
  targetSummary.blockers = final?.blockers ?? [];
  targetSummary.observedStatuses = observed.map((item) => item?.status).filter(Boolean);
  if (final?.status !== "ONLINE") {
    failures.push({ step: `接入申请 ${payload.onboardingId} 未达 ONLINE`, detail: final });
  }
  return final;
}

async function registerRegionalSource(context, orgPath, summary) {
  const ungraded = await apiPost(
    context,
    "/engine/integration/regional-sources",
    {
      sourceId: `${regionalSourceId}-ungraded`,
      regionalNetworkName: "P5 区域检验互认网络",
      sourceOrganizationId: "REG-LAB-001",
      sourceOrganizationName: "P5 区域检验中心",
      trustLevel: "",
      evidenceText: "未完成可信分级的负向探针",
      adapterId: hisAdapterId,
      onboardingId: adapterOnboardingId,
      orgPath,
    },
    "regional-source-ungraded",
  );
  summary.regionalSource = {
    ungradedRejected: !ungraded.ok,
    ungradedStatus: ungraded.status,
    ungradedCode: ungraded.body?.code ?? null,
    ungradedTraceId: ungraded.body?.traceId ?? null,
  };
  if (ungraded.ok || ungraded.body?.code !== "REGIONAL_SOURCE_UNGRADED") {
    failures.push({ step: "未可信分级区域来源没有被 REGIONAL_SOURCE_UNGRADED 拒绝", detail: ungraded.body });
    return null;
  }

  const existing = await apiGet(context, "/engine/integration/regional-sources", "regional-sources-before");
  const found = (Array.isArray(dataOf(existing)) ? dataOf(existing) : []).find(
    (item) => item.sourceId === regionalSourceId,
  );
  if (found) {
    summary.regionalSource.registered = found;
    return found;
  }
  const registered = await apiPost(
    context,
    "/engine/integration/regional-sources",
    {
      sourceId: regionalSourceId,
      regionalNetworkName: "P5 区域检验互认网络",
      sourceOrganizationId: "REG-LAB-001",
      sourceOrganizationName: "P5 区域检验中心",
      trustLevel: "MEDIUM",
      evidenceText: "P5 幕9正幕区域来源验收单与互认协议编号 ACT9-REG-001",
      adapterId: hisAdapterId,
      onboardingId: adapterOnboardingId,
      orgPath,
    },
    "regional-source-register",
  );
  if (!assertOk(registered, "登记可信区域来源")) return null;
  summary.regionalSource.registered = dataOf(registered);
  return dataOf(registered);
}

async function generateQualityReport(context, summary) {
  const response = await apiPost(context, "/engine/integration/data-quality/reports", {}, "data-quality-report");
  if (!assertOk(response, "生成数据质量报告")) return null;
  const report = dataOf(response);
  summary.dataQualityReport = {
    reportId: report.reportId,
    requiredFieldRate: report.requiredFieldRate,
    adapterTotal: report.adapterTotal,
    mappedAdapterCount: report.mappedAdapterCount,
    mappingRate: report.mappingRate,
    notConnectedCount: report.notConnectedCount,
    misconfiguredCount: report.misconfiguredCount,
    gapSummary: report.gapSummary,
    traceId: report.traceId,
  };
  return report;
}

async function findLog(context, messageId, stage) {
  const response = await apiGet(context, "/engine/integration/logs?page=1&size=100", stage);
  return pageItems(response).find((item) => item.messageId === messageId) ?? null;
}

async function waitForLogStatus(context, messageId, statuses, stage) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const log = await findLog(context, messageId, `${stage}-${attempt}`);
    if (log && statuses.includes(log.status)) return log;
    await new Promise((resolve) => setTimeout(resolve, 700));
  }
  return findLog(context, messageId, `${stage}-final`);
}

async function createAndReplayDeadLetter(context, summary) {
  const payload = {
    messageId: outboundMessageId,
    traceId: traceId("dead-letter-payload"),
    adapterId: emrAdapterId,
    targetSystem: "P5 EMR 断连演练系统",
    protocolType: "REST",
    payloadSummary: "幕9正幕死信补偿演练",
    payload: {
      runTag,
      patientId: "P5-ACT9-REGIONAL-001",
      event: "regional.lab.report.ready",
    },
    maxRetries: 1,
  };
  const existing = await findLog(context, outboundMessageId, "dead-letter-existing");
  if (!existing) {
    const queued = await apiPost(context, "/engine/integration/messages/outbound", payload, "dead-letter-enqueue");
    if (!assertOk(queued, "登记断连出站消息")) return null;
  }
  let log = await waitForLogStatus(context, outboundMessageId, ["NOT_CONNECTED", "FAILED", "DEAD_LETTER"], "dead-letter-wait");
  if (!log) {
    failures.push({ step: "断连出站消息未进入日志", detail: { outboundMessageId } });
    return null;
  }
  if (log.status !== "DEAD_LETTER") {
    const retry = await apiPost(
      context,
      `/engine/integration/logs/${encodeURIComponent(outboundMessageId)}/retry`,
      {},
      "dead-letter-retry",
    );
    if (!assertOk(retry, "手动重试断连消息")) return null;
    log = dataOf(retry);
  }
  if (log?.status !== "DEAD_LETTER") {
    failures.push({ step: "断连消息重试后未进入 DEAD_LETTER", detail: log });
    return null;
  }
  const replay = await apiPost(
    context,
    `/engine/integration/callbacks/dead-letter/${encodeURIComponent(outboundMessageId)}/replay`,
    {},
    "callback-dead-letter-replay",
  );
  if (!assertOk(replay, "回调管理视角死信重放")) return null;
  const replayData = dataOf(replay);
  summary.deadLetter = {
    sourceMessageId: outboundMessageId,
    sourceStatus: log.status,
    sourceRetryCount: log.retryCount,
    replayMessageId: replayData.replayMessageId,
    replayStatus: replayData.status,
    blocksMainFlow: replayData.blocksMainFlow,
    message: replayData.message,
  };
  if (replayData.blocksMainFlow !== false) {
    failures.push({ step: "死信重放结果错误地阻断主流程", detail: replayData });
  }
  return replayData;
}

async function captureAdapterHubPanels(browser, page) {
  await gotoPath(page, "/adapter/hub");
  steps.push(await capture(browser, page, "04-adapter-hub-after-adapters.png", "适配器目录与必接系统状态"));
  await page.getByRole("tab", { name: "接入向导" }).click();
  await page.waitForTimeout(700);
  steps.push(await capture(browser, page, "05-onboarding-online.png", "接入申请双路径推进结果"));
  await page.getByRole("tab", { name: "回调通道" }).click();
  await page.waitForTimeout(700);
  steps.push(await capture(browser, page, "06-webhook-channel.png", "回调通道与签名预览入口"));
  await page.getByRole("tab", { name: "区域来源" }).click();
  await page.waitForTimeout(700);
  steps.push(await capture(browser, page, "07-regional-source.png", "区域来源可信分级登记结果"));
  await page.getByRole("tab", { name: "死信重放" }).click();
  await page.waitForTimeout(700);
  steps.push(await capture(browser, page, "08-dead-letter-replay.png", "死信重放保留原始证据"));
  await page.getByRole("tab", { name: "数据质量看板" }).click();
  await page.waitForTimeout(700);
  steps.push(await capture(browser, page, "09-data-quality-panel.png", "数据质量看板与缺口摘要"));
}

async function collectServerFacts(context, summary) {
  const adapters = await apiGet(context, "/engine/integration/adapters", "facts-adapters");
  const status = await apiGet(context, "/engine/integration/adapter-hub/status", "facts-hub-status");
  const onboardings = await apiGet(context, "/engine/integration/onboardings", "facts-onboardings");
  const regionalSources = await apiGet(context, "/engine/integration/regional-sources", "facts-regional");
  const logs = await apiGet(context, "/engine/integration/logs?page=1&size=100", "facts-logs");
  const webhooks = await apiGet(context, "/engine/integration/webhooks", "facts-webhooks");
  const runAdapters = (Array.isArray(dataOf(adapters)) ? dataOf(adapters) : []).filter((item) =>
    [hisAdapterId, emrAdapterId].includes(item.adapterId),
  );
  const runOnboardings = (Array.isArray(dataOf(onboardings)) ? dataOf(onboardings) : []).filter((item) =>
    [adapterOnboardingId, fhirOnboardingId].includes(item.onboardingId),
  );
  const adapterById = Object.fromEntries(runAdapters.map((item) => [item.adapterId, item]));
  const onboardingById = Object.fromEntries(runOnboardings.map((item) => [item.onboardingId, item]));
  const facts = {
    adapters: runAdapters,
    adapterHubStatus: dataOf(status),
    canonicalRequiredSourceBindings: [
      {
        sourceSystem: "HIS",
        route: "ADAPTER",
        adapterId: hisAdapterId,
        onboardingId: adapterOnboardingId,
        healthStatus: adapterById[hisAdapterId]?.healthStatus ?? null,
        onboardingStatus: onboardingById[adapterOnboardingId]?.status ?? null,
        ready:
          adapterById[hisAdapterId]?.healthStatus === "HEALTHY"
          && onboardingById[adapterOnboardingId]?.status === "ONLINE"
          && (onboardingById[adapterOnboardingId]?.blockers ?? []).length === 0,
      },
      {
        sourceSystem: "EMR",
        route: "ADAPTER",
        adapterId: emrAdapterId,
        onboardingId: null,
        healthStatus: adapterById[emrAdapterId]?.healthStatus ?? null,
        onboardingStatus: null,
        ready: false,
        gaps: ["NOT_CONNECTED：未接入真实外部连接器"],
      },
      {
        sourceSystem: "LIS",
        route: "FHIR_R4",
        adapterId: null,
        onboardingId: fhirOnboardingId,
        healthStatus: onboardingById[fhirOnboardingId]?.healthStatus ?? null,
        onboardingStatus: onboardingById[fhirOnboardingId]?.status ?? null,
        ready: false,
        gaps: onboardingById[fhirOnboardingId]?.blockers ?? [],
      },
    ],
    onboardings: runOnboardings,
    regionalSources: (Array.isArray(dataOf(regionalSources)) ? dataOf(regionalSources) : []).filter(
      (item) => item.sourceId === regionalSourceId,
    ),
    logs: pageItems(logs).filter(
      (item) => item.messageId === outboundMessageId || item.messageId === summary.deadLetter?.replayMessageId,
    ),
    webhooks: (Array.isArray(dataOf(webhooks)) ? dataOf(webhooks) : [])
      .filter((item) => item.webhookId === webhookId)
      .map((item) => ({ ...item, sharedSecret: undefined })),
  };
  await writeJson("10-server-facts.json", facts);
  return facts;
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const summary = {
    act: "P5幕9-系统接入正幕",
    environment: baseUrl,
    receiver: receiverBaseUrl,
    credentialSource: credentialPath,
    identifiers: {
      hisAdapterId,
      emrAdapterId,
      webhookId,
      adapterOnboardingId,
      fhirOnboardingId,
      regionalSourceId,
      outboundMessageId,
    },
    failures,
  };

  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    const { context, page } = await login(
      browser,
      requireAccount(credentials, "integration-operator"),
      "integration-operator",
    );
    try {
      const me = await profile(context, "integration-operator");
      summary.integrationOperator = {
        username: me.username,
        permissions: me.permissions.filter((item) => item.startsWith("integration.")),
        menuKeys: me.menuKeys.filter((item) => item.includes("adapter")),
        orgPath: orgPathFromProfile(me),
      };
      const orgPath = orgPathFromProfile(me);

      await ensureHisAdapterFromUi(browser, context, page, summary);
      await ensureEmrNotConnectedAdapter(context, summary);
      await ensureWebhook(context, summary);
      await testWebhookSignature(context, summary);

      summary.adapterOnboarding = {};
      await createAndAdvanceOnboarding(
        context,
        {
          onboardingId: adapterOnboardingId,
          name: `P5 HIS 正幕接入申请 ${runSuffix}`,
          accessMode: "ADAPTER",
          adapterId: hisAdapterId,
          sourceSystem: "HIS",
          businessScenario: "急诊患者主数据与检验报告接入",
          orgPath,
          callbackWebhookId: webhookId,
        },
        summary.adapterOnboarding,
      );

      summary.fhirOnboarding = {};
      await createAndAdvanceOnboarding(
        context,
        {
          onboardingId: fhirOnboardingId,
          name: `P5 FHIR 正幕接入申请 ${runSuffix}`,
          accessMode: "FHIR",
          fhirVersion: "R4",
          sourceSystem: "LIS",
          businessScenario: "FHIR R4 检验报告门面接入",
          orgPath,
          callbackWebhookId: webhookId,
        },
        summary.fhirOnboarding,
      );

      await registerRegionalSource(context, orgPath, summary);
      await generateQualityReport(context, summary);
      await createAndReplayDeadLetter(context, summary);
      await captureAdapterHubPanels(browser, page);
      summary.serverFacts = await collectServerFacts(context, summary);
    } finally {
      await context.close();
    }
  } catch (error) {
    failures.push({ step: "幕9正幕脚本异常中断", detail: String(error).slice(0, 800) });
  } finally {
    await browser.close();
  }

  await writeJson("00-act9-main-summary.json", summary);
  await writeJson("trace-ids.json", { traceEntries });
  await writeText(
    "README.md",
    `# P5 幕9 · 系统接入正幕\n\n`
      + `> 执行时间：${new Date().toISOString()}\n`
      + `> 环境：\`${baseUrl}\`\n`
      + `> 脚本：\`scripts/drill/p5-act9-main-stage.mjs\`\n`
      + `> runTag：\`${runTag}\`\n\n`
      + `## 结果\n\n`
      + `- failures：${failures.length}\n`
      + `- HIS 适配器：\`${hisAdapterId}\`，预期 HEALTHY。\n`
      + `- EMR 适配器：\`${emrAdapterId}\`，预期 NOT_CONNECTED 诚实降级。\n`
      + `- 接入申请：\`${adapterOnboardingId}\`（ADAPTER）与 \`${fhirOnboardingId}\`（FHIR R4）。\n`
      + `- 区域来源：未分级负向探针必须返回 \`REGIONAL_SOURCE_UNGRADED\`；可信登记来源 \`${regionalSourceId}\`。\n`
      + `- 死信：原始消息 \`${outboundMessageId}\` 保留，回调管理视角重放创建补偿消息。\n\n`
      + `## 证据文件\n\n`
      + `- \`00-act9-main-summary.json\`：主汇总，未写入 Webhook 共享密钥。\n`
      + `- \`10-server-facts.json\`：适配器、AdapterHub、接入申请、区域来源、死信和回调通道服务端事实。\n`
      + `- \`trace-ids.json\`：脚本请求 traceId 与响应 traceId。\n`
      + `- \`01-*.png\` 至 \`09-*.png\`：真实前台截图，带 URL 栏。\n\n`,
  );

  if (failures.length > 0) {
    console.error("幕9系统接入正幕存在失败步骤：");
    for (const failure of failures) {
      console.error(`- ${failure.step}: ${shortJson(failure.detail ?? {})}`);
    }
    process.exitCode = 1;
    return;
  }
  console.log("幕9系统接入正幕全部通过，证据已写入", evidenceDir);
}

await main();
