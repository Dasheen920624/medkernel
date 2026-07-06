import { describe, expect, it } from "vitest";

import { buildBrowserE2eLaunchEvidence } from "../../e2e/support/launchCoverageEvidence.ts";

const passedStats = {
  startTime: "2026-07-06T08:00:00.000Z",
  expected: 1,
  unexpected: 0,
  flaky: 0,
  skipped: 0,
};

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
