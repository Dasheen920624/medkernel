package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;
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

    Optional<IntegrationWebhookConfig> findByWebhookIdAndTenantId(String webhookId, String tenantId);
}
