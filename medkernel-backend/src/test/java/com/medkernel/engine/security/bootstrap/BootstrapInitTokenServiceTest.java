package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;

class BootstrapInitTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T08:00:00Z");

    private BootstrapInitTokenRepository tokens;
    private BootstrapInitTokenService service;
    private AtomicReference<BootstrapInitToken> saved;

    @BeforeEach
    void setUp() {
        tokens = mock(BootstrapInitTokenRepository.class);
        service = new BootstrapInitTokenService(tokens, Clock.fixed(NOW, ZoneOffset.UTC));
        saved = new AtomicReference<>();
        when(tokens.save(any())).thenAnswer(inv -> {
            BootstrapInitToken token = inv.getArgument(0);
            saved.set(token);
            return token;
        });
    }

    @Test
    void registerDeploymentTokenStoresOnlySha256Hash() {
        BootstrapInitToken token = service.registerDeploymentToken(
            "mk-init-token-raw", Duration.ofMinutes(30), "deploy-cli", "trace-seed");

        assertThat(token.tokenHash()).hasSize(64).doesNotContain("mk-init-token-raw");
        assertThat(token.status()).isEqualTo(BootstrapInitTokenStatus.ACTIVE.name());
        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        assertThat(token.createdBy()).isEqualTo("deploy-cli");
        assertThat(saved.get().tokenHash()).isEqualTo(token.tokenHash());
    }

    @Test
    void registerDeploymentTokenRejectsBlankToken() {
        assertThatThrownBy(() -> service.registerDeploymentToken("  ", Duration.ofMinutes(30), "deploy-cli", "trace-seed"))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.errorCode()).isEqualTo(ErrorCode.ENG_AUTH_007));

        verify(tokens, never()).save(any());
    }

    @Test
    void registerDeploymentTokenReusesExistingHashForIdempotentStartup() {
        String rawToken = "mk-init-token-raw";
        String hash = BootstrapInitTokenService.sha256(rawToken);
        BootstrapInitToken existing = active(hash, NOW.plusSeconds(120));
        when(tokens.findFirstByTokenHashOrderByCreatedAtDesc(hash)).thenReturn(Optional.of(existing));

        BootstrapInitToken token = service.registerDeploymentToken(
            rawToken, Duration.ofMinutes(30), "deploy-cli", "trace-seed");

        assertThat(token).isSameAs(existing);
        verify(tokens, never()).save(any());
    }

    @Test
    void consumeMarksActiveTokenUsedExactlyOnce() {
        String rawToken = "mk-init-token-raw";
        String hash = BootstrapInitTokenService.sha256(rawToken);
        when(tokens.findFirstByTokenHashOrderByCreatedAtDesc(hash))
            .thenReturn(Optional.of(active(hash, NOW.plusSeconds(120))));

        BootstrapInitToken consumed = service.consume(rawToken, "bootstrap-admin", "trace-use");

        assertThat(consumed.status()).isEqualTo(BootstrapInitTokenStatus.USED.name());
        assertThat(consumed.usedBy()).isEqualTo("bootstrap-admin");
        assertThat(consumed.usedAt()).isEqualTo(NOW);
        assertThat(consumed.traceId()).isEqualTo("trace-use");
        verify(tokens).save(consumed);
    }

    @Test
    void consumeRejectsExpiredTokenWithHonestErrorCode() {
        String rawToken = "expired-token";
        String hash = BootstrapInitTokenService.sha256(rawToken);
        when(tokens.findFirstByTokenHashOrderByCreatedAtDesc(hash))
            .thenReturn(Optional.of(active(hash, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.consume(rawToken, "bootstrap-admin", "trace-use"))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.errorCode()).isEqualTo(ErrorCode.ENG_AUTH_008));
    }

    @Test
    void consumeRejectsUsedTokenWithHonestErrorCode() {
        String rawToken = "used-token";
        String hash = BootstrapInitTokenService.sha256(rawToken);
        BootstrapInitToken used = active(hash, NOW.plusSeconds(120))
            .withStatus(BootstrapInitTokenStatus.USED, NOW.minusSeconds(30), "bootstrap-admin", "trace-old");
        when(tokens.findFirstByTokenHashOrderByCreatedAtDesc(hash)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.consume(rawToken, "bootstrap-admin-2", "trace-use"))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.errorCode()).isEqualTo(ErrorCode.ENG_AUTH_009));
    }

    private BootstrapInitToken active(String hash, Instant expiresAt) {
        return new BootstrapInitToken(
            1L, "init-token-1", hash, BootstrapInitTokenStatus.ACTIVE.name(),
            expiresAt, null, null, NOW.minusSeconds(60), "deploy-cli", NOW.minusSeconds(60), "deploy-cli", "trace-seed");
    }
}
