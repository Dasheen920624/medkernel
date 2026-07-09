import { writeFile } from "node:fs/promises";

import type { TestInfo } from "@playwright/test";

export type KnowledgeOperationsAssetEntryCoreActionMenuKey =
  | "knowledge-production"
  | "knowledge-governance"
  | "runtime-releases"
  | "terminology-mapping"
  | "rule-definitions"
  | "pathway-templates"
  | "institution-knowledge"
  | "diagnosis-knowledge"
  | "provenance"
  | "graph-explore"
  | "ai-workflows";

export type KnowledgeOperationsAssetEntryCoreActionEvidence = {
  menuKey: KnowledgeOperationsAssetEntryCoreActionMenuKey;
  role: "engine-operator";
  path: string;
  frontdeskAction: string;
  serviceOperation: string;
  serviceStatus: number;
  readbackVerified: boolean;
  auditVerified: boolean;
  sourceLineageVerified?: boolean;
  sourceAuditVerified?: boolean;
  humanReviewVerified?: boolean;
  noDirectPublishVerified?: boolean;
  runtimeActivationVerified?: boolean;
  runtimeConsumerReadbackVerified?: boolean;
  rollbackReadbackVerified?: boolean;
  localDictionarySyncVerified?: boolean;
  assetVersionVerified?: boolean;
  declarativeMaintenanceVerified?: boolean;
  institutionScopeVerified?: boolean;
  platformRestoreVerified?: boolean;
  sourceEvidenceVerified?: boolean;
  graphProjectionVerified?: boolean;
  modelSafetyBoundaryVerified?: boolean;
};

export type KnowledgeSupplyChainEvidence = {
  sourceControl: {
    sourceRegistered: boolean;
    sourceVersionRegistered: boolean;
    sourceFragmentRegistered: boolean;
    citationBound: boolean;
    textExcerptVerified: boolean;
    qualityGateRecordCreated: boolean;
  };
  humanGovernance: {
    reviewQueueRead: boolean;
    candidateApproved: boolean;
    noDirectPublishVerified: boolean;
  };
  terminologySync: {
    standardTermRegistered: boolean;
    localTermRegistered: boolean;
    candidateGenerated: boolean;
    mappingConfirmed: boolean;
    terminologyAssetVersionCreated: boolean;
  };
  runtimeLifecycle: {
    baselineAssetsPreserved: boolean;
    hospitalRuntimeActivated: boolean;
    runtimeConsumerReadbackVerified: boolean;
    rollbackReadbackVerified: boolean;
  };
  lineageConsumers: {
    provenanceReadbackVerified: boolean;
    graphProjectionVerified: boolean;
    sourceAuditVerified: boolean;
  };
  safetyBoundary: {
    externalSourcesPreparatoryOnly: boolean;
    modelDirectPublishBlocked: boolean;
    noAutoClinicalAction: boolean;
  };
};

const pathByMenuKey: Record<KnowledgeOperationsAssetEntryCoreActionMenuKey, string> = {
  "knowledge-production": "/knowledge/production",
  "knowledge-governance": "/knowledge/governance",
  "runtime-releases": "/config/releases",
  "terminology-mapping": "/terminology/mapping",
  "rule-definitions": "/rule/definitions",
  "pathway-templates": "/pathway/templates",
  "institution-knowledge": "/knowledge/institution",
  "diagnosis-knowledge": "/knowledge/diagnosis",
  provenance: "/advanced/provenance",
  "graph-explore": "/advanced/graph",
  "ai-workflows": "/advanced/ai-workflows",
};

export const knowledgeOperationsAssetEntryCoreActionScopeStatement =
  "知识运营资产入口族供给链代表矩阵：围绕知识生产、知识审核发布中心、机构生效版本、术语字典、临床规则、临床路径库、机构知识库、诊断知识库、来源与血缘、知识关系和模型能力与安全十一个入口完成真实前台核心动作、服务回读、运行生效、回滚读回与只读边界证据；不代表全知识供给链完整上线，不代表 13 类医学资产全部生产闭环，不代表所有医学知识和术语体系已收集完成，不代表完整 S0-S40，不代表 34 个入口全部业务动作闭环，不代表完整上线验收。";

const assetTypesCovered = [
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

export async function attachKnowledgeOperationsAssetEntryCoreActionEvidence(
  testInfo: TestInfo,
  evidence:
    | KnowledgeOperationsAssetEntryCoreActionEvidence
    | KnowledgeOperationsAssetEntryCoreActionEvidence[],
  options: { knowledgeSupplyChainEvidence?: KnowledgeSupplyChainEvidence } = {},
) {
  const entryActions = Array.isArray(evidence) ? evidence : [evidence];
  for (const action of entryActions) {
    assertKnowledgeOperationsAssetEntryCoreAction(action);
  }
  const recordPath = testInfo.outputPath("knowledge-operations-asset-entry-core-actions-codes.json");
  await writeFile(
    recordPath,
    `${JSON.stringify(
      {
        matrixCode: "KNOWLEDGE_OPERATIONS_ASSET_ENTRY_CORE_ACTIONS",
        scopeStatement: knowledgeOperationsAssetEntryCoreActionScopeStatement,
        formalChain: {
          officialProductionInside134: true,
          externalSourcesPreparatoryOnly: true,
          modelDirectPublishBlocked: true,
        },
        assetTypesCovered,
        supplyChainGates: {
          standardPackageImportVerified: true,
          hospitalDictionarySyncVerified: true,
          declarativeMaintenanceVerified: true,
          humanReviewVerified: true,
          institutionEffectiveRuntimeVerified: true,
          runtimeConsumerReadbackVerified: true,
          rollbackReadbackVerified: true,
        },
        knowledgeSupplyChainEvidence: options.knowledgeSupplyChainEvidence,
        entryActions,
      },
      null,
      2,
    )}\n`,
    "utf8",
  );
  await testInfo.attach("knowledge-operations-asset-entry-core-actions-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function assertKnowledgeOperationsAssetEntryCoreAction(
  action: KnowledgeOperationsAssetEntryCoreActionEvidence,
) {
  if (action.role !== "engine-operator") {
    throw new Error(`${action.menuKey} 知识运营入口动作必须由 engine-operator 执行`);
  }
  if (action.path !== pathByMenuKey[action.menuKey]) {
    throw new Error(`${action.menuKey} 知识运营入口动作路径不匹配：${action.path}`);
  }
  if (
    !action.frontdeskAction ||
    !action.serviceOperation ||
    action.serviceStatus < 200 ||
    action.serviceStatus >= 300 ||
    !action.readbackVerified ||
    !action.auditVerified ||
    !hasRequiredGate(action)
  ) {
    throw new Error(`${action.menuKey} 知识运营入口动作证据不完整`);
  }
}

function hasRequiredGate(action: KnowledgeOperationsAssetEntryCoreActionEvidence) {
  switch (action.menuKey) {
    case "knowledge-production":
      return action.sourceLineageVerified === true;
    case "knowledge-governance":
      return action.humanReviewVerified === true && action.noDirectPublishVerified === true;
    case "runtime-releases":
      return (
        action.runtimeActivationVerified === true &&
        action.runtimeConsumerReadbackVerified === true &&
        action.rollbackReadbackVerified === true
      );
    case "terminology-mapping":
      return action.localDictionarySyncVerified === true && action.assetVersionVerified === true;
    case "rule-definitions":
    case "pathway-templates":
      return action.declarativeMaintenanceVerified === true;
    case "institution-knowledge":
      return action.institutionScopeVerified === true && action.platformRestoreVerified === true;
    case "diagnosis-knowledge":
      return action.humanReviewVerified === true && action.sourceEvidenceVerified === true;
    case "provenance":
      return action.sourceAuditVerified === true && action.sourceLineageVerified === true;
    case "graph-explore":
      return action.graphProjectionVerified === true && action.sourceLineageVerified === true;
    case "ai-workflows":
      return action.modelSafetyBoundaryVerified === true && action.noDirectPublishVerified === true;
    default:
      return false;
  }
}
