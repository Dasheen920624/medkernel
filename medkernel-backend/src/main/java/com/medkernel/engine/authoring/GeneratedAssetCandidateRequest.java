package com.medkernel.engine.authoring;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 自动生成资产候选入正式维护入口的请求。
 *
 * <p>该请求不包含调用方手工运行定位或手工版本号。生成器只提交类型化正文和来源，
 * 稳定身份版本号由统一资产版本服务自动分配。
 */
public record GeneratedAssetCandidateRequest(
    String tenantId,
    VersionedAssetType assetType,
    String assetIdentity,
    String organizationScope,
    String applicableScope,
    String sourceRef,
    String createdBy,
    String traceId,
    JsonNode content,
    List<AssetDependencyDeclaration> dependencies
) {
    public GeneratedAssetCandidateRequest {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
