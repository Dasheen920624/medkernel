package com.medkernel.engine.llm.egress;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jdbc.repository.query.Query;
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

    /**
     * 统计当前租户的出域责任确认记录数。
     */
    long countByTenantId(String tenantId);

    /**
     * 分页检索当前租户最近的出域责任确认记录，供审计回看与实施复核使用。
     */
    @Query("""
        SELECT * FROM mk_llm_egress_confirmation
        WHERE tenant_id = :tenantId
        ORDER BY confirmed_at DESC, id DESC
        OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY
        """)
    List<ModelEgressConfirmation> pageByTenantId(String tenantId, int offset, int limit);
}
