package com.medkernel.engine.tenant;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.medkernel.shared.context.RequestContext;

/**
 * SVC-PILOT-01 服务包对外 HTTP 契约测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TenantEngineControllerContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TenantPilotService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void brandingAndSuccessPlanUseEngineTenantRouteAndTenantReadPermission() throws Exception {
        when(service.getBranding("tenant-A")).thenReturn(new Branding(
            1L,
            "tenant-A",
            "未配置医院名称",
            null,
            "var(--mk-theme-navy)",
            false,
            null,
            null, null, null, null
        ));
        when(service.getSuccessPlan("tenant-A")).thenReturn(new SuccessPlan(
            1L,
            "tenant-A",
            "PREPARATION",
            0,
            "",
            "",
            null, null, null, null
        ));

        mvc.perform(get("/api/v1/engine/tenant/branding")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.hospitalName").value("未配置医院名称"))
            .andExpect(jsonPath("$.data.themeColor").value("var(--mk-theme-navy)"));

        mvc.perform(get("/api/v1/engine/tenant/success-plan")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.currentStage").value("PREPARATION"))
            .andExpect(jsonPath("$.data.healthScore").value(0));
    }

    @Test
    void implementationStepsUseEngineTenantRouteAndTenantReadPermission() throws Exception {
        when(service.getImplementationSteps("tenant-A")).thenReturn(List.of(
            new ImplementationStep(
                "ORGANIZATION",
                "组织树",
                "DONE",
                List.of(),
                "/tenant/onboarding",
                "已建立租户根与医院节点"
            )
        ));

        mvc.perform(get("/api/v1/engine/tenant/implementation-steps")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].key").value("ORGANIZATION"))
            .andExpect(jsonPath("$.data[0].status").value("DONE"))
            .andExpect(jsonPath("$.data[0].targetPath").value("/tenant/onboarding"));
    }

    @Test
    void guestCannotReadTenantEnginePackage() throws Exception {
        mvc.perform(get("/api/v1/engine/tenant/onboarding-readiness")
                .with(jwt().jwt(token -> token.subject("guest").claim("tenant_id", "tenant-A"))
                    .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void legacyPlatformTenantRoutesAreNotMounted() throws Exception {
        mvc.perform(get("/api/v1/platform/branding")
                .with(readJwt()))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/platform/success/lifecycle")
                .with(readJwt()))
            .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("implementer")
                .claim("tenant_id", "tenant-A")
                .claim("roles", List.of("hospital-admin")))
            .authorities(new SimpleGrantedAuthority("ROLE_HOSPITAL_ADMIN"));
    }

}
