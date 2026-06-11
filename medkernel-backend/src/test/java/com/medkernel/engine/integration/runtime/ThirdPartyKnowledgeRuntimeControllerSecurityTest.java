package com.medkernel.engine.integration.runtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ThirdPartyKnowledgeRuntimeControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean ThirdPartyKnowledgeRuntimeService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void anonymousCannotResolveEffectivePackage() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/knowledge-runtime/effective-package")
                .param("packageCode", "PKG.AF")
                .param("packageVersion", "2026.06")
                .param("targetOrgUnitId", "dept-1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_INTEGRATION_OPERATOR")
    void authenticatedRequestStillRequiresTenantScope() throws Exception {
        mvc.perform(get("/api/v1/engine/integration/knowledge-runtime/effective-package")
                .param("packageCode", "PKG.AF")
                .param("packageVersion", "2026.06")
                .param("targetOrgUnitId", "dept-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    void anonymousCannotWriteContextOrManageOverridesOrDistributePackages() throws Exception {
        mvc.perform(post("/api/v1/engine/integration/knowledge-runtime/context-snapshots"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/engine/integration/knowledge-runtime/overrides"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/engine/integration/knowledge-runtime/packages/pkg-1:distribute"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void everyWriteEndpointRequiresPlatformIdempotencyKey() {
        assertThat(Arrays.stream(ThirdPartyKnowledgeRuntimeController.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(PostMapping.class)))
            .allSatisfy(method -> assertThat(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null)
                .anyMatch(annotation ->
                    annotation.required()
                        && ("Idempotency-Key".equals(annotation.name())
                            || "Idempotency-Key".equals(annotation.value()))))
                .as(method.getName() + " 必须强制 Idempotency-Key")
                .isTrue());
    }
}
