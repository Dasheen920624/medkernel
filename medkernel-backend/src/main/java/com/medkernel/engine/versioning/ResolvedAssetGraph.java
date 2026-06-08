package com.medkernel.engine.versioning;

import java.util.List;

/**
 * 单次解析的根资产、依赖资产与一致性 epoch。
 */
public record ResolvedAssetGraph(
    ResolvedAssetVersion root,
    List<ResolvedAssetDependency> dependencies,
    List<ResolutionEpochBinding> epochBindings,
    String resolutionEpoch
) {
    public ResolvedAssetGraph {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        epochBindings = epochBindings == null ? List.of() : List.copyOf(epochBindings);
    }
}
