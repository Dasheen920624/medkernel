package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * 知识撤回影响处置任务的上线契约。
 */
class AffectedCaseTaskContractTest {

    @Test
    void affectedCaseTasksReferenceAssetsAndSyncTargetsWithoutReleasePackageConcepts() {
        assertThat(names(AffectedCaseTargetType.values()))
            .containsExactly(
                "KNOWLEDGE_VERSION",
                "ASSET_DEPENDENCY",
                "SYNC_TARGET",
                "PATIENT_CASE",
                "PATIENT_PATHWAY")
            .doesNotContain("PACKAGE_DEPENDENCY");
        assertThat(names(AffectedCaseTaskType.values()))
            .containsExactly(
                "PHYSICIAN_REVIEW",
                "ASSET_DEPENDENCY_REVIEW",
                "SYNC_ALERT")
            .doesNotContain("PACKAGE_RESYNC");
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values)
            .map(Enum::name)
            .toArray(String[]::new);
    }
}
