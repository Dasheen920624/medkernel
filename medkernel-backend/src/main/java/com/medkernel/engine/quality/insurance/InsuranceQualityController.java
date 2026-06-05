package com.medkernel.engine.quality.insurance;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SVC-QUALITY-02 病案医保服务包 API。
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
