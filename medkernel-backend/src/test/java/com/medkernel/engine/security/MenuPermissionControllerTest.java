package com.medkernel.engine.security;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuPermissionControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RolePermissionOverrideRepository rolePermissionRepository;

    @SpyBean
    AuditRecorder auditRecorder;

    @AfterEach
    void clearOverrides() {
        rolePermissionRepository.deleteAll();
        clearInvocations(auditRecorder);
    }

    @Test
    void catalogReturnsThirtyTwoMenusAndDefaultRoleMatrix() throws Exception {
        mvc.perform(get("/api/v1/security/menu-permissions/catalog")
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menus", hasSize(32)))
            .andExpect(jsonPath("$.data.menus[*].menuKey", hasItem("terminology-mapping")))
            .andExpect(jsonPath("$.data.menus[*].permissionCode", hasItem("menu.rule-validate")))
            .andExpect(jsonPath("$.data.defaultRoleMenuKeys.doctor", hasItem("rule-validate")))
            .andExpect(jsonPath("$.data.defaultRoleMenuKeys.doctor").value(org.hamcrest.Matchers.not(hasItem("clinical-run"))));
    }

    @Test
    void visibleMenuTreeUsesCurrentEffectiveSecondLevelPermissions() throws Exception {
        mvc.perform(get("/api/v1/security/menu-permissions/visible")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sections[*].sectionKey", hasItem("clinical-run")))
            .andExpect(jsonPath("$.data.sections[*].items[*].menuKey", hasItem("rule-validate")))
            .andExpect(jsonPath("$.data.sections[*].items[*].menuKey").value(org.hamcrest.Matchers.not(hasItem("pilot-setup"))));
    }

    @Test
    void tenantOverrideCanGrantSecondLevelMenuToRole() throws Exception {
        var request = Map.of(
            "roleCode", RoleCode.DOCTOR.code(),
            "menuKey", "admin-audit",
            "effect", PermissionEffect.ALLOW.name());

        mvc.perform(patch("/api/v1/security/menu-permissions/overrides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCode").value(RoleCode.DOCTOR.code()))
            .andExpect(jsonPath("$.data.menuKey").value("admin-audit"))
            .andExpect(jsonPath("$.data.permissionCode").value("menu.admin-audit"))
            .andExpect(jsonPath("$.data.effect").value("ALLOW"));

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuKeys", hasItem("admin-audit")));

        ArgumentCaptor<AuditRecordCommand> audit = ArgumentCaptor.forClass(AuditRecordCommand.class);
        verify(auditRecorder).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(AuditAction.PERMISSION_CHANGE);
        assertThat(audit.getValue().targetType()).isEqualTo("role_permission");
        assertThat(audit.getValue().targetId()).isEqualTo("t-1:doctor:menu.admin-audit");
        assertThat(audit.getValue().before()).isNull();
        assertThat(audit.getValue().after())
            .hasFieldOrPropertyWithValue("tenantId", "t-1")
            .hasFieldOrPropertyWithValue("roleCode", "doctor")
            .hasFieldOrPropertyWithValue("menuKey", "admin-audit")
            .hasFieldOrPropertyWithValue("permissionCode", "menu.admin-audit")
            .hasFieldOrPropertyWithValue("effect", PermissionEffect.ALLOW);
    }

    @Test
    void systemSuperAdminMenuOverrideIsRejectedAsImmutable() throws Exception {
        var request = Map.of(
            "roleCode", "system-superadmin",
            "menuKey", "security-baseline",
            "effect", PermissionEffect.DENY.name());

        mvc.perform(patch("/api/v1/security/menu-permissions/overrides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SUPERADMIN_IMMUTABLE"));
    }
}
