package com.medkernel.engine.llm.egress;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 出域审批记录数据访问存储库（LLM-03）。
 */
@Repository
public interface ModelEgressApprovalRepository extends CrudRepository<ModelEgressApproval, Long> {

    /**
     * 检索指定租户+能力码+载荷 hash 下、指定状态的最近一条审批记录。
     */
    Optional<ModelEgressApproval> findFirstByTenantIdAndCapabilityCodeAndPayloadHashAndStatusOrderByIdDesc(
        String tenantId, String capabilityCode, String payloadHash, String status);
}
