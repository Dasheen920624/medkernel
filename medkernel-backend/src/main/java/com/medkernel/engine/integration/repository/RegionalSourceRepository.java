package com.medkernel.engine.integration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.integration.domain.RegionalSource;

/**
 * 区域协同来源仓储。
 */
@Repository
public interface RegionalSourceRepository extends ListCrudRepository<RegionalSource, Long> {

    List<RegionalSource> findAllByTenantId(String tenantId);

    Optional<RegionalSource> findBySourceIdAndTenantId(String sourceId, String tenantId);
}
