package com.medkernel.engine.knowledge;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 创建知识版本引用请求。
 *
 * @param assetVersionId 知识资产版本 ID
 * @param sourceFragmentId 来源片段 ID
 * @param relation 引用关系
 * @param weight 多来源排序权重，范围 0-100
 * @param startOffset 片段内起始偏移，可与结束偏移同时省略
 * @param endOffset 片段内结束偏移，可与起始偏移同时省略
 */
public record CitationCreateRequest(
    @NotNull Long assetVersionId,
    @NotNull Long sourceFragmentId,
    @NotNull CitationRelation relation,
    @NotNull @Min(0) @Max(100) Integer weight,
    @PositiveOrZero Integer startOffset,
    @PositiveOrZero Integer endOffset
) {}
