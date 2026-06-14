package com.medkernel.engine.integration.masterdata;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 院内业务系统主数据同步入口和对账状态查询。
 */
@RestController
@RequestMapping("/api/v1/engine/integration/master-data")
public class MasterDataSyncController {

    private final MasterDataSyncService service;

    public MasterDataSyncController(MasterDataSyncService service) {
        this.service = service;
    }

    @PostMapping("/{webhookId}/sync")
    public ApiResult<MasterDataSyncResponse> sync(
            @PathVariable String webhookId,
            @RequestHeader("X-MedKernel-Tenant") String tenantId,
            @RequestHeader("X-MedKernel-Timestamp") String timestamp,
            @RequestHeader("X-MedKernel-Signature") String signature,
            @Valid @RequestBody MasterDataSyncRequest request) {
        return ApiResult.ok(service.sync(
            tenantId, webhookId, timestamp, signature, request));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("@perm.has('integration.read')")
    @DataScope(requireTenant = true)
    public ApiResult<MasterDataReconciliationResponse> reconciliation(
            @RequestParam String sourceSystem) {
        return ApiResult.ok(service.reconciliation(sourceSystem));
    }
}
