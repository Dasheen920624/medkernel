package com.medkernel.engine.experience;

import static org.mockito.ArgumentMatchers.any;
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
class ThemePreferenceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserPreferenceService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void getThemePreferenceWithoutAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/experience/theme-preference"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getThemePreferenceWithTenant_shouldReturnCurrentUserPreference() throws Exception {
        when(service.getThemePreference()).thenReturn(response("dark", 2));

        mockMvc.perform(get("/api/v1/experience/theme-preference")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("dark"))
            .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void saveThemePreferenceWithTenant_shouldReturnUpdatedPreference() throws Exception {
        when(service.saveThemePreference(any(ThemePreferenceRequest.class))).thenReturn(response("eye", 3));

        mockMvc.perform(put("/api/v1/experience/theme-preference")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-decision-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_DECISION_USER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "eye"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("eye"))
            .andExpect(jsonPath("$.data.version").value(3));
    }

    private static ThemePreferenceResponse response(String mode, long version) {
        return new ThemePreferenceResponse(
            mode,
            version,
            Instant.parse("2026-06-01T00:00:00Z"),
            "doctor-1"
        );
    }
}
