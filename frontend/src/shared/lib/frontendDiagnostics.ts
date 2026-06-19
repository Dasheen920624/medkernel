export const FRONTEND_DIAGNOSTIC_EVENT = "medkernel:frontend-diagnostic";

export interface FrontendDiagnostic {
  category: "RENDER_FAILURE";
  message: string;
  componentStack: string | null;
  occurredAt: string;
}

/**
 * 统一发布前端诊断事件，并在浏览器支持时交给全局错误报告器。
 */
export function reportRenderFailure(error: Error, componentStack?: string | null) {
  const diagnostic: FrontendDiagnostic = {
    category: "RENDER_FAILURE",
    message: error.message,
    componentStack: componentStack ?? null,
    occurredAt: new Date().toISOString(),
  };
  window.dispatchEvent(
    new CustomEvent<FrontendDiagnostic>(FRONTEND_DIAGNOSTIC_EVENT, {
      detail: diagnostic,
    }),
  );

  const reportError = (
    globalThis as typeof globalThis & {
      reportError?: (reportedError: unknown) => void;
    }
  ).reportError;
  reportError?.(error);
}
