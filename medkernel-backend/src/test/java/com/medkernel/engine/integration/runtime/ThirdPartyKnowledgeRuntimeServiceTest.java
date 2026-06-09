package com.medkernel.engine.integration.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResolver;
import com.medkernel.engine.pkg.EffectiveKnowledgePackageResponse;
import com.medkernel.engine.pkg.PackageEngineService;
import com.medkernel.engine.pkg.PackageSyncRequest;
import com.medkernel.engine.pkg.ReleaseScopeType;
import com.medkernel.engine.pkg.ReleaseStrategy;
import com.medkernel.engine.pkg.SyncLogResponse;
import com.medkernel.engine.pkg.SyncLogStatus;
import com.medkernel.engine.versioning.InheritanceOverrideMode;
import com.medkernel.engine.versioning.InheritanceOverrideRegisterCommand;
import com.medkernel.engine.versioning.InheritanceOverrideService;
import com.medkernel.engine.versioning.InheritancePropagation;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class ThirdPartyKnowledgeRuntimeServiceTest {

    private final EffectiveKnowledgePackageResolver packageResolver =
        mock(EffectiveKnowledgePackageResolver.class);
    private final ContextSnapshotService contexts = mock(ContextSnapshotService.class);
    private final InheritanceOverrideService overrides = mock(InheritanceOverrideService.class);
    private final PackageEngineService packages = mock(PackageEngineService.class);
    private final AuditRecorder audits = mock(AuditRecorder.class);
    private final ThirdPartyKnowledgeRuntimeService service =
        new ThirdPartyKnowledgeRuntimeService(packageResolver, contexts, overrides, packages, audits);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-third-party-runtime",
            OrgScope.tenant("tenant-A"),
            "integration-user"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void resolvesEffectivePackageWithCanonicalDimensionsAndRequestedTime() {
        Instant effectiveAt = Instant.parse("2026-06-01T08:00:00Z");
        when(packageResolver.resolve(
            eq("tenant-A"),
            eq("PKG.AF"),
            eq("2026.06"),
            eq("dept-1"),
            eq("specialty=AF;scenario=S16;setting=ED"),
            eq(effectiveAt)))
            .thenReturn(new EffectiveKnowledgePackageResponse(
                "tenant-A", "dept-1", "pkg-1", "PKG.AF", "2026.06",
                List.of(), List.of(), List.of()));

        ThirdPartyEffectivePackageResponse response = service.resolveEffectivePackage(
            new ThirdPartyEffectivePackageQuery(
                "PKG.AF", "2026.06", "dept-1",
                "AF", "S16", "ED", null, null, effectiveAt));

        assertThat(response.contractVersion()).isEqualTo("v1");
        assertThat(response.effectiveAt()).isEqualTo(effectiveAt);
        assertThat(response.applicableScope())
            .isEqualTo("specialty=AF;scenario=S16;setting=ED");
        assertThat(response.snapshot().contentSha256()).hasSize(64);
    }

    @Test
    void createsOverrideOnlyForCurrentTenantAndActor() {
        ThirdPartyOverrideRequest request = new ThirdPartyOverrideRequest(
            VersionedAssetType.RULE,
            "RULE.AF",
            "av-platform",
            "av-local",
            "dept-1",
            "specialty=AF;scenario=S16",
            InheritanceOverrideMode.REPLACE,
            "阈值按本院检验参考区间调整",
            "院内检验方法不同",
            "房颤急诊场景",
            InheritancePropagation.INHERITABLE);

        service.createOverride(request);

        ArgumentCaptor<InheritanceOverrideRegisterCommand> command =
            ArgumentCaptor.forClass(InheritanceOverrideRegisterCommand.class);
        verify(overrides).registerOverride(command.capture());
        assertThat(command.getValue())
            .extracting(
                InheritanceOverrideRegisterCommand::tenantId,
                InheritanceOverrideRegisterCommand::createdBy,
                InheritanceOverrideRegisterCommand::traceId,
                InheritanceOverrideRegisterCommand::overrideMode,
                InheritanceOverrideRegisterCommand::propagation)
            .containsExactly(
                "tenant-A",
                "integration-user",
                "trace-third-party-runtime",
                InheritanceOverrideMode.REPLACE,
                InheritancePropagation.INHERITABLE);
        verify(audits).record(any(), eq("mk_version_inheritance_override"), any(), any());
    }

    @Test
    void reconciliationReportsHonestNotSyncedState() {
        when(packages.listSyncLogs("pkg-1")).thenReturn(List.of(
            new SyncLogResponse(
                "log-1", "plan-1", "adapter-1", SyncLogStatus.SUCCESS,
                null, null, 0, "ok"),
            new SyncLogResponse(
                "log-2", "plan-1", "adapter-2", SyncLogStatus.NOT_SYNCED,
                "NOT_CONNECTED", "适配器未接入真实同步通道", 0, null)));

        ThirdPartyPackageReconciliationResponse response = service.reconcilePackage("pkg-1");

        assertThat(response.contractVersion()).isEqualTo("v1");
        assertThat(response.status()).isEqualTo(ThirdPartyReconciliationStatus.NOT_SYNCED);
        assertThat(response.logs()).hasSize(2);
    }

    @Test
    void rejectsPackageDistributionWhenBodyTenantDiffersFromRequestTenant() {
        PackageSyncRequest request = new PackageSyncRequest(
            "req-1",
            "trace-third-party-runtime",
            "tenant-B",
            null,
            "hospital-1",
            null,
            null,
            "dept-1",
            "AF",
            "integration-user",
            List.of("IT_OPS"),
            "2026.06",
            "第三方分发",
            "dept-1",
            ReleaseStrategy.GRAYSCALE,
            ReleaseScopeType.DEPARTMENT,
            "dept-1",
            List.of("adapter-1"));

        assertThatThrownBy(() -> service.distributePackage("pkg-1", request))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.ORG_SCOPE_DENIED);
        verify(packages, never()).syncPackage(any(), any());
    }
}
