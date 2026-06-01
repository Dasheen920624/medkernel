package com.medkernel.engine.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.api.error.ApiException;
import com.medkernel.shared.api.error.ErrorCode;
import com.medkernel.shared.audit.AuditAction;
import com.medkernel.shared.audit.AuditRecordCommand;
import com.medkernel.shared.audit.AuditRecorder;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 菜单权限目录与租户级覆盖接口。
 *
 * <p>INFRA-05 将菜单维度锁定为 27 个二级菜单 + 5 个高级工具，本控制器只暴露该细粒度目录。
 */
@RestController
@RequestMapping("/api/v1/security/menu-permissions")
@DataScope(requireTenant = true)
public class MenuPermissionController {

    private final EffectivePermissionService permissionService;
    private final RolePermissionOverrideRepository rolePermissionRepository;
    private final AuditRecorder auditRecorder;

    public MenuPermissionController(EffectivePermissionService permissionService,
                                    RolePermissionOverrideRepository rolePermissionRepository,
                                    AuditRecorder auditRecorder) {
        this.permissionService = permissionService;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/catalog")
    @PreAuthorize("@perm.has('org.read')")
    public ApiResult<MenuCatalogResponse> catalog() {
        Map<String, List<String>> defaultRoleMenuKeys = new LinkedHashMap<>();
        for (RoleCode role : RoleCode.values()) {
            defaultRoleMenuKeys.put(
                role.code(),
                MenuPermissionCatalog.menuKeysFor(DefaultPermissionPolicy.permissionsOf(role)));
        }
        return ApiResult.ok(new MenuCatalogResponse(menuViews(MenuPermissionCatalog.allMenus()), defaultRoleMenuKeys));
    }

    @GetMapping("/visible")
    @PreAuthorize("isAuthenticated()")
    public ApiResult<VisibleMenuTreeResponse> visible(Authentication authentication) {
        EffectivePermissionProfile profile = permissionService.resolve(
            authentication,
            RequestContext.currentOrgScope(),
            RequestContext.currentUserId().orElse(authentication == null ? null : authentication.getName()));
        return ApiResult.ok(new VisibleMenuTreeResponse(visibleSections(profile.menuKeys())));
    }

    @PatchMapping("/overrides")
    @PreAuthorize("@perm.has('org.write')")
    public ApiResult<MenuOverrideResponse> override(@Valid @RequestBody MenuOverrideRequest request) {
        String tenantId = RequestContext.currentOrgScope().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw ApiException.tenantMissing();
        }
        RoleCode role = RoleCode.fromCode(request.roleCode())
            .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "非法的系统角色编码: " + request.roleCode()));
        PermissionCode permission;
        try {
            permission = MenuPermissionCatalog.permissionForMenuKey(request.menuKey());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.BAD_REQUEST, exception.getMessage(), exception);
        }

        String actor = RequestContext.currentUserId().orElse("system");
        Instant now = Instant.now();
        RolePermissionOverride existing = rolePermissionRepository
            .findByTenantIdAndRoleCodeAndPermissionCode(tenantId, role.code(), permission.code())
            .orElse(null);
        RolePermissionOverride saved = rolePermissionRepository.save(new RolePermissionOverride(
            existing == null ? null : existing.id(),
            tenantId,
            role.code(),
            permission.code(),
            request.effect(),
            existing == null ? now : existing.createdAt(),
            existing == null ? actor : existing.createdBy(),
            now,
            actor));

        auditRecorder.record(new AuditRecordCommand(
            AuditAction.PERMISSION_CHANGE,
            "role_permission",
            tenantId + ":" + role.code() + ":" + permission.code(),
            "调整租户角色菜单权限覆盖：" + role.code() + " / " + request.menuKey(),
            auditSnapshot(existing, request.menuKey()),
            auditSnapshot(saved, request.menuKey()),
            null));

        return ApiResult.ok(new MenuOverrideResponse(
            saved.tenantId(),
            saved.roleCode(),
            request.menuKey(),
            saved.permissionCode(),
            saved.effect()));
    }

    private List<MenuView> menuViews(List<MenuPermissionCatalog.MenuPermission> menus) {
        return menus.stream()
            .map(menu -> new MenuView(
                menu.sectionKey(),
                menu.menuKey(),
                menu.displayName(),
                menu.permission().code()))
            .toList();
    }

    private List<VisibleMenuSection> visibleSections(List<String> menuKeys) {
        Map<String, List<MenuPermissionCatalog.MenuPermission>> sections = new LinkedHashMap<>();
        for (MenuPermissionCatalog.MenuPermission menu : MenuPermissionCatalog.allMenus()) {
            if (menuKeys.contains(menu.menuKey())) {
                sections.computeIfAbsent(menu.sectionKey(), ignored -> new ArrayList<>()).add(menu);
            }
        }
        return sections.entrySet()
            .stream()
            .map(entry -> new VisibleMenuSection(entry.getKey(), menuViews(entry.getValue())))
            .toList();
    }

    private MenuOverrideAuditSnapshot auditSnapshot(RolePermissionOverride override, String menuKey) {
        if (override == null) {
            return null;
        }
        return new MenuOverrideAuditSnapshot(
            override.tenantId(),
            override.roleCode(),
            menuKey,
            override.permissionCode(),
            override.effect());
    }

    public record MenuCatalogResponse(
        List<MenuView> menus,
        Map<String, List<String>> defaultRoleMenuKeys
    ) {}

    public record VisibleMenuTreeResponse(
        List<VisibleMenuSection> sections
    ) {}

    public record VisibleMenuSection(
        String sectionKey,
        List<MenuView> items
    ) {}

    public record MenuView(
        String sectionKey,
        String menuKey,
        String displayName,
        String permissionCode
    ) {}

    public record MenuOverrideRequest(
        @NotBlank(message = "角色编码不能为空")
        String roleCode,

        @NotBlank(message = "菜单 key 不能为空")
        String menuKey,

        @NotNull(message = "覆盖效果不能为空")
        PermissionEffect effect
    ) {}

    public record MenuOverrideResponse(
        String tenantId,
        String roleCode,
        String menuKey,
        String permissionCode,
        PermissionEffect effect
    ) {}

    private record MenuOverrideAuditSnapshot(
        String tenantId,
        String roleCode,
        String menuKey,
        String permissionCode,
        PermissionEffect effect
    ) {}
}
