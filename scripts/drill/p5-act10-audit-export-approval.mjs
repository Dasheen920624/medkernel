#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕10 审计导出审批。
// 剧本动作：
//   1. 合规审计员读取 /admin/audit，生成审计快照并申请 AUDIT_EVENT 导出。
//   2. 验证自审批被拒绝，组织管理员审批后通过大列表导出生成真实 CSV 文件。
//   3. 将真实导出任务登记回导出审批，验签审批证据与导出证据，并导出证据包。
//   4. 读取 /system/operations 与 /system/providers，证明未连接 / 模型关闭 / 备份状态诚实展示。
// 成功判定以服务端事实为准；截图只作为前台入口佐证。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕10-审计导出审批/
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕10-审计导出审批",
);

const runTag = process.env.DRILL_RUN_TAG ?? `p5-act10-audit-${Date.now()}`;
const runSuffix = safeIdentifier(runTag).slice(-12).toLowerCase() || String(Date.now()).slice(-12);
const idempotencyKey = process.env.DRILL_EXPORT_IDEMPOTENCY_KEY ?? `p5-act10-${runSuffix}`;

const failures = [];
const steps = [];
const traceEntries = [];

function safeIdentifier(value) {
  return String(value).replace(/[^A-Za-z0-9._-]/g, "");
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
  return JSON.stringify(redact(value)).slice(0, limit);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, val]) => {
        const lower = key.toLowerCase();
        if (
          lower.includes("password") ||
          lower.includes("cookie") ||
          lower.includes("token") ||
          lower.includes("secret") ||
          lower.includes("mfa") ||
          lower.includes("otp") ||
          lower.includes("recovery") ||
          lower === "signature" ||
          lower === "prevsignature" ||
          lower === "signaturevalue" ||
          lower === "signerpublickey"
        ) {
          return [key, "[REDACTED]"];
        }
        return [key, redact(val)];
      }),
    );
  }
  return value;
}

async function writeJson(name, value) {
  await writeFile(
    path.join(evidenceDir, name),
    `${JSON.stringify(redact({ runTag, generatedAt: new Date().toISOString(), ...value }), null, 2)}\n`,
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
    /* 页面存在轮询请求时，以加载态和短暂稳定为准。 */
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

async function login(browser, account, roleLabel) {
  const context = await browser.newContext({
    acceptDownloads: true,
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  const requestTraceId = traceId(`login-${roleLabel}`);
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: { username: account.username, password: account.password, tenantId: account.tenantId },
    headers: { "Content-Type": "application/json", "X-Trace-Id": requestTraceId },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(`${roleLabel} 登录失败：${response.status()} ${body.slice(0, 300)}`);
  }
  traceEntries.push({
    label: `login-${roleLabel}`,
    method: "POST",
    path: "/auth/login",
    status: response.status(),
    ok: response.ok(),
    requestTraceId,
  });
  return { context, page };
}

async function csrfToken(context) {
  const cookies = await context.cookies(baseUrl);
  return cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? "";
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
    parsed = { raw: text.slice(0, 1200) };
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
    parsed = { raw: text.slice(0, 1600) };
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

function absoluteApiUrl(uri) {
  if (uri.startsWith("http://") || uri.startsWith("https://")) return uri;
  if (uri.startsWith("/medkernel/")) return `${baseUrl}${uri}`;
  if (uri.startsWith("/api/v1/")) return `${baseUrl}/medkernel${uri}`;
  return `${apiBase}/${uri.replace(/^\/+/, "")}`;
}

async function apiDownload(context, uri, stage) {
  const requestTraceId = traceId(stage);
  const response = await context.request.get(absoluteApiUrl(uri), {
    headers: { "X-Trace-Id": requestTraceId },
  });
  const body = await response.body();
  traceEntries.push({
    label: stage,
    method: "GET",
    path: uri,
    status: response.status(),
    ok: response.ok(),
    requestTraceId,
    contentLength: body.length,
  });
  return { status: response.status(), ok: response.ok(), body };
}

function assertOk(response, step, acceptStatuses = []) {
  if (!response.ok && !acceptStatuses.includes(response.status)) {
    failures.push({ step, detail: { status: response.status, body: response.body } });
    return false;
  }
  return true;
}

async function profile(context, label) {
  const response = await apiGet(context, "/security/me", `${label}-security-me`);
  const data = response.body?.data ?? {};
  return {
    userId: data.userId ?? data.username,
    username: data.username,
    tenantId: data.dataScope?.tenantId,
    permissions: (data.permissions ?? []).map((p) => (typeof p === "string" ? p : p.code)).filter(Boolean),
    menuKeys: (data.menuKeys ?? data.menus ?? []).map((m) => (typeof m === "string" ? m : m.key)).filter(Boolean),
    dataScope: data.dataScope ?? {},
  };
}

function summarizeAuditEvent(item) {
  return {
    id: item.id,
    eventId: item.eventId,
    traceId: item.traceId,
    occurredAt: item.occurredAt,
    actorUserId: item.actorUserId,
    actionCode: item.actionCode ?? item.action,
    resourceType: item.resourceType,
    resourceId: item.resourceId,
    summary: item.summary,
    outcome: item.outcome,
    errorCode: item.errorCode,
    payloadDigest: item.payloadDigest,
    signaturePresent: Boolean(item.signature),
    signatureLength: item.signature?.length ?? 0,
  };
}

function summarizeApproval(approval) {
  if (!approval) return null;
  return {
    approvalId: approval.approvalId,
    resourceType: approval.resourceType,
    idempotencyKey: approval.idempotencyKey,
    status: approval.status,
    requestedBy: approval.requestedBy,
    reviewerId: approval.reviewerId,
    reviewDecision: approval.reviewDecision,
    approvalEvidenceId: approval.approvalEvidenceId,
    approvalEvidenceFileUri: approval.approvalEvidenceFileUri,
    exportUri: approval.exportUri,
    exportDigest: approval.exportDigest,
    exportEvidenceId: approval.exportEvidenceId,
    exportEvidenceFileUri: approval.exportEvidenceFileUri,
    version: approval.version,
    requestedAt: approval.requestedAt,
    reviewedAt: approval.reviewedAt,
    exportScopeSnapshot: approval.exportScopeSnapshot,
  };
}

function summarizeEvidence(evidence) {
  if (!evidence) return null;
  return {
    evidenceId: evidence.evidenceId,
    traceId: evidence.traceId,
    evidenceType: evidence.evidenceType,
    action: evidence.action,
    subjectType: evidence.subjectType,
    subjectId: evidence.subjectId,
    evidenceSummary: evidence.evidenceSummary,
    payloadHash: evidence.payloadHash,
    fileUri: evidence.fileUri,
    fileDigest: evidence.fileDigest,
    signatureAlgorithm: evidence.signatureAlgorithm,
    signatureValuePresent: Boolean(evidence.signatureValue),
    signerPublicKeyPresent: Boolean(evidence.signerPublicKey),
    isValid: evidence.isValid,
    createdAt: evidence.createdAt,
    createdBy: evidence.createdBy,
  };
}

async function waitForExportJob(context, jobId) {
  let latest = null;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const response = await apiGet(
      context,
      `/large-lists/exports/${encodeURIComponent(jobId)}`,
      `export-job-poll-${attempt}`,
    );
    if (!assertOk(response, `轮询导出任务 ${jobId}`)) return dataOf(response);
    latest = dataOf(response);
    if (latest?.status === "SUCCESS" || latest?.status === "FAILED") return latest;
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  return latest;
}

async function verifyEvidence(context, evidenceId, label) {
  const verify = await apiPost(
    context,
    `/compliance/evidence/snapshots/${encodeURIComponent(evidenceId)}/verify`,
    null,
    `verify-${label}`,
  );
  assertOk(verify, `验签证据 ${evidenceId}`);
  const data = dataOf(verify);
  if (!data?.isValid || !data?.signatureValid) {
    failures.push({ step: `证据 ${evidenceId} 验签未通过`, detail: data });
  }

  const detail = await apiGet(
    context,
    `/compliance/evidence/snapshots/${encodeURIComponent(evidenceId)}`,
    `evidence-detail-${label}`,
  );
  assertOk(detail, `读取证据详情 ${evidenceId}`);
  const evidence = dataOf(detail);

  const file = await apiDownload(
    context,
    evidence?.fileUri ?? `/api/v1/compliance/evidence/snapshots/${encodeURIComponent(evidenceId)}/file`,
    `download-evidence-${label}`,
  );
  if (!file.ok || file.body.length === 0) {
    failures.push({ step: `下载证据文件 ${evidenceId} 失败`, detail: { status: file.status } });
  } else {
    await writeFile(path.join(evidenceDir, `${label}-evidence-file.json`), file.body);
  }

  return {
    verify: data,
    detail: summarizeEvidence(evidence),
    fileBytes: file.body.length,
  };
}

async function captureAuditUi(browser, actorPage, labelPrefix) {
  await gotoPath(actorPage, "/admin/audit");
  await actorPage.getByText("审计链已启用", { exact: false }).waitFor({ timeout: 15000 });
  steps.push(await capture(browser, actorPage, `${labelPrefix}-admin-audit-events.png`, "审计与证据页事件列表"));

  const detailButton = actorPage.locator('button[aria-label^="查看详情"]').first();
  if ((await detailButton.count()) > 0) {
    await detailButton.click();
    await actorPage.getByText("审计事件详情", { exact: false }).waitFor({ timeout: 10000 });
    steps.push(await capture(browser, actorPage, `${labelPrefix}-audit-event-detail.png`, "审计事件详情抽屉"));
    await actorPage.locator(".ant-drawer:visible").last().locator(".ant-drawer-close").click();
    await waitForQuiet(actorPage);
  } else {
    failures.push({ step: "审计页未找到可打开的事件详情按钮", detail: { labelPrefix } });
  }
}

async function captureApprovalUi(browser, adminPage, filename, label) {
  await gotoPath(adminPage, "/admin/audit");
  await adminPage.getByText("审计链已启用", { exact: false }).waitFor({ timeout: 15000 });
  await adminPage.getByRole("tab", { name: "导出审批" }).click();
  await waitForQuiet(adminPage);
  steps.push(await capture(browser, adminPage, filename, label));
}

async function captureRuntimeUi(browser, adminPage) {
  await gotoPath(adminPage, "/system/providers");
  await adminPage.getByText("依赖健康", { exact: false }).waitFor({ timeout: 15000 });
  steps.push(await capture(browser, adminPage, "07-system-providers-runtime.png", "运行保障依赖健康与诚实降级"));
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const summary = {
    act: "P5幕10-审计导出审批",
    environment: baseUrl,
    credentialSource: credentialPath,
    idempotencyKey,
    failures,
  };

  const browser = await chromium.launch();
  let auditorSession;
  let adminSession;
  try {
    const credentials = await loadCredentials();
    auditorSession = await login(browser, requireAccount(credentials, "compliance-auditor"), "compliance-auditor");
    adminSession = await login(browser, requireAccount(credentials, "organization-admin"), "organization-admin");

    const auditor = await profile(auditorSession.context, "compliance-auditor");
    const admin = await profile(adminSession.context, "organization-admin");
    summary.actors = {
      auditor: {
        username: auditor.username,
        userId: auditor.userId,
        tenantId: auditor.tenantId,
        permissions: auditor.permissions.filter((p) => ["audit.read", "audit.export", "list.export"].includes(p)),
        menuKeys: auditor.menuKeys.filter((key) => key.includes("audit")),
      },
      admin: {
        username: admin.username,
        userId: admin.userId,
        tenantId: admin.tenantId,
        permissions: admin.permissions.filter((p) =>
          ["audit.read", "audit.export", "list.export", "system.read"].includes(p),
        ),
        menuKeys: admin.menuKeys.filter((key) => key.includes("audit") || key.includes("system")),
      },
    };
    if (auditor.userId === admin.userId) {
      failures.push({ step: "审批人与申请人必须不同", detail: summary.actors });
    }

    await captureAuditUi(browser, auditorSession.page, "01");

    const snapshot = await apiPost(
      auditorSession.context,
      `/compliance/audit/snapshot?reason=${encodeURIComponent(`P5幕10审计导出审批快照 ${runTag}`)}`,
      null,
      "audit-snapshot",
    );
    assertOk(snapshot, "生成审计快照");
    const snapshotEvent = dataOf(snapshot);
    summary.auditSnapshot = summarizeAuditEvent(snapshotEvent);

    const events = await apiGet(auditorSession.context, "/compliance/audit/events?size=10", "audit-events-after-snapshot");
    assertOk(events, "读取审计事件列表");
    summary.auditEvents = pageItems(events).map(summarizeAuditEvent);
    if (summary.auditEvents.length === 0) {
      failures.push({ step: "审计事件列表为空", detail: events.body });
    }

    const exportScope = { resourceType: "AUDIT_EVENT", filters: {}, selectedScope: "FILTERED_RESULT" };
    const request = await apiPost(
      auditorSession.context,
      "/compliance/exports:request",
      {
        resourceType: "AUDIT_EVENT",
        exportScope,
        reason: `P5幕10审计事件导出申请 ${runTag}`,
        idempotencyKey,
      },
      "request-export-approval",
    );
    assertOk(request, "提交审计导出申请");
    const requestedApproval = dataOf(request);
    summary.requestedApproval = summarizeApproval(requestedApproval);
    if (requestedApproval?.status !== "REQUESTED") {
      failures.push({ step: "导出申请未进入 REQUESTED", detail: requestedApproval });
    }

    const selfApprove = await apiPost(
      auditorSession.context,
      `/compliance/exports/${encodeURIComponent(requestedApproval.approvalId)}:approve`,
      {
        decision: "APPROVE",
        comment: `P5幕10自审批负向探针 ${runTag}`,
        expectedVersion: requestedApproval.version,
      },
      "self-approve-negative",
    );
    summary.selfApproveNegative = {
      rejected: !selfApprove.ok,
      status: selfApprove.status,
      code: selfApprove.body?.code,
      detail: selfApprove.body?.detail,
      traceId: selfApprove.body?.traceId,
    };
    if (selfApprove.ok || selfApprove.status !== 403 || selfApprove.body?.code !== "ENG-API-004") {
      failures.push({ step: "自审批负向探针未按权限错误拒绝", detail: selfApprove.body });
    }

    await captureApprovalUi(browser, adminSession.page, "03-export-approval-requested.png", "导出审批待审批列表");

    const approved = await apiPost(
      adminSession.context,
      `/compliance/exports/${encodeURIComponent(requestedApproval.approvalId)}:approve`,
      {
        decision: "APPROVE",
        comment: `组织管理员批准 P5幕10 审计导出 ${runTag}`,
        expectedVersion: requestedApproval.version,
      },
      "admin-approve-export",
    );
    assertOk(approved, "管理员审批导出申请");
    const approvedApproval = dataOf(approved);
    summary.approvedApproval = summarizeApproval(approvedApproval);
    if (approvedApproval?.status !== "APPROVED" || !approvedApproval?.approvalEvidenceId) {
      failures.push({ step: "导出审批未进入 APPROVED 或未生成审批证据", detail: approvedApproval });
    }

    await captureApprovalUi(browser, adminSession.page, "04-export-approval-approved.png", "导出审批已批准待生成文件");

    const exportSubmit = await apiPost(
      adminSession.context,
      "/large-lists/exports",
      {
        resourceType: "AUDIT_EVENT",
        filters: {},
        selectedScope: "FILTERED_RESULT",
        idempotencyKey,
      },
      "submit-large-list-export",
      { "Idempotency-Key": idempotencyKey },
    );
    assertOk(exportSubmit, "提交大列表导出任务");
    const exportSubmission = dataOf(exportSubmit);
    summary.exportSubmission = exportSubmission;
    const jobId = exportSubmission?.jobId;
    if (!jobId) {
      failures.push({ step: "大列表导出未返回 jobId", detail: exportSubmission });
    }

    const exportJob = jobId ? await waitForExportJob(adminSession.context, jobId) : null;
    summary.exportJob = exportJob;
    if (exportJob?.status !== "SUCCESS") {
      failures.push({ step: "大列表导出任务未成功", detail: exportJob });
    }

    const completed = await apiPost(
      adminSession.context,
      `/compliance/exports/${encodeURIComponent(requestedApproval.approvalId)}:complete-from-job`,
      {
        jobId,
        reason: `登记 P5幕10 审计导出真实文件 ${runTag}`,
        expectedVersion: approvedApproval.version,
      },
      "complete-export-approval",
    );
    assertOk(completed, "登记导出完成");
    const completedApproval = dataOf(completed);
    summary.completedApproval = summarizeApproval(completedApproval);
    if (
      completedApproval?.status !== "EXPORTED" ||
      !completedApproval.exportUri ||
      !/^sm3:[0-9a-f]{64}$/.test(completedApproval.exportDigest ?? "") ||
      !completedApproval.exportEvidenceId
    ) {
      failures.push({ step: "导出审批未形成 EXPORTED 真实文件闭环", detail: completedApproval });
    }

    const csv = completedApproval?.exportUri
      ? await apiDownload(adminSession.context, completedApproval.exportUri, "download-approved-csv")
      : { ok: false, body: Buffer.alloc(0), status: 0 };
    if (!csv.ok || csv.body.length === 0) {
      failures.push({ step: "审计 CSV 真实文件下载失败", detail: { status: csv.status } });
    } else {
      await writeFile(path.join(evidenceDir, "audit-events-export.csv"), csv.body);
      const firstLine = csv.body.toString("utf8").split(/\r?\n/)[0]?.replace(/^\uFEFF/, "");
      summary.downloadedAuditExport = {
        file: "audit-events-export.csv",
        bytes: csv.body.length,
        firstLine,
        digest: completedApproval.exportDigest,
      };
    }

    await captureApprovalUi(browser, adminSession.page, "05-export-approval-exported.png", "导出审批已导出含证据入口");

    const evidenceResults = {};
    if (completedApproval?.approvalEvidenceId) {
      evidenceResults.approvalEvidence = await verifyEvidence(
        adminSession.context,
        completedApproval.approvalEvidenceId,
        "approval",
      );
    }
    if (completedApproval?.exportEvidenceId) {
      evidenceResults.exportEvidence = await verifyEvidence(
        adminSession.context,
        completedApproval.exportEvidenceId,
        "export",
      );
    }
    summary.evidence = evidenceResults;

    const evidenceExport = await apiPost(
      adminSession.context,
      "/compliance/evidence/snapshots/export?evidenceType=COMPLIANCE_EXPORT",
      null,
      "export-evidence-package",
    );
    assertOk(evidenceExport, "导出合规证据包");
    const evidencePackage = dataOf(evidenceExport);
    summary.evidencePackage = evidencePackage;
    if (
      evidencePackage?.status !== "COMPLETED" ||
      !/^sm3:[0-9a-f]{64}$/.test(evidencePackage.archiveHash ?? "") ||
      !evidencePackage.archiveUri ||
      Number(evidencePackage.itemCount ?? 0) < 1
    ) {
      failures.push({ step: "证据包导出结果不完整", detail: evidencePackage });
    } else {
      const archive = await apiDownload(
        adminSession.context,
        evidencePackage.archiveUri,
        "download-evidence-package",
      );
      if (!archive.ok || archive.body.length === 0) {
        failures.push({ step: "证据包真实文件下载失败", detail: { status: archive.status } });
      } else {
        await writeFile(path.join(evidenceDir, "compliance-export-evidence-package.ndjson"), archive.body);
        summary.evidencePackageFile = {
          file: "compliance-export-evidence-package.ndjson",
          bytes: archive.body.length,
          lineCount: archive.body.toString("utf8").trim().split(/\r?\n/).filter(Boolean).length,
        };
      }
    }

    const operations = await apiGet(adminSession.context, "/system/operations", "runtime-operations");
    assertOk(operations, "读取系统运行态");
    const runtime = dataOf(operations);
    const dependencies = Array.isArray(runtime?.dependencies) ? runtime.dependencies : [];
    summary.runtimeOperations = {
      serviceName: runtime?.serviceName,
      healthStatus: runtime?.healthStatus,
      deploymentMode: runtime?.deploymentMode,
      databaseDialect: runtime?.databaseDialect,
      activeProfiles: runtime?.activeProfiles,
      dependencyStatusCounts: dependencies.reduce((acc, item) => {
        acc[item.status] = (acc[item.status] ?? 0) + 1;
        return acc;
      }, {}),
      honestDegradeDependencies: dependencies
        .filter((item) => ["NOT_CONNECTED", "MODEL_DISABLED", "DEGRADED"].includes(item.status))
        .map((item) => ({ key: item.key, displayName: item.displayName, status: item.status, detail: item.detail })),
      backup: runtime?.backup,
      domesticCompatibility: runtime?.domesticCompatibility
        ? {
            overallStatus: runtime.domesticCompatibility.overallStatus,
            checkedAt: runtime.domesticCompatibility.checkedAt,
            itemStatuses: runtime.domesticCompatibility.items?.map((item) => ({
              key: item.key,
              status: item.status,
              reason: item.reason,
            })),
          }
        : null,
    };
    if (!dependencies.some((item) => item.status === "NOT_CONNECTED")) {
      failures.push({ step: "运行态未暴露 NOT_CONNECTED 依赖", detail: dependencies });
    }
    if (!dependencies.some((item) => item.status === "MODEL_DISABLED")) {
      failures.push({ step: "运行态未暴露 MODEL_DISABLED 模型降级", detail: dependencies });
    }

    await captureRuntimeUi(browser, adminSession.page);
    steps.push(await capture(browser, adminSession.page, "08-runtime-post-drill.png", "幕10后运行态收口截图"));

    await writeJson("10-server-facts.json", {
      auditSnapshot: summary.auditSnapshot,
      auditEvents: summary.auditEvents,
      requestedApproval: summary.requestedApproval,
      selfApproveNegative: summary.selfApproveNegative,
      approvedApproval: summary.approvedApproval,
      exportSubmission: summary.exportSubmission,
      exportJob: summary.exportJob,
      completedApproval: summary.completedApproval,
      downloadedAuditExport: summary.downloadedAuditExport,
      evidence: summary.evidence,
      evidencePackage: summary.evidencePackage,
      evidencePackageFile: summary.evidencePackageFile,
      runtimeOperations: summary.runtimeOperations,
    });
  } catch (error) {
    failures.push({ step: "幕10审计导出审批脚本异常中断", detail: String(error).slice(0, 1200) });
  } finally {
    await auditorSession?.context.close().catch(() => undefined);
    await adminSession?.context.close().catch(() => undefined);
    await browser.close();
  }

  summary.steps = steps;
  await writeJson("00-act10-summary.json", summary);
  await writeJson("trace-ids.json", { traceEntries });
  await writeText(
    "README.md",
    `# P5 幕10 · 审计导出审批\n\n`
      + `> 执行时间：${new Date().toISOString()}\n`
      + `> 环境：\`${baseUrl}\`\n`
      + `> 脚本：\`scripts/drill/p5-act10-audit-export-approval.mjs\`\n`
      + `> runTag：\`${runTag}\`\n\n`
      + `## 结果\n\n`
      + `- failures：${failures.length}\n`
      + `- 审计导出幂等键：\`${idempotencyKey}\`\n`
      + `- 申请人：\`compliance-auditor\`；审批/导出登记人：\`organization-admin\`。\n`
      + `- 自审批负向探针预期：\`403 / ENG-API-004\`。\n`
      + `- 导出闭环预期：审批 \`APPROVED\` → 大列表任务 \`SUCCESS\` → 审批 \`EXPORTED\`，导出摘要为 \`sm3:64hex\`。\n`
      + `- 证据预期：审批证据与导出证据均由后端验签通过，证据包导出返回真实 NDJSON 文件。\n`
      + `- 运行态预期：\`/system/operations\` 暴露 \`NOT_CONNECTED\` 与 \`MODEL_DISABLED\`，不伪装外部系统或模型可用。\n\n`
      + `## 证据文件\n\n`
      + `- \`00-act10-summary.json\`：主汇总，敏感字段已脱敏。\n`
      + `- \`10-server-facts.json\`：审计、审批、导出、证据验签、证据包与运行态服务端事实。\n`
      + `- \`trace-ids.json\`：脚本请求 traceId 与响应 traceId。\n`
      + `- \`audit-events-export.csv\`：后端大列表真实生成并下载的审计 CSV。\n`
      + `- \`approval-evidence-file.json\` / \`export-evidence-file.json\`：后端证据快照真实文件。\n`
      + `- \`compliance-export-evidence-package.ndjson\`：后端证据包真实文件。\n`
      + `- \`01-*.png\`、\`03-*.png\` 至 \`08-*.png\`：真实前台截图，带 URL 栏。\n\n`,
  );

  if (failures.length > 0) {
    console.error("幕10审计导出审批存在失败步骤：");
    for (const failure of failures) {
      console.error(`- ${failure.step}: ${shortJson(failure.detail ?? {})}`);
    }
    process.exitCode = 1;
    return;
  }
  console.log("幕10审计导出审批全部通过，证据已写入", evidenceDir);
}

await main();
