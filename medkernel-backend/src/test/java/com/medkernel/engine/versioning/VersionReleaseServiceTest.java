package com.medkernel.engine.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.medkernel.engine.security.PermissionCode;
import com.medkernel.engine.security.PermissionEvaluator;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.PlatformTenant;
import com.medkernel.shared.context.RequestContext;

class VersionReleaseServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T10:00:00Z"), ZoneOffset.UTC);

    private AssetVersionRepository assetVersions;
    private VersionReleasePlanRepository releasePlans;
    private VersionActivationTransactionRepository activationTransactions;
    private PermissionEvaluator permissionEvaluator;
    private VersionReleaseService service;

    @Test
    void releaseScopeTypeContainsOnlyOrganizationScopeValues() {
        assertThat(Arrays.stream(VersionReleaseScopeType.values()).map(Enum::name))
            .containsExactly("ALL", "REGION", "FACILITY", "CAMPUS", "DEPARTMENT", "WARD");
    }

    @BeforeEach
    void setUp() {
        assetVersions = mock(AssetVersionRepository.class);
        releasePlans = mock(VersionReleasePlanRepository.class);
        activationTransactions = mock(VersionActivationTransactionRepository.class);
        permissionEvaluator = mock(PermissionEvaluator.class);
        service = new VersionReleaseService(
            assetVersions, releasePlans, activationTransactions, permissionEvaluator, CLOCK);
        authenticate(RoleCode.HOSPITAL_ADMIN);
        when(permissionEvaluator.has(PermissionCode.TENANT_OVERRIDE)).thenReturn(true);
        when(permissionEvaluator.has(PermissionCode.PLATFORM_PUBLISH)).thenReturn(false);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContext.clear();
    }

    @Test
    void advancesReviewObservationGrayAndFullWithEvidenceAndAtomicActivation() {
        AssetVersion draft = version("av-v2", "2.0.0", AssetVersionStatus.DRAFT, AssetVersionSafetyPolicy.NORMAL);
        AssetVersion pending = draft.withStatus(
            AssetVersionStatus.PENDING_REVIEW,
            "version:av-v2",
            CLOCK.instant(),
            "publisher-1"
        );
        AssetVersion published = pending.withStatus(
            AssetVersionStatus.PUBLISHED,
            "version:av-v2",
            CLOCK.instant(),
            "publisher-1"
        );
        AssetVersion oldActive = version("av-v1", "1.0.0", AssetVersionStatus.ACTIVE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.ACTIVE, activeScopeKey(), CLOCK.instant(), "publisher-1");

        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A"))
            .thenReturn(Optional.of(draft), Optional.of(pending), Optional.of(published), Optional.of(published));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            activeScopeKey(),
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(oldActive));
        when(assetVersions.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releasePlans.save(any(VersionReleasePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTransactions.save(any(VersionActivationTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VersionReleasePlan review = service.submitForReview(releaseCommand(
            "av-v2",
            null,
            null,
            "影响摘要 d1",
            List.of("IMPLEMENTER")
        ));
        VersionReleasePlan observation = service.approveForSilentObservation(releaseCommand(
            "av-v2",
            null,
            null,
            "影响摘要 d1",
            List.of("IMPLEMENTER")
        ));
        VersionReleasePlan gray = service.releaseGray(releaseCommand(
            "av-v2",
            null,
            null,
            "影响摘要 d1",
            List.of("IMPLEMENTER")
        ));
        VersionReleasePlan full = service.releaseFull(releaseCommand(
            "av-v2",
            VersionReleaseScopeType.ALL,
            null,
            "影响摘要 d1",
            List.of("HOSPITAL_ADMIN")
        ));

        assertThat(review.status()).isEqualTo(VersionReleaseStatus.PENDING_REVIEW);
        assertThat(observation.status()).isEqualTo(VersionReleaseStatus.SILENT_OBSERVATION);
        assertThat(gray.status()).isEqualTo(VersionReleaseStatus.GRAY);
        assertThat(gray.scopeType()).isEqualTo(VersionReleaseScopeType.FACILITY);
        assertThat(gray.scopeValue()).contains("\"rolloutStrategy\":\"CANARY_BED_PERCENT\"");
        assertThat(gray.scopeValue()).contains("\"percentage\":10");
        assertThat(full.status()).isEqualTo(VersionReleaseStatus.FULL);
        assertThat(full.evidenceSummary()).contains("FULL").contains("影响摘要 d1");

        verify(activationTransactions).save(any(VersionActivationTransaction.class));
        verify(assetVersions).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.versionId().equals("av-v1")
                && saved.status() == AssetVersionStatus.OFFLINE
                && saved.effectiveTo() != null
        ));
        verify(assetVersions).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.versionId().equals("av-v2")
                && saved.status() == AssetVersionStatus.ACTIVE
                && activeScopeKey().equals(saved.activeScopeKey())
                && saved.effectiveFrom() != null
        ));
    }

    @Test
    void rejectedReviewReturnsPendingVersionToDraftWithEvidence() {
        AssetVersion pending = version(
            "av-v2",
            "2.0.0",
            AssetVersionStatus.PENDING_REVIEW,
            AssetVersionSafetyPolicy.NORMAL
        );
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A"))
            .thenReturn(Optional.of(pending));
        when(assetVersions.save(any(AssetVersion.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(releasePlans.save(any(VersionReleasePlan.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VersionReleasePlan rejected = service.rejectReview(releaseCommand(
            "av-v2",
            null,
            null,
            "影响摘要 d1",
            List.of("MEDICAL_AFFAIRS")
        ));

        assertThat(rejected.status()).isEqualTo(VersionReleaseStatus.REVIEW_REJECTED);
        assertThat(rejected.evidenceSummary()).contains("审核拒绝").contains("审核结论");
        verify(assetVersions).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.versionId().equals("av-v2")
                && saved.status() == AssetVersionStatus.DRAFT
                && saved.activeScopeKey().equals("version:av-v2")
        ));
    }

    @Test
    void rejectsTenantReleaseWithoutTenantOverridePermission() {
        AssetVersion published = version("av-v2", "2.0.0", AssetVersionStatus.PUBLISHED, AssetVersionSafetyPolicy.NORMAL);
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A")).thenReturn(Optional.of(published));
        authenticate(RoleCode.IT_OPS);
        when(permissionEvaluator.has(PermissionCode.TENANT_OVERRIDE)).thenReturn(false);

        assertThatThrownBy(() -> service.releaseFull(releaseCommand(
            "av-v2",
            VersionReleaseScopeType.ALL,
            null,
            "影响摘要 d1",
            List.of("hospital-admin")
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("tenant.override")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(releasePlans, never()).save(any(VersionReleasePlan.class));
        verify(assetVersions, never()).save(any(AssetVersion.class));
    }

    @Test
    void rejectsTenantReleaseOutsideCurrentRequestTenant() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-tenant-b",
            OrgScope.tenant("tenant-B"),
            "publisher-1"
        ));

        assertThatThrownBy(() -> service.releaseFull(releaseCommand(
            "av-v2",
            VersionReleaseScopeType.ALL,
            null,
            "影响摘要 d1",
            List.of("hospital-admin")
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("当前请求租户")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(assetVersions, releasePlans, activationTransactions);
    }

    @Test
    void platformReleaseRequiresPlatformPublishPermission() {
        AssetVersion published = version(
            "av-platform-v2",
            PlatformTenant.ID,
            "2.0.0",
            PlatformAuthority.PLATFORM_ORG_PATH,
            AssetVersionStatus.PUBLISHED,
            AssetVersionSafetyPolicy.NORMAL);
        when(assetVersions.findByVersionIdAndTenantId("av-platform-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(published));
        when(permissionEvaluator.has(PermissionCode.PLATFORM_PUBLISH)).thenReturn(false);

        assertThatThrownBy(() -> service.releaseFull(new VersionReleaseCommand(
            PlatformTenant.ID,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-platform-v2",
            PlatformAuthority.PLATFORM_ORG_PATH,
            "adult|inpatient",
            VersionReleaseScopeType.ALL,
            null,
            "平台发布影响摘要",
            "平台审核结论",
            List.of("platform-admin"),
            "platform-publisher",
            "trace-platform"
        )))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("platform.publish")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FORBIDDEN);

        verify(releasePlans, never()).save(any(VersionReleasePlan.class));
        verify(assetVersions, never()).save(any(AssetVersion.class));
    }

    @Test
    void platformReleaseWithPlatformPublishPermissionActivatesPlatformVersion() {
        AssetVersion target = version(
            "av-platform-v2",
            PlatformTenant.ID,
            "2.0.0",
            PlatformAuthority.PLATFORM_ORG_PATH,
            AssetVersionStatus.PUBLISHED,
            AssetVersionSafetyPolicy.NORMAL);
        when(assetVersions.findByVersionIdAndTenantId("av-platform-v2", PlatformTenant.ID))
            .thenReturn(Optional.of(target));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            PlatformTenant.ID,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK|" + PlatformAuthority.PLATFORM_ORG_PATH + "|adult|inpatient",
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of());
        when(permissionEvaluator.has(PermissionCode.PLATFORM_PUBLISH)).thenReturn(true);
        when(assetVersions.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releasePlans.save(any(VersionReleasePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTransactions.save(any(VersionActivationTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VersionReleasePlan result = service.releaseFull(new VersionReleaseCommand(
            PlatformTenant.ID,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-platform-v2",
            PlatformAuthority.PLATFORM_ORG_PATH,
            "adult|inpatient",
            VersionReleaseScopeType.ALL,
            null,
            "平台发布影响摘要",
            "平台审核结论",
            List.of("platform-admin"),
            "platform-publisher",
            "trace-platform"
        ));

        assertThat(result.status()).isEqualTo(VersionReleaseStatus.FULL);
        verify(permissionEvaluator).has(PermissionCode.PLATFORM_PUBLISH);
        verify(activationTransactions).save(any(VersionActivationTransaction.class));
    }

    private void authenticate(RoleCode role) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "publisher-1",
                "n/a",
                List.of(new SimpleGrantedAuthority(role.authority()))
            )
        );
    }

    @Test
    void returnsExistingFullReleaseEvidenceWhenTargetAlreadyActive() {
        AssetVersion target = version("av-v2", "2.0.0", AssetVersionStatus.ACTIVE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.ACTIVE, activeScopeKey(), CLOCK.instant(), "publisher-1");
        VersionActivationTransaction transaction = activationTransaction(
            "av-v1",
            "av-v2",
            VersionActivationAction.FULL_ACTIVATE,
            "FULL 全量激活：影响摘要 d1"
        );
        VersionReleasePlan existingPlan = releasePlan(
            "av-v2",
            "av-v1",
            VersionReleaseStatus.FULL,
            "FULL 全量激活：影响摘要 d1"
        );
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A")).thenReturn(Optional.of(target));
        when(assetVersions.findByTenantIdAndAssetTypeAndActiveScopeKeyAndStatus(
            "tenant-A",
            VersionedAssetType.RULE,
            activeScopeKey(),
            AssetVersionStatus.ACTIVE
        )).thenReturn(List.of(target));
        when(activationTransactions.findByTenantIdAndAssetTypeAndAssetIdentityAndToVersionIdAndActionAndActiveScopeKey(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v2",
            VersionActivationAction.FULL_ACTIVATE,
            activeScopeKey()
        )).thenReturn(Optional.of(transaction));
        when(releasePlans.findFirstByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdAndStatusAndTargetOrgPathAndApplicableScopeOrderByCreatedAtDesc(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v2",
            VersionReleaseStatus.FULL,
            "/TENANT-A/GROUP-A/HOSP-A",
            "adult|inpatient"
        )).thenReturn(Optional.of(existingPlan));

        VersionReleasePlan result = service.releaseFull(releaseCommand(
            "av-v2",
            VersionReleaseScopeType.ALL,
            null,
            "影响摘要 d1",
            List.of("HOSPITAL_ADMIN")
        ));

        assertThat(result).isEqualTo(existingPlan);
        verify(assetVersions, never()).save(any(AssetVersion.class));
        verify(activationTransactions, never()).save(any(VersionActivationTransaction.class));
        verify(releasePlans, never()).save(any(VersionReleasePlan.class));
    }

    @Test
    void rejectsRollbackToWithdrawnSafetyRedline() {
        AssetVersion current = version("av-v2", "2.0.0", AssetVersionStatus.ACTIVE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.ACTIVE, activeScopeKey(), CLOCK.instant(), "publisher-1");
        AssetVersion withdrawnRedline = version(
            "av-v1",
            "1.0.0",
            AssetVersionStatus.WITHDRAWN,
            AssetVersionSafetyPolicy.SAFETY_REDLINE
        );
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A")).thenReturn(Optional.of(current));
        when(assetVersions.findByVersionIdAndTenantId("av-v1", "tenant-A")).thenReturn(Optional.of(withdrawnRedline));

        assertThatThrownBy(() -> service.rollback(rollbackCommand("av-v2", "av-v1", true)))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("ROLLBACK_SAFETY_DENIED")
            .extracting("errorCode")
            .isEqualTo(ErrorCode.ROLLBACK_SAFETY_DENIED);

        verify(activationTransactions, never()).save(any(VersionActivationTransaction.class));
    }

    @Test
    void rollsBackToOfflineVersionAndRecordsActivationTransaction() {
        AssetVersion current = version("av-v2", "2.0.0", AssetVersionStatus.ACTIVE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.ACTIVE, activeScopeKey(), CLOCK.instant(), "publisher-1");
        AssetVersion target = version("av-v1", "1.0.0", AssetVersionStatus.OFFLINE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.OFFLINE, "version:av-v1", CLOCK.instant(), "publisher-1");
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A")).thenReturn(Optional.of(current));
        when(assetVersions.findByVersionIdAndTenantId("av-v1", "tenant-A")).thenReturn(Optional.of(target));
        when(assetVersions.save(any(AssetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(releasePlans.save(any(VersionReleasePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTransactions.save(any(VersionActivationTransaction.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VersionReleasePlan rollback = service.rollback(rollbackCommand("av-v2", "av-v1", true));

        assertThat(rollback.status()).isEqualTo(VersionReleaseStatus.ROLLBACKED);
        assertThat(rollback.evidenceSummary()).contains("回滚").contains("临床专家确认");
        verify(activationTransactions).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.action() == VersionActivationAction.ROLLBACK
                && saved.fromVersionId().equals("av-v2")
                && saved.toVersionId().equals("av-v1")
        ));
        verify(assetVersions).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.versionId().equals("av-v2") && saved.status() == AssetVersionStatus.OFFLINE
        ));
        verify(assetVersions).save(org.mockito.ArgumentMatchers.argThat(saved ->
            saved.versionId().equals("av-v1") && saved.status() == AssetVersionStatus.ACTIVE
        ));
    }

    @Test
    void returnsExistingRollbackEvidenceWhenRetryAlreadySwitched() {
        AssetVersion current = version("av-v2", "2.0.0", AssetVersionStatus.OFFLINE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.OFFLINE, "version:av-v2", CLOCK.instant(), "publisher-1");
        AssetVersion target = version("av-v1", "1.0.0", AssetVersionStatus.ACTIVE, AssetVersionSafetyPolicy.NORMAL)
            .withStatus(AssetVersionStatus.ACTIVE, activeScopeKey(), CLOCK.instant(), "publisher-1");
        VersionActivationTransaction transaction = activationTransaction(
            "av-v2",
            "av-v1",
            VersionActivationAction.ROLLBACK,
            "ROLLBACK 回滚：回滚到 1.0.0；原因：临床专家确认回退到稳定版本"
        );
        VersionReleasePlan existingPlan = releasePlan(
            "av-v1",
            "av-v2",
            VersionReleaseStatus.ROLLBACKED,
            "ROLLBACK 回滚：回滚到 1.0.0；原因：临床专家确认回退到稳定版本"
        );
        when(assetVersions.findByVersionIdAndTenantId("av-v2", "tenant-A")).thenReturn(Optional.of(current));
        when(assetVersions.findByVersionIdAndTenantId("av-v1", "tenant-A")).thenReturn(Optional.of(target));
        when(activationTransactions.findByTenantIdAndAssetTypeAndAssetIdentityAndToVersionIdAndActionAndActiveScopeKey(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v1",
            VersionActivationAction.ROLLBACK,
            activeScopeKey()
        )).thenReturn(Optional.of(transaction));
        when(releasePlans.findFirstByTenantIdAndAssetTypeAndAssetIdentityAndVersionIdAndStatusAndTargetOrgPathAndApplicableScopeOrderByCreatedAtDesc(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            "av-v1",
            VersionReleaseStatus.ROLLBACKED,
            "/TENANT-A/GROUP-A/HOSP-A",
            "adult|inpatient"
        )).thenReturn(Optional.of(existingPlan));

        VersionReleasePlan result = service.rollback(rollbackCommand("av-v2", "av-v1", true));

        assertThat(result).isEqualTo(existingPlan);
        verify(assetVersions, never()).save(any(AssetVersion.class));
        verify(activationTransactions, never()).save(any(VersionActivationTransaction.class));
        verify(releasePlans, never()).save(any(VersionReleasePlan.class));
    }

    private VersionReleaseCommand releaseCommand(
            String versionId,
            VersionReleaseScopeType scopeType,
            String scopeValue,
            String impactDigest,
            List<String> roleCodes) {
        return new VersionReleaseCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionId,
            "/TENANT-A/GROUP-A/HOSP-A",
            "adult|inpatient",
            scopeType,
            scopeValue,
            impactDigest,
            "审核结论：规则测试全绿",
            roleCodes,
            "publisher-1",
            "trace-sys04-pr3"
        );
    }

    private VersionRollbackCommand rollbackCommand(String currentVersionId, String targetVersionId, boolean confirmedHighRisk) {
        return new VersionRollbackCommand(
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            currentVersionId,
            targetVersionId,
            "2.0.0",
            "1.0.0",
            "临床专家确认回退到稳定版本",
            confirmedHighRisk,
            "publisher-1",
            "trace-sys04-pr3"
        );
    }

    private AssetVersion version(
            String versionId,
            String versionNo,
            AssetVersionStatus status,
            AssetVersionSafetyPolicy safetyPolicy) {
        return version(versionId, "tenant-A", versionNo, "/TENANT-A/GROUP-A/HOSP-A", status, safetyPolicy);
    }

    private AssetVersion version(
            String versionId,
            String tenantId,
            String versionNo,
            String orgPath,
            AssetVersionStatus status,
            AssetVersionSafetyPolicy safetyPolicy) {
        Instant now = CLOCK.instant();
        return new AssetVersion(
            1L,
            versionId,
            tenantId,
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionNo,
            orgPath,
            "adult|inpatient",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            safetyPolicy,
            AssetVersionOverridePolicy.FREE,
            status,
            status == AssetVersionStatus.ACTIVE
                ? "RULE.VTE.RISK|" + orgPath + "|adult|inpatient"
                : "version:" + versionId,
            "rule/RULE.VTE.RISK",
            null,
            null,
            now,
            "publisher-1",
            now,
            "publisher-1",
            "trace-sys04-pr3"
        );
    }

    private String activeScopeKey() {
        return "RULE.VTE.RISK|/TENANT-A/GROUP-A/HOSP-A|adult|inpatient";
    }

    private VersionActivationTransaction activationTransaction(
            String fromVersionId,
            String toVersionId,
            VersionActivationAction action,
            String evidence) {
        return new VersionActivationTransaction(
            1L,
            "vat-existing",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            fromVersionId,
            toVersionId,
            action,
            activeScopeKey(),
            "影响摘要 d1",
            evidence,
            CLOCK.instant(),
            "publisher-1",
            CLOCK.instant(),
            "publisher-1",
            "trace-sys04-pr3"
        );
    }

    private VersionReleasePlan releasePlan(
            String versionId,
            String fromVersionId,
            VersionReleaseStatus status,
            String evidence) {
        return new VersionReleasePlan(
            1L,
            "vrl-existing",
            "tenant-A",
            VersionedAssetType.RULE,
            "RULE.VTE.RISK",
            versionId,
            fromVersionId,
            "/TENANT-A/GROUP-A/HOSP-A",
            "adult|inpatient",
            VersionReleaseScopeType.ALL,
            null,
            status,
            "影响摘要 d1",
            "审核结论：规则测试全绿",
            evidence,
            CLOCK.instant(),
            "publisher-1",
            CLOCK.instant(),
            "publisher-1",
            "trace-sys04-pr3"
        );
    }
}
