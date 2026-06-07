package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 路径试运行请求。
 *
 * <p>可指定试运行起点和每一步期望进入的下一节点，用于验证模板图的可达性。
 */
public record PathwaySimulateRequest(
    @JsonProperty("request_id") String requestId,
    @JsonProperty("trace_id") String traceId,
    @JsonProperty("tenant_id") String tenantId,
    @JsonProperty("group_id") String groupId,
    @JsonProperty("hospital_id") String hospitalId,
    @JsonProperty("campus_id") String campusId,
    @JsonProperty("site_id") String siteId,
    @JsonProperty("department_id") String departmentId,
    @JsonProperty("specialty_id") String specialtyId,
    @JsonProperty("user_id") String userId,
    @JsonProperty("role_codes") List<String> roleCodes,
    @JsonProperty("package_version") String packageVersion,
    String snapshotId,
    String startNodeCode,
    List<String> requestedNextNodeCodes
) implements PathwayContextRequest {

    /**
     * 创建不可变试运行请求，并将空目标节点序列归一为空列表。
     */
    public PathwaySimulateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        requestedNextNodeCodes = requestedNextNodeCodes == null
            ? List.of() : List.copyOf(requestedNextNodeCodes);
    }

    public PathwaySimulateRequest(String startNodeCode, List<String> requestedNextNodeCodes) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            null, startNodeCode, requestedNextNodeCodes);
    }

    public PathwaySimulateRequest(String snapshotId, String startNodeCode, List<String> requestedNextNodeCodes) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            snapshotId, startNodeCode, requestedNextNodeCodes);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
