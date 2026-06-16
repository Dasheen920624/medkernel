package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 平台知识本地派生血缘仓库。
 */
@Repository
public interface KnowledgeCustomizationRepository
        extends ListCrudRepository<KnowledgeCustomization, String> {

    Optional<KnowledgeCustomization>
        findByTenantIdAndPlatformIdentityIdAndTargetOrgUnitIdAndApplicableScope(
            String tenantId,
            Long platformIdentityId,
            String targetOrgUnitId,
            String applicableScope
        );

    Optional<KnowledgeCustomization> findByTenantIdAndCustomizationId(
        String tenantId,
        String customizationId
    );

    List<KnowledgeCustomization> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    @Query("""
        SELECT * FROM mk_knowledge_customization
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, customization_id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeCustomization> pageByTenantId(String tenantId, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM mk_knowledge_customization
        WHERE tenant_id = :tenantId
        """)
    long countByTenantId(String tenantId);

    List<KnowledgeCustomization> findByTenantIdAndLocalIdentityId(
        String tenantId,
        Long localIdentityId
    );
}
