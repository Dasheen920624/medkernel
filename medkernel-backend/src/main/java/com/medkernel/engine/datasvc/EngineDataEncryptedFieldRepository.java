package com.medkernel.engine.datasvc;

import java.util.List;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 引擎数据服务层 D3/D4 加密字段仓储。所有查询必须带租户过滤。
 */
@Repository
public interface EngineDataEncryptedFieldRepository extends ListCrudRepository<EngineDataEncryptedField, Long> {

    List<EngineDataEncryptedField> findByTenantIdAndScopeKeyOrderByIdAsc(String tenantId, String scopeKey);

    @Query("""
        SELECT * FROM mk_engine_data_encrypted_field
        WHERE tenant_id = :tenantId AND search_hash = :searchHash
        ORDER BY id ASC
        """)
    List<EngineDataEncryptedField> findByTenantIdAndSearchHash(String tenantId, String searchHash);
}
