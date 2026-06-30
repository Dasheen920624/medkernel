import { useMemo } from "react";
import { App as AntdApp, ConfigProvider } from "antd";
import zhCN from "antd/locale/zh_CN";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AppRouter } from "./router";
import { AppErrorBoundary } from "./AppErrorBoundary";
import { useThemeStore } from "@/shared/lib/themeStore";
import { createThemeConfig } from "@/shared/config/theme";
import { resolveBrowserBasename } from "./browserBasename";

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

export default function App() {
  const mode = useThemeStore((s) => s.mode);
  const browserBasename = resolveBrowserBasename();

  const themeConfig = useMemo(() => {
    const prefersDark =
      typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
    return createThemeConfig(mode, prefersDark);
  }, [mode]);

  return (
    <ConfigProvider locale={zhCN} theme={themeConfig}>
      <AntdApp>
        <AppErrorBoundary>
          <QueryClientProvider client={queryClient}>
            <BrowserRouter basename={browserBasename}>
              <AppRouter />
            </BrowserRouter>
          </QueryClientProvider>
        </AppErrorBoundary>
      </AntdApp>
    </ConfigProvider>
  );
}
