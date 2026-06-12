#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕2 术语与字典（跨角色审批链第一段）
// 剧本动作：
//   1. API 铺底（诚实登记为外部系统模拟）：机构知识治理员装载标准字典条目；医技协同人员登记院内 HIS/LIS 原始码并生成确定性映射候选。
//   2. 医技协同人员前台走查 /terminology/mapping：核对高危候选、确认入口、批量确认边界与冲突提示。
//   3. 机构知识治理员前台核对构建映射包入口状态。
//   4. 机构管理员核对术语页路由边界与配置包页全量发布权限承载。
//   5. 集成运维员核对系统接入页（发布同步通道前置）。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕2-术语与字典/
//   00-act2-summary.json 与 NN-ui-*.png（全部带 URL 栏）。
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕2-术语与字典",
);

// 幕2 铺底数据：与首轮剧本同源（CAP + 血钾危急值 + 华法林/阿司匹林 DDI 所需编码），
// normalizedName 按真实字典同义词口径维护，供确定性语义匹配器命中。
const standardTermSeeds = [
  {
    standardSystem: "LOINC",
    termCode: "2823-3",
    category: "LAB",
    displayName: "血清钾",
    normalizedName: "血清钾|血钾|serum potassium",
    versionNo: "2.78",
    evidenceText: "LOINC 2823-3 Potassium [Moles/volume] in Serum or Plasma",
  },
  {
    standardSystem: "LOINC",
    termCode: "2951-2",
    category: "LAB",
    displayName: "血清钠",
    normalizedName: "血清钠|血钠|serum sodium",
    versionNo: "2.78",
    evidenceText: "LOINC 2951-2 Sodium [Moles/volume] in Serum or Plasma",
  },
  {
    standardSystem: "ICD-10",
    termCode: "J15.9",
    category: "DIAGNOSIS",
    displayName: "细菌性肺炎",
    normalizedName: "细菌性肺炎|肺炎",
    versionNo: "2019",
    evidenceText: "ICD-10 J15.9 细菌性肺炎，未特指",
  },
  {
    standardSystem: "ATC",
    termCode: "B01AA03",
    category: "DRUG",
    displayName: "华法林",
    normalizedName: "华法林|华法林钠|warfarin",
    versionNo: "2026",
    evidenceText: "ATC B01AA03 warfarin",
  },
  {
    standardSystem: "ATC",
    termCode: "B01AC06",
    category: "DRUG",
    displayName: "阿司匹林",
    normalizedName: "阿司匹林|乙酰水杨酸|aspirin",
    versionNo: "2026",
    evidenceText: "ATC B01AC06 acetylsalicylic acid",
  },
];

const localTermSeeds = [
  {
    sourceSystem: "P5-LIS",
    localCode: "K001",
    category: "LAB",
    localName: "血钾",
    normalizedName: "血钾",
  },
  {
    sourceSystem: "P5-HIS",
    localCode: "Y2035",
    category: "DRUG",
    localName: "华法林钠片2.5mg",
    normalizedName: "华法林钠片|华法林钠",
  },
  {
    sourceSystem: "P5-HIS",
    localCode: "Y1011",
    category: "DRUG",
    localName: "阿司匹林肠溶片100mg",
    normalizedName: "阿司匹林肠溶片|阿司匹林",
  },
  {
    sourceSystem: "P5-HIS",
    localCode: "ZD0456",
    category: "DIAGNOSIS",
    localName: "社区获得性肺炎",
    normalizedName: "社区获得性肺炎|肺炎",
  },
];

function traceId(stage) {
  return `p5-act2-${stage}-${Date.now()}`;
}

// 与前端 standardApiContext 同口径的统一入参；铺底动作使用 AUTHORING 上下文版本。
async function loadStandardContext(context, stage) {
  const profile = await apiGet(context, "/security/me", `${stage}-profile`);
  if (!profile.ok) {
    throw new Error(`读取安全画像失败：${profile.status}`);
  }
  const data = profile.body.data;
  return () => ({
    request_id: crypto.randomUUID(),
    trace_id: traceId(stage),
    tenant_id: data.dataScope?.tenantId,
    group_id: data.dataScope?.groupId,
    hospital_id: data.dataScope?.hospitalId,
    campus_id: data.dataScope?.campusId,
    site_id: data.dataScope?.siteId,
    department_id: data.dataScope?.departmentId,
    specialty_id: data.dataScope?.specialtyId,
    user_id: data.userId,
    role_codes: data.roles.map((role) => role.code).filter(Boolean),
    package_version: "AUTHORING",
  });
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
  return value
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

async function apiPost(context, pathName, body, stage) {
  const cookies = await context.cookies(baseUrl);
  const xsrf = cookies.find((cookie) => cookie.name === "XSRF-TOKEN");
  const headers = {
    "Content-Type": "application/json",
    "X-Trace-Id": traceId(stage),
  };
  if (xsrf) {
    headers["X-XSRF-TOKEN"] = xsrf.value;
  }
  const response = await context.request.post(`${apiBase}${pathName}`, { data: body, headers });
  const text = await response.text();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    parsed = { raw: text.slice(0, 400) };
  }
  return { status: response.status(), ok: response.ok(), body: parsed };
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

async function gotoPath(page, route) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForQuiet(page);
}

async function bodyText(page) {
  return page.locator("body").innerText({ timeout: 8000 });
}

// 步骤一：API 铺底。标准字典由机构知识治理员装载；院内码与候选生成由医技协同人员执行，
// 模拟 HIS/LIS 同步与确定性候选生成，全部留 traceId。
async function seedTerminology(browser, credentials, summary) {
  const knowledge = await login(browser, requireAccount(credentials, "knowledge-governor"), "knowledge-governor-seed");
  const standardResults = [];
  try {
    const knowledgeContext = await loadStandardContext(knowledge.context, "std");
    for (const seed of standardTermSeeds) {
      const result = await apiPost(
        knowledge.context,
        "/engine/terminology/terms/standard",
        { ...knowledgeContext(), ...seed },
        "std",
      );
      standardResults.push({ termCode: seed.termCode, status: result.status, ok: result.ok });
      if (!result.ok) {
        summary.failures.push({
          step: `登记标准术语 ${seed.standardSystem} ${seed.termCode}`,
          detail: result.body,
        });
      }
    }
  } finally {
    await knowledge.context.close();
  }

  const diagnostic = await login(browser, requireAccount(credentials, "diagnostic-service-user"), "diagnostic-seed");
  const localResults = [];
  const generationResults = [];
  try {
    const diagnosticContext = await loadStandardContext(diagnostic.context, "local");
    for (const seed of localTermSeeds) {
      const result = await apiPost(
        diagnostic.context,
        "/engine/terminology/terms/local",
        { ...diagnosticContext(), ...seed },
        "local",
      );
      localResults.push({ localCode: seed.localCode, status: result.status, ok: result.ok });
      if (!result.ok) {
        summary.failures.push({
          step: `登记院内术语 ${seed.sourceSystem} ${seed.localCode}`,
          detail: result.body,
        });
      }
    }
    for (const sourceSystem of ["P5-LIS", "P5-HIS"]) {
      const result = await apiPost(
        diagnostic.context,
        "/engine/terminology/mappings/candidates",
        { ...diagnosticContext(), sourceSystem, semanticAssistEnabled: true },
        "gen",
      );
      generationResults.push({
        sourceSystem,
        status: result.status,
        ok: result.ok,
        generatedCount: result.body?.data?.generatedCount ?? null,
      });
      if (!result.ok) {
        summary.failures.push({ step: `生成候选 ${sourceSystem}`, detail: result.body });
      }
    }
    const candidates = await apiGet(
      diagnostic.context,
      "/engine/terminology/mappings/candidates?status=PENDING&page=0&size=50",
      "cand",
    );
    const conflicts = await apiGet(
      diagnostic.context,
      "/engine/terminology/mappings/conflicts?status=OPEN&page=0&size=20",
      "conf",
    );
    summary.seed = {
      standardResults,
      localResults,
      generationResults,
      pendingCandidates: candidates.body?.data?.items?.map((item) => ({
        id: item.id,
        riskLevel: item.riskLevel,
        highRiskFlag: item.highRiskFlag,
        conflictFlag: item.conflictFlag,
        semanticMatchScore: item.semanticMatchScore,
        evidenceText: item.evidenceText,
      })) ?? [],
      openConflicts: conflicts.body?.data?.items?.map((item) => ({
        id: item.id,
        conflictType: item.conflictType,
        riskLevel: item.riskLevel,
        description: item.description,
      })) ?? [],
    };
  } finally {
    await diagnostic.context.close();
  }
}

// 步骤二：医技协同人员前台走查候选确认链路。只观察并留证，不确认医学上错误的高危候选。
async function walkDiagnosticServiceUser(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "diagnostic-service-user"),
    "diagnostic-ui",
  );
  const steps = [];
  try {
    await gotoPath(page, "/terminology/mapping");
    await page.getByText("术语与字典", { exact: false }).first().waitFor({ timeout: 20000 });
    await waitForQuiet(page);
    steps.push(await capture(browser, page, "01-ui-terminology-overview-diagnostic.png", "医技协同人员打开术语与字典页"));

    const text = await bodyText(page);
    const observations = {
      highRiskAlertVisible: text.includes("高危近似"),
      batchDeniedHintVisible: text.includes("高危逐条确认，禁批量通过"),
      conflictPanelVisible: text.includes("冲突待裁"),
    };

    const confirmButton = page.getByRole("button", { name: "确认候选" }).first();
    const confirmEnabled = await confirmButton.isEnabled().catch(() => false);
    observations.confirmPrimaryEnabled = confirmEnabled;

    if (confirmEnabled) {
      await confirmButton.click();
      const modal = page.locator(".ant-modal:visible").last();
      await modal.waitFor({ timeout: 10000 });
      await waitForQuiet(page);
      steps.push(
        await capture(browser, page, "02-ui-terminology-confirm-modal-highrisk.png", "确认候选弹窗（核对当前队首候选与证据）"),
      );
      const modalText = await modal.innerText();
      observations.confirmModalTitle = modalText.includes("确认高危候选")
        ? "确认高危候选"
        : modalText.includes("确认普通候选")
          ? "确认普通候选"
          : "未识别";
      observations.confirmModalEvidence = modalText.slice(0, 400);
      // 队首高危候选为钾/钠互斥错配时，确认即医疗错误；此处只留证并取消，缺陷另行登记。
      const wrongPairOnTop = modalText.includes("钠");
      observations.queueHeadIsWrongPair = wrongPairOnTop;
      const cancel = modal.getByRole("button", { name: /取\s*消/ }).first();
      await cancel.click();
      await modal.waitFor({ state: "hidden", timeout: 10000 }).catch(() => undefined);
    }

    const rejectButtons = await page
      .getByRole("button", { name: /驳\s*回/ })
      .count()
      .catch(() => 0);
    observations.candidateRejectEntryCount = rejectButtons;

    const batchButton = page.getByRole("button", { name: "批量确认候选" }).first();
    observations.batchConfirmEnabled = await batchButton.isEnabled().catch(() => false);
    steps.push(
      await capture(browser, page, "03-ui-terminology-batch-and-conflicts.png", "候选批量确认按钮状态与冲突待裁面板"),
    );

    // 修复后旅程：候选行提供驳回入口时，前台驳回钾/钠错配高危候选，再批量确认普通候选。
    if (rejectButtons > 0) {
      const wrongPairRow = page.locator("tr").filter({ hasText: "钾/钠高危近似" }).first();
      if ((await wrongPairRow.count()) > 0) {
        await wrongPairRow.getByRole("button", { name: /驳\s*回/ }).click();
        const rejectModal = page.locator(".ant-modal:visible").last();
        await rejectModal.waitFor({ timeout: 10000 });
        await rejectModal
          .getByLabel("驳回理由")
          .fill("钾/钠互斥高危错配：院内血钾不得映射到标准血清钠，驳回该候选");
        steps.push(
          await capture(browser, page, "08-ui-terminology-reject-highrisk-modal.png", "驳回钾/钠错配高危候选（理由必填）"),
        );
        await rejectModal.getByRole("button", { name: "提交驳回" }).click();
        await rejectModal.waitFor({ state: "hidden", timeout: 15000 }).catch(() => undefined);
        await waitForQuiet(page);
        observations.wrongPairRejected = true;
        steps.push(
          await capture(browser, page, "09-ui-terminology-after-reject.png", "驳回后候选队列只剩普通候选"),
        );
      }

      const batchAfterReject = page.getByRole("button", { name: "批量确认候选" }).first();
      if (await batchAfterReject.isEnabled().catch(() => false)) {
        await batchAfterReject.click();
        await waitForQuiet(page);
        await page.waitForTimeout(1500);
        observations.ordinaryBatchConfirmed = true;
        steps.push(
          await capture(browser, page, "10-ui-terminology-after-batch-confirm.png", "批量确认普通候选后的映射台账"),
        );
      }

      // 队列处置后留正确高危候选的单条确认路径验证：若仍有高危候选（正确配对），逐条确认。
      const confirmAfter = page.getByRole("button", { name: "确认候选" }).first();
      if ((await page.locator("tr").filter({ hasText: "高危" }).count()) > 0
          && (await confirmAfter.isEnabled().catch(() => false))) {
        observations.residualHighRiskRemaining = true;
      }

      const mappingsAfter = await apiGet(
        context,
        "/engine/terminology/mappings?status=CONFIRMED&page=1&size=50",
        "post",
      );
      const candidatesAfter = await apiGet(
        context,
        "/engine/terminology/mappings/candidates?status=PENDING&page=0&size=50",
        "post",
      );
      observations.confirmedMappingsAfter = mappingsAfter.body?.data?.total ?? null;
      observations.pendingCandidatesAfter = candidatesAfter.body?.data?.total ?? null;
    }

    summary.diagnosticServiceUser = { observations, steps };
  } finally {
    await context.close();
  }
  return steps;
}

// 步骤三：机构知识治理员核对映射包构建/发布入口状态（确认前置依赖与按钮可用性）。
async function walkKnowledgeGovernor(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-ui",
  );
  const steps = [];
  try {
    await gotoPath(page, "/terminology/mapping");
    await page.getByText("术语与字典", { exact: false }).first().waitFor({ timeout: 20000 });
    await waitForQuiet(page);
    const buildEnabled = await page
      .getByRole("button", { name: "构建映射包" })
      .first()
      .isEnabled()
      .catch(() => false);
    steps.push(
      await capture(browser, page, "04-ui-terminology-package-actions-governor.png", "机构知识治理员视角：映射包构建/发布入口"),
    );

    const observations = { buildPackageEnabled: buildEnabled };

    // 修复后旅程：已有确认映射时由机构知识治理员前台构建映射包草稿。
    const packageAlready = await page.getByText("TERM.P5.MAPPING", { exact: false }).count();
    if (buildEnabled && packageAlready === 0) {
      await page.getByRole("button", { name: "构建映射包" }).click();
      const buildModal = page.locator(".ant-modal:visible").last();
      await buildModal.waitFor({ timeout: 10000 });
      await buildModal.getByLabel("包编码").fill("TERM.P5.MAPPING");
      await buildModal.getByLabel("新版本").fill("2026.0613.1");
      await buildModal.getByLabel("名称").fill("P5第二轮演练术语映射包");
      steps.push(
        await capture(browser, page, "11-ui-terminology-build-package-form.png", "构建术语映射包表单（包编码/版本/范围）"),
      );
      await buildModal.getByRole("button", { name: "创建草稿" }).click();
      await buildModal.waitFor({ state: "hidden", timeout: 15000 }).catch(() => undefined);
      await waitForQuiet(page);
      // 构建成功必须以服务端事实为准：回查包列表确认草稿真实落库，
      // 不允许把“模态框关闭”当成功（曾把 ENG-API-007 失败误记为通过）。
      const packagesAfterBuild = await apiGet(context, "/engine/pkg/packages?page=0&size=20", "pkg-verify");
      const builtItems = packagesAfterBuild.body?.data?.items ?? [];
      observations.packageBuilt = builtItems.some((item) => item.packageCode === "TERM.P5.MAPPING");
      if (!observations.packageBuilt) {
        summary.failures.push({
          step: "构建映射包草稿 TERM.P5.MAPPING（服务端回查未找到）",
          detail: { status: packagesAfterBuild.status, itemCount: builtItems.length },
        });
      }
      steps.push(
        await capture(browser, page, "12-ui-terminology-package-built.png", "映射包草稿构建后的发布面板（服务端回查为准）"),
      );
    }

    observations.publishPackageEnabled = await page
      .getByRole("button", { name: "发布映射包" })
      .first()
      .isEnabled()
      .catch(() => false);
    const pageText = await bodyText(page);
    observations.packagePanelVisible = pageText.includes("映射包发布");

    // 发布同步通道前置：读取当前可用发布适配器，全新机构未接入时如实记录为 0。
    const releaseAdapters = await apiGet(context, "/engine/pkg/packages/release-adapters", "adapters");
    const adapterList = releaseAdapters.body?.data ?? [];
    observations.releaseAdapterCount = Array.isArray(adapterList) ? adapterList.length : null;
    observations.usableReleaseAdapterCount = Array.isArray(adapterList)
      ? adapterList.filter(
          (item) =>
            item.status === "ACTIVE" && item.healthStatus === "HEALTHY" && item.connectorAvailable,
        ).length
      : null;

    summary.knowledgeGovernor = { observations, steps };
  } finally {
    await context.close();
  }
  return steps;
}

// 步骤四：机构管理员核对术语页路由边界与配置包页的全量发布承载。
async function walkOrganizationAdmin(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "organization-admin"),
    "orgadmin-ui",
  );
  const steps = [];
  try {
    await gotoPath(page, "/terminology/mapping");
    const terminologyText = await bodyText(page);
    const forbidden = terminologyText.includes("当前权限不足");
    steps.push(
      await capture(browser, page, "05-ui-terminology-orgadmin-boundary.png", "机构管理员访问术语与字典页的路由边界"),
    );

    await gotoPath(page, "/tenant/packages");
    await page.getByText("配置包与发布", { exact: false }).first().waitFor({ timeout: 20000 });
    await waitForQuiet(page);
    const packagesText = await bodyText(page);
    steps.push(
      await capture(browser, page, "06-ui-config-packages-orgadmin.png", "机构管理员打开配置包与发布页（全量发布承载页）"),
    );
    summary.organizationAdmin = {
      observations: {
        terminologyForbidden: forbidden,
        configPackagesAccessible: packagesText.includes("配置包与发布"),
        fullReleaseHintVisible: packagesText.includes("全量发布仅院级管理员可直接触发"),
      },
      steps,
    };
  } finally {
    await context.close();
  }
  return steps;
}

// 步骤五：集成运维员核对系统接入页：发布同步通道的前置（全新机构暂无适配器）。
async function walkIntegrationOperator(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "integration-operator"),
    "integration-ui",
  );
  const steps = [];
  try {
    await gotoPath(page, "/adapter/hub");
    await page.getByText("系统接入", { exact: false }).first().waitFor({ timeout: 20000 });
    await waitForQuiet(page);
    const createEnabled = await page
      .getByRole("button", { name: "新增适配器" })
      .first()
      .isEnabled()
      .catch(() => false);
    steps.push(
      await capture(browser, page, "07-ui-adapter-hub-integration.png", "集成运维员打开系统接入页（发布同步通道前置）"),
    );
    summary.integrationOperator = {
      observations: { createAdapterEnabled: createEnabled },
      steps,
    };
  } finally {
    await context.close();
  }
  return steps;
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const credentials = await loadCredentials();
  const browser = await chromium.launch({ headless: true });
  const summary = {
    act: "P5幕2-术语与字典（跨角色）",
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    credentialSource: credentialPath,
    failures: [],
  };
  try {
    await seedTerminology(browser, credentials, summary);
    await walkDiagnosticServiceUser(browser, credentials, summary);
    await walkKnowledgeGovernor(browser, credentials, summary);
    await walkOrganizationAdmin(browser, credentials, summary);
    await walkIntegrationOperator(browser, credentials, summary);
  } finally {
    await browser.close();
  }

  await writeFile(
    path.join(evidenceDir, "00-act2-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
  );
  console.log(
    JSON.stringify(
      {
        generatedAt: summary.generatedAt,
        environment: summary.environment,
        failures: summary.failures.length,
        pendingCandidates: summary.seed?.pendingCandidates?.length ?? 0,
        openConflicts: summary.seed?.openConflicts?.length ?? 0,
        diagnosticObservations: summary.diagnosticServiceUser?.observations ?? null,
        knowledgeGovernorObservations: summary.knowledgeGovernor?.observations ?? null,
        organizationAdminObservations: summary.organizationAdmin?.observations ?? null,
        integrationOperatorObservations: summary.integrationOperator?.observations ?? null,
      },
      null,
      2,
    ),
  );
}

await main();
