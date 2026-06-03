package com.medkernel.engine.integration.fhir;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.TerminologyMappingPort;

/**
 * FHIR R5 与 MedKernel 标准临床资源之间的确定性映射器。
 */
@Component
public class FhirR5CanonicalMapper {

    private final FhirCanonicalMapperSupport support;

    public FhirR5CanonicalMapper(ObjectMapper json, TerminologyMappingPort terminology) {
        this.support = new FhirCanonicalMapperSupport(json, new FhirTerminologyMapper(terminology));
    }

    public FhirResourceMappingResult toR5(CanonicalResource canonical) {
        return support.mapCanonical(canonical, FhirVersion.R5, null);
    }

    public FhirResourceMappingResult toR5(CanonicalResource canonical, String requestedResourceType) {
        return support.mapCanonical(canonical, FhirVersion.R5, requestedResourceType);
    }

    public CanonicalResourceMappingResult fromR5(FhirCanonicalMappingRequest request) {
        return support.mapInbound(request, FhirVersion.R5);
    }
}
