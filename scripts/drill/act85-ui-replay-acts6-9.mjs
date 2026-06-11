#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const requireFromFrontend = createRequire(
  new URL("../../frontend/package.json", import.meta.url),
);
const { chromium } = requireFromFrontend("playwright");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";
const evidenceRoot = path.join(
  repoRoot,
  "docs/release/evidence/v1.0-drill-20260611",
);

const actDirs = {
  act6: path.join(evidenceRoot, "幕6-推荐引擎全链/ui-replay"),
  act7: path.join(evidenceRoot, "幕7-随访与质控评估/ui-replay"),
  act8: path.join(evidenceRoot, "幕8-配置包与发布治理/ui-replay"),
  act9: path.join(evidenceRoot, "幕9-第三方对接能力案例集/ui-replay"),
};

const actors = {
  labTechnician: "drill-hospital-20260611:drill-lab-technician-20260611",
  hospitalAdmin: "drill-hospital-20260611:drill-hospital-admin-20260611",
  respiratoryDoctor: "drill-hospital-20260611:drill-respiratory-doctor-20260611",
  cardiologyDoctor: "drill-hospital-20260611:drill-cardiology-doctor-20260611",
  respiratoryNurse: "drill-hospital-20260611:drill-respiratory-nurse-20260611",
  clinicalPharmacist: "drill-hospital-20260611:drill-clinical-pharmacist-20260611",
  qaManager: "drill-hospital-20260611:drill-role-qa-manager-20260611",
  itOps: "drill-hospital-20260611:drill-it-ops-20260611",
};

const drillPatient = {
  mpiId: "mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY",
  encounterId: "enc-act6-8oh7bn024a",
  patientPathwayId: "pp-9b4c8389-c46e-4a12-bf5f-6d66be5768fc",
  packageVersion: "2026.06.11-act2-024101",
};

function loadCredentials() {
  const raw = execFileSync(
    "ssh",
    [
      "-o",
      "BatchMode=yes",
      "-o",
      "StrictHostKeyChecking=no",
      sshTarget,
      `cat ${credentialPath}`,
    ],
    { encoding: "utf8" },
  );
  return JSON.parse(raw).credentials;
}

function publicActor(actor) {
  return {
    tenantId: actor.tenantId,
    username: actor.username,
    displayName: actor.displayName,
    roleCode: actor.roleCode,
  };
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function shortId() {
  return randomBytes(5).toString("hex");
}

function nowIso() {
  return new Date().toISOString();
}

async function ensureDirs() {
  await Promise.all(Object.values(actDirs).map((dir) => mkdir(dir, { recursive: true })));
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function waitForReady(page, text, timeout = 25000) {
  await page.getByText(text, { exact: false }).first().waitFor({ timeout });
  await waitForQuiet(page);
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

async function capture(browser, page, dir, filename, label) {
  const finalPath = path.join(dir, filename);
  const rawPath = path.join(dir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  return {
    label,
    screenshot: path.relative(dir, finalPath),
    url: page.url(),
  };
}

async function login(browser, credentials, actorKey) {
  const actor = credentials[actorKey];
  if (!actor) throw new Error(`missing credentials for ${actorKey}`);
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.getByLabel("工号 / 账号").fill(actor.username);
  await page.getByLabel("密码").fill(actor.currentPassword);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 25000 });
  await waitForQuiet(page);
  return { context, page, actor: publicActor(actor) };
}

async function gotoAndWait(page, route, readyText) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForReady(page, readyText);
}

async function bodyText(page) {
  return page.locator("body").innerText({ timeout: 8000 });
}

async function visibleText(page, text) {
  return (await page.getByText(text, { exact: false }).count()) > 0;
}

async function waitForVisibleTextOrContinue(page, text, timeout = 8000) {
  return page
    .getByText(text, { exact: false })
    .first()
    .waitFor({ timeout })
    .then(() => true)
    .catch(() => false);
}

async function apiRequest(page, method, apiPath, body = undefined, options = {}) {
  return page.evaluate(
    async ({ method, apiPath, body, options }) => {
      const token = document.cookie
        .split("; ")
        .find((part) => part.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];
      const headers = {
        "Content-Type": "application/json",
        "X-Trace-Id": options.traceId || crypto.randomUUID(),
      };
      if (token) headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
      const response = await fetch(`/medkernel/api/v1${apiPath}`, {
        method,
        credentials: "same-origin",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      let json = null;
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        json = { raw: text };
      }
      return { ok: response.ok, status: response.status, json };
    },
    { method, apiPath, body, options },
  );
}

async function apiJson(page, method, apiPath, body = undefined, options = {}) {
  const response = await apiRequest(page, method, apiPath, body, options);
  if (!response.ok && !options.allowFailure) {
    throw new Error(`${method} ${apiPath} returned ${response.status}: ${JSON.stringify(response.json)}`);
  }
  return response;
}

async function pollFor(page, description, fn, timeoutMs = 45000) {
  const startedAt = Date.now();
  let lastValue;
  while (Date.now() - startedAt < timeoutMs) {
    lastValue = await fn();
    if (lastValue) return lastValue;
    await page.waitForTimeout(1200);
  }
  throw new Error(`timeout waiting for ${description}; last=${JSON.stringify(lastValue)}`);
}

function replaceStringsDeep(value, oldValue, newValue) {
  if (typeof value === "string") return value.replaceAll(oldValue, newValue);
  if (Array.isArray(value)) {
    return value.map((item) => replaceStringsDeep(item, oldValue, newValue));
  }
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, replaceStringsDeep(item, oldValue, newValue)]),
    );
  }
  return value;
}

async function loadAct6EventTemplates() {
  const critical = JSON.parse(
    await readFile(
      path.join(evidenceRoot, "幕6-推荐引擎全链/02-critical-potassium-event-recommendation-closure.json"),
      "utf8",
    ),
  );
  const ddi = JSON.parse(
    await readFile(
      path.join(evidenceRoot, "幕6-推荐引擎全链/03-ddi-order-override-pharmacist-review.json"),
      "utf8",
    ),
  );
  return {
    critical: critical.operations.find((operation) => operation.label === "lab-post-critical-potassium-event").request,
    ddi: ddi.operations.find((operation) => operation.label === "his-post-warfarin-aspirin-order-event").request,
  };
}

function makeClinicalEventRequest(template, runTag, kind) {
  const cloned = replaceStringsDeep(template, "act6-8oh7bn024a", runTag);
  const occurredAt = nowIso();
  cloned.occurredAt = occurredAt;
  cloned.idempotencyKey =
    kind === "critical" ? `${runTag}-k-event` : `${runTag}-ddi-event`;
  cloned.payload.admission.admissionTime = occurredAt;
  if (kind === "critical") {
    cloned.payload.results[0].valueNumeric = 6.9;
    cloned.payload.results[0].eventTime = occurredAt;
    cloned.payload.results[0].sourceRecordId = `${runTag}-K001`;
  }
  return cloned;
}

async function injectAct6Events(browser, credentials, runTag) {
  const templates = await loadAct6EventTemplates();
  const criticalRequest = makeClinicalEventRequest(templates.critical, runTag, "critical");
  const ddiRequest = makeClinicalEventRequest(templates.ddi, runTag, "ddi");

  const lab = await login(browser, credentials, actors.labTechnician);
  const critical = await apiJson(lab.page, "POST", "/engine/clinical-events", criticalRequest, {
    traceId: criticalRequest.idempotencyKey,
  });
  await lab.context.close();

  const admin = await login(browser, credentials, actors.hospitalAdmin);
  const ddi = await apiJson(admin.page, "POST", "/engine/clinical-events", ddiRequest, {
    traceId: ddiRequest.idempotencyKey,
  });

  const criticalCard = await pollFor(admin.page, "critical recommendation card", async () => {
    const response = await apiJson(
      admin.page,
      "GET",
      `/engine/recommendations/clinical-cards?patientId=${encodeURIComponent(
        drillPatient.mpiId,
      )}&page=1&size=50`,
    );
    return (response.json?.data?.items ?? []).find((card) => card.traceId === criticalRequest.idempotencyKey);
  });
  const ddiCard = await pollFor(admin.page, "DDI recommendation card", async () => {
    const response = await apiJson(
      admin.page,
      "GET",
      `/engine/recommendations/clinical-cards?patientId=${encodeURIComponent(
        drillPatient.mpiId,
      )}&page=1&size=50`,
    );
    return (response.json?.data?.items ?? []).find((card) => card.traceId === ddiRequest.idempotencyKey);
  });
  await admin.context.close();

  return {
    runTag,
    critical: {
      eventId: criticalRequest.eventId,
      traceId: criticalRequest.idempotencyKey,
      eventStatus: critical.json?.data?.status,
      cardId: criticalCard.cardId,
      title: criticalCard.title,
      status: criticalCard.status,
    },
    ddi: {
      eventId: ddiRequest.eventId,
      traceId: ddiRequest.idempotencyKey,
      eventStatus: ddi.json?.data?.status,
      cardId: ddiCard.cardId,
      title: ddiCard.title,
      status: ddiCard.status,
    },
  };
}

async function chooseAntdSelect(scope, formItemLabel, optionText, page) {
  const item = scope.locator(".ant-form-item").filter({ hasText: formItemLabel }).first();
  await item.locator(".ant-select-selector").click();
  await page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option")
    .filter({ hasText: optionText })
    .first()
    .click();
}

async function fillPatientFilter(page) {
  const input = page.getByPlaceholder("输入患者 ID").first();
  await input.fill(drillPatient.mpiId);
  await waitForQuiet(page);
}

async function completeTodoIfVisible(page, cardId, completionReason) {
  const row = page.locator("tr").filter({ hasText: cardId }).first();
  if ((await row.count()) === 0) return { status: "not_visible" };
  await row.getByRole("button", { name: "完成" }).click();
  await page.getByLabel("完成说明").fill(completionReason);
  await page.getByRole("button", { name: "确认完成" }).click();
  await page
    .locator(".ant-message-notice-content")
    .filter({ hasText: "待办已完成" })
    .first()
    .waitFor({ timeout: 12000 })
    .catch(() => undefined);
  await waitForQuiet(page);
  return { status: "completed_from_ui" };
}

async function markNotificationsRead(page) {
  const markAll = page.getByRole("button", { name: "全部已读" }).first();
  if ((await markAll.count()) > 0) {
    await markAll.click();
    await page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "已标记为已读" })
      .first()
      .waitFor({ timeout: 12000 })
      .catch(() => undefined);
    await waitForQuiet(page);
    return { status: "marked_page_read" };
  }
  return { status: "no_unread_button" };
}

async function openRecommendationCard(page, cardId) {
  await page.getByPlaceholder("输入患者 ID").fill(drillPatient.mpiId);
  await page.getByText(cardId, { exact: false }).first().waitFor({ timeout: 25000 });
  const row = page.locator("tr").filter({ hasText: cardId }).first();
  await row.getByRole("button", { name: "查看与人机反馈" }).click();
  const drawer = page.locator(".ant-drawer:visible").last();
  await drawer.getByText("智能建议人机闭环反馈", { exact: false }).waitFor({ timeout: 25000 });
  await waitForQuiet(page);
  return drawer;
}

async function acceptRecommendation(page) {
  const drawer = page.locator(".ant-drawer:visible").last();
  await drawer.getByRole("button", { name: "确认并予以采纳 (ACCEPT)" }).click();
  await page
    .locator(".ant-message-notice-content")
    .filter({ hasText: "已登记采纳" })
    .first()
    .waitFor({ timeout: 15000 })
    .catch(() => undefined);
  await waitForQuiet(page);
}

async function rejectRecommendation(page) {
  const drawer = page.locator(".ant-drawer:visible").last();
  await drawer.locator(".ant-tabs-tab").filter({ hasText: "拒绝驳回建议" }).first().click();
  await chooseAntdSelect(drawer, "医生拒绝/不采纳的临床抗拒原因", "其他合理临床抉择", page);
  await drawer.locator("textarea:visible").last().fill(
    "幕8.5前台复演：已有个体化抗凝适应证，保留医嘱并安排INR与出血风险监测。",
  );
  await drawer.getByRole("button", { name: "确认拒绝采纳该建议 (REJECT)" }).click();
  await page
    .locator(".ant-message-notice-content")
    .filter({ hasText: "已登记不采纳反馈" })
    .first()
    .waitFor({ timeout: 15000 })
    .catch(() => undefined);
  await waitForQuiet(page);
}

async function captureRecommendationDiagnosis(browser, page, dir, filename) {
  const drawer = page.locator(".ant-drawer:visible").last();
  await drawer.getByRole("button", { name: "可信推荐归因与决策审计追溯 (diagnose)" }).click();
  await page.getByText("推荐决策链可信归因审计", { exact: false }).waitFor({ timeout: 25000 });
  await waitForQuiet(page);
  return capture(browser, page, dir, filename, "推荐决策链可信归因审计抽屉");
}

async function replayAct6(browser, credentials, injection) {
  const screenshots = [];

  const respiratory = await login(browser, credentials, actors.respiratoryDoctor);
  await gotoAndWait(respiratory.page, "/mpi", "患者主索引 MPI");
  await respiratory.page.getByPlaceholder("支持按姓名或 MPI ID 检索...").fill(drillPatient.mpiId);
  await respiratory.page.getByRole("button", { name: "检索过滤" }).click();
  await respiratory.page.getByText(drillPatient.mpiId, { exact: false }).first().waitFor({ timeout: 25000 });
  await respiratory.page.getByRole("button", { name: "患者360" }).first().click();
  await respiratory.page.getByText("上下文快照", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "01-mpi-patient-360.png", "MPI 患者360能看见快照与在径路径"));

  await gotoAndWait(respiratory.page, "/pathway/patients", "患者路径");
  await fillPatientFilter(respiratory.page);
  await respiratory.page.getByText(drillPatient.patientPathwayId, { exact: false }).first().waitFor({ timeout: 25000 });
  await respiratory.page.getByRole("button", { name: "办理推进与解释追溯" }).first().click();
  await respiratory.page.getByText("Milestone", { exact: false }).first().waitFor({ timeout: 25000 }).catch(() => undefined);
  await waitForQuiet(respiratory.page);
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "02-pathway-runtime-position.png", "医生端患者路径能定位当前节点与关键时钟"));

  await gotoAndWait(respiratory.page, "/workflow/todos", "工作流协同待办中心");
  await respiratory.page.getByLabel("待办来源").first().click();
  await respiratory.page.locator(".ant-select-item-option").filter({ hasText: "临床提醒" }).first().click();
  const criticalTodoVisible = await waitForVisibleTextOrContinue(respiratory.page, injection.critical.cardId);
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "03-critical-todo-received.png", "呼吸科医生在待办中心收到血钾危急值临床提醒"));

  await gotoAndWait(respiratory.page, "/notifications", "通知中心");
  await respiratory.page.getByText(injection.critical.traceId, { exact: false }).first().waitFor({ timeout: 25000 }).catch(() => undefined);
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "04-critical-notification-received.png", "呼吸科医生在通知中心收到血钾危急值通知"));
  const criticalNotificationRead = await markNotificationsRead(respiratory.page);
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "05-critical-notification-read.png", "呼吸科医生前台标记通知已读"));

  await gotoAndWait(respiratory.page, "/cdss/fatigue", "智能建议治理");
  await openRecommendationCard(respiratory.page, injection.critical.cardId);
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "06-critical-card-feedback-before.png", "血钾危急值推荐卡：前台查看依据与反馈区"));
  screenshots.push(await captureRecommendationDiagnosis(browser, respiratory.page, actDirs.act6, "07-critical-card-diagnose.png"));
  await respiratory.page.locator(".ant-drawer:visible").last().locator(".ant-drawer-close").first().click();
  await respiratory.page.getByText("智能建议人机闭环反馈", { exact: false }).waitFor({ state: "hidden", timeout: 10000 }).catch(() => undefined);
  await acceptRecommendation(respiratory.page);
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "08-critical-card-accepted.png", "呼吸科医生前台采纳危急值提醒"));

  await gotoAndWait(respiratory.page, "/workflow/todos", "工作流协同待办中心");
  await respiratory.page.getByLabel("待办来源").first().click();
  await respiratory.page.locator(".ant-select-item-option").filter({ hasText: "临床提醒" }).first().click();
  const criticalTodoCompletion = await completeTodoIfVisible(
    respiratory.page,
    injection.critical.cardId,
    "幕8.5前台复演：危急值推荐已由医生采纳，待办闭环。",
  );
  screenshots.push(await capture(browser, respiratory.page, actDirs.act6, "09-critical-todo-completed.png", "危急值待办在前台完成或显示为无待处理"));
  await respiratory.context.close();

  const cardiology = await login(browser, credentials, actors.cardiologyDoctor);
  await gotoAndWait(cardiology.page, "/workflow/todos", "工作流协同待办中心");
  await cardiology.page.getByLabel("待办来源").first().click();
  await cardiology.page.locator(".ant-select-item-option").filter({ hasText: "临床提醒" }).first().click();
  const ddiTodoVisible = await waitForVisibleTextOrContinue(cardiology.page, injection.ddi.cardId);
  screenshots.push(await capture(browser, cardiology.page, actDirs.act6, "10-ddi-todo-received.png", "心内科医生在待办中心查看 DDI 临床提醒"));

  await gotoAndWait(cardiology.page, "/notifications", "通知中心");
  screenshots.push(await capture(browser, cardiology.page, actDirs.act6, "11-ddi-notification-center.png", "心内科医生在通知中心查看 DDI 相关通知"));
  const ddiNotificationRead = await markNotificationsRead(cardiology.page);

  await gotoAndWait(cardiology.page, "/cdss/fatigue", "智能建议治理");
  await openRecommendationCard(cardiology.page, injection.ddi.cardId);
  screenshots.push(await capture(browser, cardiology.page, actDirs.act6, "12-ddi-card-feedback-before.png", "DDI 推荐卡：前台查看依据与拒绝理由区"));
  await rejectRecommendation(cardiology.page);
  screenshots.push(await capture(browser, cardiology.page, actDirs.act6, "13-ddi-card-rejected.png", "心内科医生前台记录 DDI 覆盖理由"));
  await gotoAndWait(cardiology.page, "/workflow/todos", "工作流协同待办中心");
  await cardiology.page.getByLabel("待办来源").first().click();
  await cardiology.page.locator(".ant-select-item-option").filter({ hasText: "临床提醒" }).first().click();
  const ddiTodoCompletion = await completeTodoIfVisible(
    cardiology.page,
    injection.ddi.cardId,
    "幕8.5前台复演：DDI推荐已记录个体化覆盖理由，待办闭环。",
  );
  screenshots.push(await capture(browser, cardiology.page, actDirs.act6, "14-ddi-todo-completed.png", "DDI 待办在前台完成或显示为无待处理"));
  await cardiology.context.close();

  const pharmacist = await login(browser, credentials, actors.clinicalPharmacist);
  await gotoAndWait(pharmacist.page, "/cdss/fatigue", "智能建议治理");
  await pharmacist.page.getByPlaceholder("输入患者 ID").fill(drillPatient.mpiId);
  await pharmacist.page.getByText(injection.ddi.cardId, { exact: false }).first().waitFor({ timeout: 25000 }).catch(() => undefined);
  screenshots.push(await capture(browser, pharmacist.page, actDirs.act6, "15-pharmacist-ddi-review.png", "临床药师前台复核 DDI 覆盖后的推荐卡"));
  await pharmacist.context.close();

  const doctorApi = await login(browser, credentials, actors.respiratoryDoctor);
  const stats = await apiJson(doctorApi.page, "GET", `/engine/recommendations/stats?patientId=${encodeURIComponent(drillPatient.mpiId)}`);
  await doctorApi.context.close();

  return {
    act: "幕6-推荐引擎全链",
    actors: {
      respiratoryDoctor: publicActor(credentials[actors.respiratoryDoctor]),
      cardiologyDoctor: publicActor(credentials[actors.cardiologyDoctor]),
      clinicalPharmacist: publicActor(credentials[actors.clinicalPharmacist]),
    },
    injection,
    notificationClosure: {
      critical: criticalNotificationRead,
      ddi: ddiNotificationRead,
    },
    todoClosure: {
      critical: { initiallyVisible: criticalTodoVisible, ...criticalTodoCompletion },
      ddi: { initiallyVisible: ddiTodoVisible, ...ddiTodoCompletion },
    },
    recommendationStats: stats.json?.data,
    steps: [
      "外部 LIS/HIS 事件由 API 注入，作为外部系统触发源。",
      "医生在待办、通知、推荐卡页面完成接收、已读、采纳/覆盖和待办闭环。",
      "MPI 与患者路径页面能定位患者、快照和在径节点，但推荐链路仍分散在多页。",
    ],
    findings: [
      {
        id: "OPT-IA-01",
        title: "推荐入口仍叫智能建议治理，待办/通知/路径/推荐卡分散",
        decision: "体验重构继续升级为提醒与推荐中枢。",
      },
      {
        id: "OPT-TRACE-01",
        title: "推荐链路需要单页总览图",
        decision: "保留一张图追溯需求，从推荐卡和审计页可跳入。",
      },
      {
        id: "OPT-WORKFLOW-01",
        title: "新危急值推荐卡未稳定聚合到医生待办第一页",
        decision: "待办中心需要患者/trace/来源对象检索，并修复推荐卡与待办的闭环状态同步。",
      },
    ],
    screenshots,
  };
}

async function replayAct7(browser, credentials) {
  const screenshots = [];
  const doctor = await login(browser, credentials, actors.respiratoryDoctor);
  await gotoAndWait(doctor.page, "/clinical/followup", "智能随访工作台");
  await doctor.page.getByPlaceholder("按患者 ID 检索").fill(drillPatient.mpiId);
  await doctor.page.getByRole("button", { name: /查\s*询/ }).click();
  await waitForQuiet(doctor.page);
  screenshots.push(await capture(browser, doctor.page, actDirs.act7, "01-followup-existing-plans.png", "医生前台查看 CAP 患者随访计划和统计"));

  await doctor.page.getByRole("button", { name: "生成随访计划" }).click();
  await doctor.page.getByLabel("随访快照患者 ID").fill(drillPatient.mpiId);
  await doctor.page.getByRole("button", { name: /^选择 / }).first().waitFor({ timeout: 25000 });
  await doctor.page.getByRole("button", { name: /^选择 / }).first().click();
  await chooseAntdSelect(doctor.page.locator(".ant-modal:visible").last(), "随访风险分层", "高风险", doctor.page);
  screenshots.push(await capture(browser, doctor.page, actDirs.act7, "02-followup-generate-form.png", "医生前台选择 ACTIVE 快照并准备生成随访计划"));
  await doctor.page
    .locator(".ant-modal:visible")
    .last()
    .getByRole("button", { name: /生\s*成/ })
    .click();
  await doctor.page
    .locator(".ant-message-notice-content")
    .filter({ hasText: "随访计划已生成" })
    .first()
    .waitFor({ timeout: 20000 })
    .catch(() => undefined);
  await waitForQuiet(doctor.page);
  screenshots.push(await capture(browser, doctor.page, actDirs.act7, "03-followup-plan-created.png", "医生前台生成或复用随访计划后列表刷新"));
  await doctor.context.close();

  const nurse = await login(browser, credentials, actors.respiratoryNurse);
  await gotoAndWait(nurse.page, "/clinical/followup", "智能随访工作台");
  await nurse.page.getByPlaceholder("按患者 ID 检索").fill(drillPatient.mpiId);
  await nurse.page.getByRole("button", { name: /查\s*询/ }).click();
  await nurse.page.getByText(drillPatient.mpiId, { exact: false }).first().waitFor({ timeout: 25000 });
  await nurse.page.getByRole("button", { name: "查看与办理" }).first().click();
  await nurse.page.getByText("随访计划办理", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(await capture(browser, nurse.page, actDirs.act7, "04-nurse-followup-drawer.png", "护士前台进入随访计划办理抽屉"));

  const fillButton = nurse.page.locator(".ant-drawer:visible").last().getByRole("button", { name: "填报" }).first();
  let questionnaire = { status: "no_pending_questionnaire" };
  if ((await fillButton.count()) > 0) {
    await fillButton.click();
    await nurse.page.getByLabel("问卷回收内容").fill("幕8.5前台复演：患者电话反馈轻度气促，已按随访 SOP 完成记录。");
    await nurse.page.getByRole("button", { name: "提交问卷" }).click();
    await nurse.page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "随访问卷内容已提交" })
      .first()
      .waitFor({ timeout: 20000 })
      .catch(() => undefined);
    questionnaire = { status: "submitted_from_ui" };
  }
  screenshots.push(await capture(browser, nurse.page, actDirs.act7, "05-nurse-questionnaire-result.png", "护士前台填报问卷或显示无待填问卷"));

  await chooseAntdSelect(nurse.page.locator(".ant-drawer:visible").last(), "严重性", "高风险", nurse.page);
  await nurse.page.getByLabel("异常表现").fill("幕8.5前台复演：患者轻度气促加重，建议门诊复评。");
  await nurse.page.getByLabel("处理建议").fill("联系主管医师复核，安排门诊复评并继续观察。");
  await nurse.page.getByRole("button", { name: "上报异常事件" }).click();
  await nurse.page
    .locator(".ant-message-notice-content")
    .filter({ hasText: "随访异常事件已上报" })
    .first()
    .waitFor({ timeout: 20000 })
    .catch(() => undefined);
  await waitForQuiet(nurse.page);
  screenshots.push(await capture(browser, nurse.page, actDirs.act7, "06-nurse-abnormal-reported.png", "护士前台上报随访异常事件并显示证据"));
  await nurse.context.close();

  const qa = await login(browser, credentials, actors.qaManager);
  await gotoAndWait(qa.page, "/qc/alerts", "质控预警");
  await qa.page.getByLabel("预警时间").first().click();
  await qa.page.locator(".ant-select-item-option").filter({ hasText: "全量" }).first().click();
  await waitForQuiet(qa.page);
  screenshots.push(await capture(browser, qa.page, actDirs.act7, "07-qc-alerts-list.png", "质控员前台查看真实预警"));
  const evidenceButton = qa.page.getByRole("button", { name: "查看处置证据" }).first();
  let alertAction = { status: "no_alert_visible" };
  if ((await evidenceButton.count()) > 0) {
    await evidenceButton.click();
    await qa.page.getByText("预警处置证据", { exact: false }).waitFor({ timeout: 25000 });
    screenshots.push(await capture(browser, qa.page, actDirs.act7, "08-qc-alert-evidence.png", "质控预警处置证据抽屉"));
    const acknowledge = qa.page.getByRole("button", { name: "确认预警" }).first();
    if ((await acknowledge.count()) > 0) {
      await acknowledge.click();
      await waitForQuiet(qa.page);
      alertAction = { status: "acknowledged_from_ui" };
    } else {
      alertAction = { status: "already_not_open_or_no_ack_button" };
    }
  }
  screenshots.push(await capture(browser, qa.page, actDirs.act7, "09-qc-alert-after-action.png", "质控预警确认动作后状态"));

  await gotoAndWait(qa.page, "/qc/dashboard", "院级质控驾驶舱");
  await qa.page.getByLabel("时间范围").first().click();
  await qa.page.locator(".ant-select-item-option").filter({ hasText: "全量" }).first().click();
  await waitForQuiet(qa.page);
  screenshots.push(await capture(browser, qa.page, actDirs.act7, "10-qc-dashboard-overview.png", "院级质控驾驶舱显示真实指标和打开预警"));
  await qa.page.getByRole("button", { name: "下钻问题证据" }).click();
  await qa.page.getByText("真实下钻证据", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(await capture(browser, qa.page, actDirs.act7, "11-qc-dashboard-drilldown.png", "质控驾驶舱真实下钻证据"));
  await qa.context.close();

  return {
    act: "幕7-随访与质控评估",
    actors: {
      respiratoryDoctor: publicActor(credentials[actors.respiratoryDoctor]),
      respiratoryNurse: publicActor(credentials[actors.respiratoryNurse]),
      qaManager: publicActor(credentials[actors.qaManager]),
    },
    patient: drillPatient,
    questionnaire,
    alertAction,
    steps: [
      "医生前台查看并生成随访计划。",
      "护士前台办理随访、填报问卷并上报异常事件。",
      "质控员前台查看预警、确认处置证据并下钻驾驶舱。",
    ],
    findings: [
      {
        id: "OPT-FOLLOWUP-01",
        title: "随访异常进入通知/待办的聚合仍不统一",
        decision: "体验重构继续把随访异常、待办和质控预警串到同一闭环视图。",
      },
    ],
    screenshots,
  };
}

async function replayAct8(browser, credentials) {
  const screenshots = [];
  const it = await login(browser, credentials, actors.itOps);
  await gotoAndWait(it.page, "/config/packages", "配置包中心");
  await it.page.getByPlaceholder("配置包名称或编码").fill("DRILL.ACT8.CONFIG");
  await it.page.getByRole("button", { name: /^查\s*询$/ }).click();
  await it.page.getByText("DRILL.ACT8.CONFIG", { exact: false }).first().waitFor({ timeout: 25000 });
  screenshots.push(await capture(browser, it.page, actDirs.act8, "01-config-package-ledger.png", "配置包台账：幕8配置包可前台检索"));

  const packageText = await bodyText(it.page);
  const exposesDualId =
    packageText.includes("e845b6cc-fbe7-4577-836f-6fed3bdae47d") &&
    packageText.includes("DRILL.ACT8.CONFIG");
  await it.page.getByText("DRILL.ACT8.CONFIG", { exact: false }).first().click().catch(() => undefined);
  await it.page.getByRole("button", { name: "发布配置包" }).click();
  await it.page.getByText("院内同步发布中心", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(await capture(browser, it.page, actDirs.act8, "02-config-package-release-modal.png", "发布配置包弹窗显示灰度/全量策略与真实适配器"));
  await it.page.locator(".ant-modal:visible").last().locator(".ant-modal-close").click();
  await waitForQuiet(it.page);

  await gotoAndWait(it.page, "/config/releases", "发布治理");
  screenshots.push(await capture(browser, it.page, actDirs.act8, "03-release-governance-simulation.png", "发布治理页：影响模拟与灰度入口"));
  await it.page.getByText("覆盖模板与批量复用", { exact: false }).click();
  await waitForQuiet(it.page);
  screenshots.push(await capture(browser, it.page, actDirs.act8, "04-release-governance-template.png", "发布治理页：覆盖模板与批量复用入口"));
  await it.context.close();

  return {
    act: "幕8-配置包与发布治理",
    actors: { itOps: publicActor(credentials[actors.itOps]) },
    package: {
      code: "DRILL.ACT8.CONFIG.ACT8-8SINB347C5",
      id: "e845b6cc-fbe7-4577-836f-6fed3bdae47d",
      exposesDualId,
    },
    steps: [
      "配置包中心可按业务编码检索幕8配置包。",
      "发布弹窗能看到灰度/全量策略与真实发布适配器。",
      "发布治理页提供影响模拟、灰度启动、观察窗和回退入口。",
    ],
    findings: [
      {
        id: "OPT-PKG-01",
        title: "普通台账仍同时暴露业务编码与统一版本资产/包 ID",
        decision: "继续把技术 ID 收进专家/调试视图，默认只保留业务信号与状态。",
      },
      {
        id: "UI-ACT8-REPLAY-01",
        title: "本次 L2 未重复执行真实全量/撤回",
        decision: "幕8 L1 已完成真实发布和撤回；L2 本轮只验证前台入口与可读性，避免重复扰动已撤回资产。",
      },
    ],
    screenshots,
  };
}

async function replayAct9(browser, credentials) {
  const screenshots = [];
  const it = await login(browser, credentials, actors.itOps);
  await gotoAndWait(it.page, "/adapter/hub", "适配器中心");
  screenshots.push(await capture(browser, it.page, actDirs.act9, "01-adapter-hub-overview.png", "适配器中心总览：连接率、未连接、字段映射覆盖"));

  const healthButton = it.page.getByRole("button", { name: "健康诊断" }).first();
  let healthAction = { status: "no_adapter_button_visible" };
  if ((await healthButton.count()) > 0) {
    await healthButton.click();
    await it.page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "健康检查完成" })
      .first()
      .waitFor({ timeout: 20000 })
      .catch(() => undefined);
    healthAction = { status: "health_check_clicked" };
  }
  screenshots.push(await capture(browser, it.page, actDirs.act9, "02-adapter-health-diagnosis.png", "适配器健康诊断返回真实状态"));

  await it.page.getByText("死信重放", { exact: false }).click();
  await waitForQuiet(it.page);
  screenshots.push(await capture(browser, it.page, actDirs.act9, "03-adapter-dead-letter.png", "适配器死信重放页展示失败、重试或死信状态"));

  await it.page.getByText("数据质量看板", { exact: false }).click();
  await waitForQuiet(it.page);
  screenshots.push(await capture(browser, it.page, actDirs.act9, "04-adapter-data-quality.png", "数据质量看板显示必填率、映射率和时效率入口"));

  await it.page.getByText("接入向导", { exact: false }).click();
  await waitForQuiet(it.page);
  screenshots.push(await capture(browser, it.page, actDirs.act9, "05-adapter-onboarding.png", "接入向导与必接系统状态可读"));
  await it.page.getByText("区域来源", { exact: false }).click();
  await waitForQuiet(it.page);
  screenshots.push(await capture(browser, it.page, actDirs.act9, "06-adapter-regional-source.png", "区域来源入口和数据接入契约提示"));
  const pageText = await bodyText(it.page);
  await it.context.close();

  return {
    act: "幕9-第三方对接能力案例集",
    actors: { itOps: publicActor(credentials[actors.itOps]) },
    healthAction,
    readability: {
      mentionsHealthy: pageText.includes("HEALTHY") || pageText.includes("真实连接正常"),
      mentionsNotConnected: pageText.includes("NOT_CONNECTED") || pageText.includes("未连接"),
      mentionsMisconfigured: pageText.includes("MISCONFIGURED") || pageText.includes("配置非法"),
      mentionsDeadLetter: pageText.includes("DEAD_LETTER") || pageText.includes("死信"),
    },
    steps: [
      "适配器中心前台查看系统与适配器、健康状态和运行状态。",
      "前台触发一次健康诊断，页面只展示后端真实状态。",
      "消息日志、接入申请和数据契约页签补齐幕9 L2 页面证据。",
    ],
    findings: [
      {
        id: "UI-ACT9-ADAPTER-01",
        title: "适配器健康状态页可读，但六案例与状态之间仍缺演示视角映射",
        decision: "第三方案例集继续保留 C1-C6；后续体验重构可增加案例视图分组。",
      },
    ],
    screenshots,
  };
}

async function writeActSummary(actKey, summary, generatedAt = nowIso()) {
  await writeFile(
    path.join(actDirs[actKey], "00-ui-replay-summary.json"),
    `${JSON.stringify({ generatedAt, ...summary }, null, 2)}\n`,
  );
}

async function readActSummary(actKey) {
  const raw = await readFile(path.join(actDirs[actKey], "00-ui-replay-summary.json"), "utf8");
  return JSON.parse(raw);
}

async function main() {
  await ensureDirs();
  const credentials = loadCredentials();
  const browser = await chromium.launch({ headless: true });
  const runTag = `act85-${shortId()}`;
  const startAct = Number.parseInt(process.env.DRILL_START_ACT ?? "6", 10);
  const summaries = {};
  try {
    if (startAct <= 6) {
      const injection = await injectAct6Events(browser, credentials, runTag);
      summaries.act6 = await replayAct6(browser, credentials, injection);
      await writeActSummary("act6", summaries.act6);
    } else {
      summaries.act6 = await readActSummary("act6").catch(() => null);
    }
    if (startAct <= 7) {
      summaries.act7 = await replayAct7(browser, credentials);
      await writeActSummary("act7", summaries.act7);
    } else {
      summaries.act7 = await readActSummary("act7").catch(() => null);
    }
    if (startAct <= 8) {
      summaries.act8 = await replayAct8(browser, credentials);
      await writeActSummary("act8", summaries.act8);
    } else {
      summaries.act8 = await readActSummary("act8").catch(() => null);
    }
    if (startAct <= 9) {
      summaries.act9 = await replayAct9(browser, credentials);
      await writeActSummary("act9", summaries.act9);
    } else {
      summaries.act9 = await readActSummary("act9").catch(() => null);
    }
    const generatedAt = nowIso();
    console.log(
      JSON.stringify(
        {
          ok: true,
          generatedAt,
          runTag,
          startAct,
          screenshots: Object.fromEntries(
            Object.entries(summaries)
              .filter(([, value]) => value)
              .map(([key, value]) => [key, value.screenshots.length]),
          ),
          findings: Object.fromEntries(
            Object.entries(summaries)
              .filter(([, value]) => value)
              .map(([key, value]) => [key, value.findings.map((item) => item.id)]),
          ),
        },
        null,
        2,
      ),
    );
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
