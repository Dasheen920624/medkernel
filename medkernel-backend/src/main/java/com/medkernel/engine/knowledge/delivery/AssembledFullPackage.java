package com.medkernel.engine.knowledge.delivery;

import java.util.List;

/** 已完成 13 类、依赖、状态与安全校验的完整包内容文件集合。 */
public record AssembledFullPackage(
    String platformReleaseIdentity,
    List<PortableAssetFile> files
) {
    public AssembledFullPackage {
        files = files == null ? null : List.copyOf(files);
    }
}
