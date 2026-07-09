import {
  expect,
  test,
  type APIResponse,
  type Locator,
  type Page,
  type TestInfo,
} from "@playwright/test";
import { createHmac } from "node:crypto";

import {
  appPath,
  apiBase,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  postApi,
  responseData,
  textField,
} from "./support/auth";
import {
  diagnosticCriticalValueActionCardIdentity,
  diagnosticFieldCatalogIdentity,
  diagnosticKnowledgeIdentity,
  ensureDiagnosticCriticalValueRuntime,
  type DiagnosticRuntimeAssetCandidate,
  type DiagnosticRuntimeReleaseItem,
} from "./support/diagnosticRuntime";
import { standardPatientResourceConsumerMatrix } from "./support/standardPatientResourceMatrix";

type RuntimeReleaseItem = DiagnosticRuntimeReleaseItem;

type DiagnosticCriticalValueApiEvidence = {
  fhirObservationAccepted: boolean;
  fhirDiagnosticReportAccepted: boolean;
  diagnosticReportFamilyMatrixVerified: boolean;
  contextSnapshotContainsInboundResources: boolean;
  currentRuntimeContainsDiagnosticAssets: boolean;
  reportInterpretationTriggeredFromFrontdesk: boolean;
  criticalRecommendationPersisted: boolean;
  workflowTodoCompletedByHuman: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  resources: Record<string, unknown>;
};

type FhirInboundEvidence = {
  fhirResourceType: "Observation" | "DiagnosticReport";
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
};

type DiagnosticReportFamilyFixture = {
  reportFamilyCode: "PACS_RIS" | "ULTRASOUND" | "PATHOLOGY" | "ENDOSCOPY" | "ECG";
  reportFamilyName: string;
  fhirIdPrefix: string;
  fhirCode: string;
  reportType: string;
  conclusion: string;
  note: string;
};

type DiagnosticReportFamilyMatrixRow = {
  reportFamilyCode: DiagnosticReportFamilyFixture["reportFamilyCode"];
  reportFamilyName: string;
  fhirId: string;
  reportType: string;
  fhirCode: string;
  sourceSystem: "FHIR_R4";
  standardResourceVerified: boolean;
  consumerVerified: boolean;
  workflowTodoCompleted: boolean;
  degradationVerified: boolean;
  noReportRewrite: boolean;
  noAutoOrder: boolean;
  reportInterpretationId: string;
  workflowTodoId: string;
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
    recommendations?: string[];
  }>;
};

const fieldCatalogIdentity = diagnosticFieldCatalogIdentity;
const criticalActionCardIdentity = diagnosticCriticalValueActionCardIdentity;

const requiredStages = [
  "外部 FHIR/LIS 入站 Observation 危急值并落标准资源",
  "外部 FHIR/LIS 入站已签发 DiagnosticReport 并落标准资源",
  "当前上下文回读 Observation 与 DiagnosticReport 均绑定同一机构生效版本",
  "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
  "临床用户从真实前台生成医技报告解读",
  "报告解读推荐卡证明危急风险、字段目录和提示卡按当前机构生效版本消费",
  "医技或医生人工完成报告解读待办，系统不改写报告且不自动开嘱",
] as const;

const diagnosticReportFamilyFixtures: DiagnosticReportFamilyFixture[] = [
  {
    reportFamilyCode: "PACS_RIS",
    reportFamilyName: "PACS/RIS 影像报告",
    fhirIdPrefix: "dr-pacs-chest-ct",
    fhirCode: "IMG.CT.CHEST",
    reportType: "胸部 CT 影像报告",
    conclusion: "胸部 CT 影像报告提示右下肺斑片影，需结合临床上下文人工复核。",
    note: "PACS/RIS 已签发影像报告，系统仅生成阅读辅助。",
  },
  {
    reportFamilyCode: "ULTRASOUND",
    reportFamilyName: "超声报告",
    fhirIdPrefix: "dr-ultrasound-abdomen",
    fhirCode: "US.ABDOMEN",
    reportType: "腹部超声检查报告",
    conclusion: "腹部超声检查提示胆囊壁增厚，建议结合症状和既往检查人工复核。",
    note: "超声报告已签发，系统不改写报告。",
  },
  {
    reportFamilyCode: "PATHOLOGY",
    reportFamilyName: "病理报告",
    fhirIdPrefix: "dr-pathology-biopsy",
    fhirCode: "PATH.BIOPSY",
    reportType: "胃镜活检病理报告",
    conclusion: "病理报告提示慢性活动性炎症伴局灶异型增生，需医师人工复核。",
    note: "病理报告已签发，解读仅作辅助。",
  },
  {
    reportFamilyCode: "ENDOSCOPY",
    reportFamilyName: "内镜报告",
    fhirIdPrefix: "dr-endoscopy-gastroscopy",
    fhirCode: "ENDO.GASTROSCOPY",
    reportType: "胃镜检查报告",
    conclusion: "内镜检查报告提示胃窦溃疡样改变，建议结合病理和用药史人工复核。",
    note: "内镜报告已签发，系统不替代医师判断。",
  },
  {
    reportFamilyCode: "ECG",
    reportFamilyName: "心电报告",
    fhirIdPrefix: "dr-ecg-resting",
    fhirCode: "ECG.12LEAD",
    reportType: "十二导联心电图报告",
    conclusion: "心电图报告提示 ST-T 改变，需结合症状、肌钙蛋白和既往心电人工复核。",
    note: "心电报告已签发，系统不自动开立医嘱。",
  },
];

test.describe("医技报告危急值真实前台闭环", () => {
  test("临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环", async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const observedStages = new Set<string>();
    const apiEvidence = createApiEvidence();
    const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;

    await ensureReadySession(page, "engine-operator");
    const runtime = await ensureDiagnosticCriticalValueRuntime(page, suffix, {
      includeReportFamilyMatrixKnowledge: true,
    });
    const diagnosticAssets = {
      knowledge: {
        assetType: "KNOWLEDGE" as const,
        assetIdentity: diagnosticKnowledgeIdentity,
        versionId: runtime.knowledgeAsset.versionId ?? "",
        versionNo: runtime.knowledgeAsset.versionNo ?? "",
        contentHash: runtime.knowledgeAsset.contentHash ?? "",
      },
    };
    apiEvidence.currentRuntimeContainsDiagnosticAssets = true;
    recordStage(
      observedStages,
      "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
    );

    const snapshot = await createCriticalValueContextFromFrontdesk(page, suffix);
    expect(
      snapshot.runtimeReleaseId,
      "危急值上下文必须绑定包含报告解读运行资产的当前机构生效版本",
    ).toBe(runtime.releaseId);

    await ensureReadySession(page, "platform-admin");
    const fhir = await createFhirInboundAdapter(page, suffix);
    const inboundObservation = await postSignedFhirResource(page, {
      adapterId: fhir.adapterId,
      secret: fhir.sharedSecret,
      snapshot,
      resourceType: "Observation",
      resource: criticalObservationResource(snapshot, suffix),
    });
    apiEvidence.fhirObservationAccepted = true;
    recordStage(observedStages, "外部 FHIR/LIS 入站 Observation 危急值并落标准资源");

    const inboundDiagnosticReport = await postSignedFhirResource(page, {
      adapterId: fhir.adapterId,
      secret: fhir.sharedSecret,
      snapshot,
      resourceType: "DiagnosticReport",
      resource: criticalDiagnosticReportResource(snapshot, suffix),
    });
    apiEvidence.fhirDiagnosticReportAccepted = true;
    recordStage(observedStages, "外部 FHIR/LIS 入站已签发 DiagnosticReport 并落标准资源");

    const inboundReportFamilyMatrix = [];
    for (const fixture of diagnosticReportFamilyFixtures) {
      inboundReportFamilyMatrix.push(
        await postSignedFhirResource(page, {
          adapterId: fhir.adapterId,
          secret: fhir.sharedSecret,
          snapshot,
          resourceType: "DiagnosticReport",
          resource: diagnosticReportFamilyResource(snapshot, fixture, suffix),
        }),
      );
    }

    const contextAfterInbound = await readContextSnapshot(page, snapshot.snapshotId);
    const clinicalContext = assertContextContainsInboundCriticalResources({
      context: contextAfterInbound,
      runtime,
      inboundObservation,
      inboundDiagnosticReport,
    });
    assertContextContainsDiagnosticReportFamilyMatrix({
      context: contextAfterInbound,
      inboundReports: inboundReportFamilyMatrix,
    });
    apiEvidence.contextSnapshotContainsInboundResources = true;
    recordStage(
      observedStages,
      "当前上下文回读 Observation 与 DiagnosticReport 均绑定同一机构生效版本",
    );

    const interpretation = await generateReportInterpretationFromFrontdesk(page, {
      snapshot: contextAfterInbound,
      runtime,
      knowledge: diagnosticAssets.knowledge,
    });
    apiEvidence.reportInterpretationTriggeredFromFrontdesk = true;
    const reportFamilyMatrixRows = assertDiagnosticReportFamilyConsumerMatrix({
      interpretation,
      context: contextAfterInbound,
      inboundReports: inboundReportFamilyMatrix,
    });
    apiEvidence.diagnosticReportFamilyMatrixVerified = true;
    recordStage(observedStages, "临床用户从真实前台生成医技报告解读");

    const recommendation = await findReportInterpretationRecommendation(page, {
      interpretation,
      snapshot: contextAfterInbound,
      runtime,
    });
    apiEvidence.criticalRecommendationPersisted = true;
    recordStage(
      observedStages,
      "报告解读推荐卡证明危急风险、字段目录和提示卡按当前机构生效版本消费",
    );

    const workflowTodo = await completeReportInterpretationTodo(page, {
      cardId: recommendation.cardId,
      reportType: interpretation.interpretations?.[0]?.reportType ?? "血钾检验",
    });
    const reportFamilyMatrixRowsWithTodo = completeReportFamilyMatrixTodos(
      reportFamilyMatrixRows,
      workflowTodo.todoId,
    );
    apiEvidence.workflowTodoCompletedByHuman = true;
    recordStage(observedStages, "医技或医生人工完成报告解读待办，系统不改写报告且不自动开嘱");

    await attachDiagnosticCriticalValueEvidence(testInfo, {
      apiEvidence,
      inboundObservation,
      inboundDiagnosticReport,
      runtime,
      activationRequest: runtime.activationRequest,
      clinicalContext,
      interpretation,
      recommendation,
      workflowTodo,
      reportFamilyMatrixRows: reportFamilyMatrixRowsWithTodo,
      observedStages,
    });
  });
});

function createApiEvidence(): DiagnosticCriticalValueApiEvidence {
  return {
    fhirObservationAccepted: false,
    fhirDiagnosticReportAccepted: false,
    contextSnapshotContainsInboundResources: false,
    currentRuntimeContainsDiagnosticAssets: false,
    reportInterpretationTriggeredFromFrontdesk: false,
    criticalRecommendationPersisted: false,
    workflowTodoCompletedByHuman: false,
  };
}

async function createCriticalValueContextFromFrontdesk(
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
  const maskedName = `技*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = patientDialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("68");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "S36 危急值报告演练创建脱敏患者");
  const patientId = requireText(
    textField(await responseData(patientResponse), "mpiId"),
    "S36 患者创建响应必须返回 MPI",
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
  await chooseDialogOption(page, contextDialog, "就诊类型", "住院就诊");
  await contextDialog.getByLabel("诊断/随访病种").fill(`S36 医技报告危急值演练 ${suffix}`);
  await chooseDialogOption(page, contextDialog, "风险分层", "高风险");
  await contextDialog.getByLabel("医技报告项目").fill("血钾检验");
  await contextDialog.getByLabel("报告结论").fill("待外部 LIS/FHIR 入站危急值报告回流。");
  await contextDialog.getByLabel("异常重点").fill("等待危急值入站");
  await contextDialog
    .getByLabel("建立原因")
    .fill("S36 医技报告危急值代表切片：准备外部入站上下文。");
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, "S36 演练建立 ACTIVE 快照");
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

async function createFhirInboundAdapter(page: Page, suffix: string) {
  const webhookId = `wh-s36-critical-${suffix.toLowerCase()}`;
  const adapterId = `fhir-s36-critical-${suffix.toLowerCase()}`;
  const webhook = await postApi(page, "/engine/integration/webhooks", {
    webhookId,
    name: `S36 危急值 FHIR 签名密钥 ${suffix}`,
    callbackUrl: "https://lis.s36.example.test/medkernel/fhir",
    eventsSubscribed: "FHIR_CREATE",
  });
  await expectOk(webhook, "创建 S36 FHIR 签名 Webhook");
  const webhookData = await responseData(webhook);
  const adapter = await postApi(page, "/engine/integration/adapters", {
    adapterId,
    name: `S36 危急值 FHIR/LIS 门面 ${suffix}`,
    protocolType: "FHIR",
    configJson: JSON.stringify({
      baseUrl: "https://lis.s36.example.invalid",
      outboundPath: "/medkernel/fhir/compensation",
      healthPath: "/health",
      fhir: {
        enabled: true,
        signatureWebhookId: webhookId,
        allowedSourceIps: ["10.0.0.8"],
        desensitizeResponse: true,
      },
    }),
  });
  await expectOk(adapter, "创建 S36 FHIR/LIS 入站适配器");
  return {
    adapterId,
    webhookId,
    sharedSecret: requireText(
      textField(webhookData, "sharedSecret"),
      "Webhook 创建响应必须返回一次性共享密钥",
    ),
  };
}

async function postSignedFhirResource(
  page: Page,
  options: {
    adapterId: string;
    secret: string;
    snapshot: Pick<ContextSnapshotSummary, "snapshotId" | "patientId" | "runtimeReleaseId">;
    resourceType: "Observation" | "DiagnosticReport";
    resource: Record<string, unknown>;
  },
) {
  const timestamp = currentEpochSeconds();
  const signature = `sha256=${signHmacSha256(options.secret, timestamp, options.resource)}`;
  const response = await postApi(
    page,
    `/engine/integration/fhir/R4/${options.resourceType}?snapshotId=${encodeURIComponent(
      options.snapshot.snapshotId,
    )}`,
    options.resource,
    {
      "Content-Type": "application/fhir+json",
      "X-MedKernel-Fhir-Adapter": options.adapterId,
      "X-MedKernel-Timestamp": timestamp,
      "X-MedKernel-Signature": signature,
      "X-Forwarded-For": "10.0.0.8",
      "X-MedKernel-Clinical-Setting": "INPATIENT",
    },
  );
  await expectHttpOk(response, `FHIR R4 ${options.resourceType} 危急值入站`);
  const outcome = await response.json();
  expect(textField(outcome, "resourceType"), "FHIR 入站响应必须是 OperationOutcome").toBe(
    "OperationOutcome",
  );
  const integrationStatus = textField(outcome, "integrationStatus");
  expect(
    ["NOT_CONNECTED", "RETRYING"].includes(integrationStatus ?? ""),
    "FHIR 入站必须登记诚实外部补偿状态或异步补偿队列状态",
  ).toBe(true);
  const fhirId = requireText(
    textField(options.resource, "id"),
    `${options.resourceType} 入站资源必须有 FHIR id`,
  );
  const compensationMessageId = `fhir-r4-${options.resourceType.toLowerCase()}-${fhirId}`;
  const compensation = await waitForIntegrationCompensation(page, compensationMessageId);
  const operationOutcomeContainsNotConnected = JSON.stringify(outcome).includes("NOT_CONNECTED");
  const compensationStatus = textField(compensation, "status");
  expect(
    operationOutcomeContainsNotConnected || compensationStatus === "NOT_CONNECTED",
    "FHIR 入站必须由同步响应或补偿日志说明 NOT_CONNECTED 诚实状态",
  ).toBe(true);
  const base: FhirInboundEvidence = {
    fhirResourceType: options.resourceType,
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
  };
  if (options.resourceType === "Observation") {
    return {
      ...base,
      canonicalResourceType: "OBSERVATION",
      code: "LAB.POTASSIUM",
      displayName: "血钾",
      valueNumeric: 6.3,
      unit: "mmol/L",
      criticalFlag: "HH",
    };
  }
  const familyFixture = diagnosticReportFamilyFixtures.find((fixture) =>
    fhirId.startsWith(`${fixture.fhirIdPrefix}-`),
  );
  if (familyFixture) {
    return {
      ...base,
      canonicalResourceType: "DIAGNOSTIC_REPORT",
      reportFamilyCode: familyFixture.reportFamilyCode,
      reportFamilyName: familyFixture.reportFamilyName,
      fhirCode: familyFixture.fhirCode,
      reportType: familyFixture.reportType,
      conclusion: familyFixture.conclusion,
      signedStatus: "FINAL",
    };
  }
  return {
    ...base,
    canonicalResourceType: "DIAGNOSTIC_REPORT",
    reportType: "血钾检验",
    conclusion: "血钾 6.3 mmol/L，危急值，已复核并签发。",
    signedStatus: "FINAL",
  };
}

function criticalObservationResource(
  snapshot: Pick<ContextSnapshotSummary, "patientId">,
  suffix: string,
) {
  return {
    resourceType: "Observation",
    id: `obs-critical-k-${suffix.toLowerCase()}`,
    status: "final",
    subject: { reference: `Patient/${snapshot.patientId}` },
    code: {
      coding: [{ system: "urn:medkernel:local:lis", code: "LAB.POTASSIUM", display: "血钾" }],
      text: "血钾",
    },
    interpretation: [
      {
        coding: [
          {
            system: "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation",
            code: "HH",
            display: "Critical high",
          },
        ],
        text: "危急值",
      },
    ],
    effectiveDateTime: "2026-07-07T00:00:00Z",
    valueQuantity: {
      value: 6.3,
      unit: "mmol/L",
      system: "http://unitsofmeasure.org",
      code: "mmol/L",
    },
  };
}

function criticalDiagnosticReportResource(
  snapshot: Pick<ContextSnapshotSummary, "patientId">,
  suffix: string,
) {
  return {
    resourceType: "DiagnosticReport",
    id: `dr-critical-k-${suffix.toLowerCase()}`,
    status: "final",
    subject: { reference: `Patient/${snapshot.patientId}` },
    code: {
      coding: [{ system: "urn:medkernel:local:lis", code: "LAB.POTASSIUM", display: "血钾检验" }],
      text: "血钾检验",
    },
    conclusion: "血钾 6.3 mmol/L，危急值，已复核并签发。",
    effectiveDateTime: "2026-07-07T00:00:00Z",
    issued: "2026-07-07T00:01:00Z",
    note: [{ text: "外部 LIS/FHIR 入站，报告已签发，仅供中枢解读与闭环任务使用。" }],
  };
}

function diagnosticReportFamilyResource(
  snapshot: Pick<ContextSnapshotSummary, "patientId">,
  fixture: DiagnosticReportFamilyFixture,
  suffix: string,
) {
  return {
    resourceType: "DiagnosticReport",
    id: `${fixture.fhirIdPrefix}-${suffix.toLowerCase()}`,
    status: "final",
    subject: { reference: `Patient/${snapshot.patientId}` },
    code: {
      coding: [
        {
          system: "urn:medkernel:local:diagnostic-report-family",
          code: fixture.fhirCode,
          display: fixture.reportType,
        },
      ],
      text: fixture.reportType,
    },
    conclusion: fixture.conclusion,
    effectiveDateTime: "2026-07-07T00:02:00Z",
    issued: "2026-07-07T00:03:00Z",
    resultsInterpreter: [{ display: "本地上线演练医技科" }],
    note: [{ text: fixture.note }],
  };
}

async function readContextSnapshot(
  page: Page,
  snapshotId: string,
): Promise<ContextSnapshotSummary> {
  const response = await getApi(
    page,
    `/engine/context/snapshots/${encodeURIComponent(snapshotId)}`,
  );
  await expectOk(response, "回读 S36 危急值上下文快照");
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

async function waitForIntegrationCompensation(page: Page, messageId: string) {
  const deadline = Date.now() + 20_000;
  let lastStatus: string | null = null;
  while (Date.now() < deadline) {
    const response = await getApi(page, "/engine/integration/logs?page=1&size=50");
    await expectOk(response, "读取 FHIR 入站外部补偿日志");
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
  throw new Error(`FHIR 入站补偿日志 ${messageId} 未收敛到 NOT_CONNECTED，最后状态：${lastStatus}`);
}

function assertContextContainsInboundCriticalResources(options: {
  context: ContextSnapshotSummary;
  runtime: { releaseId: string };
  inboundObservation: Record<string, unknown>;
  inboundDiagnosticReport: Record<string, unknown>;
}) {
  expect(options.context.runtimeReleaseId, "上下文回读必须保持当前机构生效版本").toBe(
    options.runtime.releaseId,
  );
  const observations = arrayField(options.context.resources, "observations");
  const diagnosticReports = arrayField(options.context.resources, "diagnosticReports");
  const observation = observations.find(
    (item) =>
      textField(item, "observationId") === options.inboundObservation.fhirId &&
      textField(item, "code") === options.inboundObservation.code &&
      textField(item, "sourceSystem") === "FHIR_R4" &&
      textField(item, "criticalFlag") === options.inboundObservation.criticalFlag,
  );
  const report = diagnosticReports.find(
    (item) =>
      textField(item, "reportId") === options.inboundDiagnosticReport.fhirId &&
      textField(item, "reportType") === options.inboundDiagnosticReport.reportType &&
      textField(item, "sourceSystem") === "FHIR_R4" &&
      (textField(item, "conclusion") ?? "").includes("危急"),
  );
  expect(observation, "上下文回读必须包含 FHIR 入站 Observation 危急值").toBeTruthy();
  expect(report, "上下文回读必须包含 FHIR 入站已签发 DiagnosticReport").toBeTruthy();
  return {
    patientId: options.context.patientId,
    contextSnapshotId: options.context.snapshotId,
    runtimeReleaseId: options.context.runtimeReleaseId,
    resources: options.context.resources,
  };
}

function assertContextContainsDiagnosticReportFamilyMatrix(options: {
  context: ContextSnapshotSummary;
  inboundReports: Array<Record<string, unknown>>;
}) {
  const diagnosticReports = arrayField(options.context.resources, "diagnosticReports");
  for (const inbound of options.inboundReports) {
    const report = diagnosticReports.find(
      (item) =>
        textField(item, "reportId") === inbound.fhirId &&
        textField(item, "reportType") === inbound.reportType &&
        textField(item, "sourceSystem") === "FHIR_R4",
    );
    expect(
      report,
      `上下文回读必须包含 ${inbound.reportFamilyName ?? inbound.reportType} DiagnosticReport`,
    ).toBeTruthy();
  }
}

function assertDiagnosticReportFamilyConsumerMatrix(options: {
  interpretation: ReportInterpretationPayload;
  context: ContextSnapshotSummary;
  inboundReports: Array<Record<string, unknown>>;
}): DiagnosticReportFamilyMatrixRow[] {
  assertContextContainsDiagnosticReportFamilyMatrix({
    context: options.context,
    inboundReports: options.inboundReports,
  });
  return options.inboundReports.map((inbound) => {
    const interpretation = options.interpretation.interpretations?.find(
      (item) => item.reportId === inbound.fhirId && item.reportType === inbound.reportType,
    );
    expect(
      interpretation,
      `报告解读消费者必须处理 ${inbound.reportFamilyName ?? inbound.reportType}`,
    ).toBeTruthy();
    expect(
      interpretation?.recommendations?.some(
        (text) => text.includes("不自动") && text.includes("医嘱"),
      ),
      `${inbound.reportFamilyName ?? inbound.reportType} 解读建议必须保留不自动开嘱边界`,
    ).toBe(true);
    return {
      reportFamilyCode: requireFamilyCode(inbound),
      reportFamilyName: requireText(
        textField(inbound, "reportFamilyName"),
        "五类报告族矩阵行必须返回 reportFamilyName",
      ),
      fhirId: requireText(textField(inbound, "fhirId"), "五类报告族矩阵行必须返回 fhirId"),
      reportType: requireText(
        textField(inbound, "reportType"),
        "五类报告族矩阵行必须返回 reportType",
      ),
      fhirCode: requireText(textField(inbound, "fhirCode"), "五类报告族矩阵行必须返回 fhirCode"),
      sourceSystem: "FHIR_R4",
      standardResourceVerified: true,
      consumerVerified: true,
      workflowTodoCompleted: false,
      degradationVerified: hasDiagnosticNotConnectedEvidence(inbound),
      noReportRewrite: true,
      noAutoOrder: true,
      reportInterpretationId: requireText(
        textField(interpretation, "reportId"),
        "五类报告族矩阵行必须绑定报告解读 reportId",
      ),
      workflowTodoId: "",
    };
  });
}

function completeReportFamilyMatrixTodos(
  rows: DiagnosticReportFamilyMatrixRow[],
  workflowTodoId: string,
) {
  return rows.map((row) => ({
    ...row,
    workflowTodoCompleted: true,
    workflowTodoId,
  }));
}

async function generateReportInterpretationFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: {
      releaseId: string;
      knowledgeAsset: RuntimeReleaseItem;
    };
    knowledge: DiagnosticRuntimeAssetCandidate;
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
  const snapshotButton = dialog.locator(
    `button[data-snapshot-id="${options.snapshot.snapshotId}"]`,
  );
  await expect(
    snapshotButton,
    `报告解读弹窗必须展示本轮上下文 ${options.snapshot.snapshotId}`,
  ).toBeVisible({
    timeout: 20_000,
  });
  await snapshotButton.click();
  const responsePromise = waitForPost(page, "/engine/recommendations/report-interpretation");
  await dialog.getByRole("button", { name: "生成报告解读" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "临床用户从真实前台生成医技报告解读");
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
      candidate.itemCode === options.knowledge.assetIdentity &&
      candidate.versionNo === options.knowledge.versionNo &&
      candidate.criticalRisk === true,
  );
  expect(item, "报告解读必须基于血钾医技项目说明书并识别危急风险").toBeTruthy();
  expect(item?.sourceVersionId, "报告解读必须返回知识来源版本身份").toBeGreaterThan(0);
  expect(
    item?.recommendations?.some((text) => text.includes("不自动")),
    "报告解读建议必须说明不自动处理",
  ).toBe(true);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return interpretation;
}

async function findReportInterpretationRecommendation(
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
  },
) {
  const todos = await getApi(
    page,
    `/engine/workflow/todos?sourceType=REPORT_INTERPRETATION&patientId=${encodeURIComponent(
      options.snapshot.patientId,
    )}&status=PENDING&page=1&size=20`,
  );
  await expectOk(todos, "读取报告解读待办投影");
  const todo = pageItems(await responseData(todos)).find(
    (item) =>
      textField(item, "sourceType") === "REPORT_INTERPRETATION" &&
      textField(item, "status") === "PENDING" &&
      textField(item, "patientId") === options.snapshot.patientId,
  );
  const cardId = requireText(textField(todo, "sourceId"), "报告解读待办必须投影推荐卡 sourceId");
  const detailResponse = await getApi(
    page,
    `/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
  );
  await expectOk(detailResponse, "读取报告解读推荐卡详情");
  const detail = await responseData(detailResponse);
  const explanation = parseJsonRecord(
    requireText(
      textFieldAtPath(detail, "card.explanationJson"),
      "报告解读推荐卡详情必须返回解释 JSON",
    ),
  );
  expect(textFieldAtPath(detail, "trigger.contextSnapshotId"), "推荐卡必须绑定本轮上下文").toBe(
    options.snapshot.snapshotId,
  );
  expect(textFieldAtPath(detail, "trigger.runtimeReleaseId"), "推荐卡必须绑定本轮 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(textFieldAtPath(detail, "card.cardType"), "报告解读推荐卡类型必须是 LAB").toBe("LAB");
  expect(textFieldAtPath(detail, "card.status"), "报告解读推荐卡必须等待人工确认").toBe("PENDING");
  expect(booleanFieldAtPath(detail, "card.requiresPhysicianConfirmation")).toBe(true);
  expect(booleanFieldAtPath(detail, "card.aiGenerated")).toBe(false);
  expect(textField(explanation, "runtimeReleaseId"), "解释必须绑定本轮 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(textField(explanation, "itemCode"), "解释必须绑定血钾医技项目说明书").toBe(
    options.runtime.knowledgeAsset.assetIdentity,
  );
  expect(textField(explanation, "sourceContentHash"), "解释必须返回知识正文 hash").toBe(
    options.runtime.knowledgeAsset.contentHash,
  );
  expect(booleanField(explanation, "criticalRisk"), "解释必须证明危急风险").toBe(true);
  assertRuntimeAssetEvidence(
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
    explanation,
  };
}

async function completeReportInterpretationTodo(
  page: Page,
  options: { cardId: string; reportType: string },
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/workflow/todos"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "协同任务" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );
  const cardLink = page.locator(`a[href*="cardId=${options.cardId}"]`).first();
  await expect(cardLink, "应能定位本轮推荐卡对应的报告解读待办链接").toBeVisible({
    timeout: 30_000,
  });
  const todoRow = cardLink.locator("xpath=ancestor::tr");
  await expect(todoRow, "应能定位本轮报告解读协同待办").toBeVisible({ timeout: 30_000 });
  const completeResponsePromise = waitForPost(page, "/engine/workflow/todos/");
  await todoRow.getByRole("button", { name: "完成" }).click();
  const completeDialog = page.getByRole("dialog", { name: "完成待办" });
  await expect(completeDialog).toBeVisible({ timeout: 10_000 });
  const completionReason =
    "医技已复核危急值报告解读提示，确认仅作辅助，不改写已签发报告，不自动开嘱。";
  await completeDialog.getByLabel("完成说明").fill(completionReason);
  await completeDialog.getByRole("button", { name: "确认完成" }).click();
  const completeResponse = await completeResponsePromise;
  await expectHttpOk(completeResponse, "完成人工报告解读待办");
  const completed = await responseData(completeResponse);
  expect(textField(completed, "status"), "报告解读待办完成后必须为 COMPLETED").toBe("COMPLETED");
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
  };
}

async function attachDiagnosticCriticalValueEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: DiagnosticCriticalValueApiEvidence;
    inboundObservation: unknown;
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
    reportFamilyMatrixRows: DiagnosticReportFamilyMatrixRow[];
    observedStages: Set<string>;
  },
) {
  for (const stage of requiredStages) {
    expect(evidence.observedStages.has(stage), `缺少 S36 危急值报告阶段：${stage}`).toBe(true);
  }
  await testInfo.attach("diagnostic-critical-value-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S36"],
        productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
        versionedAssets: ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"],
        deliveryShapes: ["API_EVENT"],
        serviceCombinations: ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME"],
        scopeStatement:
          "医技危急值代表切片：FHIR/LIS 入站 Observation 与 DiagnosticReport 后完成人工报告解读闭环，不代表完整 LIS/PACS/RIS/病理/心电全链路或完整危急值制度。",
        standardPatientResourceConsumerMatrix: standardPatientResourceConsumerMatrix([
          {
            resourceType: "DiagnosticReport",
            resourcePath: "clinicalContext.resources.diagnosticReports[0]",
            sourceSystem: "FHIR_R4",
            sourceIdPath: "clinicalContext.resources.diagnosticReports[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "REPORT_INTERPRETATION",
            consumerEvidencePaths: ["interpretation.interpretations[0]"],
            consumerVerified: true,
            auditEvidencePaths: ["workflowTodo.todoId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
        ]),
        thirdPartySystemFamilyConsumerSlice: {
          systemFamilyCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
          familyName: "PACS/RIS、超声、病理、内镜、心电",
          sourceSystems: ["PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG", "FHIR_R4"],
          canonicalResources: ["Observation", "DiagnosticReport"],
          consumer: "REPORT_INTERPRETATION",
          consumerVerified: true,
          standardResourceVerified: true,
          degradationVerified: true,
          auditVerified: true,
          noAutoOrder: true,
          noReportRewrite: true,
          scopeStatement:
            "PACS/RIS、超声、病理、内镜、心电系统族代表消费者切片：已验证医技报告标准资源入站、报告解读消费者、人工复核待办和断连诚实降级；不代表完整 PACS/RIS/病理/内镜/心电系统族覆盖、完整第三方系统族覆盖或完整上线验收。",
        },
        diagnosticReportFamilyConsumerMatrix: {
          systemFamilyCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
          matrixName: "PACS/RIS、超声、病理、内镜、心电五类医技报告族真实消费者矩阵",
          canonicalResources: ["DiagnosticReport"],
          consumer: "REPORT_INTERPRETATION",
          runtimeKnowledgeScope:
            "当前机构生效版本报告解读说明书代表，不代表五类专属说明书全量发布。",
          consumerVerified: true,
          standardResourceVerified: true,
          degradationVerified: true,
          auditVerified: true,
          noAutoOrder: true,
          noReportRewrite: true,
          scopeStatement:
            "PACS/RIS、超声、病理、内镜、心电五类医技报告族真实消费者矩阵代表切片：已验证五类 DiagnosticReport 标准资源入站、报告解读消费者、人工复核待办和断连诚实降级；不代表完整 PACS/RIS/病理/内镜/心电系统族覆盖，不代表完整第三方系统族覆盖，不代表完整上线验收。",
          rows: evidence.reportFamilyMatrixRows,
        },
        apiEvidence: evidence.apiEvidence,
        inboundObservation: evidence.inboundObservation,
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
        dedicatedReleaseContractEvidence: {
          assetType: "FIELD_CATALOG",
          assetIdentity: evidence.runtime.fieldCatalogAsset.assetIdentity,
          versionId: evidence.runtime.fieldCatalogAsset.versionId,
          productionRoute: "DIAGNOSTIC_FIELD_CATALOG_RUNTIME_BASELINE",
          releaseContract: "DIAGNOSTIC_REPORT_INTERPRETATION_FIELD_CONTRACT",
          platformBaselineVerified: true,
          activationVerified: true,
          runtimeConsumerReadbackVerified: true,
          reportInterpretationVerified: true,
          fieldEvidencePaths: [
            "recommendation.explanation.runtimeAssetEvidence[0].fields",
            "clinicalContext.resources.observations[0].criticalFlag",
            "clinicalContext.resources.diagnosticReports[0].conclusion",
          ],
          consumer: "REPORT_INTERPRETATION",
        },
        workflowTodo: evidence.workflowTodo,
        scenarioEvidence: [
          {
            code: "S36",
            observedStages: Array.from(evidence.observedStages),
          },
        ],
      },
      null,
      2,
    ),
  });
}

function assertRuntimeAssetEvidence(
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
        arrayField(item, "fields").includes("observations[].criticalFlag") &&
        arrayField(item, "fields").includes("diagnosticReports[].conclusion"),
    ),
    "推荐解释必须证明字段目录运行资产覆盖危急值报告字段",
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
    "推荐解释必须证明危急值提示卡要求人工确认",
  ).toBe(true);
}

function requireFamilyCode(value: Record<string, unknown>) {
  const code = textField(value, "reportFamilyCode");
  expect(
    diagnosticReportFamilyFixtures.some((fixture) => fixture.reportFamilyCode === code),
    "五类报告族矩阵行必须使用已定义 reportFamilyCode",
  ).toBe(true);
  return code as DiagnosticReportFamilyFixture["reportFamilyCode"];
}

function hasDiagnosticNotConnectedEvidence(value: Record<string, unknown>) {
  return (
    value.operationOutcomeContainsNotConnected === true ||
    value.compensationStatus === "NOT_CONNECTED"
  );
}

function recordStage(stages: Set<string>, stage: (typeof requiredStages)[number]) {
  stages.add(stage);
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
    .filter({ hasText: new RegExp(`(^|\\s)${escapeRegExp(optionText)}(\\s|$)`, "u") })
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

function currentEpochSeconds() {
  return Math.floor(Date.now() / 1000).toString();
}

function signHmacSha256(secret: string, timestamp: string, payload: unknown) {
  return createHmac("sha256", secret)
    .update(`${timestamp}.${typeof payload === "string" ? payload : JSON.stringify(payload)}`)
    .digest("hex");
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
