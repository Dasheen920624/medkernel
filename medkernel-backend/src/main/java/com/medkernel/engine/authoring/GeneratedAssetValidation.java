package com.medkernel.engine.authoring;

import java.util.List;

import com.medkernel.engine.versioning.AssetDependencyDeclaration;

/**
 * 自动生成资产正文校验后的规范化结果。
 */
public record GeneratedAssetValidation(
    String canonicalContent,
    List<AssetDependencyDeclaration> dependencies
) {
    public GeneratedAssetValidation {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
