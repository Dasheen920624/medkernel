import { expect, type Page } from "@playwright/test";

import {
  arrayField,
  getApi,
  numericField,
  pageItems,
  postApi,
  recordField,
  requiredRuntimeAssetsForRehearsal,
  responseData,
  resolvedTenantIdFor,
  resolveBaselineRuntimeAssets,
  textField,
} from "./auth";

export const diagnosticCriticalValueActionCardIdentity = "ACTION_CARD.REPORT.CRITICAL_VALUE";
export const diagnosticKnowledgeIdentity = "plat:diagnostic_item:lab-potassium";
export const diagnosticReportFamilyKnowledgeIdentityPrefix =
  "launch.diagnostic-item.image-boundary";
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

type KnowledgeGenerationCandidate = {
  candidateRef?: string;
  jobCode?: string;
};

type KnowledgeCandidateItem = {
  id?: number;
  versionNo?: string;
  contentHash?: string;
  status?: string;
};

type KnowledgeCandidateClassification = {
  id?: number;
  candidateVersionId?: number;
};

export type DiagnosticCriticalValueRuntime = {
  releaseId: string;
  platformBaselineReleaseId: string;
  revisionNo: number;
  manifestSha256: string;
  assets: DiagnosticRuntimeReleaseItem[];
  knowledgeAsset: DiagnosticRuntimeReleaseItem;
  reportFamilyKnowledgeAsset?: DiagnosticRuntimeReleaseItem;
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
  options: { includeReportFamilyMatrixKnowledge?: boolean } = {},
): Promise<DiagnosticCriticalValueRuntime> {
  const hospitalId = await localRehearsalHospitalId(page);
  const actionCard = await createCriticalValueActionCardAsset(page, suffix);
  const reportFamilyKnowledge = options.includeReportFamilyMatrixKnowledge
    ? await createReportFamilyMatrixKnowledgeAsset(page, suffix, hospitalId)
    : undefined;
  const diagnosticAssets = await readDiagnosticRuntimeCandidates(page, {
    actionCard,
    reportFamilyKnowledge,
  });
  return activateRuntimeWithDiagnosticCriticalAssets(page, {
    hospitalId,
    knowledge: diagnosticAssets.knowledge,
    reportFamilyKnowledge: diagnosticAssets.reportFamilyKnowledge,
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
  options: {
    actionCard: DiagnosticRuntimeAssetCandidate;
    reportFamilyKnowledge?: DiagnosticRuntimeAssetCandidate;
  },
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
  return {
    knowledge,
    reportFamilyKnowledge: options.reportFamilyKnowledge,
    fieldCatalog,
    actionCard: options.actionCard,
  };
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
    reportFamilyKnowledge?: DiagnosticRuntimeAssetCandidate;
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
    ...(options.reportFamilyKnowledge ? [runtimeSelection(options.reportFamilyKnowledge)] : []),
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
    reportFamilyKnowledgeAsset: options.reportFamilyKnowledge
      ? assertRuntimeContainsAsset(detail, options.reportFamilyKnowledge)
      : undefined,
    fieldCatalogAsset: assertRuntimeContainsAsset(detail, options.fieldCatalog),
    actionCardAsset: assertRuntimeContainsAsset(detail, options.actionCard),
    activationRequest,
  };
}

async function createReportFamilyMatrixKnowledgeAsset(
  page: Page,
  suffix: string,
  hospitalId: string,
): Promise<DiagnosticRuntimeAssetCandidate> {
  const identityCode = `${diagnosticReportFamilyKnowledgeIdentityPrefix}.${stableSlugSuffix(
    suffix,
  )}`;
  const subject = "五类医技报告解读通用边界说明书";
  const sourceCode = `local-e2e-report-family-matrix-${suffix.toLowerCase()}`;
  const sourceVersionNo = "2026";
  const anchorPath = `diagnostic-report-family/matrix-${suffix}`;
  const source = await postApi(page, "/engine/knowledge/sources", {
    ...knowledgeContext("s36-report-family-source", hospitalId),
    sourceCode,
    sourceType: "HOSPITAL_PROTOCOL",
    authorityLevel: "D_HOSPITAL",
    authorityBasis: "本地上线演练五类医技报告族矩阵说明书，用于验证 S36 报告解读消费者。",
    title: `五类医技报告族解读边界来源 ${suffix}`,
    publisher: "MedKernel 本地上线演练",
    license: "内部演练",
    language: "zh-CN",
  });
  await expectOk(source, "登记 S36 五类医技报告族说明书来源");
  const sourceDocumentId = numericField(await responseData(source), "id");
  expect(sourceDocumentId, "S36 五类报告族知识来源必须返回 id").toBeTruthy();

  const sourceVersion = await postApi(
    page,
    `/engine/knowledge/sources/${sourceDocumentId}/versions`,
    {
      ...knowledgeContext("s36-report-family-source-version", hospitalId),
      versionNo: sourceVersionNo,
      publishedAt: "2026-07-08T00:00:00Z",
      fileUri: `medkernel://local-e2e/diagnostic-report-family/matrix-${suffix}.md`,
      language: "zh-CN",
      content: reportFamilyMatrixKnowledgeContent(),
    },
  );
  await expectOk(sourceVersion, "登记 S36 五类医技报告族说明书来源版本");
  const sourceVersionId = numericField(await responseData(sourceVersion), "id");
  expect(sourceVersionId, "S36 五类报告族知识来源版本必须返回 id").toBeTruthy();

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId,
    anchorPath,
    anchorLabel: "五类医技报告解读通用边界",
    textExcerpt: reportFamilyMatrixKnowledgeContent(),
  });
  await expectOk(fragment, "登记 S36 五类医技报告族说明书来源片段");
  const sourceFragmentId = numericField(await responseData(fragment), "id");
  expect(sourceFragmentId, "S36 五类报告族知识来源片段必须返回 id").toBeTruthy();

  const generated = await postApi(page, "/engine/knowledge-production/generate", {
    sourceVersionId,
    targetPipeline: "TENANT_OVERLAY",
    domain: "CLINICAL",
    items: [
      {
        assetType: "KNOWLEDGE",
        target: {
          targetIdentityId: null,
          newIdentity: { domain: "DIAGNOSTIC_ITEM", subject, identityCode },
        },
      },
    ],
  });
  await expectOk(generated, "从受控来源生成 S36 五类医技报告族说明书生产候选");
  const generation = await responseData(generated);
  expect(arrayField(generation, "blocked"), "S36 五类报告族知识生产安全门不得阻断").toHaveLength(0);
  expect(arrayField(generation, "skipped"), "S36 五类报告族知识生产不得被分流跳过").toHaveLength(0);
  const generatedCandidates = arrayField(generation, "candidates") as KnowledgeGenerationCandidate[];
  expect(generatedCandidates, "S36 五类报告族知识生产必须生成一个 KNOWLEDGE 候选").toHaveLength(1);
  const candidateRef = requireText(
    textField(generatedCandidates[0], "candidateRef"),
    "S36 五类报告族知识生产必须返回 candidateRef",
  );
  const jobCode = requireText(
    textField(generatedCandidates[0], "jobCode"),
    "S36 五类报告族知识生产必须返回 jobCode",
  );
  const parsed = parseKnowledgeCandidateRef(candidateRef);
  const identity = await getApi(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
  );
  await expectOk(identity, "读取 S36 五类医技报告族说明书知识身份");
  const identityId = numericField(await responseData(identity), "id");
  expect(identityId, "S36 五类报告族知识生产必须物化知识身份").toBe(parsed.identityId);

  const candidateView = await getApi(
    page,
    `/engine/knowledge/identities/${identityId}/candidates?page=1&size=20`,
  );
  await expectOk(candidateView, "读取 S36 五类医技报告族说明书候选审核项");
  const candidateData = await responseData(candidateView);
  const versionCandidate = pageItems(recordField(candidateData, "candidates")).find(
    (item) => textField(item, "versionNo") === parsed.versionNo,
  ) as KnowledgeCandidateItem | undefined;
  expect(versionCandidate, "S36 五类报告族知识生产候选版本必须物化").toBeTruthy();
  const versionId = numericField(versionCandidate, "id");
  expect(versionId, "S36 五类报告族知识版本必须返回 id").toBeTruthy();
  const contentHash = requireText(
    textField(versionCandidate, "contentHash"),
    "S36 五类报告族知识生产候选必须返回 contentHash",
  );
  const classification = (recordField(candidateData, "classifications") as unknown[] | undefined)
    ?.map((item) => recordValue(item))
    .find((item) => numericField(item, "candidateVersionId") === versionId) as
    | KnowledgeCandidateClassification
    | undefined;
  const classificationId = numericField(classification, "id");
  expect(classificationId, "S36 五类报告族知识生产候选必须生成审核分类").toBeTruthy();

  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: versionId,
    sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 90,
    startOffset: null,
    endOffset: null,
  });
  await expectOk(citation, "绑定 S36 五类医技报告族说明书来源引用");

  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode)}/publication-quality-records`,
    {
      candidateRef,
      identityId,
      versionId,
    },
  );
  await expectOk(qualityRecord, "生成 S36 五类报告族说明书服务端发布质量记录");
  const qualityGateRecordId = numericField(await responseData(qualityRecord), "id");
  expect(qualityGateRecordId, "S36 五类报告族知识发布质量记录必须返回 id").toBeTruthy();

  const review = await postApi(page, `/engine/knowledge/candidates/${classificationId}/review`, {
    ...knowledgeContext("s36-report-family-review", hospitalId),
    decision: "APPROVE",
    reason:
      "S36 五类医技报告族消费者矩阵代表切片：受控来源、引用、质量门、分流和影子评测均已通过，本次仅作为报告解读通用边界说明书。",
    qualityGateRecordId,
  });
  await expectOk(review, "审核发布 S36 五类医技报告族说明书候选");
  expect(textField(await responseData(review), "reasonCode"), "S36 五类报告族知识候选必须审核通过").toBe(
    "APPROVED",
  );

  const assetVersion = await waitForKnowledgeUnifiedAssetVersion(page, {
    hospitalId,
    identityCode,
    contentHash,
  });
  return {
    assetType: "KNOWLEDGE",
    assetIdentity: identityCode,
    versionId: requireText(
      textField(assetVersion, "versionId"),
      "S36 五类报告族知识统一版本必须返回 versionId",
    ),
    versionNo: requireText(
      textField(assetVersion, "versionNo"),
      "S36 五类报告族知识统一版本必须返回 versionNo",
    ),
    contentHash: requireText(
      textField(assetVersion, "contentHash"),
      "S36 五类报告族知识统一版本必须返回 contentHash",
    ),
  };
}

async function waitForKnowledgeUnifiedAssetVersion(
  page: Page,
  options: { hospitalId: string; identityCode: string; contentHash: string },
) {
  const deadline = Date.now() + 20_000;
  let lastCandidateCount = 0;
  while (Date.now() < deadline) {
    const response = await getApi(
      page,
      `/engine/releases/hospitals/${encodeURIComponent(
        options.hospitalId,
      )}/runtime-candidates?assetType=KNOWLEDGE&keyword=${encodeURIComponent(options.identityCode)}&page=1&size=20`,
    );
    await expectOk(response, "读取 S36 五类报告族知识统一资产版本");
    const candidates = pageItems(await responseData(response));
    lastCandidateCount = candidates.length;
    const item = candidates.find(
      (candidate) =>
        textField(candidate, "assetType") === "KNOWLEDGE" &&
        textField(candidate, "assetIdentity") === options.identityCode &&
        textField(candidate, "status") === "PUBLISHED" &&
        textField(candidate, "contentHash") === options.contentHash,
    );
    const versionId = textField(item, "versionId");
    if (item && versionId) {
      expect(versionId.startsWith("av-"), "S36 五类报告族知识 runtime 候选必须使用统一资产 av-* 版本").toBe(
        true,
      );
      return item;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error(
    `S36 五类报告族知识资产 ${options.identityCode} 未同步为医院 runtime 候选，最后候选数：${lastCandidateCount}`,
  );
}

function reportFamilyMatrixKnowledgeContent() {
  return [
    "五类医技报告解读通用边界说明书。",
    "适用于本地上线演练中的 PACS/RIS 影像、超声、病理、内镜和心电已签发报告阅读辅助。",
    "当报告结论包含检查、影像、超声、病理、内镜、心电、CT 或异常提示时，应提示医师结合患者上下文和原始报告人工复核。",
    "系统不改写已签发报告，不自动取消检查，不自动开立医嘱。",
  ].join("\n");
}

function stableSlugSuffix(value: string) {
  const normalized = value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
  return normalized.slice(0, 24);
}

function parseKnowledgeCandidateRef(candidateRef: string) {
  const parts = candidateRef.split(":");
  if (parts.length < 3 || parts[0] !== "kv") {
    throw new Error(`S36 五类报告族知识候选引用格式非法：${candidateRef}`);
  }
  const identityId = Number(parts[1]);
  expect(Number.isFinite(identityId), "S36 五类报告族知识候选引用必须包含数字身份 ID").toBe(true);
  return { identityId, versionNo: parts.slice(2).join(":") };
}

function knowledgeContext(prefix: string, hospitalId?: string) {
  const traceId = `${prefix}-${Date.now()}`;
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: resolvedTenantIdFor("engine-operator"),
    hospital_id: hospitalId,
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
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
