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
  responseData,
  arrayData,
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

type RuntimeRollbackNegativeEvidence = {
  rollbackPosted: boolean;
  currentRuntimeReadbackVerified: boolean;
  runtimeConsumerReadbackVerified: boolean;
  consumer: string;
  consumerProbeMatchedRemovedAssets: boolean;
  removedAssets: RuntimeAssetSelection[];
  currentRuntime: {
    releaseId: string;
    revisionNo: number;
    manifestSha256: string;
    assets: RuntimeReleaseItem[];
  };
  runtimeConsumer: {
    contractVersion: "v1";
    releaseId: string;
    revisionNo: number;
    manifestSha256: string;
    assets: RuntimeReleaseItem[];
  };
};

type MedicationSafetyAssetCandidate = {
  assetType: "SAFETY" | "CDSS_RISK" | "RULE";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
  sourceLayer?: string;
};

type RuntimeAssetCandidate = {
  assetType: string;
  assetIdentity: string;
  versionId: string;
};

type MedicationSafetyTerminologyGate = {
  assetType: "TERMINOLOGY";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
  standardSystem: "ATC";
  standardCode: "J01C";
  localCode: string;
  sourceSystem: string;
  category: "DRUG";
  mappingId: number;
  standardTermId: number | null;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  resources: Record<string, unknown>;
};

type MedicationSafetyApiEvidence = {
  riskMatrixCreatedFromRealService: boolean;
  safetyRedlineDraftCreated: boolean;
  safetyRedlineDryRunSubmitted: boolean;
  safetyAssetPromoted: boolean;
  terminologyCoverageGateActivated: boolean;
  ruleCreatedForMedicationPrescribe: boolean;
  ruleRuntimeCandidateResolvedFromCurrentHospital: boolean;
  runtimeActivatedWithSafetyRiskAndRule: boolean;
  contextSnapshotCreatedFromFrontdesk: boolean;
  clinicalEvaluationTriggeredFromFrontdesk: boolean;
  pharmacistReviewRecordedWithoutClosingPhysicianConfirmation: boolean;
  physicianConfirmationRecorded: boolean;
};

const requiredStages = [
  "运营员创建真实 CDSS_RISK 风险矩阵",
  "运营员创建药物过敏禁忌 SAFETY 红线草稿",
  "运营员提交静默试运行并上线 SAFETY 资产",
  "运营员补齐 ATC:J01C 术语映射并激活到当前机构生效版本",
  "运营员创建 medication-prescribe 规则资产",
  "当前机构生效版本包含 SAFETY、CDSS_RISK 与 RULE",
  "临床用户从患者 360 建立 Medication 与 AllergyIntolerance 上下文",
  "临床用户从真实前台开立用药触发推荐评估",
  "药师登记红线复核且不关闭医生确认链路",
  "医生逐条确认采纳，系统不自动开嘱",
] as const;

test.describe("用药安全代表切片真实前台闭环", () => {
  test("临床用户与运营员围绕药物过敏红线完成当前机构生效版本推荐与人工确认闭环", async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const observedStages = new Set<string>();
    const apiEvidence = createApiEvidence();

    await ensureReadySession(page, "engine-operator");
    const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;
    const hospitalId = await localRehearsalHospitalId(page);
    const riskMatrix = await createMedicationSafetyRiskMatrix(page, suffix);
    apiEvidence.riskMatrixCreatedFromRealService = true;
    recordStage(observedStages, "运营员创建真实 CDSS_RISK 风险矩阵");

    const safetyRedline = await createPromotedMedicationAllergyRedline(page, {
      suffix,
      riskMatrix,
    });
    apiEvidence.safetyRedlineDraftCreated = true;
    apiEvidence.safetyRedlineDryRunSubmitted = true;
    apiEvidence.safetyAssetPromoted = true;
    recordStage(observedStages, "运营员创建药物过敏禁忌 SAFETY 红线草稿");
    recordStage(observedStages, "运营员提交静默试运行并上线 SAFETY 资产");

    const terminologyGate = await createMedicationSafetyTerminologyGate(page, suffix);
    const terminologyRuntime = await activateRuntimeWithMedicationSafetyAssets(page, {
      hospitalId,
      extraAssets: [runtimeSelection(terminologyGate)],
    });
    assertRuntimeContainsTerminology(terminologyRuntime, terminologyGate);
    apiEvidence.terminologyCoverageGateActivated = true;
    recordStage(observedStages, "运营员补齐 ATC:J01C 术语映射并激活到当前机构生效版本");

    const rulePositiveSnapshot = await createMedicationSafetyContextFromFrontdesk(
      page,
      `${suffix}-RULE-POS`,
    );
    const ruleNegativeSnapshot = await createMedicationSafetyContextFromFrontdesk(
      page,
      `${suffix}-RULE-NEG`,
      { allergyText: "头孢菌素：呼吸困难" },
    );
    await ensureReadySession(page, "engine-operator");
    const rule = await createAndPublishMedicationSafetyRule(page, suffix, {
      positiveContextSnapshotId: rulePositiveSnapshot.snapshotId,
      negativeContextSnapshotId: ruleNegativeSnapshot.snapshotId,
    });
    apiEvidence.ruleCreatedForMedicationPrescribe = true;
    recordStage(observedStages, "运营员创建 medication-prescribe 规则资产");

    const candidates = await readMedicationSafetyRuntimeCandidates(page, hospitalId, {
      safetyIdentity: safetyRedline.assetIdentity,
      ruleIdentity: rule.assetIdentity,
    });
    apiEvidence.ruleRuntimeCandidateResolvedFromCurrentHospital = true;
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
    const runtime = await activateRuntimeWithMedicationSafetyAssets(page, {
      hospitalId,
      safety: candidates.safety,
      cdssRisk: candidates.cdssRisk,
      rule: candidates.rule,
      extraAssets: [runtimeSelection(terminologyGate)],
    });
    expect(runtime.safetyAsset, "最终机构生效版本必须包含本轮 SAFETY 资产").toBeTruthy();
    expect(runtime.cdssRiskAsset, "最终机构生效版本必须包含本轮 CDSS_RISK 资产").toBeTruthy();
    expect(runtime.ruleAsset, "最终机构生效版本必须包含本轮 RULE 资产").toBeTruthy();
    apiEvidence.runtimeActivatedWithSafetyRiskAndRule = true;
    recordStage(observedStages, "当前机构生效版本包含 SAFETY、CDSS_RISK 与 RULE");

    const snapshot = await createMedicationSafetyContextFromFrontdesk(page, suffix);
    expect(
      snapshot.runtimeReleaseId,
      "用药安全上下文必须绑定包含本轮 SAFETY/CDSS_RISK/RULE 的当前机构生效版本",
    ).toBe(runtime.releaseId);
    assertSnapshotContainsMedicationAllergy(snapshot.resources);
    assertSnapshotContainsSpecialPopulations(snapshot.resources);
    apiEvidence.contextSnapshotCreatedFromFrontdesk = true;
    recordStage(observedStages, "临床用户从患者 360 建立 Medication 与 AllergyIntolerance 上下文");

    const recommendation = await triggerMedicationSafetyRecommendationFromFrontdesk(page, {
      snapshot,
      runtime,
      safety: candidates.safety,
      cdssRisk: candidates.cdssRisk,
      rule: ruleEvidence,
      riskMatrix: riskMatrixEvidence,
    });
    apiEvidence.clinicalEvaluationTriggeredFromFrontdesk = true;
    recordStage(observedStages, "临床用户从真实前台开立用药触发推荐评估");

    const feedback = await completePharmacistAndPhysicianFeedback(page, recommendation.cardId);
    expect(feedback.pharmacist.cardStatus, "药师复核不能关闭医生待确认链路").toBe("PENDING");
    expect(feedback.physician.cardStatus, "医生确认后推荐卡才进入采纳状态").toBe("ACCEPTED");
    apiEvidence.pharmacistReviewRecordedWithoutClosingPhysicianConfirmation = true;
    apiEvidence.physicianConfirmationRecorded = true;
    recordStage(observedStages, "药师登记红线复核且不关闭医生确认链路");
    recordStage(observedStages, "医生逐条确认采纳，系统不自动开嘱");
    const rollbackNegativeEvidence = await rollbackRuntimeAndAssertAssetsRemoved(page, {
      hospitalId,
      targetReleaseId: runtime.previousReleaseId,
      consumer: "MEDICATION_SAFETY_RULE",
      removedAssets: [
        runtimeSelection(candidates.safety),
        runtimeSelection(candidates.cdssRisk),
        runtimeSelection(candidates.rule),
      ],
    });

    await attachMedicationSafetyEvidence(testInfo, {
      apiEvidence,
      riskMatrix: riskMatrixEvidence,
      safetyRedline: safetyRedlineEvidence,
      rule: ruleEvidence,
      terminologyGate,
      runtime,
      activationRequest: runtime.activationRequest,
      clinicalContext: {
        patientId: snapshot.patientId,
        contextSnapshotId: snapshot.snapshotId,
        runtimeReleaseId: snapshot.runtimeReleaseId,
        encounterId: snapshot.encounterId,
        specialPopulations: ["PREGNANCY", "GERIATRIC"],
        resources: snapshot.resources,
      },
      clinicalTrigger: {
        triggerId: recommendation.triggerId,
        contextSnapshotId: snapshot.snapshotId,
        runtimeReleaseId: snapshot.runtimeReleaseId,
        cardId: recommendation.cardId,
        relatedCardIds: recommendation.relatedCardIds,
      },
      recommendation,
      ruleRecommendation: recommendation.ruleRecommendation,
      feedback,
      rollbackNegativeEvidence,
      observedStages,
    });
  });
});

function createApiEvidence(): MedicationSafetyApiEvidence {
  return {
    riskMatrixCreatedFromRealService: false,
    safetyRedlineDraftCreated: false,
    safetyRedlineDryRunSubmitted: false,
    safetyAssetPromoted: false,
    terminologyCoverageGateActivated: false,
    ruleCreatedForMedicationPrescribe: false,
    ruleRuntimeCandidateResolvedFromCurrentHospital: false,
    runtimeActivatedWithSafetyRiskAndRule: false,
    contextSnapshotCreatedFromFrontdesk: false,
    clinicalEvaluationTriggeredFromFrontdesk: false,
    pharmacistReviewRecordedWithoutClosingPhysicianConfirmation: false,
    physicianConfirmationRecorded: false,
  };
}

async function createMedicationSafetyRiskMatrix(page: Page, suffix: string) {
  const matrixVersion = `med-safety-${suffix}`;
  const response = await putApi(page, "/engine/cdss/risk-matrix", {
    matrixVersion,
    changeReason: "P0 用药安全代表切片：验证当前机构生效版本 CDSS_RISK 风险矩阵消费。",
    status: "ACTIVE",
    entries: [
      {
        triggerPoint: "medication-prescribe",
        severityLevel: "CRITICAL",
        automationLevel: "INFORM_ONLY",
        riskLevel: "CRITICAL",
        reviewRequirement: "PHYSICIAN_CONFIRMATION",
        silentRunHours: 168,
        releaseGate: "OPT04_REDLINE_SILENT_TRIAL",
        autoExecutionAllowed: false,
        samdClassification: "NMPA_RESERVED",
        regulatoryEvidence: "NOT_ASSESSED",
        explanation: "药物过敏禁忌红线只生成必须医师确认的建议，不自动开嘱。",
      },
    ],
  });
  await expectOk(response, "创建 P0 用药安全 CDSS_RISK 风险矩阵");
  const rule = arrayField(await responseData(response), "rules").find(
    (item) =>
      textField(item, "triggerPoint") === "medication-prescribe" &&
      textField(item, "severityLevel") === "CRITICAL" &&
      textField(item, "automationLevel") === "INFORM_ONLY" &&
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

async function createPromotedMedicationAllergyRedline(
  page: Page,
  options: {
    suffix: string;
    riskMatrix: {
      matrixId: string;
      matrixVersion: string;
    };
  },
) {
  const redlineId = `redline-med-allergy-${options.suffix.toLowerCase()}`;
  const redlineKey = `RDL-MED-ALLERGY-${options.suffix}`;
  const redlineVersion = "2026.1";
  const conditionDsl = JSON.stringify({
    all: [{ fact: "allergyIntolerances[].code", operator: "contains", value: "J01C" }],
  });
  const draft = await postApi(page, "/engine/safety/redlines", {
    redlineId,
    category: "SPECIAL_POPULATION_CONTRAINDICATION",
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
    releaseGate: "OPT04_REDLINE_SILENT_TRIAL",
    title: `P0 用药过敏禁忌红线 ${options.suffix}`,
    clinicalHazard:
      "患者存在青霉素类过敏史时，开立相关用药必须触发红线并由医生逐条确认；系统不得自动开嘱。",
    conditionDsl,
    evidenceSource: "P0 用药安全代表切片演练证据",
    evidenceReference: "evidence://local-e2e/medication-safety/allergy-redline",
    sourceVersionId: null,
    lowerTenantOverrideAllowed: false,
  });
  await expectOk(draft, "创建 P0 用药过敏禁忌 SAFETY 红线草稿");
  const draftData = await responseData(draft);

  const dryRun = await postApi(page, "/engine/safety/redlines:dry-run", {
    redlineId,
    observedFrom: "2026-05-26T00:00:00Z",
    observedTo: "2026-06-03T00:00:00Z",
    evaluatedCaseCount: 1200,
    matchedCaseCount: 24,
    falsePositiveCaseCount: 1,
    safetyIncidentCount: 0,
    evidenceReference: "evidence://local-e2e/medication-safety/allergy-redline/silent-run",
    operatorNote: "P0 用药安全代表切片：静默试运行达标，不自动开嘱。",
  });
  await expectOk(dryRun, "提交 P0 用药过敏禁忌 SAFETY 静默试运行");
  const trialId = requireText(
    textField(await responseData(dryRun), "trialId"),
    "静默试运行必须返回 trialId",
  );

  const promoted = await postApi(page, "/engine/safety/redlines:promote", {
    redlineId,
    trialId,
    expectedRedlineVersion: redlineVersion,
    promotionReason: "P0 用药安全代表切片：静默试运行达标后纳入 SAFETY 资产候选。",
  });
  await expectOk(promoted, "上线 P0 用药过敏禁忌 SAFETY 资产");
  const promotedData = await responseData(promoted);
  return {
    assetType: "SAFETY" as const,
    assetIdentity: `SAFETY.${redlineKey}`,
    redlineId,
    redlineKey,
    redlineVersion,
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
    title: requireText(textField(draftData, "title"), "红线草稿响应必须返回标题"),
  };
}

async function createMedicationSafetyTerminologyGate(
  page: Page,
  suffix: string,
): Promise<MedicationSafetyTerminologyGate> {
  await ensureReadySession(page, "engine-operator");
  const standard = await postApi(page, "/engine/terminology/terms/standard", {
    ...terminologyApiContext(suffix, "standard"),
    standardSystem: "ATC",
    termCode: "J01C",
    category: "DRUG",
    displayName: "青霉素类",
    normalizedName: "青霉素类|J01C|PENICILLIN",
    versionNo: "2026",
    sourceVersionId: null,
    evidenceText: "P0 用药安全代表切片：规则发布门禁所需 ATC 标准药品术语。",
  });
  await expectOk(standard, "登记 P0 用药安全 ATC 标准术语");
  const standardData = await responseData(standard);
  const standardTermId = numberField(standardData, "id");

  const localCode = `J01C-${suffix}`;
  const sourceSystem = "MEDKERNEL_FRONTDESK";
  const local = await postApi(page, "/engine/terminology/terms/local", {
    ...terminologyApiContext(suffix, "local"),
    sourceSystem,
    localCode,
    category: "DRUG",
    localName: "青霉素类",
    normalizedName: "青霉素类|J01C|PENICILLIN",
    local_department_id: null,
  });
  await expectOk(local, "登记 P0 用药安全前台院内药品术语");

  const coverageBeforeCandidate = await readMedicationSafetyTerminologyCoverage(page);
  const mapping =
    coverageBeforeCandidate.status === "COVERED"
      ? await readConfirmedMedicationSafetyTermMapping(page, { sourceSystem, standardTermId })
      : await generateAndConfirmMedicationSafetyTermMapping(page, {
          suffix,
          sourceSystem,
          localCode,
          standardTermId: typeof standardTermId === "number" ? standardTermId : null,
        });

  const assetIdentity = `TERM.DRUG.MEDICATION.SAFETY.${suffix}`;
  const draft = await postApi(page, "/engine/terminology/assets/drafts", {
    assetIdentity,
    name: `P0 用药安全术语映射 ${suffix}`,
    scopeLevel: "TENANT",
    scopeCode: resolvedTenantIdFor("engine-operator"),
  });
  await expectOk(draft, "生成 P0 用药安全术语资产草稿");
  const draftData = await responseData(draft);
  return {
    assetType: "TERMINOLOGY",
    assetIdentity: requireText(textField(draftData, "assetIdentity"), "术语资产草稿必须返回身份"),
    versionId: requireText(textField(draftData, "versionId"), "术语资产草稿必须返回 versionId"),
    versionNo: requireText(textField(draftData, "versionNo"), "术语资产草稿必须返回 versionNo"),
    contentHash: requireText(
      textField(draftData, "contentHash"),
      "术语资产草稿必须返回 contentHash",
    ),
    standardSystem: "ATC",
    standardCode: "J01C",
    localCode,
    sourceSystem,
    category: "DRUG",
    mappingId: mapping.mappingId,
    standardTermId:
      mapping.standardTermId ?? (typeof standardTermId === "number" ? standardTermId : null),
  };
}

async function readMedicationSafetyTerminologyCoverage(page: Page) {
  const response = await getApi(
    page,
    "/engine/terminology/mappings/coverage?standardSystem=ATC&codes=J01C",
  );
  await expectOk(response, "读取 P0 用药安全术语覆盖状态");
  const item = arrayData(await responseData(response)).find(
    (value) => textField(value, "code") === "J01C",
  );
  const status = requireText(textField(item, "status"), "P0 用药安全术语覆盖必须返回状态");
  expect(["COVERED", "UNMAPPED", "NO_STANDARD_TERM"]).toContain(status);
  return {
    status,
    mappedLocalCount: numberField(item, "mappedLocalCount") ?? 0,
  };
}

async function readConfirmedMedicationSafetyTermMapping(
  page: Page,
  options: { sourceSystem: string; standardTermId?: number },
) {
  const response = await getApi(
    page,
    "/engine/terminology/mappings?category=DRUG&status=CONFIRMED&page=1&size=100",
  );
  await expectOk(response, "读取 P0 用药安全已确认术语映射");
  const mappings = pageItems(await responseData(response)).filter(
    (item) => textField(item, "status") === "CONFIRMED" && textField(item, "category") === "DRUG",
  );
  const mapping =
    (typeof options.standardTermId === "number"
      ? mappings.find((item) => numberField(item, "standardTermId") === options.standardTermId)
      : null) ??
    mappings.find((item) => textField(item, "sourceSystem") === options.sourceSystem) ??
    mappings[0];
  const mappingId = numberField(mapping, "id");
  expect(mappingId, "P0 用药安全已覆盖术语必须能读回确认映射").toBeTruthy();
  return {
    mappingId: Number(mappingId),
    standardTermId: numberField(mapping, "standardTermId") ?? null,
  };
}

async function generateAndConfirmMedicationSafetyTermMapping(
  page: Page,
  options: {
    suffix: string;
    sourceSystem: string;
    localCode: string;
    standardTermId: number | null;
  },
) {
  const generation = await postApi(page, "/engine/terminology/mappings/candidates", {
    ...terminologyApiContext(options.suffix, "candidates"),
    sourceSystem: options.sourceSystem,
    minimumScore: 0.2,
    semanticAssistEnabled: true,
  });
  await expectOk(generation, "生成 P0 用药安全术语映射候选");
  const jobCode = requireText(
    textField(await responseData(generation), "jobCode"),
    "P0 用药安全术语候选任务必须返回 jobCode",
  );
  const candidate = await waitForMedicationSafetyTerminologyCandidate(page, {
    jobCode,
    sourceSystem: options.sourceSystem,
    localCode: options.localCode,
    standardTermId: options.standardTermId,
  });
  const candidateId = numberField(candidate, "id");
  expect(candidateId, "P0 用药安全术语候选必须返回 id").toBeTruthy();
  const confirmed = await postApi(
    page,
    `/engine/terminology/mappings/${encodeURIComponent(String(candidateId))}/confirm`,
    {
      ...terminologyApiContext(options.suffix, "confirm"),
      reviewNote: `P0 用药安全代表切片：确认前台药品编码 ${options.localCode} 到 ATC:J01C。`,
      evidenceOverride: "P0 用药安全代表切片规则发布前置术语覆盖门禁。",
    },
  );
  await expectOk(confirmed, "确认 P0 用药安全术语映射");
  const confirmedData = await responseData(confirmed);
  const mappingId = numberField(confirmedData, "id");
  expect(mappingId, "P0 用药安全术语映射确认必须返回 mappingId").toBeTruthy();
  return {
    mappingId: Number(mappingId),
    standardTermId: numberField(confirmedData, "standardTermId") ?? null,
  };
}

async function waitForMedicationSafetyTerminologyCandidate(
  page: Page,
  options: {
    jobCode: string;
    sourceSystem: string;
    localCode: string;
    standardTermId: number | null;
  },
) {
  const deadline = Date.now() + 20_000;
  let lastStatus = "PENDING";
  let generatedCount = 0;
  while (Date.now() < deadline) {
    const job = await getApi(
      page,
      `/engine/terminology/mappings/candidate-generation-jobs/${encodeURIComponent(options.jobCode)}`,
    );
    await expectOk(job, "读取 P0 用药安全术语候选任务");
    const jobData = await responseData(job);
    lastStatus = textField(jobData, "status") ?? lastStatus;
    generatedCount = numberField(jobData, "generatedCount") ?? generatedCount;
    const candidates = await getApi(
      page,
      `/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=${encodeURIComponent(
        options.jobCode,
      )}&page=1&size=20`,
    );
    await expectOk(candidates, "读取 P0 用药安全术语映射候选");
    const candidate = pageItems(await responseData(candidates)).find(
      (item) =>
        textField(item, "status") === "PENDING" &&
        textField(item, "generationJobCode") === options.jobCode &&
        (options.standardTermId === null ||
          numberField(item, "standardTermId") === options.standardTermId),
    );
    if (candidate) return candidate;
    await waitForPollingInterval(250);
  }
  throw new Error(
    `P0 用药安全术语候选生成超时，最后任务状态：${lastStatus}，生成数量：${generatedCount}`,
  );
}

async function createAndPublishMedicationSafetyRule(
  page: Page,
  suffix: string,
  options: {
    positiveContextSnapshotId: string;
    negativeContextSnapshotId: string;
  },
) {
  const ruleCode = `RULE.MEDICATION.SAFETY.${suffix}`;
  const create = await postApi(page, "/engine/rule/rules", {
    ...ruleApiContext(suffix, "create"),
    triggers: [
      {
        trigger_point: "medication-prescribe",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "medications", "allergyIntolerances"],
      },
    ],
    ruleCode,
    name: `P0 用药过敏禁忌确认规则 ${suffix}`,
    ruleType: "ORDER",
    authoringMode: "DSL",
    riskLevel: "HIGH",
    priority: 950,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:medication-safety-frontdesk",
    changeSummary: "P0 用药安全代表切片：规则与 SAFETY 红线共同证明开立用药需人工确认。",
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
          { fact: "allergyIntolerances[].code", operator: "contains", value: "J01C" },
        ],
      },
      then: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "critical",
          summary: "青霉素类过敏患者开立相关用药需复核",
          detail: "用药建议仅进入医生确认链路；是否调整医嘱仍在 HIS 中由医生确认。",
          source: { label: "P0 用药安全代表切片" },
          suggestions: [],
          overrideReasons: ["医生已完成过敏史核实并记录理由"],
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        title: "P0 用药安全代表切片规则",
        reason:
          "Medication 与 AllergyIntolerance 均来自当前临床上下文，规则由当前机构生效版本锁定。",
        sourceRef: "local-e2e:medication-safety-frontdesk",
      },
    },
    explanation: {
      title: "P0 用药安全代表切片规则",
      summary: "证明用药安全规则资产进入当前机构生效版本。",
    },
    parameterBindings: {},
  });
  await expectOk(create, "创建 P0 用药安全 RULE 资产");
  const created = await responseData(create);
  const ruleId = requireText(textField(created, "ruleId"), "规则创建响应必须返回 ruleId");
  await addMedicationSafetyRuleReleaseTestCases(page, ruleId, options);
  const testRun = await postApi(
    page,
    `/engine/rule/rules/${encodeURIComponent(ruleId)}/test`,
    ruleApiContext(ruleId, "release-test-run"),
  );
  await expectOk(testRun, "执行 P0 用药安全规则发布验证用例");
  assertRuleReleaseTestRunPassed(await responseData(testRun));
  for (const targetState of ["REVIEWED", "SHADOW", "CANARY", "FULL"]) {
    const impact = await getApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/impact`);
    await expectOk(impact, `读取 P0 用药安全规则 ${targetState} 影响摘要`);
    const impactDigest = requireText(
      textField(await responseData(impact), "impactDigest"),
      `${targetState} 推进前必须返回 impactDigest`,
    );
    const transition = await postApi(
      page,
      `/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions`,
      {
        ...ruleApiContext(ruleId, `governance-${targetState}`),
        targetState,
        impactDigest,
        reason: `P0 用药安全代表切片规则推进至 ${targetState}`,
        publishEvidence: {
          qualityGate: {
            schemaValid: true,
            terminologyBindingComplete: true,
            dependencyIntegrityVerified: true,
            safetyMonotonicityVerified: true,
            impactSimulationPassed: true,
            summary: `P0 用药安全代表切片规则 ${targetState} 推进质量门已通过`,
          },
        },
      },
    );
    await expectOk(transition, `P0 用药安全规则治理推进至 ${targetState}`);
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

async function addMedicationSafetyRuleReleaseTestCases(
  page: Page,
  ruleId: string,
  options: { positiveContextSnapshotId: string; negativeContextSnapshotId: string },
) {
  const cases = [
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
  ];
  for (const testCase of cases) {
    const response = await postApi(
      page,
      `/engine/rule/rules/${encodeURIComponent(ruleId)}/test-cases`,
      {
        ...ruleApiContext(ruleId, `test-${testCase.caseType}`),
        ...testCase,
      },
    );
    await expectOk(response, `新增 P0 用药安全规则发布验证用例 ${testCase.caseType}`);
  }
}

function assertRuleReleaseTestRunPassed(testRun: unknown) {
  const failures = arrayField(testRun, "results").filter(
    (result) => textField(result, "status") !== "PASS",
  );
  expect(
    booleanField(testRun, "allPassed"),
    `P0 用药安全规则发布验证用例必须全部通过：${JSON.stringify(failures)}`,
  ).toBe(true);
}

async function readMedicationSafetyRuntimeCandidates(
  page: Page,
  hospitalId: string,
  options: {
    safetyIdentity: string;
    ruleIdentity: string;
  },
) {
  const [safety, cdssRisk, rule] = await Promise.all([
    readHospitalRuntimeCandidate(page, hospitalId, "SAFETY", options.safetyIdentity),
    readHospitalRuntimeCandidate(page, hospitalId, "CDSS_RISK", "CDSS.RISK.MATRIX"),
    readHospitalRuntimeCandidate(page, hospitalId, "RULE", options.ruleIdentity),
  ]);
  return { safety, cdssRisk, rule };
}

async function readHospitalRuntimeCandidate(
  page: Page,
  hospitalId: string,
  assetType: "SAFETY" | "CDSS_RISK" | "RULE",
  assetIdentity: string,
): Promise<MedicationSafetyAssetCandidate> {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-candidates?assetType=${assetType}&keyword=${encodeURIComponent(
      assetIdentity,
    )}&page=1&size=20`,
  );
  await expectOk(response, `读取本轮 ${assetType} runtime 候选`);
  const candidate = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "assetType") === assetType &&
      textField(item, "assetIdentity") === assetIdentity &&
      textField(item, "status") === "PUBLISHED",
  );
  const versionId = requireText(
    textField(candidate, "versionId"),
    `本轮 ${assetType} 候选 ${assetIdentity} 必须存在并返回统一资产版本 ID`,
  );
  expect(versionId.startsWith("av-"), `${assetType} runtime 候选必须使用统一资产 av-* 版本`).toBe(
    true,
  );
  return {
    assetType,
    assetIdentity,
    versionId,
    versionNo: requireText(
      textField(candidate, "versionNo"),
      `${assetType} runtime 候选必须返回版本号`,
    ),
    contentHash: requireText(
      textField(candidate, "contentHash"),
      `${assetType} runtime 候选必须返回正文 hash`,
    ),
    sourceLayer: textField(candidate, "sourceLayer") ?? undefined,
  };
}

async function activateRuntimeWithMedicationSafetyAssets(
  page: Page,
  options: {
    hospitalId: string;
    safety?: MedicationSafetyAssetCandidate;
    cdssRisk?: MedicationSafetyAssetCandidate;
    rule?: MedicationSafetyAssetCandidate;
    extraAssets?: RuntimeAssetSelection[];
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
  const currentPlatformBaselineReleaseId = textFieldAtPath(
    currentRuntime,
    "release.platformBaselineReleaseId",
  );
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    ...[options.safety, options.cdssRisk, options.rule]
      .filter((asset): asset is MedicationSafetyAssetCandidate => Boolean(asset))
      .map(runtimeSelection),
    ...(options.extraAssets ?? []),
  ]);
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId,
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest:
      currentReleaseId &&
      currentPlatformBaselineReleaseId &&
      currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
        ? await readPlatformUpgradeAnalysisDigest(
            page,
            options.hospitalId,
            baselineAssets.baselineReleaseId,
          )
        : null,
    activeAssets,
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, "激活包含 P0 用药安全资产的医院生效版本");
  const activatedRelease = await responseData(activated);
  const releaseId = requireText(
    textField(activatedRelease, "releaseId"),
    "激活机构生效版本必须返回 releaseId",
  );
  const currentAfterActivation = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfterActivation, "回读包含 P0 用药安全资产的医院生效版本");
  const detail = (await responseData(currentAfterActivation)) as RuntimeReleaseDetail;
  expect(textFieldAtPath(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(
    releaseId,
  );
  const safetyAsset = options.safety ? assertRuntimeContainsAsset(detail, options.safety) : null;
  const cdssRiskAsset = options.cdssRisk
    ? assertRuntimeContainsAsset(detail, options.cdssRisk)
    : null;
  const ruleAsset = options.rule ? assertRuntimeContainsAsset(detail, options.rule) : null;
  return {
    releaseId,
    revisionNo: numberFieldAtPath(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textFieldAtPath(detail, "release.manifestSha256"),
      "机构生效版本必须返回 manifestSha256",
    ),
    assets: detail.items ?? [],
    safetyAsset,
    cdssRiskAsset,
    ruleAsset,
    previousReleaseId: currentReleaseId,
    activationRequest,
  };
}

async function rollbackRuntimeAndAssertAssetsRemoved(
  page: Page,
  options: {
    hospitalId: string;
    targetReleaseId: string | null;
    consumer: string;
    removedAssets: RuntimeAssetSelection[];
  },
): Promise<RuntimeRollbackNegativeEvidence> {
  const targetReleaseId = requireText(
    options.targetReleaseId,
    "用药安全回滚负向证据必须有演练前机构生效版本",
  );
  await ensureReadySession(page, "engine-operator");
  const rollback = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases:rollback`,
    { targetReleaseId },
  );
  await expectOk(rollback, "回滚 P0 用药安全机构生效版本");
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "回读 P0 用药安全回滚后机构生效版本");
  const currentRuntime = runtimeReadbackEvidence(await responseData(current));
  assertAssetsRemoved(currentRuntime.assets, options.removedAssets, "回滚后 current runtime");

  const consumer = await getApi(
    page,
    "/engine/integration/knowledge-runtime/runtime-release/current",
  );
  await expectOk(consumer, "读取 P0 用药安全回滚后第三方运行契约");
  const runtimeConsumer = runtimeConsumerReadbackEvidence(await responseData(consumer));
  assertAssetsRemoved(runtimeConsumer.assets, options.removedAssets, "回滚后第三方运行契约");
  expect(runtimeConsumer.releaseId, "第三方运行契约 releaseId 必须与 current 一致").toBe(
    currentRuntime.releaseId,
  );
  expect(runtimeConsumer.revisionNo, "第三方运行契约 revisionNo 必须与 current 一致").toBe(
    currentRuntime.revisionNo,
  );
  expect(runtimeConsumer.manifestSha256, "第三方运行契约 manifestSha256 必须与 current 一致").toBe(
    currentRuntime.manifestSha256,
  );

  return {
    rollbackPosted: true,
    currentRuntimeReadbackVerified: true,
    runtimeConsumerReadbackVerified: true,
    consumer: options.consumer,
    consumerProbeMatchedRemovedAssets: false,
    removedAssets: options.removedAssets,
    currentRuntime,
    runtimeConsumer,
  };
}

async function createMedicationSafetyContextFromFrontdesk(
  page: Page,
  suffix: string,
  overrides: { medicationText?: string; allergyText?: string } = {},
): Promise<ContextSnapshotSummary> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `药*${idLast4.slice(-1)}`;
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
  await expectHttpOk(patientResponse, "P0 用药安全演练创建脱敏患者");
  const patientId = requireText(
    textField(await responseData(patientResponse), "mpiId"),
    "P0 用药安全患者创建响应必须返回 MPI",
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
  await contextDialog.getByLabel("诊断/随访病种").fill("P0 用药安全过敏禁忌演练");
  await chooseDialogOption(page, contextDialog, "风险分层", "高风险");
  await contextDialog
    .getByLabel("当前用药")
    .fill(overrides.medicationText ?? `青霉素、P0 用药安全演练 ${suffix}`);
  await contextDialog
    .getByLabel("过敏/不良反应")
    .fill(overrides.allergyText ?? "青霉素：皮疹；头孢菌素：呼吸困难");
  await contextDialog.getByRole("combobox", { name: "特殊人群标记" }).click();
  await page.getByTitle("妊娠").click();
  await contextDialog.getByRole("combobox", { name: "特殊人群标记" }).click();
  await page.getByTitle("老年").click();
  await contextDialog.getByLabel("身高 cm").fill("170");
  await contextDialog.getByLabel("体重 kg").fill("82");
  await contextDialog
    .getByLabel("建立原因")
    .fill("P0 用药安全代表切片：建立当前用药与过敏史上下文。");
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, "P0 用药安全演练建立 ACTIVE 快照");
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

async function triggerMedicationSafetyRecommendationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      safetyAsset: RuntimeReleaseItem;
      cdssRiskAsset: RuntimeReleaseItem;
      ruleAsset: RuntimeReleaseItem;
    };
    safety: MedicationSafetyAssetCandidate;
    cdssRisk: MedicationSafetyAssetCandidate;
    rule: MedicationSafetyAssetCandidate & {
      ruleId: string;
      ruleVersionId: string;
    };
    riskMatrix: {
      matrixId: string;
      matrixVersion: string;
      autoExecutionAllowed: boolean | null;
    };
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
  await expect(snapshotButton, `提醒推荐页必须展示本轮临床快照 ${snapshot.snapshotId}`).toBeVisible(
    {
      timeout: 20_000,
    },
  );
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "开立用药");
  const evaluateResponsePromise = waitForMedicationSafetyEvaluateResponse(page, snapshot);
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  await expectHttpOk(evaluateResponse, "临床用户从真实前台触发 P0 用药安全推荐评估");
  const evaluation = await responseData(evaluateResponse);
  expect(textField(evaluation, "status"), "推荐触发状态应为已评估").toBe("EVALUATED");
  const triggerId = requireText(textField(evaluation, "triggerId"), "推荐评估响应必须返回触发 ID");
  const responseCardIds = arrayField(evaluation, "cards")
    .map((card) => textField(card, "cardId"))
    .filter((cardId): cardId is string => cardId !== null);
  expect(responseCardIds.length, "推荐评估响应必须返回本次可见推荐卡 ID").toBeGreaterThan(0);
  const relatedCardIds = await readRecommendationTriggerDiagnose(page, triggerId);
  expect(relatedCardIds, "触发诊断必须关联本次评估响应中的可见推荐卡").toEqual(
    expect.arrayContaining(responseCardIds),
  );
  const recommendation = await findMedicationSafetyRecommendationCard(
    page,
    relatedCardIds,
    options,
  );
  const ruleRecommendation = await findMedicationSafetyRuleRecommendationCard(
    page,
    relatedCardIds,
    {
      triggerId,
      snapshot,
      runtime: options.runtime,
      rule: options.rule,
    },
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await page.getByLabel("患者或证据线索").fill(recommendation.cardId);
  await expect(
    page.getByRole("row", { name: new RegExp(escapeRegExp(recommendation.cardTitle)) }),
  ).toBeVisible({ timeout: 20_000 });
  return {
    triggerId,
    relatedCardIds,
    ruleRecommendation,
    ...recommendation,
  };
}

async function completePharmacistAndPhysicianFeedback(page: Page, cardId: string) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
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
    .fill("药师已复核青霉素类过敏禁忌红线，医生仍需逐条确认；未填写患者明文身份。");
  const reviewResponsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "登记药师复核" }).click();
  const reviewResponse = await reviewResponsePromise;
  await expectHttpOk(reviewResponse, "登记 P0 用药安全药师复核");
  const pharmacist = await responseData(reviewResponse);
  expect(textField(pharmacist, "cardStatus"), "药师复核响应必须保持医生待确认链路").toBe("PENDING");
  await expect(drawer.getByText(/药师\s*·\s*完成复核/u)).toBeVisible({ timeout: 30_000 });
  await drawer.getByRole("button", { name: "Close" }).click();
  await expect(drawer).toBeHidden({ timeout: 20_000 });
  await page.getByLabel("患者或证据线索").fill(cardId);
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const refreshedDrawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await expect(refreshedDrawer).toBeVisible({ timeout: 20_000 });
  await expect(refreshedDrawer.getByRole("tab", { name: /医师反馈/u })).toBeVisible({
    timeout: 30_000,
  });
  await refreshedDrawer.getByRole("tab", { name: /医师反馈/u }).click();
  await refreshedDrawer
    .getByLabel("采纳说明（可选）")
    .fill("医生已逐条确认过敏禁忌红线，是否开嘱仍在 HIS 中人工确认。");
  const acceptResponsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await refreshedDrawer.getByRole("button", { name: "确认采纳建议" }).click();
  const acceptResponse = await acceptResponsePromise;
  await expectHttpOk(acceptResponse, "登记 P0 用药安全医生确认");
  const physician = await responseData(acceptResponse);
  await expect(refreshedDrawer.getByText(/医生\s*·\s*采纳建议/u)).toBeVisible({ timeout: 30_000 });
  const detailResponse = await getApi(
    page,
    `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
  );
  await expectOk(detailResponse, "回读 P0 用药安全推荐卡反馈详情");
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
  return {
    pharmacist: {
      feedbackId: textField(pharmacist, "feedbackId"),
      cardStatus: textField(pharmacist, "cardStatus"),
      traceId: textField(pharmacist, "traceId"),
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: pharmacistPersisted,
    },
    physician: {
      feedbackId: textField(physician, "feedbackId"),
      cardStatus: textField(physician, "cardStatus"),
      traceId: textField(physician, "traceId"),
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: physicianPersisted,
    },
    noAutoOrder: true,
  };
}

async function findMedicationSafetyRecommendationCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      safetyAsset: RuntimeReleaseItem;
      cdssRiskAsset: RuntimeReleaseItem;
      ruleAsset: RuntimeReleaseItem;
    };
    riskMatrix: {
      matrixId: string;
      matrixVersion: string;
      autoExecutionAllowed: boolean | null;
    };
  },
) {
  const matchedCards: Array<{
    cardId: string;
    cardTitle: string;
    cardStatus: string | null;
    triggerRuntimeReleaseId: string | null;
    explanation: Record<string, unknown>;
    riskMatrixExplanation: string;
  }> = [];
  const inspectedCards: Array<Record<string, unknown>> = [];
  for (const cardId of Array.from(new Set(relatedCardIds))) {
    const detailResponse = await getApi(
      page,
      `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
    );
    await expectOk(detailResponse, `推荐卡 ${cardId} 详情应可由真实服务读取`);
    const detail = await responseData(detailResponse);
    const explanationJson = requireText(
      textFieldAtPath(detail, "card.explanationJson"),
      `推荐卡 ${cardId} 详情必须返回解释 JSON`,
    );
    const riskMatrixExplanationJson = requireText(
      textFieldAtPath(detail, "card.riskMatrixExplanation"),
      `推荐卡 ${cardId} 详情必须返回风险矩阵解释`,
    );
    const explanation = parseJsonRecord(explanationJson);
    if (!explanation) {
      inspectedCards.push({
        cardId,
        parseableExplanation: Boolean(explanation),
        rawRiskMatrixExplanation: riskMatrixExplanationJson,
      });
      continue;
    }
    const redlineExplanation = recordValue(recordField(explanation, "redlineExplanation"));
    const conditionEvidence = arrayField(redlineExplanation, "conditionEvidence");
    const matches =
      textFieldAtPath(detail, "trigger.contextSnapshotId") === options.snapshot.snapshotId &&
      textFieldAtPath(detail, "trigger.runtimeReleaseId") === options.runtime.releaseId &&
      textField(explanation, "matchType") === "CLINICAL_REDLINE" &&
      textField(explanation, "riskMatrixId") === options.riskMatrix.matrixId &&
      textField(explanation, "riskMatrixVersion") === options.riskMatrix.matrixVersion &&
      riskMatrixExplanationJson.includes("医师") &&
      riskMatrixExplanationJson.includes("确认") &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "allergyIntolerances[].code" &&
          textField(item, "operator") === "contains" &&
          booleanField(item, "matched") === true,
      );
    inspectedCards.push({
      cardId,
      contextSnapshotId: textFieldAtPath(detail, "trigger.contextSnapshotId"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      matchType: textField(explanation, "matchType"),
      redlineId: textField(explanation, "redlineId"),
      redlineKey: textField(explanation, "redlineKey"),
      riskMatrixId: textField(explanation, "riskMatrixId"),
      riskMatrixVersion: textField(explanation, "riskMatrixVersion"),
      riskMatrixExplanation: riskMatrixExplanationJson,
      conditionFacts: conditionEvidence.map((item) => ({
        fact: textField(item, "fact"),
        operator: textField(item, "operator"),
        matched: booleanField(item, "matched"),
      })),
    });
    if (!matches) continue;
    matchedCards.push({
      cardId,
      cardTitle: requireText(
        textFieldAtPath(detail, "card.title"),
        `推荐卡 ${cardId} 必须返回业务标题`,
      ),
      cardStatus: textFieldAtPath(detail, "card.status"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      explanation,
      riskMatrixExplanation: riskMatrixExplanationJson,
    });
  }
  expect(
    matchedCards.map((card) => card.cardId),
    `本次触发诊断关联卡中必须唯一定位本轮 P0 用药安全红线推荐卡；已检查 ${JSON.stringify(inspectedCards)}`,
  ).toHaveLength(1);
  return matchedCards[0];
}

async function findMedicationSafetyRuleRecommendationCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    triggerId: string;
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem;
    };
    rule: MedicationSafetyAssetCandidate & {
      ruleId: string;
      ruleVersionId: string;
    };
  },
) {
  const matchedCards: Array<{
    cardId: string;
    cardTitle: string;
    cardStatus: string | null;
    triggerRuntimeReleaseId: string | null;
    explanation: Record<string, unknown>;
  }> = [];
  const inspectedCards: Array<Record<string, unknown>> = [];
  for (const cardId of Array.from(new Set(relatedCardIds))) {
    const detailResponse = await getApi(
      page,
      `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
    );
    await expectOk(detailResponse, `规则推荐卡 ${cardId} 详情应可由真实服务读取`);
    const detail = await responseData(detailResponse);
    const explanationJson = requireText(
      textFieldAtPath(detail, "card.explanationJson"),
      `规则推荐卡 ${cardId} 详情必须返回解释 JSON`,
    );
    const explanation = parseJsonRecord(explanationJson);
    const runtimeRelease = recordValue(recordField(explanation, "runtimeRelease"));
    const ruleExplanation = recordValue(recordField(explanation, "ruleExplanation"));
    const ruleConditionEvidence = arrayField(ruleExplanation, "conditionEvidence");
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
      textField(ruleExplanation, "title") !== null &&
      textField(ruleExplanation, "reason") !== null &&
      ruleConditionEvidence.some(
        (item) =>
          textField(item, "fact") === "medications[].code" &&
          textField(item, "operator") === "contains" &&
          booleanField(item, "matched") === true,
      ) &&
      ruleConditionEvidence.some(
        (item) =>
          textField(item, "fact") === "allergyIntolerances[].code" &&
          textField(item, "operator") === "contains" &&
          booleanField(item, "matched") === true,
      );
    inspectedCards.push({
      cardId,
      triggerId: textFieldAtPath(detail, "trigger.triggerId"),
      contextSnapshotId: textFieldAtPath(detail, "trigger.contextSnapshotId"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      matchType: textField(explanation, "matchType"),
      ruleId: textField(explanation, "ruleId"),
      ruleCode: textField(explanation, "ruleCode"),
      ruleVersionId: textField(explanation, "ruleVersionId"),
      runtimeReleaseId: textField(runtimeRelease, "runtimeReleaseId"),
      assetVersionId: textField(runtimeRelease, "assetVersionId"),
      assetVersionNo: textField(runtimeRelease, "assetVersionNo"),
      contentHash: textField(runtimeRelease, "contentHash"),
      hasRuleExplanation: ruleExplanation !== null,
      ruleExplanationTitle: textField(ruleExplanation, "title"),
      ruleExplanationReason: textField(ruleExplanation, "reason"),
      ruleConditionFacts: ruleConditionEvidence.map((item) => ({
        fact: textField(item, "fact"),
        operator: textField(item, "operator"),
        matched: booleanField(item, "matched"),
      })),
    });
    if (!matches) continue;
    matchedCards.push({
      cardId,
      cardTitle: requireText(
        textFieldAtPath(detail, "card.title"),
        `规则推荐卡 ${cardId} 必须返回业务标题`,
      ),
      cardStatus: textFieldAtPath(detail, "card.status"),
      triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
      explanation,
    });
  }
  expect(
    matchedCards.map((card) => card.cardId),
    `本次触发诊断关联卡中必须唯一定位本轮 P0 用药安全 RULE 推荐卡；已检查 ${JSON.stringify(inspectedCards)}`,
  ).toHaveLength(1);
  return matchedCards[0];
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

function waitForMedicationSafetyEvaluateResponse(page: Page, snapshot: ContextSnapshotSummary) {
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

async function attachMedicationSafetyEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: MedicationSafetyApiEvidence;
    riskMatrix: unknown;
    safetyRedline: unknown;
    rule: unknown;
    terminologyGate: MedicationSafetyTerminologyGate;
    runtime: {
      releaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      safetyAsset: RuntimeReleaseItem | null;
      cdssRiskAsset: RuntimeReleaseItem | null;
      ruleAsset: RuntimeReleaseItem | null;
      previousReleaseId: string | null;
    };
    activationRequest: unknown;
    clinicalContext: unknown;
    clinicalTrigger: unknown;
    recommendation: unknown;
    ruleRecommendation: unknown;
    feedback: unknown;
    rollbackNegativeEvidence: RuntimeRollbackNegativeEvidence;
    observedStages: Set<string>;
  },
) {
  for (const stage of requiredStages) {
    expect(evidence.observedStages.has(stage), `缺少 P0 用药安全阶段：${stage}`).toBe(true);
  }
  await testInfo.attach("medication-safety-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S5"],
        productLayers: ["CLINICAL_EXECUTION"],
        versionedAssets: ["SAFETY", "CDSS_RISK", "RULE"],
        serviceCombinations: ["CLINICAL_RUNTIME"],
        scopeStatement: "用药安全代表切片：药物过敏红线，不代表完整药事治理或第三方审方系统闭环。",
        standardPatientResourceConsumerMatrix: standardPatientResourceConsumerMatrix([
          {
            resourceType: "Patient",
            resourcePath: "clinicalContext.resources.patient",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.patient.mpi",
            patientVerified: true,
            encounterVerified: false,
            snapshotReadbackVerified: true,
            consumer: "MEDICATION_SAFETY_RULE",
            consumerEvidencePaths: [
              "ruleRecommendation.explanation.ruleExplanation.conditionEvidence[0]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["feedback.physician.persisted.feedbackId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
          {
            resourceType: "Encounter",
            resourcePath: "clinicalContext.resources.encounters[0]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.encounters[0].encounterId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "MEDICATION_SAFETY_RULE",
            consumerEvidencePaths: ["clinicalTrigger.contextSnapshotId"],
            consumerVerified: true,
            auditEvidencePaths: ["feedback.physician.persisted.feedbackId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
          {
            resourceType: "AllergyIntolerance",
            resourcePath: "clinicalContext.resources.allergyIntolerances[0]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.allergyIntolerances[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "MEDICATION_SAFETY_RULE",
            consumerEvidencePaths: [
              "recommendation.explanation.redlineExplanation.conditionEvidence[0]",
              "ruleRecommendation.explanation.ruleExplanation.conditionEvidence[1]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["feedback.pharmacist.persisted.feedbackId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
          {
            resourceType: "Medication",
            resourcePath: "clinicalContext.resources.medications[0]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.medications[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "MEDICATION_SAFETY_RULE",
            consumerEvidencePaths: [
              "ruleRecommendation.explanation.ruleExplanation.conditionEvidence[0]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["feedback.physician.persisted.feedbackId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
        ]),
        apiEvidence: evidence.apiEvidence,
        riskMatrix: evidence.riskMatrix,
        safetyRedline: evidence.safetyRedline,
        ruleAsset: evidence.rule,
        terminologyGate: evidence.terminologyGate,
        runtime: {
          releaseId: evidence.runtime.releaseId,
          revisionNo: evidence.runtime.revisionNo,
          manifestSha256: evidence.runtime.manifestSha256,
          assets: evidence.runtime.assets,
          safetyAsset: evidence.runtime.safetyAsset,
          cdssRiskAsset: evidence.runtime.cdssRiskAsset,
          ruleAsset: evidence.runtime.ruleAsset,
        },
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        clinicalTrigger: evidence.clinicalTrigger,
        recommendation: evidence.recommendation,
        ruleRecommendation: evidence.ruleRecommendation,
        feedback: evidence.feedback,
        rollbackNegativeEvidence: evidence.rollbackNegativeEvidence,
        scenarioEvidence: [
          {
            code: "S5",
            observedStages: Array.from(evidence.observedStages),
          },
        ],
        scenarioConditionEvidence: [
          {
            code: "S5__HIGH_RISK",
            scenarioCode: "S5",
            condition: "HIGH_RISK",
            source: "MEDICATION_SAFETY_CRITICAL_REDLINE_PHYSICIAN_CONFIRMATION",
            evidence: [
              "CDSS_RISK 风险矩阵与 SAFETY 红线均为 CRITICAL",
              "药物过敏 AllergyIntolerance 已确认且命中红线条件",
              "推荐卡保持 PENDING，医生人工确认后 ACCEPTED",
              "系统未自动开嘱",
            ],
          },
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

function parseJsonRecord(value: string) {
  try {
    const parsed = JSON.parse(value);
    return recordValue(parsed);
  } catch {
    return null;
  }
}

function assertSnapshotContainsMedicationAllergy(resources: Record<string, unknown>) {
  const medications = arrayField(resources, "medications");
  const allergies = arrayField(resources, "allergyIntolerances");
  expect(
    medications.some((item) => textField(item, "code") === "J01C"),
    "前台上下文必须提交结构化 Medication J01C",
  ).toBe(true);
  expect(
    allergies.some(
      (item) =>
        textField(item, "code") === "J01C" &&
        textField(item, "category") === "medication" &&
        textField(item, "verificationStatus") === "CONFIRMED",
    ),
    "前台上下文必须提交结构化 AllergyIntolerance J01C",
  ).toBe(true);
}

function assertSnapshotContainsSpecialPopulations(resources: Record<string, unknown>) {
  const populations = arrayFieldAtPath(resources, "patient.specialPopulations").map((item) =>
    String(item),
  );
  expect(populations, "前台上下文必须把特殊人群标记写入 canonical Patient").toEqual(
    expect.arrayContaining(["PREGNANCY", "GERIATRIC"]),
  );
}

function ruleApiContext(subject: string, step: string) {
  return {
    request_id: `req-med-safety-${step}-${subject}`,
    trace_id: `trace-med-safety-${step}-${subject}`,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function terminologyApiContext(subject: string, step: string) {
  return ruleApiContext(subject, `term-${step}`);
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

async function readPlatformUpgradeAnalysisDigest(
  page: Page,
  hospitalId: string,
  targetBaselineReleaseId: string,
) {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/platform-upgrade-analysis?targetBaselineReleaseId=${encodeURIComponent(
      targetBaselineReleaseId,
    )}`,
  );
  await expectOk(response, "读取 P0 用药安全演练平台升级分析");
  return requireText(
    textField(await responseData(response), "analysisDigest"),
    "P0 用药安全演练平台升级分析必须返回摘要",
  );
}

function resolveBaselineRuntimeAssets(value: unknown) {
  const baselineReleaseId = textFieldAtPath(value, "release.baselineReleaseId");
  const activeAssets = pageItems(value)
    .filter((item) => textField(item, "entryState") === "ACTIVE")
    .map((item): RuntimeAssetSelection | null => {
      const assetType = textField(item, "assetType");
      const assetIdentity = textField(item, "assetIdentity");
      if (!assetType || !assetIdentity) return null;
      return { assetType, assetIdentity, versionId: null };
    })
    .filter((item): item is RuntimeAssetSelection => item !== null);
  return {
    baselineReleaseId,
    activeAssets: uniqueRuntimeAssets(activeAssets),
  };
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function runtimeSelection(candidate: RuntimeAssetCandidate): RuntimeAssetSelection {
  return {
    assetType: candidate.assetType,
    assetIdentity: candidate.assetIdentity,
    versionId: candidate.versionId,
  };
}

function runtimeReadbackEvidence(value: unknown) {
  const evidence = {
    releaseId: requireText(
      textFieldAtPath(value, "release.releaseId"),
      "current runtime 必须返回 releaseId",
    ),
    revisionNo: numberFieldAtPath(value, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textFieldAtPath(value, "release.manifestSha256"),
      "current runtime 必须返回 manifestSha256",
    ),
    assets: pageItems(value) as RuntimeReleaseItem[],
  };
  expect(evidence.revisionNo, "current runtime 必须返回 revisionNo").toBeGreaterThan(0);
  expect(evidence.assets.length, "current runtime 必须返回资产清单").toBeGreaterThan(0);
  return evidence;
}

function runtimeConsumerReadbackEvidence(value: unknown) {
  const evidence = {
    contractVersion: "v1" as const,
    releaseId: requireText(textField(value, "releaseId"), "runtime consumer 必须返回 releaseId"),
    revisionNo: numberField(value, "revisionNo") ?? 0,
    manifestSha256: requireText(
      textField(value, "manifestSha256"),
      "runtime consumer 必须返回 manifestSha256",
    ),
    assets: arrayField(value, "assets") as RuntimeReleaseItem[],
  };
  expect(textField(value, "contractVersion"), "runtime consumer 必须返回 v1 契约").toBe("v1");
  expect(evidence.revisionNo, "runtime consumer 必须返回 revisionNo").toBeGreaterThan(0);
  expect(evidence.assets.length, "runtime consumer 必须返回资产清单").toBeGreaterThan(0);
  return evidence;
}

function assertAssetsRemoved(
  assets: RuntimeReleaseItem[],
  removedAssets: RuntimeAssetSelection[],
  label: string,
) {
  for (const removed of removedAssets) {
    expect(
      assets.some(
        (asset) =>
          asset.assetType === removed.assetType &&
          asset.assetIdentity === removed.assetIdentity &&
          asset.versionId === removed.versionId,
      ),
      `${label} 不应继续包含本轮 ${removed.assetType}:${removed.assetIdentity}`,
    ).toBe(false);
  }
}

function assertRuntimeContainsAsset(
  runtime: RuntimeReleaseDetail,
  candidate: MedicationSafetyAssetCandidate,
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

function assertRuntimeContainsTerminology(
  runtime: { assets: RuntimeReleaseItem[] },
  terminology: MedicationSafetyTerminologyGate,
) {
  const asset = runtime.assets.find(
    (item) =>
      item.assetType === "TERMINOLOGY" &&
      item.assetIdentity === terminology.assetIdentity &&
      item.versionId === terminology.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(asset, `机构生效版本必须包含本轮术语资产 ${terminology.assetIdentity}`).toBeTruthy();
  expect(asset?.versionNo, "术语 runtime 清单必须返回本轮版本号").toBe(terminology.versionNo);
  expect(asset?.contentHash, "术语 runtime 清单必须返回本轮正文 hash").toBe(
    terminology.contentHash,
  );
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  if (
    await dialog
      .getByText(optionText, { exact: true })
      .isVisible()
      .catch(() => false)
  ) {
    return;
  }
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
