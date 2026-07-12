import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  responseData,
  type RoleAccount,
} from "./support/auth";
import { ensureDiagnosticCriticalValueRuntime } from "./support/diagnosticRuntime";

type RoleActionEvidence = {
  role: RoleAccount;
  path: string;
  frontdeskAction: string;
  serviceOperation: string;
  serviceStatus: number;
  readbackVerified: boolean;
  auditVerified: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  runtimeReleaseId: string;
  encounterId: string | null;
};

type ReportInterpretationPayload = {
  runtimeReleaseId?: string;
  recommendationCardIds?: string[];
  interpretations?: Array<{
    itemCode?: string;
    reportType?: string;
    sourceVersionId?: number;
    versionNo?: string;
    summary?: string;
  }>;
};

const reportInterpretationKnowledgeIdentity = "plat:diagnostic_item:lab-potassium";
const reportInterpretationActionCardIdentity = "ACTION_CARD.REPORT.CRITICAL_VALUE";

test.describe.configure({ mode: "serial" });

test.describe("四职责真实前台核心动作", () => {
  test("四职责主动作均完成真实前台操作与服务回读闭环", async ({ page }, testInfo) => {
    test.setTimeout(900_000);
    await page.setViewportSize({ width: 1440, height: 960 });

    const platformAdmin = await performPlatformAdminPersonnelAction(page);
    const engineOperator = await performEngineOperatorProviderAction(page);
    const clinicalUser = await performClinicalTodoAction(page);
    const auditor = await performAuditorEvidenceAction(page);

    await attachFourRoleCoreActionEvidence(testInfo, {
      platformAdmin,
      engineOperator,
      clinicalUser,
      auditor,
    });
  });
});

async function performPlatformAdminPersonnelAction(page: Page): Promise<RoleActionEvidence> {
  await ensureReadySession(page, "platform-admin");
  await page.goto(appPath("/admin/users"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "人员与账号" }).first(),
  ).toBeVisible({
    timeout: 30_000,
  });

  const suffix = Date.now().toString(36);
  const employeeNo = `ROLE-ACT-${suffix.toUpperCase()}`;
  const displayName = `四职责演练医生${suffix.slice(-4)}`;
  const username = `role-action-${suffix}`;

  await page.getByRole("button", { name: "新增人员" }).click();
  const dialog = page.getByRole("dialog", { name: "新增人员" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("院内人员身份").first().fill(employeeNo);
  await dialog.getByLabel("姓名").fill(displayName);
  await chooseDialogOption(page, dialog, "人员类型", "本机构员工");
  await selectFirstDialogOption(page, dialog, "所属机构");
  await dialog.getByLabel("岗位或职务").fill("上线演练主治医师");
  await expect(dialog.getByRole("checkbox", { name: "同时开通登录账号" })).toBeChecked();
  await dialog.getByLabel("登录名").fill(username);
  await chooseDialogOption(page, dialog, "初始角色", "临床使用者");
  await dialog.getByRole("checkbox", { name: "同时绑定院内身份来源" }).check();
  await chooseDialogOption(page, dialog, "身份来源", "院内工号");
  await dialog.getByLabel("院内人员身份").last().fill(employeeNo);

  const createResponsePromise = waitForPost(page, "/compliance/personnel");
  await dialog.getByRole("button", { name: "建立人员档案" }).click();
  const createResponse = await createResponsePromise;
  const createText = await createResponse.text();
  expect(
    createResponse.ok(),
    `平台管理员新增人员应返回成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: {
      person?: { personId?: string; employeeNo?: string };
      account?: { userId?: string; username?: string; state?: string };
      identities?: Array<{ providerType?: string; subjectHint?: string }>;
      oneTimeActivation?: { username?: string; temporaryPassword?: string };
    };
  };
  const personId = created.data?.person?.personId;
  expect(personId, "新增人员应返回人员身份").toBeTruthy();
  expect(created.data?.person?.employeeNo).toBe(employeeNo);
  expect(created.data?.account?.username).toBe(username);
  expect(created.data?.oneTimeActivation?.username).toBe(username);
  expect(created.data?.oneTimeActivation?.temporaryPassword).toBeTruthy();
  expect(created.data?.identities?.some((item) => item.providerType === "EMPLOYEE_NO")).toBe(true);
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const activationDialog = page.getByRole("dialog", { name: "一次性账号凭证" });
  await expect(activationDialog).toBeVisible({ timeout: 20_000 });
  await expect(activationDialog.getByText(username, { exact: true })).toBeVisible();
  await activationDialog.getByRole("button", { name: "已妥善记录" }).click();
  await expect(activationDialog).toBeHidden();

  await page.getByLabel("搜索人员").fill(displayName);
  await page.getByLabel("搜索人员").press("Enter");
  const row = page.getByRole("row", { name: new RegExp(escapeRegExp(displayName)) }).first();
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.getByRole("button", { name: "查看" }).click();
  const drawer = page.locator(".ant-drawer-content").filter({ hasText: "人员档案" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText(displayName, { exact: true }).first()).toBeVisible();
  await expect(drawer.getByText("账号与身份来源")).toBeVisible();
  await expect(drawer.getByText("临床使用者").first()).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText(/已绑定|院内工号|工号/u).first()).toBeVisible({ timeout: 20_000 });

  const detailResponse = await getApi(
    page,
    `/compliance/personnel/${encodeURIComponent(personId ?? "")}`,
  );
  await expectOk(detailResponse, "平台管理员回读新增人员详情");
  const detail = (await responseData(detailResponse)) as {
    person?: { employeeNo?: string };
    account?: { username?: string; state?: string };
    identities?: Array<{ providerType?: string }>;
  };
  expect(detail.person?.employeeNo).toBe(employeeNo);
  expect(detail.account?.username).toBe(username);
  expect(detail.identities?.some((item) => item.providerType === "EMPLOYEE_NO")).toBe(true);

  const auditVerified = await auditEventExists(page, {
    resourceType: "mk_identity_person",
    resourceId: personId ?? "",
  });
  expect(auditVerified, "平台管理员新增人员应产生人员审计事件").toBe(true);
  return {
    role: "platform-admin",
    path: "/admin/users",
    frontdeskAction: "前台新增人员、开通账号、绑定院内身份来源并回读人员详情",
    serviceOperation: "POST /api/v1/compliance/personnel",
    serviceStatus: createResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function performEngineOperatorProviderAction(page: Page): Promise<RoleActionEvidence> {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/knowledge/production"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page
      .locator("main")
      .getByRole("heading", { name: /知识生产/ })
      .first(),
  ).toBeVisible({ timeout: 30_000 });

  const providerCode = `role-action-ollama-${Date.now().toString(36)}`;
  await page.getByRole("button", { name: "登记模型服务" }).first().click();
  const dialog = page.getByRole("dialog", { name: "登记模型服务" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("稳定模型服务身份").fill(providerCode);
  await chooseDialogOption(page, dialog, "服务类型", "院内 Ollama");
  await dialog.getByLabel("服务地址").fill("http://127.0.0.1:11434");
  await dialog.getByLabel("模型版本").fill("medkernel-qwen25:1.5b-v1");

  const upsertResponsePromise = waitForPut(page, `/model-providers/${providerCode}`);
  await dialog.getByRole("button", { name: "保存并保持停用" }).click();
  const upsertResponse = await upsertResponsePromise;
  const upsertText = await upsertResponse.text();
  expect(
    upsertResponse.ok(),
    `运营员登记模型服务应返回成功 status=${upsertResponse.status()} body=${upsertText}`,
  ).toBe(true);
  const upserted = JSON.parse(upsertText) as {
    data?: { providerCode?: string; providerType?: string; enabled?: boolean; status?: string };
  };
  expect(upserted.data?.providerCode).toBe(providerCode);
  expect(upserted.data?.providerType).toBe("OLLAMA");
  expect(upserted.data?.enabled).toBe(false);
  expect(upserted.data?.status).toBe("NOT_CONNECTED");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  const providerRow = page
    .getByRole("row")
    .filter({ hasText: "院内 Ollama" })
    .filter({ hasText: "medkernel-qwen25:1.5b-v1" })
    .first();
  await expect(providerRow).toBeVisible({ timeout: 30_000 });
  await expect(providerRow).toContainText("待健康检查");
  await expect(page.getByText("生产前校验", { exact: true }).first()).toBeVisible();

  const readbackResponse = await getApi(
    page,
    `/model-providers/${encodeURIComponent(providerCode)}`,
  );
  await expectOk(readbackResponse, "运营员回读模型服务配置");
  const readback = (await responseData(readbackResponse)) as {
    providerCode?: string;
    enabled?: boolean;
    status?: string;
  };
  expect(readback.providerCode).toBe(providerCode);
  expect(readback.enabled).toBe(false);
  expect(readback.status).toBe("NOT_CONNECTED");
  const auditVerified = await auditEventExists(page, {
    resourceType: "mk_llm_provider",
    resourceId: providerCode,
  });
  expect(auditVerified, "运营员登记模型服务应产生审计事件").toBe(true);

  return {
    role: "engine-operator",
    path: "/knowledge/production",
    frontdeskAction: "前台登记院内模型服务并回读生产前校验保持待连接状态",
    serviceOperation: "PUT /api/v1/model-providers/{providerCode}",
    serviceStatus: upsertResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function performClinicalTodoAction(page: Page): Promise<RoleActionEvidence> {
  await ensureReadySession(page, "engine-operator");
  const runtime = await ensureDiagnosticCriticalValueRuntime(
    page,
    `four-role-${Date.now().toString(36)}`,
  );
  expect(runtime.actionCardAsset.assetIdentity).toBe(reportInterpretationActionCardIdentity);

  await ensureReadySession(page, "clinical-user");
  const snapshot = await createContextSnapshotForReportInterpretation(page);
  expect(snapshot.runtimeReleaseId, "报告解读上下文必须绑定包含危急值提示卡的机构生效版本").toBe(
    runtime.releaseId,
  );
  await expect(page.getByRole("button", { name: "生成报告解读" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "生成报告解读" }).click();
  const dialog = page.getByRole("dialog", { name: "生成医技报告解读" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });

  const interpretResponsePromise = waitForPost(
    page,
    "/engine/recommendations/report-interpretation",
  );
  await dialog.getByRole("button", { name: "生成报告解读" }).click();
  const interpretResponse = await interpretResponsePromise;
  const interpretText = await interpretResponse.text();
  expect(
    interpretResponse.ok(),
    `临床用户生成报告解读应返回成功 status=${interpretResponse.status()} body=${interpretText}`,
  ).toBe(true);
  const interpretation = JSON.parse(interpretText) as { data?: ReportInterpretationPayload };
  expect(interpretation.data?.runtimeReleaseId).toBe(snapshot.runtimeReleaseId);
  const expectedSourceId = interpretation.data?.recommendationCardIds?.[0] ?? "";
  expect(expectedSourceId, "报告解读响应必须返回本轮推荐卡来源").toBeTruthy();
  const interpretedReportType = interpretation.data?.interpretations?.[0]?.reportType ?? "血钾检验";
  const knowledgeItem = interpretation.data?.interpretations?.find(
    (item) =>
      item.itemCode === reportInterpretationKnowledgeIdentity &&
      Boolean(item.sourceVersionId) &&
      Boolean(item.versionNo),
  );
  expect(knowledgeItem, "报告解读应消费当前机构生效版本知识资产").toBeTruthy();
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await page.goto(appPath(`/workflow/todos?cardId=${encodeURIComponent(expectedSourceId)}`), {
    waitUntil: "domcontentloaded",
  });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "协同任务" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );
  const todoRow = page
    .locator("tr", { has: page.locator(`a[href*="${expectedSourceId}"]`) })
    .first();
  await expect(todoRow, "临床用户应能定位本轮报告解读协同待办").toBeVisible({
    timeout: 30_000,
  });
  await expect(todoRow).toContainText("报告解读");
  await expect(todoRow).toContainText(interpretedReportType);

  const completeResponsePromise = waitForPost(page, "/engine/workflow/todos/");
  await todoRow.getByRole("button", { name: "完成" }).click();
  const completeDialog = page.getByRole("dialog", { name: "完成待办" });
  await expect(completeDialog).toBeVisible({ timeout: 10_000 });
  await completeDialog
    .getByLabel("完成说明")
    .fill("四职责主动作演练：临床已人工复核报告解读，不改写已签发报告。");
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
  expect(completedTodo.data?.completionReason ?? "").toContain("四职责主动作演练");
  await expect(completeDialog).toBeHidden({ timeout: 20_000 });
  await page.goto(appPath(`/workflow/todos?cardId=${encodeURIComponent(expectedSourceId)}`), {
    waitUntil: "domcontentloaded",
  });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "协同任务" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );

  const completedTodosResponsePromise = waitForCompletedReportTodoReadback(page, expectedSourceId);
  await choosePageSelectOption(page, "待办状态", "已完成");
  const completedTodosResponse = await completedTodosResponsePromise;
  expect(completedTodosResponse.ok()).toBe(true);
  const completedTodos = JSON.parse(await completedTodosResponse.text()) as {
    data?: { items?: Array<{ todoId?: string; sourceId?: string; status?: string }> };
  };
  const readbackRows = completedTodos.data?.items ?? [];
  expect(
    readbackRows.some(
      (item) =>
        item.todoId === todoId && item.sourceId === expectedSourceId && item.status === "COMPLETED",
    ),
    `已完成筛选应回读本轮完成待办 url=${completedTodosResponse.url()} rows=${JSON.stringify(readbackRows)} expectedTodo=${todoId} expectedSource=${expectedSourceId}`,
  ).toBe(true);
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "workflow_todo",
    resourceId: todoId ?? "",
  });
  expect(auditVerified, "临床用户完成待办应产生审计事件").toBe(true);
  return {
    role: "clinical-user",
    path: "/workflow/todos",
    frontdeskAction: "前台筛选报告解读待办、填写完成说明并回读已完成状态",
    serviceOperation: "POST /api/v1/engine/workflow/todos/{todoId}/complete",
    serviceStatus: completeResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function performAuditorEvidenceAction(page: Page): Promise<RoleActionEvidence> {
  await ensureReadySession(page, "auditor");
  await page.goto(appPath("/admin/audit"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "审计与证据" }).first(),
  ).toBeVisible({
    timeout: 30_000,
  });

  const firstDetailButton = page.getByRole("button", { name: /查看详情/ }).first();
  await expect(firstDetailButton).toBeVisible({ timeout: 30_000 });
  const firstAuditRow = page.getByRole("row").filter({ has: firstDetailButton }).first();
  await expect(firstAuditRow.locator("td").first()).not.toHaveText("", { timeout: 10_000 });
  await firstDetailButton.click();
  const detailDrawer = page
    .locator(".ant-drawer-content")
    .filter({ hasText: "审计事件详情" })
    .last();
  await expect(detailDrawer).toBeVisible({ timeout: 20_000 });
  await expect(detailDrawer.getByText("审计事件详情")).toBeVisible();
  await expect(detailDrawer.getByRole("row", { name: /^摘要 .+/u })).toBeVisible();
  await expect(detailDrawer.getByRole("row", { name: /执行结果 成功/u })).toBeVisible();
  await expect(detailDrawer.getByRole("row", { name: /^链签名 (?!未生成).+/u })).toBeVisible();
  await expect(detailDrawer.getByRole("button", { name: /打开诊断链/u })).toBeVisible();
  await page.keyboard.press("Escape");

  const reason = `四职责审计导出证据 ${Date.now()}`;
  await page.getByRole("button", { name: "确认导出范围" }).click();
  const confirmDialog = page.getByRole("dialog", { name: "确认导出范围" });
  await expect(confirmDialog).toBeVisible({ timeout: 10_000 });
  await confirmDialog.getByLabel("导出原因").fill(reason);

  const confirmResponsePromise = waitForPost(page, "/compliance/exports:confirm");
  await confirmDialog.getByRole("button", { name: "确认范围" }).click();
  const confirmResponse = await confirmResponsePromise;
  const confirmText = await confirmResponse.text();
  expect(
    confirmResponse.ok(),
    `审计员确认导出范围应返回成功 status=${confirmResponse.status()} body=${confirmText}`,
  ).toBe(true);
  const confirmation = JSON.parse(confirmText) as {
    data?: { confirmationId?: string; confirmationEvidenceId?: string };
  };
  const confirmationId = confirmation.data?.confirmationId;
  expect(confirmationId, "确认导出范围应返回 confirmationId").toBeTruthy();

  await page.getByRole("tab", { name: "导出记录" }).click();
  await setEvidenceDetails(page, false);
  const row = page.getByRole("row").filter({ hasText: reason }).first();
  await expect(row).toBeVisible({ timeout: 30_000 });
  await expect(row).toContainText("审计导出任务");

  const exportResponsePromise = waitForPost(page, "/large-lists/exports");
  const completeResponsePromise = waitForPost(
    page,
    `/compliance/exports/${confirmationId}:complete-from-job`,
  );
  await row.getByRole("button", { name: "生成导出文件 审计导出任务" }).click();
  const exportDialog = page.getByRole("dialog", { name: "生成已确认导出文件" });
  await expect(exportDialog).toBeVisible({ timeout: 10_000 });
  await exportDialog.getByRole("button", { name: "确认生成导出文件" }).click();
  const exportResponse = await exportResponsePromise;
  expect(exportResponse.ok()).toBe(true);
  const completeResponse = await completeResponsePromise;
  const completeText = await completeResponse.text();
  expect(
    completeResponse.ok(),
    `审计员完成导出记录应返回成功 status=${completeResponse.status()} body=${completeText}`,
  ).toBe(true);
  await expect(row).toContainText("已导出", { timeout: 60_000 });

  await setEvidenceDetails(page, true);
  await expect(row).toContainText(confirmationId ?? "");
  await row.getByRole("button", { name: `查看证据 ${confirmationId}` }).click();
  const evidenceDialog = page.getByRole("dialog", { name: "导出证据" });
  await expect(evidenceDialog).toBeVisible({ timeout: 10_000 });
  const verifyResponsePromise = waitForPost(page, "/compliance/evidence/snapshots/");
  await evidenceDialog.getByRole("button", { name: "验签导出证据" }).click();
  const verifyResponse = await verifyResponsePromise;
  const verifyText = await verifyResponse.text();
  expect(
    verifyResponse.ok(),
    `审计员验签导出证据应返回成功 status=${verifyResponse.status()} body=${verifyText}`,
  ).toBe(true);
  await expect(evidenceDialog.getByText("证据验签通过")).toBeVisible({ timeout: 20_000 });

  return {
    role: "auditor",
    path: "/admin/audit",
    frontdeskAction: "前台查看审计事件详情、确认导出范围、生成导出文件并验签导出证据",
    serviceOperation: "POST /api/v1/compliance/evidence/snapshots/{evidenceId}/verify",
    serviceStatus: verifyResponse.status(),
    readbackVerified: true,
    auditVerified: true,
  };
}

async function createContextSnapshotForReportInterpretation(
  page: Page,
): Promise<ContextSnapshotSummary> {
  await page.goto(appPath("/mpi"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "患者索引" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible({ timeout: 10_000 });
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `角*${idLast4.slice(-1)}`;
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
    `创建脱敏患者主索引应返回成功 status=${patientResponse.status()} body=${patientText}`,
  ).toBe(true);
  const patient = JSON.parse(patientText) as { data?: { mpiId?: string } };
  const patientId = patient.data?.mpiId;
  expect(patientId, "创建患者后应返回 mpiId").toBeTruthy();
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
  await contextDialog.getByLabel("诊断/随访病种").fill("真实前台医技报告解读主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog.getByLabel("医技报告项目").fill("血钾检验");
  await contextDialog.getByLabel("报告结论").fill("血钾 6.3 mmol/L，危急值，已复核");
  await contextDialog.getByLabel("异常重点").fill("血钾升高、危急值");
  await contextDialog
    .getByLabel("建立原因")
    .fill("四职责主动作演练：为报告解读建立当前就诊上下文。");

  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  const contextText = await contextResponse.text();
  expect(
    contextResponse.ok(),
    `建立当前就诊上下文应返回成功 status=${contextResponse.status()} body=${contextText}`,
  ).toBe(true);
  const context = JSON.parse(contextText) as {
    data?: {
      snapshotId?: string;
      runtimeReleaseId?: string;
      resources?: { encounters?: Array<{ encounterId?: string }> };
    };
  };
  expect(context.data?.snapshotId).toBeTruthy();
  expect(context.data?.runtimeReleaseId).toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByText("当前就诊上下文已建立")).toBeVisible({ timeout: 20_000 });
  return {
    patientId: patientId ?? "",
    snapshotId: context.data?.snapshotId ?? "",
    runtimeReleaseId: context.data?.runtimeReleaseId ?? "",
    encounterId: context.data?.resources?.encounters?.[0]?.encounterId ?? null,
  };
}

async function attachFourRoleCoreActionEvidence(
  testInfo: TestInfo,
  actions: {
    platformAdmin: RoleActionEvidence;
    engineOperator: RoleActionEvidence;
    clinicalUser: RoleActionEvidence;
    auditor: RoleActionEvidence;
  },
) {
  const roleActions = [
    actions.platformAdmin,
    actions.engineOperator,
    actions.clinicalUser,
    actions.auditor,
  ];
  expect(roleActions.map((item) => item.role).sort()).toEqual([
    "auditor",
    "clinical-user",
    "engine-operator",
    "platform-admin",
  ]);
  await testInfo.attach("four-role-core-actions-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scopeStatement:
          "四职责主动作代表闭环：平台管理员、医疗引擎运营员、临床使用者和审计员各完成一个真实前台主动作，包含服务端状态变化或只读校验、回读和审计证据；不代表全部产品入口业务动作闭环，不代表完整上线验收。",
        roleActions,
        platformAdmin: actions.platformAdmin,
        engineOperator: actions.engineOperator,
        clinicalUser: actions.clinicalUser,
        auditor: actions.auditor,
      },
      null,
      2,
    ),
  });
}

async function auditEventExists(page: Page, options: { resourceType: string; resourceId: string }) {
  if (!options.resourceId) return false;
  const response = await getApi(
    page,
    `/large-lists/audit-events/list?resourceType=${encodeURIComponent(
      options.resourceType,
    )}&size=50`,
  );
  await expectOk(response, `回读审计事件 ${options.resourceType}/${options.resourceId}`);
  const data = await responseData(response);
  return pageItems(data).some((item) => {
    const record = recordValue(item);
    return (
      record?.resourceType === options.resourceType && record.resourceId === options.resourceId
    );
  });
}

async function auditEventExistsAsAuditor(
  page: Page,
  options: { resourceType: string; resourceId: string },
) {
  await ensureReadySession(page, "auditor");
  return auditEventExists(page, options);
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

async function selectFirstDialogOption(page: Page, dialog: Locator, label: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const firstOption = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await expect(firstOption).toBeVisible({ timeout: 20_000 });
  await firstOption.click();
}

async function currentSelectText(select: Locator) {
  const selected = select.locator(".ant-select-selection-item").first();
  if ((await selected.count()) === 0) return "";
  const title = await selected.getAttribute("title", { timeout: 1_000 }).catch(() => null);
  if (title) return title.trim();
  const text = await selected.textContent({ timeout: 1_000 }).catch(() => null);
  return text?.trim() ?? "";
}

async function setEvidenceDetails(page: Page, enabled: boolean) {
  const toggle = page.getByRole("switch", { name: "证据详情" });
  const visible = await toggle.isVisible({ timeout: 2_000 }).catch(() => false);
  if (!visible) return;
  const checked = (await toggle.getAttribute("aria-checked")) === "true";
  if (checked !== enabled) {
    await toggle.click();
  }
  await expect(toggle).toHaveAttribute("aria-checked", enabled ? "true" : "false");
}

function waitForGet(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "GET" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function waitForCompletedReportTodoReadback(page: Page, expectedSourceId: string) {
  return page.waitForResponse(
    (response) => {
      if (response.request().method() !== "GET") return false;
      const url = new URL(response.url());
      return (
        url.pathname.includes("/engine/workflow/todos") &&
        url.searchParams.get("status") === "COMPLETED" &&
        url.searchParams.get("sourceType") === "REPORT_INTERPRETATION" &&
        url.searchParams.get("sourceId") === expectedSourceId
      );
    },
    { timeout: 30_000 },
  );
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 60_000 },
  );
}

function waitForPut(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "PUT" && response.url().includes(path),
    { timeout: 60_000 },
  );
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
