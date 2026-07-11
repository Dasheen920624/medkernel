package com.medkernel.engine.evaluation;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import com.medkernel.shared.observability.DiagnoseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-08 canonical 评估质控入口。
 *
 * <p>面向 D4 卡片约定的单数资源与冒号动作路径：
 * {@code /api/v1/engine/evaluation/**} 与 {@code /api/v1/engine/evaluation:evaluate}。
 */
@RestController
@RequestMapping("/api/v1/engine/evaluation")
@DataScope(requireTenant = true)
public class EvaluationEngineCanonicalController {

    private final EvaluationEngineService service;

    public EvaluationEngineCanonicalController(EvaluationEngineService service) {
        this.service = service;
    }

    /**
     * 创建评估指标草稿版本。
     */
    @PostMapping("/indicators")
    @PreAuthorize("@perm.has('evaluation.write')")
    public ResponseEntity<ApiResult<EvaluationIndicator>> createIndicator(
            @RequestBody @Valid EvaluationIndicatorCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.createIndicator(request)));
    }

    /**
     * 按状态、对象类型和指标编码分页查询指标版本。
     */
    @GetMapping("/indicators")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<PageResponse<EvaluationIndicator>> indicators(
            @RequestParam(required = false) EvaluationIndicatorStatus status,
            @RequestParam(required = false) EvaluationSubjectType subjectType,
            @RequestParam(required = false) String indicatorCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listIndicators(
            new EvaluationIndicatorFilter(status, subjectType, indicatorCode),
            new PageRequest(page, size, sort)));
    }

    /**
     * 查看单个评估指标版本详情。
     */
    @GetMapping("/indicators/{indicatorId}")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<EvaluationIndicator> indicatorDetail(@PathVariable String indicatorId) {
        return ApiResult.ok(service.indicatorDetail(indicatorId));
    }

    /**
     * 将指标从草稿提交审核。
     */
    @PostMapping("/indicators/{indicatorId}/submit")
    @PreAuthorize("@perm.has('evaluation.write')")
    public ApiResult<EvaluationIndicator> submitIndicator(@PathVariable String indicatorId) {
        return ApiResult.ok(service.submitIndicator(indicatorId));
    }

    /**
     * 发布待审核指标。
     */
    @PostMapping("/indicators/{indicatorId}/publish")
    @PreAuthorize("@perm.has('evaluation.publish')")
    public ApiResult<EvaluationIndicator> publishIndicator(
            @PathVariable String indicatorId,
            @RequestBody @Valid EvaluationIndicatorReleaseRequest request) {
        return ApiResult.ok(service.publishIndicator(indicatorId, request));
    }

    /**
     * 将已发布指标进入默认 10% 床位灰度。
     */
    @PostMapping("/indicators/{indicatorId}/gray")
    @PreAuthorize("@perm.has('evaluation.publish')")
    public ApiResult<EvaluationIndicator> grayIndicator(
            @PathVariable String indicatorId,
            @RequestBody @Valid EvaluationIndicatorReleaseRequest request) {
        return ApiResult.ok(service.grayIndicator(indicatorId, request));
    }

    /**
     * 激活已发布指标并下线旧版本。
     */
    @PostMapping("/indicators/{indicatorId}/activate")
    @PreAuthorize("@perm.has('evaluation.publish')")
    public ApiResult<EvaluationIndicator> activateIndicator(
            @PathVariable String indicatorId,
            @RequestBody @Valid EvaluationIndicatorReleaseRequest request) {
        return ApiResult.ok(service.activateIndicator(indicatorId, request));
    }

    /**
     * 接收一次评估运行事实及结果问题。
     */
    @PostMapping("/runs")
    @PreAuthorize("@perm.has('evaluation.execute')")
    public ResponseEntity<ApiResult<EvaluationRunResponse>> run(
            @RequestBody @Valid EvaluationRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.run(request)));
    }

    /**
     * 分页查询评估结果。
     */
    @GetMapping("/results")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<PageResponse<EvaluationResult>> results(
            @RequestParam(required = false) String indicatorCode,
            @RequestParam(required = false) EvaluationResultLevel resultLevel,
            @RequestParam(required = false) String responsibleDepartmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listResults(
            new EvaluationResultFilter(indicatorCode, resultLevel, responsibleDepartmentId),
            new PageRequest(page, size, sort)));
    }

    /**
     * 分页查询质量问题，canonical 契约中命名为 issues。
     */
    @GetMapping("/issues")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<PageResponse<QualityFinding>> issues(
            @RequestParam(required = false) QualityFindingSeverity severity,
            @RequestParam(required = false) QualityFindingStatus status,
            @RequestParam(required = false) String responsibleDepartmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResult.ok(service.listFindings(
            new QualityFindingFilter(severity, status, responsibleDepartmentId),
            new PageRequest(page, size, sort)));
    }

    /**
     * 查看质量问题、整改任务和复核记录。
     */
    @GetMapping("/issues/{findingId}")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<QualityFindingDetailResponse> issueDetail(@PathVariable String findingId) {
        return ApiResult.ok(service.findingDetail(findingId));
    }

    /**
     * 提交质量问题整改说明和证据引用。
     */
    @PostMapping("/rectifications")
    @PreAuthorize("@perm.has('evaluation.remediate')")
    public ApiResult<RectificationResponse> submitRectification(
            @RequestParam String findingId,
            @RequestBody @Valid RectificationSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResult.ok(service.submitRectification(findingId, request, idempotencyKey));
    }

    /**
     * 提交整改复核结论。
     */
    @PostMapping("/rectifications/{findingId}/review")
    @PreAuthorize("@perm.has('evaluation.review')")
    public ApiResult<RectificationReviewResponse> reviewRectification(
            @PathVariable String findingId,
            @RequestBody @Valid RectificationReviewRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResult.ok(service.reviewRectification(findingId, request, idempotencyKey));
    }

    /**
     * 查看评估运行的诊断响应。
     */
    @GetMapping("/runs/{runId}/diagnose")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<DiagnoseResponse> diagnose(@PathVariable String runId) {
        return ApiResult.ok(service.diagnose(runId));
    }
}
