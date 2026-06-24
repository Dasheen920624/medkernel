package com.medkernel.engine.terminology;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 从当前组织范围已确认映射生成术语资产草稿的请求。
 *
 * <p>版本号由服务端按稳定资产身份自动分配，调用方不得输入 V1/V2/V3。
 */
public record TerminologyAssetDraftRequest(
    @NotBlank(message = "术语资产身份不能为空")
    @Size(max = 128, message = "术语资产身份长度不能超过128")
    String assetIdentity,

    @NotBlank(message = "术语资产名称不能为空")
    @Size(max = 256, message = "术语资产名称长度不能超过256")
    String name,

    @NotBlank(message = "范围层级不能为空")
    @Size(max = 32, message = "范围层级长度不能超过32")
    String scopeLevel,

    @NotBlank(message = "范围编码不能为空")
    @Size(max = 64, message = "范围编码长度不能超过64")
    String scopeCode
) {
}
