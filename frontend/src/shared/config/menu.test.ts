import { describe, it, expect } from "vitest";
import { menuSections } from "./menu";
import { routeMetas } from "./routes";

describe("menu config", () => {
  it("matches the five-domain CONSTITUTION ordering", () => {
    expect(menuSections).toHaveLength(5);
    expect(menuSections.map((section) => [section.key, section.label])).toEqual([
      ["workbench", "工作台"],
      ["institution-governance", "机构治理"],
      ["knowledge-configuration", "知识配置"],
      ["clinical-collaboration", "临床协同"],
      ["quality-operations", "质量与运营"],
    ]);
  });

  it("does not create a standalone advanced section", () => {
    expect(menuSections.map((section) => section.key)).not.toContain("advanced-tools");
    expect(menuSections.map((section) => section.label)).not.toContain("高级工具");
  });

  it("all items have a valid path starting with /", () => {
    menuSections.forEach((s) => {
      s.items.forEach((it) => {
        expect(it.path).toMatch(/^\//);
      });
    });
  });

  it("derives every menu item from primary route metadata", () => {
    const routePaths = new Set(
      routeMetas.filter((route) => route.placement === "primary").map((route) => route.path),
    );

    menuSections.forEach((section) => {
      section.items.forEach((item) => {
        expect(routePaths.has(item.path)).toBe(true);
      });
    });
  });

  it("keeps merged, header, profile and expert capabilities out of the sidebar", () => {
    const sidebarKeys = menuSections.flatMap((section) => section.items.map((item) => item.key));

    expect(sidebarKeys).not.toEqual(
      expect.arrayContaining([
        "rule-validate",
        "qc-eval-results",
        "notifications",
        "notification-settings",
        "provenance",
        "graph-explore",
        "ai-workflows",
        "domestic-check",
        "dev-console",
      ]),
    );
  });

  it("locks the exact 23 customer primary entries", () => {
    const visibleTotal = menuSections.reduce((sum, section) => sum + section.items.length, 0);

    expect(visibleTotal).toBe(23);
    expect(
      menuSections.map((section) => [section.key, section.items.map((item) => item.key)]),
    ).toEqual([
      ["workbench", ["workbench"]],
      [
        "institution-governance",
        [
          "tenant-onboarding",
          "admin-users",
          "identity-bindings",
          "implementation-guide",
          "adapter-hub",
        ],
      ],
      [
        "knowledge-configuration",
        [
          "knowledge-governance",
          "config-packages",
          "terminology-mapping",
          "rule-definitions",
          "pathway-templates",
        ],
      ],
      [
        "clinical-collaboration",
        ["mpi", "patient-pathways", "cdss-fatigue", "workflow-todos", "clinical-followup"],
      ],
      [
        "quality-operations",
        [
          "qc-dashboard",
          "qc-alerts",
          "insurance-audit",
          "qc-eval-sets",
          "admin-audit",
          "security-baseline",
          "system-providers",
        ],
      ],
    ]);
  });
});
