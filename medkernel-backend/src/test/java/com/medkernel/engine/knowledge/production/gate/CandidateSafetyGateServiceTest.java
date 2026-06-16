package com.medkernel.engine.knowledge.production.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import com.medkernel.engine.factory.AssetSourceRef;
import com.medkernel.engine.factory.KnowledgeAssetEnvelope;
import com.medkernel.engine.knowledge.KnowledgeRiskLevel;
import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.hash.Sha256ContentHash;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** AIK-STD-05 候选安全门禁编排单元测试（6 项确定性门禁）。 */
class CandidateSafetyGateServiceTest {

    private static final String PAYLOAD = "{\"template\":\"RULE\",\"sections\":{}}";

    private final AikGateResultRepository repository = mock(AikGateResultRepository.class);
    private final CandidateSafetyGateService service = new CandidateSafetyGateService(
        List.of(new SourcePresentGate(), new AnchorCompleteGate(), new AuthorityLevelGate(),
            new ContentFormatGate(), new ReviewElementsGate(), new ApplicableScopeGate()),
        repository);

    private final GateContext context = new GateContext("t-1", "job-1");

    @BeforeEach
    void bind() {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("t-1"), "u-1"));
    }

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    private KnowledgeAssetEnvelope envelope(List<AssetSourceRef> sources, SourceAuthorityLevel trust,
            String contentHash, String payload, AssetVersionStatus status, String subject,
            String versionLabel, String orgScope) {
        return new KnowledgeAssetEnvelope(VersionedAssetType.RULE, "identity:1", subject, versionLabel,
            sources, trust, null, null, KnowledgeRiskLevel.MEDIUM, orgScope, contentHash, payload, status);
    }

    private KnowledgeAssetEnvelope valid() {
        return envelope(List.of(new AssetSourceRef("GL:v1:s1", SourceAuthorityLevel.B_GUIDELINE)),
            SourceAuthorityLevel.B_GUIDELINE, Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD,
            AssetVersionStatus.DRAFT, "高血压规则", "draft-from-v1", "t-1");
    }

    private boolean failed(GateOutcome outcome, String code) {
        return outcome.failedItems().stream().anyMatch(item -> item.code().equals(code));
    }

    @Test
    void validCandidatePassesAllGatesAndPersistsResults() {
        GateOutcome outcome = service.evaluate(valid(), context);

        assertThat(outcome.passed()).isTrue();
        assertThat(outcome.items()).hasSize(6);
        assertThat(outcome.failedItems()).isEmpty();
        verify(repository, times(6)).save(any(AikGateResult.class));
    }

    @Test
    void noSourcesBlockedAtSourcePresent() {
        GateOutcome outcome = service.evaluate(
            envelope(List.of(), SourceAuthorityLevel.B_GUIDELINE, Sha256ContentHash.sha256(PAYLOAD, "x"),
                PAYLOAD, AssetVersionStatus.DRAFT, "主题", "v1", "t-1"),
            context);

        assertThat(outcome.passed()).isFalse();
        assertThat(failed(outcome, SourcePresentGate.CODE)).isTrue();
        verify(repository, times(6)).save(any(AikGateResult.class));
    }

    @Test
    void incompleteAnchorBlocked() {
        GateOutcome outcome = service.evaluate(
            envelope(List.of(new AssetSourceRef("GL:v1", SourceAuthorityLevel.B_GUIDELINE)),
                SourceAuthorityLevel.B_GUIDELINE, Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD,
                AssetVersionStatus.DRAFT, "主题", "v1", "t-1"),
            context);

        assertThat(outcome.passed()).isFalse();
        assertThat(failed(outcome, AnchorCompleteGate.CODE)).isTrue();
    }

    @Test
    void missingAuthorityBlocked() {
        GateOutcome outcome = service.evaluate(
            envelope(List.of(new AssetSourceRef("GL:v1:s1", SourceAuthorityLevel.B_GUIDELINE)),
                null, Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD,
                AssetVersionStatus.DRAFT, "主题", "v1", "t-1"),
            context);

        assertThat(outcome.passed()).isFalse();
        assertThat(failed(outcome, AuthorityLevelGate.CODE)).isTrue();
    }

    @Test
    void fakeContentHashBlocked() {
        GateOutcome outcome = service.evaluate(
            envelope(List.of(new AssetSourceRef("GL:v1:s1", SourceAuthorityLevel.B_GUIDELINE)),
                SourceAuthorityLevel.B_GUIDELINE, "a".repeat(64), PAYLOAD,
                AssetVersionStatus.DRAFT, "主题", "v1", "t-1"),
            context);

        assertThat(outcome.passed()).isFalse();
        assertThat(failed(outcome, ContentFormatGate.CODE)).isTrue();
    }

    @Test
    void nonCandidateStatusBlocked() {
        GateOutcome outcome = service.evaluate(
            envelope(List.of(new AssetSourceRef("GL:v1:s1", SourceAuthorityLevel.B_GUIDELINE)),
                SourceAuthorityLevel.B_GUIDELINE, Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD,
                AssetVersionStatus.PUBLISHED, "主题", "v1", "t-1"),
            context);

        assertThat(outcome.passed()).isFalse();
        assertThat(failed(outcome, ReviewElementsGate.CODE)).isTrue();
    }

    @Test
    void missingOrgScopeBlocked() {
        GateOutcome outcome = service.evaluate(
            envelope(List.of(new AssetSourceRef("GL:v1:s1", SourceAuthorityLevel.B_GUIDELINE)),
                SourceAuthorityLevel.B_GUIDELINE, Sha256ContentHash.sha256(PAYLOAD, "x"), PAYLOAD,
                AssetVersionStatus.DRAFT, "主题", "v1", "  "),
            context);

        assertThat(outcome.passed()).isFalse();
        assertThat(failed(outcome, ApplicableScopeGate.CODE)).isTrue();
    }
}
