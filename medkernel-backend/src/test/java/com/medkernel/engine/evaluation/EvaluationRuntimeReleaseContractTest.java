package com.medkernel.engine.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class EvaluationRuntimeReleaseContractTest {

    @Test
    void indicatorAuthoringDoesNotCarryPackageSelectors() {
        assertThat(componentNames(EvaluationIndicatorCreateRequest.class))
            .doesNotContain("packageVersion", "packageId", "packageCode");
        assertThat(componentNames(EvaluationIndicator.class))
            .doesNotContain("packageVersion", "packageId", "packageCode");
    }

    @Test
    void evaluationRunRecordsLockedRuntimeRelease() {
        assertThat(componentNames(EvaluationRunRequest.class))
            .contains("runtimeReleaseId")
            .doesNotContain("packageVersion", "packageId", "packageCode");
        assertThat(componentNames(EvaluationRun.class))
            .contains("runtimeReleaseId")
            .doesNotContain("packageVersion", "packageId", "packageCode");
    }

    private static String[] componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
            .map(component -> component.getName())
            .toArray(String[]::new);
    }
}
