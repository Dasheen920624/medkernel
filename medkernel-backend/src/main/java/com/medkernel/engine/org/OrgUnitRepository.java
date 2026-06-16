package com.medkernel.engine.org;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.medkernel.shared.context.OrgLevel;

/**
 * 组织单元 Spring Data JDBC 仓库。
 *
 * <p>所有方法都按 {@code tenantId} 强制过滤；不提供未带租户的全表方法，避免误用造成跨租户泄漏。
 */
@Repository
public interface OrgUnitRepository extends ListCrudRepository<OrgUnit, String> {

    /**
     * 按租户和组织编码读取组织单元。
     *
     * @param tenantId 租户标识
     * @param code 组织编码
     * @return 匹配的组织单元
     */
    Optional<OrgUnit> findByTenantIdAndCode(String tenantId, String code);

    /**
     * 按租户和主键读取组织单元。
     *
     * @param tenantId 租户标识
     * @param id 组织单元主键
     * @return 匹配的组织单元
     */
    Optional<OrgUnit> findByTenantIdAndId(String tenantId, String id);

    /**
     * 读取当前租户的根组织单元。
     *
     * @param tenantId 租户标识
     * @return 未设置父节点的租户根组织
     */
    Optional<OrgUnit> findByTenantIdAndParentIdIsNull(String tenantId);

    /**
     * 按租户和层级列出组织单元。
     *
     * @param tenantId 租户标识
     * @param level 组织层级
     * @return 按编码排序的组织单元列表
     */
    List<OrgUnit> findByTenantIdAndLevelOrderByCodeAsc(String tenantId, OrgLevel level);

    /**
     * 按租户、层级和状态统计组织单元，用于就绪门禁等存在性判断。
     *
     * @param tenantId 租户标识
     * @param level 组织层级
     * @param status 组织状态
     * @return 匹配组织单元数量
     */
    long countByTenantIdAndLevelAndStatus(String tenantId, OrgLevel level, OrgUnitStatus status);

    /**
     * 按租户和专科标识读取挂载该专科的组织单元。
     *
     * @param tenantId 租户标识
     * @param specialtyId 专科标识
     * @return 按编码排序的组织单元列表
     */
    List<OrgUnit> findByTenantIdAndSpecialtyIdOrderByCodeAsc(String tenantId, String specialtyId);

    /**
     * 列出当前租户全部组织单元。
     *
     * @param tenantId 租户标识
     * @return 按层级和编码排序的组织单元列表
     */
    List<OrgUnit> findByTenantIdOrderByLevelAscCodeAsc(String tenantId);

    /**
     * 列出当前租户下指定父节点的直接子组织。
     *
     * @param tenantId 租户标识
     * @param parentId 父组织节点标识
     * @return 按编码排序的直接子组织列表
     */
    List<OrgUnit> findByTenantIdAndParentIdOrderByCodeAsc(String tenantId, String parentId);

    /**
     * 统计当前租户组织单元数量。
     *
     * @param tenantId 租户标识
     * @return 组织单元数量
     */
    @Query("SELECT COUNT(*) FROM org_unit WHERE tenant_id = :tenantId")
    long countByTenantId(String tenantId);

    /**
     * 平台级：列出所有租户根组织（level=TENANT）。本方法**跨租户**，仅供平台开通/管理入口
     * （{@code tenant.write}/{@code tenant.read} 守卫）使用，不得用于租户内业务查询。
     */
    @Query("SELECT * FROM org_unit WHERE level_code = 'TENANT' ORDER BY created_at DESC")
    List<OrgUnit> findAllTenantRoots();

    /**
     * 分页读取当前租户组织单元。
     *
     * @param tenantId 租户标识
     * @param offset 起始偏移量
     * @param limit 返回条数
     * @return 当前页组织单元列表
     */
    @Query("""
        SELECT * FROM org_unit
        WHERE tenant_id = :tenantId
        ORDER BY level_code, code
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<OrgUnit> pageByTenantId(String tenantId, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM org_unit u
        WHERE u.tenant_id = :tenantId
          AND (:keyword IS NULL
            OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(u.specialty_id, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:level IS NULL OR u.level_code = :level)
          AND (:status IS NULL OR u.status = :status)
          AND (
            :scope IS NULL
            OR (:scope = 'SERVICE_ORGANIZATION'
                AND u.level_code IN ('TENANT','REGION','FACILITY','CAMPUS'))
            OR (:scope = 'BUSINESS_SCOPE' AND u.level_code <> 'PLATFORM')
          )
          AND (
            :ancestorId IS NULL
            OR EXISTS (
              SELECT 1
              FROM org_closure c
              WHERE c.tenant_id = u.tenant_id
                AND c.ancestor_id = :ancestorId
                AND c.descendant_id = u.id
            )
          )
        """)
    long countDirectory(
        @Param("tenantId") String tenantId,
        @Param("keyword") String keyword,
        @Param("level") String level,
        @Param("status") String status,
        @Param("scope") String scope,
        @Param("ancestorId") String ancestorId
    );

    @Query("""
        SELECT u.* FROM org_unit u
        WHERE u.tenant_id = :tenantId
          AND (:keyword IS NULL
            OR LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(COALESCE(u.specialty_id, '')) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:level IS NULL OR u.level_code = :level)
          AND (:status IS NULL OR u.status = :status)
          AND (
            :scope IS NULL
            OR (:scope = 'SERVICE_ORGANIZATION'
                AND u.level_code IN ('TENANT','REGION','FACILITY','CAMPUS'))
            OR (:scope = 'BUSINESS_SCOPE' AND u.level_code <> 'PLATFORM')
          )
          AND (
            :ancestorId IS NULL
            OR EXISTS (
              SELECT 1
              FROM org_closure c
              WHERE c.tenant_id = u.tenant_id
                AND c.ancestor_id = :ancestorId
                AND c.descendant_id = u.id
            )
          )
        ORDER BY u.name, u.code
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<OrgUnit> pageDirectory(
        @Param("tenantId") String tenantId,
        @Param("keyword") String keyword,
        @Param("level") String level,
        @Param("status") String status,
        @Param("scope") String scope,
        @Param("ancestorId") String ancestorId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
}
