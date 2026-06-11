#!/usr/bin/env node
// 幕10/P1 UI-ACT10-AUDIT-01：审计 traceId 直搜与诊断链跳转 134 前台复验。
// 产出：
// - docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/00-audit-trace-diagnosis-134-proof.json
// - docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/README.md
// - docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/NN-*.png
import { execFileSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const requireFromFrontend = createRequire(new URL("../../frontend/package.json", import.meta.url));
const { chromium } = requireFromFrontend("playwright");
const prettier = requireFromFrontend("prettier");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验",
);
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";
const proofTraceId = process.env.DRILL_AUDIT_TRACE_ID ?? "act6-8oh7bn024a-k-event";
const runTag = `audit-trace-proof-${Date.now().toString(36)}`;

const actors = {
  audit: "drill-hospital-20260611:drill-audit-20260611",
};

const deployed = {
  host: "193.112.107.134",
  source: "codex-demo-drill-audit-trace-jump-74353b56",
  mainCommit: "74353b56edc3a8f6c4550e6a301359642c6f5334",
  backup: "/zoesoft/medkernel/backups/deploy-20260611-183604",
  jarSha256: "51fdd05aabaad6b51f4016eeec9a4bcb0f4ff7934f5cb641c4117f651c90679e",
  readiness: "UP",
};

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

async function capture(browser, page, filename, label, actor, viewportLabel) {
  const finalPath = path.join(evidenceDir, filename);
  const rawPath = path.join(evidenceDir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url(), page.viewportSize().width);
  return {
    label,
    actor,
    viewportLabel,
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
    diagnostics.failedRequests.push({
      url: request.url(),
      method: request.method(),
      errorText: request.failure()?.errorText,
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

async function login(browser, credentials, viewport) {
  const actor = credentials[actors.audit];
  if (!actor) throw new Error(`找不到复验账号：${actors.audit}`);
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
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 25000 });
  await waitForQuiet(page);
  return {
    context,
    page,
    actor: publicActor(actor),
    diagnostics,
  };
}

async function apiJson(page, apiPath, traceId) {
  return page.evaluate(
    async ({ apiPath, traceId }) => {
      const token = document.cookie
        .split("; ")
        .find((part) => part.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];
      const headers = {
        Accept: "application/json, text/plain",
        "X-MedKernel-Trace-Id": traceId,
      };
      if (token) headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
      const response = await fetch(`/medkernel/api/v1${apiPath}`, {
        method: "GET",
        credentials: "same-origin",
        headers,
      });
      const text = await response.text();
      let json = null;
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        json = { raw: text.slice(0, 500) };
      }
      return { ok: response.ok, status: response.status, json };
    },
    { apiPath, traceId },
  );
}

async function readProofTarget(page, traceId) {
  const list = await apiJson(
    page,
    `/large-lists/audit-events/list?size=20&sort=id,desc&traceId=${encodeURIComponent(traceId)}`,
    `${runTag}-list-preflight`,
  );
  const diagnosis = await apiJson(
    page,
    `/engine/diagnose/traces/${encodeURIComponent(traceId)}`,
    `${runTag}-diagnosis-preflight`,
  );
  const items = list.json?.data?.items ?? [];
  const diagnosisData = diagnosis.json?.data;
  if (list.status !== 200 || diagnosis.status !== 200) {
    throw new Error(
      `预检失败：list=${list.status} diagnosis=${diagnosis.status} traceId=${traceId}`,
    );
  }
  if (items.length === 0) {
    throw new Error(`预检失败：审计列表未返回 traceId=${traceId} 的事件`);
  }
  if (!items.every((item) => item.traceId === traceId)) {
    throw new Error(`预检失败：审计列表返回了非目标 traceId 的事件`);
  }
  if (!diagnosisData || (diagnosisData.stateHistory?.length ?? 0) === 0) {
    throw new Error(`预检失败：诊断链未返回状态流转 traceId=${traceId}`);
  }
  return {
    traceId,
    auditList: {
      status: list.status,
      count: items.length,
      totalEstimate: list.json?.data?.totalEstimate ?? null,
      firstEvent: {
        eventId: items[0].eventId,
        actionCode: items[0].actionCode,
        resourceType: items[0].resourceType,
        resourceId: items[0].resourceId,
        summary: items[0].summary,
        traceId: items[0].traceId,
      },
    },
    diagnosis: {
      status: diagnosis.status,
      traceId: diagnosisData.traceId,
      stateCount: diagnosisData.stateHistory?.length ?? 0,
      payloadCount: diagnosisData.payloads?.length ?? 0,
      firstState: diagnosisData.stateHistory?.[0] ?? null,
      firstPayload: diagnosisData.payloads?.[0] ?? null,
    },
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

function assertNoPageOverflow(label, metrics) {
  if (metrics.horizontalOverflowPx > 1) {
    throw new Error(
      `${label} 存在页面级横向溢出 ${metrics.horizontalOverflowPx}px：${JSON.stringify(
        metrics.overflowingNodes,
      )}`,
    );
  }
}

async function replayViewport(browser, credentials, viewportCase, target) {
  const { context, page, actor, diagnostics } = await login(
    browser,
    credentials,
    viewportCase.viewport,
  );
  const screenshots = [];
  const layoutMetrics = [];
  try {
    await page.goto(`${baseUrl}/admin/audit`, { waitUntil: "domcontentloaded" });
    await page.getByText("审计日志", { exact: false }).first().waitFor({ timeout: 25000 });
    await waitForQuiet(page);

    const search = page.getByLabel("Trace ID 搜索");
    const filteredResponse = page.waitForResponse(
      (response) => {
        const url = new URL(response.url());
        return (
          url.pathname.endsWith("/large-lists/audit-events/list") &&
          url.searchParams.get("traceId") === target.traceId
        );
      },
      { timeout: 25000 },
    );
    await search.fill(target.traceId);
    await search.press("Enter");
    const response = await filteredResponse;
    const responseBody = await response.json();
    const responseItems = responseBody?.data?.items ?? [];
    if (response.status() !== 200 || responseItems.length === 0) {
      throw new Error(
        `${viewportCase.label} Trace ID 搜索响应异常：status=${response.status()} count=${responseItems.length}`,
      );
    }
    if (!responseItems.every((item) => item.traceId === target.traceId)) {
      throw new Error(`${viewportCase.label} Trace ID 搜索响应包含非目标 traceId 事件`);
    }
    const targetRows = page.locator(".ant-table-row");
    await targetRows.first().waitFor({ timeout: 25000 });
    const listMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} Trace ID 搜索结果`, listMetrics);
    layoutMetrics.push({ step: "trace-search", ...listMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.listScreenshot,
        `${viewportCase.label} 审计页按 Trace ID 直搜命中真实事件`,
        actor,
        viewportCase.label,
      ),
    );

    await targetRows.first().locator('button[aria-label^="查看详情"]').first().click();
    await page.getByText("审计事件详情", { exact: false }).first().waitFor({ timeout: 15000 });
    const drawer = page.locator(".ant-drawer:visible").last();
    await drawer.getByRole("button", { name: /打开诊断链/ }).waitFor({ timeout: 15000 });
    const detailMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 审计详情`, detailMetrics);
    layoutMetrics.push({ step: "audit-detail", ...detailMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.detailScreenshot,
        `${viewportCase.label} 审计详情显示 Trace ID 与诊断链入口`,
        actor,
        viewportCase.label,
      ),
    );

    await drawer.getByRole("button", { name: /打开诊断链/ }).click();
    const stateText =
      target.diagnosis.firstState?.toStatus ??
      target.diagnosis.firstState?.reason ??
      target.traceId;
    const stateLocator = drawer.getByText(stateText, { exact: false }).first();
    await stateLocator.waitFor({ timeout: 25000 });
    await stateLocator.scrollIntoViewIfNeeded();
    const stateMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 诊断链状态流转`, stateMetrics);
    layoutMetrics.push({ step: "trace-diagnosis-state", ...stateMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.stateScreenshot,
        `${viewportCase.label} 诊断链展示状态流转`,
        actor,
        viewportCase.label,
      ),
    );

    let payloadLocator;
    if (target.diagnosis.payloadCount > 0) {
      payloadLocator = drawer
        .getByText(target.diagnosis.firstPayload.digest, { exact: false })
        .first();
    } else {
      payloadLocator = drawer.getByText("无 Payload 摘要", { exact: false }).first();
    }
    await payloadLocator.waitFor({ timeout: 15000 });
    await payloadLocator.scrollIntoViewIfNeeded();
    const payloadMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 诊断链 Payload 摘要`, payloadMetrics);
    layoutMetrics.push({ step: "trace-diagnosis-payload", ...payloadMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.payloadScreenshot,
        `${viewportCase.label} 诊断链展示 Payload 摘要区域`,
        actor,
        viewportCase.label,
      ),
    );

    return {
      label: viewportCase.label,
      actor,
      rowCountOnScreen: await targetRows.count(),
      screenshots,
      layoutMetrics,
      diagnostics,
    };
  } finally {
    await context.close();
  }
}

async function writeReadme(proof) {
  const actorLabel = proof.actor.displayName ?? proof.actor.username;
  const screenshotRows = proof.viewports
    .flatMap((viewport) => viewport.screenshots)
    .map(
      (shot) =>
        `| ${shot.viewportLabel} | ${shot.label} | [${shot.screenshot}](./${encodeURI(
          shot.screenshot,
        )}) |`,
    )
    .join("\n");

  const content = `# 审计 traceId 诊断链 134 复验

## 结论

- 目标：\`UI-ACT10-AUDIT-01\`。
- 环境：134，source \`${proof.deployed.source}\`，main \`${proof.deployed.mainCommit}\`。
- 发布备份：\`${proof.deployed.backup}\`。
- 后端 jar SHA-256：\`${proof.deployed.jarSha256}\`，readiness：\`${proof.deployed.readiness}\`。
- 复验账号：${actorLabel}（\`${proof.actor.username}\`）。
- 复验 Trace ID：\`${proof.target.traceId}\`。
- 结果：\`pass=${proof.pass}\`；审计列表 traceId 直搜命中 ${proof.target.auditList.count} 条真实事件，诊断链返回 ${proof.target.diagnosis.stateCount} 条状态流转，Payload 摘要数 ${proof.target.diagnosis.payloadCount}（当前数据为空时页面显示「无 Payload 摘要」空态）。

## 截图证据

| 视口 | 证据 | 文件 |
|---|---|---|
${screenshotRows}

## 机器可读证据

- [00-audit-trace-diagnosis-134-proof.json](./00-audit-trace-diagnosis-134-proof.json)
`;

  const formatted = await prettier.format(content, {
    parser: "markdown",
    printWidth: 100,
  });
  await writeFile(path.join(evidenceDir, "README.md"), formatted, "utf8");
}

async function main() {
  await rm(evidenceDir, { recursive: true, force: true });
  await mkdir(evidenceDir, { recursive: true });
  const credentials = loadCredentials();
  const browser = await chromium.launch();

  try {
    const preflight = await login(browser, credentials, { width: 1280, height: 900 });
    const target = await readProofTarget(preflight.page, proofTraceId);
    await preflight.context.close();

    const viewports = [];
    for (const viewportCase of [
      {
        label: "desktop-1440",
        viewport: { width: 1440, height: 1000 },
        listScreenshot: "01-desktop-audit-trace-search.png",
        detailScreenshot: "02-desktop-audit-detail-trace-entry.png",
        stateScreenshot: "03-desktop-trace-diagnosis-state.png",
        payloadScreenshot: "04-desktop-trace-diagnosis-payload.png",
      },
      {
        label: "mobile-390",
        viewport: { width: 390, height: 900 },
        listScreenshot: "05-mobile-audit-trace-search.png",
        detailScreenshot: "06-mobile-audit-detail-trace-entry.png",
        stateScreenshot: "07-mobile-trace-diagnosis-state.png",
        payloadScreenshot: "08-mobile-trace-diagnosis-payload.png",
      },
    ]) {
      viewports.push(await replayViewport(browser, credentials, viewportCase, target));
    }

    const allDiagnostics = viewports.flatMap((viewport) => [
      ...viewport.diagnostics.failedRequests,
      ...viewport.diagnostics.serverErrors,
      ...viewport.diagnostics.consoleErrors,
    ]);
    if (allDiagnostics.length > 0) {
      throw new Error(
        `复验期间出现失败请求/服务端错误/控制台错误：${JSON.stringify(allDiagnostics)}`,
      );
    }

    const proof = {
      runTag,
      generatedAt: new Date().toISOString(),
      baseUrl,
      deployed,
      pass: true,
      actor: viewports[0].actor,
      target,
      viewports,
      criteria: {
        traceIdSearchIsBackendFiltered: target.auditList.count > 0,
        diagnosisJumpShowsStateHistory: target.diagnosis.stateCount > 0,
        payloadSummaryAreaReadable: true,
        desktopNoPageOverflow: viewports[0].layoutMetrics.every(
          (item) => item.horizontalOverflowPx <= 1,
        ),
        mobileNoPageOverflow: viewports[1].layoutMetrics.every(
          (item) => item.horizontalOverflowPx <= 1,
        ),
      },
    };
    await writeFile(
      path.join(evidenceDir, "00-audit-trace-diagnosis-134-proof.json"),
      `${JSON.stringify(proof, null, 2)}\n`,
      "utf8",
    );
    await writeReadme(proof);
    console.log(
      JSON.stringify(
        {
          pass: proof.pass,
          evidenceDir,
          traceId: target.traceId,
          auditEvents: target.auditList.count,
          stateTransitions: target.diagnosis.stateCount,
          payloads: target.diagnosis.payloadCount,
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
