import { fireEvent, render, screen } from "@testing-library/react";
import { ConfigProvider } from "antd";
import { describe, expect, it, vi } from "vitest";

import SandboxEmbedFrame from "./SandboxEmbedFrame";

describe("SandboxEmbedFrame", () => {
  it("loads the returned embed url and accepts decisions only from that frame and origin", () => {
    const onDecision = vi.fn();
    render(
      <ConfigProvider>
        <SandboxEmbedFrame
          embedUrl="/embed/launch?token=token-1"
          embedToken="token-1"
          mode="IFRAME"
          onModeChange={vi.fn()}
          onDecision={onDecision}
        />
      </ConfigProvider>,
    );

    const frame = screen.getByTitle("临床嵌入式终端") as HTMLIFrameElement;
    expect(frame).toHaveAttribute("src", "/embed/launch?token=token-1");

    fireEvent(
      window,
      new MessageEvent("message", {
        origin: window.location.origin,
        source: frame.contentWindow,
        data: {
          source: "MEDKERNEL_CDSS_EMBED",
          action: "ADOPT",
          cardId: "card-1",
        },
      }),
    );
    expect(onDecision).toHaveBeenCalledWith(
      expect.objectContaining({ action: "ADOPT", cardId: "card-1" }),
    );

    fireEvent(
      window,
      new MessageEvent("message", {
        origin: "https://untrusted.example",
        source: frame.contentWindow,
        data: { source: "MEDKERNEL_CDSS_EMBED", action: "REJECT" },
      }),
    );
    expect(onDecision).toHaveBeenCalledTimes(1);
  });

  it("switches between iframe, script and interface integration contract views", () => {
    const onModeChange = vi.fn();
    const { rerender } = render(
      <ConfigProvider>
        <SandboxEmbedFrame
          embedUrl="/embed/launch?token=token-1"
          embedToken="token-1"
          mode="IFRAME"
          onModeChange={onModeChange}
          onDecision={vi.fn()}
        />
      </ConfigProvider>,
    );

    fireEvent.click(screen.getByText("脚本接入"));
    expect(onModeChange).toHaveBeenCalledWith("SDK");

    rerender(
      <ConfigProvider>
        <SandboxEmbedFrame
          embedUrl="/embed/launch?token=token-1"
          embedToken="token-1"
          mode="SDK"
          onModeChange={onModeChange}
          onDecision={vi.fn()}
        />
      </ConfigProvider>,
    );
    expect(screen.getByText(/访问凭证：token-1/)).toBeInTheDocument();
    expect(
      screen.getByText((text) => text.includes("接入地址：/embed/launch?token=token-1")),
    ).toBeInTheDocument();

    rerender(
      <ConfigProvider>
        <SandboxEmbedFrame
          embedUrl="/embed/launch?token=token-1"
          embedToken="token-1"
          mode="API"
          onModeChange={onModeChange}
          onDecision={vi.fn()}
        />
      </ConfigProvider>,
    );
    expect(screen.getByText(/由信息科系统发起受控嵌入访问/)).toBeInTheDocument();
  });
});
