package com.medkernel.engine.clinical.model;

import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * 保存标准临床对象前补齐 ULID 主键。
 */
final class ClinicalIdCallbacks {

    private ClinicalIdCallbacks() {}
}

@Component
class ClinicalPatientIdCallback implements BeforeConvertCallback<ClinicalPatient> {
    @Override
    public ClinicalPatient onBeforeConvert(ClinicalPatient aggregate) {
        if (aggregate.patientId() != null) {
            return aggregate;
        }
        return new ClinicalPatient(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.nameCipher(),
            aggregate.nameMask(), aggregate.identityNoCipher(), aggregate.identityNoMask(), aggregate.phoneCipher(),
            aggregate.phoneMask(), aggregate.birthDate(), aggregate.genderCode(), aggregate.createdAt(),
            aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalEncounterIdCallback implements BeforeConvertCallback<ClinicalEncounter> {
    @Override
    public ClinicalEncounter onBeforeConvert(ClinicalEncounter aggregate) {
        if (aggregate.encounterId() != null) {
            return aggregate;
        }
        return new ClinicalEncounter(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterClass(), aggregate.status(), aggregate.startedAt(), aggregate.endedAt(),
            aggregate.orgUnitId(), aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(),
            aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalConditionIdCallback implements BeforeConvertCallback<ClinicalCondition> {
    @Override
    public ClinicalCondition onBeforeConvert(ClinicalCondition aggregate) {
        if (aggregate.conditionId() != null) {
            return aggregate;
        }
        return new ClinicalCondition(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.code(), aggregate.codeSystem(), aggregate.displayName(),
            aggregate.clinicalStatus(), aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(),
            aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalObservationIdCallback implements BeforeConvertCallback<ClinicalObservation> {
    @Override
    public ClinicalObservation onBeforeConvert(ClinicalObservation aggregate) {
        if (aggregate.observationId() != null) {
            return aggregate;
        }
        return new ClinicalObservation(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.code(), aggregate.codeSystem(), aggregate.displayName(),
            aggregate.valueNumeric(), aggregate.unit(), aggregate.criticalFlag(), aggregate.createdAt(),
            aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalMedicationIdCallback implements BeforeConvertCallback<ClinicalMedication> {
    @Override
    public ClinicalMedication onBeforeConvert(ClinicalMedication aggregate) {
        if (aggregate.medicationId() != null) {
            return aggregate;
        }
        return new ClinicalMedication(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.code(), aggregate.codeSystem(), aggregate.displayName(), aggregate.dose(),
            aggregate.doseUnit(), aggregate.route(), aggregate.frequency(), aggregate.status(), aggregate.createdAt(),
            aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalProcedureIdCallback implements BeforeConvertCallback<ClinicalProcedure> {
    @Override
    public ClinicalProcedure onBeforeConvert(ClinicalProcedure aggregate) {
        if (aggregate.procedureId() != null) {
            return aggregate;
        }
        return new ClinicalProcedure(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.code(), aggregate.codeSystem(), aggregate.displayName(),
            aggregate.status(), aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(),
            aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalDiagnosticReportIdCallback implements BeforeConvertCallback<ClinicalDiagnosticReport> {
    @Override
    public ClinicalDiagnosticReport onBeforeConvert(ClinicalDiagnosticReport aggregate) {
        if (aggregate.reportId() != null) {
            return aggregate;
        }
        return new ClinicalDiagnosticReport(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.reportType(), aggregate.status(), aggregate.conclusion(),
            aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(),
            aggregate.traceId());
    }
}

@Component
class ClinicalDocumentIdCallback implements BeforeConvertCallback<ClinicalDocument> {
    @Override
    public ClinicalDocument onBeforeConvert(ClinicalDocument aggregate) {
        if (aggregate.documentId() != null) {
            return aggregate;
        }
        return new ClinicalDocument(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.documentType(), aggregate.status(), aggregate.contentHash(),
            aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(),
            aggregate.traceId());
    }
}

@Component
class ClinicalNursingAssessmentIdCallback implements BeforeConvertCallback<ClinicalNursingAssessment> {
    @Override
    public ClinicalNursingAssessment onBeforeConvert(ClinicalNursingAssessment aggregate) {
        if (aggregate.assessmentId() != null) {
            return aggregate;
        }
        return new ClinicalNursingAssessment(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.assessmentType(), aggregate.status(), aggregate.riskLevel(),
            aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(),
            aggregate.traceId());
    }
}

@Component
class ClinicalCarePlanIdCallback implements BeforeConvertCallback<ClinicalCarePlan> {
    @Override
    public ClinicalCarePlan onBeforeConvert(ClinicalCarePlan aggregate) {
        if (aggregate.carePlanId() != null) {
            return aggregate;
        }
        return new ClinicalCarePlan(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.pathwayId(), aggregate.status(), aggregate.createdAt(),
            aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(), aggregate.traceId());
    }
}

@Component
class ClinicalFollowUpIdCallback implements BeforeConvertCallback<ClinicalFollowUp> {
    @Override
    public ClinicalFollowUp onBeforeConvert(ClinicalFollowUp aggregate) {
        if (aggregate.followUpId() != null) {
            return aggregate;
        }
        return new ClinicalFollowUp(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.planType(), aggregate.status(), aggregate.plannedAt(),
            aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(),
            aggregate.traceId());
    }
}

@Component
class ClinicalClaimIdCallback implements BeforeConvertCallback<ClinicalClaim> {
    @Override
    public ClinicalClaim onBeforeConvert(ClinicalClaim aggregate) {
        if (aggregate.claimId() != null) {
            return aggregate;
        }
        return new ClinicalClaim(ClinicalIds.newUlid(), aggregate.tenantId(), aggregate.orgPath(),
            aggregate.sourceSystem(), aggregate.sourceId(), aggregate.fhirResourceId(), aggregate.patientId(),
            aggregate.encounterId(), aggregate.claimType(), aggregate.status(), aggregate.totalAmount(),
            aggregate.createdAt(), aggregate.createdBy(), aggregate.updatedAt(), aggregate.updatedBy(),
            aggregate.traceId());
    }
}
