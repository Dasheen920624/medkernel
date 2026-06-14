import assert from "node:assert/strict";
import test from "node:test";

import {
  loadScenarioRules,
  selectSeedRules,
  validateScenarioRules,
} from "./scenario-rules.mjs";

test("完整规则清单仅开放已验证金样，其余场景保持临床门禁", async () => {
  const manifest = await loadScenarioRules();
  const selected = selectSeedRules(manifest);

  assert.equal(manifest.scenarios.length, 10);
  assert.deepEqual(selected.runnable.map((item) => item.ruleCode), [
    "SBX.LAB.CRITICAL.K",
  ]);
  assert.equal(selected.blocked.length, 9);
});

test("显式选择未评审规则会被拒绝", async () => {
  const manifest = await loadScenarioRules();

  assert.throws(
    () => selectSeedRules(manifest, "SBX.MED.WARFARIN.ASA"),
    /未完成临床评审/,
  );
});

test("未评审规则携带医学内容会被清单校验拒绝", async () => {
  const manifest = structuredClone(await loadScenarioRules());
  manifest.scenarios[1].clinicalContent = { dsl: {} };

  assert.throws(() => validateScenarioRules(manifest), /不得携带可发布医学内容/);
});
