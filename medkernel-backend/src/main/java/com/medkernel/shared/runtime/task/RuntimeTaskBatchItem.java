package com.medkernel.shared.runtime.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 批量任务输入项。
 *
 * @param itemId      调用方提供的单项 ID
 * @param payloadJson 单项 JSON payload
 */
public record RuntimeTaskBatchItem(
    @NotBlank String itemId,
    @NotBlank @Size(max = 262_144) String payloadJson
) {
}
