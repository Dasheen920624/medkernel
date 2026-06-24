package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 规则/路径维护入口使用的字段路径守门测试。
 */
class ContextFieldPathPolicyTest {

    @Test
    void acceptsCanonicalAndHospitalExtensionFields() {
        assertThat(ContextFieldPathPolicy.unknownFields(List.of(
            "patient.age",
            "observations[].valueNumeric",
            "medications[].code",
            "extensions.local.dialysis_access_type"
        ))).isEmpty();
    }

    @Test
    void rejectsRetiredOrNonexistentRuntimeFacts() {
        assertThat(ContextFieldPathPolicy.unknownFields(List.of(
            "patient.eGfr",
            "orders[].drugCode",
            "order.drugClass"
        ))).containsExactly(
            "patient.eGfr",
            "orders[].drugCode",
            "order.drugClass"
        );
    }
}
