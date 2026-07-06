import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import {
  ensureReadySession,
  expectOk,
  postApi,
  requiredRuntimeAssetsForRehearsal,
} from "./support/auth";

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
  versionNo?: string;
};
type RuntimeAssetSelection = {
  assetType?: string;
  assetIdentity?: string;
  versionId?: string | null;
};
type RuntimeReleaseLocalCandidate = {
  assetType: string;
  assetIdentity: string;
  versionId: string;
  versionNo?: string;
};
type RuntimeReleaseAssetEvidence = {
  assetType: string;
  assetIdentity: string;
  versionId: string;
  versionNo?: string;
  entryState?: string;
};
type RuntimeReleaseCoverageEvidence = {
  productLayers: string[];
  versionedAssets: string[];
  deliveryShapes: string[];
  serviceCombinations: string[];
  apiEvidence: Record<string, boolean>;
  activatedRevisionNo?: number;
  rolledBackRevisionNo?: number;
  localCandidate?: RuntimeReleaseLocalCandidate;
  activationRequest?: { activeAssets: RuntimeReleaseAssetEvidence[] };
  activationReadback?: { assets: RuntimeReleaseAssetEvidence[] };
  runtimeConsumerReadback?: { assets: RuntimeReleaseAssetEvidence[] };
  rollbackReadback?: { localCandidateAbsent: boolean; assets: RuntimeReleaseAssetEvidence[] };
  rollbackRuntimeConsumerReadback?: {
    localCandidateAbsent: boolean;
    assets: RuntimeReleaseAssetEvidence[];
  };
  scenarioEvidence: Array<{ observedStages: string[] }>;
};

test.describe.configure({ mode: "serial" });

test.describe("机构生效版本真实前台发布回滚", () => {
  test("医疗引擎运营员可为本院生成新生效版本并从历史版本回滚", async ({ page }, testInfo) => {
    test.setTimeout(300_000);
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];
    const coverageEvidence = createRuntimeReleaseCoverageEvidence();

    try {
      await ensureReadySession(page, "engine-operator");
      const localCandidate = await createHospitalRuntimeReleaseCandidate(page, testInfo);
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
      await assertRequiredRuntimeInputsVisibleAndSelected(page);
      recordRuntimeReleaseStage(coverageEvidence, "前台展示并勾选 13 类平台标准资产");
      await selectHospitalLocalRuntimeCandidate(page, localCandidate);

      clearRuntime(runtime);
      await assessLocalReleaseImpact(page);
      coverageEvidence.apiEvidence.impactSimulationRun = true;
      recordRuntimeReleaseStage(coverageEvidence, "前台评估机构生效版本发布影响");
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
      const activationRequest = activateResponse.request().postDataJSON();
      const activationRequestAssets = assertRuntimeReleaseRequestCarriesRequiredAssets(
        activationRequest,
        "前台生成机构生效版本",
      );
      const activationRequestCandidate = assertRuntimeAssetsContainLocalCandidate(
        activationRequestAssets,
        localCandidate,
        "前台生成机构生效版本请求",
      );
      coverageEvidence.apiEvidence.activationPosted = true;
      coverageEvidence.apiEvidence.activationRequestCarriesRequiredAssets = true;
      coverageEvidence.localCandidate = localCandidate;
      coverageEvidence.activationRequest = { activeAssets: [activationRequestCandidate] };
      recordRuntimeReleaseStage(
        coverageEvidence,
        "前台生成携带 13 类资产闭包的机构生效版本",
      );
      const activated = JSON.parse(activateBody) as { data?: { revisionNo?: number } };
      expect(activated.data?.revisionNo, "生成机构生效版本响应应返回新修订号").toBeGreaterThan(
        initialRevision,
      );
      await expect(
        page.getByText(`当前机构生效版本 第 ${activated.data?.revisionNo} 版`),
      ).toBeVisible({ timeout: 20_000 });
      const activatedRuntime = await assertCurrentRuntimeAssetsReady(
        page,
        "本地上线演练医院",
        activated.data?.revisionNo,
        "前台生成新机构生效版本",
      );
      const activationReadbackCandidate = assertRuntimeAssetsContainLocalCandidate(
        activatedRuntime.items ?? [],
        localCandidate,
        "前台生成新机构生效版本后端读回",
        { requireActive: true },
      );
      coverageEvidence.activationReadback = { assets: [activationReadbackCandidate] };
      coverageEvidence.apiEvidence.currentReleaseReadback = true;
      recordRuntimeReleaseStage(coverageEvidence, "后端回读当前机构生效版本资产闭包");
      const activatedConsumer = await assertThirdPartyRuntimeConsumerCarriesRequiredAssets(
        page,
        activated.data?.revisionNo,
        "前台生成新机构生效版本",
      );
      const activationConsumerCandidate = assertRuntimeAssetsContainLocalCandidate(
        activatedConsumer.assets ?? [],
        localCandidate,
        "前台生成新机构生效版本第三方运行契约",
        { requireActive: true },
      );
      coverageEvidence.runtimeConsumerReadback = { assets: [activationConsumerCandidate] };
      coverageEvidence.apiEvidence.runtimeConsumerReadback = true;
      coverageEvidence.activatedRevisionNo = activated.data?.revisionNo;
      recordRuntimeReleaseStage(coverageEvidence, "第三方运行契约读取同一机构生效版本");
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
      coverageEvidence.apiEvidence.rollbackPosted = true;
      recordRuntimeReleaseStage(coverageEvidence, "前台从历史机构生效版本回滚");
      const rolledBack = JSON.parse(rollbackBody) as { data?: { revisionNo?: number } };
      expect(rolledBack.data?.revisionNo, "回滚应复制历史清单并生成更高修订号").toBeGreaterThan(
        activated.data?.revisionNo ?? initialRevision,
      );
      await expect(
        page.getByText(`当前机构生效版本 第 ${rolledBack.data?.revisionNo} 版`),
      ).toBeVisible({ timeout: 20_000 });
      const rollbackRuntime = await assertCurrentRuntimeAssetsReady(
        page,
        "本地上线演练医院",
        rolledBack.data?.revisionNo,
        "前台从历史机构生效版本回滚",
      );
      coverageEvidence.rollbackReadback = assertRuntimeAssetsExcludeLocalCandidate(
        rollbackRuntime.items ?? [],
        localCandidate,
        "前台从历史机构生效版本回滚后端读回",
      );
      coverageEvidence.apiEvidence.rollbackCurrentReleaseReadback = true;
      const rollbackConsumer = await assertThirdPartyRuntimeConsumerCarriesRequiredAssets(
        page,
        rolledBack.data?.revisionNo,
        "前台从历史机构生效版本回滚",
      );
      coverageEvidence.rollbackRuntimeConsumerReadback = assertRuntimeAssetsExcludeLocalCandidate(
        rollbackConsumer.assets ?? [],
        localCandidate,
        "前台从历史机构生效版本回滚第三方运行契约",
      );
      coverageEvidence.apiEvidence.rollbackRuntimeConsumerReadback = true;
      coverageEvidence.rolledBackRevisionNo = rolledBack.data?.revisionNo;
      recordRuntimeReleaseStage(
        coverageEvidence,
        "回滚后后端和第三方运行契约读取同一修订",
      );
      recordCleanRuntime(page, "前台从历史机构生效版本回滚", runtime, records);
      await captureEvidence(page, testInfo, "runtime-release-frontdesk-rollback");
    } finally {
      await attachRuntimeRecords(testInfo, records);
      await attachRuntimeReleaseCoverageEvidence(testInfo, coverageEvidence);
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

async function assertRequiredRuntimeInputsVisibleAndSelected(page: Page) {
  const details = page.getByRole("switch", { name: "证据详情" });
  if ((await details.count()) > 0 && !(await details.first().isChecked())) {
    await details.first().click();
  }
  const platformStandardContent = page
    .locator(".ant-card")
    .filter({ has: page.getByText("平台标准内容", { exact: true }) })
    .first();
  await expect(platformStandardContent, "机构生效版本页必须展示平台标准内容清单").toBeVisible({
    timeout: 20_000,
  });
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const platformInputRow = platformStandardContent
      .getByRole("row")
      .filter({ hasText: required.assetIdentity })
      .first();
    await expect(
      platformInputRow,
      `平台标准内容必须展示 ${required.assetType} ${required.assetIdentity}`,
    ).toBeVisible({ timeout: 20_000 });
    const enableCheckbox = platformInputRow.getByRole("checkbox", { name: /启用/ });
    await expect(
      enableCheckbox,
      `${required.assetType} ${required.assetIdentity} 必须可勾选进入机构生效版本`,
    ).toBeVisible();
    await expect(
      enableCheckbox,
      `${required.assetType} ${required.assetIdentity} 必须在当前选择集中`,
    ).toBeChecked();
  }
}

async function createHospitalRuntimeReleaseCandidate(
  page: Page,
  testInfo: TestInfo,
): Promise<RuntimeReleaseLocalCandidate> {
  const suffix = `${Date.now()}-${testInfo.retry}`;
  const assetIdentity = `ACTION_CARD.RUNTIME.RELEASE.${suffix}`;
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity,
    applicableScope: "ALL",
    sourceRef: "local-e2e:runtime-release-frontdesk",
    content: {
      schemaVersion: "1.0",
      title: "机构生效版本本院提示卡",
      actionCode: "INFO",
      atSeverity: "LOW",
      indicator: "info",
      summary: "用于验证本院候选资产进入机构生效版本前必须完成发布影响评估。",
      detail: "本资产仅用于本地上线演练，不包含诊疗结论，不自动开立医嘱。",
      source: { label: "MedKernel 本地上线演练" },
      suggestions: [
        { label: "查看机构生效版本", actionType: "OPEN_FORM", payload: { target: "runtime" } },
      ],
      overrideReasons: [],
      requiresPhysicianConfirmation: false,
    },
  });
  await expectOk(response, "创建本院机构生效版本候选资产");
  const payload = (await response.json()) as {
    data?: { assetType?: string; assetIdentity?: string; versionId?: string; versionNo?: string };
  };
  const candidate = payload.data;
  expect(candidate?.assetType, "本院候选资产类型必须返回").toBe("ACTION_CARD");
  expect(candidate?.assetIdentity, "本院候选资产身份必须返回").toBe(assetIdentity);
  expect(candidate?.versionId, "本院候选资产必须返回版本 ID").toBeTruthy();
  return {
    assetType: candidate?.assetType ?? "ACTION_CARD",
    assetIdentity: candidate?.assetIdentity ?? assetIdentity,
    versionId: candidate?.versionId ?? "",
    versionNo: candidate?.versionNo,
  };
}

async function selectHospitalLocalRuntimeCandidate(
  page: Page,
  candidate: RuntimeReleaseLocalCandidate,
) {
  const localContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("集团与本院内容", { exact: true }) })
    .first();
  await expect(localContentCard, "机构生效版本页必须展示集团与本院内容清单").toBeVisible({
    timeout: 20_000,
  });
  const candidateRow = localContentCard
    .getByRole("row")
    .filter({ hasText: candidate.assetIdentity })
    .filter({ hasText: "本院 · 临床提示卡内容" })
    .first();
  await expect(
    candidateRow,
    `集团与本院内容必须展示本轮本院候选资产 ${candidate.assetIdentity}`,
  ).toBeVisible({ timeout: 20_000 });
  const enableCheckbox = candidateRow.getByRole("checkbox", {
    name: /启用本院临床提示卡内容/u,
  });
  await expect(enableCheckbox, "本轮本院候选资产必须可勾选进入机构生效版本").toBeVisible();
  if (!(await enableCheckbox.isChecked())) {
    await enableCheckbox.check();
  }
  await expect(enableCheckbox, "本轮本院候选资产必须已选入机构生效版本").toBeChecked();
  await expect(candidateRow, "本轮候选行必须展示候选版本").toContainText(
    candidate.versionNo ?? candidate.versionId,
  );
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
  return current.data ?? { items: [] };
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
    expect(
      match?.versionId ?? null,
      `${label} 请求中 ${required.assetType} ${required.assetIdentity} 必须沿用平台标准版本`,
    ).toBeNull();
  }
  return activeAssets;
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

async function assertThirdPartyRuntimeConsumerCarriesRequiredAssets(
  page: Page,
  expectedRevision: number | undefined,
  label: string,
) {
  const response = await page.request.get(
    "/medkernel/api/v1/engine/integration/knowledge-runtime/runtime-release/current",
    { headers: { "X-Trace-Id": `e2e-runtime-consumer-${Date.now()}` } },
  );
  const text = await response.text();
  expect(
    response.ok(),
    `${label} 后第三方运行契约必须读取当前机构生效版本 status=${response.status()} body=${text}`,
  ).toBe(true);
  const parsed = JSON.parse(text) as {
    data?: {
      contractVersion?: string;
      revisionNo?: number;
      assetCount?: number;
      assets?: RuntimeReleaseItem[];
    };
  };
  expect(parsed.data?.contractVersion, `${label} 后运行契约版本必须稳定`).toBe("v1");
  if (expectedRevision !== undefined) {
    expect(parsed.data?.revisionNo, `${label} 后运行消费者必须读取同一修订号`).toBe(
      expectedRevision,
    );
  }
  expect(parsed.data?.assetCount, `${label} 后运行消费者必须返回资产数`).toBe(
    parsed.data?.assets?.length,
  );
  assertRuntimeDetailCarriesRequiredAssets({ items: parsed.data?.assets ?? [] }, label);
  return parsed.data ?? { assets: [] };
}

function assertRuntimeAssetsContainLocalCandidate(
  assets: Array<RuntimeAssetSelection | RuntimeReleaseItem>,
  candidate: RuntimeReleaseLocalCandidate,
  label: string,
  options: { requireActive?: boolean } = {},
): RuntimeReleaseAssetEvidence {
  const match = assets.find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      (!options.requireActive || (item as RuntimeReleaseItem).entryState === "ACTIVE"),
  );
  expect(
    match,
    `${label} 必须包含本轮本院候选资产 ${candidate.assetType} ${candidate.assetIdentity} ${candidate.versionId}`,
  ).toBeTruthy();
  if (candidate.versionNo && "versionNo" in (match ?? {})) {
    expect(
      (match as RuntimeReleaseItem | undefined)?.versionNo,
      `${label} 中本轮本院候选资产版本号必须一致`,
    ).toBe(candidate.versionNo);
  }
  return toRuntimeReleaseAssetEvidence(match ?? candidate, candidate);
}

function assertRuntimeAssetsExcludeLocalCandidate(
  assets: RuntimeReleaseItem[],
  candidate: RuntimeReleaseLocalCandidate,
  label: string,
) {
  const stillPresent = assets.find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId,
  );
  expect(
    stillPresent,
    `${label} 不应继续包含本轮本院候选资产 ${candidate.assetType} ${candidate.assetIdentity} ${candidate.versionId}`,
  ).toBeUndefined();
  return {
    localCandidateAbsent: true,
    assets: assets.map((asset) => toRuntimeReleaseAssetEvidence(asset)),
  };
}

function toRuntimeReleaseAssetEvidence(
  asset: RuntimeAssetSelection | RuntimeReleaseItem | RuntimeReleaseLocalCandidate,
  fallback?: RuntimeReleaseLocalCandidate,
): RuntimeReleaseAssetEvidence {
  const entry = asset as RuntimeReleaseItem;
  return {
    assetType: asset.assetType ?? fallback?.assetType ?? "",
    assetIdentity: asset.assetIdentity ?? fallback?.assetIdentity ?? "",
    versionId: asset.versionId ?? fallback?.versionId ?? "",
    ...(entry.versionNo ? { versionNo: entry.versionNo } : {}),
    ...(entry.entryState ? { entryState: entry.entryState } : {}),
  };
}

async function assessLocalReleaseImpact(page: Page) {
  const impactButton = page.getByRole("button", { name: "评估发布影响" });
  await expect(impactButton.first(), "选择本院候选后必须先评估发布影响").toBeVisible({
    timeout: 20_000,
  });
  const simulationResponsePromise = waitForPost(page, "/engine/versioning/releases/simulations");
  await impactButton.first().click();
  const simulationResponse = await simulationResponsePromise;
  const simulationBody = await simulationResponse.text();
  expect(
    simulationResponse.ok(),
    `发布影响评估应返回成功 status=${simulationResponse.status()} body=${simulationBody}`,
  ).toBe(true);
  const simulation = JSON.parse(simulationBody) as { data?: { releasable?: boolean } };
  expect(simulation.data?.releasable, "本院候选发布影响评估必须允许生成机构生效版本").toBe(
    true,
  );
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

function createRuntimeReleaseCoverageEvidence(): RuntimeReleaseCoverageEvidence {
  return {
    productLayers: ["RELEASE_GOVERNANCE"],
    versionedAssets: requiredRuntimeAssetsForRehearsal.map((asset) => asset.assetType),
    deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
    serviceCombinations: ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"],
    apiEvidence: {
      impactSimulationRun: false,
      activationPosted: false,
      activationRequestCarriesRequiredAssets: false,
      currentReleaseReadback: false,
      runtimeConsumerReadback: false,
      rollbackPosted: false,
      rollbackCurrentReleaseReadback: false,
      rollbackRuntimeConsumerReadback: false,
    },
    scenarioEvidence: [{ observedStages: [] }],
  };
}

function recordRuntimeReleaseStage(evidence: RuntimeReleaseCoverageEvidence, stage: string) {
  const stages = evidence.scenarioEvidence[0]?.observedStages ?? [];
  if (!stages.includes(stage)) {
    stages.push(stage);
  }
  evidence.scenarioEvidence = [{ observedStages: stages }];
}

async function attachRuntimeReleaseCoverageEvidence(
  testInfo: TestInfo,
  evidence: RuntimeReleaseCoverageEvidence,
) {
  const recordPath = testInfo.outputPath("runtime-release-coverage-codes.json");
  await writeFile(recordPath, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  await testInfo.attach("runtime-release-coverage-codes", {
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
