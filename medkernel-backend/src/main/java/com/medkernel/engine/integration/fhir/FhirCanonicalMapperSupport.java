package com.medkernel.engine.integration.fhir;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import com.medkernel.engine.context.canonical.CanonicalCarePlan;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalDiagnosticReport;
import com.medkernel.engine.context.canonical.CanonicalDocument;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalMedication;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.context.canonical.CanonicalProcedure;

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

    FhirResourceMappingResult mapCanonical(CanonicalResource canonical,
                                           FhirVersion version,
                                           String requestedResourceType) {
        String requested = firstNonBlank(requestedResourceType);
        return switch (canonical.resourceType()) {
            case PATIENT -> mapPatient(canonical, patientProfile(version));
            case ENCOUNTER -> mapEncounter(canonical);
            case CONDITION -> mapCondition(canonical);
            case OBSERVATION -> mapObservationOutbound(canonical);
            case MEDICATION -> mapMedication(canonical);
            case PROCEDURE -> "ServiceRequest".equals(requested)
                ? mapServiceRequest(canonical)
                : mapProcedure(canonical);
            case CARE_PLAN -> mapCarePlan(canonical);
            case DIAGNOSTIC_REPORT -> mapDiagnosticReport(canonical);
            case DOCUMENT -> mapDocumentReference(canonical);
            case NURSING_ASSESSMENT, FOLLOW_UP, CLAIM -> throw new IllegalArgumentException(
                "OPT-01 PR4 不开放该标准资源的 FHIR 出站映射: " + canonical.resourceType());
        };
    }

    FhirResourceMappingResult mapPatient(CanonicalResource canonical, String profileUrl) {
        CanonicalPatient model = readPayload(canonical.resourcePayloadJson(), CanonicalPatient.class);
        ObjectNode patient = baseResource(StandardClinicalFhirResourceType.PATIENT.resourceType(), canonical, profileUrl);

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
        return mapped(patient);
    }

    CanonicalResourceMappingResult mapInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String resourceType = text(resource.path("resourceType"));
        return switch (resourceType) {
            case "Patient" -> mapPatientInbound(request, version);
            case "Encounter" -> mapEncounterInbound(request, version);
            case "Condition" -> mapConditionInbound(request, version);
            case "Observation" -> mapObservation(request, version);
            case "Medication" -> mapMedicationInbound(request, version);
            case "Procedure" -> mapProcedureInbound(request, version);
            case "CarePlan" -> mapCarePlanInbound(request, version);
            case "DiagnosticReport" -> mapDiagnosticReportInbound(request, version);
            case "DocumentReference" -> mapDocumentInbound(request, version);
            case "ServiceRequest" -> throw new IllegalArgumentException(
                "FHIR ServiceRequest create 必须走医师确认任务，不自动写申请单");
            default -> throw new IllegalArgumentException("OPT-01 PR4 未开放该 FHIR 入站资源映射: " + resourceType);
        };
    }

    CanonicalResourceMappingResult mapObservation(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String observationId = requiredId(resource, "Observation");
        CodingValue coding = codingValue(resource.path("code"), "Observation.code");
        JsonNode quantity = resource.path("valueQuantity");
        Instant eventTime = parseInstantOrNull(text(resource.path("effectiveDateTime")));

        String sourceSystem = sourceSystem(version);
        String sourceRecordId = "Observation/" + observationId;
        String mappedVersion = mappedVersion(version, "Observation");
        FhirCodingMappingResult codingMapping = terminology.mapObservationCode(
            request.tenantId(), observationId, coding.system(), coding.code(), coding.display(),
            sourceRecordId, mappedVersion);
        QualityStatus qualityStatus = quality(codingMapping);

        CanonicalObservation payload = new CanonicalObservation(
            observationId,
            coding.code(),
            coding.display(),
            quantity.path("value").isNumber() ? quantity.path("value").decimalValue() : null,
            quantity.path("value").isNumber() ? null : firstNonBlank(text(resource.path("valueString"))),
            firstNonBlank(text(quantity.path("unit")), text(quantity.path("code"))),
            null,
            null,
            sourceSystem,
            sourceRecordId,
            mappedVersion,
            eventTime,
            request.receivedAt(),
            qualityStatus);

        return result(request, CanonicalResourceType.OBSERVATION, observationId, payload,
            eventTime, codingMapping.issues(), codingMapping.mappingRate(), qualityStatus, sourceRecordId, mappedVersion);
    }

    private FhirResourceMappingResult mapEncounter(CanonicalResource canonical) {
        CanonicalEncounter model = readPayload(canonical.resourcePayloadJson(), CanonicalEncounter.class);
        ObjectNode resource = baseResource("Encounter", canonical, null);
        putIfPresent(resource, "status", model.dischargeTime() == null ? "in-progress" : "finished");
        if (model.encounterType() != null && !model.encounterType().isBlank()) {
            ObjectNode clazz = resource.putObject("class");
            clazz.put("code", model.encounterType());
        }
        ObjectNode period = json.createObjectNode();
        putIfPresent(period, "start", instant(model.admissionTime()));
        putIfPresent(period, "end", instant(model.dischargeTime()));
        if (!period.isEmpty()) {
            resource.set("period", period);
        }
        setIdentifierObject(resource, "serviceProvider", model.departmentId());
        if (model.attendingDoctorId() != null && !model.attendingDoctorId().isBlank()) {
            ObjectNode participant = json.createObjectNode();
            setIdentifierObject(participant, "individual", model.attendingDoctorId());
            resource.set("participant", json.createArrayNode().add(participant));
        }
        if (model.bedId() != null && !model.bedId().isBlank()) {
            ObjectNode location = json.createObjectNode();
            setIdentifierObject(location, "location", model.bedId());
            resource.set("location", json.createArrayNode().add(location));
        }
        return mapped(resource);
    }

    private FhirResourceMappingResult mapCondition(CanonicalResource canonical) {
        CanonicalCondition model = readPayload(canonical.resourcePayloadJson(), CanonicalCondition.class);
        ObjectNode resource = baseResource("Condition", canonical, null);
        resource.set("code", codeable(model.codeSystem(), model.code(), model.displayName()));
        putIfPresent(resource, "onsetDateTime", instant(model.onsetTime()));
        if (model.stage() != null && !model.stage().isBlank()) {
            resource.set("stage", json.createArrayNode().add(json.createObjectNode()
                .set("summary", textCodeable(model.stage()))));
        }
        if (model.severity() != null && !model.severity().isBlank()) {
            resource.set("severity", textCodeable(model.severity()));
        }
        return mapped(resource);
    }

    private FhirResourceMappingResult mapObservationOutbound(CanonicalResource canonical) {
        CanonicalObservation model = readPayload(canonical.resourcePayloadJson(), CanonicalObservation.class);
        ObjectNode resource = baseResource("Observation", canonical, null);
        resource.put("status", "final");
        resource.set("code", codeable("", model.code(), model.displayName()));
        putIfPresent(resource, "effectiveDateTime", instant(model.eventTime()));
        if (model.valueNumeric() != null) {
            ObjectNode quantity = resource.putObject("valueQuantity");
            quantity.put("value", model.valueNumeric());
            putIfPresent(quantity, "unit", model.unit());
        } else {
            putIfPresent(resource, "valueString", model.valueString());
        }
        return mapped(resource);
    }

    private FhirResourceMappingResult mapMedication(CanonicalResource canonical) {
        CanonicalMedication model = readPayload(canonical.resourcePayloadJson(), CanonicalMedication.class);
        ObjectNode resource = baseResource("Medication", canonical, null);
        resource.set("code", codeable("", model.code(), model.displayName()));
        return mapped(resource);
    }

    private FhirResourceMappingResult mapProcedure(CanonicalResource canonical) {
        CanonicalProcedure model = readPayload(canonical.resourcePayloadJson(), CanonicalProcedure.class);
        ObjectNode resource = baseResource("Procedure", canonical, null);
        resource.put("status", model.performedAt() == null ? "preparation" : "completed");
        resource.set("code", codeable("", model.code(), model.displayName()));
        putIfPresent(resource, "performedDateTime", instant(model.performedAt()));
        setIdentifierObject(resource, "performer", model.surgeonId());
        return mapped(resource);
    }

    private FhirResourceMappingResult mapServiceRequest(CanonicalResource canonical) {
        CanonicalProcedure model = readPayload(canonical.resourcePayloadJson(), CanonicalProcedure.class);
        ObjectNode resource = baseResource("ServiceRequest", canonical, null);
        resource.put("status", "active");
        resource.put("intent", "order");
        resource.set("code", codeable("", model.code(), model.displayName()));
        putIfPresent(resource, "occurrenceDateTime", instant(model.performedAt()));
        setIdentifierObject(resource, "requester", model.surgeonId());
        return mapped(resource);
    }

    private FhirResourceMappingResult mapCarePlan(CanonicalResource canonical) {
        CanonicalCarePlan model = readPayload(canonical.resourcePayloadJson(), CanonicalCarePlan.class);
        ObjectNode resource = baseResource("CarePlan", canonical, null);
        resource.put("status", "active");
        resource.put("intent", "plan");
        resource.set("instantiatesCanonical", json.createArrayNode().add(model.pathwayId()));
        if (model.currentNodeId() != null && !model.currentNodeId().isBlank()) {
            ObjectNode activity = json.createObjectNode();
            activity.set("detail", json.createObjectNode().set("code", textCodeable(model.currentNodeId())));
            resource.set("activity", json.createArrayNode().add(activity));
        }
        if (model.varianceCode() != null && !model.varianceCode().isBlank()) {
            resource.set("note", json.createArrayNode().add(json.createObjectNode().put("text", model.varianceCode())));
        }
        putIfPresent(resource, "created", instant(model.eventTime()));
        return mapped(resource);
    }

    private FhirResourceMappingResult mapDiagnosticReport(CanonicalResource canonical) {
        CanonicalDiagnosticReport model = readPayload(canonical.resourcePayloadJson(), CanonicalDiagnosticReport.class);
        ObjectNode resource = baseResource("DiagnosticReport", canonical, null);
        resource.put("status", "final");
        resource.set("code", textCodeable(model.reportType()));
        putIfPresent(resource, "conclusion", model.conclusion());
        putIfPresent(resource, "effectiveDateTime", instant(firstInstant(model.eventTime(), model.signedAt())));
        if (model.keyFindings() != null && !model.keyFindings().isEmpty()) {
            ArrayNode notes = resource.putArray("note");
            model.keyFindings().stream()
                .filter(value -> value != null && !value.isBlank())
                .forEach(value -> notes.add(json.createObjectNode().put("text", value)));
        }
        if (model.signedBy() != null && !model.signedBy().isBlank()) {
            ObjectNode interpreter = json.createObjectNode();
            interpreter.put("display", model.signedBy());
            resource.set("resultsInterpreter", json.createArrayNode().add(interpreter));
        }
        return mapped(resource);
    }

    private FhirResourceMappingResult mapDocumentReference(CanonicalResource canonical) {
        CanonicalDocument model = readPayload(canonical.resourcePayloadJson(), CanonicalDocument.class);
        ObjectNode resource = baseResource("DocumentReference", canonical, null);
        resource.put("status", "current");
        resource.set("type", textCodeable(model.documentType()));
        putIfPresent(resource, "description", model.contentDigest());
        putIfPresent(resource, "date", instant(firstInstant(model.eventTime(), model.signedAt())));
        if (model.signedBy() != null && !model.signedBy().isBlank()) {
            ObjectNode author = json.createObjectNode();
            author.put("display", model.signedBy());
            resource.set("author", json.createArrayNode().add(author));
        }
        return mapped(resource);
    }

    private CanonicalResourceMappingResult mapPatientInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "Patient");
        String name = patientName(resource);
        if (name.isBlank()) {
            throw new IllegalArgumentException("FHIR Patient.name 不能为空");
        }
        String mpi = firstNonBlank(firstIdentifier(resource.path("identifier")), id);
        String sourceRecordId = "Patient/" + id;
        String mappedVersion = mappedVersion(version, "Patient");
        CanonicalPatient payload = new CanonicalPatient(
            mpi, name, null, firstNonBlank(text(resource.path("gender"))),
            List.of(), List.of(), sourceSystem(version), sourceRecordId, mappedVersion,
            null, request.receivedAt(), QualityStatus.VALID);
        return result(request, CanonicalResourceType.PATIENT, id, payload, null,
            List.of(), FULL_RATE, QualityStatus.VALID, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapEncounterInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "Encounter");
        String encounterType = firstNonBlank(
            text(resource.path("class").path("code")),
            text(resource.path("type").path("text")));
        if (encounterType.isBlank()) {
            throw new IllegalArgumentException("FHIR Encounter.class.code 不能为空");
        }
        Instant admittedAt = parseInstantOrNull(text(resource.path("period").path("start")));
        if (admittedAt == null) {
            throw new IllegalArgumentException("FHIR Encounter.period.start 不能为空");
        }
        String sourceRecordId = "Encounter/" + id;
        String mappedVersion = mappedVersion(version, "Encounter");
        CanonicalEncounter payload = new CanonicalEncounter(
            id, encounterType, admittedAt, parseInstantOrNull(text(resource.path("period").path("end"))),
            text(resource.path("serviceProvider").path("identifier").path("value")),
            text(resource.path("participant").path(0).path("individual").path("identifier").path("value")),
            text(resource.path("location").path(0).path("location").path("identifier").path("value")),
            sourceSystem(version), sourceRecordId, mappedVersion, admittedAt, request.receivedAt(), QualityStatus.VALID);
        return result(request, CanonicalResourceType.ENCOUNTER, id, payload, admittedAt,
            List.of(), FULL_RATE, QualityStatus.VALID, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapConditionInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "Condition");
        CodingValue coding = codingValue(resource.path("code"), "Condition.code");
        String sourceRecordId = "Condition/" + id;
        String mappedVersion = mappedVersion(version, "Condition");
        FhirCodingMappingResult codingMapping = terminology.mapCode(
            request.tenantId(), CanonicalResourceType.CONDITION, id, "code",
            coding.system(), coding.code(), coding.display(), "ICD-10", sourceRecordId, mappedVersion);
        QualityStatus qualityStatus = quality(codingMapping);
        Instant onset = parseInstantOrNull(text(resource.path("onsetDateTime")));
        CanonicalCondition payload = new CanonicalCondition(
            id, coding.code(), firstNonBlank(coding.system(), "UNKNOWN"),
            coding.display(), text(resource.path("stage").path(0).path("summary").path("text")),
            text(resource.path("severity").path("text")),
            sourceSystem(version), sourceRecordId, mappedVersion, onset, request.receivedAt(), qualityStatus);
        return result(request, CanonicalResourceType.CONDITION, id, payload, onset,
            codingMapping.issues(), codingMapping.mappingRate(), qualityStatus, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapMedicationInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "Medication");
        CodingValue coding = codingValue(resource.path("code"), "Medication.code");
        String sourceRecordId = "Medication/" + id;
        String mappedVersion = mappedVersion(version, "Medication");
        FhirCodingMappingResult codingMapping = terminology.mapCode(
            request.tenantId(), CanonicalResourceType.MEDICATION, id, "code",
            coding.system(), coding.code(), coding.display(), "DRUG", sourceRecordId, mappedVersion);
        QualityStatus qualityStatus = quality(codingMapping);
        CanonicalMedication payload = new CanonicalMedication(
            id, coding.code(), coding.display(), null, null, null, null, null,
            text(resource.path("status")), sourceSystem(version), sourceRecordId, mappedVersion,
            null, request.receivedAt(), qualityStatus);
        return result(request, CanonicalResourceType.MEDICATION, id, payload, null,
            codingMapping.issues(), codingMapping.mappingRate(), qualityStatus, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapProcedureInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "Procedure");
        CodingValue coding = codingValue(resource.path("code"), "Procedure.code");
        String sourceRecordId = "Procedure/" + id;
        String mappedVersion = mappedVersion(version, "Procedure");
        FhirCodingMappingResult codingMapping = terminology.mapCode(
            request.tenantId(), CanonicalResourceType.PROCEDURE, id, "code",
            coding.system(), coding.code(), coding.display(), "PROCEDURE", sourceRecordId, mappedVersion);
        QualityStatus qualityStatus = quality(codingMapping);
        Instant performedAt = parseInstantOrNull(text(resource.path("performedDateTime")));
        CanonicalProcedure payload = new CanonicalProcedure(
            id, coding.code(), coding.display(), null,
            text(resource.path("performer").path(0).path("actor").path("identifier").path("value")),
            performedAt, sourceSystem(version), sourceRecordId, mappedVersion,
            performedAt, request.receivedAt(), qualityStatus);
        return result(request, CanonicalResourceType.PROCEDURE, id, payload, performedAt,
            codingMapping.issues(), codingMapping.mappingRate(), qualityStatus, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapCarePlanInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "CarePlan");
        String pathwayId = firstArrayText(resource.path("instantiatesCanonical"));
        if (pathwayId.isBlank()) {
            throw new IllegalArgumentException("FHIR CarePlan.instantiatesCanonical 不能为空");
        }
        String sourceRecordId = "CarePlan/" + id;
        String mappedVersion = mappedVersion(version, "CarePlan");
        CanonicalCarePlan payload = new CanonicalCarePlan(
            id, pathwayId,
            text(resource.path("activity").path(0).path("detail").path("code").path("text")),
            text(resource.path("note").path(0).path("text")),
            null, sourceSystem(version), sourceRecordId, mappedVersion,
            parseInstantOrNull(text(resource.path("created"))), request.receivedAt(), QualityStatus.VALID);
        return result(request, CanonicalResourceType.CARE_PLAN, id, payload, payload.eventTime(),
            List.of(), FULL_RATE, QualityStatus.VALID, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapDiagnosticReportInbound(FhirCanonicalMappingRequest request,
                                                                      FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "DiagnosticReport");
        String reportType = text(resource.path("code").path("text"));
        if (reportType.isBlank()) {
            reportType = text(firstCoding(resource.path("code").path("coding"), "DiagnosticReport.code").path("code"));
        }
        String conclusion = text(resource.path("conclusion"));
        if (reportType.isBlank() || conclusion.isBlank()) {
            throw new IllegalArgumentException("FHIR DiagnosticReport.code 和 conclusion 不能为空");
        }
        String sourceRecordId = "DiagnosticReport/" + id;
        String mappedVersion = mappedVersion(version, "DiagnosticReport");
        Instant eventTime = firstInstant(
            parseInstantOrNull(text(resource.path("effectiveDateTime"))),
            parseInstantOrNull(text(resource.path("issued"))));
        CanonicalDiagnosticReport payload = new CanonicalDiagnosticReport(
            id, reportType, conclusion, notes(resource.path("note")),
            text(resource.path("resultsInterpreter").path(0).path("display")),
            parseInstantOrNull(text(resource.path("issued"))),
            sourceSystem(version), sourceRecordId, mappedVersion, eventTime, request.receivedAt(), QualityStatus.VALID);
        return result(request, CanonicalResourceType.DIAGNOSTIC_REPORT, id, payload, eventTime,
            List.of(), FULL_RATE, QualityStatus.VALID, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult mapDocumentInbound(FhirCanonicalMappingRequest request, FhirVersion version) {
        JsonNode resource = request.resource();
        String id = requiredId(resource, "DocumentReference");
        String documentType = firstNonBlank(text(resource.path("type").path("text")), "DocumentReference");
        String sourceRecordId = "DocumentReference/" + id;
        String mappedVersion = mappedVersion(version, "DocumentReference");
        Instant eventTime = parseInstantOrNull(text(resource.path("date")));
        CanonicalDocument payload = new CanonicalDocument(
            id, documentType, text(resource.path("description")),
            text(resource.path("author").path(0).path("display")), eventTime,
            sourceSystem(version), sourceRecordId, mappedVersion, eventTime, request.receivedAt(), QualityStatus.VALID);
        return result(request, CanonicalResourceType.DOCUMENT, id, payload, eventTime,
            List.of(), FULL_RATE, QualityStatus.VALID, sourceRecordId, mappedVersion);
    }

    private CanonicalResourceMappingResult result(FhirCanonicalMappingRequest request,
                                                  CanonicalResourceType type,
                                                  String resourceId,
                                                  Object payload,
                                                  Instant eventTime,
                                                  List<FhirOperationOutcomeIssue> issues,
                                                  BigDecimal mappingRate,
                                                  QualityStatus qualityStatus,
                                                  String sourceRecordId,
                                                  String mappedVersion) {
        CanonicalResource canonical = new CanonicalResource(
            null,
            resourceId,
            request.snapshotId(),
            request.tenantId(),
            type,
            writePayload(payload),
            sourceSystemFromMappedVersion(mappedVersion),
            sourceRecordId,
            mappedVersion,
            eventTime,
            request.receivedAt(),
            qualityStatus,
            request.seqNo(),
            request.traceId());
        return new CanonicalResourceMappingResult(canonical, issues, mappingRate);
    }

    private ObjectNode baseResource(String resourceType, CanonicalResource canonical, String profileUrl) {
        ObjectNode resource = json.createObjectNode();
        resource.put("resourceType", resourceType);
        putIfPresent(resource, "id", canonical.resourceId());
        ObjectNode meta = resource.putObject("meta");
        meta.put("source", "canonical_resource/" + canonical.resourceId());
        if (profileUrl != null && !profileUrl.isBlank()) {
            meta.set("profile", json.createArrayNode().add(profileUrl));
        }
        return resource;
    }

    private ObjectNode codeable(String system, String code, String display) {
        ObjectNode codeable = json.createObjectNode();
        ArrayNode coding = codeable.putArray("coding");
        ObjectNode item = json.createObjectNode();
        putIfPresent(item, "system", system);
        putIfPresent(item, "code", code);
        putIfPresent(item, "display", display);
        coding.add(item);
        putIfPresent(codeable, "text", firstNonBlank(display, code));
        return codeable;
    }

    private ObjectNode textCodeable(String text) {
        ObjectNode codeable = json.createObjectNode();
        putIfPresent(codeable, "text", text);
        return codeable;
    }

    private void setIdentifierObject(ObjectNode target, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        ObjectNode child = json.createObjectNode();
        ObjectNode identifier = child.putObject("identifier");
        identifier.put("value", value);
        target.set(field, child);
    }

    private FhirResourceMappingResult mapped(ObjectNode resource) {
        return new FhirResourceMappingResult(resource, List.of(), FULL_RATE);
    }

    private <T> T readPayload(String payloadJson, Class<T> type) {
        try {
            return json.readValue(payloadJson, type);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("标准临床资源 payload 不是合法 JSON", ex);
        }
    }

    private String writePayload(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("标准临床资源 payload 序列化失败", ex);
        }
    }

    private static JsonNode firstCoding(JsonNode codings, String fieldName) {
        if (codings instanceof ArrayNode array && !array.isEmpty()) {
            return array.get(0);
        }
        throw new IllegalArgumentException("FHIR " + fieldName + ".coding 不能为空");
    }

    private static CodingValue codingValue(JsonNode codeable, String fieldName) {
        JsonNode coding = firstCoding(codeable.path("coding"), fieldName);
        String code = firstNonBlank(text(coding.path("code")), text(codeable.path("text")));
        String display = firstNonBlank(text(coding.path("display")), text(codeable.path("text")), code);
        if (code.isBlank()) {
            throw new IllegalArgumentException("FHIR " + fieldName + " 不能为空");
        }
        return new CodingValue(text(coding.path("system")), code, display);
    }

    private static String requiredId(JsonNode resource, String resourceType) {
        String id = text(resource.path("id"));
        if (id.isBlank()) {
            throw new IllegalArgumentException("FHIR " + resourceType + ".id 不能为空");
        }
        return id;
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

    private static String instant(Instant value) {
        return value == null ? null : value.toString();
    }

    private static Instant firstInstant(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String sourceSystem(FhirVersion version) {
        return "FHIR_" + version.name();
    }

    private static String mappedVersion(FhirVersion version, String resourceType) {
        return sourceSystem(version) + ":" + resourceType;
    }

    private static String sourceSystemFromMappedVersion(String mappedVersion) {
        int index = mappedVersion.indexOf(':');
        return index < 0 ? mappedVersion : mappedVersion.substring(0, index);
    }

    private static QualityStatus quality(FhirCodingMappingResult mapping) {
        return mapping.issues().isEmpty() ? QualityStatus.VALID : QualityStatus.PARTIAL;
    }

    private static String patientProfile(FhirVersion version) {
        return switch (version) {
            case R4 -> "http://hl7.org/fhir/StructureDefinition/Patient";
            case R5 -> "http://hl7.org/fhir/5.0/StructureDefinition/Patient";
        };
    }

    private static String firstIdentifier(JsonNode identifiers) {
        if (identifiers instanceof ArrayNode array && !array.isEmpty()) {
            return text(array.get(0).path("value"));
        }
        return "";
    }

    private static String firstArrayText(JsonNode values) {
        if (values instanceof ArrayNode array && !array.isEmpty()) {
            return text(array.get(0));
        }
        return "";
    }

    private static String patientName(JsonNode resource) {
        JsonNode names = resource.path("name");
        if (!(names instanceof ArrayNode array) || array.isEmpty()) {
            return "";
        }
        JsonNode name = array.get(0);
        String text = text(name.path("text"));
        if (!text.isBlank()) {
            return text;
        }
        List<String> parts = new ArrayList<>();
        String family = text(name.path("family"));
        if (!family.isBlank()) {
            parts.add(family);
        }
        JsonNode given = name.path("given");
        if (given instanceof ArrayNode givenArray) {
            givenArray.forEach(item -> {
                String value = text(item);
                if (!value.isBlank()) {
                    parts.add(value);
                }
            });
        }
        return String.join(" ", parts);
    }

    private static List<String> notes(JsonNode notes) {
        if (!(notes instanceof ArrayNode array) || array.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        array.forEach(item -> {
            String value = text(item.path("text"));
            if (!value.isBlank()) {
                values.add(value);
            }
        });
        return List.copyOf(values);
    }

    private record CodingValue(String system, String code, String display) {}
}
