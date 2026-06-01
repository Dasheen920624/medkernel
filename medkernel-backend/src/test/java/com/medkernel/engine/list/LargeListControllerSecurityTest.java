package com.medkernel.engine.list;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.api.PageQuery;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class LargeListControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LargeListEngineService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    private static final String EXPORT_BODY = """
        {
          "resourceType": "AUDIT_EVENT",
          "filters": {}
        }
        """;

    @Test
    void testQueryWithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/large-lists/audit-events/list")
                .param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testQueryWithValidRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/large-lists/audit-events/list")
                .param("size", "20")
                .param("sort", "id,desc")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
                .andExpect(status().isOk());

        verify(service).queryAuditEvents(any(PageQuery.class));
    }

    @Test
    void testQueryWithOversizePage_ShouldReturnPageSizeExceeded() throws Exception {
        when(service.queryAuditEvents(any(PageQuery.class)))
            .thenThrow(new ApiException(ErrorCode.PAGE_SIZE_EXCEEDED));

        mockMvc.perform(get("/api/v1/large-lists/audit-events/list")
                .param("size", "10000")
                .param("sort", "id,desc")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-LIST-006"));
    }

    @Test
    void testExportWithValidRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/large-lists/exports")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(EXPORT_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void testExportWithInvalidRole_ShouldReturnForbidden() throws Exception {
        // 医保办角色没有 list.export 权限
        mockMvc.perform(post("/api/v1/large-lists/exports")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("insurance-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_INSURANCE_MANAGER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(EXPORT_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void testQueryWithValidRoleButMissingTenant_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/large-lists/audit-events/list")
                .param("size", "20")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
