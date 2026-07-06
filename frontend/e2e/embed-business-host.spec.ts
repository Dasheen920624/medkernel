import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";

import { apiBase, appPath, ensureReadySession, expectOk, postApi } from "./support/auth";

const hostOrigin = "http://127.0.0.1:4174";

const requiredEmbedBusinessHostScenarioEvidence = [
  {
    code: "S8",
    observedStages: [
      "真实签发一次性嵌入启动凭证",
      "独立业务系统宿主加载真实 iframe 启动地址",
      "嵌入终端真实兑换启动凭证并读取当前就诊上下文",
      "嵌入终端真实读取当前就诊推荐卡",
      "医师在嵌入终端提交采纳反馈",
      "独立业务系统宿主收到医师反馈 postMessage",
    ],
  },
];

type EmbedApiEvidence = {
  launchTokenIssued: boolean;
  launchExchanged: boolean;
  recommendationsRead: boolean;
  feedbackSubmitted: boolean;
  hostMessageReceived: boolean;
};

type ContextSnapshotSummary = {
  patientId: string;
  snapshotId: string;
  encounterId: string | null;
};

type RecommendationEvaluationPayload = {
  status?: string;
  visibleCardCount?: number;
  suppressedCardCount?: number;
  traceId?: string;
  cards?: Array<{
    cardId?: string;
    title?: string;
    sourceSummary?: string;
  }>;
};

type EmbedLaunchTokenPayload = {
  token?: string;
  embedUrl?: string;
  hook?: string;
  hookInstance?: string;
  integrationMode?: string;
};

type EmbedFeedbackPayload = {
  token?: string;
  cardId?: string;
  actionType?: string;
  recommendationStatus?: string;
  callbackStatus?: string;
  callbackDelivered?: boolean;
  degradationReason?: string | null;
  traceId?: string;
};

test("独立业务系统宿主通过真实嵌入凭证完成 iframe 启动并接收医师反馈", async ({
  page,
}, testInfo) => {
  test.setTimeout(180_000);
  const observedStages = new Set<string>();
  const apiEvidence: EmbedApiEvidence = {
    launchTokenIssued: false,
    launchExchanged: false,
    recommendationsRead: false,
    feedbackSubmitted: false,
    hostMessageReceived: false,
  };
  await ensureReadySession(page, "clinical-user");
  const snapshot = await createClinicalContextFromFrontdesk(page);
  const card = await createEmbeddedRecommendationCard(page, snapshot);

  await upsertEmbedOrigin(page);
  const token = await issueEmbedLaunchToken(page, snapshot, card.cardId);
  apiEvidence.launchTokenIssued = true;
  recordEmbedBusinessHostStage(observedStages, "真实签发一次性嵌入启动凭证");

  const apiResponses = collectEmbedApiResponses(page, apiEvidence);
  const browserErrors = collectBrowserErrors(page);
  const serverErrors = collectServerErrors(page);
  const networkFailures = collectNetworkFailures(page);

  await page.goto(`${hostOrigin}/?token=${encodeURIComponent(token.token ?? "")}`);
  await expect(page.getByRole("heading", { name: "院内业务系统工作站" })).toBeVisible();
  const frame = page.frameLocator('iframe[title="MedKernel 临床建议"]');
  await expect(frame.getByText("MedKernel 临床建议已连接")).toBeVisible({ timeout: 30_000 });
  await expect(frame.getByText("检验危急值需人工确认")).toBeVisible({ timeout: 30_000 });
  recordEmbedBusinessHostStage(observedStages, "独立业务系统宿主加载真实 iframe 启动地址");
  recordEmbedBusinessHostStage(observedStages, "嵌入终端真实兑换启动凭证并读取当前就诊上下文");
  recordEmbedBusinessHostStage(observedStages, "嵌入终端真实读取当前就诊推荐卡");
  const targetRecommendationCard = embeddedRecommendationCard(frame, "检验危急值需人工确认");

  const feedbackResponse = page.waitForResponse(
    (response) =>
      response.request().method() === "POST" &&
      response.url().includes("/medkernel/api/v1/engine/embed/feedback"),
    { timeout: 30_000 },
  );
  await targetRecommendationCard.getByRole("button", { name: /采纳建议/ }).click();
  const response = await feedbackResponse;
  const responseText = await response.text();
  expect(
    response.ok(),
    `嵌入终端真实提交医师反馈应成功 status=${response.status()} body=${responseText}`,
  ).toBe(true);
  const feedback = JSON.parse(responseText) as { data?: EmbedFeedbackPayload };
  expect(feedback.data?.cardId, "嵌入反馈必须回写本轮真实推荐卡").toBe(card.cardId);
  expect(feedback.data?.actionType, "嵌入反馈必须记录医师采纳动作").toBe("ADOPT");
  expect(feedback.data?.recommendationStatus, "嵌入反馈必须推进推荐卡状态").toBe("ACCEPTED");
  expect(feedback.data?.callbackStatus, "宿主回调缺配置时必须诚实降级").toBe("NOT_CONNECTED");
  recordEmbedBusinessHostStage(observedStages, "医师在嵌入终端提交采纳反馈");

  const hostFeedback = page.getByTestId("host-feedback");
  await expect(hostFeedback).toContainText("ADOPT", { timeout: 20_000 });
  await expect(hostFeedback).toContainText(card.cardId);
  await expect(hostFeedback).toContainText(snapshot.patientId);
  await expect(hostFeedback).toContainText(snapshot.encounterId ?? "");
  recordEmbedBusinessHostStage(observedStages, "独立业务系统宿主收到医师反馈 postMessage");
  apiEvidence.hostMessageReceived = true;

  expect(apiResponses, "嵌入宿主必须调用真实 embed 后端接口").toEqual(
    expect.arrayContaining([
      expect.stringContaining("POST /medkernel/api/v1/engine/embed/launch 200"),
      expect.stringContaining("POST /medkernel/api/v1/engine/embed/recommendations 200"),
      expect.stringContaining("POST /medkernel/api/v1/engine/embed/feedback 200"),
    ]),
  );
  expect(serverErrors, "真实嵌入链路不应产生 HTTP 错误").toEqual([]);
  expect(browserErrors, "真实嵌入链路不应产生浏览器错误").toEqual([]);
  expect(networkFailures, "真实嵌入链路不应产生网络失败").toEqual([]);

  await attachEmbedBusinessHostScenarioEvidence(testInfo, observedStages, apiEvidence, {
    patientId: snapshot.patientId,
    encounterId: snapshot.encounterId,
    triggerPoint: "patient-view",
    hook: token.hook ?? "patient-view",
    hookInstance: token.hookInstance ?? null,
    cardId: card.cardId,
    recommendationTraceId: card.traceId,
    feedbackTraceId: feedback.data?.traceId ?? null,
    callbackStatus: feedback.data?.callbackStatus ?? null,
    degradationReason: feedback.data?.degradationReason ?? null,
  });
});

async function createClinicalContextFromFrontdesk(page: Page): Promise<ContextSnapshotSummary> {
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const idLast4 = String(Date.now()).slice(-4);
  const maskedName = `嵌*${idLast4.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const genderCombobox = patientDialog.getByRole("combobox", { name: "性别" });
  await genderCombobox.click();
  await genderCombobox.press("ArrowDown");
  await genderCombobox.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("66");
  await patientDialog.getByLabel("身份证后四位").fill(idLast4);

  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  const patientText = await patientResponse.text();
  expect(
    patientResponse.ok(),
    `嵌入宿主演练创建脱敏患者应成功 status=${patientResponse.status()} body=${patientText}`,
  ).toBe(true);
  const patient = JSON.parse(patientText) as { data?: { mpiId?: string } };
  const patientId = patient.data?.mpiId ?? "";
  expect(patientId, "脱敏患者创建响应必须返回 MPI").toBeTruthy();
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(maskedName)}.*${idLast4}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();

  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible();
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("嵌入宿主危急值复核主题");
  await chooseDialogOption(page, contextDialog, "风险分层", "高风险");
  await contextDialog.getByLabel("医技报告项目").fill("血钾检验");
  await contextDialog.getByLabel("报告结论").fill("血钾 6.3 mmol/L，危急值，已复核");
  await contextDialog.getByLabel("异常重点").fill("血钾升高、危急值");
  await contextDialog
    .getByLabel("建立原因")
    .fill("真实嵌入宿主演练：建立当前就诊上下文，用于工作站 iframe 建议和医师反馈闭环。");

  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  const contextText = await contextResponse.text();
  expect(
    contextResponse.ok(),
    `嵌入宿主演练建立当前就诊上下文应成功 status=${contextResponse.status()} body=${contextText}`,
  ).toBe(true);
  const context = JSON.parse(contextText) as {
    data?: {
      snapshotId?: string;
      resources?: { encounters?: Array<{ encounterId?: string }> };
    };
  };
  expect(context.data?.snapshotId, "上下文响应必须返回快照身份").toBeTruthy();
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  return {
    patientId,
    snapshotId: context.data?.snapshotId ?? "",
    encounterId: context.data?.resources?.encounters?.[0]?.encounterId ?? null,
  };
}

async function createEmbeddedRecommendationCard(page: Page, snapshot: ContextSnapshotSummary) {
  const response = await postApi(page, "/engine/recommendations:evaluate", {
    triggerCode: `embed-host-${Date.now()}`,
    triggerType: "patient-view",
    sourceEventId: `embed-host-source-${Date.now()}`,
    contextSnapshotId: snapshot.snapshotId,
    patientId: snapshot.patientId,
    encounterId: snapshot.encounterId,
    scenarioCode: "S8",
    candidateCards: [
      {
        cardCode: `EMBED_HOST_CRITICAL_${Date.now()}`,
        cardType: "LAB",
        title: "检验危急值需人工确认",
        summary: "血钾结果达到危急值，需医师复核并留痕。",
        suggestedAction: "STRONG_REMINDER",
        riskLevel: "CRITICAL",
        interruptLevel: "STRONG_INTERRUPTIVE",
        requiresPhysicianConfirmation: true,
        aiGenerated: false,
        sourceSummary: "嵌入宿主真实服务链路演练：检验危急值管理制度",
        explanationJson: JSON.stringify({
          embeddedHost: true,
          triggerPoint: "patient-view",
          source: "real-service-chain",
        }),
        fatigueKey: `EMBED_HOST:${snapshot.patientId}`,
        sources: [
          {
            sourceType: "MANUAL",
            sourceRefId: "embed-host-critical-potassium",
            sourceVersion: "2026",
            sourceTitle: "检验危急值管理制度",
            citationLocator: "危急值复核条款",
            sourceHash: "sha256:embed-host-critical-potassium",
            summary: "危急值提醒必须经医师确认并留痕。",
          },
        ],
      },
    ],
  });
  await expectOk(response, "创建嵌入宿主真实推荐卡");
  const payload = (await response.json()) as { data?: RecommendationEvaluationPayload };
  expect(payload.data?.status, "嵌入宿主推荐评估必须完成").toBe("EVALUATED");
  expect(payload.data?.visibleCardCount ?? 0, "嵌入宿主推荐卡必须可见").toBeGreaterThan(0);
  expect(payload.data?.suppressedCardCount ?? 0, "嵌入宿主推荐卡不应被疲劳策略抑制").toBe(0);
  const card = findEmbeddedRecommendationCard(payload.data?.cards ?? []);
  expect(card?.cardId, "嵌入宿主演练必须获得真实推荐卡编号").toBeTruthy();
  return { cardId: card?.cardId ?? "", traceId: payload.data?.traceId ?? "" };
}

async function upsertEmbedOrigin(page: Page) {
  const response = await postApi(page, "/engine/embed/origins", { origin: hostOrigin });
  await expectOk(response, "添加嵌入宿主 Origin 白名单");
}

async function issueEmbedLaunchToken(
  page: Page,
  snapshot: ContextSnapshotSummary,
  cardId: string,
): Promise<EmbedLaunchTokenPayload> {
  const response = await postApi(page, "/engine/embed/launch-tokens", {
    roleCode: "clinical-user",
    patientId: snapshot.patientId,
    encounterId: snapshot.encounterId,
    triggerPoint: "patient-view",
    expireSeconds: 300,
    integrationMode: "IFRAME",
    hook: "patient-view",
    hookInstance: `embed-host-${cardId}`,
    parentOrigin: hostOrigin,
  });
  await expectOk(response, "签发嵌入一次性启动凭证");
  const body = (await response.json()) as { data?: EmbedLaunchTokenPayload };
  expect(body.data?.token, "嵌入启动凭证响应必须返回 token").toBeTruthy();
  expect(body.data?.embedUrl, "嵌入启动凭证响应必须返回 embedUrl").toContain(
    "/embed/launch?token=",
  );
  expect(body.data?.integrationMode, "嵌入启动凭证必须绑定 IFRAME 模式").toBe("IFRAME");
  return body.data ?? {};
}

function recordEmbedBusinessHostStage(observedStages: Set<string>, stage: string) {
  observedStages.add(stage);
}

async function attachEmbedBusinessHostScenarioEvidence(
  testInfo: TestInfo,
  observedStageSet: Set<string>,
  apiEvidence: EmbedApiEvidence,
  context: Record<string, unknown>,
) {
  const scenarioEvidence = requiredEmbedBusinessHostScenarioEvidence.map((scenario) => ({
    code: scenario.code,
    observedStages: scenario.observedStages.filter((stage) => observedStageSet.has(stage)),
  }));
  const completedScenarioCodes = scenarioEvidence
    .filter((scenario) => {
      const requiredStages =
        requiredEmbedBusinessHostScenarioEvidence.find((item) => item.code === scenario.code)
          ?.observedStages ?? [];
      return requiredStages.every((stage) => scenario.observedStages.includes(stage));
    })
    .map((scenario) => scenario.code);
  await testInfo.attach("embed-business-host-launch-codes", {
    body: JSON.stringify(
      {
        scenarioCodes: completedScenarioCodes,
        productLayers: ["DELIVERY_FEEDBACK"],
        deliveryShapes: ["EMBEDDED_COMPONENT"],
        apiEvidence,
        context,
        scenarioEvidence,
      },
      null,
      2,
    ),
    contentType: "application/json",
  });
}

function collectEmbedApiResponses(page: Page, apiEvidence: EmbedApiEvidence) {
  const responses: string[] = [];
  page.on("response", (response) => {
    const url = response.url();
    if (!url.includes("/medkernel/api/v1/engine/embed/")) return;
    const item = `${response.request().method()} ${new URL(url).pathname} ${response.status()}`;
    responses.push(item);
    if (response.status() < 200 || response.status() >= 300) return;
    if (url.includes("/engine/embed/launch") && !url.includes("launch-tokens")) {
      apiEvidence.launchExchanged = true;
    }
    if (url.includes("/engine/embed/recommendations")) {
      apiEvidence.recommendationsRead = true;
    }
    if (url.includes("/engine/embed/feedback")) {
      apiEvidence.feedbackSubmitted = true;
    }
  });
  return responses;
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") errors.push(message.text());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

function collectServerErrors(page: Page) {
  const errors: string[] = [];
  page.on("response", (response) => {
    const url = response.url();
    if (response.status() >= 400 && url.includes("/medkernel/")) {
      errors.push(`${response.status()} ${response.request().method()} ${url}`);
    }
  });
  return errors;
}

function collectNetworkFailures(page: Page) {
  const errors: string[] = [];
  page.on("requestfailed", (request) => {
    const failure = request.failure();
    if (failure?.errorText !== "net::ERR_ABORTED") {
      errors.push(`${request.method()} ${request.url()} ${failure?.errorText ?? "requestfailed"}`);
    }
  });
  return errors;
}

function waitForPost(page: Page, path: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(path),
    { timeout: 30_000 },
  );
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  const combobox = dialog.getByRole("combobox", { name: new RegExp(escapeRegExp(label)) }).first();
  const select = combobox.locator(
    "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')][1]",
  );
  const selectedText = await currentSelectText(select);
  if (selectedText === optionText) return;
  await select.locator(".ant-select-selector").click();
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown).toBeVisible({ timeout: 5_000 });
  const optionLocator = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: new RegExp(`^\\s*${escapeRegExp(optionText)}\\s*$`) })
    .first();
  await expect(optionLocator).toBeVisible({ timeout: 20_000 });
  await optionLocator.click();
}

async function currentSelectText(select: Locator) {
  const selected = select.locator(".ant-select-selection-item").first();
  if ((await selected.count()) === 0) return "";
  return (await selected.textContent())?.trim() ?? "";
}

function embeddedRecommendationCard(frame: ReturnType<Page["frameLocator"]>, title: string) {
  return frame.locator(".ant-card").filter({ hasText: title }).first();
}

function findEmbeddedRecommendationCard(cards: RecommendationEvaluationPayload["cards"]) {
  return cards?.find(
    (card) =>
      card.title === "检验危急值需人工确认" &&
      card.sourceSummary?.includes("嵌入宿主真实服务链路演练"),
  );
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
