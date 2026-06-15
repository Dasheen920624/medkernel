package com.medkernel.engine.knowledge.discovery;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;

/**
 * 受控来源检索命中投影（LLM-06，FR-1/3）。
 *
 * <p>由 {@link ControlledSourceSearchRepository} 跨表 JOIN
 * {@code source_fragment → source_version → source_document} 产出：携带引用锚点 + 源版本 + 源文档权威分级，
 * 供编排器产出带真实来源锚点的候选（核心 §7 来源可溯 / 铁律 #1 无源不出）。
 *
 * @param fragmentId 片段 ID
 * @param anchorPath 引用锚点路径（章节 / 条款 / 表格行）
 * @param anchorLabel 锚点标签（人读）
 * @param textExcerpt 片段正文摘录（候选载荷来源）
 * @param fragmentContentHash 片段内容指纹
 * @param sourceVersionId 来源版本 ID
 * @param versionNo 来源版本号
 * @param versionContentHash 来源版本内容指纹（存证快照用）
 * @param sourceDocumentId 来源文档 ID
 * @param sourceCode 来源文档业务编码
 * @param sourceTitle 来源文档标题
 * @param sourceType 来源类型
 * @param authorityLevel 来源权威分级（A–E）
 */
public record DiscoveryFragmentHit(
    Long fragmentId,
    String anchorPath,
    String anchorLabel,
    String textExcerpt,
    String fragmentContentHash,
    Long sourceVersionId,
    String versionNo,
    String versionContentHash,
    Long sourceDocumentId,
    String sourceCode,
    String sourceTitle,
    SourceType sourceType,
    SourceAuthorityLevel authorityLevel
) {
}
