import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { ensureReadySession } from "./support/auth";

type RuntimeCollectors = {
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

type RuntimeRecord = RuntimeCollectors & {
  stage: string;
  url: string;
};

type ContextSnapshotSummary = {
  snapshotId: string;
  patientId: string;
  encounterId?: string | null;
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

type InsuranceAuditSummary = {
  issueId: string;
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
    };
    rectificationTask?: {
      taskId?: string;
      status?: string;
    };
  };
};

test.describe.configure({ mode: "serial" });

test.describe("全前台真实操作演练", () => {
  test("平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生", async ({
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];
    const suffix = Date.now().toString(36);

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
      await publishFollowupTemplateFromUi(page, testInfo, runtime, records, followupTemplate);
      const snapshot = await createContextSnapshotFromUi(page, testInfo, runtime, records, patient);
      const insuranceAudit = await runInsuranceAuditFromUi(
        page,
        testInfo,
        runtime,
        records,
        snapshot,
      );
      await closeQualityRectificationFromAlertsUi(page, testInfo, runtime, records, insuranceAudit);
      await runCdssRecommendationFromUi(page, testInfo, runtime, records, snapshot);
      await generateFollowupPlanAndHandlePatientFeedbackFromUi(
        page,
        testInfo,
        runtime,
        records,
        followupTemplate,
        snapshot,
      );
    } finally {
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
  await dialog.getByLabel("目标标准字典").fill("MEDKERNEL-REAL-FRONTDESK");

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
    data?: { snapshotId?: string; resources?: { encounters?: Array<{ encounterId?: string }> } };
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
  };
}

async function runInsuranceAuditFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  snapshot: ContextSnapshotSummary,
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
  await expect(page.getByRole("button", { name: "选择第 1 个病案快照" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "选择第 1 个病案快照" }).click();
  await expect(page.getByLabel("患者信息")).toHaveValue("已关联患者");
  if (snapshot.encounterId) {
    await expect(page.getByLabel("就诊信息")).toHaveValue("已关联就诊");
  }
  await choosePageSelectOption(page, "责任科室");
  await choosePageSelectOption(page, "评价指标");

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

  const usesManualIndicator = await page
    .getByText("未读取到已生效评价指标，将按本次医保规则依据归档并生成整改任务。")
    .isVisible()
    .catch(() => false);
  const caseReviewPromise = usesManualIndicator
    ? null
    : waitForPost(page, "/api/v1/engine/quality/case-review");
  const drgPromise = waitForPost(page, "/api/v1/engine/quality/drg-grouping");
  const auditPromise = waitForPost(page, "/api/v1/engine/quality/insurance-audit");
  await page.getByRole("button", { name: "执行审核并派整改" }).click();
  const [drgResponse, auditResponse] = await Promise.all([drgPromise, auditPromise]);
  if (caseReviewPromise) {
    const caseReviewResponse = await caseReviewPromise;
    expect(caseReviewResponse.ok(), "前台执行病案内涵质控应返回成功").toBe(true);
  }
  expect(drgResponse.ok(), "前台执行 DRG/DIP 分组应返回成功").toBe(true);
  const auditResponseText = await auditResponse.text();
  expect(
    auditResponse.ok(),
    `前台执行医保审核应返回成功 status=${auditResponse.status()} body=${auditResponseText}`,
  ).toBe(true);
  const audit = JSON.parse(auditResponseText) as {
    data?: {
      auditStatus?: string;
      findingCount?: number;
      taskCount?: number;
      issues?: Array<{ issueId?: string; ruleCode?: string; evidenceSummary?: string }>;
    };
  };
  expect(audit.data?.auditStatus, "真实结算事实应触发医保问题").toBe("ISSUE_FOUND");
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
  };
}

async function closeQualityRectificationFromAlertsUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  audit: InsuranceAuditSummary,
) {
  await ensureReadySession(page, "engine-operator");
  clearRuntime(runtime);
  await page.goto("/qc/alerts", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "质量问题与整改" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expectNoRootOverflow(page, "质量问题与整改桌面");

  const openAlert = await findInsuranceQualityAlert(page, audit.issueId, "OPEN");
  const findingId = openAlert.sourceId;
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
  await expect(dialog.getByRole("button", { name: "选择第 1 个临床快照" })).toBeVisible({
    timeout: 20_000,
  });
  await dialog.getByRole("button", { name: "选择第 1 个临床快照" }).click();
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
) {
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
}

async function generateFollowupPlanAndHandlePatientFeedbackFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
  template: { templateId: string; name: string; displayName: string; defaultName: string },
  snapshot: ContextSnapshotSummary,
) {
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
  await expect(dialog.getByRole("button", { name: "选择第 1 个随访上下文快照" })).toBeVisible({
    timeout: 20_000,
  });
  await dialog.getByRole("button", { name: "选择第 1 个随访上下文快照" }).click();
  await chooseDialogOption(page, dialog, "随访风险分层", "中风险");
  await searchDialogOption(page, dialog, "随访方案", template.defaultName, template.defaultName);

  const responsePromise = waitForPost(page, "/api/v1/engine/followup/plans/generate");
  await dialog.getByRole("button", { name: /生\s*成/ }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台生成随访计划应返回成功").toBe(true);
  const result = (await response.json()) as { data?: { planId?: string; tasks?: Array<unknown> } };
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
  await expect(page.getByText("异常回院证据已登记")).toBeVisible({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-followup-plan-questionnaire-abnormal");
  recordCleanRuntime(page, "前台生成随访计划并完成问卷与异常回院登记", runtime, records);
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, option: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const selectedText = await currentSelectText(select);
  if (selectedText === option) {
    return;
  }
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 5_000 });
  await optionLocator.click();
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
  await select.locator(".ant-select-selector").click();
  await combobox.fill(searchText);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({
      hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}(?:\\s*·.*|（.*）)?\\s*$`),
    })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function choosePageSelectOption(page: Page, label: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function choosePageSelectOptionByText(page: Page, label: string, option: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(option)}\\s*$`) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 10_000 });
  await optionLocator.click();
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

async function expectNoRootOverflow(page: Page, label: string) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth, `${label} 页面根节点不应横向溢出`).toBeLessThanOrEqual(
    dimensions.viewportWidth,
  );
}
