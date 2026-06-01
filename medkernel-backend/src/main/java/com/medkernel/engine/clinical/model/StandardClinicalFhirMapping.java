package com.medkernel.engine.clinical.model;

import com.medkernel.engine.context.CanonicalResourceType;

/**
 * 一个 SYS-01 标准临床对象到 FHIR R4 资源类型的映射定义。
 */
public record StandardClinicalFhirMapping(
    CanonicalResourceType canonicalType,
    StandardClinicalFhirResourceType fhirResourceType
) {}
