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
    platformBaselineReleaseId?: string;
  };
  items?: RuntimeReleaseItem[];
};

type PharmacyReviewAssetCandidate = {
  assetType: "TERMINOLOGY" | "SAFETY" | "CDSS_RISK" | "RULE" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type PharmacyReviewActionCardCandidate = PharmacyReviewAssetCandidate & {
  assetType: "ACTION_CARD";
  requiresPhysicianConfirmation: boolean;
  noAutoOrder: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  resources: Record<string, unknown>;
};

type PharmacyReviewApiEvidence = {
  pharmacyReviewAdapterCreatedThroughRealService: boolean;
  pharmacyReviewWebhookCreatedThroughRealService: boolean;
  webhookSignaturePreviewGenerated: boolean;
  antimicrobialTerminologyActivated: boolean;
  antimicrobialRiskMatrixCreated: boolean;
  antimicrobialSafetyAssetPromoted: boolean;
  antimicrobialActionCardPublished: boolean;
  antimicrobialRuleCreated: boolean;
  runtimeActivatedWithAntimicrobialAssets: boolean;
  contextSnapshotCreatedFromFrontdesk: boolean;
  outboundReviewRequested: boolean;
  inboundReviewAccepted: boolean;
  clinicalEvaluationTriggeredFromFrontdesk: boolean;
  pharmacistReviewRecordedWithoutClosingPhysicianConfirmation: boolean;
  physicianConfirmationRecorded: boolean;
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

const requiredStages = {
  S18: [
    "运营员发布抗菌药物术语、红线、风险矩阵、规则和动作卡资产",
    "当前机构生效版本包含抗菌药物五类运行资产",
    "临床用户从患者 360 建立 Medication、AllergyIntolerance、Condition 与 Observation 上下文",
    "临床用户从真实前台触发 medication-prescribe 推荐评估",
    "推荐卡证明抗菌药物红线、规则和动作卡按当前机构生效版本消费",
    "药师登记审方复核且不关闭医生确认链路",
    "医生逐条确认采纳，系统不自动开嘱",
  ],
  S31: [
    "平台管理员访问真实前台并经真实服务创建 PHARMACY_REVIEW 适配器、回调通道和签名预览",
    "系统向 PHARMACY_REVIEW 发出审方请求并诚实断连降级",
    "PHARMACY_REVIEW 签名回传审方结果并生成标准临床事件",
    "药事治理问题形成整改任务",
    "固定四职责账号提交并复核关闭本轮整改任务",
  ],
} as const;

test.describe("药房审方与抗菌药物治理代表切片真实前台闭环", () => {
  test("临床用户按医生和药师业务任职与运营员、平台管理员完成本轮抗菌药物审方代表切片回传、推荐确认和整改闭环", async ({
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;
    const observedStages = new Set<string>();
    const apiEvidence = createApiEvidence();

    await ensureReadySession(page, "engine-operator");
    const hospitalId = await localRehearsalHospitalId(page);
    const riskMatrix = await createAntimicrobialRiskMatrix(page, suffix);
    apiEvidence.antimicrobialRiskMatrixCreated = true;

    const safetyRedline = await createPromotedAntimicrobialRedline(page, {
      suffix,
      riskMatrix,
    });
    apiEvidence.antimicrobialSafetyAssetPromoted = true;

    const terminologyGate = await createAntimicrobialTerminologyGate(page, suffix);
    apiEvidence.antimicrobialTerminologyActivated = true;

    const actionCard = await createAntimicrobialActionCard(page, suffix);
    apiEvidence.antimicrobialActionCardPublished = true;

    const preRuleCandidates = await readPharmacyReviewPreRuleRuntimeCandidates(page, hospitalId, {
      safetyIdentity: safetyRedline.assetIdentity,
    });
    const preRuleRuntime = await activateRuntimeWithPharmacyReviewPreRuleAssets(page, {
      hospitalId,
      terminology: terminologyGate,
      safety: preRuleCandidates.safety,
      cdssRisk: preRuleCandidates.cdssRisk,
      actionCard,
    });

    const positiveSnapshot = await createPharmacyReviewContextFromFrontdesk(page, `${suffix}-POS`);
    expect(
      positiveSnapshot.runtimeReleaseId,
      "规则发布阳性用例快照必须绑定已包含 ACTION_CARD 的预备 runtime",
    ).toBe(preRuleRuntime.releaseId);
    const negativeSnapshot = await createPharmacyReviewContextFromFrontdesk(page, `${suffix}-NEG`, {
      medicationText: "阿司匹林",
      allergyText: "头孢菌素：呼吸困难",
      diagnosisText: "普通复诊",
      observationText: "CRP=4 mg/L",
    });
    expect(
      negativeSnapshot.runtimeReleaseId,
      "规则发布阴性用例快照必须绑定已包含 ACTION_CARD 的预备 runtime",
    ).toBe(preRuleRuntime.releaseId);
    await ensureReadySession(page, "engine-operator");
    const rule = await createAndPublishAntimicrobialRule(page, suffix, {
      positiveContextSnapshotId: positiveSnapshot.snapshotId,
      negativeContextSnapshotId: negativeSnapshot.snapshotId,
      actionCard,
    });
    apiEvidence.antimicrobialRuleCreated = true;
    recordStage(observedStages, "运营员发布抗菌药物术语、红线、风险矩阵、规则和动作卡资产");

    const candidates = await readPharmacyReviewRuntimeCandidates(page, hospitalId, {
      safetyIdentity: safetyRedline.assetIdentity,
      ruleIdentity: rule.assetIdentity,
    });
    const riskMatrixEvidence = {
      ...riskMatrix,
      versionId: candidates.cdssRisk.versionId,
      versionNo: candidates.cdssRisk.versionNo,
      contentHash: candidates.cdssRisk.contentHash,
    };
    const safetyRedlineEvidence = {
      ...safetyRedline,
      versionId: candidates.safety.versionId,
      versionNo: candidates.safety.versionNo,
      contentHash: candidates.safety.contentHash,
    };
    const ruleEvidence = {
      ...rule,
      versionId: candidates.rule.versionId,
      versionNo: candidates.rule.versionNo,
      contentHash: candidates.rule.contentHash,
    };
    const runtime = await activateRuntimeWithPharmacyReviewAssets(page, {
      hospitalId,
      terminology: terminologyGate,
      safety: candidates.safety,
      cdssRisk: candidates.cdssRisk,
      rule: candidates.rule,
      actionCard,
    });
    apiEvidence.runtimeActivatedWithAntimicrobialAssets = true;
    recordStage(observedStages, "当前机构生效版本包含抗菌药物五类运行资产");

    const snapshot = await createPharmacyReviewContextFromFrontdesk(page, suffix);
    expect(snapshot.runtimeReleaseId, "审方上下文必须绑定本轮抗菌药物 runtime").toBe(
      runtime.releaseId,
    );
    assertSnapshotContainsPharmacyReviewFacts(snapshot.resources);
    apiEvidence.contextSnapshotCreatedFromFrontdesk = true;
    recordStage(
      observedStages,
      "临床用户从患者 360 建立 Medication、AllergyIntolerance、Condition 与 Observation 上下文",
    );

    await ensureReadySession(page, "platform-admin");
    const adapter = await createPharmacyReviewAdapter(page, suffix);
    apiEvidence.pharmacyReviewAdapterCreatedThroughRealService = true;
    const webhook = await createPharmacyReviewWebhook(page, suffix);
    apiEvidence.pharmacyReviewWebhookCreatedThroughRealService = true;
    await generatePharmacyReviewSignaturePreview(page, webhook.webhookId);
    apiEvidence.webhookSignaturePreviewGenerated = true;
    recordStage(
      observedStages,
      "平台管理员访问真实前台并经真实服务创建 PHARMACY_REVIEW 适配器、回调通道和签名预览",
    );

    const outboundReview = await sendPharmacyReviewOutbound(page, {
      suffix,
      adapterId: adapter.adapterId,
      snapshot,
    });
    apiEvidence.outboundReviewRequested = true;
    recordStage(observedStages, "系统向 PHARMACY_REVIEW 发出审方请求并诚实断连降级");

    const inboundReview = await postSignedPharmacyReviewInbound(page, {
      suffix,
      adapterId: adapter.adapterId,
      webhookId: webhook.webhookId,
      webhookSecret: webhook.sharedSecret,
      snapshot,
      runtimeReleaseId: runtime.releaseId,
      traceId: outboundReview.traceId,
    });
    apiEvidence.inboundReviewAccepted = true;
    recordStage(observedStages, "PHARMACY_REVIEW 签名回传审方结果并生成标准临床事件");

    const recommendation = await triggerPharmacyReviewRecommendationFromFrontdesk(page, {
      snapshot,
      runtime,
      riskMatrix: riskMatrixEvidence,
      safetyRedline: safetyRedlineEvidence,
      rule: ruleEvidence,
    });
    apiEvidence.clinicalEvaluationTriggeredFromFrontdesk = true;
    recordStage(observedStages, "临床用户从真实前台触发 medication-prescribe 推荐评估");
    recordStage(observedStages, "推荐卡证明抗菌药物红线、规则和动作卡按当前机构生效版本消费");

    const feedback = await completePharmacistAndPhysicianFeedback(page, {
      cardId: recommendation.cardId,
      actionCardAsset: runtime.actionCardAsset,
      actionCard,
    });
    expect(feedback.pharmacist.cardStatus, "药师审方复核不能关闭医生确认链路").toBe("PENDING");
    expect(feedback.physician.cardStatus, "医生确认后推荐卡才进入采纳状态").toBe("ACCEPTED");
    apiEvidence.pharmacistReviewRecordedWithoutClosingPhysicianConfirmation = true;
    apiEvidence.physicianConfirmationRecorded = true;
    recordStage(observedStages, "药师登记审方复核且不关闭医生确认链路");
    recordStage(observedStages, "医生逐条确认采纳，系统不自动开嘱");

    const qualityRectification = await createAndClosePharmacyReviewRectification(page, {
      suffix,
      recommendation,
      snapshot,
      runtimeReleaseId: runtime.releaseId,
    });
    apiEvidence.qualityRectificationSubmittedAndReviewed = true;
    recordStage(observedStages, "药事治理问题形成整改任务");
    recordStage(observedStages, "固定四职责账号提交并复核关闭本轮整改任务");

    await attachPharmacyReviewAntimicrobialEvidence(testInfo, {
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
      riskMatrix: riskMatrixEvidence,
      safetyRedline: safetyRedlineEvidence,
      actionCard,
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
      outboundReview,
      inboundReview,
      clinicalTrigger: {
        triggerId: recommendation.triggerId,
        contextSnapshotId: snapshot.snapshotId,
        runtimeReleaseId: runtime.releaseId,
        cardId: recommendation.cardId,
        relatedCardIds: recommendation.relatedCardIds,
      },
      recommendation,
      ruleRecommendation: recommendation.ruleRecommendation,
      feedback,
      qualityRectification,
      observedStages,
    });
  });
});

function createApiEvidence(): PharmacyReviewApiEvidence {
  return {
    pharmacyReviewAdapterCreatedThroughRealService: false,
    pharmacyReviewWebhookCreatedThroughRealService: false,
    webhookSignaturePreviewGenerated: false,
    antimicrobialTerminologyActivated: false,
    antimicrobialRiskMatrixCreated: false,
    antimicrobialSafetyAssetPromoted: false,
    antimicrobialActionCardPublished: false,
    antimicrobialRuleCreated: false,
    runtimeActivatedWithAntimicrobialAssets: false,
    contextSnapshotCreatedFromFrontdesk: false,
    outboundReviewRequested: false,
    inboundReviewAccepted: false,
    clinicalEvaluationTriggeredFromFrontdesk: false,
    pharmacistReviewRecordedWithoutClosingPhysicianConfirmation: false,
    physicianConfirmationRecorded: false,
    qualityRectificationSubmittedAndReviewed: false,
  };
}

async function createAntimicrobialRiskMatrix(page: Page, suffix: string) {
  const matrixVersion = `pharmacy-review-antimicrobial-${suffix}`;
  const response = await putApi(page, "/engine/cdss/risk-matrix", {
    matrixVersion,
    changeReason: "S18/S31 药房审方代表切片：验证抗菌药物限制级风险需医师确认。",
    status: "ACTIVE",
    entries: [
      {
        triggerPoint: "medication-prescribe",
        severityLevel: "CRITICAL",
        automationLevel: "INFORM_ONLY",
        riskLevel: "CRITICAL",
        reviewRequirement: "PHYSICIAN_CONFIRMATION",
        silentRunHours: 168,
        releaseGate: "OPT04_ANTIMICROBIAL_RESTRICTION",
        autoExecutionAllowed: false,
        samdClassification: "NMPA_RESERVED",
        regulatoryEvidence: "NOT_ASSESSED",
        explanation: "抗菌药物限制级风险只生成需医师确认的建议，不自动开嘱。",
      },
    ],
  });
  await expectOk(response, "创建抗菌药物审方 CDSS_RISK 风险矩阵");
  const rule = arrayField(await responseData(response), "rules").find(
    (item) =>
      textField(item, "triggerPoint") === "medication-prescribe" &&
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
    severityLevel: requireText(textField(rule, "severityLevel"), "风险矩阵必须返回严重度"),
    automationLevel: requireText(textField(rule, "automationLevel"), "风险矩阵必须返回自动化等级"),
    riskLevel: requireText(textField(rule, "riskLevel"), "风险矩阵必须返回风险等级"),
    reviewRequirement: requireText(
      textField(rule, "reviewRequirement"),
      "风险矩阵必须返回复核要求",
    ),
    silentRunHours: numberField(rule, "silentRunHours") ?? 0,
    releaseGate: requireText(textField(rule, "releaseGate"), "风险矩阵必须返回上线门槛"),
    autoExecutionAllowed: booleanField(rule, "autoExecutionAllowed"),
  };
}

async function createPromotedAntimicrobialRedline(
  page: Page,
  options: { suffix: string; riskMatrix: { matrixId: string; matrixVersion: string } },
) {
  const redlineId = `redline-antimicrobial-${options.suffix.toLowerCase()}`;
  const redlineKey = `RDL-ANTIMICROBIAL-${options.suffix}`;
  const redlineVersion = "2026.1";
  const conditionDsl = JSON.stringify({
    all: [
      { fact: "medications[].code", operator: "contains", value: "J01C" },
      { fact: "observations[].valueNumeric", operator: "gte", value: 2 },
    ],
  });
  const draft = await postApi(page, "/engine/safety/redlines", {
    redlineId,
    category: "ANTIMICROBIAL_RESTRICTION",
    triggerPoint: "medication-prescribe",
    scopeType: "TENANT",
    scopeRef: resolvedTenantIdFor("engine-operator"),
    redlineKey,
    redlineVersion,
    hazardSeverity: "CRITICAL",
    riskMatrixId: options.riskMatrix.matrixId,
    riskMatrixVersion: options.riskMatrix.matrixVersion,
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    silentRunHours: 168,
    releaseGate: "OPT04_ANTIMICROBIAL_RESTRICTION",
    title: `抗菌药物限制级审方红线 ${options.suffix}`,
    clinicalHazard: "抗菌药物开立需结合感染诊断和监测指标，由医生逐条确认；系统不得自动开嘱。",
    conditionDsl,
    evidenceSource: "S18/S31 药房审方与抗菌药物治理代表切片演练证据",
    evidenceReference: "evidence://local-e2e/pharmacy-review/antimicrobial-redline",
    sourceVersionId: null,
    lowerTenantOverrideAllowed: false,
  });
  await expectOk(draft, "创建抗菌药物 SAFETY 红线草稿");
  const dryRun = await postApi(page, "/engine/safety/redlines:dry-run", {
    redlineId,
    observedFrom: "2026-05-26T00:00:00Z",
    observedTo: "2026-06-03T00:00:00Z",
    evaluatedCaseCount: 1600,
    matchedCaseCount: 42,
    falsePositiveCaseCount: 2,
    safetyIncidentCount: 0,
    evidenceReference: "evidence://local-e2e/pharmacy-review/antimicrobial-redline/silent-run",
    operatorNote: "S18/S31 药房审方代表切片：静默试运行达标，不自动开嘱。",
  });
  await expectOk(dryRun, "提交抗菌药物 SAFETY 静默试运行");
  const trialId = requireText(
    textField(await responseData(dryRun), "trialId"),
    "静默试运行必须返回 trialId",
  );
  const promoted = await postApi(page, "/engine/safety/redlines:promote", {
    redlineId,
    trialId,
    expectedRedlineVersion: redlineVersion,
    promotionReason: "S18/S31 药房审方代表切片：静默试运行达标后纳入 SAFETY 资产候选。",
  });
  await expectOk(promoted, "上线抗菌药物 SAFETY 资产");
  const promotedData = await responseData(promoted);
  return {
    assetType: "SAFETY" as const,
    assetIdentity: `SAFETY.${redlineKey}`,
    redlineId,
    redlineKey,
    redlineVersion,
    category: "ANTIMICROBIAL_RESTRICTION",
    conditionDsl,
    trialId,
    hazardSeverity: requireText(
      textField(promotedData, "hazardSeverity"),
      "红线上线响应必须返回严重度",
    ),
    riskMatrixId: requireText(
      textField(promotedData, "riskMatrixId"),
      "红线上线响应必须返回风险矩阵 ID",
    ),
    riskMatrixVersion: requireText(
      textField(promotedData, "riskMatrixVersion"),
      "红线上线响应必须返回风险矩阵版本",
    ),
    reviewRequirement: requireText(
      textField(promotedData, "reviewRequirement"),
      "红线上线响应必须返回医师确认要求",
    ),
    releaseGate: requireText(
      textField(promotedData, "releaseGate"),
      "红线上线响应必须返回上线门槛",
    ),
    lowerTenantOverrideAllowed: booleanField(promotedData, "lowerTenantOverrideAllowed"),
  };
}

async function createAntimicrobialTerminologyGate(page: Page, suffix: string) {
  const drugStandard = await postApi(page, "/engine/terminology/terms/standard", {
    ...apiContext(suffix, "term-standard"),
    standardSystem: "ATC",
    termCode: "J01C",
    category: "DRUG",
    displayName: "青霉素类",
    normalizedName: "青霉素类|J01C|PENICILLIN",
    versionNo: "2026",
    sourceVersionId: null,
    evidenceText: "S18/S31 药房审方代表切片：抗菌药物规则发布门禁所需 ATC 标准药品术语。",
  });
  await expectOk(drugStandard, "登记抗菌药物 ATC 标准术语");
  const drugStandardData = await responseData(drugStandard);
  const drugStandardTermId = numberField(drugStandardData, "id");
  const sourceSystem = "MEDKERNEL_FRONTDESK";
  const drugLocalCode = "J01C";
  const drugLocal = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-local"),
    sourceSystem,
    localCode: drugLocalCode,
    category: "DRUG",
    localName: "青霉素类抗菌药",
    normalizedName: "青霉素类抗菌药|J01C|PENICILLIN",
    local_department_id: null,
  });
  await expectOk(drugLocal, "登记前台抗菌药物院内术语");
  const drugMapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem,
    localCode: drugLocalCode,
    standardTermId: drugStandardTermId,
    category: "DRUG",
    reviewNote: "S18/S31 代表切片：确认前台 J01C 到 ATC:J01C。",
    evidenceOverride: "抗菌药物审方规则发布前置 ATC 药品术语覆盖门禁。",
  });
  const reviewSourceSystem = "PHARMACY_REVIEW";
  const reviewDrugLocal = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-review-drug-local"),
    sourceSystem: reviewSourceSystem,
    localCode: drugLocalCode,
    category: "DRUG",
    localName: "审方系统青霉素类抗菌药",
    normalizedName: "审方系统青霉素类抗菌药|J01C|PENICILLIN",
    local_department_id: null,
  });
  await expectOk(reviewDrugLocal, "登记 PHARMACY_REVIEW 抗菌药物院内术语");
  const reviewDrugMapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem: reviewSourceSystem,
    localCode: drugLocalCode,
    standardTermId: drugStandardTermId,
    category: "DRUG",
    reviewNote: "S18/S31 代表切片：确认 PHARMACY_REVIEW/J01C 到 ATC:J01C。",
    evidenceOverride: "PHARMACY_REVIEW 审方入站药品编码归一所需 ATC 映射。",
  });

  const diagnosisStandard = await postApi(page, "/engine/terminology/terms/standard", {
    ...apiContext(suffix, "term-diagnosis-standard"),
    standardSystem: "ICD-10",
    termCode: "J18.900",
    category: "DIAGNOSIS",
    displayName: "肺部感染",
    normalizedName: "肺部感染|J18.900|肺炎",
    versionNo: "2026",
    sourceVersionId: null,
    evidenceText: "S18/S31 药房审方代表切片：抗菌药物规则发布门禁所需 ICD-10 感染诊断术语。",
  });
  await expectOk(diagnosisStandard, "登记抗菌药物审方 ICD-10 标准诊断术语");
  const diagnosisStandardData = await responseData(diagnosisStandard);
  const diagnosisStandardTermId = numberField(diagnosisStandardData, "id");
  const diagnosisLocalCode = "J18.900";
  const diagnosisLocal = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-diagnosis-local"),
    sourceSystem,
    localCode: diagnosisLocalCode,
    category: "DIAGNOSIS",
    localName: "肺部感染",
    normalizedName: "肺部感染|J18.900|肺炎",
    local_department_id: null,
  });
  await expectOk(diagnosisLocal, "登记前台感染诊断院内术语");
  const diagnosisMapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem,
    localCode: diagnosisLocalCode,
    standardTermId: diagnosisStandardTermId,
    category: "DIAGNOSIS",
    reviewNote: "S18/S31 代表切片：确认前台 J18.900 到 ICD-10:J18.900。",
    evidenceOverride: "抗菌药物审方规则发布前置 ICD-10 感染诊断术语覆盖门禁。",
  });
  const reviewDiagnosisLocal = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-review-diagnosis-local"),
    sourceSystem: reviewSourceSystem,
    localCode: diagnosisLocalCode,
    category: "DIAGNOSIS",
    localName: "审方系统肺部感染",
    normalizedName: "审方系统肺部感染|J18.900|肺炎",
    local_department_id: null,
  });
  await expectOk(reviewDiagnosisLocal, "登记 PHARMACY_REVIEW 感染诊断院内术语");
  const reviewDiagnosisMapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem: reviewSourceSystem,
    localCode: diagnosisLocalCode,
    standardTermId: diagnosisStandardTermId,
    category: "DIAGNOSIS",
    reviewNote: "S18/S31 代表切片：确认 PHARMACY_REVIEW/J18.900 到 ICD-10:J18.900。",
    evidenceOverride: "PHARMACY_REVIEW 审方入站感染诊断编码归一所需 ICD-10 映射。",
  });

  const assetIdentity = `TERM.PHARMACY_REVIEW.ANTIMICROBIAL.${suffix}`;
  const draft = await postApi(page, "/engine/terminology/assets/drafts", {
    assetIdentity,
    name: `抗菌药物审方药品与诊断术语映射 ${suffix}`,
    scopeLevel: "TENANT",
    scopeCode: resolvedTenantIdFor("engine-operator"),
  });
  await expectOk(draft, "生成抗菌药物审方术语资产草稿");
  const draftData = await responseData(draft);
  return {
    assetType: "TERMINOLOGY" as const,
    assetIdentity: requireText(textField(draftData, "assetIdentity"), "术语资产草稿必须返回身份"),
    versionId: requireText(textField(draftData, "versionId"), "术语资产草稿必须返回 versionId"),
    versionNo: requireText(textField(draftData, "versionNo"), "术语资产草稿必须返回 versionNo"),
    contentHash: requireText(
      textField(draftData, "contentHash"),
      "术语资产草稿必须返回 contentHash",
    ),
    standardSystem: "ATC" as const,
    standardCode: "J01C",
    localCode: drugLocalCode,
    sourceSystem,
    category: "DRUG" as const,
    mappingId: drugMapping.mappingId,
    pharmacyReview: {
      standardSystem: "ATC" as const,
      standardCode: "J01C",
      localCode: drugLocalCode,
      sourceSystem: reviewSourceSystem,
      category: "DRUG" as const,
      mappingId: reviewDrugMapping.mappingId,
    },
    diagnosis: {
      standardSystem: "ICD-10" as const,
      standardCode: "J18.900",
      localCode: diagnosisLocalCode,
      sourceSystem,
      category: "DIAGNOSIS" as const,
      mappingId: diagnosisMapping.mappingId,
    },
    pharmacyReviewDiagnosis: {
      standardSystem: "ICD-10" as const,
      standardCode: "J18.900",
      localCode: diagnosisLocalCode,
      sourceSystem: reviewSourceSystem,
      category: "DIAGNOSIS" as const,
      mappingId: reviewDiagnosisMapping.mappingId,
    },
  };
}

async function readOrConfirmTerminologyMapping(
  page: Page,
  options: {
    suffix: string;
    sourceSystem: string;
    localCode: string;
    standardTermId?: number | null;
    category: "DRUG" | "DIAGNOSIS";
    reviewNote: string;
    evidenceOverride: string;
  },
) {
  const existing = await getApi(
    page,
    `/engine/terminology/mappings?category=${encodeURIComponent(options.category)}&status=CONFIRMED&page=1&size=100`,
  );
  await expectOk(existing, `读取已确认 ${options.category} 术语映射`);
  const mappings = pageItems(await responseData(existing));
  const found = mappings.find(
    (item) =>
      numberField(item, "standardTermId") === options.standardTermId &&
      textField(item, "sourceSystem") === options.sourceSystem,
  );
  const foundId = numberField(found, "id");
  if (foundId) {
    return { mappingId: foundId };
  }
  const generation = await postApi(page, "/engine/terminology/mappings/candidates", {
    ...apiContext(options.suffix, "term-candidates"),
    sourceSystem: options.sourceSystem,
    minimumScore: 0.2,
    semanticAssistEnabled: true,
  });
  await expectOk(generation, `生成 ${options.category} 术语映射候选`);
  const jobCode = requireText(
    textField(await responseData(generation), "jobCode"),
    "术语候选任务必须返回 jobCode",
  );
  const candidate = await waitForTerminologyCandidate(page, jobCode, options.localCode);
  const candidateId = numberField(candidate, "id");
  expect(candidateId, "术语候选必须返回 id").toBeTruthy();
  const confirmed = await postApi(
    page,
    `/engine/terminology/mappings/${encodeURIComponent(String(candidateId))}/confirm`,
    {
      ...apiContext(options.suffix, "term-confirm"),
      reviewNote: options.reviewNote,
      evidenceOverride: options.evidenceOverride,
    },
  );
  await expectOk(confirmed, `确认 ${options.category} 术语映射`);
  return { mappingId: Number(numberField(await responseData(confirmed), "id")) };
}

async function waitForTerminologyCandidate(page: Page, jobCode: string, localCode: string) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    const candidates = await getApi(
      page,
      `/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=${encodeURIComponent(jobCode)}&page=1&size=20`,
    );
    await expectOk(candidates, "读取抗菌药物术语映射候选");
    const candidate = pageItems(await responseData(candidates)).find((item) =>
      String(textField(item, "evidenceText") ?? "").includes(localCode),
    );
    if (candidate) return candidate;
    await waitForPollingInterval(250);
  }
  throw new Error(`抗菌药物术语候选生成超时 ${jobCode}`);
}

async function createAntimicrobialActionCard(
  page: Page,
  suffix: string,
): Promise<PharmacyReviewActionCardCandidate> {
  const assetIdentity = `ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL.${suffix}`;
  const cardGovernance = {
    requiresPhysicianConfirmation: true,
    noAutoOrder: true,
  };
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity,
    applicableScope: "ALL",
    sourceRef: "S18/S31 药房审方代表切片：抗菌药物审方提示卡，不自动开嘱。",
    content: {
      schemaVersion: "1.0",
      title: `抗菌药物审方提示卡 ${suffix}`,
      actionCode: "STRONG_REMINDER",
      atSeverity: "HIGH",
      indicator: "critical",
      summary: "抗菌药物开立需结合感染诊断、监测指标和审方意见人工确认。",
      detail: "提示卡只进入药师复核和医生确认链路，不自动开嘱，不替代处方系统审方。",
      source: { label: "MedKernel S18/S31 本地上线演练" },
      suggestions: [
        { label: "打开审方记录", actionType: "OPEN_FORM", payload: { target: "PHARMACY_REVIEW" } },
      ],
      overrideReasons: ["医生已结合感染指标和药师意见完成人工确认"],
      requiresPhysicianConfirmation: cardGovernance.requiresPhysicianConfirmation,
      noAutoOrder: cardGovernance.noAutoOrder,
    },
  });
  await expectOk(response, "创建抗菌药物 ACTION_CARD 资产");
  const data = await responseData(response);
  return {
    assetType: "ACTION_CARD" as const,
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "ACTION_CARD 必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "ACTION_CARD 必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "ACTION_CARD 必须返回 contentHash"),
    entryState: "ACTIVE",
    requiresPhysicianConfirmation: cardGovernance.requiresPhysicianConfirmation,
    noAutoOrder: cardGovernance.noAutoOrder,
  };
}

async function createAndPublishAntimicrobialRule(
  page: Page,
  suffix: string,
  options: {
    positiveContextSnapshotId: string;
    negativeContextSnapshotId: string;
    actionCard: { assetIdentity: string };
  },
) {
  const ruleCode = `RULE.MEDICATION.PHARMACY_REVIEW.ANTIMICROBIAL.${suffix}`;
  const create = await postApi(page, "/engine/rule/rules", {
    ...apiContext(suffix, "rule-create"),
    triggers: [
      {
        trigger_point: "medication-prescribe",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "medications", "conditions", "observations"],
      },
    ],
    ruleCode,
    name: `抗菌药物审方代表切片规则 ${suffix}`,
    ruleType: "ORDER",
    authoringMode: "DSL",
    riskLevel: "HIGH",
    priority: 960,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:pharmacy-review-antimicrobial",
    changeSummary: "S18/S31 代表切片：规则引用 Medication、Condition、Observation 和 ACTION_CARD。",
    dsl: {
      applicability: {
        population: {},
        orgScope: {},
        settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
        effective: { rolloutPercent: 100 },
      },
      when: {
        all: [
          { fact: "medications[].code", operator: "contains", value: "J01C" },
          { fact: "conditions[].code", operator: "equals", value: "J18.900" },
          { fact: "observations[].valueNumeric", operator: "gte", value: 2 },
        ],
      },
      then: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "抗菌药物需结合感染指标和审方意见复核",
          detail: "建议进入药师复核和医生确认链路；是否开嘱仍由医生在 HIS 人工确认。",
          source: { label: "S18/S31 药房审方代表切片" },
          actionCardRef: options.actionCard.assetIdentity,
          suggestions: [],
          overrideReasons: ["医生已结合感染指标和审方意见完成人工确认"],
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        title: "抗菌药物审方代表切片规则",
        reason:
          "Medication、Condition、Observation 均来自当前临床上下文，规则由当前机构生效版本锁定。",
        sourceRef: "local-e2e:pharmacy-review-antimicrobial",
      },
    },
    explanation: {
      title: "抗菌药物审方代表切片规则",
      summary: "证明抗菌药物规则和提示卡进入当前机构生效版本。",
    },
    parameterBindings: {},
  });
  await expectOk(create, "创建抗菌药物审方 RULE 资产");
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
    await expectOk(response, `新增抗菌药物规则发布验证用例 ${testCase.caseType}`);
  }
  const testRun = await postApi(
    page,
    `/engine/rule/rules/${encodeURIComponent(ruleId)}/test`,
    apiContext(ruleId, "rule-test-run"),
  );
  await expectOk(testRun, "执行抗菌药物规则发布验证用例");
  expect(
    booleanField(await responseData(testRun), "allPassed"),
    "规则发布验证用例必须全部通过",
  ).toBe(true);
  for (const targetState of ["REVIEWED", "SHADOW", "CANARY", "FULL"]) {
    const impact = await getApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/impact`);
    await expectOk(impact, `读取抗菌药物规则 ${targetState} 影响摘要`);
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
        reason: `S18/S31 抗菌药物审方规则推进至 ${targetState}`,
        publishEvidence: {
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            summary: `抗菌药物审方规则 ${targetState} 推进质量门已通过`,
          },
        },
      },
    );
    await expectOk(transition, `抗菌药物规则治理推进至 ${targetState}`);
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

async function readPharmacyReviewPreRuleRuntimeCandidates(
  page: Page,
  hospitalId: string,
  identities: {
    safetyIdentity: string;
  },
) {
  const [safety, cdssRisk] = await Promise.all([
    readHospitalRuntimeCandidate(page, hospitalId, "SAFETY", identities.safetyIdentity),
    readHospitalRuntimeCandidate(page, hospitalId, "CDSS_RISK", "CDSS.RISK.MATRIX"),
  ]);
  return { safety, cdssRisk };
}

async function readPharmacyReviewRuntimeCandidates(
  page: Page,
  hospitalId: string,
  identities: {
    safetyIdentity: string;
    ruleIdentity: string;
  },
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
  assetType: PharmacyReviewAssetCandidate["assetType"],
  assetIdentity: string,
): Promise<PharmacyReviewAssetCandidate> {
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

async function activateRuntimeWithPharmacyReviewPreRuleAssets(
  page: Page,
  options: {
    hospitalId: string;
    terminology: PharmacyReviewAssetCandidate;
    safety: PharmacyReviewAssetCandidate;
    cdssRisk: PharmacyReviewAssetCandidate;
    actionCard: PharmacyReviewAssetCandidate;
  },
) {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(baselineAssets.baselineReleaseId, "当前平台标准版本必须存在").toBeTruthy();
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const currentRuntime = await responseData(current);
  const currentReleaseId = textFieldAtPath(currentRuntime, "release.releaseId");
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    {
      platformBaselineReleaseId: baselineAssets.baselineReleaseId,
      expectedCurrentReleaseId: currentReleaseId,
      confirmedPlatformUpgradeDigest: null,
      activeAssets: uniqueRuntimeAssets([
        ...baselineAssets.activeAssets,
        ...[options.terminology, options.safety, options.cdssRisk, options.actionCard].map(
          runtimeSelection,
        ),
      ]),
    },
  );
  await expectOk(activated, "激活规则发布验证所需抗菌药物审方预备 runtime");
  return {
    releaseId: requireText(
      textField(await responseData(activated), "releaseId"),
      "预备 runtime 激活必须返回 releaseId",
    ),
  };
}

async function activateRuntimeWithPharmacyReviewAssets(
  page: Page,
  options: {
    hospitalId: string;
    terminology: PharmacyReviewAssetCandidate;
    safety: PharmacyReviewAssetCandidate;
    cdssRisk: PharmacyReviewAssetCandidate;
    rule: PharmacyReviewAssetCandidate;
    actionCard: PharmacyReviewAssetCandidate;
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
  const currentRuntime = await responseData(current);
  const currentReleaseId = textFieldAtPath(currentRuntime, "release.releaseId");
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId,
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest: null,
    activeAssets: uniqueRuntimeAssets([
      ...baselineAssets.activeAssets,
      ...[
        options.terminology,
        options.safety,
        options.cdssRisk,
        options.rule,
        options.actionCard,
      ].map(runtimeSelection),
    ]),
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, "激活包含抗菌药物审方资产的医院生效版本");
  const releaseId = requireText(
    textField(await responseData(activated), "releaseId"),
    "激活必须返回 releaseId",
  );
  const currentAfter = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfter, "回读抗菌药物审方医院生效版本");
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

async function createPharmacyReviewContextFromFrontdesk(
  page: Page,
  suffix: string,
  overrides: {
    medicationText?: string;
    allergyText?: string;
    diagnosisText?: string;
    observationText?: string;
  } = {},
): Promise<ContextSnapshotSummary> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `审*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = patientDialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("66");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "创建药房审方演练脱敏患者");
  const patientId = requireText(
    textField(await responseData(patientResponse), "mpiId"),
    "患者创建响应必须返回 MPI",
  );
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(maskedName)}.*${idLast4}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();
  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible();
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill(overrides.diagnosisText ?? "J18.900");
  await chooseDialogOption(page, contextDialog, "风险分层", "高风险");
  await contextDialog
    .getByLabel("当前用药")
    .fill(overrides.medicationText ?? `青霉素、抗菌药物审方演练 ${suffix}`);
  await contextDialog
    .getByLabel("过敏/不良反应")
    .fill(overrides.allergyText ?? "青霉素：皮疹；头孢菌素：呼吸困难");
  await contextDialog
    .getByLabel("监测指标")
    .fill(overrides.observationText ?? "CRP=128 mg/L；PCT=2.4 ng/mL");
  await contextDialog.getByLabel("身高 cm").fill("170");
  await contextDialog.getByLabel("体重 kg").fill("82");
  await contextDialog
    .getByLabel("建立原因")
    .fill("S18/S31 药房审方代表切片：建立抗菌药物、感染诊断、过敏史与监测指标上下文。");
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, "建立药房审方 ACTIVE 快照");
  const context = await responseData(contextResponse);
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    snapshotId: requireText(textField(context, "snapshotId"), "上下文响应必须返回 snapshotId"),
    runtimeReleaseId: requireText(
      textField(context, "runtimeReleaseId"),
      "上下文响应必须锁定 runtimeReleaseId",
    ),
    encounterId: textFieldAtPath(context, "resources.encounters[0].encounterId"),
    resources: recordValue(recordField(context, "resources")) ?? {},
  };
}

async function createPharmacyReviewAdapter(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  const adapterId = `pharmacy-review-${suffix.toLowerCase()}`;
  const config = {
    systemFamilyCode: "PHARMACY_REVIEW",
    sourceSystem: "PHARMACY_REVIEW",
    targetSystem: "PHARMACY_REVIEW",
    baseUrl: "https://pharmacy-review.example.test",
    healthPath: "/health",
    outboundPath: "/review-results",
    connectTimeoutMs: 800,
    requestTimeoutMs: 1200,
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      {
        sourcePath: "/medicationCode",
        targetPath: "/medications/0",
        targetDictionaryKey: "ATC",
        category: "DRUG",
      },
      {
        sourcePath: "/infectionCode",
        targetPath: "/conditions/0",
        targetDictionaryKey: "ICD-10",
        category: "DIAGNOSIS",
      },
      { sourcePath: "/observationCode", targetPath: "/observations/0/code" },
      { sourcePath: "/pct", targetPath: "/observations/0/valueNumeric" },
      { sourcePath: "/pharmacyReview/reviewResult", targetPath: "/pharmacyReview/reviewResult" },
      {
        sourcePath: "/pharmacyReview/pharmacistOpinion",
        targetPath: "/pharmacyReview/pharmacistOpinion",
      },
    ],
  };
  const response = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `PHARMACY_REVIEW 抗菌药物审方 ${suffix}`,
    protocolType: "Webhook",
    configJson: JSON.stringify(config),
  });
  await expectOk(response, "创建 PHARMACY_REVIEW 审方适配器");
  return { adapterId, protocolType: "Webhook", ...config };
}

async function createPharmacyReviewWebhook(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  const webhookId = `pharmacy-review-webhook-${suffix.toLowerCase()}`;
  const response = await postApi(page, "/engine/integration/webhooks", {
    webhookId,
    name: `PHARMACY_REVIEW 审方回传 ${suffix}`,
    callbackUrl: "https://pharmacy-review.example.test/medkernel/events",
    eventsSubscribed: "PHARMACY_REVIEW_RESULT MEDICATION_REVIEW",
  });
  await expectOk(response, "创建 PHARMACY_REVIEW 审方回调通道");
  const data = await responseData(response);
  return {
    webhookId,
    sharedSecret: requireText(textField(data, "sharedSecret"), "回调通道必须一次性返回共享密钥"),
  };
}

async function generatePharmacyReviewSignaturePreview(page: Page, webhookId: string) {
  const response = await postApi(page, "/engine/integration/webhooks/test", {
    webhookId,
    payload: JSON.stringify({
      traceId: `preview-${webhookId}`,
      eventType: "PHARMACY_REVIEW_RESULT",
    }),
  });
  await expectOk(response, "生成 PHARMACY_REVIEW 回调签名预览");
  const data = await responseData(response);
  expect(textField(data, "signature"), "签名预览必须返回裸 hex 签名").toMatch(/^[0-9a-f]{64}$/i);
}

async function sendPharmacyReviewOutbound(
  page: Page,
  options: { suffix: string; adapterId: string; snapshot: ContextSnapshotSummary },
) {
  const response = await postApi(page, "/engine/integration/messages/outbound", {
    messageId: `out-pharmacy-review-${options.suffix}`,
    traceId: `trace-pharmacy-review-${options.suffix}`,
    adapterId: options.adapterId,
    targetSystem: "PHARMACY_REVIEW",
    protocolType: "Webhook",
    payloadSummary: "抗菌药物处方审方请求",
    payload: {
      patientId: options.snapshot.patientId,
      contextSnapshotId: options.snapshot.snapshotId,
      medicationCode: "J01C",
      infectionCode: "J18.900",
      observationCode: "PCT",
      pct: 2.4,
      reviewPurpose: "ANTIMICROBIAL_RESTRICTION",
    },
    maxRetries: 2,
  });
  await expectOk(response, "登记 PHARMACY_REVIEW 出站审方请求");
  const data = await responseData(response);
  const blocksMainFlow = booleanField(data, "blocksMainFlow");
  const initialCompensationRequired = booleanField(data, "compensationRequired");
  const status = requireText(textField(data, "status"), "出站审方必须返回诚实状态");
  expect(
    ["NOT_CONNECTED", "RETRYING"].includes(status),
    "出站审方不得把配置错误 FAILED 当成断连降级证据",
  ).toBe(true);
  expect(blocksMainFlow, "审方出站断连不得阻断医生主流程").toBe(false);
  const compensation = await waitForPharmacyReviewCompensation(
    page,
    requireText(textField(data, "messageId"), "出站审方必须返回 messageId"),
  );
  const compensationStatus = requireText(
    textField(compensation, "status"),
    "出站审方补偿日志必须返回状态",
  );
  const compensationMessageId = requireText(
    textField(compensation, "messageId"),
    "出站审方补偿日志必须返回 messageId",
  );
  const compensationRequired = compensationStatus === "NOT_CONNECTED";
  expect(compensationRequired, "审方出站断连最终必须留下补偿证据").toBe(true);
  return {
    messageId: requireText(textField(data, "messageId"), "出站审方必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "出站审方必须返回 traceId"),
    adapterId: options.adapterId,
    targetSystem: "PHARMACY_REVIEW",
    protocolType: "Webhook",
    status,
    compensationStatus,
    compensationMessageId,
    blocksMainFlow,
    initialCompensationRequired,
    compensationRequired,
    payload: {
      patientId: options.snapshot.patientId,
      contextSnapshotId: options.snapshot.snapshotId,
      medicationCode: "J01C",
      infectionCode: "J18.900",
      observationCode: "PCT",
      pct: 2.4,
    },
  };
}

async function waitForPharmacyReviewCompensation(page: Page, messageId: string) {
  const deadline = Date.now() + 20_000;
  let lastStatus: string | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, "/engine/integration/logs?page=1&size=50");
    await expectOk(response, "读取 PHARMACY_REVIEW 出站补偿日志");
    const log = pageItems(await responseData(response)).find(
      (item) => textField(item, "messageId") === messageId,
    );
    if (log) {
      lastStatus = textField(log, "status") ?? lastStatus;
      if (lastStatus === "NOT_CONNECTED") {
        return log;
      }
      if (lastStatus && lastStatus !== "RETRYING") {
        throw new Error(
          `PHARMACY_REVIEW 出站补偿日志 ${messageId} 进入非诚实断连状态：${lastStatus}`,
        );
      }
    }
    await waitForPollingInterval(250);
  }
  throw new Error(
    `PHARMACY_REVIEW 出站补偿日志 ${messageId} 未收敛到 NOT_CONNECTED，最后状态：${lastStatus}`,
  );
}

async function postSignedPharmacyReviewInbound(
  page: Page,
  options: {
    suffix: string;
    adapterId: string;
    webhookId: string;
    webhookSecret: string;
    snapshot: ContextSnapshotSummary;
    runtimeReleaseId: string;
    traceId: string;
  },
) {
  const request: InboundWebhookRequest = {
    messageId: `in-pharmacy-review-${options.suffix}`,
    traceId: options.traceId,
    adapterId: options.adapterId,
    sourceSystem: "PHARMACY_REVIEW",
    eventType: "ORDER",
    patientId: options.snapshot.patientId,
    encounterId: options.snapshot.encounterId ?? `enc-${options.suffix}`,
    clinicalSetting: "OUTPATIENT",
    triggerPoint: "medication-prescribe",
    occurredAt: "2026-07-07T00:05:00Z",
    payload: {
      patientId: options.snapshot.patientId,
      contextSnapshotId: options.snapshot.snapshotId,
      medicationCode: "J01C",
      infectionCode: "J18.900",
      observationCode: "PCT",
      pct: 2.4,
      pharmacyReview: {
        reviewResult: "REQUIRES_PHYSICIAN_CONFIRMATION",
        pharmacistOpinion: "抗菌药物使用需结合感染指标与病原学复核。",
      },
    },
  };
  const webhookId = options.webhookId;
  const timestamp = currentEpochSeconds();
  const signature = `sha256=${signHmacSha256(options.webhookSecret, timestamp, request)}`;
  const response = await postApi(
    page,
    `/engine/integration/webhooks/${encodeURIComponent(webhookId)}/inbound`,
    request,
    {
      "X-MedKernel-Timestamp": timestamp,
      "X-MedKernel-Signature": signature,
    },
  );
  await expectOk(response, "PHARMACY_REVIEW 审方结果签名入站");
  const data = await responseData(response);
  const mappedPayload = recordValue(recordField(data, "mappedPayload")) ?? {};
  const clinicalEventId = requireText(
    textField(data, "clinicalEventId"),
    "入站审方必须返回 clinicalEventId",
  );
  const clinicalEvent = await waitForClinicalEventProcessed(page, clinicalEventId);
  return {
    messageId: requireText(textField(data, "messageId"), "入站审方必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "入站审方必须返回 traceId"),
    adapterId: options.adapterId,
    webhookId: options.webhookId,
    patientId: options.snapshot.patientId,
    encounterId: options.snapshot.encounterId,
    contextSnapshotId: options.snapshot.snapshotId,
    sourceSystem: "PHARMACY_REVIEW",
    status: requireText(textField(data, "status"), "入站审方必须返回状态"),
    clinicalEventStatus: textField(data, "clinicalEventStatus"),
    clinicalEvent,
    mappedFieldCount: numberField(data, "mappedFieldCount") ?? 0,
    mappedPayload,
    signedPayload: request.payload,
  };
}

async function waitForClinicalEventProcessed(
  page: Page,
  eventId: string,
): Promise<ClinicalEventDetailEvidence> {
  const deadline = Date.now() + 30_000;
  let lastDetail: ClinicalEventDetailEvidence | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, `/engine/clinical-events/${encodeURIComponent(eventId)}`);
    await expectOk(response, "读取 PHARMACY_REVIEW 入站临床事件详情");
    const data = await responseData(response);
    lastDetail = {
      eventId: requireText(textField(data, "eventId"), "入站临床事件详情必须返回 eventId"),
      status: requireText(textField(data, "status"), "入站临床事件详情必须返回状态"),
      errorCode: textField(data, "errorCode"),
      errorClass: textField(data, "errorClass"),
      retryCount: numberField(data, "retryCount"),
      runtimeReleaseId: textField(data, "runtimeReleaseId"),
    };
    if (lastDetail.status === "PROCESSED") {
      return lastDetail;
    }
    if (lastDetail.status === "FAILED") {
      throw new Error(
        `PHARMACY_REVIEW 入站临床事件 ${eventId} 处理失败：${lastDetail.errorCode ?? "UNKNOWN"}`,
      );
    }
    await waitForPollingInterval(500);
  }
  throw new Error(
    `PHARMACY_REVIEW 入站临床事件 ${eventId} 未处理到 PROCESSED，最后状态：${
      lastDetail?.status ?? "UNKNOWN"
    }，错误码：${lastDetail?.errorCode ?? "NONE"}`,
  );
}

async function triggerPharmacyReviewRecommendationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    riskMatrix: { matrixId: string; matrixVersion: string };
    safetyRedline: { redlineId: string; redlineKey: string };
    rule: PharmacyReviewAssetCandidate & { ruleId: string; ruleVersionId: string };
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
  await expect(snapshotButton).toBeVisible({ timeout: 20_000 });
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "开立用药");
  const evaluateResponsePromise = waitForEvaluateResponse(page, snapshot);
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  await expectHttpOk(evaluateResponse, "临床用户从真实前台触发抗菌药物推荐评估");
  const evaluation = await responseData(evaluateResponse);
  const triggerId = requireText(
    textField(evaluation, "triggerId"),
    "推荐评估响应必须返回 triggerId",
  );
  const responseCardIds = arrayField(evaluation, "cards")
    .map((card) => textField(card, "cardId"))
    .filter((cardId): cardId is string => cardId !== null);
  expect(responseCardIds.length, "推荐评估响应必须返回推荐卡").toBeGreaterThan(0);
  const relatedCardIds = await readRecommendationTriggerDiagnose(page, triggerId);
  const recommendation = await findPharmacyReviewRedlineCard(page, relatedCardIds, options);
  const ruleRecommendation = await findPharmacyReviewRuleCard(page, relatedCardIds, {
    triggerId,
    snapshot,
    runtime: options.runtime,
    rule: options.rule,
  });
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    triggerId,
    relatedCardIds,
    ruleRecommendation,
    ...recommendation,
  };
}

async function findPharmacyReviewRedlineCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: { releaseId: string; actionCardAsset: RuntimeReleaseItem };
    riskMatrix: { matrixId: string; matrixVersion: string };
    safetyRedline: { redlineId: string; redlineKey: string };
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
    riskMatrixExplanation: string;
  }> = [];
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
    const redlineExplanation = recordValue(recordField(explanation, "redlineExplanation"));
    const conditionEvidence = arrayField(redlineExplanation, "conditionEvidence");
    const matches =
      textFieldAtPath(detail, "trigger.contextSnapshotId") === options.snapshot.snapshotId &&
      textFieldAtPath(detail, "trigger.runtimeReleaseId") === options.runtime.releaseId &&
      textField(explanation, "matchType") === "CLINICAL_REDLINE" &&
      textField(explanation, "riskMatrixId") === options.riskMatrix.matrixId &&
      textField(explanation, "riskMatrixVersion") === options.riskMatrix.matrixVersion &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "medications[].code" &&
          booleanField(item, "matched") === true,
      ) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "observations[].valueNumeric" &&
          booleanField(item, "matched") === true,
      );
    if (!matches) continue;
    matched.push({
      cardId,
      cardStatus: textFieldAtPath(detail, "card.status"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      cardType: textFieldAtPath(detail, "card.cardType") ?? "MEDICATION",
      requiresPhysicianConfirmation: booleanFieldAtPath(
        detail,
        "card.requiresPhysicianConfirmation",
      ),
      aiGenerated: booleanFieldAtPath(detail, "card.aiGenerated"),
      explanation,
      riskMatrixExplanation: requireText(
        textFieldAtPath(detail, "card.riskMatrixExplanation"),
        "推荐卡必须返回风险矩阵解释",
      ),
    });
  }
  expect(
    matched.map((card) => card.cardId),
    "必须唯一定位本轮抗菌药物红线推荐卡",
  ).toHaveLength(1);
  return matched[0];
}

async function findPharmacyReviewRuleCard(
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
    rule: PharmacyReviewAssetCandidate & { ruleId: string; ruleVersionId: string };
  },
) {
  const matched: Array<{
    cardId: string;
    cardStatus: string | null;
    triggerRuntimeReleaseId: string | null;
    explanation: Record<string, unknown>;
  }> = [];
  for (const cardId of Array.from(new Set(relatedCardIds))) {
    const detailResponse = await getApi(
      page,
      `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
    );
    await expectOk(detailResponse, `规则推荐卡 ${cardId} 详情应可读取`);
    const detail = await responseData(detailResponse);
    const explanation = parseJsonRecord(
      requireText(textFieldAtPath(detail, "card.explanationJson"), "规则推荐卡必须返回解释 JSON"),
    );
    if (!explanation) continue;
    const runtimeRelease = recordValue(recordField(explanation, "runtimeRelease"));
    const ruleExplanation = recordValue(recordField(explanation, "ruleExplanation"));
    const conditionEvidence = arrayField(ruleExplanation, "conditionEvidence");
    const runtimeAssetEvidence = arrayField(ruleExplanation, "runtimeAssetEvidence");
    const matches =
      textFieldAtPath(detail, "trigger.triggerId") === options.triggerId &&
      textFieldAtPath(detail, "trigger.contextSnapshotId") === options.snapshot.snapshotId &&
      textFieldAtPath(detail, "trigger.runtimeReleaseId") === options.runtime.releaseId &&
      textField(explanation, "matchType") === "RULE" &&
      textField(explanation, "ruleId") === options.rule.ruleId &&
      textField(explanation, "ruleCode") === options.rule.assetIdentity &&
      textField(runtimeRelease, "assetVersionId") === options.runtime.ruleAsset.versionId &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "medications[].code" &&
          booleanField(item, "matched") === true,
      ) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "conditions[].code" && booleanField(item, "matched") === true,
      ) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "observations[].valueNumeric" &&
          booleanField(item, "matched") === true,
      ) &&
      runtimeAssetEvidence.some(
        (item) =>
          textField(item, "assetType") === "ACTION_CARD" &&
          textField(item, "assetIdentity") === options.runtime.actionCardAsset.assetIdentity &&
          textField(item, "assetVersion") === options.runtime.actionCardAsset.versionNo &&
          textField(item, "contentHash") === options.runtime.actionCardAsset.contentHash &&
          booleanField(item, "requiresPhysicianConfirmation") === true,
      );
    if (!matches) continue;
    matched.push({
      cardId,
      cardStatus: textFieldAtPath(detail, "card.status"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      explanation,
    });
  }
  expect(
    matched.map((card) => card.cardId),
    "必须唯一定位本轮抗菌药物 RULE 推荐卡",
  ).toHaveLength(1);
  return matched[0];
}

async function completePharmacistAndPhysicianFeedback(
  page: Page,
  recommendation: {
    cardId: string;
    actionCardAsset: RuntimeReleaseItem;
    actionCard: PharmacyReviewActionCardCandidate;
  },
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  const cardId = recommendation.cardId;
  await page.getByLabel("患者或证据线索").fill(cardId);
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const drawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await drawer.getByRole("tab", { name: "药师复核" }).click();
  await drawer
    .getByLabel("药师复核说明")
    .fill("药师已复核抗菌药物审方结果，医生仍需逐条确认；未填写患者明文身份。");
  const reviewResponsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "登记药师复核" }).click();
  const reviewResponse = await reviewResponsePromise;
  await expectHttpOk(reviewResponse, "登记抗菌药物药师复核");
  const pharmacist = await responseData(reviewResponse);
  await drawer.getByRole("button", { name: "Close" }).click();
  await expect(drawer).toBeHidden({ timeout: 20_000 });
  await page.getByLabel("患者或证据线索").fill(cardId);
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const refreshedDrawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await refreshedDrawer.getByRole("tab", { name: /医师反馈/u }).click();
  await refreshedDrawer
    .getByLabel("采纳说明（可选）")
    .fill("医生已结合感染指标和药师意见逐条确认，是否开嘱仍在 HIS 中人工确认。");
  const acceptResponsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await refreshedDrawer.getByRole("button", { name: "确认采纳建议" }).click();
  const acceptResponse = await acceptResponsePromise;
  await expectHttpOk(acceptResponse, "登记抗菌药物医生确认");
  const physician = await responseData(acceptResponse);
  const detailResponse = await getApi(
    page,
    `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
  );
  await expectOk(detailResponse, "回读抗菌药物推荐卡反馈详情");
  const detail = await responseData(detailResponse);
  const persistedFeedback = arrayField(detail, "feedback");
  const pharmacistPersisted = persistedFeedback.find(
    (item) =>
      textField(item, "feedbackId") === textField(pharmacist, "feedbackId") &&
      textField(item, "feedbackType") === "VIEW_SOURCE" &&
      textField(item, "operatorRole") === "PHARMACIST" &&
      textField(item, "reasonCode") === "PHARMACIST_REVIEWED",
  );
  const physicianPersisted = persistedFeedback.find(
    (item) =>
      textField(item, "feedbackId") === textField(physician, "feedbackId") &&
      textField(item, "feedbackType") === "ACCEPT" &&
      textField(item, "operatorRole") === "DOCTOR" &&
      textField(item, "reasonCode") === "CONFIRMED",
  );
  expect(pharmacistPersisted, "药师业务反馈角色必须从推荐详情真实回读").toBeTruthy();
  expect(physicianPersisted, "医生业务反馈角色必须从推荐详情真实回读").toBeTruthy();
  const actionCardEvidence = pharmacyReviewActionCardFeedbackEvidence({
    runtimeAsset: recommendation.actionCardAsset,
    actionCard: recommendation.actionCard,
  });
  return {
    pharmacist: {
      feedbackId: textField(pharmacist, "feedbackId"),
      cardStatus: textField(pharmacist, "cardStatus"),
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: pharmacistPersisted,
    },
    physician: {
      feedbackId: textField(physician, "feedbackId"),
      cardStatus: textField(physician, "cardStatus"),
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: physicianPersisted,
    },
    noAutoOrder: booleanField(actionCardEvidence, "noAutoOrder"),
    actionCardEvidence,
  };
}

function pharmacyReviewActionCardFeedbackEvidence(options: {
  runtimeAsset: RuntimeReleaseItem;
  actionCard: PharmacyReviewActionCardCandidate;
}) {
  const { runtimeAsset, actionCard } = options;
  expect(runtimeAsset.assetIdentity, "反馈闭环 ACTION_CARD 必须来自本轮动作卡").toBe(
    actionCard.assetIdentity,
  );
  expect(runtimeAsset.versionId, "反馈闭环 ACTION_CARD versionId 必须来自当前机构生效版本").toBe(
    actionCard.versionId,
  );
  expect(runtimeAsset.contentHash, "反馈闭环 ACTION_CARD hash 必须来自当前机构生效版本").toBe(
    actionCard.contentHash,
  );
  expect(actionCard.requiresPhysicianConfirmation, "动作卡治理证据必须要求医生确认").toBe(true);
  expect(actionCard.noAutoOrder, "动作卡治理证据必须禁止自动开嘱").toBe(true);
  expect(runtimeAsset.entryState, "反馈闭环必须绑定当前机构生效版本中的 ACTION_CARD").toBe(
    "ACTIVE",
  );
  return {
    assetType: runtimeAsset.assetType,
    assetIdentity: runtimeAsset.assetIdentity,
    versionId: runtimeAsset.versionId,
    versionNo: runtimeAsset.versionNo,
    contentHash: runtimeAsset.contentHash,
    entryState: runtimeAsset.entryState,
    requiresPhysicianConfirmation: actionCard.requiresPhysicianConfirmation,
    noAutoOrder: actionCard.noAutoOrder,
  };
}

async function createAndClosePharmacyReviewRectification(
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
  await ensureReadySession(page, "engine-operator");
  const indicator = await createActiveEvaluationIndicator(page, options.suffix, departmentId);
  const run = await postApi(page, "/engine/evaluation/runs", {
    runCode: `PHARMACY-REVIEW-${options.suffix}`,
    runType: "MANUAL_SAMPLE",
    sourceEventId: options.recommendation.cardId,
    patientId: options.snapshot.patientId,
    encounterId: options.snapshot.encounterId,
    scenarioCode: "S31",
    inputDigest: `pharmacy-review-${options.suffix}`,
    occurredAt: "2026-07-07T00:10:00Z",
    results: [
      {
        indicatorId: indicator.indicatorId,
        subjectType: "PATIENT",
        subjectRefId: options.snapshot.patientId,
        scoreValue: 80,
        resultLevel: "NON_COMPLIANT",
        hitFlag: true,
        evidenceSummary: "抗菌药物审方提示已形成药事治理整改问题。",
        sourceRef: options.recommendation.cardId,
        responsibleDepartmentId: departmentId,
        findings: [
          {
            findingCode: `PHARMACY_REVIEW_ANTIMICROBIAL_${options.suffix}`,
            title: "抗菌药物审方整改代表切片",
            description: "需补充抗菌药物使用依据、感染指标复核和药师审方意见归档。",
            severity: "P1",
            evidenceSummary: "药房审方回传和推荐卡均要求医生确认。",
            responsibleDepartmentId: departmentId,
            dueAt: "2026-07-15T08:30:00Z",
          },
        ],
      },
    ],
  });
  await expectOk(run, "创建药事治理质量问题");
  const issues = await getApi(
    page,
    "/engine/evaluation/issues?severity=P1&status=ASSIGNED&page=1&size=20&sort=createdAt,desc",
  );
  await expectOk(issues, "读取药事治理质量问题");
  const finding = pageItems(await responseData(issues)).find((item) =>
    String(textField(item, "findingCode") ?? "").includes(options.suffix),
  );
  const findingId = requireText(textField(finding, "findingId"), "必须回读本轮药事治理质量问题");
  const assignedFindingStatus = requireText(
    textField(finding, "status"),
    "药事治理质量问题必须回读 ASSIGNED 状态",
  );
  const detail = await getApi(page, `/engine/evaluation/issues/${encodeURIComponent(findingId)}`);
  await expectOk(detail, "读取药事治理质量问题详情");
  const detailData = await responseData(detail);
  const taskId = requireText(
    textFieldAtPath(detailData, "rectificationTask.taskId"),
    "质量问题必须自动派发整改任务",
  );
  const assignedTaskStatus = requireText(
    textFieldAtPath(detailData, "rectificationTask.status") ??
      textFieldAtPath(detailData, "rectificationTask.taskStatus"),
    "药事治理整改任务必须回读初始状态",
  );
  await ensureReadySession(page, "engine-operator");
  const submit = await postApi(
    page,
    `/engine/rectifications/${encodeURIComponent(taskId)}/submit`,
    {
      rectificationSummary: "已补充抗菌药物使用依据、感染指标复核和药师审方意见归档。",
      evidenceRef: `pharmacy-review-antimicrobial-evidence-${options.suffix}`,
    },
  );
  await expectOk(submit, "提交药事治理整改证据");
  const submitData = await responseData(submit);
  const review = await postApi(
    page,
    `/engine/rectifications/${encodeURIComponent(taskId)}/review`,
    {
      decision: "APPROVED",
      comment: "质控复核通过，整改证据与本轮审方推荐卡一致。",
      evidenceRef: `pharmacy-review-antimicrobial-review-${options.suffix}`,
    },
  );
  await expectOk(review, "复核关闭药事治理整改任务");
  const reviewData = await responseData(review);
  const auditEvidence = await readRectificationAuditEvidence(page, { findingId, taskId });
  const permissionEvidence = {
    submittedByRole: "engine-operator",
    reviewedByRole: "engine-operator",
    canonicalFixedRoleVerified:
      auditEvidence.findingAuditReadbackVerified === true &&
      auditEvidence.taskAuditReadbackVerified === true,
    clinicalUserPrivilegeEscalation: false,
  };
  const sixStateEvidence = {
    findingAssignedStatus: assignedFindingStatus,
    taskAssignedStatus: assignedTaskStatus,
    taskSubmittedStatus: textField(submitData, "taskStatus"),
    taskClosedStatus: textField(reviewData, "taskStatus"),
    findingClosedStatus: textField(reviewData, "findingStatus"),
    reviewDecision: "APPROVED",
  };
  return {
    findingId,
    sourceType: "PHARMACY_REVIEW",
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
    submittedEvidenceRef: `pharmacy-review-antimicrobial-evidence-${options.suffix}`,
    reviewDecision: "APPROVED",
    auditEvidence,
    permissionEvidence,
    sixStateEvidence,
  };
}

async function readRectificationAuditEvidence(
  page: Page,
  options: { findingId: string; taskId: string },
) {
  await ensureReadySession(page, "auditor");
  const findingAudit = await waitForAuditEvent(page, {
    resourceType: "quality_finding",
    resourceId: options.findingId,
  });
  const taskAudit = await waitForAuditEvent(page, {
    resourceType: "rectification_task",
    resourceId: options.taskId,
  });
  return {
    findingAuditReadbackVerified: Boolean(findingAudit),
    findingAuditResourceType: textField(findingAudit, "resourceType"),
    findingAuditResourceId: textField(findingAudit, "resourceId"),
    findingAuditActorRole:
      textField(findingAudit, "actorRole") ??
      textField(findingAudit, "role") ??
      textField(findingAudit, "actorRoles"),
    taskAuditReadbackVerified: Boolean(taskAudit),
    taskAuditResourceType: textField(taskAudit, "resourceType"),
    taskAuditResourceId: textField(taskAudit, "resourceId"),
    taskAuditActorRole:
      textField(taskAudit, "actorRole") ??
      textField(taskAudit, "role") ??
      textField(taskAudit, "actorRoles"),
  };
}

async function waitForAuditEvent(
  page: Page,
  options: { resourceType: string; resourceId: string },
) {
  const deadline = Date.now() + 15_000;
  let lastItems: unknown[] = [];
  while (Date.now() < deadline) {
    const response = await getApi(
      page,
      `/large-lists/audit-events/list?resourceType=${encodeURIComponent(
        options.resourceType,
      )}&size=100`,
    );
    await expectOk(response, `回读审计事件 ${options.resourceType}/${options.resourceId}`);
    lastItems = pageItems(await responseData(response));
    const matched = lastItems.find(
      (item) =>
        textField(item, "resourceType") === options.resourceType &&
        textField(item, "resourceId") === options.resourceId,
    );
    if (matched) return matched;
    await waitForPollingInterval(500);
  }
  expect(
    lastItems.map((item) => ({
      resourceType: textField(item, "resourceType"),
      resourceId: textField(item, "resourceId"),
    })),
    `等待审计事件 ${options.resourceType}/${options.resourceId}`,
  ).toContainEqual({ resourceType: options.resourceType, resourceId: options.resourceId });
  return null;
}

async function createActiveEvaluationIndicator(page: Page, suffix: string, departmentId: string) {
  const indicatorCode = `PHARMACY_REVIEW_ANTIMICROBIAL_${suffix}`;
  const created = await postApi(page, "/engine/evaluation/indicators", {
    indicatorCode,
    name: `抗菌药物审方整改指标 ${suffix}`,
    subjectType: "PATIENT",
    denominatorDefinition: JSON.stringify({
      all: [
        {
          fact: "recommendation.matchType",
          operator: "equals",
          value: "ANTIMICROBIAL_RESTRICTION",
        },
        { fact: "conditions[].code", operator: "equals", value: "J18.900" },
      ],
    }),
    numeratorDefinition: JSON.stringify({
      all: [
        { fact: "pharmacyReview.reviewStatus", operator: "equals", value: "APPROVED" },
        { fact: "observation.pct", operator: "gte", value: 0 },
      ],
    }),
    exclusionDefinition: null,
    scoringDefinition: "命中即需整改",
    timeWindow: "本地上线演练窗口",
    organizationScope: "本地上线演练医院",
    responsibleDepartmentId: departmentId,
    sourceRef: "local-e2e:pharmacy-review-antimicrobial",
  });
  await expectOk(created, "创建药事治理评价指标");
  const indicatorId = requireText(
    textField(await responseData(created), "indicatorId"),
    "评价指标必须返回 indicatorId",
  );
  for (const action of ["submit", "publish", "gray", "activate"]) {
    const response = await postApi(
      page,
      `/engine/evaluation/indicators/${encodeURIComponent(indicatorId)}/${action}`,
      {
        reason: `S31 药事治理代表切片指标 ${action}`,
        publishEvidence: {
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            summary: "S31 药事治理代表切片指标质量门已通过",
          },
        },
      },
    );
    await expectOk(response, `评价指标 ${action}`);
  }
  return { indicatorId, indicatorCode };
}

async function attachPharmacyReviewAntimicrobialEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: PharmacyReviewApiEvidence;
    adapter: unknown;
    webhookSignature: unknown;
    terminologyGate: unknown;
    riskMatrix: unknown;
    safetyRedline: unknown;
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
    outboundReview: unknown;
    inboundReview: unknown;
    clinicalTrigger: unknown;
    recommendation: unknown;
    ruleRecommendation: unknown;
    feedback: unknown;
    qualityRectification: unknown;
    observedStages: Set<string>;
  },
) {
  for (const stages of Object.values(requiredStages)) {
    for (const stage of stages) {
      expect(evidence.observedStages.has(stage), `缺少 S18/S31 阶段：${stage}`).toBe(true);
    }
  }
  await testInfo.attach("pharmacy-review-antimicrobial-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S18", "S31"],
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
          "药房审方与抗菌药物治理代表切片：PHARMACY_REVIEW 双向审方、抗菌药物风险推荐、药师/医生人工确认和 S31 整改闭环，不代表完整药事治理、完整抗菌药物分级管理或第三方药房审方系统族完整覆盖。",
        standardPatientResourceConsumerMatrix: standardPatientResourceConsumerMatrix([
          {
            resourceType: "Condition",
            resourcePath: "clinicalContext.resources.conditions[0]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.conditions[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "PHARMACY_REVIEW_RULE",
            consumerEvidencePaths: [
              "ruleRecommendation.explanation.ruleExplanation.conditionEvidence[1]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["qualityRectification.findingId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
          {
            resourceType: "Observation",
            resourcePath: "clinicalContext.resources.observations[1]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.observations[1].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "PHARMACY_REVIEW_RULE",
            consumerEvidencePaths: [
              "recommendation.ruleRecommendation.explanation.ruleExplanation.conditionEvidence[2]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["qualityRectification.findingId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
        ]),
        pharmacyReviewConsumerSlice: {
          systemFamilyCode: "PHARMACY_REVIEW",
          familyName: "第三方药房审方系统族",
          consumer: "ANTIMICROBIAL_REVIEW_RECOMMENDATION_RECTIFICATION",
          canonicalResources: [
            "Patient",
            "Encounter",
            "Medication",
            "AllergyIntolerance",
            "Condition",
            "Observation",
          ],
          sourceSystems: ["MEDKERNEL_FRONTDESK", "PHARMACY_REVIEW"],
          adapterCreatedThroughRealService:
            evidence.apiEvidence.pharmacyReviewAdapterCreatedThroughRealService,
          webhookCreatedThroughRealService:
            evidence.apiEvidence.pharmacyReviewWebhookCreatedThroughRealService,
          signaturePreviewGenerated: evidence.apiEvidence.webhookSignaturePreviewGenerated,
          outboundNotConnectedVerified: true,
          inboundReviewVerified: evidence.apiEvidence.inboundReviewAccepted,
          signedInboundProcessedVerified: true,
          clinicalEventProcessedVerified: true,
          terminologyMappingVerified: true,
          contextSnapshotReadbackVerified: evidence.apiEvidence.contextSnapshotCreatedFromFrontdesk,
          recommendationConsumerVerified:
            evidence.apiEvidence.clinicalEvaluationTriggeredFromFrontdesk,
          ruleConsumerVerified: evidence.apiEvidence.clinicalEvaluationTriggeredFromFrontdesk,
          pharmacistReviewVerified:
            evidence.apiEvidence.pharmacistReviewRecordedWithoutClosingPhysicianConfirmation,
          physicianConfirmationVerified: evidence.apiEvidence.physicianConfirmationRecorded,
          rectificationClosedVerified:
            evidence.apiEvidence.qualityRectificationSubmittedAndReviewed,
          auditVerified: true,
          permissionVerified: true,
          sixStateBoundaryVerified: true,
          requiresPhysicianConfirmation: true,
          noAutoOrder: true,
          noExternalSuccessClaim: true,
          aiGenerated: false,
          patientId: textField(evidence.clinicalContext, "patientId"),
          encounterId: requireText(
            textField(evidence.clinicalContext, "encounterId"),
            "PHARMACY_REVIEW 消费者切片必须绑定 encounterId",
          ),
          contextSnapshotId: textField(evidence.clinicalContext, "contextSnapshotId"),
          runtimeReleaseId: evidence.runtime.releaseId,
          adapterId: textField(evidence.adapter, "adapterId"),
          webhookId: textField(evidence.webhookSignature, "webhookId"),
          clinicalEventId: textFieldAtPath(evidence.inboundReview, "clinicalEvent.eventId"),
          recommendationCardId: textField(evidence.recommendation, "cardId"),
          ruleRecommendationCardId: textField(evidence.ruleRecommendation, "cardId"),
          actionCardAssetIdentity: textField(evidence.actionCard, "assetIdentity"),
          ruleAssetIdentity: textField(evidence.rule, "assetIdentity"),
          pharmacistFeedbackId: textFieldAtPath(evidence.feedback, "pharmacist.feedbackId"),
          physicianFeedbackId: textFieldAtPath(evidence.feedback, "physician.feedbackId"),
          findingId: textField(evidence.qualityRectification, "findingId"),
          taskId: textField(evidence.qualityRectification, "taskId"),
          outboundPath: "outboundReview",
          inboundPath: "inboundReview",
          feedbackPath: "feedback",
          rectificationPath: "qualityRectification",
          scopeStatement:
            "第三方药房审方系统族真实消费者代表切片：真实前台用 Patient、Encounter、Medication、AllergyIntolerance、Condition、Observation 标准资源驱动 PHARMACY_REVIEW 抗菌药物审方断连补偿、签名入站审方结果、当前机构生效版本推荐消费、药师复核、医生人工确认和药事治理整改闭环；不代表完整药房审方系统族覆盖，不代表完整药事治理，不代表完整抗菌药物分级管理，不代表真实外部药房审方成功联通，不代表自动开嘱，不代表完整 S18，不代表完整 S31，不代表完整第三方系统族覆盖，不代表完整 S0-S40，不代表完整上线验收。",
        },
        apiEvidence: evidence.apiEvidence,
        adapter: evidence.adapter,
        webhookSignature: evidence.webhookSignature,
        terminologyGate: evidence.terminologyGate,
        riskMatrix: evidence.riskMatrix,
        safetyRedline: evidence.safetyRedline,
        actionCard: evidence.actionCard,
        ruleAsset: evidence.rule,
        runtime: {
          releaseId: evidence.runtime.releaseId,
          revisionNo: evidence.runtime.revisionNo,
          manifestSha256: evidence.runtime.manifestSha256,
          assets: evidence.runtime.assets,
          terminologyAsset: evidence.runtime.terminologyAsset,
          safetyAsset: evidence.runtime.safetyAsset,
          cdssRiskAsset: evidence.runtime.cdssRiskAsset,
          ruleAsset: evidence.runtime.ruleAsset,
          actionCardAsset: evidence.runtime.actionCardAsset,
        },
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        outboundReview: evidence.outboundReview,
        inboundReview: evidence.inboundReview,
        clinicalTrigger: evidence.clinicalTrigger,
        recommendation: evidence.recommendation,
        ruleRecommendation: evidence.ruleRecommendation,
        feedback: evidence.feedback,
        qualityRectification: evidence.qualityRectification,
        pharmacyHighRiskGovernanceEvidence: {
          source: "PHARMACY_REVIEW_ANTIMICROBIAL_HIGH_RISK_GOVERNANCE_REVIEW",
          runtimeReleaseId: evidence.runtime.releaseId,
          recommendationCardId: textField(evidence.recommendation, "cardId"),
          ruleRecommendationCardId: textField(evidence.ruleRecommendation, "cardId"),
          pharmacistFeedbackId: textFieldAtPath(evidence.feedback, "pharmacist.feedbackId"),
          physicianFeedbackId: textFieldAtPath(evidence.feedback, "physician.feedbackId"),
          findingId: textField(evidence.qualityRectification, "findingId"),
          taskId: textField(evidence.qualityRectification, "taskId"),
          requiresPhysicianConfirmation: true,
          noAutoOrder: true,
          noExternalSuccessClaim: true,
          aiGenerated: false,
        },
        scenarioConditionEvidence: [
          {
            code: "S18__HIGH_RISK",
            scenarioCode: "S18",
            condition: "HIGH_RISK",
            source: "PHARMACY_REVIEW_ANTIMICROBIAL_CRITICAL_MANUAL_CONFIRMATION",
            evidence: [
              "抗菌药物 SAFETY 红线和风险矩阵均为 CRITICAL",
              "推荐卡要求医生确认且药师复核不关闭医生确认链路",
              "医生逐条确认采纳并保持 noAutoOrder=true",
            ],
          },
          {
            code: "S31__DEGRADATION",
            scenarioCode: "S31",
            condition: "DEGRADATION",
            source: "PHARMACY_REVIEW_OUTBOUND_NOT_CONNECTED",
            evidence: [
              "PHARMACY_REVIEW 出站审方请求收敛到 NOT_CONNECTED",
              "断连补偿不阻断本地推荐、药师复核和医生确认主链路",
            ],
          },
          {
            code: "S31__HIGH_RISK",
            scenarioCode: "S31",
            condition: "HIGH_RISK",
            source: "PHARMACY_REVIEW_ANTIMICROBIAL_HIGH_RISK_GOVERNANCE_REVIEW",
            evidence: [
              "PHARMACY_REVIEW 入站审方结果、抗菌药物红线和风险矩阵均要求医生确认",
              "药师复核不关闭医生确认链路，医生人工确认且 noAutoOrder=true",
              "本轮药事治理整改审计、权限、六态边界成立",
            ],
          },
          {
            code: "S31__ABNORMAL",
            scenarioCode: "S31",
            condition: "ABNORMAL",
            source: "PHARMACY_REVIEW_RECTIFICATION_REVIEW",
            evidence: ["药事治理问题形成 P1 整改任务", "固定职责账号提交并复核关闭整改"],
          },
        ],
        scenarioEvidence: [
          { code: "S18", observedStages: requiredStages.S18 },
          { code: "S31", observedStages: requiredStages.S31 },
        ],
      },
      null,
      2,
    ),
  });
}

function recordStage(stages: Set<string>, stage: string) {
  stages.add(stage);
}

function assertSnapshotContainsPharmacyReviewFacts(resources: Record<string, unknown>) {
  expect(
    arrayField(resources, "medications").some((item) => textField(item, "code") === "J01C"),
    "上下文必须包含 Medication J01C",
  ).toBe(true);
  expect(
    arrayField(resources, "allergyIntolerances").some((item) => textField(item, "code") === "J01C"),
    "上下文必须包含 AllergyIntolerance J01C",
  ).toBe(true);
  expect(
    arrayField(resources, "conditions").some((item) => textField(item, "code")),
    "上下文必须包含 Condition",
  ).toBe(true);
  expect(
    arrayField(resources, "observations").some(
      (item) =>
        textField(item, "code") === "PCT" && typeof numberField(item, "valueNumeric") === "number",
    ),
    "上下文必须包含 Observation PCT 监测指标",
  ).toBe(true);
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
          payload.triggerType === "medication-prescribe"
        );
      } catch {
        return false;
      }
    },
    { timeout: 30_000 },
  );
}

async function readRecommendationTriggerDiagnose(page: Page, triggerId: string) {
  const diagnoseResponse = await getApi(
    page,
    `/engine/recommendations/triggers/${encodeURIComponent(triggerId)}/diagnose`,
  );
  await expectOk(diagnoseResponse, "推荐触发诊断应可由真实服务读取");
  const diagnose = await responseData(diagnoseResponse);
  const relatedCardIds = arrayFieldAtPath(diagnose, "relatedEntities.cards").filter(
    (value): value is string => typeof value === "string" && value.trim().length > 0,
  );
  expect(relatedCardIds.length, "推荐触发诊断必须返回本次触发关联推荐卡").toBeGreaterThan(0);
  return relatedCardIds;
}

async function localRehearsalHospitalId(page: Page) {
  const hospitals = await getApi(
    page,
    "/engine/org/org-units?keyword=本地上线演练医院&page=1&size=20",
  );
  await expectOk(hospitals, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(hospitals)).find(
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
  const activeDepartment = pageItems(await responseData(existing)).find(
    (item) => textField(item, "level") === "DEPARTMENT" && textField(item, "status") === "ACTIVE",
  );
  const existingId = textField(activeDepartment, "id");
  if (existingId) {
    return existingId;
  }

  await ensureReadySession(page, "platform-admin");
  const created = await postApi(page, "/engine/org/org-units", {
    code: `E2E-PHARMACY-QC-${suffix.toUpperCase()}`,
    name: `上线演练药事质控科${suffix.slice(-4)}`,
    level: "DEPARTMENT",
    parentId: hospitalId,
    status: "ACTIVE",
  });
  await expectOk(created, "创建本地上线演练药事质控科");
  const department = await responseData(created);
  expect(textField(department, "level"), "药事治理责任组织必须是科室").toBe("DEPARTMENT");
  return requireText(textField(department, "id"), "药事治理责任科室必须返回 id");
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function runtimeSelection(candidate: {
  assetType: string;
  assetIdentity: string;
  versionId: string;
}) {
  return {
    assetType: candidate.assetType,
    assetIdentity: candidate.assetIdentity,
    versionId: candidate.versionId,
  };
}

function assertRuntimeContainsAsset(
  runtime: RuntimeReleaseDetail,
  candidate: PharmacyReviewAssetCandidate,
) {
  const asset = (runtime.items ?? []).find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(
    asset,
    `机构生效版本必须包含本轮 ${candidate.assetType} ${candidate.assetIdentity}`,
  ).toBeTruthy();
  expect(asset?.versionNo, `${candidate.assetType} runtime 清单必须返回版本号`).toBe(
    candidate.versionNo,
  );
  expect(asset?.contentHash, `${candidate.assetType} runtime 清单必须返回正文 hash`).toBe(
    candidate.contentHash,
  );
  return asset as RuntimeReleaseItem;
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  if (
    await dialog
      .getByText(optionText, { exact: true })
      .isVisible()
      .catch(() => false)
  )
    return;
  const field = dialog.getByLabel(label);
  const selectSelector = field
    .locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')]",
    )
    .first()
    .locator(".ant-select-selector")
    .first();
  if (await selectSelector.isVisible().catch(() => false)) {
    await selectSelector.click();
  } else {
    await field.click();
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, `${label} 下拉应展开`).toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`, "u") })
    .first();
  await expect(option, `${label} 应存在选项 ${optionText}`).toBeVisible({ timeout: 10_000 });
  await option.click();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

async function expectHttpOk(response: APIResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
}

function apiContext(subject: string, step: string) {
  return {
    request_id: `req-pharmacy-review-${step}-${subject}`,
    trace_id: `trace-pharmacy-review-${step}-${subject}`,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function signHmacSha256(secret: string, timestamp: string, payload: unknown) {
  return createHmac("sha256", secret)
    .update(`${timestamp}.${JSON.stringify(payload)}`)
    .digest("hex");
}

function currentEpochSeconds() {
  return Math.floor(Date.now() / 1000).toString();
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

function textFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function numberFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function booleanField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "boolean" ? raw : null;
}

function booleanFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "boolean" ? raw : null;
}

function valueAtPath(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, part) => {
    if (current == null) return undefined;
    const match = /^([^[\]]+)(?:\[(\d+)\])?$/u.exec(part);
    if (!match) return undefined;
    const record = recordValue(current);
    if (!record) return undefined;
    let next: unknown = record[match[1]];
    if (match[2] !== undefined) {
      if (!Array.isArray(next)) return undefined;
      next = next[Number(match[2])];
    }
    return next;
  }, value);
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function requireText(value: string | null, message: string) {
  expect(value, message).toBeTruthy();
  return value ?? "";
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
