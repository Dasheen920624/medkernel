package com.medkernel.engine.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.cdss.risk.CdssAutomationLevel;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.knowledge.AffectedCaseTargetType;
import com.medkernel.engine.knowledge.AffectedCaseTask;
import com.medkernel.engine.knowledge.AffectedCaseTaskRepository;
import com.medkernel.engine.knowledge.AffectedCaseTaskStatus;
import com.medkernel.engine.knowledge.AffectedCaseTaskType;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeInvalidation;
import com.medkernel.engine.knowledge.KnowledgeInvalidationRepository;
import com.medkernel.engine.knowledge.KnowledgeInvalidationStatus;
import com.medkernel.engine.knowledge.KnowledgeInvalidationType;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.pathway.PatientPathway;
import com.medkernel.engine.pathway.PatientPathwayRepository;
import com.medkernel.engine.pathway.PatientPathwayStatus;
import com.medkernel.engine.pathway.PathwayTemplate;
import com.medkernel.engine.pathway.PathwayTemplateLevel;
import com.medkernel.engine.pathway.PathwayTemplateRepository;
import com.medkernel.engine.pathway.PathwayTemplateStatus;
import com.medkernel.engine.recommendation.RecommendationCard;
import com.medkernel.engine.recommendation.RecommendationCardRepository;
import com.medkernel.engine.recommendation.RecommendationCardStatus;
import com.medkernel.engine.recommendation.RecommendationCardType;
import com.medkernel.engine.recommendation.RecommendationInterruptLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.recommendation.RecommendationSource;
import com.medkernel.engine.recommendation.RecommendationSourceRepository;
import com.medkernel.engine.recommendation.RecommendationSourceType;
import com.medkernel.engine.recommendation.RecommendationTrigger;
import com.medkernel.engine.recommendation.RecommendationTriggerRepository;
import com.medkernel.engine.recommendation.RecommendationTriggerStatus;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SafetyWithdrawalServiceTest {

    private KnowledgeVersionService knowledgeVersions;
    private KnowledgeInvalidationRepository invalidations;
    private AffectedCaseTaskRepository affectedTasks;
    private RecommendationSourceRepository recommendationSources;
    private RecommendationCardRepository recommendationCards;
    private RecommendationTriggerRepository recommendationTriggers;
    private PathwayTemplateRepository pathwayTemplates;
    private PatientPathwayRepository patientPathways;
    private AuditRecorder auditRecorder;
    private SafetyWithdrawalService service;

    @BeforeEach
    void setUp() {
        knowledgeVersions = mock(KnowledgeVersionService.class);
        invalidations = mock(KnowledgeInvalidationRepository.class);
        affectedTasks = mock(AffectedCaseTaskRepository.class);
        recommendationSources = mock(RecommendationSourceRepository.class);
        recommendationCards = mock(RecommendationCardRepository.class);
        recommendationTriggers = mock(RecommendationTriggerRepository.class);
        pathwayTemplates = mock(PathwayTemplateRepository.class);
        patientPathways = mock(PatientPathwayRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        ObjectMapper json = new ObjectMapper();
        json.findAndRegisterModules();
        service = new SafetyWithdrawalService(knowledgeVersions, invalidations, affectedTasks,
            recommendationSources, recommendationCards, recommendationTriggers, pathwayTemplates, patientPathways,
            auditRecorder, json);
        when(affectedTasks.findByTenantIdAndTaskKey(any(), any())).thenReturn(Optional.empty());
        when(affectedTasks.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-safety", OrgScope.tenant("tenant-A"), "medical-affairs"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void withdrawCreatesRealPatientCaseAndPathwayReviewTasksIdempotently() {
        KnowledgeAssetVersion withdrawn = version(5L, 1L, KnowledgeVersionStatus.WITHDRAWN, "v1");
        KnowledgeInvalidation invalidation = invalidation(90L, 1L, 5L);
        when(knowledgeVersions.withdraw(1L, 5L, "上游说明书新增禁忌")).thenReturn(withdrawn);
        when(invalidations.findByTenantIdAndVersionIdOrderByInvalidatedAtDesc("tenant-A", 5L))
            .thenReturn(List.of(invalidation));
        when(recommendationSources.findByTenantIdAndSourceTypeAndSourceRefIdOrderByCreatedAtAsc(
                "tenant-A", RecommendationSourceType.KNOWLEDGE, "knowledge-version:5"))
            .thenReturn(List.of(recommendationSource("src-1", "card-1", "knowledge-version:5")));
        when(recommendationCards.findByCardIdAndTenantId("card-1", "tenant-A"))
            .thenReturn(Optional.of(recommendationCard("card-1", "trigger-1")));
        when(recommendationTriggers.findByTriggerIdAndTenantId("trigger-1", "tenant-A"))
            .thenReturn(Optional.of(recommendationTrigger("trigger-1", "patient-1", "enc-1")));
        when(pathwayTemplates.findByTenantIdAndSourceRef("tenant-A", "knowledge-version:5"))
            .thenReturn(List.of(pathwayTemplate("pt-1", "knowledge-version:5")));
        when(patientPathways.findByTemplateIdAndTenantIdOrderByEnteredAtDesc("pt-1", "tenant-A"))
            .thenReturn(List.of(
                patientPathway("pp-active", PatientPathwayStatus.NODE_EXECUTING),
                patientPathway("pp-done", PatientPathwayStatus.COMPLETED)));
        when(affectedTasks.findByTenantIdAndInvalidationIdOrderByCreatedAtAsc("tenant-A", 90L))
            .thenReturn(List.of(
                task(AffectedCaseTaskType.PHYSICIAN_REVIEW, AffectedCaseTargetType.PATIENT_CASE,
                    "patient:patient-1/encounter:enc-1/card:card-1"),
                task(AffectedCaseTaskType.PHYSICIAN_REVIEW, AffectedCaseTargetType.PATIENT_PATHWAY,
                    "patient-pathway:pp-active/template:pt-1"),
                task(AffectedCaseTaskType.SYNC_ALERT, AffectedCaseTargetType.SYNC_TARGET,
                    "sync-target/version:5")));

        SafetyWithdrawalResponse response = service.withdraw(new SafetyWithdrawalRequest(
            1L, 5L, "上游说明书新增禁忌"));

        assertThat(response.withdrawalId()).isEqualTo(90L);
        assertThat(response.versionStatus()).isEqualTo(KnowledgeVersionStatus.WITHDRAWN.name());
        assertThat(response.impact().patientCaseCount()).isEqualTo(1);
        assertThat(response.impact().patientPathwayCount()).isEqualTo(1);
        assertThat(response.impact().syncTargetCount()).isEqualTo(1);
        assertThat(response.impact().impactDigest()).startsWith("sha256:");
        ArgumentCaptor<AffectedCaseTask> taskCaptor = ArgumentCaptor.forClass(AffectedCaseTask.class);
        verify(affectedTasks, org.mockito.Mockito.times(2)).save(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues()).extracting(AffectedCaseTask::targetType)
            .containsExactlyInAnyOrder(AffectedCaseTargetType.PATIENT_CASE, AffectedCaseTargetType.PATIENT_PATHWAY);
        assertThat(taskCaptor.getAllValues()).extracting(AffectedCaseTask::targetRef)
            .contains("patient:patient-1/encounter:enc-1/card:card-1", "patient-pathway:pp-active/template:pt-1")
            .doesNotContain("patient-pathway:pp-done/template:pt-1");
        verify(auditRecorder).record(AuditAction.PUBLISH, "safety_withdrawal", "90",
            "安全撤回已隔离 versionId=5 affectedTasks=3");
    }

    @Test
    void withdrawDoesNotDuplicateExistingPatientCaseTask() {
        KnowledgeAssetVersion withdrawn = version(5L, 1L, KnowledgeVersionStatus.WITHDRAWN, "v1");
        KnowledgeInvalidation invalidation = invalidation(90L, 1L, 5L);
        when(knowledgeVersions.withdraw(1L, 5L, "重复执行核对")).thenReturn(withdrawn);
        when(invalidations.findByTenantIdAndVersionIdOrderByInvalidatedAtDesc("tenant-A", 5L))
            .thenReturn(List.of(invalidation));
        when(recommendationSources.findByTenantIdAndSourceTypeAndSourceRefIdOrderByCreatedAtAsc(
                "tenant-A", RecommendationSourceType.KNOWLEDGE, "knowledge-version:5"))
            .thenReturn(List.of(recommendationSource("src-1", "card-1", "knowledge-version:5")));
        when(recommendationCards.findByCardIdAndTenantId("card-1", "tenant-A"))
            .thenReturn(Optional.of(recommendationCard("card-1", "trigger-1")));
        when(recommendationTriggers.findByTriggerIdAndTenantId("trigger-1", "tenant-A"))
            .thenReturn(Optional.of(recommendationTrigger("trigger-1", "patient-1", "enc-1")));
        when(affectedTasks.findByTenantIdAndTaskKey(eq("tenant-A"), any()))
            .thenReturn(Optional.of(task(AffectedCaseTaskType.PHYSICIAN_REVIEW, AffectedCaseTargetType.PATIENT_CASE,
                "patient:patient-1/encounter:enc-1/card:card-1")));
        when(affectedTasks.findByTenantIdAndInvalidationIdOrderByCreatedAtAsc("tenant-A", 90L))
            .thenReturn(List.of());

        service.withdraw(new SafetyWithdrawalRequest(1L, 5L, "重复执行核对"));

        verify(affectedTasks, never()).save(any());
    }

    @Test
    void withdrawDoesNotReuseExistingInvalidationForDifferentIdentity() {
        KnowledgeInvalidation invalidation = invalidation(90L, 1L, 5L);
        ApiException conflict = new ApiException(ErrorCode.CONFLICT, "知识版本已撤回");
        when(knowledgeVersions.withdraw(2L, 5L, "重复执行核对")).thenThrow(conflict);
        when(invalidations.findByTenantIdAndVersionIdOrderByInvalidatedAtDesc("tenant-A", 5L))
            .thenReturn(List.of(invalidation));

        assertThatThrownBy(() -> service.withdraw(new SafetyWithdrawalRequest(2L, 5L, "重复执行核对")))
            .isSameAs(conflict);
        verify(affectedTasks, never()).save(any());
    }

    @Test
    void exportImpactEvidenceReturnsDeterministicNdjsonAndPublishesExportAudit() {
        KnowledgeInvalidation invalidation = invalidation(90L, 1L, 5L);
        when(invalidations.findById(90L)).thenReturn(Optional.of(invalidation));
        when(affectedTasks.findByTenantIdAndInvalidationIdOrderByCreatedAtAsc("tenant-A", 90L))
            .thenReturn(List.of(
                task(AffectedCaseTaskType.PHYSICIAN_REVIEW, AffectedCaseTargetType.PATIENT_CASE,
                    "patient:patient-1/encounter:enc-1/card:card-1"),
                task(AffectedCaseTaskType.SYNC_ALERT, AffectedCaseTargetType.SYNC_TARGET,
                    "sync-target/version:5")));

        String evidence = service.exportImpactEvidence(90L);

        assertThat(evidence).contains("\"recordType\":\"summary\"");
        assertThat(evidence).contains("\"recordType\":\"task\"");
        assertThat(evidence).contains("\"patientCaseCount\":1");
        assertThat(evidence).contains("\"targetRef\":\"patient:patient-1/encounter:enc-1/card:card-1\"");
        verify(auditRecorder).record(AuditAction.EXPORT, "safety_withdrawal", "90",
            "导出安全撤回影响证据 taskCount=2");
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status, String versionNo) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(
            id, "tenant-A", identityId, versionNo, "抗凝禁忌指南", 1L, 1L, "sha256:version",
            "anchors", status, KnowledgeRiskLevel.HIGH, SourceAuthorityLevel.B_GUIDELINE,
            null, null, null, "tenant:tenant-A", "ALL", "version:" + id,
            now.minusSeconds(3600), now, "reviewer", now.minusSeconds(1800), now.minusSeconds(1200),
            null, now, "上游说明书新增禁忌", now.minusSeconds(7200), "creator", now, "medical-affairs");
    }

    private KnowledgeInvalidation invalidation(Long id, Long identityId, Long versionId) {
        Instant now = Instant.now();
        return new KnowledgeInvalidation(id, "tenant-A", identityId, versionId,
            KnowledgeInvalidationType.EMERGENCY_WITHDRAW, KnowledgeInvalidationStatus.OPEN, KnowledgeRiskLevel.HIGH,
            "上游说明书新增禁忌", "tenant:tenant-A", "ALL", "medical-affairs", now, true,
            "trace-safety", now, "medical-affairs", now, "medical-affairs");
    }

    private RecommendationSource recommendationSource(String sourceId, String cardId, String sourceRefId) {
        Instant now = Instant.now();
        return new RecommendationSource(1L, sourceId, "tenant-A", cardId, RecommendationSourceType.KNOWLEDGE,
            sourceRefId, "v1", "抗凝禁忌指南", "§禁忌", "sha256:source",
            "旧版知识命中", now, "doctor", now, "doctor", "trace-rec");
    }

    private RecommendationCard recommendationCard(String cardId, String triggerId) {
        Instant now = Instant.now();
        return new RecommendationCard(1L, cardId, "tenant-A", triggerId, "CARD.WITHDRAWN",
            RecommendationCardType.MEDICATION, "禁忌提醒", "命中旧版禁忌", "请复核",
            RecommendationRiskLevel.HIGH, RecommendationInterruptLevel.WEAK_INTERRUPTIVE,
            RecommendationCardStatus.PENDING, true, false, "旧版知识", "{}",
            "PATIENT:ANTICOAG", now.plusSeconds(3600), now, "doctor", now, "doctor", "trace-rec",
            "builtin-risk-baseline", "baseline", CdssAutomationLevel.INTERRUPTIVE,
            CdssReviewRequirement.PHYSICIAN_CONFIRMATION, 72, "OPT04_SILENT_TRIAL",
            false, "NMPA_RESERVED", "TRACEABLE_EVIDENCE_REQUIRED", "高危 CDSS 输出必须医师确认");
    }

    private RecommendationTrigger recommendationTrigger(String triggerId, String patientId, String encounterId) {
        Instant now = Instant.now();
        return new RecommendationTrigger(1L, triggerId, "tenant-A", "TRG.ORDER", "order-sign",
            "event-1", "snapshot-1", patientId, encounterId, "pp-1", "WARD_ORDER", "1.0.0",
            "sha256:trigger", RecommendationTriggerStatus.EVALUATED, null, now, now, "doctor", now, "doctor",
            "trace-rec");
    }

    private PathwayTemplate pathwayTemplate(String templateId, String sourceRef) {
        Instant now = Instant.now();
        return new PathwayTemplate(1L, templateId, "tenant-A", "sp-1", "TPL.COPD",
            "稳定期路径", "COPD", 1, PathwayTemplateLevel.STANDARD, PathwayTemplateStatus.PUBLISHED,
            "ASSESS", sourceRef, "路径引用知识版本", "{}", "{}", now, "planner", now, "planner", "trace-path");
    }

    private PatientPathway patientPathway(String patientPathwayId, PatientPathwayStatus status) {
        Instant now = Instant.now();
        return new PatientPathway(1L, patientPathwayId, "tenant-A", "patient-1", "enc-1", "pt-1",
            "ASSESS", status, now.minusSeconds(3600), null, null, null, "event-1",
            now.minusSeconds(3600), "doctor", now, "doctor", "trace-path");
    }

    private AffectedCaseTask task(AffectedCaseTaskType taskType, AffectedCaseTargetType targetType, String targetRef) {
        Instant now = Instant.now();
        return new AffectedCaseTask(1L, "tenant-A", "task-key-" + targetRef, 90L, 1L, 5L,
            taskType, AffectedCaseTaskStatus.OPEN, targetType, targetRef, "上游说明书新增禁忌",
            now.plusSeconds(86_400), "medical-affairs", "trace-safety", now, "medical-affairs", now,
            "medical-affairs");
    }
}
