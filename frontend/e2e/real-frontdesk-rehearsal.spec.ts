import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { ensureReadySession, requiredRuntimeAssetsForRehearsal } from "./support/auth";
import { standardPatientResourceConsumerMatrix } from "./support/standardPatientResourceMatrix";

type RuntimeCollectors = {
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

type RuntimeRecord = RuntimeCollectors & {
  stage: string;
  url: string;
};

type FrontdeskScenarioEvidence = {
  code: string;
  observedStages: string[];
};

type ContextSnapshotSummary = {
  snapshotId: string;
  patientId: string;
  encounterId?: string | null;
  runtimeReleaseId?: string | null;
  resources?: Record<string, unknown>;
};

type OrgUnitSummary = {
  id?: string;
  parentId?: string | null;
  level?: string;
  code?: string;
  name?: string;
  status?: string;
  orgPath?: string | null;
  facilityType?: string | null;
};

type MpiPatientCreated = {
  mpiId: string;
  maskedName: string;
  idLast4: string;
};

type RecommendationEvaluationCard = {
  cardId?: string;
  sourceSummary?: string;
  explanationJson?: string;
};

type RecommendationEvaluationPayload = {
  triggerId?: string;
  status?: string;
  visibleCardCount?: number;
  suppressedCardCount?: number;
  traceId?: string;
  cards?: RecommendationEvaluationCard[];
};

type RecommendationCardDetailPayload = {
  card?: RecommendationEvaluationCard;
  trigger?: { runtimeReleaseId?: string };
};

type ClaimEvaluationIndicatorSummary = {
  indicatorId: string;
  indicatorCode: string;
  name: string;
  versionNo: number;
};

type ClaimRuntimeCandidateSummary = {
  assetType: "EVALUATION";
  assetIdentity: string;
  versionId: string;
  versionNo?: string;
  sourceLayer?: string;
  status: "PUBLISHED";
};

type LocalRuntimeCandidateSummary = {
  assetType: string;
  assetIdentity: string;
  versionId: string;
  versionNo?: string;
  sourceLayer?: string;
  status?: string;
};

type RuntimeAssetSelection = {
  assetType?: string;
  assetIdentity?: string;
  versionId?: string | null;
};

type RuntimeReleaseDetailPayload = {
  data?: {
    release?: {
      releaseId?: string;
      revisionNo?: number;
      platformBaselineReleaseId?: string;
    };
    items?: Array<{
      assetType?: string;
      assetIdentity?: string;
      entryState?: string;
      versionId?: string | null;
    }>;
  } | null;
};

type InsuranceAuditSummary = {
  issueId: string;
  evaluationRunId: string;
};

type QualityRectificationSummary = {
  findingId: string;
  taskId: string;
  taskStatus: string;
};

type FollowupTemplateEvidence = {
  operation: "CREATE_AND_PUBLISH_FOLLOWUP_TEMPLATE";
  createStatus: number;
  publishStatus: number;
  templateId: string;
  templateCode: string;
  assetStatus?: string;
  scope?: string;
};

type FollowupRuntimeEvidence = {
  operation: "ACTIVATE_HOSPITAL_RUNTIME_WITH_FOLLOWUP";
  candidateStatus: number;
  activationStatus: number;
  runtimeReadbackStatus: number;
  runtimeReleaseId?: string | null;
  assetType: string;
  assetIdentity: string;
  versionId: string;
  sourceLayer?: string;
  entryState?: string;
  currentRuntimeContainsAsset: boolean;
};

type FollowupPlanEvidence = {
  operation: "GENERATE_FOLLOWUP_PLAN_FROM_CONTEXT";
  status: number;
  planId: string;
  templateId?: string | null;
  templateCode?: string | null;
  runtimeReleaseId?: string | null;
  patientId?: string | null;
  encounterId?: string | null;
  contextSnapshotId: string;
  taskCount: number;
  riskLevel?: string | null;
};

type FollowupQuestionnaireEvidence = {
  operation: "SUBMIT_FOLLOWUP_QUESTIONNAIRE";
  status: number;
  planId: string;
  patientId: string;
  taskId?: string | null;
  questionnaireId?: string | null;
  responseStatus?: string | null;
  source: "PATIENT_SELF_REPORT";
  submitted: boolean;
};

type FollowupAbnormalReturnEvidence = {
  operation: "REGISTER_ABNORMAL_RETURN";
  status: number;
  planId: string;
  patientId: string;
  eventId?: string | null;
  returnTaskId?: string | null;
  notificationEventId?: string | null;
  riskLevel: "HIGH";
  registered: boolean;
  noAutoOrder: boolean;
};

type FollowupResultBackflowEvidence = {
  eventId?: string | null;
  contextSnapshotId?: string | null;
  sourceQuestionnaireId?: string | null;
  abnormalFlag: "Y";
};

type FollowupBackflowContextEvidence = {
  patientId?: string | null;
  encounterId?: string | null;
  contextSnapshotId?: string | null;
  runtimeReleaseId?: string | null;
  resources?: Record<string, unknown>;
};

type FollowupS12Evidence = {
  template: FollowupTemplateEvidence;
  runtime: FollowupRuntimeEvidence;
  plan: FollowupPlanEvidence;
  questionnaire: FollowupQuestionnaireEvidence;
  abnormalReturn: FollowupAbnormalReturnEvidence;
  resultBackflow: FollowupResultBackflowEvidence;
  backflowContext: FollowupBackflowContextEvidence;
};

type QualityAlertPayload = {
  alertId?: string;
  sourceType?: string;
  sourceId?: string;
  title?: string;
};

type QualityAlertsPayload = {
  data?: {
    items?: QualityAlertPayload[];
  };
};

type QualityFindingDetailPayload = {
  data?: {
    finding?: {
      findingId?: string;
      findingCode?: string;
      runId?: string;
      indicatorId?: string;
    };
    rectificationTask?: {
      taskId?: string;
      status?: string;
    };
  };
};

const requiredFrontdeskScenarioEvidence: FrontdeskScenarioEvidence[] = [
  {
    code: "S10",
    observedStages: ["前台执行医保审核并联动质量整改"],
  },
  {
    code: "S11",
    observedStages: ["前台创建发布并激活 CLAIM 评价指标", "前台提交并复核关闭质量整改任务"],
  },
  {
    code: "S12",
    observedStages: [
      "前台创建随访方案",
      "前台发布随访方案",
      "前台生成随访计划并完成问卷与异常回院登记",
    ],
  },
];

test.describe.configure({ mode: "serial" });

test.describe("全前台真实操作演练", () => {
  test("平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生", async ({
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];
    const suffix = Date.now().toString(36);
    let snapshot: ContextSnapshotSummary | null = null;
    let insuranceAudit: InsuranceAuditSummary | null = null;
    let qualityRectification: QualityRectificationSummary | null = null;
    let followupS12: FollowupS12Evidence | null = null;

    try {
      await page.setViewportSize({ width: 1440, height: 960 });
      const adapterId = await createAdapterFromUi(page, testInfo, runtime, records, suffix);
      await createIntegrationOnboardingFromUi(page, testInfo, runtime, records, suffix, adapterId);
      await createValueSetFromUi(page, testInfo, runtime, records, suffix);
      await configureModelEgressPolicyFromUi(page, testInfo, runtime, records);
      const patient = await createMpiPatientFromUi(page, testInfo, runtime, records);
      const followupTemplate = await createFollowupTemplateFromUi(
        page,
        testInfo,
        runtime,
        records,
        suffix,
      );
      const followupTemplatePublish = await publishFollowupTemplateFromUi(
        page,
        testInfo,
        runtime,
        records,
        followupTemplate,
      );
      const claimIndicator = await createActiveClaimEvaluationIndicatorFromUi(
        page,
        testInfo,
        runtime,
        records,
        suffix,
      );
      const followupRuntime = await activateHospitalRuntimeWithClaimIndicatorFromUi(
        page,
        testInfo,
        runtime,
        records,
        claimIndicator,
        followupTemplate,
      );
      snapshot = await createContextSnapshotFromUi(page, testInfo, runtime, records, patient);
      insuranceAudit = await runInsuranceAuditFromUi(
        page,
        testInfo,
        runtime,
        records,
        snapshot,
        claimIndicator,
      );
      await assertInsuranceAuditUsesEvaluationRun(page, insuranceAudit, claimIndicator);
      qualityRectification = await closeQualityRectificationFromAlertsUi(
        page,
        testInfo,
        runtime,
        records,
        insuranceAudit,
      );
      await runCdssRecommendationFromUi(page, testInfo, runtime, records, snapshot);
      const followupExecution = await generateFollowupPlanAndHandlePatientFeedbackFromUi(
        page,
        testInfo,
        runtime,
        records,
        followupTemplate,
        snapshot,
      );
      followupS12 = {
        template: {
          operation: "CREATE_AND_PUBLISH_FOLLOWUP_TEMPLATE",
          createStatus: followupTemplate.createStatus,
          publishStatus: followupTemplatePublish.publishStatus,
          templateId: followupTemplate.templateId,
          templateCode: followupTemplate.templateCode,
          assetStatus: followupTemplatePublish.assetStatus,
          scope: followupTemplate.organizationScope,
        },
        runtime: followupRuntime,
        ...followupExecution,
      };
    } finally {
      await attachScenarioEvidence(testInfo, records, {
        snapshot,
        insuranceAudit,
        qualityRectification,
        followupS12,
      });
      await attachRuntimeRecords(testInfo, records);
    }
  });
});

async function createAdapterFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  suffix: string,
): Promise<string> {
  await ensureReadySession(page, "platform-admin");
  clearRuntime(runtime);
  await page.goto("/adapter/hub", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "系统接入" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "系统接入桌面");

  const adapterId = `real-his-${suffix}`;
  await expect(page.getByRole("button", { name: "新增适配器" })).toBeEnabled();
  await page.getByRole("button", { name: "新增适配器" }).click();
  const dialog = page.getByRole("dialog", { name: "新增适配器" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定适配器身份").fill(adapterId);
  await dialog.getByLabel("系统名称").fill(`真实演练 HIS ${suffix}`);
  await dialog.getByLabel("服务地址").fill("https://his.real-frontdesk.example.test/api");
  await dialog.getByLabel("来源字段路径").fill("/patient/identifier");
  await dialog.getByLabel("标准字段路径").fill("/subject/id");
  await dialog.getByLabel("目标标准字典").fill("ICD-10");
  await searchDialogOption(page, dialog, "术语分类", "诊断", "诊断");

  const responsePromise = waitForPost(page, "/api/v1/engine/integration/adapters");
  await dialog.getByRole("button", { name: "提交适配器" }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交适配器应返回成功").toBe(true);
  const result = (await response.json()) as { data?: { adapterId?: string } };
  expect(result.data?.adapterId).toBe(adapterId);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-adapter");
  recordCleanRuntime(page, "前台创建系统接入适配器", runtime, records);
  return adapterId;
}

async function createIntegrationOnboardingFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  suffix: string,
  adapterId: string,
) {
  await ensureReadySession(page, "platform-admin");
  clearRuntime(runtime);
  await page.goto("/adapter/hub", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "系统接入" })).toBeVisible();
  await page.getByRole("tab", { name: "接入向导" }).click();
  await expectNoRootOverflow(page, "系统接入接入向导桌面");

  const onboardingId = `onb-${adapterId}`;
  await expect(page.getByRole("button", { name: "新增接入申请" })).toBeEnabled();
  await page.getByRole("button", { name: "新增接入申请" }).click();
  const dialog = page.getByRole("dialog", { name: "新增接入申请" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定接入申请身份").fill(onboardingId);
  await dialog.getByLabel("接入申请名称").fill(`真实演练接入申请 ${suffix}`);
  await dialog.getByLabel("绑定适配器").click();
  await page.keyboard.type(`真实演练 HIS ${suffix}`);
  const adapterOption = page.getByText(`真实演练 HIS ${suffix} · REST`, { exact: true });
  await expect(adapterOption).toBeVisible({ timeout: 20_000 });
  await adapterOption.click();
  await chooseDialogFirstOption(page, dialog, "系统族", "HIS、EMR、CDR、医嘱与费用");
  await dialog.getByLabel("来源系统").fill("HIS");
  await dialog.getByLabel("业务场景").fill("门诊患者主数据");
  await dialog.getByLabel("组织范围").click();
  const facilityOption = page.getByText(/医疗服务机构$/).first();
  await expect(facilityOption).toBeVisible({ timeout: 20_000 });
  await facilityOption.click();

  const responsePromise = waitForPost(page, "/api/v1/engine/integration/onboardings");
  await dialog.getByRole("button", { name: "提交申请" }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交接入申请应返回成功").toBe(true);
  const result = (await response.json()) as { data?: { onboardingId?: string } };
  expect(result.data?.onboardingId).toBe(onboardingId);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByText(`真实演练接入申请 ${suffix}`)).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole("columnheader", { name: "最近更新" })).toBeVisible();
  await expect(page.getByText(/共 \d+ 条接入申请，当前显示 1-\d+ 条/)).toBeVisible();
  await captureEvidence(page, testInfo, "real-frontdesk-onboarding");
  recordCleanRuntime(page, "前台创建系统接入申请", runtime, records);
}

async function createValueSetFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  suffix: string,
) {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/authoring/assets", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "统一资产库" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "字段与配置资产" }).click();
  await expectNoRootOverflow(page, "统一资产库字段与配置资产桌面");

  const assetIdentity = `VS.REAL.FRONTDESK.${suffix.toUpperCase()}`;
  await expect(page.getByRole("button", { name: "新建值集" })).toBeEnabled();
  await page.getByRole("button", { name: "新建值集" }).click();
  const dialog = page.getByRole("dialog", { name: "新建值集" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定资产身份").fill(assetIdentity);
  await dialog.getByLabel("适用范围").fill("ALL");
  await dialog.getByLabel("来源依据").fill("真实前台演练：院内药品目录脱敏样例");
  await dialog.getByLabel("名称", { exact: true }).fill(`真实前台药品值集 ${suffix}`);
  await dialog.getByLabel("编码体系", { exact: true }).fill("ATC");
  await dialog.getByLabel("成员编码", { exact: true }).fill("J01GB03");
  await dialog.getByLabel("成员名称", { exact: true }).fill("庆大霉素");

  const responsePromise = waitForPost(page, "/api/v1/engine/authoring/declarative-assets");
  await dialog.getByRole("button", { name: "保存草稿" }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交值集草稿应返回成功").toBe(true);
  const result = (await response.json()) as { data?: { assetIdentity?: string } };
  expect(result.data?.assetIdentity).toBe(assetIdentity);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-value-set");
  recordCleanRuntime(page, "前台创建知识值集草稿", runtime, records);
}

async function configureModelEgressPolicyFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
) {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/advanced/ai-workflows", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "模型能力" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "模型能力安全边界桌面");

  const policyButton = page
    .getByRole("button", {
      name: /预设 .+ 模型安全边界|配置 .+ 院内模型授权边界|调整 .+ 院内模型授权边界|配置 .+ 公网模型安全策略|调整 .+ 公网模型安全策略/,
    })
    .first();
  await expect(policyButton).toBeEnabled();
  await policyButton.click();
  const dialog = page.getByRole("dialog", {
    name: /预设模型安全边界|配置院内模型授权边界|配置公网模型安全策略/,
  });
  await expect(dialog).toBeVisible();
  await expect(
    dialog.getByText(
      /当前能力仍走无模型规则链路|院内本地模型按授权使用患者上下文|公网外部模型可使用患者上下文/,
    ),
  ).toBeVisible();

  const responsePromise = waitForPut(page, "/api/v1/data-minimization/policies/model-egress/");
  await dialog
    .getByRole("button", {
      name: /保存安全边界预设|保存院内授权边界|保存公网安全策略/,
    })
    .click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交模型安全边界策略应返回成功").toBe(true);
  const result = (await response.json()) as {
    data?: { allowedFields?: string; sensitivityLevel?: string; guardrailLockedFlag?: string };
  };
  expect(result.data?.allowedFields).toContain("prompt");
  expect(result.data?.sensitivityLevel).toBe("HIGH");
  expect(result.data?.guardrailLockedFlag).toBe("Y");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-model-safety-boundary");
  recordCleanRuntime(page, "前台配置模型安全边界策略", runtime, records);
}

async function createMpiPatientFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
): Promise<MpiPatientCreated> {
  await ensureReadySession(page, "clinical-user");
  clearRuntime(runtime);
  await page.goto("/mpi", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "患者主索引桌面");

  await expect(page.getByRole("button", { name: "新增患者" })).toBeEnabled();
  await page.getByRole("button", { name: "新增患者" }).click();
  const dialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(dialog).toBeVisible();
  const maskedName = "赵*君";
  const idLast4 = String(Date.now()).slice(-4);
  await dialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = dialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await dialog.getByRole("spinbutton", { name: "年龄" }).fill("67");
  await dialog.getByLabel("身份证后四位").fill(idLast4);

  const responsePromise = waitForPost(page, "/api/v1/engine/mpi/patients");
  await dialog.getByRole("button", { name: "保存患者" }).click();
  const response = await responsePromise;
  const responseBody = await response.text();
  expect(
    response.ok(),
    `前台提交脱敏患者主索引应返回成功 status=${response.status()} body=${responseBody}`,
  ).toBe(true);
  const result = JSON.parse(responseBody) as {
    data?: { mpiId?: string; maskedName?: string; idLast4?: string };
  };
  expect(result.data?.mpiId).toBeTruthy();
  expect(result.data?.maskedName).toBe(maskedName);
  expect(result.data?.idLast4).toBe(idLast4);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-mpi-patient");
  recordCleanRuntime(page, "前台创建脱敏患者主索引", runtime, records);
  return {
    mpiId: result.data?.mpiId ?? "",
    maskedName,
    idLast4,
  };
}

async function createContextSnapshotFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  patient: MpiPatientCreated,
): Promise<ContextSnapshotSummary> {
  await ensureReadySession(page, "clinical-user");
  clearRuntime(runtime);
  await page.goto("/mpi", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(patient.maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const row = page
    .getByRole("row", {
      name: new RegExp(`${escapeRegExp(patient.maskedName)}.*${patient.idLast4}`),
    })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("alert").filter({ hasText: "暂无已生效上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();

  const dialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(dialog).toBeVisible();
  await chooseDialogOption(page, dialog, "就诊类型", "门诊复诊");
  await dialog.getByLabel("诊断/随访病种").fill("真实前台慢病随访主题");
  await chooseDialogOption(page, dialog, "风险分层", "中风险");
  await dialog.getByLabel("DRG/DIP 分组").fill("DRG-REAL-A");
  await dialog.getByLabel("本次结算金额").fill("1280.50");
  await dialog.getByLabel("医保支付金额").fill("860.00");
  await dialog
    .getByLabel("建立原因")
    .fill("真实前台演练：医生从患者 360 建立当前就诊上下文，用于医保审核与随访计划生成。");

  const responsePromise = waitForPost(page, "/api/v1/engine/context/snapshots");
  await dialog.getByRole("button", { name: "生成上下文快照" }).click();
  const response = await responsePromise;
  const responseBody = await response.text();
  expect(
    response.ok(),
    `前台建立当前就诊上下文应返回成功 status=${response.status()} body=${responseBody}`,
  ).toBe(true);
  const result = JSON.parse(responseBody) as {
    data?: {
      snapshotId?: string;
      resources?: Record<string, unknown> & { encounters?: Array<{ encounterId?: string }> };
    };
  };
  expect(result.data?.snapshotId, "上下文创建响应应返回快照身份").toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByText("当前就诊上下文已建立")).toBeVisible({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-context-snapshot");
  recordCleanRuntime(page, "前台建立当前就诊上下文与医保结算事实快照", runtime, records);
  return {
    snapshotId: result.data?.snapshotId ?? "",
    patientId: patient.mpiId,
    encounterId: result.data?.resources?.encounters?.[0]?.encounterId ?? null,
    resources: result.data?.resources ?? {},
  };
}

async function createActiveClaimEvaluationIndicatorFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  suffix: string,
): Promise<ClaimEvaluationIndicatorSummary> {
  const department = await ensureQualityDepartmentFromFrontdesk(page, suffix);
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/qc/eval/sets", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "评价指标" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "评价指标发布桌面");

  const indicatorCode = `INS.REAL.CLAIM.${suffix.toUpperCase()}`;
  const indicatorName = `真实前台医保合规指标 ${suffix}`;
  const claimIndicatorDraft = { subjectType: "CLAIM" as const };
  await page.getByRole("button", { name: "新建指标" }).click();
  const dialog = page.getByRole("dialog", { name: "新建评价指标" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定评价指标身份").fill(indicatorCode);
  await dialog.getByLabel("指标名称").fill(indicatorName);
  await chooseDialogOption(page, dialog, "评估主体", "医保合规", claimIndicatorDraft.subjectType);
  await searchDialogOption(page, dialog, "责任科室", department.name ?? "", department.name ?? "");
  await dialog
    .getByLabel("来源依据")
    .fill("真实前台演练：医保合规评价指标由前台发布并进入机构生效版本。");
  await dialog.getByLabel("评分定义").fill("P1级医保合规缺陷，命中后必须由责任科室整改。");

  const factInputs = dialog.getByRole("combobox", { name: "上下文字段路径" });
  const operatorInputs = dialog.getByRole("combobox", { name: "算子" });
  const kindInputs = dialog.getByRole("combobox", { name: "比较值类型" });
  const valueInputs = dialog.getByRole("textbox", { name: /^比较值$/ });
  await factInputs.nth(0).fill("claims[].totalCost");
  await chooseIndexedSelectOption(page, operatorInputs.nth(0), "大于");
  await chooseIndexedSelectOption(page, kindInputs.nth(0), "数值");
  await valueInputs.nth(0).fill("0");
  await factInputs.nth(1).fill("claims[].totalCost");
  await chooseIndexedSelectOption(page, operatorInputs.nth(1), "大于");
  await chooseIndexedSelectOption(page, kindInputs.nth(1), "数值");
  await valueInputs.nth(1).fill("999999");

  const createResponsePromise = waitForPost(page, "/api/v1/engine/evaluation/indicators");
  await dialog.getByRole("button", { name: "创建指标草稿" }).click();
  const createResponse = await createResponsePromise;
  const createText = await createResponse.text();
  expect(
    createResponse.ok(),
    `前台创建 CLAIM 评价指标草稿应返回成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: {
      indicatorId?: string;
      indicatorCode?: string;
      name?: string;
      subjectType?: string;
      status?: string;
      versionNo?: number;
    };
  };
  expect(created.data?.subjectType, "前台创建的评价指标必须面向医保合规主体").toBe(
    claimIndicatorDraft.subjectType,
  );
  expect(created.data?.status, "评价指标创建后必须先进入草稿").toBe("DRAFT");
  const indicatorId = created.data?.indicatorId ?? "";
  expect(indicatorId, "评价指标创建响应必须返回 indicatorId").toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await page.getByLabel("评价指标身份筛选").fill(indicatorCode);
  const row = page
    .getByRole("row", {
      name: new RegExp(escapeRegExp(indicatorName)),
    })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: "查看指标详情" }).click();
  const drawer = page.getByRole("dialog").filter({ hasText: "指标详情" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });

  await transitionClaimIndicator(page, drawer, {
    button: "提交安全复核",
    path: `/api/v1/engine/evaluation/indicators/${indicatorId}/submit`,
    expectedStatus: "PENDING_REVIEW",
  });
  await confirmClaimIndicatorRelease(page, drawer, {
    button: "确认发布",
    title: "确认发布",
    ok: "确认发布",
    path: `/api/v1/engine/evaluation/indicators/${indicatorId}/publish`,
    reason: "真实前台演练：医保合规指标安全复核通过，确认发布。",
    expectedStatus: "PUBLISHED",
  });
  await confirmClaimIndicatorRelease(page, drawer, {
    button: "开始灰度",
    title: "开始 10% 床位灰度",
    ok: "确认灰度",
    path: `/api/v1/engine/evaluation/indicators/${indicatorId}/gray`,
    reason: "真实前台演练：先按默认灰度观察医保合规指标。",
    expectedStatus: "GRAY",
  });
  await confirmClaimIndicatorRelease(page, drawer, {
    button: "全量激活",
    title: "全量激活",
    ok: "确认全量",
    path: `/api/v1/engine/evaluation/indicators/${indicatorId}/activate`,
    reason: "真实前台演练：灰度观察通过，允许 CLAIM 指标全量激活。",
    expectedStatus: "ACTIVE",
  });
  await expect(drawer.getByText("生效中", { exact: true }).first()).toBeVisible({
    timeout: 20_000,
  });
  await captureEvidence(page, testInfo, "real-frontdesk-claim-evaluation-indicator-active");
  recordCleanRuntime(page, "前台创建发布并激活 CLAIM 评价指标", runtime, records);

  return {
    indicatorId,
    indicatorCode: created.data?.indicatorCode ?? indicatorCode,
    name: created.data?.name ?? indicatorName,
    versionNo: created.data?.versionNo ?? 1,
  };
}

async function ensureQualityDepartmentFromFrontdesk(
  page: Page,
  suffix: string,
): Promise<OrgUnitSummary> {
  await ensureReadySession(page, "platform-admin");
  const hospital = await resolveLocalRehearsalHospital(page);
  const existing = await firstActiveDepartment(page, hospital.id ?? "");
  if (existing) {
    return existing;
  }

  await page.goto("/tenant/onboarding", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "服务机构" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "组织树" }).click();

  const department = await createOrgUnitFromUi(page, {
    levelLabel: "科室",
    parentId: hospital.id,
    parentName: hospital.name,
    code: `E2E-QC-DEPT-${suffix.toUpperCase()}`,
    name: `上线演练质控科${suffix.slice(-4)}`,
  });
  expect(department.level, "质控责任科室必须按组织树创建为科室").toBe("DEPARTMENT");
  expect(department.parentId, "质控责任科室必须归属本地上线演练医院").toBe(hospital.id);
  return department;
}

async function createOrgUnitFromUi(
  page: Page,
  options: {
    levelLabel: string;
    facilityTypeLabel?: string;
    parentId?: string;
    parentName?: string;
    code: string;
    name: string;
  },
): Promise<OrgUnitSummary> {
  const panel = page.locator(".ant-card").filter({ hasText: "新增组织节点" }).first();
  await expect(panel).toBeVisible();
  await chooseDialogOption(page, panel, "组织层级", options.levelLabel);
  if (options.facilityTypeLabel) {
    await chooseDialogOption(page, panel, "机构类型", options.facilityTypeLabel);
  }
  await panel.getByLabel("稳定组织身份").fill(options.code);
  await panel.getByLabel("组织名称").fill(options.name);
  await searchDialogOption(
    page,
    panel,
    "直接上级",
    options.parentName ?? "",
    options.parentName ?? "",
  );
  const responsePromise = waitForPost(page, "/api/v1/engine/org/org-units");
  await panel.getByRole("button", { name: "保存组织节点" }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(response.ok(), `前台保存组织节点应返回成功 status=${response.status()} body=${text}`).toBe(
    true,
  );
  const payload = JSON.parse(text) as { data?: OrgUnitSummary };
  expect(payload.data?.id, "组织节点响应必须返回 id").toBeTruthy();
  if (options.parentId) {
    expect(payload.data?.parentId, "组织节点必须绑定预期直接上级").toBe(options.parentId);
  }
  await expect(page.getByText(options.name, { exact: true })).toBeVisible({ timeout: 20_000 });
  return payload.data ?? {};
}

async function firstActiveDepartment(
  page: Page,
  hospitalId: string,
): Promise<OrgUnitSummary | null> {
  expect(hospitalId, "查找责任科室前必须解析本地上线演练医院 ID").toBeTruthy();
  const response = await page.request.get("/medkernel/api/v1/engine/org/org-units", {
    params: {
      page: "1",
      size: "20",
      sort: "name,asc",
      level: "DEPARTMENT",
      status: "ACTIVE",
      ancestorId: hospitalId,
    },
    headers: { "X-Trace-Id": `e2e-qc-department-${Date.now()}` },
  });
  const text = await response.text();
  expect(
    response.ok(),
    `应能读取本地上线演练医院科室 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as { data?: { items?: OrgUnitSummary[] } };
  return (
    (parsed.data?.items ?? []).find(
      (item) => item.level === "DEPARTMENT" && item.status === "ACTIVE" && item.id && item.name,
    ) ?? null
  );
}

async function resolveLocalRehearsalHospital(page: Page): Promise<OrgUnitSummary> {
  const hospital = await chooseHospitalByName(page, "本地上线演练医院", { openSelect: false });
  expect(hospital.id, "本地上线演练医院必须返回组织 ID").toBeTruthy();
  expect(hospital.level, "本地上线演练医院必须是医疗机构节点").toBe("FACILITY");
  return hospital;
}

async function transitionClaimIndicator(
  page: Page,
  drawer: Locator,
  options: { button: string; path: string; expectedStatus: string },
) {
  const responsePromise = waitForPost(page, options.path);
  await drawer.getByRole("button", { name: options.button }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(
    response.ok(),
    `评价指标 ${options.button} 应返回成功 status=${response.status()} body=${text}`,
  ).toBe(true);
  const payload = JSON.parse(text) as { data?: { status?: string } };
  expect(payload.data?.status, `评价指标 ${options.button} 后状态应正确`).toBe(
    options.expectedStatus,
  );
}

async function confirmClaimIndicatorRelease(
  page: Page,
  drawer: Locator,
  options: {
    button: string;
    title: string;
    ok: string;
    path: string;
    reason: string;
    expectedStatus: string;
  },
) {
  await drawer.getByRole("button", { name: options.button }).click();
  const modal = page.getByRole("dialog", { name: options.title });
  await expect(modal).toBeVisible({ timeout: 20_000 });
  await modal.getByLabel("发布说明").fill(options.reason);
  const responsePromise = waitForPost(page, options.path);
  await modal.getByRole("button", { name: options.ok }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(
    response.ok(),
    `评价指标 ${options.button} 应返回成功 status=${response.status()} body=${text}`,
  ).toBe(true);
  const payload = JSON.parse(text) as { data?: { status?: string } };
  expect(payload.data?.status, `评价指标 ${options.button} 后状态应正确`).toBe(
    options.expectedStatus,
  );
  await expect(modal).toBeHidden({ timeout: 20_000 });
}

async function activateHospitalRuntimeWithClaimIndicatorFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  claimIndicator: ClaimEvaluationIndicatorSummary,
  followupTemplate: { templateCode: string },
): Promise<FollowupRuntimeEvidence> {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/config/releases", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "机构生效版本" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "机构生效版本" }).click();
  const hospital = await chooseHospitalByName(page, "本地上线演练医院");
  await expect(page.getByText(/当前机构生效版本 第 \d+ 版/)).toBeVisible({ timeout: 20_000 });

  const claimCandidate = await assertHospitalRuntimeCandidateContainsClaimIndicator(
    page,
    hospital.id ?? "",
    claimIndicator,
  );
  const followupCandidate = await assertHospitalRuntimeCandidateContainsFollowupTemplate(
    page,
    hospital.id ?? "",
    followupTemplate,
  );
  await selectRequiredPlatformRuntimeAssetsForClaimActivation(page);
  await deselectUnrelatedHospitalLocalCandidates(page, [
    claimIndicator.indicatorCode,
    followupTemplate.templateCode,
  ]);
  await selectHospitalLocalClaimIndicatorCandidate(page, claimIndicator, claimCandidate);
  await selectHospitalLocalFollowupTemplateCandidate(page, followupTemplate, followupCandidate);
  await assessLocalReleaseImpactIfRequired(page);

  const activateResponsePromise = waitForPostMatching(
    page,
    /\/api\/v1\/engine\/releases\/hospitals\/.+\/runtime-releases$/u,
  );
  await page.getByRole("button", { name: "生成新机构生效版本" }).click();
  const activateResponse = await activateResponsePromise;
  const activateText = await activateResponse.text();
  expect(
    activateResponse.ok(),
    `前台生成含 CLAIM 指标的机构生效版本应返回成功 status=${activateResponse.status()} body=${activateText}`,
  ).toBe(true);
  assertRuntimeReleaseRequestContainsClaimIndicator(
    activateResponse.request().postDataJSON(),
    claimIndicator,
    claimCandidate,
  );
  assertRuntimeReleaseRequestContainsFollowupTemplate(
    activateResponse.request().postDataJSON(),
    followupTemplate,
    followupCandidate,
  );
  assertRuntimeReleaseRequestCarriesRequiredBaselineAssets(
    activateResponse.request().postDataJSON(),
  );
  const activated = JSON.parse(activateText) as { data?: { revisionNo?: number } };
  expect(activated.data?.revisionNo, "机构生效版本响应应返回修订号").toBeGreaterThan(0);
  await assertCurrentRuntimeContainsClaimIndicator(page, hospital.id ?? "", claimIndicator);
  const followupRuntime = await assertCurrentRuntimeContainsFollowupTemplate(
    page,
    hospital.id ?? "",
    followupTemplate,
    followupCandidate,
  );
  await captureEvidence(page, testInfo, "real-frontdesk-runtime-claim-evaluation-active");
  recordCleanRuntime(page, "前台生成包含 CLAIM 评价指标的机构生效版本", runtime, records);
  return {
    operation: "ACTIVATE_HOSPITAL_RUNTIME_WITH_FOLLOWUP",
    candidateStatus: 200,
    activationStatus: activateResponse.status(),
    runtimeReadbackStatus: followupRuntime.status,
    runtimeReleaseId: followupRuntime.releaseId,
    assetType: followupCandidate.assetType,
    assetIdentity: followupCandidate.assetIdentity,
    versionId: followupCandidate.versionId,
    sourceLayer: followupCandidate.sourceLayer,
    entryState: followupRuntime.entryState,
    currentRuntimeContainsAsset: true,
  };
}

async function assertHospitalRuntimeCandidateContainsClaimIndicator(
  page: Page,
  hospitalId: string,
  claimIndicator: ClaimEvaluationIndicatorSummary,
): Promise<ClaimRuntimeCandidateSummary> {
  expect(hospitalId, "读取机构生效版本候选前必须解析本地上线演练医院 ID").toBeTruthy();
  const response = await page.request.get(
    `/medkernel/api/v1/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-candidates`,
    {
      params: {
        assetType: "EVALUATION",
        keyword: claimIndicator.indicatorCode,
        page: "1",
        size: "20",
      },
      headers: { "X-Trace-Id": `e2e-runtime-candidate-claim-${Date.now()}` },
    },
  );
  const text = await response.text();
  expect(
    response.ok(),
    `应能读取本轮 CLAIM 指标机构候选 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as {
    data?: {
      items?: Array<{
        assetType?: string;
        assetIdentity?: string;
        versionId?: string;
        versionNo?: string;
        sourceLayer?: string;
        status?: string;
      }>;
    };
  };
  const claimCandidate = (parsed.data?.items ?? []).find(
    (item) =>
      item.assetType === "EVALUATION" &&
      item.assetIdentity === claimIndicator.indicatorCode &&
      item.status === "PUBLISHED",
  );
  expect(
    claimCandidate,
    `机构生效版本候选 API 必须包含本轮 CLAIM 指标 ${claimIndicator.indicatorCode}`,
  ).toBeTruthy();
  expect(claimCandidate?.versionId, "本轮 CLAIM 指标候选必须返回可发布版本 ID").toBeTruthy();
  expect(claimCandidate?.sourceLayer, "本轮 CLAIM 指标必须作为本院内容进入机构版本").toBe(
    "HOSPITAL",
  );
  return {
    assetType: "EVALUATION",
    assetIdentity: claimCandidate?.assetIdentity ?? claimIndicator.indicatorCode,
    versionId: claimCandidate?.versionId ?? "",
    versionNo: claimCandidate?.versionNo,
    sourceLayer: claimCandidate?.sourceLayer,
    status: "PUBLISHED",
  };
}

async function assertHospitalRuntimeCandidateContainsFollowupTemplate(
  page: Page,
  hospitalId: string,
  followupTemplate: { templateCode: string },
): Promise<LocalRuntimeCandidateSummary> {
  expect(hospitalId, "读取机构生效版本候选前必须解析本地上线演练医院 ID").toBeTruthy();
  const response = await page.request.get(
    `/medkernel/api/v1/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-candidates`,
    {
      params: {
        assetType: "FOLLOWUP",
        keyword: followupTemplate.templateCode,
        page: "1",
        size: "20",
      },
      headers: { "X-Trace-Id": `e2e-runtime-candidate-followup-${Date.now()}` },
    },
  );
  const text = await response.text();
  expect(
    response.ok(),
    `应能读取本轮 FOLLOWUP 随访方案机构候选 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as {
    data?: {
      items?: Array<{
        assetType?: string;
        assetIdentity?: string;
        versionId?: string;
        versionNo?: string;
        sourceLayer?: string;
        status?: string;
      }>;
    };
  };
  const followupCandidate = (parsed.data?.items ?? []).find(
    (item) =>
      item.assetType === "FOLLOWUP" &&
      item.assetIdentity === followupTemplate.templateCode &&
      item.status === "PUBLISHED",
  );
  expect(
    followupCandidate,
    `机构生效版本候选 API 必须包含本轮 FOLLOWUP 随访方案 ${followupTemplate.templateCode}`,
  ).toBeTruthy();
  expect(followupCandidate?.versionId, "本轮 FOLLOWUP 候选必须返回可发布版本 ID").toBeTruthy();
  expect(followupCandidate?.sourceLayer, "本轮 FOLLOWUP 必须作为本院内容进入机构版本").toBe(
    "HOSPITAL",
  );
  return {
    assetType: "FOLLOWUP",
    assetIdentity: followupCandidate?.assetIdentity ?? followupTemplate.templateCode,
    versionId: followupCandidate?.versionId ?? "",
    versionNo: followupCandidate?.versionNo,
    sourceLayer: followupCandidate?.sourceLayer,
    status: followupCandidate?.status,
  };
}

async function selectHospitalLocalClaimIndicatorCandidate(
  page: Page,
  claimIndicator: ClaimEvaluationIndicatorSummary,
  claimCandidate: ClaimRuntimeCandidateSummary,
) {
  await enableEvidenceDetails(page);
  const localContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("集团与本院内容", { exact: true }) })
    .first();
  await expect(localContentCard, "机构生效版本页必须展示集团与本院内容清单").toBeVisible({
    timeout: 20_000,
  });
  const claimRow = localContentCard
    .getByRole("row")
    .filter({ hasText: claimIndicator.indicatorCode })
    .filter({ hasText: "本院 · 评价指标内容" })
    .first();
  await expect(
    claimRow,
    `集团与本院内容必须展示本轮 CLAIM 指标 ${claimIndicator.indicatorCode}`,
  ).toBeVisible({ timeout: 20_000 });
  const enableCheckbox = claimRow.getByRole("checkbox", { name: /启用本院评价指标内容/u });
  await expect(
    enableCheckbox,
    `本轮 CLAIM 指标 ${claimIndicator.indicatorCode} 必须可勾选进入机构生效版本`,
  ).toBeVisible();
  if (!(await enableCheckbox.isChecked())) {
    await enableCheckbox.check();
  }
  await expect(enableCheckbox, "本轮 CLAIM 指标必须已选入机构生效版本").toBeChecked();
  await expect(claimRow, "前台选择的 CLAIM 指标行必须对应候选版本").toContainText(
    claimCandidate.versionNo ?? claimCandidate.versionId,
  );
}

async function selectHospitalLocalFollowupTemplateCandidate(
  page: Page,
  followupTemplate: { templateCode: string },
  followupCandidate: LocalRuntimeCandidateSummary,
) {
  await enableEvidenceDetails(page);
  const localContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("集团与本院内容", { exact: true }) })
    .first();
  await expect(localContentCard, "机构生效版本页必须展示集团与本院内容清单").toBeVisible({
    timeout: 20_000,
  });
  const followupRow = localContentCard
    .getByRole("row")
    .filter({ hasText: followupTemplate.templateCode })
    .filter({ hasText: "本院 · 随访内容" })
    .first();
  await expect(
    followupRow,
    `集团与本院内容必须展示本轮 FOLLOWUP 随访方案 ${followupTemplate.templateCode}`,
  ).toBeVisible({ timeout: 20_000 });
  const enableCheckbox = followupRow.getByRole("checkbox", { name: /启用本院随访内容/u });
  await expect(
    enableCheckbox,
    `本轮 FOLLOWUP 随访方案 ${followupTemplate.templateCode} 必须可勾选进入机构生效版本`,
  ).toBeVisible();
  if (!(await enableCheckbox.isChecked())) {
    await enableCheckbox.check();
  }
  await expect(enableCheckbox, "本轮 FOLLOWUP 随访方案必须已选入机构生效版本").toBeChecked();
  await expect(followupRow, "前台选择的 FOLLOWUP 随访方案行必须对应候选版本").toContainText(
    followupCandidate.versionNo ?? followupCandidate.versionId,
  );
}

async function deselectUnrelatedHospitalLocalCandidates(
  page: Page,
  retainedAssetIdentities: string[],
) {
  await enableEvidenceDetails(page);
  const retained = new Set(retainedAssetIdentities);
  const localContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("集团与本院内容", { exact: true }) })
    .first();
  await expect(localContentCard, "机构生效版本页必须展示集团与本院内容清单").toBeVisible({
    timeout: 20_000,
  });
  const rows = localContentCard.getByRole("row");
  const rowCount = await rows.count();
  for (let index = 0; index < rowCount; index += 1) {
    const row = rows.nth(index);
    const checkbox = row.getByRole("checkbox", { name: /启用/ }).first();
    if ((await checkbox.count()) === 0) {
      continue;
    }
    if (await rowContainsAnyAssetIdentity(row, retained)) {
      continue;
    }
    if (await checkbox.isChecked()) {
      await checkbox.uncheck();
      await expect(checkbox, "非本轮本院候选不应被带入 CLAIM 机构生效版本").not.toBeChecked();
    }
  }
}

async function rowContainsAnyAssetIdentity(row: Locator, retained: Set<string>) {
  for (const assetIdentity of retained) {
    if (await row.getByText(assetIdentity, { exact: true }).isVisible()) {
      return true;
    }
  }
  return false;
}

async function selectRequiredPlatformRuntimeAssetsForClaimActivation(page: Page) {
  await enableEvidenceDetails(page);
  const platformContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("平台标准内容", { exact: true }) })
    .first();
  await expect(platformContentCard, "机构生效版本页必须展示平台标准内容清单").toBeVisible({
    timeout: 20_000,
  });
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const platformRow = platformContentCard
      .getByRole("row")
      .filter({ hasText: required.assetIdentity })
      .first();
    await expect(
      platformRow,
      `平台标准内容必须展示 ${required.assetType} ${required.assetIdentity}`,
    ).toBeVisible({ timeout: 20_000 });
    const enableCheckbox = platformRow.getByRole("checkbox", { name: /启用/ });
    await expect(
      enableCheckbox,
      `${required.assetType} ${required.assetIdentity} 必须可勾选进入 CLAIM 演练机构版本`,
    ).toBeVisible();
    if (!(await enableCheckbox.isChecked())) {
      await enableCheckbox.check();
    }
    await expect(
      enableCheckbox,
      `${required.assetType} ${required.assetIdentity} 必须保留在机构生效版本选择集中`,
    ).toBeChecked();
  }
}

function assertRuntimeReleaseRequestContainsClaimIndicator(
  value: unknown,
  claimIndicator: ClaimEvaluationIndicatorSummary,
  claimCandidate: ClaimRuntimeCandidateSummary,
) {
  const activeAssets = Array.isArray((value as { activeAssets?: unknown }).activeAssets)
    ? ((value as { activeAssets: RuntimeAssetSelection[] }).activeAssets ?? [])
    : [];
  const match = activeAssets.find(
    (item) =>
      item.assetType === "EVALUATION" && item.assetIdentity === claimIndicator.indicatorCode,
  );
  expect(
    match,
    `生成机构生效版本请求必须携带本轮 CLAIM 指标 ${claimIndicator.indicatorCode}`,
  ).toBeTruthy();
  expect(match?.versionId, `本轮 CLAIM 指标必须使用本院候选版本 ${claimCandidate.versionId}`).toBe(
    claimCandidate.versionId,
  );
}

function assertRuntimeReleaseRequestContainsFollowupTemplate(
  value: unknown,
  followupTemplate: { templateCode: string },
  followupCandidate: LocalRuntimeCandidateSummary,
) {
  const activeAssets = Array.isArray((value as { activeAssets?: unknown }).activeAssets)
    ? ((value as { activeAssets: RuntimeAssetSelection[] }).activeAssets ?? [])
    : [];
  const match = activeAssets.find(
    (item) => item.assetType === "FOLLOWUP" && item.assetIdentity === followupTemplate.templateCode,
  );
  expect(
    match,
    `生成机构生效版本请求必须携带本轮 FOLLOWUP 随访方案 ${followupTemplate.templateCode}`,
  ).toBeTruthy();
  expect(
    match?.versionId,
    `本轮 FOLLOWUP 随访方案必须使用本院候选版本 ${followupCandidate.versionId}`,
  ).toBe(followupCandidate.versionId);
}

function assertRuntimeReleaseRequestCarriesRequiredBaselineAssets(value: unknown) {
  const activeAssets = Array.isArray((value as { activeAssets?: unknown }).activeAssets)
    ? ((value as { activeAssets: RuntimeAssetSelection[] }).activeAssets ?? [])
    : [];
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const match = activeAssets.find(
      (item) =>
        item.assetType === required.assetType && item.assetIdentity === required.assetIdentity,
    );
    expect(
      match,
      `生成含 CLAIM 指标的机构生效版本请求必须保留 ${required.assetType} ${required.assetIdentity}`,
    ).toBeTruthy();
    expect(
      match?.versionId ?? null,
      `${required.assetType} ${required.assetIdentity} 必须沿用平台标准版本`,
    ).toBeNull();
  }
}

async function enableEvidenceDetails(page: Page) {
  const details = page.getByRole("switch", { name: "证据详情" });
  await expect(details.first(), "精确选择本轮 CLAIM 指标需要打开证据详情").toBeVisible({
    timeout: 20_000,
  });
  if (!(await details.first().isChecked())) {
    await details.first().click();
  }
}

async function chooseHospitalByName(
  page: Page,
  hospitalName: string,
  options: { openSelect?: boolean } = {},
): Promise<OrgUnitSummary> {
  const response = await page.request.get("/medkernel/api/v1/engine/org/org-units", {
    params: {
      keyword: hospitalName,
      level: "FACILITY",
      status: "ACTIVE",
      page: "1",
      size: "20",
    },
    headers: { "X-Trace-Id": `e2e-runtime-hospital-${Date.now()}` },
  });
  const body = await response.text();
  expect(response.ok(), `应能按名称读取演练医院 status=${response.status()} body=${body}`).toBe(
    true,
  );
  const parsed = JSON.parse(body) as { data?: { items?: OrgUnitSummary[] } };
  const hospital = (parsed.data?.items ?? []).find(
    (item) => item.name === hospitalName && item.level === "FACILITY" && item.id,
  );
  expect(hospital?.id, `应能解析演练医院 ${hospitalName} 的组织 ID`).toBeTruthy();

  if (options.openSelect !== false) {
    const combobox = page.getByRole("combobox", { name: "目标医院" });
    const select = combobox.locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
    );
    await openAntdSelect(select, "目标医院");
    await combobox.fill(hospitalName);
    const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
    await expect(dropdown).toBeVisible({ timeout: 10_000 });
    const option = dropdown
      .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
      .filter({ hasText: hospitalName })
      .first();
    await expect(option).toBeVisible({ timeout: 20_000 });
    await option.click();
  }

  return hospital ?? {};
}

async function assessLocalReleaseImpactIfRequired(page: Page) {
  const impactButton = page.getByRole("button", { name: "评估发布影响" });
  if ((await impactButton.count()) === 0 || !(await impactButton.first().isVisible())) {
    return;
  }
  const simulationResponsePromise = waitForPost(page, "/engine/versioning/releases/simulations");
  await impactButton.first().click();
  const simulationResponse = await simulationResponsePromise;
  const simulationBody = await simulationResponse.text();
  expect(
    simulationResponse.ok(),
    `发布影响评估应返回成功 status=${simulationResponse.status()} body=${simulationBody}`,
  ).toBe(true);
  const simulation = JSON.parse(simulationBody) as { data?: { releasable?: boolean } };
  expect(simulation.data?.releasable, "本轮 CLAIM 指标发布影响评估必须允许生成机构生效版本").toBe(
    true,
  );
  await page.waitForLoadState("networkidle");
  await expect(page.getByText("发布影响评估未完成")).toHaveCount(0);
  await expect(page.getByText("需处理")).toHaveCount(0);
}

async function assertCurrentRuntimeContainsClaimIndicator(
  page: Page,
  hospitalId: string,
  claimIndicator: ClaimEvaluationIndicatorSummary,
) {
  expect(hospitalId, "本地上线演练医院必须可解析组织 ID").toBeTruthy();
  const response = await page.request.get(
    `/medkernel/api/v1/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-releases/current`,
    { headers: { "X-Trace-Id": `e2e-runtime-claim-${Date.now()}` } },
  );
  const text = await response.text();
  expect(response.ok(), `应能读取当前机构生效版本 status=${response.status()} body=${text}`).toBe(
    true,
  );
  const current = JSON.parse(text) as RuntimeReleaseDetailPayload;
  assertCurrentRuntimeContainsRequiredBaselineAssets(current);
  expect(
    current.data?.items?.some(
      (item) =>
        item.assetType === "EVALUATION" &&
        item.assetIdentity === claimIndicator.indicatorCode &&
        item.entryState === "ACTIVE" &&
        Boolean(item.versionId),
    ),
    `当前机构生效版本必须启用本轮 CLAIM 指标 ${claimIndicator.indicatorCode}`,
  ).toBe(true);
}

async function assertCurrentRuntimeContainsFollowupTemplate(
  page: Page,
  hospitalId: string,
  followupTemplate: { templateCode: string },
  followupCandidate: LocalRuntimeCandidateSummary,
) {
  expect(hospitalId, "本地上线演练医院必须可解析组织 ID").toBeTruthy();
  const response = await page.request.get(
    `/medkernel/api/v1/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-releases/current`,
    { headers: { "X-Trace-Id": `e2e-runtime-followup-${Date.now()}` } },
  );
  const text = await response.text();
  expect(response.ok(), `应能读取当前机构生效版本 status=${response.status()} body=${text}`).toBe(
    true,
  );
  const current = JSON.parse(text) as RuntimeReleaseDetailPayload;
  const item = current.data?.items?.find(
    (candidate) =>
      candidate.assetType === "FOLLOWUP" &&
      candidate.assetIdentity === followupTemplate.templateCode &&
      candidate.entryState === "ACTIVE" &&
      candidate.versionId === followupCandidate.versionId,
  );
  expect(
    item,
    `当前机构生效版本必须启用本轮 FOLLOWUP 随访方案 ${followupTemplate.templateCode}`,
  ).toBeTruthy();
  return {
    entryState: item?.entryState,
    releaseId: current.data?.release?.releaseId,
    status: response.status(),
  };
}

function assertCurrentRuntimeContainsRequiredBaselineAssets(current: RuntimeReleaseDetailPayload) {
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const match = (current.data?.items ?? []).find(
      (item) =>
        item.assetType === required.assetType &&
        item.assetIdentity === required.assetIdentity &&
        item.entryState === "ACTIVE" &&
        Boolean(item.versionId),
    );
    expect(
      match,
      `当前机构生效版本必须保留 ${required.assetType} ${required.assetIdentity}`,
    ).toBeTruthy();
  }
}

async function runInsuranceAuditFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  snapshot: ContextSnapshotSummary,
  claimIndicator: ClaimEvaluationIndicatorSummary,
): Promise<InsuranceAuditSummary> {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/qc/insurance", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "医保审核" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "医保审核桌面");

  await page.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await page.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  await selectInsuranceAuditSnapshotFromUi(page, snapshot);
  await expect(page.getByLabel("患者信息")).toHaveValue("已关联患者");
  if (snapshot.encounterId) {
    await expect(page.getByLabel("就诊信息")).toHaveValue("已关联就诊");
  }
  await choosePageSelectOption(page, "责任科室");
  await searchPageSelectOption(page, "评价指标", claimIndicator.indicatorCode, claimIndicator.name);
  await expect(
    page.getByText("未读取到已生效评价指标，将按本次医保规则依据归档并生成整改任务。"),
  ).toHaveCount(0);

  await page.getByLabel("审核场景").fill("A9");
  await page.getByLabel("整改截止时间").fill("2026年07月15日 08:30");
  await page.getByLabel("DRG 分组器版本").fill("GROUPER-2026");
  await page.getByLabel("期望入组").fill("DRG-REAL-A");
  await page.getByLabel("实际入组").fill("DRG-REAL-B");
  await page
    .getByLabel("入组说明")
    .fill("真实前台演练：基于当前病案快照和医保结算事实完成 DRG/DIP 入组复核。");
  await page.getByLabel("医保规则依据").fill("INS.REAL.FRONTDESK.FEE");
  await page.getByLabel("依据版本").fill("2026.07");
  await page.getByLabel("费用阈值").fill("1000");
  await page.getByLabel("规则说明").fill("结算金额超过当前演练阈值，需要责任科室提交整改证据。");

  const caseReviewPromise = waitForPost(page, "/api/v1/engine/quality/case-review");
  const drgPromise = waitForPost(page, "/api/v1/engine/quality/drg-grouping");
  const auditPromise = waitForPost(page, "/api/v1/engine/quality/insurance-audit");
  await page.getByRole("button", { name: "执行审核并派整改" }).click();
  const [caseReviewResponse, drgResponse, auditResponse] = await Promise.all([
    caseReviewPromise,
    drgPromise,
    auditPromise,
  ]);
  expect(caseReviewResponse.ok(), "前台执行病案内涵质控应返回成功").toBe(true);
  expect(drgResponse.ok(), "前台执行 DRG/DIP 分组应返回成功").toBe(true);
  const auditResponseText = await auditResponse.text();
  expect(
    auditResponse.ok(),
    `前台执行医保审核应返回成功 status=${auditResponse.status()} body=${auditResponseText}`,
  ).toBe(true);
  const audit = JSON.parse(auditResponseText) as {
    data?: {
      auditStatus?: string;
      evaluationRunId?: string | null;
      findingCount?: number;
      taskCount?: number;
      issues?: Array<{ issueId?: string; ruleCode?: string; evidenceSummary?: string }>;
    };
  };
  expect(audit.data?.auditStatus, "真实结算事实应触发医保问题").toBe("ISSUE_FOUND");
  expect(audit.data?.evaluationRunId, "CLAIM 评价指标驱动的医保审核必须返回评估运行").toBeTruthy();
  expect(audit.data?.findingCount ?? 0, "医保审核应联动质量问题").toBeGreaterThan(0);
  expect(audit.data?.taskCount ?? 0, "医保审核应派发整改任务").toBeGreaterThan(0);
  expect(audit.data?.issues?.length ?? 0, "医保审核应返回问题明细").toBeGreaterThan(0);
  await expect(
    page.getByText("医保审核已基于真实结算事实执行，命中问题已由服务联动整改闭环。"),
  ).toBeVisible({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-insurance-quality-rectification");
  recordCleanRuntime(page, "前台执行医保审核并联动质量整改", runtime, records);
  const issue = audit.data?.issues?.[0];
  expect(issue?.issueId, "医保审核问题应返回 issueId 以便前台追溯质量整改").toBeTruthy();
  return {
    issueId: issue?.issueId ?? "",
    evaluationRunId: audit.data?.evaluationRunId ?? "",
  };
}

async function assertInsuranceAuditUsesEvaluationRun(
  page: Page,
  audit: InsuranceAuditSummary,
  claimIndicator: ClaimEvaluationIndicatorSummary,
) {
  expect(audit.evaluationRunId, "医保审核必须绑定非手工评估运行").toBeTruthy();
  expect(audit.evaluationRunId, "医保审核不得落入 INSURANCE_RULE_MANUAL 手工归档路径").not.toBe(
    "INSURANCE_RULE_MANUAL",
  );
  const alert = await findInsuranceQualityAlert(page, audit.issueId, "OPEN");
  const detail = await qualityFindingDetail(page, alert.sourceId ?? "");
  expect(detail.data?.finding?.runId, "质量问题必须来自本次医保审核评估运行").toBe(
    audit.evaluationRunId,
  );
  expect(detail.data?.finding?.indicatorId, "质量问题必须绑定本轮 CLAIM 评价指标").toBe(
    claimIndicator.indicatorId,
  );
}

async function selectInsuranceAuditSnapshotFromUi(page: Page, snapshot: ContextSnapshotSummary) {
  const evidenceButton = page.getByRole("button", { name: `选择 ${snapshot.snapshotId}` });
  await expect(evidenceButton, `医保审核页必须展示本轮病案快照 ${snapshot.snapshotId}`).toBeVisible(
    { timeout: 20_000 },
  );
  await evidenceButton.click();
}

async function closeQualityRectificationFromAlertsUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  audit: InsuranceAuditSummary,
): Promise<QualityRectificationSummary> {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/qc/alerts", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "质量问题与整改" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "质量问题与整改桌面");

  const openAlert = await findInsuranceQualityAlert(page, audit.issueId, "OPEN");
  const findingId = openAlert.sourceId ?? "";
  expect(findingId, "本次医保审核问题应解析到质量问题 ID").toBeTruthy();
  const alertRow = qualityAlertRowBySourceId(page, findingId);
  await expect(alertRow, "质量问题提醒列表应展示本次医保审核生成的问题").toBeVisible({
    timeout: 20_000,
  });

  await alertRow.getByRole("button", { name: "查看处置证据" }).click();
  const drawer = qualityAlertDrawer(page);
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText(/整改任务 .* 已派发/)).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByRole("button", { name: "提交整改证据" })).toBeVisible({
    timeout: 20_000,
  });
  await drawer
    .getByLabel("整改说明")
    .fill("真实前台演练：责任科室已复核医保结算事实并补充病案费用说明，等待质控复核。");
  await drawer
    .getByRole("textbox", { name: /整改证据/ })
    .fill(`INS-AUDIT-${audit.issueId}-RECTIFIED`);

  const submitPromise = waitForPostMatching(page, /\/api\/v1\/engine\/rectifications\/.+\/submit/u);
  await drawer.getByRole("button", { name: "提交整改证据" }).click();
  const submitResponse = await submitPromise;
  const submitText = await submitResponse.text();
  expect(
    submitResponse.ok(),
    `质量整改提交应调用任务级真实接口 status=${submitResponse.status()} body=${submitText}`,
  ).toBe(true);
  const submit = JSON.parse(submitText) as { data?: { taskId?: string; taskStatus?: string } };
  const taskId = submit.data?.taskId ?? "";
  expect(taskId, "整改提交响应应返回任务 ID").toBeTruthy();
  expect(submit.data?.taskStatus, "整改提交后任务应进入待复核").toBe("SUBMITTED");
  expect(
    await taskStatusForFinding(page, findingId),
    "本次医保审核质量问题关联的整改任务应进入待复核",
  ).toEqual({ taskId, status: "SUBMITTED" });
  await expect(drawer.getByText("整改证据已提交，等待质控复核后闭环。")).toBeVisible({
    timeout: 20_000,
  });

  await page.reload({ waitUntil: "networkidle" });
  await page.goto("/qc/alerts", { waitUntil: "networkidle" });
  const submittedRow = qualityAlertRowBySourceId(page, findingId);
  await expect(submittedRow, "整改提交后质量问题仍应可由提醒入口复核").toBeVisible({
    timeout: 20_000,
  });
  await submittedRow.getByRole("button", { name: "查看处置证据" }).click();
  const reviewDrawer = qualityAlertDrawer(page);
  await expect(reviewDrawer.getByText(/整改任务 .* 待复核/)).toBeVisible({ timeout: 20_000 });
  await reviewDrawer
    .getByLabel("复核意见")
    .fill("真实前台演练：整改证据充分，医保结算问题已完成质控复核，允许关闭。");
  await reviewDrawer.getByRole("textbox", { name: /复核证据/ }).fill(`QC-REVIEW-${audit.issueId}`);

  const reviewPromise = waitForPostMatching(page, /\/api\/v1\/engine\/rectifications\/.+\/review/u);
  await reviewDrawer.getByRole("button", { name: "复核通过并关闭" }).click();
  const reviewResponse = await reviewPromise;
  const reviewText = await reviewResponse.text();
  expect(
    reviewResponse.ok(),
    `质量整改复核应调用任务级真实接口 status=${reviewResponse.status()} body=${reviewText}`,
  ).toBe(true);
  const review = JSON.parse(reviewText) as {
    data?: { reviewId?: string; findingStatus?: string; taskStatus?: string };
  };
  expect(review.data?.reviewId, "整改复核响应应返回复核记录").toBeTruthy();
  expect(review.data?.findingStatus, "复核通过后问题应关闭").toBe("CLOSED");
  expect(review.data?.taskStatus, "复核通过后任务应关闭").toBe("CLOSED");
  await expect(reviewDrawer.getByText("整改已复核通过，质量问题已闭环。")).toBeVisible({
    timeout: 20_000,
  });
  await captureEvidence(page, testInfo, "real-frontdesk-qc-alerts-rectification-reviewed");

  await reviewDrawer.getByLabel(/Close|关闭/u).click();
  await expect(reviewDrawer).toBeHidden({ timeout: 20_000 });
  await choosePageSelectOptionByText(page, "处置状态", "已闭环");
  const closedResponse = await page.request.get("/medkernel/api/v1/engine/quality/alerts", {
    params: { status: "RESOLVED", severity: "ALL", page: "1", size: "20" },
    headers: { "X-Trace-Id": `e2e-qc-closed-alerts-${Date.now()}` },
  });
  const closedText = await closedResponse.text();
  expect(
    closedResponse.ok(),
    `质量提醒闭环回看接口应返回成功 status=${closedResponse.status()} body=${closedText}`,
  ).toBe(true);
  const closedAlerts = JSON.parse(closedText) as QualityAlertsPayload;
  expect(
    closedAlerts.data?.items?.some(
      (item) => item.sourceType === "quality_finding" && item.sourceId === findingId,
    ),
    "已闭环提醒接口应包含本次医保审核质量问题",
  ).toBe(true);
  await expect(qualityAlertRowBySourceId(page, findingId)).toBeVisible({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-qc-alerts-rectification-closed");
  recordCleanRuntime(page, "前台提交并复核关闭质量整改任务", runtime, records);
  return {
    findingId,
    taskId,
    taskStatus: review.data?.taskStatus ?? "",
  };
}

async function runCdssRecommendationFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  snapshot: ContextSnapshotSummary,
) {
  await ensureReadySession(page, "clinical-user");
  clearRuntime(runtime);
  await page.goto("/cdss/fatigue", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "提醒与推荐" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "提醒与推荐桌面");

  await page.getByRole("button", { name: "登记触发评估" }).click();
  const dialog = page.getByRole("dialog", { name: "登记一次推荐触发评估" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  const snapshotButton = dialog.getByRole("button", { name: `选择 ${snapshot.snapshotId}` });
  await expect(snapshotButton, `提醒推荐页必须展示本轮临床快照 ${snapshot.snapshotId}`).toBeVisible(
    {
      timeout: 20_000,
    },
  );
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "触发时点", "查看患者");

  const responsePromise = waitForPost(page, "/api/v1/engine/recommendations:evaluate");
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const response = await responsePromise;
  const responseText = await response.text();
  expect(
    response.ok(),
    `医生从前台触发 CDSS 推荐评估应返回成功 status=${response.status()} body=${responseText}`,
  ).toBe(true);
  const result = JSON.parse(responseText) as { data?: RecommendationEvaluationPayload };
  const cardId = assertRuntimeRecommendationEvidence(
    result.data,
    "医生从前台已生效快照触发 CDSS 推荐评估",
  );
  const detailResponse = await page.request.get(
    `/medkernel/api/v1/engine/recommendations/cards/${encodeURIComponent(cardId)}`,
    { headers: { "X-Trace-Id": `e2e-card-detail-${Date.now()}` } },
  );
  const detailText = await detailResponse.text();
  expect(
    detailResponse.ok(),
    `推荐卡详情应可由真实服务读取 status=${detailResponse.status()} body=${detailText}`,
  ).toBe(true);
  const detail = JSON.parse(detailText) as { data?: RecommendationCardDetailPayload };
  assertPersistedRuntimeRecommendationEvidence(
    detail.data,
    "医生从前台已生效快照触发 CDSS 推荐评估",
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await page.getByLabel("患者或证据线索").fill(cardId);
  const recommendationRow = page
    .getByRole("row", {
      name: /本地上线演练.*基础规则.*待处理.*查看与人机反馈/u,
    })
    .first();
  await expect(recommendationRow, "前台列表应展示本次真实规则命中的推荐卡").toBeVisible({
    timeout: 20_000,
  });
  await recommendationRow.getByRole("button", { name: "查看与人机反馈" }).click();
  await expect(page.getByRole("dialog", { name: "推荐详情与反馈闭环" })).toBeVisible({
    timeout: 20_000,
  });
  await expect(page.getByText("这条推荐是怎么来的")).toBeVisible();
  await captureEvidence(page, testInfo, "real-frontdesk-cdss-recommendation");
  recordCleanRuntime(page, "医生从前台已生效快照触发 CDSS 推荐评估", runtime, records);
}

function assertRuntimeRecommendationEvidence(
  evaluation: RecommendationEvaluationPayload | undefined,
  label: string,
) {
  expect(evaluation?.status, `${label} 推荐触发状态应为已评估`).toBe("EVALUATED");
  expect(evaluation?.triggerId, `${label} 响应应返回推荐触发编号`).toBeTruthy();
  expect(evaluation?.traceId, `${label} 响应应返回追踪号`).toBeTruthy();
  expect(evaluation?.visibleCardCount ?? 0, `${label} 应新增至少 1 张可见推荐卡`).toBeGreaterThan(
    0,
  );
  expect(evaluation?.suppressedCardCount ?? 0, `${label} 不应被疲劳策略抑制`).toBe(0);
  const card = evaluation?.cards?.[0];
  expect(card?.cardId, `${label} 响应应返回推荐卡编号`).toBeTruthy();
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含运行版本`).toContain("运行版本=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含资产版本`).toContain("asset_version=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含来源层`).toContain("来源层=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含内容哈希`).toContain("content_hash=");
  const explanation = parseRecommendationExplanation(card?.explanationJson, label);
  expect(
    explanation.runtimeRelease?.runtimeReleaseId,
    `${label} 解释应记录机构生效版本`,
  ).toBeTruthy();
  expect(
    explanation.runtimeRelease?.assetVersionId,
    `${label} 解释应记录机构生效资产版本`,
  ).toBeTruthy();
  expect(explanation.runtimeRelease?.sourceLayer, `${label} 解释应记录资产来源层`).toBeTruthy();
  expect(explanation.runtimeRelease?.contentHash, `${label} 解释应记录内容哈希`).toBeTruthy();
  return card?.cardId ?? "";
}

function assertPersistedRuntimeRecommendationEvidence(
  detail: RecommendationCardDetailPayload | undefined,
  label: string,
) {
  expect(detail?.card?.cardId, `${label} 详情应返回已落库推荐卡`).toBeTruthy();
  expect(detail?.trigger?.runtimeReleaseId, `${label} 详情触发记录应关联机构生效版本`).toBeTruthy();
  assertRecommendationSourceSummary(detail?.card, label);
  parseRecommendationExplanation(detail?.card?.explanationJson, label);
}

function assertRecommendationSourceSummary(
  card: RecommendationEvaluationCard | undefined,
  label: string,
) {
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含运行版本`).toContain("运行版本=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含资产版本`).toContain("asset_version=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含来源层`).toContain("来源层=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含内容哈希`).toContain("content_hash=");
}

function parseRecommendationExplanation(value: string | undefined, label: string) {
  expect(value, `${label} 响应应返回推荐解释 JSON`).toBeTruthy();
  const parsed = JSON.parse(value ?? "{}") as {
    runtimeRelease?: {
      runtimeReleaseId?: string;
      assetVersionId?: string;
      sourceLayer?: string;
      contentHash?: string;
    };
  };
  return parsed;
}

async function createFollowupTemplateFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  suffix: string,
) {
  await ensureReadySession(page, "clinical-user");
  clearRuntime(runtime);
  await page.goto("/clinical/followup", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "随访协同" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "随访方案" }).click();
  await expectNoRootOverflow(page, "随访协同方案桌面");

  const templateCode = `FUP.REAL.FRONTDESK.${suffix.toUpperCase()}`;
  const templateDefaultName = "真实前台慢病随访方案";
  const templateDisplayName = `${templateDefaultName}（${businessRehearsalBatchLabel(suffix)}）`;
  const templateName = `${templateDisplayName} ${suffix}`;
  await expect(page.getByRole("button", { name: /新建方案/ })).toBeEnabled();
  await page.getByRole("button", { name: /新建方案/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建随访方案" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("院内随访方案身份").fill(templateCode);
  await dialog.getByLabel("方案名称").fill(templateName);
  await dialog
    .getByLabel("方案说明")
    .fill("真实前台演练创建；不包含患者姓名、证件号、电话、住址等核心敏感信息。");
  await chooseDialogOption(page, dialog, "适用机构范围", "当前医院");
  await chooseDialogOption(page, dialog, "随访病种", "慢阻肺");
  await chooseDialogOption(page, dialog, "问卷内容", "慢病随访问卷");
  await chooseDialogOption(page, dialog, "核心随访问题", "呼吸困难变化");
  await dialog.getByLabel("异常触发条件").fill("呼吸困难加重、血氧下降或患者主动报告异常");
  await dialog.getByLabel("通知对象").fill("责任医生与随访护士");
  await chooseDialogOption(page, dialog, "院内依据", "慢病随访管理制度");

  const responsePromise = waitForPost(page, "/api/v1/engine/followup/templates");
  await dialog.getByRole("button", { name: /创\s*建/ }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交随访方案应返回成功").toBe(true);
  const result = (await response.json()) as {
    data?: { templateId?: string; templateCode?: string; name?: string };
  };
  expect(result.data?.templateId, "随访方案创建响应应返回稳定方案身份").toBeTruthy();
  expect(result.data?.templateCode).toBe(templateCode);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-followup-template");
  recordCleanRuntime(page, "前台创建随访方案", runtime, records);
  return {
    templateId: result.data?.templateId ?? "",
    templateCode,
    name: result.data?.name ?? templateName,
    displayName: templateDisplayName,
    defaultName: templateDefaultName,
    createStatus: response.status(),
    organizationScope: "HOSPITAL",
  };
}

async function publishFollowupTemplateFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  template: {
    templateId: string;
    templateCode: string;
    name: string;
    displayName: string;
    defaultName: string;
  },
): Promise<{ publishStatus: number; assetStatus?: string }> {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/clinical/followup", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "随访协同" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await page.getByRole("tab", { name: "随访方案" }).click();
  await page.getByPlaceholder("按方案名称或适用范围检索").fill(template.defaultName);

  const row = page
    .getByRole("row", { name: new RegExp(escapeRegExp(template.defaultName)) })
    .filter({ hasText: "待发布" })
    .first();
  await expect(row).toBeVisible();
  const responsePromise = waitForPost(
    page,
    `/api/v1/engine/followup/templates/${template.templateId}/publish`,
  );
  await row.getByRole("button", { name: "发布方案" }).click();
  const response = await responsePromise;
  const responseText = await response.text();
  expect(
    response.ok(),
    `前台发布随访方案应返回成功 status=${response.status()} body=${responseText}`,
  ).toBe(true);
  const result = JSON.parse(responseText) as {
    data?: { assetStatus?: string; templateId?: string };
  };
  expect(result.data?.templateId).toBe(template.templateId);
  expect(result.data?.assetStatus).toBe("PUBLISHED");
  const publishedRow = page
    .getByRole("row", { name: new RegExp(escapeRegExp(template.defaultName)) })
    .filter({ hasText: "可用于计划生成" })
    .first();
  await expect(publishedRow).toBeVisible({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-followup-template-published");
  recordCleanRuntime(page, "前台发布随访方案", runtime, records);
  return { publishStatus: response.status(), assetStatus: result.data?.assetStatus };
}

async function generateFollowupPlanAndHandlePatientFeedbackFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  template: {
    templateId: string;
    templateCode: string;
    name: string;
    displayName: string;
    defaultName: string;
  },
  snapshot: ContextSnapshotSummary,
): Promise<
  Pick<
    FollowupS12Evidence,
    "plan" | "questionnaire" | "abnormalReturn" | "resultBackflow" | "backflowContext"
  >
> {
  await ensureReadySession(page, "clinical-user");
  clearRuntime(runtime);
  await page.goto("/clinical/followup", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "随访协同" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "随访协同计划办理桌面");

  await page.getByRole("button", { name: "生成随访计划" }).click();
  const dialog = page.getByRole("dialog", { name: "生成随访计划" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("随访快照患者信息").fill(snapshot.patientId);
  const snapshotButton = dialog.locator(`button[data-snapshot-id="${snapshot.snapshotId}"]`);
  await expect(snapshotButton, `随访计划弹窗必须展示本轮上下文 ${snapshot.snapshotId}`).toBeVisible(
    { timeout: 20_000 },
  );
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "随访风险分层", "中风险");
  await searchDialogOption(page, dialog, "随访方案", template.defaultName, template.defaultName);

  const responsePromise = waitForPost(page, "/api/v1/engine/followup/plans/generate");
  await dialog.getByRole("button", { name: /生\s*成/ }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台生成随访计划应返回成功").toBe(true);
  const result = (await response.json()) as {
    data?: {
      planId?: string;
      patientId?: string;
      encounterId?: string;
      templateId?: string | null;
      templateCode?: string | null;
      runtimeReleaseId?: string;
      riskLevel?: string;
      tasks?: Array<{ taskId?: string; questionnaireTemplateId?: string }>;
    };
  };
  expect(result.data?.planId, "随访计划生成响应应返回计划身份").toBeTruthy();
  expect(result.data?.tasks?.length ?? 0, "随访计划应生成可办理任务").toBeGreaterThan(0);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByRole("dialog", { name: "随访计划办理" })).toBeVisible({
    timeout: 20_000,
  });
  await expect(
    page.getByRole("dialog", { name: "随访计划办理" }).getByText(template.defaultName).first(),
  ).toBeVisible({ timeout: 20_000 });
  await expect(
    page.getByRole("dialog", { name: "随访计划办理" }).getByText(/上线复演/),
  ).toHaveCount(0);
  await expect(
    page.getByRole("dialog", { name: "随访计划办理" }).getByText(template.name),
  ).toHaveCount(0);

  await page
    .getByRole("button", { name: /填\s*报/ })
    .first()
    .click();
  await chooseDialogOption(
    page,
    page.getByRole("dialog", { name: "随访计划办理" }),
    "提交来源",
    "患者自填",
  );
  await page
    .getByLabel("问卷回收内容")
    .fill("真实前台演练：患者自述夜间咳嗽加重，已按医嘱用药，未填写姓名、电话、住址或证件号。");
  const questionnaireResponsePromise = waitForPost(page, "/api/v1/engine/followup/questionnaires");
  await page.getByRole("button", { name: "提交问卷" }).click();
  const questionnaireResponse = await questionnaireResponsePromise;
  expect(questionnaireResponse.ok(), "前台提交随访问卷应返回成功").toBe(true);
  const questionnaire = (await questionnaireResponse.json()) as {
    data?: {
      questionnaireId?: string;
      taskId?: string;
      status?: string;
      traceId?: string;
    };
  };
  expect(questionnaire.data?.questionnaireId, "随访问卷响应应返回问卷记录").toBeTruthy();
  await expect(page.getByText("请选择一个待办随访任务后提交问卷回收内容")).toBeVisible({
    timeout: 20_000,
  });

  await chooseDialogOption(
    page,
    page.getByRole("dialog", { name: "随访计划办理" }),
    "回院风险等级",
    "高风险",
  );
  await page.getByLabel("异常症状或情况").fill("患者报告胸闷加重并伴活动后气促，需要回院复核。");
  await page
    .getByLabel("医护处理建议")
    .fill("护士已提示尽快回院，由责任医生复核后决定线下处置，不在本页自动开嘱。");
  const abnormalResponsePromise = waitForPost(page, "/api/v1/engine/followup/abnormal-reports");
  await page.getByRole("button", { name: "登记异常回院" }).click();
  const abnormalResponse = await abnormalResponsePromise;
  expect(abnormalResponse.ok(), "前台登记异常回院应返回成功").toBe(true);
  const abnormal = (await abnormalResponse.json()) as {
    data?: {
      eventId?: string;
      returnTaskId?: string;
      notificationEventId?: string;
      traceId?: string;
    };
  };
  expect(abnormal.data?.eventId, "异常回院响应应返回事件身份").toBeTruthy();
  await expect(page.getByText("异常回院证据已登记")).toBeVisible({ timeout: 20_000 });

  const backflowResponsePromise = waitForPost(page, "/api/v1/engine/followup/results");
  await page.getByRole("button", { name: "回流随访结果" }).click();
  const backflowResponse = await backflowResponsePromise;
  const backflowText = await backflowResponse.text();
  expect(
    backflowResponse.ok(),
    `前台回流随访结果应返回成功 status=${backflowResponse.status()} body=${backflowText}`,
  ).toBe(true);
  const backflow = JSON.parse(backflowText) as {
    data?: {
      eventId?: string;
      contextSnapshotId?: string;
    };
  };
  expect(backflow.data?.eventId, "随访结果回流响应应返回事件身份").toBeTruthy();
  expect(backflow.data?.contextSnapshotId, "随访结果回流响应应返回上下文身份").toBeTruthy();
  await expect(page.getByText("随访结果回流已完成")).toBeVisible({ timeout: 20_000 });

  const backflowContext = await readFollowupBackflowContext(page, {
    contextSnapshotId: backflow.data?.contextSnapshotId ?? "",
    questionnaireId: questionnaire.data?.questionnaireId ?? "",
    runtimeReleaseId: result.data?.runtimeReleaseId ?? null,
  });
  await captureEvidence(page, testInfo, "real-frontdesk-followup-plan-questionnaire-abnormal");
  recordCleanRuntime(page, "前台生成随访计划并完成问卷与异常回院登记", runtime, records);
  return {
    plan: {
      operation: "GENERATE_FOLLOWUP_PLAN_FROM_CONTEXT",
      status: response.status(),
      planId: result.data?.planId ?? "",
      templateId: result.data?.templateId ?? template.templateId,
      templateCode: result.data?.templateCode ?? template.templateCode,
      runtimeReleaseId: result.data?.runtimeReleaseId ?? null,
      patientId: result.data?.patientId ?? snapshot.patientId,
      encounterId: result.data?.encounterId ?? snapshot.encounterId,
      contextSnapshotId: snapshot.snapshotId,
      taskCount: result.data?.tasks?.length ?? 0,
      riskLevel: result.data?.riskLevel ?? "MEDIUM",
    },
    questionnaire: {
      operation: "SUBMIT_FOLLOWUP_QUESTIONNAIRE",
      status: questionnaireResponse.status(),
      planId: result.data?.planId ?? "",
      patientId: result.data?.patientId ?? snapshot.patientId,
      taskId: questionnaire.data?.taskId ?? result.data?.tasks?.[0]?.taskId ?? null,
      questionnaireId: questionnaire.data?.questionnaireId ?? null,
      responseStatus: questionnaire.data?.status ?? null,
      source: "PATIENT_SELF_REPORT",
      submitted: true,
    },
    abnormalReturn: {
      operation: "REGISTER_ABNORMAL_RETURN",
      status: abnormalResponse.status(),
      planId: result.data?.planId ?? "",
      patientId: result.data?.patientId ?? snapshot.patientId,
      eventId: abnormal.data?.eventId ?? null,
      returnTaskId: abnormal.data?.returnTaskId ?? null,
      notificationEventId: abnormal.data?.notificationEventId ?? null,
      riskLevel: "HIGH",
      registered: true,
      noAutoOrder: true,
    },
    resultBackflow: {
      eventId: backflow.data?.eventId ?? null,
      contextSnapshotId: backflow.data?.contextSnapshotId ?? null,
      sourceQuestionnaireId: questionnaire.data?.questionnaireId ?? null,
      abnormalFlag: "Y",
    },
    backflowContext,
  };
}

async function readFollowupBackflowContext(
  page: Page,
  options: {
    contextSnapshotId: string;
    questionnaireId: string;
    runtimeReleaseId?: string | null;
  },
): Promise<FollowupBackflowContextEvidence> {
  expect(options.contextSnapshotId, "随访结果回流必须返回上下文 ID").toBeTruthy();
  expect(options.questionnaireId, "随访结果回流必须绑定真实问卷 ID").toBeTruthy();
  const response = await page.request.get(
    `/medkernel/api/v1/engine/context/snapshots/${encodeURIComponent(options.contextSnapshotId)}`,
    { headers: { "X-Trace-Id": `e2e-followup-backflow-context-${Date.now()}` } },
  );
  const text = await response.text();
  expect(response.ok(), `应能回读随访结果回流上下文 status=${response.status()} body=${text}`).toBe(
    true,
  );
  const result = JSON.parse(text) as {
    data?: {
      snapshotId?: string;
      patientId?: string;
      encounterId?: string | null;
      runtimeReleaseId?: string | null;
      resources?: Record<string, unknown>;
    };
  };
  expect(result.data?.snapshotId).toBe(options.contextSnapshotId);
  expect(result.data?.runtimeReleaseId, "随访结果回流上下文必须继承随访计划机构生效版本").toBe(
    options.runtimeReleaseId,
  );
  const followUps = Array.isArray(result.data?.resources?.followUps)
    ? result.data.resources.followUps
    : [];
  const followUpSummary = followUps.map((item) =>
    isRecord(item)
      ? {
          followUpId: item.followUpId,
          questionnaireId: item.questionnaireId,
          sourceRecordId: item.sourceRecordId,
          abnormalFlag: item.abnormalFlag,
          sourceSystem: item.sourceSystem,
          mappedVersion: item.mappedVersion,
          qualityStatus: item.qualityStatus,
        }
      : item,
  );
  expect(
    followUps.some(
      (item) =>
        isRecord(item) &&
        item.followUpId === options.questionnaireId &&
        typeof item.questionnaireId === "string" &&
        item.questionnaireId.length > 0 &&
        typeof item.sourceRecordId === "string" &&
        item.sourceRecordId.length > 0 &&
        item.abnormalFlag === "Y" &&
        item.sourceSystem === "FOLLOWUP" &&
        item.mappedVersion === "FOLLOWUP_RESULT" &&
        item.qualityStatus === "VALID",
    ),
    `回流上下文必须回读同一问卷生成的 FollowUp 标准资源：${JSON.stringify(followUpSummary)}`,
  ).toBe(true);
  return {
    patientId: result.data?.patientId ?? null,
    encounterId: result.data?.encounterId ?? null,
    contextSnapshotId: result.data?.snapshotId ?? null,
    runtimeReleaseId: result.data?.runtimeReleaseId ?? null,
    resources: result.data?.resources ?? {},
  };
}

async function chooseDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  option?: string,
  fallbackOption?: string,
) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const expectedOptions = [option, fallbackOption].filter((value): value is string =>
    Boolean(value),
  );
  const selectedText = await currentSelectText(select);
  if (expectedOptions.includes(selectedText)) {
    return;
  }
  await openAntdSelect(select, label);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const availableOptions = dropdown.locator(
    ".ant-select-item-option:not(.ant-select-item-option-disabled)",
  );
  const optionLocator =
    expectedOptions.length > 0
      ? availableOptions
          .filter({
            hasText: new RegExp(
              expectedOptions.map((value) => `^\\s*${escapeRegExp(value)}\\s*$`).join("|"),
            ),
          })
          .first()
      : availableOptions.first();
  await clickAntdOption(optionLocator, label, 5_000, expectedOptions[0]);
  if (expectedOptions.length > 0) {
    await expectSelectedAntdOption(select, label, expectedOptions);
  }
}

async function chooseDialogFirstOption(
  page: Page,
  dialog: Locator,
  label: string,
  expectedOption: string,
) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const selectedText = await currentSelectText(select);
  if (selectedText === expectedOption) {
    return;
  }
  await openAntdSelect(select, label);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const firstOption = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await expect(firstOption, `${label} 下拉首项必须是 ${expectedOption}`).toHaveText(
    new RegExp(`^\\s*${escapeRegExp(expectedOption)}\\s*$`),
    { timeout: 5_000 },
  );
  await firstOption.evaluate((element) => {
    (element as HTMLElement).click();
  });
  await expectSelectedAntdOption(select, label, [expectedOption], 5_000);
}

async function searchDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  searchText: string,
  optionText: string,
) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await openAntdSelect(select, label);
  if (searchText && (await combobox.getAttribute("readonly")) === null) {
    await combobox.fill(searchText);
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({
      hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}(?:\\s*·.*|\\s*（.*）)?\\s*$`),
    })
    .first();
  await clickAntdOption(optionLocator, label, 20_000, optionText);
  await expectSelectedAntdOption(select, label, [optionText], 20_000);
}

async function choosePageSelectOption(page: Page, label: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await openAntdSelect(select, label, 10_000);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await clickAntdOption(optionLocator, label, 20_000);
}

async function choosePageSelectOptionByText(page: Page, label: string, option: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await openAntdSelect(select, label, 10_000);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`) })
    .first();
  await clickAntdOption(optionLocator, label, 10_000, option);
  await expectSelectedAntdOption(select, label, [option], 10_000);
}

async function searchPageSelectOption(
  page: Page,
  label: string,
  searchText: string,
  optionText: string,
) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await openAntdSelect(select, label, 10_000);
  await combobox.fill(searchText);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({
      hasText: new RegExp(`${escapeRegExp(optionText)}|${escapeRegExp(searchText)}`),
    })
    .first();
  await clickAntdOption(optionLocator, label, 20_000, optionText);
  await expectSelectedAntdOption(select, label, [optionText, searchText], 20_000);
}

async function chooseIndexedSelectOption(page: Page, combobox: Locator, option: string) {
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await openAntdSelect(select, option, 10_000);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`) })
    .first();
  await clickAntdOption(optionLocator, option, 10_000, option);
  await expectSelectedAntdOption(select, option, [option], 10_000);
}

async function clickAntdOption(
  optionLocator: Locator,
  label: string,
  timeout = 5_000,
  optionText?: string,
) {
  const visibleOption = await optionLocator
    .waitFor({ state: "visible", timeout: optionText ? Math.min(timeout, 1_000) : timeout })
    .then(() => true)
    .catch(() => false);
  if (!visibleOption) {
    if (!optionText) {
      await expect(optionLocator, `${label} 下拉选项应可见`).toBeVisible({ timeout });
    } else {
      const selectedByText = await dispatchAntdOptionByText(
        optionLocator.page(),
        optionText,
        timeout,
      );
      expect(selectedByText, `应能在当前 AntD 下拉中选择 ${optionText}`).toBe(true);
    }
  } else {
    await optionLocator.scrollIntoViewIfNeeded({ timeout }).catch(() => undefined);
    await optionLocator.click({ timeout: Math.min(timeout, 1_500) }).catch(async (error) => {
      const clickedVisibleOption = await optionLocator
        .evaluate((element) => {
          (element as HTMLElement).click();
          return true;
        })
        .catch(() => false);
      if (clickedVisibleOption) {
        return;
      }
      if (!optionText) {
        throw error;
      }
      const selectedByText = await dispatchAntdOptionByText(
        optionLocator.page(),
        optionText,
        timeout,
      );
      expect(selectedByText, `应能在当前 AntD 下拉中选择 ${optionText}`).toBe(true);
    });
  }
  await expect(
    optionLocator.page().locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)"),
  )
    .toHaveCount(0, { timeout: 5_000 })
    .catch(() => undefined);
}

async function dispatchAntdOptionByText(page: Page, optionText: string, timeout = 2_000) {
  const clickedVisibleOption = await clickVisibleAntdOptionByText(page, optionText);
  if (clickedVisibleOption) {
    return true;
  }
  const clickedByWheel = await wheelAntdVirtualDropdownToOption(page, optionText, timeout);
  if (clickedByWheel) {
    return true;
  }
  return selectOpenAntdOptionByKeyboard(page, optionText);
}

async function clickVisibleAntdOptionByText(page: Page, optionText: string) {
  const option = page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)")
    .last()
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`) })
    .first();
  if (!(await option.isVisible().catch(() => false))) {
    return false;
  }
  await option.scrollIntoViewIfNeeded().catch(() => undefined);
  await option.click({ timeout: 1_500 }).catch(async () => {
    const box = await option.boundingBox();
    if (!box) {
      throw new Error(`AntD 下拉可见选项 ${optionText} 缺少可点击位置`);
    }
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
  });
  return true;
}

async function wheelAntdVirtualDropdownToOption(page: Page, optionText: string, timeout: number) {
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  const holder = dropdown.locator(".rc-virtual-list-holder").first();
  if ((await holder.count()) === 0) {
    return false;
  }
  const holderBox = await holder.boundingBox();
  if (!holderBox) {
    return false;
  }
  const scanPositions = [
    { deltaY: -holderBox.height * 8, attempts: 8 },
    { deltaY: holderBox.height * 0.85, attempts: 24 },
  ];
  const deadline = Date.now() + timeout;
  await page.mouse.move(holderBox.x + holderBox.width / 2, holderBox.y + holderBox.height / 2);
  for (const scan of scanPositions) {
    for (let attempt = 0; attempt < scan.attempts && Date.now() < deadline; attempt += 1) {
      if (await clickVisibleAntdOptionByText(page, optionText)) {
        return true;
      }
      await page.mouse.wheel(0, scan.deltaY);
      await waitForAnimationFrame(page);
      await waitForAnimationFrame(page);
    }
  }
  return clickVisibleAntdOptionByText(page, optionText);
}

async function selectOpenAntdOptionByKeyboard(page: Page, optionText: string) {
  const option = page.getByRole("option", { name: optionText }).first();
  if (!(await option.isVisible({ timeout: 500 }).catch(() => false))) {
    return false;
  }
  await option.click({ timeout: 1_500 }).catch(async () => {
    await page.keyboard.press("Home");
    await waitForAnimationFrame(page);
    for (let attempt = 0; attempt < 24; attempt += 1) {
      if (await clickVisibleAntdOptionByText(page, optionText)) {
        return;
      }
      await page.keyboard.press("ArrowDown");
      await waitForAnimationFrame(page);
    }
    throw new Error(`键盘扫描未能选择 AntD 下拉选项 ${optionText}`);
  });
  return true;
}

async function waitForAnimationFrame(page: Page) {
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => resolve());
      }),
  );
}

async function openAntdSelect(select: Locator, label: string, timeout = 5_000) {
  const selector = select.locator(".ant-select-selector");
  await selector.scrollIntoViewIfNeeded({ timeout });
  await selector.evaluate((element) => {
    element.scrollIntoView({ block: "center", inline: "nearest" });
  });
  await expect(selector, `${label} 下拉触发器应可见`).toBeVisible({ timeout });
  await selector.click({ timeout });
}

async function expectSelectedAntdOption(
  select: Locator,
  label: string,
  expectedOptions: string[],
  timeout = 5_000,
) {
  const expectedPattern = new RegExp(
    expectedOptions
      .filter(Boolean)
      .map((value) => `^\\s*${escapeRegExp(value)}(?:\\s*·.*|\\s*（.*）)?\\s*$`)
      .join("|"),
  );
  await expect
    .poll(() => currentSelectText(select), {
      message: `${label} 下拉应选中 ${expectedOptions.join(" 或 ")}`,
      timeout,
    })
    .toMatch(expectedPattern);
}

async function currentSelectText(select: Locator) {
  const selected = select.locator(".ant-select-selection-item").first();
  if ((await selected.count()) === 0) {
    return "";
  }
  const title = await selected.getAttribute("title", { timeout: 1_000 }).catch(() => null);
  if (title) {
    return title.trim();
  }
  const text = await selected.textContent({ timeout: 1_000 }).catch(() => null);
  return text?.trim() ?? "";
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function businessRehearsalBatchLabel(suffix: string) {
  const rawTimestamp = suffix.split("-").at(-1) ?? suffix;
  const timestamp = Number.parseInt(rawTimestamp, 36);
  if (!Number.isFinite(timestamp)) {
    return "上线复演本轮";
  }
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  })
    .formatToParts(new Date(timestamp))
    .reduce<Record<string, string>>((acc, part) => {
      if (part.type !== "literal") {
        acc[part.type] = part.value;
      }
      return acc;
    }, {});
  return `上线复演 ${parts.month}月${parts.day}日 ${parts.hour}时${parts.minute}分${parts.second}秒`;
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function waitForPostMatching(page: Page, pattern: RegExp) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && pattern.test(response.url()),
    { timeout: 30_000 },
  );
}

function qualityAlertDrawer(page: Page) {
  return page.getByRole("dialog").filter({ hasText: "质量风险处置证据" }).last();
}

function qualityAlertRowBySourceId(page: Page, sourceId: string) {
  return page.locator(`[data-source-id="${cssStringEscape(sourceId)}"]`).first();
}

async function findInsuranceQualityAlert(page: Page, issueId: string, status: "OPEN" | "RESOLVED") {
  const alertsResponse = await page.request.get("/medkernel/api/v1/engine/quality/alerts", {
    params: { status, severity: "ALL", page: "1", size: "50" },
    headers: { "X-Trace-Id": `e2e-qc-alerts-${status.toLowerCase()}-${Date.now()}` },
  });
  const alertsText = await alertsResponse.text();
  expect(
    alertsResponse.ok(),
    `质量提醒接口应返回成功 status=${alertsResponse.status()} body=${alertsText}`,
  ).toBe(true);
  const alerts = JSON.parse(alertsText) as QualityAlertsPayload;
  const candidates =
    alerts.data?.items?.filter((item) => item.sourceType === "quality_finding") ?? [];
  for (const candidate of candidates) {
    if (!candidate.sourceId) {
      continue;
    }
    const detail = await qualityFindingDetail(page, candidate.sourceId);
    if (detail.data?.finding?.findingCode === `INSURANCE.${issueId}`) {
      return candidate;
    }
  }
  throw new Error(
    `未找到本次医保审核 issueId=${issueId} 对应的 ${status} 质量提醒；候选=${candidates
      .map((item) => item.sourceId)
      .join(",")}`,
  );
}

async function qualityFindingDetail(page: Page, findingId: string) {
  const detailResponse = await page.request.get(
    `/medkernel/api/v1/engine/evaluation/issues/${encodeURIComponent(findingId)}`,
    { headers: { "X-Trace-Id": `e2e-qc-finding-${Date.now()}` } },
  );
  const detailText = await detailResponse.text();
  expect(
    detailResponse.ok(),
    `质量问题详情接口应返回成功 status=${detailResponse.status()} body=${detailText}`,
  ).toBe(true);
  return JSON.parse(detailText) as QualityFindingDetailPayload;
}

async function taskStatusForFinding(page: Page, findingId: string) {
  const detail = await qualityFindingDetail(page, findingId);
  return {
    taskId: detail.data?.rectificationTask?.taskId,
    status: detail.data?.rectificationTask?.status,
  };
}

function waitForPut(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "PUT" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function cssStringEscape(value: string) {
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}

function collectRuntime(page: Page): RuntimeCollectors {
  return {
    browserErrors: collectBrowserErrors(page),
    serverErrors: collectServerErrors(page),
    networkFailures: collectNetworkFailures(page),
  };
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      errors.push(message.text());
    }
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

function collectServerErrors(page: Page) {
  const errors: string[] = [];
  page.on("response", (response) => {
    const url = response.url();
    if (response.status() >= 400 && (url.includes("/medkernel/") || url.includes("/api/v1/"))) {
      errors.push(`${response.status()} ${response.request().method()} ${url}`);
    }
  });
  return errors;
}

function collectNetworkFailures(page: Page) {
  const errors: string[] = [];
  page.on("requestfailed", (request) => {
    const failure = request.failure();
    const url = request.url();
    if (failure?.errorText === "net::ERR_ABORTED" || url.startsWith("data:")) {
      return;
    }
    errors.push(`${request.method()} ${url} ${failure?.errorText ?? "requestfailed"}`);
  });
  return errors;
}

function clearRuntime(runtime: RuntimeCollectors) {
  runtime.browserErrors.length = 0;
  runtime.serverErrors.length = 0;
  runtime.networkFailures.length = 0;
}

function recordCleanRuntime(
  page: Page,
  stage: string,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
) {
  const record = {
    stage,
    url: page.url(),
    browserErrors: [...runtime.browserErrors],
    serverErrors: [...runtime.serverErrors],
    networkFailures: [...runtime.networkFailures],
  };
  records.push(record);
  expect(record.browserErrors, `${stage} 不应产生浏览器错误`).toEqual([]);
  expect(record.serverErrors, `${stage} 不应产生 HTTP 错误`).toEqual([]);
  expect(record.networkFailures, `${stage} 不应产生网络失败`).toEqual([]);
}

async function captureEvidence(page: Page, testInfo: TestInfo, name: string) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function attachRuntimeRecords(testInfo: TestInfo, records: RuntimeRecord[]) {
  const recordPath = testInfo.outputPath("real-frontdesk-runtime-records.json");
  await writeFile(`${recordPath}`, `${JSON.stringify(records, null, 2)}\n`, "utf8");
  await testInfo.attach("real-frontdesk-runtime-records", {
    path: recordPath,
    contentType: "application/json",
  });
}

async function attachScenarioEvidence(
  testInfo: TestInfo,
  records: RuntimeRecord[],
  resourceEvidence: {
    snapshot: ContextSnapshotSummary | null;
    insuranceAudit: InsuranceAuditSummary | null;
    qualityRectification: QualityRectificationSummary | null;
    followupS12: FollowupS12Evidence | null;
  },
) {
  const observedStageSet = new Set(records.map((record) => record.stage));
  const scenarioEvidence = requiredFrontdeskScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages =
        requiredFrontdeskScenarioEvidence.find((item) => item.code === scenario.code)
          ?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  await testInfo.attach("real-frontdesk-scenario-codes", {
    contentType: "application/json",
    body: Buffer.from(
      JSON.stringify(
        {
          scenarioCodes: completedScenarioCodes,
          scenarioEvidence,
          ...(resourceEvidence.snapshot &&
          resourceEvidence.insuranceAudit &&
          resourceEvidence.qualityRectification
            ? {
                clinicalContext: {
                  patientId: resourceEvidence.snapshot.patientId,
                  encounterId: resourceEvidence.snapshot.encounterId,
                  contextSnapshotId: resourceEvidence.snapshot.snapshotId,
                  resources: resourceEvidence.snapshot.resources ?? {},
                },
                insuranceAudit: resourceEvidence.insuranceAudit,
                qualityRectification: resourceEvidence.qualityRectification,
                followupTemplate: resourceEvidence.followupS12?.template,
                followupRuntime: resourceEvidence.followupS12?.runtime,
                followupPlan: resourceEvidence.followupS12?.plan,
                questionnaire: resourceEvidence.followupS12?.questionnaire,
                abnormalReturn: resourceEvidence.followupS12?.abnormalReturn,
                resultBackflow: resourceEvidence.followupS12
                  ? resourceEvidence.followupS12.resultBackflow
                  : undefined,
                backflowContext: resourceEvidence.followupS12
                  ? resourceEvidence.followupS12.backflowContext
                  : undefined,
                followupPatientServiceConsumerSlice: resourceEvidence.followupS12
                  ? {
                      systemFamilyCode: "FOLLOWUP_PATIENT_SERVICE",
                      familyName: "随访、消息和患者服务",
                      canonicalResources: ["Patient", "Encounter", "FollowUp"],
                      sourceSystems: ["MEDKERNEL_FRONTDESK", "FOLLOWUP"],
                      consumer: "FOLLOWUP_RESULT_BACKFLOW",
                      consumerVerified: true,
                      standardResourceVerified: true,
                      runtimeConsumerVerified: true,
                      questionnaireVerified: true,
                      abnormalReturnVerified: true,
                      resultBackflowVerified: true,
                      auditVerified: true,
                      noAutoOrder: true,
                      followUpResourcePath: "backflowContext.resources.followUps[0]",
                      resultBackflowContextPath: "resultBackflow.contextSnapshotId",
                      auditEventPath: "resultBackflow.eventId",
                      scopeStatement:
                        "随访、消息和患者服务代表消费者切片：真实前台用 Patient、Encounter 与 FollowUp 标准资源驱动 FOLLOWUP 随访方案发布、机构生效版本消费、随访计划生成、患者自填问卷、异常回院登记和随访结果回流；不代表完整随访系统覆盖，不代表完整患者服务系统覆盖，不代表完整第三方系统族覆盖，不代表完整 S12，不代表完整 S30，不代表完整 S0-S40，不代表完整上线验收。",
                    }
                  : undefined,
                scenarioConditionEvidence: resourceEvidence.followupS12
                  ? [
                      {
                        code: "S12__NORMAL",
                        scenarioCode: "S12",
                        condition: "NORMAL",
                        source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
                        evidence: [
                          "前台创建并发布 FOLLOWUP 随访方案后纳入当前机构生效版本",
                          "临床用户基于当前上下文生成随访计划并完成问卷与异常回院登记",
                        ],
                      },
                      {
                        code: "S12__ABNORMAL",
                        scenarioCode: "S12",
                        condition: "ABNORMAL",
                        source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
                        evidence: [
                          "异常回院登记为高风险且已由真实前台登记",
                          "异常回院仅生成随访处置线索，不自动开嘱",
                        ],
                      },
                      {
                        code: "S30__NORMAL",
                        scenarioCode: "S30",
                        condition: "NORMAL",
                        source: "FOLLOWUP_PATIENT_SERVICE_CONTINUITY_BACKFLOW",
                        evidence: [
                          "真实前台发布 FOLLOWUP 随访方案并纳入当前机构生效版本",
                          "患者服务链路完成随访计划、患者自填问卷和异常回院登记",
                          "随访结果回流生成 FollowUp 标准资源并通过患者服务消费者切片回读",
                        ],
                      },
                    ]
                  : [],
                standardPatientResourceConsumerMatrix: standardPatientResourceConsumerMatrix([
                  {
                    resourceType: "Claim",
                    resourcePath: "clinicalContext.resources.claims[0]",
                    sourceSystem: "MEDKERNEL_FRONTDESK",
                    sourceIdPath: "clinicalContext.resources.claims[0].sourceRecordId",
                    patientVerified: true,
                    encounterVerified: true,
                    snapshotReadbackVerified: true,
                    consumer: "INSURANCE_AUDIT",
                    consumerEvidencePaths: ["insuranceAudit.evaluationRunId"],
                    consumerVerified: true,
                    auditEvidencePaths: ["insuranceAudit.issueId", "qualityRectification.taskId"],
                    auditVerified: true,
                    dataQualityVerified: true,
                    evaluationRunVerified: true,
                    qualityRectificationVerified: true,
                  },
                  ...(resourceEvidence.followupS12
                    ? [
                        {
                          resourceType: "FollowUp",
                          resourcePath: "backflowContext.resources.followUps[0]",
                          sourceSystem: "FOLLOWUP",
                          sourceIdPath: "backflowContext.resources.followUps[0].sourceRecordId",
                          patientVerified: true,
                          encounterVerified: true,
                          snapshotReadbackVerified: true,
                          consumer: "FOLLOWUP_RESULT_BACKFLOW",
                          consumerEvidencePaths: ["resultBackflow.contextSnapshotId"],
                          consumerVerified: true,
                          auditEvidencePaths: ["resultBackflow.eventId"],
                          auditVerified: true,
                          dataQualityVerified: true,
                        } as const,
                      ]
                    : []),
                ]),
              }
            : {}),
        },
        null,
        2,
      ),
      "utf8",
    ),
  });
}

async function expectNoRootOverflow(page: Page, label: string) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth, `${label} 页面根节点不应横向溢出`).toBeLessThanOrEqual(
    dimensions.viewportWidth,
  );
}
