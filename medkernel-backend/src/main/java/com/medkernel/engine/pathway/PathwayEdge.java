package com.medkernel.engine.pathway;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 临床路径中的节点连接边。
 *
 * <p>描述源节点、目标节点、分支类型、条件摘要和优先级，是确定性推进器选择下一节点的依据。
 */
@Table("pathway_edge")
public record PathwayEdge(
    @Id Long id,
    @Column("edge_id") String edgeId,
    @Column("tenant_id") String tenantId,
    @Column("template_id") String templateId,
    @Column("edge_code") String edgeCode,
    @Column("from_node_code") String fromNodeCode,
    @Column("to_node_code") String toNodeCode,
    @Column("edge_type") PathwayEdgeType edgeType,
    @Column("condition_json") String conditionJson,
    Integer priority,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy,
    @Column("updated_at") Instant updatedAt,
    @Column("updated_by") String updatedBy,
    @Column("trace_id") String traceId
) {}
