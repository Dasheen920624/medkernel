#!/usr/bin/env node
// 全真体验沙盘规则铺底：仅对通过临床门禁的规则执行真实治理链。
// 默认输出：docs/release/evidence/p5-second-fresh-drill-20260612/sandbox/seed-summary.json。
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

import { loadScenarioRules, selectSeedRules } from "./scenario-rules.mjs";

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
  "docs/release/evidence/p5-second-fresh-drill-20260612/sandbox",
);
const packageVersion = "2026.06.1";

function traceId(stage) {
  return `sandbox-seed-${stage}-${Date.now()}`;
}

async function loadCredentials() {
  const data = JSON.parse(await readFile(credentialPath, "utf8"));
  return {
    customerTenant: data.customerTenant,
    roleAccounts: data.roleAccounts ?? {},
    platformRoleAccounts: data.platformRoleAccounts ?? {},
  };
}

function requireAccount(credentials, role) {
  if (role === "organization-admin" && credentials.customerTenant?.adminUsername) {
    return {
      username: credentials.customerTenant.adminUsername,
      password: credentials.customerTenant.password,
      tenantId: credentials.customerTenant.tenantId,
    };
  }
  const account = credentials.roleAccounts[role] ?? credentials.platformRoleAccounts[role];
  if (!account?.username || !account?.password || !account?.tenantId) {
    throw new Error(`凭据缺少角色 ${role} 的可用账号`);
  }
  return account;
}

async function login(browser, account, role) {
  const context = await browser.newContext({ ignoreHTTPSErrors: true, locale: "zh-CN" });
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: {
      username: account.username,
      password: account.password,
      tenantId: account.tenantId,
    },
    headers: { "Content-Type": "application/json", "X-Trace-Id": traceId(`login-${role}`) },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(`${role} 登录失败: ${response.status()} ${body.slice(0, 300)}`);
  }
  return context;
}

async function parseResponse(response) {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text.slice(0, 600) };
  }
}

async function apiGet(context, pathname, stage) {
  const response = await context.request.get(`${apiBase}${pathname}`, {
    headers: { "X-Trace-Id": traceId(stage) },
  });
  return { status: response.status(), ok: response.ok(), body: await parseResponse(response) };
}

async function apiPost(context, pathname, data, stage) {
  const cookies = await context.cookies(baseUrl);
  const csrf = cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? "";
  const response = await context.request.post(`${apiBase}${pathname}`, {
    data,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(stage),
      "X-XSRF-TOKEN": csrf,
    },
  });
  return { status: response.status(), ok: response.ok(), body: await parseResponse(response) };
}

function ensureOk(result, stage, accepted = [200, 201]) {
  if (!accepted.includes(result.status)) {
    throw new Error(`${stage} 失败: HTTP ${result.status} ${JSON.stringify(result.body).slice(0, 500)}`);
  }
  return result.body?.data;
}

async function envelope(context, stage) {
  const me = ensureOk(await apiGet(context, "/security/me", `${stage}-me`), `${stage} 读取登录态`);
  const scope = me.dataScope ?? {};
  const roleCodes = (me.roles ?? me.roleCodes ?? [])
    .map((role) => (typeof role === "string" ? role : role.code))
    .filter(Boolean);
  return {
    request_id: traceId(`${stage}-request`),
    trace_id: traceId(`${stage}-trace`),
    tenant_id: scope.tenantId,
    group_id: scope.groupId,
    hospital_id: scope.hospitalId,
    campus_id: scope.campusId,
    site_id: scope.siteId,
    department_id: scope.departmentId,
    specialty_id: scope.specialtyId,
    user_id: me.userId ?? me.username,
    role_codes: roleCodes,
    package_version: packageVersion,
    orgUnitId: scope.hospitalId || scope.campusId || scope.tenantId,
  };
}

function snapshotPayload(testCase, contextEnvelope) {
  const occurredAt = new Date().toISOString();
  const criticalFlag = testCase.expectedHit ? "HIGH" : null;
  return {
    request_id: traceId(`snapshot-${testCase.caseType}`),
    trace_id: traceId(`snapshot-${testCase.caseType}-trace`),
    patientId: testCase.patientId,
    encounterId: testCase.encounterId,
    orgUnitId: contextEnvelope.orgUnitId,
    package_version: packageVersion,
    resources: {
      patient: {
        mpi: testCase.patientId,
        name: `沙盘${testCase.caseType}患者`,
        birthDate: "1965-06-01",
        gender: "M",
        specialPopulations: [],
        sourceSystem: "MEDKERNEL_SANDBOX",
        sourceRecordId: testCase.patientId,
        mappedVersion: packageVersion,
        eventTime: occurredAt,
        receivedTime: occurredAt,
        qualityStatus: "VALID",
      },
      encounters: [
        {
          encounterId: testCase.encounterId,
          encounterType: "ED",
          admissionTime: occurredAt,
          dischargeTime: null,
          departmentId: "ED",
          attendingDoctorId: "SBX-DOCTOR-001",
          bedId: null,
          sourceSystem: "MEDKERNEL_SANDBOX",
          sourceRecordId: testCase.encounterId,
          mappedVersion: packageVersion,
          eventTime: occurredAt,
          receivedTime: occurredAt,
          qualityStatus: "VALID",
        },
      ],
      observations: [
        {
          observationId: `SBX-${testCase.caseType}-OBS`,
          code: testCase.observationCode,
          displayName: testCase.observationName,
          valueNumeric: testCase.valueNumeric,
          valueString: null,
          unit: "mmol/L",
          referenceRange: "3.5-5.5",
          criticalFlag,
          sourceSystem: "MEDKERNEL_SANDBOX",
          sourceRecordId: `SBX-${testCase.caseType}-RESULT`,
          mappedVersion: packageVersion,
          eventTime: occurredAt,
          receivedTime: occurredAt,
          qualityStatus: "VALID",
        },
      ],
    },
  };
}

async function seedSnapshots(browser, credentials, rule) {
  const context = await login(
    browser,
    requireAccount(credentials, "integration-operator"),
    "integration-operator",
  );
  try {
    const ctx = await envelope(context, "snapshot");
    const snapshots = [];
    for (const testCase of rule.clinicalContent.testCases) {
      const query = `/engine/context/snapshots?patientId=${encodeURIComponent(testCase.patientId)}&status=ACTIVE&page=1&size=5`;
      const existing = ensureOk(
        await apiGet(context, query, `snapshot-find-${testCase.caseType}`),
        `回查 ${testCase.caseType} 快照`,
      )?.items?.[0];
      if (existing) {
        snapshots.push({ ...testCase, snapshotId: existing.snapshotId, reused: true });
        continue;
      }
      ensureOk(
        await apiPost(
          context,
          "/engine/context/snapshots",
          snapshotPayload(testCase, ctx),
          `snapshot-create-${testCase.caseType}`,
        ),
        `创建 ${testCase.caseType} 快照`,
        [201],
      );
      const created = ensureOk(
        await apiGet(context, query, `snapshot-verify-${testCase.caseType}`),
        `验证 ${testCase.caseType} 快照`,
      )?.items?.[0];
      if (!created?.snapshotId || created.status !== "ACTIVE") {
        throw new Error(`${testCase.caseType} 快照未形成 ACTIVE 服务端事实`);
      }
      snapshots.push({ ...testCase, snapshotId: created.snapshotId, reused: false });
    }
    return snapshots;
  } finally {
    await context.close();
  }
}

async function findRule(context, ruleCode, stage) {
  const page = ensureOk(
    await apiGet(context, "/engine/rule/rules?page=1&size=100", stage),
    "读取规则列表",
  );
  return page?.items?.find((item) => item.ruleCode === ruleCode) ?? null;
}

async function ruleDetail(context, ruleId, stage) {
  return ensureOk(
    await apiGet(context, `/engine/rule/rules/${ruleId}`, stage),
    "读取规则详情",
  );
}

async function createAndTestRule(browser, credentials, rule, snapshots) {
  const context = await login(
    browser,
    requireAccount(credentials, "knowledge-governor"),
    "knowledge-governor",
  );
  try {
    const ctx = await envelope(context, "rule");
    let definition = await findRule(context, rule.ruleCode, "rule-find");
    if (!definition) {
      const created = ensureOk(
        await apiPost(context, "/engine/rule/rules", {
          ...ctx,
          ruleCode: rule.ruleCode,
          name: rule.name,
          ruleType: rule.ruleType,
          authoringMode: "DSL",
          riskLevel: rule.riskLevel,
          priority: 100,
          dedupeWindowSeconds: 0,
          applicableOrgUnitId: null,
          sourceRef: rule.sourceRef,
          changeSummary: rule.changeSummary,
          dsl: rule.clinicalContent.dsl,
          explanation: rule.clinicalContent.dsl.explain,
          parameterBindings: null,
        }, "rule-create"),
        "创建沙盘规则",
        [201],
      );
      definition = { ruleId: created.ruleId, status: "DRAFT" };
    }
    if (definition.status === "PUBLISHED") {
      return { ruleId: definition.ruleId, alreadyPublished: true };
    }

    const detail = await ruleDetail(context, definition.ruleId, "rule-detail-before-cases");
    const existingCases = new Set((detail.testCases ?? []).map((item) => item.caseType));
    for (const snapshot of snapshots) {
      if (existingCases.has(snapshot.caseType)) continue;
      ensureOk(
        await apiPost(context, `/engine/rule/rules/${definition.ruleId}/test-cases`, {
          ...ctx,
          caseType: snapshot.caseType,
          contextSnapshotId: snapshot.snapshotId,
          expectedHit: snapshot.expectedHit,
          expectedSeverity: snapshot.expectedSeverity ?? null,
          expectedActionCode: snapshot.expectedActionCode ?? null,
        }, `rule-case-${snapshot.caseType}`),
        `创建 ${snapshot.caseType} 测试用例`,
        [201],
      );
    }
    const testRun = ensureOk(
      await apiPost(
        context,
        `/engine/rule/rules/${definition.ruleId}/test`,
        ctx,
        "rule-test",
      ),
      "执行规则测试",
    );
    if ((testRun.results ?? []).some((item) => item.status !== "PASS")) {
      throw new Error(`规则测试未全绿: ${JSON.stringify(testRun.results)}`);
    }
    const positive = snapshots.find((item) => item.caseType === "POSITIVE");
    const snapshotDetail = ensureOk(
      await apiGet(
        context,
        `/engine/context/snapshots/${positive.snapshotId}`,
        "positive-snapshot-detail",
      ),
      "读取阳性快照",
    );
    const simulation = ensureOk(
      await apiPost(context, `/engine/rule/rules/${definition.ruleId}/simulate`, {
        ...ctx,
        context: snapshotDetail.resources,
      }, "rule-simulate"),
      "规则真实快照试运行",
    );
    if (simulation.hit !== true) {
      throw new Error("沙盘金样规则试运行未命中");
    }
    return { ruleId: definition.ruleId, alreadyPublished: false };
  } finally {
    await context.close();
  }
}

async function governanceState(context, ruleId, stage) {
  return (await ruleDetail(context, ruleId, stage)).governance;
}

async function impactDigest(context, ruleId, stage) {
  const impact = ensureOk(
    await apiGet(context, `/engine/rule/rules/${ruleId}/impact`, stage),
    "读取规则影响摘要",
  );
  if (!impact?.impactDigest) throw new Error("规则影响摘要缺少 impactDigest");
  return impact.impactDigest;
}

async function withRole(browser, credentials, role, run) {
  const context = await login(browser, requireAccount(credentials, role), role);
  try {
    return await run(context, await envelope(context, role));
  } finally {
    await context.close();
  }
}

async function transition(browser, credentials, role, ruleId, targetState, reason, publishEvidence) {
  return withRole(browser, credentials, role, async (context, ctx) => {
    const state = await governanceState(context, ruleId, `state-${targetState}`);
    const order = ["DRAFT", "PEER_REVIEW", "COMMITTEE", "SHADOW", "CANARY", "FULL", "MONITOR"];
    if (order.indexOf(state.state) >= order.indexOf(targetState)) return state;
    const digest = await impactDigest(context, ruleId, `impact-${targetState}`);
    return ensureOk(
      await apiPost(context, `/engine/rule/rules/${ruleId}/governance/transitions`, {
        ...ctx,
        targetState,
        impactDigest: digest,
        reason,
        ...(publishEvidence ? { publishEvidence } : {}),
      }, `transition-${targetState}`),
      `推进规则到 ${targetState}`,
    );
  });
}

async function signoff(browser, credentials, role, ruleId, stage, reason, minimumApprovals = 0) {
  return withRole(browser, credentials, role, async (context, ctx) => {
    const current = await governanceState(context, ruleId, `signoff-state-${stage}-${role}`);
    if (stage === "PEER_REVIEW" && current.state !== "PEER_REVIEW") return current;
    if (stage === "COMMITTEE" && (current.committeeApprovalCount ?? 0) >= minimumApprovals) {
      return current;
    }
    return ensureOk(
      await apiPost(context, `/engine/rule/rules/${ruleId}/governance/signoffs`, {
        ...ctx,
        stage,
        decision: "APPROVED",
        reason,
      }, `signoff-${stage}-${role}`),
      `${role} 完成 ${stage} 签署`,
    );
  });
}

async function governRule(browser, credentials, ruleId) {
  await transition(
    browser,
    credentials,
    "knowledge-governor",
    ruleId,
    "PEER_REVIEW",
    "沙盘金样四类测试全绿并完成真实快照试运行，提交同行评审。",
  );
  await signoff(
    browser,
    credentials,
    "clinical-governor",
    ruleId,
    "PEER_REVIEW",
    "确认沙盘金样沿用已验证危急值回报边界，不自动开立或修改医嘱。",
  );
  await signoff(
    browser,
    credentials,
    "clinical-governor",
    ruleId,
    "COMMITTEE",
    "临床治理负责人完成第一名独立会签。",
    1,
  );
  await signoff(
    browser,
    credentials,
    "quality-governor",
    ruleId,
    "COMMITTEE",
    "质量治理员完成第二名独立会签。",
    2,
  );
  await transition(
    browser,
    credentials,
    "organization-admin",
    ruleId,
    "SHADOW",
    "双人独立会签完成，进入影子运行。",
  );
  await transition(
    browser,
    credentials,
    "organization-admin",
    ruleId,
    "CANARY",
    "影子运行完成，进入灰度验证。",
  );
  const signedAt = new Date().toISOString();
  await transition(
    browser,
    credentials,
    "organization-admin",
    ruleId,
    "FULL",
    "灰度验证完成，院级全量激活沙盘金样。",
    {
      electronicSignature: {
        signatureId: `esig-sandbox-${Date.now()}`,
        signerId: requireAccount(credentials, "clinical-governor").username,
        signerName: "临床治理负责人",
        signedAt,
        signatureHash: createHash("sha256")
          .update(`sandbox-full-${ruleId}-${signedAt}`)
          .digest("hex"),
      },
    },
  );
}

export async function runSeed() {
  await mkdir(evidenceDir, { recursive: true });
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(manifest, process.env.SEED_ONLY ?? "");
  const summary = {
    generatedAt: new Date().toISOString(),
    environment: baseUrl,
    runnableRuleCodes: selected.runnable.map((item) => item.ruleCode),
    blockedRuleCodes: selected.blocked.map((item) => item.ruleCode),
    results: [],
    failures: [],
  };
  if (process.env.SEED_VALIDATE_ONLY === "1") {
    return summary;
  }

  const browser = await chromium.launch();
  try {
    const credentials = await loadCredentials();
    for (const rule of selected.runnable) {
      try {
        const snapshots = await seedSnapshots(browser, credentials, rule);
        const created = await createAndTestRule(browser, credentials, rule, snapshots);
        if (!created.alreadyPublished) {
          await governRule(browser, credentials, created.ruleId);
        }
        summary.results.push({
          ruleCode: rule.ruleCode,
          ruleId: created.ruleId,
          snapshotIds: snapshots.map((item) => item.snapshotId),
          result: "PASS",
        });
      } catch (error) {
        summary.failures.push({
          ruleCode: rule.ruleCode,
          detail: String(error).slice(0, 1000),
        });
      }
    }
  } finally {
    await browser.close();
  }
  await writeFile(
    path.join(evidenceDir, "seed-summary.json"),
    `${JSON.stringify(summary, null, 2)}\n`,
    "utf8",
  );
  if (summary.failures.length > 0) {
    throw new Error(`沙盘 seed 存在 ${summary.failures.length} 个失败`);
  }
  return summary;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const summary = await runSeed();
    console.log(JSON.stringify(summary, null, 2));
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
