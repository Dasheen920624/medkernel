package com.medkernel.engine.security.auth;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignment;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台成员账号管理 + 自助改密的端到端行为测试（test profile，MockMvc + 模拟 JWT）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CredentialAdminControllerTest {

    @Autowired MockMvc mvc;
    @Autowired PlatformCredentialRepository credentials;
    @Autowired UserRoleAssignmentRepository roleAssignments;
    @Autowired LoginAttemptStateRepository loginAttempts;

    @AfterEach
    void cleanUp() {
        loginAttempts.deleteAll();
        credentials.deleteAll();
        roleAssignments.deleteAll();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(t -> t.subject("admin-1").claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor member(String userId) {
        return jwt().jwt(t -> t.subject(userId).claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
    }

    @Test
    void createMember_generatesTempPassword_andListsWithoutHash() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"roleCode\":\"doctor\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("drwang"))
            .andExpect(jsonPath("$.data.userId").value("drwang"))
            .andExpect(jsonPath("$.data.tempPassword", not(emptyOrNullString())));

        mvc.perform(get("/api/v1/admin/credentials").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].username").value("drwang"))
            .andExpect(jsonPath("$.data[0].mustChangePwd").value(true))
            .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
    }

    @Test
    void createMember_duplicateUsername_conflict() throws Exception {
        String body = "{\"username\":\"drwang\",\"roleCode\":\"doctor\"}";
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-006"));
    }

    @Test
    void resetPassword_returnsNewTempPassword() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/admin/credentials/{userId}/reset-password", "drwang").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tempPassword", not(emptyOrNullString())));
    }

    @Test
    void setStatus_disablesAccount() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/credentials/{userId}/status", "drwang").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/credentials").with(admin()))
            .andExpect(jsonPath("$.data[0].status").value("DISABLED"));
    }

    @Test
    void setStatusRejectsMissingStatusBeforePersistence() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/admin/credentials/{userId}/status", "drwang").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));

        assertThat(credentials.findByTenantIdAndUsername("t-1", "drwang").orElseThrow().status())
            .isEqualTo("ACTIVE");
    }

    @Test
    void manualLockedStatusIsNotAutoUnlockedByExpiredLoginAttemptState() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());

        var credential = credentials.findByTenantIdAndUsername("t-1", "drwang").orElseThrow();
        Instant now = Instant.now();
        loginAttempts.save(new LoginAttemptState(
            null,
            "lat-expired-manual-lock",
            "t-1",
            "drwang",
            credential.credentialId(),
            5,
            now.minusSeconds(1),
            now.minusSeconds(60),
            now.minusSeconds(60),
            "test",
            now.minusSeconds(60),
            "test",
            "trace-manual-lock"));

        mvc.perform(patch("/api/v1/admin/credentials/{userId}/status", "drwang").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"t-1\",\"username\":\"drwang\",\"password\":\"Init@2026Pass!\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-002"));

        assertThat(credentials.findByTenantIdAndUsername("t-1", "drwang").orElseThrow().status())
            .isEqualTo("LOCKED");
    }

    @Test
    void createMemberCannotAssignSystemSuperAdminRole() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ops\",\"roleCode\":\"system-superadmin\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }

    @Test
    void setStatusCannotDisableSystemSuperAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"root-admin\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());
        Instant now = Instant.now();
        roleAssignments.save(new UserRoleAssignment(
            null, "t-1", "root-admin", "system-superadmin", "TENANT", "t-1",
            "Y", now, "test", now, "test"));

        mvc.perform(patch("/api/v1/admin/credentials/{userId}/status", "root-admin").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }

    @Test
    void resetPasswordCannotEditSystemSuperAdmin() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"root-admin\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());
        Instant now = Instant.now();
        roleAssignments.save(new UserRoleAssignment(
            null, "t-1", "root-admin", "system-superadmin", "TENANT", "t-1",
            "Y", now, "test", now, "test"));

        mvc.perform(post("/api/v1/admin/credentials/{userId}/reset-password", "root-admin").with(admin()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }

    @Test
    void changePassword_wrongOldRejected_thenSuccessClearsMustChange() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/change-password").with(member("drwang"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"WRONG\",\"newPassword\":\"NewPwd@2026!\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-004"));

        mvc.perform(post("/api/v1/auth/change-password").with(member("drwang"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"Init@2026Pass!\",\"newPassword\":\"NewPwd@2026!\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/credentials").with(admin()))
            .andExpect(jsonPath("$.data[0].mustChangePwd").value(false));
    }

    @Test
    void changePassword_rejectsWeakNewPasswordByRuntimePolicy() throws Exception {
        mvc.perform(post("/api/v1/admin/credentials").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"drwang\",\"initialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk());

        mvc.perform(post("/api/v1/auth/change-password").with(member("drwang"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"Init@2026Pass!\",\"newPassword\":\"weakpassword123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PWD_POLICY_VIOLATION"));

        mvc.perform(get("/api/v1/admin/credentials").with(admin()))
            .andExpect(jsonPath("$.data[0].mustChangePwd").value(true));
    }
}
