import { useEffect, useMemo, useRef } from "react";
import { Empty, Segmented, Typography } from "antd";

import styles from "./Sandbox.module.css";

export interface SandboxEmbedDecision {
  action: string;
  cardId?: string;
  reason?: string;
  recommendationStatus?: string;
  traceId?: string;
}

export type SandboxEmbedMode = "IFRAME" | "SDK" | "API";

interface SandboxEmbedFrameProps {
  embedUrl?: string | null;
  embedToken?: string | null;
  mode: SandboxEmbedMode;
  onModeChange: (mode: SandboxEmbedMode) => void;
  onDecision: (decision: SandboxEmbedDecision) => void;
}

export default function SandboxEmbedFrame({
  embedUrl,
  embedToken,
  mode,
  onModeChange,
  onDecision,
}: SandboxEmbedFrameProps) {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const embedOrigin = useMemo(
    () => (embedUrl ? new URL(embedUrl, window.location.origin).origin : null),
    [embedUrl],
  );

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (!embedOrigin || event.origin !== embedOrigin) return;
      if (event.source !== frameRef.current?.contentWindow) return;
      if (event.data?.source !== "MEDKERNEL_CDSS_EMBED") return;
      onDecision(event.data as SandboxEmbedDecision);
    };
    window.addEventListener("message", handleMessage);
    return () => window.removeEventListener("message", handleMessage);
  }, [embedOrigin, onDecision]);

  const maskedToken = embedToken
    ? `${embedToken.slice(0, Math.min(embedToken.length, 8))}...`
    : "<launch-token>";
  let embedContent;
  if (mode === "IFRAME" && embedUrl) {
    embedContent = (
      <iframe
        ref={frameRef}
        src={embedUrl}
        title="临床嵌入式终端"
        className={styles.embedFrame}
        sandbox="allow-forms allow-same-origin allow-scripts"
      />
    );
  } else if (mode === "IFRAME") {
    embedContent = (
      <div className={styles.embedEmpty}>
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待场景运行结果" />
      </div>
    );
  } else if (mode === "SDK") {
    embedContent = (
      <div className={styles.contractView} aria-label="SDK 接入契约">
        <Typography.Text strong>SDK 接入</Typography.Text>
        <pre className={styles.contractCode}>
          <code>{`MedKernelCDSS.launch({
  token: "${maskedToken}",
  endpoint: "/api/v1/engine/embed/launch"
});`}</code>
        </pre>
      </div>
    );
  } else {
    embedContent = (
      <div className={styles.contractView} aria-label="API 接入契约">
        <Typography.Text strong>API 接入</Typography.Text>
        <pre className={styles.contractCode}>
          <code>{`POST /api/v1/engine/embed/launch
Authorization: Bearer ${maskedToken}
Content-Type: application/json`}</code>
        </pre>
      </div>
    );
  }

  return (
    <section className={styles.panel} aria-labelledby="sandbox-embed-title">
      <div className={styles.panelHeader}>
        <Typography.Title id="sandbox-embed-title" level={5}>
          临床嵌入终端
        </Typography.Title>
        <Segmented<SandboxEmbedMode>
          aria-label="嵌入方式"
          value={mode}
          options={["IFRAME", "SDK", "API"]}
          onChange={onModeChange}
        />
      </div>
      {embedContent}
    </section>
  );
}
