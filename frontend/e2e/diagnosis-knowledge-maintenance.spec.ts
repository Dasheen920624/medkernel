import { expect, test, type Page } from "@playwright/test";

import { appPath, ensureReadySession, expectOk } from "./support/auth";

type StandardTerm = {
  standardSystem: string;
  termCode: string;
  displayName?: string | null;
};

test.describe("诊断知识维护真实前台链路", () => {
  test("运营员从前台创建证据完整诊断资产并登记标准与验证病例", async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    const browserErrors = collectBrowserErrors(page);
    const serverErrors = collectServerErrors(page);
    const networkFailures = collectNetworkFailures(page);

    await ensureReadySession(page, "engine-operator");
    const suffix = Date.now().toString(36);
    const findingTerm = await registerFindingTermFromFrontend(page, suffix);
    const subject = `前台诊断维护演练${suffix}`;
    const sourceContent = `${subject} 来源原文：当 ${findingTerm.displayName ?? "标准发现项"} 与临床事实一致时，需登记为结构化诊断标准，并保留验证病例复算证据。`;
    const evidenceExcerpt = "需登记为结构化诊断标准，并保留验证病例复算证据";

    await page.goto(appPath("/knowledge/diagnosis"), { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle");
    await setEvidenceDetails(page, false);
    await expect(page.getByRole("heading", { name: "诊断知识维护" })).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByText("在统一知识治理下维护诊断身份")).toBeVisible();

    await page.getByRole("button", { name: "新建诊断资产" }).click();
    const drawer = page.locator(".ant-drawer-content").filter({
      hasText: "新建证据完整的诊断资产",
    });
    await expect(drawer).toBeVisible({ timeout: 10_000 });
    await drawer.getByLabel("诊断名称").fill(subject);
    await drawer.getByLabel("稳定诊断身份").fill(`frontdesk-dx-${suffix}`);
    await drawer.getByLabel("资产说明").fill("真实前台演练创建的诊断知识资产");
    await drawer.getByLabel("来源标题").fill(`${subject} 来源指南`);
    await drawer.getByLabel("稳定来源身份").fill(`DXSRC.${suffix.toUpperCase()}`);
    await drawer.getByLabel("分级依据").fill("上线演练使用的受控指南来源");
    await drawer.getByLabel("来源版本").fill("2026");
    await drawer.getByLabel("受控文件地址").fill(`repository://diagnosis/${suffix}`);
    await drawer.getByLabel("来源原文").fill(sourceContent);
    await drawer.getByLabel("知识版本").fill(`frontdesk-${suffix}`);
    await drawer.getByLabel("版本名称").fill(`${subject} 候选版`);
    await drawer.getByLabel("证据锚点路径").fill("diagnosis.criteria.frontdesk");
    await drawer.getByLabel("证据锚点名称").fill("诊断标准与验证病例");
    await drawer.getByLabel("诊断依据原文片段").fill(evidenceExcerpt);

    const createAssetResponsePromise = waitForPost(page, "/engine/knowledge/diagnosis/assets");
    await page.getByRole("button", { name: "创建草稿" }).click();
    const createAssetResponse = await createAssetResponsePromise;
    await expectOk(createAssetResponse, "前台创建诊断知识资产");
    const created = (await createAssetResponse.json()) as {
      data?: { identity?: { id?: number; identityCode?: string }; version?: { id?: number } };
    };
    expect(created.data?.identity?.id, "创建诊断资产应返回知识身份").toBeTruthy();
    expect(created.data?.version?.id, "创建诊断资产应返回候选版本").toBeTruthy();
    await expect(drawer).toBeHidden({ timeout: 20_000 });
    await expect(page.getByText(subject).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.locator("main")).not.toContainText(created.data?.identity?.identityCode ?? "");

    await page.getByRole("button", { name: /新增标准/ }).click();
    const criterionDialog = page.getByRole("dialog", { name: "新增诊断标准" });
    await expect(criterionDialog).toBeVisible({ timeout: 10_000 });
    await selectCriterionFindingTerm(page, criterionDialog, findingTerm);
    const criterionResponsePromise = waitForPost(page, "/criteria");
    await clickDialogOk(criterionDialog);
    const criterionResponse = await criterionResponsePromise;
    await expectOk(criterionResponse, "前台新增诊断标准");
    await expect(page.getByText("发现项已登记").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("主要").first()).toBeVisible();
    await expect(page.locator("main")).not.toContainText("MAJOR");
    await expect(page.locator("main")).not.toContainText(findingTerm.termCode);

    await page.getByRole("tab", { name: /验证病例/ }).click();
    await page.getByRole("button", { name: /新增病例/ }).click();
    const caseDialog = page.getByRole("dialog", { name: "新增验证病例" });
    await expect(caseDialog).toBeVisible({ timeout: 10_000 });
    await caseDialog.getByLabel("稳定验证病例身份").fill(`DXCASE-${suffix.toUpperCase()}`);
    await caseDialog.getByLabel("发现项身份").fill(findingTerm.termCode);
    const caseResponsePromise = waitForPost(page, "/test-cases");
    await clickDialogOk(caseDialog);
    const caseResponse = await caseResponsePromise;
    await expectOk(caseResponse, "前台新增诊断验证病例");
    await expect(page.getByText("验证病例已登记").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("发现项证据已记录").first()).toBeVisible();
    await expect(page.getByText("强支持").first()).toBeVisible();
    await expect(page.locator("main")).not.toContainText(`DXCASE-${suffix.toUpperCase()}`);
    await expect(page.locator("main")).not.toContainText(findingTerm.termCode);

    expect(browserErrors, "诊断知识维护前台演练不应产生浏览器错误").toEqual([]);
    expect(serverErrors, "诊断知识维护前台演练不应产生 HTTP 错误").toEqual([]);
    expect(networkFailures, "诊断知识维护前台演练不应产生网络失败").toEqual([]);

    await page.screenshot({
      path: testInfo.outputPath("diagnosis-knowledge-maintenance.png"),
      fullPage: true,
    });
  });
});

async function registerFindingTermFromFrontend(page: Page, suffix: string): Promise<StandardTerm> {
  const termCode = `TERM.LAB.FRONTDESK.${suffix.toUpperCase()}`;
  const displayName = `前台演练发现项${suffix}`;

  await page.goto(appPath("/terminology/mapping"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await setEvidenceDetails(page, false);
  await expect(page.getByRole("heading", { name: "术语与字典" })).toBeVisible({
    timeout: 30_000,
  });

  await page.getByRole("button", { name: "登记标准术语" }).click();
  const dialog = page.getByRole("dialog", { name: "登记标准术语" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("标准体系").fill("TERM.LAB");
  await dialog.getByLabel("标准编码").fill(termCode);
  await dialog.getByLabel("标准名称").fill(displayName);
  await dialog.getByLabel("依据说明").fill("真实前台演练登记的诊断标准发现项");

  const responsePromise = waitForPost(page, "/engine/terminology/terms/standard");
  await dialog.getByRole("button", { name: "提交登记" }).click();
  const response = await responsePromise;
  await expectOk(response, "前台登记标准术语");
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  return { standardSystem: "TERM.LAB", termCode, displayName };
}

async function selectCriterionFindingTerm(
  page: Page,
  dialog: ReturnType<Page["getByRole"]>,
  findingTerm: StandardTerm,
) {
  const selector = dialog.getByRole("combobox", { name: "标准发现项身份" });
  await selector.click();
  await selector.fill(findingTerm.displayName ?? findingTerm.termCode);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)");
  await dropdown.getByText(findingTerm.displayName ?? findingTerm.termCode, { exact: true }).click();
}

async function setEvidenceDetails(page: Page, enabled: boolean) {
  await page.evaluate((next) => {
    window.localStorage.setItem("medkernel.evidence-details.enabled", String(next));
  }, enabled);
}

async function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
  );
}

async function clickDialogOk(dialog: ReturnType<Page["getByRole"]>) {
  await dialog.getByRole("button", { name: /OK|确\s*定/u }).click();
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
    if (failure?.errorText !== "net::ERR_ABORTED") {
      errors.push(`${request.method()} ${request.url()} ${failure?.errorText ?? "requestfailed"}`);
    }
  });
  return errors;
}
