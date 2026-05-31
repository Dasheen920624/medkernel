import { canAccessRoute, routeMetas, routeSections } from "./routes";
import type { RoutePermissionProfile, RouteSectionKey } from "./routes";

/**
 * 5 组菜单 + 27 项二级 + 5 项隐藏式高级工具。
 * 菜单从 routes.ts 派生，避免路由、面包屑、权限和菜单各写一套。
 */
export interface MenuItem {
  key: string;
  label: string;
  path: string;
}

export interface MenuSection {
  key: RouteSectionKey;
  label: string;
  items: MenuItem[];
  hidden?: boolean;
}

export const menuSections: MenuSection[] = routeSections.map((section) => ({
  ...section,
  items: routeMetas
    .filter((route) => route.sectionKey === section.key && route.menuKey && route.menuLabel)
    .map((route) => ({
      key: route.menuKey as string,
      label: route.menuLabel as string,
      path: route.path,
    })),
}));

export function getMenuSectionsForProfile(
  profile: RoutePermissionProfile | undefined,
): MenuSection[] {
  return routeSections.map((section) => ({
    ...section,
    items: routeMetas
      .filter((route) => route.sectionKey === section.key && route.menuKey && route.menuLabel)
      .filter((route) => canAccessRoute(route, profile))
      .map((route) => ({
        key: route.menuKey as string,
        label: route.menuLabel as string,
        path: route.path,
      })),
  }));
}
