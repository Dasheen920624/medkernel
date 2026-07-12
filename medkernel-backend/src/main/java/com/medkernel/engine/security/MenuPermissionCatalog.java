package com.medkernel.engine.security;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 后端菜单权限目录（Menu Permission Catalog）。
 *
 * <p>运行时只读取由产品入口唯一合同生成的类路径资源，禁止在 Java 中再次维护入口集合、
 * 路由、展示位置或职责快照。
 */
public final class MenuPermissionCatalog {

    private static final String CATALOG_RESOURCE = "/catalog/menu-permission-catalog.generated.json";
    private static final String CATALOG_ID = "medkernel-menu-permission-catalog";
    private static final String SOURCE_CATALOG_ID = "medkernel-product-entry-catalog";
    private static final String GENERATED_FROM = "docs/contracts/product/product-entry-catalog.v1.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<MenuPermission> ALL_MENUS = loadCatalog();

    private MenuPermissionCatalog() {
    }

    public static List<MenuPermission> allMenus() {
        return ALL_MENUS;
    }

    public static List<String> allMenuKeys() {
        return ALL_MENUS.stream().map(MenuPermission::menuKey).toList();
    }

    public static boolean isCatalogMenuPermission(PermissionCode permission) {
        return permission != null && ALL_MENUS.stream().anyMatch(menu -> menu.permission() == permission);
    }

    public static PermissionCode permissionForMenuKey(String menuKey) {
        return ALL_MENUS.stream()
            .filter(menu -> menu.menuKey().equals(menuKey))
            .findFirst()
            .map(MenuPermission::permission)
            .orElseThrow(() -> new IllegalArgumentException("未登记菜单 key: " + menuKey));
    }

    static List<String> menuKeysFor(Set<PermissionCode> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return ALL_MENUS.stream()
            .filter(menu -> permissions.contains(menu.permission()))
            .map(MenuPermission::menuKey)
            .toList();
    }

    private static List<MenuPermission> loadCatalog() {
        try (InputStream input = MenuPermissionCatalog.class.getResourceAsStream(CATALOG_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("入口目录生成资源不存在：" + CATALOG_RESOURCE);
            }
            GeneratedCatalog catalog = OBJECT_MAPPER.readValue(input, GeneratedCatalog.class);
            validateCatalogHeader(catalog);
            return parseMenus(catalog.menus());
        } catch (IOException exception) {
            throw new IllegalStateException("入口目录生成资源读取失败：" + CATALOG_RESOURCE, exception);
        }
    }

    private static void validateCatalogHeader(GeneratedCatalog catalog) {
        if (catalog == null
                || !"1.0.0".equals(catalog.schemaVersion())
                || !CATALOG_ID.equals(catalog.catalogId())
                || !SOURCE_CATALOG_ID.equals(catalog.sourceCatalogId())
                || !GENERATED_FROM.equals(catalog.generatedFrom())
                || catalog.sourceCatalogSha256() == null
                || !catalog.sourceCatalogSha256().matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("入口目录生成资源头信息非法：" + CATALOG_RESOURCE);
        }
    }

    private static List<MenuPermission> parseMenus(List<GeneratedMenu> generatedMenus) {
        if (generatedMenus == null || generatedMenus.isEmpty()) {
            throw new IllegalStateException("入口目录生成资源不得为空：" + CATALOG_RESOURCE);
        }
        Set<String> menuKeys = new HashSet<>();
        Set<String> routes = new HashSet<>();
        List<MenuPermission> menus = generatedMenus.stream()
            .map(menu -> parseMenu(menu, menuKeys, routes))
            .toList();
        return List.copyOf(menus);
    }

    private static MenuPermission parseMenu(
            GeneratedMenu generated,
            Set<String> menuKeys,
            Set<String> routes) {
        if (generated == null) {
            throw new IllegalStateException("入口目录生成资源包含空行");
        }
        String sectionKey = requireText(generated.sectionKey(), "sectionKey");
        String menuKey = requireText(generated.menuKey(), "menuKey");
        String displayName = requireText(generated.displayName(), "displayName");
        String route = requireText(generated.route(), "route");
        if (!menuKeys.add(menuKey)) {
            throw new IllegalStateException("入口目录 menuKey 重复：" + menuKey);
        }
        if (!route.startsWith("/") || !routes.add(route)) {
            throw new IllegalStateException("入口目录 route 非法或重复：" + route);
        }

        PermissionCode permission = PermissionCode.fromCode(
                requireText(generated.permissionCode(), "permissionCode"))
            .orElseThrow(() -> new IllegalStateException("入口目录权限未登记：" + generated.permissionCode()));
        if (permission.dimension() != PermissionDimension.MENU || !menuKey.equals(permission.target())) {
            throw new IllegalStateException("入口目录权限与 menuKey 不一致：" + menuKey);
        }

        MenuPlacement placement;
        try {
            placement = MenuPlacement.valueOf(requireText(generated.placement(), "placement"));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("入口目录 placement 非法：" + generated.placement(), exception);
        }

        Set<RoleCode> responsibilityRoles = parseResponsibilityRoles(
            generated.responsibilityRoles(), menuKey);
        return new MenuPermission(
            sectionKey,
            menuKey,
            displayName,
            permission,
            placement,
            route,
            responsibilityRoles);
    }

    private static Set<RoleCode> parseResponsibilityRoles(List<String> roleCodes, String menuKey) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new IllegalStateException("入口目录职责不得为空：" + menuKey);
        }
        Set<RoleCode> roles = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            RoleCode role = RoleCode.fromCode(roleCode)
                .filter(RoleCode::customerAssignable)
                .orElseThrow(() -> new IllegalStateException("入口目录职责非法：" + roleCode));
            if (!roles.add(role)) {
                throw new IllegalStateException("入口目录职责重复：" + menuKey + "/" + roleCode);
            }
        }
        return Collections.unmodifiableSet(roles);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("入口目录字段为空：" + field);
        }
        return value;
    }

    /** 产品入口展示位置。 */
    public enum MenuPlacement {
        PRIMARY,
        HEADER,
        PROFILE
    }

    /**
     * 从唯一产品入口合同生成的后端运行时入口。
     *
     * @param sectionKey 所属一级业务域
     * @param menuKey 产品入口编码
     * @param displayName 中文入口名称
     * @param permission 菜单权限
     * @param placement 展示位置
     * @param route 前端唯一入口路由
     * @param responsibilityRoles 对该入口负责的客户职责集合
     */
    public record MenuPermission(
        String sectionKey,
        String menuKey,
        String displayName,
        PermissionCode permission,
        MenuPlacement placement,
        String route,
        Set<RoleCode> responsibilityRoles
    ) {}

    private record GeneratedCatalog(
        String schemaVersion,
        String catalogId,
        String generatedFrom,
        String sourceCatalogId,
        String sourceCatalogSha256,
        List<GeneratedMenu> menus
    ) {}

    private record GeneratedMenu(
        String sectionKey,
        String menuKey,
        String displayName,
        String permissionCode,
        String placement,
        String route,
        List<String> responsibilityRoles
    ) {}
}
