package com.medkernel.engine.knowledge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 来源文献版本（Source Version）仓储接口。
 *
 * <p>用于存储指南源文档的不同版本生命周期管理（如发表时间、内容指纹等），
 * 支撑知识资产引擎的多版本解析溯源追踪。
 */
@Repository
public interface SourceVersionRepository extends ListCrudRepository<SourceVersion, Long> {

    Optional<SourceVersion> findByTenantIdAndId(String tenantId, Long id);

    List<SourceVersion> findByTenantIdAndSourceDocumentIdOrderByPublishedAtDescIdDesc(String tenantId, Long sourceDocumentId);

    Optional<SourceVersion> findBySourceDocumentIdAndVersionNo(Long sourceDocumentId, String versionNo);

    Optional<SourceVersion> findBySourceDocumentIdAndContentHash(Long sourceDocumentId, String contentHash);
}
