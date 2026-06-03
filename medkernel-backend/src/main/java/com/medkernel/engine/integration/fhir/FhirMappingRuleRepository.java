package com.medkernel.engine.integration.fhir;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * FHIR 字段映射规则仓储。
 */
@Repository
public interface FhirMappingRuleRepository extends ListCrudRepository<FhirMappingRule, Long> {

    List<FhirMappingRule> findByTenantIdAndFhirVersionAndFhirResourceTypeAndStatusOrderByRuleVersionDesc(
        String tenantId, FhirVersion fhirVersion, String fhirResourceType, FhirMappingStatus status);
}
