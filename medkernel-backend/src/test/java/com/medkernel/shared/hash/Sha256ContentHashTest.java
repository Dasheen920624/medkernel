package com.medkernel.shared.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class Sha256ContentHashTest {

    @Test
    void resolvesStableSha256FromContentAndRejectsMismatch() {
        String hash = Sha256ContentHash.resolve(
            "clinical rule content",
            null,
            "内容与外部指纹不一致",
            "内容不能为空"
        );

        assertThat(hash).isEqualTo("d9699a5648073b6afb456cb25fd0c00fd9e942f82a626dd41af374ef32e2f36c");

        assertThatThrownBy(() -> Sha256ContentHash.resolve(
            "clinical rule content",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "内容与外部指纹不一致",
            "内容不能为空"
        ))
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void rejectsNonSha256ExternalHash() {
        assertThatThrownBy(() -> Sha256ContentHash.normalizeExternalSha256("version-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("64 位 SHA-256")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
