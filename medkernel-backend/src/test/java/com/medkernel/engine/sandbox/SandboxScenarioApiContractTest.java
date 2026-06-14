package com.medkernel.engine.sandbox;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.contract.ServiceContractCatalog;
import com.medkernel.shared.audit.AuditAction;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxScenarioApiContractTest {

    @Test
    void sandboxRunControllerIsRegisteredWithPermissionAndAuditPoint() {
        var contract = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.sandbox.SandboxScenarioController")
            .orElseThrow();

        assertThat(contract.basePath()).isEqualTo("/api/v1/engine/sandbox");
        assertThat(contract.declaresPermission("sandbox.run")).isTrue();
        assertThat(contract.auditPoints())
            .anySatisfy(point -> {
                assertThat(point.action()).isEqualTo(AuditAction.EXECUTE);
                assertThat(point.targetType()).isEqualTo("sandbox_scenario");
            });
    }
}
