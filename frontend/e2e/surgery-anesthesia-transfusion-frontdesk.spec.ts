import { createHmac } from "node:crypto";
import {
  expect,
  test,
  type APIResponse,
  type Locator,
  type Page,
  type TestInfo,
} from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  postApi,
  putApi,
  requiredRuntimeAssetsForRehearsal,
  resolveBaselineRuntimeAssets,
  responseData,
  pageItems,
  resolvedTenantIdFor,
  waitForPollingInterval,
} from "./support/auth";
import { standardPatientResourceConsumerMatrix } from "./support/standardPatientResourceMatrix";

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

type PeriopAssetCandidate = {
  assetType: "TERMINOLOGY" | "SAFETY" | "CDSS_RISK" | "RULE" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type PeriopActionCardCandidate = PeriopAssetCandidate & {
  assetType: "ACTION_CARD";
  requiresPhysicianConfirmation: boolean;
  noAutoOrder: boolean;
  noAutoTransfusion: boolean;
  noAutoSurgery: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  resources: Record<string, unknown>;
};

type PeriopApiEvidence = {
  surgeryAdapterCreatedThroughRealService: boolean;
  surgeryWebhookCreatedThroughRealService: boolean;
  webhookSignaturePreviewGenerated: boolean;
  surgeryTerminologyActivated: boolean;
  surgerySafetyAssetPromoted: boolean;
  surgeryRiskMatrixCreated: boolean;
  surgeryActionCardPublished: boolean;
  surgeryRuleCreated: boolean;
  runtimeActivatedWithSurgeryAssets: boolean;
  contextSnapshotCreatedFromFrontdesk: boolean;
  outboundChecklistRequested: boolean;
  inboundSurgeryEventAccepted: boolean;
  clinicalEvaluationTriggeredFromFrontdesk: boolean;
  humanRiskConfirmationRecorded: boolean;
  qualityRectificationSubmittedAndReviewed: boolean;
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

const periopSourceSystem = "NURSING_ANESTHESIA_TRANSFUSION_ICU";
const procedureLocalCode = "OR-LAP-APP";
const procedureStandardCode = "47.0901";
const procedureAssetIdentityPrefix = "TERM.SURGERY_ANESTHESIA_TRANSFUSION.PROCEDURE";
const actionCardIdentityPrefix = "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST";
const ruleIdentityPrefix = "RULE.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST";

const requiredStages = {
  S26: [
    "平台管理员访问真实前台并经真实服务创建 NURSING_ANESTHESIA_TRANSFUSION_ICU 适配器、回调通道和签名预览",
    "运营员发布手术操作术语、高危安全红线、麻醉用血风险矩阵、术前核查规则和动作卡资产",
    "当前机构生效版本包含围手术期五类运行资产",
    "签名入站事件生成 Procedure、Observation、Medication、Document 和手麻输血本地扩展上下文",
    "系统向 NURSING_ANESTHESIA_TRANSFUSION_ICU 发出核查确认回传并诚实断连降级",
    "临床用户从真实前台触发 order-sign 推荐评估",
    "推荐卡证明术前核查规则、安全红线和动作卡按当前机构生效版本消费",
    "临床用户人工确认围手术期风险，系统不自动输血、不自动开嘱、不自动手术",
    "围手术期时序质控形成整改任务并由固定职责账号复核关闭",
  ],
} as const;

test.describe("围手术期麻醉输血代表切片真实前台闭环", () => {
  test("临床用户与运营员、平台管理员完成围手术期麻醉输血核查代表闭环", async ({
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;
    const observedStages = new Set<string>();
    const apiEvidence = createApiEvidence();

    await ensureReadySession(page, "engine-operator");
    const hospitalId = await localRehearsalHospitalId(page);
    const riskMatrix = await createPeriopRiskMatrix(page, suffix);
    apiEvidence.surgeryRiskMatrixCreated = true;

    const safetyRedline = await createPromotedPeriopRedline(page, {
      suffix,
      riskMatrix,
    });
    apiEvidence.surgerySafetyAssetPromoted = true;

    const terminologyGate = await createPeriopTerminologyGate(page, suffix);
    apiEvidence.surgeryTerminologyActivated = true;

    const actionCard = await createPeriopActionCard(page, suffix);
    apiEvidence.surgeryActionCardPublished = true;

    const preRuleCandidates = await readPeriopPreRuleRuntimeCandidates(page, hospitalId, {
      safetyIdentity: safetyRedline.assetIdentity,
    });
    const preRuleRuntime = await activateRuntimeWithPeriopPreRuleAssets(page, {
      hospitalId,
      terminology: terminologyGate,
      safety: preRuleCandidates.safety,
      cdssRisk: preRuleCandidates.cdssRisk,
      actionCard,
    });
    const ruleValidationAdapterId = await ensureTemporaryAdapterForRuleValidation(page, suffix);

    const positivePatient = await createPeriopPatientFromFrontdesk(page, `${suffix}-POS`);
    const positiveInbound = await createPeriopContextBySignedInbound(page, {
      suffix: `${suffix}-POS`,
      runtimeReleaseId: preRuleRuntime.releaseId,
      patient: positivePatient,
      adapterId: ruleValidationAdapterId,
    });
    const negativePatient = await createPeriopPatientFromFrontdesk(page, `${suffix}-NEG`);
    const negativeInbound = await createPeriopContextBySignedInbound(page, {
      suffix: `${suffix}-NEG`,
      runtimeReleaseId: preRuleRuntime.releaseId,
      patient: negativePatient,
      adapterId: ruleValidationAdapterId,
      positive: false,
    });

    const rule = await createAndPublishPeriopRule(page, suffix, {
      positiveContextSnapshotId: positiveInbound.snapshot.snapshotId,
      negativeContextSnapshotId: negativeInbound.snapshot.snapshotId,
      actionCard,
    });
    apiEvidence.surgeryRuleCreated = true;
    recordStage(
      observedStages,
      "运营员发布手术操作术语、高危安全红线、麻醉用血风险矩阵、术前核查规则和动作卡资产",
    );

    const candidates = await readPeriopRuntimeCandidates(page, hospitalId, {
      safetyIdentity: safetyRedline.assetIdentity,
      ruleIdentity: rule.assetIdentity,
    });
    const safetyEvidence = {
      ...safetyRedline,
      versionId: candidates.safety.versionId,
      versionNo: candidates.safety.versionNo,
      contentHash: candidates.safety.contentHash,
    };
    const riskMatrixEvidence = {
      ...riskMatrix,
      versionId: candidates.cdssRisk.versionId,
      versionNo: candidates.cdssRisk.versionNo,
      contentHash: candidates.cdssRisk.contentHash,
    };
    const ruleEvidence = {
      ...rule,
      versionId: candidates.rule.versionId,
      versionNo: candidates.rule.versionNo,
      contentHash: candidates.rule.contentHash,
    };
    const runtime = await activateRuntimeWithPeriopAssets(page, {
      hospitalId,
      terminology: terminologyGate,
      safety: candidates.safety,
      cdssRisk: candidates.cdssRisk,
      rule: candidates.rule,
      actionCard,
    });
    apiEvidence.runtimeActivatedWithSurgeryAssets = true;
    recordStage(observedStages, "当前机构生效版本包含围手术期五类运行资产");

    await ensureReadySession(page, "platform-admin");
    const adapter = await createPeriopAdapter(page, suffix);
    apiEvidence.surgeryAdapterCreatedThroughRealService = true;
    const webhook = await createPeriopWebhook(page, suffix);
    apiEvidence.surgeryWebhookCreatedThroughRealService = true;
    await generatePeriopSignaturePreview(page, webhook.webhookId);
    apiEvidence.webhookSignaturePreviewGenerated = true;
    recordStage(
      observedStages,
      "平台管理员访问真实前台并经真实服务创建 NURSING_ANESTHESIA_TRANSFUSION_ICU 适配器、回调通道和签名预览",
    );

    const patient = await createPeriopPatientFromFrontdesk(page, suffix);
    const inbound = await postSignedPeriopInbound(page, {
      suffix,
      adapterId: adapter.adapterId,
      webhookId: webhook.webhookId,
      webhookSecret: webhook.sharedSecret,
      patient,
      runtimeReleaseId: runtime.releaseId,
    });
    apiEvidence.inboundSurgeryEventAccepted = true;
    const snapshot = await readLatestContextForPatient(page, patient.patientId);
    expect(snapshot.runtimeReleaseId, "S26 入站上下文必须绑定本轮 runtime").toBe(runtime.releaseId);
    assertSnapshotContainsPeriopFacts(snapshot.resources);
    inbound.contextSnapshotId = snapshot.snapshotId;
    apiEvidence.contextSnapshotCreatedFromFrontdesk = true;
    recordStage(
      observedStages,
      "签名入站事件生成 Procedure、Observation、Medication、Document 和手麻输血本地扩展上下文",
    );

    const outbound = await sendPeriopOutbound(page, {
      suffix,
      adapterId: adapter.adapterId,
      snapshot,
      traceId: inbound.traceId,
    });
    apiEvidence.outboundChecklistRequested = true;
    recordStage(
      observedStages,
      "系统向 NURSING_ANESTHESIA_TRANSFUSION_ICU 发出核查确认回传并诚实断连降级",
    );

    const recommendation = await triggerPeriopRecommendationFromFrontdesk(page, {
      snapshot,
      runtime,
      rule: ruleEvidence,
    });
    apiEvidence.clinicalEvaluationTriggeredFromFrontdesk = true;
    recordStage(observedStages, "临床用户从真实前台触发 order-sign 推荐评估");
    recordStage(observedStages, "推荐卡证明术前核查规则、安全红线和动作卡按当前机构生效版本消费");

    const manualConfirmation = await completePeriopManualConfirmation(page, {
      cardId: recommendation.cardId,
      actionCard,
      actionCardAsset: runtime.actionCardAsset,
    });
    expect(manualConfirmation.cardStatus, "临床人工确认后推荐卡应为 ACCEPTED").toBe("ACCEPTED");
    apiEvidence.humanRiskConfirmationRecorded = true;
    recordStage(
      observedStages,
      "临床用户人工确认围手术期风险，系统不自动输血、不自动开嘱、不自动手术",
    );

    const qualityRectification = await createAndClosePeriopRectification(page, {
      suffix,
      recommendation,
      snapshot,
      runtimeReleaseId: runtime.releaseId,
    });
    apiEvidence.qualityRectificationSubmittedAndReviewed = true;
    recordStage(observedStages, "围手术期时序质控形成整改任务并由固定职责账号复核关闭");

    await attachPeriopEvidence(testInfo, {
      apiEvidence,
      adapter,
      webhookSignature: {
        webhookId: webhook.webhookId,
        adapterId: adapter.adapterId,
        signatureAlgorithm: "HMAC-SHA256",
        canonicalPayloadIncludesTraceId: true,
        previewGenerated: true,
      },
      terminologyGate,
      safetyRedline: safetyEvidence,
      riskMatrix: riskMatrixEvidence,
      actionCard: {
        ...actionCard,
        versionId: runtime.actionCardAsset.versionId ?? actionCard.versionId,
        versionNo: runtime.actionCardAsset.versionNo ?? actionCard.versionNo,
        contentHash: runtime.actionCardAsset.contentHash ?? actionCard.contentHash,
      },
      rule: ruleEvidence,
      runtime,
      activationRequest: runtime.activationRequest,
      clinicalContext: {
        patientId: snapshot.patientId,
        encounterId: snapshot.encounterId,
        contextSnapshotId: snapshot.snapshotId,
        runtimeReleaseId: snapshot.runtimeReleaseId,
        resources: snapshot.resources,
      },
      outboundChecklist: outbound,
      inboundSurgeryEvent: inbound,
      clinicalTrigger: {
        triggerId: recommendation.triggerId,
        contextSnapshotId: snapshot.snapshotId,
        runtimeReleaseId: runtime.releaseId,
        triggerType: "order-sign",
        cardId: recommendation.cardId,
        relatedCardIds: recommendation.relatedCardIds,
      },
      recommendation,
      manualConfirmation,
      qualityRectification,
      observedStages,
    });
  });
});

function createApiEvidence(): PeriopApiEvidence {
  return {
    surgeryAdapterCreatedThroughRealService: false,
    surgeryWebhookCreatedThroughRealService: false,
    webhookSignaturePreviewGenerated: false,
    surgeryTerminologyActivated: false,
    surgerySafetyAssetPromoted: false,
    surgeryRiskMatrixCreated: false,
    surgeryActionCardPublished: false,
    surgeryRuleCreated: false,
    runtimeActivatedWithSurgeryAssets: false,
    contextSnapshotCreatedFromFrontdesk: false,
    outboundChecklistRequested: false,
    inboundSurgeryEventAccepted: false,
    clinicalEvaluationTriggeredFromFrontdesk: false,
    humanRiskConfirmationRecorded: false,
    qualityRectificationSubmittedAndReviewed: false,
  };
}

async function createPeriopRiskMatrix(page: Page, suffix: string) {
  const matrixVersion = `surgery-anesthesia-transfusion-${suffix}`;
  const response = await putApi(page, "/engine/cdss/risk-matrix", {
    matrixVersion,
    changeReason: "S26 围手术期麻醉输血代表切片：高危核查只提示并要求医师确认。",
    status: "ACTIVE",
    entries: [
      {
        triggerPoint: "order-sign",
        severityLevel: "CRITICAL",
        automationLevel: "INFORM_ONLY",
        riskLevel: "CRITICAL",
        reviewRequirement: "PHYSICIAN_CONFIRMATION",
        silentRunHours: 168,
        releaseGate: "S26_SURGERY_ANESTHESIA_TRANSFUSION",
        autoExecutionAllowed: false,
        samdClassification: "NMPA_RESERVED",
        regulatoryEvidence: "NOT_ASSESSED",
        explanation: "围手术期、麻醉与输血风险只生成需人工确认的建议，不自动开嘱、手术或输血。",
      },
    ],
  });
  await expectOk(response, "创建围手术期 CDSS_RISK 风险矩阵");
  const rule = arrayField(await responseData(response), "rules").find(
    (item) =>
      textField(item, "triggerPoint") === "order-sign" &&
      textField(item, "reviewRequirement") === "PHYSICIAN_CONFIRMATION" &&
      booleanField(item, "autoExecutionAllowed") === false,
  );
  return {
    assetType: "CDSS_RISK" as const,
    assetIdentity: "CDSS.RISK.MATRIX",
    matrixId: requireText(textField(rule, "matrixId"), "风险矩阵响应必须返回 matrixId"),
    matrixVersion: requireText(
      textField(rule, "matrixVersion"),
      "风险矩阵响应必须返回 matrixVersion",
    ),
    triggerPoint: requireText(textField(rule, "triggerPoint"), "风险矩阵必须返回触发点"),
    riskLevel: requireText(textField(rule, "riskLevel"), "风险矩阵必须返回风险等级"),
    reviewRequirement: requireText(
      textField(rule, "reviewRequirement"),
      "风险矩阵必须返回复核要求",
    ),
    automationLevel: requireText(textField(rule, "automationLevel"), "风险矩阵必须返回自动化等级"),
    autoExecutionAllowed: booleanField(rule, "autoExecutionAllowed"),
  };
}

async function createPromotedPeriopRedline(
  page: Page,
  options: { suffix: string; riskMatrix: { matrixId: string; matrixVersion: string } },
) {
  const redlineId = `redline-surgery-${options.suffix.toLowerCase()}`;
  const redlineKey = `RDL-SURGERY-${options.suffix}`;
  const redlineVersion = "2026.1";
  const conditionDsl = JSON.stringify({
    all: [
      { fact: "procedures[].code", operator: "contains", value: procedureStandardCode },
      { fact: "observations[].valueString", operator: "equals", value: "III" },
    ],
  });
  const draft = await postApi(page, "/engine/safety/redlines", {
    redlineId,
    category: "SURGERY_ANESTHESIA_TRANSFUSION",
    triggerPoint: "order-sign",
    scopeType: "TENANT",
    scopeRef: resolvedTenantIdFor("engine-operator"),
    redlineKey,
    redlineVersion,
    hazardSeverity: "CRITICAL",
    riskMatrixId: options.riskMatrix.matrixId,
    riskMatrixVersion: options.riskMatrix.matrixVersion,
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    silentRunHours: 168,
    releaseGate: "S26_SURGERY_ANESTHESIA_TRANSFUSION",
    title: `围手术期麻醉输血高危核查 ${options.suffix}`,
    clinicalHazard:
      "术前核查、麻醉风险和用血确认必须由临床人工确认；系统不得自动手术、输血、开嘱或控制设备。",
    conditionDsl,
    evidenceSource: "S26 围手术期麻醉输血代表切片演练证据",
    evidenceReference: "evidence://local-e2e/surgery-anesthesia-transfusion/redline",
    sourceVersionId: null,
    lowerTenantOverrideAllowed: false,
  });
  await expectOk(draft, "创建围手术期 SAFETY 红线草稿");
  const dryRun = await postApi(page, "/engine/safety/redlines:dry-run", {
    redlineId,
    observedFrom: "2026-05-26T00:00:00Z",
    observedTo: "2026-06-03T00:00:00Z",
    evaluatedCaseCount: 1200,
    matchedCaseCount: 18,
    falsePositiveCaseCount: 1,
    safetyIncidentCount: 0,
    evidenceReference: "evidence://local-e2e/surgery-anesthesia-transfusion/redline/silent-run",
    operatorNote: "S26 围手术期代表切片：静默试运行达标，不自动输血、不自动手术。",
  });
  await expectOk(dryRun, "提交围手术期 SAFETY 静默试运行");
  const trialId = requireText(
    textField(await responseData(dryRun), "trialId"),
    "静默试运行必须返回 trialId",
  );
  const promoted = await postApi(page, "/engine/safety/redlines:promote", {
    redlineId,
    trialId,
    expectedRedlineVersion: redlineVersion,
    promotionReason: "S26 围手术期代表切片：静默试运行达标后纳入 SAFETY 资产候选。",
  });
  await expectOk(promoted, "上线围手术期 SAFETY 资产");
  const promotedData = await responseData(promoted);
  return {
    assetType: "SAFETY" as const,
    assetIdentity: `SAFETY.${redlineKey}`,
    redlineId,
    redlineKey,
    redlineVersion,
    category: "SURGERY_ANESTHESIA_TRANSFUSION",
    conditionDsl,
    trialId,
    hazardSeverity: requireText(
      textField(promotedData, "hazardSeverity"),
      "红线上线响应必须返回严重度",
    ),
    reviewRequirement: requireText(
      textField(promotedData, "reviewRequirement"),
      "红线上线响应必须返回复核要求",
    ),
    noAutoTransfusion: true,
    noAutoSurgery: true,
  };
}

async function createPeriopTerminologyGate(
  page: Page,
  suffix: string,
): Promise<
  PeriopAssetCandidate & {
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
  }
> {
  const standard = await postApi(page, "/engine/terminology/terms/standard", {
    ...apiContext(suffix, "term-standard"),
    standardSystem: "ICD-9-CM-3",
    termCode: procedureStandardCode,
    category: "PROCEDURE",
    displayName: "腹腔镜阑尾切除术",
    normalizedName: "腹腔镜阑尾切除术|47.0901",
    versionNo: "2026",
    sourceVersionId: null,
    evidenceText: "S26 围手术期代表切片：手术操作规则发布门禁所需 ICD-9-CM-3 术语。",
  });
  await expectOk(standard, "登记手术操作标准术语");
  const standardTermId = numberField(await responseData(standard), "id");
  expect(standardTermId, "手术操作标准术语必须返回 id").toBeTruthy();
  const local = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-local"),
    sourceSystem: periopSourceSystem,
    localCode: procedureLocalCode,
    category: "PROCEDURE",
    localName: "手术室腹腔镜阑尾切除",
    normalizedName: "手术室腹腔镜阑尾切除|OR-LAP-APP|47.0901|腹腔镜阑尾切除术",
    local_department_id: null,
  });
  await expectOk(local, "登记手麻手术室输血系统手术操作院内术语");
  const localTermId = numberField(await responseData(local), "id");
  expect(localTermId, "手麻手术室输血系统手术操作院内术语必须返回 id").toBeTruthy();
  const mapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem: periopSourceSystem,
    localCode: procedureLocalCode,
    localTermId,
    standardTermId,
    category: "PROCEDURE",
  });
  const assetIdentity = `${procedureAssetIdentityPrefix}.${suffix}`;
  const draft = await postApi(page, "/engine/terminology/assets/drafts", {
    assetIdentity,
    name: `围手术期手术操作术语映射 ${suffix}`,
    scopeLevel: "TENANT",
    scopeCode: resolvedTenantIdFor("engine-operator"),
  });
  await expectOk(draft, "生成围手术期术语资产草稿");
  const data = await responseData(draft);
  return {
    assetType: "TERMINOLOGY" as const,
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "术语资产必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "术语资产必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "术语资产必须返回 contentHash"),
    standardSystem: "ICD-9-CM-3",
    standardCode: procedureStandardCode,
    localCode: procedureLocalCode,
    localTermId: Number(localTermId),
    standardTermId: Number(standardTermId),
    sourceSystem: periopSourceSystem,
    category: "PROCEDURE",
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
    category: "PROCEDURE";
  },
) {
  const existing = await getApi(
    page,
    `/engine/terminology/mappings?category=${encodeURIComponent(options.category)}&status=CONFIRMED&page=1&size=100`,
  );
  await expectOk(existing, "读取已确认手术操作术语映射");
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
  await expectOk(generation, "生成手术操作术语映射候选");
  const jobCode = requireText(
    textField(await responseData(generation), "jobCode"),
    "术语候选任务必须返回 jobCode",
  );
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
      reviewNote: "S26 代表切片：确认手术室 OR-LAP-APP 到 ICD-9-CM-3:47.0901。",
      evidenceOverride: "围手术期规则发布前置手术操作术语覆盖门禁。",
    },
  );
  await expectOk(confirmed, "确认手术操作术语映射");
  const confirmedData = await responseData(confirmed);
  const mapping = confirmedMappingEvidence(confirmedData);
  expect(mapping.localTermId, "确认映射必须绑定本轮 localTermId").toBe(options.localTermId);
  expect(mapping.standardTermId, "确认映射必须绑定本轮 standardTermId").toBe(
    options.standardTermId,
  );
  expect(mapping.sourceSystem, "确认映射必须绑定本轮 sourceSystem").toBe(options.sourceSystem);
  return mapping;
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
  let lastEvidence = "";
  while (Date.now() < deadline) {
    const job = await getApi(
      page,
      `/engine/terminology/mappings/candidate-generation-jobs/${encodeURIComponent(jobCode)}`,
    );
    await expectOk(job, "读取手术操作术语候选生成任务");
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
    await expectOk(response, "读取手术操作术语映射候选");
    const candidate = pageItems(await responseData(response)).find((item) => {
      const evidence = String(textField(item, "evidenceText") ?? "");
      if (evidence) lastEvidence = evidence;
      return (
        textField(item, "generationJobCode") === jobCode &&
        numberField(item, "localTermId") === expected.localTermId &&
        numberField(item, "standardTermId") === expected.standardTermId
      );
    });
    if (candidate) return candidate;
    if (lastStatus === "FAILED") {
      throw new Error(`手术操作术语候选生成失败 ${jobCode}`);
    }
    if (lastStatus === "SUCCEEDED" && generatedCount === 0) {
      throw new Error(`手术操作术语候选生成成功但没有候选 ${jobCode}`);
    }
    await waitForPollingInterval(250);
  }
  throw new Error(
    `手术操作术语候选生成超时 ${jobCode}，最后状态 ${lastStatus}，候选数 ${generatedCount ?? "UNKNOWN"}，最后证据 ${lastEvidence}`,
  );
}

async function createPeriopActionCard(
  page: Page,
  suffix: string,
): Promise<PeriopActionCardCandidate> {
  const assetIdentity = `${actionCardIdentityPrefix}.${suffix}`;
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity,
    applicableScope: "ALL",
    sourceRef: "S26 围手术期麻醉输血代表切片：术前核查提示卡，不自动开嘱、不自动手术、不自动输血。",
    content: {
      schemaVersion: "1.0",
      title: `围手术期麻醉输血核查提示卡 ${suffix}`,
      actionCode: "STRONG_REMINDER",
      atSeverity: "HIGH",
      indicator: "critical",
      summary: "围手术期、麻醉与用血风险需人工确认。",
      detail:
        "提示卡只进入临床人工确认链路，不替代手麻、手术室、输血系统，不自动开嘱、手术或输血。",
      source: { label: "MedKernel S26 本地上线演练" },
      suggestions: [
        { label: "核查围手术期风险", actionType: "OPEN_FORM", payload: { target: "S26" } },
      ],
      overrideReasons: ["临床团队已完成人工核查与责任确认"],
      requiresPhysicianConfirmation: true,
      noAutoOrder: true,
      noAutoTransfusion: true,
      noAutoSurgery: true,
    },
  });
  await expectOk(response, "创建围手术期 ACTION_CARD 资产");
  const data = await responseData(response);
  return {
    assetType: "ACTION_CARD",
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "ACTION_CARD 必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "ACTION_CARD 必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "ACTION_CARD 必须返回 contentHash"),
    requiresPhysicianConfirmation: true,
    noAutoOrder: true,
    noAutoTransfusion: true,
    noAutoSurgery: true,
  };
}

async function createAndPublishPeriopRule(
  page: Page,
  suffix: string,
  options: {
    positiveContextSnapshotId: string;
    negativeContextSnapshotId: string;
    actionCard: { assetIdentity: string };
  },
) {
  await ensureReadySession(page, "engine-operator");
  const ruleCode = `${ruleIdentityPrefix}.${suffix}`;
  const create = await postApi(page, "/engine/rule/rules", {
    ...apiContext(suffix, "rule-create"),
    triggers: [
      {
        trigger_point: "order-sign",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "orders"],
      },
    ],
    ruleCode,
    name: `围手术期麻醉输血代表切片规则 ${suffix}`,
    ruleType: "ORDER",
    authoringMode: "DSL",
    riskLevel: "HIGH",
    priority: 970,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:surgery-anesthesia-transfusion",
    changeSummary:
      "S26 代表切片：规则引用 Procedure、Observation、extensions.local.transfusionRequest 和 ACTION_CARD。",
    dsl: {
      applicability: {
        population: {},
        orgScope: {},
        settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
        effective: { rolloutPercent: 100 },
      },
      when: {
        all: [
          { fact: "procedures[].code", operator: "contains", value: procedureStandardCode },
          { fact: "observations[].valueString", operator: "equals", value: "III" },
          {
            fact: "extensions.local.transfusionRequest.noAutoTransfusion",
            operator: "equals",
            value: true,
          },
        ],
      },
      then: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "围手术期麻醉输血风险需人工确认",
          detail:
            "术前安全核查、麻醉风险和用血确认必须由临床人工确认；系统不自动开嘱、手术或输血。",
          source: { label: "S26 围手术期代表切片" },
          actionCardRef: options.actionCard.assetIdentity,
          suggestions: [],
          overrideReasons: ["临床团队已完成人工核查与责任确认"],
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        title: "围手术期麻醉输血代表切片规则",
        reason: "Procedure、Observation 和输血本地扩展均来自当前临床上下文。",
        sourceRef: "local-e2e:surgery-anesthesia-transfusion",
      },
    },
    explanation: {
      title: "围手术期麻醉输血代表切片规则",
      summary: "证明围手术期规则和提示卡进入当前机构生效版本。",
    },
    parameterBindings: {},
  });
  await expectOk(create, "创建围手术期 RULE 资产");
  const created = await responseData(create);
  const ruleId = requireText(textField(created, "ruleId"), "规则创建响应必须返回 ruleId");
  for (const testCase of [
    {
      caseType: "POSITIVE",
      expectedHit: true,
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      contextSnapshotId: options.positiveContextSnapshotId,
    },
    {
      caseType: "NEGATIVE",
      expectedHit: false,
      expectedSeverity: null,
      expectedActionCode: null,
      contextSnapshotId: options.negativeContextSnapshotId,
    },
    {
      caseType: "BOUNDARY",
      expectedHit: true,
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      contextSnapshotId: options.positiveContextSnapshotId,
    },
    {
      caseType: "CONFLICT",
      expectedHit: true,
      expectedSeverity: "HIGH",
      expectedActionCode: "STRONG_REMINDER",
      contextSnapshotId: options.positiveContextSnapshotId,
    },
  ]) {
    const response = await postApi(
      page,
      `/engine/rule/rules/${encodeURIComponent(ruleId)}/test-cases`,
      {
        ...apiContext(ruleId, `rule-test-${testCase.caseType}`),
        ...testCase,
      },
    );
    await expectOk(response, `新增围手术期规则发布验证用例 ${testCase.caseType}`);
  }
  const testRun = await postApi(
    page,
    `/engine/rule/rules/${encodeURIComponent(ruleId)}/test`,
    apiContext(ruleId, "rule-test-run"),
  );
  await expectOk(testRun, "执行围手术期规则发布验证用例");
  expect(
    booleanField(await responseData(testRun), "allPassed"),
    "规则发布验证用例必须全部通过",
  ).toBe(true);
  for (const targetState of ["REVIEWED", "SHADOW", "CANARY", "FULL"]) {
    const impact = await getApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/impact`);
    await expectOk(impact, `读取围手术期规则 ${targetState} 影响摘要`);
    const transition = await postApi(
      page,
      `/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions`,
      {
        ...apiContext(ruleId, `rule-governance-${targetState}`),
        targetState,
        impactDigest: requireText(
          textField(await responseData(impact), "impactDigest"),
          "规则影响摘要必须返回 digest",
        ),
        reason: `S26 围手术期麻醉输血规则推进至 ${targetState}`,
        publishEvidence: {
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            summary: `S26 围手术期规则 ${targetState} 推进质量门已通过`,
          },
        },
      },
    );
    await expectOk(transition, `围手术期规则治理推进至 ${targetState}`);
  }
  return {
    assetType: "RULE" as const,
    assetIdentity: ruleCode,
    ruleId,
    ruleVersionId: requireText(
      textField(created, "versionId"),
      "规则创建响应必须返回规则 versionId",
    ),
  };
}

async function readPeriopPreRuleRuntimeCandidates(
  page: Page,
  hospitalId: string,
  identities: { safetyIdentity: string },
) {
  const [safety, cdssRisk] = await Promise.all([
    readHospitalRuntimeCandidate(page, hospitalId, "SAFETY", identities.safetyIdentity),
    readHospitalRuntimeCandidate(page, hospitalId, "CDSS_RISK", "CDSS.RISK.MATRIX"),
  ]);
  return { safety, cdssRisk };
}

async function readPeriopRuntimeCandidates(
  page: Page,
  hospitalId: string,
  identities: { safetyIdentity: string; ruleIdentity: string },
) {
  const [safety, cdssRisk, rule] = await Promise.all([
    readHospitalRuntimeCandidate(page, hospitalId, "SAFETY", identities.safetyIdentity),
    readHospitalRuntimeCandidate(page, hospitalId, "CDSS_RISK", "CDSS.RISK.MATRIX"),
    readHospitalRuntimeCandidate(page, hospitalId, "RULE", identities.ruleIdentity),
  ]);
  return { safety, cdssRisk, rule };
}

async function readHospitalRuntimeCandidate(
  page: Page,
  hospitalId: string,
  assetType: PeriopAssetCandidate["assetType"],
  assetIdentity: string,
): Promise<PeriopAssetCandidate> {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-candidates?assetType=${assetType}&keyword=${encodeURIComponent(assetIdentity)}&page=1&size=20`,
  );
  await expectOk(response, `读取本轮 ${assetType} runtime 候选`);
  const candidate = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "assetType") === assetType &&
      textField(item, "assetIdentity") === assetIdentity &&
      textField(item, "status") === "PUBLISHED",
  );
  return {
    assetType,
    assetIdentity,
    versionId: requireText(
      textField(candidate, "versionId"),
      `${assetType} 候选必须返回 versionId`,
    ),
    versionNo: requireText(
      textField(candidate, "versionNo"),
      `${assetType} 候选必须返回 versionNo`,
    ),
    contentHash: requireText(
      textField(candidate, "contentHash"),
      `${assetType} 候选必须返回 contentHash`,
    ),
  };
}

async function activateRuntimeWithPeriopPreRuleAssets(
  page: Page,
  options: {
    hospitalId: string;
    terminology: PeriopAssetCandidate;
    safety: PeriopAssetCandidate;
    cdssRisk: PeriopAssetCandidate;
    actionCard: PeriopAssetCandidate;
  },
) {
  const { releaseId } = await activateRuntimeRelease(page, {
    hospitalId: options.hospitalId,
    assets: [options.terminology, options.safety, options.cdssRisk, options.actionCard],
    label: "激活规则发布验证所需围手术期预备 runtime",
  });
  return { releaseId };
}

async function activateRuntimeWithPeriopAssets(
  page: Page,
  options: {
    hospitalId: string;
    terminology: PeriopAssetCandidate;
    safety: PeriopAssetCandidate;
    cdssRisk: PeriopAssetCandidate;
    rule: PeriopAssetCandidate;
    actionCard: PeriopAssetCandidate;
  },
) {
  const { releaseId, activationRequest } = await activateRuntimeRelease(page, {
    hospitalId: options.hospitalId,
    assets: [
      options.terminology,
      options.safety,
      options.cdssRisk,
      options.rule,
      options.actionCard,
    ],
    label: "激活包含围手术期资产的医院生效版本",
    returnRequest: true,
  });
  const currentAfter = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfter, "回读围手术期医院生效版本");
  const detail = (await responseData(currentAfter)) as RuntimeReleaseDetail;
  expect(textFieldAtPath(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(
    releaseId,
  );
  return {
    releaseId,
    revisionNo: numberFieldAtPath(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textFieldAtPath(detail, "release.manifestSha256"),
      "机构生效版本必须返回 manifestSha256",
    ),
    assets: detail.items ?? [],
    terminologyAsset: assertRuntimeContainsAsset(detail, options.terminology),
    safetyAsset: assertRuntimeContainsAsset(detail, options.safety),
    cdssRiskAsset: assertRuntimeContainsAsset(detail, options.cdssRisk),
    ruleAsset: assertRuntimeContainsAsset(detail, options.rule),
    actionCardAsset: assertRuntimeContainsAsset(detail, options.actionCard),
    activationRequest,
  };
}

async function activateRuntimeRelease(
  page: Page,
  options: {
    hospitalId: string;
    assets: PeriopAssetCandidate[];
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
        (asset) =>
          asset.assetType === required.assetType && asset.assetIdentity === required.assetIdentity,
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
  const releaseId = requireText(
    textField(await responseData(activated), "releaseId"),
    "激活必须返回 releaseId",
  );
  return options.returnRequest
    ? { releaseId, activationRequest }
    : { releaseId, activationRequest: undefined };
}

async function createPeriopPatientFromFrontdesk(page: Page, suffix: string) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const dialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(dialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `术*${idLast4.slice(-1)}`;
  await dialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = dialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await dialog.getByRole("spinbutton", { name: "年龄" }).fill("58");
  await dialog.getByLabel("身份证后四位").fill(idLast4);
  const responsePromise = waitForPost(page, "/engine/mpi/patients");
  await dialog.getByRole("button", { name: "保存患者" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "创建围手术期演练脱敏患者");
  const patientId = requireText(
    textField(await responseData(response), "mpiId"),
    "患者创建响应必须返回 MPI",
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    encounterId: `enc-surgery-${suffix.toLowerCase()}`,
  };
}

async function ensureTemporaryAdapterForRuleValidation(page: Page, suffix: string) {
  await ensureReadySession(page, "platform-admin");
  const adapterId = `surgery-rule-validation-${suffix.toLowerCase()}`;
  const response = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `S26 规则验证临时适配器 ${suffix}`,
    protocolType: "Webhook",
    configJson: JSON.stringify(periopAdapterConfig()),
  });
  await expectOk(response, "创建 S26 规则验证临时适配器");
  return adapterId;
}

async function createPeriopContextBySignedInbound(
  page: Page,
  options: {
    suffix: string;
    runtimeReleaseId: string;
    patient: { patientId: string; encounterId: string };
    adapterId: string;
    positive?: boolean;
  },
) {
  await ensureReadySession(page, "platform-admin");
  const webhook = await createPeriopWebhook(page, `rule-${options.suffix}`);
  const inbound = await postSignedPeriopInbound(page, {
    suffix: options.suffix,
    adapterId: options.adapterId,
    webhookId: webhook.webhookId,
    webhookSecret: webhook.sharedSecret,
    patient: options.patient,
    runtimeReleaseId: options.runtimeReleaseId,
    positive: options.positive ?? true,
  });
  const snapshot = await readLatestContextForPatient(page, options.patient.patientId);
  inbound.contextSnapshotId = snapshot.snapshotId;
  return { inbound, snapshot };
}

async function createPeriopAdapter(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  const adapterId = `surgery-anesthesia-transfusion-${suffix.toLowerCase()}`;
  const config = periopAdapterConfig();
  const response = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `S26 手麻手术室输血适配器 ${suffix}`,
    protocolType: "Webhook",
    configJson: JSON.stringify(config),
  });
  await expectOk(response, "创建 NURSING_ANESTHESIA_TRANSFUSION_ICU 适配器");
  return { adapterId, protocolType: "Webhook", ...config };
}

function periopAdapterConfig() {
  return {
    systemFamilyCode: periopSourceSystem,
    sourceSystem: periopSourceSystem,
    targetSystem: periopSourceSystem,
    baseUrl: "https://surgery-anesthesia-transfusion.example.test",
    healthPath: "/health",
    outboundPath: "/checklist",
    connectTimeoutMs: 800,
    requestTimeoutMs: 1200,
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      {
        sourcePath: "/procedureCode",
        targetPath: "/procedures/0",
        targetDictionaryKey: "ICD-9-CM-3",
        category: "PROCEDURE",
      },
      { sourcePath: "/procedures/0/procedureId", targetPath: "/procedures/0/procedureId" },
      { sourcePath: "/procedures/0/sourceId", targetPath: "/procedures/0/sourceId" },
      { sourcePath: "/procedureName", targetPath: "/procedures/0/displayName" },
      { sourcePath: "/anesthesiaType", targetPath: "/procedures/0/anesthesiaType" },
      { sourcePath: "/surgeonId", targetPath: "/procedures/0/surgeonId" },
      { sourcePath: "/performedAt", targetPath: "/procedures/0/performedAt" },
      { sourcePath: "/asaCode", targetPath: "/observations/0/code" },
      { sourcePath: "/asaClass", targetPath: "/observations/0/valueString" },
      { sourcePath: "/anesthesiaDrugCode", targetPath: "/medications/0/standardCode" },
      { sourcePath: "/anesthesiaDrugName", targetPath: "/medications/0/displayName" },
      { sourcePath: "/checklistType", targetPath: "/documents/0/documentType" },
      { sourcePath: "/checklistDigest", targetPath: "/documents/0/contentDigest" },
      { sourcePath: "/documents/0/documentId", targetPath: "/documents/0/documentId" },
      { sourcePath: "/documents/0/sourceId", targetPath: "/documents/0/sourceId" },
      { sourcePath: "/surgeryPlan/surgeryLevel", targetPath: "/surgeryPlan/surgeryLevel" },
      {
        sourcePath: "/surgeryPlan/preOpAssessmentStatus",
        targetPath: "/surgeryPlan/preOpAssessmentStatus",
      },
      { sourcePath: "/surgeryPlan/timeOutRequired", targetPath: "/surgeryPlan/timeOutRequired" },
      {
        sourcePath: "/anesthesiaAssessment/airwayRisk",
        targetPath: "/anesthesiaAssessment/airwayRisk",
      },
      {
        sourcePath: "/anesthesiaAssessment/anesthesiologistReviewRequired",
        targetPath: "/anesthesiaAssessment/anesthesiologistReviewRequired",
      },
      {
        sourcePath: "/transfusionRequest/crossmatchStatus",
        targetPath: "/transfusionRequest/crossmatchStatus",
      },
      {
        sourcePath: "/transfusionRequest/transfusionConsentConfirmed",
        targetPath: "/transfusionRequest/transfusionConsentConfirmed",
      },
      {
        sourcePath: "/transfusionRequest/noAutoTransfusion",
        targetPath: "/transfusionRequest/noAutoTransfusion",
      },
    ],
  };
}

async function createPeriopWebhook(page: Page, suffix: string) {
  const webhookId = `surgery-anesthesia-transfusion-${suffix.toLowerCase()}`;
  const response = await postApi(page, "/engine/integration/webhooks", {
    webhookId,
    name: `S26 手麻手术室输血回传 ${suffix}`,
    callbackUrl: "https://surgery-anesthesia-transfusion.example.test/medkernel/events",
    eventsSubscribed: "SURGERY_ANESTHESIA_TRANSFUSION_EVENT",
  });
  await expectOk(response, "创建 S26 回调通道");
  const data = await responseData(response);
  return {
    webhookId,
    sharedSecret: requireText(textField(data, "sharedSecret"), "回调通道必须一次性返回共享密钥"),
  };
}

async function generatePeriopSignaturePreview(page: Page, webhookId: string) {
  const response = await postApi(page, "/engine/integration/webhooks/test", {
    webhookId,
    payload: JSON.stringify({
      traceId: `preview-${webhookId}`,
      eventType: "SURGERY_ANESTHESIA_TRANSFUSION_EVENT",
    }),
  });
  await expectOk(response, "生成 S26 回调签名预览");
  const data = await responseData(response);
  expect(textField(data, "signature"), "签名预览必须返回裸 hex 签名").toMatch(/^[0-9a-f]{64}$/i);
}

async function postSignedPeriopInbound(
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
    messageId: `in-surgery-${options.suffix}`,
    traceId: `trace-surgery-${options.suffix}`,
    adapterId: options.adapterId,
    sourceSystem: periopSourceSystem,
    eventType: "ORDER",
    patientId: options.patient.patientId,
    encounterId: options.patient.encounterId,
    clinicalSetting: "INPATIENT",
    triggerPoint: "order-sign",
    occurredAt: "2026-07-07T02:15:00Z",
    payload: {
      patientId: options.patient.patientId,
      procedures: [
        {
          procedureId: `proc-surgery-${options.suffix}`,
          sourceId: `src-procedure-${options.suffix}`,
        },
      ],
      procedureCode: procedureLocalCode,
      procedureName: positive ? "腹腔镜阑尾切除术" : "浅表清创术",
      anesthesiaType: positive ? "GENERAL" : "LOCAL",
      surgeonId: "doctor-surgery-1",
      performedAt: "2026-07-07T02:30:00Z",
      asaCode: "ASA_CLASS",
      asaClass: positive ? "III" : "I",
      anesthesiaDrugCode: "N01AB06",
      anesthesiaDrugName: "七氟烷",
      checklistDigest: `sha256:surgery-safety-checklist-${options.suffix}`,
      checklistType: "SURGERY_SAFETY_CHECKLIST",
      documents: [
        {
          documentId: `doc-checklist-${options.suffix}`,
          sourceId: `src-document-${options.suffix}`,
        },
      ],
      surgeryPlan: {
        surgeryLevel: positive ? "LEVEL_3" : "LEVEL_1",
        preOpAssessmentStatus: positive ? "PASSED_WITH_RISK" : "PASSED",
        timeOutRequired: true,
      },
      anesthesiaAssessment: {
        airwayRisk: positive ? "DIFFICULT_AIRWAY" : "LOW",
        anesthesiologistReviewRequired: positive,
      },
      transfusionRequest: {
        crossmatchStatus: positive ? "MATCHED" : "NOT_REQUIRED",
        transfusionConsentConfirmed: positive,
        noAutoTransfusion: true,
      },
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
  await expectOk(response, "S26 手麻手术室输血签名入站");
  const data = await responseData(response);
  const clinicalEventId = requireText(
    textField(data, "clinicalEventId"),
    "入站必须返回 clinicalEventId",
  );
  const clinicalEvent = await waitForClinicalEventProcessed(
    page,
    clinicalEventId,
    options.runtimeReleaseId,
  );
  return {
    messageId: requireText(textField(data, "messageId"), "入站必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "入站必须返回 traceId"),
    adapterId: options.adapterId,
    webhookId: options.webhookId,
    patientId: options.patient.patientId,
    encounterId: options.patient.encounterId,
    contextSnapshotId: "",
    sourceSystem: periopSourceSystem,
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
    await expectOk(response, "读取 S26 入站临床事件详情");
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
      expect(last.runtimeReleaseId, "S26 入站事件必须绑定本轮 runtime").toBe(runtimeReleaseId);
      expect(last.errorCode, "S26 入站事件处理成功不得有 errorCode").toBeNull();
      return last;
    }
    if (last.status === "FAILED") {
      throw new Error(`S26 入站事件 ${eventId} 处理失败：${last.errorCode ?? "UNKNOWN"}`);
    }
    await waitForPollingInterval(250);
  }
  throw new Error(
    `S26 入站事件 ${eventId} 未处理到 PROCESSED，最后状态：${last?.status ?? "UNKNOWN"}`,
  );
}

async function readLatestContextForPatient(
  page: Page,
  patientId: string,
): Promise<ContextSnapshotSummary> {
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
    patientId: requireText(
      textFieldAtPath(context, "resources.patient.mpi"),
      "上下文详情必须返回 patient.mpi",
    ),
    snapshotId: requireText(textField(context, "snapshotId"), "上下文详情必须返回 snapshotId"),
    runtimeReleaseId: requireText(
      textField(context, "runtimeReleaseId"),
      "上下文详情必须返回 runtimeReleaseId",
    ),
    encounterId: textFieldAtPath(context, "resources.encounters.0.encounterId"),
    resources: recordValue(recordField(context, "resources")) ?? {},
  };
}

function assertSnapshotContainsPeriopFacts(resources: Record<string, unknown>) {
  expect(
    arrayField(resources, "procedures").some(
      (item) =>
        textField(item, "code") === procedureStandardCode &&
        textField(item, "anesthesiaType") === "GENERAL" &&
        Boolean(textField(item, "sourceRecordId")),
    ),
    "上下文必须包含带来源身份的 Procedure 手术操作和麻醉方式",
  ).toBe(true);
  expect(
    arrayField(resources, "observations").some(
      (item) => textField(item, "code") === "ASA_CLASS" && textField(item, "valueString") === "III",
    ),
    "上下文必须包含 ASA 麻醉分级 Observation",
  ).toBe(true);
  expect(
    arrayField(resources, "medications").some(
      (item) =>
        textField(item, "code") === "N01AB06" || textField(item, "standardCode") === "N01AB06",
    ),
    "上下文必须包含麻醉用药 Medication",
  ).toBe(true);
  expect(
    arrayField(resources, "documents").some(
      (item) =>
        textField(item, "documentType") === "SURGERY_SAFETY_CHECKLIST" &&
        Boolean(textField(item, "sourceRecordId")),
    ),
    "上下文必须包含带来源身份的手术安全核查单 Document",
  ).toBe(true);
  expect(
    booleanFieldAtPath(resources, "extensions.local.surgeryPlan.timeOutRequired"),
    "术前核查必须要求 time-out",
  ).toBe(true);
  expect(
    textFieldAtPath(resources, "extensions.local.anesthesiaAssessment.airwayRisk"),
    "麻醉风险扩展必须保留",
  ).toBe("DIFFICULT_AIRWAY");
  expect(
    booleanFieldAtPath(resources, "extensions.local.transfusionRequest.noAutoTransfusion"),
    "输血扩展必须禁止自动输血",
  ).toBe(true);
}

async function sendPeriopOutbound(
  page: Page,
  options: {
    suffix: string;
    adapterId: string;
    snapshot: ContextSnapshotSummary;
    traceId: string;
  },
) {
  await ensureReadySession(page, "platform-admin");
  const response = await postApi(page, "/engine/integration/messages/outbound", {
    messageId: `out-surgery-${options.suffix}`,
    traceId: options.traceId,
    adapterId: options.adapterId,
    targetSystem: periopSourceSystem,
    protocolType: "Webhook",
    payloadSummary: "围手术期麻醉输血安全核查确认回传",
    payload: {
      patientId: options.snapshot.patientId,
      contextSnapshotId: options.snapshot.snapshotId,
      noAutoTransfusion: true,
      noAutoSurgery: true,
    },
    maxRetries: 2,
  });
  await expectOk(response, "登记 S26 安全核查出站请求");
  const data = await responseData(response);
  const status = requireText(textField(data, "status"), "出站请求必须返回状态");
  expect(["NOT_CONNECTED", "RETRYING"].includes(status), "出站不得伪造成功").toBe(true);
  expect(booleanField(data, "blocksMainFlow"), "出站断连不得阻断临床主流程").toBe(false);
  const compensation = await waitForPeriopCompensation(
    page,
    requireText(textField(data, "messageId"), "出站请求必须返回 messageId"),
  );
  return {
    messageId: requireText(textField(data, "messageId"), "出站请求必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "出站请求必须返回 traceId"),
    adapterId: options.adapterId,
    targetSystem: periopSourceSystem,
    protocolType: "Webhook",
    status,
    compensationStatus: requireText(textField(compensation, "status"), "补偿日志必须返回状态"),
    compensationMessageId: requireText(
      textField(compensation, "messageId"),
      "补偿日志必须返回 messageId",
    ),
    blocksMainFlow: booleanField(data, "blocksMainFlow"),
    compensationRequired: textField(compensation, "status") === "NOT_CONNECTED",
    payload: {
      patientId: options.snapshot.patientId,
      contextSnapshotId: options.snapshot.snapshotId,
      noAutoTransfusion: true,
      noAutoSurgery: true,
    },
  };
}

async function waitForPeriopCompensation(page: Page, messageId: string) {
  const deadline = Date.now() + 20_000;
  let lastStatus: string | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, "/engine/integration/logs?page=1&size=50");
    await expectOk(response, "读取 S26 出站补偿日志");
    const log = pageItems(await responseData(response)).find(
      (item) => textField(item, "messageId") === messageId,
    );
    if (log) {
      lastStatus = textField(log, "status") ?? lastStatus;
      if (lastStatus === "NOT_CONNECTED") return log;
      if (lastStatus && lastStatus !== "RETRYING") {
        throw new Error(`S26 补偿日志 ${messageId} 进入非诚实断连状态：${lastStatus}`);
      }
    }
    await waitForPollingInterval(250);
  }
  throw new Error(`S26 补偿日志 ${messageId} 未收敛到 NOT_CONNECTED，最后状态：${lastStatus}`);
}

async function triggerPeriopRecommendationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    rule: PeriopAssetCandidate & { ruleId: string; ruleVersionId: string };
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
  await expect(
    snapshotButton,
    `提醒推荐页必须展示本轮 S26 快照 ${snapshot.snapshotId}`,
  ).toBeVisible({ timeout: 20_000 });
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "签署医嘱");
  const evaluateResponsePromise = waitForEvaluateResponse(page, snapshot);
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  await expectHttpOk(evaluateResponse, "临床用户从真实前台触发 S26 推荐评估");
  const evaluation = await responseData(evaluateResponse);
  const triggerId = requireText(
    textField(evaluation, "triggerId"),
    "推荐评估响应必须返回 triggerId",
  );
  const relatedCardIds = await readRecommendationTriggerDiagnose(page, triggerId);
  const recommendation = await findPeriopRuleCard(page, relatedCardIds, {
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

async function findPeriopRuleCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    triggerId: string;
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    rule: PeriopAssetCandidate & { ruleId: string; ruleVersionId: string };
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
    const detailResponse = await getApi(
      page,
      `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
    );
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
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "procedures[].code" && booleanField(item, "matched") === true,
      ) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "observations[].valueString" &&
          booleanField(item, "matched") === true,
      ) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "extensions.local.transfusionRequest.noAutoTransfusion" &&
          booleanField(item, "matched") === true,
      ) &&
      runtimeAssetEvidence.some(
        (item) =>
          textField(item, "assetType") === "ACTION_CARD" &&
          textField(item, "assetIdentity") === options.runtime.actionCardAsset.assetIdentity &&
          textField(item, "assetVersion") === options.runtime.actionCardAsset.versionNo &&
          textField(item, "contentHash") === options.runtime.actionCardAsset.contentHash,
      );
    if (!matches) continue;
    matched.push({
      cardId,
      cardStatus: textFieldAtPath(detail, "card.status"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      cardType: textFieldAtPath(detail, "card.cardType") ?? "WARNING",
      requiresPhysicianConfirmation: booleanFieldAtPath(
        detail,
        "card.requiresPhysicianConfirmation",
      ),
      aiGenerated: booleanFieldAtPath(detail, "card.aiGenerated"),
      explanation,
    });
  }
  expect(
    matched.map((card) => card.cardId),
    `必须唯一定位本轮 S26 RULE 推荐卡，候选摘要=${JSON.stringify(inspected)}`,
  ).toHaveLength(1);
  return matched[0];
}

async function completePeriopManualConfirmation(
  page: Page,
  options: {
    cardId: string;
    actionCard: PeriopActionCardCandidate;
    actionCardAsset: RuntimeReleaseItem;
  },
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  await page.getByLabel("患者或证据线索").fill(options.cardId);
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const drawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await drawer.getByRole("tab", { name: /医师反馈/u }).click();
  await drawer
    .getByLabel("采纳说明（可选）")
    .fill("临床用户已人工复核围手术期、麻醉与用血风险；系统不自动开嘱、不自动手术、不自动输血。");
  const responsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "确认采纳建议" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "登记 S26 围手术期人工确认");
  const feedback = await responseData(response);
  const detailResponse = await getApi(
    page,
    `/engine/recommendations/cards/${encodeURIComponent(options.cardId)}`,
  );
  await expectOk(detailResponse, "回读 S26 推荐卡反馈详情");
  const detail = await responseData(detailResponse);
  const persisted = arrayField(detail, "feedback").find(
    (item) =>
      textField(item, "feedbackId") === textField(feedback, "feedbackId") &&
      textField(item, "feedbackType") === "ACCEPT" &&
      textField(item, "operatorRole") === "DOCTOR" &&
      textField(item, "reasonCode") === "CONFIRMED",
  );
  expect(persisted, "人工确认反馈必须从推荐详情回读").toBeTruthy();
  const actionCardEvidence = {
    assetType: options.actionCardAsset.assetType,
    assetIdentity: options.actionCardAsset.assetIdentity,
    versionId: options.actionCardAsset.versionId,
    versionNo: options.actionCardAsset.versionNo,
    contentHash: options.actionCardAsset.contentHash,
    entryState: options.actionCardAsset.entryState,
    noAutoOrder: options.actionCard.noAutoOrder,
    noAutoTransfusion: options.actionCard.noAutoTransfusion,
    noAutoSurgery: options.actionCard.noAutoSurgery,
  };
  return {
    feedbackId: textField(feedback, "feedbackId"),
    cardStatus: textField(feedback, "cardStatus"),
    canonicalSessionRole: "clinical-user",
    roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
    persisted,
    noAutoOrder: true,
    noAutoTransfusion: true,
    noAutoSurgery: true,
    actionCardEvidence,
  };
}

async function createAndClosePeriopRectification(
  page: Page,
  options: {
    suffix: string;
    recommendation: { cardId: string };
    snapshot: ContextSnapshotSummary;
    runtimeReleaseId: string;
  },
) {
  await ensureReadySession(page, "engine-operator");
  const departmentId = await localRehearsalQualityDepartmentId(page, options.suffix);
  const indicator = await createActiveEvaluationIndicator(page, options.suffix, departmentId);
  const run = await postApi(page, "/engine/evaluation/runs", {
    runCode: `SURGERY-ANESTHESIA-TRANSFUSION-${options.suffix}`,
    runType: "MANUAL_SAMPLE",
    sourceEventId: options.recommendation.cardId,
    patientId: options.snapshot.patientId,
    encounterId: options.snapshot.encounterId,
    scenarioCode: "S26",
    inputDigest: `surgery-anesthesia-transfusion-${options.suffix}`,
    occurredAt: "2026-07-07T02:45:00Z",
    results: [
      {
        indicatorId: indicator.indicatorId,
        subjectType: "PATIENT",
        subjectRefId: options.snapshot.patientId,
        scoreValue: 72,
        resultLevel: "NON_COMPLIANT",
        hitFlag: true,
        evidenceSummary: "S26 推荐卡提示围手术期、麻醉与用血风险需形成整改闭环。",
        sourceRef: options.recommendation.cardId,
        responsibleDepartmentId: departmentId,
        findings: [
          {
            findingCode: `SURGERY_TIMELINE_${options.suffix}`,
            title: "围手术期时序与用血安全整改代表切片",
            description: "需补充术前 time-out、麻醉困难气道复核和输血人工确认记录。",
            severity: "P1",
            evidenceSummary: "Procedure、ASA Observation 和 transfusionRequest 均提示需人工确认。",
            responsibleDepartmentId: departmentId,
            dueAt: "2026-07-15T08:30:00Z",
          },
        ],
      },
    ],
  });
  await expectOk(run, "创建 S26 围手术期质量问题");
  const issues = await getApi(
    page,
    "/engine/evaluation/issues?severity=P1&status=ASSIGNED&page=1&size=20&sort=createdAt,desc",
  );
  await expectOk(issues, "读取 S26 质量问题");
  const finding = pageItems(await responseData(issues)).find((item) =>
    String(textField(item, "findingCode") ?? "").includes(options.suffix),
  );
  const findingId = requireText(textField(finding, "findingId"), "必须回读本轮 S26 质量问题");
  const detail = await getApi(page, `/engine/evaluation/issues/${encodeURIComponent(findingId)}`);
  await expectOk(detail, "读取 S26 质量问题详情");
  const taskId = requireText(
    textFieldAtPath(await responseData(detail), "rectificationTask.taskId"),
    "质量问题必须自动派发整改任务",
  );
  const submit = await postApi(
    page,
    `/engine/rectifications/${encodeURIComponent(taskId)}/submit`,
    {
      rectificationSummary: "已补充术前 time-out、困难气道复核和输血人工确认记录。",
      evidenceRef: `surgery-anesthesia-transfusion-evidence-${options.suffix}`,
    },
  );
  await expectOk(submit, "提交 S26 整改证据");
  const review = await postApi(
    page,
    `/engine/rectifications/${encodeURIComponent(taskId)}/review`,
    {
      decision: "APPROVED",
      comment: "复核通过，整改证据与本轮 S26 推荐卡一致。",
      evidenceRef: `surgery-anesthesia-transfusion-review-${options.suffix}`,
    },
  );
  await expectOk(review, "复核关闭 S26 整改任务");
  const reviewData = await responseData(review);
  return {
    findingId,
    sourceType: "SURGERY_TIMELINE",
    sourceId: options.recommendation.cardId,
    manualSampleRuntimeReleaseId: options.runtimeReleaseId,
    manualSampleContextSnapshotId: options.snapshot.snapshotId,
    severity: "P1",
    findingStatus: textField(reviewData, "findingStatus"),
    taskId,
    taskStatus: textField(reviewData, "taskStatus"),
    submittedByRole: "engine-operator",
    reviewedByRole: "engine-operator",
    roleEvidence: "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW",
    submittedEvidenceRef: `surgery-anesthesia-transfusion-evidence-${options.suffix}`,
    reviewDecision: "APPROVED",
  };
}

async function createActiveEvaluationIndicator(page: Page, suffix: string, departmentId: string) {
  await ensureReadySession(page, "engine-operator");
  const indicatorCode = `SURGERY_TIMELINE_${suffix}`;
  const created = await postApi(page, "/engine/evaluation/indicators", {
    indicatorCode,
    name: `围手术期时序与用血安全指标 ${suffix}`,
    subjectType: "PATIENT",
    denominatorDefinition: JSON.stringify({
      all: [
        { fact: "procedures[].code", operator: "contains", value: procedureStandardCode },
        {
          fact: "extensions.local.transfusionRequest.noAutoTransfusion",
          operator: "equals",
          value: true,
        },
      ],
    }),
    numeratorDefinition: JSON.stringify({
      all: [{ fact: "rectification.reviewStatus", operator: "equals", value: "APPROVED" }],
    }),
    exclusionDefinition: null,
    scoringDefinition: "命中即需整改",
    timeWindow: "本地上线演练窗口",
    organizationScope: "本地上线演练医院",
    responsibleDepartmentId: departmentId,
    sourceRef: "local-e2e:surgery-anesthesia-transfusion",
  });
  await expectOk(created, "创建 S26 评价指标");
  const indicatorId = requireText(
    textField(await responseData(created), "indicatorId"),
    "评价指标必须返回 indicatorId",
  );
  for (const action of ["submit", "publish", "gray", "activate"]) {
    const response = await postApi(
      page,
      `/engine/evaluation/indicators/${encodeURIComponent(indicatorId)}/${action}`,
      {
        reason: `S26 围手术期指标 ${action}`,
        publishEvidence: {
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            summary: "S26 围手术期代表切片指标质量门已通过",
          },
        },
      },
    );
    await expectOk(response, `评价指标 ${action}`);
  }
  return { indicatorId, indicatorCode };
}

async function attachPeriopEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: PeriopApiEvidence;
    adapter: unknown;
    webhookSignature: unknown;
    terminologyGate: unknown;
    safetyRedline: unknown;
    riskMatrix: unknown;
    actionCard: unknown;
    rule: unknown;
    runtime: {
      releaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      terminologyAsset: RuntimeReleaseItem;
      safetyAsset: RuntimeReleaseItem;
      cdssRiskAsset: RuntimeReleaseItem;
      ruleAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    activationRequest: unknown;
    clinicalContext: unknown;
    outboundChecklist: unknown;
    inboundSurgeryEvent: unknown;
    clinicalTrigger: unknown;
    recommendation: unknown;
    manualConfirmation: unknown;
    qualityRectification: unknown;
    observedStages: Set<string>;
  },
) {
  for (const stages of Object.values(requiredStages)) {
    for (const stage of stages) {
      expect(evidence.observedStages.has(stage), `缺少 S26 阶段：${stage}`).toBe(true);
    }
  }
  await testInfo.attach("surgery-anesthesia-transfusion-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S26"],
        productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
        versionedAssets: ["TERMINOLOGY", "SAFETY", "CDSS_RISK", "RULE", "ACTION_CARD"],
        deliveryShapes: ["API_EVENT"],
        serviceCombinations: [
          "THIRD_PARTY_INTERFACE",
          "CLINICAL_RUNTIME",
          "PROFESSIONAL_COLLABORATION",
          "QUALITY_IMPROVEMENT",
        ],
        scopeStatement:
          "围手术期、麻醉与输血代表切片：NURSING_ANESTHESIA_TRANSFUSION_ICU 入站、术前核查、麻醉风险、用血确认、人工确认和时序质控整改闭环，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收。",
        standardPatientResourceConsumerMatrix: standardPatientResourceConsumerMatrix([
          {
            resourceType: "Procedure",
            resourcePath: "clinicalContext.resources.procedures[0]",
            sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
            sourceIdPath: "clinicalContext.resources.procedures[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "SURGERY_ANESTHESIA_TRANSFUSION_RULE",
            consumerEvidencePaths: [
              "recommendation.explanation.ruleExplanation.conditionEvidence[0]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["qualityRectification.findingId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
          {
            resourceType: "Document",
            resourcePath: "clinicalContext.resources.documents[0]",
            sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
            sourceIdPath: "clinicalContext.resources.documents[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "SURGERY_ANESTHESIA_TRANSFUSION_RULE",
            consumerEvidencePaths: ["inboundSurgeryEvent.mappedPayload.documents[0]"],
            consumerVerified: true,
            auditEvidencePaths: ["manualConfirmation.persisted.feedbackId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
        ]),
        apiEvidence: evidence.apiEvidence,
        adapter: evidence.adapter,
        webhookSignature: evidence.webhookSignature,
        terminologyGate: evidence.terminologyGate,
        safetyRedline: evidence.safetyRedline,
        riskMatrix: evidence.riskMatrix,
        actionCard: evidence.actionCard,
        ruleAsset: evidence.rule,
        runtime: evidence.runtime,
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        outboundChecklist: evidence.outboundChecklist,
        inboundSurgeryEvent: evidence.inboundSurgeryEvent,
        clinicalTrigger: evidence.clinicalTrigger,
        recommendation: evidence.recommendation,
        manualConfirmation: evidence.manualConfirmation,
        qualityRectification: evidence.qualityRectification,
        scenarioConditionEvidence: [
          {
            code: "S26__HIGH_RISK",
            scenarioCode: "S26",
            condition: "HIGH_RISK",
            source: "SURGERY_ANESTHESIA_TRANSFUSION_CRITICAL_MANUAL_CONFIRMATION",
            evidence: [
              "SAFETY 红线和风险矩阵均为 CRITICAL",
              "围手术期困难气道、ASA III 和用血风险进入推荐卡",
              "医生人工确认且系统不自动开嘱、不自动输血、不自动手术",
            ],
          },
          {
            code: "S26__DEGRADATION",
            scenarioCode: "S26",
            condition: "DEGRADATION",
            source: "SURGERY_ANESTHESIA_TRANSFUSION_OUTBOUND_NOT_CONNECTED",
            evidence: [
              "外部手麻手术室输血核查回传收敛到 NOT_CONNECTED",
              "断连补偿不阻断本地推荐和人工确认主链路",
            ],
          },
          {
            code: "S26__ABNORMAL",
            scenarioCode: "S26",
            condition: "ABNORMAL",
            source: "SURGERY_TIMELINE_RECTIFICATION_REVIEW",
            evidence: ["围手术期时序质控形成 P1 整改任务", "固定职责账号提交并复核关闭整改"],
          },
        ],
        scenarioEvidence: [{ code: "S26", observedStages: requiredStages.S26 }],
      },
      null,
      2,
    ),
  });
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
          payload.triggerType === "order-sign"
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
  const response = await getApi(
    page,
    "/engine/org/org-units?keyword=本地上线演练医院&page=1&size=20",
  );
  await expectOk(response, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "name") === "本地上线演练医院" ||
      textField(item, "code") === "e2e-rehearsal-hospital",
  );
  return requireText(textField(hospital, "id"), "必须找到本地上线演练医院");
}

async function localRehearsalQualityDepartmentId(page: Page, suffix: string) {
  const hospitalId = await localRehearsalHospitalId(page);
  const existing = await getApi(
    page,
    `/engine/org/org-units?level=DEPARTMENT&status=ACTIVE&ancestorId=${encodeURIComponent(hospitalId)}&page=1&size=20`,
  );
  await expectOk(existing, "读取本地上线演练医院科室");
  const active = pageItems(await responseData(existing)).find(
    (item) => textField(item, "level") === "DEPARTMENT" && textField(item, "status") === "ACTIVE",
  );
  const activeId = textField(active, "id");
  if (activeId) return activeId;

  await ensureReadySession(page, "platform-admin");
  const created = await postApi(page, "/engine/org/org-units", {
    parentId: hospitalId,
    code: `E2E-SURGERY-QC-${suffix.toUpperCase()}`,
    name: `围手术期整改科室${suffix.slice(-4)}`,
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  await expectOk(created, "创建围手术期整改科室");
  const department = await responseData(created);
  expect(textField(department, "level"), "S26 责任组织必须是科室").toBe("DEPARTMENT");
  return requireText(textField(department, "id"), "创建科室必须返回 id");
}

function assertRuntimeContainsAsset(detail: RuntimeReleaseDetail, expected: PeriopAssetCandidate) {
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

function runtimeSelection(asset: PeriopAssetCandidate): RuntimeAssetSelection {
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
    .locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')]",
    )
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
    request_id: `req-surgery-${step}-${subject}`,
    trace_id: `trace-surgery-${step}-${subject}`,
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
