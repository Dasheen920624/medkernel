package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
            .isEqualTo(ErrorCode.DSL_OPERATOR_INVALID);
    }

    @Test
    void betweenOperatorHonorsExclusiveUpperBoundaryAndExplainsClinicalValue() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.potassium",
                    "operator": "between",
                    "value": {
                      "min": 3.5,
                      "max": 5.5,
                      "includeMin": true,
                      "includeMax": false,
                      "unit": "mmol/L"
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "血钾区间提醒"}],
              "explain": {"title": "血钾区间", "reason": "校验血钾是否位于目标区间"}
            }
            """), read("""
            {
              "lab": {
                "potassium": {
                  "value": 5.5,
                  "unit": "mmol/L",
                  "source": "LIS:K-001"
                }
              }
            }
            """));

        assertThat(result.hit()).isFalse();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("operator").asText()).isEqualTo("between");
        assertThat(evidence.path("value").asDouble()).isEqualTo(5.5d);
        assertThat(evidence.path("unit").asText()).isEqualTo("mmol/L");
        assertThat(evidence.path("source").asText()).isEqualTo("LIS:K-001");
        assertThat(evidence.path("formula").asText()).isEqualTo("5.5 mmol/L between [3.5, 5.5)");
        assertThat(evidence.path("matched").asBoolean()).isFalse();
    }

    @Test
    void betweenOperatorRejectsUnitMismatchWithoutGuessing() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.potassium",
                    "operator": "between",
                    "value": {"min": 3.5, "max": 5.5, "unit": "mmol/L"}
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "血钾区间提醒"}],
              "explain": {"title": "血钾区间", "reason": "校验血钾是否位于目标区间"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"lab": {"potassium": {"value": 4.0, "unit": "mg/dL", "source": "LIS:K-002"}}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNIT_INCOMPATIBLE);
                assertThat(exception.getMessage()).contains("lab.potassium");
            });
    }

    @Test
    void betweenOperatorRejectsInvertedRange() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.potassium",
                    "operator": "between",
                    "value": {"min": 6.0, "max": 3.5, "unit": "mmol/L"}
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "血钾区间提醒"}],
              "explain": {"title": "血钾区间", "reason": "拒绝反向区间"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"lab": {"potassium": {"value": 4.0, "unit": "mmol/L", "source": "LIS:K-003"}}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.RULE_DSL_INVALID);
                assertThat(exception.getMessage()).contains("min");
            });
    }

    @Test
    void unitCompareConvertsMgDlToMmolLBeforeComparison() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.glucose",
                    "operator": "unit_compare",
                    "value": {
                      "comparison": "gte",
                      "value": 7.0,
                      "unit": "mmol/L",
                      "analyte": "glucose"
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "MEDIUM", "message": "血糖偏高"}],
              "explain": {"title": "血糖换算", "reason": "跨单位比较血糖阈值"}
            }
            """), read("""
            {
              "lab": {
                "glucose": {
                  "value": 130,
                  "unit": "mg/dL",
                  "source": "LIS:GLU-001"
                }
              }
            }
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("value").asDouble()).isCloseTo(7.21d, within(0.01d));
        assertThat(evidence.path("unit").asText()).isEqualTo("mmol/L");
        assertThat(evidence.path("source").asText()).isEqualTo("LIS:GLU-001");
        assertThat(evidence.path("formula").asText())
            .isEqualTo("130 mg/dL / 18.0182 = 7.21 mmol/L; 7.21 gte 7.0 mmol/L");
    }

    @Test
    void unitCompareRejectsUnknownConversionWithoutGuessing() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.sodium",
                    "operator": "unit_compare",
                    "value": {
                      "comparison": "gte",
                      "value": 135,
                      "unit": "mg/dL",
                      "analyte": "sodium"
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "钠离子提醒"}],
              "explain": {"title": "钠离子换算", "reason": "未知换算拒绝"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"lab": {"sodium": {"value": 140, "unit": "mmol/L", "source": "LIS:NA-001"}}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.UNIT_INCOMPATIBLE);
                assertThat(exception.getMessage()).contains("lab.sodium");
            });
    }

    @Test
    void temporalOperatorMatchesTwoConsecutivePotassiumResultsWithinFortyEightHours() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "observations.potassium",
                    "operator": "temporal",
                    "value": {
                      "mode": "consecutive",
                      "window": "PT48H",
                      "referenceTime": "2026-06-03T00:00:00Z",
                      "count": 2,
                      "condition": {"operator": "gt", "value": 6.0, "unit": "mmol/L"}
                    }
                  }
                ]
              },
              "then": [{"actionCode": "STRONG_REMINDER", "severity": "HIGH", "message": "高钾血症风险"}],
              "explain": {"title": "连续高钾", "reason": "48h 内连续两次血钾超过阈值"}
            }
            """), read("""
            {
              "observations": {
                "potassium": [
                  {"value": 6.4, "unit": "mmol/L", "observedAt": "2026-05-31T23:59:59Z", "source": "LIS:K-old"},
                  {"value": 6.1, "unit": "mmol/L", "observedAt": "2026-06-01T00:00:00Z", "source": "LIS:K-101"},
                  {"value": 6.3, "unit": "mmol/L", "observedAt": "2026-06-02T08:00:00Z", "source": "LIS:K-102"}
                ]
              }
            }
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("value").asInt()).isEqualTo(2);
        assertThat(evidence.path("unit").asText()).isEqualTo("次");
        assertThat(evidence.path("source").asText()).isEqualTo("LIS:K-101,LIS:K-102");
        assertThat(evidence.path("formula").asText())
            .isEqualTo("PT48H window ending 2026-06-03T00:00:00Z consecutive 2 where value gt 6.0 mmol/L");
    }

    @Test
    void temporalOperatorEvaluatesUpwardTrendInWindow() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "observations.creatinine",
                    "operator": "temporal",
                    "value": {
                      "mode": "trend",
                      "direction": "up",
                      "window": "PT72H",
                      "referenceTime": "2026-06-03T00:00:00Z",
                      "count": 3,
                      "unit": "mg/dL"
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "MEDIUM", "message": "肌酐上升趋势"}],
              "explain": {"title": "肌酐趋势", "reason": "校验窗口内肌酐趋势"}
            }
            """), read("""
            {
              "observations": {
                "creatinine": [
                  {"value": 0.8, "unit": "mg/dL", "observedAt": "2026-06-01T08:00:00Z", "source": "LIS:CR-1"},
                  {"value": 1.0, "unit": "mg/dL", "observedAt": "2026-06-02T08:00:00Z", "source": "LIS:CR-2"},
                  {"value": 1.2, "unit": "mg/dL", "observedAt": "2026-06-02T20:00:00Z", "source": "LIS:CR-3"}
                ]
              }
            }
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("value").asInt()).isEqualTo(3);
        assertThat(evidence.path("formula").asText())
            .isEqualTo("PT72H window ending 2026-06-03T00:00:00Z trend up across 3 values");
    }

    @Test
    void temporalOperatorRejectsNonNumericCountWithoutDefaulting() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "observations.potassium",
                    "operator": "temporal",
                    "value": {
                      "mode": "consecutive",
                      "window": "PT48H",
                      "referenceTime": "2026-06-03T00:00:00Z",
                      "count": "two",
                      "condition": {"operator": "gt", "value": 6.0, "unit": "mmol/L"}
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "连续高钾"}],
              "explain": {"title": "连续高钾", "reason": "拒绝非数值 count"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {
              "observations": {
                "potassium": [
                  {"value": 6.4, "unit": "mmol/L", "observedAt": "2026-06-02T08:00:00Z", "source": "LIS:K-201"},
                  {"value": 6.3, "unit": "mmol/L", "observedAt": "2026-06-02T20:00:00Z", "source": "LIS:K-202"}
                ]
              }
            }
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.RULE_DSL_INVALID));
    }

    @Test
    void temporalOperatorRejectsNonPositiveWindow() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "observations.potassium",
                    "operator": "temporal",
                    "value": {
                      "mode": "consecutive",
                      "window": "PT0S",
                      "referenceTime": "2026-06-03T00:00:00Z",
                      "count": 1,
                      "condition": {"operator": "gt", "value": 6.0, "unit": "mmol/L"}
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "连续高钾"}],
              "explain": {"title": "连续高钾", "reason": "拒绝非正时间窗"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"observations": {"potassium": []}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.RULE_DSL_INVALID));
    }

    @Test
    void temporalTrendRejectsCountLessThanTwo() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "observations.creatinine",
                    "operator": "temporal",
                    "value": {
                      "mode": "trend",
                      "direction": "up",
                      "window": "PT24H",
                      "referenceTime": "2026-06-03T00:00:00Z",
                      "count": 1,
                      "unit": "mg/dL"
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "趋势提醒"}],
              "explain": {"title": "趋势", "reason": "拒绝单点趋势"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {
              "observations": {
                "creatinine": [
                  {"value": 1.2, "unit": "mg/dL", "observedAt": "2026-06-02T20:00:00Z", "source": "LIS:CR-3"}
                ]
              }
            }
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.RULE_DSL_INVALID));
    }

    @Test
    void temporalOperatorRejectsUnsupportedTrendDirectionEvenWithSingleValue() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "observations.creatinine",
                    "operator": "temporal",
                    "value": {
                      "mode": "trend",
                      "direction": "sideways",
                      "window": "PT24H",
                      "referenceTime": "2026-06-03T00:00:00Z",
                      "count": 1,
                      "unit": "mg/dL"
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "趋势提醒"}],
              "explain": {"title": "趋势", "reason": "非法趋势方向"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {
              "observations": {
                "creatinine": [
                  {"value": 1.2, "unit": "mg/dL", "observedAt": "2026-06-02T20:00:00Z", "source": "LIS:CR-3"}
                ]
              }
            }
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.DSL_OPERATOR_INVALID));
    }

    @Test
    void derivedFormulaRejectsNonPositiveClinicalParametersWithoutInternalError() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {
                    "fact": "derived.egfr",
                    "operator": "derived",
                    "value": {
                      "formula": "CKD_EPI_2021_EGFR",
                      "comparison": "gte",
                      "value": 60,
                      "unit": "mL/min/1.73m2",
                      "parameters": {
                        "creatinine": "labs.creatinine",
                        "age": "patient.age",
                        "sex": "patient.sex"
                      }
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "肾功能提醒"}],
              "explain": {"title": "eGFR", "reason": "拒绝不合法入参"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {
              "patient": {"age": 60, "sex": "FEMALE"},
              "labs": {"creatinine": {"value": 0, "unit": "mg/dL", "source": "LIS:CR-zero"}}
            }
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_DATA);
                assertThat(exception.getMessage()).contains("creatinine");
            });
    }

    @Test
    void derivedEgfrRejectsMissingCreatinineWithoutDefaultValue() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {
                    "fact": "derived.egfr",
                    "operator": "derived",
                    "value": {
                      "formula": "CKD_EPI_2021_EGFR",
                      "comparison": "gte",
                      "value": 60,
                      "unit": "mL/min/1.73m2",
                      "parameters": {
                        "creatinine": "labs.creatinine",
                        "age": "patient.age",
                        "sex": "patient.sex"
                      }
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "肾功能提醒"}],
              "explain": {"title": "eGFR", "reason": "校验肾功能"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"patient": {"age": 60, "sex": "FEMALE"}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_DATA);
                assertThat(exception.getMessage()).contains("creatinine");
            });
    }

    @Test
    void derivedEgfrCalculatesWhitelistedFormulaAndExplainsResult() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {
                    "fact": "derived.egfr",
                    "operator": "derived",
                    "value": {
                      "formula": "CKD_EPI_2021_EGFR",
                      "comparison": "gte",
                      "value": 80,
                      "unit": "mL/min/1.73m2",
                      "parameters": {
                        "creatinine": "labs.creatinine",
                        "age": "patient.age",
                        "sex": "patient.sex"
                      }
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "肾功能提醒"}],
              "explain": {"title": "eGFR", "reason": "校验肾功能"}
            }
            """), read("""
            {
              "patient": {"age": 60, "sex": "FEMALE"},
              "labs": {"creatinine": {"value": 0.8, "unit": "mg/dL", "source": "LIS:CR-20260603"}}
            }
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("value").asDouble()).isCloseTo(84.35d, within(0.05d));
        assertThat(evidence.path("unit").asText()).isEqualTo("mL/min/1.73m2");
        assertThat(evidence.path("source").asText()).isEqualTo("LIS:CR-20260603");
        assertThat(evidence.path("formula").asText())
            .contains("CKD_EPI_2021_EGFR")
            .contains("Scr=0.8 mg/dL");
    }

    @Test
    void derivedCrclAndBsaUseWhitelistedDeterministicFormulas() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {
                    "fact": "derived.crcl",
                    "operator": "derived",
                    "value": {
                      "formula": "COCKCROFT_GAULT_CRCL",
                      "comparison": "gte",
                      "value": 50,
                      "unit": "mL/min",
                      "parameters": {
                        "creatinine": "labs.creatinine",
                        "age": "patient.age",
                        "sex": "patient.sex",
                        "weightKg": "patient.weightKg"
                      }
                    }
                  },
                  {
                    "fact": "derived.bsa",
                    "operator": "derived",
                    "value": {
                      "formula": "MOSTELLER_BSA",
                      "comparison": "between",
                      "min": 1.6,
                      "max": 2.1,
                      "unit": "m2",
                      "parameters": {
                        "heightCm": "patient.heightCm",
                        "weightKg": "patient.weightKg"
                      }
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "体表面积与肌酐清除率提醒"}],
              "explain": {"title": "受控公式", "reason": "校验白名单公式"}
            }
            """), read("""
            {
              "patient": {"age": 60, "sex": "MALE", "weightKg": 70, "heightCm": 170},
              "labs": {"creatinine": {"value": 1.0, "unit": "mg/dL", "source": "LIS:CR-20260603"}}
            }
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence");
        assertThat(evidence.get(0).path("value").asDouble()).isCloseTo(77.78d, within(0.05d));
        assertThat(evidence.get(0).path("formula").asText()).contains("COCKCROFT_GAULT_CRCL");
        assertThat(evidence.get(1).path("value").asDouble()).isCloseTo(1.82d, within(0.01d));
        assertThat(evidence.get(1).path("formula").asText()).contains("MOSTELLER_BSA");
    }

    @Test
    void derivesPatientAgeFromBirthDateAtEventTime() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "老年用药提醒"}],
              "explain": {"title": "老年", "reason": "年龄阈值"}
            }
            """), read("""
            {"patient": {"birthDate": "1960-01-01", "eventTime": "2026-06-05T00:00:00Z"}}
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("fact").asText()).isEqualTo("patient.age");
        assertThat(evidence.path("actual").asInt()).isEqualTo(66);
        assertThat(evidence.path("matched").asBoolean()).isTrue();
    }

    @Test
    void derivedAgeMissingWhenBirthDateAbsentDoesNotMatchOrThrow() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "老年用药提醒"}],
              "explain": {"title": "老年", "reason": "年龄阈值"}
            }
            """), read("""
            {"patient": {"eventTime": "2026-06-05T00:00:00Z", "gender": "F"}}
            """));

        assertThat(result.hit()).isFalse();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("missing").asBoolean()).isTrue();
    }

    @Test
    void explicitPatientAgeIsNotOverriddenByDerivation() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "patient.age", "operator": "gte", "value": 65}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "老年用药提醒"}],
              "explain": {"title": "老年", "reason": "年龄阈值"}
            }
            """), read("""
            {"patient": {"age": 40, "birthDate": "1960-01-01", "eventTime": "2026-06-05T00:00:00Z"}}
            """));

        // 显式 age=40 优先，不被 birthDate 派生覆盖 → 不命中 gte 65
        assertThat(result.hit()).isFalse();
        assertThat(result.explanation().path("conditionEvidence").get(0).path("actual").asInt()).isEqualTo(40);
    }

    @Test
    void derivedAgeFeedsEgfrFormulaIdenticallyToLiteralAge() throws Exception {
        // birthDate 1966-06-05 至 eventTime 2026-06-05 恰 60 岁，eGFR 应与字面 age=60 用例一致(≈84.35)
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {
                "all": [
                  {
                    "fact": "derived.egfr",
                    "operator": "derived",
                    "value": {
                      "formula": "CKD_EPI_2021_EGFR",
                      "comparison": "gte",
                      "value": 80,
                      "unit": "mL/min/1.73m2",
                      "parameters": {
                        "creatinine": "labs.creatinine",
                        "age": "patient.age",
                        "sex": "patient.sex"
                      }
                    }
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "肾功能提醒"}],
              "explain": {"title": "eGFR", "reason": "派生年龄喂公式"}
            }
            """), read("""
            {
              "patient": {"birthDate": "1966-06-05", "eventTime": "2026-06-05T00:00:00Z", "sex": "FEMALE"},
              "labs": {"creatinine": {"value": 0.8, "unit": "mg/dL", "source": "LIS:CR-20260603"}}
            }
            """));

        assertThat(result.hit()).isTrue();
        assertThat(result.explanation().path("conditionEvidence").get(0).path("value").asDouble())
            .isCloseTo(84.35d, within(0.05d));
    }

    @Test
    void isMissingMatchesWhenFactAbsent() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "lab.potassium", "operator": "is_missing"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "缺少血钾结果，建议补检"}],
              "explain": {"title": "缺值", "reason": "血钾结果缺失"}
            }
            """), read("""
            {"lab": {"sodium": {"value": 140, "unit": "mmol/L"}}}
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("operator").asText()).isEqualTo("is_missing");
        assertThat(evidence.path("matched").asBoolean()).isTrue();
    }

    @Test
    void isMissingMatchesWhenObjectHasNoClinicalValue() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "lab.potassium", "operator": "is_missing"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "缺少血钾结果，建议补检"}],
              "explain": {"title": "缺值", "reason": "血钾结果缺失"}
            }
            """), read("""
            {"lab": {"potassium": {"unit": "mmol/L", "source": "LIS:K-9"}}}
            """));

        assertThat(result.hit()).isTrue();
    }

    @Test
    void isMissingDoesNotMatchWhenValuePresent() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "ORDER_SIGN",
              "when": {"all": [{"fact": "lab.potassium", "operator": "is_missing"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "缺少血钾结果，建议补检"}],
              "explain": {"title": "缺值", "reason": "血钾结果缺失"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 4.0, "unit": "mmol/L", "source": "LIS:K-10"}}}
            """));

        assertThat(result.hit()).isFalse();
    }

    @Test
    void notBetweenMatchesValueOutsideRange() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.potassium",
                    "operator": "not_between",
                    "value": {"min": 3.5, "max": 5.5, "unit": "mmol/L"}
                  }
                ]
              },
              "then": [{"actionCode": "STRONG_REMINDER", "severity": "HIGH", "message": "血钾偏离目标区间"}],
              "explain": {"title": "区间取反", "reason": "血钾不在目标区间内"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 6.0, "unit": "mmol/L", "source": "LIS:K-7"}}}
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("operator").asText()).isEqualTo("not_between");
        assertThat(evidence.path("matched").asBoolean()).isTrue();
        assertThat(evidence.path("formula").asText()).isEqualTo("6 mmol/L not between [3.5, 5.5]");
    }

    @Test
    void notBetweenMissesValueInsideRange() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.potassium",
                    "operator": "not_between",
                    "value": {"min": 3.5, "max": 5.5, "unit": "mmol/L"}
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "提醒"}],
              "explain": {"title": "区间取反", "reason": "血钾在目标区间内"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 4.0, "unit": "mmol/L", "source": "LIS:K-8"}}}
            """));

        assertThat(result.hit()).isFalse();
    }

    @Test
    void notBetweenDoesNotMatchWhenValueMissing() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {
                "all": [
                  {
                    "fact": "lab.potassium",
                    "operator": "not_between",
                    "value": {"min": 3.5, "max": 5.5, "unit": "mmol/L"}
                  }
                ]
              },
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "提醒"}],
              "explain": {"title": "区间取反", "reason": "缺值不臆测为越界"}
            }
            """), read("""
            {"lab": {"sodium": {"value": 140, "unit": "mmol/L"}}}
            """));

        // 缺值不得被取反成「越界=命中」：保持未命中 + missing
        assertThat(result.hit()).isFalse();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("matched").asBoolean()).isFalse();
        assertThat(evidence.path("missing").asBoolean()).isTrue();
    }

    @Test
    void withinRefMatchesValueInsideObservationReferenceRange() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "within_ref"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "血钾在参考范围内"}],
              "explain": {"title": "参考范围", "reason": "校验血钾是否在参考范围内"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 4.2, "unit": "mmol/L", "referenceRange": "3.5-5.0", "source": "LIS:K-1"}}}
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("operator").asText()).isEqualTo("within_ref");
        assertThat(evidence.path("value").asDouble()).isEqualTo(4.2d);
        assertThat(evidence.path("unit").asText()).isEqualTo("mmol/L");
        assertThat(evidence.path("source").asText()).isEqualTo("LIS:K-1");
        assertThat(evidence.path("formula").asText()).isEqualTo("4.2 mmol/L within ref [3.5, 5]");
    }

    @Test
    void aboveRefMatchesValueAboveReferenceHigh() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "above_ref"}]},
              "then": [{"actionCode": "STRONG_REMINDER", "severity": "HIGH", "message": "血钾高于参考上限"}],
              "explain": {"title": "参考范围", "reason": "校验血钾是否高于参考上限"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 5.8, "unit": "mmol/L", "referenceRange": "3.5-5.0", "source": "LIS:K-2"}}}
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("operator").asText()).isEqualTo("above_ref");
        assertThat(evidence.path("formula").asText()).isEqualTo("5.8 mmol/L above ref [3.5, 5]");
    }

    @Test
    void belowRefMatchesValueBelowReferenceLowWithEnDashRange() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "below_ref"}]},
              "then": [{"actionCode": "PROMPT", "severity": "MEDIUM", "message": "血钾低于参考下限"}],
              "explain": {"title": "参考范围", "reason": "校验血钾是否低于参考下限"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 3.0, "unit": "mmol/L", "referenceRange": "3.5–5.0", "source": "LIS:K-3"}}}
            """));

        assertThat(result.hit()).isTrue();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("formula").asText()).isEqualTo("3 mmol/L below ref [3.5, 5]");
    }

    @Test
    void withinRefMissesWhenValueOutsideReferenceRange() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "within_ref"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "血钾在参考范围内"}],
              "explain": {"title": "参考范围", "reason": "校验血钾是否在参考范围内"}
            }
            """), read("""
            {"lab": {"potassium": {"value": 5.8, "unit": "mmol/L", "referenceRange": "3.5-5.0", "source": "LIS:K-4"}}}
            """));

        assertThat(result.hit()).isFalse();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("matched").asBoolean()).isFalse();
        assertThat(evidence.path("missing").asBoolean()).isFalse();
    }

    @Test
    void referenceOperatorRejectsUnparseableRangeWithoutGuessing() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "within_ref"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "参考范围提醒"}],
              "explain": {"title": "参考范围", "reason": "拒绝不可解析的参考范围"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"lab": {"potassium": {"value": 4.2, "unit": "mmol/L", "referenceRange": "见报告", "source": "LIS:K-5"}}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_DATA);
                assertThat(exception.getMessage()).contains("lab.potassium");
            });
    }

    @Test
    void referenceOperatorProducesMissWhenValueMissingWithoutThrow() throws Exception {
        RuleDslEvaluation result = evaluator.evaluate(read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "above_ref"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "参考范围提醒"}],
              "explain": {"title": "参考范围", "reason": "缺值不臆测"}
            }
            """), read("""
            {"lab": {"sodium": {"value": 140, "unit": "mmol/L", "referenceRange": "135-145"}}}
            """));

        assertThat(result.hit()).isFalse();
        JsonNode evidence = result.explanation().path("conditionEvidence").get(0);
        assertThat(evidence.path("missing").asBoolean()).isTrue();
    }

    @Test
    void aboveRefRejectsRangeWithoutUpperBound() throws Exception {
        JsonNode dsl = read("""
            {
              "trigger": "LAB_RESULT",
              "when": {"all": [{"fact": "lab.potassium", "operator": "above_ref"}]},
              "then": [{"actionCode": "PROMPT", "severity": "LOW", "message": "参考范围提醒"}],
              "explain": {"title": "参考范围", "reason": "无上界不能判断 above_ref"}
            }
            """);

        assertThatThrownBy(() -> evaluator.evaluate(dsl, read("""
            {"lab": {"potassium": {"value": 4.2, "unit": "mmol/L", "referenceRange": ">3.5", "source": "LIS:K-6"}}}
            """)))
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_DATA));
    }

    private JsonNode read(String source) throws Exception {
        return json.readTree(source);
    }
}
