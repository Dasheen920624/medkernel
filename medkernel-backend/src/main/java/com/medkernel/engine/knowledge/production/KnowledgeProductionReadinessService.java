package com.medkernel.engine.knowledge.production;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medkernel.engine.llm.ModelCapabilityPolicy;
import com.medkernel.engine.llm.ModelCapabilityPolicyRepository;
import com.medkernel.engine.llm.ModelPolicyScope;
import com.medkernel.engine.llm.ModelVersionBundle;
import com.medkernel.engine.llm.ModelVersionBundleRepository;
import com.medkernel.engine.llm.ModelVersionBundleValidator;
import com.medkernel.engine.llm.egress.ModelEgressPolicyValidator;
import com.medkernel.engine.llm.egress.ModelEgressWhitelist;
import com.medkernel.engine.llm.egress.ModelEgressWhitelistRepository;
import com.medkernel.engine.llm.eval.MedicalRegressionCase;
import com.medkernel.engine.llm.eval.MedicalRegressionCaseRepository;
import com.medkernel.engine.llm.eval.ModelEvalRun;
import com.medkernel.engine.llm.eval.ModelEvalRunRepository;
import com.medkernel.engine.llm.eval.ModelEvalService;
import com.medkernel.engine.llm.eval.RegressionBaselineEvidence;
import com.medkernel.engine.llm.provider.DeploymentForm;
import com.medkernel.engine.llm.provider.DeploymentFormService;
import com.medkernel.engine.llm.provider.ModelProviderConfig;
import com.medkernel.engine.llm.provider.ModelProviderConfigRepository;
import com.medkernel.engine.llm.provider.ModelProviderCredentialRepository;
import com.medkernel.engine.llm.provider.ProviderType;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.config.SystemConfigService;

/**
 * 正式模型生成知识前置 readiness 闸（AIK-STD-13/LLM-01/02/04）。
 *
 * <p>本服务只聚合关系库和配置中心事实，不调用模型、不创建候选。任一强前置缺失时返回结构化阻断，
 * 模型生产器必须据此停止真实模型调用并返回诚实阻断，禁止静默回退为非模型候选。
 */
@Service
public class KnowledgeProductionReadinessService {

    public static final String DEFAULT_CAPABILITY_CODE = MedicalRegressionCase.DEFAULT_CAPABILITY_CODE;

    private final SystemConfigService configService;
    private final DeploymentFormService deploymentFormService;
    private final ModelProviderConfigRepository providerRepository;
    private final ModelProviderCredentialRepository credentialRepository;
    private final MedicalRegressionCaseRepository regressionCaseRepository;
    private final ModelEvalRunRepository evalRunRepository;
    private final ModelEvalService evalService;
    private final ModelEgressWhitelistRepository egressWhitelistRepository;
    private final ModelCapabilityPolicyRepository policyRepository;
    private final ModelVersionBundleRepository versionBundleRepository;

    public KnowledgeProductionReadinessService(SystemConfigService configService,
                                               DeploymentFormService deploymentFormService,
                                               ModelProviderConfigRepository providerRepository,
                                               ModelProviderCredentialRepository credentialRepository,
                                               MedicalRegressionCaseRepository regressionCaseRepository,
                                               ModelEvalRunRepository evalRunRepository,
                                               ModelEvalService evalService,
                                               ModelEgressWhitelistRepository egressWhitelistRepository,
                                               ModelCapabilityPolicyRepository policyRepository,
                                               ModelVersionBundleRepository versionBundleRepository) {
        this.configService = configService;
        this.deploymentFormService = deploymentFormService;
        this.providerRepository = providerRepository;
        this.credentialRepository = credentialRepository;
        this.regressionCaseRepository = regressionCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalService = evalService;
        this.egressWhitelistRepository = egressWhitelistRepository;
        this.policyRepository = policyRepository;
        this.versionBundleRepository = versionBundleRepository;
    }

    /** 评估当前租户是否可进入真实模型知识生产。 */
    @Transactional(readOnly = true)
    public KnowledgeProductionReadinessResponse evaluate(KnowledgeProducer producer,
                                                         String capabilityCode,
                                                         String providerCode) {
        String tenantId = requireCurrentTenant();
        KnowledgeProducer targetProducer = producer == null ? KnowledgeProducer.API_MODEL : producer;
        String capability = normalizeCapability(capabilityCode);
        DeploymentForm deploymentForm = deploymentFormService.currentForm();
        Optional<ModelProviderConfig> provider = resolveProvider(tenantId, targetProducer, providerCode);
        List<KnowledgeProductionReadinessItem> items = new ArrayList<>();
        items.add(literatureRootItem());
        items.add(deploymentItem(targetProducer, deploymentForm));
        items.add(providerItem(tenantId, targetProducer, provider));
        List<MedicalRegressionCase> cases =
            regressionCaseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(tenantId, capability, "Y");
        items.add(regressionBaselineItem(capability, cases));
        items.add(evaluationItem(tenantId, capability, provider, cases));
        items.add(egressItem(tenantId, capability, provider));
        items.add(policyItem(tenantId, capability, targetProducer));
        items.add(versionTripleItem(tenantId, capability, provider.orElse(null)));
        items.add(p6AcceptanceItem());
        return new KnowledgeProductionReadinessResponse(
            tenantId,
            targetProducer,
            capability,
            provider.map(ModelProviderConfig::providerCode).orElse(null),
            deploymentForm,
            false,
            false,
            items);
    }

    private KnowledgeProductionReadinessItem literatureRootItem() {
        String rootUri = configService.runtimeKnowledgeLiteratureMaterialRootUri();
        if (rootUri == null || rootUri.isBlank()) {
            return KnowledgeProductionReadinessItem.block(
                "LITERATURE_ROOT",
                "平台知识文献资料库根地址未配置，禁止正式模型生成知识",
                SystemConfigService.KNOWLEDGE_LITERATURE_MATERIAL_ROOT_URI_KEY + "=<empty>");
        }
        return KnowledgeProductionReadinessItem.pass(
            "LITERATURE_ROOT",
            "平台知识文献资料库根地址已配置",
            rootUri.trim());
    }

    private KnowledgeProductionReadinessItem deploymentItem(KnowledgeProducer producer, DeploymentForm form) {
        if (producer == KnowledgeProducer.LOCAL_MODEL) {
            return KnowledgeProductionReadinessItem.pass(
                "DEPLOYMENT_FORM",
                "本地模型生产器允许在当前部署形态下运行",
                "deploymentForm=" + form);
        }
        if (form == DeploymentForm.PRODUCTION_CENTER) {
            return KnowledgeProductionReadinessItem.pass(
                "DEPLOYMENT_FORM",
                "外部 API 模型生产仅在知识生产中心形态启用",
                "deploymentForm=" + form);
        }
        return KnowledgeProductionReadinessItem.block(
            "DEPLOYMENT_FORM",
            "当前不是 PRODUCTION_CENTER，禁止外部 API 模型生产知识",
            "deploymentForm=" + form);
    }

    private KnowledgeProductionReadinessItem providerItem(String tenantId,
                                                          KnowledgeProducer producer,
                                                          Optional<ModelProviderConfig> provider) {
        if (provider.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "未找到匹配的模型 provider",
                "producer=" + producer);
        }
        ModelProviderConfig config = provider.get();
        Optional<ProviderType> type = providerType(config);
        if (type.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "provider 类型无效，不能进入模型知识生产",
                config.providerCode() + "/" + config.providerType());
        }
        ProviderType providerType = type.get();
        boolean typeMatches = producer == KnowledgeProducer.LOCAL_MODEL ? !providerType.external() : providerType.external();
        if (!typeMatches) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "provider 类型与生产器不匹配",
                config.providerCode() + "/" + config.providerType());
        }
        if (!config.enabled() || !"HEALTHY".equalsIgnoreCase(config.status())) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "模型 provider 未启用或健康状态不是 HEALTHY",
                config.providerCode() + " status=" + config.status());
        }
        boolean vaultCredentialConfigured = credentialRepository
            .findByTenantIdAndProviderCode(tenantId, config.providerCode())
            .isPresent();
        boolean credentialMissing = providerType.external() && !vaultCredentialConfigured;
        if (blank(config.endpointUri()) || credentialMissing || blank(config.modelVersion())) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                providerType.external()
                    ? "外部模型 provider 缺端点、租户凭据或模型版本"
                    : "本地模型 provider 缺端点或模型版本",
                config.providerCode());
        }
        return KnowledgeProductionReadinessItem.pass(
            "MODEL_PROVIDER",
            "模型 provider 已启用且健康",
            config.providerCode() + "/" + config.modelVersion());
    }

    private KnowledgeProductionReadinessItem regressionBaselineItem(String capability,
                                                                    List<MedicalRegressionCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "REGRESSION_BASELINE",
                "医学回归基准集为空，禁止正式模型生成知识",
                "capabilityCode=" + capability);
        }
        return KnowledgeProductionReadinessItem.pass(
            "REGRESSION_BASELINE",
            "医学回归基准集已配置",
            "caseCount=" + cases.size());
    }

    private KnowledgeProductionReadinessItem evaluationItem(String tenantId,
                                                            String capability,
                                                            Optional<ModelProviderConfig> provider,
                                                            List<MedicalRegressionCase> cases) {
        if (provider.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "无 provider，无法确认医学回归评测通过",
                "provider=<missing>");
        }
        ModelProviderConfig config = provider.get();
        Optional<ModelEvalRun> run = evalRunRepository
            .findFirstByTenantIdAndProviderCodeAndModelVersionAndCapabilityCodeAndStatusOrderByIdDesc(
                tenantId, config.providerCode(), config.modelVersion(), capability, "PASSED");
        if (run.isEmpty()
            || !capability.equals(run.get().capabilityCode())
            || run.get().totalCases() < 1) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "provider/模型版本未找到当前能力的 PASSED 医学回归评测",
                config.providerCode() + "/" + config.modelVersion() + "/" + capability);
        }
        int expectedCases = cases == null ? 0 : cases.size();
        if (expectedCases > 0 && run.get().totalCases() < expectedCases) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "最近 PASSED 评测覆盖用例数少于当前启用基准集",
                "passedRunCases=" + run.get().totalCases() + ", currentCases=" + expectedCases);
        }
        if (!RegressionBaselineEvidence.matches(run.get().caseSummaryJson(), cases)) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "最近 PASSED 评测绑定的医学基准集已变化，必须重新评测",
                "runId=" + run.get().id() + ", currentCases=" + expectedCases);
        }
        if (!evalService.isClearedForGoLive(
                tenantId,
                config.providerCode(),
                config.modelVersion(),
                capability)) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "当前制品尚无完整、有效且按需独立签署的医学回归评测",
                "provider=" + config.providerCode()
                    + ", model=" + config.modelVersion()
                    + ", capability=" + capability);
        }
        return KnowledgeProductionReadinessItem.pass(
            "MODEL_EVALUATION",
            "provider/模型版本已通过医学回归评测",
            "runId=" + run.get().id());
    }

    private KnowledgeProductionReadinessItem egressItem(String tenantId, String capability,
                                                        Optional<ModelProviderConfig> provider) {
        if (provider.isPresent() && providerType(provider.get()).map(type -> !type.external()).orElse(false)) {
            return KnowledgeProductionReadinessItem.pass(
                "EGRESS_GOVERNANCE",
                "本地模型不外调，出域白名单不作为前置",
                provider.get().providerCode());
        }
        Optional<ModelEgressWhitelist> whitelist =
            egressWhitelistRepository.findByTenantIdAndCapabilityCode(tenantId, capability);
        ModelEgressPolicyValidator.Validation policy =
            ModelEgressPolicyValidator.validate(whitelist.orElse(null));
        if (!policy.valid()) {
            return KnowledgeProductionReadinessItem.block(
                "EGRESS_GOVERNANCE",
                "外部模型生产的出域白名单不可执行；高敏 payload 审批仍由运行时逐次判定",
                "capabilityCode=" + capability + ", reason=" + policy.reason());
        }
        if (!policy.allowedFields().contains("prompt")) {
            return KnowledgeProductionReadinessItem.block(
                "EGRESS_GOVERNANCE",
                "知识生产外调白名单必须显式允许经脱敏的 prompt 字段",
                "capabilityCode=" + capability);
        }
        return KnowledgeProductionReadinessItem.pass(
            "EGRESS_GOVERNANCE",
            "外部模型出域白名单已配置；高敏 payload 审批将由运行时逐次判定",
            "capabilityCode=" + capability);
    }

    private KnowledgeProductionReadinessItem policyItem(String tenantId, String capability,
                                                        KnowledgeProducer producer) {
        Optional<ModelCapabilityPolicy> policy = resolvePolicy(tenantId, capability);
        if (policy.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_POLICY",
                "模型能力策略未配置，不能进入正式模型生产",
                "capabilityCode=" + capability);
        }
        String strategy = normalize(policy.get().routeStrategy());
        String expected = producer == KnowledgeProducer.LOCAL_MODEL ? "LOCAL_MODEL" : "EXTERNAL_MODEL";
        if (!expected.equals(strategy)) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_POLICY",
                "模型能力策略与生产器不匹配",
                "routeStrategy=" + policy.get().routeStrategy() + ", expected=" + expected);
        }
        return KnowledgeProductionReadinessItem.pass(
            "MODEL_POLICY",
            "模型能力策略与生产器匹配",
            "routeStrategy=" + strategy + ", scope=" + policy.get().scopeType() + ":" + policy.get().scopeRef());
    }

    private Optional<ModelCapabilityPolicy> resolvePolicy(String tenantId, String capability) {
        for (ModelPolicyScope scope : ModelPolicyScope.candidates(RequestContext.currentOrgScope(), tenantId)) {
            Optional<ModelCapabilityPolicy> policy =
                policyRepository.findByTenantIdAndCapabilityCodeAndScopeTypeAndScopeRef(
                    tenantId, capability, scope.scopeType(), scope.scopeRef());
            if (policy.isPresent()) {
                return policy;
            }
        }
        return Optional.empty();
    }

    private KnowledgeProductionReadinessItem versionTripleItem(String tenantId,
                                                               String capability,
                                                               ModelProviderConfig provider) {
        Optional<ModelVersionBundle> active = versionBundleRepository
            .findFirstByTenantIdAndCapabilityCodeAndStatusOrderByIdDesc(tenantId, capability, "ACTIVE");
        ModelVersionBundleValidator.Validation validation =
            ModelVersionBundleValidator.validateActive(active.orElse(null), tenantId, capability);
        if (!validation.valid()) {
            return KnowledgeProductionReadinessItem.block(
                "VERSION_TRIPLE",
                "ACTIVE 模型版本包不可执行",
                "capabilityCode=" + capability + ", reason=" + validation.reason());
        }
        ModelVersionBundle bundle = validation.bundle();
        if (provider == null || !bundle.modelVersion().equals(provider.modelVersion())) {
            return KnowledgeProductionReadinessItem.block(
                "VERSION_TRIPLE",
                "模型版本三元组与 provider 当前模型版本不一致",
                "bundleModel=" + bundle.modelVersion()
                    + ", providerModel=" + (provider == null ? "<missing>" : provider.modelVersion()));
        }
        return KnowledgeProductionReadinessItem.pass(
            "VERSION_TRIPLE",
            "当前能力的 ACTIVE prompt/tool/model 版本包与 provider 一致",
            "bundleId=" + bundle.id()
                + ", prompt=" + bundle.promptVersion()
                + ", tool=" + bundle.toolVersion()
                + ", model=" + bundle.modelVersion());
    }

    private KnowledgeProductionReadinessItem p6AcceptanceItem() {
        if (!configService.runtimeKnowledgeProductionP6IndependentAcceptance()) {
            return KnowledgeProductionReadinessItem.block(
                "P6_ACCEPTANCE",
                "P6 独立验收未放行，禁止正式模型生成知识",
                SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY + "=false");
        }
        return KnowledgeProductionReadinessItem.pass(
            "P6_ACCEPTANCE",
            "P6 独立验收已放行",
            SystemConfigService.KNOWLEDGE_PRODUCTION_P6_INDEPENDENT_ACCEPTANCE_KEY + "=true");
    }

    private Optional<ModelProviderConfig> resolveProvider(String tenantId, KnowledgeProducer producer, String providerCode) {
        if (providerCode != null && !providerCode.isBlank()) {
            return providerRepository.findByTenantIdAndProviderCode(tenantId, providerCode.trim());
        }
        return providerRepository.findByTenantIdAndEnabledFlag(tenantId, "Y").stream()
            .filter(provider -> providerMatchesProducer(provider, producer))
            .findFirst();
    }

    private boolean providerMatchesProducer(ModelProviderConfig provider, KnowledgeProducer producer) {
        return providerType(provider)
            .map(type -> producer == KnowledgeProducer.LOCAL_MODEL ? !type.external() : type.external())
            .orElse(false);
    }

    private Optional<ProviderType> providerType(ModelProviderConfig provider) {
        try {
            return Optional.of(ProviderType.valueOf(provider.providerType().trim().toUpperCase(Locale.ROOT)));
        } catch (RuntimeException invalid) {
            return Optional.empty();
        }
    }

    private String requireCurrentTenant() {
        OrgScope scope = RequestContext.currentOrgScope();
        if (scope == null || !scope.hasTenant()) {
            throw ApiException.tenantMissing();
        }
        return scope.tenantId();
    }

    private String normalizeCapability(String capabilityCode) {
        if (capabilityCode == null || capabilityCode.isBlank()) {
            return DEFAULT_CAPABILITY_CODE;
        }
        return capabilityCode.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
