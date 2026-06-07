import axios from "axios";

/**
 * MedKernel v1.0 GA · axios HTTP client（全局单例）。
 *
 * baseURL 走 vite proxy → /medkernel/api；代理目标由开发环境显式配置。
 *
 * 登录态只放 httpOnly cookie；浏览器写操作通过 XSRF-TOKEN + X-XSRF-TOKEN 双提交防护。
 */
export const apiClient = axios.create({
  baseURL: "/medkernel/api/v1",
  timeout: 30_000,
  headers: { "Content-Type": "application/json" },
  withCredentials: true,
  xsrfCookieName: "XSRF-TOKEN",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

apiClient.interceptors.request.use((config) => {
  // 自动加 trace-id（与后端 OpenTelemetry 链路对齐）
  if (config.headers) {
    config.headers["X-Trace-Id"] = crypto.randomUUID();
  }
  return config;
});

apiClient.interceptors.response.use(
  (resp) => resp,
  (err) => {
    if (err.response?.status === 401) {
      window.dispatchEvent(new CustomEvent("medkernel:auth-required"));
    }
    return Promise.reject(err);
  },
);
