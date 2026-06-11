package com.medkernel.compliance.identitybinding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 外部身份绑定仓储。
 */
@Repository
public interface IdentityBindingRepository extends ListCrudRepository<IdentityBinding, Long> {

    List<IdentityBinding> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    Optional<IdentityBinding> findByTenantIdAndProviderTypeAndExternalSubjectDigest(
        String tenantId, String providerType, String externalSubjectDigest);

    Optional<IdentityBinding> findByTenantIdAndUserIdAndProviderTypeAndStatus(
        String tenantId, String userId, String providerType, String status);

    Optional<IdentityBinding> findByTenantIdAndBindingId(String tenantId, String bindingId);

    List<IdentityBinding> findByTenantIdAndUserIdOrderByUpdatedAtDesc(
        String tenantId,
        String userId
    );
}
