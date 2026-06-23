package com.medkernel.engine.authoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionContent;
import com.medkernel.engine.versioning.AssetVersionContentRepository;
import com.medkernel.engine.versioning.AssetVersionDraftUpdateCommand;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRegisterCommand;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.DeclarativeAssetContentValidator;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 独立配置资产维护服务测试。
 */
class DeclarativeAssetServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AssetVersionService versions = mock(AssetVersionService.class);
    private final AssetVersionRepository versionRepository = mock(AssetVersionRepository.class);
    private final AssetVersionContentRepository contents = mock(AssetVersionContentRepository.class);
    private final DeclarativeAssetService service = new DeclarativeAssetService(
        json,
        new DeclarativeAssetContentValidator(json),
        versions,
        versionRepository,
        contents
    );

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void createsTypedDraftWithIndependentAutomaticVersion() throws Exception {
        restoreTenant("trace-create");
        AssetVersion saved = version(
            "av-1",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V1",
            AssetVersionStatus.DRAFT,
            "tenant:tenant-A",
            "ALL"
        );
        when(versions.registerDraft(any())).thenReturn(saved);

        DeclarativeAssetDetailResponse response = service.create(valueSetRequest("ALL"));

        ArgumentCaptor<AssetVersionRegisterCommand> command =
            ArgumentCaptor.forClass(AssetVersionRegisterCommand.class);
        verify(versions).registerDraft(command.capture());
        assertThat(command.getValue().assetType()).isEqualTo(VersionedAssetType.VALUE_SET);
        assertThat(command.getValue().assetIdentity()).isEqualTo("VS.NEPHROTOXIC_ATC");
        assertThat(command.getValue().organizationScope()).isNull();
        assertThat(command.getValue().applicableScope()).isEqualTo("ALL");
        assertThat(json.readTree(command.getValue().content()).path("members")).hasSize(1);
        assertThat(response.versionNo()).isEqualTo("V1");
        assertThat(response.organizationScope()).isEqualTo("tenant:tenant-A");
        assertThat(response.applicableScope()).isEqualTo("ALL");
    }

    @Test
    void requestAndResponseDoNotExposePackageOrManualVersionInputs() {
        assertThat(DeclarativeAssetUpsertRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("packageId", "packageVersion", "versionNo");
        assertThat(DeclarativeAssetSummaryResponse.class.getRecordComponents())
            .extracting(component -> component.getName())
            .doesNotContain("packageId", "packageVersion");
    }

    @Test
    void defaultsApplicableScopeToAll() throws Exception {
        restoreTenant("trace-scope");
        when(versions.registerDraft(any())).thenReturn(version(
            "av-1",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V1",
            AssetVersionStatus.DRAFT,
            "tenant:tenant-A",
            "ALL"
        ));

        service.create(valueSetRequest(" "));

        verify(versions).registerDraft(org.mockito.ArgumentMatchers.argThat(command ->
            "ALL".equals(command.applicableScope())
        ));
    }

    @Test
    void reportsConcurrentAutomaticVersionAllocationConflictHonestly() throws Exception {
        restoreTenant("trace-conflict");
        when(versions.registerDraft(any()))
            .thenThrow(new DuplicateKeyException("uk_version_asset_identity_version"));

        assertThatThrownBy(() -> service.create(valueSetRequest("ALL")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("版本并发创建冲突")
            .hasMessageContaining("VS.NEPHROTOXIC_ATC");
    }

    @Test
    void updatesDraftWithoutChangingIdentityOrType() throws Exception {
        restoreTenant("trace-update");
        AssetVersion existing = version(
            "av-1",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V2",
            AssetVersionStatus.DRAFT,
            "tenant:tenant-A",
            "adult"
        );
        AssetVersion saved = version(
            "av-1",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V2",
            AssetVersionStatus.DRAFT,
            "tenant:tenant-A",
            "ALL"
        );
        when(versionRepository.findByVersionIdAndTenantId("av-1", "tenant-A"))
            .thenReturn(Optional.of(existing));
        when(versions.updateDraft(any())).thenReturn(saved);

        DeclarativeAssetDetailResponse response =
            service.update("av-1", valueSetRequest("ALL"));

        ArgumentCaptor<AssetVersionDraftUpdateCommand> command =
            ArgumentCaptor.forClass(AssetVersionDraftUpdateCommand.class);
        verify(versions).updateDraft(command.capture());
        assertThat(command.getValue().organizationScope()).isEqualTo("tenant:tenant-A");
        assertThat(command.getValue().applicableScope()).isEqualTo("ALL");
        assertThat(response.versionId()).isEqualTo("av-1");
        assertThat(response.versionNo()).isEqualTo("V2");
    }

    @Test
    void refusesIdentityTypeAndPublishedVersionMutation() throws Exception {
        restoreTenant("trace-immutable");
        AssetVersion draft = version(
            "av-draft",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V1",
            AssetVersionStatus.DRAFT,
            "tenant:tenant-A",
            "ALL"
        );
        when(versionRepository.findByVersionIdAndTenantId("av-draft", "tenant-A"))
            .thenReturn(Optional.of(draft));

        DeclarativeAssetUpsertRequest changedIdentity = new DeclarativeAssetUpsertRequest(
            VersionedAssetType.VALUE_SET,
            "VS.OTHER",
            "ALL",
            "来源",
            valueSetContent()
        );
        assertThatThrownBy(() -> service.update("av-draft", changedIdentity))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("资产编码不能原地修改");

        DeclarativeAssetUpsertRequest changedType = new DeclarativeAssetUpsertRequest(
            VersionedAssetType.FORMULA,
            "VS.NEPHROTOXIC_ATC",
            "ALL",
            "来源",
            json.readTree("""
                {
                  "schemaVersion": "1.0",
                  "name": "体质指数",
                  "runtimeFunction": "BMI",
                  "inputs": [{"name": "体重", "fieldPath": "patient.weightKg"}],
                  "output": {"dataType": "number", "unit": "kg/m2"}
                }
                """)
        );
        assertThatThrownBy(() -> service.update("av-draft", changedType))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("资产类型不能修改");

        AssetVersion published = version(
            "av-published",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V1",
            AssetVersionStatus.PUBLISHED,
            "tenant:tenant-A",
            "ALL"
        );
        when(versionRepository.findByVersionIdAndTenantId("av-published", "tenant-A"))
            .thenReturn(Optional.of(published));
        assertThatThrownBy(() -> service.update("av-published", valueSetRequest("ALL")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("创建下一版本");

        verify(versions, never()).updateDraft(org.mockito.ArgumentMatchers.argThat(command ->
            "av-published".equals(command.versionId())
        ));
    }

    @Test
    void refusesPathwayDuplicateContentModel() throws Exception {
        restoreTenant("trace-pathway");

        assertThatThrownBy(() -> service.create(new DeclarativeAssetUpsertRequest(
            VersionedAssetType.PATHWAY,
            "PATH.SUB",
            "ALL",
            "来源",
            json.readTree("{\"schemaVersion\":\"1.0\",\"name\":\"错误子路径\"}")
        ))).isInstanceOf(ApiException.class).hasMessageContaining("路径工作台");

        verify(versions, never()).registerDraft(any());
    }

    @Test
    void readsExactBodyAndScopeByTenantAndVersionId() throws Exception {
        restoreTenant("trace-read");
        AssetVersion version = version(
            "av-1",
            VersionedAssetType.FORMULA,
            "FORMULA.BMI",
            "V2",
            AssetVersionStatus.DRAFT,
            "tenant:tenant-A/hospital:H01",
            "adult"
        );
        when(versionRepository.findByVersionIdAndTenantId("av-1", "tenant-A"))
            .thenReturn(Optional.of(version));
        when(contents.findByTenantIdAndVersionId("tenant-A", "av-1"))
            .thenReturn(Optional.of(new AssetVersionContent(
                1L,
                "av-1",
                "tenant-A",
                "{\"schemaVersion\":\"1.0\",\"name\":\"体质指数\",\"runtimeFunction\":\"BMI\"}",
                "a".repeat(64),
                Instant.now(),
                "operator-1",
                Instant.now(),
                "operator-1",
                "trace-read"
            )));

        DeclarativeAssetDetailResponse response = service.detail("av-1");

        assertThat(response.assetIdentity()).isEqualTo("FORMULA.BMI");
        assertThat(response.versionNo()).isEqualTo("V2");
        assertThat(response.organizationScope()).isEqualTo("tenant:tenant-A/hospital:H01");
        assertThat(response.applicableScope()).isEqualTo("adult");
        assertThat(response.content().path("runtimeFunction").asText()).isEqualTo("BMI");
    }

    @Test
    void listsIndependentVersionsWithTheirScopes() {
        restoreTenant("trace-list");
        when(versionRepository.pageByTenantIdAndAssetType(
            "tenant-A", "VALUE_SET", 0, 20
        )).thenReturn(List.of(version(
            "av-1",
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            "V3",
            AssetVersionStatus.PUBLISHED,
            "tenant:tenant-A",
            "adult"
        )));
        when(versionRepository.countByTenantIdAndAssetType("tenant-A", "VALUE_SET"))
            .thenReturn(1L);

        var response = service.list(
            VersionedAssetType.VALUE_SET,
            new PageRequest(1, 20, null)
        );

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.versionNo()).isEqualTo("V3");
            assertThat(item.organizationScope()).isEqualTo("tenant:tenant-A");
            assertThat(item.applicableScope()).isEqualTo("adult");
        });
    }

    private DeclarativeAssetUpsertRequest valueSetRequest(String applicableScope) throws Exception {
        return new DeclarativeAssetUpsertRequest(
            VersionedAssetType.VALUE_SET,
            "VS.NEPHROTOXIC_ATC",
            applicableScope,
            "权威 ATC 来源",
            valueSetContent()
        );
    }

    private com.fasterxml.jackson.databind.JsonNode valueSetContent() throws Exception {
        return json.readTree("""
            {
              "schemaVersion": "1.0",
              "name": "肾毒性药物 ATC 值集",
              "codeSystem": "ATC",
              "members": [{"code": "J01GB03", "display": "庆大霉素"}]
            }
            """);
    }

    private void restoreTenant(String traceId) {
        RequestContext.restore(new RequestContext.Snapshot(
            traceId,
            OrgScope.tenant("tenant-A"),
            "operator-1"
        ));
    }

    private AssetVersion version(
            String versionId,
            VersionedAssetType type,
            String identity,
            String versionNo,
            AssetVersionStatus status,
            String organizationScope,
            String applicableScope) {
        Instant now = Instant.parse("2026-06-22T00:00:00Z");
        return new AssetVersion(
            1L,
            versionId,
            "tenant-A",
            type,
            identity,
            versionNo,
            organizationScope,
            applicableScope,
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            "version:" + versionId,
            "来源",
            null,
            null,
            now,
            "operator-1",
            now,
            "operator-1",
            "trace"
        );
    }
}
