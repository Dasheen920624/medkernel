package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
 * R4/R5 共用的确定性映射实现，避免双版本门面复制出两套临床模型。
 */
final class FhirCanonicalMapperSupport {

    private static final BigDecimal FULL_RATE = new BigDecimal("1.0000");

    private final ObjectMapper json;
    private final FhirTerminologyMapper terminology;

    FhirCanonicalMapperSupport(ObjectMapper json, FhirTerminologyMapper terminology) {
        this.json = json;
        this.terminology = terminology;
    }

    FhirResourceMappingResult mapPatient(CanonicalResource canonical, String profileUrl) {
        CanonicalPatient model = readPayload(canonical.resourcePayloadJson(), CanonicalPatient.class);
        ObjectNode patient = json.createObjectNode();
        patient.put("resourceType", StandardClinicalFhirResourceType.PATIENT.resourceType());
        putIfPresent(patient, "id", canonical.resourceId());

        ObjectNode meta = patient.putObject("meta");
        meta.put("source", "canonical_resource/" + canonical.resourceId());
        if (profileUrl != null && !profileUrl.isBlank()) {
            meta.set("profile", json.createArrayNode().add(profileUrl));
        }

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
        return new FhirResourceMappingResult(patient, List.of(), FULL_RATE);
    }

    CanonicalResourceMappingResult mapObservation(FhirCanonicalMappingRequest request, FhirVersion version) {
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

        String sourceSystem = "FHIR_" + version.name();
        String sourceRecordId = "Observation/" + observationId;
        String mappedVersion = sourceSystem + ":Observation";
        FhirCodingMappingResult codingMapping = terminology.mapObservationCode(
            request.tenantId(), observationId, codeSystem, code, display, sourceRecordId, mappedVersion);
        QualityStatus qualityStatus = codingMapping.issues().isEmpty() ? QualityStatus.VALID : QualityStatus.PARTIAL;

        CanonicalObservation payload = new CanonicalObservation(
            observationId,
            code,
            display,
            quantity.path("value").isNumber() ? quantity.path("value").decimalValue() : null,
            null,
            firstNonBlank(text(quantity.path("unit")), text(quantity.path("code"))),
            null,
            null,
            sourceSystem,
            sourceRecordId,
            mappedVersion,
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
            sourceSystem,
            sourceRecordId,
            mappedVersion,
            eventTime,
            request.receivedAt(),
            qualityStatus,
            request.seqNo(),
            request.traceId());
        return new CanonicalResourceMappingResult(canonical, codingMapping.issues(), codingMapping.mappingRate());
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

    static String text(JsonNode node) {
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
