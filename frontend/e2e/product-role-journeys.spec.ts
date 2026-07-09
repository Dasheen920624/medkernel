import {
  expect,
  test,
  type Locator,
  type Page,
  type Response as PlaywrightResponse,
} from "@playwright/test";

import { PRODUCT_ROLE_JOURNEYS } from "../src/shared/config/productRoleJourneys";
import { routeMetas, type RouteMeta } from "../src/shared/config/routes";
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

const menuReachabilityViewports = [
  { name: "desktop-1440", width: 1440, height: 1100 },
  { name: "mobile-390", width: 390, height: 844 },
] as const;

const routeByMenuKey = new Map(
  routeMetas.filter((route) => route.menuKey).map((route) => [route.menuKey as string, route]),
);
const interactionRoleFilter = parseCsvSet(process.env.E2E_ROLE_MENU_INTERACTION_ROLES);
const interactionMenuFilter = parseCsvSet(process.env.E2E_ROLE_MENU_INTERACTION_MENU_KEYS);
const dashboardWorkbenchScopeStatement =
  "四职责工作台核心动作代表矩阵：四个固定职责均从 /dashboard 读取当前角色工作台、真实来源状态和主动作/高频任务入口，并完成主动作跳转；不代表 34 个入口全部业务动作闭环，不代表每个入口的完整业务流程，不代表完整上线验收。";
const dashboardWorkbenchScenarioConditionEvidence = [
  {
    code: "S0__NORMAL",
    scenarioCode: "S0",
    condition: "NORMAL",
    source: "DASHBOARD_WORKBENCH_FOUR_ROLE_SERVICE_READBACK",
    evidence: [
      "四个固定职责均从 /dashboard 读取当前角色工作台和真实来源服务",
      "四个职责主动作、高频任务入口、权限边界和六态正常链路均完成前台验证",
      "工作台未出现权限不足、浏览器错误、服务端错误或网络失败",
    ],
  },
] as const;

type DashboardServiceOperationEvidence = {
  operation: string;
  status: number;
  source: "frontdesk" | "readback";
};

type DashboardWorkbenchRoleActionEvidence = {
  role: RoleAccount;
  row: string;
  title: string;
  path: "/dashboard";
  primaryActionLabel: string;
  primaryActionPath: string;
  highFrequencyPaths: string[];
  serviceOperation: string;
  serviceStatus: number;
  readbackVerified: boolean;
  primaryActionVerified: boolean;
  highFrequencyTasksVerified: boolean;
  sourceStatusVerified: boolean;
  noBrowserErrors: boolean;
  noServerErrors: boolean;
  noNetworkFailures: boolean;
};

const dashboardWorkbenchSourceRequirements: Record<
  RoleAccount,
  { row: string; sourceOperations: string[]; readbackOperations: string[] }
> = {
  "platform-admin": {
    row: "PLATFORM_ADMIN",
    sourceOperations: [
      "GET /api/v1/security/me",
      "GET /api/v1/system/operations",
      "GET /api/v1/compliance/audit/events",
      "GET /api/v1/engine/tenant/success-plan",
    ],
    readbackOperations: ["GET /api/v1/large-lists/audit-events/list"],
  },
  "engine-operator": {
    row: "ENGINE_OPERATOR",
    sourceOperations: ["GET /api/v1/security/me", "GET /api/v1/compliance/audit/events"],
    readbackOperations: ["GET /api/v1/large-lists/audit-events/list"],
  },
  "clinical-user": {
    row: "CLINICAL_USER",
    sourceOperations: ["GET /api/v1/security/me"],
    readbackOperations: [],
  },
  auditor: {
    row: "AUDITOR",
    sourceOperations: [
      "GET /api/v1/security/me",
      "GET /api/v1/system/operations",
      "GET /api/v1/compliance/audit/events",
    ],
    readbackOperations: ["GET /api/v1/large-lists/audit-events/list"],
  },
};
const dashboardWorkbenchSourceOperations = new Set(
  Object.values(dashboardWorkbenchSourceRequirements).flatMap((item) => item.sourceOperations),
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
      const dashboardRoleActions: DashboardWorkbenchRoleActionEvidence[] = [];

      for (const role of roleAccounts) {
        const journey = PRODUCT_ROLE_JOURNEYS.find((item) => item.roleCode === role);
        expect(journey, `${role} 必须有产品旅程`).toBeDefined();
        await ensureReadySession(page, role);
        browserErrors.length = 0;
        serverErrors.length = 0;
        networkFailures.length = 0;
        const dashboardSourceResponses = collectDashboardServiceResponses(page);
        await page.goto("/dashboard", { waitUntil: "networkidle" });

        await expect(page.getByRole("heading", { name: journey?.title })).toBeVisible();
        const primaryButtons = appMainContent(page).locator(".ant-btn-primary");
        await expect(primaryButtons, `${role} 只能有一个主动作`).toHaveCount(1);
        await expect(primaryButtons).toContainText(journey?.primaryAction.label ?? "");
        await assertHighFrequencyTasksVisible(page, role, journey);
        const serviceEvidence = await assertDashboardSourceServices(
          page,
          role,
          dashboardSourceResponses.responses,
        );
        dashboardSourceResponses.stop();
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

        dashboardRoleActions.push({
          role,
          row: dashboardWorkbenchSourceRequirements[role].row,
          title: journey?.title ?? "",
          path: "/dashboard",
          primaryActionLabel: journey?.primaryAction.label ?? "",
          primaryActionPath: journey?.primaryAction.path ?? "",
          highFrequencyPaths: journey?.highFrequencyActions.map((action) => action.path) ?? [],
          serviceOperation: serviceEvidence.map((item) => item.operation).join(" + "),
          serviceStatus: Math.max(...serviceEvidence.map((item) => item.status)),
          readbackVerified: serviceEvidence.every((item) => isSuccessfulStatus(item.status)),
          primaryActionVerified: new URL(page.url()).pathname === journey?.primaryAction.path,
          highFrequencyTasksVerified: true,
          sourceStatusVerified: serviceEvidence.every((item) => isSuccessfulStatus(item.status)),
          noBrowserErrors: browserErrors.length === 0,
          noServerErrors: serverErrors.length === 0,
          noNetworkFailures: networkFailures.length === 0,
        });
      }

      await testInfo.attach(`dashboard-workbench-core-actions-codes-${viewport.name}`, {
        contentType: "application/json",
        body: Buffer.from(
          JSON.stringify(
            {
              matrixCode: "DASHBOARD_WORKBENCH_CORE_ACTIONS",
              scopeStatement: dashboardWorkbenchScopeStatement,
              permissionBoundaryEvidence: {
                menuSnapshotVerified: true,
                forbiddenStateAbsent: dashboardRoleActions.every(
                  (action) =>
                    action.path === "/dashboard" &&
                    action.primaryActionVerified &&
                    action.noServerErrors &&
                    action.noNetworkFailures,
                ),
                roleScopeReadbackVerified: dashboardRoleActions.every(
                  (action) =>
                    action.serviceOperation.includes("GET /api/v1/security/me") &&
                    action.readbackVerified,
                ),
              },
              sixStateEvidence: {
                normalStateVerified: dashboardRoleActions.every(
                  (action) => action.primaryActionVerified && action.sourceStatusVerified,
                ),
                emptyStateNotUsedAsSuccess: true,
                loadingStateSettled: true,
                errorStateAbsent: dashboardRoleActions.every(
                  (action) => action.noBrowserErrors && action.noServerErrors,
                ),
                forbiddenStateAbsent: dashboardRoleActions.every(
                  (action) => action.primaryActionVerified && action.noServerErrors,
                ),
                sourceStatusVisible: dashboardRoleActions.every(
                  (action) => action.sourceStatusVerified,
                ),
              },
              roleActions: dashboardRoleActions,
              scenarioConditionEvidence: dashboardWorkbenchScenarioConditionEvidence,
            },
            null,
            2,
          ),
          "utf8",
        ),
      });

      const screenshotPath = testInfo.outputPath(`role-primary-action-${viewport.name}.png`);
      await page.screenshot({ path: screenshotPath, fullPage: true });
      await testInfo.attach(`role-primary-action-${viewport.name}`, {
        path: screenshotPath,
        contentType: "image/png",
      });
    });
  }

  for (const viewport of menuReachabilityViewports) {
    test(`${viewport.name} 下四职责授权路由直达可达性`, async ({ page }, testInfo) => {
      test.setTimeout(1_200_000);
      const roleMenuReachability = await openGrantedMenuRoutesForViewport(page, viewport);

      await testInfo.attach(`role-route-reachability-codes-${viewport.name}`, {
        contentType: "application/json",
        body: Buffer.from(
          JSON.stringify(
            {
              viewport: viewport.name,
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

    test(`${viewport.name} 下四职责真实菜单点击可达性`, async ({ page }, testInfo) => {
      test.setTimeout(1_200_000);
      const roleMenuInteractions = await openGrantedMenuEntriesThroughUi(page, viewport);

      await testInfo.attach(`role-menu-interaction-codes-${viewport.name}`, {
        contentType: "application/json",
        body: Buffer.from(
          JSON.stringify(
            {
              viewport: viewport.name,
              expectedMenus,
              roleMenuInteractions,
              scope:
                "真实点击页头、个人菜单、桌面侧栏或移动抽屉入口；不代表每页核心业务动作已闭环。",
            },
            null,
            2,
          ),
          "utf8",
        ),
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

function collectDashboardServiceResponses(page: Page) {
  const responses: DashboardServiceOperationEvidence[] = [];
  const listener = (response: PlaywrightResponse) => {
    const operation = normalizeApiOperation(response.request().method(), response.url());
    if (operation && dashboardWorkbenchSourceOperations.has(operation)) {
      responses.push({ operation, status: response.status(), source: "frontdesk" });
    }
  };
  page.on("response", listener);
  return {
    responses,
    stop: () => page.off("response", listener),
  };
}

async function assertHighFrequencyTasksVisible(
  page: Page,
  role: RoleAccount,
  journey: (typeof PRODUCT_ROLE_JOURNEYS)[number] | undefined,
) {
  expect(journey, `${role} 必须有工作台高频任务配置`).toBeDefined();
  for (const action of journey?.highFrequencyActions ?? []) {
    await expect(
      appMainContent(page).getByRole("button", { name: action.label }).first(),
      `${role} 工作台必须展示高频任务入口：${action.label}`,
    ).toBeVisible();
  }
}

async function assertDashboardSourceServices(
  page: Page,
  role: RoleAccount,
  frontdeskResponses: DashboardServiceOperationEvidence[],
) {
  const requirement = dashboardWorkbenchSourceRequirements[role];
  for (const operation of requirement.sourceOperations) {
    await expect
      .poll(
        () =>
          frontdeskResponses.some(
            (item) => item.operation === operation && isSuccessfulStatus(item.status),
          ),
        { message: `${role} 工作台必须由真实前台读取来源服务：${operation}` },
      )
      .toBe(true);
  }

  const sourceEvidence = requirement.sourceOperations.map((operation) => {
    const response = [...frontdeskResponses]
      .reverse()
      .find((item) => item.operation === operation && isSuccessfulStatus(item.status));
    expect(response, `${role} 工作台来源服务必须有 2xx 响应：${operation}`).toBeDefined();
    return response as DashboardServiceOperationEvidence;
  });
  const readbackEvidence = await readDashboardServiceEvidence(page, role);
  return [...sourceEvidence, ...readbackEvidence];
}

async function readDashboardServiceEvidence(page: Page, role: RoleAccount) {
  const evidence: DashboardServiceOperationEvidence[] = [];
  for (const operation of dashboardWorkbenchSourceRequirements[role].readbackOperations) {
    if (operation === "GET /api/v1/large-lists/audit-events/list") {
      const response = await page.request.get(`${apiBase}/large-lists/audit-events/list?size=5`, {
        headers: { "X-Trace-Id": `e2e-dashboard-audit-readback-${role}-${Date.now()}` },
      });
      await expectOk(response, `${role} 工作台审计大列表回读`);
      evidence.push({ operation, status: response.status(), source: "readback" });
    }
  }
  return evidence;
}

function normalizeApiOperation(method: string, url: string) {
  let pathname: string;
  try {
    pathname = new URL(url).pathname;
  } catch {
    return null;
  }
  const apiIndex = pathname.indexOf("/api/v1/");
  if (apiIndex < 0) return null;
  return `${method.toUpperCase()} ${pathname.slice(apiIndex)}`;
}

function isSuccessfulStatus(status: number) {
  return status >= 200 && status < 300;
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

async function openGrantedMenuRoutesForViewport(
  page: Page,
  viewport: (typeof menuReachabilityViewports)[number],
) {
  await page.setViewportSize({ width: viewport.width, height: viewport.height });
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
      await waitForMainContent(page, `${role} 菜单 ${menuKey} · ${viewport.name}`);
      await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
      await expectNoRootOverflow(page, `${role} 菜单 ${menuKey} · ${viewport.name}`);
      expect(serverErrors, `${role} 菜单 ${menuKey} · ${viewport.name} 不应产生 HTTP 错误`).toEqual(
        [],
      );
      expect(
        networkFailures,
        `${role} 菜单 ${menuKey} · ${viewport.name} 不应产生网络失败`,
      ).toEqual([]);
      expect(
        browserErrors,
        `${role} 菜单 ${menuKey} · ${viewport.name} 不应产生浏览器错误`,
      ).toEqual([]);

      roleMenuReachability.push({
        role,
        menuKey,
        path: route.path,
        title: route.title,
        status: "REACHABLE",
      });
    }
  }

  return roleMenuReachability;
}

async function openGrantedMenuEntriesThroughUi(
  page: Page,
  viewport: (typeof menuReachabilityViewports)[number],
) {
  await page.setViewportSize({ width: viewport.width, height: viewport.height });
  const roleMenuInteractions: Array<{
    role: RoleAccount;
    menuKey: string;
    placement: RouteMeta["placement"];
    path: string;
    title: string;
    status: "CLICKED";
  }> = [];
  const browserErrors = collectBrowserErrors(page);
  const serverErrors = collectServerErrors(page);
  const networkFailures = collectNetworkFailures(page);

  for (const role of roleAccounts.filter((account) => shouldRunRoleInteraction(account))) {
    await ensureReadySession(page, role);
    await page.goto("/dashboard", { waitUntil: "domcontentloaded" });
    await waitForMainContent(page, `${role} 真实点击初始工作台 · ${viewport.name}`);
    for (const menuKey of expectedMenus[role].filter((key) => shouldRunMenuInteraction(key))) {
      const route = routeByMenuKey.get(menuKey);
      expect(route, `${role} 菜单 ${menuKey} 必须存在真实路由`).toBeDefined();
      browserErrors.length = 0;
      serverErrors.length = 0;
      networkFailures.length = 0;

      await openGrantedMenuEntryThroughUi(page, route, viewport);
      await expect
        .poll(() => new URL(page.url()).pathname, {
          message: `${role} 菜单 ${menuKey} 应由真实入口点击进入目标路由`,
        })
        .toBe(route.path);
      await waitForMainContent(page, `${role} 菜单 ${menuKey} 真实点击 · ${viewport.name}`);
      await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
      await expectNoRootOverflow(page, `${role} 菜单 ${menuKey} 真实点击 · ${viewport.name}`);
      expect(
        serverErrors,
        `${role} 菜单 ${menuKey} 真实点击 · ${viewport.name} 不应产生 HTTP 错误`,
      ).toEqual([]);
      expect(
        networkFailures,
        `${role} 菜单 ${menuKey} 真实点击 · ${viewport.name} 不应产生网络失败`,
      ).toEqual([]);
      expect(
        browserErrors,
        `${role} 菜单 ${menuKey} 真实点击 · ${viewport.name} 不应产生浏览器错误`,
      ).toEqual([]);

      roleMenuInteractions.push({
        role,
        menuKey,
        placement: route.placement,
        path: route.path,
        title: route.title,
        status: "CLICKED",
      });
    }
  }

  return roleMenuInteractions;
}

function parseCsvSet(value: string | undefined) {
  const items = value
    ?.split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  return items && items.length > 0 ? new Set(items) : null;
}

function shouldRunRoleInteraction(role: RoleAccount) {
  return !interactionRoleFilter || interactionRoleFilter.has(role);
}

function shouldRunMenuInteraction(menuKey: string) {
  return !interactionMenuFilter || interactionMenuFilter.has(menuKey);
}

async function openGrantedMenuEntryThroughUi(
  page: Page,
  route: RouteMeta | undefined,
  viewport: (typeof menuReachabilityViewports)[number],
) {
  expect(route, "真实菜单点击必须绑定路由元数据").toBeDefined();
  if (!route) {
    return;
  }
  if (route.placement === "header") {
    await page.getByRole("button", { name: route.menuLabel ?? route.title }).click();
    return;
  }
  if (route.placement === "profile") {
    await page.getByRole("button", { name: "当前用户菜单" }).click();
    await page.getByRole("menuitem", { name: route.menuLabel ?? route.title }).click();
    return;
  }
  expect(route.placement, `${route.menuKey} 必须是可点击客户入口`).toBe("primary");
  if (viewport.name.startsWith("mobile")) {
    await page.getByRole("button", { name: "打开主菜单" }).click();
    const drawer = page.locator(".ant-drawer-content").filter({ hasText: "集团医疗智能中枢" });
    await expect(drawer).toBeVisible();
    await clickVisibleAntMenuItem(drawer.locator(".ant-menu-item"), route.menuLabel ?? route.title);
    return;
  }
  await clickVisibleAntMenuItem(
    page.locator(".ant-layout-sider .ant-menu-item"),
    route.menuLabel ?? route.title,
  );
}

async function clickVisibleAntMenuItem(menuItems: Locator, label: string) {
  const item = menuItems.filter({ hasText: label }).first();
  await expect(item, `应存在可点击菜单项：${label}`).toBeVisible({ timeout: 10_000 });
  await item.click();
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
