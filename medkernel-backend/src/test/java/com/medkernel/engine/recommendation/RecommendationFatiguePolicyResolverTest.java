package com.medkernel.engine.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.shared.config.SystemConfigItem;
import com.medkernel.shared.config.SystemConfigRepository;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RecommendationFatiguePolicyResolverTest {

    private final SystemConfigRepository configs = mock(SystemConfigRepository.class);
    private final RecommendationFatiguePolicyResolver resolver =
        new RecommendationFatiguePolicyResolver(configs, new ObjectMapper());

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void configuredDepartmentScenarioPolicyTakesPrecedence() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rec",
            new OrgScope("tenant-A", null, "hospital-1", null, null, "dept-icu", null),
            "doctor-1"));
        when(configs.findActive("SYSTEM", RecommendationFatiguePolicyResolver.CONFIG_KEY))
            .thenReturn(Optional.of(config("""
                {
                  "default": { "threshold": 9, "windowHours": 72 },
                  "scenarios": { "WARD_ORDER": { "threshold": 4, "windowHours": 24 } },
                  "departments": { "dept-icu": { "threshold": 3, "windowHours": 12 } },
                  "departmentScenarios": {
                    "dept-icu:WARD_ORDER": { "threshold": 2, "windowHours": 6 }
                  }
                }
                """)));

        Optional<RecommendationFatiguePolicy> policy = resolver.resolve(triggerRequest());

        assertThat(policy).isPresent();
        assertThat(policy.get().threshold()).isEqualTo(2);
        assertThat(policy.get().windowHours()).isEqualTo(6);
        assertThat(policy.get().source()).isEqualTo("CONFIG_CENTER");
    }

    @Test
    void missingConfigDoesNotSuppressWithoutConfiguredPolicy() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rec", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(configs.findActive("SYSTEM", RecommendationFatiguePolicyResolver.CONFIG_KEY))
            .thenReturn(Optional.empty());

        Optional<RecommendationFatiguePolicy> policy = resolver.resolve(triggerRequest());

        assertThat(policy).isEmpty();
    }

    @Test
    void invalidConfigDisablesSuppressionInsteadOfFallingBackToRequest() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-rec", OrgScope.tenant("tenant-A"), "doctor-1"));
        when(configs.findActive("SYSTEM", RecommendationFatiguePolicyResolver.CONFIG_KEY))
            .thenReturn(Optional.of(config("{\"default\":{\"threshold\":\"bad\",\"windowHours\":24}}")));

        Optional<RecommendationFatiguePolicy> policy = resolver.resolve(triggerRequest());

        assertThat(policy).isEmpty();
    }

    private SystemConfigItem config(String value) {
        return new SystemConfigItem(
            "SYSTEM",
            RecommendationFatiguePolicyResolver.CONFIG_KEY,
            value,
            "JSON",
            "CDSS 疲劳治理策略",
            "MEDIUM",
            "医务处 / 信息科",
            "按场景、科室配置 CDSS 低价值提醒抑制阈值。",
            "TEST",
            false,
            true,
            1,
            Instant.now());
    }

    private RecommendationTriggerRequest triggerRequest() {
        return new RecommendationTriggerRequest(
            "TRG.ORDER", "order-sign", "event-1", "snapshot-1",
            "patient-1", "enc-1", "pathway-1", "WARD_ORDER",
            "sha256:trigger", Instant.now(), List.of(),
            false);
    }

}
