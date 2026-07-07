import { expect, test, type Page } from "@playwright/test";

import { PRODUCT_ROLE_JOURNEYS } from "../src/shared/config/productRoleJourneys";
import { routeMetas } from "../src/shared/config/routes";
import {
  apiBase,
  ensureReadySession,
  expectOk,
  roleAccounts,
  type RoleAccount,
} from "./support/auth";

const expectedMenus: Record<RoleAccount, string[]> = {
  "platform-admin": [
    "workbench",
    "tenant-onboarding",
    "admin-users",
    "identity-bindings",
    "admin-audit",
    "security-baseline",
    "implementation-guide",
    "adapter-hub",
    "system-providers",
    "runtime-diagnostics",
    "domestic-check",
    "notifications",
    "notification-settings",
  ],
  "engine-operator": [
    "workbench",
    "knowledge-governance",
    "runtime-releases",
    "institution-knowledge",
    "diagnosis-knowledge",
    "terminology-mapping",
    "rule-definitions",
    "pathway-templates",
    "provenance",
    "graph-explore",
    "knowledge-production",
    "ai-workflows",
    "clinical-followup",
    "sandbox",
    "qc-dashboard",
    "qc-alerts",
    "insurance-audit",
    "qc-eval-sets",
    "admin-audit",
    "notifications",
    "notification-settings",
  ],
  "clinical-user": [
    "workbench",
    "mpi",
    "patient-pathways",
    "cdss-fatigue",
    "workflow-todos",
    "clinical-followup",
    "sandbox",
    "notifications",
    "notification-settings",
  ],
  auditor: [
    "workbench",
    "provenance",
    "admin-audit",
    "security-baseline",
    "notifications",
    "notification-settings",
  ],
};

const viewports = [
  { name: "desktop-1440", width: 1440, height: 1100 },
  { name: "desktop-1366", width: 1366, height: 768 },
  { name: "tablet", width: 768, height: 1024 },
  { name: "mobile", width: 390, height: 844 },
] as const;

const routeByMenuKey = new Map(
  routeMetas
    .filter((route) => route.menuKey)
    .map((route) => [route.menuKey as string, route]),
);

test.describe.configure({ mode: "serial" });

test.describe("四个客户职责角色任务旅程", () => {
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
        const primaryButtons = appMainContent(page).locator(".ant-btn-primary");
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
        await waitForMainContent(page, `${role} 主动作 · ${viewport.name}`);
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

  test("desktop-1440 下四职责完整菜单入口均可由 canonical 账号真实打开", async ({
    page,
  }, testInfo) => {
    test.setTimeout(900_000);
    await page.setViewportSize({ width: 1440, height: 1100 });
    const roleMenuReachability: Array<{
      role: RoleAccount;
      menuKey: string;
      path: string;
      title: string;
      status: "REACHABLE";
    }> = [];
    const browserErrors = collectBrowserErrors(page);
    const serverErrors = collectServerErrors(page);
    const networkFailures = collectNetworkFailures(page);

    for (const role of roleAccounts) {
      await ensureReadySession(page, role);
      for (const menuKey of expectedMenus[role]) {
        const route = routeByMenuKey.get(menuKey);
        expect(route, `${role} 菜单 ${menuKey} 必须存在真实路由`).toBeDefined();
        browserErrors.length = 0;
        serverErrors.length = 0;
        networkFailures.length = 0;

        await page.goto(route.path, { waitUntil: "networkidle" });
        await expect
          .poll(() => new URL(page.url()).pathname, {
            message: `${role} 菜单 ${menuKey} 应停留在真实目标路由`,
          })
          .toBe(route.path);
        await waitForMainContent(page, `${role} 菜单 ${menuKey}`);
        await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
        await expectNoRootOverflow(page, `${role} 菜单 ${menuKey}`);
        expect(serverErrors, `${role} 菜单 ${menuKey} 不应产生 HTTP 错误`).toEqual([]);
        expect(networkFailures, `${role} 菜单 ${menuKey} 不应产生网络失败`).toEqual([]);
        expect(browserErrors, `${role} 菜单 ${menuKey} 不应产生浏览器错误`).toEqual([]);

        roleMenuReachability.push({
          role,
          menuKey,
          path: route.path,
          title: route.title,
          status: "REACHABLE",
        });
      }
    }

    await testInfo.attach("role-menu-reachability-codes", {
      contentType: "application/json",
      body: Buffer.from(
        JSON.stringify(
          {
            viewport: "desktop-1440",
            expectedMenus,
            roleMenuReachability,
          },
          null,
          2,
        ),
        "utf8",
      ),
    });
  });
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

async function waitForMainContent(page: Page, label: string) {
  await page.waitForLoadState("domcontentloaded");
  await expect(page.locator(".ant-spin-spinning"), `${label} 不应停留在加载中`).toHaveCount(0, {
    timeout: 30_000,
  });
  await expect(appMainContent(page), `${label} 应展示主内容`).toBeVisible({
    timeout: 30_000,
  });
}

function appMainContent(page: Page) {
  return page.locator("main.mk-app-content");
}
