import assert from "node:assert/strict";
import test from "node:test";

import { loadScenarioRules } from "./scenario-rules.mjs";
import {
  buildCanonicalResources,
  deriveSandboxPackageVersion,
} from "./seed-scenarios.mjs";

const manifest = await loadScenarioRules();

test("配置包版本由演练规则清单内容稳定派生", () => {
  const first = deriveSandboxPackageVersion(manifest);
  const second = deriveSandboxPackageVersion(structuredClone(manifest));

  assert.match(first, /^sandbox-[a-f0-9]{16}$/u);
  assert.equal(second, first);
  assert.ok(first.startsWith("sandbox-"));

  const changed = structuredClone(manifest);
  changed.scenarios[0].changeSummary += "（变更）";
  assert.notEqual(deriveSandboxPackageVersion(changed), first);
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
      (item) => item.caseType === "MISSING_FIELD",
    );
  const claimCase = manifest.scenarios
    .find((item) => item.ruleCode === "SBX.INSURANCE.DRG")
    .clinicalContent.testCases.find(
      (item) => item.caseType === "MISSING_FIELD",
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
