package com.medkernel.engine.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

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

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.UserRoleAssignmentRepository;
import com.medkernel.engine.security.auth.PlatformCredentialDevSeeder;

/**
 * 首次部署引导端点：部署 token 只用于接管，不签发业务登录态。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BootstrapControllerTest {

    @Autowired MockMvc mvc;
    @Autowired BootstrapInitTokenService tokenService;
    @Autowired BootstrapInitTokenRepository tokenRepository;
    @Autowired PlatformCredentialRepository credentialRepository;
    @Autowired UserRoleAssignmentRepository roleAssignmentRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        credentialRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
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
    void passwordCreatesFirstPlatformAdminAndConsumesToken() throws Exception {
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
            .andExpect(jsonPath("$.data.roles", contains(RoleCode.PLATFORM_ADMIN.code())))
            .andExpect(jsonPath("$.data.mustChangePwd").value(true))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        PlatformCredential credential = credentialRepository
            .findByTenantIdAndUsername("t-1", "platform-owner").orElseThrow();
        assertThat(credential.mustChangePwd()).isEqualTo("Y");
        assertThat(passwordEncoder.matches("StrongPwd@2026", credential.passwordHash())).isTrue();
        assertThat(roleAssignmentRepository.findActiveByTenantIdAndUserId("t-1", "platform-owner"))
            .extracting("roleCode")
            .containsExactly(RoleCode.PLATFORM_ADMIN.code());

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
            .andExpect(jsonPath("$.data.roles", contains(RoleCode.PLATFORM_ADMIN.code())));

        mvc.perform(post("/api/v1/bootstrap/mfa")
                .with(jwt().jwt(t -> t.subject("platform-owner").claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"首发管理员\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaBound").value(true))
            .andExpect(jsonPath("$.data.recoveryCode").isNotEmpty());

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
            .andExpect(jsonPath("$.code").value("ENG-AUTH-009"));
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
    void devSeederIsStillDevProfileOnly() {
        Profile profile = PlatformCredentialDevSeeder.class.getAnnotation(Profile.class);

        assertThat(profile.value()).containsExactly("dev");
    }
}
