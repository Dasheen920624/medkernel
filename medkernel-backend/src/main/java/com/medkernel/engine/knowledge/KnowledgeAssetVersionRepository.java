package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识资产版本 Repository。
 *
 * <p>关键查询：
 * <ul>
 *   <li>{@link #findActiveByIdentity(String, Long)}：定位当前权威版本（同 identity 同时刻 ≤ 1）</li>
 *   <li>{@link #findByTenantIdAndIdentityIdOrderByCreatedAtDesc(String, Long)}：版本列表</li>
 * </ul>
 */
@Repository
public interface KnowledgeAssetVersionRepository extends ListCrudRepository<KnowledgeAssetVersion, Long> {

    Optional<KnowledgeAssetVersion> findByTenantIdAndId(String tenantId, Long id);

    Optional<KnowledgeAssetVersion> findByTenantIdAndIdentityIdAndVersionNo(
        String tenantId,
        Long identityId,
        String versionNo
    );

    List<KnowledgeAssetVersion> findByTenantIdAndIdentityIdOrderByCreatedAtDesc(String tenantId, Long identityId);

    List<KnowledgeAssetVersion> findByTenantIdAndStatusOrderByUpdatedAtDescIdDesc(
        String tenantId,
        KnowledgeVersionStatus status
    );

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId AND identity_id = :identityId AND status = 'ACTIVE'
        """)
    Optional<KnowledgeAssetVersion> findActiveByIdentity(String tenantId, Long identityId);

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId AND identity_id = :identityId
        ORDER BY created_at DESC, id DESC
        """)
    List<KnowledgeAssetVersion> listByIdentity(String tenantId, Long identityId);

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAssetVersion> pageByTenantId(String tenantId, int offset, int limit);
}
