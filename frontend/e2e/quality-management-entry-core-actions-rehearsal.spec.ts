import { expect, test, type Locator, type Page } from "@playwright/test";

import {
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  postApi,
  recordField,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  resolveBaselineRuntimeAssets,
  textField,
} from "./support/auth";
import {
  attachQualityManagementEntryCoreActionEvidence,
  type MedicalRecordInsurancePaymentConsumerSliceEvidence,
  type MedicalRecordQualityIssueEvidence,
  type QualityManagementEntryCoreActionEvidence,
  type QualityManagementEvaluationAssetEvidence,
  type QualityManagementRollbackNegativeEvidence,
} from "./support/qualityManagementEntryCoreActions";

type MpiPatient = {
  patientId: string;
  maskedName: string;
  idLast4: string;
};

type ContextSnapshotSummary = {
  snapshotId: string;
  patientId: string;
  encounterId: string | null;
  runtimeReleaseId: string;
  resources: unknown;
};

type ClaimIndicatorSummary = {
  indicatorId: string;
  indicatorCode: string;
  name: string;
};

type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type RuntimeCandidateSummary = RuntimeAssetSelection & {
  versionNo: string | null;
};

type RuntimeReadbackEvidence = {
  releaseId: string;
  revisionNo: number;
  manifestSha256: string;
  assets: Array<Record<string, unknown>>;
};

type ClaimRuntimeActivationSummary = {
  releaseId: string;
  hospitalId: string;
  previousReleaseId: string | null;
  candidate: RuntimeCandidateSummary & { versionId: string };
  activationRequest: unknown;
  runtimeReadback: RuntimeReadbackEvidence;
  runtimeConsumer: RuntimeReadbackEvidence & { contractVersion: "v1" };
};

type InsuranceAuditSummary = {
  issueId: string;
  evaluationRunId: string;
  findingId: string;
  auditStatus: "ISSUE_FOUND";
  findingCount: number;
  taskCount: number;
  caseReviewStatus: number;
  drgGroupingStatus: number;
  insuranceAuditStatus: number;
};

type RectificationSummary = {
  findingId: string;
  taskId: string;
};

type QualityActionResult<T> = {
  payload: T;
  action: QualityManagementEntryCoreActionEvidence;
};

test.describe("质量管理入口核心动作真实前台演练", () => {
  test("质量风险概览、质量问题整改、医保审核和评价指标均完成真实前台代表动作", async ({
    page,
  }, testInfo) => {
    test.setTimeout(600_000);
    await page.setViewportSize({ width: 1440, height: 960 });
    const suffix = Date.now().toString(36);

    const indicator = await createActiveClaimIndicatorFromUi(page, suffix);
    const runtime = await activateHospitalRuntimeWithClaimIndicator(page, indicator.payload);
    const snapshot = await preparePatientSnapshotFromUi(page, suffix);
    expect(snapshot.runtimeReleaseId, "病案快照必须绑定包含本轮 CLAIM 指标的机构生效版本").toBe(
      runtime.releaseId,
    );
    const insuranceAudit = await runInsuranceAuditFromUi(page, snapshot, indicator.payload);
    const rectification = await closeRectificationFromAlertsUi(page, insuranceAudit.payload);
    const dashboardAction = await drilldownQualityDashboardFromUi(page, rectification.payload);
    const evaluationAssetSupplyChainEvidence = buildEvaluationAssetSupplyChainEvidence({
      indicator: indicator.payload,
      runtime,
      insuranceAudit: insuranceAudit.payload,
      indicatorAuditVerified: indicator.action.auditVerified,
    });
    const rollbackNegativeEvidence = await rollbackRuntimeAndAssertEvaluationRemoved(page, runtime);

    await attachQualityManagementEntryCoreActionEvidence(
      testInfo,
      [indicator.action, insuranceAudit.action, rectification.action, dashboardAction],
      {
        evaluationAssetSupplyChainEvidence,
        rollbackNegativeEvidence,
        medicalRecordQualityIssueEvidence: buildMedicalRecordQualityIssueEvidence(
          insuranceAudit.payload,
        ),
        medicalRecordInsurancePaymentConsumerSlice:
          buildMedicalRecordInsurancePaymentConsumerSliceEvidence(),
        clinicalContext: {
          snapshotId: snapshot.snapshotId,
          patientId: snapshot.patientId,
          encounterId: snapshot.encounterId,
          runtimeReleaseId: snapshot.runtimeReleaseId,
          resources: snapshot.resources,
        },
      },
    );
  });
});

async function preparePatientSnapshotFromUi(
  page: Page,
  suffix: string,
): Promise<ContextSnapshotSummary> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible({ timeout: 30_000 });

  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible({ timeout: 10_000 });
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `质*${suffix.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = patientDialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("67");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);

  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  const patientText = await patientResponse.text();
  expect(
    patientResponse.ok(),
    `质量管理矩阵前置脱敏患者创建应成功 status=${patientResponse.status()} body=${patientText}`,
  ).toBe(true);
  const patientPayload = JSON.parse(patientText) as { data?: { mpiId?: string } };
  const patient: MpiPatient = {
    patientId: patientPayload.data?.mpiId ?? "",
    maskedName,
    idLast4,
  };
  expect(patient.patientId, "质量管理矩阵前置患者必须返回主索引").toBeTruthy();
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(patient.maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const patientRow = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(patient.maskedName)}.*${idLast4}`) })
    .first();
  await expect(patientRow).toBeVisible({ timeout: 20_000 });
  await patientRow.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();

  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible({ timeout: 10_000 });
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("质量管理入口矩阵医保审核主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog.getByLabel("DRG/DIP 分组").fill("DRG-QC-A");
  await contextDialog.getByLabel("本次结算金额").fill("1280.50");
  await contextDialog.getByLabel("医保支付金额").fill("860.00");
  await contextDialog
    .getByLabel("建立原因")
    .fill("质量管理入口矩阵：建立含医保结算事实的脱敏病案快照。");

  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  const contextText = await contextResponse.text();
  expect(
    contextResponse.ok(),
    `质量管理矩阵前置病案快照应成功 status=${contextResponse.status()} body=${contextText}`,
  ).toBe(true);
  const contextPayload = JSON.parse(contextText) as {
    data?: {
      snapshotId?: string;
      runtimeReleaseId?: string;
      resources?: { encounters?: Array<{ encounterId?: string }> };
    };
  };
  const snapshotId = contextPayload.data?.snapshotId ?? "";
  const runtimeReleaseId = contextPayload.data?.runtimeReleaseId ?? "";
  expect(snapshotId, "质量管理矩阵前置快照必须返回 snapshotId").toBeTruthy();
  expect(runtimeReleaseId, "质量管理矩阵前置快照必须返回 runtimeReleaseId").toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });

  return {
    snapshotId,
    patientId: patient.patientId,
    encounterId: contextPayload.data?.resources?.encounters?.[0]?.encounterId ?? null,
    runtimeReleaseId,
    resources: contextPayload.data?.resources ?? {},
  };
}

async function createActiveClaimIndicatorFromUi(
  page: Page,
  suffix: string,
): Promise<QualityActionResult<ClaimIndicatorSummary>> {
  const department = await ensureQualityDepartment(page, suffix);
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/qc/eval/sets"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "评价指标" })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);

  const indicatorCode = `QC.MATRIX.CLAIM.${suffix.toUpperCase()}`;
  const indicatorName = `质量矩阵医保合规指标 ${suffix}`;
  await page.getByRole("button", { name: "新建指标" }).click();
  const dialog = page.getByRole("dialog", { name: "新建评价指标" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("稳定评价指标身份").fill(indicatorCode);
  await dialog.getByLabel("指标名称").fill(indicatorName);
  await chooseDialogOption(page, dialog, "评估主体", "医保合规");
  await searchDialogOption(page, dialog, "责任科室", department.name);
  await dialog.getByLabel("来源依据").fill("质量管理入口矩阵：医保合规指标由前台创建并激活。");
  await dialog.getByLabel("评分定义").fill("P1 级医保合规缺陷，命中后生成质量问题和整改任务。");

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
    `前台创建质量指标应成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: { indicatorId?: string; indicatorCode?: string; name?: string; status?: string };
  };
  const indicatorId = created.data?.indicatorId ?? "";
  expect(indicatorId, "评价指标创建响应必须返回 indicatorId").toBeTruthy();
  expect(created.data?.status, "评价指标创建后必须先进入草稿").toBe("DRAFT");
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await page.getByLabel("评价指标身份筛选").fill(indicatorCode);
  const row = page.getByRole("row", { name: new RegExp(escapeRegExp(indicatorName)) }).first();
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
    reason: "质量管理入口矩阵：医保合规指标安全复核通过。",
    expectedStatus: "PUBLISHED",
  });
  await confirmClaimIndicatorRelease(page, drawer, {
    button: "开始灰度",
    title: "开始 10% 床位灰度",
    ok: "确认灰度",
    path: `/api/v1/engine/evaluation/indicators/${indicatorId}/gray`,
    reason: "质量管理入口矩阵：先按默认灰度观察指标。",
    expectedStatus: "GRAY",
  });
  const activateResponse = await confirmClaimIndicatorRelease(page, drawer, {
    button: "全量激活",
    title: "全量激活",
    ok: "确认全量",
    path: `/api/v1/engine/evaluation/indicators/${indicatorId}/activate`,
    reason: "质量管理入口矩阵：灰度观察通过，允许 CLAIM 指标全量激活。",
    expectedStatus: "ACTIVE",
  });
  await expect(drawer.getByText("生效中", { exact: true }).first()).toBeVisible({
    timeout: 20_000,
  });
  await assertEvaluationIndicatorActive(page, indicatorCode, indicatorId);
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "evaluation_indicator",
    resourceId: indicatorId,
  });
  expect(auditVerified, "创建并激活评价指标应产生真实审计事件").toBe(true);

  return {
    payload: {
      indicatorId,
      indicatorCode: created.data?.indicatorCode ?? indicatorCode,
      name: created.data?.name ?? indicatorName,
    },
    action: {
      menuKey: "qc-eval-sets",
      role: "engine-operator",
      path: "/qc/eval/sets",
      frontdeskAction: "医疗引擎运营员前台创建、提交、发布、灰度并激活 CLAIM 评价指标",
      serviceOperation:
        "POST /api/v1/engine/evaluation/indicators + POST /api/v1/engine/evaluation/indicators/{indicatorId}/activate",
      serviceStatus: minSuccessfulStatus(createResponse.status(), activateResponse.status()),
      readbackVerified: true,
      auditVerified,
    },
  };
}

async function runInsuranceAuditFromUi(
  page: Page,
  snapshot: ContextSnapshotSummary,
  indicator: ClaimIndicatorSummary,
): Promise<QualityActionResult<InsuranceAuditSummary>> {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/qc/insurance"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "医保审核" })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);

  await page.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await page.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  const snapshots = await getApi(
    page,
    `/engine/context/snapshots?patientId=${encodeURIComponent(
      snapshot.patientId,
    )}&encounterId=${encodeURIComponent(snapshot.encounterId ?? "")}&status=ACTIVE&page=1&size=20`,
  );
  await expectOk(snapshots, "医保审核页回读本轮 ACTIVE 病案快照候选");
  const matchedSnapshot = pageItems(await responseData(snapshots)).find(
    (item) => textField(item, "snapshotId") === snapshot.snapshotId,
  );
  expect(
    matchedSnapshot,
    `医保审核页过滤条件必须能回读本轮病案快照 ${snapshot.snapshotId}`,
  ).toBeTruthy();
  const snapshotButton = page
    .locator(`button[data-snapshot-id="${cssStringEscape(snapshot.snapshotId)}"]`)
    .first();
  await expect(snapshotButton, `医保审核页必须展示本轮病案快照 ${snapshot.snapshotId}`).toBeVisible(
    { timeout: 20_000 },
  );
  await snapshotButton.click();
  await choosePageSelectOption(page, "责任科室");
  await searchPageSelectOption(page, "评价指标", indicator.indicatorCode, indicator.name);

  await page.getByLabel("审核场景").fill("A9");
  await page.getByLabel("整改截止时间").fill("2026年07月15日 08:30");
  await page.getByLabel("DRG 分组器版本").fill("GROUPER-2026");
  await page.getByLabel("期望入组").fill("DRG-QC-A");
  await page.getByLabel("实际入组").fill("DRG-QC-B");
  await page
    .getByLabel("入组说明")
    .fill("质量管理入口矩阵：基于当前病案快照和医保结算事实完成 DRG/DIP 入组复核。");
  await page.getByLabel("医保规则依据").fill("QC.MATRIX.FEE");
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
  const auditText = await auditResponse.text();
  expect(
    auditResponse.ok(),
    `前台执行医保审核应返回成功 status=${auditResponse.status()} body=${auditText}`,
  ).toBe(true);
  const audit = JSON.parse(auditText) as {
    data?: {
      auditStatus?: string;
      evaluationRunId?: string | null;
      findingCount?: number;
      taskCount?: number;
      issues?: Array<{ issueId?: string }>;
    };
  };
  expect(audit.data?.auditStatus, "医保审核应命中本轮问题").toBe("ISSUE_FOUND");
  const issueId = audit.data?.issues?.[0]?.issueId ?? "";
  const evaluationRunId = audit.data?.evaluationRunId ?? "";
  expect(issueId, "医保审核问题应返回 issueId").toBeTruthy();
  expect(evaluationRunId, "医保审核必须绑定评价运行").toBeTruthy();
  expect(audit.data?.findingCount ?? 0, "医保审核应生成质量问题").toBeGreaterThan(0);
  expect(audit.data?.taskCount ?? 0, "医保审核应派发整改任务").toBeGreaterThan(0);
  await expect(
    page.getByText("医保审核已基于真实结算事实执行，命中问题已由服务联动整改闭环。"),
  ).toBeVisible({ timeout: 20_000 });

  const findingId = await findingIdForInsuranceIssue(page, issueId, evaluationRunId, indicator);
  const auditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "quality_finding",
    resourceId: findingId,
  });
  expect(auditVerified, "医保审核生成的质量问题应有审计事件").toBe(true);
  await ensureReadySession(page, "engine-operator");

  return {
    payload: {
      issueId,
      evaluationRunId,
      findingId,
      auditStatus: "ISSUE_FOUND",
      findingCount: audit.data?.findingCount ?? 0,
      taskCount: audit.data?.taskCount ?? 0,
      caseReviewStatus: caseReviewResponse.status(),
      drgGroupingStatus: drgResponse.status(),
      insuranceAuditStatus: auditResponse.status(),
    },
    action: {
      menuKey: "insurance-audit",
      role: "engine-operator",
      path: "/qc/insurance",
      frontdeskAction: "医疗引擎运营员前台选择真实病案快照并执行医保审核派整改",
      serviceOperation:
        "POST /api/v1/engine/quality/case-review + POST /api/v1/engine/quality/drg-grouping + POST /api/v1/engine/quality/insurance-audit",
      serviceStatus: minSuccessfulStatus(
        caseReviewResponse.status(),
        drgResponse.status(),
        auditResponse.status(),
      ),
      readbackVerified: Boolean(issueId && evaluationRunId && findingId),
      auditVerified,
    },
  };
}

function buildMedicalRecordQualityIssueEvidence(
  audit: InsuranceAuditSummary,
): MedicalRecordQualityIssueEvidence {
  return {
    operation: "CASE_REVIEW_DRG_INSURANCE_AUDIT",
    caseReviewStatus: audit.caseReviewStatus,
    drgGroupingStatus: audit.drgGroupingStatus,
    insuranceAuditStatus: audit.insuranceAuditStatus,
    auditStatus: audit.auditStatus,
    issueId: audit.issueId,
    evaluationRunId: audit.evaluationRunId,
    findingId: audit.findingId,
    findingCount: audit.findingCount,
    taskCount: audit.taskCount,
  };
}

function buildMedicalRecordInsurancePaymentConsumerSliceEvidence(): MedicalRecordInsurancePaymentConsumerSliceEvidence {
  return {
    systemFamilyCode: "MEDICAL_RECORD_INSURANCE_PAYMENT",
    familyName: "病案、医保和支付",
    canonicalResources: ["Claim"],
    sourceSystems: ["MEDKERNEL_FRONTDESK"],
    consumer: "INSURANCE_AUDIT",
    consumerVerified: true,
    standardResourceVerified: true,
    evaluationRunVerified: true,
    rectificationClosedVerified: true,
    auditVerified: true,
    noAutoPaymentDecision: true,
    claimResourcePath: "clinicalContext.resources.claims[0]",
    issueIdPath: "medicalRecordQualityIssueEvidence.issueId",
    evaluationRunIdPath: "medicalRecordQualityIssueEvidence.evaluationRunId",
    scopeStatement:
      "病案医保支付代表消费者切片：质量管理真实前台用 Claim 标准患者资源驱动病案质控、DRG/DIP 分组、医保审核、评价运行、质量问题整改与审计回读；不代表完整病案医保支付系统族覆盖，不代表完整 DRG/DIP 或医保支付审核，不代表完整第三方系统族覆盖，不代表完整 S10，不代表完整上线验收。",
  };
}

async function closeRectificationFromAlertsUi(
  page: Page,
  audit: InsuranceAuditSummary,
): Promise<QualityActionResult<RectificationSummary>> {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/qc/alerts"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "质量问题与整改" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);

  const openAlert = await findInsuranceQualityAlert(page, audit.issueId, "OPEN");
  await showQualityAlertPageContainingSourceId(page, audit.findingId, "OPEN");
  const alertRow = qualityAlertRowBySourceId(page, audit.findingId);
  await expect(alertRow, "质量问题提醒列表应展示本轮医保审核质量问题").toBeVisible({
    timeout: 20_000,
  });
  expect(openAlert.sourceId, "质量提醒必须指向本轮质量问题").toBe(audit.findingId);
  await alertRow.getByRole("button", { name: "查看处置证据" }).click();
  const drawer = qualityAlertDrawer(page);
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText(/整改任务 .* 已派发/)).toBeVisible({ timeout: 20_000 });
  await drawer
    .getByLabel("整改说明")
    .fill("质量管理入口矩阵：责任科室已复核医保结算事实并补充病案费用说明。");
  await drawer.getByRole("textbox", { name: /整改证据/ }).fill(`QC-MATRIX-${audit.issueId}`);

  const submitPromise = waitForPostMatching(page, /\/api\/v1\/engine\/rectifications\/.+\/submit/u);
  await drawer.getByRole("button", { name: "提交整改证据" }).click();
  const submitResponse = await submitPromise;
  const submitText = await submitResponse.text();
  expect(
    submitResponse.ok(),
    `质量整改提交应成功 status=${submitResponse.status()} body=${submitText}`,
  ).toBe(true);
  const submit = JSON.parse(submitText) as { data?: { taskId?: string; taskStatus?: string } };
  const taskId = submit.data?.taskId ?? "";
  expect(taskId, "整改提交响应应返回任务 ID").toBeTruthy();
  expect(submit.data?.taskStatus, "整改提交后任务应进入待复核").toBe("SUBMITTED");

  await page.goto(appPath("/qc/alerts"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await showQualityAlertPageContainingSourceId(page, audit.findingId, "OPEN");
  const submittedRow = qualityAlertRowBySourceId(page, audit.findingId);
  await expect(submittedRow, "整改提交后质量问题仍应可复核").toBeVisible({ timeout: 20_000 });
  await submittedRow.getByRole("button", { name: "查看处置证据" }).click();
  const reviewDrawer = qualityAlertDrawer(page);
  await expect(reviewDrawer.getByText(/整改任务 .* 待复核/)).toBeVisible({ timeout: 20_000 });
  await reviewDrawer
    .getByLabel("复核意见")
    .fill("质量管理入口矩阵：整改证据充分，医保结算质量问题已完成复核。");
  await reviewDrawer.getByRole("textbox", { name: /复核证据/ }).fill(`QC-REVIEW-${audit.issueId}`);

  const reviewPromise = waitForPostMatching(page, /\/api\/v1\/engine\/rectifications\/.+\/review/u);
  await reviewDrawer.getByRole("button", { name: "复核通过并关闭" }).click();
  const reviewResponse = await reviewPromise;
  const reviewText = await reviewResponse.text();
  expect(
    reviewResponse.ok(),
    `质量整改复核应成功 status=${reviewResponse.status()} body=${reviewText}`,
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

  const closedAlert = await findInsuranceQualityAlert(page, audit.issueId, "RESOLVED");
  expect(closedAlert.sourceId, "闭环提醒必须仍指向本轮质量问题").toBe(audit.findingId);
  const taskAuditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "rectification_task",
    resourceId: taskId,
  });
  const findingAuditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "quality_finding",
    resourceId: audit.findingId,
  });
  expect(taskAuditVerified && findingAuditVerified, "整改提交和复核应有真实审计链").toBe(true);
  await ensureReadySession(page, "engine-operator");

  return {
    payload: { findingId: audit.findingId, taskId },
    action: {
      menuKey: "qc-alerts",
      role: "engine-operator",
      path: "/qc/alerts",
      frontdeskAction: "医疗引擎运营员前台提交整改证据并复核关闭质量问题",
      serviceOperation:
        "POST /api/v1/engine/rectifications/{taskId}/submit + POST /api/v1/engine/rectifications/{taskId}/review",
      serviceStatus: minSuccessfulStatus(submitResponse.status(), reviewResponse.status()),
      readbackVerified: Boolean(closedAlert.sourceId === audit.findingId),
      auditVerified: taskAuditVerified && findingAuditVerified,
    },
  };
}

async function drilldownQualityDashboardFromUi(
  page: Page,
  rectification: RectificationSummary,
): Promise<QualityManagementEntryCoreActionEvidence> {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/qc/dashboard"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("heading", { name: "质量风险概览" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await enableEvidenceDetails(page);
  await choosePageSelectOptionByText(page, "时间范围", "全量");
  const drilldownResponsePromise = waitForGetMatching(
    page,
    "/api/v1/engine/quality/dashboard/drilldown",
    (url) => url.searchParams.get("type") === "RECTIFICATION",
  );
  await choosePageSelectOptionByText(page, "下钻类型", "整改证据");
  await page.getByRole("button", { name: "下钻问题证据" }).click();
  const drilldownResponse = await drilldownResponsePromise;
  const drilldownText = await drilldownResponse.text();
  expect(
    drilldownResponse.ok(),
    `质量风险概览下钻应返回成功 status=${drilldownResponse.status()} body=${drilldownText}`,
  ).toBe(true);
  const drilldown = JSON.parse(drilldownText) as {
    data?: {
      items?: Array<{ sourceId?: string; sourceType?: string }>;
      evidenceExport?: { exportId?: string };
    };
  };
  const drilldownIncludesTask = Boolean(
    drilldown.data?.items?.some(
      (item) => item.sourceId === rectification.taskId && item.sourceType === "rectification_task",
    ),
  );
  const drilldownHasExport = Boolean(drilldown.data?.evidenceExport?.exportId);
  expect(
    drilldownIncludesTask && drilldownHasExport,
    "下钻服务结果必须包含本轮整改任务和证据导出",
  ).toBe(true);
  const drilldownDialog = page.getByRole("dialog").filter({ hasText: "问题下钻证据" }).last();
  await expect(drilldownDialog).toBeVisible({ timeout: 20_000 });
  await expect(drilldownDialog).toContainText(rectification.taskId, { timeout: 20_000 });
  const dashboardReadback = await getApi(page, "/engine/quality/dashboard");
  await expectOk(dashboardReadback, "回读质量风险概览");
  const sourceAuditVerified = await auditEventExistsAsAuditor(page, {
    resourceType: "rectification_task",
    resourceId: rectification.taskId,
  });
  expect(sourceAuditVerified, "质量概览只读下钻必须绑定来源对象审计链").toBe(true);

  return {
    menuKey: "qc-dashboard",
    role: "engine-operator",
    path: "/qc/dashboard",
    frontdeskAction: "医疗引擎运营员前台查看质量风险概览并下钻本轮问题证据",
    serviceOperation:
      "GET /api/v1/engine/quality/dashboard + GET /api/v1/engine/quality/dashboard/drilldown",
    serviceStatus: minSuccessfulStatus(dashboardReadback.status(), drilldownResponse.status()),
    readbackVerified:
      (await responseData(dashboardReadback)) !== null &&
      drilldownIncludesTask &&
      drilldownHasExport,
    auditVerified: sourceAuditVerified,
    sourceAuditVerified,
  };
}

async function enableEvidenceDetails(page: Page) {
  const details = page.getByRole("switch", { name: "证据详情" }).first();
  if ((await details.count()) === 0) {
    return;
  }
  await expect(details).toBeVisible({ timeout: 20_000 });
  if (!(await details.isChecked())) {
    await details.click();
  }
}

async function ensureQualityDepartment(
  page: Page,
  suffix: string,
): Promise<{ id: string; name: string }> {
  await ensureReadySession(page, "engine-operator");
  const existing = await getApi(
    page,
    "/engine/org/org-units?level=DEPARTMENT&status=ACTIVE&page=1&size=20",
  );
  await expectOk(existing, "读取质量管理责任科室");
  const department = pageItems(await responseData(existing)).find(
    (item) => textField(item, "id") && textField(item, "name"),
  );
  const id = textField(department, "id");
  const name = textField(department, "name");
  if (id && name) {
    return { id, name };
  }

  await ensureReadySession(page, "platform-admin");
  const hospital = await resolveLocalRehearsalHospital(page);
  const created = await postApi(page, "/engine/org/org-units", {
    parentId: hospital.id,
    level: "DEPARTMENT",
    code: `QC-MATRIX-DEPT-${suffix.toUpperCase()}`,
    name: `质量矩阵责任科${suffix.slice(-4)}`,
    namePinyin: "zhiliang juzhen zeren ke",
    status: "ACTIVE",
  });
  await expectOk(created, "创建质量管理入口矩阵责任科室");
  const createdDepartment = await responseData(created);
  const createdId = textField(createdDepartment, "id");
  const createdName = textField(createdDepartment, "name");
  if (!createdId || !createdName) {
    throw new Error("创建质量管理入口矩阵责任科室响应缺少 id/name");
  }
  await ensureReadySession(page, "engine-operator");
  return { id: createdId, name: createdName };
}

async function resolveLocalRehearsalHospital(page: Page): Promise<{ id: string; name: string }> {
  const response = await getApi(
    page,
    `/engine/org/org-units?keyword=${encodeURIComponent(
      "本地上线演练医院",
    )}&level=FACILITY&status=ACTIVE&page=1&size=20`,
  );
  await expectOk(response, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "name") === "本地上线演练医院" &&
      textField(item, "level") === "FACILITY" &&
      textField(item, "id"),
  );
  const hospitalId = textField(hospital, "id");
  const hospitalName = textField(hospital, "name");
  if (!hospitalId || !hospitalName) {
    throw new Error("本地上线演练医院必须存在，才能创建质量管理责任科室");
  }
  return { id: hospitalId, name: hospitalName };
}

async function activateHospitalRuntimeWithClaimIndicator(
  page: Page,
  indicator: ClaimIndicatorSummary,
): Promise<ClaimRuntimeActivationSummary> {
  await ensureReadySession(page, "engine-operator");
  const hospital = await resolveLocalRehearsalHospital(page);
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(
    baselineAssets.baselineReleaseId,
    "质量管理矩阵必须基于当前平台标准版本激活医院 runtime",
  ).toBeTruthy();
  for (const required of requiredRuntimeAssetsForRehearsal) {
    expect(
      baselineAssets.activeAssets.some(
        (asset) =>
          asset.assetType === required.assetType && asset.assetIdentity === required.assetIdentity,
      ),
      `平台标准版本缺少 ${required.assetType}:${required.assetIdentity}`,
    ).toBe(true);
  }

  const candidate = await readClaimRuntimeCandidate(page, hospital.id, indicator);
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospital.id)}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const currentRuntime = await responseData(current);
  const currentRelease = recordField(currentRuntime, "release");
  const currentReleaseId = textField(currentRelease, "releaseId");
  const currentPlatformBaselineReleaseId = textField(currentRelease, "platformBaselineReleaseId");
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    {
      assetType: candidate.assetType,
      assetIdentity: candidate.assetIdentity,
      versionId: candidate.versionId,
    },
  ]);
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId ?? "",
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest:
      currentReleaseId &&
      currentPlatformBaselineReleaseId &&
      currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
        ? await readPlatformUpgradeAnalysisDigest(
            page,
            hospital.id,
            baselineAssets.baselineReleaseId ?? "",
          )
        : null,
    activeAssets: uniqueRuntimeAssets(activeAssets),
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospital.id)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, "激活包含本轮 CLAIM 指标的医院生效版本");
  const releaseId = textField(await responseData(activated), "releaseId");
  expect(releaseId, "机构生效版本激活响应必须返回 releaseId").toBeTruthy();

  const currentAfter = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospital.id)}/runtime-releases/current`,
  );
  await expectOk(currentAfter, "回读包含本轮 CLAIM 指标的医院生效版本");
  const currentAfterRuntime = await responseData(currentAfter);
  expect(textField(recordField(currentAfterRuntime, "release"), "releaseId")).toBe(releaseId);
  assertRuntimeCarriesRequiredAssets(currentAfterRuntime);
  assertRuntimeContainsClaimIndicator(currentAfterRuntime, indicator, candidate);
  const runtimeReadback = runtimeReadbackEvidence(currentAfterRuntime);
  const runtimeConsumer = await readRuntimeConsumerEvidence(
    page,
    "读取包含 CLAIM 指标的第三方运行契约",
  );
  assertAssetsContain(runtimeConsumer.assets, [candidate], "第三方运行契约必须包含本轮 CLAIM 指标");
  expect(runtimeConsumer.releaseId, "第三方运行契约 releaseId 必须与 current 一致").toBe(
    runtimeReadback.releaseId,
  );
  expect(runtimeConsumer.revisionNo, "第三方运行契约 revisionNo 必须与 current 一致").toBe(
    runtimeReadback.revisionNo,
  );
  expect(runtimeConsumer.manifestSha256, "第三方运行契约 manifestSha256 必须与 current 一致").toBe(
    runtimeReadback.manifestSha256,
  );
  return {
    releaseId: releaseId ?? "",
    hospitalId: hospital.id,
    previousReleaseId: currentReleaseId,
    candidate: { ...candidate, versionId: candidate.versionId ?? "" },
    activationRequest,
    runtimeReadback,
    runtimeConsumer,
  };
}

function buildEvaluationAssetSupplyChainEvidence(options: {
  indicator: ClaimIndicatorSummary;
  runtime: ClaimRuntimeActivationSummary;
  insuranceAudit: InsuranceAuditSummary;
  indicatorAuditVerified: boolean;
}): QualityManagementEvaluationAssetEvidence {
  expect(options.insuranceAudit.evaluationRunId, "医保审核必须返回评价运行").toBeTruthy();
  expect(options.insuranceAudit.findingId, "医保审核质量问题必须绑定本轮指标").toBeTruthy();
  return {
    assetType: "EVALUATION",
    assetIdentity: options.runtime.candidate.assetIdentity,
    versionId: options.runtime.candidate.versionId,
    indicatorId: options.indicator.indicatorId,
    indicatorPublished: true,
    indicatorActivated: true,
    runtimeActivationVerified: true,
    runtimeConsumerReadbackVerified: true,
    insuranceAuditEvaluationRunVerified: true,
    findingBoundToIndicatorVerified: true,
    auditVerified: options.indicatorAuditVerified,
    activationRequest: options.runtime.activationRequest,
    runtimeReadback: options.runtime.runtimeReadback,
    runtimeConsumer: options.runtime.runtimeConsumer,
  };
}

async function rollbackRuntimeAndAssertEvaluationRemoved(
  page: Page,
  runtime: ClaimRuntimeActivationSummary,
): Promise<QualityManagementRollbackNegativeEvidence> {
  const targetReleaseId = runtime.previousReleaseId;
  expect(targetReleaseId, "质量管理 EVALUATION 回滚负向证据必须有演练前机构生效版本").toBeTruthy();
  await ensureReadySession(page, "engine-operator");
  const removedAssets = [
    {
      assetType: "EVALUATION" as const,
      assetIdentity: runtime.candidate.assetIdentity,
      versionId: runtime.candidate.versionId,
    },
  ];
  const rollback = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(runtime.hospitalId)}/runtime-releases:rollback`,
    { targetReleaseId },
  );
  await expectOk(rollback, "回滚质量管理 EVALUATION 机构生效版本");
  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(runtime.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "回读质量管理 EVALUATION 回滚后机构生效版本");
  const currentRuntime = runtimeReadbackEvidence(await responseData(current));
  assertAssetsRemoved(currentRuntime.assets, removedAssets, "回滚后 current runtime");
  const runtimeConsumer = await readRuntimeConsumerEvidence(
    page,
    "读取质量管理 EVALUATION 回滚后第三方运行契约",
  );
  assertAssetsRemoved(runtimeConsumer.assets, removedAssets, "回滚后第三方运行契约");
  expect(runtimeConsumer.releaseId, "第三方运行契约 releaseId 必须与 current 一致").toBe(
    currentRuntime.releaseId,
  );
  expect(runtimeConsumer.revisionNo, "第三方运行契约 revisionNo 必须与 current 一致").toBe(
    currentRuntime.revisionNo,
  );
  expect(runtimeConsumer.manifestSha256, "第三方运行契约 manifestSha256 必须与 current 一致").toBe(
    currentRuntime.manifestSha256,
  );
  return {
    rollbackPosted: true,
    currentRuntimeReadbackVerified: true,
    runtimeConsumerReadbackVerified: true,
    consumer: "QUALITY_MANAGEMENT_EVALUATION_INDICATOR",
    consumerProbeMatchedRemovedAssets: false,
    removedAssets,
    currentRuntime,
    runtimeConsumer,
  };
}

async function readClaimRuntimeCandidate(
  page: Page,
  hospitalId: string,
  indicator: ClaimIndicatorSummary,
): Promise<RuntimeCandidateSummary> {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-candidates?assetType=EVALUATION&keyword=${encodeURIComponent(
      indicator.indicatorCode,
    )}&page=1&size=20`,
  );
  await expectOk(response, "读取本轮 CLAIM 指标医院 runtime 候选");
  const candidate = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "assetType") === "EVALUATION" &&
      textField(item, "assetIdentity") === indicator.indicatorCode &&
      textField(item, "status") === "PUBLISHED",
  );
  const versionId = textField(candidate, "versionId");
  expect(
    versionId,
    `本轮 CLAIM 指标 ${indicator.indicatorCode} 必须可作为医院 runtime 候选`,
  ).toBeTruthy();
  return {
    assetType: "EVALUATION",
    assetIdentity: indicator.indicatorCode,
    versionId: versionId ?? "",
    versionNo: textField(candidate, "versionNo"),
  };
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
  await expectOk(response, "读取质量管理矩阵平台升级分析");
  const digest = textField(await responseData(response), "analysisDigest");
  expect(digest, "平台升级分析必须返回 analysisDigest").toBeTruthy();
  return digest ?? "";
}

function assertRuntimeCarriesRequiredAssets(runtime: unknown) {
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const match = pageItems(runtime).find(
      (item) =>
        textField(item, "assetType") === required.assetType &&
        textField(item, "assetIdentity") === required.assetIdentity &&
        textField(item, "entryState") === "ACTIVE" &&
        Boolean(textField(item, "versionId")),
    );
    expect(
      match,
      `包含 CLAIM 指标的机构生效版本必须保留 ${required.assetType}:${required.assetIdentity}`,
    ).toBeTruthy();
  }
}

function assertRuntimeContainsClaimIndicator(
  runtime: unknown,
  indicator: ClaimIndicatorSummary,
  candidate: RuntimeCandidateSummary,
) {
  const match = pageItems(runtime).find(
    (item) =>
      textField(item, "assetType") === "EVALUATION" &&
      textField(item, "assetIdentity") === indicator.indicatorCode &&
      textField(item, "entryState") === "ACTIVE",
  );
  expect(match, `机构生效版本必须启用本轮 CLAIM 指标 ${indicator.indicatorCode}`).toBeTruthy();
  expect(textField(match, "versionId"), "本轮 CLAIM 指标必须使用医院候选版本").toBe(
    candidate.versionId,
  );
}

function runtimeReadbackEvidence(value: unknown): RuntimeReadbackEvidence {
  const evidence = {
    releaseId: requireLocalText(
      textField(recordField(value, "release"), "releaseId"),
      "current runtime 必须返回 releaseId",
    ),
    revisionNo: numberField(recordField(value, "release"), "revisionNo") ?? 0,
    manifestSha256: requireLocalText(
      textField(recordField(value, "release"), "manifestSha256"),
      "current runtime 必须返回 manifestSha256",
    ),
    assets: pageItems(value) as Array<Record<string, unknown>>,
  };
  expect(evidence.revisionNo, "current runtime 必须返回 revisionNo").toBeGreaterThan(0);
  expect(evidence.assets.length, "current runtime 必须返回资产清单").toBeGreaterThan(0);
  return evidence;
}

async function readRuntimeConsumerEvidence(page: Page, label: string) {
  const response = await getApi(
    page,
    "/engine/integration/knowledge-runtime/runtime-release/current",
  );
  await expectOk(response, label);
  const value = await responseData(response);
  const evidence = {
    contractVersion: "v1" as const,
    releaseId: requireLocalText(
      textField(value, "releaseId"),
      "runtime consumer 必须返回 releaseId",
    ),
    revisionNo: numberField(value, "revisionNo") ?? 0,
    manifestSha256: requireLocalText(
      textField(value, "manifestSha256"),
      "runtime consumer 必须返回 manifestSha256",
    ),
    assets: (Array.isArray(recordField(value, "assets"))
      ? recordField(value, "assets")
      : []) as Array<Record<string, unknown>>,
  };
  expect(textField(value, "contractVersion"), "runtime consumer 必须返回 v1 契约").toBe("v1");
  expect(evidence.revisionNo, "runtime consumer 必须返回 revisionNo").toBeGreaterThan(0);
  expect(evidence.assets.length, "runtime consumer 必须返回资产清单").toBeGreaterThan(0);
  return evidence;
}

function assertAssetsContain(
  assets: Array<Record<string, unknown>>,
  expectedAssets: Array<{ assetType: string; assetIdentity: string; versionId: string | null }>,
  label: string,
) {
  for (const expected of expectedAssets) {
    expect(
      assets.some((asset) => runtimeAssetMatches(asset, expected)),
      `${label} 必须包含 ${expected.assetType}:${expected.assetIdentity}`,
    ).toBe(true);
  }
}

function assertAssetsRemoved(
  assets: Array<Record<string, unknown>>,
  removedAssets: Array<{ assetType: string; assetIdentity: string; versionId: string }>,
  label: string,
) {
  for (const removed of removedAssets) {
    expect(
      assets.some((asset) => runtimeAssetMatches(asset, removed)),
      `${label} 不应继续包含本轮 ${removed.assetType}:${removed.assetIdentity}`,
    ).toBe(false);
  }
}

function runtimeAssetMatches(
  asset: Record<string, unknown>,
  candidate: { assetType: string; assetIdentity: string; versionId: string | null },
) {
  return (
    textField(asset, "assetType") === candidate.assetType &&
    textField(asset, "assetIdentity") === candidate.assetIdentity &&
    textField(asset, "versionId") === candidate.versionId
  );
}

function numberField(value: unknown, field: string) {
  const raw = recordField(value, field);
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && raw.trim().length > 0) {
    const parsed = Number(raw);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}

function requireLocalText(value: string | null, message: string) {
  expect(value, message).toBeTruthy();
  return value ?? "";
}

async function assertEvaluationIndicatorActive(
  page: Page,
  indicatorCode: string,
  indicatorId: string,
) {
  const response = await getApi(
    page,
    `/engine/evaluation/indicators?indicatorCode=${encodeURIComponent(
      indicatorCode,
    )}&page=1&size=20`,
  );
  await expectOk(response, "回读评价指标");
  const match = pageItems(await responseData(response)).find(
    (item) => textField(item, "indicatorId") === indicatorId,
  );
  expect(textField(match, "status"), "本轮评价指标必须回读为 ACTIVE").toBe("ACTIVE");
}

async function findingIdForInsuranceIssue(
  page: Page,
  issueId: string,
  evaluationRunId: string,
  indicator: ClaimIndicatorSummary,
) {
  const alert = await findInsuranceQualityAlert(page, issueId, "OPEN");
  const findingId = alert.sourceId ?? "";
  expect(findingId, "医保审核质量提醒必须指向 quality_finding").toBeTruthy();
  const detail = await qualityFindingDetail(page, findingId);
  expect(textField(detail.data?.finding, "runId"), "质量问题必须来自本轮医保审核评估运行").toBe(
    evaluationRunId,
  );
  expect(textField(detail.data?.finding, "indicatorId"), "质量问题必须绑定本轮评价指标").toBe(
    indicator.indicatorId,
  );
  return findingId;
}

async function findInsuranceQualityAlert(page: Page, issueId: string, status: "OPEN" | "RESOLVED") {
  const seenSourceIds: string[] = [];
  for (let pageNumber = 1; pageNumber <= 10; pageNumber += 1) {
    const response = await getApi(
      page,
      `/engine/quality/alerts?status=${status}&severity=ALL&page=${pageNumber}&size=50`,
    );
    await expectOk(response, `读取 ${status} 质量提醒第 ${pageNumber} 页`);
    const candidates = pageItems(await responseData(response)).filter(
      (item) => textField(item, "sourceType") === "quality_finding",
    );
    for (const candidate of candidates) {
      const sourceId = textField(candidate, "sourceId");
      if (!sourceId) continue;
      seenSourceIds.push(sourceId);
      const detail = await qualityFindingDetail(page, sourceId);
      if (textField(detail.data?.finding, "findingCode") === `INSURANCE.${issueId}`) {
        return { sourceId };
      }
    }
    if (candidates.length < 50) {
      break;
    }
  }
  throw new Error(
    `未找到本次医保审核 issueId=${issueId} 对应的 ${status} 质量提醒；候选=${seenSourceIds.join(
      ",",
    )}`,
  );
}

async function showQualityAlertPageContainingSourceId(
  page: Page,
  sourceId: string,
  status: "OPEN" | "RESOLVED",
) {
  await chooseQualityAlertFilter(page, "处置状态", status === "OPEN" ? "未处置" : "已闭环");
  await chooseQualityAlertFilter(page, "发现时间", "全量");
  await chooseQualityAlertFilter(page, "风险级别", "高风险");
  const pageNumber = await qualityAlertPageNumberForSourceId(page, sourceId, status);
  if (pageNumber > 1) {
    await page.getByRole("listitem", { name: String(pageNumber) }).click();
    await page.waitForLoadState("networkidle");
  }
  await expect(qualityAlertRowBySourceId(page, sourceId)).toBeVisible({ timeout: 20_000 });
}

async function qualityAlertPageNumberForSourceId(
  page: Page,
  sourceId: string,
  status: "OPEN" | "RESOLVED",
) {
  for (let pageNumber = 1; pageNumber <= 10; pageNumber += 1) {
    const response = await getApi(
      page,
      `/engine/quality/alerts?status=${status}&severity=HIGH_RISK&page=${pageNumber}&size=20`,
    );
    await expectOk(response, `定位 ${status} 质量提醒第 ${pageNumber} 页`);
    const items = pageItems(await responseData(response));
    if (items.some((item) => textField(item, "sourceId") === sourceId)) {
      return pageNumber;
    }
    if (items.length < 20) {
      break;
    }
  }
  throw new Error(`未在前 10 页高风险 ${status} 质量提醒中定位 sourceId=${sourceId}`);
}

async function qualityFindingDetail(page: Page, findingId: string) {
  const detail = await getApi(page, `/engine/evaluation/issues/${encodeURIComponent(findingId)}`);
  await expectOk(detail, `读取质量问题详情 ${findingId}`);
  return (await detail.json()) as {
    data?: {
      finding?: { findingCode?: string; runId?: string; indicatorId?: string };
      rectificationTask?: { taskId?: string; status?: string };
    };
  };
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
        return pageItems(await responseData(response)).some(
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
    `评价指标 ${options.button} 应成功 status=${response.status()} body=${text}`,
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
    `评价指标 ${options.button} 应成功 status=${response.status()} body=${text}`,
  ).toBe(true);
  const payload = JSON.parse(text) as { data?: { status?: string } };
  expect(payload.data?.status, `评价指标 ${options.button} 后状态应正确`).toBe(
    options.expectedStatus,
  );
  await expect(modal).toBeHidden({ timeout: 20_000 });
  return response;
}

function qualityAlertDrawer(page: Page) {
  return page.getByRole("dialog").filter({ hasText: "质量风险处置证据" }).last();
}

function qualityAlertRowBySourceId(page: Page, sourceId: string) {
  return page.locator(`[data-source-id="${cssStringEscape(sourceId)}"]`).first();
}

async function chooseQualityAlertFilter(page: Page, label: string, optionText: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = antSelectForCombobox(combobox);
  if ((await currentSelectText(select)) === optionText) return;
  await openAntdSelect(select, label);
  await chooseVisibleOption(page, optionText, true);
  await page.waitForLoadState("networkidle");
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = antSelectForCombobox(combobox);
  if ((await currentSelectText(select)) === optionText) return;
  await openAntdSelect(select, label);
  await chooseVisibleOption(page, optionText, true);
}

async function searchDialogOption(page: Page, dialog: Locator, label: string, searchText: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = antSelectForCombobox(combobox);
  await openAntdSelect(select, label);
  await combobox.fill(searchText);
  await chooseVisibleOption(page, searchText, false);
}

async function choosePageSelectOption(page: Page, label: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = antSelectForCombobox(combobox);
  await openAntdSelect(select, label);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await expect(option).toBeVisible({ timeout: 10_000 });
  await option.click();
}

async function searchPageSelectOption(
  page: Page,
  label: string,
  searchText: string,
  optionText: string,
) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = antSelectForCombobox(combobox);
  await openAntdSelect(select, label);
  await combobox.fill(searchText);
  await chooseVisibleOption(page, optionText, false);
}

async function choosePageSelectOptionByText(page: Page, label: string, optionText: string) {
  const combobox = page.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = antSelectForCombobox(combobox);
  if ((await currentSelectText(select)) === optionText) return;
  await openAntdSelect(select, label);
  await chooseVisibleOption(page, optionText, true);
}

async function chooseIndexedSelectOption(page: Page, combobox: Locator, optionText: string) {
  const select = antSelectForCombobox(combobox);
  if ((await currentSelectText(select)) === optionText) return;
  await openAntdSelect(select, optionText);
  await chooseVisibleOption(page, optionText, true);
}

function antSelectForCombobox(combobox: Locator) {
  return combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
}

async function openAntdSelect(select: Locator, label: string) {
  const selector = select.locator(".ant-select-selector").first();
  await expect(selector, `应能打开 ${label} 下拉框`).toBeVisible({ timeout: 10_000 });
  await selector.click();
}

async function chooseVisibleOption(page: Page, optionText: string, exact: boolean) {
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const pattern = exact
    ? new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`)
    : new RegExp(escapeRegExp(optionText));
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: pattern })
    .first();
  await expect(option, `下拉框应出现选项 ${optionText}`).toBeVisible({ timeout: 20_000 });
  await option.click();
}

async function currentSelectText(select: Locator) {
  const selected = select.locator(".ant-select-selection-item").first();
  if ((await selected.count()) === 0) return "";
  const title = await selected.getAttribute("title", { timeout: 1_000 }).catch(() => null);
  if (title) return title.trim();
  const text = await selected.textContent({ timeout: 1_000 }).catch(() => null);
  return text?.trim() ?? "";
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

function waitForGetMatching(page: Page, path: string, matches: (url: URL) => boolean) {
  return page.waitForResponse(
    (response) => {
      if (response.request().method() !== "GET" || !response.url().includes(path)) {
        return false;
      }
      return matches(new URL(response.url()));
    },
    { timeout: 30_000 },
  );
}

function waitForPostMatching(page: Page, pattern: RegExp) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && pattern.test(response.url()),
    { timeout: 30_000 },
  );
}

function minSuccessfulStatus(...statuses: number[]) {
  return Math.max(...statuses);
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function cssStringEscape(value: string) {
  return value.replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}
