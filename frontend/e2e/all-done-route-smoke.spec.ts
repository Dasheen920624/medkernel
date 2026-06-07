import { expect, test, type Page } from "@playwright/test";

import {
  canAccessRoute,
  routeMetas,
  type RouteMeta,
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
  test("每个受保护页面至少由一个真实角色打开且没有系统级错误", async ({ page }) => {
    test.setTimeout(600_000);
    const profiles = await loadRoleProfiles(page);
    const assignments = assignRoutes(profiles);
    const browserErrors = collectBrowserErrors(page);
    const serverErrors = collectServerErrors(page);

    for (const [role, routes] of assignments) {
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
        await expectNoRootOverflow(page, route.path);
        expect(serverErrors, `${route.path} 不应产生 HTTP 错误`).toEqual([]);
        expect(browserErrors, `${route.path} 不应产生浏览器错误`).toEqual([]);
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

function assignRoutes(profiles: Map<RoleAccount, RoutePermissionProfile>) {
  const uncovered = new Map(
    routeMetas
      .filter((route) => route.requireAuth && route.path !== "/")
      .map((route) => [route.path, route]),
  );
  const assignments = new Map<RoleAccount, RouteMeta[]>();

  while (uncovered.size > 0) {
    const best = [...profiles.entries()]
      .map(([role, profile]) => ({
        role,
        routes: [...uncovered.values()].filter((route) => canAccessRoute(route, profile)),
      }))
      .sort((left, right) => right.routes.length - left.routes.length)[0];

    expect(
      best?.routes.length ?? 0,
      `以下路由没有任何真实角色可访问: ${[...uncovered.keys()].join(", ")}`,
    ).toBeGreaterThan(0);

    assignments.set(best.role, best.routes);
    best.routes.forEach((route) => uncovered.delete(route.path));
  }

  return assignments;
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
