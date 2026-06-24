package com.medkernel.engine.list;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.medkernel.shared.api.PageQuery;
import com.medkernel.shared.api.PageResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
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
        when(service.queryAuditEvents(any(PageQuery.class)))
            .thenReturn(new PageResult<>(List.of(), null, 0L, false, false));

        mockMvc.perform(get("/api/v1/large-lists/audit-events/list")
                .param("size", "20")
                .param("sort", "id,desc")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
                .andExpect(status().isOk());

        verify(service).queryAuditEvents(any(PageQuery.class));
    }

    @Test
    void queryProjectsPersistenceRecordToPublicAuditView() throws Exception {
        AuditEventRecord record = new AuditEventRecord(
            7L,
            "evt-7",
            "trace-7",
            Instant.parse("2026-06-06T12:00:00Z"),
            "auditor-1",
            "EXPORT",
            "audit",
            "snapshot-7",
            "导出审计证据",
            "sm3:digest",
            "tenant-1",
            "hospital-1",
            "department-1",
            null,
            null,
            "sm2:signature",
            "SUCCESS",
            "SUCCESS",
            null,
            Instant.parse("2026-06-06T12:00:01Z"),
            "ROLE_AUDITOR",
            "tenant:tenant-1/hospital:hospital-1",
            "prod",
            "{\"enabled\":true}",
            "{\"enabled\":false}",
            "audit-event-7"
        );
        when(service.queryAuditEvents(any(PageQuery.class)))
            .thenReturn(new PageResult<>(List.of(record), null, 1L, false, false));

        mockMvc.perform(get("/api/v1/large-lists/audit-events/list")
                .param("size", "20")
                .with(jwt().jwt(token -> token
                    .subject("auditor-1")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actionCode").value("EXPORT"))
                .andExpect(jsonPath("$.data.items[0].summary").value("导出审计证据"))
                .andExpect(jsonPath("$.data.items[0].beforeSnapshot").value("{\"enabled\":true}"))
                .andExpect(jsonPath("$.data.items[0].afterSnapshot").value("{\"enabled\":false}"))
                .andExpect(jsonPath("$.data.items[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].action").doesNotExist());
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
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-LIST-006"));
    }

    @Test
    void testExportWithValidRole_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/large-lists/exports")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(EXPORT_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void testExportWithInvalidRole_ShouldReturnForbidden() throws Exception {
        // 临床使用者没有 list.export 权限
        mockMvc.perform(post("/api/v1/large-lists/exports")
                .with(jwt().jwt(token -> token
                    .subject("test-user")
                    .claim("tenant_id", "tenant-1")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER")))
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
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }
}
