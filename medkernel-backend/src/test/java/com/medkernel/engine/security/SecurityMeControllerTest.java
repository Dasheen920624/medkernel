package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMeControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformCredentialRepository credentialRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @AfterEach
    void clearAssignments() {
        jdbcTemplate.update("DELETE FROM user_role_assignment");
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'false', updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, "medkernel.auth.mfa.enabled");
        credentialRepository.findByTenantIdAndUserId("t-1", "platform-owner")
            .ifPresent(credentialRepository::delete);
    }

    @Test
    void currentUserReceivesRolesPermissionsMenusAndDataScope() throws Exception {
        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "d-1")
                    .claim("roles", List.of(RoleCode.CLINICAL_USER.code())))
                    .authorities(new SimpleGrantedAuthority(RoleCode.CLINICAL_USER.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("doctor-1"))
            .andExpect(jsonPath("$.data.username").value("doctor-1"))
            .andExpect(jsonPath("$.data.roles[*].code", hasItem(RoleCode.CLINICAL_USER.code())))
            .andExpect(jsonPath("$.data.permissions[*].code", hasItem(PermissionCode.RECOMMENDATION_READ.code())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.ACTION.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.MENU.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.DATA.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.ASSET.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.ENVIRONMENT.name())))
            .andExpect(jsonPath("$.data.permissions[*].target", hasItem("recommendation")))
            .andExpect(jsonPath("$.data.permissions[*].code", not(hasItem(PermissionCode.RULE_PUBLISH.code()))))
            .andExpect(jsonPath("$.data.menuKeys", hasItem("cdss-fatigue")))
            .andExpect(jsonPath("$.data.menuKeys", not(hasItem("clinical-run"))))
            .andExpect(jsonPath("$.data.environmentKeys", hasItem("production")))
            .andExpect(jsonPath("$.data.environmentKeys", not(hasItem("emergency"))))
            .andExpect(jsonPath("$.data.dataScope.tenantId").value("t-1"))
            .andExpect(jsonPath("$.data.dataScope.hospitalId").value("h-1"))
            .andExpect(jsonPath("$.data.dataScope.departmentId").value("d-1"));
    }

    @Test
    void currentUserIncludesBootstrapSecurityCompletionFlags() throws Exception {
        java.time.Instant now = java.time.Instant.parse("2026-06-01T08:00:00Z");
        credentialRepository.save(new PlatformCredential(
            null, "cred-platform-owner", "t-1", "platform-owner", "platform-owner",
            passwordEncoder.encode("Init@2026pw"), "ACTIVE", "Y", null,
            now, "test", now, "test", "trace-bootstrap"));

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("platform-owner")
                    .claim("tenant_id", "t-1")
                    .claim("roles", List.of(RoleCode.PLATFORM_ADMIN.code())))
                    .authorities(new SimpleGrantedAuthority(RoleCode.PLATFORM_ADMIN.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("platform-owner"))
            .andExpect(jsonPath("$.data.username").value("platform-owner"))
            .andExpect(jsonPath("$.data.mustChangePwd").value(true))
            .andExpect(jsonPath("$.data.mfaRequired").value(false))
            .andExpect(jsonPath("$.data.mfaBound").value(false))
            .andExpect(jsonPath("$.data.mfaVerified").value(false));
    }

    @Test
    void currentUserReportsWhetherTheCurrentSessionCompletedMfa() throws Exception {
        upsertMfaEnabled(true);

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1")
                    .claim("roles", List.of(RoleCode.CLINICAL_USER.code()))
                    .claim("mfa_verified", false))
                    .authorities(new SimpleGrantedAuthority(RoleCode.CLINICAL_USER.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaRequired").value(true))
            .andExpect(jsonPath("$.data.mfaVerified").value(false));
    }

    private void upsertMfaEnabled(boolean enabled) {
        int updated = jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = ?, updated_by = 'test'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, Boolean.toString(enabled), "medkernel.auth.mfa.enabled");
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO mk_config_item (
                config_id, tenant_id, config_key, config_value, value_type, display_name,
                risk_level, owner, description, source, protected_flag, active_flag,
                version, created_at, created_by, updated_at, updated_by
            ) VALUES (
                'cfg-test-mfa-enabled', 'SYSTEM', ?, ?, 'BOOLEAN', '登录 MFA',
                'HIGH', '安全组', '测试 MFA 运行策略。', 'YML_SEED', 'Y', 'Y',
                1, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'
            )
            """, "medkernel.auth.mfa.enabled", Boolean.toString(enabled));
    }

    @Test
    void currentUserAppliesScopedAssignmentWithoutAllowingTenantPermissionRewrite() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO user_role_assignment
                (tenant_id, user_id, role_code, scope_level, scope_code)
            VALUES (?, ?, ?, ?, ?)
            """, "t-1", "doctor-1", RoleCode.ENGINE_OPERATOR.code(), "DEPARTMENT", "d-1");

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1")
                    .claim("department_id", "d-1")
                    .claim("roles", List.of(RoleCode.CLINICAL_USER.code())))
                    .authorities(new SimpleGrantedAuthority(RoleCode.CLINICAL_USER.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].code", hasItem(RoleCode.ENGINE_OPERATOR.code())))
            .andExpect(jsonPath("$.data.permissions[*].code", hasItem(PermissionCode.EVALUATION_PUBLISH.code())))
            .andExpect(jsonPath("$.data.permissions[*].code",
                hasItem(PermissionCode.RECOMMENDATION_ACCEPT.code())));
    }

    @Test
    void ordinaryUserCannotAcquireEmergencyPermissionFromDatabaseGrant() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO emergency_permission_grant
                (tenant_id, user_id, permission_code, reason, granted_by, granted_at,
                 expires_at, active_flag, created_by, updated_by)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, DATEADD('HOUR', 1, CURRENT_TIMESTAMP), ?, ?, ?)
            """,
            "t-1",
            "doctor-1",
            PermissionCode.ENV_EMERGENCY.code(),
            "抢救高危患者需要临时访问应急环境",
            "chief-1",
            "Y",
            "chief-1",
            "chief-1");

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1")
                    .claim("roles", List.of(RoleCode.CLINICAL_USER.code())))
                    .authorities(new SimpleGrantedAuthority(RoleCode.CLINICAL_USER.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permissions[*].code", not(hasItem(PermissionCode.ENV_EMERGENCY.code()))))
            .andExpect(jsonPath("$.data.environmentKeys", not(hasItem("emergency"))));
    }

    @Test
    void currentUserEndpointRequiresTenantContext() throws Exception {
        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("roles", List.of(RoleCode.CLINICAL_USER.code())))
                    .authorities(new SimpleGrantedAuthority(RoleCode.CLINICAL_USER.authority()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
