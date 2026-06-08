package com.medkernel.engine.authoring;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 条件树或路径守卫自然语言预览请求。
 */
public record AuthoringPreviewRequest(
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
    AuthoringPreviewSubject subject,
    JsonNode dsl
) {
    public AuthoringPreviewRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public AuthoringPreviewRequest(AuthoringApiContext context, AuthoringPreviewSubject subject, JsonNode dsl) {
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
            context.packageVersion(),
            subject,
            dsl
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
            roleCodes,
            packageVersion
        );
    }
}
