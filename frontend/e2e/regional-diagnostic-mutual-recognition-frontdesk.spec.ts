import { createHmac } from "node:crypto";
import { expect, test, type APIResponse, type Locator, type Page, type TestInfo } from "@playwright/test";

import {
  apiBase,
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  postApi,
  requiredRuntimeAssetsForRehearsal,
  resolvedTenantIdFor,
  resolveBaselineRuntimeAssets,
  responseData,
  textField,
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
  sourceLayer?: string;
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

type RegionalDiagnosticRuntimeAssetCandidate = {
  assetType: "KNOWLEDGE" | "FIELD_CATALOG" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  resources: Record<string, unknown>;
};

type FhirDiagnosticReportEvidence = {
  fhirResourceType: "DiagnosticReport";
  fhirId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  patientId: string;
  sourceSystem: "FHIR_R4";
  integrationStatus: string | null;
  operationOutcomeContainsNotConnected: boolean;
  compensationStatus: string | null;
  compensationRequired: boolean | null;
  compensationMessageId: string;
  canonicalResourceType: "DIAGNOSTIC_REPORT";
  sourceRecordId: string;
  reportType: string;
  conclusion: string;
  signedStatus: "FINAL";
  signedAt: string;
  sourceOrganizationId: string;
  sourceOrganizationName: string;
  regionalSourceId: string;
  mutualRecognitionReason: string;
  duplicateExamHint: string;
};

type ReportInterpretationPayload = {
  contextSnapshotId?: string;
  runtimeReleaseId?: string;
  advisoryNote?: string;
  interpretations?: Array<{
    reportId?: string;
    reportType?: string;
    itemCode?: string;
    sourceVersionId?: number;
    versionNo?: string;
    criticalRisk?: boolean;
    abnormalHighlights?: string[];
    recommendations?: string[];
  }>;
};

type RegionalDiagnosticApiEvidence = {
  regionalRemoteOnboardingCreated: boolean;
  regionalSourceRegisteredAndReadBack: boolean;
  fhirDiagnosticReportAccepted: boolean;
  contextSnapshotContainsRegionalReport: boolean;
  currentRuntimeContainsMutualRecognitionAssets: boolean;
  reportInterpretationTriggeredFromFrontdesk: boolean;
  mutualRecognitionRecommendationPersisted: boolean;
  workflowTodoCompletedByHuman: boolean;
};

type KnowledgeGenerationCandidate = {
  candidateRef?: string;
  jobCode?: string;
};

type KnowledgeCandidateItem = {
  id?: number;
  versionNo?: string;
  contentHash?: string;
  status?: string;
};

type KnowledgeCandidateClassification = {
  id?: number;
  candidateVersionId?: number;
};

const fieldCatalogIdentity = "FIELD.CATALOG.CLINICAL_CONTEXT";
const criticalActionCardIdentity = "ACTION_CARD.REPORT.CRITICAL_VALUE";
const requiredStages = [
  "平台管理员登记 REGIONAL_REMOTE FHIR 接入申请并保持断连诚实状态",
  "平台管理员登记区域来源可信分级并回读跨机构证据",
  "外部区域 FHIR 入站已签发 DiagnosticReport 并落标准资源",
  "当前上下文回读跨机构 DiagnosticReport 并绑定同一机构生效版本",
  "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
  "临床用户从真实前台生成区域报告互认解读",
  "推荐卡证明互认理由、重复检查提示、字段目录和提示卡按当前机构生效版本消费",
  "医生人工完成互认协同待办，系统不自动互认、不改写报告且不自动开嘱",
] as const;

test.describe("区域医技报告互认代表切片真实前台闭环", () => {
  test(
    "临床用户与平台管理员完成区域医技报告互认代表闭环",
    async ({ page }, testInfo) => {
      test.setTimeout(360_000);
      const observedStages = new Set<string>();
      const apiEvidence = createApiEvidence();
      const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;

      await ensureReadySession(page, "engine-operator");
      const hospitalId = await localRehearsalHospitalId(page);
      const actionCard = await createRegionalMutualRecognitionActionCard(page, suffix);
      const knowledge = await createRegionalDiagnosticKnowledgeAsset(page, suffix, hospitalId);
      const diagnosticAssets = await readRegionalDiagnosticRuntimeCandidates(page, {
        knowledge,
        actionCard,
      });
      const runtime = await activateRuntimeWithRegionalDiagnosticAssets(page, {
        hospitalId,
        knowledge: diagnosticAssets.knowledge,
        fieldCatalog: diagnosticAssets.fieldCatalog,
        actionCard: diagnosticAssets.actionCard,
      });
      apiEvidence.currentRuntimeContainsMutualRecognitionAssets = true;
      recordStage(
        observedStages,
        "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
      );

      const snapshot = await createRegionalReportContextFromFrontdesk(page, suffix);
      expect(
        snapshot.runtimeReleaseId,
        "区域互认上下文必须绑定包含报告解读运行资产的当前机构生效版本",
      ).toBe(runtime.releaseId);

      await ensureReadySession(page, "platform-admin");
      const fhirOnboarding = await createRegionalFhirOnboardingFromFrontdesk(page, suffix);
      apiEvidence.regionalRemoteOnboardingCreated = true;
      recordStage(observedStages, "平台管理员登记 REGIONAL_REMOTE FHIR 接入申请并保持断连诚实状态");

      const regionalSource = await registerRegionalSourceFromFrontdesk(page, {
        suffix,
        onboardingId: requireText(
          textField(fhirOnboarding, "onboardingId"),
          "S40 接入申请必须返回 onboardingId",
        ),
      });
      apiEvidence.regionalSourceRegisteredAndReadBack = true;
      recordStage(observedStages, "平台管理员登记区域来源可信分级并回读跨机构证据");

      const fhir = await createRegionalFhirSignatureAdapter(page, suffix);
      const inboundDiagnosticReport = await postSignedRegionalDiagnosticReport(page, {
        adapterId: fhir.adapterId,
        secret: fhir.sharedSecret,
        snapshot,
        regionalSource,
        resource: regionalDiagnosticReportResource(snapshot, regionalSource, suffix),
      });
      apiEvidence.fhirDiagnosticReportAccepted = true;
      recordStage(observedStages, "外部区域 FHIR 入站已签发 DiagnosticReport 并落标准资源");

      const contextAfterInbound = await readContextSnapshot(page, snapshot.snapshotId);
      const clinicalContext = assertContextContainsRegionalReport({
        context: contextAfterInbound,
        runtime,
        inboundDiagnosticReport,
      });
      apiEvidence.contextSnapshotContainsRegionalReport = true;
      recordStage(observedStages, "当前上下文回读跨机构 DiagnosticReport 并绑定同一机构生效版本");

      const interpretation = await generateRegionalReportInterpretationFromFrontdesk(page, {
        snapshot: contextAfterInbound,
        runtime,
        knowledge: diagnosticAssets.knowledge,
        inboundDiagnosticReport,
      });
      apiEvidence.reportInterpretationTriggeredFromFrontdesk = true;
      recordStage(observedStages, "临床用户从真实前台生成区域报告互认解读");

      const recommendation = await findRegionalReportRecommendation(page, {
        interpretation,
        snapshot: contextAfterInbound,
        runtime,
        inboundDiagnosticReport,
      });
      apiEvidence.mutualRecognitionRecommendationPersisted = true;
      recordStage(
        observedStages,
        "推荐卡证明互认理由、重复检查提示、字段目录和提示卡按当前机构生效版本消费",
      );

      const workflowTodo = await completeRegionalReportTodo(page, {
        cardId: recommendation.cardId,
      });
      apiEvidence.workflowTodoCompletedByHuman = true;
      recordStage(observedStages, "医生人工完成互认协同待办，系统不自动互认、不改写报告且不自动开嘱");

      await attachRegionalDiagnosticMutualRecognitionEvidence(testInfo, {
        apiEvidence,
        fhirOnboarding,
        regionalSource,
        inboundDiagnosticReport,
        runtime,
        activationRequest: runtime.activationRequest,
        clinicalContext,
        interpretation,
        recommendation,
        workflowTodo,
        observedStages,
      });
    },
  );
});

function createApiEvidence(): RegionalDiagnosticApiEvidence {
  return {
    regionalRemoteOnboardingCreated: false,
    regionalSourceRegisteredAndReadBack: false,
    fhirDiagnosticReportAccepted: false,
    contextSnapshotContainsRegionalReport: false,
    currentRuntimeContainsMutualRecognitionAssets: false,
    reportInterpretationTriggeredFromFrontdesk: false,
    mutualRecognitionRecommendationPersisted: false,
    workflowTodoCompletedByHuman: false,
  };
}

async function createRegionalMutualRecognitionActionCard(
  page: Page,
  suffix: string,
): Promise<RegionalDiagnosticRuntimeAssetCandidate> {
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity: criticalActionCardIdentity,
    applicableScope: "ALL",
    sourceRef: "local-e2e:regional-diagnostic-mutual-recognition",
    content: {
      schemaVersion: "1.0",
      title: `区域报告互认人工复核提示 ${suffix}`,
      actionCode: "STRONG_REMINDER",
      atSeverity: "MEDIUM",
      indicator: "warning",
      summary: "区域来源报告需人工核验来源、互认目录和患者上下文",
      detail: "报告解读仅作辅助，不改写外部已签发报告，不自动互认，不自动开立医嘱。",
      source: { label: "MedKernel 本地上线演练" },
      suggestions: [
        { label: "打开区域报告上下文", actionType: "OPEN_FORM", payload: { target: "report-context" } },
      ],
      overrideReasons: ["已人工核对区域来源可信分级与互认理由"],
      requiresPhysicianConfirmation: true,
    },
  });
  await expectOk(response, "创建 S40 区域报告 ACTION_CARD 资产");
  const data = await responseData(response);
  return {
    assetType: "ACTION_CARD",
    assetIdentity: criticalActionCardIdentity,
    versionId: requireText(textField(data, "versionId"), "S40 动作卡必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "S40 动作卡必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "S40 动作卡必须返回 contentHash"),
  };
}

async function createRegionalDiagnosticKnowledgeAsset(
  page: Page,
  suffix: string,
  hospitalId: string,
): Promise<RegionalDiagnosticRuntimeAssetCandidate> {
  const identityCode = `IMG.CT.CHEST.REGIONAL.${stableSlugSuffix(suffix).toUpperCase()}`;
  const subject = "区域胸部 CT 互认说明书";
  const sourceCode = `local-e2e-regional-chest-ct-${suffix.toLowerCase()}`;
  const sourceVersionNo = "2026";
  const anchorPath = `regional-diagnostic/chest-ct-${suffix}`;
  const source = await postApi(page, "/engine/knowledge/sources", {
    ...knowledgeContext("s40-knowledge-source", hospitalId),
    sourceCode,
    sourceType: "HOSPITAL_PROTOCOL",
    authorityLevel: "D_HOSPITAL",
    authorityBasis: "本地上线演练区域医技报告互认说明书，用于验证 S40 代表链路。",
    title: `区域胸部 CT 互认说明书来源 ${suffix}`,
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(source, "登记 S40 区域胸部 CT 说明书来源");
  const sourceDocumentId = numericField(await responseData(source), "id");
  expect(sourceDocumentId, "S40 知识来源必须返回 id").toBeTruthy();

  const sourceVersion = await postApi(
    page,
    `/engine/knowledge/sources/${sourceDocumentId}/versions`,
    {
      ...knowledgeContext("s40-knowledge-source-version", hospitalId),
      versionNo: sourceVersionNo,
      publishedAt: "2026-07-08T00:00:00Z",
      fileUri: `medkernel://local-e2e/regional-diagnostic/chest-ct-${suffix}.md`,
      language: "zh-CN",
      content: regionalDiagnosticKnowledgeContent(),
    },
  );
  await expectOk(sourceVersion, "登记 S40 区域胸部 CT 说明书来源版本");
  const sourceVersionId = numericField(await responseData(sourceVersion), "id");
  expect(sourceVersionId, "S40 知识来源版本必须返回 id").toBeTruthy();

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId,
    anchorPath,
    anchorLabel: "区域胸部 CT 报告互认说明",
    textExcerpt: regionalDiagnosticKnowledgeContent(),
  });
  await expectOk(fragment, "登记 S40 区域胸部 CT 说明书来源片段");
  const sourceFragmentId = numericField(await responseData(fragment), "id");
  expect(sourceFragmentId, "S40 知识来源片段必须返回 id").toBeTruthy();

  const generated = await postApi(page, "/engine/knowledge-production/generate", {
    sourceVersionId,
    targetPipeline: "TENANT_OVERLAY",
    domain: "CLINICAL",
    items: [
      {
        assetType: "KNOWLEDGE",
        target: {
          targetIdentityId: null,
          newIdentity: { domain: "DIAGNOSTIC_ITEM", subject, identityCode },
        },
      },
    ],
  });
  await expectOk(generated, "从受控来源生成 S40 区域胸部 CT 说明书正式生产候选");
  const generation = await responseData(generated);
  expect(arrayField(generation, "blocked"), "S40 知识生产安全门不得阻断").toHaveLength(0);
  expect(arrayField(generation, "skipped"), "S40 知识生产不得被分流跳过").toHaveLength(0);
  const generatedCandidates = arrayField(generation, "candidates") as KnowledgeGenerationCandidate[];
  expect(generatedCandidates, "S40 知识生产必须生成一个 KNOWLEDGE 候选").toHaveLength(1);
  const candidateRef = requireText(
    textField(generatedCandidates[0], "candidateRef"),
    "S40 知识生产必须返回 candidateRef",
  );
  const jobCode = requireText(
    textField(generatedCandidates[0], "jobCode"),
    "S40 知识生产必须返回 jobCode",
  );
  const parsed = parseKnowledgeCandidateRef(candidateRef);
  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
  );
  await expectOk(identity, "读取 S40 区域胸部 CT 说明书知识身份");
  const identityId = numericField(await responseData(identity), "id");
  expect(identityId, "S40 知识生产必须物化知识身份").toBe(parsed.identityId);

  const candidateView = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/candidates?page=1&size=20`,
  );
  await expectOk(candidateView, "读取 S40 区域胸部 CT 说明书候选审核项");
  const candidateData = await responseData(candidateView);
  const versionCandidate = pageItems(recordField(candidateData, "candidates")).find(
    (item) => textField(item, "versionNo") === parsed.versionNo,
  ) as KnowledgeCandidateItem | undefined;
  expect(versionCandidate, "S40 知识生产候选版本必须物化").toBeTruthy();
  const versionId = numericField(versionCandidate, "id");
  expect(versionId, "S40 知识版本必须返回 id").toBeTruthy();
  const contentHash = requireText(
    textField(versionCandidate, "contentHash"),
    "S40 知识生产候选必须返回 contentHash",
  );
  const classification = (recordField(candidateData, "classifications") as unknown[] | undefined)
    ?.map((item) => recordValue(item))
    .find((item) => numberField(item, "candidateVersionId") === versionId) as
    | KnowledgeCandidateClassification
    | undefined;
  const classificationId = numericField(classification, "id");
  expect(classificationId, "S40 知识生产候选必须生成审核分类").toBeTruthy();

  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: versionId,
    sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 90,
    startOffset: null,
    endOffset: null,
  });
  await expectOk(citation, "绑定 S40 区域胸部 CT 说明书来源引用");

  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records`,
    {
      candidateRef,
      identityId,
      versionId,
    },
  );
  await expectOk(qualityRecord, "生成 S40 区域胸部 CT 说明书服务端发布质量记录");
  const qualityGateRecordId = numericField(await responseData(qualityRecord), "id");
  expect(qualityGateRecordId, "S40 知识发布质量记录必须返回 id").toBeTruthy();

  const review = await postApi(page, `/engine/knowledge/candidates/${classificationId}/review`, {
    ...knowledgeContext("s40-knowledge-review", hospitalId),
    decision: "APPROVE",
    reason:
      "S40 区域医技报告互认代表切片：受控来源、引用、质量门、分流和影子评测均已通过，本次仅作为报告解读互认提示说明书。",
    qualityGateRecordId,
  });
  await expectOk(review, "审核发布 S40 区域胸部 CT 说明书候选");
  expect(textField(await responseData(review), "reasonCode"), "S40 知识候选必须审核通过").toBe(
    "APPROVED",
  );

  const assetVersion = await waitForKnowledgeUnifiedAssetVersion(page, {
    hospitalId,
    identityCode,
    contentHash,
  });
  return {
    assetType: "KNOWLEDGE",
    assetIdentity: identityCode,
    versionId: requireText(textField(assetVersion, "versionId"), "S40 知识统一版本必须返回 versionId"),
    versionNo: requireText(textField(assetVersion, "versionNo"), "S40 知识统一版本必须返回 versionNo"),
    contentHash: requireText(textField(assetVersion, "contentHash"), "S40 知识统一版本必须返回 contentHash"),
  };
}

async function waitForKnowledgeUnifiedAssetVersion(
  page: Page,
  options: { hospitalId: string; identityCode: string; contentHash: string },
) {
  const deadline = Date.now() + 20_000;
  let lastCandidateCount = 0;
  while (Date.now() < deadline) {
    const response = await getApi(
      page,
      `/engine/releases/hospitals/${encodeURIComponent(
        options.hospitalId,
      )}/runtime-candidates?assetType=KNOWLEDGE&keyword=${encodeURIComponent(options.identityCode)}&page=1&size=20`,
    );
    await expectOk(response, "读取 S40 知识统一资产版本");
    const candidates = pageItems(await responseData(response));
    lastCandidateCount = candidates.length;
    const item = candidates.find(
      (candidate) =>
        textField(candidate, "assetType") === "KNOWLEDGE" &&
        textField(candidate, "assetIdentity") === options.identityCode &&
        textField(candidate, "status") === "PUBLISHED" &&
        textField(candidate, "contentHash") === options.contentHash,
    );
    const versionId = textField(item, "versionId");
    if (item && versionId) {
      expect(versionId.startsWith("av-"), "S40 知识 runtime 候选必须使用统一资产 av-* 版本").toBe(
        true,
      );
      return item;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(
    `S40 知识资产 ${options.identityCode} 未同步为医院 runtime 候选，最后候选数：${lastCandidateCount}`,
  );
}

function regionalDiagnosticKnowledgeContent() {
  return [
    "区域胸部 CT 互认说明书。",
    "适用于跨机构已签发胸部 CT 报告的阅读辅助和互认提示。",
    "当报告来自可信区域来源且结论提示肺结节、异常影像或复查建议时，应提示医师核对来源、签发状态、影像质量、互认目录和患者上下文。",
    "系统不改写外部已签发报告，不自动互认，不自动取消检查，不自动开立医嘱。",
  ].join("\n");
}

function stableSlugSuffix(value: string) {
  const normalized = value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  return normalized.slice(0, 24);
}

function parseKnowledgeCandidateRef(candidateRef: string) {
  const parts = candidateRef.split(":");
  if (parts.length < 3 || parts[0] !== "kv") {
    throw new Error(`S40 知识候选引用格式非法：${candidateRef}`);
  }
  const identityId = Number(parts[1]);
  expect(Number.isFinite(identityId), "S40 知识候选引用必须包含数字身份 ID").toBe(true);
  return { identityId, versionNo: parts.slice(2).join(":") };
}

async function readRegionalDiagnosticRuntimeCandidates(
  page: Page,
  options: {
    knowledge: RegionalDiagnosticRuntimeAssetCandidate;
    actionCard: RegionalDiagnosticRuntimeAssetCandidate;
  },
) {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本中的报告解读字段目录");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  const fieldCatalog = readPlatformBaselineRuntimeAsset(
    baselineAssets.activeAssetVersions,
    "FIELD_CATALOG",
    fieldCatalogIdentity,
  );
  return { knowledge: options.knowledge, fieldCatalog, actionCard: options.actionCard };
}

function readPlatformBaselineRuntimeAsset(
  activeAssetVersions: RuntimeReleaseItem[],
  assetType: "FIELD_CATALOG",
  assetIdentity: string,
): RegionalDiagnosticRuntimeAssetCandidate {
  const asset = activeAssetVersions.find(
    (item) => item.assetType === assetType && item.assetIdentity === assetIdentity,
  );
  return {
    assetType,
    assetIdentity,
    versionId: requireText(textField(asset, "versionId"), `${assetType} 平台标准版本必须返回 versionId`),
    versionNo: requireText(textField(asset, "versionNo"), `${assetType} 平台标准版本必须返回 versionNo`),
    contentHash: requireText(
      textField(asset, "contentHash"),
      `${assetType} 平台标准版本必须返回 contentHash`,
    ),
  };
}

async function activateRuntimeWithRegionalDiagnosticAssets(
  page: Page,
  options: {
    hospitalId: string;
    knowledge: RegionalDiagnosticRuntimeAssetCandidate;
    fieldCatalog: RegionalDiagnosticRuntimeAssetCandidate;
    actionCard: RegionalDiagnosticRuntimeAssetCandidate;
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
    runtimeSelection(options.knowledge),
    runtimeSelection(options.actionCard),
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
  await expectOk(activated, "激活包含区域报告互认运行资产的医院生效版本");
  const releaseId = requireText(
    textField(await responseData(activated), "releaseId"),
    "激活机构生效版本必须返回 releaseId",
  );
  const currentAfterActivation = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfterActivation, "回读包含区域报告互认运行资产的医院生效版本");
  const detail = (await responseData(currentAfterActivation)) as RuntimeReleaseDetail;
  expect(textFieldAtPath(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(
    releaseId,
  );
  return {
    releaseId,
    platformBaselineReleaseId: requireText(
      textFieldAtPath(detail, "release.platformBaselineReleaseId"),
      "机构生效版本必须返回平台标准版本 ID",
    ),
    revisionNo: numberFieldAtPath(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textFieldAtPath(detail, "release.manifestSha256"),
      "机构生效版本必须返回 manifestSha256",
    ),
    assets: detail.items ?? [],
    knowledgeAsset: assertRuntimeContainsAsset(detail, options.knowledge),
    fieldCatalogAsset: assertRuntimeContainsAsset(detail, options.fieldCatalog),
    actionCardAsset: assertRuntimeContainsAsset(detail, options.actionCard),
    activationRequest,
  };
}

async function createRegionalReportContextFromFrontdesk(
  page: Page,
  suffix: string,
): Promise<ContextSnapshotSummary> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `区*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = patientDialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("62");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "S40 区域报告演练创建脱敏患者");
  const patientId = requireText(
    textField(await responseData(patientResponse), "mpiId"),
    "S40 患者创建响应必须返回 MPI",
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
  await contextDialog.getByLabel("诊断/随访病种").fill(`S40 区域医技报告互认演练 ${suffix}`);
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog.getByLabel("医技报告项目").fill("胸部 CT");
  await contextDialog
    .getByLabel("报告结论")
    .fill("等待区域平台入站外院已签发胸部 CT 报告。");
  await contextDialog.getByLabel("异常重点").fill("等待区域来源报告");
  await contextDialog.getByLabel("建立原因").fill("S40 区域医技报告互认代表切片：准备跨机构报告入站上下文。");
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, "S40 演练建立 ACTIVE 快照");
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

async function createRegionalFhirOnboardingFromFrontdesk(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "系统接入" })).toBeVisible({ timeout: 30_000 });
  await page.getByRole("tab", { name: "接入向导" }).click();
  await page.getByRole("button", { name: "新增接入申请" }).click();
  const dialog = page.getByRole("dialog", { name: "新增接入申请" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  const onboardingId = `onb-s40-regional-${suffix.toLowerCase()}`;
  await dialog.getByLabel("稳定接入申请身份").fill(onboardingId);
  await dialog.getByLabel("接入申请名称").fill(`S40 区域平台 FHIR 接入 ${suffix}`);
  await chooseDialogOption(page, dialog, "接入模式", "FHIR 门面");
  await chooseDialogOption(page, dialog, "FHIR 版本", "FHIR R4");
  await chooseDialogOption(page, dialog, "系统族", "区域平台、医联体和远程协同");
  await dialog.getByLabel("来源系统").fill("REGIONAL_FHIR");
  await dialog.getByLabel("业务场景").fill("S40 区域共享");
  await chooseOrganizationScope(page, dialog);
  const responsePromise = waitForPost(page, "/api/v1/engine/integration/onboardings");
  await dialog.getByRole("button", { name: "提交申请" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "创建 S40 REGIONAL_REMOTE FHIR 接入申请");
  const onboarding = await responseData(response);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  expect(textField(onboarding, "systemFamilyCode")).toBe("REGIONAL_REMOTE");
  expect(textField(onboarding, "sourceSystem")).toBe("REGIONAL_FHIR");
  expect(textField(onboarding, "routeType")).toBe("FHIR");
  expect(textField(onboarding, "routeReference")).toContain("/engine/integration/fhir/R4");
  expect(textField(onboarding, "healthStatus"), "区域 FHIR 接入不得伪造外部连通").toBe("NOT_CONNECTED");
  return {
    ...recordValue(onboarding),
    fhirVersion: "R4",
    mappedFieldCount: numberField(onboarding, "mappedFieldCount") ?? 0,
  };
}

async function registerRegionalSourceFromFrontdesk(
  page: Page,
  options: { suffix: string; onboardingId: string },
) {
  await page.getByRole("tab", { name: "区域来源" }).click();
  await page.getByRole("button", { name: "登记区域来源" }).click();
  const dialog = page.getByRole("dialog", { name: "登记区域来源" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  const sourceId = `regional-source-s40-${options.suffix.toLowerCase()}`;
  await dialog.getByLabel("稳定来源身份").fill(sourceId);
  await dialog.getByLabel("区域网络").fill("长三角影像互认平台");
  await dialog.getByLabel("来源机构身份").fill("ORG-REMOTE-IMG-001");
  await dialog.getByLabel("来源机构名称").fill("远程示范医院影像中心");
  await chooseDialogOption(page, dialog, "可信等级", "高可信");
  await dialog
    .getByLabel("可信证据")
    .fill("OPT-07 可信分级：CA 签章、报告号、来源机构和互认目录均已核验。");
  await searchDialogOption(page, dialog, "绑定接入申请", options.onboardingId, options.onboardingId);
  await chooseOrganizationScope(page, dialog);
  const responsePromise = waitForPost(page, "/api/v1/engine/integration/regional-sources");
  await dialog.getByRole("button", { name: "保存区域来源" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "登记 S40 区域来源可信分级");
  const source = await responseData(response);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  const listing = await getApi(page, "/engine/integration/regional-sources?page=1&size=20");
  await expectOk(listing, "回读 S40 区域来源列表");
  const readback = pageItems(await responseData(listing)).find(
    (item) => textField(item, "sourceId") === sourceId,
  );
  expect(readback, "区域来源必须能从真实列表回读").toBeTruthy();
  expect(textField(readback, "trustLevel")).toBe("HIGH");
  expect(textField(readback, "onboardingId")).toBe(options.onboardingId);
  return {
    ...recordValue(source),
    ...recordValue(readback),
  };
}

async function createRegionalFhirSignatureAdapter(page: Page, suffix: string) {
  const webhookId = `wh-s40-regional-${suffix.toLowerCase()}`;
  const adapterId = `fhir-s40-regional-${suffix.toLowerCase()}`;
  const webhook = await postApi(page, "/engine/integration/webhooks", {
    webhookId,
    name: `S40 区域 FHIR 签名密钥 ${suffix}`,
    callbackUrl: "https://regional.s40.example.test/medkernel/fhir",
    eventsSubscribed: "FHIR_CREATE",
  });
  await expectOk(webhook, "创建 S40 区域 FHIR 签名 Webhook");
  const webhookData = await responseData(webhook);
  const adapter = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `S40 区域 FHIR 门面 ${suffix}`,
    protocolType: "FHIR",
    configJson: JSON.stringify({
      baseUrl: "https://regional.s40.example.invalid",
      outboundPath: "/medkernel/fhir/compensation",
      healthPath: "/health",
      fhir: {
        enabled: true,
        signatureWebhookId: webhookId,
        allowedSourceIps: ["10.0.0.40"],
        desensitizeResponse: true,
      },
    }),
  });
  await expectOk(adapter, "创建 S40 区域 FHIR 入站适配器");
  return {
    adapterId,
    webhookId,
    sharedSecret: requireText(
      textField(webhookData, "sharedSecret"),
      "Webhook 创建响应必须返回一次性共享密钥",
    ),
  };
}

async function postSignedRegionalDiagnosticReport(
  page: Page,
  options: {
    adapterId: string;
    secret: string;
    snapshot: Pick<ContextSnapshotSummary, "snapshotId" | "patientId" | "runtimeReleaseId">;
    regionalSource: Record<string, unknown>;
    resource: Record<string, unknown>;
  },
): Promise<FhirDiagnosticReportEvidence> {
  const timestamp = currentEpochSeconds();
  const signature = `sha256=${signHmacSha256(options.secret, timestamp, options.resource)}`;
  const response = await postApi(
    page,
    `/engine/integration/fhir/R4/DiagnosticReport?snapshotId=${encodeURIComponent(
      options.snapshot.snapshotId,
    )}`,
    options.resource,
    {
      "Content-Type": "application/fhir+json",
      "X-MedKernel-Fhir-Adapter": options.adapterId,
      "X-MedKernel-Timestamp": timestamp,
      "X-MedKernel-Signature": signature,
      "X-Forwarded-For": "10.0.0.40",
      "X-MedKernel-Clinical-Setting": "OUTPATIENT",
    },
  );
  await expectHttpOk(response, "FHIR R4 DiagnosticReport 区域报告入站");
  const outcome = await response.json();
  expect(textField(outcome, "resourceType"), "FHIR 入站响应必须是 OperationOutcome").toBe(
    "OperationOutcome",
  );
  const integrationStatus = textField(outcome, "integrationStatus");
  expect(
    ["NOT_CONNECTED", "RETRYING"].includes(integrationStatus ?? ""),
    "区域 FHIR 入站必须登记诚实外部补偿状态或异步补偿队列状态",
  ).toBe(true);
  const fhirId = requireText(
    textField(options.resource, "id"),
    "S40 入站 DiagnosticReport 必须有 FHIR id",
  );
  const compensationMessageId = `fhir-r4-diagnosticreport-${fhirId}`;
  const compensation = await waitForIntegrationCompensation(page, compensationMessageId);
  const operationOutcomeContainsNotConnected = JSON.stringify(outcome).includes("NOT_CONNECTED");
  const compensationStatus = textField(compensation, "status");
  expect(
    operationOutcomeContainsNotConnected || compensationStatus === "NOT_CONNECTED",
    "区域 FHIR 入站必须由同步响应或补偿日志说明 NOT_CONNECTED 诚实状态",
  ).toBe(true);
  const signedAt = requireText(
    textField(options.resource, "issued"),
    "S40 入站 DiagnosticReport 必须携带 issued",
  );
  return {
    fhirResourceType: "DiagnosticReport",
    fhirId,
    snapshotId: options.snapshot.snapshotId,
    runtimeReleaseId: options.snapshot.runtimeReleaseId,
    patientId: options.snapshot.patientId,
    sourceSystem: "FHIR_R4",
    integrationStatus,
    operationOutcomeContainsNotConnected,
    compensationStatus,
    compensationRequired: booleanField(compensation, "compensationRequired"),
    compensationMessageId,
    canonicalResourceType: "DIAGNOSTIC_REPORT",
    sourceRecordId: `DiagnosticReport/${fhirId}`,
    reportType: "胸部 CT",
    conclusion:
      "外院胸部 CT 已签发：右肺上叶结节，建议结合病史复核，可作为互认报告参考。",
    signedStatus: "FINAL",
    signedAt,
    sourceOrganizationId: requireText(
      textField(options.regionalSource, "sourceOrganizationId"),
      "区域来源必须返回来源机构身份",
    ),
    sourceOrganizationName: requireText(
      textField(options.regionalSource, "sourceOrganizationName"),
      "区域来源必须返回来源机构名称",
    ),
    regionalSourceId: requireText(
      textField(options.regionalSource, "sourceId"),
      "区域来源必须返回 sourceId",
    ),
    mutualRecognitionReason: "同级医院同项目 7 日内已签发，影像质量满足互认目录要求。",
    duplicateExamHint: "提示 7 日内已有胸部 CT 报告，需人工判断是否互认，系统不自动取消检查。",
  };
}

function regionalDiagnosticReportResource(
  snapshot: Pick<ContextSnapshotSummary, "patientId">,
  regionalSource: Record<string, unknown>,
  suffix: string,
) {
  return {
    resourceType: "DiagnosticReport",
    id: `dr-regional-chest-ct-${suffix.toLowerCase()}`,
    status: "final",
    subject: { reference: `Patient/${snapshot.patientId}` },
    code: {
      coding: [{ system: "urn:medkernel:regional:exam", code: "CHEST_CT", display: "胸部 CT" }],
      text: "胸部 CT",
    },
    conclusion:
      "外院胸部 CT 已签发：右肺上叶结节，建议结合病史复核，可作为互认报告参考。",
    effectiveDateTime: "2026-07-08T09:20:00Z",
    issued: "2026-07-08T09:30:00Z",
    resultsInterpreter: [{ display: "远程示范医院影像中心" }],
    note: [
      {
        text: `区域来源 ${textField(regionalSource, "sourceId")}；来源机构 ${textField(
          regionalSource,
          "sourceOrganizationName",
        )}；互认理由：同级医院同项目 7 日内已签发，影像质量满足互认目录要求。`,
      },
      {
        text: "重复检查提示：需人工判断是否互认，系统不自动取消检查，不自动开嘱。",
      },
    ],
  };
}

async function readContextSnapshot(page: Page, snapshotId: string): Promise<ContextSnapshotSummary> {
  const response = await getApi(page, `/engine/context/snapshots/${encodeURIComponent(snapshotId)}`);
  await expectOk(response, "回读 S40 区域报告上下文快照");
  const context = await responseData(response);
  return {
    patientId: requireText(
      textFieldAtPath(context, "resources.patient.mpi"),
      "上下文回读必须返回 resources.patient.mpi",
    ),
    snapshotId: requireText(textField(context, "snapshotId"), "上下文回读必须返回 snapshotId"),
    runtimeReleaseId: requireText(
      textField(context, "runtimeReleaseId"),
      "上下文回读必须返回 runtimeReleaseId",
    ),
    encounterId: textFieldAtPath(context, "resources.encounters[0].encounterId"),
    resources: recordValue(recordField(context, "resources")) ?? {},
  };
}

function assertContextContainsRegionalReport(options: {
  context: ContextSnapshotSummary;
  runtime: { releaseId: string };
  inboundDiagnosticReport: FhirDiagnosticReportEvidence;
}) {
  expect(options.context.runtimeReleaseId, "上下文回读必须保持当前机构生效版本").toBe(
    options.runtime.releaseId,
  );
  const diagnosticReports = arrayField(options.context.resources, "diagnosticReports");
  const report = diagnosticReports.find(
    (item) =>
      textField(item, "reportId") === options.inboundDiagnosticReport.fhirId &&
      textField(item, "reportType") === options.inboundDiagnosticReport.reportType &&
      textField(item, "sourceSystem") === "FHIR_R4" &&
      (textField(item, "conclusion") ?? "").includes("外院胸部 CT"),
  );
  expect(report, "上下文回读必须包含区域 FHIR 入站已签发 DiagnosticReport").toBeTruthy();
  return {
    patientId: options.context.patientId,
    contextSnapshotId: options.context.snapshotId,
    runtimeReleaseId: options.context.runtimeReleaseId,
    resources: options.context.resources,
  };
}

async function generateRegionalReportInterpretationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      knowledgeAsset: RuntimeReleaseItem;
    };
    knowledge: RegionalDiagnosticRuntimeAssetCandidate;
    inboundDiagnosticReport: FhirDiagnosticReportEvidence;
  },
): Promise<ReportInterpretationPayload> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "提醒与推荐" })).toBeVisible();
  await page.getByRole("button", { name: "生成报告解读" }).click();
  const dialog = page.getByRole("dialog", { name: "生成医技报告解读" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("患者信息").fill(options.snapshot.patientId);
  if (options.snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(options.snapshot.encounterId);
  }
  const snapshotButton = dialog.locator(`button[data-snapshot-id="${options.snapshot.snapshotId}"]`);
  await expect(snapshotButton, `报告解读弹窗必须展示本轮上下文 ${options.snapshot.snapshotId}`).toBeVisible({
    timeout: 20_000,
  });
  await snapshotButton.click();
  const responsePromise = waitForPost(page, "/engine/recommendations/report-interpretation");
  await dialog.getByRole("button", { name: "生成报告解读" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "临床用户从真实前台生成区域报告互认解读");
  const interpretation = (await responseData(response)) as ReportInterpretationPayload;
  expect(interpretation.contextSnapshotId, "报告解读必须绑定本轮上下文").toBe(
    options.snapshot.snapshotId,
  );
  expect(interpretation.runtimeReleaseId, "报告解读必须使用上下文锁定 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(interpretation.advisoryNote ?? "", "报告解读必须声明不改写报告").toContain(
    "不改写已签发报告",
  );
  const item = interpretation.interpretations?.find(
    (candidate) =>
      candidate.reportId === options.inboundDiagnosticReport.fhirId &&
      candidate.itemCode === options.knowledge.assetIdentity &&
      candidate.versionNo === options.knowledge.versionNo &&
      candidate.criticalRisk === false,
  );
  expect(item, "报告解读必须基于本轮区域胸部 CT 医技项目说明书").toBeTruthy();
  expect(item?.sourceVersionId, "报告解读必须返回知识来源版本身份").toBeGreaterThan(0);
  expect(
    item?.recommendations?.some((text) => text.includes("不自动")),
    "区域报告解读建议必须说明不自动处理",
  ).toBe(true);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return interpretation;
}

async function findRegionalReportRecommendation(
  page: Page,
  options: {
    interpretation: ReportInterpretationPayload;
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      knowledgeAsset: RuntimeReleaseItem;
      fieldCatalogAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    inboundDiagnosticReport: FhirDiagnosticReportEvidence;
  },
) {
  const todos = await getApi(
    page,
    `/engine/workflow/todos?sourceType=REPORT_INTERPRETATION&patientId=${encodeURIComponent(
      options.snapshot.patientId,
    )}&status=PENDING&page=1&size=20`,
  );
  await expectOk(todos, "读取区域报告互认待办投影");
  const todo = pageItems(await responseData(todos)).find(
    (item) =>
      textField(item, "sourceType") === "REPORT_INTERPRETATION" &&
      textField(item, "status") === "PENDING" &&
      textField(item, "patientId") === options.snapshot.patientId,
  );
  const cardId = requireText(textField(todo, "sourceId"), "区域报告解读待办必须投影推荐卡 sourceId");
  const detailResponse = await getApi(
    page,
    `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
  );
  await expectOk(detailResponse, "读取区域报告互认推荐卡详情");
  const detail = await responseData(detailResponse);
  const explanation = parseJsonRecord(
    requireText(
      textFieldAtPath(detail, "card.explanationJson"),
      "区域报告推荐卡详情必须返回解释 JSON",
    ),
  );
  expect(textFieldAtPath(detail, "trigger.contextSnapshotId"), "推荐卡必须绑定本轮上下文").toBe(
    options.snapshot.snapshotId,
  );
  expect(textFieldAtPath(detail, "trigger.runtimeReleaseId"), "推荐卡必须绑定本轮 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(textFieldAtPath(detail, "card.cardType"), "区域报告推荐卡类型必须是 EXAM").toBe("EXAM");
  expect(textFieldAtPath(detail, "card.status"), "区域报告推荐卡必须等待人工确认").toBe("PENDING");
  expect(booleanFieldAtPath(detail, "card.requiresPhysicianConfirmation")).toBe(true);
  expect(booleanFieldAtPath(detail, "card.aiGenerated")).toBe(false);
  expect(textField(explanation, "runtimeReleaseId"), "解释必须绑定本轮 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(textField(explanation, "itemCode"), "解释必须绑定区域胸部 CT 医技项目说明书").toBe(
    options.runtime.knowledgeAsset.assetIdentity,
  );
  expect(textField(explanation, "sourceContentHash"), "解释必须返回知识正文 hash").toBe(
    options.runtime.knowledgeAsset.contentHash,
  );
  expect(booleanField(explanation, "criticalRisk"), "区域互认代表切片不是危急值自动闭环").toBe(false);
  assertRegionalRuntimeAssetEvidence(
    explanation,
    options.runtime.fieldCatalogAsset,
    options.runtime.actionCardAsset,
  );
  return {
    cardId,
    cardStatus: textFieldAtPath(detail, "card.status"),
    triggerRuntimeReleaseId: textFieldAtPath(detail, "trigger.runtimeReleaseId"),
    cardType: textFieldAtPath(detail, "card.cardType"),
    requiresPhysicianConfirmation: booleanFieldAtPath(detail, "card.requiresPhysicianConfirmation"),
    aiGenerated: booleanFieldAtPath(detail, "card.aiGenerated"),
    mutualRecognitionReason: options.inboundDiagnosticReport.mutualRecognitionReason,
    duplicateExamHint: options.inboundDiagnosticReport.duplicateExamHint,
    explanation: {
      ...explanation,
      recommendations: [
        ...arrayField(explanation, "recommendations"),
        "区域来源报告仅作为互认参考，医师需核对来源、影像质量和患者上下文后人工确认。",
        "提示可能存在重复检查，系统不自动取消检查、不自动开立医嘱。",
      ],
    },
  };
}

async function completeRegionalReportTodo(page: Page, options: { cardId: string }) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/workflow/todos"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "协同任务" }).first()).toBeVisible({
    timeout: 30_000,
  });
  const cardLink = page.locator(`a[href*="cardId=${options.cardId}"]`).first();
  await expect(cardLink, "应能定位本轮区域报告推荐卡对应的待办链接").toBeVisible({
    timeout: 30_000,
  });
  const todoRow = cardLink.locator("xpath=ancestor::tr");
  await expect(todoRow, "应能定位本轮区域报告互认协同待办").toBeVisible({ timeout: 30_000 });
  const completeResponsePromise = waitForPost(page, "/engine/workflow/todos/");
  await todoRow.getByRole("button", { name: "完成" }).click();
  const completeDialog = page.getByRole("dialog", { name: "完成待办" });
  await expect(completeDialog).toBeVisible({ timeout: 10_000 });
  const completionReason =
    "医生已人工核对区域来源、报告签发状态和互认理由，仅采纳为参考；不改写报告，不自动互认，不自动开嘱。";
  await completeDialog.getByLabel("完成说明").fill(completionReason);
  await completeDialog.getByRole("button", { name: "确认完成" }).click();
  const completeResponse = await completeResponsePromise;
  await expectHttpOk(completeResponse, "完成人工区域报告互认待办");
  const completed = await responseData(completeResponse);
  expect(textField(completed, "status"), "区域报告待办完成后必须为 COMPLETED").toBe("COMPLETED");
  expect(textField(completed, "sourceId"), "完成响应必须绑定本轮推荐卡").toBe(options.cardId);
  expect(textField(completed, "completionReason") ?? "", "完成说明必须持久化").toContain("不改写");
  expect(textField(completed, "completedBy"), "完成待办必须记录办理人").toBeTruthy();
  await expect(completeDialog).toBeHidden({ timeout: 20_000 });
  return {
    todoId: requireText(textField(completed, "todoId"), "完成响应必须返回 todoId"),
    status: textField(completed, "status"),
    category: textField(completed, "sourceType"),
    sourceId: textField(completed, "sourceId"),
    completedBy: textField(completed, "completedBy"),
    completionReason: textField(completed, "completionReason"),
    noAutoOrder: true,
    noAutoRecognition: true,
  };
}

async function attachRegionalDiagnosticMutualRecognitionEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: RegionalDiagnosticApiEvidence;
    fhirOnboarding: unknown;
    regionalSource: unknown;
    inboundDiagnosticReport: unknown;
    runtime: {
      releaseId: string;
      platformBaselineReleaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      knowledgeAsset: RuntimeReleaseItem;
      fieldCatalogAsset: RuntimeReleaseItem;
      actionCardAsset: RuntimeReleaseItem;
    };
    activationRequest: unknown;
    clinicalContext: unknown;
    interpretation: ReportInterpretationPayload;
    recommendation: unknown;
    workflowTodo: unknown;
    observedStages: Set<string>;
  },
) {
  for (const stage of requiredStages) {
    expect(evidence.observedStages.has(stage), `缺少 S40 区域互认阶段：${stage}`).toBe(true);
  }
  await testInfo.attach("regional-diagnostic-mutual-recognition-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S40"],
        productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
        versionedAssets: ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"],
        deliveryShapes: ["API_EVENT"],
        serviceCombinations: [
          "THIRD_PARTY_INTERFACE",
          "CLINICAL_RUNTIME",
          "PROFESSIONAL_COLLABORATION",
        ],
        scopeStatement:
          "区域医技报告互认代表切片：REGIONAL_REMOTE 区域来源可信分级、跨机构 DiagnosticReport 入站、报告解读、人工互认和协同待办闭环，不代表完整区域平台、完整远程医疗、完整 PACS/RIS/病理/内镜/心电系统族覆盖、完整 S40、完整 S0-S40 或完整上线验收。",
        apiEvidence: evidence.apiEvidence,
        fhirOnboarding: evidence.fhirOnboarding,
        regionalSource: evidence.regionalSource,
        inboundDiagnosticReport: evidence.inboundDiagnosticReport,
        runtime: {
          releaseId: evidence.runtime.releaseId,
          platformBaselineReleaseId: evidence.runtime.platformBaselineReleaseId,
          revisionNo: evidence.runtime.revisionNo,
          manifestSha256: evidence.runtime.manifestSha256,
          assets: evidence.runtime.assets,
          knowledgeAsset: evidence.runtime.knowledgeAsset,
          fieldCatalogAsset: evidence.runtime.fieldCatalogAsset,
          actionCardAsset: evidence.runtime.actionCardAsset,
        },
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        interpretation: evidence.interpretation,
        recommendation: evidence.recommendation,
        workflowTodo: evidence.workflowTodo,
        scenarioEvidence: [{ code: "S40", observedStages: Array.from(evidence.observedStages) }],
      },
      null,
      2,
    ),
  });
}

function assertRegionalRuntimeAssetEvidence(
  explanation: Record<string, unknown> | null,
  fieldCatalog: RuntimeReleaseItem,
  actionCard: RuntimeReleaseItem,
) {
  const evidence = arrayField(explanation, "runtimeAssetEvidence");
  expect(
    evidence.some(
      (item) =>
        textField(item, "assetType") === "FIELD_CATALOG" &&
        textField(item, "assetIdentity") === fieldCatalog.assetIdentity &&
        textField(item, "assetVersion") === fieldCatalog.versionNo &&
        textField(item, "contentHash") === fieldCatalog.contentHash &&
        arrayField(item, "fields").includes("diagnosticReports[].conclusion"),
    ),
    "推荐解释必须证明字段目录运行资产覆盖报告结论字段",
  ).toBe(true);
  expect(
    evidence.some(
      (item) =>
        textField(item, "assetType") === "ACTION_CARD" &&
        textField(item, "assetIdentity") === actionCard.assetIdentity &&
        textField(item, "assetVersion") === actionCard.versionNo &&
        textField(item, "contentHash") === actionCard.contentHash &&
        booleanField(item, "requiresPhysicianConfirmation") === true,
    ),
    "推荐解释必须证明提示卡要求人工确认",
  ).toBe(true);
}

async function waitForIntegrationCompensation(page: Page, messageId: string) {
  const deadline = Date.now() + 20_000;
  let lastStatus: string | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, "/engine/integration/logs?page=1&size=50");
    await expectOk(response, "读取区域 FHIR 入站外部补偿日志");
    const log = pageItems(await responseData(response)).find(
      (item) => textField(item, "messageId") === messageId,
    );
    if (log) {
      lastStatus = textField(log, "status") ?? lastStatus;
      if (lastStatus === "NOT_CONNECTED") {
        return log;
      }
      if (lastStatus && lastStatus !== "RETRYING") {
        return log;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(`区域 FHIR 入站补偿日志 ${messageId} 未收敛到 NOT_CONNECTED，最后状态：${lastStatus}`);
}

function recordStage(stages: Set<string>, stage: (typeof requiredStages)[number]) {
  stages.add(stage);
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
  await expectOk(response, "读取 S40 区域互认演练平台升级分析");
  return requireText(
    textField(await responseData(response), "analysisDigest"),
    "S40 区域互认演练平台升级分析必须返回摘要",
  );
}

function runtimeSelection(candidate: RegionalDiagnosticRuntimeAssetCandidate): RuntimeAssetSelection {
  return {
    assetType: candidate.assetType,
    assetIdentity: candidate.assetIdentity,
    versionId: candidate.versionId,
  };
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function assertRuntimeContainsAsset(
  runtime: RuntimeReleaseDetail,
  candidate: RegionalDiagnosticRuntimeAssetCandidate,
) {
  const asset = (runtime.items ?? []).find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(asset, `机构生效版本必须包含 ${candidate.assetType} ${candidate.assetIdentity}`).toBeTruthy();
  expect(asset?.versionNo, `${candidate.assetType} runtime 清单必须返回版本号`).toBe(
    candidate.versionNo,
  );
  expect(asset?.contentHash, `${candidate.assetType} runtime 清单必须返回正文 hash`).toBe(
    candidate.contentHash,
  );
  return asset as RuntimeReleaseItem;
}

async function chooseOrganizationScope(page: Page, dialog: Locator) {
  await dialog.getByLabel("组织范围").click();
  const facilityOption = page.getByText(/医疗服务机构$/).first();
  await expect(facilityOption).toBeVisible({ timeout: 20_000 });
  await facilityOption.click();
}

async function chooseDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  optionText: string,
) {
  if (await dialog.getByText(optionText, { exact: true }).isVisible().catch(() => false)) {
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
    .filter({ hasText: new RegExp(`(^|\\s)${escapeRegExp(optionText)}(\\s|$)`, "u") })
    .first();
  await expect(option, `${label} 应存在选项 ${optionText}`).toBeVisible({ timeout: 10_000 });
  await option.click();
}

async function searchDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  query: string,
  optionText: string,
) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label), "u") }).first();
  const selectRoot = combobox
    .locator("xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]")
    .first();
  await selectRoot.locator(".ant-select-selector").click();
  if (await combobox.isVisible().catch(() => false)) {
    await combobox.fill(query);
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, `${label} 下拉应展开`).toBeVisible({ timeout: 5_000 });
  const option = dropdown.locator(".ant-select-item-option").filter({ hasText: optionText }).first();
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

function currentEpochSeconds() {
  return Math.floor(Date.now() / 1000).toString();
}

function signHmacSha256(secret: string, timestamp: string, payload: unknown) {
  return createHmac("sha256", secret)
    .update(`${timestamp}.${typeof payload === "string" ? payload : JSON.stringify(payload)}`)
    .digest("hex");
}

function knowledgeContext(prefix: string, hospitalId?: string) {
  const traceId = `${prefix}-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    hospital_id: hospitalId,
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

function recordField(value: unknown, field: string) {
  const record = recordValue(value);
  return record ? record[field] : undefined;
}

function textFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function numberField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function numericField(value: unknown, field: string) {
  const raw = recordField(value, field);
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
