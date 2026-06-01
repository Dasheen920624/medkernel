package com.medkernel.engine.context;

import java.util.List;

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
 * 12 类标准临床资源容器（snapshot 请求的 resources 字段）。
 */
public record ContextSnapshotResources(
    @Valid CanonicalPatient patient,
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
    @Valid List<CanonicalClaim> claims
) {}
