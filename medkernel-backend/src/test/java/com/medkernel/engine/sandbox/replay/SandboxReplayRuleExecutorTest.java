package com.medkernel.engine.sandbox.replay;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.rule.RuleDslEvaluator;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxReplayRuleExecutorTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SandboxReplayRuleExecutor executor =
        new SandboxReplayRuleExecutor(json, new RuleDslEvaluator(json));

    @Test
    void evaluatesRetiredHistoricalRuleThroughTheSharedDeterministicDslKernel() throws Exception {
        var context = json.readTree("""
            {"resources":{"patient":{"mpi":"DEID-P-1","name":"DEID-PATIENT"},
             "observations":[{"code":"K","value":6.8}]}}
            """);
        var content = json.readTree("""
            {"ruleCode":"RULE.OLD.K","name":"历史高钾规则","dsl":{"trigger":"patient-view",
             "when":{"all":[{"expr":{"field":"observations[].value"},"operator":"gte","value":6.5}]},
             "then":[{"actionCode":"BLOCK","atSeverity":"CRITICAL","indicator":"critical",
             "summary":"历史高钾红线","detail":"仅用于只读重放","source":{"label":"历史来源"},
             "suggestions":[],"overrideReasons":["复核结果"],"requiresPhysicianConfirmation":true}]}}
            """);
        SandboxReplayResolvedCase replay = new SandboxReplayResolvedCase(
            replayCase(), context, List.of(new SandboxReplayAssetBinding(
                1L, "binding-1", "tenant-1", "replay-1", VersionedAssetType.RULE,
                "RULE.OLD.K", "version-id-1", "7", SourceTier.ORG,
                "sha256:" + "5".repeat(64), content.toString(), "a".repeat(64),
                AssetVersionStatus.WITHDRAWN, Instant.now(), "governor-1", "trace-1")));

        List<SandboxReplayRuleResult> results = executor.execute(replay);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.ruleCode()).isEqualTo("RULE.OLD.K");
            assertThat(result.assetVersion()).isEqualTo("7");
            assertThat(result.historicalStatus()).isEqualTo(AssetVersionStatus.WITHDRAWN);
            assertThat(result.hit()).isTrue();
            assertThat(result.severity()).isEqualTo("CRITICAL");
            assertThat(result.actions()).hasSize(1);
            assertThat(result.explanation()).isNotNull();
        });
    }

    @Test
    void materializesActionCardReferenceFromHistoricalReplayAssetSnapshot() throws Exception {
        var context = json.readTree("""
            {"resources":{"patient":{"mpi":"DEID-P-1","name":"DEID-PATIENT"},
             "observations":[{"code":"K","value":6.8}]}}
            """);
        var ruleContent = json.readTree("""
            {"ruleCode":"RULE.OLD.K","name":"历史高钾规则","dsl":{
             "when":{"all":[{"expr":{"field":"observations[].value"},"operator":"gte","value":6.5}]},
             "then":[{"actionCardRef":"CARD.K.RECHECK"}]}}
            """);
        var actionCardContent = json.readTree("""
            {"actionCode":"REMIND","atSeverity":"HIGH","indicator":"warning",
             "summary":"历史高钾复核提醒","detail":"按历史运行修订复核。",
             "source":{"label":"历史检验危急值制度","evidenceLevel":"院内制度"},
             "suggestions":[],"overrideReasons":["已复核标本"],"requiresPhysicianConfirmation":true}
            """);
        SandboxReplayResolvedCase replay = new SandboxReplayResolvedCase(
            replayCase(), context, List.of(
                binding(VersionedAssetType.RULE, "RULE.OLD.K", "version-id-1", "7", ruleContent.toString()),
                binding(VersionedAssetType.ACTION_CARD, "CARD.K.RECHECK", "version-id-card", "3", actionCardContent.toString())
            ));

        List<SandboxReplayRuleResult> results = executor.execute(replay);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.hit()).isTrue();
            assertThat(result.severity()).isEqualTo("HIGH");
            assertThat(result.actions()).singleElement().satisfies(action -> {
                assertThat(action.summary()).isEqualTo("历史高钾复核提醒");
                assertThat(action.requiresPhysicianConfirmation()).isTrue();
            });
        });
    }

    private SandboxReplayAssetBinding binding(
            VersionedAssetType assetType,
            String identity,
            String versionId,
            String version,
            String content) {
        return new SandboxReplayAssetBinding(
            1L, "binding-" + identity, "tenant-1", "replay-1", assetType,
            identity, versionId, version, SourceTier.ORG,
            "sha256:" + "5".repeat(64), content, "a".repeat(64),
            AssetVersionStatus.WITHDRAWN, Instant.now(), "governor-1", "trace-1");
    }

    private SandboxReplayCase replayCase() {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        return new SandboxReplayCase(
            1L, "replay-1", "tenant-1", "sha256:" + "1".repeat(64),
            "sha256:" + "2".repeat(64), "sha256:" + "3".repeat(64),
            "sha256:" + "4".repeat(64), "{}", "b".repeat(64),
            "sha256:" + "6".repeat(64), 4L,
            now, "c".repeat(64), SandboxReplayDeidentificationValidator.PROFILE,
            SandboxReplayStatus.IMPORTED, now, "governor-1", null, null, null,
            now, now, "trace-1");
    }
}
