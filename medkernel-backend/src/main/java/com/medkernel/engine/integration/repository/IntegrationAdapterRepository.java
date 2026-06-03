package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import com.medkernel.engine.integration.domain.IntegrationAdapter;

@Repository
/**
 * 外部第三方对接适配器物理存储库接口。
 *
 * <p>基于 Spring Data JDBC ListCrudRepository，实现多租户隔离的数据访问与检索。
 */
public interface IntegrationAdapterRepository extends ListCrudRepository<IntegrationAdapter, Long> {

    List<IntegrationAdapter> findAllByTenantId(String tenantId);

    List<IntegrationAdapter> findAllByStatus(String status);

    Optional<IntegrationAdapter> findByAdapterIdAndTenantId(String adapterId, String tenantId);
}
