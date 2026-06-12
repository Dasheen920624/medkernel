import { expect, type APIResponse, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";

export const apiBase = requireEnv("E2E_API_BASE_URL");
export const appBase = (process.env.E2E_BASE_URL?.trim() || "http://localhost:5173").replace(
  /\/+$/,
  "",
);
const frontendApiBase = `${appBase}/medkernel/api/v1`;
export const tenantId = "t-1";
const defaultPassword = "Mk@2026dev";

export const roleAccounts = [
  "platform-governance-admin",
  "platform-knowledge-governor",
  "organization-admin",
  "identity-access-admin",
  "knowledge-governor",
  "clinical-governor",
  "clinical-decision-user",
  "nursing-collaborator",
  "medication-safety-user",
  "diagnostic-service-user",
  "quality-governor",
  "compliance-auditor",
  "integration-operator",
  "implementation-operator",
] as const;

export type RoleAccount = (typeof roleAccounts)[number];

export async function ensureReadySession(page: Page, role: RoleAccount) {
  await page.context().clearCookies();
  const password = stablePassword(role);
  let currentPassword = password;
  let login = await loginWith(page, role, password);
  if (!login.ok()) {
    currentPassword = defaultPassword;
    login = await loginWith(page, role, defaultPassword);
  }
  await expectOk(login, `${role} 登录`);
  let result = (await login.json()).data;

  if (result.mustChangePwd) {
    const change = await postApi(page, "/auth/change-password", {
      oldPassword: currentPassword,
      newPassword: password,
    });
    if (!change.ok()) {
      const retry = await postApi(page, "/auth/change-password", {
        oldPassword: defaultPassword,
        newPassword: password,
      });
      await expectOk(retry, `${role} 首次改密`);
    }
    const relogin = await loginWith(page, role, password);
    await expectOk(relogin, `${role} 改密后重新登录`);
    result = (await relogin.json()).data;
  }

  if (result.mfaRequired && !result.mfaBound) {
    const setupResponse = await postApi(page, "/auth/mfa/bind", { label: `${role} 安全设备` });
    await expectOk(setupResponse, `${role} 生成 MFA 密钥`);
    const setup = (await setupResponse.json()).data;
    const verifyResponse = await postApi(page, "/auth/mfa/bind", {
      label: `${role} 安全设备`,
      secret: setup.secret,
      code: totp(setup.secret),
    });
    await expectOk(verifyResponse, `${role} 验证 MFA`);
    const relogin = await loginWith(page, role, password);
    await expectOk(relogin, `${role} MFA 后重新登录`);
  }
  await loginWithFrontend(page, role, password);
  const frontendProfile = await page.request.get(`${frontendApiBase}/security/me`, {
    headers: { "X-Trace-Id": `e2e-front-profile-${role}-${Date.now()}` },
  });
  await expectOk(frontendProfile, `${role} 前台会话画像`);
  const profile = (await frontendProfile.json()).data as {
    roles: Array<{ code: string }>;
    menuKeys: string[];
  };
  expect(profile.roles.map((item) => item.code)).toContain(role);
  expect(profile.menuKeys).toContain("workbench");
}

export async function loginFromPlatformPage(page: Page, role: RoleAccount) {
  await page.context().clearCookies();
  await page.goto("/login");
  await expectLoginPageReady(page);

  const platformTenantSwitch = page.getByRole("button", { name: "平台治理" });
  if (await platformTenantSwitch.isVisible()) {
    await platformTenantSwitch.click();
    await expect(page.getByRole("heading", { name: "登录平台治理" })).toBeVisible();
  }

  await page.getByLabel("工号 / 账号").fill(role);
  await page.getByLabel("密码").fill(stablePassword(role));
  await page.getByRole("button", { name: "进入工作台" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

export async function expectLoginPageReady(page: Page) {
  await expect(page.getByRole("main", { name: "登录 MedKernel 工作台" })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: /登录(?:平台治理|机构工作台)/ }),
  ).toBeVisible();
}

export async function loginWith(page: Page, username: string, password: string) {
  return postApi(page, "/auth/login", { username, password, tenantId });
}

async function loginWithFrontend(page: Page, role: RoleAccount, password: string) {
  const response = await page.request.post(`${frontendApiBase}/auth/login`, {
    data: { username: role, password, tenantId },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-front-login-${role}-${Date.now()}`,
    },
  });
  await expectOk(response, `${role} 前台代理登录`);
}

export async function postApi(page: Page, path: string, data: unknown) {
  return writeApi(page, "post", path, data);
}

export async function patchApi(page: Page, path: string, data: unknown) {
  return writeApi(page, "patch", path, data);
}

async function writeApi(page: Page, method: "post" | "patch", path: string, data: unknown) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Trace-Id": `e2e-${Date.now()}`,
  };
  const xsrf = (await page.context().cookies(apiBase)).find(
    (cookie) => cookie.name === "XSRF-TOKEN",
  );
  if (xsrf) {
    headers["X-XSRF-TOKEN"] = xsrf.value;
  }
  return page.request[method](`${apiBase}${path}`, { data, headers });
}

export async function expectOk(response: APIResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
}

export function stablePassword(role: RoleAccount) {
  return `Mk@2026${role.replace(/-/g, "")}`;
}

function requireEnv(name: string) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} 未配置，E2E 必须显式指向当前真实后端。`);
  }
  return value;
}

function totp(secret: string) {
  const counter = Math.floor(Date.now() / 1000 / 30);
  const key = base32Decode(secret);
  const message = Buffer.alloc(8);
  message.writeBigInt64BE(BigInt(counter));
  const hash = createHmac("sha1", key).update(message).digest();
  const offset = hash[hash.length - 1] & 0x0f;
  const binary =
    ((hash[offset] & 0x7f) << 24) |
    ((hash[offset + 1] & 0xff) << 16) |
    ((hash[offset + 2] & 0xff) << 8) |
    (hash[offset + 3] & 0xff);
  return String(binary % 1_000_000).padStart(6, "0");
}

function base32Decode(value: string) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  let buffer = 0;
  let bitsLeft = 0;
  const bytes: number[] = [];
  for (const char of value.replace(/=|\s/g, "").toUpperCase()) {
    const index = alphabet.indexOf(char);
    if (index < 0) {
      throw new Error(`非法 TOTP 密钥字符：${char}`);
    }
    buffer = (buffer << 5) | index;
    bitsLeft += 5;
    if (bitsLeft >= 8) {
      bytes.push((buffer >> (bitsLeft - 8)) & 0xff);
      bitsLeft -= 8;
    }
  }
  return Buffer.from(bytes);
}
