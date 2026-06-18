import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  assertAllowedRequest,
  readPreSignoffConfig,
  redactEvidence,
  runPreSignoffRehearsal,
  writeEvidenceAtomic,
} from "./p9-pre-signoff-rehearsal-lib.mjs";

const API_BASE_URL = "http://127.0.0.1:28080/medkernel/api/v1";
const CAPABILITY_CODE = "rule.draft";
const READINESS_CODES = Object.freeze([
  "LITERATURE_ROOT",
  "DEPLOYMENT_FORM",
  "MODEL_PROVIDER",
  "REGRESSION_BASELINE",
  "MODEL_EVALUATION",
  "EGRESS_GOVERNANCE",
  "MODEL_POLICY",
  "VERSION_TRIPLE",
  "P6_ACCEPTANCE",
]);
const PRE_SIGNOFF_PASSED_CODES = new Set([
  "LITERATURE_ROOT",
  "DEPLOYMENT_FORM",
  "REGRESSION_BASELINE",
  "EGRESS_GOVERNANCE",
  "MODEL_POLICY",
]);
const PROVIDERS = Object.freeze([
  {
    providerCode: "ollama-qwen25-15b",
    modelVersion: "medkernel-qwen25:1.5b-v1",
  },
  {
    providerCode: "external-mimo-v25",
    modelVersion: "mimo-v2.5",
  },
]);

test("请求白名单只允许登录、Provider 读取/健康检查、评测创建/读取和 readiness", () => {
  const allowed = [
    ["POST", "/auth/login"],
    ["GET", "/model-providers/ollama-qwen25-15b"],
    ["POST", "/model-providers/ollama-qwen25-15b/health-check"],
    ["POST", "/model-evaluations"],
    ["GET", "/model-evaluations/runs/101"],
    [
      "GET",
      "/engine/knowledge-production/readiness?producer=API_MODEL&capabilityCode=rule.draft&providerCode=external-mimo-v25",
    ],
  ];
  for (const [method, path] of allowed) {
    assert.doesNotThrow(() => assertAllowedRequest(method, path));
  }

  const forbidden = [
    ["POST", "/model-providers/ollama-qwen25-15b/enable"],
    ["POST", "/model-providers/ollama-qwen25-15b/disable"],
    ["POST", "/model-evaluations/101/sign-off"],
    ["PUT", "/system-configs/knowledge.production.p6-independent-acceptance"],
    ["POST", "/engine/knowledge/candidates"],
    ["GET", "/model-evaluations/regression-cases"],
  ];
  for (const [method, path] of forbidden) {
    assert.throws(
      () => assertAllowedRequest(method, path),
      /预演请求不在安全白名单/,
    );
  }
});

test("配置必须显式提供两个不重复 Provider 且不得内嵌 URL 凭据", () => {
  const env = {
    P9_PRE_SIGNOFF_API_BASE_URL: API_BASE_URL,
    P9_PRE_SIGNOFF_CREDENTIALS_FILE: "/tmp/controlled.json",
    P9_PRE_SIGNOFF_PROVIDERS_JSON: JSON.stringify(PROVIDERS),
    P9_PRE_SIGNOFF_CAPABILITY_CODE: CAPABILITY_CODE,
    P9_PRE_SIGNOFF_OUTPUT_PATH: "/tmp/evidence.json",
  };
  const config = readPreSignoffConfig(env, {
    readFile: () =>
      JSON.stringify({
        tenantId: "t-1",
        username: "governor",
        password: "controlled-password",
      }),
  });
  assert.equal(config.apiBaseUrl, API_BASE_URL);
  assert.deepEqual(config.providers, PROVIDERS);

  assert.throws(
    () =>
      readPreSignoffConfig(
        {
          ...env,
          P9_PRE_SIGNOFF_PROVIDERS_JSON: JSON.stringify([
            PROVIDERS[0],
            PROVIDERS[0],
          ]),
        },
        {
          readFile: () =>
            JSON.stringify({
              tenantId: "t-1",
              username: "governor",
              password: "controlled-password",
            }),
        },
      ),
    /providerCode 不得重复/,
  );
  assert.throws(
    () =>
      readPreSignoffConfig(
        {
          ...env,
          P9_PRE_SIGNOFF_API_BASE_URL:
            "https://user:password@example.test/api/v1",
        },
        {
          readFile: () =>
            JSON.stringify({
              tenantId: "t-1",
              username: "governor",
              password: "controlled-password",
            }),
        },
      ),
    /不得包含内嵌凭据/,
  );
  assert.throws(
    () =>
      readPreSignoffConfig(
        {
          ...env,
          P9_PRE_SIGNOFF_PROVIDERS_JSON: JSON.stringify([
            { ...PROVIDERS[0], providerCode: "../enable" },
            PROVIDERS[1],
          ]),
        },
        {
          readFile: () =>
            JSON.stringify({
              tenantId: "t-1",
              username: "governor",
              password: "controlled-password",
            }),
        },
      ),
    /providerCode 格式非法/,
  );
});

test("真实预演仅生成新评测并保持 Provider 停用、P6=false、运行待真人签署", async () => {
  const calls = [];
  let nextRunId = 100;
  const providerState = new Map(
    PROVIDERS.map((provider) => [
      provider.providerCode,
      {
        providerCode: provider.providerCode,
        providerType: provider.providerCode.startsWith("external")
          ? "EXTERNAL_OPENAI"
          : "LOCAL_OLLAMA",
        credentialConfigured: true,
        modelVersion: provider.modelVersion,
        enabled: false,
        status: "HEALTHY",
        version: 3,
      },
    ]),
  );
  const runById = new Map();
  const fetchImpl = async (url, init = {}) => {
    const target = new URL(url);
    const method = (init.method ?? "GET").toUpperCase();
    const path = `${target.pathname.replace("/medkernel/api/v1", "")}${target.search}`;
    const headers = new Headers(init.headers);
    calls.push({
      method,
      path,
      cookie: headers.get("cookie"),
      xsrf: headers.get("x-xsrf-token"),
    });

    if (path === "/auth/login") {
      return jsonResponse(
        { code: "OK", data: { tenantId: "t-1" } },
        {
          headers: {
            "set-cookie":
              "mk_access=secret-cookie; Path=/; HttpOnly, XSRF-TOKEN=secret-xsrf; Path=/",
          },
        },
      );
    }
    const providerMatch = path.match(
      /^\/model-providers\/([^/?]+)(\/health-check)?$/,
    );
    if (providerMatch) {
      const provider = providerState.get(decodeURIComponent(providerMatch[1]));
      if (providerMatch[2]) {
        provider.version += 1;
      }
      return jsonResponse({ code: "OK", data: provider });
    }
    if (path === "/model-evaluations" && method === "POST") {
      const request = JSON.parse(init.body);
      const id = ++nextRunId;
      runById.set(id, request);
      return jsonResponse({
        code: "OK",
        data: {
          id,
          ...request,
          totalCases: 1,
          passedCases: 1,
          failedCases: 0,
          fakeCitationDetected: "N",
          redLineBreach: "N",
          hallucinationDetected: "N",
          status: "PENDING_REVIEW",
        },
      });
    }
    const runMatch = path.match(/^\/model-evaluations\/runs\/(\d+)$/);
    if (runMatch) {
      const id = Number(runMatch[1]);
      const request = runById.get(id);
      return jsonResponse({
        code: "OK",
        data: {
          run: {
            runId: id,
            ...request,
            promptVersion: "prompt-v1",
            toolVersion: "tool-v1",
            totalCases: 1,
            passedCases: 1,
            failedCases: 0,
            fakeCitationDetected: false,
            redLineBreach: false,
            hallucinationDetected: false,
            status: "PENDING_REVIEW",
            reviewer: null,
            signedAt: null,
          },
          cases: [
            {
              evidenceId: id + 1000,
              regressionCaseId: 1,
              caseVersion: "who-chb-2024-v1",
              caseInput: "真实医学输入，不得写入证据",
              expectedPhrase: "精确短语",
              sourceReference: "WHO IRIS 10665/376353",
              outputContent: "真实模型输出，不得写入证据",
              sourceCitations: "WHO IRIS 10665/376353",
              expectedPhraseHit: true,
              citationRequired: true,
              citationVerified: true,
              redLineCase: true,
              redLineBreach: false,
              passed: true,
              failureReasons: [],
            },
          ],
          evidenceComplete: true,
          baselineCurrent: true,
          reviewable: true,
          reviewBlockReason: null,
        },
      });
    }
    if (path.startsWith("/engine/knowledge-production/readiness?")) {
      const providerCode = target.searchParams.get("providerCode");
      return jsonResponse({
        code: "OK",
        data: {
          producer: "API_MODEL",
          capabilityCode: CAPABILITY_CODE,
          providerCode,
          ready: false,
          modelInvocationAllowed: false,
          items: preSignoffReadinessItems(),
        },
      });
    }
    throw new Error(`未预期请求：${method} ${path}`);
  };

  const evidence = await runPreSignoffRehearsal({
    apiBaseUrl: API_BASE_URL,
    credentials: {
      tenantId: "t-1",
      username: "governor",
      password: "controlled-password",
    },
    providers: PROVIDERS,
    capabilityCode: CAPABILITY_CODE,
    fetchImpl,
    now: sequenceClock(),
  });

  assert.equal(evidence.status, "PASSED", evidence.failures.join("；"));
  assert.equal(evidence.containsCredentials, false);
  assert.equal(evidence.containsPatientData, false);
  assert.equal(evidence.automatedExpertSignOff, false);
  assert.equal(evidence.providerEnableAttempted, false);
  assert.equal(evidence.p6MutationAttempted, false);
  assert.equal(evidence.providers.length, 2);
  assert.ok(
    evidence.providers.every(
      (provider) =>
        provider.before.enabled === false &&
        provider.after.enabled === false &&
        provider.evaluation.status === "PENDING_REVIEW" &&
        provider.evaluation.reviewable === true,
    ),
  );
  assert.ok(
    evidence.providers.every(
      (provider) =>
        provider.evaluation.cases[0].inputSha256.length === 64 &&
        provider.evaluation.cases[0].outputSha256.length === 64,
    ),
  );
  const serialized = JSON.stringify(evidence);
  assert.doesNotMatch(
    serialized,
    /真实医学输入|真实模型输出|controlled-password/,
  );
  assert.equal(calls.filter((call) => call.method !== "GET").length, 5);
  assert.ok(
    calls
      .filter((call) => call.method === "POST" && call.path !== "/auth/login")
      .every((call) => call.xsrf === "secret-xsrf"),
  );
  assert.ok(
    evidence.requests.every(
      (request) =>
        !request.path.includes("/enable") &&
        !request.path.includes("/disable") &&
        !request.path.includes("/sign-off"),
    ),
  );
});

test("Provider 已启用、评测不可复核或 P6 已放行时预演必须阻断", async () => {
  const fetchImpl = fixtureFetch({
    providerEnabled: true,
    reviewable: false,
    p6Ready: true,
  });
  const evidence = await runPreSignoffRehearsal({
    apiBaseUrl: API_BASE_URL,
    credentials: {
      tenantId: "t-1",
      username: "governor",
      password: "controlled-password",
    },
    providers: [PROVIDERS[0]],
    capabilityCode: CAPABILITY_CODE,
    fetchImpl,
  });

  assert.equal(evidence.status, "BLOCKED");
  assert.ok(evidence.failures.some((item) => item.includes("预演前已启用")));
  assert.ok(
    evidence.failures.some((item) => item.includes("不可进入真人复核")),
  );
  assert.ok(evidence.failures.some((item) => item.includes("P6 已提前放行")));
});

test("启停字段缺失、医学红线命中或九闸集合不完整时必须阻断", async () => {
  const evidence = await runPreSignoffRehearsal({
    apiBaseUrl: API_BASE_URL,
    credentials: {
      tenantId: "t-1",
      username: "governor",
      password: "controlled-password",
    },
    providers: [PROVIDERS[0]],
    capabilityCode: CAPABILITY_CODE,
    fetchImpl: fixtureFetch({
      omitEnabled: true,
      redLineBreach: true,
      incompleteReadiness: true,
    }),
  });

  assert.equal(evidence.status, "BLOCKED");
  assert.ok(
    evidence.failures.some((item) => item.includes("缺少明确启停状态")),
  );
  assert.ok(
    evidence.failures.some((item) => item.includes("医学安全裁决未通过")),
  );
  assert.ok(evidence.failures.some((item) => item.includes("九闸集合不完整")));
});

test("兼容 V151 无 Provider GET 接口时用健康检查保留的启停位与 readiness 双重取证", async () => {
  const baseFetch = fixtureFetch();
  const fetchImpl = async (url, init = {}) => {
    const target = new URL(url);
    const method = (init.method ?? "GET").toUpperCase();
    if (method === "GET" && target.pathname.includes("/model-providers/")) {
      return jsonResponse({ code: "METHOD_NOT_ALLOWED" }, { status: 405 });
    }
    const response = await baseFetch(url, init);
    if (method === "POST" && target.pathname.endsWith("/health-check")) {
      const payload = await response.json();
      delete payload.data.enabled;
      payload.data.enabledFlag = "N";
      return jsonResponse(payload);
    }
    if (
      method === "GET" &&
      target.pathname.endsWith("/engine/knowledge-production/readiness")
    ) {
      const payload = await response.json();
      payload.data.providerCode = null;
      return jsonResponse(payload);
    }
    return response;
  };

  const evidence = await runPreSignoffRehearsal({
    apiBaseUrl: API_BASE_URL,
    credentials: {
      tenantId: "t-1",
      username: "governor",
      password: "controlled-password",
    },
    providers: [PROVIDERS[0]],
    capabilityCode: CAPABILITY_CODE,
    fetchImpl,
  });

  assert.equal(evidence.status, "PASSED", evidence.failures.join("；"));
  assert.equal(
    evidence.providers[0].snapshotMode,
    "V151_HEALTH_CHECK_COMPATIBILITY",
  );
  assert.equal(evidence.providers[0].before.enabled, false);
  assert.equal(evidence.providers[0].after.enabled, false);
  assert.ok(
    evidence.requests.some(
      (request) =>
        request.method === "GET" &&
        request.path.includes("/model-providers/") &&
        request.status === 405,
    ),
  );
});

test("递归脱敏且原子证据文件权限为 0600", () => {
  const redacted = redactEvidence({
    password: "password-value",
    nested: {
      cookie: "cookie-value",
      credentialRef: "credential-value",
      mfaSecret: "mfa-value",
      safe: "retained",
    },
  });
  assert.equal(redacted.password, "[REDACTED]");
  assert.equal(redacted.nested.cookie, "[REDACTED]");
  assert.equal(redacted.nested.credentialRef, "[REDACTED]");
  assert.equal(redacted.nested.mfaSecret, "[REDACTED]");
  assert.equal(redacted.nested.safe, "retained");

  const dir = mkdtempSync(join(tmpdir(), "p9-pre-signoff-"));
  try {
    const output = join(dir, "evidence.json");
    writeEvidenceAtomic(output, {
      status: "PASSED",
      containsCredentials: false,
      containsPatientData: false,
    });
    assert.equal(statSync(output).mode & 0o777, 0o600);
    assert.equal(JSON.parse(readFileSync(output, "utf8")).status, "PASSED");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

function fixtureFetch(options = {}) {
  let runId = 200;
  return async (url, init = {}) => {
    const target = new URL(url);
    const method = (init.method ?? "GET").toUpperCase();
    const path = `${target.pathname.replace("/medkernel/api/v1", "")}${target.search}`;
    if (path === "/auth/login") {
      return jsonResponse(
        { code: "OK", data: {} },
        {
          headers: {
            "set-cookie":
              "mk_access=secret-cookie; Path=/; HttpOnly, XSRF-TOKEN=secret-xsrf; Path=/",
          },
        },
      );
    }
    if (path.includes("/model-providers/")) {
      const enabledFields = options.omitEnabled
        ? {}
        : { enabled: options.providerEnabled ?? false };
      return jsonResponse({
        code: "OK",
        data: {
          providerCode: PROVIDERS[0].providerCode,
          providerType: "LOCAL_OLLAMA",
          credentialConfigured: false,
          modelVersion: PROVIDERS[0].modelVersion,
          ...enabledFields,
          status: "HEALTHY",
          version: 3,
        },
      });
    }
    if (path === "/model-evaluations" && method === "POST") {
      return jsonResponse({
        code: "OK",
        data: {
          id: ++runId,
          providerCode: PROVIDERS[0].providerCode,
          modelVersion: PROVIDERS[0].modelVersion,
          capabilityCode: CAPABILITY_CODE,
          status: "PENDING_REVIEW",
        },
      });
    }
    if (path.startsWith("/model-evaluations/runs/")) {
      return jsonResponse({
        code: "OK",
        data: {
          run: {
            runId,
            providerCode: PROVIDERS[0].providerCode,
            modelVersion: PROVIDERS[0].modelVersion,
            capabilityCode: CAPABILITY_CODE,
            totalCases: 1,
            passedCases: 1,
            failedCases: 0,
            fakeCitationDetected: false,
            redLineBreach: options.redLineBreach ?? false,
            hallucinationDetected: false,
            status: "PENDING_REVIEW",
            reviewer: null,
            signedAt: null,
          },
          cases: [
            {
              evidenceId: 1,
              regressionCaseId: 1,
              caseVersion: "v1",
              caseInput: "input",
              expectedPhrase: "expected",
              sourceReference: "source",
              outputContent: "output",
              sourceCitations: "source",
              expectedPhraseHit: true,
              citationRequired: true,
              citationVerified: true,
              redLineCase: true,
              redLineBreach: options.redLineBreach ?? false,
              passed: !(options.redLineBreach ?? false),
              failureReasons: [],
            },
          ],
          evidenceComplete: true,
          baselineCurrent: true,
          reviewable: options.reviewable ?? true,
          reviewBlockReason: options.reviewable === false ? "证据不完整" : null,
        },
      });
    }
    if (path.startsWith("/engine/knowledge-production/readiness?")) {
      return jsonResponse({
        code: "OK",
        data: {
          producer: "API_MODEL",
          capabilityCode: CAPABILITY_CODE,
          providerCode: PROVIDERS[0].providerCode,
          ready: options.p6Ready ?? false,
          modelInvocationAllowed: options.p6Ready ?? false,
          items: options.incompleteReadiness
            ? preSignoffReadinessItems(options.p6Ready).slice(0, -1)
            : preSignoffReadinessItems(options.p6Ready),
        },
      });
    }
    throw new Error(`未预期请求：${method} ${path}`);
  };
}

function jsonResponse(body, options = {}) {
  return new Response(JSON.stringify(body), {
    status: options.status ?? 200,
    headers: {
      "content-type": "application/json",
      ...(options.headers ?? {}),
    },
  });
}

function sequenceClock() {
  const values = [
    new Date("2026-06-18T12:00:00Z"),
    new Date("2026-06-18T12:01:00Z"),
  ];
  return () => values.shift() ?? new Date("2026-06-18T12:01:00Z");
}

function preSignoffReadinessItems(p6Ready = false) {
  return READINESS_CODES.map((code) => {
    const ready =
      code === "P6_ACCEPTANCE" ? p6Ready : PRE_SIGNOFF_PASSED_CODES.has(code);
    return {
      code,
      required: true,
      ready,
      evidence:
        code === "P6_ACCEPTANCE"
          ? `knowledge.production.p6-independent-acceptance=${ready}`
          : `${code}=${ready}`,
    };
  });
}
