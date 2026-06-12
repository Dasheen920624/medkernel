import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { loadEnv } from "vite";
import { resolveApiProxyConfig } from "./src/shared/config/devProxy";

function vendorChunkName(id: string) {
  if (!id.includes("/node_modules/")) {
    return undefined;
  }
  if (id.includes("/react/") || id.includes("/react-dom/") || id.includes("/react-router")) {
    return "vendor-react";
  }
  if (id.includes("/@tanstack/react-query/") || id.includes("/axios/")) {
    return "vendor-data";
  }
  return undefined;
}

/**
 * MedKernel v1.0 GA · Vite 配置
 * 开发期 proxy /medkernel → MEDKERNEL_API_PROXY_TARGET / VITE_API_PROXY_TARGET。
 * 前端骨架按 FSD 分层（app/pages/widgets/features/entities/shared），alias 仅暴露 @ 根。
 */
export default defineConfig(({ command, mode }) => {
  const env = { ...process.env, ...loadEnv(mode, process.cwd(), "") };
  const apiProxyConfig =
    command === "serve" && mode !== "test" ? resolveApiProxyConfig(env) : undefined;

  return {
    plugins: [react()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
      },
    },
    server: {
      port: 5173,
      host: "0.0.0.0",
      proxy: apiProxyConfig
        ? {
            "/medkernel": {
              target: apiProxyConfig.target,
              changeOrigin: false,
              secure: apiProxyConfig.secure,
            },
          }
        : undefined,
    },
    build: {
      outDir: "dist",
      sourcemap: true,
      target: "es2022",
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          manualChunks: vendorChunkName,
        },
      },
    },
    test: {
      globals: true,
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      include: ["src/**/*.{test,spec}.{ts,tsx}"],
      exclude: ["e2e/**", "node_modules/**", "dist/**"],
      css: false,
    },
  };
});
