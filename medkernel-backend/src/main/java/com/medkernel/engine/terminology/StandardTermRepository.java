package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 标准术语字典持久化仓库。
 *
 * <p>所有查询按 tenant_id 隔离；按 standard_system + term_code 形成业务唯一性。
 */
@Repository
public interface StandardTermRepository extends ListCrudRepository<StandardTerm, Long> {

    Optional<StandardTerm> findByTenantIdAndId(String tenantId, Long id);

    /**
     * 按租户 + 可选过滤条件（术语体系 / 分类 / 状态 / 关键词）统计标准术语数量。
     */
    @Query("""
        SELECT COUNT(*) FROM standard_term
        WHERE tenant_id = :tenantId
          AND (:standardSystem IS NULL OR standard_system = :standardSystem)
          AND (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(display_name) LIKE :keyword OR LOWER(term_code) LIKE :keyword)
        """)
    long countByFilter(String tenantId, String standardSystem, String category, String status, String keyword);

    /**
     * 按租户 + 可选过滤条件分页查询标准术语（更新时间倒序），用于映射候选挑选与管理后台列表。
     */
    @Query("""
        SELECT * FROM standard_term
        WHERE tenant_id = :tenantId
          AND (:standardSystem IS NULL OR standard_system = :standardSystem)
          AND (:category IS NULL OR category = :category)
          AND (:status IS NULL OR status = :status)
          AND (:keyword IS NULL OR LOWER(display_name) LIKE :keyword OR LOWER(term_code) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<StandardTerm> pageByFilter(String tenantId, String standardSystem, String category, String status,
                                    String keyword, int offset, int limit);

    Optional<StandardTerm> findByTenantIdAndStandardSystemAndTermCodeAndStatus(
        String tenantId, String standardSystem, String termCode, StandardTermStatus status);

    List<StandardTerm> findByTenantIdAndStatus(String tenantId, StandardTermStatus status);
}
