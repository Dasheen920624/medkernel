package com.medkernel.engine.context;

/**
 * 字段目录统一资产常量。
 *
 * <p>字段目录只有一套维护与发布身份：平台/机构工作区固化为该资产，规则候选也必须
 * 依赖该资产，避免生成器、校验器和机构生效版本各自发明字段目录编码。
 */
public final class ContextFieldCatalogAssets {

    public static final String CLINICAL_CONTEXT_IDENTITY = "FIELD.CATALOG.CLINICAL_CONTEXT";

    private ContextFieldCatalogAssets() {
    }
}
