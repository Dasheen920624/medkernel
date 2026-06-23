package com.medkernel.engine.integration.runtime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 第三方临床运行 v1 稳定契约入口。
 */
@RestController
@Validated
@RequestMapping("/api/v1/engine/integration/knowledge-runtime")
@DataScope(requireTenant = true)
public class ThirdPartyKnowledgeRuntimeController {

    private final ThirdPartyKnowledgeRuntimeService service;

    public ThirdPartyKnowledgeRuntimeController(ThirdPartyKnowledgeRuntimeService service) {
        this.service = service;
    }

    /**
     * 查询认证医院当前不可变运行修订。
     */
    @GetMapping("/runtime-release/current")
    @PreAuthorize("@perm.has('asset.read')")
    public ApiResult<ThirdPartyRuntimeReleaseResponse> currentRuntimeRelease() {
        return ApiResult.ok(service.resolveCurrentRuntimeRelease());
    }

    /**
     * 写入临床上下文；调用方不得携带运行修订、发布容器或资产版本选择。
     */
    @PostMapping("/context-snapshots")
    @PreAuthorize("@perm.has('context.write')")
    public ResponseEntity<ApiResult<ContextSnapshotResponse>> writeContext(
            @Valid @RequestBody ContextSnapshotRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.writeContext(request, idempotencyKey)));
    }
}
