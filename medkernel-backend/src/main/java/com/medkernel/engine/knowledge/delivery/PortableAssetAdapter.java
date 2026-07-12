package com.medkernel.engine.knowledge.delivery;

import com.medkernel.engine.versioning.VersionedAssetType;

/** 单个 {@link VersionedAssetType} 的 `.mkp` 规范导出、校验和物化适配器。 */
public interface PortableAssetAdapter {

    /** 返回唯一负责的资产类型。 */
    VersionedAssetType assetType();

    /** 从完整稳定输入生成规范资产文件。 */
    PortableAssetFile export(PortableAssetDocument.ExportInput input);

    /** 从真实规范字节回读并完成类型、许可、依赖、测试和自包含校验。 */
    PortableAssetDocument validate(byte[] bytes);

    /** 校验真实字节后交给统一事务物化端口。 */
    void materialize(byte[] bytes, PortableAssetMaterializationTarget target);
}
