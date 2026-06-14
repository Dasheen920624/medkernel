package com.medkernel.engine.llm.egress;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 出域字段白名单数据访问存储库（LLM-03）。
 */
@Repository
public interface ModelEgressWhitelistRepository extends CrudRepository<ModelEgressWhitelist, Long> {

    /**
     * 按租户与能力码唯一检索出域白名单。
     */
    Optional<ModelEgressWhitelist> findByTenantIdAndCapabilityCode(String tenantId, String capabilityCode);
}
