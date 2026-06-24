package com.medkernel.engine.authoring;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 草稿即配即试请求。
 *
 * <p>请求必须携带真实脱敏上下文快照 ID，禁止以前端样例或手工 JSON 代替快照。
 */
public record AuthoringPreviewRunRequest(
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
    AuthoringPreviewSubject subject,
    @JsonProperty("snapshot_id") String snapshotId,
    JsonNode dsl,
    String startNodeCode,
    List<String> requestedNextNodeCodes
) {
    public AuthoringPreviewRunRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        requestedNextNodeCodes = requestedNextNodeCodes == null ? List.of() : List.copyOf(requestedNextNodeCodes);
    }

    public AuthoringPreviewRunRequest(
            AuthoringApiContext context,
            AuthoringPreviewSubject subject,
            String snapshotId,
            JsonNode dsl,
            String startNodeCode,
            List<String> requestedNextNodeCodes) {
        this(
            context.requestId(),
            context.traceId(),
            context.tenantId(),
            context.groupId(),
            context.hospitalId(),
            context.campusId(),
            context.siteId(),
            context.departmentId(),
            context.specialtyId(),
            context.userId(),
            context.roleCodes(),
            subject,
            snapshotId,
            dsl,
            startNodeCode,
            requestedNextNodeCodes
        );
    }

    AuthoringApiContext apiContext() {
        return new AuthoringApiContext(
            requestId,
            traceId,
            tenantId,
            groupId,
            hospitalId,
            campusId,
            siteId,
            departmentId,
            specialtyId,
            userId,
            roleCodes
        );
    }
}
