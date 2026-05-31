package com.medkernel.engine.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @AfterEach
    void clearAssignmentsAndOverrides() {
        jdbcTemplate.update("DELETE FROM user_role_assignment");
        jdbcTemplate.update("DELETE FROM role_permission");
    }

    @Test
    void currentUserReceivesRolesPermissionsMenusAndDataScope() throws Exception {
        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "d-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority(RoleCode.DOCTOR.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value("doctor-1"))
            .andExpect(jsonPath("$.data.roles[*].code", hasItem(RoleCode.DOCTOR.code())))
            .andExpect(jsonPath("$.data.permissions[*].code", hasItem(PermissionCode.RECOMMENDATION_READ.code())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.ACTION.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.MENU.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.DATA.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.ASSET.name())))
            .andExpect(jsonPath("$.data.permissions[*].dimension", hasItem(PermissionDimension.ENVIRONMENT.name())))
            .andExpect(jsonPath("$.data.permissions[*].target", hasItem("recommendation")))
            .andExpect(jsonPath("$.data.permissions[*].code", not(hasItem(PermissionCode.RULE_PUBLISH.code()))))
            .andExpect(jsonPath("$.data.menuKeys", hasItem("clinical-run")))
            .andExpect(jsonPath("$.data.dataScope.tenantId").value("t-1"))
            .andExpect(jsonPath("$.data.dataScope.hospitalId").value("h-1"))
            .andExpect(jsonPath("$.data.dataScope.departmentId").value("d-1"));
    }

    @Test
    void currentUserAppliesScopedAssignmentAndExplicitDenyFromDatabase() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO user_role_assignment
                (tenant_id, user_id, role_code, scope_level, scope_code)
            VALUES (?, ?, ?, ?, ?)
            """, "t-1", "doctor-1", RoleCode.QA_MANAGER.code(), "DEPARTMENT", "d-1");
        jdbcTemplate.update("""
            INSERT INTO role_permission
                (tenant_id, role_code, permission_code, effect)
            VALUES (?, ?, ?, ?)
            """, "t-1", RoleCode.DOCTOR.code(), PermissionCode.RECOMMENDATION_ACCEPT.code(), "DENY");

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1")
                    .claim("department_id", "d-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority(RoleCode.DOCTOR.authority()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].code", hasItem(RoleCode.QA_MANAGER.code())))
            .andExpect(jsonPath("$.data.permissions[*].code", hasItem(PermissionCode.EVALUATION_PUBLISH.code())))
            .andExpect(jsonPath("$.data.permissions[*].code",
                not(hasItem(PermissionCode.RECOMMENDATION_ACCEPT.code()))));
    }

    @Test
    void currentUserEndpointRequiresTenantContext() throws Exception {
        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority(RoleCode.DOCTOR.authority()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
