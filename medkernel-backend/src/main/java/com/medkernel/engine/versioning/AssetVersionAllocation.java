package com.medkernel.engine.versioning;

/**
 * 稳定资产身份分配出的服务端版本号。
 */
public record AssetVersionAllocation(
    long sequence,
    String versionNo
) {
}
