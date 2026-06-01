import { create } from "zustand";
import { isThemeMode, type ThemeMode } from "@/shared/config/theme";
import { readUiPreference, writeUiPreference } from "./browserStorage";

/**
 * 全局主题模式 store（Zustand 5）。
 * App.tsx 读这里的 mode 决定 ConfigProvider theme。
 */
interface ThemeState {
  mode: ThemeMode;
  setMode: (m: ThemeMode) => void;
}

const STORAGE_KEY = "medkernel.theme.mode";

function readInitial(): ThemeMode {
  if (typeof window === "undefined") return "default";
  const saved = readUiPreference(STORAGE_KEY);
  if (isThemeMode(saved)) {
    return saved;
  }
  return "default";
}

export const useThemeStore = create<ThemeState>((set) => ({
  mode: readInitial(),
  setMode: (mode) => {
    writeUiPreference(STORAGE_KEY, mode);
    set({ mode });
  },
}));
