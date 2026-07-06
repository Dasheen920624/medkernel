import path from "node:path";

export type BrowserE2eRunStats = {
  startTime?: string;
  duration?: number;
  expected: number;
  unexpected: number;
  flaky: number;
  skipped?: number;
};

export type BrowserE2eTestResult = {
  file: string;
  title: string;
  status: "passed" | "failed" | "timedOut" | "skipped" | "interrupted";
  outcome?: "skipped" | "expected" | "unexpected" | "flaky";
  attachments?: BrowserE2eAttachment[];
};

export type BrowserE2eAttachment = {
  name: string;
  contentType?: string;
  body?: string;
};

export type LaunchCoverageRow = {
  code: string;
  status: "PASSED";
  evidenceKey: string;
  observedAt: string;
};

export type BrowserE2eLaunchEvidence = {
  schemaVersion: "1.0.0";
  stage: "BROWSER_E2E";
  status: "PASSED" | "FAILED";
  generatedAt: string;
  stats: BrowserE2eRunStats;
  tests: BrowserE2eTestResult[];
  launchCoverage: Record<string, LaunchCoverageRow[]>;
};

type CoverageProof = {
  file: string;
  titleIncludes?: string;
  claims: string[];
  requiresSystemFamilyAttachment?: boolean;
  requiresRealFrontdeskScenarioAttachment?: boolean;
};

const stakeholderClaims = [
  "stakeholderViews:PHYSICIAN",
  "stakeholderViews:NURSE",
  "stakeholderViews:PHARMACIST",
  "stakeholderViews:MEDICAL_TECHNICIAN",
  "stakeholderViews:QUALITY_CONTROLLER",
  "stakeholderViews:PATIENT_PROXY",
  "stakeholderViews:PLATFORM_ADMIN",
  "stakeholderViews:ENGINE_OPERATOR",
  "stakeholderViews:AUDITOR",
  "stakeholderViews:IT_MANAGER",
  "stakeholderViews:IMPLEMENTATION_ENGINEER",
  "stakeholderViews:HOSPITAL_EXECUTIVE",
];

const runtimeReleaseClaims = [
  "productLayers:RELEASE_GOVERNANCE",
  "versionedAssets:KNOWLEDGE",
  "versionedAssets:TERMINOLOGY",
  "versionedAssets:RULE",
  "versionedAssets:PATHWAY",
  "versionedAssets:EVALUATION",
  "versionedAssets:FOLLOWUP",
  "versionedAssets:FIELD_CATALOG",
  "versionedAssets:SAFETY",
  "versionedAssets:CDSS_RISK",
  "versionedAssets:VALUE_SET",
  "versionedAssets:FORMULA",
  "versionedAssets:ORDER_SET",
  "versionedAssets:ACTION_CARD",
  "deliveryShapes:MANAGEMENT_WORKSPACE",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:CLINICAL_RUNTIME",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
];

const thirdPartySystemFamilyClaims = [
  "productLayers:DATA_INTEROPERABILITY",
  "deliveryShapes:API_EVENT",
  "serviceCombinations:THIRD_PARTY_INTERFACE",
  "thirdPartySystemFamilies:HIS_EMR_CDR",
  "thirdPartySystemFamilies:LIS_MONITORING_CRITICAL",
  "thirdPartySystemFamilies:PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
  "thirdPartySystemFamilies:PHARMACY_REVIEW",
  "thirdPartySystemFamilies:NURSING_ANESTHESIA_TRANSFUSION_ICU",
  "thirdPartySystemFamilies:MEDICAL_RECORD_INSURANCE_PAYMENT",
  "thirdPartySystemFamilies:PUBLIC_HEALTH_INFECTION_REGULATORY",
  "thirdPartySystemFamilies:FOLLOWUP_PATIENT_SERVICE",
  "thirdPartySystemFamilies:CA_OIDC_SSO_HR",
  "thirdPartySystemFamilies:REGIONAL_REMOTE",
  "thirdPartySystemFamilies:SPD_UDI_DEVICE",
  "thirdPartySystemFamilies:RESEARCH_ETHICS_DATA",
  "thirdPartySystemFamilies:MODEL_DIFY_AGENT",
];

const realFrontdeskScenarioClaims = [
  "scenarios:S10",
  "scenarios:S11",
  "scenarios:S12",
];

const requiredThirdPartySystemFamilyCodes = thirdPartySystemFamilyClaims
  .filter((claim) => claim.startsWith("thirdPartySystemFamilies:"))
  .map((claim) => claim.split(":")[1]);

const requiredRealFrontdeskScenarioEvidence: Record<string, string[]> = {
  S10: ["前台执行医保审核并联动质量整改"],
  S11: ["前台创建发布并激活 CLAIM 评价指标", "前台提交并复核关闭质量整改任务"],
  S12: ["前台创建随访方案", "前台发布随访方案", "前台生成随访计划并完成问卷与异常回院登记"],
};

const requiredRealFrontdeskScenarioCodes = Object.keys(requiredRealFrontdeskScenarioEvidence);

const coverageProofs: CoverageProof[] = [
  {
    file: "stakeholder-view-rehearsal.spec.ts",
    titleIncludes: "十二类业务视角",
    claims: stakeholderClaims,
  },
  {
    file: "runtime-release-frontdesk.spec.ts",
    titleIncludes: "生成新生效版本并从历史版本回滚",
    claims: runtimeReleaseClaims,
  },
  {
    file: "third-party-system-families-rehearsal.spec.ts",
    titleIncludes: "逐类登记第三方系统族接入并验证断连诚实降级",
    claims: thirdPartySystemFamilyClaims,
    requiresSystemFamilyAttachment: true,
  },
  {
    file: "real-frontdesk-rehearsal.spec.ts",
    titleIncludes: "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
    claims: realFrontdeskScenarioClaims,
    requiresRealFrontdeskScenarioAttachment: true,
  },
];

export function buildBrowserE2eLaunchEvidence(input: {
  stats: BrowserE2eRunStats;
  tests: BrowserE2eTestResult[];
  generatedAt?: string;
}): BrowserE2eLaunchEvidence {
  const generatedAt = input.generatedAt ?? new Date().toISOString();
  const evidence: BrowserE2eLaunchEvidence = {
    schemaVersion: "1.0.0",
    stage: "BROWSER_E2E",
    status: input.stats.unexpected === 0 && input.stats.flaky === 0 ? "PASSED" : "FAILED",
    generatedAt,
    stats: input.stats,
    tests: input.tests,
    launchCoverage: {},
  };
  if (evidence.status !== "PASSED") return evidence;

  for (const proof of coverageProofs) {
    if (hasPassingProof(input.tests, proof)) {
      mergeClaims(evidence.launchCoverage, proof.claims, generatedAt);
    }
  }
  return evidence;
}

function hasPassingProof(tests: BrowserE2eTestResult[], proof: CoverageProof) {
  return tests.some((test) => {
    const fileName = path.basename(test.file);
    return (
      fileName === proof.file &&
      test.status === "passed" &&
      (test.outcome ?? "expected") === "expected" &&
      (!proof.titleIncludes || test.title.includes(proof.titleIncludes)) &&
      (!proof.requiresSystemFamilyAttachment || hasRequiredSystemFamilyAttachment(test)) &&
      (!proof.requiresRealFrontdeskScenarioAttachment ||
        hasRequiredRealFrontdeskScenarioAttachment(test))
    );
  });
}

function hasRequiredSystemFamilyAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "third-party-system-family-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as { systemFamilyCodes?: unknown };
    if (!Array.isArray(parsed.systemFamilyCodes)) return false;
    const observed = parsed.systemFamilyCodes
      .filter((code): code is string => typeof code === "string")
      .sort();
    return JSON.stringify(observed) === JSON.stringify([...requiredThirdPartySystemFamilyCodes].sort());
  } catch {
    return false;
  }
}

function hasRequiredRealFrontdeskScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "real-frontdesk-scenario-codes");
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      scenarioEvidence?: unknown;
    };
    if (!Array.isArray(parsed.scenarioCodes) || !Array.isArray(parsed.scenarioEvidence)) {
      return false;
    }
    const observedCodes = parsed.scenarioCodes
      .filter((code): code is string => typeof code === "string")
      .sort();
    if (
      JSON.stringify(observedCodes) !== JSON.stringify([...requiredRealFrontdeskScenarioCodes].sort())
    ) {
      return false;
    }
    const evidenceByCode = new Map<string, string[]>();
    for (const item of parsed.scenarioEvidence) {
      if (!item || typeof item !== "object") continue;
      const code = (item as { code?: unknown }).code;
      const stages = (item as { observedStages?: unknown }).observedStages;
      if (typeof code !== "string" || !Array.isArray(stages)) continue;
      evidenceByCode.set(
        code,
        stages.filter((stage): stage is string => typeof stage === "string"),
      );
    }
    return requiredRealFrontdeskScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredRealFrontdeskScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function mergeClaims(
  target: Record<string, LaunchCoverageRow[]>,
  claims: string[],
  observedAt: string,
) {
  for (const claim of claims) {
    const [key, code] = claim.split(":");
    if (!key || !code) throw new Error(`无效浏览器覆盖声明 ${claim}`);
    target[key] ??= [];
    target[key].push({
      code,
      status: "PASSED",
      evidenceKey: `launchCoverage.${key}.${code}`,
      observedAt,
    });
  }
}
