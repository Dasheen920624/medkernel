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
 *   <li>/actuator/prometheus 公开端点可匿名访问（SecurityConfig 白名单）
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
        assertThat(registry.find("medkernel_quality_findings_open").gauge()).isNotNull();
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
