package com.medkernel.engine.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.context.ClinicalEvent;
import com.medkernel.engine.context.ClinicalEventPayload;
import com.medkernel.engine.context.ClinicalEventPayloadRepository;
import com.medkernel.engine.context.ClinicalEventRepository;
import com.medkernel.engine.context.ClinicalEventTriggerPoint;
import com.medkernel.engine.context.ClinicalEventType;
import com.medkernel.engine.context.canonical.ClinicalSetting;
import com.medkernel.engine.integration.domain.*;
import com.medkernel.engine.integration.dto.*;
import com.medkernel.engine.integration.repository.*;
import com.medkernel.engine.integration.service.IntegrationAdapterHealthProbeWorker;
import com.medkernel.engine.integration.service.IntegrationService;
import com.medkernel.engine.integration.service.WebhookSecretCodec;
import com.medkernel.engine.mpi.MpiPatient;
import com.medkernel.engine.mpi.MpiPatientRepository;
import com.medkernel.engine.terminology.TermMappingSnapshot;
import com.medkernel.engine.terminology.TermMappingSnapshotCodec;
import com.medkernel.engine.terminology.TermMappingSnapshotEntity;
import com.medkernel.engine.terminology.TermMappingSnapshotRepository;
import com.medkernel.engine.release.ReleaseEntryState;
import com.medkernel.engine.release.ReleaseSourceLayer;
import com.medkernel.engine.versioning.AssetIdentity;
import com.medkernel.engine.versioning.AssetIdentityRepository;
import com.medkernel.engine.versioning.AssetIdentityStatus;
import com.medkernel.engine.versioning.AssetVersion;
import com.medkernel.engine.versioning.AssetVersionOverridePolicy;
import com.medkernel.engine.versioning.AssetVersionRepository;
import com.medkernel.engine.versioning.AssetVersionSafetyPolicy;
import com.medkernel.engine.versioning.AssetVersionStatus;
import com.medkernel.engine.versioning.VersionedAssetType;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntegrationServiceTest {

    @Autowired
    private IntegrationService service;

    @Autowired
    private IntegrationAdapterHealthProbeWorker healthProbeWorker;

    @Autowired
    private IntegrationAdapterRepository adapterRepository;

    @Autowired
    private IntegrationWebhookConfigRepository webhookRepository;

    @Autowired
    private WebhookSecretCodec webhookSecretCodec;

    @Autowired
    private IntegrationMessageLogRepository logRepository;

    @Autowired
    private DataQualityReportRepository dataQualityReportRepository;

    @Autowired
    private IntegrationOnboardingRepository onboardingRepository;

    @Autowired
    private RegionalSourceRepository regionalSourceRepository;

    @Autowired
    private MpiPatientRepository mpiPatientRepository;

    @Autowired
    private ClinicalEventRepository clinicalEventRepository;

    @Autowired
    private ClinicalEventPayloadRepository clinicalEventPayloadRepository;

    @Autowired
    private TermMappingSnapshotRepository termMappingSnapshotRepository;

    @Autowired
    private AssetIdentityRepository assetIdentityRepository;

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String tenantId = "tenant-001";



    @Test
    void testAdapterLifecycle() {
        AdapterCreateDto createDto = new AdapterCreateDto("adp-1", "HIS集成", "HL7", "{}");
        IntegrationAdapter created = service.createAdapter(tenantId, createDto);

        assertNotNull(created.id());
        assertEquals("adp-1", created.adapterId());
        assertEquals("HIS集成", created.name());
        assertEquals("HL7", created.protocolType());
        assertEquals("ACTIVE", created.status());
        assertEquals("NOT_CONNECTED", created.healthStatus());
        assertNull(created.lastHeartbeatAt(), "新建适配器尚未健康检查，不应伪造心跳时间");

        // 获取列表
        PageResponse<IntegrationAdapter> page = service.getAdapters(tenantId, PageRequest.defaults());
        assertEquals(1, page.total());
        assertEquals(1, page.items().size());

        // 更新适配器
        AdapterUpdateDto updateDto = new AdapterUpdateDto("HIS集成系统大版本", "REST", "{}", "SUSPENDED");
        IntegrationAdapter updated = service.updateAdapter(tenantId, "adp-1", updateDto);
        assertEquals("HIS集成系统大版本", updated.name());
        assertEquals("REST", updated.protocolType());
        assertEquals("SUSPENDED", updated.status());

        // REST 缺少 baseUrl 属于配置错误，且健康检查不得覆盖原始 configJson。
        IntegrationAdapter checked = service.checkAdapterHealth(tenantId, "adp-1");
        assertEquals("MISCONFIGURED", checked.healthStatus());
        assertEquals(0L, checked.rttMs());
        assertEquals("{}", checked.configJson());
        assertFalse(checked.configJson().contains("dataQuality"));
    }

    @Test
    void adapterCodeIsUniqueOnlyInsideTenantScope() {
        IntegrationAdapter tenantOne = service.createAdapter(tenantId,
            new AdapterCreateDto("his-core", "一院 HIS", "HL7", "{}"));
        IntegrationAdapter tenantTwo = service.createAdapter("tenant-002",
            new AdapterCreateDto("his-core", "二院 HIS", "HL7", "{}"));

        assertEquals("tenant-001", tenantOne.tenantId());
        assertEquals("tenant-002", tenantTwo.tenantId());
        assertEquals(1, service.getAdapters(tenantId, PageRequest.defaults()).total());
        assertEquals(1, service.getAdapters("tenant-002", PageRequest.defaults()).total());

        assertThrows(ApiException.class, () ->
            service.createAdapter(tenantId, new AdapterCreateDto("his-core", "重复 HIS", "REST", "{}")));
    }

    @Test
    void healthSummaryIsTenantScopedAndNeverFakesConnectivity() {
        service.createAdapter(tenantId,
            new AdapterCreateDto("his-ok", "HIS 合法配置", "HL7", "{\"host\":\"his.local\"}"));
        service.createAdapter(tenantId,
            new AdapterCreateDto("lis-bad", "LIS 非法配置", "REST", "{bad-json"));
        service.createAdapter("tenant-002",
            new AdapterCreateDto("his-other", "其他租户 HIS", "HL7", "{\"host\":\"other.local\"}"));

        service.checkAdapterHealth(tenantId, "his-ok");
        service.checkAdapterHealth(tenantId, "lis-bad");
        service.checkAdapterHealth("tenant-002", "his-other");

        AdapterHealthSummaryDto summary = service.getAdapterHealthSummary(tenantId);

        assertEquals(2, summary.total());
        assertEquals(2, summary.active());
        assertEquals(0, summary.healthy());
        assertEquals(1, summary.notConnected());
        assertEquals(1, summary.misconfigured());
        assertEquals(2, summary.adapters().size());
        assertTrue(summary.adapters().stream().noneMatch(item -> "his-other".equals(item.adapterId())));
        assertTrue(summary.adapters().stream().allMatch(item -> item.message().contains("不伪造")));
    }

    @Test
    void healthCheckHonestlyReportsConfigStateNeverFakesHealthy() {
        // 配置合法 → NOT_CONNECTED（外部可达性未知，绝不伪造 HEALTHY）
        service.createAdapter(tenantId, new AdapterCreateDto("adp-ok", "LIS集成", "HL7", "{\"host\":\"lis.local\"}"));
        IntegrationAdapter okCheck = service.checkAdapterHealth(tenantId, "adp-ok");
        assertEquals("NOT_CONNECTED", okCheck.healthStatus());
        assertNotEquals("HEALTHY", okCheck.healthStatus());
        assertEquals(0L, okCheck.rttMs());
        assertTrue(okCheck.configJson().contains("lis.local"), "配置原值应被保留，不被体检报告覆盖");

        // 配置非法 → MISCONFIGURED
        service.createAdapter(tenantId, new AdapterCreateDto("adp-bad", "坏配置", "REST", "{not-json"));
        IntegrationAdapter badCheck = service.checkAdapterHealth(tenantId, "adp-bad");
        assertEquals("MISCONFIGURED", badCheck.healthStatus());
    }

    @Test
    void dataQualityReportUsesTenantFactsAndPersistsHonestGapSnapshot() {
        mpiPatientRepository.save(new MpiPatient(
            null, "mpi-quality-ok", tenantId, "张*三", "M", 35, "1234", 0, "ACTIVE",
            null, Instant.now(), "test", Instant.now(), "test"
        ));
        mpiPatientRepository.save(new MpiPatient(
            null, "mpi-quality-gap", tenantId, "", "F", 0, "", 0, "ACTIVE",
            null, Instant.now(), "test", Instant.now(), "test"
        ));
        mpiPatientRepository.save(new MpiPatient(
            null, "mpi-quality-other", "tenant-002", "", "M", 0, "", 0, "ACTIVE",
            null, Instant.now(), "test", Instant.now(), "test"
        ));
        service.createAdapter(tenantId, new AdapterCreateDto("his-quality", "HIS 质量接入", "Webhook", """
            {"fieldMappings":[{"sourcePath":"/patientId","targetPath":"/patient/id"}]}
            """));
        service.createAdapter(tenantId, new AdapterCreateDto("lis-quality", "LIS 未映射", "Webhook", "{}"));
        service.createAdapter("tenant-002", new AdapterCreateDto("his-quality-other", "其他租户 HIS", "Webhook", "{}"));
        service.checkAdapterHealth(tenantId, "his-quality");

        DataQualityReport report = service.generateDataQualityReport(tenantId);
        AdapterHubStatus status = service.getAdapterHubStatus(tenantId);

        assertNotNull(report.reportId());
        assertEquals(tenantId, report.tenantId());
        assertEquals(8, report.requiredFieldTotal());
        assertEquals(5, report.requiredFieldPresent());
        assertEquals(62.5, report.requiredFieldRate(), 0.01);
        assertEquals(2, report.adapterTotal());
        assertEquals(1, report.mappedAdapterCount());
        assertEquals(50.0, report.mappingRate(), 0.01);
        assertEquals(1, report.timelyAdapterCount());
        assertEquals(50.0, report.timelinessRate(), 0.01);
        assertTrue(report.gapSummary().contains("必填字段缺口"));
        assertTrue(report.gapSummary().contains("未配置字段映射"));
        assertTrue(dataQualityReportRepository.findById(report.reportId()).isPresent());

        assertEquals(2, status.totalAdapters());
        assertEquals(1, status.mappedAdapters());
        assertEquals(1, status.notConnectedAdapters());
        assertEquals(0, status.healthyAdapters());
        assertTrue(status.sources().stream().noneMatch(item -> "his-quality-other".equals(item.adapterId())));
        assertTrue(status.sources().stream().anyMatch(item ->
            "lis-quality".equals(item.adapterId()) && item.gaps().contains("未配置字段映射")));
    }

    @Test
    void adapterHubStatusIncludesAllRequiredSystemFamiliesWithoutFakingMissingConnections() {
        service.createAdapter(tenantId, new AdapterCreateDto("his-required", "一院 HIS 主数据", "Webhook", """
            {"fieldMappings":[{"sourcePath":"/patientId","targetPath":"/patient/id"}]}
            """));
        service.createIntegrationOnboarding(tenantId, new IntegrationOnboardingCreateRequest(
            "onb-his-required",
            "HIS 主数据接入",
            "ADAPTER",
            "his-required",
            null,
            "HIS_EMR_CDR",
            "HIS",
            "S2 院内系统接入",
            "/t-1/group-a/hospital-a",
            null
        ));
        service.createAdapter(tenantId, new AdapterCreateDto("pacs-required", "PACS 非必接", "Webhook", "{}"));

        AdapterHubStatus status = service.getAdapterHubStatus(tenantId);

        assertEquals(List.of(
                "HIS_EMR_CDR",
                "LIS_MONITORING_CRITICAL",
                "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG",
                "PHARMACY_REVIEW",
                "NURSING_ANESTHESIA_TRANSFUSION_ICU",
                "MEDICAL_RECORD_INSURANCE_PAYMENT",
                "PUBLIC_HEALTH_INFECTION_REGULATORY",
                "FOLLOWUP_PATIENT_SERVICE",
                "CA_OIDC_SSO_HR",
                "REGIONAL_REMOTE",
                "SPD_UDI_DEVICE",
                "RESEARCH_ETHICS_DATA",
                "MODEL_DIFY_AGENT"
            ),
            status.requiredSources().stream().map(AdapterHubRequiredSourceStatus::systemFamilyCode).toList());
        AdapterHubRequiredSourceStatus his = status.requiredSources().stream()
            .filter(item -> "HIS_EMR_CDR".equals(item.systemFamilyCode()))
            .findFirst()
            .orElseThrow();
        AdapterHubRequiredSourceStatus lis = status.requiredSources().stream()
            .filter(item -> "LIS_MONITORING_CRITICAL".equals(item.systemFamilyCode()))
            .findFirst()
            .orElseThrow();
        AdapterHubRequiredSourceStatus pacs = status.requiredSources().stream()
            .filter(item -> "PACS_RIS_PATHOLOGY_ENDOSCOPY_ECG".equals(item.systemFamilyCode()))
            .findFirst()
            .orElseThrow();
        AdapterHubRequiredSourceStatus model = status.requiredSources().stream()
            .filter(item -> "MODEL_DIFY_AGENT".equals(item.systemFamilyCode()))
            .findFirst()
            .orElseThrow();

        assertEquals("HIS", his.sourceSystem());
        assertEquals("his-required", his.adapterId());
        assertEquals("BOUND", his.status());
        assertEquals("NOT_CONNECTED", his.healthStatus());
        assertEquals(1, his.mappedFieldCount());
        assertFalse(his.ready());
        assertTrue(his.gaps().contains("未连接真实外部系统"));

        assertNull(lis.adapterId());
        assertEquals("LIS_MONITORING_CRITICAL", lis.sourceSystem());
        assertEquals("MISSING", lis.status());
        assertEquals("NOT_CONNECTED", lis.healthStatus());
        assertFalse(lis.ready());
        assertTrue(lis.gaps().stream().anyMatch(gap -> gap.contains("LIS、监护与危急值")));
        assertEquals("PACS/RIS、超声、病理、内镜、心电", pacs.label());

        assertNull(model.adapterId());
        assertEquals("MISSING", model.status());
        assertTrue(model.gaps().stream().anyMatch(gap -> gap.contains("模型服务、Dify 和 Agent")));
    }

    @Test
    void requiredSystemFamilyChecklistDoesNotInferCoverageFromAdapterName() {
        service.createAdapter(tenantId, new AdapterCreateDto(
            "this-statistics",
            "院内统计系统",
            "Webhook",
            "{}"
        ));

        AdapterHubStatus status = service.getAdapterHubStatus(tenantId);

        AdapterHubRequiredSourceStatus his = status.requiredSources().stream()
            .filter(item -> "HIS_EMR_CDR".equals(item.systemFamilyCode()))
            .findFirst()
            .orElseThrow();
        assertNull(his.adapterId());
        assertEquals("MISSING", his.status());
        assertTrue(his.gaps().stream().anyMatch(gap -> gap.contains("HIS、EMR、CDR")));
    }

    @Test
    void onboardingLifecycleComposesAdapterAndFhirRoutesWithoutFakingConnectivity() {
        service.createAdapter(tenantId, new AdapterCreateDto("his-business", "一院 HIS 业务接口", "Webhook", """
            {"fieldMappings":[{"sourcePath":"/patientId","targetPath":"/patient/id"}]}
            """));
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-business", "业务回调", "http://one.local/callback", "PATIENT_EVENT"));

        IntegrationOnboardingResponse adapterOnboarding = service.createIntegrationOnboarding(tenantId,
            new IntegrationOnboardingCreateRequest(
                "onb-adapter-1",
                "一院 HIS 业务接口接入",
                "ADAPTER",
                "his-business",
                null,
                "HIS_EMR_CDR",
                "HIS",
                "S2 院内系统接入",
                "/t-1/group-a/hospital-a",
                "whk-business"
            ));
        IntegrationOnboardingResponse fhirOnboarding = service.createIntegrationOnboarding(tenantId,
            new IntegrationOnboardingCreateRequest(
                "onb-fhir-1",
                "区域平台 FHIR 接入",
                "FHIR",
                null,
                "R4",
                "REGIONAL_REMOTE",
                "REGIONAL_FHIR",
                "S40 区域共享",
                "/t-1/group-a/hospital-a",
                null
            ));

        assertEquals("REQUESTED", adapterOnboarding.status());
        assertEquals("HIS_EMR_CDR", adapterOnboarding.systemFamilyCode());
        assertEquals("his-business", adapterOnboarding.adapterId());
        assertEquals("ADAPTER", adapterOnboarding.routeType());
        assertEquals("/api/v1/engine/integration/adapters/his-business", adapterOnboarding.routeReference());
        assertEquals("NOT_CONNECTED", adapterOnboarding.healthStatus());
        assertTrue(adapterOnboarding.blockers().contains("未完成鉴权配置"));
        assertEquals("FHIR", fhirOnboarding.routeType());
        assertNull(fhirOnboarding.adapterId());
        assertEquals("REGIONAL_REMOTE", fhirOnboarding.systemFamilyCode());
        assertEquals("/api/v1/engine/integration/fhir/R4", fhirOnboarding.routeReference());

        service.advanceIntegrationOnboarding(tenantId, "onb-adapter-1",
            new IntegrationOnboardingAdvanceRequest("AUTH_CONFIGURED", "接口凭证已通过配置中心登记"));
        service.advanceIntegrationOnboarding(tenantId, "onb-adapter-1",
            new IntegrationOnboardingAdvanceRequest("MAPPING_CONFIGURED", "字段映射已确认"));
        IntegrationOnboardingResponse online = service.advanceIntegrationOnboarding(tenantId, "onb-adapter-1",
            new IntegrationOnboardingAdvanceRequest("ONLINE", "联调完成后上线业务接口"));

        assertEquals("ONLINE", online.status());
        assertEquals("his-business", online.adapterId());
        assertEquals("NOT_CONNECTED", online.healthStatus(), "上线状态不应伪造外部连接成功");
        assertTrue(online.blockers().stream().anyMatch(item -> item.contains("NOT_CONNECTED")));
        assertTrue(online.blockers().stream().anyMatch(item -> item.contains("外部连接器待配置或外部不可达")));
        assertTrue(online.blockers().stream().noneMatch(item -> item.contains("未接入真实外部连接器")));
        PageResponse<IntegrationOnboardingResponse> onboardings =
            service.listIntegrationOnboardings(tenantId, PageRequest.defaults());
        assertEquals(2, onboardings.total());
        assertTrue(onboardings.items().stream().anyMatch(item ->
            "onb-adapter-1".equals(item.onboardingId()) && "his-business".equals(item.adapterId())));
    }

    @Test
    void onboardingRequiresCanonicalThirdPartySystemFamily() {
        service.createAdapter(tenantId, new AdapterCreateDto("custom-business", "未归类院内接口", "Webhook", """
            {"fieldMappings":[{"sourcePath":"/patientId","targetPath":"/patient/id"}]}
            """));

        ApiException missing = assertThrows(ApiException.class, () ->
            service.createIntegrationOnboarding(tenantId, new IntegrationOnboardingCreateRequest(
                "onb-missing-family",
                "未归类业务接口接入",
                "ADAPTER",
                "custom-business",
                null,
                "",
                "CUSTOM",
                "S2 院内系统接入",
                "/t-1/group-a/hospital-a",
                null
            )));
        assertEquals("ENG-INTEG-001", missing.errorCode().code());
        assertTrue(missing.getMessage().contains("第三方系统族"));

        ApiException unknown = assertThrows(ApiException.class, () ->
            service.createIntegrationOnboarding(tenantId, new IntegrationOnboardingCreateRequest(
                "onb-unknown-family",
                "未知业务接口接入",
                "ADAPTER",
                "custom-business",
                null,
                "CUSTOM_OTHER",
                "CUSTOM",
                "S2 院内系统接入",
                "/t-1/group-a/hospital-a",
                null
            )));
        assertEquals("ENG-INTEG-001", unknown.errorCode().code());
        assertTrue(unknown.getMessage().contains("第三方系统族"));
    }

    @Test
    void onboardingRejectsMappingStageWhenAdapterHasNoFieldMappings() {
        service.createAdapter(tenantId, new AdapterCreateDto("lis-without-mapping", "LIS 未映射", "Webhook", "{}"));
        service.createIntegrationOnboarding(tenantId, new IntegrationOnboardingCreateRequest(
            "onb-map-gap",
            "LIS 接入缺映射",
            "ADAPTER",
            "lis-without-mapping",
            null,
            "LIS_MONITORING_CRITICAL",
            "LIS",
            "S2 院内系统接入",
            "/t-1/group-a/hospital-a",
            null
        ));
        service.advanceIntegrationOnboarding(tenantId, "onb-map-gap",
            new IntegrationOnboardingAdvanceRequest("AUTH_CONFIGURED", "已配置凭证"));

        ApiException error = assertThrows(ApiException.class, () ->
            service.advanceIntegrationOnboarding(tenantId, "onb-map-gap",
                new IntegrationOnboardingAdvanceRequest("MAPPING_CONFIGURED", "尝试绕过字段映射")));

        assertEquals("ENG-INTEG-001", error.errorCode().code());
        assertTrue(error.getMessage().contains("字段映射"));
    }

    @Test
    void regionalSourceRequiresTrustGradeAndStoresCrossOrganizationEvidence() {
        service.createAdapter(tenantId, new AdapterCreateDto("his-business", "一院 HIS 业务接口", "Webhook", """
            {"fieldMappings":[{"sourcePath":"/patientId","targetPath":"/patient/id"}]}
            """));

        ApiException ungraded = assertThrows(ApiException.class, () ->
            service.registerRegionalSource(tenantId, new RegionalSourceRegisterRequest(
                "regional-missing-grade",
                "医联体平台",
                "org-region-1",
                "上级医院影像中心",
                "",
                "影像互认共享通道",
                "his-business",
                "onb-adapter-1",
                "/t-1/group-a/hospital-a"
            )));

        assertEquals("REGIONAL_SOURCE_UNGRADED", ungraded.errorCode().code());

        RegionalSourceResponse trusted = service.registerRegionalSource(tenantId, new RegionalSourceRegisterRequest(
            "regional-graded",
            "医联体平台",
            "org-region-1",
            "上级医院影像中心",
            "HIGH",
            "OPT-07 已分级来源证据",
            "his-business",
            null,
            "/t-1/group-a/hospital-a"
        ));
        service.registerRegionalSource("tenant-002", new RegionalSourceRegisterRequest(
            "regional-other",
            "外部区域平台",
            "org-region-2",
            "其他医院",
            "MEDIUM",
            "其他租户证据",
            null,
            null,
            "/t-2/group-b/hospital-b"
        ));

        assertEquals("HIGH", trusted.trustLevel());
        assertEquals("上级医院影像中心", trusted.sourceOrganizationName());
        assertEquals(1, service.listRegionalSources(tenantId, PageRequest.defaults()).items().size());
        assertEquals(1, regionalSourceRepository.findAllByTenantId(tenantId).size());
    }

    @Test
    void adapterHubMaintenanceListsUseTenantScopedPagesInsteadOfArraySnapshots() {
        service.createAdapter(tenantId, new AdapterCreateDto("his-business", "一院 HIS 业务接口", "Webhook", """
            {"fieldMappings":[{"sourcePath":"/patientId","targetPath":"/patient/id"}]}
            """));
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-first", "一号回调", "http://one.local/callback", "PATIENT_EVENT"));
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-second", "二号回调", "http://two.local/callback", "LAB_RESULT"));
        service.createIntegrationOnboarding(tenantId, new IntegrationOnboardingCreateRequest(
            "onb-first",
            "一号接入申请",
            "ADAPTER",
            "his-business",
            null,
            "HIS_EMR_CDR",
            "HIS",
            "S2 院内系统接入",
            "/t-1/group-a/hospital-a",
            "whk-first"
        ));
        service.createIntegrationOnboarding(tenantId, new IntegrationOnboardingCreateRequest(
            "onb-second",
            "二号接入申请",
            "FHIR",
            null,
            "R4",
            "REGIONAL_REMOTE",
            "REGIONAL_FHIR",
            "S40 区域共享",
            "/t-1/group-a/hospital-a",
            null
        ));
        service.registerRegionalSource(tenantId, new RegionalSourceRegisterRequest(
            "regional-first",
            "医联体平台",
            "org-region-1",
            "上级医院影像中心",
            "HIGH",
            "OPT-07 已分级来源证据",
            "his-business",
            null,
            "/t-1/group-a/hospital-a"
        ));
        service.registerRegionalSource(tenantId, new RegionalSourceRegisterRequest(
            "regional-second",
            "区域检验互认平台",
            "org-region-2",
            "市二院",
            "MEDIUM",
            "区域互认协议与接口验收单",
            null,
            null,
            "/t-1/group-a/hospital-a"
        ));
        service.createWebhook("tenant-002",
            new WebhookCreateDto("whk-other", "其他租户回调", "http://other.local/callback", "LAB_RESULT"));

        PageResponse<IntegrationOnboardingResponse> onboardingPage =
            service.listIntegrationOnboardings(tenantId, new PageRequest(2, 1, null));
        PageResponse<WebhookConfigResponse> webhookPage =
            service.getWebhooks(tenantId, new PageRequest(2, 1, null));
        PageResponse<RegionalSourceResponse> regionalPage =
            service.listRegionalSources(tenantId, new PageRequest(2, 1, null));

        assertEquals(2, onboardingPage.total());
        assertEquals(2, onboardingPage.page());
        assertEquals(1, onboardingPage.items().size());
        assertEquals(2, webhookPage.total());
        assertEquals(2, webhookPage.page());
        assertEquals(1, webhookPage.items().size());
        assertTrue(webhookPage.items().stream().noneMatch(item -> "whk-other".equals(item.webhookId())));
        assertEquals(2, regionalPage.total());
        assertEquals(2, regionalPage.page());
        assertEquals(1, regionalPage.items().size());
    }

    @Test
    void periodicHealthProbeScansActiveAdaptersAcrossTenantsWithoutFakingHealthy() {
        service.createAdapter(tenantId,
            new AdapterCreateDto("his-periodic", "一院 HIS", "HL7", "{\"host\":\"his.local\"}"));
        service.createAdapter(tenantId,
            new AdapterCreateDto("lis-periodic-bad", "一院 LIS 坏配置", "REST", "{bad-json"));
        service.createAdapter("tenant-002",
            new AdapterCreateDto("emr-periodic", "二院 EMR", "FHIR",
                "{\"baseUrl\":\"http://127.0.0.1:1\",\"requestTimeoutMs\":200}"));
        service.createAdapter("tenant-002",
            new AdapterCreateDto("pacs-suspended", "二院 PACS 暂停", "DICOM", "{bad-json"));
        service.updateAdapter("tenant-002", "pacs-suspended",
            new AdapterUpdateDto("二院 PACS 暂停", "DICOM", "{bad-json", "SUSPENDED"));

        IntegrationHealthProbeResultDto result = healthProbeWorker.probeOnce();

        assertEquals(3, result.total());
        assertEquals(0, result.healthy());
        assertEquals(2, result.notConnected());
        assertEquals(1, result.misconfigured());
        assertEquals(3, result.adapters().size());
        assertTrue(result.adapters().stream().anyMatch(item ->
            "tenant-001".equals(item.tenantId()) && "his-periodic".equals(item.adapterId())));
        assertTrue(result.adapters().stream().anyMatch(item ->
            "tenant-002".equals(item.tenantId()) && "emr-periodic".equals(item.adapterId())));
        assertTrue(result.adapters().stream().noneMatch(item -> "pacs-suspended".equals(item.adapterId())));
        assertTrue(result.adapters().stream().allMatch(item -> item.lastHeartbeatAt() != null));
        assertTrue(result.adapters().stream().anyMatch(item ->
            "his-periodic".equals(item.adapterId()) && item.rttMs() == 0L));
        assertTrue(result.adapters().stream().anyMatch(item ->
            "emr-periodic".equals(item.adapterId()) && item.rttMs() > 0L));
        assertTrue(result.adapters().stream().noneMatch(item -> "HEALTHY".equals(item.healthStatus())));
        IntegrationAdapter suspended = adapterRepository.findByAdapterIdAndTenantId("pacs-suspended", "tenant-002")
            .orElseThrow();
        assertNull(suspended.lastHeartbeatAt(), "暂停适配器不应被周期探活改写心跳");
        assertEquals("NOT_CONNECTED", suspended.healthStatus(), "暂停适配器不应因坏配置被周期任务改为 MISCONFIGURED");
    }

    @Test
    void testAdapterCreateConflict() {
        AdapterCreateDto createDto = new AdapterCreateDto("adp-1", "HIS集成", "HL7", "{}");
        service.createAdapter(tenantId, createDto);

        assertThrows(ApiException.class, () -> {
            service.createAdapter(tenantId, createDto);
        });
    }

    @Test
    void testWebhookLifecycleAndSignature() {
        WebhookCreateDto createDto = new WebhookCreateDto("whk-1", "出院回执", "http://localhost/callback", "OUTPATIENT_DIAGNOSIS");
        WebhookCreateResponse created = service.createWebhook(tenantId, createDto);

        assertNotNull(created.id());
        assertEquals("whk-1", created.webhookId());
        assertTrue(created.sharedSecret().startsWith("whsec_"));

        IntegrationWebhookConfig persisted = webhookRepository.findByWebhookIdAndTenantId("whk-1", tenantId)
            .orElseThrow();
        assertTrue(persisted.secretCipher().startsWith("sm4:v1:"));
        assertFalse(persisted.secretCipher().contains(created.sharedSecret()));
        assertEquals(created.sharedSecret(), webhookSecretCodec.decode(persisted.secretCipher()));

        List<WebhookConfigResponse> webhooks = service.getWebhooks(tenantId, PageRequest.defaults()).items();
        assertEquals(1, webhooks.size());
        String listJson = assertDoesNotThrow(() -> objectMapper.writeValueAsString(webhooks));
        assertFalse(listJson.contains("secret"));

        // 仅生成本地签名预览，不伪造外部网络连通成功。
        WebhookTestDto testDto = new WebhookTestDto("whk-1", "{\"patientId\":\"P-101\"}");
        WebhookTestResultDto signResult = service.testWebhookSignature(tenantId, testDto);
        assertEquals("SIGNATURE_GENERATED", signResult.status());
        assertEquals("NOT_TESTED", signResult.connectionStatus());
        assertNotNull(signResult.signature());
        assertNotNull(signResult.timestamp());
        String testJson = assertDoesNotThrow(() -> objectMapper.writeValueAsString(signResult));
        assertFalse(testJson.contains("secret"));
        assertFalse(testJson.contains("patientId"));
    }

    @Test
    void webhookIdIsUniqueOnlyInsideTenantScope() {
        WebhookCreateResponse tenantOne = service.createWebhook(tenantId,
            new WebhookCreateDto("whk-shared", "一院 Webhook", "http://one.local/callback", "LAB_RESULT"));
        WebhookCreateResponse tenantTwo = service.createWebhook("tenant-002",
            new WebhookCreateDto("whk-shared", "二院 Webhook", "http://two.local/callback", "LAB_RESULT"));

        assertEquals("whk-shared", tenantOne.webhookId());
        assertEquals("whk-shared", tenantTwo.webhookId());
        assertEquals(1, service.getWebhooks(tenantId, PageRequest.defaults()).items().size());
        assertEquals(1, service.getWebhooks("tenant-002", PageRequest.defaults()).items().size());

        assertThrows(ApiException.class, () -> service.createWebhook(tenantId,
            new WebhookCreateDto("whk-shared", "重复 Webhook", "http://duplicate.local/callback", "LAB_RESULT")));
    }

    @Test
    void inboundWebhookRejectsInvalidSignatureAndStoresFailedMessageLog() throws Exception {
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-in", "LIS 入站", "http://localhost/inbound", "LAB_RESULT"));
        WebhookInboundRequestDto inbound = inboundRequest("msg-bad-sig", "trace-bad-sig", "lis-adapter",
            "{\"patientId\":\"P-100\",\"diagnosisCode\":\"DIA-A00\"}");

        ApiException error = assertThrows(ApiException.class, () ->
            service.ingestWebhook(tenantId, "whk-in", "1780456000", "bad-signature", inbound));

        assertEquals("ENG-INTEG-004", error.errorCode().code());
        IntegrationMessageLog log = logRepository.findByMessageIdAndTenantId("msg-bad-sig", tenantId).orElseThrow();
        assertEquals("INBOUND", log.direction());
        assertEquals("Webhook", log.protocolType());
        assertEquals("FAILED", log.status());
        assertTrue(log.errorMessage().contains("Webhook 消息签名校验失败"));
        assertTrue(log.payload().contains("diagnosisCode"));
    }

    @Test
    void inboundWebhookVerifiesSignatureMapsFieldsAndNormalizesCodesByConfirmedTermMapping() throws Exception {
        Long standardTermId = insertStandardTerm("ICD-10", "A00", "霍乱");
        Long localTermId = insertLocalTerm("HIS", "DIA-A00", "本院霍乱诊断");
        Long mappingId = insertConfirmedTermMapping(localTermId, standardTermId, "HIS");
        TerminologyRuntimeAsset terminologyV1 = insertPublishedTerminologyAssetVersion(
            "TERM.DIAGNOSIS.V1", "H1", mappingId, localTermId, standardTermId, "A00");
        insertPublishedTerminologyAssetVersion(
            "TERM.DIAGNOSIS.V2", "H2", mappingId + 1000, localTermId, standardTermId, "B00");
        String runtimeReleaseId = "runtime-release-terminology-v1";
        insertCurrentRuntimeRelease(runtimeReleaseId, terminologyV1);

        service.createAdapter(tenantId, new AdapterCreateDto("lis-adapter", "LIS 入站适配器", "Webhook",
            """
            {
              "fieldMappings": [
                {"sourcePath": "/patientId", "targetPath": "/patient/mpi"},
                {
                  "sourcePath": "/dialysisAccessType",
                  "targetPath": "/extensions/local/dialysis_access_type"
                },
                {
                  "sourcePath": "/diagnosisCode",
                  "targetPath": "/diagnoses/0",
                  "targetDictionaryKey": "ICD-10",
                  "category": "DIAGNOSIS"
                }
              ]
            }
            """));
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-map", "LIS 入站", "http://localhost/inbound", "LAB_RESULT"));
        IntegrationWebhookConfig webhook = webhookRepository.findByWebhookIdAndTenantId("whk-map", tenantId)
            .orElseThrow();
        WebhookInboundRequestDto inbound = inboundRequest(
            "msg-map-1", "trace-map-1", "lis-adapter",
            """
            {
              "patientId": "P-100",
              "dialysisAccessType": "AV_FISTULA",
              "diagnosisCode": "DIA-A00"
            }
            """);
        String timestamp = currentTimestamp();
        String signature = signInbound(webhookSecretCodec.decode(webhook.secretCipher()), timestamp, inbound);

        WebhookInboundResultDto result;
        try {
            result = RequestContext.callWith(
                new RequestContext.Snapshot(
                    "trace-map-1",
                    new OrgScope(tenantId, null, "hospital-001", null, null, null, null, null),
                    "integration-test"),
                () -> service.ingestWebhook(tenantId, "whk-map", timestamp, signature, inbound));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }

        assertEquals("SUCCESS", result.status());
        assertFalse(result.idempotentReplay());
        assertEquals(3, result.mappedFieldCount());
        assertEquals(1, result.normalizedCodeCount());
        assertEquals("P-100", result.mappedPayload().at("/patient/mpi").asText());
        assertEquals(
            "AV_FISTULA",
            result.mappedPayload().at("/extensions/local/dialysis_access_type").asText());
        assertEquals("ICD-10", result.mappedPayload().at("/diagnoses/0/codeSystem").asText());
        assertEquals("A00", result.mappedPayload().at("/diagnoses/0/standardCode").asText());
        assertEquals("DIA-A00", result.mappedPayload().at("/diagnoses/0/localCode").asText());
        assertEquals("H1", result.mappedPayload().at("/diagnoses/0/mappedVersion").asText());
        assertNotNull(result.clinicalEventId());
        assertEquals("RECEIVED", result.clinicalEventStatus());

        ClinicalEvent clinicalEvent = clinicalEventRepository
            .findByEventIdAndTenantId(result.clinicalEventId(), tenantId)
            .orElseThrow();
        assertEquals(ClinicalEventType.REPORT, clinicalEvent.eventType());
        assertEquals(ClinicalEventTriggerPoint.RESULT_REVIEW, clinicalEvent.triggerPoint());
        assertEquals("P-100", clinicalEvent.patientId());
        assertEquals("encounter-100", clinicalEvent.encounterId());
        assertEquals(ClinicalSetting.OUTPATIENT, clinicalEvent.clinicalSetting());
        assertEquals(runtimeReleaseId, clinicalEvent.runtimeReleaseId());

        ClinicalEventPayload clinicalPayload = clinicalEventPayloadRepository
            .findByEventIdAndTenantId(result.clinicalEventId(), tenantId)
            .orElseThrow();
        JsonNode persistedPayload = objectMapper.readTree(clinicalPayload.payload());
        assertEquals("P-100", persistedPayload.at("/patient/mpi").asText());
        assertEquals(
            "AV_FISTULA",
            persistedPayload.at("/extensions/local/dialysis_access_type").asText());
        assertEquals("A00", persistedPayload.at("/diagnoses/0/standardCode").asText());

        IntegrationMessageLog log = logRepository.findByMessageIdAndTenantId("msg-map-1", tenantId).orElseThrow();
        assertEquals("SUCCESS", log.status());
        assertTrue(log.payload().contains("\"mappedPayload\""));
        assertTrue(log.payload().contains("\"clinicalEventId\""));
        assertTrue(log.payloadSummary().contains("映射字段 3"));

        WebhookInboundResultDto replay;
        try {
            replay = RequestContext.callWith(
                new RequestContext.Snapshot("trace-map-1", OrgScope.tenant(tenantId), "integration-test"),
                () -> service.ingestWebhook(tenantId, "whk-map", timestamp, signature, inbound));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        assertEquals("SUCCESS", replay.status());
        assertTrue(replay.idempotentReplay());
        assertEquals(result.clinicalEventId(), replay.clinicalEventId());
        assertEquals("RECEIVED", replay.clinicalEventStatus());
        assertEquals(1, logRepository.countByTenantId(tenantId));
    }

    @Test
    void inboundWebhookRejectsStaleTimestampEvenWhenSignatureMatches() throws Exception {
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-stale", "LIS 入站", "http://localhost/inbound", "LAB_RESULT"));
        IntegrationWebhookConfig webhook = webhookRepository.findByWebhookIdAndTenantId("whk-stale", tenantId).orElseThrow();
        WebhookInboundRequestDto inbound = inboundRequest("msg-stale", "trace-stale", "lis-adapter",
            "{\"patientId\":\"P-100\"}");
        String staleTimestamp = String.valueOf(Instant.now().minus(Duration.ofMinutes(10)).getEpochSecond());
        String signature = signInbound(webhookSecretCodec.decode(webhook.secretCipher()), staleTimestamp, inbound);

        ApiException error = assertThrows(ApiException.class, () ->
            service.ingestWebhook(tenantId, "whk-stale", staleTimestamp, signature, inbound));

        assertEquals("ENG-INTEG-004", error.errorCode().code());
        IntegrationMessageLog log = logRepository.findByMessageIdAndTenantId("msg-stale", tenantId).orElseThrow();
        assertEquals("FAILED", log.status());
        assertTrue(log.errorMessage().contains("Webhook 消息签名校验失败"));
    }

    @Test
    void inboundWebhookStoresFailedLogWhenFieldMappingConfigurationIsInvalid() throws Exception {
        service.createAdapter(tenantId, new AdapterCreateDto("lis-bad-map", "LIS 坏映射适配器", "Webhook", "{}"));
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-bad-map", "LIS 入站", "http://localhost/inbound", "LAB_RESULT"));
        IntegrationWebhookConfig webhook = webhookRepository.findByWebhookIdAndTenantId("whk-bad-map", tenantId)
            .orElseThrow();
        WebhookInboundRequestDto inbound = inboundRequest("msg-bad-map", "trace-bad-map", "lis-bad-map",
            "{\"patientId\":\"P-100\"}");
        String timestamp = currentTimestamp();
        String signature = signInbound(webhookSecretCodec.decode(webhook.secretCipher()), timestamp, inbound);

        ApiException error = assertThrows(ApiException.class, () ->
            service.ingestWebhook(tenantId, "whk-bad-map", timestamp, signature, inbound));

        assertEquals("ENG-INTEG-001", error.errorCode().code());
        IntegrationMessageLog log = logRepository.findByMessageIdAndTenantId("msg-bad-map", tenantId).orElseThrow();
        assertEquals("FAILED", log.status());
        assertTrue(log.errorMessage().contains("适配器未配置字段映射"));
    }

    @Test
    void inboundWebhookRejectsMutableTermMappingReference() throws Exception {
        service.createAdapter(tenantId, new AdapterCreateDto(
            "lis-mutable-term-map",
            "LIS 可变术语映射适配器",
            "Webhook",
            """
            {
              "fieldMappings": [
                {
                  "sourcePath": "/diagnosisCode",
                  "targetPath": "/diagnoses/0",
                  "termMappingId": 501
                }
              ]
            }
            """
        ));
        service.createWebhook(tenantId,
            new WebhookCreateDto("whk-mutable-term-map", "LIS 入站", "http://localhost/inbound", "LAB_RESULT"));
        IntegrationWebhookConfig webhook = webhookRepository
            .findByWebhookIdAndTenantId("whk-mutable-term-map", tenantId)
            .orElseThrow();
        WebhookInboundRequestDto inbound = inboundRequest(
            "msg-mutable-term-map",
            "trace-mutable-term-map",
            "lis-mutable-term-map",
            "{\"diagnosisCode\":\"DIA-A00\"}"
        );
        String timestamp = currentTimestamp();
        String signature = signInbound(webhookSecretCodec.decode(webhook.secretCipher()), timestamp, inbound);

        ApiException error = assertThrows(ApiException.class, () ->
            service.ingestWebhook(tenantId, "whk-mutable-term-map", timestamp, signature, inbound));

        assertEquals("ENG-INTEG-001", error.errorCode().code());
        assertTrue(error.getMessage().contains("不得引用可变 termMappingId"));
        IntegrationMessageLog log = logRepository
            .findByMessageIdAndTenantId("msg-mutable-term-map", tenantId)
            .orElseThrow();
        assertEquals("FAILED", log.status());
        assertTrue(log.errorMessage().contains("不得引用可变 termMappingId"));
    }

    @Test
    void inboundMessageIdempotencyIsScopedByTenant() {
        logRepository.save(new IntegrationMessageLog(
            null,
            "shared-message",
            tenantId,
            "trace-tenant-1",
            "INBOUND",
            "HIS",
            "Webhook",
            "tenant-1",
            "{}",
            "SUCCESS",
            0,
            3,
            null,
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        ));

        assertDoesNotThrow(() -> logRepository.save(new IntegrationMessageLog(
            null,
            "shared-message",
            "tenant-002",
            "trace-tenant-2",
            "INBOUND",
            "HIS",
            "Webhook",
            "tenant-2",
            "{}",
            "SUCCESS",
            0,
            3,
            null,
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        )));
    }

    @Test
    void testMessageLogsAndRetry() {
        // 先手动插入一条失败的消息日志，原始报文 payload 非空
        IntegrationMessageLog log = new IntegrationMessageLog(
            null,
            "msg-1",
            tenantId,
            "trace-1",
            "OUTBOUND",
            "EMR",
            "REST",
            "summary",
            "payload",
            "FAILED",
            0,
            3,
            "error",
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        );
        logRepository.save(log);

        List<IntegrationMessageLog> logsPage = service.getMessageLogs(tenantId, 0, 10);
        assertEquals(1, logsPage.size());
        assertEquals("msg-1", logsPage.get(0).messageId());

        // 历史裸字符串不是标准出站信封，不再兼容猜测目标或载荷。
        IntegrationMessageLog retried = service.retryMessage(tenantId, "msg-1");
        assertEquals(1, retried.retryCount());
        assertEquals("FAILED", retried.status());
        assertTrue(retried.errorMessage().contains("标准出站消息"));

        // 插入一条空 payload 的失败消息日志，测试其重试失败
        IntegrationMessageLog logEmpty = new IntegrationMessageLog(
            null,
            "msg-2",
            tenantId,
            "trace-2",
            "OUTBOUND",
            "EMR",
            "REST",
            "summary",
            "",
            "FAILED",
            0,
            3,
            "error",
            Instant.now(),
            "system",
            Instant.now(),
            "system"
        );
        logRepository.save(logEmpty);

        IntegrationMessageLog retriedEmpty = service.retryMessage(tenantId, "msg-2");
        assertEquals(1, retriedEmpty.retryCount());
        assertEquals("FAILED", retriedEmpty.status());
        assertTrue(retriedEmpty.errorMessage().contains("标准出站消息"));

        // 重试次数累加到 maxRetries 时强制移入死信队列
        service.retryMessage(tenantId, "msg-2"); // retryCount = 2, FAILED
        IntegrationMessageLog retriedDead = service.retryMessage(tenantId, "msg-2"); // retryCount = 3, DEAD_LETTER
        assertEquals(3, retriedDead.retryCount());
        assertEquals("DEAD_LETTER", retriedDead.status());
        assertTrue(retriedDead.errorMessage().contains("投递重试超限"));

        // 接口日志是审计证据：重试或死信后必须保留，不能通过生产接口删除。
        assertTrue(logRepository.findByMessageIdAndTenantId("msg-1", tenantId).isPresent());
        assertTrue(logRepository.findByMessageIdAndTenantId("msg-2", tenantId).isPresent());
    }

    private WebhookInboundRequestDto inboundRequest(String messageId, String traceId, String adapterId, String payload)
            throws JsonProcessingException {
        return new WebhookInboundRequestDto(
            messageId,
            traceId,
            adapterId,
            "LIS",
            ClinicalEventType.REPORT,
            "P-100",
            "encounter-100",
            ClinicalSetting.OUTPATIENT,
            ClinicalEventTriggerPoint.RESULT_REVIEW,
            Instant.parse("2026-06-22T08:00:00Z"),
            objectMapper.readTree(payload)
        );
    }

    private String currentTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private String signInbound(String secretKey, String timestamp, WebhookInboundRequestDto request) throws Exception {
        String data = timestamp + "." + objectMapper.writeValueAsString(request);
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        sha256Hmac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder signature = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                signature.append('0');
            }
            signature.append(hex);
        }
        return signature.toString();
    }

    private TerminologyRuntimeAsset insertPublishedTerminologyAssetVersion(
            String assetIdentity,
            String versionNo,
            Long mappingId,
            Long localTermId,
            Long standardTermId,
            String standardCode) {
        Instant now = Instant.parse("2026-06-22T08:00:00Z");
        String versionId = "term-version-" + UUID.randomUUID();
        String contentHash = "a".repeat(64);
        TermMappingSnapshot snapshot = new TermMappingSnapshot(
            mappingId,
            localTermId,
            standardTermId,
            "LIS",
            "DIA-A00",
            "ICD-10",
            standardCode,
            "DIAGNOSIS",
            1.0D,
            "LOW",
            "CONFIRMED",
            "构包时确认",
            "integration-test",
            now.toString()
        );
        assetIdentityRepository.findByTenantIdAndAssetTypeAndAssetIdentity(
            tenantId,
            VersionedAssetType.TERMINOLOGY,
            assetIdentity
        ).orElseGet(() -> assetIdentityRepository.save(new AssetIdentity(
            null,
            tenantId,
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            AssetIdentityStatus.ACTIVE,
            Long.parseLong(versionNo.substring(1)),
            now,
            "integration-test",
            now,
            "integration-test",
            "trace-map-1"
        )));
        assetVersionRepository.save(new AssetVersion(
            null,
            versionId,
            tenantId,
            VersionedAssetType.TERMINOLOGY,
            assetIdentity,
            versionNo,
            "tenant:" + tenantId,
            "ALL",
            contentHash,
            AssetVersionSafetyPolicy.NORMAL,
            AssetVersionOverridePolicy.FREE,
            AssetVersionStatus.PUBLISHED,
            assetIdentity + "|tenant:" + tenantId + "|ALL",
            "terminology-version:" + versionId,
            now,
            null,
            now,
            "integration-test",
            now,
            "integration-test",
            "trace-map-1"
        ));
        termMappingSnapshotRepository.save(TermMappingSnapshotEntity.fromSnapshot(
            tenantId,
            versionId,
            mappingId,
            snapshot,
            TermMappingSnapshotCodec.write(snapshot),
            now,
            "integration-test"
        ));
        return new TerminologyRuntimeAsset(assetIdentity, versionId, versionNo, contentHash);
    }

    private void insertCurrentRuntimeRelease(String releaseId, TerminologyRuntimeAsset asset) {
        jdbcTemplate.update("""
            INSERT INTO platform_baseline_release
                (baseline_release_id, revision_no, manifest_sha256, published_at,
                 published_by, created_by, trace_id)
            VALUES ('baseline-integration-1', 1, ?, CURRENT_TIMESTAMP,
                    'integration-test', 'integration-test', 'trace-map-1')
            """, "b".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO clinical_runtime_release
                (release_id, tenant_id, hospital_id, revision_no, platform_baseline_release_id,
                 manifest_sha256, activated_at, activated_by, created_by, trace_id)
            VALUES (?, ?, 'hospital-001', 1, 'baseline-integration-1',
                    ?, CURRENT_TIMESTAMP, 'integration-test', 'integration-test', 'trace-map-1')
            """, releaseId, tenantId, "c".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO clinical_runtime_release_item
                (release_id, source_tenant_id, source_layer, asset_type, asset_identity,
                 entry_state, version_id, version_no, content_hash, created_at, created_by, trace_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'integration-test', 'trace-map-1')
            """,
            releaseId,
            tenantId,
            ReleaseSourceLayer.HOSPITAL.name(),
            VersionedAssetType.TERMINOLOGY.name(),
            asset.assetIdentity(),
            ReleaseEntryState.ACTIVE.name(),
            asset.versionId(),
            asset.versionNo(),
            asset.contentHash());
    }

    private record TerminologyRuntimeAsset(
        String assetIdentity,
        String versionId,
        String versionNo,
        String contentHash
    ) {}

    private Long insertStandardTerm(String standardSystem, String termCode, String displayName) {
        jdbcTemplate.update("""
            INSERT INTO standard_term
                (tenant_id, standard_system, term_code, category, display_name, normalized_name,
                 version_no, status, evidence_text, created_by, updated_by)
            VALUES (?, ?, ?, 'DIAGNOSIS', ?, ?, '2026', 'ACTIVE', 'TERM-01 已确认标准术语', 'test', 'test')
            """, tenantId, standardSystem, termCode, displayName, displayName.toLowerCase());
        return jdbcTemplate.queryForObject("""
            SELECT id FROM standard_term
            WHERE tenant_id = ? AND standard_system = ? AND term_code = ? AND version_no = '2026'
            """, Long.class, tenantId, standardSystem, termCode);
    }

    private Long insertLocalTerm(String sourceSystem, String localCode, String localName) {
        jdbcTemplate.update("""
            INSERT INTO local_term
                (tenant_id, source_system, local_code, category, local_name, normalized_name,
                 status, created_by, updated_by)
            VALUES (?, ?, ?, 'DIAGNOSIS', ?, ?, 'MAPPED', 'test', 'test')
            """, tenantId, sourceSystem, localCode, localName, localName.toLowerCase());
        return jdbcTemplate.queryForObject("""
            SELECT id FROM local_term
            WHERE tenant_id = ? AND source_system = ? AND local_code = ? AND category = 'DIAGNOSIS'
            """, Long.class, tenantId, sourceSystem, localCode);
    }

    private Long insertConfirmedTermMapping(Long localTermId, Long standardTermId, String sourceSystem) {
        jdbcTemplate.update("""
            INSERT INTO term_mapping
                (tenant_id, local_term_id, standard_term_id, source_system, category, confidence,
                 risk_level, status, evidence_text, confirmed_by, confirmed_at, created_by, updated_by)
            VALUES (?, ?, ?, ?, 'DIAGNOSIS', 1.0, 'LOW', 'CONFIRMED',
                    'TERM-01 已确认映射', 'test', CURRENT_TIMESTAMP, 'test', 'test')
            """, tenantId, localTermId, standardTermId, sourceSystem);
        return jdbcTemplate.queryForObject("""
            SELECT id FROM term_mapping
            WHERE tenant_id = ? AND local_term_id = ? AND standard_term_id = ? AND status = 'CONFIRMED'
            """, Long.class, tenantId, localTermId, standardTermId);
    }

    @Test
    void outboundMessageIsAcceptedAsNotConnectedWithoutBlockingMainFlow() throws Exception {
        IntegrationOutboundRequestDto outbound = new IntegrationOutboundRequestDto(
            "out-main-1",
            "trace-main-1",
            "his-missing",
            "HIS",
            "REST",
            "医生主流程异步同步 HIS",
            objectMapper.readTree("{\"patientId\":\"P-200\",\"event\":\"ORDER_SUBMITTED\"}"),
            2
        );

        IntegrationOutboundResultDto result = service.enqueueOutboundMessage(tenantId, outbound);

        assertEquals("out-main-1", result.messageId());
        assertEquals("NOT_CONNECTED", result.status());
        assertFalse(result.blocksMainFlow());
        assertTrue(result.compensationRequired());
        assertTrue(result.message().contains("不阻断主流程"));
        IntegrationMessageLog log = logRepository.findByMessageIdAndTenantId("out-main-1", tenantId).orElseThrow();
        assertEquals("OUTBOUND", log.direction());
        assertEquals("NOT_CONNECTED", log.status());
        assertEquals(0, log.retryCount());
        assertEquals(2, log.maxRetries());
        assertTrue(log.errorMessage().contains("补偿消息"));
        assertTrue(log.payload().contains("ORDER_SUBMITTED"));
    }

    @Test
    void deadLettersAreTenantScopedAndReplayCreatesCompensationLogWithoutDeletingEvidence() throws Exception {
        String sourceMessageId = "out-dead-" + "x".repeat(55);
        IntegrationOutboundResultDto queued = service.enqueueOutboundMessage(tenantId, new IntegrationOutboundRequestDto(
            sourceMessageId,
            "trace-dead-1",
            "his-missing",
            "HIS",
            "REST",
            "同步 HIS 失败待死信",
            objectMapper.readTree("{\"patientId\":\"P-201\"}"),
            1
        ));
        service.enqueueOutboundMessage("tenant-002", new IntegrationOutboundRequestDto(
            "out-dead-other",
            "trace-dead-other",
            "his-missing",
            "HIS",
            "REST",
            "其他租户同步失败待死信",
            objectMapper.readTree("{\"patientId\":\"P-202\"}"),
            1
        ));

        IntegrationMessageLog dead = service.retryMessage(tenantId, queued.messageId());
        service.retryMessage("tenant-002", "out-dead-other");
        List<IntegrationMessageLog> deadLetters = service.getDeadLetters(tenantId, 0, 10);
        IntegrationReplayResultDto replay = service.replayDeadLetter(tenantId, dead.messageId());

        assertEquals("DEAD_LETTER", dead.status());
        assertEquals(1, deadLetters.size());
        assertEquals(sourceMessageId, deadLetters.get(0).messageId());
        assertEquals(sourceMessageId, replay.sourceMessageId());
        assertTrue(replay.replayMessageId().startsWith("replay-"));
        assertTrue(replay.replayMessageId().length() <= 64);
        assertEquals("NOT_CONNECTED", replay.status());
        assertFalse(replay.blocksMainFlow());
        assertTrue(logRepository.findByMessageIdAndTenantId(sourceMessageId, tenantId).isPresent(),
            "原始死信必须保留为审计证据，不能因回放被删除");
        IntegrationMessageLog replayLog = logRepository.findByMessageIdAndTenantId(replay.replayMessageId(), tenantId)
            .orElseThrow();
        assertEquals("NOT_CONNECTED", replayLog.status());
        assertEquals(0, replayLog.retryCount());
        assertTrue(replayLog.payloadSummary().contains("死信人工重放"));
    }
}
