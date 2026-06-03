package com.medkernel.engine.integration.fhir;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * FHIR OperationOutcome JSON 工厂。
 */
@Component
public class FhirOperationOutcomeFactory {

    private final ObjectMapper json;

    public FhirOperationOutcomeFactory(ObjectMapper json) {
        this.json = json;
    }

    public JsonNode fromIssues(List<FhirOperationOutcomeIssue> issues) {
        ObjectNode outcome = json.createObjectNode();
        outcome.put("resourceType", "OperationOutcome");
        ArrayNode issueNodes = outcome.putArray("issue");
        for (FhirOperationOutcomeIssue issue : issues == null ? List.<FhirOperationOutcomeIssue>of() : issues) {
            ObjectNode issueNode = json.createObjectNode();
            issueNode.put("severity", issue.severity());
            issueNode.put("code", issue.code());
            issueNode.put("diagnostics", issue.diagnostics());
            issueNodes.add(issueNode);
        }
        return outcome;
    }
}
