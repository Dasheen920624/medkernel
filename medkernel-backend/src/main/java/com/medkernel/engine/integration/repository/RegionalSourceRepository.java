package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.integration.domain.RegionalSource;

/**
 * 区域协同来源仓储。
 */
@Repository
public interface RegionalSourceRepository extends ListCrudRepository<RegionalSource, Long> {

    List<RegionalSource> findAllByTenantId(String tenantId);

    @Query("""
        SELECT COUNT(*) FROM mk_integration_regional_source
        WHERE tenant_id = :tenantId
        """)
    long countByTenantId(String tenantId);

    @Query("""
        SELECT * FROM mk_integration_regional_source
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RegionalSource> pageByTenantId(String tenantId, int offset, int limit);

    Optional<RegionalSource> findBySourceIdAndTenantId(String sourceId, String tenantId);
}
