package com.medkernel.engine.interop;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 临床路径与 FHIR PlanDefinition / GLIF 概念模型的互操作映射结果。
 *
 * <p>PlanDefinition 面向标准交换，GLIF 面向临床路径步骤/决策审阅；回导以扩展中的路径草稿为准。
 */
public record PathwayStandardMapping(
    JsonNode planDefinition,
    JsonNode glif
) {
    public PathwayStandardMapping {
        planDefinition = planDefinition == null ? null : planDefinition.deepCopy();
        glif = glif == null ? null : glif.deepCopy();
    }
}
