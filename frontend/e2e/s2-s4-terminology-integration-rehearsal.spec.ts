import {
  request as playwrightRequest,
  expect,
  test,
  type APIResponse,
  type Locator,
  type Page,
  type Request,
  type Response,
  type TestInfo,
} from "@playwright/test";
import { createHmac } from "node:crypto";

import {
  apiBase,
  appPath,
  ensureReadySession,
  expectOk,
  getApi,
  pageItems,
  postApi,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  textField,
  waitForPollingInterval,
} from "./support/auth";

type RuntimeAsset = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type TerminologyEvidence = {
  assetType: "TERMINOLOGY";
  assetIdentity: string;
  versionId: string;
  standardSystem: string;
  standardCode: string;
  localCode: string;
  sourceSystem: string;
  category: string;
  mappingId: number;
  versionNo: string;
  standardTermId?: number;
};

const requiredS2S4ScenarioEvidence = [
  {
    code: "S2",
    observedStages: [
      "平台管理员前台创建 LIS Webhook 适配器并配置字段映射",
      "平台管理员前台创建回调通道并完成签名预览",
      "真实 Webhook 入站通过验签并生成标准临床事件",
      "入站字段映射按当前机构生效版本完成术语归一",
    ],
  },
  {
    code: "S4",
    observedStages: [
      "前台登记标准术语",
      "签名主数据同步登记院内术语",
      "前台生成并确认术语映射候选",
      "前台生成不可变术语资产版本",
      "当前机构生效版本和第三方运行契约读回同一术语资产",
    ],
  },
] as const;

test.describe.configure({ mode: "serial" });

test.describe("S2/S4 系统接入与术语映射运行消费真实演练", () => {
  test("平台管理员完成系统接入且运营员完成术语映射后真实入站消息按当前机构生效版本归一", async ({
    page,
  }, testInfo) => {
    test.setTimeout(360_000);
    const observedStages = new Set<string>();
    const suffix = Date.now().toString(36);
    const sourceSystem = "LIS";
    const adapterId = `s2s4-lis-${suffix}`;
    const webhookId = `s2s4-webhook-${suffix}`;
    const webhookName = `S2S4 LIS 入站 ${suffix}`;
    const standardSystem = "LOINC";
    const standardCode = `S2S4-${suffix.toUpperCase()}`;
    const localCode = `LIS-HGB-${suffix.toUpperCase()}`;
    const assetIdentity = `TERM.LAB.S2S4.${suffix.toUpperCase()}`;

    await ensureReadySession(page, "engine-operator");
    const hospitalId = await resolveHospitalId(page, "本地上线演练医院");
    const currentBefore = await readCurrentHospitalRuntime(page, hospitalId, "演练前");
    const platformBaselineReleaseId = textField(currentBefore.release, "platformBaselineReleaseId");
    expect(platformBaselineReleaseId, "当前机构生效版本必须绑定平台标准版本").toBeTruthy();

    await ensureReadySession(page, "platform-admin");
    await setEvidenceDetails(page, false);
    await createLisWebhookAdapterFromFrontdesk(page, {
      adapterId,
      suffix,
      standardSystem,
    });
    observedStages.add("平台管理员前台创建 LIS Webhook 适配器并配置字段映射");
    const webhookSecret = await createWebhookFromFrontdesk(page, {
      webhookId,
      webhookName,
    });
    await generateWebhookSignaturePreview(page, {
      webhookId,
      webhookName,
    });
    observedStages.add("平台管理员前台创建回调通道并完成签名预览");

    const previousCursor = await readLatestMasterDataCursor(page, sourceSystem);
    const masterDataRequest = buildLocalTermMasterDataRequest({
      adapterId,
      sourceSystem,
      previousCursor,
      localCode,
      suffix,
    });
    await expectSignedMasterDataSyncRejectedWithInvalidSignature({
      webhookId,
      request: masterDataRequest,
    });
    await syncLocalTermThroughSignedMasterData({
      page,
      webhookId,
      webhookSecret,
      request: masterDataRequest,
    });
    observedStages.add("签名主数据同步登记院内术语");
    await assertMasterDataReconciliationFromFrontdesk(page, sourceSystem);

    await ensureReadySession(page, "engine-operator");
    await registerStandardTermFromFrontdesk(page, {
      standardSystem,
      standardCode,
      localCode,
      suffix,
    });
    observedStages.add("前台登记标准术语");
    const terminology = await generateAndConfirmCandidateFromFrontdesk(page, {
      sourceSystem,
      standardSystem,
      standardCode,
      localCode,
      suffix,
      assetIdentity,
    });
    observedStages.add("前台生成并确认术语映射候选");
    observedStages.add("前台生成不可变术语资产版本");

    const activatedRuntime = await activateRuntimeWithTerminologyAssetFromFrontdesk(page, {
      hospitalId,
      hospitalName: "本地上线演练医院",
      platformBaselineReleaseId: platformBaselineReleaseId ?? "",
      terminology,
    });
    const currentRuntime = activatedRuntime.runtime;
    const runtimeConsumerReadback = await readRuntimeConsumerContract(page);
    assertRuntimeContractMatchesTerminology(runtimeConsumerReadback, currentRuntime, terminology);
    observedStages.add("当前机构生效版本和第三方运行契约读回同一术语资产");

    await ensureReadySession(page, "platform-admin");
    const inboundRequest = buildInboundWebhookRequest({
      adapterId,
      sourceSystem,
      localCode,
      suffix,
    });
    await expectInboundWebhookRejectedWithInvalidSignature({
      page,
      webhookId,
      request: inboundRequest,
    });
    const inboundResult = await postSignedInboundWebhook({
      page,
      webhookId,
      webhookSecret,
      request: inboundRequest,
    });
    expect(inboundResult.status, "真实入站应成功处理").toBe("SUCCESS");
    expect(inboundResult.normalizedCodeCount, "真实入站必须完成 1 个术语归一").toBe(1);
    const normalized = findNormalizedCode(inboundResult.mappedPayload);
    expect(normalized?.standardCode, "入站归一应使用前台确认的标准码").toBe(standardCode);
    expect(normalized?.codeSystem, "入站归一应使用字段映射指定的标准字典").toBe(standardSystem);
    expect(normalized?.localCode, "入站归一应保留院内码").toBe(localCode);
    expect(normalized?.runtimeReleaseId, "入站归一必须绑定当前机构生效版本").toBe(
      currentRuntime.releaseId,
    );
    expect(normalized?.mappingId, "入站归一必须返回确认映射 ID").toBe(terminology.mappingId);
    expect(inboundResult.clinicalEventStatus, "入站必须生成标准临床事件").toBe("RECEIVED");
    observedStages.add("真实 Webhook 入站通过验签并生成标准临床事件");
    observedStages.add("入站字段映射按当前机构生效版本完成术语归一");

    await attachS2S4CoverageEvidence(testInfo, {
      observedStages,
      adapter: {
        adapterId,
        protocolType: "Webhook",
        sourceSystem,
        fieldMappings: [
          { sourcePath: "/patientId", targetPath: "/patient/mpi" },
          {
            sourcePath: "/labCode",
            targetPath: "/observations/0",
            targetDictionaryKey: standardSystem,
            category: "LAB",
          },
        ],
      },
      terminology,
      runtime: currentRuntime,
      activationRequest: activatedRuntime.activationRequest,
      inboundResult,
      runtimeConsumerReadback,
      invalidMasterDataSignatureRejected: true,
      invalidInboundWebhookSignatureRejected: true,
    });
  });
});

async function createLisWebhookAdapterFromFrontdesk(
  page: Page,
  options: { adapterId: string; suffix: string; standardSystem: string },
) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "系统接入" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("tab", { name: "适配器目录" }).click();
  await page.getByRole("button", { name: "新增适配器" }).click();
  const dialog = page.getByRole("dialog", { name: "新增适配器" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("稳定适配器身份").fill(options.adapterId);
  await dialog.getByLabel("系统名称").fill(`S2S4 LIS Webhook ${options.suffix}`);
  await chooseDialogOption(page, dialog, "接入协议", "Webhook");
  await dialog.getByLabel("服务地址").fill("https://lis.s2s4.example.test/api");
  await dialog.getByLabel("健康检查路径").fill("/health");
  await dialog.getByLabel("投递路径").fill("/messages");
  await dialog.getByLabel("来源字段路径").fill("/patientId");
  await dialog.getByLabel("标准字段路径").fill("/patient/mpi");
  await dialog.getByRole("button", { name: "添加字段映射" }).click();
  await dialog.getByLabel("来源字段路径").nth(1).fill("/labCode");
  await dialog.getByLabel("标准字段路径").nth(1).fill("/observations/0");
  await dialog.getByLabel("目标标准字典").nth(1).fill(options.standardSystem);
  await chooseFieldMappingCategory(page, dialog, 1, "检验");

  const responsePromise = waitForPost(page, "/api/v1/engine/integration/adapters");
  await dialog.getByRole("button", { name: "提交适配器" }).click();
  const response = await responsePromise;
  await expectBrowserResponseOk(response, "前台创建 LIS Webhook 适配器");
  const requestBody = await readRequestJson(response.request());
  const config = JSON.parse(String(requestBody.configJson ?? "{}")) as {
    fieldMappings?: Array<Record<string, unknown>>;
  };
  expect(config.fieldMappings?.[1]).toMatchObject({
    sourcePath: "/labCode",
    targetPath: "/observations/0",
    targetDictionaryKey: options.standardSystem,
    category: "LAB",
  });
  await expect(dialog).toBeHidden({ timeout: 20_000 });
}

async function createWebhookFromFrontdesk(
  page: Page,
  options: { webhookId: string; webhookName: string },
) {
  await page.getByRole("tab", { name: "回调通道" }).click();
  await page.getByRole("button", { name: "新增回调通道" }).click();
  const dialog = page.getByRole("dialog", { name: "新增回调通道" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("稳定回调通道身份").fill(options.webhookId);
  await dialog.getByPlaceholder(/临床事件回调/u).fill(options.webhookName);
  await dialog
    .getByPlaceholder(/his\.example\.org\/medkernel\/events/u)
    .fill("https://lis.s2s4.example.test/medkernel/events");
  await dialog.getByPlaceholder(/clinical\.event\.accepted/u).fill("LAB_RESULT MASTER_DATA");

  const responsePromise = waitForPost(page, "/api/v1/engine/integration/webhooks");
  await dialog.getByRole("button", { name: "创建回调通道" }).click();
  const response = await responsePromise;
  const created = await expectBrowserResponseOk<{ data?: { sharedSecret?: string } }>(
    response,
    "前台创建回调通道",
  );
  const secret = created.data?.sharedSecret;
  expect(secret, "创建回调通道必须只返回一次共享密钥供第三方签名").toBeTruthy();
  await page
    .getByRole("dialog", { name: "保存共享密钥" })
    .getByRole("button", {
      name: "我已安全保存",
    })
    .click();
  await expect(page.getByRole("dialog", { name: "保存共享密钥" })).toBeHidden({
    timeout: 20_000,
  });
  return secret ?? "";
}

async function generateWebhookSignaturePreview(
  page: Page,
  options: { webhookId: string; webhookName: string },
) {
  await page.getByRole("tab", { name: "回调通道" }).click();
  const signaturePreviewCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("签名预览", { exact: true }) })
    .first();
  await expect(signaturePreviewCard, "签名预览卡片必须可见").toBeVisible({
    timeout: 10_000,
  });
  await expectWebhookVisibleInFrontdesk(page, options.webhookName);
  await selectAntOptionFromSelect(
    page,
    signaturePreviewCard.locator(".ant-select").first(),
    options.webhookName,
  );
  const responsePromise = waitForPost(page, "/api/v1/engine/integration/webhooks/test");
  await page.getByRole("button", { name: "生成签名预览" }).click();
  const response = await responsePromise;
  await expectBrowserResponseOk(response, "前台生成回调通道签名预览");
  const requestBody = await readRequestJson(response.request());
  expect(requestBody?.webhookId, "签名预览必须绑定本轮回调通道").toBe(options.webhookId);
  await expect(page.getByText(/SIGNATURE_GENERATED|已生成本地 HMAC-SHA256 签名预览/u)).toBeVisible({
    timeout: 20_000,
  });
}

async function syncLocalTermThroughSignedMasterData(options: {
  page: Page;
  webhookId: string;
  webhookSecret: string;
  request: Record<string, unknown>;
}) {
  const timestamp = currentEpochSeconds();
  const signature = signHmacSha256(options.webhookSecret, timestamp, options.request);
  const response = await postExternalSignedApi(
    `/engine/integration/master-data/${encodeURIComponent(options.webhookId)}/sync`,
    options.request,
    {
      "X-MedKernel-Tenant": "t-e2e-rehearsal-local",
      "X-MedKernel-Timestamp": timestamp,
      "X-MedKernel-Signature": signature,
    },
  );
  expect(
    response.ok,
    `签名主数据同步登记院内术语 应返回成功 status=${response.status} body=${response.body}`,
  ).toBe(true);
}

function buildLocalTermMasterDataRequest(options: {
  adapterId: string;
  sourceSystem: string;
  previousCursor: string | null;
  localCode: string;
  suffix: string;
}) {
  // 签名必须基于读取到的最新服务端游标，避免绕过主数据增量连续性契约。
  return {
    batchId: `s2s4-md-${options.suffix}`,
    adapterId: options.adapterId,
    sourceSystem: options.sourceSystem,
    mode: "INCREMENTAL",
    previousCursor: options.previousCursor,
    cursor: `cursor-${options.suffix}`,
    authoritativeResourceTypes: [],
    items: [
      {
        recordId: options.localCode,
        resourceType: "LOCAL_TERM",
        operation: "UPSERT",
        sourceVersion: 1,
        sourceUpdatedAt: "2026-07-07T00:00:00Z",
        payload: {
          localCode: options.localCode,
          category: "LAB",
          localName: `S2S4 血红蛋白 ${options.suffix}`,
          normalizedName: `S2S4 血红蛋白 ${options.suffix}|${options.localCode}`,
          departmentCode: null,
          status: "ACTIVE",
        },
      },
    ],
  };
}

async function expectSignedMasterDataSyncRejectedWithInvalidSignature(options: {
  webhookId: string;
  request: Record<string, unknown>;
}) {
  const response = await postExternalSignedApi(
    `/engine/integration/master-data/${encodeURIComponent(options.webhookId)}/sync`,
    options.request,
    {
      "X-MedKernel-Tenant": "t-e2e-rehearsal-local",
      "X-MedKernel-Timestamp": currentEpochSeconds(),
      "X-MedKernel-Signature": "invalid-signature",
    },
  );
  expect(
    response.ok,
    `坏签名主数据同步必须被拒绝 status=${response.status} body=${response.body}`,
  ).toBe(false);
  expect([400, 401, 403], "坏签名主数据同步应返回认证或签名错误").toContain(response.status);
}

async function readLatestMasterDataCursor(page: Page, sourceSystem: string) {
  const response = await getApi(
    page,
    `/engine/integration/master-data/reconciliation?sourceSystem=${encodeURIComponent(
      sourceSystem,
    )}`,
  );
  await expectOk(response, "读取主数据最新同步游标");
  return textField(await responseData(response), "cursor");
}

async function assertMasterDataReconciliationFromFrontdesk(page: Page, sourceSystem: string) {
  await page.goto(appPath("/adapter/hub"), { waitUntil: "networkidle" });
  await page.getByRole("tab", { name: "主数据同步" }).click();
  await page.getByPlaceholder("例如 HIS、LIS、HRP").fill(sourceSystem);
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === "GET" &&
      response.url().includes("/engine/integration/master-data/reconciliation") &&
      response.url().includes(`sourceSystem=${sourceSystem}`),
  );
  await page.getByRole("button", { name: "查询对账" }).click();
  const response = await responsePromise;
  await expectBrowserResponseOk(response, "前台查询主数据对账");
  await expect(page.getByRole("cell", { name: "院内字典" }).first()).toBeVisible({
    timeout: 20_000,
  });
}

async function registerStandardTermFromFrontdesk(
  page: Page,
  options: { standardSystem: string; standardCode: string; localCode: string; suffix: string },
) {
  await page.goto(appPath("/terminology/mapping"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "术语字典" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("button", { name: "登记标准术语" }).click();
  const dialog = page.getByRole("dialog", { name: "登记标准术语" });
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByLabel("标准体系").fill(options.standardSystem);
  await dialog.getByLabel("标准编码").fill(options.standardCode);
  await selectAntOption(page, dialog, "术语类别", "检验");
  await dialog.getByLabel("标准名称").fill(`S2S4 血红蛋白标准 ${options.suffix}`);
  await dialog
    .getByLabel("规范名称")
    .fill(`S2S4 血红蛋白标准 ${options.suffix}|${options.localCode}`);
  await dialog.getByLabel("版本号").fill("2026.07");
  await dialog
    .getByLabel("依据说明")
    .fill("S2/S4 联合演练登记的 LOINC 检验术语，使用院内码作为确定性别名证据。");
  const responsePromise = waitForPost(page, "/api/v1/engine/terminology/terms/standard");
  await dialog.getByRole("button", { name: "提交登记" }).click();
  const response = await responsePromise;
  await expectBrowserResponseOk(response, "前台登记标准术语");
  await expect(dialog).toBeHidden({ timeout: 20_000 });
}

async function generateAndConfirmCandidateFromFrontdesk(
  page: Page,
  options: {
    sourceSystem: string;
    standardSystem: string;
    standardCode: string;
    localCode: string;
    suffix: string;
    assetIdentity: string;
  },
): Promise<TerminologyEvidence> {
  await page.goto(appPath("/terminology/mapping"), { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "生成候选" }).click();
  const generateDialog = page.getByRole("dialog", { name: "生成术语候选" });
  await expect(generateDialog).toBeVisible({ timeout: 10_000 });
  await generateDialog.getByLabel("来源系统").fill(options.sourceSystem);
  await generateDialog.getByLabel("最低语义分").fill("0.2");
  const generationResponsePromise = waitForPost(
    page,
    "/api/v1/engine/terminology/mappings/candidates",
  );
  await generateDialog.getByRole("button", { name: "提交生成" }).click();
  const generationResponse = await generationResponsePromise;
  const generation = await expectBrowserResponseOk<{ data?: { jobCode?: string } }>(
    generationResponse,
    "前台生成术语映射候选",
  );
  const jobCode = generation.data?.jobCode;
  expect(jobCode, "候选生成必须返回任务号").toBeTruthy();
  const candidate = await waitForTerminologyCandidate(page, {
    jobCode: jobCode ?? "",
    localCode: options.localCode,
  });
  const canonicalLocalCode = canonicalTerminologyAlias(options.localCode);
  const candidateRow = page.getByRole("row").filter({ hasText: canonicalLocalCode }).first();
  await expect(candidateRow, "候选映射表格必须展示本轮院内码确定性别名证据").toBeVisible({
    timeout: 20_000,
  });
  await candidateRow.getByRole("button", { name: /确\s*认/u }).click();
  const confirmDialog = page.getByRole("dialog", { name: /确认普通候选|确认高危候选/u });
  await expect(confirmDialog).toBeVisible({ timeout: 10_000 });
  await confirmDialog.getByLabel(/确认说明|核对依据/u).fill("S2/S4 演练核对院内码与标准码一致。");
  const confirmResponsePromise = waitForPost(
    page,
    `/api/v1/engine/terminology/mappings/${candidate.id}/confirm`,
  );
  await confirmDialog.getByRole("button", { name: "提交确认" }).click();
  const confirmResponse = await confirmResponsePromise;
  const confirmed = await expectBrowserResponseOk<{
    data?: { id?: number; standardTermId?: number };
  }>(confirmResponse, "前台确认术语映射候选");
  const mappingId = confirmed.data?.id;
  expect(mappingId, "确认术语映射必须返回 mappingId").toBeTruthy();

  await page.getByRole("button", { name: "生成术语版本" }).click();
  const buildDialog = page.getByRole("dialog", { name: "生成术语资产版本" });
  await expect(buildDialog).toBeVisible({ timeout: 10_000 });
  await buildDialog.getByLabel("稳定术语资产身份").fill(options.assetIdentity);
  await buildDialog.getByLabel("名称").fill(`S2S4 检验术语资产 ${options.suffix}`);
  await selectFirstOption(page, buildDialog, "生效范围");
  const draftResponsePromise = waitForPost(page, "/api/v1/engine/terminology/assets/drafts");
  await buildDialog.getByRole("button", { name: "生成草稿版本" }).click();
  const draftResponse = await draftResponsePromise;
  const draft = await expectBrowserResponseOk<{
    data?: { assetIdentity?: string; versionId?: string; versionNo?: string };
  }>(draftResponse, "前台生成不可变术语资产版本");
  const versionId = draft.data?.versionId;
  const versionNo = draft.data?.versionNo;
  expect(versionId, "术语资产草稿必须返回 versionId").toBeTruthy();
  expect(versionNo, "术语资产草稿必须返回前台可见 versionNo").toBeTruthy();

  return {
    assetType: "TERMINOLOGY",
    assetIdentity: draft.data?.assetIdentity ?? options.assetIdentity,
    versionId: versionId ?? "",
    versionNo: versionNo ?? "",
    standardSystem: options.standardSystem,
    standardCode: options.standardCode,
    localCode: options.localCode,
    sourceSystem: options.sourceSystem,
    category: "LAB",
    mappingId: Number(mappingId),
    standardTermId: confirmed.data?.standardTermId,
  };
}

async function activateRuntimeWithTerminologyAssetFromFrontdesk(
  page: Page,
  options: {
    hospitalId: string;
    hospitalName: string;
    platformBaselineReleaseId: string;
    terminology: TerminologyEvidence;
  },
) {
  const currentBeforeActivation = await readCurrentHospitalRuntime(
    page,
    options.hospitalId,
    "术语资产激活前",
  );
  const requiredPlatformSelections = runtimeAssetSelectionForActivation(
    currentBeforeActivation.items,
  );
  assertRuntimeAssetsContainRequiredPlatformSelections(
    requiredPlatformSelections,
    "术语资产激活前当前机构生效版本",
  );
  await page.goto(appPath("/config/releases"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "机构生效版本" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("tab", { name: "机构生效版本" }).click();
  await chooseHospital(page, options.hospitalName);
  await assertRequiredPlatformRuntimeInputsVisibleAndSelected(page);
  await selectHospitalRuntimeCandidate(page, options.terminology);
  await assessReleaseImpact(page);
  const activateResponsePromise = waitForPost(
    page,
    "/engine/releases/hospitals/",
    "/runtime-releases",
  );
  await page.getByRole("button", { name: "生成新机构生效版本" }).click();
  const activateResponse = await activateResponsePromise;
  const activated = await expectBrowserResponseOk<{
    data?: { releaseId?: string; revisionNo?: number };
  }>(activateResponse, "前台生成包含本轮术语资产的机构生效版本");
  const activationRequest = activateResponse.request().postDataJSON() as {
    platformBaselineReleaseId?: string;
    activeAssets?: RuntimeAsset[];
  };
  expect(
    activationRequest.platformBaselineReleaseId,
    "前台生成机构生效版本必须沿用演练前平台标准版本",
  ).toBe(options.platformBaselineReleaseId);
  expect(
    runtimeAssetListContainsTerminology(activationRequest.activeAssets ?? [], options.terminology),
    "前台生成机构生效版本请求必须携带本轮术语资产",
  ).toBe(true);
  assertRuntimeAssetsContainRequiredPlatformSelections(
    activationRequest.activeAssets ?? [],
    "前台生成机构生效版本请求",
  );
  for (const platformSelection of requiredPlatformSelections) {
    expect(
      runtimeAssetListContainsSelection(activationRequest.activeAssets ?? [], platformSelection),
      `前台生成机构生效版本请求必须沿用平台资产 ${platformSelection.assetType} ${platformSelection.assetIdentity}`,
    ).toBe(true);
  }
  await expect(page.getByText(`当前机构生效版本 第 ${activated.data?.revisionNo} 版`)).toBeVisible({
    timeout: 20_000,
  });
  const current = await readCurrentHospitalRuntime(page, options.hospitalId, "术语资产激活后");
  const releaseId = textField(current.release, "releaseId");
  const revisionNo = Number((current.release as { revisionNo?: unknown }).revisionNo ?? 0);
  const manifestSha256 = textField(current.release, "manifestSha256");
  expect(
    current.items.some(
      (item) =>
        textField(item, "assetType") === options.terminology.assetType &&
        textField(item, "assetIdentity") === options.terminology.assetIdentity &&
        textField(item, "versionId") === options.terminology.versionId &&
        textField(item, "entryState") === "ACTIVE",
    ),
    "当前机构生效版本必须包含本轮术语资产",
  ).toBe(true);
  expect(releaseId, "术语资产激活后必须返回 releaseId").toBeTruthy();
  expect(revisionNo, "术语资产激活后必须返回 revisionNo").toBeGreaterThan(0);
  expect(manifestSha256, "术语资产激活后必须返回 manifestSha256").toMatch(/^[0-9a-f]{64}$/i);
  return {
    runtime: {
      releaseId: releaseId ?? "",
      revisionNo,
      manifestSha256: manifestSha256 ?? "",
      assets: current.items,
    },
    activationRequest,
  };
}

async function postSignedInboundWebhook(options: {
  page: Page;
  webhookId: string;
  webhookSecret: string;
  request: InboundWebhookRequest;
}) {
  const timestamp = currentEpochSeconds();
  const signature = `sha256=${signHmacSha256(
    options.webhookSecret,
    timestamp,
    canonicalInboundWebhookPayload(options.request),
  )}`;
  const response = await postApi(
    options.page,
    `/engine/integration/webhooks/${encodeURIComponent(options.webhookId)}/inbound`,
    options.request,
    {
      "X-MedKernel-Timestamp": timestamp,
      "X-MedKernel-Signature": signature,
    },
  );
  const parsed = await expectApiResponseOk<{ data?: Record<string, unknown> }>(
    response,
    "真实 Webhook 入站",
  );
  return parsed.data ?? {};
}

type InboundWebhookRequest = {
  messageId: string;
  traceId: string;
  adapterId: string;
  sourceSystem: string;
  eventType: string;
  patientId: string;
  encounterId: string;
  clinicalSetting: string;
  triggerPoint: string;
  occurredAt: string;
  payload: Record<string, unknown>;
};

function buildInboundWebhookRequest(options: {
  adapterId: string;
  sourceSystem: string;
  localCode: string;
  suffix: string;
}): InboundWebhookRequest {
  return {
    messageId: `s2s4-msg-${options.suffix}`,
    traceId: `s2s4-trace-${options.suffix}`,
    adapterId: options.adapterId,
    sourceSystem: options.sourceSystem,
    eventType: "REPORT",
    patientId: `S2S4-P-${options.suffix}`,
    encounterId: `S2S4-E-${options.suffix}`,
    clinicalSetting: "OUTPATIENT",
    triggerPoint: "result-review",
    occurredAt: "2026-07-07T00:05:00Z",
    payload: {
      patientId: `S2S4-P-${options.suffix}`,
      labCode: options.localCode,
    },
  };
}

async function expectInboundWebhookRejectedWithInvalidSignature(options: {
  page: Page;
  webhookId: string;
  request: InboundWebhookRequest;
}) {
  const response = await postApi(
    options.page,
    `/engine/integration/webhooks/${encodeURIComponent(options.webhookId)}/inbound`,
    options.request,
    {
      "X-MedKernel-Timestamp": currentEpochSeconds(),
      "X-MedKernel-Signature": "sha256=invalid",
    },
  );
  expect(response.ok(), `坏签名入站必须被拒绝 status=${response.status()}`).toBe(false);
  expect([400, 401, 403], "坏签名入站应返回认证或签名错误").toContain(response.status());
}

async function readRuntimeConsumerContract(page: Page) {
  const response = await getApi(
    page,
    "/engine/integration/knowledge-runtime/runtime-release/current",
  );
  await expectOk(response, "读取第三方运行契约当前机构生效版本");
  return responseData(response);
}

async function readCurrentHospitalRuntime(page: Page, hospitalId: string, label: string) {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(hospitalId)}/runtime-releases/current`,
  );
  await expectOk(response, `${label}读取当前机构生效版本`);
  const data = (await responseData(response)) as { release?: unknown; items?: unknown[] } | null;
  return {
    release: data?.release ?? {},
    items: Array.isArray(data?.items) ? data.items : [],
  };
}

async function chooseHospital(page: Page, hospitalName: string) {
  const combobox = page.getByRole("combobox", { name: "目标医院" });
  await expect(combobox, "机构生效版本页必须提供目标医院选择").toBeVisible({
    timeout: 20_000,
  });
  await combobox.fill(hospitalName);
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, "目标医院下拉必须展示搜索结果").toBeVisible({ timeout: 10_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: hospitalName })
    .first();
  await expect(option, `目标医院下拉必须包含 ${hospitalName}`).toBeVisible({ timeout: 10_000 });
  await clickVisibleAntOption(page, option, `目标医院 ${hospitalName}`);
  await expect(page.getByText(/当前机构生效版本 第 \d+ 版/)).toBeVisible({ timeout: 20_000 });
}

async function assertRequiredPlatformRuntimeInputsVisibleAndSelected(page: Page) {
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
    const row = platformStandardContent
      .getByRole("row")
      .filter({ hasText: required.assetIdentity })
      .first();
    await expect(
      row,
      `平台标准内容必须包含 ${required.assetType} ${required.assetIdentity}`,
    ).toBeVisible({ timeout: 20_000 });
    await expect(row.getByRole("checkbox", { name: /启用平台/ })).toBeChecked();
  }
}

async function selectHospitalRuntimeCandidate(page: Page, terminology: TerminologyEvidence) {
  const localContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("集团与本院内容", { exact: true }) })
    .first();
  await expect(localContentCard, "机构生效版本页必须展示集团与本院内容清单").toBeVisible({
    timeout: 20_000,
  });
  const candidateRow = localContentCard
    .getByRole("row")
    .filter({ hasText: terminology.assetIdentity })
    .filter({ hasText: "本院 · 术语字典内容" })
    .first();
  await expect(
    candidateRow,
    `集团与本院内容必须展示本轮术语资产 ${terminology.assetIdentity}`,
  ).toBeVisible({ timeout: 20_000 });
  const enableCheckbox = candidateRow.getByRole("checkbox", {
    name: /启用本院术语字典内容/u,
  });
  await expect(enableCheckbox, "本轮术语资产必须可勾选进入机构生效版本").toBeVisible();
  if (!(await enableCheckbox.isChecked())) {
    await enableCheckbox.check();
  }
  await expect(enableCheckbox, "本轮术语资产必须已选入机构生效版本").toBeChecked();
  await expect(candidateRow, "本轮术语资产行必须展示版本").toContainText(terminology.versionNo);
}

async function assessReleaseImpact(page: Page) {
  const impactButton = page.getByRole("button", { name: "评估发布影响" });
  await expect(impactButton.first(), "选择本轮术语资产后必须先评估发布影响").toBeVisible({
    timeout: 20_000,
  });
  const simulationResponsePromise = waitForPost(page, "/engine/versioning/releases/simulations");
  await impactButton.first().click();
  const simulationResponse = await simulationResponsePromise;
  const simulation = await expectBrowserResponseOk<{ data?: { releasable?: boolean } }>(
    simulationResponse,
    "前台评估本轮术语资产发布影响",
  );
  expect(simulation.data?.releasable, "本轮术语资产发布影响评估必须允许生成机构生效版本").toBe(
    true,
  );
  await page.waitForLoadState("networkidle");
  await expect(page.getByText("发布影响评估未完成")).toHaveCount(0);
  await expect(page.getByText("需处理")).toHaveCount(0);
}

function runtimeAssetListContainsTerminology(
  assets: RuntimeAsset[],
  terminology: TerminologyEvidence,
) {
  return runtimeAssetListContainsSelection(assets, terminology);
}

function runtimeAssetListContainsSelection(assets: RuntimeAsset[], selection: RuntimeAsset) {
  return assets.some(
    (item) =>
      item.assetType === selection.assetType &&
      item.assetIdentity === selection.assetIdentity &&
      item.versionId === selection.versionId,
  );
}

function runtimeAssetSelectionForActivation(items: unknown[]): RuntimeAsset[] {
  const uniqueSelections = new Map<string, RuntimeAsset>();
  for (const item of items) {
    if (textField(item, "entryState") !== "ACTIVE") continue;
    if (textField(item, "sourceLayer") === "PLATFORM") {
      const assetType = textField(item, "assetType");
      const assetIdentity = textField(item, "assetIdentity");
      if (assetType && assetIdentity) {
        uniqueSelections.set(`${assetType}|${assetIdentity}`, {
          assetType,
          assetIdentity,
          versionId: null,
        });
      }
    }
  }
  return Array.from(uniqueSelections.values());
}

function assertRuntimeAssetsContainRequiredPlatformSelections(
  assets: RuntimeAsset[],
  label: string,
) {
  for (const required of requiredRuntimeAssetsForRehearsal) {
    const match = assets.find(
      (item) =>
        item.assetType === required.assetType &&
        item.assetIdentity === required.assetIdentity &&
        item.versionId === null,
    );
    expect(
      match,
      `${label} 必须包含平台沿用资产 ${required.assetType} ${required.assetIdentity}`,
    ).toBeTruthy();
  }
}

async function resolveHospitalId(page: Page, hospitalName: string) {
  const response = await getApi(
    page,
    `/engine/org/org-units?keyword=${encodeURIComponent(hospitalName)}&page=1&size=20`,
  );
  await expectOk(response, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(response)).find(
    (item) =>
      textField(item, "name") === hospitalName &&
      textField(item, "level") === "FACILITY" &&
      textField(item, "id"),
  );
  const hospitalId = textField(hospital, "id");
  expect(hospitalId, "必须解析本地上线演练医院 ID").toBeTruthy();
  return hospitalId ?? "";
}

async function waitForTerminologyCandidate(
  page: Page,
  options: { jobCode: string; localCode: string },
) {
  const deadline = Date.now() + 20_000;
  let lastStatus = "PENDING";
  let generatedCount = 0;
  const canonicalLocalCode = canonicalTerminologyAlias(options.localCode);
  while (Date.now() < deadline) {
    const job = await getApi(
      page,
      `/engine/terminology/mappings/candidate-generation-jobs/${encodeURIComponent(
        options.jobCode,
      )}`,
    );
    await expectOk(job, "读取 S2/S4 术语候选任务");
    const jobData = await responseData(job);
    lastStatus = textField(jobData, "status") ?? lastStatus;
    generatedCount = Number(
      (jobData as { generatedCount?: unknown })?.generatedCount ?? generatedCount,
    );
    const candidates = await getApi(
      page,
      `/engine/terminology/mappings/candidates?status=PENDING&generationJobCode=${encodeURIComponent(
        options.jobCode,
      )}&page=1&size=20`,
    );
    await expectOk(candidates, "读取 S2/S4 术语候选");
    const candidate = pageItems(await responseData(candidates)).find(
      (item) =>
        textField(item, "status") === "PENDING" &&
        textField(item, "generationJobCode") === options.jobCode &&
        (textField(item, "evidenceText") ?? "").includes(canonicalLocalCode),
    );
    if (candidate && typeof (candidate as { id?: unknown }).id === "number") {
      return candidate as { id: number };
    }
    await waitForPollingInterval(250);
  }
  throw new Error(
    `S2/S4 术语候选生成超时，最后任务状态：${lastStatus}，生成数量：${generatedCount}，院内码别名：${canonicalLocalCode}`,
  );
}

function assertRuntimeContractMatchesTerminology(
  readback: unknown,
  runtime: { releaseId: string; revisionNo: number; manifestSha256: string },
  terminology: TerminologyEvidence,
) {
  expect(textField(readback, "releaseId"), "第三方运行契约必须读取同一 releaseId").toBe(
    runtime.releaseId,
  );
  expect(Number((readback as { revisionNo?: unknown })?.revisionNo ?? 0)).toBe(runtime.revisionNo);
  expect(textField(readback, "manifestSha256")).toBe(runtime.manifestSha256);
  const assets = pageItems(readback);
  const directAssets = Array.isArray((readback as { assets?: unknown })?.assets)
    ? (readback as { assets: unknown[] }).assets
    : [];
  const runtimeAssets = assets.length > 0 ? assets : directAssets;
  expect(
    runtimeAssets.some(
      (item) =>
        textField(item, "assetType") === terminology.assetType &&
        textField(item, "assetIdentity") === terminology.assetIdentity &&
        textField(item, "versionId") === terminology.versionId &&
        textField(item, "entryState") === "ACTIVE",
    ),
    "第三方运行契约必须返回本轮术语资产",
  ).toBe(true);
}

async function attachS2S4CoverageEvidence(
  testInfo: TestInfo,
  input: {
    observedStages: Set<string>;
    adapter: Record<string, unknown>;
    terminology: TerminologyEvidence;
    runtime: { releaseId: string; revisionNo: number; manifestSha256: string; assets: unknown[] };
    activationRequest: unknown;
    inboundResult: Record<string, unknown>;
    runtimeConsumerReadback: unknown;
    invalidMasterDataSignatureRejected: boolean;
    invalidInboundWebhookSignatureRejected: boolean;
  },
) {
  const scenarioEvidence = requiredS2S4ScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => input.observedStages.has(stage)),
  }));
  const scenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const required = requiredS2S4ScenarioEvidence.find((item) => item.code === scenario.code);
      return required?.observedStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  const scenarioConditionEvidence = [
    ...(scenarioCodes.includes("S2")
      ? [
          {
            code: "S2__NORMAL",
            scenarioCode: "S2",
            condition: "NORMAL",
            source: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
            evidence: [
              "平台管理员前台创建 LIS Webhook 适配器并配置字段映射",
              "真实 Webhook 入站通过验签并生成标准临床事件",
              "入站字段映射按当前机构生效版本完成术语归一",
            ],
          },
        ]
      : []),
    ...(input.invalidInboundWebhookSignatureRejected
      ? [
          {
            code: "S2__ABNORMAL",
            scenarioCode: "S2",
            condition: "ABNORMAL",
            source: "INVALID_INBOUND_WEBHOOK_SIGNATURE_REJECTED",
            evidence: ["非法入站 Webhook 签名被拒绝"],
          },
        ]
      : []),
    ...(scenarioCodes.includes("S4")
      ? [
          {
            code: "S4__NORMAL",
            scenarioCode: "S4",
            condition: "NORMAL",
            source: "TERMINOLOGY_RUNTIME_CONTRACT",
            evidence: [
              "前台登记标准术语",
              "签名主数据同步登记院内术语",
              "前台生成并确认术语映射候选",
              "当前机构生效版本和第三方运行契约读回同一术语资产",
            ],
          },
        ]
      : []),
    ...(input.invalidMasterDataSignatureRejected
      ? [
          {
            code: "S4__ABNORMAL",
            scenarioCode: "S4",
            condition: "ABNORMAL",
            source: "INVALID_MASTER_DATA_SIGNATURE_REJECTED",
            evidence: ["非法主数据同步签名被拒绝"],
          },
        ]
      : []),
  ];
  await testInfo.attach("s2-s4-runtime-mapping-codes", {
    contentType: "application/json",
    body: JSON.stringify(
      {
        scenarioCodes,
        productLayers: ["DATA_INTEROPERABILITY", "MEDICAL_ASSET"],
        versionedAssets: ["TERMINOLOGY"],
        deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
        serviceCombinations: ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME"],
        apiEvidence: {
          adapterCreatedFromFrontdesk: true,
          fieldMappingConfigured: true,
          webhookCreatedFromFrontdesk: true,
          standardTermRegisteredFromFrontdesk: true,
          localTermRegisteredThroughSignedSync: true,
          candidateGeneratedFromFrontdesk: true,
          candidateConfirmedFromFrontdesk: true,
          terminologyAssetDraftCreatedFromFrontdesk: true,
          runtimeReleaseActivatedWithTerminologyAsset: true,
          invalidMasterDataSignatureRejected: input.invalidMasterDataSignatureRejected,
          invalidInboundWebhookSignatureRejected: input.invalidInboundWebhookSignatureRejected,
          inboundWebhookAccepted: true,
          inboundNormalizedByRuntimeRelease: true,
          runtimeContractReadbackMatched: true,
        },
        adapter: input.adapter,
        terminology: input.terminology,
        runtime: {
          releaseId: input.runtime.releaseId,
          revisionNo: input.runtime.revisionNo,
          manifestSha256: input.runtime.manifestSha256,
          assets: input.runtime.assets,
        },
        activationRequest: input.activationRequest,
        inboundResult: input.inboundResult,
        runtimeConsumerReadback: input.runtimeConsumerReadback,
        dedicatedReleaseContractEvidence: {
          assetType: input.terminology.assetType,
          assetIdentity: input.terminology.assetIdentity,
          versionId: input.terminology.versionId,
          productionRoute: "STANDARD_AND_LOCAL_TERMINOLOGY_MAPPING",
          releaseContract: "S2_S4_TERMINOLOGY_MAPPING_RUNTIME_CONTRACT",
          producerVerified: true,
          reviewerVerified: true,
          activationVerified: true,
          runtimeConsumerReadbackVerified: true,
          inboundNormalizationVerified: true,
          sourceSystems: [input.terminology.sourceSystem],
          consumer: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
        },
        scenarioEvidence,
        scenarioConditionEvidence,
      },
      null,
      2,
    ),
  });
}

function findNormalizedCode(value: unknown): Record<string, unknown> | null {
  const queue = [value];
  while (queue.length > 0) {
    const current = queue.shift();
    if (!current || typeof current !== "object") continue;
    if (Array.isArray(current)) {
      queue.push(...current);
      continue;
    }
    const record = current as Record<string, unknown>;
    if (
      typeof record.standardCode === "string" &&
      typeof record.codeSystem === "string" &&
      typeof record.localCode === "string" &&
      typeof record.runtimeReleaseId === "string"
    ) {
      return record;
    }
    queue.push(...Object.values(record));
  }
  return null;
}

function currentEpochSeconds() {
  return Math.floor(Date.now() / 1000).toString();
}

function canonicalTerminologyAlias(value: string) {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fff]+/g, "");
}

function signHmacSha256(secret: string, timestamp: string, payload: unknown) {
  return createHmac("sha256", secret)
    .update(`${timestamp}.${typeof payload === "string" ? payload : JSON.stringify(payload)}`)
    .digest("hex");
}

function canonicalInboundWebhookPayload(request: {
  messageId: string;
  traceId: string;
  adapterId: string;
  sourceSystem: string;
  eventType: string;
  patientId: string;
  encounterId: string;
  clinicalSetting: string;
  triggerPoint: string;
  occurredAt: string;
  payload: Record<string, unknown>;
}) {
  return JSON.stringify({
    messageId: request.messageId,
    traceId: request.traceId,
    adapterId: request.adapterId,
    sourceSystem: request.sourceSystem,
    eventType: request.eventType,
    patientId: request.patientId,
    encounterId: request.encounterId,
    clinicalSetting: request.clinicalSetting,
    triggerPoint: request.triggerPoint,
    occurredAt: request.occurredAt,
    payload: request.payload,
  });
}

async function waitForPost(page: Page, path: string, secondPath?: string) {
  return page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes(path) &&
      (!secondPath || response.url().includes(secondPath)),
    { timeout: 60_000 },
  );
}

async function expectBrowserResponseOk<T = unknown>(response: Response, label: string): Promise<T> {
  const body = await response.text();
  expect(response.ok(), `${label} 应返回成功 status=${response.status()} body=${body}`).toBe(true);
  return JSON.parse(body) as T;
}

async function expectApiResponseOk<T = unknown>(response: APIResponse, label: string): Promise<T> {
  const body = await response.text();
  expect(response.ok(), `${label} 应返回成功 status=${response.status()} body=${body}`).toBe(true);
  return JSON.parse(body) as T;
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  await selectAntOption(page, dialog, label, optionText);
}

async function selectAntOption(page: Page, scope: Locator, fieldLabel: string, optionText: string) {
  const combobox = scope.getByRole("combobox", { name: fieldLabel }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await selectAntOptionFromSelect(page, select, optionText, `${fieldLabel} 下拉`);
}

async function selectAntOptionFromSelect(
  page: Page,
  select: Locator,
  optionText: string,
  label = "下拉",
) {
  if (await selectedAntOptionMatches(select, optionText)) {
    await expectSelectedAntOption(select, optionText, label);
    return;
  }
  await select.click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: optionText })
    .first();
  await expect(option, `${label}应存在 ${optionText}`).toBeVisible({ timeout: 10_000 });
  await clickVisibleAntOption(page, option, `${label} ${optionText}`);
  await expectSelectedAntOption(select, optionText, label);
}

async function selectedAntOptionMatches(select: Locator, optionText: string) {
  const selected = select.locator(".ant-select-selection-item").first();
  try {
    const text = await selected.textContent({ timeout: 1_000 });
    return text?.includes(optionText) ?? false;
  } catch {
    return false;
  }
}

async function selectFirstOption(page: Page, scope: Locator, fieldLabel: string) {
  const combobox = scope.getByRole("combobox", { name: fieldLabel }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  await select.click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .first();
  await expect(option, `${fieldLabel} 下拉应至少存在一个选项`).toBeVisible({ timeout: 10_000 });
  await clickVisibleAntOption(page, option, `${fieldLabel} 首个选项`);
}

async function chooseFieldMappingCategory(
  page: Page,
  dialog: Locator,
  mappingIndex: number,
  optionText: string,
) {
  const categorySelect = dialog
    .getByLabel("术语分类")
    .nth(mappingIndex)
    .locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
    );
  await selectAntOptionFromSelect(
    page,
    categorySelect,
    optionText,
    `第 ${mappingIndex + 1} 条术语分类`,
  );
}

async function clickVisibleAntOption(page: Page, option: Locator, label: string) {
  await option.scrollIntoViewIfNeeded();
  try {
    await option.click({ timeout: 2_000 });
    return;
  } catch {
    // AntD 的浮层选项在动画/虚拟列表刷新时可能长期不满足 stable，继续使用真实可见坐标点击。
  }
  try {
    await option.click({ force: true, timeout: 2_000 });
    return;
  } catch {
    // 仍未选中时再触发同一个可见选项的标准鼠标事件，避免误点到旧选项。
  }
  const box = await option.boundingBox();
  expect(box, `${label} 必须有可点击位置`).toBeTruthy();
  await page.mouse.click(box!.x + box!.width / 2, box!.y + box!.height / 2);
}

async function expectSelectedAntOption(select: Locator, optionText: string, label: string) {
  await expect(
    select.locator(".ant-select-selection-item").first(),
    `${label} 必须实际选中 ${optionText}`,
  ).toContainText(optionText, { timeout: 5_000 });
}

async function readRequestJson(request: Request) {
  const postData = request.postData();
  expect(postData, "前台请求必须携带 JSON 请求体").toBeTruthy();
  return JSON.parse(postData ?? "{}") as Record<string, unknown>;
}

async function postExternalSignedApi(path: string, data: unknown, headers: Record<string, string>) {
  const context = await playwrightRequest.newContext();
  try {
    const response = await context.post(`${apiBase}${path}`, {
      data,
      headers: {
        "Content-Type": "application/json",
        ...headers,
      },
    });
    return {
      ok: response.ok(),
      status: response.status(),
      body: await response.text(),
    };
  } finally {
    await context.dispose();
  }
}

async function expectWebhookVisibleInFrontdesk(page: Page, webhookName: string) {
  await expect(page.getByRole("row").filter({ hasText: webhookName }).first()).toBeVisible({
    timeout: 20_000,
  });
}

async function setEvidenceDetails(page: Page, enabled: boolean) {
  await page.evaluate((next) => {
    window.localStorage.setItem("medkernel.evidence-details.enabled", String(next));
  }, enabled);
}
