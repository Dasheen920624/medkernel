import assert from "node:assert/strict";
import test from "node:test";

import {
  evaluateScenarioCase,
  loadScenarioRules,
  selectSeedRules,
  validateScenarioRules,
} from "./scenario-rules.mjs";

test("十条机构演练规则全部可运行且不依赖固定机构生效版本", async () => {
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(manifest);

  assert.equal(manifest.scenarios.length, 10);
  assert.equal(selected.runnable.length, 10);
  assert.equal(selected.blocked.length, 0);
  assert.deepEqual(manifest.dependencies, [
    {
      assetType: "PATHWAY",
      assetCode: "PATH.ED.DISPOSITION",
      assetVersion: "1",
      purpose: "沙盘急诊路径入径与推进外圈场景",
    },
  ]);
  assert.ok(
    manifest.scenarios.every((item) => item.reviewStatus === "SANDBOX_READY"),
  );
  assert.ok(
    manifest.scenarios.every(
      (item) => item.institution.tenantId === "t-rehearsal",
    ),
  );
  assert.ok(
    manifest.scenarios.every(
      (item) => item.institution.scope === "SANDBOX_INSTITUTION",
    ),
  );
  assert.ok(
    manifest.scenarios.every((item) => item.disclaimer.includes("仅限演练")),
  );
  assert.ok(manifest.scenarios.every((item) => !(("package" + "Version") in item)));
});

test("每条规则都有权威来源、可执行 DSL 和四类可验证样例", async () => {
  const manifest = await loadScenarioRules();

  for (const scenario of manifest.scenarios) {
    assert.ok(scenario.sources.length >= 1, scenario.ruleCode);
    assert.ok(
      scenario.sources.every((source) => source.url.startsWith("https://")),
    );
    assert.ok(
      scenario.sources.every((source) => source.retrievedAt === "2026-06-19"),
    );
    assert.ok(scenario.clinicalContent.dsl.when);
    assert.ok(scenario.clinicalContent.dsl.then.length >= 1);
    assert.ok(
      scenario.clinicalContent.dsl.then.every((action) =>
        ["info", "warning", "critical"].includes(action.indicator),
      ),
    );
    const caseTypes = new Set(
      scenario.clinicalContent.testCases.map((item) => item.caseType),
    );
    assert.deepEqual(
      [...caseTypes].sort(),
      ["BOUNDARY", "MISSING_FIELD", "NEGATIVE", "POSITIVE"],
      scenario.ruleCode,
    );
    for (const testCase of scenario.clinicalContent.testCases) {
      assert.equal(
        evaluateScenarioCase(scenario, testCase),
        testCase.expectedHit,
        `${scenario.ruleCode}/${testCase.caseType}`,
      );
    }
  }
});

test("每条规则的 DSL 适用域满足后端规则契约", async () => {
  const manifest = await loadScenarioRules();

  for (const scenario of manifest.scenarios) {
    const applicability = scenario.clinicalContent.dsl.applicability;
    assert.ok(applicability.population, scenario.ruleCode);
    assert.deepEqual(applicability.orgScope, {
      groupIds: [],
      hospitalIds: [],
      deptIds: [],
    });
    assert.ok(Array.isArray(applicability.settings), scenario.ruleCode);
    assert.ok(applicability.settings.length > 0, scenario.ruleCode);
    assert.ok(Number.isInteger(applicability.effective?.rolloutPercent));
  }
});

test("每条规则的 DSL 不再内嵌触发点", async () => {
  const manifest = await loadScenarioRules();

  for (const scenario of manifest.scenarios) {
    assert.equal(scenario.clinicalContent.dsl.trigger, undefined);
    assert.ok(scenario.triggerPoint, scenario.ruleCode);
  }

  const embeddedTrigger = structuredClone(manifest);
  embeddedTrigger.scenarios[0].clinicalContent.dsl.trigger = "result-review";
  assert.throws(() => validateScenarioRules(embeddedTrigger), /不得包含 trigger/);
});

test("每条规则的 DSL 不声明未绑定参数 schema", async () => {
  const manifest = await loadScenarioRules();

  for (const scenario of manifest.scenarios) {
    assert.equal(scenario.clinicalContent.dsl.meta?.parameters, undefined);
  }

  const withParameters = structuredClone(manifest);
  withParameters.scenarios[0].clinicalContent.dsl.meta = {
    parameters: [{ key: "threshold", valueType: "DECIMAL", required: true }],
  };
  assert.throws(() => validateScenarioRules(withParameters), /meta.parameters/);
});

test("显式选择任一机构规则都可进入铺底", async () => {
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(
    manifest,
    "SBX.MED.WARFARIN.ASA,SBX.RECORD.COMPLETENESS",
  );

  assert.deepEqual(
    selected.runnable.map((item) => item.ruleCode),
    ["SBX.MED.WARFARIN.ASA", "SBX.RECORD.COMPLETENESS"],
  );
  assert.equal(selected.blocked.length, 0);
});

test("十条阳性样例身份与服务端场景目录保持一致", async () => {
  const manifest = await loadScenarioRules();
  const expectedPatients = new Map([
    ["sbx-lab-critical-k", "SBX-LAB-K-001"],
    ["sbx-med-warfarin-asa", "SBX-MED-WA-001"],
    ["sbx-order-contrast-ckd", "SBX-CT-CKD-001"],
    ["sbx-dx-acs", "SBX-ACS-001"],
    ["sbx-report-critical", "SBX-RPT-001"],
    ["sbx-discharge-check", "SBX-DC-001"],
    ["sbx-followup-inr", "SBX-FU-001"],
    ["sbx-insurance-drg", "SBX-INS-001"],
    ["sbx-quality-record", "SBX-QC-001"],
    ["sbx-record-completeness", "SBX-REC-001"],
  ]);

  for (const scenario of manifest.scenarios) {
    const positive = scenario.clinicalContent.testCases.find(
      (testCase) => testCase.caseType === "POSITIVE",
    );
    assert.equal(positive.patientId, expectedPatients.get(scenario.id));
  }
});

test("缺来源、缺机构归属或缺四类样例会被清单校验拒绝", async () => {
  const manifest = await loadScenarioRules();

  const missingSource = structuredClone(manifest);
  missingSource.scenarios[0].sources = [];
  assert.throws(() => validateScenarioRules(missingSource), /权威来源/);

  const wrongOwner = structuredClone(manifest);
  wrongOwner.scenarios[0].institution.tenantId = "__platform__";
  assert.throws(() => validateScenarioRules(wrongOwner), /演练机构归属/);

  const missingCase = structuredClone(manifest);
  missingCase.scenarios[0].clinicalContent.testCases.pop();
  assert.throws(() => validateScenarioRules(missingCase), /MISSING_FIELD/);

  const missingDependencyVersion = structuredClone(manifest);
  delete missingDependencyVersion.dependencies[0].assetVersion;
  assert.throws(
    () => validateScenarioRules(missingDependencyVersion),
    /外圈资产依赖缺少 assetVersion/,
  );

  const missingPopulation = structuredClone(manifest);
  delete missingPopulation.scenarios[0].clinicalContent.dsl.applicability
    .population;
  assert.throws(() => validateScenarioRules(missingPopulation), /population/);

  const missingOrgScope = structuredClone(manifest);
  delete missingOrgScope.scenarios[0].clinicalContent.dsl.applicability.orgScope;
  assert.throws(() => validateScenarioRules(missingOrgScope), /orgScope/);
});
