import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { resolveVitestTimeout } from "./vitestRuntimeBudget";

const frontendRoot = process.cwd();

describe("Vitest 运行时预算", () => {
  it("CI 覆盖率任务使用有界的较宽超时，本地仍保持快速失败", () => {
    expect(resolveVitestTimeout({ CI: "true" })).toBe(15_000);
    expect(resolveVitestTimeout({ CI: "false" })).toBe(5_000);
    expect(resolveVitestTimeout({})).toBe(5_000);
  });

  it("全量 verify 门禁使用 CI 预算运行完整 Vitest 套件", () => {
    const packageJson = JSON.parse(readFileSync(resolve(frontendRoot, "package.json"), "utf8")) as {
      scripts: Record<string, string>;
    };

    expect(packageJson.scripts.verify).toContain("CI=true npm test");
  });
});
