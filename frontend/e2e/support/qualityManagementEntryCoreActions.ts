import { writeFile } from "node:fs/promises";

import type { TestInfo } from "@playwright/test";

export type QualityManagementEntryCoreActionMenuKey =
  | "qc-dashboard"
  | "qc-alerts"
  | "qc-insurance"
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

const pathByMenuKey: Record<QualityManagementEntryCoreActionMenuKey, string> = {
  "qc-dashboard": "/qc/dashboard",
  "qc-alerts": "/qc/alerts",
  "qc-insurance": "/qc/insurance",
  "qc-eval-sets": "/qc/eval/sets",
};

export const qualityManagementEntryCoreActionScopeStatement =
  "质量管理入口核心动作代表矩阵：围绕质量风险概览、质量问题与整改、医保审核和评价指标四个入口完成真实前台核心动作、服务回读与审计或来源对象审计链证据；不代表质量管理 4 个入口全部完整上线，不代表完整 DRG/DIP 或医保支付审核，不代表完整 S9-S11，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。";

export async function attachQualityManagementEntryCoreActionEvidence(
  testInfo: TestInfo,
  evidence:
    | QualityManagementEntryCoreActionEvidence
    | QualityManagementEntryCoreActionEvidence[],
  assetEvidence?: {
    evaluationAssetSupplyChainEvidence: QualityManagementEvaluationAssetEvidence;
    rollbackNegativeEvidence: QualityManagementRollbackNegativeEvidence;
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
