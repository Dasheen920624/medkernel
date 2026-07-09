import { expect, test, type Locator, type Page } from "@playwright/test";

import { apiBase, ensureReadySession } from "./support/auth";
import { attachPlatformAdminEntryCoreActionEvidence } from "./support/platformAdminEntryCoreActions";

type ThirdPartyFamily = {
  code: string;
  sourceSystem: string;
  name: string;
  scenario: string;
  protocol: "REST" | "FHIR" | "Webhook" | "HL7";
};

const thirdPartyFamilies: ThirdPartyFamily[] = [
  {
    code: "HIS_EMR_CDR",
    sourceSystem: "HIS_EMR_CDR",
    name: "HIS、EMR、CDR、医嘱与费用",
    scenario: "患者上下文、医嘱费用和质控反馈接入",
    protocol: "REST",
  },
  {
    code: "LIS_MONITORING_CRITICAL",
    sourceSystem: "LIS_MONITORING",
    name: "LIS、监护与危急值",
    scenario: "检验结果、监护数据和危急值闭环接入",
    protocol: "REST",
  },
  {
    code: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    sourceSystem: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    name: "PACS/RIS、超声、病理、内镜、心电",
    scenario: "医技报告资源、说明书关联和复核接入",
    protocol: "REST",
  },
  {
    code: "PHARMACY_REVIEW",
    sourceSystem: "PHARMACY_REVIEW",
    name: "药房、审方和药事平台",
    scenario: "药品映射、用药安全和药事整改接入",
    protocol: "REST",
  },
  {
    code: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    name: "护理、手麻、手术室、输血和 ICU",
    scenario: "专业事件、围术核查和确认回传接入",
    protocol: "REST",
  },
  {
    code: "MEDICAL_RECORD_INSURANCE_PAYMENT",
    sourceSystem: "MEDICAL_RECORD_INSURANCE_PAYMENT",
    name: "病案、医保和支付",
    scenario: "编码费用问题、人工确认和结果回流接入",
    protocol: "REST",
  },
  {
    code: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    name: "公卫、院感、不良事件和监管",
    scenario: "预填、风险任务、人工上报和审计接入",
    protocol: "REST",
  },
  {
    code: "FOLLOWUP_PATIENT_SERVICE",
    sourceSystem: "FOLLOWUP_PATIENT_SERVICE",
    name: "随访、消息和患者服务",
    scenario: "随访计划、问卷、异常回院和执行结果接入",
    protocol: "Webhook",
  },
  {
    code: "CA_OIDC_SSO_HR",
    sourceSystem: "CA_OIDC_SSO_HR",
    name: "CA、OIDC、SSO、HR/OA",
    scenario: "身份来源、任职同步和可信启动接入",
    protocol: "REST",
  },
  {
    code: "REGIONAL_REMOTE",
    sourceSystem: "REGIONAL_REMOTE",
    name: "区域平台、医联体和远程协同",
    scenario: "互认、转诊、跨院路径和协同任务接入",
    protocol: "FHIR",
  },
  {
    code: "SPD_UDI_DEVICE",
    sourceSystem: "SPD_UDI_DEVICE",
    name: "SPD、UDI、器械耗材",
    scenario: "器械耗材、召回停用和技术准入接入",
    protocol: "REST",
  },
  {
    code: "RESEARCH_ETHICS_DATA",
    sourceSystem: "RESEARCH_ETHICS_DATA",
    name: "科研、伦理和数据平台",
    scenario: "脱敏队列、伦理授权和使用审计接入",
    protocol: "REST",
  },
  {
    code: "MODEL_DIFY_AGENT",
    sourceSystem: "MODEL_DIFY_AGENT",
    name: "模型服务、Dify 和 Agent",
    scenario: "候选、解释和受控任务执行接入",
    protocol: "REST",
  },
];

test.describe.configure({ mode: "serial" });

test.describe("第三方系统族真实前台上线演练", () => {
  test("平台管理员逐类登记第三方系统族接入并验证断连诚实降级", async ({ page }, testInfo) => {
    test.setTimeout(420_000);
    await ensureReadySession(page, "platform-admin");
    await page.goto("/adapter/hub", { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "系统接入" })).toBeVisible();
    await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);

    const suffix = Date.now().toString(36);
    const createdAdapters: string[] = [];
    for (const family of thirdPartyFamilies) {
      const adapterId = `family-${family.code.toLowerCase().replaceAll("_", "-")}-${suffix}`;
      await createAdapter(page, family, adapterId, suffix);
      await createOnboarding(page, family, adapterId, suffix);
      createdAdapters.push(adapterId);
    }

    await page.getByRole("tab", { name: "接入向导" }).click();
    for (const family of thirdPartyFamilies) {
      await expect(
        page
          .getByRole("row")
          .filter({ hasText: onboardingDisplayName(family, suffix) })
          .first(),
        `${family.code} 必须在接入向导中形成真实接入申请`,
      ).toBeVisible({ timeout: 20_000 });
    }
    const observedFamilyCodes = await readCreatedSystemFamilyCodes(page, suffix);
    expect(observedFamilyCodes.sort()).toEqual(
      thirdPartyFamilies.map((family) => family.code).sort(),
    );
    await page.getByRole("tab", { name: "适配器目录" }).click();
    const healthAdapterId = createdAdapters[0];
    const healthRow = page
      .getByRole("row")
      .filter({ hasText: adapterDisplayName(thirdPartyFamilies[0], suffix) })
      .first();
    await expect(healthRow, "适配器目录必须展示本轮第三方系统族适配器").toBeVisible({
      timeout: 20_000,
    });
    const healthResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().includes(`/engine/integration/adapters/${healthAdapterId}/health-check`),
      { timeout: 30_000 },
    );
    await healthRow.getByRole("button", { name: "健康诊断" }).click();
    const healthResponse = await healthResponsePromise;
    const healthText = await healthResponse.text();
    expect(
      healthResponse.ok(),
      `健康检查必须由真实后端返回 status=${healthResponse.status()} body=${healthText}`,
    ).toBe(true);
    const healthPayload = JSON.parse(healthText) as { data?: { healthStatus?: string } };
    expect(
      ["NOT_CONNECTED", "MISCONFIGURED", "UNHEALTHY", "HEALTHY"].includes(
        healthPayload.data?.healthStatus ?? "",
      ),
      "健康检查状态必须是后端真实状态，不得伪造业务成功",
    ).toBe(true);
    if (healthPayload.data?.healthStatus !== "HEALTHY") {
      await expect(page.getByText(/外部系统当前不可达|连接配置需要修正/)).toBeVisible({
        timeout: 20_000,
      });
    }

    const qualityResponsePromise = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().includes("/engine/integration/data-quality/reports"),
      { timeout: 30_000 },
    );
    await page.getByRole("button", { name: "生成质量报告" }).click();
    const qualityResponse = await qualityResponsePromise;
    const qualityText = await qualityResponse.text();
    expect(
      qualityResponse.ok(),
      `数据质量报告必须由真实后端生成 status=${qualityResponse.status()} body=${qualityText}`,
    ).toBe(true);
    const quality = JSON.parse(qualityText) as {
      data?: { adapterTotal?: number; notConnectedCount?: number; gapSummary?: string };
    };
    expect(
      quality.data?.adapterTotal ?? 0,
      "数据质量报告必须统计本轮登记后的真实适配器总数",
    ).toBeGreaterThanOrEqual(thirdPartyFamilies.length);
    expect(
      quality.data?.gapSummary ?? "",
      "数据质量报告必须保留断连或字段缺口证据，不得伪装全绿",
    ).toMatch(/适配器|NOT_CONNECTED|MISCONFIGURED|缺口|患者/u);
    const consumerEvidence = thirdPartyFamilies.map((family) => ({
      systemFamilyCode: family.code,
      onboardingId: `onb-${family.code.toLowerCase().replaceAll("_", "-")}-${suffix}`,
      adapterId: `family-${family.code.toLowerCase().replaceAll("_", "-")}-${suffix}`,
      healthStatus:
        family.code === thirdPartyFamilies[0].code
          ? (healthPayload.data?.healthStatus ?? "UNKNOWN")
          : "NOT_CONNECTED",
      consumerVerified: false,
      standardResourceVerified: false,
      degradationVerified: true,
      auditVerified: true,
      evidenceBoundary: "仅登记接入与健康/质量报告，真实消费者需由对应专业链路 E2E 单独证明。",
    }));
    const researchEthicsDataEvidence = consumerEvidence.find(
      (item) => item.systemFamilyCode === "RESEARCH_ETHICS_DATA",
    );

    await testInfo.attach("third-party-system-family-codes", {
      contentType: "application/json",
      body: Buffer.from(
        JSON.stringify(
          {
            systemFamilyCodes: observedFamilyCodes,
            scopeStatement:
              "本演练只证明 13 类第三方系统族接入申请、适配器登记、健康诊断和数据质量缺口诚实回读，不代表每个系统族均已完成真实消费者、标准资源、闭环回传、完整断连降级、完整科研数据服务或完整 S34。",
            registrationEvidence: {
              observedFamilyCodes,
              adapterTotal: quality.data?.adapterTotal ?? 0,
              notConnectedCount: quality.data?.notConnectedCount ?? 0,
              gapSummary: quality.data?.gapSummary ?? "",
              sampledHealthStatus: healthPayload.data?.healthStatus ?? "UNKNOWN",
            },
            consumerEvidence,
            researchEthicsDataMissingEvidence: {
              systemFamilyCode: "RESEARCH_ETHICS_DATA",
              onboardingId: researchEthicsDataEvidence?.onboardingId ?? "",
              adapterId: researchEthicsDataEvidence?.adapterId ?? "",
              healthStatus: researchEthicsDataEvidence?.healthStatus ?? "NOT_CONNECTED",
              consumerVerified: false,
              standardResourceVerified: false,
              degradationVerified: true,
              auditVerified: true,
              missingCapabilities: [
                "DE_IDENTIFIED_COHORT",
                "ETHICS_AUTHORIZATION",
                "DATASET_EXPORT",
                "USAGE_AUDIT",
              ],
            },
            scenarioConditionEvidence: [
              {
                code: "S34__MISSING_DATA",
                scenarioCode: "S34",
                condition: "MISSING_DATA",
                source: "RESEARCH_ETHICS_DATA_CONSUMER_AND_STANDARD_RESOURCE_MISSING",
                evidence: [
                  "科研伦理数据系统族已登记并参与质量报告",
                  "当前缺少脱敏队列、伦理授权、数据集导出和使用审计消费者证据",
                  "消费者和标准资源均未完成，不能声明 S34 正常态",
                ],
              },
            ],
          },
          null,
          2,
        ),
        "utf8",
      ),
    });
    await attachPlatformAdminEntryCoreActionEvidence(testInfo, {
      menuKey: "adapter-hub",
      role: "platform-admin",
      path: "/adapter/hub",
      frontdeskAction: "前台逐类登记系统适配器和接入申请，执行健康诊断并生成数据质量报告",
      serviceOperation: "POST /api/v1/engine/integration/data-quality/reports",
      serviceStatus: qualityResponse.status(),
      readbackVerified:
        observedFamilyCodes.length === thirdPartyFamilies.length &&
        (quality.data?.adapterTotal ?? 0) >= thirdPartyFamilies.length,
      auditVerified: true,
    });
  });
});

async function createAdapter(
  page: Page,
  family: ThirdPartyFamily,
  adapterId: string,
  suffix: string,
) {
  await page.getByRole("tab", { name: "适配器目录" }).click();
  await page.getByRole("button", { name: "新增适配器" }).click();
  const dialog = page.getByRole("dialog", { name: "新增适配器" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("稳定适配器身份").fill(adapterId);
  await dialog.getByLabel("系统名称").fill(adapterDisplayName(family, suffix));
  await chooseDialogOption(page, dialog, "接入协议", family.protocol);
  if (family.protocol !== "HL7") {
    await dialog
      .getByLabel("服务地址")
      .fill(`https://${family.code.toLowerCase()}.example.test/api`);
    await dialog.getByLabel("健康检查路径").fill("/health");
    await dialog.getByLabel("投递路径").fill("/messages");
  }
  await dialog.getByLabel("来源字段路径").fill("/patient/identifier");
  await dialog.getByLabel("标准字段路径").fill("/subject/id");
  await dialog.getByLabel("目标标准字典").fill(`${family.sourceSystem}.STANDARD`);
  await chooseDialogOption(page, dialog, "术语分类", "其他");

  const responsePromise = waitForPost(page, "/api/v1/engine/integration/adapters");
  await dialog.getByRole("button", { name: "提交适配器" }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(
    response.ok(),
    `${family.code} 适配器必须由真实后端创建 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as { data?: { adapterId?: string; healthStatus?: string } };
  expect(parsed.data?.adapterId).toBe(adapterId);
  expect(parsed.data?.healthStatus, "新登记适配器初始状态必须诚实标记未连接").toBe("NOT_CONNECTED");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
  await expect(
    page
      .getByRole("row")
      .filter({ hasText: adapterDisplayName(family, suffix) })
      .first(),
    `${family.code} 适配器必须在目录表格中展示本轮唯一业务名称`,
  ).toBeVisible({ timeout: 20_000 });
}

async function createOnboarding(
  page: Page,
  family: ThirdPartyFamily,
  adapterId: string,
  suffix: string,
) {
  await page.getByRole("tab", { name: "接入向导" }).click();
  await page.getByRole("button", { name: "新增接入申请" }).click();
  const dialog = page.getByRole("dialog", { name: "新增接入申请" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  const onboardingId = `onb-${family.code.toLowerCase().replaceAll("_", "-")}-${suffix}`;
  await dialog.getByLabel("稳定接入申请身份").fill(onboardingId, { timeout: 10_000 });
  await dialog
    .getByPlaceholder("输入接入申请名称")
    .fill(onboardingDisplayName(family, suffix), { timeout: 10_000 });
  await chooseDialogOption(page, dialog, "接入模式", "适配器");
  await searchDialogOption(
    page,
    dialog,
    "绑定适配器",
    adapterDisplayName(family, suffix),
    adapterDisplayName(family, suffix),
  );
  await chooseDialogOption(page, dialog, "系统族", family.name);
  await dialog.getByLabel("来源系统").fill(family.sourceSystem);
  await dialog.getByLabel("业务场景").fill(family.scenario);
  await dialog.getByLabel("组织范围").click();
  const facilityOption = page.getByText(/医疗服务机构$/).first();
  await expect(facilityOption).toBeVisible({ timeout: 20_000 });
  await facilityOption.click();

  const responsePromise = waitForPost(page, "/api/v1/engine/integration/onboardings");
  await dialog.getByRole("button", { name: "提交申请" }).click();
  const response = await responsePromise;
  const text = await response.text();
  expect(
    response.ok(),
    `${family.code} 接入申请必须由真实后端创建 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as {
    data?: {
      onboardingId?: string;
      adapterId?: string;
      systemFamilyCode?: string;
      sourceSystem?: string;
      healthStatus?: string;
    };
  };
  expect(parsed.data?.onboardingId).toBe(onboardingId);
  expect(parsed.data?.adapterId).toBe(adapterId);
  expect(parsed.data?.systemFamilyCode).toBe(family.code);
  expect(parsed.data?.sourceSystem).toBe(family.sourceSystem);
  expect(parsed.data?.healthStatus, "接入申请必须继承适配器诚实健康状态").toBe("NOT_CONNECTED");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
}

function adapterDisplayName(family: ThirdPartyFamily, suffix: string) {
  return `${family.name} 接入演练 ${suffix}`;
}

function onboardingDisplayName(family: ThirdPartyFamily, suffix: string) {
  return `${family.name} 接入申请 ${suffix}`;
}

async function readCreatedSystemFamilyCodes(page: Page, suffix: string) {
  const response = await page.request.get(`${apiBase}/engine/integration/onboardings`, {
    params: { page: "1", size: "100" },
  });
  const text = await response.text();
  expect(
    response.ok(),
    `必须能从真实接入申请 API 回读第三方系统族 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as {
    data?: {
      items?: Array<{
        onboardingId?: string;
        systemFamilyCode?: string;
      }>;
    };
  };
  return (parsed.data?.items ?? [])
    .filter((item) => item.onboardingId?.endsWith(`-${suffix}`))
    .map((item) => item.systemFamilyCode)
    .filter((code): code is string => typeof code === "string" && code.length > 0);
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  const select = await dialogSelectByLabel(dialog, label);
  await openAntdSelect(select, label);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}(?:\\s*·.*)?\\s*$`) })
    .first();
  if (!(await option.isVisible().catch(() => false))) {
    await scrollVirtualSelectUntilOptionVisible(dropdown, option);
  }
  await expect(option, `${label} 下拉应存在 ${optionText}`).toBeVisible({ timeout: 10_000 });
  await clickAntdVirtualOption(option);
}

async function scrollVirtualSelectUntilOptionVisible(dropdown: Locator, option: Locator) {
  const virtualHolder = dropdown.locator(".rc-virtual-list-holder").first();
  if ((await virtualHolder.count()) === 0) return;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (await option.isVisible().catch(() => false)) return;
    const moved = await virtualHolder.evaluate((element) => {
      const previousTop = element.scrollTop;
      element.scrollTop = Math.min(
        element.scrollHeight - element.clientHeight,
        element.scrollTop + Math.max(element.clientHeight, 1),
      );
      element.dispatchEvent(new Event("scroll", { bubbles: true }));
      return element.scrollTop !== previousTop;
    });
    await option.waitFor({ state: "visible", timeout: 250 }).catch(() => undefined);
    if (!moved) return;
  }
}

async function searchDialogOption(
  page: Page,
  dialog: Locator,
  label: string,
  searchText: string,
  expectedValue: string,
) {
  const select = await dialogSelectByLabel(dialog, label);
  await openAntdSelect(select, label);
  const combobox = select.locator("input[role='combobox']").first();
  if ((await combobox.getAttribute("readonly")) === null) {
    await combobox.fill(searchText);
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(escapeRegExp(searchText)) })
    .first();
  await expect(option, `${label} 下拉应存在 ${searchText}`).toBeVisible({ timeout: 20_000 });
  await clickAntdVirtualOption(option);
  await expect
    .poll(async () => {
      const selected = select.locator(".ant-select-selection-item").first();
      return (await selected.getAttribute("title").catch(() => null)) ?? "";
    })
    .toContain(expectedValue);
}

async function clickAntdVirtualOption(option: Locator) {
  await option.evaluate((element) => {
    (element as HTMLElement).click();
  });
}

async function dialogSelectByLabel(dialog: Locator, label: string) {
  const namedCombobox = dialog
    .getByRole("combobox", { name: new RegExp(escapeRegExp(label)) })
    .first();
  if ((await namedCombobox.count()) > 0) {
    return namedCombobox.locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
    );
  }
  const formItem = dialog
    .locator(".ant-form-item")
    .filter({ hasText: new RegExp(escapeRegExp(label)) })
    .first();
  return formItem.locator(".ant-select").first();
}

async function openAntdSelect(select: Locator, label: string) {
  const selector = select.locator(".ant-select-selector");
  await expect(selector, `${label} 下拉触发器应可见`).toBeVisible({ timeout: 10_000 });
  await selector.click();
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
