package com.medkernel.engine.authoring;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 条件片段持久化仓库。
 */
@Repository
public interface ConditionFragmentRepository extends ListCrudRepository<ConditionFragment, Long> {

    /**
     * 按片段业务 ID 与租户查询。
     */
    Optional<ConditionFragment> findByFragmentIdAndTenantId(String fragmentId, String tenantId);

    /**
     * 按编码与版本查询稳定片段。
     */
    Optional<ConditionFragment> findByTenantIdAndFragmentCodeAndVersionNo(
        String tenantId, String fragmentCode, Integer versionNo);

    /**
     * 查询同编码最新版本，用于编辑期循环分析的兜底展示。
     */
    @Query("""
        SELECT * FROM mk_engine_condition_fragment
        WHERE tenant_id = :tenantId AND fragment_code = :fragmentCode
        ORDER BY version_no DESC, id DESC
        OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY
        """)
    Optional<ConditionFragment> findLatestByTenantIdAndFragmentCode(String tenantId, String fragmentCode);

    /**
     * 按可选状态、包版本和关键字分页查询条件片段。
     */
    @Query("""
        SELECT * FROM mk_engine_condition_fragment
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:packageVersion IS NULL OR package_version = :packageVersion)
          AND (
            :keyword IS NULL
            OR LOWER(fragment_code) LIKE '%' || LOWER(:keyword) || '%'
            OR LOWER(name) LIKE '%' || LOWER(:keyword) || '%'
            OR LOWER(COALESCE(category, '')) LIKE '%' || LOWER(:keyword) || '%'
          )
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ConditionFragment> pageByFilter(
        String tenantId,
        String status,
        String packageVersion,
        String keyword,
        int offset,
        int limit);

    /**
     * 与分页查询同口径的总数。
     */
    @Query("""
        SELECT COUNT(*) FROM mk_engine_condition_fragment
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:packageVersion IS NULL OR package_version = :packageVersion)
          AND (
            :keyword IS NULL
            OR LOWER(fragment_code) LIKE '%' || LOWER(:keyword) || '%'
            OR LOWER(name) LIKE '%' || LOWER(:keyword) || '%'
            OR LOWER(COALESCE(category, '')) LIKE '%' || LOWER(:keyword) || '%'
          )
        """)
    long countByFilter(String tenantId, String status, String packageVersion, String keyword);
}
