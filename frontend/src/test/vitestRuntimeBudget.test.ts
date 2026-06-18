import { describe, expect, it } from "vitest";

import { resolveVitestTimeout } from "./vitestRuntimeBudget";

describe("Vitest 运行时预算", () => {
  it("CI 覆盖率任务使用有界的较宽超时，本地仍保持快速失败", () => {
    expect(resolveVitestTimeout({ CI: "true" })).toBe(15_000);
    expect(resolveVitestTimeout({ CI: "false" })).toBe(5_000);
    expect(resolveVitestTimeout({})).toBe(5_000);
  });
});
