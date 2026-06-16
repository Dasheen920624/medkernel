package com.medkernel.engine.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionService;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.ReleasePort;
import com.medkernel.engine.versioning.VersionReleaseCommand;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowupTemplateServiceTest {

    @Mock
    private FollowupTemplateRepository templates;
    @Mock
    private AssetVersionService versionedAssets;
    @Mock
    private AssetVersionRepository assetVersions;
    @Mock
    private ReleasePort releasePort;
    @Mock
    private AuditRecorder auditRecorder;

    private FollowupTemplateService service;

    @BeforeEach
    void setUp() {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-followup-template", OrgScope.tenant("tenant-1"), "user-1"
        ));
        service = new FollowupTemplateService(
            templates, versionedAssets, assetVersions, releasePort, auditRecorder
        );
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void createTemplateRegistersImmutableVersionWithoutPatientRuntimeData() {
        when(templates.findByTenantIdAndTemplateCodeAndVersionNo("tenant-1", "FUP.COPD", 1))
            .thenReturn(Optional.empty());
        when(versionedAssets.registerDraft(any())).thenReturn(assetVersion(
            "av-followup-1", "pending-template-id", "1", AssetVersionStatus.DRAFT
        ));
        when(templates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FollowupTemplateResponse response = service.create(new FollowupTemplateCreateRequest(
            "FUP.COPD",
            1,
            "慢阻肺出院随访",
            "按院内受控事实生成随访任务",
            "tenant:tenant-1",
            "riskLevel in [MEDIUM,HIGH]",
            List.of(new FollowupTemplateTaskInput(
                FollowupTaskType.QUESTIONNAIRE, 7, "QUESTIONNAIRE.COPD.01"
            )),
            """
                {"templateId":"QUESTIONNAIRE.COPD.01","fields":[{"code":"dyspnea","type":"INTEGER"}]}
                """,
            """
                {"condition":"dyspnea >= 4","action":"RETURN_VISIT","notify":"FOLLOWUP_TEAM"}
                """,
            "hospital://followup/copd"
        ));

        ArgumentCaptor<com.medkernel.engine.versioning.AssetVersionRegisterCommand> versionCaptor =
            ArgumentCaptor.forClass(com.medkernel.engine.versioning.AssetVersionRegisterCommand.class);
        verify(versionedAssets).registerDraft(versionCaptor.capture());
        String versionContent = versionCaptor.getValue().content();

        assertThat(response.assetStatus()).isEqualTo(AssetVersionStatus.DRAFT);
        assertThat(response.tasks()).hasSize(1);
        assertThat(versionCaptor.getValue().assetType()).isEqualTo(VersionedAssetType.FOLLOWUP);
        assertThat(versionContent)
            .contains("FUP.COPD", "QUESTIONNAIRE.COPD.01", "RETURN_VISIT")
            .doesNotContain("patientId", "encounterId", "answerData");
    }

    @Test
    void publishTemplateAdvancesUnifiedVersionToPublished() {
        FollowupTemplate template = template("ftpl-1", "av-followup-1");
        when(templates.findByTemplateIdAndTenantId("ftpl-1", "tenant-1"))
            .thenReturn(Optional.of(template));
        when(assetVersions.findByVersionIdAndTenantId("av-followup-1", "tenant-1"))
            .thenReturn(Optional.of(assetVersion(
                "av-followup-1", "ftpl-1", "1", AssetVersionStatus.DRAFT
            )));

        FollowupTemplateResponse response = service.publish(
            "ftpl-1",
            new FollowupTemplatePublishRequest(
                "impact-followup-template-1",
                "随访模板结构、术语绑定和异常处置已复核"
            )
        );

        ArgumentCaptor<VersionReleaseCommand> commandCaptor =
            ArgumentCaptor.forClass(VersionReleaseCommand.class);
        verify(releasePort).submitForReview(commandCaptor.capture());
        verify(releasePort).approveReview(any(VersionReleaseCommand.class));
        verify(releasePort).publish(any(VersionReleaseCommand.class));
        assertThat(commandCaptor.getValue().assetType()).isEqualTo(VersionedAssetType.FOLLOWUP);
        assertThat(response.assetStatus()).isEqualTo(AssetVersionStatus.PUBLISHED);
    }

    @Test
    void listTemplatesUsesRepositoryFilterPaginationInsteadOfTenantSnapshot() {
        FollowupTemplate template = template("ftpl-1", "av-followup-1");
        when(templates.countByFilter("tenant-1", "%copd%", "PUBLISHED"))
            .thenReturn(1L);
        when(templates.pageByFilter("tenant-1", "%copd%", "PUBLISHED", 20, 20))
            .thenReturn(List.of(template));
        when(assetVersions.findByVersionIdAndTenantId("av-followup-1", "tenant-1"))
            .thenReturn(Optional.of(assetVersion(
                "av-followup-1", "ftpl-1", "1", AssetVersionStatus.PUBLISHED
            )));

        PageResponse<FollowupTemplateResponse> response = service.list(
            new FollowupTemplateFilter(AssetVersionStatus.PUBLISHED, " COPD "),
            new PageRequest(2, 20, "updatedAt,desc")
        );

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).extracting(FollowupTemplateResponse::templateId)
            .containsExactly("ftpl-1");
        verify(templates, never()).findByTenantIdOrderByUpdatedAtDesc(anyString());
    }

    private FollowupTemplate template(String templateId, String versionId) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new FollowupTemplate(
            null,
            templateId,
            "tenant-1",
            "FUP.COPD",
            1,
            "慢阻肺出院随访",
            "按院内受控事实生成随访任务",
            "tenant:tenant-1",
            "riskLevel in [MEDIUM,HIGH]",
            """
                [{"taskType":"QUESTIONNAIRE","delayDays":7,"questionnaireTemplateId":"QUESTIONNAIRE.COPD.01"}]
                """,
            """
                {"templateId":"QUESTIONNAIRE.COPD.01","fields":[{"code":"dyspnea","type":"INTEGER"}]}
                """,
            """
                {"condition":"dyspnea >= 4","action":"RETURN_VISIT","notify":"FOLLOWUP_TEAM"}
                """,
            "hospital://followup/copd",
            versionId,
            now,
            "user-1",
            now,
            "user-1",
            "trace-followup-template"
        );
    }

    private AssetVersion assetVersion(
            String versionId,
            String assetIdentity,
            String versionNo,
            AssetVersionStatus status) {
        Instant now = Instant.parse("2026-06-14T00:00:00Z");
        return new AssetVersion(
            null,
            versionId,
            "tenant-1",
            VersionedAssetType.FOLLOWUP,
            assetIdentity,
            versionNo,
            "tenant:tenant-1",
            "riskLevel in [MEDIUM,HIGH]",
            "a".repeat(64),
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            status,
            "version:" + versionId,
            "hospital://followup/copd",
            null,
            null,
            now,
            "user-1",
            now,
            "user-1",
            "trace-followup-template"
        );
    }
}
