package com.medkernel.engine.knowledge.production.shadow;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 生成期影子评测运行记录仓储（AIK-STD-06）。
 */
@Repository
public interface KnowledgeShadowRunRepository extends ListCrudRepository<KnowledgeShadowRun, Long> {

    List<KnowledgeShadowRun> findByTenantIdAndJobCodeOrderByIdAsc(String tenantId, String jobCode);
}
