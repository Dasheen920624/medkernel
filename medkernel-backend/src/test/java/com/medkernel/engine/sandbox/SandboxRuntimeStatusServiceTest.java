package com.medkernel.engine.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class SandboxRuntimeStatusServiceTest {

    private final SandboxRuntimeBaselineResolver baselines =
        mock(SandboxRuntimeBaselineResolver.class);
    private final SandboxRuntimeStatusService service = new SandboxRuntimeStatusService(baselines);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-status",
            new OrgScope("tenant-1", null, "hospital-1", null, null, "dept-ed", null, null),
            "operator-1"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void reportsCurrentRuntimeReleaseReadinessWithoutSeparateBinding() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        ClinicalRuntimeRelease release = new ClinicalRuntimeRelease(
            1L, "runtime-7", "tenant-1", "hospital-1", 7L,
            "baseline-3", "a".repeat(64), null,
            now, "operator-1", now, "operator-1", "trace-status");
        when(baselines.resolveCurrent()).thenReturn(new SandboxRuntimeBaseline(
            "sandbox-baseline-1", SandboxRunMode.CURRENT, "tenant-1", "dept-ed",
            release.releaseId(), release.revisionNo(), release.platformBaselineReleaseId(),
            release.manifestSha256(), SandboxResolutionSource.CURRENT_RUNTIME_RELEASE,
            now, new ClinicalRuntimeReleaseContent(release, List.of()), null, null));

        SandboxRuntimeStatusResponse status = service.currentStatus();

        assertThat(status.ready()).isTrue();
        assertThat(status.runtimeReleaseId()).isEqualTo("runtime-7");
        assertThat(status.runtimeRevisionNo()).isEqualTo(7L);
        assertThat(status.assetCount()).isZero();
        assertThat(status.externalSideEffects()).isFalse();
    }

    @Test
    void reportsMissingRuntimeReleaseAsHonestNotReadyState() {
        when(baselines.resolveCurrent())
            .thenThrow(new IllegalStateException("SANDBOX_RUNTIME_RELEASE_MISSING：医院尚未发布运行修订"));

        SandboxRuntimeStatusResponse status = service.currentStatus();

        assertThat(status.ready()).isFalse();
        assertThat(status.reasonCode()).isEqualTo("SANDBOX_RUNTIME_RELEASE_MISSING");
        assertThat(status.externalSideEffects()).isFalse();
    }
}
