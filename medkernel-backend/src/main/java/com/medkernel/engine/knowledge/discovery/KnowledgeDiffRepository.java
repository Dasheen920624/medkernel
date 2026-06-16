package com.medkernel.engine.knowledge.discovery;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识差异台账仓储。
 */
@Repository
public interface KnowledgeDiffRepository extends ListCrudRepository<KnowledgeDiff, Long> {

    List<KnowledgeDiff> findByTenantIdAndRunCodeOrderByDetectedAtAscIdAsc(String tenantId, String runCode);
}
