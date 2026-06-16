package com.medkernel.compliance.masking;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * SYS-06 脱敏规则仓储。
 */
@Repository
public interface MaskingRuleRepository extends ListCrudRepository<MaskingRule, Long> {

    Optional<MaskingRule> findByTenantIdAndResourceTypeAndFieldNameAndScenarioCode(
        String tenantId, String resourceType, String fieldName, String scenarioCode);

    @Query("SELECT * FROM mk_compliance_masking_rule "
        + "WHERE tenant_id = :tenantId AND resource_type = :resourceType "
        + "AND field_name = :fieldName AND scenario_code = :scenarioCode AND status = 'ACTIVE'")
    Optional<MaskingRule> findActiveRule(
        @Param("tenantId") String tenantId,
        @Param("resourceType") String resourceType,
        @Param("fieldName") String fieldName,
        @Param("scenarioCode") String scenarioCode);

    @Query("SELECT COUNT(*) FROM mk_compliance_masking_rule WHERE tenant_id = :tenantId "
        + "AND (:resourceType IS NULL OR resource_type = :resourceType) "
        + "AND (:fieldName IS NULL OR field_name = :fieldName)")
    long countRules(
        @Param("tenantId") String tenantId,
        @Param("resourceType") String resourceType,
        @Param("fieldName") String fieldName);

    @Query("SELECT * FROM mk_compliance_masking_rule WHERE tenant_id = :tenantId "
        + "AND (:resourceType IS NULL OR resource_type = :resourceType) "
        + "AND (:fieldName IS NULL OR field_name = :fieldName) "
        + "ORDER BY resource_type ASC, field_name ASC, scenario_code ASC, id ASC "
        + "OFFSET :offset ROWS FETCH NEXT :limit ROWS ONLY")
    List<MaskingRule> pageRules(
        @Param("tenantId") String tenantId,
        @Param("resourceType") String resourceType,
        @Param("fieldName") String fieldName,
        @Param("offset") int offset,
        @Param("limit") int limit);
}
