package com.medkernel.engine.sandbox;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.embed.EmbedIntegrationMode;
import com.medkernel.shared.api.error.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SandboxRunRequestTest {

    @Test
    void currentRejectsReplayCaseWhileHistoricalRequiresManifestContext() {
        assertThatThrownBy(() -> new SandboxRunRequest(
            "SNAPSHOT", null, null, null, EmbedIntegrationMode.IFRAME,
            SandboxRunMode.CURRENT, "replay-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("CURRENT");

        assertThatThrownBy(() -> new SandboxRunRequest(
            "SNAPSHOT", null, null, null, EmbedIntegrationMode.IFRAME,
            SandboxRunMode.HISTORICAL_EXACT, null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("replayCaseId");

        assertThatThrownBy(() -> new SandboxRunRequest(
            "SNAPSHOT", new ObjectMapper().createObjectNode(), null, null,
            EmbedIntegrationMode.IFRAME, SandboxRunMode.HISTORICAL_EXACT, "replay-1"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("清单内脱敏上下文");

        SandboxRunRequest valid = new SandboxRunRequest(
            "SNAPSHOT", null, null, null, EmbedIntegrationMode.IFRAME,
            SandboxRunMode.HISTORICAL_EXACT, " replay-1 ");
        assertThat(valid.replayCaseId()).isEqualTo("replay-1");
    }
}
