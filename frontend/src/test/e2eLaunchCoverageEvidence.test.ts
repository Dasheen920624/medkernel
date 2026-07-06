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
    expect(evidence.launchCoverage.deliveryShapes?.map((item) => item.code)).toEqual([
      "API_EVENT",
    ]);
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
