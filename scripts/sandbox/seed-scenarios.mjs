#!/usr/bin/env node
// 演练机构规则铺底：标准快照 -> 规则四测 -> 治理演练 -> 交付内容 -> 当前机构生效版本。
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { loadScenarioRules, selectSeedRules } from "./scenario-rules.mjs";
import {
  ASSIGNABLE_ROLES,
  selectLaunchAccount,
  validateLaunchCredentials,
} from "../release/launch-account-bootstrap-lib.mjs";

const requireFromFrontend = createRequire(
  new URL("../../frontend/package.json", import.meta.url),
);
const { chromium } = requireFromFrontend("playwright");

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const baseUrl = (
  process.env.DRILL_BASE_URL ?? "https://193.112.107.134"
).replace(/\/+$/, "");
const apiBase = `${baseUrl}/medkernel/api/v1`;
const credentialPath =
  process.env.LAUNCH_CREDENTIALS_FILE ??
  "/var/lib/medkernel/credentials/current-launch.json";
export function resolveSandboxEvidenceDir(
  env = process.env,
  repositoryRoot = repoRoot,
) {
  const runtimeRoot = path.resolve(
    env.MEDKERNEL_RUNTIME_ROOT?.trim() || "/var/lib/medkernel",
  );
  const candidate = path.resolve(
    env.DRILL_EVIDENCE_DIR?.trim() ||
      path.join(runtimeRoot, "evidence/current-launch/sandbox"),
  );
  const relative = path.relative(path.resolve(repositoryRoot), candidate);
  if (relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))) {
    throw new Error("沙盘演练证据目录必须位于代码仓库之外");
  }
  return candidate;
}
const evidenceDir = resolveSandboxEvidenceDir();
const mappingVersion = "sandbox-context-v1";

export function resolveChromiumLaunchOptions(env = process.env) {
  const executablePath =
    env.MEDKERNEL_PLAYWRIGHT_CHROMIUM_EXECUTABLE?.trim() || "";
  if (!executablePath) return {};
  return {
    executablePath,
    args: env.MEDKERNEL_PLAYWRIGHT_NO_SANDBOX === "1" ? ["--no-sandbox"] : [],
  };
}

export const RULE_GOVERNANCE_STAGES = Object.freeze([
  "DRAFT",
  "REVIEWED",
  "SHADOW",
  "CANARY",
  "FULL",
  "MONITOR",
]);

export const SANDBOX_ACTOR_ROLES = Object.freeze({
  snapshotWriter: "platform-admin",
  engineOperator: "engine-operator",
  auditReader: "auditor",
});

function traceId(stage) {
  return `sandbox-seed-${stage}-${Date.now()}`;
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(",")}]`;
  if (value && typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

/** 内容摘要即版本身份；规则内容变化必然得到新版本，不使用日期或固定发布口径。 */
export function deriveSandboxRuntimeDigest(manifest) {
  const digest = createHash("sha256")
    .update(canonicalJson(manifest))
    .digest("hex");
  return `sandbox-${digest.slice(0, 16)}`;
}

function nonBlank(value, fallback) {
  return typeof value === "string" && value.trim() ? value.trim() : fallback;
}

function resourceMeta(sourceRecordId, occurredAt) {
  return {
    sourceSystem: "MEDKERNEL_SANDBOX",
    sourceRecordId,
    mappedVersion: mappingVersion,
    eventTime: occurredAt,
    receivedTime: occurredAt,
    qualityStatus: "VALID",
  };
}

function conditionResourceMeta(sourceRecordId, occurredAt) {
  const { eventTime: _eventTime, ...meta } = resourceMeta(
    sourceRecordId,
    occurredAt,
  );
  return meta;
}

function normalizeDate(value) {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

/** 把规则样例事实转换为通过 DTO 校验的 13 类标准资源，同时不补造缺失医学事实。 */
export function buildCanonicalResources(
  testCase,
  timestamp = new Date().toISOString(),
) {
  const facts = testCase.facts ?? {};
  const occurredAt = new Date(timestamp).toISOString();
  const patientFact = facts.patient ?? {};
  const mapList = (key, mapper) => (facts[key] ?? []).map(mapper);
  const encounters = Object.hasOwn(facts, "encounters")
    ? facts.encounters
    : [{ encounterType: "ED" }];

  return {
    patient: {
      mpi: testCase.patientId,
      name: nonBlank(patientFact.name, `沙盘${testCase.caseType}患者`),
      birthDate: normalizeDate(patientFact.birthDate),
      gender: patientFact.gender ?? null,
      specialPopulations: patientFact.specialPopulations ?? [],
      ...resourceMeta(`${testCase.patientId}-PATIENT`, occurredAt),
    },
    allergyIntolerances: [],
    encounters: encounters.map((item, index) => ({
      encounterId:
        index === 0
          ? testCase.encounterId
          : `${testCase.encounterId}-${index + 1}`,
      encounterType: nonBlank(item.encounterType, "ED"),
      admissionTime: item.admissionTime ?? occurredAt,
      dischargeTime: item.dischargeTime ?? null,
      departmentId: item.departmentId ?? "SANDBOX-DEPARTMENT",
      attendingDoctorId: item.attendingDoctorId ?? "SBX-DOCTOR-001",
      bedId: item.bedId ?? null,
      ...resourceMeta(`${testCase.encounterId}-ENC-${index + 1}`, occurredAt),
    })),
    conditions: mapList("conditions", (item, index) => ({
      conditionId: `${testCase.patientId}-COND-${index + 1}`,
      code: nonBlank(item.code, "UNMAPPED"),
      codeSystem: nonBlank(item.codeSystem, "ICD-10"),
      displayName: nonBlank(item.displayName, "未映射诊断"),
      stage: item.stage ?? null,
      severity: item.severity ?? null,
      onsetTime: item.onsetTime ?? occurredAt,
      ...conditionResourceMeta(
        `${testCase.patientId}-COND-${index + 1}`,
        occurredAt,
      ),
    })),
    nursingAssessments: [],
    observations: mapList("observations", (item, index) => ({
      observationId: `${testCase.patientId}-OBS-${index + 1}`,
      code: nonBlank(item.code, "UNMAPPED"),
      displayName: nonBlank(item.displayName, "未映射观察项"),
      valueNumeric: item.valueNumeric ?? null,
      valueString: item.valueString ?? null,
      unit: item.unit ?? null,
      referenceRange: item.referenceRange ?? null,
      criticalFlag: item.criticalFlag ?? null,
      ...resourceMeta(`${testCase.patientId}-OBS-${index + 1}`, occurredAt),
    })),
    diagnosticReports: mapList("diagnosticReports", (item, index) => ({
      reportId: `${testCase.patientId}-REPORT-${index + 1}`,
      reportType: nonBlank(item.reportType, "UNCLASSIFIED"),
      conclusion: nonBlank(item.conclusion, "未提供报告结论"),
      keyFindings: item.keyFindings ?? [],
      signedBy: item.signedBy ?? null,
      signedAt: item.signedAt ?? null,
      ...resourceMeta(`${testCase.patientId}-REPORT-${index + 1}`, occurredAt),
    })),
    medications: mapList("medications", (item, index) => ({
      medicationId: `${testCase.patientId}-MED-${index + 1}`,
      code: nonBlank(item.code, "UNMAPPED"),
      displayName: nonBlank(item.displayName, "未映射药品"),
      dose: item.dose ?? null,
      doseUnit: item.doseUnit ?? null,
      route: item.route ?? null,
      frequency: item.frequency ?? null,
      durationDays: item.durationDays ?? null,
      prescriptionStatus: item.prescriptionStatus ?? null,
      ...resourceMeta(`${testCase.patientId}-MED-${index + 1}`, occurredAt),
    })),
    procedures: [],
    documents: mapList("documents", (item, index) => ({
      documentId: `${testCase.patientId}-DOC-${index + 1}`,
      documentType: nonBlank(item.documentType, "UNCLASSIFIED"),
      contentDigest: item.contentDigest ?? null,
      signedBy: item.signedBy ?? null,
      signedAt: item.signedAt ?? null,
      ...resourceMeta(`${testCase.patientId}-DOC-${index + 1}`, occurredAt),
    })),
    carePlans: [],
    followUps: mapList("followUps", (item, index) => ({
      followUpId: `${testCase.patientId}-FOLLOWUP-${index + 1}`,
      planType: nonBlank(item.planType, "UNCLASSIFIED"),
      plannedAt: item.plannedAt ?? null,
      questionnaireId: item.questionnaireId ?? null,
      abnormalFlag: item.abnormalFlag ?? null,
      ...resourceMeta(
        `${testCase.patientId}-FOLLOWUP-${index + 1}`,
        occurredAt,
      ),
    })),
    claims: mapList("claims", (item, index) => ({
      claimId: `${testCase.patientId}-CLAIM-${index + 1}`,
      drgCode: nonBlank(item.drgCode, "UNASSIGNED"),
      totalCost: item.totalCost ?? null,
      insurancePaid: item.insurancePaid ?? null,
      ...resourceMeta(`${testCase.patientId}-CLAIM-${index + 1}`, occurredAt),
    })),
  };
}

async function loadCredentials() {
  const data = JSON.parse(await readFile(credentialPath, "utf8"));
  validateLaunchCredentials(data);
  return data;
}

export function resolveSandboxAccounts(credentials) {
  validateLaunchCredentials(credentials);
  return Object.fromEntries(
    ASSIGNABLE_ROLES.map((role) => [
      role,
      selectLaunchAccount(credentials, "rehearsal", role),
    ]),
  );
}

function requireAccount(credentials, role) {
  return resolveSandboxAccounts(credentials)[role];
}

async function login(browser, account, role) {
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    locale: "zh-CN",
  });
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: {
      username: account.username,
      password: account.password,
      tenantId: account.tenantId,
    },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(`login-${role}`),
    },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(
      `${role} 登录失败: ${response.status()} ${body.slice(0, 300)}`,
    );
  }
  return context;
}

async function parseResponse(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text.slice(0, 600) };
  }
}

async function apiGet(context, pathname, stage) {
  const response = await context.request.get(`${apiBase}${pathname}`, {
    headers: { "X-Trace-Id": traceId(stage) },
  });
  return {
    status: response.status(),
    ok: response.ok(),
    body: await parseResponse(response),
  };
}

async function apiPost(context, pathname, data, stage) {
  const cookies = await context.cookies(baseUrl);
  const csrf =
    cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? "";
  const response = await context.request.post(`${apiBase}${pathname}`, {
    data,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(stage),
      "X-XSRF-TOKEN": csrf,
    },
  });
  return {
    status: response.status(),
    ok: response.ok(),
    body: await parseResponse(response),
  };
}

function ensureOk(result, stage, accepted = [200, 201]) {
  if (!accepted.includes(result.status)) {
    throw new Error(
      `${stage} 失败: HTTP ${result.status} ${JSON.stringify(result.body).slice(0, 700)}`,
    );
  }
  return result.body?.data;
}

function pageItems(data) {
  return Array.isArray(data) ? data : (data?.items ?? []);
}

async function envelope(context, stage) {
  const me = ensureOk(
    await apiGet(context, "/security/me", `${stage}-me`),
    `${stage} 读取登录态`,
  );
  const scope = me.dataScope ?? {};
  const roleCodes = (me.roles ?? me.roleCodes ?? [])
    .map((role) => (typeof role === "string" ? role : role.code))
    .filter(Boolean);
  return {
    request_id: traceId(`${stage}-request`),
    trace_id: traceId(`${stage}-trace`),
    tenant_id: scope.tenantId,
    group_id: scope.groupId ?? null,
    hospital_id: scope.hospitalId ?? null,
    campus_id: scope.campusId ?? null,
    site_id: scope.siteId ?? null,
    department_id: scope.departmentId ?? null,
    specialty_id: scope.specialtyId ?? null,
    user_id: me.userId ?? me.username,
    role_codes: roleCodes,
  };
}

function runtimeTargetOrgUnitId(contextEnvelope) {
  return (
    contextEnvelope.hospital_id ||
    contextEnvelope.campus_id ||
    contextEnvelope.tenant_id
  );
}

function snapshotPayload(testCase, contextEnvelope) {
  const occurredAt = new Date().toISOString();
  return {
    ...contextEnvelope,
    request_id: traceId(`snapshot-${testCase.caseType}`),
    trace_id: traceId(`snapshot-${testCase.caseType}-trace`),
    patientId: testCase.patientId,
    encounterId: testCase.encounterId,
    orgUnitId: runtimeTargetOrgUnitId(contextEnvelope),
    resources: buildCanonicalResources(testCase, occurredAt),
  };
}

async function seedSnapshots(browser, credentials, rule) {
  const context = await login(
    browser,
    requireAccount(credentials, SANDBOX_ACTOR_ROLES.snapshotWriter),
    SANDBOX_ACTOR_ROLES.snapshotWriter,
  );
  try {
    const ctx = await envelope(context, "snapshot");
    const snapshots = [];
    for (const testCase of rule.clinicalContent.testCases) {
      const query = `/engine/context/snapshots?patientId=${encodeURIComponent(testCase.patientId)}&status=ACTIVE&page=1&size=5`;
      const existing = pageItems(
        ensureOk(
          await apiGet(
            context,
            query,
            `snapshot-find-${rule.ruleCode}-${testCase.caseType}`,
          ),
          `回查 ${rule.ruleCode}/${testCase.caseType} 快照`,
        ),
      )[0];
      if (existing) {
        const existingDetail = ensureOk(
          await apiGet(
            context,
            `/engine/context/snapshots/${existing.snapshotId}`,
            `snapshot-detail-${rule.ruleCode}-${testCase.caseType}`,
          ),
          `读取 ${rule.ruleCode}/${testCase.caseType} 既有快照详情`,
        );
        if (existingDetail.runtimeReleaseId) {
          snapshots.push({
            ...testCase,
            snapshotId: existing.snapshotId,
            reused: true,
          });
          continue;
        }
      }
      const created = ensureOk(
        await apiPost(
          context,
          "/engine/context/snapshots",
          snapshotPayload(testCase, ctx),
          `snapshot-create-${rule.ruleCode}-${testCase.caseType}`,
        ),
        `创建 ${rule.ruleCode}/${testCase.caseType} 快照`,
        [201],
      );
      if (
        !created?.snapshotId ||
        created.status !== "ACTIVE" ||
        !created.runtimeReleaseId
      ) {
        throw new Error(
          `${rule.ruleCode}/${testCase.caseType} 快照未形成 ACTIVE 且绑定机构生效版本的服务端事实`,
        );
      }
      snapshots.push({
        ...testCase,
        snapshotId: created.snapshotId,
        reused: false,
      });
    }
    return snapshots;
  } finally {
    await context.close();
  }
}

async function findRule(context, ruleCode, stage) {
  const page = ensureOk(
    await apiGet(context, "/engine/rule/rules?page=1&size=100", stage),
    "读取规则列表",
  );
  return pageItems(page).find((item) => item.ruleCode === ruleCode) ?? null;
}

async function ruleDetail(context, ruleId, stage) {
  return ensureOk(
    await apiGet(context, `/engine/rule/rules/${ruleId}`, stage),
    "读取规则详情",
  );
}

function publishedRule(detail) {
  return (
    detail?.definition?.status === "PUBLISHED" || detail?.status === "PUBLISHED"
  );
}

function ruleVersionNo(detail) {
  return String(detail?.version?.versionNo ?? detail?.versionNo ?? 1);
}

function ruleVersionId(detail) {
  return String(
    detail?.triggerBindings?.[0]?.versionId ??
      detail?.version?.versionId ??
      detail?.versionId ??
      "",
  );
}

function assertPublishedRuleMatches(detail, rule) {
  const definition = detail.definition ?? detail;
  const version = detail.version ?? detail;
  let actualDsl = version.dslJson ?? version.dsl;
  if (typeof actualDsl === "string") actualDsl = JSON.parse(actualDsl);
  if (
    definition.ruleCode !== rule.ruleCode ||
    version.sourceRef !== rule.sourceRef ||
    version.changeSummary !== rule.changeSummary ||
    canonicalJson(actualDsl) !== canonicalJson(rule.clinicalContent.dsl)
  ) {
    throw new Error(
      `已发布规则 ${rule.ruleCode} 与当前演练清单不一致，拒绝静默复用`,
    );
  }
}

async function createAndTestRule(
  browser,
  credentials,
  rule,
  snapshots,
) {
  const context = await login(
    browser,
    requireAccount(credentials, SANDBOX_ACTOR_ROLES.engineOperator),
    SANDBOX_ACTOR_ROLES.engineOperator,
  );
  try {
    const ctx = await envelope(context, "rule");
    let definition = await findRule(
      context,
      rule.ruleCode,
      `rule-find-${rule.ruleCode}`,
    );
    if (!definition) {
      const created = ensureOk(
        await apiPost(
          context,
          "/engine/rule/rules",
          {
            ...ctx,
            ruleCode: rule.ruleCode,
            name: rule.name,
            ruleType: rule.ruleType,
            authoringMode: "DSL",
            riskLevel: rule.riskLevel,
            priority: 100,
            dedupeWindowSeconds: 0,
            applicableOrgUnitId: null,
            sourceRef: rule.sourceRef,
            changeSummary: rule.changeSummary,
            dsl: rule.clinicalContent.dsl,
            explanation: rule.clinicalContent.dsl.explain,
            parameterBindings: null,
          },
          `rule-create-${rule.ruleCode}`,
        ),
        `创建沙盘规则 ${rule.ruleCode}`,
        [201],
      );
      definition = { ruleId: created.ruleId, status: "DRAFT" };
    }

    let detail = await ruleDetail(
      context,
      definition.ruleId,
      `rule-detail-${rule.ruleCode}`,
    );
    if (publishedRule(detail)) {
      assertPublishedRuleMatches(detail, rule);
      return {
        ruleId: definition.ruleId,
        assetVersion: ruleVersionNo(detail),
        alreadyPublished: true,
      };
    }

    const existingCases = new Set(
      (detail.testCases ?? []).map((item) => item.caseType),
    );
    for (const snapshot of snapshots) {
      if (existingCases.has(snapshot.caseType)) continue;
      ensureOk(
        await apiPost(
          context,
          `/engine/rule/rules/${definition.ruleId}/test-cases`,
          {
            ...ctx,
            caseType: snapshot.caseType,
            contextSnapshotId: snapshot.snapshotId,
            expectedHit: snapshot.expectedHit,
            expectedSeverity: snapshot.expectedSeverity ?? null,
            expectedActionCode: snapshot.expectedActionCode ?? null,
          },
          `rule-case-${rule.ruleCode}-${snapshot.caseType}`,
        ),
        `创建 ${rule.ruleCode}/${snapshot.caseType} 测试用例`,
        [201],
      );
    }
    const testRun = ensureOk(
      await apiPost(
        context,
        `/engine/rule/rules/${definition.ruleId}/test`,
        ctx,
        `rule-test-${rule.ruleCode}`,
      ),
      `执行规则测试 ${rule.ruleCode}`,
    );
    if ((testRun.results ?? []).some((item) => item.status !== "PASS")) {
      throw new Error(
        `${rule.ruleCode} 四类规则测试未全绿: ${JSON.stringify(testRun.results)}`,
      );
    }
    const positive = snapshots.find((item) => item.caseType === "POSITIVE");
    const snapshotDetail = ensureOk(
      await apiGet(
        context,
        `/engine/context/snapshots/${positive.snapshotId}`,
        `positive-${rule.ruleCode}`,
      ),
      `读取 ${rule.ruleCode} 阳性快照`,
    );
    const simulation = ensureOk(
      await apiPost(
        context,
        `/engine/rule/rules/${definition.ruleId}/simulate`,
        {
          ...ctx,
          context: snapshotDetail.resources,
        },
        `rule-simulate-${rule.ruleCode}`,
      ),
      `规则真实快照试运行 ${rule.ruleCode}`,
    );
    if (simulation.hit !== true)
      throw new Error(`${rule.ruleCode} 阳性快照试运行未命中`);
    detail = await ruleDetail(
      context,
      definition.ruleId,
      `rule-detail-after-test-${rule.ruleCode}`,
    );
    return {
      ruleId: definition.ruleId,
      assetVersion: ruleVersionNo(detail),
      versionId: ruleVersionId(detail),
      alreadyPublished: false,
    };
  } finally {
    await context.close();
  }
}

async function withRole(browser, credentials, role, run) {
  const context = await login(browser, requireAccount(credentials, role), role);
  try {
    return await run(context, await envelope(context, role));
  } finally {
    await context.close();
  }
}

async function governanceState(context, ruleId, stage) {
  return (await ruleDetail(context, ruleId, stage)).governance;
}

async function impactDigest(context, ruleId, stage) {
  const impact = ensureOk(
    await apiGet(context, `/engine/rule/rules/${ruleId}/impact`, stage),
    "读取规则影响摘要",
  );
  if (!impact?.impactDigest) throw new Error("规则影响摘要缺少 impactDigest");
  return impact.impactDigest;
}

async function transition(
  browser,
  credentials,
  role,
  ruleId,
  targetState,
  reason,
  publishEvidence,
) {
  return withRole(
    browser,
    credentials,
    role,
    async (context, ctx) => {
      const state = await governanceState(
        context,
        ruleId,
        `state-${targetState}`,
      );
      if (
        RULE_GOVERNANCE_STAGES.indexOf(state.state) >=
        RULE_GOVERNANCE_STAGES.indexOf(targetState)
      )
        return state;
      const digest = await impactDigest(
        context,
        ruleId,
        `impact-${targetState}`,
      );
      return ensureOk(
        await apiPost(
          context,
          `/engine/rule/rules/${ruleId}/governance/transitions`,
          {
            ...ctx,
            targetState,
            impactDigest: digest,
            reason,
            ...(publishEvidence ? { publishEvidence } : {}),
          },
          `transition-${targetState}`,
        ),
        `推进规则到 ${targetState}`,
      );
    },
  );
}

async function governRule(browser, credentials, ruleId) {
  await transition(
    browser,
    credentials,
    SANDBOX_ACTOR_ROLES.engineOperator,
    ruleId,
    "REVIEWED",
    "沙盘四类样例测试、术语覆盖、冲突检查与真实快照试运行全绿，确认进入发布验证。",
  );
  await transition(
    browser,
    credentials,
    SANDBOX_ACTOR_ROLES.engineOperator,
    ruleId,
    "SHADOW",
    "规则质量校验完成，进入无外部副作用影子运行。",
  );
  await transition(
    browser,
    credentials,
    SANDBOX_ACTOR_ROLES.engineOperator,
    ruleId,
    "CANARY",
    "影子验证完成，进入演练机构灰度。",
  );
  await transition(
    browser,
    credentials,
    SANDBOX_ACTOR_ROLES.engineOperator,
    ruleId,
    "FULL",
    "演练机构灰度验证完成，激活沙盘规则；不进入真实诊疗链路。",
    {
      qualityGate: {
        schemaValid: true,
        terminologyBindingComplete: true,
        dependencyIntegrityVerified: true,
        safetyMonotonicityVerified: true,
        impactSimulationPassed: true,
        summary: "四类样例、真实快照、影响摘要和技术质量门均已通过",
      },
    },
  );
}

async function discoverRequiredOuterAssets(context, dependencies) {
  const response = await apiGet(
    context,
    "/engine/pathway/pathway-templates?status=PUBLISHED&page=0&size=100",
    "find-sandbox-pathway",
  );
  const pathways = pageItems(ensureOk(response, "读取已发布路径资产"));
  return dependencies.map((dependency) => {
    if (dependency.assetType !== "PATHWAY") {
      throw new Error(`暂不支持的沙盘外圈资产类型 ${dependency.assetType}`);
    }
    const pathway = pathways.find(
      (item) =>
        item.templateCode === dependency.assetCode &&
        String(item.templateVersion) === dependency.assetVersion,
    );
    if (!pathway?.templateId) {
      throw new Error(
        `缺少沙盘外圈精确资产 ${dependency.assetCode}@${dependency.assetVersion}`,
      );
    }
    return {
      ...dependency,
      assetId: pathway.templateId,
      assetIdentity: dependency.assetCode,
      versionId: pathway.versionId ?? null,
    };
  });
}

function runtimeAssetSelection(item) {
  const assetIdentity = item.assetIdentity ?? item.assetCode ?? item.assetId;
  if (!assetIdentity) {
    throw new Error(`运行资产缺少稳定身份: ${JSON.stringify(item).slice(0, 300)}`);
  }
  return {
    assetType: item.assetType,
    assetIdentity,
    ...(item.versionId ? { versionId: item.versionId } : {}),
  };
}

function runtimeCoversDesiredAssets(currentDetail, desiredAssets) {
  const active = new Set(
    (currentDetail?.items ?? [])
      .filter((item) => item.entryState === "ACTIVE")
      .map((item) =>
        [
          item.assetType,
          item.assetIdentity,
          item.versionId ?? "",
        ].join(":"),
      ),
  );
  return desiredAssets.every((item) => {
    const exact = [
      item.assetType,
      item.assetIdentity,
      item.versionId ?? "",
    ].join(":");
    if (active.has(exact)) return true;
    if (item.versionId) return false;
    return [...active].some((candidate) =>
      candidate.startsWith(`${item.assetType}:${item.assetIdentity}:`),
    );
  });
}

async function readCurrentHospitalRuntime(context, hospitalId, stage) {
  const result = await apiGet(
    context,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
    stage,
  );
  if (result.status === 404) return null;
  return ensureOk(result, "读取当前机构生效版本");
}

async function activateRuntimeRelease(context, ctx, assets) {
  const targetOrgUnitId = runtimeTargetOrgUnitId(ctx);
  const desiredAssets = assets.map(runtimeAssetSelection);
  const currentDetail = await readCurrentHospitalRuntime(
    context,
    targetOrgUnitId,
    "sandbox-runtime-current",
  );
  if (currentDetail && runtimeCoversDesiredAssets(currentDetail, desiredAssets)) {
    const status = ensureOk(
      await apiGet(
        context,
        "/engine/sandbox/runtime-status",
        "verify-sandbox-runtime-status",
      ),
      "验证沙盘机构生效版本",
    );
    return { reused: true, release: currentDetail.release, status };
  }

  const baseline = ensureOk(
    await apiGet(
      context,
      "/engine/releases/platform-baselines/current",
      "sandbox-platform-baseline-current",
    ),
    "读取平台当前标准版本",
  );
  const baselineReleaseId = baseline?.release?.baselineReleaseId;
  if (!baselineReleaseId) {
    throw new Error("平台当前标准版本缺少 baselineReleaseId，无法生成机构生效版本");
  }
  const release = ensureOk(
    await apiPost(
      context,
      `/engine/releases/hospitals/${encodeURIComponent(targetOrgUnitId)}/runtime-releases`,
      {
        platformBaselineReleaseId: baselineReleaseId,
        expectedCurrentReleaseId: currentDetail?.release?.releaseId ?? null,
        activeAssets: desiredAssets,
      },
      "activate-sandbox-runtime-release",
    ),
    "发布沙盘机构生效版本",
  );
  const status = ensureOk(
    await apiGet(
      context,
      "/engine/sandbox/runtime-status",
      "verify-sandbox-runtime-status",
    ),
    "验证沙盘机构生效版本",
  );
  if (
    !status?.ready ||
    status.runtimeReleaseId !== release.releaseId ||
    status.externalSideEffects !== false
  ) {
    throw new Error(
      `沙盘机构生效版本未就绪: ${JSON.stringify({ release, status }).slice(0, 1200)}`,
    );
  }
  return { reused: false, release, status };
}

export async function runSeed() {
  await mkdir(evidenceDir, { recursive: true });
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(manifest, process.env.SEED_ONLY ?? "");
  const runtimeDigest = deriveSandboxRuntimeDigest(manifest);
  const summary = {
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    runtimeDigest,
    versionResolution: "MANIFEST_SHA256_RUNTIME_RELEASE",
    runnableRuleCodes: selected.runnable.map((item) => item.ruleCode),
    blockedRuleCodes: selected.blocked.map((item) => item.ruleCode),
    results: [],
    runtimeRelease: null,
    failures: [],
  };
  if (process.env.SEED_VALIDATE_ONLY === "1") return summary;

  const browser = await chromium.launch(resolveChromiumLaunchOptions());
  try {
    const credentials = await loadCredentials();
    const expectedTenantId = manifest.scenarios[0].institution.tenantId;
    const accountTenantId = requireAccount(
      credentials,
      SANDBOX_ACTOR_ROLES.snapshotWriter,
    ).tenantId;
    if (accountTenantId !== expectedTenantId) {
      throw new Error(
        `演练机构凭据租户 ${accountTenantId} 与规则归属 ${expectedTenantId} 不一致`,
      );
    }

    for (const rule of selected.runnable) {
      try {
        const snapshots = await seedSnapshots(
          browser,
          credentials,
          rule,
        );
        const created = await createAndTestRule(
          browser,
          credentials,
          rule,
          snapshots,
        );
        if (!created.alreadyPublished) {
          await governRule(browser, credentials, created.ruleId);
        }
        const verification = await withRole(
          browser,
          credentials,
          SANDBOX_ACTOR_ROLES.engineOperator,
          async (context) =>
            ruleDetail(
              context,
              created.ruleId,
              `verify-published-${rule.ruleCode}`,
            ),
        );
        if (!publishedRule(verification))
          throw new Error(`${rule.ruleCode} 治理后未发布`);
        summary.results.push({
          ruleCode: rule.ruleCode,
          ruleId: created.ruleId,
          assetIdentity: rule.ruleCode,
          assetVersion: ruleVersionNo(verification),
          versionId: ruleVersionId(verification),
          snapshotIds: snapshots.map((item) => item.snapshotId),
          result: "PASS",
        });
      } catch (error) {
        summary.failures.push({
          ruleCode: rule.ruleCode,
          detail: String(error).slice(0, 1200),
        });
      }
    }

    if (summary.failures.length === 0) {
      const adminContext = await login(
        browser,
        requireAccount(credentials, SANDBOX_ACTOR_ROLES.engineOperator),
        SANDBOX_ACTOR_ROLES.engineOperator,
      );
      try {
        const ctx = await envelope(adminContext, "sandbox-runtime");
        const outerAssets = await discoverRequiredOuterAssets(
          adminContext,
          manifest.dependencies,
        );
        const ruleAssets = summary.results.map((item) => ({
          assetType: "RULE",
          assetId: item.ruleId,
          assetIdentity: item.assetIdentity,
          assetVersion: item.assetVersion,
          versionId: item.versionId,
          assetCode: item.ruleCode,
        }));
        const releaseAssets = [...ruleAssets, ...outerAssets];
        const runtimeRelease = await activateRuntimeRelease(
          adminContext,
          ctx,
          releaseAssets,
        );
        summary.runtimeRelease = runtimeRelease;
      } finally {
        await adminContext.close();
      }
    }
  } finally {
    await browser.close();
  }

  await writeFile(
    path.join(evidenceDir, "seed-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  if (summary.failures.length > 0) {
    throw new Error(`沙盘铺底存在 ${summary.failures.length} 个失败`);
  }
  if (!summary.runtimeRelease?.status?.ready)
    throw new Error("沙盘铺底完成但 CURRENT 机构生效版本未就绪");
  return summary;
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  try {
    const summary = await runSeed();
    console.log(JSON.stringify(summary, null, 2));
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
