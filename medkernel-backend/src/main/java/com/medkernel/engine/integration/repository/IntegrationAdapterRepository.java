package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.medkernel.engine.integration.domain.IntegrationAdapter;

/**
 * 外部第三方对接适配器物理存储库接口。
 *
 * <p>基于 Spring Data JDBC ListCrudRepository，实现多租户隔离的数据访问与检索。
 */
@Repository
public interface IntegrationAdapterRepository extends ListCrudRepository<IntegrationAdapter, Long> {

    List<IntegrationAdapter> findAllByTenantId(String tenantId);

    List<IntegrationAdapter> findAllByStatus(String status);

    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM integration_adapter
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<IntegrationAdapter> pageByTenantId(
        @Param("tenantId") String tenantId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );

    long countByTenantIdAndStatus(String tenantId, String status);

    Optional<IntegrationAdapter> findByAdapterIdAndTenantId(String adapterId, String tenantId);
}
