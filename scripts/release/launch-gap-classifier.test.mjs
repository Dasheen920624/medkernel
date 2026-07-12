import assert from "node:assert/strict";
import test from "node:test";

import {
  LAUNCH_GAP_CLASSIFICATIONS,
  classifyLaunchGaps,
} from "./launch-gap-classifier.mjs";

test("上线缺口只能按固定原因唯一归入四类", () => {
  const result = classifyLaunchGaps([
    launchGap({
      gapId: "GAP-LAUNCH-01-IMPLEMENTATION",
      launchCode: "LAUNCH-01",
      evidenceKey: "launch.runtime.implementation",
      gapKind: "IMPLEMENTATION_ABSENT",
      classification: "IMPLEMENTATION",
      ownerPath:
        "medkernel-backend/src/main/java/com/medkernel/LaunchService.java",
    }),
    launchGap({
      gapId: "GAP-LAUNCH-02-TEST",
      launchCode: "LAUNCH-02",
      evidenceKey: "launch.runtime.observation",
      gapKind: "EXECUTABLE_EVIDENCE_ABSENT",
      classification: "TEST",
      ownerPath: "scripts/release/launch-coverage-audit.test.mjs",
    }),
    launchGap({
      gapId: "GAP-LAUNCH-03-DATA",
      launchCode: "LAUNCH-03",
      evidenceKey: "launch.runtime.published-data",
      gapKind: "PUBLISHED_RUNTIME_DATA_ABSENT",
      classification: "DATA",
      ownerPath: "docs/contracts/knowledge/medical-resource-coverage.v1.json",
    }),
    launchGap({
      gapId: "GAP-LAUNCH-15-ENVIRONMENT",
      launchCode: "LAUNCH-15",
      evidenceKey: "launch.target.resource",
      gapKind: "UNCONTROLLED_TARGET_RESOURCE_ABSENT",
      classification: "ENVIRONMENT",
      ownerPath: "docs/audit/deferred-issues.md",
    }),
  ]);

  assert.deepEqual(LAUNCH_GAP_CLASSIFICATIONS, [
    "IMPLEMENTATION",
    "TEST",
    "DATA",
    "ENVIRONMENT",
  ]);
  assert.equal(result.schemaVersion, "1.0.0");
  assert.equal(result.evidenceKey, "launch.gap.classification");
  assert.equal(result.gapCount, 4);
  assert.equal(result.unclassifiedCount, 0);
  assert.deepEqual(result.classificationCounts, {
    IMPLEMENTATION: 1,
    TEST: 1,
    DATA: 1,
    ENVIRONMENT: 1,
  });
  assert.deepEqual(
    result.gaps.map((gap) => gap.classification),
    LAUNCH_GAP_CLASSIFICATIONS,
  );
});

test("未知分类或一项声明多个分类时拒绝", () => {
  assert.throws(
    () => classifyLaunchGaps([launchGap({ classification: "UNKNOWN" })]),
    /未知缺口分类.*UNKNOWN/u,
  );
  assert.throws(
    () => classifyLaunchGaps([launchGap({ classification: ["TEST", "DATA"] })]),
    /classification.*只能声明一个/u,
  );
});

test("缺口分类必须与固定缺口原因一致", () => {
  assert.throws(
    () =>
      classifyLaunchGaps([
        launchGap({
          gapKind: "IMPLEMENTATION_ABSENT",
          classification: "ENVIRONMENT",
        }),
      ]),
    /IMPLEMENTATION_ABSENT.*只能归入 IMPLEMENTATION/u,
  );
  for (const gapKind of ["UNRECOGNIZED_GAP_KIND", "__proto__"]) {
    assert.throws(
      () => classifyLaunchGaps([launchGap({ gapKind })]),
      new RegExp(`未知缺口原因.*${gapKind}`, "u"),
    );
  }
});

test("无 ownerPath 或非规范仓库相对路径的缺口拒绝", () => {
  const withoutOwnerPath = launchGap();
  delete withoutOwnerPath.ownerPath;
  assert.throws(
    () => classifyLaunchGaps([withoutOwnerPath]),
    /ownerPath.*不能为空/u,
  );
  for (const ownerPath of [
    "/tmp/fix.mjs",
    "../outside/fix.mjs",
    "scripts//release/fix.mjs",
    "scripts\\release\\fix.mjs",
  ]) {
    assert.throws(
      () => classifyLaunchGaps([launchGap({ ownerPath })]),
      /ownerPath.*仓库相对路径/u,
    );
  }
});

test("缺口 ID 与 LAUNCH/证据键组合不得重复且每项字段必须完整", () => {
  const gap = launchGap();
  assert.throws(
    () => classifyLaunchGaps([gap, structuredClone(gap)]),
    /重复缺口 ID.*GAP-LAUNCH-01-IMPLEMENTATION/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        gap,
        launchGap({
          gapId: "GAP-LAUNCH-01-TEST",
          gapKind: "EXECUTABLE_EVIDENCE_ABSENT",
          classification: "TEST",
          ownerPath: "scripts/release/launch-coverage-audit.test.mjs",
        }),
      ]),
    /重复缺口键.*LAUNCH-01.*launch\.runtime\.implementation/u,
  );

  const missingEvidenceKey = launchGap();
  delete missingEvidenceKey.evidenceKey;
  assert.throws(
    () => classifyLaunchGaps([missingEvidenceKey]),
    /evidenceKey.*不能为空/u,
  );
});

test("无缺口时输出四类严格归零结果", () => {
  assert.deepEqual(classifyLaunchGaps([]), {
    schemaVersion: "1.0.0",
    evidenceKey: "launch.gap.classification",
    gapCount: 0,
    unclassifiedCount: 0,
    classificationCounts: {
      IMPLEMENTATION: 0,
      TEST: 0,
      DATA: 0,
      ENVIRONMENT: 0,
    },
    gaps: [],
  });
});

function launchGap(overrides = {}) {
  return {
    gapId: "GAP-LAUNCH-01-IMPLEMENTATION",
    launchCode: "LAUNCH-01",
    evidenceKey: "launch.runtime.implementation",
    gapKind: "IMPLEMENTATION_ABSENT",
    classification: "IMPLEMENTATION",
    summary: "缺少可执行的运行实现",
    ownerPath:
      "medkernel-backend/src/main/java/com/medkernel/LaunchService.java",
    ...overrides,
  };
}
