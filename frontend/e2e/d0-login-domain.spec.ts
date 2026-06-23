import { expect, test, type Page } from "@playwright/test";

import {
  apiBase,
  ensureReadySession,
  expectLoginPageReady,
  expectOk,
  roleAccounts,
} from "./support/auth";

test.describe.configure({ mode: "serial" });

test.describe("D0 登录域真实验收", () => {
  for (const role of roleAccounts) {
    test(`${role} 可完成真实登录并获得二级菜单权限画像`, async ({ page }) => {
      await ensureReadySession(page, role);

      const profile = await getSecurityProfile(page);
      expect(profile.roles.map((item: { code: string }) => item.code)).toContain(role);
      expect(profile.menuKeys).toContain("workbench");
      expect(profile.menuKeys).not.toContain("clinical-run");
      expect(profile.menuKeys).not.toContain("pilot-setup");

      await page.goto("/dashboard");
      await expect(page.getByText("工作台").first()).toBeVisible();
      await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible();
    });
  }

  test("临床使用者只看到临床协同菜单，并可从用户菜单退出登录", async ({ page }) => {
    await ensureReadySession(page, "clinical-user");
    await page.goto("/dashboard");

    await expect(page.getByText("临床协同").first()).toBeVisible();
    await expect(page.getByText("患者索引").first()).toBeVisible();
    await expect(page.getByText("交付准备").first()).toHaveCount(0);

    await page.getByRole("button", { name: "当前用户菜单" }).click();
    await page.getByRole("menuitem", { name: /退出登录/ }).click();
    await page.getByRole("button", { name: "确认退出" }).click();

    await expect(page).toHaveURL(/\/login$/);
    await expectLoginPageReady(page);
  });
});

async function getSecurityProfile(page: Page) {
  const response = await page.request.get(`${apiBase}/security/me`, {
    headers: { "X-Trace-Id": `e2e-d0-${Date.now()}` },
  });
  await expectOk(response, "读取当前权限画像");
  return (await response.json()).data;
}
