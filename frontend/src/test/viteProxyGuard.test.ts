import { describe, expect, it } from "vitest";

import { resolveApiProxyTarget } from "@/shared/config/devProxy";

describe("Vite API 代理配置", () => {
  it("允许用环境变量指向当前后端实例，避免误打旧 18080 服务", () => {
    expect(resolveApiProxyTarget({ MEDKERNEL_API_PROXY_TARGET: "http://localhost:18081" })).toBe(
      "http://localhost:18081",
    );
  });

  it("未配置时直接失败，避免误连旧后端", () => {
    expect(() => resolveApiProxyTarget({})).toThrow(
      "缺少 MEDKERNEL_API_PROXY_TARGET 或 VITE_API_PROXY_TARGET",
    );
  });
});
