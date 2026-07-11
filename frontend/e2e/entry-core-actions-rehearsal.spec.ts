import { createHash } from "node:crypto";

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
  arrayData,
  ensureReadySession,
  expectOk,
  getApi,
  numericField,
  pageItems,
  postApi,
  recordField,
  responseData,
  resolvedTenantIdFor,
  textField,
  type RoleAccount,
} from "./support/auth";
import { ensureDiagnosticCriticalValueRuntime } from "./support/diagnosticRuntime";

type EntryActionEvidence = {
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

type KnowledgeReviewCandidateSeed = {
  identityId: number;
  classificationId: number;
  identityCode: string;
  subject: string;
  versionNo: string;
};

type WorkflowTodoNotificationSeed = {
  notificationId: string;
  title: string;
  todoId: string;
};

type ProvenanceSeed = {
  identityId: number;
  identityCode: string;
  versionId: number;
  subject: string;
  sourceDocumentId: number;
  sourceVersionId: number;
  sourceCode: string;
  sourceVersionNo: string;
  sourceVersionHash: string;
  sourceFragmentId: number;
  fragmentHash: string;
  anchorPath: string;
  anchorLabel: string;
  textExcerpt: string;
  citationId: number;
};

const securityConfigKey = "medkernel.knowledge.literature.material-root-uri";

test.describe.configure({ mode: "serial" });

test.describe("七路由六入口族真实前台核心动作", () => {
  test("七个路由覆盖六类入口族完成真实前台核心动作代表闭环", async ({ page }, testInfo) => {
    test.setTimeout(1_200_000);
    await page.setViewportSize({ width: 1440, height: 960 });

    const security = await performSecurityBaselineAction(page);
    const knowledgeGovernance = await performKnowledgeGovernanceAction(page);
    const ruleDefinitions = await performRuleDefinitionsAction(page);
    const notifications = await performNotificationsAction(page);
    const notificationSettings = await performNotificationSettingsAction(page);
    const sandbox = await performSandboxAction(page);
    const provenance = await performProvenanceAction(page);

    await attachEntryCoreActionEvidence(testInfo, [
      security,
      knowledgeGovernance,
      ruleDefinitions,
      notifications,
      notificationSettings,
      sandbox,
      provenance,
    ]);
  });
});

async function performSecurityBaselineAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "platform-admin");
  await page.goto(appPath("/security/baseline"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "安全与配置" }).first(),
  ).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("tab", { name: "系统配置" }).click();

  const before = await getSystemConfigItem(page, securityConfigKey);
  const configRow = await locateSystemConfigRow(page, before.displayName);
  await expect(configRow).toBeVisible({ timeout: 30_000 });
  await configRow.getByRole("button", { name: /编辑/u }).click();
  const dialog = page.getByRole("dialog", { name: "编辑系统配置" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  const nextValue = `file:///srv/medkernel/platform-knowledge/t-1/literature-materials/entry-core-${Date.now().toString(36)}/`;
  await dialog.getByLabel("配置值").fill(nextValue);
  await dialog.getByLabel("变更原因").fill("七入口代表闭环演练：验证安全基线配置保存和审计留痕。");
  await dialog.getByRole("checkbox", { name: "确认高风险影响" }).check();

  const saveResponsePromise = waitForResponse(page, "PATCH", "/system/configs/");
  await dialog.getByRole("button", { name: "保存配置" }).click();
  const saveResponse = await saveResponsePromise;
  await expectOk(saveResponse, "安全基线保存配置");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(configRow).toContainText("资料库根地址已配置", { timeout: 20_000 });

  const readbackValue = await getSystemConfigItem(page, securityConfigKey);
  expect(textField(readbackValue, "value")).toBe(nextValue);

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "system_config",
    resourceId: securityConfigKey,
  });
  return {
    role: "platform-admin",
    path: "/security/baseline",
    frontdeskAction: "前台确认高风险影响后保存平台知识资料根地址并回读配置中心当前值",
    serviceOperation: "PATCH /api/v1/system/configs/{key}",
    serviceStatus: saveResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function getSystemConfigItem(page: Page, key: string) {
  const readback = await getApi(page, "/system/configs");
  await expectOk(readback, `回读系统配置 ${key}`);
  const item = arrayData(await responseData(readback)).find(
    (candidate) => textField(candidate, "key") === key,
  );
  expect(item, `系统配置中心应存在 ${key}`).toBeTruthy();
  return item;
}

async function locateSystemConfigRow(page: Page, displayName: string | null) {
  expect(displayName, "系统配置应带有前台显示名称").toBeTruthy();
  const rowName = displayName ?? "";
  const table = page.locator("main").getByRole("table").first();
  await expect(table).toBeVisible({ timeout: 30_000 });
  for (let pageIndex = 0; pageIndex < 10; pageIndex += 1) {
    const row = table.getByRole("row").filter({ hasText: rowName }).first();
    if ((await row.count()) > 0 && (await row.isVisible())) {
      return row;
    }
    const nextButton = page.locator(".ant-pagination-next button").last();
    if ((await nextButton.count()) === 0 || (await nextButton.isDisabled())) {
      break;
    }
    await nextButton.click();
    await expect(table.getByRole("row").first()).toBeVisible({ timeout: 10_000 });
  }
  return table.getByRole("row").filter({ hasText: rowName }).first();
}

async function performKnowledgeGovernanceAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "engine-operator");
  const seed = await prepareKnowledgeReviewCandidate(page);
  await page.goto(appPath("/knowledge/governance?entry-core-review=" + seed.identityCode), {
    waitUntil: "domcontentloaded",
  });
  await expect(
    page.locator("main").getByRole("heading", { name: "知识审核发布中心" }).first(),
  ).toBeVisible({ timeout: 30_000 });

  await page.getByLabel("知识关键词").fill(seed.identityCode);
  const searchResponsePromise = waitForResponseWithQuery(
    page,
    "GET",
    "/engine/knowledge/identities",
    "keyword",
    seed.identityCode,
  );
  await page.locator("main").getByRole("button", { name: "search", exact: true }).click();
  const searchResponse = await searchResponsePromise;
  await expectOk(searchResponse, "检索七入口知识审核身份");
  expect(
    pageItems(await responseData(searchResponse)).some(
      (item) => numericField(item, "id") === seed.identityId,
    ),
    "七入口知识审核身份必须能通过真实前台关键词查询回读",
  ).toBe(true);
  await expect(page.getByRole("row").filter({ hasText: seed.subject }).first()).toBeVisible({
    timeout: 30_000,
  });
  const identityRow = page
    .getByRole("row")
    .filter({ hasText: seed.subject })
    .filter({ has: page.getByRole("button", { name: "查看候选" }) })
    .first();
  await expect(identityRow).toBeVisible({ timeout: 30_000 });
  await identityRow.getByRole("button", { name: "查看候选" }).click();

  const candidateButton = page
    .getByRole("row")
    .filter({ hasText: seed.versionNo })
    .getByRole("button", { name: "查看审核对照" })
    .first();
  await expect(candidateButton).toBeVisible({ timeout: 30_000 });
  const candidateResponsePromise = waitForResponse(page, "GET", "/engine/knowledge/candidates/");
  await candidateButton.click();
  const candidateResponse = await candidateResponsePromise;
  await expectOk(candidateResponse, "读取知识候选审核对照");
  const drawer = page.getByRole("dialog").filter({ hasText: "知识候选审核对照" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText("审核对象已锁定为当前候选版本")).toBeVisible();
  await drawer
    .getByLabel("审核理由")
    .fill("七入口代表闭环演练：来源锚点不足，退回生产者补充后再提审。");
  const returnReviewButton = page.getByRole("button", { name: /退\s*修/u }).last();
  await expect(returnReviewButton).toBeVisible({ timeout: 10_000 });

  const reviewResponsePromise = waitForResponse(page, "POST", "/engine/knowledge/candidates/");
  await returnReviewButton.click();
  const reviewResponse = await reviewResponsePromise;
  await expectOk(reviewResponse, "退修知识候选");
  const reviewed = await responseData(reviewResponse);
  expect(textField(reviewed, "reasonCode")).toBe("RETURNED");
  await expect(drawer).toBeHidden({ timeout: 30_000 });

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "knowledge_candidate_classification",
    resourceId: String(seed.classificationId),
  });
  return {
    role: "engine-operator",
    path: "/knowledge/governance",
    frontdeskAction: "前台选择知识身份、查看候选审核对照并完成退修决策",
    serviceOperation: "POST /api/v1/engine/knowledge/candidates/{candidateId}/review",
    serviceStatus: reviewResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function prepareKnowledgeReviewCandidate(page: Page): Promise<KnowledgeReviewCandidateSeed> {
  const suffix = Date.now().toString(36);
  const subject = `七入口代表闭环审核说明书 ${suffix}`;
  const identityCode = `ENTRY.CORE.REVIEW.${suffix.toUpperCase()}`;
  const content = [
    "七入口代表闭环审核说明书。",
    "该候选仅用于真实前台审核台退修动作演练。",
    "候选必须经来源采集、生产生成和人工审核，不参与临床执行。",
  ].join("\n");
  const source = await postApi(page, "/engine/knowledge/sources", {
    ...knowledgeContext("entry-core-knowledge-source"),
    sourceCode: `entry-core-review-${suffix}`,
    sourceType: "GUIDELINE",
    authorityLevel: "B_GUIDELINE",
    authorityBasis: "七入口代表闭环演练受控来源，用于验证知识审核台真实退修动作。",
    title: `七入口代表闭环审核说明书来源 ${suffix}`,
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(source, "登记七入口知识审核来源");
  const sourceDocumentId = numericField(await responseData(source), "id");
  expect(sourceDocumentId, "七入口知识审核来源必须返回 id").toBeTruthy();

  const sourceVersion = await postApi(
    page,
    `/engine/knowledge/sources/${sourceDocumentId}/versions`,
    {
      ...knowledgeContext("entry-core-knowledge-source-version"),
      versionNo: "2026",
      publishedAt: "2026-07-08T00:00:00Z",
      fileUri: `file:///srv/medkernel/platform-knowledge/t-1/literature-materials/entry-core/${suffix}.md`,
      language: "zh-CN",
      content,
    },
  );
  await expectOk(sourceVersion, "登记七入口知识审核来源版本");
  const sourceVersionId = numericField(await responseData(sourceVersion), "id");
  expect(sourceVersionId, "七入口知识审核来源版本必须返回 id").toBeTruthy();

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId,
    anchorPath: `entry-core/review-${suffix}`,
    anchorLabel: "七入口代表闭环审核说明",
    textExcerpt: content,
  });
  await expectOk(fragment, "登记七入口知识审核来源片段");

  const generated = await postApi(page, "/engine/knowledge-production/generate", {
    sourceVersionId,
    targetPipeline: "TENANT_OVERLAY",
    domain: "CLINICAL",
    items: [
      {
        assetType: "KNOWLEDGE",
        target: {
          targetIdentityId: null,
          newIdentity: { domain: "GUIDELINE", subject, identityCode },
        },
      },
    ],
  });
  await expectOk(generated, "从受控来源生成七入口知识审核候选");
  const generation = await responseData(generated);
  expect(arrayField(generation, "blocked"), "七入口知识审核候选不得被安全门阻断").toHaveLength(0);
  expect(arrayField(generation, "skipped"), "七入口知识审核候选不得被分流跳过").toHaveLength(0);
  const candidateRef = textField(arrayField(generation, "candidates")[0], "candidateRef");
  expect(candidateRef, "七入口知识审核候选必须返回 candidateRef").toBeTruthy();
  const parsed = parseKnowledgeCandidateRef(candidateRef ?? "");
  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
  );
  await expectOk(identity, "读取七入口知识审核身份");
  const identityId = numericField(await responseData(identity), "id");
  expect(identityId, "七入口知识审核身份必须返回 id").toBe(parsed.identityId);

  const candidateView = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/candidates?page=1&size=20`,
  );
  await expectOk(candidateView, "读取七入口知识审核候选项");
  const candidateData = await responseData(candidateView);
  const versionCandidate = pageItems(recordField(candidateData, "candidates")).find(
    (item) => textField(item, "versionNo") === parsed.versionNo,
  );
  const versionId = numericField(versionCandidate, "id");
  expect(versionId, "七入口知识审核候选版本必须返回 id").toBeTruthy();
  const classification = arrayField(candidateData, "classifications").find(
    (item) => numericField(item, "candidateVersionId") === versionId,
  );
  const classificationId = numericField(classification, "id");
  expect(classificationId, "七入口知识审核候选必须生成审核分类").toBeTruthy();
  return {
    identityId: identityId ?? 0,
    classificationId: classificationId ?? 0,
    identityCode,
    subject,
    versionNo: parsed.versionNo,
  };
}

async function performRuleDefinitionsAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "engine-operator");
  const runtime = await ensureDiagnosticCriticalValueRuntime(
    page,
    `entry-rule-${Date.now().toString(36)}`,
  );
  await ensureReadySession(page, "clinical-user");
  const snapshot = await createContextSnapshotForRuleCase(page);
  expect(snapshot.runtimeReleaseId).toBe(runtime.releaseId);

  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/rule/definitions"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "临床规则" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );

  const suffix = Date.now().toString(36);
  const ruleCode = `RULE.ENTRY.CORE.${suffix.toUpperCase()}`;
  const ruleName = `七入口代表闭环规则 ${suffix}`;
  await page.getByRole("button", { name: "新建临床规则" }).click();
  const createDialog = page.getByRole("dialog", { name: "创建新临床规则" });
  await expect(createDialog).toBeVisible({ timeout: 10_000 });
  await createDialog.getByLabel("稳定规则资产身份").fill(ruleCode);
  await createDialog.getByLabel("规则显示名称").fill(ruleName);
  await chooseDialogOption(page, createDialog, "规则门类", "临床质控");
  await chooseDialogOption(page, createDialog, "风险严重等级", "低风险");
  await chooseDialogOption(page, createDialog, "临床触发场景", "查看患者");
  await createDialog.getByLabel("医学依据/来源").fill("七入口代表闭环演练：本地上线演练规则依据。");
  await createDialog.getByLabel("初始化变更内容说明").fill("创建代表闭环规则草稿。");
  await createDialog.getByLabel("执行优先级").fill("101");
  await createDialog.getByLabel("同患者去重窗口").fill("0");
  await createDialog.getByRole("tab", { name: /L2 条件树/u }).click();
  await createDialog.locator("#rule-condition-fact").fill("observations[].valueNumeric");
  await createDialog.locator("#rule-condition-value").fill("6");

  const createResponsePromise = waitForResponse(page, "POST", "/engine/rule/rules");
  await createDialog.getByRole("button", { name: "创建草稿" }).click();
  const createResponse = await createResponsePromise;
  await expectOk(createResponse, "创建临床规则草稿");
  const created = await responseData(createResponse);
  const ruleId = textField(created, "ruleId");
  expect(ruleId).toBeTruthy();
  await expect(createDialog).toBeHidden({ timeout: 30_000 });

  await page
    .getByRole("row")
    .filter({ hasText: ruleName })
    .first()
    .getByRole("button", { name: /详情|查看/u })
    .click();
  const detailDrawer = page
    .locator(".ant-drawer-content")
    .filter({ hasText: "临床规则详情与试运行" })
    .last();
  await expect(detailDrawer).toBeVisible({ timeout: 20_000 });
  await detailDrawer.getByRole("tab", { name: /发布验证用例/u }).click();
  await detailDrawer.getByRole("button", { name: "新增验证用例" }).click();
  const caseDialog = page.getByRole("dialog", { name: "新增验证用例" });
  await expect(caseDialog).toBeVisible({ timeout: 10_000 });
  await chooseDialogOption(page, caseDialog, "用例类别", "阳性命中用例");
  await caseDialog.getByLabel("验证用例患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await caseDialog.getByLabel("验证用例就诊信息").fill(snapshot.encounterId);
  }
  await caseDialog.getByRole("button", { name: "读取已生效快照" }).click();
  await expect(caseDialog.getByText("已生效").first()).toBeVisible({ timeout: 30_000 });
  await assertContextSnapshotSearchContains(page, snapshot);
  const snapshotChoice = caseDialog.getByText("第 1 个临床快照");
  await expect(snapshotChoice).toBeVisible({ timeout: 20_000 });
  await snapshotChoice.click();
  await expect(caseDialog.getByText("验证快照已关联")).toBeVisible({ timeout: 20_000 });
  await chooseDialogOption(page, caseDialog, "期望求值结果", "应当触发规则命中");
  await chooseDialogOption(page, caseDialog, "期望风险等级", "低风险");
  await chooseDialogOption(page, caseDialog, "期望处置动作", "一般提醒");

  const caseResponsePromise = waitForResponse(
    page,
    "POST",
    `/engine/rule/rules/${ruleId}/test-cases`,
  );
  await caseDialog.getByRole("button", { name: "保存用例" }).click();
  const caseResponse = await caseResponsePromise;
  await expectOk(caseResponse, "保存规则验证用例");
  const savedCase = await responseData(caseResponse);
  const caseId = textField(savedCase, "caseId");
  expect(caseId, "保存规则验证用例必须返回 caseId").toBeTruthy();
  await expect(caseDialog).toBeHidden({ timeout: 30_000 });

  const runResponsePromise = waitForResponse(page, "POST", `/engine/rule/rules/${ruleId}/test`);
  await detailDrawer.getByRole("button", { name: "执行全部用例" }).click();
  const runResponse = await runResponsePromise;
  await expectOk(runResponse, "执行规则验证用例");
  const runResult = await responseData(runResponse);
  expect(String(textField(runResult, "status") ?? "")).not.toBe("FAILED");

  const detailResponse = await getApi(
    page,
    `/engine/rule/rules/${encodeURIComponent(ruleId ?? "")}`,
  );
  await expectOk(detailResponse, "回读临床规则详情");
  const detail = await responseData(detailResponse);
  expect(textField(recordField(detail, "definition"), "ruleId")).toBe(ruleId);
  expect(
    arrayField(detail, "testCases").some((item) => textField(item, "caseId") === caseId),
    "规则详情必须回读本轮保存的验证用例",
  ).toBe(true);
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "rule_definition",
    resourceId: ruleId ?? "",
  });
  return {
    role: "engine-operator",
    path: "/rule/definitions",
    frontdeskAction: "前台新建规则草稿、绑定已生效快照验证用例并执行全部用例",
    serviceOperation: "POST /api/v1/engine/rule/rules/{ruleId}/test",
    serviceStatus: runResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function performNotificationsAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  const notification = await prepareWorkflowTodoNotification(page);
  await page.goto(appPath("/notifications"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "消息通知" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );

  const unread = await getApi(page, "/engine/notifications?status=UNREAD&page=1&size=10");
  await expectOk(unread, "读取未读通知");
  expect(
    pageItems(await responseData(unread)).some(
      (item) =>
        textField(item, "notificationId") === notification.notificationId &&
        textField(item, "sourceType") === "WORKFLOW_TODO" &&
        textField(item, "sourceId") === notification.todoId,
    ),
    "未读通知列表必须包含本轮报告解读待办投影通知",
  ).toBe(true);
  const notificationItem = page
    .locator(".ant-list-item")
    .filter({ hasText: notification.title })
    .filter({ hasText: "协同待办" })
    .first();
  await expect(notificationItem).toBeVisible({ timeout: 30_000 });

  const readResponsePromise = waitForResponse(
    page,
    "POST",
    `/engine/notifications/${notification.notificationId}/read`,
  );
  await notificationItem.getByRole("button", { name: "标为已读" }).click();
  const readResponse = await readResponsePromise;
  await expectOk(readResponse, "标记通知已读");
  const read = await responseData(readResponse);
  expect(textField(read, "status")).toBe("READ");
  expect(textField(read, "notificationId")).toBe(notification.notificationId);

  const readback = await getApi(page, "/engine/notifications?status=READ&page=1&size=20");
  await expectOk(readback, "回读已读通知");
  expect(
    pageItems(await responseData(readback)).some(
      (item) => textField(item, "notificationId") === notification.notificationId,
    ),
  ).toBe(true);
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "workflow_notification",
    resourceId: notification.notificationId,
  });
  return {
    role: "clinical-user",
    path: "/notifications",
    frontdeskAction: "前台查看未读业务通知并标记为已读，回读已读状态",
    serviceOperation: "POST /api/v1/engine/notifications/{notificationId}/read",
    serviceStatus: readResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function prepareWorkflowTodoNotification(page: Page): Promise<WorkflowTodoNotificationSeed> {
  await ensureReadySession(page, "engine-operator");
  const runtime = await ensureDiagnosticCriticalValueRuntime(
    page,
    `entry-notification-${Date.now().toString(36)}`,
  );

  await ensureReadySession(page, "clinical-user");
  const snapshot = await createContextSnapshotForReportInterpretation(page);
  expect(snapshot.runtimeReleaseId, "通知演练上下文必须绑定本轮报告解读运行版本").toBe(
    runtime.releaseId,
  );

  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "提醒与推荐" })).toBeVisible();
  await page.getByRole("button", { name: "生成报告解读" }).click();
  const dialog = page.getByRole("dialog", { name: "生成医技报告解读" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  const snapshotButton = dialog.getByRole("button", { name: /临床快照/u }).first();
  await expect(snapshotButton, "报告解读弹窗必须展示本轮危急值上下文选择按钮").toBeVisible({
    timeout: 20_000,
  });
  await snapshotButton.click();

  const interpretResponsePromise = waitForResponse(
    page,
    "POST",
    "/engine/recommendations/report-interpretation",
  );
  await dialog.getByRole("button", { name: "生成报告解读" }).click();
  const interpretResponse = await interpretResponsePromise;
  await expectOk(interpretResponse, "生成报告解读通知前置推荐卡");
  const interpretation = await responseData(interpretResponse);
  expect(textField(interpretation, "runtimeReleaseId")).toBe(snapshot.runtimeReleaseId);
  expect(
    arrayField(interpretation, "interpretations").some(
      (item) => textField(item, "itemCode") === "plat:diagnostic_item:lab-potassium",
    ),
    "报告解读必须消费血钾危急值知识资产",
  ).toBe(true);
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const todos = await getApi(
    page,
    `/engine/workflow/todos?sourceType=REPORT_INTERPRETATION&patientId=${encodeURIComponent(
      snapshot.patientId,
    )}&status=PENDING&page=1&size=20`,
  );
  await expectOk(todos, "读取报告解读待办以准备通知");
  const todo = pageItems(await responseData(todos)).find(
    (item) =>
      textField(item, "sourceType") === "REPORT_INTERPRETATION" &&
      textField(item, "status") === "PENDING" &&
      textField(item, "patientId") === snapshot.patientId,
  );
  const todoId = textField(todo, "todoId");
  expect(todoId, "报告解读必须投影成本轮待办").toBeTruthy();

  const unread = await getApi(page, "/engine/notifications?status=UNREAD&page=1&size=20");
  await expectOk(unread, "读取报告解读待办通知");
  const notification = pageItems(await responseData(unread)).find(
    (item) =>
      textField(item, "sourceType") === "WORKFLOW_TODO" && textField(item, "sourceId") === todoId,
  );
  const notificationId = textField(notification, "notificationId");
  const title = textField(notification, "title");
  expect(notificationId, "本轮报告解读待办必须生成未读通知").toBeTruthy();
  expect(title, "本轮报告解读待办通知必须返回标题").toBeTruthy();
  return {
    notificationId: notificationId ?? "",
    title: title ?? "",
    todoId: todoId ?? "",
  };
}

async function performNotificationSettingsAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/notifications/settings"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.locator("main").getByRole("heading", { name: "通知偏好" }).first()).toBeVisible(
    {
      timeout: 30_000,
    },
  );

  const quietSwitch = page.getByRole("switch", { name: "免打扰偏好" });
  if ((await quietSwitch.getAttribute("aria-checked")) !== "true") {
    await quietSwitch.click();
  }
  await expect(quietSwitch).toHaveAttribute("aria-checked", "true");
  await page.getByLabel("免打扰开始时间").fill("21:30");
  await page.getByLabel("免打扰结束时间").fill("07:30");
  const saveResponsePromise = waitForResponse(page, "PUT", "/engine/notifications/settings");
  await page.getByRole("button", { name: "保存通知偏好" }).click();
  const saveResponse = await saveResponsePromise;
  await expectOk(saveResponse, "保存个人通知偏好");
  const saved = await responseData(saveResponse);
  expect(textField(saved, "source")).toBe("PERSONAL");
  expect(arrayField(saved, "subscribedTypes")).toContain("SAFETY");
  expect(arrayField(saved, "quietBypassLevels")).toEqual(
    expect.arrayContaining(["CRITICAL", "HIGH"]),
  );

  const readback = await getApi(page, "/engine/notifications/settings");
  await expectOk(readback, "回读通知偏好");
  const settings = await responseData(readback);
  expect(textField(settings, "quietStart")).toBe("21:30");
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "notification_settings",
    resourceId: "clinical-user",
  });
  return {
    role: "clinical-user",
    path: "/notifications/settings",
    frontdeskAction: "前台保存个人免打扰通知偏好并确认安全通知强制订阅",
    serviceOperation: "PUT /api/v1/engine/notifications/settings",
    serviceStatus: saveResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function performSandboxAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "clinical-user");
  await ensureSandboxEmbedOrigin(page);
  await page.goto(appPath("/sandbox"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "全真体验沙盘" }).first(),
  ).toBeVisible({
    timeout: 30_000,
  });

  const runButton = page.getByRole("button", { name: "医生复核并触发 MedKernel" });
  await expect(runButton).toBeVisible({ timeout: 30_000 });
  const runResponsePromise = waitForResponse(page, "POST", "/engine/sandbox/scenarios/");
  await runButton.click();
  const runResponse = await runResponsePromise;
  await expectOk(runResponse, "医生复核触发沙盘真实协同链路");
  const run = await responseData(runResponse);
  const scenarioId = textField(run, "scenarioId");
  expect(scenarioId).toBeTruthy();
  expect(textField(run, "result")).toBe("PASS");
  await expect(page.getByText("真实链路通过")).toBeVisible({ timeout: 60_000 });
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "sandbox_scenario",
    resourceId: scenarioId ?? "",
  });
  return {
    role: "clinical-user",
    path: "/sandbox",
    frontdeskAction: "前台医生复核血钾危急值并触发 MedKernel 沙盘真实协同链路",
    serviceOperation: "POST /api/v1/engine/sandbox/scenarios/{scenarioId}/run",
    serviceStatus: runResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function ensureSandboxEmbedOrigin(page: Page) {
  const origin = new URL(page.url()).origin;
  const response = await postApi(page, "/engine/embed/origins", { origin });
  await expectOk(response, "添加沙盘嵌入 Origin 白名单");
}

async function performProvenanceAction(page: Page): Promise<EntryActionEvidence> {
  await ensureReadySession(page, "engine-operator");
  const seed = await prepareProvenanceSeed(page);
  await assertProvenanceSeedReadback(page, seed);

  await ensureReadySession(page, "auditor");
  const provenanceResponsePromise = waitForResponse(
    page,
    "GET",
    `/engine/knowledge/identities/${seed.identityId}/provenance`,
  );
  await page.goto(appPath(`/advanced/provenance?identityId=${seed.identityId}`), {
    waitUntil: "domcontentloaded",
  });
  const provenanceResponse = await provenanceResponsePromise;
  await expectOk(provenanceResponse, "前台回读本轮来源血缘详情");
  const provenance = await responseData(provenanceResponse);
  expect(numericField(provenance, "currentVersionId")).toBe(seed.versionId);
  expect(
    arrayField(provenance, "sourceEvidence").some(
      (item) => numericField(item, "citationId") === seed.citationId,
    ),
    "前台来源血缘详情必须包含本轮 citationId",
  ).toBe(true);
  await expect(
    page.locator("main").getByRole("heading", { name: "来源与血缘" }).first(),
  ).toBeVisible({
    timeout: 30_000,
  });
  const row = page.getByRole("row").filter({ hasText: seed.subject }).first();
  await expect(row).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("来源详情")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("版本沿革")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("精确来源锚点")).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(seed.anchorLabel).first()).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(seed.textExcerpt).first()).toBeVisible({ timeout: 30_000 });

  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "knowledge_identity",
    resourceId: String(seed.identityId),
  });
  return {
    role: "auditor",
    path: "/advanced/provenance",
    frontdeskAction: "前台打开本轮唯一知识身份来源血缘并查看精确来源锚点",
    serviceOperation: "GET /api/v1/engine/knowledge/identities/{id}/provenance",
    serviceStatus: provenanceResponse.status(),
    readbackVerified: true,
    auditVerified,
  };
}

async function prepareProvenanceSeed(page: Page): Promise<ProvenanceSeed> {
  const suffix = Date.now().toString(36);
  const subject = `七入口来源血缘说明书 ${suffix}`;
  const identityCode = `ENTRY.CORE.PROVENANCE.${suffix.toUpperCase()}`;
  const sourceCode = `entry-core-provenance-${suffix}`;
  const sourceVersionNo = `2026-${suffix}`;
  const anchorPath = `entry-core/provenance-${suffix}`;
  const anchorLabel = `七入口来源血缘锚点 ${suffix}`;
  const textExcerpt = `七入口来源血缘锚点 ${suffix}：本材料只用于验证来源与血缘前台对当前 ACTIVE 知识版本、结构化 citation 和原文锚点的真实回读。`;
  const content = [
    textExcerpt,
    "不得由此推断诊断、处方、剂量、阈值或自动医嘱。",
    "正式医学知识仍需经完整来源治理、人工审核、发布和机构生效版本控制。",
  ].join("\n");
  const sourceVersionHash = sha256(content);
  const fragmentHash = sha256(textExcerpt);

  const source = await postApi(page, "/engine/knowledge/sources", {
    ...knowledgeContext("entry-core-provenance-source"),
    sourceCode,
    sourceType: "GUIDELINE",
    authorityLevel: "B_GUIDELINE",
    authorityBasis: "七入口代表闭环演练受控来源，用于验证来源血缘真实前台回读。",
    title: `七入口来源血缘说明书来源 ${suffix}`,
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(source, "登记七入口来源血缘受控来源");
  const sourceDocumentId = numericField(await responseData(source), "id");
  expect(sourceDocumentId, "七入口来源血缘来源必须返回 id").toBeTruthy();

  const sourceVersion = await postApi(
    page,
    `/engine/knowledge/sources/${sourceDocumentId}/versions`,
    {
      ...knowledgeContext("entry-core-provenance-source-version"),
      versionNo: sourceVersionNo,
      publishedAt: "2026-07-08T00:00:00Z",
      contentHash: sourceVersionHash,
      fileUri: `file:///srv/medkernel/platform-knowledge/t-1/literature-materials/entry-core/provenance-${suffix}.md`,
      language: "zh-CN",
      content,
    },
  );
  await expectOk(sourceVersion, "登记七入口来源血缘来源版本");
  const sourceVersionId = numericField(await responseData(sourceVersion), "id");
  expect(sourceVersionId, "七入口来源血缘来源版本必须返回 id").toBeTruthy();

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId,
    anchorPath,
    anchorLabel,
    textExcerpt,
  });
  await expectOk(fragment, "登记七入口来源血缘来源片段");
  const sourceFragmentId = numericField(await responseData(fragment), "id");
  expect(sourceFragmentId, "七入口来源血缘来源片段必须返回 id").toBeTruthy();

  const generated = await postApi(page, "/engine/knowledge-production/generate", {
    sourceVersionId,
    targetPipeline: "TENANT_OVERLAY",
    domain: "CLINICAL",
    items: [
      {
        assetType: "KNOWLEDGE",
        target: {
          targetIdentityId: null,
          newIdentity: { domain: "GUIDELINE", subject, identityCode },
        },
      },
    ],
  });
  await expectOk(generated, "从受控来源生成七入口来源血缘候选");
  const generation = await responseData(generated);
  expect(arrayField(generation, "blocked"), "七入口来源血缘候选不得被安全门阻断").toHaveLength(0);
  expect(arrayField(generation, "skipped"), "七入口来源血缘候选不得被分流跳过").toHaveLength(0);
  const generatedCandidate = arrayField(generation, "candidates")[0];
  const candidateRef = requireText(
    textField(generatedCandidate, "candidateRef"),
    "七入口来源血缘候选必须返回 candidateRef",
  );
  const jobCode = requireText(
    textField(generatedCandidate, "jobCode"),
    "七入口来源血缘候选必须返回 jobCode",
  );

  const parsed = parseKnowledgeCandidateRef(candidateRef);
  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
  );
  await expectOk(identity, "读取七入口来源血缘知识身份");
  const identityId = numericField(await responseData(identity), "id");
  expect(identityId, "七入口来源血缘知识身份必须返回 id").toBe(parsed.identityId);

  const candidateView = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/candidates?page=1&size=20`,
  );
  await expectOk(candidateView, "读取七入口来源血缘候选项");
  const candidateData = await responseData(candidateView);
  const versionCandidate = pageItems(recordField(candidateData, "candidates")).find(
    (item) => textField(item, "versionNo") === parsed.versionNo,
  );
  const versionId = numericField(versionCandidate, "id");
  expect(versionId, "七入口来源血缘候选版本必须返回 id").toBeTruthy();
  const classification = arrayField(candidateData, "classifications").find(
    (item) => numericField(item, "candidateVersionId") === versionId,
  );
  const classificationId = numericField(classification, "id");
  expect(classificationId, "七入口来源血缘候选必须生成审核分类").toBeTruthy();

  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: versionId,
    sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 100,
    startOffset: 0,
    endOffset: textExcerpt.length,
  });
  await expectOk(citation, "绑定七入口来源血缘结构化 citation");
  const citationId = numericField(await responseData(citation), "id");
  expect(citationId, "七入口来源血缘 citation 必须返回 id").toBeTruthy();

  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records`,
    {
      candidateRef,
      identityId,
      versionId,
    },
  );
  await expectOk(qualityRecord, "生成七入口来源血缘发布质量记录");
  const qualityGateRecordId = numericField(await responseData(qualityRecord), "id");
  expect(qualityGateRecordId, "七入口来源血缘发布质量记录必须返回 id").toBeTruthy();

  const review = await postApi(page, `/engine/knowledge/candidates/${classificationId}/review`, {
    ...knowledgeContext("entry-core-provenance-review"),
    decision: "APPROVE",
    reason: "七入口代表闭环演练：本轮来源血缘候选已绑定结构化 citation 并完成服务端质量门。",
    qualityGateRecordId,
  });
  await expectOk(review, "审核激活七入口来源血缘候选");

  return {
    identityId: identityId ?? 0,
    identityCode,
    versionId: versionId ?? 0,
    subject,
    sourceDocumentId: sourceDocumentId ?? 0,
    sourceVersionId: sourceVersionId ?? 0,
    sourceCode,
    sourceVersionNo,
    sourceVersionHash,
    sourceFragmentId: sourceFragmentId ?? 0,
    fragmentHash,
    anchorPath,
    anchorLabel,
    textExcerpt,
    citationId: citationId ?? 0,
  };
}

async function assertProvenanceSeedReadback(page: Page, seed: ProvenanceSeed) {
  const response = await getApi(
    page,
    `/engine/knowledge/identities/${seed.identityId}/provenance?page=1&size=20`,
  );
  await expectOk(response, "回读七入口来源血缘种子");
  const provenance = await responseData(response);
  const identity = recordField(provenance, "identity");
  expect(numericField(identity, "id")).toBe(seed.identityId);
  expect(textField(identity, "identityCode")).toBe(seed.identityCode);
  expect(numericField(provenance, "currentVersionId")).toBe(seed.versionId);
  expect(provenance.partial, "七入口来源血缘引用必须完整解析").toBe(false);
  expect(numericField(provenance, "unresolvedCitationCount")).toBe(0);
  const activeVersion = pageItems(recordField(provenance, "versions")).find(
    (item) => numericField(item, "id") === seed.versionId,
  );
  expect(textField(activeVersion, "status")).toBe("ACTIVE");
  const evidence = arrayField(provenance, "sourceEvidence").find(
    (item) => numericField(item, "citationId") === seed.citationId,
  );
  expect(evidence, "七入口来源血缘证据必须包含本轮 citationId").toBeTruthy();
  expect(numericField(evidence, "assetVersionId")).toBe(seed.versionId);
  expect(numericField(evidence, "sourceFragmentId")).toBe(seed.sourceFragmentId);
  expect(numericField(evidence, "sourceDocumentId")).toBe(seed.sourceDocumentId);
  expect(numericField(evidence, "sourceVersionId")).toBe(seed.sourceVersionId);
  expect(textField(evidence, "sourceCode")).toBe(seed.sourceCode);
  expect(textField(evidence, "sourceVersionNo")).toBe(seed.sourceVersionNo);
  expect(textField(evidence, "sourceVersionHash")).toBe(seed.sourceVersionHash);
  expect(textField(evidence, "anchorPath")).toBe(seed.anchorPath);
  expect(textField(evidence, "anchorLabel")).toBe(seed.anchorLabel);
  expect(textField(evidence, "textExcerpt")).toBe(seed.textExcerpt);
  expect(textField(evidence, "fragmentHash")).toBe(seed.fragmentHash);
  expect(numericField(evidence, "startOffset")).toBe(0);
  expect(numericField(evidence, "endOffset")).toBe(seed.textExcerpt.length);
  expect(textField(evidence, "relation")).toBe("DERIVED_FROM");
  expect(numericField(evidence, "weight")).toBe(100);
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
  const maskedName = `通*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = patientDialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("66");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);

  const patientResponsePromise = waitForResponse(page, "POST", "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectOk(patientResponse, "创建通知演练患者");
  const patient = await responseData(patientResponse);
  const patientId = textField(patient, "mpiId");
  expect(patientId, "通知演练患者必须返回 mpiId").toBeTruthy();
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(maskedName);
  await page.getByRole("button", { name: /检索过滤/u }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(maskedName)}.*${idLast4}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/u }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();

  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible({ timeout: 10_000 });
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("七入口代表闭环通知报告解读主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog.getByLabel("医技报告项目").fill("血钾检验");
  await contextDialog.getByLabel("报告结论").fill("血钾 6.3 mmol/L，危急值，已复核");
  await contextDialog.getByLabel("异常重点").fill("血钾升高、危急值");
  await contextDialog
    .getByLabel("建立原因")
    .fill("七入口代表闭环演练：为消息通知创建报告解读待办前置上下文。");

  const contextResponsePromise = waitForResponse(page, "POST", "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectOk(contextResponse, "建立通知演练报告解读上下文");
  const context = await responseData(contextResponse);
  const snapshotId = textField(context, "snapshotId");
  const runtimeReleaseId = textField(context, "runtimeReleaseId");
  expect(snapshotId, "通知演练上下文必须返回 snapshotId").toBeTruthy();
  expect(runtimeReleaseId, "通知演练上下文必须返回 runtimeReleaseId").toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId: patientId ?? "",
    snapshotId: snapshotId ?? "",
    runtimeReleaseId: runtimeReleaseId ?? "",
    encounterId: textField(recordField(context, "resources")?.encounters?.[0], "encounterId"),
  };
}

async function createContextSnapshotForRuleCase(page: Page): Promise<ContextSnapshotSummary> {
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
  const maskedName = `入*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = patientDialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("45");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);

  const patientResponsePromise = waitForResponse(page, "POST", "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectOk(patientResponse, "创建规则验证患者");
  const patient = await responseData(patientResponse);
  const patientId = textField(patient, "mpiId");
  expect(patientId).toBeTruthy();
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(maskedName);
  await page.getByRole("button", { name: /检索过滤/u }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(maskedName)}.*${idLast4}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/u }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();

  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible({ timeout: 10_000 });
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("七入口代表闭环规则验证主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "低风险");
  await contextDialog.getByLabel("医技报告项目").fill("血钾检验");
  await contextDialog.getByLabel("报告结论").fill("规则验证上下文，无危急值。");
  await contextDialog.getByLabel("异常重点").fill("无");
  await contextDialog
    .getByLabel("建立原因")
    .fill("七入口代表闭环演练：为规则验证用例建立当前就诊上下文。");

  const contextResponsePromise = waitForResponse(page, "POST", "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectOk(contextResponse, "建立规则验证上下文");
  const context = await responseData(contextResponse);
  const snapshotId = textField(context, "snapshotId");
  const runtimeReleaseId = textField(context, "runtimeReleaseId");
  expect(snapshotId).toBeTruthy();
  expect(runtimeReleaseId).toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId: patientId ?? "",
    snapshotId: snapshotId ?? "",
    runtimeReleaseId: runtimeReleaseId ?? "",
    encounterId: textField(recordField(context, "resources")?.encounters?.[0], "encounterId"),
  };
}

async function assertContextSnapshotSearchContains(page: Page, snapshot: ContextSnapshotSummary) {
  const response = await getApi(
    page,
    `/engine/context/snapshots?patientId=${encodeURIComponent(snapshot.patientId)}${
      snapshot.encounterId ? `&encounterId=${encodeURIComponent(snapshot.encounterId)}` : ""
    }&status=ACTIVE&page=1&size=20`,
  );
  await expectOk(response, "回读规则验证快照检索结果");
  expect(
    pageItems(await responseData(response)).some(
      (item) => textField(item, "snapshotId") === snapshot.snapshotId,
    ),
    "规则验证快照检索结果必须包含本轮已生效上下文",
  ).toBe(true);
}

async function attachEntryCoreActionEvidence(
  testInfo: TestInfo,
  entryActions: EntryActionEvidence[],
) {
  expect(entryActions.map((item) => item.path).sort()).toEqual([
    "/advanced/provenance",
    "/knowledge/governance",
    "/notifications",
    "/notifications/settings",
    "/rule/definitions",
    "/sandbox",
    "/security/baseline",
  ]);
  expect(new Set(entryActions.map((item) => item.role))).toEqual(
    new Set(["platform-admin", "engine-operator", "clinical-user", "auditor"]),
  );
  for (const action of entryActions) {
    expect(action.auditVerified, `${action.path} 的代表核心动作必须能回读真实审计事件`).toBe(true);
  }
  await testInfo.attach("entry-core-actions-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        matrixCode: "SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE",
        scopeStatement:
          "七个路由覆盖六类入口族的六入口核心动作代表闭环：围绕安全与配置、知识审核发布中心、临床规则、消息通知与通知偏好、全真体验沙盘和来源血缘完成真实前台核心动作、服务回读与审计证据；不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
        entryActions,
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
    `/large-lists/audit-events/list?resourceType=${encodeURIComponent(options.resourceType)}&size=100`,
  );
  await expectOk(response, `回读审计事件 ${options.resourceType}/${options.resourceId}`);
  return pageItems(await responseData(response)).some(
    (item) =>
      textField(item, "resourceType") === options.resourceType &&
      textField(item, "resourceId") === options.resourceId,
  );
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
  await expect(combobox, `对话框中应存在下拉字段：${label}`).toBeVisible({ timeout: 5_000 });
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
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

function waitForResponse(page: Page, method: string, path: string): Promise<APIResponse> {
  return page.waitForResponse(
    (response) => response.request().method() === method && response.url().includes(path),
    { timeout: 60_000 },
  );
}

function waitForResponseWithQuery(
  page: Page,
  method: string,
  path: string,
  queryKey: string,
  queryValue: string,
): Promise<APIResponse> {
  return page.waitForResponse(
    (response) => {
      if (response.request().method() !== method || !response.url().includes(path)) {
        return false;
      }
      const url = new URL(response.url());
      return url.searchParams.get(queryKey) === queryValue;
    },
    { timeout: 60_000 },
  );
}

function knowledgeContext(prefix: string) {
  const traceId = `${prefix}-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function parseKnowledgeCandidateRef(candidateRef: string) {
  const parts = candidateRef.split(":");
  if (parts.length < 3 || parts[0] !== "kv") {
    throw new Error(`七入口知识审核候选引用格式非法：${candidateRef}`);
  }
  const identityId = Number(parts[1]);
  expect(Number.isFinite(identityId), "七入口知识审核候选引用必须包含数字身份 ID").toBe(true);
  return { identityId, versionNo: parts.slice(2).join(":") };
}

function arrayField(value: unknown, field: string) {
  const record = recordValue(value);
  const raw = record ? record[field] : undefined;
  return Array.isArray(raw) ? raw : [];
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

function sha256(value: string) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}
