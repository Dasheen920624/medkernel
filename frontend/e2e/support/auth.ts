import { expect, type APIResponse, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";
import { readFileSync } from "node:fs";

import {
  ROLE_ACCOUNT_CODES,
  resolveLaunchCredentialScopes,
  type RoleAccountCode,
  type RoleCredentialScope,
  type ScopedRoleCredentialOverrides,
} from "./e2eRoleCredentials";

export const apiBase = requireEnv("E2E_API_BASE_URL");
export const appBase = (process.env.E2E_BASE_URL?.trim() || "http://localhost:5173").replace(
  /\/+$/,
  "",
);
const frontendApiBase = resolveFrontendApiBase(appBase);
export const tenantId = "t-1";
const defaultPassword = "Mk@2026dev";
export const roleAccounts = ROLE_ACCOUNT_CODES;
const defaultCredentialScope: RoleCredentialScope = "rehearsal";

export type RoleAccount = RoleAccountCode;

const credentialsConfigured = Boolean(process.env.E2E_ROLE_CREDENTIALS_FILE?.trim());
const credentialOverrides = loadCredentialOverrides();

export async function ensureReadySession(
  page: Page,
  role: RoleAccount,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  await resetRoleSession(page);
  const password = stablePassword(role, scope);
  const username = usernameFor(role, scope);
  let currentPassword = password;
  let login = await loginWith(page, username, password, scope);
  if (!login.ok() && !credentialsConfigured) {
    currentPassword = defaultPassword;
    login = await loginWith(page, username, defaultPassword, scope);
  }
  await expectOk(login, `${role} 登录`);
  let result = (await login.json()).data;

  if (process.env.E2E_EXPECT_MFA_DISABLED === "1") {
    expect(result.mfaRequired, `${role} 上线默认 MFA 必须关闭`).toBe(false);
  }

  if (result.mustChangePwd) {
    const change = await postApi(page, "/auth/change-password", {
      oldPassword: currentPassword,
      newPassword: password,
    });
    if (!change.ok() && !credentialsConfigured) {
      const retry = await postApi(page, "/auth/change-password", {
        oldPassword: defaultPassword,
        newPassword: password,
      });
      await expectOk(retry, `${role} 首次改密`);
    } else {
      await expectOk(change, `${role} 首次改密`);
    }
    const relogin = await loginWith(page, username, password, scope);
    await expectOk(relogin, `${role} 改密后重新登录`);
    result = (await relogin.json()).data;
    if (process.env.E2E_EXPECT_MFA_DISABLED === "1") {
      expect(result.mfaRequired, `${role} 改密后默认 MFA 必须关闭`).toBe(false);
    }
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
    const relogin = await loginWith(page, username, password, scope);
    await expectOk(relogin, `${role} MFA 后重新登录`);
  }
  await loginWithFrontend(page, role, password, scope);
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
  await reloadFrontendSession(page, role);
}

export async function loginFromPlatformPage(page: Page, role: RoleAccount) {
  await page.context().clearCookies();
  await page.goto(appPath("/login"));
  await expectLoginPageReady(page);
  const scope: RoleCredentialScope = "platform";

  const platformTenantSwitch = page
    .locator('[aria-label="登录类型切换"]')
    .getByRole("button", { name: "平台治理", exact: true });
  await expect(platformTenantSwitch).toBeVisible();
  await platformTenantSwitch.click();
  await expect(platformTenantSwitch).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("heading", { name: "登录平台治理" })).toBeVisible();

  await page.getByLabel("工号 / 账号").fill(usernameFor(role, scope));
  await page.getByLabel("密码").fill(stablePassword(role, scope));
  await page.getByRole("button", { name: "进入工作台" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

export async function expectLoginPageReady(page: Page) {
  await expect(page.getByRole("main", { name: "登录 MedKernel 工作台" })).toBeVisible();
  await expect(page.getByRole("heading", { name: /登录(?:平台治理|机构工作台)/ })).toBeVisible();
}

export async function loginWith(
  page: Page,
  username: string,
  password: string,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  return postApi(page, "/auth/login", {
    username,
    password,
    tenantId: tenantIdFor(username, scope),
  });
}

async function loginWithFrontend(
  page: Page,
  role: RoleAccount,
  password: string,
  scope: RoleCredentialScope,
) {
  const username = usernameFor(role, scope);
  const response = await page.request.post(`${frontendApiBase}/auth/login`, {
    data: { username, password, tenantId: tenantIdFor(role, scope) },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-front-login-${role}-${Date.now()}`,
    },
  });
  await expectOk(response, `${role} 前台代理登录`);
  await mirrorSecureCookiesForLocalProxy(page, response);
}

async function resetRoleSession(page: Page) {
  await Promise.allSettled([
    page.request.post(`${frontendApiBase}/auth/logout`, {
      headers: { "X-Trace-Id": `e2e-front-logout-${Date.now()}` },
    }),
    page.request.post(`${apiBase}/auth/logout`, {
      headers: { "X-Trace-Id": `e2e-api-logout-${Date.now()}` },
    }),
  ]);
  await page.context().clearCookies();
  await page.goto(appPath(`/login?e2e-session-reset=${Date.now()}`), {
    waitUntil: "domcontentloaded",
  });
}

async function reloadFrontendSession(page: Page, role: RoleAccount) {
  await page.goto(appPath(`/dashboard?e2e-session-refresh=${role}-${Date.now()}`), {
    waitUntil: "domcontentloaded",
  });
  await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible({
    timeout: 20_000,
  });
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

export function stablePassword(
  role: RoleAccount,
  scope: RoleCredentialScope = defaultCredentialScope,
) {
  return credentialOverrides[scope]?.[role]?.password ?? `Mk@2026${role.replace(/-/g, "")}`;
}

function usernameFor(role: RoleAccount, scope: RoleCredentialScope = defaultCredentialScope) {
  return credentialOverrides[scope]?.[role]?.username ?? role;
}

export function resolveFrontendApiBase(baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, "");
  const pathname = new URL(normalized).pathname.replace(/\/+$/, "");
  const contextPath = pathname.endsWith("/medkernel") ? "" : "/medkernel";
  return `${normalized}${contextPath}/api/v1`;
}

export function appPath(path: string) {
  const basePath = new URL(appBase).pathname.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  if (!basePath || basePath === "/") return normalizedPath;
  if (normalizedPath === "/") return basePath;
  return `${basePath}${normalizedPath}`;
}

function tenantIdFor(username: string, scope: RoleCredentialScope = defaultCredentialScope) {
  return credentialFor(username, scope)?.tenantId ?? tenantId;
}

function credentialFor(principal: string, scope: RoleCredentialScope = defaultCredentialScope) {
  const byRole = credentialOverrides[scope]?.[principal as RoleAccount];
  if (byRole) return byRole;
  return Object.values(credentialOverrides[scope] ?? {}).find(
    (credential) => credential.username === principal,
  );
}

function loadCredentialOverrides() {
  const file = process.env.E2E_ROLE_CREDENTIALS_FILE?.trim();
  if (!file) return {} as Partial<ScopedRoleCredentialOverrides>;
  const parsed = JSON.parse(readFileSync(file, "utf8"));
  const source =
    parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as {
          schemaVersion?: unknown;
          status?: unknown;
          platform?: { tenantId?: unknown; accounts?: unknown };
          rehearsal?: { tenantId?: unknown; accounts?: unknown };
        })
      : null;
  if (!source || source.schemaVersion !== "1.0.0" || source.status !== "READY") {
    throw new Error("E2E 上线凭据必须使用 READY 状态的 1.0.0 契约");
  }
  const retiredCredentialFields = [
    "role" + "Accounts",
    "platform" + "RoleAccounts",
    "customer" + "Tenant",
  ];
  if (retiredCredentialFields.some((field) => hasOwnField(source, field))) {
    throw new Error("E2E 上线凭据不得包含已移除旧账号字段");
  }
  if (
    !source.platform ||
    typeof source.platform !== "object" ||
    Array.isArray(source.platform) ||
    !source.platform.accounts ||
    typeof source.platform.accounts !== "object" ||
    Array.isArray(source.platform.accounts)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical platform.accounts 四职责账号");
  }
  if (
    !source.rehearsal ||
    typeof source.rehearsal !== "object" ||
    Array.isArray(source.rehearsal) ||
    !source.rehearsal.accounts ||
    typeof source.rehearsal.accounts !== "object" ||
    Array.isArray(source.rehearsal.accounts)
  ) {
    throw new Error("E2E 上线凭据缺少 canonical rehearsal.accounts 机构四职责账号");
  }
  return resolveLaunchCredentialScopes({
    schemaVersion: source.schemaVersion,
    status: source.status,
    platform: {
      tenantId: source.platform.tenantId,
      accounts: source.platform.accounts,
    },
    rehearsal: {
      tenantId: source.rehearsal.tenantId,
      accounts: source.rehearsal.accounts,
    },
  });
}

function hasOwnField(source: object, field: string) {
  return Object.prototype.hasOwnProperty.call(source, field);
}

async function mirrorSecureCookiesForLocalProxy(page: Page, response: APIResponse) {
  const frontendUrl = new URL(frontendApiBase);
  if (frontendUrl.protocol !== "http:" || !isLoopbackHost(frontendUrl.hostname)) {
    return;
  }
  const cookies = response
    .headersArray()
    .filter((header) => header.name.toLowerCase() === "set-cookie")
    .map((header) => parseSetCookieForLocalProxy(header.value, frontendUrl.origin))
    .filter((cookie): cookie is NonNullable<ReturnType<typeof parseSetCookieForLocalProxy>> =>
      Boolean(cookie),
    );
  if (cookies.length > 0) {
    await page.context().addCookies(cookies);
  }
}

export function parseSetCookieForLocalProxy(header: string, origin: string) {
  const [nameValue, ...attributes] = header.split(";");
  const separator = nameValue.indexOf("=");
  if (separator <= 0) return null;
  const secure = attributes.some((attribute) => attribute.trim().toLowerCase() === "secure");
  if (!secure) return null;
  const name = nameValue.slice(0, separator).trim();
  const value = nameValue.slice(separator + 1).trim();
  if (!name || !value) return null;
  const sameSite = sameSiteAttribute(cookieAttribute(attributes, "samesite"));
  return {
    name,
    value,
    url: origin,
    httpOnly: attributes.some((attribute) => attribute.trim().toLowerCase() === "httponly"),
    secure: false,
    sameSite,
  };
}

function cookieAttribute(attributes: string[], name: string) {
  const prefix = `${name.toLowerCase()}=`;
  const match = attributes.find((attribute) => attribute.trim().toLowerCase().startsWith(prefix));
  if (!match) return undefined;
  return match.trim().slice(prefix.length);
}

function sameSiteAttribute(value?: string) {
  const normalized = value?.toLowerCase();
  if (normalized === "lax") return "Lax" as const;
  if (normalized === "none") return "None" as const;
  return "Strict" as const;
}

function isLoopbackHost(hostname: string) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
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
