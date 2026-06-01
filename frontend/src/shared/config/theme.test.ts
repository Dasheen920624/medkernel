import { describe, expect, it } from "vitest";

import { THEME_MODE_OPTIONS, createThemeConfig, type ThemeMode } from "./theme";

describe("MedKernel 主题 token 配置", () => {
  it("提供固定的 5 种主题模式", () => {
    expect(THEME_MODE_OPTIONS.map((option) => option.mode)).toEqual([
      "default",
      "elder",
      "dark",
      "eye",
      "system",
    ]);
  });

  it.each<ThemeMode>(["default", "elder", "dark", "eye", "system"])(
    "%s 模式启用 Ant Design CSS 变量",
    (mode) => {
      expect(createThemeConfig(mode, false).cssVar).toBe(true);
    },
  );

  it("老年医生模式正文不低于 16pt", () => {
    const config = createThemeConfig("elder", false);

    expect(Number(config.token?.fontSize)).toBeGreaterThanOrEqual(22);
    expect(Number(config.token?.controlHeight)).toBeGreaterThanOrEqual(44);
    expect(Number(config.token?.controlHeightLG)).toBeGreaterThanOrEqual(52);
  });

  it("system 模式按系统深色偏好选择算法", () => {
    expect(createThemeConfig("system", false).algorithm).toBeUndefined();
    expect(createThemeConfig("system", true).algorithm).toBeDefined();
  });
});
