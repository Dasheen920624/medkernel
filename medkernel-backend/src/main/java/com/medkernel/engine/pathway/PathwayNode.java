package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 临床路径中的临床节点。
 *
 * <p>保存节点编码、节点类型、所属里程碑、执行顺序、责任分工角色、依赖条件、时间窗、终止/禁用标记和配置摘要。
 */
@Table("pathway_node")
public record PathwayNode(
    @Id Long id,
    @Column("node_id") String nodeId,
    @Column("tenant_id") String tenantId,
    @Column("template_id") String templateId,
    @Column("node_code") String nodeCode,
    String name,
    @Column("node_type") PathwayNodeType nodeType,
    @Column("milestone_code") String milestoneCode,
    @Column("sort_order") Integer sortOrder,
    @Column("responsible_role") String responsibleRole,
    @Column("accountable_role") String accountableRole,
    @Column("consulted_roles_json") String consultedRolesJson,
    @Column("informed_roles_json") String informedRolesJson,
    @Column("dependency_json") String dependencyJson,
    @Column("time_window_minutes") Integer timeWindowMinutes,
    @Column("terminal_flag") Boolean terminalFlag,
    @Column("disabled_flag") Boolean disabledFlag,
    @Column("config_json") String configJson,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {
    public PathwayNode(
            Long id,
            String nodeId,
            String tenantId,
            String templateId,
            String nodeCode,
            String name,
            PathwayNodeType nodeType,
            String milestoneCode,
            Integer sortOrder,
            String responsibleRole,
            String accountableRole,
            String consultedRolesJson,
            String informedRolesJson,
            String dependencyJson,
            Integer timeWindowMinutes,
            Boolean terminalFlag,
            String configJson,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, nodeId, tenantId, templateId, nodeCode, name, nodeType, milestoneCode, sortOrder,
            responsibleRole, accountableRole, consultedRolesJson, informedRolesJson, dependencyJson,
            timeWindowMinutes, terminalFlag, false, configJson, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }

    public PathwayNode(
            Long id,
            String nodeId,
            String tenantId,
            String templateId,
            String nodeCode,
            String name,
            PathwayNodeType nodeType,
            String milestoneCode,
            Integer sortOrder,
            String responsibleRole,
            String dependencyJson,
            Integer timeWindowMinutes,
            Boolean terminalFlag,
            String configJson,
            Instant createdAt,
            String createdBy,
            Instant updatedAt,
            String updatedBy,
            String traceId) {
        this(id, nodeId, tenantId, templateId, nodeCode, name, nodeType, milestoneCode, sortOrder,
            responsibleRole, responsibleRole, "[]", "[]", dependencyJson, timeWindowMinutes, terminalFlag,
            false, configJson, createdAt, createdBy, updatedAt, updatedBy, traceId);
    }
}
