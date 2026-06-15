package com.medkernel.engine.knowledge.discovery;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.stereotype.Repository;

import com.medkernel.engine.knowledge.SourceFragment;

/**
 * 受控来源检索仓储（LLM-06，FR-1 受控源 / FR-3 来源核验）。
 *
 * <p>仅检索已登记的受控来源（{@code source_document → source_version → source_fragment}，KNOW-01 注册表），
 * <b>不开放全网</b>；强租户隔离。按关键词匹配片段正文，返回携带引用锚点 + 源版本 + 权威分级的命中投影，
 * 按权威分级（A→E）排序使高权威来源优先。
 */
@Repository
public interface ControlledSourceSearchRepository
    extends org.springframework.data.repository.Repository<SourceFragment, Long> {

    @Query("""
        SELECT f.id AS fragment_id,
               f.anchor_path AS anchor_path,
               f.anchor_label AS anchor_label,
               f.text_excerpt AS text_excerpt,
               f.content_hash AS fragment_content_hash,
               v.id AS source_version_id,
               v.version_no AS version_no,
               v.content_hash AS version_content_hash,
               d.id AS source_document_id,
               d.source_code AS source_code,
               d.title AS source_title,
               d.source_type AS source_type,
               d.authority_level AS authority_level
        FROM source_fragment f
        JOIN source_version v ON v.id = f.source_version_id
        JOIN source_document d ON d.id = v.source_document_id
        WHERE f.tenant_id = :tenantId
          AND LOWER(f.text_excerpt) LIKE :keyword
        ORDER BY d.authority_level ASC, f.id ASC
        OFFSET 0 ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<DiscoveryFragmentHit> searchControlledFragments(String tenantId, String keyword, int limit);
}
