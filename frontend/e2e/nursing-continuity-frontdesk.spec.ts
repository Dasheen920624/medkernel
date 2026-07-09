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
  pageItems,
  postApi,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  resolveBaselineRuntimeAssets,
  textField,
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

type FollowupRuntimeAssetCandidate = {
  assetType: "FOLLOWUP";
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

type FollowupTaskEvidence = {
  taskId?: string;
  taskType?: string;
  status?: string;
  questionnaireTemplateId?: string | null;
};

type FollowupPlanEvidence = {
  planId?: string;
  patientId?: string;
  encounterId?: string;
  diseaseCode?: string;
  runtimeReleaseId?: string;
  status?: string;
  tasks?: FollowupTaskEvidence[];
  templateId?: string | null;
  templateVersion?: number | null;
  templateCode?: string | null;
  templateName?: string | null;
  modelStatus?: string;
  sourceFactType?: string | null;
  sourceFactId?: string | null;
  generationRuleCode?: string | null;
  generationExplanation?: string | Record<string, unknown> | null;
};

type NursingContinuityApiEvidence = {
  contextSnapshotCreatedFromFrontdesk: boolean;
  nursingAssessmentReadback: boolean;
  carePlanReadback: boolean;
  followupTemplatePublished: boolean;
  runtimeActivatedWithFollowupAsset: boolean;
  followupPlanGeneratedFromFrontdesk: boolean;
  questionnaireSubmitted: boolean;
  abnormalReported: boolean;
  resultBackflowPosted: boolean;
  backflowContextContainsFollowUp: boolean;
};

const requiredStages = [
  "运营员发布 FOLLOWUP 随访方案并激活到当前机构生效版本",
  "临床用户从患者 360 建立护理高风险评估标准上下文",
  "标准上下文回读 NursingAssessment 与 CarePlan 护理事实",
  "临床用户从真实前台基于护理上下文生成随访计划",
  "随访计划解释消费 NursingAssessment 风险等级与护理计划节点",
  "临床用户提交随访问卷并登记异常回院",
  "随访结果回流生成 FollowUp 标准资源并绑定同一机构生效版本",
] as const;

test.describe("护理连续照护真实前台闭环", () => {
  test("临床用户围绕护理高风险评估完成随访计划、异常回院与结果回流闭环", async ({
    page,
  }, testInfo) => {
    test.setTimeout(300_000);
    const observedStages = new Set<string>();
    const apiEvidence = createApiEvidence();
    const suffix = `${Date.now().toString(36).toUpperCase()}-${testInfo.retry}`;

    await ensureReadySession(page, "engine-operator");
    const hospitalId = await localRehearsalHospitalId(page);
    const template = await createAndPublishNursingContinuityTemplate(page, suffix);
    apiEvidence.followupTemplatePublished = true;
    const runtime = await activateRuntimeWithNursingContinuityAssets(page, {
      hospitalId,
      followup: template,
    });
    apiEvidence.runtimeActivatedWithFollowupAsset = true;
    recordStage(observedStages, "运营员发布 FOLLOWUP 随访方案并激活到当前机构生效版本");

    const snapshot = await createNursingContinuityContextFromFrontdesk(page, suffix);
    apiEvidence.contextSnapshotCreatedFromFrontdesk = true;
    expect(
      snapshot.runtimeReleaseId,
      "护理连续照护上下文必须绑定包含本轮 FOLLOWUP 的机构生效版本",
    ).toBe(runtime.releaseId);
    recordStage(observedStages, "临床用户从患者 360 建立护理高风险评估标准上下文");

    const contextAfterCreate = await readContextSnapshot(page, snapshot.snapshotId);
    const clinicalContext = assertContextContainsNursingContinuityFacts({
      context: contextAfterCreate,
      runtime,
    });
    apiEvidence.nursingAssessmentReadback = true;
    apiEvidence.carePlanReadback = true;
    recordStage(observedStages, "标准上下文回读 NursingAssessment 与 CarePlan 护理事实");

    const plan = await generateFollowupPlanFromFrontdesk(page, {
      snapshot: contextAfterCreate,
      template,
      runtime,
    });
    apiEvidence.followupPlanGeneratedFromFrontdesk = true;
    recordStage(observedStages, "临床用户从真实前台基于护理上下文生成随访计划");
    assertFollowupPlanConsumedNursingFacts(plan, {
      snapshot: contextAfterCreate,
      runtime,
      template,
    });
    recordStage(observedStages, "随访计划解释消费 NursingAssessment 风险等级与护理计划节点");

    const drawer = await openFollowupPlanDrawer(page, {
      templateName: template.name,
      patientId: contextAfterCreate.patientId,
    });
    const questionnaire = await submitQuestionnaireFromFrontdesk(drawer, page);
    apiEvidence.questionnaireSubmitted = true;
    const abnormalReport = await reportAbnormalReturnFromFrontdesk(drawer, page);
    apiEvidence.abnormalReported = true;
    recordStage(observedStages, "临床用户提交随访问卷并登记异常回院");
    const refreshedPlan = await readFollowupPlan(
      page,
      requireText(plan.planId ?? null, "随访计划必须返回 planId"),
    );
    const followupPlan = mergePlanTasks(plan, refreshedPlan);

    const resultBackflow = await backflowFollowupResultFromFrontdesk(drawer, page);
    apiEvidence.resultBackflowPosted = true;
    const backflowContext = await readBackflowContext(page, {
      contextSnapshotId: requireText(
        textField(resultBackflow, "contextSnapshotId"),
        "随访结果回流必须返回 contextSnapshotId",
      ),
      questionnaire,
      runtime,
    });
    apiEvidence.backflowContextContainsFollowUp = true;
    recordStage(observedStages, "随访结果回流生成 FollowUp 标准资源并绑定同一机构生效版本");

    await attachNursingContinuityEvidence(testInfo, {
      apiEvidence,
      runtime,
      activationRequest: runtime.activationRequest,
      clinicalContext,
      followupPlan,
      questionnaire,
      abnormalReport,
      resultBackflow,
      backflowContext,
      observedStages,
    });
  });
});

function createApiEvidence(): NursingContinuityApiEvidence {
  return {
    contextSnapshotCreatedFromFrontdesk: false,
    nursingAssessmentReadback: false,
    carePlanReadback: false,
    followupTemplatePublished: false,
    runtimeActivatedWithFollowupAsset: false,
    followupPlanGeneratedFromFrontdesk: false,
    questionnaireSubmitted: false,
    abnormalReported: false,
    resultBackflowPosted: false,
    backflowContextContainsFollowUp: false,
  };
}

async function createAndPublishNursingContinuityTemplate(
  page: Page,
  suffix: string,
): Promise<
  FollowupRuntimeAssetCandidate & {
    templateId: string;
    templateCode: string;
    name: string;
  }
> {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "随访协同" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );
  await page.getByRole("tab", { name: "随访方案" }).click();

  const templateCode = `FOLLOWUP.NURSING.CONTINUITY.${suffix}`;
  const templateName = `护理高风险连续照护随访方案 ${suffix}`;
  await page.getByRole("button", { name: /新建方案/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建随访方案" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("院内随访方案身份").fill(templateCode);
  await dialog.getByLabel("方案名称").fill(templateName);
  await dialog
    .getByLabel("方案说明")
    .fill("护理高风险评估后连续照护代表切片；不包含患者姓名、电话、住址或证件号。");
  await chooseDialogOption(page, dialog, "适用机构范围", "当前医院");
  await chooseDialogOption(page, dialog, "随访病种", "慢阻肺");
  await dialog.getByLabel("问卷延迟天数").fill("1");
  await dialog.getByLabel("复诊延迟天数").fill("3");
  await chooseDialogOption(page, dialog, "问卷内容", "慢病随访问卷");
  await chooseDialogOption(page, dialog, "核心随访问题", "呼吸困难变化");
  await dialog.getByLabel("异常触发条件").fill("跌倒风险升高、夜间呼吸困难加重或患者主动报告异常");
  await dialog.getByLabel("通知对象").fill("责任护士与责任医生人工复核");
  await chooseDialogOption(page, dialog, "院内依据", "慢病随访管理制度");

  const createResponsePromise = waitForPost(page, "/engine/followup/templates");
  await dialog.getByRole("button", { name: /创\s*建/ }).click();
  const createResponse = await createResponsePromise;
  await expectHttpOk(createResponse, "创建护理连续照护随访方案");
  const created = await responseData(createResponse);
  const templateId = requireText(
    textField(created, "templateId"),
    "随访方案创建响应必须返回 templateId",
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("按方案名称或适用范围检索").fill(templateName);
  const row = page.getByRole("row", { name: new RegExp(escapeRegExp(templateName)) }).first();
  await expect(row, "本轮护理连续照护随访方案必须出现在治理列表").toBeVisible({
    timeout: 20_000,
  });
  const publishResponsePromise = waitForPost(
    page,
    `/engine/followup/templates/${templateId}/publish`,
  );
  await row.getByRole("button", { name: "发布方案" }).click();
  const publishResponse = await publishResponsePromise;
  await expectHttpOk(publishResponse, "发布护理连续照护随访方案");
  const published = await responseData(publishResponse);
  expect(textField(published, "templateId"), "随访方案发布响应必须绑定本轮 templateId").toBe(
    templateId,
  );
  expect(textField(published, "assetStatus"), "随访方案必须发布为 PUBLISHED").toBe("PUBLISHED");

  return {
    assetType: "FOLLOWUP",
    assetIdentity: templateCode,
    versionId: requireText(
      textField(published, "assetVersionId"),
      "随访方案发布必须返回资产版本 ID",
    ),
    versionNo: `V${numberField(published, "versionNo") ?? 1}`,
    contentHash: requireText(
      textField(published, "contentHash"),
      "随访方案发布必须返回 contentHash",
    ),
    templateId,
    templateCode,
    name: textField(published, "name") ?? templateName,
  };
}

async function activateRuntimeWithNursingContinuityAssets(
  page: Page,
  options: {
    hospitalId: string;
    followup: FollowupRuntimeAssetCandidate;
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
    runtimeSelection(options.followup),
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
  await expectOk(activated, "激活包含护理连续照护 FOLLOWUP 的医院生效版本");
  const releaseId = requireText(
    textField(await responseData(activated), "releaseId"),
    "激活机构生效版本必须返回 releaseId",
  );
  const currentAfterActivation = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfterActivation, "回读包含护理连续照护 FOLLOWUP 的医院生效版本");
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
    followupAsset: assertRuntimeContainsAsset(detail, options.followup),
    activationRequest,
  };
}

async function createNursingContinuityContextFromFrontdesk(
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
  const maskedName = `护*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = patientDialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("72");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "S20/S35 护理连续照护演练创建脱敏患者");
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
  await chooseDialogOption(page, contextDialog, "就诊类型", "住院就诊");
  await contextDialog.getByLabel("诊断/随访病种").fill(`S20/S35 护理连续照护 ${suffix}`);
  await chooseDialogOption(page, contextDialog, "风险分层", "高风险");
  await contextDialog.getByLabel("护理评估类型").fill("跌倒风险评估");
  await chooseLabeledSelectOption(page, contextDialog.getByLabel("护理风险等级"), "高风险");
  await contextDialog.getByLabel("护理评估状态").fill("CONFIRMED");
  await contextDialog.getByLabel("护理计划路径").fill(`CAREPLAN.NURSING.FALL.${suffix}`);
  await contextDialog.getByLabel("当前护理节点").fill("NURSING_CONTINUITY_EDUCATION");
  await contextDialog.getByLabel("护理计划变异").fill("HIGH_RISK_RECHECK_REQUIRED");
  await contextDialog.getByLabel("计划完成时间").fill("2026-07-20T08:00:00.000Z");
  await contextDialog
    .getByLabel("建立原因")
    .fill("S20/S35 护理高风险评估与连续照护代表切片：准备随访计划和结果回流。");
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, "S20/S35 演练建立护理 ACTIVE 快照");
  const context = await responseData(contextResponse);
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    snapshotId: requireText(textField(context, "snapshotId"), "上下文响应必须返回 snapshotId"),
    runtimeReleaseId: requireText(
      textField(context, "runtimeReleaseId"),
      "上下文响应必须锁定 runtimeReleaseId",
    ),
    encounterId: requireText(
      textFieldAtPath(context, "resources.encounters[0].encounterId"),
      "护理上下文必须返回就诊 ID",
    ),
    resources: recordValue(recordField(context, "resources")) ?? {},
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
  await expectOk(response, "回读护理连续照护上下文快照");
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

function assertContextContainsNursingContinuityFacts(options: {
  context: ContextSnapshotSummary;
  runtime: { releaseId: string };
}) {
  expect(options.context.runtimeReleaseId, "护理上下文回读必须保持当前机构生效版本").toBe(
    options.runtime.releaseId,
  );
  expect(options.context.encounterId, "护理上下文回读必须包含就诊身份").toBeTruthy();
  const nursingAssessments = arrayField(options.context.resources, "nursingAssessments");
  const carePlans = arrayField(options.context.resources, "carePlans");
  const nursingAssessment = nursingAssessments.find(
    (item) =>
      textField(item, "assessmentType") === "跌倒风险评估" &&
      textField(item, "riskLevel") === "HIGH" &&
      textField(item, "status") === "CONFIRMED" &&
      textField(item, "sourceSystem") === "MEDKERNEL_FRONTDESK",
  );
  const carePlan = carePlans.find(
    (item) =>
      (textField(item, "pathwayId") ?? "").startsWith("CAREPLAN.NURSING.FALL.") &&
      textField(item, "currentNodeId") === "NURSING_CONTINUITY_EDUCATION" &&
      textField(item, "sourceSystem") === "MEDKERNEL_FRONTDESK",
  );
  expect(nursingAssessment, "上下文回读必须包含 NursingAssessment 护理评估事实").toBeTruthy();
  expect(carePlan, "上下文回读必须包含 CarePlan 护理计划事实").toBeTruthy();
  return {
    patientId: options.context.patientId,
    encounterId: options.context.encounterId,
    contextSnapshotId: options.context.snapshotId,
    runtimeReleaseId: options.context.runtimeReleaseId,
    resources: options.context.resources,
  };
}

async function generateFollowupPlanFromFrontdesk(
  page: Page,
  options: {
    snapshot: ContextSnapshotSummary;
    template: { templateId: string; name: string };
    runtime: { releaseId: string; followupAsset: RuntimeReleaseItem };
  },
): Promise<FollowupPlanEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "随访协同" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );
  await page.getByRole("button", { name: "生成随访计划" }).click();
  const dialog = page.getByRole("dialog", { name: "生成随访计划" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("随访快照患者信息").fill(options.snapshot.patientId);
  const snapshotButton = dialog.locator(
    `button[data-snapshot-id="${options.snapshot.snapshotId}"]`,
  );
  await expect(
    snapshotButton,
    `随访计划弹窗必须展示本轮上下文 ${options.snapshot.snapshotId}`,
  ).toBeVisible({
    timeout: 30_000,
  });
  await snapshotButton.click();
  await chooseDialogOption(page, dialog, "随访风险分层", "高风险");
  await searchDialogOption(page, dialog, "随访方案", options.template.name, options.template.name);
  const planResponsePromise = waitForPost(page, "/engine/followup/plans/generate");
  await dialog.getByRole("button", { name: /生\s*成/ }).click();
  const planResponse = await planResponsePromise;
  await expectHttpOk(planResponse, "临床用户从真实前台生成护理连续照护随访计划");
  const plan = (await responseData(planResponse)) as FollowupPlanEvidence;
  expect(plan.planId, "随访计划生成响应应返回计划身份").toBeTruthy();
  expect(plan.patientId, "随访计划必须绑定本轮患者").toBe(options.snapshot.patientId);
  expect(plan.encounterId, "随访计划必须绑定本轮就诊").toBe(options.snapshot.encounterId);
  expect(plan.runtimeReleaseId, "随访计划必须使用上下文锁定 runtime").toBe(
    options.runtime.releaseId,
  );
  expect(plan.templateId, "随访计划必须绑定本轮随访方案").toBe(options.template.templateId);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return plan;
}

function assertFollowupPlanConsumedNursingFacts(
  plan: FollowupPlanEvidence,
  options: {
    snapshot: ContextSnapshotSummary;
    runtime: { releaseId: string; followupAsset: RuntimeReleaseItem };
    template: FollowupRuntimeAssetCandidate;
  },
) {
  const explanation = parseJsonRecord(plan.generationExplanation);
  expect(plan.modelStatus, "随访计划必须在无模型条件下诚实运行").toBe("MODEL_DISABLED");
  expect(plan.runtimeReleaseId, "随访计划响应必须返回机构生效版本").toBe(options.runtime.releaseId);
  expect(explanation?.runtimeReleaseId, "随访计划解释必须绑定机构生效版本").toBe(
    options.runtime.releaseId,
  );
  expect(
    arrayField(explanation, "nursingAssessmentEvidence").some(
      (item) =>
        textField(item, "assessmentType") === "跌倒风险评估" &&
        textField(item, "riskLevel") === "HIGH" &&
        textField(item, "status") === "CONFIRMED",
    ),
    "随访计划解释必须消费 NursingAssessment 风险等级",
  ).toBe(true);
  expect(
    arrayField(explanation, "carePlanEvidence").some(
      (item) =>
        (textField(item, "pathwayId") ?? "").startsWith("CAREPLAN.NURSING.FALL.") &&
        textField(item, "currentNodeId") === "NURSING_CONTINUITY_EDUCATION",
    ),
    "随访计划解释必须消费 CarePlan 当前节点",
  ).toBe(true);
  expect(
    arrayField(explanation, "runtimeAssetEvidence").some(
      (item) =>
        textField(item, "assetType") === "FOLLOWUP" &&
        textField(item, "assetIdentity") === options.template.assetIdentity &&
        textField(item, "assetVersionId") === options.template.versionId &&
        textField(item, "assetVersionNo") === options.template.versionNo,
    ),
    "随访计划解释必须返回 FOLLOWUP 运行资产证据",
  ).toBe(true);
}

function recordStage(stages: Set<string>, stage: (typeof requiredStages)[number]) {
  stages.add(stage);
}

async function openFollowupPlanDrawer(
  page: Page,
  options: { templateName: string; patientId: string },
) {
  const drawer = page.getByRole("dialog", { name: "随访计划办理" });
  if (await drawer.isVisible({ timeout: 1_000 }).catch(() => false)) {
    if (
      await drawer
        .getByText(options.templateName, { exact: false })
        .first()
        .isVisible()
        .catch(() => false)
    ) {
      return drawer;
    }
    await drawer.getByRole("button", { name: /close/i }).click();
    await expect(drawer).toBeHidden({ timeout: 5_000 });
  }
  await page.getByPlaceholder("按患者线索检索").fill(options.patientId);
  const listResponsePromise = waitForGet(page, "/engine/followup/plans");
  await page.getByRole("button", { name: /查\s*询/ }).click();
  const listResponse = await listResponsePromise;
  await expectHttpOk(listResponse, "按本次患者线索查询护理连续照护随访计划");
  const row = page
    .getByRole("row", { name: new RegExp(escapeRegExp(options.templateName)) })
    .first();
  await expect(row, "新生成护理连续照护随访计划必须能按患者线索定位").toBeVisible({
    timeout: 30_000,
  });
  await row.getByRole("button", { name: /查看与办理/ }).click();
  await expect(drawer).toBeVisible({ timeout: 30_000 });
  await expect(drawer.getByText(options.templateName, { exact: false }).first()).toBeVisible({
    timeout: 30_000,
  });
  return drawer;
}

async function submitQuestionnaireFromFrontdesk(drawer: Locator, page: Page) {
  const fillButton = drawer.getByRole("button", { name: /填\s*报/ }).first();
  await expect(fillButton, "护理连续照护随访计划必须生成可办理问卷任务").toBeVisible({
    timeout: 30_000,
  });
  await fillButton.click();
  await chooseDialogOption(page, drawer, "提交来源", "护士代填");
  await drawer
    .getByLabel("问卷回收内容")
    .fill("护士代填记录：患者夜间起身频繁且步态不稳，已完成跌倒风险宣教并提醒家属陪护。");
  const questionnaireResponsePromise = waitForPost(page, "/engine/followup/questionnaires");
  await drawer.getByRole("button", { name: "提交问卷" }).click();
  const questionnaireResponse = await questionnaireResponsePromise;
  await expectHttpOk(questionnaireResponse, "提交护理连续照护随访问卷");
  await expect(drawer.getByText("请选择一个待办随访任务后提交问卷回收内容")).toBeVisible({
    timeout: 20_000,
  });
  const questionnaire = await responseData(questionnaireResponse);
  expect(
    textField(questionnaire, "questionnaireId"),
    "问卷提交响应必须返回 questionnaireId",
  ).toBeTruthy();
  expect(textField(questionnaire, "status"), "问卷提交后必须完成任务").toBe("COMPLETED");
  return questionnaire;
}

async function reportAbnormalReturnFromFrontdesk(drawer: Locator, page: Page) {
  await chooseDialogOption(page, drawer, "回院风险等级", "高风险");
  await drawer
    .getByLabel("异常症状或情况")
    .fill("患者反馈夜间下床时头晕、步态不稳，跌倒风险升高，需要回院复评。");
  await drawer
    .getByLabel("医护处理建议")
    .fill("责任护士已通知责任医生人工复核，建议线下评估后决定处置；本页不自动开嘱。");
  const abnormalResponsePromise = waitForPost(page, "/engine/followup/abnormal-reports");
  await drawer.getByRole("button", { name: "登记异常回院" }).click();
  const abnormalResponse = await abnormalResponsePromise;
  await expectHttpOk(abnormalResponse, "登记护理连续照护异常回院");
  await expect(drawer.getByText("异常回院证据已登记")).toBeVisible({ timeout: 20_000 });
  const abnormal = await responseData(abnormalResponse);
  expect(textField(abnormal, "eventId"), "异常回院响应必须返回事件 ID").toBeTruthy();
  expect(textField(abnormal, "returnTaskId"), "异常回院响应必须返回回院任务 ID").toBeTruthy();
  return abnormal;
}

async function backflowFollowupResultFromFrontdesk(drawer: Locator, page: Page) {
  const backflowResponsePromise = waitForPost(page, "/engine/followup/results");
  await drawer.getByRole("button", { name: "回流随访结果" }).click();
  const backflowResponse = await backflowResponsePromise;
  await expectHttpOk(backflowResponse, "回流护理连续照护随访结果");
  await expect(drawer.getByText("随访结果回流已完成")).toBeVisible({ timeout: 20_000 });
  const backflow = await responseData(backflowResponse);
  expect(textField(backflow, "eventId"), "随访结果回流响应必须返回事件 ID").toBeTruthy();
  expect(
    textField(backflow, "contextSnapshotId"),
    "随访结果回流响应必须返回上下文 ID",
  ).toBeTruthy();
  return backflow;
}

async function readFollowupPlan(page: Page, planId: string): Promise<FollowupPlanEvidence> {
  const response = await getApi(page, `/engine/followup/plans/${encodeURIComponent(planId)}`);
  await expectOk(response, "回读护理连续照护随访计划详情");
  return (await responseData(response)) as FollowupPlanEvidence;
}

function mergePlanTasks(
  plan: FollowupPlanEvidence,
  refreshed: FollowupPlanEvidence,
): FollowupPlanEvidence {
  return {
    ...plan,
    tasks: refreshed.tasks ?? plan.tasks ?? [],
    status: refreshed.status ?? plan.status,
  };
}

async function readBackflowContext(
  page: Page,
  options: {
    contextSnapshotId: string;
    questionnaire: unknown;
    runtime: { releaseId: string };
  },
) {
  const context = await readContextSnapshot(page, options.contextSnapshotId);
  expect(context.runtimeReleaseId, "随访结果回流上下文必须继承随访计划 runtime").toBe(
    options.runtime.releaseId,
  );
  const questionnaireId = requireText(
    textField(options.questionnaire, "questionnaireId"),
    "回流验证必须持有真实 questionnaireId",
  );
  const followUps = arrayField(context.resources, "followUps");
  expect(
    followUps.some(
      (item) =>
        textField(item, "followUpId") === questionnaireId &&
        textField(item, "questionnaireId") &&
        textField(item, "abnormalFlag") === "Y" &&
        textField(item, "sourceSystem") === "FOLLOWUP" &&
        textField(item, "mappedVersion") === "FOLLOWUP_RESULT",
    ),
    "回流上下文必须回读 FollowUp 标准资源",
  ).toBe(true);
  return {
    patientId: context.patientId,
    encounterId: context.encounterId,
    contextSnapshotId: context.snapshotId,
    runtimeReleaseId: context.runtimeReleaseId,
    resources: context.resources,
  };
}

async function attachNursingContinuityEvidence(
  testInfo: TestInfo,
  evidence: {
    apiEvidence: NursingContinuityApiEvidence;
    runtime: {
      releaseId: string;
      platformBaselineReleaseId: string;
      revisionNo: number;
      manifestSha256: string;
      assets: RuntimeReleaseItem[];
      followupAsset: RuntimeReleaseItem;
    };
    activationRequest: unknown;
    clinicalContext: unknown;
    followupPlan: unknown;
    questionnaire: unknown;
    abnormalReport: unknown;
    resultBackflow: unknown;
    backflowContext: unknown;
    observedStages: Set<string>;
  },
) {
  for (const stage of requiredStages) {
    expect(evidence.observedStages.has(stage), `缺少护理连续照护阶段：${stage}`).toBe(true);
  }
  await testInfo.attach("nursing-continuity-frontdesk-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes: ["S20", "S35"],
        productLayers: ["CLINICAL_EXECUTION"],
        versionedAssets: ["FOLLOWUP"],
        serviceCombinations: ["CLINICAL_RUNTIME"],
        scopeStatement:
          "护理连续照护代表切片：围绕 NursingAssessment、CarePlan 与 FollowUp 完成高风险护理评估后的随访计划、异常回院和结果回流，不代表完整护理专业智能、完整护理计划执行或完整 S20/S35 上线。",
        standardPatientResourceConsumerMatrix: standardPatientResourceConsumerMatrix([
          {
            resourceType: "NursingAssessment",
            resourcePath: "clinicalContext.resources.nursingAssessments[0]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.nursingAssessments[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "FOLLOWUP_PLAN_GENERATION",
            consumerEvidencePaths: [
              "followupPlanGenerationExplanation.nursingAssessmentEvidence[0]",
            ],
            consumerVerified: true,
            auditEvidencePaths: ["questionnaire.questionnaireId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
          {
            resourceType: "CarePlan",
            resourcePath: "clinicalContext.resources.carePlans[0]",
            sourceSystem: "MEDKERNEL_FRONTDESK",
            sourceIdPath: "clinicalContext.resources.carePlans[0].sourceRecordId",
            patientVerified: true,
            encounterVerified: true,
            snapshotReadbackVerified: true,
            consumer: "FOLLOWUP_PLAN_GENERATION",
            consumerEvidencePaths: ["followupPlanGenerationExplanation.carePlanEvidence[0]"],
            consumerVerified: true,
            auditEvidencePaths: ["abnormalReport.eventId"],
            auditVerified: true,
            dataQualityVerified: true,
          },
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
          },
        ]),
        apiEvidence: evidence.apiEvidence,
        runtime: {
          releaseId: evidence.runtime.releaseId,
          platformBaselineReleaseId: evidence.runtime.platformBaselineReleaseId,
          revisionNo: evidence.runtime.revisionNo,
          manifestSha256: evidence.runtime.manifestSha256,
          assets: evidence.runtime.assets,
          followupAsset: evidence.runtime.followupAsset,
        },
        activationRequest: evidence.activationRequest,
        clinicalContext: evidence.clinicalContext,
        followupPlan: evidence.followupPlan,
        followupPlanGenerationExplanation: parseJsonRecord(
          recordValue(evidence.followupPlan)?.generationExplanation,
        ),
        questionnaire: evidence.questionnaire,
        abnormalReport: evidence.abnormalReport,
        resultBackflow: {
          ...recordValue(evidence.resultBackflow),
          sourceQuestionnaireId: textField(evidence.questionnaire, "questionnaireId"),
          abnormalFlag: "Y",
        },
        backflowContext: evidence.backflowContext,
        scenarioConditionEvidence: [
          {
            code: "S20__NORMAL",
            scenarioCode: "S20",
            condition: "NORMAL",
            source: "NURSING_CONTINUITY_FOLLOWUP_PLAN_RESULT_BACKFLOW",
            evidence: [
              "FOLLOWUP 资产已激活到当前机构生效版本",
              "临床用户从真实前台基于护理上下文生成随访计划并完成问卷",
              "随访结果回流生成 FollowUp 标准资源并绑定同一 runtime",
            ],
          },
          {
            code: "S35__ABNORMAL",
            scenarioCode: "S35",
            condition: "ABNORMAL",
            source: "NURSING_HIGH_RISK_ASSESSMENT_ABNORMAL_RETURN",
            evidence: [
              "标准上下文回读 NursingAssessment 高风险评估与 CarePlan 护理计划",
              "随访计划解释消费护理评估风险等级和护理计划节点",
              "异常回院事件、回院任务和通知事件均已登记",
            ],
          },
        ],
        scenarioEvidence: [
          {
            code: "S20",
            observedStages: Array.from(evidence.observedStages).filter(
              (stage) =>
                stage.includes("FOLLOWUP") ||
                stage.includes("随访") ||
                stage.includes("回流") ||
                stage.includes("异常"),
            ),
          },
          {
            code: "S35",
            observedStages: Array.from(evidence.observedStages).filter(
              (stage) =>
                stage.includes("护理") ||
                stage.includes("NursingAssessment") ||
                stage.includes("CarePlan"),
            ),
          },
        ],
      },
      null,
      2,
    ),
  });
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
  await expectOk(response, "读取 S20/S35 护理连续照护平台升级分析");
  return requireText(
    textField(await responseData(response), "analysisDigest"),
    "S20/S35 护理连续照护平台升级分析必须返回摘要",
  );
}

function runtimeSelection(candidate: FollowupRuntimeAssetCandidate): RuntimeAssetSelection {
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
  candidate: FollowupRuntimeAssetCandidate,
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
    `机构生效版本必须包含 ${candidate.assetType} ${candidate.assetIdentity}`,
  ).toBeTruthy();
  expect(asset?.versionNo, "FOLLOWUP runtime 清单必须返回版本号").toBe(candidate.versionNo);
  expect(asset?.contentHash, "FOLLOWUP runtime 清单必须返回正文 hash").toBe(candidate.contentHash);
  return asset as RuntimeReleaseItem;
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

async function searchDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  searchText: string,
  optionText: string,
) {
  const field = dialog.getByLabel(label);
  await field.click();
  await field.fill(searchText);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, `${label} 搜索下拉应展开`).toBeVisible({ timeout: 10_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(escapeRegExp(optionText), "u") })
    .first();
  await expect(option, `${label} 应存在选项 ${optionText}`).toBeVisible({ timeout: 20_000 });
  await option.click();
}

async function chooseLabeledSelectOption(page: Page, field: Locator, optionText: string) {
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
  await expect(dropdown, "下拉应展开").toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`(^|\\s)${escapeRegExp(optionText)}(\\s|$)`, "u") })
    .first();
  await expect(option, `应存在选项 ${optionText}`).toBeVisible({ timeout: 10_000 });
  await option.click();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function waitForGet(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "GET" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

async function expectHttpOk(response: APIResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
}

function parseJsonRecord(value: unknown) {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  if (typeof value !== "string" || !value.trim()) return null;
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

function numberField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function numberFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
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
