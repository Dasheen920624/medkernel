package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.DeclarativeAssetRuntimePort;
import com.medkernel.engine.versioning.ResolvedDeclarativeAsset;
import com.medkernel.engine.versioning.VersionedAssetType;

class RuntimeReleaseFieldCatalogResolverTest {

    private final DeclarativeAssetRuntimePort assets = mock(DeclarativeAssetRuntimePort.class);
    private final RuntimeReleaseFieldCatalogResolver resolver =
        new RuntimeReleaseFieldCatalogResolver(assets, new ObjectMapper());

    @Test
    void resolvesFieldDescriptorsFromFrozenRuntimeReleaseContent() {
        String content = """
            {
              "schemaVersion": "1.0",
              "fields": [
                {
                  "category": "检验检查",
                  "group": "检验",
                  "resourceType": "Observation",
                  "fieldPath": "observations[].code",
                  "displayName": "检验编码",
                  "dataType": "code",
                  "unit": null,
                  "codeSystem": "LOINC",
                  "description": "检验标准编码",
                  "derived": false
                },
                {
                  "category": "基本信息",
                  "group": "患者",
                  "resourceType": "Patient",
                  "fieldPath": "patient.age",
                  "displayName": "年龄",
                  "dataType": "number",
                  "unit": "岁",
                  "codeSystem": null,
                  "description": "按出生日期计算的年龄",
                  "derived": true
                }
              ]
            }
            """;
        when(assets.resolve(
            "tenant-A",
            "runtime-7",
            VersionedAssetType.FIELD_CATALOG,
            ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY))
            .thenReturn(Optional.of(new ResolvedDeclarativeAsset(
                VersionedAssetType.FIELD_CATALOG,
                ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY,
                "V7",
                "runtime-7",
                content,
                "hash-field-catalog")));

        var fields = resolver.resolve("tenant-A", "runtime-7");

        assertThat(fields).extracting(ContextFieldDescriptor::fieldPath)
            .containsExactly("observations[].code", "patient.age");
        assertThat(fields.get(0).codeSystem()).isEqualTo("LOINC");
        assertThat(fields.get(0).payloadKey()).isEqualTo("observations");
        assertThat(fields.get(0).propertyName()).isEqualTo("code");
        assertThat(fields.get(0).externalWritable()).isTrue();
        assertThat(fields.get(1).derived()).isTrue();
        assertThat(fields.get(1).externalWritable()).isFalse();
        verify(assets).resolve(
            "tenant-A",
            "runtime-7",
            VersionedAssetType.FIELD_CATALOG,
            ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY);
    }

    @Test
    void normalizesBlankDescriptionFromExistingRuntimeReleaseContent() {
        String content = """
            {
              "schemaVersion": "1.0",
              "fields": [
                {
                  "category": "检验检查",
                  "group": "检验",
                  "resourceType": "Observation",
                  "fieldPath": "observations[].valueNumeric",
                  "displayName": "检验数值",
                  "dataType": "number",
                  "unit": null,
                  "codeSystem": null,
                  "description": "",
                  "derived": false
                }
              ]
            }
            """;
        when(assets.resolve(
            "tenant-A",
            "runtime-blank-description",
            VersionedAssetType.FIELD_CATALOG,
            ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY))
            .thenReturn(Optional.of(new ResolvedDeclarativeAsset(
                VersionedAssetType.FIELD_CATALOG,
                ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY,
                "V8",
                "runtime-blank-description",
                content,
                "hash-field-catalog")));

        var fields = resolver.resolve("tenant-A", "runtime-blank-description");

        assertThat(fields).singleElement()
            .satisfies(field -> assertThat(field.description()).isEqualTo("检验数值字段说明"));
    }

    @Test
    void rejectsRuntimeReleaseWithoutFieldCatalogAsset() {
        when(assets.resolve(
            "tenant-A",
            "runtime-missing",
            VersionedAssetType.FIELD_CATALOG,
            ContextFieldCatalogAssets.CLINICAL_CONTEXT_IDENTITY))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("tenant-A", "runtime-missing"))
            .hasMessageContaining("机构生效版本缺少字段目录资产");
    }
}
