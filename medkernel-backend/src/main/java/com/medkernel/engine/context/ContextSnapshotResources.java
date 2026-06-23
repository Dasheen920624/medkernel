package com.medkernel.engine.context;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.medkernel.engine.context.canonical.CanonicalAllergyIntolerance;
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

import jakarta.validation.Valid;

/**
 * 13 类标准临床资源容器（snapshot 请求的 resources 字段）。
 */
public record ContextSnapshotResources(
    @Valid CanonicalPatient patient,
    @Valid List<CanonicalAllergyIntolerance> allergyIntolerances,
    @Valid List<CanonicalEncounter> encounters,
    @Valid List<CanonicalCondition> conditions,
    @Valid List<CanonicalNursingAssessment> nursingAssessments,
    @Valid List<CanonicalObservation> observations,
    @Valid List<CanonicalDiagnosticReport> diagnosticReports,
    @Valid List<CanonicalMedication> medications,
    @Valid List<CanonicalProcedure> procedures,
    @Valid List<CanonicalDocument> documents,
    @Valid List<CanonicalCarePlan> carePlans,
    @Valid List<CanonicalFollowUp> followUps,
    @Valid List<CanonicalClaim> claims,
    JsonNode extensions
) {

    public ContextSnapshotResources {
        allergyIntolerances = safeList(allergyIntolerances);
        encounters = safeList(encounters);
        conditions = safeList(conditions);
        nursingAssessments = safeList(nursingAssessments);
        observations = safeList(observations);
        diagnosticReports = safeList(diagnosticReports);
        medications = safeList(medications);
        procedures = safeList(procedures);
        documents = safeList(documents);
        carePlans = safeList(carePlans);
        followUps = safeList(followUps);
        claims = safeList(claims);
        extensions = normalizeExtensions(extensions);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /**
     * 显式表达“本次快照无院内扩展事实”。
     */
    public static JsonNode emptyExtensions() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static JsonNode normalizeExtensions(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return JsonNodeFactory.instance.objectNode();
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("上下文扩展字段必须是 JSON 对象");
        }
        value.fieldNames().forEachRemaining(namespace -> {
            if (!"local".equals(namespace)) {
                throw new IllegalArgumentException("上下文扩展字段只允许 extensions.local 命名空间");
            }
        });
        JsonNode local = value.path("local");
        if (!local.isMissingNode() && !local.isObject()) {
            throw new IllegalArgumentException("上下文 extensions.local 必须是 JSON 对象");
        }
        return value.deepCopy();
    }
}
