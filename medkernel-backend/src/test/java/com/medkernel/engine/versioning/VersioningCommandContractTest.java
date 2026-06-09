package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class VersioningCommandContractTest {

    @Test
    void assetVersionRequiresExplicitSafetyAndOverridePolicies() {
        assertThat(publicConstructors(AssetVersion.class)).hasSize(1);
        assertThat(publicConstructors(AssetVersion.class)[0].getParameterTypes())
            .containsSubsequence(AssetVersionSafetyPolicy.class, AssetVersionOverridePolicy.class);
    }

    @Test
    void inheritanceOverrideRequiresExplicitPropagation() {
        assertThat(publicConstructors(InheritanceOverrideRegisterCommand.class)).hasSize(1);
        assertThat(publicConstructors(InheritanceOverrideRegisterCommand.class)[0].getParameterTypes())
            .endsWith(InheritancePropagation.class);
    }

    @Test
    void rolloutStrategySeparatesOrganizationSelectionFromReleaseScope() {
        assertThat(Arrays.stream(RolloutStrategy.values()).map(Enum::name))
            .containsExactly("ALL", "ORG_SUBTREE", "ORG_LIST", "CANARY_BED_PERCENT", "STAGED");
    }

    @Test
    void versionReleaseCommandRequiresStructuredRolloutPolicyWithoutCompatibilityConstructor() {
        assertThat(Arrays.stream(VersionReleaseCommand.class.getRecordComponents())
            .map(component -> component.getName()))
            .containsSubsequence("scopeType", "scopeValue", "rolloutPolicy", "impactDigest")
            .doesNotContain("roleCodes");
        assertThat(publicConstructors(VersionReleaseCommand.class)).hasSize(1);
    }

    @Test
    void releasePlanPersistsRolloutStateOutsideOrganizationScopeFields() {
        assertThat(Arrays.stream(VersionReleasePlan.class.getRecordComponents())
            .map(component -> component.getName()))
            .containsSubsequence(
                "scopeType",
                "scopeValue",
                "rolloutStrategy",
                "rolloutConfigJson",
                "rolloutStageIndex",
                "rolloutPausedReason",
                "status"
            );
        assertThat(Arrays.stream(VersionReleaseStatus.values()).map(Enum::name))
            .contains("PAUSED");
    }

    @Test
    void rolloutObservationUsesDedicatedCommandFactAndResultContracts() throws Exception {
        Class<?> observation = Class.forName(
            "com.medkernel.engine.versioning.VersionRolloutObservation");
        Class<?> command = Class.forName(
            "com.medkernel.engine.versioning.VersionRolloutObservationCommand");
        Class<?> result = Class.forName(
            "com.medkernel.engine.versioning.VersionRolloutObservationResult");
        Class<?> service = Class.forName(
            "com.medkernel.engine.versioning.VersionRolloutService");

        assertThat(Arrays.stream(observation.getRecordComponents()).map(component -> component.getName()))
            .contains("sampleCount", "hitRate", "manualRejectionRate", "anomalyRate");
        assertThat(Arrays.stream(command.getRecordComponents()).map(component -> component.getName()))
            .contains("planId", "sampleCount", "manualRejectionCount", "anomalyCount");
        assertThat(Arrays.stream(result.getRecordComponents()).map(component -> component.getName()))
            .contains("paused", "readyForFullRelease", "currentStagePercent");
        assertThat(service.getDeclaredMethods()).anyMatch(method -> method.getName().equals("observe"));
    }

    @Test
    void releaseSimulationExposesReadOnlyImpactReplayAndSafetyContracts() throws Exception {
        Class<?> command = Class.forName(
            "com.medkernel.engine.versioning.ReleaseSimulationCommand");
        Class<?> result = Class.forName(
            "com.medkernel.engine.versioning.ReleaseSimulationResult");
        Class<?> service = Class.forName(
            "com.medkernel.engine.versioning.ReleaseSimulationService");

        assertThat(Arrays.stream(command.getRecordComponents()).map(component -> component.getName()))
            .contains(
                "candidateTenantId",
                "candidateVersionId",
                "targetOrgUnitIds",
                "rolloutPolicy",
                "replayDays",
                "replayLimit"
            );
        assertThat(Arrays.stream(result.getRecordComponents()).map(component -> component.getName()))
            .contains(
                "simulationDigest",
                "affectedOrganizations",
                "applicableDimensions",
                "diff",
                "replay",
                "safety",
                "dependencies",
                "conflicts",
                "releasable"
            );
        assertThat(service.getDeclaredMethods()).anyMatch(method -> method.getName().equals("simulate"));
    }

    @Test
    void overrideReuseExposesTemplatePreviewApplyRevokeAndCloneContracts() throws Exception {
        Class<?> template = Class.forName(
            "com.medkernel.engine.versioning.OverrideTemplate");
        Class<?> item = Class.forName(
            "com.medkernel.engine.versioning.OverrideTemplateItem");
        Class<?> previewCommand = Class.forName(
            "com.medkernel.engine.versioning.OverrideBatchPreviewCommand");
        Class<?> previewResult = Class.forName(
            "com.medkernel.engine.versioning.OverrideBatchPreviewResult");
        Class<?> service = Class.forName(
            "com.medkernel.engine.versioning.OverrideTemplateService");

        assertThat(Arrays.stream(template.getRecordComponents()).map(component -> component.getName()))
            .contains("templateId", "tenantId", "templateName", "applicableScope", "status");
        assertThat(Arrays.stream(item.getRecordComponents()).map(component -> component.getName()))
            .contains("assetType", "assetIdentity", "overrideMode", "propagation");
        assertThat(Arrays.stream(previewCommand.getRecordComponents()).map(component -> component.getName()))
            .contains("templateId", "sourceOrgUnitId", "targetOrgUnitIds", "targetVersionIds");
        assertThat(Arrays.stream(previewResult.getRecordComponents()).map(component -> component.getName()))
            .contains("previewDigest", "rows", "releasable");
        assertThat(Arrays.stream(service.getDeclaredMethods()).map(method -> method.getName()))
            .contains("createTemplate", "preview", "apply", "revoke");
    }

    @Test
    void releaseGovernanceUsesOneCanonicalController() throws Exception {
        Class<?> controller = Class.forName(
            "com.medkernel.engine.versioning.ReleaseGovernanceController");
        assertThat(Arrays.stream(controller.getDeclaredMethods()).map(method -> method.getName()))
            .contains(
                "simulate",
                "startRollout",
                "observeRollout",
                "rollbackRollout",
                "listTemplates",
                "createTemplate",
                "previewOverrides",
                "applyOverrides",
                "revokeOverrides"
            );
        assertThat(Arrays.stream(ReleaseGovernanceController.StartRolloutRequest.class.getRecordComponents())
            .map(component -> component.getName()))
            .doesNotContain("scopeType", "scopeValue");
    }

    private Constructor<?>[] publicConstructors(Class<?> type) {
        return Arrays.stream(type.getConstructors())
            .filter(constructor -> !constructor.isSynthetic())
            .toArray(Constructor<?>[]::new);
    }
}
