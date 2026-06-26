import { describe, expect, it } from "vitest";

import {
  appendBrowserCompatibilityEvidence,
  evaluateBrowserCompatibility,
  formatBrowserCompatibilityEvidence,
  type BrowserCapabilitySnapshot,
} from "./browserCompatibility";

const ALL_CAPABILITIES: BrowserCapabilitySnapshot = {
  esModules: true,
  fetch: true,
  abortController: true,
  url: true,
  textEncoder: true,
  webCrypto: true,
  matchMedia: true,
  resizeObserver: true,
  cssGrid: true,
  cssVariables: true,
};

function readBlobAsText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () =>
      typeof reader.result === "string"
        ? resolve(reader.result)
        : reject(new Error("测试报告不是文本"));
    reader.onerror = () => reject(reader.error ?? new Error("测试报告读取失败"));
    reader.readAsText(blob, "UTF-8");
  });
}

describe("browserCompatibility", () => {
  it("关键与增强能力全部可用时返回通过", () => {
    const report = evaluateBrowserCompatibility(ALL_CAPABILITIES, "2026-06-18T00:00:00Z");

    expect(report.overallStatus).toBe("PASS");
    expect(report.items).toHaveLength(10);
    expect(report.items.every((item) => item.status === "PASS")).toBe(true);
    expect(report.evidenceBasis).toBe("CAPABILITY_PROBE");
  });

  it("关键能力缺失时返回不通过并给出具体修复提示", () => {
    const report = evaluateBrowserCompatibility(
      { ...ALL_CAPABILITIES, webCrypto: false },
      "2026-06-18T00:00:00Z",
    );

    expect(report.overallStatus).toBe("FAIL");
    expect(report.items).toContainEqual(
      expect.objectContaining({
        key: "webCrypto",
        status: "FAIL",
        required: true,
      }),
    );
    expect(report.summary).toContain("关键能力");
  });

  it("仅增强能力缺失时返回警告而不是伪造失败或通过", () => {
    const report = evaluateBrowserCompatibility(
      { ...ALL_CAPABILITIES, matchMedia: false },
      "2026-06-18T00:00:00Z",
    );

    expect(report.overallStatus).toBe("WARN");
    expect(report.items).toContainEqual(
      expect.objectContaining({
        key: "matchMedia",
        status: "WARN",
        required: false,
      }),
    );
  });

  it("格式化证据不把浏览器名称或 User-Agent 当作认证结论", () => {
    const report = evaluateBrowserCompatibility(ALL_CAPABILITIES, "2026-06-18T00:00:00Z");
    const text = formatBrowserCompatibilityEvidence(report);

    expect(text).toContain("证据依据：客户端浏览器能力探测");
    expect(text).toContain("不替代目标国产浏览器现场确认");
    expect(text).not.toContain("User-Agent");
    expect(text).not.toContain("浏览器认证通过");
  });

  it("导出时追加客户端能力证据且不改写服务报告", async () => {
    const serverReport = new Blob(["MedKernel 国产化服务自检报告"], {
      type: "text/plain;charset=utf-8",
    });
    const browserReport = evaluateBrowserCompatibility(ALL_CAPABILITIES, "2026-06-18T00:00:00Z");

    const combined = await appendBrowserCompatibilityEvidence(serverReport, browserReport);
    const text = await readBlobAsText(combined);

    expect(text).toContain("MedKernel 国产化服务自检报告");
    expect(text).toContain("当前浏览器能力预检");
    expect(text).not.toContain("Cookie");
    expect(text).not.toContain("localStorage");
    expect(text).not.toContain("令牌");
  });
});
