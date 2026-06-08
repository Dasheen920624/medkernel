package com.medkernel.engine.authoring;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * 统一资产库编目资料更新请求。
 */
public record AuthoringAssetProfileRequest(
    @Size(max = 64, message = "资产分类最多 64 个字符")
    String category,

    @Size(max = 20, message = "资产标签最多 20 个")
    List<@Size(max = 32, message = "单个资产标签最多 32 个字符") String> tags
) {}
