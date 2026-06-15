package com.medkernel.engine.knowledge;

/**
 * 解析后的受控源 FK + 锚点（AIK-STD-13 PR4 物化）。
 *
 * <p>把信封串源引用回查为受控源外键，供候选物化构造标准版本请求。
 */
public record ResolvedSource(Long sourceDocumentId, Long sourceVersionId, String anchorPath) {
}
