import { createHash } from "node:crypto";

import { expect, test, type Page, type TestInfo } from "@playwright/test";

import { apiBase, ensureReadySession, expectOk, patchApi, postApi } from "./support/auth";

type SourceLineageApiEvidence = {
  sourceRegistered: boolean;
  sourceVersionRegistered: boolean;
  sourceFragmentRegistered: boolean;
  knowledgeCandidateSubmitted: boolean;
  citationBound: boolean;
  candidateApproved: boolean;
  graphProjectionRebuilt: boolean;
  provenanceReadback: boolean;
  graphNodeExplored: boolean;
  traceEvidenceVisible: boolean;
};

type GraphKnowledgeSource = {
  sourceDocumentId: number;
  sourceVersionId: number;
  sourceCode: string;
  sourceVersionNo: string;
  sourceVersionHash: string;
  sourceRef: string;
  sourceFragmentId: number;
  fragmentHash: string;
  anchorPath: string;
  anchorLabel: string;
  textExcerpt: string;
  content: string;
};

type GraphKnowledgeSeed = GraphKnowledgeSource & {
  identityId: number;
  identityCode: string;
  versionId: number;
  subject: string;
  citationId: number;
  candidateRef: string;
  jobCode: string;
  classificationId: number;
  qualityGateRecordId: number;
  provenanceReadback: SourceLineageProvenanceEvidence;
};

type SourceLineageProvenanceEvidence = {
  identityId: number;
  identityCode: string;
  currentVersionId: number;
  activeVersionStatus: string;
  partial: boolean;
  unresolvedCitationCount: number;
  citationId: number;
  sourceFragmentId: number;
  sourceDocumentId: number;
  sourceVersionId: number;
  sourceCode: string;
  sourceType: string;
  authorityLevel: string;
  authorityLabel: string;
  sourceVersionNo: string;
  sourceVersionHash: string;
  anchorPath: string;
  anchorLabel: string;
  fragmentHash: string;
  relation: string;
  weight: number;
};

const sourceLineageTitle = "医疗引擎运营员可重建并探索真实知识投影";
const requiredSourceLineageScenarioEvidence = [
  {
    code: "S7",
    observedStages: [
      "真实登记受控来源、版本和锚点",
      "真实提交并审核激活带来源引用的知识候选",
      "真实绑定来源引用并回读血缘证据",
      "真实重建知识关系投影",
      "前台探索知识关系图并查看追踪证据",
    ],
  },
] as const;

test.describe.configure({ mode: "serial" });

test.describe("D6 知识关系真实验收", () => {
  test(sourceLineageTitle, async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    const observedStages = new Set<string>();
    const apiEvidence: SourceLineageApiEvidence = {
      sourceRegistered: false,
      sourceVersionRegistered: false,
      sourceFragmentRegistered: false,
      knowledgeCandidateSubmitted: false,
      citationBound: false,
      candidateApproved: false,
      graphProjectionRebuilt: false,
      provenanceReadback: false,
      graphNodeExplored: false,
      traceEvidenceVisible: false,
    };

    await enableGraphProjection(page);
    const seed = await seedActiveKnowledge(page, observedStages, apiEvidence);
    await ensureReadySession(page, "engine-operator", "platform");
    const rebuild = await postApi(page, "/projections/knowledge-graph/rebuild", {});
    await expectOk(rebuild, "重建知识关系投影");
    const rebuilt = (await rebuild.json()).data;
    expect(rebuilt.sourceCount).toBeGreaterThan(0);
    expect(rebuilt.projectionCount).toBe(rebuilt.sourceCount);
    await assertSeedProjected(page, seed);
    apiEvidence.graphProjectionRebuilt = true;
    recordSourceLineageStage(observedStages, "真实重建知识关系投影");

    await ensureReadySession(page, "engine-operator", "platform");

    await page.goto("/advanced/graph");
    await expect(page.getByRole("heading", { name: "知识关系" })).toBeVisible();
    await selectKnowledgeProjection(page);
    await ensureEvidenceDetailsEnabled(page);
    await page
      .getByRole("textbox", { name: "实体、关系或追踪号" })
      .fill(`KNOWLEDGE_IDENTITY:${seed.identityId}`);
    await page.getByRole("button", { name: "查询" }).click();
    await expect(page.getByRole("group", { name: "知识关系图" })).toBeVisible();
    await expect(page.getByRole("button", { name: "重建投影" })).toBeVisible();

    const nodes = page.locator('svg[aria-label="知识关系图"] g[role="button"]');
    expect(await nodes.count()).toBeGreaterThan(0);
    const seedNode = page.locator(
      `svg[aria-label="知识关系图"] g[role="button"][aria-label="知识身份 ${seed.identityId}"]`,
    );
    await expect(seedNode).toBeVisible({ timeout: 30_000 });
    await seedNode.click();
    const detail = page.locator("aside");
    await expect(detail.getByText("对象标识", { exact: true })).toBeVisible();
    await expect(detail.getByText(String(seed.identityId), { exact: true })).toBeVisible();
    await expect(detail.getByText("追踪号", { exact: true })).toBeVisible();
    await expect(detail.getByText("未返回", { exact: true })).toHaveCount(0);
    apiEvidence.graphNodeExplored = true;
    apiEvidence.traceEvidenceVisible = true;
    recordSourceLineageStage(observedStages, "前台探索知识关系图并查看追踪证据");
    await expectNoRootOverflow(page);
    await page.evaluate(() => window.scrollTo(0, 0));

    const screenshotPath = testInfo.outputPath("graph-desktop.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("graph-desktop", {
      path: screenshotPath,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
    await attachSourceLineageScenarioEvidence(testInfo, observedStages, apiEvidence, {
      source: {
        sourceDocumentId: seed.sourceDocumentId,
        sourceVersionId: seed.sourceVersionId,
        sourceFragmentId: seed.sourceFragmentId,
        sourceCode: seed.sourceCode,
        sourceVersionNo: seed.sourceVersionNo,
        sourceVersionHash: seed.sourceVersionHash,
        fragmentHash: seed.fragmentHash,
        sourceRef: seed.sourceRef,
        anchorPath: seed.anchorPath,
        anchorLabel: seed.anchorLabel,
        textExcerpt: seed.textExcerpt,
        contentHashVerified: true,
        fragmentHashVerified: true,
      },
      knowledgeCandidate: {
        operation: "GENERATE_REVIEW_APPROVE",
        identityId: seed.identityId,
        identityCode: seed.identityCode,
        versionId: seed.versionId,
        candidateRef: seed.candidateRef,
        jobCode: seed.jobCode,
        classificationId: seed.classificationId,
        qualityGateRecordId: seed.qualityGateRecordId,
        status: "ACTIVE",
      },
      citation: {
        citationId: seed.citationId,
        relation: "DERIVED_FROM",
        weight: 100,
        startOffset: 0,
        endOffset: seed.textExcerpt.length,
        sourceFragmentId: seed.sourceFragmentId,
        assetVersionId: seed.versionId,
      },
      provenanceReadback: seed.provenanceReadback,
      graphProjection: {
        operation: "REBUILD_AND_EXPLORE",
        sourceCount: Number(rebuilt.sourceCount),
        projectionCount: Number(rebuilt.projectionCount),
        projectionMatchesSourceCount: rebuilt.projectionCount === rebuilt.sourceCount,
        graphNodeExplored: apiEvidence.graphNodeExplored,
        traceEvidenceVisible: apiEvidence.traceEvidenceVisible,
        browserErrors,
      },
    });
  });

  test("医疗引擎运营员在移动端可查询图谱且页面无根级横向溢出", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await ensureReadySession(page, "engine-operator", "platform");
    await page.goto("/advanced/graph");

    await expect(page.getByRole("heading", { name: "知识关系" })).toBeVisible();
    await selectKnowledgeProjection(page);
    await expect(page.getByRole("group", { name: "知识关系图" })).toBeVisible();
    await expectNoRootOverflow(page);
    await expectGraphIsInternallyScrollable(page);
    await page.evaluate(() => window.scrollTo(0, 0));

    const screenshotPath = testInfo.outputPath("graph-mobile.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("graph-mobile", {
      path: screenshotPath,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
  });
});

async function enableGraphProjection(page: Page) {
  await ensureReadySession(page, "platform-admin", "platform");
  const key = "medkernel.runtime.feature-flags.graph-projection.enabled";
  const response = await page.request.get(
    `${apiBase}/system/configs?prefix=${encodeURIComponent("medkernel.runtime.feature-flags")}`,
  );
  await expectOk(response, "读取图谱投影配置");
  const configs = (await response.json()).data as Array<{
    key: string;
    value: string;
    version: number;
  }>;
  const config = configs.find((item) => item.key === key);
  expect(config, "图谱投影配置必须由配置中心登记").toBeDefined();
  if (config?.value === "true") return;

  const update = await patchApi(page, `/system/configs/${encodeURIComponent(key)}`, {
    value: "true",
    reason: "D6 图谱真实链路验收启用投影",
    expectedVersion: config?.version,
    confirmedHighRisk: false,
  });
  await expectOk(update, "启用图谱投影");
}

async function seedActiveKnowledge(
  page: Page,
  observedStages: Set<string>,
  apiEvidence: SourceLineageApiEvidence,
): Promise<GraphKnowledgeSeed> {
  await ensureReadySession(page, "engine-operator", "platform");
  const suffix = Date.now();
  return createModelKnowledgeSeed(page, suffix, observedStages, apiEvidence);
}

async function rebuildKnowledgeProjection(page: Page, label: string) {
  const response = await postApi(page, "/projections/knowledge-graph/rebuild", {});
  await expectOk(response, label);
  return (await response.json()).data as { sourceCount: number; projectionCount: number };
}

async function createModelKnowledgeSeed(
  page: Page,
  suffix: number,
  observedStages: Set<string>,
  apiEvidence: SourceLineageApiEvidence,
): Promise<GraphKnowledgeSeed> {
  const source = await registerGraphKnowledgeSource(page, suffix, observedStages, apiEvidence);
  const identityCode = `e2e.graph.source-boundary.${suffix}`;
  const subject = "图谱投影验收来源边界知识";
  const generated = await postApi(page, "/engine/knowledge-production/generate", {
    sourceVersionId: source.sourceVersionId,
    targetPipeline: "PLATFORM_SOURCE",
    domain: "CLINICAL",
    items: [
      {
        assetType: "KNOWLEDGE",
        target: {
          targetIdentityId: null,
          newIdentity: { domain: "OTHER", subject, identityCode },
        },
      },
    ],
  });
  await expectOk(generated, "从受控来源生成图谱种子正式生产候选");
  const generation = (await generated.json()).data as {
    candidates?: Array<{ candidateRef?: string; jobCode?: string }>;
    skipped?: unknown[];
    blocked?: unknown[];
  };
  expect(generation.blocked ?? []).toHaveLength(0);
  expect(generation.skipped ?? []).toHaveLength(0);
  expect(generation.candidates ?? []).toHaveLength(1);
  apiEvidence.knowledgeCandidateSubmitted = true;
  const candidateRef = generation.candidates?.[0]?.candidateRef;
  const jobCode = generation.candidates?.[0]?.jobCode;
  expect(candidateRef).toBeTruthy();
  expect(jobCode).toBeTruthy();

  const identity = await getApiData<{ id: number }>(
    page,
    `/engine/knowledge/identities/by-code/${encodeURIComponent(identityCode)}`,
    "读取图谱种子知识身份",
  );
  const parsed = parseCandidateRef(candidateRef || "");
  expect(parsed.identityId).toBe(identity.id);
  const candidateView = await getApiData<{
    candidates: { items: Array<{ id: number; versionNo: string; status: string }> };
    classifications: Array<{ id: number; candidateVersionId: number }>;
  }>(
    page,
    `/engine/knowledge/identities/${identity.id}/candidates?page=1&size=20`,
    "读取图谱种子候选审核项",
  );
  const version = candidateView.candidates.items.find(
    (item) => item.versionNo === parsed.versionNo,
  );
  expect(version, "图谱种子候选版本必须物化").toBeDefined();
  const classification = candidateView.classifications.find(
    (item) => item.candidateVersionId === version?.id,
  );
  expect(classification, "图谱种子候选必须生成审核分类").toBeDefined();

  const citation = await postApi(page, "/engine/knowledge/citations", {
    assetVersionId: version?.id,
    sourceFragmentId: source.sourceFragmentId,
    relation: "DERIVED_FROM",
    weight: 100,
    startOffset: 0,
    endOffset: source.textExcerpt.length,
  });
  await expectOk(citation, "绑定图谱种子来源引用");
  const citationData = (await citation.json()).data as { id: number };
  expect(citationData.id, "来源引用必须返回 citationId").toBeTruthy();
  apiEvidence.citationBound = true;

  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobCode ?? "")}/publication-quality-records`,
    {
      candidateRef,
      identityId: identity.id,
      versionId: version?.id,
    },
  );
  await expectOk(qualityRecord, "生成图谱种子服务端发布质量记录");
  const quality = (await qualityRecord.json()).data as { id: number };

  const review = await postApi(page, `/engine/knowledge/candidates/${classification?.id}/review`, {
    ...apiContext(`e2e-graph-review-${suffix}`),
    decision: "APPROVE",
    reason: "D6 图谱投影验收：低风险来源边界候选已完成服务端质量门",
    qualityGateRecordId: quality.id,
  });
  await expectOk(review, "审核激活图谱种子知识");
  apiEvidence.candidateApproved = true;
  recordSourceLineageStage(observedStages, "真实提交并审核激活带来源引用的知识候选");

  const seedBase = {
    ...source,
    identityId: identity.id,
    identityCode,
    versionId: version?.id ?? 0,
    subject,
    citationId: citationData.id,
    candidateRef: candidateRef ?? "",
    jobCode: jobCode ?? "",
    classificationId: classification?.id ?? 0,
    qualityGateRecordId: quality.id,
  };
  const seed = {
    ...seedBase,
    provenanceReadback: await assertSourceLineageReadback(page, seedBase),
  };
  apiEvidence.provenanceReadback = true;
  recordSourceLineageStage(observedStages, "真实绑定来源引用并回读血缘证据");

  return seed;
}

async function registerGraphKnowledgeSource(
  page: Page,
  suffix: number,
  observedStages: Set<string>,
  apiEvidence: SourceLineageApiEvidence,
): Promise<GraphKnowledgeSource> {
  const sourceCode = `E2E-GRAPH-SOURCE-${suffix}`;
  const versionNo = `2026-e2e-${suffix}`;
  const anchorPath = "section:source-boundary";
  const anchorLabel = "来源边界";
  const textExcerpt = `图谱投影验收来源边界 ${suffix}：本材料只验证 MedKernel 关系库权威知识到知识关系投影的真实链路。`;
  const content = `${textExcerpt}\n不得由此推断诊断、处方、剂量、阈值或自动医嘱。`;
  const sourceVersionHash = sha256(content);
  const fragmentHash = sha256(textExcerpt);
  const source = await postApi(page, "/engine/knowledge/sources", {
    ...apiContext(`e2e-graph-source-${suffix}`),
    sourceCode,
    sourceType: "GUIDELINE",
    authorityLevel: "B_GUIDELINE",
    authorityBasis: "D6 图谱投影验收受控来源",
    title: "图谱投影验收来源边界",
    publisher: "MedKernel 上线验收",
    license: "受控验收材料",
    language: "zh-CN",
  });
  await expectOk(source, "登记图谱种子受控来源");
  const sourceDocument = (await source.json()).data as { id: number };
  apiEvidence.sourceRegistered = true;

  const version = await postApi(page, `/engine/knowledge/sources/${sourceDocument.id}/versions`, {
    ...apiContext(`e2e-graph-source-version-${suffix}`),
    versionNo,
    publishedAt: "2026-06-25T00:00:00Z",
    contentHash: sourceVersionHash,
    fileUri: `repository://e2e/graph-source-boundary-${suffix}`,
    language: "zh-CN",
    content,
  });
  await expectOk(version, "登记图谱种子来源版本");
  const sourceVersion = (await version.json()).data as { id: number };
  apiEvidence.sourceVersionRegistered = true;

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId: sourceVersion.id,
    anchorPath,
    anchorLabel,
    textExcerpt,
  });
  await expectOk(fragment, "登记图谱种子来源锚点");
  const sourceFragment = (await fragment.json()).data as { id: number };
  apiEvidence.sourceFragmentRegistered = true;
  recordSourceLineageStage(observedStages, "真实登记受控来源、版本和锚点");

  return {
    sourceDocumentId: sourceDocument.id,
    sourceVersionId: sourceVersion.id,
    sourceCode,
    sourceVersionNo: versionNo,
    sourceVersionHash,
    sourceFragmentId: sourceFragment.id,
    fragmentHash,
    anchorPath,
    anchorLabel,
    sourceRef: `${sourceCode}:${versionNo}:${anchorPath}`,
    textExcerpt,
    content,
  };
}

async function assertSourceLineageReadback(
  page: Page,
  seed: Omit<GraphKnowledgeSeed, "provenanceReadback">,
): Promise<SourceLineageProvenanceEvidence> {
  const provenance = await getApiData<{
    identity: { id: number; identityCode: string };
    currentVersionId?: number | null;
    versions: { items?: Array<{ id: number; status: string }> };
    sourceEvidence: Array<{
      assetVersionId: number;
      citationId: number;
      sourceFragmentId: number;
      sourceDocumentId: number;
      sourceVersionId: number;
      sourceCode: string;
      sourceTitle: string;
      sourceType: string;
      authorityLevel?: string | null;
      authorityLabel: string;
      authorityBasis?: string | null;
      sourceVersionNo?: string | null;
      sourceVersionHash?: string | null;
      anchorPath?: string | null;
      anchorLabel?: string | null;
      textExcerpt?: string | null;
      fragmentHash?: string | null;
      startOffset?: number | null;
      endOffset?: number | null;
      publishedAt?: string | null;
      relation?: string | null;
      weight?: number | null;
      displayRole: string;
      recommendedByDefault: boolean;
      supplementary: boolean;
      displayLabel: string;
      rankingReason: string;
    }>;
    unresolvedCitationCount: number;
    partial: boolean;
  }>(
    page,
    `/engine/knowledge/identities/${seed.identityId}/provenance?page=1&size=20`,
    "回读图谱种子来源血缘",
  );

  expect(provenance.identity.id).toBe(seed.identityId);
  expect(provenance.identity.identityCode).toBe(seed.identityCode);
  expect(provenance.currentVersionId).toBe(seed.versionId);
  expect(provenance.partial, "本轮来源引用必须完整解析").toBe(false);
  expect(provenance.unresolvedCitationCount, "本轮来源引用不得产生未解析引用").toBe(0);
  const activeVersion = provenance.versions.items?.find(
    (item) => item.id === provenance.currentVersionId,
  );
  expect(activeVersion?.status).toBe("ACTIVE");

  const evidence = provenance.sourceEvidence.find((item) => item.citationId === seed.citationId);
  expect(evidence, "血缘证据必须包含本轮 citationId").toBeDefined();
  expect(evidence?.assetVersionId).toBe(seed.versionId);
  expect(evidence?.sourceFragmentId).toBe(seed.sourceFragmentId);
  expect(evidence?.sourceDocumentId).toBe(seed.sourceDocumentId);
  expect(evidence?.sourceVersionId).toBe(seed.sourceVersionId);
  expect(evidence?.sourceCode).toBe(seed.sourceCode);
  expect(evidence?.sourceTitle).toBe("图谱投影验收来源边界");
  expect(evidence?.sourceType).toBe("GUIDELINE");
  expect(evidence?.authorityLevel).toBe("B_GUIDELINE");
  expect(evidence?.authorityLabel).toBe("B 指南");
  expect(evidence?.authorityBasis).toBe("D6 图谱投影验收受控来源");
  expect(evidence?.sourceVersionNo).toBe(seed.sourceVersionNo);
  expect(evidence?.sourceVersionHash).toBe(seed.sourceVersionHash);
  expect(evidence?.anchorPath).toBe(seed.anchorPath);
  expect(evidence?.anchorLabel).toBe(seed.anchorLabel);
  expect(evidence?.textExcerpt).toBe(seed.textExcerpt);
  expect(evidence?.fragmentHash).toBe(seed.fragmentHash);
  expect(evidence?.startOffset).toBe(0);
  expect(evidence?.endOffset).toBe(seed.textExcerpt.length);
  expect(evidence?.publishedAt).toBe("2026-06-25T00:00:00Z");
  expect(evidence?.relation).toBe("DERIVED_FROM");
  expect(evidence?.weight).toBe(100);
  expect(evidence?.displayRole).toBe("PRIMARY");
  expect(evidence?.recommendedByDefault).toBe(true);
  expect(evidence?.supplementary).toBe(false);
  expect(evidence?.displayLabel).toContain("主证据");
  expect(evidence?.rankingReason).toContain("引用权重 100");

  const citations = await getApiData<
    Array<{
      id: number;
      assetVersionId: number;
      sourceFragmentId: number;
      relation: string;
      weight?: number | null;
      startOffset?: number | null;
      endOffset?: number | null;
    }>
  >(page, `/engine/knowledge/identities/${seed.identityId}/citations`, "回读图谱种子来源引用");
  const citation = citations.find((item) => item.id === seed.citationId);
  expect(citation, "当前 ACTIVE 版本必须包含本轮结构化引用").toBeDefined();
  expect(citation?.assetVersionId).toBe(seed.versionId);
  expect(citation?.sourceFragmentId).toBe(seed.sourceFragmentId);
  expect(citation?.relation).toBe("DERIVED_FROM");
  expect(citation?.weight).toBe(100);
  expect(citation?.startOffset).toBe(0);
  expect(citation?.endOffset).toBe(seed.textExcerpt.length);

  return {
    identityId: provenance.identity.id,
    identityCode: provenance.identity.identityCode,
    currentVersionId: provenance.currentVersionId ?? 0,
    activeVersionStatus: activeVersion?.status ?? "",
    partial: provenance.partial,
    unresolvedCitationCount: provenance.unresolvedCitationCount,
    citationId: evidence?.citationId ?? 0,
    sourceFragmentId: evidence?.sourceFragmentId ?? 0,
    sourceDocumentId: evidence?.sourceDocumentId ?? 0,
    sourceVersionId: evidence?.sourceVersionId ?? 0,
    sourceCode: evidence?.sourceCode ?? "",
    sourceType: evidence?.sourceType ?? "",
    authorityLevel: evidence?.authorityLevel ?? "",
    authorityLabel: evidence?.authorityLabel ?? "",
    sourceVersionNo: evidence?.sourceVersionNo ?? "",
    sourceVersionHash: evidence?.sourceVersionHash ?? "",
    anchorPath: evidence?.anchorPath ?? "",
    anchorLabel: evidence?.anchorLabel ?? "",
    fragmentHash: evidence?.fragmentHash ?? "",
    relation: evidence?.relation ?? "",
    weight: evidence?.weight ?? 0,
  };
}

async function assertSeedProjected(page: Page, seed: GraphKnowledgeSeed) {
  const identityFacts = await getProjectionFacts(page, `KNOWLEDGE_IDENTITY:${seed.identityId}`);
  expect(
    identityFacts.some(
      (item) => item.factKind === "NODE" && item.objectType === "KNOWLEDGE_IDENTITY",
    ),
    `知识身份 ${seed.identityId} 必须进入知识关系投影`,
  ).toBe(true);
  expect(
    identityFacts.some(
      (item) =>
        item.factKind === "EDGE" &&
        item.subjectKey === `KNOWLEDGE_IDENTITY:${seed.identityId}` &&
        item.predicate === "HAS_ACTIVE_VERSION" &&
        item.objectKey === `KNOWLEDGE_VERSION:${seed.versionId}`,
    ),
    `知识身份 ${seed.identityCode} 必须投影到当前 ACTIVE 版本关系`,
  ).toBe(true);

  const versionFacts = await getProjectionFacts(page, `KNOWLEDGE_VERSION:${seed.versionId}`);
  expect(
    versionFacts.some(
      (item) =>
        item.factKind === "EDGE" &&
        item.subjectKey === `KNOWLEDGE_VERSION:${seed.versionId}` &&
        item.predicate === "CITES_FRAGMENT" &&
        item.objectKey === `SOURCE_FRAGMENT:${seed.sourceFragmentId}`,
    ),
    `知识版本 ${seed.versionId} 必须投影到本轮来源片段`,
  ).toBe(true);

  const fragmentFacts = await getProjectionFacts(page, `SOURCE_FRAGMENT:${seed.sourceFragmentId}`);
  expect(
    fragmentFacts.some((item) => item.factKind === "NODE" && item.objectType === "SOURCE_FRAGMENT"),
    `来源片段 ${seed.sourceFragmentId} 必须进入知识关系投影`,
  ).toBe(true);
  expect(
    fragmentFacts.some(
      (item) =>
        item.factKind === "EDGE" &&
        item.subjectKey === `SOURCE_FRAGMENT:${seed.sourceFragmentId}` &&
        item.predicate === "BELONGS_TO_SOURCE" &&
        item.objectKey === `SOURCE_DOCUMENT:${seed.sourceDocumentId}`,
    ),
    `来源片段 ${seed.sourceFragmentId} 必须投影到来源文档 ${seed.sourceDocumentId}`,
  ).toBe(true);
}

async function getProjectionFacts(page: Page, keyword: string) {
  const result = await getApiData<{
    items: Array<{
      factKind: string;
      objectType: string;
      objectId: string;
      subjectKey?: string | null;
      predicate?: string | null;
      objectKey?: string | null;
      traceId?: string | null;
    }>;
  }>(
    page,
    `/projections/knowledge-graph/facts?keyword=${encodeURIComponent(keyword)}&page=1&size=40`,
    `读取知识关系投影事实 ${keyword}`,
  );
  return result.items ?? [];
}

async function getApiData<T>(page: Page, path: string, label: string): Promise<T> {
  const response = await page.request.get(`${apiBase}${path}`, {
    headers: { "X-Trace-Id": `e2e-get-${Date.now()}` },
  });
  await expectOk(response, label);
  return (await response.json()).data as T;
}

function apiContext(traceId: string) {
  return {
    request_id: traceId,
    trace_id: traceId,
    tenant_id: "t-1",
    user_id: "engine-operator",
    role_codes: ["engine-operator"],
  };
}

function parseCandidateRef(candidateRef: string) {
  const parts = candidateRef.split(":");
  if (parts.length < 3 || parts[0] !== "kv") {
    throw new Error(`候选引用格式非法：${candidateRef}`);
  }
  return { identityId: Number(parts[1]), versionNo: parts.slice(2).join(":") };
}

function sha256(value: string) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

async function selectKnowledgeProjection(page: Page) {
  await page.locator('div.ant-select[aria-label="关系范围"]').click();
  const option = page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option")
    .filter({ hasText: "知识关系" });
  await expect(option).toBeVisible();
  await option.click();
  await expect(page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)")).toHaveCount(
    0,
  );
}

async function ensureEvidenceDetailsEnabled(page: Page) {
  const toggle = page.getByRole("switch", { name: "证据详情" });
  await expect(toggle).toBeVisible();
  if ((await toggle.getAttribute("aria-checked")) !== "true") {
    await toggle.click();
  }
}

function recordSourceLineageStage(observedStages: Set<string>, stage: string) {
  observedStages.add(stage);
}

async function attachSourceLineageScenarioEvidence(
  testInfo: TestInfo,
  observedStageSet: Set<string>,
  apiEvidence: SourceLineageApiEvidence,
  structuredEvidence: Record<string, unknown>,
) {
  const scenarioEvidence = requiredSourceLineageScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages =
        requiredSourceLineageScenarioEvidence.find((item) => item.code === scenario.code)
          ?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);

  await testInfo.attach("source-lineage-scenario-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: completedScenarioCodes,
        semanticFamilies: ["SOURCE_VALIDITY"],
        apiEvidence,
        ...structuredEvidence,
        context: structuredEvidence,
        scenarioConditionEvidence: [
          {
            code: "S7__NORMAL",
            scenarioCode: "S7",
            condition: "NORMAL",
            source: "SOURCE_LINEAGE_GRAPH_PROVENANCE_READBACK",
            evidence: [
              "医疗引擎运营员登记受控来源、版本和锚点并审核激活带来源引用的知识候选",
              "后端回读完整 provenance，前台重建并探索知识关系图且追踪证据可见",
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

async function expectNoRootOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewportWidth: document.documentElement.clientWidth,
    documentWidth: document.documentElement.scrollWidth,
  }));
  expect(dimensions.documentWidth).toBeLessThanOrEqual(dimensions.viewportWidth);
}

async function expectGraphIsInternallyScrollable(page: Page) {
  const dimensions = await page.getByRole("group", { name: "知识关系图" }).evaluate((svg) => {
    const viewport = svg.parentElement?.parentElement;
    return {
      clientWidth: viewport?.clientWidth ?? 0,
      scrollWidth: viewport?.scrollWidth ?? 0,
    };
  });
  expect(dimensions.scrollWidth).toBeGreaterThan(dimensions.clientWidth);
}
