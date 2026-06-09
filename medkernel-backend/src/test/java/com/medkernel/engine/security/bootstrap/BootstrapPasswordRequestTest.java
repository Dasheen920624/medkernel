package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 首次部署管理员请求只接收身份凭据，平台租户归属由服务端强制决定。
 */
class BootstrapPasswordRequestTest {

    @Test
    void usernameIsNormalized() {
        assertThat(new BootstrapPasswordRequest("token", " owner ", "pw").usernameNormalized())
            .isEqualTo("owner");
    }
}
