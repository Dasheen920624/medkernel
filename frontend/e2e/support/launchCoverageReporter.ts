import type {
  FullConfig,
  FullResult,
  Reporter,
  Suite,
  TestCase,
  TestResult,
} from "@playwright/test/reporter";
import { mkdirSync, readFileSync, renameSync, writeFileSync } from "node:fs";
import path from "node:path";

import {
  buildBrowserE2eLaunchEvidence,
  type BrowserE2eAttachment,
  type BrowserE2eRunStats,
  type BrowserE2eTestResult,
} from "./launchCoverageEvidence";

class LaunchCoverageReporter implements Reporter {
  private tests: BrowserE2eTestResult[] = [];
  private startTime = new Date();

  onBegin(_config: FullConfig, _suite: Suite) {
    this.startTime = new Date();
    this.tests = [];
  }

  onTestEnd(test: TestCase, result: TestResult) {
    this.tests.push({
      file: test.location.file,
      title: test.title,
      status: result.status,
      outcome: test.outcome(),
      attachments: this.attachments(result),
    });
  }

  onEnd(result: FullResult) {
    const evidenceDir = process.env.E2E_EVIDENCE_DIR?.trim();
    if (!evidenceDir) return;
    const generatedAt = new Date().toISOString();
    const stats = this.buildStats(result, generatedAt);
    const evidence = buildBrowserE2eLaunchEvidence({
      stats,
      tests: this.tests,
      generatedAt,
    });
    const reportDir = path.join(evidenceDir, "report");
    mkdirSync(reportDir, { recursive: true });
    const outputPath = path.join(reportDir, "results.json");
    const tempPath = `${outputPath}.${process.pid}.tmp`;
    writeFileSync(tempPath, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
    renameSync(tempPath, outputPath);
  }

  private buildStats(result: FullResult, generatedAt: string): BrowserE2eRunStats {
    const unexpected = this.tests.filter((test) =>
      ["failed", "timedOut", "interrupted"].includes(test.status),
    ).length;
    const skipped = this.tests.filter((test) => test.status === "skipped").length;
    const expected = this.tests.filter((test) => test.status === "passed").length;
    const flaky = this.tests.filter((test) => test.outcome === "flaky").length;
    return {
      startTime: this.startTime.toISOString(),
      duration: Date.parse(generatedAt) - this.startTime.getTime(),
      expected,
      unexpected: result.status === "passed" ? unexpected : Math.max(unexpected, 1),
      flaky,
      skipped,
    };
  }

  private attachments(result: TestResult): BrowserE2eAttachment[] {
    return result.attachments.map((attachment) => ({
      name: attachment.name,
      contentType: attachment.contentType,
      body: attachment.body?.toString("utf8") ?? this.readAttachmentBody(attachment.path),
    }));
  }

  private readAttachmentBody(attachmentPath: string | undefined) {
    if (!attachmentPath) return undefined;
    try {
      return readFileSync(attachmentPath, "utf8");
    } catch {
      return undefined;
    }
  }
}

export default LaunchCoverageReporter;
