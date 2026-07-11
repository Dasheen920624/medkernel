package com.medkernel.shared.web;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 候选制品身份解析测试，确保运行时只能认可 JAR 内嵌的完整候选提交。
 */
class BuildIdentityResolverTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void resolvesCandidateCommitFromEmbeddedBackendMetadata() {
        BuildIdentityResolver resolver = resolver("""
            {
              "schemaVersion": "1.0.0",
              "kind": "MEDKERNEL_BUILD_METADATA",
              "artifactId": "BACKEND_JAR",
              "candidateCommit": "%s"
            }
            """.formatted(COMMIT));

        BuildIdentity identity = resolver.resolve();

        assertThat(identity.bound()).isTrue();
        assertThat(identity.candidateCommit()).isEqualTo(COMMIT);
        assertThat(identity.reason()).isEqualTo("BOUND");
    }

    @Test
    void missingOrInvalidMetadataIsHonestlyUnbound() {
        BuildIdentity missing = new BuildIdentityResolver(new ObjectMapper(), () -> null).resolve();
        BuildIdentity wrongArtifact = resolver("""
            {
              "schemaVersion": "1.0.0",
              "kind": "MEDKERNEL_BUILD_METADATA",
              "artifactId": "FRONTEND_DIST",
              "candidateCommit": "%s"
            }
            """.formatted(COMMIT)).resolve();
        BuildIdentity malformed = resolver("not-json").resolve();

        assertThat(missing).isEqualTo(BuildIdentity.unbound("METADATA_MISSING"));
        assertThat(wrongArtifact).isEqualTo(BuildIdentity.unbound("METADATA_INVALID"));
        assertThat(malformed).isEqualTo(BuildIdentity.unbound("METADATA_INVALID"));
    }

    private BuildIdentityResolver resolver(String json) {
        return new BuildIdentityResolver(
            new ObjectMapper(),
            () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))
        );
    }
}
