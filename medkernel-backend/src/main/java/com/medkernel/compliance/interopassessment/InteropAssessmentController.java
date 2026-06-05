package com.medkernel.compliance.interopassessment;

import java.util.List;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPT-05 互联互通测评映射 API。
 */
@RestController
@RequestMapping("/api/v1/compliance/interop-assessment")
@DataScope(requireTenant = true)
public class InteropAssessmentController {

    private final InteropAssessmentService service;

    public InteropAssessmentController(InteropAssessmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<InteropAssessmentResponse> assessment(@RequestParam String standardVersion) {
        return ApiResult.ok(service.assessment(standardVersion));
    }

    @GetMapping("/gaps")
    @PreAuthorize("@perm.has('audit.read')")
    public ApiResult<List<InteropAssessmentItemResponse>> gaps(@RequestParam String standardVersion) {
        return ApiResult.ok(service.gaps(standardVersion));
    }
}
