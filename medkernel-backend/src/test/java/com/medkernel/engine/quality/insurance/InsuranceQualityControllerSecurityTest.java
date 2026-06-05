package com.medkernel.engine.quality.insurance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.medkernel.engine.evaluation.EvaluationModelStatus;
import com.medkernel.engine.evaluation.QualityFindingSeverity;
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
          "packageVersion": "pkg-quality-v1",
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
          "packageVersion": "pkg-quality-v1",
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
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/engine/quality/drg-grouping")
                .contentType("application/json")
                .content(DRG_GROUPING_BODY)
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
            .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/engine/quality/insurance-audit")
                .contentType("application/json")
                .content(INSURANCE_AUDIT_BODY)
                .with(jwt().jwt(token -> token
                    .subject("qa-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("qa-manager")))
                    .authorities(new SimpleGrantedAuthority("ROLE_QA_MANAGER"))))
            .andExpect(status().isCreated());
    }

    @Test
    void doctorCannotExecuteInsuranceQualityEndpoints() throws Exception {
        mvc.perform(post("/api/v1/engine/quality/insurance-audit")
                .contentType("application/json")
                .content(INSURANCE_AUDIT_BODY)
                .with(jwt().jwt(token -> token
                    .subject("doctor-1")
                    .claim("tenant_id", "tenant-A")
                    .claim("roles", List.of("doctor")))
                    .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"))))
            .andExpect(status().isForbidden());
    }
}
