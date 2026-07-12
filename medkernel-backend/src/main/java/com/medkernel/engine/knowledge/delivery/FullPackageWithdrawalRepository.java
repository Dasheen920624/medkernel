package com.medkernel.engine.knowledge.delivery;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 完整包安全撤回事实仓储。 */
@Repository
public interface FullPackageWithdrawalRepository
        extends ListCrudRepository<FullPackageWithdrawal, Long> {

    Optional<FullPackageWithdrawal>
        findByTenantIdAndAuthorityIdAndDeliveryIdAndAssetTypeAndAssetIdentityAndWithdrawnVersionId(
            String tenantId,
            String authorityId,
            String deliveryId,
            com.medkernel.engine.versioning.VersionedAssetType assetType,
            String assetIdentity,
            String withdrawnVersionId
        );
}
