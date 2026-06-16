package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 路径模板仓库。
 *
 * <p>保存专病路径模板主数据，支持按状态、病种、路径知识包和模板编码进行租户内分页检索。
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
     * 按真实来源引用查询模板，用于知识安全撤回后的路径影响扫描。
     */
    List<PathwayTemplate> findByTenantIdAndSourceRef(String tenantId, String sourceRef);

    /**
     * 按可选状态、病种、路径知识包和模板编码分页查询路径模板。
     */
    @Query("""
        SELECT * FROM pathway_template
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:diseaseCode IS NULL OR disease_code = :diseaseCode)
          AND (:packageId IS NULL OR package_id = :packageId)
          AND (:templateCode IS NULL OR template_code = :templateCode)
          AND (:keyword IS NULL OR LOWER(template_code) LIKE :keyword OR LOWER(name) LIKE :keyword OR LOWER(disease_code) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<PathwayTemplate> pageByFilter(String tenantId, String status, String diseaseCode,
                                       String packageId, String templateCode, String keyword,
                                       int offset, int limit);

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
          AND (:keyword IS NULL OR LOWER(template_code) LIKE :keyword OR LOWER(name) LIKE :keyword OR LOWER(disease_code) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        """)
    List<PathwayTemplate> listByFilter(String tenantId, String status, String diseaseCode, String packageId,
                                       String templateCode, String keyword);

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
          AND (:keyword IS NULL OR LOWER(template_code) LIKE :keyword OR LOWER(name) LIKE :keyword OR LOWER(disease_code) LIKE :keyword)
        """)
    long countByFilter(String tenantId, String status, String diseaseCode, String packageId,
                       String templateCode, String keyword);

    /**
     * 统计租户本地模板与平台已发布模板合并后的有效总数。
     *
     * <p>平台模板保持平台主源语义，不按租户包过滤；本地同编码同版本模板优先覆盖平台模板。
     */
    @Query("""
        SELECT COUNT(*) FROM (
            SELECT local.template_code, local.template_version
            FROM pathway_template local
            WHERE local.tenant_id = :tenantId
              AND (:tenantStatus IS NULL OR local.status = :tenantStatus)
              AND (:diseaseCode IS NULL OR local.disease_code = :diseaseCode)
              AND (:packageId IS NULL OR local.package_id = :packageId)
              AND (:templateCode IS NULL OR local.template_code = :templateCode)
              AND (:keyword IS NULL OR LOWER(local.template_code) LIKE :keyword OR LOWER(local.name) LIKE :keyword OR LOWER(local.disease_code) LIKE :keyword)
            UNION ALL
            SELECT platform.template_code, platform.template_version
            FROM pathway_template platform
            WHERE platform.tenant_id = :platformTenantId
              AND platform.status = :platformStatus
              AND (:diseaseCode IS NULL OR platform.disease_code = :diseaseCode)
              AND (:templateCode IS NULL OR platform.template_code = :templateCode)
              AND (:keyword IS NULL OR LOWER(platform.template_code) LIKE :keyword OR LOWER(platform.name) LIKE :keyword OR LOWER(platform.disease_code) LIKE :keyword)
              AND NOT EXISTS (
                    SELECT 1
                    FROM pathway_template local_shadow
                    WHERE local_shadow.tenant_id = :tenantId
                      AND local_shadow.template_code = platform.template_code
                      AND local_shadow.template_version = platform.template_version
                      AND (:tenantStatus IS NULL OR local_shadow.status = :tenantStatus)
                      AND (:diseaseCode IS NULL OR local_shadow.disease_code = :diseaseCode)
                      AND (:packageId IS NULL OR local_shadow.package_id = :packageId)
                      AND (:templateCode IS NULL OR local_shadow.template_code = :templateCode)
                      AND (:keyword IS NULL OR LOWER(local_shadow.template_code) LIKE :keyword OR LOWER(local_shadow.name) LIKE :keyword OR LOWER(local_shadow.disease_code) LIKE :keyword)
                  )
        ) effective_rows
        """)
    long countEffectiveByFilter(String tenantId, String platformTenantId,
                                String tenantStatus, String platformStatus,
                                String diseaseCode, String packageId,
                                String templateCode, String keyword);

    /**
     * 分页读取租户本地模板与平台已发布模板合并后的有效模板。
     */
    @Query("""
        SELECT * FROM (
            SELECT local.*
            FROM pathway_template local
            WHERE local.tenant_id = :tenantId
              AND (:tenantStatus IS NULL OR local.status = :tenantStatus)
              AND (:diseaseCode IS NULL OR local.disease_code = :diseaseCode)
              AND (:packageId IS NULL OR local.package_id = :packageId)
              AND (:templateCode IS NULL OR local.template_code = :templateCode)
              AND (:keyword IS NULL OR LOWER(local.template_code) LIKE :keyword OR LOWER(local.name) LIKE :keyword OR LOWER(local.disease_code) LIKE :keyword)
            UNION ALL
            SELECT platform.*
            FROM pathway_template platform
            WHERE platform.tenant_id = :platformTenantId
              AND platform.status = :platformStatus
              AND (:diseaseCode IS NULL OR platform.disease_code = :diseaseCode)
              AND (:templateCode IS NULL OR platform.template_code = :templateCode)
              AND (:keyword IS NULL OR LOWER(platform.template_code) LIKE :keyword OR LOWER(platform.name) LIKE :keyword OR LOWER(platform.disease_code) LIKE :keyword)
              AND NOT EXISTS (
                    SELECT 1
                    FROM pathway_template local_shadow
                    WHERE local_shadow.tenant_id = :tenantId
                      AND local_shadow.template_code = platform.template_code
                      AND local_shadow.template_version = platform.template_version
                      AND (:tenantStatus IS NULL OR local_shadow.status = :tenantStatus)
                      AND (:diseaseCode IS NULL OR local_shadow.disease_code = :diseaseCode)
                      AND (:packageId IS NULL OR local_shadow.package_id = :packageId)
                      AND (:templateCode IS NULL OR local_shadow.template_code = :templateCode)
                      AND (:keyword IS NULL OR LOWER(local_shadow.template_code) LIKE :keyword OR LOWER(local_shadow.name) LIKE :keyword OR LOWER(local_shadow.disease_code) LIKE :keyword)
                  )
        ) effective_rows
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<PathwayTemplate> pageEffectiveByFilter(String tenantId, String platformTenantId,
                                                String tenantStatus, String platformStatus,
                                                String diseaseCode, String packageId,
                                                String templateCode, String keyword,
                                                int offset, int limit);
}
