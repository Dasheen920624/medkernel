package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class VersionReplayServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T10:30:00Z"), ZoneOffset.UTC);

    private AssetVersionRepository assetVersions;
    private VersionReplayBindingRepository bindings;
    private VersionReplayService service;

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        bindings = mock(VersionReplayBindingRepository.class);
        service = new VersionReplayService(assetVersions, bindings, CLOCK);
    }

    @Test
    void bindsRuntimeResultToHistoricalVersionForReplay() {
        AssetVersion historical = version("av-v1", "1.0.0", AssetVersionStatus.OFFLINE);
        when(assetVersions.findByVersionIdAndTenantId("av-v1", "tenant-A")).thenReturn(Optional.of(historical));
        when(bindings.save(org.mockito.ArgumentMatchers.any(VersionReplayBinding.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VersionReplayBinding binding = service.bindRuntimeResult(new VersionReplayBindingCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v1",
            "ctx-patient-001",
            "runtime-event-001",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "engine-worker",
            "trace-replay"
        ));

        assertThat(binding.bindingId()).matches("vrb-[0-9A-HJKMNP-TV-Z]{26}");
        assertThat(binding.versionId()).isEqualTo("av-v1");
        assertThat(binding.patientSnapshotId()).isEqualTo("ctx-patient-001");
        assertThat(binding.resultHash()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(binding.createdAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void rejectsDraftVersionForReplayBinding() {
        AssetVersion draft = version("av-draft", "0.1.0", AssetVersionStatus.DRAFT);
        when(assetVersions.findByVersionIdAndTenantId("av-draft", "tenant-A")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.bindRuntimeResult(new VersionReplayBindingCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-draft",
            "ctx-patient-001",
            "runtime-event-001",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "engine-worker",
            "trace-replay"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("未审核版本不得绑定历史重放")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(bindings, never()).save(org.mockito.ArgumentMatchers.any(VersionReplayBinding.class));
    }

    @Test
    void rejectsNonSha256ResultHashForReplayBinding() {
        AssetVersion historical = version("av-v1", "1.0.0", AssetVersionStatus.OFFLINE);
        when(assetVersions.findByVersionIdAndTenantId("av-v1", "tenant-A")).thenReturn(Optional.of(historical));

        assertThatThrownBy(() -> service.bindRuntimeResult(new VersionReplayBindingCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v1",
            "ctx-patient-001",
            "runtime-event-001",
            "not-a-real-hash",
            "engine-worker",
            "trace-replay"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("SHA-256")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(bindings, never()).save(org.mockito.ArgumentMatchers.any(VersionReplayBinding.class));
    }

    @Test
    void replaysBoundHistoricalVersionWithoutChangingCurrentResolution() {
        AssetVersion historical = version("av-v1", "1.0.0", AssetVersionStatus.OFFLINE);
        VersionReplayBinding binding = binding("vrb-1", historical.versionId());
        when(bindings.findByTenantIdAndBindingId("tenant-A", binding.bindingId())).thenReturn(Optional.of(binding));
        when(assetVersions.findByVersionIdAndTenantId(historical.versionId(), "tenant-A")).thenReturn(Optional.of(historical));

        VersionReplayResult result = service.replay(new VersionReplayQuery("tenant-A", binding.bindingId()));

        assertThat(result.version()).isEqualTo(historical);
        assertThat(result.binding().patientSnapshotId()).isEqualTo("ctx-patient-001");
        assertThat(result.replaySummary()).contains("历史重放").contains("1.0.0");
    }

    private AssetVersion version(String versionId, String versionNo, AssetVersionStatus status) {
        Instant now = CLOCK.instant();
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            "/TENANT-A/GROUP-A/HOSP-A",
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            status == AssetVersionStatus.ACTIVE
                ? "RULE.VTE.RISK|/TENANT-A/GROUP-A/HOSP-A|adult|inpatient"
                : "version:" + versionId,
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-sys04-pr3"
        );
    }

    private VersionReplayBinding binding(String bindingId, String versionId) {
        Instant now = CLOCK.instant();
        return new VersionReplayBinding(
            1L,
            bindingId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionId,
            "ctx-patient-001",
            "runtime-event-001",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            now,
            "engine-worker",
            now,
            "engine-worker",
            "trace-replay"
        );
    }
}
