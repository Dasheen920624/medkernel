package com.medkernel.engine.pathway;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 路径推进器输入命令。
 *
 * <p>将路径图、当前节点、推进事件、可选目标节点和上下文事实组合为一次纯计算输入。
 */
public record PathwayProgressCommand(
    PathwayGraph graph,
    String currentNodeCode,
    PathwayAdvanceEventType eventType,
    String requestedNextNodeCode,
    Map<String, Object> facts,
    String runtimeReleaseId,
    String tenantId
) {

    public PathwayProgressCommand {
        facts = facts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    public PathwayProgressCommand(PathwayGraph graph,
                                  String currentNodeCode,
                                  PathwayAdvanceEventType eventType,
                                  String requestedNextNodeCode,
                                  Map<String, Object> facts,
                                  String runtimeReleaseId) {
        this(graph, currentNodeCode, eventType, requestedNextNodeCode, facts, runtimeReleaseId, null);
    }

    public PathwayProgressCommand(PathwayGraph graph,
                                  String currentNodeCode,
                                  PathwayAdvanceEventType eventType,
                                  String requestedNextNodeCode,
                                  Map<String, Object> facts) {
        this(graph, currentNodeCode, eventType, requestedNextNodeCode, facts, null, null);
    }

    public PathwayProgressCommand(PathwayGraph graph,
                                  String currentNodeCode,
                                  PathwayAdvanceEventType eventType,
                                  String requestedNextNodeCode) {
        this(graph, currentNodeCode, eventType, requestedNextNodeCode, Map.of(), null, null);
    }
}
