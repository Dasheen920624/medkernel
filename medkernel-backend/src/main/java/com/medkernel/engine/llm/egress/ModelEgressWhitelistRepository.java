package com.medkernel.engine.llm.egress;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 外调允许字段数据访问存储库（LLM-03）。
 */
@Repository
public interface ModelEgressWhitelistRepository extends CrudRepository<ModelEgressWhitelist, Long> {

    /**
     * 按机构与能力码唯一检索外调允许范围。
     */
    Optional<ModelEgressWhitelist> findByTenantIdAndCapabilityCode(String tenantId, String capabilityCode);
}
