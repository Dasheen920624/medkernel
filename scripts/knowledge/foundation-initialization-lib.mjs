import { createHash, randomUUID } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname } from "node:path";

const REQUIRED_COVERAGE = Object.freeze([
  "SOURCE_LICENSE_MANIFEST",
  "DATA_ELEMENT_CATALOG",
  "TERMINOLOGY_CODE_SYSTEM",
  "VALUE_SET_SYSTEM_ACTION_DICTIONARY",
  "UNIT_DIMENSION_ALIAS_CONVERSION",
  "MASTER_DATA",
  "INTEROPERABILITY_MAPPING_PROFILE",
  "SEMANTIC_RELATION_DEPRECATION_REDIRECT",
  "EVIDENCE_GRADE_AUTHORITY",
  "DEPENDENCY_COMPATIBILITY_IMPACT",
  "AUTHORITATIVE_SOURCE_SCOPE",
  "GOLDEN_REGRESSION_BOM_COVERAGE",
]);
const REQUIRED_FOUNDATION_CODES = Object.freeze([
  "KNOWGEN-29",
  "KNOWGEN-01",
  "KNOWGEN-26",
  "KNOWGEN-27",
  "KNOWGEN-28",
  "KNOWGEN-25",
  "KNOWGEN-15",
  "KNOWGEN-32",
]);
const ALLOWED_ASSET_TYPES = new Set([
  "FIELD_CATALOG",
  "TERMINOLOGY",
  "VALUE_SET",
  "KNOWLEDGE",
]);
const SENSITIVE_KEY =
  /password|cookie|token|secret|credential|recovery|mfa|otp|totp|signature/i;
const SAFETY_BOOLEAN_KEYS = new Set([
  "containsCredentials",
  "containsPatientData",
  "providerEnableAttempted",
  "p6MutationAttempted",
  "automatedMedicalReviewAttempted",
  "automatedExpertSignOff",
]);
const ALLOWED_REQUESTS = Object.freeze([
  ["POST", /^\/auth\/login$/],
  ["GET", /^\/model-evaluations\/regression-cases\?enabledFlag=Y$/],
  ["POST", /^\/model-evaluations\/regression-cases$/],
  ["GET", /^\/engine\/knowledge-production\/initialization\/batches$/],
  ["GET", /^\/engine\/knowledge-production\/initialization\/batches\/[^/?]+$/],
  ["POST", /^\/engine\/knowledge\/sources$/],
  ["POST", /^\/engine\/knowledge\/sources\/\d+\/versions$/],
  ["POST", /^\/engine\/knowledge\/sources\/fragments$/],
  [
    "POST",
    /^\/engine\/knowledge-production\/initialization\/source-versions\/\d+\/approval$/,
  ],
  ["GET", /^\/engine\/knowledge\/identities\/by-code\/[^/?]+$/],
  [
    "GET",
    /^\/engine\/knowledge\/identities\/\d+\/candidates\?page=1&size=200$/,
  ],
  ["POST", /^\/engine\/knowledge-production\/generate$/],
  [
    "POST",
    /^\/engine\/knowledge-production\/initialization\/batches\/preview$/,
  ],
  ["POST", /^\/engine\/knowledge-production\/initialization\/batches$/],
]);

export function assertAllowedFoundationRequest(method, path) {
  const normalizedMethod = requireText(method, "method").toUpperCase();
  const normalizedPath = requireRelativePath(path);
  const allowed = ALLOWED_REQUESTS.some(
    ([allowedMethod, pattern]) =>
      normalizedMethod === allowedMethod && pattern.test(normalizedPath),
  );
  if (!allowed) {
    throw new Error(
      `请求不在稳定知识初始化白名单：${normalizedMethod} ${normalizedPath}`,
    );
  }
}

export function readFoundationConfig(env, options = {}) {
  const required = [
    "FOUNDATION_INIT_API_BASE_URL",
    "FOUNDATION_INIT_CREDENTIALS_FILE",
    "FOUNDATION_INIT_REGISTRY_PATH",
    "FOUNDATION_INIT_EVIDENCE_PATH",
  ];
  for (const key of required) {
    if (!hasText(env?.[key])) {
      throw new Error(`缺少必填环境变量 ${key}`);
    }
  }
  const readFile = options.readFile ?? ((path) => readFileSync(path, "utf8"));
  const credentials = parseJson(
    readFile(env.FOUNDATION_INIT_CREDENTIALS_FILE),
    "受控账号文件",
  );
  const registry = parseJson(
    readFile(env.FOUNDATION_INIT_REGISTRY_PATH),
    "基础权威来源目录",
  );
  validateFoundationRegistry(registry);

  const tenantId = requireText(credentials?.tenantId, "credentials.tenantId");
  if (tenantId !== "t-1") {
    throw new Error("基础平台知识初始化只允许在平台主源租户 t-1 执行");
  }
  const sourceUsername = hasText(env.FOUNDATION_INIT_SOURCE_ACTOR)
    ? env.FOUNDATION_INIT_SOURCE_ACTOR.trim()
    : "knowledge-source-steward";
  const governorUsername = hasText(env.FOUNDATION_INIT_GOVERNOR_ACTOR)
    ? env.FOUNDATION_INIT_GOVERNOR_ACTOR.trim()
    : "platform-owner";
  if (sourceUsername === governorUsername) {
    throw new Error("来源登记人与治理人必须分离");
  }

  const sourceActor = findAccount(
    credentials.accounts,
    sourceUsername,
    tenantId,
  );
  const governorActor = findAccount(
    credentials.accounts,
    governorUsername,
    tenantId,
  );
  return {
    apiBaseUrl: normalizeBaseUrl(env.FOUNDATION_INIT_API_BASE_URL),
    credentialsPath: env.FOUNDATION_INIT_CREDENTIALS_FILE.trim(),
    registryPath: env.FOUNDATION_INIT_REGISTRY_PATH.trim(),
    evidencePath: env.FOUNDATION_INIT_EVIDENCE_PATH.trim(),
    tenantId,
    sourceActor,
    governorActor,
    registry,
    dryRun: /^true$/i.test(env.FOUNDATION_INIT_DRY_RUN ?? ""),
  };
}

export function validateFoundationRegistry(registry) {
  if (!registry || typeof registry !== "object" || Array.isArray(registry)) {
    throw new Error("基础权威来源目录必须是 JSON 对象");
  }
  for (const field of [
    "schemaVersion",
    "registryCode",
    "releaseVersion",
    "batchCode",
    "idempotencyKey",
    "templateVersion",
    "publishedAt",
    "checkedAt",
    "statement",
  ]) {
    requireText(registry[field], `registry.${field}`);
  }
  if (!semanticVersion(registry.schemaVersion)) {
    throw new Error("registry.schemaVersion 必须使用 major.minor.patch");
  }
  if (!semanticVersion(registry.releaseVersion)) {
    throw new Error("registry.releaseVersion 必须使用 major.minor.patch");
  }
  if (!Array.isArray(registry.coverage)) {
    throw new Error("registry.coverage 必须是数组");
  }
  if (
    registry.coverage.length !== REQUIRED_COVERAGE.length ||
    REQUIRED_COVERAGE.some((item, index) => registry.coverage[index] !== item)
  ) {
    throw new Error("基础权威来源目录覆盖维度不完整或顺序漂移");
  }
  if (!Array.isArray(registry.entries) || registry.entries.length !== 8) {
    throw new Error("基础权威来源目录必须恰好包含 8 个基础目录项");
  }
  if (
    REQUIRED_FOUNDATION_CODES.some(
      (code, index) => registry.entries[index]?.catalogCode !== code,
    )
  ) {
    throw new Error("基础目录项必须按 KNOWGEN 稳定拓扑排序");
  }

  const canonicalIds = new Set();
  const sourceCodes = new Set();
  for (const [index, entry] of registry.entries.entries()) {
    validateRegistryEntry(entry, index, registry.checkedAt);
    if (!canonicalIds.add(entry.canonicalId)) {
      throw new Error(`canonicalId 重复：${entry.canonicalId}`);
    }
    if (!sourceCodes.add(entry.source.sourceCode)) {
      throw new Error(`sourceCode 重复：${entry.source.sourceCode}`);
    }
  }
  for (const entry of registry.entries) {
    for (const dependency of entry.dependencyCanonicalIds) {
      if (!canonicalIds.has(dependency)) {
        throw new Error(
          `基础目录存在孤儿依赖：${entry.canonicalId} -> ${dependency}`,
        );
      }
      if (dependency === entry.canonicalId) {
        throw new Error(`基础目录存在自引用：${entry.canonicalId}`);
      }
    }
  }
  validateDependencyCycles(registry.entries);
  return registry;
}

export function buildInitializationDraft(registry, candidateRefs) {
  validateFoundationRegistry(registry);
  if (!(candidateRefs instanceof Map)) {
    throw new Error("candidateRefs 必须是 Map");
  }
  const entries = registry.entries.map((entry) => {
    const candidateRef = candidateRefs.get(entry.canonicalId);
    if (!hasText(candidateRef)) {
      throw new Error(`缺少候选引用：${entry.canonicalId}`);
    }
    return {
      catalogCode: entry.catalogCode,
      canonicalId: entry.canonicalId,
      namespace: entry.namespace,
      assetVersion: entry.assetVersion,
      candidateRef: candidateRef.trim(),
      dependencyCanonicalIds: entry.dependencyCanonicalIds,
      parentCanonicalId: null,
      unitDimension: null,
      conversionTargetCanonicalId: null,
      sourcePolicy: entry.sourcePolicy,
      reviewPolicy: entry.reviewPolicy,
      testEvidenceRef: entry.testEvidenceRef,
      ownerRole: entry.ownerRole,
      runtimeConsumers: entry.runtimeConsumers,
      rollbackStrategy: entry.rollbackStrategy,
      changeType: "NEW",
      replacementCanonicalId: null,
      effectiveTo: null,
    };
  });
  return {
    batchCode: registry.batchCode,
    releaseType: "FOUNDATION",
    releaseVersion: registry.releaseVersion,
    foundationReleaseVersion: null,
    phase: "F8",
    declaredSourceFileCount: registry.entries.length,
    declaredEntryCount: entries.length,
    coverage: registry.coverage,
    templateVersion: registry.templateVersion,
    modelVersion: null,
    summary:
      "F8 基础目录 B0 候选冻结：仅包含权威来源元数据、结构模板和治理责任，全部待人工逐条审核，不代表基础医学知识已完成。",
    idempotencyKey: registry.idempotencyKey,
    entries,
  };
}

export function redactFoundationEvidence(value) {
  if (Array.isArray(value)) {
    return value.map(redactFoundationEvidence);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      SENSITIVE_KEY.test(key) && !SAFETY_BOOLEAN_KEYS.has(key)
        ? "[REDACTED]"
        : redactFoundationEvidence(item),
    ]),
  );
}

export function writeFoundationEvidenceAtomic(outputPath, evidence) {
  const normalized = requireText(outputPath, "outputPath");
  const parent = dirname(normalized);
  const temporary = `${normalized}.${process.pid}.tmp`;
  mkdirSync(parent, { recursive: true });
  try {
    writeFileSync(
      temporary,
      `${JSON.stringify(redactFoundationEvidence(evidence), null, 2)}\n`,
      {
        encoding: "utf8",
        mode: 0o600,
      },
    );
    renameSync(temporary, normalized);
  } finally {
    if (existsSync(temporary)) {
      rmSync(temporary, { force: true });
    }
  }
}

export async function runFoundationInitialization(options) {
  const registry = validateFoundationRegistry(options?.registry);
  const apiBaseUrl = normalizeBaseUrl(options?.apiBaseUrl);
  const tenantId = requireText(options?.tenantId, "tenantId");
  if (tenantId !== "t-1") {
    throw new Error("基础平台知识初始化只允许在平台主源租户 t-1 执行");
  }
  const sourceActor = normalizeActor(
    options?.sourceActor,
    "sourceActor",
    tenantId,
  );
  const governorActor = normalizeActor(
    options?.governorActor,
    "governorActor",
    tenantId,
  );
  if (sourceActor.username === governorActor.username) {
    throw new Error("来源登记人与治理人必须分离");
  }
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function") {
    throw new Error("当前 Node.js 运行时不支持 fetch");
  }
  const startedAt = currentTime(options?.now);
  const requests = [];
  const evidence = {
    status: "BLOCKED",
    stage: "FOUNDATION_B0_INITIALIZATION",
    startedAt,
    finishedAt: startedAt,
    tenantId,
    registryCode: registry.registryCode,
    registryVersion: registry.releaseVersion,
    batchCode: registry.batchCode,
    batch: null,
    regressionCases: [],
    sources: [],
    candidates: [],
    requests,
    safety: {
      containsCredentials: false,
      containsPatientData: false,
      providerEnableAttempted: false,
      p6MutationAttempted: false,
      automatedMedicalReviewAttempted: false,
      automatedExpertSignOff: false,
    },
  };

  const governorSession = await login({
    fetchImpl,
    requests,
    apiBaseUrl,
    actor: governorActor,
    tenantId,
  });
  const existing = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: governorSession,
    method: "GET",
    path: "/engine/knowledge-production/initialization/batches",
    label: "读取初始化批次",
  });
  const existingBatch = requireArray(existing.data, "初始化批次列表").find(
    (batch) => batch?.batchCode === registry.batchCode,
  );
  if (existingBatch) {
    const view = await requestJson({
      fetchImpl,
      requests,
      apiBaseUrl,
      session: governorSession,
      method: "GET",
      path:
        "/engine/knowledge-production/initialization/batches/" +
        encodeURIComponent(registry.batchCode),
      label: "复核既有初始化批次",
    });
    evidence.batch = assertFrozenReviewBatch(view.data, registry);
    evidence.status = "REUSED";
    evidence.finishedAt = currentTime(options?.now);
    return redactFoundationEvidence(evidence);
  }

  const sourceSession = await login({
    fetchImpl,
    requests,
    apiBaseUrl,
    actor: sourceActor,
    tenantId,
  });
  evidence.regressionCases = await ensureB0RegressionCases({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: governorSession,
    registry,
  });

  const candidateRefs = new Map();
  for (const entry of registry.entries) {
    const result = await initializeEntry({
      fetchImpl,
      requests,
      apiBaseUrl,
      tenantId,
      registry,
      entry,
      sourceActor,
      sourceSession,
      governorSession,
    });
    evidence.sources.push(result.sourceEvidence);
    evidence.candidates.push(result.candidateEvidence);
    candidateRefs.set(entry.canonicalId, result.candidateRef);
  }

  const draft = buildInitializationDraft(registry, candidateRefs);
  const preview = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: governorSession,
    method: "POST",
    path: "/engine/knowledge-production/initialization/batches/preview",
    body: draft,
    label: "预览初始化批次",
  });
  assertPreview(preview.data, registry);
  const hashes = preview.data.hashes;
  const created = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: governorSession,
    method: "POST",
    path: "/engine/knowledge-production/initialization/batches",
    body: {
      draft,
      expectedSourceManifestHash: hashes.sourceManifestHash,
      expectedCandidateManifestHash: hashes.candidateManifestHash,
      expectedOverallHash: hashes.overallHash,
    },
    label: "创建初始化冻结批次",
  });
  evidence.batch = assertFrozenReviewBatch(created.data, registry);
  evidence.status = "CREATED";
  evidence.finishedAt = currentTime(options?.now);
  return redactFoundationEvidence(evidence);
}

async function initializeEntry({
  fetchImpl,
  requests,
  apiBaseUrl,
  tenantId,
  registry,
  entry,
  sourceActor,
  sourceSession,
  governorSession,
}) {
  const context = apiContext(tenantId, sourceActor, registry);
  const sourceResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: sourceSession,
    method: "POST",
    path: "/engine/knowledge/sources",
    body: {
      ...context,
      sourceCode: entry.source.sourceCode,
      sourceType: entry.source.sourceType,
      authorityLevel: entry.source.authorityLevel,
      authorityBasis: entry.source.authorityBasis,
      title: entry.source.title,
      publisher: entry.source.publisher,
      license: entry.source.license,
      language: entry.source.language,
    },
    label: `${entry.catalogCode} 登记来源`,
  });
  const sourceDocument = sourceResponse.data;
  assertSourceDocument(sourceDocument, entry);

  const sourceContent = stableJson({
    registryCode: registry.registryCode,
    registryVersion: registry.releaseVersion,
    checkedAt: registry.checkedAt,
    statement: registry.statement,
    entry,
  });
  const contentHash = sha256(sourceContent);
  const versionResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: sourceSession,
    method: "POST",
    path: `/engine/knowledge/sources/${sourceDocument.id}/versions`,
    body: {
      ...context,
      versionNo: entry.source.versionNo,
      publishedAt: entry.source.publishedAt,
      contentHash,
      fileUri: entry.source.fileUri,
      language: entry.source.language,
      content: sourceContent,
    },
    label: `${entry.catalogCode} 登记来源版本`,
  });
  const sourceVersion = versionResponse.data;
  if (
    !sourceVersion?.id ||
    sourceVersion.versionNo !== entry.source.versionNo ||
    sourceVersion.contentHash !== contentHash
  ) {
    throw new Error(`${entry.catalogCode} 来源版本返回事实与清单不一致`);
  }

  const fragmentResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: sourceSession,
    method: "POST",
    path: "/engine/knowledge/sources/fragments",
    body: {
      sourceVersionId: sourceVersion.id,
      anchorPath: entry.source.anchorPath,
      anchorLabel: entry.source.anchorLabel,
      textExcerpt: entry.source.textExcerpt,
    },
    label: `${entry.catalogCode} 登记来源锚点`,
  });
  const fragment = fragmentResponse.data;
  if (
    !fragment?.id ||
    fragment.sourceVersionId !== sourceVersion.id ||
    fragment.anchorPath !== entry.source.anchorPath ||
    fragment.textExcerpt !== entry.source.textExcerpt
  ) {
    throw new Error(`${entry.catalogCode} 来源锚点返回事实与清单不一致`);
  }

  const approvalResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: governorSession,
    method: "POST",
    path:
      "/engine/knowledge-production/initialization/source-versions/" +
      `${sourceVersion.id}/approval`,
    body: {
      reason:
        "已核对受控注册表、官方链接、访问条件、许可边界和精确内容摘要；本批准只覆盖来源元数据，不批准医学正文。",
    },
    label: `${entry.catalogCode} 独立批准来源版本`,
  });
  if (
    approvalResponse.data?.status !== "APPROVED" ||
    approvalResponse.data?.sourceHash !== contentHash
  ) {
    throw new Error(`${entry.catalogCode} 来源版本未形成精确摘要批准`);
  }

  const candidateRef = await findOrGenerateCandidate({
    fetchImpl,
    requests,
    apiBaseUrl,
    session: sourceSession,
    sourceVersionId: sourceVersion.id,
    entry,
  });
  return {
    candidateRef,
    sourceEvidence: {
      catalogCode: entry.catalogCode,
      sourceDocumentId: sourceDocument.id,
      sourceVersionId: sourceVersion.id,
      sourceCode: entry.source.sourceCode,
      versionNo: sourceVersion.versionNo,
      contentHash,
      anchorPath: fragment.anchorPath,
      approvalStatus: approvalResponse.data.status,
    },
    candidateEvidence: {
      catalogCode: entry.catalogCode,
      canonicalId: entry.canonicalId,
      assetType: entry.assetType,
      candidateRef,
      expectedRiskLevel: "MEDIUM",
      medicalContentStatus: "PENDING_AUTHORING",
      generatedByModel: false,
    },
  };
}

async function findOrGenerateCandidate({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  sourceVersionId,
  entry,
}) {
  const identityResponse = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "GET",
    path:
      "/engine/knowledge/identities/by-code/" +
      encodeURIComponent(entry.canonicalId),
    label: `${entry.catalogCode} 查询既有身份`,
    acceptedStatuses: [200, 404],
  });
  const expectedVersionNo = `draft-from-${entry.source.versionNo}`;
  let target;
  if (identityResponse.status === 200) {
    const identity = identityResponse.data;
    const existingRef = await findExistingCandidateRef({
      fetchImpl,
      requests,
      apiBaseUrl,
      session,
      identity,
      expectedVersionNo,
      entry,
    });
    if (existingRef) {
      return existingRef;
    }
    target = { targetIdentityId: identity.id, newIdentity: null };
  } else {
    target = {
      targetIdentityId: null,
      newIdentity: {
        domain: entry.domain,
        subject: entry.subject,
        identityCode: entry.canonicalId,
      },
    };
  }

  const generated = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "POST",
    path: "/engine/knowledge-production/generate",
    body: {
      sourceVersionId,
      targetPipeline: "PLATFORM_SOURCE",
      domain: entry.domain,
      items: [{ assetType: entry.assetType, target }],
    },
    label: `${entry.catalogCode} 生成 B0 候选`,
  });
  const summary = generated.data;
  if (
    !summary ||
    !Array.isArray(summary.candidates) ||
    summary.candidates.length !== 1 ||
    (summary.blocked?.length ?? 0) !== 0 ||
    (summary.skipped?.length ?? 0) !== 0
  ) {
    throw new Error(`${entry.catalogCode} B0 候选未唯一生成或被门禁阻断`);
  }
  const candidate = summary.candidates[0];
  if (
    candidate.assetType !== entry.assetType ||
    !hasText(candidate.candidateRef)
  ) {
    throw new Error(`${entry.catalogCode} B0 候选响应不完整`);
  }
  return candidate.candidateRef.trim();
}

async function findExistingCandidateRef({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  identity,
  expectedVersionNo,
  entry,
}) {
  if (!identity?.id || identity.identityCode !== entry.canonicalId) {
    throw new Error(`${entry.catalogCode} 既有身份与 canonical ID 不一致`);
  }
  const response = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "GET",
    path: `/engine/knowledge/identities/${identity.id}/candidates?page=1&size=200`,
    label: `${entry.catalogCode} 查询既有候选`,
  });
  const candidates = requireArray(
    response.data?.candidates?.items,
    `${entry.catalogCode} 候选列表`,
  ).filter((candidate) => candidate?.versionNo === expectedVersionNo);
  if (candidates.length > 1) {
    throw new Error(`${entry.catalogCode} 存在多个同版本待审候选`);
  }
  return candidates.length === 1
    ? `kv:${identity.id}:${expectedVersionNo}`
    : null;
}

async function ensureB0RegressionCases({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  registry,
}) {
  const listed = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    session,
    method: "GET",
    path: "/model-evaluations/regression-cases?enabledFlag=Y",
    label: "读取启用的影子评测基准",
  });
  const existing = requireArray(listed.data, "影子评测基准列表");
  const firstByType = new Map();
  for (const entry of registry.entries) {
    if (!firstByType.has(entry.assetType)) {
      firstByType.set(entry.assetType, entry);
    }
  }
  const evidence = [];
  for (const [assetType, entry] of firstByType) {
    const capabilityCode = `knowledge.production.${assetType.toLowerCase()}`;
    const sourceReference = `${entry.source.sourceCode}:${entry.source.versionNo}:${entry.source.anchorPath}`;
    const matching = existing.find(
      (item) =>
        item?.capabilityCode === capabilityCode &&
        item?.caseVersion === registry.releaseVersion &&
        item?.expectedPhrase === "PENDING_AUTHORING" &&
        item?.sourceReference === sourceReference &&
        item?.enabledFlag === "Y",
    );
    if (matching) {
      evidence.push({
        id: matching.id,
        capabilityCode,
        caseVersion: matching.caseVersion,
        status: "REUSED",
      });
      continue;
    }
    const created = await requestJson({
      fetchImpl,
      requests,
      apiBaseUrl,
      session,
      method: "POST",
      path: "/model-evaluations/regression-cases",
      body: {
        capabilityCode,
        caseDomain: "B0_FOUNDATION_INITIALIZATION",
        caseInput: `${assetType} 基础初始化候选必须保持 B0 待编著结构，不得生成医学正文。`,
        expectedPhrase: "PENDING_AUTHORING",
        expectedTerms: ["B0_TEMPLATE"],
        forbiddenAssertions: [
          '"medicalContentStatus":"COMPLETE"',
          '"generatedByModel":true',
        ],
        minScore: 100,
        redLineType: null,
        citationRequired: false,
        caseVersion: registry.releaseVersion,
        sourceReference,
        enabled: true,
      },
      label: `${assetType} 建立 B0 结构影子基准`,
    });
    evidence.push({
      id: created.data?.id,
      capabilityCode,
      caseVersion: registry.releaseVersion,
      status: "CREATED",
    });
  }
  return evidence;
}

function assertFrozenReviewBatch(view, registry) {
  const batch = view?.batch;
  const items = requireArray(view?.items, "初始化批次条目");
  if (
    !batch ||
    batch.batchCode !== registry.batchCode ||
    batch.status !== "IN_REVIEW" ||
    batch.candidateCount !== registry.entries.length ||
    batch.mediumCount !== registry.entries.length ||
    items.length !== registry.entries.length
  ) {
    throw new Error("初始化批次不是预期的 8 条 MEDIUM IN_REVIEW 冻结状态");
  }
  const expectedIds = new Set(
    registry.entries.map((entry) => entry.canonicalId),
  );
  if (
    items.some(
      (item) =>
        !expectedIds.has(item?.canonicalId) ||
        item?.riskLevel !== "MEDIUM" ||
        item?.status !== "PENDING_REVIEW",
    )
  ) {
    throw new Error("初始化批次包含非 MEDIUM 或非待审条目");
  }
  return {
    batchCode: batch.batchCode,
    status: batch.status,
    candidateCount: batch.candidateCount,
    mediumCount: batch.mediumCount,
    overallHash: batch.overallHash ?? null,
    pendingCanonicalIds: items.map((item) => item.canonicalId),
  };
}

function assertPreview(preview, registry) {
  if (
    !preview ||
    preview.sourceCount !== registry.entries.length ||
    preview.candidateCount !== registry.entries.length ||
    preview.lowCount !== 0 ||
    preview.mediumCount !== registry.entries.length ||
    preview.highCount !== 0
  ) {
    throw new Error("初始化预览风险统计或来源数量不符合 8 条 MEDIUM 候选");
  }
  for (const field of [
    "sourceManifestHash",
    "candidateManifestHash",
    "overallHash",
  ]) {
    if (!hash(preview.hashes?.[field])) {
      throw new Error(`初始化预览缺少合法 ${field}`);
    }
  }
}

function assertSourceDocument(sourceDocument, entry) {
  if (
    !sourceDocument?.id ||
    sourceDocument.sourceCode !== entry.source.sourceCode ||
    sourceDocument.sourceType !== entry.source.sourceType ||
    sourceDocument.authorityLevel !== entry.source.authorityLevel ||
    sourceDocument.title !== entry.source.title ||
    sourceDocument.publisher !== entry.source.publisher ||
    sourceDocument.license !== entry.source.license
  ) {
    throw new Error(`${entry.catalogCode} 既有来源事实与受控清单不一致`);
  }
}

async function login({ fetchImpl, requests, apiBaseUrl, actor, tenantId }) {
  const response = await requestJson({
    fetchImpl,
    requests,
    apiBaseUrl,
    method: "POST",
    path: "/auth/login",
    body: {
      tenantId,
      username: actor.username,
      password: actor.password,
    },
    label: `${actor.username} 登录`,
  });
  return authenticatedSession(response.headers);
}

async function requestJson({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  method,
  path,
  body,
  label,
  acceptedStatuses = [200],
}) {
  assertAllowedFoundationRequest(method, path);
  const normalizedMethod = method.toUpperCase();
  const headers = {
    Accept: "application/json",
    "X-Trace-Id": `foundation-init-${randomUUID()}`,
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (session) {
    headers.Cookie = session.cookie;
    if (!["GET", "HEAD", "OPTIONS"].includes(normalizedMethod)) {
      headers["X-XSRF-TOKEN"] = session.xsrf;
    }
  }
  let response;
  try {
    response = await fetchImpl(`${apiBaseUrl}${path}`, {
      method: normalizedMethod,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    throw new Error(`${label}请求失败：${safeMessage(error)}`);
  }
  requests.push({ method: normalizedMethod, path, status: response.status });
  if (!acceptedStatuses.includes(response.status)) {
    throw new Error(`${label}返回 HTTP ${response.status}`);
  }
  const contentType = response.headers.get("content-type") ?? "";
  let parsed = null;
  if (contentType.includes("application/json")) {
    const text = await response.text();
    parsed = text ? parseJson(text, `${label}响应`) : null;
  } else if (response.status === 200) {
    throw new Error(`${label}响应不是 JSON`);
  }
  if (response.status === 200 && parsed?.success === false) {
    throw new Error(`${label}业务响应失败`);
  }
  return {
    status: response.status,
    data: parsed?.data ?? null,
    body: parsed,
    headers: response.headers,
  };
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

function validateRegistryEntry(entry, index, checkedAt) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    throw new Error(`registry.entries[${index}] 必须是对象`);
  }
  for (const field of [
    "catalogCode",
    "canonicalId",
    "namespace",
    "assetVersion",
    "assetType",
    "domain",
    "subject",
    "medicalContentStatus",
    "sourcePolicy",
    "reviewPolicy",
    "testEvidenceRef",
    "ownerRole",
    "runtimeConsumers",
    "rollbackStrategy",
  ]) {
    requireText(entry[field], `registry.entries[${index}].${field}`);
  }
  if (!semanticVersion(entry.assetVersion)) {
    throw new Error(
      `${entry.catalogCode} assetVersion 必须使用 major.minor.patch`,
    );
  }
  if (!ALLOWED_ASSET_TYPES.has(entry.assetType)) {
    throw new Error(`${entry.catalogCode} 使用了未允许的基础资产类型`);
  }
  if (
    entry.medicalContentStatus !== "PENDING_AUTHORING" ||
    entry.generatedByModel !== false
  ) {
    throw new Error(`${entry.catalogCode} 必须保持 B0 待编著且非模型生成`);
  }
  if (!Array.isArray(entry.dependencyCanonicalIds)) {
    throw new Error(`${entry.catalogCode} dependencyCanonicalIds 必须是数组`);
  }
  const source = entry.source;
  if (!source || typeof source !== "object") {
    throw new Error(`${entry.catalogCode} 缺少受控来源`);
  }
  for (const field of [
    "sourceCode",
    "sourceType",
    "authorityLevel",
    "authorityBasis",
    "title",
    "publisher",
    "license",
    "language",
    "versionNo",
    "publishedAt",
    "fileUri",
    "anchorPath",
    "anchorLabel",
    "textExcerpt",
  ]) {
    requireText(source[field], `${entry.catalogCode}.source.${field}`);
  }
  if (
    source.authorityLevel !== "D_HOSPITAL" ||
    !source.fileUri.startsWith("file:///zoesoft/medkernel/conf/knowledge-init/")
  ) {
    throw new Error(`${entry.catalogCode} 来源必须明确为内部受控注册表文件`);
  }
  if (!semanticVersion(source.versionNo)) {
    throw new Error(`${entry.catalogCode} 来源版本必须使用 major.minor.patch`);
  }
  if (
    !Array.isArray(entry.officialReferences) ||
    entry.officialReferences.length === 0
  ) {
    throw new Error(`${entry.catalogCode} 至少需要一个官方原始来源`);
  }
  for (const reference of entry.officialReferences) {
    for (const field of [
      "title",
      "publisher",
      "url",
      "release",
      "checkedAt",
      "accessPolicy",
    ]) {
      requireText(
        reference?.[field],
        `${entry.catalogCode}.reference.${field}`,
      );
    }
    if (
      !reference.url.startsWith("https://") ||
      reference.checkedAt !== checkedAt
    ) {
      throw new Error(`${entry.catalogCode} 官方来源 URL 或检索日期不合规`);
    }
  }
}

function validateDependencyCycles(entries) {
  const byId = new Map(entries.map((entry) => [entry.canonicalId, entry]));
  const visited = new Set();
  const active = new Set();
  const visit = (canonicalId) => {
    if (visited.has(canonicalId)) return;
    if (!active.add(canonicalId)) {
      throw new Error(`基础目录存在循环依赖：${canonicalId}`);
    }
    for (const dependency of byId.get(canonicalId).dependencyCanonicalIds) {
      visit(dependency);
    }
    active.delete(canonicalId);
    visited.add(canonicalId);
  };
  for (const canonicalId of byId.keys()) visit(canonicalId);
}

function apiContext(tenantId, actor, registry) {
  const traceId = `foundation-init-${randomUUID()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: tenantId,
    group_id: null,
    hospital_id: null,
    campus_id: null,
    site_id: null,
    department_id: null,
    specialty_id: null,
    user_id: actor.username,
    role_codes: actor.role ? [actor.role] : [],
    package_version: registry.releaseVersion,
  };
}

function normalizeActor(actor, name, tenantId) {
  return {
    tenantId,
    username: requireText(actor?.username, `${name}.username`),
    password: requireText(actor?.password, `${name}.password`),
    role: hasText(actor?.role) ? actor.role.trim() : null,
  };
}

function findAccount(accounts, username, tenantId) {
  if (!Array.isArray(accounts)) {
    throw new Error("受控账号文件缺少 accounts 数组");
  }
  const account = accounts.find((item) => item?.username === username);
  if (!account) {
    throw new Error(`受控账号文件缺少 ${username}`);
  }
  if (!hasText(account.password)) {
    throw new Error(`${username} 尚未形成正式受控密码，不允许用于知识初始化`);
  }
  return normalizeActor(account, username, tenantId);
}

function normalizeBaseUrl(value) {
  const text = requireText(value, "apiBaseUrl").replace(/\/+$/, "");
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error("FOUNDATION_INIT_API_BASE_URL 不是合法 URL");
  }
  if (url.username || url.password) {
    throw new Error("API URL 不得包含内嵌凭据");
  }
  if (
    url.protocol !== "https:" &&
    !(
      url.protocol === "http:" &&
      ["127.0.0.1", "localhost", "::1"].includes(url.hostname)
    )
  ) {
    throw new Error("知识初始化 API 必须使用 HTTPS 或本机回环 HTTP");
  }
  return text;
}

function stableJson(value) {
  return JSON.stringify(sortValue(value));
}

function sortValue(value) {
  if (Array.isArray(value)) {
    return value.map(sortValue);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  return Object.fromEntries(
    Object.keys(value)
      .sort()
      .map((key) => [key, sortValue(value[key])]),
  );
}

function sha256(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function parseJson(value, label) {
  try {
    return JSON.parse(value);
  } catch {
    throw new Error(`${label}不是合法 JSON`);
  }
}

function requireArray(value, label) {
  if (!Array.isArray(value)) {
    throw new Error(`${label}必须是数组`);
  }
  return value;
}

function requireRelativePath(value) {
  const path = requireText(value, "path");
  if (!path.startsWith("/") || path.startsWith("//")) {
    throw new Error("API path 必须是站内绝对路径");
  }
  const url = new URL(path, "https://medkernel.invalid");
  if (url.origin !== "https://medkernel.invalid") {
    throw new Error("API path 不得跳转到外部地址");
  }
  return `${url.pathname}${url.search}`;
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) {
    throw new Error(`${label}不能为空`);
  }
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function semanticVersion(value) {
  return typeof value === "string" && /^\d+\.\d+\.\d+$/.test(value);
}

function hash(value) {
  return typeof value === "string" && /^[0-9a-f]{64}$/.test(value);
}

function currentTime(now) {
  const value = typeof now === "function" ? now() : new Date().toISOString();
  return typeof value === "string" ? value : new Date(value).toISOString();
}

function splitSetCookie(value) {
  return value ? value.split(/,(?=\s*[^;,]+=)/) : [];
}

function safeMessage(error) {
  return error instanceof Error ? error.message : String(error);
}
