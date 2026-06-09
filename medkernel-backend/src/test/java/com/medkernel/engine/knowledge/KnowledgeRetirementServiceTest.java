package com.medkernel.engine.knowledge;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.engine.versioning.InheritanceOverrideRepository;
import com.medkernel.engine.versioning.InheritanceOverrideStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetirementServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");

    private KnowledgeIdentityRepository identities;
    private KnowledgeAssetVersionRepository versions;
    private KnowledgeSupersessionRepository supersessions;
    private InheritanceOverrideRepository overrides;
    private KnowledgeRetirementService service;

    @BeforeEach
    void setUp() {
        identities = Mockito.mock(KnowledgeIdentityRepository.class);
        versions = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessions = Mockito.mock(KnowledgeSupersessionRepository.class);
        overrides = Mockito.mock(InheritanceOverrideRepository.class);
        service = new KnowledgeRetirementService(
            identities, versions, supersessions, overrides,
            Clock.fixed(NOW, ZoneOffset.UTC));
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "platform-admin"));
        when(identities.save(any(KnowledgeIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.save(any(KnowledgeAssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supersessions.save(any(KnowledgeSupersession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(overrides.save(any(InheritanceOverride.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void deprecateRequiresSuccessorAndKeepsCurrentVersionDuringGracePeriod() {
        KnowledgeIdentity current = identity(1L, "plat:drug:old-guide", KnowledgeIdentityStatus.ACTIVE, 10L);
        KnowledgeIdentity successor = identity(2L, "plat:drug:new-guide", KnowledgeIdentityStatus.ACTIVE, 20L);
        when(identities.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(current));
        when(identities.findByTenantIdAndId("t-1", 2L)).thenReturn(Optional.of(successor));

        KnowledgeSupersession result = service.deprecate(
            1L,
            new KnowledgeRetirementRequest(
                2L, NOW.plusSeconds(86400L * 30), "请迁移到新版用药指南"));

        ArgumentCaptor<KnowledgeIdentity> identity = ArgumentCaptor.forClass(KnowledgeIdentity.class);
        verify(identities).save(identity.capture());
        assertThat(identity.getValue().status()).isEqualTo(KnowledgeIdentityStatus.DEPRECATED);
        assertThat(identity.getValue().currentVersionId()).isEqualTo(10L);
        assertThat(result.transitionType()).isEqualTo(SupersessionType.DEPRECATE);
        assertThat(result.successorIdentityId()).isEqualTo(2L);
        assertThat(result.gracePeriodEnd()).isEqualTo(NOW.plusSeconds(86400L * 30));
        assertThat(result.migrationGuidance()).isEqualTo("请迁移到新版用药指南");
    }

    @Test
    void finalizationWithdrawsVersionAndSuspendsPublishedTenantOverridesForMigration() {
        KnowledgeSupersession due = new KnowledgeSupersession(
            100L, "t-1", 1L, 10L, null, SupersessionType.DEPRECATE, "计划弃用",
            NOW.minusSeconds(86400), "platform-admin", 2L, NOW.minusSeconds(1), "请迁移到新版指南");
        KnowledgeIdentity current = identity(1L, "plat:drug:old-guide", KnowledgeIdentityStatus.DEPRECATED, 10L);
        KnowledgeAssetVersion active = version(10L, 1L);
        InheritanceOverride published = override("tenant-a", "plat:drug:old-guide");
        InheritanceOverride publishedWithoutReason = new InheritanceOverride(
            8L, "io-8", "tenant-b", VersionedAssetType.KNOWLEDGE, "plat:drug:old-guide",
            "av-platform", "av-local-b",
            com.medkernel.engine.versioning.InheritanceOverrideMode.REPLACE,
            com.medkernel.engine.versioning.InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.PUBLISHED, "/hospital-b", "ALL", "本地差异", null,
            "医院范围", NOW.minusSeconds(86400), "tenant-admin", NOW.minusSeconds(86400),
            "tenant-admin", "trace-b");
        when(supersessions.findDueDeprecations(NOW)).thenReturn(List.of(due));
        when(identities.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(current));
        when(versions.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(active));
        when(overrides.findByAssetTypeAndAssetIdentityAndLifecycleStatus(
            VersionedAssetType.KNOWLEDGE, "plat:drug:old-guide", InheritanceOverrideStatus.PUBLISHED))
            .thenReturn(List.of(published, publishedWithoutReason));

        int finalized = service.finalizeDueRetirements();

        assertThat(finalized).isEqualTo(1);
        ArgumentCaptor<KnowledgeAssetVersion> version = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versions).save(version.capture());
        assertThat(version.getValue().status()).isEqualTo(KnowledgeVersionStatus.WITHDRAWN);
        assertThat(version.getValue().withdrawnReason()).contains("请迁移到新版指南");
        ArgumentCaptor<KnowledgeIdentity> identity = ArgumentCaptor.forClass(KnowledgeIdentity.class);
        verify(identities).save(identity.capture());
        assertThat(identity.getValue().status()).isEqualTo(KnowledgeIdentityStatus.WITHDRAWN);
        assertThat(identity.getValue().currentVersionId()).isNull();
        ArgumentCaptor<InheritanceOverride> override = ArgumentCaptor.forClass(InheritanceOverride.class);
        verify(overrides, Mockito.times(2)).save(override.capture());
        assertThat(override.getAllValues())
            .allSatisfy(item -> {
                assertThat(item.lifecycleStatus()).isEqualTo(InheritanceOverrideStatus.DEPRECATED);
                assertThat(item.overrideReason()).contains("请迁移到新版指南");
            });
        assertThat(override.getAllValues().get(1).overrideReason()).doesNotStartWith("null");
        ArgumentCaptor<KnowledgeSupersession> transition = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessions).save(transition.capture());
        assertThat(transition.getValue().transitionType()).isEqualTo(SupersessionType.RETIRE);
    }

    @Test
    void scheduledFinalizationWritesAuditInRetiredIdentityTenantContext() {
        AuditRecorder audit = Mockito.mock(AuditRecorder.class);
        service = new KnowledgeRetirementService(
            identities, versions, supersessions, overrides, audit,
            Clock.fixed(NOW, ZoneOffset.UTC));
        KnowledgeSupersession due = new KnowledgeSupersession(
            100L, "t-1", 1L, null, 20L, SupersessionType.DEPRECATE, "计划弃用",
            NOW.minusSeconds(86400), "platform-admin", 2L, NOW.minusSeconds(1), "请迁移到新版指南");
        KnowledgeIdentity current =
            identity(1L, "plat:drug:old-guide", KnowledgeIdentityStatus.DEPRECATED, null);
        when(supersessions.findDueDeprecations(NOW)).thenReturn(List.of(due));
        when(identities.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(current));
        when(overrides.findByAssetTypeAndAssetIdentityAndLifecycleStatus(
            VersionedAssetType.KNOWLEDGE, "plat:drug:old-guide", InheritanceOverrideStatus.PUBLISHED))
            .thenReturn(List.of());
        AtomicReference<String> auditTenant = new AtomicReference<>();
        AtomicReference<String> auditActor = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            auditTenant.set(RequestContext.currentOrgScope().tenantId());
            auditActor.set(RequestContext.currentUserId().orElse(null));
            return null;
        }).when(audit).record(any(), any(), any(), any());
        RequestContext.clear();

        service.finalizeDueRetirements();

        assertThat(auditTenant).hasValue("t-1");
        assertThat(auditActor).hasValue("system:knowledge-retirement");
    }

    private KnowledgeIdentity identity(Long id, String code, KnowledgeIdentityStatus status, Long currentVersionId) {
        return new KnowledgeIdentity(
            id, "t-1", code, KnowledgeDomain.DRUG, "测试知识", null, null, status, currentVersionId,
            NOW.minusSeconds(86400), "platform-admin", NOW.minusSeconds(86400), "platform-admin");
    }

    private KnowledgeAssetVersion version(Long id, Long identityId) {
        return new KnowledgeAssetVersion(
            id, "t-1", identityId, "v1", "当前版", null, null, "a".repeat(64), null,
            KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.B_GUIDELINE,
            GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG, null,
            "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(identityId, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            NOW.minusSeconds(86400), null, "reviewer", NOW.minusSeconds(86400),
            NOW.minusSeconds(86400), null, null, null,
            NOW.minusSeconds(86400), "platform-admin", NOW.minusSeconds(86400), "platform-admin",
            12, NOW.plusSeconds(86400L * 365));
    }

    private InheritanceOverride override(String tenantId, String identityCode) {
        return new InheritanceOverride(
            7L, "io-7", tenantId, VersionedAssetType.KNOWLEDGE, identityCode, "av-platform", "av-local",
            com.medkernel.engine.versioning.InheritanceOverrideMode.REPLACE,
            com.medkernel.engine.versioning.InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.PUBLISHED, "/hospital-a", "ALL", "本地差异", "本地适配",
            "医院范围", NOW.minusSeconds(86400), "tenant-admin", NOW.minusSeconds(86400), "tenant-admin", "trace");
    }
}
