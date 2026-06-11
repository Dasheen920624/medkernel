package com.medkernel.compliance.audit;

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

import com.medkernel.shared.api.CursorResponse;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.audit.persistence.AuditEventRecord;
import com.medkernel.shared.audit.persistence.AuditQueryService;
import com.medkernel.shared.context.RequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller 安全 + DataScope 矩阵：
 *
 * <ul>
 *   <li>{@code GET /events}：{@code audit.read} 权限拥有者通过 PreAuthorize，缺租户被 DataScope 拒</li>
 *   <li>{@code GET /events}：无 {@code audit.read} 的角色（医生）直接 403</li>
 *   <li>{@code POST /snapshot}：{@code audit.export} 拥有者通过 PreAuthorize，缺租户被 DataScope 拒</li>
 *   <li>{@code POST /snapshot}：无 {@code audit.export} 的角色（医生）直接 403</li>
 * </ul>
 *
 * <p>{@link AuditQueryService} 被 @MockBean 替换，避免数据库依赖；这里只验证 Controller 层契约。
 * 端到端的链路 / 落库正确性已由 {@code AuditChainWriterTest} 覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuditControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AuditQueryService queryService;

    @MockBean
    AuditSafetyGuard safetyGuard;

    @AfterEach
    void clearAll() {
        RequestContext.clear();
    }

    @Test
    @WithMockUser(authorities = "ROLE_COMPLIANCE_AUDITOR")
    void auditComplianceHasReadButDataScopeRejectsMissingTenant() throws Exception {
        mvc.perform(get("/api/v1/compliance/audit/events"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorIsForbiddenFromReadingAudit() throws Exception {
        mvc.perform(get("/api/v1/compliance/audit/events"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_GUEST")
    void guestIsForbiddenFromReadingAudit() throws Exception {
        mvc.perform(get("/api/v1/compliance/audit/events"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_COMPLIANCE_AUDITOR")
    void auditComplianceCanReachSnapshotButDataScopeFails() throws Exception {
        mvc.perform(post("/api/v1/compliance/audit/snapshot?reason=test"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void doctorCannotExportSnapshot() throws Exception {
        mvc.perform(post("/api/v1/compliance/audit/snapshot?reason=test"))
            .andExpect(status().isForbidden());
    }

    @Test
    void eventsExposeSpineFieldsAndSuperAdminHighlight() throws Exception {
        AuditEventRecord record = new AuditEventRecord(
            10L,
            "evt-super-1",
            "trace-1",
            Instant.parse("2026-01-01T00:00:00Z"),
            "system-super-admin",
            "UPDATE",
            "audit_config",
            "medkernel.audit.persistence.enabled",
            "拒绝关闭审计持久化",
            "sm3:abc",
            "t-1",
            "h-1",
            null,
            null,
            "GENESIS",
            "sig-1",
            "SIGNED",
            "FAILED",
            "ENG-AUDIT-001",
            Instant.parse("2026-01-01T00:00:01Z"),
            "ROLE_SYSTEM_SUPERADMIN,ROLE_PLATFORM_GOVERNANCE_ADMIN",
            "tenant:t-1/hospital:h-1",
            "prod",
            "{\"enabled\":true}",
            "{\"enabled\":false}",
            "sm3:dedupe");
        when(queryService.list(any(), eq("UPDATE"), eq("audit_config"), eq("system-super-admin"),
            eq("tenant:t-1/hospital:h-1"), eq("prod"), eq("FAILED"), eq(true),
            any(), any()))
            .thenReturn(CursorResponse.of(List.of(record), null));

        mvc.perform(get("/api/v1/compliance/audit/events")
                .param("action", "UPDATE")
                .param("resourceType", "audit_config")
                .param("actorUserId", "system-super-admin")
                .param("orgPath", "tenant:t-1/hospital:h-1")
                .param("environmentKey", "prod")
                .param("outcome", "FAILED")
                .param("superAdminOnly", "true")
                .with(jwt().jwt(token -> token
                    .subject("audit-controller-reader")
                    .claim("tenant_id", "t-1")
                    .claim("roles", List.of("compliance-auditor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].eventId").value("evt-super-1"))
            .andExpect(jsonPath("$.data.items[0].actorRoles").value("ROLE_SYSTEM_SUPERADMIN,ROLE_PLATFORM_GOVERNANCE_ADMIN"))
            .andExpect(jsonPath("$.data.items[0].orgPath").value("tenant:t-1/hospital:h-1"))
            .andExpect(jsonPath("$.data.items[0].environmentKey").value("prod"))
            .andExpect(jsonPath("$.data.items[0].outcome").value("FAILED"))
            .andExpect(jsonPath("$.data.items[0].errorCode").value("ENG-AUDIT-001"))
            .andExpect(jsonPath("$.data.items[0].payloadDigest").value("sm3:abc"))
            .andExpect(jsonPath("$.data.items[0].beforeSnapshot").value("{\"enabled\":true}"))
            .andExpect(jsonPath("$.data.items[0].afterSnapshot").value("{\"enabled\":false}"))
            .andExpect(jsonPath("$.data.items[0].superAdminAction").value(true));
    }

    @Test
    void systemManagerCanValidateAuditSettingChangeThroughSafetyGuard() throws Exception {
        String body = """
            {
              "key": "medkernel.audit.banner",
              "value": "visible",
              "reason": "更新审计页提示"
            }
            """;

        mvc.perform(post("/api/v1/compliance/audit/settings/validate")
                .with(jwt().jwt(token -> token
                    .subject("audit-controller-platform-admin")
                    .claim("tenant_id", "t-1")
                    .claim("roles", List.of("platform-governance-admin")))
                    .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_GOVERNANCE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accepted").value(true));

        verify(safetyGuard).assertChangeAllowed(any());
    }
}
