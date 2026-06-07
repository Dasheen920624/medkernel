package com.medkernel.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ContextFieldCatalogService;
import com.medkernel.engine.context.ContextFieldDescriptor;
import com.medkernel.engine.integration.service.IntegrationDataContractService;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class IntegrationDataContractServiceTest {

    private final ContextFieldCatalogService catalog = mock(ContextFieldCatalogService.class);
    private final IntegrationDataContractService service = new IntegrationDataContractService(catalog);

    @Test
    void generatesVersionedJsonSchemaStyleContractFromContextFieldCatalog() {
        when(catalog.query(null, null, "pkg-2026.06")).thenReturn(List.of(
            field("Observation", "observations[].code", "检验编码", "code", null, "LOINC", false),
            field("Observation", "observations[].valueNumeric", "数值结果", "number", "mg/dL", null, false),
            field("Patient", "patient.age", "年龄", "number", "岁", null, true),
            field("AllergyIntolerance", "allergyIntolerances[].code", "过敏物质编码", "code", null, "ATC", false)
        ));

        var contract = service.generate("pkg-2026.06");

        assertThat(contract.contractId()).isEqualTo("context-field-contract:pkg-2026.06");
        assertThat(contract.packageVersion()).isEqualTo("pkg-2026.06");
        assertThat(contract.schemaVersion()).isEqualTo("medkernel.context-field-contract.v1");
        assertThat(String.join("\n", contract.accessGuide()))
            .contains("packageVersion=pkg-2026.06", "投影", "规则/路径");
        assertThat(contract.fields()).extracting("fieldPath")
            .containsExactly(
                "observations[].code",
                "observations[].valueNumeric",
                "patient.age",
                "allergyIntolerances[].code");
        assertThat(contract.resources()).containsKeys("Observation", "Patient", "AllergyIntolerance");
        assertThat(contract.resources().get("Observation").payloadKey()).isEqualTo("observations");
        assertThat(contract.resources().get("Observation").jsonSchema().properties())
            .containsKey("valueNumeric");
        assertThat(contract.resources().get("Observation").jsonSchema().properties().get("valueNumeric").type())
            .isEqualTo("number");
        assertThat(contract.resources().get("Observation").jsonSchema().properties().get("code").codeSystem())
            .isEqualTo("LOINC");
        assertThat(contract.resources().get("Patient").jsonSchema().properties().get("age").derived())
            .isTrue();
        verify(catalog).query(null, null, "pkg-2026.06");
    }

    @Test
    void rejectsBlankPackageVersionInsteadOfReturningUnversionedContract() {
        assertThatThrownBy(() -> service.generate(" "))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ENG_CONTEXT_002);
    }

    private static ContextFieldDescriptor field(
            String resourceType,
            String fieldPath,
            String displayName,
            String dataType,
            String unit,
            String codeSystem,
            boolean derived) {
        return new ContextFieldDescriptor(
            "检验检查",
            "检验/体征结果",
            resourceType,
            fieldPath,
            displayName,
            dataType,
            unit,
            codeSystem,
            displayName + "说明",
            "SYSTEM",
            null,
            derived);
    }
}
