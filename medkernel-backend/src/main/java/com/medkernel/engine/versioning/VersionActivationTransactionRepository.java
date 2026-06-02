package com.medkernel.engine.versioning;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 通用版本激活事务仓库。
 */
@Repository
public interface VersionActivationTransactionRepository extends ListCrudRepository<VersionActivationTransaction, Long> {
    Optional<VersionActivationTransaction> findByTenantIdAndAssetTypeAndAssetIdentityAndToVersionIdAndActionAndActiveScopeKey(
        String tenantId,
        VersionedAssetType assetType,
        String assetIdentity,
        String toVersionId,
        VersionActivationAction action,
        String activeScopeKey
    );
}
