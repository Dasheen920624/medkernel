package com.medkernel.engine.llm.provider;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/**
 * 模型 provider 接入治理控制器（LLM-08）。
 *
 * <p>由集成运维员（{@code llm.provider.manage}）配置 provider 接入；运行侧内网禁启外部 provider（ENG-LLM-009）。
 * 全线 {@link DataScope} 强多租户隔离。
 */
@RestController
@RequestMapping("/api/v1/model-providers")
@DataScope(requireTenant = true)
public class ModelProviderController {

    private final ModelProviderGovernanceService service;

    public ModelProviderController(ModelProviderGovernanceService service) {
        this.service = service;
    }

    /**
     * 新增或更新指定 provider 接入配置。
     */
    @PutMapping("/{providerCode}")
    @PreAuthorize("@perm.has('llm.provider.manage')")
    public ApiResult<ModelProviderGovernanceView> upsertProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody ModelProviderUpsertRequest request) {
        return ApiResult.ok(ModelProviderGovernanceView.from(
            service.upsertProvider(providerCode, request)));
    }

    /**
     * 读取指定 provider 的脱敏治理快照。
     */
    @GetMapping("/{providerCode}")
    @PreAuthorize("@perm.has('llm.provider.manage')")
    public ApiResult<ModelProviderGovernanceView> getProvider(@PathVariable String providerCode) {
        return ApiResult.ok(service.getProvider(providerCode));
    }

    /**
     * 经高危门禁启用指定 provider。
     */
    @PostMapping("/{providerCode}/enable")
    @PreAuthorize("@perm.has('llm.provider.manage')")
    public ApiResult<ModelProviderGovernanceView> enableProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody ModelProviderActivationRequest request) {
        return ApiResult.ok(service.enableProvider(providerCode, request));
    }

    /**
     * 经高危门禁停用指定 provider。
     */
    @PostMapping("/{providerCode}/disable")
    @PreAuthorize("@perm.has('llm.provider.manage')")
    public ApiResult<ModelProviderGovernanceView> disableProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody ModelProviderActivationRequest request) {
        return ApiResult.ok(service.disableProvider(providerCode, request));
    }

    /**
     * 对指定 provider 执行真实健康检查并持久化状态。
     */
    @PostMapping("/{providerCode}/health-check")
    @PreAuthorize("@perm.has('llm.provider.manage')")
    public ApiResult<ModelProviderGovernanceView> checkHealth(@PathVariable String providerCode) {
        return ApiResult.ok(ModelProviderGovernanceView.from(service.checkHealth(providerCode)));
    }
}
