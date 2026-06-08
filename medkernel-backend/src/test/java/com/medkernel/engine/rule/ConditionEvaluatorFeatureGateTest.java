package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.authoring.AuthoringFeatureFlag;
import com.medkernel.engine.authoring.AuthoringFeatureGate;
import com.medkernel.shared.api.error.ApiException;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorFeatureGateTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void basicOperatorsStillEvaluateWhenClinicalOperatorsAreDisabled() throws Exception {
        AuthoringFeatureGate gate = mock(AuthoringFeatureGate.class);
        when(gate.enabled(AuthoringFeatureFlag.CLINICAL_OPERATORS)).thenReturn(false);
        ConditionEvaluator evaluator = new ConditionEvaluator(json, gate);

        ConditionEvaluation evaluation = evaluator.evaluate(json.readTree("""
            {"fact": "patient.age", "operator": "gte", "value": 18}
            """), json.readTree("""
            {"patient": {"age": 42}}
            """));

        assertThat(evaluation.matched()).isTrue();
    }

    @Test
    void clinicalOperatorDisabledFailsHonestlyInsteadOfCalculating() throws Exception {
        AuthoringFeatureGate gate = mock(AuthoringFeatureGate.class);
        when(gate.enabled(AuthoringFeatureFlag.CLINICAL_OPERATORS)).thenReturn(false);
        ConditionEvaluator evaluator = new ConditionEvaluator(json, gate);

        assertThatThrownBy(() -> evaluator.evaluate(json.readTree("""
            {"fact": "observation.value", "operator": "between", "value": [3, 8]}
            """), json.readTree("""
            {"observation": {"value": 5}}
            """)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("临床算子能力开关未启用")
            .hasMessageContaining("authoring-clinical-operators");
    }

    @Test
    void recursiveConditionTreeDisabledRejectsNestedGroupsButKeepsFlatGroupsUsable() throws Exception {
        AuthoringFeatureGate gate = mock(AuthoringFeatureGate.class);
        when(gate.enabled(AuthoringFeatureFlag.RECURSIVE_CONDITION_TREE)).thenReturn(false);
        ConditionEvaluator evaluator = new ConditionEvaluator(json, gate);

        ConditionEvaluation flat = evaluator.evaluate(json.readTree("""
            {"all": [{"fact": "patient.age", "operator": "gte", "value": 18}]}
            """), json.readTree("""
            {"patient": {"age": 42}}
            """));

        assertThat(flat.matched()).isTrue();
        assertThatThrownBy(() -> evaluator.evaluate(json.readTree("""
            {
              "all": [
                {"fact": "patient.age", "operator": "gte", "value": 18},
                {"any": [{"fact": "encounter.type", "operator": "equals", "value": "INPATIENT"}]}
              ]
            }
            """), json.readTree("""
            {"patient": {"age": 42}, "encounter": {"type": "INPATIENT"}}
            """)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("递归条件树能力开关未启用")
            .hasMessageContaining("authoring-recursive-condition-tree");
    }
}
