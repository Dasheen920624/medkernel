import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, statSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  EXPECTED_READINESS_CODES,
  assessKnowledgeReadiness,
  assessSourceReadiness,
  readPreflightConfig,
  redactEvidence,
  runReadinessPreflight,
  writeEvidenceAtomic,
} from "./p9-t98-readiness-preflight-lib.mjs";

const EXPECTED_CONTEXT = Object.freeze({
  producer: "API_MODEL",
  providerCode: "external-mimo-v25",
  capabilityCode: "rule.draft",
});

function greenItems() {
  return EXPECTED_READINESS_CODES.map((code) => ({
    code,
    ready: true,
    required: true,
    message: `${code} 已通过`,
    evidence: `${code}-evidence`,
  }));
}

function greenReadiness(overrides = {}) {
  return {
    tenantId: "t-1",
    producer: EXPECTED_CONTEXT.producer,
    capabilityCode: EXPECTED_CONTEXT.capabilityCode,
    providerCode: EXPECTED_CONTEXT.providerCode,
    deploymentForm: "PRODUCTION_CENTER",
    ready: true,
    modelInvocationAllowed: true,
    items: greenItems(),
    ...overrides,
  };
}

function effectiveSource(overrides = {}) {
  return {
    sourceCode: "WHO-CHB-GUIDELINE-2024",
    enabledFlag: "Y",
    approvedBy: "independent-governor",
    approvedAt: "2026-06-18T00:00:00Z",
    licensePolicy: "PERMITTED",
    robotsPolicy: "ALLOW_FETCH",
    ...overrides,
  };
}

test("9 项 readiness 精确全绿且上下文一致时通过", () => {
  const result = assessKnowledgeReadiness(greenReadiness(), EXPECTED_CONTEXT);

  assert.equal(result.ready, true);
  assert.deepEqual(result.failures, []);
  assert.deepEqual(
    result.items.map((item) => item.code),
    EXPECTED_READINESS_CODES,
  );
});

test("readiness 缺项、重复、阻断或上下文漂移时诚实阻断", () => {
  const cases = [
    {
      name: "缺少 P6 闸",
      response: greenReadiness({ items: greenItems().slice(0, -1) }),
      expected: "readiness 闸门集合不完整",
    },
    {
      name: "重复 provider 闸",
      response: greenReadiness({
        items: [...greenItems(), greenItems()[2]],
      }),
      expected: "readiness 闸门存在重复",
    },
    {
      name: "单项阻断",
      response: greenReadiness({
        items: greenItems().map((item) =>
          item.code === "MODEL_EVALUATION"
            ? { ...item, ready: false, message: "医学评测未签署" }
            : item,
        ),
      }),
      expected: "MODEL_EVALUATION 未通过",
    },
    {
      name: "聚合标志不一致",
      response: greenReadiness({ ready: false }),
      expected: "readiness 聚合状态不是全绿",
    },
    {
      name: "生产器漂移",
      response: greenReadiness({ producer: "LOCAL_MODEL" }),
      expected: "producer 与请求不一致",
    },
    {
      name: "provider 漂移",
      response: greenReadiness({ providerCode: "other-provider" }),
      expected: "providerCode 与请求不一致",
    },
    {
      name: "能力漂移",
      response: greenReadiness({ capabilityCode: "knowledge.extract" }),
      expected: "capabilityCode 与请求不一致",
    },
  ];

  for (const fixture of cases) {
    const result = assessKnowledgeReadiness(fixture.response, EXPECTED_CONTEXT);
    assert.equal(result.ready, false, fixture.name);
    assert.ok(
      result.failures.some((failure) => failure.includes(fixture.expected)),
      `${fixture.name}: ${result.failures.join("；")}`,
    );
  }
});

test("受控来源必须启用、独立审批且许可与 robots 均允许", () => {
  const sourceCode = "WHO-CHB-GUIDELINE-2024";
  const passed = assessSourceReadiness(
    { items: [effectiveSource()] },
    sourceCode,
  );
  assert.equal(passed.ready, true);
  assert.deepEqual(passed.failures, []);

  const blockedSources = [
    effectiveSource({ enabledFlag: "N" }),
    effectiveSource({ approvedBy: "" }),
    effectiveSource({ approvedAt: null }),
    effectiveSource({ licensePolicy: "RESTRICTED" }),
    effectiveSource({ robotsPolicy: "DISALLOW_FETCH" }),
  ];
  for (const source of blockedSources) {
    const result = assessSourceReadiness({ items: [source] }, sourceCode);
    assert.equal(result.ready, false);
    assert.ok(result.failures.length > 0);
  }

  const missing = assessSourceReadiness({ items: [] }, sourceCode);
  assert.equal(missing.ready, false);
  assert.ok(
    missing.failures.some((failure) => failure.includes("未找到受控来源")),
  );
});

test("证据递归脱敏认证、凭据、签名与 MFA 字段", () => {
  const redacted = redactEvidence({
    password: "password-value",
    nested: {
      accessToken: "token-value",
      cookie: "cookie-value",
      credentialRef: "credential-value",
      signature: "signature-value",
      recoveryCode: "recovery-value",
      mfaSecret: "secret-value",
      otpCode: "otp-value",
      safe: "retained",
    },
  });

  assert.equal(redacted.password, "[REDACTED]");
  assert.equal(redacted.nested.accessToken, "[REDACTED]");
  assert.equal(redacted.nested.cookie, "[REDACTED]");
  assert.equal(redacted.nested.credentialRef, "[REDACTED]");
  assert.equal(redacted.nested.signature, "[REDACTED]");
  assert.equal(redacted.nested.recoveryCode, "[REDACTED]");
  assert.equal(redacted.nested.mfaSecret, "[REDACTED]");
  assert.equal(redacted.nested.otpCode, "[REDACTED]");
  assert.equal(redacted.nested.safe, "retained");
});

test("网络预检除登录外只执行 GET 且不在证据中保留 Cookie", async () => {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    const target = new URL(url);
    const method = init.method ?? "GET";
    calls.push({
      method,
      path: `${target.pathname}${target.search}`,
      cookie: new Headers(init.headers).get("cookie"),
    });
    if (target.pathname.endsWith("/auth/login")) {
      return jsonResponse(
        {
          code: "OK",
          data: {
            userId: "platform-owner-134",
            tenantId: "t-1",
            roles: ["system-superadmin"],
            mfaRequired: true,
            mfaBound: true,
          },
        },
        {
          headers: {
            "set-cookie":
              "mk_access=secret-cookie; Path=/; HttpOnly, XSRF-TOKEN=secret-xsrf; Path=/",
          },
        },
      );
    }
    if (target.pathname.endsWith("/security/me")) {
      return jsonResponse({
        code: "OK",
        data: {
          userId: "platform-owner-134",
          username: "platform-owner-134",
          roles: [{ code: "system-superadmin" }],
          dataScope: { tenantId: "t-1" },
          mfaRequired: true,
          mfaBound: true,
        },
      });
    }
    if (target.pathname.endsWith("/actuator/health/readiness")) {
      return jsonResponse({ status: "UP" });
    }
    if (target.pathname.endsWith("/knowledge-production/readiness")) {
      return jsonResponse({ code: "OK", data: greenReadiness() });
    }
    if (target.pathname.endsWith("/knowledge/acquisition/sources")) {
      return jsonResponse({
        code: "OK",
        data: {
          items: [effectiveSource()],
          page: 1,
          size: 100,
          total: 1,
          hasNext: false,
          totalEstimated: false,
        },
      });
    }
    throw new Error(`未预期请求：${url}`);
  };

  const result = await runReadinessPreflight({
    fetchImpl,
    apiBaseUrl: "http://127.0.0.1:18080/medkernel/api/v1",
    healthUrl: "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
    credentials: {
      username: "platform-owner-134",
      password: "controlled-password",
      tenantId: "t-1",
    },
    ...EXPECTED_CONTEXT,
    sourceCode: "WHO-CHB-GUIDELINE-2024",
    now: () => new Date("2026-06-18T12:00:00Z"),
  });

  assert.equal(result.status, "PASSED");
  assert.deepEqual(
    calls.map((call) => call.method),
    ["POST", "GET", "GET", "GET", "GET"],
  );
  assert.equal(calls[0].cookie, null);
  assert.ok(calls[1].cookie?.includes("mk_access="));
  assert.equal(calls[2].cookie, null);
  assert.ok(calls[3].cookie?.includes("mk_access="));
  assert.ok(calls[4].cookie?.includes("mk_access="));
  assert.equal(JSON.stringify(result).includes("secret-cookie"), false);
  assert.equal(JSON.stringify(result).includes("controlled-password"), false);
  assert.deepEqual(result.failures, []);
});

test("JSON 异常返回 BLOCKED 且保留安全请求账本", async () => {
  const fetchImpl = async (url) => {
    const target = new URL(url);
    if (target.pathname.endsWith("/auth/login")) {
      return jsonResponse(
        { code: "OK", data: { userId: "owner", tenantId: "t-1", roles: [] } },
        { headers: { "set-cookie": "mk_access=secret-cookie; Path=/" } },
      );
    }
    if (target.pathname.endsWith("/security/me")) {
      return jsonResponse({
        code: "OK",
        data: {
          userId: "owner",
          roles: [],
          dataScope: { tenantId: "t-1" },
        },
      });
    }
    if (target.pathname.endsWith("/actuator/health/readiness")) {
      return jsonResponse({ status: "UP" });
    }
    return new Response("not-json", {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  const result = await runReadinessPreflight({
    fetchImpl,
    apiBaseUrl: "http://127.0.0.1:18080/medkernel/api/v1",
    healthUrl: "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
    credentials: {
      username: "owner",
      password: "controlled-password",
      tenantId: "t-1",
    },
    ...EXPECTED_CONTEXT,
    sourceCode: "WHO-CHB-GUIDELINE-2024",
    now: () => new Date("2026-06-18T12:00:00Z"),
  });

  assert.equal(result.status, "BLOCKED");
  assert.ok(
    result.failures.some((failure) => failure.includes("响应不是合法 JSON")),
  );
  assert.ok(
    result.requests.every(
      (request) =>
        request.method === "GET" ||
        (request.method === "POST" && request.path.endsWith("/auth/login")),
    ),
  );
  assert.equal(JSON.stringify(result).includes("secret-cookie"), false);
});

test("HTTP 非 2xx 返回 BLOCKED 且不泄漏登录凭据", async () => {
  const result = await runReadinessPreflight({
    fetchImpl: async () =>
      new Response("upstream unavailable", {
        status: 503,
        headers: { "content-type": "text/plain" },
      }),
    apiBaseUrl: "http://127.0.0.1:18080/medkernel/api/v1",
    healthUrl: "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
    credentials: {
      username: "owner",
      password: "controlled-password",
      tenantId: "t-1",
    },
    ...EXPECTED_CONTEXT,
    sourceCode: "WHO-CHB-GUIDELINE-2024",
    now: () => new Date("2026-06-18T12:00:00Z"),
  });

  assert.equal(result.status, "BLOCKED");
  assert.deepEqual(result.requests, [
    {
      method: "POST",
      path: "/medkernel/api/v1/auth/login",
      status: 503,
    },
  ]);
  assert.ok(
    result.failures.some((failure) => failure.includes("登录返回 HTTP 503")),
  );
  assert.equal(JSON.stringify(result).includes("controlled-password"), false);
  assert.equal(JSON.stringify(result).includes("upstream unavailable"), false);
});

test("CLI 配置要求显式目标与受控凭据文件", () => {
  const env = {
    P9_T98_API_BASE_URL: "http://127.0.0.1:18080/medkernel/api/v1",
    P9_T98_HEALTH_URL:
      "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
    P9_T98_CREDENTIALS_FILE: "/controlled/p9.json",
    P9_T98_PROVIDER_CODE: "external-mimo-v25",
    P9_T98_SOURCE_CODE: "WHO-CHB-GUIDELINE-2024",
    P9_T98_OUTPUT_PATH: "/tmp/p9-t98.json",
  };
  const config = readPreflightConfig(env, {
    readFile: (path) => {
      assert.equal(path, "/controlled/p9.json");
      return JSON.stringify({
        username: "owner",
        password: "controlled-password",
        tenantId: "t-1",
      });
    },
  });

  assert.equal(config.producer, "API_MODEL");
  assert.equal(config.capabilityCode, "rule.draft");
  assert.equal(config.providerCode, "external-mimo-v25");
  assert.equal(config.outputPath, "/tmp/p9-t98.json");
  assert.equal(config.credentials.username, "owner");

  assert.throws(
    () =>
      readPreflightConfig(
        { ...env, P9_T98_PROVIDER_CODE: "" },
        { readFile: () => "{}" },
      ),
    /缺少必填环境变量 P9_T98_PROVIDER_CODE/,
  );
});

test("预检目标 URL 拒绝内嵌凭据、查询串与片段", () => {
  const baseEnv = {
    P9_T98_API_BASE_URL: "http://127.0.0.1:18080/medkernel/api/v1",
    P9_T98_HEALTH_URL:
      "http://127.0.0.1:18080/medkernel/actuator/health/readiness",
    P9_T98_CREDENTIALS_FILE: "/controlled/p9.json",
    P9_T98_PROVIDER_CODE: "external-mimo-v25",
    P9_T98_SOURCE_CODE: "WHO-CHB-GUIDELINE-2024",
    P9_T98_OUTPUT_PATH: "/tmp/p9-t98.json",
  };
  const options = {
    readFile: () =>
      JSON.stringify({
        username: "owner",
        password: "controlled-password",
        tenantId: "t-1",
      }),
  };

  assert.throws(
    () =>
      readPreflightConfig(
        {
          ...baseEnv,
          P9_T98_API_BASE_URL:
            "http://owner:secret@127.0.0.1:18080/medkernel/api/v1",
        },
        options,
      ),
    /P9_T98_API_BASE_URL 不得包含内嵌凭据、查询串或片段/,
  );
  assert.throws(
    () =>
      readPreflightConfig(
        {
          ...baseEnv,
          P9_T98_HEALTH_URL:
            "http://127.0.0.1:18080/medkernel/actuator/health/readiness?token=secret",
        },
        options,
      ),
    /P9_T98_HEALTH_URL 不得包含内嵌凭据、查询串或片段/,
  );
  assert.throws(
    () =>
      readPreflightConfig(
        {
          ...baseEnv,
          P9_T98_HEALTH_URL:
            "http://127.0.0.1:18080/medkernel/actuator/health/readiness#secret",
        },
        options,
      ),
    /P9_T98_HEALTH_URL 不得包含内嵌凭据、查询串或片段/,
  );
});

test("证据通过同目录临时文件原子落盘并限制权限", () => {
  const dir = mkdtempSync(join(tmpdir(), "medkernel-p9-t98-"));
  try {
    const outputPath = join(dir, "preflight.json");
    writeEvidenceAtomic(outputPath, {
      status: "BLOCKED",
      failures: ["P6 未放行"],
    });

    const content = JSON.parse(readFileSync(outputPath, "utf8"));
    assert.equal(content.status, "BLOCKED");
    assert.deepEqual(content.failures, ["P6 未放行"]);
    assert.equal(statSync(outputPath).mode & 0o777, 0o600);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

function jsonResponse(body, init = {}) {
  const headers = new Headers(init.headers);
  headers.set("content-type", "application/json");
  return new Response(JSON.stringify(body), {
    ...init,
    headers,
  });
}
