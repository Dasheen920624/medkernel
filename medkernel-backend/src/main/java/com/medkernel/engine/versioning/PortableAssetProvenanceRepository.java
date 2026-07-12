package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 可移植资产来源事实仓储。 */
@Repository
public interface PortableAssetProvenanceRepository
        extends ListCrudRepository<PortableAssetProvenance, Long> {

    Optional<PortableAssetProvenance> findByTenantIdAndVersionId(
        String tenantId,
        String versionId
    );

    Optional<PortableAssetProvenance> findByProvenanceId(String provenanceId);
}
