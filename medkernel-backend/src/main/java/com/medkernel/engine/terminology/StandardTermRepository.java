package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准术语字典持久化仓库。
 *
 * <p>写入仍由 tenant_id 归属隔离；租户读路径通过平台标准版本 + 租户覆盖联合查询，
 * 保证院内映射可以引用平台标准词。
 */
@Repository
public interface StandardTermRepository extends ListCrudRepository<StandardTerm, Long> {

    @Query("""
        SELECT * FROM standard_term
        WHERE tenant_id IN (:tenantIds)
          AND id = :id
        ORDER BY CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END, updated_at DESC, id DESC
        """)
    List<StandardTerm> findByTenantIdsAndId(
        List<String> tenantIds,
        String tenantId,
        Long id
    );

    default Optional<StandardTerm> findFirstByTenantIdsAndId(
            List<String> tenantIds,
            String tenantId,
            Long id) {
        return findByTenantIdsAndId(tenantIds, tenantId, id).stream().findFirst();
    }

    /**
     * 平台标准 + 租户覆盖联合统计。tenantIds 应按平台优先、租户随后传入。
     */
    @Query("""
        SELECT COUNT(*) FROM standard_term
        WHERE tenant_id IN (:tenantIds)
          AND (:standardSystem IS NULL OR standard_system = :standardSystem)
          AND (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(display_name) LIKE :keyword OR LOWER(term_code) LIKE :keyword)
        """)
    long countByTenantIdsFilter(
        List<String> tenantIds,
        String standardSystem,
        String category,
        String status,
        String keyword
    );

    /**
     * 平台标准 + 租户覆盖联合分页。先展示租户覆盖，再展示平台标准版本，保证本地覆盖更容易被人工看见。
     */
    @Query("""
        SELECT * FROM standard_term
        WHERE tenant_id IN (:tenantIds)
          AND (:standardSystem IS NULL OR standard_system = :standardSystem)
          AND (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(display_name) LIKE :keyword OR LOWER(term_code) LIKE :keyword)
        ORDER BY CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END, updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<StandardTerm> pageByTenantIdsFilter(
        List<String> tenantIds,
        String tenantId,
        String standardSystem,
        String category,
        String status,
        String keyword,
        int offset,
        int limit
    );

    Optional<StandardTerm> findByTenantIdAndStandardSystemAndTermCodeAndStatus(
        String tenantId, String standardSystem, String termCode, StandardTermStatus status);

    Optional<StandardTerm> findByTenantIdAndStandardSystemAndTermCodeAndVersionNo(
        String tenantId, String standardSystem, String termCode, String versionNo);

    @Query("""
        SELECT * FROM standard_term
        WHERE tenant_id IN (:tenantIds)
          AND standard_system = :standardSystem
          AND term_code = :termCode
          AND status = :status
        ORDER BY CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END, updated_at DESC, id DESC
        """)
    List<StandardTerm> findByTenantIdsAndStandardSystemAndTermCodeAndStatus(
        List<String> tenantIds,
        String tenantId,
        String standardSystem,
        String termCode,
        StandardTermStatus status
    );

    default Optional<StandardTerm> findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
            List<String> tenantIds,
            String tenantId,
            String standardSystem,
            String termCode,
            StandardTermStatus status) {
        return findByTenantIdsAndStandardSystemAndTermCodeAndStatus(
            tenantIds, tenantId, standardSystem, termCode, status
        ).stream().findFirst();
    }

    default Optional<StandardTerm> findFirstActiveByTenantIdsAndStandardSystemAndTermCode(
            List<String> tenantIds,
            String tenantId,
            String standardSystem,
            String termCode) {
        return findFirstByTenantIdsAndStandardSystemAndTermCodeAndStatus(
            tenantIds, tenantId, standardSystem, termCode, StandardTermStatus.ACTIVE);
    }

    /**
     * 查 ACTIVE 标准术语（按字典 standard_system + term_code）。把包私有的 {@link StandardTermStatus}
     * 封装在本包内，供跨包只读复用（如院内直接使用标准字典编码时的"已是标准码透传"判定）而无需引用枚举。
     */
    default Optional<StandardTerm> findActiveByTenantIdAndStandardSystemAndTermCode(
            String tenantId, String standardSystem, String termCode) {
        return findByTenantIdAndStandardSystemAndTermCodeAndStatus(
            tenantId, standardSystem, termCode, StandardTermStatus.ACTIVE);
    }

    List<StandardTerm> findByTenantIdAndStatus(String tenantId, StandardTermStatus status);

    @Query("""
        SELECT * FROM standard_term
        WHERE tenant_id IN (:tenantIds)
          AND status = :status
        ORDER BY CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END, updated_at DESC, id DESC
        """)
    List<StandardTerm> findByTenantIdsAndStatus(
        List<String> tenantIds,
        String tenantId,
        StandardTermStatus status
    );

    /**
     * 分页扫描平台标准 + 租户覆盖的有效标准术语，供候选生成构建本地索引。
     */
    @Query("""
        SELECT * FROM standard_term
        WHERE tenant_id IN (:tenantIds)
          AND status = :status
        ORDER BY CASE WHEN tenant_id = :tenantId THEN 0 ELSE 1 END, updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<StandardTerm> pageByTenantIdsAndStatus(
        List<String> tenantIds,
        String tenantId,
        StandardTermStatus status,
        int offset,
        int limit
    );
}
