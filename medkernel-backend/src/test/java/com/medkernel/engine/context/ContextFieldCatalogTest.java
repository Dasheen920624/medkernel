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
                "CarePlan", "Document", "AllergyIntolerance");
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
            assertThat(field.payloadKey()).isNotBlank();
            assertThat(field.propertyName()).isNotBlank();
            assertThat(field.jsonSchemaType()).isIn("number", "string", "boolean", "array");
        });
        // 业务一级域齐全
        assertThat(all).extracting(ContextFieldDescriptor::category)
            .contains("基本信息", "诊断信息", "医嘱信息", "检验检查");
        // 平台派生字段标记 PLATFORM 且无 fieldId（不可前台删除）
        assertThat(all).allMatch(f -> "PLATFORM".equals(f.source()) && f.fieldId() == null);
    }

    @Test
    void codeFieldsBindStandardDictionary() {
        List<ContextFieldDescriptor> all = catalog.query(null, null);
        assertThat(all).anyMatch(f -> f.fieldPath().equals("conditions[].code")
            && "ICD-10".equals(f.codeSystem()));
        assertThat(all).anyMatch(f -> f.fieldPath().equals("medications[].code")
            && "ATC".equals(f.codeSystem()));
        assertThat(all).anyMatch(f -> f.fieldPath().equals("allergyIntolerances[].code")
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
    void registersPatientAgeAsDerivedField() {
        List<ContextFieldDescriptor> patientFields = catalog.query("Patient", null);
        ContextFieldDescriptor age = patientFields.stream()
            .filter(f -> "patient.age".equals(f.fieldPath()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("字段目录未登记 patient.age"));
        // age 是求值期由出生日期算得的派生整岁
        assertThat(age.derived()).isTrue();
        assertThat(age.dataType()).isEqualTo("number");
        assertThat(age.source()).isEqualTo("PLATFORM");
        assertThat(age.category()).isEqualTo("基本信息");
        // 对照：原始存储字段（出生日期）非派生
        ContextFieldDescriptor birthDate = patientFields.stream()
            .filter(f -> "patient.birthDate".equals(f.fieldPath()))
            .findFirst()
            .orElseThrow();
        assertThat(birthDate.derived()).isFalse();
    }

    @Test
    void exposesThirdPartyPayloadContractMetadata() {
        ContextFieldDescriptor observationValue = catalog.query("Observation", null).stream()
            .filter(f -> "observations[].valueNumeric".equals(f.fieldPath()))
            .findFirst()
            .orElseThrow();

        assertThat(observationValue.payloadKey()).isEqualTo("observations");
        assertThat(observationValue.propertyName()).isEqualTo("valueNumeric");
        assertThat(observationValue.jsonSchemaType()).isEqualTo("number");
        assertThat(observationValue.externalWritable()).isTrue();

        ContextFieldDescriptor age = catalog.query("Patient", null).stream()
            .filter(f -> "patient.age".equals(f.fieldPath()))
            .findFirst()
            .orElseThrow();
        assertThat(age.payloadKey()).isEqualTo("patient");
        assertThat(age.propertyName()).isEqualTo("age");
        assertThat(age.externalWritable()).isFalse();
    }

    @Test
    void exposesStructuredAllergyIntoleranceFieldsForMedicationAllergyAuthoring() {
        List<ContextFieldDescriptor> fields = catalog.query("AllergyIntolerance", null);

        assertThat(fields).extracting(ContextFieldDescriptor::fieldPath)
            .contains(
                "allergyIntolerances[].code",
                "allergyIntolerances[].substance",
                "allergyIntolerances[].category",
                "allergyIntolerances[].criticality",
                "allergyIntolerances[].reactions",
                "allergyIntolerances[].onsetTime",
                "allergyIntolerances[].qualityStatus");
        assertThat(fields).filteredOn(f -> f.fieldPath().equals("allergyIntolerances[].code"))
            .singleElement()
            .satisfies(field -> {
                assertThat(field.displayName()).contains("过敏物质");
                assertThat(field.dataType()).isEqualTo("code");
                assertThat(field.codeSystem()).isEqualTo("ATC");
            });
        assertThat(catalog.query(null, null))
            .extracting(ContextFieldDescriptor::fieldPath)
            .doesNotContain("patient.allergies");
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
