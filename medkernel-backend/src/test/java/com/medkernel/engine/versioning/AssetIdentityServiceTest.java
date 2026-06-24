package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class AssetIdentityServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T09:00:00Z");
    private final AssetIdentityRepository identities = mock(AssetIdentityRepository.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private AssetIdentityService service;

    @BeforeEach
    void setUp() {
        service = new AssetIdentityService(identities, clock);
        when(identities.save(any(AssetIdentity.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void allocatesV1ForNewIdentityAndV2ForItsNextVersion() {
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.RULE, "RULE.RENAL.DOSE"))
            .thenReturn(
                Optional.empty(),
                Optional.of(identity(1L, AssetIdentityStatus.ACTIVE))
            );

        AssetVersionAllocation first = service.allocateNextVersion(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.RENAL.DOSE",
            "operator-A",
            "trace-1");
        AssetVersionAllocation second = service.allocateNextVersion(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.RENAL.DOSE",
            "operator-A",
            "trace-2");

        assertThat(first.sequence()).isEqualTo(1L);
        assertThat(first.versionNo()).isEqualTo("V1");
        assertThat(second.sequence()).isEqualTo(2L);
        assertThat(second.versionNo()).isEqualTo("V2");
    }

    @Test
    void rejectsNewVersionForRetiredIdentity() {
        when(identities.findByTenantIdAndAssetTypeAndAssetIdentity(
            "tenant-A", VersionedAssetType.PATHWAY, "PATH.COPD"))
            .thenReturn(Optional.of(identity(4L, AssetIdentityStatus.RETIRED)));

        assertThatThrownBy(() -> service.allocateNextVersion(
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.COPD",
            "operator-A",
            "trace-3"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("已退役")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void stableIdentityDoesNotFreezeGroupOrHospitalOwnership() {
        assertThat(AssetIdentity.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("sourceLayer");
    }

    private AssetIdentity identity(
            long latestVersionSequence,
            AssetIdentityStatus status) {
        return new AssetIdentity(
            1L,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.RENAL.DOSE",
            status,
            latestVersionSequence,
            NOW.minusSeconds(60),
            "operator-A",
            NOW.minusSeconds(60),
            "operator-A",
            "trace-old"
        );
    }
}
