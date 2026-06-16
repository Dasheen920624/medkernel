package com.medkernel.engine.llm;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * prompt/tool/model 版本包数据访问存储库。
 */
@Repository
public interface ModelVersionBundleRepository extends CrudRepository<ModelVersionBundle, Long> {

    Optional<ModelVersionBundle> findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
        String tenantId, String capabilityCode, String status);

    List<ModelVersionBundle> findByTenantIdAndCapabilityCodeOrderByIdDesc(String tenantId, String capabilityCode);

    @Modifying
    @Query("""
        UPDATE mk_llm_model_version_bundle
           SET status = 'RETIRED',
               retired_at = :retiredAt,
               updated_at = :retiredAt,
               updated_by = :actor
         WHERE tenant_id = :tenantId
           AND capability_code = :capabilityCode
           AND status = 'ACTIVE'
        """)
    int retireActive(String tenantId, String capabilityCode, String actor, Instant retiredAt);

    @Modifying
    @Query("""
        UPDATE mk_llm_model_version_bundle
           SET status = 'ACTIVE',
               retired_at = NULL,
               effective_at = :effectiveAt,
               updated_at = :effectiveAt,
               updated_by = :actor
         WHERE id = :id
           AND tenant_id = :tenantId
           AND capability_code = :capabilityCode
        """)
    int activateBundle(Long id, String tenantId, String capabilityCode, String actor, Instant effectiveAt);
}
