import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const source = readFileSync(resolve(process.cwd(), "src/app/index.css"), "utf8");

describe("全局布局 CSS 契约", () => {
  it("限制所有抽屉不超过当前视口宽度", () => {
    expect(source).toMatch(/\.ant-drawer-content-wrapper\s*\{[^}]*max-width:\s*100vw\s*;/s);
  });
});
