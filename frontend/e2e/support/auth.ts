import { expect, type APIResponse, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";

export const apiBase = requireEnv("E2E_API_BASE_URL");
export const tenantId = "t-1";
const defaultPassword = "Mk@2026dev";

export const roleAccounts = [
  "platform-admin",
  "group-admin",
  "hospital-admin",
  "it-ops",
  "medical-affairs",
  "qa-manager",
  "insurance-manager",
  "dept-head",
  "specialist",
  "doctor",
  "nurse",
  "audit-compliance",
  "implementation-engineer",
] as const;

export type RoleAccount = (typeof roleAccounts)[number];

export async function ensureReadySession(page: Page, role: RoleAccount) {
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
}

export async function loginWith(page: Page, username: string, password: string) {
  return postApi(page, "/auth/login", { username, password, tenantId });
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
  const xsrf = (await page.context().cookies()).find((cookie) => cookie.name === "XSRF-TOKEN");
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
