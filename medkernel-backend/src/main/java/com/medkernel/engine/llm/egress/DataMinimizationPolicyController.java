package com.medkernel.engine.llm.egress;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;

/**
 * OPT-09 数据最小化策略正式入口。
 *
 * <p>当前模型外调策略复用统一的允许范围、脱敏规则、责任确认阈值和证据账本；本控制器提供
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
     * 分页回看当前租户的模型外调用途确认记录。
     */
    @GetMapping("/model-egress/confirmations")
    @PreAuthorize("@perm.hasAny('audit.read','llm.egress.manage')")
    public ApiResult<PageResponse<ModelEgressConfirmation>> listModelEgressConfirmations(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listConfirmations(new PageRequest(page, size, sort)));
    }

    /**
     * 确认一条脱敏后高敏载荷的外调用途。
     */
    @PostMapping("/model-egress/confirmations")
    @PreAuthorize("@perm.hasAny('llm.egress.manage','knowledge.write')")
    public ApiResult<ModelEgressConfirmation> confirmModelEgress(
            @Valid @RequestBody ModelEgressConfirmationRequest request) {
        return ApiResult.ok(service.confirmEgress(request));
    }
}
