import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  ASSIGNABLE_ROLES,
  assertLaunchOutputPathsAvailable,
  buildLaunchCredentialPlan,
  readLaunchBootstrapConfig,
  runLaunchAccountBootstrap,
  selectLaunchAccount,
  validateLaunchCredentials,
} from "./launch-account-bootstrap-lib.mjs";

test("上线接管实现使用已移除字段命名而非旧入口命名", () => {
  const source = readFileSync(
    new URL("./launch-account-bootstrap-lib.mjs", import.meta.url),
    "utf8",
  );
  const retiredConstantName = ["LEGACY", "FIELDS"].join("_");

  assert.doesNotMatch(source, new RegExp(`\\b${retiredConstantName}\\b`, "u"));
});

test("上线凭据只包含内置接管身份与平台、演练机构两组四职责账号", () => {
  const plan = buildLaunchCredentialPlan({
    generatedAt: "2026-06-22T08:00:00.000Z",
    passwordFactory: (label) => `Strong@${label}2026!`,
  });

  assert.deepEqual(Object.keys(plan.platform.accounts), [...ASSIGNABLE_ROLES]);
  assert.deepEqual(Object.keys(plan.rehearsal.accounts), [...ASSIGNABLE_ROLES]);
  assert.equal(plan.platform.tenantId, "t-1");
  assert.equal(plan.rehearsal.tenantId, "t-rehearsal");
  assert.equal(plan.platform.takeover.role, "system-superadmin");
  assert.equal(plan.platform.takeover.assignable, false);
  assert.equal(plan.platform.accounts["engine-operator"].username, "engine-operator");
  assert.equal(plan.rehearsal.accounts.auditor.role, "auditor");
});

test("正式凭据契约拒绝旧字段、临时口令和缺失职责", () => {
  const plan = buildLaunchCredentialPlan({
    generatedAt: "2026-06-22T08:00:00.000Z",
    passwordFactory: (label) => `Strong@${label}2026!`,
  });
  const credentials = structuredClone(plan);
  stripInitialPasswords(credentials);

  assert.doesNotThrow(() => validateLaunchCredentials(credentials));
  assert.equal(
    selectLaunchAccount(credentials, "platform", "engine-operator").tenantId,
    "t-1",
  );
  assert.equal(
    selectLaunchAccount(credentials, "rehearsal", "clinical-user").tenantId,
    "t-rehearsal",
  );

  const retiredShape = { ...credentials, roleAccounts: {} };
  assert.throws(() => validateLaunchCredentials(retiredShape), /旧凭据字段/u);

  const missing = structuredClone(credentials);
  delete missing.rehearsal.accounts.auditor;
  assert.throws(() => validateLaunchCredentials(missing), /四职责/u);

  const leaked = structuredClone(credentials);
  leaked.platform.accounts["platform-admin"].initialPassword = "temporary";
  assert.throws(() => validateLaunchCredentials(leaked), /临时口令/u);
});

test("接管配置只接受仓库外的令牌、凭据和证据路径", () => {
  const config = readLaunchBootstrapConfig(
    {
      LAUNCH_API_BASE_URL: "https://127.0.0.1/medkernel/api/v1",
      LAUNCH_BOOTSTRAP_TOKEN_FILE: "/run/secrets/bootstrap-init-token.txt",
      LAUNCH_CREDENTIALS_FILE: "/var/lib/medkernel/credentials/current-launch.json",
      MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel",
    },
    {
      repoRoot: "/workspace/medkernel",
      readFile: () => "bootstrap-token-at-least-thirty-two-bytes\n",
    },
  );

  assert.equal(config.bootstrapToken, "bootstrap-token-at-least-thirty-two-bytes");
  assert.equal(
    config.evidencePath,
    "/var/lib/medkernel/evidence/current-launch/account-bootstrap.json",
  );
  assert.throws(
    () =>
      readLaunchBootstrapConfig(
        {
          LAUNCH_API_BASE_URL: "https://127.0.0.1/medkernel/api/v1",
          LAUNCH_BOOTSTRAP_TOKEN_FILE: "/run/secrets/bootstrap-init-token.txt",
          LAUNCH_CREDENTIALS_FILE: "/workspace/medkernel/runtime/accounts.json",
        },
        {
          repoRoot: "/workspace/medkernel",
          readFile: () => "bootstrap-token-at-least-thirty-two-bytes",
        },
      ),
    /必须位于代码仓库之外/u,
  );
});

test("全新接管拒绝覆盖既有凭据或证据", () => {
  assert.doesNotThrow(() =>
    assertLaunchOutputPathsAvailable(
      { credentialsPath: "/runtime/accounts.json", evidencePath: "/runtime/evidence.json" },
      () => false,
    ),
  );
  assert.throws(
    () =>
      assertLaunchOutputPathsAvailable(
        { credentialsPath: "/runtime/accounts.json", evidencePath: "/runtime/evidence.json" },
        (file) => file.endsWith("accounts.json"),
      ),
    /拒绝覆盖既有上线凭据/u,
  );
});

test("全新接管真实完成首登改密、MFA 关闭、两租户四职责与权限画像核验", async () => {
  const calls = [];
  const plan = buildLaunchCredentialPlan({
    generatedAt: "2026-06-22T08:00:00.000Z",
    passwordFactory: (label) => `Strong@${label}2026!`,
  });
  const fetchImpl = createSuccessfulBootstrapFetch(calls, plan);

  const result = await runLaunchAccountBootstrap({
    apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
    bootstrapToken: "bootstrap-token-at-least-thirty-two-bytes",
    plan,
    fetchImpl,
    now: () => "2026-06-22T08:10:00.000Z",
  });

  assert.equal(result.evidence.status, "PASSED");
  assert.equal(result.evidence.mfaRequired, false);
  assert.deepEqual(result.evidence.verifiedRoles, [...ASSIGNABLE_ROLES]);
  assert.doesNotThrow(() => validateLaunchCredentials(result.credentials));
  assert.equal(calls.filter((call) => call.path === "/compliance/users").length, 7);
  assert.equal(calls.filter((call) => call.path === "/admin/tenants").length, 1);
  assert.equal(calls.filter((call) => call.path === "/security/me").length, 9);
});

test("已初始化环境拒绝伪装成全新接管", async () => {
  const plan = buildLaunchCredentialPlan({
    passwordFactory: (label) => `Strong@${label}2026!`,
  });
  await assert.rejects(
    () =>
      runLaunchAccountBootstrap({
        apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
        bootstrapToken: "bootstrap-token-at-least-thirty-two-bytes",
        plan,
        fetchImpl: async () => response({ data: { initialized: true } }),
      }),
    /必须处于未初始化状态/u,
  );
});

function stripInitialPasswords(credentials) {
  delete credentials.platform.takeover.initialPassword;
  for (const scope of [credentials.platform, credentials.rehearsal]) {
    for (const account of Object.values(scope.accounts)) {
      delete account.initialPassword;
    }
  }
  return credentials;
}

function createSuccessfulBootstrapFetch(calls, plan) {
  const roleByTenantAndUsername = new Map([
    [`t-1/${plan.platform.takeover.username}`, "system-superadmin"],
    ...Object.values(plan.platform.accounts).map((account) => [
      `${account.tenantId}/${account.username}`,
      account.role,
    ]),
    ...Object.values(plan.rehearsal.accounts).map((account) => [
      `${account.tenantId}/${account.username}`,
      account.role,
    ]),
  ]);
  const finalPasswordByAccount = new Map([
    [`t-1/${plan.platform.takeover.username}`, plan.platform.takeover.password],
    ...Object.values(plan.platform.accounts).map((account) => [
      `${account.tenantId}/${account.username}`,
      account.password,
    ]),
    ...Object.values(plan.rehearsal.accounts).map((account) => [
      `${account.tenantId}/${account.username}`,
      account.password,
    ]),
  ]);

  return async (url, init = {}) => {
    const parsed = new URL(url);
    const path = parsed.pathname.replace(/^.*\/api\/v1/u, "");
    const body = init.body ? JSON.parse(init.body) : null;
    const method = init.method ?? "GET";
    calls.push({ method, path, body });

    if (method === "GET" && path === "/bootstrap/status") {
      return response({ data: { initialized: false } });
    }
    if (path === "/bootstrap/init-token") {
      return response({ data: { valid: true, expiresAt: "2026-06-22T09:00:00Z" } });
    }
    if (path === "/bootstrap/password") {
      return response({ data: { userId: body.username, tenantId: "t-1" } });
    }
    if (path === "/auth/login") {
      const key = `${body.tenantId}/${body.username}`;
      const role = roleByTenantAndUsername.get(key);
      assert.ok(role, `未知登录账号 ${key}`);
      const finalPassword = finalPasswordByAccount.get(key);
      return response(
        {
          data: {
            userId: body.username,
            tenantId: body.tenantId,
            roles: [role],
            mustChangePwd: body.password !== finalPassword,
            mfaRequired: false,
            mfaBound: false,
          },
        },
        "mk_access=session; Path=/; HttpOnly, XSRF-TOKEN=xsrf; Path=/",
      );
    }
    if (path === "/auth/change-password") return response({ data: null });
    if (path === "/compliance/users") {
      return response({ data: { user: { userId: body.username }, tempPassword: null } });
    }
    if (path === "/admin/tenants") {
      return response({ data: { tenantId: body.tenantId, adminUsername: body.adminUsername } });
    }
    if (path === "/security/me") {
      const cookie = String(init.headers?.Cookie ?? "");
      assert.match(cookie, /mk_access=/u);
      const lastLogin = [...calls].reverse().find((call) => call.path === "/auth/login");
      const key = `${lastLogin.body.tenantId}/${lastLogin.body.username}`;
      return response({
        data: {
          roles: [{ code: roleByTenantAndUsername.get(key) }],
          menuKeys: ["workbench"],
          mustChangePwd: false,
          mfaRequired: false,
          mfaBound: false,
        },
      });
    }
    throw new Error(`未模拟接口 ${method} ${path}`);
  };
}

function response(payload, setCookie = "") {
  const source = JSON.stringify(payload);
  return {
    ok: true,
    status: 200,
    headers: { get: (name) => (name.toLowerCase() === "set-cookie" ? setCookie : null) },
    text: async () => source,
  };
}
