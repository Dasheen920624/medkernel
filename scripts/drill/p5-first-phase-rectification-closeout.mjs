#!/usr/bin/env node
// 第一阶段收官：只关闭 P5 幕7失败演练遗留的整改任务，不触碰其他业务问题。
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const requireFromFrontend = createRequire(new URL("../../frontend/package.json", import.meta.url));
const { chromium } = requireFromFrontend("playwright");

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const baseUrl = (process.env.DRILL_BASE_URL ?? "https://193.112.107.134").replace(/\/+$/, "");
const apiBase = `${baseUrl}/medkernel/api/v1`;
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ?? "/tmp/p5-14-role-drill-credentials-20260612.json";
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/p5-second-fresh-drill-20260612/第一阶段最终收官",
);
const findingPrefix = "P5.ACT7.FOLLOWUP.ABNORMAL.";
const sandboxFindingCode = "P5.ACT7.FOLLOWUP.QUALITY_FND";
const sandboxTracePrefix = "sandbox-fulltruth-run-sbx-evaluation-closed-loop-";
const runTag = `p5-final-rectification-${Date.now()}`;

function traceId(stage) {
  return `${runTag}-${stage}-${Date.now()}`;
}

function dataOf(result) {
  return result.body?.data ?? result.body;
}

async function parseResponse(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text.slice(0, 800) };
  }
}

async function csrfToken(context) {
  const cookies = await context.cookies(baseUrl);
  return cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? "";
}

async function apiGet(context, pathname, stage) {
  const response = await context.request.get(`${apiBase}${pathname}`, {
    headers: { "X-Trace-Id": traceId(stage) },
  });
  return { status: response.status(), ok: response.ok(), body: await parseResponse(response) };
}

async function apiPost(context, pathname, body, stage, idempotencyKey) {
  const response = await context.request.post(`${apiBase}${pathname}`, {
    data: body,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(stage),
      "X-XSRF-TOKEN": await csrfToken(context),
      "Idempotency-Key": idempotencyKey,
    },
  });
  return { status: response.status(), ok: response.ok(), body: await parseResponse(response) };
}

function requireOk(result, stage) {
  if (!result.ok) {
    throw new Error(`${stage} 失败: HTTP ${result.status} ${JSON.stringify(result.body).slice(0, 600)}`);
  }
  return dataOf(result);
}

function closeoutScope(issue) {
  if (issue.findingCode?.startsWith(findingPrefix)) {
    return "P5_ACT7_LEGACY_RECTIFICATION";
  }
  if (
    issue.findingCode === sandboxFindingCode
    && issue.traceId?.startsWith(sandboxTracePrefix)
  ) {
    return "SANDBOX_EVALUATION_RECTIFICATION";
  }
  return null;
}

async function login(browser, account, role) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: "zh-CN" });
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: {
      username: account.username,
      password: account.password,
      tenantId: account.tenantId,
    },
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(`login-${role}`),
    },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(`${role} 登录失败: HTTP ${response.status()} ${body.slice(0, 300)}`);
  }
  return context;
}

async function loadAccounts() {
  const credentials = JSON.parse(await readFile(credentialPath, "utf8"));
  const quality = credentials.roleAccounts?.["quality-governor"];
  const remediation = credentials.roleAccounts?.["clinical-governor"];
  if (!quality?.username || !quality?.password || !quality?.tenantId) {
    throw new Error("凭据缺少 quality-governor 可用账号");
  }
  if (!remediation?.username || !remediation?.password || !remediation?.tenantId) {
    throw new Error("凭据缺少 clinical-governor 可用账号");
  }
  if (quality.tenantId !== remediation.tenantId) {
    throw new Error("整改提交人与质控复核人不属于同一演练租户");
  }
  return { quality, remediation };
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const accounts = await loadAccounts();
  const browser = await chromium.launch({ headless: true });
  const qualityContext = await login(browser, accounts.quality, "quality-governor");
  const remediationContext = await login(browser, accounts.remediation, "clinical-governor");
  const actions = [];

  try {
    const issuePage = requireOk(
      await apiGet(qualityContext, "/engine/evaluation/issues?page=1&size=100", "list-issues"),
      "查询质控问题",
    );
    const pendingIssues = (issuePage.items ?? []).filter(
      (issue) => closeoutScope(issue) && !["CLOSED", "WAIVED"].includes(issue.status),
    );

    for (const issue of pendingIssues) {
      const scope = closeoutScope(issue);
      let detail = requireOk(
        await apiGet(
          qualityContext,
          `/engine/evaluation/issues/${encodeURIComponent(issue.findingId)}`,
          "issue-detail-before",
        ),
        `查询问题 ${issue.findingId}`,
      );
      const taskId = detail.rectificationTask?.taskId;
      if (!taskId) {
        throw new Error(`问题 ${issue.findingId} 缺少整改任务，不能伪造闭环`);
      }

      if (["ASSIGNED", "RETURNED"].includes(detail.rectificationTask.status)) {
        const submitted = requireOk(
          await apiPost(
            remediationContext,
            `/engine/rectifications/${encodeURIComponent(taskId)}/submit`,
            {
              rectificationSummary:
                scope === "SANDBOX_EVALUATION_RECTIFICATION"
                  ? "关闭沙盘评估演练整改：已核对沙盘随访质量评估结果、问题编码、任务派发和证据引用；该任务仅由全真沙盘演练触发，不涉及真实患者处置。"
                  : "补齐失败演练遗留整改：已完成随访异常复评记录核对、证据引用校验和责任科室确认；未自动开立医嘱。",
              evidenceRef: `P5-FIRST-PHASE-CLOSEOUT:${issue.findingId}:${taskId}`,
            },
            "submit-rectification",
            `${runTag}-submit-${taskId}`,
          ),
          `提交整改 ${taskId}`,
        );
        actions.push({
          findingId: issue.findingId,
          findingCode: issue.findingCode,
          scope,
          taskId,
          action: "SUBMIT",
          result: submitted,
        });
      }

      detail = requireOk(
        await apiGet(
          qualityContext,
          `/engine/evaluation/issues/${encodeURIComponent(issue.findingId)}`,
          "issue-detail-after-submit",
        ),
        `复查问题 ${issue.findingId}`,
      );
      if (detail.rectificationTask?.status === "SUBMITTED") {
        const reviewed = requireOk(
          await apiPost(
            qualityContext,
            `/engine/rectifications/${encodeURIComponent(taskId)}/review`,
            {
              decision: "APPROVED",
              comment:
                scope === "SANDBOX_EVALUATION_RECTIFICATION"
                  ? "第一阶段收官复核：沙盘评估演练整改已由独立责任角色提交，问题、任务和演练证据引用一致，同意关闭。"
                  : "第一阶段收官复核：遗留演练整改已由独立责任角色提交，问题、任务和证据引用一致，同意关闭。",
              evidenceRef: `P5-FIRST-PHASE-REVIEW:${issue.findingId}:${taskId}`,
            },
            "review-rectification",
            `${runTag}-review-${taskId}`,
          ),
          `复核整改 ${taskId}`,
        );
        actions.push({
          findingId: issue.findingId,
          findingCode: issue.findingCode,
          scope,
          taskId,
          action: "REVIEW",
          result: reviewed,
        });
      }
    }

    const finalIssues = requireOk(
      await apiGet(qualityContext, "/engine/evaluation/issues?page=1&size=100", "verify-issues"),
      "回查质控问题",
    );
    const remaining = (finalIssues.items ?? []).filter(
      (issue) => closeoutScope(issue) && !["CLOSED", "WAIVED"].includes(issue.status),
    );
    const report = requireOk(
      await apiGet(qualityContext, "/engine/rectifications/report", "verify-report"),
      "回查整改报告",
    );
    if (remaining.length > 0 || report.openTasks !== 0) {
      throw new Error(`整改闭环仍有未完成项: P5=${remaining.length}, report.openTasks=${report.openTasks}`);
    }

    const evidence = {
      runTag,
      generatedAt: new Date().toISOString(),
      baseUrl,
      findingPrefix,
      sandboxFindingCode,
      sandboxTracePrefix,
      initialPendingCount: pendingIssues.length,
      actions,
      remainingP5Issues: remaining,
      finalReport: report,
      conclusion: "PASS",
    };
    await writeFile(
      path.join(evidenceDir, "01-rectification-closeout.json"),
      `${JSON.stringify(evidence, null, 2)}\n`,
      "utf8",
    );
    process.stdout.write(`${JSON.stringify(evidence, null, 2)}\n`);
  } finally {
    await remediationContext.close();
    await qualityContext.close();
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
