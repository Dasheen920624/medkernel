package com.medkernel.engine.knowledge.delivery;

/**
 * 适配器生成的单个规范资产文件。
 *
 * @param path `.mkp` 内规范相对路径
 * @param bytes 规范 UTF-8 JSON 字节
 * @param sm3Digest 按真实文件字节计算的 SM3 摘要
 */
public record PortableAssetFile(
    String path,
    byte[] bytes,
    String sm3Digest
) {
    public PortableAssetFile {
        bytes = bytes == null ? null : bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }
}
