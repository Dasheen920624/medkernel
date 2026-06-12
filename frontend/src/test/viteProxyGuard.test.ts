import { describe, expect, it } from "vitest";

import { resolveApiProxyConfig, resolveApiProxyTarget } from "@/shared/config/devProxy";

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

  it("默认校验 HTTPS 证书，只有显式本地开关才允许自签证书代理", () => {
    expect(
      resolveApiProxyConfig({
        MEDKERNEL_API_PROXY_TARGET: "https://193.112.107.134",
      }),
    ).toEqual({
      target: "https://193.112.107.134",
      secure: true,
    });

    expect(
      resolveApiProxyConfig({
        MEDKERNEL_API_PROXY_TARGET: "https://193.112.107.134",
        MEDKERNEL_API_PROXY_ALLOW_SELF_SIGNED: "true",
      }),
    ).toEqual({
      target: "https://193.112.107.134",
      secure: false,
    });
  });
});
