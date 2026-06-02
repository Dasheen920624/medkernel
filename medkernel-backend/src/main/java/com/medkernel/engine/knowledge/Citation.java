package com.medkernel.engine.knowledge;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 资产版本对来源片段的引用关系。
 *
 * <p>同一对 (asset_version_id, source_fragment_id, relation) 唯一；
 * weight 0-100 用于多引用时排序展示；start_offset/end_offset 用于精确定位片段内引用范围。
 */
@Table("citation")
public record Citation(
    @Id Long id,
    @Column("tenant_id") String tenantId,
    @Column("asset_version_id") Long assetVersionId,
    @Column("source_fragment_id") Long sourceFragmentId,
    @Column("relation") CitationRelation relation,
    @Column("weight") Integer weight,
    @Column("start_offset") Integer startOffset,
    @Column("end_offset") Integer endOffset,
    @Column("created_at") Instant createdAt,
    @Column("created_by") String createdBy
) {
}
