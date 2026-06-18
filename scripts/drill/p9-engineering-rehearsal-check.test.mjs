import assert from "node:assert/strict";
import test from "node:test";

import {
  REQUIRED_ENGINEERING_CHECK_CODES,
  assessEngineeringRehearsal,
} from "./p9-engineering-rehearsal-check.mjs";

function manifest(overrides = {}) {
  return {
    checks: REQUIRED_ENGINEERING_CHECK_CODES.map((code) => ({
      code,
      path: `evidence/${code.toLowerCase()}.json`,
    })),
    ...overrides,
  };
}

function passedEvidence(overrides = {}) {
  return {
    status: "PASSED",
    containsCredentials: false,
    containsPatientData: false,
    ...overrides,
  };
}

function evidenceReader(overrides = {}) {
  const calls = [];
  return {
    calls,
    readJson(path) {
      calls.push(path);
      return overrides[path] ?? passedEvidence();
    },
  };
}

test("精确 11 类工程证据均通过时只标记 REHEARSAL_READY", () => {
  const reader = evidenceReader({
    "evidence/evaluation_case_evidence.json": passedEvidence({
      evaluationStatus: "PENDING_REVIEW",
      automatedExpertSignOff: false,
    }),
  });

  const result = assessEngineeringRehearsal(manifest(), {
    readJson: reader.readJson,
  });

  assert.equal(result.status, "PASSED");
  assert.equal(result.stage, "REHEARSAL_READY");
  assert.equal(result.containsCredentials, false);
  assert.equal(result.containsPatientData, false);
  assert.deepEqual(result.failures, []);
  assert.deepEqual(
    result.checks.map((check) => check.code),
    REQUIRED_ENGINEERING_CHECK_CODES,
  );
  assert.equal(result.checks.length, 11);
  assert.equal(JSON.stringify(result).includes("LIVE_ACCEPTED"), false);
});

test("缺少任一必需证据时阻断", () => {
  const checks = manifest().checks.slice(0, -1);
  const result = assessEngineeringRehearsal(
    manifest({ checks }),
    evidenceReader(),
  );

  assert.equal(result.status, "BLOCKED");
  assert.equal(result.stage, "ENGINEERING");
  assert.ok(
    result.failures.some((failure) => failure.includes("缺少必需证据")),
  );
});

test("重复或未知证据类型不能冒充完整集合", () => {
  const checks = manifest().checks;
  const result = assessEngineeringRehearsal(
    manifest({
      checks: [
        ...checks,
        { ...checks[0] },
        { code: "EXPERT_SIGNOFF", path: "evidence/expert-signoff.json" },
      ],
    }),
    evidenceReader(),
  );

  assert.equal(result.status, "BLOCKED");
  assert.ok(
    result.failures.some((failure) => failure.includes("重复证据类型")),
  );
  assert.ok(
    result.failures.some((failure) => failure.includes("未知证据类型")),
  );
});

test("任一证据非 PASSED 时阻断并保留失败项", () => {
  const reader = evidenceReader({
    "evidence/frontend_gates.json": passedEvidence({
      status: "BLOCKED",
      failures: ["lint 存在 warning"],
    }),
  });

  const result = assessEngineeringRehearsal(manifest(), {
    readJson: reader.readJson,
  });

  assert.equal(result.status, "BLOCKED");
  assert.ok(
    result.failures.some(
      (failure) =>
        failure.includes("FRONTEND_GATES") && failure.includes("BLOCKED"),
    ),
  );
});

test("证据缺失、非法 JSON 或读取失败均诚实阻断", () => {
  const reader = evidenceReader();
  reader.readJson = (path) => {
    if (path.endsWith("backend_tests.json")) {
      throw new Error("ENOENT");
    }
    if (path.endsWith("migrations.json")) {
      throw new SyntaxError("Unexpected token");
    }
    return passedEvidence();
  };

  const result = assessEngineeringRehearsal(manifest(), {
    readJson: reader.readJson,
  });

  assert.equal(result.status, "BLOCKED");
  assert.ok(
    result.failures.some((failure) => failure.includes("BACKEND_TESTS")),
  );
  assert.ok(result.failures.some((failure) => failure.includes("MIGRATIONS")));
});

test("凭据或患者数据标志不是显式 false 时不得通过", () => {
  const reader = evidenceReader({
    "evidence/backup_restore.json": {
      status: "PASSED",
      containsCredentials: false,
    },
    "evidence/model_provider.json": passedEvidence({
      containsCredentials: true,
    }),
  });

  const result = assessEngineeringRehearsal(manifest(), {
    readJson: reader.readJson,
  });

  assert.equal(result.status, "BLOCKED");
  assert.equal(result.containsCredentials, true);
  assert.equal(result.containsPatientData, true);
  assert.ok(
    result.failures.some((failure) =>
      failure.includes("安全数据边界声明不完整"),
    ),
  );
});

test("聚合器只读取 manifest 显式列出的 JSON 路径", () => {
  const reader = evidenceReader();
  const input = manifest();

  assessEngineeringRehearsal(input, { readJson: reader.readJson });

  assert.deepEqual(
    reader.calls,
    input.checks.map((check) => check.path),
  );
});
