package com.medkernel.engine.security;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 后端提供给前端的菜单可见性收敛映射表（Menu Permission Catalog）。
 *
 * <p>这里只输出一级业务域 key；具体路由仍由前端路由元数据决定。
 * 配合 GA-ENG-BASE-02 身份权限引擎进行菜单层级的可见性物理计算与隔离。
 */
public final class MenuPermissionCatalog {

    private MenuPermissionCatalog() {
    }

    static List<String> menuKeysFor(Set<PermissionCode> permissions) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        addIfAny(keys, permissions, "workbench", PermissionCode.MENU_WORKBENCH);
        addIfAny(keys, permissions, "pilot-setup", PermissionCode.MENU_PILOT_SETUP);
        addIfAny(keys, permissions, "clinical-run", PermissionCode.MENU_CLINICAL_RUN);
        addIfAny(keys, permissions, "quality-improve", PermissionCode.MENU_QUALITY_IMPROVE);
        addIfAny(keys, permissions, "compliance-ops", PermissionCode.MENU_COMPLIANCE_OPS);
        addIfAny(keys, permissions, "advanced-tools", PermissionCode.MENU_ADVANCED_TOOLS);
        return List.copyOf(keys);
    }

    private static void addIfAny(LinkedHashSet<String> keys,
                                 Set<PermissionCode> permissions,
                                 String menuKey,
                                 PermissionCode... candidates) {
        EnumSet<PermissionCode> candidateSet = EnumSet.noneOf(PermissionCode.class);
        candidateSet.addAll(List.of(candidates));
        if (candidateSet.stream().anyMatch(permissions::contains)) {
            keys.add(menuKey);
        }
    }
}
