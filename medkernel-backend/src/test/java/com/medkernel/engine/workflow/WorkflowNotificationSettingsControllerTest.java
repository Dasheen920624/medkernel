package com.medkernel.engine.workflow;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class WorkflowNotificationSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkflowCollaborationService workflowService;

    @MockBean
    private WorkflowNotificationSettingsService settingsService;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void getSettingsReturnsCurrentUserNotificationPreferences() throws Exception {
        when(settingsService.getSettings()).thenReturn(response());

        mockMvc.perform(get("/api/v1/engine/notifications/settings")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.inAppEnabled").value(true))
            .andExpect(jsonPath("$.data.webhookEnabled").value(false))
            .andExpect(jsonPath("$.data.inHospitalMessageEnabled").value(false))
            .andExpect(jsonPath("$.data.quietBypassLevels[0]").value("CRITICAL"));
    }

    @Test
    void saveSettingsPersistsQuietHoursThroughBackendEndpoint() throws Exception {
        when(settingsService.saveSettings(org.mockito.ArgumentMatchers.any())).thenReturn(response());

        mockMvc.perform(put("/api/v1/engine/notifications/settings")
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inAppEnabled": true,
                      "smsEnabled": false,
                      "emailEnabled": false,
                      "pushEnabled": false,
                      "webhookEnabled": false,
                      "inHospitalMessageEnabled": false,
                      "quietHoursEnabled": true,
                      "quietStart": "22:00",
                      "quietEnd": "07:00",
                      "quietBypassLevels": ["CRITICAL", "HIGH"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.quietHoursEnabled").value(true));
    }

    private static WorkflowNotificationSettingsResponse response() {
        return new WorkflowNotificationSettingsResponse(
            true,
            false,
            false,
            false,
            false,
            false,
            true,
            "22:00",
            "07:00",
            new LinkedHashSet<>(List.of(WorkflowNotificationLevel.CRITICAL, WorkflowNotificationLevel.HIGH)),
            true,
            3,
            Instant.parse("2026-06-04T08:00:00Z"),
            "doctor-1");
    }
}
