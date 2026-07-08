package com.medkernel.engine.knowledge;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.AssetOwnershipScope;
import com.medkernel.engine.versioning.AssetScopeResolver;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.VersionPublishEvidence;
import com.medkernel.engine.versioning.VersionPublishQualityGate;
import com.medkernel.engine.versioning.VersionRollbackCommand;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.security.EffectivePermissionService;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.knowledge.production.gate.PublicationQualityRecordService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KnowledgeVersionService 单元测试。重点覆盖 activate 原子替换 + withdraw 状态机门禁。
 */
class KnowledgeVersionServiceTest {

    private KnowledgeIdentityRepository identityRepo;
    private KnowledgeAssetVersionRepository versionRepo;
    private KnowledgeSupersessionRepository supersessionRepo;
    private CitationRepository citationRepo;
    private SourceDocumentRepository sourceDocRepo;
    private SourceVersionRepository sourceVersionRepo;
    private KnowledgeProjectionRefreshPort projectionRefreshPort;
    private CandidateClassificationRepository candidateClassificationRepo;
    private ReviewAssignmentRepository reviewAssignmentRepo;
    private KnowledgeInvalidationRepository invalidationRepo;
    private AffectedCaseTaskRepository affectedCaseTaskRepo;
    private KnowledgeVersionedAssetAdapter versionedAssets;
    private AssetVersionRepository assetVersions;
    private ReleasePort releasePort;
    private PublicationQualityRecordService publicationQualityRecords;
    private AssetScopeResolver assetScopes;
    private UserRoleAssignmentRepository userRoleAssignments;
    private AuditRecorder auditRecorder;
    private KnowledgeVersionService service;

    @BeforeEach
    void setUp() {
        identityRepo = Mockito.mock(KnowledgeIdentityRepository.class);
        versionRepo = Mockito.mock(KnowledgeAssetVersionRepository.class);
        supersessionRepo = Mockito.mock(KnowledgeSupersessionRepository.class);
        citationRepo = Mockito.mock(CitationRepository.class);
        sourceDocRepo = Mockito.mock(SourceDocumentRepository.class);
        sourceVersionRepo = Mockito.mock(SourceVersionRepository.class);
        projectionRefreshPort = Mockito.mock(KnowledgeProjectionRefreshPort.class);
        candidateClassificationRepo = Mockito.mock(CandidateClassificationRepository.class);
        reviewAssignmentRepo = Mockito.mock(ReviewAssignmentRepository.class);
        invalidationRepo = Mockito.mock(KnowledgeInvalidationRepository.class);
        affectedCaseTaskRepo = Mockito.mock(AffectedCaseTaskRepository.class);
        versionedAssets = Mockito.mock(KnowledgeVersionedAssetAdapter.class);
        assetVersions = Mockito.mock(AssetVersionRepository.class);
        releasePort = Mockito.mock(ReleasePort.class);
        publicationQualityRecords = Mockito.mock(PublicationQualityRecordService.class);
        assetScopes = Mockito.mock(AssetScopeResolver.class);
        userRoleAssignments = Mockito.mock(UserRoleAssignmentRepository.class);
        auditRecorder = Mockito.mock(AuditRecorder.class);
        service = new KnowledgeVersionService(
            identityRepo, versionRepo, supersessionRepo, citationRepo, sourceDocRepo, sourceVersionRepo, projectionRefreshPort,
            candidateClassificationRepo, reviewAssignmentRepo, invalidationRepo, affectedCaseTaskRepo,
            versionedAssets, assetVersions, releasePort, publicationQualityRecords, assetScopes,
            new EffectivePermissionService(userRoleAssignments), auditRecorder);
        when(userRoleAssignments.findActiveByTenantIdAndUserId(any(), any())).thenReturn(List.of());
        when(assetScopes.resolve(any(), any(OrgScope.class)))
            .thenAnswer(invocation -> {
                String tenantId = invocation.getArgument(0);
                return "t-1".equals(tenantId)
                    ? new AssetOwnershipScope(
                        ReleaseSourceLayer.PLATFORM, "/__platform__")
                    : new AssetOwnershipScope(
                        ReleaseSourceLayer.HOSPITAL, "/" + tenantId + "/h-1");
            });
        when(publicationQualityRecords.requirePublishEvidence(any(), any(), any()))
            .thenReturn(new VersionPublishEvidence(new VersionPublishQualityGate(
                true, true, true, true, true, "服务端质量门测试记录")));
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u-99"));

        // 默认 save 返回参数，方便断言保留字段
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identityRepo.save(any(KnowledgeIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(supersessionRepo.save(any(KnowledgeSupersession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewAssignmentRepo.save(any(ReviewAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invalidationRepo.save(any(KnowledgeInvalidation.class))).thenAnswer(inv -> {
            KnowledgeInvalidation item = inv.getArgument(0);
            return new KnowledgeInvalidation(
                item.id() == null ? 77L : item.id(),
                item.tenantId(), item.identityId(), item.versionId(), item.invalidationType(), item.status(),
                item.riskLevel(), item.reason(), item.organizationScope(), item.applicableScope(), item.authorizedBy(),
                item.invalidatedAt(), item.expeditedReviewRequired(), item.traceId(),
                item.createdAt(), item.createdBy(), item.updatedAt(), item.updatedBy());
        });
        when(affectedCaseTaskRepo.findByTenantIdAndTaskKey(any(), any())).thenReturn(Optional.empty());
        when(affectedCaseTaskRepo.save(any(AffectedCaseTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            any(), eq(VersionedAssetType.KNOWLEDGE), any(), any()))
            .thenAnswer(inv -> Optional.of(unifiedVersion(
                inv.getArgument(2), inv.getArgument(3), AssetVersionStatus.DRAFT)));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    @Test
    void knowledgeAssetVersionDeclaresEffectiveScopeFieldsAndScopedActiveLookup() {
        assertThat(java.util.Arrays.stream(KnowledgeAssetVersion.class.getRecordComponents())
            .map(RecordComponent::getName))
            .contains("organizationScope", "applicableScope", "activeScopeKey",
                "reviewCycleMonths", "nextReviewAt");
        assertThat(java.util.Arrays.stream(KnowledgeAssetVersionRepository.class.getMethods())
            .map(method -> method.getName()))
            .contains("findActiveByEffectiveScope", "pageReviewDueByTenantId", "countReviewDueByTenantId");
    }

    @Test
    void knowledgeAssetVersionNeverFabricatesAMissingOrganizationOwner() {
        KnowledgeAssetVersion invalid = version(
            10L,
            "tenant-A",
            1L,
            KnowledgeVersionStatus.CANDIDATE,
            KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE,
            null,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE
        );

        assertThatThrownBy(invalid::effectiveOrganizationScope)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("组织归属");
        assertThatThrownBy(() -> KnowledgeAssetVersion.activeScopeKey(
            1L, null, KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("组织归属");
    }

    @Test
    void activateFirstVersionTransitionsToActiveAndWritesSupersession() {
        // 给一个无 active 版本的身份 + 一个 UNDER_REVIEW 候选版本
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion candidate = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.empty());
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 10L))
            .thenReturn(List.of(citation(10L)));

        KnowledgeAssetVersion activated = service.activate(1L, 10L, null, 900L);

        // 1) 目标版本变 ACTIVE
        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        assertThat(activated.activatedAt()).isNotNull();
        assertThat(activated.reviewedBy()).isEqualTo("u-99");
        assertThat(activated.reviewCycleMonths()).isEqualTo(12);
        assertThat(activated.nextReviewAt())
            .isCloseTo(
                activated.reviewedAt().atZone(ZoneOffset.UTC).plusMonths(12).toInstant(),
                org.assertj.core.api.Assertions.within(1, ChronoUnit.SECONDS));

        // 2) 没有旧 ACTIVE，因此不会保存 SUPERSEDED
        verify(versionRepo, times(1)).save(any(KnowledgeAssetVersion.class));

        // 3) 身份 current_version_id 指向新版
        ArgumentCaptor<KnowledgeIdentity> idCap = ArgumentCaptor.forClass(KnowledgeIdentity.class);
        verify(identityRepo).save(idCap.capture());
        assertThat(idCap.getValue().currentVersionId()).isEqualTo(10L);

        // 4) supersession 记录 type=ACTIVATE
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.ACTIVATE);
        assertThat(spCap.getValue().oldVersionId()).isNull();
        assertThat(spCap.getValue().newVersionId()).isEqualTo(10L);
        assertThat(spCap.getValue().transitionedBy()).isEqualTo("u-99");
        verify(releasePort).submitForReview(any());
        verify(releasePort).approveReview(any());
        verify(releasePort).publish(any());
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 10L, "u-99", "trace");
    }

    @Test
    void activateReplacingPriorActiveDemotesItToSuperseded() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion newCandidate = version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(1L, 11L, "新版指南更新", 900L);

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());

        // 第一次 save 应该是旧版变 SUPERSEDED
        KnowledgeAssetVersion superseded = vCap.getAllValues().get(0);
        assertThat(superseded.id()).isEqualTo(5L);
        assertThat(superseded.status()).isEqualTo(KnowledgeVersionStatus.SUPERSEDED);
        assertThat(superseded.supersededAt()).isNotNull();
        assertThat(superseded.effectiveTo()).isNotNull();

        // 第二次是新版变 ACTIVE
        KnowledgeAssetVersion activated = vCap.getAllValues().get(1);
        assertThat(activated.id()).isEqualTo(11L);
        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);

        // supersession type 应该是 REPLACE
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.REPLACE);
        assertThat(spCap.getValue().oldVersionId()).isEqualTo(5L);
        assertThat(spCap.getValue().newVersionId()).isEqualTo(11L);
        assertThat(spCap.getValue().transitionReason()).isEqualTo("新版指南更新");
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 11L, "u-99", "trace");

        ArgumentCaptor<KnowledgeInvalidation> invalidation = ArgumentCaptor.forClass(KnowledgeInvalidation.class);
        verify(invalidationRepo).save(invalidation.capture());
        assertThat(invalidation.getValue().versionId()).isEqualTo(5L);
        assertThat(invalidation.getValue().invalidationType().name()).isEqualTo("SUPERSEDED_REPLACEMENT");
        assertThat(invalidation.getValue().expeditedReviewRequired()).isFalse();
        assertThat(invalidation.getValue().reason()).contains("新版指南更新").contains("newVersionId=11");

        ArgumentCaptor<AffectedCaseTask> task = ArgumentCaptor.forClass(AffectedCaseTask.class);
        verify(affectedCaseTaskRepo, times(3)).save(task.capture());
        assertThat(task.getAllValues()).extracting(AffectedCaseTask::versionId).containsOnly(5L);
        assertThat(task.getAllValues()).extracting(AffectedCaseTask::taskType)
            .containsExactlyInAnyOrder(
                AffectedCaseTaskType.PHYSICIAN_REVIEW,
                AffectedCaseTaskType.ASSET_DEPENDENCY_REVIEW,
                AffectedCaseTaskType.SYNC_ALERT);
        assertThat(task.getAllValues()).extracting(AffectedCaseTask::reason)
            .allSatisfy(reason -> assertThat(reason).contains("新版指南更新").contains("newVersionId=11"));
    }

    @Test
    void activateSupersededVersionRollsBackThroughTheSameAtomicReplacementFlow() {
        KnowledgeIdentity identity = identity(1L, 11L);
        KnowledgeAssetVersion rollbackTarget =
            version(5L, 1L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion currentActive = version(11L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(rollbackTarget));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(currentActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 5L))
            .thenReturn(List.of(citation(5L)));

        KnowledgeAssetVersion activated = service.activate(
            1L, 5L, "回滚到上一版权威知识", 900L);

        assertThat(activated.id()).isEqualTo(5L);
        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);

        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.ROLLBACK);
        assertThat(spCap.getValue().oldVersionId()).isEqualTo(11L);
        assertThat(spCap.getValue().newVersionId()).isEqualTo(5L);

        ArgumentCaptor<KnowledgeInvalidation> invalidation = ArgumentCaptor.forClass(KnowledgeInvalidation.class);
        verify(invalidationRepo).save(invalidation.capture());
        assertThat(invalidation.getValue().versionId()).isEqualTo(11L);
        assertThat(invalidation.getValue().invalidationType().name()).isEqualTo("SUPERSEDED_REPLACEMENT");
        assertThat(invalidation.getValue().reason()).contains("newVersionId=5");
        verify(affectedCaseTaskRepo, times(3)).save(any(AffectedCaseTask.class));
    }

    @Test
    void activateRestoredVersionReusesOpenReplacementInvalidationForPreviouslySupersededVersion() {
        KnowledgeIdentity identity = identity(1L, 11L);
        KnowledgeAssetVersion v1Active =
            versionWithVersionNo(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, "V1");
        KnowledgeAssetVersion v1Superseded =
            versionWithVersionNo(5L, 1L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW, "V1");
        KnowledgeAssetVersion v2Candidate =
            versionWithVersionNo(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, "V2");
        KnowledgeAssetVersion v2Active =
            versionWithVersionNo(11L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, "V2");
        KnowledgeAssetVersion v2Superseded =
            versionWithVersionNo(11L, 1L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW, "V2");
        KnowledgeInvalidation existingV1Invalidation = new KnowledgeInvalidation(
            77L,
            "t-1",
            1L,
            5L,
            KnowledgeInvalidationType.SUPERSEDED_REPLACEMENT,
            KnowledgeInvalidationStatus.OPEN,
            KnowledgeRiskLevel.LOW,
            "知识版本原子替换：oldVersionId=5，newVersionId=11",
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            "u-99",
            Instant.now(),
            false,
            "trace",
            Instant.now(),
            "u-99",
            Instant.now(),
            "u-99"
        );
        KnowledgeInvalidation resolvedV1Invalidation = new KnowledgeInvalidation(
            existingV1Invalidation.id(),
            existingV1Invalidation.tenantId(),
            existingV1Invalidation.identityId(),
            existingV1Invalidation.versionId(),
            existingV1Invalidation.invalidationType(),
            KnowledgeInvalidationStatus.RESOLVED,
            existingV1Invalidation.riskLevel(),
            existingV1Invalidation.reason(),
            existingV1Invalidation.organizationScope(),
            existingV1Invalidation.applicableScope(),
            existingV1Invalidation.authorizedBy(),
            existingV1Invalidation.invalidatedAt(),
            existingV1Invalidation.expeditedReviewRequired(),
            existingV1Invalidation.traceId(),
            existingV1Invalidation.createdAt(),
            existingV1Invalidation.createdBy(),
            existingV1Invalidation.updatedAt(),
            existingV1Invalidation.updatedBy()
        );
        KnowledgeInvalidation existingV2Invalidation = new KnowledgeInvalidation(
            78L,
            "t-1",
            1L,
            11L,
            KnowledgeInvalidationType.SUPERSEDED_REPLACEMENT,
            KnowledgeInvalidationStatus.OPEN,
            KnowledgeRiskLevel.LOW,
            "知识版本原子替换：oldVersionId=11，newVersionId=5",
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            "u-99",
            Instant.now(),
            false,
            "trace",
            Instant.now(),
            "u-99",
            Instant.now(),
            "u-99"
        );

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L))
            .thenReturn(Optional.of(identity), Optional.of(identity), Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L))
            .thenReturn(Optional.of(v2Candidate), Optional.of(v2Superseded));
        when(versionRepo.findByTenantIdAndId("t-1", 5L))
            .thenReturn(Optional.of(v1Superseded));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(v1Active), Optional.of(v2Active), Optional.of(v1Active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 5L))
            .thenReturn(List.of(citation(5L)));
        when(invalidationRepo.findByTenantIdAndVersionIdOrderByInvalidatedAtDesc("t-1", 5L))
            .thenReturn(List.of(), List.of(existingV1Invalidation), List.of(resolvedV1Invalidation));
        when(invalidationRepo.findByTenantIdAndVersionIdOrderByInvalidatedAtDesc("t-1", 11L))
            .thenReturn(List.of(), List.of(), List.of(existingV2Invalidation));

        service.activate(1L, 11L, "发布 V2", 900L);
        service.activate(1L, 5L, "回滚至 V1", 900L);
        KnowledgeAssetVersion restored = service.activate(1L, 11L, "恢复 V2", 900L);

        assertThat(restored.id()).isEqualTo(11L);
        assertThat(restored.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        verify(supersessionRepo, times(3)).save(any(KnowledgeSupersession.class));
        ArgumentCaptor<KnowledgeInvalidation> invalidation = ArgumentCaptor.forClass(KnowledgeInvalidation.class);
        verify(invalidationRepo, times(5)).save(invalidation.capture());
        assertThat(invalidation.getAllValues().stream().filter(item -> item.id() == null).toList())
            .extracting(KnowledgeInvalidation::versionId)
            .containsExactly(5L, 11L);
        assertThat(invalidation.getAllValues()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(77L);
            assertThat(item.versionId()).isEqualTo(5L);
            assertThat(item.status()).isEqualTo(KnowledgeInvalidationStatus.RESOLVED);
        });
        assertThat(invalidation.getAllValues()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(77L);
            assertThat(item.versionId()).isEqualTo(5L);
            assertThat(item.status()).isEqualTo(KnowledgeInvalidationStatus.OPEN);
            assertThat(item.reason()).contains("恢复 V2").contains("newVersionId=11");
        });
        assertThat(invalidation.getAllValues()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(78L);
            assertThat(item.versionId()).isEqualTo(11L);
            assertThat(item.status()).isEqualTo(KnowledgeInvalidationStatus.RESOLVED);
        });
    }

    @Test
    void activateSupersededVersionRollsBackWithdrawnUnifiedVersionInsteadOfRepublishing() {
        KnowledgeIdentity identity = identity(1L, 11L);
        KnowledgeAssetVersion rollbackTarget = versionWithVersionNo(
            5L, 1L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW, "V1");
        KnowledgeAssetVersion currentActive = versionWithVersionNo(
            11L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, "V2");

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(rollbackTarget));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(currentActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 5L))
            .thenReturn(List.of(citation(5L)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "t-1", VersionedAssetType.KNOWLEDGE, "DRUG.X", "V1"))
            .thenReturn(Optional.of(unifiedVersion(
                "DRUG.X", "V1", rollbackTarget.contentHash(),
                "knowledge-version:DRUG.X:V1", AssetVersionStatus.WITHDRAWN)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "t-1", VersionedAssetType.KNOWLEDGE, "DRUG.X", "V2"))
            .thenReturn(Optional.of(unifiedVersion(
                "DRUG.X", "V2", currentActive.contentHash(),
                "knowledge-version:DRUG.X:V2", AssetVersionStatus.PUBLISHED)));

        service.activate(1L, 5L, "回滚到上一版权威知识", 900L);

        ArgumentCaptor<VersionRollbackCommand> rollback =
            ArgumentCaptor.forClass(VersionRollbackCommand.class);
        verify(releasePort).rollback(rollback.capture());
        assertThat(rollback.getValue().currentVersionId()).isEqualTo("av-DRUG.X-V2");
        assertThat(rollback.getValue().targetVersionId()).isEqualTo("av-DRUG.X-V1");
        assertThat(rollback.getValue().confirmedCurrentVersion()).isEqualTo("V2");
        assertThat(rollback.getValue().confirmedTargetVersion()).isEqualTo("V1");
        assertThat(rollback.getValue().confirmedOperation()).isTrue();
        verify(releasePort, never()).publish(any());
    }

    @Test
    void activateKeepsOtherEffectiveScopeActiveWhenPublishingScopedVersion() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion otherScopeActive = version(
            5L, "t-1", 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, "tenant:t-1", "specialty:ENDO");
        KnowledgeAssetVersion cardiologyCandidate = version(
            11L, "t-1", 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW,
            SourceAuthorityLevel.B_GUIDELINE, "tenant:t-1", "specialty:CARD");

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(cardiologyCandidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", "specialty:CARD")).thenReturn(Optional.empty());
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        KnowledgeAssetVersion activated = service.activate(
            1L, 11L, "心内科专项版本", 900L);

        verify(versionRepo, times(1)).save(any(KnowledgeAssetVersion.class));
        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        assertThat(activated.activeScopeKey()).isEqualTo("1|tenant:t-1|specialty:CARD");
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.ACTIVATE);
        assertThat(spCap.getValue().oldVersionId()).isNull();
    }

    @Test
    void activateRefreshesKnowledgeGraphAndSearchProjectionAfterPublication() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion candidate = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.empty());
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 10L))
            .thenReturn(List.of(citation(10L)));

        KnowledgeAssetVersion activated = service.activate(
            1L, 10L, "发布新版知识资产", 900L);

        assertThat(activated.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE);
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 10L, "u-99", "trace");
    }

    @Test
    void activateHigherAuthorityCandidateRecordsArbitrationWithoutManualReason() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.D_HOSPITAL);
        KnowledgeAssetVersion newCandidate =
            version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(1L, 11L, null, 900L);

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());
        KnowledgeAssetVersion activated = vCap.getAllValues().get(1);
        assertThat(activated.conflictArbitration()).contains("A 法规").contains("D 院内");

        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionReason()).contains("可信分级裁决");
    }

    @Test
    void activateSameAuthorityCandidateRecordsRecencyArbitrationBeforeScope() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            versionWithSourceVersion(5L, 1L, KnowledgeVersionStatus.ACTIVE, SourceAuthorityLevel.B_GUIDELINE,
                501L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE);
        KnowledgeAssetVersion newCandidate =
            versionWithSourceVersion(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, SourceAuthorityLevel.B_GUIDELINE,
                511L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(oldActive));
        when(sourceVersionRepo.findByTenantIdAndId("t-1", 501L))
            .thenReturn(Optional.of(sourceVersion(501L, Instant.parse("2024-01-01T00:00:00Z"))));
        when(sourceVersionRepo.findByTenantIdAndId("t-1", 511L))
            .thenReturn(Optional.of(sourceVersion(511L, Instant.parse("2026-01-01T00:00:00Z"))));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(1L, 11L, null, 900L);

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());
        assertThat(vCap.getAllValues().get(1).conflictArbitration())
            .contains("时效优先")
            .contains("2026-01-01")
            .contains("2024-01-01");
    }

    @Test
    void conflictArbitrationFallsBackToScopeSpecificityWhenAuthorityAndRecencyTie() {
        KnowledgeAssetVersion broad =
            versionWithSourceVersion(5L, 1L, KnowledgeVersionStatus.ACTIVE, SourceAuthorityLevel.B_GUIDELINE,
                501L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE);
        KnowledgeAssetVersion specialty =
            versionWithSourceVersion(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, SourceAuthorityLevel.B_GUIDELINE,
                511L, "tenant:t-1", "specialty:CARD");
        SourceVersion oldSource = sourceVersion(501L, Instant.parse("2026-01-01T00:00:00Z"));
        SourceVersion targetSource = sourceVersion(511L, Instant.parse("2026-01-01T00:00:00Z"));

        ConflictArbitration arbitration = ConflictArbitration.between(broad, specialty, oldSource, targetSource);

        assertThat(arbitration.summary())
            .contains("适用域优先")
            .contains("specialty:CARD")
            .contains(KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE);
        assertThat(arbitration.lowAuthorityOverrideHighAuthority()).isFalse();
    }

    @Test
    void activateRejectsLowAuthorityOverrideWithoutReason() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION);
        KnowledgeAssetVersion newCandidate =
            version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.D_HOSPITAL);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        assertThatThrownBy(() -> service.activate(1L, 11L, "  ", 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.AUTHORITY_OVERRIDE_DENIED);
        verify(versionRepo, never()).save(any());
        verify(supersessionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void activateAllowsLowAuthorityOverrideWithExplicitReasonAndRecordsArbitration() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion oldActive =
            version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.A_REGULATION);
        KnowledgeAssetVersion newCandidate =
            version(11L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW, SourceAuthorityLevel.D_HOSPITAL);

        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 11L)).thenReturn(Optional.of(newCandidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(oldActive));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 11L))
            .thenReturn(List.of(citation(11L)));

        service.activate(
            1L, 11L, "院内药事会已审核本院禁忌证差异", 900L);

        ArgumentCaptor<KnowledgeAssetVersion> vCap = ArgumentCaptor.forClass(KnowledgeAssetVersion.class);
        verify(versionRepo, times(2)).save(vCap.capture());
        assertThat(vCap.getAllValues().get(1).conflictArbitration()).contains("低阶来源覆盖高阶来源");

        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionReason())
            .contains("院内药事会已审核本院禁忌证差异")
            .contains("低阶来源覆盖高阶来源");
    }

    @Test
    void activateRejectsNonActivatableVersion() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion draft = version(10L, 1L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.activate(1L, 10L, null, 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        verify(versionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void activateRejectsCrossIdentityVersion() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion otherIdentityVersion = version(10L, 999L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(otherIdentityVersion));

        assertThatThrownBy(() -> service.activate(1L, 10L, null, 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void activateHighRiskRequiresReason() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion highRisk = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(highRisk));

        assertThatThrownBy(() -> service.activate(1L, 10L, "  ", 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void activateRejectsVersionWithoutCitation() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion candidate = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.activate(1L, 10L, null, 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.KNOWLEDGE_CITATION_REQUIRED);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void activateRejectsWithdrawnIdentity() {
        KnowledgeIdentity identity = new KnowledgeIdentity(
            1L, "t-1", "DRUG.X", KnowledgeDomain.DRUG, "已撤回主题", null, null,
            KnowledgeIdentityStatus.WITHDRAWN, null,
            Instant.now(), "u", Instant.now(), "u"
        );
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> service.activate(1L, 99L, null, 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void withdrawActiveVersionTransitionsToWithdrawn() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(active));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), active.versionNo()))
            .thenReturn(Optional.of(unifiedVersion(
                identity.identityCode(), active.versionNo(), AssetVersionStatus.PUBLISHED)));

        KnowledgeAssetVersion withdrawn = service.withdraw(1L, 5L, "上游召回紧急通知");

        assertThat(withdrawn.status()).isEqualTo(KnowledgeVersionStatus.WITHDRAWN);
        assertThat(withdrawn.withdrawnAt()).isNotNull();
        assertThat(withdrawn.withdrawnReason()).isEqualTo("上游召回紧急通知");

        // identity.current_version_id 应该被置 null
        ArgumentCaptor<KnowledgeIdentity> idCap = ArgumentCaptor.forClass(KnowledgeIdentity.class);
        verify(identityRepo).save(idCap.capture());
        assertThat(idCap.getValue().currentVersionId()).isNull();

        // supersession type=WITHDRAW
        ArgumentCaptor<KnowledgeSupersession> spCap = ArgumentCaptor.forClass(KnowledgeSupersession.class);
        verify(supersessionRepo).save(spCap.capture());
        assertThat(spCap.getValue().transitionType()).isEqualTo(SupersessionType.WITHDRAW);

        ArgumentCaptor<AssetVersion> unifiedCap = ArgumentCaptor.forClass(AssetVersion.class);
        verify(assetVersions).save(unifiedCap.capture());
        assertThat(unifiedCap.getValue().status()).isEqualTo(AssetVersionStatus.WITHDRAWN);
        assertThat(unifiedCap.getValue().effectiveTo()).isNotNull();
    }

    @Test
    void withdrawHighRiskVersionCreatesInvalidationTasksAndProjectionRefresh() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(active));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), active.versionNo()))
            .thenReturn(Optional.of(unifiedVersion(
                identity.identityCode(), active.versionNo(), AssetVersionStatus.PUBLISHED)));

        service.withdraw(1L, 5L, "说明书新增禁忌证，立即限制旧版");

        ArgumentCaptor<KnowledgeInvalidation> invalidation = ArgumentCaptor.forClass(KnowledgeInvalidation.class);
        verify(invalidationRepo).save(invalidation.capture());
        assertThat(invalidation.getValue().versionId()).isEqualTo(5L);
        assertThat(invalidation.getValue().invalidationType()).isEqualTo(KnowledgeInvalidationType.EMERGENCY_WITHDRAW);
        assertThat(invalidation.getValue().status()).isEqualTo(KnowledgeInvalidationStatus.OPEN);
        assertThat(invalidation.getValue().expeditedReviewRequired()).isTrue();
        assertThat(invalidation.getValue().reason()).contains("新增禁忌证");

        ArgumentCaptor<AffectedCaseTask> task = ArgumentCaptor.forClass(AffectedCaseTask.class);
        verify(affectedCaseTaskRepo, times(3)).save(task.capture());
        assertThat(task.getAllValues()).extracting(AffectedCaseTask::taskType)
            .containsExactlyInAnyOrder(
                AffectedCaseTaskType.PHYSICIAN_REVIEW,
                AffectedCaseTaskType.ASSET_DEPENDENCY_REVIEW,
                AffectedCaseTaskType.SYNC_ALERT);
        assertThat(task.getAllValues()).extracting(AffectedCaseTask::status)
            .containsOnly(AffectedCaseTaskStatus.OPEN);
        assertThat(task.getAllValues()).extracting(AffectedCaseTask::targetRef)
            .allSatisfy(ref -> assertThat(ref).contains("version:5"));
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 5L, "u-99", "trace");
    }

    @Test
    void activateRejectsWithdrawnHighRiskVersionAsUnsafeRollback() {
        KnowledgeIdentity identity = identity(1L, null);
        KnowledgeAssetVersion withdrawn = version(5L, 1L, KnowledgeVersionStatus.WITHDRAWN, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.activate(
            1L, 5L, "尝试回滚旧版", 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ROLLBACK_SAFETY_DENIED);
        verify(versionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void withdrawRequiresReason() {
        // 没有跑到锁定步骤就被拒
        assertThatThrownBy(() -> service.withdraw(1L, 5L, null))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(identityRepo, never()).findByTenantIdAndIdForUpdate(any(), any());
    }

    @Test
    void withdrawRejectsNonActiveVersion() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion draft = version(5L, 1L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 5L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.withdraw(1L, 5L, "原因"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void listByIdentityRequiresIdentityExists() {
        when(identityRepo.findByTenantIdAndId("t-1", 99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listByIdentity(99L, PageRequest.defaults()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void reviewQueueClassifiesOverdueAndUpcomingActiveVersions() {
        Instant now = Instant.now();
        KnowledgeAssetVersion overdue = withReviewSchedule(
            version(10L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW),
            now.minus(2, ChronoUnit.DAYS));
        KnowledgeAssetVersion upcoming = withReviewSchedule(
            version(11L, 2L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW),
            now.plus(10, ChronoUnit.DAYS));
        when(versionRepo.countReviewDueByTenantId(eq("t-1"), any(Instant.class))).thenReturn(2L);
        when(versionRepo.pageReviewDueByTenantId(eq("t-1"), any(Instant.class), eq(0), eq(2)))
            .thenReturn(List.of(overdue, upcoming));
        when(identityRepo.findByTenantIdAndIdIn("t-1", List.of(1L, 2L)))
            .thenReturn(List.of(identity(1L, 10L), identity(2L, 11L)));

        PageResponse<KnowledgeReviewQueueItem> queue =
            service.listReviewQueue(30, new PageRequest(1, 2, "nextReviewAt,asc"));

        assertThat(queue.items()).extracting(KnowledgeReviewQueueItem::status)
            .containsExactly(KnowledgeReviewStatus.OVERDUE, KnowledgeReviewStatus.UPCOMING);
        assertThat(queue.total()).isEqualTo(2L);
        assertThat(queue.items().get(0).daysUntilDue()).isNegative();
        assertThat(queue.items().get(1).daysUntilDue()).isBetween(9L, 10L);
        verify(identityRepo, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void reviewQueueRejectsUnboundedWindow() {
        assertThatThrownBy(() -> service.listReviewQueue(366, PageRequest.defaults()))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(versionRepo, never()).countReviewDueByTenantId(any(), any());
        verify(versionRepo, never()).pageReviewDueByTenantId(any(), any(), anyInt(), anyInt());
    }

    @Test
    void classifyCandidateWithStandardRequestUsesPathIdentity() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeCandidateResponse response = service.classifyCandidate(1L, versionCreateRequest("v2"));

        KnowledgeAssetVersion created = response.candidates().items().get(0);
        assertThat(created.identityId()).isEqualTo(1L);
        assertThat(created.versionNo()).isEqualTo("v2");
        assertThat(created.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
        assertThat(created.contentHash()).isNotBlank();
        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.B_GUIDELINE);
        assertThat(created.gradeQuality()).isEqualTo(GradeEvidenceQuality.HIGH);
        assertThat(created.gradeStrength()).isEqualTo(GradeRecommendationStrength.STRONG);
        assertThat(created.reviewCycleMonths()).isEqualTo(12);
        assertThat(created.nextReviewAt()).isNull();
        assertThat(created.createdBy()).isEqualTo("u-99");
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.NEW_ASSET);
                assertThat(item.orgPath()).isEqualTo("tenant:t-1");
            });
        ArgumentCaptor<ReviewAssignment> assignment = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo).save(assignment.capture());
        assertThat(assignment.getValue().orgPath()).isEqualTo("tenant:t-1");
    }

    @Test
    void classifyInstitutionCandidateUsesTheRealFacilityOwnerPath() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace",
            new OrgScope(
                "tenant-A", null, "h-1", null, null,
                "d-1", null, "CARD"),
            "u-99"
        ));
        when(identityRepo.findByTenantIdAndId("tenant-A", 1L))
            .thenReturn(Optional.of(identity(1L, "tenant-A", "DRUG.X", null)));
        when(sourceDocRepo.findByTenantIdAndId("tenant-A", 7L))
            .thenReturn(Optional.of(sourceDocument(
                "tenant-A", 7L, SourceAuthorityLevel.B_GUIDELINE)));

        KnowledgeAssetVersion created = service.classifyCandidate(
            1L,
            versionCreateRequestWithTenant(
                "tenant-A", 7L, 8L, "v2", "院内真实指南内容")
        ).candidates().items().getFirst();

        assertThat(created.organizationScope()).isEqualTo("/tenant-A/h-1");
    }

    @Test
    void classifyCandidateUsesPointLookupsInsteadOfLoadingAllIdentityVersions() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        String contentHash = sha256("国家法规更新后的真实内容");
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L))
            .thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase("t-1", 1L, "regulation-v2"))
            .thenReturn(false);
        when(versionRepo.findByTenantIdAndIdentityIdAndContentHash("t-1", 1L, contentHash))
            .thenReturn(Optional.empty());
        when(versionRepo.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
            "t-1", 1L, KnowledgeVersionStatus.ACTIVE)).thenReturn(Optional.of(active));

        service.classifyCandidate(1L, versionCreateRequestWithContent(
            "regulation-v2", "国家法规更新后的真实内容"));

        verify(versionRepo).existsByTenantIdAndIdentityIdAndVersionNoIgnoreCase("t-1", 1L, "regulation-v2");
        verify(versionRepo).findByTenantIdAndIdentityIdAndContentHash("t-1", 1L, contentHash);
        verify(versionRepo).findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
            "t-1", 1L, KnowledgeVersionStatus.ACTIVE);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void classifyCandidateStoresCanonicalSha256() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAssetVersion created = service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-1", 10L, 20L, "v2", "真实指南内容")).candidates().items().get(0);

        assertThat(created.contentHash()).isEqualTo(sha256("真实指南内容"));
        assertThat(created.contentHash()).matches("[0-9a-f]{64}");
        assertThat(created.authorityLevel()).isEqualTo(SourceAuthorityLevel.C_CONSENSUS_LITERATURE);
        assertThat(created.gradeQuality()).isEqualTo(GradeEvidenceQuality.HIGH);
        assertThat(created.gradeStrength()).isEqualTo(GradeRecommendationStrength.STRONG);
    }

    @Test
    void classifyCandidateRejectsBlankContentInsteadOfHashingEmptyString() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(sourceDocument(10L, SourceAuthorityLevel.C_CONSENSUS_LITERATURE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-1", 10L, 20L, "v2", "   ")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void submitDraftTransitionsToUnderReviewAndKeepsVersionFields() {
        KnowledgeAssetVersion draft = version(10L, 1L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.HIGH);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(draft));

        KnowledgeAssetVersion submitted = service.submit(1L, 10L, actionRequest("t-1"));

        assertThat(submitted.status()).isEqualTo(KnowledgeVersionStatus.UNDER_REVIEW);
        assertThat(submitted.riskLevel()).isEqualTo(KnowledgeRiskLevel.HIGH);
        assertThat(submitted.contentHash()).isEqualTo(draft.contentHash());
        assertThat(submitted.identityId()).isEqualTo(1L);
        assertThat(submitted.updatedBy()).isEqualTo("u-99");
        verify(versionRepo).save(any(KnowledgeAssetVersion.class));
    }

    @Test
    void submitUnderReviewIsIdempotent() {
        KnowledgeAssetVersion underReview = version(10L, 1L, KnowledgeVersionStatus.UNDER_REVIEW, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(underReview));

        KnowledgeAssetVersion result = service.submit(1L, 10L, actionRequest("t-1"));

        assertThat(result).isSameAs(underReview);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void submitRejectsCrossIdentityVersion() {
        KnowledgeAssetVersion otherIdentityVersion = version(10L, 2L, KnowledgeVersionStatus.DRAFT, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(otherIdentityVersion));

        assertThatThrownBy(() -> service.submit(1L, 10L, actionRequest("t-1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        verify(versionRepo, never()).save(any());
    }

    @Test
    void submitRejectsMismatchedTenantContext() {
        assertThatThrownBy(() -> service.submit(1L, 10L, actionRequest("t-2")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
        verify(versionRepo, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void replayVersionRejectsVersionNotBelongingToIdentity() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L))
            .thenReturn(Optional.of(version(10L, 2L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW)));

        assertThatThrownBy(() -> service.replayVersion(1L, 10L, "snap-1"))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void replayVersionReturnsHistoricalMarkerWithoutRequiringActiveStatus() {
        KnowledgeAssetVersion superseded = version(10L, 1L, KnowledgeVersionStatus.SUPERSEDED, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.findByTenantIdAndId("t-1", 10L)).thenReturn(Optional.of(superseded));

        KnowledgeReplayResponse replay = service.replayVersion(1L, 10L, "snap-1");

        assertThat(replay.identityId()).isEqualTo(1L);
        assertThat(replay.versionId()).isEqualTo(10L);
        assertThat(replay.historicalVersion()).isTrue();
        assertThat(replay.status()).isEqualTo(KnowledgeVersionStatus.SUPERSEDED);
        assertThat(replay.snapshotId()).isEqualTo("snap-1");
    }

    @Test
    void listCandidatesReturnsAvailableEmptyWorkflowWhenNoPendingCandidates() {
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));

        KnowledgeCandidateResponse response = service.listCandidates(1L, new PageRequest(2, 5, null));

        assertThat(response.identityId()).isEqualTo(1L);
        assertThat(response.available()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("OK");
        assertThat(response.candidates().items()).isEmpty();
        assertThat(response.candidates().page()).isEqualTo(2);
        assertThat(response.candidates().size()).isEqualTo(5);
        assertThat(response.classifications()).isEmpty();
        verify(versionRepo).countPendingReplacementCandidatesByTenantIdAndIdentityId("t-1", 1L);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc(any(), any());
        verify(candidateClassificationRepo, never())
            .findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void listCandidatesPagesPendingCandidatesAndLoadsOnlyCurrentPageClassifications() {
        KnowledgeAssetVersion candidate =
            version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.HIGH);
        CandidateClassification classification = classification(
            77L,
            1L,
            22L,
            5L,
            CandidateClassificationType.CONFLICT,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity(1L, null)));
        when(versionRepo.countPendingReplacementCandidatesByTenantIdAndIdentityId("t-1", 1L))
            .thenReturn(21L);
        when(versionRepo.pagePendingReplacementCandidatesByTenantIdAndIdentityId("t-1", 1L, 5, 5))
            .thenReturn(List.of(candidate));
        when(candidateClassificationRepo.findByTenantIdAndCandidateVersionIdIn("t-1", List.of(22L)))
            .thenReturn(List.of(classification));

        KnowledgeCandidateResponse response = service.listCandidates(1L, new PageRequest(2, 5, null));

        assertThat(response.candidates().items()).containsExactly(candidate);
        assertThat(response.candidates().page()).isEqualTo(2);
        assertThat(response.candidates().size()).isEqualTo(5);
        assertThat(response.candidates().total()).isEqualTo(21L);
        assertThat(response.classifications()).containsExactly(classification);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc(any(), any());
        verify(candidateClassificationRepo, never())
            .findByTenantIdAndIdentityIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void classifyDuplicateCandidateRecordsBasisWithoutCreatingReviewTodoOrVersion() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L))
            .thenReturn(List.of(active));
        when(versionRepo.findByTenantIdAndIdentityIdAndContentHash("t-1", 1L, active.contentHash()))
            .thenReturn(Optional.of(active));
        when(versionRepo.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
            "t-1", 1L, KnowledgeVersionStatus.ACTIVE)).thenReturn(Optional.of(active));

        KnowledgeCandidateResponse response = service.classifyCandidate(1L, versionCreateRequestWithContent(
            "duplicate-v2", "知识版本夹具内容-t-1-5"));

        assertThat(response.available()).isTrue();
        assertThat(response.reasonCode()).isEqualTo("DUPLICATE");
        assertThat(response.candidates().items()).isEmpty();
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.DUPLICATE);
                assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.DUPLICATE_SKIPPED);
                assertThat(item.basis()).contains("content_hash").contains(active.contentHash());
                assertThat(item.activeVersionId()).isEqualTo(5L);
                assertThat(item.candidateVersionId()).isNull();
            });
        verify(versionRepo, never()).save(any());
        verify(reviewAssignmentRepo, never()).save(any());
    }

    @Test
    void classifyNewVersionCreatesPendingReviewCandidateWithoutActivatingIt() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-1", 7L)).thenReturn(Optional.of(sourceDocument(7L, SourceAuthorityLevel.B_GUIDELINE)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L))
            .thenReturn(List.of(active));
        when(versionRepo.findByTenantIdAndIdentityIdAndContentHash("t-1", 1L, sha256("国家法规更新后的真实内容")))
            .thenReturn(Optional.empty());
        when(versionRepo.findFirstByTenantIdAndIdentityIdAndStatusOrderByCreatedAtDescIdDesc(
            "t-1", 1L, KnowledgeVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> {
            KnowledgeAssetVersion candidate = inv.getArgument(0);
            return new KnowledgeAssetVersion(
                22L, candidate.tenantId(), candidate.identityId(), candidate.versionNo(), candidate.versionLabel(),
                candidate.sourceDocumentId(), candidate.sourceVersionId(), candidate.contentHash(), candidate.anchors(),
                candidate.status(), candidate.riskLevel(), candidate.authorityLevel(), candidate.gradeQuality(),
                candidate.gradeStrength(), candidate.conflictArbitration(),
                candidate.effectiveOrganizationScope(), candidate.effectiveApplicableScope(), candidate.activeScopeKey(),
                candidate.effectiveFrom(), candidate.effectiveTo(),
                candidate.reviewedBy(), candidate.reviewedAt(), candidate.activatedAt(), candidate.supersededAt(),
                candidate.withdrawnAt(), candidate.withdrawnReason(), candidate.createdAt(), candidate.createdBy(),
                candidate.updatedAt(), candidate.updatedBy(), 12, null);
        });

        KnowledgeCandidateResponse response = service.classifyCandidate(1L, versionCreateRequestWithContent(
            "regulation-v2", "国家法规更新后的真实内容"));

        assertThat(response.reasonCode()).isEqualTo("SAME_IDENTITY_NEW_VERSION");
        assertThat(response.candidates().items()).singleElement()
            .satisfies(candidate -> {
                assertThat(candidate.id()).isEqualTo(22L);
                assertThat(candidate.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW);
                assertThat(candidate.activatedAt()).isNull();
            });
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.SAME_IDENTITY_NEW_VERSION);
                assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
                assertThat(item.activeVersionId()).isEqualTo(5L);
                assertThat(item.candidateVersionId()).isEqualTo(22L);
                assertThat(item.diffSummary()).contains("当前 ACTIVE").contains("候选");
            });
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
        verify(reviewAssignmentRepo).save(any(ReviewAssignment.class));
        ArgumentCaptor<AssetVersionRegisterCommand> registered =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versionedAssets).registerDraft(registered.capture());
        assertThat(registered.getValue().assetType()).isEqualTo(VersionedAssetType.KNOWLEDGE);
        assertThat(registered.getValue().assetIdentity()).isEqualTo(identity.identityCode());
        assertThat(registered.getValue().contentHash())
            .isEqualTo(response.candidates().items().getFirst().contentHash());
    }

    @Test
    void reviewCandidateHasTransactionalBoundaryForApprovalReplacement() throws NoSuchMethodException {
        Transactional transactional = KnowledgeVersionService.class
            .getDeclaredMethod("reviewCandidate", Long.class, KnowledgeCandidateReviewRequest.class)
            .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void approveCandidateDelegatesToAtomicActivationFlow() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        stubPendingAssignment(classification, candidate, "u-99");
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(22L)));

        KnowledgeCandidateResponse response = service.reviewCandidate(88L, candidateReviewRequest("t-1"));

        assertThat(response.reasonCode()).isEqualTo("APPROVED");
        assertThat(response.candidates().items()).singleElement()
            .satisfies(approved -> assertThat(approved.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE));
        verify(supersessionRepo).save(any(KnowledgeSupersession.class));
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 22L, "u-99", "trace");
    }

    @Test
    void approveCandidateUsesSourceRefLinkedCanonicalUnifiedVersionWhenVersionNoDiffers() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        stubPendingAssignment(classification, candidate, "u-99");
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(22L)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), candidate.versionNo()))
            .thenReturn(Optional.empty());
        String sourceRef = "knowledge-version:" + identity.identityCode() + ":" + candidate.versionNo();
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndSourceRef(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), sourceRef))
            .thenReturn(Optional.of(unifiedVersion(
                identity.identityCode(), "V1", candidate.contentHash(), sourceRef, AssetVersionStatus.DRAFT)));

        KnowledgeCandidateResponse response = service.reviewCandidate(88L, candidateReviewRequest("t-1"));

        assertThat(response.reasonCode()).isEqualTo("APPROVED");
        verify(assetVersions).findByTenantIdAndAssetTypeAndAssetIdentityAndSourceRef(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), sourceRef);
        verify(releasePort).publish(any());
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 22L, "u-99", "trace");
    }

    @Test
    void approveCandidateRejectsSourceRefLinkedUnifiedVersionWhenContentHashDiffers() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        stubPendingAssignment(classification, candidate, "u-99");
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(22L)));
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), candidate.versionNo()))
            .thenReturn(Optional.empty());
        String sourceRef = "knowledge-version:" + identity.identityCode() + ":" + candidate.versionNo();
        when(assetVersions.findByTenantIdAndAssetTypeAndAssetIdentityAndSourceRef(
            "t-1", VersionedAssetType.KNOWLEDGE, identity.identityCode(), sourceRef))
            .thenReturn(Optional.of(unifiedVersion(
                identity.identityCode(), "V1", sha256("被串线的统一资产内容"), sourceRef, AssetVersionStatus.DRAFT)));

        assertThatThrownBy(() -> service.reviewCandidate(88L, candidateReviewRequest("t-1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
        verify(releasePort, never()).publish(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void rejectCandidateMarksVersionRejectedWithoutActivationSideEffects() {
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.CONFLICT,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        stubPendingAssignment(classification, candidate, "u-99");
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));

        KnowledgeCandidateReviewRequest rejectRequest = new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"),
            KnowledgeCandidateReviewDecision.REJECT, "来源冲突，退回补证",
            900L
        );
        KnowledgeCandidateResponse response = service.reviewCandidate(88L, rejectRequest);

        assertThat(response.reasonCode()).isEqualTo("REJECTED");
        assertThat(response.candidates().items()).singleElement()
            .satisfies(rejected -> assertThat(rejected.status()).isEqualTo(KnowledgeVersionStatus.REJECTED));
        ArgumentCaptor<ReviewAssignment> assignment = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo).save(assignment.capture());
        assertThat(assignment.getValue().reason()).isEqualTo("来源冲突，退回补证");
        assertThat(assignment.getValue().decision()).isEqualTo(KnowledgeCandidateReviewDecision.REJECT);
        assertThat(assignment.getValue().feedbackType()).isEqualTo(KnowledgeReviewFeedbackType.NOT_ADOPTED);
        assertThat(assignment.getValue().followupAction()).isEqualTo(KnowledgeReviewFollowupAction.ARCHIVE_REJECTED);
        verify(supersessionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void returnCandidateSendsVersionBackToDraftAndRecordsReturnedAssignment() {
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        stubPendingAssignment(classification, candidate, "u-99");
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));

        KnowledgeCandidateReviewRequest returnRequest = new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"),
            KnowledgeCandidateReviewDecision.RETURN, "请补充禁忌章节后重提",
            900L
        );
        KnowledgeCandidateResponse response = service.reviewCandidate(88L, returnRequest);

        assertThat(response.reasonCode()).isEqualTo("RETURNED");
        assertThat(response.candidates().items()).singleElement()
            .satisfies(returned -> assertThat(returned.status()).isEqualTo(KnowledgeVersionStatus.DRAFT));
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.RETURNED));
        ArgumentCaptor<ReviewAssignment> assignment = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo).save(assignment.capture());
        assertThat(assignment.getValue().reason()).isEqualTo("请补充禁忌章节后重提");
        assertThat(assignment.getValue().decision()).isEqualTo(KnowledgeCandidateReviewDecision.RETURN);
        assertThat(assignment.getValue().reviewStatus()).isEqualTo(CandidateReviewStatus.RETURNED);
        assertThat(assignment.getValue().feedbackType()).isEqualTo(KnowledgeReviewFeedbackType.CONTENT_GAP);
        assertThat(assignment.getValue().followupAction())
            .isEqualTo(KnowledgeReviewFollowupAction.CREATE_REVISION_CANDIDATE);
        verify(supersessionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
        verify(auditRecorder).record(
            AuditAction.REVIEW,
            "knowledge_candidate_classification",
            "88",
            "审核知识候选 RETURN identityId=1");
    }

    @Test
    void returnCandidateRecordsStructuredFeedbackAndRevisionFollowup() {
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        stubPendingAssignment(classification, candidate, "u-99");
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));

        KnowledgeCandidateReviewRequest returnRequest = new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"),
            KnowledgeCandidateReviewDecision.RETURN, "AI 生成内容缺少关键禁忌章节，需回流生产台补齐",
            900L,
            KnowledgeReviewFeedbackType.CONTENT_GAP,
            KnowledgeReviewFollowupAction.CREATE_REVISION_CANDIDATE
        );

        service.reviewCandidate(88L, returnRequest);

        ArgumentCaptor<ReviewAssignment> assignment = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo).save(assignment.capture());
        assertThat(assignment.getValue().feedbackType()).isEqualTo(KnowledgeReviewFeedbackType.CONTENT_GAP);
        assertThat(assignment.getValue().followupAction())
            .isEqualTo(KnowledgeReviewFollowupAction.CREATE_REVISION_CANDIDATE);
    }

    @Test
    void returnCandidateRejectsBlankRevisionReason() {
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity(1L, 5L)));

        KnowledgeCandidateReviewRequest blankReason = new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"),
            KnowledgeCandidateReviewDecision.RETURN, "   ",
            900L
        );
        assertThatThrownBy(() -> service.reviewCandidate(88L, blankReason))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("退修");
        verify(reviewAssignmentRepo, never()).save(any());
    }

    @Test
    void reviewCandidateRejectsOperatorOutsidePendingAssignment() {
        KnowledgeAssetVersion candidate = version(
            22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L, 1L, 22L, 5L, CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity(1L, 5L)));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(reviewAssignmentRepo.findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc("t-1", 88L))
            .thenReturn(List.of(assignment(
                101L, classification, "reviewer-else",
                CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW, null, null)));
        authenticate(RoleCode.ENGINE_OPERATOR);

        assertThatThrownBy(() -> service.reviewCandidate(88L, candidateReviewRequest("t-1")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未命中待审核分派");

        verify(versionRepo, never()).save(any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void highRiskActivatesAfterSingleAssignedApproval() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion candidate = version(
            22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.HIGH);
        CandidateClassification classification = classification(
            88L, 1L, 22L, 5L, CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(22L)));
        when(reviewAssignmentRepo.findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc("t-1", 88L))
            .thenReturn(List.of(assignment(
                101L, classification, RoleCode.ENGINE_OPERATOR.code(),
                CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW, null, null)));
        authenticate(RoleCode.ENGINE_OPERATOR);

        KnowledgeCandidateResponse response = service.reviewCandidate(88L, candidateReviewRequest("t-1"));

        assertThat(response.reasonCode()).isEqualTo("APPROVED");
        assertThat(response.candidates().items()).singleElement()
            .satisfies(approved -> assertThat(approved.status()).isEqualTo(KnowledgeVersionStatus.ACTIVE));
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 22L, "u-99", "trace");
    }

    @Test
    void assignedRoleApprovalUsesEffectiveTenantRoleWhenJwtAuthorityIsAbsent() {
        KnowledgeIdentity identity = identity(1L, 5L);
        KnowledgeAssetVersion active = version(5L, 1L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        KnowledgeAssetVersion candidate = version(
            22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L, 1L, 22L, 5L, CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(versionRepo.findActiveByEffectiveScope(
            "t-1", 1L, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE)).thenReturn(Optional.of(active));
        when(citationRepo.findByTenantIdAndAssetVersionIdOrderByWeightDescIdAsc("t-1", 22L))
            .thenReturn(List.of(citation(22L)));
        when(reviewAssignmentRepo.findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc("t-1", 88L))
            .thenReturn(List.of(assignment(
                101L, classification, RoleCode.ENGINE_OPERATOR.code(),
                CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW, null, null)));
        when(userRoleAssignments.findActiveByTenantIdAndUserId("t-1", "u-99"))
            .thenReturn(List.of(tenantRoleAssignment("u-99", RoleCode.ENGINE_OPERATOR)));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("u-99", "N/A", List.of()));

        KnowledgeCandidateResponse response = service.reviewCandidate(88L, candidateReviewRequest("t-1"));

        assertThat(response.reasonCode()).isEqualTo("APPROVED");
        verify(projectionRefreshPort).refreshPublishedVersion("t-1", 1L, 22L, "u-99", "trace");
    }

    @Test
    void highRiskReturnTerminatesCandidateAndClosesOtherPendingSeatWithoutForgingSignature() {
        KnowledgeAssetVersion candidate = version(
            22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.HIGH);
        CandidateClassification classification = classification(
            88L, 1L, 22L, 5L, CandidateClassificationType.SAME_IDENTITY_NEW_VERSION,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", 1L)).thenReturn(Optional.of(identity(1L, 5L)));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));
        when(reviewAssignmentRepo.findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc("t-1", 88L))
            .thenReturn(List.of(
                assignment(101L, classification, RoleCode.ENGINE_OPERATOR.code(),
                    CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW, null, null),
                assignment(102L, classification, RoleCode.ENGINE_OPERATOR.code(),
                    CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW, null, null)));
        authenticate(RoleCode.ENGINE_OPERATOR);
        KnowledgeCandidateReviewRequest returnRequest = new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", "t-1", null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"),
            KnowledgeCandidateReviewDecision.RETURN, "补充禁忌来源后重提",
            900L);

        KnowledgeCandidateResponse response = service.reviewCandidate(88L, returnRequest);

        assertThat(response.reasonCode()).isEqualTo("RETURNED");
        ArgumentCaptor<ReviewAssignment> saved = ArgumentCaptor.forClass(ReviewAssignment.class);
        verify(reviewAssignmentRepo, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(101L);
            assertThat(item.decision()).isEqualTo(KnowledgeCandidateReviewDecision.RETURN);
            assertThat(item.decidedBy()).isEqualTo("u-99");
        });
        assertThat(saved.getAllValues()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(102L);
            assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.RETURNED);
            assertThat(item.decision()).isNull();
            assertThat(item.decidedBy()).isNull();
        });
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void diffCandidateReturnsStoredConflictViewWithoutActivatingCandidate() {
        KnowledgeAssetVersion candidate = version(22L, 1L, KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW, KnowledgeRiskLevel.LOW);
        CandidateClassification classification = classification(
            88L,
            1L,
            22L,
            5L,
            CandidateClassificationType.CONFLICT,
            CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 88L)).thenReturn(Optional.of(classification));
        when(versionRepo.findByTenantIdAndId("t-1", 22L)).thenReturn(Optional.of(candidate));

        KnowledgeCandidateResponse response = service.diffCandidate(88L);

        assertThat(response.reasonCode()).isEqualTo("CONFLICT");
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> {
                assertThat(item.classification()).isEqualTo(CandidateClassificationType.CONFLICT);
                assertThat(item.diffSummary()).contains("当前 ACTIVE 与候选对照");
            });
        assertThat(response.candidates().items()).singleElement()
            .satisfies(item -> assertThat(item.status()).isEqualTo(KnowledgeVersionStatus.PENDING_REPLACEMENT_REVIEW));
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void diffDuplicateCandidateReturnsClassificationWithoutLookingUpMissingVersion() {
        CandidateClassification classification = classification(
            89L,
            1L,
            null,
            5L,
            CandidateClassificationType.DUPLICATE,
            CandidateReviewStatus.DUPLICATE_SKIPPED);
        when(candidateClassificationRepo.findByTenantIdAndId("t-1", 89L)).thenReturn(Optional.of(classification));

        KnowledgeCandidateResponse response = service.diffCandidate(89L);

        assertThat(response.reasonCode()).isEqualTo("DUPLICATE");
        assertThat(response.candidates().items()).isEmpty();
        assertThat(response.classifications()).singleElement()
            .satisfies(item -> assertThat(item.reviewStatus()).isEqualTo(CandidateReviewStatus.DUPLICATE_SKIPPED));
        verify(versionRepo, never()).findByTenantIdAndId(any(), any());
        verify(projectionRefreshPort, never()).refreshPublishedVersion(any(), any(), any(), any(), any());
    }

    @Test
    void reviewCandidateReturnsNotFoundWhileKnow02StorageIsAbsent() {
        assertThatThrownBy(() -> service.reviewCandidate(88L, candidateReviewRequest("t-1")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void diffCandidateReturnsNotFoundWhileKnow02StorageIsAbsent() {
        assertThatThrownBy(() -> service.diffCandidate(88L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void requiresTenantContext() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.empty(), null));
        assertThatThrownBy(() -> service.activate(1L, 10L, null, 900L))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void classifyCandidateInCustomerTenantDoesNotWriteBackToPlatformTenant() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "platform-admin"));
        KnowledgeIdentity identity = new KnowledgeIdentity(
            1L, "t-hospital", "DRUG.X", KnowledgeDomain.DRUG, "医院定制主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, null,
            Instant.now(), "platform-admin", Instant.now(), "platform-admin"
        );
        when(identityRepo.findByTenantIdAndId("t-hospital", 1L)).thenReturn(Optional.of(identity));
        when(sourceDocRepo.findByTenantIdAndId("t-hospital", 10L)).thenReturn(Optional.of(sourceDocument("t-hospital", 10L, SourceAuthorityLevel.D_HOSPITAL)));
        when(versionRepo.findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-hospital", 1L))
            .thenReturn(List.of());
        when(versionRepo.save(any(KnowledgeAssetVersion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(candidateClassificationRepo.save(any(CandidateClassification.class))).thenAnswer(inv -> inv.getArgument(0));

        KnowledgeAssetVersion saved = service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-hospital", 10L, 20L, "hospital-v1", "医院本地定制内容"))
            .candidates()
            .items()
            .get(0);

        assertThat(saved.tenantId()).isEqualTo("t-hospital");
        assertThat(saved.createdBy()).isEqualTo("platform-admin");
        verify(identityRepo, never()).findByTenantIdAndId("t-1", 1L);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L);
    }

    @Test
    void classifyCandidateInCustomerTenantTreatsPlatformIdentityAsReadOnly() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "platform-admin"));
        KnowledgeIdentity platformIdentity = new KnowledgeIdentity(
            1L, "t-1", "DRUG.X", KnowledgeDomain.DRUG, "平台主源主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, null,
            Instant.now(), "platform-admin", Instant.now(), "platform-admin"
        );
        when(identityRepo.findByTenantIdAndId("t-hospital", 1L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 1L)).thenReturn(Optional.of(platformIdentity));

        assertThatThrownBy(() -> service.classifyCandidate(1L,
            versionCreateRequestWithTenant("t-hospital", 10L, 20L, "hospital-v1", "医院本地定制内容")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_FOUND);

        verify(sourceDocRepo, never()).findByTenantIdAndId(any(), any());
        verify(versionRepo, never()).save(any(KnowledgeAssetVersion.class));
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 1L);
    }

    @Test
    void listByIdentityFallsBackToPlatformIdentityWhenCustomerHasNoLocalOverride() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "clinical-user"));
        KnowledgeIdentity platformIdentity = identity(100L, "t-1", "DRUG.X", null);
        KnowledgeAssetVersion platformVersion =
            version(900L, "t-1", 100L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-hospital", 100L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(platformIdentity));
        when(identityRepo.findByTenantIdAndIdentityCode("t-hospital", "DRUG.X")).thenReturn(Optional.empty());
        PageRequest page = new PageRequest(2, 1, null);
        when(versionRepo.countByTenantIdAndIdentityId("t-1", 100L))
            .thenReturn(2L);
        when(versionRepo.pageByTenantIdAndIdentityId("t-1", 100L, 1, 1))
            .thenReturn(List.of(platformVersion));

        PageResponse<KnowledgeAssetVersion> versions = service.listByIdentity(100L, page);

        assertThat(versions.page()).isEqualTo(2);
        assertThat(versions.total()).isEqualTo(2L);
        assertThat(versions.items()).containsExactly(platformVersion);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 100L);
    }

    @Test
    void listByIdentityPrefersLocalIdentityWithSameCodeOverPlatformIdentity() {
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-hospital"), "clinical-user"));
        KnowledgeIdentity platformIdentity = identity(100L, "t-1", "DRUG.X", null);
        KnowledgeIdentity localIdentity = identity(200L, "t-hospital", "DRUG.X", null);
        KnowledgeAssetVersion localVersion =
            version(901L, "t-hospital", 200L, KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.LOW);
        when(identityRepo.findByTenantIdAndId("t-hospital", 100L)).thenReturn(Optional.empty());
        when(identityRepo.findByTenantIdAndId("t-1", 100L)).thenReturn(Optional.of(platformIdentity));
        when(identityRepo.findByTenantIdAndIdentityCode("t-hospital", "DRUG.X")).thenReturn(Optional.of(localIdentity));
        PageRequest page = new PageRequest(1, 20, null);
        when(versionRepo.countByTenantIdAndIdentityId("t-hospital", 200L))
            .thenReturn(1L);
        when(versionRepo.pageByTenantIdAndIdentityId("t-hospital", 200L, 0, 20))
            .thenReturn(List.of(localVersion));

        PageResponse<KnowledgeAssetVersion> versions = service.listByIdentity(100L, page);

        assertThat(versions.items()).containsExactly(localVersion);
        verify(versionRepo, never()).findByTenantIdAndIdentityIdOrderByCreatedAtDesc("t-1", 100L);
    }

    // ─── helpers ──────────────────────────────────────────────

    private KnowledgeIdentity identity(Long id, Long currentVersionId) {
        return identity(id, "t-1", "DRUG.X", currentVersionId);
    }

    private KnowledgeIdentity identity(Long id, String tenantId, String identityCode, Long currentVersionId) {
        Instant now = Instant.now();
        return new KnowledgeIdentity(
            id, tenantId, identityCode, KnowledgeDomain.DRUG, "测试主题", null, null,
            KnowledgeIdentityStatus.ACTIVE, currentVersionId,
            now, "init", now, "init"
        );
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status, KnowledgeRiskLevel risk) {
        return version(id, "t-1", identityId, status, risk, SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeAssetVersion version(Long id, String tenantId, Long identityId,
                                          KnowledgeVersionStatus status, KnowledgeRiskLevel risk) {
        return version(id, tenantId, identityId, status, risk, SourceAuthorityLevel.B_GUIDELINE);
    }

    private KnowledgeAssetVersion version(Long id, Long identityId, KnowledgeVersionStatus status,
                                          KnowledgeRiskLevel risk, SourceAuthorityLevel authorityLevel) {
        return version(id, "t-1", identityId, status, risk, authorityLevel);
    }

    private KnowledgeAssetVersion version(Long id, String tenantId, Long identityId,
                                          KnowledgeVersionStatus status, KnowledgeRiskLevel risk,
                                          SourceAuthorityLevel authorityLevel) {
        return version(
            id,
            tenantId,
            identityId,
            status,
            risk,
            authorityLevel,
            "tenant:" + tenantId,
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE
        );
    }

    private KnowledgeAssetVersion version(Long id, String tenantId, Long identityId,
                                          KnowledgeVersionStatus status, KnowledgeRiskLevel risk,
                                          SourceAuthorityLevel authorityLevel,
                                          String organizationScope, String applicableScope) {
        return versionWithSourceVersion(id, tenantId, identityId, status, risk, authorityLevel,
            null, organizationScope, applicableScope);
    }

    private KnowledgeAssetVersion versionWithSourceVersion(Long id, Long identityId, KnowledgeVersionStatus status,
                                                           SourceAuthorityLevel authorityLevel, Long sourceVersionId,
                                                           String organizationScope, String applicableScope) {
        return versionWithSourceVersion(id, "t-1", identityId, status, KnowledgeRiskLevel.LOW, authorityLevel,
            sourceVersionId, organizationScope, applicableScope);
    }

    private KnowledgeAssetVersion versionWithSourceVersion(Long id, String tenantId, Long identityId,
                                                           KnowledgeVersionStatus status, KnowledgeRiskLevel risk,
                                                           SourceAuthorityLevel authorityLevel, Long sourceVersionId,
                                                           String organizationScope, String applicableScope) {
        return versionWithVersionNo(id, tenantId, identityId, status, risk, authorityLevel, sourceVersionId,
            organizationScope, applicableScope, "v1");
    }

    private KnowledgeAssetVersion versionWithVersionNo(Long id, Long identityId, KnowledgeVersionStatus status,
                                                       KnowledgeRiskLevel risk, String versionNo) {
        return versionWithVersionNo(id, "t-1", identityId, status, risk, SourceAuthorityLevel.B_GUIDELINE,
            null, "tenant:t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE, versionNo);
    }

    private KnowledgeAssetVersion versionWithVersionNo(Long id, String tenantId, Long identityId,
                                                       KnowledgeVersionStatus status, KnowledgeRiskLevel risk,
                                                       SourceAuthorityLevel authorityLevel, Long sourceVersionId,
                                                       String organizationScope, String applicableScope,
                                                       String versionNo) {
        Instant now = Instant.now();
        String activeScopeKey = status == KnowledgeVersionStatus.ACTIVE
            ? KnowledgeAssetVersion.activeScopeKey(identityId, organizationScope, applicableScope)
            : "version:" + id;
        return new KnowledgeAssetVersion(
            id, tenantId, identityId, versionNo, "label",
            null, sourceVersionId, sha256("知识版本夹具内容-" + tenantId + "-" + id), null,
            status, risk,
            authorityLevel, null, null, null,
            organizationScope, applicableScope, activeScopeKey,
            null, null, null, null,
            status == KnowledgeVersionStatus.ACTIVE ? now : null, null,
            null, null,
            now, "init", now, "init"
        , 12, null);
    }

    private Citation citation(Long versionId) {
        return new Citation(1L, "t-1", versionId, 100L, CitationRelation.SUPPORTS, 100, null, null, Instant.now(), "init");
    }

    private SourceVersion sourceVersion(Long id, Instant publishedAt) {
        return new SourceVersion(
            id, "t-1", 7L, "v-" + id, publishedAt, sha256("来源版本-" + id),
            "s3://source-" + id + ".pdf", "zh-CN", Instant.now(), "tester"
        );
    }

    private KnowledgeAssetVersion withReviewSchedule(KnowledgeAssetVersion source, Instant nextReviewAt) {
        return new KnowledgeAssetVersion(
            source.id(), source.tenantId(), source.identityId(), source.versionNo(), source.versionLabel(),
            source.sourceDocumentId(), source.sourceVersionId(), source.contentHash(), source.anchors(),
            source.status(), source.riskLevel(), source.authorityLevel(), source.gradeQuality(), source.gradeStrength(),
            source.conflictArbitration(), source.organizationScope(), source.applicableScope(), source.activeScopeKey(),
            source.effectiveFrom(), source.effectiveTo(), source.reviewedBy(), source.reviewedAt(),
            source.activatedAt(), source.supersededAt(), source.withdrawnAt(), source.withdrawnReason(),
            source.createdAt(), source.createdBy(), source.updatedAt(), source.updatedBy(),
            12, nextReviewAt);
    }

    private KnowledgeVersionCreateRequest versionCreateRequest(String versionNo) {
        return versionCreateRequestWithContent(versionNo, "真实指南内容");
    }

    private KnowledgeVersionCreateRequest versionCreateRequestWithContent(String versionNo, String content) {
        return versionCreateRequestWithTenant("t-1", 7L, 8L, versionNo, content);
    }

    private KnowledgeVersionCreateRequest versionCreateRequestWithTenant(String tenantId, Long sourceDocumentId,
            Long sourceVersionId, String versionNo, String content) {
        return new KnowledgeVersionCreateRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.write"),
            versionNo, "2026 版", sourceDocumentId, sourceVersionId, content, "[]", KnowledgeRiskLevel.LOW,
            GradeEvidenceQuality.HIGH, GradeRecommendationStrength.STRONG, 12
        );
    }

    private KnowledgeActionRequest actionRequest(String tenantId) {
        return new KnowledgeActionRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"), "提交审核"
        );
    }

    private KnowledgeCandidateReviewRequest candidateReviewRequest(String tenantId) {
        return new KnowledgeCandidateReviewRequest(
            "req-1", "trace-1", tenantId, null, "h-1", null, null, "d-1", "CARD",
            "u-99", List.of("knowledge.review"),
            KnowledgeCandidateReviewDecision.APPROVE, "同意",
            900L
        );
    }

    private AssetVersion unifiedVersion(
            String identityCode,
            String versionNo,
            AssetVersionStatus status) {
        return unifiedVersion(
            identityCode,
            versionNo,
            sha256(identityCode + ":" + versionNo),
            "knowledge-version:" + identityCode + ":" + versionNo,
            status
        );
    }

    private AssetVersion unifiedVersion(
            String identityCode,
            String versionNo,
            String contentHash,
            String sourceRef,
            AssetVersionStatus status) {
        Instant now = Instant.now();
        return new AssetVersion(
            null,
            "av-" + identityCode + "-" + versionNo,
            "t-1",
            VersionedAssetType.KNOWLEDGE,
            identityCode,
            versionNo,
            "tenant:t-1",
            KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            contentHash,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            "version:av-" + identityCode + "-" + versionNo,
            sourceRef,
            null,
            null,
            now,
            "init",
            now,
            "init",
            "trace"
        );
    }

    private CandidateClassification classification(Long id, Long identityId, Long candidateVersionId,
                                                   Long activeVersionId, CandidateClassificationType type,
                                                   CandidateReviewStatus status) {
        Instant now = Instant.now();
        return new CandidateClassification(
            id, "t-1", "tenant:t-1", identityId, candidateVersionId, activeVersionId, type, status,
            sha256("candidate-" + candidateVersionId), "测试分类依据", "当前 ACTIVE 与候选对照",
            now, "init", now, "init"
        );
    }

    private void stubPendingAssignment(
            CandidateClassification classification,
            KnowledgeAssetVersion candidate,
            String assignedTo) {
        when(identityRepo.findByTenantIdAndIdForUpdate("t-1", classification.identityId()))
            .thenReturn(Optional.of(identity(classification.identityId(), classification.activeVersionId())));
        when(versionRepo.findByTenantIdAndId("t-1", classification.candidateVersionId()))
            .thenReturn(Optional.of(candidate));
        when(reviewAssignmentRepo.findByTenantIdAndCandidateClassificationIdOrderByCreatedAtAscIdAsc(
            "t-1", classification.id()))
            .thenReturn(List.of(assignment(
                101L,
                classification,
                assignedTo,
                CandidateReviewStatus.PENDING_REPLACEMENT_REVIEW,
                null,
                null)));
    }

    private ReviewAssignment assignment(
            Long id,
            CandidateClassification classification,
            String assignedTo,
            CandidateReviewStatus status,
            KnowledgeCandidateReviewDecision decision,
            String decidedBy) {
        Instant now = Instant.now();
        return new ReviewAssignment(
            id,
            classification.tenantId(),
            classification.orgPath(),
            classification.id(),
            classification.identityId(),
            classification.candidateVersionId(),
            assignedTo,
            status,
            decision,
            decision == null ? null : "审核意见",
            decision == null ? null : KnowledgeReviewFeedbackType.ACCEPTED,
            decision == null ? null : KnowledgeReviewFollowupAction.NONE,
            decidedBy,
            decision == null ? null : now,
            now,
            "init",
            now,
            decidedBy == null ? "init" : decidedBy);
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "u-99",
                "N/A",
                List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    private UserRoleAssignment tenantRoleAssignment(String userId, RoleCode role) {
        return new UserRoleAssignment(
            null,
            "t-1",
            userId,
            role.code(),
            "TENANT",
            "t-1",
            "Y",
            null,
            "test",
            null,
            "test");
    }

    private SourceDocument sourceDocument(Long id, SourceAuthorityLevel authorityLevel) {
        return sourceDocument("t-1", id, authorityLevel);
    }

    private SourceDocument sourceDocument(String tenantId, Long id, SourceAuthorityLevel authorityLevel) {
        Instant now = Instant.now();
        return new SourceDocument(
            id, tenantId, "SRC." + id, SourceType.GUIDELINE, authorityLevel,
            "来源分级依据", "来源文件", "发布机构", "LICENSE", "zh-CN",
            now, "tester", now, "tester"
        );
    }

    private String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unused")
    private List<KnowledgeAssetVersion> nothing() {
        return List.of();
    }
}
