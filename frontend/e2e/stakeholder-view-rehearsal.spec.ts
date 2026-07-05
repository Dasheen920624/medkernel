import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { appPath, ensureReadySession, type RoleAccount } from "./support/auth";

type StakeholderView = {
  code: string;
  label: string;
  role: RoleAccount;
  path: string;
  heading: string | RegExp;
  markers: Array<string | RegExp>;
  action?:
    | "PHYSICIAN_FEEDBACK"
    | "QUALITY_DRILLDOWN"
    | "AUDIT_EXPORT_VERIFY"
    | "ADAPTER_QUALITY_REPORT"
    | "REPORT_INTERPRETATION"
    | "FOLLOWUP_NURSE_HANDOFF"
    | "PHARMACIST_REVIEW"
    | "PATIENT_PROXY_QUESTIONNAIRE"
    | "PLATFORM_ADMIN_PERSONNEL"
    | "ENGINE_OPERATOR_PROVIDER_GOVERNANCE";
};

type RuntimeRecord = {
  code: string;
  label: string;
  role: RoleAccount;
  path: string;
  url: string;
  entryUrl: string;
  finalUrl: string;
  actions: string[];
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

type RecommendationContextOptions = {
  namePrefix: string;
  diagnosis: string;
  currentMedicationText: string;
  reason: string;
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

const stakeholderViews: StakeholderView[] = [
  {
    code: "PHYSICIAN",
    label: "医生",
    role: "clinical-user",
    path: "/cdss/fatigue",
    heading: "提醒与推荐",
    markers: ["必须医师确认", "医生反馈", "登记触发评估"],
    action: "PHYSICIAN_FEEDBACK",
  },
  {
    code: "NURSE",
    label: "护士",
    role: "clinical-user",
    path: "/clinical/followup",
    heading: "随访协同",
    markers: ["护士代填", "问卷回收", "异常回院处理"],
    action: "FOLLOWUP_NURSE_HANDOFF",
  },
  {
    code: "PHARMACIST",
    label: "药师",
    role: "clinical-user",
    path: "/cdss/fatigue",
    heading: "提醒与推荐",
    markers: ["药师复核", /联合用药|用药风险|DDI/u],
    action: "PHARMACIST_REVIEW",
  },
  {
    code: "MEDICAL_TECHNICIAN",
    label: "医技",
    role: "clinical-user",
    path: "/cdss/fatigue",
    heading: "提醒与推荐",
    markers: ["生成报告解读", "不会改写已签发报告"],
    action: "REPORT_INTERPRETATION",
  },
  {
    code: "QUALITY_CONTROLLER",
    label: "质控",
    role: "engine-operator",
    path: "/qc/dashboard",
    heading: "质量风险概览",
    markers: ["质量指标", "指标口径已记录", "下钻问题证据", "导出证据"],
    action: "QUALITY_DRILLDOWN",
  },
  {
    code: "PATIENT_PROXY",
    label: "患者代理",
    role: "clinical-user",
    path: "/clinical/followup",
    heading: "随访协同",
    markers: ["患者问卷回收", "患者自填", "患者报告"],
    action: "PATIENT_PROXY_QUESTIONNAIRE",
  },
  {
    code: "PLATFORM_ADMIN",
    label: "平台管理员",
    role: "platform-admin",
    path: "/admin/users",
    heading: "人员与账号",
    markers: ["任职", "登录账号", "组织范围"],
    action: "PLATFORM_ADMIN_PERSONNEL",
  },
  {
    code: "ENGINE_OPERATOR",
    label: "医疗引擎运营员",
    role: "engine-operator",
    path: "/knowledge/production",
    heading: "知识生产",
    markers: ["模型服务配置", "医学评测", "生产前校验"],
    action: "ENGINE_OPERATOR_PROVIDER_GOVERNANCE",
  },
  {
    code: "AUDITOR",
    label: "审计员",
    role: "auditor",
    path: "/admin/audit",
    heading: "审计与证据",
    markers: ["审计事件", "导出证据", "模型外调确认"],
    action: "AUDIT_EXPORT_VERIFY",
  },
  {
    code: "IT_MANAGER",
    label: "信息科长",
    role: "platform-admin",
    path: "/system/runtime-diagnostics",
    heading: "运行诊断",
    markers: ["服务契约", "追踪诊断", "扩展能力"],
    action: "ADAPTER_QUALITY_REPORT",
  },
  {
    code: "IMPLEMENTATION_ENGINEER",
    label: "实施工程师",
    role: "platform-admin",
    path: "/onboarding/guide",
    heading: "实施与验收",
    markers: ["实施步骤真实状态", "阻塞项", "下一处理入口"],
    action: "ADAPTER_QUALITY_REPORT",
  },
  {
    code: "HOSPITAL_EXECUTIVE",
    label: "院长",
    role: "engine-operator",
    path: "/qc/dashboard",
    heading: "质量风险概览",
    markers: ["质量成效", "风险热力", "闭环"],
    action: "QUALITY_DRILLDOWN",
  },
];

test.describe.configure({ mode: "serial" });

test.describe("全角色真实体验视角", () => {
  test("十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力", async ({
    page,
  }, testInfo) => {
    test.setTimeout(900_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];

    try {
      await page.setViewportSize({ width: 1440, height: 960 });
      expect(
        stakeholderViews.filter((view) => !view.action).map((view) => view.label),
        "十二类业务视角都必须有真实前台动作，不能只进入页面看标记",
      ).toEqual([]);
      for (const view of stakeholderViews) {
        await assertStakeholderView(page, view, runtime, records, testInfo);
      }
    } finally {
      await attachRuntimeRecords(testInfo, records);
    }
  });
});

async function assertStakeholderView(
  page: Page,
  view: StakeholderView,
  runtime: ReturnType<typeof collectRuntime>,
  records: RuntimeRecord[],
  testInfo: TestInfo,
) {
  await ensureReadySession(page, view.role);
  clearRuntime(runtime);
  await page.goto(appPath(view.path), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");

  await expect(
    page.locator("main").getByRole("heading", { name: view.heading }).first(),
    `${view.label} 应进入 ${view.path} 的真实主页面`,
  ).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expect(
    page.locator(".ant-spin-spinning"),
    `${view.label} 页面不应停留在加载中`,
  ).toHaveCount(0, { timeout: 30_000 });
  await setEvidenceDetails(page, false);

  for (const marker of view.markers) {
    await expect(
      page.getByText(marker).first(),
      `${view.label} 视角应展示业务能力 ${String(marker)}`,
    ).toBeVisible({ timeout: 30_000 });
  }

  const entryUrl = page.url();
  const actions = await performStakeholderAction(page, view);
  if (view.action) {
    expect(actions.length, `${view.label} 视角应记录至少一个真实前台动作`).toBeGreaterThan(0);
  }
  const finalUrl = page.url();
  await expectNoRootOverflow(page, view.label);
  const record = {
    code: view.code,
    label: view.label,
    role: view.role,
    path: view.path,
    url: entryUrl,
    entryUrl,
    finalUrl,
    actions,
    browserErrors: [...runtime.browserErrors],
    serverErrors: [...runtime.serverErrors],
    networkFailures: [...runtime.networkFailures],
  };
  records.push(record);
  expect(record.browserErrors, `${view.label} 视角不应产生浏览器错误`).toEqual([]);
  expect(record.serverErrors, `${view.label} 视角不应产生 HTTP 错误`).toEqual([]);
  expect(record.networkFailures, `${view.label} 视角不应产生网络失败`).toEqual([]);

  const screenshotPath = testInfo.outputPath(`stakeholder-${view.code.toLowerCase()}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(`stakeholder-${view.code.toLowerCase()}`, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function performStakeholderAction(page: Page, view: StakeholderView) {
  if (view.action === "PHYSICIAN_FEEDBACK") {
    return performPhysicianFeedbackAction(page, view);
  }
  if (view.action === "QUALITY_DRILLDOWN") {
    return performQualityDrilldownAction(page, view);
  }
  if (view.action === "AUDIT_EXPORT_VERIFY") {
    return performAuditExportVerifyAction(page, view);
  }
  if (view.action === "ADAPTER_QUALITY_REPORT") {
    return performAdapterQualityReportAction(page, view);
  }
  if (view.action === "REPORT_INTERPRETATION") {
    return performReportInterpretationAction(page, view);
  }
  if (view.action === "FOLLOWUP_NURSE_HANDOFF") {
    return performFollowupPlanAction(page, view, {
      source: "护士代填",
      questionnaire: "护士代填记录：患者反馈夜间咳嗽增加，已提醒遵医嘱复诊；未写入患者明文身份。",
      abnormal: true,
    });
  }
  if (view.action === "PHARMACIST_REVIEW") {
    return performPharmacistReviewAction(page, view);
  }
  if (view.action === "PATIENT_PROXY_QUESTIONNAIRE") {
    return performFollowupPlanAction(page, view, {
      source: "患者自填",
      questionnaire:
        "患者代理回收：患者自述活动后气促较前增加，已知晓回院提醒；不填写电话、住址或证件号。",
      abnormal: false,
    });
  }
  if (view.action === "PLATFORM_ADMIN_PERSONNEL") {
    return performPlatformAdminPersonnelAction(page, view);
  }
  if (view.action === "ENGINE_OPERATOR_PROVIDER_GOVERNANCE") {
    return performEngineOperatorProviderGovernanceAction(page, view);
  }

  return [];
}

async function performPhysicianFeedbackAction(page: Page, view: StakeholderView) {
  const cardId = await createRecommendationCardForStakeholderAction(page, view, {
    namePrefix: "医",
    diagnosis: "真实前台医生用药决策主题",
    currentMedicationText: "华法林、阿司匹林",
    reason: "全角色真实演练：医生从患者 360 建立当前用药上下文并亲自确认推荐；不写入患者明文身份。",
  });
  await expect(
    page.locator("main").getByRole("heading", { name: "提醒与推荐" }).first(),
    `${view.label} 应能在提醒与推荐页登记医师反馈`,
  ).toBeVisible({ timeout: 30_000 });
  expect(cardId, `${view.label} 医师反馈应由当前前台用药上下文生成推荐卡`).toBeTruthy();
  await page.getByLabel("患者或证据线索").fill(cardId ?? "");
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const drawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await drawer.getByRole("tab", { name: /医师反馈/u }).click();
  await drawer
    .getByLabel("采纳说明（可选）")
    .fill("医生已结合当前病情确认联用风险，需回到 HIS 完成线下医嘱调整。");
  const feedbackResponsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "确认采纳建议" }).click();
  const feedbackResponse = await feedbackResponsePromise;
  const feedbackText = await feedbackResponse.text();
  expect(
    feedbackResponse.ok(),
    `${view.label} 登记医师采纳反馈应返回成功 status=${feedbackResponse.status()} body=${feedbackText}`,
  ).toBe(true);
  await expect(drawer.getByText(/医生\s*·\s*采纳建议/u)).toBeVisible({ timeout: 30_000 });
  return ["前台建立医生用药上下文、触发推荐评估并登记医师采纳反馈"];
}

async function performQualityDrilldownAction(page: Page, view: StakeholderView) {
  const typeSelect = page
    .getByRole("combobox", { name: "下钻类型" })
    .locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
    );
  await typeSelect.locator(".ant-select-selector").click({ force: true });
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const responsePromise = waitForGet(page, "/engine/quality/dashboard/drilldown");
  await dropdown.getByText("整改证据", { exact: true }).click();
  const response = await responsePromise;
  expect(response.ok(), `${view.label} 切换下钻证据应返回真实服务端结果`).toBe(true);

  await page.getByRole("button", { name: "下钻问题证据" }).click();
  const drawer = page.locator(".ant-drawer-content").filter({ hasText: "问题下钻证据" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText("问题下钻证据", { exact: true })).toBeVisible();
  await expect(
    drawer.getByText(/证据包已生成|已按当前筛选条件生成证据包|证据导出编号/u).first(),
  ).toBeVisible({ timeout: 20_000 });
  await expectDrawerSettledInViewport(page, drawer, `${view.label} 下钻证据抽屉`);
  return ["切换质量下钻类型并读取整改证据"];
}

async function performAuditExportVerifyAction(page: Page, view: StakeholderView) {
  const reason = `审计员复核导出证据 ${Date.now()}`;
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
    `${view.label} 确认审计导出范围应返回成功 status=${confirmResponse.status()} body=${confirmText}`,
  ).toBe(true);
  const confirmation = JSON.parse(confirmText) as { data?: { confirmationId?: string } };
  const confirmationId = confirmation.data?.confirmationId;
  expect(confirmationId, `${view.label} 确认导出范围应返回确认编号`).toBeTruthy();

  await page.getByRole("tab", { name: "导出记录" }).click();
  await setEvidenceDetails(page, false);

  const row = page.getByRole("row").filter({ hasText: reason });
  await expect(row).toBeVisible({ timeout: 20_000 });
  await expect(row).toContainText("审计导出任务");
  await expect(row).not.toContainText(confirmationId ?? "");

  const exportResponsePromise = waitForPost(page, "/large-lists/exports");
  const completeResponsePromise = waitForPost(
    page,
    `/compliance/exports/${confirmationId}:complete-from-job`,
  );
  await row
    .getByRole("button", {
      name: "生成导出文件 审计导出任务",
    })
    .click();
  const exportDialog = page.getByRole("dialog", { name: "生成已确认导出文件" });
  await expect(exportDialog).toBeVisible({ timeout: 10_000 });
  await exportDialog.getByRole("button", { name: "确认生成导出文件" }).click();
  const exportResponse = await exportResponsePromise;
  const exportText = await exportResponse.text();
  expect(
    exportResponse.ok(),
    `${view.label} 生成审计导出文件应返回成功 status=${exportResponse.status()} body=${exportText}`,
  ).toBe(true);
  const completeResponse = await completeResponsePromise;
  const completeText = await completeResponse.text();
  expect(
    completeResponse.ok(),
    `${view.label} 完成审计导出记录应返回成功 status=${completeResponse.status()} body=${completeText}`,
  ).toBe(true);
  await expect(row).toContainText("已导出", { timeout: 60_000 });
  await expect(row.getByRole("link", { name: "下载文件" })).toBeVisible({ timeout: 30_000 });

  await expect(row.getByRole("button", { name: "查看证据 审计导出任务" })).toBeVisible({
    timeout: 30_000,
  });
  await setEvidenceDetails(page, true);
  await expect(row).toContainText(confirmationId ?? "");
  await expect(row.getByRole("button", { name: `查看证据 ${confirmationId}` })).toBeVisible({
    timeout: 30_000,
  });
  await row.getByRole("button", { name: `查看证据 ${confirmationId}` }).click();
  const evidenceDialog = page.getByRole("dialog", { name: "导出证据" });
  await expect(evidenceDialog).toBeVisible({ timeout: 10_000 });

  const verifyResponsePromise = waitForPost(page, "/compliance/evidence/snapshots/");
  await evidenceDialog.getByRole("button", { name: "验签导出证据" }).click();
  const verifyResponse = await verifyResponsePromise;
  const verifyText = await verifyResponse.text();
  expect(
    verifyResponse.ok(),
    `${view.label} 验签导出证据应返回成功 status=${verifyResponse.status()} body=${verifyText}`,
  ).toBe(true);
  await expect(evidenceDialog.getByText("证据验签通过")).toBeVisible({ timeout: 20_000 });

  return ["确认审计导出范围、生成导出文件并验签导出证据"];
}

async function performAdapterQualityReportAction(page: Page, view: StakeholderView) {
  if (view.path === "/system/runtime-diagnostics") {
    await page.getByRole("tab", { name: "扩展能力" }).click();
    await expect(
      page.getByRole("button", { name: /登记扩展能力/u }),
      `${view.label} 应能从运行诊断页看到扩展能力授权边界`,
    ).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("边界", { exact: true }).first()).toBeVisible({
      timeout: 30_000,
    });
  }

  await page.goto(appPath("/adapter/hub"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "系统接入" }).first(),
    `${view.label} 应能进入系统接入页生成上线前数据质量报告`,
  ).toBeVisible({ timeout: 30_000 });
  await setEvidenceDetails(page, false);
  await expect(
    page.getByText(/过敏与不良反应 · 可由外部系统接入|患者信息 · 可由外部系统接入/).first(),
    `${view.label} 系统接入默认层应展示业务接入口径`,
  ).toBeVisible({ timeout: 30_000 });
  await expect(
    page.locator("main").getByText(/allergyIntolerance\.|NOT_CONNECTED/),
    `${view.label} 系统接入默认层不应泄漏技术字段路径或原始枚举`,
  ).toHaveCount(0);

  await page.getByRole("tab", { name: "数据质量看板" }).click();
  const reportResponsePromise = waitForPost(page, "/engine/integration/data-quality/reports");
  await page.getByRole("button", { name: "生成质量报告" }).click();
  const reportResponse = await reportResponsePromise;
  const reportText = await reportResponse.text();
  expect(
    reportResponse.ok(),
    `${view.label} 生成系统接入数据质量报告应返回成功 status=${reportResponse.status()} body=${reportText}`,
  ).toBe(true);

  const reportCard = page.locator(".ant-card").filter({ hasText: "数据质量报告" }).last();
  await expect(reportCard).toBeVisible({ timeout: 30_000 });
  await expect(reportCard.getByText("数据质量报告已生成")).toBeVisible({ timeout: 30_000 });
  await expect(reportCard.getByText("缺口摘要")).toBeVisible();
  await expect(
    reportCard.getByText("未接通适配器").first(),
    `${view.label} 数据质量报告默认摘要应使用业务可读文案`,
  ).toBeVisible({ timeout: 30_000 });
  await expect(
    reportCard.getByText(/NOT_CONNECTED/),
    `${view.label} 数据质量报告默认摘要不应展示原始枚举`,
  ).toHaveCount(0);
  return [
    view.path === "/system/runtime-diagnostics"
      ? "查看运行诊断扩展能力授权边界并生成系统接入数据质量报告"
      : "生成系统接入数据质量报告",
  ];
}

async function performPlatformAdminPersonnelAction(page: Page, view: StakeholderView) {
  const suffix = Date.now().toString(36);
  const employeeNo = `IMPL-STK-${suffix.toUpperCase()}`;
  const displayName = `实施演练人员${suffix.slice(-4)}`;

  await page.getByRole("button", { name: "新增人员" }).click();
  const dialog = page.getByRole("dialog", { name: "新增人员" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("院内人员身份").fill(employeeNo);
  await dialog.getByLabel("姓名").fill(displayName);
  await chooseDialogOption(page, dialog, "人员类型", "实施与运维人员");
  await selectFirstDialogOption(page, dialog, "所属机构");
  await dialog.getByLabel("岗位或职务").fill("上线验收工程师");
  await dialog.getByRole("checkbox", { name: "同时开通登录账号" }).uncheck();

  const createResponsePromise = waitForPost(page, "/compliance/personnel");
  await dialog.getByRole("button", { name: "建立人员档案" }).click();
  const createResponse = await createResponsePromise;
  const createText = await createResponse.text();
  expect(
    createResponse.ok(),
    `${view.label} 新增实施人员档案应返回成功 status=${createResponse.status()} body=${createText}`,
  ).toBe(true);
  const created = JSON.parse(createText) as {
    data?: { person?: { personId?: string; employeeNo?: string }; account?: unknown };
  };
  expect(created.data?.person?.personId, `${view.label} 新增人员应返回人员身份`).toBeTruthy();
  expect(created.data?.person?.employeeNo).toBe(employeeNo);
  expect(created.data?.account ?? null, `${view.label} 演练不得生成一次性账号密码截图`).toBeNull();
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  await page.getByLabel("搜索人员").fill(displayName);
  await page.getByLabel("搜索人员").press("Enter");
  const row = page.getByRole("row", { name: new RegExp(escapeRegExp(displayName)) }).first();
  await expect(row).toBeVisible({ timeout: 30_000 });
  await row.getByRole("button", { name: "查看" }).click();
  const drawer = page.locator(".ant-drawer-content").filter({ hasText: "人员档案" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expectDrawerSettledInViewport(page, drawer, `${view.label} 人员详情抽屉`);
  await expect(drawer.getByText(displayName, { exact: true }).first()).toBeVisible();
  await expect(drawer.getByText("账号与身份来源")).toBeVisible();
  await expect(drawer.getByText("未开通", { exact: true }).first()).toBeVisible();
  return ["前台新增实施人员档案并核查任职、账号与身份来源治理边界"];
}

async function performEngineOperatorProviderGovernanceAction(page: Page, view: StakeholderView) {
  const healthButton = page.getByRole("button", { name: "健康检查" }).first();
  if (!(await healthButton.isVisible({ timeout: 2_000 }).catch(() => false))) {
    return registerModelProviderForStakeholderAction(page, view);
  }

  const healthResponsePromise = waitForPost(page, "/health-check");
  await healthButton.click();
  const healthResponse = await healthResponsePromise;
  const healthText = await healthResponse.text();
  expect(
    healthResponse.ok(),
    `${view.label} 模型服务健康检查应返回成功 status=${healthResponse.status()} body=${healthText}`,
  ).toBe(true);
  const health = JSON.parse(healthText) as { data?: { providerCode?: string; status?: string } };
  expect(health.data?.providerCode, `${view.label} 健康检查应返回模型服务身份`).toBeTruthy();
  expect(health.data?.status, `${view.label} 健康检查应返回真实连接状态`).toBeTruthy();
  await expect(page.getByText(/健康|待健康检查|连接异常/u).first()).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("生产前校验", { exact: true }).first()).toBeVisible();
  return ["对模型服务执行真实健康检查并复核生产前校验状态"];
}

async function registerModelProviderForStakeholderAction(page: Page, view: StakeholderView) {
  const providerCode = `stakeholder-ollama-${Date.now().toString(36)}`;
  const registerButton = page.getByRole("button", { name: "登记模型服务" }).first();
  await expect(
    registerButton,
    `${view.label} 空环境应能先通过前台登记模型服务，再保持停用并等待真实健康检查`,
  ).toBeVisible({ timeout: 30_000 });
  await registerButton.click();

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
    `${view.label} 登记模型服务应返回成功 status=${upsertResponse.status()} body=${upsertText}`,
  ).toBe(true);
  const upserted = JSON.parse(upsertText) as {
    data?: { providerCode?: string; providerType?: string; enabled?: boolean; status?: string };
  };
  expect(upserted.data?.providerCode).toBe(providerCode);
  expect(upserted.data?.providerType).toBe("OLLAMA");
  expect(upserted.data?.enabled, `${view.label} 新登记模型服务不得自动启用`).toBe(false);
  expect(upserted.data?.status, `${view.label} 新登记模型服务应等待真实健康检查`).toBe(
    "NOT_CONNECTED",
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByText("院内 Ollama").first()).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("medkernel-qwen25:1.5b-v1").first()).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("待健康检查").first()).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText("生产前校验", { exact: true }).first()).toBeVisible();
  return ["前台登记院内模型服务并复核生产前校验保持真实阻断"];
}

async function performReportInterpretationAction(page: Page, view: StakeholderView) {
  const snapshot = await createContextSnapshotForReportInterpretation(page, view);
  await expect(page.getByRole("button", { name: "生成报告解读" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "生成报告解读" }).click();

  await expect(
    page.locator("main").getByRole("heading", { name: "提醒与推荐" }).first(),
    `${view.label} 应能进入提醒与推荐页生成医技报告解读`,
  ).toBeVisible({ timeout: 30_000 });
  const dialog = page.getByRole("dialog", { name: "生成医技报告解读" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await expect(dialog.getByText("已从患者 360 带入当前上下文")).toBeVisible({
    timeout: 10_000,
  });
  await expectDialogInputValueAbsent(
    dialog,
    snapshot.patientId,
    `${view.label} 医技报告解读弹窗不应暴露患者主索引`,
  );
  if (snapshot.encounterId) {
    await expectDialogInputValueAbsent(
      dialog,
      snapshot.encounterId,
      `${view.label} 医技报告解读弹窗不应暴露就诊号`,
    );
  }

  const interpretResponsePromise = waitForPost(
    page,
    "/engine/recommendations/report-interpretation",
  );
  await dialog.getByRole("button", { name: "生成报告解读" }).click();
  const interpretResponse = await interpretResponsePromise;
  const interpretText = await interpretResponse.text();
  expect(
    interpretResponse.ok(),
    `${view.label} 生成医技报告解读应返回成功 status=${interpretResponse.status()} body=${interpretText}`,
  ).toBe(true);
  const interpretation = JSON.parse(interpretText) as { data?: { interpretations?: unknown[] } };
  expect(
    Array.isArray(interpretation.data?.interpretations),
    `${view.label} 医技报告解读响应应返回 interpretations 数组`,
  ).toBe(true);
  expect(
    interpretation.data?.interpretations?.length ?? 0,
    `${view.label} 医技报告解读必须基于前台录入的已签发报告生成至少 1 项结果`,
  ).toBeGreaterThan(0);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await page.goto(appPath("/workflow/todos"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "协同任务" }).first(),
    `${view.label} 应能在协同任务查看报告解读闭环待办`,
  ).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText(/报告解读 [1-9]\d* 项/u)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole("link", { name: "打开报告上下文" }).first()).toBeVisible({
    timeout: 30_000,
  });
  return ["从患者360带入当前上下文并生成医技报告解读，协同任务出现报告解读待办"];
}

async function performFollowupPlanAction(
  page: Page,
  view: StakeholderView,
  options: { source: "护士代填" | "患者自填"; questionnaire: string; abnormal: boolean },
) {
  const suffix = `${view.code.toLowerCase()}-${Date.now().toString(36)}`;
  const isNurse = options.source === "护士代填";
  const template = await createFollowupTemplateForStakeholderAction(page, view, suffix);
  await publishFollowupTemplateForStakeholderAction(page, view, template);
  const snapshot = await createContextSnapshotForStakeholderAction(page, view, {
    namePrefix: isNurse ? "护" : "患",
    diagnosis: isNurse ? "真实前台护士随访交接主题" : "真实前台患者代理问卷回收主题",
    reason: isNurse
      ? "全角色真实演练：护士从患者 360 建立随访交接上下文，不写入患者明文身份。"
      : "全角色真实演练：为患者代理问卷回收建立随访上下文，不写入电话、住址或证件号。",
  });

  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "随访协同" }).first(),
    `${view.label} 应能进入随访协同页生成并办理随访计划`,
  ).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: "生成随访计划" }).click();
  const dialog = page.getByRole("dialog", { name: "生成随访计划" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("随访快照患者信息").fill(snapshot.patientId);
  await expect(dialog.getByRole("button", { name: "选择第 1 个随访上下文快照" })).toBeVisible({
    timeout: 30_000,
  });
  await dialog.getByRole("button", { name: "选择第 1 个随访上下文快照" }).click();
  await chooseDialogOption(page, dialog, "随访风险分层", "中风险");
  await searchDialogOption(page, dialog, "随访方案", template.defaultName, template.defaultName);

  const planResponsePromise = waitForPost(page, "/engine/followup/plans/generate");
  await dialog.getByRole("button", { name: /生\s*成/ }).click();
  const planResponse = await planResponsePromise;
  const planText = await planResponse.text();
  expect(
    planResponse.ok(),
    `${view.label} 生成随访计划应返回成功 status=${planResponse.status()} body=${planText}`,
  ).toBe(true);
  const plan = JSON.parse(planText) as { data?: { planId?: string; tasks?: Array<unknown> } };
  expect(plan.data?.planId, `${view.label} 随访计划生成响应应返回计划身份`).toBeTruthy();
  expect(plan.data?.tasks?.length ?? 0, `${view.label} 随访计划应生成可办理任务`).toBeGreaterThan(
    0,
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  const drawer = await openFollowupPlanDrawerForTemplate(
    page,
    view,
    template.defaultName,
    snapshot.patientId,
  );
  const fillButton = drawer.getByRole("button", { name: /填\s*报/ }).first();
  await expect(fillButton, `${view.label} 生成计划后应有可办理的问卷任务`).toBeVisible({
    timeout: 30_000,
  });
  await expect(drawer.getByText(template.name)).toHaveCount(0);
  await expect(drawer.getByText(/上线复演/)).toHaveCount(0);
  await fillButton.click();
  await chooseDialogOption(page, drawer, "提交来源", options.source);
  await drawer.getByLabel("问卷回收内容").fill(options.questionnaire);
  const questionnaireResponsePromise = waitForPost(page, "/engine/followup/questionnaires");
  await drawer.getByRole("button", { name: "提交问卷" }).click();
  const questionnaireResponse = await questionnaireResponsePromise;
  const questionnaireText = await questionnaireResponse.text();
  expect(
    questionnaireResponse.ok(),
    `${view.label} 提交随访问卷应返回成功 status=${questionnaireResponse.status()} body=${questionnaireText}`,
  ).toBe(true);
  await expect(drawer.getByText("请选择一个待办随访任务后提交问卷回收内容")).toBeVisible({
    timeout: 20_000,
  });

  if (!options.abnormal) {
    return ["生成随访计划并完成患者代理问卷回收"];
  }

  await chooseDialogOption(page, drawer, "回院风险等级", "高风险");
  await drawer
    .getByLabel("异常症状或情况")
    .fill("护士随访发现夜间咳嗽与活动后气促加重，需要回院复核。");
  await drawer
    .getByLabel("医护处理建议")
    .fill("护士已提示患者尽快回院，由责任医生复核后决定线下处置；本页不自动开嘱。");
  const abnormalResponsePromise = waitForPost(page, "/engine/followup/abnormal-reports");
  await drawer.getByRole("button", { name: "登记异常回院" }).click();
  const abnormalResponse = await abnormalResponsePromise;
  const abnormalText = await abnormalResponse.text();
  expect(
    abnormalResponse.ok(),
    `${view.label} 登记异常回院应返回成功 status=${abnormalResponse.status()} body=${abnormalText}`,
  ).toBe(true);
  await expect(drawer.getByText("异常回院证据已登记")).toBeVisible({ timeout: 20_000 });
  return ["生成随访计划并完成护士代填问卷与异常回院登记"];
}

async function openFollowupPlanDrawerForTemplate(
  page: Page,
  view: StakeholderView,
  templateName: string,
  patientId: string,
) {
  const drawer = page.getByRole("dialog", { name: "随访计划办理" });
  const fillButton = drawer.getByRole("button", { name: /填\s*报/ }).first();
  if (await fillButton.isVisible({ timeout: 1_000 }).catch(() => false)) {
    return drawer;
  }
  if (await drawer.isVisible({ timeout: 1_000 }).catch(() => false)) {
    await drawer.getByRole("button", { name: /close/i }).click();
    await expect(drawer).toBeHidden({ timeout: 5_000 });
  }

  await page.getByPlaceholder("按患者线索检索").fill(patientId);
  const listResponsePromise = waitForGet(page, "/engine/followup/plans");
  await page.getByRole("button", { name: /查\s*询/ }).click();
  const listResponse = await listResponsePromise;
  expect(listResponse.ok(), `${view.label} 按本次患者线索查询随访计划应返回成功`).toBe(true);

  const templateRow = page
    .getByRole("row", { name: new RegExp(escapeRegExp(templateName)) })
    .first();
  const row = (await templateRow.isVisible({ timeout: 2_000 }).catch(() => false))
    ? templateRow
    : page
        .getByRole("row")
        .filter({ has: page.getByRole("button", { name: /查看与办理/ }) })
        .first();
  await expect(row, `${view.label} 新生成随访计划应能按患者线索收窄后定位办理`).toBeVisible({
    timeout: 30_000,
  });
  await row.getByRole("button", { name: /查看与办理/ }).click();
  await expect(drawer).toBeVisible({ timeout: 30_000 });
  await expect(drawer.getByText(templateName, { exact: false }).first()).toBeVisible({
    timeout: 30_000,
  });
  return drawer;
}

async function createFollowupTemplateForStakeholderAction(
  page: Page,
  view: StakeholderView,
  suffix: string,
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "随访协同" }).first(),
    `${view.label} 应能进入随访协同页创建随访方案`,
  ).toBeVisible({ timeout: 30_000 });
  await page.getByRole("tab", { name: "随访方案" }).click();

  const templateCode = `FUP.STAKEHOLDER.${suffix.toUpperCase()}`;
  const templateDefaultName = `全角色${view.label}随访方案`;
  const templateDisplayName = `${templateDefaultName}（${businessRehearsalBatchLabel(suffix)}）`;
  const templateName = `${templateDisplayName} ${suffix}`;
  await page.getByRole("button", { name: /新建方案/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建随访方案" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("院内随访方案身份").fill(templateCode);
  await dialog.getByLabel("方案名称").fill(templateName);
  await dialog
    .getByLabel("方案说明")
    .fill("全角色真实前台演练创建；不包含患者姓名、电话、住址、证件号等核心敏感信息。");
  await chooseDialogOption(page, dialog, "适用机构范围", "当前医院");
  await chooseDialogOption(page, dialog, "随访病种", "慢阻肺");
  await chooseDialogOption(page, dialog, "问卷内容", "慢病随访问卷");
  await chooseDialogOption(page, dialog, "核心随访问题", "呼吸困难变化");
  await dialog.getByLabel("异常触发条件").fill("呼吸困难加重、血氧下降或患者主动报告异常");
  await dialog.getByLabel("通知对象").fill("责任医生与随访护士");
  await chooseDialogOption(page, dialog, "院内依据", "慢病随访管理制度");

  const templateResponsePromise = waitForPost(page, "/engine/followup/templates");
  await dialog.getByRole("button", { name: /创\s*建/ }).click();
  const templateResponse = await templateResponsePromise;
  const templateText = await templateResponse.text();
  expect(
    templateResponse.ok(),
    `${view.label} 创建随访方案应返回成功 status=${templateResponse.status()} body=${templateText}`,
  ).toBe(true);
  const template = JSON.parse(templateText) as {
    data?: { templateId?: string; templateCode?: string; name?: string };
  };
  expect(template.data?.templateId, `${view.label} 随访方案创建响应应返回方案身份`).toBeTruthy();
  expect(template.data?.templateCode).toBe(templateCode);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return {
    templateId: template.data?.templateId ?? "",
    templateCode,
    name: template.data?.name ?? templateName,
    displayName: templateDisplayName,
    defaultName: templateDefaultName,
  };
}

async function publishFollowupTemplateForStakeholderAction(
  page: Page,
  view: StakeholderView,
  template: {
    templateId: string;
    templateCode: string;
    name: string;
    displayName: string;
    defaultName: string;
  },
) {
  await ensureReadySession(page, "engine-operator");
  await page.goto(appPath("/clinical/followup"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "随访协同" }).first(),
    `${view.label} 应能进入随访方案治理页发布方案`,
  ).toBeVisible({ timeout: 30_000 });
  await page.getByRole("tab", { name: "随访方案" }).click();
  await page.getByPlaceholder("按方案名称或适用范围检索").fill(template.defaultName);

  const row = page
    .getByRole("row", { name: new RegExp(escapeRegExp(template.defaultName)) })
    .filter({ hasText: "待发布" })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  const publishResponsePromise = waitForPost(
    page,
    `/engine/followup/templates/${template.templateId}/publish`,
  );
  await row.getByRole("button", { name: "发布方案" }).click();
  const publishResponse = await publishResponsePromise;
  const publishText = await publishResponse.text();
  expect(
    publishResponse.ok(),
    `${view.label} 发布随访方案应返回成功 status=${publishResponse.status()} body=${publishText}`,
  ).toBe(true);
  const published = JSON.parse(publishText) as {
    data?: { assetStatus?: string; templateId?: string };
  };
  expect(published.data?.templateId).toBe(template.templateId);
  expect(published.data?.assetStatus).toBe("PUBLISHED");
  const publishedRow = page
    .getByRole("row", { name: new RegExp(escapeRegExp(template.defaultName)) })
    .filter({ hasText: "可用于计划生成" })
    .first();
  await expect(publishedRow).toBeVisible({ timeout: 30_000 });
}

async function performPharmacistReviewAction(page: Page, view: StakeholderView) {
  const cardId = await createRecommendationCardForStakeholderAction(page, view);
  await expect(
    page.locator("main").getByRole("heading", { name: "提醒与推荐" }).first(),
    `${view.label} 应能在提醒与推荐页登记药师复核`,
  ).toBeVisible({ timeout: 30_000 });
  expect(cardId, `${view.label} 药师复核应由当前前台用药上下文生成推荐卡`).toBeTruthy();
  await page.getByLabel("患者或证据线索").fill(cardId ?? "");
  await expect(page.getByRole("button", { name: "查看与人机反馈" }).first()).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "查看与人机反馈" }).first().click();
  const drawer = page.getByRole("dialog", { name: "推荐详情与反馈闭环" });
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await drawer.getByRole("tab", { name: "药师复核" }).click();
  await drawer
    .getByLabel("药师复核说明")
    .fill("药师已复核联合用药风险，建议医生结合出血风险确认；未填写患者明文身份。");
  const reviewResponsePromise = waitForPost(page, "/engine/recommendations/cards/");
  await drawer.getByRole("button", { name: "登记药师复核" }).click();
  const reviewResponse = await reviewResponsePromise;
  const reviewText = await reviewResponse.text();
  expect(
    reviewResponse.ok(),
    `${view.label} 登记药师复核应返回成功 status=${reviewResponse.status()} body=${reviewText}`,
  ).toBe(true);
  await expect(drawer.getByText(/药师\s*·\s*完成复核/u)).toBeVisible({ timeout: 30_000 });
  return ["前台建立药师复核上下文、触发推荐评估并登记联合用药风险复核"];
}

async function createRecommendationCardForStakeholderAction(
  page: Page,
  view: StakeholderView,
  options: RecommendationContextOptions = {
    namePrefix: "药",
    diagnosis: "真实前台药师联合用药复核主题",
    currentMedicationText: "华法林、阿司匹林",
    reason: "全角色真实演练：为药师联合用药复核建立当前就诊上下文，不写入患者明文身份。",
  },
) {
  const snapshot = await createContextSnapshotForStakeholderAction(page, view, options);
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "提醒与推荐" }).first(),
    `${view.label} 应能进入提醒与推荐页触发药师复核所需推荐卡`,
  ).toBeVisible({ timeout: 30_000 });

  await page.getByRole("button", { name: "登记触发评估" }).click();
  const dialog = page.getByRole("dialog", { name: "登记一次推荐触发评估" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  await expect(dialog.getByRole("button", { name: "选择第 1 个临床快照" })).toBeVisible({
    timeout: 30_000,
  });
  await dialog.getByRole("button", { name: "选择第 1 个临床快照" }).click();
  await chooseDialogOption(page, dialog, "触发时点", "开立用药");

  const evaluateResponsePromise = waitForPost(page, "/engine/recommendations:evaluate");
  await dialog.getByRole("button", { name: "执行推荐评估" }).click();
  const evaluateResponse = await evaluateResponsePromise;
  const evaluateText = await evaluateResponse.text();
  expect(
    evaluateResponse.ok(),
    `${view.label} 触发推荐评估应返回成功 status=${evaluateResponse.status()} body=${evaluateText}`,
  ).toBe(true);
  const evaluation = JSON.parse(evaluateText) as { data?: RecommendationEvaluationPayload };
  const cardId = assertRuntimeRecommendationEvidence(
    evaluation.data,
    `${view.label} 触发推荐评估`,
    evaluateText,
  );
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return cardId;
}

function assertRuntimeRecommendationEvidence(
  evaluation: RecommendationEvaluationPayload | undefined,
  label: string,
  responseText: string,
) {
  expect(evaluation?.status, `${label} 应由当前前台上下文完成规则评估：${responseText}`).toBe(
    "EVALUATED",
  );
  expect(evaluation?.triggerId, `${label} 响应应返回触发编号：${responseText}`).toBeTruthy();
  expect(evaluation?.traceId, `${label} 响应应返回追踪号：${responseText}`).toBeTruthy();
  expect(
    evaluation?.visibleCardCount ?? 0,
    `${label} 应新增至少 1 张可见推荐卡：${responseText}`,
  ).toBeGreaterThan(0);
  expect(evaluation?.suppressedCardCount ?? 0, `${label} 不应被疲劳策略抑制：${responseText}`).toBe(
    0,
  );
  const card = evaluation?.cards?.[0];
  expect(card?.cardId, `${label} 应返回推荐卡编号：${responseText}`).toBeTruthy();
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含运行版本`).toContain("运行版本=");
  expect(card?.sourceSummary ?? "", `${label} 来源摘要应包含资产版本`).toContain("asset_version=");
  const explanation = JSON.parse(card?.explanationJson ?? "{}") as {
    runtimeRelease?: { runtimeReleaseId?: string; assetVersionId?: string; sourceLayer?: string };
  };
  expect(
    explanation.runtimeRelease?.runtimeReleaseId,
    `${label} 解释应记录机构生效版本`,
  ).toBeTruthy();
  expect(explanation.runtimeRelease?.assetVersionId, `${label} 解释应记录资产版本`).toBeTruthy();
  expect(explanation.runtimeRelease?.sourceLayer, `${label} 解释应记录来源层`).toBeTruthy();
  return card?.cardId ?? "";
}

async function createContextSnapshotForReportInterpretation(page: Page, view: StakeholderView) {
  return createContextSnapshotForStakeholderAction(page, view, {
    namePrefix: "技",
    diagnosis: "真实前台医技报告解读主题",
    diagnosticReportType: "血钾检验",
    diagnosticReportConclusion: "血钾 6.3 mmol/L，危急值，已复核",
    diagnosticReportKeyFindingsText: "血钾升高、危急值",
    reason: "全角色真实演练：为医技报告解读建立当前就诊上下文，不写入患者明文身份。",
  });
}

async function createContextSnapshotForStakeholderAction(
  page: Page,
  view: StakeholderView,
  options: {
    namePrefix: string;
    diagnosis: string;
    currentMedicationText?: string;
    diagnosticReportType?: string;
    diagnosticReportConclusion?: string;
    diagnosticReportKeyFindingsText?: string;
    reason: string;
  },
) {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "患者索引" }).first(),
    `${view.label} 应能进入患者索引建立脱敏上下文`,
  ).toBeVisible({ timeout: 30_000 });

  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible({ timeout: 10_000 });
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `${options.namePrefix}*${idLast4.slice(-1)}`;
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
    `${view.label} 创建脱敏患者主索引应返回成功 status=${patientResponse.status()} body=${patientText}`,
  ).toBe(true);
  const patient = JSON.parse(patientText) as { data?: { mpiId?: string } };
  const patientId = patient.data?.mpiId;
  expect(patientId, `${view.label} 创建脱敏患者后应返回患者身份`).toBeTruthy();
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
  await contextDialog.getByLabel("诊断/随访病种").fill(options.diagnosis);
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  if (options.currentMedicationText) {
    await contextDialog.getByLabel("当前用药").fill(options.currentMedicationText);
  }
  if (options.diagnosticReportType) {
    await contextDialog.getByLabel("医技报告项目").fill(options.diagnosticReportType);
  }
  if (options.diagnosticReportConclusion) {
    await contextDialog.getByLabel("报告结论").fill(options.diagnosticReportConclusion);
  }
  if (options.diagnosticReportKeyFindingsText) {
    await contextDialog.getByLabel("异常重点").fill(options.diagnosticReportKeyFindingsText);
  }
  await contextDialog.getByLabel("建立原因").fill(options.reason);

  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  const contextText = await contextResponse.text();
  expect(
    contextResponse.ok(),
    `${view.label} 建立当前就诊上下文应返回成功 status=${contextResponse.status()} body=${contextText}`,
  ).toBe(true);
  const context = JSON.parse(contextText) as {
    data?: { snapshotId?: string; resources?: { encounters?: Array<{ encounterId?: string }> } };
  };
  expect(context.data?.snapshotId, `${view.label} 上下文创建响应应返回快照身份`).toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByText("当前就诊上下文已建立")).toBeVisible({ timeout: 20_000 });
  return {
    patientId: patientId ?? "",
    snapshotId: context.data?.snapshotId ?? "",
    encounterId: context.data?.resources?.encounters?.[0]?.encounterId ?? null,
  };
}

function collectRuntime(page: Page) {
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
      errors.push(`${response.status()} ${response.request().method()} ${response.url()}`);
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

function clearRuntime(runtime: ReturnType<typeof collectRuntime>) {
  runtime.browserErrors.length = 0;
  runtime.serverErrors.length = 0;
  runtime.networkFailures.length = 0;
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

async function expectDrawerSettledInViewport(page: Page, drawer: Locator, label: string) {
  await expect
    .poll(
      async () => {
        const [box, viewport] = await Promise.all([drawer.boundingBox(), page.viewportSize()]);
        if (!box || !viewport) {
          return false;
        }
        const left = Math.round(box.x);
        const right = Math.round(box.x + box.width);
        return left >= 0 && right <= viewport.width;
      },
      {
        message: `${label} 应在截图前完全进入当前视口`,
        timeout: 5_000,
      },
    )
    .toBe(true);
}

async function attachRuntimeRecords(testInfo: TestInfo, records: RuntimeRecord[]) {
  const recordPath = testInfo.outputPath("stakeholder-view-runtime-records.json");
  await writeFile(recordPath, `${JSON.stringify(records, null, 2)}\n`, "utf8");
  await testInfo.attach("stakeholder-view-runtime-records", {
    path: recordPath,
    contentType: "application/json",
  });
}

function waitForGet(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "GET" && response.url().includes(path),
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

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const selectedText = await currentSelectText(select);
  if (selectedText === optionText) {
    return;
  }
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

async function expectDialogInputValueAbsent(dialog: Locator, value: string, message: string) {
  const matchingInputCount = await dialog
    .locator("input, textarea")
    .evaluateAll((elements, expected) => {
      return elements.filter((element) => {
        if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) {
          return element.value === expected;
        }
        return false;
      }).length;
    }, value);
  expect(matchingInputCount, message).toBe(0);
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
  const optionText = (await firstOption.textContent())?.trim() ?? "";
  expect(optionText, `${label} 应至少返回一个可选择项`).not.toBe("");
  await firstOption.click();
  return optionText;
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
