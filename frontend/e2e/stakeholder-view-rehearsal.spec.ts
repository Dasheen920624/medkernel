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
};

type RuntimeRecord = {
  code: string;
  label: string;
  role: RoleAccount;
  path: string;
  url: string;
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
  await expect(page.locator(".ant-spin-spinning"), `${view.label} 页面不应停留在加载中`).toHaveCount(
    0,
    { timeout: 30_000 },
  );

  for (const marker of view.markers) {
    await expect(
      page.getByText(marker).first(),
      `${view.label} 视角应展示业务能力 ${String(marker)}`,
    ).toBeVisible({ timeout: 30_000 });
  }

  await expectNoRootOverflow(page, view.label);
  const record = {
    code: view.code,
    label: view.label,
    role: view.role,
    path: view.path,
    url: page.url(),
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
    if (response.status() >= 400 && response.url().includes("/medkernel/")) {
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
