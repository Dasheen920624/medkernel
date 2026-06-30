import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";

import type * as AuthSupport from "../../e2e/support/auth.ts";

const envSnapshot = {
  E2E_API_BASE_URL: process.env.E2E_API_BASE_URL,
  E2E_ROLE_CREDENTIALS_FILE: process.env.E2E_ROLE_CREDENTIALS_FILE,
};

let tempDir: string | null = null;

afterEach(() => {
  if (tempDir) {
    rmSync(tempDir, { recursive: true, force: true });
    tempDir = null;
  }
  restoreEnv("E2E_API_BASE_URL", envSnapshot.E2E_API_BASE_URL);
  restoreEnv("E2E_ROLE_CREDENTIALS_FILE", envSnapshot.E2E_ROLE_CREDENTIALS_FILE);
});

describe("E2E credential contract", () => {
  it("loads canonical platform account credentials during auth support module initialization", async () => {
    tempDir = mkdtempSync(join(tmpdir(), "medkernel-e2e-auth-"));
    const credentialsPath = join(tempDir, "current-launch.json");
    writeFileSync(
      credentialsPath,
      JSON.stringify({
        schemaVersion: "1.0.0",
        status: "READY",
        platform: {
          tenantId: "t-1",
          accounts: {
            "platform-admin": account("platform-admin", "t-1", "platform"),
            "engine-operator": account("engine-operator", "t-1", "platform"),
            "clinical-user": account("clinical-user", "t-1", "platform"),
            auditor: account("auditor", "t-1", "platform"),
          },
        },
        rehearsal: {
          tenantId: "t-rehearsal",
          accounts: {
            "platform-admin": account("platform-admin", "t-rehearsal", "rehearsal"),
            "engine-operator": account("engine-operator", "t-rehearsal", "rehearsal"),
            "clinical-user": account("clinical-user", "t-rehearsal", "rehearsal"),
            auditor: account("auditor", "t-rehearsal", "rehearsal"),
          },
        },
      }),
      "utf8",
    );
    process.env.E2E_API_BASE_URL = "https://127.0.0.1/medkernel/api/v1";
    process.env.E2E_ROLE_CREDENTIALS_FILE = credentialsPath;

    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    expect(auth.roleAccounts).toEqual([
      "platform-admin",
      "engine-operator",
      "clinical-user",
      "auditor",
    ]);
    expect(auth.stablePassword("engine-operator")).toBe("secret-rehearsal-engine-operator");
    expect(auth.stablePassword("engine-operator", "platform")).toBe(
      "secret-platform-engine-operator",
    );
    expect(auth.resolveFrontendApiBase("http://localhost:5173")).toBe(
      "http://localhost:5173/medkernel/api/v1",
    );
    expect(auth.resolveFrontendApiBase("https://193.112.107.134/medkernel")).toBe(
      "https://193.112.107.134/medkernel/api/v1",
    );
  });

  it("mirrors secure backend cookies only as local proxy cookies for HTTP frontdesk rehearsal", async () => {
    process.env.E2E_API_BASE_URL = "https://127.0.0.1/medkernel/api/v1";
    const auth = (await import("../../e2e/support/auth.ts")) as typeof AuthSupport;

    const cookie = auth.parseSetCookieForLocalProxy(
      "mk_access=jwt-value; Path=/medkernel; Max-Age=1800; Secure; HttpOnly; SameSite=Strict",
      "http://127.0.0.1:5173",
    );

    expect(cookie).toMatchObject({
      name: "mk_access",
      value: "jwt-value",
      url: "http://127.0.0.1:5173",
      secure: false,
      httpOnly: true,
      sameSite: "Strict",
    });
    expect(
      auth.parseSetCookieForLocalProxy(
        "mk_access=jwt-value; Path=/medkernel; HttpOnly; SameSite=Strict",
        "http://127.0.0.1:5173",
      ),
    ).toBeNull();
  });
});

function account(role: string, tenantId: string, prefix: string) {
  return {
    tenantId,
    username: `${prefix}-${role}`,
    role,
    password: `secret-${prefix}-${role}`,
  };
}

function restoreEnv(name: string, value: string | undefined) {
  if (value === undefined) {
    delete process.env[name];
  } else {
    process.env[name] = value;
  }
}
