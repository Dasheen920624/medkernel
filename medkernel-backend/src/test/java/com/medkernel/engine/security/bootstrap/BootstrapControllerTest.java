package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.auth.PlatformCredentialDevSeeder;
import com.medkernel.shared.api.error.ApiException;

/**
 * 首次部署引导端点：部署 token 只用于接管，不签发业务登录态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapControllerTest {

    @Autowired MockMvc mvc;
    @Autowired BootstrapInitTokenService tokenService;
    @Autowired BootstrapIdentityService bootstrapIdentityService;
    @Autowired BootstrapInitTokenRepository tokenRepository;
    @Autowired PlatformCredentialRepository credentialRepository;
    @Autowired TenantUserRepository tenantUserRepository;
    @Autowired UserRoleAssignmentRepository roleAssignmentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;
    @Autowired TotpService totpService;

    @AfterEach
    void cleanUp() {
        credentialRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        tenantUserRepository.deleteAll();
        tokenRepository.deleteAll();
    }

    @Test
    void initTokenChecksTokenWithoutCreatingAccountOrCookie() throws Exception {
        tokenService.registerDeploymentToken("mk-init-token", Duration.ofMinutes(15), "test", "trace-test");

        mvc.perform(post("/api/v1/bootstrap/init-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"mk-init-token\"}"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.expiresAt").exists());

        assertThat(credentialRepository.findByTenantIdAndUsername("t-1", "platform-owner")).isEmpty();
    }

    @Test
    void bootstrapStatusTracksInitializationAndFreshTokenCannotCreateSecondSuperAdmin() throws Exception {
        mvc.perform(get("/api/v1/bootstrap/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.initialized").value(false));

        tokenService.registerDeploymentToken("mk-init-token-1", Duration.ofMinutes(15), "test", "trace-test");
        mvc.perform(post("/api/v1/bootstrap/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "mk-init-token-1",
                      "username": "platform-owner",
                      "password": "StrongPwd@2026"
                    }
                    """))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/bootstrap/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.initialized").value(true));

        tokenService.registerDeploymentToken("mk-init-token-2", Duration.ofMinutes(15), "test", "trace-test");
        mvc.perform(post("/api/v1/bootstrap/init-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"mk-init-token-2\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-017"));

        mvc.perform(post("/api/v1/bootstrap/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "mk-init-token-2",
                      "username": "platform-owner-2",
                      "password": "StrongPwd@2026"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-017"));

        assertThat(credentialRepository.findByTenantIdAndUsername("t-1", "platform-owner-2")).isEmpty();
    }

    @Test
    void bootstrapAlwaysCreatesTheFirstAdminInThePlatformTenant() throws Exception {
        tokenService.registerDeploymentToken("mk-init-token", Duration.ofMinutes(15), "test", "trace-test");

        mvc.perform(post("/api/v1/bootstrap/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "mk-init-token",
                      "tenantId": "customer-tenant",
                      "username": "platform-owner",
                      "password": "StrongPwd@2026"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tenantId").value("t-1"));

        assertThat(credentialRepository.findByTenantIdAndUsername("t-1", "platform-owner")).isPresent();
        assertThat(credentialRepository.findByTenantIdAndUsername("customer-tenant", "platform-owner")).isEmpty();
    }

    @Test
    void concurrentBootstrapRequestsCreateExactlyOneSystemSuperAdmin() throws Exception {
        tokenService.registerDeploymentToken("mk-init-token-1", Duration.ofMinutes(15), "test", "trace-test");
        tokenService.registerDeploymentToken("mk-init-token-2", Duration.ofMinutes(15), "test", "trace-test");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> createAdminAfterBarrier(
                ready, start, new BootstrapPasswordRequest(
                    "mk-init-token-1", "platform-owner-1", "StrongPwd@2026")));
            var second = executor.submit(() -> createAdminAfterBarrier(
                ready, start, new BootstrapPasswordRequest(
                    "mk-init-token-2", "platform-owner-2", "StrongPwd@2026")));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("OK", "ENG-AUTH-017");
            assertThat(roleAssignmentRepository.findAll().stream()
                .filter(assignment -> "system-superadmin".equals(assignment.roleCode())))
                .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void passwordCreatesFirstSystemSuperAdminAndConsumesToken() throws Exception {
        tokenService.registerDeploymentToken("mk-init-token", Duration.ofMinutes(15), "test", "trace-test");

        mvc.perform(post("/api/v1/bootstrap/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "mk-init-token",
                      "tenantId": "t-1",
                      "username": "platform-owner",
                      "password": "StrongPwd@2026"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andExpect(jsonPath("$.data.userId").value("platform-owner"))
            .andExpect(jsonPath("$.data.username").value("platform-owner"))
            .andExpect(jsonPath("$.data.tenantId").value("t-1"))
            .andExpect(jsonPath("$.data.roles", contains("system-superadmin")))
            .andExpect(jsonPath("$.data.mustChangePwd").value(true))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        PlatformCredential credential = credentialRepository
            .findByTenantIdAndUsername("t-1", "platform-owner").orElseThrow();
        assertThat(credential.mustChangePwd()).isEqualTo("Y");
        assertThat(passwordEncoder.matches("StrongPwd@2026", credential.passwordHash())).isTrue();
        assertThat(roleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "platform-owner"))
            .extracting("roleCode")
            .containsExactly("system-superadmin");

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "t-1",
                      "username": "platform-owner",
                      "password": "StrongPwd@2026"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mustChangePwd").value(true))
            .andExpect(jsonPath("$.data.mfaRequired").value(true))
            .andExpect(jsonPath("$.data.mfaBound").value(false))
            .andExpect(jsonPath("$.data.roles", contains("system-superadmin")));

        mvc.perform(post("/api/v1/auth/change-password")
                .with(jwt().jwt(t -> t.subject("platform-owner").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "oldPassword": "StrongPwd@2026",
                      "newPassword": "StrongPwd@2026!"
                    }
                    """))
            .andExpect(status().isOk());

        var setup = mvc.perform(post("/api/v1/bootstrap/mfa")
                .with(jwt().jwt(t -> t.subject("platform-owner").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"首发管理员\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaBound").value(false))
            .andExpect(jsonPath("$.data.secret").isNotEmpty())
            .andExpect(jsonPath("$.data.otpauthUri").isNotEmpty())
            .andExpect(jsonPath("$.data.recoveryCode").doesNotExist())
            .andReturn();
        String secret = objectMapper.readTree(setup.getResponse().getContentAsByteArray())
            .at("/data/secret")
            .asText();
        String code = totpService.codeAt(secret, java.time.Instant.now());

        mvc.perform(post("/api/v1/bootstrap/mfa")
                .with(jwt().jwt(t -> t.subject("platform-owner").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "label": "首发管理员",
                      "secret": "%s",
                      "code": "%s"
                    }
                    """.formatted(secret, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaBound").value(true))
            .andExpect(jsonPath("$.data.recoveryCode").isNotEmpty());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "tenantId": "t-1",
                      "username": "platform-owner",
                      "password": "StrongPwd@2026!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mustChangePwd").value(false))
            .andExpect(jsonPath("$.data.mfaRequired").value(true))
            .andExpect(jsonPath("$.data.mfaBound").value(true));

        mvc.perform(post("/api/v1/bootstrap/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "mk-init-token",
                      "tenantId": "t-1",
                      "username": "platform-owner-2",
                      "password": "StrongPwd@2026"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-017"));
    }

    @Test
    void expiredInitTokenReturnsHonestError() throws Exception {
        tokenService.registerDeploymentToken("expired-init-token", Duration.ofSeconds(-1), "test", "trace-test");

        mvc.perform(post("/api/v1/bootstrap/init-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"expired-init-token\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-008"));
    }

    @Test
    void authMfaEndpointsBindAndVerifyTotpCode() throws Exception {
        java.time.Instant now = java.time.Instant.now();
        credentialRepository.save(new PlatformCredential(
            null, "cred-mfa-user", "t-1", "mfa-user", "mfa-user",
            passwordEncoder.encode("StrongPwd@2026!"), "ACTIVE", "N", null,
            now, "test", now, "test", "trace-mfa"));

        var setup = mvc.perform(post("/api/v1/auth/mfa/bind")
                .with(jwt().jwt(t -> t.subject("mfa-user").claim("tenant_id", "t-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"mfa-user\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaBound").value(false))
            .andExpect(jsonPath("$.data.secret").isNotEmpty())
            .andReturn();
        String secret = objectMapper.readTree(setup.getResponse().getContentAsByteArray())
            .at("/data/secret")
            .asText();
        String code = totpService.codeAt(secret, java.time.Instant.now());

        mvc.perform(post("/api/v1/auth/mfa/bind")
                .with(jwt().jwt(t -> t.subject("mfa-user").claim("tenant_id", "t-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "label": "mfa-user",
                      "secret": "%s",
                      "code": "%s"
                    }
                    """.formatted(secret, code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaBound").value(true))
            .andExpect(jsonPath("$.data.recoveryCode").isNotEmpty());

        mvc.perform(post("/api/v1/auth/mfa/verify")
                .with(jwt().jwt(t -> t.subject("mfa-user").claim("tenant_id", "t-1")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"%s\"}".formatted(code)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verified").value(true));
    }

    @Test
    void devSeederIsStillDevProfileOnly() {
        Profile profile = PlatformCredentialDevSeeder.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("dev");
    }

    private String createAdminAfterBarrier(
            CountDownLatch ready, CountDownLatch start, BootstrapPasswordRequest request) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            bootstrapIdentityService.createFirstAdmin(request);
            return "OK";
        } catch (ApiException exception) {
            return exception.errorCode().code();
        }
    }
}
