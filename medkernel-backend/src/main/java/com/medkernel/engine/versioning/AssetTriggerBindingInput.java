package com.medkernel.engine.versioning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 资产版本触发绑定维护入参。
 *
 * @param triggerPoint 标准临床触发点编码
 * @param purpose 触发用途
 * @param requiredFields 执行前必须具备的标准字段编码
 */
public record AssetTriggerBindingInput(
    @JsonProperty("trigger_point") @NotBlank String triggerPoint,
    @NotNull AssetTriggerPurpose purpose,
    @JsonProperty("required_fields") List<@NotBlank String> requiredFields
) {
    public AssetTriggerBindingInput {
        requiredFields = requiredFields == null ? List.of() : List.copyOf(requiredFields);
    }
}
