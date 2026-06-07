import { expect, test, type APIResponse, type Page } from "@playwright/test";
import { createHmac } from "node:crypto";

const apiBase = requireEnv("E2E_API_BASE_URL");
const tenantId = "t-1";
const defaultPassword = "Mk@2026dev";

const roleAccounts = [
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

type RoleAccount = (typeof roleAccounts)[number];

function requireEnv(name: string) {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} 未配置，E2E 必须显式指向当前真实后端。`);
  }
  return value;
}

test.describe.configure({ mode: "serial" });

test.describe("D0 登录域真实验收", () => {
  for (const role of roleAccounts) {
    test(`${role} 可完成真实登录并获得二级菜单权限画像`, async ({ page }) => {
      await ensureReadySession(page, role);

      const profile = await getSecurityProfile(page);
      expect(profile.roles.map((item: { code: string }) => item.code)).toContain(role);
      expect(profile.menuKeys).toContain("workbench");
      expect(profile.menuKeys).not.toContain("clinical-run");
      expect(profile.menuKeys).not.toContain("pilot-setup");

      await page.goto("/dashboard");
      await expect(page.getByText("工作台").first()).toBeVisible();
      await expect(page.getByRole("button", { name: "当前用户菜单" })).toBeVisible();
    });
  }

  test("临床医生只看到临床运行菜单，并可从用户菜单退出登录", async ({ page }) => {
    await ensureReadySession(page, "doctor");
    await page.goto("/dashboard");

    await expect(page.getByText("临床运行").first()).toBeVisible();
    await expect(page.getByText("患者主索引").first()).toBeVisible();
    await expect(page.getByText("试点准备").first()).toHaveCount(0);

    await page.getByRole("button", { name: "当前用户菜单" }).click();
    await page.getByRole("menuitem", { name: /退出登录/ }).click();
    await page.getByRole("button", { name: "确认退出" }).click();

    await expect(page.getByRole("heading", { name: "集团医疗智能中枢" })).toBeVisible();
  });
});

async function ensureReadySession(page: Page, role: RoleAccount) {
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

async function loginWith(page: Page, username: string, password: string) {
  return postApi(page, "/auth/login", { username, password, tenantId });
}

async function getSecurityProfile(page: Page) {
  const response = await page.request.get(`${apiBase}/security/me`, {
    headers: { "X-Trace-Id": `e2e-d0-${Date.now()}` },
  });
  await expectOk(response, "读取当前权限画像");
  return (await response.json()).data;
}

async function postApi(page: Page, path: string, data: unknown) {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "X-Trace-Id": `e2e-d0-${Date.now()}`,
  };
  const xsrf = (await page.context().cookies()).find((cookie) => cookie.name === "XSRF-TOKEN");
  if (xsrf) {
    headers["X-XSRF-TOKEN"] = xsrf.value;
  }
  return page.request.post(`${apiBase}${path}`, { data, headers });
}

async function expectOk(response: APIResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
}

function stablePassword(role: RoleAccount) {
  return `Mk@2026${role.replace(/-/g, "")}`;
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
