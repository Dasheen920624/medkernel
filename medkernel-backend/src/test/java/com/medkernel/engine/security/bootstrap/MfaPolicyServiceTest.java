package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.crypto.SmCryptoService;
import com.medkernel.shared.security.JwtSecretResolver;
import com.medkernel.shared.security.MfaRuntimePolicy;

class MfaPolicyServiceTest {

    private PlatformCredentialRepository credentials;
    private MfaPolicyService service;
    private TotpService totpService;
    private MfaSecretCodec secretCodec;
    private AtomicReference<PlatformCredential> saved;
    private MfaRuntimePolicy runtimePolicy;

    @BeforeEach
    void setUp() {
        credentials = mock(PlatformCredentialRepository.class);
        totpService = new TotpService();
        JwtSecretResolver secretResolver = mock(JwtSecretResolver.class);
        when(secretResolver.resolve()).thenReturn("medkernel-test-secret-for-mfa-32-bytes");
        SmCryptoService crypto = new SmCryptoService();
        secretCodec = new MfaSecretCodec(crypto, secretResolver);
        runtimePolicy = mock(MfaRuntimePolicy.class);
        when(runtimePolicy.enabled()).thenReturn(true);
        service = new MfaPolicyService(credentials, totpService, secretCodec, runtimePolicy);
        saved = new AtomicReference<>();
        when(credentials.save(any())).thenAnswer(inv -> {
            PlatformCredential credential = inv.getArgument(0);
            saved.set(credential);
            return credential;
        });
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-mfa", new OrgScope("t-1", null, null, null, null, null, null, null), "platform-owner"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void highRiskActionRejectsCurrentUserWithoutMfa() {
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential(null)));

        assertThatThrownBy(() -> service.assertHighRiskAllowed("system_config", "medkernel.auth.jwt.ttl-seconds"))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.errorCode()).isEqualTo(ErrorCode.ENG_AUTH_010));
    }

    @Test
    void highRiskActionRejectsLegacyRecoveryHashWithoutTotpSecret() {
        authenticate(true);
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential("sha256-mfa-recovery-code")));

        assertThatThrownBy(() -> service.assertHighRiskAllowed("system_config", "medkernel.auth.jwt.ttl-seconds"))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.errorCode()).isEqualTo(ErrorCode.ENG_AUTH_010));
    }

    @Test
    void highRiskActionAllowsCurrentUserWithMfa() {
        authenticate(true);
        String secret = totpService.generateSecret();
        String stored = secretCodec.encode(secret, "Recovery@2026");
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential(stored)));

        service.assertHighRiskAllowed("system_config", "medkernel.auth.jwt.ttl-seconds");
    }

    @Test
    void highRiskActionDoesNotRequireMfaWhenFeatureIsDisabled() {
        when(runtimePolicy.enabled()).thenReturn(false);

        service.assertHighRiskAllowed("platform_tenant", "t-2");
    }

    @Test
    void bindForCurrentUserDoesNotMarkMfaBoundBeforeTotpCodeIsVerified() {
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential(null)));

        BootstrapMfaResponse response = service.bindForCurrentUser(new BootstrapMfaRequest("初始管理员"));

        assertThat(response.mfaBound()).isFalse();
        assertThat(response.recoveryCode()).isNull();
        assertThat(saved.get()).isNull();
    }

    @Test
    void bindForCurrentUserStoresEncryptedTotpSecretAfterVerifiedCode() {
        when(credentials.findByTenantIdAndUserId("t-1", "platform-owner"))
            .thenReturn(Optional.of(credential(null)));
        String secret = totpService.generateSecret();
        String code = totpService.codeAt(secret, Instant.now());

        BootstrapMfaResponse response =
            service.bindForCurrentUser(new BootstrapMfaRequest("初始管理员", secret, code));

        assertThat(response.mfaBound()).isTrue();
        assertThat(response.recoveryCode()).isNotBlank();
        assertThat(saved.get().mfaSecret()).startsWith("totp:v1:");
        assertThat(saved.get().mfaSecret()).doesNotContain(secret, response.recoveryCode());
        assertThat(secretCodec.decodeTotpSecret(saved.get().mfaSecret())).contains(secret);
    }

    private PlatformCredential credential(String mfaSecret) {
        Instant now = Instant.parse("2026-06-01T08:00:00Z");
        return new PlatformCredential(
            1L, "cred-platform-owner", "t-1", "platform-owner", "platform-owner",
            "$2a$10$hash", "ACTIVE", "Y", mfaSecret,
            now, "test", now, "test", "trace-test");
    }

    private void authenticate(boolean mfaVerified) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS256")
            .subject("platform-owner")
            .claim("tenant_id", "t-1")
            .claim("mfa_verified", mfaVerified)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
