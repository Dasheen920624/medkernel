package com.medkernel.engine.terminology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.context.ContextFieldCatalogService;
import com.medkernel.engine.context.ContextFieldDescriptor;
import org.junit.jupiter.api.Test;

class TerminologyCoverageGateTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void checkConditionCoverageFindsUnmappedCodeFieldsInNestedConditions() throws Exception {
        ContextFieldCatalogService fieldCatalogService = mock(ContextFieldCatalogService.class);
        TerminologyService terminologyService = mock(TerminologyService.class);
        when(fieldCatalogService.query(null, null)).thenReturn(List.of(codeField("conditions[].code", "ICD-10")));
        when(terminologyService.evaluateCoverage("ICD-10", List.of("I10", "E11"))).thenReturn(List.of(
            new MappingCoverageItem("I10", MappingCoverageItem.COVERED, 1),
            new MappingCoverageItem("E11", MappingCoverageItem.UNMAPPED, 0)
        ));
        TerminologyCoverageGate gate = new TerminologyCoverageGate(fieldCatalogService, terminologyService);

        List<TerminologyCoverageIssue> issues = gate.checkConditionCoverage(read("""
            {
              "all": [
                {"fact": "conditions[].code", "operator": "equals", "value": "I10"},
                {
                  "any": [
                    {"fact": "context.conditions[].code", "operator": "in", "value": ["E11"]},
                    {"fact": "patient.age", "operator": "gte", "value": 18}
                  ]
                }
              ]
            }
            """));

        assertThat(issues).containsExactly(new TerminologyCoverageIssue(
            "conditions[].code", "ICD-10", "E11", MappingCoverageItem.UNMAPPED, 0));
    }

    private ContextFieldDescriptor codeField(String fieldPath, String codeSystem) {
        return new ContextFieldDescriptor(
            "诊断信息", "诊断", "Condition", fieldPath, "诊断编码",
            "code", null, codeSystem, "诊断标准编码", "SYSTEM", null, false);
    }

    private JsonNode read(String source) throws Exception {
        return json.readTree(source);
    }
}
