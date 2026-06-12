import { expect, test, type Locator, type Page } from "@playwright/test";

import {
  apiBase,
  ensureReadySession,
  expectOk,
  loginFromPlatformPage,
  postApi,
} from "./support/auth";

test.describe.configure({ mode: "serial" });

test.describe("D6 受限平台包租户授权真实验收", () => {
  test("平台治理管理员从登录页完成受限包创建、授权和撤销", async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    const browserErrors = collectBrowserErrors(page);
    const targetTenantId = "e2e-entitlement";
    const targetTenantName = "授权验收医院";
    const suffix = Date.now();
    const packageCode = `E2E.ENTITLEMENT.${suffix}`;

    await ensureReadySession(page, "platform-governance-admin");
    await ensureTargetTenant(page, targetTenantId, targetTenantName);
    await loginFromPlatformPage(page, "platform-governance-admin");

    await page.goto("/config/packages");
    await expect(page.getByRole("heading", { name: "配置包与发布" })).toBeVisible();
    await page.getByRole("button", { name: "新建配置包草案" }).click();

    const createDialog = page.getByRole("dialog", { name: "新建配置包草案" });
    await createDialog.getByLabel("配置包编码").fill(packageCode);
    await createDialog.getByLabel("配置包版本").fill("2026.06");
    await createDialog.getByLabel("配置包名称").fill("受限包授权真实验收");
    await createDialog.getByLabel("发布范围说明").fill("验证平台受限包按客户租户授权");
    await selectField(createDialog, "访问策略");
    await chooseVisibleOption(page, "按服务空间授权");
    await createDialog.getByRole("button", { name: "提交创建草案" }).click();

    const packageRow = page.getByRole("row").filter({ hasText: packageCode });
    await expect(packageRow).toBeVisible();
    await expect(packageRow.getByText("按服务空间授权", { exact: true })).toBeVisible();
    await packageRow.getByRole("button", { name: "授权管理" }).click();

    const entitlementDialog = page.getByRole("dialog", {
      name: `服务空间授权 · ${packageCode}`,
    });
    await selectField(entitlementDialog, "目标服务空间");
    await chooseVisibleOption(page, `${targetTenantName} · ${targetTenantId}`);
    await entitlementDialog.getByLabel("授权到期时间").fill("2027-12-31T23:59");
    await entitlementDialog.getByLabel("授权原因").fill("真实验收授权审批通过");
    await entitlementDialog.getByRole("button", { name: "开通或续期授权" }).click();

    const entitlementRow = entitlementDialog.getByRole("row").filter({ hasText: targetTenantId });
    await expect(entitlementRow.getByText(targetTenantName, { exact: true })).toBeVisible();
    await expect(entitlementRow.getByText("有效", { exact: true })).toBeVisible();
    await entitlementRow.getByRole("button", { name: "撤销" }).click();
    const revokeDialog = page.getByRole("dialog", { name: "撤销服务空间授权" });
    await revokeDialog.getByLabel("撤销原因").fill("真实验收授权终止审批通过");
    await revokeDialog.getByRole("button", { name: "确认撤销授权" }).click();
    await expect(entitlementRow.getByText("已撤销", { exact: true })).toBeVisible();

    await expectNoRootOverflow(page);
    const desktopScreenshot = testInfo.outputPath("package-entitlement-desktop.png");
    await page.screenshot({ path: desktopScreenshot, fullPage: true });
    await testInfo.attach("package-entitlement-desktop", {
      path: desktopScreenshot,
      contentType: "image/png",
    });

    await page.setViewportSize({ width: 390, height: 844 });
    await expectNoRootOverflow(page);
    await expect(entitlementDialog).toBeVisible();
    await expect(entitlementDialog.getByLabel("目标服务空间")).toBeVisible();
    await expectNoRootOverflow(page);

    const mobileScreenshot = testInfo.outputPath("package-entitlement-mobile.png");
    await page.screenshot({ path: mobileScreenshot, fullPage: true });
    await testInfo.attach("package-entitlement-mobile", {
      path: mobileScreenshot,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
  });
});

async function ensureTargetTenant(page: Page, tenantId: string, tenantName: string) {
  const response = await page.request.get(`${apiBase}/admin/tenants`);
  await expectOk(response, "读取客户租户目录");
  const tenants = (await response.json()).data as Array<{ tenantId: string }>;
  if (tenants.some((tenant) => tenant.tenantId === tenantId)) return;

  const provision = await postApi(page, "/admin/tenants", {
    tenantId,
    tenantName,
    adminUsername: "entitlement-admin",
  });
  await expectOk(provision, "开通授权验收客户租户");
}

async function selectField(scope: Locator, label: string) {
  await scope
    .locator(".ant-form-item")
    .filter({ hasText: label })
    .locator(".ant-select-selector")
    .click();
}

async function chooseVisibleOption(page: Page, label: string) {
  const option = page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option")
    .filter({ hasText: label });
  await expect(option).toBeVisible();
  await option.click();
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

async function expectNoRootOverflow(page: Page) {
  await expect
    .poll(
      () =>
        page.evaluate(() => ({
          viewportWidth: document.documentElement.clientWidth,
          documentWidth: document.documentElement.scrollWidth,
        })),
      { message: "等待响应式布局完成后根节点不再横向溢出" },
    )
    .toEqual(
      expect.objectContaining({
        viewportWidth: await page.evaluate(() => document.documentElement.clientWidth),
        documentWidth: await page.evaluate(() => document.documentElement.clientWidth),
      }),
    );

  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
    offenders: Array.from(document.querySelectorAll<HTMLElement>("body *"))
      .map((element) => {
        const rect = element.getBoundingClientRect();
        return {
          tag: element.tagName.toLowerCase(),
          className: element.className,
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
          scrollWidth: element.scrollWidth,
          clientWidth: element.clientWidth,
        };
      })
      .filter(
        (element) =>
          element.right > document.documentElement.clientWidth + 1 ||
          element.left < -1 ||
          element.scrollWidth > element.clientWidth + 1,
      )
      .slice(0, 20),
  }));
  expect(
    dimensions.documentWidth,
    `根节点横向溢出元素: ${JSON.stringify(dimensions.offenders)}`,
  ).toBeLessThanOrEqual(dimensions.viewportWidth);
}
