package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import com.medkernel.engine.integration.domain.IntegrationWebhookConfig;

/**
 * 外部 Webhook 订阅安全配置物理存储库接口。
 *
 * <p>基于 Spring Data JDBC ListCrudRepository，实现多租户隔离的数据访问与检索。
 */
@Repository
public interface IntegrationWebhookConfigRepository extends ListCrudRepository<IntegrationWebhookConfig, Long> {

    List<IntegrationWebhookConfig> findAllByTenantId(String tenantId);

    @Query("""
        SELECT COUNT(*) FROM integration_webhook_config
        WHERE tenant_id = :tenantId
        """)
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM integration_webhook_config
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<IntegrationWebhookConfig> pageByTenantId(String tenantId, int offset, int limit);

    Optional<IntegrationWebhookConfig> findByWebhookIdAndTenantId(String webhookId, String tenantId);
}
