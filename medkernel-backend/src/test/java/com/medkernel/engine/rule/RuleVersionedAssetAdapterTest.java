package com.medkernel.engine.rule;

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

class RuleVersionedAssetAdapterTest {

    private AssetVersionService delegate;
    private RuleVersionedAssetAdapter adapter;

    @BeforeEach
    void setUp() {
        delegate = mock(AssetVersionService.class);
        adapter = new RuleVersionedAssetAdapter(delegate);
    }

    @Test
    void registersRuleDraftThroughUnifiedAssetVersionPort() {
        AssetVersion saved = version();
        ArgumentCaptor<AssetVersionRegisterCommand> captor =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        when(delegate.registerDraft(captor.capture())).thenReturn(saved);

        AssetDependencyDeclaration dependency = new AssetDependencyDeclaration(
            VersionedAssetType.VALUE_SET,
            "VS.VTE.RISK",
            "3",
            "3",
            AssetDependencyKind.RUNTIME_ASSET
        );
        AssetVersion result = adapter.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            null,
            "RULE.VTE.RISK",
            "/TENANT-A/HOSP-A/CARDIO",
            "adult|inpatient",
            "{\"when\":{\"operator\":\"all\",\"conditions\":[]}}",
            null,
            "rule/RULE.VTE.RISK",
            "rule-admin",
            "trace-rule-1",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            List.of(dependency)
        ));

        assertThat(result).isEqualTo(saved);
        assertThat(captor.getValue().assetType()).isEqualTo(VersionedAssetType.RULE);
        assertThat(captor.getValue().assetIdentity()).isEqualTo("RULE.VTE.RISK");
        assertThat(captor.getValue().organizationScope()).isEqualTo("/TENANT-A/HOSP-A/CARDIO");
        assertThat(captor.getValue().dependencies()).containsExactly(dependency);
    }

    @Test
    void rejectsNonRuleAssetType() {
        assertThatThrownBy(() -> adapter.registerDraft(new AssetVersionRegisterCommand(
            "tenant-A",
            VersionedAssetType.PATHWAY,
            "PATH.CARDIO.REVIEW",
            "/TENANT-A/HOSP-A",
            "specialty:cardiology",
            "{}",
            null,
            "pathway/PATH.CARDIO.REVIEW",
            "rule-admin",
            "trace-rule-2"
        )))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private AssetVersion version() {
        Instant now = Instant.parse("2026-06-06T04:00:00Z");
        return new AssetVersion(
            1L,
            "av-rule-1",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "2",
            "/TENANT-A/HOSP-A/CARDIO",
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT,
            "version:av-rule-1",
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "rule-admin",
            now,
            "rule-admin",
            "trace-rule-1"
        );
    }
}
