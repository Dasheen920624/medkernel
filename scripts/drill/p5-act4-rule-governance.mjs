#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕4 规则治理（治理侧完整旅程，执行与医师确认留幕6）。
// 剧本动作：
//   1. 集成运维员以 API 模拟外部系统铺底 4 份标准上下文快照（血钾危急/边界/阴性 + 血钠冲突），
//      仅集成/系统角色有 context.write，临床角色只有 context.read。
//   2. 机构知识治理员真实前台 /rule/definitions 用「危急值回报」模板创建红线规则草稿
//      （血钾 observations[].valueNumeric ≥ 5.5，命中 STRONG_REMINDER 强提醒、需医师确认）。
//   3. 医疗安全红线断言：动作为提醒类、需医师确认、不自动开立或修改医嘱。
//   4. 治理员补齐阳性/阴性/边界/冲突四类发布门禁测试用例并执行全绿；真实快照试运行命中。
//   5. 治理链到院级全量：读影响摘要 → 提交同行评审 → 同行评审通过 → 委员会双人独立会签 →
//      进入影子运行 → 进入灰度验证(CANARY) → 院级全量激活(FULL)。
// 成功判定一律以服务端回查为准（/engine/rule/rules、/engine/rule/rules/{id}、/engine/context/snapshots）。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕4-规则治理/
//   00-act4-summary.json 与 NN-ui-*.png（全部带 URL 栏）。
// 凭据：默认读取本机受控副本 /tmp/p5-14-role-drill-credentials-20260612.json（权威文件在服务器
//   /zoesoft/medkernel/conf/p5-14-role-drill-credentials-20260612.json，权限 600）；凭据不入仓库。
import { createHash } from "node:crypto";
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕4-规则治理",
);
// 阶段闸门：seed|create|cases|simulate|govern|all（默认 all），便于断点续跑。
const phase = process.env.DRILL_PHASE ?? "all";

const PACKAGE_VERSION = "2026.06.1";
const RULE = {
  code: "P5.ACT4.CRITICAL.K",
  name: "血钾危急值回报红线规则",
  sourceRef: "院内危急值管理制度2026版·血钾≥5.5mmol/L 须即时回报",
  changeSummary: "初始化血钾危急值回报红线规则草稿（强提醒+医师确认，不自动开嘱）",
  observationCode: "2823-3",
  observationName: "血清钾",
  threshold: "5.5",
  returnMinutes: 15,
};

// 四类发布门禁用例对应的 4 份快照；用不同患者主索引保证逐份可检索选中。
const SNAPSHOTS = [
  {
    key: "POSITIVE",
    caseType: "POSITIVE",
    patientId: "P5-ACT4-K-POS",
    encounterId: "P5-ACT4-ENC-POS",
    name: "危急值阳性患者",
    obsCode: "2823-3",
    obsName: "血清钾",
    valueNumeric: 6.8,
    unit: "mmol/L",
    referenceRange: "3.5-5.5",
    criticalFlag: "HIGH",
    expectedHit: true,
    expectedSeverity: "CRITICAL",
    expectedActionCode: "STRONG_REMINDER",
  },
  {
    key: "BOUNDARY",
    caseType: "BOUNDARY",
    patientId: "P5-ACT4-K-BND",
    encounterId: "P5-ACT4-ENC-BND",
    name: "危急值边界患者",
    obsCode: "2823-3",
    obsName: "血清钾",
    valueNumeric: 5.5,
    unit: "mmol/L",
    referenceRange: "3.5-5.5",
    criticalFlag: "HIGH",
    expectedHit: true,
    expectedSeverity: "CRITICAL",
    expectedActionCode: "STRONG_REMINDER",
  },
  {
    key: "NEGATIVE",
    caseType: "NEGATIVE",
    patientId: "P5-ACT4-K-NEG",
    encounterId: "P5-ACT4-ENC-NEG",
    name: "血钾正常患者",
    obsCode: "2823-3",
    obsName: "血清钾",
    valueNumeric: 4.2,
    unit: "mmol/L",
    referenceRange: "3.5-5.5",
    criticalFlag: null,
    expectedHit: false,
  },
  {
    // 冲突校验：仅血钠危急、无血钾命中项，规则按 code 过滤后不应误触发。
    key: "CONFLICT",
    caseType: "CONFLICT",
    patientId: "P5-ACT4-NA-CFL",
    encounterId: "P5-ACT4-ENC-CFL",
    name: "血钠危急血钾缺失患者",
    obsCode: "2951-2",
    obsName: "血清钠",
    valueNumeric: 160,
    unit: "mmol/L",
    referenceRange: "137-147",
    criticalFlag: "HIGH",
    expectedHit: false,
  },
];

function traceId(stage) {
  return `p5-act4-${stage}-${Date.now()}`;
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
  // 机构管理员是租户首发管理员，凭据保存在 customerTenant 节点。
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

// ── 标准上下文快照（API 模拟外部系统铺底）─────────────────────

function isoNow(offsetMinutes = 0) {
  return new Date(Date.now() + offsetMinutes * 60_000).toISOString();
}

function buildSnapshotPayload(snapshot, orgUnitId) {
  const eventTime = isoNow(-30);
  const receivedTime = isoNow(-20);
  return {
    request_id: `p5-act4-snap-${snapshot.key}`,
    trace_id: traceId(`snap-${snapshot.key}`),
    patientId: snapshot.patientId,
    encounterId: snapshot.encounterId,
    orgUnitId,
    package_version: PACKAGE_VERSION,
    resources: {
      patient: {
        mpi: snapshot.patientId,
        name: snapshot.name,
        birthDate: "1958-03-12",
        gender: "MALE",
        specialPopulations: [],
        sourceSystem: "P5-ACT4-MOCK-LIS",
        sourceRecordId: `pat-${snapshot.key}`,
        mappedVersion: PACKAGE_VERSION,
        eventTime,
        receivedTime,
        qualityStatus: "VALID",
      },
      encounters: [
        {
          encounterId: snapshot.encounterId,
          encounterType: "INPATIENT",
          admissionTime: isoNow(-2880),
          dischargeTime: null,
          departmentId: "P5-DEPT-NEPHRO",
          attendingDoctorId: "P5-DOC-001",
          bedId: "P5-BED-12A",
          sourceSystem: "P5-ACT4-MOCK-HIS",
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
          sourceSystem: "P5-ACT4-MOCK-LIS",
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

async function seedSnapshots(browser, credentials, summary) {
  const { context } = await login(
    browser,
    requireAccount(credentials, "integration-operator"),
    "integration-operator",
  );
  const observations = { seeded: [] };
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
    for (const snap of SNAPSHOTS) {
      const existing = await findActiveSnapshot(context, snap.patientId, `pre-${snap.key}`);
      if (existing.snapshot) {
        observations.seeded.push({
          key: snap.key,
          snapshotId: existing.snapshot.snapshotId,
          reused: true,
        });
        continue;
      }
      const payload = buildSnapshotPayload(snap, orgUnitId);
      const created = await apiPost(context, "/engine/context/snapshots", payload, `snap-${snap.key}`);
      if (created.status !== 201) {
        summary.failures.push({
          step: `铺底快照 ${snap.key} 失败`,
          detail: { status: created.status, body: created.body },
        });
        return;
      }
      const after = await findActiveSnapshot(context, snap.patientId, `post-${snap.key}`);
      if (!after.snapshot || after.snapshot.status !== "ACTIVE") {
        summary.failures.push({
          step: `快照 ${snap.key} 未回查到 ACTIVE`,
          detail: { after: after.snapshot },
        });
        return;
      }
      observations.seeded.push({
        key: snap.key,
        snapshotId: after.snapshot.snapshotId,
        quality: after.snapshot.qualityStatus,
        reused: false,
      });
    }
  } finally {
    summary.seedSnapshots = { observations };
    await context.close();
  }
}

// ── 规则查找与详情读回（服务端事实）─────────────────────

async function findRule(context, stage) {
  const res = await apiGet(context, "/engine/rule/rules?page=1&size=100", stage);
  const items = res.body?.data?.items ?? [];
  const rule = items.find((item) => item.ruleCode === RULE.code);
  return { status: res.status, rule };
}

async function ruleDetail(context, ruleId, stage) {
  const res = await apiGet(context, `/engine/rule/rules/${ruleId}`, stage);
  return { status: res.status, detail: res.body?.data };
}

// ── 步骤二：知识治理员前台创建红线规则 ─────────────────────

async function createRule(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-create",
  );
  const steps = [];
  const observations = {};
  try {
    const before = await findRule(context, "rule-before-create");
    if (before.rule) {
      observations.ruleId = before.rule.ruleId;
      observations.alreadyExists = true;
      summary.ruleId = before.rule.ruleId;
      return;
    }

    await gotoPath(page, "/rule/definitions");
    steps.push(await capture(browser, page, "01-ui-rule-list-before.png", "规则定义页（创建前，零数据）"));

    await page.getByRole("button", { name: "新建规则模板" }).click();
    const modal = page.locator(".ant-modal-content", { hasText: "创建新临床规则" });
    await modal.waitFor({ state: "visible", timeout: 10000 });

    // L1 选用「危急值回报」模板（自动填 CRITICAL 风险 / result-review 触发 / STRONG_REMINDER 动作）。
    await modal.getByRole("radio", { name: "危急值回报" }).click();
    await page.waitForTimeout(400);

    // L1 危急值专属字段：检验项编码与快照对齐(2823-3)、危急阈值=5.5。
    await modal.locator("#critical-observation-code").fill(RULE.observationCode);
    await modal.locator("#critical-threshold").fill(RULE.threshold);

    // L1 顶部必填。
    await modal.getByLabel("规则唯一业务编码").fill(RULE.code);
    await modal.getByLabel("规则显示名称").fill(RULE.name);
    await modal.getByLabel("医学依据/来源").fill(RULE.sourceRef);
    await modal.getByLabel("标准上下文包版本").fill(PACKAGE_VERSION);
    await modal.getByLabel("初始化变更内容说明").fill(RULE.changeSummary);
    steps.push(await capture(browser, page, "02-ui-rule-create-form.png", "危急值回报红线规则创建表单"));

    await modal.getByRole("button", { name: "创建草稿" }).click();
    await modal.waitFor({ state: "hidden", timeout: 20000 }).catch(() => undefined);
    await waitForQuiet(page);
    steps.push(await capture(browser, page, "03-ui-rule-list-after-create.png", "创建草稿后的规则列表"));

    const after = await findRule(context, "rule-after-create");
    if (!after.rule) {
      observations.errorText = await page
        .locator(".ant-message")
        .innerText()
        .catch(() => null);
      summary.failures.push({ step: "创建规则后服务端回查未找到", detail: observations });
      return;
    }
    observations.ruleId = after.rule.ruleId;
    observations.riskLevel = after.rule.riskLevel;
    observations.status = after.rule.status;
    summary.ruleId = after.rule.ruleId;
  } finally {
    summary.createRule = { observations, steps };
    await context.close();
  }
}

// ── 步骤三：医疗安全红线断言（定义时，服务端事实 + 前台可读路径）─────────────

async function assertSafety(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-safety",
  );
  const steps = [];
  const observations = {};
  try {
    const found = await findRule(context, "rule-for-safety");
    if (!found.rule) {
      summary.failures.push({ step: "红线断言找不到规则", detail: {} });
      return;
    }
    const detail = await ruleDetail(context, found.rule.ruleId, "detail-safety");
    let dsl = null;
    try {
      dsl = JSON.parse(detail.detail?.version?.dslJson ?? "null");
    } catch {
      dsl = null;
    }
    const actions = Array.isArray(dsl?.then) ? dsl.then : [];
    observations.actionCodes = actions.map((a) => a.actionCode);
    observations.requiresPhysicianConfirmation = actions.some(
      (a) => a.requiresPhysicianConfirmation === true,
    );
    observations.hasStrongReminder = actions.some((a) => a.actionCode === "STRONG_REMINDER");
    observations.hasBlockingAutoOrder = actions.some(
      (a) => a.actionCode === "BLOCK" || a.actionCode === "SUGGEST_ORDER" || a.actionCode === "AUTO_DOCUMENT",
    );
    observations.riskLevel = detail.detail?.definition?.riskLevel;

    if (!observations.requiresPhysicianConfirmation) {
      summary.failures.push({ step: "红线断言失败：动作未要求医师确认", detail: observations });
    }
    if (!observations.hasStrongReminder) {
      summary.failures.push({ step: "红线断言失败：动作非 STRONG_REMINDER 强提醒", detail: observations });
    }
    if (observations.hasBlockingAutoOrder) {
      summary.failures.push({ step: "红线断言失败：出现自动开嘱/阻断类动作", detail: observations });
    }

    // 前台可读路径佐证：打开详情，治理与安全段应显示「需要医师确认」「不自动开立或修改医嘱」。
    await gotoPath(page, "/rule/definitions");
    await page.getByRole("button", { name: "查看配置与试运行" }).first().click();
    const drawer = page.locator(".ant-drawer-content");
    await drawer.waitFor({ state: "visible", timeout: 10000 });
    await page.waitForTimeout(800);
    observations.readableSafetyVisible = await page
      .getByText("不自动开立或修改医嘱", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    steps.push(await capture(browser, page, "04-ui-rule-readable-safety.png", "规则可读路径·治理与安全（医师确认+不自动开嘱）"));
  } finally {
    summary.assertSafety = { observations, steps };
    await context.close();
  }
}

// ── 步骤四：补四类发布门禁测试用例 + 执行全绿 + 真实快照试运行 ─────────────

async function openRuleDetail(page) {
  await gotoPath(page, "/rule/definitions");
  await page.getByRole("button", { name: "查看配置与试运行" }).first().click();
  const drawer = page.locator(".ant-drawer-content");
  await drawer.waitFor({ state: "visible", timeout: 10000 });
  await page.waitForTimeout(800);
  return drawer;
}

async function addTestCase(browser, page, drawer, snap, steps, index) {
  await drawer.getByRole("tab", { name: /发布门禁测试用例/ }).click();
  await page.waitForTimeout(400);
  await drawer.getByRole("button", { name: "新增测试用例" }).click();
  const modal = page.locator(".ant-modal-content", { hasText: "新增测试用例" });
  await modal.waitFor({ state: "visible", timeout: 10000 });

  await chooseSelectOption(page, modal, "caseType", snap.caseType === "POSITIVE"
    ? "阳性命中用例"
    : snap.caseType === "NEGATIVE"
      ? "阴性不命中用例"
      : snap.caseType === "BOUNDARY"
        ? "边界条件用例"
        : "规则冲突校验用例");

  await modal.locator("#rule-case-snapshot-patient-id").fill(snap.patientId);
  await modal.locator("#rule-case-snapshot-encounter-id").fill(snap.encounterId);
  await modal.getByRole("button", { name: "读取 ACTIVE 快照" }).click();
  await page.waitForTimeout(1200);
  // 选中唯一返回的快照卡片。
  await modal.locator(".ant-card", { hasText: snap.patientId }).first().click();
  await page.waitForTimeout(800);

  await chooseSelectOption(page, modal, "expectedHit", snap.expectedHit ? "应当触发规则命中" : "不应当命中");
  if (snap.expectedHit) {
    await chooseSelectOption(page, modal, "expectedSeverity", "红线");
    await chooseSelectOption(page, modal, "expectedActionCode", "强提醒");
  }
  steps.push(
    await capture(browser, page, `05${String.fromCharCode(97 + index)}-ui-testcase-${snap.key}.png`, `新增${snap.caseType}测试用例`),
  );
  await modal.getByRole("button", { name: "保存用例" }).click();
  await modal.waitFor({ state: "hidden", timeout: 15000 }).catch(() => undefined);
  await page.waitForTimeout(800);
}

async function buildTestCasesAndRun(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-cases",
  );
  const steps = [];
  const observations = {};
  try {
    const found = await findRule(context, "rule-for-cases");
    if (!found.rule) {
      summary.failures.push({ step: "补用例找不到规则", detail: {} });
      return;
    }
    const ruleId = found.rule.ruleId;
    summary.ruleId = ruleId;

    const detailBefore = await ruleDetail(context, ruleId, "cases-before");
    const existingTypes = new Set((detailBefore.detail?.testCases ?? []).map((c) => c.caseType));
    observations.existingTypes = [...existingTypes];

    const drawer = await openRuleDetail(page);
    let index = 0;
    for (const snap of SNAPSHOTS) {
      if (existingTypes.has(snap.caseType)) {
        index += 1;
        continue;
      }
      await addTestCase(browser, page, drawer, snap, steps, index);
      index += 1;
    }

    // 执行全部用例。
    await drawer.getByRole("tab", { name: /发布门禁测试用例/ }).click();
    await page.waitForTimeout(400);
    await drawer.getByRole("button", { name: "执行全部用例" }).click();
    await page.waitForTimeout(3500);
    steps.push(await capture(browser, page, "06-ui-testcases-run-result.png", "四类发布门禁用例执行结果"));

    const detailAfter = await ruleDetail(context, ruleId, "cases-after");
    const cases = detailAfter.detail?.testCases ?? [];
    observations.caseSummary = cases.map((c) => ({ caseType: c.caseType, lastStatus: c.lastStatus }));
    const types = new Set(cases.map((c) => c.caseType));
    const requiredTypes = ["POSITIVE", "NEGATIVE", "BOUNDARY", "CONFLICT"];
    observations.missingTypes = requiredTypes.filter((t) => !types.has(t));
    observations.allPassed =
      cases.length > 0 &&
      observations.missingTypes.length === 0 &&
      cases.every((c) => c.lastStatus === "PASS");
    if (!observations.allPassed) {
      summary.failures.push({ step: "发布门禁四类用例未全绿", detail: observations });
    }
  } finally {
    summary.testCases = { observations, steps };
    await context.close();
  }
}

async function simulateHit(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-simulate",
  );
  const steps = [];
  const observations = {};
  try {
    const found = await findRule(context, "rule-for-simulate");
    if (!found.rule) {
      summary.failures.push({ step: "试运行找不到规则", detail: {} });
      return;
    }
    const drawer = await openRuleDetail(page);
    await drawer.getByRole("tab", { name: /真实快照试运行/ }).click();
    await page.waitForTimeout(500);
    await page.locator("#rule-snapshot-patient-id").fill(SNAPSHOTS[0].patientId);
    await page.locator("#rule-snapshot-encounter-id").fill(SNAPSHOTS[0].encounterId);
    await page.getByRole("button", { name: "读取真实快照" }).click();
    await page.waitForTimeout(1200);
    await page.locator(".ant-card", { hasText: SNAPSHOTS[0].patientId }).first().click();
    await page.waitForTimeout(800);
    await page.getByRole("button", { name: "使用该快照试运行" }).click();
    await page.waitForTimeout(2500);
    steps.push(await capture(browser, page, "07-ui-simulate-hit.png", "阳性快照试运行命中（强提醒+必须医师确认）"));

    observations.hit = await page
      .getByText("命中", { exact: true })
      .first()
      .isVisible()
      .catch(() => false);
    observations.strongReminderVisible = await page
      .getByText("STRONG_REMINDER", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    observations.physicianConfirmVisible = await page
      .getByText("必须医师确认", { exact: false })
      .first()
      .isVisible()
      .catch(() => false);
    if (!observations.hit || !observations.strongReminderVisible || !observations.physicianConfirmVisible) {
      summary.failures.push({ step: "试运行未呈现命中/强提醒/医师确认", detail: observations });
    }
  } finally {
    summary.simulate = { observations, steps };
    await context.close();
  }
}

// ── 缺陷发现：红线规则法定治理角色被前端路由守卫挡在规则页外 ──
// P5-ACT4-01 质量治理员（法定第二名独立委员）；P5-ACT4-02 机构管理员（唯一职责分离合规发布人）。
// 复跑只针对仍开放的缺陷探测；P5-ACT4-01（质量治理员）已修复部署，其发现证据已归档，
// 不再纳入复跑以免覆盖既有证据。
const DISCOVERY_TARGETS = [
  {
    role: "organization-admin",
    defect: "P5-ACT4-02",
    dir: "defect-p5-act4-02-discovery",
    title: "机构管理员缺 menu.rule-definitions，红线规则唯一职责分离合规发布人无法经真实前台推进影子/灰度/全量",
    backendFacts:
      "validateTransition 要求作者≠会签人≠发布人；客户租户委员会必为临床+质量（皆会签人）、作者=知识治理员，唯一合规发布人只能是机构管理员；其持 rule.publish 但 withOnlyMenus 未含 MENU_RULE_DEFINITIONS",
  },
];

async function discoverRoleRuleAccess(browser, credentials, summary) {
  summary.defectDiscovery = [];
  for (const target of DISCOVERY_TARGETS) {
    const discoveryDir = path.join(evidenceDir, target.dir);
    await mkdir(discoveryDir, { recursive: true });
    const { context, page } = await login(
      browser,
      requireAccount(credentials, target.role),
      `${target.role}-discover`,
    );
    const observations = {};
    try {
      const me = await apiGet(context, "/security/me", `${target.role}-me`);
      const menuKeys = me.body?.data?.menuKeys ?? [];
      const permCodes = (me.body?.data?.permissions ?? []).map((p) => p.code);
      observations.hasRuleRead = permCodes.includes("rule.read");
      observations.hasRulePublish = permCodes.includes("rule.publish");
      observations.hasRuleDefinitionsMenu = menuKeys.includes("rule-definitions");

      await gotoPath(page, "/rule/definitions");
      await page.waitForTimeout(1000);
      observations.detailButtonVisible = await page
        .getByRole("button", { name: "查看配置与试运行" })
        .first()
        .isVisible()
        .catch(() => false);
      observations.pageText = (await page.locator("body").innerText().catch(() => "")).slice(0, 400);

      const finalPath = path.join(discoveryDir, `01-${target.role}-rule-page.png`);
      const rawPath = path.join(discoveryDir, ".raw-blocked.png");
      await page.screenshot({ path: rawPath, fullPage: false });
      await renderWithUrlBar(browser, rawPath, finalPath, page.url());

      const record = {
        defect: target.defect,
        role: target.role,
        title: target.title,
        generatedAt: new Date().toISOString(),
        environment: baseUrl,
        backendFacts: target.backendFacts,
        observations,
        screenshot: `01-${target.role}-rule-page.png`,
      };
      await writeFile(
        path.join(discoveryDir, "00-discovery.json"),
        `${JSON.stringify(record, null, 2)}\n`,
        "utf8",
      );
      summary.defectDiscovery.push(record);
    } finally {
      await context.close();
    }
  }
}

// ── 步骤五：治理链到院级全量 ─────────────────────

async function openReleaseTab(page) {
  const drawer = await openRuleDetail(page);
  await drawer.getByRole("tab", { name: /治理与发布/ }).click();
  await page.waitForTimeout(800);
  return drawer;
}

// 通用治理动作：填治理说明 → （如需）轮询按钮可用 → 点击 → （如需）填独立电子签名 → 截图。
async function performGovernanceAction(browser, page, drawer, { buttonName, reason, captureName, label, needsEnable, signature }) {
  await page.locator("#rule-release-reason").fill(reason);
  const button = drawer.getByRole("button", { name: buttonName }).first();
  if (needsEnable) {
    for (let attempt = 0; attempt < 24; attempt += 1) {
      if (await button.isEnabled().catch(() => false)) break;
      await page.waitForTimeout(500);
    }
  }
  const enabled = await button.isEnabled().catch(() => false);
  await button.click();
  await page.waitForTimeout(1500);
  let signatureProvided = false;
  if (signature) {
    // 高风险规则院级全量激活弹出独立电子签名弹窗，复核人须不同于发布人。
    const modal = page.locator(".ant-modal-content", { hasText: "独立电子签名" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByLabel("电子签名 ID").fill(signature.signatureId);
    const signedAt = modal.getByLabel("签名时间");
    await signedAt.fill("");
    await signedAt.fill(signature.signedAt);
    await modal.getByLabel("复核人 ID").fill(signature.signerId);
    await modal.getByLabel("复核人姓名").fill(signature.signerName);
    await modal.getByLabel("电子签名摘要（SHA-256）").fill(signature.signatureHash);
    signatureProvided = true;
    await modal.getByRole("button", { name: "电子签名并全量激活" }).click();
    await modal.waitFor({ state: "hidden", timeout: 20000 }).catch(() => undefined);
  }
  await page.waitForTimeout(2500);
  const step = await capture(browser, page, captureName, label);
  return { enabled, step, signatureProvided };
}

async function governanceWalk(browser, credentials, summary) {
  const steps = [];
  const observations = { transitions: [] };

  // 角色凭据。
  const roleAccounts = {
    "knowledge-governor": requireAccount(credentials, "knowledge-governor"),
    "clinical-governor": requireAccount(credentials, "clinical-governor"),
    "quality-governor": requireAccount(credentials, "quality-governor"),
    "organization-admin": requireAccount(credentials, "organization-admin"),
  };

  // 读当前治理态（任意治理员会话即可）。
  async function currentGovernance(role) {
    const { context } = await login(browser, roleAccounts[role], `gov-read-${role}`);
    try {
      const found = await findRule(context, "gov-read");
      if (!found.rule) return null;
      const detail = await ruleDetail(context, found.rule.ruleId, "gov-detail");
      summary.ruleId = found.rule.ruleId;
      return detail.detail?.governance ?? null;
    } finally {
      await context.close();
    }
  }

  // 单步：登录角色 → 打开发布 Tab → 执行动作 → 回查。
  async function step(role, opts, expect) {
    const gov = await currentGovernance(role);
    observations.transitions.push({ role, before: gov?.state, committee: gov?.committeeApprovalCount });
    if (expect.skipIf && expect.skipIf(gov)) {
      return gov;
    }
    if (expect.fromStates && gov && !expect.fromStates.includes(gov.state)) {
      summary.failures.push({
        step: `${opts.label}：当前状态 ${gov?.state} 不在期望前置 ${expect.fromStates.join("/")}`,
        detail: { gov },
      });
      return gov;
    }
    const { context, page } = await login(browser, roleAccounts[role], `gov-${role}`);
    try {
      const drawer = await openReleaseTab(page);
      const result = await performGovernanceAction(browser, page, drawer, opts);
      steps.push(result.step);
      // 弹窗错误（治理推进被拒绝/会签失败）即记失败。
      const modalError = await page
        .locator(".ant-modal-confirm-error, .ant-modal-confirm-title")
        .first()
        .innerText()
        .catch(() => null);
      const after = await currentGovernance(role);
      observations.transitions.push({ role, after: after?.state, committee: after?.committeeApprovalCount });
      if (expect.toState && after?.state !== expect.toState) {
        summary.failures.push({
          step: `${opts.label}：回查状态 ${after?.state} ≠ 期望 ${expect.toState}`,
          detail: { buttonEnabled: result.enabled, modalError, after },
        });
      }
      if (expect.toCommittee && (after?.committeeApprovalCount ?? 0) < expect.toCommittee) {
        summary.failures.push({
          step: `${opts.label}：会签数 ${after?.committeeApprovalCount} < 期望 ${expect.toCommittee}`,
          detail: { modalError, after },
        });
      }
      return after;
    } finally {
      await context.close();
    }
  }

  try {
    // 5.1 知识治理员：DRAFT → 提交同行评审（需四类用例全绿 + 影响摘要）。
    await step(
      "knowledge-governor",
      {
        buttonName: "提交同行评审",
        reason: "四类发布门禁用例全绿、真实快照试运行命中，按制度提交同行评审。",
        captureName: "08-ui-gov-submit-peer-review.png",
        label: "提交同行评审",
        needsEnable: true,
      },
      { fromStates: ["DRAFT"], toState: "PEER_REVIEW", skipIf: (g) => g && g.state !== "DRAFT" },
    );

    // 5.2 临床治理员：PEER_REVIEW 同行评审通过 → COMMITTEE。
    await step(
      "clinical-governor",
      {
        buttonName: "同行评审通过",
        reason: "规则条件、动作与医师确认红线符合危急值制度，同行评审通过。",
        captureName: "09-ui-gov-peer-approve.png",
        label: "同行评审通过",
        needsEnable: false,
      },
      { fromStates: ["PEER_REVIEW"], toState: "COMMITTEE", skipIf: (g) => g && !["PEER_REVIEW"].includes(g.state) && g.state !== "DRAFT" },
    );

    // 5.3 委员会第一名独立成员（临床治理员）会签通过。
    await step(
      "clinical-governor",
      {
        buttonName: "委员会会签通过",
        reason: "红线规则委员会第一名独立成员会签通过。",
        captureName: "10-ui-gov-committee-sign1.png",
        label: "委员会会签1",
        needsEnable: false,
      },
      { fromStates: ["COMMITTEE"], toCommittee: 1, skipIf: (g) => g && (g.committeeApprovalCount ?? 0) >= 1 },
    );

    // 5.4 委员会第二名独立成员（质量治理员）会签通过 → 达双签。
    await step(
      "quality-governor",
      {
        buttonName: "委员会会签通过",
        reason: "红线规则委员会第二名独立成员会签通过，满足双人独立会签。",
        captureName: "11-ui-gov-committee-sign2.png",
        label: "委员会会签2",
        needsEnable: false,
      },
      { fromStates: ["COMMITTEE"], toCommittee: 2, skipIf: (g) => g && (g.committeeApprovalCount ?? 0) >= 2 },
    );

    // 5.5 机构管理员：COMMITTEE(2/2) → 进入影子运行。
    // 发布人必须与作者、会签人相互分离（validateTransition）；客户租户里临床/质量治理员都是会签人，
    // 知识治理员是作者，唯一职责分离合规且持 rule.publish 的发布人是机构管理员。
    await step(
      "organization-admin",
      {
        buttonName: "进入影子运行",
        reason: "双人独立会签达成，机构管理员作为独立发布人进入影子运行采集真实命中样本。",
        captureName: "12-ui-gov-enter-shadow.png",
        label: "进入影子运行",
        needsEnable: true,
      },
      { fromStates: ["COMMITTEE"], toState: "SHADOW", skipIf: (g) => g && ["SHADOW", "CANARY", "FULL", "MONITOR"].includes(g.state) },
    );

    // 5.6 机构管理员：SHADOW → 进入灰度验证(CANARY)。
    await step(
      "organization-admin",
      {
        buttonName: "进入灰度验证",
        reason: "影子运行无异常，机构管理员进入灰度验证按比例放量。",
        captureName: "13-ui-gov-enter-canary.png",
        label: "进入灰度验证",
        needsEnable: true,
      },
      { fromStates: ["SHADOW"], toState: "CANARY", skipIf: (g) => g && ["CANARY", "FULL", "MONITOR"].includes(g.state) },
    );

    // 5.7 机构管理员：CANARY → 院级全量激活(FULL)。
    // 红线规则全量发布须独立电子签名：复核人取临床治理员（独立于发布人机构管理员）。
    const fullSignature = {
      signatureId: `esig-p5-act4-${Date.now()}`,
      signerId: roleAccounts["clinical-governor"].username,
      signerName: "临床治理负责人",
      signedAt: new Date().toISOString(),
      signatureHash: createHash("sha256")
        .update(`p5-act4-full-${RULE.code}-${Date.now()}`)
        .digest("hex"),
    };
    await step(
      "organization-admin",
      {
        buttonName: "院级全量激活",
        reason: "灰度验证无异常，机构管理员批准院级全量激活并留存独立电子签名。",
        captureName: "14-ui-gov-activate-full.png",
        label: "院级全量激活（含独立电子签名）",
        needsEnable: true,
        signature: fullSignature,
      },
      { fromStates: ["CANARY"], toState: "FULL", skipIf: (g) => g && ["FULL", "MONITOR"].includes(g.state) },
    );
  } finally {
    summary.governance = { observations, steps };
  }
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const summary = {
    act: "P5幕4-规则治理（治理侧完整旅程到院级全量）",
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    credentialSource: credentialPath,
    phase,
    rule: RULE,
    failures: [],
  };
  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    const runAll = phase === "all";
    if (runAll || phase === "seed") await seedSnapshots(browser, credentials, summary);
    if ((runAll || phase === "create") && summary.failures.length === 0) {
      await createRule(browser, credentials, summary);
    }
    if ((runAll || phase === "create" || phase === "safety") && summary.failures.length === 0) {
      await assertSafety(browser, credentials, summary);
    }
    if ((runAll || phase === "cases") && summary.failures.length === 0) {
      await buildTestCasesAndRun(browser, credentials, summary);
    }
    if ((runAll || phase === "simulate") && summary.failures.length === 0) {
      await simulateHit(browser, credentials, summary);
    }
    if (phase === "discover") {
      await discoverRoleRuleAccess(browser, credentials, summary);
    }
    if ((runAll || phase === "govern") && summary.failures.length === 0) {
      await governanceWalk(browser, credentials, summary);
    }
  } catch (error) {
    summary.failures.push({ step: "主流程异常中断", detail: String(error).slice(0, 600) });
  } finally {
    await browser.close();
  }
  // discover 阶段单独归档发现证据，不覆盖主旅程 summary。
  if (phase !== "discover") {
    await writeFile(
      path.join(evidenceDir, "00-act4-summary.json"),
      `${JSON.stringify(summary, null, 2)}\n`,
      "utf8",
    );
  }
  if (summary.failures.length > 0) {
    console.error("幕4 规则治理旅程存在失败步骤：");
    for (const failure of summary.failures) {
      console.error(`- ${failure.step}`);
    }
    process.exitCode = 1;
    return;
  }
  console.log("幕4 规则治理旅程全部通过，证据已写入", evidenceDir);
}

await main();
