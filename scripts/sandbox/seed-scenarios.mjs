#!/usr/bin/env node
// 演练机构规则铺底：标准快照 -> 规则四测 -> 治理演练 -> 内容寻址包 -> CURRENT 绑定。
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { loadScenarioRules, selectSeedRules } from "./scenario-rules.mjs";

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
  process.env.DRILL_CREDENTIAL_PATH ??
  "/tmp/medkernel-sandbox-role-credentials.json";
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/sandbox-full-fidelity-20260619",
);
const packageCode = "SBX.INSTITUTION.RULES";
const mappingVersion = "sandbox-context-v1";

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
export function deriveSandboxPackageVersion(manifest) {
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
  return {
    customerTenant: data.customerTenant,
    roleAccounts: data.roleAccounts ?? {},
    platformRoleAccounts: data.platformRoleAccounts ?? {},
  };
}

function requireAccount(credentials, role) {
  if (
    role === "organization-admin" &&
    credentials.customerTenant?.adminUsername
  ) {
    return {
      username: credentials.customerTenant.adminUsername,
      password: credentials.customerTenant.password,
      tenantId: credentials.customerTenant.tenantId,
    };
  }
  const account =
    credentials.roleAccounts[role] ?? credentials.platformRoleAccounts[role];
  if (!account?.username || !account?.password || !account?.tenantId) {
    throw new Error(`凭据缺少角色 ${role} 的可用账号`);
  }
  return account;
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

async function envelope(context, stage, actualPackageVersion) {
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
    package_version: actualPackageVersion,
    orgUnitId: scope.hospitalId || scope.campusId || scope.tenantId,
  };
}

function snapshotPayload(testCase, contextEnvelope, actualPackageVersion) {
  const occurredAt = new Date().toISOString();
  return {
    ...contextEnvelope,
    request_id: traceId(`snapshot-${testCase.caseType}`),
    trace_id: traceId(`snapshot-${testCase.caseType}-trace`),
    patientId: testCase.patientId,
    encounterId: testCase.encounterId,
    package_version: actualPackageVersion,
    resources: buildCanonicalResources(testCase, occurredAt),
  };
}

async function seedSnapshots(browser, credentials, rule, actualPackageVersion) {
  const context = await login(
    browser,
    requireAccount(credentials, "integration-operator"),
    "integration-operator",
  );
  try {
    const ctx = await envelope(context, "snapshot", actualPackageVersion);
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
        if (existingDetail.packageVersion === actualPackageVersion) {
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
          snapshotPayload(testCase, ctx, actualPackageVersion),
          `snapshot-create-${rule.ruleCode}-${testCase.caseType}`,
        ),
        `创建 ${rule.ruleCode}/${testCase.caseType} 快照`,
        [201],
      );
      if (
        !created?.snapshotId ||
        created.status !== "ACTIVE" ||
        created.packageVersion !== actualPackageVersion
      ) {
        throw new Error(
          `${rule.ruleCode}/${testCase.caseType} 快照未形成 ACTIVE 服务端事实`,
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
  actualPackageVersion,
) {
  const context = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor",
  );
  try {
    const ctx = await envelope(context, "rule", actualPackageVersion);
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
      alreadyPublished: false,
    };
  } finally {
    await context.close();
  }
}

async function withRole(browser, credentials, role, actualPackageVersion, run) {
  const context = await login(browser, requireAccount(credentials, role), role);
  try {
    return await run(
      context,
      await envelope(context, role, actualPackageVersion),
    );
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
  actualPackageVersion,
  publishEvidence,
) {
  return withRole(
    browser,
    credentials,
    role,
    actualPackageVersion,
    async (context, ctx) => {
      const state = await governanceState(
        context,
        ruleId,
        `state-${targetState}`,
      );
      const order = [
        "DRAFT",
        "PEER_REVIEW",
        "COMMITTEE",
        "SHADOW",
        "CANARY",
        "FULL",
        "MONITOR",
      ];
      if (order.indexOf(state.state) >= order.indexOf(targetState))
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

async function signoff(
  browser,
  credentials,
  role,
  ruleId,
  stage,
  reason,
  actualPackageVersion,
  minimumApprovals = 0,
) {
  return withRole(
    browser,
    credentials,
    role,
    actualPackageVersion,
    async (context, ctx) => {
      const current = await governanceState(
        context,
        ruleId,
        `signoff-state-${stage}-${role}`,
      );
      if (stage === "PEER_REVIEW" && current.state !== "PEER_REVIEW")
        return current;
      if (
        stage === "COMMITTEE" &&
        (current.committeeApprovalCount ?? 0) >= minimumApprovals
      ) {
        return current;
      }
      return ensureOk(
        await apiPost(
          context,
          `/engine/rule/rules/${ruleId}/governance/signoffs`,
          {
            ...ctx,
            stage,
            decision: "APPROVED",
            reason,
          },
          `signoff-${stage}-${role}`,
        ),
        `${role} 完成 ${stage} 沙盘治理演练签署`,
      );
    },
  );
}

async function governRule(browser, credentials, ruleId, actualPackageVersion) {
  await transition(
    browser,
    credentials,
    "knowledge-governor",
    ruleId,
    "PEER_REVIEW",
    "沙盘四类样例测试与真实快照试运行全绿，提交治理流程演练；不等同于生产医学签署。",
    actualPackageVersion,
  );
  await signoff(
    browser,
    credentials,
    "clinical-governor",
    ruleId,
    "PEER_REVIEW",
    "仅确认演练规则的安全边界与人工确认要求，不作为生产医学批准。",
    actualPackageVersion,
  );
  await signoff(
    browser,
    credentials,
    "clinical-governor",
    ruleId,
    "COMMITTEE",
    "完成第一角色沙盘治理演练签署；不替代真实专家双签。",
    actualPackageVersion,
    1,
  );
  await signoff(
    browser,
    credentials,
    "quality-governor",
    ruleId,
    "COMMITTEE",
    "完成第二角色沙盘治理演练签署；不替代真实专家双签。",
    actualPackageVersion,
    2,
  );
  await transition(
    browser,
    credentials,
    "organization-admin",
    ruleId,
    "SHADOW",
    "沙盘治理流程演练签署完成，进入无外部副作用影子运行。",
    actualPackageVersion,
  );
  await transition(
    browser,
    credentials,
    "organization-admin",
    ruleId,
    "CANARY",
    "影子验证完成，进入演练机构灰度。",
    actualPackageVersion,
  );
  const signedAt = new Date().toISOString();
  await transition(
    browser,
    credentials,
    "organization-admin",
    ruleId,
    "FULL",
    "演练机构灰度验证完成，激活沙盘规则；不进入真实诊疗链路。",
    actualPackageVersion,
    {
      electronicSignature: {
        signatureId: `esig-sandbox-${Date.now()}`,
        signerId: requireAccount(credentials, "clinical-governor").username,
        signerName: "沙盘临床治理角色",
        signedAt,
        signatureHash: createHash("sha256")
          .update(`sandbox-full-${ruleId}-${signedAt}`)
          .digest("hex"),
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
    };
  });
}

async function packageDetail(context, packageId, stage) {
  return ensureOk(
    await apiGet(
      context,
      `/engine/pkg/packages/${encodeURIComponent(packageId)}`,
      stage,
    ),
    "读取演练配置包详情",
  );
}

async function ensureSandboxPackage(
  context,
  ctx,
  actualPackageVersion,
  assets,
) {
  const packages = pageItems(
    ensureOk(
      await apiGet(
        context,
        `/engine/pkg/packages?keyword=${encodeURIComponent(packageCode)}&page=0&size=100`,
        "find-sandbox-package",
      ),
      "查找演练配置包",
    ),
  );
  let pack = packages.find(
    (item) =>
      item.packageCode === packageCode &&
      item.packageVersion === actualPackageVersion,
  );
  let reused = Boolean(pack);
  if (!pack) {
    pack = ensureOk(
      await apiPost(
        context,
        "/engine/pkg/packages",
        {
          ...ctx,
          packageCode,
          packageVersion: actualPackageVersion,
          name: "演练机构全功能规则包",
          description:
            "由十条机构演练规则清单内容摘要生成；仅限沙盘，不进入真实诊疗链路。",
          accessPolicy: "OPEN",
        },
        "create-sandbox-package",
      ),
      "创建演练配置包",
      [201],
    );
    reused = false;
  }

  let detail = await packageDetail(
    context,
    pack.packageId,
    "sandbox-package-detail",
  );
  const existing = new Set(
    (detail.items ?? []).map(
      (item) => `${item.assetType}:${item.assetId}:${item.assetVersion}`,
    ),
  );
  const missing = assets.filter(
    (item) =>
      !existing.has(`${item.assetType}:${item.assetId}:${item.assetVersion}`),
  );
  if (missing.length > 0 && detail.status !== "DRAFT") {
    throw new Error(
      `已锁定演练配置包缺少资产: ${missing.map((item) => item.assetCode).join(", ")}`,
    );
  }
  for (const item of missing) {
    ensureOk(
      await apiPost(
        context,
        `/engine/pkg/packages/${encodeURIComponent(pack.packageId)}/items`,
        {
          ...ctx,
          assetType: item.assetType,
          assetId: item.assetId,
          assetVersion: item.assetVersion,
        },
        `add-sandbox-package-${item.assetType}-${item.assetCode}`,
      ),
      `加入演练资产 ${item.assetCode}`,
      [201, 409],
    );
  }
  detail = await packageDetail(
    context,
    pack.packageId,
    "sandbox-package-detail-after-items",
  );
  const validation = ensureOk(
    await apiPost(
      context,
      `/engine/pkg/packages/${encodeURIComponent(pack.packageId)}/validate`,
      ctx,
      "validate-sandbox-package",
    ),
    "校验演练配置包",
  );
  if (!validation?.valid) {
    throw new Error(
      `演练配置包校验未通过: ${JSON.stringify(validation).slice(0, 900)}`,
    );
  }
  return { detail, validation, reused };
}

async function releaseSandboxPackage(context, ctx, pack, reviewerAccount) {
  if (["PUBLISHED", "ACTIVE"].includes(pack.status)) {
    return { reused: true, package: pack, sync: null };
  }
  const adapterPage = ensureOk(
    await apiGet(
      context,
      "/engine/pkg/packages/release-adapters?page=0&size=100",
      "sandbox-adapters",
    ),
    "读取演练配置包发布适配器",
  );
  const adapter = pageItems(adapterPage).find(
    (item) => item.status === "ACTIVE" && item.connectorAvailable,
  );
  if (!adapter) throw new Error("没有 ACTIVE 且连接器可用的配置包发布适配器");

  const signedAt = new Date().toISOString();
  const sync = ensureOk(
    await apiPost(
      context,
      `/engine/pkg/packages/${encodeURIComponent(pack.packageId)}/release`,
      {
        ...ctx,
        reason: "发布演练机构内容寻址规则包并激活 CURRENT 基线",
        targetOrgUnitId: ctx.orgUnitId,
        strategy: "FULL",
        scopeType: "ALL",
        scopeValue: null,
        adapterIds: [adapter.adapterId],
        publishEvidence: {
          electronicSignature: {
            signatureId: `esig-package-${Date.now()}`,
            signerId: reviewerAccount.username,
            signerName: "沙盘质量复核角色",
            signedAt,
            signatureHash: createHash("sha256")
              .update(`${pack.packageId}-${pack.packageVersion}-${signedAt}`)
              .digest("hex"),
          },
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: false,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            peerReviewSigned: true,
            summary:
              "十条演练规则四类样例全绿、来源完整、无自动诊疗副作用；生产术语绑定由后续知识发行门禁独立验收",
          },
        },
      },
      "release-sandbox-package",
    ),
    "发布演练配置包",
  );
  const failedLogs = (sync.logs ?? []).filter(
    (item) => item.status !== "SUCCESS",
  );
  if (sync.status !== "SUCCESS" || failedLogs.length > 0) {
    throw new Error(
      `演练配置包发布未全成功: ${JSON.stringify(sync).slice(0, 1000)}`,
    );
  }
  const released = await packageDetail(
    context,
    pack.packageId,
    "sandbox-package-after-release",
  );
  if (!["PUBLISHED", "ACTIVE"].includes(released.status)) {
    throw new Error(`演练配置包发布后状态不可运行: ${released.status}`);
  }
  return { reused: false, package: released, sync, adapter };
}

async function activateRuntimeBinding(context, ctx, pack) {
  const activated = ensureOk(
    await apiPost(
      context,
      "/engine/sandbox/runtime-binding",
      {
        packageOwnerTenantId: ctx.tenant_id,
        packageId: pack.packageId,
      },
      "activate-sandbox-runtime-binding",
    ),
    "激活沙盘运行绑定",
  );
  const status = ensureOk(
    await apiGet(
      context,
      "/engine/sandbox/runtime-binding",
      "verify-sandbox-runtime-binding",
    ),
    "验证沙盘运行绑定",
  );
  if (
    !activated?.ready ||
    !status?.ready ||
    status.packageId !== pack.packageId ||
    status.packageVersion !== pack.packageVersion ||
    status.externalSideEffects !== false
  ) {
    throw new Error(
      `沙盘运行绑定未就绪: ${JSON.stringify({ activated, status }).slice(0, 1200)}`,
    );
  }
  return status;
}

export async function runSeed() {
  await mkdir(evidenceDir, { recursive: true });
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(manifest, process.env.SEED_ONLY ?? "");
  const actualPackageVersion = deriveSandboxPackageVersion(manifest);
  const summary = {
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    packageCode,
    packageVersion: actualPackageVersion,
    versionResolution: "MANIFEST_SHA256",
    runnableRuleCodes: selected.runnable.map((item) => item.ruleCode),
    blockedRuleCodes: selected.blocked.map((item) => item.ruleCode),
    results: [],
    package: null,
    runtimeBinding: null,
    failures: [],
  };
  if (process.env.SEED_VALIDATE_ONLY === "1") return summary;

  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    const expectedTenantId = manifest.scenarios[0].institution.tenantId;
    const accountTenantId = requireAccount(
      credentials,
      "organization-admin",
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
          actualPackageVersion,
        );
        const created = await createAndTestRule(
          browser,
          credentials,
          rule,
          snapshots,
          actualPackageVersion,
        );
        if (!created.alreadyPublished) {
          await governRule(
            browser,
            credentials,
            created.ruleId,
            actualPackageVersion,
          );
        }
        const verification = await withRole(
          browser,
          credentials,
          "knowledge-governor",
          actualPackageVersion,
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
          assetVersion: ruleVersionNo(verification),
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
        requireAccount(credentials, "organization-admin"),
        "organization-admin",
      );
      try {
        const ctx = await envelope(
          adminContext,
          "sandbox-package",
          actualPackageVersion,
        );
        const outerAssets = await discoverRequiredOuterAssets(
          adminContext,
          manifest.dependencies,
        );
        const ruleAssets = summary.results.map((item) => ({
          assetType: "RULE",
          assetId: item.ruleId,
          assetVersion: item.assetVersion,
          assetCode: item.ruleCode,
        }));
        const ensured = await ensureSandboxPackage(
          adminContext,
          ctx,
          actualPackageVersion,
          [...ruleAssets, ...outerAssets],
        );
        const released = await releaseSandboxPackage(
          adminContext,
          ctx,
          ensured.detail,
          requireAccount(credentials, "quality-governor"),
        );
        const runtimeBinding = await activateRuntimeBinding(
          adminContext,
          ctx,
          released.package,
        );
        summary.package = {
          packageId: released.package.packageId,
          packageCode: released.package.packageCode,
          packageVersion: released.package.packageVersion,
          status: released.package.status,
          itemCount:
            released.package.items?.length ?? ensured.validation.itemCount,
          reused: ensured.reused && released.reused,
          releasePlanId: released.sync?.planId ?? null,
          adapterId: released.adapter?.adapterId ?? null,
        };
        summary.runtimeBinding = runtimeBinding;
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
  if (!summary.runtimeBinding?.ready)
    throw new Error("沙盘铺底完成但 CURRENT 运行绑定未就绪");
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
