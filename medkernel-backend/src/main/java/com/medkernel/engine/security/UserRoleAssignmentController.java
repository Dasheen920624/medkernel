package com.medkernel.engine.security;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * 用户角色分配关系（UserRoleAssignment）REST 控制器。
 *
 * <p>支撑 E6 阶段 GA-SVC-COMPLIANCE-01 身份安全服务包，提供组织内部用户的角色与数据范围绑定管理。
 */
@RestController
@RequestMapping("/api/v1/compliance/user-roles")
@DataScope(requireTenant = true)
public class UserRoleAssignmentController {

    private final UserRoleAssignmentRepository repository;

    public UserRoleAssignmentController(UserRoleAssignmentRepository repository) {
        this.repository = repository;
    }

    private String requireTenantId() {
        return RequestContext.currentOrgScope().tenantId();
    }

    private String currentActor() {
        return RequestContext.currentUserId().orElse("system");
    }

    /**
     * 获取当前租户下所有的用户角色分配记录。
     *
     * @return 包含分配列表的统一 ApiResult 返回包
     */
    @GetMapping
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<List<UserRoleAssignment>> getAssignments() {
        String tenantId = requireTenantId();
        return ApiResult.ok(repository.findByTenantId(tenantId));
    }

    /**
     * 新增/绑定一条用户角色与组织数据范围的映射关系。
     *
     * @param request 创建请求传输对象
     * @return 已持久化落库的分配关系实体
     */
    @PostMapping
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<UserRoleAssignment> createAssignment(@Valid @RequestBody AssignmentCreateRequest request) {
        String tenantId = requireTenantId();

        RoleCode role = RoleCode.fromCode(request.roleCode())
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "非法的系统角色编码: " + request.roleCode()));
        SystemSuperAdminGuard.assertTenantManagedRole(role.code());

        UserRoleAssignment toSave = new UserRoleAssignment(
            null,
            tenantId,
            request.userId(),
            role.code(),
            request.scopeLevel() == null ? "TENANT" : request.scopeLevel(),
            request.scopeCode() == null ? tenantId : request.scopeCode(),
            "Y",
            Instant.now(),
            currentActor(),
            Instant.now(),
            currentActor()
        );

        return ApiResult.ok(repository.save(toSave));
    }

    /**
     * 物理移去/解除某个用户的角色与作用域范围映射。
     *
     * @param id 分配记录物理主键
     * @return 空响应包
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<Void> deleteAssignment(@PathVariable("id") Long id) {
        String tenantId = requireTenantId();
        UserRoleAssignment existing = repository.findById(id)
            .orElseThrow(() -> ApiException.notFound("用户角色分配关系 id=" + id));

        if (!existing.tenantId().equals(tenantId)) {
            throw ApiException.forbidden("无权删除非本租户的用户角色分配记录");
        }
        SystemSuperAdminGuard.assertAssignmentMutable(existing);

        repository.delete(existing);
        return ApiResult.empty();
    }

    /**
     * 分配创建请求负载。
     */
    public record AssignmentCreateRequest(
        @NotBlank(message = "用户ID不能为空")
        String userId,

        @NotBlank(message = "系统角色编码不能为空")
        String roleCode,

        String scopeLevel,
        String scopeCode
    ) {}
}
