package com.medkernel.engine.context;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * CDS Hooks 风格临床触发点，作为 D3 OPT-02 事件、推荐与嵌入共享的 6 类客户面入口。
 */
public enum ClinicalEventTriggerPoint {
    PATIENT_VIEW("patient-view", List.of("patientId", "packageVersion")),
    ORDER_SIGN("order-sign", List.of("patientId", "encounterId", "packageVersion", "orders")),
    MEDICATION_PRESCRIBE("medication-prescribe", List.of("patientId", "encounterId", "packageVersion", "medications")),
    RESULT_REVIEW("result-review", List.of("patientId", "encounterId", "packageVersion", "results")),
    DISCHARGE_SIGN("discharge-sign", List.of("patientId", "encounterId", "packageVersion", "dischargeSummary")),
    FOLLOWUP_ALERT("followup-alert", List.of("patientId", "packageVersion", "followupPlanId"));

    private final String wireValue;
    private final List<String> requiredContextFields;

    ClinicalEventTriggerPoint(String wireValue, List<String> requiredContextFields) {
        this.wireValue = wireValue;
        this.requiredContextFields = List.copyOf(requiredContextFields);
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    public List<String> requiredContextFields() {
        return requiredContextFields;
    }

    @JsonCreator
    public static ClinicalEventTriggerPoint fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
            .filter(candidate -> candidate.wireValue.equalsIgnoreCase(value.trim())
                || candidate.name().equalsIgnoreCase(value.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("不支持的临床事件触发点: " + value));
    }
}
