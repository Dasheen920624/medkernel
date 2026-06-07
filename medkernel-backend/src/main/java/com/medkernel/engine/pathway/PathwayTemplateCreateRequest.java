package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 创建路径模板请求。
 *
 * <p>一次性携带模板主数据、阶段里程碑、节点、边和指标绑定，保存为可发布前校验的草稿资产。
 */
public record PathwayTemplateCreateRequest(
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
    @NotBlank String packageId,
    @NotBlank String templateCode,
    @NotBlank String name,
    @NotBlank String diseaseCode,
    @NotNull Integer templateVersion,
    @NotNull PathwayTemplateLevel templateLevel,
    @NotNull PathwayEntryMode entryMode,
    @NotBlank String startNodeCode,
    @NotBlank String sourceRef,
    String description,
    JsonNode entryCriteria,
    JsonNode exitCriteria,
    List<@Valid PathwayMilestoneRequest> milestones,
    @NotEmpty List<@Valid PathwayNodeRequest> nodes,
    List<@Valid PathwayEdgeRequest> edges,
    List<@Valid SpecialtyMetricBindingRequest> metricBindings
) implements PathwayContextRequest {
    public PathwayTemplateCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
    }

    public PathwayTemplateCreateRequest(String packageId,
                                        String templateCode,
                                        String name,
                                        String diseaseCode,
                                        Integer templateVersion,
                                        PathwayTemplateLevel templateLevel,
                                        PathwayEntryMode entryMode,
                                        String startNodeCode,
                                        String sourceRef,
                                        String description,
                                        JsonNode entryCriteria,
                                        JsonNode exitCriteria,
                                        List<PathwayMilestoneRequest> milestones,
                                        List<PathwayNodeRequest> nodes,
                                        List<PathwayEdgeRequest> edges,
                                        List<SpecialtyMetricBindingRequest> metricBindings) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            packageId, templateCode, name, diseaseCode, templateVersion, templateLevel,
            entryMode, startNodeCode, sourceRef, description, entryCriteria, exitCriteria,
            milestones, nodes, edges, metricBindings);
    }

    public PathwayTemplateCreateRequest(String packageId,
                                        String templateCode,
                                        String name,
                                        String diseaseCode,
                                        Integer templateVersion,
                                        PathwayTemplateLevel templateLevel,
                                        PathwayEntryMode entryMode,
                                        String startNodeCode,
                                        String sourceRef,
                                        String description,
                                        JsonNode entryCriteria,
                                        JsonNode exitCriteria,
                                        List<PathwayNodeRequest> nodes,
                                        List<PathwayEdgeRequest> edges,
                                        List<SpecialtyMetricBindingRequest> metricBindings) {
        this(packageId, templateCode, name, diseaseCode, templateVersion, templateLevel,
            entryMode, startNodeCode, sourceRef, description, entryCriteria, exitCriteria,
            List.of(), nodes, edges, metricBindings);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
