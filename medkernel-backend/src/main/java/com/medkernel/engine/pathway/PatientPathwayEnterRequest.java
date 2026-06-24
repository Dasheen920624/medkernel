package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/**
 * 患者入径请求。
 *
 * <p>指定 ACTIVE 标准上下文快照、临床触发点、路径模板和可选起始节点。患者与就诊由服务端从快照解析；
 * 路径版本定位由当前机构生效版本和模板发布状态解析，浏览器不提交可伪造的临床身份字段。
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
    @NotBlank String contextSnapshotId,
    @NotBlank String triggerPoint,
    @NotBlank String templateId,
    String startNodeCode
) implements PathwayContextRequest {
    public PatientPathwayEnterRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PatientPathwayEnterRequest(String contextSnapshotId,
                                      String triggerPoint,
                                      String templateId,
                                      String startNodeCode) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(),
            contextSnapshotId, triggerPoint, templateId, startNodeCode);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes
        );
    }
}
