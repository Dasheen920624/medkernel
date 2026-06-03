package com.medkernel.engine.integration.fhir;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * FHIR 资源映射证据仓储。
 */
@Repository
public interface FhirResourceMappingRepository extends ListCrudRepository<FhirResourceMapping, Long> {

    Optional<FhirResourceMapping> findByTenantIdAndFhirVersionAndFhirResourceTypeAndFhirId(
        String tenantId, FhirVersion fhirVersion, String fhirResourceType, String fhirId);

    Optional<FhirResourceMapping> findByTenantIdAndCanonicalResourceIdAndFhirVersion(
        String tenantId, Long canonicalResourceId, FhirVersion fhirVersion);
}
