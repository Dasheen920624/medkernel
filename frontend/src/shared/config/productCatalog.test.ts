import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { describe, expect, it } from "vitest";
import { routeMetas } from "./routes";

const repositoryRoot = resolve(process.cwd(), "..");
const catalogPath = resolve(repositoryRoot, "docs/audit/product-function-catalog.md");
const menuCatalogPath = resolve(
  repositoryRoot,
  "medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java",
);
const exporterPath = resolve(repositoryRoot, "scripts/audit/export-product-capabilities.mjs");

const allowedDecisions = new Set([
  "KEEP",
  "RENAME",
  "MOVE",
  "MERGE",
  "SPLIT",
  "EXPERT",
  "API_ONLY",
  "REMOVE",
]);

function readCatalog(): string {
  return readFileSync(catalogPath, "utf8");
}

function extractMenuKeys(): string[] {
  const javaSource = readFileSync(menuCatalogPath, "utf8");
  return Array.from(javaSource.matchAll(/menu\("[^"]+",\s*"([^"]+)"/g), (match) => match[1]);
}

function extractCapabilityDecisions(catalog: string): Array<{ id: string; decision: string }> {
  return Array.from(
    catalog.matchAll(/<!-- capability:[^:]+:([^\s]+) decision=([A-Z_]+) -->/g),
    (match) => ({ id: match[1], decision: match[2] }),
  );
}

describe("product function catalog", () => {
  it("is reproducible from the current route, menu, page and controller inventory", () => {
    const result = spawnSync(process.execPath, [exporterPath, "--check"], {
      cwd: repositoryRoot,
      encoding: "utf8",
    });

    expect(result.status, `${result.stdout}\n${result.stderr}`).toBe(0);
  });

  it("registers every authenticated route and every backend menu key", () => {
    const catalog = readCatalog();

    routeMetas
      .filter((route) => route.requireAuth && route.path !== "*")
      .forEach((route) => {
        expect(catalog, `缺少认证路由 ${route.path}`).toContain(`<!-- route:${route.path} -->`);
      });

    extractMenuKeys().forEach((menuKey) => {
      expect(catalog, `缺少后端菜单 ${menuKey}`).toContain(`<!-- menu:${menuKey} -->`);
    });
  });

  it("gives every inventoried capability exactly one supported non-empty decision", () => {
    const decisions = extractCapabilityDecisions(readCatalog());
    const ids = decisions.map(({ id }) => id);

    expect(decisions.length).toBeGreaterThan(0);
    expect(new Set(ids).size).toBe(ids.length);
    decisions.forEach(({ id, decision }) => {
      expect(allowedDecisions.has(decision), `${id} 的裁决 ${decision} 不受支持`).toBe(true);
    });
  });
});
