package com.medkernel.engine.sandbox.replay;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxReplayHashingTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SandboxReplayHashing hashing = new SandboxReplayHashing(json);

    @Test
    void hashesCanonicalContentAndWholeManifestWithoutDependingOnObjectFieldOrder() throws Exception {
        var left = json.readTree("{\"b\":2,\"a\":1}");
        var right = json.readTree("{\"a\":1,\"b\":2}");
        assertThat(hashing.contentHash(left)).isEqualTo(hashing.contentHash(right));

        SandboxReplayImportRequest request = request("0".repeat(64));
        String manifestHash = hashing.manifestHash(request);
        assertThat(manifestHash).matches("[0-9a-f]{64}");
        assertThat(hashing.manifestHash(request.withManifestHash(manifestHash)))
            .isEqualTo(manifestHash);
    }

    private SandboxReplayImportRequest request(String manifestHash) throws Exception {
        var context = json.readTree("""
            {"resources":{"patient":{"mpi":"DEID-P-1","name":"DEID-PATIENT"},"observations":[]}}
            """);
        var content = json.readTree("""
            {"ruleCode":"RULE.OLD","dsl":{"trigger":"patient-view","when":{"all":[]},"then":[]}}
            """);
        return new SandboxReplayImportRequest(
            "replay-1", "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
            "sha256:" + "3".repeat(64), "sha256:" + "4".repeat(64), context,
            hashing.contentHash(context), "sha256:" + "6".repeat(64), 4L,
            Instant.parse("2025-01-01T00:00:00Z"),
            manifestHash, "MEDKERNEL_D4_STRICT_V1", List.of(new SandboxReplayAssetImportRequest(
                VersionedAssetType.RULE, "RULE.OLD", "rv-old-1", "1", SourceTier.ORG,
                "sha256:" + "5".repeat(64), content, hashing.contentHash(content),
                AssetVersionStatus.WITHDRAWN)));
    }
}
