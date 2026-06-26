package com.medkernel.engine.security.auth;

import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
import com.medkernel.engine.security.bootstrap.MfaSecretCodec;

import jakarta.servlet.http.Cookie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
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
    LoginAttemptStateRepository loginAttemptRepository;

    @Autowired
    UserRoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JwtIssuer jwtIssuer;

    @Autowired
    MfaSecretCodec mfaSecretCodec;

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
    private static final String MFA_ENABLED_KEY = "medkernel.auth.mfa.enabled";
    private static final String LOGIN_MAX_FAILED_ATTEMPTS_KEY = "medkernel.auth.login.max-failed-attempts";
    private static final String LOGIN_LOCKOUT_SECONDS_KEY = "medkernel.auth.login.lockout-seconds";
    private static final String LOGIN_RATE_LIMIT_ATTEMPTS_KEY = "medkernel.auth.login.rate-limit-attempts";
    private static final String LOGIN_RATE_LIMIT_WINDOW_SECONDS_KEY = "medkernel.auth.login.rate-limit-window-seconds";
    private static final String XSRF_COOKIE = "XSRF-TOKEN";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final String AUTH_TEST_HOSPITAL = "hospital-auth-main";
    private static final String AUTH_TEST_DEPARTMENT = "dept-auth-cardiology";
    private static final String AUTH_TEST_HOSPITAL_ID = "auth-facility-01";
    private static final String AUTH_TEST_DEPARTMENT_ID = "auth-dept-01";

    @BeforeEach
    void setUp() {
        // 清理旧数据（保证幂等）
        loginAttemptRepository.deleteAll();
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
            .stream().anyMatch(a -> RoleCode.CLINICAL_USER.code().equals(a.roleCode()));
        if (!hasRole) {
            roleAssignmentRepository.save(new UserRoleAssignment(
                null, TENANT, USER_ID, RoleCode.CLINICAL_USER.code(), "TENANT", TENANT,
                "Y", now, "test", now, "test"
            ));
        }
    }

    @Test
    void loginTenantsIsPublicAndDefaultsToPlatformTenantWhenNoCustomerTenantExists() throws Exception {
        mvc.perform(get("/api/v1/auth/login-tenants"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hasCustomerTenants").value(false))
            .andExpect(jsonPath("$.data.primaryTenants[0].tenantId").value("t-1"))
            .andExpect(jsonPath("$.data.platformTenant.name").value("平台治理入口（唯一内置）"));
    }

    @Test
    void loginCookieJwtCarriesPrimaryOrgScopeFromRoleAssignment() throws Exception {
        List<UserRoleAssignment> assignments =
            roleAssignmentRepository.findActiveByTenantIdAndUserId(TENANT, USER_ID);
        roleAssignmentRepository.deleteAll(assignments);
        jdbcTemplate.update("""
            DELETE FROM org_unit
             WHERE tenant_id = ?
               AND code IN (?, ?)
            """, TENANT, AUTH_TEST_DEPARTMENT, AUTH_TEST_HOSPITAL);
        jdbcTemplate.update("""
            INSERT INTO org_unit (
                id, parent_id, tenant_id, org_path, level_code, code, name,
                facility_type, status, created_by, updated_by
            ) VALUES (?, NULL, ?, ?, 'FACILITY', ?, '登录测试医院',
                'HOSPITAL', 'ACTIVE', 'test', 'test')
            """, AUTH_TEST_HOSPITAL_ID, TENANT, "/" + TENANT + "/" + AUTH_TEST_HOSPITAL, AUTH_TEST_HOSPITAL);
        jdbcTemplate.update("""
            INSERT INTO org_unit (
                id, parent_id, tenant_id, org_path, level_code, code, name,
                facility_type, status, created_by, updated_by
            ) VALUES (?, ?, ?, ?, 'DEPARTMENT', ?, '登录测试心内科',
                NULL, 'ACTIVE', 'test', 'test')
            """, AUTH_TEST_DEPARTMENT_ID, AUTH_TEST_HOSPITAL_ID, TENANT,
            "/" + TENANT + "/" + AUTH_TEST_HOSPITAL + "/" + AUTH_TEST_DEPARTMENT,
            AUTH_TEST_DEPARTMENT);
        roleAssignmentRepository.save(new UserRoleAssignment(
            null, TENANT, USER_ID, RoleCode.CLINICAL_USER.code(), "DEPARTMENT", AUTH_TEST_DEPARTMENT,
            "Y", Instant.now(), "test", Instant.now(), "test"
        ));

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(USERNAME, RAW_PASSWORD, TENANT))))
            .andExpect(status().isOk())
            .andReturn();

        String token = login.getResponse().getCookie("mk_access").getValue();
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        Map<?, ?> claims = objectMapper.readValue(payload, Map.class);

        assertThat(claims.get("tenant_id")).isEqualTo(TENANT);
        assertThat(claims.get("hospital_id")).isEqualTo(AUTH_TEST_HOSPITAL_ID);
        assertThat(claims.get("department_id")).isEqualTo(AUTH_TEST_DEPARTMENT_ID);
    }

    @AfterEach
    void cleanUp() {
        // I2: 清理凭证
        loginAttemptRepository.deleteAll();
        credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME)
            .ifPresent(c -> credentialRepository.delete(c));
        // I2: 对称清理角色分配
        List<UserRoleAssignment> assignments =
            roleAssignmentRepository.findActiveByTenantIdAndUserId(TENANT, USER_ID);
        roleAssignmentRepository.deleteAll(assignments);
        jdbcTemplate.update("""
            DELETE FROM org_unit
             WHERE tenant_id = ?
               AND code IN (?, ?)
            """, TENANT, AUTH_TEST_DEPARTMENT, AUTH_TEST_HOSPITAL);
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
        upsertConfig(LOGIN_MAX_FAILED_ATTEMPTS_KEY, "5");
        upsertConfig(LOGIN_LOCKOUT_SECONDS_KEY, "900");
        upsertConfig(LOGIN_RATE_LIMIT_ATTEMPTS_KEY, "10");
        upsertConfig(LOGIN_RATE_LIMIT_WINDOW_SECONDS_KEY, "60");
        upsertConfig(MFA_ENABLED_KEY, "false");
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
            .andExpect(cookie().path(XSRF_COOKIE, "/"))
            .andExpect(jsonPath("$.data.userId").value(USER_ID))
            .andExpect(jsonPath("$.data.tenantId").value(TENANT))
            .andExpect(jsonPath("$.data.mustChangePwd").value(false))
            .andExpect(jsonPath("$.data.mfaRequired").value(false));
    }

    @Test
    void enabledMfaRequiresVerifiedTotpBeforeBusinessSessionIsUsable() throws Exception {
        String secret = "JBSWY3DPEHPK3PXP";
        PlatformCredential active = credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME).orElseThrow();
        credentialRepository.save(new PlatformCredential(
            active.id(), active.credentialId(), active.tenantId(), active.userId(), active.username(),
            active.passwordHash(), active.status(), active.mustChangePwd(),
            mfaSecretCodec.encode(secret, "Recovery@2026"),
            active.createdAt(), active.createdBy(), Instant.now(), "test", active.traceId()));
        upsertConfig(MFA_ENABLED_KEY, "true");

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(USERNAME, RAW_PASSWORD, TENANT))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mfaRequired").value(true))
            .andExpect(jsonPath("$.data.mfaBound").value(true))
            .andReturn();

        Cookie restrictedCookie = login.getResponse().getCookie("mk_access");
        String payload = new String(
            Base64.getUrlDecoder().decode(restrictedCookie.getValue().split("\\.")[1]),
            StandardCharsets.UTF_8);
        Map<?, ?> claims = objectMapper.readValue(payload, Map.class);
        assertThat(claims.get("mfa_verified")).isEqualTo(false);

        mvc.perform(get("/api/v1/security/menu-permissions/visible").cookie(restrictedCookie))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-010"));

        Cookie xsrf = login.getResponse().getCookie(XSRF_COOKIE);
        var verified = mvc.perform(post("/api/v1/auth/mfa/verify")
                .cookie(restrictedCookie)
                .cookie(xsrf)
                .header(XSRF_HEADER, xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", currentTotpCode(secret)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verified").value(true))
            .andExpect(cookie().exists("mk_access"))
            .andReturn();

        Cookie verifiedCookie = verified.getResponse().getCookie("mk_access");
        mvc.perform(get("/api/v1/security/menu-permissions/visible").cookie(verifiedCookie))
            .andExpect(status().isOk());
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
            .andExpect(jsonPath("$.data.status").value("NOT_CONNECTED"))
            .andExpect(jsonPath("$.data.message").value("院方统一身份入口已开放，请由信息科在身份来源完成配置后启用。"));
    }

    @Test
    void delegatedCallbackReturnsNotConnectedWhenIdpIsUnconfigured() throws Exception {
        upsertAuthMode("DELEGATED");

        mvc.perform(post("/api/v1/auth/delegated/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-014"))
            .andExpect(jsonPath("$.detail").value("院方统一身份服务待配置，无法完成委托登录"));
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
    void cookieSessionRenewAcceptsMatchingXsrfWhenStalePathCookieExists() throws Exception {
        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        Cookie xsrf = login.getResponse().getCookie(XSRF_COOKIE);
        Cookie stalePathCookie = new Cookie(XSRF_COOKIE, "stale-path-token");
        stalePathCookie.setPath("/medkernel");

        mvc.perform(post("/api/v1/auth/session/renew")
                .cookie(login.getResponse().getCookie("mk_access"))
                .cookie(stalePathCookie)
                .cookie(xsrf)
                .header(XSRF_HEADER, xsrf.getValue()))
            .andExpect(status().isOk());
    }

    @Test
    void cookieSessionRenewRejectsNonCanonicalXsrfHeaderAlias() throws Exception {
        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        Cookie xsrf = login.getResponse().getCookie(XSRF_COOKIE);

        mvc.perform(post("/api/v1/auth/session/renew")
                .cookie(login.getResponse().getCookie("mk_access"))
                .cookie(xsrf)
                .header(XSRF_COOKIE, xsrf.getValue()))
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
            List.of(RoleCode.CLINICAL_USER.code()),
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
    void loginDisabledCredentialReturnsAccountUnavailableError() throws Exception {
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
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-002"));
    }

    @Test
    void loginWithMustChangePasswordCannotOpenBusinessApiBeforeChangingPassword() throws Exception {
        PlatformCredential active = credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME).orElseThrow();
        credentialRepository.save(new PlatformCredential(
            active.id(), active.credentialId(), active.tenantId(), active.userId(), active.username(),
            active.passwordHash(), active.status(), "Y", active.mfaSecret(),
            active.createdAt(), active.createdBy(), Instant.now(), "test", active.traceId()));

        var body = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, RAW_PASSWORD, TENANT));

        var login = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mustChangePwd").value(true))
            .andReturn();

        mvc.perform(get("/api/v1/security/menu-permissions/visible")
                .cookie(login.getResponse().getCookie("mk_access")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-015"));
    }

    @Test
    void repeatedWrongPasswordLocksCredentialUsingConfigCenterThreshold() throws Exception {
        upsertConfig(LOGIN_MAX_FAILED_ATTEMPTS_KEY, "2");
        upsertConfig(LOGIN_LOCKOUT_SECONDS_KEY, "600");
        upsertConfig(LOGIN_RATE_LIMIT_ATTEMPTS_KEY, "20");

        var wrongPassword = objectMapper.writeValueAsString(
            new LoginRequest(USERNAME, "WrongPassword123", TENANT));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongPassword))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-001"));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongPassword))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-002"));

        assertThat(credentialRepository.findByTenantIdAndUsername(TENANT, USERNAME).orElseThrow().status())
            .isEqualTo("LOCKED");
    }

    @Test
    void unknownUsernameFailuresAreRateLimitedWithoutCreatingCredential() throws Exception {
        upsertConfig(LOGIN_MAX_FAILED_ATTEMPTS_KEY, "5");
        upsertConfig(LOGIN_RATE_LIMIT_ATTEMPTS_KEY, "2");
        upsertConfig(LOGIN_RATE_LIMIT_WINDOW_SECONDS_KEY, "60");

        var body = objectMapper.writeValueAsString(
            new LoginRequest("nobody-xyz", "WrongPassword123", TENANT));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-001"));

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("ENG-API-008"));

        assertThat(credentialRepository.findByTenantIdAndUsername(TENANT, "nobody-xyz")).isEmpty();
    }

    private void upsertAuthMode(String value) {
        upsertConfig(AUTH_MODE_KEY, value);
    }

    private void upsertConfig(String key, String value) {
        int updated = jdbcTemplate.update("""
            UPDATE mk_config_item
               SET config_value = ?, source = 'YML_SEED', version = 1, updated_by = 'test'
             WHERE tenant_id = 'SYSTEM' AND config_key = ?
            """, value, key);
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO mk_config_item (
                config_id, tenant_id, config_key, config_value, value_type, display_name,
                risk_level, owner, description, source, protected_flag, active_flag,
                version, created_at, created_by, updated_at, updated_by
            ) VALUES (
                ?, 'SYSTEM', ?, ?, 'STRING', '认证安全测试配置',
                'HIGH', '安全组', '认证安全测试运行配置。', 'YML_SEED', 'Y', 'Y',
                1, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'
            )
            """, "cfg-test-" + key.replaceAll("[^a-zA-Z0-9]", "-"), key, value);
    }

    private static String currentTotpCode(String secret) {
        try {
            byte[] key = base32Decode(secret);
            long counter = Instant.now().getEpochSecond() / 30;
            byte[] message = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
            return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new AssertionError("无法生成测试 TOTP", exception);
        }
    }

    private static byte[] base32Decode(String text) {
        String normalized = text.replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            int value = current >= 'A' && current <= 'Z' ? current - 'A' : current - '2' + 26;
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
