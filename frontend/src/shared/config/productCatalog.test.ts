import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { spawnSync } from "node:child_process";
import { describe, expect, it } from "vitest";
import { menuSections } from "./menu";
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

function extractBackendMenuEntries() {
  const javaSource = readFileSync(menuCatalogPath, "utf8");
  return Array.from(
    javaSource.matchAll(
      /menu\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*([A-Z0-9_]+),\s*MenuPlacement\.([A-Z]+)\)/g,
    ),
    (match) => ({
      sectionKey: match[1],
      menuKey: match[2],
      label: match[3],
      permission: match[4],
      placement: match[5].toLowerCase(),
    }),
  );
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

  it("keeps frontend routes and backend navigation catalog in one exact contract", () => {
    const backendEntries = extractBackendMenuEntries();
    const frontendEntries = routeMetas
      .filter((route) => route.requireAuth && route.menuKey && route.menuLabel)
      .map((route) => {
        const menuKey = route.menuKey as string;
        return {
          sectionKey: route.sectionKey,
          menuKey,
          label: route.menuLabel,
          permission: `MENU_${menuKey.replace(/-/g, "_").toUpperCase()}`,
          placement: route.placement,
        };
      });

    expect(backendEntries).toHaveLength(34);
    expect(frontendEntries).toHaveLength(34);
    expect(backendEntries).toEqual(expect.arrayContaining(frontendEntries));
    expect(
      backendEntries.filter((entry) => entry.placement === "primary").map((entry) => entry.menuKey),
    ).toEqual(menuSections.flatMap((section) => section.items.map((item) => item.key)));
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

  it("classifies the full-truth sandbox as an ordinary clinical collaboration capability", () => {
    const catalog = readCatalog();

    expect(catalog).toContain("<!-- capability:route:route@%2Fsandbox decision=KEEP -->");
    expect(catalog).toContain(
      "| `/sandbox` | 全真体验沙盘 | clinical-collaboration | sandbox | primary | KEEP | 临床协同 | 全真体验沙盘 |",
    );
    expect(catalog).toContain("<!-- capability:menu:menu@sandbox decision=KEEP -->");
  });

  it("keeps route customer tasks in hospital-facing language", () => {
    const catalog = readCatalog();
    const retiredReleaseTask = "维护" + "平台标准版本、机构生效版本、发布影响和回滚证据";
    const retiredTerminologyTask = "维护" + "院内术语映射、冲突和高风险确认";
    const retiredDiagnosisTask = "维护" + "诊断身份、诊断标准、鉴别诊断、验证病例与来源证据";
    const retiredPathwayTask = "维护" + "、审核、发布和回滚临床路径版本";
    const retiredEvaluationTask = "维护" + "评价指标、影响分析和发布状态";

    expect(catalog).not.toContain("知识生产 readiness");
    expect(catalog).not.toContain("生产 job");
    expect(catalog).toContain(
      "| `/config/releases` | 机构生效版本 | knowledge-governance | runtime-releases | primary | MERGE | 知识治理 | 机构生效版本 | 发布平台标准版本、生成机构生效版本并保留影响和回滚证据 |",
    );
    expect(catalog).toContain(
      "| `/terminology/mapping` | 术语字典 | knowledge-governance | terminology-mapping | primary | MOVE | 知识治理 | 术语字典 | 校准院内术语映射、裁定冲突并逐条确认高危近似 |",
    );
    expect(catalog).toContain(
      "| `/pathway/templates` | 临床路径库 | knowledge-governance | pathway-templates | primary | MOVE | 知识治理 | 临床路径库 | 编排、审核、发布和回滚临床路径版本 |",
    );
    expect(catalog).toContain(
      "| `/qc/eval/sets` | 评价指标 | quality-management | qc-eval-sets | primary | RENAME | 质量管理 | 评价指标 | 定义评价指标、试算影响分析并发布生效 |",
    );
    expect(catalog).toContain(
      "| `/knowledge/diagnosis` | 诊断知识库 | knowledge-governance | diagnosis-knowledge | primary | SPLIT | 知识治理 | 诊断知识库 | 管理诊断身份、诊断标准、鉴别诊断、验证病例与来源证据 |",
    );
    expect(catalog).not.toContain(retiredReleaseTask);
    expect(catalog).not.toContain(retiredTerminologyTask);
    expect(catalog).not.toContain(retiredDiagnosisTask);
    expect(catalog).not.toContain(retiredPathwayTask);
    expect(catalog).not.toContain(retiredEvaluationTask);
  });

  it("summarizes every primary sidebar domain in the inventory conclusion", () => {
    const catalog = readCatalog();
    const domainLine = catalog.match(/^- 目标客户业务域：(.+)。$/m);

    expect(domainLine?.[1].split("、")).toEqual(menuSections.map((section) => section.label));
  });
});
