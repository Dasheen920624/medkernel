import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname } from "node:path";

export const EXPECTED_READINESS_CODES = Object.freeze([
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
const ALLOWED_ROBOTS_POLICIES = new Set(["ALLOW_FETCH", "MANUAL_APPROVED"]);
const SENSITIVE_KEY =
  /password|cookie|token|secret|credential|recovery|mfa|otp|totp|signature/i;
const REQUIRED_ENV_KEYS = Object.freeze([
  "P9_T98_API_BASE_URL",
  "P9_T98_HEALTH_URL",
  "P9_T98_CREDENTIALS_FILE",
  "P9_T98_PROVIDER_CODE",
  "P9_T98_SOURCE_CODE",
  "P9_T98_OUTPUT_PATH",
]);

export function assessKnowledgeReadiness(data, expected) {
  const failures = [];
  const items = Array.isArray(data?.items) ? data.items : [];
  const codes = items.map((item) => item?.code).filter(Boolean);
  const codeCounts = new Map();

  for (const code of codes) {
    codeCounts.set(code, (codeCounts.get(code) ?? 0) + 1);
  }

  const duplicates = [...codeCounts.entries()]
    .filter(([, count]) => count > 1)
    .map(([code]) => code);
  if (duplicates.length > 0) {
    failures.push(`readiness 闸门存在重复：${duplicates.join(",")}`);
  }

  const missing = EXPECTED_READINESS_CODES.filter(
    (code) => !codeCounts.has(code),
  );
  const unknown = [...codeCounts.keys()].filter(
    (code) => !EXPECTED_READINESS_SET.has(code),
  );
  if (
    items.length !== EXPECTED_READINESS_CODES.length ||
    missing.length > 0 ||
    unknown.length > 0
  ) {
    failures.push(
      `readiness 闸门集合不完整：missing=${missing.join(",") || "<none>"}` +
        ` unknown=${unknown.join(",") || "<none>"} count=${items.length}`,
    );
  }

  for (const item of items) {
    if (!item?.code) {
      failures.push("readiness 存在缺少 code 的裁决项");
      continue;
    }
    if (item.required !== true) {
      failures.push(`${item.code} 未声明为强制闸门`);
    }
    if (item.ready !== true) {
      failures.push(`${item.code} 未通过：${item.message || "未提供原因"}`);
    }
  }

  if (data?.ready !== true || data?.modelInvocationAllowed !== true) {
    failures.push("readiness 聚合状态不是全绿");
  }
  compareContext(failures, "producer", data?.producer, expected?.producer);
  compareContext(
    failures,
    "providerCode",
    data?.providerCode,
    expected?.providerCode,
  );
  compareContext(
    failures,
    "capabilityCode",
    data?.capabilityCode,
    expected?.capabilityCode,
  );

  return {
    ready: failures.length === 0,
    failures,
    items,
  };
}

export function assessSourceReadiness(pageData, sourceCode) {
  const failures = [];
  const items = Array.isArray(pageData?.items) ? pageData.items : [];
  const source = items.find((item) => item?.sourceCode === sourceCode) ?? null;

  if (!source) {
    failures.push(`未找到受控来源：${sourceCode}`);
    return { ready: false, failures, source: null };
  }
  if (String(source.enabledFlag).toUpperCase() !== "Y") {
    failures.push(`${sourceCode} 未启用`);
  }
  if (!hasText(source.approvedBy) || !hasText(source.approvedAt)) {
    failures.push(`${sourceCode} 缺少独立审批证据`);
  }
  if (source.licensePolicy !== "PERMITTED") {
    failures.push(`${sourceCode} 许可策略不允许知识生产`);
  }
  if (!ALLOWED_ROBOTS_POLICIES.has(source.robotsPolicy)) {
    failures.push(`${sourceCode} robots/ToS 策略不允许抓取`);
  }

  return {
    ready: failures.length === 0,
    failures,
    source,
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
      SENSITIVE_KEY.test(key) ? "[REDACTED]" : redactEvidence(item),
    ]),
  );
}

export function readPreflightConfig(env, options = {}) {
  for (const key of REQUIRED_ENV_KEYS) {
    if (!hasText(env?.[key])) {
      throw new Error(`缺少必填环境变量 ${key}`);
    }
  }
  const readFile = options.readFile ?? ((path) => readFileSync(path, "utf8"));
  let rawCredentials;
  try {
    rawCredentials = JSON.parse(readFile(env.P9_T98_CREDENTIALS_FILE));
  } catch {
    throw new Error("P9_T98_CREDENTIALS_FILE 不是合法 JSON");
  }

  return {
    apiBaseUrl: requireAbsoluteUrl(
      env.P9_T98_API_BASE_URL,
      "P9_T98_API_BASE_URL",
    ),
    healthUrl: requireAbsoluteUrl(env.P9_T98_HEALTH_URL, "P9_T98_HEALTH_URL"),
    credentials: requireCredentials(rawCredentials),
    producer: hasText(env.P9_T98_PRODUCER)
      ? env.P9_T98_PRODUCER.trim()
      : "API_MODEL",
    providerCode: env.P9_T98_PROVIDER_CODE.trim(),
    capabilityCode: hasText(env.P9_T98_CAPABILITY_CODE)
      ? env.P9_T98_CAPABILITY_CODE.trim()
      : "rule.draft",
    sourceCode: env.P9_T98_SOURCE_CODE.trim(),
    outputPath: env.P9_T98_OUTPUT_PATH.trim(),
  };
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

export async function runReadinessPreflight(options) {
  const startedAt = currentTime(options?.now);
  const requests = [];
  const failures = [];
  const apiBaseUrl = normalizeBaseUrl(options?.apiBaseUrl);
  const healthUrl = requireAbsoluteUrl(options?.healthUrl, "healthUrl");
  const credentials = requireCredentials(options?.credentials);
  const expected = {
    producer: requireText(options?.producer, "producer"),
    providerCode: requireText(options?.providerCode, "providerCode"),
    capabilityCode: requireText(options?.capabilityCode, "capabilityCode"),
  };
  const sourceCode = requireText(options?.sourceCode, "sourceCode");
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function") {
    throw new Error("当前 Node.js 运行时不支持 fetch");
  }

  const evidence = {
    status: "BLOCKED",
    startedAt,
    finishedAt: startedAt,
    target: {
      apiBaseUrl,
      healthUrl,
      ...expected,
      sourceCode,
    },
    session: null,
    health: null,
    knowledgeReadiness: null,
    sourceReadiness: null,
    requests,
    failures,
  };

  try {
    const loginUrl = `${apiBaseUrl}/auth/login`;
    const login = await requestJson({
      fetchImpl,
      requests,
      url: loginUrl,
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(credentials),
      label: "登录",
    });
    const cookie = cookieHeader(login.headers);
    if (!cookie) {
      throw new Error("登录响应未返回受控会话 Cookie");
    }

    const authenticatedHeaders = {
      Accept: "application/json",
      Cookie: cookie,
    };
    const profileResponse = await requestJson({
      fetchImpl,
      requests,
      url: `${apiBaseUrl}/security/me`,
      method: "GET",
      headers: authenticatedHeaders,
      label: "安全画像",
    });
    const profile = profileResponse.body?.data;
    const profileTenantId = profile?.dataScope?.tenantId ?? null;
    evidence.session = {
      userId: profile?.userId ?? null,
      tenantId: profileTenantId,
      roles: Array.isArray(profile?.roles)
        ? profile.roles
            .map((role) => (typeof role === "string" ? role : role?.code))
            .filter(Boolean)
        : [],
      mfaRequired: profile?.mfaRequired === true,
      mfaBound: profile?.mfaBound === true,
    };
    if (!profile?.userId) {
      failures.push("安全画像缺少当前用户标识");
    }
    if (profileTenantId !== credentials.tenantId) {
      failures.push(
        `安全画像租户与凭据不一致：expected=${credentials.tenantId}` +
          ` actual=${profileTenantId ?? "<missing>"}`,
      );
    }

    const healthResponse = await requestJson({
      fetchImpl,
      requests,
      url: healthUrl,
      method: "GET",
      headers: { Accept: "application/json" },
      label: "服务健康",
    });
    evidence.health = {
      status: healthResponse.body?.status ?? null,
      ready: healthResponse.body?.status === "UP",
    };
    if (!evidence.health.ready) {
      failures.push(
        `服务 readiness 不是 UP：${evidence.health.status ?? "<missing>"}`,
      );
    }

    const readinessUrl = new URL(
      `${apiBaseUrl}/engine/knowledge-production/readiness`,
    );
    readinessUrl.searchParams.set("producer", expected.producer);
    readinessUrl.searchParams.set("capabilityCode", expected.capabilityCode);
    readinessUrl.searchParams.set("providerCode", expected.providerCode);
    const readinessResponse = await requestJson({
      fetchImpl,
      requests,
      url: readinessUrl.toString(),
      method: "GET",
      headers: authenticatedHeaders,
      label: "知识生产 readiness",
    });
    const readiness = assessKnowledgeReadiness(
      readinessResponse.body?.data,
      expected,
    );
    evidence.knowledgeReadiness = {
      ready: readiness.ready,
      passedCount: readiness.items.filter((item) => item?.ready === true)
        .length,
      total: readiness.items.length,
      items: readiness.items,
    };
    failures.push(...readiness.failures);

    const sourcesUrl = new URL(
      `${apiBaseUrl}/engine/knowledge/acquisition/sources`,
    );
    sourcesUrl.searchParams.set("page", "1");
    sourcesUrl.searchParams.set("size", "100");
    const sourceResponse = await requestJson({
      fetchImpl,
      requests,
      url: sourcesUrl.toString(),
      method: "GET",
      headers: authenticatedHeaders,
      label: "受控来源",
    });
    const source = assessSourceReadiness(sourceResponse.body?.data, sourceCode);
    evidence.sourceReadiness = {
      ready: source.ready,
      source: source.source
        ? {
            sourceCode: source.source.sourceCode,
            enabledFlag: source.source.enabledFlag,
            approvedBy: source.source.approvedBy,
            approvedAt: source.source.approvedAt,
            licensePolicy: source.source.licensePolicy,
            robotsPolicy: source.source.robotsPolicy,
          }
        : null,
    };
    failures.push(...source.failures);
  } catch (error) {
    failures.push(safeErrorMessage(error));
  }

  evidence.finishedAt = currentTime(options?.now);
  evidence.status = failures.length === 0 ? "PASSED" : "BLOCKED";
  return redactEvidence(evidence);
}

function compareContext(failures, field, actual, expected) {
  if (actual !== expected) {
    failures.push(
      `${field} 与请求不一致：expected=${expected ?? "<missing>"}` +
        ` actual=${actual ?? "<missing>"}`,
    );
  }
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

async function requestJson({
  fetchImpl,
  requests,
  url,
  method,
  headers,
  body,
  label,
}) {
  let response;
  try {
    response = await fetchImpl(url, { method, headers, body });
  } catch (error) {
    throw new Error(`${label}请求失败：${safeErrorMessage(error)}`);
  }
  const target = new URL(url);
  requests.push({
    method,
    path: `${target.pathname}${target.search}`,
    status: response.status,
  });
  if (!response.ok) {
    throw new Error(`${label}返回 HTTP ${response.status}`);
  }
  const contentType = response.headers.get("content-type") ?? "";
  const text = await response.text();
  if (!contentType.includes("application/json") || !text) {
    throw new Error(`${label}响应不是合法 JSON`);
  }
  try {
    return {
      body: JSON.parse(text),
      headers: response.headers,
    };
  } catch {
    throw new Error(`${label}响应不是合法 JSON`);
  }
}

function cookieHeader(headers) {
  const values =
    typeof headers.getSetCookie === "function"
      ? headers.getSetCookie()
      : splitSetCookie(headers.get("set-cookie") ?? "");
  return values
    .flatMap((value) => splitSetCookie(value))
    .map((value) => value.split(";")[0]?.trim())
    .filter(Boolean)
    .join("; ");
}

function splitSetCookie(value) {
  if (!value) {
    return [];
  }
  return value.split(/,(?=\s*[^;,]+=)/);
}

function normalizeBaseUrl(value) {
  return requireAbsoluteUrl(value, "apiBaseUrl").replace(/\/+$/, "");
}

function requireAbsoluteUrl(value, field) {
  const text = requireText(value, field);
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error(`${field} 必须是绝对 URL`);
  }
  if (!["http:", "https:"].includes(url.protocol)) {
    throw new Error(`${field} 只允许 HTTP 或 HTTPS`);
  }
  if (url.username || url.password || url.search || url.hash) {
    throw new Error(`${field} 不得包含内嵌凭据、查询串或片段`);
  }
  return url.toString().replace(/\/$/, "");
}

function requireCredentials(credentials) {
  return {
    username: requireText(credentials?.username, "credentials.username"),
    password: requireText(credentials?.password, "credentials.password"),
    tenantId: requireText(credentials?.tenantId, "credentials.tenantId"),
  };
}

function requireText(value, field) {
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error(`${field} 不能为空`);
  }
  return value.trim();
}

function currentTime(now) {
  const value = typeof now === "function" ? now() : new Date();
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) {
    throw new Error("now 必须返回有效 Date");
  }
  return value.toISOString();
}

function safeErrorMessage(error) {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return "预检发生未知错误";
}
