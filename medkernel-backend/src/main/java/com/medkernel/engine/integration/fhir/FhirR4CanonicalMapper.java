package com.medkernel.engine.integration.fhir;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceType;
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
        if (canonical.resourceType() == CanonicalResourceType.PATIENT) {
            return support.mapPatient(canonical, "http://hl7.org/fhir/StructureDefinition/Patient");
        }
        throw new IllegalArgumentException("OPT-01 PR2 暂未开放该标准资源的 FHIR R4 出站映射: "
            + canonical.resourceType());
    }

    public CanonicalResourceMappingResult fromR4(FhirCanonicalMappingRequest request) {
        JsonNode resource = request.resource();
        String resourceType = FhirCanonicalMapperSupport.text(resource.path("resourceType"));
        if (!"Observation".equals(resourceType)) {
            throw new IllegalArgumentException("OPT-01 PR2 暂未开放该 FHIR R4 入站资源映射: " + resourceType);
        }
        return support.mapObservation(request, FhirVersion.R4);
    }
}
