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
  it("优先使用演练机构四职责账号，避免真实前台数据落入平台治理租户", () => {
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
      tenantId: "t-rehearsal",
      username: "rehearsal-platform-admin",
      password: "rehearsal-platform-admin",
    });
    expect(credentials["clinical-user"].tenantId).toBe("t-rehearsal");
  });

  it("缺少演练机构账号时保留平台四职责账号作为本地开发回退", () => {
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
