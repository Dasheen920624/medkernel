import { readFile } from "node:fs/promises";

const SANDBOX_READY = "SANDBOX_READY";
const SANDBOX_TENANT = "t-rehearsal";
const REQUIRED_CASES = new Set([
  "POSITIVE",
  "NEGATIVE",
  "BOUNDARY",
  "CONFLICT",
]);
const REQUIRED_SOURCE_FIELDS = new Set([
  "sourceType",
  "title",
  "issuingBody",
  "url",
  "retrievedAt",
  "applicability",
]);
const CLINICAL_SETTINGS = new Set([
  "INPATIENT",
  "OUTPATIENT",
  "ED",
  "FOLLOWUP",
]);
const ORG_SCOPE_FIELDS = ["groupIds", "hospitalIds", "deptIds"];
const REMOVED_FIXED_RUNTIME_VERSION_FIELD = "package" + "Version";

export async function loadScenarioRules(
  url = new URL("./scenario-rules.json", import.meta.url),
) {
  const manifest = JSON.parse(await readFile(url, "utf8"));
  validateScenarioRules(manifest);
  return manifest;
}

export function validateScenarioRules(manifest) {
  if (manifest?.schemaVersion !== 2 || !Array.isArray(manifest.scenarios)) {
    throw new Error("沙盘规则清单 schemaVersion/scenarios 无效");
  }
  if (manifest.scenarios.length !== 10) {
    throw new Error(
      `沙盘规则清单必须完整登记 10 条规则，当前 ${manifest.scenarios.length} 条`,
    );
  }
  validateDependencies(manifest.dependencies);
  const ids = new Set();
  const codes = new Set();
  for (const scenario of manifest.scenarios) {
    validateIdentity(scenario, ids, codes);
    validateInstitution(scenario);
    validateSources(scenario);
    validateClinicalContent(scenario);
    if (hasKeyDeep(scenario, REMOVED_FIXED_RUNTIME_VERSION_FIELD)) {
      throw new Error(`沙盘规则 ${scenario.ruleCode} 不得固化机构生效版本`);
    }
  }
  return manifest;
}

function validateDependencies(dependencies) {
  if (!Array.isArray(dependencies) || dependencies.length === 0) {
    throw new Error("沙盘规则清单缺少外圈资产精确版本依赖");
  }
  for (const dependency of dependencies) {
    for (const field of ["assetType", "assetCode", "assetVersion", "purpose"]) {
      if (typeof dependency[field] !== "string" || !dependency[field].trim()) {
        throw new Error(`沙盘外圈资产依赖缺少 ${field}`);
      }
    }
  }
}

function validateIdentity(scenario, ids, codes) {
  for (const field of [
    "id",
    "ruleCode",
    "ruleType",
    "triggerPoint",
    "riskLevel",
    "actionCode",
    "reviewStatus",
    "reviewEvidence",
    "name",
    "sourceRef",
    "changeSummary",
    "disclaimer",
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
  if (scenario.reviewStatus !== SANDBOX_READY) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 必须为 ${SANDBOX_READY}`);
  }
  if (!scenario.disclaimer.includes("仅限演练")) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 缺少演练免责声明`);
  }
}

function validateInstitution(scenario) {
  if (
    scenario.institution?.tenantId !== SANDBOX_TENANT ||
    scenario.institution?.scope !== "SANDBOX_INSTITUTION" ||
    scenario.institution?.customRule !== true
  ) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 演练机构归属无效`);
  }
}

function validateSources(scenario) {
  if (!Array.isArray(scenario.sources) || scenario.sources.length === 0) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 缺少权威来源`);
  }
  for (const source of scenario.sources) {
    for (const field of REQUIRED_SOURCE_FIELDS) {
      if (typeof source[field] !== "string" || !source[field].trim()) {
        throw new Error(`沙盘规则 ${scenario.ruleCode} 权威来源缺少 ${field}`);
      }
    }
    if (!source.url.startsWith("https://")) {
      throw new Error(`沙盘规则 ${scenario.ruleCode} 权威来源必须使用 HTTPS`);
    }
    if (!source.documentNumber && !source.publicationDate && !source.version) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode} 权威来源缺少文号、发布日期或版本`,
      );
    }
  }
}

function validateClinicalContent(scenario) {
  const content = scenario.clinicalContent;
  if (!content?.dsl || !Array.isArray(content.testCases)) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 缺少 DSL 或验证用例`);
  }
  if (Object.hasOwn(content.dsl, "trigger")) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} DSL 不得包含 trigger`,
    );
  }
  if (Object.hasOwn(content.dsl.meta ?? {}, "parameters")) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} DSL 不得声明未绑定 meta.parameters`,
    );
  }
  if (!content.dsl.when) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} DSL 条件无效`);
  }
  validateApplicability(scenario, content.dsl.applicability);
  if (!Array.isArray(content.dsl.then) || content.dsl.then.length === 0) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 缺少执行动作`);
  }
  const caseTypes = new Set(content.testCases.map((item) => item.caseType));
  for (const required of REQUIRED_CASES) {
    if (!caseTypes.has(required)) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode} 缺少 ${required} 验证用例`,
      );
    }
  }
  if (
    caseTypes.size !== REQUIRED_CASES.size ||
    content.testCases.length !== REQUIRED_CASES.size
  ) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} 验证用例类型必须且只能覆盖四类`,
    );
  }
  for (const testCase of content.testCases) {
    if (
      !testCase.patientId ||
      !testCase.encounterId ||
      !testCase.facts ||
      typeof testCase.expectedHit !== "boolean"
    ) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode}/${testCase.caseType} 测试数据不完整`,
      );
    }
    validateTestCaseApplicability(
      scenario,
      testCase,
      content.dsl.applicability.settings,
    );
  }
  for (const action of content.dsl.then) {
    if (!new Set(["info", "warning", "critical"]).has(action.indicator)) {
      throw new Error(`沙盘规则 ${scenario.ruleCode} 动作 indicator 无效`);
    }
    if (
      action.actionCode !== scenario.actionCode ||
      action.atSeverity !== scenario.riskLevel
    ) {
      throw new Error(`沙盘规则 ${scenario.ruleCode} 动作或风险与目录不一致`);
    }
    if (
      ["BLOCK", "STRONG_REMINDER", "SUGGEST_ORDER"].includes(
        action.actionCode,
      ) &&
      action.requiresPhysicianConfirmation !== true
    ) {
      throw new Error(`高风险动作 ${scenario.ruleCode} 缺少医师确认`);
    }
    if (!action.detail?.includes("不自动")) {
      throw new Error(`沙盘规则 ${scenario.ruleCode} 未声明不自动执行临床动作`);
    }
  }
}

function validateTestCaseApplicability(scenario, testCase, settings) {
  if (settings.includes("ED")) return;
  const encounters = testCase.facts.encounters;
  if (
    !Array.isArray(encounters) ||
    !encounters.some((item) => settings.includes(item?.encounterType))
  ) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode}/${testCase.caseType} 缺少匹配适用场景 ${settings.join("|")}`,
    );
  }
}

function validateApplicability(scenario, applicability) {
  if (!isPlainObject(applicability)) {
    throw new Error(`沙盘规则 ${scenario.ruleCode} 缺少 applicability`);
  }
  const population = applicability.population;
  if (!isPlainObject(population)) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} applicability.population 必须是对象`,
    );
  }
  for (const field of ["include", "exclude"]) {
    if (population[field] !== undefined && !isPlainObject(population[field])) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode} applicability.population.${field} 必须是对象`,
      );
    }
  }
  const orgScope = applicability.orgScope;
  if (!isPlainObject(orgScope)) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} applicability.orgScope 必须是对象`,
    );
  }
  for (const field of ORG_SCOPE_FIELDS) {
    const values = orgScope[field];
    if (!Array.isArray(values)) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode} applicability.orgScope.${field} 必须是字符串数组`,
      );
    }
    const unique = new Set();
    for (const value of values) {
      if (typeof value !== "string" || !value.trim()) {
        throw new Error(
          `沙盘规则 ${scenario.ruleCode} applicability.orgScope.${field} 仅允许非空字符串`,
        );
      }
      if (unique.has(value)) {
        throw new Error(
          `沙盘规则 ${scenario.ruleCode} applicability.orgScope.${field} 不允许重复值`,
        );
      }
      unique.add(value);
    }
  }
  if (
    !Array.isArray(applicability.settings) ||
    applicability.settings.length === 0
  ) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} applicability.settings 至少包含一个临床场景`,
    );
  }
  const uniqueSettings = new Set();
  for (const setting of applicability.settings) {
    if (typeof setting !== "string" || !CLINICAL_SETTINGS.has(setting)) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode} applicability.settings 仅允许 INPATIENT/OUTPATIENT/ED/FOLLOWUP`,
      );
    }
    if (uniqueSettings.has(setting)) {
      throw new Error(
        `沙盘规则 ${scenario.ruleCode} applicability.settings 不允许重复值`,
      );
    }
    uniqueSettings.add(setting);
  }
  if (!isPlainObject(applicability.effective)) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} applicability.effective 必须是对象`,
    );
  }
  const rolloutPercent = applicability.effective.rolloutPercent;
  if (
    !Number.isInteger(rolloutPercent) ||
    rolloutPercent < 0 ||
    rolloutPercent > 100
  ) {
    throw new Error(
      `沙盘规则 ${scenario.ruleCode} applicability.effective.rolloutPercent 必须是 0 到 100 的整数`,
    );
  }
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

export function selectSeedRules(manifest, seedOnly = "") {
  const requested = new Set(
    seedOnly
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean),
  );
  const runnable =
    requested.size === 0
      ? manifest.scenarios
      : manifest.scenarios.filter((scenario) =>
          requested.has(scenario.ruleCode),
        );
  const missing = [...requested].filter(
    (code) =>
      !manifest.scenarios.some((scenario) => scenario.ruleCode === code),
  );
  if (missing.length > 0) {
    throw new Error(`SEED_ONLY 包含未知规则: ${missing.join(", ")}`);
  }
  return { runnable, blocked: [] };
}

export function evaluateScenarioCase(scenario, testCase) {
  return evaluateCondition(scenario.clinicalContent.dsl.when, testCase.facts);
}

function evaluateCondition(node, facts) {
  if (Array.isArray(node?.all)) {
    return node.all.every((child) => evaluateCondition(child, facts));
  }
  if (Array.isArray(node?.any)) {
    return node.any.some((child) => evaluateCondition(child, facts));
  }
  if (node?.not) {
    return !evaluateCondition(node.not, facts);
  }
  const values = resolveValues(facts, node?.expr?.field ?? node?.fact);
  const expected = unwrapValue(node?.value);
  switch (node?.operator) {
    case "exists":
      return values.some(isPresent);
    case "is_missing":
      return values.length === 0 || values.every((value) => !isPresent(value));
    case "equals":
      return values.some((value) => Object.is(value, expected));
    case "not_equals":
      return values.every((value) => !Object.is(value, expected));
    case "contains":
      return values.some(
        (value) => typeof value === "string" && value.includes(expected),
      );
    case "in":
      return values.some((value) => expected.includes(value));
    case "not_in":
      return values.every((value) => !expected.includes(value));
    case "gt":
      return values.some((value) => Number(value) > Number(expected));
    case "gte":
      return values.some((value) => Number(value) >= Number(expected));
    case "lt":
      return values.some((value) => Number(value) < Number(expected));
    case "lte":
      return values.some((value) => Number(value) <= Number(expected));
    default:
      throw new Error(
        `离线规则校验不支持算子: ${node?.operator ?? "<missing>"}`,
      );
  }
}

function resolveValues(facts, field) {
  if (typeof field !== "string" || !field) return [];
  let values = [facts];
  for (const rawSegment of field.split(".")) {
    const array = rawSegment.endsWith("[]");
    const segment = array ? rawSegment.slice(0, -2) : rawSegment;
    values = values.flatMap((value) => {
      const next = value?.[segment];
      if (array) return Array.isArray(next) ? next : [];
      return next === undefined ? [] : [next];
    });
  }
  return values;
}

function unwrapValue(value) {
  return value &&
    typeof value === "object" &&
    !Array.isArray(value) &&
    "const" in value
    ? value.const
    : value;
}

function isPresent(value) {
  return value !== null && value !== undefined && value !== "";
}

function hasKeyDeep(value, key) {
  if (!value || typeof value !== "object") return false;
  if (Object.prototype.hasOwnProperty.call(value, key)) return true;
  return Object.values(value).some((item) => hasKeyDeep(item, key));
}
