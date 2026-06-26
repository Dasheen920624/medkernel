import { createHash, randomUUID } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  selectLaunchAccount,
  validateLaunchCredentials,
} from "../release/launch-account-bootstrap-lib.mjs";

export const FULL_KNOWLEDGE_DOMAINS = Object.freeze([
  "GUIDELINE",
  "DRUG",
  "PATHWAY_KNOWLEDGE",
  "NURSING",
  "DIAGNOSTIC_ITEM",
  "TCM",
  "PROTOCOL",
  "POLICY",
  "LITERATURE",
  "OTHER",
  "DIAGNOSIS",
]);

const DOMAIN_SET = new Set(FULL_KNOWLEDGE_DOMAINS);
const PRODUCTION_DOMAINS = new Set([
  "CLINICAL",
  "PHARMACY",
  "TERMINOLOGY_REPORT",
  "EVALUATION_INSURANCE",
  "GENERAL",
]);
const SOURCE_TYPES = new Set([
  "GUIDELINE",
  "DRUG_LABEL",
  "STANDARD",
  "POLICY",
  "HOSPITAL_PROTOCOL",
  "TCM_CLASSIC",
  "LITERATURE",
  "CONSENSUS",
  "OTHER",
]);
const AUTHORITY_LEVELS = new Set([
  "A_REGULATION",
  "B_GUIDELINE",
  "C_CONSENSUS_LITERATURE",
  "D_HOSPITAL",
  "E_FEEDBACK",
]);
const SENSITIVE_KEY =
  /password|cookie|token|secret|credential|recovery|mfa|otp|totp|patientData|patient_data/i;
const SAFE_BOOLEAN_KEYS = new Set([
  "mfaRequired",
  "containsCredentials",
  "containsPatientData",
  "clinicalActionGenerated",
  "automatedOrderGenerated",
]);
const CAPABILITY = "knowledge.production.knowledge";
const REPO_ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const API_ALLOWLIST = Object.freeze([
  ["POST", /^\/auth\/login$/u],
  ["GET", /^\/engine\/knowledge-production\/asset-templates$/u],
  ["GET", /^\/engine\/knowledge-production\/readiness\?/u],
  ["POST", /^\/engine\/knowledge-production\/jobs$/u],
  ["POST", /^\/engine\/knowledge-production\/jobs\/[^/?]+\/model-candidates$/u],
  ["POST", /^\/engine\/knowledge-production\/jobs\/[^/?]+\/publication-quality-records$/u],
  ["GET", /^\/engine\/knowledge-production\/jobs\/[^/?]+\/(gate-results|triage-results|shadow-runs)$/u],
  ["POST", /^\/engine\/knowledge-production\/jobs\/[^/?]+\/complete$/u],
  ["POST", /^\/engine\/knowledge\/sources$/u],
  ["POST", /^\/engine\/knowledge\/sources\/\d+\/versions$/u],
  ["POST", /^\/engine\/knowledge\/sources\/fragments$/u],
  ["POST", /^\/engine\/knowledge\/citations$/u],
  ["GET", /^\/engine\/knowledge\/identities\/by-code\/[^/?]+$/u],
  ["GET", /^\/engine\/knowledge\/identities\/\d+\/(candidates|versions)(\?|$)/u],
  ["GET", /^\/engine\/knowledge\/identities\/\d+\/(active|provenance|citations|source-evidence)$/u],
  ["POST", /^\/engine\/knowledge\/candidates\/\d+\/review$/u],
  ["POST", /^\/engine\/knowledge\/identities\/\d+\/versions\/\d+\/activate$/u],
]);

export function validateFullKnowledgeManifest(manifest) {
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error("全知识演练清单必须是 JSON 对象");
  }
  for (const field of [
    "schemaVersion",
    "manifestCode",
    "releaseVersion",
    "checkedAt",
    "capabilityCode",
    "rollbackIdentityCode",
    "statement",
  ]) {
    requireText(manifest[field], `manifest.${field}`);
  }
  if (!semanticVersion(manifest.schemaVersion)) {
    throw new Error("manifest.schemaVersion 必须使用 major.minor.patch");
  }
  if (!semanticVersion(manifest.releaseVersion)) {
    throw new Error("manifest.releaseVersion 必须使用 major.minor.patch");
  }
  if (manifest.capabilityCode !== CAPABILITY) {
    throw new Error(`正式知识能力码必须为 ${CAPABILITY}`);
  }
  if (!Array.isArray(manifest.entries) || manifest.entries.length !== DOMAIN_SET.size) {
    throw new Error("全知识演练必须恰好包含 11 个知识域条目");
  }

  const domains = new Set();
  const identityCodes = new Set();
  const sourceCodes = new Set();
  for (const [index, entry] of manifest.entries.entries()) {
    validateEntry(entry, index, manifest.checkedAt);
    unique(domains, entry.domain, "知识域");
    unique(identityCodes, entry.identityCode, "知识身份编码");
    unique(sourceCodes, entry.source.sourceCode, "来源编码");
  }
  if (
    domains.size !== DOMAIN_SET.size ||
    FULL_KNOWLEDGE_DOMAINS.some((domain) => !domains.has(domain))
  ) {
    throw new Error("全知识演练没有完整覆盖 11 个 KnowledgeDomain");
  }
  if (!identityCodes.has(manifest.rollbackIdentityCode)) {
    throw new Error("回滚代表知识必须来自当前 11 域清单");
  }
  return manifest;
}

export function buildModelPrompt(entry, template, revisionNote = "V1 初始候选") {
  validateTemplate(template, entry.domain);
  const sections = template.sections.map((section) => ({
    key: section.key,
    label: section.label,
    required: Boolean(section.required),
  }));
  const sourceRef = `${entry.source.sourceCode}:${entry.source.versionNo}:${entry.source.anchorPath}`;
  const templateJson = {
    domain: entry.domain,
    subject: entry.subject,
    clinicalActionable: false,
    sourceReferences: [
      {
        sourceRef,
        authorityLevel: entry.source.authorityLevel,
        anchorLabel: entry.source.anchorLabel,
      },
    ],
    limitations: [
      "仅用于验证 MedKernel 知识生产流程，不构成诊断、处方、剂量、阈值或自动医嘱。",
      "正式临床内容必须绑定具体原始文件、机构版本、适用范围和人工审核结论。",
    ],
    sections: Object.fromEntries(
      sections.map((section) => [
        section.key,
        `${section.label}：只基于受控来源锚点说明来源边界；不可从当前来源推断的内容必须明确写明不可推断。`,
      ]),
    ),
  };
  return [
    "你是 MedKernel 正式医学知识生产器。只返回一个合法 JSON 对象，不要 Markdown、代码围栏或额外说明；第一个字符必须是 {，最后一个字符必须是 }。",
    `知识域：${entry.domain}；主题：${entry.subject}；版本演练：${revisionNote}。`,
    `唯一受控来源：${sourceRef}。`,
    `来源锚点原文：${entry.source.textExcerpt}`,
    "目标仅是生成低风险的来源说明、结构边界和使用限制；不得补造诊断、剂量、阈值、治疗建议、患者事实或自动医嘱。",
    "每个章节都必须明确：正式临床内容仍须绑定具体原始文件、版本和适用范围；证据不足时写明不可推断。",
    `返回对象字段固定为：domain、subject、clinicalActionable(false)、sourceReferences(数组)、limitations(数组)、sections(对象)。sections 键必须覆盖：${sections
      .map((section) => `${section.key}(${section.label})`)
      .join("、")}。`,
    "必须严格按以下 JSON 模板返回；顶层字段不得增删，domain、subject、clinicalActionable、sourceReferences.sourceRef 必须保持模板值：",
    JSON.stringify(templateJson, null, 2),
  ].join("\n");
}

export function buildRehearsalPlan(manifest) {
  validateFullKnowledgeManifest(manifest);
  return {
    capabilityCode: manifest.capabilityCode,
    v1: manifest.entries.map((entry) => ({
      domain: entry.domain,
      identityCode: entry.identityCode,
      sourceCode: entry.source.sourceCode,
    })),
    v2: {
      identityCode: manifest.rollbackIdentityCode,
      sourceVersionReused: true,
    },
    rollbackSequence: ["V1", "V2", "V1", "V2"],
  };
}

export function buildPublicationQualityRecordRequest({
  candidateRef,
  identityId,
  versionId,
}) {
  return {
    candidateRef: requireText(candidateRef, "candidateRef"),
    identityId: requirePositiveInteger(identityId, "identityId"),
    versionId: requirePositiveInteger(versionId, "versionId"),
  };
}

export function isAcceptableShadowRun(shadowRun) {
  return (
    (shadowRun?.status === "PASSED" || shadowRun?.status === "PENDING_REVIEW") &&
    shadowRun.readyForReview === true &&
    shadowRun.degradationDetected !== true
  );
}

export function readRehearsalConfig(env, options = {}) {
  const readFile = options.readFile ?? ((file) => readFileSync(file, "utf8"));
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  const required = [
    "FULL_KNOWLEDGE_API_BASE_URL",
    "FULL_KNOWLEDGE_CREDENTIALS_FILE",
    "FULL_KNOWLEDGE_MANIFEST_PATH",
    "FULL_KNOWLEDGE_PROVIDER_CODE",
  ];
  for (const key of required) {
    if (!hasText(env?.[key])) throw new Error(`缺少必填环境变量 ${key}`);
  }
  const manifest = parseJson(
    readFile(env.FULL_KNOWLEDGE_MANIFEST_PATH),
    "全知识演练清单",
  );
  validateFullKnowledgeManifest(manifest);
  const credentials = parseJson(
    readFile(env.FULL_KNOWLEDGE_CREDENTIALS_FILE),
    "受控账号文件",
  );
  validateLaunchCredentials(credentials);
  const tenantId = credentials.platform.tenantId;
  const account = selectLaunchAccount(credentials, "platform", "engine-operator");
  if (account.role !== "engine-operator") {
    throw new Error("正式全知识演练必须使用 engine-operator 职责");
  }
  const evidencePath = resolveEvidencePath(env, repoRoot);
  return {
    apiBaseUrl: normalizeBaseUrl(env.FULL_KNOWLEDGE_API_BASE_URL),
    tenantId,
    operator: account,
    providerCode: requireText(
      env.FULL_KNOWLEDGE_PROVIDER_CODE,
      "FULL_KNOWLEDGE_PROVIDER_CODE",
    ),
    manifest,
    evidencePath,
    dryRun: /^true$/iu.test(env.FULL_KNOWLEDGE_DRY_RUN ?? ""),
  };
}

export function redactEvidence(value) {
  if (Array.isArray(value)) return value.map(redactEvidence);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.entries(value).map(([key, item]) => [
      key,
      SENSITIVE_KEY.test(key) && !SAFE_BOOLEAN_KEYS.has(key)
        ? "[REDACTED]"
        : redactEvidence(item),
    ]),
  );
}

export function writeEvidenceAtomic(outputPath, evidence) {
  const normalized = path.resolve(requireText(outputPath, "outputPath"));
  const temporary = `${normalized}.${process.pid}.tmp`;
  mkdirSync(path.dirname(normalized), { recursive: true });
  try {
    writeFileSync(
      temporary,
      `${JSON.stringify(redactEvidence(evidence), null, 2)}\n`,
      { encoding: "utf8", mode: 0o600 },
    );
    renameSync(temporary, normalized);
  } finally {
    if (existsSync(temporary)) rmSync(temporary, { force: true });
  }
}

export function formatFullKnowledgeProgress(event) {
  if (!event || typeof event !== "object") return "[full-knowledge] 进度事件无效";
  switch (event.type) {
    case "stage-start":
      return `[full-knowledge] 开始全知识演练：${event.total} 个知识域，Provider=${event.providerCode}`;
    case "source-verified":
      return `[full-knowledge] 来源核验 ${event.completed}/${event.total}：${event.domain} 已核验，还剩 ${event.remaining} 个`;
    case "domain-start":
      return `[full-knowledge] 知识生产 ${event.completed + 1}/${event.total}：${event.domain} 开始，剩余 ${event.remaining} 个`;
    case "domain-complete":
      return `[full-knowledge] 知识生产 ${event.completed}/${event.total}：${event.domain} 已发布，模型任务 ${event.modelTaskId} 用时 ${formatDuration(event.modelTaskDurationMs)}，还剩 ${event.remaining} 个`;
    case "version-refresh-start":
      return `[full-knowledge] 代表知识 V2 生产开始：${event.domain}`;
    case "version-refresh-complete":
      return `[full-knowledge] 代表知识 V2 已发布：${event.domain}，模型任务 ${event.modelTaskId} 用时 ${formatDuration(event.modelTaskDurationMs)}`;
    case "rollback-start":
      return `[full-knowledge] 开始回滚与恢复验证：${event.identityCode}`;
    case "rollback-complete":
      return `[full-knowledge] 回滚与恢复验证完成：${event.identityCode}，当前版本 ${event.restoredActiveVersionId}`;
    case "stage-complete":
      return `[full-knowledge] 全知识演练通过：${event.completed}/${event.total} 个知识域，模型任务 ${event.modelTaskCount} 个`;
    default:
      return `[full-knowledge] ${event.type ?? "未知进度"} ${event.domain ?? ""}`.trim();
  }
}

/**
 * 在任何数据库写入前核验演练知识的官方来源。
 *
 * 只接受清单声明的 HTTPS 主机，并要求页面包含全部稳定核验词；返回内容哈希供演练证据留存。
 */
export async function verifyOfficialSource(entry, options = {}) {
  const source = entry?.source;
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function") throw new Error("当前 Node.js 运行时不支持 fetch");
  const sourceUrl = new URL(requireText(source?.url, `${entry?.domain ?? "知识"}.source.url`));
  if (sourceUrl.protocol !== "https:") throw new Error("正式知识来源必须使用 HTTPS");
  const allowedHosts = requireArray(source?.allowedHosts, "来源允许主机")
    .map((host) => requireText(host, "来源允许主机"));
  if (!allowedHosts.includes(sourceUrl.hostname)) {
    throw new Error(`来源主机不在允许主机：${sourceUrl.hostname}`);
  }

  let response;
  try {
    response = await fetchImpl(sourceUrl, {
      method: "GET",
      headers: {
        Accept: "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.1",
        "User-Agent": "MedKernel-Launch-Source-Verification/1.0",
      },
      redirect: "follow",
    });
  } catch (error) {
    throw new Error(`${entry.domain} 官方来源抓取失败：${safeMessage(error)}`);
  }
  if (!response.ok) {
    throw new Error(`${entry.domain} 官方来源返回 HTTP ${response.status}`);
  }
  const effectiveUrl = new URL(options.effectiveUrl ?? response.url ?? source.url);
  if (!allowedHosts.includes(effectiveUrl.hostname)) {
    throw new Error(`来源重定向主机不在允许主机：${effectiveUrl.hostname}`);
  }
  const body = await response.text();
  const normalizedBody = body.toLocaleLowerCase("en-US");
  const verificationTerms = requireArray(source.verificationTerms, "来源核验词")
    .map((term) => requireText(term, "来源核验词"));
  const matchedTerms = verificationTerms.filter((term) =>
    normalizedBody.includes(term.toLocaleLowerCase("en-US")),
  );
  if (matchedTerms.length !== verificationTerms.length) {
    const missing = verificationTerms.filter((term) => !matchedTerms.includes(term));
    throw new Error(`${entry.domain} 官方来源缺少核验词：${missing.join("、")}`);
  }
  return {
    domain: entry.domain,
    status: "VERIFIED",
    sourceUrl: source.url,
    effectiveUrl: effectiveUrl.toString(),
    httpStatus: response.status,
    contentType: response.headers.get("content-type"),
    contentSha256: sha256(body),
    matchedTerms,
    verifiedAt: now(options.now),
  };
}

export async function runFullKnowledgeRehearsal(options) {
  const manifest = validateFullKnowledgeManifest(options?.manifest);
  const fetchImpl = options?.fetchImpl ?? globalThis.fetch;
  if (typeof fetchImpl !== "function") throw new Error("当前 Node.js 运行时不支持 fetch");
  const config = {
    apiBaseUrl: normalizeBaseUrl(options?.apiBaseUrl),
    tenantId: requireText(options?.tenantId, "tenantId"),
    operator: normalizeActor(options?.operator, options?.tenantId),
    providerCode: requireText(options?.providerCode, "providerCode"),
  };
  if (config.tenantId !== "t-1" || config.operator.role !== "engine-operator") {
    throw new Error("正式全知识演练必须由 t-1 的 engine-operator 执行");
  }

  const requests = [];
  const progress = createProgressReporter(options?.onProgress, options?.now);
  const startedAt = now(options?.now);
  const totalDomains = FULL_KNOWLEDGE_DOMAINS.length;
  const evidence = {
    status: "BLOCKED",
    stage: "FULL_FUNCTION_FULL_KNOWLEDGE",
    startedAt,
    finishedAt: startedAt,
    tenantId: config.tenantId,
    manifestCode: manifest.manifestCode,
    releaseVersion: manifest.releaseVersion,
    providerCode: config.providerCode,
    capabilityCode: manifest.capabilityCode,
    coverage: {
      expectedDomains: [...FULL_KNOWLEDGE_DOMAINS],
      publishedDomains: [],
      structuralTemplatesObserved: 0,
    },
    readiness: null,
    sourceVerification: [],
    knowledge: [],
    versionLifecycle: null,
    observability: {
      totalDomains,
      completedDomains: 0,
      remainingDomains: totalDomains,
      modelTasks: [],
    },
    requests,
    safety: {
      containsCredentials: false,
      containsPatientData: false,
      clinicalActionGenerated: false,
      automatedOrderGenerated: false,
      mfaRequired: false,
    },
  };
  progress({
    type: "stage-start",
    total: totalDomains,
    completed: 0,
    remaining: totalDomains,
    providerCode: config.providerCode,
  });

  for (const [index, entry] of manifest.entries.entries()) {
    const verified = await verifyOfficialSource(entry, {
      fetchImpl: options?.sourceFetchImpl ?? fetchImpl,
      now: options?.now,
    });
    evidence.sourceVerification.push(verified);
    progress({
      type: "source-verified",
      domain: entry.domain,
      completed: index + 1,
      total: totalDomains,
      remaining: totalDomains - index - 1,
      sourceUrl: entry.source.url,
      httpStatus: verified.httpStatus,
    });
  }

  const loginResponse = await requestJson({
    ...config,
    fetchImpl,
    requests,
    method: "POST",
    path: "/auth/login",
    body: {
      tenantId: config.tenantId,
      username: config.operator.username,
      password: config.operator.password,
    },
    label: "医疗引擎运营员登录",
  });
  assertLaunchLogin(loginResponse.data, config.operator);
  const session = authenticatedSession(loginResponse.headers);

  const templatesResponse = await requestJson({
    ...config,
    fetchImpl,
    requests,
    session,
    method: "GET",
    path: "/engine/knowledge-production/asset-templates",
    label: "读取专业资产模板",
  });
  const templates = assertTemplateCoverage(templatesResponse.data);
  evidence.coverage.structuralTemplatesObserved = templates.structuralCount;

  const readinessPath =
    "/engine/knowledge-production/readiness?" +
    new URLSearchParams({
      producer: "API_MODEL",
      capabilityCode: manifest.capabilityCode,
      providerCode: config.providerCode,
    });
  const readinessResponse = await requestJson({
    ...config,
    fetchImpl,
    requests,
    session,
    method: "GET",
    path: readinessPath,
    label: "核对正式模型知识生产就绪度",
  });
  evidence.readiness = assertReadiness(readinessResponse.data, config);

  const produced = new Map();
  for (const [index, entry] of manifest.entries.entries()) {
    progress({
      type: "domain-start",
      phase: "V1",
      domain: entry.domain,
      completed: index,
      total: totalDomains,
      remaining: totalDomains - index,
      identityCode: entry.identityCode,
    });
    const source = await prepareSource({
      ...config,
      fetchImpl,
      requests,
      session,
      manifest,
      entry,
    });
    const result = await produceAndPublish({
      ...config,
      fetchImpl,
      requests,
      session,
      manifest,
      entry,
      template: templates.byDomain.get(entry.domain),
      source,
      targetIdentityId: null,
      revisionNote: "V1 初始候选",
      now: options?.now,
    });
    produced.set(entry.identityCode, { source, v1: result });
    const completedDomains = index + 1;
    const knowledgeEvidence = {
      ...result.evidence,
      progress: {
        phase: "V1",
        sequence: completedDomains,
        totalDomains,
        completedDomains,
        remainingDomains: totalDomains - completedDomains,
      },
    };
    evidence.knowledge.push(knowledgeEvidence);
    evidence.coverage.publishedDomains.push(entry.domain);
    evidence.observability.completedDomains = completedDomains;
    evidence.observability.remainingDomains = totalDomains - completedDomains;
    evidence.observability.modelTasks.push(modelTaskProgress(entry.domain, "V1", result.evidence));
    progress({
      type: "domain-complete",
      phase: "V1",
      domain: entry.domain,
      completed: completedDomains,
      total: totalDomains,
      remaining: totalDomains - completedDomains,
      identityCode: entry.identityCode,
      modelTaskId: result.evidence.modelTaskId,
      modelTaskDurationMs: result.evidence.modelTaskDurationMs,
      versionId: result.versionId,
    });
  }

  const rollbackEntry = manifest.entries.find(
    (entry) => entry.identityCode === manifest.rollbackIdentityCode,
  );
  const representative = produced.get(manifest.rollbackIdentityCode);
  progress({
    type: "version-refresh-start",
    phase: "V2",
    domain: rollbackEntry.domain,
    identityCode: rollbackEntry.identityCode,
  });
  const v2 = await produceAndPublish({
    ...config,
    fetchImpl,
    requests,
    session,
    manifest,
    entry: rollbackEntry,
    template: templates.byDomain.get(rollbackEntry.domain),
    source: representative.source,
    targetIdentityId: representative.v1.identityId,
    revisionNote: "V2 演练：补强来源版本、适用边界和不可推断说明",
    now: options?.now,
  });
  evidence.observability.modelTasks.push(modelTaskProgress(rollbackEntry.domain, "V2", v2.evidence));
  progress({
    type: "version-refresh-complete",
    phase: "V2",
    domain: rollbackEntry.domain,
    identityCode: rollbackEntry.identityCode,
    modelTaskId: v2.evidence.modelTaskId,
    modelTaskDurationMs: v2.evidence.modelTaskDurationMs,
    versionId: v2.versionId,
  });
  progress({
    type: "rollback-start",
    domain: rollbackEntry.domain,
    identityCode: rollbackEntry.identityCode,
    v1VersionId: representative.v1.versionId,
    v2VersionId: v2.versionId,
  });
  const rollback = await exerciseRollback({
    ...config,
    fetchImpl,
    requests,
    session,
    identityId: representative.v1.identityId,
    v1VersionId: representative.v1.versionId,
    v1QualityGateRecordId: representative.v1.qualityGateRecordId,
    v2VersionId: v2.versionId,
    v2QualityGateRecordId: v2.qualityGateRecordId,
  });
  progress({
    type: "rollback-complete",
    domain: rollbackEntry.domain,
    identityCode: rollbackEntry.identityCode,
    rollbackActiveVersionId: rollback.rollbackActiveVersionId,
    restoredActiveVersionId: rollback.restoredActiveVersionId,
    finalStatus: rollback.finalStatus,
  });
  evidence.versionLifecycle = {
    identityCode: rollbackEntry.identityCode,
    v1VersionId: representative.v1.versionId,
    v2VersionId: v2.versionId,
    v2ModelTask: modelTaskProgress(rollbackEntry.domain, "V2", v2.evidence),
    ...rollback,
  };

  if (
    new Set(evidence.coverage.publishedDomains).size !== FULL_KNOWLEDGE_DOMAINS.length
  ) {
    throw new Error("正式知识发布结果没有唯一覆盖全部 11 个知识域");
  }
  evidence.status = "PASSED";
  evidence.finishedAt = now(options?.now);
  progress({
    type: "stage-complete",
    status: evidence.status,
    total: totalDomains,
    completed: totalDomains,
    remaining: 0,
    modelTaskCount: evidence.observability.modelTasks.length,
  });
  return redactEvidence(evidence);
}

async function prepareSource(args) {
  const { entry, manifest } = args;
  const context = apiContext(args);
  const sourceDocument = (
    await requestJson({
      ...args,
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
      label: `${entry.domain} 登记官方来源`,
    })
  ).data;
  if (!sourceDocument?.id || sourceDocument.sourceCode !== entry.source.sourceCode) {
    throw new Error(`${entry.domain} 来源登记结果不一致`);
  }
  const content = stableJson({
    manifestCode: manifest.manifestCode,
    releaseVersion: manifest.releaseVersion,
    checkedAt: manifest.checkedAt,
    domain: entry.domain,
    statement: manifest.statement,
    officialSource: entry.source,
  });
  const contentHash = sha256(content);
  const sourceVersion = (
    await requestJson({
      ...args,
      method: "POST",
      path: `/engine/knowledge/sources/${sourceDocument.id}/versions`,
      body: {
        ...context,
        versionNo: entry.source.versionNo,
        publishedAt: entry.source.publishedAt,
        contentHash,
        fileUri: entry.source.url,
        language: entry.source.language,
        content,
      },
      label: `${entry.domain} 登记来源版本`,
    })
  ).data;
  if (
    !sourceVersion?.id ||
    sourceVersion.versionNo !== entry.source.versionNo ||
    sourceVersion.contentHash !== contentHash
  ) {
    throw new Error(`${entry.domain} 来源版本结果不一致`);
  }
  const fragment = (
    await requestJson({
      ...args,
      method: "POST",
      path: "/engine/knowledge/sources/fragments",
      body: {
        sourceVersionId: sourceVersion.id,
        anchorPath: entry.source.anchorPath,
        anchorLabel: entry.source.anchorLabel,
        textExcerpt: entry.source.textExcerpt,
      },
      label: `${entry.domain} 登记来源锚点`,
    })
  ).data;
  if (!fragment?.id || fragment.sourceVersionId !== sourceVersion.id) {
    throw new Error(`${entry.domain} 来源锚点结果不一致`);
  }
  return {
    sourceDocumentId: sourceDocument.id,
    sourceVersionId: sourceVersion.id,
    fragmentId: fragment.id,
    sourceRef: `${entry.source.sourceCode}:${entry.source.versionNo}:${entry.source.anchorPath}`,
    contentHash,
  };
}

async function produceAndPublish(args) {
  const { entry, manifest, source, targetIdentityId } = args;
  const job = (
    await requestJson({
      ...args,
      method: "POST",
      path: "/engine/knowledge-production/jobs",
      body: {
        sourceScope: source.sourceRef,
        assetType: "KNOWLEDGE",
        producer: "API_MODEL",
        targetPipeline: "PLATFORM_SOURCE",
        domain: entry.productionDomain,
        modelStrategy: "FORMAL_KNOWLEDGE",
      },
      label: `${entry.domain} 创建正式模型生产任务`,
    })
  ).data;
  if (!hasText(job?.jobCode) || job.assetType !== "KNOWLEDGE") {
    throw new Error(`${entry.domain} 正式生产任务响应不完整`);
  }
  const target = targetIdentityId
    ? { targetIdentityId, newIdentity: null }
    : {
        targetIdentityId: null,
        newIdentity: {
          domain: entry.domain,
          subject: entry.subject,
          identityCode: entry.identityCode,
        },
      };
  const modelTaskStartedAt = now(args.now);
  const generated = (
    await requestJson({
      ...args,
      method: "POST",
      path: `/engine/knowledge-production/jobs/${encodeURIComponent(job.jobCode)}/model-candidates`,
      body: {
        capabilityCode: manifest.capabilityCode,
        prompt: buildModelPrompt(entry, args.template, args.revisionNote),
        providerCode: args.providerCode,
        timeoutSeconds: 120,
        assetIdentity: entry.identityCode,
        subject: entry.subject,
        sources: [
          {
            sourceRef: source.sourceRef,
            authorityLevel: entry.source.authorityLevel,
          },
        ],
        trustLevel: entry.source.authorityLevel,
        riskLevel: entry.riskLevel,
        target,
      },
      label: `${entry.domain} 调用正式模型生成候选`,
    })
  ).data;
  const modelTaskFinishedAt = now(args.now);
  const modelTaskDurationMs = elapsedMs(modelTaskStartedAt, modelTaskFinishedAt);
  const candidate = assertModelGeneration(generated, entry.domain);
  const parsedRef = parseCandidateRef(candidate.candidateRef);

  const identity = (
    await requestJson({
      ...args,
      method: "GET",
      path:
        "/engine/knowledge/identities/by-code/" +
        encodeURIComponent(entry.identityCode),
      label: `${entry.domain} 读取知识身份`,
    })
  ).data;
  if (
    !identity?.id ||
    identity.id !== parsedRef.identityId ||
    identity.domain !== entry.domain
  ) {
    throw new Error(`${entry.domain} 知识身份物化结果不一致`);
  }
  const candidateView = (
    await requestJson({
      ...args,
      method: "GET",
      path: `/engine/knowledge/identities/${identity.id}/candidates?page=1&size=200`,
      label: `${entry.domain} 读取候选审核项`,
    })
  ).data;
  const version = requireArray(candidateView?.candidates?.items, "候选版本列表").find(
    (item) => item?.versionNo === parsedRef.versionNo,
  );
  const classification = requireArray(
    candidateView?.classifications,
    "候选分类列表",
  ).find((item) => item?.candidateVersionId === version?.id);
  if (!version?.id || !classification?.id) {
    throw new Error(`${entry.domain} 候选版本或审核分类缺失`);
  }

  await requestJson({
    ...args,
    method: "POST",
    path: "/engine/knowledge/citations",
    body: {
      assetVersionId: version.id,
      sourceFragmentId: source.fragmentId,
      relation: "DERIVED_FROM",
      weight: 100,
      startOffset: 0,
      endOffset: entry.source.textExcerpt.length,
    },
    label: `${entry.domain} 绑定精确来源引用`,
  });
  const technicalEvidence = await assertTechnicalEvidence(args, job.jobCode);
  const qualityRecord = (
    await requestJson({
      ...args,
      method: "POST",
      path: `/engine/knowledge-production/jobs/${encodeURIComponent(job.jobCode)}/publication-quality-records`,
      body: buildPublicationQualityRecordRequest({
        candidateRef: candidate.candidateRef,
        identityId: identity.id,
        versionId: version.id,
      }),
      label: `${entry.domain} 执行服务端发布质量门`,
    })
  ).data;
  if (
    !Number.isSafeInteger(qualityRecord?.id) ||
    qualityRecord.id < 1 ||
    qualityRecord.candidateRef !== candidate.candidateRef ||
    qualityRecord.versionId !== version.id
  ) {
    throw new Error(`${entry.domain} 服务端发布质量门记录无效`);
  }
  const reviewed = (
    await requestJson({
      ...args,
      method: "POST",
      path: `/engine/knowledge/candidates/${classification.id}/review`,
      body: {
        ...apiContext(args),
        decision: "APPROVE",
        reason: "低风险上线演练知识：来源、结构、引用、安全门和影响评估均已核对",
        qualityGateRecordId: qualityRecord.id,
      },
      label: `${entry.domain} 当前责任操作者确认并发布`,
    })
  ).data;
  const activeVersion = requireArray(reviewed?.candidates?.items, "发布版本列表")[0];
  if (reviewed?.reasonCode !== "APPROVED" || activeVersion?.status !== "ACTIVE") {
    throw new Error(`${entry.domain} 候选未原子激活为 ACTIVE`);
  }
  await requestJson({
    ...args,
    method: "POST",
    path: `/engine/knowledge-production/jobs/${encodeURIComponent(job.jobCode)}/complete`,
    label: `${entry.domain} 完成正式生产任务`,
  });
  const runtimeEvidence = await assertRuntimeEvidence(args, identity.id, activeVersion.id);
  return {
    identityId: identity.id,
    versionId: activeVersion.id,
    qualityGateRecordId: qualityRecord.id,
    versionNo: activeVersion.versionNo,
    evidence: {
      domain: entry.domain,
      identityCode: entry.identityCode,
      sourceCode: entry.source.sourceCode,
      sourceVersionId: source.sourceVersionId,
      sourceContentHash: source.contentHash,
      jobCode: job.jobCode,
      modelTaskId: generated.modelTaskId,
      modelMode: generated.modelMode,
      modelVersion: generated.modelVersion,
      promptVersion: generated.promptVersion,
      toolVersion: generated.toolVersion,
      modelTaskStartedAt,
      modelTaskFinishedAt,
      modelTaskDurationMs,
      candidateRef: candidate.candidateRef,
      classificationId: classification.id,
      versionId: activeVersion.id,
      versionNo: activeVersion.versionNo,
      status: activeVersion.status,
      technicalEvidence,
      qualityGateRecordId: qualityRecord.id,
      runtimeEvidence,
    },
  };
}

async function assertTechnicalEvidence(args, jobCode) {
  const read = async (suffix, label) =>
    (
      await requestJson({
        ...args,
        method: "GET",
        path: `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/${suffix}`,
        label,
      })
    ).data;
  const gates = requireArray(await read("gate-results", "读取候选安全门"), "安全门结果");
  const triage = requireArray(await read("triage-results", "读取候选分流"), "分流结果");
  const shadow = requireArray(await read("shadow-runs", "读取影子评测"), "影子评测结果");
  if (gates.length === 0 || gates.some((item) => item?.passed !== true)) {
    throw new Error("候选存在未通过或缺失的安全门结果");
  }
  const finalTriage = triage.at(-1);
  if (!finalTriage?.action?.endsWith("REVIEW") || finalTriage.action === "SKIP_DUPLICATE") {
    throw new Error("候选没有进入真实审核分流");
  }
  const finalShadow = shadow.at(-1);
  if (!isAcceptableShadowRun(finalShadow)) {
    throw new Error("候选影子评测未通过或检测到退化");
  }
  return {
    gateCount: gates.length,
    triageAction: finalTriage.action,
    shadowStatus: finalShadow.status,
    shadowCaseCount: finalShadow.totalCases,
  };
}

async function assertRuntimeEvidence(args, identityId, versionId) {
  const read = async (suffix, label) =>
    (
      await requestJson({
        ...args,
        method: "GET",
        path: `/engine/knowledge/identities/${identityId}/${suffix}`,
        label,
      })
    ).data;
  const active = await read("active", "读取当前权威知识");
  const provenance = await read("provenance", "读取知识血缘");
  const citations = requireArray(await read("citations", "读取知识引用"), "知识引用");
  const sourceEvidence = requireArray(
    await read("source-evidence", "读取来源证据"),
    "来源证据",
  );
  if (active?.id !== versionId || active.status !== "ACTIVE") {
    throw new Error("运行时没有命中新发布的当前权威版本");
  }
  if (!provenance || citations.length === 0 || sourceEvidence.length === 0) {
    throw new Error("发布后血缘、引用或来源证据不完整");
  }
  return {
    activeVersionId: active.id,
    citationCount: citations.length,
    sourceEvidenceCount: sourceEvidence.length,
  };
}

async function exerciseRollback(args) {
  const activate = async (versionId, qualityGateRecordId, reason) => {
    const response = await requestJson({
      ...args,
      method: "POST",
      path: `/engine/knowledge/identities/${args.identityId}/versions/${versionId}/activate`,
      body: { reason, qualityGateRecordId },
      label: reason,
    });
    if (response.data?.id !== versionId || response.data?.status !== "ACTIVE") {
      throw new Error(`${reason}未形成预期 ACTIVE 版本`);
    }
  };
  await activate(
    args.v1VersionId,
    args.v1QualityGateRecordId,
    "V2 发布后回滚至 V1 演练",
  );
  const rollbackActive = await readActive(args);
  await activate(
    args.v2VersionId,
    args.v2QualityGateRecordId,
    "回滚验证后恢复 V2 当前版本",
  );
  const restoredActive = await readActive(args);
  if (rollbackActive.id !== args.v1VersionId || restoredActive.id !== args.v2VersionId) {
    throw new Error("知识版本回滚或恢复结果不一致");
  }
  return {
    rollbackActiveVersionId: rollbackActive.id,
    restoredActiveVersionId: restoredActive.id,
    finalStatus: restoredActive.status,
  };
}

async function readActive(args) {
  return (
    await requestJson({
      ...args,
      method: "GET",
      path: `/engine/knowledge/identities/${args.identityId}/active`,
      label: "核对回滚后的当前权威版本",
    })
  ).data;
}

function assertModelGeneration(generated, domain) {
  if (
    !generated ||
    !hasText(generated.modelTaskId) ||
    !hasText(generated.modelMode) ||
    generated.modelMode.toUpperCase() === "B0" ||
    !hasText(generated.modelVersion)
  ) {
    throw new Error(`${domain} 未使用真实非 B0 模型生成候选`);
  }
  const candidates = requireArray(generated.summary?.candidates, "模型候选列表");
  const blocked = requireArray(generated.summary?.blocked, "模型阻断列表");
  const skipped = requireArray(generated.summary?.skipped, "模型跳过列表");
  if (candidates.length !== 1 || blocked.length !== 0 || skipped.length !== 0) {
    throw new Error(`${domain} 模型候选未唯一生成，或被阻断/跳过`);
  }
  return candidates[0];
}

function assertLaunchLogin(data, operator) {
  if (!data || data.tenantId !== operator.tenantId) {
    throw new Error("登录租户与受控账号不一致");
  }
  if (data.mustChangePwd === true) throw new Error("演练账号仍要求修改初始密码");
  if (data.mfaRequired === true) throw new Error("上线默认配置错误：MFA 应保持关闭");
  if (!Array.isArray(data.roles) || !data.roles.includes("engine-operator")) {
    throw new Error("登录账号未获得 engine-operator 职责");
  }
}

function assertTemplateCoverage(value) {
  const templates = requireArray(value, "专业资产模板");
  const knowledge = templates.filter((item) => item?.assetType === "KNOWLEDGE");
  const byDomain = new Map(knowledge.map((item) => [item.knowledgeDomain, item]));
  if (
    knowledge.length !== FULL_KNOWLEDGE_DOMAINS.length ||
    FULL_KNOWLEDGE_DOMAINS.some((domain) => !byDomain.has(domain))
  ) {
    throw new Error("后端专业模板没有完整覆盖 11 个知识域");
  }
  return {
    byDomain,
    structuralCount: templates.filter((item) => item?.assetType !== "KNOWLEDGE").length,
  };
}

function assertReadiness(data, config) {
  const items = requireArray(data?.items, "知识生产就绪项");
  const blocked = items.filter((item) => item?.required && !item?.ready);
  if (
    data?.capabilityCode !== CAPABILITY ||
    data?.providerCode !== config.providerCode ||
    data?.ready !== true ||
    data?.modelInvocationAllowed !== true ||
    blocked.length > 0
  ) {
    throw new Error(
      `正式模型知识生产未就绪：${blocked.map((item) => item?.code).join(",")}`,
    );
  }
  return {
    ready: true,
    providerCode: data.providerCode,
    deploymentForm: data.deploymentForm,
    requiredItemCount: items.filter((item) => item?.required).length,
  };
}

function parseCandidateRef(value) {
  const match = /^kv:(\d+):(.+)$/u.exec(requireText(value, "candidateRef"));
  if (!match) throw new Error("模型候选引用不是标准 kv:<identityId>:<versionNo>");
  return { identityId: Number(match[1]), versionNo: match[2] };
}

async function requestJson({
  fetchImpl,
  requests,
  apiBaseUrl,
  session,
  method,
  path: requestPath,
  body,
  label,
}) {
  const normalizedMethod = requireText(method, "method").toUpperCase();
  const normalizedPath = normalizeApiPath(requestPath);
  if (
    !API_ALLOWLIST.some(
      ([allowedMethod, pattern]) =>
        normalizedMethod === allowedMethod && pattern.test(normalizedPath),
    )
  ) {
    throw new Error(`请求不在正式全知识演练白名单：${normalizedMethod} ${normalizedPath}`);
  }
  const headers = {
    Accept: "application/json",
    "X-Trace-Id": `full-knowledge-${randomUUID()}`,
  };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (session) {
    headers.Cookie = session.cookie;
    if (!new Set(["GET", "HEAD", "OPTIONS"]).has(normalizedMethod)) {
      headers["X-XSRF-TOKEN"] = session.xsrf;
    }
  }
  let response;
  try {
    response = await fetchImpl(`${apiBaseUrl}${normalizedPath}`, {
      method: normalizedMethod,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    throw new Error(`${label}请求失败：${safeMessage(error)}`);
  }
  requests.push({ method: normalizedMethod, path: normalizedPath, status: response.status });
  const text = await response.text();
  const parsed = text ? parseJson(text, `${label}响应`) : null;
  if (response.status !== 200) {
    throw new Error(`${label}返回 HTTP ${response.status}：${safeApiMessage(parsed)}`);
  }
  if (parsed?.success === false) {
    throw new Error(`${label}业务响应失败：${safeApiMessage(parsed)}`);
  }
  return { data: parsed?.data ?? null, body: parsed, headers: response.headers };
}

function authenticatedSession(headers) {
  const values =
    typeof headers.getSetCookie === "function"
      ? headers.getSetCookie()
      : splitSetCookie(headers.get("set-cookie") ?? "");
  const pairs = values
    .flatMap(splitSetCookie)
    .map((value) => value.split(";")[0]?.trim())
    .filter(Boolean);
  const xsrfPair = pairs.find((pair) => pair.startsWith("XSRF-TOKEN="));
  if (!xsrfPair || pairs.length === 0) {
    throw new Error("登录响应未返回受控会话 Cookie 与 XSRF-TOKEN");
  }
  return {
    cookie: pairs.join("; "),
    xsrf: decodeURIComponent(xsrfPair.slice("XSRF-TOKEN=".length)),
  };
}

function apiContext(args) {
  const traceId = `full-knowledge-${randomUUID()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: args.tenantId,
    group_id: null,
    hospital_id: null,
    campus_id: null,
    site_id: null,
    department_id: null,
    specialty_id: null,
    user_id: args.operator.username,
    role_codes: [args.operator.role],
  };
}

function validateEntry(entry, index, checkedAt) {
  if (!entry || typeof entry !== "object" || Array.isArray(entry)) {
    throw new Error(`manifest.entries[${index}] 必须是对象`);
  }
  for (const field of [
    "domain",
    "productionDomain",
    "assetType",
    "identityCode",
    "subject",
    "riskLevel",
  ]) {
    requireText(entry[field], `entries[${index}].${field}`);
  }
  if (!DOMAIN_SET.has(entry.domain)) throw new Error(`${entry.domain} 不是正式知识域`);
  if (!PRODUCTION_DOMAINS.has(entry.productionDomain)) {
    throw new Error(`${entry.domain} 的生产责任域无效`);
  }
  if (
    entry.assetType !== "KNOWLEDGE" ||
    entry.riskLevel !== "LOW" ||
    entry.generatedByModel !== true
  ) {
    throw new Error(`${entry.domain} 必须是模型生成的低风险 KNOWLEDGE 候选`);
  }
  const source = entry.source;
  if (!source || typeof source !== "object") throw new Error(`${entry.domain} 缺少来源`);
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
    "publishedAtBasis",
    "publishedAtEvidence",
    "url",
    "checkedAt",
    "anchorPath",
    "anchorLabel",
    "textExcerpt",
  ]) {
    requireText(source[field], `${entry.domain}.source.${field}`);
  }
  if (!SOURCE_TYPES.has(source.sourceType)) throw new Error(`${entry.domain} 来源类型无效`);
  if (!AUTHORITY_LEVELS.has(source.authorityLevel)) {
    throw new Error(`${entry.domain} 来源权威级别无效`);
  }
  if (!source.url.startsWith("https://") || source.checkedAt !== checkedAt) {
    throw new Error(`${entry.domain} 官方来源 URL 或核查日期不合规`);
  }
  if (!["OFFICIAL_PUBLICATION", "VERIFIED_SNAPSHOT"].includes(source.publishedAtBasis)) {
    throw new Error(`${entry.domain} 发布日期依据无效`);
  }
  if (!Array.isArray(source.allowedHosts) || source.allowedHosts.length === 0) {
    throw new Error(`${entry.domain} 缺少来源允许主机`);
  }
  if (!Array.isArray(source.verificationTerms) || source.verificationTerms.length === 0) {
    throw new Error(`${entry.domain} 缺少来源核验词`);
  }
  const sourceHost = new URL(source.url).hostname;
  if (!source.allowedHosts.includes(sourceHost)) {
    throw new Error(`${entry.domain} 来源 URL 主机不在允许主机`);
  }
  if (
    source.publishedAtBasis === "OFFICIAL_PUBLICATION" &&
    !/官方|official|publication|published|edition|release/iu.test(source.publishedAtEvidence)
  ) {
    throw new Error(`${entry.domain} 缺少官方发布日期证据`);
  }
  if (
    source.publishedAtBasis === "VERIFIED_SNAPSHOT" &&
    !source.publishedAt.startsWith(checkedAt)
  ) {
    throw new Error(`${entry.domain} 核查快照日期必须等于清单核查日期`);
  }
  if (source.textExcerpt.length < 12 || source.textExcerpt.length > 240) {
    throw new Error(`${entry.domain} 来源锚点必须为 12 至 240 字符的短文本`);
  }
}

function validateTemplate(template, domain) {
  if (
    !template ||
    template.assetType !== "KNOWLEDGE" ||
    template.knowledgeDomain !== domain ||
    !Array.isArray(template.sections) ||
    template.sections.length === 0
  ) {
    throw new Error(`${domain} 缺少正式知识模板`);
  }
}

function resolveEvidencePath(env, repoRoot) {
  let candidate;
  if (hasText(env.FULL_KNOWLEDGE_EVIDENCE_PATH)) {
    candidate = path.resolve(env.FULL_KNOWLEDGE_EVIDENCE_PATH);
  } else {
    const runtimeRoot = path.resolve(
      requireText(env.MEDKERNEL_RUNTIME_ROOT, "MEDKERNEL_RUNTIME_ROOT"),
    );
    candidate = path.join(runtimeRoot, "evidence/current-launch/full-knowledge.json");
  }
  const relative = path.relative(repoRoot, candidate);
  if (relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))) {
    throw new Error("证据路径必须位于仓库之外");
  }
  return candidate;
}

function normalizeActor(actor, tenantId) {
  return {
    tenantId: requireText(actor?.tenantId ?? tenantId, "operator.tenantId"),
    username: requireText(actor?.username, "operator.username"),
    password: requireText(actor?.password, "operator.password"),
    role: requireText(actor?.role, "operator.role"),
  };
}

function normalizeBaseUrl(value) {
  const text = requireText(value, "apiBaseUrl").replace(/\/+$/u, "");
  let url;
  try {
    url = new URL(text);
  } catch {
    throw new Error("FULL_KNOWLEDGE_API_BASE_URL 不是合法 URL");
  }
  if (url.username || url.password) throw new Error("API URL 不得内嵌凭据");
  const loopback = ["127.0.0.1", "localhost", "::1"].includes(url.hostname);
  if (url.protocol !== "https:" && !(url.protocol === "http:" && loopback)) {
    throw new Error("正式知识演练 API 必须使用 HTTPS 或本机回环 HTTP");
  }
  return text;
}

function normalizeApiPath(value) {
  const text = requireText(value, "path");
  if (!text.startsWith("/") || text.startsWith("//")) {
    throw new Error("API path 必须是站内绝对路径");
  }
  const url = new URL(text, "https://medkernel.invalid");
  return `${url.pathname}${url.search}`;
}

function stableJson(value) {
  return JSON.stringify(sortValue(value));
}

function sortValue(value) {
  if (Array.isArray(value)) return value.map(sortValue);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.keys(value)
      .sort()
      .map((key) => [key, sortValue(value[key])]),
  );
}

function unique(set, value, label) {
  if (set.has(value)) throw new Error(`${label}重复：${value}`);
  set.add(value);
}

function parseJson(value, label) {
  try {
    return JSON.parse(value);
  } catch {
    throw new Error(`${label}不是合法 JSON`);
  }
}

function requireArray(value, label) {
  if (!Array.isArray(value)) throw new Error(`${label}必须是数组`);
  return value;
}

function requirePositiveInteger(value, label) {
  if (!Number.isSafeInteger(value) || value < 1) {
    throw new Error(`${label} 必须是正整数`);
  }
  return value;
}

function requireText(value, label) {
  if (typeof value !== "string" || !value.trim()) throw new Error(`${label}不能为空`);
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function semanticVersion(value) {
  return typeof value === "string" && /^\d+\.\d+\.\d+$/u.test(value);
}

function createProgressReporter(onProgress, clock) {
  if (typeof onProgress !== "function") return () => {};
  return (event) => {
    onProgress({
      at: now(clock),
      ...redactEvidence(event),
    });
  };
}

function modelTaskProgress(domain, phase, evidence) {
  return {
    domain,
    phase,
    modelTaskId: evidence.modelTaskId,
    modelMode: evidence.modelMode,
    modelVersion: evidence.modelVersion,
    startedAt: evidence.modelTaskStartedAt,
    finishedAt: evidence.modelTaskFinishedAt,
    durationMs: evidence.modelTaskDurationMs,
  };
}

function elapsedMs(startedAt, finishedAt) {
  const started = Date.parse(startedAt);
  const finished = Date.parse(finishedAt);
  if (!Number.isFinite(started) || !Number.isFinite(finished)) return 0;
  return Math.max(0, finished - started);
}

function formatDuration(value) {
  const milliseconds = Number.isFinite(value) ? Math.max(0, value) : 0;
  if (milliseconds < 1000) return `${milliseconds}ms`;
  const seconds = Math.round(milliseconds / 100) / 10;
  return `${seconds}s`;
}

function sha256(value) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function now(clock) {
  const value = typeof clock === "function" ? clock() : new Date().toISOString();
  return typeof value === "string" ? value : new Date(value).toISOString();
}

function splitSetCookie(value) {
  return value ? value.split(/,(?=\s*[^;,]+=)/u) : [];
}

function safeMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

function safeApiMessage(body) {
  return body?.message ?? body?.code ?? "无可用错误信息";
}
