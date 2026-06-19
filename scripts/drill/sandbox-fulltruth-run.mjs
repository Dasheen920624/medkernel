#!/usr/bin/env node
// 全功能沙盘验收：CURRENT 基线 -> 真实编排 -> 一次性令牌 -> 推荐卡 -> 人工反馈。
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  loadScenarioRules,
  selectSeedRules,
} from "../sandbox/scenario-rules.mjs";

const requireFromFrontend = createRequire(
  new URL("../../frontend/package.json", import.meta.url),
);
const { chromium } = requireFromFrontend("playwright");

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const baseUrl = (
  process.env.DRILL_BASE_URL ?? "https://193.112.107.134"
).replace(/\/+$/, "");
const apiBase = `${baseUrl}/medkernel/api/v1`;
const parentOrigin = new URL(baseUrl).origin;
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/tmp/medkernel-sandbox-role-credentials.json";
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/sandbox-full-fidelity-20260619",
);
const outerScenarios = [
  {
    id: "sbx-pathway-ed",
    kind: "PATHWAY",
    patientId: "SBX-LAB-K-001",
    triggerPoint: "patient-view",
    actionCode: "PATHWAY",
    expectedFact: "patientPathwayId",
  },
  {
    id: "sbx-recommendation-composite",
    kind: "RECOMMENDATION_COMPOSITE",
    patientId: "SBX-ACS-001",
    triggerPoint: "patient-view",
    actionCode: "SUGGEST_ORDER",
  },
  {
    id: "sbx-followup-closed-loop",
    kind: "FOLLOWUP",
    patientId: "SBX-FU-001",
    triggerPoint: "patient-view",
    actionCode: "FOLLOWUP",
    expectedFact: "followupPlanId",
  },
  {
    id: "sbx-evaluation-closed-loop",
    kind: "EVALUATION",
    patientId: "SBX-QC-001",
    triggerPoint: "patient-view",
    actionCode: "EVALUATION",
    expectedFact: "evaluationRunId",
  },
  {
    id: "sbx-embed-modes",
    kind: "EMBED",
    patientId: "SBX-LAB-K-001",
    triggerPoint: "patient-view",
    actionCode: "IFRAME_SDK_API",
  },
];

function traceId(stage) {
  return `sandbox-fulltruth-${stage}-${Date.now()}`;
}

async function loadAccount(roleCode = "clinical-decision-user") {
  const data = JSON.parse(await readFile(credentialPath, "utf8"));
  const account =
    data.roleAccounts?.[roleCode] ?? data.platformRoleAccounts?.[roleCode];
  if (!account?.username || !account?.password || !account?.tenantId) {
    throw new Error(`凭据缺少 ${roleCode} 可用账号`);
  }
  return account;
}

async function parseResponse(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text.slice(0, 600) };
  }
}

async function apiPost(context, pathname, data, stage) {
  const cookies = await context.cookies(baseUrl);
  const csrf =
    cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? "";
  const response = await context.request.post(`${apiBase}${pathname}`, {
    data,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(stage),
      "X-XSRF-TOKEN": csrf,
    },
  });
  return {
    status: response.status(),
    ok: response.ok(),
    body: await parseResponse(response),
  };
}

async function apiGet(context, pathname, stage) {
  const response = await context.request.get(`${apiBase}${pathname}`, {
    headers: { "X-Trace-Id": traceId(stage) },
  });
  return {
    status: response.status(),
    ok: response.ok(),
    body: await parseResponse(response),
  };
}

function requireOk(result, stage, accepted = [200]) {
  if (!accepted.includes(result.status)) {
    throw new Error(
      `${stage} 失败: HTTP ${result.status} ${JSON.stringify(result.body).slice(0, 500)}`,
    );
  }
  return result.body?.data;
}

function maskedUrl(value) {
  if (!value) return null;
  return String(value).replace(/([?&]token=)[^&]+/u, "$1***");
}

async function login(browser, account) {
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    locale: "zh-CN",
  });
  const login = await apiPost(
    context,
    "/auth/login",
    {
      username: account.username,
      password: account.password,
      tenantId: account.tenantId,
    },
    "login",
  );
  requireOk(login, "沙盘角色登录");
  return context;
}

function assertCurrentRuntime(run, scenarioId) {
  if (
    run.mode !== "CURRENT" ||
    !run.runId ||
    !run.baselineId ||
    !run.resolvedPackageVersion ||
    !["TENANT_PACKAGE", "PLATFORM_PACKAGE"].includes(run.resolutionSource) ||
    run.externalSideEffects !== false
  ) {
    throw new Error(
      `场景 ${scenarioId} 未返回完整 CURRENT 冻结基线: ${JSON.stringify({
        mode: run.mode,
        runId: run.runId,
        baselineId: run.baselineId,
        resolvedPackageVersion: run.resolvedPackageVersion,
        resolutionSource: run.resolutionSource,
        externalSideEffects: run.externalSideEffects,
      })}`,
    );
  }
}

async function runScenario(context, verificationContext, scenario) {
  const integrationMode = scenario.integrationMode ?? "IFRAME";
  const run = requireOk(
    await apiPost(
      context,
      `/engine/sandbox/scenarios/${scenario.id}/run`,
      {
        entryMode: "SNAPSHOT",
        mode: "CURRENT",
        occurredAt: new Date().toISOString(),
        parentOrigin,
        integrationMode,
      },
      `run-${scenario.id}`,
    ),
    `运行场景 ${scenario.id}`,
  );
  assertCurrentRuntime(run, scenario.id);
  if (run.result !== "PASS" || run.cardCount < 1 || !run.embedToken) {
    throw new Error(
      `场景 ${scenario.id} 编排未通过: ${JSON.stringify({
        result: run.result,
        cardCount: run.cardCount,
        steps: run.steps,
      })}`,
    );
  }
  if (scenario.expectedFact && !run[scenario.expectedFact]) {
    throw new Error(
      `场景 ${scenario.id} 未返回业务事实 ${scenario.expectedFact}`,
    );
  }
  if (scenario.kind === "PATHWAY") {
    const advanceStep = run.steps?.find(
      (step) => step.stage === "PATHWAY_ADVANCE",
    );
    if (
      advanceStep?.status !== "OK" ||
      !advanceStep.serverFacts?.previousNodeCode ||
      !advanceStep.serverFacts?.nextNodeCode ||
      !advanceStep.serverFacts?.edgeCode
    ) {
      throw new Error(`场景 ${scenario.id} 未形成可核验的路径推进证据`);
    }
  }

  const launch = requireOk(
    await apiPost(
      context,
      "/engine/embed/launch",
      {
        token: run.embedToken,
        integrationMode,
        hook: scenario.triggerPoint,
        hookInstance: run.hookInstance,
      },
      `launch-${scenario.id}`,
    ),
    `兑换场景 ${scenario.id} 嵌入令牌`,
  );
  if (launch.active !== true || launch.patientId !== scenario.patientId) {
    throw new Error(`场景 ${scenario.id} 嵌入上下文不一致`);
  }

  const recommendations = requireOk(
    await apiPost(
      context,
      "/engine/embed/recommendations",
      { token: run.embedToken },
      `cards-${scenario.id}`,
    ),
    `读取场景 ${scenario.id} 推荐卡`,
  );
  const card =
    recommendations.items?.find(
      (item) => item.suggestedAction === scenario.actionCode,
    ) ?? recommendations.items?.[0];
  if (!card?.cardId) {
    throw new Error(`场景 ${scenario.id} 未读取到真实推荐卡`);
  }

  const feedback = requireOk(
    await apiPost(
      context,
      "/engine/embed/feedback",
      {
        token: run.embedToken,
        cardId: card.cardId,
        actionType: "ADOPT",
        reason: "沙盘全真验收：医师确认符合当前场景并采纳提醒",
      },
      `feedback-${scenario.id}`,
    ),
    `提交场景 ${scenario.id} 医师反馈`,
  );
  if (feedback.recommendationStatus !== "ACCEPTED") {
    throw new Error(`场景 ${scenario.id} 医师反馈未推进为 ACCEPTED`);
  }

  let execution = null;
  if (scenario.kind === "RULE_ONLY") {
    const executions = requireOk(
      await apiGet(
        context,
        "/engine/rule/rules/executions?page=1&size=100",
        `executions-${scenario.id}`,
      ),
      `回查场景 ${scenario.id} 规则执行`,
    );
    execution = executions.items?.find(
      (item) =>
        item.traceId === run.traceId ||
        item.ruleCode === scenario.ruleCode ||
        item.ruleId === scenario.ruleCode,
    );
  }

  const businessFact = await verifyBusinessFact(
    context,
    verificationContext,
    scenario,
    run,
  );

  return {
    scenarioId: scenario.id,
    ruleCode: scenario.ruleCode,
    result: run.result,
    traceId: run.traceId,
    runId: run.runId,
    baselineId: run.baselineId,
    mode: run.mode,
    resolvedPackageVersion: run.resolvedPackageVersion,
    resolutionSource: run.resolutionSource,
    externalSideEffects: run.externalSideEffects,
    snapshotId: run.snapshotId,
    triggerId: run.triggerId,
    cardId: card.cardId,
    cardAction: card.suggestedAction,
    cardRiskLevel: card.riskLevel,
    feedbackStatus: feedback.recommendationStatus,
    callbackStatus: feedback.callbackStatus,
    callbackDelivered: feedback.callbackDelivered,
    degradationReason: feedback.degradationReason,
    integrationMode,
    supportedEmbedModes: run.embedModes,
    embedUrl: maskedUrl(run.embedUrl),
    businessFact,
    engineSteps: run.steps?.map((step) => ({
      stage: step.stage,
      endpoint: step.endpoint,
      status: step.status,
      serverFacts: step.serverFacts,
      error: step.error,
    })),
    executionFact:
      scenario.kind !== "RULE_ONLY"
        ? {
            status: "NOT_APPLICABLE",
            note: "外圈引擎场景以对应运行事实回查为准。",
          }
        : execution
          ? {
              executionId: execution.executionId,
              hit: execution.hit,
              status: execution.status,
              traceId: execution.traceId,
            }
          : {
              status: "NOT_RETURNED_BY_LIST",
              note: "编排响应、推荐卡与反馈事实已闭环；执行目录未返回可关联行时不伪造。",
            },
  };
}

async function verifyBusinessFact(context, verificationContext, scenario, run) {
  if (scenario.kind === "PATHWAY") {
    const advanceStep = run.steps?.find(
      (step) => step.stage === "PATHWAY_ADVANCE",
    );
    const detail = requireOk(
      await apiGet(
        context,
        `/engine/pathway/patient-pathways/${run.patientPathwayId}`,
        `verify-pathway-${scenario.id}`,
      ),
      `回查场景 ${scenario.id} 患者路径`,
    );
    if (
      detail.patientPathway?.currentNodeCode !==
      advanceStep?.serverFacts?.nextNodeCode
    ) {
      throw new Error(`场景 ${scenario.id} 路径实例当前节点与推进证据不一致`);
    }
    return {
      patientPathwayId: detail.patientPathway?.patientPathwayId,
      status: detail.patientPathway?.status,
      currentNodeCode: detail.patientPathway?.currentNodeCode,
      previousNodeCode: advanceStep?.serverFacts?.previousNodeCode,
      edgeCode: advanceStep?.serverFacts?.edgeCode,
      advancementVerified: true,
    };
  }
  if (scenario.kind === "FOLLOWUP") {
    const detail = requireOk(
      await apiGet(
        context,
        `/engine/followup/plans/${run.followupPlanId}`,
        `verify-followup-${scenario.id}`,
      ),
      `回查场景 ${scenario.id} 随访计划`,
    );
    return {
      followupPlanId: detail.planId,
      status: detail.status,
      taskCount: detail.tasks?.length ?? 0,
    };
  }
  if (scenario.kind === "EVALUATION") {
    const evaluationStep = run.steps?.find(
      (step) => step.stage === "EVALUATION",
    );
    const diagnose = requireOk(
      await apiGet(
        verificationContext,
        `/engine/evaluation/runs/${run.evaluationRunId}/diagnose`,
        `verify-evaluation-${scenario.id}`,
      ),
      `回查场景 ${scenario.id} 评估运行`,
    );
    const entity = diagnose.entity ?? {};
    const resultCount = Number(
      entity.resultCount ?? evaluationStep?.serverFacts?.resultCount ?? 0,
    );
    const findingCount = Number(
      entity.findingCount ?? evaluationStep?.serverFacts?.findingCount ?? 0,
    );
    const taskCount = Number(
      entity.taskCount ?? evaluationStep?.serverFacts?.taskCount ?? 0,
    );
    if (resultCount < 1 || findingCount < 1) {
      throw new Error(`场景 ${scenario.id} 评估运行未形成结果与问题闭环`);
    }
    return {
      evaluationRunId: run.evaluationRunId,
      status: diagnose.currentStatus ?? entity.status,
      resultCount,
      findingCount,
      taskCount,
      diagnosisVerified: diagnose.entityId === run.evaluationRunId,
    };
  }
  return { status: "NOT_APPLICABLE" };
}

async function verifyEmbedModes(context) {
  const scenario = outerScenarios.find((item) => item.kind === "EMBED");
  const modes = [];
  for (const integrationMode of ["SDK", "API"]) {
    const run = requireOk(
      await apiPost(
        context,
        `/engine/sandbox/scenarios/${scenario.id}/run`,
        {
          entryMode: "SNAPSHOT",
          mode: "CURRENT",
          occurredAt: new Date().toISOString(),
          parentOrigin,
          integrationMode,
        },
        `run-${scenario.id}-${integrationMode.toLowerCase()}`,
      ),
      `运行场景 ${scenario.id} ${integrationMode} 模式`,
    );
    assertCurrentRuntime(run, `${scenario.id}/${integrationMode}`);
    if (
      run.result !== "PASS" ||
      !run.embedToken ||
      !run.embedModes?.includes(integrationMode)
    ) {
      throw new Error(`${scenario.id} ${integrationMode} 模式编排未通过`);
    }
    const launch = requireOk(
      await apiPost(
        context,
        "/engine/embed/launch",
        {
          token: run.embedToken,
          integrationMode,
          hook: scenario.triggerPoint,
          hookInstance: run.hookInstance,
        },
        `launch-${scenario.id}-${integrationMode.toLowerCase()}`,
      ),
      `兑换场景 ${scenario.id} ${integrationMode} 令牌`,
    );
    if (launch.active !== true || launch.integrationMode !== integrationMode) {
      throw new Error(`${scenario.id} ${integrationMode} 兑换上下文不一致`);
    }
    modes.push({
      integrationMode,
      traceId: run.traceId,
      runId: run.runId,
      baselineId: run.baselineId,
      resolvedPackageVersion: run.resolvedPackageVersion,
      resolutionSource: run.resolutionSource,
      launchActive: launch.active,
      supportedEmbedModes: run.embedModes,
    });
  }
  return modes;
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(manifest, process.env.SEED_ONLY ?? "");
  if (selected.blocked.length > 0) {
    throw new Error(
      `存在不可运行演练规则: ${selected.blocked.map((item) => item.ruleCode).join(", ")}`,
    );
  }
  const readyScenarios = [
    ...selected.runnable.map((scenario) => {
      const positive = scenario.clinicalContent.testCases.find(
        (testCase) => testCase.caseType === "POSITIVE",
      );
      return { ...scenario, kind: "RULE_ONLY", patientId: positive.patientId };
    }),
    ...outerScenarios,
  ];
  const summary = {
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    parentOrigin,
    credentialSource: credentialPath,
    primaryRole: "clinical-decision-user",
    verificationRole: "quality-governor",
    readyScenarioCount: readyScenarios.length,
    blockedScenarioCount: 0,
    runtimeBinding: null,
    results: [],
    embedModeVerifications: [],
    failures: [],
  };

  const browser = await chromium.launch();
  try {
    const context = await login(
      browser,
      await loadAccount("clinical-decision-user"),
    );
    const verificationContext = await login(
      browser,
      await loadAccount("quality-governor"),
    );
    try {
      const runtimeBinding = requireOk(
        await apiGet(
          context,
          "/engine/sandbox/runtime-binding",
          "runtime-binding",
        ),
        "读取沙盘 CURRENT 运行绑定",
      );
      if (
        runtimeBinding.ready !== true ||
        !runtimeBinding.packageId ||
        !runtimeBinding.packageVersion ||
        !["TENANT_PACKAGE", "PLATFORM_PACKAGE"].includes(
          runtimeBinding.resolutionSource,
        ) ||
        runtimeBinding.externalSideEffects !== false
      ) {
        throw new Error(
          `沙盘 CURRENT 运行绑定未就绪: ${JSON.stringify(runtimeBinding)}`,
        );
      }
      summary.runtimeBinding = runtimeBinding;
      for (const scenario of readyScenarios) {
        try {
          const result = await runScenario(
            context,
            verificationContext,
            scenario,
          );
          if (
            result.resolvedPackageVersion !== runtimeBinding.packageVersion ||
            result.resolutionSource !== runtimeBinding.resolutionSource
          ) {
            throw new Error(
              `场景 ${scenario.id} 的冻结基线与当前明确绑定不一致`,
            );
          }
          summary.results.push(result);
        } catch (error) {
          summary.failures.push({
            scenarioId: scenario.id,
            detail: String(error).slice(0, 1200),
          });
        }
      }
      try {
        summary.embedModeVerifications = await verifyEmbedModes(context);
      } catch (error) {
        summary.failures.push({
          scenarioId: "sbx-embed-modes",
          phase: "SDK_API_MODE_VERIFICATION",
          detail: String(error).slice(0, 1200),
        });
      }
    } finally {
      await verificationContext.close();
      await context.close();
    }
  } finally {
    await browser.close();
  }

  await writeFile(
    path.join(evidenceDir, "00-sandbox-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  if (summary.failures.length > 0) {
    console.error(`沙盘全真演练存在 ${summary.failures.length} 个失败`);
    process.exitCode = 1;
    return;
  }
  console.log(
    "沙盘全真演练通过，十条机构规则与五类外圈场景均已执行，证据目录：",
    evidenceDir,
  );
}

await main();
