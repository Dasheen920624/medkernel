import { describe, it, expect } from "vitest";
import { menuSections } from "./menu";
import { routeMetas } from "./routes";

describe("menu config", () => {
  it("has exactly 6 sections (5 visible + 1 hidden advanced)", () => {
    expect(menuSections).toHaveLength(6);
    const visible = menuSections.filter((s) => !s.hidden);
    expect(visible).toHaveLength(5);
  });

  it("matches CONSTITUTION §2.1 ordering", () => {
    expect(menuSections.map((section) => section.key)).toEqual([
      "workbench",
      "pilot-setup",
      "clinical-run",
      "quality-improve",
      "compliance-ops",
      "advanced-tools",
    ]);
  });

  it("advanced tools section is hidden", () => {
    const advanced = menuSections.find((s) => s.key === "advanced-tools");
    expect(advanced?.hidden).toBe(true);
  });

  it("all items have a valid path starting with /", () => {
    menuSections.forEach((s) => {
      s.items.forEach((it) => {
        expect(it.path).toMatch(/^\//);
      });
    });
  });

  it("derives every menu item from route metadata", () => {
    const routePaths = new Set(routeMetas.map((route) => route.path));

    menuSections.forEach((section) => {
      section.items.forEach((item) => {
        expect(routePaths.has(item.path)).toBe(true);
      });
    });
  });

  it("keeps the demo-validation page inside the workbench tab instead of adding a second menu item", () => {
    const workbench = menuSections.find((section) => section.key === "workbench");

    expect(routeMetas.map((route) => route.path)).toContain("/workbench/demo-validation");
    expect(workbench?.items).toEqual([{ key: "workbench", label: "工作台", path: "/dashboard" }]);
  });

  it("locks the exact 27 customer-facing items + 5 advanced tools from CONSTITUTION §2.2", () => {
    const visible = menuSections.filter((s) => !s.hidden);
    const visibleTotal = visible.reduce((sum, s) => sum + s.items.length, 0);
    const advanced = menuSections.find((s) => s.key === "advanced-tools");

    expect(visibleTotal).toBe(27);
    expect(advanced?.items).toHaveLength(5);
    expect(visible.map((s) => [s.key, s.items.map((item) => item.key)])).toEqual([
      ["workbench", ["workbench"]],
      [
        "pilot-setup",
        [
          "implementation-guide",
          "tenant-onboarding",
          "config-packages",
          "pathway-templates",
          "rule-definitions",
          "terminology-mapping",
          "adapter-hub",
        ],
      ],
      [
        "clinical-run",
        [
          "mpi",
          "patient-pathways",
          "cdss-fatigue",
          "rule-validate",
          "workflow-todos",
          "notifications",
          "clinical-followup",
        ],
      ],
      [
        "quality-improve",
        [
          "qc-dashboard",
          "qc-alerts",
          "insurance-audit",
          "qc-eval-sets",
          "qc-eval-results",
          "aik-review",
        ],
      ],
      [
        "compliance-ops",
        [
          "admin-users",
          "identity-bindings",
          "admin-audit",
          "security-baseline",
          "system-providers",
          "notification-settings",
        ],
      ],
    ]);
    expect(advanced?.items.map((item) => item.key)).toEqual([
      "provenance",
      "graph-explore",
      "ai-workflows",
      "domestic-check",
      "dev-console",
    ]);
  });
});
