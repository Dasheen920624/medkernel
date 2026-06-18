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
 * OPT-09 数据最小化策略正式入口。
 *
 * <p>当前模型出域策略复用 LLM-03 的白名单、脱敏规则、审批阈值和证据账本；本控制器提供
 * {@code /data-minimization/policies/*} 卡片契约入口，避免调用方绑定模型出域内部路径。
 */
@RestController
@RequestMapping("/api/v1/data-minimization/policies")
@DataScope(requireTenant = true)
public class DataMinimizationPolicyController {

    private final ModelEgressGovernanceService service;

    public DataMinimizationPolicyController(ModelEgressGovernanceService service) {
        this.service = service;
    }

    /**
     * 新增或更新指定模型能力的出域数据最小化策略。
     */
    @PutMapping("/model-egress/{capabilityCode}")
    @PreAuthorize("@perm.has('llm.egress.manage')")
    public ApiResult<ModelEgressWhitelist> upsertModelEgressPolicy(
            @PathVariable String capabilityCode,
            @Valid @RequestBody ModelEgressWhitelistUpsertRequest request) {
        return ApiResult.ok(service.upsertWhitelist(capabilityCode, request));
    }

    /**
     * 裁定一条模型高敏出域审批。
     */
    @PostMapping("/model-egress/approvals")
    @PreAuthorize("@perm.has('llm.egress.manage')")
    public ApiResult<ModelEgressApproval> decideModelEgressApproval(
            @Valid @RequestBody ModelEgressApprovalRequest request) {
        return ApiResult.ok(service.decideApproval(request));
    }
}
