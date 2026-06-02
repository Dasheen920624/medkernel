package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotNull;

/**
 * 患者路径推进请求。
 *
 * <p>携带患者路径实例、事件类型、当前节点、目标节点、变异说明或退出原因等流程事实。
 */
public record PathwayAdvanceRequest(
    @JsonAlias("request_id") String requestId,
    @JsonAlias("trace_id") String traceId,
    @JsonAlias("tenant_id") String tenantId,
    @JsonAlias("group_id") String groupId,
    @JsonAlias("hospital_id") String hospitalId,
    @JsonAlias("campus_id") String campusId,
    @JsonAlias("site_id") String siteId,
    @JsonAlias("department_id") String departmentId,
    @JsonAlias("specialty_id") String specialtyId,
    @JsonAlias("user_id") String userId,
    @JsonAlias("role_codes") List<String> roleCodes,
    @JsonAlias("package_version") String packageVersion,
    String patientPathwayId,
    @NotNull PathwayAdvanceEventType eventType,
    String currentNodeCode,
    String requestedNextNodeCode,
    VarianceType varianceType,
    String varianceReason,
    String resolutionAction,
    String exitReason,
    String eventId
) implements PathwayContextRequest {
    public PathwayAdvanceRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PathwayAdvanceRequest(String patientPathwayId,
                                 PathwayAdvanceEventType eventType,
                                 String currentNodeCode,
                                 String requestedNextNodeCode,
                                 VarianceType varianceType,
                                 String varianceReason,
                                 String resolutionAction,
                                 String exitReason,
                                 String eventId) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            patientPathwayId, eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            varianceReason, resolutionAction, exitReason, eventId);
    }

    public PathwayAdvanceRequest withPatientPathwayId(String id) {
        return new PathwayAdvanceRequest(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion, id,
            eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            varianceReason, resolutionAction, exitReason, eventId
        );
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
