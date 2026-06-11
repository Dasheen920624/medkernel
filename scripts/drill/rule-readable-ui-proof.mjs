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
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/规则可读路径-134复验",
);
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";
const actorKey =
  process.env.DRILL_RULE_ACTOR ??
  "drill-hospital-20260611:drill-medical-affairs-20260611";

const requiredReadableTexts = [
  "规则可读路径",
  "触发时点",
  "适用范围",
  "命中条件",
  "处置动作",
  "治理与安全",
  "需要医师确认",
  "不自动开立或修改医嘱",
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

async function login(browser, credentials, viewport) {
  const actor = credentials[actorKey];
  if (!actor) throw new Error(`找不到复验账号：${actorKey}`);
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
        return {
          tagName: node.tagName.toLowerCase(),
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

async function verifyRuleReadable(
  browser,
  credentials,
  viewport,
  filenames,
  labelPrefix,
) {
  const { context, page, actor } = await login(browser, credentials, viewport);
  const captures = [];
  try {
    await page.goto(`${baseUrl}/rule/definitions`, {
      waitUntil: "domcontentloaded",
    });
    await page
      .getByText("规则中枢", { exact: false })
      .first()
      .waitFor({ timeout: 25000 });
    await waitForQuiet(page);
    captures.push(
      await capture(
        browser,
        page,
        filenames.list,
        `${labelPrefix} 规则库列表`,
        actor,
      ),
    );

    const detailButtons = page.getByRole("button", {
      name: "查看配置与试运行",
    });
    const detailButtonCount = await detailButtons.count();
    if (detailButtonCount === 0) {
      throw new Error("134 规则库页面未返回可查看详情的规则");
    }
    await detailButtons.first().click();
    await page.getByText("规则配置详情与试运行").waitFor({ timeout: 25000 });
    const readableRegion = page.getByRole("region", { name: "规则可读路径" });
    await readableRegion.waitFor({ state: "visible", timeout: 25000 });
    await waitForQuiet(page);

    const readableText = await readableRegion.innerText();
    const missingTexts = requiredReadableTexts.filter(
      (text) => !readableText.includes(text),
    );
    const metrics = await getLayoutMetrics(page);
    if (missingTexts.length > 0) {
      throw new Error(`规则可读路径缺少文案：${missingTexts.join("、")}`);
    }
    if (metrics.horizontalOverflowPx > 1) {
      throw new Error(
        `视口 ${viewport.width} 存在横向溢出 ${metrics.horizontalOverflowPx}px`,
      );
    }
    captures.push(
      await capture(
        browser,
        page,
        filenames.drawer,
        `${labelPrefix} 规则可读路径抽屉`,
        actor,
      ),
    );

    return {
      viewport,
      actor,
      detailButtonCount,
      readableText,
      metrics,
      captures,
    };
  } finally {
    await context.close();
  }
}

async function writeReadme(summary) {
  const readme = `# 规则可读路径 134 复验

- 时间：${summary.completedAt}
- 环境：${summary.baseUrl}
- 分支 / 合并提交：${summary.branch} / ${summary.mergeCommit}
- 发布备份：${summary.deployBackup}
- 账号：${summary.actor.username}（${summary.actor.tenantId}，已脱敏保存，不含口令）

## 结论

- 规则库真实页面 \`/rule/definitions\` 可打开规则详情抽屉。
- 新增「规则可读路径」在桌面与 390px 移动视口均可见。
- 复验文案覆盖触发时点、适用范围、命中条件、处置动作、治理与安全，并显示「需要医师确认」「不自动开立或修改医嘱」。
- 两个视口均未发现页面级横向溢出。

## 文件

- [00-rule-readable-134-proof.json](00-rule-readable-134-proof.json)
- [01-desktop-rule-list.png](01-desktop-rule-list.png)
- [02-desktop-rule-readable-drawer.png](02-desktop-rule-readable-drawer.png)
- [03-mobile-rule-list.png](03-mobile-rule-list.png)
- [04-mobile-rule-readable-drawer.png](04-mobile-rule-readable-drawer.png)
`;
  await writeFile(path.join(evidenceDir, "README.md"), readme, "utf8");
}

async function main() {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });

  const credentials = loadCredentials();
  const browser = await chromium.launch({ headless: true });
  try {
    const desktop = await verifyRuleReadable(
      browser,
      credentials,
      { width: 1440, height: 1100 },
      {
        list: "01-desktop-rule-list.png",
        drawer: "02-desktop-rule-readable-drawer.png",
      },
      "desktop",
    );
    const mobile = await verifyRuleReadable(
      browser,
      credentials,
      { width: 390, height: 844 },
      {
        list: "03-mobile-rule-list.png",
        drawer: "04-mobile-rule-readable-drawer.png",
      },
      "mobile",
    );
    const summary = {
      proof: "rule-readable-134",
      baseUrl,
      credentialRedaction:
        "credentials loaded from server file; password/token/cookie not persisted",
      actor: desktop.actor,
      branch: "main",
      mergeCommit: "27d762c50817fe04775e7ebd2fabe0749a248591",
      deployBackup: "/zoesoft/medkernel/backups/deploy-20260611-162406",
      completedAt: new Date().toISOString(),
      desktop,
      mobile,
    };
    await writeFile(
      path.join(evidenceDir, "00-rule-readable-134-proof.json"),
      `${JSON.stringify(summary, null, 2)}\n`,
      "utf8",
    );
    await writeReadme(summary);
    console.log(`规则可读路径 134 复验通过：${evidenceDir}`);
  } finally {
    await browser.close();
  }
}

await main();
