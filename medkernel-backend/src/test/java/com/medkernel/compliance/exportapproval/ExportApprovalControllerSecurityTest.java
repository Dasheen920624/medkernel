package com.medkernel.compliance.exportapproval;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

import com.medkernel.shared.context.RequestContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

/**
 * SYS-06 PR3 导出审批控制器安全矩阵。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ExportApprovalControllerSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ExportApprovalService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("审计合规角色可到达导出申请，但缺租户上下文被 DataScope 拦截")
    @WithMockUser(authorities = "ROLE_COMPLIANCE_AUDITOR")
    void requestExport_auditRoleWithoutTenant_returns400() throws Exception {
        mvc.perform(post("/api/v1/compliance/exports:request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ENG-BASE-001"));
    }

    @Test
    @DisplayName("普通医生无 audit.export 权限，不能申请敏感数据导出")
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void requestExport_doctorRole_returns403() throws Exception {
        mvc.perform(post("/api/v1/compliance/exports:request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("审计合规角色带租户上下文可申请敏感数据导出")
    void requestExport_auditRoleWithTenant_returns200() throws Exception {
        when(service.requestExport(eq("t-1"), any(ExportApprovalRequest.class), eq("auditor-1")))
            .thenReturn(response(ExportApprovalStatus.REQUESTED));

        mvc.perform(post("/api/v1/compliance/exports:request")
                .with(jwt().jwt(token -> token
                    .subject("auditor-1")
                    .claim("tenant_id", "t-1")
                    .claim("group_id", "g-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "compliance")
                    .claim("roles", List.of("audit_compliance")))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REQUESTED"));
    }

    @Test
    @DisplayName("审计合规角色带租户上下文可查询本租户导出审批")
    void listExports_auditRoleWithTenant_returns200() throws Exception {
        when(service.listApprovals("t-1", "AUDIT_EVENT", ExportApprovalStatus.REQUESTED))
            .thenReturn(List.of(response(ExportApprovalStatus.REQUESTED)));

        mvc.perform(get("/api/v1/compliance/exports")
                .param("resourceType", "AUDIT_EVENT")
                .param("status", "REQUESTED")
                .with(jwt().jwt(token -> token
                    .subject("auditor-1")
                    .claim("tenant_id", "t-1")
                    .claim("group_id", "g-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "compliance")
                    .claim("roles", List.of("audit_compliance")))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].status").value("REQUESTED"));
    }

    @Test
    @DisplayName("审计合规角色带租户上下文可审批导出申请")
    void approveExport_auditRoleWithTenant_returns200() throws Exception {
        when(service.reviewExport(eq("t-1"), eq("exp-clinical-case-idem-001"),
            any(ExportApprovalReviewRequest.class), eq("auditor-1")))
            .thenReturn(response(ExportApprovalStatus.APPROVED));

        mvc.perform(post("/api/v1/compliance/exports/exp-clinical-case-idem-001:approve")
                .with(jwt().jwt(token -> token
                    .subject("auditor-1")
                    .claim("tenant_id", "t-1")
                    .claim("group_id", "g-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "compliance")
                    .claim("roles", List.of("audit_compliance")))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("普通医生无 audit.export 权限，不能审批导出申请")
    @WithMockUser(authorities = "ROLE_CLINICAL_DECISION_USER")
    void approveExport_doctorRole_returns403() throws Exception {
        mvc.perform(post("/api/v1/compliance/exports/exp-clinical-case-idem-001:approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveBody()))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("审计合规角色带租户上下文可从后端任务登记真实导出完成")
    void completeExportFromJob_auditRoleWithTenant_returns200() throws Exception {
        when(service.completeExportFromJob(eq("t-1"), eq("exp-clinical-case-idem-001"),
            any(ExportJobCompletionRequest.class), eq("auditor-1")))
            .thenReturn(response(ExportApprovalStatus.EXPORTED));

        mvc.perform(post("/api/v1/compliance/exports/exp-clinical-case-idem-001:complete-from-job")
                .with(jwt().jwt(token -> token
                    .subject("auditor-1")
                    .claim("tenant_id", "t-1")
                    .claim("group_id", "g-1")
                    .claim("hospital_id", "h-1")
                    .claim("department_id", "compliance")
                    .claim("roles", List.of("audit_compliance")))
                    .authorities(new SimpleGrantedAuthority("ROLE_COMPLIANCE_AUDITOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("EXPORTED"));
    }

    private String requestBody() {
        return """
            {
              "resourceType": "clinical_case",
              "exportScope": {
                "patientId": "p-1",
                "reasonCode": "audit-review"
              },
              "reason": "合规审计需要导出当前患者证据包",
              "idempotencyKey": "idem-001"
            }
            """;
    }

    private String approveBody() {
        return """
            {
              "decision": "APPROVE",
              "comment": "审批通过，允许生成真实导出文件",
              "expectedVersion": 1
            }
            """;
    }

    private String completeBody() {
        return """
            {
              "jobId": "job-audit-1",
              "reason": "真实导出文件已生成并完成摘要登记",
              "expectedVersion": 2
            }
            """;
    }

    private ExportApprovalResponse response(ExportApprovalStatus status) {
        Instant now = Instant.parse("2026-06-05T00:00:00Z");
        return new ExportApprovalResponse(
            "exp-clinical-case-idem-001",
            "clinical_case",
            "{\"patientId\":\"p-1\"}",
            "idem-001",
            "合规审计需要导出当前患者证据包",
            status,
            "auditor-1",
            "auditor-2",
            "APPROVE",
            "审批通过，允许生成真实导出文件",
            "evd-exp-clinical-case-idem-001-approval",
            "/api/v1/compliance/evidence/snapshots/evd-exp-clinical-case-idem-001-approval/file",
            "s3://tenant-t-1/exports/clinical-case.ndjson",
            "sm3:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "evd-exp-clinical-case-idem-001-export",
            "/api/v1/compliance/evidence/snapshots/evd-exp-clinical-case-idem-001-export/file",
            3L,
            now,
            now);
    }
}
