import { theme as antdTheme } from "antd";
import type { ThemeConfig } from "antd";

/**
 * MedKernel 设计 token（与 docs/CONSTITUTION.md §8 对齐）。
 */
const baseToken: NonNullable<ThemeConfig["token"]> = {
  colorPrimary: "#1565c0",
  colorInfo: "#1565c0",
  colorSuccess: "#52c41a",
  colorWarning: "#faad14",
  colorError: "#ff4d4f",
  borderRadius: 6,
  fontSize: 14,
};

export type ThemeMode = "default" | "elder" | "dark" | "eye" | "system";

export const THEME_MODE_OPTIONS: Array<{
  mode: ThemeMode;
  label: string;
  description: string;
}> = [
  { mode: "default", label: "默认", description: "医蓝 14px 标准工作模式" },
  { mode: "elder", label: "老年医生", description: "≥16pt 正文、大按钮、高对比" },
  { mode: "dark", label: "暗黑", description: "夜间值守与低光环境" },
  { mode: "eye", label: "护眼", description: "柔和背景，降低长时间阅读疲劳" },
  { mode: "system", label: "跟随系统", description: "按操作系统深浅色偏好切换" },
];

export function isThemeMode(value: unknown): value is ThemeMode {
  return THEME_MODE_OPTIONS.some((option) => option.mode === value);
}

export const eyeModeToken: ThemeConfig["token"] = {
  colorBgLayout: "#f5f1e8",
  colorBgContainer: "#fdfaf2",
};

export const theme: ThemeConfig = {
  token: {
    ...baseToken,
  },
  cssVar: true,
};

export function createThemeConfig(mode: ThemeMode, systemPrefersDark: boolean): ThemeConfig {
  const base: ThemeConfig = {
    token: {
      ...baseToken,
    },
    cssVar: true,
  };

  if (mode === "elder") {
    return {
      ...base,
      token: {
        ...base.token,
        fontSize: 22,
        fontSizeSM: 20,
        fontSizeLG: 24,
        controlHeight: 44,
        controlHeightLG: 52,
        borderRadius: 8,
      },
    };
  }

  if (mode === "dark") {
    return { ...base, algorithm: antdTheme.darkAlgorithm };
  }

  if (mode === "eye") {
    return {
      ...base,
      token: {
        ...base.token,
        ...eyeModeToken,
      },
    };
  }

  if (mode === "system" && systemPrefersDark) {
    return { ...base, algorithm: antdTheme.darkAlgorithm };
  }

  return base;
}
