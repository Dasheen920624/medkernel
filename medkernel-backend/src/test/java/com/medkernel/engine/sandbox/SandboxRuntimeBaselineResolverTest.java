package com.medkernel.engine.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.sandbox.replay.SandboxReplayAssetBinding;
import com.medkernel.engine.sandbox.replay.SandboxReplayCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayDeidentificationValidator;
import com.medkernel.engine.sandbox.replay.SandboxReplayResolvedCase;
import com.medkernel.engine.sandbox.replay.SandboxReplayService;
import com.medkernel.engine.sandbox.replay.SandboxReplayStatus;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class SandboxRuntimeBaselineResolverTest {

    private final CurrentClinicalRuntimeReleaseResolver runtimeReleases =
        mock(CurrentClinicalRuntimeReleaseResolver.class);
    private final ClinicalRuntimeReleaseContentResolver runtimeContents =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final SandboxReplayService replayCases = mock(SandboxReplayService.class);
    private final SandboxRuntimeBaselineResolver resolver =
        new SandboxRuntimeBaselineResolver(runtimeReleases, runtimeContents, replayCases);

    private final OrgScope scope =
        new OrgScope("tenant-A", null, "hospital-A", null, null, "hospital-A", null, null);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-sandbox-baseline", scope, "tester"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void currentModeFreezesAuthenticatedHospitalRuntimeReleaseAndExactManifest() {
        ClinicalRuntimeRelease release = runtimeRelease();
        ClinicalRuntimeReleaseContent content = runtimeContent(release);
        when(runtimeReleases.resolve(scope)).thenReturn(release);
        when(runtimeContents.resolve("tenant-A", "runtime-release-A")).thenReturn(content);

        SandboxRuntimeBaseline baseline = resolver.resolveCurrent();

        assertThat(baseline.mode()).isEqualTo(SandboxRunMode.CURRENT);
        assertThat(baseline.runtimeReleaseId()).isEqualTo("runtime-release-A");
        assertThat(baseline.runtimeRevisionNo()).isEqualTo(7L);
        assertThat(baseline.platformBaselineReleaseId()).isEqualTo("platform-baseline-3");
        assertThat(baseline.manifestSha256()).isEqualTo("a".repeat(64));
        assertThat(baseline.resolutionSource())
            .isEqualTo(SandboxResolutionSource.CURRENT_RUNTIME_RELEASE);
        assertThat(baseline.runtimeContent()).isSameAs(content);
        verify(runtimeContents).resolve("tenant-A", "runtime-release-A");
    }

    @Test
    void historicalModeUsesOnlyImmutableReplayManifest() {
        SandboxReplayResolvedCase replay = historicalReplay();
        when(replayCases.resolve("replay-1")).thenReturn(replay);

        SandboxRuntimeBaseline baseline = resolver.resolveHistorical("replay-1");

        assertThat(baseline.mode()).isEqualTo(SandboxRunMode.HISTORICAL_EXACT);
        assertThat(baseline.runtimeReleaseRef()).isEqualTo("sha256:" + "6".repeat(64));
        assertThat(baseline.runtimeRevisionNo()).isEqualTo(4L);
        assertThat(baseline.resolutionSource()).isEqualTo(SandboxResolutionSource.REPLAY_MANIFEST);
        assertThat(baseline.historicalReplay()).isSameAs(replay);
        assertThat(baseline.runtimeContent()).isNull();
        verifyNoInteractions(runtimeReleases, runtimeContents);
    }

    @Test
    void historicalReplayMustBelongToCurrentInstitution() {
        SandboxReplayResolvedCase replay = historicalReplay("replay-tenant-b", "tenant-B");
        when(replayCases.resolve("replay-tenant-b")).thenReturn(replay);

        assertThatThrownBy(() -> resolver.resolveHistorical("replay-tenant-b"))
            .hasMessageContaining("当前机构")
            .hasMessageNotContaining("演练机构");
    }

    @Test
    void compareModeKeepsCurrentAndHistoricalManifestsSeparate() {
        ClinicalRuntimeRelease release = runtimeRelease();
        ClinicalRuntimeReleaseContent content = runtimeContent(release);
        SandboxReplayResolvedCase replay = historicalReplay();
        when(runtimeReleases.resolve(scope)).thenReturn(release);
        when(runtimeContents.resolve("tenant-A", "runtime-release-A")).thenReturn(content);
        when(replayCases.resolve("replay-1")).thenReturn(replay);

        SandboxRuntimeBaseline baseline = resolver.resolveCompare("replay-1");

        assertThat(baseline.mode()).isEqualTo(SandboxRunMode.COMPARE);
        assertThat(baseline.runtimeReleaseId()).isEqualTo("runtime-release-A");
        assertThat(baseline.runtimeContent()).isSameAs(content);
        assertThat(baseline.replayCaseId()).isEqualTo("replay-1");
        assertThat(baseline.historicalReplay()).isSameAs(replay);
    }

    private static ClinicalRuntimeRelease runtimeRelease() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new ClinicalRuntimeRelease(
            1L, "runtime-release-A", "tenant-A", "hospital-A", 7L,
            "platform-baseline-3", "a".repeat(64), null,
            now, "tester", now, "tester", "trace-sandbox-baseline");
    }

    private static ClinicalRuntimeReleaseContent runtimeContent(ClinicalRuntimeRelease release) {
        Instant now = release.activatedAt();
        return new ClinicalRuntimeReleaseContent(release, List.of(
            new ClinicalRuntimeReleaseItem(
                1L, release.releaseId(), "tenant-A", ReleaseSourceLayer.HOSPITAL,
                VersionedAssetType.RULE, "RULE.CURRENT", ReleaseEntryState.ACTIVE,
                "rv-current", "7", "b".repeat(64), now, "tester", "trace-sandbox-baseline")));
    }

    private static SandboxReplayResolvedCase historicalReplay() {
        return historicalReplay("replay-1", "tenant-A");
    }

    private static SandboxReplayResolvedCase historicalReplay(String replayCaseId, String tenantId) {
        Instant now = Instant.parse("2025-01-01T00:00:00Z");
        SandboxReplayCase replayCase = new SandboxReplayCase(
            1L, replayCaseId, tenantId, "sha256:" + "1".repeat(64),
            "sha256:" + "2".repeat(64), "sha256:" + "3".repeat(64),
            "sha256:" + "4".repeat(64), "{}", "c".repeat(64),
            "sha256:" + "6".repeat(64), 4L,
            now, "d".repeat(64), SandboxReplayDeidentificationValidator.PROFILE,
            SandboxReplayStatus.IMPORTED, now, "governor-1", null, null, null,
            now, now, "trace-1");
        SandboxReplayAssetBinding rule = new SandboxReplayAssetBinding(
            1L, "binding-old-1", tenantId, replayCaseId, VersionedAssetType.RULE,
            "RULE.OLD", "rv-old-1", "1", SourceTier.ORG,
            "sha256:" + "5".repeat(64), "{}", "e".repeat(64),
            AssetVersionStatus.WITHDRAWN, now, "governor-1", "trace-1");
        return new SandboxReplayResolvedCase(
            replayCase, new ObjectMapper().createObjectNode(), List.of(rule));
    }
}
