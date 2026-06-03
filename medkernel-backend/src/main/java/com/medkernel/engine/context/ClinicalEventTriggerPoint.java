package com.medkernel.engine.context;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * CDS Hooks 风格临床事件触发点，锁定 D3 API-02 的 6 类客户面入口。
 */
public enum ClinicalEventTriggerPoint {
    PATIENT_VIEW("patient-view"),
    ORDER_SIGN("order-sign"),
    MEDICATION_PRESCRIBE("medication-prescribe"),
    RESULT_REVIEW("result-review"),
    DISCHARGE_SIGN("discharge-sign"),
    FOLLOWUP_ALERT("followup-alert");

    private final String wireValue;

    ClinicalEventTriggerPoint(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
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
