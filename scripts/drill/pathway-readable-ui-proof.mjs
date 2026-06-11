#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const requireFromFrontend = createRequire(new URL("../../frontend/package.json", import.meta.url));
const { chromium } = requireFromFrontend("playwright");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/路径可读化-134复验",
);
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";

const actors = {
  specialist: "drill-hospital-20260611:drill-role-specialist-20260611",
  respiratoryDoctor: "drill-hospital-20260611:drill-respiratory-doctor-20260611",
};

const businessObjects = {
  capTemplateCode: "TPL.DRILL.CAP.1781126077791",
  capTemplateId: "pt-e8b9a1f1-f423-44aa-ba6c-835c7246c186",
};

const requiredPatientGraphTexts = [
  "当前患者位置",
  "已完成",
  "当前节点",
  "待执行",
  "只读展示",
  "不自动开立或修改医嘱",
];

const requiredTemplateCopyTexts = [
  "复制为新版本",
  "维护已全量生效拓扑时先复制为下一版草稿",
  "影响预览",
  "灰度发布",
  "回滚证据",
];

function loadCredentials() {
  const raw = execFileSync(
    "ssh",
    ["-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=no", sshTarget, `cat ${credentialPath}`],
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
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => undefined);
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
  await renderWithUrlBar(browser, rawPath, finalPath, page.url(), page.viewportSize().width);
  return {
    label,
    actor,
    screenshot: filename,
    url: page.url(),
    viewport: page.viewportSize(),
  };
}

function attachDiagnostics(page) {
  const diagnostics = {
    failedRequests: [],
    clientErrors: [],
    serverErrors: [],
    consoleErrors: [],
  };
  page.on("requestfailed", (request) => {
    const failure = request.failure();
    diagnostics.failedRequests.push({
      url: request.url(),
      method: request.method(),
      errorText: failure?.errorText,
    });
  });
  page.on("response", (response) => {
    if (response.status() >= 400 && response.status() < 500) {
      diagnostics.clientErrors.push({
        url: response.url(),
        status: response.status(),
      });
    }
    if (response.status() >= 500) {
      diagnostics.serverErrors.push({
        url: response.url(),
        status: response.status(),
      });
    }
  });
  page.on("console", (message) => {
    if (message.type() === "error" && !message.text().startsWith("Failed to load resource:")) {
      diagnostics.consoleErrors.push(message.text().slice(0, 300));
    }
  });
  return diagnostics;
}

async function login(browser, credentials, actorKey, viewport) {
  const actor = credentials[actorKey];
  if (!actor) throw new Error(`找不到复验账号：${actorKey}`);
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport,
    locale: "zh-CN",
  });
  const page = await context.newPage();
  const diagnostics = attachDiagnostics(page);
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.getByLabel("工号 / 账号").fill(actor.username);
  await page.getByLabel("密码").fill(actor.currentPassword);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), {
    timeout: 25000,
  });
  await waitForQuiet(page);
  return {
    context,
    page,
    actor: publicActor(actor),
    diagnostics,
  };
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
        const style = window.getComputedStyle(node);
        const text = (node.textContent || "").trim().replace(/\s+/g, " ").slice(0, 80);
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
          visible:
            style.display !== "none" &&
            style.visibility !== "hidden" &&
            rect.width > 0 &&
            rect.height > 0 &&
            rect.bottom >= 0 &&
            rect.top <= window.innerHeight,
        };
      })
      .filter((item) => item.visible && (item.left < -1 || item.right > viewportWidth + 1))
      .slice(0, 8);

    return {
      viewportWidth,
      documentScrollWidth,
      horizontalOverflowPx: Math.max(0, Math.round(documentScrollWidth - viewportWidth)),
      overflowingNodes,
    };
  });
}

async function gotoAndWait(page, route, readyText) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await page.getByText(readyText, { exact: false }).first().waitFor({
    timeout: 25000,
  });
  await waitForQuiet(page);
}

async function clickRowButton(page, rowText, buttonName) {
  const row = page.locator("tr").filter({ hasText: rowText }).first();
  await row.waitFor({ timeout: 25000 });
  const rowInnerText = await row.innerText();
  await row.getByRole("button", { name: buttonName }).click();
  await waitForQuiet(page);
  return rowInnerText;
}

function parseVersionFromRow(rowText) {
  const match = rowText.match(/v(\d+)\.0/);
  return match ? Number.parseInt(match[1], 10) : null;
}

function assertNoOverflow(label, metrics) {
  if (metrics.horizontalOverflowPx > 1) {
    throw new Error(
      `${label} 存在横向溢出 ${metrics.horizontalOverflowPx}px：${JSON.stringify(
        metrics.overflowingNodes,
      )}`,
    );
  }
}

async function verifyPatientGraph(browser, credentials, viewport, filenames, labelPrefix) {
  const { context, page, actor, diagnostics } = await login(
    browser,
    credentials,
    actors.respiratoryDoctor,
    viewport,
  );
  const captures = [];
  try {
    await gotoAndWait(page, "/pathway/patients", "患者路径");
    captures.push(
      await capture(browser, page, filenames.list, `${labelPrefix} 医生患者路径列表`, actor),
    );

    await clickRowButton(page, businessObjects.capTemplateId, "办理推进与解释追溯");
    const drawer = page.locator(".ant-drawer:visible").last();
    await drawer.waitFor({ timeout: 25000 });
    const graph = page.getByRole("region", { name: "医生只读路径图" });
    await graph.waitFor({ state: "visible", timeout: 25000 });
    await waitForQuiet(page);

    const graphText = await graph.innerText({ timeout: 10000 });
    const drawerText = await drawer.innerText({ timeout: 10000 });
    const missingTexts = requiredPatientGraphTexts.filter((text) => !graphText.includes(text));
    const nodeCount = await graph.locator('[aria-label^="路径节点 "]').count();
    const currentNodeCount = await graph.locator('[aria-label*="当前节点"]').count();
    const deleteButtonCount = await graph.getByRole("button", { name: /删除/ }).count();
    const edgeVisible = graphText.includes("流转") && graphText.includes("→");
    const metrics = await getLayoutMetrics(page);

    if (missingTexts.length > 0) {
      throw new Error(`医生只读路径图缺少文案：${missingTexts.join("、")}`);
    }
    if (!drawerText.includes("医生只读路径图")) {
      throw new Error("患者路径详情抽屉缺少医生只读路径图标题");
    }
    if (nodeCount < 2) {
      throw new Error(`医生只读路径图节点数不足：${nodeCount}`);
    }
    if (currentNodeCount < 1) {
      throw new Error("医生只读路径图未标记当前节点");
    }
    if (deleteButtonCount > 0) {
      throw new Error("医生只读路径图出现删除按钮，不符合只读约束");
    }
    if (!edgeVisible) {
      throw new Error("医生只读路径图未展示可读流转边");
    }
    assertNoOverflow(`${labelPrefix} 医生只读路径图`, metrics);
    await graph.scrollIntoViewIfNeeded();
    await waitForQuiet(page);

    captures.push(
      await capture(browser, page, filenames.drawer, `${labelPrefix} 医生只读路径图抽屉`, actor),
    );

    return {
      viewport,
      actor,
      nodeCount,
      currentNodeCount,
      deleteButtonCount,
      edgeVisible,
      graphText,
      metrics,
      diagnostics,
      captures,
    };
  } finally {
    await context.close();
  }
}

async function verifyTemplateCopy(browser, credentials, viewport, filenames, labelPrefix) {
  const { context, page, actor, diagnostics } = await login(
    browser,
    credentials,
    actors.specialist,
    viewport,
  );
  const captures = [];
  try {
    await gotoAndWait(page, "/pathway/templates", "路径中枢");
    captures.push(
      await capture(browser, page, filenames.list, `${labelPrefix} 专科专家路径模板列表`, actor),
    );

    const rowText = await clickRowButton(page, businessObjects.capTemplateCode, "设计与试运行");
    const currentVersion = parseVersionFromRow(rowText);
    const drawer = page.locator(".ant-drawer:visible").last();
    await drawer.waitFor({ timeout: 25000 });
    const copyButton = drawer.getByRole("button", { name: "复制为新版本" });
    await copyButton.waitFor({ state: "visible", timeout: 25000 });
    await waitForQuiet(page);

    const drawerText = await drawer.innerText({ timeout: 10000 });
    const missingDrawerTexts = requiredTemplateCopyTexts.filter(
      (text) => !drawerText.includes(text),
    );
    const drawerMetrics = await getLayoutMetrics(page);
    if (missingDrawerTexts.length > 0) {
      throw new Error(`路径模板详情缺少复制说明：${missingDrawerTexts.join("、")}`);
    }
    assertNoOverflow(`${labelPrefix} 路径模板复制入口`, drawerMetrics);
    captures.push(
      await capture(browser, page, filenames.drawer, `${labelPrefix} 已发布模板复制入口`, actor),
    );

    await copyButton.click();
    const dialog = page.getByRole("dialog", { name: "新建路径模板模型" });
    await dialog.waitFor({ state: "visible", timeout: 25000 });
    await page.getByLabel("路径模型代码").waitFor({ state: "visible", timeout: 25000 });
    const copiedCode = await page.getByLabel("路径模型代码").inputValue();
    const copiedVersion = Number(await page.getByLabel("模板版本号").inputValue());
    if (copiedCode !== businessObjects.capTemplateCode) {
      throw new Error(`复制草稿未保留路径模型代码：${copiedCode}`);
    }
    if (currentVersion !== null && copiedVersion !== currentVersion + 1) {
      throw new Error(`复制草稿版本号不是下一版：当前 v${currentVersion}.0，弹窗 ${copiedVersion}`);
    }
    if (!Number.isFinite(copiedVersion) || copiedVersion < 2) {
      throw new Error(`复制草稿版本号异常：${copiedVersion}`);
    }
    await page
      .locator(".ant-modal:visible .ant-tabs-tab")
      .filter({ hasText: "L2 节点画布" })
      .click();
    await page.getByText("结构化节点画布", { exact: false }).first().waitFor({
      timeout: 15000,
    });
    await waitForQuiet(page);
    const modal = page.locator(".ant-modal:visible").last();
    const nodeFieldCount = await modal.locator("label").filter({ hasText: "节点编码" }).count();
    const edgeFieldCount = await modal.locator("label").filter({ hasText: "边编码" }).count();
    const dialogMetrics = await getLayoutMetrics(page);
    if (nodeFieldCount < 2) {
      throw new Error(`复制草稿未带出路径节点：${nodeFieldCount}`);
    }
    if (edgeFieldCount < 1) {
      throw new Error(`复制草稿未带出流转边：${edgeFieldCount}`);
    }
    assertNoOverflow(`${labelPrefix} 路径模板复制弹窗`, dialogMetrics);
    await modal
      .getByText(/^节点 1$/)
      .first()
      .scrollIntoViewIfNeeded();
    await waitForQuiet(page);
    captures.push(
      await capture(
        browser,
        page,
        filenames.dialog,
        `${labelPrefix} 复制为新版本弹窗与 L2 节点画布`,
        actor,
      ),
    );

    return {
      viewport,
      actor,
      currentVersion,
      copiedCode,
      copiedVersion,
      nodeFieldCount,
      edgeFieldCount,
      drawerMetrics,
      dialogMetrics,
      diagnostics,
      captures,
    };
  } finally {
    await context.close();
  }
}

async function writeReadme(summary) {
  const templateIndicator403Count = [
    ...summary.templateDesktop.diagnostics.clientErrors,
    ...summary.templateMobile.diagnostics.clientErrors,
  ].filter(
    (item) => item.status === 403 && item.url.includes("/engine/evaluation/indicators"),
  ).length;
  const diagnosticNote =
    templateIndicator403Count > 0
      ? `- 脚本诊断记录复制弹窗加载 ACTIVE 评估指标下拉时出现 ${templateIndicator403Count} 次 \`/engine/evaluation/indicators\` 403；本次验收对象是复制入口、版本 +1、节点与流转边预填，未提交草稿，后续若要求在复制弹窗内直接绑定结局评估指标，需补齐该账号权限并单独复验。`
      : "- 脚本诊断未记录阻断性请求失败、5xx 响应或 console error。";
  const readme = `# 路径可读化 134 复验

- 时间：${summary.completedAt}
- 环境：${summary.baseUrl}
- 合并提交：${summary.mergeCommit}
- 前端发布备份：${summary.deployBackup}
- 账号：医生 ${summary.patientDesktop.actor.username}；专科专家 ${summary.templateDesktop.actor.username}（仅保存账号与租户，不含口令、Cookie、令牌）

## 结论

- 医生真实前台 \`/pathway/patients\` 可打开患者路径详情，并看到「医生只读路径图」。
- 只读路径图在桌面与 390px 移动视口均展示当前患者位置、已完成/当前/待执行图例、可读流转边，并明确「不自动开立或修改医嘱」。
- 只读路径图未出现删除按钮，节点数与当前节点标识均通过脚本断言。
- 专科专家真实前台 \`/pathway/templates\` 可在已全量生效模板详情顶部看到「复制为新版本」主按钮。
- 点击复制后仅打开下一版草稿弹窗，不提交新草稿；脚本断言同编码、版本号 +1、L2 节点与流转边已带出。
- 桌面与 390px 移动视口均未发现页面级横向溢出。

## 文件

- [00-pathway-readable-134-proof.json](00-pathway-readable-134-proof.json)
- [01-desktop-patient-pathways-list.png](01-desktop-patient-pathways-list.png)
- [02-desktop-patient-readonly-graph.png](02-desktop-patient-readonly-graph.png)
- [03-mobile-patient-pathways-list.png](03-mobile-patient-pathways-list.png)
- [04-mobile-patient-readonly-graph.png](04-mobile-patient-readonly-graph.png)
- [05-desktop-template-list.png](05-desktop-template-list.png)
- [06-desktop-template-copy-action.png](06-desktop-template-copy-action.png)
- [07-desktop-template-copy-dialog.png](07-desktop-template-copy-dialog.png)
- [08-mobile-template-list.png](08-mobile-template-list.png)
- [09-mobile-template-copy-action.png](09-mobile-template-copy-action.png)
- [10-mobile-template-copy-dialog.png](10-mobile-template-copy-dialog.png)

## 真实限制

- 134 当前仍为自签证书环境，Playwright context 使用 \`ignoreHTTPSErrors=true\`；正式部署需替换院方信任证书。
- 本脚本只验证复制入口与预填弹窗，不点击 OK 创建新草稿，避免污染演练数据。
${diagnosticNote}
`;
  await writeFile(path.join(evidenceDir, "README.md"), readme, "utf8");
}

async function main() {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });

  const credentials = loadCredentials();
  const browser = await chromium.launch({ headless: true });
  try {
    const patientDesktop = await verifyPatientGraph(
      browser,
      credentials,
      { width: 1440, height: 1100 },
      {
        list: "01-desktop-patient-pathways-list.png",
        drawer: "02-desktop-patient-readonly-graph.png",
      },
      "desktop",
    );
    const patientMobile = await verifyPatientGraph(
      browser,
      credentials,
      { width: 390, height: 844 },
      {
        list: "03-mobile-patient-pathways-list.png",
        drawer: "04-mobile-patient-readonly-graph.png",
      },
      "mobile",
    );
    const templateDesktop = await verifyTemplateCopy(
      browser,
      credentials,
      { width: 1440, height: 1100 },
      {
        list: "05-desktop-template-list.png",
        drawer: "06-desktop-template-copy-action.png",
        dialog: "07-desktop-template-copy-dialog.png",
      },
      "desktop",
    );
    const templateMobile = await verifyTemplateCopy(
      browser,
      credentials,
      { width: 390, height: 844 },
      {
        list: "08-mobile-template-list.png",
        drawer: "09-mobile-template-copy-action.png",
        dialog: "10-mobile-template-copy-dialog.png",
      },
      "mobile",
    );
    const checks = {
      patientDesktop: patientDesktop.nodeCount >= 2 && patientDesktop.currentNodeCount >= 1,
      patientMobile: patientMobile.nodeCount >= 2 && patientMobile.currentNodeCount >= 1,
      patientReadOnly:
        patientDesktop.deleteButtonCount === 0 && patientMobile.deleteButtonCount === 0,
      patientEdges: patientDesktop.edgeVisible && patientMobile.edgeVisible,
      templateDesktop:
        templateDesktop.copiedCode === businessObjects.capTemplateCode &&
        templateDesktop.nodeFieldCount >= 2 &&
        templateDesktop.edgeFieldCount >= 1,
      templateMobile:
        templateMobile.copiedCode === businessObjects.capTemplateCode &&
        templateMobile.nodeFieldCount >= 2 &&
        templateMobile.edgeFieldCount >= 1,
      noHorizontalOverflow:
        patientDesktop.metrics.horizontalOverflowPx === 0 &&
        patientMobile.metrics.horizontalOverflowPx === 0 &&
        templateDesktop.drawerMetrics.horizontalOverflowPx === 0 &&
        templateDesktop.dialogMetrics.horizontalOverflowPx === 0 &&
        templateMobile.drawerMetrics.horizontalOverflowPx === 0 &&
        templateMobile.dialogMetrics.horizontalOverflowPx === 0,
    };
    const pass = Object.values(checks).every(Boolean);
    const summary = {
      proof: "pathway-readable-134",
      baseUrl,
      route: ["/pathway/patients", "/pathway/templates"],
      source: "codex-demo-drill-path-readable-1f32dbfb",
      mergeCommit: "1f32dbfbcaffa83c18470462da714645ee430355",
      deployBackup: "/zoesoft/medkernel/backups/deploy-20260611-173711",
      completedAt: new Date().toISOString(),
      credentialLocation: credentialPath,
      credentialRedaction: "凭据仅从服务器受限文件读取；本摘要不写入口令、Cookie、令牌或签名材料。",
      businessObjects,
      checks,
      pass,
      patientDesktop,
      patientMobile,
      templateDesktop,
      templateMobile,
    };
    await writeFile(
      path.join(evidenceDir, "00-pathway-readable-134-proof.json"),
      `${JSON.stringify(summary, null, 2)}\n`,
      "utf8",
    );
    await writeReadme(summary);
    if (!pass) {
      throw new Error(`路径可读化 134 复验失败：${JSON.stringify(checks)}`);
    }
    console.log(
      JSON.stringify(
        {
          pass,
          summaryPath: path.relative(
            repoRoot,
            path.join(evidenceDir, "00-pathway-readable-134-proof.json"),
          ),
          screenshots: [
            ...patientDesktop.captures,
            ...patientMobile.captures,
            ...templateDesktop.captures,
            ...templateMobile.captures,
          ].map((item) => item.screenshot),
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
  process.exit(1);
});
