package com.medkernel.engine.knowledge.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.medkernel.engine.llm.ModelCapabilityPolicy;
import com.medkernel.engine.llm.ModelCapabilityPolicyRepository;
import com.medkernel.engine.llm.ModelVersionBundle;
import com.medkernel.engine.llm.ModelVersionBundleRepository;
import com.medkernel.engine.llm.egress.ModelEgressWhitelist;
import com.medkernel.engine.llm.egress.ModelEgressWhitelistRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionCase;
import com.medkernel.engine.llm.eval.MedicalRegressionCaseRepository;
import com.medkernel.engine.llm.eval.ModelEvalRun;
import com.medkernel.engine.llm.eval.ModelEvalRunRepository;
import com.medkernel.engine.llm.eval.RegressionBaselineEvidence;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
import com.medkernel.engine.llm.provider.ModelProviderConfig;
import com.medkernel.engine.llm.provider.ModelProviderConfigRepository;
import com.medkernel.shared.config.SystemConfigService;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;

/**
 * 知识生产模型 readiness 闸测试。
 *
 * <p>readiness 只聚合真实前置事实；缺文献根、缺 provider、缺评测、缺出域白名单、缺版本三元组或 P6 未验收均阻断模型生成。
 */
class KnowledgeProductionReadinessServiceTest {

    private static final String TENANT = "tenant-ready";
    private static final String CAPABILITY = "rule.draft";

    private SystemConfigService configService;
    private DeploymentFormService deploymentFormService;
    private ModelProviderConfigRepository providerRepository;
    private MedicalRegressionCaseRepository caseRepository;
    private ModelEvalRunRepository evalRunRepository;
    private ModelEgressWhitelistRepository whitelistRepository;
    private ModelCapabilityPolicyRepository policyRepository;
    private ModelVersionBundleRepository versionBundleRepository;
    private KnowledgeProductionReadinessService service;

    @BeforeEach
    void setUp() {
        configService = mock(SystemConfigService.class);
        deploymentFormService = mock(DeploymentFormService.class);
        providerRepository = mock(ModelProviderConfigRepository.class);
        caseRepository = mock(MedicalRegressionCaseRepository.class);
        evalRunRepository = mock(ModelEvalRunRepository.class);
        whitelistRepository = mock(ModelEgressWhitelistRepository.class);
        policyRepository = mock(ModelCapabilityPolicyRepository.class);
        versionBundleRepository = mock(ModelVersionBundleRepository.class);
        service = new KnowledgeProductionReadinessService(
            configService,
            deploymentFormService,
            providerRepository,
            caseRepository,
            evalRunRepository,
            whitelistRepository,
            policyRepository,
            versionBundleRepository);
        RequestContext.restore(new RequestContext.Snapshot("trace-ready", OrgScope.tenant(TENANT), "u"));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void blocksWhenAllFormalProductionPrerequisitesAreMissing() {
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri()).thenReturn("");
        when(configService.runtimeKnowledgeProductionP6IndependentAcceptance()).thenReturn(false);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.HOSPITAL_RUNTIME);
        when(providerRepository.findByTenantIdAndEnabledFlag(TENANT, "Y")).thenReturn(List.of());
        when(caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(TENANT, CAPABILITY, "Y"))
            .thenReturn(List.of());

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            null);

        assertThat(response.ready()).isFalse();
        assertThat(response.modelInvocationAllowed()).isFalse();
        assertThat(response.items()).filteredOn(item -> !item.ready())
            .extracting(KnowledgeProductionReadinessItem::code)
            .contains("LITERATURE_ROOT", "DEPLOYMENT_FORM", "MODEL_PROVIDER", "REGRESSION_BASELINE",
                "MODEL_EVALUATION", "EGRESS_GOVERNANCE", "VERSION_TRIPLE", "P6_ACCEPTANCE");
    }

    @Test
    void passesWhenExternalModelProductionPrerequisitesArePresent() {
        ModelProviderConfig provider = provider("claude-prod", "CLAUDE", "claude-opus-4");
        stubExternalPrerequisites(provider, whitelist());

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            "claude-prod");

        assertThat(response.ready()).isTrue();
        assertThat(response.modelInvocationAllowed()).isTrue();
        assertThat(response.providerCode()).isEqualTo("claude-prod");
        assertThat(response.items()).allSatisfy(item -> assertThat(item.ready()).isTrue());
    }

    @Test
    void blocksForgedVersionTripleTextWhenNoActiveVersionBundleExists() {
        ModelProviderConfig provider = provider("claude-prod", "CLAUDE", "claude-opus-4");
        stubExternalPrerequisites(provider, whitelist());
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            TENANT, CAPABILITY, "ACTIVE")).thenReturn(Optional.empty());

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            "claude-prod");

        assertThat(response.items()).filteredOn(item -> "VERSION_TRIPLE".equals(item.code()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.ready()).isFalse();
                assertThat(item.message()).contains("版本包");
            });
    }

    @Test
    void blocksMalformedEgressWhitelistInsteadOfReportingReadinessGreen() {
        ModelProviderConfig provider = provider("claude-prod", "CLAUDE", "claude-opus-4");
        Instant now = Instant.now();
        ModelEgressWhitelist malformed = new ModelEgressWhitelist(
            1L, TENANT, CAPABILITY, "not-json", "LOW", "{}", "HIGH", "Y",
            now, "u", now, "u");
        stubExternalPrerequisites(provider, malformed);

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.API_MODEL,
            CAPABILITY,
            "claude-prod");

        assertThat(response.items()).filteredOn(item -> "EGRESS_GOVERNANCE".equals(item.code()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.ready()).isFalse();
                assertThat(item.message()).contains("白名单");
            });
    }

    @Test
    void localModelDoesNotRequireExternalDeploymentOrEgressWhitelist() {
        ModelProviderConfig provider = localProvider("ollama-local", "qwen2.5:7b");
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri())
            .thenReturn("s3://mk/platform-knowledge/t-1/literature-materials/");
        when(configService.runtimeKnowledgeProductionP6IndependentAcceptance()).thenReturn(true);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.HOSPITAL_RUNTIME);
        when(providerRepository.findByTenantIdAndProviderCode(TENANT, "ollama-local")).thenReturn(Optional.of(provider));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(TENANT, CAPABILITY, "Y"))
            .thenReturn(List.of(regressionCase()));
        when(evalRunRepository
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                TENANT, "ollama-local", "qwen2.5:7b", CAPABILITY, "PASSED"))
            .thenReturn(Optional.of(evalRun(provider)));
        when(policyRepository.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            TENANT, CAPABILITY, "TENANT", TENANT))
            .thenReturn(Optional.of(policy("LOCAL_MODEL")));
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            TENANT, CAPABILITY, "ACTIVE")).thenReturn(Optional.of(versionBundle(provider.modelVersion())));

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            "ollama-local");

        assertThat(response.ready()).isTrue();
        assertThat(response.items()).filteredOn(item -> "EGRESS_GOVERNANCE".equals(item.code()))
            .singleElement()
            .satisfies(item -> assertThat(item.message()).contains("本地模型"));
    }

    @Test
    void blocksPassedEvaluationFromDifferentCapability() {
        ModelProviderConfig provider = localProvider("ollama-local", "qwen2.5:7b");
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri())
            .thenReturn("s3://mk/platform-knowledge/t-1/literature-materials/");
        when(configService.runtimeKnowledgeProductionP6IndependentAcceptance()).thenReturn(true);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(providerRepository.findByTenantIdAndProviderCode(TENANT, "ollama-local"))
            .thenReturn(Optional.of(provider));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(TENANT, CAPABILITY, "Y"))
            .thenReturn(List.of(regressionCase()));
        when(evalRunRepository
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                TENANT, "ollama-local", "qwen2.5:7b", CAPABILITY, "PASSED"))
            .thenReturn(Optional.of(evalRun(provider, "pathway.draft")));
        when(policyRepository.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            TENANT, CAPABILITY, "TENANT", TENANT))
            .thenReturn(Optional.of(policy("LOCAL_MODEL")));

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            "ollama-local");

        assertThat(response.items()).filteredOn(item -> "MODEL_EVALUATION".equals(item.code()))
            .singleElement()
            .satisfies(item -> assertThat(item.ready()).isFalse());
    }

    @Test
    void blocksPassedEvaluationWhenBaselineContentChangedWithoutChangingCaseCount() {
        ModelProviderConfig provider = localProvider("ollama-local", "qwen2.5:7b");
        MedicalRegressionCase evaluatedCase = regressionCase("旧期望");
        MedicalRegressionCase currentCase = regressionCase("新期望");
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri())
            .thenReturn("s3://mk/platform-knowledge/t-1/literature-materials/");
        when(configService.runtimeKnowledgeProductionP6IndependentAcceptance()).thenReturn(true);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(providerRepository.findByTenantIdAndProviderCode(TENANT, "ollama-local"))
            .thenReturn(Optional.of(provider));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(TENANT, CAPABILITY, "Y"))
            .thenReturn(List.of(currentCase));
        when(evalRunRepository
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                TENANT, "ollama-local", "qwen2.5:7b", CAPABILITY, "PASSED"))
            .thenReturn(Optional.of(evalRun(provider, CAPABILITY, List.of(evaluatedCase))));
        when(policyRepository.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            TENANT, CAPABILITY, "TENANT", TENANT))
            .thenReturn(Optional.of(policy("LOCAL_MODEL")));

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            "ollama-local");

        assertThat(response.items()).filteredOn(item -> "MODEL_EVALUATION".equals(item.code()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.ready()).isFalse();
                assertThat(item.message()).contains("基准集");
            });
    }

    @Test
    void blocksUnknownProviderTypeEvenForLocalProducer() {
        ModelProviderConfig provider = provider("private-box", "PRIVATE_BOX", "mk-local-v1");
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri())
            .thenReturn("s3://mk/platform-knowledge/t-1/literature-materials/");
        when(configService.runtimeKnowledgeProductionP6IndependentAcceptance()).thenReturn(true);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.HOSPITAL_RUNTIME);
        when(providerRepository.findByTenantIdAndProviderCode(TENANT, "private-box")).thenReturn(Optional.of(provider));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(TENANT, CAPABILITY, "Y"))
            .thenReturn(List.of(regressionCase()));
        when(evalRunRepository
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                TENANT, "private-box", "mk-local-v1", CAPABILITY, "PASSED"))
            .thenReturn(Optional.of(evalRun(provider)));
        when(policyRepository.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            TENANT, CAPABILITY, "TENANT", TENANT))
            .thenReturn(Optional.of(policy("LOCAL_MODEL")));

        KnowledgeProductionReadinessResponse response = service.evaluate(
            KnowledgeProducer.LOCAL_MODEL,
            CAPABILITY,
            "private-box");

        assertThat(response.ready()).isFalse();
        assertThat(response.items()).filteredOn(item -> "MODEL_PROVIDER".equals(item.code()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.ready()).isFalse();
                assertThat(item.message()).contains("类型无效");
            });
    }

    private ModelProviderConfig provider(String code, String type, String modelVersion) {
        Instant now = Instant.now();
        return new ModelProviderConfig(
            1L, TENANT, code, type, "https://model.example/v1", "secret-ref",
            modelVersion, "Y", "HEALTHY", now, "u", now, "u", 0L);
    }

    private void stubExternalPrerequisites(ModelProviderConfig provider, ModelEgressWhitelist whitelist) {
        when(configService.runtimeKnowledgeLiteratureMaterialRootUri())
            .thenReturn("s3://mk/platform-knowledge/t-1/literature-materials/");
        when(configService.runtimeKnowledgeProductionP6IndependentAcceptance()).thenReturn(true);
        when(deploymentFormService.currentForm()).thenReturn(DeploymentForm.PRODUCTION_CENTER);
        when(providerRepository.findByTenantIdAndProviderCode(TENANT, provider.providerCode()))
            .thenReturn(Optional.of(provider));
        when(caseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(TENANT, CAPABILITY, "Y"))
            .thenReturn(List.of(regressionCase()));
        when(evalRunRepository
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                TENANT, provider.providerCode(), provider.modelVersion(), CAPABILITY, "PASSED"))
            .thenReturn(Optional.of(evalRun(provider)));
        when(whitelistRepository.findByTenantIdAndCapabilityCode(TENANT, CAPABILITY))
            .thenReturn(Optional.of(whitelist));
        when(policyRepository.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
            TENANT, CAPABILITY, "TENANT", TENANT))
            .thenReturn(Optional.of(policy("EXTERNAL_MODEL")));
        when(versionBundleRepository.findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(
            TENANT, CAPABILITY, "ACTIVE")).thenReturn(Optional.of(versionBundle(provider.modelVersion())));
    }

    private ModelProviderConfig localProvider(String code, String modelVersion) {
        Instant now = Instant.now();
        return new ModelProviderConfig(
            1L, TENANT, code, "OLLAMA", "http://127.0.0.1:11434", null,
            modelVersion, "Y", "HEALTHY", now, "u", now, "u", 0L);
    }

    private ModelVersionBundle versionBundle(String modelVersion) {
        Instant now = Instant.now();
        return new ModelVersionBundle(
            1L, TENANT, CAPABILITY,
            "prompt:v1", "a".repeat(64),
            "tool:v1", "b".repeat(64),
            modelVersion, "c".repeat(64),
            "ACTIVE", TENANT + "|" + CAPABILITY, now, null, now, "u", now, "u");
    }

    private MedicalRegressionCase regressionCase() {
        return regressionCase("期望");
    }

    private MedicalRegressionCase regressionCase(String expectedPhrase) {
        Instant now = Instant.now();
        return new MedicalRegressionCase(
            1L, TENANT, CAPABILITY, "general", "输入", expectedPhrase, "[]", "[]", 100,
            null, "source-version:1", "Y",
            "2026.06", "Y", now, "u", now, "u");
    }

    private ModelEvalRun evalRun(ModelProviderConfig provider) {
        return evalRun(provider, CAPABILITY);
    }

    private ModelEvalRun evalRun(ModelProviderConfig provider, String capabilityCode) {
        return evalRun(provider, capabilityCode, List.of(regressionCase()));
    }

    private ModelEvalRun evalRun(ModelProviderConfig provider,
                                 String capabilityCode,
                                 List<MedicalRegressionCase> evaluatedCases) {
        Instant now = Instant.now();
        return new ModelEvalRun(
            1L, TENANT, provider.providerCode(), provider.modelVersion(),
            capabilityCode, "prompt:v1", "tool:v1",
            1, 1, 0, 100.0, 100.0, "N", "N", "N", "PASSED",
            RegressionBaselineEvidence.toJson(evaluatedCases),
            "逐例证据已核查并确认可放行。", "reviewer", now, now, "u", now, "u");
    }

    private ModelEgressWhitelist whitelist() {
        Instant now = Instant.now();
        return new ModelEgressWhitelist(1L, TENANT, CAPABILITY, "[\"prompt\"]", "LOW", now, "u", now, "u");
    }

    private ModelCapabilityPolicy policy(String strategy) {
        Instant now = Instant.now();
        return new ModelCapabilityPolicy(
            1L, TENANT, CAPABILITY, "TENANT", TENANT, strategy, "DEFAULT", null,
            null, null, null, now, "u", now, "u");
    }
}
