package com.medkernel.shared.observability;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;

/**
 * 可观测诊断查询接口。
 *
 * <p>诊断信息包含 trace 与 payload 引用，默认仅系统运维和审计角色可读。
 */
@RestController
@RequestMapping("/api/v1/engine/diagnose")
public class ObservabilityDiagnoseController {

    private final ObservabilityDiagnoseService service;

    public ObservabilityDiagnoseController(ObservabilityDiagnoseService service) {
        this.service = service;
    }

    @GetMapping("/traces/{traceId}")
    @PreAuthorize("@perm.has('system.read') or @perm.has('audit.read')")
    public ApiResult<TraceDiagnoseResponse> trace(@PathVariable String traceId) {
        return ApiResult.ok(service.findByTraceId(traceId));
    }
}
