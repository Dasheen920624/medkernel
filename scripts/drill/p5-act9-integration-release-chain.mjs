#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕9 系统接入（前置）＋ 幕2 遗留回收（映射包跨角色发布链）。
// 剧本动作：
//   1. 集成运维员真实前台 /adapter/hub 新增 REST 适配器（指向 134 本机模拟第三方接收端
//      scripts/drill/mock-third-party-receiver.py），并完成健康诊断到 HEALTHY。
//   2. 机构知识治理员真实前台 /terminology/mapping 以 10% 灰度发布映射包 TERM.P5.MAPPING，
//      同步通道选用上一步的健康适配器。
//   3. 机构管理员真实前台 /config/packages 「院内同步发布」以 FULL 全量激活该包。
//   4. P5-ACT2-04 部署复验：机构知识治理员重复构建同名同版本映射包，前台必须出现可见
//      错误提示且弹窗保持打开，不允许静默吞错。
// 成功判定一律以服务端回查为准（/engine/integration/adapters、/engine/pkg/packages），
// 接收端落盘 JSONL 证据由部署侧在 134 上单独采集归档。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕9-系统接入与发布链/
//   00-act9-summary.json 与 NN-ui-*.png（全部带 URL 栏）。
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕9-系统接入与发布链",
);

// 模拟第三方接收端运行在 134 本机回环地址，由部署侧 systemd 拉起后再执行本脚本。
const receiverBaseUrl = process.env.DRILL_RECEIVER_BASE_URL ?? "http://127.0.0.1:9301";
const adapterId = "p5-his-gateway";
const adapterName = "P5模拟院内HIS网关";
const packageCode = "TERM.P5.MAPPING";

function traceId(stage) {
  return `p5-act9-${stage}-${Date.now()}`;
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

// 选择 antd 下拉选项，并以表单项内出现选中标签为准确认成功，
// 规避下拉展开动画期间点击丢失的竞态；必要时用键盘确认活动项兜底。
async function chooseSelectOption(page, scope, controlId, optionText) {
  const formItem = scope.locator(`.ant-form-item:has(#${controlId})`);
  await formItem.locator(".ant-select-selector").click();
  await page.waitForTimeout(500);
  // 选项定位收窄到当前可见下拉，避免命中此前已隐藏下拉里的同类节点。
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
  // 单选下拉选中后自动收起，此时再按 Escape 会误关外层弹窗；仅在下拉仍可见时收起它。
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

async function findAdapter(context, stage) {
  const adapters = await apiGet(context, "/engine/integration/adapters", stage);
  const items = adapters.body?.data?.items ?? adapters.body?.data ?? [];
  const list = Array.isArray(items) ? items : (items.items ?? []);
  return {
    status: adapters.status,
    adapter: list.find((item) => item.adapterId === adapterId),
  };
}

async function findPackages(context, stage) {
  const response = await apiGet(context, "/engine/pkg/packages?page=0&size=50", stage);
  const items = response.body?.data?.items ?? [];
  return {
    status: response.status,
    packages: items.filter((item) => item.packageCode === packageCode),
  };
}

// 步骤一：集成运维员前台完成系统接入：新增适配器并健康诊断到 HEALTHY。
async function walkIntegrationOperator(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "integration-operator"),
    "integration-operator",
  );
  const steps = [];
  const observations = {};
  try {
    await gotoPath(page, "/adapter/hub");
    steps.push(await capture(browser, page, "01-ui-adapter-hub-before.png", "系统接入页（接入前）"));

    const existing = await findAdapter(context, "adapter-precheck");
    observations.adapterAlreadyExists = Boolean(existing.adapter);
    if (!existing.adapter) {
      await page.getByRole("button", { name: "新增适配器" }).click();
      const modal = page.locator(".ant-modal-content", { hasText: "新增适配器" });
      await modal.waitFor({ state: "visible", timeout: 10000 });
      await modal.getByLabel("适配器标识").fill(adapterId);
      await modal.getByLabel("系统名称").fill(adapterName);
      await modal.getByLabel("服务地址").fill(receiverBaseUrl);
      await modal.getByLabel("来源字段路径").fill("/package/code");
      await modal.getByLabel("标准字段路径").fill("/package/code");
      steps.push(
        await capture(browser, page, "02-ui-adapter-create-form.png", "新增适配器表单（指向模拟第三方接收端）"),
      );
      await modal.getByRole("button", { name: "提交适配器" }).click();
      await modal.waitFor({ state: "hidden", timeout: 15000 }).catch(() => undefined);
      await waitForQuiet(page);
    }

    // 服务端回查：适配器真实落库且属于当前租户。
    const created = await findAdapter(context, "adapter-created");
    observations.adapterCreated = Boolean(created.adapter);
    observations.adapterStatus = created.adapter?.status ?? null;
    if (!created.adapter) {
      summary.failures.push({ step: "新增适配器（服务端回查未找到）", detail: { status: created.status } });
      return;
    }

    // 健康诊断必须真实命中模拟接收端 /health。
    const row = page.getByRole("row", { name: new RegExp(adapterId) });
    await row.getByRole("button", { name: "健康诊断" }).click();
    await page.waitForTimeout(2500);
    steps.push(
      await capture(browser, page, "03-ui-adapter-health-check.png", "适配器健康诊断结果"),
    );

    const afterHealth = await findAdapter(context, "adapter-health");
    observations.healthStatus = afterHealth.adapter?.healthStatus ?? null;
    observations.adapterHealthy = afterHealth.adapter?.healthStatus === "HEALTHY";
    if (!observations.adapterHealthy) {
      summary.failures.push({
        step: "适配器健康诊断未达 HEALTHY",
        detail: { healthStatus: observations.healthStatus },
      });
    }
  } finally {
    summary.integrationOperator = { observations, steps };
    await context.close();
  }
}

// 步骤二：机构知识治理员前台构建映射包并灰度发布（同步通道=健康适配器）。
// 构建先按弹窗默认的最窄范围提交：铺底院内码无科室归属，该范围必然 409
// 「当前范围没有已确认映射」，这正是 P5-ACT2-04 修复前被静默吞掉的真实失败路径，
// 必须看到可见报错且弹窗不误关；随后改选「当前服务空间」完成真实构建。
async function walkKnowledgeGovernorGrayRelease(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor",
  );
  const steps = [];
  const observations = {};
  try {
    const before = await findPackages(context, "pkg-before-build");
    observations.packageVersionsBefore = before.packages.map((item) => ({
      packageVersion: item.packageVersion,
      status: item.status,
    }));
    // 状态感知幂等：灰度（PUBLISHED）或全量（ACTIVE）已完成时不得重复构建/发布。
    const alreadyReleased = before.packages.find(
      (item) => item.status === "PUBLISHED" || item.status === "ACTIVE",
    );
    if (alreadyReleased) {
      observations.draftVersion = alreadyReleased.packageVersion;
      observations.statusAfterGrayRelease = alreadyReleased.status;
      observations.grayReleased = true;
      observations.grayAlreadyDoneBefore = true;
      return;
    }
    let draft = before.packages.find((item) => item.status === "DRAFT");
    if (!draft) {
      await gotoPath(page, "/terminology/mapping");
      await page.getByRole("button", { name: "构建映射包" }).click();
      const buildModal = page.locator(".ant-modal-content", { hasText: "构建术语映射包" });
      await buildModal.waitFor({ state: "visible", timeout: 10000 });
      await buildModal.getByLabel("包编码").fill(packageCode);
      await buildModal.getByLabel("新版本").fill("2026.06.1");
      await buildModal.getByLabel("名称").fill("P5 检验与诊断字典映射包");
      await buildModal.getByRole("button", { name: "创建草稿" }).click();

      // 默认最窄范围下的失败必须可见可追责（P5-ACT2-04 现场复验第一段）。
      const errorMessage = page.locator(".ant-message-error, .ant-message-notice-error");
      await errorMessage.first().waitFor({ state: "visible", timeout: 10000 });
      observations.narrowScopeErrorVisible = await errorMessage.first().isVisible();
      observations.narrowScopeErrorText = await page
        .locator(".ant-message")
        .innerText()
        .catch(() => null);
      observations.narrowScopeModalStillOpen = await buildModal.isVisible();
      steps.push(
        await capture(
          browser,
          page,
          "04a-ui-terminology-build-narrow-scope-error.png",
          "默认最窄范围构建失败的可见错误（P5-ACT2-04 复验）",
        ),
      );
      if (!observations.narrowScopeErrorVisible || !observations.narrowScopeModalStillOpen) {
        summary.failures.push({
          step: "P5-ACT2-04 复验失败：窄范围构建错误不可见或弹窗被误关",
          detail: observations,
        });
        return;
      }

      // 改选「当前服务空间」范围后真实构建草稿。
      await buildModal.locator(".ant-form-item:has(#scopeKey) .ant-select-selector").click();
      await page.locator(".ant-select-item-option", { hasText: "当前服务空间" }).first().click();
      await buildModal.getByRole("button", { name: "创建草稿" }).click();
      await buildModal.waitFor({ state: "hidden", timeout: 15000 }).catch(() => undefined);
      await waitForQuiet(page);
      steps.push(
        await capture(browser, page, "04b-ui-terminology-built-tenant-scope.png", "服务空间范围构建草稿后的发布面板"),
      );
      const afterBuild = await findPackages(context, "pkg-after-build");
      draft = afterBuild.packages.find((item) => item.status === "DRAFT");
      observations.packageBuilt = Boolean(draft);
      if (!draft) {
        summary.failures.push({
          step: "服务空间范围构建草稿失败（服务端回查未找到 DRAFT）",
          detail: afterBuild.packages,
        });
        return;
      }
    }
    observations.draftVersion = draft.packageVersion;

    // 目标组织使用机构组织树中的真实组织单元。
    const orgUnits = await apiGet(context, "/engine/org/org-units?page=1&size=100&sort=level,asc", "org-units");
    const orgItems = orgUnits.body?.data?.items ?? [];
    const targetOrg = orgItems.find((item) => item.status === "ACTIVE" && item.id);
    observations.targetOrgUnitId = targetOrg?.id ?? null;
    if (!targetOrg) {
      summary.failures.push({ step: "组织树中没有可用的目标组织", detail: { status: orgUnits.status } });
      return;
    }

    await gotoPath(page, "/terminology/mapping");
    const publishButton = page.getByRole("button", { name: "发布映射包" });
    // 页面查询（包列表/发布适配器）异步加载完成前按钮短暂禁用，轮询等待而非瞬时判定。
    observations.publishEnabled = false;
    for (let attempt = 0; attempt < 20; attempt += 1) {
      if (await publishButton.isEnabled().catch(() => false)) {
        observations.publishEnabled = true;
        break;
      }
      await page.waitForTimeout(500);
    }
    steps.push(
      await capture(browser, page, "04-ui-terminology-before-publish.png", "术语页发布入口（适配器健康后）"),
    );
    if (!observations.publishEnabled) {
      summary.failures.push({ step: "发布映射包按钮未启用（适配器已健康仍禁用）", detail: {} });
      return;
    }

    await publishButton.click();
    const modal = page.locator(".ant-modal-content", { hasText: "发布映射包流程" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByLabel("目标组织").fill(String(targetOrg.id));
    const channelChosen = await chooseSelectOption(page, modal, "adapterIds", adapterName);
    observations.channelChosen = channelChosen;
    if (!channelChosen) {
      summary.failures.push({ step: "同步通道未能选中健康适配器", detail: { adapterName } });
      return;
    }
    await modal.getByLabel("发布原因").fill("幕9前置完成系统接入后，按 10% 灰度发布术语映射包验证发布同步链");
    steps.push(await capture(browser, page, "05-ui-terminology-publish-form.png", "灰度发布表单"));
    await modal.getByRole("button", { name: "提交发布" }).click();
    await modal.waitFor({ state: "hidden", timeout: 20000 }).catch(() => undefined);
    await waitForQuiet(page);
    steps.push(
      await capture(browser, page, "06-ui-terminology-after-gray-release.png", "灰度发布提交后的发布面板"),
    );

    // 服务端回查：灰度后包状态离开 DRAFT。
    const after = await findPackages(context, "pkg-after-gray");
    const released = after.packages.find((item) => item.packageVersion === draft.packageVersion);
    observations.statusAfterGrayRelease = released?.status ?? null;
    observations.grayReleased = Boolean(released && released.status !== "DRAFT");
    if (!observations.grayReleased) {
      summary.failures.push({
        step: "灰度发布后包状态未变化（服务端回查仍为 DRAFT 或缺失）",
        detail: { packages: after.packages },
      });
    }
  } finally {
    summary.knowledgeGovernor = { observations, steps };
    await context.close();
  }
}

// 步骤三：机构管理员前台 /config/packages 以 FULL 全量激活映射包。
async function walkOrganizationAdminFullRelease(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "organization-admin"),
    "organization-admin",
  );
  const steps = [];
  const observations = {};
  try {
    const before = await findPackages(context, "pkg-before-full");
    const alreadyActive = before.packages.find((item) => item.status === "ACTIVE");
    if (alreadyActive) {
      observations.versionForFullRelease = alreadyActive.packageVersion;
      observations.statusAfterFullRelease = "ACTIVE";
      observations.fullReleased = true;
      observations.fullAlreadyDoneBefore = true;
      return;
    }
    const releasable = before.packages.find(
      (item) => item.status === "PUBLISHED" || item.status === "DRAFT",
    );
    observations.versionForFullRelease = releasable?.packageVersion ?? null;
    if (!releasable) {
      summary.failures.push({ step: "没有可全量发布的映射包版本", detail: before.packages });
      return;
    }

    await gotoPath(page, "/config/packages");
    steps.push(
      await capture(browser, page, "07-ui-config-packages-orgadmin.png", "配置包页（机构管理员，全量发布前）"),
    );
    const row = page.getByRole("row", { name: new RegExp(packageCode.replaceAll(".", "\\.")) }).first();
    await row.getByRole("button", { name: "院内同步发布" }).click();
    const modal = page.locator(".ant-modal-content", { hasText: "发布投放策略" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByText("全量发布 (FULL)").click();
    await modal.getByLabel("发布说明").fill("灰度观察无异常，机构管理员批准全量激活术语映射包");
    const fullChannelChosen = await chooseSelectOption(page, modal, "adapterIds", adapterName);
    const orgChosen = await chooseSelectOption(page, modal, "targetOrgUnitId", null);
    observations.fullChannelChosen = fullChannelChosen;
    observations.orgChosen = orgChosen;
    if (!fullChannelChosen || !orgChosen) {
      summary.failures.push({
        step: "全量发布表单未能选中适配器或接收组织",
        detail: { fullChannelChosen, orgChosen },
      });
      return;
    }
    steps.push(await capture(browser, page, "08-ui-config-packages-full-form.png", "全量发布表单"));
    await modal.getByRole("button", { name: /开始同步发布/ }).click();
    await page.waitForTimeout(4000);
    steps.push(
      await capture(browser, page, "09-ui-config-packages-after-full.png", "全量发布执行结果（含同步日志）"),
    );

    // 服务端回查：全量后该版本 ACTIVE。
    const after = await findPackages(context, "pkg-after-full");
    const active = after.packages.find((item) => item.packageVersion === releasable.packageVersion);
    observations.statusAfterFullRelease = active?.status ?? null;
    observations.fullReleased = active?.status === "ACTIVE";
    if (!observations.fullReleased) {
      summary.failures.push({
        step: "全量发布后包状态未达 ACTIVE（服务端回查）",
        detail: { packages: after.packages },
      });
    }
  } finally {
    summary.organizationAdmin = { observations, steps };
    await context.close();
  }
}

// 步骤四：P5-ACT2-04 部署复验——重复构建同名同版本映射包，前台必须出现可见错误且弹窗不误关。
async function verifySilentErrorFixed(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-act204",
  );
  const steps = [];
  const observations = {};
  try {
    const existing = await findPackages(context, "pkg-before-dup");
    const duplicated = existing.packages[0];
    observations.duplicateTargetVersion = duplicated?.packageVersion ?? null;
    if (!duplicated) {
      summary.failures.push({ step: "缺少已存在的映射包版本，无法复验重复构建", detail: {} });
      return;
    }

    await gotoPath(page, "/terminology/mapping");
    await page.getByRole("button", { name: "构建映射包" }).click();
    const modal = page.locator(".ant-modal-content", { hasText: "构建术语映射包" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByLabel("新版本").fill(duplicated.packageVersion);
    await modal.getByRole("button", { name: "创建草稿" }).click();

    // 可见错误提示必须出现，弹窗必须保持打开。
    const messageLocator = page.locator(".ant-message-error, .ant-message-notice-error");
    await messageLocator.first().waitFor({ state: "visible", timeout: 10000 });
    observations.visibleErrorShown = await messageLocator.first().isVisible();
    observations.visibleErrorText = await page
      .locator(".ant-message")
      .innerText()
      .catch(() => null);
    observations.modalStillOpen = await modal.isVisible();
    steps.push(
      await capture(browser, page, "10-ui-terminology-duplicate-build-error.png", "重复构建映射包的可见错误提示（P5-ACT2-04 复验）"),
    );
    if (!observations.visibleErrorShown || !observations.modalStillOpen) {
      summary.failures.push({ step: "P5-ACT2-04 复验失败：错误不可见或弹窗被误关", detail: observations });
    }

    // 服务端回查：包数量不应因失败构建而变化。
    const after = await findPackages(context, "pkg-after-dup");
    observations.packageCountUnchanged = after.packages.length === existing.packages.length;
    if (!observations.packageCountUnchanged) {
      summary.failures.push({
        step: "重复构建后包数量异常变化",
        detail: { before: existing.packages.length, after: after.packages.length },
      });
    }
  } finally {
    summary.silentErrorFixVerification = { observations, steps };
    await context.close();
  }
}

// 步骤五：P5-ACT2-04 部署复验补充——按弹窗默认最窄范围用全新版本号构建，
// 服务端按 409「当前范围没有已确认映射」拒绝（铺底院内码无科室归属），
// 前台必须出现可见报错且弹窗不误关；失败构建不得产生任何落库副作用。
async function verifyNarrowScopeBuildErrorVisible(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor-narrow",
  );
  const steps = [];
  const observations = {};
  try {
    const existing = await findPackages(context, "pkg-before-narrow");
    await gotoPath(page, "/terminology/mapping");
    await page.getByRole("button", { name: "构建映射包" }).click();
    const modal = page.locator(".ant-modal-content", { hasText: "构建术语映射包" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByLabel("新版本").fill("2026.06.2");
    await modal.getByRole("button", { name: "创建草稿" }).click();

    const messageLocator = page.locator(".ant-message-error, .ant-message-notice-error");
    await messageLocator.first().waitFor({ state: "visible", timeout: 10000 });
    observations.visibleErrorShown = await messageLocator.first().isVisible();
    observations.visibleErrorText = await page
      .locator(".ant-message")
      .innerText()
      .catch(() => null);
    observations.modalStillOpen = await modal.isVisible();
    steps.push(
      await capture(
        browser,
        page,
        "11-ui-terminology-narrow-scope-error.png",
        "默认最窄范围构建失败的可见错误（P5-ACT2-04 复验·窄范围路径）",
      ),
    );
    if (!observations.visibleErrorShown || !observations.modalStillOpen) {
      summary.failures.push({
        step: "P5-ACT2-04 窄范围构建复验失败：错误不可见或弹窗被误关",
        detail: observations,
      });
    }

    const after = await findPackages(context, "pkg-after-narrow");
    observations.packageCountUnchanged = after.packages.length === existing.packages.length;
    if (!observations.packageCountUnchanged) {
      summary.failures.push({
        step: "窄范围失败构建产生了落库副作用",
        detail: { before: existing.packages.length, after: after.packages.length },
      });
    }
  } finally {
    summary.narrowScopeBuildVerification = { observations, steps };
    await context.close();
  }
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const summary = {
    act: "P5幕9-系统接入与发布链（含P5-ACT2-04部署复验）",
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    receiver: receiverBaseUrl,
    credentialSource: credentialPath,
    failures: [],
  };
  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    const walks = [
      walkIntegrationOperator,
      walkKnowledgeGovernorGrayRelease,
      walkOrganizationAdminFullRelease,
      verifySilentErrorFixed,
      verifyNarrowScopeBuildErrorVisible,
    ];
    for (const walk of walks) {
      if (summary.failures.length > 0) break;
      try {
        await walk(browser, credentials, summary);
      } catch (error) {
        summary.failures.push({ step: `${walk.name} 异常中断`, detail: String(error).slice(0, 500) });
      }
    }
  } finally {
    await browser.close();
  }
  await writeFile(
    path.join(evidenceDir, "00-act9-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  if (summary.failures.length > 0) {
    console.error("幕9 发布链旅程存在失败步骤：");
    for (const failure of summary.failures) {
      console.error(`- ${failure.step}`);
    }
    process.exitCode = 1;
    return;
  }
  console.log("幕9 发布链旅程全部通过，证据已写入", evidenceDir);
}

await main();
