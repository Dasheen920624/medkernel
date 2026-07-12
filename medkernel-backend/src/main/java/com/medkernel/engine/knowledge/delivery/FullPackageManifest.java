package com.medkernel.engine.knowledge.delivery;

import java.util.List;

import com.medkernel.engine.knowledge.authority.MedicalPackageType;

/**
 * 可移植医疗资源完整包的确定性 manifest。
 *
 * @param schemaVersion manifest 模式版本
 * @param packageType 包类型；首发只允许 FULL
 * @param deliveryId 与宿主和介质无关的稳定交付标识
 * @param authorityId 平台知识权威稳定标识
 * @param issuerInstanceId 实际签发实例稳定标识
 * @param keyId 外置签名密钥公开标识
 * @param releaseSequence 权威内单调发布序号
 * @param platformReleaseIdentity 平台标准版本稳定标识
 * @param parentManifestDigest 父交付 manifest 摘要；FULL 必须为空
 * @param compatibility 包格式、引擎和数据库模式兼容范围
 * @param files 包内全部内容文件的真实字节事实
 */
public record FullPackageManifest(
    String schemaVersion,
    MedicalPackageType packageType,
    String deliveryId,
    String authorityId,
    String issuerInstanceId,
    String keyId,
    long releaseSequence,
    String platformReleaseIdentity,
    String parentManifestDigest,
    Compatibility compatibility,
    List<FileEntry> files
) {

    /**
     * 包格式与运行环境兼容范围。
     *
     * @param packageFormatVersion .mkp 容器格式版本
     * @param minimumEngineVersion 最低引擎版本
     * @param maximumEngineVersion 最高引擎版本范围
     * @param minimumDatabaseSchemaVersion 最低数据库模式版本
     * @param maximumDatabaseSchemaVersion 最高数据库模式版本
     */
    public record Compatibility(
        String packageFormatVersion,
        String minimumEngineVersion,
        String maximumEngineVersion,
        String minimumDatabaseSchemaVersion,
        String maximumDatabaseSchemaVersion
    ) {
    }

    /**
     * manifest 绑定的单个真实文件。
     *
     * @param path 使用正斜杠的规范相对路径
     * @param size 真实文件字节数
     * @param sm3Digest 按真实文件字节计算的 SM3 摘要
     */
    public record FileEntry(
        String path,
        long size,
        String sm3Digest
    ) {
    }
}
