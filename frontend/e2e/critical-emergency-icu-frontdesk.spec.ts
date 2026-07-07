import { createHmac } from "node:crypto";
import { expect, test, type APIResponse, type Locator, type Page, type TestInfo } from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  postApi,
  putApi,
  requiredRuntimeAssetsForRehearsal,
  resolveBaselineRuntimeAssets,
  resolvedTenantIdFor,
  responseData,
  waitForPollingInterval,
} from "./support/auth";

type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type RuntimeReleaseItem = RuntimeAssetSelection & {
  versionNo?: string;
  contentHash?: string;
  entryState?: string;
};

type RuntimeReleaseDetail = {
  release?: {
    releaseId?: string;
    revisionNo?: number;
    manifestSha256?: string;
  };
  items?: RuntimeReleaseItem[];
};

type CriticalAssetCandidate = {
  assetType: "TERMINOLOGY" | "CDSS_RISK" | "RULE" | "PATHWAY" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type CriticalActionCardCandidate = CriticalAssetCandidate & {
  assetType: "ACTION_CARD";
  requiresPhysicianConfirmation: boolean;
  noAutoOrder: boolean;
  noAutoTransfer: boolean;
  noDeviceControl: boolean;
  noAutoVentilatorChange: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  clinicalSetting: string;
  resources: Record<string, unknown>;
};

type InboundWebhookRequest = {
  messageId: string;
  traceId: string;
  adapterId: string;
  sourceSystem: string;
  eventType: string;
  patientId: string;
  encounterId: string;
  clinicalSetting: string;
  triggerPoint: string;
  occurredAt: string;
  payload: Record<string, unknown>;
};

type ClinicalEventDetailEvidence = {
  eventId: string;
  status: string;
  errorCode: string | null;
  errorClass: string | null;
  retryCount: number | null;
  runtimeReleaseId: string | null;
};

type CriticalEmergencyIcuApiEvidence = {
  monitoringAdapterCreatedThroughRealService: boolean;
  monitoringWebhookCreatedThroughRealService: boolean;
  emergencyOnboardingCreatedThroughRealService: boolean;
  webhookSignaturePreviewGenerated: boolean;
  terminologyActivated: boolean;
  riskMatrixCreated: boolean;
  ruleCreated: boolean;
  pathwayCreated: boolean;
  actionCardPublished: boolean;
  runtimeActivatedWithCriticalAssets: boolean;
  triageContextCreatedFromFrontdesk: boolean;
  inboundMonitoringEventAccepted: boolean;
  clinicalEvaluationTriggeredFromFrontdesk: boolean;
  humanEscalationConfirmationRecorded: boolean;
  workflowEscalationTodoCompleted: boolean;
};

const criticalSourceSystem = "LIS_MONITORING_CRITICAL";
const lactateLocalCode = "ICU-LAC";
const lactateStandardCode = "2524-7";
const shockDiagnosisCode = "R57.900";
const ventilationProcedureCode = "5A1955Z";
const terminologyIdentityPrefix = "TERM.CRITICAL.EMERGENCY.ICU.LACTATE";
const actionCardIdentityPrefix = "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION";
const ruleIdentityPrefix = "RULE.CRITICAL.EMERGENCY.ICU.ESCALATION";
const pathwayIdentityPrefix = "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION";

const requiredStages = {
  S19: [
    "平台管理员登记 LIS_MONITORING_CRITICAL 监护入站适配器、回调通道和签名预览",
    "运营员发布乳酸术语、急危重症风险矩阵、预警规则、升级路径和动作卡资产",
    "当前机构生效版本包含急危重症五类运行资产",
    "签名入站监护事件生成生命体征和检验 Observation 并处理到 PROCESSED",
    "临床用户从真实前台触发 patient-view 急危重症预警评估",
    "推荐卡证明风险规则和动作卡按当前机构生效版本消费",
  ],
  S24: [
    "临床用户从患者 360 建立急诊分诊上下文和去向候选",
    "推荐卡证明分诊等级和留观或入 ICU 候选仅为人工确认建议",
    "医生人工确认升级候选，系统不自动转科、不自动开嘱",
  ],
  S27: [
    "入站上下文保留生命支持模式、升压药运行和不控制设备证据",
    "推荐卡证明 ICU 生命支持风险与升级路径按当前机构生效版本消费",
    "临床用户从真实待办完成升级协同，系统不控制呼吸机或生命支持设备",
  ],
} as const;

test.describe("急诊分诊与 ICU 生命支持风险代表切片真实前台闭环", () => {
  test(
    "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
    async ({ page }, testInfo) => {
      test.setTimeout(420_000);
      const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;
      const observedStages = new Set<string>();
      const apiEvidence = createApiEvidence();

      await ensureReadySession(page, "platform-admin");
      const adapter = await createCriticalMonitoringAdapter(page, suffix);
      apiEvidence.monitoringAdapterCreatedThroughRealService = true;
      const webhook = await createCriticalMonitoringWebhook(page, suffix);
      apiEvidence.monitoringWebhookCreatedThroughRealService = true;
      await generateCriticalSignaturePreview(page, webhook.webhookId);
      apiEvidence.webhookSignaturePreviewGenerated = true;
      const onboarding = await createCriticalMonitoringOnboarding(page, suffix, adapter.adapterId);
      apiEvidence.emergencyOnboardingCreatedThroughRealService = true;
      recordStage(observedStages, "平台管理员登记 LIS_MONITORING_CRITICAL 监护入站适配器、回调通道和签名预览");

      await ensureReadySession(page, "engine-operator");
      const hospitalId = await localRehearsalHospitalId(page);
      const riskMatrix = await createCriticalRiskMatrix(page, suffix);
      apiEvidence.riskMatrixCreated = true;
      const terminologyGate = await createCriticalTerminologyGate(page, suffix);
      apiEvidence.terminologyActivated = true;
      const actionCard = await createCriticalActionCard(page, suffix);
      apiEvidence.actionCardPublished = true;
      const pathwayAsset = await createCriticalIcuPathwayAsset(page, suffix);
      apiEvidence.pathwayCreated = true;
      const preRuleRuntime = await activateRuntimeWithCriticalAssets(page, {
        hospitalId,
        terminology: terminologyGate,
        cdssRisk: await readHospitalRuntimeCandidate(page, hospitalId, "CDSS_RISK", "CDSS.RISK.MATRIX"),
        pathway: await readHospitalRuntimeCandidate(page, hospitalId, "PATHWAY", pathwayAsset.assetIdentity),
        actionCard,
      });

      const positivePatient = await createCriticalPatientFromFrontdesk(page, `${suffix}-POS`);
      await postSignedCriticalInbound(page, {
        suffix: `${suffix}-POS`,
        adapterId: adapter.adapterId,
        webhookId: webhook.webhookId,
        webhookSecret: webhook.sharedSecret,
        patient: positivePatient,
        runtimeReleaseId: preRuleRuntime.releaseId,
      });
      const positiveSnapshot = await readLatestContextForPatient(page, positivePatient.patientId);
      const negativePatient = await createCriticalPatientFromFrontdesk(page, `${suffix}-NEG`);
      await postSignedCriticalInbound(page, {
        suffix: `${suffix}-NEG`,
        adapterId: adapter.adapterId,
        webhookId: webhook.webhookId,
        webhookSecret: webhook.sharedSecret,
        patient: negativePatient,
        runtimeReleaseId: preRuleRuntime.releaseId,
        positive: false,
      });
      const negativeSnapshot = await readLatestContextForPatient(page, negativePatient.patientId);

      const rule = await createAndPublishCriticalIcuRule(page, suffix, {
        positiveContextSnapshotId: positiveSnapshot.snapshotId,
        negativeContextSnapshotId: negativeSnapshot.snapshotId,
        actionCard,
        pathway: pathwayAsset,
      });
      apiEvidence.ruleCreated = true;
      recordStage(observedStages, "运营员发布乳酸术语、急危重症风险矩阵、预警规则、升级路径和动作卡资产");

      const candidates = await readCriticalRuntimeCandidates(page, hospitalId, {
        ruleIdentity: rule.assetIdentity,
        pathwayIdentity: pathwayAsset.assetIdentity,
      });
      const runtime = await activateRuntimeWithCriticalAssets(page, {
        hospitalId,
        terminology: terminologyGate,
        cdssRisk: candidates.cdssRisk,
        rule: candidates.rule,
        pathway: candidates.pathway,
        actionCard,
      });
      apiEvidence.runtimeActivatedWithCriticalAssets = true;
      recordStage(observedStages, "当前机构生效版本包含急危重症五类运行资产");

      const patient = await createCriticalPatientFromFrontdesk(page, suffix);
      apiEvidence.triageContextCreatedFromFrontdesk = true;
      const inbound = await postSignedCriticalInbound(page, {
        suffix,
        adapterId: adapter.adapterId,
        webhookId: webhook.webhookId,
        webhookSecret: webhook.sharedSecret,
        patient,
        runtimeReleaseId: runtime.releaseId,
      });
      apiEvidence.inboundMonitoringEventAccepted = true;
      const snapshot = await readLatestContextForPatient(page, patient.patientId);
      expect(snapshot.runtimeReleaseId, "S19/S24/S27 正式入站上下文必须绑定本轮 runtime").toBe(runtime.releaseId);
      assertSnapshotContainsCriticalFacts(snapshot);
      inbound.contextSnapshotId = snapshot.snapshotId;
      recordStage(observedStages, "临床用户从患者 360 建立急诊分诊上下文和去向候选");
      recordStage(observedStages, "签名入站监护事件生成生命体征和检验 Observation 并处理到 PROCESSED");
      recordStage(observedStages, "入站上下文保留生命支持模式、升压药运行和不控制设备证据");

      const recommendation = await triggerCriticalRecommendationFromFrontdesk(page, {
        snapshot,
        runtime,
        rule: {
          ...rule,
          versionId: runtime.ruleAsset.versionId ?? candidates.rule.versionId,
          versionNo: runtime.ruleAsset.versionNo ?? candidates.rule.versionNo,
          contentHash: runtime.ruleAsset.contentHash ?? candidates.rule.contentHash,
        },
      });
      apiEvidence.clinicalEvaluationTriggeredFromFrontdesk = true;
      recordStage(observedStages, "临床用户从真实前台触发 patient-view 急危重症预警评估");
      recordStage(observedStages, "推荐卡证明风险规则和动作卡按当前机构生效版本消费");
      recordStage(observedStages, "推荐卡证明分诊等级和留观或入 ICU 候选仅为人工确认建议");
      recordStage(observedStages, "推荐卡证明 ICU 生命支持风险与升级路径按当前机构生效版本消费");

      const escalationTodo = await completeCriticalEscalationTodo(page, {
        suffix,
        recommendation,
        snapshot,
      });
      apiEvidence.workflowEscalationTodoCompleted = true;
      recordStage(observedStages, "临床用户从真实待办完成升级协同，系统不控制呼吸机或生命支持设备");

      const manualEscalation = await completeCriticalManualEscalation(page, {
        cardId: recommendation.cardId,
        actionCard,
        actionCardAsset: runtime.actionCardAsset,
      });
      apiEvidence.humanEscalationConfirmationRecorded = true;
      recordStage(observedStages, "医生人工确认升级候选，系统不自动转科、不自动开嘱");

      await attachCriticalEmergencyIcuEvidence(testInfo, {
        apiEvidence,
        monitoringAdapter: adapter,
        emergencyOnboarding: onboarding,
        webhookSignature: {
          webhookId: webhook.webhookId,
          adapterId: adapter.adapterId,
          signatureAlgorithm: "HMAC-SHA256",
          canonicalPayloadIncludesTraceId: true,
          previewGenerated: true,
        },
        terminologyGate,
        riskMatrix: {
          ...riskMatrix,
          versionId: runtime.cdssRiskAsset.versionId ?? candidates.cdssRisk.versionId,
          versionNo: runtime.cdssRiskAsset.versionNo ?? candidates.cdssRisk.versionNo,
          contentHash: runtime.cdssRiskAsset.contentHash ?? candidates.cdssRisk.contentHash,
          entryState: runtime.cdssRiskAsset.entryState,
        },
        actionCard: {
          ...actionCard,
          versionId: runtime.actionCardAsset.versionId ?? actionCard.versionId,
          versionNo: runtime.actionCardAsset.versionNo ?? actionCard.versionNo,
          contentHash: runtime.actionCardAsset.contentHash ?? actionCard.contentHash,
          entryState: runtime.actionCardAsset.entryState,
        },
        ruleAsset: {
          ...rule,
          versionId: runtime.ruleAsset.versionId ?? candidates.rule.versionId,
          versionNo: runtime.ruleAsset.versionNo ?? candidates.rule.versionNo,
          contentHash: runtime.ruleAsset.contentHash ?? candidates.rule.contentHash,
          entryState: runtime.ruleAsset.entryState,
        },
        pathwayAsset: {
          ...pathwayAsset,
          versionId: runtime.pathwayAsset.versionId ?? candidates.pathway.versionId,
          versionNo: runtime.pathwayAsset.versionNo ?? candidates.pathway.versionNo,
          contentHash: runtime.pathwayAsset.contentHash ?? candidates.pathway.contentHash,
          entryState: runtime.pathwayAsset.entryState,
        },
        runtime,
        activationRequest: runtime.activationRequest,
        clinicalContext: {
          patientId: snapshot.patientId,
          encounterId: snapshot.encounterId,
          contextSnapshotId: snapshot.snapshotId,
          runtimeReleaseId: snapshot.runtimeReleaseId,
          clinicalSetting: snapshot.clinicalSetting,
          resources: snapshot.resources,
        },
        inboundMonitoringEvent: inbound,
        clinicalTrigger: {
          triggerId: recommendation.triggerId,
          contextSnapshotId: snapshot.snapshotId,
          runtimeReleaseId: runtime.releaseId,
          triggerType: "patient-view",
          cardId: recommendation.cardId,
          relatedCardIds: recommendation.relatedCardIds,
        },
        recommendation,
        manualEscalation,
        escalationTodo,
        observedStages,
      });
    },
  );
});

function createApiEvidence(): CriticalEmergencyIcuApiEvidence {
  return {
    monitoringAdapterCreatedThroughRealService: false,
    monitoringWebhookCreatedThroughRealService: false,
    emergencyOnboardingCreatedThroughRealService: false,
    webhookSignaturePreviewGenerated: false,
    terminologyActivated: false,
    riskMatrixCreated: false,
    ruleCreated: false,
    pathwayCreated: false,
    actionCardPublished: false,
    runtimeActivatedWithCriticalAssets: false,
    triageContextCreatedFromFrontdesk: false,
    inboundMonitoringEventAccepted: false,
    clinicalEvaluationTriggeredFromFrontdesk: false,
    humanEscalationConfirmationRecorded: false,
    workflowEscalationTodoCompleted: false,
  };
}

async function createCriticalRiskMatrix(page: Page, suffix: string) {
  const response = await putApi(page, "/engine/cdss/risk-matrix", {
    matrixVersion: `critical-emergency-icu-${suffix}`,
    changeReason: "S19/S24/S27 急诊分诊与 ICU 生命支持风险代表切片：只提示并要求医师确认。",
    status: "ACTIVE",
    entries: [
      {
        triggerPoint: "patient-view",
        severityLevel: "CRITICAL",
        automationLevel: "INFORM_ONLY",
        riskLevel: "CRITICAL",
        reviewRequirement: "PHYSICIAN_CONFIRMATION",
        silentRunHours: 168,
        releaseGate: "S19_S24_S27_CRITICAL_EMERGENCY_ICU",
        autoExecutionAllowed: false,
        samdClassification: "NMPA_RESERVED",
        regulatoryEvidence: "NOT_ASSESSED",
        explanation: "急危重症升级仅进入人工确认，不自动转 ICU、不自动开嘱、不控制设备。",
      },
    ],
  });
  await expectOk(response, "创建急危重症 CDSS_RISK 风险矩阵");
  const rule = arrayField(await responseData(response), "rules").find(
    (item) =>
      textField(item, "triggerPoint") === "patient-view" &&
      textField(item, "reviewRequirement") === "PHYSICIAN_CONFIRMATION" &&
      booleanField(item, "autoExecutionAllowed") === false,
  );
  return {
    assetType: "CDSS_RISK" as const,
    assetIdentity: "CDSS.RISK.MATRIX",
    matrixId: requireText(textField(rule, "matrixId"), "风险矩阵响应必须返回 matrixId"),
    matrixVersion: requireText(textField(rule, "matrixVersion"), "风险矩阵响应必须返回 matrixVersion"),
    triggerPoint: requireText(textField(rule, "triggerPoint"), "风险矩阵必须返回触发点"),
    riskLevel: requireText(textField(rule, "riskLevel"), "风险矩阵必须返回风险等级"),
    reviewRequirement: requireText(textField(rule, "reviewRequirement"), "风险矩阵必须返回复核要求"),
    automationLevel: requireText(textField(rule, "automationLevel"), "风险矩阵必须返回自动化等级"),
    autoExecutionAllowed: booleanField(rule, "autoExecutionAllowed"),
  };
}

async function createCriticalTerminologyGate(page: Page, suffix: string): Promise<CriticalAssetCandidate & {
  standardSystem: string;
  standardCode: string;
  localCode: string;
  localTermId: number;
  standardTermId: number;
  sourceSystem: string;
  category: string;
  mappingId: number;
  confirmedMapping: {
    mappingId: number;
    localTermId: number;
    standardTermId: number;
    sourceSystem: string;
    category: string;
  };
}> {
  const standard = await postApi(page, "/engine/terminology/terms/standard", {
    ...apiContext(suffix, "term-standard"),
    standardSystem: "LOINC",
    termCode: lactateStandardCode,
    category: "LAB",
    displayName: "Lactate [Moles/volume] in Blood",
    normalizedName: "乳酸|LOINC|2524-7",
    versionNo: "2026",
    sourceVersionId: null,
    evidenceText: "S19/S24/S27 代表切片：监护入站乳酸编码标准术语。",
  });
  await expectOk(standard, "登记乳酸 LOINC 标准术语");
  const standardTermId = numberField(await responseData(standard), "id");
  expect(standardTermId, "乳酸标准术语必须返回 id").toBeTruthy();
  const local = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-local"),
    sourceSystem: criticalSourceSystem,
    localCode: lactateLocalCode,
    category: "LAB",
    localName: "ICU 血乳酸",
    normalizedName: "ICU 血乳酸|ICU-LAC|2524-7|LOINC",
    local_department_id: null,
  });
  await expectOk(local, "登记 LIS_MONITORING_CRITICAL 乳酸院内术语");
  const localTermId = numberField(await responseData(local), "id");
  expect(localTermId, "乳酸院内术语必须返回 id").toBeTruthy();
  const mapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem: criticalSourceSystem,
    localCode: lactateLocalCode,
    localTermId,
    standardTermId,
    category: "LAB",
  });
  const assetIdentity = `${terminologyIdentityPrefix}.${suffix}`;
  const draft = await postApi(page, "/engine/terminology/assets/drafts", {
    assetIdentity,
    name: `急危重症乳酸术语映射 ${suffix}`,
    scopeLevel: "TENANT",
    scopeCode: resolvedTenantIdFor("engine-operator"),
  });
  await expectOk(draft, "生成急危重症术语资产草稿");
  const data = await responseData(draft);
  return {
    assetType: "TERMINOLOGY" as const,
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "术语资产必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "术语资产必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "术语资产必须返回 contentHash"),
    standardSystem: "LOINC",
    standardCode: lactateStandardCode,
    localCode: lactateLocalCode,
    localTermId: Number(localTermId),
    standardTermId: Number(standardTermId),
    sourceSystem: criticalSourceSystem,
    category: "LAB",
    mappingId: mapping.mappingId,
    confirmedMapping: mapping,
  };
}

async function readOrConfirmTerminologyMapping(
  page: Page,
  options: {
    suffix: string;
    sourceSystem: string;
    localCode: string;
    localTermId?: number | null;
    standardTermId?: number | null;
    category: "LAB";
  },
) {
  const existing = await getApi(
    page,
    `/engine/terminology/mappings?category=${encodeURIComponent(options.category)}&status=CONFIRMED&page=1&size=100`,
  );
  await expectOk(existing, "读取已确认乳酸术语映射");
  const found = pageItems(await responseData(existing)).find(
    (item) =>
      numberField(item, "localTermId") === options.localTermId &&
      numberField(item, "standardTermId") === options.standardTermId &&
      textField(item, "sourceSystem") === options.sourceSystem,
  );
  const foundId = numberField(found, "id");
  if (foundId) return confirmedMappingEvidence(found);
  const generation = await postApi(page, "/engine/terminology/mappings/candidates", {
    ...apiContext(options.suffix, "term-candidates"),
    sourceSystem: options.sourceSystem,
    minimumScore: 0.2,
    semanticAssistEnabled: true,
  });
  await expectOk(generation, "生成乳酸术语映射候选");
  const jobCode = requireText(textField(await responseData(generation), "jobCode"), "术语候选任务必须返回 jobCode");
  const candidate = await waitForTerminologyCandidate(page, jobCode, {
    localCode: options.localCode,
    localTermId: options.localTermId,
    standardTermId: options.standardTermId,
    sourceSystem: options.sourceSystem,
  });
  const candidateId = numberField(candidate, "id");
  expect(candidateId, "术语候选必须返回 id").toBeTruthy();
  const confirmed = await postApi(
    page,
    `/engine/terminology/mappings/${encodeURIComponent(String(candidateId))}/confirm`,
    {
      ...apiContext(options.suffix, "term-confirm"),
      reviewNote: "S19/S24/S27 代表切片：确认 ICU-LAC 到 LOINC:2524-7。",
      evidenceOverride: "急危重症监护入站规则发布前置术语覆盖门禁。",
    },
  );
  await expectOk(confirmed, "确认乳酸术语映射");
  const mapping = confirmedMappingEvidence(await responseData(confirmed));
  expect(mapping.localTermId, "确认映射必须绑定本轮 localTermId").toBe(options.localTermId);
  expect(mapping.standardTermId, "确认映射必须绑定本轮 standardTermId").toBe(options.standardTermId);
  expect(mapping.sourceSystem, "确认映射必须绑定本轮 sourceSystem").toBe(options.sourceSystem);
  return mapping;
}

async function waitForTerminologyCandidate(
  page: Page,
  jobCode: string,
  expected: {
    localCode: string;
    localTermId?: number | null;
    standardTermId?: number | null;
    sourceSystem: string;
  },
) {
  const deadline = Date.now() + 20_000;
  let lastStatus = "PENDING";
  let generatedCount: number | null = null;
  while (Date.now() < deadline) {
    const job = await getApi(
      page,
      `/engine/terminology/mappings/candidate-generation-jobs/${encodeURIComponent(jobCode)}`,
    );
    await expectOk(job, "读取乳酸术语候选生成任务");
    const jobData = await responseData(job);
    lastStatus = textField(jobData, "status") ?? lastStatus;
    generatedCount = numberField(jobData, "generatedCount") ?? generatedCount;
    expect(textField(jobData, "sourceSystem"), "术语候选任务必须绑定本轮来源系统").toBe(
      expected.sourceSystem,
    );
    const response = await getApi(
      page,
      `/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=${encodeURIComponent(jobCode)}&page=1&size=20`,
    );
    await expectOk(response, "读取乳酸术语映射候选");
    const candidate = pageItems(await responseData(response)).find(
      (item) =>
        textField(item, "generationJobCode") === jobCode &&
        numberField(item, "localTermId") === expected.localTermId &&
        numberField(item, "standardTermId") === expected.standardTermId,
    );
    if (candidate) return candidate;
    if (lastStatus === "FAILED") {
      throw new Error(`乳酸术语候选生成失败 ${jobCode}`);
    }
    if (lastStatus === "SUCCEEDED" && generatedCount === 0) {
      throw new Error(`乳酸术语候选生成成功但没有候选 ${jobCode}`);
    }
    await waitForPollingInterval(250);
  }
  throw new Error(`乳酸术语候选生成超时 ${jobCode}，最后状态 ${lastStatus}，候选数 ${generatedCount ?? "UNKNOWN"}`);
}

function confirmedMappingEvidence(value: unknown) {
  const mappingId = numberField(value, "id");
  const localTermId = numberField(value, "localTermId");
  const standardTermId = numberField(value, "standardTermId");
  const sourceSystem = textField(value, "sourceSystem");
  const category = textField(value, "category");
  expect(mappingId, "确认映射必须返回 id").toBeTruthy();
  expect(localTermId, "确认映射必须返回 localTermId").toBeTruthy();
  expect(standardTermId, "确认映射必须返回 standardTermId").toBeTruthy();
  expect(sourceSystem, "确认映射必须返回 sourceSystem").toBeTruthy();
  expect(category, "确认映射必须返回 category").toBeTruthy();
  return {
    mappingId: Number(mappingId),
    localTermId: Number(localTermId),
    standardTermId: Number(standardTermId),
    sourceSystem: sourceSystem as string,
    category: category as string,
  };
}

async function createCriticalActionCard(page: Page, suffix: string): Promise<CriticalActionCardCandidate> {
  const assetIdentity = `${actionCardIdentityPrefix}.${suffix}`;
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity,
    applicableScope: "ALL",
    sourceRef: "S19/S24/S27 急危重症代表切片：升级提示卡，不自动转科、不自动开嘱、不控制生命支持设备。",
    content: {
      schemaVersion: "1.0",
      title: `急危重症升级提示卡 ${suffix}`,
      actionCode: "STRONG_REMINDER",
      atSeverity: "HIGH",
      indicator: "critical",
      summary: "急诊分诊与 ICU 生命支持风险需人工确认。",
      detail: "系统只提示升级处置候选，不替代急诊、ICU 或生命支持设备控制，不自动转 ICU、不自动开嘱、不改呼吸机参数。",
      source: { label: "MedKernel S19/S24/S27 本地上线演练" },
      suggestions: [{ label: "人工确认急危重症升级", actionType: "OPEN_FORM", payload: { target: "S19/S24/S27" } }],
      overrideReasons: ["临床团队已完成人工复核与责任确认"],
      requiresPhysicianConfirmation: true,
      noAutoOrder: true,
      noAutoTransfer: true,
      noDeviceControl: true,
      noAutoVentilatorChange: true,
    },
  });
  await expectOk(response, "创建急危重症 ACTION_CARD 资产");
  const data = await responseData(response);
  return {
    assetType: "ACTION_CARD",
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "ACTION_CARD 必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "ACTION_CARD 必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "ACTION_CARD 必须返回 contentHash"),
    requiresPhysicianConfirmation: true,
    noAutoOrder: true,
    noAutoTransfer: true,
    noDeviceControl: true,
    noAutoVentilatorChange: true,
  };
}

async function createCriticalIcuPathwayAsset(page: Page, suffix: string): Promise<CriticalAssetCandidate & {
  templateId: string;
}> {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/pathway/templates"), { waitUntil: "networkidle" });
  const templateCode = `${pathwayIdentityPrefix}.${suffix}`;
  const response = await postApi(page, "/engine/pathway/pathway-templates", {
    ...apiContext(suffix, "pathway-create"),
    templateCode,
    name: `急危重症升级路径 ${suffix}`,
    diseaseCode: "CRITICAL-S19-S24-S27",
    templateLevel: "HOSPITAL",
    entryMode: "MANUAL_CONFIRM",
    startNodeCode: "TRIAGE",
    sourceRef: "local-e2e:critical-emergency-icu-frontdesk",
    description: "S19/S24/S27 急诊分诊与 ICU 生命支持风险代表切片路径，不自动转科、不控制设备。",
    entryCriteria: { all: [{ fact: "extensions.local.emergencyTriage.triageLevel", operator: "equals", value: "LEVEL_1" }] },
    exitCriteria: { all: [{ fact: "extensions.local.criticalCare.noDeviceControl", operator: "equals", value: true }] },
    milestones: [
      milestone("EMERGENCY_TRIAGE", "急诊分诊", "M-TRIAGE", "分诊确认", 0, 15, 10),
      milestone("ICU_REVIEW", "ICU 会诊", "M-ICU", "ICU 人工复核", 0, 30, 20),
      milestone("DEVICE_BOUNDARY", "设备边界", "M-DEVICE", "生命支持设备边界确认", 0, 45, 30),
    ],
    nodes: [
      node("TRIAGE", "急诊一级分诊人工确认", "MANUAL_GATE", "M-TRIAGE", 10, false, 15),
      node("ICU_REVIEW", "ICU 升级候选人工复核", "MANUAL_GATE", "M-ICU", 20, false, 30),
      node("DEVICE_BOUNDARY", "生命支持设备边界确认", "MANUAL_GATE", "M-DEVICE", 30, true, 45),
    ],
    edges: [
      {
        edgeCode: "EDGE.TRIAGE.ICU_REVIEW",
        fromNodeCode: "TRIAGE",
        toNodeCode: "ICU_REVIEW",
        edgeType: "DEFAULT",
        priority: 10,
      },
      {
        edgeCode: "EDGE.ICU_REVIEW.DEVICE_BOUNDARY",
        fromNodeCode: "ICU_REVIEW",
        toNodeCode: "DEVICE_BOUNDARY",
        edgeType: "DEFAULT",
        priority: 20,
      },
    ],
    metricBindings: [
      { nodeCode: "TRIAGE", metricCode: "CRITICAL.TIME_TO_TRIAGE", required: true },
      { nodeCode: "ICU_REVIEW", metricCode: "CRITICAL.TIME_TO_ICU_REVIEW", required: true },
      { nodeCode: "DEVICE_BOUNDARY", metricCode: "CRITICAL.DEVICE_BOUNDARY", required: true },
    ],
  });
  await expectOk(response, "创建急危重症 PATHWAY 路径资产");
  const data = await responseData(response);
  const templateId = requireText(
    textFieldAtPath(data, "template.templateId") ?? textField(data, "templateId"),
    "路径保存响应必须返回 templateId",
  );
  const detail = await getApi(page, `/engine/pathway/pathway-templates/${encodeURIComponent(templateId)}`);
  await expectOk(detail, "回读急危重症 PATHWAY 详情");
  const detailData = await responseData(detail);
  expect(textFieldAtPath(detailData, "template.templateCode")).toBe(templateCode);
  return {
    assetType: "PATHWAY",
    assetIdentity: templateCode,
    versionId: "",
    versionNo: "",
    contentHash: "",
    templateId,
  };
}

async function createAndPublishCriticalIcuRule(
  page: Page,
  suffix: string,
  options: {
    positiveContextSnapshotId: string;
    negativeContextSnapshotId: string;
    actionCard: { assetIdentity: string };
    pathway: { assetIdentity: string };
  },
) {
  await ensureReadySession(page, "engine-operator");
  const ruleCode = `${ruleIdentityPrefix}.${suffix}`;
  const create = await postApi(page, "/engine/rule/rules", {
    ...apiContext(suffix, "rule-create"),
    triggers: [
      {
        trigger_point: "patient-view",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "observations", "extensions"],
      },
    ],
    ruleCode,
    name: `急诊分诊 ICU 生命支持风险代表切片规则 ${suffix}`,
    ruleType: "RECORD",
    authoringMode: "DSL",
    riskLevel: "CRITICAL",
    priority: 990,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:critical-emergency-icu",
    changeSummary: "S19/S24/S27 代表切片：规则引用 Observation、extensions.local.emergencyTriage/criticalCare、PATHWAY 和 ACTION_CARD。",
    dsl: {
      applicability: {
        population: {},
        orgScope: {},
        settings: ["ED", "INPATIENT", "OUTPATIENT", "FOLLOWUP"],
        effective: { rolloutPercent: 100 },
      },
      when: {
        all: [
          { fact: "observations[].criticalFlag", operator: "contains", value: "CRITICAL" },
          { fact: "extensions.local.emergencyTriage.triageLevel", operator: "equals", value: "LEVEL_1" },
          { fact: "extensions.local.criticalCare.vasopressorRunning", operator: "equals", value: true },
        ],
      },
      then: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "急危重症升级候选需人工确认",
          detail: "一级分诊、乳酸危急值和升压药运行提示 ICU 升级候选；系统不自动转 ICU、不自动开嘱、不控制生命支持设备。",
          source: { label: "S19/S24/S27 急危重症代表切片" },
          actionCardRef: options.actionCard.assetIdentity,
          pathwayRef: options.pathway.assetIdentity,
          suggestions: [],
          overrideReasons: ["临床团队已完成人工复核与责任确认"],
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        title: "急诊分诊 ICU 生命支持风险代表切片规则",
        reason: "Observation 和急诊/ICU 本地扩展均来自当前临床上下文。",
        sourceRef: "local-e2e:critical-emergency-icu",
      },
    },
    explanation: {
      title: "急诊分诊 ICU 生命支持风险代表切片规则",
      summary: "证明风险规则、升级路径和动作卡进入当前机构生效版本。",
    },
    parameterBindings: {},
  });
  await expectOk(create, "创建急危重症 RULE 资产");
  const created = await responseData(create);
  const ruleId = requireText(textField(created, "ruleId"), "规则创建响应必须返回 ruleId");
  for (const testCase of [
    { caseType: "POSITIVE", expectedHit: true, expectedSeverity: "HIGH", expectedActionCode: "STRONG_REMINDER", contextSnapshotId: options.positiveContextSnapshotId },
    { caseType: "NEGATIVE", expectedHit: false, expectedSeverity: null, expectedActionCode: null, contextSnapshotId: options.negativeContextSnapshotId },
    { caseType: "BOUNDARY", expectedHit: true, expectedSeverity: "HIGH", expectedActionCode: "STRONG_REMINDER", contextSnapshotId: options.positiveContextSnapshotId },
    { caseType: "CONFLICT", expectedHit: true, expectedSeverity: "HIGH", expectedActionCode: "STRONG_REMINDER", contextSnapshotId: options.positiveContextSnapshotId },
  ]) {
    const response = await postApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/test-cases`, {
      ...apiContext(ruleId, `rule-test-${testCase.caseType}`),
      ...testCase,
    });
    await expectOk(response, `新增急危重症规则发布验证用例 ${testCase.caseType}`);
  }
  const testRun = await postApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/test`, apiContext(ruleId, "rule-test-run"));
  await expectOk(testRun, "执行急危重症规则发布验证用例");
  const testRunData = await responseData(testRun);
  expect(
    booleanField(testRunData, "allPassed"),
    `规则发布验证用例必须全部通过，结果=${JSON.stringify(recordField(testRunData, "results"))}`,
  ).toBe(true);
  for (const targetState of ["REVIEWED", "SHADOW", "CANARY", "FULL"]) {
    const impact = await getApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/impact`);
    await expectOk(impact, `读取急危重症规则 ${targetState} 影响摘要`);
    const transition = await postApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions`, {
      ...apiContext(ruleId, `rule-governance-${targetState}`),
      targetState,
      impactDigest: requireText(textField(await responseData(impact), "impactDigest"), "规则影响摘要必须返回 digest"),
      reason: `S19/S24/S27 急危重症规则推进至 ${targetState}`,
      publishEvidence: {
        qualityGate: {
          schemaValid: true,
          terminologyBindingComplete: true,
          dependencyIntegrityVerified: true,
          safetyMonotonicityVerified: true,
          impactSimulationPassed: true,
          summary: `S19/S24/S27 急危重症规则 ${targetState} 推进质量门已通过`,
        },
      },
    });
    await expectOk(transition, `急危重症规则治理推进至 ${targetState}`);
  }
  return {
    assetType: "RULE" as const,
    assetIdentity: ruleCode,
    ruleId,
    ruleVersionId: requireText(textField(created, "versionId"), "规则创建响应必须返回规则 versionId"),
  };
}

async function readCriticalRuntimeCandidates(
  page: Page,
  hospitalId: string,
  identities: { ruleIdentity: string; pathwayIdentity: string },
) {
  const [cdssRisk, rule, pathway] = await Promise.all([
    readHospitalRuntimeCandidate(page, hospitalId, "CDSS_RISK", "CDSS.RISK.MATRIX"),
    readHospitalRuntimeCandidate(page, hospitalId, "RULE", identities.ruleIdentity),
    readHospitalRuntimeCandidate(page, hospitalId, "PATHWAY", identities.pathwayIdentity),
  ]);
  return { cdssRisk, rule, pathway };
}

async function readHospitalRuntimeCandidate(
  page: Page,
  hospitalId: string,
  assetType: CriticalAssetCandidate["assetType"],
  assetIdentity: string,
): Promise<CriticalAssetCandidate> {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-candidates?assetType=${assetType}&keyword=${encodeURIComponent(assetIdentity)}&page=1&size=20`,
  );
  await expectOk(response, `读取本轮 ${assetType} runtime 候选`);
  const allowedStatuses = assetType === "PATHWAY" ? ["DRAFT", "PUBLISHED"] : ["PUBLISHED"];
  const candidate = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "assetType") === assetType &&
      textField(item, "assetIdentity") === assetIdentity &&
      allowedStatuses.includes(textField(item, "status") ?? ""),
  );
  return {
    assetType,
    assetIdentity,
    versionId: requireText(textField(candidate, "versionId"), `${assetType} 候选必须返回 versionId`),
    versionNo: requireText(textField(candidate, "versionNo"), `${assetType} 候选必须返回 versionNo`),
    contentHash: requireText(textField(candidate, "contentHash"), `${assetType} 候选必须返回 contentHash`),
  };
}

async function activateRuntimeWithCriticalAssets(
  page: Page,
  options: {
    hospitalId: string;
    terminology: CriticalAssetCandidate;
    cdssRisk: CriticalAssetCandidate;
    rule?: CriticalAssetCandidate;
    pathway: CriticalAssetCandidate;
    actionCard: CriticalAssetCandidate;
  },
) {
  const { releaseId, activationRequest } = await activateRuntimeRelease(page, {
    hospitalId: options.hospitalId,
    assets: [
      options.terminology,
      options.cdssRisk,
      ...(options.rule ? [options.rule] : []),
      options.pathway,
      options.actionCard,
    ],
    label: options.rule ? "激活包含急危重症五类资产的医院生效版本" : "激活急危重症规则验证预备 runtime",
    returnRequest: true,
  });
  const currentAfter = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfter, "回读急危重症医院生效版本");
  const detail = (await responseData(currentAfter)) as RuntimeReleaseDetail;
  expect(textFieldAtPath(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(releaseId);
  return {
    releaseId,
    revisionNo: numberFieldAtPath(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(textFieldAtPath(detail, "release.manifestSha256"), "机构生效版本必须返回 manifestSha256"),
    assets: detail.items ?? [],
    terminologyAsset: assertRuntimeContainsAsset(detail, options.terminology),
    cdssRiskAsset: assertRuntimeContainsAsset(detail, options.cdssRisk),
    ruleAsset: options.rule ? assertRuntimeContainsAsset(detail, options.rule) : ({ assetType: "RULE", assetIdentity: "", versionId: null } as RuntimeReleaseItem),
    pathwayAsset: assertRuntimeContainsAsset(detail, options.pathway),
    actionCardAsset: assertRuntimeContainsAsset(detail, options.actionCard),
    activationRequest,
  };
}

async function activateRuntimeRelease(
  page: Page,
  options: {
    hospitalId: string;
    assets: CriticalAssetCandidate[];
    label: string;
    returnRequest?: boolean;
  },
) {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(baselineAssets.baselineReleaseId, "当前平台标准版本必须存在").toBeTruthy();
  for (const required of requiredRuntimeAssetsForRehearsal) {
    expect(
      baselineAssets.activeAssets.some(
        (asset) => asset.assetType === required.assetType && asset.assetIdentity === required.assetIdentity,
      ),
      `平台标准版本缺少 ${required.assetType}:${required.assetIdentity}`,
    ).toBe(true);
  }
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const currentReleaseId = textFieldAtPath(await responseData(current), "release.releaseId");
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId,
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest: null,
    activeAssets: uniqueRuntimeAssets([
      ...baselineAssets.activeAssets,
      ...options.assets.map(runtimeSelection),
    ]),
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, options.label);
  const releaseId = requireText(textField(await responseData(activated), "releaseId"), "激活必须返回 releaseId");
  return options.returnRequest ? { releaseId, activationRequest } : { releaseId, activationRequest: undefined };
}

async function createCriticalPatientFromFrontdesk(page: Page, suffix: string) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const dialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(dialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  await dialog.getByLabel("脱敏姓名").fill(`急*${idLast4.slice(-1)}`);
  const gender = dialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await dialog.getByRole("spinbutton", { name: "年龄" }).fill("67");
  await dialog.getByLabel("身份证后四位").fill(idLast4);
  const responsePromise = waitForPost(page, "/engine/mpi/patients");
  await dialog.getByRole("button", { name: "保存患者" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "创建急危重症演练脱敏患者");
  const patientId = requireText(textField(await responseData(response), "mpiId"), "患者创建响应必须返回 MPI");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    encounterId: `enc-critical-${suffix.toLowerCase()}`,
  };
}

async function createCriticalMonitoringAdapter(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  const adapterId = `critical-emergency-icu-${suffix.toLowerCase()}`;
  const config = criticalAdapterConfig();
  const response = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `S19/S24/S27 急危重症监护适配器 ${suffix}`,
    protocolType: "Webhook",
    configJson: JSON.stringify(config),
  });
  await expectOk(response, "创建 LIS_MONITORING_CRITICAL 适配器");
  return { adapterId, protocolType: "Webhook", ...config };
}

function criticalAdapterConfig() {
  return {
    systemFamilyCode: criticalSourceSystem,
    sourceSystem: criticalSourceSystem,
    targetSystem: criticalSourceSystem,
    baseUrl: "https://critical-monitoring.example.test",
    healthPath: "/health",
    outboundPath: "/manual-escalation",
    connectTimeoutMs: 800,
    requestTimeoutMs: 1200,
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      { sourcePath: "/encounterType", targetPath: "/admission/encounterType" },
      { sourcePath: "/departmentId", targetPath: "/admission/departmentId" },
      { sourcePath: "/diagnosisCode", targetPath: "/conditions/0/code" },
      { sourcePath: "/diagnosisName", targetPath: "/conditions/0/displayName" },
      { sourcePath: "/shockIndexCode", targetPath: "/observations/0/code" },
      { sourcePath: "/shockIndexValue", targetPath: "/observations/0/valueNumeric" },
      { sourcePath: "/shockIndexFlag", targetPath: "/observations/0/criticalFlag" },
      { sourcePath: "/lactateCode", targetPath: "/observations/1", targetDictionaryKey: "LOINC", category: "LAB" },
      { sourcePath: "/lactateValue", targetPath: "/observations/1/valueNumeric" },
      { sourcePath: "/lactateUnit", targetPath: "/observations/1/unit" },
      { sourcePath: "/criticalFlag", targetPath: "/observations/1/criticalFlag" },
      { sourcePath: "/procedureCode", targetPath: "/procedures/0/code" },
      { sourcePath: "/procedureName", targetPath: "/procedures/0/displayName" },
      { sourcePath: "/triageLevel", targetPath: "/extensions/local/emergencyTriage/triageLevel" },
      { sourcePath: "/destinationCandidate", targetPath: "/extensions/local/emergencyTriage/destinationCandidate" },
      { sourcePath: "/manualEscalationRequired", targetPath: "/extensions/local/emergencyTriage/manualEscalationRequired" },
      { sourcePath: "/ventilatorMode", targetPath: "/extensions/local/criticalCare/ventilatorMode" },
      { sourcePath: "/vasopressorRunning", targetPath: "/extensions/local/criticalCare/vasopressorRunning" },
      { sourcePath: "/noDeviceControl", targetPath: "/extensions/local/criticalCare/noDeviceControl" },
    ],
  };
}

async function createCriticalMonitoringWebhook(page: Page, suffix: string) {
  const webhookId = `critical-emergency-icu-${suffix.toLowerCase()}`;
  const response = await postApi(page, "/engine/integration/webhooks", {
    webhookId,
    name: `S19/S24/S27 急危重症监护回传 ${suffix}`,
    callbackUrl: "https://critical-monitoring.example.test/medkernel/events",
    eventsSubscribed: "CRITICAL_MONITORING_EVENT",
  });
  await expectOk(response, "创建急危重症回调通道");
  const data = await responseData(response);
  return {
    webhookId,
    sharedSecret: requireText(textField(data, "sharedSecret"), "回调通道必须一次性返回共享密钥"),
  };
}

async function generateCriticalSignaturePreview(page: Page, webhookId: string) {
  const response = await postApi(page, "/engine/integration/webhooks/test", {
    webhookId,
    payload: JSON.stringify({ traceId: `preview-${webhookId}`, eventType: "CRITICAL_MONITORING_EVENT" }),
  });
  await expectOk(response, "生成急危重症回调签名预览");
  const data = await responseData(response);
  expect(textField(data, "signature"), "签名预览必须返回裸 hex 签名").toMatch(/^[0-9a-f]{64}$/i);
}

async function createCriticalMonitoringOnboarding(page: Page, suffix: string, adapterId: string) {
  const onboardingId = `onb-critical-emergency-${suffix.toLowerCase()}`;
  const response = await postApi(page, "/engine/integration/onboardings", {
    onboardingId,
    name: `S19/S24/S27 急危重症监护接入 ${suffix}`,
    accessMode: "ADAPTER",
    adapterId,
    fhirVersion: null,
    systemFamilyCode: criticalSourceSystem,
    sourceSystem: criticalSourceSystem,
    businessScenario: "S19/S24/S27 急危重症预警",
    orgPath: "/platform/group/hospital/ed-icu",
    callbackWebhookId: null,
  });
  await expectOk(response, "创建急危重症接入申请");
  const data = await responseData(response);
  return {
    onboardingId: requireText(textField(data, "onboardingId"), "接入申请必须返回 onboardingId"),
    accessMode: textField(data, "routeType") ?? "ADAPTER",
    adapterId,
    systemFamilyCode: textField(data, "systemFamilyCode") ?? criticalSourceSystem,
    sourceSystem: textField(data, "sourceSystem") ?? criticalSourceSystem,
    businessScenario: textField(data, "businessScenario") ?? "S19/S24/S27 急危重症预警",
    healthStatus: textField(data, "healthStatus") ?? "NOT_CONNECTED",
  };
}

async function postSignedCriticalInbound(
  page: Page,
  options: {
    suffix: string;
    adapterId: string;
    webhookId: string;
    webhookSecret: string;
    patient: { patientId: string; encounterId: string };
    runtimeReleaseId: string;
    positive?: boolean;
  },
) {
  await ensureReadySession(page, "platform-admin");
  const positive = options.positive ?? true;
  const request: InboundWebhookRequest = {
    messageId: `in-critical-${options.suffix}`,
    traceId: `trace-critical-${options.suffix}`,
    adapterId: options.adapterId,
    sourceSystem: criticalSourceSystem,
    eventType: "REPORT",
    patientId: options.patient.patientId,
    encounterId: options.patient.encounterId,
    clinicalSetting: "ED",
    triggerPoint: "patient-view",
    occurredAt: "2026-07-08T02:15:00Z",
    payload: {
      patientId: options.patient.patientId,
      encounterType: "ED",
      departmentId: "ED",
      diagnosisCode: shockDiagnosisCode,
      diagnosisName: positive ? "休克" : "轻度脱水",
      shockIndexCode: "SHOCK_INDEX",
      shockIndexValue: positive ? 1.42 : 0.82,
      shockIndexFlag: positive ? "HIGH" : "NORMAL",
      lactateCode: lactateLocalCode,
      lactateValue: positive ? 5.2 : 1.1,
      lactateUnit: "mmol/L",
      criticalFlag: positive ? "CRITICAL" : "NORMAL",
      procedureCode: ventilationProcedureCode,
      procedureName: positive ? "机械通气" : "氧疗观察",
      triageLevel: positive ? "LEVEL_1" : "LEVEL_3",
      destinationCandidate: positive ? "ICU" : "OBSERVATION",
      manualEscalationRequired: true,
      ventilatorMode: positive ? "SIMV" : "NONE",
      vasopressorRunning: positive,
      noDeviceControl: true,
    },
  };
  const timestamp = currentEpochSeconds();
  const signature = `sha256=${signHmacSha256(options.webhookSecret, timestamp, request)}`;
  const response = await postApi(
    page,
    `/engine/integration/webhooks/${encodeURIComponent(options.webhookId)}/inbound`,
    request,
    {
      "X-MedKernel-Timestamp": timestamp,
      "X-MedKernel-Signature": signature,
    },
  );
  await expectOk(response, "S19/S24/S27 急危重症签名入站");
  const data = await responseData(response);
  const clinicalEventId = requireText(textField(data, "clinicalEventId"), "入站必须返回 clinicalEventId");
  const clinicalEvent = await waitForClinicalEventProcessed(page, clinicalEventId, options.runtimeReleaseId);
  return {
    messageId: requireText(textField(data, "messageId"), "入站必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "入站必须返回 traceId"),
    adapterId: options.adapterId,
    webhookId: options.webhookId,
    patientId: options.patient.patientId,
    encounterId: options.patient.encounterId,
    contextSnapshotId: "",
    sourceSystem: criticalSourceSystem,
    status: requireText(textField(data, "status"), "入站必须返回状态"),
    clinicalEventStatus: textField(data, "clinicalEventStatus"),
    clinicalEvent,
    mappedFieldCount: numberField(data, "mappedFieldCount") ?? 0,
    mappedPayload: recordValue(recordField(data, "mappedPayload")) ?? {},
    signedPayload: request.payload,
  };
}

async function waitForClinicalEventProcessed(
  page: Page,
  eventId: string,
  runtimeReleaseId: string,
): Promise<ClinicalEventDetailEvidence> {
  const deadline = Date.now() + 30_000;
  let last: ClinicalEventDetailEvidence | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, `/engine/clinical-events/${encodeURIComponent(eventId)}`);
    await expectOk(response, "读取 S19/S24/S27 入站临床事件详情");
    const data = await responseData(response);
    last = {
      eventId: requireText(textField(data, "eventId"), "临床事件详情必须返回 eventId"),
      status: requireText(textField(data, "status"), "临床事件详情必须返回 status"),
      errorCode: textField(data, "errorCode"),
      errorClass: textField(data, "errorClass"),
      retryCount: numberField(data, "retryCount"),
      runtimeReleaseId: textField(data, "runtimeReleaseId"),
    };
    if (last.status === "PROCESSED") {
      expect(last.runtimeReleaseId, "S19/S24/S27 入站事件必须绑定本轮 runtime").toBe(runtimeReleaseId);
      expect(last.errorCode, "S19/S24/S27 入站事件处理成功不得有 errorCode").toBeNull();
      return last;
    }
    if (last.status === "FAILED") {
      throw new Error(`S19/S24/S27 入站事件 ${eventId} 处理失败：${last.errorCode ?? "UNKNOWN"}`);
    }
    await waitForPollingInterval(250);
  }
  throw new Error(`S19/S24/S27 入站事件 ${eventId} 未处理到 PROCESSED，最后状态：${last?.status ?? "UNKNOWN"}`);
}

async function readLatestContextForPatient(page: Page, patientId: string): Promise<ContextSnapshotSummary> {
  const list = await getApi(
    page,
    `/engine/context/snapshots?patientId=${encodeURIComponent(patientId)}&status=ACTIVE&page=1&size=5&sort=createdAt,desc`,
  );
  await expectOk(list, "按患者读取最新上下文快照");
  const snapshotId = requireText(
    textField(pageItems(await responseData(list))[0], "snapshotId"),
    "必须找到入站事件生成的上下文快照",
  );
  const detail = await getApi(page, `/engine/context/snapshots/${encodeURIComponent(snapshotId)}`);
  await expectOk(detail, "读取入站事件生成的上下文详情");
  const context = await responseData(detail);
  return {
    patientId: requireText(textFieldAtPath(context, "resources.patient.mpi"), "上下文详情必须返回 patient.mpi"),
    snapshotId: requireText(textField(context, "snapshotId"), "上下文详情必须返回 snapshotId"),
    runtimeReleaseId: requireText(textField(context, "runtimeReleaseId"), "上下文详情必须返回 runtimeReleaseId"),
    encounterId: textFieldAtPath(context, "resources.encounters.0.encounterId"),
    clinicalSetting: textField(context, "clinicalSetting") ?? "ED",
    resources: recordValue(recordField(context, "resources")) ?? {},
  };
}

function assertSnapshotContainsCriticalFacts(snapshot: ContextSnapshotSummary) {
  const resources = snapshot.resources;
  expect(snapshot.clinicalSetting, "上下文必须保留标准急诊场景").toBe("ED");
  expect(
    arrayField(resources, "encounters").some(
      (item) => textField(item, "encounterType") === "ED" && textField(item, "departmentId") === "ED",
    ),
    "上下文必须包含急诊 encounter",
  ).toBe(true);
  expect(
    arrayField(resources, "conditions").some(
      (item) => textField(item, "code") === shockDiagnosisCode && textField(item, "displayName") === "休克",
    ),
    "上下文必须包含休克 Condition",
  ).toBe(true);
  expect(
    arrayField(resources, "observations").some(
      (item) => textField(item, "code") === "SHOCK_INDEX" && Number(numberField(item, "valueNumeric")) >= 1.3,
    ),
    "上下文必须包含休克指数 Observation",
  ).toBe(true);
  expect(
    arrayField(resources, "observations").some(
      (item) =>
        textField(item, "code") === lactateStandardCode &&
        Number(numberField(item, "valueNumeric")) >= 4 &&
        textField(item, "criticalFlag") === "CRITICAL",
    ),
    "上下文必须包含 LOINC 乳酸危急 Observation",
  ).toBe(true);
  expect(
    arrayField(resources, "procedures").some(
      (item) => textField(item, "code") === ventilationProcedureCode && textField(item, "displayName") === "机械通气",
    ),
    "上下文必须包含机械通气 Procedure",
  ).toBe(true);
  expect(textFieldAtPath(resources, "extensions.local.emergencyTriage.triageLevel")).toBe("LEVEL_1");
  expect(textFieldAtPath(resources, "extensions.local.emergencyTriage.destinationCandidate")).toBe("ICU");
  expect(booleanFieldAtPath(resources, "extensions.local.emergencyTriage.manualEscalationRequired")).toBe(true);
  expect(textFieldAtPath(resources, "extensions.local.criticalCare.ventilatorMode")).toBe("SIMV");
  expect(booleanFieldAtPath(resources, "extensions.local.criticalCare.vasopressorRunning")).toBe(true);
  expect(booleanFieldAtPath(resources, "extensions.local.criticalCare.noDeviceControl")).toBe(true);
}

async function triggerCriticalRecommendationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem;
      pathwayAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    rule: CriticalAssetCandidate & { ruleId: string; ruleVersionId: string };
  },
) {
  const { snapshot } = options;
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "提醒与推荐" })).toBeVisible();
  await page.getByRole("button", { name: "登记触发评估" }).click();
  const dialog = page.getByRole("dialog", { name: "登记一次推荐触发评估" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  const snapshotButton = dialog.locator(`button[data-snapshot-id="${snapshot.snapshotId}"]`);
  await expect(snapshotButton, `提醒推荐页必须展示本轮 S19/S24/S27 快照 ${snapshot.snapshotId}`).toBeVisible({ timeout: 20_000 });
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "查看患者");
  const evaluateResponsePromise = waitForEvaluateResponse(page, snapshot);
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  await expectHttpOk(evaluateResponse, "临床用户从真实前台触发 S19/S24/S27 推荐评估");
  const evaluation = await responseData(evaluateResponse);
  const triggerId = requireText(textField(evaluation, "triggerId"), "推荐评估响应必须返回 triggerId");
  const relatedCardIds = await readRecommendationTriggerDiagnose(page, triggerId);
  const recommendation = await findCriticalRuleCard(page, relatedCardIds, {
    triggerId,
    snapshot,
    runtime: options.runtime,
    rule: options.rule,
  });
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    triggerId,
    relatedCardIds,
    ...recommendation,
  };
}

async function findCriticalRuleCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    triggerId: string;
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem;
      pathwayAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    rule: CriticalAssetCandidate & { ruleId: string; ruleVersionId: string };
  },
) {
  const matched: Array<{
    cardId: string;
    cardStatus: string | null;
    triggerRuntimeReleaseId: string | null;
    cardType: string | null;
    requiresPhysicianConfirmation: boolean;
    aiGenerated: boolean;
    explanation: Record<string, unknown>;
  }> = [];
  const inspected: Array<Record<string, unknown>> = [];
  for (const cardId of Array.from(new Set(relatedCardIds))) {
    const detailResponse = await getApi(page, `/engine/recommendations/cards/${encodeURIComponent(cardId)}`);
    await expectOk(detailResponse, `推荐卡 ${cardId} 详情应可读取`);
    const detail = await responseData(detailResponse);
    const explanation = parseJsonRecord(
      requireText(textFieldAtPath(detail, "card.explanationJson"), "推荐卡必须返回解释 JSON"),
    );
    if (!explanation) continue;
    const runtimeRelease = recordValue(recordField(explanation, "runtimeRelease"));
    const ruleExplanation = recordValue(recordField(explanation, "ruleExplanation"));
    const conditionEvidence = arrayField(ruleExplanation, "conditionEvidence");
    const runtimeAssetEvidence = arrayField(ruleExplanation, "runtimeAssetEvidence");
    inspected.push({
      cardId,
      triggerId: textFieldAtPath(detail, "trigger.triggerId"),
      contextSnapshotId: textFieldAtPath(detail, "trigger.contextSnapshotId"),
      runtimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      matchType: textField(explanation, "matchType"),
      ruleId: textField(explanation, "ruleId"),
      ruleCode: textField(explanation, "ruleCode"),
      ruleVersionId: textField(explanation, "ruleVersionId"),
      runtimeAssetVersionId: textField(runtimeRelease, "assetVersionId"),
      runtimeAssetVersionNo: textField(runtimeRelease, "assetVersionNo"),
      runtimeContentHash: textField(runtimeRelease, "contentHash"),
      conditionFacts: conditionEvidence.map((item) => ({
        fact: textField(item, "fact"),
        matched: booleanField(item, "matched"),
      })),
      runtimeAssets: runtimeAssetEvidence.map((item) => ({
        assetType: textField(item, "assetType"),
        assetIdentity: textField(item, "assetIdentity"),
        assetVersion: textField(item, "assetVersion"),
        contentHash: textField(item, "contentHash"),
        noDeviceControl: booleanField(item, "noDeviceControl"),
      })),
    });
    const matches =
      textFieldAtPath(detail, "trigger.triggerId") === options.triggerId &&
      textFieldAtPath(detail, "trigger.contextSnapshotId") === options.snapshot.snapshotId &&
      textFieldAtPath(detail, "trigger.runtimeReleaseId") === options.runtime.releaseId &&
      textField(explanation, "matchType") === "RULE" &&
      textField(explanation, "ruleId") === options.rule.ruleId &&
      textField(explanation, "ruleCode") === options.rule.assetIdentity &&
      textField(explanation, "ruleVersionId") === options.rule.ruleVersionId &&
      textField(runtimeRelease, "runtimeReleaseId") === options.runtime.releaseId &&
      textField(runtimeRelease, "assetVersionId") === options.runtime.ruleAsset.versionId &&
      textField(runtimeRelease, "assetVersionNo") === options.runtime.ruleAsset.versionNo &&
      textField(runtimeRelease, "contentHash") === options.runtime.ruleAsset.contentHash &&
      conditionEvidence.some((item) => textField(item, "fact") === "observations[].criticalFlag" && booleanField(item, "matched") === true) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "extensions.local.emergencyTriage.triageLevel" &&
          booleanField(item, "matched") === true,
      ) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "extensions.local.criticalCare.vasopressorRunning" &&
          booleanField(item, "matched") === true,
      ) &&
      runtimeAssetEvidence.some(
        (item) =>
          textField(item, "assetType") === "ACTION_CARD" &&
          textField(item, "assetIdentity") === options.runtime.actionCardAsset.assetIdentity &&
          textField(item, "assetVersion") === options.runtime.actionCardAsset.versionNo &&
          textField(item, "contentHash") === options.runtime.actionCardAsset.contentHash,
      ) &&
      runtimeAssetEvidence.some(
        (item) =>
          textField(item, "assetType") === "PATHWAY" &&
          textField(item, "assetIdentity") === options.runtime.pathwayAsset.assetIdentity &&
          textField(item, "assetVersion") === options.runtime.pathwayAsset.versionNo &&
          textField(item, "contentHash") === options.runtime.pathwayAsset.contentHash,
      );
    if (!matches) continue;
    matched.push({
      cardId,
      cardStatus: textFieldAtPath(detail, "card.status"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      cardType: textFieldAtPath(detail, "card.cardType") ?? "RISK",
      requiresPhysicianConfirmation: booleanFieldAtPath(detail, "card.requiresPhysicianConfirmation"),
      aiGenerated: booleanFieldAtPath(detail, "card.aiGenerated"),
      explanation,
    });
  }
  expect(
    matched.map((card) => card.cardId),
    `必须唯一定位本轮 S19/S24/S27 RULE 推荐卡，候选摘要=${JSON.stringify(inspected)}`,
  ).toHaveLength(1);
  return matched[0];
}

async function completeCriticalManualEscalation(
  page: Page,
  options: {
    cardId: string;
    actionCard: CriticalActionCardCandidate;
    actionCardAsset: RuntimeReleaseItem;
  },
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  await page.getByLabel("患者或证据线索").fill(options.cardId);
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const drawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await drawer.getByRole("tab", { name: /医师反馈/u }).click();
  await drawer
    .getByLabel("采纳说明（可选）")
    .fill("医生已人工确认急危重症升级候选；系统不自动转 ICU、不自动开嘱、不控制设备、不改呼吸机参数。");
  const responsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "确认采纳建议" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "登记 S19/S24/S27 人工升级确认");
  const feedback = await responseData(response);
  const detailResponse = await getApi(page, `/engine/recommendations/cards/${encodeURIComponent(options.cardId)}`);
  await expectOk(detailResponse, "回读 S19/S24/S27 推荐卡反馈详情");
  const detail = await responseData(detailResponse);
  const persisted = arrayField(detail, "feedback").find(
    (item) =>
      textField(item, "feedbackId") === textField(feedback, "feedbackId") &&
      textField(item, "feedbackType") === "ACCEPT" &&
      textField(item, "operatorRole") === "DOCTOR" &&
      textField(item, "reasonCode") === "CONFIRMED",
  );
  expect(persisted, "人工确认反馈必须从推荐详情回读").toBeTruthy();
  expect(textField(persisted, "cardId"), "人工确认反馈必须绑定本轮推荐卡").toBe(options.cardId);
  const actionCardEvidence = {
    assetType: options.actionCardAsset.assetType,
    assetIdentity: options.actionCardAsset.assetIdentity,
    versionId: options.actionCardAsset.versionId,
    versionNo: options.actionCardAsset.versionNo,
    contentHash: options.actionCardAsset.contentHash,
    entryState: options.actionCardAsset.entryState,
    noAutoOrder: options.actionCard.noAutoOrder,
    noAutoTransfer: options.actionCard.noAutoTransfer,
    noDeviceControl: options.actionCard.noDeviceControl,
    noAutoVentilatorChange: options.actionCard.noAutoVentilatorChange,
  };
  return {
    feedbackId: textField(feedback, "feedbackId"),
    cardStatus: textField(feedback, "cardStatus"),
    canonicalSessionRole: "clinical-user",
    persisted,
    noAutoOrder: true,
    noAutoTransfer: true,
    noDeviceControl: true,
    noAutoVentilatorChange: true,
    actionCardEvidence,
  };
}

async function completeCriticalEscalationTodo(
  page: Page,
  options: {
    suffix: string;
    recommendation: { cardId: string };
    snapshot: ContextSnapshotSummary;
  },
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/workflow/todos"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "协同任务" }).first()).toBeVisible({
    timeout: 30_000,
  });
  const cardLink = page.locator(`a[href*="cardId=${options.recommendation.cardId}"]`).first();
  await expect(cardLink, "应能定位本轮急危重症推荐卡对应的待办链接").toBeVisible({ timeout: 30_000 });
  const todoRow = cardLink.locator("xpath=ancestor::tr");
  await expect(todoRow, "应能定位本轮急危重症协同待办").toBeVisible({ timeout: 30_000 });
  const todoId = requireText(
    (await todoRow.getAttribute("data-row-key")) ?? null,
    "协同任务表格必须暴露本轮待办 todoId",
  );
  const completeResponsePromise = waitForPost(page, "/engine/workflow/todos/");
  await todoRow.getByRole("button", { name: "完成" }).click();
  const completeDialog = page.getByRole("dialog", { name: "完成待办" });
  await expect(completeDialog).toBeVisible({ timeout: 10_000 });
  const completionReason = "临床用户已完成升级协同登记，仍需医师责任确认；系统不自动转 ICU、不自动开嘱、不控制设备。";
  await completeDialog.getByLabel("完成说明").fill(completionReason);
  await completeDialog.getByRole("button", { name: "确认完成" }).click();
  const completeResponse = await completeResponsePromise;
  await expectHttpOk(completeResponse, "完成人工急危重症升级待办");
  const completed = await responseData(completeResponse);
  expect(textField(completed, "todoId"), "完成响应必须绑定本轮待办").toBe(todoId);
  expect(textField(completed, "status"), "急危重症待办完成后必须为 COMPLETED").toBe("COMPLETED");
  expect(textField(completed, "sourceId"), "完成响应必须绑定本轮推荐卡").toBe(options.recommendation.cardId);
  expect(textField(completed, "patientId"), "完成响应必须绑定本轮患者").toBe(options.snapshot.patientId);
  expect(textField(completed, "encounterId"), "完成响应必须绑定本轮就诊").toBe(options.snapshot.encounterId);
  expect(textField(completed, "completionReason") ?? "", "完成说明必须持久化").toContain("不控制设备");
  await expect(completeDialog).toBeHidden({ timeout: 20_000 });
  return {
    todoId,
    sourceType: textField(completed, "sourceType") ?? "RECOMMENDATION_CARD",
    sourceId: textField(completed, "sourceId"),
    priority: textField(completed, "priority") ?? "CRITICAL",
    status: textField(completed, "status"),
    completedByRole: "clinical-user",
    completionReason: textField(completed, "completionReason"),
    patientId: textField(completed, "patientId"),
    encounterId: textField(completed, "encounterId"),
  };
}

async function attachCriticalEmergencyIcuEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: CriticalEmergencyIcuApiEvidence;
    monitoringAdapter: unknown;
    emergencyOnboarding: unknown;
    webhookSignature: unknown;
    terminologyGate: unknown;
    riskMatrix: unknown;
    actionCard: unknown;
    ruleAsset: unknown;
    pathwayAsset: unknown;
    runtime: {
      releaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      terminologyAsset: RuntimeReleaseItem;
      cdssRiskAsset: RuntimeReleaseItem;
      ruleAsset: RuntimeReleaseItem;
      pathwayAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    activationRequest: unknown;
    clinicalContext: unknown;
    inboundMonitoringEvent: unknown;
    clinicalTrigger: unknown;
    recommendation: unknown;
    manualEscalation: unknown;
    escalationTodo: unknown;
    observedStages: Set<string>;
  },
) {
  for (const stages of Object.values(requiredStages)) {
    for (const stage of stages) {
      expect(evidence.observedStages.has(stage), `缺少 S19/S24/S27 阶段：${stage}`).toBe(true);
    }
  }
  await testInfo.attach("critical-emergency-icu-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S19", "S24", "S27"],
        productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
        versionedAssets: ["TERMINOLOGY", "CDSS_RISK", "RULE", "PATHWAY", "ACTION_CARD"],
        deliveryShapes: ["API_EVENT"],
        serviceCombinations: [
          "THIRD_PARTY_INTERFACE",
          "CLINICAL_RUNTIME",
          "PROFESSIONAL_COLLABORATION",
        ],
        scopeStatement:
          "急诊分诊与 ICU 生命支持风险代表切片：LIS_MONITORING_CRITICAL 入站监护事实、HIS_EMR_CDR 急诊分诊上下文、当前机构生效版本风险规则、路径升级候选、人工确认和升级待办闭环，不代表完整急诊系统、完整 ICU 系统、完整生命支持系统、生命支持设备控制、完整 S19/S24/S27、完整 S0-S40 或完整上线验收。",
        apiEvidence: evidence.apiEvidence,
        monitoringAdapter: evidence.monitoringAdapter,
        emergencyOnboarding: evidence.emergencyOnboarding,
        webhookSignature: evidence.webhookSignature,
        terminologyGate: evidence.terminologyGate,
        riskMatrix: evidence.riskMatrix,
        actionCard: evidence.actionCard,
        ruleAsset: evidence.ruleAsset,
        pathwayAsset: evidence.pathwayAsset,
        runtime: evidence.runtime,
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        inboundMonitoringEvent: evidence.inboundMonitoringEvent,
        clinicalTrigger: evidence.clinicalTrigger,
        recommendation: evidence.recommendation,
        manualEscalation: evidence.manualEscalation,
        escalationTodo: evidence.escalationTodo,
        scenarioEvidence: [
          { code: "S19", observedStages: requiredStages.S19 },
          { code: "S24", observedStages: requiredStages.S24 },
          { code: "S27", observedStages: requiredStages.S27 },
        ],
      },
      null,
      2,
    ),
  });
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
    achievementCriteria: { all: [milestoneCode] },
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
    responsibleRole: "主管医师",
    accountableRole: "主管医师",
    consultedRoles: ["ICU 医师", "急诊护士"],
    informedRoles: ["质控员"],
    timeWindowMinutes,
    terminal,
    config: {
      visibleSummary: name,
      noAutoOrder: true,
      noAutoTransfer: true,
      noDeviceControl: true,
      noAutoVentilatorChange: true,
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

function recordStage(stages: Set<string>, stage: string) {
  stages.add(stage);
}

function waitForEvaluateResponse(page: Page, snapshot: ContextSnapshotSummary) {
  return page.waitForResponse(
    (response) => {
      if (response.request().method() !== "POST") return false;
      const pathname = new URL(response.url()).pathname;
      if (!pathname.endsWith("/engine/recommendations:evaluate")) return false;
      try {
        const payload = response.request().postDataJSON() as Record<string, unknown>;
        return (
          payload.contextSnapshotId === snapshot.snapshotId &&
          payload.patientId === snapshot.patientId &&
          payload.triggerType === "patient-view"
        );
      } catch {
        return false;
      }
    },
    { timeout: 30_000 },
  );
}

async function readRecommendationTriggerDiagnose(page: Page, triggerId: string) {
  const response = await getApi(
    page,
    `/engine/recommendations/triggers/${encodeURIComponent(triggerId)}/diagnose`,
  );
  await expectOk(response, "推荐触发诊断应可由真实服务读取");
  const diagnose = await responseData(response);
  const relatedCardIds = arrayFieldAtPath(diagnose, "relatedEntities.cards").filter(
    (value): value is string => typeof value === "string" && value.trim().length > 0,
  );
  expect(relatedCardIds.length, "推荐触发诊断必须返回关联推荐卡").toBeGreaterThan(0);
  return relatedCardIds;
}

async function localRehearsalHospitalId(page: Page) {
  const response = await getApi(page, "/engine/org/org-units?keyword=本地上线演练医院&page=1&size=20");
  await expectOk(response, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "name") === "本地上线演练医院" ||
      textField(item, "code") === "e2e-rehearsal-hospital",
  );
  return requireText(textField(hospital, "id"), "必须找到本地上线演练医院");
}

function assertRuntimeContainsAsset(detail: RuntimeReleaseDetail, expected: CriticalAssetCandidate) {
  const matched = (detail.items ?? []).find(
    (item) =>
      item.assetType === expected.assetType &&
      item.assetIdentity === expected.assetIdentity &&
      item.versionId === expected.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(matched, `runtime 必须包含 ${expected.assetType}:${expected.assetIdentity}`).toBeTruthy();
  return matched as RuntimeReleaseItem;
}

function runtimeSelection(asset: CriticalAssetCandidate): RuntimeAssetSelection {
  return {
    assetType: asset.assetType,
    assetIdentity: asset.assetIdentity,
    versionId: asset.versionId,
  };
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function currentEpochSeconds() {
  return String(Math.floor(Date.now() / 1000));
}

function signHmacSha256(secret: string, timestamp: string, payload: unknown) {
  return createHmac("sha256", secret)
    .update(`${timestamp}.${JSON.stringify(payload)}`)
    .digest("hex");
}

async function expectHttpOk(response: APIResponse, label: string) {
  const text = await response.text();
  expect(response.ok(), `${label} status=${response.status()} body=${text}`).toBe(true);
}

async function waitForPost(page: Page, pathIncludes: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(pathIncludes),
    { timeout: 30_000 },
  );
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, option: string) {
  const field = dialog.getByRole("combobox", { name: label }).first();
  const selector = field
    .locator("xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')]")
    .first()
    .locator(".ant-select-selector")
    .first();
  if (await selector.isVisible().catch(() => false)) {
    await selector.click();
  } else {
    await field.click();
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, `${label} 下拉应展开`).toBeVisible({ timeout: 5_000 });
  const item = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`, "u") })
    .first();
  await expect(item, `${label} 应存在选项 ${option}`).toBeVisible({ timeout: 10_000 });
  await item.click();
}

function apiContext(subject: string, step: string) {
  return {
    request_id: `req-critical-${step}-${subject}`,
    trace_id: `trace-critical-${step}-${subject}`,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function parseJsonRecord(value: string) {
  try {
    return recordValue(JSON.parse(value));
  } catch {
    return null;
  }
}

function arrayField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return Array.isArray(raw) ? raw : [];
}

function arrayFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return Array.isArray(raw) ? raw : [];
}

function recordField(value: unknown, field: string) {
  const record = recordValue(value);
  return record ? record[field] : undefined;
}

function textField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "number" ? raw : null;
}

function booleanField(value: unknown, field: string) {
  return recordField(value, field) === true;
}

function textFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "number" ? raw : null;
}

function booleanFieldAtPath(value: unknown, path: string) {
  return valueAtPath(value, path) === true;
}

function valueAtPath(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, segment) => {
    if (current == null) return undefined;
    if (Array.isArray(current)) {
      const index = Number(segment);
      return Number.isInteger(index) ? current[index] : undefined;
    }
    if (typeof current === "object") {
      return (current as Record<string, unknown>)[segment];
    }
    return undefined;
  }, value);
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function requireText(value: string | null | undefined, message: string) {
  expect(value, message).toBeTruthy();
  return value as string;
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
