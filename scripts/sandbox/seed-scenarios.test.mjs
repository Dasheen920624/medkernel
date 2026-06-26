import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { evaluateScenarioCase, loadScenarioRules } from "./scenario-rules.mjs";
import {
  buildCanonicalResources,
  deriveSandboxRuntimeDigest,
  evaluationTestCase,
  resolveChromiumLaunchOptions,
  ruleEvaluationPayload,
  ruleTriggerBindings,
  resolveSandboxEvidenceDir,
  resolveSandboxAccounts,
  sandboxPathwayPrototype,
  RULE_GOVERNANCE_STAGES,
  SANDBOX_ACTOR_ROLES,
} from "./seed-scenarios.mjs";

const manifest = await loadScenarioRules();

test("演练只使用四个可分配职责并遵循无会签的规则发布链", () => {
  assert.deepEqual(RULE_GOVERNANCE_STAGES, [
    "DRAFT",
    "REVIEWED",
    "SHADOW",
    "CANARY",
    "FULL",
    "MONITOR",
  ]);
  assert.deepEqual(
    [...new Set(Object.values(SANDBOX_ACTOR_ROLES))].sort(),
    ["auditor", "engine-operator", "platform-admin"].sort(),
  );
});

test("沙盘证据只写运行时目录且拒绝回写仓库", () => {
  assert.equal(
    resolveSandboxEvidenceDir(
      { MEDKERNEL_RUNTIME_ROOT: "/var/lib/medkernel" },
      "/workspace/medkernel",
    ),
    "/var/lib/medkernel/evidence/current-launch/sandbox",
  );
  assert.throws(
    () =>
      resolveSandboxEvidenceDir(
        {
          DRILL_EVIDENCE_DIR:
            "/workspace/medkernel/tmp/forbidden-evidence/sandbox",
        },
        "/workspace/medkernel",
      ),
    /必须位于代码仓库之外/u,
  );
});

test("沙盘浏览器可显式使用受信演练环境中的 Chromium 可执行文件", () => {
  assert.deepEqual(resolveChromiumLaunchOptions({}), {});
  assert.deepEqual(
    resolveChromiumLaunchOptions({
      MEDKERNEL_PLAYWRIGHT_CHROMIUM_EXECUTABLE:
        "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome",
      MEDKERNEL_PLAYWRIGHT_NO_SANDBOX: "1",
    }),
    {
      executablePath:
        "/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome",
      args: ["--no-sandbox"],
    },
  );
});

test("沙盘只读取统一上线凭据中的演练机构四职责", () => {
  const credentials = canonicalCredentials();
  const accounts = resolveSandboxAccounts(credentials);

  assert.equal(accounts["platform-admin"].tenantId, "t-rehearsal");
  assert.equal(accounts["engine-operator"].role, "engine-operator");
  assert.equal(accounts.auditor.username, "auditor");
  assert.throws(
    () => resolveSandboxAccounts({ roleAccounts: {} }),
    /旧凭据字段|schemaVersion/u,
  );
});

test("机构生效版本明细校验码由演练规则清单内容稳定派生", () => {
  const first = deriveSandboxRuntimeDigest(manifest);
  const second = deriveSandboxRuntimeDigest(structuredClone(manifest));

  assert.match(first, /^sandbox-[a-f0-9]{16}$/u);
  assert.equal(second, first);
  assert.ok(first.startsWith("sandbox-"));

  const changed = structuredClone(manifest);
  changed.scenarios[0].changeSummary += "（变更）";
  assert.notEqual(deriveSandboxRuntimeDigest(changed), first);
});

test("沙盘创建快照前先激活含平台基线资产的当前医院机构生效版本", async () => {
  const source = await readFile(new URL("./seed-scenarios.mjs", import.meta.url), "utf8");
  const runSeed = source.slice(source.indexOf("export async function runSeed"));

  assert.ok(
    runSeed.indexOf("ensureInitialRuntimeRelease(") >= 0 &&
      runSeed.indexOf("ensureInitialRuntimeRelease(") < runSeed.indexOf("seedSnapshots("),
  );
  assert.match(runSeed, /activePlatformBaselineAssets/u);
  assert.match(runSeed, /runtimeBinding/u);
});

test("沙盘外圈路径资产沿用前台真实通用原型且不提交旧容器字段", () => {
  assert.equal(sandboxPathwayPrototype.templateCode, "PATH.CLINICAL.CYCLE");
  assert.equal(sandboxPathwayPrototype.name, "基础节点闭环");
  assert.equal(sandboxPathwayPrototype.diseaseCode, "GENERAL");
  assert.equal(sandboxPathwayPrototype.startNodeCode, "ASSESS");
  assert.equal(sandboxPathwayPrototype.nodes.length, 2);
  assert.deepEqual(
    sandboxPathwayPrototype.nodes.map((item) => item.nodeCode),
    ["ASSESS", "DISPOSITION"],
  );
  assert.equal(sandboxPathwayPrototype.nodes[1].terminal, true);
  assert.deepEqual(sandboxPathwayPrototype.edges, [
    {
      edgeCode: "E-ASSESS-DISPOSITION",
      fromNodeCode: "ASSESS",
      toNodeCode: "DISPOSITION",
      edgeType: "DEFAULT",
      priority: 1,
    },
  ]);
  assert.equal(JSON.stringify(sandboxPathwayPrototype).includes("package"), false);
});

test("沙盘最终发布前先统一准备规则清单声明的外圈资产", async () => {
  const source = await readFile(new URL("./seed-scenarios.mjs", import.meta.url), "utf8");
  const runSeed = source.slice(source.indexOf("export async function runSeed"));
  const ensureOuter = runSeed.indexOf("ensureRequiredOuterAssets(");
  const discoverOuter = runSeed.indexOf("discoverRequiredOuterAssets(");

  assert.ok(ensureOuter >= 0, "缺少外圈资产统一准备步骤");
  assert.ok(discoverOuter >= 0, "缺少外圈资产精确发现步骤");
  assert.ok(ensureOuter < discoverOuter, "外圈资产必须先准备再精确发现");
  assert.match(source, /sandboxPathwayPrototype/u);
  assert.match(source, /activateRequiredPathwayRuntimeAsset/u);
});

test("沙盘正式评估在最终机构生效版本激活后使用新正例快照", async () => {
  const source = await readFile(new URL("./seed-scenarios.mjs", import.meta.url), "utf8");
  const runSeed = source.slice(source.indexOf("export async function runSeed"));
  const finalRelease = runSeed.indexOf("activate-sandbox-final-runtime-release");
  const finalEvaluation = runSeed.indexOf("verifyPublishedRuleEvaluations(");

  assert.ok(finalRelease >= 0, "缺少最终机构生效版本激活");
  assert.ok(finalEvaluation >= 0, "缺少最终正式评估校验");
  assert.ok(finalRelease < finalEvaluation, "正式评估必须晚于最终机构生效版本");
});

test("沙盘规则创建请求显式提交外层执行触发绑定", () => {
  const rule = manifest.scenarios[0];

  assert.deepEqual(ruleTriggerBindings(rule), [
    {
      trigger_point: rule.triggerPoint,
      purpose: "RULE_EXECUTION",
      required_fields: [],
    },
  ]);
});

test("沙盘规则正例校验走正式评估接口并使用已生效快照", () => {
  const rule = manifest.scenarios[0];
  const ctx = { tenant_id: "t-rehearsal", hospital_id: "h-rehearsal" };
  const positive = { caseType: "POSITIVE", snapshotId: "snap-positive" };
  const definition = { ruleId: "rule-positive" };

  assert.deepEqual(ruleEvaluationPayload(ctx, rule, positive, definition), {
    ...ctx,
    triggerPoint: rule.triggerPoint,
    contextSnapshotId: positive.snapshotId,
    eventId: `sandbox-${rule.ruleCode}-POSITIVE`,
    ruleIds: [definition.ruleId],
  });
});

test("沙盘正式评估正例快照不复用铺底四测患者身份", () => {
  const rule = manifest.scenarios[0];
  const positive = rule.clinicalContent.testCases.find(
    (item) => item.caseType === "POSITIVE",
  );
  const evaluation = evaluationTestCase(rule);

  assert.equal(evaluation.caseType, "POSITIVE");
  assert.equal(evaluation.expectedHit, true);
  assert.equal(evaluation.patientId, `${positive.patientId}-EVAL`);
  assert.equal(evaluation.encounterId, `${positive.encounterId}-EVAL`);
});

test("沙盘演练脚本不再创建或发布旧容器", async () => {
  const source = await readFile(new URL("./seed-scenarios.mjs", import.meta.url), "utf8");

  assert.equal(source.includes("/engine/pkg"), false);
  assert.equal(source.includes("package_version"), false);
  assert.equal(source.includes("packageVersion"), false);
  assert.equal(source.includes("packageCode"), false);
  assert.equal(source.includes("packageId"), false);
});

test("全部四十个测试样例都能规范化为完整的十三类标准资源容器", () => {
  for (const scenario of manifest.scenarios) {
    for (const testCase of scenario.clinicalContent.testCases) {
      const resources = buildCanonicalResources(
        testCase,
        "2026-06-19T03:00:00.000Z",
      );
      assert.equal(resources.patient.mpi, testCase.patientId);
      assert.equal(resources.patient.qualityStatus, "VALID");
      assert.equal(Object.keys(resources).length, 13);
      for (const key of [
        "allergyIntolerances",
        "encounters",
        "conditions",
        "nursingAssessments",
        "observations",
        "diagnosticReports",
        "medications",
        "procedures",
        "documents",
        "carePlans",
        "followUps",
        "claims",
      ]) {
        assert.ok(
          Array.isArray(resources[key]),
          `${scenario.ruleCode}/${testCase.caseType}/${key}`,
        );
      }
      for (const resource of Object.values(resources).flatMap((value) =>
        Array.isArray(value) ? value : [value],
      )) {
        assert.equal(resource.mappedVersion, "sandbox-context-v1");
        assert.equal(resource.qualityStatus, "VALID");
      }
    }
  }
});

test("缺字段样例使用非匹配占位值通过 DTO 校验且不伪造医学事实", () => {
  const medicationCase = manifest.scenarios
    .find((item) => item.ruleCode === "SBX.MED.WARFARIN.ASA")
    .clinicalContent.testCases.find(
      (item) => item.caseType === "CONFLICT",
    );
  const claimCase = manifest.scenarios
    .find((item) => item.ruleCode === "SBX.INSURANCE.DRG")
    .clinicalContent.testCases.find(
      (item) => item.caseType === "CONFLICT",
    );
  const recordCase = manifest.scenarios
    .find((item) => item.ruleCode === "SBX.RECORD.COMPLETENESS")
    .clinicalContent.testCases.find((item) => item.caseType === "POSITIVE");

  assert.equal(
    buildCanonicalResources(medicationCase).medications[0].code,
    "UNMAPPED",
  );
  assert.equal(
    buildCanonicalResources(claimCase).claims[0].drgCode,
    "UNASSIGNED",
  );
  assert.equal(buildCanonicalResources(recordCase).patient.birthDate, null);
});

test("出院随访空类型边界在标准资源规范化后仍触发缺随访提醒", () => {
  const rule = manifest.scenarios.find(
    (item) => item.ruleCode === "SBX.DISCHARGE.CHECK",
  );
  const boundary = rule.clinicalContent.testCases.find(
    (item) => item.caseType === "BOUNDARY",
  );
  const resources = buildCanonicalResources(
    boundary,
    "2026-06-19T03:00:00.000Z",
  );

  assert.equal(resources.followUps[0].planType, "UNCLASSIFIED");
  assert.equal(
    evaluateScenarioCase(rule, { ...boundary, facts: resources }),
    true,
  );
});

test("DRG 缺编码样例规范化为占位编码后仍不触发支付缺项提醒", () => {
  const rule = manifest.scenarios.find(
    (item) => item.ruleCode === "SBX.INSURANCE.DRG",
  );
  const conflict = rule.clinicalContent.testCases.find(
    (item) => item.caseType === "CONFLICT",
  );
  const resources = buildCanonicalResources(
    conflict,
    "2026-06-19T03:00:00.000Z",
  );

  assert.equal(resources.claims[0].drgCode, "UNASSIGNED");
  assert.equal(
    evaluateScenarioCase(rule, { ...conflict, facts: resources }),
    false,
  );
});

function canonicalCredentials() {
  const account = (tenantId, role) => ({
    tenantId,
    userId: role,
    username: role,
    displayName: role,
    role,
    assignable: true,
    password: "controlled-password",
  });
  const accounts = (tenantId) => ({
    "platform-admin": account(tenantId, "platform-admin"),
    "engine-operator": account(tenantId, "engine-operator"),
    "clinical-user": account(tenantId, "clinical-user"),
    auditor: account(tenantId, "auditor"),
  });
  return {
    schemaVersion: "1.0.0",
    status: "READY",
    generatedAt: "2026-06-22T08:00:00.000Z",
    platform: {
      tenantId: "t-1",
      takeover: {
        tenantId: "t-1",
        userId: "system-takeover",
        username: "system-takeover",
        displayName: "system-superadmin",
        role: "system-superadmin",
        assignable: false,
        password: "controlled-password",
      },
      accounts: accounts("t-1"),
    },
    rehearsal: {
      tenantId: "t-rehearsal",
      tenantName: "完整上线演练机构",
      hospital: {
        code: "REHEARSAL-HOSPITAL",
        name: "完整上线演练医院",
        facilityType: "HOSPITAL",
      },
      accounts: accounts("t-rehearsal"),
    },
  };
}
