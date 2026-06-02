package com.medkernel.engine.terminology;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 术语映射包持久化仓库；按 tenant_id 隔离。
 */
@Repository
public interface TermMappingPackageRepository extends ListCrudRepository<TermMappingPackage, Long> {

    Optional<TermMappingPackage> findByTenantIdAndId(String tenantId, Long id);

    Optional<TermMappingPackage> findByTenantIdAndPackageCodeAndPackageVersionAndScopeLevelAndScopeCode(
        String tenantId,
        String packageCode,
        String packageVersion,
        String scopeLevel,
        String scopeCode
    );

    /**
     * 按租户 + 可选过滤条件（包编码 / 状态 / 作用域层级 / 作用域编码）统计映射包数量。
     */
    @Query("""
        SELECT COUNT(*) FROM term_mapping_package
        WHERE tenant_id = :tenantId
          AND (:packageCode IS NULL OR package_code = :packageCode)
          AND (:status IS NULL OR status = :status)
          AND (:scopeLevel IS NULL OR scope_level = :scopeLevel)
          AND (:scopeCode IS NULL OR scope_code = :scopeCode)
        """)
    long countByFilter(String tenantId, String packageCode, String status, String scopeLevel, String scopeCode);

    /**
     * 按租户 + 可选过滤条件分页查询映射包（更新时间倒序），用于发布管理后台列表。
     */
    @Query("""
        SELECT * FROM term_mapping_package
        WHERE tenant_id = :tenantId
          AND (:packageCode IS NULL OR package_code = :packageCode)
          AND (:status IS NULL OR status = :status)
          AND (:scopeLevel IS NULL OR scope_level = :scopeLevel)
          AND (:scopeCode IS NULL OR scope_code = :scopeCode)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<TermMappingPackage> pageByFilter(String tenantId, String packageCode, String status, String scopeLevel,
                                          String scopeCode, int offset, int limit);

    /**
     * 查询同 (tenant + packageCode + scope) 下处于 GRAY / PUBLISHED 的活动映射包。
     *
     * <p>全量发布时用于把同作用域的旧 PUBLISHED 包置为 SUPERSEDED。
     */
    @Query("""
        SELECT * FROM term_mapping_package
        WHERE tenant_id = :tenantId
          AND package_code = :packageCode
          AND scope_level = :scopeLevel
          AND scope_code = :scopeCode
          AND status IN ('GRAY','PUBLISHED')
        ORDER BY published_at DESC, id DESC
        """)
    List<TermMappingPackage> findActiveByTenantIdAndPackageCodeAndScope(String tenantId, String packageCode,
                                                                        String scopeLevel, String scopeCode);
}
