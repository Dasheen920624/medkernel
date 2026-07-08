import { expect, type Page } from "@playwright/test";

import {
  getApi,
  pageItems,
  postApi,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  resolveBaselineRuntimeAssets,
  textField,
} from "./auth";

export const diagnosticCriticalValueActionCardIdentity = "ACTION_CARD.REPORT.CRITICAL_VALUE";
export const diagnosticKnowledgeIdentity = "plat:diagnostic_item:lab-potassium";
export const diagnosticFieldCatalogIdentity = "FIELD.CATALOG.CLINICAL_CONTEXT";

export type DiagnosticRuntimeAssetCandidate = {
  assetType: "KNOWLEDGE" | "FIELD_CATALOG" | "ACTION_CARD";
  assetIdentity: string;
  versionId: string;
  versionNo: string;
  contentHash: string;
};

export type DiagnosticRuntimeReleaseItem = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
  versionNo?: string;
  contentHash?: string;
  entryState?: string;
  sourceLayer?: string;
};

type RuntimeAssetSelection = {
  assetType: string;
  assetIdentity: string;
  versionId: string | null;
};

type RuntimeReleaseDetail = {
  release?: {
    releaseId?: string;
    revisionNo?: number;
    manifestSha256?: string;
    platformBaselineReleaseId?: string;
  };
  items?: DiagnosticRuntimeReleaseItem[];
};

export type DiagnosticCriticalValueRuntime = {
  releaseId: string;
  platformBaselineReleaseId: string;
  revisionNo: number;
  manifestSha256: string;
  assets: DiagnosticRuntimeReleaseItem[];
  knowledgeAsset: DiagnosticRuntimeReleaseItem;
  fieldCatalogAsset: DiagnosticRuntimeReleaseItem;
  actionCardAsset: DiagnosticRuntimeReleaseItem;
  activationRequest: {
    platformBaselineReleaseId: string | null;
    expectedCurrentReleaseId: string | null;
    confirmedPlatformUpgradeDigest: string | null;
    activeAssets: RuntimeAssetSelection[];
  };
};

export async function ensureDiagnosticCriticalValueRuntime(
  page: Page,
  suffix: string,
): Promise<DiagnosticCriticalValueRuntime> {
  const hospitalId = await localRehearsalHospitalId(page);
  const actionCard = await createCriticalValueActionCardAsset(page, suffix);
  const diagnosticAssets = await readDiagnosticRuntimeCandidates(page, actionCard);
  return activateRuntimeWithDiagnosticCriticalAssets(page, {
    hospitalId,
    knowledge: diagnosticAssets.knowledge,
    fieldCatalog: diagnosticAssets.fieldCatalog,
    actionCard: diagnosticAssets.actionCard,
  });
}

async function createCriticalValueActionCardAsset(
  page: Page,
  suffix: string,
): Promise<DiagnosticRuntimeAssetCandidate> {
  const response = await postApi(page, "/engine/authoring/declarative-assets", {
    assetType: "ACTION_CARD",
    assetIdentity: diagnosticCriticalValueActionCardIdentity,
    applicableScope: "ALL",
    sourceRef: "local-e2e:diagnostic-critical-value-runtime",
    content: {
      schemaVersion: "1.0",
      title: `危急值报告人工复核提示 ${suffix}`,
      actionCode: "STRONG_REMINDER",
      atSeverity: "HIGH",
      indicator: "critical",
      summary: "危急值报告需人工确认、回报和记录",
      detail: "报告解读仅作辅助，不改写已签发报告，不自动开立医嘱。",
      source: { label: "MedKernel 本地上线演练" },
      suggestions: [
        { label: "打开报告上下文", actionType: "OPEN_FORM", payload: { target: "report-context" } },
      ],
      overrideReasons: ["已完成人工危急值回报与复核记录"],
      requiresPhysicianConfirmation: true,
    },
  });
  await expectOk(response, "创建危急值报告 ACTION_CARD 资产");
  const data = await responseData(response);
  return {
    assetType: "ACTION_CARD",
    assetIdentity: diagnosticCriticalValueActionCardIdentity,
    versionId: requireText(textField(data, "versionId"), "危急值提示卡必须返回 versionId"),
    versionNo: requireText(textField(data, "versionNo"), "危急值提示卡必须返回 versionNo"),
    contentHash: requireText(textField(data, "contentHash"), "危急值提示卡必须返回 contentHash"),
  };
}

async function readDiagnosticRuntimeCandidates(
  page: Page,
  actionCard: DiagnosticRuntimeAssetCandidate,
) {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本中的报告解读运行资产");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  const knowledge = readPlatformBaselineRuntimeAsset(
    baselineAssets.activeAssetVersions,
    "KNOWLEDGE",
    diagnosticKnowledgeIdentity,
  );
  const fieldCatalog = readPlatformBaselineRuntimeAsset(
    baselineAssets.activeAssetVersions,
    "FIELD_CATALOG",
    diagnosticFieldCatalogIdentity,
  );
  return { knowledge, fieldCatalog, actionCard };
}

function readPlatformBaselineRuntimeAsset(
  activeAssetVersions: DiagnosticRuntimeReleaseItem[],
  assetType: "KNOWLEDGE" | "FIELD_CATALOG",
  assetIdentity: string,
): DiagnosticRuntimeAssetCandidate {
  const asset = activeAssetVersions.find(
    (item) => item.assetType === assetType && item.assetIdentity === assetIdentity,
  );
  return {
    assetType,
    assetIdentity,
    versionId: requireText(textField(asset, "versionId"), `${assetType} 平台标准版本必须返回 versionId`),
    versionNo: requireText(textField(asset, "versionNo"), `${assetType} 平台标准版本必须返回 versionNo`),
    contentHash: requireText(
      textField(asset, "contentHash"),
      `${assetType} 平台标准版本必须返回 contentHash`,
    ),
  };
}

async function activateRuntimeWithDiagnosticCriticalAssets(
  page: Page,
  options: {
    hospitalId: string;
    knowledge: DiagnosticRuntimeAssetCandidate;
    fieldCatalog: DiagnosticRuntimeAssetCandidate;
    actionCard: DiagnosticRuntimeAssetCandidate;
  },
): Promise<DiagnosticCriticalValueRuntime> {
  const baseline = await getApi(page, "/engine/releases/platform-baselines/current");
  await expectOk(baseline, "读取当前平台标准版本");
  const baselineAssets = resolveBaselineRuntimeAssets(await responseData(baseline));
  expect(baselineAssets.baselineReleaseId, "当前平台标准版本必须存在").toBeTruthy();
  for (const required of requiredRuntimeAssetsForRehearsal) {
    expect(
      baselineAssets.activeAssets.some(
        (asset) =>
          asset.assetType === required.assetType && asset.assetIdentity === required.assetIdentity,
      ),
      `平台标准版本缺少 ${required.assetType}:${required.assetIdentity}`,
    ).toBe(true);
  }

  const current = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(current, "读取当前医院生效版本");
  const currentRuntime = await responseData(current);
  const currentReleaseId = textFieldAtPath(currentRuntime, "release.releaseId");
  const currentPlatformBaselineReleaseId = textFieldAtPath(
    currentRuntime,
    "release.platformBaselineReleaseId",
  );
  const activeAssets = uniqueRuntimeAssets([
    ...baselineAssets.activeAssets,
    runtimeSelection(options.actionCard),
  ]);
  const activationRequest = {
    platformBaselineReleaseId: baselineAssets.baselineReleaseId,
    expectedCurrentReleaseId: currentReleaseId,
    confirmedPlatformUpgradeDigest:
      currentReleaseId &&
      currentPlatformBaselineReleaseId &&
      currentPlatformBaselineReleaseId !== baselineAssets.baselineReleaseId
        ? await readPlatformUpgradeAnalysisDigest(
            page,
            options.hospitalId,
            baselineAssets.baselineReleaseId,
          )
        : null,
    activeAssets,
  };
  const activated = await postApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases`,
    activationRequest,
  );
  await expectOk(activated, "激活包含危急值报告解读运行资产的医院生效版本");
  const activatedRelease = await responseData(activated);
  const releaseId = requireText(
    textField(activatedRelease, "releaseId"),
    "激活机构生效版本必须返回 releaseId",
  );
  const currentAfterActivation = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(options.hospitalId)}/runtime-releases/current`,
  );
  await expectOk(currentAfterActivation, "回读包含危急值报告解读运行资产的医院生效版本");
  const detail = (await responseData(currentAfterActivation)) as RuntimeReleaseDetail;
  expect(textFieldAtPath(detail, "release.releaseId"), "当前医院生效版本必须指向本次激活").toBe(
    releaseId,
  );
  return {
    releaseId,
    platformBaselineReleaseId: requireText(
      textFieldAtPath(detail, "release.platformBaselineReleaseId"),
      "机构生效版本必须返回平台标准版本 ID",
    ),
    revisionNo: numberFieldAtPath(detail, "release.revisionNo") ?? 0,
    manifestSha256: requireText(
      textFieldAtPath(detail, "release.manifestSha256"),
      "机构生效版本必须返回 manifestSha256",
    ),
    assets: detail.items ?? [],
    knowledgeAsset: assertRuntimeContainsAsset(detail, options.knowledge),
    fieldCatalogAsset: assertRuntimeContainsAsset(detail, options.fieldCatalog),
    actionCardAsset: assertRuntimeContainsAsset(detail, options.actionCard),
    activationRequest,
  };
}

async function localRehearsalHospitalId(page: Page) {
  const hospitals = await getApi(
    page,
    "/engine/org/org-units?keyword=本地上线演练医院&page=1&size=20",
  );
  await expectOk(hospitals, "读取本地上线演练医院");
  const hospital = pageItems(await responseData(hospitals)).find(
    (item) =>
      textField(item, "name") === "本地上线演练医院" ||
      textField(item, "code") === "e2e-rehearsal-hospital",
  );
  return requireText(textField(hospital, "id"), "必须找到本地上线演练医院");
}

async function readPlatformUpgradeAnalysisDigest(
  page: Page,
  hospitalId: string,
  targetBaselineReleaseId: string,
) {
  const response = await getApi(
    page,
    `/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/platform-upgrade-analysis?targetBaselineReleaseId=${encodeURIComponent(
      targetBaselineReleaseId,
    )}`,
  );
  await expectOk(response, "读取报告解读平台升级分析");
  return requireText(
    textField(await responseData(response), "analysisDigest"),
    "报告解读平台升级分析必须返回摘要",
  );
}

function runtimeSelection(candidate: DiagnosticRuntimeAssetCandidate): RuntimeAssetSelection {
  return {
    assetType: candidate.assetType,
    assetIdentity: candidate.assetIdentity,
    versionId: candidate.versionId,
  };
}

function uniqueRuntimeAssets(assets: RuntimeAssetSelection[]) {
  const byKey = new Map<string, RuntimeAssetSelection>();
  for (const asset of assets) {
    byKey.set(`${asset.assetType}:${asset.assetIdentity}`, asset);
  }
  return Array.from(byKey.values());
}

function assertRuntimeContainsAsset(
  runtime: RuntimeReleaseDetail,
  candidate: DiagnosticRuntimeAssetCandidate,
) {
  const asset = (runtime.items ?? []).find(
    (item) =>
      item.assetType === candidate.assetType &&
      item.assetIdentity === candidate.assetIdentity &&
      item.versionId === candidate.versionId &&
      item.entryState === "ACTIVE",
  );
  expect(asset, `机构生效版本必须包含 ${candidate.assetType} ${candidate.assetIdentity}`).toBeTruthy();
  expect(asset?.versionNo, `${candidate.assetType} runtime 清单必须返回版本号`).toBe(
    candidate.versionNo,
  );
  expect(asset?.contentHash, `${candidate.assetType} runtime 清单必须返回正文 hash`).toBe(
    candidate.contentHash,
  );
  return asset as DiagnosticRuntimeReleaseItem;
}

async function expectOk(response: { ok(): boolean; status(): number; text(): Promise<string> }, label: string) {
  if (response.ok()) return;
  const body = await response.text();
  throw new Error(`${label} 失败：${response.status()} ${body}`);
}

function textFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberFieldAtPath(value: unknown, path: string) {
  const raw = valueAtPath(value, path);
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function valueAtPath(value: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((current, part) => {
    if (current == null) return undefined;
    const match = /^([^[\]]+)(?:\[(\d+)\])?$/u.exec(part);
    if (!match) return undefined;
    const record = recordValue(current);
    if (!record) return undefined;
    let next: unknown = record[match[1]];
    if (match[2] !== undefined) {
      if (!Array.isArray(next)) return undefined;
      next = next[Number(match[2])];
    }
    return next;
  }, value);
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function requireText(value: string | null, message: string) {
  expect(value, message).toBeTruthy();
  return value ?? "";
}
