package com.medkernel.engine.clinical.model;

import java.util.List;

/**
 * 按患者聚合的标准临床对象关系库权威读取结果。
 */
public record StandardClinicalAuthorityBundle(
    String tenantId,
    String patientId,
    String authoritySource,
    ClinicalProjectionStatus projectionStatus,
    List<StandardClinicalFhirReference> fhirReferences
) {
    public StandardClinicalAuthorityBundle {
        fhirReferences = fhirReferences == null ? List.of() : List.copyOf(fhirReferences);
    }
}
