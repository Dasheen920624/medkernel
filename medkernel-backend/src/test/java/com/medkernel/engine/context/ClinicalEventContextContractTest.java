package com.medkernel.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.context.OrgScope;

class ClinicalEventContextContractTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void contextCarriesStableClinicalEventFieldsForAllEngines() {
        var payload = json.createObjectNode()
            .put("diagnosisCode", "I10")
            .put("sourceRecordId", "his-rec-1");
        var orgScope = new OrgScope("tenant-A", "group-A", "hospital-A",
            "campus-A", "site-A", "dept-A", "specialty-A");
        var anchor = new ClinicalCodeMappingAnchor(
            CanonicalResourceType.CONDITION,
            "cond-1",
            "code",
            "I10",
            "ICD-10",
            "原发性高血压",
            "TERM.DIAGNOSIS",
            "HIS",
            "his-rec-1",
            null);

        var context = new ClinicalEventContext(
            "evt-1",
            "tenant-A",
            orgScope,
            ClinicalEventType.DIAGNOSIS,
            "MPI-1",
            "ENC-1",
            "ctx-1",
            "HIS",
            "pkg-2026.06",
            "sha256:payload",
            Instant.parse("2026-06-01T01:00:00Z"),
            "HIS:DIAGNOSIS",
            "trace-1",
            payload,
            List.of(anchor));

        assertThat(context.eventId()).isEqualTo("evt-1");
        assertThat(context.tenantId()).isEqualTo("tenant-A");
        assertThat(context.orgScope().departmentId()).isEqualTo("dept-A");
        assertThat(context.patientId()).isEqualTo("MPI-1");
        assertThat(context.encounterId()).isEqualTo("ENC-1");
        assertThat(context.contextSnapshotId()).isEqualTo("ctx-1");
        assertThat(context.triggerSource()).isEqualTo("HIS:DIAGNOSIS");
        assertThat(context.traceId()).isEqualTo("trace-1");
        assertThat(context.payloadDigest()).isEqualTo("sha256:payload");
        assertThat(context.payload().path("diagnosisCode").asText()).isEqualTo("I10");
        assertThat(context.codeMappingAnchors()).containsExactly(anchor);
        assertThat(anchor.key()).isEqualTo("CONDITION:cond-1:code:I10");
    }
}
