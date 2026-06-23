package com.medkernel.engine.knowledge.production.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.GradeEvidenceQuality;
import com.medkernel.engine.knowledge.GradeRecommendationStrength;
import com.medkernel.engine.knowledge.KnowledgeAssetVersion;
import com.medkernel.engine.knowledge.KnowledgeAssetVersionRepository;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.KnowledgeVersionStatus;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.AssetOwnershipScope;
import com.medkernel.engine.versioning.AssetScopeResolver;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.hash.Sha256ContentHash;

class AuthorityConflictGateTest {

    private static final String PAYLOAD = "{\"template\":\"RULE\",\"sections\":{}}";

    private KnowledgeAssetVersionRepository versions;
    private AssetScopeResolver assetScopes;
    private AuthorityConflictGate gate;

    @BeforeEach
    void setUp() {
        versions = mock(KnowledgeAssetVersionRepository.class);
        assetScopes = mock(AssetScopeResolver.class);
        when(assetScopes.resolve("t-1", OrgScope.tenant("t-1")))
            .thenReturn(new AssetOwnershipScope(ReleaseSourceLayer.GROUP, "/t-1"));
        gate = new AuthorityConflictGate(versions, assetScopes);
    }

    private KnowledgeAssetEnvelope envelope(SourceAuthorityLevel level) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.RULE, "identity:1", "主题", "v1",
            List.of(), level, null, null, KnowledgeRiskLevel.MEDIUM, "t-1",
            Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD, AssetVersionStatus.DRAFT);
    }

    private KnowledgeAssetVersion active(SourceAuthorityLevel level) {
        Instant now = Instant.EPOCH;
        return new KnowledgeAssetVersion(5L, "t-1", 10L, "v0", "现行", 7L, 9L,
            "a".repeat(64), "a", KnowledgeVersionStatus.ACTIVE, KnowledgeRiskLevel.MEDIUM,
            level, GradeEvidenceQuality.MODERATE, GradeRecommendationStrength.WEAK, null,
            "/t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE,
            KnowledgeAssetVersion.activeScopeKey(10L, "/t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE),
            now, null, null, null, now, null, null, null, now, "u", now, "u", 12, now);
    }

    @Test
    void passesForNewIdentityWithoutCurrentAuthority() {
        GateItemResult result = gate.evaluate(envelope(SourceAuthorityLevel.E_FEEDBACK),
            new GateContext("t-1", "job-1"));

        assertThat(result.passed()).isTrue();
    }

    @Test
    void failsWhenLowAuthorityCandidateOverridesHighAuthorityActiveVersion() {
        when(versions.findActiveByEffectiveScope(
            "t-1", 10L, "/t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(active(SourceAuthorityLevel.A_REGULATION)));

        GateItemResult result = gate.evaluate(envelope(SourceAuthorityLevel.E_FEEDBACK),
            new GateContext("t-1", "job-1", 10L));

        assertThat(result.passed()).isFalse();
        assertThat(result.reason())
            .contains("低阶来源覆盖高阶来源")
            .contains("targetIdentityId=10")
            .contains("activeVersionId=5")
            .contains("scope=/t-1");
    }

    @Test
    void passesWhenCandidateAuthorityIsNotLowerThanActiveVersion() {
        when(versions.findActiveByEffectiveScope(
            "t-1", 10L, "/t-1", KnowledgeAssetVersion.DEFAULT_APPLICABLE_SCOPE))
            .thenReturn(Optional.of(active(SourceAuthorityLevel.D_HOSPITAL)));

        GateItemResult result = gate.evaluate(envelope(SourceAuthorityLevel.B_GUIDELINE),
            new GateContext("t-1", "job-1", 10L));

        assertThat(result.passed()).isTrue();
    }
}
