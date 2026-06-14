package com.medkernel.engine.llm.provider;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ApiResult<ModelProviderConfig> upsertProvider(
            @PathVariable String providerCode,
            @Valid @RequestBody ModelProviderUpsertRequest request) {
        return ApiResult.ok(service.upsertProvider(providerCode, request));
    }
}
