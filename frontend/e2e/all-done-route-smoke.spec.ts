import { expect, test, type Page } from "@playwright/test";

import {
  canAccessRoute,
  routeMetas,
  type RoutePermissionProfile,
} from "../src/shared/config/routes";
import {
  apiBase,
  ensureReadySession,
  expectOk,
  roleAccounts,
  type RoleAccount,
} from "./support/auth";

test.describe.configure({ mode: "serial" });

test.describe("全部 done 功能真实路由验收", () => {
  test("每个真实角色打开全部授权页面且没有系统级错误", async ({ page }) => {
    test.setTimeout(1_200_000);
    const profiles = await loadRoleProfiles(page);
    const browserErrors = collectBrowserErrors(page);
    const serverErrors = collectServerErrors(page);
    const protectedRoutes = routeMetas.filter((route) => route.requireAuth && route.path !== "/");
    const uncoveredRoutes = protectedRoutes.filter(
      (route) => ![...profiles.values()].some((profile) => canAccessRoute(route, profile)),
    );

    expect(
      uncoveredRoutes.map((route) => route.path),
      "每个受保护页面都必须至少有一个真实角色可访问",
    ).toEqual([]);

    for (const role of roleAccounts) {
      const profile = profiles.get(role);
      expect(profile, `${role} 应具有真实权限画像`).toBeDefined();
      const routes = protectedRoutes.filter((route) => canAccessRoute(route, profile));
      await ensureReadySession(page, role);
      for (const route of routes) {
        browserErrors.length = 0;
        serverErrors.length = 0;

        await page.goto(route.path, { waitUntil: "domcontentloaded" });
        await page.waitForLoadState("networkidle");

        await expect(
          page.locator("main").getByRole("heading").first(),
          `${role} 打开 ${route.path} 后应显示页面主标题`,
        ).toBeVisible();
        await expect(
          page.getByText("当前权限不足", { exact: true }),
          `${role} 打开 ${route.path} 不应进入无权限态`,
        ).toHaveCount(0);
        await expectNoRootOverflow(page, `${role} 打开 ${route.path}`);
        expect(serverErrors, `${role} 打开 ${route.path} 不应产生 HTTP 错误`).toEqual([]);
        expect(browserErrors, `${role} 打开 ${route.path} 不应产生浏览器错误`).toEqual([]);
      }
    }
  });
});

async function loadRoleProfiles(page: Page) {
  const profiles = new Map<RoleAccount, RoutePermissionProfile>();
  for (const role of roleAccounts) {
    await ensureReadySession(page, role);
    const response = await page.request.get(`${apiBase}/security/me`, {
      headers: { "X-Trace-Id": `e2e-route-profile-${role}-${Date.now()}` },
    });
    await expectOk(response, `读取 ${role} 权限画像`);
    profiles.set(role, (await response.json()).data as RoutePermissionProfile);
  }
  return profiles;
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

async function expectNoRootOverflow(page: Page, route: string) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth, `${route} 页面根节点不应横向溢出`).toBeLessThanOrEqual(
    dimensions.viewportWidth,
  );
}
