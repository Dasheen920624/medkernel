package com.medkernel.engine.security.auth;

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

import com.medkernel.engine.org.OrgUnitRepository;
import com.medkernel.engine.security.PlatformCredential;
import com.medkernel.engine.security.PlatformCredentialRepository;
import com.medkernel.engine.security.UserRoleAssignmentRepository;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台租户开通端到端行为测试（test profile）：开通→建组织/管理员/角色→新租户管理员可登录。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantProvisioningControllerTest {

    private static final String NEW_TENANT = "t-hosp2";

    @Autowired MockMvc mvc;
    @Autowired OrgUnitRepository orgUnits;
    @Autowired PlatformCredentialRepository credentials;
    @Autowired UserRoleAssignmentRepository roleAssignments;

    @BeforeEach
    void seedPlatformAdminMfa() {
        if (credentials.findByTenantIdAndUserId("t-1", "platform-admin-1").isEmpty()) {
            java.time.Instant now = java.time.Instant.now();
            credentials.save(new PlatformCredential(
                null, "cred-platform-admin-1", "t-1", "platform-admin-1", "platform-admin",
                "$2a$10$hash", "ACTIVE", "N", "sha256-mfa-recovery-code",
                now, "test", now, "test", "trace-test"));
        }
    }

    @AfterEach
    void cleanUp() {
        orgUnits.findByTenantIdOrderByLevelAscCodeAsc(NEW_TENANT).forEach(orgUnits::delete);
        credentials.deleteAll();
        roleAssignments.deleteAll();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdmin() {
        return jwt().jwt(t -> t.subject("platform-admin-1").claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor platformAdminWithoutMfa() {
        return jwt().jwt(t -> t.subject("platform-admin-no-mfa").claim("tenant_id", "t-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    @Test
    void provisionTenant_createsTenantWithAdmin_andAdminCanLogin() throws Exception {
        // 开通：指定初始密码以便随后登录验证
        mvc.perform(post("/api/v1/admin/tenants").with(platformAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"t-hosp2\",\"tenantName\":\"测试医院2\","
                    + "\"adminUsername\":\"hosp2admin\",\"adminInitialPassword\":\"Init@2026Pass!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tenantId").value("t-hosp2"))
            .andExpect(jsonPath("$.data.adminUsername").value("hosp2admin"));

        // 列表含新租户
        mvc.perform(get("/api/v1/admin/tenants").with(platformAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.tenantId=='t-hosp2')].name").value(
                org.hamcrest.Matchers.hasItem("测试医院2")));

        // 新租户管理员可在 t-hosp2 登录（验证跨租户链路 + 角色）
        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"hosp2admin\",\"password\":\"Init@2026Pass!\",\"tenantId\":\"t-hosp2\"}"))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly("mk_access", true))
            .andExpect(jsonPath("$.data.tenantId").value("t-hosp2"))
            .andExpect(jsonPath("$.data.roles[0]").value("hospital-admin"))
            .andExpect(jsonPath("$.data.mustChangePwd").value(true));
    }

    @Test
    void provisionTenant_generatesTempPasswordWhenOmitted() throws Exception {
        mvc.perform(post("/api/v1/admin/tenants").with(platformAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"t-hosp2\",\"tenantName\":\"测试医院2\",\"adminUsername\":\"hosp2admin\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.tempPassword", not(emptyOrNullString())));
    }

    @Test
    void provisionTenant_requiresBoundMfaForHighRiskAction() throws Exception {
        mvc.perform(post("/api/v1/admin/tenants").with(platformAdminWithoutMfa())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tenantId\":\"t-hosp2\",\"tenantName\":\"测试医院2\",\"adminUsername\":\"hosp2admin\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ENG-AUTH-010"));
    }

    @Test
    void provisionTenant_duplicate_conflict() throws Exception {
        String body = "{\"tenantId\":\"t-hosp2\",\"tenantName\":\"测试医院2\",\"adminUsername\":\"hosp2admin\"}";
        mvc.perform(post("/api/v1/admin/tenants").with(platformAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/tenants").with(platformAdmin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ENG-TENANT-001"));
    }
}
