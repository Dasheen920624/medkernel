package com.medkernel.engine.evaluation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 评估指标持久化仓库（GA-ENG-API-08 指标配置版本）。
 *
 * <p>支持按租户查询指标详情、同编码生效版本、可选过滤分页和激活旧版下线。
 */
@Repository
public interface EvaluationIndicatorRepository extends ListCrudRepository<EvaluationIndicator, Long> {

    /**
     * 按指标业务 ID 与租户 ID 查询单个指标版本。
     */
    Optional<EvaluationIndicator> findByIndicatorIdAndTenantId(String indicatorId, String tenantId);

    Optional<EvaluationIndicator> findTopByTenantIdAndIndicatorCodeOrderByVersionNoDesc(
        String tenantId,
        String indicatorCode
    );

    /**
     * 按租户列出全部指标版本，按更新时间倒序用于管理端浏览。
     */
    List<EvaluationIndicator> findByTenantIdOrderByUpdatedAtDesc(String tenantId);

    /**
     * 按租户 ID 与指标状态获取指标版本集合。
     */
    List<EvaluationIndicator> findByTenantIdAndStatus(String tenantId, EvaluationIndicatorStatus status);

    /**
     * 查询同租户、同指标编码、指定状态的指标版本，用于激活新版本时下线旧 {@code ACTIVE} 版本。
     */
    List<EvaluationIndicator> findByTenantIdAndIndicatorCodeAndStatus(
        String tenantId, String indicatorCode, EvaluationIndicatorStatus status);

    /**
     * 按稳定指标编码与业务版本号定位运行修订锁定的精确指标版本。
     */
    Optional<EvaluationIndicator> findByTenantIdAndIndicatorCodeAndVersionNo(
        String tenantId, String indicatorCode, int versionNo);

    /**
     * 按状态、对象类型和指标编码可选过滤统计指标总数。
     */
    @Query("""
        SELECT COUNT(*)
        FROM evaluation_indicator
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:subjectType IS NULL OR subject_type = :subjectType)
          AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
        """)
    long countByFilter(String tenantId, String status, String subjectType, String indicatorCode);

    /**
     * 与 {@link #countByFilter} 同口径分页查询指标版本，按更新时间倒序返回。
     */
    @Query("""
        SELECT *
        FROM evaluation_indicator
        WHERE tenant_id = :tenantId
          AND (:status IS NULL OR status = :status)
          AND (:subjectType IS NULL OR subject_type = :subjectType)
          AND (:indicatorCode IS NULL OR indicator_code = :indicatorCode)
        ORDER BY updated_at DESC, id DESC
        LIMIT :limit OFFSET :offset
        """)
    List<EvaluationIndicator> pageByFilter(
        String tenantId, String status, String subjectType, String indicatorCode, int offset, int limit);
}
