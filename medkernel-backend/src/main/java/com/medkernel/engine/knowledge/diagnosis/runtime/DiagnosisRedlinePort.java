package com.medkernel.engine.knowledge.diagnosis.runtime;

import java.util.List;
import java.util.Set;

/**
 * 红线合流端口：对发现集跑 OPT-04 临床安全红线。未接线时实现返回空、不阻断（B0 诚实降级）。
 */
@FunctionalInterface
public interface DiagnosisRedlinePort {
    List<RedlineHit> check(String tenantId, Set<String> normalizedFindings);
}
