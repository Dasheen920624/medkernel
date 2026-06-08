package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorFragmentTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void referencedConditionFragmentIsInlinedBeforeEvaluation() throws Exception {
        JsonNode resolvedFragment = json.readTree("""
            {
              "all": [
                {"fact": "patient.age", "operator": "gte", "value": 65},
                {"fact": "conditions[].code", "operator": "contains", "value": "N18.5"}
              ]
            }
            """);
        ConditionFragmentResolver resolver = reference -> resolvedFragment;
        ConditionEvaluator evaluator =
            new ConditionEvaluator(json, AuthoringFeatureGate.alwaysEnabled(), resolver);

        ConditionEvaluation evaluation = evaluator.evaluate(json.readTree("""
            {"fragmentRef": "FRAG_RENAL_IMPAIRED", "version": 1, "packageVersion": "pkg-2026.06"}
            """), json.readTree("""
            {
              "patient": {"age": 72},
              "conditions": [{"code": "N18.5"}]
            }
            """));

        assertThat(evaluation.matched()).isTrue();
        assertThat(evaluation.evidence())
            .extracting(ConditionEvidence::fact)
            .containsExactly("patient.age", "conditions[].code");
    }

    @Test
    void cyclicConditionFragmentReferenceIsRejectedDeterministically() throws Exception {
        ConditionFragmentResolver resolver = reference -> {
            if ("FRAG_A".equals(reference.fragmentCode())) {
                return fragment("FRAG_B");
            }
            return fragment("FRAG_A");
        };
        ConditionEvaluator evaluator =
            new ConditionEvaluator(json, AuthoringFeatureGate.alwaysEnabled(), resolver);

        assertThatThrownBy(() -> evaluator.evaluate(fragment("FRAG_A"), json.createObjectNode()))
            .isInstanceOf(ApiException.class)
            .satisfies(error -> assertThat(((ApiException) error).errorCode()).isEqualTo(ErrorCode.ENG_RULE_001))
            .hasMessageContaining("条件片段循环引用");
    }

    private JsonNode fragment(String fragmentCode) {
        try {
            return json.readTree("""
                {"fragmentRef": "%s", "version": 1, "packageVersion": "pkg-2026.06"}
                """.formatted(fragmentCode));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
