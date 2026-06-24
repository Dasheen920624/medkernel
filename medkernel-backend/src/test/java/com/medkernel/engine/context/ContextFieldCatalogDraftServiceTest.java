package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class ContextFieldCatalogDraftServiceTest {

    private final ContextFieldCatalogService catalog = mock(ContextFieldCatalogService.class);
    private final AssetVersionRepository versions = mock(AssetVersionRepository.class);
    private final AssetVersionService versionService = mock(AssetVersionService.class);
    private final ContextFieldCatalogDraftService service =
        new ContextFieldCatalogDraftService(catalog, versions, versionService, new ObjectMapper());

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createsAutomaticallyNumberedDraftFromCurrentWorkingCatalog() throws Exception {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-field-catalog", OrgScope.tenant("tenant-A"), "operator-A"));
        when(catalog.query(null, null)).thenReturn(List.of(
            descriptor("observations[].valueNumeric", "检验数值", "number", null, false),
            descriptor("extensions.local.dialysis_access_type", "透析通路类型", "code",
                "LOCAL_DIALYSIS_ACCESS", false)
        ));
        when(versions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.FIELD_CATALOG,
            "FIELD.CATALOG.CLINICAL_CONTEXT", AssetVersionStatus.DRAFT
        )).thenReturn(List.of());
        AssetVersion saved = version("av-field-catalog-v1", "V1");
        when(versionService.registerDraft(any())).thenReturn(saved);

        assertThat(service.snapshotDraft()).isEqualTo(saved);

        ArgumentCaptor<AssetVersionRegisterCommand> command =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versionService).registerDraft(command.capture());
        AssetVersionRegisterCommand captured = command.getValue();
        assertThat(captured.tenantId()).isEqualTo("tenant-A");
        assertThat(captured.assetType()).isEqualTo(VersionedAssetType.FIELD_CATALOG);
        assertThat(captured.assetIdentity()).isEqualTo("FIELD.CATALOG.CLINICAL_CONTEXT");
        assertThat(captured.organizationScope()).isNull();
        assertThat(captured.applicableScope()).isEqualTo("ALL");
        assertThat(captured.sourceRef()).isEqualTo("field-catalog:working-directory");
        assertThat(captured.createdBy()).isEqualTo("operator-A");
        assertThat(captured.traceId()).isEqualTo("trace-field-catalog");
        assertThat(captured.safetyPolicy()).isEqualTo(AssetVersionSafetyPolicy.NORMAL);
        assertThat(captured.overridePolicy()).isEqualTo(AssetVersionOverridePolicy.FREE);
        assertThat(captured.contentHash()).isNull();

        JsonNode root = new ObjectMapper().readTree(captured.content());
        assertThat(root.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(root.path("fields")).hasSize(2);
        assertThat(root.path("fields").get(0).path("fieldPath").asText())
            .isEqualTo("extensions.local.dialysis_access_type");
        assertThat(root.path("fields").get(0).path("codeSystem").asText())
            .isEqualTo("LOCAL_DIALYSIS_ACCESS");
        assertThat(root.path("fields").get(0).has("source")).isFalse();
        assertThat(root.path("fields").get(0).has("fieldId")).isFalse();
        assertThat(root.path("fields").get(0).has("packageVersion")).isFalse();
        verify(versionService, never()).updateDraft(any());
    }

    @Test
    void updatesTheSingleUnpublishedDraftInsteadOfAllocatingAnotherVersion() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-update", OrgScope.tenant("tenant-A"), "operator-B"));
        when(catalog.query(null, null)).thenReturn(List.of(
            descriptor("patient.birthDate", "出生日期", "date", null, false)
        ));
        AssetVersion existing = version("av-field-catalog-v2", "V2");
        when(versions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.FIELD_CATALOG,
            "FIELD.CATALOG.CLINICAL_CONTEXT", AssetVersionStatus.DRAFT
        )).thenReturn(List.of(existing));
        when(versionService.updateDraft(any())).thenReturn(existing);

        assertThat(service.snapshotDraft()).isEqualTo(existing);

        ArgumentCaptor<AssetVersionDraftUpdateCommand> command =
            ArgumentCaptor.forClass(AssetVersionDraftUpdateCommand.class);
        verify(versionService).updateDraft(command.capture());
        assertThat(command.getValue().versionId()).isEqualTo("av-field-catalog-v2");
        assertThat(command.getValue().assetIdentity()).isEqualTo("FIELD.CATALOG.CLINICAL_CONTEXT");
        assertThat(command.getValue().actor()).isEqualTo("operator-B");
        assertThat(command.getValue().traceId()).isEqualTo("trace-update");
        verify(versionService, never()).registerDraft(any());
    }

    @Test
    void rejectsAmbiguousMultipleWorkingDrafts() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-conflict", OrgScope.tenant("tenant-A"), "operator-C"));
        when(catalog.query(null, null)).thenReturn(List.of(
            descriptor("patient.birthDate", "出生日期", "date", null, false)
        ));
        when(versions.findByTenantIdAndAssetTypeAndAssetIdentityAndStatus(
            "tenant-A", VersionedAssetType.FIELD_CATALOG,
            "FIELD.CATALOG.CLINICAL_CONTEXT", AssetVersionStatus.DRAFT
        )).thenReturn(List.of(
            version("av-field-catalog-a", "V2"),
            version("av-field-catalog-b", "V3")
        ));

        assertThatThrownBy(service::snapshotDraft)
            .hasMessageContaining("存在多个未发布草稿");
        verify(versionService, never()).registerDraft(any());
        verify(versionService, never()).updateDraft(any());
    }

    private static ContextFieldDescriptor descriptor(
            String fieldPath,
            String displayName,
            String dataType,
            String codeSystem,
            boolean derived) {
        return new ContextFieldDescriptor(
            "临床上下文", "基础字段", fieldPath.startsWith("extensions.") ? "Extension" : "Observation",
            fieldPath, displayName, dataType, null, codeSystem, "字段说明",
            "TENANT", "field-1", derived);
    }

    private static AssetVersion version(String versionId, String versionNo) {
        Instant now = Instant.parse("2026-06-23T00:00:00Z");
        return new AssetVersion(
            1L, versionId, "tenant-A", VersionedAssetType.FIELD_CATALOG,
            "FIELD.CATALOG.CLINICAL_CONTEXT", versionNo, "/tenant-A", "ALL",
            "a".repeat(64), AssetVersionSafetyPolicy.NORMAL, AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.DRAFT, "draft:" + versionId, "field-catalog:working-directory",
            null, null, now, "operator-A", now, "operator-A", "trace");
    }
}
