import { createHash } from "node:crypto";

import { expect, test, type Page } from "@playwright/test";

import {
  apiBase,
  ensureReadySession,
  expectOk,
  loginFromPlatformPage,
  patchApi,
  postApi,
} from "./support/auth";

test.describe.configure({ mode: "serial" });

test.describe("D6 图谱查询真实验收", () => {
  test("医疗引擎运营员可重建并探索真实知识投影", async ({
    page,
  }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);

    await enableGraphProjection(page);
    await seedActiveKnowledge(page);
    await ensureReadySession(page, "engine-operator");
    const rebuild = await postApi(page, "/projections/knowledge-graph/rebuild", {});
    await expectOk(rebuild, "重建知识关系投影");
    const rebuilt = (await rebuild.json()).data;
    expect(rebuilt.sourceCount).toBeGreaterThan(0);
    expect(rebuilt.projectionCount).toBe(rebuilt.sourceCount);

    await ensureReadySession(page, "engine-operator");
    await loginFromPlatformPage(page, "engine-operator");

    await page.goto("/advanced/graph");
    await expect(page.getByRole("heading", { name: "图谱查询" })).toBeVisible();
    await selectKnowledgeProjection(page);
    await expect(page.getByRole("group", { name: "投影关系图" })).toBeVisible();
    await expect(page.getByRole("button", { name: "重建投影" })).toBeVisible();

    const nodes = page.locator('svg[aria-label="投影关系图"] g[role="button"]');
    expect(await nodes.count()).toBeGreaterThan(0);
    await nodes.first().click();
    const detail = page.locator("aside");
    await expect(detail.getByText("追踪号", { exact: true })).toBeVisible();
    await expect(detail.getByText("未返回", { exact: true })).toHaveCount(0);
    await expectNoRootOverflow(page);
    await page.evaluate(() => window.scrollTo(0, 0));

    const screenshotPath = testInfo.outputPath("graph-desktop.png");
    await page.screenshot({ path: screenshotPath, fullPage: true });
    await testInfo.attach("graph-desktop", {
      path: screenshotPath,
      contentType: "image/png",
    });
    expect(browserErrors).toEqual([]);
  });

  test("医疗引擎运营员在移动端可查询图谱且页面无根级横向溢出", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await ensureReadySession(page, "engine-operator");
    await page.goto("/advanced/graph");

    await expect(page.getByRole("heading", { name: "图谱查询" })).toBeVisible();
    await selectKnowledgeProjection(page);
    await expect(page.getByRole("group", { name: "投影关系图" })).toBeVisible();
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
  await ensureReadySession(page, "platform-admin");
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

async function seedActiveKnowledge(page: Page) {
  await ensureReadySession(page, "engine-operator");
  const existing = await rebuildKnowledgeProjection(page, "检查已有知识关系投影源");
  if (existing.sourceCount > 0) return;

  const suffix = Date.now();
  const seed = await createModelKnowledgeSeed(page, suffix);
  const rebuilt = await rebuildKnowledgeProjection(page, "种子知识发布后重建知识关系投影");
  expect(rebuilt.sourceCount, `图谱种子知识 ${seed.identityCode} 必须进入投影源`).toBeGreaterThan(0);
}

async function rebuildKnowledgeProjection(page: Page, label: string) {
  const response = await postApi(page, "/projections/knowledge-graph/rebuild", {});
  await expectOk(response, label);
  return (await response.json()).data as { sourceCount: number; projectionCount: number };
}

async function createModelKnowledgeSeed(page: Page, suffix: number) {
  const source = await registerGraphKnowledgeSource(page, suffix);
  const identityCode = `e2e.graph.source-boundary.${suffix}`;
  const subject = "图谱投影验收来源边界知识";
  const job = await postApi(page, "/engine/knowledge-production/jobs", {
    sourceScope: source.sourceRef,
    assetType: "KNOWLEDGE",
    producer: "API_MODEL",
    targetPipeline: "PLATFORM_SOURCE",
    domain: "CLINICAL",
    modelStrategy: "FORMAL_KNOWLEDGE",
  });
  await expectOk(job, "创建图谱种子正式模型生产任务");
  const jobData = (await job.json()).data as { jobCode: string };

  const generated = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobData.jobCode)}/model-candidates`,
    {
      capabilityCode: "knowledge.production.knowledge",
      prompt: buildGraphSeedPrompt(source.sourceRef, subject),
      providerCode: graphSeedProviderCode(),
      timeoutSeconds: 120,
      assetIdentity: identityCode,
      subject,
      sources: [{ sourceRef: source.sourceRef, authorityLevel: "B_GUIDELINE" }],
      trustLevel: "B_GUIDELINE",
      riskLevel: "LOW",
      target: {
        targetIdentityId: null,
        newIdentity: { domain: "OTHER", subject, identityCode },
      },
    },
  );
  await expectOk(generated, "生成图谱种子正式模型候选");
  const generation = (await generated.json()).data as {
    modelMode?: string;
    modelVersion?: string;
    summary?: {
      candidates?: Array<{ candidateRef: string; jobCode: string }>;
      skipped?: unknown[];
      blocked?: unknown[];
    };
  };
  expect(generation.modelMode?.toUpperCase()).not.toBe("B0");
  expect(generation.modelVersion).toBeTruthy();
  expect(generation.summary?.blocked ?? []).toHaveLength(0);
  expect(generation.summary?.skipped ?? []).toHaveLength(0);
  expect(generation.summary?.candidates ?? []).toHaveLength(1);
  const candidateRef = generation.summary?.candidates?.[0]?.candidateRef;
  expect(candidateRef).toBeTruthy();

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
    sourceFragmentId: source.fragmentId,
    relation: "DERIVED_FROM",
    weight: 100,
    startOffset: 0,
    endOffset: source.textExcerpt.length,
  });
  await expectOk(citation, "绑定图谱种子来源引用");

  const qualityRecord = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobData.jobCode)}/publication-quality-records`,
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

  const complete = await postApi(
    page,
    `/engine/knowledge-production/jobs/${encodeURIComponent(jobData.jobCode)}/complete`,
    {},
  );
  await expectOk(complete, "完成图谱种子生产任务");

  return { identityCode };
}

async function registerGraphKnowledgeSource(page: Page, suffix: number) {
  const sourceCode = `E2E-GRAPH-SOURCE-${suffix}`;
  const versionNo = `2026-e2e-${suffix}`;
  const anchorPath = "section:source-boundary";
  const textExcerpt = `图谱投影验收来源边界 ${suffix}：本材料只验证 MedKernel 关系库权威知识到知识关系投影的真实链路。`;
  const content = `${textExcerpt}\n不得由此推断诊断、处方、剂量、阈值或自动医嘱。`;
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

  const version = await postApi(
    page,
    `/engine/knowledge/sources/${sourceDocument.id}/versions`,
    {
      ...apiContext(`e2e-graph-source-version-${suffix}`),
      versionNo,
      publishedAt: "2026-06-25T00:00:00Z",
      contentHash: sha256(content),
      fileUri: `repository://e2e/graph-source-boundary-${suffix}`,
      language: "zh-CN",
      content,
    },
  );
  await expectOk(version, "登记图谱种子来源版本");
  const sourceVersion = (await version.json()).data as { id: number };

  const fragment = await postApi(page, "/engine/knowledge/sources/fragments", {
    sourceVersionId: sourceVersion.id,
    anchorPath,
    anchorLabel: "来源边界",
    textExcerpt,
  });
  await expectOk(fragment, "登记图谱种子来源锚点");
  const sourceFragment = (await fragment.json()).data as { id: number };

  return {
    fragmentId: sourceFragment.id,
    sourceRef: `${sourceCode}:${versionNo}:${anchorPath}`,
    textExcerpt,
  };
}

function buildGraphSeedPrompt(sourceRef: string, subject: string) {
  const template = {
    domain: "OTHER",
    subject,
    clinicalActionable: false,
    sourceReferences: [{ sourceRef, authorityLevel: "B_GUIDELINE", anchorLabel: "来源边界" }],
    limitations: [
      "本候选仅用于 MedKernel 图谱投影验收，不构成诊断、处方、剂量、阈值或自动医嘱。",
      "正式临床内容必须绑定具体原始文件、机构版本、适用范围和人工审核结论。",
    ],
    sections: {
      sourceBoundary: "来源边界：仅验证关系库权威知识进入知识关系投影；不可推断医学结论。",
      clinicalLimit: "正式临床内容不可推断，必须由人工审核后按来源和版本另行编著。",
    },
  };
  return [
    "只返回一个合法 JSON 对象，不要 Markdown、代码围栏或额外说明；第一个字符必须是 {，最后一个字符必须是 }。",
    `主题：${subject}；唯一受控来源：${sourceRef}。`,
    "目标是生成低风险来源边界说明，禁止生成诊断、处方、剂量、阈值、治疗建议、患者事实或自动医嘱。",
    "必须严格按以下 JSON 返回，顶层字段不得增删：",
    JSON.stringify(template, null, 2),
  ].join("\n");
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

function graphSeedProviderCode() {
  return (
    process.env.E2E_GRAPH_SEED_PROVIDER_CODE?.trim() ||
    process.env.E2E_KNOWLEDGE_PROVIDER_CODE?.trim() ||
    "ollama-launch"
  );
}

function sha256(value: string) {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

async function selectKnowledgeProjection(page: Page) {
  await page.locator('div.ant-select[aria-label="投影目标"]').click();
  const option = page
    .locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option")
    .filter({ hasText: "知识关系投影" });
  await expect(option).toBeVisible();
  await option.click();
  await expect(page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)")).toHaveCount(
    0,
  );
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
  const dimensions = await page.getByRole("group", { name: "投影关系图" }).evaluate((svg) => {
    const viewport = svg.parentElement?.parentElement;
    return {
      clientWidth: viewport?.clientWidth ?? 0,
      scrollWidth: viewport?.scrollWidth ?? 0,
    };
  });
  expect(dimensions.scrollWidth).toBeGreaterThan(dimensions.clientWidth);
}
