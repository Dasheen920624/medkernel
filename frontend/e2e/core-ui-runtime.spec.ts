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

test.describe("核心 UI 运行旅程", () => {
  test("登录、用户菜单、规则维护与发布治理页面均可运行且无系统错误", async ({
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
      await captureEvidence(page, testInfo, "core-login-desktop");
      recordCleanRuntime(page, "登录页桌面", runtime, records);

      await ensureReadySession(page, "engine-operator");
      await page.setViewportSize({ width: 1440, height: 960 });
      clearRuntime(runtime);
      await page.goto("/dashboard", { waitUntil: "networkidle" });
      await page.getByRole("button", { name: "当前用户菜单" }).click();
      const userMenu = page.getByRole("menu").filter({ hasText: "修改密码" });
      await expect(userMenu.getByRole("menuitem", { name: "修改密码" })).toBeVisible();
      await expect(userMenu.getByRole("menuitem", { name: "退出登录" })).toBeVisible();
      await captureEvidence(page, testInfo, "core-header-user-menu");
      recordCleanRuntime(page, "登录后 Header 用户菜单", runtime, records);

      clearRuntime(runtime);
      await page.goto("/rule/definitions", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "规则中枢" })).toBeVisible();
      await expectNoRootOverflow(page, "规则中枢桌面");
      await captureEvidence(page, testInfo, "core-rule-definitions-desktop");
      recordCleanRuntime(page, "规则中枢桌面", runtime, records);

      await page.setViewportSize({ width: 390, height: 844 });
      clearRuntime(runtime);
      await page.goto("/rule/definitions", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "规则中枢" })).toBeVisible();
      await expectNoRootOverflow(page, "规则中枢 390px");
      await captureEvidence(page, testInfo, "core-rule-definitions-390px");
      recordCleanRuntime(page, "规则中枢 390px", runtime, records);

      await page.setViewportSize({ width: 1440, height: 960 });
      clearRuntime(runtime);
      await page.goto("/config/releases", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "发布治理" })).toBeVisible();
      await expect(page.getByRole("tab", { name: "平台标准版本" })).toBeVisible();
      await expect(page.getByRole("tab", { name: "机构生效版本" })).toBeVisible();
      await expectNoRootOverflow(page, "发布治理桌面");
      await captureEvidence(page, testInfo, "core-runtime-releases-desktop");
      recordCleanRuntime(page, "发布治理桌面", runtime, records);
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

async function attachRuntimeRecords(testInfo: TestInfo, records: RuntimeRecord[]) {
  const recordPath = testInfo.outputPath("core-ui-runtime-records.json");
  await writeFile(`${recordPath}`, `${JSON.stringify(records, null, 2)}\n`, "utf8");
  await testInfo.attach("core-ui-runtime-records", {
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
