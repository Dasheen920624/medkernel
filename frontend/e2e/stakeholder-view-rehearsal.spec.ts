import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { ensureReadySession, type RoleAccount } from "./support/auth";

type StakeholderView = {
  code: string;
  label: string;
  role: RoleAccount;
  path: string;
  heading: string | RegExp;
  markers: Array<string | RegExp>;
  action?: "QUALITY_DRILLDOWN" | "AUDIT_EXPORT_VERIFY";
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
  },
  {
    code: "IMPLEMENTATION_ENGINEER",
    label: "实施工程师",
    role: "platform-admin",
    path: "/onboarding/guide",
    heading: "实施与验收",
    markers: ["实施步骤真实状态", "阻塞项", "下一配置页"],
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
  await page.goto(view.path, { waitUntil: "domcontentloaded" });
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

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
