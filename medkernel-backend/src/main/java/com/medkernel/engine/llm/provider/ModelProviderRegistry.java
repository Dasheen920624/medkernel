package com.medkernel.engine.llm.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * 模型服务注册表（LLM-08 FR-1/2/3）。
 *
 * <p>按路由策略与部署形态解析一个健康的真实 provider：{@code LOCAL_MODEL}→B1 本地 Ollama；
 * {@code EXTERNAL_MODEL}→B2 外部（仅外网生产中心形态放行，运行侧内网一律不解析）。
 * 无配置 / 全不健康 / 形态禁外部 → 返回空，由网关诚实降级 B0。
 */
@Service
public class ModelProviderRegistry {

    private static final Set<ProviderType> LOCAL_TYPES = Set.of(ProviderType.OLLAMA);
    private static final Set<ProviderType> EXTERNAL_TYPES =
        Set.of(ProviderType.OPENAI_COMPATIBLE, ProviderType.CLAUDE, ProviderType.DIFY);

    private final ModelProviderConfigRepository repository;
    private final DeploymentFormService deploymentForm;
    private final Map<ProviderType, ModelProvider> adaptersByType;

    public ModelProviderRegistry(ModelProviderConfigRepository repository,
                                 DeploymentFormService deploymentForm,
                                 List<ModelProvider> adapters) {
        this.repository = repository;
        this.deploymentForm = deploymentForm;
        Map<ProviderType, ModelProvider> map = new EnumMap<>(ProviderType.class);
        adapters.forEach(adapter -> map.put(adapter.type(), adapter));
        this.adaptersByType = Map.copyOf(map);
    }

    /**
     * 解析结果：选中的适配器 + 其配置（含真实模型版本与 B1/B2 模式）。
     */
    public record ResolvedProvider(ModelProvider adapter, ModelProviderConfig config) {
        public String modelMode() {
            return adapter.type().modelMode();
        }
    }

    public Optional<ResolvedProvider> resolve(String tenantId, String routeStrategy) {
        return resolve(tenantId, routeStrategy, null);
    }

    public Optional<ResolvedProvider> resolve(String tenantId, String routeStrategy, String providerCode) {
        Set<ProviderType> desired = switch (routeStrategy == null ? "" : routeStrategy.toUpperCase()) {
            case "LOCAL_MODEL" -> LOCAL_TYPES;
            case "EXTERNAL_MODEL" -> EXTERNAL_TYPES;
            default -> Set.of();
        };
        if (desired.isEmpty()) {
            return Optional.empty();
        }
        // 双形态门禁：运行侧内网形态禁用一切外部 provider（核心 §1/§8）。
        boolean externalDesired = desired.equals(EXTERNAL_TYPES);
        if (externalDesired && !deploymentForm.allowsExternalProvider()) {
            return Optional.empty();
        }

        List<ModelProviderConfig> candidates = providerCode == null || providerCode.isBlank()
            ? repository.findByTenantIdAndEnabledFlag(tenantId, "Y")
            : repository.findByTenantIdAndProviderCode(tenantId, providerCode.trim())
                .filter(config -> "Y".equalsIgnoreCase(config.enabledFlag()))
                .stream()
                .toList();
        for (ModelProviderConfig config : candidates) {
            ProviderType type = parseType(config.providerType());
            if (type == null || !desired.contains(type)) {
                continue;
            }
            ModelProvider adapter = adaptersByType.get(type);
            if (adapter == null) {
                continue;
            }
            if (adapter.checkHealth(config) == ProviderHealth.HEALTHY) {
                return Optional.of(new ResolvedProvider(adapter, config));
            }
        }
        return Optional.empty();
    }

    /**
     * 按 provider 编码解析适配器+配置（用于医学回归评测，不论启停/健康，以便上线前评测候选 provider）。
     */
    public Optional<ResolvedProvider> resolveByCode(String tenantId, String providerCode) {
        return repository.findByTenantIdAndProviderCode(tenantId, providerCode)
            .map(config -> {
                ProviderType type = parseType(config.providerType());
                ModelProvider adapter = type == null ? null : adaptersByType.get(type);
                return adapter == null ? null : new ResolvedProvider(adapter, config);
            });
    }

    private ProviderType parseType(String raw) {
        try {
            return ProviderType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return null;
        }
    }
}
