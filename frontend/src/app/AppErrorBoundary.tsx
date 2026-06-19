import { Button, Result } from "antd";
import { Component, type ErrorInfo, type ReactNode } from "react";
import { reportRenderFailure } from "@/shared/lib/frontendDiagnostics";

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  failed: boolean;
}

/**
 * 应用级渲染兜底：保留登录态并给出可恢复入口，禁止未处理异常把客户页面变成白屏。
 */
export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  override state: AppErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true };
  }

  override componentDidCatch(error: Error, info: ErrorInfo) {
    reportRenderFailure(error, info.componentStack);
  }

  override render() {
    if (this.state.failed) {
      return (
        <Result
          status="500"
          title="页面运行异常"
          subTitle="系统已保留当前登录状态，请重新加载页面。"
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              重新加载
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}
