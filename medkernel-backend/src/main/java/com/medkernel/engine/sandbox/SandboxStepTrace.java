package com.medkernel.engine.sandbox;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 沙盘编排单步轨迹，保留真实请求、响应、服务端事实与失败原因。
 */
public record SandboxStepTrace(
    String stage,
    String endpoint,
    JsonNode request,
    JsonNode response,
    Map<String, Object> serverFacts,
    String status,
    String error
) {

    public SandboxStepTrace {
        serverFacts = serverFacts == null ? Map.of() : Map.copyOf(serverFacts);
    }

    public static SandboxStepTrace ok(
            String stage,
            String endpoint,
            JsonNode request,
            JsonNode response,
            Map<String, Object> serverFacts) {
        return new SandboxStepTrace(stage, endpoint, request, response, serverFacts, "OK", null);
    }

    public static SandboxStepTrace fail(
            String stage,
            String endpoint,
            JsonNode request,
            String error) {
        return new SandboxStepTrace(stage, endpoint, request, null, Map.of(), "FAIL", error);
    }
}
