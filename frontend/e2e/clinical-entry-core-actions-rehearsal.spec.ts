import { expect, test, type Locator, type Page } from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  recordField,
  responseData,
  textField,
} from "./support/auth";
import {
  attachClinicalEntryCoreActionEvidence,
  type ClinicalEntryCoreActionEvidence,
} from "./support/clinicalEntryCoreActions";
import { ensureDiagnosticCriticalValueRuntime } from "./support/diagnosticRuntime";

type ContextSnapshotSummary = {
  patientId: string;
  maskedName: string;
  idLast4: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
  createdAt: string | null;
};

type ReportInterpretationPayload = {
  runtimeReleaseId?: string;
  interpretations?: Array<{
    itemCode?: string;
    reportType?: string;
    sourceVersionId?: number;
    versionNo?: string;
  }>;
};

type RecommendationEvaluationPayload = {
  status?: string;
  triggerId?: string;
  traceId?: string;
  visibleCardCount?: number;
  suppressedCardCount?: number;
  cards?: Array<{ cardId?: string; sourceSummary?: string; explanationJson?: string }>;
};

const reportInterpretationKnowledgeIdentity = "plat:diagnostic_item:lab-potassium";

test.describe("临床协同入口核心动作真实前台演练", () => {
  test("MPI、患者路径、CDSS、协同任务和随访协同均完成真实前台代表动作", async ({
    page,
  }, testInfo) => {
    test.setTimeout(900_000);
    await page.setViewportSize({ width: 1440, height: 960 });

    await ensureReadySession(page, "engine-operator");
    const runtime = await ensureDiagnosticCriticalValueRuntime(
      page,
      `clinical-entry-${Date.now().toString(36)}`,
    );
    const followupTemplate = await preparePublishedFollowupTemplate(page);
    const currentRuntimeReleaseId = await currentHospitalRuntimeReleaseId(page);
    expect(currentRuntimeReleaseId, "发布随访方案后必须仍有当前机构生效版本").toBeTruthy();

    await ensureReadySession(page, "clinical-user");
    const { snapshot, action: mpiAction } = await createMpiContextSnapshot(
      page,
      currentRuntimeReleaseId,
    );
    const cdssAction = await evaluateRecommendationFromCdss(page, snapshot);
    const workflowAction = await completeReportInterpretationTodo(
      page,
      snapshot,
      currentRuntimeReleaseId,
    );
    const pathwayAction = await enterBaselinePathway(page, snapshot);
    const followupAction = await generateFollowupPlan(page, snapshot, followupTemplate);

    await attachClinicalEntryCoreActionEvidence(testInfo, [
      mpiAction,
      pathwayAction,
      cdssAction,
      workflowAction,
      followupAction,
    ]);
  });
});

async function createMpiContextSnapshot(
  page: Page,
  expectedRuntimeReleaseId: string,
): Promise<{ snapshot: ContextSnapshotSummary; action: ClinicalEntryCoreActionEvidence }> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "患者索引" }).first()).toBeVisible({
    timeout: 30_000,
  });

  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible({ timeout: 10_000 });
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `临*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = patientDialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("66");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);

  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  const patientText = await patientResponse.text();
  expect(
    patientResponse.ok(),
    `临床用户创建脱敏患者应返回成功 status=${patientResponse.status()} body=${patientText}`,
  ).toBe(true);
  const patient = JSON.parse(patientText) as { data?: { mpiId?: string } };
  const patientId = patient.data?.mpiId;
  expect(patientId, "创建脱敏患者后应返回患者身份").toBeTruthy();
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
  await expect(contextDialog).toBeVisible({ timeout: 10_000 });
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("临床协同入口矩阵真实演练主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog.getByLabel("医技报告项目").fill("血钾检验");
  await contextDialog.getByLabel("报告结论").fill("血钾 6.3 mmol/L，危急值，已复核");
  await contextDialog.getByLabel("异常重点").fill("血钾升高、危急值");
  await contextDialog
    .getByLabel("建立原因")
    .fill("临床协同入口矩阵：建立脱敏患者当前就诊上下文，不写入患者明文身份。");

  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  const contextText = await contextResponse.text();
  expect(
    contextResponse.ok(),
    `临床用户建立当前就诊上下文应返回成功 status=${contextResponse.status()} body=${contextText}`,
  ).toBe(true);
  const context = JSON.parse(contextText) as {
    data?: {
      snapshotId?: string;
      runtimeReleaseId?: string;
      createdAt?: string;
      resources?: { encounters?: Array<{ encounterId?: string }> };
    };
  };
  const snapshotId = context.data?.snapshotId;
  expect(snapshotId, "上下文创建响应应返回快照身份").toBeTruthy();
  expect(context.data?.runtimeReleaseId, "上下文必须绑定当前机构生效版本").toBe(
    expectedRuntimeReleaseId,
  );
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });

  const contextAuditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "context_snapshot",
    resourceId: snapshotId ?? "",
  });
  expect(contextAuditVerified, "建立上下文快照应产生真实审计事件").toBe(true);

  return {
    snapshot: {
      patientId: patientId ?? "",
      maskedName,
      idLast4,
      snapshotId: snapshotId ?? "",
      runtimeReleaseId: context.data?.runtimeReleaseId ?? "",
      encounterId: context.data?.resources?.encounters?.[0]?.encounterId ?? null,
      createdAt: context.data?.createdAt ?? null,
    },
    action: {
      menuKey: "mpi",
      role: "clinical-user",
      path: "/mpi",
      frontdeskAction: "临床用户前台创建脱敏患者、回读患者行并生成可审计上下文快照",
      serviceOperation:
        "POST /api/v1/engine/mpi/patients + POST /api/v1/engine/context/snapshots",
      serviceStatus: minSuccessfulStatus(patientResponse.status(), contextResponse.status()),
      readbackVerified: Boolean(patientId && snapshotId && context.data?.runtimeReleaseId),
      auditVerified: contextAuditVerified,
    },
  };
}

async function currentHospitalRuntimeReleaseId(page: Page) {
  await ensureReadySession(page, "clinical-user");
  const profileResponse = await getApi(page, "/security/me");
  await expectOk(profileResponse, "读取当前临床用户安全画像");
  const profile = await responseData(profileResponse);
  const hospitalId = textField(recordField(profile, "dataScope"), "hospitalId");
  expect(hospitalId, "当前临床用户安全画像必须包含医院 ID").toBeTruthy();

  await ensureReadySession(page, "engine-operator");
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId ?? "")}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const releaseId =
    textField(recordField(await responseData(current), "release"), "releaseId") ?? "";
  await ensureReadySession(page, "clinical-user");
  return releaseId;
}

async function preparePublishedFollowupTemplate(page: Page) {
  const suffix = Date.now().toString(36);
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "随访协同" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("tab", { name: "随访方案" }).click();
  const templateCode = `FUP.CLINICAL.ENTRY.${suffix.toUpperCase()}`;
  const templateDefaultName = "临床协同入口随访方案";
  const templateName = `${templateDefaultName} ${suffix}`;
  await page.getByRole("button", { name: /新建方案/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建随访方案" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("院内随访方案身份").fill(templateCode);
  await dialog.getByLabel("方案名称").fill(templateName);
  await dialog
    .getByLabel("方案说明")
    .fill("临床协同入口矩阵前置方案；不包含患者姓名、电话、住址、证件号等核心敏感信息。");
  await chooseDialogOption(page, dialog, "适用机构范围", "当前医院");
  await chooseDialogOption(page, dialog, "随访病种", "慢阻肺");
  await chooseDialogOption(page, dialog, "问卷内容", "慢病随访问卷");
  await chooseDialogOption(page, dialog, "核心随访问题", "呼吸困难变化");
  await dialog.getByLabel("异常触发条件").fill("呼吸困难加重、血氧下降或患者主动报告异常");
  await dialog.getByLabel("通知对象").fill("责任医生与随访护士");
  await chooseDialogOption(page, dialog, "院内依据", "慢病随访管理制度");

  const createResponsePromise = waitForPost(page, "/engine/followup/templates");
  await dialog.getByRole("button", { name: /创\s*建/ }).click();
  const createResponse = await createResponsePromise;
  const createText = await createResponse.text();
  expect(
    createResponse.ok(),
    `创建临床协同入口随访方案应返回成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: { templateId?: string; templateCode?: string; name?: string };
  };
  const templateId = created.data?.templateId;
  expect(templateId, "随访方案创建响应应返回方案身份").toBeTruthy();
  expect(created.data?.templateCode).toBe(templateCode);
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "随访协同" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("tab", { name: "随访方案" }).click();
  await page.getByPlaceholder("按方案名称或适用范围检索").fill(templateDefaultName);
  const row = page
    .getByRole("row", { name: new RegExp(escapeRegExp(templateDefaultName)) })
    .filter({ hasText: "待发布" })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  const publishResponsePromise = waitForPost(
    page,
    `/engine/followup/templates/${templateId}/publish`,
  );
  await row.getByRole("button", { name: "发布方案" }).click();
  const publishResponse = await publishResponsePromise;
  const publishText = await publishResponse.text();
  expect(
    publishResponse.ok(),
    `发布临床协同入口随访方案应返回成功 status=${publishResponse.status()} body=${publishText}`,
  ).toBe(true);
  const published = JSON.parse(publishText) as {
    data?: { assetStatus?: string; templateId?: string; name?: string };
  };
  expect(published.data?.templateId).toBe(templateId);
  expect(published.data?.assetStatus).toBe("PUBLISHED");
  await expect(
    page
      .getByRole("row", { name: new RegExp(escapeRegExp(templateDefaultName)) })
      .filter({ hasText: "可用于计划生成" })
      .first(),
  ).toBeVisible({ timeout: 20_000 });

  return {
    templateId: templateId ?? "",
    templateName: published.data?.name ?? created.data?.name ?? templateName,
    templateDefaultName,
  };
}

async function evaluateRecommendationFromCdss(
  page: Page,
  snapshot: ContextSnapshotSummary,
): Promise<ClinicalEntryCoreActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "提醒与推荐" }).first()).toBeVisible({
    timeout: 30_000,
  });

  await page.getByRole("button", { name: "登记触发评估" }).click();
  const dialog = page.getByRole("dialog", { name: "登记一次推荐触发评估" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  await selectSnapshotByBackendVerifiedFilter(page, dialog, snapshot);
  await chooseDialogOption(page, dialog, "触发时点", "开立用药");

  const evaluateResponsePromise = waitForPost(page, "/engine/recommendations:evaluate");
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  const evaluateText = await evaluateResponse.text();
  expect(
    evaluateResponse.ok(),
    `临床用户触发推荐评估应返回成功 status=${evaluateResponse.status()} body=${evaluateText}`,
  ).toBe(true);
  const evaluation = JSON.parse(evaluateText) as { data?: RecommendationEvaluationPayload };
  const triggerId = evaluation.data?.triggerId;
  expect(evaluation.data?.status).toBe("EVALUATED");
  expect(triggerId, "推荐评估响应应返回触发编号").toBeTruthy();
  expect(evaluation.data?.traceId, "推荐评估响应应返回追踪号").toBeTruthy();
  expect(evaluation.data?.visibleCardCount ?? 0, "推荐评估应产生可见提示卡").toBeGreaterThan(0);
  expect(evaluation.data?.suppressedCardCount ?? 0, "本次推荐评估不应被疲劳策略抑制").toBe(0);
  const card = evaluation.data?.cards?.[0];
  expect(card?.cardId, "推荐评估应返回提示卡编号").toBeTruthy();
  expect(card?.sourceSummary ?? "", "提示卡来源摘要应包含运行版本").toContain("运行版本=");
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "recommendation_trigger",
    resourceId: triggerId ?? "",
  });
  expect(auditVerified, "推荐触发应产生真实审计事件").toBe(true);
  await ensureReadySession(page, "clinical-user");

  return {
    menuKey: "cdss-fatigue",
    role: "clinical-user",
    path: "/cdss/fatigue",
    frontdeskAction: "临床用户前台选择本轮临床快照并触发推荐评估",
    serviceOperation: "POST /api/v1/engine/recommendations:evaluate",
    serviceStatus: evaluateResponse.status(),
    readbackVerified: Boolean(triggerId && card?.cardId),
    auditVerified,
  };
}

async function completeReportInterpretationTodo(
  page: Page,
  snapshot: ContextSnapshotSummary,
  expectedRuntimeReleaseId: string,
): Promise<ClinicalEntryCoreActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath(`/cdss/fatigue?patientId=${encodeURIComponent(snapshot.patientId)}`), {
    waitUntil: "domcontentloaded",
  });
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("button", { name: "生成报告解读" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "生成报告解读" }).click();
  const dialog = page.getByRole("dialog", { name: "生成医技报告解读" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  if (await dialog.getByLabel("患者信息").isVisible().catch(() => false)) {
    await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  }
  if (snapshot.encounterId && (await dialog.getByLabel("就诊信息").isVisible().catch(() => false))) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  await selectSnapshotByBackendVerifiedFilter(page, dialog, snapshot);

  const interpretResponsePromise = waitForPost(page, "/engine/recommendations/report-interpretation");
  await dialog.getByRole("button", { name: "生成报告解读" }).click();
  const interpretResponse = await interpretResponsePromise;
  const interpretText = await interpretResponse.text();
  expect(
    interpretResponse.ok(),
    `临床用户生成报告解读应返回成功 status=${interpretResponse.status()} body=${interpretText}`,
  ).toBe(true);
  const interpretation = JSON.parse(interpretText) as { data?: ReportInterpretationPayload };
  expect(interpretation.data?.runtimeReleaseId).toBe(expectedRuntimeReleaseId);
  const interpretedReportType = interpretation.data?.interpretations?.[0]?.reportType ?? "血钾检验";
  const knowledgeItem = interpretation.data?.interpretations?.find(
    (item) =>
      item.itemCode === reportInterpretationKnowledgeIdentity &&
      Boolean(item.sourceVersionId) &&
      Boolean(item.versionNo),
  );
  expect(knowledgeItem, "报告解读应消费当前机构生效版本知识资产").toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await page.goto(appPath("/workflow/todos"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "协同任务" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await choosePageSelectOption(page, "待办来源", "报告解读");
  const todoRow = page
    .getByRole("row")
    .filter({ hasText: "报告解读" })
    .filter({ hasText: interpretedReportType })
    .first();
  await expect(todoRow, "临床用户应能定位本轮报告解读协同待办").toBeVisible({
    timeout: 30_000,
  });

  const completeResponsePromise = waitForPost(page, "/engine/workflow/todos/");
  await todoRow.getByRole("button", { name: "完成" }).click();
  const completeDialog = page.getByRole("dialog", { name: "完成待办" });
  await expect(completeDialog).toBeVisible({ timeout: 10_000 });
  await completeDialog
    .getByLabel("完成说明")
    .fill("临床协同入口矩阵：临床已人工复核报告解读，不改写已签发报告。");
  await completeDialog.getByRole("button", { name: "确认完成" }).click();
  const completeResponse = await completeResponsePromise;
  const completeText = await completeResponse.text();
  expect(
    completeResponse.ok(),
    `临床用户完成报告解读待办应返回成功 status=${completeResponse.status()} body=${completeText}`,
  ).toBe(true);
  const completedTodo = JSON.parse(completeText) as {
    data?: { todoId?: string; status?: string; completionReason?: string };
  };
  const todoId = completedTodo.data?.todoId;
  expect(todoId, "完成待办响应应返回 todoId").toBeTruthy();
  expect(completedTodo.data?.status).toBe("COMPLETED");
  await expect(completeDialog).toBeHidden({ timeout: 20_000 });

  const completedTodosResponsePromise = waitForGet(page, "/engine/workflow/todos");
  await choosePageSelectOption(page, "待办状态", "已完成");
  const completedTodosResponse = await completedTodosResponsePromise;
  expect(completedTodosResponse.ok()).toBe(true);
  const completedTodos = JSON.parse(await completedTodosResponse.text()) as {
    data?: { items?: Array<{ todoId?: string; status?: string }> };
  };
  expect(
    completedTodos.data?.items?.some((item) => item.todoId === todoId && item.status === "COMPLETED"),
    "已完成筛选应回读本轮完成待办",
  ).toBe(true);

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "workflow_todo",
    resourceId: todoId ?? "",
  });
  expect(auditVerified, "临床用户完成待办应产生真实审计事件").toBe(true);
  await ensureReadySession(page, "clinical-user");

  return {
    menuKey: "workflow-todos",
    role: "clinical-user",
    path: "/workflow/todos",
    frontdeskAction: "临床用户前台筛选报告解读待办、填写完成说明并回读已完成状态",
    serviceOperation: "POST /api/v1/engine/workflow/todos/{todoId}/complete",
    serviceStatus: completeResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function enterBaselinePathway(
  page: Page,
  snapshot: ContextSnapshotSummary,
): Promise<ClinicalEntryCoreActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/pathway/patients"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者路径" })).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "办理患者入径" }).click();
  const dialog = page.getByRole("dialog", { name: "办理患者临床路径准入" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
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
  const baselineOption = page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)")
    .last()
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: /本地上线演练基础路径|PATHWAY\.LOCAL\.REHEARSAL\.BASELINE/u })
    .first();
  await expect(baselineOption).toBeVisible({ timeout: 20_000 });
  await baselineOption.click();

  const enteredResponsePromise = waitForPost(page, "/engine/pathway/patient-pathways/enter");
  await clickDialogConfirm(dialog);
  const enteredResponse = await enteredResponsePromise;
  const enteredText = await enteredResponse.text();
  expect(
    enteredResponse.ok(),
    `临床用户办理患者入径应返回成功 status=${enteredResponse.status()} body=${enteredText}`,
  ).toBe(true);
  const entered = JSON.parse(enteredText) as {
    data?: {
      patientPathwayId?: string;
      runtimeReleaseId?: string;
      patientPathway?: { patientPathwayId?: string; runtimeReleaseId?: string; status?: string };
    };
  };
  const patientPathwayId =
    entered.data?.patientPathwayId ?? entered.data?.patientPathway?.patientPathwayId;
  const runtimeReleaseId =
    entered.data?.runtimeReleaseId ?? entered.data?.patientPathway?.runtimeReleaseId;
  expect(patientPathwayId, "入径响应应返回患者路径身份").toBeTruthy();
  expect(runtimeReleaseId, "入径必须绑定当前快照的机构生效版本").toBe(snapshot.runtimeReleaseId);
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const readback = await getApi(
    page,
    `/engine/pathway/patient-pathways?patientId=${encodeURIComponent(
      snapshot.patientId,
    )}&page=1&size=20`,
  );
  await expectOk(readback, "回读本轮患者路径实例");
  const found = pageItems(await responseData(readback)).some(
    (item) => textField(item, "patientPathwayId") === patientPathwayId,
  );
  expect(found, "患者路径列表应回读本轮入径实例").toBe(true);

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "patient_pathway",
    resourceId: patientPathwayId ?? "",
  });
  expect(auditVerified, "患者入径应产生真实审计事件").toBe(true);
  await ensureReadySession(page, "clinical-user");

  return {
    menuKey: "patient-pathways",
    role: "clinical-user",
    path: "/pathway/patients",
    frontdeskAction: "临床用户前台基于本轮快照办理患者入径并回读患者路径实例",
    serviceOperation: "POST /api/v1/engine/pathway/patient-pathways/enter",
    serviceStatus: enteredResponse.status(),
    readbackVerified: found,
    auditVerified,
  };
}

async function generateFollowupPlan(
  page: Page,
  snapshot: ContextSnapshotSummary,
  template: { templateId: string; templateName: string; templateDefaultName: string },
): Promise<ClinicalEntryCoreActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "随访协同" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "生成随访计划" }).click();
  const dialog = page.getByRole("dialog", { name: "生成随访计划" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("随访快照患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("随访快照就诊信息").fill(snapshot.encounterId);
  }
  await selectSnapshotByBackendVerifiedFilter(page, dialog, snapshot, "随访上下文快照");
  await chooseDialogOption(page, dialog, "随访风险分层", "中风险");
  await searchDialogOption(page, dialog, "随访方案", template.templateDefaultName);

  const planResponsePromise = waitForPost(page, "/engine/followup/plans/generate");
  await dialog.getByRole("button", { name: /生\s*成/ }).click();
  const planResponse = await planResponsePromise;
  const planText = await planResponse.text();
  expect(
    planResponse.ok(),
    `临床用户生成随访计划应返回成功 status=${planResponse.status()} body=${planText}`,
  ).toBe(true);
  const plan = JSON.parse(planText) as {
    data?: { planId?: string; templateId?: string; runtimeReleaseId?: string; tasks?: Array<unknown> };
  };
  const planId = plan.data?.planId;
  expect(planId, "随访计划生成响应应返回计划身份").toBeTruthy();
  expect(plan.data?.templateId, "随访计划必须绑定本轮已发布随访方案").toBe(template.templateId);
  expect(plan.data?.runtimeReleaseId, "随访计划必须绑定当前快照的机构生效版本").toBe(
    snapshot.runtimeReleaseId,
  );
  expect(plan.data?.tasks?.length ?? 0, "随访计划应生成可办理任务").toBeGreaterThan(0);
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await closeFollowupPlanDrawerIfOpen(page);
  await page.getByPlaceholder("按患者线索检索").fill(snapshot.patientId);
  const listResponsePromise = waitForGet(page, "/engine/followup/plans");
  await page.getByRole("button", { name: /查\s*询/ }).click();
  const listResponse = await listResponsePromise;
  expect(listResponse.ok(), "按本次患者线索查询随访计划应返回成功").toBe(true);
  const list = JSON.parse(await listResponse.text()) as {
    data?: { items?: Array<{ planId?: string }> };
  };
  const found = list.data?.items?.some((item) => item.planId === planId) ?? false;
  expect(found, "随访计划列表应回读本轮计划").toBe(true);

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "followup_plan",
    resourceId: planId ?? "",
  });
  expect(auditVerified, "生成随访计划应产生真实审计事件").toBe(true);
  await ensureReadySession(page, "clinical-user");

  return {
    menuKey: "clinical-followup",
    role: "clinical-user",
    path: "/clinical/followup",
    frontdeskAction: "临床用户前台基于本轮快照选择已发布随访方案、生成随访计划并回读计划列表",
    serviceOperation: "POST /api/v1/engine/followup/plans/generate",
    serviceStatus: planResponse.status(),
    readbackVerified: found,
    auditVerified,
  };
}

async function closeFollowupPlanDrawerIfOpen(page: Page) {
  const drawer = page.getByRole("dialog", { name: "随访计划办理" });
  if (!(await drawer.isVisible({ timeout: 1_000 }).catch(() => false))) {
    return;
  }
  await drawer.getByRole("button", { name: /close/i }).click();
  await expect(drawer).toBeHidden({ timeout: 10_000 });
}

async function selectSnapshotByBackendVerifiedFilter(
  page: Page,
  scope: Locator,
  snapshot: ContextSnapshotSummary,
  noun = "临床快照",
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
      name: `选择 ${createdAtText} 建立的${noun}`,
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
  if ((await selectableButtons.count()) === 1) {
    await selectableButtons.first().click();
    return;
  }
  const snapshotButtons = scope.locator("button").filter({ hasText: noun });
  const snapshotButtonCount = await snapshotButtons.count();
  expect(
    snapshotButtonCount,
    `本轮患者/就诊过滤后必须只展示一个可选 ACTIVE 快照 ${snapshot.snapshotId}`,
  ).toBe(1);
  await snapshotButtons.first().click();
}

async function auditEventExistsAsAuditor(
  page: Page,
  options: { resourceType: string; resourceId: string },
) {
  if (!options.resourceId) return false;
  await ensureReadySession(page, "auditor");
  return expect
    .poll(
      async () => {
        const response = await getApi(
          page,
          `/large-lists/audit-events/list?resourceType=${encodeURIComponent(
            options.resourceType,
          )}&size=50`,
        );
        await expectOk(response, `回读审计事件 ${options.resourceType}/${options.resourceId}`);
        const data = await responseData(response);
        return pageItems(data).some(
          (item) =>
            textField(item, "resourceType") === options.resourceType &&
            textField(item, "resourceId") === options.resourceId,
        );
      },
      {
        message: `等待审计事件 ${options.resourceType}/${options.resourceId}`,
        timeout: 15_000,
        intervals: [500, 1_000, 2_000],
      },
    )
    .toBe(true)
    .then(() => true);
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const selectedText = await currentSelectText(select);
  if (selectedText === optionText) return;
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function choosePageSelectOption(page: Page, label: string, optionText: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const selectedText = await currentSelectText(select);
  if (selectedText === optionText) return;
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function searchDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  searchText: string,
) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  await combobox.fill(searchText);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(escapeRegExp(searchText)) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function currentSelectText(select: Locator) {
  const selected = select.locator(".ant-select-selection-item").first();
  if ((await selected.count()) === 0) return "";
  const title = await selected.getAttribute("title", { timeout: 1_000 }).catch(() => null);
  if (title) return title.trim();
  const text = await selected.textContent({ timeout: 1_000 }).catch(() => null);
  return text?.trim() ?? "";
}

async function clickDialogConfirm(dialog: Locator) {
  const confirm = dialog.getByRole("button", { name: /OK|确 定|确定/u });
  await expect(confirm).toBeEnabled({ timeout: 10_000 });
  await confirm.click();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 60_000 },
  );
}

function waitForGet(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "GET" && response.url().includes(path),
    { timeout: 30_000 },
  );
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

function minSuccessfulStatus(...statuses: number[]) {
  const failed = statuses.find((status) => status < 200 || status >= 300);
  return failed ?? Math.min(...statuses);
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
