package com.medkernel.engine.pkg;

import com.medkernel.engine.versioning.VersionedAssetType;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 知识包添加子项资产请求 DTO。
 */
public record PackageItemRequest(
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

    @NotNull(message = "资产类型不能为空")
    VersionedAssetType assetType,

    @NotBlank(message = "资产 ID 不能为空")
    String assetId,

    @NotBlank(message = "资产版本号不能为空")
    String assetVersion
) implements PackageContextRequest {
    public PackageItemRequest {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
    }

    public PackageItemRequest(
            VersionedAssetType assetType,
            String assetId,
            String assetVersion) {
        this(null, null, null, null, null, null, null, null, null, null, List.of(), null,
            assetType, assetId, assetVersion);
    }

    public PackageApiContext apiContext() {
        return new PackageApiContext(
            requestId, traceId, tenantId, groupId, hospitalId, campusId, siteId,
            departmentId, specialtyId, userId, roleCodes, packageVersion
        );
    }
}
