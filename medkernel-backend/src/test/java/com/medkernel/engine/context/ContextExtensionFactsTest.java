package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ContextExtensionFactsTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void namespacedExtensionsRemainAvailableToRuleAndPathwayFacts() throws Exception {
        JsonNode extensions = json.readTree(
            """
            {
              "local": {
                "dialysis_access_type": "AVF",
                "dialysis_years": 3
              }
            }
            """
        );
        ContextSnapshotResources resources = new ContextSnapshotResources(
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            extensions
        );

        Map<String, Object> facts = ContextFactBridge.facts(resources);
        JsonNode conditionContext = ContextFactBridge.conditionContext(json, resources);

        assertThat(facts).containsKey("extensions");
        assertThat(conditionContext.at("/extensions/local/dialysis_access_type").asText()).isEqualTo("AVF");
        assertThat(conditionContext.at("/extensions/local/dialysis_years").asInt()).isEqualTo(3);
    }
}
