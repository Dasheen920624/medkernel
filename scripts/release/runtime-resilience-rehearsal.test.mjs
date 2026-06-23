import assert from "node:assert/strict";
import test from "node:test";

import { buildLaunchCredentialPlan } from "./launch-account-bootstrap-lib.mjs";
import {
  readRuntimeResilienceConfig,
  runRuntimeResilienceRehearsal,
} from "./runtime-resilience-rehearsal-lib.mjs";

test("运行韧性配置复用统一医疗引擎运营员凭据且证据位于仓库外", () => {
  const config = readRuntimeResilienceConfig(
    {
      LAUNCH_API_BASE_URL: "https://127.0.0.1/medkernel/api/v1",
      LAUNCH_CREDENTIALS_FILE: "/var/lib/medkernel/credentials/current-launch.json",
      RUNTIME_RESILIENCE_PROVIDER_CODE: "ollama-launch",
      MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel",
    },
    {
      repoRoot: "/workspace/medkernel",
      readFile: () => JSON.stringify(readyCredentials()),
    },
  );

  assert.equal(config.operator.role, "engine-operator");
  assert.equal(config.operator.tenantId, "t-1");
  assert.equal(config.providerCode, "ollama-launch");
  assert.equal(
    config.evidencePath,
    "/var/lib/medkernel/evidence/current-launch/runtime-resilience.json",
  );
});

test("模型关闭时只阻断模型调用且 B0 核心继续可用，真实探活后恢复启用", async () => {
  const requests = [];
  const evidence = await runRuntimeResilienceRehearsal({
    apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
    operator: readyCredentials().platform.accounts["engine-operator"],
    providerCode: "ollama-launch",
    fetchImpl: createResilienceFetch(requests),
    now: () => "2026-06-22T10:00:00.000Z",
  });

  assert.equal(evidence.status, "PASSED");
  assert.deepEqual(evidence.disabled.blockingRequiredItems, ["MODEL_PROVIDER"]);
  assert.equal(evidence.disabled.modelInvocationAllowed, false);
  assert.deepEqual(evidence.b0, {
    fixtureCount: 17,
    passedCount: 17,
    modelRequiredCount: 0,
  });
  assert.equal(evidence.restored.providerEnabled, true);
  assert.equal(evidence.restored.providerStatus, "HEALTHY");
  assert.equal(evidence.restored.modelInvocationAllowed, true);
  assert.deepEqual(
    requests.map((item) => `${item.method} ${item.path}`),
    [
      "POST /auth/login",
      "GET /model-providers/ollama-launch",
      "POST /model-providers/ollama-launch/disable",
      "GET /engine/knowledge-production/readiness",
      "GET /engine/domain-facades/b0-fixtures",
      "POST /model-providers/ollama-launch/health-check",
      "POST /model-providers/ollama-launch/enable",
      "GET /engine/knowledge-production/readiness",
    ],
  );
  assert.deepEqual(requests[2].body, {
    capabilityCode: null,
    reason: "134 完整上线韧性演练：确认模型关闭期间诚实降级且 B0 核心继续运行",
    expectedVersion: 2,
    confirmedHighRisk: true,
  });
  assert.equal(requests[6].body.expectedVersion, 4);
  assert.equal(requests[6].body.confirmedHighRisk, true);
  assert.equal(JSON.stringify(evidence).includes("mk_access"), false);
  assert.equal(JSON.stringify(evidence).includes("Strong@"), false);
});

test("模型关闭后存在额外必需阻断项时拒绝伪报为 Provider 单点降级", async () => {
  const requests = [];
  await assert.rejects(
    () =>
      runRuntimeResilienceRehearsal({
        apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
        operator: readyCredentials().platform.accounts["engine-operator"],
        providerCode: "ollama-launch",
        fetchImpl: createResilienceFetch(requests, { extraBlocker: true }),
      }),
    /除 MODEL_PROVIDER 外仍有必需阻断项/u,
  );
  assert.equal(requests.some((item) => item.path.endsWith("/enable")), true);
});

test("任一 B0 门面失败时拒绝恢复并放行整套演练", async () => {
  const requests = [];
  await assert.rejects(
    () =>
      runRuntimeResilienceRehearsal({
        apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
        operator: readyCredentials().platform.accounts["engine-operator"],
        providerCode: "ollama-launch",
        fetchImpl: createResilienceFetch(requests, { b0Failure: true }),
      }),
    /B0 核心门面/u,
  );
  assert.equal(requests.some((item) => item.path.endsWith("/enable")), true);
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

function createResilienceFetch(requests, options = {}) {
  let provider = providerView(true, "HEALTHY", 2);
  return async (url, init = {}) => {
    const parsed = new URL(url);
    const path = parsed.pathname.replace(/^.*\/api\/v1/u, "");
    const method = init.method ?? "GET";
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ method, path, body, search: parsed.search });

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
        "mk_access=session; Path=/; HttpOnly, XSRF-TOKEN=xsrf; Path=/",
      );
    }
    if (method === "GET" && path === "/model-providers/ollama-launch") {
      return response({ data: provider });
    }
    if (path.endsWith("/disable")) {
      provider = providerView(false, "HEALTHY", 3);
      return response({ data: provider });
    }
    if (path === "/engine/knowledge-production/readiness") {
      return response({ data: provider.enabled ? readyReadiness() : blockedReadiness(options) });
    }
    if (path === "/engine/domain-facades/b0-fixtures") {
      const fixtures = Array.from({ length: 17 }, (_, index) => ({
        code: `FACADE-${index + 1}`,
        status: options.b0Failure && index === 16 ? "FAIL" : "PASS",
        b0Executable: true,
        modelRequired: false,
      }));
      return response({ data: fixtures });
    }
    if (path.endsWith("/health-check")) {
      provider = providerView(false, "HEALTHY", 4);
      return response({ data: provider });
    }
    if (path.endsWith("/enable")) {
      provider = providerView(true, "HEALTHY", 5);
      return response({ data: provider });
    }
    throw new Error(`未模拟接口 ${method} ${path}`);
  };
}

function blockedReadiness(options) {
  const items = [
    { code: "LITERATURE_ROOT", ready: true, required: true },
    { code: "DEPLOYMENT_FORM", ready: true, required: true },
    { code: "MODEL_PROVIDER", ready: false, required: true },
    { code: "MODEL_EVALUATION", ready: true, required: true },
  ];
  if (options.extraBlocker) {
    items.push({ code: "MODEL_POLICY", ready: false, required: true });
  }
  return {
    ready: false,
    modelInvocationAllowed: false,
    providerCode: "ollama-launch",
    items,
  };
}

function readyReadiness() {
  return {
    ready: true,
    modelInvocationAllowed: true,
    providerCode: "ollama-launch",
    items: [
      { code: "LITERATURE_ROOT", ready: true, required: true },
      { code: "DEPLOYMENT_FORM", ready: true, required: true },
      { code: "MODEL_PROVIDER", ready: true, required: true },
      { code: "MODEL_EVALUATION", ready: true, required: true },
    ],
  };
}

function providerView(enabled, status, version) {
  return {
    providerCode: "ollama-launch",
    providerType: "OLLAMA",
    endpointUri: "http://127.0.0.1:11434",
    modelVersion: "medkernel-qwen25:1.5b-v1",
    enabled,
    status,
    version,
  };
}

function response(payload, setCookie = "") {
  return {
    ok: true,
    status: 200,
    headers: { get: (name) => (name.toLowerCase() === "set-cookie" ? setCookie : null) },
    text: async () => JSON.stringify(payload),
  };
}
