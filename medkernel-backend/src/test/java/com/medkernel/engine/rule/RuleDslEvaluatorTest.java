package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

class RuleDslEvaluatorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RuleDslEvaluator evaluator = new RuleDslEvaluator(json);

    @Test
    void allConditionsHitAndReturnHighestSeverityAction() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {"fact": "patient.age", "operator": "gte", "value": 18},
                  {"fact": "order.drugClass", "operator": "equals", "value": "ANTICOAGULANT"},
                  {"fact": "patient.diagnoses", "operator": "contains", "value": "AF"}
                ]
              },
              "then": [
                {
                  "actionCode": "STRONG_REMINDER",
                  "severity": "HIGH",
                  "message": "抗凝用药需确认出血风险",
                  "requiresPhysicianConfirmation": true
                }
              ],
              "explain": {
                "title": "抗凝风险提示",
                "reason": "患者年龄、诊断和医嘱类别满足规则条件",
                "sourceRef": "院内抗凝用药管理规范 2026"
              }
            }
            """), read("""
            {
              "patient": {"age": 72, "diagnoses": ["AF", "HTN"]},
              "order": {"drugClass": "ANTICOAGULANT"}
            }
            """));

        assertThat(result.hit()).isTrue();
        assertThat(result.severity()).isEqualTo(RuleRiskLevel.HIGH);
        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().getFirst().actionCode()).isEqualTo("STRONG_REMINDER");
        assertThat(result.actions().getFirst().requiresPhysicianConfirmation()).isTrue();
        assertThat(result.explanation().get("title").asText()).isEqualTo("抗凝风险提示");
        JsonNode evidence = result.explanation().path("conditionEvidence");
        assertThat(evidence).hasSize(3);
        assertThat(evidence.get(0).path("fact").asText()).isEqualTo("patient.age");
        assertThat(evidence.get(0).path("sourcePath").asText()).isEqualTo("$.patient.age");
        assertThat(evidence.get(0).path("operator").asText()).isEqualTo("gte");
        assertThat(evidence.get(0).path("expected").asInt()).isEqualTo(18);
        assertThat(evidence.get(0).path("actual").asInt()).isEqualTo(72);
        assertThat(evidence.get(0).path("matched").asBoolean()).isTrue();
        assertThat(evidence.get(0).path("missing").asBoolean()).isFalse();
        assertThat(evidence.get(1).path("fact").asText()).isEqualTo("order.drugClass");
        assertThat(evidence.get(2).path("actual")).hasSize(2);
        assertThat(evidence.get(2).path("actual").get(0).asText()).isEqualTo("AF");
        assertThat(evidence.get(2).path("actual").get(1).asText()).isEqualTo("HTN");
    }

    @Test
    void anyConditionCanMatchAndInOperatorAcceptsAllowedValues() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "any": [
                  {"fact": "lab.panic", "operator": "equals", "value": true},
                  {"fact": "lab.code", "operator": "in", "value": ["K", "TNI"]}
                ]
              },
              "then": [
                {"actionCode": "PROMPT", "severity": "MEDIUM", "message": "检验结果需关注"}
              ],
              "explain": {"title": "检验关注", "reason": "命中检验条件"}
            }
            """), read("""
            {"lab": {"panic": false, "code": "TNI"}}
            """));

        assertThat(result.hit()).isTrue();
        assertThat(result.severity()).isEqualTo(RuleRiskLevel.MEDIUM);
    }

    @Test
    void missingFieldProducesMissWithoutThrowing() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "REPORT_SUBMIT",
              "when": {"all": [{"fact": "report.criticalFlag", "operator": "exists"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "报告提醒"}],
              "explain": {"title": "报告提醒", "reason": "存在危急值标记"}
            }
            """), read("""
            {"report": {"status": "FINAL"}}
            """));

        assertThat(result.hit()).isFalse();
        assertThat(result.actions()).isEmpty();
        assertThat(result.severity()).isNull();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("fact").asText()).isEqualTo("report.criticalFlag");
        assertThat(evidence.path("sourcePath").asText()).isEqualTo("$.report.criticalFlag");
        assertThat(evidence.path("operator").asText()).isEqualTo("exists");
        assertThat(evidence.path("actual").isNull()).isTrue();
        assertThat(evidence.path("matched").asBoolean()).isFalse();
        assertThat(evidence.path("missing").asBoolean()).isTrue();
    }

    @Test
    void missingNumericFieldDoesNotMatchLessThanComparison() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "patient.age", "operator": "lt", "value": 18}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "年龄提醒"}],
              "explain": {"title": "年龄提醒", "reason": "年龄低于阈值"}
            }
            """), read("""
            {"patient": {"gender": "F"}}
            """));

        assertThat(result.hit()).isFalse();
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void unsupportedOperatorIsRuleDslValidationError() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "order.name", "operator": "regex", "value": ".*"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "提醒"}],
              "explain": {"title": "提醒", "reason": "测试"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("{}")))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.RULE_DSL_INVALID);
    }

    private JsonNode read(String source) throws Exception {
        return json.readTree(source);
    }
}
