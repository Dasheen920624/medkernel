import { describe, expect, it } from "vitest";
import {
  canAccessRoute,
  findRouteByPath,
  getRouteBreadcrumb,
  routeMetas,
  customerRouteMetas,
} from "./routes";

describe("route metadata", () => {
  it("registers every current frontend page route", () => {
    expect(routeMetas.length).toBeGreaterThanOrEqual(34);
    expect(findRouteByPath("/terminology/mapping")?.title).toBe("字典映射");
    expect(findRouteByPath("/advanced/graph")?.hidden).toBe(true);
  });

  it("keeps paths unique", () => {
    const paths = routeMetas.map((route) => route.path);
    expect(new Set(paths).size).toBe(paths.length);
  });

  it("does not keep hidden demo-only routes in the production router metadata", () => {
    expect(routeMetas.some((route) => route.path.includes("demo"))).toBe(false);
    expect(routeMetas.some((route) => route.title.includes("演示"))).toBe(false);
  });

  it("requires breadcrumb metadata for authenticated pages", () => {
    routeMetas
      .filter((route) => route.requireAuth)
      .forEach((route) => {
        expect(route.breadcrumb.length).toBeGreaterThan(0);
        expect(route.title.length).toBeGreaterThan(0);
      });
  });

  it("requires BASE-06 route authorization metadata for every authenticated route", () => {
    routeMetas
      .filter((route) => route.requireAuth)
      .forEach((route) => {
        expect(route.requiredPermissions, `${route.path} 缺少 requiredPermissions`).toBeDefined();
        expect(route.requiredRoles, `${route.path} 缺少 requiredRoles`).toBeDefined();
        expect(Array.isArray(route.requiredPermissions)).toBe(true);
        expect(Array.isArray(route.requiredRoles)).toBe(true);
      });
  });

  it("binds menu routes to the corresponding BASE-02 menu permission code", () => {
    routeMetas
      .filter((route) => route.requireAuth && route.sectionKey)
      .forEach((route) => {
        expect(route.requiredPermissions).toContain(`menu.${route.sectionKey}`);
      });
  });

  it("accepts backend menuKeys as the route authorization source for menu sections", () => {
    expect(
      canAccessRoute(findRouteByPath("/qc/eval/results"), {
        roles: [{ code: "qa-manager" }],
        permissions: [],
        menuKeys: ["quality-improve"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/advanced/provenance"), {
        roles: [{ code: "audit-compliance" }],
        permissions: [],
        menuKeys: ["advanced-tools"],
      }),
    ).toBe(true);
  });

  it("requires experience metadata for authenticated menu routes", () => {
    const menuRoutes = routeMetas.filter((route) => route.requireAuth && route.menuKey);

    expect(menuRoutes.length).toBeGreaterThan(0);
    menuRoutes.forEach((route) => {
      expect(route.experience, `${route.path} 缺少 experience`).toBeDefined();
      expect(route.experience?.primaryRole).toBeTruthy();
      expect(route.experience?.goal).toBeTruthy();
      expect(route.experience?.defaultView).toBeTruthy();
      expect(route.experience?.evidence).toBeTruthy();
      expect(route.experience?.interruptionLevel).toMatch(/^(none|info|weak|strong)$/);
      expect(route.experience?.dataScale.exportStrategy).toMatch(/^(none|disabled|async)$/);
      expect(route.experience?.defaultFilters.length ?? 0).toBeLessThanOrEqual(3);
    });
  });

  it("marks every authenticated route as six-state capable", () => {
    routeMetas.forEach((route) => {
      expect(route.requiresSixStates).toBe(route.requireAuth);
    });
  });

  it("requires configuration routes to use the 7-step flow and an approved state machine", () => {
    const configurationRoutes = routeMetas.filter((route) => route.pageType === "configuration");

    expect(configurationRoutes.length).toBeGreaterThan(0);
    configurationRoutes.forEach((route) => {
      expect(route.requiresStepFlow, `${route.path} 缺少 7 步流约束`).toBe(true);
      expect(["config", "change"]).toContain(route.stateMachine);
    });
  });

  it("returns customer routes without hidden advanced tools", () => {
    expect(customerRouteMetas.some((route) => route.hidden)).toBe(false);
    expect(customerRouteMetas.map((route) => route.path)).toContain("/dashboard");
    expect(customerRouteMetas.map((route) => route.path)).not.toContain("/advanced/dev-console");
  });

  it("builds breadcrumbs from route metadata", () => {
    expect(getRouteBreadcrumb("/qc/dashboard")).toEqual(["质控改进", "院级质控驾驶舱"]);
    expect(getRouteBreadcrumb("/missing")).toEqual(["未找到页面"]);
  });
});
