package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TerminologyMappingConfigTest {

    @Test
    void noopMappingKeepsDuplicateAnchorsFromBreakingSnapshotCreation() {
        TerminologyMappingPort mapping = new TerminologyMappingConfig().noopTerminologyMappingPort();
        var anchor = new ClinicalCodeMappingAnchor(
            CanonicalResourceType.PATIENT,
            "MPI-1",
            "allergies",
            "ALG-PEN",
            null,
            "青霉素过敏",
            "TERM.OTHER",
            "HIS",
            "pat-rec-1",
            "v1");

        Map<String, String> result = mapping.evaluate("tenant-A", List.of(anchor, anchor));

        assertThat(result).containsEntry("PATIENT:MPI-1:allergies:ALG-PEN", "UNKNOWN");
        assertThat(result).hasSize(1);
    }
}
