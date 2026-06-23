package com.medkernel.engine.sandbox;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.contract.ServiceContractCatalog;
import com.medkernel.shared.audit.AuditAction;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxScenarioApiContractTest {

    @Test
    void sandboxRunAndReplayGovernanceUseDedicatedPermissionsWithoutPackagePublishing() {
        var contract = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.sandbox.SandboxScenarioController")
            .orElseThrow();

        assertThat(contract.basePath()).isEqualTo("/api/v1/engine/sandbox");
        assertThat(contract.declaresPermission("sandbox.run")).isTrue();
        assertThat(contract.permissions())
            .extracting(permission -> permission.code())
            .noneMatch(code -> code.startsWith("pack" + "age."));
        assertThat(contract.auditPoints())
            .anySatisfy(point -> {
                assertThat(point.action()).isEqualTo(AuditAction.EXECUTE);
                assertThat(point.targetType()).isEqualTo("sandbox_scenario");
            });
        assertThat(contract.auditPoints())
            .noneMatch(point -> "sandbox_runtime_binding".equals(point.targetType()));

        var replay = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.sandbox.replay.SandboxReplayController")
            .orElseThrow();
        assertThat(replay.declaresPermission("sandbox.run")).isTrue();
        assertThat(replay.declaresPermission("sandbox.manage")).isTrue();
        assertThat(replay.permissions())
            .extracting(permission -> permission.code())
            .noneMatch(code -> code.startsWith("pack" + "age."));
    }
}
