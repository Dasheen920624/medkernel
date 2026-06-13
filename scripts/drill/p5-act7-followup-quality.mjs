#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕7 随访质控。
// 剧本动作：
//   1. [readiness] 采集临床、护理、质控、机构管理员权限画像，并验证护理角色不能创建质控指标。
//   2. [seed]      集成运维员铺底含诊断受控事实的 ACTIVE 标准上下文快照。
//   3. [followup]  临床决策使用者生成随访计划，护理协同人员提交问卷、上报异常回院并回流结果。
//   4. [quality]   质量治理员创建/发布/灰度质控指标，机构管理员全量激活，质量治理员运行评估；
//                  护理/科室侧提交整改，质量治理员复核关闭。
//   5. [verify]    服务端回查随访计划、问卷、异常事件、回流快照、质控问题、整改任务和驾驶舱预警。
// 成功判定一律以服务端事实为准；截图只是前台佐证。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕7-随访质控/
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕7-随访质控",
);
const phase = (process.env.DRILL_PHASE ?? "all").split(",").map((s) => s.trim());
const runTag = process.env.DRILL_RUN_TAG ?? `p5-act7-${Date.now()}`;

const PACKAGE_VERSION = "2026.06.1";
const PATIENT_ID = process.env.DRILL_PATIENT_ID ?? "P5-ACT7-FOLLOWUP-001";
const ENCOUNTER_ID = process.env.DRILL_ENCOUNTER_ID ?? "P5-ACT7-ENC-001";
const CONDITION_CODE = "P5.ACT7.CAP.FOLLOWUP";
const INDICATOR_CODE = "P5.ACT7.FOLLOWUP.QUALITY";

const failures = [];
const steps = [];
const traceEntries = [];

function runPhase(name) {
  return phase.includes("all") || phase.includes(name);
}

function traceId(stage) {
  return `${runTag}-${stage}-${Date.now()}`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function dataOf(response) {
  return response.body?.data ?? response.body;
}

function pageItems(response) {
  return dataOf(response)?.items ?? [];
}

function ruleDefinition(fact, operator, value) {
  return JSON.stringify({ all: [{ fact, operator, value }] });
}

function isoNow(offsetMinutes = 0) {
  return new Date(Date.now() + offsetMinutes * 60_000).toISOString();
}

function shortJson(value, limit = 700) {
  return JSON.stringify(value).slice(0, limit);
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
    /* 部分页面有轮询请求，进入骨架消失判断 */
  }
  try {
    await page.getByText(/正在加载/).first().waitFor({ state: "hidden", timeout: 10000 });
  } catch {
    /* 无加载态或已消失 */
  }
  await page.waitForTimeout(700);
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
    parsed = { raw: text.slice(0, 600) };
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
    parsed = { raw: text.slice(0, 900) };
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

async function gotoPath(page, route) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForQuiet(page);
}

async function profile(context, label) {
  const res = await apiGet(context, "/security/me", `${label}-security-me`);
  const d = res.body?.data ?? {};
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

function preferredOrgUnit(scope) {
  return scope.departmentId ?? scope.hospitalId ?? scope.campusId ?? scope.siteId ?? scope.tenantId;
}

function buildSnapshotPayload(orgUnitId) {
  const eventTime = isoNow(-90);
  const receivedTime = isoNow(-80);
  return {
    request_id: `${runTag}-snapshot-${PATIENT_ID}`,
    trace_id: traceId("seed-snapshot-payload"),
    patientId: PATIENT_ID,
    encounterId: ENCOUNTER_ID,
    orgUnitId,
    package_version: PACKAGE_VERSION,
    resources: {
      patient: {
        mpi: PATIENT_ID,
        name: "幕7随访质控患者",
        birthDate: "1968-05-20",
        gender: "FEMALE",
        specialPopulations: [],
        sourceSystem: "HIS",
        sourceRecordId: `HIS-${PATIENT_ID}`,
        mappedVersion: PACKAGE_VERSION,
        eventTime,
        receivedTime,
        qualityStatus: "VALID",
      },
      encounters: [
        {
          encounterId: ENCOUNTER_ID,
          encounterType: "FOLLOWUP",
          admissionTime: isoNow(-240),
          dischargeTime: isoNow(-120),
          departmentId: orgUnitId,
          attendingDoctorId: "DR-ACT7-001",
          bedId: "BED-ACT7-001",
          sourceSystem: "HIS",
          sourceRecordId: `HIS-ENC-${ENCOUNTER_ID}`,
          mappedVersion: PACKAGE_VERSION,
          eventTime,
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
      conditions: [
        {
          conditionId: "COND-ACT7-CAP-001",
          code: CONDITION_CODE,
          codeSystem: "MEDKERNEL-DRILL",
          displayName: "幕7社区获得性肺炎出院随访质控演练",
          stage: "POST_DISCHARGE",
          severity: "HIGH",
          sourceSystem: "EMR",
          sourceRecordId: `EMR-COND-${PATIENT_ID}`,
          mappedVersion: PACKAGE_VERSION,
          onsetTime: isoNow(-240),
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
      observations: [
        {
          observationId: "OBS-ACT7-SPO2-001",
          code: "59408-5",
          displayName: "血氧饱和度",
          valueNumeric: 91,
          unit: "%",
          referenceRange: "95-100",
          criticalFlag: "LOW",
          sourceSystem: "FOLLOWUP",
          sourceRecordId: `FOLLOWUP-OBS-${PATIENT_ID}`,
          mappedVersion: PACKAGE_VERSION,
          eventTime,
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
    },
  };
}

async function findSnapshot(context) {
  const res = await apiGet(
    context,
    `/engine/context/snapshots?patientId=${encodeURIComponent(PATIENT_ID)}&status=ACTIVE&page=1&size=5`,
    "find-act7-snapshot",
  );
  return pageItems(res)[0]?.snapshotId ?? null;
}

async function findPlan(context) {
  const res = await apiGet(
    context,
    `/engine/followup/plans?patientId=${encodeURIComponent(PATIENT_ID)}&page=1&size=20`,
    "find-act7-followup-plan",
  );
  return pageItems(res)[0] ?? null;
}

async function findIndicator(context) {
  const res = await apiGet(
    context,
    `/engine/evaluation/indicators?indicatorCode=${encodeURIComponent(INDICATOR_CODE)}&page=1&size=20`,
    "find-act7-indicator",
  );
  const items = pageItems(res);
  return items.find((i) => i.status === "ACTIVE")
    ?? items.find((i) => i.status === "GRAY")
    ?? items.find((i) => i.status === "PUBLISHED")
    ?? items.find((i) => i.status === "PENDING_REVIEW")
    ?? items.find((i) => i.status === "DRAFT")
    ?? null;
}

async function ensureIndicatorLifecycle(qaCtx, adminCtx, responsibleDepartmentId) {
  let indicator = await findIndicator(qaCtx);
  if (!indicator) {
    const createRes = await apiPost(
      qaCtx,
      "/engine/evaluation/indicators",
      {
        indicatorCode: INDICATOR_CODE,
        versionNo: 1,
        name: "幕7随访异常回院整改闭环率",
        subjectType: "PATIENT",
        denominatorDefinition: ruleDefinition("patient.mpi", "equals", PATIENT_ID),
        numeratorDefinition: ruleDefinition("followUps[].abnormalFlag", "equals", "Y"),
        scoringDefinition: "P1高风险；随访异常回院后必须形成整改复核闭环",
        timeWindow: "FOLLOWUP+7D",
        organizationScope: "P5第二轮演练机构",
        responsibleDepartmentId,
        sourceRef: "docs/release/evidence/p5-second-fresh-drill-20260612/幕7-随访质控",
        packageVersion: PACKAGE_VERSION,
      },
      "quality-create-indicator",
    );
    if (!createRes.ok) {
      throw new Error(`创建质控指标失败 ${createRes.status}: ${shortJson(createRes.body)}`);
    }
    indicator = dataOf(createRes);
    steps.push({ step: "quality-create-indicator", indicatorId: indicator.indicatorId, status: indicator.status });
  }

  if (indicator.status === "DRAFT") {
    const res = await apiPost(qaCtx, `/engine/evaluation/indicators/${indicator.indicatorId}/submit`, {}, "quality-submit-indicator");
    if (!res.ok) throw new Error(`提交质控指标失败 ${res.status}: ${shortJson(res.body)}`);
    indicator = dataOf(res);
  }
  if (indicator.status === "PENDING_REVIEW") {
    const res = await apiPost(
      qaCtx,
      `/engine/evaluation/indicators/${indicator.indicatorId}/publish`,
      { reason: "幕7随访质控演练：质控办复核指标口径后发布" },
      "quality-publish-indicator",
    );
    if (!res.ok) throw new Error(`发布质控指标失败 ${res.status}: ${shortJson(res.body)}`);
    indicator = dataOf(res);
  }
  if (indicator.status === "PUBLISHED") {
    const res = await apiPost(
      qaCtx,
      `/engine/evaluation/indicators/${indicator.indicatorId}/gray`,
      { reason: "幕7随访质控演练：进入默认10%灰度验证" },
      "quality-gray-indicator",
    );
    if (!res.ok) throw new Error(`灰度质控指标失败 ${res.status}: ${shortJson(res.body)}`);
    indicator = dataOf(res);
  }
  if (indicator.status === "GRAY") {
    const res = await apiPost(
      adminCtx,
      `/engine/evaluation/indicators/${indicator.indicatorId}/activate`,
      { reason: "幕7随访质控演练：机构管理员确认院级全量激活" },
      "quality-activate-indicator",
    );
    if (!res.ok) throw new Error(`激活质控指标失败 ${res.status}: ${shortJson(res.body)}`);
    indicator = dataOf(res);
  }
  if (indicator.status !== "ACTIVE") {
    throw new Error(`质控指标未达到 ACTIVE，当前 status=${indicator.status}`);
  }
  steps.push({ step: "quality-indicator-active", indicatorId: indicator.indicatorId, status: indicator.status });
  return indicator;
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const credentials = await loadCredentials();
  const browser = await chromium.launch({ headless: true, args: ["--ignore-certificate-errors"] });

  let snapshotId = null;
  let planId = null;
  let questionnaireTaskId = null;
  let questionnaireId = null;
  let abnormalEventId = null;
  let returnTaskId = null;
  let notificationEventId = null;
  let backflowSnapshotId = null;
  let indicatorId = null;
  let runId = null;
  let findingId = null;
  let rectificationTaskId = null;
  let responsibleDepartmentId = null;

  try {
    if (runPhase("readiness")) {
      console.log("\n=== [readiness] 角色画像与越权边界 ===");
      const roles = {
        "clinical-decision-user": requireAccount(credentials, "clinical-decision-user"),
        "nursing-collaborator": requireAccount(credentials, "nursing-collaborator"),
        "clinical-governor": requireAccount(credentials, "clinical-governor"),
        "quality-governor": requireAccount(credentials, "quality-governor"),
        "organization-admin": requireAccount(credentials, "organization-admin"),
      };
      const profiles = {};
      for (const [role, account] of Object.entries(roles)) {
        const { context, page } = await login(browser, account, role);
        profiles[role] = await profile(context, role);
        if (role === "clinical-decision-user") {
          await gotoPath(page, "/clinical/followup");
          await capture(browser, page, "01-clinical-followup-entry.png", "临床角色随访协同入口");
        }
        if (role === "quality-governor") {
          await gotoPath(page, "/qc/dashboard");
          await capture(browser, page, "02-quality-dashboard-entry.png", "质量治理员质控驾驶舱入口");
        }
        await context.close();
      }

      const nurseAccount = roles["nursing-collaborator"];
      const { context: nurseCtx } = await login(browser, nurseAccount, "nursing-collaborator-boundary");
      const nurseProbe = await apiPost(
        nurseCtx,
        "/engine/evaluation/indicators",
        {
          indicatorCode: `DENY.${runTag}`,
          versionNo: 1,
          name: "护士越权创建质控指标探测",
          subjectType: "PATIENT",
          denominatorDefinition: ruleDefinition("patient.mpi", "equals", PATIENT_ID),
          numeratorDefinition: ruleDefinition("patient.mpi", "equals", PATIENT_ID),
          timeWindow: "ACT7",
          organizationScope: "P5第二轮演练机构",
          responsibleDepartmentId: preferredOrgUnit(profiles["nursing-collaborator"].dataScope) ?? "unknown",
          sourceRef: "p5-act7-permission-probe",
          packageVersion: PACKAGE_VERSION,
        },
        "nurse-denied-evaluation-create",
      );
      await nurseCtx.close();
      if (nurseProbe.status !== 403) {
        failures.push({ phase: "readiness", error: "护理角色创建质控指标未被403拦截", probe: nurseProbe });
      }
      responsibleDepartmentId =
        preferredOrgUnit(profiles["nursing-collaborator"].dataScope)
        ?? preferredOrgUnit(profiles["clinical-decision-user"].dataScope)
        ?? preferredOrgUnit(profiles["quality-governor"].dataScope);
      steps.push({
        step: "readiness",
        profiles,
        nurseBoundaryStatus: nurseProbe.status,
        responsibleDepartmentId,
      });
      await writeFile(path.join(evidenceDir, "00-readiness-actors-followup-quality.json"),
        JSON.stringify({ runTag, generatedAt: new Date().toISOString(), profiles, nurseBoundaryProbe: nurseProbe }, null, 2));
    }

    if (runPhase("seed")) {
      console.log("\n=== [seed] 铺底随访诊断受控事实快照 ===");
      const account = requireAccount(credentials, "integration-operator");
      const { context } = await login(browser, account, "integration-operator");
      const me = await profile(context, "seed-integration");
      const orgUnitId = preferredOrgUnit(me.dataScope);
      responsibleDepartmentId = responsibleDepartmentId ?? orgUnitId;
      snapshotId = await findSnapshot(context);
      if (snapshotId) {
        console.log(`  → 复用 ACTIVE 快照 ${snapshotId}`);
        steps.push({ step: "seed", snapshotId, orgUnitId, reused: true });
      } else {
        const res = await apiPost(context, "/engine/context/snapshots", buildSnapshotPayload(orgUnitId), "seed-context-snapshot");
        if (res.ok) {
          snapshotId = dataOf(res)?.snapshotId ?? await findSnapshot(context);
        }
        if (!res.ok || !snapshotId) {
          failures.push({ phase: "seed", status: res.status, body: res.body });
        } else {
          steps.push({ step: "seed", snapshotId, orgUnitId, status: dataOf(res)?.status ?? "ACTIVE" });
        }
      }
      await context.close();
    }

    if (runPhase("followup")) {
      console.log("\n=== [followup] 随访计划、问卷、异常回院与结果回流 ===");
      const clinicAccount = requireAccount(credentials, "clinical-decision-user");
      const nurseAccount = requireAccount(credentials, "nursing-collaborator");
      const { context: clinicCtx, page: clinicPage } = await login(browser, clinicAccount, "clinical-decision-user");
      const { context: nurseCtx, page: nursePage } = await login(browser, nurseAccount, "nursing-collaborator");

      snapshotId = snapshotId ?? await findSnapshot(clinicCtx);
      if (!snapshotId) {
        failures.push({ phase: "followup", error: "无 ACTIVE 快照，请先运行 seed 阶段" });
      } else {
        const existingPlan = await findPlan(clinicCtx);
        let planDetail = existingPlan;
        if (!planDetail) {
          const generateRes = await apiPost(
            clinicCtx,
            "/engine/followup/plans/generate",
            {
              contextSnapshotId: snapshotId,
              riskLevel: "HIGH",
              taskTypes: ["QUESTIONNAIRE"],
              idempotencyKey: `p5-act7-plan-${PATIENT_ID}`,
              modelEnabled: true,
            },
            "followup-generate-plan",
          );
          if (!generateRes.ok) {
            failures.push({ phase: "followup-generate", status: generateRes.status, body: generateRes.body });
          } else {
            planDetail = dataOf(generateRes);
          }
        }

        if (planDetail?.planId) {
          planId = planDetail.planId;
          const tasks = planDetail.tasks ?? [];
          const questionnaireTask =
            tasks.find((task) => task.taskType === "QUESTIONNAIRE")
            ?? tasks.find((task) => task.questionnaireTemplateId);
          questionnaireTaskId = questionnaireTask?.taskId;
          const questionnaireTemplateId =
            questionnaireTask?.questionnaireTemplateId ?? "FOLLOWUP_QUESTIONNAIRE_DEFAULT";
          steps.push({ step: "followup-plan", planId, questionnaireTaskId, reused: Boolean(existingPlan) });

          await gotoPath(clinicPage, "/clinical/followup");
          await capture(browser, clinicPage, "03-followup-plan-visible.png", "临床角色随访计划列表");

          if (!questionnaireTaskId) {
            failures.push({ phase: "followup-questionnaire", error: "未找到问卷任务", planDetail });
          } else {
            const questionnaireRes = await apiPost(
              nurseCtx,
              "/engine/followup/questionnaires",
              {
                taskId: questionnaireTaskId,
                questionnaireTemplateId,
                formData: JSON.stringify({
                  templateId: questionnaireTemplateId,
                  taskId: questionnaireTaskId,
                  title: "幕7出院后7天症状随访问卷",
                }),
                answerData: JSON.stringify({
                  cough: "仍有咳嗽",
                  dyspnea: "活动后气促",
                  temperature: 37.8,
                  submittedAt: new Date().toISOString(),
                }),
                score: 72,
                idempotencyKey: `p5-act7-questionnaire-${PATIENT_ID}`,
                executorType: "NURSE",
              },
              "followup-submit-questionnaire",
            );
            if (!questionnaireRes.ok) {
              failures.push({ phase: "followup-questionnaire", status: questionnaireRes.status, body: questionnaireRes.body });
            } else {
              questionnaireId = dataOf(questionnaireRes)?.questionnaireId;
            }

            const abnormalRes = await apiPost(
              nurseCtx,
              "/engine/followup/abnormal-reports",
              {
                planId,
                eventType: "ABNORMAL_RETURN",
                payload: JSON.stringify({
                  severity: "P1",
                  symptoms: ["活动后气促", "血氧偏低"],
                  remark: "幕7演练：随访异常回院，需纳入质控整改复核。",
                  reportedAt: new Date().toISOString(),
                }),
                triggeredBy: "nursing-collaborator",
                idempotencyKey: `p5-act7-abnormal-${PATIENT_ID}`,
              },
              "followup-report-abnormal",
            );
            if (!abnormalRes.ok) {
              failures.push({ phase: "followup-abnormal", status: abnormalRes.status, body: abnormalRes.body });
            } else {
              abnormalEventId = dataOf(abnormalRes)?.eventId ?? dataOf(abnormalRes)?.abnormalEventId;
              returnTaskId = dataOf(abnormalRes)?.returnTaskId;
              notificationEventId = dataOf(abnormalRes)?.notificationEventId;
            }

            if (questionnaireId) {
              const backflowRes = await apiPost(
                nurseCtx,
                "/engine/followup/results",
                {
                  planId,
                  taskId: questionnaireTaskId,
                  questionnaireId,
                  resultPayload: JSON.stringify({
                    abnormalFlag: "Y",
                    symptoms: ["活动后气促", "血氧偏低"],
                    source: "P5_ACT7_FOLLOWUP",
                    abnormalEventId,
                  }),
                  abnormalFlag: "Y",
                  packageVersion: PACKAGE_VERSION,
                  idempotencyKey: `p5-act7-backflow-${PATIENT_ID}`,
                },
                "followup-result-backflow",
              );
              if (!backflowRes.ok) {
                failures.push({ phase: "followup-backflow", status: backflowRes.status, body: backflowRes.body });
              } else {
                backflowSnapshotId = dataOf(backflowRes)?.contextSnapshotId;
              }
            }
          }

          await gotoPath(nursePage, "/clinical/followup");
          await capture(browser, nursePage, "04-followup-abnormal-evidence.png", "护理角色随访异常证据");
          await writeFile(path.join(evidenceDir, "01-followup-plan-questionnaire-abnormal.json"), JSON.stringify({
            runTag,
            generatedAt: new Date().toISOString(),
            patientId: PATIENT_ID,
            encounterId: ENCOUNTER_ID,
            snapshotId,
            planId,
            questionnaireTaskId,
            questionnaireId,
            abnormalEventId,
            returnTaskId,
            notificationEventId,
            backflowSnapshotId,
          }, null, 2));
        }
      }

      await clinicCtx.close();
      await nurseCtx.close();
    }

    if (runPhase("quality")) {
      console.log("\n=== [quality] 质控指标、评估运行、整改闭环 ===");
      const qaAccount = requireAccount(credentials, "quality-governor");
      const adminAccount = requireAccount(credentials, "organization-admin");
      const clinicalGovernorAccount = requireAccount(credentials, "clinical-governor");
      const { context: qaCtx, page: qaPage } = await login(browser, qaAccount, "quality-governor");
      const { context: adminCtx } = await login(browser, adminAccount, "organization-admin");
      const { context: clinicalGovernorCtx } = await login(
        browser,
        clinicalGovernorAccount,
        "clinical-governor-remediate",
      );

      const remediationProfile = await profile(clinicalGovernorCtx, "quality-remediation-owner");
      responsibleDepartmentId = responsibleDepartmentId
        ?? preferredOrgUnit(remediationProfile.dataScope)
        ?? "P5-ACT7-DEPT";
      const indicator = await ensureIndicatorLifecycle(qaCtx, adminCtx, responsibleDepartmentId);
      indicatorId = indicator.indicatorId;
      snapshotId = snapshotId ?? await findSnapshot(qaCtx);
      if (!planId) {
        const plan = await findPlan(qaCtx);
        planId = plan?.planId;
      }
      const runCode = `P5.ACT7.RUN.${runTag}`;
      const findingCode = `P5.ACT7.FOLLOWUP.ABNORMAL.${runTag}`;
      const runRes = await apiPost(
        qaCtx,
        "/engine/evaluation/runs",
        {
          runCode,
          runType: "UPSTREAM_RESULT",
          sourceEventId: abnormalEventId ?? `followup-abnormal-${PATIENT_ID}`,
          contextSnapshotId: backflowSnapshotId ?? snapshotId,
          patientId: PATIENT_ID,
          encounterId: ENCOUNTER_ID,
          scenarioCode: "FOLLOWUP_ABNORMAL_RETURN",
          packageVersion: PACKAGE_VERSION,
          inputDigest: `sha256:${runTag}`,
          occurredAt: new Date().toISOString(),
          results: [
            {
              indicatorId,
              subjectType: "PATIENT",
              subjectRefId: PATIENT_ID,
              scoreValue: 65,
              resultLevel: "NON_COMPLIANT",
              hitFlag: true,
              evidenceSummary: `随访计划 ${planId} 已上报异常回院 ${abnormalEventId ?? "已记录"}，需要责任科室整改复核。`,
              sourceRef: "P5_ACT7_FOLLOWUP_ABNORMAL",
              responsibleDepartmentId,
              findings: [
                {
                  findingCode,
                  title: "随访异常回院后复评未闭环",
                  description: "患者随访问卷提示活动后气促和血氧偏低，异常回院事件已生成，需要责任科室补充复评和后续处置证据。",
                  severity: "P1",
                  evidenceSummary: `followup_plan=${planId}; abnormal_event=${abnormalEventId}; backflow_snapshot=${backflowSnapshotId}`,
                  responsibleDepartmentId,
                  dueAt: isoNow(7 * 24 * 60),
                  assigneeUserId: remediationProfile.userId,
                },
              ],
            },
          ],
        },
        "quality-run-evaluation",
      );
      if (!runRes.ok) {
        failures.push({ phase: "quality-run", status: runRes.status, body: runRes.body });
      } else {
        runId = dataOf(runRes)?.runId;
      }

      const issueRes = await apiGet(
        qaCtx,
        `/engine/evaluation/issues?status=ASSIGNED&responsibleDepartmentId=${encodeURIComponent(responsibleDepartmentId)}&page=1&size=50`,
        "quality-find-assigned-issue",
      );
      const issue = pageItems(issueRes).find((item) => item.findingCode === findingCode) ?? pageItems(issueRes)[0];
      findingId = issue?.findingId;
      if (!findingId) {
        failures.push({ phase: "quality-issue", error: "评估运行后未找到 ASSIGNED 质控问题", body: issueRes.body });
      } else {
        const detailRes = await apiGet(qaCtx, `/engine/evaluation/issues/${findingId}`, "quality-issue-detail");
        rectificationTaskId = dataOf(detailRes)?.rectificationTask?.taskId;
        if (!rectificationTaskId) {
          failures.push({ phase: "quality-rectification", error: "质控问题未生成整改任务", detail: detailRes.body });
        } else {
          const submitRes = await apiPost(
            clinicalGovernorCtx,
            `/engine/rectifications/${rectificationTaskId}/submit`,
            {
              rectificationSummary: "已电话复评患者症状，补录随访复评记录并通知门诊复诊；未自动开立医嘱。",
              evidenceRef: `P5-ACT7:${planId}:${abnormalEventId ?? "abnormal"}`,
            },
            "quality-submit-rectification",
            { "Idempotency-Key": `p5-act7-rect-submit-${runTag}` },
          );
          if (!submitRes.ok) {
            failures.push({ phase: "quality-submit-rectification", status: submitRes.status, body: submitRes.body });
          }
          const reviewRes = await apiPost(
            qaCtx,
            `/engine/rectifications/${rectificationTaskId}/review`,
            {
              decision: "APPROVED",
              comment: "质控办复核：随访异常已完成责任科室复评、证据引用完整，同意关闭。",
              evidenceRef: `P5-ACT7-REVIEW:${runId}:${findingId}`,
            },
            "quality-review-rectification",
            { "Idempotency-Key": `p5-act7-rect-review-${runTag}` },
          );
          if (!reviewRes.ok) {
            failures.push({ phase: "quality-review-rectification", status: reviewRes.status, body: reviewRes.body });
          }
        }
      }

      const dashboardBefore = await apiGet(
        qaCtx,
        `/engine/quality/dashboard?departmentId=${encodeURIComponent(responsibleDepartmentId)}`,
        "quality-dashboard-after-review",
      );
      const alertsRes = await apiGet(
        qaCtx,
        `/engine/quality/alerts?departmentId=${encodeURIComponent(responsibleDepartmentId)}&page=1&size=20`,
        "quality-alerts-after-review",
      );
      const alert = pageItems(alertsRes).find((item) => item.sourceId === findingId) ?? pageItems(alertsRes)[0];
      if (alert?.alertId && alert.status === "OPEN") {
        await apiPost(qaCtx, `/engine/quality/alerts/${encodeURIComponent(alert.alertId)}/acknowledge`, {}, "quality-ack-alert");
      }

      await gotoPath(qaPage, "/qc/alerts");
      await capture(browser, qaPage, "05-quality-alerts-after-rectification.png", "质控预警与整改闭环");
      await gotoPath(qaPage, "/qc/dashboard");
      await capture(browser, qaPage, "06-quality-dashboard-after-rectification.png", "质控驾驶舱闭环概览");

      await writeFile(path.join(evidenceDir, "02-quality-indicator-run-rectification.json"), JSON.stringify({
        runTag,
        generatedAt: new Date().toISOString(),
        patientId: PATIENT_ID,
        responsibleDepartmentId,
        indicatorId,
        runId,
        findingId,
        rectificationTaskId,
        dashboard: dashboardBefore.body,
        alerts: alertsRes.body,
      }, null, 2));

      await qaCtx.close();
      await adminCtx.close();
      await clinicalGovernorCtx.close();
    }

    if (runPhase("verify")) {
      console.log("\n=== [verify] 服务端事实回查 ===");
      const qaAccount = requireAccount(credentials, "quality-governor");
      const clinicAccount = requireAccount(credentials, "clinical-decision-user");
      const { context: qaCtx } = await login(browser, qaAccount, "quality-governor-verify");
      const { context: clinicCtx } = await login(browser, clinicAccount, "clinical-decision-user-verify");
      const followupPlans = await apiGet(
        clinicCtx,
        `/engine/followup/plans?patientId=${encodeURIComponent(PATIENT_ID)}&page=1&size=20`,
        "verify-followup-plans",
      );
      const followupStats = await apiGet(
        clinicCtx,
        `/engine/followup/stats?patientId=${encodeURIComponent(PATIENT_ID)}`,
        "verify-followup-stats",
      );
      const issues = await apiGet(qaCtx, "/engine/evaluation/issues?page=1&size=20", "verify-quality-issues");
      const report = await apiGet(qaCtx, "/engine/rectifications/report", "verify-rectification-report");
      const dashboard = await apiGet(qaCtx, "/engine/quality/dashboard", "verify-quality-dashboard");
      steps.push({
        step: "verify",
        followupPlanCount: dataOf(followupPlans)?.total ?? pageItems(followupPlans).length,
        followupStats: dataOf(followupStats),
        qualityIssueCount: dataOf(issues)?.total ?? pageItems(issues).length,
        rectificationReport: dataOf(report),
        dashboardSummary: dataOf(dashboard)?.summary,
      });
      await writeFile(path.join(evidenceDir, "03-act7-service-verification.json"), JSON.stringify({
        runTag,
        generatedAt: new Date().toISOString(),
        followupPlans: followupPlans.body,
        followupStats: followupStats.body,
        qualityIssues: issues.body,
        rectificationReport: report.body,
        dashboard: dashboard.body,
      }, null, 2));
      await qaCtx.close();
      await clinicCtx.close();
    }
  } catch (error) {
    failures.push({ phase: "fatal", error: error instanceof Error ? error.message : String(error) });
    console.error(error);
  } finally {
    await browser.close();
  }

  await writeFile(path.join(evidenceDir, "trace-ids.txt"),
    traceEntries.map((entry) => JSON.stringify(entry)).join("\n") + "\n");
  const summary = {
    runTag,
    generatedAt: new Date().toISOString(),
    commit: process.env.DRILL_COMMIT ?? "local",
    phases: phase,
    patientId: PATIENT_ID,
    encounterId: ENCOUNTER_ID,
    snapshotId,
    planId,
    questionnaireTaskId,
    questionnaireId,
    abnormalEventId,
    returnTaskId,
    notificationEventId,
    backflowSnapshotId,
    indicatorId,
    runId,
    findingId,
    rectificationTaskId,
    responsibleDepartmentId,
    failures,
    steps,
    result: failures.length === 0 ? "PASS" : "FAIL",
  };
  await writeFile(path.join(evidenceDir, "00-act7-summary.json"), JSON.stringify(summary, null, 2));
  console.log(`\n幕7结果：${summary.result}`);
  console.log(`证据目录：${evidenceDir}`);
  if (failures.length > 0) {
    console.error(JSON.stringify(failures, null, 2));
    process.exitCode = 1;
  }
}

await main();
