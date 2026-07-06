import { expect, test, type Browser, type Page, type TestInfo } from "@playwright/test";

import {
  apiBase,
  ensureReadySession,
  expectLoginPageReady,
  expectOk,
  patchApi,
  postApi,
  resolvedTenantIdFor,
  stablePassword,
  totp,
} from "./support/auth";

type SystemConfigItem = {
  key: string;
  value: string;
  version: number;
};

const mfaConfigKey = "medkernel.auth.mfa.enabled";
const platformTenantId = resolvedTenantIdFor("platform-admin", "platform");

const requiredMfaLoginScenarioEvidence = [
  {
    code: "S14",
    observedStages: [
      "配置中心读取上线默认 MFA 关闭",
      "创建 MFA 临时平台管理员账号",
      "临时账号完成首次改密并绑定 TOTP",
      "配置中心临时开启 MFA",
      "登录页要求已绑定账号完成 MFA 验证",
      "前台提交真实 TOTP 验证并进入工作台",
      "验证后回读权限画像与 MFA 状态",
      "恢复 MFA 上线默认关闭状态",
      "停用 MFA 演练临时管理员账号",
    ],
  },
] as const;

test.describe.configure({ mode: "serial" });

test.describe("D0 MFA 真实前台登录验收", () => {
  test("开启 MFA 后已绑定账号必须在登录页完成真实 TOTP 验证", async ({
    browser,
    page,
  }, testInfo) => {
    test.setTimeout(240_000);
    const suffix = Date.now().toString(36);
    const mfaAccount = {
      userId: `e2e-mfa-admin-${suffix}`,
      username: `e2e-mfa-admin-${suffix}`,
      initialPassword: "Mk@2026MfaInit!",
      password: "Mk@2026MfaFinal!",
    };
    const adminContext = await browser.newContext();
    const mfaAdminContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const mfaAdminPage = await mfaAdminContext.newPage();
    let originalConfig: SystemConfigItem | null = null;
    let enabledByTest = false;
    let accountCreated = false;
    const observedStages = new Set<string>();

    try {
      await loginAsPlatformAdmin(adminPage);
      originalConfig = await readMfaConfig(adminPage);
      expect(originalConfig.value, "上线默认 MFA 必须关闭，演练用例负责临时开启并恢复").toBe(
        "false",
      );
      recordMfaLoginStage(observedStages, "配置中心读取上线默认 MFA 关闭");

      await createMfaAdminAccount(adminPage, mfaAccount);
      accountCreated = true;
      recordMfaLoginStage(observedStages, "创建 MFA 临时平台管理员账号");
      const secret = await prepareBoundMfaAccount(mfaAdminPage, mfaAccount);
      recordMfaLoginStage(observedStages, "临时账号完成首次改密并绑定 TOTP");

      const enabledConfig = await updateMfaConfig(
        mfaAdminPage,
        "true",
        originalConfig.version,
        "临时开启 MFA 真实前台登录演练",
      );
      enabledByTest = enabledConfig.value === "true";
      recordMfaLoginStage(observedStages, "配置中心临时开启 MFA");

      await page.context().clearCookies();
      await page.goto("/login");
      await expectLoginPageReady(page);

      const platformTenantSwitch = page
        .locator('[aria-label="登录类型切换"]')
        .getByRole("button", { name: "平台治理", exact: true });
      if (await platformTenantSwitch.isVisible()) {
        await platformTenantSwitch.click();
        await expect(platformTenantSwitch).toHaveAttribute("aria-pressed", "true");
      }

      await page.getByLabel("工号 / 账号").fill(mfaAccount.username);
      await page.getByLabel("密码").fill(mfaAccount.password);
      await page.getByRole("button", { name: "进入工作台" }).click();

      await expect(page).toHaveURL(/\/bootstrap$/);
      await expect(page.getByRole("heading", { name: "验证多因素认证" })).toBeVisible();
      await expect(page.getByText("请输入认证器中的动态验证码", { exact: false })).toBeVisible();
      recordMfaLoginStage(observedStages, "登录页要求已绑定账号完成 MFA 验证");

      const verifyResponsePromise = waitForPost(page, "/api/v1/auth/mfa/verify");
      await page.getByLabel("动态验证码").fill(totp(secret));
      await page.getByRole("button", { name: "验证并进入系统" }).click();
      const verifyResponse = await verifyResponsePromise;
      expect(verifyResponse.ok(), "前台提交 TOTP 验证应成功").toBe(true);
      const verifyPayload = (await verifyResponse.json()) as { data?: { verified?: boolean } };
      expect(verifyPayload.data?.verified).toBe(true);

      await expect(page.getByText("账号安全设置完成")).toBeVisible();
      await page.getByRole("button", { name: "进入工作台" }).click();
      await expect(page).toHaveURL(/\/dashboard$/);
      await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible({
        timeout: 20_000,
      });
      recordMfaLoginStage(observedStages, "前台提交真实 TOTP 验证并进入工作台");

      const profileResponse = await page.request.get(`${apiBase}/security/me`, {
        headers: { "X-Trace-Id": `e2e-mfa-profile-${suffix}` },
      });
      await expectOk(profileResponse, "读取 MFA 验证后权限画像");
      const profile = (await profileResponse.json()).data as {
        username?: string;
        mfaRequired?: boolean;
        mfaBound?: boolean;
        mfaVerified?: boolean;
        roles?: Array<{ code?: string }>;
      };
      expect(profile.username).toBe(mfaAccount.username);
      expect(profile.roles?.map((role) => role.code)).toContain("platform-admin");
      expect(profile.mfaRequired).toBe(true);
      expect(profile.mfaBound).toBe(true);
      expect(profile.mfaVerified).toBe(true);
      recordMfaLoginStage(observedStages, "验证后回读权限画像与 MFA 状态");

      const screenshotPath = testInfo.outputPath("mfa-login-dashboard.png");
      await page.screenshot({ path: screenshotPath, fullPage: true });
      await testInfo.attach("mfa-login-dashboard", {
        path: screenshotPath,
        contentType: "image/png",
      });
    } finally {
      try {
        if (originalConfig) {
          await restoreMfaConfig(mfaAdminPage, originalConfig.value);
          recordMfaLoginStage(observedStages, "恢复 MFA 上线默认关闭状态");
        } else if (enabledByTest) {
          await restoreMfaConfig(mfaAdminPage, "false");
          recordMfaLoginStage(observedStages, "恢复 MFA 上线默认关闭状态");
        }
      } finally {
        try {
          if (accountCreated) {
            await disableMfaAdminAccount(adminPage, mfaAccount.userId);
            recordMfaLoginStage(observedStages, "停用 MFA 演练临时管理员账号");
          }
        } finally {
          await Promise.all([adminContext.close(), mfaAdminContext.close()]);
        }
      }
    }
    await attachMfaLoginScenarioEvidence(testInfo, observedStages);
  });
});

async function loginAsPlatformAdmin(page: Page) {
  await ensureReadySession(page, "platform-admin", "platform");
}

async function createMfaAdminAccount(
  page: Page,
  account: { userId: string; username: string; initialPassword: string },
) {
  const response = await postApi(page, "/compliance/users", {
    credentialManaged: true,
    userId: account.userId,
    displayName: "MFA 前台登录演练管理员",
    username: account.username,
    roleCode: "platform-admin",
    initialPassword: account.initialPassword,
  });
  await expectOk(response, "创建 MFA 前台登录演练账号");
}

async function prepareBoundMfaAccount(
  page: Page,
  account: { username: string; initialPassword: string; password: string },
) {
  const login = await page.request.post(`${apiBase}/auth/login`, {
    data: {
      username: account.username,
      password: account.initialPassword,
      tenantId: platformTenantId,
    },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-mfa-account-login-${Date.now()}`,
    },
  });
  await expectOk(login, "MFA 演练账号首次登录");

  const change = await postApi(page, "/auth/change-password", {
    oldPassword: account.initialPassword,
    newPassword: account.password,
  });
  await expectOk(change, "MFA 演练账号首次改密");

  const relogin = await page.request.post(`${apiBase}/auth/login`, {
    data: {
      username: account.username,
      password: account.password,
      tenantId: platformTenantId,
    },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-mfa-account-relogin-${Date.now()}`,
    },
  });
  await expectOk(relogin, "MFA 演练账号改密后登录");

  const setupResponse = await postApi(page, "/auth/mfa/bind", {
    label: "MFA 前台登录演练安全设备",
  });
  await expectOk(setupResponse, "生成 MFA 演练账号密钥");
  const setup = (await setupResponse.json()).data as { secret?: string };
  expect(setup.secret, "MFA 绑定必须返回 TOTP 密钥").toBeTruthy();

  const verifyResponse = await postApi(page, "/auth/mfa/bind", {
    label: "MFA 前台登录演练安全设备",
    secret: setup.secret,
    code: totp(setup.secret ?? ""),
  });
  await expectOk(verifyResponse, "绑定 MFA 演练账号密钥");
  return setup.secret ?? "";
}

async function readMfaConfig(page: Page) {
  const response = await page.request.get(
    `${apiBase}/system/configs?prefix=${encodeURIComponent("medkernel.auth.mfa")}`,
    { headers: { "X-Trace-Id": `e2e-mfa-config-read-${Date.now()}` } },
  );
  await expectOk(response, "读取 MFA 配置");
  const configs = (await response.json()).data as SystemConfigItem[];
  const config = configs.find((item) => item.key === mfaConfigKey);
  expect(config, "MFA 开关必须由配置中心登记").toBeDefined();
  return config as SystemConfigItem;
}

async function updateMfaConfig(page: Page, value: string, expectedVersion: number, reason: string) {
  const response = await patchApi(page, `/system/configs/${encodeURIComponent(mfaConfigKey)}`, {
    value,
    reason,
    expectedVersion,
    confirmedHighRisk: true,
  });
  await expectOk(response, reason);
  return (await response.json()).data as SystemConfigItem;
}

async function restoreMfaConfig(page: Page, value: string) {
  const current = await readMfaConfig(page);
  if (current.value === value) return;
  await updateMfaConfig(page, value, current.version, "恢复 MFA 上线默认关闭状态");
}

async function disableMfaAdminAccount(page: Page, userId: string) {
  const response = await patchApi(page, `/compliance/users/${encodeURIComponent(userId)}/status`, {
    status: "DISABLED",
  });
  await expectOk(response, "停用 MFA 前台登录演练临时管理员账号");
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function recordMfaLoginStage(observedStages: Set<string>, stage: string) {
  observedStages.add(stage);
}

async function attachMfaLoginScenarioEvidence(testInfo: TestInfo, observedStageSet: Set<string>) {
  const scenarioEvidence = requiredMfaLoginScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages = requiredMfaLoginScenarioEvidence.find(
        (item) => item.code === scenario.code,
      )?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  await testInfo.attach("mfa-login-scenario-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: completedScenarioCodes,
        productLayers: ["FOUNDATION_GOVERNANCE"],
        serviceCombinations: ["COMPLIANCE_OPERATIONS"],
        scenarioEvidence,
      },
      null,
      2,
    ),
    contentType: "application/json",
  });
}
