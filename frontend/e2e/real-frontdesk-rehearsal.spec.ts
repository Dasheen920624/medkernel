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

test.describe.configure({ mode: "serial" });

test.describe("全前台真实操作演练", () => {
  test("平台接入、知识资产、外调策略、患者资源与临床随访数据均由前台页面提交产生", async ({
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];
    const suffix = Date.now().toString(36);

    try {
      await page.setViewportSize({ width: 1440, height: 960 });
      await createAdapterFromUi(page, testInfo, runtime, records, suffix);
      await createValueSetFromUi(page, testInfo, runtime, records, suffix);
      await configureModelEgressPolicyFromUi(page, testInfo, runtime, records);
      await createMpiPatientFromUi(page, testInfo, runtime, records);
      await createFollowupTemplateFromUi(page, testInfo, runtime, records, suffix);
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
) {
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
  await dialog.getByLabel("适配器标识").fill(adapterId);
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
  await page.getByRole("tab", { name: "配置资产维护" }).click();
  await expectNoRootOverflow(page, "统一资产库配置资产维护桌面");

  const assetIdentity = `VS.REAL.FRONTDESK.${suffix.toUpperCase()}`;
  await expect(page.getByRole("button", { name: "新建值集" })).toBeEnabled();
  await page.getByRole("button", { name: "新建值集" }).click();
  const dialog = page.getByRole("dialog", { name: "新建值集" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("资产编码").fill(assetIdentity);
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
  await expectNoRootOverflow(page, "模型能力外调安全策略桌面");

  const policyButton = page.getByRole("button", { name: /配置 .+ 外调安全策略/ }).first();
  await expect(policyButton).toBeEnabled();
  await policyButton.click();
  const dialog = page.getByRole("dialog", { name: "配置外调安全策略" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText("公网外部模型可使用患者上下文")).toBeVisible();

  const responsePromise = waitForPut(page, "/api/v1/data-minimization/policies/model-egress/");
  await dialog.getByRole("button", { name: "保存外调安全策略" }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交模型外调安全策略应返回成功").toBe(true);
  const result = (await response.json()) as {
    data?: { allowedFields?: string; sensitivityLevel?: string; guardrailLockedFlag?: string };
  };
  expect(result.data?.allowedFields).toContain("prompt");
  expect(result.data?.sensitivityLevel).toBe("HIGH");
  expect(result.data?.guardrailLockedFlag).toBe("Y");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-model-egress-policy");
  recordCleanRuntime(page, "前台配置模型外调安全策略", runtime, records);
}

async function createMpiPatientFromUi(
  page: Page,
  testInfo: TestInfo,
  runtime: RuntimeCollectors,
  records: RuntimeRecord[],
) {
  await ensureReadySession(page, "clinical-user");
  clearRuntime(runtime);
  await page.goto("/mpi", { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者主索引 MPI" })).toBeVisible();
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
  await page.getByRole("tab", { name: "随访模板" }).click();
  await expectNoRootOverflow(page, "随访协同模板桌面");

  const templateCode = `FUP.REAL.FRONTDESK.${suffix.toUpperCase()}`;
  await expect(page.getByRole("button", { name: /新建模板/ })).toBeEnabled();
  await page.getByRole("button", { name: /新建模板/ }).click();
  const dialog = page.getByRole("dialog", { name: "新建随访模板" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("院内方案编号").fill(templateCode);
  await dialog.getByLabel("模板名称").fill(`真实前台慢病随访模板 ${suffix}`);
  await dialog
    .getByLabel("模板说明")
    .fill("真实前台演练创建；不包含患者姓名、证件号、电话、住址等核心敏感信息。");
  await chooseDialogOption(page, dialog, "适用机构范围", "当前医院");
  await chooseDialogOption(page, dialog, "随访病种", "慢阻肺");
  await chooseDialogOption(page, dialog, "问卷内容模板", "真实前台慢病随访问卷");
  await chooseDialogOption(page, dialog, "核心随访问题", "呼吸困难变化");
  await dialog.getByLabel("异常触发条件").fill("呼吸困难加重、血氧下降或患者主动报告异常");
  await dialog.getByLabel("通知对象").fill("责任医生与随访护士");
  await chooseDialogOption(page, dialog, "院内依据", "真实前台演练随访制度");

  const responsePromise = waitForPost(page, "/api/v1/engine/followup/templates");
  await dialog.getByRole("button", { name: /创\s*建/ }).click();
  const response = await responsePromise;
  expect(response.ok(), "前台提交随访模板应返回成功").toBe(true);
  const result = (await response.json()) as { data?: { templateCode?: string } };
  expect(result.data?.templateCode).toBe(templateCode);
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await captureEvidence(page, testInfo, "real-frontdesk-followup-template");
  recordCleanRuntime(page, "前台创建随访模板", runtime, records);
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, option: string) {
  await dialog.getByLabel(label).click();
  await page.getByRole("option", { name: option }).click();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function waitForPut(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "PUT" && response.url().includes(path),
    { timeout: 30_000 },
  );
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
