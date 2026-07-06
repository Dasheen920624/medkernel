import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { ensureReadySession, requiredRuntimeAssetsForRehearsal } from "./support/auth";

type RuntimeCollectors = {
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

type RuntimeRecord = RuntimeCollectors & {
  stage: string;
  url: string;
};

type RuntimeReleaseDetail = {
  release?: { revisionNo?: number };
  items?: RuntimeReleaseItem[];
};

type RuntimeReleaseItem = {
  assetType?: string;
  assetIdentity?: string;
  entryState?: string;
  versionId?: string;
};
type RuntimeAssetSelection = {
  assetType?: string;
  assetIdentity?: string;
  versionId?: string | null;
};

test.describe.configure({ mode: "serial" });

test.describe("机构生效版本真实前台发布回滚", () => {
  test("医疗引擎运营员可为本院生成新生效版本并从历史版本回滚", async ({ page }, testInfo) => {
    test.setTimeout(300_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];

    try {
      await ensureReadySession(page, "engine-operator");
      clearRuntime(runtime);
      await page.goto("/config/releases", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "机构生效版本" })).toBeVisible();
      await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
      await expect(page.getByText("平台标准版本由平台治理入口发布")).toBeVisible();
      await page.getByRole("tab", { name: "机构生效版本" }).click();
      await chooseHospital(page, "本地上线演练医院");
      await expect(page.getByText(/当前机构生效版本 第 \d+ 版/)).toBeVisible({
        timeout: 20_000,
      });
      await expectNoRootOverflow(page, "机构生效版本初始桌面");
      const initialRevision = await currentHospitalRevision(page);
      recordCleanRuntime(page, "选择本地上线演练医院", runtime, records);

      clearRuntime(runtime);
      await assessLocalReleaseImpactIfRequired(page);
      const activateResponsePromise = waitForPost(
        page,
        "/engine/releases/hospitals/",
        "/runtime-releases",
      );
      await page.getByRole("button", { name: "生成新机构生效版本" }).click();
      const activateResponse = await activateResponsePromise;
      const activateBody = await activateResponse.text();
      expect(
        activateResponse.ok(),
        `前台生成机构生效版本应返回成功 status=${activateResponse.status()} body=${activateBody}`,
      ).toBe(true);
      assertRuntimeReleaseRequestCarriesRequiredAssets(
        activateResponse.request().postDataJSON(),
        "前台生成机构生效版本",
      );
      const activated = JSON.parse(activateBody) as { data?: { revisionNo?: number } };
      expect(activated.data?.revisionNo, "生成机构生效版本响应应返回新修订号").toBeGreaterThan(
        initialRevision,
      );
      await expect(
        page.getByText(`当前机构生效版本 第 ${activated.data?.revisionNo} 版`),
      ).toBeVisible({ timeout: 20_000 });
      await assertCurrentRuntimeAssetsReady(
        page,
        "本地上线演练医院",
        activated.data?.revisionNo,
        "前台生成新机构生效版本",
      );
      recordCleanRuntime(page, "前台生成新机构生效版本", runtime, records);

      clearRuntime(runtime);
      await page.getByRole("button", { name: "刷新" }).click();
      const rollbackButton = page
        .getByRole("button", { name: `回滚到 第 ${initialRevision} 版` })
        .first();
      await expect(rollbackButton).toBeVisible({ timeout: 20_000 });
      const rollbackResponsePromise = waitForPost(
        page,
        "/engine/releases/hospitals/",
        "/runtime-releases:rollback",
      );
      await rollbackButton.click();
      await page.getByRole("button", { name: "确认回滚" }).click();
      const rollbackResponse = await rollbackResponsePromise;
      const rollbackBody = await rollbackResponse.text();
      expect(
        rollbackResponse.ok(),
        `前台回滚机构生效版本应返回成功 status=${rollbackResponse.status()} body=${rollbackBody}`,
      ).toBe(true);
      const rolledBack = JSON.parse(rollbackBody) as { data?: { revisionNo?: number } };
      expect(rolledBack.data?.revisionNo, "回滚应复制历史清单并生成更高修订号").toBeGreaterThan(
        activated.data?.revisionNo ?? initialRevision,
      );
      await expect(
        page.getByText(`当前机构生效版本 第 ${rolledBack.data?.revisionNo} 版`),
      ).toBeVisible({ timeout: 20_000 });
      await assertCurrentRuntimeAssetsReady(
        page,
        "本地上线演练医院",
        rolledBack.data?.revisionNo,
        "前台从历史机构生效版本回滚",
      );
      recordCleanRuntime(page, "前台从历史机构生效版本回滚", runtime, records);
      await captureEvidence(page, testInfo, "runtime-release-frontdesk-rollback");
    } finally {
      await attachRuntimeRecords(testInfo, records);
    }
  });
});

async function chooseHospital(page: Page, hospitalName: string) {
  const combobox = page.getByRole("combobox", { name: "目标医院" });
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.locator(".ant-select-selector").click();
  await combobox.fill(hospitalName);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 10_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: hospitalName })
    .first();
  await expect(option).toBeVisible({ timeout: 20_000 });
  await option.click();
}

async function assertCurrentRuntimeAssetsReady(
  page: Page,
  hospitalName: string,
  expectedRevision: number | undefined,
  label: string,
) {
  const hospitalId = await resolveHospitalId(page, hospitalName);
  const response = await page.request.get(
    `/medkernel/api/v1/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-releases/current`,
    { headers: { "X-Trace-Id": `e2e-runtime-current-${Date.now()}` } },
  );
  const body = await response.text();
  expect(
    response.ok(),
    `${label} 后应能读取当前机构生效版本 status=${response.status()} body=${body}`,
  ).toBe(true);
  const current = JSON.parse(body) as { data?: RuntimeReleaseDetail | null };
  if (expectedRevision !== undefined) {
    expect(current.data?.release?.revisionNo, `${label} 后当前版本应指向新修订`).toBe(
      expectedRevision,
    );
  }
  assertRuntimeDetailCarriesRequiredAssets(current.data, label);
}

async function resolveHospitalId(page: Page, hospitalName: string) {
  const response = await page.request.get(
    `/medkernel/api/v1/engine/org/org-units?keyword=${encodeURIComponent(
      hospitalName,
    )}&page=1&size=20`,
    { headers: { "X-Trace-Id": `e2e-runtime-hospital-${Date.now()}` } },
  );
  const body = await response.text();
  expect(response.ok(), `应能按名称读取演练医院 status=${response.status()} body=${body}`).toBe(
    true,
  );
  const parsed = JSON.parse(body) as {
    data?: { items?: Array<{ id?: string; name?: string; level?: string }> };
  };
  const hospital = (parsed.data?.items ?? []).find(
    (item) => item.name === hospitalName && item.level === "FACILITY" && item.id,
  );
  expect(hospital?.id, `应能解析演练医院 ${hospitalName} 的组织 ID`).toBeTruthy();
  return hospital?.id ?? "";
}

function assertRuntimeReleaseRequestCarriesRequiredAssets(value: unknown, label: string) {
  const activeAssets = Array.isArray((value as { activeAssets?: unknown }).activeAssets)
    ? ((value as { activeAssets: RuntimeAssetSelection[] }).activeAssets ?? [])
    : [];
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const match = activeAssets.find(
      (item) =>
        item.assetType === required.assetType && item.assetIdentity === required.assetIdentity,
    );
    expect(
      match,
      `${label} 请求必须携带 ${required.assetType} ${required.assetIdentity}`,
    ).toBeTruthy();
  }
}

function assertRuntimeDetailCarriesRequiredAssets(
  detail: RuntimeReleaseDetail | null | undefined,
  label: string,
) {
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const match = (detail?.items ?? []).find(
      (item) =>
        item.assetType === required.assetType &&
        item.assetIdentity === required.assetIdentity &&
        item.entryState === "ACTIVE" &&
        Boolean(item.versionId),
    );
    expect(
      match,
      `${label} 后必须启用 ${required.assetType} ${required.assetIdentity}`,
    ).toBeTruthy();
  }
}

async function assessLocalReleaseImpactIfRequired(page: Page) {
  const impactButton = page.getByRole("button", { name: "评估发布影响" });
  if ((await impactButton.count()) === 0 || !(await impactButton.first().isVisible())) {
    return;
  }
  const simulationResponsePromise = waitForPost(page, "/engine/versioning/releases/simulations");
  await impactButton.first().click();
  const simulationResponse = await simulationResponsePromise;
  const simulationBody = await simulationResponse.text();
  expect(
    simulationResponse.ok(),
    `发布影响评估应返回成功 status=${simulationResponse.status()} body=${simulationBody}`,
  ).toBe(true);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText("发布影响评估未完成")).toHaveCount(0);
  await expect(page.getByText("需处理")).toHaveCount(0);
}

async function currentHospitalRevision(page: Page) {
  const heading = page.getByText(/当前机构生效版本 第 \d+ 版/).first();
  const text = (await heading.textContent()) ?? "";
  const match = text.match(/第\s*(\d+)\s*版/);
  expect(match?.[1], `应能从当前机构生效版本标题解析修订号，实际文本：${text}`).toBeTruthy();
  return Number(match?.[1]);
}

function waitForPost(page: Page, urlPart: string, secondUrlPart?: string) {
  return page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes(urlPart) &&
      (!secondUrlPart || response.url().includes(secondUrlPart)),
    { timeout: 60_000 },
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
  const recordPath = testInfo.outputPath("runtime-release-frontdesk-records.json");
  await writeFile(recordPath, `${JSON.stringify(records, null, 2)}\n`, "utf8");
  await testInfo.attach("runtime-release-frontdesk-records", {
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
