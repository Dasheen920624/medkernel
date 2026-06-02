package com.medkernel.engine.pathway;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建专病包请求。
 *
 * <p>包含专病包编码、病种、名称、版本、来源引用、说明和可选专病画像列表。
 */
public record SpecialtyPackageCreateRequest(
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
    @JsonAlias("package_version") String contextPackageVersion,
    @NotBlank String packageCode,
    @NotBlank String diseaseCode,
    @NotBlank String name,
    @NotBlank String packageVersion,
    @NotBlank String sourceRef,
    String description,
    List<@Valid SpecialtyProfileRequest> profiles
) implements PathwayContextRequest {
    public SpecialtyPackageCreateRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public SpecialtyPackageCreateRequest(String packageCode,
                                         String diseaseCode,
                                         String name,
                                         String packageVersion,
                                         String sourceRef,
                                         String description,
                                         List<SpecialtyProfileRequest> profiles) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            packageCode, diseaseCode, name, packageVersion, sourceRef, description, profiles);
    }

    public PathwayApiContext apiContext() {
        return new PathwayApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, contextPackageVersion
        );
    }
}
