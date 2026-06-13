#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕5 路径治理（治理侧完整旅程，患者入径与节点推进留幕6）。
// 剧本动作：
//   1. 集成运维员以 API 模拟外部系统铺底 1 份急诊上下文 ACTIVE 快照（供路径试运行选用）。
//   2. 机构知识治理员真实前台 /pathway/templates 「管理路径知识包」建专用路径知识包草稿。
//   3. 机构知识治理员真实前台用「急诊处置路径」内置原型创建路径模板草稿
//      （ASSESS 急诊评估 → DISPOSITION 处置安排终止，含里程碑与默认边，发布门禁可过）。
//   4. 知识治理员选 ACTIVE 快照试运行，期望轨迹命中 ASSESS→DISPOSITION。
//   5. 知识治理员读发布影响摘要 → 灰度发布门禁通过（DRAFT→PUBLISHED，10% 灰度，保留回滚证据）。
//   6. org-admin 探针：真实前台 /pathway/templates 被菜单/路由拦截，而其持 pathway.publish 且
//      后端 requireReleaseCoordinator 放行 ORGANIZATION_ADMIN ——坐实 P5-ACT5-01 菜单缺口缺陷。
//   7. 协调角色（修复部署后 org-admin；缺陷未修时可用 clinical-governor）院级确认全量激活（→FULL）。
// 成功判定一律以服务端回查为准（/engine/pathway/pathway-templates[/{id}]、/engine/pkg/packages）。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕5-路径治理/
//   00-act5-summary.json 与 NN-ui-*.png（全部带 URL 栏）。
// 凭据：默认读取本机受控副本 /tmp/p5-14-role-drill-credentials-20260612.json（权威文件在服务器
//   /zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json，权限 600）；凭据不入仓库。
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕5-路径治理",
);
// 阶段闸门：seed|package|create|simulate|canary|probe|full|all（默认 all），便于断点续跑。
const phase = process.env.DRILL_PHASE ?? "all";
// 院级全量协调角色：修复部署后用 organization-admin 实证缺陷修复价值；缺陷未修时用 clinical-governor。
const fullRolloutRole = process.env.DRILL_FULL_ROLE ?? "organization-admin";

const PACKAGE_VERSION = "2026.06.1";
const PKG = {
  code: "PATH.P5.ED",
  name: "急诊处置路径知识包",
  diseaseCode: "ED",
  version: PACKAGE_VERSION,
  sourceRef: "院内已审核急诊处置制度2026版",
  description: "P5 幕5 演练专用：急诊评估到处置安排标准临床路径知识包。",
};
const TEMPLATE = {
  // 与前端「急诊处置路径」内置原型 applyPathwayPrototype 一致。
  code: "PATH.ED.DISPOSITION",
  name: "急诊处置路径",
  diseaseCode: "ED",
  startNode: "ASSESS",
  terminalNode: "DISPOSITION",
  prototypeTitle: "急诊处置路径",
};
// 供路径试运行选用的单份急诊 ACTIVE 上下文快照。
const SIM_SNAPSHOT = {
  key: "ED",
  patientId: "P5-ACT5-ED-001",
  encounterId: "P5-ACT5-ENC-ED-001",
  name: "急诊处置路径试运行患者",
  obsCode: "8310-5",
  obsName: "体温",
  valueNumeric: 38.6,
  unit: "Cel",
  referenceRange: "36-37.3",
  criticalFlag: null,
};

function traceId(stage) {
  return `p5-act5-${stage}-${Date.now()}`;
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
  // organization-admin 凭据存放在 customerTenant 块；其余职责角色在 roleAccounts。
  if (role === "organization-admin") {
    const tenant = credentials.customerTenant ?? {};
    return {
      username: tenant.adminUsername,
      password: tenant.password,
      tenantId: tenant.tenantId,
    };
  }
  const account = credentials.roleAccounts[role] ?? credentials.platformRoleAccounts[role];
  if (!account) {
    throw new Error(`缺少角色凭据：${role}`);
  }
  return account;
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

async function capture(browser, page, filename, label, dir = evidenceDir) {
  const finalPath = path.join(dir, filename);
  const rawPath = path.join(dir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  return { label, screenshot: filename, url: page.url() };
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function apiGet(context, pathName, stage) {
  const response = await context.request.get(`${apiBase}${pathName}`, {
    headers: { "X-Trace-Id": traceId(stage) },
  });
  const text = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 400) };
  }
  return { status: response.status(), ok: response.ok(), body: parsed };
}

// 浏览器 cookie 会话的写请求需带 CSRF 双提交令牌：cookie XSRF-TOKEN ↔ 头 X-XSRF-TOKEN。
async function csrfToken(context) {
  const cookies = await context.cookies(baseUrl);
  return cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? "";
}

async function apiPost(context, pathName, data, stage) {
  const token = await csrfToken(context);
  const response = await context.request.post(`${apiBase}${pathName}`, {
    data,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(stage),
      "X-XSRF-TOKEN": token,
    },
  });
  const text = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 600) };
  }
  return { status: response.status(), ok: response.ok(), body: parsed };
}

// 通过真实登录接口建立会话；账号均已在幕1 完成首登改密与 MFA 绑定，无需在此重走首登流程。
async function login(browser, account, roleLabel) {
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: {
      username: account.username,
      password: account.password,
      tenantId: account.tenantId,
    },
    headers: { "Content-Type": "application/json", "X-Trace-Id": traceId(`login-${roleLabel}`) },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(`${roleLabel} 登录失败：${response.status()} ${body.slice(0, 300)}`);
  }
  return { context, page };
}

// 选择 antd 下拉选项，并以表单项内出现选中标签为准确认成功（规避展开动画期间的点击竞态）。
async function chooseSelectOption(page, scope, controlId, optionText) {
  const formItem = scope.locator(`.ant-form-item:has(#${controlId})`);
  await formItem.locator(".ant-select-selector").click();
  await page.waitForTimeout(500);
  const visibleDropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)");
  const option = optionText
    ? visibleDropdown.locator(".ant-select-item-option-content", { hasText: optionText }).first()
    : visibleDropdown.locator(".ant-select-item-option-content").first();
  await option.click();
  await page.waitForTimeout(300);
  let selected = await formItem.locator(".ant-select-selection-item").count();
  if (selected === 0) {
    await page.keyboard.press("Enter");
    await page.waitForTimeout(300);
    selected = await formItem.locator(".ant-select-selection-item").count();
  }
  const openDropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)");
  if ((await openDropdown.count()) > 0 && (await openDropdown.first().isVisible())) {
    await page.keyboard.press("Escape");
  }
  await page.waitForTimeout(200);
  return selected > 0;
}

async function gotoPath(page, route) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForQuiet(page);
}

// ── 标准上下文快照（API 模拟外部系统铺底，供路径试运行选用）─────────────────────

function isoNow(offsetMinutes = 0) {
  return new Date(Date.now() + offsetMinutes * 60_000).toISOString();
}

function buildSnapshotPayload(snapshot, orgUnitId) {
  const eventTime = isoNow(-30);
  const receivedTime = isoNow(-20);
  return {
    request_id: `p5-act5-snap-${snapshot.key}`,
    trace_id: traceId(`snap-${snapshot.key}`),
    patientId: snapshot.patientId,
    encounterId: snapshot.encounterId,
    orgUnitId,
    package_version: PACKAGE_VERSION,
    resources: {
      patient: {
        mpi: snapshot.patientId,
        name: snapshot.name,
        birthDate: "1972-09-04",
        gender: "FEMALE",
        specialPopulations: [],
        sourceSystem: "P5-ACT5-MOCK-HIS",
        sourceRecordId: `pat-${snapshot.key}`,
        mappedVersion: PACKAGE_VERSION,
        eventTime,
        receivedTime,
        qualityStatus: "VALID",
      },
      encounters: [
        {
          encounterId: snapshot.encounterId,
          encounterType: "ED",
          admissionTime: isoNow(-180),
          dischargeTime: null,
          departmentId: "P5-DEPT-ED",
          attendingDoctorId: "P5-DOC-ED-001",
          bedId: "P5-ED-BAY-3",
          sourceSystem: "P5-ACT5-MOCK-HIS",
          sourceRecordId: `enc-${snapshot.key}`,
          mappedVersion: PACKAGE_VERSION,
          eventTime,
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
      observations: [
        {
          observationId: `obs-${snapshot.key}`,
          code: snapshot.obsCode,
          displayName: snapshot.obsName,
          valueNumeric: snapshot.valueNumeric,
          unit: snapshot.unit,
          referenceRange: snapshot.referenceRange,
          criticalFlag: snapshot.criticalFlag,
          sourceSystem: "P5-ACT5-MOCK-LIS",
          sourceRecordId: `lab-${snapshot.key}`,
          mappedVersion: PACKAGE_VERSION,
          eventTime,
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
    },
  };
}

async function findActiveSnapshot(context, patientId, stage) {
  const res = await apiGet(
    context,
    `/engine/context/snapshots?patientId=${encodeURIComponent(patientId)}&status=ACTIVE&page=1&size=5`,
    stage,
  );
  const items = res.body?.data?.items ?? [];
  return { status: res.status, snapshot: items[0] ?? null };
}

async function seedSnapshot(browser, credentials, summary) {
  const { context } = await login(
    browser,
    requireAccount(credentials, "integration-operator"),
    "integration-operator",
  );
  const observations = {};
  try {
    const me = await apiGet(context, "/security/me", "me");
    const scope = me.body?.data?.dataScope ?? {};
    const orgUnitId = scope.hospitalId || scope.campusId || scope.tenantId;
    observations.orgUnitId = orgUnitId;
    observations.tenantId = scope.tenantId;
    if (!orgUnitId) {
      summary.failures.push({ step: "集成运维员数据范围缺少可用组织", detail: { scope } });
      return;
    }
    const existing = await findActiveSnapshot(context, SIM_SNAPSHOT.patientId, "pre-ed");
    if (existing.snapshot) {
      observations.snapshotId = existing.snapshot.snapshotId;
      observations.reused = true;
    } else {
      const payload = buildSnapshotPayload(SIM_SNAPSHOT, orgUnitId);
      const created = await apiPost(context, "/engine/context/snapshots", payload, "snap-ed");
      if (created.status !== 201) {
        summary.failures.push({
          step: "铺底急诊快照失败",
          detail: { status: created.status, body: created.body },
        });
        return;
      }
      const after = await findActiveSnapshot(context, SIM_SNAPSHOT.patientId, "post-ed");
      if (!after.snapshot || after.snapshot.status !== "ACTIVE") {
        summary.failures.push({ step: "急诊快照未回查到 ACTIVE", detail: { after: after.snapshot } });
        return;
      }
      observations.snapshotId = after.snapshot.snapshotId;
      observations.quality = after.snapshot.qualityStatus;
      observations.reused = false;
    }
  } finally {
    summary.seedSnapshot = { observations };
    await context.close();
  }
}

// ── 路径模板/知识包服务端事实读回 ─────────────────────

async function findTemplate(context, stage) {
  const res = await apiGet(
    context,
    `/engine/pathway/pathway-templates?templateCode=${encodeURIComponent(TEMPLATE.code)}&page=1&size=50`,
    stage,
  );
  const items = res.body?.data?.items ?? [];
  // 取最新版本（templateVersion 最大）作为当前在治理的模板。
  const sorted = [...items].sort((a, b) => (b.templateVersion ?? 0) - (a.templateVersion ?? 0));
  return { status: res.status, template: sorted[0] ?? null };
}

async function templateDetail(context, templateId, stage) {
  const res = await apiGet(context, `/engine/pathway/pathway-templates/${templateId}`, stage);
  return { status: res.status, detail: res.body?.data };
}

async function findPathwayPackage(context, stage) {
  const res = await apiGet(context, "/engine/pkg/packages?assetType=PATHWAY&page=1&size=50", stage);
  const items = res.body?.data?.items ?? [];
  const pkg = items.find((item) => item.packageCode === PKG.code);
  return { status: res.status, pkg, count: items.length };
}

// ── 步骤二：机构知识治理员前台建专用路径知识包 ─────────────────────

async function buildPackage(browser, credentials, summary) {
  const steps = [];
  const observations = {};
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-pkg",
  );
  try {
    const existing = await findPathwayPackage(context, "pkg-pre");
    if (existing.pkg) {
      observations.reused = true;
      observations.packageId = existing.pkg.packageId;
      summary.buildPackage = { observations, steps };
      return;
    }
    await gotoPath(page, "/pathway/templates");
    steps.push(await capture(browser, page, "01-ui-pathway-list-before.png", "路径模板治理页（建包前，零数据）"));

    await page.getByRole("button", { name: "管理路径知识包" }).click();
    await page.waitForTimeout(800);
    const drawer = page.locator(".ant-drawer-content:has-text('新建路径知识包')");
    await drawer.locator("#packageCode").fill(PKG.code);
    await drawer.locator("#diseaseCode").fill(PKG.diseaseCode);
    await drawer.locator("#name").fill(PKG.name);
    await drawer.locator("#packageVersion").fill(PKG.version);
    await drawer.locator("#sourceRef").fill(PKG.sourceRef);
    await drawer.locator("#description").fill(PKG.description);
    steps.push(await capture(browser, page, "02-ui-pathway-package-form.png", "新建路径知识包草稿表单"));
    await drawer.getByRole("button", { name: "创建草稿" }).click();
    await page.waitForTimeout(2000);
    steps.push(await capture(browser, page, "03-ui-pathway-package-created.png", "路径知识包草稿创建成功"));

    const after = await findPathwayPackage(context, "pkg-post");
    observations.packageId = after.pkg?.packageId ?? null;
    observations.packageStatus = after.pkg?.status ?? null;
    observations.reused = false;
    if (!after.pkg) {
      summary.failures.push({ step: "建路径知识包后服务端未回查到", detail: { count: after.count } });
    }
  } catch (error) {
    summary.failures.push({ step: "建路径知识包异常", detail: String(error).slice(0, 400) });
    await capture(browser, page, "03e-ui-pathway-package-error.png", "建路径知识包异常现场").catch(() => undefined);
  } finally {
    summary.buildPackage = { observations, steps };
    await context.close();
  }
}

// ── 步骤三：机构知识治理员前台用「急诊处置路径」原型建模板草稿 ─────────────────────

async function createTemplate(browser, credentials, summary) {
  const steps = [];
  const observations = {};
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-create",
  );
  try {
    const before = await findTemplate(context, "tpl-pre");
    if (before.template) {
      observations.reused = true;
      observations.templateId = before.template.templateId;
      observations.status = before.template.status;
      summary.createTemplate = { observations, steps };
      return;
    }
    await gotoPath(page, "/pathway/templates");
    await page.getByRole("button", { name: "新建路径模板" }).click();
    await page.waitForTimeout(800);
    const modal = page.locator(".ant-modal-content:has-text('新建路径模板模型')");
    // 选择「急诊处置路径」内置原型（Radio.Group），自动播种 L1 基本信息 + L2 两节点骨架。
    await modal.locator(".ant-radio-wrapper", { hasText: TEMPLATE.prototypeTitle }).first().click();
    await page.waitForTimeout(1000);
    // 显式选定归属路径知识包，消除 items[0] 歧义。
    await chooseSelectOption(page, modal, "packageId", PKG.name).catch(() => undefined);
    steps.push(await capture(browser, page, "04-ui-pathway-create-form.png", "急诊处置路径原型创建表单（节点/边/里程碑已播种）"));
    await modal.locator(".ant-modal-footer button.ant-btn-primary").click();
    await page.waitForTimeout(2500);
    steps.push(await capture(browser, page, "05-ui-pathway-list-after-create.png", "创建草稿后的路径模板列表"));

    const after = await findTemplate(context, "tpl-post");
    observations.templateId = after.template?.templateId ?? null;
    observations.status = after.template?.status ?? null;
    observations.reused = false;
    if (!after.template) {
      summary.failures.push({ step: "建路径模板后服务端未回查到", detail: { status: after.status } });
    } else if (after.template.status !== "DRAFT") {
      summary.failures.push({ step: `路径模板初始状态 ${after.template.status} ≠ DRAFT`, detail: { template: after.template } });
    }
  } catch (error) {
    summary.failures.push({ step: "建路径模板异常", detail: String(error).slice(0, 400) });
    await capture(browser, page, "05e-ui-pathway-create-error.png", "建路径模板异常现场").catch(() => undefined);
  } finally {
    summary.createTemplate = { observations, steps };
    await context.close();
  }
}

// 打开某模板的「设计与试运行」详情抽屉。
async function openTemplateDrawer(page) {
  await gotoPath(page, "/pathway/templates");
  await page.getByRole("button", { name: "设计与试运行" }).first().click();
  const drawer = page.locator(".ant-drawer-content").first();
  // 抽屉详情走异步查询；等到 tabs 渲染出来再交互，避免点到加载态空白。
  await drawer.getByRole("tab").first().waitFor({ state: "visible", timeout: 30000 });
  await page.waitForTimeout(800);
  return drawer;
}

// ── 步骤四：知识治理员选 ACTIVE 快照试运行轨迹 ─────────────────────

async function simulateTrajectory(browser, credentials, summary) {
  const steps = [];
  const observations = {};
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-sim",
  );
  try {
    // 试运行面板按患者 ID 检索 ACTIVE 快照，按钮文案为 snapshotId，先服务端查得以精确点选。
    const active = await findActiveSnapshot(context, SIM_SNAPSHOT.patientId, "sim-snap");
    observations.snapshotId = active.snapshot?.snapshotId ?? null;
    if (!active.snapshot) {
      summary.failures.push({ step: "试运行前未回查到 ACTIVE 急诊快照", detail: {} });
      summary.simulate = { observations, steps };
      return;
    }
    const drawer = await openTemplateDrawer(page);
    await drawer.getByRole("tab", { name: /真实快照试运行/ }).click();
    await page.waitForTimeout(1000);
    await drawer.locator("#pathway-snapshot-patient-id").fill(SIM_SNAPSHOT.patientId);
    await drawer.getByRole("button", { name: "读取真实快照" }).click();
    await page.waitForTimeout(1500);
    await drawer.locator("button", { hasText: active.snapshot.snapshotId }).first().click();
    await page.waitForTimeout(600);
    const runBtn = drawer.getByRole("button", { name: "使用该快照试运行" }).first();
    for (let attempt = 0; attempt < 20; attempt += 1) {
      if (await runBtn.isEnabled().catch(() => false)) break;
      await page.waitForTimeout(500);
    }
    await runBtn.click();
    await page.waitForTimeout(2500);
    steps.push(await capture(browser, page, "06-ui-pathway-simulate-trajectory.png", "ACTIVE 快照试运行轨迹（ASSESS→DISPOSITION）"));
    const bodyText = (await drawer.innerText().catch(() => "")) || "";
    observations.trajectoryMentionsStart = bodyText.includes(TEMPLATE.startNode) || bodyText.includes("急诊评估");
    observations.trajectoryMentionsTerminal = bodyText.includes(TEMPLATE.terminalNode) || bodyText.includes("处置安排");
    observations.simulateSuccessToast = await page
      .locator(".ant-message", { hasText: "试运行成功" })
      .count()
      .then((c) => c > 0)
      .catch(() => false);
    if (!observations.trajectoryMentionsStart || !observations.trajectoryMentionsTerminal) {
      summary.failures.push({ step: "试运行轨迹未同时呈现起止节点", detail: observations });
    }
  } catch (error) {
    summary.failures.push({ step: "路径试运行异常", detail: String(error).slice(0, 400) });
    await capture(browser, page, "06e-ui-pathway-simulate-error.png", "路径试运行异常现场").catch(() => undefined);
  } finally {
    summary.simulate = { observations, steps };
    await context.close();
  }
}

// 进入详情抽屉的 7 步流发布 Tab。
async function openReleaseTab(page) {
  const drawer = await openTemplateDrawer(page);
  const enter = page.getByRole("button", { name: "进入 7 步流发布" }).first();
  if (await enter.count()) {
    await enter.click();
  } else {
    await drawer.getByRole("tab", { name: /发布/ }).click();
  }
  await page.waitForTimeout(1000);
  return drawer;
}

// ── 步骤五：知识治理员灰度发布（DRAFT→PUBLISHED）─────────────────────

async function canaryPublish(browser, credentials, summary) {
  const steps = [];
  const observations = {};
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-canary",
  );
  try {
    const before = await findTemplate(context, "canary-pre");
    observations.before = before.template?.status;
    if (before.template && before.template.status !== "DRAFT") {
      observations.skipped = `当前状态 ${before.template.status}，非 DRAFT，跳过灰度`;
      summary.canary = { observations, steps };
      return;
    }
    const drawer = await openReleaseTab(page);
    steps.push(await capture(browser, page, "07-ui-pathway-release-impact.png", "发布影响摘要（灰度前自动读取）"));
    await page.locator("#pathway-release-reason").fill(
      "已核查影响摘要、灰度范围 10%、随访交接与回滚安排，按制度提交审核并进入灰度发布。",
    );
    const publishBtn = drawer.getByRole("button", { name: "提交审核并进入灰度发布" }).first();
    for (let attempt = 0; attempt < 24; attempt += 1) {
      if (await publishBtn.isEnabled().catch(() => false)) break;
      await page.waitForTimeout(500);
    }
    observations.publishEnabled = await publishBtn.isEnabled().catch(() => false);
    await publishBtn.click();
    await page.waitForTimeout(2800);
    steps.push(await capture(browser, page, "08-ui-pathway-canary-published.png", "灰度发布门禁通过（进入 10% 灰度）"));

    const after = await findTemplate(context, "canary-post");
    observations.after = after.template?.status;
    summary.templateId = after.template?.templateId ?? summary.templateId;
    if (after.template?.status !== "PUBLISHED") {
      summary.failures.push({ step: `灰度发布后状态 ${after.template?.status} ≠ PUBLISHED`, detail: { after: after.template } });
    }
  } catch (error) {
    summary.failures.push({ step: "灰度发布异常", detail: String(error).slice(0, 400) });
    await capture(browser, page, "08e-ui-pathway-canary-error.png", "灰度发布异常现场").catch(() => undefined);
  } finally {
    summary.canary = { observations, steps };
    await context.close();
  }
}

// ── 步骤六：org-admin 探针，坐实 P5-ACT5-01 菜单缺口 ─────────────────────

async function probeOrgAdmin(browser, credentials, summary) {
  const discoveryDir = path.join(evidenceDir, "defect-p5-act5-01-discovery");
  await mkdir(discoveryDir, { recursive: true });
  const observations = {};
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "organization-admin"),
    "organization-admin-probe",
  );
  try {
    const me = await apiGet(context, "/security/me", "org-admin-me");
    const menuKeys = me.body?.data?.menuKeys ?? [];
    const permCodes = (me.body?.data?.permissions ?? []).map((p) => p.code);
    observations.hasPathwayRead = permCodes.includes("pathway.read");
    observations.hasPathwayWrite = permCodes.includes("pathway.write");
    observations.hasPathwayPublish = permCodes.includes("pathway.publish");
    observations.hasPathwayTemplatesMenu = menuKeys.includes("pathway-templates");
    observations.hasRuleDefinitionsMenu = menuKeys.includes("rule-definitions");

    await gotoPath(page, "/pathway/templates");
    await page.waitForTimeout(1200);
    observations.newTemplateButtonVisible = await page
      .getByRole("button", { name: "新建路径模板" })
      .first()
      .isVisible()
      .catch(() => false);
    observations.pageText = (await page.locator("body").innerText().catch(() => "")).slice(0, 400);

    const finalPath = path.join(discoveryDir, "01-org-admin-pathway-blocked.png");
    const rawPath = path.join(discoveryDir, ".raw-blocked.png");
    await page.screenshot({ path: rawPath, fullPage: false });
    await renderWithUrlBar(browser, rawPath, finalPath, page.url());

    const record = {
      defect: "P5-ACT5-01",
      role: "organization-admin",
      title: "机构管理员持 pathway.publish 且为合法全量协调角色，却缺 MENU_PATHWAY_TEMPLATES，进不去路径模板治理页",
      generatedAt: new Date().toISOString(),
      environment: baseUrl,
      backendFacts: {
        requireReleaseCoordinator: "客户租户全量/回滚门放行 CLINICAL_GOVERNOR 或 ORGANIZATION_ADMIN（PathwayEngineService.requireReleaseCoordinator）",
        permissionPolicy: "organizationAdministrationPermissions() 菜单白名单含 MENU_RULE_DEFINITIONS（P5-ACT4-02 修复）但缺 MENU_PATHWAY_TEMPLATES（DefaultPermissionPolicy.java）",
        routeGuard: "/pathway/templates 路由守卫 every(menu.pathway-templates, pathway.read)（routes.ts），菜单缺失即路由拦截",
      },
      observations,
      screenshot: "01-org-admin-pathway-blocked.png",
    };
    await writeFile(path.join(discoveryDir, "00-discovery.json"), `${JSON.stringify(record, null, 2)}\n`, "utf8");
    summary.defectDiscovery = record;
    // 与后端事实矛盾即为缺陷证据（持发布权/合法协调角色，却无菜单、进不去页）。
    observations.isDefect = observations.hasPathwayPublish && !observations.hasPathwayTemplatesMenu;
  } finally {
    await context.close();
  }
}

// ── 步骤七：协调角色院级确认全量激活（→FULL）─────────────────────

async function fullRollout(browser, credentials, summary) {
  const steps = [];
  const observations = { role: fullRolloutRole };
  const { context, page } = await login(
    browser,
    requireAccount(credentials, fullRolloutRole),
    `${fullRolloutRole}-full`,
  );
  try {
    const before = await findTemplate(context, "full-pre");
    observations.before = before.template?.status;
    if (!before.template) {
      summary.failures.push({ step: "全量前未回查到路径模板", detail: {} });
      return;
    }
    if (before.template.status !== "PUBLISHED") {
      observations.skipped = `当前状态 ${before.template.status}，非 PUBLISHED（灰度态），跳过全量`;
      summary.full = { observations, steps };
      return;
    }
    const beforeDetail = await templateDetail(context, before.template.templateId, "full-pre-detail");
    observations.beforeDeployment = beforeDetail.detail?.deploymentStatus;

    const drawer = await openReleaseTab(page);
    steps.push(await capture(browser, page, "09-ui-pathway-fullrollout-impact.png", `${fullRolloutRole} 院级全量前影响摘要`));
    await page.locator("#pathway-release-reason").fill(
      "灰度验证无异常，已复核影响摘要与回滚证据，院级确认全量激活急诊处置路径。",
    );
    const fullBtn = drawer.getByRole("button", { name: "院级确认全量激活" }).first();
    for (let attempt = 0; attempt < 24; attempt += 1) {
      if (await fullBtn.isEnabled().catch(() => false)) break;
      await page.waitForTimeout(500);
    }
    observations.fullButtonVisible = await fullBtn.count().then((c) => c > 0);
    observations.fullButtonEnabled = await fullBtn.isEnabled().catch(() => false);
    await fullBtn.click();
    await page.waitForTimeout(3000);
    steps.push(await capture(browser, page, "10-ui-pathway-fullrollout-done.png", `${fullRolloutRole} 院级全量激活完成`));

    const after = await findTemplate(context, "full-post");
    const afterDetail = after.template
      ? await templateDetail(context, after.template.templateId, "full-post-detail")
      : { detail: null };
    observations.after = after.template?.status;
    observations.afterDeployment = afterDetail.detail?.deploymentStatus;
    if (afterDetail.detail?.deploymentStatus !== "PUBLISHED") {
      summary.failures.push({
        step: `院级全量后 deploymentStatus ${afterDetail.detail?.deploymentStatus} ≠ PUBLISHED`,
        detail: { after: after.template, deployment: afterDetail.detail?.deploymentStatus },
      });
    }
  } catch (error) {
    summary.failures.push({ step: "院级全量异常", detail: String(error).slice(0, 400) });
    await capture(browser, page, "10e-ui-pathway-fullrollout-error.png", "院级全量异常现场").catch(() => undefined);
  } finally {
    summary.full = { observations, steps };
    await context.close();
  }
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const summary = {
    act: "P5幕5-路径治理（治理侧完整旅程到院级全量）",
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    credentialSource: credentialPath,
    phase,
    fullRolloutRole,
    package: PKG,
    template: TEMPLATE,
    failures: [],
  };
  // 支持 all 或逗号分隔多阶段（如 seed,package,create,simulate,canary,probe）。
  const phaseSet = new Set(phase.split(",").map((p) => p.trim()).filter(Boolean));
  const want = (name) => phase === "all" || phaseSet.has(name);
  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    if (want("seed")) await seedSnapshot(browser, credentials, summary);
    if (want("package") && summary.failures.length === 0) {
      await buildPackage(browser, credentials, summary);
    }
    if (want("create") && summary.failures.length === 0) {
      await createTemplate(browser, credentials, summary);
    }
    if (want("simulate") && summary.failures.length === 0) {
      await simulateTrajectory(browser, credentials, summary);
    }
    if (want("canary") && summary.failures.length === 0) {
      await canaryPublish(browser, credentials, summary);
    }
    if (want("probe")) {
      await probeOrgAdmin(browser, credentials, summary);
    }
    if (want("full") && summary.failures.length === 0) {
      await fullRollout(browser, credentials, summary);
    }
  } catch (error) {
    summary.failures.push({ step: "主流程异常中断", detail: String(error).slice(0, 600) });
  } finally {
    await browser.close();
  }
  await writeFile(
    path.join(evidenceDir, "00-act5-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  const ok = summary.failures.length === 0;
  console.log(ok ? "幕5 路径治理演练通过" : "幕5 路径治理演练存在失败");
  console.log(JSON.stringify({ phase, failures: summary.failures }, null, 2));
  process.exit(ok ? 0 : 1);
}

await main();
