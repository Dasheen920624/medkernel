package com.medkernel.engine.security.auth;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;

/**
 * 平台成员账号（凭证）管理 API。租户管理员开通成员、重置临时密码、启用/停用。
 *
 * <p>类级 {@link DataScope#requireTenant} 强制租户隔离；读用 {@code org.read}，写用 {@code org.write}。
 * 仅 dev/test profile（与平台登录一致）；内网 govcloud 走院方 IdP，不提供本入口。
 */
@RestController
@RequestMapping("/api/v1/admin/credentials")
@DataScope(requireTenant = true)
@Profile({"dev", "test"})
public class CredentialAdminController {

    private final CredentialAdminService service;

    public CredentialAdminController(CredentialAdminService service) {
        this.service = service;
    }

    /** 列出当前租户成员账号（不含口令哈希）。 */
    @GetMapping
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<List<CredentialSummary>> list() {
        return ApiResult.ok(service.list());
    }

    /** 开通成员账号（可授角色，临时密码一次性返回）。 */
    @PostMapping
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<CreateMemberResponse> create(@Valid @RequestBody CreateMemberRequest request) {
        return ApiResult.ok(service.createMember(request));
    }

    /** 重置成员临时密码（须首登改密）。 */
    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<ResetPasswordResponse> resetPassword(@PathVariable String userId) {
        return ApiResult.ok(service.resetPassword(userId));
    }

    /** 发放一次性受控重置 token（明文仅返回一次）。 */
    @PostMapping("/{userId}/reset-password-token")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<PasswordResetTokenResponse> issueResetToken(@PathVariable String userId) {
        return ApiResult.ok(service.issueResetToken(userId));
    }

    /** 启用 / 停用 / 锁定成员账号。 */
    @PatchMapping("/{userId}/status")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<Void> setStatus(@PathVariable String userId, @Valid @RequestBody SetStatusRequest request) {
        service.setStatus(userId, request.status());
        return ApiResult.ok(null);
    }
}
