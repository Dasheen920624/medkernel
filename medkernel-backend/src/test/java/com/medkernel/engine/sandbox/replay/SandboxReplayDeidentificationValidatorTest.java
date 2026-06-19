package com.medkernel.engine.sandbox.replay;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.api.error.ApiException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxReplayDeidentificationValidatorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final SandboxReplayDeidentificationValidator validator =
        new SandboxReplayDeidentificationValidator();

    @Test
    void acceptsStrictlyPseudonymizedCanonicalContext() throws Exception {
        assertThatCode(() -> validator.validate(json.readTree("""
            {"resources":{"patient":{"mpi":"DEID-P-1","name":"DEID-PATIENT"},
             "observations":[{"sourceRecordId":"DEID-OBS-1"}]}}
            """))).doesNotThrowAnyException();
    }

    @Test
    void rejectsPlainPatientNameIdentityPhoneAndSourceRecordId() throws Exception {
        for (String context : new String[] {
            "{\"resources\":{\"patient\":{\"mpi\":\"DEID-P-1\",\"name\":\"张三\"}}}",
            "{\"resources\":{\"patient\":{\"mpi\":\"DEID-P-1\",\"name\":\"DEID-PATIENT\",\"idCard\":\"110101199001011234\"}}}",
            "{\"resources\":{\"patient\":{\"mpi\":\"DEID-P-1\",\"name\":\"DEID-PATIENT\",\"phone\":\"13800000000\"}}}",
            "{\"resources\":{\"patient\":{\"mpi\":\"DEID-P-1\",\"name\":\"DEID-PATIENT\"},\"observations\":[{\"sourceRecordId\":\"LIS-REAL-1\"}]}}"
        }) {
            assertThatThrownBy(() -> validator.validate(json.readTree(context)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("D4");
        }
    }
}
