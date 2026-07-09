import { createHmac } from "node:crypto";
import { expect, test, type APIResponse, type Locator, type Page, type TestInfo } from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  postApi,
  requiredRuntimeAssetsForRehearsal,
  resolveBaselineRuntimeAssets,
  resolvedTenantIdFor,
  responseData,
  textField,
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
    platformBaselineReleaseId?: string;
  };
  items?: RuntimeReleaseItem[];
};

type PublicHealthAssetCandidate = {
  assetType: "TERMINOLOGY" | "RULE" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type PublicHealthActionCardCandidate = PublicHealthAssetCandidate & {
  assetType: "ACTION_CARD";
  requiresHumanReportReview: boolean;
  noLegalAutoSubmit: boolean;
};

type PublicHealthApiEvidence = {
  publicHealthAdapterCreatedThroughRealService: boolean;
  publicHealthWebhookCreatedThroughRealService: boolean;
  webhookSignaturePreviewGenerated: boolean;
  infectionTerminologyActivated: boolean;
  publicHealthActionCardPublished: boolean;
  publicHealthRuleCreated: boolean;
  runtimeActivatedWithPublicHealthAssets: boolean;
  contextSnapshotCreatedFromFrontdesk: boolean;
  prefillOutboundRequested: boolean;
  inboundPublicHealthReportAccepted: boolean;
  clinicalEvaluationTriggeredFromFrontdesk: boolean;
  humanReportReviewRecorded: boolean;
  safetyRectificationSubmittedAndReviewed: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
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

const publicHealthSourceSystem = "PUBLIC_HEALTH_INFECTION_REGULATORY";
const diagnosisStandardCode = "U07.100";
const diagnosisLocalCode = "PH-COVID-19";
const observationCode = "NAT_RESULT";
const actionCardIdentityPrefix = "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL";
const ruleIdentityPrefix = "RULE.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL";

const requiredStages = {
  S21: [
    "平台管理员访问真实前台并经真实服务创建 PUBLIC_HEALTH_INFECTION_REGULATORY 适配器、回调通道和签名预览",
    "运营员发布院感公卫术语、上报预填规则和动作卡资产",
    "当前机构生效版本包含院感公卫三类运行资产",
    "临床用户从患者 360 建立脱敏患者，签名入站事件生成感染诊断、检验结果和上报预填上下文",
    "系统向 PUBLIC_HEALTH_INFECTION_REGULATORY 发出上报预填回传并诚实断连降级",
    "PUBLIC_HEALTH_INFECTION_REGULATORY 签名回传感染监测结果并生成标准临床事件",
    "临床用户从真实前台触发 result-review 推荐评估",
    "推荐卡证明上报预填规则和动作卡按当前机构生效版本消费",
    "临床用户人工确认上报预填，系统不替代法定上报",
  ],
  S32: [
    "入站安全事件保留风险、原因和整改要求扩展证据",
    "医疗安全事件形成整改任务",
    "固定四职责账号提交并复核关闭本轮安全事件整改任务",
  ],
} as const;

test.describe("院感公卫与医疗安全事件代表切片真实前台闭环", () => {
  test(
    "临床用户与运营员、平台管理员完成院感公卫上报预填和医疗安全事件整改代表闭环",
    async ({ page }, testInfo) => {
      test.setTimeout(360_000);
      const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;
      const observedStages = new Set<string>();
      const apiEvidence = createApiEvidence();

      await ensureReadySession(page, "platform-admin");
      const adapter = await createPublicHealthAdapter(page, suffix);
      apiEvidence.publicHealthAdapterCreatedThroughRealService = true;
      const webhook = await createPublicHealthWebhook(page, suffix);
      apiEvidence.publicHealthWebhookCreatedThroughRealService = true;
      await generatePublicHealthSignaturePreview(page, webhook.webhookId);
      apiEvidence.webhookSignaturePreviewGenerated = true;
      recordStage(
        observedStages,
        "平台管理员访问真实前台并经真实服务创建 PUBLIC_HEALTH_INFECTION_REGULATORY 适配器、回调通道和签名预览",
      );

      await ensureReadySession(page, "engine-operator");
      const hospitalId = await localRehearsalHospitalId(page);
      const terminologyGate = await createPublicHealthTerminologyGate(page, suffix);
      apiEvidence.infectionTerminologyActivated = true;
      const actionCard = await createPublicHealthActionCard(page, suffix);
      apiEvidence.publicHealthActionCardPublished = true;
      const preRuleRuntime = await activateRuntimeWithPublicHealthAssets(page, {
        hospitalId,
        terminology: terminologyGate,
        actionCard,
      });

      const positivePatient = await createPublicHealthPatientFromFrontdesk(page, `${suffix}-POS`);
      const positiveInbound = await postSignedPublicHealthInbound(page, {
        suffix: `${suffix}-POS`,
        adapterId: adapter.adapterId,
        webhookId: webhook.webhookId,
        webhookSecret: webhook.sharedSecret,
        patientId: positivePatient.patientId,
        encounterId: positivePatient.encounterId,
        traceId: `trace-public-health-rule-pos-${suffix}`,
        runtimeReleaseId: preRuleRuntime.releaseId,
        positive: true,
      });
      const positiveSnapshot = await readLatestContextForPatient(page, positivePatient.patientId);
      expect(positiveSnapshot.runtimeReleaseId, "规则阳性用例快照必须绑定预备 runtime").toBe(
        preRuleRuntime.releaseId,
      );
      const negativePatient = await createPublicHealthPatientFromFrontdesk(page, `${suffix}-NEG`);
      await postSignedPublicHealthInbound(page, {
        suffix: `${suffix}-NEG`,
        adapterId: adapter.adapterId,
        webhookId: webhook.webhookId,
        webhookSecret: webhook.sharedSecret,
        patientId: negativePatient.patientId,
        encounterId: negativePatient.encounterId,
        traceId: `trace-public-health-rule-neg-${suffix}`,
        runtimeReleaseId: preRuleRuntime.releaseId,
        positive: false,
      });
      const negativeSnapshot = await readLatestContextForPatient(page, negativePatient.patientId);
      await ensureReadySession(page, "engine-operator");
      const rule = await createAndPublishPublicHealthRule(page, suffix, {
        positiveContextSnapshotId: positiveSnapshot.snapshotId,
        negativeContextSnapshotId: negativeSnapshot.snapshotId,
        actionCard,
      });
      apiEvidence.publicHealthRuleCreated = true;
      recordStage(observedStages, "运营员发布院感公卫术语、上报预填规则和动作卡资产");

      const ruleCandidate = await readHospitalRuntimeCandidate(
        page,
        hospitalId,
        "RULE",
        rule.assetIdentity,
      );
      const ruleEvidence = {
        ...rule,
        versionId: ruleCandidate.versionId,
        versionNo: ruleCandidate.versionNo,
        contentHash: ruleCandidate.contentHash,
      };
      const runtime = await activateRuntimeWithPublicHealthAssets(page, {
        hospitalId,
        terminology: terminologyGate,
        rule: ruleCandidate,
        actionCard,
      });
      apiEvidence.runtimeActivatedWithPublicHealthAssets = true;
      recordStage(observedStages, "当前机构生效版本包含院感公卫三类运行资产");

      const patient = await createPublicHealthPatientFromFrontdesk(page, suffix);
      apiEvidence.contextSnapshotCreatedFromFrontdesk = true;
      const inboundReport = await postSignedPublicHealthInbound(page, {
        suffix,
        adapterId: adapter.adapterId,
        webhookId: webhook.webhookId,
        webhookSecret: webhook.sharedSecret,
        patientId: patient.patientId,
        encounterId: patient.encounterId,
        traceId: `trace-public-health-${suffix}`,
        runtimeReleaseId: runtime.releaseId,
        positive: true,
      });
      apiEvidence.inboundPublicHealthReportAccepted = true;
      const snapshot = await readLatestContextForPatient(page, patient.patientId);
      expect(snapshot.runtimeReleaseId, "正式入站上下文必须绑定本轮 runtime").toBe(
        runtime.releaseId,
      );
      assertSnapshotContainsPublicHealthFacts(snapshot.resources);
      const outboundPrefill = await sendPublicHealthOutbound(page, {
        suffix,
        adapterId: adapter.adapterId,
        patientId: patient.patientId,
        contextSnapshotId: snapshot.snapshotId,
        traceId: inboundReport.traceId,
      });
      apiEvidence.prefillOutboundRequested = true;
      recordStage(
        observedStages,
        "系统向 PUBLIC_HEALTH_INFECTION_REGULATORY 发出上报预填回传并诚实断连降级",
      );
      recordStage(
        observedStages,
        "临床用户从患者 360 建立脱敏患者，签名入站事件生成感染诊断、检验结果和上报预填上下文",
      );
      recordStage(
        observedStages,
        "PUBLIC_HEALTH_INFECTION_REGULATORY 签名回传感染监测结果并生成标准临床事件",
      );
      recordStage(observedStages, "入站安全事件保留风险、原因和整改要求扩展证据");

      const recommendation = await triggerPublicHealthRecommendationFromFrontdesk(page, {
        snapshot,
        runtime,
        rule: ruleEvidence,
      });
      apiEvidence.clinicalEvaluationTriggeredFromFrontdesk = true;
      recordStage(observedStages, "临床用户从真实前台触发 result-review 推荐评估");
      recordStage(observedStages, "推荐卡证明上报预填规则和动作卡按当前机构生效版本消费");

      const manualReview = await completePublicHealthManualReview(page, {
        cardId: recommendation.cardId,
        actionCard,
        actionCardAsset: runtime.actionCardAsset,
      });
      apiEvidence.humanReportReviewRecorded = true;
      recordStage(observedStages, "临床用户人工确认上报预填，系统不替代法定上报");

      const qualityRectification = await createAndCloseSafetyEventRectification(page, {
        suffix,
        recommendation,
        snapshot,
        runtimeReleaseId: runtime.releaseId,
      });
      apiEvidence.safetyRectificationSubmittedAndReviewed = true;
      recordStage(observedStages, "医疗安全事件形成整改任务");
      recordStage(observedStages, "固定四职责账号提交并复核关闭本轮安全事件整改任务");

      await attachInfectionPublicHealthSafetyEvidence(testInfo, {
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
        outboundPrefill,
        inboundReport: {
          ...inboundReport,
          contextSnapshotId: snapshot.snapshotId,
        },
        clinicalTrigger: {
          triggerId: recommendation.triggerId,
          contextSnapshotId: snapshot.snapshotId,
          runtimeReleaseId: runtime.releaseId,
          cardId: recommendation.cardId,
          relatedCardIds: recommendation.relatedCardIds,
        },
        recommendation,
        manualReview,
        qualityRectification,
        observedStages,
      });
    },
  );
});

function createApiEvidence(): PublicHealthApiEvidence {
  return {
    publicHealthAdapterCreatedThroughRealService: false,
    publicHealthWebhookCreatedThroughRealService: false,
    webhookSignaturePreviewGenerated: false,
    infectionTerminologyActivated: false,
    publicHealthActionCardPublished: false,
    publicHealthRuleCreated: false,
    runtimeActivatedWithPublicHealthAssets: false,
    contextSnapshotCreatedFromFrontdesk: false,
    prefillOutboundRequested: false,
    inboundPublicHealthReportAccepted: false,
    clinicalEvaluationTriggeredFromFrontdesk: false,
    humanReportReviewRecorded: false,
    safetyRectificationSubmittedAndReviewed: false,
  };
}

async function createPublicHealthAdapter(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  const adapterId = `public-health-infection-${suffix.toLowerCase()}`;
  const fieldMappings = [
    { sourcePath: "/patientId", targetPath: "/patient/mpi" },
    {
      sourcePath: "/infectionCode",
      targetPath: "/conditions/0",
      targetDictionaryKey: "ICD-10",
      category: "DIAGNOSIS",
    },
    { sourcePath: "/labCode", targetPath: "/observations/0/code" },
    { sourcePath: "/labResult", targetPath: "/observations/0/valueString" },
    { sourcePath: "/reportCardType", targetPath: "/documents/0/documentType" },
    { sourcePath: "/reportCardDigest", targetPath: "/documents/0/contentDigest" },
    { sourcePath: "/publicHealthReport/reportType", targetPath: "/publicHealthReport/reportType" },
    {
      sourcePath: "/publicHealthReport/reportableCondition",
      targetPath: "/publicHealthReport/reportableCondition",
    },
    {
      sourcePath: "/publicHealthReport/manualSubmitRequired",
      targetPath: "/publicHealthReport/manualSubmitRequired",
    },
    {
      sourcePath: "/publicHealthReport/legalSubmissionDelegated",
      targetPath: "/publicHealthReport/legalSubmissionDelegated",
    },
    {
      sourcePath: "/publicHealthReport/prefillStatus",
      targetPath: "/publicHealthReport/prefillStatus",
    },
    { sourcePath: "/safetyEvent/eventType", targetPath: "/safetyEvent/eventType" },
    { sourcePath: "/safetyEvent/riskLevel", targetPath: "/safetyEvent/riskLevel" },
    { sourcePath: "/safetyEvent/rootCause", targetPath: "/safetyEvent/rootCause" },
    {
      sourcePath: "/safetyEvent/rectificationRequired",
      targetPath: "/safetyEvent/rectificationRequired",
    },
    { sourcePath: "/safetyEvent/reviewRequired", targetPath: "/safetyEvent/reviewRequired" },
  ];
  const config = {
    systemFamilyCode: publicHealthSourceSystem,
    sourceSystem: publicHealthSourceSystem,
    targetSystem: publicHealthSourceSystem,
    baseUrl: "https://public-health.example.invalid",
    healthPath: "/health",
    outboundPath: "/report-prefill",
    connectTimeoutMs: 800,
    requestTimeoutMs: 1200,
    fieldMappings,
  };
  const response = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `院感公卫监管接入 ${suffix}`,
    protocolType: "Webhook",
    configJson: JSON.stringify(config),
  });
  await expectOk(response, "创建 PUBLIC_HEALTH_INFECTION_REGULATORY 适配器");
  return { adapterId, protocolType: "Webhook", ...config };
}

async function createPublicHealthWebhook(page: Page, suffix: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  const webhookId = `public-health-webhook-${suffix.toLowerCase()}`;
  const response = await postApi(page, "/engine/integration/webhooks", {
    webhookId,
    name: `院感公卫监管回传 ${suffix}`,
    callbackUrl: "https://public-health.example.invalid/medkernel/events",
    eventsSubscribed: "PUBLIC_HEALTH_INFECTION_REPORT SAFETY_EVENT",
  });
  await expectOk(response, "创建院感公卫监管回调通道");
  const data = await responseData(response);
  return {
    webhookId,
    sharedSecret: requireText(textField(data, "sharedSecret"), "回调通道必须一次性返回共享密钥"),
  };
}

async function generatePublicHealthSignaturePreview(page: Page, webhookId: string) {
  const response = await postApi(page, "/engine/integration/webhooks/test", {
    webhookId,
    payload: JSON.stringify({ traceId: `preview-${webhookId}`, eventType: "PUBLIC_HEALTH_INFECTION_REPORT" }),
  });
  await expectOk(response, "生成院感公卫回调签名预览");
  const data = await responseData(response);
  expect(textField(data, "signature"), "签名预览必须返回 hex 签名").toMatch(/^[0-9a-f]{64}$/i);
}

async function createPublicHealthTerminologyGate(page: Page, suffix: string) {
  const standard = await postApi(page, "/engine/terminology/terms/standard", {
    ...apiContext(suffix, "term-standard"),
    standardSystem: "ICD-10",
    termCode: diagnosisStandardCode,
    category: "DIAGNOSIS",
    displayName: "新型冠状病毒感染",
    normalizedName: "新型冠状病毒感染|U07.100|COVID-19",
    versionNo: "2026",
    sourceVersionId: null,
    evidenceText: "S21/S32 院感公卫代表切片：上报预填规则所需 ICD-10 标准诊断术语。",
  });
  await expectOk(standard, "登记院感公卫 ICD-10 标准术语");
  const standardTermId = numberField(await responseData(standard), "id");
  const local = await postApi(page, "/engine/terminology/terms/local", {
    ...apiContext(suffix, "term-local"),
    sourceSystem: publicHealthSourceSystem,
    localCode: diagnosisLocalCode,
    category: "DIAGNOSIS",
    localName: "院感公卫新型冠状病毒感染疑似病例",
    normalizedName: "院感公卫新型冠状病毒感染疑似病例|PH-COVID-19|COVID-19|U07.100|新型冠状病毒感染",
    local_department_id: null,
  });
  await expectOk(local, "登记院感公卫院内诊断术语");
  const localTermId = numberField(await responseData(local), "id");
  expect(localTermId, "院感公卫院内诊断术语必须返回 id").toBeTruthy();
  const mapping = await readOrConfirmTerminologyMapping(page, {
    suffix,
    sourceSystem: publicHealthSourceSystem,
    localCode: diagnosisLocalCode,
    localTermId,
    standardTermId,
    category: "DIAGNOSIS",
    reviewNote: "S21/S32 代表切片：确认院感公卫 PH-COVID-19 到 ICD-10:U07.100。",
    evidenceOverride: "PUBLIC_HEALTH_INFECTION_REGULATORY 入站感染诊断编码归一所需映射。",
  });
  const assetIdentity = `TERM.PUBLIC_HEALTH.INFECTION.${suffix}`;
  const draft = await postApi(page, "/engine/terminology/assets/drafts", {
    assetIdentity,
    name: `院感公卫感染诊断术语映射 ${suffix}`,
    scopeLevel: "TENANT",
    scopeCode: resolvedTenantIdFor("engine-operator"),
  });
  await expectOk(draft, "生成院感公卫术语资产草稿");
  const draftData = await responseData(draft);
  return {
    assetType: "TERMINOLOGY" as const,
    assetIdentity,
    versionId: requireText(textField(draftData, "versionId"), "术语资产必须返回 versionId"),
    versionNo: requireText(textField(draftData, "versionNo"), "术语资产必须返回 versionNo"),
    contentHash: requireText(textField(draftData, "contentHash"), "术语资产必须返回 contentHash"),
    standardSystem: "ICD-10" as const,
    standardCode: diagnosisStandardCode,
    localCode: diagnosisLocalCode,
    sourceSystem: publicHealthSourceSystem,
    category: "DIAGNOSIS" as const,
    mappingId: mapping.mappingId,
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
    category: "DIAGNOSIS";
    reviewNote: string;
    evidenceOverride: string;
  },
) {
  const existing = await getApi(
    page,
    `/engine/terminology/mappings?category=${encodeURIComponent(options.category)}&status=CONFIRMED&page=1&size=100`,
  );
  await expectOk(existing, "读取已确认院感公卫术语映射");
  const found = pageItems(await responseData(existing)).find(
    (item) =>
      numberField(item, "localTermId") === options.localTermId &&
      numberField(item, "standardTermId") === options.standardTermId &&
      textField(item, "sourceSystem") === options.sourceSystem,
  );
  const foundId = numberField(found, "id");
  if (foundId) return { mappingId: foundId };
  const generation = await postApi(page, "/engine/terminology/mappings/candidates", {
    ...apiContext(options.suffix, "term-candidates"),
    sourceSystem: options.sourceSystem,
    minimumScore: 0.2,
    semanticAssistEnabled: true,
  });
  await expectOk(generation, "生成院感公卫术语映射候选");
  const jobCode = requireText(textField(await responseData(generation), "jobCode"), "术语候选任务必须返回 jobCode");
  const candidate = await waitForTerminologyCandidate(page, jobCode, {
    localCode: options.localCode,
    localTermId: options.localTermId,
    standardTermId: options.standardTermId,
  });
  const candidateId = numberField(candidate, "id");
  expect(candidateId, "院感公卫术语候选必须返回 id").toBeTruthy();
  const confirmed = await postApi(
    page,
    `/engine/terminology/mappings/${encodeURIComponent(String(candidateId))}/confirm`,
    {
      ...apiContext(options.suffix, "term-confirm"),
      reviewNote: options.reviewNote,
      evidenceOverride: options.evidenceOverride,
    },
  );
  await expectOk(confirmed, "确认院感公卫术语映射");
  return { mappingId: Number(numberField(await responseData(confirmed), "id")) };
}

async function waitForTerminologyCandidate(
  page: Page,
  jobCode: string,
  expected: { localCode: string; localTermId?: number | null; standardTermId?: number | null },
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
    await expectOk(job, "读取院感公卫术语候选生成任务");
    const jobData = await responseData(job);
    lastStatus = textField(jobData, "status") ?? lastStatus;
    generatedCount = numberField(jobData, "generatedCount") ?? generatedCount;
    const response = await getApi(
      page,
      `/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=${encodeURIComponent(jobCode)}&page=1&size=20`,
    );
    await expectOk(response, "读取院感公卫术语映射候选");
    const candidates = pageItems(await responseData(response));
    const candidate = candidates.find((item) => {
      const evidence = String(textField(item, "evidenceText") ?? "");
      if (evidence) lastEvidence = evidence;
      return (
        numberField(item, "localTermId") === expected.localTermId &&
        numberField(item, "standardTermId") === expected.standardTermId
      );
    });
    if (candidate) return candidate;
    if (lastStatus === "FAILED") {
      throw new Error(`院感公卫术语候选生成失败 ${jobCode}`);
    }
    if (lastStatus === "SUCCEEDED" && generatedCount === 0) {
      throw new Error(`院感公卫术语候选生成成功但没有候选 ${jobCode}`);
    }
    await waitForPollingInterval(250);
  }
  throw new Error(
    `院感公卫术语候选生成超时 ${jobCode}，最后状态 ${lastStatus}，候选数 ${generatedCount ?? "UNKNOWN"}，最后证据 ${lastEvidence}`,
  );
}

async function createPublicHealthActionCard(
  page: Page,
  suffix: string,
): Promise<PublicHealthActionCardCandidate> {
  const assetIdentity = `${actionCardIdentityPrefix}.${suffix}`;
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity,
    applicableScope: "ALL",
    sourceRef: "S21/S32 院感公卫代表切片：上报预填提示卡，不替代法定上报。",
    content: {
      schemaVersion: "1.0",
      title: `院感公卫上报预填提示卡 ${suffix}`,
      actionCode: "STRONG_REMINDER",
      atSeverity: "HIGH",
      indicator: "warning",
      summary: "疑似传染病上报预填需人工复核。",
      detail: "系统只生成上报预填和任务证据，不自动提交法定上报。",
      source: { label: "MedKernel S21/S32 本地上线演练" },
      suggestions: [{ label: "打开上报预填", actionType: "OPEN_FORM", payload: { target: "PUBLIC_HEALTH_REPORT" } }],
      overrideReasons: ["临床用户已人工复核上报预填，后续依法在法定系统提交"],
      requiresHumanReportReview: true,
      noLegalAutoSubmit: true,
      requiresPhysicianConfirmation: true,
      noAutoOrder: true,
    },
  });
  await expectOk(response, "创建院感公卫 ACTION_CARD 资产");
  const data = await responseData(response);
  return {
    assetType: "ACTION_CARD",
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "ACTION_CARD 必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "ACTION_CARD 必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "ACTION_CARD 必须返回 contentHash"),
    entryState: "ACTIVE",
    requiresHumanReportReview: true,
    noLegalAutoSubmit: true,
  };
}

async function createAndPublishPublicHealthRule(
  page: Page,
  suffix: string,
  options: {
    positiveContextSnapshotId: string;
    negativeContextSnapshotId: string;
    actionCard: { assetIdentity: string };
  },
) {
  const ruleCode = `${ruleIdentityPrefix}.${suffix}`;
  const create = await postApi(page, "/engine/rule/rules", {
    ...apiContext(suffix, "rule-create"),
    triggers: [
      {
        trigger_point: "result-review",
        purpose: "RULE_EXECUTION",
        required_fields: ["patientId", "encounterId", "conditions", "observations", "extensions"],
      },
    ],
    ruleCode,
    name: `院感公卫上报预填代表切片规则 ${suffix}`,
    ruleType: "REPORT",
    authoringMode: "DSL",
    riskLevel: "HIGH",
    priority: 940,
    suppressedBy: null,
    dedupeWindowSeconds: 0,
    applicableOrgUnitId: null,
    sourceRef: "local-e2e:infection-public-health-safety",
    changeSummary: "S21/S32 代表切片：规则引用 Condition、Observation、extensions.local 与 ACTION_CARD。",
    dsl: {
      applicability: {
        population: {},
        orgScope: {},
        settings: ["INPATIENT", "OUTPATIENT", "ED", "FOLLOWUP"],
        effective: { rolloutPercent: 100 },
      },
      when: {
        all: [
          { fact: "conditions[].code", operator: "equals", value: diagnosisStandardCode },
          { fact: "observations[].valueString", operator: "equals", value: "POSITIVE" },
          {
            fact: "extensions.local.publicHealthReport.manualSubmitRequired",
            operator: "equals",
            value: true,
          },
        ],
      },
      then: [
        {
          actionCode: "STRONG_REMINDER",
          atSeverity: "HIGH",
          indicator: "warning",
          summary: "疑似传染病上报预填需人工复核",
          detail: "生成上报预填和安全事件整改证据；法定上报仍需人工在法定系统完成。",
          source: { label: "S21/S32 院感公卫代表切片" },
          actionCardRef: options.actionCard.assetIdentity,
          suggestions: [],
          overrideReasons: ["临床用户已人工复核上报预填"],
          requiresPhysicianConfirmation: true,
        },
      ],
      explain: {
        title: "院感公卫上报预填代表切片规则",
        reason: "感染诊断、检验结果和上报预填事实均来自当前临床上下文。",
        sourceRef: "local-e2e:infection-public-health-safety",
      },
    },
    explanation: {
      title: "院感公卫上报预填代表切片规则",
      summary: "证明院感公卫规则和提示卡进入当前机构生效版本。",
    },
    parameterBindings: {},
  });
  await expectOk(create, "创建院感公卫 RULE 资产");
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
    await expectOk(response, `新增院感公卫规则发布验证用例 ${testCase.caseType}`);
  }
  const testRun = await postApi(
    page,
    `/engine/rule/rules/${encodeURIComponent(ruleId)}/test`,
    apiContext(ruleId, "rule-test-run"),
  );
  await expectOk(testRun, "执行院感公卫规则发布验证用例");
  expect(booleanField(await responseData(testRun), "allPassed"), "规则发布验证用例必须全部通过").toBe(true);
  for (const targetState of ["REVIEWED", "SHADOW", "CANARY", "FULL"]) {
    const impact = await getApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/impact`);
    await expectOk(impact, `读取院感公卫规则 ${targetState} 影响摘要`);
    const transition = await postApi(page, `/engine/rule/rules/${encodeURIComponent(ruleId)}/governance/transitions`, {
      ...apiContext(ruleId, `rule-governance-${targetState}`),
      targetState,
      impactDigest: requireText(textField(await responseData(impact), "impactDigest"), "规则影响摘要必须返回 digest"),
      reason: `S21/S32 院感公卫上报预填规则推进至 ${targetState}`,
      publishEvidence: {
        qualityGate: {
          schemaValid: true,
          terminologyBindingComplete: true,
          dependencyIntegrityVerified: true,
          safetyMonotonicityVerified: true,
          impactSimulationPassed: true,
          summary: `院感公卫规则 ${targetState} 推进质量门已通过`,
        },
      },
    });
    await expectOk(transition, `院感公卫规则治理推进至 ${targetState}`);
  }
  return {
    assetType: "RULE" as const,
    assetIdentity: ruleCode,
    ruleId,
    ruleVersionId: requireText(textField(created, "versionId"), "规则创建响应必须返回 versionId"),
  };
}

async function activateRuntimeWithPublicHealthAssets(
  page: Page,
  options: {
    hospitalId: string;
    terminology: PublicHealthAssetCandidate;
    rule?: PublicHealthAssetCandidate;
    actionCard: PublicHealthAssetCandidate;
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
  const currentRuntime = await responseData(current);
  const currentReleaseId = textFieldAtPath(currentRuntime, "release.releaseId");
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    runtimeSelection(options.terminology),
    ...(options.rule ? [runtimeSelection(options.rule)] : []),
    runtimeSelection(options.actionCard),
  ]);
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId,
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest: null,
    activeAssets,
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, "激活包含院感公卫资产的医院生效版本");
  const releaseId = requireText(textField(await responseData(activated), "releaseId"), "激活必须返回 releaseId");
  const currentAfter = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfter, "回读院感公卫医院生效版本");
  const detail = (await responseData(currentAfter)) as RuntimeReleaseDetail;
  expect(textFieldAtPath(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(releaseId);
  return {
    releaseId,
    revisionNo: numberFieldAtPath(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(textFieldAtPath(detail, "release.manifestSha256"), "机构生效版本必须返回 manifestSha256"),
    assets: detail.items ?? [],
    terminologyAsset: assertRuntimeContainsAsset(detail, options.terminology),
    ruleAsset: options.rule ? assertRuntimeContainsAsset(detail, options.rule) : undefined,
    actionCardAsset: assertRuntimeContainsAsset(detail, options.actionCard),
    activationRequest,
  };
}

async function readHospitalRuntimeCandidate(
  page: Page,
  hospitalId: string,
  assetType: PublicHealthAssetCandidate["assetType"],
  assetIdentity: string,
): Promise<PublicHealthAssetCandidate> {
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
    versionId: requireText(textField(candidate, "versionId"), `${assetType} 候选必须返回 versionId`),
    versionNo: requireText(textField(candidate, "versionNo"), `${assetType} 候选必须返回 versionNo`),
    contentHash: requireText(textField(candidate, "contentHash"), `${assetType} 候选必须返回 contentHash`),
  };
}

async function createPublicHealthPatientFromFrontdesk(page: Page, suffix: string) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const dialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(dialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `感*${idLast4.slice(-1)}`;
  await dialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = dialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await dialog.getByRole("spinbutton", { name: "年龄" }).fill("42");
  await dialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await dialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "创建院感公卫演练脱敏患者");
  const patientId = requireText(textField(await responseData(patientResponse), "mpiId"), "患者创建响应必须返回 MPI");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    encounterId: `enc-public-health-${suffix.toLowerCase()}`,
  };
}

async function sendPublicHealthOutbound(
  page: Page,
  options: {
    suffix: string;
    adapterId: string;
    patientId: string;
    contextSnapshotId: string;
    traceId: string;
  },
) {
  await ensureReadySession(page, "platform-admin");
  const response = await postApi(page, "/engine/integration/messages/outbound", {
    messageId: `out-public-health-${options.suffix}`,
    traceId: options.traceId,
    adapterId: options.adapterId,
    targetSystem: publicHealthSourceSystem,
    protocolType: "Webhook",
    payloadSummary: "院感公卫上报预填回传",
    payload: {
      patientId: options.patientId,
      contextSnapshotId: options.contextSnapshotId,
      reportType: "INFECTIOUS_DISEASE_PREFILL",
      manualSubmitRequired: true,
      legalSubmissionDelegated: false,
    },
    maxRetries: 2,
  });
  await expectOk(response, "登记院感公卫上报预填出站请求");
  const data = await responseData(response);
  const status = requireText(textField(data, "status"), "出站请求必须返回状态");
  expect(["NOT_CONNECTED", "RETRYING"].includes(status), "出站不得伪造成功").toBe(true);
  expect(booleanField(data, "blocksMainFlow"), "出站断连不得阻断临床主流程").toBe(false);
  const compensation = await waitForPublicHealthCompensation(
    page,
    requireText(textField(data, "messageId"), "出站请求必须返回 messageId"),
  );
  return {
    messageId: requireText(textField(data, "messageId"), "出站请求必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "出站请求必须返回 traceId"),
    adapterId: options.adapterId,
    targetSystem: publicHealthSourceSystem,
    protocolType: "Webhook",
    status,
    compensationStatus: requireText(textField(compensation, "status"), "补偿日志必须返回状态"),
    compensationMessageId: requireText(textField(compensation, "messageId"), "补偿日志必须返回 messageId"),
    blocksMainFlow: booleanField(data, "blocksMainFlow"),
    compensationRequired: textField(compensation, "status") === "NOT_CONNECTED",
    payload: {
      patientId: options.patientId,
      contextSnapshotId: options.contextSnapshotId,
      reportType: "INFECTIOUS_DISEASE_PREFILL",
      manualSubmitRequired: true,
      legalSubmissionDelegated: false,
    },
  };
}

async function waitForPublicHealthCompensation(page: Page, messageId: string) {
  const deadline = Date.now() + 20_000;
  let lastStatus: string | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, "/engine/integration/logs?page=1&size=50");
    await expectOk(response, "读取院感公卫出站补偿日志");
    const log = pageItems(await responseData(response)).find(
      (item) => textField(item, "messageId") === messageId,
    );
    if (log) {
      lastStatus = textField(log, "status") ?? lastStatus;
      if (lastStatus === "NOT_CONNECTED") return log;
      if (lastStatus && lastStatus !== "RETRYING") {
        throw new Error(`院感公卫补偿日志 ${messageId} 进入非诚实断连状态：${lastStatus}`);
      }
    }
    await waitForPollingInterval(250);
  }
  throw new Error(`院感公卫补偿日志 ${messageId} 未收敛到 NOT_CONNECTED，最后状态：${lastStatus}`);
}

async function postSignedPublicHealthInbound(
  page: Page,
  options: {
    suffix: string;
    adapterId: string;
    webhookId: string;
    webhookSecret: string;
    patientId: string;
    encounterId: string;
    traceId: string;
    runtimeReleaseId: string;
    positive: boolean;
  },
) {
  await ensureReadySession(page, "platform-admin");
  const request: InboundWebhookRequest = {
    messageId: `in-public-health-${options.suffix}`,
    traceId: options.traceId,
    adapterId: options.adapterId,
    sourceSystem: publicHealthSourceSystem,
    eventType: "REPORT",
    patientId: options.patientId,
    encounterId: options.encounterId,
    clinicalSetting: "INPATIENT",
    triggerPoint: "result-review",
    occurredAt: "2026-07-07T01:15:00Z",
    payload: {
      patientId: options.patientId,
      infectionCode: diagnosisLocalCode,
      labCode: observationCode,
      labResult: options.positive ? "POSITIVE" : "NEGATIVE",
      reportCardDigest: `sha256:public-health-report-prefill-${options.suffix}`,
      reportCardType: "PUBLIC_HEALTH_REPORT_PREFILL",
      publicHealthReport: {
        reportType: "INFECTIOUS_DISEASE_PREFILL",
        reportableCondition: options.positive ? "SUSPECTED_COVID_19" : "NOT_REPORTABLE",
        manualSubmitRequired: options.positive,
        legalSubmissionDelegated: false,
        prefillStatus: options.positive ? "READY_FOR_HUMAN_REVIEW" : "NOT_REQUIRED",
      },
      safetyEvent: {
        eventType: "OCCUPATIONAL_EXPOSURE",
        riskLevel: options.positive ? "HIGH" : "LOW",
        rootCause: options.positive ? "ISOLATION_PROTOCOL_GAP" : "NONE",
        rectificationRequired: options.positive,
        reviewRequired: options.positive,
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
  await expectOk(response, "院感公卫监管签名入站");
  const data = await responseData(response);
  const clinicalEventId = requireText(textField(data, "clinicalEventId"), "入站必须返回 clinicalEventId");
  const clinicalEvent = await waitForClinicalEventProcessed(page, clinicalEventId, options.runtimeReleaseId);
  return {
    messageId: requireText(textField(data, "messageId"), "入站必须返回 messageId"),
    traceId: requireText(textField(data, "traceId"), "入站必须返回 traceId"),
    adapterId: options.adapterId,
    webhookId: options.webhookId,
    patientId: options.patientId,
    encounterId: options.encounterId,
    contextSnapshotId: "",
    sourceSystem: publicHealthSourceSystem,
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
    await expectOk(response, "读取院感公卫入站临床事件详情");
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
      expect(last.runtimeReleaseId, "入站事件必须绑定本轮 runtime").toBe(runtimeReleaseId);
      expect(last.errorCode, "入站事件处理成功不得有 errorCode").toBeNull();
      return last;
    }
    if (last.status === "FAILED") {
      throw new Error(`院感公卫入站事件 ${eventId} 处理失败：${last.errorCode ?? "UNKNOWN"}`);
    }
    await waitForPollingInterval(250);
  }
  throw new Error(`院感公卫入站事件 ${eventId} 未处理到 PROCESSED，最后状态：${last?.status ?? "UNKNOWN"}`);
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
    patientId: requireText(
      textFieldAtPath(context, "resources.patient.mpi"),
      "上下文详情必须返回 patient.mpi",
    ),
    snapshotId: requireText(textField(context, "snapshotId"), "上下文详情必须返回 snapshotId"),
    runtimeReleaseId: requireText(textField(context, "runtimeReleaseId"), "上下文详情必须返回 runtimeReleaseId"),
    encounterId: textFieldAtPath(context, "resources.encounters.0.encounterId"),
    resources: recordValue(recordField(context, "resources")) ?? {},
  };
}

function assertSnapshotContainsPublicHealthFacts(resources: Record<string, unknown>) {
  expect(
    arrayField(resources, "conditions").some(
      (item) => textField(item, "code") === diagnosisStandardCode && textField(item, "sourceSystem") === publicHealthSourceSystem,
    ),
    "上下文必须包含院感公卫入站感染诊断",
  ).toBe(true);
  expect(
    arrayField(resources, "observations").some(
      (item) => textField(item, "code") === observationCode && textField(item, "valueString") === "POSITIVE",
    ),
    "上下文必须包含院感公卫入站检验结果",
  ).toBe(true);
  expect(
    arrayField(resources, "documents").some(
      (item) => textField(item, "documentType") === "PUBLIC_HEALTH_REPORT_PREFILL",
    ),
    "上下文必须包含上报预填文档证据",
  ).toBe(true);
  expect(booleanFieldAtPath(resources, "extensions.local.publicHealthReport.manualSubmitRequired"), "上报预填必须要求人工提交").toBe(true);
  expect(booleanFieldAtPath(resources, "extensions.local.publicHealthReport.legalSubmissionDelegated"), "中枢不得替代法定上报").toBe(false);
  expect(booleanFieldAtPath(resources, "extensions.local.safetyEvent.rectificationRequired"), "安全事件必须要求整改").toBe(true);
}

async function triggerPublicHealthRecommendationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      ruleAsset: RuntimeReleaseItem | undefined;
      actionCardAsset: RuntimeReleaseItem;
    };
    rule: PublicHealthAssetCandidate & { ruleId: string; ruleVersionId: string };
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
  await expect(snapshotButton, `提醒推荐页必须展示本轮院感公卫快照 ${snapshot.snapshotId}`).toBeVisible({ timeout: 20_000 });
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "审核结果");
  const evaluateResponsePromise = waitForEvaluateResponse(page, snapshot);
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  await expectHttpOk(evaluateResponse, "临床用户从真实前台触发院感公卫推荐评估");
  const evaluation = await responseData(evaluateResponse);
  const triggerId = requireText(textField(evaluation, "triggerId"), "推荐评估响应必须返回 triggerId");
  const relatedCardIds = await readRecommendationTriggerDiagnose(page, triggerId);
  const recommendation = await findPublicHealthRuleCard(page, relatedCardIds, {
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

async function findPublicHealthRuleCard(
  page: Page,
  relatedCardIds: string[],
  options: {
    triggerId: string;
    snapshot: ContextSnapshotSummary;
    runtime: { releaseId: string; ruleAsset: RuntimeReleaseItem | undefined; actionCardAsset: RuntimeReleaseItem };
    rule: PublicHealthAssetCandidate & { ruleId: string; ruleVersionId: string };
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
    const matches =
      textFieldAtPath(detail, "trigger.triggerId") === options.triggerId &&
      textFieldAtPath(detail, "trigger.contextSnapshotId") === options.snapshot.snapshotId &&
      textFieldAtPath(detail, "trigger.runtimeReleaseId") === options.runtime.releaseId &&
      textField(explanation, "matchType") === "RULE" &&
      textField(explanation, "ruleId") === options.rule.ruleId &&
      textField(explanation, "ruleCode") === options.rule.assetIdentity &&
      textField(runtimeRelease, "assetVersionId") === options.runtime.ruleAsset?.versionId &&
      conditionEvidence.some((item) => textField(item, "fact") === "conditions[].code" && booleanField(item, "matched") === true) &&
      conditionEvidence.some((item) => textField(item, "fact") === "observations[].valueString" && booleanField(item, "matched") === true) &&
      conditionEvidence.some(
        (item) =>
          textField(item, "fact") === "extensions.local.publicHealthReport.manualSubmitRequired" &&
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
      cardType: textFieldAtPath(detail, "card.cardType") ?? "REPORT",
      requiresPhysicianConfirmation: booleanFieldAtPath(detail, "card.requiresPhysicianConfirmation"),
      aiGenerated: booleanFieldAtPath(detail, "card.aiGenerated"),
      explanation,
    });
  }
  expect(matched.map((card) => card.cardId), "必须唯一定位本轮院感公卫 RULE 推荐卡").toHaveLength(1);
  return matched[0];
}

async function completePublicHealthManualReview(
  page: Page,
  options: {
    cardId: string;
    actionCard: PublicHealthActionCardCandidate;
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
    .fill("临床用户已人工复核上报预填；法定上报仍需在法定系统人工提交，中枢不自动上报。");
  const responsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "确认采纳建议" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "登记院感公卫上报预填人工确认");
  const feedback = await responseData(response);
  const detailResponse = await getApi(page, `/engine/recommendations/cards/${encodeURIComponent(options.cardId)}`);
  await expectOk(detailResponse, "回读院感公卫推荐卡反馈详情");
  const detail = await responseData(detailResponse);
  const persisted = arrayField(detail, "feedback").find(
    (item) =>
      textField(item, "feedbackId") === textField(feedback, "feedbackId") &&
      textField(item, "feedbackType") === "ACCEPT",
  );
  expect(persisted, "人工确认反馈必须从推荐详情回读").toBeTruthy();
  const actionCardEvidence = {
    assetType: options.actionCardAsset.assetType,
    assetIdentity: options.actionCardAsset.assetIdentity,
    versionId: options.actionCardAsset.versionId,
    versionNo: options.actionCardAsset.versionNo,
    contentHash: options.actionCardAsset.contentHash,
    entryState: options.actionCardAsset.entryState,
    requiresHumanReportReview: options.actionCard.requiresHumanReportReview,
    noLegalAutoSubmit: options.actionCard.noLegalAutoSubmit,
  };
  return {
    feedbackId: textField(feedback, "feedbackId"),
    cardStatus: textField(feedback, "cardStatus"),
    canonicalSessionRole: "clinical-user",
    roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
    persisted: {
      ...(recordValue(persisted) ?? {}),
    },
    noLegalAutoSubmit: true,
    actionCardEvidence,
  };
}

async function createAndCloseSafetyEventRectification(
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
    runCode: `PUBLIC-HEALTH-SAFETY-${options.suffix}`,
    runType: "MANUAL_SAMPLE",
    sourceEventId: options.recommendation.cardId,
    patientId: options.snapshot.patientId,
    encounterId: options.snapshot.encounterId,
    scenarioCode: "S32",
    inputDigest: `public-health-safety-${options.suffix}`,
    occurredAt: "2026-07-07T01:30:00Z",
    results: [
      {
        indicatorId: indicator.indicatorId,
        subjectType: "PATIENT",
        subjectRefId: options.snapshot.patientId,
        scoreValue: 70,
        resultLevel: "NON_COMPLIANT",
        hitFlag: true,
        evidenceSummary: "院感公卫入站 safetyEvent 要求隔离流程整改和复核。",
        sourceRef: options.recommendation.cardId,
        responsibleDepartmentId: departmentId,
        findings: [
          {
            findingCode: `PUBLIC_HEALTH_SAFETY_EVENT_${options.suffix}`,
            title: "院感公卫安全事件整改代表切片",
            description: "需补充隔离流程复盘、暴露人员追踪和整改复核证据。",
            severity: "P1",
            evidenceSummary: "安全事件扩展标记 rectificationRequired=true/reviewRequired=true。",
            responsibleDepartmentId: departmentId,
            dueAt: "2026-07-15T08:30:00Z",
          },
        ],
      },
    ],
  });
  await expectOk(run, "创建医疗安全事件质量问题");
  const issues = await getApi(page, "/engine/evaluation/issues?severity=P1&status=ASSIGNED&page=1&size=20&sort=createdAt,desc");
  await expectOk(issues, "读取医疗安全事件质量问题");
  const finding = pageItems(await responseData(issues)).find((item) =>
    String(textField(item, "findingCode") ?? "").includes(options.suffix),
  );
  const findingId = requireText(textField(finding, "findingId"), "必须回读本轮安全事件质量问题");
  const detail = await getApi(page, `/engine/evaluation/issues/${encodeURIComponent(findingId)}`);
  await expectOk(detail, "读取安全事件质量问题详情");
  const taskId = requireText(textFieldAtPath(await responseData(detail), "rectificationTask.taskId"), "质量问题必须自动派发整改任务");
  const submit = await postApi(page, `/engine/rectifications/${encodeURIComponent(taskId)}/submit`, {
    rectificationSummary: "已补充隔离流程复盘、暴露人员追踪和院感公卫上报预填人工复核记录。",
    evidenceRef: `public-health-safety-evidence-${options.suffix}`,
  });
  await expectOk(submit, "提交医疗安全事件整改证据");
  const review = await postApi(page, `/engine/rectifications/${encodeURIComponent(taskId)}/review`, {
    decision: "APPROVED",
    comment: "复核通过，整改证据与本轮院感公卫安全事件一致。",
    evidenceRef: `public-health-safety-review-${options.suffix}`,
  });
  await expectOk(review, "复核关闭医疗安全事件整改任务");
  const reviewData = await responseData(review);
  return {
    findingId,
    sourceType: "SAFETY_EVENT",
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
    submittedEvidenceRef: `public-health-safety-evidence-${options.suffix}`,
    reviewDecision: "APPROVED",
  };
}

async function createActiveEvaluationIndicator(page: Page, suffix: string, departmentId: string) {
  const indicatorCode = `PUBLIC_HEALTH_SAFETY_EVENT_${suffix}`;
  const created = await postApi(page, "/engine/evaluation/indicators", {
    indicatorCode,
    name: `院感公卫安全事件整改指标 ${suffix}`,
    subjectType: "PATIENT",
    denominatorDefinition: JSON.stringify({
      all: [
        { fact: "extensions.local.safetyEvent.rectificationRequired", operator: "equals", value: true },
      ],
    }),
    numeratorDefinition: JSON.stringify({
      all: [
        { fact: "rectification.reviewStatus", operator: "equals", value: "APPROVED" },
      ],
    }),
    exclusionDefinition: null,
    scoringDefinition: "命中即需整改",
    timeWindow: "本地上线演练窗口",
    organizationScope: "本地上线演练医院",
    responsibleDepartmentId: departmentId,
    sourceRef: "local-e2e:infection-public-health-safety",
  });
  await expectOk(created, "创建院感公卫安全事件评价指标");
  const indicatorId = requireText(textField(await responseData(created), "indicatorId"), "评价指标必须返回 indicatorId");
  for (const action of ["submit", "publish", "gray", "activate"]) {
    const response = await postApi(page, `/engine/evaluation/indicators/${encodeURIComponent(indicatorId)}/${action}`, {
      reason: `S32 医疗安全事件代表切片指标 ${action}`,
      publishEvidence: {
        qualityGate: {
          schemaValid: true,
          terminologyBindingComplete: true,
          dependencyIntegrityVerified: true,
          safetyMonotonicityVerified: true,
          impactSimulationPassed: true,
          summary: "S32 医疗安全事件代表切片指标质量门已通过",
        },
      },
    });
    await expectOk(response, `评价指标 ${action}`);
  }
  return { indicatorId, indicatorCode };
}

async function attachInfectionPublicHealthSafetyEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: PublicHealthApiEvidence;
    adapter: unknown;
    webhookSignature: unknown;
    terminologyGate: unknown;
    actionCard: unknown;
    rule: unknown;
    runtime: {
      releaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      terminologyAsset: RuntimeReleaseItem;
      ruleAsset: RuntimeReleaseItem | undefined;
      actionCardAsset: RuntimeReleaseItem;
    };
    activationRequest: unknown;
    clinicalContext: unknown;
    outboundPrefill: unknown;
    inboundReport: unknown;
    clinicalTrigger: unknown;
    recommendation: unknown;
    manualReview: unknown;
    qualityRectification: unknown;
    observedStages: Set<string>;
  },
) {
  for (const stages of Object.values(requiredStages)) {
    for (const stage of stages) {
      expect(evidence.observedStages.has(stage), `缺少 S21/S32 阶段：${stage}`).toBe(true);
    }
  }
  await testInfo.attach("infection-public-health-safety-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S21", "S32"],
        productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
        versionedAssets: ["TERMINOLOGY", "RULE", "ACTION_CARD"],
        deliveryShapes: ["API_EVENT"],
        serviceCombinations: [
          "THIRD_PARTY_INTERFACE",
          "CLINICAL_RUNTIME",
          "PROFESSIONAL_COLLABORATION",
          "QUALITY_IMPROVEMENT",
        ],
        scopeStatement:
          "院感公卫与医疗安全事件代表切片：PUBLIC_HEALTH_INFECTION_REGULATORY 入站、感染监测、上报预填、人工确认和安全事件整改闭环，不代表完整院感系统、完整公卫法定上报、完整不良事件系统或第三方公卫院感监管系统族完整覆盖。",
        apiEvidence: evidence.apiEvidence,
        adapter: evidence.adapter,
        webhookSignature: evidence.webhookSignature,
        terminologyGate: evidence.terminologyGate,
        actionCard: evidence.actionCard,
        ruleAsset: evidence.rule,
        runtime: {
          releaseId: evidence.runtime.releaseId,
          revisionNo: evidence.runtime.revisionNo,
          manifestSha256: evidence.runtime.manifestSha256,
          assets: evidence.runtime.assets,
          terminologyAsset: evidence.runtime.terminologyAsset,
          ruleAsset: evidence.runtime.ruleAsset,
          actionCardAsset: evidence.runtime.actionCardAsset,
        },
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        outboundPrefill: evidence.outboundPrefill,
        inboundReport: evidence.inboundReport,
        clinicalTrigger: evidence.clinicalTrigger,
        recommendation: evidence.recommendation,
        manualReview: evidence.manualReview,
        qualityRectification: evidence.qualityRectification,
        scenarioEvidence: [
          { code: "S21", observedStages: requiredStages.S21 },
          { code: "S32", observedStages: requiredStages.S32 },
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
          payload.triggerType === "result-review"
        );
      } catch {
        return false;
      }
    },
    { timeout: 30_000 },
  );
}

function apiContext(subject: string, step: string) {
  return {
    request_id: `req-public-health-safety-${step}-${subject}`,
    trace_id: `trace-public-health-safety-${step}-${subject}`,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
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
    code: `E2E-PUBLIC-HEALTH-SAFETY-${suffix.toUpperCase()}`,
    name: `院感公卫整改科室${suffix.slice(-4)}`,
    level: "DEPARTMENT",
    status: "ACTIVE",
  });
  await expectOk(created, "创建院感公卫整改科室");
  const department = await responseData(created);
  expect(textField(department, "level"), "院感公卫安全事件责任组织必须是科室").toBe("DEPARTMENT");
  return requireText(textField(department, "id"), "创建科室必须返回 id");
}

function assertRuntimeContainsAsset(detail: RuntimeReleaseDetail, expected: PublicHealthAssetCandidate) {
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

function runtimeSelection(asset: PublicHealthAssetCandidate): RuntimeAssetSelection {
  return {
    assetType: asset.assetType,
    assetIdentity: asset.assetIdentity,
    versionId: asset.versionId,
  };
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const seen = new Set<string>();
  return assets.filter((asset) => {
    const key = `${asset.assetType}:${asset.assetIdentity}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
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
  const selectSelector = field
    .locator("xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')]")
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
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`, "u") })
    .first();
  await expect(optionLocator, `${label} 应存在选项 ${option}`).toBeVisible({ timeout: 10_000 });
  await optionLocator.click();
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function parseJsonRecord(value: string) {
  try {
    return recordValue(JSON.parse(value));
  } catch {
    return null;
  }
}

function requireText(value: string | null | undefined, message: string) {
  if (!value) throw new Error(message);
  return value;
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function recordField(value: unknown, field: string) {
  const record = recordValue(value);
  return record ? record[field] : undefined;
}

function arrayField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return Array.isArray(raw) ? raw : [];
}

function arrayFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return Array.isArray(raw) ? raw : [];
}

function textFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function booleanFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return raw === true;
}

function numberFieldAtPath(value: unknown, path: string) {
  return numberValue(valueAtPath(value, path));
}

function valueAtPath(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, segment) => {
    if (current === null || current === undefined) return undefined;
    if (Array.isArray(current)) {
      const index = Number(segment);
      return Number.isInteger(index) ? current[index] : undefined;
    }
    const record = recordValue(current);
    return record ? record[segment] : undefined;
  }, value);
}

function numberField(value: unknown, field: string) {
  return numberValue(recordField(value, field));
}

function numberValue(value: unknown) {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function booleanField(value: unknown, field: string) {
  return recordField(value, field) === true;
}
