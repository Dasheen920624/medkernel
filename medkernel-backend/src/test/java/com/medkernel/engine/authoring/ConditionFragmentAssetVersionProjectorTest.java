package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.PlatformAuthority;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.PlatformTenant;

class ConditionFragmentAssetVersionProjectorTest {

    private final AssetVersionRepository versions = mock(AssetVersionRepository.class);
    private final ConditionFragmentAssetVersionProjector projector =
        new ConditionFragmentAssetVersionProjector(versions);

    @Test
    void projectsActivePlatformFragmentAsPublishedUnifiedVersion() {
        ConditionFragment fragment = fragment(ConditionFragmentStatus.ACTIVE);
        when(versions.findByTenantIdAndAssetTypeAndAssetIdentityAndVersionNo(
                PlatformTenant.ID, VersionedAssetType.CONDITION_FRAGMENT, "frag-1", "1"))
            .thenReturn(Optional.empty());
        when(versions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        projector.project(fragment);

        ArgumentCaptor<AssetVersion> captor = ArgumentCaptor.forClass(AssetVersion.class);
        verify(versions).save(captor.capture());
        AssetVersion projected = captor.getValue();
        assertThat(projected.assetType()).isEqualTo(VersionedAssetType.CONDITION_FRAGMENT);
        assertThat(projected.assetIdentity()).isEqualTo("frag-1");
        assertThat(projected.versionNo()).isEqualTo("1");
        assertThat(projected.organizationScope()).isEqualTo(PlatformAuthority.PLATFORM_ORG_PATH);
        assertThat(projected.applicableScope()).isEqualTo("pkg-2026.06");
        assertThat(projected.status()).isEqualTo(AssetVersionStatus.PUBLISHED);
        assertThat(projected.contentHash()).matches("[a-f0-9]{64}");
        assertThat(projected.activeScopeKey())
            .isEqualTo("frag-1|" + PlatformAuthority.PLATFORM_ORG_PATH + "|pkg-2026.06");
    }

    private ConditionFragment fragment(ConditionFragmentStatus status) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new ConditionFragment(
            1L,
            "frag-1",
            PlatformTenant.ID,
            "FRAG.ADULT",
            "成人基础条件",
            "验收",
            "{\"all\":[{\"field\":\"patient.age\",\"operator\":\"GTE\",\"value\":18}]}",
            1,
            status,
            "pkg-2026.06",
            now,
            "platform-admin-1",
            now,
            "platform-admin-1",
            "trace-fragment"
        );
    }
}
