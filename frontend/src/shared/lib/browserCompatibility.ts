export type BrowserCompatibilityStatus = "PASS" | "WARN" | "FAIL";

export interface BrowserCapabilitySnapshot {
  esModules: boolean;
  fetch: boolean;
  abortController: boolean;
  url: boolean;
  textEncoder: boolean;
  webCrypto: boolean;
  matchMedia: boolean;
  resizeObserver: boolean;
  cssGrid: boolean;
  cssVariables: boolean;
}

export interface BrowserCompatibilityItem {
  key: keyof BrowserCapabilitySnapshot;
  displayName: string;
  required: boolean;
  supported: boolean;
  status: BrowserCompatibilityStatus;
  recommendation: string;
}

export interface BrowserCompatibilityReport {
  overallStatus: BrowserCompatibilityStatus;
  checkedAt: string;
  evidenceBasis: "CAPABILITY_PROBE";
  summary: string;
  disclaimer: string;
  items: BrowserCompatibilityItem[];
}

interface BrowserCapabilityDefinition {
  key: keyof BrowserCapabilitySnapshot;
  displayName: string;
  required: boolean;
  recommendation: string;
}

const CAPABILITY_DEFINITIONS: BrowserCapabilityDefinition[] = [
  {
    key: "esModules",
    displayName: "页面模块加载能力",
    required: true,
    recommendation: "升级到支持页面模块加载的现代 Chromium 内核浏览器。",
  },
  {
    key: "fetch",
    displayName: "网络请求能力",
    required: true,
    recommendation: "启用浏览器网络请求能力，或升级浏览器内核。",
  },
  {
    key: "abortController",
    displayName: "请求取消与超时控制",
    required: true,
    recommendation: "升级浏览器内核以支持请求取消和超时控制。",
  },
  {
    key: "url",
    displayName: "地址解析能力",
    required: true,
    recommendation: "升级浏览器内核以支持标准地址解析。",
  },
  {
    key: "textEncoder",
    displayName: "文本编码能力",
    required: true,
    recommendation: "升级浏览器内核以支持标准文本编码。",
  },
  {
    key: "webCrypto",
    displayName: "安全加密能力",
    required: true,
    recommendation: "确认浏览器处于安全上下文并启用安全加密能力。",
  },
  {
    key: "matchMedia",
    displayName: "系统主题识别能力",
    required: false,
    recommendation: "升级浏览器以完整支持系统主题与媒体查询联动。",
  },
  {
    key: "resizeObserver",
    displayName: "布局变化监听能力",
    required: false,
    recommendation: "升级浏览器以获得更稳定的响应式布局体验。",
  },
  {
    key: "cssGrid",
    displayName: "网格布局能力",
    required: true,
    recommendation: "升级浏览器内核以支持网格布局。",
  },
  {
    key: "cssVariables",
    displayName: "主题变量能力",
    required: true,
    recommendation: "升级浏览器内核以支持主题变量。",
  },
];

function capabilityStatus(supported: boolean, required: boolean): BrowserCompatibilityStatus {
  if (supported) {
    return "PASS";
  }
  return required ? "FAIL" : "WARN";
}

function overallCompatibilityStatus(
  failedCount: number,
  warningCount: number,
): BrowserCompatibilityStatus {
  if (failedCount > 0) {
    return "FAIL";
  }
  return warningCount > 0 ? "WARN" : "PASS";
}

function compatibilitySummary(
  status: BrowserCompatibilityStatus,
  failedCount: number,
  warningCount: number,
): string {
  if (status === "PASS") {
    return "关键与增强浏览器能力均可用。";
  }
  if (status === "FAIL") {
    return `有 ${failedCount} 项关键能力不可用，当前浏览器不满足上线使用要求。`;
  }
  return `关键能力可用，另有 ${warningCount} 项增强能力缺失。`;
}

export function detectBrowserCapabilities(): BrowserCapabilitySnapshot {
  const cssSupports = typeof CSS !== "undefined" && typeof CSS.supports === "function";

  return {
    esModules:
      typeof HTMLScriptElement !== "undefined" && "noModule" in HTMLScriptElement.prototype,
    fetch: typeof globalThis.fetch === "function",
    abortController: typeof globalThis.AbortController === "function",
    url: typeof globalThis.URL === "function",
    textEncoder: typeof globalThis.TextEncoder === "function",
    webCrypto:
      typeof globalThis.crypto === "object" &&
      globalThis.crypto !== null &&
      typeof globalThis.crypto.subtle === "object",
    matchMedia: typeof window !== "undefined" && typeof window.matchMedia === "function",
    resizeObserver: typeof globalThis.ResizeObserver === "function",
    cssGrid: cssSupports && CSS.supports("display", "grid"),
    cssVariables: cssSupports && CSS.supports("color", "var(--medkernel-browser-probe)"),
  };
}

export function evaluateBrowserCompatibility(
  snapshot: BrowserCapabilitySnapshot,
  checkedAt = new Date().toISOString(),
): BrowserCompatibilityReport {
  const items = CAPABILITY_DEFINITIONS.map((definition): BrowserCompatibilityItem => {
    const supported = snapshot[definition.key];
    return {
      ...definition,
      supported,
      status: capabilityStatus(supported, definition.required),
    };
  });
  const failedCount = items.filter((item) => item.status === "FAIL").length;
  const warningCount = items.filter((item) => item.status === "WARN").length;
  const overallStatus = overallCompatibilityStatus(failedCount, warningCount);
  const summary = compatibilitySummary(overallStatus, failedCount, warningCount);

  return {
    overallStatus,
    checkedAt,
    evidenceBasis: "CAPABILITY_PROBE",
    summary,
    disclaimer: "自动化能力预检不替代目标国产浏览器现场确认。",
    items,
  };
}

export function probeBrowserCompatibility(): BrowserCompatibilityReport {
  return evaluateBrowserCompatibility(detectBrowserCapabilities());
}

export function formatBrowserCompatibilityEvidence(report: BrowserCompatibilityReport): string {
  const lines = report.items.map(
    (item) =>
      `- ${item.displayName}: ${item.status}（${item.required ? "关键能力" : "增强能力"}）${
        item.supported ? "" : `；建议：${item.recommendation}`
      }`,
  );

  return [
    "当前浏览器能力预检",
    `检查时间：${report.checkedAt}`,
    "证据依据：客户端浏览器能力探测",
    `整体状态：${report.overallStatus}`,
    `结论：${report.summary}`,
    ...lines,
    `说明：${report.disclaimer}`,
  ].join("\n");
}

export async function appendBrowserCompatibilityEvidence(
  serverReport: Blob,
  browserReport: BrowserCompatibilityReport,
): Promise<Blob> {
  const serverText = await readBlobAsText(serverReport);
  const clientText = formatBrowserCompatibilityEvidence(browserReport);
  return new Blob([serverText, "\n\n", clientText, "\n"], {
    type: serverReport.type || "text/plain;charset=utf-8",
  });
}

function readBlobAsText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () =>
      typeof reader.result === "string"
        ? resolve(reader.result)
        : reject(new Error("国产化适配自检报告不是文本内容"));
    reader.onerror = () => reject(reader.error ?? new Error("国产化适配自检报告读取失败"));
    reader.readAsText(blob, "UTF-8");
  });
}
