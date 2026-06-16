package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识资产异步导出任务（Export Job）仓储接口。
 *
 * <p>用于管理大规模医学知识库导出作业的后台状态、凭证映射与导出文件属性，
 * 支撑 GA-ENG-API-03 知识资产引擎中，异步 CSV/JSON 医学逻辑包的批量导出任务。
 */
@Repository
public interface KnowledgeExportJobRepository extends ListCrudRepository<KnowledgeExportJob, Long> {

    Optional<KnowledgeExportJob> findByTenantIdAndJobCode(String tenantId, String jobCode);

    List<KnowledgeExportJob> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, ExportStatus status);

    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM knowledge_export_job
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeExportJob> pageByTenantId(String tenantId, int offset, int limit);
}
