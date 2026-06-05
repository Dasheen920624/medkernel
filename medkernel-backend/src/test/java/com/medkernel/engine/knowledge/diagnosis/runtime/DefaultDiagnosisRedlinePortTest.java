package com.medkernel.engine.knowledge.diagnosis.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.cdss.risk.CdssReviewRequirement;
import com.medkernel.engine.context.ContextSnapshotResources;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotStatus;
import com.medkernel.engine.context.QualityStatus;
import com.medkernel.engine.context.canonical.CanonicalPatient;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeDomain;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.recommendation.RecommendationRiskLevel;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.safety.ClinicalRedlineCategory;
import com.medkernel.engine.safety.ClinicalRedlineRepository;
import com.medkernel.engine.safety.ClinicalRedlineRule;
import com.medkernel.engine.safety.ClinicalRedlineStatus;

import org.junit.jupiter.api.Test;

/**
 * 红线合流端口：对 OPT-04 ACTIVE 红线按患者结构化上下文求值，命中且红线经 source_version_id
 * 关联到 DIAGNOSIS 身份时返回该诊断身份码（置顶）；非诊断来源 / 未命中 / 无来源 / 无红线均诚实返回空集。
 */
class DefaultDiagnosisRedlinePortTest {

    private static final String TENANT = "tenant-A";
    private static final String PLATFORM = "t-1";

    private final ClinicalRedlineRepository redlines = mock(ClinicalRedlineRepository.class);
    private final KnowledgeAssetVersionRepository versions = mock(KnowledgeAssetVersionRepository.class);
    private final KnowledgeIdentityRepository identities = mock(KnowledgeIdentityRepository.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final DefaultDiagnosisRedlinePort port = new DefaultDiagnosisRedlinePort(
        redlines, versions, identities, new RuleDslEvaluator(json), json);

    @Test
    void noActiveRedlinesYieldsEmpty() {
        stubActive(PLATFORM, List.of());
        stubActive(TENANT, List.of());

        assertThat(port.pinnedDiagnosisCodes(TENANT, snapshot("FEMALE"))).isEmpty();
    }

    @Test
    void diagnosisSourcedRedlineHitPinsItsDiagnosisCode() {
        stubActive(PLATFORM, List.of());
        stubActive(TENANT, List.of(redline(TENANT, "rdl-aortic", 42L, "FEMALE")));
        stubDiagnosis(TENANT, 42L, 100L, "DX.AORTIC");

        assertThat(port.pinnedDiagnosisCodes(TENANT, snapshot("FEMALE")))
            .containsExactly("DX.AORTIC");
    }

    @Test
    void nonDiagnosisSourcedRedlineNeverPins() {
        stubActive(PLATFORM, List.of());
        stubActive(TENANT, List.of(redline(TENANT, "rdl-guideline", 99L, "FEMALE")));
        // source 指向非 DIAGNOSIS 域（指南），即便 DSL 命中也不置顶任何诊断候选
        when(versions.findByTenantIdAndId(TENANT, 99L)).thenReturn(Optional.of(version(TENANT, 99L, 300L)));
        when(identities.findByTenantIdAndId(TENANT, 300L))
            .thenReturn(Optional.of(identity(TENANT, 300L, "GL.CHESTPAIN", KnowledgeDomain.GUIDELINE)));

        assertThat(port.pinnedDiagnosisCodes(TENANT, snapshot("FEMALE"))).isEmpty();
    }

    @Test
    void diagnosisSourcedRedlineMissIsNotPinned() {
        stubActive(PLATFORM, List.of());
        // 红线 DSL 要求 gender=MALE，但患者为 FEMALE → 未命中 → 不置顶
        stubActive(TENANT, List.of(redline(TENANT, "rdl-aortic", 42L, "MALE")));
        stubDiagnosis(TENANT, 42L, 100L, "DX.AORTIC");

        assertThat(port.pinnedDiagnosisCodes(TENANT, snapshot("FEMALE"))).isEmpty();
    }

    @Test
    void redlineWithoutSourceVersionNeverPins() {
        stubActive(PLATFORM, List.of());
        stubActive(TENANT, List.of(redline(TENANT, "rdl-nosource", null, "FEMALE")));

        assertThat(port.pinnedDiagnosisCodes(TENANT, snapshot("FEMALE"))).isEmpty();
    }

    @Test
    void platformDiagnosisRedlinePinsForTenantWithoutLocalRedline() {
        stubActive(TENANT, List.of());
        stubActive(PLATFORM, List.of(redline(PLATFORM, "platform-rdl-aortic", 42L, "FEMALE")));
        stubDiagnosis(PLATFORM, 42L, 100L, "DX.AORTIC");

        assertThat(port.pinnedDiagnosisCodes(TENANT, snapshot("FEMALE")))
            .containsExactly("DX.AORTIC");
    }

    private void stubActive(String tenantId, List<ClinicalRedlineRule> rules) {
        when(redlines.findByTenantIdAndStatusOrderByCategoryAscRedlineKeyAscUpdatedAtDesc(
            tenantId, ClinicalRedlineStatus.ACTIVE)).thenReturn(rules);
    }

    private void stubDiagnosis(String tenantId, Long versionId, Long identityId, String code) {
        when(versions.findByTenantIdAndId(tenantId, versionId))
            .thenReturn(Optional.of(version(tenantId, versionId, identityId)));
        when(identities.findByTenantIdAndId(tenantId, identityId))
            .thenReturn(Optional.of(identity(tenantId, identityId, code, KnowledgeDomain.DIAGNOSIS)));
    }

    private ContextSnapshotResponse snapshot(String gender) {
        CanonicalPatient patient = new CanonicalPatient(
            "mpi-1", "测试患者", null, gender, List.of(), List.of(),
            "HIS", "patient-1", "v1", Instant.now(), Instant.now(), QualityStatus.VALID);
        ContextSnapshotResources resources = new ContextSnapshotResources(
            patient, List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new ContextSnapshotResponse(
            "snapshot-1", ContextSnapshotStatus.ACTIVE, resources,
            "1.0.0", "knowledge-1", "rule-1", "pathway-1",
            QualityStatus.VALID, List.of(), Map.of(), Instant.now(), "trace-redline-port");
    }

    private ClinicalRedlineRule redline(String tenantId, String redlineId, Long sourceVersionId, String gender) {
        Instant now = Instant.parse("2026-06-05T02:00:00Z");
        return new ClinicalRedlineRule(
            null, redlineId, tenantId,
            ClinicalRedlineCategory.CRITICAL_VALUE,
            "patient-view", "TENANT", tenantId,
            tenantId + "|CRITICAL_VALUE|patient-view|" + redlineId,
            redlineId, "2026.1",
            ClinicalRedlineStatus.ACTIVE,
            RecommendationRiskLevel.CRITICAL,
            "risk-matrix-critical", "4",
            CdssReviewRequirement.DUAL_REVIEW, 0, "OPT04_REDLINE_RUNTIME_GUARD",
            "致命病不可漏：主动脉夹层", "漏诊致命",
            """
            {
              "when": { "fact": "patient.gender", "operator": "equals", "value": "%s" },
              "then": [ { "actionCode": "CLINICAL_REDLINE", "severity": "CRITICAL", "message": "红线命中" } ]
            }
            """.formatted(gender),
            "诊断知识与指南证据", "source-version#sec-1", sourceVersionId,
            false, now, "tester", now, "tester", "trace-redline");
    }

    private KnowledgeAssetVersion version(String tenantId, Long id, Long identityId) {
        Instant now = Instant.now();
        return new KnowledgeAssetVersion(id, tenantId, identityId, "v1.0", null, null, null, "h" + id, "[]",
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION,
            GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, null,
            "tenant:" + tenantId, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE, "scope-" + id,
            null, null, null, null, now, null, null, null, now, "system", now, "system");
    }

    private KnowledgeIdentity identity(String tenantId, Long id, String code, KnowledgeDomain domain) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(id, tenantId, code, domain, "主体", null, null,
            KnowledgeIdentityStatus.ACTIVE, null, now, "system", now, "system");
    }
}
