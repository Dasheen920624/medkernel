package com.medkernel.engine.llm.eval;

import java.util.List;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 医学回归逐用例证据存储库，所有读取均同时限定租户和运行。
 */
@Repository
public interface ModelEvalCaseEvidenceRepository extends CrudRepository<ModelEvalCaseEvidence, Long> {

    List<ModelEvalCaseEvidence> findByTenantIdAndRunIdOrderByIdAsc(String tenantId, Long runId);

    long countByTenantIdAndRunId(String tenantId, Long runId);
}
