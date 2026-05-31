package com.medkernel.shared.datascope;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.medkernel.engine.org.OrgLevel;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataScopeAspectTest {

    @AfterEach
    void clear() {
        RequestContext.clear();
    }

    @Test
    void tenantRequiredButMissingThrows() {
        TenantRequiredService target = proxy(new TenantRequiredService());
        assertThatThrownBy(target::run)
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.TENANT_CONTEXT_MISSING);
    }

    @Test
    void tenantPresentAllowsCall() {
        TenantRequiredService target = proxy(new TenantRequiredService());
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u"));
        assertThat(target.run()).isEqualTo("ok");
    }

    @Test
    void hospitalLevelRequiresHospitalId() {
        HospitalScopedService target = proxy(new HospitalScopedService());
        RequestContext.restore(new RequestContext.Snapshot("trace", OrgScope.tenant("t-1"), "u"));
        assertThatThrownBy(target::run)
            .isInstanceOf(ApiException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.DATA_SCOPE_DENIED);
    }

    @Test
    void hospitalLevelSatisfiedWhenHospitalIdPresent() {
        HospitalScopedService target = proxy(new HospitalScopedService());
        RequestContext.restore(new RequestContext.Snapshot("trace",
            new OrgScope("t-1", null, "h-1", null, null, null, null),
            "u"));
        assertThat(target.run()).isEqualTo("hospital-ok");
    }

    @Test
    void methodLevelOverridesClassLevel() {
        // 类标 requireTenant=false，方法标 requireTenant=true → 方法级应生效
        OverrideService target = proxy(new OverrideService());
        assertThatThrownBy(target::strict)
            .isInstanceOf(ApiException.class);
        assertThat(target.relaxed()).isEqualTo("relaxed-ok");
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T bean) {
        AspectJProxyFactory factory = new AspectJProxyFactory(bean);
        factory.addAspect(new DataScopeAspect());
        return (T) factory.getProxy();
    }

    static class TenantRequiredService {
        @DataScope(requireTenant = true)
        public String run() {
            return "ok";
        }
    }

    static class HospitalScopedService {
        @DataScope(requireTenant = true, requireAtLeast = OrgLevel.HOSPITAL)
        public String run() {
            return "hospital-ok";
        }
    }

    @DataScope(requireTenant = false)
    static class OverrideService {
        @DataScope(requireTenant = true)
        public String strict() {
            return "strict-ok";
        }

        public String relaxed() {
            return "relaxed-ok";
        }
    }
}
