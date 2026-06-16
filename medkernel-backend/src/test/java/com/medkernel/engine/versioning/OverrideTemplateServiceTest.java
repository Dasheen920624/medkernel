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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.org.OrgHierarchyRepository;
import com.medkernel.engine.org.OrgUnit;
import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.org.OrgUnitStatus;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgLevel;

class OverrideTemplateServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private OverrideTemplateRepository templates;
    private OverrideTemplateItemRepository items;
    private OverrideOperationRepository operations;
    private InheritanceOverrideRepository overrides;
    private InheritanceOverrideService overrideService;
    private OrgUnitRepository orgUnits;
    private OverrideTemplateService service;

    @BeforeEach
    void setUp() {
        templates = mock(OverrideTemplateRepository.class);
        items = mock(OverrideTemplateItemRepository.class);
        operations = mock(OverrideOperationRepository.class);
        overrides = mock(InheritanceOverrideRepository.class);
        overrideService = mock(InheritanceOverrideService.class);
        orgUnits = mock(OrgUnitRepository.class);
        service = new OverrideTemplateService(
            templates,
            items,
            operations,
            overrides,
            overrideService,
            orgUnits,
            mock(OrgHierarchyRepository.class),
            new ObjectMapper().findAndRegisterModules(),
            CLOCK
        );
        when(templates.save(any(OverrideTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(items.save(any(OverrideTemplateItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(operations.save(any(OverrideOperation.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsReusableTemplateWithExplicitInheritanceAndPropagation() {
        OverrideTemplateDetail detail = service.createTemplate(createCommand());

        assertThat(detail.template().templateName()).isEqualTo("儿科剂量包");
        assertThat(detail.items()).singleElement().satisfies(item -> {
            assertThat(item.inheritedVersionId()).isEqualTo("av-platform-v1");
            assertThat(item.overrideMode()).isEqualTo(InheritanceOverrideMode.REPLACE);
            assertThat(item.propagation()).isEqualTo(InheritancePropagation.INHERITABLE);
        });
    }

    @Test
    void listTemplatesReturnsTenantScopedPageInsteadOfArraySnapshot() {
        PageRequest request = new PageRequest(2, 1, null);
        OverrideTemplate template = activeTemplate("tpl-2", "成人模板");
        when(templates.countByTenantIdAndStatus("tenant-A", OverrideTemplateStatus.ACTIVE)).thenReturn(2L);
        when(templates.pageByTenantIdAndStatus("tenant-A", OverrideTemplateStatus.ACTIVE, 1, 1))
            .thenReturn(List.of(template));

        PageResponse<OverrideTemplate> page = service.listTemplates("tenant-A", request);

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).containsExactly(template);
        verify(templates, never()).findByTenantIdAndStatusOrderByUpdatedAtDesc(any(), any());
    }

    @Test
    void rejectsTemplateItemMissingGovernanceEvidence() {
        OverrideTemplateCreateCommand command = new OverrideTemplateCreateCommand(
            "tenant-A",
            "最小覆盖模板",
            null,
            "ALL",
            List.of(new OverrideTemplateItemInput(
                VersionedAssetType.RULE,
                "RULE.VTE.RISK",
                "av-platform-v1",
                null,
                InheritanceOverrideMode.REPLACE,
                InheritancePropagation.INHERITABLE,
                null,
                null,
                null
            )),
            "operator-1",
            "trace-minimal-template"
        );

        assertThatThrownBy(() -> service.createTemplate(command))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("字段不完整")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void previewRequiresTargetOwnedVersionForReplaceWithoutReusingSourceVersion() {
        stubTemplate();
        when(orgUnits.findByTenantIdAndId("tenant-A", "target-1"))
            .thenReturn(Optional.of(org("target-1", "/TENANT-A/HOSP-1")));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndLifecycleStatus(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());

        OverrideBatchPreviewResult preview = service.preview(previewCommand(Map.of()));

        assertThat(preview.releasable()).isFalse();
        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("TARGET_VERSION_REQUIRED");
            assertThat(row.targetVersionId()).isNull();
            assertThat(row.issue()).contains("target-1|RULE|RULE.VTE.RISK");
        });
        assertThat(preview.previewDigest()).matches("[0-9a-f]{64}");
    }

    @Test
    void appliesOnlyTheExactConfirmedPreviewAndRecordsGeneratedOverrideIds() {
        stubTemplate();
        when(orgUnits.findByTenantIdAndId("tenant-A", "target-1"))
            .thenReturn(Optional.of(org("target-1", "/TENANT-A/HOSP-1")));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndLifecycleStatus(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());
        when(overrideService.registerOverride(any())).thenReturn(registeredOverride("io-1"));
        OverrideBatchPreviewCommand previewCommand = previewCommand(
            Map.of("target-1|RULE|RULE.VTE.RISK", "av-target-v2")
        );
        OverrideBatchPreviewResult preview = service.preview(previewCommand);

        OverrideBatchOperationResult result = service.apply(
            new OverrideBatchApplyCommand(previewCommand, preview.previewDigest())
        );

        assertThat(result.status()).isEqualTo(OverrideOperationStatus.APPLIED);
        assertThat(result.overrideIds()).containsExactly("io-1");
        ArgumentCaptor<InheritanceOverrideRegisterCommand> command =
            ArgumentCaptor.forClass(InheritanceOverrideRegisterCommand.class);
        verify(overrideService).registerOverride(command.capture());
        assertThat(command.getValue().inheritedVersionId()).isEqualTo("av-platform-v1");
        assertThat(command.getValue().overrideVersionId()).isEqualTo("av-target-v2");
        assertThat(command.getValue().targetOrgUnitId()).isEqualTo("target-1");
    }

    @Test
    void rejectsApplyWhenConfirmedPreviewDigestDoesNotMatchRecomputedPreview() {
        stubTemplate();
        when(orgUnits.findByTenantIdAndId("tenant-A", "target-1"))
            .thenReturn(Optional.of(org("target-1", "/TENANT-A/HOSP-1")));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndLifecycleStatus(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());

        assertThatThrownBy(() -> service.apply(new OverrideBatchApplyCommand(
            previewCommand(Map.of("target-1|RULE|RULE.VTE.RISK", "av-target-v2")),
            "stale-digest"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("预演摘要")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CONFLICT);

        verify(overrideService, never()).registerOverride(any());
        verify(operations, never()).save(any());
    }

    @Test
    void previewsCrossOrganizationCloneWithExplicitTargetVersionMapping() {
        when(orgUnits.findByTenantIdAndId("tenant-A", "source-1"))
            .thenReturn(Optional.of(org("source-1", "/TENANT-A/HOSP-SOURCE")));
        when(orgUnits.findByTenantIdAndId("tenant-A", "target-1"))
            .thenReturn(Optional.of(org("target-1", "/TENANT-A/HOSP-1")));
        when(overrides.findByTenantIdAndOrgPathAndLifecycleStatus(
            "tenant-A",
            "/TENANT-A/HOSP-SOURCE",
            InheritanceOverrideStatus.PUBLISHED
        )).thenReturn(List.of(registeredOverride("io-source")));
        when(overrides.findByTenantIdAndAssetTypeAndAssetIdentityAndOrgPathAndApplicableScopeAndLifecycleStatus(
            any(), any(), any(), any(), any(), any()
        )).thenReturn(List.of());

        OverrideBatchPreviewResult preview = service.preview(new OverrideBatchPreviewCommand(
            "tenant-A",
            null,
            "source-1",
            List.of("target-1"),
            Map.of("target-1|RULE|RULE.VTE.RISK", "av-target-v2"),
            "operator-1",
            "trace-clone"
        ));

        assertThat(preview.operationType()).isEqualTo("CLONE");
        assertThat(preview.releasable()).isTrue();
        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.sourceId()).isEqualTo("io-source");
            assertThat(row.targetVersionId()).isEqualTo("av-target-v2");
        });
    }

    @Test
    void revokesOnlyOverridesRecordedByTheOriginalBatchOperation() {
        OverrideOperation operation = new OverrideOperation(
            1L,
            "ovo-1",
            "tenant-A",
            OverrideOperationType.APPLY,
            "tpl-1",
            null,
            "[\"target-1\"]",
            OverrideOperationStatus.APPLIED,
            "preview-digest",
            "{\"overrideIds\":[\"io-1\",\"io-2\"]}",
            NOW,
            "operator-1",
            "trace"
        );
        when(operations.findByOperationIdAndTenantId("ovo-1", "tenant-A"))
            .thenReturn(Optional.of(operation));

        OverrideBatchOperationResult result = service.revoke(new OverrideBatchRevokeCommand(
            "tenant-A",
            "ovo-1",
            "operator-2",
            "trace-revoke"
        ));

        assertThat(result.status()).isEqualTo(OverrideOperationStatus.REVOKED);
        assertThat(result.overrideIds()).containsExactly("io-1", "io-2");
        verify(overrideService).retireOverride("tenant-A", "io-1", "operator-2", "trace-revoke");
        verify(overrideService).retireOverride("tenant-A", "io-2", "operator-2", "trace-revoke");
        verify(operations).save(org.mockito.ArgumentMatchers.argThat(
            saved -> saved.status() == OverrideOperationStatus.REVOKED
        ));
    }

    private OverrideTemplateCreateCommand createCommand() {
        return new OverrideTemplateCreateCommand(
            "tenant-A",
            "儿科剂量包",
            "儿科人群本地剂量规则",
            "pediatric|inpatient",
            List.of(new OverrideTemplateItemInput(
                VersionedAssetType.RULE,
                "RULE.VTE.RISK",
                "av-platform-v1",
                "av-source-v2",
                InheritanceOverrideMode.REPLACE,
                InheritancePropagation.INHERITABLE,
                "pediatric|inpatient",
                "阈值按儿科制度收紧",
                "本院儿科制度"
            )),
            "operator-1",
            "trace-template"
        );
    }

    private void stubTemplate() {
        OverrideTemplate template = activeTemplate("tpl-1", "儿科剂量包");
        OverrideTemplateItem item = new OverrideTemplateItem(
            1L,
            "tpi-1",
            "tpl-1",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-platform-v1",
            "av-source-v2",
            InheritanceOverrideMode.REPLACE,
            InheritancePropagation.INHERITABLE,
            "pediatric|inpatient",
            "阈值按儿科制度收紧",
            "本院儿科制度"
        );
        when(templates.findByTemplateIdAndTenantId("tpl-1", "tenant-A"))
            .thenReturn(Optional.of(template));
        when(items.findByTemplateIdOrderByAssetTypeAscAssetIdentityAsc("tpl-1"))
            .thenReturn(List.of(item));
    }

    private OverrideTemplate activeTemplate(String templateId, String templateName) {
        return new OverrideTemplate(
            1L,
            templateId,
            "tenant-A",
            templateName,
            "儿科人群本地剂量规则",
            "pediatric|inpatient",
            OverrideTemplateStatus.ACTIVE,
            NOW,
            "operator-1",
            NOW,
            "operator-1",
            "trace-template"
        );
    }

    private OverrideBatchPreviewCommand previewCommand(Map<String, String> targetVersions) {
        return new OverrideBatchPreviewCommand(
            "tenant-A",
            "tpl-1",
            null,
            List.of("target-1"),
            targetVersions,
            "operator-1",
            "trace-template"
        );
    }

    private OrgUnit org(String id, String path) {
        return new OrgUnit(
            id,
            null,
            "tenant-A",
            path,
            OrgLevel.FACILITY,
            id,
            id,
            null,
            null,
            null,
            OrgUnitStatus.ACTIVE,
            NOW,
            "operator-1",
            NOW,
            "operator-1"
        );
    }

    private InheritanceOverride registeredOverride(String overrideId) {
        return new InheritanceOverride(
            1L,
            overrideId,
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-platform-v1",
            "av-target-v2",
            InheritanceOverrideMode.REPLACE,
            InheritancePropagation.INHERITABLE,
            InheritanceOverrideStatus.PUBLISHED,
            "/TENANT-A/HOSP-1",
            "pediatric|inpatient",
            "差异",
            "原因",
            "目标组织",
            NOW,
            "operator-1",
            NOW,
            "operator-1",
            "trace"
        );
    }
}
