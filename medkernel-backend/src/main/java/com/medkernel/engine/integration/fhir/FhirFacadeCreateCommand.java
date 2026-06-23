package com.medkernel.engine.integration.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.medkernel.engine.context.canonical.ClinicalSetting;

/**
 * FHIR 运行门面 create 请求的内部命令。
 */
public record FhirFacadeCreateCommand(
    FhirVersion version,
    String resourceType,
    JsonNode resource,
    String adapterId,
    String timestamp,
    String signature,
    String sourceIp,
    String snapshotId,
    ClinicalSetting clinicalSetting
) {}
