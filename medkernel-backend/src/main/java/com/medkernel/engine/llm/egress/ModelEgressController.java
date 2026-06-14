package com.medkernel.engine.llm.egress;

import org.springframework.security.access.prepost.PreAuthorize;
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
 * 模型外调出域治理接口控制器（LLM-03）。
 *
 * <p>由集成运维员（{@code llm.egress.manage}）维护出域字段白名单与裁定高敏出域审批；
 * 全线 {@link DataScope} 强多租户隔离。运行时出域拦截在 {@code ModelEgressGuard}。
 */
@RestController
@RequestMapping("/api/v1/model-egress")
@DataScope(requireTenant = true)
public class ModelEgressController {

    private final ModelEgressGovernanceService service;

    public ModelEgressController(ModelEgressGovernanceService service) {
        this.service = service;
    }

    /**
     * 新增或更新指定能力码的出域字段白名单。
     */
    @PutMapping("/whitelist/{capabilityCode}")
    @PreAuthorize("@perm.has('llm.egress.manage')")
    public ApiResult<ModelEgressWhitelist> upsertWhitelist(
            @PathVariable String capabilityCode,
            @Valid @RequestBody ModelEgressWhitelistUpsertRequest request) {
        return ApiResult.ok(service.upsertWhitelist(capabilityCode, request));
    }

    /**
     * 裁定一条高敏出域审批（APPROVED / REJECTED）。
     */
    @PostMapping("/approvals")
    @PreAuthorize("@perm.has('llm.egress.manage')")
    public ApiResult<ModelEgressApproval> decideApproval(
            @Valid @RequestBody ModelEgressApprovalRequest request) {
        return ApiResult.ok(service.decideApproval(request));
    }
}
