package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 上下文字段目录（P2/P5）单元测试：业务层级派生、字典绑定、过滤与诚实性（不内置业务值）。
 */
class ContextFieldCatalogTest {

    private final ContextFieldCatalog catalog = new ContextFieldCatalog();

    @Test
    void returnsFieldsAcrossAllCanonicalResources() {
        List<ContextFieldDescriptor> all = catalog.query(null, null);
        assertThat(all).isNotEmpty();
        assertThat(all).extracting(ContextFieldDescriptor::resourceType)
            .contains("Patient", "Encounter", "Condition", "Medication", "Procedure",
                "Observation", "DiagnosticReport", "NursingAssessment", "FollowUp", "Claim",
                "CarePlan", "Document");
    }

    @Test
    void everyFieldDeclaresBusinessHierarchy() {
        List<ContextFieldDescriptor> all = catalog.query(null, null);
        assertThat(all).allSatisfy(field -> {
            assertThat(field.category()).isNotBlank();
            assertThat(field.group()).isNotBlank();
            assertThat(field.fieldPath()).isNotBlank();
            assertThat(field.displayName()).isNotBlank();
            assertThat(field.dataType()).isIn("number", "string", "boolean", "date", "code", "list");
        });
        // 业务一级域齐全
        assertThat(all).extracting(ContextFieldDescriptor::category)
            .contains("基本信息", "诊断信息", "医嘱信息", "检验检查");
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
        assertThat(all).anyMatch(f -> f.fieldPath().equals("procedures[].code")
            && "ICD-9-CM-3".equals(f.codeSystem()));
        // 非编码字段无字典绑定
        assertThat(all).filteredOn(f -> f.fieldPath().equals("patient.gender"))
            .allMatch(f -> f.codeSystem() == null);
    }

    @Test
    void filtersByResourceTypeAndKeyword() {
        List<ContextFieldDescriptor> obs = catalog.query("Observation", null);
        assertThat(obs).isNotEmpty().allMatch(f -> f.resourceType().equals("Observation"));
        assertThat(catalog.query("observation", null)).hasSameSizeAs(obs);
        // 关键词可命中业务分组（如「用药」）
        assertThat(catalog.query(null, "用药"))
            .isNotEmpty()
            .allMatch(f -> f.group().contains("用药") || f.fieldPath().contains("medications"));
    }
}
