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
 *   <li>{@link #findActiveByEffectiveScope(String, Long, String, String)}：定位完整适用域内当前权威版本</li>
 *   <li>{@link #findActiveByIdentity(String, Long)}：无适用域旧接口的默认权威版本兜底查询</li>
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
        ORDER BY activated_at DESC, id DESC
        OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
        """)
    Optional<KnowledgeAssetVersion> findActiveByIdentity(String tenantId, Long identityId);

    /**
     * 查询租户下所有 ACTIVE 的诊断知识版本（join 身份过滤 {@code domain=DIAGNOSIS}），供运行时鉴别诊断遍历命中。
     */
    @Query("""
        SELECT v.* FROM knowledge_asset_version v
        JOIN knowledge_identity i ON v.identity_id = i.id AND v.tenant_id = i.tenant_id
        WHERE v.tenant_id = :tenantId AND v.status = 'ACTIVE' AND i.domain = 'DIAGNOSIS'
        ORDER BY v.updated_at DESC, v.id DESC
        """)
    List<KnowledgeAssetVersion> findActiveDiagnosisVersions(String tenantId);

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
