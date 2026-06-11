package com.medkernel.engine.experience;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class SavedViewControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SavedViewService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void listWithoutAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/experience/saved-views")
                .param("pageKey", "terminology.mapping"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listWithTenant_shouldReturnCurrentUserViews() throws Exception {
        when(service.list(eq("terminology.mapping"))).thenReturn(List.of(response("sv-01")));

        mockMvc.perform(get("/api/v1/experience/saved-views")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .param("pageKey", "terminology.mapping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].savedViewId").value("sv-01"));
    }

    @Test
    void upsertWithTenant_shouldReturnSavedView() throws Exception {
        when(service.upsert(any(SavedViewRequest.class))).thenReturn(response("sv-02"));

        mockMvc.perform(put("/api/v1/experience/saved-views")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pageKey": "terminology.mapping",
                      "viewName": "默认视图",
                      "definitionJson": "{\\"filters\\":[]}",
                      "defaultView": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.savedViewId").value("sv-02"));
    }

    private static SavedViewResponse response(String id) {
        return new SavedViewResponse(
            id,
            "terminology.mapping",
            "默认视图",
            "{\"filters\":[]}",
            true,
            1,
            Instant.parse("2026-06-01T00:00:00Z"),
            "doctor-1"
        );
    }
}
