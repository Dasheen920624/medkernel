#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕3 知识治理诚实边界验证（跨角色）。
// 剧本动作（全部真实前台 + 服务端回查，验证「诚实降级优先」红线）：
//   1. 机构知识治理员 /knowledge/governance：零知识时诚实空态，不造数；
//      /advanced/ai-workflows：无外部模型时诚实降级到基线能力，不伪装 AI。
//   2. 平台知识治理员 /knowledge/governance：平台域同样零知识、诚实空态。
//   3. 平台治理管理员 /security/baseline 系统配置：文献资料库根地址未配置（长度 0），
//      真实前台填非法本机目录值必须被边界守卫拒绝且可见报错，正式根地址保持未配置（P6 阻断）。
// 成功判定一律服务端回查（/engine/knowledge/identities、/model-capabilities/status、/system/configs）。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕3-知识治理诚实边界/
//   00-act3-summary.json 与 NN-ui-*.png（全部带 URL 栏）。
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
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕3-知识治理诚实边界",
);

const LITERATURE_KEY = "medkernel.knowledge.literature.material-root-uri";
// 非法本机目录值：无 URI scheme、指向本机 tmp、缺正式资料库结构，必被边界守卫拒绝。
const ILLEGAL_LITERATURE_VALUE = "/tmp/p5-literature-materials/";

function traceId(stage) {
  return `p5-act3-${stage}-${Date.now()}`;
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
  // 知识/配置页查询较慢，等网络空闲后页面才渲染空态或表格，固定短等待会撞 loading。
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
  await page.waitForTimeout(1200);
}

async function bodyText(page) {
  return page.locator("body").innerText({ timeout: 8000 });
}

async function knowledgeIdentityTotal(context, stage) {
  const res = await apiGet(context, "/engine/knowledge/identities?page=1&size=5", stage);
  return { status: res.status, total: res.body?.data?.total ?? null };
}

// 段1：机构知识治理员——零知识空态 + AI 能力诚实降级。
async function walkKnowledgeGovernor(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor",
  );
  const steps = [];
  const observations = {};
  try {
    // 知识审核空态：服务端零身份 + 页面诚实空态文案。
    const identity = await knowledgeIdentityTotal(context, "kg-identities");
    observations.knowledgeIdentityTotal = identity.total;
    await gotoPath(page, "/knowledge/governance");
    const govText = await bodyText(page);
    observations.emptyStateVisible = govText.includes("暂无待审核知识身份");
    observations.noFabricatedKnowledge = !govText.includes("示例") && !govText.includes("演示");
    steps.push(
      await capture(browser, page, "01-ui-knowledge-governance-empty.png", "知识审核与发布：零知识诚实空态"),
    );
    if (identity.total !== 0 || !observations.emptyStateVisible) {
      summary.failures.push({
        step: "知识审核页未呈现零知识诚实空态",
        detail: { total: identity.total, emptyStateVisible: observations.emptyStateVisible },
      });
    }

    // AI 能力降级：服务端全部 BASELINE + fallback，页面无外部模型伪装。
    const cap = await apiGet(context, "/model-capabilities/status", "kg-capabilities");
    const caps = cap.body?.data ?? [];
    observations.capabilityCount = caps.length;
    observations.allBaseline = caps.length > 0 && caps.every((c) => c.routeStrategy === "BASELINE");
    observations.allFallbackAvailable = caps.every((c) => c.fallbackAvailable === true);
    observations.externalModelStrategies = caps
      .filter((c) => c.routeStrategy === "EXTERNAL_MODEL" || c.routeStrategy === "LOCAL_MODEL")
      .map((c) => c.capabilityCode);
    await gotoPath(page, "/advanced/ai-workflows");
    const aiText = await bodyText(page);
    observations.baselineDegradeVisible =
      aiText.includes("基础规则能力") || aiText.includes("基线可用");
    steps.push(
      await capture(browser, page, "02-ui-ai-workflows-baseline-degrade.png", "智能工作流：无外部模型诚实降级到基线"),
    );
    if (!observations.allBaseline || observations.externalModelStrategies.length > 0) {
      summary.failures.push({
        step: "AI 能力未诚实降级（出现外部/本地模型伪装或非全基线）",
        detail: {
          allBaseline: observations.allBaseline,
          externalModelStrategies: observations.externalModelStrategies,
        },
      });
    }
    if (!observations.baselineDegradeVisible) {
      summary.failures.push({ step: "AI 工作流页未呈现可见的基线降级状态", detail: {} });
    }
  } finally {
    summary.knowledgeGovernor = { observations, steps };
    await context.close();
  }
}

// 段2：平台知识治理员——平台域零知识诚实空态。
async function walkPlatformKnowledgeGovernor(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "platform-knowledge-governor"),
    "platform-knowledge-governor",
  );
  const steps = [];
  const observations = {};
  try {
    const identity = await knowledgeIdentityTotal(context, "pkg-identities");
    observations.knowledgeIdentityTotal = identity.total;
    await gotoPath(page, "/knowledge/governance");
    const govText = await bodyText(page);
    observations.emptyStateVisible = govText.includes("暂无待审核知识身份");
    observations.forbidden = govText.includes("当前权限不足");
    steps.push(
      await capture(
        browser,
        page,
        "03-ui-platform-knowledge-governance-empty.png",
        "平台知识治理员：平台域零知识诚实空态",
      ),
    );
    if (identity.total !== 0 || observations.forbidden || !observations.emptyStateVisible) {
      summary.failures.push({
        step: "平台知识治理员知识审核页未呈现零知识诚实空态",
        detail: observations,
      });
    }
  } finally {
    summary.platformKnowledgeGovernor = { observations, steps };
    await context.close();
  }
}

// 段3：平台治理管理员——文献根地址未配置 + 非法本机目录值被边界守卫拒绝。
async function walkPlatformGovernanceAdmin(browser, credentials, summary) {
  const { context, page } = await login(
    browser,
    requireAccount(credentials, "platform-governance-admin"),
    "platform-governance-admin",
  );
  const steps = [];
  const observations = {};
  try {
    const literatureValue = async (stage) => {
      const res = await apiGet(context, "/system/configs?scope=system", stage);
      const item = (res.body?.data ?? []).find((i) => i.key === LITERATURE_KEY);
      return { status: res.status, value: item?.value ?? null, version: item?.version ?? null };
    };
    const before = await literatureValue("lit-before");
    observations.literatureLengthBefore = (before.value ?? "").length;

    await gotoPath(page, "/security/baseline");
    await page.getByRole("tab", { name: "系统配置" }).click();
    await waitForQuiet(page);
    const cfgText = await bodyText(page);
    observations.unconfiguredHintVisible = cfgText.includes("未配置");
    steps.push(
      await capture(
        browser,
        page,
        "04-ui-security-baseline-literature-unconfigured.png",
        "系统配置：平台知识文献资料库根地址未配置",
      ),
    );

    // 文献项在配置表格分页靠后，逐页翻到出现编辑按钮再点击。
    const editButton = page.getByRole("button", { name: "编辑 平台知识文献资料库根地址" });
    let editorFound = false;
    for (let pageIndex = 0; pageIndex < 8; pageIndex += 1) {
      if ((await editButton.count()) > 0) {
        editorFound = true;
        break;
      }
      const next = page.locator(".ant-pagination-next:not(.ant-pagination-disabled)");
      if ((await next.count()) === 0) break;
      await next.first().click();
      await page.waitForTimeout(700);
    }
    observations.editorFound = editorFound;
    if (!editorFound) {
      summary.failures.push({ step: "配置表格未找到文献根地址编辑入口", detail: {} });
      return;
    }
    // 真实前台填非法本机目录值，必须被边界守卫拒绝且可见报错。
    await editButton.first().click();
    const modal = page.locator(".ant-modal-content", { hasText: "编辑系统配置" });
    await modal.waitFor({ state: "visible", timeout: 10000 });
    await modal.getByLabel("配置值").fill(ILLEGAL_LITERATURE_VALUE);
    await modal.getByLabel("变更原因").fill("幕3诚实边界：故意填本机 tmp 目录，验证被拒绝且 P6 阻断保持");
    await modal.locator(".ant-checkbox-wrapper", { hasText: "确认高风险影响" }).click();
    steps.push(
      await capture(
        browser,
        page,
        "05-ui-security-baseline-illegal-value-form.png",
        "填入非法本机目录值的编辑表单",
      ),
    );
    await modal.getByRole("button", { name: "保存配置" }).click();

    const errorLocator = page.locator(".ant-message-error, .ant-message-notice-error");
    await errorLocator.first().waitFor({ state: "visible", timeout: 10000 });
    observations.illegalValueRejectedVisible = await errorLocator.first().isVisible();
    observations.rejectionText = await page
      .locator(".ant-message")
      .innerText()
      .catch(() => null);
    steps.push(
      await capture(
        browser,
        page,
        "06-ui-security-baseline-illegal-value-rejected.png",
        "非法文献根地址被边界守卫拒绝的可见报错",
      ),
    );

    // 服务端回查：正式根地址仍未配置，P6 阻断保持。
    const after = await literatureValue("lit-after");
    observations.literatureLengthAfter = (after.value ?? "").length;
    observations.p6StillBlocked = observations.literatureLengthAfter === 0;
    if (!observations.illegalValueRejectedVisible) {
      summary.failures.push({ step: "非法文献根地址未给出可见拒绝报错", detail: observations });
    }
    if (!observations.p6StillBlocked) {
      summary.failures.push({
        step: "非法配置后文献根地址被写入，P6 阻断被破坏",
        detail: { lengthAfter: observations.literatureLengthAfter },
      });
    }
  } finally {
    summary.platformGovernanceAdmin = { observations, steps };
    await context.close();
  }
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const summary = {
    act: "P5幕3-知识治理诚实边界（跨角色）",
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    credentialSource: credentialPath,
    failures: [],
  };
  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    const walks = [
      walkKnowledgeGovernor,
      walkPlatformKnowledgeGovernor,
      walkPlatformGovernanceAdmin,
    ];
    for (const walk of walks) {
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
    path.join(evidenceDir, "00-act3-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  if (summary.failures.length > 0) {
    console.error("幕3 诚实边界旅程存在失败步骤：");
    for (const failure of summary.failures) {
      console.error(`- ${failure.step}`);
    }
    process.exitCode = 1;
    return;
  }
  console.log("幕3 诚实边界旅程全部通过，证据已写入", evidenceDir);
}

await main();
