import { expect, test, type Page } from "@playwright/test";

import { apiBase } from "./support/auth";

test.describe.configure({ mode: "serial" });

test.describe("D0 首次部署入口关闭真实验收", () => {
  test("初始化完成后登录页不再暴露首次部署入口", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);

    const statusResponse = await page.request.get(`${apiBase}/bootstrap/status`);
    expect(statusResponse.ok()).toBe(true);
    expect((await statusResponse.json()).data).toEqual({ initialized: true });

    await page.goto("/login");
    await expect(page.getByRole("heading", { name: "登录工作台" })).toBeVisible();
    await expect(page.getByText("首次部署接管", { exact: true })).toHaveCount(0);
    await expectNoRootOverflow(page);

    const screenshotPath = testInfo.outputPath("bootstrap-closed-login-desktop.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("bootstrap-closed-login-desktop", {
      path: screenshotPath,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
  });

  test("直接访问首次部署页只展示已完成状态", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    await page.setViewportSize({ width: 390, height: 844 });

    await page.goto("/bootstrap");
    await expect(page.getByText("系统已完成首次部署", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "返回登录" })).toBeVisible();
    await expect(page.getByLabel("一次性部署令牌")).toHaveCount(0);
    await expectNoRootOverflow(page);

    const screenshotPath = testInfo.outputPath("bootstrap-closed-mobile.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("bootstrap-closed-mobile", {
      path: screenshotPath,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
  });
});

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

async function expectNoRootOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth);
}
