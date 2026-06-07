type ApiProxyEnv = Record<string, string | undefined>;

export function resolveApiProxyTarget(env: ApiProxyEnv): string {
  const configured = env.MEDKERNEL_API_PROXY_TARGET?.trim() || env.VITE_API_PROXY_TARGET?.trim();
  if (!configured) {
    throw new Error(
      "缺少 MEDKERNEL_API_PROXY_TARGET 或 VITE_API_PROXY_TARGET，不能启动未指向真实后端的前端代理。",
    );
  }
  return configured;
}
