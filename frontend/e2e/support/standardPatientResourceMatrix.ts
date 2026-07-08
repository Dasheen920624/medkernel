export const standardPatientResourceConsumerMatrixScope =
  "13 类标准患者资源真实接入与消费者代表矩阵：跨真实前台演练聚合 Patient、AllergyIntolerance、Encounter、Condition、NursingAssessment、Observation、DiagnosticReport、Medication、Procedure、Document、CarePlan、FollowUp 与 Claim 的标准资源回读、运行消费者、审计和数据质量证据；不代表每类字段目录全量落地，不代表完整 S0-S40，不代表完整上线验收。";

export type StandardPatientResourceMatrixRow = {
  resourceType: string;
  resourcePath: string;
  sourceSystem: string;
  sourceId?: string;
  sourceIdPath?: string;
  patientVerified: true;
  encounterVerified: boolean;
  snapshotReadbackVerified: true;
  consumer: string;
  consumerEvidencePaths: string[];
  consumerVerified: true;
  auditEvidencePaths: string[];
  auditVerified: true;
  dataQualityVerified: true;
  evaluationRunVerified?: true;
  qualityRectificationVerified?: true;
};

export function standardPatientResourceConsumerMatrix(
  resources: StandardPatientResourceMatrixRow[],
) {
  return {
    matrixCode: "THIRTEEN_STANDARD_RESOURCES_REPRESENTATIVE",
    scopeStatement: standardPatientResourceConsumerMatrixScope,
    resources,
  };
}
