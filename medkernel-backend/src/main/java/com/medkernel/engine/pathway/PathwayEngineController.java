package com.medkernel.engine.pathway;

import java.util.List;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 路径引擎 REST 入口（GA-ENG-API-06 {@code /api/v1/engine/pathway/**}）。
 *
 * <p>承担路径模板、发布、试运行、患者入径、节点推进、变异与关键时钟的 HTTP 合同；
 * 权限由 {@code pathway.read}/{@code pathway.write}/{@code pathway.publish} 拆分控制，
 * 租户隔离由类级 {@link DataScope}{@code (requireTenant=true)} 兜底。
 */
@RestController
@RequestMapping("/api/v1/engine/pathway")
@DataScope(requireTenant = true)
public class PathwayEngineController {

    private final PathwayEngineService service;

    /**
     * 注入路径引擎应用服务，控制器仅负责 HTTP 参数、权限入口与统一响应包装。
     */
    public PathwayEngineController(PathwayEngineService service) {
        this.service = service;
    }

    /**
     * 创建路径模板草稿，并一次性保存节点、边和质控指标绑定。
     *
     * <p>权限：{@code pathway.write}；模板必须关联当前租户下存在的路径知识包。
     */
    @PostMapping("/pathway-templates")
    @PreAuthorize("@perm.has('pathway.write')")
    public ResponseEntity<ApiResult<PathwayTemplateDetailResponse>> createTemplate(
            @RequestBody @Valid PathwayTemplateCreateRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.createTemplate(request)));
    }

    /**
     * 按状态、病种、路径知识包和模板编码过滤分页查询路径模板。
     *
     * <p>权限：{@code pathway.read}；过滤参数均可选，{@code null} 表示不过滤。
     */
    @GetMapping("/pathway-templates")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<PageResponse<PathwayTemplate>> listTemplates(
            @RequestParam(required = false) PathwayTemplateStatus status,
            @RequestParam(required = false) String diseaseCode,
            @RequestParam(required = false) String packageId,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listTemplates(
            new PathwayTemplateFilter(status, diseaseCode, packageId, templateCode),
            new PageRequest(page, size, sort)));
    }

    /**
     * 查看路径模板详情，包括模板主数据、节点、边和指标绑定。
     *
     * <p>权限：{@code pathway.read}；模板不存在时抛出 {@code ENG-PATHWAY-002}。
     */
    @GetMapping("/pathway-templates/{templateId}")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<PathwayTemplateDetailResponse> templateDetail(@PathVariable String templateId) {
        return ApiResult.ok(service.templateDetail(templateId));
    }

    /**
     * 查看路径模板继承差异与有效合并结果。
     *
     * <p>权限：{@code pathway.read}；返回下级模板相对父级链路的新增、覆盖、禁用和合并图。
     */
    @GetMapping("/pathway-templates/{templateId}/inheritance-diff")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<PathwayTemplateInheritanceDiffResponse> templateInheritanceDiff(
            @PathVariable String templateId) {
        return ApiResult.ok(service.templateInheritanceDiff(templateId));
    }

    /**
     * 读取路径模板发布前影响摘要。
     *
     * <p>权限：{@code pathway.read}；摘要来自真实拓扑、关键时钟和患者路径实例事实。
     */
    @GetMapping("/pathway-templates/{templateId}/impact")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<PathwayTemplateImpactResponse> templateImpact(@PathVariable String templateId) {
        return ApiResult.ok(service.templateImpact(templateId));
    }

    /**
     * 执行路径模板发布门禁并发布草稿模板。
     *
     * <p>权限：{@code pathway.publish}；发布门禁失败时抛出 {@code ENG-PATHWAY-004}。
     */
    @PostMapping("/pathway-templates/{templateId}/publish")
    @PreAuthorize("@perm.has('pathway.publish')")
    public ApiResult<PathwayTemplatePublishResponse> publishTemplate(
            @PathVariable String templateId,
            @RequestBody @Valid PathwayOperationRequest request) {
        validateContext(request);
        return ApiResult.ok(service.publishTemplate(templateId, request));
    }

    /**
     * 对已灰度发布的路径模板执行院级全量确认。
     *
     * <p>权限：{@code pathway.publish}；必须携带当前影响摘要、审核说明和院级管理员角色。
     */
    @PostMapping("/pathway-templates/{templateId}/rollout/full")
    @PreAuthorize("@perm.has('pathway.publish')")
    public ApiResult<PathwayTemplatePublishResponse> fullRolloutTemplate(
            @PathVariable String templateId,
            @RequestBody @Valid PathwayOperationRequest request) {
        validateContext(request);
        return ApiResult.ok(service.fullRolloutTemplate(templateId, request));
    }

    /**
     * 将当前路径模板回滚到同编码历史版本。
     *
     * <p>权限：{@code pathway.publish}；必须携带当前影响摘要、审核说明和回滚目标模板。
     */
    @PostMapping("/pathway-templates/{templateId}/rollback")
    @PreAuthorize("@perm.has('pathway.publish')")
    public ApiResult<PathwayTemplatePublishResponse> rollbackTemplate(
            @PathVariable String templateId,
            @RequestBody @Valid PathwayOperationRequest request) {
        validateContext(request);
        return ApiResult.ok(service.rollbackTemplate(templateId, request));
    }

    /**
     * 使用指定起点和目标节点序列试运行路径推进轨迹。
     *
     * <p>权限：{@code pathway.write}；试运行不创建患者路径实例，也不写入变异或关键时钟。
     */
    @PostMapping("/pathway-templates/{templateId}/simulate")
    @PreAuthorize("@perm.has('pathway.write')")
    public ApiResult<PathwaySimulationResponse> simulate(
            @PathVariable String templateId,
            @RequestBody @Valid PathwaySimulateRequest request) {
        validateContext(request);
        return ApiResult.ok(service.simulate(templateId, request));
    }

    /**
     * 为患者创建路径实例并进入起始节点。
     *
     * <p>权限：{@code pathway.write}；仅允许基于已发布模板入径，成功后创建首个关键时钟。
     */
    @PostMapping("/patient-pathways/enter")
    @PreAuthorize("@perm.has('pathway.write')")
    public ResponseEntity<ApiResult<PatientPathwayDetailResponse>> enterPatientPathway(
            @RequestBody @Valid PatientPathwayEnterRequest request) {
        validateContext(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.enterPatientPathway(request)));
    }

    /**
     * 分页查询患者路径运行实例。
     *
     * <p>权限：{@code pathway.read}；列表来自关系库运行事实，可按患者和状态过滤。
     */
    @GetMapping("/patient-pathways")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<PageResponse<PatientPathway>> listPatientPathways(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) PatientPathwayStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listPatientPathways(patientId, status, new PageRequest(page, size, sort)));
    }

    /**
     * 查看患者路径实例详情。
     *
     * <p>权限：{@code pathway.read}；返回当前节点、累计变异和关键时钟事实。
     */
    @GetMapping("/patient-pathways/{patientPathwayId}")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<PatientPathwayDetailResponse> patientDetail(@PathVariable String patientPathwayId) {
        return ApiResult.ok(service.patientDetail(patientPathwayId));
    }

    /**
     * 推进患者路径节点，或登记变异、退出路径。
     *
     * <p>权限：{@code pathway.write}；接口只记录流程事实，不自动诊断、不自动开立医嘱。
     */
    @PostMapping("/patient-pathways/{patientPathwayId}/advance")
    @PreAuthorize("@perm.has('pathway.write')")
    public ApiResult<PathwayAdvanceResponse> advance(
            @PathVariable String patientPathwayId,
            @RequestBody @Valid PathwayAdvanceRequest request) {
        validateContext(request);
        return ApiResult.ok(service.advance(request.withPatientPathwayId(patientPathwayId)));
    }

    /**
     * 查询患者路径实例的变异事实列表。
     *
     * <p>权限：{@code pathway.read}；先校验患者路径属于当前租户，再返回变异记录。
     */
    @GetMapping("/patient-pathways/{patientPathwayId}/variances")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<List<PathwayVariance>> variances(@PathVariable String patientPathwayId) {
        return ApiResult.ok(service.variances(patientPathwayId));
    }

    /**
     * 查询患者路径实例的关键时钟列表。
     *
     * <p>权限：{@code pathway.read}；关键时钟用于追踪节点时间窗和质控指标关联。
     */
    @GetMapping("/patient-pathways/{patientPathwayId}/clocks")
    @PreAuthorize("@perm.has('pathway.read')")
    public ApiResult<List<ClinicalClock>> clocks(@PathVariable String patientPathwayId) {
        return ApiResult.ok(service.clocks(patientPathwayId));
    }

    private void validateContext(PathwayContextRequest request) {
        request.apiContext().validateTenant(RequestContext.currentOrgScope().tenantId());
    }
}
