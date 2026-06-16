package com.medkernel.engine.llm;

import java.util.List;

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
 * 模型能力网关接口控制器 (GA-ENG-API-12)。
 *
 * <p>封装大模型统一调用与平滑降级 RESTful 端点，承载能力扫描、任务提交推理、任务状态追溯、以及策略有效性校验服务。
 * 全线实施 {@link DataScope} 强多租户物理安全拦截。
 */
@RestController
@RequestMapping("/api/v1/model-capabilities")
@DataScope(requireTenant = true)
public class ModelGatewayController {

    private final ModelGatewayService service;

    public ModelGatewayController(ModelGatewayService service) {
        this.service = service;
    }

    /**
     * 扫描获取当前租户全部可用模型能力状态与降级指标。
     *
     * @return 模型能力状态列表
     */
    @GetMapping("/status")
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<List<ModelCapabilityStatusResponse>> getStatus() {
        return ApiResult.ok(service.getStatus());
    }

    /**
     * 查询平台模型能力目录，包括停用项。
     *
     * @return 模型能力目录
     */
    @GetMapping("/catalog")
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<List<ModelCapabilityDefinitionResponse>> getCatalog() {
        return ApiResult.ok(service.listDefinitions());
    }

    /**
     * 新增或更新平台模型能力目录项。
     *
     * @param capabilityCode 能力代码
     * @param request 能力目录定义
     * @return 保存后的目录项
     */
    @PutMapping("/catalog/{capabilityCode}")
    @PreAuthorize("@perm.has('system.manage')")
    public ApiResult<ModelCapabilityDefinitionResponse> saveCatalogEntry(
            @PathVariable String capabilityCode,
            @Valid @RequestBody ModelCapabilityDefinitionUpsertRequest request) {
        return ApiResult.ok(service.saveDefinition(capabilityCode, request));
    }

    /**
     * 提交语义抽取、关系发现、智能生成或临床评估任务，由网关执行路由、数据脱敏与Schema检验。
     *
     * @param request 任务提交流入参数
     * @return 推理成功结果或降级回退基线响应
     */
    @PostMapping("/tasks")
    @PreAuthorize("@perm.has('llm.execute')")
    public ApiResult<ModelTaskResponse> submitTask(@Valid @RequestBody ModelTaskRequest request) {
        return ApiResult.ok(service.submitTask(request));
    }

    /**
     * 根据任务ID追溯大模型推理或降级回退任务的流转实情与风险评级。
     *
     * @param id 任务唯一ID
     * @return 推理任务详情
     */
    @GetMapping("/tasks/{id}")
    @PreAuthorize("@perm.has('llm.read')")
    public ApiResult<ModelTaskResponse> getTask(@PathVariable String id) {
        return ApiResult.ok(service.getTask(id));
    }

    /**
     * 重试失败的模型推理任务或改走B0人工/确定性基线通道。
     *
     * @param id 任务唯一ID
     * @return 新任务处理结果
     */
    @PostMapping("/tasks/{id}/retry")
    @PreAuthorize("@perm.has('llm.execute')")
    public ApiResult<ModelTaskResponse> retryTask(@PathVariable String id) {
        return ApiResult.ok(service.retryTask(id));
    }

    /**
     * 按 task_id 重放 B0 确定性模型任务，复现原任务保存的输入摘要与版本三元组。
     *
     * @param id 原任务唯一 ID
     * @return 新重放任务处理结果
     */
    @PostMapping("/tasks/{id}/replay")
    @PreAuthorize("@perm.has('llm.execute')")
    public ApiResult<ModelTaskResponse> replayTask(@PathVariable String id) {
        return ApiResult.ok(service.replayTask(id));
    }

    /**
     * 发布路由及脱敏策略前的合法性逻辑校验，确保路由降级通道畅通。
     *
     * @param request 策略配置检验请求参数
     * @return 逻辑校验报告响应
     */
    @PostMapping("/policies/validate")
    @PreAuthorize("@perm.has('llm.manage')")
    public ApiResult<ModelPolicyValidateResponse> validatePolicy(@Valid @RequestBody ModelPolicyValidateRequest request) {
        return ApiResult.ok(service.validatePolicy(request));
    }

    /**
     * 保存当前租户指定能力的路由、脱敏与结构化输出策略。
     *
     * @param capabilityCode 能力代码
     * @param request 策略配置
     * @return 保存后的真实策略状态
     */
    @PutMapping("/policies/{capabilityCode}")
    @PreAuthorize("@perm.has('llm.manage')")
    public ApiResult<ModelCapabilityStatusResponse> savePolicy(
            @PathVariable String capabilityCode,
            @Valid @RequestBody ModelPolicyUpsertRequest request) {
        return ApiResult.ok(service.savePolicy(capabilityCode, request));
    }
}
