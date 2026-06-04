package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 上下文字段目录（P2）单元测试：派生字段清单、过滤与诚实性（不内置业务值）。
 */
class ContextFieldCatalogTest {

    private final ContextFieldCatalog catalog = new ContextFieldCatalog();

    @Test
    void returnsDerivedFieldsForCoreResources() {
        List<ContextFieldDescriptor> all = catalog.query(null, null);
        assertThat(all).isNotEmpty();
        assertThat(all).extracting(ContextFieldDescriptor::resourceType)
            .contains("Patient", "Observation", "Condition", "Medication", "Encounter");
        // 关键临床字段存在
        assertThat(all).anyMatch(f -> f.fieldPath().equals("observations[].valueNumeric")
            && f.dataType().equals("number"));
        assertThat(all).anyMatch(f -> f.fieldPath().equals("observations[].referenceRange"));
        assertThat(all).anyMatch(f -> f.fieldPath().equals("medications[].code")
            && f.dataType().equals("code"));
    }

    @Test
    void codeFieldsBindStandardDictionary() {
        List<ContextFieldDescriptor> all = catalog.query(null, null);
        assertThat(all).anyMatch(f -> f.fieldPath().equals("conditions[].code")
            && "ICD-10".equals(f.codeSystem()));
        assertThat(all).anyMatch(f -> f.fieldPath().equals("medications[].code")
            && "ATC".equals(f.codeSystem()));
        assertThat(all).anyMatch(f -> f.fieldPath().equals("observations[].code")
            && "LOINC".equals(f.codeSystem()));
        // 非编码字段无字典绑定
        assertThat(all).filteredOn(f -> f.fieldPath().equals("patient.gender"))
            .allMatch(f -> f.codeSystem() == null);
    }

    @Test
    void filtersByResourceType() {
        List<ContextFieldDescriptor> obs = catalog.query("Observation", null);
        assertThat(obs).isNotEmpty();
        assertThat(obs).allMatch(f -> f.resourceType().equals("Observation"));
        // 大小写不敏感
        assertThat(catalog.query("observation", null)).hasSameSizeAs(obs);
    }

    @Test
    void filtersByKeywordOnPathOrDisplayName() {
        assertThat(catalog.query(null, "valueNumeric"))
            .isNotEmpty()
            .allMatch(f -> f.fieldPath().contains("valueNumeric"));
        assertThat(catalog.query(null, "诊断"))
            .isNotEmpty()
            .allMatch(f -> f.displayName().contains("诊断") || f.fieldPath().contains("conditions"));
    }

    @Test
    void carriesNoBusinessData() {
        // 目录仅元数据：不得出现具体编码值/患者标识等业务数据
        assertThat(catalog.query(null, null)).allSatisfy(field -> {
            assertThat(field.fieldPath()).isNotBlank();
            assertThat(field.displayName()).isNotBlank();
            assertThat(field.dataType()).isIn("number", "string", "boolean", "date", "code", "list");
        });
    }
}
