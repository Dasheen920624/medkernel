package com.medkernel.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ContextFieldDescriptor;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.engine.context.RuntimeReleaseFieldCatalogResolver;
import com.medkernel.engine.integration.service.IntegrationDataContractService;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class IntegrationDataContractServiceTest {

    private final RuntimeReleaseFieldCatalogResolver runtimeCatalog =
        mock(RuntimeReleaseFieldCatalogResolver.class);
    private final CurrentClinicalRuntimeReleaseResolver releases =
        mock(CurrentClinicalRuntimeReleaseResolver.class);
    private final IntegrationDataContractService service =
        new IntegrationDataContractService(runtimeCatalog, releases);

    @Test
    void generatesContractForCurrentHospitalRuntimeWithoutCallerSelectedVersion() throws Exception {
        OrgScope scope = new OrgScope(
            "tenant-A", "group-A", "hospital-A", null, null, null, null, null);
        when(releases.resolve(scope)).thenReturn(new ClinicalRuntimeRelease(
            1L,
            "runtime-H7",
            "tenant-A",
            "hospital-A",
            7L,
            "baseline-A3",
            "manifest-sha256",
            null,
            Instant.parse("2026-06-23T08:00:00Z"),
            "operator-1",
            Instant.parse("2026-06-23T08:00:00Z"),
            "operator-1",
            "trace-1"
        ));
        when(runtimeCatalog.resolve("tenant-A", "runtime-H7")).thenReturn(List.of(
            field("Observation", "observations[].code", "检验编码", "code", null, "LOINC", false),
            field("Observation", "observations[].valueNumeric", "数值结果", "number", "mg/dL", null, false),
            field("Patient", "patient.age", "年龄", "number", "岁", null, true),
            field("AllergyIntolerance", "allergyIntolerances[].code", "过敏物质编码", "code", null, "ATC", false)
        ));

        var contract = RequestContext.callWith(
            new RequestContext.Snapshot("trace-1", scope, "operator-1"),
            service::generate);

        assertThat(contract.contractId()).isEqualTo("context-field-contract:runtime-H7");
        assertThat(contract.runtimeReleaseId()).isEqualTo("runtime-H7");
        assertThat(contract.schemaVersion()).isEqualTo("medkernel.context-field-contract.v1");
        assertThat(String.join("\n", contract.accessGuide()))
            .contains("医院当前运行修订", "runtime-H7", "投影", "规则/路径")
            .doesNotContain("packageVersion", "packageId");
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
        assertThat(contract.resources().get("Patient").jsonSchema().properties().get("age").externalWritable())
            .isFalse();
        assertThat(contract.fields()).filteredOn(field -> field.fieldPath().equals("observations[].valueNumeric"))
            .singleElement()
            .satisfies(field -> assertThat(field.externalWritable()).isTrue());
        assertThat(contract.fields()).filteredOn(field -> field.fieldPath().equals("patient.age"))
            .singleElement()
            .satisfies(field -> assertThat(field.externalWritable()).isFalse());
        verify(releases).resolve(scope);
        verify(runtimeCatalog).resolve("tenant-A", "runtime-H7");
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
