package com.medkernel.engine.integration.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.context.ClinicalRuntimeRelease;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContent;
import com.medkernel.engine.context.ClinicalRuntimeReleaseContentResolver;
import com.medkernel.engine.context.ClinicalRuntimeReleaseItem;
import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.context.ContextSnapshotService;
import com.medkernel.engine.context.CurrentClinicalRuntimeReleaseResolver;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

class ThirdPartyKnowledgeRuntimeServiceTest {

    private final CurrentClinicalRuntimeReleaseResolver currentReleases =
        mock(CurrentClinicalRuntimeReleaseResolver.class);
    private final ClinicalRuntimeReleaseContentResolver releaseContents =
        mock(ClinicalRuntimeReleaseContentResolver.class);
    private final ContextSnapshotService contexts = mock(ContextSnapshotService.class);
    private final ThirdPartyKnowledgeRuntimeService service =
        new ThirdPartyKnowledgeRuntimeService(currentReleases, releaseContents, contexts);

    @BeforeEach
    void setUpContext() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-third-party-runtime",
            new OrgScope("tenant-A", null, "hospital-A", null, null, "dept-A", null, null),
            "integration-user"));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void resolvesTheAuthenticatedHospitalsCurrentImmutableRuntimeRelease() {
        Instant activatedAt = Instant.parse("2026-06-23T08:00:00Z");
        ClinicalRuntimeRelease release = new ClinicalRuntimeRelease(
            1L,
            "runtime-H12",
            "tenant-A",
            "hospital-A",
            12L,
            "baseline-A7",
            "a".repeat(64),
            null,
            activatedAt,
            "publisher-A",
            activatedAt,
            "publisher-A",
            "trace-release");
        ClinicalRuntimeReleaseItem rule = new ClinicalRuntimeReleaseItem(
            1L,
            release.releaseId(),
            "t-1",
            ReleaseSourceLayer.PLATFORM,
            VersionedAssetType.RULE,
            "RULE.AF.SAFETY",
            ReleaseEntryState.ACTIVE,
            "rule-v3",
            "V3",
            "b".repeat(64),
            activatedAt,
            "publisher-A",
            "trace-release");
        when(currentReleases.resolve(RequestContext.currentOrgScope())).thenReturn(release);
        when(releaseContents.resolve("tenant-A", "runtime-H12"))
            .thenReturn(new ClinicalRuntimeReleaseContent(release, List.of(rule)));

        ThirdPartyRuntimeReleaseResponse response = service.resolveCurrentRuntimeRelease();

        assertThat(response.contractVersion()).isEqualTo("v1");
        assertThat(response.releaseId()).isEqualTo("runtime-H12");
        assertThat(response.revisionNo()).isEqualTo(12L);
        assertThat(response.platformBaselineReleaseId()).isEqualTo("baseline-A7");
        assertThat(response.manifestSha256()).isEqualTo("a".repeat(64));
        assertThat(response.assetCount()).isEqualTo(1);
        assertThat(response.assets()).containsExactly(rule);
        verify(releaseContents).resolve("tenant-A", "runtime-H12");
    }

    @Test
    void delegatesContextSnapshotWritesWithoutAcceptingPackageSelectors() {
        ContextSnapshotRequest request = mock(ContextSnapshotRequest.class);
        ContextSnapshotResponse response = mock(ContextSnapshotResponse.class);
        when(contexts.create(request, "idem-1")).thenReturn(response);

        assertThat(service.writeContext(request, "idem-1")).isSameAs(response);
    }
}
