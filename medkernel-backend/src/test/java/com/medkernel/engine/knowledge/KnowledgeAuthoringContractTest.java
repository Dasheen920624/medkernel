package com.medkernel.engine.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import com.medkernel.engine.knowledge.diagnosis.DiagnosisAssetCreateRequest;
import com.medkernel.engine.knowledge.diagnosis.DiagnosisVersionCreateRequest;
import org.junit.jupiter.api.Test;

class KnowledgeAuthoringContractTest {

    @Test
    void authoringRequestsDoNotCarryReleasePackageSelectors() {
        assertNoPackageSelector(KnowledgeIdentityCreateRequest.class);
        assertNoPackageSelector(KnowledgeSourceCreateRequest.class);
        assertNoPackageSelector(KnowledgeSourceVersionCreateRequest.class);
        assertNoPackageSelector(KnowledgeVersionCreateRequest.class);
        assertNoPackageSelector(KnowledgeActionRequest.class);
        assertNoPackageSelector(KnowledgeCandidateReviewRequest.class);
        assertNoPackageSelector(KnowledgeExportController.SubmitExportRequest.class);
        assertNoPackageSelector(DiagnosisAssetCreateRequest.class);
        assertNoPackageSelector(DiagnosisVersionCreateRequest.class);
        assertNoPackageSelector(KnowledgeReplayResponse.class);
    }

    private void assertNoPackageSelector(Class<?> type) {
        assertThat(Arrays.stream(type.getRecordComponents()).map(component -> component.getName()))
            .doesNotContain("packageVersion", "packageId", "packageCode");
    }
}
