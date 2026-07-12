import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

import { expect, test, type Page, type TestInfo } from "@playwright/test";

import {
  canAccessRoute,
  routeMetas,
  type RoutePermissionProfile,
} from "../src/shared/config/routes";
import { productEntryCatalog } from "../src/shared/contracts/productEntryCatalog.generated";
import {
  apiBase,
  ensureReadySession,
  expectOk,
  getApi,
  getFrontendApi,
  loginWithFrontendProxy,
  patchApi,
  postApi,
  resolvedTenantIdFor,
  responseData,
  roleAccounts,
  type RoleAccount,
} from "./support/auth";

const execFileAsync = promisify(execFile);
const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const rolelessAccount = {
  userId: "e2e-launch-contract-roleless",
  username: "e2e-launch-contract-roleless",
  initialPassword: "Mk@2026launchinit",
  password: "Mk@2026launchready",
};

type ObservedPermissionProfile = RoutePermissionProfile & {
  observedStatus: number;
  userId: string;
  roles: Array<{
    code: string;
    scopeLevel: string;
    scopeCode: string;
  }>;
  permissions: Array<{ code: string }>;
  menuKeys: string[];
  dataScope: Record<string, string | null>;
};

test.describe.configure({ mode: "serial" });

test.describe("全部 done 功能真实路由验收", () => {
  test("每个真实角色打开全部授权页面且没有系统级错误", async ({ page }, testInfo) => {
    test.setTimeout(1_200_000);
    const stateRenderer = await observePageStateRuntime(testInfo);
    const profiles = await loadRoleProfiles(page);
    const browserErrors = collectBrowserErrors(page);
    const serverErrors = collectServerErrors(page);
    const protectedRoutes = routeMetas.filter((route) => route.requireAuth && route.path !== "/");
    const entryByRoute = new Map(productEntryCatalog.map((entry) => [entry.route, entry]));
    const allowedObservations = new Map<string, Record<string, unknown>>();
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

        const entry = entryByRoute.get(route.path);
        if (entry && !allowedObservations.has(entry.entryCode)) {
          const roleAssignment = profile?.roles.find((item) => item.code === role);
          expect(
            roleAssignment,
            `${entry.entryCode} 必须回读 ${role} 的真实职责范围`,
          ).toBeDefined();
          const scopeLevel = roleAssignment?.scopeLevel.toUpperCase() ?? "";
          const effectiveScopeField = effectiveScopeFieldFor(scopeLevel);
          const effectiveScopeCode = profile?.dataScope[effectiveScopeField] ?? null;
          expect(effectiveScopeCode, `${entry.entryCode} 有效组织范围必须与真实职责分配相交`).toBe(
            roleAssignment?.scopeCode,
          );
          const grantedPermissionCodes = new Set(
            profile?.permissions.map((permission) => permission.code) ?? [],
          );
          const observedPermissionCodes = entry.requiredPermissions.filter((permission) =>
            grantedPermissionCodes.has(permission),
          );
          expect(
            observedPermissionCodes,
            `${entry.entryCode} 必须从 /security/me 回读全部入口权限`,
          ).toEqual(entry.requiredPermissions);
          expect(entry.responsibilityRoles).toContain(role);
          const actualPath = expectNormalizedRoutePath(page, entry.route);

          allowedObservations.set(entry.entryCode, {
            observedCode: "ENTRY_PERMISSION_ALLOWED",
            role,
            profileStatus: profile?.observedStatus,
            actualPath,
            headingText:
              (await page.locator("main").getByRole("heading").first().textContent())?.trim() ?? "",
            permissionCodes: observedPermissionCodes,
            organizationScope: {
              observedCode: "ENTRY_ORGANIZATION_SCOPE",
              mode: entry.organizationScopeMode,
              tenantId: profile?.dataScope.tenantId,
              assignmentScopeLevel: scopeLevel,
              assignmentScopeCode: roleAssignment?.scopeCode,
              effectiveScopeField,
              effectiveScopeCode,
            },
          });
        }
      }
    }

    expect(allowedObservations.size, "35 个产品入口必须都有真实允许访问观察").toBe(
      productEntryCatalog.length,
    );
    const rolelessProfile = await prepareRolelessFrontendSession(page);
    const forbiddenObservations = new Map<string, Record<string, unknown>>();
    for (const entry of productEntryCatalog) {
      await page.goto(entry.route, { waitUntil: "domcontentloaded" });
      const forbiddenTitle = page.getByText("当前权限不足", { exact: true });
      await expect(forbiddenTitle).toBeVisible();
      const actualPath = expectNormalizedRoutePath(page, entry.route);
      forbiddenObservations.set(entry.entryCode, {
        observedCode: "ENTRY_PERMISSION_FORBIDDEN",
        userId: rolelessProfile.profile.userId,
        profileStatus: rolelessProfile.status,
        actualPath,
        title: (await forbiddenTitle.textContent())?.trim() ?? "",
        permissionCodes: [...rolelessProfile.profile.permissions.map((item) => item.code)],
        menuKeys: [...rolelessProfile.profile.menuKeys],
      });
    }

    await testInfo.attach("product-entry-runtime-observations", {
      contentType: "application/json",
      body: Buffer.from(
        JSON.stringify({
          schemaVersion: "1.0.0",
          observationEvidence: "launch.entry.runtime-observation",
          stateRenderer,
          entries: productEntryCatalog.map((entry) => {
            const allowed = allowedObservations.get(entry.entryCode);
            const forbidden = forbiddenObservations.get(entry.entryCode);
            expect(allowed, `${entry.entryCode} 缺少允许访问观察`).toBeDefined();
            expect(forbidden, `${entry.entryCode} 缺少拒绝访问观察`).toBeDefined();
            const { organizationScope, ...allowedFields } = allowed ?? {};
            return {
              entryCode: entry.entryCode,
              route: entry.route,
              allowed: allowedFields,
              forbidden,
              organizationScope,
            };
          }),
        }),
        "utf8",
      ),
    });
  });
});

async function loadRoleProfiles(page: Page) {
  const profiles = new Map<RoleAccount, ObservedPermissionProfile>();
  for (const role of roleAccounts) {
    await ensureReadySession(page, role);
    const response = await page.request.get(`${apiBase}/security/me`, {
      headers: { "X-Trace-Id": `e2e-route-profile-${role}-${Date.now()}` },
    });
    await expectOk(response, `读取 ${role} 权限画像`);
    profiles.set(role, {
      ...((await response.json()).data as Omit<ObservedPermissionProfile, "observedStatus">),
      observedStatus: response.status(),
    });
  }
  return profiles;
}

async function observePageStateRuntime(testInfo: TestInfo) {
  const outputPath = testInfo.outputPath("page-state-vitest.json");
  await execFileAsync(
    path.join(frontendRoot, "node_modules/.bin/vitest"),
    ["run", "src/shared/ui/PageState.test.tsx", "--reporter=json", `--outputFile=${outputPath}`],
    { cwd: frontendRoot, env: process.env, maxBuffer: 4 * 1024 * 1024 },
  );
  const rawResult = await readFile(outputPath);
  const result = JSON.parse(rawResult.toString("utf8")) as {
    success?: boolean;
    testResults?: Array<{
      name?: string;
      assertionResults?: Array<{ fullName?: string; status?: string }>;
    }>;
  };
  expect(result.success, "PageState 定向 Vitest 必须完整通过").toBe(true);
  expect(
    result.testResults?.some((item) => item.name?.endsWith("src/shared/ui/PageState.test.tsx")),
    "Vitest JSON 必须来自真实 PageState DOM 测试",
  ).toBe(true);
  const assertions = result.testResults?.flatMap((item) => item.assertionResults ?? []) ?? [];
  const requiredCodes = productEntryCatalog[0].sixStates.map(
    (state) => `STATE_${state.toUpperCase()}`,
  );
  const observedAssertions = requiredCodes.map((observedCode) => {
    const matches = assertions.filter((item) => item.fullName?.includes(`[${observedCode}]`));
    expect(matches, `${observedCode} 必须恰有一个真实 DOM 观察`).toHaveLength(1);
    expect(matches[0].status, `${observedCode} 真实 DOM 观察必须通过`).toBe("passed");
    return {
      observedCode,
      testName: matches[0].fullName,
      status: matches[0].status,
    };
  });
  return {
    runner: "vitest",
    testFile: "frontend/src/shared/ui/PageState.test.tsx",
    resultSha256: createHash("sha256").update(rawResult).digest("hex"),
    assertions: observedAssertions,
  };
}

async function prepareRolelessFrontendSession(page: Page) {
  await ensureReadySession(page, "platform-admin");
  const detailPath = `/compliance/users/${encodeURIComponent(rolelessAccount.userId)}`;
  const existing = await getApi(page, detailPath);
  if (existing.status() === 404) {
    const created = await postApi(page, "/compliance/users", {
      credentialManaged: true,
      userId: rolelessAccount.userId,
      displayName: "完整入口合同无职责观察账号",
      username: rolelessAccount.username,
      initialPassword: rolelessAccount.initialPassword,
    });
    await expectOk(created, "创建无职责入口合同观察账号");
  } else {
    await expectOk(existing, "读取无职责入口合同观察账号");
  }
  const enabled = await patchApi(page, `${detailPath}/status`, { status: "ACTIVE" });
  await expectOk(enabled, "启用无职责入口合同观察账号");
  const detail = await getApi(page, detailPath);
  await expectOk(detail, "回读无职责入口合同观察账号");
  const detailData = (await responseData(detail)) as { roles?: unknown[] };
  expect(detailData.roles ?? [], "无职责观察账号不得绑定任何职责").toEqual([]);

  const tenantId = resolvedTenantIdFor("platform-admin");
  let currentPassword = rolelessAccount.password;
  let login = await loginRolelessApi(page, currentPassword, tenantId);
  if (!login.ok()) {
    currentPassword = rolelessAccount.initialPassword;
    login = await loginRolelessApi(page, currentPassword, tenantId);
  }
  await expectOk(login, "无职责入口合同观察账号登录");
  const loginData = (await login.json()).data as { mustChangePwd?: boolean };
  if (loginData.mustChangePwd) {
    expect(currentPassword, "首次改密只能从初始化密码执行").toBe(rolelessAccount.initialPassword);
    const changed = await postApi(page, "/auth/change-password", {
      oldPassword: currentPassword,
      newPassword: rolelessAccount.password,
    });
    await expectOk(changed, "无职责入口合同观察账号首次改密");
    login = await loginRolelessApi(page, rolelessAccount.password, tenantId);
    await expectOk(login, "无职责入口合同观察账号改密后登录");
  }

  await page.context().clearCookies();
  const frontendLogin = await loginWithFrontendProxy(
    page,
    rolelessAccount.username,
    rolelessAccount.password,
    tenantId,
  );
  await expectOk(frontendLogin, "无职责入口合同观察账号前台登录");
  const profileResponse = await getFrontendApi(page, "/security/me");
  await expectOk(profileResponse, "无职责入口合同观察账号前台画像");
  const profile = (await profileResponse.json()).data as ObservedPermissionProfile;
  expect(profile.roles, "无职责画像 roles 必须为空").toEqual([]);
  expect(profile.permissions, "无职责画像 permissions 必须为空").toEqual([]);
  expect(profile.menuKeys, "无职责画像 menuKeys 必须为空").toEqual([]);
  return { profile, status: profileResponse.status() };
}

async function loginRolelessApi(page: Page, password: string, tenantId: string) {
  return page.request.post(`${apiBase}/auth/login`, {
    data: { username: rolelessAccount.username, password, tenantId },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-roleless-login-${Date.now()}`,
    },
  });
}

function effectiveScopeFieldFor(scopeLevel: string) {
  const fields: Record<string, string> = {
    TENANT: "tenantId",
    GROUP: "groupId",
    FACILITY: "hospitalId",
    CAMPUS: "campusId",
    SITE: "siteId",
    DEPARTMENT: "departmentId",
    WARD: "wardId",
    SPECIALTY: "specialtyId",
  };
  const field = fields[scopeLevel];
  expect(field, `不支持的职责范围级别 ${scopeLevel}`).toBeTruthy();
  return field;
}

function expectNormalizedRoutePath(page: Page, route: string) {
  const pathname = new URL(page.url()).pathname;
  expect(pathname === route || pathname.endsWith(route), `浏览器必须停留在真实路由 ${route}`).toBe(
    true,
  );
  return pathname;
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
