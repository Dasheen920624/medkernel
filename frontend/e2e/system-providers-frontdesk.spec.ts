import { expect, test, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { apiBase, appPath, ensureReadySession, expectOk } from "./support/auth";

type RuntimeOperationsSnapshot = {
  healthStatus?: string;
  databaseDialect?: string;
  migrationLocation?: string;
  activeProfiles?: string[];
  dependencies?: Array<{ key?: string; displayName?: string; status?: string; detail?: string }>;
  backup?: {
    enabled?: boolean;
    rpo?: string;
    rto?: string;
    backupScript?: string;
    restoreScript?: string;
    checksumPolicy?: string;
    drillEvidence?: {
      status?: string;
      migrationCount?: number | null;
      evidenceReference?: string | null;
      checksumEvidence?: string | null;
      drillDatabaseIsIsolated?: boolean | null;
      rpo?: string | null;
      rto?: string | null;
      detail?: string;
    };
  };
  domesticProfile?: {
    targetOs?: string;
    targetJdk?: string;
    databaseVendors?: string[];
    cryptoAlgorithms?: string[];
  };
};

type RuntimeCollectors = {
  browserErrors: string[];
  serverErrors: string[];
  operationsResponses: number[];
};

type SystemProvidersCoverageEvidence = {
  deliveryShapes: string[];
  serviceCombinations: string[];
  apiEvidence: Record<string, boolean>;
  snapshot?: {
    healthStatus?: string;
    databaseDialect?: string;
    migrationLocation?: string;
    activeProfiles?: string[];
  };
  backup?: RuntimeOperationsSnapshot["backup"];
  dependencyEvidence?: {
    dependencies: Array<{ key?: string; displayName?: string; status?: string }>;
    honestDegradationText?: string;
  };
  accessEvidence?: {
    platformAdminOperationsStatus?: number;
    clinicalOperationsStatus?: number;
    clinicalPageForbidden?: boolean;
    clinicalPageNoOperationsData?: boolean;
  };
  scenarioEvidence: Array<{ observedStages: string[] }>;
};

test.describe("服务运行保障真实前台上线演练", () => {
  test("平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖", async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);
    const runtime = collectRuntime(page);
    const coverageEvidence = createSystemProvidersCoverageEvidence();
    try {
      await ensureReadySession(page, "platform-admin");

      const snapshot = await loadRuntimeOperationsSnapshot(page);
      assertRuntimeOperationsSnapshot(snapshot);
      coverageEvidence.apiEvidence.operationsSnapshotRead = true;
      coverageEvidence.snapshot = {
        healthStatus: snapshot.healthStatus,
        databaseDialect: snapshot.databaseDialect,
        migrationLocation: snapshot.migrationLocation,
        activeProfiles: snapshot.activeProfiles ?? [],
      };
      coverageEvidence.backup = snapshot.backup;
      coverageEvidence.dependencyEvidence = {
        dependencies: (snapshot.dependencies ?? []).map((dependency) => ({
          key: dependency.key,
          displayName: dependency.displayName,
          status: dependency.status,
        })),
      };
      recordSystemProvidersStage(coverageEvidence, "平台管理员读取真实服务运行保障快照");

      await page.goto(appPath("/system/providers"), { waitUntil: "networkidle" });
      await expect(page.getByRole("heading", { name: "服务运行保障" })).toBeVisible();
      await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
      await expect(page.getByText("核心服务", { exact: true })).toBeVisible();
      await expect(page.getByText("依赖服务", { exact: true })).toBeVisible();
      await assertBackupReadinessCard(page, snapshot);
      coverageEvidence.apiEvidence.backupReadinessObserved = true;
      recordSystemProvidersStage(
        coverageEvidence,
        "前台展示备份恢复 RPO、RTO 与 SHA-256 校验策略",
      );
      await assertHonestDependencyDegradation(page, snapshot);
      coverageEvidence.apiEvidence.honestDegradationObserved = true;
      coverageEvidence.dependencyEvidence = {
        ...(coverageEvidence.dependencyEvidence ?? { dependencies: [] }),
        honestDegradationText: "核心业务继续走本地确定性主链路",
      };
      recordSystemProvidersStage(
        coverageEvidence,
        "前台展示依赖诚实降级并保留本地主链路提示",
      );
      await assertEvidenceDetailsDiagnostics(page, snapshot);
      coverageEvidence.apiEvidence.evidenceDetailsObserved = true;
      recordSystemProvidersStage(
        coverageEvidence,
        "证据详情展示部署档案、迁移路径和备份恢复诊断",
      );

      expect(
        runtime.operationsResponses.some((status) => status >= 200 && status < 300),
        "服务运行保障前台必须读取真实 /system/operations",
      ).toBe(true);
      coverageEvidence.accessEvidence = {
        platformAdminOperationsStatus: runtime.operationsResponses.find(
          (status) => status >= 200 && status < 300,
        ),
      };
      expect(runtime.serverErrors, "服务运行保障前台不应产生 HTTP 错误").toEqual([]);
      expect(runtime.browserErrors, "服务运行保障前台不应产生浏览器错误").toEqual([]);

      const clinicalAccessEvidence = await assertClinicalUserCannotReadOperations(page);
      coverageEvidence.apiEvidence.clinicalForbidden = true;
      coverageEvidence.accessEvidence = {
        ...coverageEvidence.accessEvidence,
        ...clinicalAccessEvidence,
      };
      recordSystemProvidersStage(
        coverageEvidence,
        "临床账号无法读取或展示服务运行保障快照",
      );
    } finally {
      await attachSystemProvidersCoverageEvidence(testInfo, coverageEvidence);
    }
  });

  test("临床账号不能读取或展示服务运行保障快照", async ({ page }, testInfo) => {
    test.setTimeout(120_000);
    const runtime = collectRuntime(page);
    const coverageEvidence = createSystemProvidersCoverageEvidence();
    try {
      const accessEvidence = await assertClinicalUserCannotReadOperations(page);
      coverageEvidence.apiEvidence.clinicalForbidden = true;
      coverageEvidence.accessEvidence = accessEvidence;
      recordSystemProvidersStage(
        coverageEvidence,
        "临床账号无法读取或展示服务运行保障快照",
      );

      expect(runtime.operationsResponses, "无权限页面不应发起运维快照读取").toEqual([]);
      expect(runtime.browserErrors, "无权限服务运行保障页不应产生浏览器错误").toEqual([]);
    } finally {
      await attachSystemProvidersCoverageEvidence(testInfo, coverageEvidence);
    }
  });
});

async function loadRuntimeOperationsSnapshot(page: Page) {
  const response = await page.request.get(`${apiBase}/system/operations`, {
    headers: { "X-Trace-Id": `e2e-system-providers-${Date.now()}` },
  });
  await expectOk(response, "读取服务运行保障快照");
  const payload = (await response.json()) as { data?: RuntimeOperationsSnapshot };
  return payload.data ?? {};
}

function assertRuntimeOperationsSnapshot(snapshot: RuntimeOperationsSnapshot) {
  expect(snapshot.healthStatus, "运行快照必须返回核心服务状态").toBeTruthy();
  expect(snapshot.dependencies ?? [], "运行快照必须返回依赖状态").toEqual(
    expect.arrayContaining([
      expect.objectContaining({ key: "database" }),
      expect.objectContaining({ key: "backup-restore" }),
    ]),
  );
  expect(snapshot.backup?.rpo, "备份恢复必须返回 RPO").toBeTruthy();
  expect(snapshot.backup?.rto, "备份恢复必须返回 RTO").toBeTruthy();
  expect(snapshot.backup?.checksumPolicy, "备份恢复必须返回校验策略").toContain("SHA-256");
  expect(snapshot.backup?.backupScript, "备份脚本只应作为只读证据呈现").toContain("backup.sh");
  expect(snapshot.backup?.restoreScript, "恢复脚本只应作为只读证据呈现").toContain("restore.sh");
  expect(snapshot.domesticProfile?.targetOs, "国产化档案必须返回目标操作系统").toBeTruthy();
}

async function assertBackupReadinessCard(page: Page, snapshot: RuntimeOperationsSnapshot) {
  const backup = snapshot.backup;
  const card = page
    .locator(".ant-card")
    .filter({ has: page.getByText("备份恢复就绪") })
    .first();
  await expect(card).toBeVisible();
  await expect(card.getByText(`RPO：${backup?.rpo}`)).toBeVisible();
  await expect(card.getByText(`RTO：${backup?.rto}`)).toBeVisible();
  await expect(card.getByText(backup?.checksumPolicy ?? "", { exact: true })).toBeVisible();
  if (backup?.drillEvidence?.status === "SUCCESS") {
    await expect(card.getByText("演练通过")).toBeVisible();
    await expect(
      card.getByText(`迁移校验：${backup.drillEvidence.migrationCount} 条`),
    ).toBeVisible();
  } else {
    await expect(card.getByText(backup?.drillEvidence?.detail ?? "")).toBeVisible();
  }
}

async function assertHonestDependencyDegradation(page: Page, snapshot: RuntimeOperationsSnapshot) {
  const backup = snapshot.dependencies?.find((dependency) => dependency.key === "backup-restore");
  expect(backup?.displayName).toBe("备份恢复");
  await expect(page.getByText("备份恢复").first()).toBeVisible();
  const dependencyIssueCount =
    snapshot.dependencies?.filter((dependency) => dependency.status !== "UP").length ?? 0;
  if (dependencyIssueCount === 0) {
    await expect(page.getByText("全部依赖状态正常")).toBeVisible();
    return;
  }
  const disconnected = snapshot.dependencies?.find((dependency) =>
    ["graph-projection", "search-projection", "external-provider"].includes(dependency.key ?? ""),
  );
  if (disconnected) {
    const honestText =
      disconnected.key === "graph-projection"
        ? "知识图谱未连接；核心业务继续使用关系库权威数据。"
        : disconnected.key === "search-projection"
          ? "知识搜索待验证；知识生产和审核继续使用已发布资产。"
          : "外部系统连接待验证；不伪造同步成功，请在服务对接页完成健康验证。";
    await expect(page.getByText(honestText)).toBeVisible();
  }
  await expect(page.getByText("核心业务继续走本地确定性主链路")).toBeVisible();
}

async function assertEvidenceDetailsDiagnostics(page: Page, snapshot: RuntimeOperationsSnapshot) {
  await expect(page.getByText(snapshot.backup?.backupScript ?? "")).toHaveCount(0);
  await expect(page.getByText(snapshot.backup?.restoreScript ?? "")).toHaveCount(0);

  await page.getByRole("switch", { name: "证据详情" }).click();

  await expect(
    page.getByText(`当前部署档案：${snapshot.activeProfiles?.join(" / ")}`),
  ).toBeVisible();
  await expect(page.getByText(snapshot.databaseDialect ?? "", { exact: true })).toBeVisible();
  await expect(page.getByText(snapshot.migrationLocation ?? "", { exact: true })).toBeVisible();
  await expect(page.getByText(snapshot.backup?.backupScript ?? "", { exact: true })).toBeVisible();
  await expect(page.getByText(snapshot.backup?.restoreScript ?? "", { exact: true })).toBeVisible();
  if (snapshot.backup?.drillEvidence?.evidenceReference) {
    await expect(page.getByText(snapshot.backup.drillEvidence.evidenceReference)).toBeVisible();
  }
  if (snapshot.backup?.drillEvidence?.checksumEvidence) {
    await expect(page.getByText(snapshot.backup.drillEvidence.checksumEvidence)).toBeVisible();
  }
  if (snapshot.backup?.drillEvidence?.drillDatabaseIsIsolated) {
    await expect(page.getByText("隔离库已验证")).toBeVisible();
  }
  if (snapshot.backup?.drillEvidence?.rpo) {
    await expect(page.getByText(`证据 RPO：${snapshot.backup.drillEvidence.rpo}`)).toBeVisible();
  }
  if (snapshot.backup?.drillEvidence?.rto) {
    await expect(page.getByText(`证据 RTO：${snapshot.backup.drillEvidence.rto}`)).toBeVisible();
  }
  await expect(page.getByText("backup-restore-drill.sh")).toHaveCount(0);
}

async function assertClinicalUserCannotReadOperations(page: Page) {
  await ensureReadySession(page, "clinical-user");
  const direct = await page.request.get(`${apiBase}/system/operations`, {
    headers: { "X-Trace-Id": `e2e-system-providers-forbidden-${Date.now()}` },
  });
  expect(direct.status(), "临床账号不能直接读取服务运行保障快照").toBe(403);

  await page.goto(appPath("/system/providers"), { waitUntil: "networkidle" });
  await expect(page.getByText("当前权限不足", { exact: true })).toBeVisible();
  await expect(page.getByText("关系数据库")).toHaveCount(0);
  await expect(page.getByText("备份恢复就绪")).toHaveCount(0);
  return {
    clinicalOperationsStatus: direct.status(),
    clinicalPageForbidden: true,
    clinicalPageNoOperationsData: true,
  };
}

function collectRuntime(page: Page): RuntimeCollectors {
  return {
    browserErrors: collectBrowserErrors(page),
    serverErrors: collectServerErrors(page),
    operationsResponses: collectOperationsResponses(page),
  };
}

function collectBrowserErrors(page: Page) {
  const errors: string[] = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      errors.push(message.text());
    }
  });
  page.on("pageerror", (error) => errors.push(error.message));
  return errors;
}

function collectServerErrors(page: Page) {
  const errors: string[] = [];
  page.on("response", (response) => {
    if (response.status() >= 400 && response.url().includes("/medkernel/")) {
      errors.push(`${response.status()} ${response.request().method()} ${response.url()}`);
    }
  });
  return errors;
}

function collectOperationsResponses(page: Page) {
  const statuses: number[] = [];
  page.on("response", (response) => {
    if (response.url().includes("/medkernel/api/v1/system/operations")) {
      statuses.push(response.status());
    }
  });
  return statuses;
}

function createSystemProvidersCoverageEvidence(): SystemProvidersCoverageEvidence {
  return {
    deliveryShapes: ["MANAGEMENT_WORKSPACE"],
    serviceCombinations: ["COMPLIANCE_OPERATIONS"],
    apiEvidence: {
      operationsSnapshotRead: false,
      backupReadinessObserved: false,
      honestDegradationObserved: false,
      evidenceDetailsObserved: false,
      clinicalForbidden: false,
    },
    scenarioEvidence: [{ observedStages: [] }],
  };
}

function recordSystemProvidersStage(
  evidence: SystemProvidersCoverageEvidence,
  stage: string,
) {
  const stages = evidence.scenarioEvidence[0]?.observedStages ?? [];
  if (!stages.includes(stage)) {
    stages.push(stage);
  }
  evidence.scenarioEvidence = [{ observedStages: stages }];
}

async function attachSystemProvidersCoverageEvidence(
  testInfo: TestInfo,
  evidence: SystemProvidersCoverageEvidence,
) {
  const recordPath = testInfo.outputPath("system-providers-operations-codes.json");
  await writeFile(recordPath, `${JSON.stringify(evidence, null, 2)}\n`, "utf8");
  await testInfo.attach("system-providers-operations-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}
