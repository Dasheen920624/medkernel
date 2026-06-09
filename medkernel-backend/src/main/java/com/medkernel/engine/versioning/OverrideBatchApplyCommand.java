package com.medkernel.engine.versioning;

/**
 * 覆盖批量生效命令。
 */
public record OverrideBatchApplyCommand(
    OverrideBatchPreviewCommand preview,
    String confirmedPreviewDigest
) {
}
