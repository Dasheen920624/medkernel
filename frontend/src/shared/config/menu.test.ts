import { describe, it, expect } from "vitest";
import { menuSections } from "./menu";
import { routeMetas } from "./routes";

describe("menu config", () => {
  it("matches the split clinical runtime and knowledge production information architecture", () => {
    expect(menuSections).toHaveLength(8);
    expect(menuSections.map((section) => [section.key, section.label])).toEqual([
      ["workbench", "工作台"],
      ["organization-people", "机构与人员"],
      ["knowledge-governance", "知识治理"],
      ["knowledge-production", "知识生产"],
      ["clinical-collaboration", "临床协同"],
      ["quality-management", "质量管理"],
      ["compliance-security", "合规安全"],
      ["system-operations", "系统运维"],
    ]);
  });

  it("does not create a standalone advanced section", () => {
    const retiredStandaloneToolLabel = "高级" + "工具";

    expect(menuSections.map((section) => section.key)).not.toContain("advanced-tools");
    expect(menuSections.map((section) => section.label)).not.toContain(retiredStandaloneToolLabel);
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

  it("keeps merged, header and profile capabilities out of the sidebar", () => {
    const sidebarKeys = menuSections.flatMap((section) => section.items.map((item) => item.key));

    expect(sidebarKeys).not.toEqual(
      expect.arrayContaining([
        "rule-validate",
        "qc-eval-results",
        "notifications",
        "notification-settings",
      ]),
    );
    expect(sidebarKeys).toEqual(
      expect.arrayContaining([
        "provenance",
        "graph-explore",
        "knowledge-production",
        "ai-workflows",
        "domestic-check",
        "runtime-diagnostics",
      ]),
    );
  });

  it("locks the exact 32 customer and production primary entries", () => {
    const visibleTotal = menuSections.reduce((sum, section) => sum + section.items.length, 0);

    expect(visibleTotal).toBe(32);
    expect(
      menuSections.map((section) => [section.key, section.items.map((item) => item.key)]),
    ).toEqual([
      ["workbench", ["workbench"]],
      ["organization-people", ["tenant-onboarding", "admin-users", "identity-bindings"]],
      [
        "knowledge-governance",
        [
          "knowledge-governance",
          "institution-knowledge",
          "diagnosis-knowledge",
          "runtime-releases",
          "terminology-mapping",
          "rule-definitions",
          "pathway-templates",
          "provenance",
          "graph-explore",
        ],
      ],
      ["knowledge-production", ["knowledge-production", "ai-workflows"]],
      [
        "clinical-collaboration",
        [
          "mpi",
          "patient-pathways",
          "cdss-fatigue",
          "workflow-todos",
          "clinical-followup",
          "sandbox",
        ],
      ],
      ["quality-management", ["qc-dashboard", "qc-alerts", "insurance-audit", "qc-eval-sets"]],
      ["compliance-security", ["admin-audit", "security-baseline"]],
      [
        "system-operations",
        [
          "implementation-guide",
          "adapter-hub",
          "system-providers",
          "domestic-check",
          "runtime-diagnostics",
        ],
      ],
    ]);
  });
});
