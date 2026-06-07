package com.medkernel.engine.pathway;

import java.time.Instant;
import java.util.List;

/**
 * 路径节点进入待办中心的命令。
 */
public record PathwayNodeWorklistCommand(
    String tenantId,
    String orgUnitId,
    String patientPathwayId,
    String patientId,
    String encounterId,
    String nodeCode,
    String nodeName,
    PathwayNodeType nodeType,
    String clockId,
    String responsibleRole,
    String accountableRole,
    List<String> consultedRoles,
    List<String> informedRoles,
    Instant dueAt,
    String deepLink,
    String traceId,
    String actor
) {
    public PathwayNodeWorklistCommand {
        consultedRoles = consultedRoles == null ? List.of() : List.copyOf(consultedRoles);
        informedRoles = informedRoles == null ? List.of() : List.copyOf(informedRoles);
    }
}
