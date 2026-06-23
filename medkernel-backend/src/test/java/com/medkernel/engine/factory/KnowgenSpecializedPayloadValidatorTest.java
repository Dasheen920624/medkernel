package com.medkernel.engine.factory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;

/**
 * T7.2 KNOWGEN 专用 payload 结构校验测试。
 */
class KnowgenSpecializedPayloadValidatorTest {

    private final KnowgenSpecializedPayloadValidator validator =
        new KnowgenSpecializedPayloadValidator(new KnowgenSpecializedAssetSkeletonRegistry(), new ObjectMapper());

    @Test
    void acceptsCalculatorPayloadWithRequiredSectionsAndNoSeededClinicalContentFlag() {
        String payload = """
            {
              "sections": {
                "inputs": "待编著",
                "algorithm": "待编著",
                "thresholds": "待编著",
                "test_vectors": "待编著",
                "source": "source:fixture"
              }
            }
            """;

        assertThatCode(() -> validator.validate("KNOWGEN-16", payload)).doesNotThrowAnyException();
    }

    @Test
    void acceptsStructuredCalculatorSectionsForRealFormulaSkeletons() {
        String payload = """
            {
              "sections": {
                "inputs": [{"key": "component_a", "required": true}],
                "algorithm": {"terms": [{"inputKey": "component_a", "coefficient": 1}]},
                "thresholds": [{"level": "fixture", "min": 0}],
                "test_vectors": [{"input": {"component_a": 2}, "expected": 2}],
                "source": "source:fixture"
              }
            }
            """;

        assertThatCode(() -> validator.validate("KNOWGEN-16", payload)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingRuleTestCasesForRuleBasedKnowgenCards() {
        String payload = """
            {
              "sections": {
                "trigger": "待编著",
                "logic": "待编著",
                "action": "待编著",
                "source": "source:fixture"
              }
            }
            """;

        assertThatThrownBy(() -> validator.validate("KNOWGEN-04", payload))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("test_cases");
    }

    @Test
    void rejectsClinicalContentSeededFlagEvenWhenRequiredFieldsExist() {
        String payload = """
            {
              "clinicalContentSeeded": true,
              "sections": {
                "inputs": "待编著",
                "algorithm": "待编著",
                "thresholds": "待编著",
                "test_vectors": "待编著",
                "source": "source:fixture"
              }
            }
            """;

        assertThatThrownBy(() -> validator.validate("KNOWGEN-16", payload))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("clinicalContentSeeded");
    }

    @Test
    void validatesFoundationFieldCatalogAndCompositeStructures() {
        String fieldCatalog = """
            {
              "sections": {
                "data_element_id": "待导入",
                "name_zh": "待导入",
                "definition": "待导入",
                "data_type": "待导入",
                "cardinality": "待导入",
                "privacy_level": "待导入",
                "source_manifest": "待导入"
              }
            }
            """;
        String composite = """
            {
              "sections": {
                "asset_type": "SAFETY",
                "canonical_id": "待编著",
                "structure": "待编著",
                "dependencies": "待编著",
                "test_cases": "待编著",
                "source": "待编著"
              }
            }
            """;

        assertThatCode(() -> validator.validate("KNOWGEN-26", fieldCatalog))
            .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("KNOWGEN-30", composite))
            .doesNotThrowAnyException();
    }
}
