import { describe, expect, it } from "vitest";

import {
  ROLE_ACCOUNT_CODES,
  resolveRoleCredentialOverrides,
} from "../../e2e/support/e2eRoleCredentials.ts";

const platformAccounts = Object.fromEntries(
  ROLE_ACCOUNT_CODES.map((role) => [
    role,
    {
      tenantId: "t-1",
      username: role,
      role,
      password: `platform-${role}`,
    },
  ]),
);

const rehearsalAccounts = Object.fromEntries(
  ROLE_ACCOUNT_CODES.map((role) => [
    role,
    {
      tenantId: "t-rehearsal",
      username: `rehearsal-${role}`,
      role,
      password: `rehearsal-${role}`,
    },
  ]),
);

describe("E2E 上线职责凭据契约", () => {
  it("只使用 canonical platform.accounts 四职责账号，不读取非权威账号块", () => {
    const credentials = resolveRoleCredentialOverrides({
      schemaVersion: "1.0.0",
      status: "READY",
      platform: {
        tenantId: "t-1",
        accounts: platformAccounts,
      },
      rehearsal: {
        tenantId: "t-rehearsal",
        accounts: rehearsalAccounts,
      },
    });

    expect(credentials["platform-admin"]).toMatchObject({
      tenantId: "t-1",
      username: "platform-admin",
      password: "platform-platform-admin",
    });
    expect(credentials["clinical-user"].tenantId).toBe("t-1");
  });

  it("缺少平台四职责账号时拒绝继续运行", () => {
    expect(() =>
      resolveRoleCredentialOverrides({
        schemaVersion: "1.0.0",
        status: "READY",
        rehearsal: {
          tenantId: "t-rehearsal",
          accounts: rehearsalAccounts,
        },
      }),
    ).toThrow("E2E 上线凭据缺少 canonical platform.accounts 四职责账号");
  });

  it("读取平台四职责账号作为唯一上线演练身份", () => {
    const credentials = resolveRoleCredentialOverrides({
      schemaVersion: "1.0.0",
      status: "READY",
      platform: {
        tenantId: "t-1",
        accounts: platformAccounts,
      },
    });

    expect(credentials["engine-operator"]).toMatchObject({
      tenantId: "t-1",
      username: "engine-operator",
      password: "platform-engine-operator",
    });
  });
});
