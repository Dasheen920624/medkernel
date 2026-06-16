package com.medkernel.compliance.identitybinding;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 外部身份绑定仓储。
 */
@Repository
public interface IdentityBindingRepository extends ListCrudRepository<IdentityBinding, Long> {

    List<IdentityBinding> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    @Query("""
        SELECT * FROM mk_compliance_identity_binding
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<IdentityBinding> pageByTenantId(String tenantId, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM mk_compliance_identity_binding
        WHERE tenant_id = :tenantId
        """)
    long countByTenantId(String tenantId);

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
