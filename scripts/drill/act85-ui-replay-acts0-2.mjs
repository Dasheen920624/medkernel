#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
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
  act0: path.join(evidenceRoot, "幕0-部署接管与首次登录/ui-replay"),
  act1: path.join(evidenceRoot, "幕1-租户组织与用户/ui-replay"),
  act2: path.join(evidenceRoot, "幕2-字典与术语对照/ui-replay"),
};

const actors = {
  hospitalAdmin: "drill-hospital-20260611:drill-hospital-admin-20260611",
  itOps: "drill-hospital-20260611:drill-it-ops-20260611",
  doctor: "drill-hospital-20260611:drill-cardiology-doctor-20260611",
};

const businessObjects = {
  orgCode: "dept-act85-collab-20260611",
  orgName: "演练·幕8.5门诊协同科",
  observerUserId: "external-act85-observer-20260611",
  observerName: "演练·幕8.5观察员",
  observerRoleName: "临床医生",
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

function stableEvidenceId(input) {
  return createHash("sha256").update(input).digest("hex").slice(0, 16);
}

async function ensureDirs() {
  await Promise.all(Object.values(actDirs).map((dir) => mkdir(dir, { recursive: true })));
}

async function waitForReady(page, text, timeout = 20000) {
  await page.getByText(text, { exact: false }).first().waitFor({ timeout });
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
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
  const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await page.setContent(html, { waitUntil: "load" });
  await page.screenshot({ path: finalPath, fullPage: true });
  await page.close();
  await rm(rawPath, { force: true });
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function capture(browser, page, dir, filename, label) {
  const finalPath = path.join(dir, filename);
  const rawPath = path.join(dir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  return {
    screenshot: path.relative(dir, finalPath),
    url: page.url(),
    label,
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
  await page.goto(`${baseUrl}/login`);
  await waitForReady(page, "登录工作台");
  await page.getByLabel("工号 / 账号").fill(actor.username);
  await page.getByLabel("密码").fill(actor.currentPassword);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 20000 });
  await waitForQuiet(page);
  return { context, page, actor: publicActor(actor) };
}

async function gotoAndWait(page, route, text) {
  await page.goto(`${baseUrl}${route}`);
  await waitForReady(page, text);
  await waitForQuiet(page);
}

async function waitForTerminologyData(page) {
  await page.getByText("候选映射", { exact: true }).waitFor({ timeout: 20000 });
  await page
    .getByText("正在加载", { exact: false })
    .waitFor({ state: "hidden", timeout: 20000 })
    .catch(() => undefined);
  await page.waitForTimeout(800);
}

async function chooseAntdSelect(formItemLabel, optionText, page) {
  const item = page.locator(".ant-form-item").filter({ hasText: formItemLabel }).first();
  await item.locator(".ant-select-selector").click();
  await page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option")
    .filter({ hasText: optionText })
    .first()
    .click();
}

async function closeModal(page, titleText) {
  const modal = page.locator(".ant-modal").filter({ hasText: titleText }).last();
  const cancel = modal.getByRole("button", { name: /取\s*消|取消/ }).first();
  if ((await cancel.count()) > 0) {
    await cancel.click();
  } else {
    await modal.locator(".ant-modal-close").click();
  }
  await modal.waitFor({ state: "hidden", timeout: 10000 }).catch(() => undefined);
}

async function findTableRow(page, text, maxPages = 4) {
  for (let i = 0; i < maxPages; i += 1) {
    if ((await page.getByText(text, { exact: false }).count()) > 0) return true;
    const next = page.locator(".ant-pagination-next:not(.ant-pagination-disabled)").first();
    if ((await next.count()) === 0) return false;
    await next.click();
    await waitForQuiet(page);
  }
  return (await page.getByText(text, { exact: false }).count()) > 0;
}

async function maybeCreateOrg(page) {
  await gotoAndWait(page, "/tenant/onboarding", "租户实施配置");
  const alreadyVisible = await findTableRow(page, businessObjects.orgName, 3);
  if (alreadyVisible) {
    return { status: "already_present", note: "组织树中已存在本次复演科室" };
  }

  await chooseAntdSelect("组织层级", "科室", page);
  await page.getByLabel("组织编码").fill(businessObjects.orgCode);
  await page.getByLabel("组织名称").fill(businessObjects.orgName);
  await chooseAntdSelect("直接上级", "演练总医院", page);
  await page.getByLabel("专病适用维度").fill("门诊协同演练");
  await page.getByRole("button", { name: "保存组织节点" }).click();
  const result = await Promise.race([
    page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "组织节点已创建" })
      .first()
      .waitFor({ timeout: 10000 })
      .then(() => "created"),
    page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "组织节点创建失败" })
      .first()
      .waitFor({ timeout: 10000 })
      .then(() => "failed"),
  ]).catch(() => "unknown");
  await waitForQuiet(page);
  return {
    status: result,
    note:
      result === "created"
        ? "通过租户实施配置页保存组织节点"
        : "页面未返回创建成功，按前台结果保留缺口/重复校验证据",
  };
}

async function maybeCreateExternalUser(page) {
  await gotoAndWait(page, "/admin/users", "用户管理");
  const exists = await findTableRow(page, businessObjects.observerName, 3);
  if (exists) {
    return { status: "already_present", note: "用户列表中已存在本次复演观察员" };
  }

  await page.getByRole("button", { name: "新建用户" }).click();
  await page.locator(".ant-modal").filter({ hasText: "新建用户" }).waitFor({ timeout: 10000 });
  await page.getByText("外部身份", { exact: true }).click();
  await page.getByLabel("显示名称").fill(businessObjects.observerName);
  await page.getByLabel("用户标识").fill(businessObjects.observerUserId);
  await chooseAntdSelect("初始角色", businessObjects.observerRoleName, page);
  await page.getByRole("button", { name: "创建" }).click();
  const result = await Promise.race([
    page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "外部身份用户已创建" })
      .first()
      .waitFor({ timeout: 10000 })
      .then(() => "created"),
    page
      .locator(".ant-message-notice-content")
      .filter({ hasText: "用户创建失败" })
      .first()
      .waitFor({ timeout: 10000 })
      .then(() => "failed"),
  ]).catch(() => "unknown");
  await waitForQuiet(page);
  return { status: result === "duplicate" ? "already_present" : result, note: "通过用户管理页创建外部身份用户并选择初始角色" };
}

async function openUserDetailIfVisible(page) {
  await gotoAndWait(page, "/admin/users", "用户管理");
  const found = await findTableRow(page, businessObjects.observerName, 4);
  if (!found) return { opened: false, note: "分页内未定位到新建观察员，截图保留用户管理页总览" };
  const button = page.getByRole("button", {
    name: new RegExp(`查看 ${businessObjects.observerUserId}`),
  });
  if ((await button.count()) === 0) return { opened: false, note: "列表行可见但详情按钮未按用户标识暴露" };
  await button.first().click();
  await page.getByText("角色与数据范围").waitFor({ timeout: 10000 });
  await waitForQuiet(page);
  return { opened: true, note: "已打开观察员详情抽屉核对角色范围" };
}

async function captureAct0(browser, credentials) {
  const dir = actDirs.act0;
  const steps = [];

  const anonymous = await browser.newPage({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  await anonymous.goto(`${baseUrl}/login`);
  await waitForReady(anonymous, "登录工作台");
  steps.push({
    _uiStep: "幕0-01",
    role: "未登录访客",
    route: "/login",
    action: "打开登录页，确认客户可识别租户、账号、密码入口",
    result: "登录入口可见；未填写任何凭据，避免截图泄露",
    ...(await capture(browser, anonymous, dir, "00-ui-login-entry.png", "登录入口")),
  });
  await anonymous.goto(`${baseUrl}/bootstrap`);
  await waitForReady(anonymous, "系统已完成首次部署");
  steps.push({
    _uiStep: "幕0-02",
    role: "未登录访客",
    route: "/bootstrap",
    action: "重读首次部署页；接管已完成不可重置",
    result: "页面明确提示系统已完成首次部署，并引导返回登录",
    ...(await capture(browser, anonymous, dir, "01-ui-bootstrap-completed.png", "首次部署已完成")),
  });
  await anonymous.close();

  const it = await login(browser, credentials, actors.itOps);
  await gotoAndWait(it.page, "/dashboard", "信息科工作台");
  steps.push({
    _uiStep: "幕0-03",
    role: "信息科",
    actor: it.actor,
    route: "/dashboard",
    action: "使用幕1信息科账号重新登录工作台",
    result: "账号已完成首次安全设置，可进入工作台；未截图 MFA 秘钥、恢复码或密码",
    ...(await capture(browser, it.page, dir, "02-ui-it-ops-dashboard-after-login.png", "信息科登录后工作台")),
  });
  await gotoAndWait(it.page, "/workbench/readiness-validation", "验收自检");
  steps.push({
    _uiStep: "幕0-04",
    role: "信息科",
    actor: it.actor,
    route: "/workbench/readiness-validation",
    action: "打开验收自检页，重读就绪/阻塞/未启用状态",
    result: "自检页能展示系统健康、模型禁用、外部连接与备份降级等状态",
    ...(await capture(browser, it.page, dir, "03-ui-readiness-validation.png", "验收自检")),
  });
  await it.context.close();

  return writeSummary(dir, "幕0 UI 重演", steps, [
    "首次接管动作已在幕0完成，本次只重读状态页，不重置接管码。",
    "MFA 绑定页含一次性恢复码，本次不截图；用成功登录与账号安全状态作为复演证据。",
  ]);
}

async function captureAct1(browser, credentials) {
  const dir = actDirs.act1;
  const admin = await login(browser, credentials, actors.hospitalAdmin);
  const steps = [];

  await gotoAndWait(admin.page, "/tenant/onboarding", "租户实施配置");
  steps.push({
    _uiStep: "幕1-01",
    role: "医院管理员",
    actor: admin.actor,
    route: "/tenant/onboarding",
    action: "打开租户实施配置页，查看组织树与实施就绪状态",
    result: "组织节点数、就绪进度、阻塞项和新增组织表单均在同页可见",
    ...(await capture(browser, admin.page, dir, "10-ui-tenant-onboarding-before.png", "租户实施配置总览")),
  });

  const orgResult = await maybeCreateOrg(admin.page);
  steps.push({
    _uiStep: "幕1-02",
    role: "医院管理员",
    actor: admin.actor,
    route: "/tenant/onboarding",
    action: `前台保存科室：${businessObjects.orgName}`,
    result: orgResult.status,
    note: orgResult.note,
    businessObject: { code: businessObjects.orgCode, name: businessObjects.orgName },
    ...(await capture(browser, admin.page, dir, "11-ui-tenant-onboarding-org-created.png", "组织节点创建结果")),
  });

  await gotoAndWait(admin.page, "/admin/users", "用户管理");
  steps.push({
    _uiStep: "幕1-03",
    role: "医院管理员",
    actor: admin.actor,
    route: "/admin/users",
    action: "打开用户管理页，确认新建用户入口与账号安全列",
    result: "用户、角色、状态、账号安全与查看入口可见",
    ...(await capture(browser, admin.page, dir, "12-ui-admin-users-before-create.png", "用户管理总览")),
  });

  const userResult = await maybeCreateExternalUser(admin.page);
  steps.push({
    _uiStep: "幕1-04",
    role: "医院管理员",
    actor: admin.actor,
    route: "/admin/users",
    action: `前台创建外部身份用户：${businessObjects.observerName}`,
    result: userResult.status,
    note: userResult.note,
    businessObject: {
      userId: businessObjects.observerUserId,
      displayName: businessObjects.observerName,
      role: businessObjects.observerRoleName,
    },
    ...(await capture(browser, admin.page, dir, "13-ui-admin-users-created-external-user.png", "外部身份用户创建结果")),
  });

  const detail = await openUserDetailIfVisible(admin.page);
  steps.push({
    _uiStep: "幕1-05",
    role: "医院管理员",
    actor: admin.actor,
    route: "/admin/users",
    action: "打开观察员详情，核对角色与数据范围",
    result: detail.opened ? "opened" : "not_visible",
    note: detail.note,
    ...(await capture(browser, admin.page, dir, "14-ui-admin-users-role-detail.png", "用户角色详情")),
  });

  await gotoAndWait(admin.page, "/onboarding/guide", "客户实施向导");
  steps.push({
    _uiStep: "幕1-06",
    role: "医院管理员",
    actor: admin.actor,
    route: "/onboarding/guide",
    action: "重走上线准备向导",
    result: "六步准备状态与跳转入口可见",
    ...(await capture(browser, admin.page, dir, "15-ui-onboarding-guide.png", "上线准备向导")),
  });
  await admin.context.close();

  const doctor = await login(browser, credentials, actors.doctor);
  await doctor.page.goto(`${baseUrl}/admin/users`);
  await waitForQuiet(doctor.page);
  const forbiddenVisible =
    (await doctor.page.getByText("当前权限不足", { exact: false }).count()) > 0 ||
    (await doctor.page.getByText("不能查看用户管理", { exact: false }).count()) > 0 ||
    (await doctor.page.getByText("没有访问权限", { exact: false }).count()) > 0 ||
    doctor.page.url().includes("/dashboard");
  steps.push({
    _uiStep: "幕1-07",
    role: "心内科医生",
    actor: doctor.actor,
    route: "/admin/users",
    action: "交叉登录验证临床医生访问用户管理",
    result: forbiddenVisible ? "forbidden_or_redirected" : "unexpected_visible",
    ...(await capture(browser, doctor.page, dir, "16-ui-admin-users-forbidden-doctor.png", "医生越权访问反馈")),
  });
  await doctor.context.close();

  return writeSummary(dir, "幕1 UI 重演", steps, [
    "本次创建外部身份观察员，避免一次性临时密码出现在页面证据中。",
    "若对象已存在，脚本不重复创建，保留前台定位与状态核对证据。",
  ]);
}

async function captureAct2(browser, credentials) {
  const dir = actDirs.act2;
  const it = await login(browser, credentials, actors.itOps);
  const steps = [];

  await gotoAndWait(it.page, "/terminology/mapping", "字典映射");
  await waitForTerminologyData(it.page);
  steps.push({
    _uiStep: "幕2-01",
    role: "信息科",
    actor: it.actor,
    route: "/terminology/mapping",
    action: "打开字典映射页，查看标准字典、院内待映射、高危候选与冲突",
    result: "映射总览、候选映射、冲突待裁与映射包发布区可见",
    ...(await capture(browser, it.page, dir, "20-ui-terminology-overview.png", "字典映射总览")),
  });

  await it.page.getByRole("button", { name: "确认候选", exact: true }).click();
  const confirmModal = it.page.locator(".ant-modal").filter({ hasText: "确认高危候选" }).last();
  await confirmModal.waitFor({ state: "visible", timeout: 10000 });
  await confirmModal.getByText("高危确认理由").waitFor({ timeout: 10000 });
  await it.page.waitForTimeout(300);
  steps.push({
    _uiStep: "幕2-02",
    role: "信息科",
    actor: it.actor,
    route: "/terminology/mapping",
    action: "打开高危候选确认弹窗但不提交",
    result: "系统要求勾选高危风险确认并填写理由；未对高危映射执行确认",
    ...(await capture(browser, it.page, dir, "21-ui-high-risk-confirmation-modal.png", "高危确认弹窗")),
  });
  await closeModal(it.page, "确认高危候选");
  await waitForQuiet(it.page);

  await it.page.locator(".ant-card-head-title", { hasText: "冲突待裁" }).scrollIntoViewIfNeeded();
  steps.push({
    _uiStep: "幕2-03",
    role: "信息科",
    actor: it.actor,
    route: "/terminology/mapping",
    action: "查看冲突待裁列表",
    result: "页面用一对多冲突、风险、待裁说明呈现冲突；尚未提供前台新建冲突入口",
    ...(await capture(browser, it.page, dir, "22-ui-conflict-readable.png", "冲突待裁")),
  });

  const buildButton = it.page.getByRole("button", { name: "构建映射包" });
  const buildDisabled = await buildButton.isDisabled();
  if (!buildDisabled) {
    await buildButton.click();
    await it.page.locator(".ant-modal").filter({ hasText: "构建术语映射包" }).waitFor({
      timeout: 10000,
    });
  }
  steps.push({
    _uiStep: "幕2-04",
    role: "信息科",
    actor: it.actor,
    route: "/terminology/mapping",
    action: "检查构建映射包前台入口",
    result: buildDisabled ? "disabled" : "modal_opened",
    note: buildDisabled ? "当前账号或数据状态下构建入口不可用" : "构建草稿弹窗可见，本批不提交新版本以避免污染配置包业务命名",
    ...(await capture(browser, it.page, dir, "23-ui-build-package-entry.png", "构建映射包入口")),
  });
  if (!buildDisabled) {
    await closeModal(it.page, "构建术语映射包");
    await waitForQuiet(it.page);
  }

  await it.page.locator(".ant-card-head-title", { hasText: "映射包发布" }).scrollIntoViewIfNeeded();
  const publishDisabled = await it.page.getByRole("button", { name: "发布映射包" }).isDisabled();
  const rollbackDisabled = await it.page.getByRole("button", { name: "回滚映射包" }).isDisabled();
  steps.push({
    _uiStep: "幕2-05",
    role: "信息科",
    actor: it.actor,
    route: "/terminology/mapping",
    action: "检查发布与回滚前台入口",
    result: { publishDisabled, rollbackDisabled },
    note: "页面有发布/回滚入口，但当前生效包无可回滚历史版本；草稿→确认→替换→回滚全状态机仍缺前台新建映射入口",
    ...(await capture(browser, it.page, dir, "24-ui-package-publish-rollback-entry.png", "映射包发布与回滚入口")),
  });

  await it.context.close();

  return writeSummary(dir, "幕2 UI 重演", steps, [
    "本页没有发现“新建本地映射/手工登记映射”的前台入口，幕8.5 按体验缺口登记，不用 API 补做客户面动作。",
    "高危候选为医疗安全敏感项，本次只验证二次确认门槛，不提交确认。",
  ]);
}

async function writeSummary(dir, title, steps, findings) {
  const summary = {
    _uiStep: title,
    capturedAt: new Date().toISOString(),
    baseUrl,
    evidenceId: stableEvidenceId(`${title}:${baseUrl}:${steps.length}`),
    sensitiveDataPolicy:
      "脚本不输出密码、MFA 秘钥、恢复码、Cookie、Token；截图避开一次性凭据页面。",
    steps,
    findings,
  };
  await writeFile(
    path.join(dir, "00-ui-replay-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  return summary;
}

async function main() {
  await ensureDirs();
  const credentials = loadCredentials();
  const browser = await chromium.launch({ headless: true });
  try {
    const summaries = [];
    summaries.push(await captureAct0(browser, credentials));
    summaries.push(await captureAct1(browser, credentials));
    summaries.push(await captureAct2(browser, credentials));
    for (const summary of summaries) {
      console.log(`${summary._uiStep}: ${summary.steps.length} steps`);
    }
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
