package com.medkernel.shared.observability;

import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * GA-CORE-04 / W1-G6 闸门：业务指标桥接 Micrometer + Prometheus。
 *
 * <p>暴露 5 大业务主线的核心指标（详见 docs/CONSTITUTION.md §2）：
 * <ul>
 *   <li>medkernel_tenant_onboarding_total — 租户开通累计数（试点准备）
 *   <li>medkernel_pathway_active — 当前在径患者数（临床运行 gauge）
 *   <li>medkernel_cdss_alerts_total — CDSS 提醒发出累计数（临床运行）
 *   <li>medkernel_quality_findings_open — 当前未闭环质控问题数（质控改进 gauge）
 *   <li>medkernel_audit_chain_signed_total — 已验签审计条目累计数（合规运维）
 * </ul>
 *
 * <p>Prometheus 端点：{@code /actuator/prometheus}（management.endpoints.web.exposure 已开放）
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    private Counter tenantOnboarding;
    private Counter cdssAlerts;
    private Counter auditChainSigned;
    private Counter auditPersistenceFailures;
    private Counter auditFallbackWritten;
    private Counter auditFallbackFailures;

    private final AtomicLong activePathways = new AtomicLong();
    private final AtomicLong openFindings = new AtomicLong();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void register() {
        this.tenantOnboarding = Counter.builder("medkernel_tenant_onboarding_total")
            .description("试点准备：累计完成开通的租户数")
            .register(registry);

        this.cdssAlerts = Counter.builder("medkernel_cdss_alerts_total")
            .description("临床运行：CDSS 累计发出的提醒条数")
            .register(registry);

        this.auditChainSigned = Counter.builder("medkernel_audit_chain_signed_total")
            .description("合规运维：累计完成审计链验签的条目数")
            .register(registry);

        this.auditPersistenceFailures = Counter.builder("medkernel_audit_persistence_failures_total")
            .description("合规运维：审计事件落库失败累计数（业务调用不受影响，但需要告警）")
            .register(registry);

        this.auditFallbackWritten = Counter.builder("medkernel_audit_fallback_written_total")
            .description("合规运维：审计落库失败后成功写入本地降级文件的累计数")
            .register(registry);

        this.auditFallbackFailures = Counter.builder("medkernel_audit_fallback_failures_total")
            .description("合规运维：审计本地降级文件写入失败累计数")
            .register(registry);

        Gauge.builder("medkernel_pathway_active", activePathways, AtomicLong::doubleValue)
            .description("临床运行：当前在径患者数")
            .register(registry);

        Gauge.builder("medkernel_quality_findings_open", openFindings, AtomicLong::doubleValue)
            .description("质控改进：当前未闭环质控问题数")
            .register(registry);
    }

    public void incTenantOnboarding() { tenantOnboarding.increment(); }
    public void incCdssAlerts() { cdssAlerts.increment(); }
    public void incAuditChainSigned() { auditChainSigned.increment(); }
    public void incAuditPersistenceFailures() { auditPersistenceFailures.increment(); }
    public void incAuditFallbackWritten() { auditFallbackWritten.increment(); }
    public void incAuditFallbackFailures() { auditFallbackFailures.increment(); }

    public void setActivePathways(long n) { activePathways.set(n); }
    public void setOpenFindings(long n) { openFindings.set(n); }
}
