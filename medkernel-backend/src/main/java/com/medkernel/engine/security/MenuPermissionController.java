package com.medkernel.engine.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.datascope.DataScope;

/**
 * 菜单权限目录与当前用户可见菜单接口。
 *
 * <p>角色权限包固定在代码中，租户只分配职责与组织范围，不提供按租户改写菜单或动作权限的入口。
 */
@RestController
@RequestMapping("/api/v1/security/menu-permissions")
@DataScope(requireTenant = true)
public class MenuPermissionController {

    private final EffectivePermissionService permissionService;

    public MenuPermissionController(EffectivePermissionService permissionService) {
        this.permissionService = permissionService;
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
        return ApiResult.ok(new VisibleMenuTreeResponse(
            visibleSections(profile.menuKeys()),
            visibleItems(profile.menuKeys(), MenuPermissionCatalog.MenuPlacement.HEADER),
            visibleItems(profile.menuKeys(), MenuPermissionCatalog.MenuPlacement.PROFILE)));
    }

    private List<MenuView> menuViews(List<MenuPermissionCatalog.MenuPermission> menus) {
        return menus.stream()
            .map(menu -> new MenuView(
                menu.sectionKey(),
                menu.menuKey(),
                menu.displayName(),
                menu.permission().code(),
                menu.placement()))
            .toList();
    }

    private List<VisibleMenuSection> visibleSections(List<String> menuKeys) {
        Map<String, List<MenuPermissionCatalog.MenuPermission>> sections = new LinkedHashMap<>();
        for (MenuPermissionCatalog.MenuPermission menu : MenuPermissionCatalog.allMenus()) {
            if (menu.placement() == MenuPermissionCatalog.MenuPlacement.PRIMARY
                    && menuKeys.contains(menu.menuKey())) {
                sections.computeIfAbsent(menu.sectionKey(), ignored -> new ArrayList<>()).add(menu);
            }
        }
        return sections.entrySet()
            .stream()
            .map(entry -> new VisibleMenuSection(entry.getKey(), menuViews(entry.getValue())))
            .toList();
    }

    private List<MenuView> visibleItems(
            List<String> menuKeys,
            MenuPermissionCatalog.MenuPlacement placement) {
        return menuViews(MenuPermissionCatalog.allMenus()
            .stream()
            .filter(menu -> menu.placement() == placement)
            .filter(menu -> menuKeys.contains(menu.menuKey()))
            .toList());
    }

    public record MenuCatalogResponse(
        List<MenuView> menus,
        Map<String, List<String>> defaultRoleMenuKeys
    ) {}

    public record VisibleMenuTreeResponse(
        List<VisibleMenuSection> sections,
        List<MenuView> headerItems,
        List<MenuView> profileItems
    ) {}

    public record VisibleMenuSection(
        String sectionKey,
        List<MenuView> items
    ) {}

    public record MenuView(
        String sectionKey,
        String menuKey,
        String displayName,
        String permissionCode,
        MenuPermissionCatalog.MenuPlacement placement
    ) {}

}
