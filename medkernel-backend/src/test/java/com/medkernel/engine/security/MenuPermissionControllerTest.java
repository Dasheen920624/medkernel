package com.medkernel.engine.security;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

    @Test
    void catalogReturnsCompleteNavigationEntriesWithPlacementAndDefaultRoleMatrix() throws Exception {
        mvc.perform(get("/api/v1/security/menu-permissions/catalog")
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menus", hasSize(34)))
            .andExpect(jsonPath("$.data.menus[*].menuKey")
                .value(org.hamcrest.Matchers.not(hasItem("model-evaluation-review"))))
            .andExpect(jsonPath("$.data.menus[*].menuKey", hasItem("terminology-mapping")))
            .andExpect(jsonPath("$.data.menus[*].placement", hasItem("PRIMARY")))
            .andExpect(jsonPath("$.data.menus[*].permissionCode")
                .value(org.hamcrest.Matchers.not(hasItem("menu.rule-validate"))))
            .andExpect(jsonPath("$.data.defaultRoleMenuKeys.clinical-user")
                .value(org.hamcrest.Matchers.not(hasItem("rule-validate"))));
    }

    @Test
    void visibleMenuTreeUsesCurrentEffectiveSecondLevelPermissions() throws Exception {
        mvc.perform(get("/api/v1/security/menu-permissions/visible")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sections[*].sectionKey", hasItem("clinical-collaboration")))
            .andExpect(jsonPath("$.data.sections[*].items[*].menuKey", hasItem("patient-pathways")))
            .andExpect(jsonPath("$.data.sections[*].items[*].menuKey")
                .value(org.hamcrest.Matchers.not(hasItem("rule-validate"))))
            .andExpect(jsonPath("$.data.headerItems[*].menuKey", hasItem("notifications")));
    }

    @Test
    void fixedRoleBundleCannotBeOverriddenPerTenant() throws Exception {
        var request = Map.of(
            "roleCode", RoleCode.CLINICAL_USER.code(),
            "menuKey", "admin-audit",
            "effect", "ALLOW");

        mvc.perform(patch("/api/v1/security/menu-permissions/overrides")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(token -> token
                    .subject("admin-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/security/me")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "t-1"))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuKeys")
                .value(org.hamcrest.Matchers.not(hasItem("admin-audit"))));
    }
}
