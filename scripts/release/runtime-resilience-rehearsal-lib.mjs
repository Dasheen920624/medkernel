import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  selectLaunchAccount,
  validateLaunchCredentials,
} from "./launch-account-bootstrap-lib.mjs";
import { launchCoverageClaims } from "./stage-launch-coverage-lib.mjs";

const CAPABILITY = "knowledge.production.knowledge";
const EXPECTED_B0_EVIDENCE_COUNT = 17;
const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const API_ALLOWLIST = Object.freeze([
  ["POST", /^\/auth\/login$/u],
  ["GET", /^\/model-providers\/[a-z0-9][a-z0-9._-]{0,63}$/u],
  ["POST", /^\/model-providers\/[a-z0-9][a-z0-9._-]{0,63}\/disable$/u],
  ["POST", /^\/model-providers\/[a-z0-9][a-z0-9._-]{0,63}\/health-check$/u],
  ["POST", /^\/model-providers\/[a-z0-9][a-z0-9._-]{0,63}\/enable$/u],
  ["GET", /^\/engine\/knowledge-production\/readiness$/u],
  ["GET", /^\/engine\/domain-facades\/b0-evidence$/u],
]);

export function readRuntimeResilienceConfig(env, options = {}) {
  const readFile = options.readFile ?? ((file) => readFileSync(file, "utf8"));
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  for (const key of [
    "LAUNCH_API_BASE_URL",
    "LAUNCH_CREDENTIALS_FILE",
    "RUNTIME_RESILIENCE_PROVIDER_CODE",
  ]) {
    if (!hasText(env?.[key])) throw new Error(`缺少必填环境变量 ${key}`);
  }

  const credentialsPath = outsideRepo(
    env.LAUNCH_CREDENTIALS_FILE,
    repoRoot,
    "统一上线凭据路径",
  );
  const runtimeRoot = path.resolve(
    env.MEDKERNEL_RUNTIME_ROOT?.trim() || "/var/lib/medkernel",
  );
  const evidencePath = outsideRepo(
    env.RUNTIME_RESILIENCE_EVIDENCE_PATH?.trim() ||
      path.join(runtimeRoot, "evidence/current-launch/runtime-resilience.json"),
    repoRoot,
    "运行韧性证据路径",
  );
  const credentials = parseJson(readFile(credentialsPath), "统一上线凭据");
  validateLaunchCredentials(credentials);
  return {
    apiBaseUrl: normalizeApiBaseUrl(env.LAUNCH_API_BASE_URL),
    credentialsPath,
    evidencePath,
    providerCode: normalizeProviderCode(env.RUNTIME_RESILIENCE_PROVIDER_CODE),
    operator: selectLaunchAccount(credentials, "platform", "engine-operator"),
  };
}

export async function runRuntimeResilienceRehearsal(options) {
  const apiBaseUrl = normalizeApiBaseUrl(options?.apiBaseUrl);
  const operator = requireOperator(options?.operator);
  const providerCode = normalizeProviderCode(options?.providerCode);
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function")
    throw new Error("当前 Node.js 运行时不支持 fetch");

  const requests = [];
  const startedAt = now(options?.now);
  const login = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    method: "POST",
    path: "/auth/login",
    body: {
      tenantId: operator.tenantId,
      username: operator.username,
      password: operator.password,
    },
    label: "医疗引擎运营员登录",
  });
  assertOperatorLogin(login.data, operator);
  const session = authenticatedSession(login.headers);

  const initial = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session,
    method: "GET",
    path: `/model-providers/${providerCode}`,
    label: "读取演练前 Provider 状态",
  });
  assertProviderSnapshot(initial.data, providerCode, true, "HEALTHY");

  const disabled = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session,
    method: "POST",
    path: `/model-providers/${providerCode}/disable`,
    body: {
      capabilityCode: null,
      reason:
        "134 完整上线韧性演练：确认模型关闭期间诚实降级且 B0 核心继续运行",
      expectedVersion: initial.data.version,
      confirmedHighRisk: true,
    },
    label: "当前操作者确认停用 Provider",
  });
  assertProviderSnapshot(disabled.data, providerCode, false, "HEALTHY");
  assertVersionAdvanced(
    initial.data.version,
    disabled.data.version,
    "停用 Provider",
  );

  let disabledReadiness;
  let b0Summary;
  let degradationFailure;
  try {
    disabledReadiness = await requestReadiness({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      providerCode,
      label: "验证 Provider 停用后的模型 readiness",
    });
    assertProviderOnlyBlocker(disabledReadiness.data, providerCode);

    const b0 = await requestJson({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      method: "GET",
      path: "/engine/domain-facades/b0-evidence",
      label: "验证无模型 B0 核心门面",
    });
    b0Summary = assertB0Evidence(b0.data);
  } catch (error) {
    degradationFailure = error;
  }

  let restored;
  let restoredReadiness;
  try {
    const health = await requestJson({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      method: "POST",
      path: `/model-providers/${providerCode}/health-check`,
      label: "恢复前执行 Provider 真实探活",
    });
    assertProviderSnapshot(health.data, providerCode, false, "HEALTHY");
    assertVersionAdvanced(
      disabled.data.version,
      health.data.version,
      "Provider 真实探活",
    );

    restored = await requestJson({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      method: "POST",
      path: `/model-providers/${providerCode}/enable`,
      body: {
        capabilityCode: CAPABILITY,
        reason: "134 完整上线韧性演练：真实探活完成，恢复正式模型知识生产",
        expectedVersion: health.data.version,
        confirmedHighRisk: true,
      },
      label: "当前操作者确认恢复 Provider",
    });
    assertProviderSnapshot(restored.data, providerCode, true, "HEALTHY");
    assertVersionAdvanced(
      health.data.version,
      restored.data.version,
      "恢复 Provider",
    );

    restoredReadiness = await requestReadiness({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      providerCode,
      label: "验证 Provider 恢复后的模型 readiness",
    });
    assertFullyReady(restoredReadiness.data, providerCode);
  } catch (restoreError) {
    const prior = degradationFailure
      ? `；此前降级检查失败：${errorMessage(degradationFailure)}`
      : "";
    throw new Error(`Provider 恢复失败：${errorMessage(restoreError)}${prior}`);
  }

  if (degradationFailure) throw degradationFailure;

  return {
    status: "PASSED",
    stage: "RUNTIME_RESILIENCE_REHEARSAL",
    startedAt,
    finishedAt: now(options?.now),
    operator: {
      tenantId: operator.tenantId,
      userId: operator.userId,
      role: operator.role,
    },
    providerCode,
    disabled: {
      providerEnabled: disabled.data.enabled,
      providerStatus: disabled.data.status,
      providerVersion: disabled.data.version,
      readinessReady: disabledReadiness.data.ready,
      modelInvocationAllowed: disabledReadiness.data.modelInvocationAllowed,
      blockingRequiredItems: requiredBlockers(disabledReadiness.data),
    },
    b0: b0Summary,
    restored: {
      providerEnabled: restored.data.enabled,
      providerStatus: restored.data.status,
      providerVersion: restored.data.version,
      readinessReady: restoredReadiness.data.ready,
      modelInvocationAllowed: restoredReadiness.data.modelInvocationAllowed,
    },
    launchCoverage: launchCoverageClaims(
      [
        ["deliveryShapes", "ENGINE_CORE"],
        ["serviceCombinations", "QUALITY_IMPROVEMENT"],
        ["serviceCombinations", "COMPLIANCE_OPERATIONS"],
      ],
      now(options?.now),
    ),
    requests,
  };
}

async function requestReadiness(context) {
  const query = new URLSearchParams({
    producer: "API_MODEL",
    capabilityCode: CAPABILITY,
    providerCode: context.providerCode,
  });
  return requestJson({
    ...context,
    method: "GET",
    path: `/engine/knowledge-production/readiness?${query}`,
  });
}

function assertProviderOnlyBlocker(data, providerCode) {
  if (
    !data ||
    data.providerCode !== providerCode ||
    data.ready !== false ||
    data.modelInvocationAllowed !== false ||
    !Array.isArray(data.items)
  ) {
    throw new Error("Provider 停用后模型 readiness 未诚实阻断调用");
  }
  const blockers = requiredBlockers(data);
  if (blockers.length !== 1 || blockers[0] !== "MODEL_PROVIDER") {
    throw new Error(
      `Provider 停用后除 MODEL_PROVIDER 外仍有必需阻断项：${blockers.join(",") || "<none>"}`,
    );
  }
}

function assertFullyReady(data, providerCode) {
  if (
    !data ||
    data.providerCode !== providerCode ||
    data.ready !== true ||
    data.modelInvocationAllowed !== true ||
    !Array.isArray(data.items) ||
    data.items.some((item) => item?.required === true && item?.ready !== true)
  ) {
    throw new Error("Provider 恢复后模型知识生产 readiness 未恢复为全绿");
  }
}

function requiredBlockers(data) {
  return (data?.items ?? [])
    .filter((item) => item?.required === true && item?.ready !== true)
    .map((item) => item?.code)
    .filter(hasText)
    .sort();
}

function assertB0Evidence(data) {
  if (!Array.isArray(data) || data.length !== EXPECTED_B0_EVIDENCE_COUNT) {
    throw new Error(`B0 核心门面数量必须为 ${EXPECTED_B0_EVIDENCE_COUNT}`);
  }
  const passed = data.filter(
    (item) =>
      item?.status === "PASS" &&
      item?.b0Executable === true &&
      item?.modelRequired === false,
  );
  if (passed.length !== EXPECTED_B0_EVIDENCE_COUNT) {
    throw new Error("B0 核心门面存在失败、不可执行或错误依赖模型的项目");
  }
  return {
    evidenceCount: data.length,
    passedCount: passed.length,
    modelRequiredCount: data.filter((item) => item?.modelRequired === true)
      .length,
  };
}

function assertProviderSnapshot(data, providerCode, enabled, status) {
  if (
    !data ||
    data.providerCode !== providerCode ||
    data.enabled !== enabled ||
    data.status !== status ||
    !Number.isInteger(data.version) ||
    data.version < 0
  ) {
    throw new Error(
      `Provider 状态必须为 code=${providerCode}, enabled=${enabled}, status=${status} 且具备有效版本`,
    );
  }
}

function assertVersionAdvanced(previous, current, label) {
  if (
    !Number.isInteger(previous) ||
    !Number.isInteger(current) ||
    current <= previous
  ) {
    throw new Error(`${label} 后关系库版本没有递增`);
  }
}

function assertOperatorLogin(data, operator) {
  if (
    !data ||
    data.tenantId !== operator.tenantId ||
    data.userId !== operator.userId
  ) {
    throw new Error("运行韧性演练登录身份与统一凭据不一致");
  }
  if (
    data.mustChangePwd !== false ||
    data.mfaRequired !== false ||
    data.mfaBound !== false
  ) {
    throw new Error("运行韧性演练账号必须完成改密且默认 MFA 关闭");
  }
  if (
    !Array.isArray(data.roles) ||
    data.roles.length !== 1 ||
    data.roles[0] !== "engine-operator"
  ) {
    throw new Error("运行韧性演练必须由且仅由医疗引擎运营员执行");
  }
}

async function requestJson(options) {
  const route = options.path.split("?", 1)[0];
  assertAllowedPath(options.method, route);
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-Trace-Id": `runtime-resilience-${Date.now()}`,
  };
  if (options.session) {
    headers.Cookie = options.session.cookie;
    headers["X-XSRF-TOKEN"] = options.session.xsrf;
  }
  const response = await options.fetchImpl(
    `${options.apiBaseUrl}${options.path}`,
    {
      method: options.method,
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
    },
  );
  const raw = await response.text();
  let payload;
  try {
    payload = raw ? JSON.parse(raw) : {};
  } catch {
    throw new Error(
      `${options.label} 返回的不是合法 JSON（HTTP ${response.status}）`,
    );
  }
  options.requests.push({
    method: options.method,
    path: route,
    status: response.status,
    ok: response.ok,
    label: options.label,
  });
  if (!response.ok || payload?.success === false) {
    throw new Error(
      `${options.label} 失败（HTTP ${response.status}，${payload?.code ?? "NO_CODE"}）：` +
        `${payload?.detail ?? payload?.message ?? "无错误详情"}`,
    );
  }
  return { data: payload?.data, headers: response.headers };
}

function authenticatedSession(headers) {
  const raw = headers?.get?.("set-cookie") ?? "";
  const pairs = String(raw)
    .split(/,(?=\s*[^;,=\s]+=[^;,]*)/u)
    .map((item) => item.split(";", 1)[0]?.trim())
    .filter(Boolean);
  const access = pairs.find((item) => item.startsWith("mk_access="));
  const xsrf = pairs.find((item) => item.startsWith("XSRF-TOKEN="));
  if (!access || !xsrf)
    throw new Error("登录响应未返回 mk_access 与 XSRF-TOKEN");
  return {
    cookie: pairs.join("; "),
    xsrf: decodeURIComponent(xsrf.slice("XSRF-TOKEN=".length)),
  };
}

function assertAllowedPath(method, requestPath) {
  const allowed = API_ALLOWLIST.some(
    ([allowedMethod, pattern]) =>
      allowedMethod === method && pattern.test(requestPath),
  );
  if (!allowed)
    throw new Error(
      `运行韧性脚本拒绝未列入白名单的接口 ${method} ${requestPath}`,
    );
}

function requireOperator(operator) {
  if (!operator || typeof operator !== "object" || Array.isArray(operator)) {
    throw new Error("运行韧性操作者必须是统一凭据账号");
  }
  for (const field of ["tenantId", "userId", "username", "password", "role"]) {
    requireText(operator[field], `operator.${field}`);
  }
  if (operator.tenantId !== "t-1" || operator.role !== "engine-operator") {
    throw new Error("运行韧性演练只允许平台租户医疗引擎运营员执行");
  }
  return operator;
}

function normalizeProviderCode(value) {
  const code = requireText(value, "Provider 编码").toLowerCase();
  if (!/^[a-z0-9][a-z0-9._-]{0,63}$/u.test(code)) {
    throw new Error(
      "Provider 编码只能包含小写字母、数字、点、下划线和连字符，最长 64 位",
    );
  }
  return code;
}

function normalizeApiBaseUrl(value) {
  const normalized = requireText(value, "LAUNCH_API_BASE_URL").replace(
    /\/+$/u,
    "",
  );
  const parsed = new URL(normalized);
  const loopback = ["127.0.0.1", "localhost", "::1"].includes(parsed.hostname);
  if (
    !/^https?:$/u.test(parsed.protocol) ||
    !parsed.pathname.endsWith("/api/v1") ||
    (parsed.protocol !== "https:" && !loopback)
  ) {
    throw new Error(
      "上线 API 必须使用 HTTPS，或仅在回环地址使用 HTTP，并以 /api/v1 结尾",
    );
  }
  return normalized;
}

function outsideRepo(value, repoRoot, label) {
  const target = path.resolve(requireText(value, label));
  const relative = path.relative(repoRoot, target);
  if (
    relative === "" ||
    (!relative.startsWith("..") && !path.isAbsolute(relative))
  ) {
    throw new Error(`${label}必须位于代码仓库之外`);
  }
  return target;
}

function parseJson(raw, label) {
  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(`${label}不是合法 JSON：${error.message}`);
  }
}

function now(clock) {
  const value = clock ? clock() : new Date();
  return value instanceof Date
    ? value.toISOString()
    : new Date(value).toISOString();
}

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function requireText(value, label) {
  if (!hasText(value)) throw new Error(`${label}不能为空`);
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}
