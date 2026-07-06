import {
  expect,
  test,
  type APIResponse,
  type Locator,
  type Page,
  type TestInfo,
} from "@playwright/test";
import { writeFile } from "node:fs/promises";

import {
  apiBase,
  ensureReadySession,
  expectOk,
  patchApi,
  postApi,
  requiredRuntimeAssetsForRehearsal,
  resolvedTenantIdFor,
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
  release?: {
    releaseId?: string;
    revisionNo?: number;
    manifestSha256?: string;
  };
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
  unselectedLocalCandidate?: RuntimeReleaseLocalCandidate;
  activationRequest?: { activeAssets: RuntimeReleaseAssetEvidence[] };
  activationReadback?: { assets: RuntimeReleaseAssetEvidence[] };
  runtimeConsumerReadback?: { assets: RuntimeReleaseAssetEvidence[] };
  partialSelection?: {
    selectedCandidate: RuntimeReleaseLocalCandidate;
    unselectedCandidate: RuntimeReleaseLocalCandidate;
    activationRequestOmitsUnselected: boolean;
    activationReadbackOmitsUnselected: boolean;
    runtimeConsumerOmitsUnselected: boolean;
  };
  multiHospitalDifferentiation?: {
    primaryHospital: RuntimeReleaseHospitalDifferentiationEvidence;
    secondaryHospital: RuntimeReleaseHospitalDifferentiationEvidence;
    distinctHospitals: boolean;
    distinctSelectedCandidates: boolean;
    backendReadbacksIsolated: boolean;
    runtimeConsumerReadbacksIsolated: boolean;
  };
  offlineDelivery?: RuntimeReleaseOfflineDeliveryEvidence;
  rollbackReadback?: { localCandidateAbsent: boolean; assets: RuntimeReleaseAssetEvidence[] };
  rollbackRuntimeConsumerReadback?: {
    localCandidateAbsent: boolean;
    assets: RuntimeReleaseAssetEvidence[];
  };
  scenarioEvidence: Array<{ observedStages: string[] }>;
};
type RuntimeReleaseOfflineDeliveryEvidence = {
  delivery: {
    deliveryKind: string;
    evidenceId: string;
    fileUri: string;
    fileDigest: string;
    signatureAlgorithm: string;
    runtimeMutation: boolean;
    releaseId: string;
    hospitalId: string;
    itemCount: number;
  };
  downloadedFile: {
    fileUri: string;
    containsDeliveryKind: boolean;
    containsRuntimeMutationFalse: boolean;
    containsReleaseId: boolean;
  };
  importPreview: {
    status: string;
    signatureValid: boolean;
    manifestMatched: boolean;
    runtimeMutation: boolean;
    releaseId: string;
    hospitalId: string;
    itemCount: number;
  };
  runtimeBefore: RuntimeReleaseSnapshotIdentity;
  runtimeAfter: RuntimeReleaseSnapshotIdentity;
};
type RuntimeReleaseSnapshotIdentity = {
  releaseId: string;
  revisionNo: number;
  manifestSha256: string;
};
type RuntimeReleaseHospitalDifferentiationEvidence = {
  hospitalId: string;
  hospitalName: string;
  selectedCandidate: RuntimeReleaseLocalCandidate;
  activationReadback: { assets: RuntimeReleaseAssetEvidence[] };
  runtimeConsumerReadback: { assets: RuntimeReleaseAssetEvidence[] };
  excludesOtherHospitalCandidate: boolean;
};
type RuntimeReleaseSecondHospitalContext = {
  hospitalId: string;
  hospitalName: string;
  account: {
    userId: string;
    username: string;
    tenantId: string;
    initialPassword: string;
    finalPassword: string;
  };
};

const primaryHospitalName = "本地上线演练医院";
const secondHospitalCode = "e2e-rehearsal-hospital-b";
const secondHospitalName = "本地上线演练二院";
const secondHospitalEngineOperator = {
  userId: "e2e-runtime-second-engine-operator",
  username: "e2e-runtime-second-engine-operator",
  initialPassword: "Mk@2026RuntimeSecondInit!",
  finalPassword: "Mk@2026RuntimeSecondFinal!",
};

test.describe.configure({ mode: "serial" });

test.describe("机构生效版本真实前台发布回滚", () => {
  test("医疗引擎运营员可为本院生成新生效版本并从历史版本回滚", async ({
    browser,
    page,
  }, testInfo) => {
    test.setTimeout(420_000);
    const adminContext = await browser.newContext();
    const secondHospitalContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const secondHospitalPage = await secondHospitalContext.newPage();
    const runtime = collectRuntime(page);
    const records: RuntimeRecord[] = [];
    const coverageEvidence = createRuntimeReleaseCoverageEvidence();

    try {
      await ensureReadySession(adminPage, "platform-admin");
      const secondHospital = await ensureSecondHospitalRuntimeReleaseRehearsalContext(adminPage);
      await loginSecondHospitalRuntimeAccount(secondHospitalPage, secondHospital);
      const secondaryCandidate = await createHospitalRuntimeReleaseCandidate(
        secondHospitalPage,
        testInfo,
        { purpose: "secondary" },
      );

      await ensureReadySession(page, "engine-operator");
      const localCandidate = await createHospitalRuntimeReleaseCandidate(page, testInfo);
      const unselectedLocalCandidate = await createHospitalRuntimeReleaseCandidate(page, testInfo, {
        purpose: "unselected",
      });
      clearRuntime(runtime);
      await page.goto("/config/releases", { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "机构生效版本" })).toBeVisible();
      await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
      await expect(page.getByText("平台标准版本由平台治理入口发布")).toBeVisible();
      await page.getByRole("tab", { name: "机构生效版本" }).click();
      await chooseHospital(page, primaryHospitalName);
      await expect(page.getByText(/当前机构生效版本 第 \d+ 版/)).toBeVisible({
        timeout: 20_000,
      });
      await expectNoRootOverflow(page, "机构生效版本初始桌面");
      const initialRevision = await currentHospitalRevision(page);
      const primaryHospitalId = await resolveHospitalId(page, primaryHospitalName);
      recordCleanRuntime(page, "选择本地上线演练医院", runtime, records);
      await assertRequiredRuntimeInputsVisibleAndSelected(page);
      recordRuntimeReleaseStage(coverageEvidence, "前台展示并勾选 13 类平台标准资产");
      await selectHospitalLocalRuntimeCandidate(page, localCandidate);
      await assertHospitalLocalRuntimeCandidateVisibleAndUnselected(page, unselectedLocalCandidate);
      coverageEvidence.unselectedLocalCandidate = unselectedLocalCandidate;
      recordRuntimeReleaseStage(
        coverageEvidence,
        "前台只选择本轮部分本院内容进入机构生效版本",
      );

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
      const activationRequestOmitsUnselected = assertRuntimeAssetsExcludeUnselectedCandidate(
        activationRequestAssets,
        unselectedLocalCandidate,
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
        primaryHospitalName,
        activated.data?.revisionNo,
        "前台生成新机构生效版本",
      );
      const activationReadbackCandidate = assertRuntimeAssetsContainLocalCandidate(
        activatedRuntime.items ?? [],
        localCandidate,
        "前台生成新机构生效版本后端读回",
        { requireActive: true },
      );
      const activationReadbackOmitsUnselected = assertRuntimeAssetsExcludeUnselectedCandidate(
        activatedRuntime.items ?? [],
        unselectedLocalCandidate,
        "前台生成新机构生效版本后端读回",
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
      const runtimeConsumerOmitsUnselected = assertRuntimeAssetsExcludeUnselectedCandidate(
        activatedConsumer.assets ?? [],
        unselectedLocalCandidate,
        "前台生成新机构生效版本第三方运行契约",
      );
      coverageEvidence.runtimeConsumerReadback = { assets: [activationConsumerCandidate] };
      coverageEvidence.apiEvidence.runtimeConsumerReadback = true;
      coverageEvidence.partialSelection = {
        selectedCandidate: localCandidate,
        unselectedCandidate: unselectedLocalCandidate,
        activationRequestOmitsUnselected,
        activationReadbackOmitsUnselected,
        runtimeConsumerOmitsUnselected,
      };
      coverageEvidence.apiEvidence.partialSelectionProved =
        activationRequestOmitsUnselected &&
        activationReadbackOmitsUnselected &&
        runtimeConsumerOmitsUnselected;
      coverageEvidence.activatedRevisionNo = activated.data?.revisionNo;
      recordRuntimeReleaseStage(coverageEvidence, "第三方运行契约读取同一机构生效版本");
      recordCleanRuntime(page, "前台生成新机构生效版本", runtime, records);

      clearRuntime(runtime);
      await chooseHospital(page, secondHospital.hospitalName);
      await assertRequiredRuntimeInputsVisibleAndSelected(page);
      await selectHospitalLocalRuntimeCandidate(page, secondaryCandidate);
      await assessLocalReleaseImpact(page);
      const secondaryActivateResponsePromise = waitForPost(
        page,
        "/engine/releases/hospitals/",
        "/runtime-releases",
      );
      await page.getByRole("button", { name: "生成新机构生效版本" }).click();
      const secondaryActivateResponse = await secondaryActivateResponsePromise;
      const secondaryActivateBody = await secondaryActivateResponse.text();
      expect(
        secondaryActivateResponse.ok(),
        `前台为第二家医院生成机构生效版本应返回成功 status=${secondaryActivateResponse.status()} body=${secondaryActivateBody}`,
      ).toBe(true);
      const secondaryActivationRequest =
        secondaryActivateResponse.request().postDataJSON();
      const secondaryActivationRequestAssets =
        assertRuntimeReleaseRequestCarriesRequiredAssets(
          secondaryActivationRequest,
          "前台为第二家医院生成机构生效版本",
        );
      assertRuntimeAssetsContainLocalCandidate(
        secondaryActivationRequestAssets,
        secondaryCandidate,
        "前台为第二家医院生成机构生效版本请求",
      );
      assertRuntimeAssetsExcludeUnselectedCandidate(
        secondaryActivationRequestAssets,
        localCandidate,
        "前台为第二家医院生成机构生效版本请求",
      );
      const secondaryActivated = JSON.parse(secondaryActivateBody) as {
        data?: { revisionNo?: number };
      };
      await expect(
        page.getByText(`当前机构生效版本 第 ${secondaryActivated.data?.revisionNo} 版`),
      ).toBeVisible({ timeout: 20_000 });
      const secondaryRuntime = await assertCurrentRuntimeAssetsReady(
        page,
        secondHospital.hospitalName,
        secondaryActivated.data?.revisionNo,
        "前台为第二家医院生成机构生效版本",
      );
      const secondaryReadbackCandidate = assertRuntimeAssetsContainLocalCandidate(
        secondaryRuntime.items ?? [],
        secondaryCandidate,
        "第二家医院后端当前机构生效版本读回",
        { requireActive: true },
      );
      const secondaryBackendExcludesPrimary = assertRuntimeAssetsExcludeUnselectedCandidate(
        secondaryRuntime.items ?? [],
        localCandidate,
        "第二家医院后端当前机构生效版本读回",
      );
      const secondaryConsumer = await readThirdPartyRuntimeConsumerForRole(
        secondHospitalPage,
        secondHospital,
        secondaryActivated.data?.revisionNo,
        "第二家医院第三方运行契约",
      );
      const secondaryConsumerCandidate = assertRuntimeAssetsContainLocalCandidate(
        secondaryConsumer.assets ?? [],
        secondaryCandidate,
        "第二家医院第三方运行契约",
        { requireActive: true },
      );
      const secondaryConsumerExcludesPrimary = assertRuntimeAssetsExcludeUnselectedCandidate(
        secondaryConsumer.assets ?? [],
        localCandidate,
        "第二家医院第三方运行契约",
      );
      const primaryRuntimeAfterSecondary = await assertCurrentRuntimeAssetsReady(
        page,
        primaryHospitalName,
        activated.data?.revisionNo,
        "第二家医院发布后第一家医院当前机构生效版本读回",
      );
      const primaryReadbackCandidateAfterSecondary = assertRuntimeAssetsContainLocalCandidate(
        primaryRuntimeAfterSecondary.items ?? [],
        localCandidate,
        "第二家医院发布后第一家医院当前机构生效版本读回",
        { requireActive: true },
      );
      const primaryBackendExcludesSecondary = assertRuntimeAssetsExcludeUnselectedCandidate(
        primaryRuntimeAfterSecondary.items ?? [],
        secondaryCandidate,
        "第二家医院发布后第一家医院当前机构生效版本读回",
      );
      const primaryConsumerAfterSecondary =
        await assertThirdPartyRuntimeConsumerCarriesRequiredAssets(
          page,
          activated.data?.revisionNo,
          "第二家医院发布后第一家医院第三方运行契约",
        );
      const primaryConsumerCandidateAfterSecondary = assertRuntimeAssetsContainLocalCandidate(
        primaryConsumerAfterSecondary.assets ?? [],
        localCandidate,
        "第二家医院发布后第一家医院第三方运行契约",
        { requireActive: true },
      );
      const primaryConsumerExcludesSecondary = assertRuntimeAssetsExcludeUnselectedCandidate(
        primaryConsumerAfterSecondary.assets ?? [],
        secondaryCandidate,
        "第二家医院发布后第一家医院第三方运行契约",
      );
      coverageEvidence.multiHospitalDifferentiation = {
        primaryHospital: {
          hospitalId: primaryHospitalId,
          hospitalName: primaryHospitalName,
          selectedCandidate: localCandidate,
          activationReadback: {
            assets: (primaryRuntimeAfterSecondary.items ?? []).map((asset) =>
              toRuntimeReleaseAssetEvidence(asset),
            ),
          },
          runtimeConsumerReadback: {
            assets: (primaryConsumerAfterSecondary.assets ?? []).map((asset) =>
              toRuntimeReleaseAssetEvidence(asset),
            ),
          },
          excludesOtherHospitalCandidate:
            primaryBackendExcludesSecondary && primaryConsumerExcludesSecondary,
        },
        secondaryHospital: {
          hospitalId: secondHospital.hospitalId,
          hospitalName: secondHospital.hospitalName,
          selectedCandidate: secondaryCandidate,
          activationReadback: {
            assets: (secondaryRuntime.items ?? []).map((asset) =>
              toRuntimeReleaseAssetEvidence(asset),
            ),
          },
          runtimeConsumerReadback: {
            assets: (secondaryConsumer.assets ?? []).map((asset) =>
              toRuntimeReleaseAssetEvidence(asset),
            ),
          },
          excludesOtherHospitalCandidate:
            secondaryBackendExcludesPrimary && secondaryConsumerExcludesPrimary,
        },
        distinctHospitals: primaryHospitalId !== secondHospital.hospitalId,
        distinctSelectedCandidates: !sameRuntimeReleaseCandidate(
          localCandidate,
          secondaryCandidate,
        ),
        backendReadbacksIsolated:
          primaryBackendExcludesSecondary &&
          secondaryBackendExcludesPrimary &&
          Boolean(primaryReadbackCandidateAfterSecondary.versionId) &&
          Boolean(secondaryReadbackCandidate.versionId),
        runtimeConsumerReadbacksIsolated:
          primaryConsumerExcludesSecondary &&
          secondaryConsumerExcludesPrimary &&
          Boolean(primaryConsumerCandidateAfterSecondary.versionId) &&
          Boolean(secondaryConsumerCandidate.versionId),
      };
      recordRuntimeReleaseStage(
        coverageEvidence,
        "前台为第二家医院选择不同本院内容生成机构生效版本",
      );
      recordRuntimeReleaseStage(
        coverageEvidence,
        "两家医院后端与第三方运行契约读回互不串用",
      );
      recordCleanRuntime(page, "前台为第二家医院生成差异化机构生效版本", runtime, records);

      clearRuntime(runtime);
      await chooseHospital(page, primaryHospitalName);
      await expect(
        page.getByText(`当前机构生效版本 第 ${activated.data?.revisionNo} 版`),
      ).toBeVisible({ timeout: 20_000 });
      const offlineDelivery = await exerciseOfflineDelivery(
        page,
        primaryHospitalName,
        primaryHospitalId,
        activated.data?.revisionNo,
      );
      coverageEvidence.offlineDelivery = offlineDelivery;
      coverageEvidence.apiEvidence.offlineDeliveryExported = true;
      coverageEvidence.apiEvidence.offlineDeliveryFileDownloaded = true;
      coverageEvidence.apiEvidence.offlineDeliveryImportPreviewValidated = true;
      coverageEvidence.apiEvidence.offlineDeliveryRuntimeUnchanged =
        offlineDelivery.runtimeBefore.releaseId === offlineDelivery.runtimeAfter.releaseId &&
        offlineDelivery.runtimeBefore.revisionNo === offlineDelivery.runtimeAfter.revisionNo &&
        offlineDelivery.runtimeBefore.manifestSha256 === offlineDelivery.runtimeAfter.manifestSha256;
      recordRuntimeReleaseStage(
        coverageEvidence,
        "前台导出机构生效版本离线交付文件",
      );
      recordRuntimeReleaseStage(coverageEvidence, "下载离线交付文件并校验完整快照");
      recordRuntimeReleaseStage(
        coverageEvidence,
        "离线交付导入预检验签且不改写当前机构生效版本",
      );
      recordCleanRuntime(page, "前台导出并预检机构生效版本离线交付文件", runtime, records);

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
        primaryHospitalName,
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
      await Promise.allSettled([adminContext.close(), secondHospitalContext.close()]);
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

async function ensureSecondHospitalRuntimeReleaseRehearsalContext(
  page: Page,
): Promise<RuntimeReleaseSecondHospitalContext> {
  const tenantId = resolvedTenantIdFor("engine-operator");
  const hospital = await ensureSecondHospital(page);
  await ensureSecondHospitalRuntimeAccount(page, hospital.id);
  return {
    hospitalId: hospital.id,
    hospitalName: secondHospitalName,
    account: {
      userId: secondHospitalEngineOperator.userId,
      username: secondHospitalEngineOperator.username,
      tenantId,
      initialPassword: secondHospitalEngineOperator.initialPassword,
      finalPassword: secondHospitalEngineOperator.finalPassword,
    },
  };
}

async function ensureSecondHospital(page: Page) {
  const existing = await apiGet(page, `/engine/org/org-units/${encodeURIComponent(secondHospitalCode)}`);
  if (existing.ok()) {
    return requireOrgUnit(await responseData(existing), "读取第二家上线演练医院");
  }
  if (existing.status() !== 404) {
    await expectOk(existing, "读取第二家上线演练医院");
  }
  const rootsResponse = await apiGet(page, "/engine/org/org-units/by-level?level=TENANT");
  await expectOk(rootsResponse, "读取上线演练租户根组织");
  const root = arrayData(await responseData(rootsResponse)).find(
    (item) =>
      textField(item, "tenantId") === resolvedTenantIdFor("engine-operator") ||
      textField(item, "code") === resolvedTenantIdFor("engine-operator"),
  );
  const rootId = textField(root, "id");
  expect(rootId, "上线演练租户必须存在根组织用于创建第二家医院").toBeTruthy();
  const created = await postApi(page, "/engine/org/org-units", {
    parentId: rootId,
    level: "FACILITY",
    code: secondHospitalCode,
    name: secondHospitalName,
    namePinyin: "bendi shangxian yanlian eryuan",
    facilityType: "HOSPITAL",
    status: "ACTIVE",
  });
  await expectOk(created, "创建第二家上线演练医院");
  return requireOrgUnit(await responseData(created), "创建第二家上线演练医院");
}

async function ensureSecondHospitalRuntimeAccount(page: Page, hospitalId: string) {
  const detail = await apiGet(
    page,
    `/compliance/users/${encodeURIComponent(secondHospitalEngineOperator.userId)}`,
  );
  if (detail.status() === 404) {
    const created = await postApi(page, "/compliance/users", {
      credentialManaged: true,
      userId: secondHospitalEngineOperator.userId,
      displayName: "第二医院机构版本演练运营员",
      username: secondHospitalEngineOperator.username,
      initialPassword: secondHospitalEngineOperator.initialPassword,
    });
    await expectOk(created, "创建第二医院机构版本演练运营员");
  } else {
    await expectOk(detail, "读取第二医院机构版本演练运营员");
  }
  const status = await patchApi(
    page,
    `/compliance/users/${encodeURIComponent(secondHospitalEngineOperator.userId)}/status`,
    { status: "ACTIVE" },
  );
  await expectOk(status, "启用第二医院机构版本演练运营员");
  const assigned = await postApi(
    page,
    `/compliance/users/${encodeURIComponent(secondHospitalEngineOperator.userId)}/roles`,
    {
      roleCode: "engine-operator",
      scopeLevel: "FACILITY",
      scopeCode: hospitalId,
    },
  );
  await expectOk(assigned, "绑定第二医院机构版本演练运营员职责");
}

async function loginSecondHospitalRuntimeAccount(
  page: Page,
  context: RuntimeReleaseSecondHospitalContext,
) {
  const firstLogin = await apiLogin(
    page,
    context.account.username,
    context.account.finalPassword,
    context.account.tenantId,
  );
  if (!firstLogin.ok()) {
    const initialLogin = await apiLogin(
      page,
      context.account.username,
      context.account.initialPassword,
      context.account.tenantId,
    );
    await expectOk(initialLogin, "第二医院机构版本演练运营员初始登录");
    const initialPayload = (await initialLogin.json()) as { data?: { mustChangePwd?: boolean } };
    if (initialPayload.data?.mustChangePwd) {
      const change = await postApi(page, "/auth/change-password", {
        oldPassword: context.account.initialPassword,
        newPassword: context.account.finalPassword,
      });
      await expectOk(change, "第二医院机构版本演练运营员首次改密");
    }
    const relogin = await apiLogin(
      page,
      context.account.username,
      context.account.finalPassword,
      context.account.tenantId,
    );
    await expectOk(relogin, "第二医院机构版本演练运营员改密后登录");
  }
  const profile = await apiGet(page, "/security/me");
  await expectOk(profile, "第二医院机构版本演练运营员权限画像");
  const profilePayload = (await profile.json()) as {
    data?: { dataScope?: { hospitalId?: string | null }; roles?: Array<{ code?: string }> };
  };
  expect(
    profilePayload.data?.roles?.map((role) => role.code),
    "第二医院机构版本演练运营员必须拥有 engine-operator 角色",
  ).toContain("engine-operator");
  expect(
    profilePayload.data?.dataScope?.hospitalId,
    "第二医院机构版本演练运营员 JWT 必须绑定第二家医院",
  ).toBe(context.hospitalId);
}

async function readThirdPartyRuntimeConsumerForRole(
  page: Page,
  context: RuntimeReleaseSecondHospitalContext,
  expectedRevision: number | undefined,
  label: string,
) {
  await loginSecondHospitalRuntimeAccount(page, context);
  return assertThirdPartyRuntimeConsumerCarriesRequiredAssets(page, expectedRevision, label);
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
  options: { purpose?: "selected" | "unselected" | "secondary" } = {},
): Promise<RuntimeReleaseLocalCandidate> {
  const suffix = `${Date.now()}-${testInfo.retry}-${options.purpose ?? "selected"}`;
  const assetIdentity = `ACTION_CARD.RUNTIME.RELEASE.${suffix}`;
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity,
    applicableScope: "ALL",
    sourceRef: "local-e2e:runtime-release-frontdesk",
    content: {
      schemaVersion: "1.0",
      title:
        options.purpose === "unselected"
          ? "机构生效版本未选择本院提示卡"
          : options.purpose === "secondary"
            ? "第二医院机构生效版本本院提示卡"
          : "机构生效版本本院提示卡",
      actionCode: "INFO",
      atSeverity: "LOW",
      indicator: "info",
      summary:
        options.purpose === "unselected"
          ? "用于验证本院候选资产可见但未被选入本轮机构生效版本。"
          : options.purpose === "secondary"
            ? "用于验证第二家医院可选择不同本院资产并形成独立机构生效版本。"
          : "用于验证本院候选资产进入机构生效版本前必须完成发布影响评估。",
      detail:
        options.purpose === "unselected"
          ? "本资产仅用于本地上线演练的部分选择缺席证明，不参与临床运行。"
          : options.purpose === "secondary"
            ? "本资产仅用于本地上线演练的两机构差异证明，不包含诊疗结论，不自动开立医嘱。"
          : "本资产仅用于本地上线演练，不包含诊疗结论，不自动开立医嘱。",
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

async function assertHospitalLocalRuntimeCandidateVisibleAndUnselected(
  page: Page,
  candidate: RuntimeReleaseLocalCandidate,
) {
  const localContentCard = page
    .locator(".ant-card")
    .filter({ has: page.getByText("集团与本院内容", { exact: true }) })
    .first();
  const candidateRow = localContentCard
    .getByRole("row")
    .filter({ hasText: candidate.assetIdentity })
    .filter({ hasText: "本院 · 临床提示卡内容" })
    .first();
  await expect(
    candidateRow,
    `集团与本院内容必须展示未选择候选资产 ${candidate.assetIdentity}`,
  ).toBeVisible({ timeout: 20_000 });
  const enableCheckbox = candidateRow.getByRole("checkbox", {
    name: /启用本院临床提示卡内容/u,
  });
  await expect(enableCheckbox, "未选择候选资产必须可勾选但保持未选").toBeVisible();
  await expect(enableCheckbox, "本轮未选择候选资产不应进入机构生效版本").not.toBeChecked();
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

async function exerciseOfflineDelivery(
  page: Page,
  hospitalName: string,
  hospitalId: string,
  expectedRevision: number | undefined,
): Promise<RuntimeReleaseOfflineDeliveryEvidence> {
  const runtimeBefore = await assertCurrentRuntimeAssetsReady(
    page,
    hospitalName,
    expectedRevision,
    "离线交付导出前",
  );
  const beforeIdentity = runtimeSnapshotIdentity(runtimeBefore, "离线交付导出前");
  await expect(page.getByRole("button", { name: "导出离线交付文件" })).toBeVisible({
    timeout: 20_000,
  });
  const exportResponsePromise = waitForPost(
    page,
    "/engine/releases/hospitals/",
    "/runtime-releases/offline-delivery",
  );
  await page.getByRole("button", { name: "导出离线交付文件" }).click();
  const exportResponse = await exportResponsePromise;
  const exportBody = await exportResponse.text();
  expect(
    exportResponse.ok(),
    `前台导出机构生效版本离线交付文件应成功 status=${exportResponse.status()} body=${exportBody}`,
  ).toBe(true);
  const delivery = (JSON.parse(exportBody) as {
    data?: {
      deliveryKind?: string;
      evidenceId?: string;
      fileUri?: string;
      fileDigest?: string;
      signatureAlgorithm?: string;
      runtimeMutation?: boolean;
      release?: { releaseId?: string; hospitalId?: string };
      items?: RuntimeReleaseItem[];
    };
  }).data;
  expect(delivery?.deliveryKind, "离线交付文件类型必须是机构生效版本完整快照").toBe(
    "CLINICAL_RUNTIME_RELEASE",
  );
  expect(delivery?.runtimeMutation, "导出离线交付文件不得修改机构生效版本").toBe(false);
  expect(delivery?.signatureAlgorithm, "离线交付文件必须使用 SM3/SM2 签名").toBe(
    "SM3_WITH_SM2",
  );
  expect(delivery?.evidenceId, "离线交付必须返回可信证据 ID").toBeTruthy();
  expect(delivery?.fileUri, "离线交付必须返回真实文件下载 URI").toContain(
    `/snapshots/${delivery?.evidenceId}/file`,
  );
  expect(delivery?.fileDigest, "离线交付必须返回 SM3 文件摘要").toMatch(/^sm3:[0-9a-f]{64}$/);
  expect(delivery?.release?.releaseId, "离线交付文件必须指向当前机构生效版本").toBe(
    beforeIdentity.releaseId,
  );
  expect(delivery?.release?.hospitalId, "离线交付文件必须指向当前医院").toBe(hospitalId);
  expect(delivery?.items?.length, "离线交付文件必须包含完整物化资产清单").toBe(
    runtimeBefore.items?.length,
  );
  await expect(page.getByText("离线交付文件已生成")).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("SM3/SM2 签名已生成")).toBeVisible();

  const fileResponse = await page.request.get(apiPathFromFileUri(delivery?.fileUri ?? ""), {
    headers: { "X-Trace-Id": `e2e-runtime-offline-file-${Date.now()}` },
  });
  const fileBody = await fileResponse.text();
  expect(
    fileResponse.ok(),
    `离线交付真实文件必须可下载 status=${fileResponse.status()} body=${fileBody}`,
  ).toBe(true);
  expect(fileBody, "离线交付文件必须写入机构生效版本快照类型").toContain(
    '"deliveryKind":"CLINICAL_RUNTIME_RELEASE"',
  );
  expect(fileBody, "离线交付文件必须标明不改写运行版本").toContain(
    '"runtimeMutation":false',
  );
  expect(fileBody, "离线交付文件必须包含当前 releaseId").toContain(
    `"releaseId":"${beforeIdentity.releaseId}"`,
  );

  const validateResponsePromise = waitForPost(
    page,
    "/engine/releases/hospitals/",
    "/runtime-releases/offline-delivery:validate-import",
  );
  await page.getByRole("button", { name: "校验离线交付文件" }).click();
  const validateResponse = await validateResponsePromise;
  const validateBody = await validateResponse.text();
  expect(
    validateResponse.ok(),
    `离线交付导入预检应验签通过 status=${validateResponse.status()} body=${validateBody}`,
  ).toBe(true);
  const preview = (JSON.parse(validateBody) as {
    data?: {
      status?: string;
      signatureValid?: boolean;
      manifestMatched?: boolean;
      runtimeMutation?: boolean;
      releaseId?: string;
      hospitalId?: string;
      itemCount?: number;
    };
  }).data;
  expect(preview?.status, "离线交付导入预检状态必须通过").toBe("VALIDATED");
  expect(preview?.signatureValid, "离线交付导入预检必须验签通过").toBe(true);
  expect(preview?.manifestMatched, "离线交付导入预检必须命中当前清单摘要").toBe(true);
  expect(preview?.runtimeMutation, "离线交付导入预检不得改写当前机构生效版本").toBe(false);
  expect(preview?.releaseId, "导入预检 releaseId 必须与导出文件一致").toBe(
    beforeIdentity.releaseId,
  );
  expect(preview?.hospitalId, "导入预检 hospitalId 必须与当前医院一致").toBe(hospitalId);
  expect(preview?.itemCount, "导入预检必须返回完整资产条数").toBe(delivery?.items?.length);
  await expect(page.getByText("导入预检通过")).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText("不会改写当前机构生效版本")).toBeVisible();

  const runtimeAfter = await assertCurrentRuntimeAssetsReady(
    page,
    hospitalName,
    expectedRevision,
    "离线交付导入预检后",
  );
  const afterIdentity = runtimeSnapshotIdentity(runtimeAfter, "离线交付导入预检后");
  expect(afterIdentity, "离线交付导入预检前后当前机构生效版本不得改变").toEqual(
    beforeIdentity,
  );

  return {
    delivery: {
      deliveryKind: delivery?.deliveryKind ?? "",
      evidenceId: delivery?.evidenceId ?? "",
      fileUri: delivery?.fileUri ?? "",
      fileDigest: delivery?.fileDigest ?? "",
      signatureAlgorithm: delivery?.signatureAlgorithm ?? "",
      runtimeMutation: delivery?.runtimeMutation ?? true,
      releaseId: delivery?.release?.releaseId ?? "",
      hospitalId: delivery?.release?.hospitalId ?? "",
      itemCount: delivery?.items?.length ?? 0,
    },
    downloadedFile: {
      fileUri: delivery?.fileUri ?? "",
      containsDeliveryKind: fileBody.includes('"deliveryKind":"CLINICAL_RUNTIME_RELEASE"'),
      containsRuntimeMutationFalse: fileBody.includes('"runtimeMutation":false'),
      containsReleaseId: fileBody.includes(`"releaseId":"${beforeIdentity.releaseId}"`),
    },
    importPreview: {
      status: preview?.status ?? "",
      signatureValid: preview?.signatureValid ?? false,
      manifestMatched: preview?.manifestMatched ?? false,
      runtimeMutation: preview?.runtimeMutation ?? true,
      releaseId: preview?.releaseId ?? "",
      hospitalId: preview?.hospitalId ?? "",
      itemCount: preview?.itemCount ?? 0,
    },
    runtimeBefore: beforeIdentity,
    runtimeAfter: afterIdentity,
  };
}

function runtimeSnapshotIdentity(
  detail: RuntimeReleaseDetail,
  label: string,
): RuntimeReleaseSnapshotIdentity {
  expect(detail.release?.releaseId, `${label} 必须返回机构生效版本 ID`).toBeTruthy();
  expect(detail.release?.revisionNo, `${label} 必须返回机构生效版本修订号`).toBeGreaterThan(0);
  expect(detail.release?.manifestSha256, `${label} 必须返回机构生效版本清单摘要`).toMatch(
    /^[0-9a-f]{64}$/i,
  );
  return {
    releaseId: detail.release?.releaseId ?? "",
    revisionNo: detail.release?.revisionNo ?? 0,
    manifestSha256: detail.release?.manifestSha256 ?? "",
  };
}

function apiPathFromFileUri(fileUri: string) {
  expect(fileUri, "离线交付文件下载 URI 不能为空").toBeTruthy();
  if (fileUri.startsWith("/api/v1/")) {
    return `${apiBase}${fileUri.slice("/api/v1".length)}`;
  }
  if (fileUri.startsWith(apiBase)) {
    return fileUri;
  }
  throw new Error(`不支持的离线交付文件下载 URI: ${fileUri}`);
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

function assertRuntimeAssetsExcludeUnselectedCandidate(
  assets: Array<RuntimeAssetSelection | RuntimeReleaseItem>,
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
    `${label} 不应包含本轮未选择候选资产 ${candidate.assetType} ${candidate.assetIdentity} ${candidate.versionId}`,
  ).toBeUndefined();
  return true;
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
      partialSelectionProved: false,
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

async function apiGet(page: Page, path: string) {
  return page.request.get(`${apiBase}${path}`, {
    headers: { "X-Trace-Id": `e2e-runtime-release-get-${Date.now()}` },
  });
}

async function apiLogin(page: Page, username: string, password: string, tenantId: string) {
  return page.request.post(`${apiBase}/auth/login`, {
    data: { username, password, tenantId },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": `e2e-runtime-release-login-${Date.now()}`,
    },
  });
}

async function responseData(response: APIResponse) {
  const body = (await response.json()) as { data?: unknown };
  return body.data ?? null;
}

function requireOrgUnit(value: unknown, label: string) {
  const id = textField(value, "id");
  const code = textField(value, "code");
  if (!id || !code) {
    throw new Error(`${label} 响应缺少组织 id/code`);
  }
  return { id, code };
}

function arrayData(value: unknown) {
  if (Array.isArray(value)) return value;
  const items = recordField(value, "items");
  return Array.isArray(items) ? items : [];
}

function recordField(value: unknown, field: string) {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)[field]
    : undefined;
}

function textField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function sameRuntimeReleaseCandidate(
  left: RuntimeReleaseLocalCandidate,
  right: RuntimeReleaseLocalCandidate,
) {
  return (
    left.assetType === right.assetType &&
    left.assetIdentity === right.assetIdentity &&
    left.versionId === right.versionId
  );
}
