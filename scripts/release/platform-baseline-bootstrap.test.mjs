import assert from "node:assert/strict";
import test from "node:test";

import { buildLaunchCredentialPlan } from "./launch-account-bootstrap-lib.mjs";
import {
  readPlatformBaselineBootstrapConfig,
  runPlatformBaselineBootstrap,
} from "./platform-baseline-bootstrap-lib.mjs";

test("平台基线启动配置复用平台 engine-operator 且证据固定在仓库外", () => {
  const credentials = readyCredentials();
  const config = readPlatformBaselineBootstrapConfig(
    {
      LAUNCH_API_BASE_URL: "https://127.0.0.1/medkernel/api/v1",
      LAUNCH_CREDENTIALS_FILE: "/var/lib/medkernel/credentials/current-launch.json",
      MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel",
    },
    {
      repoRoot: "/workspace/medkernel",
      readFile: () => JSON.stringify(credentials),
    },
  );

  assert.equal(config.operator.role, "engine-operator");
  assert.equal(config.operator.tenantId, "t-1");
  assert.equal(
    config.evidencePath,
    "/var/lib/medkernel/evidence/current-launch/platform-baseline.json",
  );
});

test("清库时先固化字段目录草稿再发布平台标准版本并回读完整基线", async () => {
  const requests = [];
  const evidence = await runPlatformBaselineBootstrap({
    apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
    operator: readyCredentials().platform.accounts["engine-operator"],
    fetchImpl: createPlatformBaselineFetch(requests),
    now: () => "2026-06-22T08:40:00.000Z",
  });

  assert.equal(evidence.status, "PASSED");
  assert.equal(evidence.stage, "PLATFORM_BASELINE_BOOTSTRAP");
  assert.equal(evidence.reused, false);
  assert.equal(evidence.fieldCatalog.assetType, "FIELD_CATALOG");
  assert.equal(evidence.fieldCatalog.assetIdentity, "FIELD.CATALOG.CLINICAL_CONTEXT");
  assert.equal(evidence.fieldCatalog.entryState, "ACTIVE");
  assert.equal(evidence.baseline.revisionNo, 1);
  assert.deepEqual(
    requests.map((item) => `${item.method} ${item.path}`),
    [
      "POST /auth/login",
      "GET /engine/releases/platform-baselines/current",
      "POST /engine/context/field-catalog/drafts",
      "POST /engine/releases/platform-baselines",
      "GET /engine/releases/platform-baselines/current",
    ],
  );
  assert.deepEqual(requests[3].body.publishVersionIds, ["field-catalog-v1"]);
});

test("已存在含字段目录的当前平台标准版本时只复用并写证据", async () => {
  const requests = [];
  const evidence = await runPlatformBaselineBootstrap({
    apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
    operator: readyCredentials().platform.accounts["engine-operator"],
    fetchImpl: createPlatformBaselineFetch(requests, { currentInitiallyExists: true }),
    now: () => "2026-06-22T08:40:00.000Z",
  });

  assert.equal(evidence.status, "PASSED");
  assert.equal(evidence.reused, true);
  assert.deepEqual(
    requests.map((item) => `${item.method} ${item.path}`),
    [
      "POST /auth/login",
      "GET /engine/releases/platform-baselines/current",
    ],
  );
});

test("字段目录草稿或平台基线回读缺失时拒绝继续演练", async () => {
  await assert.rejects(
    () =>
      runPlatformBaselineBootstrap({
        apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
        operator: readyCredentials().platform.accounts["engine-operator"],
        fetchImpl: createPlatformBaselineFetch([], { draftType: "RULE" }),
      }),
    /字段目录草稿/u,
  );
});

function readyCredentials() {
  const credentials = buildLaunchCredentialPlan({
    generatedAt: "2026-06-22T08:00:00.000Z",
    passwordFactory: (label) => `Strong@${label}2026!`,
  });
  delete credentials.platform.takeover.initialPassword;
  for (const scope of [credentials.platform, credentials.rehearsal]) {
    for (const account of Object.values(scope.accounts)) delete account.initialPassword;
  }
  return credentials;
}

function createPlatformBaselineFetch(requests, options = {}) {
  let published = options.currentInitiallyExists === true;
  return async (url, init = {}) => {
    const parsed = new URL(url);
    const path = parsed.pathname.replace(/^.*\/api\/v1/u, "");
    const method = init.method ?? "GET";
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ method, path, body });
    if (path === "/auth/login") {
      return response(
        {
          data: {
            userId: "engine-operator",
            tenantId: "t-1",
            roles: ["engine-operator"],
            mustChangePwd: false,
            mfaRequired: false,
            mfaBound: false,
          },
        },
        200,
        "mk_access=session; Path=/; HttpOnly, XSRF-TOKEN=xsrf; Path=/",
      );
    }
    if (method === "GET" && path === "/engine/releases/platform-baselines/current") {
      return published
        ? response({ data: platformBaselineDetail() })
        : response({ code: "ENG-API-005", detail: "平台尚未发布标准版本" }, 404);
    }
    if (method === "POST" && path === "/engine/context/field-catalog/drafts") {
      return response({
        data: {
          versionId: "field-catalog-v1",
          versionNo: "1",
          assetType: options.draftType ?? "FIELD_CATALOG",
          assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
          status: "DRAFT",
        },
      });
    }
    if (method === "POST" && path === "/engine/releases/platform-baselines") {
      published = true;
      return response({
        data: {
          baselineReleaseId: "baseline-1",
          revisionNo: 1,
          manifestHash: "sha256:abc",
        },
      });
    }
    throw new Error(`未模拟接口 ${method} ${path}`);
  };
}

function platformBaselineDetail() {
  return {
    release: {
      baselineReleaseId: "baseline-1",
      revisionNo: 1,
      manifestHash: "sha256:abc",
    },
    items: [
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        entryState: "ACTIVE",
        versionId: "field-catalog-v1",
        versionNo: "1",
        contentHash: "sha256:def",
      },
    ],
  };
}

function response(payload, status = 200, setCookie = "") {
  const text = JSON.stringify(payload);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => (name.toLowerCase() === "set-cookie" ? setCookie : null) },
    text: async () => text,
  };
}
