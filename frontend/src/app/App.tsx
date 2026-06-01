import { useMemo } from "react";
import { App as AntdApp, ConfigProvider, theme as antdTheme } from "antd";
import zhCN from "antd/locale/zh_CN";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AppRouter } from "./router";
import { useThemeStore } from "@/shared/lib/themeStore";
import { eyeModeToken, theme as medkernelTheme } from "@/shared/config/theme";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

export default function App() {
  const mode = useThemeStore((s) => s.mode);

  const themeConfig = useMemo(() => {
    const base = {
      token: medkernelTheme.token,
      cssVar: true,
    };

    if (mode === "elder") {
      return {
        ...base,
        token: { ...base.token, fontSize: 16, controlHeight: 40, borderRadius: 8 },
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
    if (mode === "system") {
      const prefersDark =
        typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
      return prefersDark ? { ...base, algorithm: antdTheme.darkAlgorithm } : base;
    }
    return base;
  }, [mode]);

  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <AppRouter />
          </BrowserRouter>
        </QueryClientProvider>
      </AntdApp>
    </ConfigProvider>
  );
}
