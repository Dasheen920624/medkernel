package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 路径模板仓库。
 *
 * <p>保存专病路径模板主数据，支持按状态、病种、专病包和模板编码进行租户内分页检索。
 */
@Repository
public interface PathwayTemplateRepository extends ListCrudRepository<PathwayTemplate, Long> {

    /**
     * 按模板业务 ID 和租户查询路径模板。
     */
    Optional<PathwayTemplate> findByTemplateIdAndTenantId(String templateId, String tenantId);

    /**
     * 按租户、模板编码和版本查询模板，用于版本唯一性判断。
     */
    Optional<PathwayTemplate> findByTenantIdAndTemplateCodeAndTemplateVersion(
        String tenantId, String templateCode, Integer templateVersion);

    /**
     * 按可选状态、病种、专病包和模板编码分页查询路径模板。
     */
    @Query("""
        SELECT * FROM pathway_template
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:diseaseCode IS NULL OR disease_code = :diseaseCode)
          AND (:packageId IS NULL OR package_id = :packageId)
          AND (:templateCode IS NULL OR template_code = :templateCode)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<PathwayTemplate> pageByFilter(String tenantId, String status, String diseaseCode,
                                       String packageId, String templateCode, int offset, int limit);

    /**
     * 与分页查询同口径的完整列表，用于平台主源与租户覆盖层的有效模板合并。
     */
    @Query("""
        SELECT * FROM pathway_template
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:diseaseCode IS NULL OR disease_code = :diseaseCode)
          AND (:packageId IS NULL OR package_id = :packageId)
          AND (:templateCode IS NULL OR template_code = :templateCode)
        ORDER BY updated_at DESC, id DESC
        """)
    List<PathwayTemplate> listByFilter(String tenantId, String status, String diseaseCode, String packageId,
                                       String templateCode);

    /**
     * 统计可选过滤条件下的路径模板总数。
     */
    @Query("""
        SELECT COUNT(*) FROM pathway_template
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:diseaseCode IS NULL OR disease_code = :diseaseCode)
          AND (:packageId IS NULL OR package_id = :packageId)
          AND (:templateCode IS NULL OR template_code = :templateCode)
        """)
    long countByFilter(String tenantId, String status, String diseaseCode, String packageId, String templateCode);
}
