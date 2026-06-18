import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname } from "node:path";

const SENSITIVE_KEY =
  /password|cookie|token|secret|credential|recovery|mfa|otp|totp|signature/i;
const SAFETY_DECLARATION_KEYS = new Set([
  "containsCredentials",
  "containsPatientData",
]);
const REQUIRED_ENV_KEYS = Object.freeze([
  "P9_PRE_SIGNOFF_API_BASE_URL",
  "P9_PRE_SIGNOFF_CREDENTIALS_FILE",
  "P9_PRE_SIGNOFF_PROVIDERS_JSON",
  "P9_PRE_SIGNOFF_CAPABILITY_CODE",
  "P9_PRE_SIGNOFF_OUTPUT_PATH",
]);
const PROVIDER_CODE = /^[A-Za-z0-9][A-Za-z0-9._-]{1,127}$/;
const EXPECTED_READINESS_CODES = Object.freeze([
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
const EXPECTED_READINESS_SET = new Set(EXPECTED_READINESS_CODES);
const PRE_SIGNOFF_PASSED_CODES = new Set([
  "LITERATURE_ROOT",
  "DEPLOYMENT_FORM",
  "REGRESSION_BASELINE",
  "EGRESS_GOVERNANCE",
  "MODEL_POLICY",
]);
const ALLOWED_REQUESTS = Object.freeze([
  ["POST", /^\/auth\/login$/],
  ["GET", /^\/model-providers\/[^/?]+$/],
  ["POST", /^\/model-providers\/[^/?]+\/health-check$/],
  ["POST", /^\/model-evaluations$/],
  ["GET", /^\/model-evaluations\/runs\/\d+$/],
  ["GET", /^\/engine\/knowledge-production\/readiness\?.+$/],
]);

export function assertAllowedRequest(method, path) {
  const normalizedMethod = requireText(method, "method").toUpperCase();
  const normalizedPath = requireRelativeApiPath(path);
  const matched = ALLOWED_REQUESTS.some(
    ([allowedMethod, pattern]) =>
      normalizedMethod === allowedMethod && pattern.test(normalizedPath),
  );
  if (!matched) {
    throw new Error(
      `预演请求不在安全白名单：${normalizedMethod} ${normalizedPath}`,
    );
  }
  if (normalizedPath.startsWith("/engine/knowledge-production/readiness?")) {
    validateReadinessQuery(normalizedPath);
  }
}

export function readPreSignoffConfig(env, options = {}) {
  for (const key of REQUIRED_ENV_KEYS) {
    if (!hasText(env?.[key])) {
      throw new Error(`缺少必填环境变量 ${key}`);
    }
  }
  const readFile = options.readFile ?? ((path) => readFileSync(path, "utf8"));
  let rawCredentials;
  try {
    rawCredentials = JSON.parse(readFile(env.P9_PRE_SIGNOFF_CREDENTIALS_FILE));
  } catch {
    throw new Error("P9_PRE_SIGNOFF_CREDENTIALS_FILE 不是合法 JSON");
  }
  let rawProviders;
  try {
    rawProviders = JSON.parse(env.P9_PRE_SIGNOFF_PROVIDERS_JSON);
  } catch {
    throw new Error("P9_PRE_SIGNOFF_PROVIDERS_JSON 不是合法 JSON");
  }

  return {
    apiBaseUrl: normalizeBaseUrl(env.P9_PRE_SIGNOFF_API_BASE_URL),
    credentials: requireCredentials(rawCredentials),
    providers: requireProviders(rawProviders, { exactCount: 2 }),
    capabilityCode: requireText(
      env.P9_PRE_SIGNOFF_CAPABILITY_CODE,
      "P9_PRE_SIGNOFF_CAPABILITY_CODE",
    ),
    producer: hasText(env.P9_PRE_SIGNOFF_PRODUCER)
      ? env.P9_PRE_SIGNOFF_PRODUCER.trim()
      : "API_MODEL",
    outputPath: env.P9_PRE_SIGNOFF_OUTPUT_PATH.trim(),
  };
}

export function redactEvidence(value) {
  if (Array.isArray(value)) {
    return value.map(redactEvidence);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      SENSITIVE_KEY.test(key) && !SAFETY_DECLARATION_KEYS.has(key)
        ? "[REDACTED]"
        : redactEvidence(item),
    ]),
  );
}

export function writeEvidenceAtomic(outputPath, evidence) {
  const normalizedOutput = requireText(outputPath, "outputPath");
  const parent = dirname(normalizedOutput);
  const tempPath = `${normalizedOutput}.${process.pid}.tmp`;
  mkdirSync(parent, { recursive: true });
  try {
    writeFileSync(tempPath, `${JSON.stringify(evidence, null, 2)}\n`, {
      encoding: "utf8",
      mode: 0o600,
    });
    renameSync(tempPath, normalizedOutput);
  } finally {
    if (existsSync(tempPath)) {
      rmSync(tempPath, { force: true });
    }
  }
}

export async function runPreSignoffRehearsal(options) {
  const startedAt = currentTime(options?.now);
  const apiBaseUrl = normalizeBaseUrl(options?.apiBaseUrl);
  const credentials = requireCredentials(options?.credentials);
  const providers = requireProviders(options?.providers);
  const capabilityCode = requireText(options?.capabilityCode, "capabilityCode");
  const producer = hasText(options?.producer)
    ? options.producer.trim()
    : "API_MODEL";
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function") {
    throw new Error("当前 Node.js 运行时不支持 fetch");
  }

  const failures = [];
  const requests = [];
  const evidence = {
    status: "BLOCKED",
    stage: "PRE_SIGNOFF_REHEARSAL",
    startedAt,
    finishedAt: startedAt,
    capabilityCode,
    producer,
    providers: [],
    requests,
    failures,
    containsCredentials: false,
    containsPatientData: false,
    automatedExpertSignOff: false,
    providerEnableAttempted: false,
    p6MutationAttempted: false,
  };

  try {
    const login = await requestJson({
      fetchImpl,
      requests,
      apiBaseUrl,
      method: "POST",
      path: "/auth/login",
      headers: { "Content-Type": "application/json" },
      body: credentials,
      label: "登录",
    });
    const session = authenticatedSession(login.headers);

    for (const provider of providers) {
      try {
        const result = await rehearseProvider({
          fetchImpl,
          requests,
          apiBaseUrl,
          session,
          provider,
          capabilityCode,
          producer,
          failures,
        });
        evidence.providers.push(result);
      } catch (error) {
        failures.push(
          `${provider.providerCode} 预演失败：${safeErrorMessage(error)}`,
        );
      }
    }
  } catch (error) {
    failures.push(safeErrorMessage(error));
  }

  evidence.finishedAt = currentTime(options?.now);
  evidence.status =
    failures.length === 0 && evidence.providers.length === providers.length
      ? "PASSED"
      : "BLOCKED";
  return redactEvidence(evidence);
}

async function rehearseProvider({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  provider,
  capabilityCode,
  producer,
  failures,
}) {
  const encodedProvider = encodeURIComponent(provider.providerCode);
  const beforeResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "GET",
    path: `/model-providers/${encodedProvider}`,
    label: `${provider.providerCode} 治理快照（前）`,
    acceptedStatuses: [200, 405],
  });
  const supportsGovernanceRead = beforeResponse.status === 200;
  const readBefore = supportsGovernanceRead
    ? summarizeProvider(beforeResponse.body?.data)
    : null;
  if (readBefore) {
    assessProviderSnapshot(
      failures,
      provider,
      readBefore,
      `${provider.providerCode} 预演前`,
    );
  }

  const healthResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "POST",
    path: `/model-providers/${encodedProvider}/health-check`,
    label: `${provider.providerCode} 真实健康检查`,
  });
  const health = summarizeProvider(healthResponse.body?.data);
  assessProviderSnapshot(
    failures,
    provider,
    health,
    `${provider.providerCode} 健康检查后`,
  );
  if (health.status !== "HEALTHY") {
    failures.push(
      `${provider.providerCode} 真实健康检查不是 HEALTHY：${health.status ?? "<missing>"}`,
    );
  }
  const before = readBefore ?? {
    ...health,
    evidenceSource: "HEALTH_CHECK_PRESERVES_ENABLED_FLAG",
  };

  const runResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "POST",
    path: "/model-evaluations",
    body: {
      providerCode: provider.providerCode,
      modelVersion: provider.modelVersion,
      capabilityCode,
    },
    label: `${provider.providerCode} 医学回归评测`,
  });
  const runId = runResponse.body?.data?.id ?? runResponse.body?.data?.runId;
  if (!Number.isInteger(runId) || runId <= 0) {
    throw new Error("评测响应缺少有效 runId");
  }

  const detailResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "GET",
    path: `/model-evaluations/runs/${runId}`,
    label: `${provider.providerCode} 逐例证据`,
  });
  const evaluation = summarizeEvaluation(detailResponse.body?.data);
  assessEvaluation(failures, provider, capabilityCode, runId, evaluation);

  let after;
  if (supportsGovernanceRead) {
    const afterResponse = await requestJson({
      fetchImpl,
      requests,
      apiBaseUrl,
      session,
      method: "GET",
      path: `/model-providers/${encodedProvider}`,
      label: `${provider.providerCode} 治理快照（后）`,
    });
    after = summarizeProvider(afterResponse.body?.data);
    assessProviderSnapshot(
      failures,
      provider,
      after,
      `${provider.providerCode} 预演后`,
    );
  } else {
    after = {
      ...health,
      evidenceSource: "HEALTH_CHECK_PRESERVES_ENABLED_FLAG",
    };
  }

  const readinessUrl = new URL(
    `${apiBaseUrl}/engine/knowledge-production/readiness`,
  );
  readinessUrl.searchParams.set("producer", producer);
  readinessUrl.searchParams.set("capabilityCode", capabilityCode);
  readinessUrl.searchParams.set("providerCode", provider.providerCode);
  const readinessPath = `${readinessUrl.pathname.replace(
    new URL(apiBaseUrl).pathname.replace(/\/$/, ""),
    "",
  )}${readinessUrl.search}`;
  const readinessResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "GET",
    path: readinessPath,
    label: `${provider.providerCode} readiness`,
  });
  const readiness = summarizeReadiness(readinessResponse.body?.data);
  assessPreSignoffReadiness(
    failures,
    provider,
    capabilityCode,
    producer,
    readiness,
  );

  return {
    providerCode: provider.providerCode,
    expectedModelVersion: provider.modelVersion,
    snapshotMode: supportsGovernanceRead
      ? "GOVERNANCE_READ"
      : "V151_HEALTH_CHECK_COMPATIBILITY",
    before,
    health,
    evaluation,
    after,
    readiness,
  };
}

function assessProviderSnapshot(failures, expected, actual, label) {
  if (actual.providerCode !== expected.providerCode) {
    failures.push(`${label} providerCode 与请求不一致`);
  }
  if (actual.modelVersion !== expected.modelVersion) {
    failures.push(
      `${label}模型版本漂移：expected=${expected.modelVersion} actual=${actual.modelVersion ?? "<missing>"}`,
    );
  }
  if (actual.enabled === null) {
    failures.push(`${label}缺少明确启停状态`);
  } else if (actual.enabled !== false) {
    failures.push(`${label}已启用，违反签署前停用边界`);
  }
}

function assessEvaluation(
  failures,
  expectedProvider,
  capabilityCode,
  runId,
  evaluation,
) {
  const prefix = `${expectedProvider.providerCode} 运行 ${runId}`;
  if (
    evaluation.runId !== runId ||
    evaluation.providerCode !== expectedProvider.providerCode ||
    evaluation.modelVersion !== expectedProvider.modelVersion ||
    evaluation.capabilityCode !== capabilityCode
  ) {
    failures.push(`${prefix} 评测上下文漂移`);
  }
  if (evaluation.status !== "PENDING_REVIEW") {
    failures.push(
      `${prefix} 未保持 PENDING_REVIEW：${evaluation.status ?? "<missing>"}`,
    );
  }
  if (evaluation.reviewerPresent || evaluation.signedAtPresent) {
    failures.push(`${prefix} 出现自动或既有签署痕迹`);
  }
  if (
    !evaluation.evidenceComplete ||
    !evaluation.baselineCurrent ||
    !evaluation.reviewable
  ) {
    failures.push(`${prefix} 不可进入真人复核`);
  }
  if (
    evaluation.totalCases <= 0 ||
    evaluation.passedCases !== evaluation.totalCases ||
    evaluation.failedCases !== 0 ||
    evaluation.cases.length !== evaluation.totalCases
  ) {
    failures.push(
      `${prefix} 评测汇总不完整：cases=${evaluation.cases.length} passed=${evaluation.passedCases} failed=${evaluation.failedCases} total=${evaluation.totalCases}`,
    );
  }
  const unsafeCase = evaluation.cases.some(
    (item) =>
      !item.passed ||
      item.failureCount !== 0 ||
      item.redLineBreach ||
      !item.expectedPhraseHit ||
      (item.citationRequired && !item.citationVerified),
  );
  if (
    evaluation.fakeCitationDetected ||
    evaluation.redLineBreach ||
    evaluation.hallucinationDetected ||
    unsafeCase
  ) {
    failures.push(`${prefix} 医学安全裁决未通过`);
  }
}

function assessPreSignoffReadiness(
  failures,
  expectedProvider,
  capabilityCode,
  producer,
  readiness,
) {
  const prefix = `${expectedProvider.providerCode} readiness`;
  const modelProvider = readiness.items.find(
    (item) => item.code === "MODEL_PROVIDER",
  );
  const disabledProviderContext =
    readiness.providerCode === null && modelProvider?.ready === false;
  const counts = new Map();
  for (const item of readiness.items) {
    counts.set(item.code, (counts.get(item.code) ?? 0) + 1);
  }
  const missing = EXPECTED_READINESS_CODES.filter((code) => !counts.has(code));
  const unknown = [...counts.keys()].filter(
    (code) => !EXPECTED_READINESS_SET.has(code),
  );
  const duplicates = [...counts.entries()]
    .filter(([, count]) => count > 1)
    .map(([code]) => code);
  if (
    readiness.items.length !== EXPECTED_READINESS_CODES.length ||
    missing.length > 0 ||
    unknown.length > 0 ||
    duplicates.length > 0
  ) {
    failures.push(
      `${prefix} 九闸集合不完整：missing=${missing.join(",") || "<none>"} unknown=${unknown.join(",") || "<none>"} duplicates=${duplicates.join(",") || "<none>"}`,
    );
  }
  for (const item of readiness.items) {
    const expectedReady = PRE_SIGNOFF_PASSED_CODES.has(item.code);
    if (item.required !== true || item.ready !== expectedReady) {
      failures.push(`${prefix} ${item.code ?? "<missing>"} 不符合签署前裁决`);
    }
  }
  if (
    (readiness.providerCode !== expectedProvider.providerCode &&
      !disabledProviderContext) ||
    readiness.capabilityCode !== capabilityCode ||
    readiness.producer !== producer
  ) {
    failures.push(`${prefix} 上下文漂移`);
  }
  const p6 = readiness.items.find((item) => item.code === "P6_ACCEPTANCE");
  if (!p6) {
    failures.push(`${prefix} 缺少 P6_ACCEPTANCE 闸门`);
  } else if (p6.ready !== false || !p6.evidenceEndsWithFalse) {
    failures.push(`${prefix} P6 已提前放行或证据不是 false`);
  }
  if (readiness.ready !== false || readiness.modelInvocationAllowed !== false) {
    failures.push(`${prefix} 在真人签署前意外全绿`);
  }
  if (!modelProvider || modelProvider.ready !== false) {
    failures.push(`${prefix} 未证明 Provider 保持停用`);
  }
}

function summarizeProvider(provider) {
  let enabled = null;
  if (typeof provider?.enabled === "boolean") {
    enabled = provider.enabled;
  } else if (typeof provider?.enabledFlag === "string") {
    const flag = provider.enabledFlag.trim().toUpperCase();
    if (flag === "Y" || flag === "N") {
      enabled = flag === "Y";
    }
  }
  return {
    providerCode: provider?.providerCode ?? null,
    providerType: provider?.providerType ?? null,
    modelVersion: provider?.modelVersion ?? null,
    enabled,
    status: provider?.status ?? null,
    version: Number.isInteger(provider?.version) ? provider.version : null,
  };
}

function summarizeEvaluation(detail) {
  const run = detail?.run ?? {};
  const cases = Array.isArray(detail?.cases) ? detail.cases : [];
  return {
    runId: run.runId ?? run.id ?? null,
    providerCode: run.providerCode ?? null,
    modelVersion: run.modelVersion ?? null,
    capabilityCode: run.capabilityCode ?? null,
    promptVersion: run.promptVersion ?? null,
    toolVersion: run.toolVersion ?? null,
    totalCases: Number.isInteger(run.totalCases) ? run.totalCases : 0,
    passedCases: Number.isInteger(run.passedCases) ? run.passedCases : 0,
    failedCases: Number.isInteger(run.failedCases) ? run.failedCases : 0,
    fakeCitationDetected: run.fakeCitationDetected === true,
    redLineBreach: run.redLineBreach === true,
    hallucinationDetected: run.hallucinationDetected === true,
    status: run.status ?? null,
    reviewerPresent: hasText(run.reviewer),
    signedAtPresent: hasText(run.signedAt),
    evidenceComplete: detail?.evidenceComplete === true,
    baselineCurrent: detail?.baselineCurrent === true,
    reviewable: detail?.reviewable === true,
    reviewBlockReason: detail?.reviewBlockReason ?? null,
    cases: cases.map((item) => ({
      evidenceId: item?.evidenceId ?? null,
      regressionCaseId: item?.regressionCaseId ?? null,
      caseVersion: item?.caseVersion ?? null,
      inputSha256: sha256Text(item?.caseInput),
      expectedPhraseSha256: sha256Text(item?.expectedPhrase),
      sourceReferenceSha256: sha256Text(item?.sourceReference),
      outputSha256: sha256Text(item?.outputContent),
      sourceCitationsSha256: sha256Text(item?.sourceCitations),
      expectedPhraseHit: item?.expectedPhraseHit === true,
      citationRequired: item?.citationRequired === true,
      citationVerified: item?.citationVerified === true,
      redLineCase: item?.redLineCase === true,
      redLineBreach: item?.redLineBreach === true,
      passed: item?.passed === true,
      failureCount: Array.isArray(item?.failureReasons)
        ? item.failureReasons.length
        : 0,
    })),
  };
}

function summarizeReadiness(readiness) {
  const items = Array.isArray(readiness?.items) ? readiness.items : [];
  return {
    producer: readiness?.producer ?? null,
    capabilityCode: readiness?.capabilityCode ?? null,
    providerCode: readiness?.providerCode ?? null,
    ready: readiness?.ready === true,
    modelInvocationAllowed: readiness?.modelInvocationAllowed === true,
    passedCount: items.filter((item) => item?.ready === true).length,
    total: items.length,
    items: items.map((item) => ({
      code: item?.code ?? null,
      required: item?.required === true,
      ready: item?.ready === true,
      evidenceEndsWithFalse:
        typeof item?.evidence === "string" &&
        item.evidence.trim().endsWith("=false"),
    })),
  };
}

async function requestJson({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  method,
  path,
  headers = {},
  body,
  label,
  acceptedStatuses = [200],
}) {
  assertAllowedRequest(method, path);
  const normalizedMethod = method.toUpperCase();
  const requestHeaders = {
    Accept: "application/json",
    ...headers,
  };
  if (body !== undefined) {
    requestHeaders["Content-Type"] = "application/json";
  }
  if (session) {
    requestHeaders.Cookie = session.cookie;
    if (!["GET", "HEAD", "OPTIONS"].includes(normalizedMethod)) {
      requestHeaders["X-XSRF-TOKEN"] = session.xsrf;
    }
  }

  let response;
  try {
    response = await fetchImpl(`${apiBaseUrl}${path}`, {
      method: normalizedMethod,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    throw new Error(`${label}请求失败：${safeErrorMessage(error)}`);
  }
  requests.push({
    method: normalizedMethod,
    path,
    status: response.status,
  });
  if (!acceptedStatuses.includes(response.status)) {
    throw new Error(`${label}返回 HTTP ${response.status}`);
  }
  if (response.status !== 200) {
    return { body: null, headers: response.headers, status: response.status };
  }
  const text = await response.text();
  if (
    !(response.headers.get("content-type") ?? "").includes("application/json")
  ) {
    throw new Error(`${label}响应不是 JSON`);
  }
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(`${label}响应不是合法 JSON`);
  }
  return { body: parsed, headers: response.headers, status: response.status };
}

function authenticatedSession(headers) {
  const values =
    typeof headers.getSetCookie === "function"
      ? headers.getSetCookie()
      : splitSetCookie(headers.get("set-cookie") ?? "");
  const pairs = values
    .flatMap((value) => splitSetCookie(value))
    .map((value) => value.split(";")[0]?.trim())
    .filter(Boolean);
  const cookie = pairs.join("; ");
  const xsrfPair = pairs.find((pair) => pair.startsWith("XSRF-TOKEN="));
  const xsrf = xsrfPair
    ? decodeURIComponent(xsrfPair.slice("XSRF-TOKEN=".length))
    : null;
  if (!cookie || !xsrf) {
    throw new Error("登录响应未返回受控会话 Cookie 与 XSRF-TOKEN");
  }
  return { cookie, xsrf };
}

function splitSetCookie(value) {
  return value ? value.split(/,(?=\s*[^;,]+=)/) : [];
}

function requireProviders(providers, options = {}) {
  if (!Array.isArray(providers) || providers.length === 0) {
    throw new Error("providers 必须是非空数组");
  }
  if (options.exactCount && providers.length !== options.exactCount) {
    throw new Error(`工程预演必须显式提供 ${options.exactCount} 个 Provider`);
  }
  const normalized = providers.map((provider, index) => ({
    providerCode: requireText(
      provider?.providerCode,
      `providers[${index}].providerCode`,
    ),
    modelVersion: requireText(
      provider?.modelVersion,
      `providers[${index}].modelVersion`,
    ),
  }));
  for (const provider of normalized) {
    if (!PROVIDER_CODE.test(provider.providerCode)) {
      throw new Error(`providerCode 格式非法：${provider.providerCode}`);
    }
  }
  const codes = normalized.map((provider) => provider.providerCode);
  if (new Set(codes).size !== codes.length) {
    throw new Error("providerCode 不得重复");
  }
  return normalized;
}

function requireCredentials(credentials) {
  return {
    tenantId: requireText(credentials?.tenantId, "credentials.tenantId"),
    username: requireText(credentials?.username, "credentials.username"),
    password: requireText(credentials?.password, "credentials.password"),
  };
}

function validateReadinessQuery(path) {
  const url = new URL(path, "http://localhost");
  const expected = ["producer", "capabilityCode", "providerCode"];
  const keys = [...url.searchParams.keys()];
  if (
    keys.length !== expected.length ||
    expected.some(
      (key) =>
        !hasText(url.searchParams.get(key)) ||
        keys.filter((candidate) => candidate === key).length !== 1,
    )
  ) {
    throw new Error(`预演请求不在安全白名单：GET ${path}`);
  }
}

function normalizeBaseUrl(value) {
  const text = requireText(value, "apiBaseUrl");
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error("apiBaseUrl 必须是绝对 URL");
  }
  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error("apiBaseUrl 只允许 HTTP 或 HTTPS");
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error("apiBaseUrl 不得包含内嵌凭据、查询串或片段");
  }
  return url.toString().replace(/\/+$/, "");
}

function requireRelativeApiPath(value) {
  const path = requireText(value, "path");
  if (!path.startsWith("/") || path.startsWith("//")) {
    throw new Error("path 必须是 API 相对路径");
  }
  return path;
}

function sha256Text(value) {
  if (typeof value !== "string") {
    return null;
  }
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function requireText(value, field) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${field} 不能为空`);
  }
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function currentTime(now) {
  const value = typeof now === "function" ? now() : new Date();
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) {
    throw new Error("now 必须返回有效 Date");
  }
  return value.toISOString();
}

function safeErrorMessage(error) {
  return error instanceof Error && error.message
    ? error.message
    : "预演发生未知错误";
}
