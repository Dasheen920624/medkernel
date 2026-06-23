package com.medkernel.engine.knowledge;

import java.time.Instant;
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
 *   <li>{@link #findActiveByEffectiveScope(String, Long, String, String)}：定位完整适用域内当前权威版本</li>
 *   <li>{@link #pageByTenantIdAndIdentityId(String, Long, int, int)}：版本历史分页</li>
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

    @Query("""
        SELECT COUNT(*) FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
          AND LOWER(version_no) = LOWER(:versionNo)
        """)
    long countByTenantIdAndIdentityIdAndVersionNoIgnoreCase(
        String tenantId,
        Long identityId,
        String versionNo
    );

    default boolean existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase(
        String tenantId,
        Long identityId,
        String versionNo
    ) {
        return countByTenantIdAndIdentityIdAndVersionNoIgnoreCase(tenantId, identityId, versionNo) > 0L;
    }

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
          AND content_hash = :contentHash
        ORDER BY created_at DESC, id DESC
        OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
        """)
    Optional<KnowledgeAssetVersion> findByTenantIdAndIdentityIdAndContentHash(
        String tenantId,
        Long identityId,
        String contentHash
    );

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
          AND status = :status
        ORDER BY created_at DESC, id DESC
        OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
        """)
    Optional<KnowledgeAssetVersion> findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
        String tenantId,
        Long identityId,
        KnowledgeVersionStatus status
    );

    List<KnowledgeAssetVersion> findByTenantIdAndIdentityIdOrderByCreatedAtDesc(String tenantId, Long identityId);

    @Query("""
        SELECT COUNT(*) FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
          AND status = 'PENDING_REPLACEMENT_REVIEW'
        """)
    long countPendingReplacementCandidatesByTenantIdAndIdentityId(String tenantId, Long identityId);

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
          AND status = 'PENDING_REPLACEMENT_REVIEW'
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAssetVersion> pagePendingReplacementCandidatesByTenantIdAndIdentityId(
        String tenantId,
        Long identityId,
        int offset,
        int limit
    );

    long countByTenantIdAndIdentityId(String tenantId, Long identityId);

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAssetVersion> pageByTenantIdAndIdentityId(
        String tenantId,
        Long identityId,
        int offset,
        int limit
    );

    List<KnowledgeAssetVersion> findByTenantIdAndStatusOrderByUpdatedAtDescIdDesc(
        String tenantId,
        KnowledgeVersionStatus status
    );

    @Query("""
        SELECT COUNT(*) FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND status = 'ACTIVE'
          AND next_review_at <= :threshold
        """)
    long countReviewDueByTenantId(String tenantId, Instant threshold);

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND status = 'ACTIVE'
          AND next_review_at <= :threshold
        ORDER BY next_review_at ASC, id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<KnowledgeAssetVersion> pageReviewDueByTenantId(
        String tenantId,
        Instant threshold,
        int offset,
        int limit
    );

    @Query("""
        SELECT * FROM knowledge_asset_version
        WHERE tenant_id = :tenantId
          AND identity_id = :identityId
          AND organization_scope = :organizationScope
          AND applicable_scope = :applicableScope
          AND status = 'ACTIVE'
        """)
    Optional<KnowledgeAssetVersion> findActiveByEffectiveScope(
        String tenantId,
        Long identityId,
        String organizationScope,
        String applicableScope
    );

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
