package com.medkernel.engine.integration.runtime;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.context.ContextSnapshotRequest;
import com.medkernel.engine.context.ContextSnapshotResponse;
import com.medkernel.engine.pkg.PackageSyncRequest;
import com.medkernel.engine.pkg.PackageSyncResponse;
import com.medkernel.engine.versioning.InheritanceOverride;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 第三方知识运行时 v1 稳定契约入口。
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

    @GetMapping("/effective-package")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<ThirdPartyEffectivePackageResponse> resolveEffectivePackage(
            @RequestParam @NotBlank @Size(max = 128) String packageCode,
            @RequestParam @NotBlank @Size(max = 64) String packageVersion,
            @RequestParam @NotBlank @Size(max = 64) String targetOrgUnitId,
            @RequestParam(required = false) @Size(max = 64) String specialtyId,
            @RequestParam(required = false) @Size(max = 64) String scenarioCode,
            @RequestParam(required = false) @Size(max = 64) String careSetting,
            @RequestParam(required = false) @Size(max = 64) String cohort,
            @RequestParam(required = false) @Size(max = 64) String role,
            @RequestParam(required = false) Instant effectiveAt) {
        return ApiResult.ok(service.resolveEffectivePackage(new ThirdPartyEffectivePackageQuery(
            packageCode,
            packageVersion,
            targetOrgUnitId,
            specialtyId,
            scenarioCode,
            careSetting,
            cohort,
            role,
            effectiveAt)));
    }

    @PostMapping("/context-snapshots")
    @PreAuthorize("@perm.has('context.write')")
    public ResponseEntity<ApiResult<ContextSnapshotResponse>> writeContext(
            @Valid @RequestBody ContextSnapshotRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.writeContext(request, idempotencyKey)));
    }

    @PostMapping("/overrides")
    @PreAuthorize("@perm.has('tenant.override')")
    public ResponseEntity<ApiResult<InheritanceOverride>> createOverride(
            @Valid @RequestBody ThirdPartyOverrideRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResult.ok(service.createOverride(request)));
    }

    @PostMapping("/overrides/{overrideId}:retire")
    @PreAuthorize("@perm.has('tenant.override')")
    public ApiResult<InheritanceOverride> retireOverride(
            @PathVariable String overrideId,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        return ApiResult.ok(service.retireOverride(overrideId));
    }

    @PostMapping("/packages/{packageId}:distribute")
    @PreAuthorize("@perm.has('package.publish')")
    public ApiResult<PackageSyncResponse> distributePackage(
            @PathVariable String packageId,
            @Valid @RequestBody PackageSyncRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey) {
        return ApiResult.ok(service.distributePackage(packageId, request));
    }

    @GetMapping("/packages/{packageId}/reconciliation")
    @PreAuthorize("@perm.has('package.read')")
    public ApiResult<ThirdPartyPackageReconciliationResponse> reconcilePackage(
            @PathVariable String packageId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResult.ok(service.reconcilePackage(packageId, new PageRequest(page, size, null)));
    }
}
