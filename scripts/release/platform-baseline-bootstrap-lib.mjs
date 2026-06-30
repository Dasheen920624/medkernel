import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  selectLaunchAccount,
  validateLaunchCredentials,
} from "./launch-account-bootstrap-lib.mjs";
import { validateFullKnowledgeManifest } from "../knowledge/full-knowledge-rehearsal-lib.mjs";

const FIELD_CATALOG_IDENTITY = "FIELD.CATALOG.CLINICAL_CONTEXT";
const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const API_ALLOWLIST = Object.freeze([
  ["POST", /^\/auth\/login$/u],
  ["GET", /^\/engine\/releases\/platform-baselines\/current$/u],
  ["GET", /^\/engine\/releases\/platform-baselines\/candidates\?/u],
  ["POST", /^\/engine\/context\/field-catalog\/drafts$/u],
  ["POST", /^\/engine\/releases\/platform-baselines$/u],
]);

export function readPlatformBaselineBootstrapConfig(env, options = {}) {
  const readFile = options.readFile ?? ((file) => readFileSync(file, "utf8"));
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  for (const key of ["LAUNCH_API_BASE_URL", "LAUNCH_CREDENTIALS_FILE"]) {
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
    env.LAUNCH_PLATFORM_BASELINE_EVIDENCE_PATH?.trim() ||
      path.join(runtimeRoot, "evidence/current-launch/platform-baseline.json"),
    repoRoot,
    "平台基线启动证据路径",
  );
  const credentials = parseJson(readFile(credentialsPath), "统一上线凭据");
  validateLaunchCredentials(credentials);
  const knowledgeManifestPath = hasText(env.FULL_KNOWLEDGE_MANIFEST_PATH)
    ? path.resolve(env.FULL_KNOWLEDGE_MANIFEST_PATH)
    : hasText(env.LAUNCH_PLATFORM_BASELINE_KNOWLEDGE_MANIFEST_PATH)
    ? path.resolve(env.LAUNCH_PLATFORM_BASELINE_KNOWLEDGE_MANIFEST_PATH)
    : null;
  const knowledgeManifest = knowledgeManifestPath
    ? validateFullKnowledgeManifest(parseJson(
        readFile(knowledgeManifestPath),
        "全知识演练清单",
      ))
    : null;
  return {
    apiBaseUrl: normalizeApiBaseUrl(env.LAUNCH_API_BASE_URL),
    credentialsPath,
    evidencePath,
    knowledgeManifestPath,
    knowledgeManifest,
    operator: selectLaunchAccount(credentials, "platform", "engine-operator"),
  };
}

export async function runPlatformBaselineBootstrap(options) {
  const apiBaseUrl = normalizeApiBaseUrl(options?.apiBaseUrl);
  const operator = requireOperator(options?.operator);
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function") throw new Error("当前 Node.js 运行时不支持 fetch");

  const requests = [];
  const startedAt = now(options?.now);
  const knowledgeManifest = options?.knowledgeManifest
    ? validateFullKnowledgeManifest(options.knowledgeManifest)
    : null;
  const requiredKnowledgeIdentities = knowledgeManifest
    ? knowledgeManifest.entries.map((entry) => entry.identityCode)
    : [];
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
    label: "平台医疗引擎运营员登录",
  });
  assertOperatorLogin(login.data, operator);
  const session = authenticatedSession(login.headers);

  const current = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session,
    method: "GET",
    path: "/engine/releases/platform-baselines/current",
    label: "读取当前平台标准版本",
    acceptedStatuses: [200, 404],
  });
  let knowledgeCandidates = [];
  if (requiredKnowledgeIdentities.length > 0) {
    knowledgeCandidates = await fetchRequiredKnowledgeCandidates({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      requiredKnowledgeIdentities,
    });
  }
  if (current.status === 200) {
    const fieldCatalog = requireFieldCatalogItem(current.data, "当前平台标准版本");
    const currentKnowledge = summarizeKnowledgeCoverage(
      current.data,
      requiredKnowledgeIdentities,
    );
    if (currentKnowledge.missingIdentities.length === 0) {
      return evidence({
        startedAt,
        finishedAt: now(options?.now),
        operator,
        baselineDetail: current.data,
        fieldCatalog,
        knowledgeManifest,
        knowledgeAssets: currentKnowledge.activeAssets,
        reused: true,
        refreshed: false,
        requests,
      });
    }

    await requestJson({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      method: "POST",
      path: "/engine/releases/platform-baselines",
      body: {
        publishVersionIds: knowledgeCandidates
          .filter((candidate) =>
            currentKnowledge.missingIdentities.includes(candidate.assetIdentity),
          )
          .map((candidate) => candidate.versionId),
        disabledAssets: [],
      },
      label: "刷新平台字段目录与全知识权威基线",
    });

    const refreshed = await requestJson({
      apiBaseUrl,
      fetchImpl,
      requests,
      session,
      method: "GET",
      path: "/engine/releases/platform-baselines/current",
      label: "回读平台字段目录与全知识权威基线",
    });
    const refreshedFieldCatalog = requireFieldCatalogItem(
      refreshed.data,
      "刷新后的平台标准版本",
    );
    const refreshedKnowledge = requireKnowledgeCoverage(
      refreshed.data,
      requiredKnowledgeIdentities,
      "刷新后的平台标准版本",
    );
    return evidence({
      startedAt,
      finishedAt: now(options?.now),
      operator,
      baselineDetail: refreshed.data,
      fieldCatalog: refreshedFieldCatalog,
      knowledgeManifest,
      knowledgeAssets: refreshedKnowledge.activeAssets,
      reused: false,
      refreshed: true,
      requests,
    });
  }

  const draft = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session,
    method: "POST",
    path: "/engine/context/field-catalog/drafts",
    label: "固化平台字段目录草稿",
  });
  const draftVersion = requireFieldCatalogDraft(draft.data);

  await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session,
    method: "POST",
    path: "/engine/releases/platform-baselines",
    body: {
      publishVersionIds: [
        draftVersion.versionId,
        ...knowledgeCandidates.map((candidate) => candidate.versionId),
      ],
      disabledAssets: [],
    },
    label: requiredKnowledgeIdentities.length > 0
      ? "发布平台字段目录与全知识权威基线"
      : "发布平台字段目录权威基线",
  });

  const published = await requestJson({
    apiBaseUrl,
    fetchImpl,
    requests,
    session,
    method: "GET",
    path: "/engine/releases/platform-baselines/current",
    label: requiredKnowledgeIdentities.length > 0
      ? "回读平台字段目录与全知识权威基线"
      : "回读平台字段目录权威基线",
  });
  const fieldCatalog = requireFieldCatalogItem(published.data, "发布后的平台标准版本");
  if (fieldCatalog.versionId !== draftVersion.versionId) {
    throw new Error("字段目录平台基线未绑定刚发布的字段目录草稿");
  }
  const knowledgeCoverage = requireKnowledgeCoverage(
    published.data,
    requiredKnowledgeIdentities,
    "发布后的平台标准版本",
  );

  return evidence({
    startedAt,
    finishedAt: now(options?.now),
    operator,
    baselineDetail: published.data,
    draftVersion,
    fieldCatalog,
    knowledgeManifest,
    knowledgeAssets: knowledgeCoverage.activeAssets,
    reused: false,
    refreshed: false,
    requests,
  });
}

function evidence(args) {
  const release = requireRelease(args.baselineDetail?.release);
  return {
    status: "PASSED",
    stage: "PLATFORM_BASELINE_BOOTSTRAP",
    startedAt: args.startedAt,
    finishedAt: args.finishedAt,
    reused: args.reused,
    refreshed: args.refreshed === true,
    operator: {
      tenantId: args.operator.tenantId,
      userId: args.operator.userId,
      role: args.operator.role,
    },
    draftVersion: args.draftVersion
      ? {
          versionId: args.draftVersion.versionId,
          versionNo: args.draftVersion.versionNo,
          status: args.draftVersion.status,
        }
      : null,
    baseline: release,
    fieldCatalog: {
      assetType: args.fieldCatalog.assetType,
      assetIdentity: args.fieldCatalog.assetIdentity,
      entryState: args.fieldCatalog.entryState,
      versionId: args.fieldCatalog.versionId,
      versionNo: args.fieldCatalog.versionNo,
      contentHash: args.fieldCatalog.contentHash ?? null,
    },
    knowledge: args.knowledgeManifest
      ? {
          manifestCode: args.knowledgeManifest.manifestCode,
          releaseVersion: args.knowledgeManifest.releaseVersion,
          requiredCount: args.knowledgeManifest.entries.length,
          activeCount: args.knowledgeAssets.length,
          missingIdentities: [],
        }
      : null,
    knowledgeAssets: args.knowledgeAssets.map((item) => ({
      assetType: item.assetType,
      assetIdentity: item.assetIdentity,
      entryState: item.entryState,
      versionId: item.versionId,
      versionNo: item.versionNo,
      contentHash: item.contentHash ?? null,
    })),
    requests: args.requests,
  };
}

async function fetchRequiredKnowledgeCandidates(args) {
  const response = await requestJson({
    apiBaseUrl: args.apiBaseUrl,
    fetchImpl: args.fetchImpl,
    requests: args.requests,
    session: args.session,
    method: "GET",
    path: "/engine/releases/platform-baselines/candidates?assetType=KNOWLEDGE&page=1&size=200",
    label: "查询可进入平台标准版本的全知识资产",
  });
  const candidates = Array.isArray(response.data?.items) ? response.data.items : [];
  const byIdentity = new Map();
  for (const candidate of candidates) {
    if (
      candidate?.assetType !== "KNOWLEDGE" ||
      !hasText(candidate.assetIdentity) ||
      !hasText(candidate.versionId) ||
      !hasText(String(candidate.versionNo ?? "")) ||
      !["DRAFT", "PUBLISHED"].includes(candidate.status)
    ) {
      continue;
    }
    if (!byIdentity.has(candidate.assetIdentity)) {
      byIdentity.set(candidate.assetIdentity, {
        assetType: candidate.assetType,
        assetIdentity: candidate.assetIdentity,
        versionId: candidate.versionId,
        versionNo: String(candidate.versionNo),
        status: candidate.status,
      });
    }
  }
  const missing = args.requiredKnowledgeIdentities.filter(
    (identity) => !byIdentity.has(identity),
  );
  if (missing.length > 0) {
    throw new Error(`全知识平台基线缺少可发布候选版本：${missing.join(", ")}`);
  }
  return args.requiredKnowledgeIdentities.map((identity) => byIdentity.get(identity));
}

function summarizeKnowledgeCoverage(detail, requiredKnowledgeIdentities) {
  if (requiredKnowledgeIdentities.length === 0) {
    return { activeAssets: [], missingIdentities: [] };
  }
  const required = new Set(requiredKnowledgeIdentities);
  const activeAssets = extractActiveKnowledgeAssets(detail)
    .filter((item) => required.has(item.assetIdentity));
  const activeIdentities = new Set(activeAssets.map((item) => item.assetIdentity));
  return {
    activeAssets,
    missingIdentities: requiredKnowledgeIdentities.filter(
      (identity) => !activeIdentities.has(identity),
    ),
  };
}

function requireKnowledgeCoverage(detail, requiredKnowledgeIdentities, label) {
  const coverage = summarizeKnowledgeCoverage(detail, requiredKnowledgeIdentities);
  if (coverage.missingIdentities.length > 0) {
    throw new Error(`${label}缺少 ACTIVE 全知识平台基线：${coverage.missingIdentities.join(", ")}`);
  }
  return coverage;
}

function extractActiveKnowledgeAssets(detail) {
  return (Array.isArray(detail?.items) ? detail.items : [])
    .filter((item) =>
      item?.assetType === "KNOWLEDGE" &&
      item.entryState === "ACTIVE" &&
      hasText(item.assetIdentity) &&
      hasText(item.versionId) &&
      hasText(String(item.versionNo ?? "")),
    )
    .map((item) => ({
      assetType: item.assetType,
      assetIdentity: item.assetIdentity,
      entryState: item.entryState,
      versionId: item.versionId,
      versionNo: String(item.versionNo),
      contentHash: item.contentHash ?? null,
    }));
}

function requireFieldCatalogDraft(data) {
  if (
    !data ||
    data.assetType !== "FIELD_CATALOG" ||
    data.assetIdentity !== FIELD_CATALOG_IDENTITY ||
    !hasText(data.versionId) ||
    !hasText(String(data.versionNo ?? ""))
  ) {
    throw new Error("字段目录草稿未返回统一 FIELD_CATALOG 资产版本");
  }
  if (data.status !== "DRAFT") {
    throw new Error("字段目录草稿必须保持 DRAFT 状态再进入平台发布");
  }
  return {
    versionId: data.versionId,
    versionNo: String(data.versionNo),
    status: data.status,
  };
}

function requireFieldCatalogItem(detail, label) {
  const items = Array.isArray(detail?.items) ? detail.items : [];
  const item = items.find(
    (value) =>
      value?.assetType === "FIELD_CATALOG" &&
      value?.assetIdentity === FIELD_CATALOG_IDENTITY,
  );
  if (
    !item ||
    item.entryState !== "ACTIVE" ||
    !hasText(item.versionId) ||
    !hasText(String(item.versionNo ?? ""))
  ) {
    throw new Error(`${label}缺少 ACTIVE 字段目录平台基线`);
  }
  return {
    assetType: item.assetType,
    assetIdentity: item.assetIdentity,
    entryState: item.entryState,
    versionId: item.versionId,
    versionNo: String(item.versionNo),
    contentHash: item.contentHash ?? null,
  };
}

function requireRelease(release) {
  if (!release?.baselineReleaseId || !Number.isInteger(release.revisionNo)) {
    throw new Error("字段目录平台基线缺少发布修订信息");
  }
  return {
    baselineReleaseId: release.baselineReleaseId,
    revisionNo: release.revisionNo,
    manifestHash: release.manifestHash ?? null,
  };
}

async function requestJson(options) {
  assertAllowedPath(options.method, options.path);
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-Trace-Id": `platform-baseline-bootstrap-${Date.now()}`,
  };
  if (options.session) {
    headers.Cookie = options.session.cookie;
    headers["X-XSRF-TOKEN"] = options.session.xsrf;
  }
  const response = await options.fetchImpl(`${options.apiBaseUrl}${options.path}`, {
    method: options.method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
  const raw = await response.text();
  let payload;
  try {
    payload = raw ? JSON.parse(raw) : {};
  } catch {
    throw new Error(`${options.label} 返回的不是合法 JSON（HTTP ${response.status}）`);
  }
  const accepted = options.acceptedStatuses ?? [200, 201];
  const ok = accepted.includes(response.status);
  options.requests.push({
    method: options.method,
    path: options.path,
    status: response.status,
    ok,
    label: options.label,
  });
  if (!ok || (payload?.success === false && response.ok)) {
    throw new Error(
      `${options.label} 失败（HTTP ${response.status}，${payload?.code ?? "NO_CODE"}）：` +
        `${payload?.detail ?? payload?.message ?? "无错误详情"}`,
    );
  }
  return { data: payload?.data, payload, headers: response.headers, status: response.status };
}

function authenticatedSession(headers) {
  const raw = headers?.get?.("set-cookie") ?? "";
  const pairs = String(raw)
    .split(/,(?=\s*[^;,=\s]+=[^;,]*)/u)
    .map((item) => item.split(";", 1)[0]?.trim())
    .filter(Boolean);
  const access = pairs.find((item) => item.startsWith("mk_access="));
  const xsrf = pairs.find((item) => item.startsWith("XSRF-TOKEN="));
  if (!access || !xsrf) throw new Error("登录响应未返回 mk_access 与 XSRF-TOKEN");
  return {
    cookie: pairs.join("; "),
    xsrf: decodeURIComponent(xsrf.slice("XSRF-TOKEN=".length)),
  };
}

function assertAllowedPath(method, requestPath) {
  const allowed = API_ALLOWLIST.some(
    ([allowedMethod, pattern]) => allowedMethod === method && pattern.test(requestPath),
  );
  if (!allowed) {
    throw new Error(`平台基线启动脚本拒绝未列入白名单的接口 ${method} ${requestPath}`);
  }
}

function assertOperatorLogin(data, operator) {
  if (!data || data.tenantId !== operator.tenantId || data.userId !== operator.userId) {
    throw new Error("平台基线启动登录身份与统一凭据不一致");
  }
  if (data.mustChangePwd !== false || data.mfaRequired !== false || data.mfaBound !== false) {
    throw new Error("平台基线启动账号必须完成改密且默认 MFA 关闭");
  }
  if (!Array.isArray(data.roles) || data.roles.length !== 1 || data.roles[0] !== "engine-operator") {
    throw new Error("平台基线启动必须由且仅由医疗引擎运营员执行");
  }
}

function requireOperator(operator) {
  if (!operator || typeof operator !== "object" || Array.isArray(operator)) {
    throw new Error("平台基线启动操作者必须是统一凭据账号");
  }
  for (const field of ["tenantId", "userId", "username", "password", "role"]) {
    requireText(operator[field], `operator.${field}`);
  }
  if (operator.tenantId !== "t-1" || operator.role !== "engine-operator") {
    throw new Error("平台基线启动只允许平台租户医疗引擎运营员执行");
  }
  return operator;
}

function normalizeApiBaseUrl(value) {
  const normalized = requireText(value, "LAUNCH_API_BASE_URL").replace(/\/+$/u, "");
  const parsed = new URL(normalized);
  const loopback = ["127.0.0.1", "localhost", "::1"].includes(parsed.hostname);
  if (
    !/^https?:$/u.test(parsed.protocol) ||
    !parsed.pathname.endsWith("/api/v1") ||
    (parsed.protocol !== "https:" && !loopback)
  ) {
    throw new Error("上线 API 必须使用 HTTPS，或仅在回环地址使用 HTTP，并以 /api/v1 结尾");
  }
  return normalized;
}

function outsideRepo(value, repoRoot, label) {
  const target = path.resolve(requireText(value, label));
  const relative = path.relative(repoRoot, target);
  if (relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))) {
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
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}

function requireText(value, label) {
  if (!hasText(value)) throw new Error(`${label} 不能为空`);
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}
