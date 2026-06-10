package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 患者入径请求。
 *
 * <p>指定 ACTIVE 标准上下文快照、路径模板和可选起始节点。患者与就诊由服务端从快照解析；
 * 路径包版本按统一入参复核，浏览器不提交可伪造的临床身份字段。
 */
public record PatientPathwayEnterRequest(
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
    @NotBlank String contextSnapshotId,
    @NotBlank String templateId,
    String startNodeCode
) implements PathwayContextRequest {
    public PatientPathwayEnterRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PatientPathwayEnterRequest(String contextSnapshotId,
                                      String templateId,
                                      String startNodeCode,
                                      String packageVersion) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), packageVersion,
            contextSnapshotId, templateId, startNodeCode);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
