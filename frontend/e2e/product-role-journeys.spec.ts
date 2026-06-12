import { expect, test, type Page } from "@playwright/test";

import { PRODUCT_ROLE_JOURNEYS } from "../src/shared/config/productRoleJourneys";
import {
  apiBase,
  ensureReadySession,
  expectOk,
  roleAccounts,
  type RoleAccount,
} from "./support/auth";

const expectedMenus: Record<RoleAccount, string[]> = {
  "platform-governance-admin": [
    "workbench",
    "tenant-onboarding",
    "admin-users",
    "identity-bindings",
    "implementation-guide",
    "knowledge-governance",
    "config-packages",
    "qc-dashboard",
    "admin-audit",
    "security-baseline",
    "system-providers",
    "notification-settings",
    "provenance",
    "ai-workflows",
    "domestic-check",
  ],
  "platform-knowledge-governor": [
    "workbench",
    "knowledge-governance",
    "config-packages",
    "terminology-mapping",
    "rule-definitions",
    "pathway-templates",
    "admin-audit",
    "provenance",
    "graph-explore",
    "ai-workflows",
  ],
  "organization-admin": [
    "workbench",
    "tenant-onboarding",
    "admin-users",
    "identity-bindings",
    "implementation-guide",
    "knowledge-governance",
    "config-packages",
    "qc-dashboard",
    "admin-audit",
    "security-baseline",
    "system-providers",
    "notification-settings",
    "provenance",
    "domestic-check",
  ],
  "identity-access-admin": [
    "workbench",
    "admin-users",
    "identity-bindings",
    "admin-audit",
    "security-baseline",
  ],
  "knowledge-governor": [
    "workbench",
    "knowledge-governance",
    "config-packages",
    "terminology-mapping",
    "rule-definitions",
    "pathway-templates",
    "admin-audit",
    "provenance",
    "graph-explore",
    "ai-workflows",
  ],
  "clinical-governor": [
    "workbench",
    "knowledge-governance",
    "rule-definitions",
    "pathway-templates",
    "mpi",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "clinical-followup",
    "qc-dashboard",
    "qc-alerts",
    "notifications",
    "provenance",
  ],
  "clinical-decision-user": [
    "workbench",
    "mpi",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "clinical-followup",
    "notifications",
  ],
  "nursing-collaborator": [
    "workbench",
    "mpi",
    "patient-pathways",
    "workflow-todos",
    "clinical-followup",
    "notifications",
  ],
  "medication-safety-user": [
    "workbench",
    "knowledge-governance",
    "rule-definitions",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "notifications",
    "provenance",
  ],
  "diagnostic-service-user": [
    "workbench",
    "terminology-mapping",
    "mpi",
    "workflow-todos",
    "notifications",
  ],
  "quality-governor": [
    "workbench",
    "knowledge-governance",
    "qc-dashboard",
    "qc-alerts",
    "insurance-audit",
    "qc-eval-sets",
    "admin-audit",
    "provenance",
  ],
  "compliance-auditor": ["workbench", "admin-audit", "provenance"],
  "integration-operator": [
    "workbench",
    "identity-bindings",
    "adapter-hub",
    "terminology-mapping",
    "admin-audit",
    "security-baseline",
    "system-providers",
    "notification-settings",
    "graph-explore",
    "ai-workflows",
    "domestic-check",
    "dev-console",
  ],
  "implementation-operator": [
    "workbench",
    "tenant-onboarding",
    "admin-users",
    "identity-bindings",
    "implementation-guide",
    "adapter-hub",
    "knowledge-governance",
    "config-packages",
    "terminology-mapping",
    "admin-audit",
    "security-baseline",
    "system-providers",
    "notification-settings",
    "provenance",
    "graph-explore",
    "ai-workflows",
    "domestic-check",
    "dev-console",
  ],
};

const viewports = [
  { name: "desktop-1440", width: 1440, height: 1100 },
  { name: "desktop-1366", width: 1366, height: 768 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "mobile", width: 390, height: 844 },
] as const;

test.describe.configure({ mode: "serial" });

test.describe("14 个客户职责角色任务旅程", () => {
  test("后端权限画像与产品菜单快照完全一致", async ({ page }) => {
    test.setTimeout(300_000);
    for (const role of roleAccounts) {
      await ensureReadySession(page, role);
      const profile = await loadProfile(page, role);
      expect(profile.menuKeys, `${role} 菜单快照`).toEqual(expectedMenus[role]);
    }
  });

  for (const viewport of viewports) {
    test(`${viewport.name} 下全部角色工作台可完成主任务起步`, async ({ page }, testInfo) => {
      test.setTimeout(600_000);
      await page.setViewportSize({ width: viewport.width, height: viewport.height });

      for (const role of roleAccounts) {
        const journey = PRODUCT_ROLE_JOURNEYS.find((item) => item.roleCode === role);
        expect(journey, `${role} 必须有产品旅程`).toBeDefined();
        await ensureReadySession(page, role);
        await page.goto("/dashboard", { waitUntil: "networkidle" });

        await expect(page.getByRole("heading", { name: journey?.title })).toBeVisible();
        const primaryButtons = page.locator("main .ant-btn-primary");
        await expect(primaryButtons, `${role} 只能有一个主动作`).toHaveCount(1);
        await expect(primaryButtons).toContainText(journey?.primaryAction.label ?? "");
        await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
        await expectNoRootOverflow(page, `${role} · ${viewport.name}`);

        await primaryButtons.click();
        await expect
          .poll(() => new URL(page.url()).pathname, {
            message: `${role} 主动作应进入真实目标页`,
          })
          .toBe(journey?.primaryAction.path);
        await page.waitForLoadState("networkidle");
        await expect(page.locator("main")).toBeVisible();
        await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
        await expectNoRootOverflow(page, `${role} 主动作 · ${viewport.name}`);
      }

      const screenshotPath = testInfo.outputPath(`role-primary-action-${viewport.name}.png`);
      await page.screenshot({ path: screenshotPath, fullPage: true });
      await testInfo.attach(`role-primary-action-${viewport.name}`, {
        path: screenshotPath,
        contentType: "image/png",
      });
    });
  }
});

async function loadProfile(page: Page, role: RoleAccount) {
  const response = await page.request.get(`${apiBase}/security/me`, {
    headers: { "X-Trace-Id": `e2e-product-role-${role}-${Date.now()}` },
  });
  await expectOk(response, `读取 ${role} 权限画像`);
  return (await response.json()).data as { menuKeys: string[] };
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
