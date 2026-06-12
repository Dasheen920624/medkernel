import { describe, expect, it } from "vitest";
import {
  canAccessRoute,
  findRouteByPath,
  getRouteBreadcrumb,
  routeMetas,
  routeSections,
  customerRouteMetas,
} from "./routes";
import { ROLE_OPTIONS } from "./roleCatalog";

describe("route metadata", () => {
  it("registers every current frontend page route", () => {
    expect(routeMetas.length).toBeGreaterThanOrEqual(34);
    expect(findRouteByPath("/terminology/mapping")?.title).toBe("术语与字典");
    expect(findRouteByPath("/advanced/graph")?.placement).toBe("expert");
  });

  it("locks five stable customer domains and explicit entry placements", () => {
    expect(routeSections.map((section) => [section.key, section.label])).toEqual([
      ["workbench", "工作台"],
      ["institution-governance", "机构治理"],
      ["knowledge-configuration", "知识配置"],
      ["clinical-collaboration", "临床协同"],
      ["quality-operations", "质量与运营"],
    ]);

    const placementCounts = routeMetas
      .filter((route) => route.requireAuth && route.menuKey)
      .reduce<Record<string, number>>((counts, route) => {
        counts[route.placement] = (counts[route.placement] ?? 0) + 1;
        return counts;
      }, {});

    expect(placementCounts).toEqual({
      primary: 23,
      header: 1,
      profile: 1,
      expert: 5,
    });
  });

  it("removes duplicate menu permissions from merged pages", () => {
    expect(findRouteByPath("/rule/validate")).toMatchObject({
      sectionKey: "knowledge-configuration",
      placement: "hidden",
      hidden: true,
      requiredPermissions: ["menu.rule-definitions", "rule.read"],
    });
    expect(findRouteByPath("/rule/validate")?.menuKey).toBeUndefined();

    expect(findRouteByPath("/qc/eval/results")).toMatchObject({
      sectionKey: "quality-operations",
      placement: "hidden",
      hidden: true,
      requiredPermissions: ["menu.qc-alerts", "evaluation.read"],
    });
    expect(findRouteByPath("/qc/eval/results")?.menuKey).toBeUndefined();
  });

  it("places cross-domain and expert entries outside the primary sidebar", () => {
    expect(findRouteByPath("/notifications")).toMatchObject({
      sectionKey: "workbench",
      placement: "header",
      menuLabel: "消息通知",
    });
    expect(findRouteByPath("/notifications/settings")).toMatchObject({
      sectionKey: "workbench",
      placement: "profile",
      menuLabel: "通知偏好",
    });
    expect(findRouteByPath("/advanced/provenance")).toMatchObject({
      sectionKey: "knowledge-configuration",
      placement: "expert",
      menuLabel: "来源与血缘",
    });
    expect(findRouteByPath("/advanced/domestic")).toMatchObject({
      sectionKey: "quality-operations",
      placement: "expert",
      menuLabel: "国产化核验",
    });
  });

  it("keeps paths unique", () => {
    const paths = routeMetas.map((route) => route.path);
    expect(new Set(paths).size).toBe(paths.length);
  });

  it("uses only canonical role codes in route access rules", () => {
    const canonicalRoleCodes = new Set<string>([
      ...ROLE_OPTIONS.map((role) => role.code),
      "system-superadmin",
    ]);

    routeMetas.forEach((route) => {
      expect(new Set(route.requiredRoles).size, `${route.path} 不得重复登记职责角色`).toBe(
        route.requiredRoles.length,
      );
      expect(
        route.requiredRoles.filter((roleCode) => !canonicalRoleCodes.has(roleCode)),
        route.path,
      ).toEqual([]);
    });
  });

  it("does not keep hidden demo-only routes in the production router metadata", () => {
    expect(routeMetas.map((route) => route.path)).not.toContain("/config/packages/demo");
    expect(routeMetas.some((route) => route.title.includes("StepFlow"))).toBe(false);
  });

  it("registers the WORKBENCH-02 readiness validation route without opening a new menu slot", () => {
    const route = findRouteByPath("/workbench/readiness-validation");

    expect(route).toMatchObject({
      title: "验收自检",
      sectionKey: "workbench",
      hidden: true,
      placement: "hidden",
      pageType: "workbench",
    });
    expect(route?.menuKey).toBeUndefined();
    expect(route?.menuLabel).toBeUndefined();
    expect(route?.requiredPermissions).toEqual(["menu.workbench", "workbench:readiness:view"]);
    expect(route?.requiredRoles).toEqual([
      "implementation-operator",
      "integration-operator",
      "organization-admin",
      "platform-governance-admin",
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

  it("requires evaluation read permission for every primary quality workspace", () => {
    const routes = [
      ["/qc/dashboard", "qc-dashboard"],
      ["/qc/alerts", "qc-alerts"],
      ["/qc/insurance", "insurance-audit"],
      ["/qc/eval/sets", "qc-eval-sets"],
    ] as const;

    routes.forEach(([path, menuKey]) => {
      const route = findRouteByPath(path);
      expect(route?.requiredPermissions).toEqual([`menu.${menuKey}`, "evaluation.read"]);
      expect(
        canAccessRoute(route, {
          roles: [{ code: "integration-operator" }],
          permissions: [],
          menuKeys: [menuKey],
        }),
        path,
      ).toBe(false);
    });
  });

  it("requires audit read permission for the audit workspace", () => {
    const route = findRouteByPath("/admin/audit");

    expect(route?.requiredPermissions).toEqual(["menu.admin-audit", "audit.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [],
        menuKeys: ["admin-audit"],
      }),
    ).toBe(false);
  });

  it("lets the built-in superadmin use every backend-granted authenticated route", () => {
    const authenticatedRoutes = routeMetas.filter((route) => route.requireAuth);
    const permissions = Array.from(
      new Set(authenticatedRoutes.flatMap((route) => route.requiredPermissions)),
      (code) => ({ code }),
    );
    const menuKeys = authenticatedRoutes
      .map((route) => route.menuKey)
      .filter((menuKey): menuKey is string => Boolean(menuKey));
    const profile = {
      roles: [{ code: "system-superadmin" }],
      permissions,
      menuKeys,
    };

    authenticatedRoutes.forEach((route) => {
      expect(canAccessRoute(route, profile), route.path).toBe(true);
    });
  });

  it("limits the implementation guide to implementation and administrator roles", () => {
    const route = findRouteByPath("/onboarding/guide");

    expect(route?.requiredPermissions).toEqual(["menu.implementation-guide", "tenant.read"]);
    expect(route?.requiredRoles).toEqual([
      "implementation-operator",
      "platform-governance-admin",
      "organization-admin",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "implementation-operator" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["implementation-guide"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "organization-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["implementation-guide"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["implementation-guide"],
      }),
    ).toBe(false);
  });

  it("limits tenant onboarding to tenant readers in implementation and administrator roles", () => {
    const route = findRouteByPath("/tenant/onboarding");

    expect(route?.title).toBe("服务机构");
    expect(route?.requiredPermissions).toEqual(["menu.tenant-onboarding", "tenant.read"]);
    expect(route?.requiredRoles).toEqual([
      "implementation-operator",
      "platform-governance-admin",
      "organization-admin",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "organization-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["tenant-onboarding"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "organization-admin" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["tenant-onboarding"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [{ code: "tenant.read" }],
        menuKeys: ["tenant-onboarding"],
      }),
    ).toBe(false);
  });

  it("requires both the operations menu and system read permission for runtime status", () => {
    const route = findRouteByPath("/system/providers");

    expect(route?.requiredPermissions).toEqual(["menu.system-providers", "system.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [{ code: "system.read" }],
        menuKeys: ["system-providers"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "compliance-auditor" }],
        permissions: [],
        menuKeys: ["system-providers"],
      }),
    ).toBe(false);
  });

  it("limits config packages to package publishers with the pilot setup menu", () => {
    const route = findRouteByPath("/config/packages");

    expect(route?.requiredPermissions).toEqual([
      "menu.config-packages",
      "package.read",
      "package.publish",
    ]);
    expect(route?.requiredRoles).toEqual([
      "implementation-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "platform-governance-admin",
      "organization-admin",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "implementation-operator" }],
        permissions: [{ code: "package.read" }, { code: "package.publish" }],
        menuKeys: ["config-packages"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-knowledge-governor" }],
        permissions: [{ code: "package.read" }, { code: "package.publish" }],
        menuKeys: ["config-packages"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-governor" }],
        permissions: [{ code: "package.read" }, { code: "package.publish" }],
        menuKeys: ["config-packages"],
      }),
    ).toBe(false);
  });

  it("requires domain read permissions for rule and pathway configuration workspaces", () => {
    expect(findRouteByPath("/pathway/templates")?.requiredPermissions).toEqual([
      "menu.pathway-templates",
      "pathway.read",
    ]);
    expect(findRouteByPath("/rule/definitions")?.requiredPermissions).toEqual([
      "menu.rule-definitions",
      "rule.read",
    ]);
  });

  it("keeps unified authoring assets as a governed knowledge route without adding a second-level menu", () => {
    const route = findRouteByPath("/authoring/assets");

    expect(route?.sectionKey).toBe("knowledge-configuration");
    expect(route?.placement).toBe("hidden");
    expect(route?.hidden).toBe(true);
    expect(route?.menuKey).toBeUndefined();
    expect(route?.menuLabel).toBeUndefined();
    expect(route?.requiredPermissions).toEqual(["rule.read", "pathway.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [{ code: "rule.read" }, { code: "pathway.read" }],
        menuKeys: ["config-packages"],
      }),
    ).toBe(true);
  });

  it("opens terminology mapping to authorized readers and leaves actions to page permissions", () => {
    const route = findRouteByPath("/terminology/mapping");

    expect(route?.requiredPermissions).toEqual(["menu.terminology-mapping", "term.read"]);
    expect(route?.requiredRoles).toEqual([
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "diagnostic-service-user",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [{ code: "term.read" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "knowledge-governor" }],
        permissions: [{ code: "term.read" }, { code: "term.write" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "diagnostic-service-user" }],
        permissions: [{ code: "term.read" }, { code: "term.write" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-governance-admin" }],
        permissions: [{ code: "term.read" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-decision-user" }],
        permissions: [{ code: "term.read" }, { code: "term.write" }, { code: "term.publish" }],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [],
        menuKeys: ["terminology-mapping"],
      }),
    ).toBe(false);
  });

  it("limits adapter hub to integration operators with read/write/execute permissions", () => {
    const route = findRouteByPath("/adapter/hub");
    const protocolFilter = route?.experience?.defaultFilters.find(
      (filter) => filter.key === "protocolType",
    );

    expect(route?.requiredPermissions).toEqual([
      "menu.adapter-hub",
      "integration.read",
      "integration.write",
      "integration.execute",
    ]);
    expect(route?.requiredRoles).toEqual(["integration-operator", "implementation-operator"]);
    expect(protocolFilter?.label).toBe("接入协议");
    expect(protocolFilter?.options?.map((option) => option.value)).toEqual([
      "HL7",
      "FHIR",
      "Webhook",
      "REST",
      "WebService",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "implementation-operator" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "organization-admin" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-decision-user" }],
        permissions: [
          { code: "integration.read" },
          { code: "integration.write" },
          { code: "integration.execute" },
        ],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "integration-operator" }],
        permissions: [{ code: "integration.read" }],
        menuKeys: ["adapter-hub"],
      }),
    ).toBe(false);
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
      .filter(
        (route) =>
          route.requireAuth && route.sectionKey && route.menuKey && route.placement !== "hidden",
      )
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
        roles: [{ code: "quality-governor" }],
        permissions: [{ code: "evaluation.read" }],
        menuKeys: ["qc-alerts"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/advanced/provenance"), {
        roles: [{ code: "compliance-auditor" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["provenance"],
      }),
    ).toBe(true);
  });

  it("requires both the provenance menu and knowledge read permission", () => {
    const route = findRouteByPath("/advanced/provenance");

    expect(route?.requiredPermissions).toEqual(["menu.provenance", "knowledge.read"]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "knowledge-governor" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["provenance"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "implementation-operator" }],
        permissions: [],
        menuKeys: ["provenance"],
      }),
    ).toBe(false);
  });

  it("allows the platform administrator to enter knowledge governance for platform retirement", () => {
    const route = findRouteByPath("/knowledge/governance");

    expect(
      canAccessRoute(route, {
        roles: [{ code: "platform-governance-admin" }],
        permissions: [{ code: "knowledge.read" }],
        menuKeys: ["knowledge-governance"],
      }),
    ).toBe(true);
  });

  it("allows dedicated knowledge and access responsibilities into their owned pages", () => {
    const knowledgeRoute = findRouteByPath("/knowledge/governance");
    const usersRoute = findRouteByPath("/admin/users");
    const identityRoute = findRouteByPath("/security/identity-binding");

    for (const roleCode of ["platform-knowledge-governor", "knowledge-governor"]) {
      expect(
        canAccessRoute(knowledgeRoute, {
          roles: [{ code: roleCode }],
          permissions: [{ code: "knowledge.read" }],
          menuKeys: ["knowledge-governance"],
        }),
      ).toBe(true);
    }
    expect(
      canAccessRoute(usersRoute, {
        roles: [{ code: "identity-access-admin" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["admin-users"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(usersRoute, {
        roles: [{ code: "implementation-operator" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["admin-users"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(identityRoute, {
        roles: [{ code: "identity-access-admin" }],
        permissions: [{ code: "org.read" }],
        menuKeys: ["identity-bindings"],
      }),
    ).toBe(true);
  });

  it("limits graph exploration to projection readers in advanced technical roles", () => {
    const route = findRouteByPath("/advanced/graph");

    expect(route?.placement).toBe("expert");
    expect(route?.requiredPermissions).toEqual(["menu.graph-explore", "projection.read"]);
    expect(route?.requiredRoles).toEqual([
      "implementation-operator",
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "platform-governance-admin",
      "organization-admin",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "knowledge-governor" }],
        permissions: [{ code: "projection.read" }],
        menuKeys: ["graph-explore"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-decision-user" }],
        permissions: [{ code: "projection.read" }],
        menuKeys: ["graph-explore"],
      }),
    ).toBe(false);
  });

  it("limits AI workflow status to real governance roles with read permission", () => {
    const route = findRouteByPath("/advanced/ai-workflows");

    expect(route?.placement).toBe("expert");
    expect(route?.requiredPermissions).toEqual(["menu.ai-workflows", "llm.read"]);
    expect(route?.requiredRoles).toEqual([
      "implementation-operator",
      "integration-operator",
      "platform-knowledge-governor",
      "knowledge-governor",
      "clinical-governor",
      "platform-governance-admin",
      "organization-admin",
    ]);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-governor" }],
        permissions: [{ code: "llm.read" }],
        menuKeys: ["ai-workflows"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(route, {
        roles: [{ code: "clinical-decision-user" }],
        permissions: [{ code: "llm.read" }],
        menuKeys: ["ai-workflows"],
      }),
    ).toBe(false);
  });

  it("requires the WORKBENCH-02 readiness action permission in addition to the workbench menu", () => {
    expect(
      canAccessRoute(findRouteByPath("/workbench/readiness-validation"), {
        roles: [{ code: "implementation-operator" }],
        permissions: [{ code: "workbench:readiness:view" }],
        menuKeys: ["workbench"],
      }),
    ).toBe(true);
    expect(
      canAccessRoute(findRouteByPath("/workbench/readiness-validation"), {
        roles: [{ code: "clinical-decision-user" }],
        permissions: [{ code: "workbench:readiness:view" }],
        menuKeys: ["workbench"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(findRouteByPath("/workbench/readiness-validation"), {
        roles: [{ code: "implementation-operator" }],
        permissions: [],
        menuKeys: ["workbench"],
      }),
    ).toBe(false);
  });

  it("rejects legacy first-level section menuKeys for second-level routes", () => {
    expect(
      canAccessRoute(findRouteByPath("/terminology/mapping"), {
        roles: [{ code: "implementation-operator" }],
        permissions: [],
        menuKeys: ["pilot-setup"],
      }),
    ).toBe(false);
    expect(
      canAccessRoute(findRouteByPath("/qc/eval/results"), {
        roles: [{ code: "quality-governor" }],
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

  it("classifies the unified security baseline as system management instead of a readonly dashboard", () => {
    const route = findRouteByPath("/security/baseline");

    expect(route).toEqual(
      expect.objectContaining({
        title: "安全与配置",
        pageType: "system",
        requiresStepFlow: false,
        requiredPermissions: ["menu.security-baseline", "system.read"],
      }),
    );
    expect(route?.experience?.goal).toContain("管理");
    expect(route?.experience?.riskLevel).toBe("high");
  });

  it("treats identity binding as a tenant-scoped managed security workflow", () => {
    const route = findRouteByPath("/security/identity-binding");

    expect(route).toEqual(
      expect.objectContaining({
        title: "身份来源",
        pageType: "system",
        stateMachine: "change",
        requiredPermissions: ["menu.identity-bindings", "org.read"],
        requiredRoles: [
          "identity-access-admin",
          "integration-operator",
          "platform-governance-admin",
          "organization-admin",
        ],
      }),
    );
    expect(route?.experience?.goal).toContain("管理");
    expect(route?.experience?.riskLevel).toBe("high");
  });

  it("returns customer routes without hidden or expert tools", () => {
    expect(customerRouteMetas.some((route) => route.placement === "hidden")).toBe(false);
    expect(customerRouteMetas.some((route) => route.placement === "expert")).toBe(false);
    expect(customerRouteMetas.map((route) => route.path)).toContain("/dashboard");
    expect(customerRouteMetas.map((route) => route.path)).toContain("/notifications");
    expect(customerRouteMetas.map((route) => route.path)).not.toContain("/advanced/dev-console");
  });

  it("builds breadcrumbs from route metadata", () => {
    expect(getRouteBreadcrumb("/qc/dashboard")).toEqual(["质量与运营", "质量与运营概览"]);
    expect(getRouteBreadcrumb("/missing")).toEqual(["未找到页面"]);
  });
});
