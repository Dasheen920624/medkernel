#!/usr/bin/env node
import { execFileSync } from "node:child_process";
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
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/推荐中枢-134复验",
);
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";

const actors = {
  doctor: "drill-hospital-20260611:drill-respiratory-doctor-20260611",
};

const requiredTexts = [
  "提醒与推荐中枢",
  "推荐链路总览",
  "按患者 ID / traceId / 来源对象查推荐",
  "患者或 traceId",
  "触发事件",
  "命中规则",
  "知识来源",
  "路径上下文",
  "待办 / 通知",
  "医生反馈",
  "药师复核",
];

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
    { encoding: "utf8", maxBuffer: 2 * 1024 * 1024 },
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

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function waitForReady(page, text, timeout = 25000) {
  await page.getByText(text, { exact: false }).first().waitFor({ timeout });
  await waitForQuiet(page);
}

async function renderWithUrlBar(browser, rawPath, finalPath, url, width) {
  const image = await readFile(rawPath);
  const imageData = `data:image/png;base64,${image.toString("base64")}`;
  const html = `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <style>
      html, body { margin: 0; background: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
      .bar { height: 44px; display: flex; align-items: center; gap: 10px; padding: 0 16px; background: #eef2f7; color: #1f2937; border-bottom: 1px solid #cbd5e1; box-sizing: border-box; font-size: 13px; }
      .dot { width: 10px; height: 10px; border-radius: 50%; background: #94a3b8; flex: 0 0 auto; }
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
  const wrapper = await browser.newPage({ viewport: { width, height: 1100 } });
  await wrapper.setContent(html, { waitUntil: "load" });
  await wrapper.screenshot({ path: finalPath, fullPage: true });
  await wrapper.close();
  await rm(rawPath, { force: true });
}

async function capture(browser, page, filename, label, actor) {
  const finalPath = path.join(evidenceDir, filename);
  const rawPath = path.join(evidenceDir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(
    browser,
    rawPath,
    finalPath,
    page.url(),
    page.viewportSize().width,
  );
  return {
    label,
    actor,
    screenshot: filename,
    url: page.url(),
    viewport: page.viewportSize(),
  };
}

async function login(browser, credentials, actorKey, viewport) {
  const actor = credentials[actorKey];
  if (!actor) throw new Error(`missing credentials for ${actorKey}`);
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport,
    locale: "zh-CN",
  });
  const page = await context.newPage();
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.getByLabel("工号 / 账号").fill(actor.username);
  await page.getByLabel("密码").fill(actor.currentPassword);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), {
    timeout: 25000,
  });
  await waitForQuiet(page);
  return { context, page, actor: publicActor(actor) };
}

async function getLayoutMetrics(page) {
  return page.evaluate(() => {
    const viewportWidth = window.innerWidth;
    const documentScrollWidth = Math.max(
      document.documentElement.scrollWidth,
      document.body.scrollWidth,
    );
    const overflowingNodes = Array.from(document.querySelectorAll("body *"))
      .map((node) => {
        const rect = node.getBoundingClientRect();
        const text = (node.textContent || "")
          .trim()
          .replace(/\s+/g, " ")
          .slice(0, 80);
        const tagName = node.tagName.toLowerCase();
        return {
          tagName,
          className:
            typeof node.className === "string"
              ? node.className.split(" ").slice(0, 3).join(" ")
              : "",
          text,
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
        };
      })
      .filter(
        (item) =>
          item.width > 0 && (item.left < -1 || item.right > viewportWidth + 1),
      )
      .slice(0, 8);

    return {
      viewportWidth,
      documentScrollWidth,
      horizontalOverflowPx: Math.max(
        0,
        Math.round(documentScrollWidth - viewportWidth),
      ),
      overflowingNodes,
    };
  });
}

async function verifyMainSurface(page) {
  await page.goto(`${baseUrl}/cdss/fatigue`, { waitUntil: "domcontentloaded" });
  await waitForReady(page, "提醒与推荐中枢");
  const bodyText = await page.locator("body").innerText({ timeout: 10000 });
  const missingTexts = requiredTexts.filter((text) => !bodyText.includes(text));
  const quickSearchPlaceholderVisible =
    (await page
      .getByPlaceholder("输入患者 ID、traceId、卡片或触发事件")
      .count()) > 0;
  const cardActionCount = await page
    .getByRole("button", { name: "查看与人机反馈" })
    .count();

  return {
    missingTexts,
    quickSearchPlaceholderVisible,
    cardActionCount,
    metrics: await getLayoutMetrics(page),
  };
}

async function verifyDrawer(browser, page, actor) {
  const action = page.getByRole("button", { name: "查看与人机反馈" }).first();
  if ((await action.count()) === 0) {
    return {
      opened: false,
      missingTexts: ["查看与人机反馈"],
      screenshot: null,
    };
  }

  await action.click();
  await page.locator(".ant-drawer:visible").last().waitFor({ timeout: 15000 });
  await waitForReady(page, "这条推荐是怎么来的");
  const drawerText = await page
    .locator(".ant-drawer:visible")
    .last()
    .innerText({ timeout: 10000 });
  const drawerRequiredTexts = [
    "这条推荐是怎么来的",
    "推荐卡主数据",
    "触发事件",
    "命中规则",
    "知识来源",
    "路径上下文",
    "待办 / 通知",
    "医生反馈",
    "药师复核",
  ];
  const missingTexts = drawerRequiredTexts.filter(
    (text) => !drawerText.includes(text),
  );
  const screenshot = await capture(
    browser,
    page,
    "02-desktop-recommendation-drawer.png",
    "医生打开推荐详情，看到七段推荐来源链路",
    actor,
  );

  return {
    opened: true,
    missingTexts,
    screenshot,
    metrics: await getLayoutMetrics(page),
  };
}

async function run() {
  await mkdir(evidenceDir, { recursive: true });
  const credentials = loadCredentials();
  const browser = await chromium.launch({ headless: true });
  const screenshots = [];
  const startedAt = new Date().toISOString();

  try {
    const desktop = await login(browser, credentials, actors.doctor, {
      width: 1440,
      height: 1000,
    });
    const desktopMain = await verifyMainSurface(desktop.page);
    screenshots.push(
      await capture(
        browser,
        desktop.page,
        "01-desktop-recommendation-hub.png",
        "医生桌面端查看提醒与推荐中枢和推荐链路总览",
        desktop.actor,
      ),
    );
    const drawer = await verifyDrawer(browser, desktop.page, desktop.actor);
    if (drawer.screenshot) screenshots.push(drawer.screenshot);
    await desktop.context.close();

    const mobile = await login(browser, credentials, actors.doctor, {
      width: 390,
      height: 844,
    });
    const mobileMain = await verifyMainSurface(mobile.page);
    screenshots.push(
      await capture(
        browser,
        mobile.page,
        "03-mobile-recommendation-hub.png",
        "医生移动端查看推荐中枢筛选区和链路节点，无横向溢出",
        mobile.actor,
      ),
    );
    await mobile.page.getByLabel("患者或 traceId").scrollIntoViewIfNeeded();
    await waitForQuiet(mobile.page);
    screenshots.push(
      await capture(
        browser,
        mobile.page,
        "04-mobile-recommendation-search.png",
        "医生移动端滚动到患者 / traceId 检索输入，筛选控件保持单列可用",
        mobile.actor,
      ),
    );
    await mobile.context.close();

    const checks = {
      desktopTexts: desktopMain.missingTexts.length === 0,
      desktopQuickSearch: desktopMain.quickSearchPlaceholderVisible,
      desktopNoHorizontalOverflow:
        desktopMain.metrics.horizontalOverflowPx === 0,
      drawerOpened: drawer.opened,
      drawerTexts: drawer.missingTexts.length === 0,
      mobileTexts: mobileMain.missingTexts.length === 0,
      mobileQuickSearch: mobileMain.quickSearchPlaceholderVisible,
      mobileNoHorizontalOverflow: mobileMain.metrics.horizontalOverflowPx === 0,
    };
    const pass = Object.values(checks).every(Boolean);
    const summary = {
      runAt: startedAt,
      environment: baseUrl,
      route: "/cdss/fatigue",
      source: "codex-demo-drill-recommendation-hub-17f4cc4d",
      deployBackup: "/zoesoft/medkernel/backups/deploy-20260611-154250",
      actor: desktop.actor,
      checks,
      pass,
      observations: {
        desktop: desktopMain,
        drawer,
        mobile: mobileMain,
      },
      screenshots,
      credentialLocation: credentialPath,
      credentialRedaction:
        "凭据仅从服务器受限文件读取；本摘要不写入口令、Cookie、令牌或签名材料。",
    };

    const summaryPath = path.join(
      evidenceDir,
      "00-recommendation-hub-134-proof.json",
    );
    await writeFile(
      summaryPath,
      `${JSON.stringify(summary, null, 2)}\n`,
      "utf8",
    );
    if (!pass) {
      throw new Error(
        `recommendation hub proof failed: ${JSON.stringify(checks)}`,
      );
    }
    console.log(
      JSON.stringify(
        {
          pass,
          summaryPath: path.relative(repoRoot, summaryPath),
          screenshots: screenshots.map((item) => item.screenshot),
        },
        null,
        2,
      ),
    );
  } finally {
    await browser.close();
  }
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
