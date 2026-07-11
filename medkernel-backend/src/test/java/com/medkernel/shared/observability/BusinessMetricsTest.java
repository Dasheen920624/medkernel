package com.medkernel.shared.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GA-CORE-04 / W1-G6 smoke：
 * <ul>
 *   <li>5 个业务指标已注册到 Micrometer Registry
 *   <li>/actuator/prometheus 公开端点可匿名访问（SecurityConfig 匿名允许入口）
 *   <li>Prometheus 格式 scrape 输出包含业务指标名
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessMetricsTest {

    @Autowired
    MeterRegistry registry;

    @Autowired
    MockMvc mvc;

    @Autowired
    BusinessMetrics metrics;

    @Test
    void allBusinessMetersRegistered() {
        assertThat(registry.find("medkernel_tenant_onboarding_total").counter()).isNotNull();
        assertThat(registry.find("medkernel_cdss_alerts_total").counter()).isNotNull();
        assertThat(registry.find("medkernel_audit_chain_signed_total").counter()).isNotNull();
        assertThat(registry.find("medkernel_audit_persistence_failures_total").counter()).isNotNull();
        assertThat(registry.find("medkernel_audit_fallback_written_total").counter()).isNotNull();
        assertThat(registry.find("medkernel_audit_fallback_failures_total").counter()).isNotNull();
        assertThat(registry.find("medkernel_pathway_active").gauge()).isNotNull();
        var openFindingsGauge = registry.find("medkernel_quality_findings_open").gauge();
        assertThat(openFindingsGauge).isNotNull();
        assertThat(openFindingsGauge.getId().getDescription())
            .isEqualTo("质量管理：当前未闭环质量问题数")
            .doesNotContain("质控问题");
    }

    @Test
    void diagnosisAssistAndCandidateDistributionMetersWork() {
        var assist = registry.find("medkernel_diagnosis_assist_total").counter();
        assertThat(assist).isNotNull();
        double before = assist.count();
        metrics.incDiagnosisAssist();
        assertThat(assist.count()).isEqualTo(before + 1.0);

        metrics.incDiagnosisCandidate("STRONG");
        metrics.incDiagnosisCandidate("STRONG");
        metrics.incDiagnosisCandidate("MODERATE");
        assertThat(registry.find("medkernel_diagnosis_candidate_total")
            .tag("confidence", "STRONG").counter().count()).isGreaterThanOrEqualTo(2.0);
        assertThat(registry.find("medkernel_diagnosis_candidate_total")
            .tag("confidence", "MODERATE").counter().count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    void prometheusEndpointExposesMetrics() throws Exception {
        metrics.incTenantOnboarding();
        metrics.setActivePathways(42);

        mvc.perform(get("/actuator/prometheus"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("medkernel_tenant_onboarding_total")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("medkernel_pathway_active")));
    }
}
