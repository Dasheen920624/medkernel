import { expect, test, type Page, type TestInfo } from "@playwright/test";

import { appPath, ensureReadySession } from "./support/auth";

type StandardTerm = {
  standardSystem: string;
  termCode: string;
  displayName?: string | null;
  status: number;
};

type DiagnosisKnowledgeAttachmentEvidence = {
  standardTerm: {
    operation: string;
    status: number;
    system: string;
    termCode: string;
    displayName: string;
  };
  diagnosisAsset: {
    operation: string;
    status: number;
    identityId: number;
    identityCode: string;
    versionId: number;
    requestedIdentityCode: string;
    evidenceExcerpt: string;
  };
  diagnosisCriterion: {
    operation: string;
    status: number;
    findingTermCode: string;
  };
  validationCase: {
    operation: string;
    status: number;
    caseIdentity: string;
    findingTermCode: string;
  };
};

const requiredDiagnosisKnowledgeScenarioEvidence = [
  {
    code: "S3",
    observedStages: [
      "前台登记标准发现项术语",
      "前台创建证据完整诊断资产草稿",
      "前台登记诊断标准",
      "前台登记验证病例",
    ],
  },
] as const;

test.describe("诊断知识库真实前台链路", () => {
  test("运营员从前台创建证据完整诊断资产并登记标准与验证病例", async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    const browserErrors = collectBrowserErrors(page);
    const serverErrors = collectServerErrors(page);
    const networkFailures = collectNetworkFailures(page);
    const observedStages = new Set<string>();

    await ensureReadySession(page, "engine-operator");
    const suffix = Date.now().toString(36);
    const findingTerm = await registerFindingTermFromFrontend(page, suffix);
    recordDiagnosisKnowledgeStage(observedStages, "前台登记标准发现项术语");
    const subject = `前台诊断维护演练${suffix}`;
    const sourceContent = `${subject} 来源原文：当 ${findingTerm.displayName ?? "标准发现项"} 与临床事实一致时，需登记为结构化诊断标准，并保留验证病例复算证据。`;
    const evidenceExcerpt = "需登记为结构化诊断标准，并保留验证病例复算证据";
    const requestedIdentityCode = `frontdesk-dx-${suffix}`;
    const validationCaseIdentity = `DXCASE-${suffix.toUpperCase()}`;

    await page.goto(appPath("/knowledge/diagnosis"), { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle");
    await setEvidenceDetails(page, false);
    await expect(page.getByRole("heading", { name: "诊断知识库" })).toBeVisible({
      timeout: 30_000,
    });
    await expect(
      page.getByText("在统一知识治理下管理诊断身份、诊断标准、鉴别诊断、验证病例与来源证据"),
    ).toBeVisible();

    await page.getByRole("button", { name: "新建诊断资产" }).click();
    const drawer = page.locator(".ant-drawer-content").filter({
      hasText: "新建证据完整的诊断资产",
    });
    await expect(drawer).toBeVisible({ timeout: 10_000 });
    await drawer.getByLabel("诊断名称").fill(subject);
    await drawer.getByLabel("稳定诊断身份").fill(requestedIdentityCode);
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
    await expectBrowserResponseOk(createAssetResponse, "前台创建诊断知识资产");
    const created = (await createAssetResponse.json()) as {
      data?: { identity?: { id?: number; identityCode?: string }; version?: { id?: number } };
    };
    expect(created.data?.identity?.id, "创建诊断资产应返回知识身份").toBeTruthy();
    expect(created.data?.version?.id, "创建诊断资产应返回候选版本").toBeTruthy();
    const identityId = created.data?.identity?.id;
    const identityCode = created.data?.identity?.identityCode;
    const versionId = created.data?.version?.id;
    expect(typeof identityId, "诊断资产知识身份应为数值").toBe("number");
    expect(typeof identityCode, "诊断资产身份编码应为字符串").toBe("string");
    expect(typeof versionId, "诊断资产候选版本应为数值").toBe("number");
    recordDiagnosisKnowledgeStage(observedStages, "前台创建证据完整诊断资产草稿");
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
    await expectBrowserResponseOk(criterionResponse, "前台新增诊断标准");
    await expect(page.getByText("发现项已登记").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("主要").first()).toBeVisible();
    await expect(page.locator("main")).not.toContainText("MAJOR");
    await expect(page.locator("main")).not.toContainText(findingTerm.termCode);
    recordDiagnosisKnowledgeStage(observedStages, "前台登记诊断标准");

    await page.getByRole("tab", { name: /验证病例/ }).click();
    await page.getByRole("button", { name: /新增病例/ }).click();
    const caseDialog = page.getByRole("dialog", { name: "新增验证病例" });
    await expect(caseDialog).toBeVisible({ timeout: 10_000 });
    await caseDialog.getByLabel("稳定验证病例身份").fill(validationCaseIdentity);
    await caseDialog.getByLabel("发现项身份").fill(findingTerm.termCode);
    const caseResponsePromise = waitForPost(page, "/test-cases");
    await clickDialogOk(caseDialog);
    const caseResponse = await caseResponsePromise;
    await expectBrowserResponseOk(caseResponse, "前台新增诊断验证病例");
    await expect(page.getByText("验证病例已登记").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("发现项证据已记录").first()).toBeVisible();
    await expect(page.getByText("强支持").first()).toBeVisible();
    await expect(page.locator("main")).not.toContainText(validationCaseIdentity);
    await expect(page.locator("main")).not.toContainText(findingTerm.termCode);
    recordDiagnosisKnowledgeStage(observedStages, "前台登记验证病例");

    expect(browserErrors, "诊断知识库前台演练不应产生浏览器错误").toEqual([]);
    expect(serverErrors, "诊断知识库前台演练不应产生 HTTP 错误").toEqual([]);
    expect(networkFailures, "诊断知识库前台演练不应产生网络失败").toEqual([]);

    await page.screenshot({
      path: testInfo.outputPath("diagnosis-knowledge-maintenance.png"),
      fullPage: true,
    });
    await attachDiagnosisKnowledgeScenarioEvidence(testInfo, observedStages, {
      standardTerm: {
        operation: "POST /engine/terminology/terms/standard",
        status: findingTerm.status,
        system: findingTerm.standardSystem,
        termCode: findingTerm.termCode,
        displayName: findingTerm.displayName ?? "",
      },
      diagnosisAsset: {
        operation: "POST /engine/knowledge/diagnosis/assets",
        status: createAssetResponse.status(),
        identityId: identityId as number,
        identityCode: identityCode as string,
        versionId: versionId as number,
        requestedIdentityCode,
        evidenceExcerpt,
      },
      diagnosisCriterion: {
        operation: "POST /criteria",
        status: criterionResponse.status(),
        findingTermCode: findingTerm.termCode,
      },
      validationCase: {
        operation: "POST /test-cases",
        status: caseResponse.status(),
        caseIdentity: validationCaseIdentity,
        findingTermCode: findingTerm.termCode,
      },
    });
  });
});

async function registerFindingTermFromFrontend(page: Page, suffix: string): Promise<StandardTerm> {
  const termCode = `TERM.LAB.FRONTDESK.${suffix.toUpperCase()}`;
  const displayName = `前台演练发现项${suffix}`;

  await page.goto(appPath("/terminology/mapping"), { waitUntil: "domcontentloaded" });
  await page.waitForLoadState("networkidle");
  await setEvidenceDetails(page, false);
  await expect(page.getByRole("heading", { name: "术语字典" })).toBeVisible({
    timeout: 30_000,
  });

  await page.getByRole("button", { name: "登记标准术语" }).click();
  const dialog = page.getByRole("dialog", { name: "登记标准术语" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("标准体系").fill("TERM.LAB");
  await dialog.getByLabel("标准编码").fill(termCode);
  await selectAntOption(page, dialog, "术语类别", "检验项目");
  await dialog.getByLabel("标准名称").fill(displayName);
  await dialog.getByLabel("版本号").fill("2026.07");
  await dialog.getByLabel("依据说明").fill("真实前台演练登记的诊断标准发现项");

  const responsePromise = waitForPost(page, "/engine/terminology/terms/standard");
  await dialog.getByRole("button", { name: "提交登记" }).click();
  const response = await responsePromise;
  await expectBrowserResponseOk(response, "前台登记标准术语");
  await expect(dialog).toBeHidden({ timeout: 20_000 });

  return { standardSystem: "TERM.LAB", termCode, displayName, status: response.status() };
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

async function expectBrowserResponseOk(response: Awaited<ReturnType<typeof waitForPost>>, label: string) {
  const body = await response.text();
  expect(response.ok(), `${label} 应返回成功 status=${response.status()} body=${body}`).toBe(true);
}

async function clickDialogOk(dialog: ReturnType<Page["getByRole"]>) {
  await dialog.getByRole("button", { name: /OK|确\s*定/u }).click();
}

async function selectAntOption(
  page: Page,
  dialog: ReturnType<Page["getByRole"]>,
  fieldLabel: string,
  optionLabel: string,
) {
  const combobox = dialog.getByRole("combobox", { name: fieldLabel });
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)");
  await dropdown.getByText(optionLabel, { exact: true }).click();
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

function recordDiagnosisKnowledgeStage(observedStages: Set<string>, stage: string) {
  observedStages.add(stage);
}

async function attachDiagnosisKnowledgeScenarioEvidence(
  testInfo: TestInfo,
  observedStageSet: Set<string>,
  evidence: DiagnosisKnowledgeAttachmentEvidence,
) {
  const scenarioEvidence = requiredDiagnosisKnowledgeScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages = requiredDiagnosisKnowledgeScenarioEvidence.find(
        (item) => item.code === scenario.code,
      )?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  await testInfo.attach("diagnosis-knowledge-scenario-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: completedScenarioCodes,
        productLayers: ["MEDICAL_ASSET"],
        semanticFamilies: ["DISEASE_DIAGNOSIS"],
        specialtyDomains: ["CLINICAL_SPECIALTIES"],
        apiEvidence: {
          standardTermRegisteredFromFrontdesk: {
            operation: evidence.standardTerm.operation,
            status: evidence.standardTerm.status,
          },
          diagnosisAssetDraftCreatedFromFrontdesk: {
            operation: evidence.diagnosisAsset.operation,
            status: evidence.diagnosisAsset.status,
          },
          diagnosisCriterionRegisteredFromFrontdesk: {
            operation: evidence.diagnosisCriterion.operation,
            status: evidence.diagnosisCriterion.status,
          },
          validationCaseRegisteredFromFrontdesk: {
            operation: evidence.validationCase.operation,
            status: evidence.validationCase.status,
          },
        },
        standardTerm: evidence.standardTerm,
        diagnosisAsset: evidence.diagnosisAsset,
        diagnosisCriterion: evidence.diagnosisCriterion,
        validationCase: evidence.validationCase,
        scenarioConditionEvidence: [
          {
            code: "S3__NORMAL",
            scenarioCode: "S3",
            condition: "NORMAL",
            source: "DIAGNOSIS_KNOWLEDGE_ASSET_STANDARD_CASE_MAINTENANCE",
            evidence: [
              "前台登记标准发现项术语后创建证据完整诊断资产草稿",
              "诊断标准和验证病例均绑定同一标准发现项术语",
            ],
          },
        ],
        scenarioEvidence,
      },
      null,
      2,
    ),
    contentType: "application/json",
  });
}
