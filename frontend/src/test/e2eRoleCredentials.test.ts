import { describe, expect, it } from "vitest";

import {
  ROLE_ACCOUNT_CODES,
  resolveLaunchCredentialScopes,
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
  it("同时读取平台治理与上线机构两组 canonical 四职责账号", () => {
    const credentials = resolveLaunchCredentialScopes({
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

    expect(credentials.platform["platform-admin"]).toMatchObject({
      tenantId: "t-1",
      username: "platform-admin",
      password: "platform-platform-admin",
    });
    expect(credentials.rehearsal["clinical-user"]).toMatchObject({
      tenantId: "t-rehearsal",
      username: "rehearsal-clinical-user",
      password: "rehearsal-clinical-user",
    });
  });

  it("缺少任一 canonical 四职责账号块时拒绝继续运行", () => {
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

    expect(() =>
      resolveRoleCredentialOverrides({
        schemaVersion: "1.0.0",
        status: "READY",
        platform: {
          tenantId: "t-1",
          accounts: platformAccounts,
        },
      }),
    ).toThrow("E2E 上线凭据缺少 canonical rehearsal.accounts 机构四职责账号");
  });

  it("默认读取上线机构身份，平台治理身份必须显式选择", () => {
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

    expect(credentials["engine-operator"]).toMatchObject({
      tenantId: "t-rehearsal",
      username: "rehearsal-engine-operator",
      password: "rehearsal-engine-operator",
    });

    const platformCredentials = resolveRoleCredentialOverrides(
      {
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
      },
      "platform",
    );
    expect(platformCredentials["engine-operator"]).toMatchObject({
      tenantId: "t-1",
      username: "engine-operator",
      password: "platform-engine-operator",
    });
  });
});
