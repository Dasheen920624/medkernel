package com.medkernel.engine.interop;

import com.medkernel.engine.pathway.PathwayTemplateCreateRequest;
import com.medkernel.engine.rule.RuleCreateRequest;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标准互操作映射 REST 入口。
 *
 * <p>提供规则 DSL 与 CDS Hooks/CQL/Arden、路径模板与 FHIR PlanDefinition/GLIF 的导出和回导；
 * 映射服务不保存第二份事实源，所有回导结果仍进入既有规则/路径创建与发布流程。
 */
@RestController
@RequestMapping("/api/v1/engine/interoperability")
@DataScope(requireTenant = true)
public class InteroperabilityController {

    private final InteroperabilityMappingService service;

    /**
     * 注入标准互操作映射服务，控制器只负责 HTTP 合同、权限和统一响应包装。
     */
    public InteroperabilityController(InteroperabilityMappingService service) {
        this.service = service;
    }

    /**
     * 导出规则草稿到 CDS Hooks 服务声明、Card、CQL 与 Arden 概念映射。
     */
    @PostMapping("/rules/cds-hooks:export")
    @PreAuthorize("@perm.hasAny('rule.read','rule.write')")
    public ApiResult<RuleCdsHooksMapping> exportRuleToCdsHooks(
            @RequestBody @Valid RuleCreateRequest request) {
        return ApiResult.ok(service.exportRuleToCdsHooks(request));
    }

    /**
     * 从 CDS Hooks 映射回导规则草稿。
     */
    @PostMapping("/rules/cds-hooks:import")
    @PreAuthorize("@perm.has('rule.write')")
    public ApiResult<RuleCreateRequest> importRuleFromCdsHooks(
            @RequestBody @Valid RuleCdsHooksMapping mapping) {
        return ApiResult.ok(service.importRuleFromCdsHooks(mapping));
    }

    /**
     * 导出路径模板草稿到 FHIR PlanDefinition 与 GLIF 概念映射。
     */
    @PostMapping("/pathways/plan-definition:export")
    @PreAuthorize("@perm.hasAny('pathway.read','pathway.write')")
    public ApiResult<PathwayStandardMapping> exportPathwayToPlanDefinition(
            @RequestBody @Valid PathwayTemplateCreateRequest request) {
        return ApiResult.ok(service.exportPathwayToPlanDefinition(request));
    }

    /**
     * 从 PlanDefinition 映射回导路径模板草稿。
     */
    @PostMapping("/pathways/plan-definition:import")
    @PreAuthorize("@perm.has('pathway.write')")
    public ApiResult<PathwayTemplateCreateRequest> importPathwayFromPlanDefinition(
            @RequestBody @Valid PathwayStandardMapping mapping) {
        return ApiResult.ok(service.importPathwayFromPlanDefinition(mapping));
    }
}
