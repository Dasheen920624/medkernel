package com.medkernel.engine.knowledge.acquisition;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 公域资料来源允许清单仓储。业务查询强制带 tenantId；系统调度跨服务机构扫描后按来源服务机构提交 SYS-05 任务。
 */
@Repository
public interface KnowledgeAcquisitionSourceRepository extends ListCrudRepository<KnowledgeAcquisitionSource, Long> {

    Optional<KnowledgeAcquisitionSource> findByTenantIdAndSourceCode(String tenantId, String sourceCode);

    @Query("SELECT COUNT(*) FROM mk_knowledge_acquisition_source WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_knowledge_acquisition_source
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAcquisitionSource> pageByTenantId(String tenantId, int offset, int limit);

    @Query("""
        SELECT * FROM mk_knowledge_acquisition_source
        WHERE schedule_enabled_flag = 'Y'
          AND enabled_flag = 'Y'
          AND license_policy = 'PERMITTED'
          AND robots_policy = 'ALLOW_FETCH'
          AND schedule_interval_minutes > 0
          AND default_format IS NOT NULL
          AND (next_check_at IS NULL OR next_check_at <= :now)
        ORDER BY next_check_at ASC, updated_at ASC, id ASC
        OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAcquisitionSource> findDueForSchedule(Instant now, int limit);

    @Modifying
    @Query("""
        UPDATE mk_knowledge_acquisition_source
        SET last_check_at = :now,
            next_check_at = :nextCheckAt,
            updated_at = :now,
            updated_by = :actor,
            lock_version = lock_version + 1
        WHERE tenant_id = :tenantId
          AND id = :id
          AND lock_version = :expectedVersion
          AND schedule_enabled_flag = 'Y'
          AND enabled_flag = 'Y'
          AND license_policy = 'PERMITTED'
          AND robots_policy = 'ALLOW_FETCH'
          AND schedule_interval_minutes > 0
          AND default_format IS NOT NULL
          AND base_url IS NOT NULL
          AND base_url <> ''
          AND (next_check_at IS NULL OR next_check_at <= :now)
        """)
    int markScheduleSubmitted(String tenantId, Long id, Long expectedVersion,
                              Instant now, Instant nextCheckAt, String actor);
}
