import { describe, expect, it } from "vitest";

import { buildBrowserE2eLaunchEvidence } from "../../e2e/support/launchCoverageEvidence.ts";

const passedStats = {
  startTime: "2026-07-06T08:00:00.000Z",
  expected: 1,
  unexpected: 0,
  flaky: 0,
  skipped: 0,
};

const serviceOrganizationEvidence = {
  scenarioCodes: ["S1", "S14"],
  organizationLevels: ["HOSPITAL", "DEPARTMENT"],
  serviceCombinations: ["ONBOARDING_INTEGRATION", "COMPLIANCE_OPERATIONS"],
  onboardingEvidence: {
    serviceOperation: "POST /api/v1/admin/tenants",
    serviceStatus: 201,
    tenantId: "t-e2e-org-s1",
    tenantName: "上线演练服务机构S1",
    adminUsername: "org-admin-s1",
    adminUserId: "org-admin-s1",
    temporaryPasswordIssued: true,
    temporaryPasswordDisplayedOnce: true,
  },
  adminBootstrapEvidence: {
    username: "org-admin-s1",
    tenantId: "t-e2e-org-s1",
    loginMustChangePwd: true,
    changePasswordStatus: 204,
    dashboardReached: true,
  },
  orgTreeEvidence: {
    facility: {
      id: "facility-s1",
      tenantId: "t-e2e-org-s1",
      level: "FACILITY",
      name: "上线演练医院S1",
      status: "ACTIVE",
    },
    department: {
      id: "department-s1",
      tenantId: "t-e2e-org-s1",
      parentId: "facility-s1",
      level: "DEPARTMENT",
      name: "上线演练科室S1",
      status: "ACTIVE",
    },
    facilityReadbackVerified: true,
    departmentReadbackVerified: true,
  },
  scenarioConditionEvidence: [
    {
      code: "S1__NORMAL",
      scenarioCode: "S1",
      condition: "NORMAL",
      source: "SERVICE_ORGANIZATION_ONBOARDING_ORG_TREE_READBACK",
      evidence: [
        "前台开通服务机构接口返回 2xx 且一次性临时密码仅记录签发与展示状态",
        "机构管理员首次登录要求改密并完成自助改密进入工作台",
        "医疗机构与科室按同一 tenant 回读为 ACTIVE 且科室父级绑定医疗机构",
      ],
    },
  ],
  scenarioEvidence: [
    {
      code: "S1",
      observedStages: [
        "前台开通服务机构",
        "机构管理员首次登录并改密",
        "前台创建医疗机构与科室",
        "前台回读服务机构组织树",
      ],
    },
    {
      code: "S14",
      observedStages: [
        "前台创建临床账号并绑定科室职责范围",
        "临床账号首次登录后读取权限画像",
        "前台停用演练账号",
      ],
    },
  ],
};

function serviceOrganizationEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/service-organization-frontdesk.spec.ts",
        title: "平台开通服务机构后，新机构可完成组织树、科室账号、职责范围和登录画像闭环",
        status: "passed",
        attachments: [
          {
            name: "service-organization-scenario-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoServiceOrganizationScenarioConditionCoverage(body: Record<string, unknown>) {
  const evidence = serviceOrganizationEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain("S1__NORMAL");
}

const diagnosisKnowledgeEvidence = {
  scenarioCodes: ["S3"],
  productLayers: ["MEDICAL_ASSET"],
  semanticFamilies: ["DISEASE_DIAGNOSIS"],
  specialtyDomains: ["CLINICAL_SPECIALTIES"],
  apiEvidence: {
    standardTermRegisteredFromFrontdesk: {
      operation: "POST /engine/terminology/terms/standard",
      status: 201,
    },
    diagnosisAssetDraftCreatedFromFrontdesk: {
      operation: "POST /engine/knowledge/diagnosis/assets",
      status: 201,
    },
    diagnosisCriterionRegisteredFromFrontdesk: { operation: "POST /criteria", status: 201 },
    validationCaseRegisteredFromFrontdesk: { operation: "POST /test-cases", status: 201 },
  },
  standardTerm: {
    operation: "POST /engine/terminology/terms/standard",
    status: 201,
    system: "TERM.LAB",
    termCode: "TERM.LAB.FRONTDESK.S3",
    displayName: "前台演练发现项S3",
  },
  diagnosisAsset: {
    operation: "POST /engine/knowledge/diagnosis/assets",
    status: 201,
    identityId: 3001,
    identityCode: "DX.FRONTDESK.S3",
    versionId: 9001,
    requestedIdentityCode: "frontdesk-dx-s3",
    evidenceExcerpt: "需登记为结构化诊断标准，并保留验证病例复算证据",
  },
  diagnosisCriterion: {
    operation: "POST /criteria",
    status: 201,
    findingTermCode: "TERM.LAB.FRONTDESK.S3",
  },
  validationCase: {
    operation: "POST /test-cases",
    status: 201,
    caseIdentity: "DXCASE-S3",
    findingTermCode: "TERM.LAB.FRONTDESK.S3",
  },
  scenarioConditionEvidence: [
    {
      code: "S3__NORMAL",
      scenarioCode: "S3",
      condition: "NORMAL",
      source: "DIAGNOSIS_KNOWLEDGE_ASSET_STANDARD_CASE_MAINTENANCE",
      evidence: [
        "前台登记标准发现项术语后创建证据完整诊断资产草稿",
        "诊断标准和验证病例均绑定同一标准发现项术语",
      ],
    },
  ],
  scenarioEvidence: [
    {
      code: "S3",
      observedStages: [
        "前台登记标准发现项术语",
        "前台创建证据完整诊断资产草稿",
        "前台登记诊断标准",
        "前台登记验证病例",
      ],
    },
  ],
};

function diagnosisKnowledgeEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/diagnosis-knowledge-maintenance.spec.ts",
        title: "运营员从前台创建证据完整诊断资产并登记标准与验证病例",
        status: "passed",
        attachments: [
          {
            name: "diagnosis-knowledge-scenario-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoDiagnosisKnowledgeScenarioConditionCoverage(body: Record<string, unknown>) {
  const evidence = diagnosisKnowledgeEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain("S3__NORMAL");
}

const runtimeReleaseVersionedAssets = [
  "KNOWLEDGE",
  "TERMINOLOGY",
  "RULE",
  "PATHWAY",
  "EVALUATION",
  "FOLLOWUP",
  "FIELD_CATALOG",
  "SAFETY",
  "CDSS_RISK",
  "VALUE_SET",
  "FORMULA",
  "ORDER_SET",
  "ACTION_CARD",
];

const versionedAssetRepresentativeRows = runtimeReleaseVersionedAssets.filter(
  (asset) => asset !== "EVALUATION",
);
const fullVersionedAssetRepresentativeRows = runtimeReleaseVersionedAssets;

const versionedAssetRollbackRepresentativeRows = [
  "SAFETY",
  "CDSS_RISK",
  "VALUE_SET",
  "FORMULA",
  "PATHWAY",
  "ORDER_SET",
];
const fullVersionedAssetRollbackRepresentativeRows = [
  ...versionedAssetRollbackRepresentativeRows,
  "EVALUATION",
];
const dedicatedReleaseContractRows = ["TERMINOLOGY", "FIELD_CATALOG", "PATHWAY"];

function rollbackNegativeEvidence(
  removedAssets: Array<{ assetType: string; assetIdentity: string; versionId: string }>,
  consumer: string,
) {
  return {
    rollbackPosted: true,
    currentRuntimeReadbackVerified: true,
    runtimeConsumerReadbackVerified: true,
    consumer,
    consumerProbeMatchedRemovedAssets: false,
    removedAssets,
    currentRuntime: {
      releaseId: `rollback-current-${consumer}`,
      revisionNo: 31,
      manifestSha256: "f".repeat(64),
      assets: [],
    },
    runtimeConsumer: {
      contractVersion: "v1",
      releaseId: `rollback-current-${consumer}`,
      revisionNo: 31,
      manifestSha256: "f".repeat(64),
      assets: [],
    },
  };
}

function withoutRollbackNegativeEvidence(value: Record<string, unknown>) {
  const copy = { ...value };
  delete copy.rollbackNegativeEvidence;
  return copy;
}

const runtimeReleaseApiEvidence = {
  impactSimulationRun: true,
  activationPosted: true,
  activationRequestCarriesRequiredAssets: true,
  currentReleaseReadback: true,
  runtimeConsumerReadback: true,
  rollbackPosted: true,
  rollbackCurrentReleaseReadback: true,
  rollbackRuntimeConsumerReadback: true,
  partialSelectionProved: true,
  offlineDeliveryExported: true,
  offlineDeliveryFileDownloaded: true,
  offlineDeliveryImportPreviewValidated: true,
  offlineDeliveryRuntimeUnchanged: true,
  offlineDeliveryRestoreExecuted: true,
  offlineDeliveryRestoreCreatedNewRevision: true,
  offlineDeliveryRestoreReadbackMatched: true,
  offlineDeliveryRestoreRuntimeConsumerMatched: true,
};

const runtimeReleaseScenarioEvidence = [
  {
    observedStages: [
      "前台展示并勾选 13 类平台标准资产",
      "前台评估机构生效版本发布影响",
      "前台完成平台升级差异与冲突分析",
      "前台只选择本轮部分本院内容进入机构生效版本",
      "前台为第二家医院选择不同本院内容生成机构生效版本",
      "前台生成携带 13 类资产闭包的机构生效版本",
      "后端回读当前机构生效版本资产闭包",
      "第三方运行契约读取同一机构生效版本",
      "两家医院后端与第三方运行契约读回互不串用",
      "前台导出机构生效版本离线交付文件",
      "下载离线交付文件并校验完整快照",
      "离线交付导入预检验签且不改写当前机构生效版本",
      "离线交付恢复执行生成新机构生效版本",
      "恢复后后端和第三方运行契约读取同一机构生效版本",
      "前台从历史机构生效版本回滚",
      "回滚后后端和第三方运行契约读取同一修订",
    ],
  },
];

const runtimeReleasePrimaryCandidate = {
  assetType: "ACTION_CARD",
  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
  versionId: "local-version-1",
};

const runtimeReleaseUnselectedCandidate = {
  assetType: "ACTION_CARD",
  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.2",
  versionId: "local-version-2",
};

const runtimeReleaseSecondaryCandidate = {
  assetType: "ACTION_CARD",
  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.3",
  versionId: "local-version-3",
};

const runtimeReleasePrimaryAsset = {
  ...runtimeReleasePrimaryCandidate,
  entryState: "ACTIVE",
};

const runtimeReleaseSecondaryAsset = {
  ...runtimeReleaseSecondaryCandidate,
  entryState: "ACTIVE",
};

const runtimeReleaseMultiHospitalDifferentiation = {
  primaryHospital: {
    hospitalId: "hospital-A",
    hospitalName: "本地上线演练医院",
    selectedCandidate: runtimeReleasePrimaryCandidate,
    activationReadback: { assets: [runtimeReleasePrimaryAsset] },
    runtimeConsumerReadback: { assets: [runtimeReleasePrimaryAsset] },
    excludesOtherHospitalCandidate: true,
  },
  secondaryHospital: {
    hospitalId: "hospital-B",
    hospitalName: "本地上线演练二院",
    selectedCandidate: runtimeReleaseSecondaryCandidate,
    activationReadback: { assets: [runtimeReleaseSecondaryAsset] },
    runtimeConsumerReadback: { assets: [runtimeReleaseSecondaryAsset] },
    excludesOtherHospitalCandidate: true,
  },
  distinctHospitals: true,
  distinctSelectedCandidates: true,
  backendReadbacksIsolated: true,
  runtimeConsumerReadbacksIsolated: true,
};

const runtimeReleaseOfflineDelivery = {
  delivery: {
    deliveryKind: "CLINICAL_RUNTIME_RELEASE",
    evidenceId: "runtime-offline-runtime-H9-01",
    fileUri: "/api/v1/compliance/evidence/snapshots/runtime-offline-runtime-H9-01/file",
    fileDigest: "sm3:" + "1".repeat(64),
    signatureAlgorithm: "SM3_WITH_SM2",
    runtimeMutation: false,
    releaseId: "runtime-H9",
    hospitalId: "hospital-A",
    itemCount: runtimeReleaseVersionedAssets.length,
  },
  downloadedFile: {
    fileUri: "/api/v1/compliance/evidence/snapshots/runtime-offline-runtime-H9-01/file",
    containsDeliveryKind: true,
    containsRuntimeMutationFalse: true,
    containsReleaseId: true,
  },
  importPreview: {
    status: "VALIDATED",
    signatureValid: true,
    manifestMatched: true,
    runtimeMutation: false,
    releaseId: "runtime-H9",
    hospitalId: "hospital-A",
    itemCount: runtimeReleaseVersionedAssets.length,
  },
  runtimeBefore: {
    releaseId: "runtime-H9",
    revisionNo: 9,
    manifestSha256: "b".repeat(64),
  },
  runtimeAfter: {
    releaseId: "runtime-H9",
    revisionNo: 9,
    manifestSha256: "b".repeat(64),
  },
  runtimeBeforeRestore: {
    releaseId: "runtime-H10-rollback",
    revisionNo: 10,
    manifestSha256: "c".repeat(64),
  },
  restore: {
    status: "RESTORED",
    runtimeMutation: true,
    sourceReleaseId: "runtime-H9",
    targetHospitalId: "hospital-A",
    fileDigest: "sm3:" + "1".repeat(64),
    manifestSha256: "b".repeat(64),
    itemCount: runtimeReleaseVersionedAssets.length,
    restoredReleaseId: "runtime-H11-restore",
    restoredRevisionNo: 11,
    rollbackFromReleaseId: "runtime-H9",
  },
  runtimeAfterRestore: {
    releaseId: "runtime-H11-restore",
    revisionNo: 11,
    manifestSha256: "b".repeat(64),
    selectedCandidatePresent: true,
  },
  runtimeConsumerAfterRestore: {
    releaseId: "runtime-H11-restore",
    revisionNo: 11,
    manifestSha256: "b".repeat(64),
    selectedCandidatePresent: true,
  },
};

const runtimeReleasePlatformUpgradeAnalysis = {
  targetBaseline: {
    baselineReleaseId: "baseline-A9",
    revisionNo: 9,
    manifestSha256: "a".repeat(64),
  },
  currentRuntime: {
    releaseId: "runtime-H8",
    revisionNo: 8,
    platformBaselineReleaseId: "baseline-A8",
    manifestSha256: "b".repeat(64),
  },
  analysisDigest: "c".repeat(64),
  runtimeMutation: false,
  diffSummary: {
    added: 1,
    modified: 1,
    disabled: 0,
    unchanged: 11,
    conflictCount: 0,
  },
  items: [
    {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.UPGRADE.NEW",
      changeType: "ADDED",
      currentVersionId: null,
      targetVersionId: "platform-card-v2",
      conflicts: [],
    },
    {
      assetType: "RULE",
      assetIdentity: "RULE.UPGRADE.MODIFIED",
      changeType: "MODIFIED",
      currentVersionId: "platform-rule-v1",
      targetVersionId: "platform-rule-v2",
      conflicts: [],
    },
    ...Array.from({ length: 11 }, (_, index) => ({
      assetType: "DIAGNOSIS_KNOWLEDGE",
      assetIdentity: `DIAGNOSIS.UPGRADE.UNCHANGED.${index + 1}`,
      changeType: "UNCHANGED",
      currentVersionId: `diagnosis-unchanged-v${index + 1}`,
      targetVersionId: `diagnosis-unchanged-v${index + 1}`,
      conflicts: [],
    })),
  ],
  runtimeBefore: {
    releaseId: "runtime-H8",
    revisionNo: 8,
    manifestSha256: "b".repeat(64),
  },
  runtimeAfter: {
    releaseId: "runtime-H8",
    revisionNo: 8,
    manifestSha256: "b".repeat(64),
  },
};

function runtimeReleaseCompleteEvidence(overrides: Record<string, unknown> = {}) {
  return {
    productLayers: ["RELEASE_GOVERNANCE"],
    versionedAssets: runtimeReleaseVersionedAssets,
    deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
    serviceCombinations: ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"],
    apiEvidence: runtimeReleaseApiEvidence,
    activatedRevisionNo: 9,
    rolledBackRevisionNo: 10,
    localCandidate: runtimeReleasePrimaryCandidate,
    unselectedLocalCandidate: runtimeReleaseUnselectedCandidate,
    activationRequest: { activeAssets: [runtimeReleasePrimaryCandidate] },
    activationReadback: { assets: [runtimeReleasePrimaryAsset] },
    runtimeConsumerReadback: { assets: [runtimeReleasePrimaryAsset] },
    partialSelection: {
      selectedCandidate: runtimeReleasePrimaryCandidate,
      unselectedCandidate: runtimeReleaseUnselectedCandidate,
      activationRequestOmitsUnselected: true,
      activationReadbackOmitsUnselected: true,
      runtimeConsumerOmitsUnselected: true,
    },
    platformUpgradeAnalysis: runtimeReleasePlatformUpgradeAnalysis,
    multiHospitalDifferentiation: runtimeReleaseMultiHospitalDifferentiation,
    offlineDelivery: runtimeReleaseOfflineDelivery,
    rollbackReadback: { localCandidateAbsent: true, assets: [] },
    rollbackRuntimeConsumerReadback: { localCandidateAbsent: true, assets: [] },
    scenarioEvidence: runtimeReleaseScenarioEvidence,
    scenarioConditionEvidence: [
      {
        code: "S13__NORMAL",
        scenarioCode: "S13",
        condition: "NORMAL",
        source: "RUNTIME_RELEASE_ACTIVATION_ROLLBACK_CONTRACT_READBACK",
        evidence: [
          "前台生成机构生效版本并回读当前机构资产闭包",
          "第三方运行契约读取同一机构生效版本且回滚后排除本轮候选",
        ],
      },
    ],
    ...overrides,
  };
}

function runtimeReleaseEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
        title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
        status: "passed",
        attachments: [
          {
            name: "runtime-release-coverage-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function s2s4RuntimeMappingEvidence(overrides: Record<string, unknown> = {}) {
  return {
    scenarioCodes: ["S2", "S4"],
    productLayers: ["DATA_INTEROPERABILITY", "MEDICAL_ASSET"],
    versionedAssets: ["TERMINOLOGY"],
    deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
    serviceCombinations: ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME"],
    apiEvidence: {
      adapterCreatedFromFrontdesk: true,
      fieldMappingConfigured: true,
      webhookCreatedFromFrontdesk: true,
      standardTermRegisteredFromFrontdesk: true,
      localTermRegisteredThroughSignedSync: true,
      candidateGeneratedFromFrontdesk: true,
      candidateConfirmedFromFrontdesk: true,
      terminologyAssetDraftCreatedFromFrontdesk: true,
      runtimeReleaseActivatedWithTerminologyAsset: true,
      invalidMasterDataSignatureRejected: true,
      invalidInboundWebhookSignatureRejected: true,
      inboundWebhookAccepted: true,
      inboundNormalizedByRuntimeRelease: true,
      runtimeContractReadbackMatched: true,
    },
    adapter: {
      adapterId: "lis-s2-s4",
      protocolType: "Webhook",
      sourceSystem: "LIS",
      fieldMappings: [
        { sourcePath: "/patientId", targetPath: "/patient/mpi" },
        {
          sourcePath: "/labCode",
          targetPath: "/observations/0",
          targetDictionaryKey: "LOINC",
          category: "LAB",
        },
      ],
    },
    terminology: {
      assetType: "TERMINOLOGY",
      assetIdentity: "TERM.LAB.S2S4",
      versionId: "term-lab-v1",
      standardSystem: "LOINC",
      standardCode: "718-7",
      localCode: "LIS-HGB",
      sourceSystem: "LIS",
      category: "LAB",
      mappingId: 101,
    },
    runtime: {
      releaseId: "runtime-s2-s4",
      revisionNo: 7,
      manifestSha256: "a".repeat(64),
      assets: [
        {
          assetType: "TERMINOLOGY",
          assetIdentity: "TERM.LAB.S2S4",
          versionId: "term-lab-v1",
          entryState: "ACTIVE",
        },
      ],
    },
    activationRequest: {
      activeAssets: [
        {
          assetType: "TERMINOLOGY",
          assetIdentity: "TERM.LAB.S2S4",
          versionId: "term-lab-v1",
        },
      ],
    },
    inboundResult: {
      status: "SUCCESS",
      mappedFieldCount: 2,
      normalizedCodeCount: 1,
      clinicalEventStatus: "RECEIVED",
      mappedPayload: {
        patient: { mpi: "P-100" },
        observations: [
          {
            standardCode: "718-7",
            codeSystem: "LOINC",
            localCode: "LIS-HGB",
            localCodeSystem: "LIS",
            sourceSystem: "LIS",
            runtimeReleaseId: "runtime-s2-s4",
            mappingId: 101,
            mappedVersion: "H1",
          },
        ],
      },
    },
    runtimeConsumerReadback: {
      releaseId: "runtime-s2-s4",
      revisionNo: 7,
      manifestSha256: "a".repeat(64),
      assets: [
        {
          assetType: "TERMINOLOGY",
          assetIdentity: "TERM.LAB.S2S4",
          versionId: "term-lab-v1",
          entryState: "ACTIVE",
        },
      ],
    },
    dedicatedReleaseContractEvidence: {
      assetType: "TERMINOLOGY",
      assetIdentity: "TERM.LAB.S2S4",
      versionId: "term-lab-v1",
      productionRoute: "STANDARD_AND_LOCAL_TERMINOLOGY_MAPPING",
      releaseContract: "S2_S4_TERMINOLOGY_MAPPING_RUNTIME_CONTRACT",
      producerVerified: true,
      reviewerVerified: true,
      activationVerified: true,
      runtimeConsumerReadbackVerified: true,
      inboundNormalizationVerified: true,
      sourceSystems: ["LIS"],
      consumer: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
    },
    scenarioEvidence: [
      {
        code: "S2",
        observedStages: [
          "平台管理员前台创建 LIS Webhook 适配器并配置字段映射",
          "平台管理员前台创建回调通道并完成签名预览",
          "真实 Webhook 入站通过验签并生成标准临床事件",
          "入站字段映射按当前机构生效版本完成术语归一",
        ],
      },
      {
        code: "S4",
        observedStages: [
          "前台登记标准术语",
          "签名主数据同步登记院内术语",
          "前台生成并确认术语映射候选",
          "前台生成不可变术语资产版本",
          "当前机构生效版本和第三方运行契约读回同一术语资产",
        ],
      },
    ],
    scenarioConditionEvidence: [
      {
        code: "S2__NORMAL",
        scenarioCode: "S2",
        condition: "NORMAL",
        source: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
        evidence: [
          "平台管理员前台创建 LIS Webhook 适配器并配置字段映射",
          "真实 Webhook 入站通过验签并生成标准临床事件",
          "入站字段映射按当前机构生效版本完成术语归一",
        ],
      },
      {
        code: "S2__ABNORMAL",
        scenarioCode: "S2",
        condition: "ABNORMAL",
        source: "INVALID_INBOUND_WEBHOOK_SIGNATURE_REJECTED",
        evidence: ["非法入站 Webhook 签名被拒绝"],
      },
      {
        code: "S4__NORMAL",
        scenarioCode: "S4",
        condition: "NORMAL",
        source: "TERMINOLOGY_RUNTIME_CONTRACT",
        evidence: [
          "前台登记标准术语",
          "签名主数据同步登记院内术语",
          "前台生成并确认术语映射候选",
          "当前机构生效版本和第三方运行契约读回同一术语资产",
        ],
      },
      {
        code: "S4__ABNORMAL",
        scenarioCode: "S4",
        condition: "ABNORMAL",
        source: "INVALID_MASTER_DATA_SIGNATURE_REJECTED",
        evidence: ["非法主数据同步签名被拒绝"],
      },
    ],
    ...overrides,
  };
}

function s2s4EvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
        title: "平台管理员完成系统接入且运营员完成术语映射后真实入站消息按当前机构生效版本归一",
        status: "passed",
        attachments: [
          {
            name: "s2-s4-runtime-mapping-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoS2S4RuntimeMappingCoverage(body: Record<string, unknown>) {
  const evidence = s2s4EvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["S2", "S4"]),
  );
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toContain(
    "TERMINOLOGY",
  );
}

const cdssRuntimeDeclarativeAssets = {
  scenarioCodes: ["S5"],
  productLayers: ["CLINICAL_EXECUTION"],
  versionedAssets: ["VALUE_SET", "FORMULA", "ACTION_CARD"],
  serviceCombinations: ["CLINICAL_RUNTIME"],
  scenarioConditionEvidence: [
    {
      code: "S5__NORMAL",
      scenarioCode: "S5",
      condition: "NORMAL",
      source: "CDSS_DECLARATIVE_RUNTIME_ASSET_CONSUMPTION",
      evidence: [
        "前台创建 VALUE_SET/FORMULA/ACTION_CARD 并纳入当前机构生效版本",
        "临床用户从真实前台触发 CDSS 推荐且解释回读三类声明式资产物化证据",
      ],
    },
  ],
  apiEvidence: {
    valueSetCreatedFromFrontdesk: true,
    formulaCreatedFromFrontdesk: true,
    actionCardCreatedFromFrontdesk: true,
    declarativeRuntimeActivatedBeforeRuleTestCases: true,
    ruleTestSnapshotBoundToDeclarativeRuntime: true,
    ruleCreatedWithRuntimeAssetReferences: true,
    ruleRuntimeCandidateResolvedFromCurrentHospital: true,
    runtimeReleaseActivatedWithDeclarativeAssets: true,
    activeSnapshotBoundToRuntimeRelease: true,
    cdssEvaluationTriggeredFromFrontdesk: true,
    recommendationPersisted: true,
    ruleExplanationContainsRuntimeMaterialization: true,
  },
  runtime: {
    releaseId: "runtime-cdss-assets",
    revisionNo: 12,
    manifestSha256: "c".repeat(64),
    assets: [
      {
        assetType: "VALUE_SET",
        assetIdentity: "VALUE_SET.CDSS.RUNTIME",
        versionId: "vs-v1",
        versionNo: "V1",
        contentHash: "1".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "FORMULA",
        assetIdentity: "FORMULA.CDSS.RUNTIME",
        versionId: "formula-v1",
        versionNo: "V1",
        contentHash: "2".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.CDSS.RUNTIME",
        versionId: "card-v1",
        versionNo: "V1",
        contentHash: "3".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    ruleAsset: {
      assetType: "RULE",
      assetIdentity: "RULE.CDSS.RUNTIME",
      versionId: "av-rule-v1",
      versionNo: "V1",
      contentHash: "4".repeat(64),
      entryState: "ACTIVE",
    },
  },
  createdAssets: [
    {
      assetType: "VALUE_SET",
      assetIdentity: "VALUE_SET.CDSS.RUNTIME",
      versionId: "vs-v1",
      versionNo: "V1",
      contentHash: "1".repeat(64),
    },
    {
      assetType: "FORMULA",
      assetIdentity: "FORMULA.CDSS.RUNTIME",
      versionId: "formula-v1",
      versionNo: "V1",
      contentHash: "2".repeat(64),
    },
    {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.CDSS.RUNTIME",
      versionId: "card-v1",
      versionNo: "V1",
      contentHash: "3".repeat(64),
    },
  ],
  rule: {
    assetType: "RULE",
    assetIdentity: "RULE.CDSS.RUNTIME",
    ruleId: "rule-cdss-runtime",
    ruleVersionId: "rv-rule-v1",
  },
  ruleRuntimeCandidate: {
    assetType: "RULE",
    assetIdentity: "RULE.CDSS.RUNTIME",
    versionId: "av-rule-v1",
    versionNo: "V1",
    contentHash: "4".repeat(64),
    status: "PUBLISHED",
    sourceLayer: "HOSPITAL",
  },
  declarativeRuntime: {
    releaseId: "runtime-cdss-declarative-assets",
    revisionNo: 11,
    manifestSha256: "b".repeat(64),
    assets: [
      {
        assetType: "VALUE_SET",
        assetIdentity: "VALUE_SET.CDSS.RUNTIME",
        versionId: "vs-v1",
        versionNo: "V1",
        contentHash: "1".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "FORMULA",
        assetIdentity: "FORMULA.CDSS.RUNTIME",
        versionId: "formula-v1",
        versionNo: "V1",
        contentHash: "2".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.CDSS.RUNTIME",
        versionId: "card-v1",
        versionNo: "V1",
        contentHash: "3".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    activationRequest: {
      activeAssets: [
        {
          assetType: "VALUE_SET",
          assetIdentity: "VALUE_SET.CDSS.RUNTIME",
          versionId: "vs-v1",
        },
        {
          assetType: "FORMULA",
          assetIdentity: "FORMULA.CDSS.RUNTIME",
          versionId: "formula-v1",
        },
        {
          assetType: "ACTION_CARD",
          assetIdentity: "ACTION_CARD.CDSS.RUNTIME",
          versionId: "card-v1",
        },
      ],
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "VALUE_SET",
        assetIdentity: "VALUE_SET.CDSS.RUNTIME",
        versionId: "vs-v1",
      },
      {
        assetType: "FORMULA",
        assetIdentity: "FORMULA.CDSS.RUNTIME",
        versionId: "formula-v1",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.CDSS.RUNTIME",
        versionId: "card-v1",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.CDSS.RUNTIME",
        versionId: "av-rule-v1",
      },
    ],
  },
  clinicalTrigger: {
    triggerId: "trigger-cdss-runtime",
    contextSnapshotId: "ctx-cdss-runtime",
    runtimeReleaseId: "runtime-cdss-assets",
    cardId: "card-cdss-runtime",
    relatedCardIds: ["card-cdss-runtime", "card-other-runtime"],
  },
  recommendation: {
    cardId: "card-cdss-runtime",
    contextSnapshotId: "ctx-cdss-runtime",
    triggerRuntimeReleaseId: "runtime-cdss-assets",
    explanation: {
      runtimeRelease: {
        runtimeReleaseId: "runtime-cdss-assets",
        assetVersionId: "av-rule-v1",
        assetVersionNo: "V1",
        contentHash: "4".repeat(64),
      },
      ruleExplanation: {
        conditionEvidence: [
          {
            fact: "medications[].code",
            operator: "in",
            expected: ["J01GB03"],
            actual: "J01GB03",
            matched: true,
          },
          {
            fact: "patient.bodyMassIndex",
            operator: "gte",
            expected: 30,
            actual: 32,
            matched: true,
            formula: "BMI: WeightKg / (HeightM^2)",
          },
        ],
        runtimeAssetEvidence: [
          {
            assetType: "VALUE_SET",
            assetIdentity: "VALUE_SET.CDSS.RUNTIME",
            assetVersion: "V1",
            contentHash: "1".repeat(64),
            expandedCount: 1,
          },
          {
            assetType: "FORMULA",
            assetIdentity: "FORMULA.CDSS.RUNTIME",
            assetVersion: "V1",
            contentHash: "2".repeat(64),
            runtimeFunction: "BMI",
          },
          {
            assetType: "ACTION_CARD",
            actionCardRef: "ACTION_CARD.CDSS.RUNTIME",
            assetIdentity: "ACTION_CARD.CDSS.RUNTIME",
            resolvedActionCardVersion: "V1",
            resolvedActionCardHash: "3".repeat(64),
            assetVersion: "V1",
            contentHash: "3".repeat(64),
            requiresPhysicianConfirmation: true,
          },
        ],
      },
    },
  },
  rollbackNegativeEvidence: rollbackNegativeEvidence(
    [
      {
        assetType: "VALUE_SET",
        assetIdentity: "VALUE_SET.CDSS.RUNTIME",
        versionId: "vs-v1",
      },
      {
        assetType: "FORMULA",
        assetIdentity: "FORMULA.CDSS.RUNTIME",
        versionId: "formula-v1",
      },
    ],
    "CDSS_DECLARATIVE_ASSET_EVALUATION",
  ),
  scenarioEvidence: [
    {
      code: "S5",
      observedStages: [
        "前台创建 VALUE_SET 值集资产草稿",
        "前台创建 FORMULA 公式资产草稿",
        "前台创建 ACTION_CARD 临床提示卡资产草稿",
        "临床规则引用三类运行资产",
        "当前机构生效版本包含三类本轮运行资产",
        "临床用户从真实前台触发 CDSS 推荐评估",
        "推荐卡解释证明三类资产按当前机构生效版本物化消费",
      ],
    },
  ],
};

function cdssRuntimeDeclarativeEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/cdss-runtime-declarative-assets.spec.ts",
        title: "临床用户从真实前台触发 CDSS 推荐并消费当前机构生效版本声明式运行资产",
        status: "passed",
        attachments: [
          {
            name: "cdss-runtime-declarative-assets-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoCdssRuntimeDeclarativeCoverage(body: Record<string, unknown>) {
  const evidence = cdssRuntimeDeclarativeEvidenceResult(body);
  const assets = evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? [];
  expect(assets).not.toEqual(expect.arrayContaining(["VALUE_SET", "FORMULA", "ACTION_CARD"]));
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toContain("S5");
}

function expectNoCdssRuntimeDeclarativeScenarioConditionCoverage(body: Record<string, unknown>) {
  const evidence = cdssRuntimeDeclarativeEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain("S5__NORMAL");
}

const medicationSafetyFrontdeskEvidence = {
  scenarioCodes: ["S5"],
  productLayers: ["CLINICAL_EXECUTION"],
  versionedAssets: ["SAFETY", "CDSS_RISK", "RULE"],
  serviceCombinations: ["CLINICAL_RUNTIME"],
  scopeStatement: "用药安全代表切片：药物过敏红线，不代表完整药事治理或第三方审方系统闭环。",
  apiEvidence: {
    riskMatrixCreatedFromRealService: true,
    safetyRedlineDraftCreated: true,
    safetyRedlineDryRunSubmitted: true,
    safetyAssetPromoted: true,
    terminologyCoverageGateActivated: true,
    ruleCreatedForMedicationPrescribe: true,
    ruleRuntimeCandidateResolvedFromCurrentHospital: true,
    runtimeActivatedWithSafetyRiskAndRule: true,
    contextSnapshotCreatedFromFrontdesk: true,
    clinicalEvaluationTriggeredFromFrontdesk: true,
    pharmacistReviewRecordedWithoutClosingPhysicianConfirmation: true,
    physicianConfirmationRecorded: true,
  },
  riskMatrix: {
    assetType: "CDSS_RISK",
    assetIdentity: "CDSS.RISK.MATRIX",
    versionId: "av-risk-p0",
    versionNo: "V1",
    contentHash: "6".repeat(64),
    matrixId: "risk-matrix-med-safety",
    matrixVersion: "med-safety-v1",
    triggerPoint: "medication-prescribe",
    severityLevel: "CRITICAL",
    automationLevel: "INFORM_ONLY",
    riskLevel: "CRITICAL",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    silentRunHours: 168,
    releaseGate: "OPT04_REDLINE_SILENT_TRIAL",
    autoExecutionAllowed: false,
  },
  safetyRedline: {
    assetType: "SAFETY",
    assetIdentity: "SAFETY.RDL-MED-ALLERGY-P0",
    versionId: "av-safety-p0",
    versionNo: "V1",
    contentHash: "5".repeat(64),
    redlineId: "redline-med-allergy-p0",
    redlineKey: "RDL-MED-ALLERGY-P0",
    redlineVersion: "2026.1",
    conditionDsl:
      '{"all":[{"fact":"allergyIntolerances[].code","operator":"contains","value":"J01C"}]}',
    trialId: "crt-p0",
    hazardSeverity: "CRITICAL",
    riskMatrixId: "risk-matrix-med-safety",
    riskMatrixVersion: "med-safety-v1",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    releaseGate: "OPT04_REDLINE_SILENT_TRIAL",
    lowerTenantOverrideAllowed: false,
  },
  ruleAsset: {
    assetType: "RULE",
    assetIdentity: "RULE.MEDICATION.SAFETY.P0",
    versionId: "av-rule-med-p0",
    versionNo: "V1",
    contentHash: "7".repeat(64),
    ruleId: "rule-med-safety-p0",
    ruleVersionId: "rv-med-safety-p0",
  },
  terminologyGate: {
    assetType: "TERMINOLOGY",
    assetIdentity: "TERM.DRUG.MEDICATION.SAFETY.P0",
    versionId: "av-term-med-p0",
    versionNo: "V1",
    contentHash: "8".repeat(64),
    standardSystem: "ATC",
    standardCode: "J01C",
    localCode: "J01C",
    sourceSystem: "MEDKERNEL_FRONTDESK",
    category: "DRUG",
    mappingId: 18,
  },
  runtime: {
    releaseId: "runtime-med-safety",
    revisionNo: 18,
    manifestSha256: "d".repeat(64),
    assets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.DRUG.MEDICATION.SAFETY.P0",
        versionId: "av-term-med-p0",
        versionNo: "V1",
        contentHash: "8".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-MED-ALLERGY-P0",
        versionId: "av-safety-p0",
        versionNo: "V1",
        contentHash: "5".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "CDSS_RISK",
        assetIdentity: "CDSS.RISK.MATRIX",
        versionId: "av-risk-p0",
        versionNo: "V1",
        contentHash: "6".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.MEDICATION.SAFETY.P0",
        versionId: "av-rule-med-p0",
        versionNo: "V1",
        contentHash: "7".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    safetyAsset: {
      assetType: "SAFETY",
      assetIdentity: "SAFETY.RDL-MED-ALLERGY-P0",
      versionId: "av-safety-p0",
      versionNo: "V1",
      contentHash: "5".repeat(64),
      entryState: "ACTIVE",
    },
    cdssRiskAsset: {
      assetType: "CDSS_RISK",
      assetIdentity: "CDSS.RISK.MATRIX",
      versionId: "av-risk-p0",
      versionNo: "V1",
      contentHash: "6".repeat(64),
      entryState: "ACTIVE",
    },
    ruleAsset: {
      assetType: "RULE",
      assetIdentity: "RULE.MEDICATION.SAFETY.P0",
      versionId: "av-rule-med-p0",
      versionNo: "V1",
      contentHash: "7".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-MED-ALLERGY-P0",
        versionId: "av-safety-p0",
      },
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.DRUG.MEDICATION.SAFETY.P0",
        versionId: "av-term-med-p0",
      },
      {
        assetType: "CDSS_RISK",
        assetIdentity: "CDSS.RISK.MATRIX",
        versionId: "av-risk-p0",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.MEDICATION.SAFETY.P0",
        versionId: "av-rule-med-p0",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-med-safety",
    contextSnapshotId: "ctx-med-safety",
    runtimeReleaseId: "runtime-med-safety",
    encounterId: "enc-med-safety",
    resources: {
      medications: [{ code: "J01C", displayName: "青霉素类" }],
      allergyIntolerances: [
        {
          code: "J01C",
          substance: "青霉素类",
          category: "medication",
          verificationStatus: "CONFIRMED",
        },
      ],
    },
  },
  clinicalTrigger: {
    triggerId: "trigger-med-safety",
    contextSnapshotId: "ctx-med-safety",
    runtimeReleaseId: "runtime-med-safety",
    cardId: "card-med-safety",
    relatedCardIds: ["card-med-safety", "card-rule-med-safety", "card-other"],
  },
  recommendation: {
    cardId: "card-med-safety",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-med-safety",
    explanation: {
      matchType: "CLINICAL_REDLINE",
      redlineId: "redline-med-allergy-p0",
      redlineKey: "RDL-MED-ALLERGY-P0",
      riskMatrixId: "risk-matrix-med-safety",
      riskMatrixVersion: "med-safety-v1",
      redlineExplanation: {
        conditionEvidence: [
          {
            fact: "allergyIntolerances[].code",
            operator: "contains",
            expected: "J01C",
            actual: ["J01C"],
            matched: true,
          },
        ],
      },
    },
    riskMatrixExplanation: "高危或高危害触发点的打断式 CDSS 输出必须医师确认",
  },
  ruleRecommendation: {
    cardId: "card-rule-med-safety",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-med-safety",
    explanation: {
      matchType: "RULE",
      ruleId: "rule-med-safety-p0",
      ruleCode: "RULE.MEDICATION.SAFETY.P0",
      ruleVersionId: "rv-med-safety-p0",
      runtimeRelease: {
        runtimeReleaseId: "runtime-med-safety",
        assetVersionId: "av-rule-med-p0",
        assetVersionNo: "V1",
        contentHash: "7".repeat(64),
      },
      ruleExplanation: {
        title: "P0 用药安全代表切片规则",
        reason:
          "Medication 与 AllergyIntolerance 均来自当前临床上下文，规则由当前机构生效版本锁定。",
        conditionEvidence: [
          {
            fact: "medications[].code",
            operator: "contains",
            expected: "J01C",
            actual: ["J01C"],
            matched: true,
          },
          {
            fact: "allergyIntolerances[].code",
            operator: "contains",
            expected: "J01C",
            actual: ["J01C"],
            matched: true,
          },
        ],
      },
    },
  },
  feedback: {
    pharmacist: {
      feedbackId: "rf-pharmacist",
      cardStatus: "PENDING",
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: {
        feedbackId: "rf-pharmacist",
        feedbackType: "VIEW_SOURCE",
        operatorRole: "PHARMACIST",
        reasonCode: "PHARMACIST_REVIEWED",
      },
    },
    physician: {
      feedbackId: "rf-physician",
      cardStatus: "ACCEPTED",
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: {
        feedbackId: "rf-physician",
        feedbackType: "ACCEPT",
        operatorRole: "DOCTOR",
        reasonCode: "CONFIRMED",
      },
    },
    noAutoOrder: true,
    actionCardEvidence: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
      versionId: "av-action-pharmacy-review",
      versionNo: "V1",
      contentHash: "9".repeat(64),
      entryState: "ACTIVE",
      requiresPhysicianConfirmation: true,
      noAutoOrder: true,
    },
  },
  rollbackNegativeEvidence: rollbackNegativeEvidence(
    [
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-MED-ALLERGY-P0",
        versionId: "av-safety-p0",
      },
      { assetType: "CDSS_RISK", assetIdentity: "CDSS.RISK.MATRIX", versionId: "av-risk-p0" },
    ],
    "MEDICATION_SAFETY_RULE",
  ),
  scenarioEvidence: [
    {
      code: "S5",
      observedStages: [
        "运营员创建真实 CDSS_RISK 风险矩阵",
        "运营员创建药物过敏禁忌 SAFETY 红线草稿",
        "运营员提交静默试运行并上线 SAFETY 资产",
        "运营员补齐 ATC:J01C 术语映射并激活到当前机构生效版本",
        "运营员创建 medication-prescribe 规则资产",
        "当前机构生效版本包含 SAFETY、CDSS_RISK 与 RULE",
        "临床用户从患者 360 建立 Medication 与 AllergyIntolerance 上下文",
        "临床用户从真实前台开立用药触发推荐评估",
        "药师登记红线复核且不关闭医生确认链路",
        "医生逐条确认采纳，系统不自动开嘱",
      ],
    },
  ],
  scenarioConditionEvidence: [
    {
      code: "S5__HIGH_RISK",
      scenarioCode: "S5",
      condition: "HIGH_RISK",
      source: "MEDICATION_SAFETY_CRITICAL_REDLINE_PHYSICIAN_CONFIRMATION",
      evidence: [
        "CDSS_RISK 风险矩阵与 SAFETY 红线均为 CRITICAL",
        "药物过敏 AllergyIntolerance 已确认且命中红线条件",
        "推荐卡保持 PENDING，医生人工确认后 ACCEPTED",
        "系统未自动开嘱",
      ],
    },
  ],
};

function medicationSafetyEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/medication-safety-frontdesk.spec.ts",
        title: "临床用户与运营员围绕药物过敏红线完成当前机构生效版本推荐与人工确认闭环",
        status: "passed",
        attachments: [
          {
            name: "medication-safety-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoMedicationSafetyCoverage(body: Record<string, unknown>) {
  const evidence = medicationSafetyEvidenceResult(body);
  const assets = evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? [];
  expect(assets).not.toEqual(expect.arrayContaining(["SAFETY", "CDSS_RISK", "RULE"]));
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toContain("S5");
}

const diagnosticCriticalValueEvidence = {
  scenarioCodes: ["S36"],
  productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
  versionedAssets: ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"],
  deliveryShapes: ["API_EVENT"],
  serviceCombinations: ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME"],
  scopeStatement:
    "医技危急值代表切片：LIS/FHIR 入站 Observation 与 DiagnosticReport 后完成人工报告解读闭环，不代表完整 LIS/PACS/RIS/病理/心电全链路或完整危急值制度。",
  thirdPartySystemFamilyConsumerSlice: {
    systemFamilyCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    familyName: "PACS/RIS、超声、病理、内镜、心电",
    sourceSystems: ["PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG", "FHIR_R4"],
    canonicalResources: ["Observation", "DiagnosticReport"],
    consumer: "REPORT_INTERPRETATION",
    consumerVerified: true,
    standardResourceVerified: true,
    degradationVerified: true,
    auditVerified: true,
    noAutoOrder: true,
    noReportRewrite: true,
    scopeStatement:
      "PACS/RIS、超声、病理、内镜、心电系统族代表消费者切片：已验证医技报告标准资源入站、报告解读消费者、人工复核待办和断连诚实降级；不代表完整 PACS/RIS/病理/内镜/心电系统族覆盖、完整第三方系统族覆盖或完整上线验收。",
  },
  apiEvidence: {
    fhirObservationAccepted: true,
    fhirDiagnosticReportAccepted: true,
    contextSnapshotContainsInboundResources: true,
    currentRuntimeContainsDiagnosticAssets: true,
    reportInterpretationTriggeredFromFrontdesk: true,
    criticalRecommendationPersisted: true,
    workflowTodoCompletedByHuman: true,
  },
  inboundObservation: {
    fhirResourceType: "Observation",
    fhirId: "obs-critical-k",
    canonicalResourceType: "OBSERVATION",
    snapshotId: "ctx-critical-report",
    runtimeReleaseId: "runtime-critical-report",
    patientId: "mpi-critical-report",
    sourceSystem: "FHIR_R4",
    code: "LAB.POTASSIUM",
    displayName: "血钾",
    valueNumeric: 6.3,
    unit: "mmol/L",
    criticalFlag: "CRITICAL",
    integrationStatus: "NOT_CONNECTED",
    operationOutcomeContainsNotConnected: true,
    compensationStatus: "NOT_CONNECTED",
    compensationRequired: true,
    compensationMessageId: "fhir-observation-obs-critical-k",
  },
  inboundDiagnosticReport: {
    fhirResourceType: "DiagnosticReport",
    fhirId: "dr-critical-k",
    canonicalResourceType: "DIAGNOSTIC_REPORT",
    snapshotId: "ctx-critical-report",
    runtimeReleaseId: "runtime-critical-report",
    patientId: "mpi-critical-report",
    sourceSystem: "FHIR_R4",
    reportType: "血钾检验",
    conclusion: "血钾 6.3 mmol/L，危急值，已复核并签发。",
    signedStatus: "FINAL",
    integrationStatus: "RETRYING",
    operationOutcomeContainsNotConnected: true,
    compensationStatus: "NOT_CONNECTED",
    compensationRequired: true,
    compensationMessageId: "fhir-diagnosticreport-dr-critical-k",
  },
  runtime: {
    releaseId: "runtime-critical-report",
    platformBaselineReleaseId: "baseline-critical-report",
    revisionNo: 16,
    manifestSha256: "9".repeat(64),
    assets: [
      {
        assetType: "KNOWLEDGE",
        assetIdentity: "plat:diagnostic_item:lab-potassium",
        sourceLayer: "PLATFORM",
        versionId: "kv-critical-report",
        versionNo: "V1",
        contentHash: "1".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        sourceLayer: "PLATFORM",
        versionId: "fc-critical-report",
        versionNo: "V1",
        contentHash: "2".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
        sourceLayer: "HOSPITAL",
        versionId: "ac-critical-report",
        versionNo: "V1",
        contentHash: "3".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    knowledgeAsset: {
      assetType: "KNOWLEDGE",
      assetIdentity: "plat:diagnostic_item:lab-potassium",
      sourceLayer: "PLATFORM",
      versionId: "kv-critical-report",
      versionNo: "V1",
      contentHash: "1".repeat(64),
      entryState: "ACTIVE",
    },
    fieldCatalogAsset: {
      assetType: "FIELD_CATALOG",
      assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
      sourceLayer: "PLATFORM",
      versionId: "fc-critical-report",
      versionNo: "V1",
      contentHash: "2".repeat(64),
      entryState: "ACTIVE",
    },
    actionCardAsset: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
      sourceLayer: "HOSPITAL",
      versionId: "ac-critical-report",
      versionNo: "V1",
      contentHash: "3".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    platformBaselineReleaseId: "baseline-critical-report",
    activeAssets: [
      {
        assetType: "KNOWLEDGE",
        assetIdentity: "plat:diagnostic_item:lab-potassium",
        versionId: null,
      },
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        versionId: null,
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
        versionId: "ac-critical-report",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-critical-report",
    contextSnapshotId: "ctx-critical-report",
    runtimeReleaseId: "runtime-critical-report",
    resources: {
      observations: [
        {
          observationId: "obs-critical-k",
          code: "LAB.POTASSIUM",
          valueNumeric: 6.3,
          unit: "mmol/L",
          criticalFlag: "CRITICAL",
          sourceSystem: "FHIR_R4",
        },
      ],
      diagnosticReports: [
        {
          reportId: "dr-critical-k",
          reportType: "血钾检验",
          conclusion: "血钾 6.3 mmol/L，危急值，已复核并签发。",
          sourceSystem: "FHIR_R4",
        },
      ],
    },
  },
  interpretation: {
    contextSnapshotId: "ctx-critical-report",
    runtimeReleaseId: "runtime-critical-report",
    advisoryNote: "报告解读仅用于辅助阅读，不改写已签发报告，不替代医师判断。",
    interpretations: [
      {
        reportId: "dr-critical-k",
        itemCode: "plat:diagnostic_item:lab-potassium",
        sourceVersionId: 21,
        versionNo: "V1",
        criticalRisk: true,
        abnormalHighlights: ["血钾升高", "危急值"],
        recommendations: [
          "请按本机构危急值闭环完成人工确认、回报和记录，系统不自动修改报告。",
          "医师结合症状、体征、既往趋势和医技项目说明书判断后续处理，系统不自动开立医嘱。",
        ],
      },
    ],
  },
  recommendation: {
    cardId: "card-critical-report",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-critical-report",
    cardType: "LAB",
    requiresPhysicianConfirmation: true,
    aiGenerated: false,
    explanation: {
      reportId: "dr-critical-k",
      runtimeReleaseId: "runtime-critical-report",
      itemCode: "plat:diagnostic_item:lab-potassium",
      sourceVersionId: 21,
      sourceContentHash: "1".repeat(64),
      criticalRisk: true,
      recommendations: ["请按本机构危急值闭环完成人工确认、回报和记录，系统不自动修改报告。"],
      runtimeAssetEvidence: [
        {
          assetType: "FIELD_CATALOG",
          assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
          assetVersion: "V1",
          contentHash: "2".repeat(64),
          fields: ["observations[].criticalFlag", "diagnosticReports[].conclusion"],
        },
        {
          assetType: "ACTION_CARD",
          assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
          assetVersion: "V1",
          contentHash: "3".repeat(64),
          requiresPhysicianConfirmation: true,
        },
      ],
    },
  },
  dedicatedReleaseContractEvidence: {
    assetType: "FIELD_CATALOG",
    assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
    versionId: "fc-critical-report",
    productionRoute: "DIAGNOSTIC_FIELD_CATALOG_RUNTIME_BASELINE",
    releaseContract: "DIAGNOSTIC_REPORT_INTERPRETATION_FIELD_CONTRACT",
    platformBaselineVerified: true,
    activationVerified: true,
    runtimeConsumerReadbackVerified: true,
    reportInterpretationVerified: true,
    fieldEvidencePaths: [
      "recommendation.explanation.runtimeAssetEvidence[0].fields",
      "clinicalContext.resources.observations[0].criticalFlag",
      "clinicalContext.resources.diagnosticReports[0].conclusion",
    ],
    consumer: "REPORT_INTERPRETATION",
  },
  workflowTodo: {
    todoId: "todo-critical-report",
    status: "COMPLETED",
    category: "REPORT_INTERPRETATION",
    sourceId: "card-critical-report",
    completedBy: "medical-technician",
    completionReason: "医技已复核危急值报告解读提示，确认仅作辅助，不改写已签发报告。",
    noAutoOrder: true,
  },
  scenarioEvidence: [
    {
      code: "S36",
      observedStages: [
        "外部 FHIR/LIS 入站 Observation 危急值并落标准资源",
        "外部 FHIR/LIS 入站已签发 DiagnosticReport 并落标准资源",
        "当前上下文回读 Observation 与 DiagnosticReport 均绑定同一机构生效版本",
        "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
        "临床用户从真实前台生成医技报告解读",
        "报告解读推荐卡证明危急风险、字段目录和提示卡按当前机构生效版本消费",
        "医技或医生人工完成报告解读待办，系统不改写报告且不自动开嘱",
      ],
    },
  ],
  scenarioConditionEvidence: [
    {
      code: "S36__HIGH_RISK",
      scenarioCode: "S36",
      condition: "HIGH_RISK",
      source: "DIAGNOSTIC_CRITICAL_VALUE_HUMAN_CLOSURE",
      evidence: [
        "FHIR/LIS 入站 Observation 标记危急值",
        "报告解读解释 criticalRisk=true 且推荐卡要求医师确认",
        "医技或医生人工完成报告解读待办",
        "系统不改写报告且不自动开嘱",
      ],
    },
    {
      code: "S36__DEGRADATION",
      scenarioCode: "S36",
      condition: "DEGRADATION",
      source: "FHIR_LIS_NOT_CONNECTED_COMPENSATION",
      evidence: [
        "外部 Observation 入站补偿收敛到 NOT_CONNECTED",
        "外部 DiagnosticReport 入站补偿收敛到 NOT_CONNECTED",
        "断连状态下本地标准资源与报告解读主链路仍可人工闭环",
      ],
    },
  ],
};

const diagnosticReportFamilyMatrixRows = [
  {
    reportFamilyCode: "PACS_RIS",
    reportFamilyName: "PACS/RIS 影像报告",
    fhirId: "dr-pacs-chest-ct",
    reportType: "胸部 CT 影像报告",
    fhirCode: "IMG.CT.CHEST",
    sourceSystem: "FHIR_R4",
    standardResourceVerified: true,
    consumerVerified: true,
    workflowTodoCompleted: true,
    degradationVerified: true,
    noReportRewrite: true,
    noAutoOrder: true,
    reportInterpretationId: "dr-pacs-chest-ct",
    workflowTodoId: "todo-critical-report",
  },
  {
    reportFamilyCode: "ULTRASOUND",
    reportFamilyName: "超声报告",
    fhirId: "dr-ultrasound-abdomen",
    reportType: "腹部超声报告",
    fhirCode: "US.ABDOMEN",
    sourceSystem: "FHIR_R4",
    standardResourceVerified: true,
    consumerVerified: true,
    workflowTodoCompleted: true,
    degradationVerified: true,
    noReportRewrite: true,
    noAutoOrder: true,
    reportInterpretationId: "dr-ultrasound-abdomen",
    workflowTodoId: "todo-critical-report",
  },
  {
    reportFamilyCode: "PATHOLOGY",
    reportFamilyName: "病理报告",
    fhirId: "dr-pathology-biopsy",
    reportType: "胃镜活检病理报告",
    fhirCode: "PATH.BIOPSY",
    sourceSystem: "FHIR_R4",
    standardResourceVerified: true,
    consumerVerified: true,
    workflowTodoCompleted: true,
    degradationVerified: true,
    noReportRewrite: true,
    noAutoOrder: true,
    reportInterpretationId: "dr-pathology-biopsy",
    workflowTodoId: "todo-critical-report",
  },
  {
    reportFamilyCode: "ENDOSCOPY",
    reportFamilyName: "内镜报告",
    fhirId: "dr-endoscopy-gastroscopy",
    reportType: "胃镜检查报告",
    fhirCode: "ENDO.GASTROSCOPY",
    sourceSystem: "FHIR_R4",
    standardResourceVerified: true,
    consumerVerified: true,
    workflowTodoCompleted: true,
    degradationVerified: true,
    noReportRewrite: true,
    noAutoOrder: true,
    reportInterpretationId: "dr-endoscopy-gastroscopy",
    workflowTodoId: "todo-critical-report",
  },
  {
    reportFamilyCode: "ECG",
    reportFamilyName: "心电报告",
    fhirId: "dr-ecg-resting",
    reportType: "十二导联心电图报告",
    fhirCode: "ECG.12LEAD",
    sourceSystem: "FHIR_R4",
    standardResourceVerified: true,
    consumerVerified: true,
    workflowTodoCompleted: true,
    degradationVerified: true,
    noReportRewrite: true,
    noAutoOrder: true,
    reportInterpretationId: "dr-ecg-resting",
    workflowTodoId: "todo-critical-report",
  },
];

const diagnosticReportFamilyMatrixEvidence = {
  ...diagnosticCriticalValueEvidence,
  diagnosticReportFamilyConsumerMatrix: {
    systemFamilyCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
    matrixName: "PACS/RIS、超声、病理、内镜、心电五类医技报告族真实消费者矩阵",
    canonicalResources: ["DiagnosticReport"],
    consumer: "REPORT_INTERPRETATION",
    runtimeKnowledgeScope: "当前机构生效版本报告解读说明书代表，不代表五类专属说明书全量发布。",
    consumerVerified: true,
    standardResourceVerified: true,
    degradationVerified: true,
    auditVerified: true,
    noAutoOrder: true,
    noReportRewrite: true,
    scopeStatement:
      "PACS/RIS、超声、病理、内镜、心电五类医技报告族真实消费者矩阵代表切片：已验证五类 DiagnosticReport 标准资源入站、报告解读消费者、人工复核待办和断连诚实降级；不代表完整 PACS/RIS/病理/内镜/心电系统族覆盖，不代表完整第三方系统族覆盖，不代表完整上线验收。",
    rows: diagnosticReportFamilyMatrixRows,
  },
  clinicalContext: {
    ...diagnosticCriticalValueEvidence.clinicalContext,
    resources: {
      ...diagnosticCriticalValueEvidence.clinicalContext.resources,
      diagnosticReports: [
        ...diagnosticCriticalValueEvidence.clinicalContext.resources.diagnosticReports,
        ...diagnosticReportFamilyMatrixRows.map((row) => ({
          reportId: row.fhirId,
          reportType: row.reportType,
          conclusion: `${row.reportFamilyName} 已签发，需结合当前患者上下文人工复核。`,
          sourceSystem: "FHIR_R4",
        })),
      ],
    },
  },
  interpretation: {
    ...diagnosticCriticalValueEvidence.interpretation,
    interpretations: [
      ...diagnosticCriticalValueEvidence.interpretation.interpretations,
      ...diagnosticReportFamilyMatrixRows.map((row) => ({
        reportId: row.fhirId,
        reportType: row.reportType,
        itemCode: "plat:diagnostic_item:lab-potassium",
        sourceVersionId: 21,
        versionNo: "V1",
        criticalRisk: false,
        abnormalHighlights: [`${row.reportFamilyName} 人工复核重点`],
        recommendations: ["请结合患者上下文人工复核报告，系统不自动开立医嘱。"],
      })),
    ],
  },
};

function diagnosticCriticalValueEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/diagnostic-critical-value-frontdesk.spec.ts",
        title: "临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环",
        status: "passed",
        attachments: [
          {
            name: "diagnostic-critical-value-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoDiagnosticCriticalValueCoverage(body: Record<string, unknown>) {
  const evidence = diagnosticCriticalValueEvidenceResult(body);
  const assets = evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? [];
  expect(assets).not.toEqual(expect.arrayContaining(["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"]));
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toContain("S36");
}

function expectNoDiagnosticFamilyConsumerSliceCoverage(body: Record<string, unknown>) {
  const evidence = diagnosticCriticalValueEvidenceResult(body);
  expect(
    evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code) ?? [],
  ).not.toContain("PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG");
}

function expectNoDiagnosticReportFamilyMatrixCoverage(body: Record<string, unknown>) {
  const evidence = diagnosticCriticalValueEvidenceResult(body);
  expect(
    evidence.launchCoverage.diagnosticReportFamilyConsumerMatrix?.map((item) => item.code) ?? [],
  ).not.toContain("PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG");
}

const regionalDiagnosticMutualRecognitionEvidence = {
  scenarioCodes: ["S40"],
  productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
  versionedAssets: ["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"],
  deliveryShapes: ["API_EVENT"],
  serviceCombinations: ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME", "PROFESSIONAL_COLLABORATION"],
  scopeStatement:
    "区域医技报告互认代表切片：REGIONAL_REMOTE 区域来源可信分级、跨机构 DiagnosticReport 入站、报告解读、人工互认和协同待办闭环，不代表完整区域平台、完整远程医疗、完整 PACS/RIS/病理/内镜/心电系统族覆盖、完整 S40、完整 S0-S40 或完整上线验收。",
  apiEvidence: {
    regionalRemoteOnboardingCreated: true,
    regionalSourceRegisteredAndReadBack: true,
    fhirDiagnosticReportAccepted: true,
    contextSnapshotContainsRegionalReport: true,
    currentRuntimeContainsMutualRecognitionAssets: true,
    reportInterpretationTriggeredFromFrontdesk: true,
    mutualRecognitionRecommendationPersisted: true,
    workflowTodoCompletedByHuman: true,
  },
  fhirOnboarding: {
    onboardingId: "onboarding-s40-regional-fhir",
    routeType: "FHIR",
    routeReference: "/api/v1/engine/integration/fhir/R4",
    systemFamilyCode: "REGIONAL_REMOTE",
    sourceSystem: "REGIONAL_FHIR",
    businessScenario: "S40 区域共享",
    status: "REQUESTED",
    healthStatus: "NOT_CONNECTED",
    fhirVersion: "R4",
    mappedFieldCount: 0,
  },
  regionalSource: {
    sourceId: "regional-source-s40",
    regionalNetworkName: "长三角影像互认平台",
    sourceOrganizationId: "ORG-REMOTE-IMG-001",
    sourceOrganizationName: "远程示范医院影像中心",
    trustLevel: "HIGH",
    evidenceText: "OPT-07 可信分级：CA 签章、报告号、来源机构和互认目录均已核验。",
    adapterId: null,
    onboardingId: "onboarding-s40-regional-fhir",
    orgPath: "/tenant/demo/hospital/h001",
    status: "ACTIVE",
  },
  inboundDiagnosticReport: {
    fhirResourceType: "DiagnosticReport",
    fhirId: "dr-regional-chest-ct",
    canonicalResourceType: "DIAGNOSTIC_REPORT",
    snapshotId: "ctx-regional-report",
    runtimeReleaseId: "runtime-regional-report",
    patientId: "mpi-regional-report",
    sourceSystem: "FHIR_R4",
    sourceRecordId: "DiagnosticReport/dr-regional-chest-ct",
    reportType: "胸部 CT",
    conclusion: "外院胸部 CT 已签发：右肺上叶结节，建议结合病史复核，可作为互认报告参考。",
    signedStatus: "FINAL",
    signedAt: "2026-07-08T09:30:00Z",
    sourceOrganizationId: "ORG-REMOTE-IMG-001",
    sourceOrganizationName: "远程示范医院影像中心",
    regionalSourceId: "regional-source-s40",
    mutualRecognitionReason: "同级医院同项目 7 日内已签发，影像质量满足互认目录要求。",
    duplicateExamHint: "提示 7 日内已有胸部 CT 报告，需人工判断是否互认，系统不自动取消检查。",
    integrationStatus: "RETRYING",
    operationOutcomeContainsNotConnected: true,
    compensationStatus: "NOT_CONNECTED",
    compensationRequired: true,
    compensationMessageId: "fhir-diagnosticreport-dr-regional-chest-ct",
  },
  runtime: {
    releaseId: "runtime-regional-report",
    platformBaselineReleaseId: "baseline-regional-report",
    revisionNo: 19,
    manifestSha256: "6".repeat(64),
    assets: [
      {
        assetType: "KNOWLEDGE",
        assetIdentity: "IMG.CT.CHEST.REGIONAL.S40",
        sourceLayer: "HOSPITAL",
        versionId: "av-regional-report-knowledge",
        versionNo: "V1",
        contentHash: "a".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        sourceLayer: "PLATFORM",
        versionId: "fc-regional-report",
        versionNo: "V1",
        contentHash: "b".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
        sourceLayer: "HOSPITAL",
        versionId: "ac-regional-report",
        versionNo: "V1",
        contentHash: "c".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    knowledgeAsset: {
      assetType: "KNOWLEDGE",
      assetIdentity: "IMG.CT.CHEST.REGIONAL.S40",
      sourceLayer: "HOSPITAL",
      versionId: "av-regional-report-knowledge",
      versionNo: "V1",
      contentHash: "a".repeat(64),
      entryState: "ACTIVE",
    },
    fieldCatalogAsset: {
      assetType: "FIELD_CATALOG",
      assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
      sourceLayer: "PLATFORM",
      versionId: "fc-regional-report",
      versionNo: "V1",
      contentHash: "b".repeat(64),
      entryState: "ACTIVE",
    },
    actionCardAsset: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
      sourceLayer: "HOSPITAL",
      versionId: "ac-regional-report",
      versionNo: "V1",
      contentHash: "c".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    platformBaselineReleaseId: "baseline-regional-report",
    activeAssets: [
      {
        assetType: "KNOWLEDGE",
        assetIdentity: "IMG.CT.CHEST.REGIONAL.S40",
        versionId: "av-regional-report-knowledge",
      },
      {
        assetType: "FIELD_CATALOG",
        assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
        versionId: null,
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
        versionId: "ac-regional-report",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-regional-report",
    contextSnapshotId: "ctx-regional-report",
    runtimeReleaseId: "runtime-regional-report",
    resources: {
      diagnosticReports: [
        {
          reportId: "dr-regional-chest-ct",
          reportType: "胸部 CT",
          conclusion: "外院胸部 CT 已签发：右肺上叶结节，建议结合病史复核，可作为互认报告参考。",
          sourceSystem: "FHIR_R4",
        },
      ],
    },
  },
  interpretation: {
    contextSnapshotId: "ctx-regional-report",
    runtimeReleaseId: "runtime-regional-report",
    advisoryNote: "报告解读仅用于辅助阅读，不改写已签发报告，不替代医师判断。",
    interpretations: [
      {
        reportId: "dr-regional-chest-ct",
        itemCode: "IMG.CT.CHEST.REGIONAL.S40",
        sourceVersionId: 21,
        versionNo: "V1",
        criticalRisk: false,
        abnormalHighlights: ["右肺上叶结节"],
        recommendations: ["请结合患者上下文复核异常重点，系统不自动开立医嘱。"],
      },
    ],
  },
  recommendation: {
    cardId: "card-regional-report",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-regional-report",
    cardType: "EXAM",
    requiresPhysicianConfirmation: true,
    aiGenerated: false,
    mutualRecognitionReason: "同级医院同项目 7 日内已签发，影像质量满足互认目录要求。",
    duplicateExamHint: "提示 7 日内已有胸部 CT 报告，需人工判断是否互认，系统不自动取消检查。",
    explanation: {
      reportId: "dr-regional-chest-ct",
      runtimeReleaseId: "runtime-regional-report",
      itemCode: "IMG.CT.CHEST.REGIONAL.S40",
      sourceVersionId: 21,
      sourceContentHash: "a".repeat(64),
      criticalRisk: false,
      recommendations: [
        "区域来源报告仅作为互认参考，医师需核对来源、影像质量和患者上下文后人工确认。",
        "提示可能存在重复检查，系统不自动取消检查、不自动开立医嘱。",
      ],
      runtimeAssetEvidence: [
        {
          assetType: "FIELD_CATALOG",
          assetIdentity: "FIELD.CATALOG.CLINICAL_CONTEXT",
          assetVersion: "V1",
          contentHash: "b".repeat(64),
          fields: ["diagnosticReports[].conclusion", "diagnosticReports[].signedAt"],
        },
        {
          assetType: "ACTION_CARD",
          assetIdentity: "ACTION_CARD.REPORT.CRITICAL_VALUE",
          assetVersion: "V1",
          contentHash: "c".repeat(64),
          requiresPhysicianConfirmation: true,
        },
      ],
    },
  },
  workflowTodo: {
    todoId: "todo-regional-report",
    status: "COMPLETED",
    category: "REPORT_INTERPRETATION",
    sourceId: "card-regional-report",
    completedBy: "clinical-user",
    completionReason:
      "已人工核对区域来源、报告签发状态和互认理由，仅采纳为参考；不改写报告，不自动开嘱。",
    noAutoOrder: true,
    noAutoRecognition: true,
  },
  scenarioConditionEvidence: [
    {
      code: "S40__DEGRADATION",
      scenarioCode: "S40",
      condition: "DEGRADATION",
      source: "REGIONAL_DIAGNOSTIC_MUTUAL_RECOGNITION_NOT_CONNECTED_COMPENSATION",
      evidence: [
        "REGIONAL_REMOTE FHIR 接入保持 NOT_CONNECTED 诚实状态",
        "跨机构 DiagnosticReport 入站收敛到 RETRYING 与 NOT_CONNECTED 补偿",
        "医生人工完成互认待办且系统不自动互认、不自动开嘱",
      ],
    },
  ],
  scenarioEvidence: [
    {
      code: "S40",
      observedStages: [
        "平台管理员登记 REGIONAL_REMOTE FHIR 接入申请并保持断连诚实状态",
        "平台管理员登记区域来源可信分级并回读跨机构证据",
        "外部区域 FHIR 入站已签发 DiagnosticReport 并落标准资源",
        "当前上下文回读跨机构 DiagnosticReport 并绑定同一机构生效版本",
        "当前机构生效版本包含 DIAGNOSTIC_ITEM 知识说明书、FIELD_CATALOG 与 ACTION_CARD",
        "临床用户从真实前台生成区域报告互认解读",
        "推荐卡证明互认理由、重复检查提示、字段目录和提示卡按当前机构生效版本消费",
        "医生人工完成互认协同待办，系统不自动互认、不改写报告且不自动开嘱",
      ],
    },
  ],
};

function regionalDiagnosticMutualRecognitionEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/regional-diagnostic-mutual-recognition-frontdesk.spec.ts",
        title: "临床用户与平台管理员完成区域医技报告互认代表闭环",
        status: "passed",
        attachments: [
          {
            name: "regional-diagnostic-mutual-recognition-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoRegionalDiagnosticMutualRecognitionCoverage(body: Record<string, unknown>) {
  const evidence = regionalDiagnosticMutualRecognitionEvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toContain("S40");
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["KNOWLEDGE", "FIELD_CATALOG", "ACTION_CARD"]),
  );
}

function expectNoRegionalRemoteConsumerSliceCoverage(body: Record<string, unknown>) {
  const evidence = regionalDiagnosticMutualRecognitionEvidenceResult(body);
  expect(
    evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code) ?? [],
  ).not.toContain("REGIONAL_REMOTE");
}

function expectNoRegionalDiagnosticMutualRecognitionScenarioConditionCoverage(
  body: Record<string, unknown>,
) {
  const evidence = regionalDiagnosticMutualRecognitionEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain("S40__DEGRADATION");
}

const nursingContinuityEvidence = {
  scenarioCodes: ["S20", "S35"],
  productLayers: ["CLINICAL_EXECUTION"],
  versionedAssets: ["FOLLOWUP"],
  serviceCombinations: ["CLINICAL_RUNTIME"],
  scopeStatement:
    "护理连续照护代表切片：护理高风险评估、护理计划、随访问卷、异常回院和结果回流闭环，不代表完整护理专业智能、完整护理计划执行或第三方护理系统族。",
  apiEvidence: {
    contextSnapshotCreatedFromFrontdesk: true,
    nursingAssessmentReadback: true,
    carePlanReadback: true,
    followupTemplatePublished: true,
    runtimeActivatedWithFollowupAsset: true,
    followupPlanGeneratedFromFrontdesk: true,
    questionnaireSubmitted: true,
    abnormalReported: true,
    resultBackflowPosted: true,
    backflowContextContainsFollowUp: true,
  },
  runtime: {
    releaseId: "runtime-nursing-continuity",
    revisionNo: 20,
    manifestSha256: "4".repeat(64),
    assets: [
      {
        assetType: "FOLLOWUP",
        assetIdentity: "FOLLOWUP.NURSING.CONTINUITY",
        versionId: "av-followup-nursing",
        versionNo: "V1",
        contentHash: "5".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    followupAsset: {
      assetType: "FOLLOWUP",
      assetIdentity: "FOLLOWUP.NURSING.CONTINUITY",
      versionId: "av-followup-nursing",
      versionNo: "V1",
      contentHash: "5".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "FOLLOWUP",
        assetIdentity: "FOLLOWUP.NURSING.CONTINUITY",
        versionId: "av-followup-nursing",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-nursing-continuity",
    encounterId: "enc-nursing-continuity",
    contextSnapshotId: "ctx-nursing-continuity",
    runtimeReleaseId: "runtime-nursing-continuity",
    resources: {
      nursingAssessments: [
        {
          assessmentId: "na-nursing-continuity",
          assessmentType: "跌倒风险评估",
          riskLevel: "HIGH",
          status: "CONFIRMED",
          sourceSystem: "MEDKERNEL_FRONTDESK",
          mappedVersion: "FRONTDESK_CONTEXT_V1",
          qualityStatus: "VALID",
        },
      ],
      carePlans: [
        {
          planId: "care-nursing-continuity",
          pathwayId: "PATHWAY.NURSING.FALL",
          currentNodeId: "NURSING_REHAB_EDUCATION",
          varianceCode: null,
          plannedFinishAt: "2026-07-20T08:00:00.000Z",
          sourceSystem: "MEDKERNEL_FRONTDESK",
          mappedVersion: "FRONTDESK_CONTEXT_V1",
          qualityStatus: "VALID",
        },
      ],
    },
  },
  followupPlan: {
    planId: "fp-nursing-continuity",
    patientId: "mpi-nursing-continuity",
    encounterId: "enc-nursing-continuity",
    runtimeReleaseId: "runtime-nursing-continuity",
    templateId: "tpl-nursing-continuity",
    templateVersion: 1,
    templateCode: "FOLLOWUP.NURSING.CONTINUITY",
    modelStatus: "MODEL_DISABLED",
    sourceFactType: "DIAGNOSIS",
    sourceFactId: "NURSING_CONTINUITY",
    generationRuleCode: "FOLLOWUP_TEMPLATE_FOLLOWUP.NURSING.CONTINUITY_V1",
    generationExplanation: {
      runtimeReleaseId: "runtime-nursing-continuity",
      sourceFactType: "DIAGNOSIS",
      sourceFactId: "NURSING_CONTINUITY",
      modelStatus: "MODEL_DISABLED",
      nursingAssessmentEvidence: [
        {
          assessmentId: "na-nursing-continuity",
          assessmentType: "跌倒风险评估",
          riskLevel: "HIGH",
          status: "CONFIRMED",
        },
      ],
      carePlanEvidence: [
        {
          planId: "care-nursing-continuity",
          pathwayId: "PATHWAY.NURSING.FALL",
          currentNodeId: "NURSING_REHAB_EDUCATION",
          plannedFinishAt: "2026-07-20T08:00:00.000Z",
        },
      ],
      runtimeAssetEvidence: [
        {
          assetType: "FOLLOWUP",
          assetIdentity: "FOLLOWUP.NURSING.CONTINUITY",
          assetVersionId: "av-followup-nursing",
          assetVersionNo: "V1",
          contentHash: "5".repeat(64),
        },
      ],
      generatedTaskTypes: ["QUESTIONNAIRE", "RETURN_VISIT"],
    },
    tasks: [
      {
        taskId: "ft-nursing-questionnaire",
        taskType: "QUESTIONNAIRE",
        status: "COMPLETED",
        questionnaireTemplateId: "Q-NURSING-FALL",
      },
      {
        taskId: "ft-nursing-return",
        taskType: "RETURN_VISIT",
        status: "ABNORMAL_RETURN",
      },
    ],
  },
  questionnaire: {
    questionnaireId: "fq-nursing-continuity",
    taskId: "ft-nursing-questionnaire",
    questionnaireTemplateId: "Q-NURSING-FALL",
    status: "COMPLETED",
  },
  abnormalReport: {
    eventId: "fe-nursing-abnormal",
    returnTaskId: "ft-nursing-return",
    notificationEventId: "fe-nursing-notice",
  },
  resultBackflow: {
    eventId: "fe-nursing-result",
    contextSnapshotId: "ctx-nursing-backflow",
    sourceQuestionnaireId: "fq-nursing-continuity",
    abnormalFlag: "Y",
  },
  backflowContext: {
    contextSnapshotId: "ctx-nursing-backflow",
    runtimeReleaseId: "runtime-nursing-continuity",
    resources: {
      followUps: [
        {
          followUpId: "fq-nursing-continuity",
          planType: "NURSING_CONTINUITY",
          plannedAt: "2026-07-20T08:00:00.000Z",
          questionnaireId: "Q-NURSING-FALL",
          abnormalFlag: "Y",
          sourceSystem: "FOLLOWUP",
          sourceRecordId: "fq-nursing-continuity",
          mappedVersion: "FOLLOWUP_RESULT",
          qualityStatus: "VALID",
        },
      ],
    },
  },
  scenarioConditionEvidence: [
    {
      code: "S20__NORMAL",
      scenarioCode: "S20",
      condition: "NORMAL",
      source: "NURSING_CONTINUITY_FOLLOWUP_PLAN_RESULT_BACKFLOW",
      evidence: [
        "FOLLOWUP 资产已激活到当前机构生效版本",
        "临床用户从真实前台基于护理上下文生成随访计划并完成问卷",
        "随访结果回流生成 FollowUp 标准资源并绑定同一 runtime",
      ],
    },
    {
      code: "S35__ABNORMAL",
      scenarioCode: "S35",
      condition: "ABNORMAL",
      source: "NURSING_HIGH_RISK_ASSESSMENT_ABNORMAL_RETURN",
      evidence: [
        "标准上下文回读 NursingAssessment 高风险评估与 CarePlan 护理计划",
        "随访计划解释消费护理评估风险等级和护理计划节点",
        "异常回院事件、回院任务和通知事件均已登记",
      ],
    },
  ],
  scenarioEvidence: [
    {
      code: "S35",
      observedStages: [
        "临床用户从患者 360 建立护理高风险评估标准上下文",
        "标准上下文回读 NursingAssessment 与 CarePlan 护理事实",
        "随访计划解释消费 NursingAssessment 风险等级与护理计划节点",
      ],
    },
    {
      code: "S20",
      observedStages: [
        "运营员发布 FOLLOWUP 随访方案并激活到当前机构生效版本",
        "临床用户从真实前台基于护理上下文生成随访计划",
        "临床用户提交随访问卷并登记异常回院",
        "随访结果回流生成 FollowUp 标准资源并绑定同一机构生效版本",
      ],
    },
  ],
};

function nursingContinuityEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/nursing-continuity-frontdesk.spec.ts",
        title: "临床用户围绕护理高风险评估完成随访计划、异常回院与结果回流闭环",
        status: "passed",
        attachments: [
          {
            name: "nursing-continuity-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoNursingContinuityCoverage(body: Record<string, unknown>) {
  const evidence = nursingContinuityEvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["S20", "S35"]),
  );
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toContain(
    "FOLLOWUP",
  );
}

function expectNoNursingContinuityScenarioConditionCoverage(
  body: Record<string, unknown>,
  code: "S20__NORMAL" | "S35__ABNORMAL",
) {
  const evidence = nursingContinuityEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain(code);
}

const pharmacyReviewAntimicrobialEvidence = {
  scenarioCodes: ["S18", "S31"],
  productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
  versionedAssets: ["TERMINOLOGY", "SAFETY", "CDSS_RISK", "RULE", "ACTION_CARD"],
  deliveryShapes: ["API_EVENT"],
  serviceCombinations: [
    "THIRD_PARTY_INTERFACE",
    "CLINICAL_RUNTIME",
    "PROFESSIONAL_COLLABORATION",
    "QUALITY_IMPROVEMENT",
  ],
  scopeStatement:
    "药房审方与抗菌药物治理代表切片：PHARMACY_REVIEW 双向审方、抗菌药物风险推荐、药师/医生人工确认和 S31 整改闭环，不代表完整药事治理、完整抗菌药物分级管理或第三方药房审方系统族完整覆盖。",
  apiEvidence: {
    pharmacyReviewAdapterCreatedThroughRealService: true,
    pharmacyReviewWebhookCreatedThroughRealService: true,
    webhookSignaturePreviewGenerated: true,
    antimicrobialTerminologyActivated: true,
    antimicrobialRiskMatrixCreated: true,
    antimicrobialSafetyAssetPromoted: true,
    antimicrobialActionCardPublished: true,
    antimicrobialRuleCreated: true,
    runtimeActivatedWithAntimicrobialAssets: true,
    contextSnapshotCreatedFromFrontdesk: true,
    outboundReviewRequested: true,
    inboundReviewAccepted: true,
    clinicalEvaluationTriggeredFromFrontdesk: true,
    pharmacistReviewRecordedWithoutClosingPhysicianConfirmation: true,
    physicianConfirmationRecorded: true,
    qualityRectificationSubmittedAndReviewed: true,
  },
  adapter: {
    adapterId: "adapter-pharmacy-review",
    systemFamilyCode: "PHARMACY_REVIEW",
    sourceSystem: "PHARMACY_REVIEW",
    targetSystem: "PHARMACY_REVIEW",
    protocolType: "Webhook",
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      { sourcePath: "/medicationCode", targetPath: "/medications/0", targetDictionaryKey: "ATC" },
      { sourcePath: "/infectionCode", targetPath: "/conditions/0", targetDictionaryKey: "ICD-10" },
      { sourcePath: "/observationCode", targetPath: "/observations/0/code" },
      { sourcePath: "/pct", targetPath: "/observations/0/valueNumeric" },
      { sourcePath: "/pharmacyReview/reviewResult", targetPath: "/pharmacyReview/reviewResult" },
      {
        sourcePath: "/pharmacyReview/pharmacistOpinion",
        targetPath: "/pharmacyReview/pharmacistOpinion",
      },
    ],
  },
  webhookSignature: {
    webhookId: "webhook-pharmacy-review",
    adapterId: "adapter-pharmacy-review",
    signatureAlgorithm: "HMAC-SHA256",
    canonicalPayloadIncludesTraceId: true,
    previewGenerated: true,
  },
  terminologyGate: {
    assetType: "TERMINOLOGY",
    assetIdentity: "TERM.DRUG.PHARMACY_REVIEW.ANTIMICROBIAL",
    versionId: "av-term-pharmacy-review",
    versionNo: "V1",
    contentHash: "8".repeat(64),
    standardSystem: "ATC",
    standardCode: "J01C",
    localCode: "J01C",
    sourceSystem: "MEDKERNEL_FRONTDESK",
    category: "DRUG",
    mappingId: 31,
    pharmacyReview: {
      standardSystem: "ATC",
      standardCode: "J01C",
      localCode: "J01C",
      sourceSystem: "PHARMACY_REVIEW",
      category: "DRUG",
      mappingId: 33,
    },
    diagnosis: {
      standardSystem: "ICD-10",
      standardCode: "J18.900",
      localCode: "J18.900",
      sourceSystem: "MEDKERNEL_FRONTDESK",
      category: "DIAGNOSIS",
      mappingId: 32,
    },
    pharmacyReviewDiagnosis: {
      standardSystem: "ICD-10",
      standardCode: "J18.900",
      localCode: "J18.900",
      sourceSystem: "PHARMACY_REVIEW",
      category: "DIAGNOSIS",
      mappingId: 34,
    },
  },
  riskMatrix: {
    assetType: "CDSS_RISK",
    assetIdentity: "CDSS.RISK.MATRIX",
    versionId: "av-risk-pharmacy-review",
    versionNo: "V1",
    contentHash: "6".repeat(64),
    matrixId: "risk-matrix-pharmacy-review",
    matrixVersion: "pharmacy-review-v1",
    triggerPoint: "medication-prescribe",
    severityLevel: "CRITICAL",
    automationLevel: "INFORM_ONLY",
    riskLevel: "CRITICAL",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    silentRunHours: 168,
    releaseGate: "OPT04_ANTIMICROBIAL_RESTRICTION",
    autoExecutionAllowed: false,
  },
  safetyRedline: {
    assetType: "SAFETY",
    assetIdentity: "SAFETY.RDL-ANTIMICROBIAL-RESTRICTION",
    versionId: "av-safety-pharmacy-review",
    versionNo: "V1",
    contentHash: "5".repeat(64),
    redlineId: "redline-antimicrobial-restriction",
    redlineKey: "RDL-ANTIMICROBIAL-RESTRICTION",
    redlineVersion: "2026.1",
    category: "ANTIMICROBIAL_RESTRICTION",
    conditionDsl:
      '{"all":[{"fact":"medications[].code","operator":"contains","value":"J01C"},{"fact":"observations[].valueNumeric","operator":"gte","value":2}]}',
    trialId: "crt-antimicrobial",
    hazardSeverity: "CRITICAL",
    riskMatrixId: "risk-matrix-pharmacy-review",
    riskMatrixVersion: "pharmacy-review-v1",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    releaseGate: "OPT04_ANTIMICROBIAL_RESTRICTION",
    lowerTenantOverrideAllowed: false,
  },
  actionCard: {
    assetType: "ACTION_CARD",
    assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
    versionId: "av-action-pharmacy-review",
    versionNo: "V1",
    contentHash: "9".repeat(64),
    entryState: "ACTIVE",
    requiresPhysicianConfirmation: true,
    noAutoOrder: true,
  },
  ruleAsset: {
    assetType: "RULE",
    assetIdentity: "RULE.MEDICATION.PHARMACY_REVIEW.ANTIMICROBIAL",
    versionId: "av-rule-pharmacy-review",
    versionNo: "V1",
    contentHash: "7".repeat(64),
    ruleId: "rule-pharmacy-review-antimicrobial",
    ruleVersionId: "rv-pharmacy-review-antimicrobial",
  },
  runtime: {
    releaseId: "runtime-pharmacy-review",
    revisionNo: 31,
    manifestSha256: "d".repeat(64),
    assets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.DRUG.PHARMACY_REVIEW.ANTIMICROBIAL",
        versionId: "av-term-pharmacy-review",
        versionNo: "V1",
        contentHash: "8".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-ANTIMICROBIAL-RESTRICTION",
        versionId: "av-safety-pharmacy-review",
        versionNo: "V1",
        contentHash: "5".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "CDSS_RISK",
        assetIdentity: "CDSS.RISK.MATRIX",
        versionId: "av-risk-pharmacy-review",
        versionNo: "V1",
        contentHash: "6".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.MEDICATION.PHARMACY_REVIEW.ANTIMICROBIAL",
        versionId: "av-rule-pharmacy-review",
        versionNo: "V1",
        contentHash: "7".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
        versionId: "av-action-pharmacy-review",
        versionNo: "V1",
        contentHash: "9".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    terminologyAsset: {
      assetType: "TERMINOLOGY",
      assetIdentity: "TERM.DRUG.PHARMACY_REVIEW.ANTIMICROBIAL",
      versionId: "av-term-pharmacy-review",
      versionNo: "V1",
      contentHash: "8".repeat(64),
      entryState: "ACTIVE",
    },
    safetyAsset: {
      assetType: "SAFETY",
      assetIdentity: "SAFETY.RDL-ANTIMICROBIAL-RESTRICTION",
      versionId: "av-safety-pharmacy-review",
      versionNo: "V1",
      contentHash: "5".repeat(64),
      entryState: "ACTIVE",
    },
    cdssRiskAsset: {
      assetType: "CDSS_RISK",
      assetIdentity: "CDSS.RISK.MATRIX",
      versionId: "av-risk-pharmacy-review",
      versionNo: "V1",
      contentHash: "6".repeat(64),
      entryState: "ACTIVE",
    },
    ruleAsset: {
      assetType: "RULE",
      assetIdentity: "RULE.MEDICATION.PHARMACY_REVIEW.ANTIMICROBIAL",
      versionId: "av-rule-pharmacy-review",
      versionNo: "V1",
      contentHash: "7".repeat(64),
      entryState: "ACTIVE",
    },
    actionCardAsset: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
      versionId: "av-action-pharmacy-review",
      versionNo: "V1",
      contentHash: "9".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.DRUG.PHARMACY_REVIEW.ANTIMICROBIAL",
        versionId: "av-term-pharmacy-review",
      },
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-ANTIMICROBIAL-RESTRICTION",
        versionId: "av-safety-pharmacy-review",
      },
      {
        assetType: "CDSS_RISK",
        assetIdentity: "CDSS.RISK.MATRIX",
        versionId: "av-risk-pharmacy-review",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.MEDICATION.PHARMACY_REVIEW.ANTIMICROBIAL",
        versionId: "av-rule-pharmacy-review",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
        versionId: "av-action-pharmacy-review",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-pharmacy-review",
    encounterId: "enc-pharmacy-review",
    contextSnapshotId: "ctx-pharmacy-review",
    runtimeReleaseId: "runtime-pharmacy-review",
    resources: {
      medications: [{ code: "J01C", displayName: "青霉素类抗菌药" }],
      allergyIntolerances: [
        {
          code: "J01C",
          substance: "青霉素类",
          category: "medication",
          verificationStatus: "CONFIRMED",
        },
      ],
      conditions: [{ code: "J18.900", codeSystem: "ICD-10", displayName: "肺部感染" }],
      observations: [
        {
          observationId: "obs-pct",
          code: "PCT",
          valueNumeric: 2.4,
          unit: "ng/mL",
          sourceSystem: "MEDKERNEL_FRONTDESK",
        },
      ],
    },
  },
  outboundReview: {
    messageId: "out-pharmacy-review",
    traceId: "trace-pharmacy-review",
    adapterId: "adapter-pharmacy-review",
    targetSystem: "PHARMACY_REVIEW",
    protocolType: "Webhook",
    status: "RETRYING",
    compensationStatus: "NOT_CONNECTED",
    compensationMessageId: "out-pharmacy-review",
    blocksMainFlow: false,
    initialCompensationRequired: false,
    compensationRequired: true,
    payload: {
      patientId: "mpi-pharmacy-review",
      contextSnapshotId: "ctx-pharmacy-review",
      medicationCode: "J01C",
      infectionCode: "J18.900",
      observationCode: "PCT",
      pct: 2.4,
    },
  },
  inboundReview: {
    messageId: "in-pharmacy-review",
    traceId: "trace-pharmacy-review",
    adapterId: "adapter-pharmacy-review",
    webhookId: "webhook-pharmacy-review",
    patientId: "mpi-pharmacy-review",
    encounterId: "enc-pharmacy-review",
    contextSnapshotId: "ctx-pharmacy-review",
    sourceSystem: "PHARMACY_REVIEW",
    status: "SUCCESS",
    clinicalEventStatus: "RECEIVED",
    clinicalEvent: {
      eventId: "evt-wh-pharmacy-review",
      status: "PROCESSED",
      errorCode: null,
      errorClass: null,
      retryCount: 0,
      runtimeReleaseId: "runtime-pharmacy-review",
    },
    mappedFieldCount: 7,
    mappedPayload: {
      pharmacyReview: {
        reviewResult: "REQUIRES_PHYSICIAN_CONFIRMATION",
        pharmacistOpinion: "抗菌药物使用需结合感染指标与病原学复核。",
      },
      medications: [{ standardCode: "J01C", runtimeReleaseId: "runtime-pharmacy-review" }],
      conditions: [
        {
          standardCode: "J18.900",
          codeSystem: "ICD-10",
          sourceSystem: "PHARMACY_REVIEW",
          runtimeReleaseId: "runtime-pharmacy-review",
        },
      ],
      observations: [{ code: "PCT", valueNumeric: 2.4 }],
    },
    signedPayload: {
      patientId: "mpi-pharmacy-review",
      contextSnapshotId: "ctx-pharmacy-review",
      medicationCode: "J01C",
      infectionCode: "J18.900",
      observationCode: "PCT",
      pct: 2.4,
    },
  },
  clinicalTrigger: {
    triggerId: "trigger-pharmacy-review",
    contextSnapshotId: "ctx-pharmacy-review",
    runtimeReleaseId: "runtime-pharmacy-review",
    cardId: "card-pharmacy-review",
    relatedCardIds: ["card-pharmacy-review", "card-rule-pharmacy-review"],
  },
  recommendation: {
    cardId: "card-pharmacy-review",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-pharmacy-review",
    cardType: "MEDICATION",
    requiresPhysicianConfirmation: true,
    aiGenerated: false,
    explanation: {
      matchType: "CLINICAL_REDLINE",
      redlineId: "redline-antimicrobial-restriction",
      redlineKey: "RDL-ANTIMICROBIAL-RESTRICTION",
      riskMatrixId: "risk-matrix-pharmacy-review",
      riskMatrixVersion: "pharmacy-review-v1",
      redlineExplanation: {
        conditionEvidence: [
          {
            fact: "medications[].code",
            operator: "contains",
            expected: "J01C",
            actual: ["J01C"],
            matched: true,
          },
          {
            fact: "observations[].valueNumeric",
            operator: "gte",
            expected: 2,
            actual: 2.4,
            matched: true,
          },
        ],
      },
    },
    riskMatrixExplanation:
      "临床安全红线运行时强制提升为最高优先级；红线级 CDSS 输出必须由医师逐次确认并经过静默试运行门槛。",
  },
  ruleRecommendation: {
    cardId: "card-rule-pharmacy-review",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-pharmacy-review",
    explanation: {
      matchType: "RULE",
      ruleId: "rule-pharmacy-review-antimicrobial",
      ruleCode: "RULE.MEDICATION.PHARMACY_REVIEW.ANTIMICROBIAL",
      ruleVersionId: "rv-pharmacy-review-antimicrobial",
      runtimeRelease: {
        runtimeReleaseId: "runtime-pharmacy-review",
        assetVersionId: "av-rule-pharmacy-review",
        assetVersionNo: "V1",
        contentHash: "7".repeat(64),
      },
      ruleExplanation: {
        title: "抗菌药物审方代表切片规则",
        reason:
          "Medication、Condition、Observation 均来自当前临床上下文，规则由当前机构生效版本锁定。",
        conditionEvidence: [
          {
            fact: "medications[].code",
            operator: "contains",
            expected: "J01C",
            actual: ["J01C"],
            matched: true,
          },
          {
            fact: "conditions[].code",
            operator: "exists",
            expected: true,
            actual: ["J18.900"],
            matched: true,
          },
          {
            fact: "observations[].valueNumeric",
            operator: "gte",
            expected: 2,
            actual: [2.4],
            matched: true,
          },
        ],
        runtimeAssetEvidence: [
          {
            assetType: "ACTION_CARD",
            assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
            assetVersion: "V1",
            contentHash: "9".repeat(64),
            requiresPhysicianConfirmation: true,
          },
        ],
      },
    },
  },
  feedback: {
    pharmacist: {
      feedbackId: "rf-pharmacy-pharmacist",
      cardStatus: "PENDING",
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: {
        feedbackId: "rf-pharmacy-pharmacist",
        feedbackType: "VIEW_SOURCE",
        operatorRole: "PHARMACIST",
        reasonCode: "PHARMACIST_REVIEWED",
      },
    },
    physician: {
      feedbackId: "rf-pharmacy-physician",
      cardStatus: "ACCEPTED",
      canonicalSessionRole: "clinical-user",
      roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
      persisted: {
        feedbackId: "rf-pharmacy-physician",
        feedbackType: "ACCEPT",
        operatorRole: "DOCTOR",
        reasonCode: "CONFIRMED",
      },
    },
    noAutoOrder: true,
    actionCardEvidence: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
      versionId: "av-action-pharmacy-review",
      versionNo: "V1",
      contentHash: "9".repeat(64),
      entryState: "ACTIVE",
      requiresPhysicianConfirmation: true,
      noAutoOrder: true,
    },
  },
  qualityRectification: {
    findingId: "finding-pharmacy-review",
    sourceType: "PHARMACY_REVIEW",
    sourceId: "card-pharmacy-review",
    severity: "P1",
    findingStatus: "CLOSED",
    taskId: "task-pharmacy-review",
    taskStatus: "CLOSED",
    submittedByRole: "engine-operator",
    reviewedByRole: "engine-operator",
    roleEvidence: "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW",
    submittedEvidenceRef: "pharmacy-review-antimicrobial-evidence",
    reviewDecision: "APPROVED",
  },
  scenarioConditionEvidence: [
    {
      code: "S18__HIGH_RISK",
      scenarioCode: "S18",
      condition: "HIGH_RISK",
      source: "PHARMACY_REVIEW_ANTIMICROBIAL_CRITICAL_MANUAL_CONFIRMATION",
      evidence: [
        "抗菌药物 SAFETY 红线和风险矩阵均为 CRITICAL",
        "推荐卡要求医生确认且药师复核不关闭医生确认链路",
        "医生逐条确认采纳并保持 noAutoOrder=true",
      ],
    },
    {
      code: "S31__DEGRADATION",
      scenarioCode: "S31",
      condition: "DEGRADATION",
      source: "PHARMACY_REVIEW_OUTBOUND_NOT_CONNECTED",
      evidence: [
        "PHARMACY_REVIEW 出站审方请求收敛到 NOT_CONNECTED",
        "断连补偿不阻断本地推荐、药师复核和医生确认主链路",
      ],
    },
    {
      code: "S31__ABNORMAL",
      scenarioCode: "S31",
      condition: "ABNORMAL",
      source: "PHARMACY_REVIEW_RECTIFICATION_REVIEW",
      evidence: ["药事治理问题形成 P1 整改任务", "固定职责账号提交并复核关闭整改"],
    },
  ],
  scenarioEvidence: [
    {
      code: "S18",
      observedStages: [
        "运营员发布抗菌药物术语、红线、风险矩阵、规则和动作卡资产",
        "当前机构生效版本包含抗菌药物五类运行资产",
        "临床用户从患者 360 建立 Medication、AllergyIntolerance、Condition 与 Observation 上下文",
        "临床用户从真实前台触发 medication-prescribe 推荐评估",
        "推荐卡证明抗菌药物红线、规则和动作卡按当前机构生效版本消费",
        "药师登记审方复核且不关闭医生确认链路",
        "医生逐条确认采纳，系统不自动开嘱",
      ],
    },
    {
      code: "S31",
      observedStages: [
        "平台管理员访问真实前台并经真实服务创建 PHARMACY_REVIEW 适配器、回调通道和签名预览",
        "系统向 PHARMACY_REVIEW 发出审方请求并诚实断连降级",
        "PHARMACY_REVIEW 签名回传审方结果并生成标准临床事件",
        "药事治理问题形成整改任务",
        "固定四职责账号提交并复核关闭本轮整改任务",
      ],
    },
  ],
};

function pharmacyReviewAntimicrobialEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/pharmacy-review-antimicrobial-frontdesk.spec.ts",
        title:
          "临床用户按医生和药师业务任职与运营员、平台管理员完成本轮抗菌药物审方代表切片回传、推荐确认和整改闭环",
        status: "passed",
        attachments: [
          {
            name: "pharmacy-review-antimicrobial-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoPharmacyReviewAntimicrobialCoverage(body: Record<string, unknown>) {
  const evidence = pharmacyReviewAntimicrobialEvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["S18", "S31"]),
  );
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["TERMINOLOGY", "SAFETY", "CDSS_RISK", "RULE", "ACTION_CARD"]),
  );
  expect(
    evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code) ?? [],
  ).not.toContain("PHARMACY_REVIEW");
}

const infectionPublicHealthSafetyEvidence = {
  scenarioCodes: ["S21", "S32"],
  productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
  versionedAssets: ["TERMINOLOGY", "RULE", "ACTION_CARD"],
  deliveryShapes: ["API_EVENT"],
  serviceCombinations: [
    "THIRD_PARTY_INTERFACE",
    "CLINICAL_RUNTIME",
    "PROFESSIONAL_COLLABORATION",
    "QUALITY_IMPROVEMENT",
  ],
  scopeStatement:
    "院感公卫与医疗安全事件代表切片：PUBLIC_HEALTH_INFECTION_REGULATORY 入站、感染监测、上报预填、人工确认和安全事件整改闭环，不代表完整院感系统、完整公卫法定上报、完整不良事件系统或第三方公卫院感监管系统族完整覆盖。",
  apiEvidence: {
    publicHealthAdapterCreatedThroughRealService: true,
    publicHealthWebhookCreatedThroughRealService: true,
    webhookSignaturePreviewGenerated: true,
    infectionTerminologyActivated: true,
    publicHealthActionCardPublished: true,
    publicHealthRuleCreated: true,
    runtimeActivatedWithPublicHealthAssets: true,
    contextSnapshotCreatedFromFrontdesk: true,
    prefillOutboundRequested: true,
    inboundPublicHealthReportAccepted: true,
    clinicalEvaluationTriggeredFromFrontdesk: true,
    humanReportReviewRecorded: true,
    safetyRectificationSubmittedAndReviewed: true,
  },
  adapter: {
    adapterId: "adapter-public-health-infection",
    systemFamilyCode: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    targetSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    protocolType: "Webhook",
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      { sourcePath: "/infectionCode", targetPath: "/conditions/0", targetDictionaryKey: "ICD-10" },
      { sourcePath: "/labCode", targetPath: "/observations/0/code" },
      { sourcePath: "/labResult", targetPath: "/observations/0/valueString" },
      { sourcePath: "/reportCardType", targetPath: "/documents/0/documentType" },
      { sourcePath: "/reportCardDigest", targetPath: "/documents/0/contentDigest" },
      {
        sourcePath: "/publicHealthReport/reportType",
        targetPath: "/publicHealthReport/reportType",
      },
      {
        sourcePath: "/publicHealthReport/manualSubmitRequired",
        targetPath: "/publicHealthReport/manualSubmitRequired",
      },
      {
        sourcePath: "/publicHealthReport/legalSubmissionDelegated",
        targetPath: "/publicHealthReport/legalSubmissionDelegated",
      },
      { sourcePath: "/safetyEvent/eventType", targetPath: "/safetyEvent/eventType" },
      { sourcePath: "/safetyEvent/riskLevel", targetPath: "/safetyEvent/riskLevel" },
      {
        sourcePath: "/safetyEvent/rectificationRequired",
        targetPath: "/safetyEvent/rectificationRequired",
      },
    ],
  },
  webhookSignature: {
    webhookId: "webhook-public-health-infection",
    adapterId: "adapter-public-health-infection",
    signatureAlgorithm: "HMAC-SHA256",
    canonicalPayloadIncludesTraceId: true,
    previewGenerated: true,
  },
  terminologyGate: {
    assetType: "TERMINOLOGY",
    assetIdentity: "TERM.PUBLIC_HEALTH.INFECTION",
    versionId: "av-term-public-health-infection",
    versionNo: "V1",
    contentHash: "a".repeat(64),
    standardSystem: "ICD-10",
    standardCode: "U07.100",
    localCode: "PH-COVID-19",
    sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    category: "DIAGNOSIS",
    mappingId: 41,
  },
  actionCard: {
    assetType: "ACTION_CARD",
    assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
    versionId: "av-action-public-health-infection",
    versionNo: "V1",
    contentHash: "b".repeat(64),
    entryState: "ACTIVE",
    requiresHumanReportReview: true,
    noLegalAutoSubmit: true,
  },
  ruleAsset: {
    assetType: "RULE",
    assetIdentity: "RULE.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
    versionId: "av-rule-public-health-infection",
    versionNo: "V1",
    contentHash: "c".repeat(64),
    ruleId: "rule-public-health-infection",
    ruleVersionId: "rv-public-health-infection",
  },
  runtime: {
    releaseId: "runtime-public-health-infection",
    revisionNo: 32,
    manifestSha256: "e".repeat(64),
    assets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.PUBLIC_HEALTH.INFECTION",
        versionId: "av-term-public-health-infection",
        versionNo: "V1",
        contentHash: "a".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
        versionId: "av-rule-public-health-infection",
        versionNo: "V1",
        contentHash: "c".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
        versionId: "av-action-public-health-infection",
        versionNo: "V1",
        contentHash: "b".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    terminologyAsset: {
      assetType: "TERMINOLOGY",
      assetIdentity: "TERM.PUBLIC_HEALTH.INFECTION",
      versionId: "av-term-public-health-infection",
      versionNo: "V1",
      contentHash: "a".repeat(64),
      entryState: "ACTIVE",
    },
    ruleAsset: {
      assetType: "RULE",
      assetIdentity: "RULE.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
      versionId: "av-rule-public-health-infection",
      versionNo: "V1",
      contentHash: "c".repeat(64),
      entryState: "ACTIVE",
    },
    actionCardAsset: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
      versionId: "av-action-public-health-infection",
      versionNo: "V1",
      contentHash: "b".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.PUBLIC_HEALTH.INFECTION",
        versionId: "av-term-public-health-infection",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
        versionId: "av-rule-public-health-infection",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
        versionId: "av-action-public-health-infection",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-public-health-infection",
    encounterId: "enc-public-health-infection",
    contextSnapshotId: "ctx-public-health-infection",
    runtimeReleaseId: "runtime-public-health-infection",
    resources: {
      conditions: [
        {
          code: "U07.100",
          codeSystem: "ICD-10",
          displayName: "新型冠状病毒感染",
          sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
        },
      ],
      observations: [
        {
          observationId: "obs-nat-result",
          code: "NAT_RESULT",
          valueString: "POSITIVE",
          sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
        },
      ],
      documents: [
        {
          documentId: "doc-public-health-report",
          documentType: "PUBLIC_HEALTH_REPORT_PREFILL",
          contentDigest: "sha256:public-health-report-prefill",
          sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
        },
      ],
      extensions: {
        local: {
          publicHealthReport: {
            reportType: "INFECTIOUS_DISEASE_PREFILL",
            reportableCondition: "SUSPECTED_COVID_19",
            manualSubmitRequired: true,
            legalSubmissionDelegated: false,
            prefillStatus: "READY_FOR_HUMAN_REVIEW",
          },
          safetyEvent: {
            eventType: "OCCUPATIONAL_EXPOSURE",
            riskLevel: "HIGH",
            rootCause: "ISOLATION_PROTOCOL_GAP",
            rectificationRequired: true,
            reviewRequired: true,
          },
        },
      },
    },
  },
  outboundPrefill: {
    messageId: "out-public-health-prefill",
    traceId: "trace-public-health-infection",
    adapterId: "adapter-public-health-infection",
    targetSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    protocolType: "Webhook",
    status: "RETRYING",
    compensationStatus: "NOT_CONNECTED",
    compensationMessageId: "out-public-health-prefill",
    blocksMainFlow: false,
    compensationRequired: true,
    payload: {
      patientId: "mpi-public-health-infection",
      contextSnapshotId: "ctx-public-health-infection",
      reportType: "INFECTIOUS_DISEASE_PREFILL",
      manualSubmitRequired: true,
      legalSubmissionDelegated: false,
    },
  },
  inboundReport: {
    messageId: "in-public-health-report",
    traceId: "trace-public-health-infection",
    adapterId: "adapter-public-health-infection",
    webhookId: "webhook-public-health-infection",
    patientId: "mpi-public-health-infection",
    encounterId: "enc-public-health-infection",
    contextSnapshotId: "ctx-public-health-infection",
    sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
    status: "SUCCESS",
    clinicalEventStatus: "RECEIVED",
    clinicalEvent: {
      eventId: "evt-wh-public-health-infection",
      status: "PROCESSED",
      errorCode: null,
      errorClass: null,
      retryCount: 0,
      runtimeReleaseId: "runtime-public-health-infection",
    },
    mappedFieldCount: 16,
    mappedPayload: {
      conditions: [
        {
          standardCode: "U07.100",
          codeSystem: "ICD-10",
          sourceSystem: "PUBLIC_HEALTH_INFECTION_REGULATORY",
          runtimeReleaseId: "runtime-public-health-infection",
        },
      ],
      observations: [{ code: "NAT_RESULT", valueString: "POSITIVE" }],
      documents: [
        {
          documentType: "PUBLIC_HEALTH_REPORT_PREFILL",
          contentDigest: "sha256:public-health-report-prefill",
        },
      ],
      publicHealthReport: {
        reportType: "INFECTIOUS_DISEASE_PREFILL",
        manualSubmitRequired: true,
        legalSubmissionDelegated: false,
        prefillStatus: "READY_FOR_HUMAN_REVIEW",
      },
      safetyEvent: {
        eventType: "OCCUPATIONAL_EXPOSURE",
        riskLevel: "HIGH",
        rootCause: "ISOLATION_PROTOCOL_GAP",
        rectificationRequired: true,
        reviewRequired: true,
      },
    },
    signedPayload: {
      patientId: "mpi-public-health-infection",
      contextSnapshotId: "ctx-public-health-infection",
      infectionCode: "PH-COVID-19",
      labCode: "NAT_RESULT",
      labResult: "POSITIVE",
      publicHealthReport: {
        manualSubmitRequired: true,
        legalSubmissionDelegated: false,
      },
      safetyEvent: {
        rectificationRequired: true,
        reviewRequired: true,
      },
    },
  },
  clinicalTrigger: {
    triggerId: "trigger-public-health-infection",
    contextSnapshotId: "ctx-public-health-infection",
    runtimeReleaseId: "runtime-public-health-infection",
    cardId: "card-public-health-infection",
    relatedCardIds: ["card-public-health-infection"],
  },
  recommendation: {
    cardId: "card-public-health-infection",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-public-health-infection",
    cardType: "REPORT",
    requiresPhysicianConfirmation: true,
    aiGenerated: false,
    explanation: {
      matchType: "RULE",
      ruleId: "rule-public-health-infection",
      ruleCode: "RULE.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
      ruleVersionId: "rv-public-health-infection",
      runtimeRelease: {
        runtimeReleaseId: "runtime-public-health-infection",
        assetVersionId: "av-rule-public-health-infection",
        assetVersionNo: "V1",
        contentHash: "c".repeat(64),
      },
      ruleExplanation: {
        title: "院感公卫上报预填代表切片规则",
        reason: "感染诊断、检验结果和上报预填事实均来自当前临床上下文。",
        conditionEvidence: [
          {
            fact: "conditions[].code",
            operator: "equals",
            expected: "U07.100",
            actual: ["U07.100"],
            matched: true,
          },
          {
            fact: "observations[].valueString",
            operator: "equals",
            expected: "POSITIVE",
            actual: ["POSITIVE"],
            matched: true,
          },
          {
            fact: "extensions.local.publicHealthReport.manualSubmitRequired",
            operator: "equals",
            expected: true,
            actual: true,
            matched: true,
          },
        ],
        runtimeAssetEvidence: [
          {
            assetType: "ACTION_CARD",
            assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
            assetVersion: "V1",
            contentHash: "b".repeat(64),
            requiresHumanReportReview: true,
            noLegalAutoSubmit: true,
          },
        ],
      },
    },
  },
  manualReview: {
    feedbackId: "rf-public-health-review",
    cardStatus: "ACCEPTED",
    canonicalSessionRole: "clinical-user",
    roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
    persisted: {
      feedbackId: "rf-public-health-review",
      feedbackType: "ACCEPT",
      operatorRole: "DOCTOR",
    },
    noLegalAutoSubmit: true,
    actionCardEvidence: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
      versionId: "av-action-public-health-infection",
      versionNo: "V1",
      contentHash: "b".repeat(64),
      entryState: "ACTIVE",
      requiresHumanReportReview: true,
      noLegalAutoSubmit: true,
    },
  },
  qualityRectification: {
    findingId: "finding-public-health-safety",
    sourceType: "SAFETY_EVENT",
    sourceId: "card-public-health-infection",
    severity: "P1",
    findingStatus: "CLOSED",
    taskId: "task-public-health-safety",
    taskStatus: "CLOSED",
    submittedByRole: "engine-operator",
    reviewedByRole: "engine-operator",
    roleEvidence: "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW",
    submittedEvidenceRef: "public-health-safety-evidence",
    reviewDecision: "APPROVED",
  },
  scenarioConditionEvidence: [
    {
      code: "S21__HIGH_RISK",
      scenarioCode: "S21",
      condition: "HIGH_RISK",
      source: "INFECTION_PUBLIC_HEALTH_MANUAL_REPORT_CONFIRMATION",
      evidence: [
        "阳性入站包含 U07.100、POSITIVE 和 SUSPECTED_COVID_19",
        "上报预填必须人工提交且中枢不替代法定上报",
        "推荐卡要求医生确认并由临床用户人工采纳",
      ],
    },
    {
      code: "S21__DEGRADATION",
      scenarioCode: "S21",
      condition: "DEGRADATION",
      source: "PUBLIC_HEALTH_OUTBOUND_NOT_CONNECTED",
      evidence: [
        "PUBLIC_HEALTH_INFECTION_REGULATORY 出站上报预填收敛到 NOT_CONNECTED",
        "断连补偿不阻断本地推荐和人工确认主链路",
      ],
    },
    {
      code: "S32__ABNORMAL",
      scenarioCode: "S32",
      condition: "ABNORMAL",
      source: "PUBLIC_HEALTH_SAFETY_EVENT_RECTIFICATION_REVIEW",
      evidence: [
        "入站安全事件为 HIGH 风险职业暴露且要求整改复核",
        "固定职责账号提交并复核关闭本轮安全事件整改任务",
      ],
    },
  ],
  scenarioEvidence: [
    {
      code: "S21",
      observedStages: [
        "平台管理员访问真实前台并经真实服务创建 PUBLIC_HEALTH_INFECTION_REGULATORY 适配器、回调通道和签名预览",
        "运营员发布院感公卫术语、上报预填规则和动作卡资产",
        "当前机构生效版本包含院感公卫三类运行资产",
        "临床用户从患者 360 建立脱敏患者，签名入站事件生成感染诊断、检验结果和上报预填上下文",
        "系统向 PUBLIC_HEALTH_INFECTION_REGULATORY 发出上报预填回传并诚实断连降级",
        "PUBLIC_HEALTH_INFECTION_REGULATORY 签名回传感染监测结果并生成标准临床事件",
        "临床用户从真实前台触发 result-review 推荐评估",
        "推荐卡证明上报预填规则和动作卡按当前机构生效版本消费",
        "临床用户人工确认上报预填，系统不替代法定上报",
      ],
    },
    {
      code: "S32",
      observedStages: [
        "入站安全事件保留风险、原因和整改要求扩展证据",
        "医疗安全事件形成整改任务",
        "固定四职责账号提交并复核关闭本轮安全事件整改任务",
      ],
    },
  ],
};

function infectionPublicHealthSafetyEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/infection-public-health-safety-frontdesk.spec.ts",
        title: "临床用户与运营员、平台管理员完成院感公卫上报预填和医疗安全事件整改代表闭环",
        status: "passed",
        attachments: [
          {
            name: "infection-public-health-safety-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoInfectionPublicHealthSafetyCoverage(body: Record<string, unknown>) {
  const evidence = infectionPublicHealthSafetyEvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["S21", "S32"]),
  );
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["TERMINOLOGY", "RULE", "ACTION_CARD"]),
  );
  expect(
    evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code) ?? [],
  ).not.toContain("PUBLIC_HEALTH_INFECTION_REGULATORY");
}

const surgeryAnesthesiaTransfusionEvidence = {
  scenarioCodes: ["S26"],
  productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
  versionedAssets: ["TERMINOLOGY", "SAFETY", "CDSS_RISK", "RULE", "ACTION_CARD"],
  deliveryShapes: ["API_EVENT"],
  serviceCombinations: [
    "THIRD_PARTY_INTERFACE",
    "CLINICAL_RUNTIME",
    "PROFESSIONAL_COLLABORATION",
    "QUALITY_IMPROVEMENT",
  ],
  scopeStatement:
    "围手术期、麻醉与输血代表切片：NURSING_ANESTHESIA_TRANSFUSION_ICU 入站、术前核查、麻醉风险、用血确认、人工确认和时序质控整改闭环，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收。",
  apiEvidence: {
    surgeryAdapterCreatedThroughRealService: true,
    surgeryWebhookCreatedThroughRealService: true,
    webhookSignaturePreviewGenerated: true,
    surgeryTerminologyActivated: true,
    surgerySafetyAssetPromoted: true,
    surgeryRiskMatrixCreated: true,
    surgeryActionCardPublished: true,
    surgeryRuleCreated: true,
    runtimeActivatedWithSurgeryAssets: true,
    contextSnapshotCreatedFromFrontdesk: true,
    outboundChecklistRequested: true,
    inboundSurgeryEventAccepted: true,
    clinicalEvaluationTriggeredFromFrontdesk: true,
    humanRiskConfirmationRecorded: true,
    qualityRectificationSubmittedAndReviewed: true,
  },
  adapter: {
    adapterId: "adapter-surgery-anesthesia-transfusion",
    systemFamilyCode: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    targetSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    protocolType: "Webhook",
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      {
        sourcePath: "/procedureCode",
        targetPath: "/procedures/0",
        targetDictionaryKey: "ICD-9-CM-3",
      },
      { sourcePath: "/asaCode", targetPath: "/observations/0/code" },
      { sourcePath: "/asaClass", targetPath: "/observations/0/valueString" },
      { sourcePath: "/anesthesiaDrugCode", targetPath: "/medications/0/standardCode" },
      { sourcePath: "/checklistType", targetPath: "/documents/0/documentType" },
      { sourcePath: "/checklistDigest", targetPath: "/documents/0/contentDigest" },
      { sourcePath: "/surgeryPlan/surgeryLevel", targetPath: "/surgeryPlan/surgeryLevel" },
      {
        sourcePath: "/anesthesiaAssessment/airwayRisk",
        targetPath: "/anesthesiaAssessment/airwayRisk",
      },
      {
        sourcePath: "/transfusionRequest/noAutoTransfusion",
        targetPath: "/transfusionRequest/noAutoTransfusion",
      },
    ],
  },
  webhookSignature: {
    webhookId: "webhook-surgery-anesthesia-transfusion",
    adapterId: "adapter-surgery-anesthesia-transfusion",
    signatureAlgorithm: "HMAC-SHA256",
    canonicalPayloadIncludesTraceId: true,
    previewGenerated: true,
  },
  terminologyGate: {
    assetType: "TERMINOLOGY",
    assetIdentity: "TERM.SURGERY_ANESTHESIA_TRANSFUSION.PROCEDURE",
    versionId: "av-term-surgery",
    versionNo: "V1",
    contentHash: "a".repeat(64),
    standardSystem: "ICD-9-CM-3",
    standardCode: "47.0901",
    localCode: "OR-LAP-APP",
    localTermId: 5201,
    standardTermId: 470901,
    sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    category: "PROCEDURE",
    mappingId: 52,
    confirmedMapping: {
      mappingId: 52,
      localTermId: 5201,
      standardTermId: 470901,
      sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      category: "PROCEDURE",
    },
  },
  safetyRedline: {
    assetType: "SAFETY",
    assetIdentity: "SAFETY.RDL-SURGERY-ANESTHESIA-TRANSFUSION",
    versionId: "av-safety-surgery",
    versionNo: "V1",
    contentHash: "b".repeat(64),
    entryState: "ACTIVE",
    category: "SURGERY_ANESTHESIA_TRANSFUSION",
    hazardSeverity: "CRITICAL",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    noAutoTransfusion: true,
    noAutoSurgery: true,
  },
  riskMatrix: {
    assetType: "CDSS_RISK",
    assetIdentity: "CDSS.RISK.MATRIX",
    versionId: "av-risk-surgery",
    versionNo: "V1",
    contentHash: "f".repeat(64),
    entryState: "ACTIVE",
    triggerPoint: "order-sign",
    riskLevel: "CRITICAL",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    automationLevel: "INFORM_ONLY",
    autoExecutionAllowed: false,
  },
  actionCard: {
    assetType: "ACTION_CARD",
    assetIdentity: "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
    versionId: "av-action-surgery",
    versionNo: "V1",
    contentHash: "c".repeat(64),
    entryState: "ACTIVE",
    requiresPhysicianConfirmation: true,
    noAutoOrder: true,
    noAutoTransfusion: true,
    noAutoSurgery: true,
  },
  ruleAsset: {
    assetType: "RULE",
    assetIdentity: "RULE.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
    versionId: "av-rule-surgery",
    versionNo: "V1",
    contentHash: "d".repeat(64),
    ruleId: "rule-surgery-checklist",
    ruleVersionId: "rv-surgery-checklist",
  },
  runtime: {
    releaseId: "runtime-surgery",
    revisionNo: 26,
    manifestSha256: "e".repeat(64),
    assets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.SURGERY_ANESTHESIA_TRANSFUSION.PROCEDURE",
        versionId: "av-term-surgery",
        versionNo: "V1",
        contentHash: "a".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-SURGERY-ANESTHESIA-TRANSFUSION",
        versionId: "av-safety-surgery",
        versionNo: "V1",
        contentHash: "b".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
        versionId: "av-rule-surgery",
        versionNo: "V1",
        contentHash: "d".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "CDSS_RISK",
        assetIdentity: "CDSS.RISK.MATRIX",
        versionId: "av-risk-surgery",
        versionNo: "V1",
        contentHash: "f".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
        versionId: "av-action-surgery",
        versionNo: "V1",
        contentHash: "c".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    terminologyAsset: {
      assetType: "TERMINOLOGY",
      assetIdentity: "TERM.SURGERY_ANESTHESIA_TRANSFUSION.PROCEDURE",
      versionId: "av-term-surgery",
      versionNo: "V1",
      contentHash: "a".repeat(64),
      entryState: "ACTIVE",
    },
    safetyAsset: {
      assetType: "SAFETY",
      assetIdentity: "SAFETY.RDL-SURGERY-ANESTHESIA-TRANSFUSION",
      versionId: "av-safety-surgery",
      versionNo: "V1",
      contentHash: "b".repeat(64),
      entryState: "ACTIVE",
    },
    cdssRiskAsset: {
      assetType: "CDSS_RISK",
      assetIdentity: "CDSS.RISK.MATRIX",
      versionId: "av-risk-surgery",
      versionNo: "V1",
      contentHash: "f".repeat(64),
      entryState: "ACTIVE",
    },
    ruleAsset: {
      assetType: "RULE",
      assetIdentity: "RULE.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
      versionId: "av-rule-surgery",
      versionNo: "V1",
      contentHash: "d".repeat(64),
      entryState: "ACTIVE",
    },
    actionCardAsset: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
      versionId: "av-action-surgery",
      versionNo: "V1",
      contentHash: "c".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.SURGERY_ANESTHESIA_TRANSFUSION.PROCEDURE",
        versionId: "av-term-surgery",
      },
      {
        assetType: "SAFETY",
        assetIdentity: "SAFETY.RDL-SURGERY-ANESTHESIA-TRANSFUSION",
        versionId: "av-safety-surgery",
      },
      { assetType: "CDSS_RISK", assetIdentity: "CDSS.RISK.MATRIX", versionId: "av-risk-surgery" },
      {
        assetType: "RULE",
        assetIdentity: "RULE.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
        versionId: "av-rule-surgery",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
        versionId: "av-action-surgery",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-surgery",
    encounterId: "enc-surgery",
    contextSnapshotId: "ctx-surgery",
    runtimeReleaseId: "runtime-surgery",
    resources: {
      procedures: [{ code: "47.0901", displayName: "腹腔镜阑尾切除术", anesthesiaType: "GENERAL" }],
      observations: [{ code: "ASA_CLASS", valueString: "III" }],
      medications: [{ code: "N01AB06", displayName: "七氟烷" }],
      documents: [
        {
          documentType: "SURGERY_SAFETY_CHECKLIST",
          contentDigest: "sha256:surgery-safety-checklist",
        },
      ],
      extensions: {
        local: {
          surgeryPlan: {
            surgeryLevel: "LEVEL_3",
            preOpAssessmentStatus: "PASSED_WITH_RISK",
            timeOutRequired: true,
          },
          anesthesiaAssessment: {
            airwayRisk: "DIFFICULT_AIRWAY",
            anesthesiologistReviewRequired: true,
          },
          transfusionRequest: {
            crossmatchStatus: "MATCHED",
            transfusionConsentConfirmed: true,
            noAutoTransfusion: true,
          },
        },
      },
    },
  },
  outboundChecklist: {
    messageId: "out-surgery-checklist",
    traceId: "trace-surgery",
    adapterId: "adapter-surgery-anesthesia-transfusion",
    targetSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    protocolType: "Webhook",
    status: "RETRYING",
    compensationStatus: "NOT_CONNECTED",
    compensationMessageId: "out-surgery-checklist",
    blocksMainFlow: false,
    compensationRequired: true,
    payload: {
      patientId: "mpi-surgery",
      contextSnapshotId: "ctx-surgery",
      noAutoTransfusion: true,
      noAutoSurgery: true,
    },
  },
  inboundSurgeryEvent: {
    messageId: "in-surgery",
    traceId: "trace-surgery",
    adapterId: "adapter-surgery-anesthesia-transfusion",
    webhookId: "webhook-surgery-anesthesia-transfusion",
    patientId: "mpi-surgery",
    encounterId: "enc-surgery",
    contextSnapshotId: "ctx-surgery",
    sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
    status: "SUCCESS",
    clinicalEventStatus: "RECEIVED",
    clinicalEvent: {
      eventId: "evt-wh-surgery",
      status: "PROCESSED",
      errorCode: null,
      errorClass: null,
      retryCount: 0,
      runtimeReleaseId: "runtime-surgery",
    },
    mappedFieldCount: 20,
    mappedPayload: {
      procedures: [
        {
          standardCode: "47.0901",
          codeSystem: "ICD-9-CM-3",
          sourceSystem: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
          runtimeReleaseId: "runtime-surgery",
        },
      ],
      observations: [{ code: "ASA_CLASS", valueString: "III" }],
      medications: [{ standardCode: "N01AB06" }],
      documents: [
        {
          documentType: "SURGERY_SAFETY_CHECKLIST",
          contentDigest: "sha256:surgery-safety-checklist",
        },
      ],
      surgeryPlan: { timeOutRequired: true },
      anesthesiaAssessment: {
        airwayRisk: "DIFFICULT_AIRWAY",
        anesthesiologistReviewRequired: true,
      },
      transfusionRequest: {
        crossmatchStatus: "MATCHED",
        transfusionConsentConfirmed: true,
        noAutoTransfusion: true,
      },
    },
    signedPayload: {
      procedureCode: "OR-LAP-APP",
      asaClass: "III",
      transfusionRequest: { noAutoTransfusion: true },
    },
  },
  clinicalTrigger: {
    triggerId: "trigger-surgery",
    contextSnapshotId: "ctx-surgery",
    runtimeReleaseId: "runtime-surgery",
    triggerType: "order-sign",
    cardId: "card-surgery",
    relatedCardIds: ["card-surgery"],
  },
  recommendation: {
    cardId: "card-surgery",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-surgery",
    cardType: "WARNING",
    requiresPhysicianConfirmation: true,
    aiGenerated: false,
    explanation: {
      matchType: "RULE",
      ruleId: "rule-surgery-checklist",
      ruleCode: "RULE.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
      ruleVersionId: "rv-surgery-checklist",
      runtimeRelease: {
        runtimeReleaseId: "runtime-surgery",
        assetVersionId: "av-rule-surgery",
        assetVersionNo: "V1",
        contentHash: "d".repeat(64),
      },
      ruleExplanation: {
        conditionEvidence: [
          { fact: "procedures[].code", matched: true },
          { fact: "observations[].valueString", matched: true },
          { fact: "extensions.local.transfusionRequest.noAutoTransfusion", matched: true },
        ],
        runtimeAssetEvidence: [
          {
            assetType: "ACTION_CARD",
            assetIdentity: "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
            assetVersion: "V1",
            contentHash: "c".repeat(64),
            noAutoOrder: true,
            noAutoTransfusion: true,
            noAutoSurgery: true,
          },
        ],
      },
    },
  },
  manualConfirmation: {
    feedbackId: "rf-surgery",
    cardStatus: "ACCEPTED",
    canonicalSessionRole: "clinical-user",
    roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
    persisted: {
      feedbackId: "rf-surgery",
      feedbackType: "ACCEPT",
      operatorRole: "DOCTOR",
      reasonCode: "CONFIRMED",
    },
    noAutoOrder: true,
    noAutoTransfusion: true,
    noAutoSurgery: true,
    actionCardEvidence: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.SURGERY.ANESTHESIA.TRANSFUSION.CHECKLIST",
      versionId: "av-action-surgery",
      versionNo: "V1",
      contentHash: "c".repeat(64),
      entryState: "ACTIVE",
      noAutoOrder: true,
      noAutoTransfusion: true,
      noAutoSurgery: true,
    },
  },
  qualityRectification: {
    findingId: "finding-surgery",
    sourceType: "SURGERY_TIMELINE",
    sourceId: "card-surgery",
    severity: "P1",
    findingStatus: "CLOSED",
    taskId: "task-surgery",
    taskStatus: "CLOSED",
    submittedByRole: "engine-operator",
    reviewedByRole: "engine-operator",
    roleEvidence: "CANONICAL_FIXED_ROLE_EVALUATION_REMEDIATE_REVIEW",
    submittedEvidenceRef: "surgery-evidence",
    reviewDecision: "APPROVED",
  },
  scenarioConditionEvidence: [
    {
      code: "S26__HIGH_RISK",
      scenarioCode: "S26",
      condition: "HIGH_RISK",
      source: "SURGERY_ANESTHESIA_TRANSFUSION_CRITICAL_MANUAL_CONFIRMATION",
      evidence: [
        "SAFETY 红线和风险矩阵均为 CRITICAL",
        "围手术期困难气道、ASA III 和用血风险进入推荐卡",
        "医生人工确认且系统不自动开嘱、不自动输血、不自动手术",
      ],
    },
    {
      code: "S26__DEGRADATION",
      scenarioCode: "S26",
      condition: "DEGRADATION",
      source: "SURGERY_ANESTHESIA_TRANSFUSION_OUTBOUND_NOT_CONNECTED",
      evidence: [
        "外部手麻手术室输血核查回传收敛到 NOT_CONNECTED",
        "断连补偿不阻断本地推荐和人工确认主链路",
      ],
    },
    {
      code: "S26__ABNORMAL",
      scenarioCode: "S26",
      condition: "ABNORMAL",
      source: "SURGERY_TIMELINE_RECTIFICATION_REVIEW",
      evidence: ["围手术期时序质控形成 P1 整改任务", "固定职责账号提交并复核关闭整改"],
    },
  ],
  scenarioEvidence: [
    {
      code: "S26",
      observedStages: [
        "平台管理员访问真实前台并经真实服务创建 NURSING_ANESTHESIA_TRANSFUSION_ICU 适配器、回调通道和签名预览",
        "运营员发布手术操作术语、高危安全红线、麻醉用血风险矩阵、术前核查规则和动作卡资产",
        "当前机构生效版本包含围手术期五类运行资产",
        "签名入站事件生成 Procedure、Observation、Medication、Document 和手麻输血本地扩展上下文",
        "系统向 NURSING_ANESTHESIA_TRANSFUSION_ICU 发出核查确认回传并诚实断连降级",
        "临床用户从真实前台触发 order-sign 推荐评估",
        "推荐卡证明术前核查规则、安全红线和动作卡按当前机构生效版本消费",
        "临床用户人工确认围手术期风险，系统不自动输血、不自动开嘱、不自动手术",
        "围手术期时序质控形成整改任务并由固定职责账号复核关闭",
      ],
    },
  ],
};

function surgeryAnesthesiaTransfusionEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/surgery-anesthesia-transfusion-frontdesk.spec.ts",
        title: "临床用户与运营员、平台管理员完成围手术期麻醉输血核查代表闭环",
        status: "passed",
        attachments: [
          {
            name: "surgery-anesthesia-transfusion-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoSurgeryAnesthesiaTransfusionCoverage(body: Record<string, unknown>) {
  const evidence = surgeryAnesthesiaTransfusionEvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toContain("S26");
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["TERMINOLOGY", "SAFETY", "RULE", "ACTION_CARD"]),
  );
  expect(
    evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code) ?? [],
  ).not.toContain("NURSING_ANESTHESIA_TRANSFUSION_ICU");
}

const criticalEmergencyIcuEvidence = {
  scenarioCodes: ["S19", "S24", "S27"],
  productLayers: ["CLINICAL_EXECUTION", "DATA_INTEROPERABILITY"],
  versionedAssets: ["TERMINOLOGY", "CDSS_RISK", "RULE", "PATHWAY", "ACTION_CARD"],
  deliveryShapes: ["API_EVENT"],
  serviceCombinations: ["THIRD_PARTY_INTERFACE", "CLINICAL_RUNTIME", "PROFESSIONAL_COLLABORATION"],
  scopeStatement:
    "急诊分诊与 ICU 生命支持风险代表切片：LIS_MONITORING_CRITICAL 入站监护事实、HIS_EMR_CDR 急诊分诊上下文、当前机构生效版本风险规则、路径升级候选、人工确认和升级待办闭环，不代表完整急诊系统、完整 ICU 系统、完整生命支持系统、生命支持设备控制、完整 S19/S24/S27、完整 S0-S40 或完整上线验收。",
  apiEvidence: {
    monitoringAdapterCreatedThroughRealService: true,
    monitoringWebhookCreatedThroughRealService: true,
    emergencyOnboardingCreatedThroughRealService: true,
    webhookSignaturePreviewGenerated: true,
    terminologyActivated: true,
    riskMatrixCreated: true,
    ruleCreated: true,
    pathwayCreated: true,
    actionCardPublished: true,
    runtimeActivatedWithCriticalAssets: true,
    triageContextCreatedFromFrontdesk: true,
    inboundMonitoringEventAccepted: true,
    clinicalEvaluationTriggeredFromFrontdesk: true,
    humanEscalationConfirmationRecorded: true,
    workflowEscalationTodoCompleted: true,
  },
  monitoringAdapter: {
    adapterId: "adapter-critical-monitoring",
    systemFamilyCode: "LIS_MONITORING_CRITICAL",
    sourceSystem: "LIS_MONITORING_CRITICAL",
    targetSystem: "LIS_MONITORING_CRITICAL",
    protocolType: "Webhook",
    fieldMappings: [
      { sourcePath: "/patientId", targetPath: "/patient/mpi" },
      { sourcePath: "/shockIndexCode", targetPath: "/observations/0/code" },
      { sourcePath: "/shockIndexValue", targetPath: "/observations/0/valueNumeric" },
      { sourcePath: "/lactateCode", targetPath: "/observations/1", targetDictionaryKey: "LOINC" },
      { sourcePath: "/lactateValue", targetPath: "/observations/1/valueNumeric" },
      {
        sourcePath: "/ventilatorMode",
        targetPath: "/extensions/local/criticalCare/ventilatorMode",
      },
      {
        sourcePath: "/vasopressorRunning",
        targetPath: "/extensions/local/criticalCare/vasopressorRunning",
      },
      {
        sourcePath: "/noDeviceControl",
        targetPath: "/extensions/local/criticalCare/noDeviceControl",
      },
    ],
  },
  emergencyOnboarding: {
    onboardingId: "onb-critical-emergency",
    accessMode: "ADAPTER",
    adapterId: "adapter-critical-monitoring",
    systemFamilyCode: "LIS_MONITORING_CRITICAL",
    sourceSystem: "LIS_MONITORING_CRITICAL",
    businessScenario: "S19/S24/S27 急危重症预警",
    healthStatus: "NOT_CONNECTED",
  },
  webhookSignature: {
    webhookId: "webhook-critical-monitoring",
    adapterId: "adapter-critical-monitoring",
    signatureAlgorithm: "HMAC-SHA256",
    canonicalPayloadIncludesTraceId: true,
    previewGenerated: true,
  },
  terminologyGate: {
    assetType: "TERMINOLOGY",
    assetIdentity: "TERM.CRITICAL.EMERGENCY.ICU.LACTATE",
    versionId: "av-term-critical",
    versionNo: "V1",
    contentHash: "a".repeat(64),
    standardSystem: "LOINC",
    standardCode: "2524-7",
    localCode: "ICU-LAC",
    localTermId: 1901,
    standardTermId: 25247,
    sourceSystem: "LIS_MONITORING_CRITICAL",
    category: "LAB",
    mappingId: 19,
    confirmedMapping: {
      mappingId: 19,
      localTermId: 1901,
      standardTermId: 25247,
      sourceSystem: "LIS_MONITORING_CRITICAL",
      category: "LAB",
    },
  },
  riskMatrix: {
    assetType: "CDSS_RISK",
    assetIdentity: "CDSS.RISK.MATRIX",
    versionId: "av-risk-critical",
    versionNo: "V1",
    contentHash: "b".repeat(64),
    entryState: "ACTIVE",
    triggerPoint: "patient-view",
    riskLevel: "CRITICAL",
    reviewRequirement: "PHYSICIAN_CONFIRMATION",
    automationLevel: "INFORM_ONLY",
    autoExecutionAllowed: false,
  },
  actionCard: {
    assetType: "ACTION_CARD",
    assetIdentity: "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION",
    versionId: "av-action-critical",
    versionNo: "V1",
    contentHash: "c".repeat(64),
    entryState: "ACTIVE",
    requiresPhysicianConfirmation: true,
    noAutoOrder: true,
    noAutoTransfer: true,
    noDeviceControl: true,
    noAutoVentilatorChange: true,
  },
  ruleAsset: {
    assetType: "RULE",
    assetIdentity: "RULE.CRITICAL.EMERGENCY.ICU.ESCALATION",
    versionId: "av-rule-critical",
    versionNo: "V1",
    contentHash: "d".repeat(64),
    ruleId: "rule-critical-icu",
    ruleVersionId: "rv-critical-icu",
  },
  pathwayAsset: {
    assetType: "PATHWAY",
    assetIdentity: "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
    versionId: "av-pathway-critical",
    versionNo: "V1",
    contentHash: "e".repeat(64),
    templateId: "tpl-critical-icu",
  },
  runtime: {
    releaseId: "runtime-critical",
    revisionNo: 27,
    manifestSha256: "f".repeat(64),
    assets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.CRITICAL.EMERGENCY.ICU.LACTATE",
        versionId: "av-term-critical",
        versionNo: "V1",
        contentHash: "a".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "CDSS_RISK",
        assetIdentity: "CDSS.RISK.MATRIX",
        versionId: "av-risk-critical",
        versionNo: "V1",
        contentHash: "b".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "RULE",
        assetIdentity: "RULE.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-rule-critical",
        versionNo: "V1",
        contentHash: "d".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "PATHWAY",
        assetIdentity: "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-pathway-critical",
        versionNo: "V1",
        contentHash: "e".repeat(64),
        entryState: "ACTIVE",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-action-critical",
        versionNo: "V1",
        contentHash: "c".repeat(64),
        entryState: "ACTIVE",
      },
    ],
    terminologyAsset: {
      assetType: "TERMINOLOGY",
      assetIdentity: "TERM.CRITICAL.EMERGENCY.ICU.LACTATE",
      versionId: "av-term-critical",
      versionNo: "V1",
      contentHash: "a".repeat(64),
      entryState: "ACTIVE",
    },
    cdssRiskAsset: {
      assetType: "CDSS_RISK",
      assetIdentity: "CDSS.RISK.MATRIX",
      versionId: "av-risk-critical",
      versionNo: "V1",
      contentHash: "b".repeat(64),
      entryState: "ACTIVE",
    },
    ruleAsset: {
      assetType: "RULE",
      assetIdentity: "RULE.CRITICAL.EMERGENCY.ICU.ESCALATION",
      versionId: "av-rule-critical",
      versionNo: "V1",
      contentHash: "d".repeat(64),
      entryState: "ACTIVE",
    },
    pathwayAsset: {
      assetType: "PATHWAY",
      assetIdentity: "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
      versionId: "av-pathway-critical",
      versionNo: "V1",
      contentHash: "e".repeat(64),
      entryState: "ACTIVE",
    },
    actionCardAsset: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION",
      versionId: "av-action-critical",
      versionNo: "V1",
      contentHash: "c".repeat(64),
      entryState: "ACTIVE",
    },
  },
  activationRequest: {
    activeAssets: [
      {
        assetType: "TERMINOLOGY",
        assetIdentity: "TERM.CRITICAL.EMERGENCY.ICU.LACTATE",
        versionId: "av-term-critical",
      },
      { assetType: "CDSS_RISK", assetIdentity: "CDSS.RISK.MATRIX", versionId: "av-risk-critical" },
      {
        assetType: "RULE",
        assetIdentity: "RULE.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-rule-critical",
      },
      {
        assetType: "PATHWAY",
        assetIdentity: "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-pathway-critical",
      },
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-action-critical",
      },
    ],
  },
  clinicalContext: {
    patientId: "mpi-critical",
    encounterId: "enc-critical-ed",
    contextSnapshotId: "ctx-critical",
    runtimeReleaseId: "runtime-critical",
    clinicalSetting: "ED",
    resources: {
      encounters: [{ encounterType: "ED", departmentId: "ED" }],
      conditions: [{ code: "R57.900", displayName: "休克" }],
      observations: [
        { code: "SHOCK_INDEX", valueNumeric: 1.4, criticalFlag: "HIGH" },
        { code: "2524-7", valueNumeric: 5.2, unit: "mmol/L", criticalFlag: "CRITICAL" },
      ],
      procedures: [{ code: "5A1955Z", displayName: "机械通气" }],
      extensions: {
        local: {
          emergencyTriage: {
            triageLevel: "LEVEL_1",
            destinationCandidate: "ICU",
            manualEscalationRequired: true,
          },
          criticalCare: {
            ventilatorMode: "SIMV",
            vasopressorRunning: true,
            noDeviceControl: true,
          },
        },
      },
    },
  },
  inboundMonitoringEvent: {
    messageId: "in-critical-monitoring",
    traceId: "trace-critical",
    adapterId: "adapter-critical-monitoring",
    webhookId: "webhook-critical-monitoring",
    patientId: "mpi-critical",
    encounterId: "enc-critical-ed",
    contextSnapshotId: "ctx-critical",
    sourceSystem: "LIS_MONITORING_CRITICAL",
    status: "SUCCESS",
    clinicalEventStatus: "RECEIVED",
    clinicalEvent: {
      eventId: "evt-wh-critical",
      status: "PROCESSED",
      errorCode: null,
      errorClass: null,
      retryCount: 0,
      runtimeReleaseId: "runtime-critical",
    },
    mappedFieldCount: 7,
    mappedPayload: {
      observations: [
        { code: "SHOCK_INDEX", valueNumeric: 1.4, criticalFlag: "HIGH" },
        {
          standardCode: "2524-7",
          codeSystem: "LOINC",
          sourceSystem: "LIS_MONITORING_CRITICAL",
          runtimeReleaseId: "runtime-critical",
          valueNumeric: 5.2,
          criticalFlag: "CRITICAL",
        },
      ],
      criticalCare: {
        ventilatorMode: "SIMV",
        vasopressorRunning: true,
        noDeviceControl: true,
      },
    },
    signedPayload: {
      shockIndexValue: 1.4,
      lactateCode: "ICU-LAC",
      lactateValue: 5.2,
      criticalCare: { noDeviceControl: true },
    },
  },
  clinicalTrigger: {
    triggerId: "trigger-critical",
    contextSnapshotId: "ctx-critical",
    runtimeReleaseId: "runtime-critical",
    triggerType: "patient-view",
    cardId: "card-critical",
    relatedCardIds: ["card-critical"],
  },
  recommendation: {
    cardId: "card-critical",
    cardStatus: "PENDING",
    triggerRuntimeReleaseId: "runtime-critical",
    cardType: "RISK",
    requiresPhysicianConfirmation: true,
    aiGenerated: false,
    explanation: {
      matchType: "RULE",
      ruleId: "rule-critical-icu",
      ruleCode: "RULE.CRITICAL.EMERGENCY.ICU.ESCALATION",
      ruleVersionId: "rv-critical-icu",
      runtimeRelease: {
        runtimeReleaseId: "runtime-critical",
        assetVersionId: "av-rule-critical",
        assetVersionNo: "V1",
        contentHash: "d".repeat(64),
      },
      ruleExplanation: {
        conditionEvidence: [
          { fact: "observations[].criticalFlag", matched: true },
          { fact: "extensions.local.emergencyTriage.triageLevel", matched: true },
          { fact: "extensions.local.criticalCare.vasopressorRunning", matched: true },
        ],
        runtimeAssetEvidence: [
          {
            assetType: "ACTION_CARD",
            assetIdentity: "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION",
            assetVersion: "V1",
            contentHash: "c".repeat(64),
            noAutoOrder: true,
            noAutoTransfer: true,
            noDeviceControl: true,
          },
          {
            assetType: "PATHWAY",
            assetIdentity: "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
            assetVersion: "V1",
            contentHash: "e".repeat(64),
          },
        ],
      },
    },
  },
  manualEscalation: {
    feedbackId: "rf-critical",
    cardStatus: "ACCEPTED",
    canonicalSessionRole: "clinical-user",
    persisted: {
      feedbackId: "rf-critical",
      cardId: "card-critical",
      feedbackType: "ACCEPT",
      operatorRole: "DOCTOR",
      reasonCode: "CONFIRMED",
    },
    noAutoOrder: true,
    noAutoTransfer: true,
    noDeviceControl: true,
    noAutoVentilatorChange: true,
    actionCardEvidence: {
      assetType: "ACTION_CARD",
      assetIdentity: "ACTION_CARD.CRITICAL.EMERGENCY.ICU.ESCALATION",
      versionId: "av-action-critical",
      versionNo: "V1",
      contentHash: "c".repeat(64),
      entryState: "ACTIVE",
      noAutoOrder: true,
      noAutoTransfer: true,
      noDeviceControl: true,
      noAutoVentilatorChange: true,
    },
  },
  escalationTodo: {
    todoId: "todo-critical",
    sourceType: "RECOMMENDATION_CARD",
    sourceId: "card-critical",
    priority: "CRITICAL",
    status: "COMPLETED",
    completedByRole: "clinical-user",
    completionReason: "医生已人工确认升级处置候选，不自动转 ICU、不自动开嘱、不控制设备。",
    patientId: "mpi-critical",
    encounterId: "enc-critical-ed",
  },
  scenarioConditionEvidence: [
    {
      code: "S19__HIGH_RISK",
      scenarioCode: "S19",
      condition: "HIGH_RISK",
      source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
      evidence: [
        "监护入站和急诊上下文证明休克指数、乳酸和 CRITICAL 风险",
        "风险矩阵要求医师确认且禁止自动执行",
        "医生人工确认升级建议并保留不自动开嘱证据",
      ],
    },
    {
      code: "S24__HIGH_RISK",
      scenarioCode: "S24",
      condition: "HIGH_RISK",
      source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
      evidence: [
        "急诊分诊 LEVEL_1 且 ICU 去向仅作为人工确认候选",
        "系统不自动转 ICU、不自动开嘱",
        "临床用户完成升级协同待办",
      ],
    },
    {
      code: "S27__HIGH_RISK",
      scenarioCode: "S27",
      condition: "HIGH_RISK",
      source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
      evidence: [
        "ICU 生命支持上下文包含机械通气、升压药和不控制设备证据",
        "动作卡和人工确认均要求 noDeviceControl/noAutoVentilatorChange",
        "升级待办完成说明保留不控制设备边界",
      ],
    },
  ],
  rollbackNegativeEvidence: rollbackNegativeEvidence(
    [
      {
        assetType: "PATHWAY",
        assetIdentity: "PATHWAY.CRITICAL.EMERGENCY.ICU.ESCALATION",
        versionId: "av-pathway-critical",
      },
    ],
    "CRITICAL_EMERGENCY_ICU_PATHWAY",
  ),
  scenarioEvidence: [
    {
      code: "S19",
      observedStages: [
        "平台管理员登记 LIS_MONITORING_CRITICAL 监护入站适配器、回调通道和签名预览",
        "运营员发布乳酸术语、急危重症风险矩阵、预警规则、升级路径和动作卡资产",
        "当前机构生效版本包含急危重症五类运行资产",
        "签名入站监护事件生成生命体征和检验 Observation 并处理到 PROCESSED",
        "临床用户从真实前台触发 patient-view 急危重症预警评估",
        "推荐卡证明风险规则和动作卡按当前机构生效版本消费",
      ],
    },
    {
      code: "S24",
      observedStages: [
        "临床用户从患者 360 建立急诊分诊上下文和去向候选",
        "推荐卡证明分诊等级和留观或入 ICU 候选仅为人工确认建议",
        "医生人工确认升级候选，系统不自动转科、不自动开嘱",
      ],
    },
    {
      code: "S27",
      observedStages: [
        "入站上下文保留生命支持模式、升压药运行和不控制设备证据",
        "推荐卡证明 ICU 生命支持风险与升级路径按当前机构生效版本消费",
        "临床用户从真实待办完成升级协同，系统不控制呼吸机或生命支持设备",
      ],
    },
  ],
};

function criticalEmergencyIcuEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/critical-emergency-icu-frontdesk.spec.ts",
        title: "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
        status: "passed",
        attachments: [
          {
            name: "critical-emergency-icu-frontdesk-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoCriticalEmergencyIcuCoverage(body: Record<string, unknown>) {
  const evidence = criticalEmergencyIcuEvidenceResult(body);
  expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["S19", "S24", "S27"]),
  );
  expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toEqual(
    expect.arrayContaining(["TERMINOLOGY", "CDSS_RISK", "RULE", "PATHWAY", "ACTION_CARD"]),
  );
  expect(
    evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code) ?? [],
  ).not.toContain("LIS_MONITORING_CRITICAL");
}

const systemProvidersEvidence = {
  deliveryShapes: ["MANAGEMENT_WORKSPACE"],
  serviceCombinations: ["COMPLIANCE_OPERATIONS"],
  apiEvidence: {
    operationsSnapshotRead: true,
    backupReadinessObserved: true,
    honestDegradationObserved: true,
    evidenceDetailsObserved: true,
    runtimeReadbackObserved: true,
    runtimeConsumerReadbackObserved: true,
    clinicalSmokeAfterRestore: true,
    clinicalForbidden: true,
  },
  snapshot: {
    healthStatus: "UP",
    databaseDialect: "h2",
    migrationLocation: "classpath:db/migration/h2",
    activeProfiles: ["dev"],
  },
  backup: {
    rpo: "24h",
    rto: "4h",
    checksumPolicy: "SHA-256",
    backupScript: "deploy/docker/scripts/backup.sh",
    restoreScript: "deploy/docker/scripts/restore.sh",
    drillEvidence: {
      status: "SUCCESS",
      migrationCount: 42,
      evidenceReference: "/var/lib/medkernel/evidence/backup-restore.json",
      checksumEvidence: "sha256:abc",
      drillDatabaseIsIsolated: true,
      rpo: "24h",
      rto: "4h",
    },
  },
  dependencyEvidence: {
    dependencies: [
      { key: "backup-restore", displayName: "备份恢复", status: "UP" },
      { key: "external-provider", displayName: "外部系统连接", status: "NOT_CONNECTED" },
    ],
    honestDegradationText: "核心业务继续走本地确定性主链路",
  },
  accessEvidence: {
    platformAdminOperationsStatus: 200,
    clinicalOperationsStatus: 403,
    clinicalPageForbidden: true,
    clinicalPageNoOperationsData: true,
  },
  runtimeContinuityEvidence: {
    currentRuntime: {
      releaseId: "runtime-system-providers",
      revisionNo: 9,
      manifestSha256: "a".repeat(64),
      assetCount: 13,
    },
    runtimeConsumer: {
      contractVersion: "v1",
      releaseId: "runtime-system-providers",
      revisionNo: 9,
      manifestSha256: "a".repeat(64),
      assetCount: 13,
    },
    clinicalSmoke: {
      role: "clinical-user",
      page: "/mpi",
      patientId: "mpi-system-providers",
      contextSnapshotId: "ctx-system-providers",
      runtimeReleaseId: "runtime-system-providers",
    },
  },
  scenarioEvidence: [
    {
      observedStages: [
        "平台管理员读取真实服务运行保障快照",
        "前台展示备份恢复 RPO、RTO 与 SHA-256 校验策略",
        "前台展示依赖诚实降级并保留本地主链路提示",
        "证据详情展示部署档案、迁移路径和备份恢复诊断",
        "恢复后后端当前机构生效版本与第三方运行契约读回一致",
        "临床账号恢复后完成患者主索引和上下文主链路冒烟",
        "临床账号无法读取或展示服务运行保障快照",
      ],
    },
  ],
  scenarioConditionEvidence: [
    {
      code: "S15__NORMAL",
      scenarioCode: "S15",
      condition: "NORMAL",
      source: "SYSTEM_OPERATIONS_RESTORE_CONTINUITY",
      evidence: [
        "备份恢复成功后当前机构生效版本和第三方运行契约一致",
        "临床账号恢复后完成患者主索引和上下文主链路冒烟",
      ],
    },
    {
      code: "S15__DEGRADATION",
      scenarioCode: "S15",
      condition: "DEGRADATION",
      source: "SYSTEM_DEPENDENCY_HONEST_DEGRADATION",
      evidence: ["外部依赖 NOT_CONNECTED 时前台诚实展示降级且本地确定性主链路继续可用"],
    },
    {
      code: "S14__ABNORMAL",
      scenarioCode: "S14",
      condition: "ABNORMAL",
      source: "CLINICAL_SYSTEM_OPERATIONS_FORBIDDEN",
      evidence: ["临床账号 API 读取系统运维快照返回 403，前台只展示权限不足且不展示运维数据"],
    },
  ],
};

const identityBindingEvidence = {
  scenarioCodes: ["S14"],
  productLayers: ["FOUNDATION_GOVERNANCE"],
  serviceCombinations: ["COMPLIANCE_OPERATIONS"],
  apiEvidence: {
    personnelCreated: true,
    bindingPosted: true,
    bindingListRead: true,
    plaintextNotPersisted: true,
    duplicateRejected: true,
    unbindPosted: true,
    cleanupCompleted: true,
  },
  createdPersonnel: [
    {
      userId: "user-1",
      username: "idb-user-1",
      displayName: "身份绑定演练人员0001",
    },
    {
      userId: "user-2",
      username: "idb-user-2",
      displayName: "身份绑定演练人员0002",
    },
  ],
  binding: {
    bindingId: "binding-1",
    userId: "user-1",
    providerType: "EMPLOYEE_NO",
    subjectHint: "****A001",
    status: "ACTIVE",
    version: 1,
  },
  plaintextSafety: {
    subjectHintIncludesTail: true,
    listOmitsExternalSubjectDigest: true,
    listOmitsExternalSubjectPlaintext: true,
    duplicateStatus: 409,
    duplicateRejectedMessage: "该外部身份已绑定其他用户",
  },
  unbinding: {
    bindingId: "binding-1",
    status: "UNBOUND",
    versionAdvanced: true,
  },
  cleanup: {
    createdAccountDisabled: true,
    duplicateAccountDisabled: true,
    bindingUnboundOrAlreadyUnbound: true,
  },
  scenarioConditionEvidence: [
    {
      code: "S14__NORMAL",
      scenarioCode: "S14",
      condition: "NORMAL",
      source: "IDENTITY_BINDING_LIFECYCLE_PLAINTEXT_SAFETY",
      evidence: [
        "平台管理员前台绑定院内身份来源并列表脱敏回读",
        "重复外部身份被后端拒绝，解绑后保留历史证据并清理演练账号",
      ],
    },
  ],
  scenarioEvidence: [
    {
      code: "S14",
      observedStages: [
        "前台创建身份来源演练人员账号",
        "前台绑定院内身份来源",
        "列表回读只展示脱敏身份提示",
        "后端拒绝重复外部身份绑定",
        "前台解绑身份来源并保留历史证据",
        "停用身份来源演练账号",
      ],
    },
  ],
};

function identityBindingEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/identity-binding-frontdesk.spec.ts",
        title: "平台管理员可前台绑定和解绑院内身份来源且身份原文不落库",
        status: "passed",
        attachments: [
          {
            name: "identity-binding-scenario-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoIdentityBindingScenarioConditionCoverage(body: Record<string, unknown>) {
  const evidence = identityBindingEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain("S14__NORMAL");
}

const fourRoleCoreActionsEvidence = {
  scopeStatement:
    "四职责主动作代表闭环：平台管理员、医疗引擎运营员、临床使用者和审计员各完成一个真实前台主动作，包含服务端状态变化或只读校验、回读和审计证据；不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  roleActions: [
    {
      role: "platform-admin",
      path: "/admin/users",
      frontdeskAction: "前台新增人员、开通账号并回读身份来源",
      serviceOperation: "POST /api/v1/compliance/personnel",
      serviceStatus: 201,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "engine-operator",
      path: "/knowledge/production",
      frontdeskAction: "前台登记院内模型服务并回读生产前校验保持待连接状态",
      serviceOperation: "PUT /api/v1/model-providers/{providerCode}",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "clinical-user",
      path: "/workflow/todos",
      frontdeskAction: "前台筛选并完成本轮临床待办",
      serviceOperation: "POST /api/v1/engine/workflow/todos/{todoId}/complete",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "auditor",
      path: "/admin/audit",
      frontdeskAction: "前台按追踪号筛选审计事件并完成导出证据验签",
      serviceOperation: "POST /api/v1/compliance/evidence/snapshots/{evidenceId}/verify",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
  ],
  platformAdmin: {
    role: "platform-admin",
    path: "/admin/users",
    frontdeskAction: "前台新增人员、开通账号并回读身份来源",
    serviceOperation: "POST /api/v1/compliance/personnel",
    serviceStatus: 201,
    readbackVerified: true,
    auditVerified: true,
  },
  engineOperator: {
    role: "engine-operator",
    path: "/knowledge/production",
    frontdeskAction: "前台登记院内模型服务并回读生产前校验保持待连接状态",
    serviceOperation: "PUT /api/v1/model-providers/{providerCode}",
    serviceStatus: 200,
    readbackVerified: true,
    auditVerified: true,
  },
  clinicalUser: {
    role: "clinical-user",
    path: "/workflow/todos",
    frontdeskAction: "前台筛选并完成本轮临床待办",
    serviceOperation: "POST /api/v1/engine/workflow/todos/{todoId}/complete",
    serviceStatus: 200,
    readbackVerified: true,
    auditVerified: true,
  },
  auditor: {
    role: "auditor",
    path: "/admin/audit",
    frontdeskAction: "前台按追踪号筛选审计事件并完成导出证据验签",
    serviceOperation: "POST /api/v1/compliance/evidence/snapshots/{evidenceId}/verify",
    serviceStatus: 200,
    readbackVerified: true,
    auditVerified: true,
  },
};

function fourRoleCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/four-role-core-actions-rehearsal.spec.ts",
        title: "四职责主动作均完成真实前台操作与服务回读闭环",
        status: "passed",
        attachments: [
          {
            name: "four-role-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoFourRoleCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = fourRoleCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.roleRepresentativeCoreActions).toBeUndefined();
}

const sixEntryCoreActionsEvidence = {
  matrixCode: "SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE",
  scopeStatement:
    "六入口核心动作代表闭环：围绕安全与配置、知识审核发布中心、临床规则、消息通知、全真体验沙盘和来源血缘完成真实前台核心动作、服务回读与审计证据；不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  entryActions: [
    {
      role: "platform-admin",
      path: "/security/baseline",
      frontdeskAction: "前台保存配置、执行权限试算和脱敏预览",
      serviceOperation: "PATCH /api/v1/system/configs/{key}",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "engine-operator",
      path: "/knowledge/governance",
      frontdeskAction: "前台查看候选、打开审核对照并完成退修决策",
      serviceOperation: "POST /api/v1/engine/knowledge/review/{versionId}/decisions",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "engine-operator",
      path: "/rule/definitions",
      frontdeskAction: "前台新建规则草稿、保存验证用例并执行全部用例",
      serviceOperation: "POST /api/v1/engine/rules/{ruleId}/test-cases/run",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "clinical-user",
      path: "/notifications",
      frontdeskAction: "前台打开来源并标为已读",
      serviceOperation: "POST /api/v1/engine/notifications/{notificationId}/read",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "clinical-user",
      path: "/notifications/settings",
      frontdeskAction: "前台保存个人通知偏好并回读强制安全订阅",
      serviceOperation: "PUT /api/v1/engine/notifications/settings",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "clinical-user",
      path: "/sandbox",
      frontdeskAction: "前台运行真实协同链路并查看运行证据摘要",
      serviceOperation: "POST /api/v1/engine/sandbox/scenarios/{scenarioId}/run",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      role: "auditor",
      path: "/advanced/provenance",
      frontdeskAction: "前台检索来源血缘、选择版本并查看来源详情",
      serviceOperation: "GET /api/v1/engine/knowledge/identities/{id}/provenance",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
  ],
};

function sixEntryCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/entry-core-actions-rehearsal.spec.ts",
        title: "七个路由覆盖六类入口族完成真实前台核心动作代表闭环",
        status: "passed",
        attachments: [
          {
            name: "entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoSixEntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = sixEntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.entryRepresentativeCoreActions).toBeUndefined();
}

function complianceWorkbenchPersonalEntryEvidenceResult(
  roleBody: Record<string, unknown> = fourRoleCoreActionsEvidence,
  entryBody: Record<string, unknown> = sixEntryCoreActionsEvidence,
) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/four-role-core-actions-rehearsal.spec.ts",
        title: "四职责主动作均完成真实前台操作与服务回读闭环",
        status: "passed",
        attachments: [
          {
            name: "four-role-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(roleBody),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/entry-core-actions-rehearsal.spec.ts",
        title: "七个路由覆盖六类入口族完成真实前台核心动作代表闭环",
        status: "passed",
        attachments: [
          {
            name: "entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(entryBody),
          },
        ],
      },
    ],
  });
}

function expectNoComplianceWorkbenchPersonalEntryCoverage(
  roleBody: Record<string, unknown> = fourRoleCoreActionsEvidence,
  entryBody: Record<string, unknown> = sixEntryCoreActionsEvidence,
) {
  const evidence = complianceWorkbenchPersonalEntryEvidenceResult(roleBody, entryBody);
  expect(evidence.launchCoverage.complianceWorkbenchPersonalEntryMatrix).toBeUndefined();
  expect(evidence.launchCoverage.complianceWorkbenchPersonalEntryRows).toBeUndefined();
}

function fourRoleCoreActionsWithAuditorOverride(
  patch: Partial<typeof fourRoleCoreActionsEvidence.auditor>,
) {
  const body = structuredClone(fourRoleCoreActionsEvidence);
  body.roleActions = body.roleActions.map((item) =>
    item.role === "auditor" ? { ...item, ...patch } : item,
  );
  body.auditor = { ...body.auditor, ...patch };
  return body;
}

function sixEntryCoreActionsWithPathOverride(
  path: string,
  patch: Partial<(typeof sixEntryCoreActionsEvidence.entryActions)[number]>,
) {
  const body = structuredClone(sixEntryCoreActionsEvidence);
  body.entryActions = body.entryActions.map((item) =>
    item.path === path ? { ...item, ...patch } : item,
  );
  return body;
}

const platformAdminEntryCoreActionsEvidence = {
  matrixCode: "PLATFORM_ADMIN_P0_ENTRY_CORE_ACTIONS",
  scopeStatement:
    "平台管理员 P0 入口核心动作代表矩阵：围绕服务机构、身份来源、系统接入和服务运行保障四个入口完成真实前台核心动作、服务回读与审计证据；不代表 6 个平台管理员入口全部闭环，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  entryActions: [
    {
      menuKey: "tenant-onboarding",
      role: "platform-admin",
      path: "/tenant/onboarding",
      frontdeskAction: "前台开通服务机构并回读组织树和职责范围",
      serviceOperation: "POST /api/v1/admin/tenants",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "identity-bindings",
      role: "platform-admin",
      path: "/security/identity-binding",
      frontdeskAction: "前台绑定院内身份来源、拒绝重复身份并解绑留痕",
      serviceOperation: "POST /api/v1/compliance/identity-bindings",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "adapter-hub",
      role: "platform-admin",
      path: "/adapter/hub",
      frontdeskAction: "前台登记系统适配器、接入申请、健康诊断和数据质量报告",
      serviceOperation: "POST /api/v1/engine/integration/data-quality/reports",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "system-providers",
      role: "platform-admin",
      path: "/system/providers",
      frontdeskAction: "前台核查运行快照、备份恢复证据和依赖诚实降级",
      serviceOperation: "GET /api/v1/system/operations",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
  ],
};

type StakeholderRuntimeRecordFixture = {
  code: string;
  label: string;
  role: string;
  path: string;
  url: string;
  entryUrl: string;
  finalUrl: string;
  actions: string[];
  browserErrors: string[];
  serverErrors: string[];
  networkFailures: string[];
};

const launchReadinessStakeholderRecords: StakeholderRuntimeRecordFixture[] = [
  {
    code: "IT_MANAGER",
    label: "信息科长",
    role: "platform-admin",
    path: "/system/runtime-diagnostics",
    url: "http://localhost:5174/system/runtime-diagnostics",
    entryUrl: "http://localhost:5174/system/runtime-diagnostics",
    finalUrl: "http://localhost:5174/adapter/hub",
    actions: ["查看运行诊断扩展能力授权边界并生成系统接入数据质量报告"],
    browserErrors: [],
    serverErrors: [],
    networkFailures: [],
  },
  {
    code: "IMPLEMENTATION_ENGINEER",
    label: "实施工程师",
    role: "platform-admin",
    path: "/onboarding/guide",
    url: "http://localhost:5174/onboarding/guide",
    entryUrl: "http://localhost:5174/onboarding/guide",
    finalUrl: "http://localhost:5174/adapter/hub",
    actions: ["生成系统接入数据质量报告"],
    browserErrors: [],
    serverErrors: [],
    networkFailures: [],
  },
  {
    code: "HOSPITAL_EXECUTIVE",
    label: "院长",
    role: "engine-operator",
    path: "/qc/dashboard",
    url: "http://localhost:5174/qc/dashboard",
    entryUrl: "http://localhost:5174/qc/dashboard",
    finalUrl: "http://localhost:5174/qc/dashboard",
    actions: ["切换质量下钻类型并读取整改证据"],
    browserErrors: [],
    serverErrors: [],
    networkFailures: [],
  },
];

function stakeholderReadinessEvidence(records = launchReadinessStakeholderRecords) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
        title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
        status: "passed",
        attachments: [
          {
            name: "stakeholder-view-runtime-records",
            contentType: "application/json",
            body: JSON.stringify(records),
          },
        ],
      },
    ],
  });
}

const implementationGuideEntryCoreActionsEvidence = {
  matrixCode: "IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS",
  scopeStatement:
    "实施与验收入口代表动作矩阵：实施工程师从实施与验收页读取当前机构实施步骤和开通就绪状态，并进入系统接入生成上线前数据质量报告；不代表 34 个入口全部业务动作闭环，不代表第三方系统族全部真实消费者完成，不代表 134 清库重部署或完整交付验收。",
  entryActions: [
    {
      menuKey: "implementation-guide",
      role: "platform-admin",
      path: "/onboarding/guide",
      frontdeskAction: "实施工程师前台读取当前机构实施步骤、开通就绪状态并生成系统接入数据质量报告",
      serviceOperation:
        "GET /api/v1/engine/tenant/implementation-steps + GET /api/v1/engine/tenant/onboarding-readiness + POST /api/v1/engine/integration/data-quality/reports",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      implementationStepsReadbackVerified: true,
      onboardingReadinessReadbackVerified: true,
      dataQualityReportVerified: true,
    },
  ],
};

function implementationGuideEntryCoreActionsEvidenceResult(
  body: Record<string, unknown> = implementationGuideEntryCoreActionsEvidence,
) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
        title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
        status: "passed",
        attachments: [
          {
            name: "implementation-guide-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoImplementationGuideEntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = implementationGuideEntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.implementationGuideEntryCoreActions).toBeUndefined();
  expect(evidence.launchCoverage.implementationGuideEntryCoreActionRows).toBeUndefined();
}

const dashboardWorkbenchCoreActionsEvidence = {
  matrixCode: "DASHBOARD_WORKBENCH_CORE_ACTIONS",
  scopeStatement:
    "四职责工作台核心动作代表矩阵：四个固定职责均从 /dashboard 读取当前角色工作台、真实来源状态和主动作/高频任务入口，并完成主动作跳转；不代表 34 个入口全部业务动作闭环，不代表每个入口的完整业务流程，不代表完整上线验收。",
  roleActions: [
    {
      role: "platform-admin",
      row: "PLATFORM_ADMIN",
      title: "平台管理员工作台",
      path: "/dashboard",
      primaryActionLabel: "维护人员与账号",
      primaryActionPath: "/admin/users",
      highFrequencyPaths: ["/security/baseline", "/onboarding/guide", "/adapter/hub"],
      serviceOperation:
        "GET /api/v1/security/me + GET /api/v1/system/operations + GET /api/v1/compliance/audit/events + GET /api/v1/large-lists/audit-events/list + GET /api/v1/engine/tenant/success-plan",
      serviceStatus: 200,
      readbackVerified: true,
      primaryActionVerified: true,
      highFrequencyTasksVerified: true,
      sourceStatusVerified: true,
      noBrowserErrors: true,
      noServerErrors: true,
      noNetworkFailures: true,
    },
    {
      role: "engine-operator",
      row: "ENGINE_OPERATOR",
      title: "医疗引擎运营员工作台",
      path: "/dashboard",
      primaryActionLabel: "进入知识生产",
      primaryActionPath: "/knowledge/production",
      highFrequencyPaths: ["/knowledge/governance", "/qc/alerts", "/advanced/provenance"],
      serviceOperation:
        "GET /api/v1/security/me + GET /api/v1/compliance/audit/events + GET /api/v1/large-lists/audit-events/list",
      serviceStatus: 200,
      readbackVerified: true,
      primaryActionVerified: true,
      highFrequencyTasksVerified: true,
      sourceStatusVerified: true,
      noBrowserErrors: true,
      noServerErrors: true,
      noNetworkFailures: true,
    },
    {
      role: "clinical-user",
      row: "CLINICAL_USER",
      title: "临床使用者工作台",
      path: "/dashboard",
      primaryActionLabel: "处理协同任务",
      primaryActionPath: "/workflow/todos",
      highFrequencyPaths: ["/pathway/patients", "/cdss/fatigue", "/clinical/followup"],
      serviceOperation: "GET /api/v1/security/me",
      serviceStatus: 200,
      readbackVerified: true,
      primaryActionVerified: true,
      highFrequencyTasksVerified: true,
      sourceStatusVerified: true,
      noBrowserErrors: true,
      noServerErrors: true,
      noNetworkFailures: true,
    },
    {
      role: "auditor",
      row: "AUDITOR",
      title: "审计员工作台",
      path: "/dashboard",
      primaryActionLabel: "查看审计证据",
      primaryActionPath: "/admin/audit",
      highFrequencyPaths: ["/advanced/provenance", "/security/baseline"],
      serviceOperation:
        "GET /api/v1/security/me + GET /api/v1/system/operations + GET /api/v1/compliance/audit/events + GET /api/v1/large-lists/audit-events/list",
      serviceStatus: 200,
      readbackVerified: true,
      primaryActionVerified: true,
      highFrequencyTasksVerified: true,
      sourceStatusVerified: true,
      noBrowserErrors: true,
      noServerErrors: true,
      noNetworkFailures: true,
    },
  ],
};

function dashboardWorkbenchCoreActionsEvidenceResult(
  body: Record<string, unknown> = dashboardWorkbenchCoreActionsEvidence,
) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/product-role-journeys.spec.ts",
        title: "desktop-1440 下全部角色工作台可完成主任务起步",
        status: "passed",
        attachments: [
          {
            name: "dashboard-workbench-core-actions-codes-desktop-1440",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoDashboardWorkbenchCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = dashboardWorkbenchCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.dashboardWorkbenchCoreActions).toBeUndefined();
  expect(evidence.launchCoverage.dashboardWorkbenchCoreActionRows).toBeUndefined();
}

const platformAdminP1EntryCoreActionsEvidence = {
  matrixCode: "PLATFORM_ADMIN_P1_ENTRY_CORE_ACTIONS",
  scopeStatement:
    "平台管理员 P1 系统运维入口核心动作代表矩阵：围绕运行诊断和国产化适配自检两个入口完成真实前台核心动作、服务回读与审计证据；不代表 6 个平台管理员入口全部闭环，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  entryActions: [
    {
      menuKey: "runtime-diagnostics",
      role: "platform-admin",
      path: "/system/runtime-diagnostics",
      frontdeskAction: "前台核查运行摘要、服务契约和扩展能力授权边界",
      serviceOperation:
        "GET /api/v1/system/runtime + GET /api/v1/system/runtime-diagnostics/api-contracts",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "domestic-check",
      role: "platform-admin",
      path: "/advanced/domestic",
      frontdeskAction: "前台筛选国产化待确认项并导出国产化适配自检报告",
      serviceOperation:
        "GET /api/v1/system/operations + GET /api/v1/system/operations/domestic-report",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
  ],
};

const platformAdminP1SystemOperationsEvidence = {
  scopeStatement:
    "平台管理员 P1 系统运维入口真实前台证据：只证明运行诊断与国产化适配自检两个入口的代表核心动作，不代表 6 个平台管理员入口全部闭环，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  runtimeDiagnosticsEvidence: {
    runtimeStatus: 200,
    operationsStatus: 200,
    apiContractsStatus: 200,
    contractCount: 3,
    pluginBoundaryObserved: true,
    clinicalRuntimeStatus: 403,
    clinicalPageForbidden: true,
  },
  domesticCheckEvidence: {
    operationsStatus: 200,
    reportStatus: 200,
    reportContainsSummary: true,
    issueFilterObserved: true,
    unknownFilterObserved: true,
    clinicalOperationsStatus: 403,
    clinicalPageForbidden: true,
  },
};

const clinicalEntryCoreActionsEvidence = {
  matrixCode: "CLINICAL_COLLABORATION_ENTRY_CORE_ACTIONS",
  scopeStatement:
    "临床协同入口核心动作代表矩阵：围绕 MPI、患者路径、CDSS 提醒推荐、协同任务和随访协同五个入口完成真实前台核心动作、服务回读与审计证据；不代表完整临床流程，不代表 34 个入口全部业务动作闭环，不代表完整 S0-S40，不代表完整上线验收。",
  entryActions: [
    {
      menuKey: "mpi",
      role: "clinical-user",
      path: "/mpi",
      frontdeskAction: "临床用户前台创建脱敏患者并生成患者上下文快照",
      serviceOperation: "POST /api/v1/engine/mpi/patients + POST /api/v1/engine/context/snapshots",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "patient-pathways",
      role: "clinical-user",
      path: "/pathway/patients",
      frontdeskAction: "临床用户前台为患者办理入径并回读患者路径",
      serviceOperation: "POST /api/v1/engine/pathway/patient-pathways/enter",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "cdss-fatigue",
      role: "clinical-user",
      path: "/cdss/fatigue",
      frontdeskAction: "临床用户前台触发报告解读推荐并查看提醒卡",
      serviceOperation: "POST /api/v1/engine/recommendations:evaluate",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "workflow-todos",
      role: "clinical-user",
      path: "/workflow/todos",
      frontdeskAction: "临床用户前台完成本轮报告解读协同待办",
      serviceOperation: "POST /api/v1/engine/workflow/todos/{todoId}/complete",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "clinical-followup",
      role: "clinical-user",
      path: "/clinical/followup",
      frontdeskAction: "临床用户前台生成随访计划并回读随访任务",
      serviceOperation: "POST /api/v1/engine/followup/plans/generate",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
  ],
  scenarioConditionEvidence: [
    {
      code: "S11__NORMAL",
      scenarioCode: "S11",
      condition: "NORMAL",
      source: "CLINICAL_WORKFLOW_TODO_COMPLETION",
      evidence: [
        "临床用户从真实前台完成人工协同待办",
        "服务回读待办已完成",
        "协同任务完成动作写入审计",
      ],
    },
  ],
};

const qualityManagementEntryCoreActionsEvidence = {
  matrixCode: "QUALITY_MANAGEMENT_ENTRY_CORE_ACTIONS",
  scopeStatement:
    "质量管理入口核心动作代表矩阵：围绕质量风险概览、质量问题与整改、医保审核和评价指标四个入口完成真实前台核心动作、服务回读与审计或来源对象审计链证据；不代表质量管理 4 个入口全部完整上线，不代表完整 DRG/DIP 或医保支付审核，不代表完整 S9-S11，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  evaluationAssetSupplyChainEvidence: {
    assetType: "EVALUATION",
    assetIdentity: "QC.MATRIX.CLAIM.EVAL",
    versionId: "av-evaluation-claim",
    indicatorId: "ei-evaluation-claim",
    indicatorPublished: true,
    indicatorActivated: true,
    runtimeActivationVerified: true,
    runtimeConsumerReadbackVerified: true,
    insuranceAuditEvaluationRunVerified: true,
    findingBoundToIndicatorVerified: true,
    auditVerified: true,
    activationRequest: {
      activeAssets: [
        {
          assetType: "EVALUATION",
          assetIdentity: "QC.MATRIX.CLAIM.EVAL",
          versionId: "av-evaluation-claim",
        },
      ],
    },
    runtimeReadback: {
      releaseId: "runtime-evaluation-claim",
      revisionNo: 17,
      manifestSha256: "e".repeat(64),
      assets: [
        {
          assetType: "EVALUATION",
          assetIdentity: "QC.MATRIX.CLAIM.EVAL",
          versionId: "av-evaluation-claim",
          entryState: "ACTIVE",
        },
      ],
    },
    runtimeConsumer: {
      contractVersion: "v1",
      releaseId: "runtime-evaluation-claim",
      revisionNo: 17,
      manifestSha256: "e".repeat(64),
      assets: [
        {
          assetType: "EVALUATION",
          assetIdentity: "QC.MATRIX.CLAIM.EVAL",
          versionId: "av-evaluation-claim",
          entryState: "ACTIVE",
        },
      ],
    },
  },
  rollbackNegativeEvidence: rollbackNegativeEvidence(
    [
      {
        assetType: "EVALUATION",
        assetIdentity: "QC.MATRIX.CLAIM.EVAL",
        versionId: "av-evaluation-claim",
      },
    ],
    "QUALITY_MANAGEMENT_EVALUATION_INDICATOR",
  ),
  entryActions: [
    {
      menuKey: "qc-eval-sets",
      role: "engine-operator",
      path: "/qc/eval/sets",
      frontdeskAction: "医疗引擎运营员前台创建、提交、发布、灰度并激活 CLAIM 评价指标",
      serviceOperation:
        "POST /api/v1/engine/evaluation/indicators + POST /api/v1/engine/evaluation/indicators/{indicatorId}/activate",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "insurance-audit",
      role: "engine-operator",
      path: "/qc/insurance",
      frontdeskAction: "医疗引擎运营员前台选择真实病案快照并执行医保审核派整改",
      serviceOperation:
        "POST /api/v1/engine/quality/case-review + POST /api/v1/engine/quality/drg-grouping + POST /api/v1/engine/quality/insurance-audit",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "qc-alerts",
      role: "engine-operator",
      path: "/qc/alerts",
      frontdeskAction: "医疗引擎运营员前台提交整改证据并复核关闭质量问题",
      serviceOperation:
        "POST /api/v1/engine/rectifications/{taskId}/submit + POST /api/v1/engine/rectifications/{taskId}/review",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
    },
    {
      menuKey: "qc-dashboard",
      role: "engine-operator",
      path: "/qc/dashboard",
      frontdeskAction: "医疗引擎运营员前台查看质量风险概览并下钻本轮问题证据",
      serviceOperation:
        "GET /api/v1/engine/quality/dashboard + GET /api/v1/engine/quality/dashboard/drilldown",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      sourceAuditVerified: true,
    },
  ],
  scenarioConditionEvidence: [
    {
      code: "S10__NORMAL",
      scenarioCode: "S10",
      condition: "NORMAL",
      source: "INSURANCE_AUDIT_SERVICE_READBACK",
      evidence: [
        "医保审核真实前台执行病案质控、DRG 分组和医保审核",
        "服务回读命中问题并派发整改",
        "医保审核生成的质量问题写入审计",
      ],
    },
    {
      code: "S11__NORMAL",
      scenarioCode: "S11",
      condition: "NORMAL",
      source: "QUALITY_ALERT_RECTIFICATION_REVIEW",
      evidence: [
        "质量问题整改真实前台提交整改证据",
        "整改复核服务关闭本轮质量问题",
        "质量整改闭环写入审计",
      ],
    },
  ],
};

const requiredMenuEntryCoreActionRows = [
  "workbench",
  "tenant-onboarding",
  "admin-users",
  "identity-bindings",
  "admin-audit",
  "security-baseline",
  "implementation-guide",
  "adapter-hub",
  "system-providers",
  "runtime-diagnostics",
  "domestic-check",
  "notifications",
  "notification-settings",
  "knowledge-governance",
  "runtime-releases",
  "institution-knowledge",
  "diagnosis-knowledge",
  "terminology-mapping",
  "rule-definitions",
  "pathway-templates",
  "provenance",
  "graph-explore",
  "knowledge-production",
  "ai-workflows",
  "clinical-followup",
  "sandbox",
  "qc-dashboard",
  "qc-alerts",
  "insurance-audit",
  "qc-eval-sets",
  "mpi",
  "patient-pathways",
  "cdss-fatigue",
  "workflow-todos",
];

const knowledgeOperationsAssetEntryCoreActionsEvidence = {
  matrixCode: "KNOWLEDGE_OPERATIONS_ASSET_ENTRY_CORE_ACTIONS",
  scopeStatement:
    "知识运营资产入口族供给链代表矩阵：围绕知识生产、知识审核发布中心、机构生效版本、术语字典、临床规则、临床路径库、机构知识库、诊断知识库、来源与血缘、知识关系和模型能力与安全十一个入口完成真实前台核心动作、服务回读、运行生效、回滚读回与只读边界证据；不代表全知识供给链完整上线，不代表 13 类医学资产全部生产闭环，不代表所有医学知识和术语体系已收集完成，不代表完整 S0-S40，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
  formalChain: {
    officialProductionInside134: true,
    externalSourcesPreparatoryOnly: true,
    modelDirectPublishBlocked: true,
  },
  assetTypesCovered: runtimeReleaseVersionedAssets,
  supplyChainGates: {
    standardPackageImportVerified: true,
    hospitalDictionarySyncVerified: true,
    declarativeMaintenanceVerified: true,
    humanReviewVerified: true,
    institutionEffectiveRuntimeVerified: true,
    runtimeConsumerReadbackVerified: true,
    rollbackReadbackVerified: true,
  },
  knowledgeSupplyChainEvidence: {
    sourceControl: {
      sourceRegistered: true,
      sourceVersionRegistered: true,
      sourceFragmentRegistered: true,
      uploadParseJobSucceeded: true,
      parseResultSourceVersionId: 9001,
      parsedFragmentCount: 2,
      sourceFragmentIds: [1001, 1002],
      citationBound: true,
      textExcerptVerified: true,
      qualityGateRecordCreated: true,
    },
    humanGovernance: {
      reviewQueueRead: true,
      candidateApproved: true,
      noDirectPublishVerified: true,
    },
    terminologySync: {
      standardTermRegistered: true,
      localTermRegistered: true,
      candidateGenerated: true,
      mappingConfirmed: true,
      terminologyAssetVersionCreated: true,
    },
    runtimeLifecycle: {
      baselineAssetsPreserved: true,
      hospitalRuntimeActivated: true,
      runtimeConsumerReadbackVerified: true,
      rollbackReadbackVerified: true,
    },
    lineageConsumers: {
      provenanceReadbackVerified: true,
      graphProjectionVerified: true,
      sourceAuditVerified: true,
    },
    safetyBoundary: {
      externalSourcesPreparatoryOnly: true,
      modelDirectPublishBlocked: true,
      noAutoClinicalAction: true,
    },
  },
  entryActions: [
    {
      menuKey: "knowledge-production",
      role: "engine-operator",
      path: "/knowledge/production",
      frontdeskAction: "医疗引擎运营员前台登记受控来源并生成带来源血缘的知识候选",
      serviceOperation:
        "POST /api/v1/engine/knowledge/documents:upload-parse + POST /api/v1/engine/knowledge-production/generate",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      sourceLineageVerified: true,
    },
    {
      menuKey: "terminology-mapping",
      role: "engine-operator",
      path: "/terminology/mapping",
      frontdeskAction: "医疗引擎运营员前台登记标准术语、同步院内术语并确认映射候选",
      serviceOperation:
        "POST /api/v1/engine/terminology/terms/standard + POST /api/v1/engine/terminology/mappings/candidates/confirm",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      localDictionarySyncVerified: true,
      assetVersionVerified: true,
    },
    {
      menuKey: "knowledge-governance",
      role: "engine-operator",
      path: "/knowledge/governance",
      frontdeskAction: "医疗引擎运营员前台审核受控候选并发布平台标准知识版本",
      serviceOperation:
        "POST /api/v1/engine/knowledge/candidates/{candidateId}/review + GET /api/v1/engine/knowledge/review-queue",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      humanReviewVerified: true,
      noDirectPublishVerified: true,
    },
    {
      menuKey: "rule-definitions",
      role: "engine-operator",
      path: "/rule/definitions",
      frontdeskAction: "医疗引擎运营员前台声明式维护临床规则并完成试运行证据",
      serviceOperation:
        "POST /api/v1/engine/rule/rules + POST /api/v1/engine/rule/rules/{ruleId}/simulate",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      declarativeMaintenanceVerified: true,
    },
    {
      menuKey: "pathway-templates",
      role: "engine-operator",
      path: "/pathway/templates",
      frontdeskAction: "医疗引擎运营员前台声明式维护临床路径并完成草稿试运行",
      serviceOperation:
        "POST /api/v1/engine/pathway/pathway-templates + POST /api/v1/engine/pathway/pathway-templates/{templateId}/simulate",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      declarativeMaintenanceVerified: true,
    },
    {
      menuKey: "diagnosis-knowledge",
      role: "engine-operator",
      path: "/knowledge/diagnosis",
      frontdeskAction: "医疗引擎运营员前台创建证据完整诊断资产并登记标准和验证病例",
      serviceOperation:
        "POST /api/v1/engine/knowledge/diagnosis/assets + POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/criteria + POST /api/v1/engine/knowledge/diagnosis/versions/{versionId}/test-cases",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      humanReviewVerified: true,
      sourceEvidenceVerified: true,
    },
    {
      menuKey: "runtime-releases",
      role: "engine-operator",
      path: "/config/releases",
      frontdeskAction: "医疗引擎运营员前台发布机构生效版本并从历史版本回滚读回",
      serviceOperation:
        "POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases + POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases:rollback",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      runtimeActivationVerified: true,
      runtimeConsumerReadbackVerified: true,
      rollbackReadbackVerified: true,
    },
    {
      menuKey: "institution-knowledge",
      role: "engine-operator",
      path: "/knowledge/institution",
      frontdeskAction: "医疗引擎运营员前台派生机构知识版本并恢复平台标准",
      serviceOperation:
        "POST /api/v1/engine/knowledge/customizations + POST /api/v1/engine/knowledge/customizations/{customizationId}:restore-platform",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      institutionScopeVerified: true,
      platformRestoreVerified: true,
    },
    {
      menuKey: "provenance",
      role: "engine-operator",
      path: "/advanced/provenance",
      frontdeskAction: "医疗引擎运营员前台查看本轮知识版本来源血缘和原文锚点",
      serviceOperation: "GET /api/v1/engine/knowledge/identities/{identityId}/provenance",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      sourceAuditVerified: true,
      sourceLineageVerified: true,
    },
    {
      menuKey: "graph-explore",
      role: "engine-operator",
      path: "/advanced/graph",
      frontdeskAction: "医疗引擎运营员前台重建知识关系投影并查询来源追踪证据",
      serviceOperation:
        "POST /api/v1/projections/knowledge-graph/rebuild + GET /api/v1/projections/knowledge-graph/facts",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      graphProjectionVerified: true,
      sourceLineageVerified: true,
    },
    {
      menuKey: "ai-workflows",
      role: "engine-operator",
      path: "/advanced/ai-workflows",
      frontdeskAction: "医疗引擎运营员前台核查模型能力、安全边界和无模型诚实降级",
      serviceOperation: "GET /api/v1/engine/knowledge-production/readiness",
      serviceStatus: 200,
      readbackVerified: true,
      auditVerified: true,
      modelSafetyBoundaryVerified: true,
      noDirectPublishVerified: true,
    },
  ],
};

function platformAdminEntryCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/platform-admin-entry-core-actions-rehearsal.spec.ts",
        title: "平台管理员 P0 入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "platform-admin-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoPlatformAdminEntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = platformAdminEntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.platformAdminEntryCoreActions).toBeUndefined();
}

function platformAdminP1EntryCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/platform-admin-p1-entry-core-actions-rehearsal.spec.ts",
        title: "平台管理员 P1 系统运维入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "platform-admin-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function platformAdminP1SystemOperationsEvidenceResult(options?: {
  matrixBody?: Record<string, unknown>;
  operationsBody?: Record<string, unknown>;
  title?: string;
}) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/platform-admin-p1-entry-core-actions-rehearsal.spec.ts",
        title: options?.title ?? "平台管理员 P1 系统运维入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "platform-admin-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(options?.matrixBody ?? platformAdminP1EntryCoreActionsEvidence),
          },
          {
            name: "platform-admin-p1-system-operations-codes",
            contentType: "application/json",
            body: JSON.stringify(
              options?.operationsBody ?? platformAdminP1SystemOperationsEvidence,
            ),
          },
        ],
      },
    ],
  });
}

function expectNoPlatformAdminP1EntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = platformAdminP1EntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.platformAdminP1EntryCoreActions).toBeUndefined();
}

function clinicalEntryCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/clinical-entry-core-actions-rehearsal.spec.ts",
        title: "临床协同入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "clinical-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoClinicalEntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = clinicalEntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.clinicalEntryCoreActions).toBeUndefined();
}

function qualityManagementEntryCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/quality-management-entry-core-actions-rehearsal.spec.ts",
        title: "质量管理入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "quality-management-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoQualityManagementEntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = qualityManagementEntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.qualityManagementEntryCoreActions).toBeUndefined();
}

function knowledgeOperationsAssetEntryCoreActionsEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/knowledge-operations-asset-entry-core-actions-rehearsal.spec.ts",
        title: "知识运营资产入口族完成真实前台供给链代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "knowledge-operations-asset-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoKnowledgeOperationsEntryCoreActionsCoverage(body: Record<string, unknown>) {
  const evidence = knowledgeOperationsAssetEntryCoreActionsEvidenceResult(body);
  expect(evidence.launchCoverage.knowledgeOperationsAssetEntryCoreActions).toBeUndefined();
}

function allMenuEntryCoreActionsEvidenceResult(
  options: {
    roleBody?: Record<string, unknown>;
    entryBody?: Record<string, unknown>;
    platformAdminBody?: Record<string, unknown>;
    platformAdminP1Body?: Record<string, unknown>;
    implementationGuideBody?: Record<string, unknown>;
    dashboardBody?: Record<string, unknown>;
    clinicalBody?: Record<string, unknown>;
    qualityBody?: Record<string, unknown>;
    knowledgeBody?: Record<string, unknown>;
  } = {},
) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/product-role-journeys.spec.ts",
        title: "desktop-1440 下全部角色工作台可完成主任务起步",
        status: "passed",
        attachments: [
          {
            name: "dashboard-workbench-core-actions-codes-desktop-1440",
            contentType: "application/json",
            body: JSON.stringify(options.dashboardBody ?? dashboardWorkbenchCoreActionsEvidence),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/four-role-core-actions-rehearsal.spec.ts",
        title: "四职责主动作均完成真实前台操作与服务回读闭环",
        status: "passed",
        attachments: [
          {
            name: "four-role-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(options.roleBody ?? fourRoleCoreActionsEvidence),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/entry-core-actions-rehearsal.spec.ts",
        title: "七个路由覆盖六类入口族完成真实前台核心动作代表闭环",
        status: "passed",
        attachments: [
          {
            name: "entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(options.entryBody ?? sixEntryCoreActionsEvidence),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/platform-admin-entry-core-actions-rehearsal.spec.ts",
        title: "平台管理员 P0 入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "platform-admin-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(
              options.platformAdminBody ?? platformAdminEntryCoreActionsEvidence,
            ),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/platform-admin-p1-entry-core-actions-rehearsal.spec.ts",
        title: "平台管理员 P1 系统运维入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "platform-admin-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(
              options.platformAdminP1Body ?? platformAdminP1EntryCoreActionsEvidence,
            ),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
        title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
        status: "passed",
        attachments: [
          {
            name: "implementation-guide-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(
              options.implementationGuideBody ?? implementationGuideEntryCoreActionsEvidence,
            ),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/clinical-entry-core-actions-rehearsal.spec.ts",
        title: "临床协同入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "clinical-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(options.clinicalBody ?? clinicalEntryCoreActionsEvidence),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/quality-management-entry-core-actions-rehearsal.spec.ts",
        title: "质量管理入口完成真实前台核心动作代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "quality-management-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(options.qualityBody ?? qualityManagementEntryCoreActionsEvidence),
          },
        ],
      },
      {
        file: "/repo/frontend/e2e/knowledge-operations-asset-entry-core-actions-rehearsal.spec.ts",
        title: "知识运营资产入口族完成真实前台供给链代表矩阵",
        status: "passed",
        attachments: [
          {
            name: "knowledge-operations-asset-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify(
              options.knowledgeBody ?? knowledgeOperationsAssetEntryCoreActionsEvidence,
            ),
          },
        ],
      },
    ],
  });
}

function pathwayLifecycleEvidence(overrides: Record<string, unknown> = {}) {
  return {
    scenarioCodes: ["S6"],
    productLayers: ["CLINICAL_EXECUTION"],
    versionedAssets: ["PATHWAY", "ORDER_SET"],
    serviceCombinations: ["SPECIAL_DISEASE_PATHWAY"],
    specialDiseaseStages: [
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
    ],
    apiEvidence: {
      templateSaved: true,
      templateReadback: true,
      draftPreviewRun: true,
      templateSimulated: true,
      entryCandidatesRead: true,
      patientEntered: true,
      standardAdvanced: true,
      orderSetRuntimeConsumed: true,
      varianceRecorded: true,
      followupHandoffCreated: true,
      clocksRead: true,
      variancesRead: true,
      followupHandoffObserved: true,
    },
    orderSetRuntimeConsumer: {
      asset: {
        assetType: "ORDER_SET",
        assetIdentity: "ORDER_SET.S6.COPD.RECHECK",
        versionId: "av-order-set-s6",
        versionNo: "V1",
        contentHash: "a".repeat(64),
      },
      runtimeRelease: {
        releaseId: "runtime-s6",
        assetPresent: true,
        assets: [
          {
            assetType: "ORDER_SET",
            assetIdentity: "ORDER_SET.S6.COPD.RECHECK",
            versionId: "av-order-set-s6",
          },
        ],
      },
      patientPathway: {
        patientPathwayId: "pp-s6-copd",
        runtimeReleaseId: "runtime-s6",
      },
      advanceResponse: {
        previousNodeCode: "ASSESS",
        nextNodeCode: "FOLLOWUP",
        status: "NODE_EXECUTING",
        decisionEvidence: {
          "pathway.currentNodeType": "ORDER_SET",
          "pathway.orderSetRef": "ORDER_SET.S6.COPD.RECHECK",
          "pathway.orderSetVersion": "V1",
          "pathway.orderSetHash": "a".repeat(64),
          "pathway.orderSetRequiresPhysicianConfirmation": true,
          "pathway.orderSetItemCount": 1,
          "pathway.orderSetItems": [
            {
              itemType: "LAB",
              codeSystem: "LOCAL-E2E",
              code: "COPD-ABG",
              display: "血气分析复查",
              required: true,
            },
          ],
        },
      },
    },
    scenarioConditionEvidence: [
      {
        code: "S6__NORMAL",
        scenarioCode: "S6",
        condition: "NORMAL",
        source: "SPECIAL_DISEASE_PATHWAY_ORDER_SET_RUNTIME_CONSUMPTION",
        evidence: [
          "专病路径正常主链路消费当前机构生效 ORDER_SET",
          "推进到医嘱套餐节点仅生成需医师确认的建议，不自动开嘱",
        ],
      },
    ],
    context: {
      templateCode: "PATHWAY.S6.COPD",
      patientPathwayId: "pp-s6-copd",
      orderSetRuntimeConsumer: {
        patientPathway: {
          patientPathwayId: "pp-s6-copd",
          runtimeReleaseId: "runtime-s6",
        },
      },
    },
    dedicatedReleaseContractEvidence: {
      assetType: "PATHWAY",
      assetIdentity: "PATHWAY.S6.COPD",
      versionId: "av-pathway-s6",
      productionRoute: "SPECIAL_DISEASE_PATHWAY_TEMPLATE_LIFECYCLE",
      releaseContract: "SPECIAL_DISEASE_PATHWAY_ENTRY_AND_ADVANCE_CONTRACT",
      templateLifecycleVerified: true,
      activationVerified: true,
      runtimeConsumerReadbackVerified: true,
      pathwayEntryVerified: true,
      pathwayAdvanceVerified: true,
      orderSetConsumerVerified: true,
      consumer: "SPECIAL_DISEASE_PATHWAY",
    },
    rollbackNegativeEvidence: rollbackNegativeEvidence(
      [
        {
          assetType: "PATHWAY",
          assetIdentity: "PATHWAY.S6.COPD",
          versionId: "av-pathway-s6",
        },
        {
          assetType: "ORDER_SET",
          assetIdentity: "ORDER_SET.S6.COPD.RECHECK",
          versionId: "av-order-set-s6",
        },
      ],
      "SPECIAL_DISEASE_PATHWAY_ORDER_SET",
    ),
    scenarioEvidence: [
      {
        code: "S6",
        observedStages: [
          "前台创建专病路径草稿并保存节点边时钟",
          "后端回读路径节点边时钟与十阶段里程碑",
          "前台使用真实 ACTIVE 快照完成草稿试运行",
          "真实服务链路对已保存路径执行仿真",
          "临床用户基于当前机构生效版本读取入径候选",
          "临床用户办理患者入径并生成首个关键时钟",
          "临床用户完成当前节点并标准推进",
          "临床用户推进到医嘱套餐节点并消费当前机构生效版本 ORDER_SET",
          "真实后端登记路径变异与处置决策",
          "真实后端完成随访接续终点节点",
          "后端回读关键时钟和变异事实",
          "路径完成后生成随访接续证据",
        ],
      },
    ],
    ...overrides,
  };
}

const requiredSpecialDiseaseStages = pathwayLifecycleEvidence().specialDiseaseStages as string[];

function pathwayLifecycleEvidenceResult(body: Record<string, unknown>) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: [
      {
        file: "/repo/frontend/e2e/pathway-lifecycle-frontdesk.spec.ts",
        title: "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
        status: "passed",
        attachments: [
          {
            name: "pathway-lifecycle-scenario-codes",
            contentType: "application/json",
            body: JSON.stringify(body),
          },
        ],
      },
    ],
  });
}

function expectNoPathwayLifecycleScenarioConditionCoverage(body: Record<string, unknown>) {
  const evidence = pathwayLifecycleEvidenceResult(body);
  expect(
    evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
  ).not.toContain("S6__NORMAL");
}

function versionedAssetSupplyChainMatrixTests(
  options: {
    omitFiles?: string[];
    bodyOverrides?: Record<string, Record<string, unknown>>;
  } = {},
) {
  const omitFiles = new Set(options.omitFiles ?? []);
  const definitions = [
    {
      file: "runtime-release-frontdesk.spec.ts",
      title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
      attachmentName: "runtime-release-coverage-codes",
      body: runtimeReleaseCompleteEvidence(),
    },
    {
      file: "knowledge-operations-asset-entry-core-actions-rehearsal.spec.ts",
      title: "知识运营资产入口族完成真实前台供给链代表矩阵",
      attachmentName: "knowledge-operations-asset-entry-core-actions-codes",
      body: knowledgeOperationsAssetEntryCoreActionsEvidence,
    },
    {
      file: "s2-s4-terminology-integration-rehearsal.spec.ts",
      title: "平台管理员完成系统接入且运营员完成术语映射后真实入站消息按当前机构生效版本归一",
      attachmentName: "s2-s4-runtime-mapping-codes",
      body: s2s4RuntimeMappingEvidence(),
    },
    {
      file: "cdss-runtime-declarative-assets.spec.ts",
      title: "临床用户从真实前台触发 CDSS 推荐并消费当前机构生效版本声明式运行资产",
      attachmentName: "cdss-runtime-declarative-assets-codes",
      body: cdssRuntimeDeclarativeAssets,
    },
    {
      file: "medication-safety-frontdesk.spec.ts",
      title: "临床用户与运营员围绕药物过敏红线完成当前机构生效版本推荐与人工确认闭环",
      attachmentName: "medication-safety-frontdesk-codes",
      body: medicationSafetyFrontdeskEvidence,
    },
    {
      file: "diagnostic-critical-value-frontdesk.spec.ts",
      title: "临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环",
      attachmentName: "diagnostic-critical-value-frontdesk-codes",
      body: diagnosticCriticalValueEvidence,
    },
    {
      file: "nursing-continuity-frontdesk.spec.ts",
      title: "临床用户围绕护理高风险评估完成随访计划、异常回院与结果回流闭环",
      attachmentName: "nursing-continuity-frontdesk-codes",
      body: nursingContinuityEvidence,
    },
    {
      file: "critical-emergency-icu-frontdesk.spec.ts",
      title: "临床用户与运营员、平台管理员完成急诊分诊与 ICU 生命支持风险代表闭环",
      attachmentName: "critical-emergency-icu-frontdesk-codes",
      body: criticalEmergencyIcuEvidence,
    },
    {
      file: "pathway-lifecycle-frontdesk.spec.ts",
      title: "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
      attachmentName: "pathway-lifecycle-scenario-codes",
      body: pathwayLifecycleEvidence(),
    },
    {
      file: "quality-management-entry-core-actions-rehearsal.spec.ts",
      title: "质量管理入口完成真实前台核心动作代表矩阵",
      attachmentName: "quality-management-entry-core-actions-codes",
      body: qualityManagementEntryCoreActionsEvidence,
    },
  ];
  return definitions
    .filter((definition) => !omitFiles.has(definition.file))
    .map((definition) => ({
      file: `/repo/frontend/e2e/${definition.file}`,
      title: definition.title,
      status: "passed" as const,
      attachments: [
        {
          name: definition.attachmentName,
          contentType: "application/json",
          body: JSON.stringify(options.bodyOverrides?.[definition.file] ?? definition.body),
        },
      ],
    }));
}

function platformAdminEntryCoreActionSpecFile(menuKey: string) {
  const files: Record<string, string> = {
    "tenant-onboarding": "service-organization-frontdesk.spec.ts",
    "identity-bindings": "identity-binding-frontdesk.spec.ts",
    "adapter-hub": "third-party-system-families-rehearsal.spec.ts",
    "system-providers": "system-providers-frontdesk.spec.ts",
  };
  return files[menuKey] ?? "unknown.spec.ts";
}

const standardPatientResourceMatrixScope =
  "13 类标准患者资源真实接入与消费者代表矩阵：跨真实前台演练聚合 Patient、AllergyIntolerance、Encounter、Condition、NursingAssessment、Observation、DiagnosticReport、Medication、Procedure、Document、CarePlan、FollowUp 与 Claim 的标准资源回读、运行消费者、审计和数据质量证据；不代表每类字段目录全量落地，不代表完整 S0-S40，不代表完整上线验收。";

const standardPatientResourceTypes = [
  "Patient",
  "AllergyIntolerance",
  "Encounter",
  "Condition",
  "NursingAssessment",
  "Observation",
  "DiagnosticReport",
  "Medication",
  "Procedure",
  "Document",
  "CarePlan",
  "FollowUp",
  "Claim",
] as const;

type StandardPatientResourceType = (typeof standardPatientResourceTypes)[number];

const standardPatientResourcePathByType: Record<StandardPatientResourceType, string> = {
  Patient: "clinicalContext.resources.patient",
  AllergyIntolerance: "clinicalContext.resources.allergyIntolerances[0]",
  Encounter: "clinicalContext.resources.encounters[0]",
  Condition: "clinicalContext.resources.conditions[0]",
  NursingAssessment: "clinicalContext.resources.nursingAssessments[0]",
  Observation: "clinicalContext.resources.observations[0]",
  DiagnosticReport: "clinicalContext.resources.diagnosticReports[0]",
  Medication: "clinicalContext.resources.medications[0]",
  Procedure: "clinicalContext.resources.procedures[0]",
  Document: "clinicalContext.resources.documents[0]",
  CarePlan: "clinicalContext.resources.carePlans[0]",
  FollowUp: "backflowContext.resources.followUps[0]",
  Claim: "clinicalContext.resources.claims[0]",
};

const standardPatientResourceSourceIdByType: Record<StandardPatientResourceType, string> = {
  Patient: "mpi-resource-matrix",
  AllergyIntolerance: "allergy-resource-matrix",
  Encounter: "enc-resource-matrix",
  Condition: "cond-resource-matrix",
  NursingAssessment: "nursing-resource-matrix",
  Observation: "obs-resource-matrix",
  DiagnosticReport: "report-resource-matrix",
  Medication: "med-resource-matrix",
  Procedure: "proc-resource-matrix",
  Document: "doc-resource-matrix",
  CarePlan: "care-resource-matrix",
  FollowUp: "follow-resource-matrix",
  Claim: "claim-resource-matrix",
};

function standardPatientResourceSourceSystem(type: StandardPatientResourceType) {
  if (type === "DiagnosticReport" || type === "Observation") return "FHIR_R4";
  if (type === "FollowUp") return "FOLLOWUP";
  return "MEDKERNEL_FRONTDESK";
}

function standardPatientResourceObject(type: StandardPatientResourceType) {
  const sourceRecordId = standardPatientResourceSourceIdByType[type];
  const resourceShape: Record<StandardPatientResourceType, Record<string, unknown>> = {
    Patient: { mpi: sourceRecordId, name: "脱敏患者" },
    AllergyIntolerance: { allergyIntoleranceId: sourceRecordId, code: "J01C" },
    Encounter: { encounterId: sourceRecordId, encounterType: "OUTPATIENT" },
    Condition: { conditionId: sourceRecordId, code: "A41.9" },
    NursingAssessment: { assessmentId: sourceRecordId, assessmentType: "FALL_RISK" },
    Observation: { observationId: sourceRecordId, code: "LOINC-K" },
    DiagnosticReport: { reportId: sourceRecordId, reportType: "CT" },
    Medication: { medicationId: sourceRecordId, standardCode: "J01C" },
    Procedure: { procedureId: sourceRecordId, code: "47.01" },
    Document: { documentId: sourceRecordId, documentType: "SURGERY_SAFETY_CHECKLIST" },
    CarePlan: { planId: sourceRecordId, planType: "FOLLOWUP" },
    FollowUp: { followUpId: sourceRecordId, questionnaireId: "questionnaire-resource-matrix" },
    Claim: { claimId: sourceRecordId, drgCode: "DRG-REAL-A" },
  };
  return {
    ...resourceShape[type],
    sourceSystem: standardPatientResourceSourceSystem(type),
    sourceRecordId,
    qualityStatus: "VALID",
  };
}

function standardPatientResourceBody(
  resourceTypes: readonly StandardPatientResourceType[],
  overrides: Partial<{
    scopeStatement: string;
    rows: Array<Record<string, unknown>>;
    clinicalContext: Record<string, unknown>;
    consumerEvidence: Record<string, unknown>;
    auditEvidence: Record<string, unknown>;
    insuranceAudit: Record<string, unknown>;
    qualityRectification: Record<string, unknown>;
    followupPlanGenerationExplanation: Record<string, unknown>;
  }> = {},
) {
  const resources = {
    patient: standardPatientResourceObject("Patient"),
    allergyIntolerances: [standardPatientResourceObject("AllergyIntolerance")],
    encounters: [standardPatientResourceObject("Encounter")],
    conditions: [standardPatientResourceObject("Condition")],
    nursingAssessments: [standardPatientResourceObject("NursingAssessment")],
    observations: [standardPatientResourceObject("Observation")],
    diagnosticReports: [standardPatientResourceObject("DiagnosticReport")],
    medications: [standardPatientResourceObject("Medication")],
    procedures: [standardPatientResourceObject("Procedure")],
    documents: [standardPatientResourceObject("Document")],
    carePlans: [standardPatientResourceObject("CarePlan")],
    followUps: [standardPatientResourceObject("FollowUp")],
    claims: [standardPatientResourceObject("Claim")],
  };
  const consumerEvidence = Object.fromEntries(
    standardPatientResourceTypes.map((type) => [type, { consumed: true }]),
  );
  const auditEvidence = Object.fromEntries(
    standardPatientResourceTypes.map((type) => [type, { auditId: `audit-${type}` }]),
  );
  const rows =
    overrides.rows ??
    resourceTypes.map((type) => ({
      resourceType: type,
      resourcePath: standardPatientResourcePathByType[type],
      sourceSystem: standardPatientResourceSourceSystem(type),
      sourceId: standardPatientResourceSourceIdByType[type],
      patientVerified: true,
      encounterVerified: type !== "Patient",
      snapshotReadbackVerified: true,
      consumer: type === "Claim" ? "INSURANCE_AUDIT" : "REPRESENTATIVE_RUNTIME_CONSUMER",
      consumerEvidencePaths:
        type === "Claim" ? ["insuranceAudit.evaluationRunId"] : [`consumerEvidence.${type}`],
      consumerVerified: true,
      auditEvidencePaths:
        type === "Claim"
          ? ["insuranceAudit.issueId", "qualityRectification.taskId"]
          : [`auditEvidence.${type}`],
      auditVerified: true,
      dataQualityVerified: true,
      ...(type === "Claim"
        ? { evaluationRunVerified: true, qualityRectificationVerified: true }
        : {}),
    }));
  return {
    clinicalContext: overrides.clinicalContext ?? {
      patientId: "mpi-resource-matrix",
      encounterId: "enc-resource-matrix",
      contextSnapshotId: "ctx-resource-matrix",
      resources,
    },
    backflowContext: {
      resources: {
        followUps: [standardPatientResourceObject("FollowUp")],
      },
    },
    consumerEvidence: overrides.consumerEvidence ?? consumerEvidence,
    auditEvidence: overrides.auditEvidence ?? auditEvidence,
    insuranceAudit: overrides.insuranceAudit ?? {
      issueId: "issue-resource-matrix",
      evaluationRunId: "eval-resource-matrix",
      auditStatus: "ISSUE_FOUND",
    },
    qualityRectification: overrides.qualityRectification ?? {
      taskId: "task-resource-matrix",
      taskStatus: "CLOSED",
    },
    followupPlanGenerationExplanation: overrides.followupPlanGenerationExplanation ?? {
      nursingAssessmentEvidence: [{ consumed: true }],
      carePlanEvidence: [{ consumed: true }],
    },
    standardPatientResourceConsumerMatrix: {
      matrixCode: "THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE",
      scopeStatement: overrides.scopeStatement ?? standardPatientResourceMatrixScope,
      resources: rows,
    },
  };
}

function standardPatientResourceMatrixEvidenceResult(
  bodies: Array<{
    file: string;
    title: string;
    attachmentName: string;
    body: Record<string, unknown>;
  }>,
) {
  return buildBrowserE2eLaunchEvidence({
    stats: passedStats,
    tests: bodies.map((item) => ({
      file: `/repo/frontend/e2e/${item.file}`,
      title: item.title,
      status: "passed",
      attachments: [
        {
          name: item.attachmentName,
          contentType: "application/json",
          body: JSON.stringify(item.body),
        },
      ],
    })),
  });
}

function standardPatientResourceMatrixBodies(
  overrides: Partial<
    Record<
      "medication" | "pharmacy" | "diagnostic" | "nursing" | "surgery" | "realFrontdesk",
      Record<string, unknown>
    >
  > = {},
) {
  return [
    {
      file: "medication-safety-frontdesk.spec.ts",
      title: "临床用户与运营员围绕药物过敏红线完成当前机构生效版本推荐与人工确认闭环",
      attachmentName: "medication-safety-frontdesk-codes",
      body:
        overrides.medication ??
        standardPatientResourceBody(["Patient", "AllergyIntolerance", "Encounter", "Medication"]),
    },
    {
      file: "pharmacy-review-antimicrobial-frontdesk.spec.ts",
      title:
        "临床用户按医生和药师业务任职与运营员、平台管理员完成本轮抗菌药物审方代表切片回传、推荐确认和整改闭环",
      attachmentName: "pharmacy-review-antimicrobial-frontdesk-codes",
      body: overrides.pharmacy ?? standardPatientResourceBody(["Condition", "Observation"]),
    },
    {
      file: "diagnostic-critical-value-frontdesk.spec.ts",
      title: "临床用户与医技人员围绕危急值报告完成外部入站、报告解读与人工闭环",
      attachmentName: "diagnostic-critical-value-frontdesk-codes",
      body: overrides.diagnostic ?? standardPatientResourceBody(["DiagnosticReport"]),
    },
    {
      file: "nursing-continuity-frontdesk.spec.ts",
      title: "临床用户围绕护理高风险评估完成随访计划、异常回院与结果回流闭环",
      attachmentName: "nursing-continuity-frontdesk-codes",
      body:
        overrides.nursing ??
        standardPatientResourceBody(["NursingAssessment", "CarePlan", "FollowUp"]),
    },
    {
      file: "surgery-anesthesia-transfusion-frontdesk.spec.ts",
      title: "临床用户与运营员、平台管理员完成围手术期麻醉输血核查代表闭环",
      attachmentName: "surgery-anesthesia-transfusion-frontdesk-codes",
      body: overrides.surgery ?? standardPatientResourceBody(["Procedure", "Document"]),
    },
    {
      file: "real-frontdesk-rehearsal.spec.ts",
      title:
        "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
      attachmentName: "real-frontdesk-scenario-codes",
      body: overrides.realFrontdesk ?? standardPatientResourceBody(["Claim"]),
    },
  ];
}

describe("browser E2E launch coverage evidence", () => {
  it("declares stakeholder views only when the real stakeholder rehearsal spec passes", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
          title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
          status: "passed",
        },
      ],
    });

    expect(evidence.stats).toEqual(passedStats);
    expect(evidence.launchCoverage.stakeholderViews?.map((item) => item.code)).toEqual([
      "PHYSICIAN",
      "NURSE",
      "PHARMACIST",
      "MEDICAL_TECHNICIAN",
      "QUALITY_CONTROLLER",
      "PATIENT_PROXY",
      "PLATFORM_ADMIN",
      "ENGINE_OPERATOR",
      "AUDITOR",
      "IT_MANAGER",
      "IMPLEMENTATION_ENGINEER",
      "HOSPITAL_EXECUTIVE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares launch readiness stakeholder matrix only from clean runtime records for IT, implementation, and executive views", () => {
    const evidence = stakeholderReadinessEvidence();

    expect(
      evidence.launchCoverage.launchReadinessStakeholderMatrix?.map((item) => item.code),
    ).toEqual(["IT_IMPLEMENTATION_EXECUTIVE_READINESS_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.launchReadinessStakeholderRows?.map((item) => item.code),
    ).toEqual([
      "IT_MANAGER_RUNTIME_DIAGNOSTICS",
      "IMPLEMENTATION_ENGINEER_ONBOARDING_GUIDE",
      "HOSPITAL_EXECUTIVE_QUALITY_OVERVIEW",
    ]);
  });

  it.each([
    {
      name: "缺少实施工程师视角",
      records: launchReadinessStakeholderRecords.filter(
        (item) => item.code !== "IMPLEMENTATION_ENGINEER",
      ),
    },
    {
      name: "信息科长没有真实动作",
      records: launchReadinessStakeholderRecords.map((item) =>
        item.code === "IT_MANAGER" ? { ...item, actions: [] } : item,
      ),
    },
    {
      name: "院长视角出现服务端错误",
      records: launchReadinessStakeholderRecords.map((item) =>
        item.code === "HOSPITAL_EXECUTIVE"
          ? { ...item, serverErrors: ["GET /api/v1/engine/quality/dashboard 500"] }
          : item,
      ),
    },
    {
      name: "实施工程师路径不匹配",
      records: launchReadinessStakeholderRecords.map((item) =>
        item.code === "IMPLEMENTATION_ENGINEER" ? { ...item, path: "/adapter/hub" } : item,
      ),
    },
    {
      name: "信息科长不是平台管理员",
      records: launchReadinessStakeholderRecords.map((item) =>
        item.code === "IT_MANAGER" ? { ...item, role: "engine-operator" } : item,
      ),
    },
    {
      name: "信息科长动作没有覆盖运行诊断",
      records: launchReadinessStakeholderRecords.map((item) =>
        item.code === "IT_MANAGER" ? { ...item, actions: ["生成系统接入数据质量报告"] } : item,
      ),
    },
  ])("does not declare launch readiness stakeholder matrix when $name", ({ records }) => {
    const evidence = stakeholderReadinessEvidence(records);

    expect(evidence.launchCoverage.launchReadinessStakeholderMatrix).toBeUndefined();
    expect(evidence.launchCoverage.launchReadinessStakeholderRows).toBeUndefined();
  });

  it("does not declare launch readiness stakeholder matrix from the same attachment in a non-target spec", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-stakeholder-view.spec.ts",
          title: "三视角附件不能由非目标 spec 冒领",
          status: "passed",
          attachments: [
            {
              name: "stakeholder-view-runtime-records",
              contentType: "application/json",
              body: JSON.stringify(launchReadinessStakeholderRecords),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.launchReadinessStakeholderMatrix).toBeUndefined();
    expect(evidence.launchCoverage.launchReadinessStakeholderRows).toBeUndefined();
  });

  it("declares implementation guide entry core actions only from structured service evidence", () => {
    const evidence = implementationGuideEntryCoreActionsEvidenceResult();

    expect(
      evidence.launchCoverage.implementationGuideEntryCoreActions?.map((item) => item.code),
    ).toEqual(["IMPLEMENTATION_GUIDE_SERVICE_READINESS_ACTIONS"]);
    expect(
      evidence.launchCoverage.implementationGuideEntryCoreActionRows?.map((item) => item.code),
    ).toEqual(["IMPLEMENTATION_ENGINEER_READINESS_AND_DATA_QUALITY"]);
  });

  it.each([
    {
      name: "没有边界声明",
      body: { ...implementationGuideEntryCoreActionsEvidence, scopeStatement: "实施与验收完成" },
    },
    {
      name: "缺少实施步骤回读",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          implementationStepsReadbackVerified: false,
        })),
      },
    },
    {
      name: "缺少开通就绪回读",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          onboardingReadinessReadbackVerified: false,
        })),
      },
    },
    {
      name: "缺少数据质量报告",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          dataQualityReportVerified: false,
        })),
      },
    },
    {
      name: "服务操作缺少实施步骤接口",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          serviceOperation:
            "GET /api/v1/engine/tenant/onboarding-readiness + POST /api/v1/engine/integration/data-quality/reports",
        })),
      },
    },
    {
      name: "服务状态不是 2xx",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          serviceStatus: 500,
        })),
      },
    },
    {
      name: "没有审计或服务证据",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          auditVerified: false,
        })),
      },
    },
    {
      name: "角色不是平台管理员",
      body: {
        ...implementationGuideEntryCoreActionsEvidence,
        entryActions: implementationGuideEntryCoreActionsEvidence.entryActions.map((action) => ({
          ...action,
          role: "engine-operator",
        })),
      },
    },
  ])("does not declare implementation guide entry core actions when $name", ({ body }) => {
    expectNoImplementationGuideEntryCoreActionsCoverage(body);
  });

  it("does not declare implementation guide entry core actions from a non-target spec", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-onboarding-guide.spec.ts",
          title: "实施与验收附件不能由非目标 spec 冒领",
          status: "passed",
          attachments: [
            {
              name: "implementation-guide-entry-core-actions-codes",
              contentType: "application/json",
              body: JSON.stringify(implementationGuideEntryCoreActionsEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.implementationGuideEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.implementationGuideEntryCoreActionRows).toBeUndefined();
  });

  it("declares dashboard workbench core actions only from four-role structured service evidence", () => {
    const evidence = dashboardWorkbenchCoreActionsEvidenceResult();

    expect(evidence.launchCoverage.dashboardWorkbenchCoreActions?.map((item) => item.code)).toEqual(
      ["FOUR_ROLE_DASHBOARD_WORKBENCH_CORE_ACTIONS"],
    );
    expect(
      evidence.launchCoverage.dashboardWorkbenchCoreActionRows?.map((item) => item.code),
    ).toEqual(["PLATFORM_ADMIN", "ENGINE_OPERATOR", "CLINICAL_USER", "AUDITOR"]);
  });

  it.each([
    {
      name: "缺少边界声明",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        scopeStatement: "四职责工作台完整上线验收已完成",
      },
    },
    {
      name: "缺少审计员职责行",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.filter(
          (action) => action.role !== "auditor",
        ),
      },
    },
    {
      name: "不是工作台路径",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "clinical-user" ? { ...action, path: "/workflow/todos" } : action,
        ),
      },
    },
    {
      name: "职责行码错配",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "auditor" ? { ...action, row: "AUDIT_VIEW_ONLY" } : action,
        ),
      },
    },
    {
      name: "缺少真实服务来源",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "platform-admin"
            ? { ...action, serviceOperation: "GET /api/v1/security/me" }
            : action,
        ),
      },
    },
    {
      name: "缺少工作台审计来源",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "engine-operator"
            ? {
                ...action,
                serviceOperation:
                  "GET /api/v1/security/me + GET /api/v1/large-lists/audit-events/list",
              }
            : action,
        ),
      },
    },
    {
      name: "没有验证主动作",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "engine-operator" ? { ...action, primaryActionVerified: false } : action,
        ),
      },
    },
    {
      name: "没有高频任务入口",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "auditor"
            ? { ...action, highFrequencyPaths: ["/advanced/provenance"] }
            : action,
        ),
      },
    },
    {
      name: "有服务端错误",
      body: {
        ...dashboardWorkbenchCoreActionsEvidence,
        roleActions: dashboardWorkbenchCoreActionsEvidence.roleActions.map((action) =>
          action.role === "clinical-user" ? { ...action, noServerErrors: false } : action,
        ),
      },
    },
  ])("does not declare dashboard workbench core actions when $name", ({ body }) => {
    expectNoDashboardWorkbenchCoreActionsCoverage(body);
  });

  it("does not declare dashboard workbench core actions from a non-target spec", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-dashboard.spec.ts",
          title: "工作台附件不能由非目标 spec 冒领",
          status: "passed",
          attachments: [
            {
              name: "dashboard-workbench-core-actions-codes-desktop-1440",
              contentType: "application/json",
              body: JSON.stringify(dashboardWorkbenchCoreActionsEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.dashboardWorkbenchCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.dashboardWorkbenchCoreActionRows).toBeUndefined();
  });

  it("declares release governance and runtime asset coverage only when the real runtime release spec passes", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
          title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
          status: "passed",
          attachments: [
            {
              name: "runtime-release-coverage-codes",
              contentType: "application/json",
              body: JSON.stringify({
                productLayers: ["RELEASE_GOVERNANCE"],
                versionedAssets: runtimeReleaseVersionedAssets,
                deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
                serviceCombinations: ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"],
                apiEvidence: runtimeReleaseApiEvidence,
                localCandidate: {
                  assetType: "ACTION_CARD",
                  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                  versionId: "local-version-1",
                },
                unselectedLocalCandidate: {
                  assetType: "ACTION_CARD",
                  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.2",
                  versionId: "local-version-2",
                },
                activationRequest: {
                  activeAssets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                    },
                  ],
                },
                activationReadback: {
                  assets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                      entryState: "ACTIVE",
                    },
                  ],
                },
                runtimeConsumerReadback: {
                  assets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                      entryState: "ACTIVE",
                    },
                  ],
                },
                partialSelection: {
                  selectedCandidate: {
                    assetType: "ACTION_CARD",
                    assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                    versionId: "local-version-1",
                  },
                  unselectedCandidate: {
                    assetType: "ACTION_CARD",
                    assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.2",
                    versionId: "local-version-2",
                  },
                  activationRequestOmitsUnselected: true,
                  activationReadbackOmitsUnselected: true,
                  runtimeConsumerOmitsUnselected: true,
                },
                platformUpgradeAnalysis: runtimeReleasePlatformUpgradeAnalysis,
                multiHospitalDifferentiation: {
                  primaryHospital: {
                    hospitalId: "hospital-A",
                    hospitalName: "本地上线演练医院",
                    selectedCandidate: {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                    },
                    activationReadback: {
                      assets: [
                        {
                          assetType: "ACTION_CARD",
                          assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                          versionId: "local-version-1",
                          entryState: "ACTIVE",
                        },
                      ],
                    },
                    runtimeConsumerReadback: {
                      assets: [
                        {
                          assetType: "ACTION_CARD",
                          assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                          versionId: "local-version-1",
                          entryState: "ACTIVE",
                        },
                      ],
                    },
                    excludesOtherHospitalCandidate: true,
                  },
                  secondaryHospital: {
                    hospitalId: "hospital-B",
                    hospitalName: "本地上线演练二院",
                    selectedCandidate: {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.3",
                      versionId: "local-version-3",
                    },
                    activationReadback: {
                      assets: [
                        {
                          assetType: "ACTION_CARD",
                          assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.3",
                          versionId: "local-version-3",
                          entryState: "ACTIVE",
                        },
                      ],
                    },
                    runtimeConsumerReadback: {
                      assets: [
                        {
                          assetType: "ACTION_CARD",
                          assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.3",
                          versionId: "local-version-3",
                          entryState: "ACTIVE",
                        },
                      ],
                    },
                    excludesOtherHospitalCandidate: true,
                  },
                  distinctHospitals: true,
                  distinctSelectedCandidates: true,
                  backendReadbacksIsolated: true,
                  runtimeConsumerReadbacksIsolated: true,
                },
                offlineDelivery: runtimeReleaseOfflineDelivery,
                rollbackReadback: { localCandidateAbsent: true, assets: [] },
                rollbackRuntimeConsumerReadback: { localCandidateAbsent: true, assets: [] },
                scenarioEvidence: runtimeReleaseScenarioEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "KNOWLEDGE",
      "TERMINOLOGY",
      "RULE",
      "PATHWAY",
      "EVALUATION",
      "FOLLOWUP",
      "FIELD_CATALOG",
      "SAFETY",
      "CDSS_RISK",
      "VALUE_SET",
      "FORMULA",
      "ORDER_SET",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual([
      "MANAGEMENT_WORKSPACE",
      "API_EVENT",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "CLINICAL_RUNTIME",
      "THIRD_PARTY_INTERFACE",
    ]);
    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S13"]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares S13 normal condition row only with explicit runtime release activation, consumer, and rollback evidence", () => {
    const evidence = runtimeReleaseEvidenceResult(runtimeReleaseCompleteEvidence());

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S13__NORMAL",
    ]);
  });

  it.each([
    {
      name: "缺显式条件附件",
      overrides: { scenarioConditionEvidence: undefined },
    },
    {
      name: "未知条件行",
      overrides: {
        scenarioConditionEvidence: [
          {
            code: "S13__ABNORMAL",
            scenarioCode: "S13",
            condition: "ABNORMAL",
            source: "RUNTIME_RELEASE_ACTIVATION_ROLLBACK_CONTRACT_READBACK",
            evidence: ["未知条件不应声明"],
          },
        ],
      },
    },
    {
      name: "来源错配",
      overrides: {
        scenarioConditionEvidence: [
          {
            code: "S13__NORMAL",
            scenarioCode: "S13",
            condition: "NORMAL",
            source: "RUNTIME_RELEASE_MENU_NAVIGATION_ONLY",
            evidence: ["错误来源不应声明"],
          },
        ],
      },
    },
    {
      name: "证据文本为空",
      overrides: {
        scenarioConditionEvidence: [
          {
            code: "S13__NORMAL",
            scenarioCode: "S13",
            condition: "NORMAL",
            source: "RUNTIME_RELEASE_ACTIVATION_ROLLBACK_CONTRACT_READBACK",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "激活接口未成功",
      overrides: {
        apiEvidence: {
          ...runtimeReleaseApiEvidence,
          activationPosted: false,
        },
      },
    },
    {
      name: "当前机构版本未回读",
      overrides: {
        apiEvidence: {
          ...runtimeReleaseApiEvidence,
          currentReleaseReadback: false,
        },
      },
    },
    {
      name: "第三方运行契约未回读",
      overrides: {
        apiEvidence: {
          ...runtimeReleaseApiEvidence,
          runtimeConsumerReadback: false,
        },
      },
    },
    {
      name: "激活请求不含本轮候选",
      overrides: {
        activationRequest: { activeAssets: [] },
      },
    },
    {
      name: "机构版本回读候选未 ACTIVE",
      overrides: {
        activationReadback: {
          assets: [{ ...runtimeReleasePrimaryAsset, entryState: "DISABLED" }],
        },
      },
    },
    {
      name: "第三方运行契约缺本轮候选",
      overrides: {
        runtimeConsumerReadback: { assets: [] },
      },
    },
    {
      name: "回滚后机构版本仍含本轮候选",
      overrides: {
        rollbackReadback: { localCandidateAbsent: true, assets: [runtimeReleasePrimaryAsset] },
      },
    },
    {
      name: "回滚后第三方运行契约仍含本轮候选",
      overrides: {
        rollbackRuntimeConsumerReadback: {
          localCandidateAbsent: true,
          assets: [runtimeReleasePrimaryAsset],
        },
      },
    },
    {
      name: "未选候选未被排除",
      overrides: {
        partialSelection: {
          selectedCandidate: runtimeReleasePrimaryCandidate,
          unselectedCandidate: runtimeReleaseUnselectedCandidate,
          activationRequestOmitsUnselected: false,
          activationReadbackOmitsUnselected: true,
          runtimeConsumerOmitsUnselected: true,
        },
      },
    },
    {
      name: "资产类型清单含重复项",
      overrides: {
        versionedAssets: [...runtimeReleaseVersionedAssets, "ACTION_CARD"],
      },
    },
  ])("does not declare S13 normal condition row when $name", ({ overrides }) => {
    const evidence = runtimeReleaseEvidenceResult(runtimeReleaseCompleteEvidence(overrides));

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it("does not declare release governance coverage from a runtime release spec without complete runtime evidence", () => {
    const missingAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
          title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
          status: "passed",
        },
      ],
    });
    const incompleteAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
          title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
          status: "passed",
          attachments: [
            {
              name: "runtime-release-coverage-codes",
              contentType: "application/json",
              body: JSON.stringify({
                productLayers: ["RELEASE_GOVERNANCE"],
                versionedAssets: ["KNOWLEDGE"],
                deliveryShapes: ["MANAGEMENT_WORKSPACE"],
                serviceCombinations: ["CLINICAL_RUNTIME"],
                apiEvidence: {
                  impactSimulationRun: true,
                  activationPosted: true,
                  activationRequestCarriesRequiredAssets: false,
                  currentReleaseReadback: true,
                  runtimeConsumerReadback: true,
                  rollbackPosted: true,
                  rollbackCurrentReleaseReadback: true,
                  rollbackRuntimeConsumerReadback: false,
                },
                scenarioEvidence: [
                  {
                    observedStages: ["前台生成携带 13 类资产闭包的机构生效版本"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(missingAttachment.launchCoverage.productLayers).toBeUndefined();
    expect(missingAttachment.launchCoverage.versionedAssets).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.productLayers).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.versionedAssets).toBeUndefined();
  });

  it("does not declare release governance coverage when runtime evidence omits local candidate readbacks", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
          title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
          status: "passed",
          attachments: [
            {
              name: "runtime-release-coverage-codes",
              contentType: "application/json",
              body: JSON.stringify({
                productLayers: ["RELEASE_GOVERNANCE"],
                versionedAssets: runtimeReleaseVersionedAssets,
                deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
                serviceCombinations: ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"],
                apiEvidence: runtimeReleaseApiEvidence,
                scenarioEvidence: runtimeReleaseScenarioEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.productLayers).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssets).toBeUndefined();
  });

  it("does not declare S13 partial-selection coverage when runtime evidence omits the unselected candidate absence proof", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
          title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
          status: "passed",
          attachments: [
            {
              name: "runtime-release-coverage-codes",
              contentType: "application/json",
              body: JSON.stringify({
                productLayers: ["RELEASE_GOVERNANCE"],
                versionedAssets: runtimeReleaseVersionedAssets,
                deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
                serviceCombinations: ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"],
                apiEvidence: runtimeReleaseApiEvidence,
                localCandidate: {
                  assetType: "ACTION_CARD",
                  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                  versionId: "local-version-1",
                },
                activationRequest: {
                  activeAssets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                    },
                  ],
                },
                activationReadback: {
                  assets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                      entryState: "ACTIVE",
                    },
                  ],
                },
                runtimeConsumerReadback: {
                  assets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                      entryState: "ACTIVE",
                    },
                  ],
                },
                rollbackReadback: { localCandidateAbsent: true, assets: [] },
                rollbackRuntimeConsumerReadback: { localCandidateAbsent: true, assets: [] },
                scenarioEvidence: runtimeReleaseScenarioEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when runtime evidence lacks two-hospital differentiation", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/runtime-release-frontdesk.spec.ts",
          title: "医疗引擎运营员可为本院生成新生效版本并从历史版本回滚",
          status: "passed",
          attachments: [
            {
              name: "runtime-release-coverage-codes",
              contentType: "application/json",
              body: JSON.stringify({
                productLayers: ["RELEASE_GOVERNANCE"],
                versionedAssets: runtimeReleaseVersionedAssets,
                deliveryShapes: ["MANAGEMENT_WORKSPACE", "API_EVENT"],
                serviceCombinations: ["CLINICAL_RUNTIME", "THIRD_PARTY_INTERFACE"],
                apiEvidence: runtimeReleaseApiEvidence,
                localCandidate: {
                  assetType: "ACTION_CARD",
                  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                  versionId: "local-version-1",
                },
                unselectedLocalCandidate: {
                  assetType: "ACTION_CARD",
                  assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.2",
                  versionId: "local-version-2",
                },
                activationRequest: {
                  activeAssets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                    },
                  ],
                },
                activationReadback: {
                  assets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                      entryState: "ACTIVE",
                    },
                  ],
                },
                runtimeConsumerReadback: {
                  assets: [
                    {
                      assetType: "ACTION_CARD",
                      assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                      versionId: "local-version-1",
                      entryState: "ACTIVE",
                    },
                  ],
                },
                partialSelection: {
                  selectedCandidate: {
                    assetType: "ACTION_CARD",
                    assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.1",
                    versionId: "local-version-1",
                  },
                  unselectedCandidate: {
                    assetType: "ACTION_CARD",
                    assetIdentity: "ACTION_CARD.RUNTIME.RELEASE.2",
                    versionId: "local-version-2",
                  },
                  activationRequestOmitsUnselected: true,
                  activationReadbackOmitsUnselected: true,
                  runtimeConsumerOmitsUnselected: true,
                },
                rollbackReadback: { localCandidateAbsent: true, assets: [] },
                rollbackRuntimeConsumerReadback: { localCandidateAbsent: true, assets: [] },
                scenarioEvidence: runtimeReleaseScenarioEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when two-hospital evidence reuses one hospital", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        multiHospitalDifferentiation: {
          ...runtimeReleaseMultiHospitalDifferentiation,
          secondaryHospital: {
            ...runtimeReleaseMultiHospitalDifferentiation.secondaryHospital,
            hospitalId: "hospital-A",
          },
        },
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when hospital readbacks leak the other hospital candidate", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        multiHospitalDifferentiation: {
          ...runtimeReleaseMultiHospitalDifferentiation,
          primaryHospital: {
            ...runtimeReleaseMultiHospitalDifferentiation.primaryHospital,
            activationReadback: {
              assets: [runtimeReleasePrimaryAsset, runtimeReleaseSecondaryAsset],
            },
          },
        },
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when runtime evidence lacks offline delivery export validation", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        offlineDelivery: undefined,
        apiEvidence: {
          ...runtimeReleaseApiEvidence,
          offlineDeliveryExported: false,
          offlineDeliveryFileDownloaded: false,
          offlineDeliveryImportPreviewValidated: false,
          offlineDeliveryRuntimeUnchanged: false,
        },
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when runtime evidence lacks platform upgrade analysis", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        platformUpgradeAnalysis: undefined,
        scenarioEvidence: [
          {
            observedStages: runtimeReleaseScenarioEvidence[0].observedStages.filter(
              (stage) => stage !== "前台完成平台升级差异与冲突分析",
            ),
          },
        ],
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when platform upgrade analysis still has unresolved conflicts", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        platformUpgradeAnalysis: {
          ...runtimeReleasePlatformUpgradeAnalysis,
          diffSummary: {
            ...runtimeReleasePlatformUpgradeAnalysis.diffSummary,
            conflictCount: 1,
          },
          items: [
            {
              ...runtimeReleasePlatformUpgradeAnalysis.items[0],
              conflicts: [
                {
                  overrideId: "override-local-1",
                  orgPath: "/tenant-A/hospital-A",
                  overrideMode: "REPLACE",
                  resultingSource: "LOCAL_OVERRIDE:local-card-v1",
                },
              ],
            },
          ],
        },
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when platform upgrade analysis lacks changed diff items", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        platformUpgradeAnalysis: {
          ...runtimeReleasePlatformUpgradeAnalysis,
          diffSummary: {
            ...runtimeReleasePlatformUpgradeAnalysis.diffSummary,
            added: 1,
            modified: 0,
            disabled: 0,
          },
          items: [
            {
              assetType: "ACTION_CARD",
              assetIdentity: "ACTION_CARD.UPGRADE.UNCHANGED",
              changeType: "UNCHANGED",
              currentVersionId: "platform-card-v1",
              targetVersionId: "platform-card-v1",
              conflicts: [],
            },
          ],
        },
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when offline delivery preview mutates runtime or fails signature", () => {
    const mutatingPreview = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        offlineDelivery: {
          ...runtimeReleaseOfflineDelivery,
          importPreview: {
            ...runtimeReleaseOfflineDelivery.importPreview,
            runtimeMutation: true,
          },
        },
      }),
    );
    const invalidSignature = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        offlineDelivery: {
          ...runtimeReleaseOfflineDelivery,
          importPreview: {
            ...runtimeReleaseOfflineDelivery.importPreview,
            signatureValid: false,
          },
        },
      }),
    );

    expect(mutatingPreview.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(mutatingPreview.launchCoverage.scenarios).toBeUndefined();
    expect(invalidSignature.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(invalidSignature.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when offline delivery has no restore execution readback", () => {
    const noRestore = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        offlineDelivery: {
          ...runtimeReleaseOfflineDelivery,
          restore: undefined,
          runtimeBeforeRestore: undefined,
          runtimeAfterRestore: undefined,
          runtimeConsumerAfterRestore: undefined,
        },
        apiEvidence: {
          ...runtimeReleaseApiEvidence,
          offlineDeliveryRestoreExecuted: false,
          offlineDeliveryRestoreCreatedNewRevision: false,
          offlineDeliveryRestoreReadbackMatched: false,
          offlineDeliveryRestoreRuntimeConsumerMatched: false,
        },
      }),
    );

    expect(noRestore.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(noRestore.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when human-readable evidence omits offline restore stages", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        scenarioEvidence: [
          {
            observedStages: runtimeReleaseScenarioEvidence[0].observedStages.filter(
              (stage) =>
                stage !== "离线交付恢复执行生成新机构生效版本" &&
                stage !== "恢复后后端和第三方运行契约读取同一机构生效版本",
            ),
          },
        ],
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when offline restore reuses the source release id", () => {
    const evidence = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        offlineDelivery: {
          ...runtimeReleaseOfflineDelivery,
          restore: {
            ...runtimeReleaseOfflineDelivery.restore,
            restoredReleaseId: "runtime-H9",
          },
          runtimeAfterRestore: {
            ...runtimeReleaseOfflineDelivery.runtimeAfterRestore,
            releaseId: "runtime-H9",
          },
          runtimeConsumerAfterRestore: {
            ...runtimeReleaseOfflineDelivery.runtimeConsumerAfterRestore,
            releaseId: "runtime-H9",
          },
        },
      }),
    );

    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare S13 coverage when offline restore lacks third-party runtime readback", () => {
    const missingConsumer = runtimeReleaseEvidenceResult(
      runtimeReleaseCompleteEvidence({
        offlineDelivery: {
          ...runtimeReleaseOfflineDelivery,
          runtimeConsumerAfterRestore: {
            ...runtimeReleaseOfflineDelivery.runtimeConsumerAfterRestore,
            selectedCandidatePresent: false,
          },
        },
        apiEvidence: {
          ...runtimeReleaseApiEvidence,
          offlineDeliveryRestoreRuntimeConsumerMatched: false,
        },
      }),
    );

    expect(missingConsumer.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "RELEASE_GOVERNANCE",
    ]);
    expect(missingConsumer.launchCoverage.scenarios).toBeUndefined();
  });

  it("declares system operations coverage only when service providers rehearsal proves restore continuity", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(systemProvidersEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual([
      "MANAGEMENT_WORKSPACE",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "COMPLIANCE_OPERATIONS",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
  });

  it("declares only the system operations condition rows backed by explicit real evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(systemProvidersEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S15__NORMAL",
      "S15__DEGRADATION",
      "S14__ABNORMAL",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not auto-generate system operations condition rows from ordinary operations coverage", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify({
                ...systemProvidersEvidence,
                scenarioConditionEvidence: undefined,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual([
      "MANAGEMENT_WORKSPACE",
    ]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it("declares only backed system operations condition rows when local backup drill is still NOT_AVAILABLE", () => {
    const localNotAvailableEvidence = {
      ...systemProvidersEvidence,
      apiEvidence: {
        operationsSnapshotRead: true,
        backupReadinessObserved: true,
        honestDegradationObserved: true,
        evidenceDetailsObserved: true,
        runtimeReadbackObserved: false,
        runtimeConsumerReadbackObserved: false,
        clinicalSmokeAfterRestore: false,
        clinicalForbidden: true,
      },
      backup: {
        ...systemProvidersEvidence.backup,
        enabled: false,
        rpo: "未启用",
        rto: "未启用",
        drillEvidence: {
          status: "NOT_AVAILABLE",
          completedAt: null,
          migrationCount: null,
          evidenceReference: null,
          checksumEvidence: null,
          drillDatabaseIsIsolated: null,
          rpo: null,
          rto: null,
          detail: "尚未提供隔离恢复演练证据",
        },
      },
      runtimeContinuityEvidence: undefined,
      scenarioConditionEvidence: [
        {
          code: "S15__DEGRADATION",
          scenarioCode: "S15",
          condition: "DEGRADATION",
          source: "SYSTEM_DEPENDENCY_HONEST_DEGRADATION",
          evidence: ["外部依赖断连或不健康时前台诚实展示降级且本地确定性主链路继续可用"],
        },
        {
          code: "S14__ABNORMAL",
          scenarioCode: "S14",
          condition: "ABNORMAL",
          source: "CLINICAL_SYSTEM_OPERATIONS_FORBIDDEN",
          evidence: ["临床账号 API 读取系统运维快照返回 403，前台只展示权限不足且不展示运维数据"],
        },
      ],
    };
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(localNotAvailableEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S15__DEGRADATION",
      "S14__ABNORMAL",
    ]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).not.toContain(
      "S15__NORMAL",
    );
    expect(evidence.launchCoverage.deliveryShapes).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码不属于已证明的系统运维五态范围",
      missingCode: "S15__HIGH_RISK",
      body: {
        ...systemProvidersEvidence,
        scenarioConditionEvidence: [
          ...systemProvidersEvidence.scenarioConditionEvidence,
          {
            code: "S15__HIGH_RISK",
            scenarioCode: "S15",
            condition: "HIGH_RISK",
            source: "UNPROVEN_DESTRUCTIVE_OPERATION",
            evidence: ["没有真实高危操作门证据"],
          },
        ],
      },
    },
    {
      name: "恢复后运行消费者与当前机构生效版本不一致",
      missingCode: "S15__NORMAL",
      body: {
        ...systemProvidersEvidence,
        runtimeContinuityEvidence: {
          ...systemProvidersEvidence.runtimeContinuityEvidence,
          runtimeConsumer: {
            ...systemProvidersEvidence.runtimeContinuityEvidence.runtimeConsumer,
            releaseId: "runtime-other",
          },
        },
      },
    },
    {
      name: "缺少依赖诚实降级提示",
      missingCode: "S15__DEGRADATION",
      body: {
        ...systemProvidersEvidence,
        dependencyEvidence: {
          ...systemProvidersEvidence.dependencyEvidence,
          honestDegradationText: "",
        },
      },
    },
    {
      name: "临床账号越权访问未被拒绝",
      missingCode: "S14__ABNORMAL",
      body: {
        ...systemProvidersEvidence,
        accessEvidence: {
          ...systemProvidersEvidence.accessEvidence,
          clinicalOperationsStatus: 200,
        },
      },
    },
    {
      name: "条件行没有具体证据文本",
      missingCode: "S15__NORMAL",
      body: {
        ...systemProvidersEvidence,
        scenarioConditionEvidence: [
          {
            code: "S15__NORMAL",
            scenarioCode: "S15",
            condition: "NORMAL",
            source: "SYSTEM_OPERATIONS_RESTORE_CONTINUITY",
            evidence: [],
          },
        ],
      },
    },
  ])("does not declare system operations condition rows when $name", ({ body, missingCode }) => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(body),
            },
          ],
        },
      ],
    });

    const codes = evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [];
    expect(codes).not.toContain(missingCode);
  });

  it("does not declare system operations coverage from local NOT_AVAILABLE backup drill evidence", () => {
    const localNotAvailableEvidence = {
      ...systemProvidersEvidence,
      apiEvidence: {
        operationsSnapshotRead: true,
        backupReadinessObserved: true,
        honestDegradationObserved: true,
        evidenceDetailsObserved: true,
        runtimeReadbackObserved: false,
        runtimeConsumerReadbackObserved: false,
        clinicalSmokeAfterRestore: false,
        clinicalForbidden: true,
      },
      backup: {
        ...systemProvidersEvidence.backup,
        enabled: false,
        rpo: "未启用",
        rto: "未启用",
        drillEvidence: {
          status: "NOT_AVAILABLE",
          completedAt: null,
          migrationCount: null,
          evidenceReference: null,
          checksumEvidence: null,
          drillDatabaseIsIsolated: null,
          rpo: null,
          rto: null,
          detail: "尚未提供隔离恢复演练证据",
        },
      },
      runtimeContinuityEvidence: undefined,
      scenarioEvidence: [
        {
          observedStages: [
            "平台管理员读取真实服务运行保障快照",
            "前台展示备份恢复 RPO、RTO 与 SHA-256 校验策略",
            "前台展示依赖诚实降级并保留本地主链路提示",
            "证据详情展示部署档案、迁移路径和备份恢复诊断",
            "备份恢复隔离演练未完成，服务运行保障诚实展示待演练状态",
            "临床账号无法读取或展示服务运行保障快照",
          ],
        },
      ],
    };
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(localNotAvailableEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.deliveryShapes).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it.each([
    {
      name: "缺少恢复后当前机构生效版本读回",
      body: {
        ...systemProvidersEvidence,
        runtimeContinuityEvidence: {
          ...systemProvidersEvidence.runtimeContinuityEvidence,
          currentRuntime: undefined,
        },
      },
    },
    {
      name: "第三方运行契约与当前机构生效版本不一致",
      body: {
        ...systemProvidersEvidence,
        runtimeContinuityEvidence: {
          ...systemProvidersEvidence.runtimeContinuityEvidence,
          runtimeConsumer: {
            ...systemProvidersEvidence.runtimeContinuityEvidence.runtimeConsumer,
            releaseId: "runtime-other",
          },
        },
      },
    },
    {
      name: "缺少恢复后临床前台主链路冒烟",
      body: {
        ...systemProvidersEvidence,
        runtimeContinuityEvidence: {
          ...systemProvidersEvidence.runtimeContinuityEvidence,
          clinicalSmoke: undefined,
        },
      },
    },
    {
      name: "备份恢复证据不是隔离库成功演练",
      body: {
        ...systemProvidersEvidence,
        backup: {
          ...systemProvidersEvidence.backup,
          drillEvidence: {
            ...systemProvidersEvidence.backup.drillEvidence,
            drillDatabaseIsIsolated: false,
          },
        },
      },
    },
  ])("does not declare system operations coverage when $name", ({ body }) => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(body),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.deliveryShapes).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("does not declare system operations coverage without complete readonly operations evidence", () => {
    const missingAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
        },
      ],
    });
    const incompleteAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify({
                deliveryShapes: ["MANAGEMENT_WORKSPACE"],
                serviceCombinations: ["COMPLIANCE_OPERATIONS"],
                apiEvidence: { operationsSnapshotRead: true },
                scenarioEvidence: [{ observedStages: ["平台管理员读取真实服务运行保障快照"] }],
              }),
            },
          ],
        },
      ],
    });

    expect(missingAttachment.launchCoverage.deliveryShapes).toBeUndefined();
    expect(missingAttachment.launchCoverage.serviceCombinations).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.deliveryShapes).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("does not declare third-party system family coverage from registration-only family codes without consumer evidence", () => {
    const consumerEvidence = [
      "HIS_EMR_CDR",
      "LIS_MONITORING_CRITICAL",
      "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      "PHARMACY_REVIEW",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      "MEDICAL_RECORD_INSURANCE_PAYMENT",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
      "FOLLOWUP_PATIENT_SERVICE",
      "CA_OIDC_SSO_HR",
      "REGIONAL_REMOTE",
      "SPD_UDI_DEVICE",
      "RESEARCH_ETHICS_DATA",
      "MODEL_DIFY_AGENT",
    ].map((systemFamilyCode) => ({
      systemFamilyCode,
      onboardingId: `onb-${systemFamilyCode.toLowerCase()}`,
      adapterId: `adapter-${systemFamilyCode.toLowerCase()}`,
      healthStatus: "NOT_CONNECTED",
      consumerVerified: false,
      standardResourceVerified: false,
      degradationVerified: true,
      auditVerified: true,
    }));
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/third-party-system-families-rehearsal.spec.ts",
          title: "平台管理员逐类登记第三方系统族接入并验证断连诚实降级",
          status: "passed",
          attachments: [
            {
              name: "third-party-system-family-codes",
              contentType: "application/json",
              body: JSON.stringify({
                systemFamilyCodes: consumerEvidence.map((item) => item.systemFamilyCode),
                scopeStatement:
                  "只证明 13 类第三方系统族接入申请、适配器登记、健康诊断和数据质量缺口诚实回读，不代表每个系统族均已完成真实消费者、标准资源、闭环回传或完整断连降级。",
                registrationEvidence: {
                  adapterTotal: 13,
                  notConnectedCount: 13,
                  gapSummary: "NOT_CONNECTED 适配器：13",
                  sampledHealthStatus: "NOT_CONNECTED",
                },
                consumerEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
    expect(evidence.launchCoverage.deliveryShapes).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
    expect(
      evidence.launchCoverage.thirdPartySystemFamilyDegradationRows?.map((item) => item.code),
    ).toEqual(consumerEvidence.map((item) => item.systemFamilyCode));
  });

  it("does not declare third-party degradation rows when registration evidence omits a disconnected family", () => {
    const consumerEvidence = [
      "HIS_EMR_CDR",
      "LIS_MONITORING_CRITICAL",
      "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      "PHARMACY_REVIEW",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      "MEDICAL_RECORD_INSURANCE_PAYMENT",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
      "FOLLOWUP_PATIENT_SERVICE",
      "CA_OIDC_SSO_HR",
      "REGIONAL_REMOTE",
      "SPD_UDI_DEVICE",
      "RESEARCH_ETHICS_DATA",
      "MODEL_DIFY_AGENT",
    ].map((systemFamilyCode) => ({
      systemFamilyCode,
      onboardingId: `onb-${systemFamilyCode.toLowerCase()}`,
      adapterId: `adapter-${systemFamilyCode.toLowerCase()}`,
      healthStatus: systemFamilyCode === "MODEL_DIFY_AGENT" ? "HEALTHY" : "NOT_CONNECTED",
      consumerVerified: false,
      standardResourceVerified: false,
      degradationVerified: systemFamilyCode !== "MODEL_DIFY_AGENT",
      auditVerified: true,
    }));
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/third-party-system-families-rehearsal.spec.ts",
          title: "平台管理员逐类登记第三方系统族接入并验证断连诚实降级",
          status: "passed",
          attachments: [
            {
              name: "third-party-system-family-codes",
              contentType: "application/json",
              body: JSON.stringify({
                systemFamilyCodes: consumerEvidence.map((item) => item.systemFamilyCode),
                scopeStatement:
                  "只证明 13 类第三方系统族接入申请、适配器登记、健康诊断和数据质量缺口诚实回读，不代表每个系统族均已完成真实消费者、标准资源、闭环回传或完整断连降级。",
                registrationEvidence: {
                  adapterTotal: 13,
                  notConnectedCount: 12,
                  gapSummary: "NOT_CONNECTED 适配器：12",
                  sampledHealthStatus: "NOT_CONNECTED",
                },
                consumerEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.thirdPartySystemFamilyDegradationRows).toBeUndefined();
  });

  it("declares third-party system family coverage only when registration, honest degradation and real consumer evidence are attached", () => {
    const consumerEvidence = [
      "HIS_EMR_CDR",
      "LIS_MONITORING_CRITICAL",
      "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      "PHARMACY_REVIEW",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      "MEDICAL_RECORD_INSURANCE_PAYMENT",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
      "FOLLOWUP_PATIENT_SERVICE",
      "CA_OIDC_SSO_HR",
      "REGIONAL_REMOTE",
      "SPD_UDI_DEVICE",
      "RESEARCH_ETHICS_DATA",
      "MODEL_DIFY_AGENT",
    ].map((systemFamilyCode) => ({
      systemFamilyCode,
      onboardingId: `onb-${systemFamilyCode.toLowerCase()}`,
      adapterId: `adapter-${systemFamilyCode.toLowerCase()}`,
      healthStatus: "NOT_CONNECTED",
      consumerVerified: true,
      standardResourceVerified: true,
      degradationVerified: true,
      auditVerified: true,
    }));
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/third-party-system-families-rehearsal.spec.ts",
          title: "平台管理员逐类登记第三方系统族接入并验证断连诚实降级",
          status: "passed",
          attachments: [
            {
              name: "third-party-system-family-codes",
              contentType: "application/json",
              body: JSON.stringify({
                systemFamilyCodes: consumerEvidence.map((item) => item.systemFamilyCode),
                consumerEvidence,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.thirdPartySystemFamilies?.map((item) => item.code)).toEqual([
      "HIS_EMR_CDR",
      "LIS_MONITORING_CRITICAL",
      "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
      "PHARMACY_REVIEW",
      "NURSING_ANESTHESIA_TRANSFUSION_ICU",
      "MEDICAL_RECORD_INSURANCE_PAYMENT",
      "PUBLIC_HEALTH_INFECTION_REGULATORY",
      "FOLLOWUP_PATIENT_SERVICE",
      "CA_OIDC_SSO_HR",
      "REGIONAL_REMOTE",
      "SPD_UDI_DEVICE",
      "RESEARCH_ETHICS_DATA",
      "MODEL_DIFY_AGENT",
    ]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
    ]);
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
  });

  it("does not declare third-party family coverage from a passed spec without API回读 attachment", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/third-party-system-families-rehearsal.spec.ts",
          title: "平台管理员逐类登记第三方系统族接入并验证断连诚实降级",
          status: "passed",
        },
      ],
    });

    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
  });

  it("does not declare S2/S4 runtime mapping coverage from adapter registration alone", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/third-party-system-families-rehearsal.spec.ts",
          title: "平台管理员逐类登记第三方系统族接入并验证断连诚实降级",
          status: "passed",
          attachments: [
            {
              name: "third-party-system-family-codes",
              contentType: "application/json",
              body: JSON.stringify({
                systemFamilyCodes: [
                  "HIS_EMR_CDR",
                  "LIS_MONITORING_CRITICAL",
                  "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
                  "PHARMACY_REVIEW",
                  "NURSING_ANESTHESIA_TRANSFUSION_ICU",
                  "MEDICAL_RECORD_INSURANCE_PAYMENT",
                  "PUBLIC_HEALTH_INFECTION_REGULATORY",
                  "FOLLOWUP_PATIENT_SERVICE",
                  "CA_OIDC_SSO_HR",
                  "REGIONAL_REMOTE",
                  "SPD_UDI_DEVICE",
                  "RESEARCH_ETHICS_DATA",
                  "MODEL_DIFY_AGENT",
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code) ?? []).not.toEqual(
      expect.arrayContaining(["S2", "S4"]),
    );
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code) ?? []).not.toContain(
      "TERMINOLOGY",
    );
  });

  it("declares S2/S4 runtime mapping coverage and only the condition rows backed by real evidence", () => {
    const evidence = s2s4EvidenceResult(s2s4RuntimeMappingEvidence());

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S2", "S4"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "DATA_INTEROPERABILITY",
      "MEDICAL_ASSET",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "TERMINOLOGY",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
    ]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S2__NORMAL",
      "S2__ABNORMAL",
      "S4__NORMAL",
      "S4__ABNORMAL",
    ]);
  });

  it("does not auto-generate S2/S4 condition rows from ordinary scenario coverage", () => {
    const evidence = s2s4EvidenceResult(
      s2s4RuntimeMappingEvidence({ scenarioConditionEvidence: undefined }),
    );

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S2", "S4"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "异常签名拒绝证据缺失",
      body: s2s4RuntimeMappingEvidence({
        apiEvidence: {
          ...s2s4RuntimeMappingEvidence().apiEvidence,
          invalidInboundWebhookSignatureRejected: false,
        },
      }),
    },
    {
      name: "条件行代码不属于 S2/S4 已证明范围",
      body: s2s4RuntimeMappingEvidence({
        scenarioConditionEvidence: [
          ...(s2s4RuntimeMappingEvidence().scenarioConditionEvidence as unknown[]),
          {
            code: "S2__DEGRADATION",
            scenarioCode: "S2",
            condition: "DEGRADATION",
            source: "UNPROVEN_DEGRADATION",
            evidence: ["没有真实降级证据"],
          },
        ],
      }),
    },
    {
      name: "条件行场景和状态与代码不一致",
      body: s2s4RuntimeMappingEvidence({
        scenarioConditionEvidence: [
          {
            code: "S2__NORMAL",
            scenarioCode: "S4",
            condition: "NORMAL",
            source: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
            evidence: ["场景代码不一致"],
          },
        ],
      }),
    },
    {
      name: "条件行没有具体证据文本",
      body: s2s4RuntimeMappingEvidence({
        scenarioConditionEvidence: [
          {
            code: "S2__NORMAL",
            scenarioCode: "S2",
            condition: "NORMAL",
            source: "SIGNED_WEBHOOK_INBOUND_NORMALIZATION",
            evidence: [],
          },
        ],
      }),
    },
  ])("does not declare S2/S4 condition rows when $name", ({ body }) => {
    const evidence = s2s4EvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "缺少真实入站归一结果",
      body: s2s4RuntimeMappingEvidence({ inboundResult: undefined }),
    },
    {
      name: "入站归一 releaseId 与当前 runtime 不一致",
      body: s2s4RuntimeMappingEvidence({
        inboundResult: {
          ...s2s4RuntimeMappingEvidence().inboundResult,
          mappedPayload: {
            observations: [
              {
                standardCode: "718-7",
                codeSystem: "LOINC",
                localCode: "LIS-HGB",
                sourceSystem: "LIS",
                runtimeReleaseId: "runtime-other",
                mappingId: 101,
              },
            ],
          },
        },
      }),
    },
    {
      name: "第三方 runtime contract 未读回本轮术语资产",
      body: s2s4RuntimeMappingEvidence({
        runtimeConsumerReadback: {
          releaseId: "runtime-s2-s4",
          revisionNo: 7,
          manifestSha256: "a".repeat(64),
          assets: [],
        },
      }),
    },
    {
      name: "前台生成机构生效版本请求没有携带本轮术语资产",
      body: s2s4RuntimeMappingEvidence({ activationRequest: { activeAssets: [] } }),
    },
    {
      name: "适配器只有登记没有术语字段映射",
      body: s2s4RuntimeMappingEvidence({
        adapter: {
          adapterId: "lis-s2-s4",
          protocolType: "Webhook",
          sourceSystem: "LIS",
          fieldMappings: [{ sourcePath: "/patientId", targetPath: "/patient/mpi" }],
        },
      }),
    },
  ])("does not declare S2/S4 runtime mapping coverage when $name", ({ body }) => {
    expectNoS2S4RuntimeMappingCoverage(body);
  });

  it("declares VALUE_SET/FORMULA/ACTION_CARD coverage only when CDSS consumes them from the current runtime", () => {
    const evidence = cdssRuntimeDeclarativeEvidenceResult(cdssRuntimeDeclarativeAssets);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S5"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "VALUE_SET",
      "FORMULA",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "CLINICAL_RUNTIME",
    ]);
  });

  it("declares S5 normal condition row only from explicit CDSS declarative runtime consumption evidence", () => {
    const evidence = cdssRuntimeDeclarativeEvidenceResult(cdssRuntimeDeclarativeAssets);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S5__NORMAL",
    ]);
  });

  it("does not declare S5 normal condition row from ordinary declarative runtime coverage", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = cdssRuntimeDeclarativeAssets;
    const evidence = cdssRuntimeDeclarativeEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S5"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "只证明发布读回没有推荐解释",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: undefined,
      },
    },
    {
      name: "推荐触发绑定的 runtime 与当前机构生效版本不一致",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        clinicalTrigger: {
          ...cdssRuntimeDeclarativeAssets.clinicalTrigger,
          runtimeReleaseId: "runtime-other",
        },
      },
    },
    {
      name: "本轮前台创建资产没有进入当前机构生效版本",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        createdAssets: cdssRuntimeDeclarativeAssets.createdAssets.map((asset) =>
          asset.assetType === "VALUE_SET" ? { ...asset, versionId: "vs-other" } : asset,
        ),
      },
    },
    {
      name: "推荐详情卡片不是本次真实前台触发生成的卡片",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          cardId: "card-other",
        },
      },
    },
    {
      name: "推荐详情卡片不属于本次触发诊断关联卡",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        clinicalTrigger: {
          ...cdssRuntimeDeclarativeAssets.clinicalTrigger,
          relatedCardIds: ["card-other-runtime"],
        },
      },
    },
    {
      name: "推荐详情没有绑定本次前台选择的上下文快照",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          contextSnapshotId: "ctx-other",
        },
      },
    },
    {
      name: "最终机构生效版本没有包含本轮 RULE 统一资产版本",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        runtime: {
          ...cdssRuntimeDeclarativeAssets.runtime,
          ruleAsset: undefined,
        },
      },
    },
    {
      name: "最终机构生效版本激活请求没有携带本轮 RULE 候选",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        activationRequest: {
          activeAssets: cdssRuntimeDeclarativeAssets.activationRequest.activeAssets.filter(
            (asset) => asset.assetType !== "RULE",
          ),
        },
      },
    },
    {
      name: "解释里缺少 VALUE_SET 物化版本",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          explanation: {
            ...cdssRuntimeDeclarativeAssets.recommendation.explanation,
            ruleExplanation: {
              ...cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation,
              runtimeAssetEvidence:
                cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.filter(
                  (item) => item.assetType !== "VALUE_SET",
                ),
            },
          },
        },
      },
    },
    {
      name: "解释里缺少 FORMULA 物化版本",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          explanation: {
            ...cdssRuntimeDeclarativeAssets.recommendation.explanation,
            ruleExplanation: {
              ...cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation,
              runtimeAssetEvidence:
                cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.filter(
                  (item) => item.assetType !== "FORMULA",
                ),
            },
          },
        },
      },
    },
    {
      name: "解释里缺少 ACTION_CARD 运行版本与哈希",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          explanation: {
            ...cdssRuntimeDeclarativeAssets.recommendation.explanation,
            ruleExplanation: {
              ...cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation,
              runtimeAssetEvidence:
                cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.filter(
                  (item) => item.assetType !== "ACTION_CARD",
                ),
            },
          },
        },
      },
    },
  ])("does not declare CDSS declarative runtime asset coverage when $name", ({ body }) => {
    expectNoCdssRuntimeDeclarativeCoverage(body);
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        scenarioConditionEvidence: [
          {
            code: "S5__ABNORMAL",
            scenarioCode: "S5",
            condition: "ABNORMAL",
            source: "CDSS_DECLARATIVE_RUNTIME_ASSET_CONSUMPTION",
            evidence: ["声明式资产正常消费不能冒领异常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        scenarioConditionEvidence: [
          {
            code: "S5__NORMAL",
            scenarioCode: "S5",
            condition: "NORMAL",
            source: "CDSS_PAGE_VISIBLE_ONLY",
            evidence: ["不能只靠页面可见冒领正常态"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        scenarioConditionEvidence: [
          {
            code: "S5__NORMAL",
            scenarioCode: "S5",
            condition: "NORMAL",
            source: "CDSS_DECLARATIVE_RUNTIME_ASSET_CONSUMPTION",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "规则候选没有进入最终机构生效版本",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        runtime: {
          ...cdssRuntimeDeclarativeAssets.runtime,
          ruleAsset: undefined,
        },
      },
    },
    {
      name: "推荐触发绑定的 runtime 与当前机构生效版本不一致",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        clinicalTrigger: {
          ...cdssRuntimeDeclarativeAssets.clinicalTrigger,
          runtimeReleaseId: "runtime-other",
        },
      },
    },
    {
      name: "推荐解释缺少 VALUE_SET 物化证据",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          explanation: {
            ...cdssRuntimeDeclarativeAssets.recommendation.explanation,
            ruleExplanation: {
              ...cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation,
              runtimeAssetEvidence:
                cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.filter(
                  (item) => item.assetType !== "VALUE_SET",
                ),
            },
          },
        },
      },
    },
    {
      name: "ACTION_CARD 不要求医生确认",
      body: {
        ...cdssRuntimeDeclarativeAssets,
        recommendation: {
          ...cdssRuntimeDeclarativeAssets.recommendation,
          explanation: {
            ...cdssRuntimeDeclarativeAssets.recommendation.explanation,
            ruleExplanation: {
              ...cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation,
              runtimeAssetEvidence:
                cdssRuntimeDeclarativeAssets.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.map(
                  (item) =>
                    item.assetType === "ACTION_CARD"
                      ? { ...item, requiresPhysicianConfirmation: false }
                      : item,
                ),
            },
          },
        },
      },
    },
  ])("does not declare S5 normal condition row when $name", ({ body }) => {
    expectNoCdssRuntimeDeclarativeScenarioConditionCoverage(body);
  });

  it("declares SAFETY/CDSS_RISK/RULE coverage only for the medication safety representative slice", () => {
    const evidence = medicationSafetyEvidenceResult(medicationSafetyFrontdeskEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S5"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "SAFETY",
      "CDSS_RISK",
      "RULE",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "CLINICAL_RUNTIME",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it.each([
    {
      name: "SAFETY 候选版本与当前机构生效版本不一致",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        safetyRedline: {
          ...medicationSafetyFrontdeskEvidence.safetyRedline,
          contentHash: "a".repeat(64),
        },
      },
    },
    {
      name: "规则候选版本与当前机构生效版本不一致",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        ruleAsset: {
          ...medicationSafetyFrontdeskEvidence.ruleAsset,
          versionId: "av-rule-med-other",
        },
      },
    },
    {
      name: "规则发布缺少术语覆盖门禁证据",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        terminologyGate: undefined,
      },
    },
    {
      name: "规则发布未证明术语覆盖门禁已激活",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        apiEvidence: {
          ...medicationSafetyFrontdeskEvidence.apiEvidence,
          terminologyCoverageGateActivated: false,
        },
      },
    },
    {
      name: "同次触发未证明本轮 RULE 推荐卡命中",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        ruleRecommendation: undefined,
      },
    },
    {
      name: "RULE 推荐解释缺少 Medication 与 AllergyIntolerance 条件命中证据",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        ruleRecommendation: {
          ...medicationSafetyFrontdeskEvidence.ruleRecommendation,
          explanation: {
            ...medicationSafetyFrontdeskEvidence.ruleRecommendation.explanation,
            ruleExplanation: {
              ...medicationSafetyFrontdeskEvidence.ruleRecommendation.explanation.ruleExplanation,
              conditionEvidence: [
                {
                  fact: "medications[].code",
                  operator: "contains",
                  expected: "J01C",
                  actual: ["J01C"],
                  matched: true,
                },
              ],
            },
          },
        },
      },
    },
    {
      name: "没有医生与药师双人工闭环",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        feedback: {
          ...medicationSafetyFrontdeskEvidence.feedback,
          pharmacist: {
            ...medicationSafetyFrontdeskEvidence.feedback.pharmacist,
            cardStatus: "VIEWED",
          },
        },
      },
    },
    {
      name: "当前机构生效版本没有激活 SAFETY 资产",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        runtime: {
          ...medicationSafetyFrontdeskEvidence.runtime,
          safetyAsset: {
            ...medicationSafetyFrontdeskEvidence.runtime.safetyAsset,
            entryState: "DISABLED",
          },
        },
      },
    },
    {
      name: "推荐详情不是临床安全红线卡",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        recommendation: {
          ...medicationSafetyFrontdeskEvidence.recommendation,
          explanation: {
            ...medicationSafetyFrontdeskEvidence.recommendation.explanation,
            matchType: "RULE",
          },
        },
      },
    },
    {
      name: "临床上下文缺少 AllergyIntolerance 结构化过敏资源",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        clinicalContext: {
          ...medicationSafetyFrontdeskEvidence.clinicalContext,
          resources: {
            ...medicationSafetyFrontdeskEvidence.clinicalContext.resources,
            allergyIntolerances: [],
          },
        },
      },
    },
  ])("does not declare medication safety representative coverage when $name", ({ body }) => {
    expectNoMedicationSafetyCoverage(body);
  });

  it("declares S36 diagnostic critical-value coverage only with inbound resources, runtime assets and human closure", () => {
    const evidence = diagnosticCriticalValueEvidenceResult(diagnosticCriticalValueEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S36"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "KNOWLEDGE",
      "FIELD_CATALOG",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares PACS/RIS diagnostic family representative consumer slice only with S36 real consumer evidence", () => {
    const evidence = diagnosticCriticalValueEvidenceResult(diagnosticCriticalValueEvidence);

    expect(
      evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code),
    ).toEqual(["PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG"]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares five diagnostic report family consumer matrix only with all real report families", () => {
    const evidence = diagnosticCriticalValueEvidenceResult(diagnosticReportFamilyMatrixEvidence);

    expect(
      evidence.launchCoverage.diagnosticReportFamilyConsumerMatrix?.map((item) => item.code),
    ).toEqual(["PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG"]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares 13 standard patient resource representative consumer matrix only from cross-frontdesk resource evidence", () => {
    const evidence = standardPatientResourceMatrixEvidenceResult(
      standardPatientResourceMatrixBodies(),
    );

    expect(
      evidence.launchCoverage.standardPatientResourceConsumerMatrix?.map((item) => item.code),
    ).toEqual(["THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.standardPatientResourceRepresentativeRows?.map((item) => item.code),
    ).toEqual([
      "Patient",
      "AllergyIntolerance",
      "Encounter",
      "Condition",
      "NursingAssessment",
      "Observation",
      "DiagnosticReport",
      "Medication",
      "Procedure",
      "Document",
      "CarePlan",
      "FollowUp",
      "Claim",
    ]);
    expect(evidence.launchCoverage.standardPatientResources).toBeUndefined();
  });

  it.each([
    {
      name: "缺少 Claim 患者资源",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody([]),
      }),
    },
    {
      name: "重复资源类型伪造 13 类矩阵",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody([], {
          rows: [
            {
              ...standardPatientResourceBody(["Observation"]).standardPatientResourceConsumerMatrix
                .resources[0],
              resourceType: "Observation",
            },
          ],
        }),
      }),
    },
    {
      name: "包含未知资源类型",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody([], {
          rows: [
            {
              ...standardPatientResourceBody(["Claim"]).standardPatientResourceConsumerMatrix
                .resources[0],
              resourceType: "BillingStatement",
            },
          ],
        }),
      }),
    },
    {
      name: "只有附件常量没有绑定真实资源路径",
      bodies: standardPatientResourceMatrixBodies({
        medication: standardPatientResourceBody(["Patient"], {
          rows: [
            {
              ...standardPatientResourceBody(["Patient"]).standardPatientResourceConsumerMatrix
                .resources[0],
              resourcePath: "clinicalContext.resources.patientMissing",
            },
          ],
        }),
      }),
    },
    {
      name: "sourceIdPath 指向空值无法证明真实资源身份",
      bodies: standardPatientResourceMatrixBodies({
        surgery: standardPatientResourceBody(["Procedure", "Document"], {
          rows: [
            {
              ...standardPatientResourceBody(["Procedure"]).standardPatientResourceConsumerMatrix
                .resources[0],
              sourceId: undefined,
              sourceIdPath: "clinicalContext.resources.procedures[1].sourceRecordId",
              resourcePath: "clinicalContext.resources.procedures[0]",
            },
            standardPatientResourceBody(["Document"]).standardPatientResourceConsumerMatrix
              .resources[0],
          ],
        }),
      }),
    },
    {
      name: "Claim 未绑定医保审核评估运行",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody(["Claim"], {
          insuranceAudit: {
            issueId: "issue-resource-matrix",
            evaluationRunId: "",
            auditStatus: "ISSUE_FOUND",
          },
        }),
      }),
    },
    {
      name: "Claim 行冒用 Patient 资源路径",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody(["Claim"], {
          rows: [
            {
              ...standardPatientResourceBody(["Claim"]).standardPatientResourceConsumerMatrix
                .resources[0],
              resourcePath: "clinicalContext.resources.patient",
              sourceSystem: "MEDKERNEL_FRONTDESK",
              sourceIdPath: "clinicalContext.resources.patient.sourceRecordId",
            },
          ],
        }),
      }),
    },
    {
      name: "scope 漏写不代表完整 S0-S40",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody(["Claim"], {
          scopeStatement:
            "13 类标准患者资源真实接入与消费者代表矩阵：跨真实前台演练聚合资源证据；不代表每类字段目录全量落地，不代表完整上线验收。",
        }),
      }),
    },
    {
      name: "scope 过度宣称 13 类标准患者资源全量上线完成",
      bodies: standardPatientResourceMatrixBodies({
        realFrontdesk: standardPatientResourceBody(["Claim"], {
          scopeStatement:
            "13 类标准患者资源真实接入与消费者代表矩阵：跨真实前台演练聚合资源证据；13 类标准患者资源全量上线完成。",
        }),
      }),
    },
  ])("does not declare standard patient resource matrix when $name", ({ bodies }) => {
    const evidence = standardPatientResourceMatrixEvidenceResult(bodies);

    expect(evidence.launchCoverage.standardPatientResourceConsumerMatrix).toBeUndefined();
    expect(evidence.launchCoverage.standardPatientResourceRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.standardPatientResources).toBeUndefined();
  });

  it("does not aggregate standard patient resource matrix from non-target attachments", () => {
    const evidence = standardPatientResourceMatrixEvidenceResult([
      ...standardPatientResourceMatrixBodies().slice(0, 5),
      {
        file: "ad-hoc-resource-dump.spec.ts",
        title: "临时资源导出附件",
        attachmentName: "ad-hoc-resource-dump",
        body: standardPatientResourceBody(["Claim"]),
      },
    ]);

    expect(evidence.launchCoverage.standardPatientResourceConsumerMatrix).toBeUndefined();
    expect(evidence.launchCoverage.standardPatientResourceRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.standardPatientResources).toBeUndefined();
  });

  it("declares S36 diagnostic critical-value coverage when FHIR retry compensation reaches NOT_CONNECTED", () => {
    const evidence = diagnosticCriticalValueEvidenceResult({
      ...diagnosticCriticalValueEvidence,
      inboundObservation: {
        ...diagnosticCriticalValueEvidence.inboundObservation,
        integrationStatus: "RETRYING",
        operationOutcomeContainsNotConnected: false,
        compensationStatus: "NOT_CONNECTED",
        compensationRequired: null,
      },
      inboundDiagnosticReport: {
        ...diagnosticCriticalValueEvidence.inboundDiagnosticReport,
        integrationStatus: "RETRYING",
        operationOutcomeContainsNotConnected: false,
        compensationStatus: "NOT_CONNECTED",
        compensationRequired: null,
      },
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S36"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares S36 high-risk and degradation condition rows only from explicit critical-value evidence", () => {
    const evidence = diagnosticCriticalValueEvidenceResult(diagnosticCriticalValueEvidence);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S36__HIGH_RISK",
      "S36__DEGRADATION",
    ]);
  });

  it("does not declare S36 condition rows without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = diagnosticCriticalValueEvidence;
    const evidence = diagnosticCriticalValueEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...diagnosticCriticalValueEvidence,
        scenarioConditionEvidence: [
          {
            code: "S36__NORMAL",
            scenarioCode: "S36",
            condition: "NORMAL",
            source: "DIAGNOSTIC_CRITICAL_VALUE_HUMAN_CLOSURE",
            evidence: ["危急值高风险链路不能冒领普通正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...diagnosticCriticalValueEvidence,
        scenarioConditionEvidence: [
          {
            code: "S36__HIGH_RISK",
            scenarioCode: "S36",
            condition: "HIGH_RISK",
            source: "FHIR_LIS_NOT_CONNECTED_COMPENSATION",
            evidence: ["来源错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...diagnosticCriticalValueEvidence,
        scenarioConditionEvidence: [
          {
            code: "S36__HIGH_RISK",
            scenarioCode: "S36",
            condition: "HIGH_RISK",
            source: "DIAGNOSTIC_CRITICAL_VALUE_HUMAN_CLOSURE",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "报告解读不是危急风险",
      body: {
        ...diagnosticCriticalValueEvidence,
        interpretation: {
          ...diagnosticCriticalValueEvidence.interpretation,
          interpretations: [
            {
              ...diagnosticCriticalValueEvidence.interpretation.interpretations[0],
              criticalRisk: false,
            },
          ],
        },
      },
    },
    {
      name: "推荐卡不要求医师确认",
      body: {
        ...diagnosticCriticalValueEvidence,
        recommendation: {
          ...diagnosticCriticalValueEvidence.recommendation,
          requiresPhysicianConfirmation: false,
        },
      },
    },
    {
      name: "协同待办未完成",
      body: {
        ...diagnosticCriticalValueEvidence,
        workflowTodo: {
          ...diagnosticCriticalValueEvidence.workflowTodo,
          status: "PENDING",
        },
      },
    },
    {
      name: "Observation 未收敛到 NOT_CONNECTED",
      body: {
        ...diagnosticCriticalValueEvidence,
        inboundObservation: {
          ...diagnosticCriticalValueEvidence.inboundObservation,
          integrationStatus: "RETRYING",
          operationOutcomeContainsNotConnected: false,
          compensationStatus: "RETRYING",
        },
      },
    },
    {
      name: "DiagnosticReport 未收敛到 NOT_CONNECTED",
      body: {
        ...diagnosticCriticalValueEvidence,
        inboundDiagnosticReport: {
          ...diagnosticCriticalValueEvidence.inboundDiagnosticReport,
          integrationStatus: "RETRYING",
          operationOutcomeContainsNotConnected: false,
          compensationStatus: "RETRYING",
        },
      },
    },
  ])("does not declare S36 scenario condition rows when $name", ({ body }) => {
    const evidence = diagnosticCriticalValueEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "缺少外部 Observation 危急值标准资源",
      body: {
        ...diagnosticCriticalValueEvidence,
        inboundObservation: undefined,
      },
    },
    {
      name: "上下文未回读入站 DiagnosticReport",
      body: {
        ...diagnosticCriticalValueEvidence,
        clinicalContext: {
          ...diagnosticCriticalValueEvidence.clinicalContext,
          resources: {
            ...diagnosticCriticalValueEvidence.clinicalContext.resources,
            diagnosticReports: [],
          },
        },
      },
    },
    {
      name: "当前机构生效版本缺少 FIELD_CATALOG 物化证据",
      body: {
        ...diagnosticCriticalValueEvidence,
        runtime: {
          ...diagnosticCriticalValueEvidence.runtime,
          fieldCatalogAsset: {
            ...diagnosticCriticalValueEvidence.runtime.fieldCatalogAsset,
            entryState: "DISABLED",
          },
        },
      },
    },
    {
      name: "FIELD_CATALOG 被当作本地版本激活而不是选择平台标准版本",
      body: {
        ...diagnosticCriticalValueEvidence,
        activationRequest: {
          ...diagnosticCriticalValueEvidence.activationRequest,
          activeAssets: diagnosticCriticalValueEvidence.activationRequest.activeAssets.map(
            (asset) =>
              asset.assetType === "FIELD_CATALOG"
                ? { ...asset, versionId: "fc-critical-report" }
                : asset,
          ),
        },
      },
    },
    {
      name: "推荐解释缺少 ACTION_CARD 运行资产证据",
      body: {
        ...diagnosticCriticalValueEvidence,
        recommendation: {
          ...diagnosticCriticalValueEvidence.recommendation,
          explanation: {
            ...diagnosticCriticalValueEvidence.recommendation.explanation,
            runtimeAssetEvidence:
              diagnosticCriticalValueEvidence.recommendation.explanation.runtimeAssetEvidence.filter(
                (item) => item.assetType !== "ACTION_CARD",
              ),
          },
        },
      },
    },
    {
      name: "报告解读不是危急风险",
      body: {
        ...diagnosticCriticalValueEvidence,
        interpretation: {
          ...diagnosticCriticalValueEvidence.interpretation,
          interpretations: [
            {
              ...diagnosticCriticalValueEvidence.interpretation.interpretations[0],
              criticalRisk: false,
            },
          ],
        },
      },
    },
    {
      name: "协同待办未由人工完成",
      body: {
        ...diagnosticCriticalValueEvidence,
        workflowTodo: {
          ...diagnosticCriticalValueEvidence.workflowTodo,
          status: "PENDING",
        },
      },
    },
    {
      name: "完成的协同待办未绑定本轮推荐卡",
      body: {
        ...diagnosticCriticalValueEvidence,
        workflowTodo: {
          ...diagnosticCriticalValueEvidence.workflowTodo,
          sourceId: "card-from-previous-rehearsal",
        },
      },
    },
    {
      name: "FHIR 异步补偿日志未收敛到 NOT_CONNECTED",
      body: {
        ...diagnosticCriticalValueEvidence,
        inboundDiagnosticReport: {
          ...diagnosticCriticalValueEvidence.inboundDiagnosticReport,
          integrationStatus: "RETRYING",
          operationOutcomeContainsNotConnected: false,
          compensationStatus: "RETRYING",
        },
      },
    },
  ])("does not declare diagnostic critical-value coverage when $name", ({ body }) => {
    expectNoDiagnosticCriticalValueCoverage(body);
  });

  it.each([
    {
      name: "缺少 PACS/RIS 医技系统族代表消费者证据",
      body: {
        ...diagnosticCriticalValueEvidence,
        thirdPartySystemFamilyConsumerSlice: undefined,
      },
    },
    {
      name: "医技系统族代表消费者证据过度宣称完整覆盖",
      body: {
        ...diagnosticCriticalValueEvidence,
        thirdPartySystemFamilyConsumerSlice: {
          ...diagnosticCriticalValueEvidence.thirdPartySystemFamilyConsumerSlice,
          scopeStatement: "完整 PACS/RIS/病理/内镜/心电系统族覆盖已完成。",
        },
      },
    },
    {
      name: "系统族代码不是 PACS/RIS 医技系统族",
      body: {
        ...diagnosticCriticalValueEvidence,
        thirdPartySystemFamilyConsumerSlice: {
          ...diagnosticCriticalValueEvidence.thirdPartySystemFamilyConsumerSlice,
          systemFamilyCode: "REGIONAL_REMOTE",
        },
      },
    },
  ])("does not declare PACS/RIS diagnostic family consumer slice when $name", ({ body }) => {
    expectNoDiagnosticFamilyConsumerSliceCoverage(body);
  });

  it.each([
    {
      name: "缺少五类报告族矩阵附件字段",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        diagnosticReportFamilyConsumerMatrix: undefined,
      },
    },
    {
      name: "五类报告族矩阵缺少心电报告",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        diagnosticReportFamilyConsumerMatrix: {
          ...diagnosticReportFamilyMatrixEvidence.diagnosticReportFamilyConsumerMatrix,
          rows: diagnosticReportFamilyMatrixRows.filter((row) => row.reportFamilyCode !== "ECG"),
        },
      },
    },
    {
      name: "五类报告族矩阵包含未知报告族",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        diagnosticReportFamilyConsumerMatrix: {
          ...diagnosticReportFamilyMatrixEvidence.diagnosticReportFamilyConsumerMatrix,
          rows: [
            ...diagnosticReportFamilyMatrixRows.slice(0, 4),
            {
              ...diagnosticReportFamilyMatrixRows[4],
              reportFamilyCode: "DENTAL",
              reportFamilyName: "口腔影像报告",
            },
          ],
        },
      },
    },
    {
      name: "五类报告族矩阵重复 PACS/RIS 且缺少超声",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        diagnosticReportFamilyConsumerMatrix: {
          ...diagnosticReportFamilyMatrixEvidence.diagnosticReportFamilyConsumerMatrix,
          rows: [
            diagnosticReportFamilyMatrixRows[0],
            { ...diagnosticReportFamilyMatrixRows[0], fhirId: "dr-pacs-duplicate" },
            ...diagnosticReportFamilyMatrixRows.slice(2),
          ],
        },
      },
    },
    {
      name: "矩阵行未回读标准 DiagnosticReport",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        clinicalContext: {
          ...diagnosticReportFamilyMatrixEvidence.clinicalContext,
          resources: {
            ...diagnosticReportFamilyMatrixEvidence.clinicalContext.resources,
            diagnosticReports:
              diagnosticReportFamilyMatrixEvidence.clinicalContext.resources.diagnosticReports.filter(
                (row) => row.reportId !== "dr-endoscopy-gastroscopy",
              ),
          },
        },
      },
    },
    {
      name: "矩阵行未被报告解读消费者处理",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        interpretation: {
          ...diagnosticReportFamilyMatrixEvidence.interpretation,
          interpretations:
            diagnosticReportFamilyMatrixEvidence.interpretation.interpretations.filter(
              (row) => row.reportId !== "dr-pathology-biopsy",
            ),
        },
      },
    },
    {
      name: "矩阵行待办未完成人工闭环",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        diagnosticReportFamilyConsumerMatrix: {
          ...diagnosticReportFamilyMatrixEvidence.diagnosticReportFamilyConsumerMatrix,
          rows: diagnosticReportFamilyMatrixRows.map((row) =>
            row.reportFamilyCode === "ULTRASOUND" ? { ...row, workflowTodoCompleted: false } : row,
          ),
        },
      },
    },
    {
      name: "矩阵附件过度宣称完整上线验收",
      body: {
        ...diagnosticReportFamilyMatrixEvidence,
        diagnosticReportFamilyConsumerMatrix: {
          ...diagnosticReportFamilyMatrixEvidence.diagnosticReportFamilyConsumerMatrix,
          scopeStatement: "五类医技报告族完整上线验收已完成。",
        },
      },
    },
  ])("does not declare five diagnostic report family matrix when $name", ({ body }) => {
    expectNoDiagnosticReportFamilyMatrixCoverage(body);
  });

  it("declares S40 regional diagnostic mutual-recognition coverage only with trusted source, runtime assets and human closure", () => {
    const evidence = regionalDiagnosticMutualRecognitionEvidenceResult(
      regionalDiagnosticMutualRecognitionEvidence,
    );

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S40"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "KNOWLEDGE",
      "FIELD_CATALOG",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
      "PROFESSIONAL_COLLABORATION",
    ]);
    expect(
      evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code),
    ).toEqual(["REGIONAL_REMOTE"]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S40__DEGRADATION",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S40 degradation row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } =
      regionalDiagnosticMutualRecognitionEvidence;
    const evidence = regionalDiagnosticMutualRecognitionEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S40"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        scenarioConditionEvidence: [
          {
            code: "S40__NORMAL",
            scenarioCode: "S40",
            condition: "NORMAL",
            source: "REGIONAL_DIAGNOSTIC_MUTUAL_RECOGNITION_NOT_CONNECTED_COMPENSATION",
            evidence: ["区域互认断连切片不能冒领正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        scenarioConditionEvidence: [
          {
            code: "S40__DEGRADATION",
            scenarioCode: "S40",
            condition: "DEGRADATION",
            source: "REGIONAL_DIAGNOSTIC_MUTUAL_RECOGNITION_NORMAL",
            evidence: ["来源错配不能声明降级行"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        scenarioConditionEvidence: [
          {
            code: "S40__DEGRADATION",
            scenarioCode: "S40",
            condition: "DEGRADATION",
            source: "REGIONAL_DIAGNOSTIC_MUTUAL_RECOGNITION_NOT_CONNECTED_COMPENSATION",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "区域 FHIR 接入不是 NOT_CONNECTED",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        fhirOnboarding: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.fhirOnboarding),
          healthStatus: "HEALTHY",
        },
      },
    },
    {
      name: "入站报告不是 NOT_CONNECTED 或 RETRYING",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        inboundDiagnosticReport: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.inboundDiagnosticReport),
          integrationStatus: "PROCESSED",
        },
      },
    },
    {
      name: "入站报告未进入 NOT_CONNECTED 补偿",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        inboundDiagnosticReport: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.inboundDiagnosticReport),
          compensationStatus: "NOT_REQUIRED",
        },
      },
    },
    {
      name: "推荐卡不要求医生确认",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        recommendation: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.recommendation),
          requiresPhysicianConfirmation: false,
        },
      },
    },
    {
      name: "推荐卡由 AI 自动生成",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        recommendation: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.recommendation),
          aiGenerated: true,
        },
      },
    },
    {
      name: "协同待办未完成",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        workflowTodo: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.workflowTodo),
          status: "PENDING",
        },
      },
    },
    {
      name: "协同待办允许自动开嘱",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        workflowTodo: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.workflowTodo),
          noAutoOrder: false,
        },
      },
    },
    {
      name: "协同待办允许自动互认",
      body: {
        ...structuredClone(regionalDiagnosticMutualRecognitionEvidence),
        workflowTodo: {
          ...structuredClone(regionalDiagnosticMutualRecognitionEvidence.workflowTodo),
          noAutoRecognition: false,
        },
      },
    },
  ])("does not declare S40 degradation row when $name", ({ body }) => {
    expectNoRegionalDiagnosticMutualRecognitionScenarioConditionCoverage(body);
  });

  it.each([
    {
      name: "区域来源未回读可信分级",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        regionalSource: undefined,
      },
    },
    {
      name: "区域来源为低可信",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        regionalSource: {
          ...regionalDiagnosticMutualRecognitionEvidence.regionalSource,
          trustLevel: "LOW",
        },
      },
    },
    {
      name: "区域来源缺少分级证据",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        regionalSource: {
          ...regionalDiagnosticMutualRecognitionEvidence.regionalSource,
          evidenceText: "",
        },
      },
    },
    {
      name: "接入申请不是 REGIONAL_REMOTE",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        fhirOnboarding: {
          ...regionalDiagnosticMutualRecognitionEvidence.fhirOnboarding,
          systemFamilyCode: "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
        },
      },
    },
    {
      name: "接入申请不是区域 FHIR 来源",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        fhirOnboarding: {
          ...regionalDiagnosticMutualRecognitionEvidence.fhirOnboarding,
          sourceSystem: "FHIR_R4",
        },
      },
    },
    {
      name: "入站报告未绑定跨机构来源证据",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        inboundDiagnosticReport: {
          ...regionalDiagnosticMutualRecognitionEvidence.inboundDiagnosticReport,
          sourceOrganizationId: "",
          sourceOrganizationName: "",
          regionalSourceId: "",
        },
      },
    },
    {
      name: "上下文未回读入站区域报告",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        clinicalContext: {
          ...regionalDiagnosticMutualRecognitionEvidence.clinicalContext,
          resources: {
            ...regionalDiagnosticMutualRecognitionEvidence.clinicalContext.resources,
            diagnosticReports: [],
          },
        },
      },
    },
    {
      name: "当前机构生效版本缺少 ACTION_CARD 证据",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        recommendation: {
          ...regionalDiagnosticMutualRecognitionEvidence.recommendation,
          explanation: {
            ...regionalDiagnosticMutualRecognitionEvidence.recommendation.explanation,
            runtimeAssetEvidence:
              regionalDiagnosticMutualRecognitionEvidence.recommendation.explanation.runtimeAssetEvidence.filter(
                (item) => item.assetType !== "ACTION_CARD",
              ),
          },
        },
      },
    },
    {
      name: "报告解读未绑定本轮 runtime",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        interpretation: {
          ...regionalDiagnosticMutualRecognitionEvidence.interpretation,
          runtimeReleaseId: "runtime-from-previous-rehearsal",
        },
      },
    },
    {
      name: "协同待办未由人工完成",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        workflowTodo: {
          ...regionalDiagnosticMutualRecognitionEvidence.workflowTodo,
          status: "PENDING",
        },
      },
    },
    {
      name: "协同待办声明自动互认",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        workflowTodo: {
          ...regionalDiagnosticMutualRecognitionEvidence.workflowTodo,
          noAutoRecognition: false,
        },
      },
    },
    {
      name: "scope 过度宣称完整区域平台",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        scopeStatement: "区域医技报告互认代表切片，完整区域平台已上线。",
      },
    },
    {
      name: "scope 过度宣称完整 PACS/RIS/病理/心电系统族覆盖",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        scopeStatement:
          "区域医技报告互认代表切片，不代表完整区域平台；完整 PACS/RIS/病理/内镜/心电系统族覆盖已完成。",
      },
    },
    {
      name: "scope 过度宣称完整 S0-S40",
      body: {
        ...regionalDiagnosticMutualRecognitionEvidence,
        scopeStatement:
          "区域医技报告互认代表切片，不代表完整区域平台、完整远程医疗或完整 PACS/RIS/病理/内镜/心电系统族覆盖；完整 S0-S40 已上线。",
      },
    },
  ])("does not declare S40 regional mutual-recognition coverage when $name", ({ body }) => {
    expectNoRegionalDiagnosticMutualRecognitionCoverage(body);
    expectNoRegionalRemoteConsumerSliceCoverage(body);
  });

  it("declares S20/S35 nursing continuity coverage only with nursing facts, followup asset and backflow", () => {
    const evidence = nursingContinuityEvidenceResult(nursingContinuityEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S20", "S35"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual(["FOLLOWUP"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "CLINICAL_RUNTIME",
    ]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S20__NORMAL",
      "S35__ABNORMAL",
    ]);
    expect(evidence.launchCoverage.deliveryShapes).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S20/S35 condition rows without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = nursingContinuityEvidence;
    const evidence = nursingContinuityEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S20", "S35"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      code: "S20__NORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        scenarioConditionEvidence: [
          {
            code: "S20__HIGH_RISK",
            scenarioCode: "S20",
            condition: "HIGH_RISK",
            source: "NURSING_CONTINUITY_FOLLOWUP_PLAN_RESULT_BACKFLOW",
            evidence: ["护理连续照护正常闭环不能冒领高危态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      code: "S35__ABNORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        scenarioConditionEvidence: [
          {
            code: "S35__ABNORMAL",
            scenarioCode: "S35",
            condition: "ABNORMAL",
            source: "NURSING_CONTINUITY_FOLLOWUP_PLAN_RESULT_BACKFLOW",
            evidence: ["S35 异常行来源错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      code: "S20__NORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        scenarioConditionEvidence: [
          {
            code: "S20__NORMAL",
            scenarioCode: "S20",
            condition: "NORMAL",
            source: "NURSING_CONTINUITY_FOLLOWUP_PLAN_RESULT_BACKFLOW",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "FOLLOWUP 资产未激活",
      code: "S20__NORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        runtime: {
          ...structuredClone(nursingContinuityEvidence.runtime),
          followupAsset: {
            ...structuredClone(nursingContinuityEvidence.runtime.followupAsset),
            entryState: "DRAFT",
          },
        },
      },
    },
    {
      name: "随访计划未由真实前台生成完成问卷",
      code: "S20__NORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        questionnaire: {
          ...structuredClone(nursingContinuityEvidence.questionnaire),
          status: "DISPATCHED",
        },
      },
    },
    {
      name: "随访结果回流未生成 FollowUp 资源",
      code: "S20__NORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        backflowContext: {
          ...structuredClone(nursingContinuityEvidence.backflowContext),
          resources: {
            followUps: [],
          },
        },
      },
    },
    {
      name: "护理评估不是高风险",
      code: "S35__ABNORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        clinicalContext: {
          ...structuredClone(nursingContinuityEvidence.clinicalContext),
          resources: {
            ...structuredClone(nursingContinuityEvidence.clinicalContext.resources),
            nursingAssessments: [
              {
                ...structuredClone(
                  nursingContinuityEvidence.clinicalContext.resources.nursingAssessments[0],
                ),
                riskLevel: "LOW",
              },
            ],
          },
        },
      },
    },
    {
      name: "随访计划解释未消费护理计划",
      code: "S35__ABNORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        followupPlan: {
          ...structuredClone(nursingContinuityEvidence.followupPlan),
          generationExplanation: {
            ...structuredClone(nursingContinuityEvidence.followupPlan.generationExplanation),
            carePlanEvidence: [],
          },
        },
      },
    },
    {
      name: "随访计划解释中的护理评估不是高风险",
      code: "S35__ABNORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        followupPlan: {
          ...structuredClone(nursingContinuityEvidence.followupPlan),
          generationExplanation: {
            ...structuredClone(nursingContinuityEvidence.followupPlan.generationExplanation),
            nursingAssessmentEvidence: [
              {
                ...structuredClone(
                  nursingContinuityEvidence.followupPlan.generationExplanation
                    .nursingAssessmentEvidence[0],
                ),
                riskLevel: "LOW",
              },
            ],
          },
        },
      },
    },
    {
      name: "异常回院没有通知事件",
      code: "S35__ABNORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        abnormalReport: {
          ...structuredClone(nursingContinuityEvidence.abnormalReport),
          notificationEventId: "",
        },
      },
    },
    {
      name: "异常回院未绑定 RETURN_VISIT 任务",
      code: "S35__ABNORMAL" as const,
      body: {
        ...structuredClone(nursingContinuityEvidence),
        abnormalReport: {
          ...structuredClone(nursingContinuityEvidence.abnormalReport),
          returnTaskId: "ft-other-task",
        },
      },
    },
  ])("does not declare nursing continuity condition rows when $name", ({ body, code }) => {
    expectNoNursingContinuityScenarioConditionCoverage(body, code);
  });

  it.each([
    {
      name: "缺少 NursingAssessment 护理评估事实",
      body: {
        ...nursingContinuityEvidence,
        clinicalContext: {
          ...nursingContinuityEvidence.clinicalContext,
          resources: {
            ...nursingContinuityEvidence.clinicalContext.resources,
            nursingAssessments: [],
          },
        },
      },
    },
    {
      name: "缺少 CarePlan 护理计划事实",
      body: {
        ...nursingContinuityEvidence,
        clinicalContext: {
          ...nursingContinuityEvidence.clinicalContext,
          resources: {
            ...nursingContinuityEvidence.clinicalContext.resources,
            carePlans: [],
          },
        },
      },
    },
    {
      name: "当前机构生效版本未包含 FOLLOWUP 资产",
      body: {
        ...nursingContinuityEvidence,
        runtime: {
          ...nursingContinuityEvidence.runtime,
          followupAsset: {
            ...nursingContinuityEvidence.runtime.followupAsset,
            entryState: "DISABLED",
          },
        },
      },
    },
    {
      name: "激活请求未选择本轮 FOLLOWUP 资产",
      body: {
        ...nursingContinuityEvidence,
        activationRequest: {
          activeAssets: [],
        },
      },
    },
    {
      name: "随访计划解释未消费 NursingAssessment 风险等级",
      body: {
        ...nursingContinuityEvidence,
        followupPlan: {
          ...nursingContinuityEvidence.followupPlan,
          generationExplanation: {
            ...nursingContinuityEvidence.followupPlan.generationExplanation,
            nursingAssessmentEvidence: [],
          },
        },
      },
    },
    {
      name: "随访计划解释未消费 CarePlan 节点",
      body: {
        ...nursingContinuityEvidence,
        followupPlan: {
          ...nursingContinuityEvidence.followupPlan,
          generationExplanation: {
            ...nursingContinuityEvidence.followupPlan.generationExplanation,
            carePlanEvidence: [],
          },
        },
      },
    },
    {
      name: "随访计划解释缺少 FOLLOWUP 运行资产证据",
      body: {
        ...nursingContinuityEvidence,
        followupPlan: {
          ...nursingContinuityEvidence.followupPlan,
          generationExplanation: {
            ...nursingContinuityEvidence.followupPlan.generationExplanation,
            runtimeAssetEvidence: [],
          },
        },
      },
    },
    {
      name: "没有问卷完成证据",
      body: {
        ...nursingContinuityEvidence,
        questionnaire: {
          ...nursingContinuityEvidence.questionnaire,
          status: "DISPATCHED",
        },
      },
    },
    {
      name: "没有异常回院证据",
      body: {
        ...nursingContinuityEvidence,
        abnormalReport: undefined,
      },
    },
    {
      name: "结果回流未生成上下文",
      body: {
        ...nursingContinuityEvidence,
        resultBackflow: {
          ...nursingContinuityEvidence.resultBackflow,
          contextSnapshotId: "",
        },
      },
    },
    {
      name: "回流上下文未回读 FollowUp 标准资源",
      body: {
        ...nursingContinuityEvidence,
        backflowContext: {
          ...nursingContinuityEvidence.backflowContext,
          resources: {
            followUps: [],
          },
        },
      },
    },
    {
      name: "scopeStatement 没有限定代表切片边界",
      body: {
        ...nursingContinuityEvidence,
        scopeStatement: "护理连续照护已完整上线",
      },
    },
  ])("does not declare nursing continuity coverage when $name", ({ body }) => {
    expectNoNursingContinuityCoverage(body);
  });

  it("declares S18/S31 pharmacy-review antimicrobial coverage only with bidirectional review, runtime assets and rectification closure", () => {
    const evidence = pharmacyReviewAntimicrobialEvidenceResult(pharmacyReviewAntimicrobialEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S18", "S31"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "TERMINOLOGY",
      "SAFETY",
      "CDSS_RISK",
      "RULE",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
      "PROFESSIONAL_COLLABORATION",
      "QUALITY_IMPROVEMENT",
    ]);
    expect(
      evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code),
    ).toEqual(["PHARMACY_REVIEW"]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S18__HIGH_RISK",
      "S31__DEGRADATION",
      "S31__ABNORMAL",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S18/S31 condition rows without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = pharmacyReviewAntimicrobialEvidence;
    const evidence = pharmacyReviewAntimicrobialEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scenarioConditionEvidence: [
          {
            code: "S31__NORMAL",
            scenarioCode: "S31",
            condition: "NORMAL",
            source: "PHARMACY_REVIEW_RECTIFICATION_REVIEW",
            evidence: ["药学审方断连与整改链路不能冒领普通正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scenarioConditionEvidence: [
          {
            code: "S18__HIGH_RISK",
            scenarioCode: "S18",
            condition: "HIGH_RISK",
            source: "PHARMACY_REVIEW_RECTIFICATION_REVIEW",
            evidence: ["来源错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scenarioConditionEvidence: [
          {
            code: "S31__DEGRADATION",
            scenarioCode: "S31",
            condition: "DEGRADATION",
            source: "PHARMACY_REVIEW_OUTBOUND_NOT_CONNECTED",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "高危风险矩阵允许自动执行",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        riskMatrix: {
          ...pharmacyReviewAntimicrobialEvidence.riskMatrix,
          autoExecutionAllowed: true,
        },
      },
    },
    {
      name: "高危红线不是 CRITICAL",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        safetyRedline: {
          ...pharmacyReviewAntimicrobialEvidence.safetyRedline,
          hazardSeverity: "HIGH",
        },
      },
    },
    {
      name: "药师复核直接关闭医生确认链路",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        feedback: {
          ...pharmacyReviewAntimicrobialEvidence.feedback,
          pharmacist: {
            ...pharmacyReviewAntimicrobialEvidence.feedback.pharmacist,
            cardStatus: "ACCEPTED",
          },
        },
      },
    },
    {
      name: "医生未确认采纳",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        feedback: {
          ...pharmacyReviewAntimicrobialEvidence.feedback,
          physician: {
            ...pharmacyReviewAntimicrobialEvidence.feedback.physician,
            cardStatus: "PENDING",
          },
        },
      },
    },
    {
      name: "反馈链路允许自动开嘱",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        feedback: {
          ...pharmacyReviewAntimicrobialEvidence.feedback,
          noAutoOrder: false,
        },
      },
    },
    {
      name: "出站审方未收敛到 NOT_CONNECTED",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        outboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.outboundReview,
          status: "SUCCESS",
          compensationStatus: "CONNECTED",
          compensationRequired: false,
        },
      },
    },
    {
      name: "出站断连阻断主链路",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        outboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.outboundReview,
          blocksMainFlow: true,
        },
      },
    },
    {
      name: "整改未复核通过",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        qualityRectification: {
          ...pharmacyReviewAntimicrobialEvidence.qualityRectification,
          reviewDecision: "REJECTED",
        },
      },
    },
    {
      name: "整改未绑定本轮推荐卡",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        qualityRectification: {
          ...pharmacyReviewAntimicrobialEvidence.qualityRectification,
          sourceId: "other-card",
        },
      },
    },
  ])("does not declare S18/S31 scenario condition rows when $name", ({ body }) => {
    const evidence = pharmacyReviewAntimicrobialEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "缺少 PHARMACY_REVIEW 适配器证据",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        adapter: {
          ...pharmacyReviewAntimicrobialEvidence.adapter,
          systemFamilyCode: "LIS_MONITORING_CRITICAL",
        },
      },
    },
    {
      name: "术语门禁缺少 ICD-10 感染诊断覆盖",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        terminologyGate: {
          ...pharmacyReviewAntimicrobialEvidence.terminologyGate,
          diagnosis: undefined,
        },
      },
    },
    {
      name: "临床上下文缺少 Observation 监测指标事实",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        clinicalContext: {
          ...pharmacyReviewAntimicrobialEvidence.clinicalContext,
          resources: {
            ...pharmacyReviewAntimicrobialEvidence.clinicalContext.resources,
            observations: [],
          },
        },
      },
    },
    {
      name: "出站审方请求为 FAILED 未收敛到 NOT_CONNECTED",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        outboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.outboundReview,
          status: "FAILED",
          compensationStatus: "FAILED",
        },
      },
    },
    {
      name: "出站审方请求会阻断主链路",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        outboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.outboundReview,
          blocksMainFlow: true,
        },
      },
    },
    {
      name: "出站审方请求缺少感染诊断和监测指标依据",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        outboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.outboundReview,
          payload: {
            patientId: "mpi-pharmacy-review",
            contextSnapshotId: "ctx-pharmacy-review",
            medicationCode: "J01C",
          },
        },
      },
    },
    {
      name: "缺少审方结果签名入站证据",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        inboundReview: undefined,
      },
    },
    {
      name: "入站审方未绑定同 trace 与本轮上下文",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        inboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.inboundReview,
          traceId: "trace-other",
          contextSnapshotId: "ctx-other",
        },
      },
    },
    {
      name: "入站审方映射缺少感染诊断归一结果",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        inboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.inboundReview,
          mappedPayload: {
            ...pharmacyReviewAntimicrobialEvidence.inboundReview.mappedPayload,
            conditions: [],
          },
        },
      },
    },
    {
      name: "入站临床事件未处理到 PROCESSED",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        inboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.inboundReview,
          clinicalEvent: {
            ...pharmacyReviewAntimicrobialEvidence.inboundReview.clinicalEvent,
            status: "RECEIVED",
          },
        },
      },
    },
    {
      name: "入站临床事件处理失败仍不得声明覆盖",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        inboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.inboundReview,
          clinicalEvent: {
            ...pharmacyReviewAntimicrobialEvidence.inboundReview.clinicalEvent,
            status: "FAILED",
            errorCode: "ENG-API-002",
            errorClass: "VALIDATION_FAILED",
          },
        },
      },
    },
    {
      name: "入站临床事件未绑定本轮 runtime",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        inboundReview: {
          ...pharmacyReviewAntimicrobialEvidence.inboundReview,
          clinicalEvent: {
            ...pharmacyReviewAntimicrobialEvidence.inboundReview.clinicalEvent,
            runtimeReleaseId: "runtime-other",
          },
        },
      },
    },
    {
      name: "当前机构生效版本缺少 ACTION_CARD 资产",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        runtime: {
          ...pharmacyReviewAntimicrobialEvidence.runtime,
          actionCardAsset: {
            ...pharmacyReviewAntimicrobialEvidence.runtime.actionCardAsset,
            entryState: "DISABLED",
          },
        },
      },
    },
    {
      name: "红线推荐卡附件手工拼入 ACTION_CARD 但规则推荐缺少真实物化证据",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        ruleRecommendation: {
          ...pharmacyReviewAntimicrobialEvidence.ruleRecommendation,
          explanation: {
            ...pharmacyReviewAntimicrobialEvidence.ruleRecommendation.explanation,
            ruleExplanation: {
              ...pharmacyReviewAntimicrobialEvidence.ruleRecommendation.explanation.ruleExplanation,
              runtimeAssetEvidence: [],
            },
          },
        },
        recommendation: {
          ...pharmacyReviewAntimicrobialEvidence.recommendation,
          explanation: {
            ...pharmacyReviewAntimicrobialEvidence.recommendation.explanation,
            runtimeAssetEvidence: [
              {
                assetType: "ACTION_CARD",
                assetIdentity: "ACTION_CARD.PHARMACY_REVIEW.ANTIMICROBIAL",
                assetVersion: "V1",
                contentHash: "9".repeat(64),
                requiresPhysicianConfirmation: true,
              },
            ],
          },
        },
      },
    },
    {
      name: "规则推荐解释未消费 Observation 和 Condition",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        ruleRecommendation: {
          ...pharmacyReviewAntimicrobialEvidence.ruleRecommendation,
          explanation: {
            ...pharmacyReviewAntimicrobialEvidence.ruleRecommendation.explanation,
            ruleExplanation: {
              ...pharmacyReviewAntimicrobialEvidence.ruleRecommendation.explanation.ruleExplanation,
              conditionEvidence: [
                {
                  fact: "medications[].code",
                  operator: "contains",
                  expected: "J01C",
                  actual: ["J01C"],
                  matched: true,
                },
              ],
            },
          },
        },
      },
    },
    {
      name: "没有药师与医生双人工闭环",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        feedback: {
          ...pharmacyReviewAntimicrobialEvidence.feedback,
          pharmacist: {
            ...pharmacyReviewAntimicrobialEvidence.feedback.pharmacist,
            cardStatus: "VIEWED",
          },
        },
      },
    },
    {
      name: "药师医生业务角色只有附件常量没有详情回读证据",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        feedback: {
          ...pharmacyReviewAntimicrobialEvidence.feedback,
          pharmacist: {
            feedbackId: "rf-pharmacy-pharmacist",
            cardStatus: "PENDING",
            operatorRole: "PHARMACIST",
            roleEvidence: "BUSINESS_FEEDBACK_ROLE_ONLY",
            reasonCode: "PHARMACIST_REVIEWED",
          },
        },
      },
    },
    {
      name: "反馈闭环缺少本轮 ACTION_CARD 不自动开嘱运行证据",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        feedback: {
          ...pharmacyReviewAntimicrobialEvidence.feedback,
          actionCardEvidence: undefined,
        },
      },
    },
    {
      name: "红线推荐卡详情未回读医师确认或 AI 标识错误",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        recommendation: {
          ...pharmacyReviewAntimicrobialEvidence.recommendation,
          requiresPhysicianConfirmation: false,
          aiGenerated: true,
        },
      },
    },
    {
      name: "整改任务未闭环",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        qualityRectification: {
          ...pharmacyReviewAntimicrobialEvidence.qualityRectification,
          taskStatus: "SUBMITTED",
        },
      },
    },
    {
      name: "整改闭环使用非 canonical 职责账号",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        qualityRectification: {
          ...pharmacyReviewAntimicrobialEvidence.qualityRectification,
          submittedByRole: "quality-controller",
          reviewedByRole: "quality-controller",
        },
      },
    },
    {
      name: "scopeStatement 缺少完整抗菌药物分级管理边界",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scopeStatement:
          "药房审方与抗菌药物治理代表切片：PHARMACY_REVIEW 双向审方，不代表完整药事治理或第三方药房审方系统族完整覆盖。",
      },
    },
    {
      name: "scopeStatement 过度声明完整药事治理",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scopeStatement: "完整药事治理和完整第三方药房审方系统族已上线。",
      },
    },
    {
      name: "scopeStatement 混合代表切片与完整抗菌药物分级管理过度声明",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scopeStatement:
          "药房审方与抗菌药物治理代表切片：PHARMACY_REVIEW 双向审方，不代表完整药事治理；完整抗菌药物分级管理已上线；不代表第三方药房审方系统族完整覆盖。",
      },
    },
    {
      name: "scopeStatement 同一分句重复完整抗菌药物分级管理并过度声明",
      body: {
        ...pharmacyReviewAntimicrobialEvidence,
        scopeStatement:
          "药房审方与抗菌药物治理代表切片：PHARMACY_REVIEW 双向审方，不代表完整药事治理，不代表完整抗菌药物分级管理，且完整抗菌药物分级管理已上线，不代表第三方药房审方系统族完整覆盖。",
      },
    },
  ])("does not declare pharmacy-review antimicrobial coverage when $name", ({ body }) => {
    expectNoPharmacyReviewAntimicrobialCoverage(body);
  });

  it("declares S21/S32 infection public-health safety coverage only with signed inbound, runtime assets and rectification closure", () => {
    const evidence = infectionPublicHealthSafetyEvidenceResult(infectionPublicHealthSafetyEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S21", "S32"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "TERMINOLOGY",
      "RULE",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
      "PROFESSIONAL_COLLABORATION",
      "QUALITY_IMPROVEMENT",
    ]);
    expect(
      evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code),
    ).toEqual(["PUBLIC_HEALTH_INFECTION_REGULATORY"]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S21__HIGH_RISK",
      "S21__DEGRADATION",
      "S32__ABNORMAL",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S21/S32 condition rows without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = infectionPublicHealthSafetyEvidence;
    const evidence = infectionPublicHealthSafetyEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        scenarioConditionEvidence: [
          {
            code: "S21__NORMAL",
            scenarioCode: "S21",
            condition: "NORMAL",
            source: "INFECTION_PUBLIC_HEALTH_MANUAL_REPORT_CONFIRMATION",
            evidence: ["院感公卫阳性入站和断连补偿不能冒领普通正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        scenarioConditionEvidence: [
          {
            code: "S32__ABNORMAL",
            scenarioCode: "S32",
            condition: "ABNORMAL",
            source: "PUBLIC_HEALTH_OUTBOUND_NOT_CONNECTED",
            evidence: ["来源错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        scenarioConditionEvidence: [
          {
            code: "S21__DEGRADATION",
            scenarioCode: "S21",
            condition: "DEGRADATION",
            source: "PUBLIC_HEALTH_OUTBOUND_NOT_CONNECTED",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "阳性诊断不是 U07.100",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        clinicalContext: {
          ...infectionPublicHealthSafetyEvidence.clinicalContext,
          resources: {
            ...infectionPublicHealthSafetyEvidence.clinicalContext.resources,
            conditions: [
              {
                ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.conditions[0],
                code: "J18.900",
              },
            ],
          },
        },
      },
    },
    {
      name: "上报预填缺少疑似新冠可报告病种",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        clinicalContext: {
          ...infectionPublicHealthSafetyEvidence.clinicalContext,
          resources: {
            ...infectionPublicHealthSafetyEvidence.clinicalContext.resources,
            extensions: {
              local: {
                ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local,
                publicHealthReport: {
                  ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local
                    .publicHealthReport,
                  reportableCondition: "NOT_REPORTABLE",
                },
              },
            },
          },
        },
      },
    },
    {
      name: "推荐卡不要求医生确认",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        recommendation: {
          ...infectionPublicHealthSafetyEvidence.recommendation,
          requiresPhysicianConfirmation: false,
        },
      },
    },
    {
      name: "人工确认声称替代法定上报",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        manualReview: {
          ...infectionPublicHealthSafetyEvidence.manualReview,
          noLegalAutoSubmit: false,
        },
      },
    },
    {
      name: "出站未收敛到 NOT_CONNECTED",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        outboundPrefill: {
          ...infectionPublicHealthSafetyEvidence.outboundPrefill,
          status: "SUCCESS",
          compensationStatus: "CONNECTED",
          compensationRequired: false,
        },
      },
    },
    {
      name: "出站断连阻断主链路",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        outboundPrefill: {
          ...infectionPublicHealthSafetyEvidence.outboundPrefill,
          blocksMainFlow: true,
        },
      },
    },
    {
      name: "安全事件风险不是 HIGH",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        clinicalContext: {
          ...infectionPublicHealthSafetyEvidence.clinicalContext,
          resources: {
            ...infectionPublicHealthSafetyEvidence.clinicalContext.resources,
            extensions: {
              local: {
                ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local,
                safetyEvent: {
                  ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local
                    .safetyEvent,
                  riskLevel: "LOW",
                },
              },
            },
          },
        },
      },
    },
    {
      name: "安全事件根因不是隔离流程缺口",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        clinicalContext: {
          ...infectionPublicHealthSafetyEvidence.clinicalContext,
          resources: {
            ...infectionPublicHealthSafetyEvidence.clinicalContext.resources,
            extensions: {
              local: {
                ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local,
                safetyEvent: {
                  ...infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local
                    .safetyEvent,
                  rootCause: "UNKNOWN",
                },
              },
            },
          },
        },
      },
    },
    {
      name: "整改未复核通过",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        qualityRectification: {
          ...infectionPublicHealthSafetyEvidence.qualityRectification,
          reviewDecision: "REJECTED",
        },
      },
    },
    {
      name: "整改未绑定本轮推荐卡",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        qualityRectification: {
          ...infectionPublicHealthSafetyEvidence.qualityRectification,
          sourceId: "other-card",
        },
      },
    },
  ])("does not declare S21/S32 scenario condition rows when $name", ({ body }) => {
    const evidence = infectionPublicHealthSafetyEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it("declares S21/S32 coverage when legal auto-submit evidence is carried by action card and manual review", () => {
    const runtimeAssetEvidence = [
      {
        assetType: "ACTION_CARD",
        assetIdentity: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
        actionCardRef: "ACTION_CARD.PUBLIC_HEALTH.INFECTION.REPORT_PREFILL",
        assetVersion: "V1",
        resolvedActionCardVersion: "V1",
        runtimeReleaseId: "runtime-public-health-infection",
        contentHash: "b".repeat(64),
        resolvedActionCardHash: "b".repeat(64),
        requiresPhysicianConfirmation: true,
      },
    ];
    const evidence = infectionPublicHealthSafetyEvidenceResult({
      ...infectionPublicHealthSafetyEvidence,
      recommendation: {
        ...infectionPublicHealthSafetyEvidence.recommendation,
        explanation: {
          ...infectionPublicHealthSafetyEvidence.recommendation.explanation,
          ruleExplanation: {
            ...infectionPublicHealthSafetyEvidence.recommendation.explanation.ruleExplanation,
            runtimeAssetEvidence,
          },
        },
      },
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S21", "S32"]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "TERMINOLOGY",
      "RULE",
      "ACTION_CARD",
    ]);
  });

  it.each([
    {
      name: "缺少 PUBLIC_HEALTH_INFECTION_REGULATORY 适配器证据",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        adapter: {
          ...infectionPublicHealthSafetyEvidence.adapter,
          systemFamilyCode: "PHARMACY_REVIEW",
        },
      },
    },
    {
      name: "入站报告缺少上报预填人工审核边界",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        inboundReport: {
          ...infectionPublicHealthSafetyEvidence.inboundReport,
          mappedPayload: {
            ...infectionPublicHealthSafetyEvidence.inboundReport.mappedPayload,
            publicHealthReport: {
              reportType: "INFECTIOUS_DISEASE_PREFILL",
              manualSubmitRequired: false,
              legalSubmissionDelegated: true,
            },
          },
        },
      },
    },
    {
      name: "临床上下文缺少 safetyEvent 整改扩展证据",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        clinicalContext: {
          ...infectionPublicHealthSafetyEvidence.clinicalContext,
          resources: {
            ...infectionPublicHealthSafetyEvidence.clinicalContext.resources,
            extensions: {
              local: {
                publicHealthReport:
                  infectionPublicHealthSafetyEvidence.clinicalContext.resources.extensions.local
                    .publicHealthReport,
              },
            },
          },
        },
      },
    },
    {
      name: "入站临床事件未处理到 PROCESSED",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        inboundReport: {
          ...infectionPublicHealthSafetyEvidence.inboundReport,
          clinicalEvent: {
            ...infectionPublicHealthSafetyEvidence.inboundReport.clinicalEvent,
            status: "FAILED",
            errorCode: "ENG-EVENT-005",
            errorClass: "DOWNSTREAM",
          },
        },
      },
    },
    {
      name: "推荐卡没有动作卡物化证据",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        recommendation: {
          ...infectionPublicHealthSafetyEvidence.recommendation,
          explanation: {
            ...infectionPublicHealthSafetyEvidence.recommendation.explanation,
            ruleExplanation: {
              ...infectionPublicHealthSafetyEvidence.recommendation.explanation.ruleExplanation,
              runtimeAssetEvidence: [],
            },
          },
        },
      },
    },
    {
      name: "人工确认声称系统已替代法定上报",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        manualReview: {
          ...infectionPublicHealthSafetyEvidence.manualReview,
          noLegalAutoSubmit: false,
        },
      },
    },
    {
      name: "医疗安全事件整改任务未关闭",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        qualityRectification: {
          ...infectionPublicHealthSafetyEvidence.qualityRectification,
          taskStatus: "SUBMITTED",
        },
      },
    },
    {
      name: "scopeStatement 过度声明完整法定上报",
      body: {
        ...infectionPublicHealthSafetyEvidence,
        scopeStatement: "完整院感系统和完整公卫法定上报已上线。",
      },
    },
  ])("does not declare infection public-health safety coverage when $name", ({ body }) => {
    expectNoInfectionPublicHealthSafetyCoverage(body);
  });

  it("declares S26 surgery anesthesia transfusion coverage only with signed inbound, runtime assets, manual confirmation and rectification closure", () => {
    const evidence = surgeryAnesthesiaTransfusionEvidenceResult(
      surgeryAnesthesiaTransfusionEvidence,
    );

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S26"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "TERMINOLOGY",
      "SAFETY",
      "CDSS_RISK",
      "RULE",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
      "PROFESSIONAL_COLLABORATION",
      "QUALITY_IMPROVEMENT",
    ]);
    expect(
      evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code),
    ).toEqual(["NURSING_ANESTHESIA_TRANSFUSION_ICU"]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S26__HIGH_RISK",
      "S26__DEGRADATION",
      "S26__ABNORMAL",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S26 condition rows without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = surgeryAnesthesiaTransfusionEvidence;
    const evidence = surgeryAnesthesiaTransfusionEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scenarioConditionEvidence: [
          {
            code: "S26__NORMAL",
            scenarioCode: "S26",
            condition: "NORMAL",
            source: "SURGERY_TIMELINE_RECTIFICATION_REVIEW",
            evidence: ["高危围手术期代表切片不能冒领普通正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scenarioConditionEvidence: [
          {
            code: "S26__HIGH_RISK",
            scenarioCode: "S26",
            condition: "HIGH_RISK",
            source: "SURGERY_TIMELINE_RECTIFICATION_REVIEW",
            evidence: ["来源错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scenarioConditionEvidence: [
          {
            code: "S26__HIGH_RISK",
            scenarioCode: "S26",
            condition: "HIGH_RISK",
            source: "SURGERY_ANESTHESIA_TRANSFUSION_CRITICAL_MANUAL_CONFIRMATION",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "高危风险矩阵允许自动执行",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        riskMatrix: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.riskMatrix),
          autoExecutionAllowed: true,
        },
      },
    },
    {
      name: "高危红线不是 CRITICAL",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        safetyRedline: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.safetyRedline),
          hazardSeverity: "HIGH",
        },
      },
    },
    {
      name: "推荐卡不要求医师确认",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        recommendation: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.recommendation),
          requiresPhysicianConfirmation: false,
        },
      },
    },
    {
      name: "人工确认未接受推荐卡",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        manualConfirmation: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.manualConfirmation),
          cardStatus: "PENDING",
        },
      },
    },
    {
      name: "出站未收敛到 NOT_CONNECTED",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        outboundChecklist: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.outboundChecklist),
          status: "SUCCESS",
          compensationStatus: "CONNECTED",
          compensationRequired: false,
        },
      },
    },
    {
      name: "出站断连阻断本地主链路",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        outboundChecklist: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.outboundChecklist),
          blocksMainFlow: true,
        },
      },
    },
    {
      name: "整改未复核通过",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        qualityRectification: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.qualityRectification),
          reviewDecision: "REJECTED",
        },
      },
    },
    {
      name: "整改未绑定本轮推荐卡",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        qualityRectification: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.qualityRectification),
          sourceId: "other-card",
        },
      },
    },
  ])("does not declare S26 scenario condition rows when $name", ({ body }) => {
    const evidence = surgeryAnesthesiaTransfusionEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "缺 CDSS_RISK 风险矩阵资产",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        versionedAssets: ["TERMINOLOGY", "SAFETY", "RULE", "ACTION_CARD"],
      },
    },
    {
      name: "术语 mappingId 未绑定本轮 localTermId/standardTermId/sourceSystem",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        terminologyGate: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.terminologyGate),
          confirmedMapping: {
            ...structuredClone(
              surgeryAnesthesiaTransfusionEvidence.terminologyGate.confirmedMapping,
            ),
            localTermId: 9999,
          },
        },
      },
    },
    {
      name: "缺 Procedure 标准资源",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        clinicalContext: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.clinicalContext),
          resources: {
            ...structuredClone(surgeryAnesthesiaTransfusionEvidence.clinicalContext.resources),
            procedures: [],
          },
        },
      },
    },
    {
      name: "入站临床事件未处理完成",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        inboundSurgeryEvent: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.inboundSurgeryEvent),
          clinicalEvent: {
            ...structuredClone(
              surgeryAnesthesiaTransfusionEvidence.inboundSurgeryEvent.clinicalEvent,
            ),
            status: "FAILED",
            errorCode: "ENG-API-002",
          },
        },
      },
    },
    {
      name: "出站断连阻断主流程",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        outboundChecklist: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.outboundChecklist),
          blocksMainFlow: true,
        },
      },
    },
    {
      name: "人工确认缺少禁止自动输血证据",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        manualConfirmation: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.manualConfirmation),
          noAutoTransfusion: false,
        },
      },
    },
    {
      name: "动作卡资产缺少禁止自动手术治理要求",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        actionCard: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.actionCard),
          noAutoSurgery: false,
        },
      },
    },
    {
      name: "人工确认把手术医生业务任职写成系统角色",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        manualConfirmation: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.manualConfirmation),
          persisted: {
            ...structuredClone(surgeryAnesthesiaTransfusionEvidence.manualConfirmation.persisted),
            operatorRole: "SURGEON",
          },
        },
      },
    },
    {
      name: "整改任务未关闭",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        qualityRectification: {
          ...structuredClone(surgeryAnesthesiaTransfusionEvidence.qualityRectification),
          taskStatus: "OPEN",
        },
      },
    },
    {
      name: "scope 过度宣称完整上线",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，完整手麻系统已上线、完整输血系统已上线、完整上线验收已完成。",
      },
    },
    {
      name: "scope 过度宣称护理手麻手术室输血 ICU 第三方系统族完整覆盖",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统或完整上线验收；护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖已完成。",
      },
    },
    {
      name: "scope 过度宣称完整上线完成",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收；完整上线已完成。",
      },
    },
    {
      name: "scope 过度宣称完整 S0-S40 已上线",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收；完整 S0-S40 已上线。",
      },
    },
    {
      name: "scope 过度宣称完整第三方系统族覆盖已完成",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收；完整第三方系统族覆盖已完成。",
      },
    },
    {
      name: "scope 过度宣称所有第三方系统族完整覆盖已完成",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收；所有第三方系统族完整覆盖已完成。",
      },
    },
    {
      name: "scope 过度宣称完整手麻手术室输血系统已上线",
      body: {
        ...structuredClone(surgeryAnesthesiaTransfusionEvidence),
        scopeStatement:
          "围手术期、麻醉与输血代表切片，不代表完整围手术期系统、完整手麻系统、完整手术室系统、完整输血系统、护理、手麻、手术室、输血和 ICU 第三方系统族完整覆盖或完整上线验收；完整手麻手术室输血系统已上线。",
      },
    },
  ])("does not declare surgery anesthesia transfusion coverage when $name", ({ body }) => {
    expectNoSurgeryAnesthesiaTransfusionCoverage(body);
  });

  it("declares S19/S24/S27 critical emergency ICU coverage only with signed monitoring inbound, runtime assets, manual escalation and todo closure", () => {
    const evidence = criticalEmergencyIcuEvidenceResult(criticalEmergencyIcuEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual([
      "S19",
      "S24",
      "S27",
    ]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
      "DATA_INTEROPERABILITY",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "TERMINOLOGY",
      "CDSS_RISK",
      "RULE",
      "PATHWAY",
      "ACTION_CARD",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual(["API_EVENT"]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "THIRD_PARTY_INTERFACE",
      "CLINICAL_RUNTIME",
      "PROFESSIONAL_COLLABORATION",
    ]);
    expect(
      evidence.launchCoverage.thirdPartySystemFamilyConsumerSlices?.map((item) => item.code),
    ).toEqual(["LIS_MONITORING_CRITICAL"]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S19__HIGH_RISK",
      "S24__HIGH_RISK",
      "S27__HIGH_RISK",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S19/S24/S27 condition rows without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = criticalEmergencyIcuEvidence;
    const evidence = criticalEmergencyIcuEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        scenarioConditionEvidence: [
          {
            code: "S19__NORMAL",
            scenarioCode: "S19",
            condition: "NORMAL",
            source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
            evidence: ["急危重高危切片不能冒领普通正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        scenarioConditionEvidence: [
          {
            code: "S24__HIGH_RISK",
            scenarioCode: "S24",
            condition: "HIGH_RISK",
            source: "CRITICAL_EMERGENCY_ICU_NORMAL_TRIAGE",
            evidence: ["来源错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        scenarioConditionEvidence: [
          {
            code: "S27__HIGH_RISK",
            scenarioCode: "S27",
            condition: "HIGH_RISK",
            source: "CRITICAL_EMERGENCY_ICU_MANUAL_ESCALATION",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "风险矩阵不是 CRITICAL",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        riskMatrix: {
          ...structuredClone(criticalEmergencyIcuEvidence.riskMatrix),
          riskLevel: "HIGH",
        },
      },
    },
    {
      name: "风险矩阵允许自动执行",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        riskMatrix: {
          ...structuredClone(criticalEmergencyIcuEvidence.riskMatrix),
          autoExecutionAllowed: true,
        },
      },
    },
    {
      name: "动作卡不要求医生确认",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        actionCard: {
          ...structuredClone(criticalEmergencyIcuEvidence.actionCard),
          requiresPhysicianConfirmation: false,
        },
      },
    },
    {
      name: "人工确认未采纳",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        manualEscalation: {
          ...structuredClone(criticalEmergencyIcuEvidence.manualEscalation),
          cardStatus: "PENDING",
        },
      },
    },
    {
      name: "人工确认允许自动转 ICU",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        manualEscalation: {
          ...structuredClone(criticalEmergencyIcuEvidence.manualEscalation),
          noAutoTransfer: false,
        },
      },
    },
    {
      name: "人工确认允许控制设备",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        manualEscalation: {
          ...structuredClone(criticalEmergencyIcuEvidence.manualEscalation),
          noDeviceControl: false,
        },
      },
    },
    {
      name: "升级待办未完成",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        escalationTodo: {
          ...structuredClone(criticalEmergencyIcuEvidence.escalationTodo),
          status: "PENDING",
        },
      },
    },
    {
      name: "升级待办未绑定本轮推荐卡",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        escalationTodo: {
          ...structuredClone(criticalEmergencyIcuEvidence.escalationTodo),
          sourceId: "card-other",
        },
      },
    },
  ])("does not declare S19/S24/S27 scenario condition rows when $name", ({ body }) => {
    const evidence = criticalEmergencyIcuEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it("declares S19/S24/S27 critical emergency ICU coverage for the real frontdesk attachment shape", () => {
    const body = structuredClone(criticalEmergencyIcuEvidence);
    const mappedPayload = {
      ...body.inboundMonitoringEvent.mappedPayload,
      extensions: {
        local: {
          criticalCare: body.inboundMonitoringEvent.mappedPayload.criticalCare,
        },
      },
    } as Record<string, unknown>;
    delete mappedPayload.criticalCare;
    body.inboundMonitoringEvent.mappedPayload =
      mappedPayload as typeof body.inboundMonitoringEvent.mappedPayload;
    const signedPayload = {
      ...body.inboundMonitoringEvent.signedPayload,
      noDeviceControl: true,
    } as Record<string, unknown>;
    delete signedPayload.criticalCare;
    body.inboundMonitoringEvent.signedPayload =
      signedPayload as typeof body.inboundMonitoringEvent.signedPayload;
    const actionCardRuntimeEvidence =
      body.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.find(
        (item) => item.assetType === "ACTION_CARD",
      ) as Record<string, unknown>;
    delete actionCardRuntimeEvidence.noAutoOrder;
    delete actionCardRuntimeEvidence.noAutoTransfer;
    delete actionCardRuntimeEvidence.noDeviceControl;
    body.escalationTodo.priority = "HIGH";

    const evidence = criticalEmergencyIcuEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual([
      "S19",
      "S24",
      "S27",
    ]);
  });

  it.each([
    {
      name: "缺 PATHWAY 升级路径资产",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        versionedAssets: ["TERMINOLOGY", "CDSS_RISK", "RULE", "ACTION_CARD"],
      },
    },
    {
      name: "接入系统族不是 LIS_MONITORING_CRITICAL",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        monitoringAdapter: {
          ...structuredClone(criticalEmergencyIcuEvidence.monitoringAdapter),
          systemFamilyCode: "NURSING_ANESTHESIA_TRANSFUSION_ICU",
        },
      },
    },
    {
      name: "术语 mapping 未绑定本轮 localTermId/standardTermId/sourceSystem",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        terminologyGate: {
          ...structuredClone(criticalEmergencyIcuEvidence.terminologyGate),
          confirmedMapping: {
            ...structuredClone(criticalEmergencyIcuEvidence.terminologyGate.confirmedMapping),
            sourceSystem: "OTHER_SYSTEM",
          },
        },
      },
    },
    {
      name: "上下文缺少急诊分诊扩展",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        clinicalContext: {
          ...structuredClone(criticalEmergencyIcuEvidence.clinicalContext),
          resources: {
            ...structuredClone(criticalEmergencyIcuEvidence.clinicalContext.resources),
            extensions: {
              local: {
                criticalCare: {
                  ventilatorMode: "SIMV",
                  vasopressorRunning: true,
                  noDeviceControl: true,
                },
              },
            },
          },
        },
      },
    },
    {
      name: "入站监护事件未处理完成",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        inboundMonitoringEvent: {
          ...structuredClone(criticalEmergencyIcuEvidence.inboundMonitoringEvent),
          clinicalEvent: {
            ...structuredClone(criticalEmergencyIcuEvidence.inboundMonitoringEvent.clinicalEvent),
            status: "FAILED",
            errorCode: "ENG-API-002",
          },
        },
      },
    },
    {
      name: "推荐解释缺少路径运行资产证据",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        recommendation: {
          ...structuredClone(criticalEmergencyIcuEvidence.recommendation),
          explanation: {
            ...structuredClone(criticalEmergencyIcuEvidence.recommendation.explanation),
            ruleExplanation: {
              ...structuredClone(
                criticalEmergencyIcuEvidence.recommendation.explanation.ruleExplanation,
              ),
              runtimeAssetEvidence:
                criticalEmergencyIcuEvidence.recommendation.explanation.ruleExplanation.runtimeAssetEvidence.filter(
                  (item) => item.assetType !== "PATHWAY",
                ),
            },
          },
        },
      },
    },
    {
      name: "人工确认缺少不控制设备证据",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        manualEscalation: {
          ...structuredClone(criticalEmergencyIcuEvidence.manualEscalation),
          noDeviceControl: false,
        },
      },
    },
    {
      name: "人工确认反馈未绑定本轮推荐卡",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        manualEscalation: {
          ...structuredClone(criticalEmergencyIcuEvidence.manualEscalation),
          persisted: {
            ...structuredClone(criticalEmergencyIcuEvidence.manualEscalation.persisted),
            cardId: "card-other",
          },
        },
      },
    },
    {
      name: "升级待办未完成",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        escalationTodo: {
          ...structuredClone(criticalEmergencyIcuEvidence.escalationTodo),
          status: "PENDING",
        },
      },
    },
    {
      name: "升级待办未绑定本轮患者上下文",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        escalationTodo: {
          ...structuredClone(criticalEmergencyIcuEvidence.escalationTodo),
          patientId: "mpi-other",
        },
      },
    },
    {
      name: "scope 过度宣称完整急诊系统上线",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        scopeStatement:
          "急诊分诊与 ICU 生命支持风险代表切片，不代表完整 ICU 系统、完整生命支持系统、生命支持设备控制、完整 S19/S24/S27、完整 S0-S40 或完整上线验收；完整急诊系统已上线。",
      },
    },
    {
      name: "scope 过度宣称生命支持设备控制",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        scopeStatement:
          "急诊分诊与 ICU 生命支持风险代表切片，不代表完整急诊系统、完整 ICU 系统、完整生命支持系统、完整 S19/S24/S27、完整 S0-S40 或完整上线验收；生命支持设备控制已完成。",
      },
    },
    {
      name: "scope 过度宣称完整上线验收完成",
      body: {
        ...structuredClone(criticalEmergencyIcuEvidence),
        scopeStatement:
          "急诊分诊与 ICU 生命支持风险代表切片，不代表完整急诊系统、完整 ICU 系统、完整生命支持系统、生命支持设备控制、完整 S19/S24/S27 或完整 S0-S40；完整上线验收已完成。",
      },
    },
  ])("does not declare critical emergency ICU coverage when $name", ({ body }) => {
    expectNoCriticalEmergencyIcuCoverage(body);
  });

  const realFrontdeskScenarioEvidence = {
    scenarioCodes: ["S10", "S11", "S12"],
    clinicalContext: {
      patientId: "mpi-real-frontdesk-s12",
      encounterId: "enc-real-frontdesk-s12",
      contextSnapshotId: "ctx-real-frontdesk-s12",
    },
    followupTemplate: {
      operation: "CREATE_AND_PUBLISH_FOLLOWUP_TEMPLATE",
      createStatus: 201,
      publishStatus: 200,
      templateId: "fup-template-s12",
      templateCode: "FUP.REAL.FRONTDESK.S12",
      assetStatus: "PUBLISHED",
      scope: "HOSPITAL",
    },
    followupRuntime: {
      operation: "ACTIVATE_HOSPITAL_RUNTIME_WITH_FOLLOWUP",
      candidateStatus: 200,
      activationStatus: 200,
      runtimeReadbackStatus: 200,
      runtimeReleaseId: "runtime-followup-s12",
      assetType: "FOLLOWUP",
      assetIdentity: "FUP.REAL.FRONTDESK.S12",
      versionId: "av-followup-s12",
      sourceLayer: "HOSPITAL",
      entryState: "ACTIVE",
      currentRuntimeContainsAsset: true,
    },
    followupPlan: {
      operation: "GENERATE_FOLLOWUP_PLAN_FROM_CONTEXT",
      status: 200,
      planId: "followup-plan-s12",
      templateId: "fup-template-s12",
      templateCode: "FUP.REAL.FRONTDESK.S12",
      runtimeReleaseId: "runtime-followup-s12",
      patientId: "mpi-real-frontdesk-s12",
      encounterId: "enc-real-frontdesk-s12",
      contextSnapshotId: "ctx-real-frontdesk-s12",
      taskCount: 1,
      riskLevel: "MEDIUM",
    },
    questionnaire: {
      operation: "SUBMIT_FOLLOWUP_QUESTIONNAIRE",
      status: 200,
      planId: "followup-plan-s12",
      patientId: "mpi-real-frontdesk-s12",
      taskId: "followup-task-questionnaire-s12",
      questionnaireId: "followup-questionnaire-s12",
      responseStatus: "COMPLETED",
      source: "PATIENT_SELF_REPORT",
      submitted: true,
    },
    abnormalReturn: {
      operation: "REGISTER_ABNORMAL_RETURN",
      status: 200,
      planId: "followup-plan-s12",
      patientId: "mpi-real-frontdesk-s12",
      eventId: "followup-abnormal-event-s12",
      returnTaskId: "followup-return-task-s12",
      notificationEventId: "followup-notification-s12",
      riskLevel: "HIGH",
      registered: true,
      noAutoOrder: true,
    },
    scenarioConditionEvidence: [
      {
        code: "S12__NORMAL",
        scenarioCode: "S12",
        condition: "NORMAL",
        source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
        evidence: [
          "前台创建并发布 FOLLOWUP 随访方案后纳入当前机构生效版本",
          "临床用户基于当前上下文生成随访计划并完成问卷与异常回院登记",
        ],
      },
    ],
    scenarioEvidence: [
      { code: "S10", observedStages: ["前台执行医保审核并联动质量整改"] },
      {
        code: "S11",
        observedStages: ["前台创建发布并激活 CLAIM 评价指标", "前台提交并复核关闭质量整改任务"],
      },
      {
        code: "S12",
        observedStages: [
          "前台创建随访方案",
          "前台发布随访方案",
          "前台生成随访计划并完成问卷与异常回院登记",
        ],
      },
    ],
  };

  function realFrontdeskScenarioEvidenceResult(body: Record<string, unknown>) {
    return buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/real-frontdesk-rehearsal.spec.ts",
          title:
            "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
          status: "passed",
          attachments: [
            {
              name: "real-frontdesk-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify(body),
            },
          ],
        },
      ],
    });
  }

  function expectNoRealFrontdeskScenarioConditionCoverage(body: Record<string, unknown>) {
    const evidence = realFrontdeskScenarioEvidenceResult(body);
    expect(
      evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
    ).not.toContain("S12__NORMAL");
  }

  it("declares real-frontdesk scenario coverage only when the passed spec attaches complete scenario evidence", () => {
    const evidence = realFrontdeskScenarioEvidenceResult(realFrontdeskScenarioEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual([
      "S10",
      "S11",
      "S12",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares S12 normal condition row only from complete followup template, plan, questionnaire and abnormal-return evidence", () => {
    const evidence = realFrontdeskScenarioEvidenceResult(realFrontdeskScenarioEvidence);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S12__NORMAL",
    ]);
  });

  it("does not declare S12 normal condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = realFrontdeskScenarioEvidence;
    const evidence = realFrontdeskScenarioEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual([
      "S10",
      "S11",
      "S12",
    ]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        scenarioConditionEvidence: [
          {
            code: "S12__ABNORMAL",
            scenarioCode: "S12",
            condition: "ABNORMAL",
            source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
            evidence: ["正常随访主链路不能冒领异常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        scenarioConditionEvidence: [
          {
            code: "S12__NORMAL",
            scenarioCode: "S12",
            condition: "NORMAL",
            source: "FOLLOWUP_PAGE_VISIBLE_ONLY",
            evidence: ["不能只靠随访页面可见冒领正常态"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        scenarioConditionEvidence: [
          {
            code: "S12__NORMAL",
            scenarioCode: "S12",
            condition: "NORMAL",
            source: "FOLLOWUP_TEMPLATE_PLAN_QUESTIONNAIRE_ABNORMAL_RETURN",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "随访方案创建不是 2xx",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        followupTemplate: {
          ...structuredClone(realFrontdeskScenarioEvidence.followupTemplate),
          createStatus: 409,
        },
      },
    },
    {
      name: "随访方案未发布",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        followupTemplate: {
          ...structuredClone(realFrontdeskScenarioEvidence.followupTemplate),
          assetStatus: "DRAFT",
        },
      },
    },
    {
      name: "机构生效版本未包含 FOLLOWUP",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        followupRuntime: {
          ...structuredClone(realFrontdeskScenarioEvidence.followupRuntime),
          currentRuntimeContainsAsset: false,
        },
      },
    },
    {
      name: "计划生成机构版本不匹配",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        followupPlan: {
          ...structuredClone(realFrontdeskScenarioEvidence.followupPlan),
          runtimeReleaseId: "runtime-other-followup",
        },
      },
    },
    {
      name: "计划没有任务",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        followupPlan: {
          ...structuredClone(realFrontdeskScenarioEvidence.followupPlan),
          taskCount: 0,
        },
      },
    },
    {
      name: "问卷提交不是 2xx",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        questionnaire: {
          ...structuredClone(realFrontdeskScenarioEvidence.questionnaire),
          status: 503,
        },
      },
    },
    {
      name: "异常回院风险不是高风险",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        abnormalReturn: {
          ...structuredClone(realFrontdeskScenarioEvidence.abnormalReturn),
          riskLevel: "LOW",
        },
      },
    },
    {
      name: "异常回院允许自动开嘱",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        abnormalReturn: {
          ...structuredClone(realFrontdeskScenarioEvidence.abnormalReturn),
          noAutoOrder: false,
        },
      },
    },
    {
      name: "计划患者与上下文不一致",
      body: {
        ...structuredClone(realFrontdeskScenarioEvidence),
        followupPlan: {
          ...structuredClone(realFrontdeskScenarioEvidence.followupPlan),
          patientId: "mpi-other",
        },
      },
    },
  ])("does not declare S12 normal condition row when $name", ({ body }) => {
    expectNoRealFrontdeskScenarioConditionCoverage(body);
  });

  it("declares service organization coverage only when the passed spec attaches complete frontdesk evidence", () => {
    const evidence = serviceOrganizationEvidenceResult(serviceOrganizationEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S1", "S14"]);
    expect(evidence.launchCoverage.organizationLevels?.map((item) => item.code)).toEqual([
      "HOSPITAL",
      "DEPARTMENT",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "ONBOARDING_INTEGRATION",
      "COMPLIANCE_OPERATIONS",
    ]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S1__NORMAL",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("does not declare S1 normal condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = serviceOrganizationEvidence;
    const evidence = serviceOrganizationEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S1", "S14"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        scenarioConditionEvidence: [
          {
            code: "S1__HIGH_RISK",
            scenarioCode: "S1",
            condition: "HIGH_RISK",
            source: "SERVICE_ORGANIZATION_ONBOARDING_ORG_TREE_READBACK",
            evidence: ["服务机构正常开通不能冒领高危态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        scenarioConditionEvidence: [
          {
            code: "S1__NORMAL",
            scenarioCode: "S1",
            condition: "NORMAL",
            source: "PLATFORM_ADMIN_ENTRY_CORE_ACTIONS",
            evidence: ["不能只靠平台管理员入口矩阵冒领 S1 正常态"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        scenarioConditionEvidence: [
          {
            code: "S1__NORMAL",
            scenarioCode: "S1",
            condition: "NORMAL",
            source: "SERVICE_ORGANIZATION_ONBOARDING_ORG_TREE_READBACK",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "服务机构开通接口不是 2xx",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        onboardingEvidence: {
          ...structuredClone(serviceOrganizationEvidence.onboardingEvidence),
          serviceStatus: 500,
        },
      },
    },
    {
      name: "一次性临时密码没有展示确认",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        onboardingEvidence: {
          ...structuredClone(serviceOrganizationEvidence.onboardingEvidence),
          temporaryPasswordDisplayedOnce: false,
        },
      },
    },
    {
      name: "机构管理员首次登录未要求改密",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        adminBootstrapEvidence: {
          ...structuredClone(serviceOrganizationEvidence.adminBootstrapEvidence),
          loginMustChangePwd: false,
        },
      },
    },
    {
      name: "机构管理员改密未成功",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        adminBootstrapEvidence: {
          ...structuredClone(serviceOrganizationEvidence.adminBootstrapEvidence),
          changePasswordStatus: 500,
        },
      },
    },
    {
      name: "机构不是 FACILITY",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        orgTreeEvidence: {
          ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence),
          facility: {
            ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence.facility),
            level: "GROUP",
          },
        },
      },
    },
    {
      name: "科室不是 DEPARTMENT",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        orgTreeEvidence: {
          ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence),
          department: {
            ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence.department),
            level: "WARD",
          },
        },
      },
    },
    {
      name: "科室父级未绑定本轮医疗机构",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        orgTreeEvidence: {
          ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence),
          department: {
            ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence.department),
            parentId: "facility-other",
          },
        },
      },
    },
    {
      name: "组织树回读 tenant 不一致",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        orgTreeEvidence: {
          ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence),
          department: {
            ...structuredClone(serviceOrganizationEvidence.orgTreeEvidence.department),
            tenantId: "tenant-other",
          },
        },
      },
    },
    {
      name: "只证明 S14 权限画像",
      body: {
        ...structuredClone(serviceOrganizationEvidence),
        scenarioCodes: ["S14"],
        scenarioEvidence: [
          {
            code: "S14",
            observedStages: [
              "前台创建临床账号并绑定科室职责范围",
              "临床账号首次登录后读取权限画像",
              "前台停用演练账号",
            ],
          },
        ],
      },
    },
  ])("does not declare S1 normal condition row when $name", ({ body }) => {
    expectNoServiceOrganizationScenarioConditionCoverage(body);
  });

  it("declares MFA login coverage only when the passed spec attaches complete authentication-safety evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/mfa-login-frontdesk.spec.ts",
          title: "开启 MFA 后已绑定账号必须在登录页完成真实 TOTP 验证",
          status: "passed",
          attachments: [
            {
              name: "mfa-login-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S14"],
                productLayers: ["FOUNDATION_GOVERNANCE"],
                serviceCombinations: ["COMPLIANCE_OPERATIONS"],
                scenarioEvidence: [
                  {
                    code: "S14",
                    observedStages: [
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
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S14"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "FOUNDATION_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "COMPLIANCE_OPERATIONS",
    ]);
    expect(evidence.launchCoverage.organizationLevels).toBeUndefined();
  });

  it("does not declare MFA login coverage from a passed spec without complete authentication-safety evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/mfa-login-frontdesk.spec.ts",
          title: "开启 MFA 后已绑定账号必须在登录页完成真实 TOTP 验证",
          status: "passed",
          attachments: [
            {
              name: "mfa-login-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S14"],
                productLayers: ["FOUNDATION_GOVERNANCE"],
                serviceCombinations: ["COMPLIANCE_OPERATIONS"],
                scenarioEvidence: [
                  {
                    code: "S14",
                    observedStages: ["配置中心读取上线默认 MFA 关闭"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("declares identity binding coverage only when the passed spec attaches complete plaintext-safety evidence", () => {
    const evidence = identityBindingEvidenceResult(identityBindingEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S14"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "FOUNDATION_GOVERNANCE",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "COMPLIANCE_OPERATIONS",
    ]);
    expect(evidence.launchCoverage.organizationLevels).toBeUndefined();
  });

  it("declares S14 normal condition row only from identity binding lifecycle and plaintext-safety evidence", () => {
    const evidence = identityBindingEvidenceResult(identityBindingEvidence);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S14__NORMAL",
    ]);
  });

  it("does not declare S14 normal condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = identityBindingEvidence;
    const evidence = identityBindingEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S14"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(identityBindingEvidence),
        scenarioConditionEvidence: [
          {
            code: "S14__HIGH_RISK",
            scenarioCode: "S14",
            condition: "HIGH_RISK",
            source: "IDENTITY_BINDING_LIFECYCLE_PLAINTEXT_SAFETY",
            evidence: ["身份来源正常绑定生命周期不能冒领高危态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(identityBindingEvidence),
        scenarioConditionEvidence: [
          {
            code: "S14__NORMAL",
            scenarioCode: "S14",
            condition: "NORMAL",
            source: "PLATFORM_ADMIN_ENTRY_CORE_ACTIONS",
            evidence: ["不能只靠平台管理员入口矩阵冒领 S14 正常态"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(identityBindingEvidence),
        scenarioConditionEvidence: [
          {
            code: "S14__NORMAL",
            scenarioCode: "S14",
            condition: "NORMAL",
            source: "IDENTITY_BINDING_LIFECYCLE_PLAINTEXT_SAFETY",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "绑定未成功",
      body: {
        ...structuredClone(identityBindingEvidence),
        apiEvidence: {
          ...structuredClone(identityBindingEvidence.apiEvidence),
          bindingPosted: false,
        },
      },
    },
    {
      name: "列表泄露身份原文",
      body: {
        ...structuredClone(identityBindingEvidence),
        plaintextSafety: {
          ...structuredClone(identityBindingEvidence.plaintextSafety),
          listOmitsExternalSubjectPlaintext: false,
        },
      },
    },
    {
      name: "重复外部身份未拒绝",
      body: {
        ...structuredClone(identityBindingEvidence),
        plaintextSafety: {
          ...structuredClone(identityBindingEvidence.plaintextSafety),
          duplicateStatus: 200,
        },
      },
    },
    {
      name: "解绑未推进版本",
      body: {
        ...structuredClone(identityBindingEvidence),
        unbinding: {
          ...structuredClone(identityBindingEvidence.unbinding),
          versionAdvanced: false,
        },
      },
    },
    {
      name: "演练账号未清理",
      body: {
        ...structuredClone(identityBindingEvidence),
        cleanup: {
          ...structuredClone(identityBindingEvidence.cleanup),
          duplicateAccountDisabled: false,
        },
      },
    },
  ])("does not declare S14 normal condition row when $name", ({ body }) => {
    expectNoIdentityBindingScenarioConditionCoverage(body);
  });

  it("does not declare identity binding coverage from a passed spec without complete bind/unbind cleanup evidence", () => {
    const missingAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/identity-binding-frontdesk.spec.ts",
          title: "平台管理员可前台绑定和解绑院内身份来源且身份原文不落库",
          status: "passed",
        },
      ],
    });
    const incompleteAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/identity-binding-frontdesk.spec.ts",
          title: "平台管理员可前台绑定和解绑院内身份来源且身份原文不落库",
          status: "passed",
          attachments: [
            {
              name: "identity-binding-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S14"],
                productLayers: ["FOUNDATION_GOVERNANCE"],
                serviceCombinations: ["COMPLIANCE_OPERATIONS"],
                apiEvidence: {
                  personnelCreated: true,
                  bindingPosted: true,
                  bindingListRead: true,
                },
                binding: {
                  bindingId: "binding-1",
                  providerType: "EMPLOYEE_NO",
                  subjectHint: "****A001",
                  status: "ACTIVE",
                },
                scenarioEvidence: [
                  {
                    code: "S14",
                    observedStages: ["前台绑定院内身份来源"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });
    const unsafeAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/identity-binding-frontdesk.spec.ts",
          title: "平台管理员可前台绑定和解绑院内身份来源且身份原文不落库",
          status: "passed",
          attachments: [
            {
              name: "identity-binding-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                ...identityBindingEvidence,
                plaintextSafety: {
                  ...identityBindingEvidence.plaintextSafety,
                  listOmitsExternalSubjectPlaintext: false,
                },
              }),
            },
          ],
        },
      ],
    });

    expect(missingAttachment.launchCoverage.scenarios).toBeUndefined();
    expect(missingAttachment.launchCoverage.productLayers).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.scenarios).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.productLayers).toBeUndefined();
    expect(unsafeAttachment.launchCoverage.scenarios).toBeUndefined();
    expect(unsafeAttachment.launchCoverage.productLayers).toBeUndefined();
  });

  it("does not declare service organization coverage from a passed spec without complete scenario附件", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/service-organization-frontdesk.spec.ts",
          title: "平台开通服务机构后，新机构可完成组织树、科室账号、职责范围和登录画像闭环",
          status: "passed",
          attachments: [
            {
              name: "service-organization-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S1"],
                organizationLevels: ["HOSPITAL"],
                serviceCombinations: ["ONBOARDING_INTEGRATION"],
                scenarioEvidence: [{ code: "S1", observedStages: ["前台开通服务机构"] }],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.organizationLevels).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("declares diagnosis knowledge maintenance coverage only when the passed spec attaches complete asset-production evidence", () => {
    const evidence = diagnosisKnowledgeEvidenceResult(diagnosisKnowledgeEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S3"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "MEDICAL_ASSET",
    ]);
    expect(evidence.launchCoverage.semanticFamilies?.map((item) => item.code)).toEqual([
      "DISEASE_DIAGNOSIS",
    ]);
    expect(evidence.launchCoverage.specialtyDomains?.map((item) => item.code)).toEqual([
      "CLINICAL_SPECIALTIES",
    ]);
    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).not.toContain("S16");
  });

  it("declares S3 normal condition row only from diagnosis asset, standard and validation case evidence", () => {
    const evidence = diagnosisKnowledgeEvidenceResult(diagnosisKnowledgeEvidence);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S3__NORMAL",
    ]);
  });

  it("does not declare S3 normal condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = diagnosisKnowledgeEvidence;
    const evidence = diagnosisKnowledgeEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S3"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        scenarioConditionEvidence: [
          {
            code: "S3__HIGH_RISK",
            scenarioCode: "S3",
            condition: "HIGH_RISK",
            source: "DIAGNOSIS_KNOWLEDGE_ASSET_STANDARD_CASE_MAINTENANCE",
            evidence: ["诊断知识维护正常主链不能冒领高危态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        scenarioConditionEvidence: [
          {
            code: "S3__NORMAL",
            scenarioCode: "S3",
            condition: "NORMAL",
            source: "DIAGNOSIS_SUPPORT_RUNTIME",
            evidence: ["不能把诊断支持消费冒领为诊断知识维护"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        scenarioConditionEvidence: [
          {
            code: "S3__NORMAL",
            scenarioCode: "S3",
            condition: "NORMAL",
            source: "DIAGNOSIS_KNOWLEDGE_ASSET_STANDARD_CASE_MAINTENANCE",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "标准术语接口不是 2xx",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        standardTerm: {
          ...structuredClone(diagnosisKnowledgeEvidence.standardTerm),
          status: 500,
        },
      },
    },
    {
      name: "诊断资产缺知识身份",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        diagnosisAsset: {
          ...structuredClone(diagnosisKnowledgeEvidence.diagnosisAsset),
          identityId: 0,
        },
      },
    },
    {
      name: "诊断资产缺版本",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        diagnosisAsset: {
          ...structuredClone(diagnosisKnowledgeEvidence.diagnosisAsset),
          versionId: undefined,
        },
      },
    },
    {
      name: "诊断标准发现项与标准术语不一致",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        diagnosisCriterion: {
          ...structuredClone(diagnosisKnowledgeEvidence.diagnosisCriterion),
          findingTermCode: "TERM.LAB.OTHER",
        },
      },
    },
    {
      name: "验证病例发现项与标准术语不一致",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        validationCase: {
          ...structuredClone(diagnosisKnowledgeEvidence.validationCase),
          findingTermCode: "TERM.LAB.OTHER",
        },
      },
    },
    {
      name: "验证病例身份为空",
      body: {
        ...structuredClone(diagnosisKnowledgeEvidence),
        validationCase: {
          ...structuredClone(diagnosisKnowledgeEvidence.validationCase),
          caseIdentity: "",
        },
      },
    },
  ])("does not declare S3 normal condition row when $name", ({ body }) => {
    expectNoDiagnosisKnowledgeScenarioConditionCoverage(body);
  });

  const sourceLineageEvidence = {
    scenarioCodes: ["S7"],
    semanticFamilies: ["SOURCE_VALIDITY"],
    apiEvidence: {
      sourceRegistered: true,
      sourceVersionRegistered: true,
      sourceFragmentRegistered: true,
      knowledgeCandidateSubmitted: true,
      citationBound: true,
      candidateApproved: true,
      graphProjectionRebuilt: true,
      provenanceReadback: true,
      graphNodeExplored: true,
      traceEvidenceVisible: true,
    },
    source: {
      sourceDocumentId: 1001,
      sourceVersionId: 2001,
      sourceFragmentId: 3001,
      sourceCode: "E2E-GRAPH-SOURCE-20260709",
      sourceVersionNo: "2026-e2e-20260709",
      sourceVersionHash: "b1df565b377d7c3d6b7d82552e925a8af497f731475116da8f93f1f3560f1c8f",
      fragmentHash: "76f8711a99f1b63f3f4fdb293c27eb0f65c7a261bb56ba29bdf1cd84a2077c10",
      sourceRef: "E2E-GRAPH-SOURCE-20260709:2026-e2e-20260709:section:source-boundary",
      anchorPath: "section:source-boundary",
      anchorLabel: "来源边界",
      textExcerpt:
        "图谱投影验收来源边界：本材料只验证 MedKernel 关系库权威知识到知识关系投影的真实链路。",
      contentHashVerified: true,
      fragmentHashVerified: true,
    },
    knowledgeCandidate: {
      operation: "GENERATE_REVIEW_APPROVE",
      identityId: 4001,
      identityCode: "e2e.graph.source-boundary.20260709",
      versionId: 5001,
      candidateRef: "kv:4001:V1",
      jobCode: "job-graph-source-lineage-20260709",
      classificationId: 6001,
      qualityGateRecordId: 7001,
      status: "ACTIVE",
    },
    citation: {
      citationId: 8001,
      relation: "DERIVED_FROM",
      weight: 100,
      startOffset: 0,
      endOffset: 51,
      sourceFragmentId: 3001,
      assetVersionId: 5001,
    },
    provenanceReadback: {
      identityId: 4001,
      identityCode: "e2e.graph.source-boundary.20260709",
      currentVersionId: 5001,
      activeVersionStatus: "ACTIVE",
      partial: false,
      unresolvedCitationCount: 0,
      citationId: 8001,
      sourceFragmentId: 3001,
      sourceDocumentId: 1001,
      sourceVersionId: 2001,
      sourceCode: "E2E-GRAPH-SOURCE-20260709",
      sourceType: "GUIDELINE",
      authorityLevel: "B_GUIDELINE",
      authorityLabel: "B 指南",
      sourceVersionNo: "2026-e2e-20260709",
      sourceVersionHash: "b1df565b377d7c3d6b7d82552e925a8af497f731475116da8f93f1f3560f1c8f",
      anchorPath: "section:source-boundary",
      anchorLabel: "来源边界",
      fragmentHash: "76f8711a99f1b63f3f4fdb293c27eb0f65c7a261bb56ba29bdf1cd84a2077c10",
      relation: "DERIVED_FROM",
      weight: 100,
    },
    graphProjection: {
      operation: "REBUILD_AND_EXPLORE",
      sourceCount: 1,
      projectionCount: 1,
      projectionMatchesSourceCount: true,
      graphNodeExplored: true,
      traceEvidenceVisible: true,
      browserErrors: [],
    },
    scenarioConditionEvidence: [
      {
        code: "S7__NORMAL",
        scenarioCode: "S7",
        condition: "NORMAL",
        source: "SOURCE_LINEAGE_GRAPH_PROVENANCE_READBACK",
        evidence: [
          "医疗引擎运营员登记受控来源、版本和锚点并审核激活带来源引用的知识候选",
          "后端回读完整 provenance，前台重建并探索知识关系图且追踪证据可见",
        ],
      },
    ],
    scenarioEvidence: [
      {
        code: "S7",
        observedStages: [
          "真实登记受控来源、版本和锚点",
          "真实提交并审核激活带来源引用的知识候选",
          "真实绑定来源引用并回读血缘证据",
          "真实重建知识关系投影",
          "前台探索知识关系图并查看追踪证据",
        ],
      },
    ],
  };

  function sourceLineageEvidenceResult(body: Record<string, unknown>) {
    return buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/d6-graph-explore.spec.ts",
          title: "医疗引擎运营员可重建并探索真实知识投影",
          status: "passed",
          attachments: [
            {
              name: "source-lineage-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify(body),
            },
          ],
        },
      ],
    });
  }

  function expectNoSourceLineageScenarioConditionCoverage(body: Record<string, unknown>) {
    const evidence = sourceLineageEvidenceResult(body);
    expect(
      evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
    ).not.toContain("S7__NORMAL");
  }

  it("declares S7 source lineage coverage only from a complete graph provenance attachment", () => {
    const evidence = sourceLineageEvidenceResult(sourceLineageEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S7"]);
    expect(evidence.launchCoverage.semanticFamilies?.map((item) => item.code)).toEqual([
      "SOURCE_VALIDITY",
    ]);
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
  });

  it("declares S7 normal condition row only from source lineage provenance and graph evidence", () => {
    const evidence = sourceLineageEvidenceResult(sourceLineageEvidence);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S7__NORMAL",
    ]);
  });

  it("does not declare S7 normal condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = sourceLineageEvidence;
    const evidence = sourceLineageEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S7"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(sourceLineageEvidence),
        scenarioConditionEvidence: [
          {
            code: "S7__HIGH_RISK",
            scenarioCode: "S7",
            condition: "HIGH_RISK",
            source: "SOURCE_LINEAGE_GRAPH_PROVENANCE_READBACK",
            evidence: ["来源血缘正常链路不能冒领高危态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(sourceLineageEvidence),
        scenarioConditionEvidence: [
          {
            code: "S7__NORMAL",
            scenarioCode: "S7",
            condition: "NORMAL",
            source: "GRAPH_UI_ONLY",
            evidence: ["不能只靠图谱 UI 冒领来源血缘正常态"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(sourceLineageEvidence),
        scenarioConditionEvidence: [
          {
            code: "S7__NORMAL",
            scenarioCode: "S7",
            condition: "NORMAL",
            source: "SOURCE_LINEAGE_GRAPH_PROVENANCE_READBACK",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "受控来源未登记",
      body: {
        ...structuredClone(sourceLineageEvidence),
        apiEvidence: {
          ...structuredClone(sourceLineageEvidence.apiEvidence),
          sourceRegistered: false,
        },
      },
    },
    {
      name: "来源版本 hash 非法",
      body: {
        ...structuredClone(sourceLineageEvidence),
        source: {
          ...structuredClone(sourceLineageEvidence.source),
          sourceVersionHash: "not-a-sha256",
        },
      },
    },
    {
      name: "知识候选未激活",
      body: {
        ...structuredClone(sourceLineageEvidence),
        knowledgeCandidate: {
          ...structuredClone(sourceLineageEvidence.knowledgeCandidate),
          status: "DRAFT",
        },
      },
    },
    {
      name: "引用未绑定同一片段",
      body: {
        ...structuredClone(sourceLineageEvidence),
        citation: {
          ...structuredClone(sourceLineageEvidence.citation),
          sourceFragmentId: 9999,
        },
      },
    },
    {
      name: "provenance 存在未解析引用",
      body: {
        ...structuredClone(sourceLineageEvidence),
        provenanceReadback: {
          ...structuredClone(sourceLineageEvidence.provenanceReadback),
          unresolvedCitationCount: 1,
        },
      },
    },
    {
      name: "provenance 不是完整血缘",
      body: {
        ...structuredClone(sourceLineageEvidence),
        provenanceReadback: {
          ...structuredClone(sourceLineageEvidence.provenanceReadback),
          partial: true,
        },
      },
    },
    {
      name: "图谱投影数量不一致",
      body: {
        ...structuredClone(sourceLineageEvidence),
        graphProjection: {
          ...structuredClone(sourceLineageEvidence.graphProjection),
          projectionMatchesSourceCount: false,
        },
      },
    },
    {
      name: "前台追踪证据不可见",
      body: {
        ...structuredClone(sourceLineageEvidence),
        graphProjection: {
          ...structuredClone(sourceLineageEvidence.graphProjection),
          traceEvidenceVisible: false,
        },
      },
    },
  ])("does not declare S7 normal condition row when $name", ({ body }) => {
    expectNoSourceLineageScenarioConditionCoverage(body);
  });

  it("does not declare S7 source lineage coverage from graph UI without complete source evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/d6-graph-explore.spec.ts",
          title: "医疗引擎运营员可重建并探索真实知识投影",
          status: "passed",
          attachments: [
            {
              name: "source-lineage-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S7"],
                semanticFamilies: ["SOURCE_VALIDITY"],
                apiEvidence: {
                  sourceRegistered: true,
                  sourceVersionRegistered: true,
                  sourceFragmentRegistered: true,
                  knowledgeCandidateSubmitted: true,
                  citationBound: false,
                  candidateApproved: true,
                  graphProjectionRebuilt: true,
                  provenanceReadback: false,
                  graphNodeExplored: true,
                  traceEvidenceVisible: true,
                },
                scenarioEvidence: [
                  {
                    code: "S7",
                    observedStages: ["真实重建知识关系投影"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.semanticFamilies).toBeUndefined();
  });

  const embedBusinessHostEvidence = {
    scenarioCodes: ["S8"],
    productLayers: ["DELIVERY_FEEDBACK"],
    deliveryShapes: ["EMBEDDED_COMPONENT"],
    apiEvidence: {
      launchTokenIssued: true,
      launchExchanged: true,
      recommendationsRead: true,
      feedbackSubmitted: true,
      hostMessageReceived: true,
    },
    apiResponses: [
      "POST /medkernel/api/v1/engine/embed/launch 200",
      "POST /medkernel/api/v1/engine/embed/recommendations 200",
      "POST /medkernel/api/v1/engine/embed/feedback 200",
    ],
    clinicalContext: {
      patientId: "mpi-embed-s8",
      snapshotId: "ctx-embed-s8",
      encounterId: "enc-embed-s8",
      triggerPoint: "patient-view",
    },
    launchToken: {
      operation: "ISSUE_AND_EXCHANGE",
      status: 200,
      integrationMode: "IFRAME",
      hook: "patient-view",
      hookInstance: "embed-host-card-s8",
      embedUrlIncludesLaunchToken: true,
      parentOrigin: "http://127.0.0.1:4174",
    },
    recommendation: {
      operation: "READ_EMBEDDED_RECOMMENDATIONS",
      status: 200,
      cardId: "card-embed-s8",
      title: "检验危急值需人工确认",
      traceId: "trace-recommendation-s8",
      visibleCardCount: 1,
      suppressedCardCount: 0,
      sourceSummary: "嵌入宿主真实服务链路演练：检验危急值管理制度",
    },
    feedback: {
      operation: "SUBMIT_DOCTOR_FEEDBACK",
      status: 200,
      cardId: "card-embed-s8",
      actionType: "ADOPT",
      recommendationStatus: "ACCEPTED",
      callbackStatus: "NOT_CONNECTED",
      callbackDelivered: false,
      degradationReason: "EMBED_CALLBACK_NOT_CONFIGURED",
      traceId: "trace-feedback-s8",
    },
    hostMessage: {
      received: true,
      actionType: "ADOPT",
      cardId: "card-embed-s8",
      patientId: "mpi-embed-s8",
      encounterId: "enc-embed-s8",
    },
    runtimeSafety: {
      browserErrors: [],
      serverErrors: [],
      networkFailures: [],
    },
    scenarioConditionEvidence: [
      {
        code: "S8__DEGRADATION",
        scenarioCode: "S8",
        condition: "DEGRADATION",
        source: "EMBEDDED_HOST_CALLBACK_NOT_CONNECTED_LOCAL_FEEDBACK_CONTINUES",
        evidence: [
          "独立业务系统宿主通过真实 iframe 启动地址完成嵌入建议读取和医师采纳反馈",
          "外部回调缺配置时反馈状态为 NOT_CONNECTED，但本地 postMessage 主链路继续回传医师动作",
        ],
      },
    ],
    scenarioEvidence: [
      {
        code: "S8",
        observedStages: [
          "真实签发一次性嵌入启动凭证",
          "独立业务系统宿主加载真实 iframe 启动地址",
          "嵌入终端真实兑换启动凭证并读取当前就诊上下文",
          "嵌入终端真实读取当前就诊推荐卡",
          "医师在嵌入终端提交采纳反馈",
          "独立业务系统宿主收到医师反馈 postMessage",
        ],
      },
    ],
  };

  function embedBusinessHostEvidenceResult(body: Record<string, unknown>) {
    return buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/embed-business-host.spec.ts",
          title: "独立业务系统宿主通过真实嵌入凭证完成 iframe 启动并接收医师反馈",
          status: "passed",
          attachments: [
            {
              name: "embed-business-host-launch-codes",
              contentType: "application/json",
              body: JSON.stringify(body),
            },
          ],
        },
      ],
    });
  }

  function expectNoEmbedBusinessHostScenarioConditionCoverage(body: Record<string, unknown>) {
    const evidence = embedBusinessHostEvidenceResult(body);
    expect(
      evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code) ?? [],
    ).not.toContain("S8__DEGRADATION");
  }

  it("declares embedded business host coverage only when the passed spec attaches complete real service evidence", () => {
    const evidence = embedBusinessHostEvidenceResult(embedBusinessHostEvidence);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S8"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "DELIVERY_FEEDBACK",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual([
      "EMBEDDED_COMPONENT",
    ]);
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("declares S8 degradation condition row only from callback NOT_CONNECTED with local embedded feedback continuity", () => {
    const evidence = embedBusinessHostEvidenceResult(embedBusinessHostEvidence);

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S8__DEGRADATION",
    ]);
  });

  it("does not declare S8 degradation condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = embedBusinessHostEvidence;
    const evidence = embedBusinessHostEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S8"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        scenarioConditionEvidence: [
          {
            code: "S8__NORMAL",
            scenarioCode: "S8",
            condition: "NORMAL",
            source: "EMBEDDED_HOST_CALLBACK_NOT_CONNECTED_LOCAL_FEEDBACK_CONTINUES",
            evidence: ["外部回调 NOT_CONNECTED 不能冒领正常态"],
          },
        ],
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        scenarioConditionEvidence: [
          {
            code: "S8__DEGRADATION",
            scenarioCode: "S8",
            condition: "DEGRADATION",
            source: "EMBEDDED_IFRAME_VISIBLE_ONLY",
            evidence: ["不能只靠 iframe 可见冒领降级态"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        scenarioConditionEvidence: [
          {
            code: "S8__DEGRADATION",
            scenarioCode: "S8",
            condition: "DEGRADATION",
            source: "EMBEDDED_HOST_CALLBACK_NOT_CONNECTED_LOCAL_FEEDBACK_CONTINUES",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "嵌入启动凭证未兑换",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        apiEvidence: {
          ...structuredClone(embedBusinessHostEvidence.apiEvidence),
          launchExchanged: false,
        },
      },
    },
    {
      name: "推荐卡读取不是 2xx",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        recommendation: {
          ...structuredClone(embedBusinessHostEvidence.recommendation),
          status: 503,
        },
      },
    },
    {
      name: "医师反馈未采纳",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        feedback: {
          ...structuredClone(embedBusinessHostEvidence.feedback),
          recommendationStatus: "PENDING",
        },
      },
    },
    {
      name: "回调不是诚实断连",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        feedback: {
          ...structuredClone(embedBusinessHostEvidence.feedback),
          callbackStatus: "DELIVERED",
          callbackDelivered: true,
        },
      },
    },
    {
      name: "宿主 postMessage 未收到反馈",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        hostMessage: {
          ...structuredClone(embedBusinessHostEvidence.hostMessage),
          received: false,
        },
      },
    },
    {
      name: "浏览器存在错误",
      body: {
        ...structuredClone(embedBusinessHostEvidence),
        runtimeSafety: {
          ...structuredClone(embedBusinessHostEvidence.runtimeSafety),
          browserErrors: ["iframe crashed"],
        },
      },
    },
  ])("does not declare S8 degradation condition row when $name", ({ body }) => {
    expectNoEmbedBusinessHostScenarioConditionCoverage(body);
  });

  it("declares S6 pathway lifecycle evidence slice without packaging milestone config as ten-stage runtime coverage", () => {
    const evidence = pathwayLifecycleEvidenceResult(pathwayLifecycleEvidence());

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S6"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
    ]);
    expect(evidence.launchCoverage.versionedAssets?.map((item) => item.code)).toEqual([
      "ORDER_SET",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "SPECIAL_DISEASE_PATHWAY",
    ]);
    expect(evidence.launchCoverage.specialDiseaseStages?.map((item) => item.code)).toEqual(
      requiredSpecialDiseaseStages,
    );
  });

  it("declares S6 normal condition row only from explicit pathway ORDER_SET runtime consumption evidence", () => {
    const evidence = pathwayLifecycleEvidenceResult(pathwayLifecycleEvidence());

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S6__NORMAL",
    ]);
  });

  it("does not declare S6 normal condition row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = pathwayLifecycleEvidence();
    const evidence = pathwayLifecycleEvidenceResult(body);

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S6"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: pathwayLifecycleEvidence({
        scenarioConditionEvidence: [
          {
            code: "S6__ABNORMAL",
            scenarioCode: "S6",
            condition: "ABNORMAL",
            source: "SPECIAL_DISEASE_PATHWAY_ORDER_SET_RUNTIME_CONSUMPTION",
            evidence: ["专病路径正常主链路不能冒领异常态"],
          },
        ],
      }),
    },
    {
      name: "条件行来源错配",
      body: pathwayLifecycleEvidence({
        scenarioConditionEvidence: [
          {
            code: "S6__NORMAL",
            scenarioCode: "S6",
            condition: "NORMAL",
            source: "PATHWAY_TEMPLATE_MILESTONE_MATRIX",
            evidence: ["不能只靠十阶段配置矩阵冒领 S6 正常态"],
          },
        ],
      }),
    },
    {
      name: "条件行证据为空",
      body: pathwayLifecycleEvidence({
        scenarioConditionEvidence: [
          {
            code: "S6__NORMAL",
            scenarioCode: "S6",
            condition: "NORMAL",
            source: "SPECIAL_DISEASE_PATHWAY_ORDER_SET_RUNTIME_CONSUMPTION",
            evidence: [],
          },
        ],
      }),
    },
    {
      name: "未消费 ORDER_SET 运行资产",
      body: pathwayLifecycleEvidence({
        apiEvidence: {
          ...(pathwayLifecycleEvidence().apiEvidence as Record<string, unknown>),
          orderSetRuntimeConsumed: false,
        },
      }),
    },
    {
      name: "患者路径 runtime 与机构生效版本不一致",
      body: pathwayLifecycleEvidence({
        orderSetRuntimeConsumer: {
          ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer as Record<string, unknown>),
          patientPathway: {
            patientPathwayId: "pp-s6-copd",
            runtimeReleaseId: "runtime-other",
          },
        },
      }),
    },
    {
      name: "推进节点不是 ORDER_SET 节点",
      body: pathwayLifecycleEvidence({
        orderSetRuntimeConsumer: {
          ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer as Record<string, unknown>),
          advanceResponse: {
            ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer.advanceResponse as Record<
              string,
              unknown
            >),
            decisionEvidence: {
              ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer.advanceResponse
                .decisionEvidence as Record<string, unknown>),
              "pathway.currentNodeType": "FOLLOWUP",
            },
          },
        },
      }),
    },
    {
      name: "医嘱套餐不要求医师确认",
      body: pathwayLifecycleEvidence({
        orderSetRuntimeConsumer: {
          ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer as Record<string, unknown>),
          advanceResponse: {
            ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer.advanceResponse as Record<
              string,
              unknown
            >),
            decisionEvidence: {
              ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer.advanceResponse
                .decisionEvidence as Record<string, unknown>),
              "pathway.orderSetRequiresPhysicianConfirmation": false,
            },
          },
        },
      }),
    },
    {
      name: "医嘱套餐项目为空",
      body: pathwayLifecycleEvidence({
        orderSetRuntimeConsumer: {
          ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer as Record<string, unknown>),
          advanceResponse: {
            ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer.advanceResponse as Record<
              string,
              unknown
            >),
            decisionEvidence: {
              ...(pathwayLifecycleEvidence().orderSetRuntimeConsumer.advanceResponse
                .decisionEvidence as Record<string, unknown>),
              "pathway.orderSetItemCount": 0,
              "pathway.orderSetItems": [],
            },
          },
        },
      }),
    },
  ])("does not declare S6 normal condition row when $name", ({ body }) => {
    expectNoPathwayLifecycleScenarioConditionCoverage(body);
  });

  it("does not declare special disease stage coverage when the pathway milestone matrix is incomplete", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/pathway-lifecycle-frontdesk.spec.ts",
          title:
            "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
          status: "passed",
          attachments: [
            {
              name: "pathway-lifecycle-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify(
                pathwayLifecycleEvidence({
                  specialDiseaseStages: requiredSpecialDiseaseStages.filter(
                    (stage) => stage !== "QUALITY_ITERATION",
                  ),
                }),
              ),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.specialDiseaseStages).toBeUndefined();
  });

  it("does not declare S6 pathway lifecycle coverage from a passed spec without complete real lifecycle evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/pathway-lifecycle-frontdesk.spec.ts",
          title:
            "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
          status: "passed",
          attachments: [
            {
              name: "pathway-lifecycle-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S6"],
                productLayers: ["CLINICAL_EXECUTION"],
                serviceCombinations: ["SPECIAL_DISEASE_PATHWAY"],
                specialDiseaseStages: ["SCREENING_TRIAGE"],
                apiEvidence: {
                  templateSaved: true,
                  templateReadback: true,
                  draftPreviewRun: true,
                  templateSimulated: true,
                  entryCandidatesRead: true,
                  patientEntered: true,
                  standardAdvanced: true,
                  varianceRecorded: true,
                  followupHandoffCreated: false,
                  clocksRead: true,
                  variancesRead: true,
                  followupHandoffObserved: false,
                },
                scenarioEvidence: [
                  {
                    code: "S6",
                    observedStages: ["前台创建专病路径草稿并保存节点边时钟"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
    expect(evidence.launchCoverage.specialDiseaseStages).toBeUndefined();
  });

  it("does not declare S6 pathway lifecycle coverage without ORDER_SET runtime consumer evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/pathway-lifecycle-frontdesk.spec.ts",
          title:
            "运营员与临床用户完成专病路径生产、真实服务仿真、入径、推进、变异和随访接续证据切片",
          status: "passed",
          attachments: [
            {
              name: "pathway-lifecycle-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S6"],
                productLayers: ["CLINICAL_EXECUTION"],
                serviceCombinations: ["SPECIAL_DISEASE_PATHWAY"],
                specialDiseaseStages: [
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
                ],
                apiEvidence: {
                  templateSaved: true,
                  templateReadback: true,
                  draftPreviewRun: true,
                  templateSimulated: true,
                  entryCandidatesRead: true,
                  patientEntered: true,
                  standardAdvanced: true,
                  varianceRecorded: true,
                  followupHandoffCreated: true,
                  clocksRead: true,
                  variancesRead: true,
                  followupHandoffObserved: true,
                },
                scenarioEvidence: [
                  {
                    code: "S6",
                    observedStages: [
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
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssets).toBeUndefined();
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("does not declare embedded business host coverage from a passed spec without complete real service evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/embed-business-host.spec.ts",
          title: "独立业务系统宿主通过真实嵌入凭证完成 iframe 启动并接收医师反馈",
          status: "passed",
          attachments: [
            {
              name: "embed-business-host-launch-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S8"],
                productLayers: ["DELIVERY_FEEDBACK"],
                deliveryShapes: ["EMBEDDED_COMPONENT"],
                apiEvidence: {
                  launchTokenIssued: true,
                  launchExchanged: true,
                  recommendationsRead: true,
                  feedbackSubmitted: true,
                  hostMessageReceived: false,
                },
                scenarioEvidence: [
                  {
                    code: "S8",
                    observedStages: ["真实签发一次性嵌入启动凭证"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
    expect(evidence.launchCoverage.deliveryShapes).toBeUndefined();
  });

  it("does not declare diagnosis knowledge coverage from a passed spec without complete asset-production附件", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/diagnosis-knowledge-maintenance.spec.ts",
          title: "运营员从前台创建证据完整诊断资产并登记标准与验证病例",
          status: "passed",
          attachments: [
            {
              name: "diagnosis-knowledge-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S3"],
                productLayers: ["MEDICAL_ASSET"],
                semanticFamilies: ["DISEASE_DIAGNOSIS"],
                specialtyDomains: ["CLINICAL_SPECIALTIES"],
                scenarioEvidence: [
                  {
                    code: "S3",
                    observedStages: ["前台创建证据完整诊断资产草稿"],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.semanticFamilies).toBeUndefined();
    expect(evidence.launchCoverage.specialtyDomains).toBeUndefined();
  });

  it("does not declare real-frontdesk scenario coverage from a passed spec without complete scenario附件", () => {
    const missingAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/real-frontdesk-rehearsal.spec.ts",
          title:
            "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
          status: "passed",
        },
      ],
    });
    const incompleteAttachment = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/real-frontdesk-rehearsal.spec.ts",
          title:
            "平台接入、知识资产、模型安全边界、患者资源、医保质控与临床随访数据均由前台页面提交产生",
          status: "passed",
          attachments: [
            {
              name: "real-frontdesk-scenario-codes",
              contentType: "application/json",
              body: JSON.stringify({
                scenarioCodes: ["S10", "S11"],
                scenarioEvidence: [
                  { code: "S10", observedStages: ["前台执行医保审核并联动质量整改"] },
                ],
              }),
            },
          ],
        },
      ],
    });

    expect(missingAttachment.launchCoverage.scenarios).toBeUndefined();
    expect(incompleteAttachment.launchCoverage.scenarios).toBeUndefined();
  });

  it("declares four-role core action coverage only from complete real frontdesk action evidence", () => {
    const evidence = fourRoleCoreActionsEvidenceResult(fourRoleCoreActionsEvidence);

    expect(evidence.launchCoverage.roleRepresentativeCoreActions?.map((item) => item.code)).toEqual(
      ["FOUR_ROLE_PRIMARY_ACTIONS"],
    );
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares six-entry core action representative coverage only from complete real frontdesk action evidence", () => {
    const evidence = sixEntryCoreActionsEvidenceResult(sixEntryCoreActionsEvidence);

    expect(
      evidence.launchCoverage.entryRepresentativeCoreActions?.map((item) => item.code),
    ).toEqual(["SIX_ENTRY_CORE_ACTIONS_REPRESENTATIVE"]);
    expect(evidence.launchCoverage.roleRepresentativeCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares compliance, audit, notification and provenance entry rows only from cross-spec real frontdesk action evidence", () => {
    const evidence = complianceWorkbenchPersonalEntryEvidenceResult();

    expect(
      evidence.launchCoverage.complianceWorkbenchPersonalEntryMatrix?.map((item) => item.code),
    ).toEqual(["COMPLIANCE_WORKBENCH_PERSONAL_ENTRY_ACTIONS"]);
    expect(
      evidence.launchCoverage.complianceWorkbenchPersonalEntryRows?.map((item) => item.code),
    ).toEqual([
      "SECURITY_BASELINE_CONFIG_CHANGE",
      "AUDIT_EVIDENCE_EXPORT_VERIFY",
      "NOTIFICATION_READBACK",
      "NOTIFICATION_SETTINGS_SAVE",
      "SOURCE_LINEAGE_PROVENANCE_READBACK",
    ]);
    expect(evidence.launchCoverage.platformAdminEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares all 34 product menu entry core-action rows only from complete cross-family evidence", () => {
    const evidence = allMenuEntryCoreActionsEvidenceResult();

    expect(evidence.launchCoverage.menuEntryCoreActions?.map((item) => item.code)).toEqual([
      "ALL_34_MENU_ENTRY_CORE_ACTIONS",
    ]);
    expect(evidence.launchCoverage.menuEntryCoreActionRows?.map((item) => item.code)).toEqual(
      requiredMenuEntryCoreActionRows,
    );
  });

  it("does not declare all 34 menu entry rows when one authority menu key is still missing", () => {
    const body = {
      ...qualityManagementEntryCoreActionsEvidence,
      entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.filter(
        (item) => item.menuKey !== "insurance-audit",
      ),
    };
    const evidence = allMenuEntryCoreActionsEvidenceResult({ qualityBody: body });

    expect(evidence.launchCoverage.menuEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.menuEntryCoreActionRows).toBeUndefined();
  });

  it("does not declare compliance personal entry rows from only one proving spec", () => {
    const roleOnly = fourRoleCoreActionsEvidenceResult(fourRoleCoreActionsEvidence);
    const entryOnly = sixEntryCoreActionsEvidenceResult(sixEntryCoreActionsEvidence);

    expect(roleOnly.launchCoverage.complianceWorkbenchPersonalEntryMatrix).toBeUndefined();
    expect(roleOnly.launchCoverage.complianceWorkbenchPersonalEntryRows).toBeUndefined();
    expect(entryOnly.launchCoverage.complianceWorkbenchPersonalEntryMatrix).toBeUndefined();
    expect(entryOnly.launchCoverage.complianceWorkbenchPersonalEntryRows).toBeUndefined();
  });

  it("does not declare compliance personal entry rows from matching attachments in non-target specs", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-four-role-core-actions-rehearsal.spec.ts",
          title: "非目标 spec 不能冒领审计入口强证据",
          status: "passed",
          attachments: [
            {
              name: "four-role-core-actions-codes",
              contentType: "application/json",
              body: JSON.stringify(fourRoleCoreActionsEvidence),
            },
          ],
        },
        {
          file: "/repo/frontend/e2e/ad-hoc-entry-core-actions-rehearsal.spec.ts",
          title: "非目标 spec 不能冒领合规与个人入口强证据",
          status: "passed",
          attachments: [
            {
              name: "entry-core-actions-codes",
              contentType: "application/json",
              body: JSON.stringify(sixEntryCoreActionsEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.complianceWorkbenchPersonalEntryMatrix).toBeUndefined();
    expect(evidence.launchCoverage.complianceWorkbenchPersonalEntryRows).toBeUndefined();
  });

  it("declares platform-admin P0 entry core action coverage only from complete real frontdesk matrix evidence", () => {
    const evidence = platformAdminEntryCoreActionsEvidenceResult(
      platformAdminEntryCoreActionsEvidence,
    );

    expect(evidence.launchCoverage.platformAdminEntryCoreActions?.map((item) => item.code)).toEqual(
      ["FOUR_PLATFORM_ADMIN_P0_ENTRY_ACTIONS"],
    );
    expect(evidence.launchCoverage.entryRepresentativeCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("aggregates platform-admin P0 entry core action coverage from existing frontdesk specs", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: platformAdminEntryCoreActionsEvidence.entryActions.map((action) => ({
        file: `/repo/frontend/e2e/${platformAdminEntryCoreActionSpecFile(action.menuKey)}`,
        title: `平台管理员 ${action.menuKey} 真实前台核心动作`,
        status: "passed",
        attachments: [
          {
            name: "platform-admin-entry-core-actions-codes",
            contentType: "application/json",
            body: JSON.stringify({
              matrixCode: platformAdminEntryCoreActionsEvidence.matrixCode,
              scopeStatement: platformAdminEntryCoreActionsEvidence.scopeStatement,
              entryActions: [action],
            }),
          },
        ],
      })),
    });

    expect(evidence.launchCoverage.platformAdminEntryCoreActions?.map((item) => item.code)).toEqual(
      ["FOUR_PLATFORM_ADMIN_P0_ENTRY_ACTIONS"],
    );
  });

  it("declares platform-admin P1 system-operations entry coverage only from runtime diagnostics and domestic check evidence", () => {
    const evidence = platformAdminP1EntryCoreActionsEvidenceResult(
      platformAdminP1EntryCoreActionsEvidence,
    );

    expect(
      evidence.launchCoverage.platformAdminP1EntryCoreActions?.map((item) => item.code),
    ).toEqual(["RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK"]);
    expect(evidence.launchCoverage.platformAdminEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.entryRepresentativeCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares S14 abnormal condition row from P1 unauthorized API and page evidence only with the detailed attachment", () => {
    const evidence = platformAdminP1SystemOperationsEvidenceResult();

    expect(
      evidence.launchCoverage.platformAdminP1EntryCoreActions?.map((item) => item.code),
    ).toEqual(["RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK"]);
    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S14__ABNORMAL",
    ]);
  });

  it("declares P1 S14 abnormal condition row from the real test title emitted by Playwright", () => {
    const evidence = platformAdminP1SystemOperationsEvidenceResult({
      title: "运行诊断和国产化适配自检均完成真实前台动作、服务回读与权限边界",
    });

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S14__ABNORMAL",
    ]);
  });

  it("declares medication safety high-risk, insurance normal and workflow normal condition rows from explicit backed evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        ...medicationSafetyEvidenceResult(medicationSafetyFrontdeskEvidence).tests,
        ...qualityManagementEntryCoreActionsEvidenceResult(
          qualityManagementEntryCoreActionsEvidence,
        ).tests,
        ...clinicalEntryCoreActionsEvidenceResult(clinicalEntryCoreActionsEvidence).tests,
      ],
    });

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S5__HIGH_RISK",
      "S10__NORMAL",
      "S11__NORMAL",
    ]);
  });

  it("does not declare medication safety high-risk row without explicit condition evidence", () => {
    const { scenarioConditionEvidence: _omitted, ...body } = medicationSafetyFrontdeskEvidence;
    const evidence = medicationSafetyEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "条件行代码未知",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        scenarioConditionEvidence: [
          {
            code: "S5__NORMAL",
            scenarioCode: "S5",
            condition: "NORMAL",
            source: "MEDICATION_SAFETY_CRITICAL_REDLINE_PHYSICIAN_CONFIRMATION",
            evidence: ["不能把高风险红线冒领为正常行"],
          },
        ],
      },
    },
    {
      name: "条件行状态错配",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        scenarioConditionEvidence: [
          {
            code: "S5__HIGH_RISK",
            scenarioCode: "S5",
            condition: "NORMAL",
            source: "MEDICATION_SAFETY_CRITICAL_REDLINE_PHYSICIAN_CONFIRMATION",
            evidence: ["状态错配不能声明"],
          },
        ],
      },
    },
    {
      name: "条件行证据为空",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        scenarioConditionEvidence: [
          {
            code: "S5__HIGH_RISK",
            scenarioCode: "S5",
            condition: "HIGH_RISK",
            source: "MEDICATION_SAFETY_CRITICAL_REDLINE_PHYSICIAN_CONFIRMATION",
            evidence: [],
          },
        ],
      },
    },
    {
      name: "风险矩阵允许自动执行",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        riskMatrix: {
          ...medicationSafetyFrontdeskEvidence.riskMatrix,
          autoExecutionAllowed: true,
        },
      },
    },
    {
      name: "安全红线不是 CRITICAL",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        safetyRedline: {
          ...medicationSafetyFrontdeskEvidence.safetyRedline,
          hazardSeverity: "HIGH",
        },
      },
    },
    {
      name: "医生确认链路允许自动开嘱",
      body: {
        ...medicationSafetyFrontdeskEvidence,
        feedback: {
          ...medicationSafetyFrontdeskEvidence.feedback,
          noAutoOrder: false,
        },
      },
    },
  ])("does not declare S5 high-risk condition row when $name", ({ body }) => {
    const evidence = medicationSafetyEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "医保审核服务不是 2xx",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "insurance-audit" ? { ...item, serviceStatus: 409 } : item,
        ),
      },
      expectedMissing: "S10__NORMAL",
    },
    {
      name: "医保审核缺少服务回读",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "insurance-audit" ? { ...item, readbackVerified: false } : item,
        ),
      },
      expectedMissing: "S10__NORMAL",
    },
    {
      name: "质量整改缺少复核服务",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "qc-alerts"
            ? {
                ...item,
                serviceOperation: "POST /api/v1/engine/rectifications/{taskId}/submit",
              }
            : item,
        ),
      },
      expectedMissing: "S11__NORMAL",
    },
    {
      name: "质量整改缺少审计",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "qc-alerts" ? { ...item, auditVerified: false } : item,
        ),
      },
      expectedMissing: "S11__NORMAL",
    },
    {
      name: "条件行夹带未知代码",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        scenarioConditionEvidence: [
          ...qualityManagementEntryCoreActionsEvidence.scenarioConditionEvidence,
          {
            code: "S10__HIGH_RISK",
            scenarioCode: "S10",
            condition: "HIGH_RISK",
            source: "INSURANCE_AUDIT_SERVICE_READBACK",
            evidence: ["医保审核问题不能冒领高风险行"],
          },
        ],
      },
      expectedMissing: "S10__NORMAL",
    },
  ])("does not declare quality scenario condition rows when $name", ({ body }) => {
    const evidence = qualityManagementEntryCoreActionsEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "协同任务服务不是 2xx",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "workflow-todos" ? { ...item, serviceStatus: 409 } : item,
        ),
      },
    },
    {
      name: "协同任务不是临床用户角色",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "workflow-todos" ? { ...item, role: "engine-operator" } : item,
        ),
      },
    },
    {
      name: "协同任务缺少审计",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "workflow-todos" ? { ...item, auditVerified: false } : item,
        ),
      },
    },
    {
      name: "条件行来源错配",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        scenarioConditionEvidence: [
          {
            code: "S11__NORMAL",
            scenarioCode: "S11",
            condition: "NORMAL",
            source: "NOTIFICATION_READBACK",
            evidence: ["通知回读不能冒领临床协同任务五态行"],
          },
        ],
      },
    },
  ])("does not declare clinical workflow normal condition row when $name", ({ body }) => {
    const evidence = clinicalEntryCoreActionsEvidenceResult(body);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it("keeps scenario condition rows unique when system providers and P1 both prove S14 abnormal", () => {
    const p1Evidence = platformAdminP1SystemOperationsEvidenceResult({
      title: "运行诊断和国产化适配自检均完成真实前台动作、服务回读与权限边界",
    });
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        ...p1Evidence.tests,
        {
          file: "/repo/frontend/e2e/system-providers-frontdesk.spec.ts",
          title: "平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖",
          status: "passed",
          attachments: [
            {
              name: "system-providers-operations-codes",
              contentType: "application/json",
              body: JSON.stringify(systemProvidersEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarioConditionRows?.map((item) => item.code)).toEqual([
      "S15__NORMAL",
      "S15__DEGRADATION",
      "S14__ABNORMAL",
    ]);
  });

  it("does not auto-generate P1 S14 abnormal condition row from the representative matrix alone", () => {
    const evidence = platformAdminP1EntryCoreActionsEvidenceResult(
      platformAdminP1EntryCoreActionsEvidence,
    );

    expect(
      evidence.launchCoverage.platformAdminP1EntryCoreActions?.map((item) => item.code),
    ).toEqual(["RUNTIME_DIAGNOSTICS_DOMESTIC_CHECK"]);
    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it.each([
    {
      name: "运行诊断 API 越权没有返回 403",
      operationsBody: {
        ...platformAdminP1SystemOperationsEvidence,
        runtimeDiagnosticsEvidence: {
          ...platformAdminP1SystemOperationsEvidence.runtimeDiagnosticsEvidence,
          clinicalRuntimeStatus: 200,
        },
      },
    },
    {
      name: "运行诊断页面没有权限不足证据",
      operationsBody: {
        ...platformAdminP1SystemOperationsEvidence,
        runtimeDiagnosticsEvidence: {
          ...platformAdminP1SystemOperationsEvidence.runtimeDiagnosticsEvidence,
          clinicalPageForbidden: false,
        },
      },
    },
    {
      name: "国产化自检 API 越权没有返回 403",
      operationsBody: {
        ...platformAdminP1SystemOperationsEvidence,
        domesticCheckEvidence: {
          ...platformAdminP1SystemOperationsEvidence.domesticCheckEvidence,
          clinicalOperationsStatus: 200,
        },
      },
    },
    {
      name: "国产化自检页面没有权限不足证据",
      operationsBody: {
        ...platformAdminP1SystemOperationsEvidence,
        domesticCheckEvidence: {
          ...platformAdminP1SystemOperationsEvidence.domesticCheckEvidence,
          clinicalPageForbidden: false,
        },
      },
    },
    {
      name: "P1 矩阵缺少国产化适配入口",
      matrixBody: {
        ...platformAdminP1EntryCoreActionsEvidence,
        entryActions: platformAdminP1EntryCoreActionsEvidence.entryActions.filter(
          (item) => item.menuKey !== "domestic-check",
        ),
      },
      operationsBody: platformAdminP1SystemOperationsEvidence,
    },
  ])("does not declare P1 S14 abnormal condition row when $name", (options) => {
    const evidence = platformAdminP1SystemOperationsEvidenceResult(options);

    expect(evidence.launchCoverage.scenarioConditionRows).toBeUndefined();
  });

  it("does not declare platform-admin P1 entry coverage from only the P0 platform-admin matrix", () => {
    const evidence = platformAdminEntryCoreActionsEvidenceResult(
      platformAdminEntryCoreActionsEvidence,
    );

    expect(evidence.launchCoverage.platformAdminP1EntryCoreActions).toBeUndefined();
  });

  it("declares clinical collaboration entry coverage only from complete real frontdesk matrix evidence", () => {
    const evidence = clinicalEntryCoreActionsEvidenceResult(clinicalEntryCoreActionsEvidence);

    expect(evidence.launchCoverage.clinicalEntryCoreActions?.map((item) => item.code)).toEqual([
      "CLINICAL_COLLABORATION_CORE_ACTIONS_REPRESENTATIVE",
    ]);
    expect(evidence.launchCoverage.platformAdminEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.platformAdminP1EntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.entryRepresentativeCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares quality management entry coverage only from complete real frontdesk matrix evidence", () => {
    const evidence = qualityManagementEntryCoreActionsEvidenceResult(
      qualityManagementEntryCoreActionsEvidence,
    );

    expect(
      evidence.launchCoverage.qualityManagementEntryCoreActions?.map((item) => item.code),
    ).toEqual(["QUALITY_MANAGEMENT_CORE_ACTIONS_REPRESENTATIVE"]);
    expect(evidence.launchCoverage.clinicalEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.platformAdminEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.platformAdminP1EntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.entryRepresentativeCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares knowledge-operations asset entry coverage only from complete real frontdesk supply-chain matrix evidence", () => {
    const evidence = knowledgeOperationsAssetEntryCoreActionsEvidenceResult(
      knowledgeOperationsAssetEntryCoreActionsEvidence,
    );

    expect(
      evidence.launchCoverage.knowledgeOperationsAssetEntryCoreActions?.map((item) => item.code),
    ).toEqual(["KNOWLEDGE_OPERATIONS_ASSET_ENTRY_FAMILY_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.knowledgeSupplyChainEvidenceMatrix?.map((item) => item.code),
    ).toEqual(["CONTROLLED_SOURCE_TO_RUNTIME_ROLLBACK_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.knowledgeSupplyChainEvidenceRows?.map((item) => item.code),
    ).toEqual([
      "SOURCE_CONTROL",
      "HUMAN_GOVERNANCE",
      "TERMINOLOGY_SYNC",
      "RUNTIME_LIFECYCLE",
      "LINEAGE_CONSUMERS",
      "SAFETY_BOUNDARY",
    ]);
    expect(evidence.launchCoverage.qualityManagementEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.clinicalEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.platformAdminEntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.platformAdminP1EntryCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.entryRepresentativeCoreActions).toBeUndefined();
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares a gap-aware 13 asset supply-chain matrix only from cross-spec real evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests(),
    });

    expect(
      evidence.launchCoverage.versionedAssetSupplyChainMatrix?.map((item) => item.code),
    ).toEqual(["THIRTEEN_VERSIONED_ASSETS_GAP_AWARE_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.versionedAssetRepresentativeRows?.map((item) => item.code),
    ).toEqual(fullVersionedAssetRepresentativeRows);
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("declares a gap-aware rollback-negative representative matrix only from asset-specific runtime consumer evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests(),
    });

    expect(
      evidence.launchCoverage.versionedAssetRollbackRepresentativeMatrix?.map((item) => item.code),
    ).toEqual(["GAP_AWARE_RUNTIME_CONSUMER_NEGATIVE_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.versionedAssetRollbackRepresentativeRows?.map((item) => item.code),
    ).toEqual(fullVersionedAssetRollbackRepresentativeRows);
  });

  it("declares dedicated release-contract rows only from terminology, field catalog and pathway specific evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests(),
    });

    expect(
      evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix?.map(
        (item) => item.code,
      ),
    ).toEqual(["TERMINOLOGY_FIELD_CATALOG_PATHWAY_DEDICATED_RELEASE_CONTRACTS"]);
    expect(
      evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows?.map((item) => item.code),
    ).toEqual(dedicatedReleaseContractRows);
  });

  it("does not declare dedicated release-contract rows without TERMINOLOGY specific contract evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "s2-s4-terminology-integration-rehearsal.spec.ts": s2s4RuntimeMappingEvidence({
            dedicatedReleaseContractEvidence: undefined,
          }),
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not declare dedicated release-contract rows without FIELD_CATALOG specific contract evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "diagnostic-critical-value-frontdesk.spec.ts": {
            ...diagnosticCriticalValueEvidence,
            dedicatedReleaseContractEvidence: undefined,
          },
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not declare dedicated release-contract rows without PATHWAY specific contract evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "pathway-lifecycle-frontdesk.spec.ts": pathwayLifecycleEvidence({
            dedicatedReleaseContractEvidence: undefined,
          }),
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not splice dedicated release-contract evidence across different tests in one spec file", () => {
    const tests = versionedAssetSupplyChainMatrixTests({
      bodyOverrides: {
        "s2-s4-terminology-integration-rehearsal.spec.ts": s2s4RuntimeMappingEvidence({
          dedicatedReleaseContractEvidence: undefined,
        }),
      },
    });
    tests.push({
      file: "/repo/frontend/e2e/s2-s4-terminology-integration-rehearsal.spec.ts",
      title: "同文件额外测试只带术语专用契约但缺少完整 S2/S4 强证据",
      status: "passed",
      attachments: [
        {
          name: "s2-s4-runtime-mapping-codes",
          contentType: "application/json",
          body: JSON.stringify(
            s2s4RuntimeMappingEvidence({
              scenarioCodes: ["S2"],
            }),
          ),
        },
      ],
    });

    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests,
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not declare dedicated release-contract rows when terminology mapping readback is mismatched", () => {
    const badTerminology = s2s4RuntimeMappingEvidence();
    badTerminology.inboundResult.mappedPayload.observations[0].mappingId = 999;
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "s2-s4-terminology-integration-rehearsal.spec.ts": badTerminology,
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not declare dedicated release-contract rows when field catalog evidence paths are empty", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "diagnostic-critical-value-frontdesk.spec.ts": {
            ...diagnosticCriticalValueEvidence,
            dedicatedReleaseContractEvidence: {
              ...diagnosticCriticalValueEvidence.dedicatedReleaseContractEvidence,
              fieldEvidencePaths: ["recommendation.explanation.missingRuntimeAssetEvidence"],
            },
          },
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not declare dedicated release-contract rows when pathway rollback removed asset is mismatched", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "pathway-lifecycle-frontdesk.spec.ts": pathwayLifecycleEvidence({
            rollbackNegativeEvidence: rollbackNegativeEvidence(
              [
                {
                  assetType: "PATHWAY",
                  assetIdentity: "PATHWAY.S6.OTHER",
                  versionId: "av-pathway-s6",
                },
                {
                  assetType: "ORDER_SET",
                  assetIdentity: "ORDER_SET.S6.COPD.RECHECK",
                  versionId: "av-order-set-s6",
                },
              ],
              "SPECIAL_DISEASE_PATHWAY_ORDER_SET",
            ),
          }),
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetDedicatedReleaseContractRows).toBeUndefined();
  });

  it("does not declare rollback-negative representative matrix from generic runtime release rollback alone", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "cdss-runtime-declarative-assets.spec.ts": withoutRollbackNegativeEvidence(
            cdssRuntimeDeclarativeAssets,
          ),
          "medication-safety-frontdesk.spec.ts": withoutRollbackNegativeEvidence(
            medicationSafetyFrontdeskEvidence,
          ),
          "critical-emergency-icu-frontdesk.spec.ts": withoutRollbackNegativeEvidence(
            criticalEmergencyIcuEvidence,
          ),
          "pathway-lifecycle-frontdesk.spec.ts": withoutRollbackNegativeEvidence(
            pathwayLifecycleEvidence(),
          ),
        },
      }),
    });

    expect(
      evidence.launchCoverage.versionedAssetSupplyChainMatrix?.map((item) => item.code),
    ).toEqual(["THIRTEEN_VERSIONED_ASSETS_GAP_AWARE_REPRESENTATIVE"]);
    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeRows).toBeUndefined();
  });

  it("does not declare rollback-negative representative matrix when VALUE_SET rollback consumer evidence is missing", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "cdss-runtime-declarative-assets.spec.ts": {
            ...cdssRuntimeDeclarativeAssets,
            rollbackNegativeEvidence: rollbackNegativeEvidence(
              [
                {
                  assetType: "FORMULA",
                  assetIdentity: "FORMULA.CDSS.RUNTIME",
                  versionId: "formula-v1",
                },
              ],
              "CDSS_DECLARATIVE_ASSET_EVALUATION",
            ),
          },
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeRows).toBeUndefined();
  });

  it("does not declare rollback-negative representative matrix when SAFETY/CDSS_RISK rollback consumer evidence is incomplete", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "medication-safety-frontdesk.spec.ts": {
            ...medicationSafetyFrontdeskEvidence,
            rollbackNegativeEvidence: {
              ...medicationSafetyFrontdeskEvidence.rollbackNegativeEvidence,
              consumerProbeMatchedRemovedAssets: true,
            },
          },
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeRows).toBeUndefined();
  });

  it("does not declare rollback-negative representative matrix without PATHWAY rollback consumer evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "critical-emergency-icu-frontdesk.spec.ts": withoutRollbackNegativeEvidence(
            criticalEmergencyIcuEvidence,
          ),
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeRows).toBeUndefined();
  });

  it("does not declare rollback-negative representative matrix without ORDER_SET rollback consumer evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "pathway-lifecycle-frontdesk.spec.ts": withoutRollbackNegativeEvidence(
            pathwayLifecycleEvidence(),
          ),
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRollbackRepresentativeRows).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix without EVALUATION production evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        omitFiles: ["quality-management-entry-core-actions-rehearsal.spec.ts"],
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("keeps EVALUATION as a known gap when quality management evidence lacks runtime consumer proof", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "quality-management-entry-core-actions-rehearsal.spec.ts": {
            ...qualityManagementEntryCoreActionsEvidence,
            evaluationAssetSupplyChainEvidence: {
              ...qualityManagementEntryCoreActionsEvidence.evaluationAssetSupplyChainEvidence,
              runtimeConsumerReadbackVerified: false,
            },
          },
        },
      }),
    });

    expect(
      evidence.launchCoverage.versionedAssetSupplyChainMatrix?.map((item) => item.code),
    ).toEqual(["THIRTEEN_VERSIONED_ASSETS_GAP_AWARE_REPRESENTATIVE"]);
    expect(
      evidence.launchCoverage.versionedAssetRepresentativeRows?.map((item) => item.code),
    ).toEqual(versionedAssetRepresentativeRows);
    expect(evidence.launchCoverage.versionedAssetKnownGaps?.map((item) => item.code)).toEqual([
      "EVALUATION",
    ]);
  });

  it("does not declare the 13 asset supply-chain matrix without runtime release rollback evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        omitFiles: ["runtime-release-frontdesk.spec.ts"],
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix without declarative runtime asset evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        omitFiles: ["cdss-runtime-declarative-assets.spec.ts"],
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix without FOLLOWUP runtime evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        omitFiles: ["nursing-continuity-frontdesk.spec.ts"],
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix without SAFETY/CDSS_RISK runtime evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        omitFiles: ["medication-safety-frontdesk.spec.ts"],
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix when FORMULA is missing from declarative asset evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "cdss-runtime-declarative-assets.spec.ts": {
            ...cdssRuntimeDeclarativeAssets,
            versionedAssets: ["VALUE_SET", "ACTION_CARD"],
          },
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix without ORDER_SET runtime consumer evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "pathway-lifecycle-frontdesk.spec.ts": pathwayLifecycleEvidence({
            orderSetRuntimeConsumer: undefined,
          }),
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare the 13 asset supply-chain matrix when knowledge scope overclaims complete production", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: versionedAssetSupplyChainMatrixTests({
        bodyOverrides: {
          "knowledge-operations-asset-entry-core-actions-rehearsal.spec.ts": {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence,
            scopeStatement:
              "知识运营资产入口族供给链代表矩阵，13 类医学资产全部生产闭环已完成，不代表完整上线验收。",
          },
        },
      }),
    });

    expect(evidence.launchCoverage.versionedAssetSupplyChainMatrix).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetRepresentativeRows).toBeUndefined();
    expect(evidence.launchCoverage.versionedAssetKnownGaps).toBeUndefined();
  });

  it("does not declare clinical entry coverage from platform-admin entry matrices", () => {
    const p0Evidence = platformAdminEntryCoreActionsEvidenceResult(
      platformAdminEntryCoreActionsEvidence,
    );
    const p1Evidence = platformAdminP1EntryCoreActionsEvidenceResult(
      platformAdminP1EntryCoreActionsEvidence,
    );

    expect(p0Evidence.launchCoverage.clinicalEntryCoreActions).toBeUndefined();
    expect(p1Evidence.launchCoverage.clinicalEntryCoreActions).toBeUndefined();
  });

  it("does not declare clinical entry coverage from the same attachment in a non-target spec", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-clinical-entry.spec.ts",
          title: "临床协同入口附件不能由非目标 spec 冒领",
          status: "passed",
          attachments: [
            {
              name: "clinical-entry-core-actions-codes",
              contentType: "application/json",
              body: JSON.stringify(clinicalEntryCoreActionsEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.clinicalEntryCoreActions).toBeUndefined();
  });

  it("does not declare quality management entry coverage from the same attachment in a non-target spec", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-quality-entry.spec.ts",
          title: "质量管理入口附件不能由非目标 spec 冒领",
          status: "passed",
          attachments: [
            {
              name: "quality-management-entry-core-actions-codes",
              contentType: "application/json",
              body: JSON.stringify(qualityManagementEntryCoreActionsEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.qualityManagementEntryCoreActions).toBeUndefined();
  });

  it("does not declare knowledge-operations asset entry coverage from the same attachment in a non-target spec", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/ad-hoc-knowledge-entry.spec.ts",
          title: "知识运营资产入口族附件不能由非目标 spec 冒领",
          status: "passed",
          attachments: [
            {
              name: "knowledge-operations-asset-entry-core-actions-codes",
              contentType: "application/json",
              body: JSON.stringify(knowledgeOperationsAssetEntryCoreActionsEvidence),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.knowledgeOperationsAssetEntryCoreActions).toBeUndefined();
  });

  it.each([
    {
      name: "缺少机构生效版本入口",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.filter(
          (item) => item.menuKey !== "runtime-releases",
        ),
      },
    },
    {
      name: "知识生产入口路径不匹配",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "knowledge-production"
            ? { ...item, path: "/knowledge/governance" }
            : item,
        ),
      },
    },
    {
      name: "术语字典入口不是医疗引擎运营员角色",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "terminology-mapping" ? { ...item, role: "platform-admin" } : item,
        ),
      },
    },
    {
      name: "机构生效版本服务不是 2xx",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "runtime-releases" ? { ...item, serviceStatus: 409 } : item,
        ),
      },
    },
    {
      name: "机构生效版本缺少回滚服务",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "runtime-releases"
            ? {
                ...item,
                serviceOperation:
                  "POST /api/v1/engine/releases/hospitals/{hospitalId}/runtime-releases",
              }
            : item,
        ),
      },
    },
    {
      name: "模型能力入口缺少禁止模型直发证据",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "ai-workflows" ? { ...item, noDirectPublishVerified: false } : item,
        ),
      },
    },
    {
      name: "来源与血缘入口缺少来源对象审计链",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        entryActions: knowledgeOperationsAssetEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "provenance" ? { ...item, sourceAuditVerified: false } : item,
        ),
      },
    },
    {
      name: "缺少 134 唯一正式链路边界",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        formalChain: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.formalChain,
          officialProductionInside134: false,
        },
      },
    },
    {
      name: "缺少 13 类资产清单",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        assetTypesCovered: runtimeReleaseVersionedAssets.filter((item) => item !== "FORMULA"),
      },
    },
    {
      name: "缺少运行消费读回门禁",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        supplyChainGates: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.supplyChainGates,
          runtimeConsumerReadbackVerified: false,
        },
      },
    },
    {
      name: "scope 过度宣称全知识供给链完整上线",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        scopeStatement:
          "知识运营资产入口族供给链代表矩阵，全知识供给链完整上线已完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称 13 类医学资产全部生产闭环",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        scopeStatement:
          "知识运营资产入口族供给链代表矩阵，不代表全知识供给链完整上线，13 类医学资产全部生产闭环已完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称所有医学知识和术语已收集完成",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        scopeStatement:
          "知识运营资产入口族供给链代表矩阵，不代表全知识供给链完整上线，所有医学知识和术语体系已收集完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称完整上线验收",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        scopeStatement:
          "知识运营资产入口族供给链代表矩阵，不代表全知识供给链完整上线，不代表 13 类医学资产全部生产闭环，完整上线验收已完成。",
      },
    },
  ])("does not declare knowledge-operations asset entry coverage when $name", ({ body }) => {
    expectNoKnowledgeOperationsEntryCoreActionsCoverage(body);
  });

  it.each([
    {
      name: "缺少上传解析 job",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          sourceControl: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .sourceControl,
            uploadParseJobSucceeded: false,
          },
        },
      },
    },
    {
      name: "缺少解析来源版本",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          sourceControl: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .sourceControl,
            parseResultSourceVersionId: null,
          },
        },
      },
    },
    {
      name: "缺少解析片段 ID",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          sourceControl: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .sourceControl,
            sourceFragmentIds: [],
          },
        },
      },
    },
    {
      name: "解析片段数与回读片段 ID 不一致",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          sourceControl: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .sourceControl,
            parsedFragmentCount: 2,
            sourceFragmentIds: [1001],
          },
        },
      },
    },
    {
      name: "缺少来源片段",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          sourceControl: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .sourceControl,
            sourceFragmentRegistered: false,
          },
        },
      },
    },
    {
      name: "缺少人工审核批准",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          humanGovernance: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .humanGovernance,
            candidateApproved: false,
          },
        },
      },
    },
    {
      name: "缺少院内术语同步",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          terminologySync: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .terminologySync,
            localTermRegistered: false,
          },
        },
      },
    },
    {
      name: "缺少第三方运行契约读回",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          runtimeLifecycle: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .runtimeLifecycle,
            runtimeConsumerReadbackVerified: false,
          },
        },
      },
    },
    {
      name: "缺少图谱来源消费者",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          lineageConsumers: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .lineageConsumers,
            graphProjectionVerified: false,
          },
        },
      },
    },
    {
      name: "缺少模型直发阻断",
      body: {
        ...knowledgeOperationsAssetEntryCoreActionsEvidence,
        knowledgeSupplyChainEvidence: {
          ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence,
          safetyBoundary: {
            ...knowledgeOperationsAssetEntryCoreActionsEvidence.knowledgeSupplyChainEvidence
              .safetyBoundary,
            modelDirectPublishBlocked: false,
          },
        },
      },
    },
  ])("does not declare knowledge supply-chain evidence matrix when $name", ({ body }) => {
    const evidence = knowledgeOperationsAssetEntryCoreActionsEvidenceResult(body);

    expect(
      evidence.launchCoverage.knowledgeOperationsAssetEntryCoreActions?.map((item) => item.code),
    ).toEqual(["KNOWLEDGE_OPERATIONS_ASSET_ENTRY_FAMILY_REPRESENTATIVE"]);
    expect(evidence.launchCoverage.knowledgeSupplyChainEvidenceMatrix).toBeUndefined();
    expect(evidence.launchCoverage.knowledgeSupplyChainEvidenceRows).toBeUndefined();
  });

  it.each([
    {
      name: "缺少质量风险概览入口",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.filter(
          (item) => item.menuKey !== "qc-dashboard",
        ),
      },
    },
    {
      name: "医保审核入口路径不匹配",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "insurance-audit" ? { ...item, path: "/qc/alerts" } : item,
        ),
      },
    },
    {
      name: "评价指标不是医疗引擎运营员角色",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "qc-eval-sets" ? { ...item, role: "platform-admin" } : item,
        ),
      },
    },
    {
      name: "整改复核服务不是 2xx",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "qc-alerts" ? { ...item, serviceStatus: 409 } : item,
        ),
      },
    },
    {
      name: "医保审核服务缺少 insurance-audit",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "insurance-audit"
            ? { ...item, serviceOperation: "POST /api/v1/engine/quality/case-review" }
            : item,
        ),
      },
    },
    {
      name: "质量风险概览没有来源对象审计链",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        entryActions: qualityManagementEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "qc-dashboard" ? { ...item, sourceAuditVerified: false } : item,
        ),
      },
    },
    {
      name: "scope 过度宣称质量管理完整上线",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        scopeStatement:
          "质量管理入口核心动作代表矩阵，质量管理 4 个入口全部完整上线已完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称完整 DRG/DIP",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        scopeStatement:
          "质量管理入口核心动作代表矩阵，不代表质量管理 4 个入口全部完整上线，完整 DRG/DIP 和医保支付审核已完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称完整上线验收",
      body: {
        ...qualityManagementEntryCoreActionsEvidence,
        scopeStatement:
          "质量管理入口核心动作代表矩阵，不代表质量管理 4 个入口全部完整上线，不代表完整 DRG/DIP 或医保支付审核，完整上线验收已完成。",
      },
    },
  ])("does not declare quality management entry coverage when $name", ({ body }) => {
    expectNoQualityManagementEntryCoreActionsCoverage(body);
  });

  it.each([
    {
      name: "缺少随访协同入口",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.filter(
          (item) => item.menuKey !== "clinical-followup",
        ),
      },
    },
    {
      name: "患者路径入口路径不匹配",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "patient-pathways" ? { ...item, path: "/pathway/templates" } : item,
        ),
      },
    },
    {
      name: "CDSS 提醒推荐不是临床用户角色",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "cdss-fatigue" ? { ...item, role: "engine-operator" } : item,
        ),
      },
    },
    {
      name: "协同任务完成服务不是 2xx",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "workflow-todos" ? { ...item, serviceStatus: 409 } : item,
        ),
      },
    },
    {
      name: "MPI 服务操作错配为随访计划生成",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        entryActions: clinicalEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "mpi"
            ? { ...item, serviceOperation: "POST /api/v1/engine/followup/plans/generate" }
            : item,
        ),
      },
    },
    {
      name: "scope 过度宣称完整临床流程",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        scopeStatement:
          "临床协同入口核心动作代表矩阵，完整临床流程已完成，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称完整 S0-S40",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        scopeStatement:
          "临床协同入口核心动作代表矩阵，不代表完整临床流程，不代表 34 个入口全部业务动作闭环，完整 S0-S40 已完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称完整上线验收完成",
      body: {
        ...clinicalEntryCoreActionsEvidence,
        scopeStatement:
          "临床协同入口核心动作代表矩阵，不代表完整临床流程，不代表 34 个入口全部业务动作闭环，不代表完整 S0-S40，完整上线验收已完成。",
      },
    },
  ])("does not declare clinical entry core action coverage when $name", ({ body }) => {
    expectNoClinicalEntryCoreActionsCoverage(body);
  });

  it.each([
    {
      name: "缺少国产化适配自检入口",
      body: {
        ...platformAdminP1EntryCoreActionsEvidence,
        entryActions: platformAdminP1EntryCoreActionsEvidence.entryActions.filter(
          (item) => item.menuKey !== "domestic-check",
        ),
      },
    },
    {
      name: "运行诊断入口路径不匹配",
      body: {
        ...platformAdminP1EntryCoreActionsEvidence,
        entryActions: platformAdminP1EntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "runtime-diagnostics" ? { ...item, path: "/system/providers" } : item,
        ),
      },
    },
    {
      name: "国产化适配导出服务不是 2xx",
      body: {
        ...platformAdminP1EntryCoreActionsEvidence,
        entryActions: platformAdminP1EntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "domestic-check" ? { ...item, serviceStatus: 500 } : item,
        ),
      },
    },
    {
      name: "scope 过度宣称 6 个平台管理员入口全部闭环",
      body: {
        ...platformAdminP1EntryCoreActionsEvidence,
        scopeStatement:
          "平台管理员 P1 系统运维入口核心动作代表矩阵，6 个平台管理员入口全部闭环已完成，不代表完整上线验收。",
      },
    },
  ])("does not declare platform-admin P1 entry coverage when $name", ({ body }) => {
    expectNoPlatformAdminP1EntryCoreActionsCoverage(body);
  });

  it.each([
    {
      name: "缺少系统接入入口",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.filter(
          (item) => item.menuKey !== "adapter-hub",
        ),
      },
    },
    {
      name: "身份来源入口没有 menuKey",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "identity-bindings" ? { ...item, menuKey: "" } : item,
        ),
      },
    },
    {
      name: "服务运行保障不是平台管理员角色",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "system-providers" ? { ...item, role: "engine-operator" } : item,
        ),
      },
    },
    {
      name: "服务机构服务端状态不是 2xx",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "tenant-onboarding" ? { ...item, serviceStatus: 500 } : item,
        ),
      },
    },
    {
      name: "scope 过度宣称 34 个入口全部业务动作已闭环",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        scopeStatement:
          "平台管理员 P0 入口核心动作代表矩阵，34 个入口全部业务动作闭环已完成，不代表完整上线验收。",
      },
    },
    {
      name: "服务机构入口缺少开通租户端点",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "tenant-onboarding"
            ? { ...item, serviceOperation: "GET /api/v1/admin/tenants" }
            : item,
        ),
      },
    },
    {
      name: "身份来源入口缺少绑定端点",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "identity-bindings"
            ? { ...item, serviceOperation: "GET /api/v1/compliance/identity-bindings" }
            : item,
        ),
      },
    },
    {
      name: "系统接入口缺少数据质量报告端点",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "adapter-hub"
            ? { ...item, serviceOperation: "POST /api/v1/engine/integration/adapters" }
            : item,
        ),
      },
    },
    {
      name: "服务运行保障入口缺少运行快照端点",
      body: {
        ...platformAdminEntryCoreActionsEvidence,
        entryActions: platformAdminEntryCoreActionsEvidence.entryActions.map((item) =>
          item.menuKey === "system-providers"
            ? { ...item, serviceOperation: "GET /api/v1/system/providers" }
            : item,
        ),
      },
    },
  ])("does not declare platform-admin entry core action coverage when $name", ({ body }) => {
    expectNoPlatformAdminEntryCoreActionsCoverage(body);
  });

  it.each([
    {
      name: "缺少来源血缘入口",
      body: {
        ...sixEntryCoreActionsEvidence,
        entryActions: sixEntryCoreActionsEvidence.entryActions.filter(
          (item) => item.path !== "/advanced/provenance",
        ),
      },
    },
    {
      name: "缺少审计员角色",
      body: {
        ...sixEntryCoreActionsEvidence,
        entryActions: sixEntryCoreActionsEvidence.entryActions.map((item) =>
          item.role === "auditor" ? { ...item, role: "engine-operator" } : item,
        ),
      },
    },
    {
      name: "scope 过度宣称 34 个入口全部业务动作已闭环",
      body: {
        ...sixEntryCoreActionsEvidence,
        scopeStatement:
          "六入口核心动作代表闭环，34 个入口全部业务动作闭环已完成，不代表完整上线验收。",
      },
    },
    {
      name: "临床用户通知偏好没有服务端 2xx 证据",
      body: {
        ...sixEntryCoreActionsEvidence,
        entryActions: sixEntryCoreActionsEvidence.entryActions.map((item) =>
          item.path === "/notifications/settings" ? { ...item, serviceStatus: 500 } : item,
        ),
      },
    },
    {
      name: "临床规则动作没有回读证据",
      body: {
        ...sixEntryCoreActionsEvidence,
        entryActions: sixEntryCoreActionsEvidence.entryActions.map((item) =>
          item.path === "/rule/definitions" ? { ...item, readbackVerified: false } : item,
        ),
      },
    },
    {
      name: "安全与配置动作没有审计证据",
      body: {
        ...sixEntryCoreActionsEvidence,
        entryActions: sixEntryCoreActionsEvidence.entryActions.map((item) =>
          item.path === "/security/baseline" ? { ...item, auditVerified: false } : item,
        ),
      },
    },
  ])("does not declare six-entry core action coverage when $name", ({ body }) => {
    expectNoSixEntryCoreActionsCoverage(body);
  });

  it.each([
    {
      name: "缺少审计证据导出验签入口",
      roleBody: {
        ...fourRoleCoreActionsEvidence,
        roleActions: fourRoleCoreActionsEvidence.roleActions.filter(
          (item) => item.path !== "/admin/audit",
        ),
      },
      entryBody: sixEntryCoreActionsEvidence,
    },
    {
      name: "审计入口服务端操作不是真实验签端点",
      roleBody: fourRoleCoreActionsWithAuditorOverride({
        serviceOperation: "GET /api/v1/compliance/audit/events",
      }),
      entryBody: sixEntryCoreActionsEvidence,
    },
    {
      name: "安全基线入口缺少配置变更端点",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/security/baseline", {
        serviceOperation: "PATCH /api/v1/system/config-items/{key}",
      }),
    },
    {
      name: "通知已读入口缺少引擎通知已读端点",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/notifications", {
        serviceOperation: "POST /api/v1/notifications/{notificationId}/read",
      }),
    },
    {
      name: "通知偏好入口没有审计证据",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/notifications/settings", {
        auditVerified: false,
      }),
    },
    {
      name: "通知偏好入口不是临床使用者",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/notifications/settings", {
        role: "auditor",
      }),
    },
    {
      name: "来源血缘入口缺少来源详情端点",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/advanced/provenance", {
        serviceOperation: "GET /api/v1/provenance/knowledge-identities",
      }),
    },
    {
      name: "来源血缘入口没有回读证据",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/advanced/provenance", {
        readbackVerified: false,
      }),
    },
    {
      name: "安全基线入口服务端状态不是 2xx",
      roleBody: fourRoleCoreActionsEvidence,
      entryBody: sixEntryCoreActionsWithPathOverride("/security/baseline", {
        serviceStatus: 500,
      }),
    },
  ])(
    "does not declare compliance personal entry coverage when $name",
    ({ roleBody, entryBody }) => {
      expectNoComplianceWorkbenchPersonalEntryCoverage(roleBody, entryBody);
    },
  );

  it("does not declare four-role core action coverage from menu or route reachability evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/product-role-journeys.spec.ts",
          title: "四职责桌面和移动端真实菜单点击可达性",
          status: "passed",
          attachments: [
            {
              name: "role-menu-interaction-codes-desktop-1440",
              contentType: "application/json",
              body: JSON.stringify({
                scopeStatement: "只证明菜单点击和路由可达，不代表每页核心业务动作已闭环。",
                roleActions: fourRoleCoreActionsEvidence.roleActions,
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.roleRepresentativeCoreActions).toBeUndefined();
  });

  it.each([
    {
      name: "缺少审计员主动作",
      body: {
        ...fourRoleCoreActionsEvidence,
        roleActions: fourRoleCoreActionsEvidence.roleActions.filter(
          (item) => item.role !== "auditor",
        ),
      },
    },
    {
      name: "scope 过度宣称 34 个入口全部业务动作已闭环",
      body: {
        ...fourRoleCoreActionsEvidence,
        scopeStatement:
          "四职责主动作代表闭环，34 个入口全部业务动作闭环已完成，不代表完整上线验收。",
      },
    },
    {
      name: "scope 过度宣称完整上线验收已完成",
      body: {
        ...fourRoleCoreActionsEvidence,
        scopeStatement:
          "四职责主动作代表闭环，不代表 34 个入口全部业务动作闭环，完整上线验收已完成。",
      },
    },
    {
      name: "scope 过度宣称完整上线已完成",
      body: {
        ...fourRoleCoreActionsEvidence,
        scopeStatement:
          "四职责主动作代表闭环，不代表 34 个入口全部业务动作闭环，不代表完整上线验收，完整上线已完成。",
      },
    },
    {
      name: "scope 过度宣称全量验收已完成",
      body: {
        ...fourRoleCoreActionsEvidence,
        scopeStatement:
          "四职责主动作代表闭环，不代表 34 个入口全部业务动作闭环，不代表完整上线验收，全量验收已完成。",
      },
    },
    {
      name: "运营员动作没有服务端 2xx 证据",
      body: {
        ...fourRoleCoreActionsEvidence,
        roleActions: fourRoleCoreActionsEvidence.roleActions.map((item) =>
          item.role === "engine-operator" ? { ...item, serviceStatus: 503 } : item,
        ),
        engineOperator: {
          ...fourRoleCoreActionsEvidence.engineOperator,
          serviceStatus: 503,
        },
      },
    },
    {
      name: "临床使用者动作没有回读证据",
      body: {
        ...fourRoleCoreActionsEvidence,
        roleActions: fourRoleCoreActionsEvidence.roleActions.map((item) =>
          item.role === "clinical-user" ? { ...item, readbackVerified: false } : item,
        ),
        clinicalUser: {
          ...fourRoleCoreActionsEvidence.clinicalUser,
          readbackVerified: false,
        },
      },
    },
    {
      name: "平台管理员动作没有审计证据",
      body: {
        ...fourRoleCoreActionsEvidence,
        roleActions: fourRoleCoreActionsEvidence.roleActions.map((item) =>
          item.role === "platform-admin" ? { ...item, auditVerified: false } : item,
        ),
        platformAdmin: {
          ...fourRoleCoreActionsEvidence.platformAdmin,
          auditVerified: false,
        },
      },
    },
  ])("does not declare four-role core action coverage when $name", ({ body }) => {
    expectNoFourRoleCoreActionsCoverage(body);
  });

  it("does not declare coverage when the proving spec fails or the run is flaky", () => {
    const failedEvidence = buildBrowserE2eLaunchEvidence({
      stats: { ...passedStats, expected: 0, unexpected: 1 },
      tests: [
        {
          file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
          title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
          status: "failed",
        },
      ],
    });
    const flakyEvidence = buildBrowserE2eLaunchEvidence({
      stats: { ...passedStats, flaky: 1 },
      tests: [
        {
          file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
          title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
          status: "passed",
        },
      ],
    });

    expect(failedEvidence.launchCoverage).toEqual({});
    expect(flakyEvidence.launchCoverage).toEqual({});
  });

  it("does not declare coverage when a proving spec only passed after retry", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
      stats: passedStats,
      tests: [
        {
          file: "/repo/frontend/e2e/stakeholder-view-rehearsal.spec.ts",
          title: "十二类业务视角均能通过四职责账号进入真实页面并看到对应业务能力",
          status: "passed",
          outcome: "flaky",
        },
      ],
    });

    expect(evidence.launchCoverage).toEqual({});
  });
});
