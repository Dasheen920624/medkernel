package com.medkernel.engine.security.auth;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PlatformCredentialRepository credentialRepository;

    @Autowired
    UserRoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtIssuer jwtIssuer;

    private static final String TENANT = "t-1";
    private static final String USERNAME = "doctor-test";
    private static final String USER_ID = "doctor-1";
    private static final String RAW_PASSWORD = "Mk@2026pw";
    private static final String COOKIE_SECURE_KEY = "medkernel.auth.cookie.secure";
    private static final String COOKIE_SAME_SITE_KEY = "medkernel.auth.cookie.same-site";
    private static final String COOKIE_MAX_AGE_KEY = "medkernel.auth.cookie.max-age-seconds";
    private static final String SESSION_IDLE_TIMEOUT_KEY = "medkernel.auth.session.idle-timeout-seconds";
    private static final String SESSION_WARNING_KEY = "medkernel.auth.session.warning-seconds";
    private static final String SESSION_MAX_DURATION_KEY = "medkernel.auth.session.max-duration-seconds";
    private static final String JWT_TTL_KEY = "medkernel.auth.jwt.ttl-seconds";
    private static final String AUTH_MODE_KEY = "medkernel.auth.mode";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";

    @BeforeEach
    void setUp() {
        // 清理旧数据（保证幂等）
        credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME)
            .ifPresent(c -> credentialRepository.delete(c));

        Instant now = Instant.now();
        // 插入 ACTIVE 凭证
        credentialRepository.save(new PlatformCredential(
            null, "cred-doctor-test", TENANT, USER_ID, USERNAME,
            passwordEncoder.encode(RAW_PASSWORD), "ACTIVE", "N", null,
            now, "test", now, "test", "test-trace"
        ));

        // 插入角色分配（仅当不存在时）
        boolean hasRole = roleAssignmentRepository.findActiveByTenantIdAndUserId(TENANT, USER_ID)
            .stream().anyMatch(a -> RoleCode.DOCTOR.code().equals(a.roleCode()));
        if (!hasRole) {
            roleAssignmentRepository.save(new UserRoleAssignment(
                null, TENANT, USER_ID, RoleCode.DOCTOR.code(), "TENANT", TENANT,
                "Y", now, "test", now, "test"
            ));
        }
    }

    @AfterEach
    void cleanUp() {
        // I2: 清理凭证
        credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME)
            .ifPresent(c -> credentialRepository.delete(c));
        // I2: 对称清理角色分配
        List<UserRoleAssignment> assignments =
            roleAssignmentRepository.findActiveByTenantIdAndUserId(TENANT, USER_ID);
        roleAssignmentRepository.deleteAll(assignments);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'false', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, COOKIE_SECURE_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = 'Strict', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, COOKIE_SAME_SITE_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '28800', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, COOKIE_MAX_AGE_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '1800', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, SESSION_IDLE_TIMEOUT_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '120', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, SESSION_WARNING_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '28800', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, SESSION_MAX_DURATION_KEY);
        jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = '28800', source = 'YML_SEED', version = 1, updated_by = 'test-cleanup'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, JWT_TTL_KEY);
        upsertAuthMode("PLATFORM");
    }

    @Test
    void login_success_setsHttpOnlyCookieAndXsrfToken() throws Exception {
        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        // I1: 改用 MockMvc 真断言代替 Java assert（assert 关键字默认不执行）
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("mk_access"))
            .andExpect(cookie().httpOnly("mk_access", true))
            .andExpect(cookie().exists(XSRF_COOKIE))
            .andExpect(cookie().httpOnly(XSRF_COOKIE, false))
            .andExpect(jsonPath("$.data.userId").value(USER_ID))
            .andExpect(jsonPath("$.data.tenantId").value(TENANT))
            .andExpect(jsonPath("$.data.mustChangePwd").value(false));
    }

    @Test
    void loginDelegatedModeRejectsPlatformPasswordLogin() throws Exception {
        upsertAuthMode("DELEGATED");

        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-013"));
    }

    @Test
    void invalidAuthModeFailsClosedForPlatformPasswordLogin() throws Exception {
        upsertAuthMode("LEGACY");

        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-013"));
    }

    @Test
    void delegatedStatusReportsNotConnectedWithoutFakingLogin() throws Exception {
        upsertAuthMode("BOTH");

        mvc.perform(get("/api/v1/auth/delegated/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("BOTH"))
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.status").value("NOT_CONNECTED"));
    }

    @Test
    void delegatedCallbackReturnsNotConnectedWhenIdpIsUnconfigured() throws Exception {
        upsertAuthMode("DELEGATED");

        mvc.perform(post("/api/v1/auth/delegated/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-014"));
    }

    @Test
    void loginCookiePolicyComesFromConfigCenterWithoutRestart() throws Exception {
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = 'true' WHERE config_key = ?", COOKIE_SECURE_KEY);
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = 'Lax' WHERE config_key = ?", COOKIE_SAME_SITE_KEY);
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '120' WHERE config_key = ?", COOKIE_MAX_AGE_KEY);

        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString("Max-Age=120"),
                containsString("SameSite=Lax"),
                containsString("Secure"))));
    }

    @Test
    void sessionStatusAndRenewUseConfigCenterPolicyWithoutRestart() throws Exception {
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '120' WHERE config_key = ?", JWT_TTL_KEY);
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '30' WHERE config_key = ?", SESSION_IDLE_TIMEOUT_KEY);
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '8' WHERE config_key = ?", SESSION_WARNING_KEY);
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '300' WHERE config_key = ?", SESSION_MAX_DURATION_KEY);

        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=30")))
            .andExpect(jsonPath("$.data.session.idleTimeoutSeconds").value(30))
            .andExpect(jsonPath("$.data.session.warningSeconds").value(8))
            .andReturn();

        var cookie = login.getResponse().getCookie("mk_access");

        mvc.perform(get("/api/v1/auth/session").cookie(cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.idleTimeoutSeconds").value(30))
            .andExpect(jsonPath("$.data.warningSeconds").value(8))
            .andExpect(jsonPath("$.data.maxSessionSeconds").value(300))
            .andExpect(jsonPath("$.data.remainingSeconds").isNumber());

        Cookie xsrf = login.getResponse().getCookie(XSRF_COOKIE);
        mvc.perform(post("/api/v1/auth/session/renew")
                .cookie(cookie)
                .cookie(xsrf)
                .header(XSRF_HEADER, xsrf.getValue()))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("mk_access"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=30")))
            .andExpect(jsonPath("$.data.idleTimeoutSeconds").value(30))
            .andExpect(jsonPath("$.data.warningSeconds").value(8))
            .andExpect(jsonPath("$.data.maxSessionRemainingSeconds").isNumber());
    }

    @Test
    void cookieSessionRenewRejectsMissingXsrfHeader() throws Exception {
        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();

        mvc.perform(post("/api/v1/auth/session/renew")
                .cookie(login.getResponse().getCookie("mk_access"))
                .cookie(new Cookie(XSRF_COOKIE, "xsrf-token-without-header")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-API-004"));
    }

    @Test
    void protectedSessionRejectsJwtWhenRuntimeSessionPolicyIsShortened() throws Exception {
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '5' WHERE config_key = ?", SESSION_IDLE_TIMEOUT_KEY);
        jdbcTemplate.update("UPDATE mk_config_item SET config_value = '300' WHERE config_key = ?", SESSION_MAX_DURATION_KEY);

        Instant now = Instant.now();
        JwtIssuer.IssuedJwt issued = jwtIssuer.issueSession(
            USER_ID,
            TENANT,
            List.of(RoleCode.DOCTOR.code()),
            now.minusSeconds(20),
            now.minusSeconds(10),
            now.plusSeconds(120));

        mvc.perform(get("/api/v1/auth/session").cookie(new Cookie("mk_access", issued.token())))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPassword_rejectedWithoutLeakingExistence() throws Exception {
        // 错误密码 → 401 + ENG-AUTH-001
        var wrongPwd = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, "WrongPassword123", TENANT));
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongPwd))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-001"));

        // 不存在用户 → 也 401 + ENG-AUTH-001（防枚举：状态码与错误码均一致）
        var noUser = objectMapper.writeValueAsString(
            new LoginRequest("nobody-xyz", "anything", TENANT));
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noUser))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-001"));
    }

    @Test
    void loginDisabledCredentialUsesSameErrorCodeAsWrongPassword() throws Exception {
        PlatformCredential active = credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME).orElseThrow();
        credentialRepository.save(new PlatformCredential(
            active.id(), active.credentialId(), active.tenantId(), active.userId(), active.username(),
            active.passwordHash(), "DISABLED", active.mustChangePwd(), active.mfaSecret(),
            active.createdAt(), active.createdBy(), Instant.now(), "test", active.traceId()));

        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-001"));
    }

    private void upsertAuthMode(String value) {
        int updated = jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = ?, source = 'YML_SEED', version = 1, updated_by = 'test'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, value, AUTH_MODE_KEY);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO mk_config_item (
                config_id, tenant_id, config_key, config_value, value_type, display_name,
                risk_level, owner, description, source, protected_flag, active_flag,
                version, created_at, created_by, updated_at, updated_by
            ) VALUES (
                'cfg-auth-mode-test', 'SYSTEM', ?, ?, 'STRING', '认证模式',
                'HIGH', '安全组', '控制平台账号与院方统一身份认证模式。', 'YML_SEED', 'Y', 'Y',
                1, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'
            )
            """, AUTH_MODE_KEY, value);
    }
}
