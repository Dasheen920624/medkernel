package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 路径边仓库。
 *
 * <p>保存模板节点之间的可达关系、分支类型、条件摘要和推进优先级。
 */
@Repository
public interface PathwayEdgeRepository extends ListCrudRepository<PathwayEdge, Long> {

    /**
     * 按业务 ID 和租户查询单条路径边。
     */
    Optional<PathwayEdge> findByEdgeIdAndTenantId(String edgeId, String tenantId);

    /**
     * 查询模板内所有路径边，并按优先级升序排列。
     */
    List<PathwayEdge> findByTemplateIdAndTenantIdOrderByPriorityAsc(String templateId, String tenantId);

    /**
     * 查询指定源节点的出边集合，并按优先级升序排列。
     */
    List<PathwayEdge> findByTemplateIdAndTenantIdAndFromNodeCodeOrderByPriorityAsc(
        String templateId, String tenantId, String fromNodeCode);

    /**
     * 查询可能引用指定规则的路径流转边候选，调用方必须再解析 JSON 做精确确认。
     */
    @Query("""
        SELECT * FROM pathway_edge
        WHERE tenant_id = :tenantId
          AND (
            condition_json LIKE '%' || :ruleId || '%'
            OR condition_json LIKE '%' || :ruleCode || '%'
            OR condition_json LIKE '%' || :versionId || '%'
          )
        ORDER BY template_id ASC, priority ASC, id ASC
        """)
    List<PathwayEdge> findByTenantIdAndRuleReference(
        String tenantId, String ruleId, String ruleCode, String versionId);
}
