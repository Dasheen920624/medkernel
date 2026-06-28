import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { join } from "node:path";
import test from "node:test";

const ROOT = process.cwd();
const PERFORMANCE_DIR = join(ROOT, "docs", "handbook", "performance");
const PERFORMANCE_SCRIPTS = [
  "k6-1000-concurrent.js",
  "k6-llm-degradation.js",
];
const CURRENT_PRODUCT_FILES = [
  "docs/DEPLOYMENT_AND_REHEARSAL.md",
  "frontend/src/shared/api/hooks.ts",
  "medkernel-backend/src/main/java/com/medkernel/shared/observability/BusinessMetrics.java",
  "medkernel-backend/src/main/java/com/medkernel/engine/org/OrgUnitController.java",
  "medkernel-backend/src/main/resources/catalog/model-catalog.json",
];
const EVIDENCE_DETAILS_FILES = [
  "docs/CONSTITUTION.md",
  "docs/EXPERIENCE_CONTRACT.md",
  "docs/handbook/performance/k6-1000-concurrent.js",
  "frontend/src/shared/api/hooks.ts",
  "medkernel-backend/src/main/java/com/medkernel/shared/observability/ObservabilityDiagnoseService.java",
  "medkernel-backend/src/main/java/com/medkernel/engine/security/SecurityMeController.java",
  "medkernel-backend/src/main/java/com/medkernel/engine/security/EffectivePermissionProfile.java",
  "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
  "medkernel-backend/src/main/resources/db/migration/postgres/V1__baseline.sql",
  "medkernel-backend/src/main/resources/db/migration/h2/V1__baseline.sql",
  "medkernel-backend/src/main/resources/db/migration/oracle/V1__baseline.sql",
  "medkernel-backend/src/main/resources/db/migration/kingbase/V1__baseline.sql",
  "medkernel-backend/src/main/resources/db/migration/dm/V1__baseline.sql",
];

async function readPerformanceScript(fileName) {
  return readFile(join(PERFORMANCE_DIR, fileName), "utf8");
}

async function readRootFile(fileName) {
  return readFile(join(ROOT, fileName), "utf8");
}

test("性能压测脚本只打当前模型能力契约，不回流退役 LLM 路径", async () => {
  const scripts = await Promise.all(PERFORMANCE_SCRIPTS.map(readPerformanceScript));
  const combined = scripts.join("\n");

  assert.equal(combined.includes("/api/v1/" + "advanced" + "/llm"), false);
  assert.equal(combined.includes("docs/" + "performance/"), false);
  assert.match(combined, /\/api\/v1\/model-capabilities\/tasks/);
  assert.match(combined, /\/api\/v1\/model-capabilities\/status/);
  assert.match(combined, /\/api\/v1\/model-providers/);
});

test("性能压测脚本使用当前产品空间和非敏感输入，不保留旧四域或固定患者病例", async () => {
  const combined = (await Promise.all(PERFORMANCE_SCRIPTS.map(readPerformanceScript))).join("\n");
  const retiredLabels = [
    "试点" + "准备域",
    "临床" + "运行域",
    "质控" + "改进域",
    "合规" + "运维域",
    "LLM " + "Gateway",
  ];
  const patientScenarioTokens = ["胸痛", "AMI", "卒中", "rt-PA", "80 岁"];

  for (const label of retiredLabels) {
    assert.equal(combined.includes(label), false, `${label} 不应作为当前压测分组`);
  }
  for (const token of patientScenarioTokens) {
    assert.equal(combined.includes(token), false, `${token} 不应作为性能脚本固定患者样例`);
  }

  assert.match(combined, /医疗引擎/);
  assert.match(combined, /知识生产/);
  assert.match(combined, /平台管理/);
});

test("当前产品契约文件不保留退役四域作为现行说明", async () => {
  const combined = (await Promise.all(CURRENT_PRODUCT_FILES.map(readRootFile))).join("\n");
  const retiredCurrentLabels = [
    "试点" + "准备",
    "临床" + "运行",
    "质控" + "改进",
    "合规" + "运维",
  ];

  for (const label of retiredCurrentLabels) {
    assert.equal(combined.includes(label), false, `${label} 不应作为当前产品域说明`);
  }

  assert.match(combined, /医疗引擎/);
  assert.match(combined, /知识生产/);
  assert.match(combined, /平台管理/);
});

test("证据详情契约不回流退役体验表达", async () => {
  const combined = (await Promise.all(EVIDENCE_DETAILS_FILES.map(readRootFile))).join("\n");
  const retiredExperienceLabels = ["高级" + "信息", "低频" + "诊断", "技术" + "细节"];

  for (const label of retiredExperienceLabels) {
    assert.equal(combined.includes(label), false, `${label} 不应作为当前体验契约说明`);
  }

  assert.match(combined, /证据详情/);
});
