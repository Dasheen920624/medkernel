import { writeFile } from "node:fs/promises";

import type { TestInfo } from "@playwright/test";

export type ClinicalEntryCoreActionMenuKey =
  | "mpi"
  | "patient-pathways"
  | "cdss-fatigue"
  | "workflow-todos"
  | "clinical-followup";

export type ClinicalEntryCoreActionEvidence = {
  menuKey: ClinicalEntryCoreActionMenuKey;
  role: "clinical-user";
  path: string;
  frontdeskAction: string;
  serviceOperation: string;
  serviceStatus: number;
  readbackVerified: boolean;
  auditVerified: boolean;
};

const pathByMenuKey: Record<ClinicalEntryCoreActionMenuKey, string> = {
  mpi: "/mpi",
  "patient-pathways": "/pathway/patients",
  "cdss-fatigue": "/cdss/fatigue",
  "workflow-todos": "/workflow/todos",
  "clinical-followup": "/clinical/followup",
};

export const clinicalEntryCoreActionScopeStatement =
  "临床协同入口核心动作代表矩阵：围绕 MPI、患者路径、CDSS 提醒推荐、协同任务和随访协同五个入口完成真实前台核心动作、服务回读与审计证据；不代表完整临床流程，不代表 34 个入口全部业务动作闭环，不代表完整 S0-S40，不代表完整上线验收。";

export async function attachClinicalEntryCoreActionEvidence(
  testInfo: TestInfo,
  evidence: ClinicalEntryCoreActionEvidence | ClinicalEntryCoreActionEvidence[],
) {
  const entryActions = Array.isArray(evidence) ? evidence : [evidence];
  for (const action of entryActions) {
    assertClinicalEntryCoreAction(action);
  }
  const recordPath = testInfo.outputPath("clinical-entry-core-actions-codes.json");
  await writeFile(
    recordPath,
    `${JSON.stringify(
      {
        matrixCode: "CLINICAL_COLLABORATION_ENTRY_CORE_ACTIONS",
        scopeStatement: clinicalEntryCoreActionScopeStatement,
        entryActions,
      },
      null,
      2,
    )}\n`,
    "utf8",
  );
  await testInfo.attach("clinical-entry-core-actions-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function assertClinicalEntryCoreAction(action: ClinicalEntryCoreActionEvidence) {
  if (action.role !== "clinical-user") {
    throw new Error(`${action.menuKey} 临床协同入口动作必须由 clinical-user 执行`);
  }
  if (action.path !== pathByMenuKey[action.menuKey]) {
    throw new Error(`${action.menuKey} 临床协同入口动作路径不匹配：${action.path}`);
  }
  if (
    !action.frontdeskAction ||
    !action.serviceOperation ||
    action.serviceStatus < 200 ||
    action.serviceStatus >= 300 ||
    !action.readbackVerified ||
    !action.auditVerified
  ) {
    throw new Error(`${action.menuKey} 临床协同入口动作证据不完整`);
  }
}
