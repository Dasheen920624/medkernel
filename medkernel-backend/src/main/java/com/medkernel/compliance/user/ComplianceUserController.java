package com.medkernel.compliance.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.engine.security.auth.PasswordResetTokenResponse;
import com.medkernel.engine.security.auth.ResetPasswordResponse;
import com.medkernel.engine.security.auth.SetStatusRequest;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.PageRequest;
import com.medkernel.shared.api.PageResponse;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 身份安全服务包的统一用户管理 API。
 */
@RestController
@RequestMapping("/api/v1/compliance/users")
@DataScope(requireTenant = true)
public class ComplianceUserController {

    private static final String READ_GUARD =
        "@perm.has('org.read') and hasAnyRole('SYSTEM_SUPERADMIN','PLATFORM_ADMIN','GROUP_ADMIN','HOSPITAL_ADMIN')";
    private static final String WRITE_GUARD =
        "@perm.has('org.write') and hasAnyRole('SYSTEM_SUPERADMIN','PLATFORM_ADMIN','GROUP_ADMIN','HOSPITAL_ADMIN')";

    private final ComplianceUserService service;

    public ComplianceUserController(ComplianceUserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_GUARD)
    public ApiResult<PageResponse<ComplianceUserSummary>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ApiResult.ok(service.page(new PageRequest(page, size, "username,asc")));
    }

    @GetMapping("/{userId}")
    @PreAuthorize(READ_GUARD)
    public ApiResult<ComplianceUserDetail> detail(@PathVariable String userId) {
        return ApiResult.ok(service.detail(userId));
    }

    @PostMapping
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<ComplianceUserCreateResponse> create(
            @Valid @RequestBody ComplianceUserCreateRequest request,
            Authentication authentication) {
        return ApiResult.ok(service.create(request, authentication));
    }

    @PostMapping("/{userId}:reset-password")
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<ResetPasswordResponse> resetPassword(@PathVariable String userId) {
        return ApiResult.ok(service.resetPassword(userId));
    }

    @PostMapping("/{userId}:reset-password-token")
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<PasswordResetTokenResponse> issueResetToken(@PathVariable String userId) {
        return ApiResult.ok(service.issueResetToken(userId));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<ComplianceUserDetail> setStatus(
            @PathVariable String userId,
            @Valid @RequestBody SetStatusRequest request) {
        return ApiResult.ok(service.setStatus(userId, request.status()));
    }

    @PostMapping("/{userId}/roles")
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<ComplianceUserDetail> assignRole(
            @PathVariable String userId,
            @Valid @RequestBody ComplianceUserRoleRequest request,
            Authentication authentication) {
        return ApiResult.ok(service.assignRole(userId, request, authentication));
    }

    @DeleteMapping("/{userId}/roles/{roleCode}")
    @PreAuthorize(WRITE_GUARD)
    public ApiResult<ComplianceUserDetail> removeRole(
            @PathVariable String userId,
            @PathVariable String roleCode,
            @RequestParam String scopeLevel,
            @RequestParam String scopeCode) {
        return ApiResult.ok(service.removeRole(userId, roleCode, scopeLevel, scopeCode));
    }
}
