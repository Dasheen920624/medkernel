import { describe, expect, it } from "vitest";
import {
  canAccessRoute,
  findRouteByPath,
  getRouteBreadcrumb,
  routeMetas,
  customerRouteMetas,
} from "./routes";
import { ROLE_OPTIONS } from "./roleCatalog";

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
    expect(routeMetas.map((route) => route.path)).not.toContain("/config/packages/demo");
    expect(routeMetas.some((route) => route.title.includes("StepFlow"))).toBe(false);
  });

  it("registers the WORKBENCH-02 production demo-validation route without opening a new menu slot", () => {
    const route = findRouteByPath("/workbench/demo-validation");

    expect(route).toMatchObject({
      title: "演示与校验",
      sectionKey: "workbench",
      menuKey: "demo-validation",
      menuLabel: "演示与校验",
      hidden: true,
      pageType: "workbench",
    });
    expect(route?.requiredPermissions).toEqual(["menu.workbench", "workbench:demo:view"]);
    expect(route?.requiredRoles).toEqual([
      "implementation-engineer",
      "it-ops",
      "hospital-admin",
      "platform-admin",
      "system-superadmin",
    ]);
  });

  it("makes the dashboard an explicit default landing route for customer roles and system superadmin", () => {
    const route = findRouteByPath("/dashboard");
    const customerRoleCodes = ROLE_OPTIONS.map((role) => role.code);
    const dashboardRoleCodes = [...customerRoleCodes, "system-superadmin"];

    expect(route?.requiredPermissions).toEqual(["menu.workbench"]);
    expect(route?.requiredRoles).toEqual(dashboardRoleCodes);
    dashboardRoleCodes.forEach((roleCode) => {
      expect(
        canAccessRoute(route, {
          roles: [{ code: roleCode }],
          permissions: [],
          menuKeys: ["workbench"],
        }),
      ).toBe(true);
    });
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

  it("binds menu routes to the INFRA-05 second-level menu permission code", () => {
    routeMetas
      .filter((route) => route.requireAuth && route.sectionKey && route.menuKey && !route.hidden)
      .forEach((route) => {
        expect(route.requiredPermissions).toContain(`menu.${route.menuKey}`);
        if (route.menuKey !== route.sectionKey) {
          expect(route.requiredPermissions).not.toContain(`menu.${route.sectionKey}`);
        }
      });
  });

  it("accepts backend menuKeys as the route authorization source for second-level menus", () => {
    expect(
      canAccessRoute(findRouteByPath("/qc/eval/results"), {
        roles: [{ code: "qa-manager" }],
        permissions: [],
        menuKeys: ["qc-eval-results"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/advanced/provenance"), {
        roles: [{ code: "audit-compliance" }],
        permissions: [],
        menuKeys: ["provenance"],
      }),
    ).toBe(true);
  });

  it("requires the WORKBENCH-02 action permission in addition to the workbench menu", () => {
    expect(
      canAccessRoute(findRouteByPath("/workbench/demo-validation"), {
        roles: [{ code: "implementation-engineer" }],
        permissions: [{ code: "workbench:demo:view" }],
        menuKeys: ["workbench"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/workbench/demo-validation"), {
        roles: [{ code: "doctor" }],
        permissions: [{ code: "workbench:demo:view" }],
        menuKeys: ["workbench"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(findRouteByPath("/workbench/demo-validation"), {
        roles: [{ code: "implementation-engineer" }],
        permissions: [],
        menuKeys: ["workbench"],
      }),
    ).toBe(false);
  });

  it("rejects legacy first-level section menuKeys for second-level routes", () => {
    expect(
      canAccessRoute(findRouteByPath("/terminology/mapping"), {
        roles: [{ code: "implementation-engineer" }],
        permissions: [],
        menuKeys: ["pilot-setup"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(findRouteByPath("/qc/eval/results"), {
        roles: [{ code: "qa-manager" }],
        permissions: [],
        menuKeys: ["quality-improve"],
      }),
    ).toBe(false);
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
