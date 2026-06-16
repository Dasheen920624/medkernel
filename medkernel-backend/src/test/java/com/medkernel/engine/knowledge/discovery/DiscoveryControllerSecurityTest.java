package com.medkernel.engine.knowledge.discovery;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;

/**
 * 可信来源探索控制器权限安全测试（LLM-06）。
 *
 * <p>探索（产候选草稿）走 {@code knowledge.write}；运行台账走 {@code knowledge.read}。
 * 临床决策用户无 write 不得探索（403）；GUEST 无权（403）；知识治理员具 write/read 可达（200）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DiscoveryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscoveryOrchestrationService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String BODY = "{\"query\":\"阿司匹林\"}";

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void clinicalUserCannotExplore() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/discovery:explore")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotExplore() throws Exception {
        mockMvc.perform(post("/api/v1/engine/knowledge/discovery:explore")
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanExplore() throws Exception {
        when(service.explore(any())).thenReturn(new DiscoveryResponse("run-x", Instant.now(),
            DiscoveryRunStatus.EMPTY, false, 0, 0, "0".repeat(64), List.of(), List.of()));

        mockMvc.perform(post("/api/v1/engine/knowledge/discovery:explore")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR")))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestCannotListRuns() throws Exception {
        mockMvc.perform(get("/api/v1/engine/knowledge/discovery/runs"))
            .andExpect(status().isForbidden());
    }

    @Test
    void knowledgeGovernorCanListRuns() throws Exception {
        when(service.listRuns(anyInt(), anyInt())).thenReturn(PageResponse.empty(PageRequest.defaults()));

        mockMvc.perform(get("/api/v1/engine/knowledge/discovery/runs")
                .with(jwt().jwt(token -> token.subject("u").claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("knowledge-governor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_KNOWLEDGE_GOVERNOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isArray())
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.total").value(0));
    }
}
