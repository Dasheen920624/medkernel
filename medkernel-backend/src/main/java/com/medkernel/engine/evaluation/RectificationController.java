package com.medkernel.engine.evaluation;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
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
 * SVC-QUALITY-03 整改闭环服务入口。
 *
 * <p>提供派发、整改提交、复核、豁免和报告接口；全部复用评估质控闭环主链路，
 * 以当前租户数据范围为边界。
 */
@RestController
@RequestMapping("/api/v1/engine/rectifications")
@DataScope(requireTenant = true)
public class RectificationController {

    private final EvaluationEngineService service;

    public RectificationController(EvaluationEngineService service) {
        this.service = service;
    }

    /**
     * 派发质控问题为整改任务。
     */
    @PostMapping
    @PreAuthorize("@perm.has('evaluation.review')")
    public ResponseEntity<ApiResult<RectificationResponse>> dispatch(
            @RequestBody @Valid RectificationDispatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.dispatchRectification(request, idempotencyKey)));
    }

    /**
     * 科室提交整改说明和证据引用。
     */
    @PostMapping("/{taskId}/submit")
    @PreAuthorize("@perm.has('evaluation.remediate')")
    public ApiResult<RectificationResponse> submit(
            @PathVariable String taskId,
            @RequestBody @Valid RectificationSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResult.ok(service.submitRectificationTask(taskId, request, idempotencyKey));
    }

    /**
     * 质控复核整改任务。
     */
    @PostMapping("/{taskId}/review")
    @PreAuthorize("@perm.has('evaluation.review')")
    public ApiResult<RectificationReviewResponse> review(
            @PathVariable String taskId,
            @RequestBody @Valid RectificationReviewRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResult.ok(service.reviewRectificationTask(taskId, request, idempotencyKey));
    }

    /**
     * 按专用动作豁免整改任务，要求提交决定依据。
     */
    @PostMapping("/{taskId}/waive")
    @PreAuthorize("@perm.has('evaluation.review')")
    public ApiResult<RectificationReviewResponse> waive(
            @PathVariable String taskId,
            @RequestBody @Valid RectificationWaiveRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResult.ok(service.waiveRectificationTask(taskId, request, idempotencyKey));
    }

    /**
     * 查询整改闭环报告。
     */
    @GetMapping("/report")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<RectificationReportResponse> report(
            @RequestParam(required = false) String responsibleDepartmentId) {
        return ApiResult.ok(service.rectificationReport(
            new RectificationReportFilter(responsibleDepartmentId)));
    }
}
