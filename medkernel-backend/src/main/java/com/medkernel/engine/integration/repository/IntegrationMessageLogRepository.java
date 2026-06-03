package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import com.medkernel.engine.integration.domain.IntegrationMessageLog;

/**
 * 外部第三方集成消息队列审计与死信补偿日志物理存储库接口。
 *
 * <p>提供基于 SQL 游标的服务器分页、计数以及特定消息定位能力，受强多租户隔离保护。
 */
@Repository
public interface IntegrationMessageLogRepository extends ListCrudRepository<IntegrationMessageLog, Long> {

    @Query("""
        SELECT * FROM integration_message_log
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    List<IntegrationMessageLog> pageByTenantIdOrderByCreatedAtDesc(String tenantId, int offset, int limit);

    @Query("SELECT COUNT(*) FROM integration_message_log WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    Optional<IntegrationMessageLog> findByMessageIdAndTenantId(String messageId, String tenantId);
}
