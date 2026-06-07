package com.medkernel.engine.context;

import java.util.List;
import java.util.Map;

/**
 * 字典映射端口。
 *
 * <p>{@code ContextSnapshotService} 通过此端口查询每类资源的映射状态，
 * 而非直连 {@code engine.terminology} 内部实现，避免循环依赖。
 * 完整应用必须装配真实术语映射实现；缺失实现属于启动配置错误，不允许静默降级。
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
