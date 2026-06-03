package com.medkernel.engine.integration.fhir;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.TerminologyMappingPort;

/**
 * FHIR R4 与 MedKernel 标准临床资源之间的确定性映射器。
 *
 * <p>映射只读写 {@link com.medkernel.engine.context.CanonicalResource}，
 * 编码状态经 TERM-01 端口评估；未映射项返回 OperationOutcome warning，
 * 不伪造缺失字段。
 */
@Component
public class FhirR4CanonicalMapper {

    private final FhirCanonicalMapperSupport support;

    public FhirR4CanonicalMapper(ObjectMapper json, TerminologyMappingPort terminology) {
        this.support = new FhirCanonicalMapperSupport(json, new FhirTerminologyMapper(terminology));
    }

    public FhirResourceMappingResult toR4(CanonicalResource canonical) {
        return support.mapCanonical(canonical, FhirVersion.R4, null);
    }

    public FhirResourceMappingResult toR4(CanonicalResource canonical, String requestedResourceType) {
        return support.mapCanonical(canonical, FhirVersion.R4, requestedResourceType);
    }

    public CanonicalResourceMappingResult fromR4(FhirCanonicalMappingRequest request) {
        return support.mapInbound(request, FhirVersion.R4);
    }
}
