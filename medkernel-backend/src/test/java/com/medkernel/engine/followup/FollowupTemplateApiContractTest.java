package com.medkernel.engine.followup;

import org.junit.jupiter.api.Test;

import com.medkernel.engine.contract.ServiceContractCatalog;
import com.medkernel.shared.audit.AuditAction;

import static org.assertj.core.api.Assertions.assertThat;

class FollowupTemplateApiContractTest {

    @Test
    void templateControllerIsRegisteredWithPermissionsAndAuditPoints() {
        var contract = ServiceContractCatalog.contractOfController(
                "com.medkernel.engine.followup.FollowupTemplateController")
            .orElseThrow();

        assertThat(contract.basePath()).isEqualTo("/api/v1/engine/followup/templates");
        assertThat(contract.declaresPermission("followup.read")).isTrue();
        assertThat(contract.declaresPermission("followup.write")).isTrue();
        assertThat(contract.declaresPermission("package.publish")).isTrue();
        assertThat(contract.auditPoints())
            .anySatisfy(point -> {
                assertThat(point.action()).isEqualTo(AuditAction.CREATE);
                assertThat(point.targetType()).isEqualTo("mk_followup_template");
            })
            .anySatisfy(point -> {
                assertThat(point.action()).isEqualTo(AuditAction.PUBLISH);
                assertThat(point.targetType()).isEqualTo("mk_followup_template");
            });
    }
}
