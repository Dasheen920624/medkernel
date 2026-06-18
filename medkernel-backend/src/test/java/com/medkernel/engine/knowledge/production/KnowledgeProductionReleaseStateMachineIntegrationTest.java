package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import com.medkernel.engine.llm.ModelCapabilityPolicy;
import com.medkernel.engine.llm.ModelCapabilityPolicyRepository;
import com.medkernel.engine.llm.ModelVersionBundle;
import com.medkernel.engine.llm.ModelVersionBundleRepository;
import com.medkernel.engine.llm.egress.ModelEgressWhitelistRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionCase;
import com.medkernel.engine.llm.eval.MedicalRegressionCaseRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionEvaluator;
import com.medkernel.engine.llm.eval.ModelEvalCaseEvidence;
import com.medkernel.engine.llm.eval.ModelEvalCaseEvidenceRepository;
import com.medkernel.engine.llm.eval.ModelEvalRun;
import com.medkernel.engine.llm.eval.ModelEvalRunRepository;
import com.medkernel.engine.llm.eval.ModelEvalService;
import com.medkernel.engine.llm.eval.ModelEvalSignOffRequest;
import com.medkernel.engine.llm.eval.RegressionBaselineEvidence;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
import com.medkernel.engine.llm.provider.ModelProviderActivationRequest;
import com.medkernel.engine.llm.provider.ModelProviderConfig;
import com.medkernel.engine.llm.provider.ModelProviderConfigRepository;
import com.medkernel.engine.llm.provider.ModelProviderGovernanceService;
import com.medkernel.engine.llm.provider.ModelProviderGovernanceView;
import com.medkernel.engine.llm.provider.ModelProviderRegistry;
import com.medkernel.engine.security.RoleCode;
import com.medkernel.engine.security.SpringSecurityPrivilegedConfigChangeGuard;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.audit.AuditSafetyGuard;
import com.medkernel.shared.audit.IsolatedAuditPublisher;
import com.medkernel.shared.config.HighRiskChangeGuard;
import com.medkernel.shared.config.RuntimeLogLevelManager;
import com.medkernel.shared.config.SystemConfigRepository;
import com.medkernel.shared.config.SystemConfigSeed;
import com.medkernel.shared.config.SystemConfigSeedWriter;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.config.SystemConfigUpdateRequest;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 正式知识生产放行状态机集成测试。
 *
 * <p>测试只使用隔离 H2 数据库，锁定评测逐例证据、独立签署、能力级 provider 启用、
 * P6 特权放行与九项 readiness 的真实关系库状态迁移，不连接或修改任何部署环境。
 */
@DataJdbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:knowledge-release-${random.uuid};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2"
})
class KnowledgeProductionReleaseStateMachineIntegrationTest {

    private static final String SYSTEM_TENANT = "SYSTEM";
    private static final String TENANT = "tenant-release-it";
    private static final String PROVIDER = "ollama-release-it";
    private static final String MODEL_VERSION = "qwen-release-v1";
    private static final String CAPABILITY = "rule.draft";
    private static final String OTHER_CAPABILITY = "pathway.draft";

    @Autowired
    private MedicalRegressionCaseRepository caseRepository;

    @Autowired
    private ModelEvalRunRepository evalRunRepository;

    @Autowired
    private ModelEvalCaseEvidenceRepository evidenceRepository;

    @Autowired
    private ModelProviderConfigRepository providerRepository;

    @Autowired
    private ModelCapabilityPolicyRepository policyRepository;

    @Autowired
    private ModelEgressWhitelistRepository whitelistRepository;

    @Autowired
    private ModelVersionBundleRepository versionBundleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DeploymentFormService deploymentFormService;
    private ModelEvalService evalService;
    private ModelProviderGovernanceService providerGovernanceService;
    private KnowledgeProductionReadinessService readinessService;
    private SystemConfigService configService;
    private SystemConfigRepository systemConfigRepository;

    @BeforeEach
    void setUp() {
        systemConfigRepository = new SystemConfigRepository(jdbcTemplate);
        deploymentFormService = mock(DeploymentFormService.class);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.HOSPITAL_RUNTIME);

        AuditRecorder auditRecorder = mock(AuditRecorder.class);
        HighRiskChangeGuard highRiskChangeGuard = mock(HighRiskChangeGuard.class);
        ModelProviderRegistry providerRegistry = mock(ModelProviderRegistry.class);
        evalService = new ModelEvalService(
            caseRepository,
            evalRunRepository,
            evidenceRepository,
            mock(MedicalRegressionEvaluator.class),
            providerRegistry,
            auditRecorder,
            highRiskChangeGuard);
        providerGovernanceService = new ModelProviderGovernanceService(
            providerRepository,
            deploymentFormService,
            evalService,
            providerRegistry,
            auditRecorder,
            highRiskChangeGuard);

        configService = new SystemConfigService(
            systemConfigRepository,
            mock(AuditSafetyGuard.class),
            auditRecorder,
            mock(IsolatedAuditPublisher.class),
            mock(RuntimeLogLevelManager.class),
            highRiskChangeGuard,
            new SpringSecurityPrivilegedConfigChangeGuard(),
            new SystemConfigSeedWriter(systemConfigRepository));
        readinessService = new KnowledgeProductionReadinessService(
            configService,
            deploymentFormService,
            providerRepository,
            caseRepository,
            evalRunRepository,
            whitelistRepository,
            policyRepository,
            versionBundleRepository);

        seedConfig(
            SystemConfigService.KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY,
            "s3://mk/platform-knowledge/release-it/literature-materials/",
            "STRING",
            "平台知识文献资料库根地址",
            "MEDIUM",
            false);
        seedConfig(
            SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY,
            "false",
            "BOOLEAN",
            "P6 正式知识生产独立验收",
            "HIGH",
            true);
        authenticate("ops-release-it", RoleCode.INTEGRATION_OPERATOR);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void signedEvaluationProviderP6AndReadinessFollowFailClosedStateMachine() {
        Instant now = Instant.parse("2026-06-18T12:00:00Z");
        MedicalRegressionCase baseline = caseRepository.save(regressionCase(null, "必须保持人工复核", now));
        ModelProviderConfig provider = providerRepository.save(provider(now));
        ModelVersionBundle activeBundle = versionBundleRepository.save(versionBundle(MODEL_VERSION, now));
        policyRepository.save(policy(now));

        ModelEvalRun pending = evalRunRepository.save(pendingRun(baseline, now));
        evidenceRepository.save(passedEvidence(pending.id(), baseline, now));

        assertThatThrownBy(() -> providerGovernanceService.enableProvider(
            PROVIDER,
            activation(CAPABILITY, provider.version())))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(com.medkernel.shared.api.error.ErrorCode.ENG_LLM_008);

        authenticate("independent-medical-reviewer", RoleCode.QUALITY_GOVERNOR);
        ModelEvalRun signed = evalService.signOff(
            pending.id(),
            new ModelEvalSignOffRequest(true, "已逐例核对真实输出、来源引用、红线裁决及基准指纹，同意放行。"));
        assertThat(signed.status()).isEqualTo("PASSED");
        assertThat(evalRunRepository.findById(pending.id()).orElseThrow().reviewer())
            .isEqualTo("independent-medical-reviewer");

        authenticate("ops-release-it", RoleCode.INTEGRATION_OPERATOR);
        assertThatThrownBy(() -> providerGovernanceService.enableProvider(
            PROVIDER,
            activation(OTHER_CAPABILITY, provider.version())))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(com.medkernel.shared.api.error.ErrorCode.ENG_LLM_008);

        MedicalRegressionCase drifted = caseRepository.save(
            regressionCase(baseline.id(), "基准内容已变化，旧签署不得复用", now.plusSeconds(60)));
        assertThatThrownBy(() -> providerGovernanceService.enableProvider(
            PROVIDER,
            activation(CAPABILITY, provider.version())))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).errorCode())
            .isEqualTo(com.medkernel.shared.api.error.ErrorCode.ENG_LLM_008);

        baseline = caseRepository.save(
            regressionCase(drifted.id(), "必须保持人工复核", now.plusSeconds(120)));
        ModelProviderGovernanceView enabled = providerGovernanceService.enableProvider(
            PROVIDER,
            activation(CAPABILITY, provider.version()));
        assertThat(enabled.enabled()).isTrue();

        KnowledgeProductionReadinessResponse beforeP6 = readinessService.evaluate(
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            PROVIDER);
        assertThat(beforeP6.ready()).isFalse();
        assertThat(beforeP6.items()).filteredOn(KnowledgeProductionReadinessItem::ready).hasSize(8);
        assertThat(beforeP6.items()).filteredOn(item -> !item.ready())
            .extracting(KnowledgeProductionReadinessItem::code)
            .containsExactly("P6_ACCEPTANCE");

        long p6Version = systemConfigRepository.findActive(
            SYSTEM_TENANT,
            SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY)
            .orElseThrow()
            .version();
        assertThatThrownBy(() -> configService.update(
            SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY,
            new SystemConfigUpdateRequest("true", "独立验收完成，申请正式放行", p6Version, true),
            "ops-release-it"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("内置超级管理员");

        authenticate("system-superadmin-release-it", RoleCode.SYSTEM_SUPERADMIN);
        configService.update(
            SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY,
            new SystemConfigUpdateRequest("true", "独立验收完成，内置超管正式放行", p6Version, true),
            "system-superadmin-release-it");

        KnowledgeProductionReadinessResponse released = readinessService.evaluate(
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            PROVIDER);
        assertThat(released.ready()).isTrue();
        assertThat(released.modelInvocationAllowed()).isTrue();
        assertThat(released.items()).hasSize(9).allMatch(KnowledgeProductionReadinessItem::ready);

        caseRepository.save(
            regressionCase(baseline.id(), "上线后基准再次变化", now.plusSeconds(180)));
        assertBlocked(readinessService.evaluate(KnowledgeProducer.LOCAL_MODEL, CAPABILITY, PROVIDER),
            "MODEL_EVALUATION");

        caseRepository.save(
            regressionCase(baseline.id(), "必须保持人工复核", now.plusSeconds(240)));
        versionBundleRepository.save(versionBundle(
            activeBundle.id(),
            "qwen-release-v2",
            now.plusSeconds(240)));
        assertBlocked(readinessService.evaluate(KnowledgeProducer.LOCAL_MODEL, CAPABILITY, PROVIDER),
            "VERSION_TRIPLE");
    }

    private ModelProviderActivationRequest activation(String capabilityCode, Long expectedVersion) {
        return new ModelProviderActivationRequest(
            capabilityCode,
            "独立医学专家已签署，按正式状态机受控启用",
            expectedVersion,
            true);
    }

    private void assertBlocked(KnowledgeProductionReadinessResponse response, String gateCode) {
        assertThat(response.ready()).isFalse();
        assertThat(response.modelInvocationAllowed()).isFalse();
        assertThat(response.items()).filteredOn(item -> gateCode.equals(item.code()))
            .singleElement()
            .satisfies(item -> assertThat(item.ready()).isFalse());
    }

    private void authenticate(String userId, RoleCode role) {
        RequestContext.restore(new RequestContext.Snapshot(
            "trace-release-state-machine",
            OrgScope.tenant(TENANT),
            userId));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                userId,
                "N/A",
                List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    private void seedConfig(
            String key,
            String value,
            String valueType,
            String displayName,
            String risk,
            boolean protectedConfig) {
        systemConfigRepository.insertSeedIfAbsent(new SystemConfigSeed(
            SYSTEM_TENANT,
            key,
            value,
            valueType,
            displayName,
            risk,
            "平台知识治理组",
            displayName,
            "PLATFORM_SEED",
            protectedConfig,
            Instant.parse("2026-06-18T11:00:00Z")),
            "system");
    }

    private MedicalRegressionCase regressionCase(Long id, String expectedPhrase, Instant now) {
        return new MedicalRegressionCase(
            id,
            TENANT,
            CAPABILITY,
            "rule",
            "高风险知识候选是否可以绕过人工复核？",
            expectedPhrase,
            "[]",
            "[\"不得自动激活\"]",
            100,
            "HUMAN_REVIEW_REQUIRED",
            "source-version:release-it#human-review",
            "Y",
            "2026.06",
            "Y",
            now,
            "quality-author",
            now,
            "quality-author");
    }

    private ModelEvalRun pendingRun(MedicalRegressionCase baseline, Instant now) {
        return new ModelEvalRun(
            null,
            TENANT,
            PROVIDER,
            MODEL_VERSION,
            CAPABILITY,
            "prompt:release-v1",
            "tool:release-v1",
            1,
            1,
            0,
            100.0,
            100.0,
            "N",
            "N",
            "N",
            "PENDING_REVIEW",
            RegressionBaselineEvidence.toJson(List.of(baseline)),
            null,
            null,
            null,
            now,
            "quality-author",
            now,
            "quality-author");
    }

    private ModelEvalCaseEvidence passedEvidence(
            Long runId,
            MedicalRegressionCase baseline,
            Instant now) {
        return new ModelEvalCaseEvidence(
            null,
            TENANT,
            runId,
            baseline.id(),
            baseline.caseVersion(),
            baseline.caseInput(),
            baseline.expectedPhrase(),
            baseline.redLineType(),
            baseline.sourceReference(),
            "该候选必须保持人工复核，不能自动激活。",
            "[\"source-version:release-it#human-review\"]",
            "Y",
            "Y",
            "Y",
            "Y",
            "N",
            "Y",
            "[]",
            now,
            "quality-author");
    }

    private ModelProviderConfig provider(Instant now) {
        return new ModelProviderConfig(
            null,
            TENANT,
            PROVIDER,
            "OLLAMA",
            "http://127.0.0.1:11434",
            null,
            MODEL_VERSION,
            "N",
            "HEALTHY",
            now,
            "ops-release-it",
            now,
            "ops-release-it",
            null);
    }

    private ModelCapabilityPolicy policy(Instant now) {
        return new ModelCapabilityPolicy(
            null,
            TENANT,
            CAPABILITY,
            "TENANT",
            TENANT,
            "LOCAL_MODEL",
            "DEFAULT",
            null,
            "[\"LOCAL_MODEL\",\"BASELINE\"]",
            60_000,
            10,
            now,
            "ops-release-it",
            now,
            "ops-release-it");
    }

    private ModelVersionBundle versionBundle(String modelVersion, Instant now) {
        return versionBundle(null, modelVersion, now);
    }

    private ModelVersionBundle versionBundle(Long id, String modelVersion, Instant now) {
        return new ModelVersionBundle(
            id,
            TENANT,
            CAPABILITY,
            "prompt:release-v1",
            "a".repeat(64),
            "tool:release-v1",
            "b".repeat(64),
            modelVersion,
            "c".repeat(64),
            "ACTIVE",
            TENANT + "|" + CAPABILITY,
            now,
            null,
            now,
            "ops-release-it",
            now,
            "ops-release-it");
    }
}
