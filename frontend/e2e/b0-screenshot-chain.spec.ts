import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { ensureReadySession, expectLoginPageReady } from "./support/auth";

type RuntimeCollectors = {
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

type RuntimeRecord = RuntimeCollectors & {
  stage: string;
  url: string;
};

test.describe.configure({ mode: "serial" });

test.describe("B0 第一阶段项目 Playwright 截图证据链", () => {
  test("登录、用户菜单、规则维护与配置包发布弹窗均可截图且无系统错误", async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];

    try {
      await page.setViewportSize({ width: 1440, height: 960 });
      clearRuntime(runtime);
      await page.goto("/login", { waitUntil: "networkidle" });
      await expectLoginPageReady(page);
      await captureEvidence(page, testInfo, "b0-login-desktop");
      recordCleanRuntime(page, "登录页桌面截图", runtime, records);

      await ensureReadySession(page, "platform-knowledge-governor");
      await page.setViewportSize({ width: 1440, height: 960 });
      clearRuntime(runtime);
      await page.goto("/dashboard", { waitUntil: "networkidle" });
      await page.getByRole("button", { name: "当前用户菜单" }).click();
      const userMenu = page.getByRole("menu").filter({ hasText: "修改密码" });
      await expect(userMenu.getByRole("menuitem", { name: "修改密码" })).toBeVisible();
      await expect(userMenu.getByRole("menuitem", { name: "退出登录" })).toBeVisible();
      await captureEvidence(page, testInfo, "b0-header-user-menu");
      recordCleanRuntime(page, "登录后 Header 用户菜单", runtime, records);

      clearRuntime(runtime);
      await page.goto("/rule/definitions", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "规则中枢" })).toBeVisible();
      await expectNoRootOverflow(page, "规则中枢桌面");
      await captureEvidence(page, testInfo, "b0-rule-definitions-desktop");
      recordCleanRuntime(page, "规则中枢桌面截图", runtime, records);

      await page.setViewportSize({ width: 390, height: 844 });
      clearRuntime(runtime);
      await page.goto("/rule/definitions?b0-screenshot=390px", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "规则中枢" })).toBeVisible();
      await expectNoRootOverflow(page, "规则中枢 390px");
      await captureEvidence(page, testInfo, "b0-rule-definitions-390px");
      recordCleanRuntime(page, "规则中枢 390px 截图", runtime, records);

      await page.setViewportSize({ width: 1440, height: 960 });
      clearRuntime(runtime);
      await page.goto("/config/packages", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "配置包与发布" })).toBeVisible();
      await ensureConfigPackageDraft(page);
      const publishButton = page.getByRole("button", { name: "发布配置包" });
      await expect(publishButton).toBeEnabled();
      await publishButton.click();
      await expect(page.getByRole("dialog", { name: "院内同步发布中心" })).toBeVisible();
      await captureEvidence(page, testInfo, "b0-config-packages-release-modal");
      recordCleanRuntime(page, "配置包发布弹窗截图", runtime, records);
    } finally {
      await attachRuntimeRecords(testInfo, records);
    }
  });
});

function collectRuntime(page: Page): RuntimeCollectors {
  return {
    browserErrors: collectBrowserErrors(page),
    serverErrors: collectServerErrors(page),
    networkFailures: collectNetworkFailures(page),
  };
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      errors.push(message.text());
    }
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

function collectServerErrors(page: Page) {
  const errors: string[] = [];
  page.on("response", (response) => {
    if (response.status() >= 400 && response.url().includes("/medkernel/")) {
      errors.push(`${response.status()} ${response.request().method()} ${response.url()}`);
    }
  });
  return errors;
}

function collectNetworkFailures(page: Page) {
  const errors: string[] = [];
  page.on("requestfailed", (request) => {
    const failure = request.failure();
    const url = request.url();
    if (failure?.errorText === "net::ERR_ABORTED" || url.startsWith("data:")) {
      return;
    }
    errors.push(`${request.method()} ${url} ${failure?.errorText ?? "requestfailed"}`);
  });
  return errors;
}

function clearRuntime(runtime: RuntimeCollectors) {
  runtime.browserErrors.length = 0;
  runtime.serverErrors.length = 0;
  runtime.networkFailures.length = 0;
}

function recordCleanRuntime(
  page: Page,
  stage: string,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
) {
  const record = {
    stage,
    url: page.url(),
    browserErrors: [...runtime.browserErrors],
    serverErrors: [...runtime.serverErrors],
    networkFailures: [...runtime.networkFailures],
  };
  records.push(record);
  expect(record.browserErrors, `${stage} 不应产生浏览器错误`).toEqual([]);
  expect(record.serverErrors, `${stage} 不应产生 HTTP 错误`).toEqual([]);
  expect(record.networkFailures, `${stage} 不应产生网络失败`).toEqual([]);
}

async function captureEvidence(page: Page, testInfo: TestInfo, name: string) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function ensureConfigPackageDraft(page: Page) {
  const publishButton = page.getByRole("button", { name: "发布配置包" });
  if ((await publishButton.count()) > 0) {
    return;
  }

  await page.getByRole("button", { name: "新建配置包草案" }).click();
  const dialog = page.getByRole("dialog", { name: "新建配置包草案" });
  await expect(dialog).toBeVisible();
  const suffix = Date.now().toString(36).toUpperCase();
  await dialog.getByLabel("配置包编码").fill(`PKG.B0E2E.${suffix}`);
  await dialog.getByLabel("配置包版本").fill(`b0-e2e-${suffix}`);
  await dialog.getByLabel("配置包名称").fill("B0 截图链临时配置包");
  await dialog.getByLabel("发布范围说明").fill("用于 B0 第一阶段截图证据链自动化验收");
  await dialog.getByRole("button", { name: "提交创建草案" }).click();
  await expect(dialog).toBeHidden({ timeout: 15_000 });
  await expect(publishButton).toBeEnabled({ timeout: 15_000 });
}

async function attachRuntimeRecords(testInfo: TestInfo, records: RuntimeRecord[]) {
  const recordPath = testInfo.outputPath("b0-screenshot-chain-runtime-records.json");
  await writeFile(`${recordPath}`, `${JSON.stringify(records, null, 2)}\n`, "utf8");
  await testInfo.attach("b0-screenshot-chain-runtime-records", {
    path: recordPath,
    contentType: "application/json",
  });
}

async function expectNoRootOverflow(page: Page, label: string) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth, `${label} 页面根节点不应横向溢出`).toBeLessThanOrEqual(
    dimensions.viewportWidth,
  );
}
