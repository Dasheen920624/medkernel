import { writeFile } from "node:fs/promises";

import type { TestInfo } from "@playwright/test";

export type QualityManagementEntryCoreActionMenuKey =
  | "qc-dashboard"
  | "qc-alerts"
  | "insurance-audit"
  | "qc-eval-sets";

export type QualityManagementEntryCoreActionEvidence = {
  menuKey: QualityManagementEntryCoreActionMenuKey;
  role: "engine-operator";
  path: string;
  frontdeskAction: string;
  serviceOperation: string;
  serviceStatus: number;
  readbackVerified: boolean;
  auditVerified: boolean;
  sourceAuditVerified?: boolean;
};

export type QualityManagementEvaluationAssetEvidence = {
  assetType: "EVALUATION";
  assetIdentity: string;
  versionId: string;
  indicatorId: string;
  indicatorPublished: boolean;
  indicatorActivated: boolean;
  runtimeActivationVerified: boolean;
  runtimeConsumerReadbackVerified: boolean;
  insuranceAuditEvaluationRunVerified: boolean;
  findingBoundToIndicatorVerified: boolean;
  auditVerified: boolean;
  activationRequest: unknown;
  runtimeReadback: unknown;
  runtimeConsumer: unknown;
};

export type QualityManagementRollbackNegativeEvidence = {
  rollbackPosted: boolean;
  currentRuntimeReadbackVerified: boolean;
  runtimeConsumerReadbackVerified: boolean;
  consumer: "QUALITY_MANAGEMENT_EVALUATION_INDICATOR";
  consumerProbeMatchedRemovedAssets: false;
  removedAssets: Array<{
    assetType: "EVALUATION";
    assetIdentity: string;
    versionId: string;
  }>;
  currentRuntime: unknown;
  runtimeConsumer: unknown;
};

export type MedicalRecordQualityIssueEvidence = {
  operation: "CASE_REVIEW_DRG_INSURANCE_AUDIT";
  caseReviewStatus: number;
  drgGroupingStatus: number;
  insuranceAuditStatus: number;
  auditStatus: "ISSUE_FOUND";
  issueId: string;
  evaluationRunId: string;
  findingId: string;
  findingCount: number;
  taskCount: number;
};

export type MedicalRecordInsurancePaymentConsumerSliceEvidence = {
  systemFamilyCode: "MEDICAL_RECORD_INSURANCE_PAYMENT";
  familyName: string;
  canonicalResources: ["Claim"];
  sourceSystems: ["MEDKERNEL_FRONTDESK"];
  consumer: "INSURANCE_AUDIT";
  consumerVerified: boolean;
  standardResourceVerified: boolean;
  evaluationRunVerified: boolean;
  rectificationClosedVerified: boolean;
  auditVerified: boolean;
  noAutoPaymentDecision: boolean;
  claimResourcePath: "clinicalContext.resources.claims[0]";
  issueIdPath: "medicalRecordQualityIssueEvidence.issueId";
  evaluationRunIdPath: "medicalRecordQualityIssueEvidence.evaluationRunId";
  scopeStatement: string;
};

const pathByMenuKey: Record<QualityManagementEntryCoreActionMenuKey, string> = {
  "qc-dashboard": "/qc/dashboard",
  "qc-alerts": "/qc/alerts",
  "insurance-audit": "/qc/insurance",
  "qc-eval-sets": "/qc/eval/sets",
};

export const qualityManagementEntryCoreActionScopeStatement =
  "质量管理入口核心动作代表矩阵：围绕质量风险概览、质量问题与整改、医保审核和评价指标四个入口完成真实前台核心动作、服务回读与审计或来源对象审计链证据；不代表质量管理 4 个入口全部完整上线，不代表完整 DRG/DIP 或医保支付审核，不代表完整 S9-S11，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。";

export async function attachQualityManagementEntryCoreActionEvidence(
  testInfo: TestInfo,
  evidence: QualityManagementEntryCoreActionEvidence | QualityManagementEntryCoreActionEvidence[],
  assetEvidence?: {
    evaluationAssetSupplyChainEvidence: QualityManagementEvaluationAssetEvidence;
    rollbackNegativeEvidence: QualityManagementRollbackNegativeEvidence;
    medicalRecordQualityIssueEvidence?: MedicalRecordQualityIssueEvidence;
    medicalRecordInsurancePaymentConsumerSlice?: MedicalRecordInsurancePaymentConsumerSliceEvidence;
    clinicalContext?: unknown;
  },
) {
  const entryActions = Array.isArray(evidence) ? evidence : [evidence];
  for (const action of entryActions) {
    assertQualityManagementEntryCoreAction(action);
  }
  const recordPath = testInfo.outputPath("quality-management-entry-core-actions-codes.json");
  await writeFile(
    recordPath,
    `${JSON.stringify(
      {
        matrixCode: "QUALITY_MANAGEMENT_ENTRY_CORE_ACTIONS",
        scopeStatement: qualityManagementEntryCoreActionScopeStatement,
        ...assetEvidence,
        entryActions,
        scenarioConditionEvidence: buildQualityManagementScenarioConditionEvidence(entryActions),
      },
      null,
      2,
    )}\n`,
    "utf8",
  );
  await testInfo.attach("quality-management-entry-core-actions-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function buildQualityManagementScenarioConditionEvidence(
  entryActions: QualityManagementEntryCoreActionEvidence[],
) {
  const rows = [];
  const insuranceAudit = entryActions.find((action) => action.menuKey === "insurance-audit");
  if (insuranceAudit) {
    rows.push({
      code: "S9__ABNORMAL",
      scenarioCode: "S9",
      condition: "ABNORMAL",
      source: "MEDICAL_RECORD_CASE_REVIEW_ISSUE_FOUND",
      evidence: [
        "病历内涵质控真实前台执行病案质控并命中质量问题",
        `服务操作：${insuranceAudit.serviceOperation}`,
        "医保审核返回 ISSUE_FOUND、评价运行、质量问题和整改任务",
      ],
    });
    rows.push({
      code: "S10__NORMAL",
      scenarioCode: "S10",
      condition: "NORMAL",
      source: "INSURANCE_AUDIT_SERVICE_READBACK",
      evidence: [
        "医保审核真实前台执行病案质控、DRG 分组和医保审核",
        `服务操作：${insuranceAudit.serviceOperation}`,
        "服务回读命中问题并派发整改",
        "医保审核生成的质量问题写入审计",
      ],
    });
  }
  const qualityAlerts = entryActions.find((action) => action.menuKey === "qc-alerts");
  if (qualityAlerts) {
    rows.push({
      code: "S11__NORMAL",
      scenarioCode: "S11",
      condition: "NORMAL",
      source: "QUALITY_ALERT_RECTIFICATION_REVIEW",
      evidence: [
        "质量问题整改真实前台提交整改证据",
        `服务操作：${qualityAlerts.serviceOperation}`,
        "整改复核服务关闭本轮质量问题",
        "质量整改闭环写入审计",
      ],
    });
  }
  return rows;
}

function assertQualityManagementEntryCoreAction(action: QualityManagementEntryCoreActionEvidence) {
  if (action.role !== "engine-operator") {
    throw new Error(`${action.menuKey} 质量管理入口动作必须由 engine-operator 执行`);
  }
  if (action.path !== pathByMenuKey[action.menuKey]) {
    throw new Error(`${action.menuKey} 质量管理入口动作路径不匹配：${action.path}`);
  }
  if (
    !action.frontdeskAction ||
    !action.serviceOperation ||
    action.serviceStatus < 200 ||
    action.serviceStatus >= 300 ||
    !action.readbackVerified ||
    !action.auditVerified ||
    (action.menuKey === "qc-dashboard" && action.sourceAuditVerified !== true)
  ) {
    throw new Error(`${action.menuKey} 质量管理入口动作证据不完整`);
  }
}
