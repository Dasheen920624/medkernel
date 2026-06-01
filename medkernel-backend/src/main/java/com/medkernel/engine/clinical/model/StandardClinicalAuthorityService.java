package com.medkernel.engine.clinical.model;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

/**
 * 标准临床对象关系库权威读取服务。
 *
 * <p>图投影状态只作为诚实诊断信息返回，不参与业务事实读取，确保图/Dify 关闭时
 * 标准临床对象主链路仍从关系库真实可用。
 */
@Service
public class StandardClinicalAuthorityService {

    private static final Logger log = LoggerFactory.getLogger(StandardClinicalAuthorityService.class);
    public static final String AUTHORITY_SOURCE_RELATIONAL_DATABASE = "RELATIONAL_DATABASE";

    private final ClinicalPatientRepository patients;
    private final ClinicalEncounterRepository encounters;
    private final ClinicalConditionRepository conditions;
    private final ClinicalObservationRepository observations;
    private final ClinicalMedicationRepository medications;
    private final ClinicalProcedureRepository procedures;
    private final ClinicalDiagnosticReportRepository diagnosticReports;
    private final ClinicalDocumentRepository documents;
    private final ClinicalNursingAssessmentRepository nursingAssessments;
    private final ClinicalCarePlanRepository carePlans;
    private final ClinicalFollowUpRepository followUps;
    private final ClinicalClaimRepository claims;
    private final ClinicalProjectionStatusPort projectionStatus;
    private final StandardClinicalFhirMappingRegistry fhirMappings;

    public StandardClinicalAuthorityService(
            ClinicalPatientRepository patients,
            ClinicalEncounterRepository encounters,
            ClinicalConditionRepository conditions,
            ClinicalObservationRepository observations,
            ClinicalMedicationRepository medications,
            ClinicalProcedureRepository procedures,
            ClinicalDiagnosticReportRepository diagnosticReports,
            ClinicalDocumentRepository documents,
            ClinicalNursingAssessmentRepository nursingAssessments,
            ClinicalCarePlanRepository carePlans,
            ClinicalFollowUpRepository followUps,
            ClinicalClaimRepository claims,
            ClinicalProjectionStatusPort projectionStatus,
            StandardClinicalFhirMappingRegistry fhirMappings) {
        this.patients = patients;
        this.encounters = encounters;
        this.conditions = conditions;
        this.observations = observations;
        this.medications = medications;
        this.procedures = procedures;
        this.diagnosticReports = diagnosticReports;
        this.documents = documents;
        this.nursingAssessments = nursingAssessments;
        this.carePlans = carePlans;
        this.followUps = followUps;
        this.claims = claims;
        this.projectionStatus = projectionStatus;
        this.fhirMappings = fhirMappings;
    }

    @Transactional(readOnly = true)
    public StandardClinicalAuthorityBundle authorityBundleForPatient(String tenantId, String patientId) {
        ClinicalPatient patient = patients.findByTenantIdAndPatientId(tenantId, patientId)
            .orElseThrow(() -> new ApiException(ErrorCode.ENG_CONTEXT_001,
                "标准患者不存在: " + patientId));

        List<StandardClinicalFhirReference> references = new ArrayList<>();
        references.add(fhirMappings.reference(patient));
        encounters.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        conditions.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        observations.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        medications.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        procedures.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        diagnosticReports.findByTenantIdAndPatientId(tenantId, patientId)
            .forEach(item -> references.add(fhirMappings.reference(item)));
        documents.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        nursingAssessments.findByTenantIdAndPatientId(tenantId, patientId)
            .forEach(item -> references.add(fhirMappings.reference(item)));
        carePlans.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        followUps.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));
        claims.findByTenantIdAndPatientId(tenantId, patientId).forEach(item -> references.add(fhirMappings.reference(item)));

        return new StandardClinicalAuthorityBundle(
            tenantId,
            patientId,
            AUTHORITY_SOURCE_RELATIONAL_DATABASE,
            safeProjectionStatus(tenantId),
            references);
    }

    private ClinicalProjectionStatus safeProjectionStatus(String tenantId) {
        try {
            ClinicalProjectionStatus status = projectionStatus.status(tenantId);
            return status == null ? ClinicalProjectionStatus.NOT_SYNCED : status;
        } catch (RuntimeException exception) {
            log.warn("标准临床对象图投影状态查询失败，按关系库权威主链路降级 tenantId={}", tenantId, exception);
            return ClinicalProjectionStatus.NOT_SYNCED;
        }
    }
}
