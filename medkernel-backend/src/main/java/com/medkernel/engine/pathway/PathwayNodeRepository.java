package com.medkernel.engine.pathway;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 路径节点仓库。
 *
 * <p>保存模板图中的临床步骤、节点类型、责任角色、依赖条件和时间窗。
 */
@Repository
public interface PathwayNodeRepository extends ListCrudRepository<PathwayNode, Long> {

    /**
     * 按业务 ID 和租户查询单个路径节点。
     */
    Optional<PathwayNode> findByNodeIdAndTenantId(String nodeId, String tenantId);

    /**
     * 按模板和节点编码查询节点，用于入径起点校验和推进目标校验。
     */
    Optional<PathwayNode> findByTemplateIdAndTenantIdAndNodeCode(
        String templateId, String tenantId, String nodeCode);

    /**
     * 查询模板节点列表，并按画布顺序升序排列。
     */
    List<PathwayNode> findByTemplateIdAndTenantIdOrderBySortOrderAsc(String templateId, String tenantId);

    /**
     * 查询可能引用指定规则的路径节点候选，调用方必须再解析 JSON 做精确确认。
     */
    @Query("""
        SELECT * FROM pathway_node
        WHERE tenant_id = :tenantId
          AND (
            dependency_json LIKE '%' || :ruleId || '%'
            OR dependency_json LIKE '%' || :ruleCode || '%'
            OR dependency_json LIKE '%' || :versionId || '%'
            OR config_json LIKE '%' || :ruleId || '%'
            OR config_json LIKE '%' || :ruleCode || '%'
            OR config_json LIKE '%' || :versionId || '%'
          )
        ORDER BY template_id ASC, sort_order ASC, id ASC
        """)
    List<PathwayNode> findByTenantIdAndRuleReference(
        String tenantId, String ruleId, String ruleCode, String versionId);
}
