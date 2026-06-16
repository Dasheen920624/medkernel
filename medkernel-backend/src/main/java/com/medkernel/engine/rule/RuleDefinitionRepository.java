package com.medkernel.engine.rule;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 规则定义持久化仓库（GA-ENG-API-05）。
 *
 * <p>承担按租户 + 业务键查询、已发布列表加载与分页过滤计数，均强制带租户隔离。
 */
@Repository
public interface RuleDefinitionRepository extends ListCrudRepository<RuleDefinition, Long> {

    /**
     * 按规则业务 ID 与租户 ID 查询单条规则定义，用于详情、试运行、发布等单规则入口。
     */
    Optional<RuleDefinition> findByRuleIdAndTenantId(String ruleId, String tenantId);

    /**
     * 按租户和规则编码查询规则定义，用于创建前的同租户唯一性校验。
     */
    Optional<RuleDefinition> findByTenantIdAndRuleCode(String tenantId, String ruleCode);

    /**
     * 按租户查询内容已审核的规则定义，按更新时间倒序。
     *
     * <p>运行入口还必须校验对应统一资产版本为 {@code ACTIVE}，本查询不能单独证明规则可执行。
     */
    @Query("""
        SELECT * FROM rule_definition
        WHERE tenant_id = :tenantId AND status = 'PUBLISHED'
        ORDER BY updated_at DESC, id DESC
        """)
    List<RuleDefinition> findPublishedByTenantId(String tenantId);

    /**
     * 按状态、类型、风险级别可选过滤的规则定义分页查询；过滤条件为 {@code null} 时跳过。
     */
    @Query("""
        SELECT * FROM rule_definition
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:ruleType IS NULL OR rule_type = :ruleType)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:keyword IS NULL OR LOWER(rule_code) LIKE :keyword OR LOWER(name) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleDefinition> pageByFilter(String tenantId, String status, String ruleType,
                                      String riskLevel, String keyword, int offset, int limit);

    /**
     * 与分页查询同口径的完整列表，用于平台主源与当前租户覆盖层的有效资产合并。
     */
    @Query("""
        SELECT * FROM rule_definition
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:ruleType IS NULL OR rule_type = :ruleType)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:keyword IS NULL OR LOWER(rule_code) LIKE :keyword OR LOWER(name) LIKE :keyword)
        ORDER BY updated_at DESC, id DESC
        """)
    List<RuleDefinition> listByFilter(String tenantId, String status, String ruleType,
                                      String riskLevel, String keyword);

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT local.rule_code
            FROM rule_definition local
            WHERE local.tenant_id = :tenantId
              AND (:tenantStatus IS NULL OR local.status = :tenantStatus)
              AND (:ruleType IS NULL OR local.rule_type = :ruleType)
              AND (:riskLevel IS NULL OR local.risk_level = :riskLevel)
              AND (:keyword IS NULL OR LOWER(local.rule_code) LIKE :keyword OR LOWER(local.name) LIKE :keyword)
            UNION ALL
            SELECT platform.rule_code
            FROM rule_definition platform
            WHERE platform.tenant_id = :platformTenantId
              AND platform.status = :platformStatus
              AND (:ruleType IS NULL OR platform.rule_type = :ruleType)
              AND (:riskLevel IS NULL OR platform.risk_level = :riskLevel)
              AND (:keyword IS NULL OR LOWER(platform.rule_code) LIKE :keyword OR LOWER(platform.name) LIKE :keyword)
              AND NOT EXISTS (
                    SELECT 1
                    FROM rule_definition local_shadow
                    WHERE local_shadow.tenant_id = :tenantId
                      AND local_shadow.rule_code = platform.rule_code
                      AND (:tenantStatus IS NULL OR local_shadow.status = :tenantStatus)
                      AND (:ruleType IS NULL OR local_shadow.rule_type = :ruleType)
                      AND (:riskLevel IS NULL OR local_shadow.risk_level = :riskLevel)
                      AND (:keyword IS NULL OR LOWER(local_shadow.rule_code) LIKE :keyword OR LOWER(local_shadow.name) LIKE :keyword)
                  )
        ) effective_rows
        """)
    long countEffectiveByFilter(String tenantId, String platformTenantId,
                                String tenantStatus, String platformStatus,
                                String ruleType, String riskLevel, String keyword);

    @Query("""
        SELECT * FROM (
            SELECT local.*
            FROM rule_definition local
            WHERE local.tenant_id = :tenantId
              AND (:tenantStatus IS NULL OR local.status = :tenantStatus)
              AND (:ruleType IS NULL OR local.rule_type = :ruleType)
              AND (:riskLevel IS NULL OR local.risk_level = :riskLevel)
              AND (:keyword IS NULL OR LOWER(local.rule_code) LIKE :keyword OR LOWER(local.name) LIKE :keyword)
            UNION ALL
            SELECT platform.*
            FROM rule_definition platform
            WHERE platform.tenant_id = :platformTenantId
              AND platform.status = :platformStatus
              AND (:ruleType IS NULL OR platform.rule_type = :ruleType)
              AND (:riskLevel IS NULL OR platform.risk_level = :riskLevel)
              AND (:keyword IS NULL OR LOWER(platform.rule_code) LIKE :keyword OR LOWER(platform.name) LIKE :keyword)
              AND NOT EXISTS (
                    SELECT 1
                    FROM rule_definition local_shadow
                    WHERE local_shadow.tenant_id = :tenantId
                      AND local_shadow.rule_code = platform.rule_code
                      AND (:tenantStatus IS NULL OR local_shadow.status = :tenantStatus)
                      AND (:ruleType IS NULL OR local_shadow.rule_type = :ruleType)
                      AND (:riskLevel IS NULL OR local_shadow.risk_level = :riskLevel)
                      AND (:keyword IS NULL OR LOWER(local_shadow.rule_code) LIKE :keyword OR LOWER(local_shadow.name) LIKE :keyword)
                  )
        ) effective_rows
        ORDER BY updated_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleDefinition> pageEffectiveByFilter(String tenantId, String platformTenantId,
                                               String tenantStatus, String platformStatus,
                                               String ruleType, String riskLevel, String keyword,
                                               int offset, int limit);

    /**
     * 与 {@link #pageByFilter} 同口径的总数查询，用于分页响应的 total 字段。
     */
    @Query("""
        SELECT COUNT(*) FROM rule_definition
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:ruleType IS NULL OR rule_type = :ruleType)
          AND (:riskLevel IS NULL OR risk_level = :riskLevel)
          AND (:keyword IS NULL OR LOWER(rule_code) LIKE :keyword OR LOWER(name) LIKE :keyword)
        """)
    long countByFilter(String tenantId, String status, String ruleType, String riskLevel, String keyword);

    /**
     * 统一创作资产库按标签、收藏和关键字分页查询规则，避免全量租户快照。
     */
    @Query("""
        SELECT r.*
        FROM rule_definition r
        LEFT JOIN mk_engine_authoring_asset_profile p
          ON p.tenant_id = r.tenant_id
         AND p.asset_type = 'RULE'
         AND p.asset_id = r.rule_id
        WHERE r.tenant_id = :tenantId
          AND (
              :keyword IS NULL
              OR LOWER(r.rule_code) LIKE :keyword
              OR LOWER(r.name) LIKE :keyword
              OR LOWER(COALESCE(p.category, '')) LIKE :keyword
              OR LOWER(p.tags_json) LIKE :keyword
          )
          AND (:tagPattern IS NULL OR LOWER(p.tags_json) LIKE :tagPattern)
          AND (:favoriteUserId IS NULL OR EXISTS (
              SELECT 1
              FROM mk_engine_authoring_asset_favorite f
              WHERE f.tenant_id = r.tenant_id
                AND f.user_id = :favoriteUserId
                AND f.asset_type = 'RULE'
                AND f.asset_id = r.rule_id
          ))
        ORDER BY r.updated_at DESC, r.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleDefinition> pageForAuthoringLibrary(
        String tenantId,
        String keyword,
        String tagPattern,
        String favoriteUserId,
        int offset,
        int limit);

    /**
     * 与 {@link #pageForAuthoringLibrary} 同口径统计规则资产数。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rule_definition r
        LEFT JOIN mk_engine_authoring_asset_profile p
          ON p.tenant_id = r.tenant_id
         AND p.asset_type = 'RULE'
         AND p.asset_id = r.rule_id
        WHERE r.tenant_id = :tenantId
          AND (
              :keyword IS NULL
              OR LOWER(r.rule_code) LIKE :keyword
              OR LOWER(r.name) LIKE :keyword
              OR LOWER(COALESCE(p.category, '')) LIKE :keyword
              OR LOWER(p.tags_json) LIKE :keyword
          )
          AND (:tagPattern IS NULL OR LOWER(p.tags_json) LIKE :tagPattern)
          AND (:favoriteUserId IS NULL OR EXISTS (
              SELECT 1
              FROM mk_engine_authoring_asset_favorite f
              WHERE f.tenant_id = r.tenant_id
                AND f.user_id = :favoriteUserId
                AND f.asset_type = 'RULE'
                AND f.asset_id = r.rule_id
          ))
        """)
    long countForAuthoringLibrary(
        String tenantId,
        String keyword,
        String tagPattern,
        String favoriteUserId);

    /**
     * 条件片段影响分析按当前激活规则版本 DSL 预过滤候选规则，避免全量规则扫描。
     */
    @Query("""
        SELECT r.*
        FROM rule_definition r
        JOIN rule_version rv
          ON rv.tenant_id = r.tenant_id
         AND rv.version_id = r.active_version_id
        WHERE r.tenant_id = :tenantId
          AND r.active_version_id IS NOT NULL
          AND LOWER(rv.dsl_json) LIKE :fragmentPattern
        ORDER BY r.updated_at DESC, r.id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<RuleDefinition> pageActiveRuleImpactsByFragmentPattern(
        String tenantId,
        String fragmentPattern,
        int offset,
        int limit);

    /**
     * 与 {@link #pageActiveRuleImpactsByFragmentPattern} 同口径统计规则影响候选数。
     */
    @Query("""
        SELECT COUNT(*)
        FROM rule_definition r
        JOIN rule_version rv
          ON rv.tenant_id = r.tenant_id
         AND rv.version_id = r.active_version_id
        WHERE r.tenant_id = :tenantId
          AND r.active_version_id IS NOT NULL
          AND LOWER(rv.dsl_json) LIKE :fragmentPattern
        """)
    long countActiveRuleImpactsByFragmentPattern(String tenantId, String fragmentPattern);
}
