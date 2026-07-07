package com.medkernel.engine.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.context.canonical.CanonicalCarePlan;
import com.medkernel.engine.context.canonical.CanonicalClaim;
import com.medkernel.engine.context.canonical.CanonicalCondition;
import com.medkernel.engine.context.canonical.CanonicalDiagnosticReport;
import com.medkernel.engine.context.canonical.CanonicalDocument;
import com.medkernel.engine.context.canonical.CanonicalEncounter;
import com.medkernel.engine.context.canonical.CanonicalFollowUp;
import com.medkernel.engine.context.canonical.CanonicalMedication;
import com.medkernel.engine.context.canonical.CanonicalNursingAssessment;
import com.medkernel.engine.context.canonical.CanonicalObservation;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.context.canonical.CanonicalProcedure;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;

/**
 * 从持久化事件与 payload 构造统一临床事件上下文。
 */
@Component
public class ClinicalEventContextFactory {

    private final ObjectMapper json;

    public ClinicalEventContextFactory(ObjectMapper json) {
        this.json = json;
    }

    public ClinicalEventContext from(ClinicalEvent event, ClinicalEventPayload payload) {
        JsonNode payloadNode = readPayload(payload);
        ContextSnapshotResources resources = projectResources(event, payloadNode);
        ObjectNode canonicalPayload = json.valueToTree(resources);
        canonicalPayload.set("eventPayload", payloadNode.deepCopy());
        List<ClinicalCodeMappingAnchor> anchors = mergedAnchors(resources, event, payloadNode);
        return new ClinicalEventContext(
            event.eventId(),
            event.tenantId(),
            readOrgScope(event),
            event.eventType(),
            event.triggerPoint(),
            event.patientId(),
            event.encounterId(),
            event.clinicalSetting(),
            event.snapshotId(),
            event.sourceSystem(),
            event.runtimeReleaseId(),
            event.payloadDigest(),
            event.occurredAt(),
            triggerSource(event),
            event.traceId(),
            resources,
            canonicalPayload,
            anchors
        );
    }

    private JsonNode readPayload(ClinicalEventPayload payload) {
        try {
            return json.readTree(payload.payload());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "临床事件 payload JSON 解析失败", exception);
        }
    }

    OrgScope readOrgScope(ClinicalEvent event) {
        if (event.orgScopeJson() == null || event.orgScopeJson().isBlank()) {
            return OrgScope.tenant(event.tenantId());
        }
        try {
            OrgScope scope = json.readValue(event.orgScopeJson(), OrgScope.class);
            return scope.hasTenant() ? scope : OrgScope.tenant(event.tenantId());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.ENG_EVENT_001, "临床事件组织上下文 JSON 解析失败", exception);
        }
    }

    private String triggerSource(ClinicalEvent event) {
        String source = event.sourceSystem() == null || event.sourceSystem().isBlank()
            ? "UNKNOWN"
            : event.sourceSystem();
        return source + ":" + event.triggerPoint().wireValue();
    }

    private ContextSnapshotResources projectResources(ClinicalEvent event, JsonNode payload) {
        return new ContextSnapshotResources(
            patient(event, payload.path("patient")),
            List.of(),
            encounters(event, payload),
            conditions(event, payload),
            List.<CanonicalNursingAssessment>of(),
            observations(event, payload),
            diagnosticReports(event, payload),
            medications(event, payload),
            List.<CanonicalProcedure>of(),
            documents(event, payload),
            List.<CanonicalCarePlan>of(),
            followUps(event, payload),
            List.<CanonicalClaim>of(),
            extensions(payload)
        );
    }

    private CanonicalPatient patient(ClinicalEvent event, JsonNode patient) {
        return new CanonicalPatient(
            firstText(patient, event.patientId(), "mpi", "patientId"),
            firstText(patient, "脱敏患者", "name", "displayName"),
            localDate(patient.path("birthDate").asText(null)),
            text(patient, "gender"),
            stringList(patient.path("specialPopulations")),
            sourceSystem(event, patient),
            firstText(patient, event.patientId(), "sourceRecordId", "sourceId"),
            mappedVersion(event, patient),
            event.occurredAt(),
            event.receivedAt(),
            QualityStatus.VALID
        );
    }

    private List<CanonicalEncounter> encounters(ClinicalEvent event, JsonNode payload) {
        List<CanonicalEncounter> values = new ArrayList<>();
        JsonNode admission = payload.path("admission");
        if (admission.isObject() || hasText(event.encounterId())) {
            values.add(new CanonicalEncounter(
                firstText(admission, event.encounterId(), "encounterId", "id"),
                firstText(admission, event.clinicalSetting().name(), "encounterType", "type"),
                instant(firstText(admission, event.occurredAt().toString(), "admissionTime", "occurredAt")),
                instant(text(admission, "dischargeTime")),
                text(admission, "departmentId"),
                text(admission, "attendingDoctorId"),
                text(admission, "bedId"),
                sourceSystem(event, admission),
                firstText(admission, event.encounterId(), "sourceRecordId", "sourceId"),
                mappedVersion(event, admission),
                event.occurredAt(),
                event.receivedAt(),
                QualityStatus.VALID
            ));
        }
        return List.copyOf(values);
    }

    private List<CanonicalCondition> conditions(ClinicalEvent event, JsonNode payload) {
        List<CanonicalCondition> values = new ArrayList<>();
        for (JsonNode item : array(payload, "diagnoses", "conditions")) {
            String standardCode = standardCode(item);
            values.add(new CanonicalCondition(
                firstText(item, "cond-" + values.size(), "conditionId", "id"),
                standardCode,
                firstText(item, "ICD-10", "codeSystem", "standardCodeSystem"),
                firstText(item, standardCode, "displayName", "name"),
                text(item, "stage"),
                text(item, "severity"),
                sourceSystem(event, item),
                firstText(item, null, "sourceRecordId", "sourceId"),
                mappedVersion(event, item),
                instant(text(item, "onsetTime")),
                event.receivedAt(),
                QualityStatus.VALID
            ));
        }
        return List.copyOf(values);
    }

    private List<CanonicalObservation> observations(ClinicalEvent event, JsonNode payload) {
        List<CanonicalObservation> values = new ArrayList<>();
        for (JsonNode item : array(payload, "results", "observations")) {
            String standardCode = standardCode(item);
            values.add(new CanonicalObservation(
                firstText(item, "obs-" + values.size(), "observationId", "id", "resultId"),
                standardCode,
                firstText(item, standardCode, "displayName", "name"),
                decimal(item.path("valueNumeric").asText(null)),
                text(item, "valueString"),
                text(item, "unit"),
                text(item, "referenceRange"),
                text(item, "criticalFlag"),
                sourceSystem(event, item),
                firstText(item, null, "sourceRecordId", "sourceId"),
                mappedVersion(event, item),
                instant(firstText(item, event.occurredAt().toString(), "eventTime", "effectiveTime")),
                event.receivedAt(),
                QualityStatus.VALID
            ));
        }
        return List.copyOf(values);
    }

    private List<CanonicalDiagnosticReport> diagnosticReports(ClinicalEvent event, JsonNode payload) {
        List<CanonicalDiagnosticReport> values = new ArrayList<>();
        JsonNode report = payload.path("report");
        if (report.isObject()) {
            values.add(report(event, report, values.size()));
        }
        for (JsonNode item : array(payload, "diagnosticReports")) {
            values.add(report(event, item, values.size()));
        }
        return List.copyOf(values);
    }

    private CanonicalDiagnosticReport report(ClinicalEvent event, JsonNode item, int index) {
        return new CanonicalDiagnosticReport(
            firstText(item, "report-" + index, "reportId", "id"),
            firstText(item, "REPORT", "reportType", "type"),
            firstText(item, "已接收", "conclusion", "summary"),
            stringList(item.path("keyFindings")),
            text(item, "signedBy"),
            instant(text(item, "signedAt")),
            sourceSystem(event, item),
            firstText(item, null, "sourceRecordId", "sourceId"),
            mappedVersion(event, item),
            event.occurredAt(),
            event.receivedAt(),
            QualityStatus.VALID
        );
    }

    private List<CanonicalMedication> medications(ClinicalEvent event, JsonNode payload) {
        List<CanonicalMedication> values = new ArrayList<>();
        for (JsonNode item : array(payload, "orders", "medications")) {
            String standardCode = standardCode(item);
            values.add(new CanonicalMedication(
                firstText(item, "med-" + values.size(), "medicationId", "orderId", "id"),
                standardCode,
                firstText(item, standardCode, "displayName", "name"),
                decimal(text(item, "dose")),
                text(item, "doseUnit"),
                text(item, "route"),
                text(item, "frequency"),
                text(item, "durationDays"),
                firstText(item, "ACTIVE", "prescriptionStatus", "status"),
                sourceSystem(event, item),
                firstText(item, null, "sourceRecordId", "sourceId"),
                mappedVersion(event, item),
                event.occurredAt(),
                event.receivedAt(),
                QualityStatus.VALID
            ));
        }
        return List.copyOf(values);
    }

    private List<CanonicalDocument> documents(ClinicalEvent event, JsonNode payload) {
        List<CanonicalDocument> values = new ArrayList<>();
        JsonNode dischargeSummary = payload.path("dischargeSummary");
        if (dischargeSummary.isObject()) {
            values.add(document(event, dischargeSummary, values.size(), "DISCHARGE_SUMMARY"));
        }
        for (JsonNode item : array(payload, "documents")) {
            values.add(document(event, item, values.size(), "DOCUMENT"));
        }
        return List.copyOf(values);
    }

    private CanonicalDocument document(ClinicalEvent event, JsonNode item, int index, String defaultType) {
        return new CanonicalDocument(
            firstText(item, "doc-" + index, "documentId", "id"),
            firstText(item, defaultType, "documentType", "type"),
            text(item, "contentDigest"),
            text(item, "signedBy"),
            instant(text(item, "signedAt")),
            sourceSystem(event, item),
            firstText(item, null, "sourceRecordId", "sourceId"),
            mappedVersion(event, item),
            event.occurredAt(),
            event.receivedAt(),
            QualityStatus.VALID
        );
    }

    private List<CanonicalFollowUp> followUps(ClinicalEvent event, JsonNode payload) {
        List<CanonicalFollowUp> values = new ArrayList<>();
        JsonNode followup = payload.path("followup");
        if (followup.isObject()) {
            values.add(followUp(event, followup, values.size()));
        }
        for (JsonNode item : array(payload, "followUps")) {
            values.add(followUp(event, item, values.size()));
        }
        return List.copyOf(values);
    }

    private CanonicalFollowUp followUp(ClinicalEvent event, JsonNode item, int index) {
        return new CanonicalFollowUp(
            firstText(item, "follow-" + index, "followUpId", "id", "followupPlanId"),
            firstText(item, "FOLLOWUP", "planType", "type"),
            instant(text(item, "plannedAt")),
            text(item, "questionnaireId"),
            text(item, "abnormalFlag"),
            sourceSystem(event, item),
            firstText(item, null, "sourceRecordId", "sourceId"),
            mappedVersion(event, item),
            event.occurredAt(),
            event.receivedAt(),
            QualityStatus.VALID
        );
    }

    private List<ClinicalCodeMappingAnchor> mergedAnchors(
            ContextSnapshotResources resources, ClinicalEvent event, JsonNode payload) {
        ArrayList<ClinicalCodeMappingAnchor> anchors =
            new ArrayList<>(ClinicalCodeMappingAnchorRegistry.fromResources(resources));
        addPayloadAnchors(anchors, CanonicalResourceType.MEDICATION, "code", "TERM.DRUG",
            event, array(payload, "orders", "medications"), "medicationId", "orderId");
        addPayloadAnchors(anchors, CanonicalResourceType.CONDITION, "code", "TERM.DIAGNOSIS",
            event, array(payload, "diagnoses", "conditions"), "conditionId", "id");
        addPayloadAnchors(anchors, CanonicalResourceType.OBSERVATION, "code", "TERM.LAB",
            event, array(payload, "results", "observations"), "observationId", "resultId", "id");
        return List.copyOf(anchors);
    }

    private JsonNode extensions(JsonNode payload) {
        ObjectNode extensions = payload.path("extensions").isObject()
            ? (ObjectNode) payload.path("extensions").deepCopy()
            : json.createObjectNode();
        ObjectNode local = extensions.path("local").isObject()
            ? (ObjectNode) extensions.path("local")
            : json.createObjectNode();
        projectLocalExtension(payload, local, "pharmacyReview");
        projectLocalExtension(payload, local, "publicHealthReport");
        projectLocalExtension(payload, local, "safetyEvent");
        if (!local.isEmpty()) {
            extensions.set("local", local);
        }
        return extensions;
    }

    private void projectLocalExtension(JsonNode payload, ObjectNode local, String fieldName) {
        JsonNode value = payload.path(fieldName);
        if (value.isObject()) {
            local.set(fieldName, value.deepCopy());
        }
    }

    private void addPayloadAnchors(List<ClinicalCodeMappingAnchor> anchors,
                                   CanonicalResourceType resourceType,
                                   String fieldName,
                                   String dictionary,
                                   ClinicalEvent event,
                                   List<JsonNode> items,
                                   String... idFields) {
        for (int index = 0; index < items.size(); index++) {
            JsonNode item = items.get(index);
            String localCode = firstText(item, null, "localCode", "code");
            if (!hasText(localCode)) {
                continue;
            }
            String resourceId = firstText(item, resourceType.name().toLowerCase() + "-" + index, idFields);
            anchors.add(new ClinicalCodeMappingAnchor(
                resourceType,
                resourceId,
                fieldName,
                localCode,
                text(item, "localCodeSystem"),
                firstText(item, localCode, "displayName", "name"),
                dictionary,
                sourceSystem(event, item),
                firstText(item, null, "sourceRecordId", "sourceId"),
                mappedVersion(event, item)
            ));
        }
    }

    private List<JsonNode> array(JsonNode payload, String... fields) {
        for (String field : fields) {
            JsonNode node = payload.path(field);
            if (node.isArray()) {
                ArrayList<JsonNode> values = new ArrayList<>();
                node.forEach(values::add);
                return values;
            }
        }
        return List.of();
    }

    private String standardCode(JsonNode item) {
        return firstText(item, null, "standardCode", "code", "localCode");
    }

    private String mappedVersion(ClinicalEvent event, JsonNode node) {
        return firstText(node, event.runtimeReleaseId(), "mappedVersion", "mappingVersion");
    }

    private String sourceSystem(ClinicalEvent event, JsonNode node) {
        return firstText(node, event.sourceSystem(), "sourceSystem");
    }

    private String firstText(JsonNode node, String fallback, String... fields) {
        if (node != null) {
            for (String field : fields) {
                String value = text(node, field);
                if (hasText(value)) {
                    return value;
                }
            }
        }
        return hasText(fallback) ? fallback : null;
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return hasText(text) ? text.trim() : null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        ArrayList<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText(null);
            if (hasText(value)) {
                values.add(value.trim());
            }
        });
        return List.copyOf(values);
    }

    private BigDecimal decimal(String value) {
        if (!hasText(value)) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private Instant instant(String value) {
        if (!hasText(value)) {
            return null;
        }
        return Instant.parse(value.trim());
    }

    private LocalDate localDate(String value) {
        if (!hasText(value)) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
