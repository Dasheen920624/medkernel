package com.medkernel.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class RuleConflictDetectorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RuleConflictDetector detector = new RuleConflictDetector();

    @Test
    void detectsOverlappingNumericConditionsWithOppositeDispositions() throws Exception {
        JsonNode candidate = dsl("gte", 5, "BLOCK");
        JsonNode existing = dsl("gte", 4, "REMIND");

        RuleConflict conflict = detector.detect(
            candidate,
            List.of(new RuleConflictTarget("RULE.EXISTING", existing)))
            .orElseThrow();

        assertThat(conflict.ruleCode()).isEqualTo("RULE.EXISTING");
        assertThat(conflict.fact()).isEqualTo("lab.potassium");
        assertThat(conflict.reason()).contains("动作处置冲突");
    }

    @Test
    void ignoresNonOverlappingNumericConditions() throws Exception {
        JsonNode candidate = dsl("gte", 5, "BLOCK");
        JsonNode existing = dsl("lt", 5, "REMIND");

        assertThat(detector.detect(
            candidate,
            List.of(new RuleConflictTarget("RULE.EXISTING", existing))))
            .isEmpty();
    }

    private JsonNode dsl(String operator, int value, String actionCode) throws Exception {
        return json.readTree("""
            {
              "trigger": "order-sign",
              "when": {
                "all": [
                  {"fact": "lab.potassium", "operator": "%s", "value": %d}
                ]
              },
              "then": [
                {"actionCode": "%s", "atSeverity": "HIGH"}
              ],
              "explain": {"title": "测试规则"}
            }
            """.formatted(operator, value, actionCode));
    }
}
