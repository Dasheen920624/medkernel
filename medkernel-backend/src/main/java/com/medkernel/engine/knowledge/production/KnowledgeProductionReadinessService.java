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
 * 正式模型生成知识前置上线准备闸（AIK-STD-13/LLM-01/02/04）。
 *
 * <p>本服务只聚合关系库和配置中心事实，不调用模型、不创建候选。任一强前置缺失时返回结构化阻断，
 * 模型生产器必须据此停止真实模型调用并返回诚实阻断，禁止静默回退为非模型候选。
 */
@Service
public class KnowledgeProductionReadinessService {

    /** 正式医学知识生产从就绪检查到影子评测共用的唯一模型能力码。 */
    public static final String DEFAULT_CAPABILITY_CODE = "knowledge.production.knowledge";

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
        items.add(deploymentItem(targetProducer, deploymentForm, provider));
        items.add(providerItem(tenantId, targetProducer, provider));
        List<MedicalRegressionCase> cases =
            regressionCaseRepository.findByTenantIdAndCapabilityCodeAndEnabledFlag(tenantId, capability, "Y");
        items.add(regressionBaselineItem(capability, cases));
        items.add(evaluationItem(tenantId, capability, provider, cases));
        items.add(egressItem(tenantId, capability, provider));
        items.add(policyItem(tenantId, capability, targetProducer, provider.orElse(null)));
        items.add(versionTripleItem(tenantId, capability, provider.orElse(null)));
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
                "文献资料库地址未填写");
        }
        return KnowledgeProductionReadinessItem.pass(
            "LITERATURE_ROOT",
            "平台知识文献资料库根地址已配置",
            rootUri.trim());
    }

    private KnowledgeProductionReadinessItem deploymentItem(
            KnowledgeProducer producer,
            DeploymentForm form,
            Optional<ModelProviderConfig> provider) {
        if (producer == KnowledgeProducer.LOCAL_MODEL) {
            return KnowledgeProductionReadinessItem.pass(
                "DEPLOYMENT_FORM",
                "本地模型生产器允许在当前部署形态下运行",
                "部署形态：" + deploymentFormLabel(form));
        }
        boolean localProviderApi = provider
            .flatMap(this::providerType)
            .map(type -> !type.external())
            .orElse(false);
        if (producer == KnowledgeProducer.API_MODEL && localProviderApi) {
            return KnowledgeProductionReadinessItem.pass(
                "DEPLOYMENT_FORM",
                "本地模型服务允许在当前部署形态下运行",
                "部署形态：" + deploymentFormLabel(form) + "；模型服务：" + provider.get().providerCode());
        }
        if (form == DeploymentForm.PRODUCTION_CENTER) {
            return KnowledgeProductionReadinessItem.pass(
                "DEPLOYMENT_FORM",
                "外部模型服务生产仅在知识生产中心形态启用",
                "部署形态：" + deploymentFormLabel(form));
        }
        return KnowledgeProductionReadinessItem.block(
            "DEPLOYMENT_FORM",
            "当前不是知识生产中心形态，禁止外部模型服务生产知识",
            "部署形态：" + deploymentFormLabel(form));
    }

    private KnowledgeProductionReadinessItem providerItem(String tenantId,
                                                          KnowledgeProducer producer,
                                                          Optional<ModelProviderConfig> provider) {
        if (provider.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "未找到匹配的模型服务",
                "生产方式：" + producerLabel(producer));
        }
        ModelProviderConfig config = provider.get();
        Optional<ProviderType> type = providerType(config);
        if (type.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "模型服务类型无效，不能进入模型知识生产",
                "模型服务：" + config.providerCode() + "；服务类型：" + config.providerType());
        }
        ProviderType providerType = type.get();
        boolean typeMatches = producer == KnowledgeProducer.API_MODEL || !providerType.external();
        if (!typeMatches) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "模型服务类型与生产方式不匹配",
                "模型服务：" + config.providerCode() + "；服务类型：" + providerTypeLabel(providerType));
        }
        if (!config.enabled() || !"HEALTHY".equalsIgnoreCase(config.status())) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                "模型服务未启用或连接状态不是连接正常",
                "模型服务：" + config.providerCode() + "；连接状态：" + providerStatusLabel(config.status()));
        }
        boolean vaultCredentialConfigured = credentialRepository
            .findByTenantIdAndProviderCode(tenantId, config.providerCode())
            .isPresent();
        boolean credentialMissing = providerType.external() && !vaultCredentialConfigured;
        if (blank(config.endpointUri()) || credentialMissing || blank(config.modelVersion())) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_PROVIDER",
                providerType.external()
                    ? "外部模型服务缺调用地址、机构凭据或模型版本"
                    : "本地模型服务缺调用地址或模型版本",
                "模型服务：" + config.providerCode());
        }
        return KnowledgeProductionReadinessItem.pass(
            "MODEL_PROVIDER",
            "模型服务已启用且连接正常",
            "模型服务：" + config.providerCode() + "；模型版本：" + config.modelVersion());
    }

    private KnowledgeProductionReadinessItem regressionBaselineItem(String capability,
                                                                    List<MedicalRegressionCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "REGRESSION_BASELINE",
                "医学验证用例为空，禁止正式模型生成知识",
                "能力：" + capability);
        }
        return KnowledgeProductionReadinessItem.pass(
            "REGRESSION_BASELINE",
            "医学验证用例已配置",
            "用例数：" + cases.size());
    }

    private KnowledgeProductionReadinessItem evaluationItem(String tenantId,
                                                            String capability,
                                                            Optional<ModelProviderConfig> provider,
                                                            List<MedicalRegressionCase> cases) {
        if (provider.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "无模型服务，无法确认医学验证评测通过",
                "模型服务未选择");
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
                "模型服务与模型版本未找到当前能力的已通过医学验证评测",
                "模型服务：" + config.providerCode()
                    + "；模型版本：" + config.modelVersion()
                    + "；能力：" + capability);
        }
        int expectedCases = cases == null ? 0 : cases.size();
        if (expectedCases > 0 && run.get().totalCases() < expectedCases) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "最近已通过评测覆盖用例数少于当前启用基准集",
                "已通过用例数：" + run.get().totalCases() + "；当前用例数：" + expectedCases);
        }
        if (!RegressionBaselineEvidence.matches(run.get().caseSummaryJson(), cases)) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "最近已通过评测绑定的医学验证用例已变化，必须重新评测",
                "评测记录：" + run.get().id() + "；当前用例数：" + expectedCases);
        }
        if (!evalService.isClearedForGoLive(
                tenantId,
                config.providerCode(),
                config.modelVersion(),
                capability)) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_EVALUATION",
                "当前交付内容尚无完整且有效的医学验证评测",
                "模型服务：" + config.providerCode()
                    + "；模型版本：" + config.modelVersion()
                    + "；能力：" + capability);
        }
        return KnowledgeProductionReadinessItem.pass(
            "MODEL_EVALUATION",
            "模型服务与模型版本已通过医学验证评测",
            "评测记录：" + run.get().id());
    }

    private KnowledgeProductionReadinessItem egressItem(String tenantId, String capability,
                                                        Optional<ModelProviderConfig> provider) {
        if (provider.isPresent() && providerType(provider.get()).map(type -> !type.external()).orElse(false)) {
            return KnowledgeProductionReadinessItem.pass(
                "EGRESS_GOVERNANCE",
                "院内本地模型使用边界已配置，患者上下文按院内授权处理",
                "模型服务：" + provider.get().providerCode());
        }
        Optional<ModelEgressWhitelist> whitelist =
            egressWhitelistRepository.findByTenantIdAndCapabilityCode(tenantId, capability);
        ModelEgressPolicyValidator.Validation policy =
            ModelEgressPolicyValidator.validate(whitelist.orElse(null));
        if (!policy.valid()) {
            return KnowledgeProductionReadinessItem.block(
                "EGRESS_GOVERNANCE",
                "公网模型使用边界不可执行；核心敏感信息屏蔽和责任确认仍会逐次拦截",
                "能力：" + capability + "；原因：" + policy.reason());
        }
        if (!policy.allowedFields().contains("prompt")) {
            return KnowledgeProductionReadinessItem.block(
                "EGRESS_GOVERNANCE",
                "知识生产模型使用边界必须包含经脱敏的提示词内容",
                "能力：" + capability);
        }
        return KnowledgeProductionReadinessItem.pass(
            "EGRESS_GOVERNANCE",
            "公网模型使用边界已配置；核心敏感信息将先屏蔽并保留责任确认",
            "能力：" + capability);
    }

    private KnowledgeProductionReadinessItem policyItem(
            String tenantId,
            String capability,
            KnowledgeProducer producer,
            ModelProviderConfig provider) {
        Optional<ModelCapabilityPolicy> policy = resolvePolicy(tenantId, capability);
        if (policy.isEmpty()) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_POLICY",
                "模型能力策略未配置，不能进入正式模型生产",
                "能力：" + capability);
        }
        String strategy = normalize(policy.get().routeStrategy());
        boolean localProvider = provider != null
            && providerType(provider).map(type -> !type.external()).orElse(false);
        String expected = producer == KnowledgeProducer.LOCAL_MODEL || localProvider
            ? "LOCAL_MODEL"
            : "EXTERNAL_MODEL";
        if (!expected.equals(strategy)) {
            return KnowledgeProductionReadinessItem.block(
                "MODEL_POLICY",
                "模型能力策略与生产器不匹配",
                "当前策略：" + strategyLabel(policy.get().routeStrategy())
                    + "；期望策略：" + strategyLabel(expected));
        }
        return KnowledgeProductionReadinessItem.pass(
            "MODEL_POLICY",
            "模型能力策略与生产器匹配",
            "策略：" + strategyLabel(strategy) + "；适用范围：" + policy.get().scopeType() + ":" + policy.get().scopeRef());
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
                "已生效模型版本组合不可执行",
                "能力：" + capability + "；原因：" + validation.reason());
        }
        ModelVersionBundle bundle = validation.bundle();
        if (provider == null || !bundle.modelVersion().equals(provider.modelVersion())) {
            return KnowledgeProductionReadinessItem.block(
                "VERSION_TRIPLE",
                "模型版本组合与模型服务当前版本不一致",
                "版本组合模型：" + bundle.modelVersion()
                    + "；模型服务版本：" + (provider == null ? "未选择模型服务" : provider.modelVersion()));
        }
        return KnowledgeProductionReadinessItem.pass(
            "VERSION_TRIPLE",
            "当前能力的已生效提示词、工具与模型版本一致",
            "版本组合：" + bundle.id()
                + "；提示词：" + bundle.promptVersion()
                + "；工具：" + bundle.toolVersion()
                + "；模型：" + bundle.modelVersion());
    }

    private Optional<ModelProviderConfig> resolveProvider(String tenantId, KnowledgeProducer producer, String providerCode) {
        if (providerCode != null && !providerCode.isBlank()) {
            return providerRepository.findByTenantIdAndProviderCode(tenantId, providerCode.trim());
        }
        return providerRepository.findByTenantIdAndEnabledFlag(tenantId, "Y").stream()
            .filter(provider -> producer == KnowledgeProducer.API_MODEL
                || providerMatchesProducer(provider, producer))
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

    private static String deploymentFormLabel(DeploymentForm form) {
        return form == DeploymentForm.PRODUCTION_CENTER ? "知识生产中心" : "院内运行";
    }

    private static String producerLabel(KnowledgeProducer producer) {
        return switch (producer) {
            case API_MODEL -> "模型服务生产";
            case AGENT_TOOL -> "工具协助生产";
            case LOCAL_MODEL -> "本地模型生产";
            case MANUAL -> "人工录入或批量导入";
        };
    }

    private static String providerTypeLabel(ProviderType type) {
        return type.external() ? "外部模型服务" : "本地模型服务";
    }

    private static String providerStatusLabel(String status) {
        return "HEALTHY".equalsIgnoreCase(status) ? "连接正常" : "未验证连接";
    }

    private static String strategyLabel(String strategy) {
        return "LOCAL_MODEL".equalsIgnoreCase(strategy) ? "本地模型" : "外部模型";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
