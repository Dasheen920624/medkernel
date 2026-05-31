package com.medkernel.engine.knowledge;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 知识资产源引用关联关系（Citation）仓储接口。
 *
 * <p>用于存储规则、路径等计算性医学知识资产，与其对应的物理指南、源法规文献片段之间的映射，
 * 支撑 GA-ENG-KNOW-01 知识资产引擎的指南源可信度评分与可追溯溯源链路。
 */
@Repository
public interface CitationRepository extends ListCrudRepository<Citation, Long> {

    List<Citation> findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc(String tenantId, Long assetVersionId);

    @Query("""
        SELECT * FROM citation
        WHERE tenant_id = :tenantId
        ORDER BY id ASC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<Citation> pageByTenantId(String tenantId, int offset, int limit);
}
