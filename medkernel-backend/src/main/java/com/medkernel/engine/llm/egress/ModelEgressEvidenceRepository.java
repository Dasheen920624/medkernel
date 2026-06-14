package com.medkernel.engine.llm.egress;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 出域证据数据访问存储库（LLM-03）。
 */
@Repository
public interface ModelEgressEvidenceRepository extends CrudRepository<ModelEgressEvidence, Long> {
}
