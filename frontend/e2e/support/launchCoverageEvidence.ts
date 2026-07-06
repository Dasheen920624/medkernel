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
  requiresServiceOrganizationScenarioAttachment?: boolean;
  requiresMfaLoginScenarioAttachment?: boolean;
  requiresDiagnosisKnowledgeScenarioAttachment?: boolean;
  requiresSourceLineageAttachment?: boolean;
  requiresEmbedBusinessHostAttachment?: boolean;
  requiresPathwayLifecycleAttachment?: boolean;
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

const realFrontdeskScenarioClaims = ["scenarios:S10", "scenarios:S11", "scenarios:S12"];

const serviceOrganizationClaims = [
  "scenarios:S1",
  "scenarios:S14",
  "organizationLevels:HOSPITAL",
  "organizationLevels:DEPARTMENT",
  "serviceCombinations:ONBOARDING_INTEGRATION",
  "serviceCombinations:COMPLIANCE_OPERATIONS",
];

const mfaLoginClaims = [
  "scenarios:S14",
  "productLayers:FOUNDATION_GOVERNANCE",
  "serviceCombinations:COMPLIANCE_OPERATIONS",
];

const diagnosisKnowledgeClaims = [
  "scenarios:S3",
  "productLayers:MEDICAL_ASSET",
  "semanticFamilies:DISEASE_DIAGNOSIS",
  "specialtyDomains:CLINICAL_SPECIALTIES",
];

const sourceLineageClaims = ["scenarios:S7", "semanticFamilies:SOURCE_VALIDITY"];

const embedBusinessHostClaims = [
  "scenarios:S8",
  "productLayers:DELIVERY_FEEDBACK",
  "deliveryShapes:EMBEDDED_COMPONENT",
];

const pathwayLifecycleClaims = [
  "scenarios:S6",
  "productLayers:CLINICAL_EXECUTION",
  "serviceCombinations:SPECIAL_DISEASE_PATHWAY",
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

const requiredServiceOrganizationScenarioEvidence: Record<string, string[]> = {
  S1: [
    "前台开通服务机构",
    "机构管理员首次登录并改密",
    "前台创建医疗机构与科室",
    "前台回读服务机构组织树",
  ],
  S14: ["前台创建临床账号并绑定科室职责范围", "临床账号首次登录后读取权限画像", "前台停用演练账号"],
};

const requiredServiceOrganizationScenarioCodes = Object.keys(
  requiredServiceOrganizationScenarioEvidence,
);

const requiredMfaLoginScenarioEvidence: Record<string, string[]> = {
  S14: [
    "配置中心读取上线默认 MFA 关闭",
    "创建 MFA 临时平台管理员账号",
    "临时账号完成首次改密并绑定 TOTP",
    "配置中心临时开启 MFA",
    "登录页要求已绑定账号完成 MFA 验证",
    "前台提交真实 TOTP 验证并进入工作台",
    "验证后回读权限画像与 MFA 状态",
    "恢复 MFA 上线默认关闭状态",
    "停用 MFA 演练临时管理员账号",
  ],
};

const requiredMfaLoginScenarioCodes = Object.keys(requiredMfaLoginScenarioEvidence);

const requiredDiagnosisKnowledgeScenarioEvidence: Record<string, string[]> = {
  S3: [
    "前台登记标准发现项术语",
    "前台创建证据完整诊断资产草稿",
    "前台登记诊断标准",
    "前台登记验证病例",
  ],
};

const requiredDiagnosisKnowledgeScenarioCodes = Object.keys(
  requiredDiagnosisKnowledgeScenarioEvidence,
);

const requiredSourceLineageScenarioEvidence: Record<string, string[]> = {
  S7: [
    "真实登记受控来源、版本和锚点",
    "真实提交并审核激活带来源引用的知识候选",
    "真实绑定来源引用并回读血缘证据",
    "真实重建知识关系投影",
    "前台探索知识关系图并查看追踪证据",
  ],
};

const requiredSourceLineageScenarioCodes = Object.keys(requiredSourceLineageScenarioEvidence);

const requiredEmbedBusinessHostScenarioEvidence: Record<string, string[]> = {
  S8: [
    "真实签发一次性嵌入启动凭证",
    "独立业务系统宿主加载真实 iframe 启动地址",
    "嵌入终端真实兑换启动凭证并读取当前就诊上下文",
    "嵌入终端真实读取当前就诊推荐卡",
    "医师在嵌入终端提交采纳反馈",
    "独立业务系统宿主收到医师反馈 postMessage",
  ],
};

const requiredEmbedBusinessHostScenarioCodes = Object.keys(
  requiredEmbedBusinessHostScenarioEvidence,
);

const requiredPathwayLifecycleScenarioEvidence: Record<string, string[]> = {
  S6: [
    "前台创建专病路径草稿并保存节点边时钟",
    "后端回读路径节点边时钟与十阶段里程碑",
    "前台使用真实 ACTIVE 快照完成草稿试运行",
    "真实服务链路对已保存路径执行仿真",
    "临床用户基于当前机构生效版本读取入径候选",
    "临床用户办理患者入径并生成首个关键时钟",
    "临床用户完成当前节点并标准推进",
    "真实后端登记路径变异与处置决策",
    "真实后端完成随访接续终点节点",
    "后端回读关键时钟和变异事实",
    "路径完成后生成随访接续证据",
  ],
};

const requiredPathwayLifecycleScenarioCodes = Object.keys(
  requiredPathwayLifecycleScenarioEvidence,
);

const requiredPathwayMilestoneStages = [
  "SCREENING_TRIAGE",
  "DIAGNOSIS_DIFFERENTIAL",
  "RISK_STRATIFICATION",
  "TREATMENT_DECISION",
  "EXECUTION_CANDIDATE",
  "MONITORING_WARNING",
  "DISCHARGE_REFERRAL",
  "REHAB_EDUCATION_FOLLOWUP",
  "OUTCOME_EVALUATION",
  "QUALITY_ITERATION",
];

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
    titleIncludes:
      "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
    claims: realFrontdeskScenarioClaims,
    requiresRealFrontdeskScenarioAttachment: true,
  },
  {
    file: "service-organization-frontdesk.spec.ts",
    titleIncludes: "平台开通服务机构后，新机构可完成组织树、科室账号、职责范围和登录画像闭环",
    claims: serviceOrganizationClaims,
    requiresServiceOrganizationScenarioAttachment: true,
  },
  {
    file: "mfa-login-frontdesk.spec.ts",
    titleIncludes: "开启 MFA 后已绑定账号必须在登录页完成真实 TOTP 验证",
    claims: mfaLoginClaims,
    requiresMfaLoginScenarioAttachment: true,
  },
  {
    file: "diagnosis-knowledge-maintenance.spec.ts",
    titleIncludes: "运营员从前台创建证据完整诊断资产并登记标准与验证病例",
    claims: diagnosisKnowledgeClaims,
    requiresDiagnosisKnowledgeScenarioAttachment: true,
  },
  {
    file: "d6-graph-explore.spec.ts",
    titleIncludes: "医疗引擎运营员可重建并探索真实知识投影",
    claims: sourceLineageClaims,
    requiresSourceLineageAttachment: true,
  },
  {
    file: "embed-business-host.spec.ts",
    titleIncludes: "独立业务系统宿主通过真实嵌入凭证完成 iframe 启动并接收医师反馈",
    claims: embedBusinessHostClaims,
    requiresEmbedBusinessHostAttachment: true,
  },
  {
    file: "pathway-lifecycle-frontdesk.spec.ts",
    titleIncludes: "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
    claims: pathwayLifecycleClaims,
    requiresPathwayLifecycleAttachment: true,
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
        hasRequiredRealFrontdeskScenarioAttachment(test)) &&
      (!proof.requiresServiceOrganizationScenarioAttachment ||
        hasRequiredServiceOrganizationScenarioAttachment(test)) &&
      (!proof.requiresMfaLoginScenarioAttachment || hasRequiredMfaLoginScenarioAttachment(test)) &&
      (!proof.requiresDiagnosisKnowledgeScenarioAttachment ||
        hasRequiredDiagnosisKnowledgeScenarioAttachment(test)) &&
      (!proof.requiresSourceLineageAttachment || hasRequiredSourceLineageAttachment(test)) &&
      (!proof.requiresEmbedBusinessHostAttachment || hasRequiredEmbedBusinessHostAttachment(test)) &&
      (!proof.requiresPathwayLifecycleAttachment || hasRequiredPathwayLifecycleAttachment(test))
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
    return (
      JSON.stringify(observed) === JSON.stringify([...requiredThirdPartySystemFamilyCodes].sort())
    );
  } catch {
    return false;
  }
}

function hasRequiredRealFrontdeskScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "real-frontdesk-scenario-codes",
  );
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
      JSON.stringify(observedCodes) !==
      JSON.stringify([...requiredRealFrontdeskScenarioCodes].sort())
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

function hasRequiredServiceOrganizationScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "service-organization-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      organizationLevels?: unknown;
      serviceCombinations?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredServiceOrganizationScenarioCodes) ||
      !arrayEquals(parsed.organizationLevels, ["HOSPITAL", "DEPARTMENT"]) ||
      !arrayEquals(parsed.serviceCombinations, [
        "ONBOARDING_INTEGRATION",
        "COMPLIANCE_OPERATIONS",
      ]) ||
      !Array.isArray(parsed.scenarioEvidence)
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
    return requiredServiceOrganizationScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredServiceOrganizationScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredMfaLoginScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find((item) => item.name === "mfa-login-scenario-codes");
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      serviceCombinations?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredMfaLoginScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["FOUNDATION_GOVERNANCE"]) ||
      !arrayEquals(parsed.serviceCombinations, ["COMPLIANCE_OPERATIONS"]) ||
      !Array.isArray(parsed.scenarioEvidence)
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
    return requiredMfaLoginScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredMfaLoginScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredDiagnosisKnowledgeScenarioAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "diagnosis-knowledge-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      semanticFamilies?: unknown;
      specialtyDomains?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredDiagnosisKnowledgeScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["MEDICAL_ASSET"]) ||
      !arrayEquals(parsed.semanticFamilies, ["DISEASE_DIAGNOSIS"]) ||
      !arrayEquals(parsed.specialtyDomains, ["CLINICAL_SPECIALTIES"]) ||
      !Array.isArray(parsed.scenarioEvidence)
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
    return requiredDiagnosisKnowledgeScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredDiagnosisKnowledgeScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredSourceLineageAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "source-lineage-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      semanticFamilies?: unknown;
      apiEvidence?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredSourceLineageScenarioCodes) ||
      !arrayEquals(parsed.semanticFamilies, ["SOURCE_VALIDITY"]) ||
      !hasCompleteSourceLineageApiEvidence(parsed.apiEvidence) ||
      !Array.isArray(parsed.scenarioEvidence)
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
    return requiredSourceLineageScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredSourceLineageScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredEmbedBusinessHostAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "embed-business-host-launch-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      deliveryShapes?: unknown;
      apiEvidence?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredEmbedBusinessHostScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["DELIVERY_FEEDBACK"]) ||
      !arrayEquals(parsed.deliveryShapes, ["EMBEDDED_COMPONENT"]) ||
      !hasCompleteEmbedApiEvidence(parsed.apiEvidence) ||
      !Array.isArray(parsed.scenarioEvidence)
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
    return requiredEmbedBusinessHostScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredEmbedBusinessHostScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasRequiredPathwayLifecycleAttachment(test: BrowserE2eTestResult) {
  const attachment = test.attachments?.find(
    (item) => item.name === "pathway-lifecycle-scenario-codes",
  );
  if (!attachment?.body) return false;
  try {
    const parsed = JSON.parse(attachment.body) as {
      scenarioCodes?: unknown;
      productLayers?: unknown;
      serviceCombinations?: unknown;
      specialDiseaseStages?: unknown;
      apiEvidence?: unknown;
      scenarioEvidence?: unknown;
    };
    if (
      !arrayEquals(parsed.scenarioCodes, requiredPathwayLifecycleScenarioCodes) ||
      !arrayEquals(parsed.productLayers, ["CLINICAL_EXECUTION"]) ||
      !arrayEquals(parsed.serviceCombinations, ["SPECIAL_DISEASE_PATHWAY"]) ||
      !arrayEquals(parsed.specialDiseaseStages, requiredPathwayMilestoneStages) ||
      !hasCompletePathwayLifecycleApiEvidence(parsed.apiEvidence) ||
      !Array.isArray(parsed.scenarioEvidence)
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
    return requiredPathwayLifecycleScenarioCodes.every((code) => {
      const observedStages = evidenceByCode.get(code) ?? [];
      return requiredPathwayLifecycleScenarioEvidence[code].every((stage) =>
        observedStages.includes(stage),
      );
    });
  } catch {
    return false;
  }
}

function hasCompleteEmbedApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "launchTokenIssued",
    "launchExchanged",
    "recommendationsRead",
    "feedbackSubmitted",
    "hostMessageReceived",
  ].every((key) => evidence[key] === true);
}

function hasCompleteSourceLineageApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "sourceRegistered",
    "sourceVersionRegistered",
    "sourceFragmentRegistered",
    "knowledgeCandidateSubmitted",
    "citationBound",
    "candidateApproved",
    "graphProjectionRebuilt",
    "provenanceReadback",
    "graphNodeExplored",
    "traceEvidenceVisible",
  ].every((key) => evidence[key] === true);
}

function hasCompletePathwayLifecycleApiEvidence(value: unknown) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const evidence = value as Record<string, unknown>;
  return [
    "templateSaved",
    "templateReadback",
    "draftPreviewRun",
    "templateSimulated",
    "entryCandidatesRead",
    "patientEntered",
    "standardAdvanced",
    "varianceRecorded",
    "followupHandoffCreated",
    "clocksRead",
    "variancesRead",
    "followupHandoffObserved",
  ].every((key) => evidence[key] === true);
}

function arrayEquals(value: unknown, expected: string[]) {
  if (!Array.isArray(value)) return false;
  const observed = value.filter((item): item is string => typeof item === "string").sort();
  return JSON.stringify(observed) === JSON.stringify([...expected].sort());
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
