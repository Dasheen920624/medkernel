import { canAccessRoute, routeMetas, routeSections } from "./routes";
import type { RoutePermissionProfile, RoutePlacement, RouteSectionKey } from "./routes";

/**
 * 业务域菜单从 routes.ts 派生，页头和个人入口复用同一目录。
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
}

function routeItemsForPlacement(
  placement: RoutePlacement,
  profile?: RoutePermissionProfile,
): MenuSection[] {
  return routeSections.map((section) => ({
    ...section,
    items: routeMetas
      .filter(
        (route) =>
          route.sectionKey === section.key &&
          route.placement === placement &&
          route.menuKey &&
          route.menuLabel,
      )
      .filter((route) => profile === undefined || canAccessRoute(route, profile))
      .sort((left, right) => left.navigationOrder - right.navigationOrder)
      .map((route) => ({
        key: route.menuKey as string,
        label: route.menuLabel as string,
        path: route.path,
      })),
  }));
}

export const menuSections: MenuSection[] = routeItemsForPlacement("primary");

export function getMenuSectionsForProfile(
  profile: RoutePermissionProfile | undefined,
  placement: RoutePlacement = "primary",
): MenuSection[] {
  if (!profile) {
    return routeSections.map((section) => ({ ...section, items: [] }));
  }
  return routeItemsForPlacement(placement, profile);
}

export function getMenuItemsForProfile(
  profile: RoutePermissionProfile | undefined,
  placement: RoutePlacement,
): MenuItem[] {
  return getMenuSectionsForProfile(profile, placement).flatMap((section) => section.items);
}
