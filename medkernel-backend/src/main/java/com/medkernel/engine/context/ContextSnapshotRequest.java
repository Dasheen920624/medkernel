package com.medkernel.engine.context;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/engine/context/snapshots 请求体。
 */
public record ContextSnapshotRequest(
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
    @JsonAlias("patient_id")
    @NotBlank String patientId,
    @JsonAlias("encounter_id")
    String encounterId,
    @JsonAlias("org_unit_id")
    @NotBlank String orgUnitId,
    @JsonAlias("package_version") String packageVersion,
    @JsonAlias("knowledge_package_version") String knowledgePackageVersion,
    @JsonAlias("rule_package_version") String rulePackageVersion,
    @JsonAlias("pathway_package_version") String pathwayPackageVersion,
    @NotNull @Valid ContextSnapshotResources resources
) {

    public ContextSnapshotRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        boolean unifiedPackageProvided = hasText(packageVersion);
        packageVersion = firstNonBlank(packageVersion, knowledgePackageVersion, rulePackageVersion, pathwayPackageVersion);
        if (unifiedPackageProvided) {
            knowledgePackageVersion = firstNonBlank(knowledgePackageVersion, packageVersion);
            rulePackageVersion = firstNonBlank(rulePackageVersion, packageVersion);
            pathwayPackageVersion = firstNonBlank(pathwayPackageVersion, packageVersion);
        }
    }

    public ContextSnapshotRequest(
            String patientId,
            String encounterId,
            String orgUnitId,
            String knowledgePackageVersion,
            String rulePackageVersion,
            String pathwayPackageVersion,
            ContextSnapshotResources resources) {
        this(
            null, null, null, null, null, null, null, null, null, null, List.of(),
            patientId, encounterId, orgUnitId, null,
            knowledgePackageVersion, rulePackageVersion, pathwayPackageVersion, resources
        );
    }

    public String effectiveIdempotencyKey(String headerIdempotencyKey) {
        return firstNonBlank(requestId, headerIdempotencyKey);
    }

    public String effectiveTraceId(String currentTraceId) {
        return firstNonBlank(currentTraceId, traceId);
    }

    public String effectivePackageVersion() {
        return packageVersion;
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
