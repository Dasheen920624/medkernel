package com.medkernel.engine.domainfacade;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.medkernel.shared.context.RequestContext;

/**
 * 领域门面目录控制器权限测试：目录读侧要求 {@code knowledge.read}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DomainFacadeControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DomainFacadeCatalogService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void guestCannotReadDomainFacadeCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/engine/domain-facades")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("guest")))
                    .authorities(new SimpleGrantedAuthority("ROLE_GUEST"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanReadDomainFacadeCatalog() throws Exception {
        when(service.listDefinitions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/engine/domain-facades")
                .with(jwt().jwt(token -> token
                    .subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"))))
                .andExpect(status().isOk());
    }
}
