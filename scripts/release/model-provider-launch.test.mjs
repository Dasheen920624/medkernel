import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
  buildProviderRegressionCases,
  readModelProviderLaunchConfig,
  runModelProviderLaunch,
} from "./model-provider-launch-lib.mjs";
import { buildLaunchCredentialPlan } from "./launch-account-bootstrap-lib.mjs";

const MANIFEST_PATH = fileURLToPath(
  new URL("../knowledge/manifests/full-knowledge-rehearsal-1.0.0.json", import.meta.url),
);
const manifest = JSON.parse(readFileSync(MANIFEST_PATH, "utf8"));

test("正式 Provider 评测基线使用三条真实来源并只验证来源约束与不可推断边界", () => {
  const cases = buildProviderRegressionCases(manifest);

  assert.equal(cases.length, 3);
  for (const item of cases) {
    assert.equal(item.capabilityCode, "knowledge.production.knowledge");
    assert.equal(item.expectedPhrase, "证据不足，不可推断");
    assert.equal(item.citationRequired, true);
    assert.equal(item.enabled, true);
    assert.match(item.sourceReference, /^https:\/\//u);
    assert.match(item.caseInput, new RegExp(item.sourceReference.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&"), "u"));
    assert.match(item.caseInput, /输出两行/u);
    assert.match(item.caseInput, /第一行必须完全等于：证据不足，不可推断。/u);
    assert.match(
      item.caseInput,
      new RegExp(`第二行必须完全等于：来源：${item.sourceReference.replace(/[.*+?^${}()|[\]\\]/gu, "\\$&")}`, "u"),
    );
    assert.match(item.caseInput, /禁止输出其他内容/u);
    assert.deepEqual(item.forbiddenAssertions, ["自动开立医嘱", "已确诊", "推荐剂量"]);
  }
});

test("Provider 上线配置复用统一平台四职责凭据且证据位于仓库外", () => {
  const credentials = readyCredentials();
  const config = readModelProviderLaunchConfig(
    {
      LAUNCH_API_BASE_URL: "https://127.0.0.1/medkernel/api/v1",
      LAUNCH_CREDENTIALS_FILE: "/var/lib/medkernel/credentials/current-launch.json",
      LAUNCH_MODEL_PROVIDER_CODE: "ollama-launch",
      LAUNCH_MODEL_PROVIDER_TYPE: "OLLAMA",
      LAUNCH_MODEL_PROVIDER_ENDPOINT: "http://127.0.0.1:11434",
      LAUNCH_MODEL_VERSION: "medkernel-qwen25:1.5b-v1",
      FULL_KNOWLEDGE_MANIFEST_PATH: MANIFEST_PATH,
      MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel",
    },
    {
      repoRoot: "/workspace/medkernel",
      readFile: (file) =>
        file === MANIFEST_PATH ? readFileSync(file, "utf8") : JSON.stringify(credentials),
    },
  );

  assert.equal(config.operator.role, "engine-operator");
  assert.equal(config.operator.tenantId, "t-1");
  assert.equal(config.systemOperator.role, "platform-admin");
  assert.equal(config.systemOperator.tenantId, "t-1");
  assert.equal(config.provider.type, "OLLAMA");
  assert.equal(
    config.knowledgeLiteratureRootUri,
    "file:///var/lib/medkernel/platform-knowledge/t-1/literature-materials/",
  );
  assert.equal(
    config.evidencePath,
    "/var/lib/medkernel/evidence/current-launch/model-provider.json",
  );
});

test("正式 Provider 上线按配置、探活、真实评测、当前操作者确认顺序启用", async () => {
  const requests = [];
  const result = await runModelProviderLaunch({
    apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
    systemOperator: readyCredentials().platform.accounts["platform-admin"],
    operator: readyCredentials().platform.accounts["engine-operator"],
    provider: {
      code: "ollama-launch",
      type: "OLLAMA",
      endpoint: "http://127.0.0.1:11434",
      modelVersion: "medkernel-qwen25:1.5b-v1",
    },
    manifest,
    fetchImpl: createProviderFetch(requests),
    now: () => "2026-06-22T08:20:00.000Z",
  });

  assert.equal(result.status, "PASSED");
  assert.equal(result.provider.enabled, true);
  assert.equal(result.provider.status, "HEALTHY");
  assert.equal(result.evaluation.totalCases, 3);
  assert.equal(result.evaluation.failedCases, 0);
  assert.equal(result.readiness.ready, true);
  assert.equal(result.knowledgeGovernance.literatureRoot.value, "file:///var/lib/medkernel/platform-knowledge/t-1/literature-materials/");
  assert.equal(result.knowledgeGovernance.policy.routeStrategy, "LOCAL_MODEL");
  assert.equal(result.knowledgeGovernance.versionBundle.modelVersion, "medkernel-qwen25:1.5b-v1");
  assert.deepEqual(
    requests.filter((item) => item.path !== "/auth/login").map((item) => `${item.method} ${item.path}`),
    [
      "PATCH /system/configs/medkernel.knowledge.literature.material-root-uri",
      "GET /model-providers/ollama-launch",
      "PUT /model-providers/ollama-launch",
      "POST /model-providers/ollama-launch/health-check",
      "POST /model-evaluations/regression-cases:bulk-import",
      "POST /model-evaluations",
      "PUT /model-capabilities/policies/knowledge.production.knowledge",
      "POST /model-versions/bundles",
      "POST /model-providers/ollama-launch/enable",
      "GET /engine/knowledge-production/readiness?producer=API_MODEL&capabilityCode=knowledge.production.knowledge&providerCode=ollama-launch",
    ],
  );
  const literature = requests.find((item) => item.path === "/system/configs/medkernel.knowledge.literature.material-root-uri");
  assert.equal(literature.body.confirmedHighRisk, true);
  assert.match(literature.body.reason, /134 完整上线演练/u);
  const providerConfig = requests.find((item) => item.path === "/model-providers/ollama-launch" && item.method === "PUT");
  assert.equal(providerConfig.body.expectedVersion, null);
  const policy = requests.find((item) => item.path === "/model-capabilities/policies/knowledge.production.knowledge");
  assert.deepEqual(policy.body.fallbackOrder, ["LOCAL_MODEL", "BASELINE"]);
  assert.equal(policy.body.desensitizeStrategy, "MASK_ALL");
  assert.equal(policy.body.timeoutMs, 120_000);
  const bundle = requests.find((item) => item.path === "/model-versions/bundles");
  assert.equal(bundle.body.capabilityCode, "knowledge.production.knowledge");
  assert.equal(bundle.body.modelVersion, "medkernel-qwen25:1.5b-v1");
});

test("正式 Provider 重新上线时使用当前关系库版本避免恢复演练冲突", async () => {
  const requests = [];
  const result = await runModelProviderLaunch({
    apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
    systemOperator: readyCredentials().platform.accounts["platform-admin"],
    operator: readyCredentials().platform.accounts["engine-operator"],
    provider: {
      code: "ollama-launch",
      type: "OLLAMA",
      endpoint: "http://127.0.0.1:11434",
      modelVersion: "medkernel-qwen25:1.5b-v1",
    },
    manifest,
    fetchImpl: createProviderFetch(requests, {
      existingProviderVersion: 7,
      evaluationTotalCases: 6,
    }),
  });

  assert.equal(result.status, "PASSED");
  const providerConfig = requests.find((item) => item.path === "/model-providers/ollama-launch" && item.method === "PUT");
  assert.equal(providerConfig.body.expectedVersion, 7);
});

test("医学回归失败时禁止继续启用 Provider", async () => {
  const fetchImpl = createProviderFetch([], { evaluationStatus: "FAILED" });
  await assert.rejects(
    () =>
      runModelProviderLaunch({
        apiBaseUrl: "https://127.0.0.1/medkernel/api/v1",
        systemOperator: readyCredentials().platform.accounts["platform-admin"],
        operator: readyCredentials().platform.accounts["engine-operator"],
        provider: {
          code: "ollama-launch",
          type: "OLLAMA",
          endpoint: "http://127.0.0.1:11434",
          modelVersion: "medkernel-qwen25:1.5b-v1",
        },
        manifest,
        fetchImpl,
      }),
    /医学回归评测未通过/u,
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

function createProviderFetch(requests, options = {}) {
  return async (url, init = {}) => {
    const parsed = new URL(url);
    const path = parsed.pathname.replace(/^.*\/api\/v1/u, "") + parsed.search;
    const cleanPath = parsed.pathname.replace(/^.*\/api\/v1/u, "");
    const method = init.method ?? "GET";
    const body = init.body ? JSON.parse(init.body) : null;
    requests.push({ method, path, body });
    if (cleanPath === "/auth/login") {
      const role = body.username;
      return response(
        {
          data: {
            userId: role,
            tenantId: "t-1",
            roles: [role],
            mustChangePwd: false,
            mfaRequired: false,
            mfaBound: false,
          },
        },
        "mk_access=session; Path=/; HttpOnly, XSRF-TOKEN=xsrf; Path=/",
      );
    }
    if (method === "PATCH" && cleanPath === "/system/configs/medkernel.knowledge.literature.material-root-uri") {
      return response({
        data: {
          key: "medkernel.knowledge.literature.material-root-uri",
          value: body.value,
          risk: "HIGH",
          protectedConfig: true,
          version: 2,
        },
      });
    }
    if (method === "GET" && cleanPath === "/model-providers/ollama-launch") {
      if (options.existingProviderVersion === undefined) {
        return response(
          { success: false, code: "ENG-API-004", message: "模型服务不存在" },
          "",
          404,
        );
      }
      return response({ data: providerView(false, "HEALTHY", options.existingProviderVersion) });
    }
    if (method === "PUT" && cleanPath === "/model-providers/ollama-launch") {
      if (options.existingProviderVersion !== undefined) {
        assert.equal(body.expectedVersion, options.existingProviderVersion);
        return response({ data: providerView(false, "HEALTHY", options.existingProviderVersion + 1) });
      }
      return response({ data: providerView(false, "NOT_CONNECTED", 0) });
    }
    if (cleanPath.endsWith("/health-check")) {
      return response({ data: providerView(false, "HEALTHY", 1) });
    }
    if (cleanPath.endsWith("regression-cases:bulk-import")) {
      return response({ data: body.cases.map((item, index) => ({ ...item, id: index + 1 })) });
    }
    if (cleanPath === "/model-evaluations") {
      const status = options.evaluationStatus ?? "PASSED";
      const totalCases = options.evaluationTotalCases ?? 3;
      return response({
        data: {
          id: 9,
          totalCases,
          passedCases: status === "PASSED" ? totalCases : totalCases - 1,
          failedCases: status === "PASSED" ? 0 : 1,
          fakeCitationDetected: status === "PASSED" ? "N" : "Y",
          redLineBreach: status === "PASSED" ? "N" : "Y",
          status,
        },
      });
    }
    if (method === "PUT" && cleanPath === "/model-capabilities/policies/knowledge.production.knowledge") {
      return response({
        data: {
          capabilityCode: "knowledge.production.knowledge",
          routeStrategy: body.routeStrategy,
          desensitizeStrategy: body.desensitizeStrategy,
          expectedSchema: body.expectedSchema ?? null,
          fallbackOrder: body.fallbackOrder,
          timeoutMs: body.timeoutMs,
          rateLimitPerMinute: body.rateLimitPerMinute,
          configured: true,
          fallbackAvailable: true,
        },
      });
    }
    if (method === "POST" && cleanPath === "/model-versions/bundles") {
      return response({
        data: {
          id: 7,
          tenantId: "t-1",
          capabilityCode: body.capabilityCode,
          promptVersion: body.promptVersion,
          toolVersion: body.toolVersion,
          modelVersion: body.modelVersion,
          status: "ACTIVE",
        },
      });
    }
    if (cleanPath.endsWith("/enable")) {
      return response({ data: providerView(true, "HEALTHY", 2) });
    }
    if (method === "GET" && cleanPath === "/engine/knowledge-production/readiness") {
      return response({
        data: {
          tenantId: "t-1",
          producer: "API_MODEL",
          capabilityCode: "knowledge.production.knowledge",
          providerCode: "ollama-launch",
          ready: true,
          modelInvocationAllowed: true,
          items: [
            { code: "LITERATURE_ROOT", ready: true, required: true },
            { code: "MODEL_POLICY", ready: true, required: true },
            { code: "VERSION_TRIPLE", ready: true, required: true },
          ],
        },
      });
    }
    throw new Error(`未模拟接口 ${method} ${path}`);
  };
}

function providerView(enabled, status, version) {
  return {
    providerCode: "ollama-launch",
    providerType: "OLLAMA",
    endpointUri: "http://127.0.0.1:11434",
    credentialConfigured: false,
    modelVersion: "medkernel-qwen25:1.5b-v1",
    enabled,
    status,
    version,
  };
}

function response(payload, setCookie = "", status = 200) {
  const text = JSON.stringify(payload);
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name) => (name.toLowerCase() === "set-cookie" ? setCookie : null) },
    text: async () => text,
  };
}
