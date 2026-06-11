package com.medkernel.engine.terminology;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TerminologyControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    TerminologyService terminologyService;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_IMPLEMENTATION_OPERATOR")
    void implementationEngineerCanReadButDataScopeRejectsMissingTenant() throws Exception {
        when(terminologyService.pageLocalTerms(any(PageRequest.class), any(LocalTermFilter.class)))
            .thenReturn(PageResponse.empty(PageRequest.defaults()));

        mvc.perform(get("/api/v1/engine/terminology/terms/local"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestRoleIsForbiddenFromReadingTerminology() throws Exception {
        mvc.perform(get("/api/v1/engine/terminology/terms/local"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_KNOWLEDGE_GOVERNOR")
    void specialistCanReachCandidateConfirmationButStillNeedsTenant() throws Exception {
        mvc.perform(post("/api/v1/engine/terminology/mappings/10/confirm")
                .contentType("application/json")
                .content("{\"reviewNote\":\"专家确认\",\"highRiskAcknowledged\":true,\"highRiskReason\":\"已逐条核对\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void oldMappingPackagePublishRouteIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/terminology/mapping-packages/30/publish")
                .contentType("application/json")
                .content("{\"releaseMode\":\"FULL\",\"reason\":\"全量发布\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void oldMappingPackageRollbackRouteIsRemoved() throws Exception {
        mvc.perform(post("/api/v1/engine/terminology/mapping-packages/30/rollback")
                .contentType("application/json")
                .content("{\"targetPackageId\":29,\"reason\":\"发现映射异常\"}"))
            .andExpect(status().isNotFound());
    }
}
