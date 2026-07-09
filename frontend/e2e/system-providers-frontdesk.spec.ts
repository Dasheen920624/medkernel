import { expect, test, type Locator, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import {
  apiBase,
  appPath,
  ensureRehearsalRuntimeAssetApiSession,
  ensureReadySession,
  expectOk,
} from "./support/auth";
import { attachPlatformAdminEntryCoreActionEvidence } from "./support/platformAdminEntryCoreActions";

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

type RuntimeIdentityEvidence = {
  releaseId: string;
  revisionNo: number;
  manifestSha256: string;
  assetCount: number;
};

type RuntimeConsumerIdentityEvidence = RuntimeIdentityEvidence & {
  contractVersion: string;
};

type ClinicalSmokeEvidence = {
  role: "clinical-user";
  page: "/mpi";
  patientId: string;
  contextSnapshotId: string;
  runtimeReleaseId: string;
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
  runtimeContinuityEvidence?: {
    currentRuntime: RuntimeIdentityEvidence;
    runtimeConsumer: RuntimeConsumerIdentityEvidence;
    clinicalSmoke: ClinicalSmokeEvidence;
  };
  scenarioEvidence: Array<{ observedStages: string[] }>;
  scenarioConditionEvidence?: Array<{
    code: string;
    scenarioCode: string;
    condition: string;
    source: string;
    evidence: string[];
  }>;
};

test.describe("服务运行保障真实前台上线演练", () => {
  test("平台管理员可只读核查运行状态、备份恢复证据和诚实降级依赖", async ({ page }, testInfo) => {
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
      recordSystemProvidersStage(coverageEvidence, "前台展示备份恢复 RPO、RTO 与 SHA-256 校验策略");
      await assertHonestDependencyDegradation(page, snapshot);
      coverageEvidence.apiEvidence.honestDegradationObserved = true;
      coverageEvidence.dependencyEvidence = {
        ...(coverageEvidence.dependencyEvidence ?? { dependencies: [] }),
        honestDegradationText: "核心业务继续走本地确定性主链路",
      };
      recordSystemProvidersStage(coverageEvidence, "前台展示依赖诚实降级并保留本地主链路提示");
      await assertEvidenceDetailsDiagnostics(page, snapshot);
      coverageEvidence.apiEvidence.evidenceDetailsObserved = true;
      recordSystemProvidersStage(coverageEvidence, "证据详情展示部署档案、迁移路径和备份恢复诊断");

      if (backupDrillSucceeded(snapshot)) {
        const runtimeContinuity = await readRuntimeContinuityAfterRestore(page);
        coverageEvidence.apiEvidence.runtimeReadbackObserved = true;
        coverageEvidence.apiEvidence.runtimeConsumerReadbackObserved = true;
        coverageEvidence.runtimeContinuityEvidence = {
          currentRuntime: runtimeContinuity.currentRuntime,
          runtimeConsumer: runtimeContinuity.runtimeConsumer,
          clinicalSmoke: await createClinicalSmokeAfterRestore(
            page,
            runtimeContinuity.currentRuntime,
          ),
        };
        coverageEvidence.apiEvidence.clinicalSmokeAfterRestore = true;
        recordSystemProvidersStage(
          coverageEvidence,
          "恢复后后端当前机构生效版本与第三方运行契约读回一致",
        );
        recordSystemProvidersStage(
          coverageEvidence,
          "临床账号恢复后完成患者主索引和上下文主链路冒烟",
        );
      } else {
        recordSystemProvidersStage(
          coverageEvidence,
          "备份恢复隔离演练未完成，服务运行保障诚实展示待演练状态",
        );
      }

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
      recordSystemProvidersStage(coverageEvidence, "临床账号无法读取或展示服务运行保障快照");
    } finally {
      await attachSystemProvidersCoverageEvidence(testInfo, coverageEvidence);
      await attachPlatformAdminEntryCoreActionEvidence(testInfo, {
        menuKey: "system-providers",
        role: "platform-admin",
        path: "/system/providers",
        frontdeskAction: "前台核查运行快照、备份恢复证据、依赖诚实降级和临床账号权限隔离",
        serviceOperation: "GET /api/v1/system/operations",
        serviceStatus: coverageEvidence.accessEvidence?.platformAdminOperationsStatus ?? 0,
        readbackVerified:
          coverageEvidence.apiEvidence.operationsSnapshotRead === true &&
          coverageEvidence.apiEvidence.backupReadinessObserved === true &&
          coverageEvidence.apiEvidence.honestDegradationObserved === true &&
          coverageEvidence.apiEvidence.evidenceDetailsObserved === true,
        auditVerified: coverageEvidence.apiEvidence.clinicalForbidden === true,
      });
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
      recordSystemProvidersStage(coverageEvidence, "临床账号无法读取或展示服务运行保障快照");

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

function backupDrillSucceeded(snapshot: RuntimeOperationsSnapshot) {
  const drill = snapshot.backup?.drillEvidence;
  if (drill?.status !== "SUCCESS") {
    return false;
  }
  expect(drill.migrationCount ?? 0, "隔离恢复必须校验迁移历史").toBeGreaterThan(0);
  expect(drill.checksumEvidence, "隔离恢复必须返回校验摘要证据").toBeTruthy();
  expect(drill.drillDatabaseIsIsolated, "备份恢复演练必须使用隔离库").toBe(true);
  return true;
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

async function readRuntimeContinuityAfterRestore(page: Page) {
  await ensureRehearsalRuntimeAssetApiSession(page);
  const hospitalResponse = await page.request.get(
    `${apiBase}/engine/org/org-units/e2e-rehearsal-hospital`,
    { headers: { "X-Trace-Id": `e2e-system-providers-hospital-${Date.now()}` } },
  );
  await expectOk(hospitalResponse, "读取本地上线演练医院");
  const hospital = await responseData(hospitalResponse);
  const hospitalId = requireText(textField(hospital, "id"), "本地上线演练医院必须返回 hospitalId");

  const currentResponse = await page.request.get(
    `${apiBase}/engine/releases/hospitals/${encodeURIComponent(
      hospitalId,
    )}/runtime-releases/current`,
    { headers: { "X-Trace-Id": `e2e-system-providers-current-runtime-${Date.now()}` } },
  );
  await expectOk(currentResponse, "读取恢复后当前机构生效版本");
  const currentRuntime = parseCurrentRuntimeIdentity(
    await responseData(currentResponse),
    "恢复后当前机构生效版本",
  );

  const consumerResponse = await page.request.get(
    `${apiBase}/engine/integration/knowledge-runtime/runtime-release/current`,
    { headers: { "X-Trace-Id": `e2e-system-providers-runtime-consumer-${Date.now()}` } },
  );
  await expectOk(consumerResponse, "读取恢复后第三方运行契约");
  const runtimeConsumer = parseRuntimeConsumerIdentity(
    await responseData(consumerResponse),
    "恢复后第三方运行契约",
  );

  expect(runtimeConsumer.contractVersion, "第三方运行契约版本必须稳定").toBe("v1");
  expect(runtimeConsumer.releaseId, "第三方运行契约必须读取同一 current runtime").toBe(
    currentRuntime.releaseId,
  );
  expect(runtimeConsumer.revisionNo, "第三方运行契约修订号必须一致").toBe(
    currentRuntime.revisionNo,
  );
  expect(runtimeConsumer.manifestSha256, "第三方运行契约清单摘要必须一致").toBe(
    currentRuntime.manifestSha256,
  );
  expect(runtimeConsumer.assetCount, "第三方运行契约资产数量必须一致").toBe(
    currentRuntime.assetCount,
  );
  return { currentRuntime, runtimeConsumer };
}

async function createClinicalSmokeAfterRestore(
  page: Page,
  currentRuntime: RuntimeIdentityEvidence,
): Promise<ClinicalSmokeEvidence> {
  await ensureReadySession(page, "clinical-user");
  await page.goto(appPath("/mpi"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "患者索引" })).toBeVisible();
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);

  await page.getByRole("button", { name: "新增患者" }).click();
  const patientDialog = page.getByRole("dialog", { name: /新增患者主索引/ });
  await expect(patientDialog).toBeVisible();
  const suffix = String(Date.now()).slice(-4);
  const maskedName = `恢*${suffix.slice(-1)}`;
  await patientDialog.getByLabel("脱敏姓名").fill(maskedName);
  const gender = patientDialog.getByRole("combobox", { name: "性别" });
  await gender.click();
  await gender.press("ArrowDown");
  await gender.press("Enter");
  await patientDialog.getByRole("spinbutton", { name: "年龄" }).fill("66");
  await patientDialog.getByLabel("身份证后四位").fill(suffix);
  const patientResponsePromise = waitForPost(page, "/engine/mpi/patients");
  await patientDialog.getByRole("button", { name: "保存患者" }).click();
  const patientResponse = await patientResponsePromise;
  await expectOk(patientResponse, "恢复后临床前台创建脱敏患者主索引");
  const patient = await responseData(patientResponse);
  const patientId = requireText(textField(patient, "mpiId"), "恢复后患者创建必须返回 MPI");
  await expect(patientDialog).toBeHidden({ timeout: 20_000 });

  await page.getByPlaceholder("支持按姓名或院内患者编号检索...").fill(maskedName);
  await page.getByRole("button", { name: /检索过滤/ }).click();
  const row = page
    .getByRole("row", { name: new RegExp(`${escapeRegExp(maskedName)}.*${suffix}`) })
    .first();
  await expect(row).toBeVisible({ timeout: 20_000 });
  await row.getByRole("button", { name: /患者360/ }).click();
  await expect(page.getByRole("button", { name: "建立当前就诊上下文" })).toBeVisible({
    timeout: 20_000,
  });
  await page.getByRole("button", { name: "建立当前就诊上下文" }).click();
  const contextDialog = page.getByRole("dialog", { name: "建立当前就诊上下文" });
  await expect(contextDialog).toBeVisible();
  await chooseDialogOption(page, contextDialog, "就诊类型", "门诊复诊");
  await contextDialog.getByLabel("诊断/随访病种").fill("恢复后上线主链路冒烟");
  await chooseDialogOption(page, contextDialog, "风险分层", "中风险");
  await contextDialog
    .getByLabel("建立原因")
    .fill("恢复后临床前台演练：验证患者主索引和当前就诊上下文仍按当前机构生效版本运行。");
  const contextResponsePromise = waitForPost(page, "/engine/context/snapshots");
  await contextDialog.getByRole("button", { name: "生成上下文快照" }).click();
  const contextResponse = await contextResponsePromise;
  await expectOk(contextResponse, "恢复后临床前台建立当前就诊上下文");
  const context = await responseData(contextResponse);
  const contextSnapshotId = requireText(
    textField(context, "snapshotId"),
    "恢复后上下文创建必须返回快照 ID",
  );
  const runtimeReleaseId = requireText(
    textField(context, "runtimeReleaseId"),
    "恢复后上下文创建必须绑定机构生效版本",
  );
  expect(runtimeReleaseId, "恢复后临床上下文必须绑定恢复后 current runtime").toBe(
    currentRuntime.releaseId,
  );
  await expect(contextDialog).toBeHidden({ timeout: 20_000 });
  await expect(page.getByText("当前就诊上下文已建立")).toBeVisible({ timeout: 20_000 });

  return {
    role: "clinical-user",
    page: "/mpi",
    patientId,
    contextSnapshotId,
    runtimeReleaseId,
  };
}

function parseCurrentRuntimeIdentity(value: unknown, label: string): RuntimeIdentityEvidence {
  const release = recordField(value, "release");
  const releaseId = requireText(textField(release, "releaseId"), `${label} 必须返回 releaseId`);
  const revisionNo = requireNumber(numberField(release, "revisionNo"), `${label} 必须返回修订号`);
  const manifestSha256 = requireText(
    textField(release, "manifestSha256"),
    `${label} 必须返回清单摘要`,
  );
  expect(manifestSha256, `${label} 清单摘要必须是 SHA-256`).toMatch(/^[0-9a-f]{64}$/i);
  const assets = arrayField(value, "items").filter(
    (item) => textField(item, "entryState") === "ACTIVE" && textField(item, "versionId"),
  );
  expect(assets.length, `${label} 必须返回当前启用资产`).toBeGreaterThan(0);
  return { releaseId, revisionNo, manifestSha256, assetCount: assets.length };
}

function parseRuntimeConsumerIdentity(
  value: unknown,
  label: string,
): RuntimeConsumerIdentityEvidence {
  const contractVersion = requireText(
    textField(value, "contractVersion"),
    `${label} 必须返回契约版本`,
  );
  const releaseId = requireText(textField(value, "releaseId"), `${label} 必须返回 releaseId`);
  const revisionNo = requireNumber(numberField(value, "revisionNo"), `${label} 必须返回修订号`);
  const manifestSha256 = requireText(
    textField(value, "manifestSha256"),
    `${label} 必须返回清单摘要`,
  );
  expect(manifestSha256, `${label} 清单摘要必须是 SHA-256`).toMatch(/^[0-9a-f]{64}$/i);
  const assetCount = requireNumber(numberField(value, "assetCount"), `${label} 必须返回资产数`);
  expect(assetCount, `${label} 资产数必须为正`).toBeGreaterThan(0);
  expect(arrayField(value, "assets").length, `${label} assetCount 必须与资产明细一致`).toBe(
    assetCount,
  );
  return { contractVersion, releaseId, revisionNo, manifestSha256, assetCount };
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
      runtimeReadbackObserved: false,
      runtimeConsumerReadbackObserved: false,
      clinicalSmokeAfterRestore: false,
      clinicalForbidden: false,
    },
    scenarioEvidence: [{ observedStages: [] }],
  };
}

async function responseData(response: { json(): Promise<unknown> }) {
  const payload = (await response.json()) as { data?: unknown };
  return payload.data ?? {};
}

function arrayField(value: unknown, field: string) {
  const raw = recordField(value, field);
  return Array.isArray(raw) ? raw : [];
}

function recordField(value: unknown, field: string) {
  const record = recordValue(value);
  return record ? record[field] : undefined;
}

function textField(value: unknown, field: string) {
  const record = recordValue(value);
  const raw = record ? record[field] : undefined;
  return typeof raw === "string" && raw.trim() ? raw.trim() : null;
}

function numberField(value: unknown, field: string) {
  const record = recordValue(value);
  const raw = record ? record[field] : undefined;
  return typeof raw === "number" && Number.isFinite(raw) ? raw : null;
}

function recordValue(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function requireText(value: string | null, message: string) {
  expect(value, message).toBeTruthy();
  return value ?? "";
}

function requireNumber(value: number | null, message: string) {
  expect(value, message).not.toBeNull();
  return value ?? 0;
}

async function chooseDialogOption(page: Page, dialog: Locator, label: string, optionText: string) {
  if (
    await dialog
      .getByText(optionText, { exact: true })
      .isVisible()
      .catch(() => false)
  ) {
    return;
  }
  const field = dialog.getByLabel(label);
  const selectSelector = field
    .locator(
      "xpath=ancestor::*[contains(concat(' ', normalize-space(@class), ' '), ' ant-select ')]",
    )
    .first()
    .locator(".ant-select-selector")
    .first();
  if (await selectSelector.isVisible().catch(() => false)) {
    await selectSelector.click();
  } else {
    await field.click();
  }
  const dropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)").last();
  await expect(dropdown, `${label} 下拉应展开`).toBeVisible({ timeout: 5_000 });
  const option = dropdown
    .locator(".ant-select-item-option:not(.ant-select-item-option-disabled)")
    .filter({ hasText: optionText })
    .first();
  await expect(option, `${label} 下拉必须存在 ${optionText}`).toBeVisible({ timeout: 5_000 });
  await option.click();
  await expect(dialog.getByText(optionText, { exact: true })).toBeVisible({ timeout: 5_000 });
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function waitForPost(page: Page, pathIncludes: string) {
  return page.waitForResponse(
    (response) => response.request().method() === "POST" && response.url().includes(pathIncludes),
    { timeout: 60_000 },
  );
}

function recordSystemProvidersStage(evidence: SystemProvidersCoverageEvidence, stage: string) {
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
  const enrichedEvidence = {
    ...evidence,
    scenarioConditionEvidence: collectSystemProvidersScenarioConditionEvidence(evidence),
  };
  await writeFile(recordPath, `${JSON.stringify(enrichedEvidence, null, 2)}\n`, "utf8");
  await testInfo.attach("system-providers-operations-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function collectSystemProvidersScenarioConditionEvidence(
  evidence: SystemProvidersCoverageEvidence,
) {
  const rows: NonNullable<SystemProvidersCoverageEvidence["scenarioConditionEvidence"]> = [];
  if (
    evidence.apiEvidence.runtimeReadbackObserved === true &&
    evidence.apiEvidence.runtimeConsumerReadbackObserved === true &&
    evidence.apiEvidence.clinicalSmokeAfterRestore === true &&
    evidence.runtimeContinuityEvidence
  ) {
    rows.push({
      code: "S15__NORMAL",
      scenarioCode: "S15",
      condition: "NORMAL",
      source: "SYSTEM_OPERATIONS_RESTORE_CONTINUITY",
      evidence: [
        "备份恢复成功后当前机构生效版本和第三方运行契约一致",
        "临床账号恢复后完成患者主索引和上下文主链路冒烟",
      ],
    });
  }
  if (
    evidence.apiEvidence.honestDegradationObserved === true &&
    evidence.dependencyEvidence?.honestDegradationText?.includes("核心业务继续走本地确定性主链路")
  ) {
    rows.push({
      code: "S15__DEGRADATION",
      scenarioCode: "S15",
      condition: "DEGRADATION",
      source: "SYSTEM_DEPENDENCY_HONEST_DEGRADATION",
      evidence: ["外部依赖断连或不健康时前台诚实展示降级且本地确定性主链路继续可用"],
    });
  }
  if (
    evidence.apiEvidence.clinicalForbidden === true &&
    evidence.accessEvidence?.clinicalOperationsStatus === 403 &&
    evidence.accessEvidence.clinicalPageForbidden === true &&
    evidence.accessEvidence.clinicalPageNoOperationsData === true
  ) {
    rows.push({
      code: "S14__ABNORMAL",
      scenarioCode: "S14",
      condition: "ABNORMAL",
      source: "CLINICAL_SYSTEM_OPERATIONS_FORBIDDEN",
      evidence: ["临床账号 API 读取系统运维快照返回 403，前台只展示权限不足且不展示运维数据"],
    });
  }
  return rows.length > 0 ? rows : undefined;
}
