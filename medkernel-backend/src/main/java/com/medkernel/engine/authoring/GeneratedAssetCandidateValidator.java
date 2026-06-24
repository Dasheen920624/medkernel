package com.medkernel.engine.authoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * 自动生成资产的类型专属结构校验器。
 */
public interface GeneratedAssetCandidateValidator {

    /**
     * 支持的资产类型。
     */
    VersionedAssetType assetType();

    /**
     * 校验正文并返回可登记为统一草稿版本的规范 JSON 与运行必需依赖。
     */
    GeneratedAssetValidation validate(String assetIdentity, JsonNode content);
}
