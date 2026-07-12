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
      ownerPath:
        "medkernel-backend/src/main/resources/catalog/medical-resource-coverage.v1.json",
    }),
    launchGap({
      gapId: "GAP-LAUNCH-15-ENVIRONMENT",
      launchCode: "LAUNCH-15",
      evidenceKey: "launch.target.resource",
      gapKind: "UNCONTROLLED_TARGET_RESOURCE_ABSENT",
      classification: "ENVIRONMENT",
      summary: "人大金仓与达梦真实运行环境尚未接入当前工作机",
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

test("IMPLEMENTATION 缺口必须提供完整可执行修复计划", () => {
  for (const field of [
    "failingTest",
    "implementationPath",
    "consumerReadback",
    "auditReadback",
  ]) {
    const remediationPlan = implementationRemediationPlan();
    delete remediationPlan[field];
    assert.throws(
      () => classifyLaunchGaps([launchGap({ remediationPlan })]),
      new RegExp(`IMPLEMENTATION.*remediationPlan.*${field}.*不能为空`, "u"),
    );
  }

  assert.throws(
    () =>
      classifyLaunchGaps([
        launchGap({
          remediationPlan: implementationRemediationPlan({
            implementationPath: "scripts/release/not-the-owner.mjs",
          }),
        }),
      ]),
    /implementationPath.*必须与 ownerPath 一致/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        launchGap({
          remediationPlan: implementationRemediationPlan({
            consumerReadback: true,
          }),
        }),
      ]),
    /consumerReadback.*不能为空/u,
  );
});

test("IMPLEMENTATION 失败测试可指向仓内 Java、Node 或部署测试", () => {
  const result = classifyLaunchGaps([
    launchGap({
      remediationPlan: implementationRemediationPlan({
        failingTest: "deploy/onprem/tests/validate-medkernel-deploy.sh",
      }),
    }),
  ]);
  assert.equal(
    result.gaps[0].remediationPlan.failingTest,
    "deploy/onprem/tests/validate-medkernel-deploy.sh",
  );
});

test("IMPLEMENTATION 修复计划保持缺口开放，移除已修复项后重跑归零", () => {
  const openResult = classifyLaunchGaps([launchGap()]);
  assert.deepEqual(openResult.implementationClosure, {
    evidenceKey: "launch.gap.implementation.closed",
    status: "OPEN",
    remainingGapCount: 1,
    remainingGapIds: ["GAP-LAUNCH-01-IMPLEMENTATION"],
  });
  assert.deepEqual(openResult.gaps[0].remediationPlan, {
    failingTest: "scripts/release/launch-gap-classifier.test.mjs",
    implementationPath:
      "medkernel-backend/src/main/java/com/medkernel/LaunchService.java",
    consumerReadback: "launch.runtime.implementation.consumer-readback",
    auditReadback: "launch.runtime.implementation.audit-readback",
  });

  const closedResult = classifyLaunchGaps([]);
  assert.deepEqual(closedResult.implementationClosure, {
    evidenceKey: "launch.gap.implementation.closed",
    status: "CLOSED",
    remainingGapCount: 0,
    remainingGapIds: [],
  });
  assert.equal(closedResult.classificationCounts.IMPLEMENTATION, 0);
});

test("TEST 缺口必须绑定仓内可执行测试、真实观察证据与稳定观察码", () => {
  const testGap = launchGap({
    gapId: "GAP-LAUNCH-02-TEST",
    launchCode: "LAUNCH-02",
    evidenceKey: "launch.entry.runtime-observation",
    gapKind: "EXECUTABLE_EVIDENCE_ABSENT",
    classification: "TEST",
    ownerPath: "frontend/e2e/all-done-route-smoke.spec.ts",
  });

  for (const field of [
    "executableTest",
    "observationEvidence",
    "observedCode",
  ]) {
    const remediationPlan = testRemediationPlan();
    delete remediationPlan[field];
    assert.throws(
      () => classifyLaunchGaps([{ ...testGap, remediationPlan }]),
      new RegExp(`TEST.*remediationPlan.*${field}.*不能为空`, "u"),
    );
  }

  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...testGap,
          remediationPlan: testRemediationPlan({ observationEvidence: true }),
        },
      ]),
    /observationEvidence.*不能为空/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...testGap,
          remediationPlan: testRemediationPlan({ observedCode: true }),
        },
      ]),
    /observedCode.*不能为空/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...testGap,
          remediationPlan: testRemediationPlan({ observedCode: "observed" }),
        },
      ]),
    /observedCode.*稳定的大写观察码/u,
  );

  const result = classifyLaunchGaps([testGap]);
  assert.deepEqual(result.testClosure, {
    evidenceKey: "launch.gap.test.closed",
    status: "OPEN",
    remainingGapCount: 1,
    remainingGapIds: ["GAP-LAUNCH-02-TEST"],
  });
  assert.deepEqual(result.gaps[0].remediationPlan, testRemediationPlan());
  assert.deepEqual(classifyLaunchGaps([]).testClosure, {
    evidenceKey: "launch.gap.test.closed",
    status: "CLOSED",
    remainingGapCount: 0,
    remainingGapIds: [],
  });
});

test("DATA 缺口必须绑定唯一资源矩阵与发布生效消费审计闭环", () => {
  const dataGap = launchGap({
    gapId: "GAP-LAUNCH-03-DATA",
    launchCode: "LAUNCH-03",
    evidenceKey: "launch.runtime.published-data",
    gapKind: "PUBLISHED_RUNTIME_DATA_ABSENT",
    classification: "DATA",
    summary: "缺少已发布并由正式消费者回读的医疗资源",
    ownerPath:
      "medkernel-backend/src/main/resources/catalog/medical-resource-coverage.v1.json",
  });

  for (const field of [
    "coverageContract",
    "productionApiEvidence",
    "publicationReadback",
    "effectiveReleaseReadback",
    "consumerReadback",
    "auditReadback",
  ]) {
    const remediationPlan = dataRemediationPlan();
    delete remediationPlan[field];
    assert.throws(
      () => classifyLaunchGaps([{ ...dataGap, remediationPlan }]),
      new RegExp(`DATA.*remediationPlan.*${field}.*不能为空`, "u"),
    );
  }

  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...dataGap,
          remediationPlan: dataRemediationPlan({
            coverageContract:
              "docs/contracts/knowledge/medical-resource-coverage.v1.json",
          }),
        },
      ]),
    /coverageContract.*必须指向唯一医疗资源覆盖矩阵/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...dataGap,
          remediationPlan: dataRemediationPlan({ productionApiEvidence: true }),
        },
      ]),
    /productionApiEvidence.*不能为空/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...dataGap,
          remediationPlan: dataRemediationPlan({
            publicationReadback: "resource.production.api-observation",
          }),
        },
      ]),
    /生产、发布、生效、消费与审计必须使用不同证据键/u,
  );

  const result = classifyLaunchGaps([dataGap]);
  assert.deepEqual(result.dataClosure, {
    evidenceKey: "launch.gap.data.closed",
    status: "OPEN",
    remainingGapCount: 1,
    remainingGapIds: ["GAP-LAUNCH-03-DATA"],
  });
  assert.deepEqual(result.gaps[0].remediationPlan, dataRemediationPlan());
  assert.deepEqual(classifyLaunchGaps([]).dataClosure, {
    evidenceKey: "launch.gap.data.closed",
    status: "CLOSED",
    remainingGapCount: 0,
    remainingGapIds: [],
  });
});

test("ENVIRONMENT 缺口只能引用有目标事实的不可控现场事项并持续阻断上线", () => {
  const environmentGap = launchGap({
    gapId: "GAP-LAUNCH-15-ENVIRONMENT",
    launchCode: "LAUNCH-15",
    evidenceKey: "launch.target.resource",
    gapKind: "UNCONTROLLED_TARGET_RESOURCE_ABSENT",
    classification: "ENVIRONMENT",
    summary: "人大金仓与达梦真实运行环境尚未接入当前工作机",
    ownerPath: "docs/audit/deferred-issues.md",
  });

  for (const field of [
    "deferredIssueId",
    "targetResourceKind",
    "targetFactEvidence",
    "observedCode",
  ]) {
    const remediationPlan = environmentRemediationPlan();
    delete remediationPlan[field];
    assert.throws(
      () => classifyLaunchGaps([{ ...environmentGap, remediationPlan }]),
      new RegExp(`ENVIRONMENT.*remediationPlan.*${field}.*不能为空`, "u"),
    );
  }

  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...environmentGap,
          ownerPath: "scripts/release/launch-gap-classifier.mjs",
        },
      ]),
    /ENVIRONMENT.*ownerPath.*当前待处理问题清单/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...environmentGap,
          summary: "仓库内缺少可执行实现",
        },
      ]),
    /summary.*必须与待处理事项一致/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...environmentGap,
          remediationPlan: environmentRemediationPlan({
            deferredIssueId: "DEFER-999",
          }),
        },
      ]),
    /deferredIssueId.*未登记/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...environmentGap,
          remediationPlan: environmentRemediationPlan({
            targetResourceKind: "REPOSITORY_IMPLEMENTATION",
          }),
        },
      ]),
    /targetResourceKind.*待处理事实不一致/u,
  );
  assert.throws(
    () =>
      classifyLaunchGaps([
        {
          ...environmentGap,
          remediationPlan: environmentRemediationPlan({
            targetFactEvidence: true,
          }),
        },
      ]),
    /targetFactEvidence.*不能为空/u,
  );

  const result = classifyLaunchGaps([environmentGap]);
  assert.deepEqual(result.environmentConstraint, {
    evidenceKey: "launch.gap.environment.honest",
    status: "OPEN",
    blocksLaunch: true,
    remainingGapCount: 1,
    remainingGapIds: ["GAP-LAUNCH-15-ENVIRONMENT"],
    deferredIssueIds: ["DEFER-001"],
  });
  assert.deepEqual(
    result.gaps[0].remediationPlan,
    environmentRemediationPlan(),
  );
  assert.deepEqual(classifyLaunchGaps([]).environmentConstraint, {
    evidenceKey: "launch.gap.environment.honest",
    status: "CLEAR",
    blocksLaunch: false,
    remainingGapCount: 0,
    remainingGapIds: [],
    deferredIssueIds: [],
  });
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
    implementationClosure: {
      evidenceKey: "launch.gap.implementation.closed",
      status: "CLOSED",
      remainingGapCount: 0,
      remainingGapIds: [],
    },
    testClosure: {
      evidenceKey: "launch.gap.test.closed",
      status: "CLOSED",
      remainingGapCount: 0,
      remainingGapIds: [],
    },
    dataClosure: {
      evidenceKey: "launch.gap.data.closed",
      status: "CLOSED",
      remainingGapCount: 0,
      remainingGapIds: [],
    },
    environmentConstraint: {
      evidenceKey: "launch.gap.environment.honest",
      status: "CLEAR",
      blocksLaunch: false,
      remainingGapCount: 0,
      remainingGapIds: [],
      deferredIssueIds: [],
    },
    gaps: [],
  });
});

function launchGap(overrides = {}) {
  const gap = {
    gapId: "GAP-LAUNCH-01-IMPLEMENTATION",
    launchCode: "LAUNCH-01",
    evidenceKey: "launch.runtime.implementation",
    gapKind: "IMPLEMENTATION_ABSENT",
    classification: "IMPLEMENTATION",
    summary: "缺少可执行的运行实现",
    ownerPath:
      "medkernel-backend/src/main/java/com/medkernel/LaunchService.java",
    remediationPlan: implementationRemediationPlan(),
    ...overrides,
  };
  if (
    !Object.hasOwn(overrides, "remediationPlan") &&
    gap.classification === "TEST"
  ) {
    gap.remediationPlan = testRemediationPlan();
  } else if (
    !Object.hasOwn(overrides, "remediationPlan") &&
    gap.classification === "DATA"
  ) {
    gap.remediationPlan = dataRemediationPlan();
  } else if (
    !Object.hasOwn(overrides, "remediationPlan") &&
    gap.classification === "ENVIRONMENT"
  ) {
    gap.remediationPlan = environmentRemediationPlan();
  } else if (
    !["IMPLEMENTATION", "TEST", "DATA", "ENVIRONMENT"].includes(
      gap.classification,
    ) &&
    !Object.hasOwn(overrides, "remediationPlan")
  ) {
    delete gap.remediationPlan;
  }
  return gap;
}

function implementationRemediationPlan(overrides = {}) {
  return {
    failingTest: "scripts/release/launch-gap-classifier.test.mjs",
    implementationPath:
      "medkernel-backend/src/main/java/com/medkernel/LaunchService.java",
    consumerReadback: "launch.runtime.implementation.consumer-readback",
    auditReadback: "launch.runtime.implementation.audit-readback",
    ...overrides,
  };
}

function testRemediationPlan(overrides = {}) {
  return {
    executableTest: "frontend/e2e/all-done-route-smoke.spec.ts",
    observationEvidence: "launch.entry.runtime-observation",
    observedCode: "ENTRY_PERMISSION_ALLOWED",
    ...overrides,
  };
}

function dataRemediationPlan(overrides = {}) {
  return {
    coverageContract:
      "medkernel-backend/src/main/resources/catalog/medical-resource-coverage.v1.json",
    productionApiEvidence: "resource.production.api-observation",
    publicationReadback: "resource.lifecycle.publication-readback",
    effectiveReleaseReadback: "resource.lifecycle.effective-release-readback",
    consumerReadback: "resource.runtime.consumer-readback",
    auditReadback: "resource.lifecycle.audit-readback",
    ...overrides,
  };
}

function environmentRemediationPlan(overrides = {}) {
  return {
    deferredIssueId: "DEFER-001",
    targetResourceKind: "TARGET_DATABASE_RUNTIME",
    targetFactEvidence: "environment.target-database-runtime.observed",
    observedCode: "TARGET_DATABASE_RUNTIME_UNAVAILABLE",
    ...overrides,
  };
}
