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
    | "QUALITY_DRILLDOWN"
    | "AUDIT_EXPORT_VERIFY"
    | "ADAPTER_QUALITY_REPORT"
    | "REPORT_INTERPRETATION";
};

type RuntimeRecord = {
  code: string;
  label: string;
  role: RoleAccount;
  path: string;
  url: string;
  actions: string[];
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

const stakeholderViews: StakeholderView[] = [
  {
    code: "PHYSICIAN",
    label: "医生",
    role: "clinical-user",
    path: "/cdss/fatigue",
    heading: "提醒与推荐",
    markers: ["必须医师确认", "医生反馈", "登记触发评估"],
  },
  {
    code: "NURSE",
    label: "护士",
    role: "clinical-user",
    path: "/clinical/followup",
    heading: "随访协同",
    markers: ["护士代填", "问卷回收", "异常回院处理"],
  },
  {
    code: "PHARMACIST",
    label: "药师",
    role: "clinical-user",
    path: "/cdss/fatigue",
    heading: "提醒与推荐",
    markers: ["药师复核", /联合用药|用药风险|DDI/u],
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
    heading: "质量管理概览",
    markers: ["真实指标", "下钻问题证据", "导出证据"],
    action: "QUALITY_DRILLDOWN",
  },
  {
    code: "PATIENT_PROXY",
    label: "患者代理",
    role: "clinical-user",
    path: "/clinical/followup",
    heading: "随访协同",
    markers: ["患者问卷回收", "患者自填", "患者报告"],
  },
  {
    code: "PLATFORM_ADMIN",
    label: "平台管理员",
    role: "platform-admin",
    path: "/admin/users",
    heading: "人员与账号",
    markers: ["任职", "登录账号", "组织范围"],
  },
  {
    code: "ENGINE_OPERATOR",
    label: "医疗引擎运营员",
    role: "engine-operator",
    path: "/knowledge/production",
    heading: "知识生产",
    markers: ["模型服务配置", "医学评测", "生产前校验"],
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
    markers: ["服务契约", "追踪诊断", "插件边界"],
    action: "ADAPTER_QUALITY_REPORT",
  },
  {
    code: "IMPLEMENTATION_ENGINEER",
    label: "实施工程师",
    role: "platform-admin",
    path: "/onboarding/guide",
    heading: "实施与验收",
    markers: ["实施步骤真实状态", "阻塞项", "下一配置页"],
    action: "ADAPTER_QUALITY_REPORT",
  },
  {
    code: "HOSPITAL_EXECUTIVE",
    label: "院长",
    role: "engine-operator",
    path: "/qc/dashboard",
    heading: "质量管理概览",
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

  for (const marker of view.markers) {
    await expect(
      page.getByText(marker).first(),
      `${view.label} 视角应展示业务能力 ${String(marker)}`,
    ).toBeVisible({ timeout: 30_000 });
  }

  const actions = await performStakeholderAction(page, view);
  if (view.action) {
    expect(actions.length, `${view.label} 视角应记录至少一个真实前台动作`).toBeGreaterThan(0);
  }
  await expectNoRootOverflow(page, view.label);
  const record = {
    code: view.code,
    label: view.label,
    role: view.role,
    path: view.path,
    url: page.url(),
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

  return [];
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
  const drawer = page.locator(".ant-drawer-content").filter({ hasText: "真实下钻证据" }).last();
  await expect(drawer).toBeVisible({ timeout: 20_000 });
  await expect(drawer.getByText("真实下钻证据", { exact: true })).toBeVisible();
  await expect(
    drawer.getByText(/证据包已生成|已按当前筛选范围记录|证据导出编号/u).first(),
  ).toBeVisible({ timeout: 20_000 });
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
  const row = page.getByRole("row").filter({ hasText: confirmationId ?? "" });
  await expect(row).toBeVisible({ timeout: 20_000 });

  const exportResponsePromise = waitForPost(page, "/large-lists/exports");
  const completeResponsePromise = waitForPost(
    page,
    `/compliance/exports/${confirmationId}:complete-from-job`,
  );
  await row
    .getByRole("button", {
      name: new RegExp(`生成导出文件\\s+${escapeRegExp(confirmationId ?? "")}`),
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
  await page.goto(appPath("/adapter/hub"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "系统接入" }).first(),
    `${view.label} 应能进入系统接入页生成上线前数据质量报告`,
  ).toBeVisible({ timeout: 30_000 });

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
  return ["生成系统接入数据质量报告"];
}

async function performReportInterpretationAction(page: Page, view: StakeholderView) {
  const snapshot = await createContextSnapshotForReportInterpretation(page, view);
  await page.goto(appPath("/cdss/fatigue"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "提醒与推荐" }).first(),
    `${view.label} 应能进入提醒与推荐页生成医技报告解读`,
  ).toBeVisible({ timeout: 30_000 });

  await page.getByRole("button", { name: "生成报告解读" }).click();
  const dialog = page.getByRole("dialog", { name: "生成医技报告解读" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("患者信息").fill(snapshot.patientId);
  if (snapshot.encounterId) {
    await dialog.getByLabel("就诊信息").fill(snapshot.encounterId);
  }
  await expect(dialog.getByRole("button", { name: "选择第 1 个临床快照" })).toBeVisible({
    timeout: 30_000,
  });
  await dialog.getByRole("button", { name: "选择第 1 个临床快照" }).click();

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
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  return ["前台建立医技报告上下文并生成报告解读"];
}

async function createContextSnapshotForReportInterpretation(page: Page, view: StakeholderView) {
  await page.goto(appPath("/mpi"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("main").getByRole("heading", { name: "患者索引" }).first(),
    `${view.label} 生成报告解读前应能进入患者索引建立脱敏上下文`,
  ).toBeVisible({ timeout: 30_000 });

  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible({ timeout: 10_000 });
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `技*${idLast4.slice(-1)}`;
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
  await contextDialog.getByLabel("诊断/随访病种").fill("真实前台医技报告解读主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog
    .getByLabel("建立原因")
    .fill("全角色真实演练：为医技报告解读建立当前就诊上下文，不写入患者明文身份。");

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

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown.getByText(optionText, { exact: true }).last();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
