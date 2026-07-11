package com.medkernel.engine.context;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.medkernel.engine.rule.RuntimeReleaseRuleSelector;
import com.medkernel.engine.rule.RuntimeRuleSelection;
import com.medkernel.engine.rule.RuleEngineService;
import com.medkernel.engine.rule.RuleEvaluateResponse;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 临床事件到临床规则真实执行入口的适配器。
 */
@Component
public class ClinicalEventRuleEngineAdapter implements ClinicalEventEngineAdapter {

    private final RuleEngineService rules;
    private final ObjectMapper json;
    private final RuntimeReleaseRuleSelector runtimeRules;

    @Autowired
    public ClinicalEventRuleEngineAdapter(
            RuleEngineService rules,
            ObjectMapper json,
            RuntimeReleaseRuleSelector runtimeRules) {
        this.rules = rules;
        this.json = json;
        this.runtimeRules = runtimeRules;
    }

    ClinicalEventRuleEngineAdapter(RuleEngineService rules, ObjectMapper json) {
        this(rules, json, null);
    }

    @Override
    public ClinicalEventEngine engine() {
        return ClinicalEventEngine.RULE;
    }

    @Override
    public ClinicalEventEngineDispatchResult dispatch(ClinicalEventContext context) {
        RuntimeRuleSelection selection = runtimeRules == null
            ? new RuntimeRuleSelection(context.runtimeReleaseId(), null, List.of())
            : runtimeRules.select(
                context.tenantId(), context.runtimeReleaseId(), context.triggerPoint());
        RuleEvaluateResponse response = runtimeRules == null
            ? rules.evaluateContext(
                context.triggerPoint(),
                toRuleContext(context),
                context.eventId(),
                List.of(),
                context.runtimeReleaseId()
            )
            : rules.evaluatePinnedContext(
                context.triggerPoint(),
                toRuleContext(context),
                context.eventId(),
                selection.rules(),
                selection.runtimeReleaseId()
            );
        return ClinicalEventEngineDispatchResult.dispatched(
            engine(), response.requestId(), "临床规则已接收临床事件上下文");
    }

    private ObjectNode toRuleContext(ClinicalEventContext context) {
        ObjectNode root = json.createObjectNode();
        if (context.payload().isObject()) {
            root.setAll((ObjectNode) context.payload().deepCopy());
        }
        ObjectNode event = root.putObject("event");
        event.put("eventId", context.eventId());
        event.put("eventType", context.eventType().name());
        event.put("triggerPoint", context.triggerPoint());
        event.put("sourceSystem", context.sourceSystem());
        event.put("triggerSource", context.triggerSource());
        event.put("occurredAt", context.occurredAt().toString());
        event.put("payloadDigest", context.payloadDigest());
        event.put("runtimeReleaseId", context.runtimeReleaseId());
        ObjectNode patient = root.path("patient").isObject()
            ? (ObjectNode) root.path("patient")
            : root.putObject("patient");
        patient.put("patientId", context.patientId());
        patient.put("encounterId", context.encounterId());
        if (!root.path("encounters").isArray() || root.path("encounters").isEmpty()) {
            ObjectNode encounter = root.putArray("encounters").addObject();
            encounter.put("encounterId", context.encounterId());
            encounter.put("encounterType", context.clinicalSetting().name());
        }
        root.set("orgScope", json.valueToTree(context.orgScope()));
        root.set("payload", context.payload());
        root.set("codeMappingAnchors", json.valueToTree(context.codeMappingAnchors()));
        return root;
    }
}
