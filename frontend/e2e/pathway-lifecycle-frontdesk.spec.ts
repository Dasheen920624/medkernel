import { expect, test, type APIResponse, type Page, type TestInfo } from "@playwright/test";
import { randomUUID } from "node:crypto";

import {
  apiBase,
  appPath,
  ensureReadySession,
  expectOk,
  postApi,
  requiredRuntimeAssetsForRehearsal,
} from "./support/auth";

type JsonHttpResponse = {
  ok(): boolean;
  status(): number;
  text(): Promise<string>;
  json(): Promise<unknown>;
};

type ContextSnapshotSummary = {
  patientId: string;
  maskedName: string;
  idLast4: string;
  snapshotId: string;
  encounterId: string | null;
  createdAt: string | null;
};

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

type OrderSetCandidate = {
  assetType: "ORDER_SET";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

type OrderSetRuntimeConsumerEvidence = {
  asset: OrderSetCandidate;
  runtimeRelease: {
    releaseId: string;
    revisionNo?: number;
    manifestSha256?: string;
    assetPresent: boolean;
    assets: RuntimeReleaseItem[];
  };
  patientPathway: {
    patientPathwayId: string;
    runtimeReleaseId: string;
  };
  advanceResponse: {
    previousNodeCode: string | null;
    nextNodeCode: string | null;
    status: string | null;
    decisionEvidence: Record<string, unknown>;
  };
};

type PathwayLifecycleApiEvidence = {
  templateSaved: boolean;
  templateReadback: boolean;
  draftPreviewRun: boolean;
  templateSimulated: boolean;
  entryCandidatesRead: boolean;
  patientEntered: boolean;
  standardAdvanced: boolean;
  orderSetRuntimeConsumed: boolean;
  varianceRecorded: boolean;
  followupHandoffCreated: boolean;
  clocksRead: boolean;
  variancesRead: boolean;
  followupHandoffObserved: boolean;
};

const PATHWAY_RUNTIME_TRIGGER = "patient-view";
const pathwayLifecycleTitle =
  "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片";
const requiredPathwayLifecycleScenarioEvidence = [
  {
    code: "S6",
    observedStages: [
      "前台创建专病路径草稿并保存节点边时钟",
      "后端回读路径节点边时钟与十阶段里程碑",
      "前台使用真实 ACTIVE 快照完成草稿试运行",
      "真实服务链路对已保存路径执行仿真",
      "临床用户基于当前机构生效版本读取入径候选",
      "临床用户办理患者入径并生成首个关键时钟",
      "临床用户完成当前节点并标准推进",
      "临床用户推进到医嘱套餐节点并消费当前机构生效版本 ORDER_SET",
      "真实后端登记路径变异与处置决策",
      "真实后端完成随访接续终点节点",
      "后端回读关键时钟和变异事实",
      "路径完成后生成随访接续证据",
    ],
  },
];
const specialDiseaseStages = [
  "SCREENING_TRIAGE",
  "DIAGNOSIS_DIFFERENTIAL",
  "RISK_STRATIFICATION",
  "TREATMENT_DECISION",
  "EXECUTION_CANDIDATE",
  "MONITORING_WARNING",
  "DISCHARGE_REFERRAL",
  "REHAB_EDUCATION_FOLLOWUP",
  "OUTCOME_EVALUATION",
  "QUALITY_ITERATION",
];

test(pathwayLifecycleTitle, async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const observedStages = new Set<string>();
  const apiEvidence: PathwayLifecycleApiEvidence = {
    templateSaved: false,
    templateReadback: false,
    draftPreviewRun: false,
    templateSimulated: false,
    entryCandidatesRead: false,
    patientEntered: false,
    standardAdvanced: false,
    orderSetRuntimeConsumed: false,
    varianceRecorded: false,
    followupHandoffCreated: false,
    clocksRead: false,
    variancesRead: false,
    followupHandoffObserved: false,
  };

  await ensureReadySession(page, "clinical-user");
  const snapshot = await createClinicalContextFromFrontdesk(page);

  await ensureReadySession(page, "engine-operator");
  const orderSetCandidate = await createOrderSetAssetFromFrontdesk(page);
  const templateCode = `PATHWAY.S6.COPD.${Date.now()}`;
  const templateName = `S6 慢阻肺专病证据切片路径 ${templateCode.slice(-6)}`;
  const templateRequest = pathwayTemplateRequest(
    templateCode,
    templateName,
    orderSetCandidate.assetIdentity,
  );
  const created = await createPathwayTemplateFromFrontdesk(
    page,
    snapshot,
    templateRequest,
    apiEvidence,
  );
  recordPathwayLifecycleStage(observedStages, "前台创建专病路径草稿并保存节点边时钟");
  recordPathwayLifecycleStage(observedStages, "前台使用真实 ACTIVE 快照完成草稿试运行");

  const templateId = requireText(
    textField(created, "template.templateId") ?? textField(created, "templateId"),
    "路径保存响应必须返回 templateId",
  );

  const detail = await getApi(page, `/engine/pathway/pathway-templates/${encodeURIComponent(templateId)}`);
  await expectOk(detail, "回读专病临床路径详情");
  const detailData = await responseData(detail);
  assertTenStagePathwayDetail(detailData, templateCode);
  apiEvidence.templateReadback = true;
  recordPathwayLifecycleStage(observedStages, "后端回读路径节点边时钟与十阶段里程碑");

  const activatedRuntime = await activateRuntimeWithPathway(page, templateCode, orderSetCandidate);
  const runtimeOrderSetAsset = assertRuntimeContainsOrderSetAsset(
    activatedRuntime,
    orderSetCandidate,
  );

  await ensureReadySession(page, "clinical-user");
  const entrySnapshot = await rebuildClinicalContextFromFrontdesk(page, snapshot);
  await ensureReadySession(page, "engine-operator");
  const simulation = await postApi(
    page,
    `/engine/pathway/pathway-templates/${encodeURIComponent(templateId)}/simulate`,
    {
      ...(await pathwayApiContext(page)),
      simulationMode: "SINGLE_SNAPSHOT",
      snapshotId: entrySnapshot.snapshotId,
      startNodeCode: "SCREEN",
      requestedNextNodeCodes: ["ASSESS", "FOLLOWUP"],
    },
  );
  await expectOk(simulation, "真实 ACTIVE 快照仿真已保存路径");
  const simulationData = await responseData(simulation);
  expect(arrayField(simulationData, "nodeTrajectory")).toEqual(["SCREEN", "ASSESS", "FOLLOWUP"]);
  expect(textField(simulationData, "simulationMode")).toBe("SINGLE_SNAPSHOT");
  apiEvidence.templateSimulated = true;
  recordPathwayLifecycleStage(observedStages, "真实服务链路对已保存路径执行仿真");

  await ensureReadySession(page, "clinical-user");
  const entryCandidates = await getApi(
    page,
    `/engine/pathway/patient-pathways/entry-candidates?contextSnapshotId=${encodeURIComponent(
      entrySnapshot.snapshotId,
    )}&triggerPoint=${encodeURIComponent(PATHWAY_RUNTIME_TRIGGER)}`,
  );
  await expectOk(entryCandidates, "读取当前机构生效版本入径候选");
  const candidate = arrayField(await responseData(entryCandidates), "candidates").find(
    (item) => textField(item, "templateCode") === templateCode,
  );
  expect(candidate, `本轮路径 ${templateCode} 必须成为入径候选`).toBeTruthy();
  apiEvidence.entryCandidatesRead = true;
  recordPathwayLifecycleStage(observedStages, "临床用户基于当前机构生效版本读取入径候选");

  const patientPathway = await enterPathwayFromFrontdesk(page, entrySnapshot, templateName, apiEvidence);
  recordPathwayLifecycleStage(observedStages, "临床用户办理患者入径并生成首个关键时钟");

  const patientPathwayId = requireText(
    textField(patientPathway, "patientPathway.patientPathwayId"),
    "患者入径响应必须返回 patientPathwayId",
  );

  await advancePathwayFromFrontdesk(
    page,
    patientPathwayId,
    entrySnapshot.patientId,
    "ASSESS",
    "风险评估与复查医嘱建议",
    apiEvidence,
  );
  recordPathwayLifecycleStage(observedStages, "临床用户完成当前节点并标准推进");

  const variance = await recordVarianceAndCompleteFromApi(
    page,
    patientPathwayId,
    entrySnapshot,
    apiEvidence,
  );
  recordPathwayLifecycleStage(observedStages, "真实后端登记路径变异与处置决策");

  const orderSetAdvance = await advancePathwayFromFrontdesk(
    page,
    patientPathwayId,
    entrySnapshot.patientId,
    "FOLLOWUP",
    "随访接续",
    apiEvidence,
  );
  const orderSetRuntimeConsumer = assertOrderSetRuntimeConsumerEvidence({
    activatedRuntime,
    runtimeOrderSetAsset,
    orderSetCandidate,
    patientPathway,
    orderSetAdvance,
    patientPathwayId,
  });
  apiEvidence.orderSetRuntimeConsumed = true;
  recordPathwayLifecycleStage(
    observedStages,
    "临床用户推进到医嘱套餐节点并消费当前机构生效版本 ORDER_SET",
  );

  const completion = await completeFollowupNodeFromApi(page, patientPathwayId, apiEvidence);
  recordPathwayLifecycleStage(observedStages, "真实后端完成随访接续终点节点");

  const clocks = await getApi(
    page,
    `/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/clocks`,
  );
  await expectOk(clocks, "回读患者路径关键时钟");
  expect(arrayData(await responseData(clocks)).length).toBeGreaterThanOrEqual(2);
  apiEvidence.clocksRead = true;
  const variances = await getApi(
    page,
    `/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/variances`,
  );
  await expectOk(variances, "回读患者路径变异事实");
  expect(
    arrayData(await responseData(variances)).some(
      (item) => textField(item, "varianceId") === textField(variance, "varianceId"),
    ),
  ).toBe(true);
  apiEvidence.variancesRead = true;
  recordPathwayLifecycleStage(observedStages, "后端回读关键时钟和变异事实");

  const followupPlanId = textField(completion, "followupPlanId");
  const followupPlans = await getApi(
    page,
    `/engine/followup/plans?patientId=${encodeURIComponent(entrySnapshot.patientId)}&page=1&size=20`,
  );
  await expectOk(followupPlans, "回读路径完成后的随访接续计划");
  const followupPlan = pageItems(await responseData(followupPlans)).find(
    (item) =>
      textField(item, "planId") === followupPlanId ||
      (textField(item, "patientId") === entrySnapshot.patientId &&
        textField(item, "encounterId") === entrySnapshot.encounterId &&
        textField(item, "sourceFactId") === patientPathwayId),
  );
  expect(followupPlan, "路径完成后必须生成本轮患者随访接续计划").toBeTruthy();
  apiEvidence.followupHandoffObserved = Boolean(followupPlanId && followupPlan);
  recordPathwayLifecycleStage(observedStages, "路径完成后生成随访接续证据");

  await attachPathwayLifecycleScenarioEvidence(testInfo, observedStages, apiEvidence, {
    patientId: entrySnapshot.patientId,
    encounterId: entrySnapshot.encounterId,
    contextSnapshotId: entrySnapshot.snapshotId,
    previewSnapshotId: snapshot.snapshotId,
    templateCode,
    templateId,
    patientPathwayId,
    followupPlanId,
    orderSetRuntimeConsumer,
  });
});

async function createClinicalContextFromFrontdesk(page: Page): Promise<ContextSnapshotSummary> {
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `径*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = patientDialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("68");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectHttpOk(patientResponse, "S6 演练创建脱敏患者");
  const patientId = textField(await responseData(patientResponse), "mpiId");
  expect(patientId, "S6 演练患者创建响应必须返回 MPI").toBeTruthy();
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

  const context = await submitClinicalContextDialog(page, "建立 ACTIVE 上下文");
  const snapshotId = textField(context, "snapshotId");
  return {
    patientId: patientId ?? "",
    snapshotId: snapshotId ?? "",
    encounterId: textField(context, "resources.encounters.0.encounterId"),
    maskedName,
    idLast4,
    createdAt: textField(context, "createdAt"),
  };
}

async function submitClinicalContextDialog(page: Page, label: string) {
  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible();
  await chooseDialogOption(contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("慢阻肺急性加重风险");
  await chooseDialogOption(contextDialog, "风险分层", "中风险");
  await contextDialog.getByLabel("医技报告项目").fill("肺功能与血气复查");
  await contextDialog.getByLabel("报告结论").fill("FEV1 下降，氧合波动，需纳入专病路径");
  await contextDialog.getByLabel("异常重点").fill("呼吸困难加重、氧合波动");
  await contextDialog
    .getByLabel("建立原因")
    .fill("S6 专病路径生命周期演练：建立 ACTIVE 上下文用于入径、推进、变异和随访接续。");

  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectHttpOk(contextResponse, `S6 演练${label}快照`);
  const context = await responseData(contextResponse);
  expect(textField(context, "snapshotId"), `${label}响应必须返回 snapshotId`).toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return context;
}

async function rebuildClinicalContextFromFrontdesk(
  page: Page,
  previous: ContextSnapshotSummary,
): Promise<ContextSnapshotSummary> {
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(previous.maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(previous.maskedName)}.*${previous.idLast4}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("button", { name: "更新当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "更新当前就诊上下文" }).click();
  const context = await submitClinicalContextDialog(page, "更新当前就诊上下文");
  return {
    patientId: previous.patientId,
    maskedName: previous.maskedName,
    idLast4: previous.idLast4,
    snapshotId: requireText(textField(context, "snapshotId"), "更新上下文响应必须返回 snapshotId"),
    encounterId: textField(context, "resources.encounters.0.encounterId"),
    createdAt: textField(context, "createdAt"),
  };
}

async function createOrderSetAssetFromFrontdesk(page: Page): Promise<OrderSetCandidate> {
  const suffix = Date.now().toString(36).toUpperCase();
  const assetIdentity = `ORDER_SET.S6.COPD.${suffix}`;
  await page.goto(appPath("/authoring/assets"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "统一资产库" })).toBeVisible();
  await page.getByRole("tab", { name: "字段与配置资产" }).click();
  await page.getByRole("tab", { name: "医嘱套餐" }).click();
  await page.getByRole("button", { name: "新建医嘱套餐" }).click();
  const dialog = page.getByRole("dialog", { name: "新建医嘱套餐" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("稳定资产身份").fill(assetIdentity);
  await dialog.getByLabel("适用范围").fill("ALL");
  await dialog
    .getByLabel("来源依据")
    .fill("S6 专病路径演练：慢阻肺复查医嘱套餐，仅生成建议并要求医师确认。");
  await dialog.getByLabel("名称", { exact: true }).fill(`S6 慢阻肺复查医嘱套餐 ${suffix}`);
  await chooseDialogOption(dialog, "项目类型", "检验项目");
  await dialog.getByLabel("编码体系").fill("LOCAL-E2E");
  await dialog.getByLabel("项目编码").fill(`COPD-ABG-${suffix}`);
  await dialog.getByLabel("项目名称").fill(`S6 慢阻肺血气复查 ${suffix}`);
  const requiredCheckbox = dialog.getByRole("checkbox", { name: "必选" });
  if (!(await requiredCheckbox.isChecked())) {
    await requiredCheckbox.click();
  }
  const responsePromise = waitForPost(page, "/engine/authoring/declarative-assets");
  await dialog.getByRole("button", { name: "保存草稿" }).click();
  const response = await responsePromise;
  await expectHttpOk(response, "前台创建 S6 医嘱套餐草稿");
  const data = await responseData(response);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    assetType: "ORDER_SET",
    assetIdentity,
    versionId: requireText(textField(data, "versionId"), "医嘱套餐草稿必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "医嘱套餐草稿必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "医嘱套餐草稿必须返回 contentHash"),
  };
}

async function createPathwayTemplateFromFrontdesk(
  page: Page,
  snapshot: ContextSnapshotSummary,
  templateRequest: Record<string, unknown>,
  apiEvidence: PathwayLifecycleApiEvidence,
) {
  await page.goto(appPath("/pathway/templates"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "临床路径库" })).toBeVisible();
  await page.getByRole("button", { name: "新建临床路径" }).click();
  const dialog = page.getByRole("dialog", { name: "新建临床路径" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("路径名称").fill(String(templateRequest.name ?? ""));
  await dialog.getByLabel("稳定临床路径身份").fill(String(templateRequest.templateCode ?? ""));
  await dialog.getByLabel("适用病种身份").fill(String(templateRequest.diseaseCode ?? ""));
  await dialog.getByLabel("临床知识与指南基础").fill(String(templateRequest.sourceRef ?? ""));
  await dialog
    .getByLabel("收治标准与排除指标")
    .fill(String(templateRequest.description ?? ""));
  await dialog.getByRole("switch", { name: "受控配置文本模式" }).click();
  await dialog.getByRole("tab", { name: "即配即试" }).click();
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  const snapshotsPromise = waitForGet(page, "/engine/context/snapshots");
  await dialog.getByRole("button", { name: "读取真实快照" }).click();
  await expectHttpOk(await snapshotsPromise, "新建路径弹窗读取本轮 ACTIVE 快照");
  await selectSnapshotByBackendVerifiedFilter(page, dialog, snapshot);
  await dialog.getByRole("tab", { name: "受控配置文本" }).click();
  const textArea = dialog.locator("textarea").last();
  await textArea.fill(JSON.stringify(templateRequest, null, 2));
  await dialog.getByRole("button", { name: "回填到 L2" }).click();
  await dialog.getByRole("tab", { name: "节点画布" }).click();
  await expect(dialog.getByLabel("医嘱套餐引用")).toHaveValue(
    String(recordField(templateRequest, "nodes.1.config.orderSetRef")),
  );
  await dialog.getByRole("tab", { name: "即配即试" }).click();
  const previewRun = waitForPost(page, "/engine/authoring/preview-run");
  await dialog.getByRole("button", { name: "运行草稿试运行" }).click();
  await expectHttpOk(await previewRun, "前台真实 ACTIVE 快照草稿试运行");
  apiEvidence.draftPreviewRun = true;
  const saved = waitForPost(page, "/engine/pathway/pathway-templates");
  await clickDialogConfirm(dialog);
  const savedResponse = await saved;
  await expectHttpOk(savedResponse, "前台保存专病路径草稿");
  apiEvidence.templateSaved = true;
  const savedData = await responseData(savedResponse);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return savedData;
}

async function enterPathwayFromFrontdesk(
  page: Page,
  snapshot: ContextSnapshotSummary,
  templateName: string,
  apiEvidence: PathwayLifecycleApiEvidence,
) {
  await page.goto(appPath("/pathway/patients"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者路径" })).toBeVisible();
  await page.getByRole("button", { name: "办理患者入径" }).click();
  const dialog = page.getByRole("dialog", { name: "办理患者临床路径准入" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  await selectSnapshotByBackendVerifiedFilter(page, dialog, snapshot);
  await expect(dialog.getByText(/已读取 \d+ 条当前机构生效候选路径/)).toBeVisible({
    timeout: 20_000,
  });
  const candidateSelect = dialog.getByRole("combobox", { name: "选择当前运行候选路径" });
  await candidateSelect.click();
  await page.getByRole("option", { name: new RegExp(escapeRegExp(templateName)) }).click();
  const entered = waitForPost(page, "/engine/pathway/patient-pathways/enter");
  await clickDialogConfirm(dialog);
  const enteredResponse = await entered;
  await expectHttpOk(enteredResponse, "临床用户前台办理患者入径");
  apiEvidence.patientEntered = true;
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return responseData(enteredResponse);
}

async function advancePathwayFromFrontdesk(
  page: Page,
  patientPathwayId: string,
  patientId: string,
  nextNodeCode: string,
  targetOptionName: string,
  apiEvidence: PathwayLifecycleApiEvidence,
) {
  await page.getByLabel("患者检索").fill(patientId);
  await page.getByRole("button", { name: "办理推进与解释追溯" }).first().click();
  const drawer = page.getByRole("dialog", { name: /患者路径推进与解释追溯/ });
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  const nextNodeSelect = drawer.getByRole("combobox", { name: "指定流转目标节点" });
  await nextNodeSelect.click();
  await page.getByRole("option", { name: targetOptionName }).click();
  const advanced = waitForPost(
    page,
    `/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/advance`,
  );
  await drawer.getByRole("button", { name: "完成当前节点并推进" }).click();
  const advancedResponse = await advanced;
  await expectHttpOk(advancedResponse, "临床用户前台标准推进患者路径");
  const advancedData = await responseData(advancedResponse);
  expect(textField(advancedData, "nextNodeCode")).toBe(nextNodeCode);
  apiEvidence.standardAdvanced = true;
  await drawer.getByRole("button", { name: "Close" }).click();
  await expect(drawer).toBeHidden({ timeout: 20_000 });
  return advancedData;
}

async function recordVarianceAndCompleteFromApi(
  page: Page,
  patientPathwayId: string,
  snapshot: ContextSnapshotSummary,
  apiEvidence: PathwayLifecycleApiEvidence,
) {
  const response = await postApi(
    page,
    `/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/advance`,
    {
      ...(await pathwayApiContext(page)),
      triggerPoint: PATHWAY_RUNTIME_TRIGGER,
      eventType: "VARIANCE",
      currentNodeCode: "ASSESS",
      requestedNextNodeCode: null,
      snapshotId: snapshot.snapshotId,
      varianceType: "CLINICAL",
      varianceReasonCode: "CLINICAL_ESCALATION",
      varianceReason: "医师确认患者氧合波动，先暂停在医嘱套餐节点并记录偏离事实。",
      responsibleRole: "主管医师",
      resolutionDecision: "HOLD",
      resolutionAction: "暂停在医嘱套餐节点，完成复查医嘱确认后再继续路径。",
      eventId: `e2e-s6-variance-${randomUUID()}`,
    },
  );
  await expectOk(response, "真实后端登记路径变异并暂停在医嘱套餐节点");
  const data = await responseData(response);
  expect(textField(data, "status")).toBe("VARIANCE");
  expect(textField(data, "nextNodeCode")).toBe("ASSESS");
  expect(textField(data, "varianceId"), "路径变异推进响应必须返回 varianceId").toBeTruthy();
  apiEvidence.varianceRecorded = true;
  return data;
}

async function completeFollowupNodeFromApi(
  page: Page,
  patientPathwayId: string,
  apiEvidence: PathwayLifecycleApiEvidence,
) {
  const response = await postApi(
    page,
    `/engine/pathway/patient-pathways/${encodeURIComponent(patientPathwayId)}/advance`,
    {
      ...(await pathwayApiContext(page)),
      triggerPoint: PATHWAY_RUNTIME_TRIGGER,
      eventType: "COMPLETE",
      currentNodeCode: "FOLLOWUP",
      eventId: `e2e-s6-followup-complete-${randomUUID()}`,
    },
  );
  await expectOk(response, "真实后端完成终端随访节点并生成随访接续计划");
  const data = await responseData(response);
  expect(textField(data, "status")).toBe("COMPLETED");
  expect(textField(data, "followupPlanId"), "路径完成响应必须返回随访接续计划").toBeTruthy();
  apiEvidence.followupHandoffCreated = true;
  return data;
}

async function activateRuntimeWithPathway(
  page: Page,
  templateCode: string,
  orderSetCandidate: OrderSetCandidate,
): Promise<RuntimeReleaseDetail> {
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

  const hospitals = await getApi(
    page,
    "/engine/org/org-units?keyword=本地上线演练医院&page=1&size=20",
  );
  await expectOk(hospitals, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(hospitals)).find(
    (item) => textField(item, "name") === "本地上线演练医院" || textField(item, "code") === "e2e-rehearsal-hospital",
  );
  const hospitalId = requireText(textField(hospital, "id"), "必须找到本地上线演练医院");

  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const currentRuntime = await responseData(current);
  const currentReleaseId = textField(currentRuntime, "release.releaseId");
  const currentPlatformBaselineReleaseId = textField(
    currentRuntime,
    "release.platformBaselineReleaseId",
  );

  const candidateResponse = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-candidates?assetType=PATHWAY&keyword=${encodeURIComponent(templateCode)}&page=1&size=20`,
  );
  await expectOk(candidateResponse, "读取本轮 PATHWAY runtime 候选");
  const pathwayCandidate = pageItems(await responseData(candidateResponse)).find(
    (item) => textField(item, "assetType") === "PATHWAY" && textField(item, "assetIdentity") === templateCode,
  );
  const pathwayVersionId = requireText(
    textField(pathwayCandidate, "versionId"),
    `本轮 PATHWAY 候选 ${templateCode} 必须存在`,
  );

  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    {
      assetType: "PATHWAY",
      assetIdentity: templateCode,
      versionId: pathwayVersionId,
    },
    {
      assetType: "ORDER_SET",
      assetIdentity: orderSetCandidate.assetIdentity,
      versionId: orderSetCandidate.versionId,
    },
  ]);
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases`,
    {
      platformBaselineReleaseId: baselineAssets.baselineReleaseId,
      expectedCurrentReleaseId: currentReleaseId,
      confirmedPlatformUpgradeDigest:
        currentReleaseId &&
        currentPlatformBaselineReleaseId &&
        currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
          ? await readPlatformUpgradeAnalysisDigest(
              page,
              hospitalId,
              baselineAssets.baselineReleaseId,
            )
          : null,
      activeAssets,
    },
  );
  await expectOk(activated, "激活包含本轮 PATHWAY 的医院生效版本");
  const activatedRelease = await responseData(activated);
  const releaseId = requireText(
    textField(activatedRelease, "releaseId"),
    "激活机构生效版本必须返回 releaseId",
  );
  const currentAfterActivation = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfterActivation, "回读包含本轮 PATHWAY 和 ORDER_SET 的医院生效版本");
  const detail = (await responseData(currentAfterActivation)) as RuntimeReleaseDetail;
  expect(textField(detail, "release.releaseId")).toBe(releaseId);
  return detail;
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
  await expectOk(response, "读取 PATHWAY 演练平台升级分析");
  const digest = textField(await responseData(response), "analysisDigest");
  return requireText(digest, "PATHWAY 演练平台升级分析必须返回摘要");
}

function assertRuntimeContainsOrderSetAsset(
  runtime: RuntimeReleaseDetail,
  candidate: OrderSetCandidate,
): RuntimeReleaseItem {
  const asset = (runtime.items ?? []).find(
    (item) =>
      item.assetType === "ORDER_SET" &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(asset, `机构生效版本必须包含本轮 ORDER_SET ${candidate.assetIdentity}`).toBeTruthy();
  expect(asset?.versionNo, "ORDER_SET runtime 清单必须返回版本号").toBe(candidate.versionNo);
  expect(asset?.contentHash, "ORDER_SET runtime 清单必须返回正文 hash").toBe(candidate.contentHash);
  return asset as RuntimeReleaseItem;
}

function assertOrderSetRuntimeConsumerEvidence(options: {
  activatedRuntime: RuntimeReleaseDetail;
  runtimeOrderSetAsset: RuntimeReleaseItem;
  orderSetCandidate: OrderSetCandidate;
  patientPathway: unknown;
  orderSetAdvance: unknown;
  patientPathwayId: string;
}): OrderSetRuntimeConsumerEvidence {
  const runtimeReleaseId = requireText(
    textField(options.activatedRuntime, "release.releaseId"),
    "激活响应必须返回机构生效版本 ID",
  );
  expect(textField(options.patientPathway, "patientPathway.runtimeReleaseId")).toBe(runtimeReleaseId);
  const decisionEvidence = recordField(
    options.orderSetAdvance,
    "decisionEvidence",
  ) as Record<string, unknown> | undefined;
  expect(decisionEvidence, "路径推进响应必须返回 decisionEvidence").toBeTruthy();
  expect(textField(options.orderSetAdvance, "previousNodeCode")).toBe("ASSESS");
  expect(textField(options.orderSetAdvance, "nextNodeCode")).toBe("FOLLOWUP");
  expect(textField(options.orderSetAdvance, "status")).toBe("NODE_EXECUTING");
  expect(decisionEvidence?.["pathway.currentNodeType"]).toBe("ORDER_SET");
  expect(decisionEvidence?.["pathway.orderSetRef"]).toBe(options.orderSetCandidate.assetIdentity);
  expect(decisionEvidence?.["pathway.orderSetVersion"]).toBe(
    options.runtimeOrderSetAsset.versionNo,
  );
  expect(decisionEvidence?.["pathway.orderSetHash"]).toBe(
    options.runtimeOrderSetAsset.contentHash,
  );
  expect(decisionEvidence?.["pathway.orderSetRequiresPhysicianConfirmation"]).toBe(true);
  expect(decisionEvidence?.["pathway.orderSetItemCount"]).toBeGreaterThanOrEqual(1);
  const orderSetItems = Array.isArray(decisionEvidence?.["pathway.orderSetItems"])
    ? decisionEvidence["pathway.orderSetItems"]
    : [];
  expect(orderSetItems.length).toBe(
    decisionEvidence?.["pathway.orderSetItemCount"],
  );
  return {
    asset: {
      assetType: "ORDER_SET",
      assetIdentity: options.orderSetCandidate.assetIdentity,
      versionId: options.orderSetCandidate.versionId,
      versionNo: requireText(options.runtimeOrderSetAsset.versionNo ?? null, "ORDER_SET 版本号"),
      contentHash: requireText(options.runtimeOrderSetAsset.contentHash ?? null, "ORDER_SET hash"),
    },
    runtimeRelease: {
      releaseId: runtimeReleaseId,
      revisionNo: numberField(options.activatedRuntime, "release.revisionNo"),
      manifestSha256: textField(options.activatedRuntime, "release.manifestSha256") ?? undefined,
      assetPresent: true,
      assets: [options.runtimeOrderSetAsset],
    },
    patientPathway: {
      patientPathwayId: options.patientPathwayId,
      runtimeReleaseId,
    },
    advanceResponse: {
      previousNodeCode: textField(options.orderSetAdvance, "previousNodeCode"),
      nextNodeCode: textField(options.orderSetAdvance, "nextNodeCode"),
      status: textField(options.orderSetAdvance, "status"),
      decisionEvidence: decisionEvidence ?? {},
    },
  };
}

function pathwayTemplateRequest(
  templateCode: string,
  templateName: string,
  orderSetRef: string,
) {
  const dslPayload = pathwayDslPayload(orderSetRef);
  return {
    ...dslPayload,
    templateCode,
    name: templateName,
    diseaseCode: "COPD-S6",
    templateLevel: "HOSPITAL",
    entryMode: "MANUAL_CONFIRM",
    startNodeCode: "SCREEN",
    sourceRef: "local-e2e:pathway-lifecycle-frontdesk",
    description: "S6 专病路径生命周期真实前台与服务链路演练路径。",
    entryCriteria: { all: [{ fact: "patient.mpi", operator: "exists" }] },
    exitCriteria: { all: [{ fact: "patient.mpi", operator: "exists" }] },
  };
}

function pathwayDslPayload(orderSetRef: string) {
  return {
    startNodeCode: "SCREEN",
    milestones: [
      milestone("SCREENING_TRIAGE", "筛查分诊", "M-SCREEN", "筛查分诊完成", 0, 120, 10),
      milestone("DIAGNOSIS_DIFFERENTIAL", "诊断鉴别", "M-DIAG", "诊断鉴别完成", 0, 240, 20),
      milestone("RISK_STRATIFICATION", "风险分层", "M-RISK", "风险分层完成", 1, 360, 30),
      milestone("TREATMENT_DECISION", "治疗决策", "M-TREAT", "治疗决策完成", 1, 480, 40),
      milestone("EXECUTION_CANDIDATE", "执行候选", "M-EXEC", "执行候选确认", 2, 720, 50),
      milestone("MONITORING_WARNING", "监测预警", "M-MONITOR", "监测预警处置", 3, 1440, 60),
      milestone("DISCHARGE_REFERRAL", "出院转诊", "M-DISCHARGE", "出院转诊准备", 5, 2880, 70),
      milestone(
        "REHAB_EDUCATION_FOLLOWUP",
        "康复宣教随访",
        "M-FOLLOWUP",
        "康复宣教随访接续",
        7,
        4320,
        80,
      ),
      milestone("OUTCOME_EVALUATION", "结局评价", "M-OUTCOME", "结局评价完成", 14, 10080, 90),
      milestone("QUALITY_ITERATION", "质量迭代", "M-QUALITY", "质量迭代完成", 30, 20160, 100),
    ],
    nodes: [
      node("SCREEN", "筛查分诊", "SCREENING", "M-SCREEN", 10, false, 120),
      node("ASSESS", "风险评估与复查医嘱建议", "ORDER_SET", "M-RISK", 20, false, 240, {
        orderSetRef,
        visibleSummary: "医师确认后在 HIS 线下处理复查医嘱，系统不自动开嘱。",
      }),
      node("FOLLOWUP", "随访接续", "FOLLOWUP", "M-FOLLOWUP", 30, true, 4320),
    ],
    edges: [
      {
        edgeCode: "EDGE.SCREEN.ASSESS",
        fromNodeCode: "SCREEN",
        toNodeCode: "ASSESS",
        edgeType: "DEFAULT",
        priority: 10,
      },
      {
        edgeCode: "EDGE.ASSESS.FOLLOWUP",
        fromNodeCode: "ASSESS",
        toNodeCode: "FOLLOWUP",
        edgeType: "DEFAULT",
        priority: 20,
      },
    ],
    metricBindings: [
      { nodeCode: "SCREEN", metricCode: "COPD.TIME_TO_SCREEN", required: true },
      { nodeCode: "ASSESS", metricCode: "COPD.TIME_TO_ASSESS", required: true },
      { nodeCode: "FOLLOWUP", metricCode: "COPD.TIME_TO_FOLLOWUP", required: true },
    ],
  };
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
  extraConfig: Record<string, unknown> = {},
) {
  return {
    nodeCode,
    name,
    nodeType,
    milestoneCode,
    sortOrder,
    responsibleRole: terminal ? "随访护士" : "主管医师",
    accountableRole: "主管医师",
    consultedRoles: ["药师", "护理"],
    informedRoles: ["质控员"],
    timeWindowMinutes,
    terminal,
    config: {
      visibleSummary: name,
      ...extraConfig,
      clockSla: {
        baselineEvent: sortOrder === 10 ? "PATHWAY_ENTRY" : "NODE_START",
        targetMinutes: timeWindowMinutes,
        maxMinutes: timeWindowMinutes * 2,
      },
    },
  };
}

function assertTenStagePathwayDetail(value: unknown, templateCode: string) {
  expect(textField(value, "template.templateCode")).toBe(templateCode);
  const milestones = arrayField(value, "milestones");
  expect(milestones.map((item) => textField(item, "phaseCode"))).toEqual(specialDiseaseStages);
  expect(arrayField(value, "nodes").map((item) => textField(item, "nodeCode"))).toEqual([
    "SCREEN",
    "ASSESS",
    "FOLLOWUP",
  ]);
  expect(arrayField(value, "edges").map((item) => textField(item, "edgeCode"))).toEqual([
    "EDGE.SCREEN.ASSESS",
    "EDGE.ASSESS.FOLLOWUP",
  ]);
}

async function selectSnapshotByBackendVerifiedFilter(
  page: Page,
  scope: ReturnType<Page["getByRole"]>,
  snapshot: ContextSnapshotSummary,
) {
  const snapshots = await getApi(
    page,
    `/engine/context/snapshots?patientId=${encodeURIComponent(
      snapshot.patientId,
    )}&encounterId=${encodeURIComponent(snapshot.encounterId ?? "")}&status=ACTIVE&page=1&size=20`,
  );
  await expectOk(snapshots, "回读本轮 ACTIVE 快照候选");
  const matched = pageItems(await responseData(snapshots)).find(
    (item) => textField(item, "snapshotId") === snapshot.snapshotId,
  );
  expect(matched, `页面过滤前必须能回读本轮 ACTIVE 快照 ${snapshot.snapshotId}`).toBeTruthy();
  const evidenceButton = scope.getByRole("button", { name: `选择 ${snapshot.snapshotId}` });
  if (await evidenceButton.isVisible().catch(() => false)) {
    await evidenceButton.click();
    return;
  }
  const createdAtText = formatClinicalDateTimeForE2e(
    textField(matched, "createdAt") ?? snapshot.createdAt,
  );
  if (createdAtText) {
    const createdAtButton = scope.getByRole("button", {
      name: `选择 ${createdAtText} 建立的临床快照`,
    });
    if (await createdAtButton.isVisible().catch(() => false)) {
      await createdAtButton.click();
      return;
    }
  }
  const snapshotText = scope.getByText(snapshot.snapshotId);
  if (await snapshotText.isVisible().catch(() => false)) {
    await snapshotText.click();
    return;
  }
  const selectableButtons = scope.getByRole("button", { name: /^选择$/ });
  const accessibleSelectCount = await selectableButtons.count();
  if (accessibleSelectCount === 1) {
    await selectableButtons.first().click();
    return;
  }
  const textSelectButtons = scope.locator("button").filter({ hasText: /^选择$/ });
  const textSelectCount = await textSelectButtons.count();
  if (textSelectCount === 1) {
    await textSelectButtons.first().click();
    return;
  }
  const snapshotButtons = scope.locator("button").filter({ hasText: "临床快照" });
  const snapshotButtonCount = await snapshotButtons.count();
  expect(
    snapshotButtonCount,
    `本轮患者/就诊过滤后必须只展示一个可选 ACTIVE 快照 ${snapshot.snapshotId}`,
  ).toBe(1);
  await snapshotButtons.first().click();
}

function formatClinicalDateTimeForE2e(value: string | null | undefined) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const parts = new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23",
  })
    .formatToParts(date)
    .reduce<Record<string, string>>((acc, part) => {
      if (part.type !== "literal") {
        acc[part.type] = part.value;
      }
      return acc;
    }, {});
  return `${parts.year}年${parts.month}月${parts.day}日 ${parts.hour}:${parts.minute}:${parts.second}`;
}

async function chooseDialogOption(
  dialog: ReturnType<Page["getByRole"]>,
  label: string,
  optionText: string,
) {
  if ((await dialog.getByTitle(optionText).count()) > 0) {
    return;
  }
  const combobox = dialog.getByRole("combobox", { name: label });
  await combobox.locator("xpath=ancestor::*[contains(@class, 'ant-select')][1]").click();
  await dialog.page().getByRole("option", { name: optionText }).click();
}

async function getApi(page: Page, path: string) {
  return page.request.get(`${apiBase}${path}`, {
    headers: { "X-Trace-Id": `e2e-pathway-get-${Date.now()}` },
  });
}

async function pathwayApiContext(page: Page) {
  const profileResponse = await getApi(page, "/security/me");
  await expectOk(profileResponse as APIResponse, "读取当前用户安全画像生成路径标准上下文");
  const profile = objectValue(await responseData(profileResponse));
  const dataScope = objectValue(profile?.dataScope);
  const roles = arrayField(profile, "roles");
  const tenantId = textField(dataScope, "tenantId");
  const userId = textField(profile, "userId");
  const roleCodes = roles.map((role) => textField(role, "code")).filter((role): role is string =>
    Boolean(role),
  );
  if (!tenantId || !userId || roleCodes.length === 0) {
    throw new Error("当前用户安全画像缺少路径标准上下文字段");
  }
  const traceId = `e2e-pathway-${randomUUID()}`;
  return {
    request_id: `req-${traceId}`,
    trace_id: traceId,
    tenant_id: tenantId,
    group_id: textField(dataScope, "groupId"),
    hospital_id: textField(dataScope, "hospitalId"),
    campus_id: textField(dataScope, "campusId"),
    site_id: textField(dataScope, "siteId"),
    department_id: textField(dataScope, "departmentId"),
    specialty_id: textField(dataScope, "specialtyId"),
    user_id: userId,
    role_codes: roleCodes,
  };
}

async function responseData(response: JsonHttpResponse) {
  const body = (await response.json()) as { data?: unknown };
  return body.data ?? null;
}

async function expectHttpOk(response: JsonHttpResponse, label: string) {
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`${label} 失败：${response.status()} ${body}`);
  }
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

async function clickDialogConfirm(dialog: ReturnType<Page["getByRole"]>) {
  const confirm = dialog.getByRole("button", { name: /OK|确 定|确定/u });
  await expect(confirm).toBeVisible();
  await confirm.click();
}

function resolveBaselineRuntimeAssets(value: unknown) {
  const baselineReleaseId = textField(value, "release.baselineReleaseId");
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

function pageItems(value: unknown) {
  const items = recordField(value, "items");
  return Array.isArray(items) ? items : [];
}

function arrayData(value: unknown) {
  return Array.isArray(value) ? value : pageItems(value);
}

function arrayField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return Array.isArray(raw) ? raw : [];
}

function objectValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function recordField(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, segment) => {
    if (Array.isArray(current) && /^\d+$/.test(segment)) {
      return current[Number(segment)];
    }
    if (!current || typeof current !== "object" || Array.isArray(current)) {
      return undefined;
    }
    return (current as Record<string, unknown>)[segment];
  }, value);
}

function textField(value: unknown, path: string): string | null {
  const raw = recordField(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberField(value: unknown, path: string): number | undefined {
  const raw = recordField(value, path);
  return typeof raw === "number" ? raw : undefined;
}

function requireText(value: string | null, label: string) {
  expect(value, label).toBeTruthy();
  return value ?? "";
}

function recordPathwayLifecycleStage(observedStages: Set<string>, stage: string) {
  observedStages.add(stage);
}

async function attachPathwayLifecycleScenarioEvidence(
  testInfo: TestInfo,
  observedStageSet: Set<string>,
  apiEvidence: PathwayLifecycleApiEvidence,
  context: Record<string, unknown> & {
    orderSetRuntimeConsumer: OrderSetRuntimeConsumerEvidence;
  },
) {
  const scenarioEvidence = requiredPathwayLifecycleScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages =
        requiredPathwayLifecycleScenarioEvidence.find((item) => item.code === scenario.code)
          ?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  await testInfo.attach("pathway-lifecycle-scenario-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: completedScenarioCodes,
        productLayers: ["CLINICAL_EXECUTION"],
        versionedAssets: ["ORDER_SET"],
        serviceCombinations: ["SPECIAL_DISEASE_PATHWAY"],
        specialDiseaseStages,
        apiEvidence,
        orderSetRuntimeConsumer: context.orderSetRuntimeConsumer,
        context,
        scenarioEvidence,
      },
      null,
      2,
    ),
    contentType: "application/json",
  });
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
