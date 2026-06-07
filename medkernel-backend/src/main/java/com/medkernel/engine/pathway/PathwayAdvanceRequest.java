package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

/**
 * 患者路径推进请求。
 *
 * <p>携带患者路径实例、事件类型、当前节点、目标节点、变异说明或退出原因等流程事实。
 */
public record PathwayAdvanceRequest(
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
    String patientPathwayId,
    @NotNull PathwayAdvanceEventType eventType,
    String currentNodeCode,
    String requestedNextNodeCode,
    VarianceType varianceType,
    String varianceReasonCode,
    String varianceReason,
    String responsibleRole,
    VarianceResolutionDecision resolutionDecision,
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
            null, patientPathwayId, eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            null, varianceReason, null, null, resolutionAction, exitReason, eventId);
    }

    public PathwayAdvanceRequest(String patientPathwayId,
                                 PathwayAdvanceEventType eventType,
                                 String currentNodeCode,
                                 String requestedNextNodeCode,
                                 VarianceType varianceType,
                                 String varianceReasonCode,
                                 String varianceReason,
                                 String responsibleRole,
                                 VarianceResolutionDecision resolutionDecision,
                                 String resolutionAction,
                                 String exitReason,
                                 String eventId) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            null, patientPathwayId, eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            varianceReasonCode, varianceReason, responsibleRole, resolutionDecision,
            resolutionAction, exitReason, eventId);
    }

    public PathwayAdvanceRequest(String patientPathwayId,
                                 PathwayAdvanceEventType eventType,
                                 String currentNodeCode,
                                 String requestedNextNodeCode,
                                 VarianceType varianceType,
                                 String varianceReason,
                                 String resolutionAction,
                                 String exitReason,
                                 String eventId,
                                 String snapshotId) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            snapshotId, patientPathwayId, eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            null, varianceReason, null, null, resolutionAction, exitReason, eventId);
    }

    public PathwayAdvanceRequest(String patientPathwayId,
                                 PathwayAdvanceEventType eventType,
                                 String currentNodeCode,
                                 String requestedNextNodeCode,
                                 VarianceType varianceType,
                                 String varianceReasonCode,
                                 String varianceReason,
                                 String responsibleRole,
                                 VarianceResolutionDecision resolutionDecision,
                                 String resolutionAction,
                                 String exitReason,
                                 String eventId,
                                 String snapshotId) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            snapshotId, patientPathwayId, eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            varianceReasonCode, varianceReason, responsibleRole, resolutionDecision,
            resolutionAction, exitReason, eventId);
    }

    public PathwayAdvanceRequest withPatientPathwayId(String id) {
        return new PathwayAdvanceRequest(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion, snapshotId, id,
            eventType, currentNodeCode, requestedNextNodeCode, varianceType,
            varianceReasonCode, varianceReason, responsibleRole, resolutionDecision,
            resolutionAction, exitReason, eventId
        );
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
