package com.medkernel.engine.context;

import java.util.List;
import java.util.Map;

/**
 * 字典映射端口。
 *
 * <p>{@code ContextSnapshotService} 通过此端口查询每类资源的映射状态，
 * 而非直连 {@code engine.terminology} 内部实现，避免循环依赖。
 *
 * <p>当 terminology 模块未提供该端口的 Bean 时，使用 {@link NoopTerminologyMappingPort}
 * 默认 stub，返回 UNKNOWN 状态，snapshot 仍可创建但 mapping_status 标记为待补全。
 */
public interface TerminologyMappingPort {

    /**
     * 评估 snapshot 中各资源类型的映射状态。
     *
     * @param tenantId          租户
     * @param anchors           每个标准资源编码字段的可追踪锚点
     * @return                  anchor.key → "VALID" / "PARTIAL" / "UNKNOWN"
     */
    Map<String, String> evaluate(String tenantId, List<ClinicalCodeMappingAnchor> anchors);
}
