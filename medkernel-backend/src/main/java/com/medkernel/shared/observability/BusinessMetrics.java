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
 * <p>暴露医疗引擎、知识生产、质量管理与平台管理的核心指标（详见 docs/CONSTITUTION.md §2）：
 * <ul>
 *   <li>medkernel_tenant_onboarding_total — 租户开通累计数（平台管理）
 *   <li>medkernel_pathway_active — 当前在径患者数（医疗引擎 gauge）
 *   <li>medkernel_cdss_alerts_total — CDSS 提醒发出累计数（医疗引擎）
 *   <li>medkernel_quality_findings_open — 当前未闭环质量问题数（质量管理 gauge）
 *   <li>medkernel_audit_chain_signed_total — 已验签审计条目累计数（平台管理）
 * </ul>
 *
 * <p>Prometheus 端点：{@code /actuator/prometheus}（management.endpoints.web.exposure 已开放）
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    private Counter tenantOnboarding;
    private Counter cdssAlerts;
    private Counter diagnosisAssist;
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
            .description("平台管理：累计完成开通的租户数")
            .register(registry);

        this.cdssAlerts = Counter.builder("medkernel_cdss_alerts_total")
            .description("医疗引擎：CDSS 累计发出的提醒条数")
            .register(registry);

        this.diagnosisAssist = Counter.builder("medkernel_diagnosis_assist_total")
            .description("医疗引擎：diagnosis-assist 鉴别诊断累计调用数")
            .register(registry);

        this.auditChainSigned = Counter.builder("medkernel_audit_chain_signed_total")
            .description("平台管理：累计完成审计链验签的条目数")
            .register(registry);

        this.auditPersistenceFailures = Counter.builder("medkernel_audit_persistence_failures_total")
            .description("平台管理：审计事件落库失败累计数（业务调用不受影响，但需要告警）")
            .register(registry);

        this.auditFallbackWritten = Counter.builder("medkernel_audit_fallback_written_total")
            .description("平台管理：审计落库失败后成功写入本地降级文件的累计数")
            .register(registry);

        this.auditFallbackFailures = Counter.builder("medkernel_audit_fallback_failures_total")
            .description("平台管理：审计本地降级文件写入失败累计数")
            .register(registry);

        Gauge.builder("medkernel_pathway_active", activePathways, AtomicLong::doubleValue)
            .description("医疗引擎：当前在径患者数")
            .register(registry);

        Gauge.builder("medkernel_quality_findings_open", openFindings, AtomicLong::doubleValue)
            .description("质量管理：当前未闭环质量问题数")
            .register(registry);
    }

    public void incTenantOnboarding() { tenantOnboarding.increment(); }
    public void incCdssAlerts() { cdssAlerts.increment(); }
    public void incDiagnosisAssist() { diagnosisAssist.increment(); }

    /**
     * 候选分级分布：按置信等级（STRONG/MODERATE/WEAK/EXCLUDE 的名称）累计鉴别诊断候选数。
     * 入参为等级名称字符串，不引用 engine 层枚举（守 shared 不反向依赖 engine 边界）。
     */
    public void incDiagnosisCandidate(String confidenceLevel) {
        registry.counter("medkernel_diagnosis_candidate_total", "confidence", confidenceLevel).increment();
    }
    public void incAuditChainSigned() { auditChainSigned.increment(); }
    public void incAuditPersistenceFailures() { auditPersistenceFailures.increment(); }
    public void incAuditFallbackWritten() { auditFallbackWritten.increment(); }
    public void incAuditFallbackFailures() { auditFallbackFailures.increment(); }

    public void setActivePathways(long n) { activePathways.set(n); }
    public void setOpenFindings(long n) { openFindings.set(n); }
}
