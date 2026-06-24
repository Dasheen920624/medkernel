package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

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

class ClinicalCodeMappingAnchorRegistryTest {

    @Test
    void definitionsCoverEveryStandardClinicalResourceType() {
        assertThat(ClinicalCodeMappingAnchorRegistry.definitions())
            .extracting(ClinicalCodeMappingAnchorDefinition::resourceType)
            .contains(CanonicalResourceType.values());
        assertThat(ClinicalCodeMappingAnchorRegistry.definitions())
            .noneMatch(definition -> definition.resourceType() == CanonicalResourceType.PATIENT
                && "allergies".equals(definition.fieldName()));
    }

    @Test
    void extractsTraceableAnchorsFromTwelveStandardResources() {
        ContextSnapshotResources resources = fullResources();

        List<ClinicalCodeMappingAnchor> anchors =
            ClinicalCodeMappingAnchorRegistry.fromResources(resources);

        assertThat(anchors)
            .extracting(ClinicalCodeMappingAnchor::resourceType)
            .contains(CanonicalResourceType.values());
        assertThat(anchors).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.CONDITION);
            assertThat(anchor.resourceId()).isEqualTo("cond-1");
            assertThat(anchor.fieldName()).isEqualTo("code");
            assertThat(anchor.localCode()).isEqualTo("I10");
            assertThat(anchor.localCodeSystem()).isEqualTo("ICD-10");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.DIAGNOSIS");
            assertThat(anchor.key()).isEqualTo("CONDITION:cond-1:code:I10");
        });
        assertThat(anchors).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.MEDICATION);
            assertThat(anchor.localCode()).isEqualTo("DRUG-A");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.DRUG");
        });
        assertThat(anchors).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.CLAIM);
            assertThat(anchor.localCode()).isEqualTo("DRG-A");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.INSURANCE");
        });
        assertThat(anchors).anySatisfy(anchor -> {
            assertThat(anchor.resourceType()).isEqualTo(CanonicalResourceType.ALLERGY_INTOLERANCE);
            assertThat(anchor.resourceId()).isEqualTo("alg-1");
            assertThat(anchor.fieldName()).isEqualTo("code");
            assertThat(anchor.localCode()).isEqualTo("ATC-J01C");
            assertThat(anchor.targetDictionaryKey()).isEqualTo("TERM.DRUG");
        });
    }

    private ContextSnapshotResources fullResources() {
        Instant now = Instant.parse("2026-06-01T01:00:00Z");
        return new ContextSnapshotResources(
            new CanonicalPatient("MPI-1", "张三", LocalDate.of(1980, 1, 1), "M",
                List.of("PREGNANT"), "HIS", "pat-rec-1", "v1", now, now, QualityStatus.VALID),
            List.of(new CanonicalAllergyIntolerance("alg-1", "ATC-J01C", "ATC", "青霉素类",
                "MEDICATION", "HIGH", List.of("皮疹", "喉头水肿"), "ACTIVE", "CONFIRMED",
                "HIS", "alg-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalEncounter("ENC-1", "INPATIENT", now, null,
                "DEPT-A", "DOC-A", null, "HIS", "enc-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalCondition("cond-1", "I10", "ICD-10", "原发性高血压",
                "ACTIVE", "HIGH", "HIS", "cond-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalNursingAssessment("nurse-1", "FALL_RISK", "HIGH", "ACTIVE",
                "NIS", "nurse-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalObservation("obs-1", "GLU", "血糖", BigDecimal.TEN, null,
                "mmol/L", "3.9-6.1", "CRITICAL", "LIS", "obs-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalDiagnosticReport("report-1", "LAB", "已完成",
                List.of("GLU"), "doc-1", now, "LIS", "report-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalMedication("med-1", "DRUG-A", "药品A", BigDecimal.ONE,
                "mg", "PO", "BID", "7", "ACTIVE", "HIS", "med-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalProcedure("proc-1", "PROC-A", "操作A", "GENERAL",
                "doc-2", now, "EMR", "proc-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalDocument("doc-1", "DISCHARGE_SUMMARY", "sha256:doc",
                "doc-1", now, "EMR", "doc-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalCarePlan("care-1", "PATH-A", "NODE-A", "VAR-A",
                now, "PATH", "care-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalFollowUp("follow-1", "PHONE", now, "Q-A", "ABNORMAL",
                "FOLLOW", "follow-rec-1", "v1", now, now, QualityStatus.VALID)),
            List.of(new CanonicalClaim("claim-1", "DRG-A", BigDecimal.TEN, BigDecimal.ONE,
                "CLAIM", "claim-rec-1", "v1", now, now, QualityStatus.VALID)),
            ContextSnapshotResources.emptyExtensions()
        );
    }
}
