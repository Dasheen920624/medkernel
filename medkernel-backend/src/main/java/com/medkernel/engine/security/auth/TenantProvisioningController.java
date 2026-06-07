package com.medkernel.engine.security.auth;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 平台租户开通 API（平台级，跨租户）。开通新医院租户 + 首个管理员账号。
 *
 * <p>读用 {@code tenant.read}，开通用 {@code tenant.write}（高风险，实际仅平台管理员具备）。
 * 仅 dev/test profile（与平台账号体系一致）；内网 govcloud 走院方 IdP，不提供本入口。
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@DataScope(requireTenant = true)
@Profile({"dev", "test"})
public class TenantProvisioningController {

    private final TenantProvisioningService service;

    public TenantProvisioningController(TenantProvisioningService service) {
        this.service = service;
    }

    /** 列出客户租户，不把唯一平台主租户混入客户台账。 */
    @GetMapping
    @PreAuthorize("@perm.has('tenant.read')")
    public ApiResult<List<TenantSummary>> list() {
        return ApiResult.ok(service.listTenants());
    }

    /** 开通新租户 + 首个管理员账号（临时密码一次性返回）。 */
    @PostMapping
    @PreAuthorize("@perm.has('tenant.write')")
    public ApiResult<ProvisionTenantResponse> provision(@Valid @RequestBody ProvisionTenantRequest request) {
        return ApiResult.ok(service.provisionTenant(request));
    }
}
