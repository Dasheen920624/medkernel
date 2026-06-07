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
 * <p>一次性携带模板主数据、节点、边和指标绑定，保存为可发布前校验的草稿资产。
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
    @NotBlank String startNodeCode,
    @NotBlank String sourceRef,
    String description,
    JsonNode entryCriteria,
    JsonNode exitCriteria,
    @NotEmpty List<@Valid PathwayNodeRequest> nodes,
    List<@Valid PathwayEdgeRequest> edges,
    List<@Valid SpecialtyMetricBindingRequest> metricBindings
) implements PathwayContextRequest {
    public PathwayTemplateCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PathwayTemplateCreateRequest(String packageId,
                                        String templateCode,
                                        String name,
                                        String diseaseCode,
                                        Integer templateVersion,
                                        PathwayTemplateLevel templateLevel,
                                        String startNodeCode,
                                        String sourceRef,
                                        String description,
                                        JsonNode entryCriteria,
                                        JsonNode exitCriteria,
                                        List<PathwayNodeRequest> nodes,
                                        List<PathwayEdgeRequest> edges,
                                        List<SpecialtyMetricBindingRequest> metricBindings) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            packageId, templateCode, name, diseaseCode, templateVersion, templateLevel,
            startNodeCode, sourceRef, description, entryCriteria, exitCriteria,
            nodes, edges, metricBindings);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
