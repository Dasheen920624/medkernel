import { describe, it, expect } from "vitest";
import { productEntryCatalog } from "@/shared/contracts/productEntryCatalog.generated";
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

  it("matches every primary entry from the product entry contract", () => {
    const visibleTotal = menuSections.reduce((sum, section) => sum + section.items.length, 0);
    const primaryEntries = productEntryCatalog.filter((entry) => entry.placement === "primary");
    const expectedBySection = new Map<string, string[]>();
    primaryEntries.forEach((entry) => {
      expectedBySection.set(entry.sectionCode, [
        ...(expectedBySection.get(entry.sectionCode) ?? []),
        entry.entryCode,
      ]);
    });

    expect(visibleTotal).toBe(primaryEntries.length);
    expect(
      menuSections.map((section) => [section.key, section.items.map((item) => item.key)]),
    ).toEqual(
      menuSections.map((section) => [section.key, expectedBySection.get(section.key) ?? []]),
    );
  });
});
