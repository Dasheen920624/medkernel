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
    "knowledge-governance",
    "diagnosis-knowledge",
    "config-packages",
    "provenance",
    "ai-workflows",
    "qc-dashboard",
    "admin-audit",
    "security-baseline",
    "implementation-guide",
    "system-providers",
    "domestic-check",
    "notification-settings",
  ],
  "platform-knowledge-governor": [
    "workbench",
    "knowledge-governance",
    "diagnosis-knowledge",
    "config-packages",
    "terminology-mapping",
    "rule-definitions",
    "pathway-templates",
    "provenance",
    "graph-explore",
    "ai-workflows",
    "admin-audit",
  ],
  "organization-admin": [
    "workbench",
    "tenant-onboarding",
    "admin-users",
    "identity-bindings",
    "knowledge-governance",
    "config-packages",
    "rule-definitions",
    "pathway-templates",
    "provenance",
    "qc-dashboard",
    "admin-audit",
    "security-baseline",
    "implementation-guide",
    "system-providers",
    "domestic-check",
    "notification-settings",
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
    "diagnosis-knowledge",
    "config-packages",
    "terminology-mapping",
    "rule-definitions",
    "pathway-templates",
    "provenance",
    "graph-explore",
    "ai-workflows",
    "admin-audit",
  ],
  "clinical-governor": [
    "workbench",
    "knowledge-governance",
    "diagnosis-knowledge",
    "rule-definitions",
    "pathway-templates",
    "provenance",
    "mpi",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "clinical-followup",
    "sandbox",
    "qc-dashboard",
    "qc-alerts",
    "notifications",
  ],
  "clinical-decision-user": [
    "workbench",
    "mpi",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "clinical-followup",
    "sandbox",
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
    "diagnosis-knowledge",
    "rule-definitions",
    "provenance",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "notifications",
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
    "rule-definitions",
    "provenance",
    "qc-dashboard",
    "qc-alerts",
    "insurance-audit",
    "qc-eval-sets",
    "admin-audit",
  ],
  "compliance-auditor": ["workbench", "provenance", "admin-audit"],
  "integration-operator": [
    "workbench",
    "identity-bindings",
    "terminology-mapping",
    "graph-explore",
    "ai-workflows",
    "sandbox",
    "admin-audit",
    "security-baseline",
    "adapter-hub",
    "system-providers",
    "domestic-check",
    "dev-console",
    "notification-settings",
  ],
  "implementation-operator": [
    "workbench",
    "tenant-onboarding",
    "admin-users",
    "identity-bindings",
    "knowledge-governance",
    "config-packages",
    "terminology-mapping",
    "provenance",
    "graph-explore",
    "ai-workflows",
    "sandbox",
    "admin-audit",
    "security-baseline",
    "implementation-guide",
    "adapter-hub",
    "system-providers",
    "domestic-check",
    "dev-console",
    "notification-settings",
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
      const browserErrors = collectBrowserErrors(page);
      const serverErrors = collectServerErrors(page);
      const networkFailures = collectNetworkFailures(page);

      for (const role of roleAccounts) {
        const journey = PRODUCT_ROLE_JOURNEYS.find((item) => item.roleCode === role);
        expect(journey, `${role} 必须有产品旅程`).toBeDefined();
        await ensureReadySession(page, role);
        browserErrors.length = 0;
        serverErrors.length = 0;
        networkFailures.length = 0;
        await page.goto("/dashboard", { waitUntil: "networkidle" });

        await expect(page.getByRole("heading", { name: journey?.title })).toBeVisible();
        const primaryButtons = page.locator("main .ant-btn-primary");
        await expect(primaryButtons, `${role} 只能有一个主动作`).toHaveCount(1);
        await expect(primaryButtons).toContainText(journey?.primaryAction.label ?? "");
        await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
        await expectNoRootOverflow(page, `${role} · ${viewport.name}`);
        expect(serverErrors, `${role} · ${viewport.name} 工作台不应产生 HTTP 错误`).toEqual([]);
        expect(networkFailures, `${role} · ${viewport.name} 工作台不应产生网络失败`).toEqual([]);
        expect(browserErrors, `${role} · ${viewport.name} 工作台不应产生浏览器错误`).toEqual([]);
        browserErrors.length = 0;
        serverErrors.length = 0;
        networkFailures.length = 0;

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
        expect(serverErrors, `${role} 主动作 · ${viewport.name} 不应产生 HTTP 错误`).toEqual([]);
        expect(networkFailures, `${role} 主动作 · ${viewport.name} 不应产生网络失败`).toEqual([]);
        expect(browserErrors, `${role} 主动作 · ${viewport.name} 不应产生浏览器错误`).toEqual([]);
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
    if (failure?.errorText === "net::ERR_ABORTED") {
      return;
    }
    if (!url.startsWith("data:")) {
      errors.push(`${request.method()} ${url} ${failure?.errorText ?? "requestfailed"}`);
    }
  });
  return errors;
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
