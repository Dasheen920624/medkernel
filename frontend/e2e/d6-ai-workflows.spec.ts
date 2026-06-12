import { expect, test, type Page } from "@playwright/test";

import { ensureReadySession, loginFromPlatformPage } from "./support/auth";

test.describe.configure({ mode: "serial" });

test.describe("D6 AI 工作流真实验收", () => {
  test("实施运维员从登录页查看真实能力状态且没有执行或管理入口", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    await ensureReadySession(page, "implementation-operator");
    await loginFromPlatformPage(page, "implementation-operator");

    await page.goto("/advanced/ai-workflows");
    await expect(page.getByRole("heading", { name: "AI 工作流" })).toBeVisible();
    await expect(page.getByText("临床知识关联发现")).toBeVisible();
    await expect(page.getByText("智能随访", { exact: true })).toBeVisible();
    await expect(page.locator("tbody tr.ant-table-row")).toHaveCount(8);
    await expect(page.getByText("B0 基线").last()).toBeVisible();
    await expect(page.getByText("未配置专属策略，使用系统 B0 基线").first()).toBeVisible();
    await expect(
      page.getByRole("button", { name: /提交|运行|重试|配置|编辑|新增|保存/ }),
    ).toHaveCount(0);

    await page.getByRole("button", { name: "展开行" }).first().click();
    await expect(page.getByText("路由策略", { exact: true })).toBeVisible();
    await expect(page.getByText("基础规则能力", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("BASELINE", { exact: true })).toHaveCount(0);
    await expect(page.getByText("使用系统默认", { exact: true })).toBeVisible();
    await expectNoRootOverflow(page);
    await page.evaluate(() => window.scrollTo(0, 0));

    const screenshotPath = testInfo.outputPath("ai-workflows-desktop.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("ai-workflows-desktop", {
      path: screenshotPath,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
  });

  test("实施运维员在移动端可读且宽表只在页面内部滚动", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await ensureReadySession(page, "implementation-operator");
    await page.goto("/advanced/ai-workflows");

    await expect(page.getByRole("heading", { name: "AI 工作流" })).toBeVisible();
    await expect(page.getByText("临床知识关联发现")).toBeVisible();
    await expectNoRootOverflow(page);
    await expectTableIsInternallyScrollable(page);
    await page.evaluate(() => window.scrollTo(0, 0));

    const screenshotPath = testInfo.outputPath("ai-workflows-mobile.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("ai-workflows-mobile", {
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

async function expectTableIsInternallyScrollable(page: Page) {
  const dimensions = await page.locator(".ant-table-content").evaluate((table) => ({
    clientWidth: table.clientWidth,
    scrollWidth: table.scrollWidth,
  }));
  expect(dimensions.scrollWidth).toBeGreaterThan(dimensions.clientWidth);
}
