package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetDependencyDeclaration;
import com.medkernel.engine.versioning.AssetDependencyKind;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class PathwayVersionedAssetAdapterTest {

    private AssetVersionService delegate;
    private PathwayVersionedAssetAdapter adapter;

    @BeforeEach
    void setUp() {
        delegate = mock(AssetVersionService.class);
        adapter = new PathwayVersionedAssetAdapter(delegate);
    }

    @Test
    void registersPathwayDraftThroughUnifiedAssetVersionPort() {
        AssetVersion saved = version();
        ArgumentCaptor<AssetVersionRegisterCommand> captor =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        when(delegate.registerDraft(captor.capture())).thenReturn(saved);

        AssetDependencyDeclaration dependency = new AssetDependencyDeclaration(
            VersionedAssetType.ORDER_SET,
            "OS.CARDIO.ADMISSION",
            "2",
            "2",
            AssetDependencyKind.RUNTIME_ASSET
        );
        AssetVersion result = adapter.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            null,
            "PATH.CARDIO.REVIEW",
            "/TENANT-A/HOSP-A/CARDIO",
            "specialty:cardiology",
            "{\"nodes\":[],\"edges\":[]}",
            null,
            "pathway/PATH.CARDIO.REVIEW",
            "pathway-admin",
            "trace-pathway-1",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            List.of(dependency)
        ));

        assertThat(result).isEqualTo(saved);
        assertThat(captor.getValue().assetType()).isEqualTo(VersionedAssetType.PATHWAY);
        assertThat(captor.getValue().assetIdentity()).isEqualTo("PATH.CARDIO.REVIEW");
        assertThat(captor.getValue().applicableScope()).isEqualTo("specialty:cardiology");
        assertThat(captor.getValue().dependencies()).containsExactly(dependency);
    }

    @Test
    void rejectsNonPathwayAssetType() {
        assertThatThrownBy(() -> adapter.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "/TENANT-A/HOSP-A",
            "adult|inpatient",
            "{}",
            null,
            "rule/RULE.VTE.RISK",
            "pathway-admin",
            "trace-pathway-2"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private AssetVersion version() {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new AssetVersion(
            1L,
            "av-pathway-1",
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.CARDIO.REVIEW",
            "3",
            "/TENANT-A/HOSP-A/CARDIO",
            "specialty:cardiology",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT,
            "version:av-pathway-1",
            "pathway/PATH.CARDIO.REVIEW",
            null,
            null,
            now,
            "pathway-admin",
            now,
            "pathway-admin",
            "trace-pathway-1"
        );
    }
}
