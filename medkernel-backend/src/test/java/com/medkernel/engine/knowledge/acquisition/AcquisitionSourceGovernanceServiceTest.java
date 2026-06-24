package com.medkernel.engine.knowledge.acquisition;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.knowledge.SourceAuthorityLevel;
import com.medkernel.engine.knowledge.SourceType;
import com.medkernel.engine.knowledge.parsing.DocumentFormat;
import com.medkernel.engine.knowledge.production.KnowledgeDomain;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/** AIK-STD-14 公域来源配置、启用与停用治理单元测试。 */
class AcquisitionSourceGovernanceServiceTest {

    private KnowledgeAcquisitionSourceRepository repository;
    private AuditRecorder auditRecorder;
    private AcquisitionSourceGovernanceService service;

    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeAcquisitionSourceRepository.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new AcquisitionSourceGovernanceService(
            repository, auditRecorder, new ObjectMapper().findAndRegisterModules());
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-source-governance", OrgScope.tenant("t-1"), "knowledge-editor"));
        when(repository.save(any(KnowledgeAcquisitionSource.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void savingDraftNormalizesSourceAndCanNeverEnableIt() {
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.empty());

        KnowledgeAcquisitionSource saved = service.saveDraft(" nhc-guideline ", draft());

        assertThat(saved.sourceCode()).isEqualTo("NHC-GUIDELINE");
        assertThat(saved.domain()).isEqualTo("www.nhc.gov.cn");
        assertThat(saved.baseUrl()).isEqualTo("https://www.nhc.gov.cn/");
        assertThat(saved.enabledFlag()).isEqualTo("N");
        assertThat(saved.createdBy()).isEqualTo("knowledge-editor");
        verify(auditRecorder).record(AuditAction.CREATE, "mk_knowledge_acquisition_source",
            "NHC-GUIDELINE", "登记公域来源停用配置 NHC-GUIDELINE");
    }

    @Test
    void materialDraftUpdateDisablesSourceUntilExplicitlyEnabledAgain() {
        KnowledgeAcquisitionSource current = source(
            "Y", "original-editor", "knowledge-editor", "Y");
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.of(current));

        KnowledgeAcquisitionSource saved = service.saveDraft("NHC-GUIDELINE", draft());

        assertThat(saved.id()).isEqualTo(11L);
        assertThat(saved.enabledFlag()).isEqualTo("N");
        assertThat(saved.createdBy()).isEqualTo("original-editor");
        assertThat(saved.updatedBy()).isEqualTo("knowledge-editor");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_knowledge_acquisition_source",
            "NHC-GUIDELINE", "更新公域来源停用配置 NHC-GUIDELINE");
    }

    @Test
    void draftRejectsHttpOrMismatchedDomain() {
        AcquisitionSourceDraftRequest http = draftWith("www.nhc.gov.cn", "http://www.nhc.gov.cn/");
        AcquisitionSourceDraftRequest mismatch = draftWith("www.nhc.gov.cn", "https://example.org/");

        assertThatThrownBy(() -> service.saveDraft("NHC-GUIDELINE", http))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> service.saveDraft("NHC-GUIDELINE", mismatch))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("域名");
        verify(repository, never()).save(any());
    }

    @Test
    void draftRejectsIpLiteralAndInternalDomain() {
        AcquisitionSourceDraftRequest ipLiteral = draftWith("127.0.0.1", "https://127.0.0.1/");
        AcquisitionSourceDraftRequest internal = draftWith("knowledge.internal", "https://knowledge.internal/");

        assertThatThrownBy(() -> service.saveDraft("NHC-GUIDELINE", ipLiteral))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("公开完整域名");
        assertThatThrownBy(() -> service.saveDraft("NHC-GUIDELINE", internal))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("内部网络");
        verify(repository, never()).save(any());
    }

    @Test
    void draftRejectsInvalidGenerationPlanAndOversizedDatabaseFields() {
        AcquisitionSourceDraftRequest invalidPlan = new AcquisitionSourceDraftRequest(
            "www.nhc.gov.cn", "https://www.nhc.gov.cn/", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "国家级官方来源", "公开指南", "国家卫生健康委",
            "公开访问", AcquisitionLicensePolicy.PERMITTED, AcquisitionRobotsPolicy.ALLOW_FETCH,
            false, null, null,
            new AcquisitionCandidateGenerationRequest(null, KnowledgeDomain.GENERAL, List.of()));
        AcquisitionSourceDraftRequest oversizedTitle = new AcquisitionSourceDraftRequest(
            "www.nhc.gov.cn", "https://www.nhc.gov.cn/", SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE, "国家级官方来源", "超".repeat(513), "国家卫生健康委",
            "公开访问", AcquisitionLicensePolicy.PERMITTED, AcquisitionRobotsPolicy.ALLOW_FETCH,
            false, null, null, null);

        assertThatThrownBy(() -> service.saveDraft("NHC-GUIDELINE", invalidPlan))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("候选生成计划");
        assertThatThrownBy(() -> service.saveDraft("NHC-GUIDELINE", oversizedTitle))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("来源标题长度");
        verify(repository, never()).save(any());
    }

    @Test
    void sameActorCanEnableValidatedSourceWithoutHighRiskApproval() {
        KnowledgeAcquisitionSource current = source("N", "knowledge-editor", "knowledge-editor", "N");
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.of(current));

        KnowledgeAcquisitionSource enabled = service.enable("NHC-GUIDELINE");

        assertThat(enabled.enabledFlag()).isEqualTo("Y");
        assertThat(enabled.updatedBy()).isEqualTo("knowledge-editor");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_knowledge_acquisition_source",
            "NHC-GUIDELINE", "启用公域来源 NHC-GUIDELINE");
    }

    @Test
    void enableValidSourceSchedulesFirstCheck() {
        KnowledgeAcquisitionSource current = source("N", "knowledge-editor", "knowledge-editor", "Y");
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.of(current));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-source-enable", OrgScope.tenant("t-1"), "engine-operator"));

        KnowledgeAcquisitionSource enabled = service.enable("NHC-GUIDELINE");

        assertThat(enabled.enabledFlag()).isEqualTo("Y");
        assertThat(enabled.nextCheckAt()).isNotNull();
        assertThat(enabled.updatedBy()).isEqualTo("engine-operator");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_knowledge_acquisition_source",
            "NHC-GUIDELINE", "启用公域来源 NHC-GUIDELINE");
    }

    @Test
    void repeatedEnableIsIdempotent() {
        KnowledgeAcquisitionSource enabled = source(
            "Y", "knowledge-editor", "engine-operator", "Y");
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.of(enabled));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-source-enable", OrgScope.tenant("t-1"), "engine-operator"));

        assertThat(service.enable("NHC-GUIDELINE")).isSameAs(enabled);
        verify(repository, never()).save(any());
        verify(auditRecorder, never()).record(any(), any(), any(), any());
    }

    @Test
    void enableRejectsUnlicensedOrDisallowedSource() {
        KnowledgeAcquisitionSource restricted = new KnowledgeAcquisitionSource(
            11L, "t-1", "NHC-GUIDELINE", "www.nhc.gov.cn", "https://www.nhc.gov.cn/",
            SourceType.GUIDELINE, SourceAuthorityLevel.B_GUIDELINE, "国家级官方来源",
            "公开指南", "国家卫生健康委", "待确认许可", AcquisitionLicensePolicy.RESTRICTED,
            AcquisitionRobotsPolicy.DISALLOW_FETCH, "N", "N", null, null, null,
            DocumentFormat.STRUCTURED_TEXT, null, Instant.EPOCH, "knowledge-editor",
            Instant.EPOCH, "knowledge-editor", 0L);
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.of(restricted));
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-source-enable", OrgScope.tenant("t-1"), "engine-operator"));

        assertThatThrownBy(() -> service.enable("NHC-GUIDELINE"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("许可");
        verify(repository, never()).save(any());
    }

    @Test
    void disableStopsSourceAndSchedule() {
        KnowledgeAcquisitionSource current = source(
            "Y", "knowledge-editor", "engine-operator", "Y");
        when(repository.findByTenantIdAndSourceCode("t-1", "NHC-GUIDELINE"))
            .thenReturn(Optional.of(current));

        KnowledgeAcquisitionSource disabled = service.disable("NHC-GUIDELINE");

        assertThat(disabled.enabledFlag()).isEqualTo("N");
        assertThat(disabled.scheduleEnabledFlag()).isEqualTo("N");
        verify(auditRecorder).record(AuditAction.UPDATE, "mk_knowledge_acquisition_source",
            "NHC-GUIDELINE", "停用公域来源 NHC-GUIDELINE");
    }

    private static AcquisitionSourceDraftRequest draft() {
        return draftWith(" WWW.NHC.GOV.CN ", "https://www.nhc.gov.cn");
    }

    private static AcquisitionSourceDraftRequest draftWith(String domain, String baseUrl) {
        return new AcquisitionSourceDraftRequest(
            domain,
            baseUrl,
            SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE,
            "国家级官方来源",
            "公开指南",
            "国家卫生健康委",
            "公开访问，仅内部留存原件用于锚点和审计",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            true,
            1440,
            DocumentFormat.STRUCTURED_TEXT,
            null);
    }

    private static KnowledgeAcquisitionSource source(String enabled,
                                                     String createdBy,
                                                     String updatedBy,
                                                     String scheduleEnabled) {
        return new KnowledgeAcquisitionSource(
            11L,
            "t-1",
            "NHC-GUIDELINE",
            "www.nhc.gov.cn",
            "https://www.nhc.gov.cn/",
            SourceType.GUIDELINE,
            SourceAuthorityLevel.B_GUIDELINE,
            "国家级官方来源",
            "公开指南",
            "国家卫生健康委",
            "公开访问，仅内部留存原件用于锚点和审计",
            AcquisitionLicensePolicy.PERMITTED,
            AcquisitionRobotsPolicy.ALLOW_FETCH,
            enabled,
            scheduleEnabled,
            1440,
            null,
            null,
            DocumentFormat.STRUCTURED_TEXT,
            null,
            Instant.EPOCH,
            createdBy,
            Instant.EPOCH,
            updatedBy,
            0L);
    }
}
