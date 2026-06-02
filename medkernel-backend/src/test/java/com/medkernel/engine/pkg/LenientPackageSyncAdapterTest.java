package com.medkernel.engine.pkg;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LenientPackageSyncAdapterTest {

    @Test
    void defaultAdapterRefusesToForgeSyncEvidenceWhenNoRealChannelExists() {
        LenientPackageSyncAdapter adapter = new LenientPackageSyncAdapter();
        ReleasePlan plan = new ReleasePlan(
            1L,
            "plan-1",
            "tenant-A",
            "pkg-1",
            "org-1",
            ReleaseStrategy.FULL,
            ReleaseScopeType.ALL,
            null,
            ReleasePlanStatus.EXECUTING,
            Instant.now(),
            "tester",
            Instant.now(),
            "tester",
            "trace"
        );
        SyncTarget target = new SyncTarget(
            1L,
            "target-1",
            "tenant-A",
            "图谱同步",
            SyncTargetType.GRAPH_DB,
            null,
            SyncTargetStatus.ACTIVE,
            Instant.now(),
            "tester",
            Instant.now(),
            "tester",
            "trace"
        );

        assertThatThrownBy(() -> adapter.sync("tenant-A", plan, target))
            .isInstanceOf(PackageSyncNotConnectedException.class)
            .hasMessageContaining("NOT_SYNCED")
            .hasMessageContaining("未配置真实同步适配器");
    }
}
