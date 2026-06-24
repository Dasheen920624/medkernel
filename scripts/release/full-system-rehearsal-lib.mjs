import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { writeJsonAtomic } from "./launch-account-bootstrap-lib.mjs";
import { FULL_KNOWLEDGE_DOMAINS } from "../knowledge/full-knowledge-rehearsal-lib.mjs";

const REPO_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

export function readFullSystemRehearsalConfig(env, options = {}) {
  const repoRoot = path.resolve(options.repoRoot ?? REPO_ROOT);
  const required = [
    "MEDKERNEL_RUNTIME_ROOT",
    "LAUNCH_WEB_BASE_URL",
    "LAUNCH_API_BASE_URL",
    "LAUNCH_BOOTSTRAP_TOKEN_FILE",
    "LAUNCH_CREDENTIALS_FILE",
    "LAUNCH_MODEL_PROVIDER_CODE",
    "LAUNCH_MODEL_PROVIDER_TYPE",
    "LAUNCH_MODEL_PROVIDER_ENDPOINT",
    "LAUNCH_MODEL_VERSION",
    "FULL_KNOWLEDGE_MANIFEST_PATH",
    "LAUNCH_SOURCE",
  ];
  for (const key of required) {
    if (!hasText(env?.[key])) throw new Error(`缺少必填环境变量 ${key}`);
  }
  if (env.E2E_IGNORE_HTTPS_ERRORS === "1") {
    throw new Error("完整上线演练禁止忽略 HTTPS 证书错误");
  }

  const runtimeRoot = outsideRepo(env.MEDKERNEL_RUNTIME_ROOT, repoRoot, "运行时根目录");
  const evidenceRoot = outsideRepo(
    env.FULL_SYSTEM_EVIDENCE_ROOT?.trim() ||
      path.join(runtimeRoot, "evidence/current-launch"),
    repoRoot,
    "整套演练证据目录",
  );
  const credentialsPath = outsideRepo(
    env.LAUNCH_CREDENTIALS_FILE,
    repoRoot,
    "统一上线凭据路径",
  );
  const bootstrapTokenPath = outsideRepo(
    env.LAUNCH_BOOTSTRAP_TOKEN_FILE,
    repoRoot,
    "首次接管令牌路径",
  );
  const webBaseUrl = normalizeWebBaseUrl(env.LAUNCH_WEB_BASE_URL);
  const apiBaseUrl = normalizeApiBaseUrl(env.LAUNCH_API_BASE_URL);
  if (!apiBaseUrl.startsWith(`${webBaseUrl}/`)) {
    throw new Error("上线 Web 与 API 地址必须属于同一 /medkernel 部署上下文");
  }

  return {
    repoRoot,
    runtimeRoot,
    evidenceRoot,
    indexPath: path.join(evidenceRoot, "full-system.json"),
    credentialsPath,
    bootstrapTokenPath,
    manifestPath: path.resolve(env.FULL_KNOWLEDGE_MANIFEST_PATH.trim()),
    source: normalizeSource(env.LAUNCH_SOURCE),
    webBaseUrl,
    apiBaseUrl,
    provider: {
      code: requireText(env.LAUNCH_MODEL_PROVIDER_CODE, "Provider 编码"),
      type: requireText(env.LAUNCH_MODEL_PROVIDER_TYPE, "Provider 类型"),
      endpoint: requireText(env.LAUNCH_MODEL_PROVIDER_ENDPOINT, "Provider 端点"),
      modelVersion: requireText(env.LAUNCH_MODEL_VERSION, "模型版本"),
    },
  };
}

export function buildFullSystemStagePlan(config) {
  const accountEvidence = path.join(config.evidenceRoot, "account-bootstrap.json");
  const modelEvidence = path.join(config.evidenceRoot, "model-provider.json");
  const sandboxRoot = path.join(config.evidenceRoot, "sandbox");
  const knowledgeEvidence = path.join(config.evidenceRoot, "full-knowledge.json");
  const resilienceEvidence = path.join(config.evidenceRoot, "runtime-resilience.json");
  const browserRoot = path.join(config.evidenceRoot, "e2e");
  const common = {
    MEDKERNEL_RUNTIME_ROOT: config.runtimeRoot,
  };
  return [
    {
      id: "account-bootstrap",
      label: "全新接管与四职责账号",
      command: process.execPath,
      args: ["scripts/release/launch-account-bootstrap.mjs"],
      cwd: config.repoRoot,
      evidencePath: accountEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_BOOTSTRAP_TOKEN_FILE: config.bootstrapTokenPath,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        LAUNCH_ACCOUNT_EVIDENCE_PATH: accountEvidence,
      },
    },
    {
      id: "model-provider",
      label: "真实 Provider 探活与医学回归",
      command: process.execPath,
      args: ["scripts/release/model-provider-launch.mjs"],
      cwd: config.repoRoot,
      evidencePath: modelEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        LAUNCH_MODEL_PROVIDER_CODE: config.provider.code,
        LAUNCH_MODEL_PROVIDER_TYPE: config.provider.type,
        LAUNCH_MODEL_PROVIDER_ENDPOINT: config.provider.endpoint,
        LAUNCH_MODEL_VERSION: config.provider.modelVersion,
        FULL_KNOWLEDGE_MANIFEST_PATH: config.manifestPath,
        LAUNCH_MODEL_EVIDENCE_PATH: modelEvidence,
      },
    },
    {
      id: "sandbox",
      label: "演练机构十规则四十用例与机构生效版本",
      command: process.execPath,
      args: ["scripts/sandbox/seed-scenarios.mjs"],
      cwd: config.repoRoot,
      evidencePath: path.join(sandboxRoot, "seed-summary.json"),
      env: {
        ...common,
        DRILL_BASE_URL: new URL(config.webBaseUrl).origin,
        DRILL_EVIDENCE_DIR: sandboxRoot,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
      },
    },
    {
      id: "full-knowledge",
      label: "11 域全知识与 V2 回滚恢复",
      command: process.execPath,
      args: ["scripts/knowledge/full-knowledge-rehearsal.mjs"],
      cwd: config.repoRoot,
      evidencePath: knowledgeEvidence,
      env: {
        ...common,
        FULL_KNOWLEDGE_API_BASE_URL: config.apiBaseUrl,
        FULL_KNOWLEDGE_CREDENTIALS_FILE: config.credentialsPath,
        FULL_KNOWLEDGE_MANIFEST_PATH: config.manifestPath,
        FULL_KNOWLEDGE_PROVIDER_CODE: config.provider.code,
        FULL_KNOWLEDGE_EVIDENCE_PATH: knowledgeEvidence,
      },
    },
    {
      id: "runtime-resilience",
      label: "模型关闭诚实降级、B0 核心可用与恢复启用",
      command: process.execPath,
      args: ["scripts/release/runtime-resilience-rehearsal.mjs"],
      cwd: config.repoRoot,
      evidencePath: resilienceEvidence,
      env: {
        ...common,
        LAUNCH_API_BASE_URL: config.apiBaseUrl,
        LAUNCH_CREDENTIALS_FILE: config.credentialsPath,
        RUNTIME_RESILIENCE_PROVIDER_CODE: config.provider.code,
        RUNTIME_RESILIENCE_EVIDENCE_PATH: resilienceEvidence,
      },
    },
    {
      id: "browser-e2e",
      label: "全页面全职责浏览器旅程",
      command: "npm",
      args: ["run", "e2e"],
      cwd: path.join(config.repoRoot, "frontend"),
      evidencePath: path.join(browserRoot, "report/results.json"),
      env: {
        ...common,
        E2E_EXTERNAL_DEPLOYMENT: "1",
        E2E_BASE_URL: config.webBaseUrl,
        E2E_API_BASE_URL: config.apiBaseUrl,
        E2E_ROLE_CREDENTIALS_FILE: config.credentialsPath,
        E2E_EVIDENCE_DIR: browserRoot,
        E2E_EXPECT_MFA_DISABLED: "1",
      },
    },
  ];
}

export async function runFullSystemRehearsal(config, dependencies = {}) {
  const runCommand = dependencies.runCommand ?? spawnStage;
  const readJson = dependencies.readJson ?? readJsonFile;
  const writeJson = dependencies.writeJson ?? writeJsonAtomic;
  const clock = dependencies.now;
  const startedAt = now(clock);
  const completed = [];

  for (const stage of buildFullSystemStagePlan(config)) {
    const stageStartedAt = now(clock);
    const commandResult = await runCommand(stage);
    if (commandResult?.exitCode !== 0) {
      throw new Error(`${stage.id} 阶段失败（exit=${commandResult?.exitCode ?? "unknown"}）`);
    }
    const evidence = readJson(stage.evidencePath, stage);
    const summary = validateStageEvidence(stage.id, evidence);
    completed.push({
      id: stage.id,
      label: stage.label,
      status: "PASSED",
      startedAt: stageStartedAt,
      finishedAt: now(clock),
      evidencePath: stage.evidencePath,
      summary,
    });
  }

  const index = {
    schemaVersion: "1.0.0",
    status: "PASSED",
    stage: "FULL_SYSTEM_REHEARSAL",
    source: config.source,
    startedAt,
    finishedAt: now(clock),
    webBaseUrl: config.webBaseUrl,
    apiBaseUrl: config.apiBaseUrl,
    coverage: {
      assignableRoles: ["platform-admin", "engine-operator", "clinical-user", "auditor"],
      sandboxRules: 10,
      sandboxCases: 40,
      knowledgeDomains: [...FULL_KNOWLEDGE_DOMAINS],
      knowledgeVersionSequence: ["V1", "V2", "V1", "V2"],
      providerResilienceVerified: true,
      b0CoreVerifiedWithoutModel: true,
      mfaDefaultEnabled: false,
      tlsVerificationSkipped: false,
    },
    stages: completed,
  };
  writeJson(config.indexPath, index);
  return index;
}

export function validateStageEvidence(stageId, evidence) {
  if (!evidence || typeof evidence !== "object" || Array.isArray(evidence)) {
    throw new Error(`${stageId} 阶段证据不是 JSON 对象`);
  }
  switch (stageId) {
    case "account-bootstrap":
      if (evidence.status !== "PASSED" || evidence.verifiedAccountCount !== 9) {
        throw new Error("四职责账号与系统接管身份未完整验证");
      }
      return { verifiedAccountCount: 9, mfaRequired: evidence.mfaRequired };
    case "model-provider":
      if (
        evidence.status !== "PASSED" ||
        evidence.provider?.enabled !== true ||
        evidence.provider?.status !== "HEALTHY" ||
        evidence.evaluation?.status !== "PASSED" ||
        evidence.evaluation?.totalCases !== 3 ||
        evidence.evaluation?.passedCases !== 3 ||
        evidence.evaluation?.failedCases !== 0
      ) {
        throw new Error("Provider 未同时通过真实探活、医学回归与启用门禁");
      }
      return {
        providerCode: evidence.provider.code,
        evaluationCases: evidence.evaluation.totalCases,
      };
    case "sandbox":
      if (
        !Array.isArray(evidence.results) ||
        evidence.results.length !== 10 ||
        evidence.results.some((item) => item?.result !== "PASS") ||
        !Array.isArray(evidence.failures) ||
        evidence.failures.length !== 0 ||
        evidence.runtimeBinding?.ready !== true ||
        evidence.runtimeBinding?.externalSideEffects !== false
      ) {
        throw new Error("演练机构十规则、四十用例或 CURRENT 运行绑定未完整通过");
      }
      return { ruleCount: 10, caseCount: 40, runtimeReady: true };
    case "full-knowledge": {
      const expected = new Set(FULL_KNOWLEDGE_DOMAINS);
      const declared = new Set(evidence.coverage?.expectedDomains ?? []);
      const published = new Set(evidence.coverage?.publishedDomains ?? []);
      const lifecycle = evidence.versionLifecycle;
      const lifecycleValid =
        lifecycle?.v1VersionId != null &&
        lifecycle?.v2VersionId != null &&
        lifecycle.rollbackActiveVersionId === lifecycle.v1VersionId &&
        lifecycle.restoredActiveVersionId === lifecycle.v2VersionId &&
        lifecycle.finalStatus === "ACTIVE";
      if (
        evidence.status !== "PASSED" ||
        declared.size !== 11 ||
        published.size !== 11 ||
        [...expected].some((domain) => !declared.has(domain) || !published.has(domain)) ||
        !lifecycleValid
      ) {
        throw new Error("正式全知识没有完整覆盖 11 个知识域及 V1/V2 回滚恢复");
      }
      return { knowledgeDomainCount: 11, finalVersion: "V2", finalStatus: "ACTIVE" };
    }
    case "runtime-resilience":
      if (
        evidence.status !== "PASSED" ||
        evidence.disabled?.providerEnabled !== false ||
        evidence.disabled?.readinessReady !== false ||
        evidence.disabled?.modelInvocationAllowed !== false ||
        !Array.isArray(evidence.disabled?.blockingRequiredItems) ||
        evidence.disabled.blockingRequiredItems.length !== 1 ||
        evidence.disabled.blockingRequiredItems[0] !== "MODEL_PROVIDER" ||
        evidence.b0?.fixtureCount !== 17 ||
        evidence.b0?.passedCount !== 17 ||
        evidence.b0?.modelRequiredCount !== 0 ||
        evidence.restored?.providerEnabled !== true ||
        evidence.restored?.providerStatus !== "HEALTHY" ||
        evidence.restored?.readinessReady !== true ||
        evidence.restored?.modelInvocationAllowed !== true
      ) {
        throw new Error("模型关闭诚实降级、B0 核心可用或恢复启用证据不完整");
      }
      return {
        disabledBlocker: "MODEL_PROVIDER",
        b0FixtureCount: 17,
        restored: true,
      };
    case "browser-e2e":
      if (
        !Number.isInteger(evidence.stats?.expected) ||
        evidence.stats.expected <= 0 ||
        evidence.stats.unexpected !== 0 ||
        (evidence.stats.flaky ?? 0) !== 0
      ) {
        throw new Error("浏览器全量旅程存在失败、波动或没有实际执行");
      }
      return { passed: evidence.stats.expected, unexpected: 0, flaky: 0 };
    default:
      throw new Error(`未知整套演练阶段 ${stageId}`);
  }
}

function spawnStage(stage) {
  return new Promise((resolve, reject) => {
    const child = spawn(stage.command, stage.args, {
      cwd: stage.cwd,
      env: { ...process.env, ...stage.env },
      stdio: "inherit",
      shell: false,
    });
    child.once("error", reject);
    child.once("close", (exitCode) => resolve({ exitCode }));
  });
}

function readJsonFile(file) {
  try {
    return JSON.parse(readFileSync(file, "utf8"));
  } catch (error) {
    throw new Error(`无法读取阶段证据 ${file}：${error.message}`);
  }
}

function normalizeWebBaseUrl(value) {
  const normalized = normalizeHttpsUrl(value, "上线 Web 地址");
  if (!new URL(normalized).pathname.endsWith("/medkernel")) {
    throw new Error("上线 Web 地址必须以 /medkernel 结尾");
  }
  return normalized;
}

function normalizeApiBaseUrl(value) {
  const normalized = normalizeHttpsUrl(value, "上线 API 地址");
  if (!new URL(normalized).pathname.endsWith("/medkernel/api/v1")) {
    throw new Error("上线 API 地址必须以 /medkernel/api/v1 结尾");
  }
  return normalized;
}

function normalizeHttpsUrl(value, label) {
  const normalized = requireText(value, label).replace(/\/+$/u, "");
  const parsed = new URL(normalized);
  if (parsed.protocol !== "https:" || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error(`${label}必须使用不含凭据、查询和片段的 HTTPS 地址`);
  }
  return normalized;
}

function normalizeSource(value) {
  const source = requireText(value, "LAUNCH_SOURCE");
  if (!/^[a-f0-9]{40}$/iu.test(source)) {
    throw new Error("LAUNCH_SOURCE 必须是 40 位提交哈希");
  }
  return source.toLowerCase();
}

function outsideRepo(value, repoRoot, label) {
  const target = path.resolve(requireText(value, label));
  const relative = path.relative(repoRoot, target);
  if (relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative))) {
    throw new Error(`${label}必须位于代码仓库之外`);
  }
  return target;
}

function now(clock) {
  const value = clock ? clock() : new Date();
  return value instanceof Date ? value.toISOString() : new Date(value).toISOString();
}

function requireText(value, label) {
  if (!hasText(value)) throw new Error(`${label}不能为空`);
  return value.trim();
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}
