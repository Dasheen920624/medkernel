package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识资产源片段（Source Fragment）仓储接口。
 *
 * <p>处理经过医学指南或法规解析后的原子条文片段（锚点碎片）的读取与检索，
 * 支撑知识资产引擎的引用锚点定位及可信度评估。
 */
@Repository
public interface SourceFragmentRepository extends ListCrudRepository<SourceFragment, Long> {

    Optional<SourceFragment> findByTenantIdAndId(String tenantId, Long id);

    List<SourceFragment> findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc(String tenantId, Long sourceVersionId);

    Optional<SourceFragment> findBySourceVersionIdAndAnchorPath(Long sourceVersionId, String anchorPath);

    Optional<SourceFragment> findBySourceVersionIdAndContentHash(Long sourceVersionId, String contentHash);
}
