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
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 字段目录合并逻辑单测（P2/P5）：平台字段保持 canonical 形状，租户扩展统一落在
 * {@code extensions.local.*} 命名空间。
 */
class ContextFieldCatalogServiceMergeTest {

    private final List<ContextFieldDescriptor> systemFields =
        new ContextFieldCatalog().query(null, null);

    private ContextFieldCatalogEntry tenant(String fieldPath, String resourceType, String category) {
        Instant now = Instant.now();
        return new ContextFieldCatalogEntry(
            1L, "f1", "t", category, "自定义分组", resourceType, fieldPath, "院内自定义", "string",
            null, null, "扩展", "ACTIVE", now, "u", now, "u", "trace");
    }

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void tenantFieldOutsideExtensionNamespaceIsIgnoredInsteadOfBecomingUnusableFact() {
        var merged = ContextFieldCatalogService.merge(
            systemFields, List.of(tenant("medications[].customFlag", "Medication", "医嘱信息")), null, null);
        assertThat(merged).hasSize(systemFields.size());
        assertThat(merged).noneMatch(f -> f.fieldPath().equals("medications[].customFlag"));
    }

    @Test
    void tenantNamespacedExtensionIsAddedAsRealWritableField() {
        var merged = ContextFieldCatalogService.merge(
            systemFields,
            List.of(tenant("extensions.local.dialysis_access_type", "Extension", "院内扩展")),
            null,
            "院内");

        assertThat(merged).anySatisfy(field -> {
            assertThat(field.fieldPath()).isEqualTo("extensions.local.dialysis_access_type");
            assertThat(field.resourceType()).isEqualTo("Extension");
            assertThat(field.source()).isEqualTo("TENANT");
            assertThat(field.payloadKey()).isEqualTo("extensions");
            assertThat(field.propertyName()).isEqualTo("dialysis_access_type");
            assertThat(field.externalWritable()).isTrue();
        });
    }

    @Test
    void tenantOverrideForSystemFieldReplacesMetadataWithoutAddingDuplicate() {
        // conditions[].code 已是平台字段
        var merged = ContextFieldCatalogService.merge(
            systemFields, List.of(new ContextFieldCatalogEntry(
                1L, "f1", "t", "诊断信息", "诊断", "Condition", "conditions[].code",
                "院内诊断编码", "code", null, "ICD-10-CM", "院内展示说明", "ACTIVE",
                Instant.now(), "u", Instant.now(), "u", "trace")), null, null);
        assertThat(merged).hasSize(systemFields.size());
        assertThat(merged).filteredOn(f -> f.fieldPath().equals("conditions[].code"))
            .singleElement()
            .satisfies(field -> {
                assertThat(field.displayName()).isEqualTo("院内诊断编码");
                assertThat(field.description()).isEqualTo("院内展示说明");
                assertThat(field.codeSystem()).isEqualTo("ICD-10-CM");
                assertThat(field.source()).isEqualTo("TENANT");
                assertThat(field.fieldId()).isEqualTo("f1");
            });
    }

    @Test
    void buildEntryValidatesAndMaps() {
        ContextFieldDescriptor systemField = systemFields.stream()
            .filter(f -> f.fieldPath().equals("medications[].code"))
            .findFirst()
            .orElseThrow();
        var req = new ContextFieldCatalogUpsertRequest(
            "医嘱信息", "用药医嘱", "Medication", " medications[].code ", "院内药品编码",
            "code", "", "ATC-LOCAL", "扩展说明");
        var entry = ContextFieldCatalogService.buildEntry(req, systemField, "t-1", "u-1", "trace-1");
        assertThat(entry.tenantId()).isEqualTo("t-1");
        assertThat(entry.fieldPath()).isEqualTo("medications[].code"); // 去空格
        assertThat(entry.category()).isEqualTo("医嘱信息");
        assertThat(entry.groupName()).isEqualTo("用药医嘱");
        assertThat(entry.status()).isEqualTo("ACTIVE");
        assertThat(entry.displayName()).isEqualTo("院内药品编码");
        assertThat(entry.codeSystem()).isEqualTo("ATC-LOCAL");
    }

    @Test
    void buildEntryRejectsChangedDataType() {
        ContextFieldDescriptor systemField = systemFields.stream()
            .filter(f -> f.fieldPath().equals("medications[].code"))
            .findFirst()
            .orElseThrow();
        var req = new ContextFieldCatalogUpsertRequest(
            "医嘱信息", "用药医嘱", "Medication", "medications[].code", "名", "string", null, null, null);
        assertThatThrownBy(() -> ContextFieldCatalogService.buildEntry(req, systemField, "t", "u", "tr"))
            .hasMessageContaining("字段数据类型不能修改");
    }

    @Test
    void createRejectsUnknownFieldPathOutsideExtensionNamespaceBeforeSaving() {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-A"), "u-1"));
        ContextFieldCatalogRepository repository = mock(ContextFieldCatalogRepository.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        ContextFieldCatalogService service =
            new ContextFieldCatalogService(
                new ContextFieldCatalog(), repository, auditRecorder);
        var req = new ContextFieldCatalogUpsertRequest(
            "医嘱信息", "用药医嘱", "Medication", "medications[].customFlag", "院内自定义",
            "string", null, null, "不可用字段");

        assertThatThrownBy(() -> service.create(req))
            .hasMessageContaining("字段路径不属于 canonical 或 extensions.local 字段目录");
        verify(repository, never()).save(any());
        verify(auditRecorder, never()).record(any());
    }

    @Test
    void createAcceptsNamespacedTenantExtensionWithDeclaredShape() {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-A"), "u-1"));
        ContextFieldCatalogRepository repository = mock(ContextFieldCatalogRepository.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        when(repository.findByTenantIdAndFieldPath(
            "tenant-A", "extensions.local.dialysis_access_type")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ContextFieldCatalogService service =
            new ContextFieldCatalogService(
                new ContextFieldCatalog(), repository, auditRecorder);
        var request = new ContextFieldCatalogUpsertRequest(
            "院内扩展", "肾脏替代治疗", "Extension",
            "extensions.local.dialysis_access_type",
            "透析通路类型", "code", null, "LOCAL_DIALYSIS_ACCESS", "院内结构化扩展字段");

        ContextFieldDescriptor created = service.create(request);

        assertThat(created.fieldPath()).isEqualTo("extensions.local.dialysis_access_type");
        assertThat(created.resourceType()).isEqualTo("Extension");
        assertThat(created.dataType()).isEqualTo("code");
        assertThat(created.source()).isEqualTo("TENANT");
        assertThat(created.payloadKey()).isEqualTo("extensions");
        assertThat(created.propertyName()).isEqualTo("dialysis_access_type");
        verify(repository).save(any());
        verify(auditRecorder).record(any());
    }

    @Test
    void createRejectsDictionaryOnNonCodeExtension() {
        var request = new ContextFieldCatalogUpsertRequest(
            "院内扩展", "肾脏替代治疗", "Extension",
            "extensions.local.dialysis_access_type",
            "透析通路类型", "string", null, "LOCAL_DIALYSIS_ACCESS", "非法字典绑定");

        assertThatThrownBy(() ->
            ContextFieldCatalogService.buildEntry(request, null, "tenant-A", "u-1", "trace-1"))
            .hasMessageContaining("只有 code 扩展字段可以绑定标准字典");
    }

    @Test
    void updateExistingOverrideKeepsFieldIdAndSystemFieldShape() {
        RequestContext.restore(new RequestContext.Snapshot("trace-1", OrgScope.tenant("tenant-A"), "u-2"));
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        ContextFieldCatalogRepository repository = mock(ContextFieldCatalogRepository.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        when(repository.findByTenantIdAndFieldId("tenant-A", "f1"))
            .thenReturn(Optional.of(new ContextFieldCatalogEntry(
                1L, "f1", "tenant-A", "诊断信息", "诊断", "Condition", "conditions[].code",
                "诊断编码", "code", null, "ICD-10", null, "ACTIVE", now, "u-1", now, "u-1", "trace-old")));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ContextFieldCatalogService service =
            new ContextFieldCatalogService(
                new ContextFieldCatalog(), repository, auditRecorder);

        ContextFieldDescriptor updated = service.update("f1", new ContextFieldCatalogUpsertRequest(
            "诊断信息", "诊断", "Condition", "conditions[].code", "院内诊断编码",
            "code", null, "ICD-10-CM", "院内展示说明"));

        assertThat(updated.fieldPath()).isEqualTo("conditions[].code");
        assertThat(updated.displayName()).isEqualTo("院内诊断编码");
        assertThat(updated.codeSystem()).isEqualTo("ICD-10-CM");
        assertThat(updated.source()).isEqualTo("TENANT");
        assertThat(updated.fieldId()).isEqualTo("f1");
    }

    @Test
    void createUpdateAndDeleteRecordAuditForTenantMetadataOverride() {
        RequestContext.restore(new RequestContext.Snapshot("trace-audit", OrgScope.tenant("tenant-A"), "u-audit"));
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        ContextFieldCatalogRepository repository = mock(ContextFieldCatalogRepository.class);
        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        ContextFieldCatalogService service =
            new ContextFieldCatalogService(
                new ContextFieldCatalog(), repository, auditRecorder);
        ContextFieldCatalogEntry existing = new ContextFieldCatalogEntry(
            1L, "f1", "tenant-A", "诊断信息", "诊断", "Condition", "conditions[].code",
            "诊断编码", "code", null, "ICD-10", null, "ACTIVE", now, "u-1", now, "u-1", "trace-old");
        when(repository.findByTenantIdAndFieldPath("tenant-A", "conditions[].code")).thenReturn(Optional.empty());
        when(repository.findByTenantIdAndFieldId("tenant-A", "f1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ContextFieldCatalogUpsertRequest request = new ContextFieldCatalogUpsertRequest(
            "诊断信息", "诊断", "Condition", "conditions[].code", "院内诊断编码",
            "code", null, "ICD-10-CM", "院内展示说明");

        ContextFieldDescriptor created = service.create(request);
        ContextFieldDescriptor updated = service.update("f1", request);
        service.delete("f1");

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder, org.mockito.Mockito.times(3)).record(audit.capture());
        assertThat(audit.getAllValues()).extracting(AuditRecordCommand::action)
            .containsExactly(AuditAction.CREATE, AuditAction.UPDATE, AuditAction.DELETE);
        assertThat(audit.getAllValues()).allSatisfy(command -> {
            assertThat(command.targetType()).isEqualTo("context_field_catalog");
            assertThat(command.targetId()).isNotBlank();
            assertThat(command.summary()).contains("上下文字段目录");
        });
        assertThat(audit.getAllValues().get(0).before()).isNull();
        assertThat(audit.getAllValues().get(0).after()).isEqualTo(created);
        assertThat(audit.getAllValues().get(1).before()).isEqualTo(existing.toDescriptor());
        assertThat(audit.getAllValues().get(1).after()).isEqualTo(updated);
        assertThat(audit.getAllValues().get(2).before()).isEqualTo(existing.toDescriptor());
        assertThat(audit.getAllValues().get(2).after()).isNull();
    }

    @Test
    void tenantFieldFilteredByResourceTypeAndKeyword() {
        var entries = List.of(new ContextFieldCatalogEntry(
            1L, "f1", "t", "医嘱信息", "用药医嘱", "Medication", "medications[].code",
            "院内药品编码", "code", null, "ATC-LOCAL", "院内", "ACTIVE",
            Instant.now(), "u", Instant.now(), "u", "trace"));
        assertThat(ContextFieldCatalogService.merge(systemFields, entries, "Observation", null))
            .hasSize(new ContextFieldCatalog().query("Observation", null).size()); // 资源类型不匹配，不补充
        assertThat(ContextFieldCatalogService.merge(systemFields, entries, null, "自定义"))
            .isEmpty(); // 关键词不命中覆盖字段
        assertThat(ContextFieldCatalogService.merge(systemFields, entries, null, "院内"))
            .anyMatch(f -> f.fieldPath().equals("medications[].code")
                && "院内药品编码".equals(f.displayName())); // 关键词命中覆盖元数据
    }

}
