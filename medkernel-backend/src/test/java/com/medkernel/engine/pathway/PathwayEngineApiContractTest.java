package com.medkernel.engine.pathway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * API-06 路径引擎客户面合同测试。
 *
 * <p>只锁定对外路径、统一入参和旧入口清理；路径图推进细节由服务测试覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class PathwayEngineApiContractTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PathwayEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void pathwayPackageNotFoundUsesUnifiedKnowledgePackageWording() {
        assertThat(ErrorCode.ENG_PATHWAY_007.defaultMessage()).isEqualTo("路径知识包不存在");
    }

    @Test
    void oldSpecialtyPackageRouteIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/pathway/specialty-packages")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "packageCode": "PKG.COPD",
                      "diseaseCode": "COPD",
                      "name": "慢阻肺专病包",
                      "packageVersion": "1.0.0",
                      "sourceRef": "专病路径专家共识 2026"
                    }
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void advanceUsesPatientPathwayResourceRouteAndRequiresUnifiedContext() throws Exception {
        mvc.perform(post("/api/v1/engine/pathway/patient-pathways/pp-1/advance")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventType": "COMPLETE",
                      "eventId": "evt-1"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-API-002"));
    }

    @Test
    void varianceAndClockEndpointsUseCustomerRouteAndTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/variances")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/api/v1/engine/pathway/patient-pathways/pp-1/clocks")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void templateImpactEndpointUsesCustomerRouteAndTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/pathway/pathway-templates/pt-1/impact")
                .with(readJwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void oldPluralRootAndDiagnoseRouteAreRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/pathways/packages")
                .with(writeJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/engine/pathways/patients/pp-1/diagnose")
                .with(readJwt()))
            .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor readJwt() {
        return jwt().jwt(token -> token
                .subject("api06-doctor")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("clinical-decision-user")))
            .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"));
    }

    private static RequestPostProcessor writeJwt() {
        return jwt().jwt(token -> token
                .subject("api06-specialist")
                .claim("tenant_id", "t-1")
                .claim("roles", List.of("knowledge-governor")))
            .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"));
    }
}
