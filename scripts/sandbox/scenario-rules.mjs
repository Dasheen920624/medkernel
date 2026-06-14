import { readFile } from "node:fs/promises";

const APPROVED = "APPROVED_FOR_SANDBOX";
const CLINICAL_REVIEW_REQUIRED = "CLINICAL_REVIEW_REQUIRED";
const REQUIRED_CASES = new Set(["POSITIVE", "NEGATIVE", "BOUNDARY", "CONFLICT"]);

export async function loadScenarioRules(url = new URL("./scenario-rules.json", import.meta.url)) {
  const manifest = JSON.parse(await readFile(url, "utf8"));
  validateScenarioRules(manifest);
  return manifest;
}

export function validateScenarioRules(manifest) {
  if (manifest?.schemaVersion !== 1 || !Array.isArray(manifest.scenarios)) {
    throw new Error("沙盘规则清单 schemaVersion/scenarios 无效");
  }
  if (manifest.scenarios.length !== 10) {
    throw new Error(`沙盘规则清单必须完整登记 10 条规则，当前 ${manifest.scenarios.length} 条`);
  }
  const ids = new Set();
  const codes = new Set();
  for (const scenario of manifest.scenarios) {
    for (const field of [
      "id",
      "ruleCode",
      "ruleType",
      "triggerPoint",
      "riskLevel",
      "actionCode",
      "reviewStatus",
      "name",
    ]) {
      if (typeof scenario[field] !== "string" || !scenario[field].trim()) {
        throw new Error(`沙盘规则 ${scenario.id ?? "<unknown>"} 缺少 ${field}`);
      }
    }
    if (ids.has(scenario.id) || codes.has(scenario.ruleCode)) {
      throw new Error(`沙盘规则标识重复: ${scenario.id}/${scenario.ruleCode}`);
    }
    ids.add(scenario.id);
    codes.add(scenario.ruleCode);

    if (scenario.reviewStatus === APPROVED) {
      if (!scenario.reviewEvidence || !scenario.sourceRef || !scenario.changeSummary) {
        throw new Error(`已开放规则 ${scenario.ruleCode} 缺少评审证据或来源`);
      }
      const content = scenario.clinicalContent;
      if (!content?.dsl || !Array.isArray(content.testCases)) {
        throw new Error(`已开放规则 ${scenario.ruleCode} 缺少 DSL 或测试用例`);
      }
      const caseTypes = new Set(content.testCases.map((item) => item.caseType));
      for (const required of REQUIRED_CASES) {
        if (!caseTypes.has(required)) {
          throw new Error(`已开放规则 ${scenario.ruleCode} 缺少 ${required} 测试用例`);
        }
      }
      if (!content.dsl.then?.every((action) =>
        action.requiresPhysicianConfirmation === true
        || !["BLOCK", "STRONG_REMINDER", "SUGGEST_ORDER"].includes(action.actionCode))) {
        throw new Error(`高风险动作 ${scenario.ruleCode} 缺少医师确认`);
      }
      continue;
    }

    if (scenario.reviewStatus !== CLINICAL_REVIEW_REQUIRED) {
      throw new Error(`沙盘规则 ${scenario.ruleCode} reviewStatus 非法`);
    }
    if (scenario.clinicalContent !== null || scenario.reviewEvidence !== null) {
      throw new Error(`未评审规则 ${scenario.ruleCode} 不得携带可发布医学内容`);
    }
  }
  return manifest;
}

export function selectSeedRules(manifest, seedOnly = "") {
  const requested = new Set(
    seedOnly
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
  );
  const selected = requested.size === 0
    ? manifest.scenarios
    : manifest.scenarios.filter((scenario) => requested.has(scenario.ruleCode));
  const missing = [...requested].filter(
    (code) => !manifest.scenarios.some((scenario) => scenario.ruleCode === code),
  );
  if (missing.length > 0) {
    throw new Error(`SEED_ONLY 包含未知规则: ${missing.join(", ")}`);
  }
  const blocked = selected.filter((scenario) => scenario.reviewStatus !== APPROVED);
  if (requested.size > 0 && blocked.length > 0) {
    throw new Error(
      `以下规则未完成临床评审，禁止 seed: ${blocked.map((item) => item.ruleCode).join(", ")}`,
    );
  }
  return {
    runnable: selected.filter((scenario) => scenario.reviewStatus === APPROVED),
    blocked,
  };
}
