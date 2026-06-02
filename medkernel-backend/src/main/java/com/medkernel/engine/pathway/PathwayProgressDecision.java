package com.medkernel.engine.pathway;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路径推进器输出决策。
 *
 * <p>描述本次推进的上一节点、下一节点、运行后状态、命中路径边和上下文事实证据。
 */
public record PathwayProgressDecision(
    String previousNodeCode,
    String nextNodeCode,
    PatientPathwayStatus status,
    PathwayEdgeType edgeType,
    String edgeCode,
    Map<String, Object> evidence
) {

    public PathwayProgressDecision {
        evidence = evidence == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }

    public PathwayProgressDecision(String previousNodeCode,
                                   String nextNodeCode,
                                   PatientPathwayStatus status,
                                   PathwayEdgeType edgeType) {
        this(previousNodeCode, nextNodeCode, status, edgeType, null, Map.of());
    }
}
