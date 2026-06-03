package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.clinical.model.StandardClinicalFhirResourceType;
import com.medkernel.engine.context.CanonicalResource;
import com.medkernel.engine.context.CanonicalResourceType;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;

/**
 * FHIR R4 与 MedKernel 标准临床资源之间的确定性映射器。
 *
 * <p>PR1 只落地患者出站与 Observation 入站的可测最小闭环，不伪造缺失字段；
 * 术语未标准化时返回 OperationOutcome 风格 warning，后续由 TERM-01 字典治理补齐。
 */
@Component
public class FhirR4CanonicalMapper {

    private static final String SOURCE_SYSTEM = "FHIR_R4";
    private static final String STANDARD_CODE_SYSTEM_LOINC = "http://loinc.org";
    private static final BigDecimal FULL_RATE = new BigDecimal("1.0000");
    private static final BigDecimal PARTIAL_RATE = new BigDecimal("0.9000");

    private final ObjectMapper json;

    public FhirR4CanonicalMapper(ObjectMapper json) {
        this.json = json;
    }

    public FhirResourceMappingResult toR4(CanonicalResource canonical) {
        if (canonical.resourceType() == CanonicalResourceType.PATIENT) {
            return new FhirResourceMappingResult(mapPatient(canonical), List.of(), FULL_RATE);
        }
        throw new IllegalArgumentException("OPT-01 PR1 暂未开放该标准资源的 FHIR R4 出站映射: "
            + canonical.resourceType());
    }

    public CanonicalResourceMappingResult fromR4(FhirCanonicalMappingRequest request) {
        JsonNode resource = request.resource();
        String resourceType = text(resource.path("resourceType"));
        if (!"Observation".equals(resourceType)) {
            throw new IllegalArgumentException("OPT-01 PR1 暂未开放该 FHIR R4 入站资源映射: " + resourceType);
        }
        return mapObservation(request);
    }

    private JsonNode mapPatient(CanonicalResource canonical) {
        CanonicalPatient model = readPayload(canonical.resourcePayloadJson(), CanonicalPatient.class);
        ObjectNode patient = json.createObjectNode();
        patient.put("resourceType", StandardClinicalFhirResourceType.PATIENT.resourceType());
        putIfPresent(patient, "id", canonical.resourceId());

        ObjectNode meta = patient.putObject("meta");
        meta.put("source", "canonical_resource/" + canonical.resourceId());

        if (model.mpi() != null && !model.mpi().isBlank()) {
            ObjectNode identifier = json.createObjectNode();
            identifier.put("system", "urn:medkernel:mpi");
            identifier.put("value", model.mpi());
            patient.set("identifier", json.createArrayNode().add(identifier));
        }

        if (model.name() != null && !model.name().isBlank()) {
            ObjectNode humanName = json.createObjectNode();
            humanName.put("text", model.name());
            patient.set("name", json.createArrayNode().add(humanName));
        }

        putIfPresent(patient, "gender", model.gender());
        putIfPresent(patient, "birthDate", model.birthDate() == null ? null : model.birthDate().toString());
        return patient;
    }

    private CanonicalResourceMappingResult mapObservation(FhirCanonicalMappingRequest request) {
        JsonNode resource = request.resource();
        String observationId = text(resource.path("id"));
        if (observationId.isBlank()) {
            throw new IllegalArgumentException("FHIR Observation.id 不能为空");
        }

        JsonNode coding = firstCoding(resource.path("code").path("coding"));
        String codeSystem = text(coding.path("system"));
        String code = firstNonBlank(text(coding.path("code")), text(resource.path("code").path("text")));
        String display = firstNonBlank(text(coding.path("display")), text(resource.path("code").path("text")), code);
        if (code.isBlank()) {
            throw new IllegalArgumentException("FHIR Observation.code 不能为空");
        }
        JsonNode quantity = resource.path("valueQuantity");
        Instant eventTime = parseInstantOrNull(text(resource.path("effectiveDateTime")));

        List<FhirOperationOutcomeIssue> issues = codeSystem.isBlank() || STANDARD_CODE_SYSTEM_LOINC.equals(codeSystem)
            ? List.of()
            : List.of(new FhirOperationOutcomeIssue(
                "warning",
                "not-supported",
                "TERM-01 尚未标准化 FHIR Observation 编码系统: " + codeSystem));
        QualityStatus qualityStatus = issues.isEmpty() ? QualityStatus.VALID : QualityStatus.PARTIAL;
        BigDecimal mappingRate = issues.isEmpty() ? FULL_RATE : PARTIAL_RATE;

        CanonicalObservation payload = new CanonicalObservation(
            observationId,
            code,
            display,
            quantity.path("value").isNumber() ? quantity.path("value").decimalValue() : null,
            null,
            firstNonBlank(text(quantity.path("unit")), text(quantity.path("code"))),
            null,
            null,
            SOURCE_SYSTEM,
            "Observation/" + observationId,
            "FHIR_R4:Observation",
            eventTime,
            request.receivedAt(),
            qualityStatus);

        CanonicalResource canonical = new CanonicalResource(
            null,
            observationId,
            request.snapshotId(),
            request.tenantId(),
            CanonicalResourceType.OBSERVATION,
            writePayload(payload),
            SOURCE_SYSTEM,
            "Observation/" + observationId,
            "FHIR_R4:Observation",
            eventTime,
            request.receivedAt(),
            qualityStatus,
            request.seqNo(),
            request.traceId());
        return new CanonicalResourceMappingResult(canonical, issues, mappingRate);
    }

    private <T> T readPayload(String payloadJson, Class<T> type) {
        try {
            return json.readValue(payloadJson, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("标准临床资源 payload 不是合法 JSON", ex);
        }
    }

    private String writePayload(CanonicalObservation payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("标准临床资源 payload 序列化失败", ex);
        }
    }

    private static JsonNode firstCoding(JsonNode codings) {
        if (codings instanceof ArrayNode array && !array.isEmpty()) {
            return array.get(0);
        }
        throw new IllegalArgumentException("FHIR Observation.code.coding 不能为空");
    }

    private static Instant parseInstantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static void putIfPresent(ObjectNode target, String field, String value) {
        if (value != null && !value.isBlank()) {
            target.put(field, value);
        }
    }
}
