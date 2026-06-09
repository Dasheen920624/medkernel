import { expect, test, type Page } from "@playwright/test";
import { createHash } from "node:crypto";

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
  test("实施工程师重建后，专科专家可从登录页探索真实投影且不能重建", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);

    await enableGraphProjection(page);
    await seedActiveKnowledge(page);
    await ensureReadySession(page, "implementation-engineer");
    const rebuild = await postApi(page, "/projections/knowledge-graph/rebuild", {});
    await expectOk(rebuild, "重建知识关系投影");
    const rebuilt = (await rebuild.json()).data;
    expect(rebuilt.sourceCount).toBeGreaterThan(0);
    expect(rebuilt.projectionCount).toBe(rebuilt.sourceCount);

    await ensureReadySession(page, "specialist");
    await loginFromPlatformPage(page, "specialist");

    await page.goto("/advanced/graph");
    await expect(page.getByRole("heading", { name: "图谱查询" })).toBeVisible();
    await selectKnowledgeProjection(page);
    await expect(page.getByRole("group", { name: "投影关系图" })).toBeVisible();
    await expect(page.getByRole("button", { name: "重建投影" })).toHaveCount(0);

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

  test("专科专家在移动端可查询图谱且页面无根级横向溢出", async ({ page }, testInfo) => {
    const browserErrors = collectBrowserErrors(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await ensureReadySession(page, "specialist");
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
  await ensureReadySession(page, "it-ops");
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
  await ensureReadySession(page, "medical-affairs");
  const suffix = Date.now();
  const create = await postApi(page, "/engine/knowledge/diagnosis/assets", {
    request_id: `e2e-graph-${suffix}`,
    trace_id: `e2e-graph-${suffix}`,
    tenant_id: "t-1",
    user_id: "medical-affairs",
    role_codes: ["medical-affairs"],
    package_version: "2026.06",
    identity: {
      identitySlug: `e2e-graph-${suffix}`,
      subject: "图谱真实链路验收知识",
      assetSpecialtyId: "GENERAL",
      description: "用于验证关系库权威知识到图投影查询的真实链路",
    },
    source: {
      sourceCode: `E2E.SOURCE.${suffix}`,
      sourceType: "GUIDELINE",
      authorityLevel: "B_GUIDELINE",
      authorityBasis: "受控验收知识来源",
      title: "图谱真实链路验收指南",
      publisher: "MedKernel 质量验收",
      license: "受控测试许可",
      language: "zh-CN",
      versionNo: String(suffix),
      publishedAt: "2026-06-07T00:00:00Z",
      fileUri: `repository://e2e/graph-${suffix}`,
      content: `图谱真实链路验收原文 ${suffix}。发热与咳嗽构成验收发现集。`,
    },
    version: {
      versionNo: String(suffix),
      versionLabel: "真实链路验收版",
      riskLevel: "HIGH",
      gradeQuality: "HIGH",
      gradeStrength: "STRONG",
      reviewCycleMonths: 12,
    },
    evidence: {
      anchorPath: "section-1",
      anchorLabel: "验收标准",
      textExcerpt: `图谱真实链路验收原文 ${suffix}`,
    },
  });
  await expectOk(create, "创建真实知识资产");
  const draft = (await create.json()).data;
  const identityId = draft.identity.id;
  const versionId = draft.version.id;

  for (const criterion of [
    { findingTermCode: "FEVER", direction: "REQUIRED", weight: "MAJOR" },
    { findingTermCode: "COUGH", direction: "SUPPORTING", weight: "MAJOR" },
  ]) {
    const response = await postApi(
      page,
      `/engine/knowledge/diagnosis/versions/${versionId}/criteria`,
      criterion,
    );
    await expectOk(response, `新增诊断标准 ${criterion.findingTermCode}`);
  }

  const testCase = await postApi(
    page,
    `/engine/knowledge/diagnosis/versions/${versionId}/test-cases`,
    {
      caseCode: `E2E-GRAPH-${suffix}`,
      findings: "FEVER,COUGH",
      expectedIdentityId: identityId,
      expectedConfidence: "STRONG",
    },
  );
  await expectOk(testCase, "新增知识发布回归病例");

  const publishReason = "真实图谱链路验收";
  await ensureReadySession(page, "platform-admin");
  const publish = await postApi(
    page,
    `/engine/knowledge/diagnosis/identities/${identityId}/versions/${versionId}/publish`,
    {
      reason: publishReason,
      publishEvidence: {
        electronicSignature: {
          signatureId: `sig-e2e-graph-${suffix}`,
          signerId: "platform-admin-1",
          signerName: "平台管理员验收账号",
          signedAt: new Date().toISOString(),
          signatureHash: createHash("sha256")
            .update(`${identityId}|${versionId}|${publishReason}`)
            .digest("hex"),
        },
        qualityGate: {
          schemaValid: true,
          terminologyBindingComplete: true,
          dependencyIntegrityVerified: true,
          safetyMonotonicityVerified: true,
          impactSimulationPassed: true,
          peerReviewSigned: true,
          summary: "E2E 已完成结构、术语、依赖、安全、影响模拟与同行复核门禁",
        },
      },
    },
  );
  await expectOk(publish, "发布真实知识资产");
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
