package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class AssetTriggerBindingServiceTest {

    private AssetTriggerBindingRepository repository;
    private AssetTriggerBindingService service;

    @BeforeEach
    void setUp() {
        repository = mock(AssetTriggerBindingRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new AssetTriggerBindingService(
            repository,
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-06-23T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void replaceBindingsPersistsMultipleCanonicalTriggersForOneRuleVersion() {
        AssetVersion version = ruleVersion("av-rule-v1", AssetVersionStatus.DRAFT);

        List<AssetTriggerBinding> saved = service.replaceBindings(
            version,
            List.of(
                new AssetTriggerBindingInput(
                    "order-sign",
                    AssetTriggerPurpose.RULE_EXECUTION,
                    List.of("patient.age", "medications[].code")
                ),
                new AssetTriggerBindingInput(
                    "result-review",
                    AssetTriggerPurpose.RULE_EXECUTION,
                    List.of("observation.code")
                )
            ),
            "tester",
            "trace-trigger"
        );

        assertThat(saved).hasSize(2);
        ArgumentCaptor<AssetTriggerBinding> captor =
            ArgumentCaptor.forClass(AssetTriggerBinding.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(AssetTriggerBinding::triggerPoint)
            .containsExactly("order-sign", "result-review");
        assertThat(captor.getAllValues().get(0).requiredFieldsJson())
            .isEqualTo("[\"patient.age\",\"medications[].code\"]");
        assertThat(captor.getAllValues())
            .allSatisfy(binding -> {
                assertThat(binding.versionId()).isEqualTo("av-rule-v1");
                assertThat(binding.assetIdentity()).isEqualTo("RULE.ANTICOAG");
                assertThat(binding.createdBy()).isEqualTo("tester");
            });
        verify(repository).deleteByTenantIdAndVersionId("tenant-A", "av-rule-v1");
    }

    @Test
    void replaceBindingsRejectsLegacyOrDuplicateTriggerDeclarationsBeforeDeletingCurrentBindings() {
        AssetVersion version = ruleVersion("av-rule-v1", AssetVersionStatus.DRAFT);

        assertThatThrownBy(() -> service.replaceBindings(
            version,
            List.of(
                new AssetTriggerBindingInput(
                    "ORDER_SIGN",
                    AssetTriggerPurpose.RULE_EXECUTION,
                    List.of()
                ),
                new AssetTriggerBindingInput(
                    "ORDER_SIGN",
                    AssetTriggerPurpose.RULE_EXECUTION,
                    List.of()
                )
            ),
            "tester",
            "trace-trigger"
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).deleteByTenantIdAndVersionId(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void replaceBindingsRejectsPurposeThatDoesNotBelongToTheAssetType() {
        AssetVersion version = ruleVersion("av-rule-v1", AssetVersionStatus.DRAFT);

        assertThatThrownBy(() -> service.replaceBindings(
            version,
            List.of(new AssetTriggerBindingInput(
                "order-sign",
                AssetTriggerPurpose.PATHWAY_PROGRESS,
                List.of()
            )),
            "tester",
            "trace-trigger"
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);

        verify(repository, never()).deleteByTenantIdAndVersionId(any(), any());
    }

    @Test
    void copyBindingsPinsTheNewAssetVersionWithoutReusingPhysicalBindingIds() {
        AssetVersion source = ruleVersion("av-rule-v1", AssetVersionStatus.PUBLISHED);
        AssetVersion target = new AssetVersion(
            null, "av-rule-v2", "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG",
            "2", "/tenant-A", "ALL", "sha256:v2", AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE, AssetVersionStatus.DRAFT, "version:av-rule-v2",
            "source", null, null, Instant.now(), "tester", Instant.now(), "tester", "trace-v2"
        );
        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-v1"
        )).thenReturn(List.of(new AssetTriggerBinding(
            1L, "atb-source", "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG",
            "av-rule-v1", "order-sign", AssetTriggerPurpose.RULE_EXECUTION,
            "[\"patient.age\"]", Instant.now(), "tester", Instant.now(), "tester", "trace-v1"
        )));

        List<AssetTriggerBinding> copied =
            service.copyBindings(source, target, "tester", "trace-v2");

        assertThat(copied).singleElement().satisfies(binding -> {
            assertThat(binding.id()).isNull();
            assertThat(binding.triggerBindingId()).startsWith("atb-");
            assertThat(binding.triggerBindingId()).isNotEqualTo("atb-source");
            assertThat(binding.versionId()).isEqualTo("av-rule-v2");
            assertThat(binding.requiredFieldsJson()).isEqualTo("[\"patient.age\"]");
        });
    }

    @Test
    void coversRequiresSourceVersionToContainEveryTargetTriggerForThePurpose() {
        AssetVersion source = ruleVersion("av-rule-source", AssetVersionStatus.PUBLISHED);
        AssetVersion target = new AssetVersion(
            null, "av-rule-target", "tenant-A", VersionedAssetType.RULE, "RULE.TARGET",
            "1", "/tenant-A", "ALL", "sha256:target", AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE, AssetVersionStatus.PUBLISHED,
            "version:av-rule-target", "source", null, null, Instant.now(), "tester",
            Instant.now(), "tester", "trace-target"
        );
        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-source"
        )).thenReturn(List.of(
            binding("av-rule-source", "RULE.ANTICOAG", "order-sign"),
            binding("av-rule-source", "RULE.ANTICOAG", "result-review")
        ));
        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-target"
        )).thenReturn(List.of(
            binding("av-rule-target", "RULE.TARGET", "order-sign"),
            binding("av-rule-target", "RULE.TARGET", "result-review")
        ));

        assertThat(service.covers(
            source, target, AssetTriggerPurpose.RULE_EXECUTION
        )).isTrue();

        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-source"
        )).thenReturn(List.of(
            binding("av-rule-source", "RULE.ANTICOAG", "order-sign")
        ));

        assertThat(service.covers(
            source, target, AssetTriggerPurpose.RULE_EXECUTION
        )).isFalse();
    }

    @Test
    void overlapsRequiresAtLeastOneSharedTriggerForThePurpose() {
        AssetVersion left = ruleVersion("av-rule-left", AssetVersionStatus.PUBLISHED);
        AssetVersion right = new AssetVersion(
            null, "av-rule-right", "tenant-A", VersionedAssetType.RULE, "RULE.RIGHT",
            "1", "/tenant-A", "ALL", "sha256:right", AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE, AssetVersionStatus.PUBLISHED,
            "version:av-rule-right", "source", null, null, Instant.now(), "tester",
            Instant.now(), "tester", "trace-right"
        );
        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-left"
        )).thenReturn(List.of(
            binding("av-rule-left", "RULE.ANTICOAG", "order-sign")
        ));
        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-right"
        )).thenReturn(List.of(
            binding("av-rule-right", "RULE.RIGHT", "order-sign"),
            binding("av-rule-right", "RULE.RIGHT", "result-review")
        ));

        assertThat(service.overlaps(
            left, right, AssetTriggerPurpose.RULE_EXECUTION
        )).isTrue();

        when(repository.findByTenantIdAndVersionIdOrderByPurposeAscTriggerPointAsc(
            "tenant-A", "av-rule-left"
        )).thenReturn(List.of(
            binding("av-rule-left", "RULE.ANTICOAG", "encounter-close")
        ));

        assertThat(service.overlaps(
            left, right, AssetTriggerPurpose.RULE_EXECUTION
        )).isFalse();
    }

    private static AssetTriggerBinding binding(
            String versionId,
            String assetIdentity,
            String triggerPoint) {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        return new AssetTriggerBinding(
            null, "atb-" + versionId + "-" + triggerPoint, "tenant-A",
            VersionedAssetType.RULE, assetIdentity, versionId, triggerPoint,
            AssetTriggerPurpose.RULE_EXECUTION, "[]", now, "tester", now, "tester",
            "trace-trigger"
        );
    }

    private static AssetVersion ruleVersion(String versionId, AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-23T08:00:00Z");
        return new AssetVersion(
            null, versionId, "tenant-A", VersionedAssetType.RULE, "RULE.ANTICOAG",
            "1", "/tenant-A", "ALL", "sha256:v1", AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE, status, "version:" + versionId,
            "source", null, null, now, "tester", now, "tester", "trace-v1"
        );
    }
}
