package com.medkernel.shared.hash;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;

class Sha256ContentHashBytesTest {

    @Test
    void sha256BytesMatchesStringHashForUtf8() {
        byte[] bytes = "临床指南正文".getBytes(UTF_8);
        assertThat(Sha256ContentHash.sha256Bytes(bytes, "空"))
            .isEqualTo(Sha256ContentHash.sha256("临床指南正文", "空"))
            .hasSize(64);
    }

    @Test
    void sha256BytesRejectsEmpty() {
        assertThatThrownBy(() -> Sha256ContentHash.sha256Bytes(new byte[0], "原文字节不能为空"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("原文字节不能为空");
    }
}
