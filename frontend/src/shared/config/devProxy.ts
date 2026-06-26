type ApiProxyEnv = Record<string, string | undefined>;

export type ApiProxyConfig = {
  target: string;
  secure: boolean;
};

export function resolveApiProxyTarget(env: ApiProxyEnv): string {
  return resolveApiProxyConfig(env).target;
}

export function resolveApiProxyConfig(env: ApiProxyEnv): ApiProxyConfig {
  const configured = env.MEDKERNEL_API_PROXY_TARGET?.trim() || env.VITE_API_PROXY_TARGET?.trim();
  if (!configured) {
    throw new Error(
      "缺少 MEDKERNEL_API_PROXY_TARGET 或 VITE_API_PROXY_TARGET，不能启动未指向真实 API 服务的前端代理。",
    );
  }
  return {
    target: configured,
    secure: env.MEDKERNEL_API_PROXY_ALLOW_SELF_SIGNED?.trim() !== "true",
  };
}
