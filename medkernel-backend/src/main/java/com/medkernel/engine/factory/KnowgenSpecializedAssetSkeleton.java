package com.medkernel.engine.factory;

import java.util.List;

import com.medkernel.engine.versioning.VersionedAssetType;

/**
 * KNOWGEN 专用资产类型代码骨架。
 *
 * <p>只声明生成、校验和计算所需结构，不内置真实医学内容。
 *
 * @param cardCode KNOWGEN 卡号
 * @param displayName 中文名称
 * @param assetTypes 承载的统一资产类型
 * @param requiredPayloadFields 专用 payload 必备字段
 * @param codeCapabilities 已具备的代码能力
 * @param b0Executable 是否无模型可运行
 * @param modelRequired 是否依赖模型
 * @param clinicalContentSeeded 是否预填医学内容
 */
public record KnowgenSpecializedAssetSkeleton(
    String cardCode,
    String displayName,
    List<VersionedAssetType> assetTypes,
    List<String> requiredPayloadFields,
    List<String> codeCapabilities,
    boolean b0Executable,
    boolean modelRequired,
    boolean clinicalContentSeeded
) {
    public KnowgenSpecializedAssetSkeleton {
        assetTypes = List.copyOf(assetTypes);
        requiredPayloadFields = List.copyOf(requiredPayloadFields);
        codeCapabilities = List.copyOf(codeCapabilities);
    }
}
