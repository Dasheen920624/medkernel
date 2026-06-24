package com.medkernel.compliance.user;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.TenantUser;
import com.medkernel.engine.security.TenantUserRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 身份安全服务的统一用户管理契约测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComplianceUserControllerTest {

    @Autowired MockMvc mvc;
    @Autowired PlatformCredentialRepository credentials;
    @Autowired TenantUserRepository users;
    @Autowired UserRoleAssignmentRepository roleAssignments;

    @BeforeEach
    void prepare() {
        cleanUp();
    }

    @AfterEach
    void cleanUp() {
        roleAssignments.deleteAll();
        credentials.deleteAll();
        users.deleteAll();
    }

    @Test
    void listsTenantUsersWithServerPaginationAndRoleSummary() throws Exception {
        saveCredential("t-1", "managed-701", "managed.one");
        saveCredential("t-1", "managed-702", "managed.two");
        saveCredential("t-2", "managed-hidden", "hidden.one");
        saveRole("t-1", "managed-701", "clinical-user");

        mvc.perform(get("/api/v1/compliance/users")
                .param("page", "1")
                .param("size", "1")
                .with(admin("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items", hasSize(1)))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.size").value(1))
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.items[0].tenantId").doesNotExist())
            .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist());
    }

    @Test
    void createsAndListsExternalIdentityUserWithoutPlatformCredential() throws Exception {
        mvc.perform(post("/api/v1/compliance/users")
                .with(admin("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "credentialManaged": false,
                      "userId": "delegated-701",
                      "displayName": "委托身份医生",
                      "roleCode": "clinical-user"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.userId").value("delegated-701"))
            .andExpect(jsonPath("$.data.user.displayName").value("委托身份医生"))
            .andExpect(jsonPath("$.data.user.credentialManaged").value(false))
            .andExpect(jsonPath("$.data.user.username").doesNotExist())
            .andExpect(jsonPath("$.data.tempPassword").doesNotExist());

        mvc.perform(get("/api/v1/compliance/users")
                .with(admin("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].userId").value("delegated-701"))
            .andExpect(jsonPath("$.data.items[0].credentialManaged").value(false));

        org.assertj.core.api.Assertions.assertThat(
            credentials.findByTenantIdAndUserId("t-1", "delegated-701")).isEmpty();
    }

    @Test
    void createsManagedPlatformUserWithSupportedLongTraceId() throws Exception {
        String traceId = "trace-" + "x".repeat(80);

        mvc.perform(post("/api/v1/compliance/users")
                .header("X-Trace-Id", traceId)
                .with(systemSuperAdmin("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "credentialManaged": true,
                      "userId": "engine-operator-user",
                      "displayName": "医疗引擎运营员",
                      "username": "engine-operator-user",
                      "roleCode": "engine-operator"
                    }
                    """))
            .andExpect(status().isOk());

        PlatformCredential credential = credentials
            .findByTenantIdAndUserId("t-1", "engine-operator-user")
            .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(credential.traceId()).isEqualTo(traceId);
    }

    @Test
    void externalIdentityUserCannotUsePasswordOperations() throws Exception {
        mvc.perform(post("/api/v1/compliance/users")
                .with(admin("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "credentialManaged": false,
                      "userId": "delegated-702",
                      "displayName": "委托身份护士"
                    }
                    """))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/compliance/users/{userId}:reset-password", "delegated-702")
                .with(admin("t-1")))
            .andExpect(status().isConflict());
    }

    @Test
    void returnsRealAssignedRolesAndEffectivePermissionsForUserDetail() throws Exception {
        saveCredential("t-1", "managed-703", "managed.three");
        saveRole("t-1", "managed-703", "clinical-user");

        mvc.perform(get("/api/v1/compliance/users/{userId}", "managed-703")
                .with(admin("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("managed-703"))
            .andExpect(jsonPath("$.data.roles[0].code").value("clinical-user"))
            .andExpect(jsonPath("$.data.effectivePermissions[*].code", hasItem("context.read")));
    }

    @Test
    void platformAdministratorCanMaintainUsersDuringOnboarding() throws Exception {
        saveCredential("t-1", "managed-710", "managed.ten");

        mvc.perform(get("/api/v1/compliance/users")
                .with(platformAdministrator("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(post("/api/v1/compliance/users")
                .with(platformAdministrator("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "credentialManaged": false,
                      "userId": "implementation-created",
                      "displayName": "实施创建人员",
                      "roleCode": "clinical-user"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user.userId").value("implementation-created"));
    }

    @Test
    void rejectsRoleAssignmentForUnknownTenantUser() throws Exception {
        mvc.perform(post("/api/v1/compliance/users/{userId}/roles", "missing-user")
                .with(admin("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "clinical-user",
                      "scopeLevel": "TENANT",
                      "scopeCode": "t-1"
                    }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void cannotReadAnotherTenantsUser() throws Exception {
        saveCredential("t-2", "managed-704", "managed.four");

        mvc.perform(get("/api/v1/compliance/users/{userId}", "managed-704")
                .with(admin("t-1")))
            .andExpect(status().isNotFound());
    }

    @Test
    void clinicalUserCannotGrantPlatformAdministratorRole() throws Exception {
        saveCredential("t-1", "managed-705", "managed.five");

        mvc.perform(post("/api/v1/compliance/users/{userId}/roles", "managed-705")
                .with(clinicalUser("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "platform-admin",
                      "scopeLevel": "TENANT",
                      "scopeCode": "t-1"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void assignsAndRemovesRoleWithoutDeletingAuditHistory() throws Exception {
        saveCredential("t-1", "managed-706", "managed.six");

        mvc.perform(post("/api/v1/compliance/users/{userId}/roles", "managed-706")
                .with(admin("t-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "clinical-user",
                      "scopeLevel": "TENANT",
                      "scopeCode": "t-1"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0].code").value("clinical-user"))
            .andExpect(jsonPath("$.data.roles[0].scopeName").value("平台治理空间"));

        mvc.perform(delete("/api/v1/compliance/users/{userId}/roles/{roleCode}",
                "managed-706", "clinical-user")
                .param("scopeLevel", "TENANT")
                .param("scopeCode", "t-1")
                .with(admin("t-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles", hasSize(0)));

        UserRoleAssignment history = roleAssignments
            .findByTenantIdAndUserIdAndRoleCodeAndScopeLevelAndScopeCode(
                "t-1", "managed-706", "clinical-user", "TENANT", "t-1")
            .orElseThrow();
        org.assertj.core.api.Assertions.assertThat(history.activeFlag()).isEqualTo("N");
    }

    @Test
    void cannotRemoveSystemSuperAdministratorRole() throws Exception {
        saveCredential("t-1", "managed-root", "managed.root");
        saveRole("t-1", "managed-root", "system-superadmin");

        mvc.perform(delete("/api/v1/compliance/users/{userId}/roles/{roleCode}",
                "managed-root", "system-superadmin")
                .param("scopeLevel", "TENANT")
                .param("scopeCode", "t-1")
                .with(admin("t-1")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(String tenantId) {
        return jwt().jwt(token -> token.subject("admin-1").claim("tenant_id", tenantId))
            .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor systemSuperAdmin(
            String tenantId) {
        return jwt().jwt(token -> token.subject("system-root").claim("tenant_id", tenantId))
            .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_SUPERADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdministrator(
            String tenantId) {
        return jwt().jwt(token -> token.subject("platform-admin-2").claim("tenant_id", tenantId))
            .authorities(
                new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"),
                new SimpleGrantedAuthority("org.read"),
                new SimpleGrantedAuthority("org.write"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor clinicalUser(
            String tenantId) {
        return jwt().jwt(token -> token.subject("clinical-user-2").claim("tenant_id", tenantId))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"));
    }

    private void saveCredential(String tenantId, String userId, String username) {
        Instant now = Instant.now();
        users.save(new TenantUser(
            null,
            tenantId,
            userId,
            username,
            "ACTIVE",
            1L,
            now,
            "test",
            now,
            "test",
            "trace-test"));
        credentials.save(new PlatformCredential(
            null,
            "cred-" + userId,
            tenantId,
            userId,
            username,
            "test-hash",
            "ACTIVE",
            "N",
            null,
            now,
            "test",
            now,
            "test",
            "trace-test"));
    }

    private void saveRole(String tenantId, String userId, String roleCode) {
        Instant now = Instant.now();
        roleAssignments.save(new UserRoleAssignment(
            null, tenantId, userId, roleCode, "TENANT", tenantId, "Y",
            now, "test", now, "test"));
    }
}
