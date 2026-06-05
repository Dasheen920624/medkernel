package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.Set;

import com.medkernel.engine.context.ContextSnapshotResponse;

/**
 * 红线合流端口：对患者结构化上下文跑 OPT-04 临床安全红线，返回必须置顶且不可疲劳抑制的诊断身份码集合。
 *
 * <p>红线条件按 medications/conditions 等结构求值（非仅发现编码集），故入参为整份上下文快照。
 * 无红线 / 红线非诊断来源 / 未命中时返回空集（诚实降级，不阻断鉴别诊断主链路）。
 */
public interface DiagnosisRedlinePort {
    Set<String> pinnedDiagnosisCodes(String tenantId, ContextSnapshotResponse snapshot);
}
