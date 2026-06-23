package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class ReleaseGovernanceControllerTest {

    private final ReleaseSimulationService simulations = mock(ReleaseSimulationService.class);
    private final VersionReleaseService releases = mock(VersionReleaseService.class);
    private final VersionRolloutService rollouts = mock(VersionRolloutService.class);
    private final OverrideTemplateService overrideTemplates = mock(OverrideTemplateService.class);
    private final ReleaseGovernanceController controller = new ReleaseGovernanceController(
        simulations,
        releases,
        rollouts,
        overrideTemplates
    );

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-release",
            OrgScope.tenant("tenant-a"),
            "publisher-a"
        ));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void rejectsRolloutWhenConfirmedSimulationDigestDoesNotMatchServerResult() {
        ReleaseGovernanceController.SimulationRequest simulation = simulationRequest();
        when(simulations.simulate(any())).thenReturn(simulationResult("server-digest", true));

        assertThatThrownBy(() -> controller.startRollout(startRequest(simulation, "client-digest")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("模拟摘要");

        verify(releases, never()).releaseGray(any());
    }

    @Test
    void startsRolloutOnlyWithServerConfirmedReleasableSimulation() {
        ReleaseGovernanceController.SimulationRequest simulation = simulationRequest();
        when(simulations.simulate(any())).thenReturn(simulationResult("confirmed-digest", true));

        controller.startRollout(startRequest(simulation, "confirmed-digest"));

        ArgumentCaptor<VersionReleaseCommand> command = ArgumentCaptor.forClass(VersionReleaseCommand.class);
        verify(releases).releaseGray(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue())
            .extracting(
                VersionReleaseCommand::tenantId,
                VersionReleaseCommand::actor,
                VersionReleaseCommand::traceId,
                VersionReleaseCommand::impactDigest,
                VersionReleaseCommand::scopeType,
                VersionReleaseCommand::scopeValue
            )
            .containsExactly(
                "tenant-a",
                "publisher-a",
                "trace-release",
                "confirmed-digest",
                VersionReleaseScopeType.FACILITY,
                "/tenant-a/org-a"
            );
    }

    @Test
    void requestContractsRejectIncompleteReleaseConfirmation() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ReleaseGovernanceController.SimulationRequest invalidSimulation =
            new ReleaseGovernanceController.SimulationRequest(
                null,
                null,
                " ",
                "",
                List.of(),
                "",
                "",
                null,
                0,
                0
            );
        ReleaseGovernanceController.StartRolloutRequest invalidRequest =
            new ReleaseGovernanceController.StartRolloutRequest(
                invalidSimulation,
                "",
                "",
                null
            );

        org.assertj.core.api.Assertions.assertThat(validator.validate(invalidRequest))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains(
                "simulation.assetType",
                "simulation.assetIdentity",
                "simulation.candidateVersionId",
                "simulation.targetOrgUnitIds",
                "confirmedSimulationDigest",
                "reviewConclusion"
            );
    }

    @Test
    void rollsBackTheCurrentTenantPlanWithoutAcceptingVersionIdsFromTheBrowser() {
        controller.rollbackRollout(
            "vrl-1",
            new ReleaseGovernanceController.RolloutRollbackRequest("灰度异常", true)
        );

        ArgumentCaptor<VersionRolloutRollbackCommand> command =
            ArgumentCaptor.forClass(VersionRolloutRollbackCommand.class);
        verify(rollouts).rollback(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue())
            .extracting(
                VersionRolloutRollbackCommand::tenantId,
                VersionRolloutRollbackCommand::planId,
                VersionRolloutRollbackCommand::actor,
                VersionRolloutRollbackCommand::traceId
            )
            .containsExactly("tenant-a", "vrl-1", "publisher-a", "trace-release");
    }

    @Test
    void listsOverrideTemplatesAsPagedTenantScopedContract() {
        OverrideTemplate template = new OverrideTemplate(
            1L,
            "tpl-a",
            "tenant-a",
            "儿科模板",
            "儿科本地覆盖",
            "ALL",
            OverrideTemplateStatus.ACTIVE,
            Instant.parse("2026-06-07T00:00:00Z"),
            "publisher-a",
            Instant.parse("2026-06-07T00:00:00Z"),
            "publisher-a",
            "trace-release"
        );
        when(overrideTemplates.listTemplates("tenant-a", new PageRequest(2, 1, "updatedAt,desc")))
            .thenReturn(PageResponse.of(List.of(template), new PageRequest(2, 1, "updatedAt,desc"), 2));

        PageResponse<OverrideTemplate> page = controller.listTemplates(2, 1, "updatedAt,desc").data();

        org.assertj.core.api.Assertions.assertThat(page.items()).containsExactly(template);
        org.assertj.core.api.Assertions.assertThat(page.page()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(page.total()).isEqualTo(2);
    }

    private ReleaseGovernanceController.SimulationRequest simulationRequest() {
        return new ReleaseGovernanceController.SimulationRequest(
            null,
            VersionedAssetType.RULE,
            "rule-a",
            "version-a",
            List.of("org-a"),
            "/tenant-a/org-a",
            "ALL",
            RolloutPolicy.canaryBedPercent(10),
            30,
            100
        );
    }

    private ReleaseGovernanceController.StartRolloutRequest startRequest(
            ReleaseGovernanceController.SimulationRequest simulation,
            String confirmedDigest) {
        return new ReleaseGovernanceController.StartRolloutRequest(
            simulation,
            confirmedDigest,
            "已完成临床复核",
            null
        );
    }

    private ReleaseSimulationResult simulationResult(String digest, boolean releasable) {
        return new ReleaseSimulationResult(
            digest,
            Instant.parse("2026-06-07T00:00:00Z"),
            "version-a",
            "version-current",
            List.of(),
            List.of("ALL"),
            new ReleaseSimulationResult.Diff("MODIFIED", "1", "2", "old", "new"),
            ReleaseSimulationResult.Replay.noData("无历史病例"),
            new ReleaseSimulationResult.Check(true, List.of()),
            new ReleaseSimulationResult.Check(true, List.of()),
            List.of(),
            releasable
        );
    }
}
