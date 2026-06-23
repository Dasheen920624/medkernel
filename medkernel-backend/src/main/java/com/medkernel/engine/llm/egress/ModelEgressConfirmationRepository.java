package com.medkernel.engine.llm.egress;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * 出域责任确认记录数据访问存储库。
 */
@Repository
public interface ModelEgressConfirmationRepository extends CrudRepository<ModelEgressConfirmation, Long> {

    /**
     * 检索指定租户、能力码与脱敏后载荷摘要的最近一条责任确认。
     */
    Optional<ModelEgressConfirmation> findFirstByTenantIdAndCapabilityCodeAndPayloadHashOrderByIdDesc(
        String tenantId, String capabilityCode, String payloadHash);
}
