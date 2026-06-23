package com.medkernel.engine.quality.insurance;

import java.time.Instant;

import com.medkernel.engine.evaluation.QualityFindingSeverity;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SVC-QUALITY-02 病案医保服务 API。
 *
 * <p>提供病案内涵质控、DRG/DIP 入组核对与医保审核三个确定性 B0 入口。
 */
@RestController
@RequestMapping("/api/v1/engine/quality")
@DataScope(requireTenant = true)
public class InsuranceQualityController {
    private final InsuranceQualityService service;

    public InsuranceQualityController(InsuranceQualityService service) {
        this.service = service;
    }

    /**
     * 分页查询当前租户作用域内的真实医保病案问题。
     */
    @GetMapping("/insurance-issues")
    @PreAuthorize("@perm.has('evaluation.read')")
    public ApiResult<PageResponse<InsuranceIssuePageItemResponse>> insuranceIssues(
            @RequestParam(required = false) InsuranceIssueStatus status,
            @RequestParam(required = false) QualityFindingSeverity severity,
            @RequestParam(required = false) String departmentId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResult.ok(service.listInsuranceIssues(
            new InsuranceIssueFilter(status, severity, departmentId, from, to),
            new PageRequest(page, size, null)));
    }

    /**
     * 执行病案内涵质控，复用评估引擎主链路。
     */
    @PostMapping("/case-review")
    @PreAuthorize("@perm.has('evaluation.execute')")
    public ResponseEntity<ApiResult<QualityCaseReviewResponse>> caseReview(
            @RequestBody @Valid QualityCaseReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.caseReview(request)));
    }

    /**
     * 执行 DRG/DIP 入组核对。
     */
    @PostMapping("/drg-grouping")
    @PreAuthorize("@perm.has('evaluation.execute')")
    public ResponseEntity<ApiResult<DrgGroupingResponse>> drgGrouping(
            @RequestBody @Valid DrgGroupingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.drgGrouping(request)));
    }

    /**
     * 执行医保审核并在命中时联动整改闭环。
     */
    @PostMapping("/insurance-audit")
    @PreAuthorize("@perm.has('evaluation.execute')")
    public ResponseEntity<ApiResult<InsuranceAuditResponse>> insuranceAudit(
            @RequestBody @Valid InsuranceAuditRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.ok(service.insuranceAudit(request)));
    }
}
