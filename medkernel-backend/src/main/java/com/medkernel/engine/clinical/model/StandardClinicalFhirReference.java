package com.medkernel.engine.clinical.model;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * 标准临床对象可追踪的 FHIR R4 引用锚点。
 */
public record StandardClinicalFhirReference(
    CanonicalResourceType canonicalType,
    String localId,
    String fhirVersion,
    String resourceType,
    String resourceId,
    String mappingStatus
) {}
