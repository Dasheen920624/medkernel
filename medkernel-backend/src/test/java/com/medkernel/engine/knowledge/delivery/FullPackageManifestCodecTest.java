package com.medkernel.engine.knowledge.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.medkernel.engine.knowledge.authority.MedicalPackageType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.crypto.SmCryptoService;
import org.junit.jupiter.api.Test;

/** 确定性 FULL 医疗资源包 manifest 合同测试。 */
class FullPackageManifestCodecTest {

    private static final String DIGEST_A = "sm3:" + "a".repeat(64);
    private static final String DIGEST_B = "sm3:" + "b".repeat(64);

    private final FullPackageManifestCodec codec = new FullPackageManifestCodec(
        new ObjectMapper(), new SmCryptoService());

    @Test
    void sameRegisteredInputProducesIdenticalCanonicalBytesAcrossFileOrder() {
        FullPackageManifest first = manifest(List.of(
            new FullPackageManifest.FileEntry("sources/source-a.json", 23, DIGEST_B),
            new FullPackageManifest.FileEntry("assets/RULE/rule-a.json", 41, DIGEST_A)));
        FullPackageManifest reordered = manifest(List.of(
            new FullPackageManifest.FileEntry("assets/RULE/rule-a.json", 41, DIGEST_A),
            new FullPackageManifest.FileEntry("sources/source-a.json", 23, DIGEST_B)));

        byte[] firstBytes = codec.encode(first);
        byte[] reorderedBytes = codec.encode(reordered);

        assertThat(firstBytes).containsExactly(reorderedBytes);
        assertThat(codec.sm3Digest(firstBytes)).startsWith("sm3:").hasSize(68);
        assertThat(codec.decode(firstBytes).files())
            .extracting(FullPackageManifest.FileEntry::path)
            .containsExactly("assets/RULE/rule-a.json", "sources/source-a.json");
        assertThat(new String(firstBytes, StandardCharsets.UTF_8))
            .doesNotContain("hostname", "192.0.2.134", "exportedAt", "evidenceId")
            .contains("\"packageType\":\"FULL\"")
            .contains("\"parentManifestDigest\":null");
    }

    @Test
    void rejectsUnknownHostFieldsAbsolutePathsDuplicatePathsAndFullParentChain() {
        byte[] canonical = codec.encode(manifest(List.of(
            new FullPackageManifest.FileEntry("assets/RULE/rule-a.json", 41, DIGEST_A))));
        String withHost = new String(canonical, StandardCharsets.UTF_8)
            .replace("\"files\":", "\"hostname\":\"192.0.2.134\",\"files\":");

        assertValidation(() -> codec.decode(withHost.getBytes(StandardCharsets.UTF_8)));
        assertValidation(() -> codec.encode(manifest(List.of(
            new FullPackageManifest.FileEntry("/var/lib/medkernel/rule.json", 41, DIGEST_A)))));
        assertValidation(() -> codec.encode(manifest(List.of(
            new FullPackageManifest.FileEntry("assets/rule.json", 41, DIGEST_A),
            new FullPackageManifest.FileEntry("assets/rule.json", 42, DIGEST_B)))));
        assertValidation(() -> codec.encode(new FullPackageManifest(
            "1.0",
            MedicalPackageType.FULL,
            "delivery-platform-0008",
            "mka-medkernel-cn-01",
            "issuer-platform-134",
            "key-platform-2026-01",
            8,
            "baseline-release-0008",
            DIGEST_B,
            compatibility(),
            List.of(new FullPackageManifest.FileEntry("assets/rule.json", 41, DIGEST_A)))));
        assertValidation(() -> codec.encode(new FullPackageManifest(
            "1.0",
            MedicalPackageType.FULL,
            "delivery-platform-0008",
            "mka-medkernel-cn-01",
            "issuer-platform-134",
            "key-platform-2026-01",
            8,
            "baseline-release-0008",
            null,
            new FullPackageManifest.Compatibility(
                "host-134", "1.0.0", "1.x", "V1", "V1"),
            List.of(new FullPackageManifest.FileEntry("assets/rule.json", 41, DIGEST_A)))));
    }

    @Test
    void changingAnyDeclaredFileByteFactChangesManifestDigest() {
        byte[] original = codec.encode(manifest(List.of(
            new FullPackageManifest.FileEntry("assets/RULE/rule-a.json", 41, DIGEST_A))));
        byte[] changed = codec.encode(manifest(List.of(
            new FullPackageManifest.FileEntry("assets/RULE/rule-a.json", 42, DIGEST_B))));

        assertThat(changed).isNotEqualTo(original);
        assertThat(codec.sm3Digest(changed)).isNotEqualTo(codec.sm3Digest(original));
    }

    @Test
    void rejectsManifestWhoseFileArrayIsNotInCanonicalPathOrder() throws Exception {
        byte[] canonical = codec.encode(manifest(List.of(
            new FullPackageManifest.FileEntry("assets/RULE/rule-a.json", 41, DIGEST_A),
            new FullPackageManifest.FileEntry("sources/source-a.json", 23, DIGEST_B))));
        ObjectMapper mapper = new ObjectMapper();
        var root = mapper.readTree(canonical);
        ArrayNode files = (ArrayNode) root.path("files");
        JsonNodePair pair = new JsonNodePair(files.get(0), files.get(1));
        files.removeAll().add(pair.second()).add(pair.first());

        assertValidation(() -> codec.decode(mapper.writeValueAsBytes(root)));
    }

    private FullPackageManifest manifest(List<FullPackageManifest.FileEntry> files) {
        return new FullPackageManifest(
            "1.0",
            MedicalPackageType.FULL,
            "delivery-platform-0008",
            "mka-medkernel-cn-01",
            "issuer-platform-134",
            "key-platform-2026-01",
            8,
            "baseline-release-0008",
            null,
            compatibility(),
            files);
    }

    private FullPackageManifest.Compatibility compatibility() {
        return new FullPackageManifest.Compatibility(
            "1.0", "1.0.0", "1.x", "V1", "V1");
    }

    private void assertValidation(ThrowingCall call) {
        assertThatThrownBy(call::run)
            .isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }

    private record JsonNodePair(
        com.fasterxml.jackson.databind.JsonNode first,
        com.fasterxml.jackson.databind.JsonNode second
    ) {
    }
}
