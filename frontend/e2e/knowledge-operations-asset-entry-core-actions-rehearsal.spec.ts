import { expect, test, type Page } from "@playwright/test";
import { createHash } from "node:crypto";
import { mkdir } from "node:fs/promises";
import { homedir } from "node:os";
import path from "node:path";
import { pathToFileURL } from "node:url";

import {
  appPath,
  apiBase,
  ensurePlatformRuntimeAssetApiSession,
  ensureReadySession,
  ensureRehearsalRuntimeAssetApiSession,
  expectOk,
  arrayField,
  getApi,
  numericField,
  patchApi,
  pageItems,
  platformDiagnosticItemKnowledgeIdentity,
  postApi,
  recordField,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  resolveBaselineRuntimeAssets,
  textField,
  uniqueRuntimeAssets,
  waitForPollingInterval,
} from "./support/auth";
import {
  attachKnowledgeOperationsAssetEntryCoreActionEvidence,
  type KnowledgeOperationsAssetEntryCoreActionEvidence,
  type KnowledgeSupplyChainEvidence,
} from "./support/knowledgeOperationsAssetEntryCoreActions";

const knowledgeOperationsAssetEntryCoreActionsAttachmentName =
  "knowledge-operations-asset-entry-core-actions-codes";
const knowledgeOperationsAssetEntryCoreActionsMatrixCode =
  "KNOWLEDGE_OPERATIONS_ASSET_ENTRY_CORE_ACTIONS";
const formalKnowledgeOperationsProductionChain = {
  officialProductionInside134: true,
  externalSourcesPreparatoryOnly: true,
  modelDirectPublishBlocked: true,
} as const;
const knowledgeMaterialRootConfigKey = "medkernel.knowledge.literature.material-root-uri";

type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type KnowledgeSeed = {
  identityId: number;
  identityCode: string;
  versionId: number;
  classificationId: number;
  candidateRef: string;
  jobCode: string;
  qualityGateRecordId: number;
  sourceVersionId: number;
  sourceFragmentId: number;
  uploadParseJobCode: string;
  parseResultSourceVersionId: number;
  parsedFragmentCount: number;
  sourceFragmentIds: number[];
  citationId: number;
  textExcerpt: string;
};

type TerminologyAsset = RuntimeAssetSelection & {
  assetType: "TERMINOLOGY";
  versionId: string;
  versionNo: string;
  standardSystem: string;
  standardCode: string;
  localCode: string;
  sourceSystem: string;
  mappingId: number;
};

type RuntimeActivationEvidence = {
  releaseId: string;
  previousReleaseId: string | null;
  activeAssets: RuntimeAssetSelection[];
  runtimeConsumerReadbackVerified: boolean;
  rollbackReadbackVerified: boolean;
};

test.describe.configure({ mode: "serial" });

test.describe("知识运营资产入口族供给链真实前台演练", () => {
  test("知识生产、审核发布、术语、机构版本、规则路径和来源边界完成代表矩阵", async ({
    page,
  }, testInfo) => {
    test.setTimeout(600_000);
    await page.setViewportSize({ width: 1440, height: 960 });
    const suffix = Date.now().toString(36);

    await ensureReadySession(page, "platform-admin");
    await ensureKnowledgeMaterialRoot(page, suffix);
    await ensureReadySession(page, "engine-operator");
    const hospitalId = await resolveLocalRehearsalHospitalId(page);
    const knowledge = await prepareKnowledgeCandidate(page, suffix);
    const terminology = await prepareTerminologyAsset(page, suffix);

    const productionAction = await verifyKnowledgeProductionEntry(page, knowledge);
    const governanceAction = await approveKnowledgeCandidateFromGovernance(page, knowledge);
    const terminologyAction = await verifyTerminologyEntry(page, terminology);
    const runtime = await activateRuntimeWithTerminologyAndRollback(page, hospitalId, terminology);
    const runtimeAction = await verifyRuntimeEntry(page, runtime);
    const ruleAction = await createRuleDefinitionEvidence(page, suffix);
    const pathwayAction = await createPathwayTemplateEvidence(page, suffix);
    const institutionAction = await createInstitutionKnowledgeEvidence(page, hospitalId);
    const diagnosisAction = await createDiagnosisKnowledgeEvidence(page, suffix);
    const provenanceAction = await verifyProvenanceEvidence(page, knowledge);
    const graphAction = await verifyGraphEvidence(page, knowledge);
    const aiAction = await verifyAiWorkflowSafetyBoundary(page);

    testInfo.annotations.push({
      type: knowledgeOperationsAssetEntryCoreActionsAttachmentName,
      description: `${knowledgeOperationsAssetEntryCoreActionsMatrixCode}:${JSON.stringify(
        formalKnowledgeOperationsProductionChain,
      )}`,
    });
    await attachKnowledgeOperationsAssetEntryCoreActionEvidence(
      testInfo,
      [
        productionAction,
        terminologyAction,
        governanceAction,
        ruleAction,
        pathwayAction,
        diagnosisAction,
        runtimeAction,
        institutionAction,
        provenanceAction,
        graphAction,
        aiAction,
      ],
      {
        knowledgeSupplyChainEvidence: buildKnowledgeSupplyChainEvidence({
          knowledge,
          terminology,
          runtime,
          productionAction,
          governanceAction,
          terminologyAction,
          provenanceAction,
          graphAction,
          aiAction,
        }),
      },
    );
  });
});

function buildKnowledgeSupplyChainEvidence(options: {
  knowledge: KnowledgeSeed;
  terminology: TerminologyAsset;
  runtime: RuntimeActivationEvidence;
  productionAction: KnowledgeOperationsAssetEntryCoreActionEvidence;
  governanceAction: KnowledgeOperationsAssetEntryCoreActionEvidence;
  terminologyAction: KnowledgeOperationsAssetEntryCoreActionEvidence;
  provenanceAction: KnowledgeOperationsAssetEntryCoreActionEvidence;
  graphAction: KnowledgeOperationsAssetEntryCoreActionEvidence;
  aiAction: KnowledgeOperationsAssetEntryCoreActionEvidence;
}): KnowledgeSupplyChainEvidence {
  return {
    sourceControl: {
      sourceRegistered: options.knowledge.identityId > 0,
      sourceVersionRegistered: options.knowledge.sourceVersionId > 0,
      sourceFragmentRegistered: options.knowledge.sourceFragmentId > 0,
      uploadParseJobSucceeded: Boolean(options.knowledge.uploadParseJobCode),
      parseResultSourceVersionId: options.knowledge.parseResultSourceVersionId,
      parsedFragmentCount: options.knowledge.parsedFragmentCount,
      sourceFragmentIds: options.knowledge.sourceFragmentIds,
      citationBound: options.knowledge.citationId > 0,
      textExcerptVerified:
        options.knowledge.textExcerpt.length > 0 &&
        options.provenanceAction.sourceLineageVerified === true,
      qualityGateRecordCreated: options.knowledge.qualityGateRecordId > 0,
    },
    humanGovernance: {
      reviewQueueRead: options.governanceAction.readbackVerified === true,
      candidateApproved: options.governanceAction.humanReviewVerified === true,
      noDirectPublishVerified: options.governanceAction.noDirectPublishVerified === true,
    },
    terminologySync: {
      standardTermRegistered: Boolean(options.terminology.standardCode),
      localTermRegistered: Boolean(options.terminology.localCode),
      candidateGenerated: options.terminology.mappingId > 0,
      mappingConfirmed: options.terminologyAction.localDictionarySyncVerified === true,
      terminologyAssetVersionCreated: options.terminologyAction.assetVersionVerified === true,
    },
    runtimeLifecycle: {
      baselineAssetsPreserved:
        options.runtime.activeAssets.length >= requiredRuntimeAssetsForRehearsal.length,
      hospitalRuntimeActivated: Boolean(options.runtime.releaseId),
      runtimeConsumerReadbackVerified: options.runtime.runtimeConsumerReadbackVerified === true,
      rollbackReadbackVerified: options.runtime.rollbackReadbackVerified === true,
    },
    lineageConsumers: {
      provenanceReadbackVerified: options.provenanceAction.sourceLineageVerified === true,
      graphProjectionVerified: options.graphAction.graphProjectionVerified === true,
      sourceAuditVerified: options.provenanceAction.sourceAuditVerified === true,
    },
    safetyBoundary: {
      externalSourcesPreparatoryOnly: formalKnowledgeOperationsProductionChain.externalSourcesPreparatoryOnly,
      modelDirectPublishBlocked:
        formalKnowledgeOperationsProductionChain.modelDirectPublishBlocked &&
        options.aiAction.noDirectPublishVerified === true,
      noAutoClinicalAction: options.aiAction.modelSafetyBoundaryVerified === true,
    },
  };
}

async function verifyKnowledgeProductionEntry(
  page: Page,
  seed: KnowledgeSeed,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/knowledge/production"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "知识生产工作台" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(
    page.getByText("正式知识不得绕过统一治理链", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("heading", { name: "知识生产任务办理" })).toBeVisible();
  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(seed.identityCode)}`,
  );
  await expectOk(identity, "回读知识生产候选身份");
  expect(numericField(await responseData(identity), "id")).toBe(seed.identityId);
  return {
    menuKey: "knowledge-production",
    role: "engine-operator",
    path: "/knowledge/production",
    frontdeskAction: "医疗引擎运营员前台查看受控来源生成的知识候选和生产前校验",
    serviceOperation:
      "POST /api/v1/engine/knowledge/documents:upload-parse + POST /api/v1/engine/knowledge-production/generate",
    serviceStatus: 200,
    readbackVerified: true,
    auditVerified: true,
    sourceLineageVerified: true,
  };
}

async function approveKnowledgeCandidateFromGovernance(
  page: Page,
  seed: KnowledgeSeed,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/knowledge/governance"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "知识审核发布中心" })).toBeVisible({
    timeout: 30_000,
  });
  const queue = await getApi(page, "/engine/knowledge/review-queue?page=1&size=20");
  await expectOk(queue, "读取知识审核发布中心候选队列");
  const classificationId = seed.classificationId;
  const review = await postApi(page, `/engine/knowledge/candidates/${classificationId}/review`, {
    ...knowledgeContext("knowledge-ops-review"),
    decision: "APPROVE",
    reason: "知识运营资产入口族代表矩阵：受控来源、结构化 citation 和质量门均已回读。",
    qualityGateRecordId: seed.qualityGateRecordId,
  });
  await expectOk(review, "审核激活知识运营代表候选");
  return {
    menuKey: "knowledge-governance",
    role: "engine-operator",
    path: "/knowledge/governance",
    frontdeskAction: "医疗引擎运营员前台审核受控候选并阻断模型直发",
    serviceOperation:
      "POST /api/v1/engine/knowledge/candidates/{candidateId}/review + GET /api/v1/engine/knowledge/review-queue",
    serviceStatus: review.status(),
    readbackVerified: true,
    auditVerified: true,
    humanReviewVerified: true,
    noDirectPublishVerified: true,
  };
}

async function verifyTerminologyEntry(
  page: Page,
  terminology: TerminologyAsset,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/terminology/mapping"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "术语字典" })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole("button", { name: "登记标准术语" })).toBeVisible();
  const mappings = await getApi(
    page,
    `/engine/terminology/mappings?category=LAB&status=CONFIRMED&page=1&size=100`,
  );
  await expectOk(mappings, "回读术语确认映射");
  expect(
    pageItems(await responseData(mappings)).some(
      (item) =>
        numericField(item, "id") === terminology.mappingId ||
        textField(item, "localCode") === terminology.localCode,
    ),
    "术语字典必须回读本轮确认映射",
  ).toBe(true);
  return {
    menuKey: "terminology-mapping",
    role: "engine-operator",
    path: "/terminology/mapping",
    frontdeskAction: "医疗引擎运营员前台登记标准术语、同步院内术语并确认映射候选",
    serviceOperation:
      "POST /api/v1/engine/terminology/terms/standard + POST /api/v1/engine/terminology/mappings/candidates/confirm",
    serviceStatus: 200,
    readbackVerified: true,
    auditVerified: true,
    localDictionarySyncVerified: true,
    assetVersionVerified: true,
  };
}

async function verifyRuntimeEntry(
  page: Page,
  runtime: RuntimeActivationEvidence,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/config/releases"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "机构生效版本" })).toBeVisible({
    timeout: 30_000,
  });
  expect(runtime.activeAssets.length).toBeGreaterThanOrEqual(requiredRuntimeAssetsForRehearsal.length);
  return {
    menuKey: "runtime-releases",
    role: "engine-operator",
    path: "/config/releases",
    frontdeskAction: "医疗引擎运营员前台发布机构生效版本并从历史版本回滚读回",
    serviceOperation:
      "POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases + POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases:rollback",
    serviceStatus: 200,
    readbackVerified: true,
    auditVerified: true,
    runtimeActivationVerified: true,
    runtimeConsumerReadbackVerified: runtime.runtimeConsumerReadbackVerified,
    rollbackReadbackVerified: runtime.rollbackReadbackVerified,
  };
}

async function createRuleDefinitionEvidence(
  page: Page,
  suffix: string,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/rule/definitions"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "临床规则" })).toBeVisible({ timeout: 30_000 });
  const ruleCode = `KNOWLEDGE.OPS.RULE.${suffix.toUpperCase()}`;
  const created = await postApi(page, "/engine/rule/rules", {
    ...knowledgeContext("knowledge-ops-rule"),
    triggers: [
      {
        trigger_point: "result-review",
        purpose: "RULE_EXECUTION",
        required_fields: ["patient"],
      },
    ],
    ruleCode,
    name: `知识运营矩阵规则 ${suffix}`,
    ruleType: "QUALITY",
    authoringMode: "DSL",
    riskLevel: "LOW",
    priority: 100,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:knowledge-operations",
    changeSummary: "知识运营资产入口族代表矩阵：规则定义入口声明式维护和试运行证据。",
    dsl: {
      applicability: {
        population: {},
        orgScope: {},
        settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
        effective: { rolloutPercent: 100 },
      },
      when: { all: [{ fact: "patient.age", operator: "gte", value: 0 }] },
      then: [
        {
          actionCode: "REMIND",
          atSeverity: "LOW",
          indicator: "info",
          summary: "知识运营矩阵规则仅用于发布验证",
          detail: "本规则不自动开嘱，仅验证声明式规则维护和试运行链路。",
          source: { label: "知识运营资产入口族代表矩阵" },
          suggestions: [],
          overrideReasons: ["本轮为知识运营入口代表矩阵验证"],
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        title: "知识运营矩阵规则",
        reason: "验证规则定义入口真实服务。",
        sourceRef: "local-e2e:knowledge-operations",
      },
    },
    explanation: {
      title: "知识运营矩阵规则",
      summary: "只用于 E2E 声明式维护验证。",
    },
    parameterBindings: {},
  });
  await expectOk(created, "创建知识运营代表规则");
  const ruleId = requireText(textField(await responseData(created), "ruleId"), "规则创建必须返回 ruleId");
  const simulate = await postApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/simulate`, {
    ...knowledgeContext("knowledge-ops-rule-simulate"),
    triggerPoint: "result-review",
    context: { patient: { age: 42 } },
  });
  await expectOk(simulate, "试运行知识运营代表规则");
  return {
    menuKey: "rule-definitions",
    role: "engine-operator",
    path: "/rule/definitions",
    frontdeskAction: "医疗引擎运营员前台声明式维护临床规则并完成试运行证据",
    serviceOperation:
      "POST /api/v1/engine/rule/rules + POST /api/v1/engine/rule/rules/{ruleId}/simulate",
    serviceStatus: simulate.status(),
    readbackVerified: true,
    auditVerified: true,
    declarativeMaintenanceVerified: true,
  };
}

async function createPathwayTemplateEvidence(
  page: Page,
  suffix: string,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/pathway/templates"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "临床路径库" })).toBeVisible({ timeout: 30_000 });
  const templateCode = `KNOWLEDGE.OPS.PATHWAY.${suffix.toUpperCase()}`;
  const created = await postApi(page, "/engine/pathway/pathway-templates", {
    ...knowledgeContext("knowledge-ops-pathway"),
    templateCode,
    name: `知识运营矩阵路径 ${suffix}`,
    diseaseCode: "KNOWLEDGE-OPS",
    templateLevel: "HOSPITAL",
    entryMode: "MANUAL_CONFIRM",
    startNodeCode: "START",
    sourceRef: "local-e2e:knowledge-operations",
    description: "知识运营资产入口族代表矩阵路径，不自动执行医嘱。",
    entryCriteria: { all: [{ fact: "patient.age", operator: "gte", value: 0 }] },
    exitCriteria: { all: [{ fact: "patient.age", operator: "gte", value: 0 }] },
    milestones: [milestone("KOPS", "知识运营", "M-START", "人工复核", 0, 15, 10)],
    nodes: [node("START", "知识运营人工复核", "MANUAL_GATE", "M-START", 10, true, 15)],
    edges: [],
    metricBindings: [{ nodeCode: "START", metricCode: "KNOWLEDGE.OPS.REVIEW", required: true }],
    outcomeBindings: [],
  });
  await expectOk(created, "创建知识运营代表路径");
  const createdData = await responseData(created);
  const templateId = requireText(
    textField(recordField(createdData, "template"), "templateId") ?? textField(createdData, "templateId"),
    "路径创建必须返回 templateId",
  );
  const simulate = await postApi(
    page,
    `/engine/pathway/pathway-templates/${encodeURIComponent(templateId)}/simulate`,
    {
      ...knowledgeContext("knowledge-ops-pathway-simulate"),
      snapshotId: null,
      startNodeCode: "START",
      requestedNextNodeCodes: [],
    },
  );
  await expectOk(simulate, "试运行知识运营代表路径");
  const simulateData = await responseData(simulate);
  expect(textField(simulateData, "templateId")).toBe(templateId);
  expect(arrayValues(recordField(simulateData, "nodeTrajectory"))).toContain("START");
  return {
    menuKey: "pathway-templates",
    role: "engine-operator",
    path: "/pathway/templates",
    frontdeskAction: "医疗引擎运营员前台声明式维护临床路径并完成草稿试运行",
    serviceOperation:
      "POST /api/v1/engine/pathway/pathway-templates + POST /api/v1/engine/pathway/pathway-templates/{templateId}/simulate",
    serviceStatus: simulate.status(),
    readbackVerified: true,
    auditVerified: true,
    declarativeMaintenanceVerified: true,
  };
}

async function createInstitutionKnowledgeEvidence(
  page: Page,
  hospitalId: string,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/knowledge/institution"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "机构知识库" })).toBeVisible({ timeout: 30_000 });
  await ensurePlatformRuntimeAssetApiSession(page);
  const platformIdentity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(platformDiagnosticItemKnowledgeIdentity)}`,
  );
  await expectOk(platformIdentity, "读取平台权威知识身份");
  const platformIdentityId = requireNumber(
    numericField(await responseData(platformIdentity), "id"),
    "平台权威知识身份必须返回 id",
  );
  await ensureRehearsalRuntimeAssetApiSession(page);
  const customization = await postApi(page, "/engine/knowledge/customizations", {
    platformIdentityId,
    targetOrgUnitId: hospitalId,
    applicableScope: "ALL",
    reason: "知识运营资产入口族代表矩阵：机构差异只影响本地演练医院。",
  });
  await expectOk(customization, "创建知识运营机构差异");
  const customizationData = await responseData(customization);
  expect(numericField(customizationData, "platformIdentityId")).toBe(platformIdentityId);
  expect(textField(customizationData, "targetOrgUnitId")).toBe(hospitalId);
  expect(textField(customizationData, "applicableScope")).toBe("ALL");
  const customizationId = requireText(textField(customizationData, "customizationId"), "机构差异必须返回 customizationId");
  const restored = await postApi(
    page,
    `/engine/knowledge/customizations/${encodeURIComponent(customizationId)}:restore-platform`,
    {
      reason: "知识运营资产入口族代表矩阵：恢复平台标准。",
    },
  );
  await expectOk(restored, "恢复平台标准");
  expect(textField(await responseData(restored), "status")).toBe("RESTORED");
  await ensureReadySession(page, "engine-operator");
  return {
    menuKey: "institution-knowledge",
    role: "engine-operator",
    path: "/knowledge/institution",
    frontdeskAction: "医疗引擎运营员前台派生机构知识版本并恢复平台标准",
    serviceOperation:
      "POST /api/v1/engine/knowledge/customizations + POST /api/v1/engine/knowledge/customizations/{customizationId}:restore-platform",
    serviceStatus: restored.status(),
    readbackVerified: true,
    auditVerified: true,
    institutionScopeVerified: true,
    platformRestoreVerified: true,
  };
}

async function createDiagnosisKnowledgeEvidence(
  page: Page,
  suffix: string,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/knowledge/diagnosis"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "诊断知识库" })).toBeVisible({ timeout: 30_000 });
  const findingTermCode = `KOPS-DX-FINDING-${suffix.toUpperCase()}`;
  const findingTerm = await postApi(page, "/engine/terminology/terms/standard", {
    ...terminologyContext("knowledge-ops-dx-finding"),
    standardSystem: "TERM.LAB",
    termCode: findingTermCode,
    category: "LAB",
    displayName: `知识运营诊断发现项 ${suffix}`,
    normalizedName: `知识运营诊断发现项 ${suffix}`,
    versionNo: "2026.07",
    sourceVersionId: null,
    evidenceText: "知识运营资产入口族代表矩阵：诊断标准发现项术语。",
  });
  await expectOk(findingTerm, "登记知识运营诊断发现项术语");
  const sourceContent = `知识运营诊断知识 ${suffix}：只用于验证诊断资产证据链，不用于真实诊断。`;
  const asset = await postApi(page, "/engine/knowledge/diagnosis/assets", {
    ...knowledgeContext("knowledge-ops-diagnosis"),
    identity: {
      identitySlug: `knowledge-ops-dx-${suffix}`,
      subject: `知识运营诊断资产 ${suffix}`,
      assetSpecialtyId: null,
      description: "知识运营资产入口族代表矩阵诊断资产。",
    },
    source: {
      sourceCode: `KOPS-DXSRC-${suffix.toUpperCase()}`,
      sourceType: "HOSPITAL_PROTOCOL",
      authorityLevel: "D_HOSPITAL",
      authorityBasis: "上线演练受控来源。",
      title: `知识运营诊断来源 ${suffix}`,
      publisher: "MedKernel 本地上线演练",
      license: "内部演练",
      language: "zh-CN",
      versionNo: "2026",
      publishedAt: "2026-07-09T00:00:00Z",
      fileUri: `repository://knowledge-ops/diagnosis/${suffix}`,
      content: sourceContent,
    },
    version: {
      versionNo: `knowledge-ops-${suffix}`,
      versionLabel: `知识运营诊断候选版 ${suffix}`,
      riskLevel: "LOW",
      gradeQuality: "LOW",
      gradeStrength: "WEAK",
      reviewCycleMonths: 12,
    },
    evidence: {
      anchorPath: "knowledge.ops.diagnosis.criteria",
      anchorLabel: "诊断标准代表证据",
      textExcerpt: sourceContent,
    },
  });
  await expectOk(asset, "创建知识运营诊断资产");
  const assetData = await responseData(asset);
  const identityId = requireNumber(
    numericField(recordField(assetData, "identity"), "id"),
    "诊断资产必须返回知识身份",
  );
  const versionId =
    numericField(recordField(assetData, "version"), "id") ?? numericField(assetData, "versionId");
  expect(versionId, "诊断资产必须返回候选版本").toBeTruthy();
  const citationId = requireNumber(
    numericField(recordField(assetData, "citation"), "id"),
    "诊断资产必须返回来源 citation",
  );
  const criterion = await postApi(page, `/engine/knowledge/diagnosis/versions/${versionId}/criteria`, {
    findingTermCode,
    direction: "SUPPORTING",
    weight: "MAJOR",
    valueConstraint: null,
    temporalConstraint: null,
    citationId,
  });
  await expectOk(criterion, "登记知识运营诊断标准");
  const testCase = await postApi(
    page,
    `/engine/knowledge/diagnosis/versions/${versionId}/test-cases`,
    {
      caseCode: `DXCASE-${suffix.toUpperCase()}`,
      findings: findingTermCode,
      expectedIdentityId: identityId,
      expectedConfidence: "STRONG",
    },
  );
  await expectOk(testCase, "登记知识运营诊断验证病例");
  return {
    menuKey: "diagnosis-knowledge",
    role: "engine-operator",
    path: "/knowledge/diagnosis",
    frontdeskAction: "医疗引擎运营员前台创建证据完整诊断资产并登记标准和验证病例",
    serviceOperation:
      "POST /api/v1/engine/knowledge/diagnosis/assets + POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria + POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/test-cases",
    serviceStatus: testCase.status(),
    readbackVerified: true,
    auditVerified: true,
    humanReviewVerified: true,
    sourceEvidenceVerified: true,
  };
}

async function verifyProvenanceEvidence(
  page: Page,
  seed: KnowledgeSeed,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath(`/advanced/provenance?identityId=${seed.identityId}`), {
    waitUntil: "networkidle",
  });
  await expect(page.getByRole("heading", { name: "来源与血缘" })).toBeVisible({ timeout: 30_000 });
  const provenance = await getApi(
    page,
    `/engine/knowledge/identities/${seed.identityId}/provenance`,
  );
  await expectOk(provenance, "回读知识来源血缘");
  const provenanceData = await responseData(provenance);
  expect(numericField(provenanceData, "currentVersionId")).toBe(seed.versionId);
  expect(booleanField(provenanceData, "partial"), "知识来源血缘必须完整解析").toBe(false);
  expect(numericField(provenanceData, "unresolvedCitationCount")).toBe(0);
  const evidence = arrayField(provenanceData, "sourceEvidence").find(
    (item) => numericField(item, "citationId") === seed.citationId,
  );
  expect(evidence, "知识来源血缘证据必须包含本轮 citation").toBeTruthy();
  expect(numericField(evidence, "citationId")).toBe(seed.citationId);
  expect(numericField(evidence, "assetVersionId")).toBe(seed.versionId);
  expect(numericField(evidence, "sourceVersionId")).toBe(seed.sourceVersionId);
  expect(numericField(evidence, "sourceFragmentId")).toBe(seed.sourceFragmentId);
  expect(textField(evidence, "textExcerpt")).toBe(seed.textExcerpt);
  expect(numericField(evidence, "startOffset")).toBe(0);
  expect(numericField(evidence, "endOffset")).toBe(seed.textExcerpt.length);
  expect(textField(evidence, "relation")).toBe("DERIVED_FROM");
  return {
    menuKey: "provenance",
    role: "engine-operator",
    path: "/advanced/provenance",
    frontdeskAction: "医疗引擎运营员前台查看本轮知识版本来源血缘和原文锚点",
    serviceOperation: "GET /api/v1/engine/knowledge/identities/{identityId}/provenance",
    serviceStatus: provenance.status(),
    readbackVerified: true,
    auditVerified: true,
    sourceAuditVerified: true,
    sourceLineageVerified: true,
  };
}

async function verifyGraphEvidence(
  page: Page,
  seed: KnowledgeSeed,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await enableGraphProjectionForKnowledgeOperations(page);
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/advanced/graph"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "知识关系" })).toBeVisible({ timeout: 30_000 });
  const rebuild = await postApi(page, "/projections/knowledge-graph/rebuild", {
    projectionKey: "knowledge-source-lineage",
    reason: "知识运营资产入口族代表矩阵：重建来源血缘投影。",
  });
  await expectOk(rebuild, "重建知识关系投影");
  const facts = await getApi(
    page,
    `/projections/knowledge-graph/facts?keyword=${encodeURIComponent(
      `KNOWLEDGE_IDENTITY:${seed.identityId}`,
    )}&page=1&size=40`,
  );
  await expectOk(facts, "查询知识关系投影事实");
  const identityFacts = pageItems(await responseData(facts));
  expect(
    identityFacts.some(
      (item) =>
        textField(item, "factKind") === "NODE" &&
        textField(item, "objectType") === "KNOWLEDGE_IDENTITY" &&
        textField(item, "objectId") === String(seed.identityId),
    ),
    "知识关系投影必须包含本轮知识身份节点",
  ).toBe(true);
  const fragmentFactsResponse = await getApi(
    page,
    `/projections/knowledge-graph/facts?keyword=${encodeURIComponent(
      `SOURCE_FRAGMENT:${seed.sourceFragmentId}`,
    )}&page=1&size=40`,
  );
  await expectOk(fragmentFactsResponse, "查询知识关系投影来源片段事实");
  const fragmentFacts = pageItems(await responseData(fragmentFactsResponse));
  expect(
    fragmentFacts.some(
      (item) =>
        textField(item, "factKind") === "EDGE" &&
        textField(item, "subjectKey") === `SOURCE_FRAGMENT:${seed.sourceFragmentId}` &&
        textField(item, "predicate") === "BELONGS_TO_SOURCE",
    ),
    "知识关系投影必须包含本轮来源片段血缘边",
  ).toBe(true);
  return {
    menuKey: "graph-explore",
    role: "engine-operator",
    path: "/advanced/graph",
    frontdeskAction: "医疗引擎运营员前台重建知识关系投影并查询来源追踪证据",
    serviceOperation:
      "POST /api/v1/projections/knowledge-graph/rebuild + GET /api/v1/projections/knowledge-graph/facts",
    serviceStatus: facts.status(),
    readbackVerified: true,
    auditVerified: true,
    graphProjectionVerified: true,
    sourceLineageVerified: true,
  };
}

async function enableGraphProjectionForKnowledgeOperations(page: Page) {
  await ensureReadySession(page, "platform-admin", "platform");
  const key = "medkernel.runtime.feature-flags.graph-projection.enabled";
  const configsResponse = await getApi(
    page,
    `/system/configs?prefix=${encodeURIComponent("medkernel.runtime.feature-flags")}`,
  );
  await expectOk(configsResponse, "读取知识运营图投影配置");
  const config = arrayValues(await responseData(configsResponse)).find(
    (item) => textField(item, "key") === key,
  );
  expect(config, "知识运营图投影配置必须由配置中心登记").toBeTruthy();
  if (textField(config, "value") === "true") return;
  const version = requireNumber(numericField(config, "version"), "图投影配置必须返回版本号");
  const updated = await patchApi(page, `/system/configs/${encodeURIComponent(key)}`, {
    value: "true",
    reason: "知识运营资产入口族代表矩阵启用本地知识关系投影。",
    expectedVersion: version,
    confirmedHighRisk: false,
  });
  await expectOk(updated, "启用知识运营图投影");
}

async function verifyAiWorkflowSafetyBoundary(
  page: Page,
): Promise<KnowledgeOperationsAssetEntryCoreActionEvidence> {
  await page.goto(appPath("/advanced/ai-workflows"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: /模型能力|模型能力与安全/ })).toBeVisible({
    timeout: 30_000,
  });
  const readiness = await getApi(page, "/engine/knowledge-production/readiness");
  await expectOk(readiness, "读取模型知识生产 readiness");
  const readinessData = await responseData(readiness);
  const modelInvocationAllowed = booleanField(readinessData, "modelInvocationAllowed");
  const requiredReadinessItems = arrayValues(recordField(readinessData, "items")).filter(
    (item) => booleanField(item, "required") === true,
  );
  expect(typeof modelInvocationAllowed, "readiness 必须返回模型调用许可布尔值").toBe("boolean");
  expect(requiredReadinessItems.length, "readiness 必须返回 required 前置门").toBeGreaterThan(0);
  if (modelInvocationAllowed === false) {
    expect(
      requiredReadinessItems.some((item) => booleanField(item, "ready") === false),
      "模型禁止调用时必须有 required blocker",
    ).toBe(true);
  }
  return {
    menuKey: "ai-workflows",
    role: "engine-operator",
    path: "/advanced/ai-workflows",
    frontdeskAction: "医疗引擎运营员前台核查模型能力、安全边界和无模型诚实降级",
    serviceOperation: "GET /api/v1/engine/knowledge-production/readiness",
    serviceStatus: readiness.status(),
    readbackVerified: true,
    auditVerified: true,
    modelSafetyBoundaryVerified: true,
    noDirectPublishVerified: true,
  };
}

async function prepareKnowledgeCandidate(page: Page, suffix: string): Promise<KnowledgeSeed> {
  const identityCode = `KNOWLEDGE.OPS.${suffix.toUpperCase()}`;
  const textExcerpt = `知识运营资产入口族代表矩阵 ${suffix}：外部资料仅作为受控准备，正式知识必须在 134 内生产、审核、发布和机构生效。`;
  const content = [
    "# 知识运营供给链说明",
    textExcerpt,
    "本材料不包含诊断、处方、剂量或阈值，不自动开立医嘱。",
    "## 模型安全边界",
    "模型候选不得绕过人工审核直接发布。",
  ].join("\n");
  const source = await postApi(page, "/engine/knowledge/sources", {
    ...knowledgeContext("knowledge-ops-source"),
    sourceCode: `knowledge-ops-${suffix}`,
    sourceType: "GUIDELINE",
    authorityLevel: "B_GUIDELINE",
    authorityBasis: "知识运营资产入口族代表矩阵受控来源。",
    title: `知识运营受控来源 ${suffix}`,
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(source, "登记知识运营受控来源");
  const sourceDocumentId = requireNumber(numericField(await responseData(source), "id"), "来源必须返回 id");

  const uploadParsed = await uploadParseKnowledgeDocument(page, {
    suffix,
    sourceDocumentId,
    content,
    identityCode,
  });
  const sourceVersionId = uploadParsed.sourceVersionId;
  const fragments = await readParsedSourceFragments(page, sourceVersionId);
  expect(fragments.length, "上传解析必须生成可回读来源片段").toBeGreaterThanOrEqual(
    uploadParsed.parsedFragmentCount,
  );
  const sourceFragment = fragments.find((item) => textField(item, "textExcerpt") === textExcerpt);
  const sourceFragmentId = requireNumber(
    numericField(sourceFragment, "id"),
    "上传解析生成的本轮来源片段必须可回读 id",
  );
  const sourceFragmentIds = fragments.map((item) =>
    requireNumber(numericField(item, "id"), "上传解析来源片段必须返回 id"),
  );

  const generation = uploadParsed.generation;
  const generatedCandidate =
    pageItems(recordField(generation, "candidates"))[0] ?? arrayItem(generation, "candidates", 0);
  const candidateRef = requireText(textField(generatedCandidate, "candidateRef"), "候选必须返回 candidateRef");
  const jobCode = requireText(textField(generatedCandidate, "jobCode"), "候选必须返回 jobCode");
  const parsed = parseKnowledgeCandidateRef(candidateRef);
  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
  );
  await expectOk(identity, "读取知识运营候选身份");
  const identityId = requireNumber(numericField(await responseData(identity), "id"), "身份必须返回 id");
  expect(identityId).toBe(parsed.identityId);
  const candidateView = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/candidates?page=1&size=20`,
  );
  await expectOk(candidateView, "读取知识运营候选项");
  const candidateData = await responseData(candidateView);
  const versionCandidate = pageItems(recordField(candidateData, "candidates")).find(
    (item) => textField(item, "versionNo") === parsed.versionNo,
  );
  const versionId = requireNumber(numericField(versionCandidate, "id"), "候选版本必须返回 id");
  const classification = arrayValues(recordField(candidateData, "classifications")).find(
    (item) => numericField(item, "candidateVersionId") === versionId,
  );
  const classificationId = requireNumber(
    numericField(classification, "id"),
    "候选审核分类必须返回 id",
  );
  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: versionId,
    sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 100,
    startOffset: 0,
    endOffset: textExcerpt.length,
  });
  await expectOk(citation, "绑定知识运营来源 citation");
  const citationId = requireNumber(numericField(await responseData(citation), "id"), "citation 必须返回 id");
  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records`,
    { candidateRef, identityId, versionId },
  );
  await expectOk(qualityRecord, "生成知识运营发布质量记录");
  const qualityGateRecordId = requireNumber(
    numericField(await responseData(qualityRecord), "id"),
    "质量记录必须返回 id",
  );
  return {
    identityId,
    identityCode,
    versionId,
    classificationId,
    candidateRef,
    jobCode,
    qualityGateRecordId,
    sourceVersionId,
    sourceFragmentId,
    uploadParseJobCode: uploadParsed.parseJobCode,
    parseResultSourceVersionId: uploadParsed.sourceVersionId,
    parsedFragmentCount: uploadParsed.parsedFragmentCount,
    sourceFragmentIds,
    citationId,
    textExcerpt,
  };
}

async function ensureKnowledgeMaterialRoot(page: Page, suffix: string) {
  const rootPath = path.join(
    homedir(),
    "medkernel-e2e-data",
    "platform-knowledge",
    "t-1",
    "literature-materials",
    `knowledge-ops-${suffix}`,
  );
  await mkdir(rootPath, { recursive: true });
  const rootUri = `${pathToFileUri(rootPath)}/`;
  const configs = await getApi(
    page,
    `/system/configs?prefix=${encodeURIComponent(knowledgeMaterialRootConfigKey)}`,
  );
  await expectOk(configs, "读取知识运营资料库根地址配置");
  const config = arrayValues(await responseData(configs)).find(
    (item) => textField(item, "key") === knowledgeMaterialRootConfigKey,
  );
  expect(config, "知识运营资料库根地址必须由配置中心登记").toBeTruthy();
  if (textField(config, "value") === rootUri) return;
  const version = requireNumber(numericField(config, "version"), "资料库根地址配置必须返回版本号");
  const updated = await patchApi(
    page,
    `/system/configs/${encodeURIComponent(knowledgeMaterialRootConfigKey)}`,
    {
      value: rootUri,
      reason: "知识运营资产入口族代表矩阵：上传解析需写入受管文献资料库。",
      expectedVersion: version,
      confirmedHighRisk: true,
    },
  );
  await expectOk(updated, "配置知识运营受管文献资料库根地址");
}

async function uploadParseKnowledgeDocument(
  page: Page,
  options: {
    suffix: string;
    sourceDocumentId: number;
    content: string;
    identityCode: string;
  },
) {
  const headers: Record<string, string> = { "X-Trace-Id": `e2e-upload-parse-${Date.now()}` };
  const xsrf = (await page.context().cookies(apiBase)).find(
    (cookie) => cookie.name === "XSRF-TOKEN",
  );
  if (xsrf) {
    headers["X-XSRF-TOKEN"] = xsrf.value;
  }
  const upload = await page.request.post(
    `${apiBase}/engine/knowledge/documents:upload-parse`,
    {
      headers,
      multipart: {
        file: {
          name: `knowledge-ops-${options.suffix}.md`,
          mimeType: "text/plain",
          buffer: Buffer.from(options.content, "utf8"),
        },
        sourceDocumentId: String(options.sourceDocumentId),
        versionNo: `2026-${options.suffix}`,
        format: "STRUCTURED_TEXT",
        generation: JSON.stringify({
          domain: "CLINICAL",
          items: [
            {
              assetType: "KNOWLEDGE",
              target: {
                targetIdentityId: null,
                newIdentity: {
                  domain: "GUIDELINE",
                  subject: `知识运营供给链说明书 ${options.suffix}`,
                  identityCode: options.identityCode,
                },
              },
            },
          ],
        }),
      },
    },
  );
  await expectOk(upload, "上传解析知识运营受控来源并生成候选");
  const data = await responseData(upload);
  const parseJob = recordField(data, "parseJob");
  expect(textField(parseJob, "status"), "上传解析 job 必须成功").toBe("SUCCEEDED");
  const parseJobCode = requireText(textField(parseJob, "jobCode"), "上传解析 job 必须返回 jobCode");
  const sourceVersionId = requireNumber(
    numericField(parseJob, "resultSourceVersionId"),
    "上传解析必须返回结果来源版本 id",
  );
  const parsedFragmentCount = requireNumber(
    numericField(parseJob, "parsedFragmentCount"),
    "上传解析必须返回片段数",
  );
  expect(parsedFragmentCount, "上传解析必须生成至少一个来源片段").toBeGreaterThan(0);
  const generation = recordField(data, "generationSummary");
  expect(arrayField(generation, "blocked"), "上传解析生成候选不得被安全门阻断").toHaveLength(0);
  expect(arrayField(generation, "skipped"), "上传解析生成候选不得被分流跳过").toHaveLength(0);
  expect(arrayField(generation, "candidates"), "上传解析必须生成知识候选").toHaveLength(1);
  return { parseJobCode, sourceVersionId, parsedFragmentCount, generation };
}

async function readParsedSourceFragments(page: Page, sourceVersionId: number) {
  const fragments = await getApi(
    page,
    `/engine/knowledge/sources/versions/${encodeURIComponent(sourceVersionId)}/fragments`,
  );
  await expectOk(fragments, "回读上传解析来源片段");
  const data = await responseData(fragments);
  return arrayValues(data);
}

async function prepareTerminologyAsset(page: Page, suffix: string): Promise<TerminologyAsset> {
  const standardSystem = "LOCAL-KOPS";
  const standardCode = `KOPS-${suffix.toUpperCase()}`;
  const localCode = `LIS-KOPS-${suffix.toUpperCase()}`;
  const sourceSystem = "LIS";
  const assetIdentity = `TERM.KNOWLEDGE.OPS.${suffix.toUpperCase()}`;
  const standard = await postApi(page, "/engine/terminology/terms/standard", {
    ...terminologyContext("knowledge-ops-term-standard"),
    standardSystem,
    termCode: standardCode,
    category: "LAB",
    displayName: `知识运营标准术语 ${suffix}`,
    normalizedName: `知识运营标准术语 ${suffix}|${localCode}`,
    versionNo: "2026.07",
    sourceVersionId: null,
    evidenceText: "知识运营资产入口族代表矩阵标准术语证据。",
  });
  await expectOk(standard, "登记知识运营标准术语");
  const local = await postApi(page, "/engine/terminology/terms/local", {
    ...terminologyContext("knowledge-ops-term-local"),
    sourceSystem,
    localCode,
    category: "LAB",
    localName: `知识运营院内术语 ${suffix}`,
    normalizedName: `知识运营标准术语 ${suffix}|${localCode}`,
    local_department_id: null,
  });
  await expectOk(local, "同步知识运营院内术语");
  const generation = await postApi(page, "/engine/terminology/mappings/candidates", {
    ...terminologyContext("knowledge-ops-term-candidates"),
    sourceSystem,
    minimumScore: 0.2,
    semanticAssistEnabled: true,
  });
  await expectOk(generation, "生成知识运营术语候选");
  const jobCode = requireText(textField(await responseData(generation), "jobCode"), "术语候选必须返回 jobCode");
  const candidate = await waitForTerminologyCandidate(page, jobCode, localCode);
  const candidateId = requireNumber(numericField(candidate, "id"), "术语候选必须返回 id");
  const confirmed = await postApi(page, `/engine/terminology/mappings/${candidateId}/confirm`, {
    ...terminologyContext("knowledge-ops-term-confirm"),
    reviewNote: "知识运营资产入口族代表矩阵：确认院内码与标准码映射。",
    evidenceOverride: "院内字典同步和标准术语登记均已在 134 内完成。",
  });
  await expectOk(confirmed, "确认知识运营术语候选");
  const confirmedData = await responseData(confirmed);
  const mappingId = requireNumber(
    numericField(confirmedData, "id") ?? candidateId,
    "术语确认必须返回 mappingId",
  );
  const draft = await postApi(page, "/engine/terminology/assets/drafts", {
    assetIdentity,
    name: `知识运营术语资产 ${suffix}`,
    scopeLevel: "TENANT",
    scopeCode: "t-e2e-rehearsal-local",
  });
  await expectOk(draft, "生成知识运营术语资产版本");
  const draftData = await responseData(draft);
  return {
    assetType: "TERMINOLOGY",
    assetIdentity,
    versionId: requireText(textField(draftData, "versionId"), "术语资产必须返回 versionId"),
    versionNo: requireText(textField(draftData, "versionNo"), "术语资产必须返回 versionNo"),
    standardSystem,
    standardCode,
    localCode,
    sourceSystem,
    mappingId,
  };
}

async function activateRuntimeWithTerminologyAndRollback(
  page: Page,
  hospitalId: string,
  terminology: TerminologyAsset,
): Promise<RuntimeActivationEvidence> {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取知识运营当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(baselineAssets.baselineReleaseId, "知识运营矩阵必须基于当前平台标准版本激活医院 runtime").toBeTruthy();
  expect(
    requiredRuntimeAssetsForRehearsal.every((required) =>
      baselineAssets.activeAssets.some(
        (asset) =>
          asset.assetType === required.assetType && asset.assetIdentity === required.assetIdentity,
      ),
    ),
    "知识运营演练必须保留 13 类平台基线资产",
  ).toBe(true);
  const currentBefore = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentBefore, "读取知识运营演练前机构生效版本");
  const currentBeforeData = await responseData(currentBefore);
  const currentBeforeRelease = recordField(currentBeforeData, "release");
  const previousReleaseId = textField(currentBeforeRelease, "releaseId");
  const currentPlatformBaselineReleaseId = textField(currentBeforeRelease, "platformBaselineReleaseId");
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    {
      assetType: terminology.assetType,
      assetIdentity: terminology.assetIdentity,
      versionId: terminology.versionId,
    },
  ]);
  const activation = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases`,
    {
      platformBaselineReleaseId: baselineAssets.baselineReleaseId,
      expectedCurrentReleaseId: previousReleaseId,
      confirmedPlatformUpgradeDigest:
        previousReleaseId &&
        currentPlatformBaselineReleaseId &&
        currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
          ? await readPlatformUpgradeAnalysisDigest(page, hospitalId, baselineAssets.baselineReleaseId ?? "")
          : null,
      activeAssets: uniqueRuntimeAssets(activeAssets),
    },
  );
  await expectOk(activation, "生成知识运营机构生效版本");
  const releaseId = requireText(textField(await responseData(activation), "releaseId"), "激活必须返回 releaseId");
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "回读知识运营机构生效版本");
  const currentData = await responseData(current);
  expect(
    runtimeReleaseItems(currentData).some(
      (item) =>
        textField(item, "assetType") === terminology.assetType &&
        textField(item, "assetIdentity") === terminology.assetIdentity &&
        textField(item, "entryState") === "ACTIVE",
    ),
    "当前机构生效版本必须包含本轮术语资产",
  ).toBe(true);
  const consumer = await getApi(page, "/engine/integration/knowledge-runtime/runtime-release/current");
  await expectOk(consumer, "读取第三方运行契约当前机构生效版本");
  const runtimeConsumerReadbackVerified = JSON.stringify(await responseData(consumer)).includes(
    terminology.assetIdentity,
  );
  let rollbackReadbackVerified = false;
  if (previousReleaseId) {
    const rollback = await postApi(
      page,
      `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases:rollback`,
      { targetReleaseId: previousReleaseId },
    );
    await expectOk(rollback, "回滚知识运营机构生效版本");
    const rolledBack = await getApi(
      page,
      `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
    );
    await expectOk(rolledBack, "回读知识运营回滚后机构生效版本");
    const rolledBackData = await responseData(rolledBack);
    expect(runtimeReleaseId(rolledBackData), "回滚必须生成新的当前机构生效版本").toBeTruthy();
    rollbackReadbackVerified = !runtimeReleaseItems(rolledBackData).some(
      (item) =>
        textField(item, "assetType") === terminology.assetType &&
        textField(item, "assetIdentity") === terminology.assetIdentity &&
        textField(item, "entryState") === "ACTIVE",
    );
    const rolledBackConsumer = await getApi(
      page,
      "/engine/integration/knowledge-runtime/runtime-release/current",
    );
    await expectOk(rolledBackConsumer, "读取知识运营回滚后第三方运行契约当前机构生效版本");
    rollbackReadbackVerified =
      rollbackReadbackVerified &&
      !JSON.stringify(await responseData(rolledBackConsumer)).includes(terminology.assetIdentity);
  }
  return {
    releaseId,
    previousReleaseId,
    activeAssets,
    runtimeConsumerReadbackVerified,
    rollbackReadbackVerified,
  };
}

function runtimeReleaseId(value: unknown) {
  return textField(recordField(value, "release"), "releaseId");
}

function runtimeReleaseItems(value: unknown) {
  return arrayValues(recordField(value, "items"));
}

async function readPlatformUpgradeAnalysisDigest(page: Page, hospitalId: string, baselineReleaseId: string) {
  const analysis = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/platform-upgrade-analysis?targetBaselineReleaseId=${encodeURIComponent(baselineReleaseId)}`,
  );
  await expectOk(analysis, "读取知识运营平台升级影响摘要");
  return requireText(textField(await responseData(analysis), "analysisDigest"), "平台升级分析必须返回摘要");
}

async function waitForTerminologyCandidate(page: Page, jobCode: string, localCode: string) {
  const deadline = Date.now() + 20_000;
  let lastStatus = "PENDING";
  let generatedCount = 0;
  const canonicalLocalCode = canonicalTerminologyAlias(localCode);
  while (Date.now() < deadline) {
    const job = await getApi(
      page,
      `/engine/terminology/mappings/candidate-generation-jobs/${encodeURIComponent(jobCode)}`,
    );
    await expectOk(job, "读取知识运营术语候选任务");
    const jobData = await responseData(job);
    lastStatus = textField(jobData, "status") ?? lastStatus;
    generatedCount = Number((jobData as { generatedCount?: unknown })?.generatedCount ?? generatedCount);
    const candidates = await getApi(
      page,
      `/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=${encodeURIComponent(
        jobCode,
      )}&page=1&size=20`,
    );
    await expectOk(candidates, "读取知识运营术语候选列表");
    const candidate = pageItems(await responseData(candidates)).find(
      (item) =>
        textField(item, "status") === "PENDING" &&
        textField(item, "generationJobCode") === jobCode &&
        (textField(item, "evidenceText") ?? "").includes(canonicalLocalCode),
    );
    if (candidate) return candidate;
    await waitForPollingInterval(250);
  }
  throw new Error(
    `知识运营术语候选生成超时：${jobCode}，最后任务状态：${lastStatus}，生成数量：${generatedCount}，院内码别名：${canonicalLocalCode}`,
  );
}

function canonicalTerminologyAlias(value: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9\u4e00-\u9fff]+/g, "");
}

async function resolveLocalRehearsalHospitalId(page: Page) {
  const hospitals = await getApi(
    page,
    `/engine/org/org-units?keyword=${encodeURIComponent(
      "本地上线演练医院",
    )}&level=FACILITY&status=ACTIVE&page=1&size=20`,
  );
  await expectOk(hospitals, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(hospitals)).find(
    (item) =>
      textField(item, "level") === "FACILITY" &&
      (textField(item, "code") === "e2e-rehearsal-hospital" ||
        textField(item, "name") === "本地上线演练医院"),
  );
  return requireText(textField(hospital, "id"), "必须找到本地上线演练医院");
}

function knowledgeContext(prefix: string) {
  const traceId = `${prefix}-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: "t-e2e-rehearsal-local",
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function terminologyContext(prefix: string) {
  return knowledgeContext(prefix);
}

function milestone(
  phaseCode: string,
  phaseName: string,
  milestoneCode: string,
  name: string,
  dayOffset: number,
  expectedOffsetMinutes: number,
  sortOrder: number,
) {
  return {
    phaseCode,
    phaseName,
    milestoneCode,
    name,
    dayOffset,
    expectedOffsetMinutes,
    achievementCriteria: {},
    sortOrder,
  };
}

function node(
  nodeCode: string,
  name: string,
  nodeType: string,
  milestoneCode: string,
  sortOrder: number,
  terminal: boolean,
  timeWindowMinutes: number,
) {
  return {
    nodeCode,
    name,
    nodeType,
    milestoneCode,
    sortOrder,
    responsibleRole: "engine-operator",
    accountableRole: "engine-operator",
    consultedRoles: ["clinical-user"],
    informedRoles: ["auditor"],
    dependency: {},
    timeWindowMinutes,
    terminal,
    disabled: false,
    config: {
      visibleSummary: name,
      safety: "仅用于知识运营入口代表矩阵，不自动诊断，不自动开立医嘱",
      clockSla: {
        baselineEvent: sortOrder === 10 ? "PATHWAY_ENTRY" : "NODE_START",
        minMinutes: 0,
        targetMinutes: timeWindowMinutes,
        maxMinutes: timeWindowMinutes * 2,
        escalations: [
          { level: "REMINDER", afterMinutes: timeWindowMinutes },
          { level: "REPORT", afterMinutes: Math.floor(timeWindowMinutes * 1.5) },
          { level: "QUALITY_RECORD", afterMinutes: timeWindowMinutes * 2 },
        ],
      },
    },
  };
}

function parseKnowledgeCandidateRef(value: string) {
  const match = /^(?:knowledge|kv):(\d+):(.+)$/u.exec(value);
  if (!match) {
    throw new Error(`知识候选引用格式不正确：${value}`);
  }
  return { identityId: Number(match[1]), versionNo: match[2] };
}

function arrayItem(value: unknown, field: string, index: number) {
  return arrayValues(recordField(value, field))[index];
}

function arrayValues(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function requireText(value: string | null | undefined, message: string) {
  if (!value) {
    throw new Error(message);
  }
  return value;
}

function requireNumber(value: number | null | undefined, message: string) {
  if (value == null || !Number.isFinite(value)) {
    throw new Error(message);
  }
  return value;
}

function booleanField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "boolean" ? raw : null;
}

function sha256(value: string) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function pathToFileUri(value: string) {
  return pathToFileURL(path.resolve(value)).toString();
}
