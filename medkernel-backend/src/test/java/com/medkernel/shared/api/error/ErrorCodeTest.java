package com.medkernel.shared.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ErrorCode.ErrorClass;

class ErrorCodeTest {

    @Test
    void allErrorCodesHaveErrorClassAndRetryableFlag() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.errorClass())
                .as("ErrorCode %s 必须声明 errorClass", code.name())
                .isNotNull();
        }
    }

    @Test
    void retryableExternalErrorsAreFlagged() {
        assertThat(ErrorCode.DOWNSTREAM_UNAVAILABLE.retryable()).isTrue();
        assertThat(ErrorCode.TOO_MANY_REQUESTS.retryable()).isTrue();
        assertThat(ErrorCode.MODEL_DEGRADED.retryable()).isTrue();
        assertThat(ErrorCode.MODEL_DEGRADED.errorClass()).isEqualTo(ErrorClass.EXTERNAL);
    }

    @Test
    void nonRetryableInputErrors() {
        assertThat(ErrorCode.BAD_REQUEST.retryable()).isFalse();
        assertThat(ErrorCode.VALIDATION_FAILED.retryable()).isFalse();
        assertThat(ErrorCode.NOT_FOUND.retryable()).isFalse();
    }

    @Test
    void errorClassMatchesNaturalGrouping() {
        assertThat(ErrorCode.BAD_REQUEST.errorClass()).isEqualTo(ErrorClass.INPUT);
        assertThat(ErrorCode.UNAUTHORIZED.errorClass()).isEqualTo(ErrorClass.AUTH);
        assertThat(ErrorCode.FORBIDDEN.errorClass()).isEqualTo(ErrorClass.AUTH);
        assertThat(ErrorCode.NOT_FOUND.errorClass()).isEqualTo(ErrorClass.DATA);
        assertThat(ErrorCode.DOWNSTREAM_UNAVAILABLE.errorClass()).isEqualTo(ErrorClass.EXTERNAL);
        assertThat(ErrorCode.INTERNAL_ERROR.errorClass()).isEqualTo(ErrorClass.INTERNAL);
    }

    @Test
    void obsErrorCodesAreRegistered() {
        assertThat(ErrorCode.fromCode("ENG-OBS-001")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-OBS-002")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(500);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INTERNAL);
            assertThat(code.retryable()).isFalse();
        });
    }

    @Test
    void fromCodeHandlesNullAndCaseAndTrim() {
        assertThat(ErrorCode.fromCode(null)).isEmpty();
        assertThat(ErrorCode.fromCode("")).isEmpty();
        assertThat(ErrorCode.fromCode("eng-obs-001")).hasValue(ErrorCode.ENG_OBS_001);
        assertThat(ErrorCode.fromCode("  ENG-OBS-001  ")).hasValue(ErrorCode.ENG_OBS_001);
        assertThat(ErrorCode.fromCode("UNKNOWN")).isEmpty();
    }

    @Test
    void clinicalEventErrorCodesAreRegistered() {
        assertThat(ErrorCode.fromCode("ENG-EVENT-001")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-EVENT-002")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-EVENT-003")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-EVENT-004")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(503);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.EXTERNAL);
            assertThat(code.retryable()).isTrue();
        });
        assertThat(ErrorCode.fromCode("ENG-EVENT-005")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(500);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INTERNAL);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-EVENT-006")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
    }

    @Test
    void ruleEngineErrorCodesAreRegistered() {
        assertThat(ErrorCode.fromCode("ENG-RULE-001")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-RULE-002")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-RULE-003")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-RULE-004")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-RULE-005")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(500);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INTERNAL);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-RULE-006")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
    }

    @Test
    void pathwayEngineErrorCodesAreRegistered() {
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-001")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-002")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-003")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-004")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-005")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-006")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-PATHWAY-007")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("PATHWAY_CLOCK_MISSING")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
    }

    @Test
    void recommendationErrorCodesAreRegistered() {
        assertThat(ErrorCode.fromCode("ENG-REC-001")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-REC-002")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-REC-003")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-REC-004")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-REC-005")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-REC-006")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
    }

    @Test
    void evaluationQualityErrorCodesAreRegistered() {
        assertThat(ErrorCode.fromCode("ENG-EVAL-001")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
            assertThat(code.retryable()).isFalse();
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-002")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-003")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-004")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-005")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(404);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-006")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(400);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.INPUT);
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-007")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
        });
        assertThat(ErrorCode.fromCode("ENG-EVAL-008")).hasValueSatisfying(code -> {
            assertThat(code.httpStatus()).isEqualTo(409);
            assertThat(code.errorClass()).isEqualTo(ErrorClass.DATA);
            assertThat(code.retryable()).isFalse();
        });
    }
}
