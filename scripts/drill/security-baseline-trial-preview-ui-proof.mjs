#!/usr/bin/env node
// 幕10/P1 UI-ACT10-SECBASE-01：安全基线页权限试算与脱敏预览 134 前台复验。
// 产出：
// - docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/00-security-baseline-trial-preview-134-proof.json
// - docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/README.md
// - docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/NN-*.png
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
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验",
);
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";
const runTag = `security-baseline-proof-${Date.now().toString(36)}`;

const actors = {
  hospitalAdmin: "drill-hospital-20260611:drill-hospital-admin-20260611",
};

const businessObjects = {
  dataPermission: {
    resourceType: "act10_patient_scope",
    action: "READ",
    hospitalId: "01KTSAC1JJB2V2X4F9DBF1NSVR",
    departmentId: "01KTSAC1SCFJB2RKCY3DHEAGMQ",
    requestedColumns: ["patientId", "encounterId", "departmentId", "patientName", "idNo"],
  },
  masking: {
    resourceType: "act10_patient_export",
    scenarioCode: "DEFAULT",
    sensitiveFields: ["patientName", "idNo"],
    values: {
      patientName: "张建国",
      idNo: "110101196203018888",
      encounterId: "enc-act6-8oh7bn024a",
    },
    expectedValues: {
      patientName: "张*国",
      idNo: "**************8888",
    },
  },
};

const deployed = {
  host: "193.112.107.134",
  source: "codex-demo-drill-security-baseline-trial-preview-13a93a5b",
  mainCommit: "13a93a5b",
  backup: "/zoesoft/medkernel/backups/deploy-20260611-193235",
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

function firstData(response) {
  return response.json?.data ?? response.json;
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

async function dismissFloatingLayers(page) {
  await page.keyboard.press("Escape").catch(() => undefined);
  await page.keyboard.press("Escape").catch(() => undefined);
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
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 25000 });
  await waitForQuiet(page);
  return {
    context,
    page,
    actor: publicActor(actor),
    diagnostics,
  };
}

async function apiRequest(page, method, apiPath, traceId, body) {
  return page.evaluate(
    async ({ method, apiPath, traceId, body }) => {
      const token = document.cookie
        .split("; ")
        .find((part) => part.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];
      const headers = {
        Accept: "application/json, text/plain",
        "X-MedKernel-Trace-Id": traceId,
      };
      if (body !== undefined) {
        headers["Content-Type"] = "application/json";
      }
      if (token && !["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase())) {
        headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
      }
      const response = await fetch(`/medkernel/api/v1${apiPath}`, {
        method,
        credentials: "same-origin",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      let json = null;
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        json = { raw: text.slice(0, 500) };
      }
      return {
        ok: response.ok,
        status: response.status,
        traceId: response.headers.get("x-trace-id"),
        path: apiPath,
        json,
      };
    },
    { method, apiPath, traceId, body },
  );
}

async function readOrgUnits(page) {
  const response = await apiRequest(
    page,
    "GET",
    "/engine/org/org-units?page=1&size=500&sort=name,asc&status=ACTIVE",
    `${runTag}-org-units-preflight`,
  );
  if (response.status !== 200) {
    throw new Error(`组织目录预检失败：status=${response.status}`);
  }
  return firstData(response)?.items ?? [];
}

function requireOrgUnit(orgUnits, id, label) {
  const unit = orgUnits.find((item) => item.id === id || item.code === id);
  if (!unit) {
    throw new Error(`${label} 未在组织目录中找到：${id}`);
  }
  return unit;
}

async function readProofTargets(page) {
  const dataPolicy = await apiRequest(
    page,
    "GET",
    `/compliance/data-permissions?resourceType=${encodeURIComponent(
      businessObjects.dataPermission.resourceType,
    )}&action=${businessObjects.dataPermission.action}`,
    `${runTag}-data-policy-preflight`,
  );
  const maskingRules = await apiRequest(
    page,
    "GET",
    `/compliance/masking-rules?resourceType=${encodeURIComponent(
      businessObjects.masking.resourceType,
    )}`,
    `${runTag}-masking-rules-preflight`,
  );
  const dataAccess = await apiRequest(
    page,
    "POST",
    "/compliance/data-permissions:check",
    `${runTag}-ui-data-check-preflight`,
    businessObjects.dataPermission,
  );
  const maskingPreview = await apiRequest(
    page,
    "POST",
    "/compliance/masking-rules:preview",
    `${runTag}-ui-masking-preview-preflight`,
    businessObjects.masking,
  );
  const dataPolicyItems = firstData(dataPolicy) ?? [];
  const maskingRuleItems = firstData(maskingRules) ?? [];
  const dataDecision = firstData(dataAccess);
  const maskingDecision = firstData(maskingPreview);
  if (dataPolicy.status !== 200 || dataPolicyItems.length === 0) {
    throw new Error(`数据权限策略预检失败：status=${dataPolicy.status}`);
  }
  if (maskingRules.status !== 200 || maskingRuleItems.length < 2) {
    throw new Error(`脱敏规则预检失败：status=${maskingRules.status}`);
  }
  if (
    dataAccess.status !== 200 ||
    dataDecision?.resourceType !== businessObjects.dataPermission.resourceType
  ) {
    throw new Error(`权限试算预检失败：status=${dataAccess.status}`);
  }
  if (
    maskingPreview.status !== 200 ||
    maskingDecision?.values?.patientName !== businessObjects.masking.expectedValues.patientName ||
    maskingDecision?.values?.idNo !== businessObjects.masking.expectedValues.idNo
  ) {
    throw new Error(`脱敏预览预检失败：status=${maskingPreview.status}`);
  }
  return {
    dataPolicy: {
      status: dataPolicy.status,
      traceId: dataPolicy.traceId,
      count: dataPolicyItems.length,
      firstPolicy: dataPolicyItems[0],
    },
    maskingRules: {
      status: maskingRules.status,
      traceId: maskingRules.traceId,
      count: maskingRuleItems.length,
      fields: maskingRuleItems.map((item) => item.fieldName),
    },
    dataAccess: {
      status: dataAccess.status,
      traceId: dataAccess.traceId,
      decision: dataDecision,
    },
    maskingPreview: {
      status: maskingPreview.status,
      traceId: maskingPreview.traceId,
      result: maskingDecision,
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

function selectRoot(page, formName, fieldName) {
  return page
    .locator(`#${formName}_${fieldName}`)
    .locator(
      "xpath=ancestor::div[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
    );
}

async function clearSelectTags(root) {
  for (let index = 0; index < 12; index += 1) {
    const removeButtons = root.locator(".ant-select-selection-item-remove");
    const count = await removeButtons.count();
    if (count === 0) return;
    await removeButtons.first().click();
  }
}

async function setTagSelect(page, formName, fieldName, values) {
  const root = selectRoot(page, formName, fieldName);
  await root.waitFor({ state: "visible", timeout: 15000 });
  await clearSelectTags(root);
  const input = page.locator(`#${formName}_${fieldName}`);
  for (const value of values) {
    await root.click();
    await input.fill(value);
    const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
    const option = dropdown.locator(".ant-select-item-option").filter({ hasText: value }).first();
    await option.waitFor({ state: "visible", timeout: 5000 });
    await option.click();
    await root
      .locator(".ant-select-selection-item")
      .filter({ hasText: value })
      .first()
      .waitFor({ state: "visible", timeout: 5000 });
  }
  await page.keyboard.press("Escape");
}

async function selectStaticOption(page, formName, fieldName, optionText) {
  const root = selectRoot(page, formName, fieldName);
  await root.waitFor({ state: "visible", timeout: 15000 });
  await root.click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await dropdown.locator(".ant-select-item-option").filter({ hasText: optionText }).first().click();
}

async function selectOrgOption(page, formName, fieldName, unit) {
  const root = selectRoot(page, formName, fieldName);
  await root.waitFor({ state: "visible", timeout: 15000 });
  const currentText = await root.innerText().catch(() => "");
  if (currentText.includes(unit.code) || currentText.includes(unit.name)) {
    return;
  }
  await root.hover();
  const clearButton = root.locator(".ant-select-clear");
  if ((await clearButton.count()) > 0) {
    await clearButton.first().click({ force: true });
  }
  await root.click();
  const input = page.locator(`#${formName}_${fieldName}`);
  await input.fill(unit.code);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  const option = dropdown.locator(".ant-select-item-option").filter({ hasText: unit.code }).first();
  await option.waitFor({ state: "visible", timeout: 15000 });
  await option.click();
}

async function gotoSecurityBaseline(page) {
  await page.goto(`${baseUrl}/security/baseline`, { waitUntil: "domcontentloaded" });
  await page.getByText("安全基线与系统配置", { exact: false }).first().waitFor({
    timeout: 25000,
  });
  await waitForQuiet(page);
}

async function clickSecurityTab(page, tabName, readyText) {
  await page.getByRole("tab", { name: tabName }).click();
  await page.getByText(readyText, { exact: false }).first().waitFor({ timeout: 25000 });
  await waitForQuiet(page);
}

async function fillDataPermissionTrial(page, orgUnits) {
  const hospital = requireOrgUnit(orgUnits, businessObjects.dataPermission.hospitalId, "医院");
  const department = requireOrgUnit(orgUnits, businessObjects.dataPermission.departmentId, "科室");
  await page
    .locator("#dataPermissionTrial_resourceType")
    .fill(businessObjects.dataPermission.resourceType);
  await selectStaticOption(page, "dataPermissionTrial", "action", "读取");
  await setTagSelect(
    page,
    "dataPermissionTrial",
    "requestedColumns",
    businessObjects.dataPermission.requestedColumns,
  );
  await selectOrgOption(page, "dataPermissionTrial", "hospitalId", hospital);
  await selectOrgOption(page, "dataPermissionTrial", "departmentId", department);
  await dismissFloatingLayers(page);
  return {
    hospital,
    department,
  };
}

async function fillMaskingPreview(page) {
  await page.locator("#maskingPreview_resourceType").fill(businessObjects.masking.resourceType);
  await page.locator("#maskingPreview_scenarioCode").fill(businessObjects.masking.scenarioCode);
  await setTagSelect(
    page,
    "maskingPreview",
    "sensitiveFields",
    businessObjects.masking.sensitiveFields,
  );
  await page
    .locator("#maskingPreview_valuesJson")
    .fill(JSON.stringify(businessObjects.masking.values, null, 2));
  await dismissFloatingLayers(page);
}

async function submitAndReadJson(page, pathSuffix, submitButtonName) {
  const responsePromise = page.waitForResponse(
    (response) =>
      response.url().includes(pathSuffix) && response.request().method().toUpperCase() === "POST",
    { timeout: 25000 },
  );
  await page.getByRole("button", { name: submitButtonName }).click();
  const response = await responsePromise;
  const json = await response.json();
  if (response.status() !== 200) {
    throw new Error(`${submitButtonName} 响应异常：status=${response.status()}`);
  }
  return {
    status: response.status(),
    traceId: response.headers()["x-trace-id"] ?? null,
    json,
  };
}

async function replayViewport(browser, credentials, viewportCase, preflight, orgUnits) {
  const { context, page, actor, diagnostics } = await login(
    browser,
    credentials,
    actors.hospitalAdmin,
    viewportCase.viewport,
  );
  const screenshots = [];
  const layoutMetrics = [];
  try {
    await gotoSecurityBaseline(page);
    await clickSecurityTab(page, "数据权限", "权限试算");
    const selectedScope = await fillDataPermissionTrial(page, orgUnits);
    const dataFormMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 权限试算表单`, dataFormMetrics);
    layoutMetrics.push({ step: "data-permission-form", ...dataFormMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.dataFormScreenshot,
        `${viewportCase.label} 数据权限页填写权限试算`,
        actor,
        viewportCase.label,
      ),
    );
    const dataPost = await submitAndReadJson(
      page,
      "/compliance/data-permissions:check",
      "执行权限试算",
    );
    await page.getByText("行级结果", { exact: false }).first().waitFor({ timeout: 15000 });
    await page
      .getByText(preflight.dataAccess.decision.policyId, { exact: false })
      .first()
      .waitFor({ timeout: 15000 });
    await page.getByText("拒绝字段", { exact: false }).first().scrollIntoViewIfNeeded();
    const dataResult = dataPost.json?.data;
    if (dataResult?.resourceType !== businessObjects.dataPermission.resourceType) {
      throw new Error(`权限试算未返回目标资源：${JSON.stringify(dataResult)}`);
    }
    const dataResultMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 权限试算结果`, dataResultMetrics);
    layoutMetrics.push({ step: "data-permission-result", ...dataResultMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.dataResultScreenshot,
        `${viewportCase.label} 权限试算返回后端裁决结果`,
        actor,
        viewportCase.label,
      ),
    );

    await clickSecurityTab(page, "脱敏规则", "脱敏预览");
    await fillMaskingPreview(page);
    const maskingFormMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 脱敏预览表单`, maskingFormMetrics);
    layoutMetrics.push({ step: "masking-preview-form", ...maskingFormMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.maskingFormScreenshot,
        `${viewportCase.label} 脱敏规则页填写脱敏预览`,
        actor,
        viewportCase.label,
      ),
    );
    const maskingPost = await submitAndReadJson(
      page,
      "/compliance/masking-rules:preview",
      "执行脱敏预览",
    );
    await page.getByText("已按规则脱敏", { exact: false }).first().waitFor({ timeout: 15000 });
    const patientNameValue = page
      .getByText(businessObjects.masking.expectedValues.patientName, { exact: false })
      .first();
    await patientNameValue.waitFor({ timeout: 15000 });
    const idNoValue = page
      .getByText(businessObjects.masking.expectedValues.idNo, { exact: false })
      .first();
    await idNoValue.waitFor({ timeout: 15000 });
    await idNoValue.scrollIntoViewIfNeeded();
    const maskingResult = maskingPost.json?.data;
    if (
      maskingResult?.values?.patientName !== businessObjects.masking.expectedValues.patientName ||
      maskingResult?.values?.idNo !== businessObjects.masking.expectedValues.idNo
    ) {
      throw new Error(`脱敏预览未返回目标遮罩结果：${JSON.stringify(maskingResult)}`);
    }
    const maskingResultMetrics = await getLayoutMetrics(page);
    assertNoPageOverflow(`${viewportCase.label} 脱敏预览结果`, maskingResultMetrics);
    layoutMetrics.push({ step: "masking-preview-result", ...maskingResultMetrics });
    screenshots.push(
      await capture(
        browser,
        page,
        viewportCase.maskingResultScreenshot,
        `${viewportCase.label} 脱敏预览返回字段级输出`,
        actor,
        viewportCase.label,
      ),
    );

    return {
      label: viewportCase.label,
      actor,
      selectedScope: {
        hospital: {
          id: selectedScope.hospital.id,
          code: selectedScope.hospital.code,
          name: selectedScope.hospital.name,
        },
        department: {
          id: selectedScope.department.id,
          code: selectedScope.department.code,
          name: selectedScope.department.name,
        },
      },
      responses: {
        dataPermission: {
          status: dataPost.status,
          traceId: dataPost.traceId,
          result: dataResult,
        },
        maskingPreview: {
          status: maskingPost.status,
          traceId: maskingPost.traceId,
          result: maskingResult,
        },
      },
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

  const content = `# 安全基线试算预览 134 复验

## 结论

- 目标：\`UI-ACT10-SECBASE-01\`。
- 环境：134，source \`${proof.deployed.source}\`，main \`${proof.deployed.mainCommit}\`。
- 发布备份：\`${proof.deployed.backup}\`。
- 后端 jar SHA-256：\`${proof.deployed.jarSha256}\`，readiness：\`${proof.deployed.readiness}\`。
- 复验账号：${actorLabel}（\`${proof.actor.username}\`），凭据仅从 \`${proof.credentialLocation}\` 读取，证据不写入口令、Cookie 或令牌。
- 结果：\`pass=${proof.pass}\`；\`/security/baseline\` 桌面与 390px 移动视口均可在前台提交权限试算和脱敏预览。
- 权限试算：资源 \`${proof.businessObjects.dataPermission.resourceType}\`，动作 \`${proof.businessObjects.dataPermission.action}\`，命中策略 \`${proof.preflight.dataAccess.decision.policyId}\`，桌面 / 移动 POST 均为 200。
- 脱敏预览：资源 \`${proof.businessObjects.masking.resourceType}\`，字段 \`${proof.businessObjects.masking.sensitiveFields.join(", ")}\`，后端返回 \`${proof.businessObjects.masking.expectedValues.patientName}\` / \`${proof.businessObjects.masking.expectedValues.idNo}\`，桌面 / 移动 POST 均为 200。

## 截图证据

| 视口 | 证据 | 文件 |
|---|---|---|
${screenshotRows}

## 机器可读证据

- [00-security-baseline-trial-preview-134-proof.json](./00-security-baseline-trial-preview-134-proof.json)
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
  const browser = await chromium.launch({ headless: true });

  try {
    const preflightSession = await login(browser, credentials, actors.hospitalAdmin, {
      width: 1280,
      height: 900,
    });
    const orgUnits = await readOrgUnits(preflightSession.page);
    const preflight = await readProofTargets(preflightSession.page);
    await preflightSession.context.close();

    const viewports = [];
    for (const viewportCase of [
      {
        label: "desktop-1440",
        viewport: { width: 1440, height: 1100 },
        dataFormScreenshot: "01-desktop-data-permission-form.png",
        dataResultScreenshot: "02-desktop-data-permission-result.png",
        maskingFormScreenshot: "03-desktop-masking-form.png",
        maskingResultScreenshot: "04-desktop-masking-result.png",
      },
      {
        label: "mobile-390",
        viewport: { width: 390, height: 900 },
        dataFormScreenshot: "05-mobile-data-permission-form.png",
        dataResultScreenshot: "06-mobile-data-permission-result.png",
        maskingFormScreenshot: "07-mobile-masking-form.png",
        maskingResultScreenshot: "08-mobile-masking-result.png",
      },
    ]) {
      viewports.push(await replayViewport(browser, credentials, viewportCase, preflight, orgUnits));
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

    const checks = {
      dataPermissionPost200: viewports.every(
        (viewport) => viewport.responses.dataPermission.status === 200,
      ),
      dataPermissionUsesAct10Resource: viewports.every(
        (viewport) =>
          viewport.responses.dataPermission.result.resourceType ===
          businessObjects.dataPermission.resourceType,
      ),
      maskingPreviewPost200: viewports.every(
        (viewport) => viewport.responses.maskingPreview.status === 200,
      ),
      maskedPatientName: viewports.every(
        (viewport) =>
          viewport.responses.maskingPreview.result.values.patientName ===
          businessObjects.masking.expectedValues.patientName,
      ),
      maskedIdNo: viewports.every(
        (viewport) =>
          viewport.responses.maskingPreview.result.values.idNo ===
          businessObjects.masking.expectedValues.idNo,
      ),
      desktopNoPageOverflow: viewports[0].layoutMetrics.every(
        (item) => item.horizontalOverflowPx <= 1,
      ),
      mobileNoPageOverflow: viewports[1].layoutMetrics.every(
        (item) => item.horizontalOverflowPx <= 1,
      ),
      noFailedRequestsOrConsoleErrors: allDiagnostics.length === 0,
    };
    const pass = Object.values(checks).every(Boolean);
    const proof = {
      proof: "security-baseline-trial-preview-134",
      runTag,
      generatedAt: new Date().toISOString(),
      baseUrl,
      deployed,
      credentialLocation: credentialPath,
      credentialRedaction: "凭据仅从服务器受限文件读取；证据不写入口令、Cookie、令牌或签名材料。",
      businessObjects,
      actor: viewports[0].actor,
      preflight,
      viewports,
      checks,
      pass,
    };
    await writeFile(
      path.join(evidenceDir, "00-security-baseline-trial-preview-134-proof.json"),
      `${JSON.stringify(proof, null, 2)}\n`,
      "utf8",
    );
    await writeReadme(proof);
    if (!pass) {
      throw new Error(`安全基线试算预览 134 复验失败：${JSON.stringify(checks)}`);
    }
    console.log(
      JSON.stringify(
        {
          pass,
          evidenceDir,
          dataPermissionStatus: viewports.map(
            (viewport) => viewport.responses.dataPermission.status,
          ),
          maskingPreviewStatus: viewports.map(
            (viewport) => viewport.responses.maskingPreview.status,
          ),
          screenshots: viewports.flatMap((viewport) =>
            viewport.screenshots.map((item) => item.screenshot),
          ),
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
