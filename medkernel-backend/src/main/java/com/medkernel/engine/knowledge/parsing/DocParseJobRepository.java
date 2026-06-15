package com.medkernel.engine.knowledge.parsing;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/** 文档解析 job 仓储（AIK-STD-02）。所有查询强制带 tenantId（强租户隔离）。 */
@Repository
public interface DocParseJobRepository extends ListCrudRepository<DocParseJob, Long> {

    Optional<DocParseJob> findByTenantIdAndJobCode(String tenantId, String jobCode);

    @Query("""
        SELECT * FROM mk_doc_parse_job
        WHERE tenant_id = :tenantId
        ORDER BY created_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<DocParseJob> pageByTenantId(String tenantId, int offset, int limit);
}
