package com.medkernel.engine.sandbox.replay;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.SourceTier;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SandboxReplayServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final SandboxReplayCaseRepository cases = mock(SandboxReplayCaseRepository.class);
    private final SandboxReplayAssetBindingRepository assets =
        mock(SandboxReplayAssetBindingRepository.class);
    private final AuditRecorder audit = mock(AuditRecorder.class);
    private final SandboxReplayHashing hashing = new SandboxReplayHashing(json);
    private final SandboxReplayService service = new SandboxReplayService(
        cases, assets, hashing, new SandboxReplayDeidentificationValidator(), json, audit);

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-replay", new OrgScope("sandbox-tenant", null, "hospital-1", null, null, null, null, null),
            "governor-1"));
        when(cases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void importsImmutableManifestAndResolvesRetiredRuleWithoutCrossTenantLookup() throws Exception {
        SandboxReplayImportRequest unsigned = request("0".repeat(64));
        SandboxReplayImportRequest request = unsigned.withManifestHash(hashing.manifestHash(unsigned));
        when(cases.findBySandboxTenantIdAndReplayCaseId("sandbox-tenant", "replay-1"))
            .thenReturn(Optional.empty());

        SandboxReplayCaseResponse response = service.importCase(request);

        assertThat(response.replayCaseId()).isEqualTo("replay-1");
        assertThat(response.status()).isEqualTo(SandboxReplayStatus.IMPORTED);
        verify(cases).save(any(SandboxReplayCase.class));
        verify(assets).save(any(SandboxReplayAssetBinding.class));
    }

    @Test
    void rejectsDirectTenantReferenceTamperedAssetAndInPlaceOverwrite() throws Exception {
        SandboxReplayImportRequest unsigned = request("0".repeat(64));
        SandboxReplayImportRequest valid = unsigned.withManifestHash(hashing.manifestHash(unsigned));

        assertThatThrownBy(() -> service.importCase(valid.withSourceTenantRef("source-tenant-real")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不可逆别名");

        var tamperedAsset = valid.assets().getFirst().withContentHash("f".repeat(64));
        assertThatThrownBy(() -> service.importCase(valid.withAssets(List.of(tamperedAsset))))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("资产内容摘要");

        when(cases.findBySandboxTenantIdAndReplayCaseId("sandbox-tenant", "replay-1"))
            .thenReturn(Optional.of(mock(SandboxReplayCase.class)));
        assertThatThrownBy(() -> service.importCase(valid))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("不可原地覆盖");
        verify(assets, never()).deleteAll();
    }

    private SandboxReplayImportRequest request(String manifestHash) throws Exception {
        var context = json.readTree("""
            {"resources":{"patient":{"mpi":"DEID-P-1","name":"DEID-PATIENT"},"observations":[]}}
            """);
        var content = json.readTree("""
            {"ruleCode":"RULE.OLD","name":"历史规则","dsl":{"trigger":"patient-view",
             "when":{"all":[]},"then":[{"actionCode":"INFO","atSeverity":"LOW","indicator":"info",
             "summary":"历史提示","detail":"只读重放，不自动执行","source":{"label":"历史来源"},
             "suggestions":[],"overrideReasons":[],"requiresPhysicianConfirmation":false}]}}
            """);
        return new SandboxReplayImportRequest(
            "replay-1", "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64),
            "sha256:" + "3".repeat(64), "sha256:" + "4".repeat(64), context,
            hashing.contentHash(context), "sha256:" + "6".repeat(64), 4L,
            Instant.parse("2025-01-01T00:00:00Z"),
            manifestHash, "MEDKERNEL_D4_STRICT_V1", List.of(new SandboxReplayAssetImportRequest(
                VersionedAssetType.RULE, "RULE.OLD", "rv-old-1", "1", SourceTier.ORG,
                "sha256:" + "5".repeat(64), content, hashing.contentHash(content),
                AssetVersionStatus.WITHDRAWN)));
    }
}
