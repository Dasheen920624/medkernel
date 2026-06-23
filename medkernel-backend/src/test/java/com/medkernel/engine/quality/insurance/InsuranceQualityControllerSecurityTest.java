package com.medkernel.engine.quality.insurance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.medkernel.engine.evaluation.EvaluationModelStatus;
import com.medkernel.engine.evaluation.QualityFindingSeverity;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class InsuranceQualityControllerSecurityTest {

    private static final String CASE_REVIEW_BODY = """
        {
          "contextSnapshotId": "snapshot-case",
          "scenarioCode": "A9",
          "responsibleDepartmentId": "dept-records"
        }
        """;

    private static final String DRG_GROUPING_BODY = """
        {
          "contextSnapshotId": "snapshot-drg",
          "grouperVersion": "DRG-GROUPER-2026A",
          "expectedGroupCode": "GROUP-A",
          "actualGroupCode": "GROUP-B",
          "responsibleDepartmentId": "dept-records",
          "explanation": "首页诊断与费用组合进入复核"
        }
        """;

    private static final String INSURANCE_AUDIT_BODY = """
        {
          "contextSnapshotId": "snapshot-ins",
          "scenarioCode": "A9",
          "indicatorId": "indicator-insurance",
          "responsibleDepartmentId": "dept-insurance",
          "dueAt": "2026-06-12T00:00:00Z",
          "rules": [
            {
              "ruleCode": "RULE-FEE-A",
              "ruleVersion": "2026-A",
              "issueType": "FEE",
              "severity": "P1",
              "maxAmount": 1000.00,
              "description": "费用超过版本化规则阈值"
            }
          ]
        }
        """;

    @Autowired MockMvc mvc;

    @MockBean InsuranceQualityService service;

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void unauthenticatedCannotExecuteInsuranceQualityEndpoints() throws Exception {
        mvc.perform(post("/api/v1/engine/quality/case-review")
                .contentType("application/json")
                .content(CASE_REVIEW_BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void qualityManagerCanExecuteCaseReviewDrgGroupingAndInsuranceAudit() throws Exception {
        when(service.caseReview(any())).thenReturn(new QualityCaseReviewResponse(
            "review-1", CaseReviewStatus.PASS, "run-1", 1, 0, 0,
            EvaluationModelStatus.MODEL_DISABLED, "MODEL_DISABLED_DETERMINISTIC_RULES", "trace-quality"));
        when(service.drgGrouping(any())).thenReturn(new DrgGroupingResponse(
            "drg-1", DrgGroupingStatus.MATCHED, "GROUP-A", "GROUP-A", "DRG-GROUPER-2026A",
            "入组一致", "trace-quality"));
        when(service.insuranceAudit(any())).thenReturn(new InsuranceAuditResponse(
            "audit-1", InsuranceAuditStatus.NO_ISSUE, List.of(), null, 0, 0, "trace-quality"));

        mvc.perform(post("/api/v1/engine/quality/case-review")
                .contentType("application/json")
                .content(CASE_REVIEW_BODY)
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/engine/quality/drg-grouping")
                .contentType("application/json")
                .content(DRG_GROUPING_BODY)
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/engine/quality/insurance-audit")
                .contentType("application/json")
                .content(INSURANCE_AUDIT_BODY)
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isCreated());
    }

    @Test
    void qualityManagerCanReadTenantScopedInsuranceIssues() throws Exception {
        when(service.listInsuranceIssues(any(), any())).thenReturn(PageResponse.of(List.of(
            new InsuranceIssuePageItemResponse(
                "ins-1", "claim-1", InsuranceIssueType.FEE, QualityFindingSeverity.P1,
                InsuranceIssueStatus.OPEN, "RULE-FEE-A", "2026-A",
                new BigDecimal("1200.00"), new BigDecimal("1000.00"),
                "结算事实 claim-1；规则 RULE-FEE-A@2026-A",
                "dept-insurance", null, "trace-ins", Instant.parse("2026-06-05T00:00:00Z"))
        ), new PageRequest(1, 20, null), 1));

        mvc.perform(get("/api/v1/engine/quality/insurance-issues")
                .param("status", "OPEN")
                .param("severity", "P1")
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("engine-operator")))
                    .authorities(new SimpleGrantedAuthority("ROLE_ENGINE_OPERATOR"))))
            .andExpect(status().isOk());
    }

    @Test
    void doctorCannotExecuteInsuranceQualityEndpoints() throws Exception {
        mvc.perform(post("/api/v1/engine/quality/insurance-audit")
                .contentType("application/json")
                .content(INSURANCE_AUDIT_BODY)
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("clinical-user")))
                    .authorities(new SimpleGrantedAuthority("ROLE_CLINICAL_USER"))))
            .andExpect(status().isForbidden());
    }
}
