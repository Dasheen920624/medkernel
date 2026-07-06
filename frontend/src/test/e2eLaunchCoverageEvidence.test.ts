import { describe, expect, it } from "vitest";

import { buildBrowserE2eLaunchEvidence } from "../../e2e/support/launchCoverageEvidence.ts";

const passedStats = {
  startTime: "2026-07-06T08:00:00.000Z",
  expected: 1,
  unexpected: 0,
  flaky: 0,
  skipped: 0,
};

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

const runtimeReleaseApiEvidence = {
  impactSimulationRun: true,
  activationPosted: true,
  activationRequestCarriesRequiredAssets: true,
  currentReleaseReadback: true,
  runtimeConsumerReadback: true,
  rollbackPosted: true,
  rollbackCurrentReleaseReadback: true,
  rollbackRuntimeConsumerReadback: true,
};

const runtimeReleaseScenarioEvidence = [
  {
    observedStages: [
      "前台展示并勾选 13 类平台标准资产",
      "前台评估机构生效版本发布影响",
      "前台生成携带 13 类资产闭包的机构生效版本",
      "后端回读当前机构生效版本资产闭包",
      "第三方运行契约读取同一机构生效版本",
      "前台从历史机构生效版本回滚",
      "回滚后后端和第三方运行契约读取同一修订",
    ],
  },
];

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
    expect(evidence.launchCoverage.scenarios).toBeUndefined();
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
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

  it("declares third-party system family coverage only when the real spec attaches all observed family codes", () => {
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

  it("declares real-frontdesk scenario coverage only when the passed spec attaches complete scenario evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
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
                scenarioCodes: ["S10", "S11", "S12"],
                scenarioEvidence: [
                  { code: "S10", observedStages: ["前台执行医保审核并联动质量整改"] },
                  {
                    code: "S11",
                    observedStages: [
                      "前台创建发布并激活 CLAIM 评价指标",
                      "前台提交并复核关闭质量整改任务",
                    ],
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
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual([
      "S10",
      "S11",
      "S12",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
  });

  it("declares service organization coverage only when the passed spec attaches complete frontdesk evidence", () => {
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
                scenarioCodes: ["S1", "S14"],
                organizationLevels: ["HOSPITAL", "DEPARTMENT"],
                serviceCombinations: ["ONBOARDING_INTEGRATION", "COMPLIANCE_OPERATIONS"],
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
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S1", "S14"]);
    expect(evidence.launchCoverage.organizationLevels?.map((item) => item.code)).toEqual([
      "HOSPITAL",
      "DEPARTMENT",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "ONBOARDING_INTEGRATION",
      "COMPLIANCE_OPERATIONS",
    ]);
    expect(evidence.launchCoverage.thirdPartySystemFamilies).toBeUndefined();
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
                    observedStages: [
                      "前台登记标准发现项术语",
                      "前台创建证据完整诊断资产草稿",
                      "前台登记诊断标准",
                      "前台登记验证病例",
                    ],
                  },
                ],
              }),
            },
          ],
        },
      ],
    });

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

  it("declares S7 source lineage coverage only from a complete graph provenance attachment", () => {
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
                  citationBound: true,
                  candidateApproved: true,
                  graphProjectionRebuilt: true,
                  provenanceReadback: true,
                  graphNodeExplored: true,
                  traceEvidenceVisible: true,
                },
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
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S7"]);
    expect(evidence.launchCoverage.semanticFamilies?.map((item) => item.code)).toEqual([
      "SOURCE_VALIDITY",
    ]);
    expect(evidence.launchCoverage.productLayers).toBeUndefined();
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

  it("declares embedded business host coverage only when the passed spec attaches complete real service evidence", () => {
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
                  hostMessageReceived: true,
                },
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
              }),
            },
          ],
        },
      ],
    });

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S8"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "DELIVERY_FEEDBACK",
    ]);
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual([
      "EMBEDDED_COMPONENT",
    ]);
    expect(evidence.launchCoverage.serviceCombinations).toBeUndefined();
  });

  it("declares S6 pathway lifecycle evidence slice without packaging milestone config as ten-stage runtime coverage", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
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

    expect(evidence.launchCoverage.scenarios?.map((item) => item.code)).toEqual(["S6"]);
    expect(evidence.launchCoverage.productLayers?.map((item) => item.code)).toEqual([
      "CLINICAL_EXECUTION",
    ]);
    expect(evidence.launchCoverage.serviceCombinations?.map((item) => item.code)).toEqual([
      "SPECIAL_DISEASE_PATHWAY",
    ]);
    expect(evidence.launchCoverage.specialDiseaseStages).toBeUndefined();
  });

  it("does not declare S6 pathway lifecycle coverage from a passed spec without complete real lifecycle evidence", () => {
    const evidence = buildBrowserE2eLaunchEvidence({
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
