package com.medkernel.engine.knowledge.production;

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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeIdentity;
import com.medkernel.engine.knowledge.KnowledgeIdentityRepository;
import com.medkernel.engine.knowledge.KnowledgeIdentityStatus;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionCreateRequest;
import com.medkernel.engine.knowledge.KnowledgeVersionService;
import com.medkernel.engine.knowledge.ResolvedSource;
import com.medkernel.engine.knowledge.ReviewAssignmentPlan;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceReferenceResolver;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;

/**
 * 候选真实物化 intake 单元测试（AIK-STD-13 PR4）。
 *
 * <p>验证：现有身份物化 + 路由分派计划（归口∪领域）、新建身份壳 find-or-create、GENERAL 去重单角色、源解析失败诚实拒收。
 * 内容域用 {@code engine.knowledge.KnowledgeDomain}（FQN，与同包路由域 {@code production.KnowledgeDomain} 区分）。
 */
class MaterializingCandidateIntakeTest {

    private static final String TENANT = "tenant-1";

    private KnowledgeVersionService versionService;
    private KnowledgeIdentityRepository identities;
    private SourceReferenceResolver sourceResolver;
    private MaterializingCandidateIntake intake;

    @BeforeEach
    void setUp() {
        versionService = mock(KnowledgeVersionService.class);
        identities = mock(KnowledgeIdentityRepository.class);
        sourceResolver = mock(SourceReferenceResolver.class);
        intake = new MaterializingCandidateIntake(versionService, identities, sourceResolver);
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant(TENANT), "user-001"));
        when(sourceResolver.resolve(eq(TENANT), any())).thenReturn(new ResolvedSource(7L, 9L, "root/0"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private KnowledgeIdentity identity(Long id, String code) {
        return new KnowledgeIdentity(id, TENANT, code,
            com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "二甲双胍", null, null,
            KnowledgeIdentityStatus.ACTIVE, null, Instant.now(), "u", Instant.now(), "u");
    }

    private KnowledgeAssetEnvelope envelope() {
        String payload = "受控候选正文";
        return new KnowledgeAssetEnvelope(VersionedAssetType.KNOWLEDGE, "discovery:SRC-1:v1:root/0", "二甲双胍",
            "run-1", List.of(new AssetSourceRef("SRC-1:v1:root/0", SourceAuthorityLevel.A_REGULATION)),
            SourceAuthorityLevel.A_REGULATION, null, null, KnowledgeRiskLevel.HIGH, TENANT,
            Sha256ContentHash.sha256(payload, "x"), payload, AssetVersionStatus.DRAFT);
    }

    private KnowledgeProductionJob overlayJob() {
        return new KnowledgeProductionJob(1L, TENANT, "job-1", "run-1", VersionedAssetType.KNOWLEDGE,
            KnowledgeProducer.MANUAL, TargetPipeline.TENANT_OVERLAY, KnowledgeDomain.PHARMACY, null,
            ProductionJobStatus.RUNNING, 0, null, Instant.now(), "u", Instant.now(), "u", "t");
    }

    @Test
    void materializesExistingIdentityWithRoutedAssignmentPlan() {
        when(identities.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(identity(5L, "KN-X")));
        ReviewRoutingDecision routing = new ReviewRoutingDecision(
            RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.MEDICATION_SAFETY_USER, true, KnowledgeDomain.PHARMACY);

        String ref = intake.intake(overlayJob(), envelope(), new MaterializationTarget(5L, null), routing);

        assertThat(ref).startsWith("kv:5:");
        ArgumentCaptor<ReviewAssignmentPlan> plan = ArgumentCaptor.forClass(ReviewAssignmentPlan.class);
        verify(versionService).classifyCandidate(eq(5L), any(KnowledgeVersionCreateRequest.class), plan.capture());
        assertThat(plan.getValue().reviewerRoleCodes())
            .containsExactlyInAnyOrder(RoleCode.KNOWLEDGE_GOVERNOR.code(), RoleCode.MEDICATION_SAFETY_USER.code());
    }

    @Test
    void findsOrCreatesNewIdentityShell() {
        when(identities.findByTenantIdAndIdentityCode(TENANT, "KN-MET")).thenReturn(Optional.empty());
        when(identities.save(any())).thenReturn(identity(42L, "KN-MET"));
        ReviewRoutingDecision routing = new ReviewRoutingDecision(
            RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.KNOWLEDGE_GOVERNOR, false, KnowledgeDomain.GENERAL);

        intake.intake(overlayJob(), envelope(),
            new MaterializationTarget(null,
                new NewIdentitySpec(com.medkernel.engine.knowledge.KnowledgeDomain.GUIDELINE, "二甲双胍", "KN-MET")),
            routing);

        verify(identities).save(any(KnowledgeIdentity.class));
        ArgumentCaptor<ReviewAssignmentPlan> plan = ArgumentCaptor.forClass(ReviewAssignmentPlan.class);
        verify(versionService).classifyCandidate(eq(42L), any(), plan.capture());
        // GENERAL：归口==领域，去重后单角色
        assertThat(plan.getValue().reviewerRoleCodes()).containsExactly(RoleCode.KNOWLEDGE_GOVERNOR.code());
    }

    @Test
    void rejectsWhenSourceUnresolvable() {
        when(identities.findByTenantIdAndId(TENANT, 5L)).thenReturn(Optional.of(identity(5L, "KN-X")));
        when(sourceResolver.resolve(eq(TENANT), any()))
            .thenThrow(new ApiException(ErrorCode.ENG_KNOW_001, "受控来源不存在"));
        ReviewRoutingDecision routing = new ReviewRoutingDecision(
            RoleCode.KNOWLEDGE_GOVERNOR, RoleCode.KNOWLEDGE_GOVERNOR, false, KnowledgeDomain.GENERAL);

        assertThatThrownBy(() ->
            intake.intake(overlayJob(), envelope(), new MaterializationTarget(5L, null), routing))
            .isInstanceOf(ApiException.class);
        verify(versionService, never()).classifyCandidate(any(), any(), any());
    }
}
