package com.medkernel.engine.safety;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MED-C3 安全撤回 API。
 *
 * <p>发起撤回复用知识撤回权限，影响集合查询复用知识读取权限；所有请求必须带租户上下文。
 */
@RestController
@RequestMapping("/api/v1/engine/safety")
@DataScope(requireTenant = true)
public class SafetyWithdrawalController {

    private final SafetyWithdrawalService service;

    public SafetyWithdrawalController(SafetyWithdrawalService service) {
        this.service = service;
    }

    @PostMapping("/withdrawals")
    @PreAuthorize("@perm.has('knowledge.withdraw')")
    public ResponseEntity<ApiResult<SafetyWithdrawalResponse>> withdraw(
            @RequestBody @Valid SafetyWithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.withdraw(request)));
    }

    @GetMapping("/withdrawals/{withdrawalId}/impact")
    @PreAuthorize("@perm.has('knowledge.read')")
    public ApiResult<SafetyImpactResponse> impact(@PathVariable Long withdrawalId) {
        return ApiResult.ok(service.impact(withdrawalId));
    }

    @GetMapping("/withdrawals/{withdrawalId}/impact/export")
    @PreAuthorize("@perm.has('knowledge.read')")
    public void exportImpact(@PathVariable Long withdrawalId, HttpServletResponse response) throws IOException {
        String evidence = service.exportImpactEvidence(withdrawalId);
        response.setContentType("application/x-ndjson;charset=utf-8");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"safety-withdrawal-impact-" + withdrawalId + ".jsonl\"");
        try (OutputStream output = response.getOutputStream()) {
            output.write(evidence.getBytes(StandardCharsets.UTF_8));
        }
    }
}
