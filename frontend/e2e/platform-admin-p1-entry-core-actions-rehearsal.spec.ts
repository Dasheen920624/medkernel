import { expect, test, type APIResponse, type Page, type TestInfo } from "@playwright/test";
import { writeFile } from "node:fs/promises";

import { apiBase, appPath, ensureReadySession, expectOk } from "./support/auth";
import {
  attachPlatformAdminEntryCoreActionEvidence,
  platformAdminP1EntryCoreActionScopeStatement,
} from "./support/platformAdminEntryCoreActions";

type RuntimeDiagnosticsEvidence = {
  runtimeStatus: number;
  operationsStatus: number;
  apiContractsStatus: number;
  contractCount: number;
  pluginBoundaryObserved: boolean;
  clinicalRuntimeStatus: number;
  clinicalPageForbidden: boolean;
};

type DomesticCheckEvidence = {
  operationsStatus: number;
  reportStatus: number;
  reportContainsSummary: boolean;
  issueFilterObserved: boolean;
  unknownFilterObserved: boolean;
  clinicalOperationsStatus: number;
  clinicalPageForbidden: boolean;
};

type PlatformAdminP1SystemOperationsEvidence = {
  runtimeDiagnosticsEvidence: RuntimeDiagnosticsEvidence;
  domesticCheckEvidence: DomesticCheckEvidence;
  scenarioConditionEvidence?: Array<{
    code: string;
    scenarioCode: string;
    condition: string;
    source: string;
    evidence: string[];
  }>;
};

test.describe("平台管理员 P1 系统运维入口核心动作真实前台演练", () => {
  test("运行诊断和国产化适配自检均完成真实前台动作、服务回读与权限边界", async ({
    page,
  }, testInfo) => {
    test.setTimeout(180_000);

    const runtimeDiagnosticsEvidence = await exerciseRuntimeDiagnostics(page);
    const domesticCheckEvidence = await exerciseDomesticCheck(page);
    await attachRuntimeP1Evidence(testInfo, {
      runtimeDiagnosticsEvidence,
      domesticCheckEvidence,
    });
    await attachPlatformAdminEntryCoreActionEvidence(
      testInfo,
      [
        {
          menuKey: "runtime-diagnostics",
          role: "platform-admin",
          path: "/system/runtime-diagnostics",
          frontdeskAction: "前台核查运行摘要、服务契约和扩展能力授权边界",
          serviceOperation:
            "GET /api/v1/system/runtime + GET /api/v1/system/runtime-diagnostics/api-contracts",
          serviceStatus: minSuccessfulStatus(
            runtimeDiagnosticsEvidence.runtimeStatus,
            runtimeDiagnosticsEvidence.apiContractsStatus,
          ),
          readbackVerified:
            is2xx(runtimeDiagnosticsEvidence.runtimeStatus) &&
            is2xx(runtimeDiagnosticsEvidence.operationsStatus) &&
            is2xx(runtimeDiagnosticsEvidence.apiContractsStatus) &&
            runtimeDiagnosticsEvidence.contractCount > 0 &&
            runtimeDiagnosticsEvidence.pluginBoundaryObserved,
          auditVerified:
            runtimeDiagnosticsEvidence.clinicalRuntimeStatus === 403 &&
            runtimeDiagnosticsEvidence.clinicalPageForbidden,
        },
        {
          menuKey: "domestic-check",
          role: "platform-admin",
          path: "/advanced/domestic",
          frontdeskAction: "前台筛选国产化待确认项并导出国产化适配自检报告",
          serviceOperation:
            "GET /api/v1/system/operations + GET /api/v1/system/operations/domestic-report",
          serviceStatus: minSuccessfulStatus(
            domesticCheckEvidence.operationsStatus,
            domesticCheckEvidence.reportStatus,
          ),
          readbackVerified:
            is2xx(domesticCheckEvidence.operationsStatus) &&
            is2xx(domesticCheckEvidence.reportStatus) &&
            domesticCheckEvidence.reportContainsSummary &&
            domesticCheckEvidence.issueFilterObserved &&
            domesticCheckEvidence.unknownFilterObserved,
          auditVerified:
            domesticCheckEvidence.clinicalOperationsStatus === 403 &&
            domesticCheckEvidence.clinicalPageForbidden,
        },
      ],
      {
        matrixCode: "PLATFORM_ADMIN_P1_ENTRY_CORE_ACTIONS",
        scopeStatement: platformAdminP1EntryCoreActionScopeStatement,
      },
    );
  });
});

async function exerciseRuntimeDiagnostics(page: Page): Promise<RuntimeDiagnosticsEvidence> {
  await ensureReadySession(page, "platform-admin");
  const runtimeResponse = await page.request.get(`${apiBase}/system/runtime`, {
    headers: { "X-Trace-Id": `e2e-platform-p1-runtime-${Date.now()}` },
  });
  await expectOk(runtimeResponse, "读取运行探针");
  const runtimeBody = (await runtimeResponse.json()) as { data?: Record<string, unknown> };
  expect(runtimeBody.data?.javaVersion, "运行诊断必须返回 Java 版本").toBeTruthy();

  const operationsResponse = await page.request.get(`${apiBase}/system/operations`, {
    headers: { "X-Trace-Id": `e2e-platform-p1-operations-${Date.now()}` },
  });
  await expectOk(operationsResponse, "读取系统运维快照");

  const apiContractsResponse = await page.request.get(
    `${apiBase}/system/runtime-diagnostics/api-contracts`,
    { headers: { "X-Trace-Id": `e2e-platform-p1-contracts-${Date.now()}` } },
  );
  await expectOk(apiContractsResponse, "读取运行诊断服务目录");
  const contractsBody = (await apiContractsResponse.json()) as {
    data?: { contracts?: Array<{ id?: string; title?: string }> };
  };
  const contractCount = contractsBody.data?.contracts?.length ?? 0;
  expect(contractCount, "运行诊断必须回读至少一个服务契约").toBeGreaterThan(0);

  await page.goto(appPath("/system/runtime-diagnostics"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "运行诊断" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expect(page.getByRole("tab", { name: /服务目录/u })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "服务" })).toBeVisible();
  await page.getByPlaceholder("搜索服务、路径或权限").fill("运行诊断");
  await expect(page.getByText("运行诊断服务").first()).toBeVisible({ timeout: 30_000 });
  await page.getByRole("tab", { name: "扩展能力" }).click();
  await expect(page.getByRole("button", { name: "登记扩展能力" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("边界", { exact: true }).first()).toBeVisible();

  const clinical = await assertClinicalUserCannotReadSystemPage(
    page,
    `${apiBase}/system/runtime`,
    "/system/runtime-diagnostics",
    "临床账号不能读取运行诊断探针",
  );

  return {
    runtimeStatus: runtimeResponse.status(),
    operationsStatus: operationsResponse.status(),
    apiContractsStatus: apiContractsResponse.status(),
    contractCount,
    pluginBoundaryObserved: true,
    clinicalRuntimeStatus: clinical.directStatus,
    clinicalPageForbidden: clinical.pageForbidden,
  };
}

async function exerciseDomesticCheck(page: Page): Promise<DomesticCheckEvidence> {
  await ensureReadySession(page, "platform-admin");
  const operationsResponse = await page.request.get(`${apiBase}/system/operations`, {
    headers: { "X-Trace-Id": `e2e-platform-p1-domestic-operations-${Date.now()}` },
  });
  await expectOk(operationsResponse, "读取国产化适配自检快照");
  const operations = (await operationsResponse.json()) as {
    data?: { domesticCompatibility?: { summary?: string; items?: unknown[] } };
  };
  expect(operations.data?.domesticCompatibility?.summary, "国产化自检必须返回摘要").toBeTruthy();
  expect(operations.data?.domesticCompatibility?.items?.length ?? 0).toBeGreaterThan(0);

  const reportResponse = await page.request.get(`${apiBase}/system/operations/domestic-report`, {
    headers: { "X-Trace-Id": `e2e-platform-p1-domestic-report-${Date.now()}` },
  });
  await expectOk(reportResponse, "导出国产化适配自检报告");
  const report = await reportResponse.text();

  await page.goto(appPath("/advanced/domestic"), { waitUntil: "networkidle" });
  await expect(page.getByRole("heading", { name: "国产化适配自检" })).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText("当前权限不足", { exact: true })).toHaveCount(0);
  await expect(page.getByText("逐项自检")).toBeVisible();
  await selectSegmentedOption(page, "不兼容");
  await selectSegmentedOption(page, "待确认");
  await expect(page.getByRole("button", { name: "导出报告" })).toBeVisible();

  const clinical = await assertClinicalUserCannotReadSystemPage(
    page,
    `${apiBase}/system/operations`,
    "/advanced/domestic",
    "临床账号不能读取国产化适配自检",
  );

  return {
    operationsStatus: operationsResponse.status(),
    reportStatus: reportResponse.status(),
    reportContainsSummary:
      report.includes("MedKernel") && report.includes("国产化") && report.length > 20,
    issueFilterObserved: true,
    unknownFilterObserved: true,
    clinicalOperationsStatus: clinical.directStatus,
    clinicalPageForbidden: clinical.pageForbidden,
  };
}

async function selectSegmentedOption(page: Page, name: string) {
  const option = page.getByRole("option", { name, exact: true });
  await option.click();
  await expect(option).toHaveAttribute("aria-selected", "true");
}

async function assertClinicalUserCannotReadSystemPage(
  page: Page,
  directUrl: string,
  pagePath: string,
  message: string,
) {
  await ensureReadySession(page, "clinical-user");
  const direct = await page.request.get(directUrl, {
    headers: { "X-Trace-Id": `e2e-platform-p1-forbidden-${Date.now()}` },
  });
  expect(direct.status(), message).toBe(403);
  await page.goto(appPath(pagePath), { waitUntil: "networkidle" });
  await expect(page.getByText("当前权限不足", { exact: true })).toBeVisible({
    timeout: 30_000,
  });
  return { directStatus: direct.status(), pageForbidden: true };
}

async function attachRuntimeP1Evidence(
  testInfo: TestInfo,
  evidence: PlatformAdminP1SystemOperationsEvidence,
) {
  const recordPath = testInfo.outputPath("platform-admin-p1-system-operations-codes.json");
  await writeFile(
    recordPath,
    `${JSON.stringify(
      {
        scopeStatement:
          "平台管理员 P1 系统运维入口真实前台证据：只证明运行诊断与国产化适配自检两个入口的代表核心动作，不代表 6 个平台管理员入口全部闭环，不代表全部产品入口业务动作闭环，不代表完整上线验收。",
        ...evidence,
        scenarioConditionEvidence: collectPlatformAdminP1ScenarioConditionEvidence(evidence),
      },
      null,
      2,
    )}\n`,
    "utf8",
  );
  await testInfo.attach("platform-admin-p1-system-operations-codes", {
    path: recordPath,
    contentType: "application/json",
  });
}

function collectPlatformAdminP1ScenarioConditionEvidence(
  evidence: PlatformAdminP1SystemOperationsEvidence,
) {
  if (
    evidence.runtimeDiagnosticsEvidence.clinicalRuntimeStatus !== 403 ||
    evidence.runtimeDiagnosticsEvidence.clinicalPageForbidden !== true ||
    evidence.domesticCheckEvidence.clinicalOperationsStatus !== 403 ||
    evidence.domesticCheckEvidence.clinicalPageForbidden !== true
  ) {
    return undefined;
  }
  return [
    {
      code: "S14__ABNORMAL",
      scenarioCode: "S14",
      condition: "ABNORMAL",
      source: "P1_SYSTEM_OPERATIONS_FORBIDDEN",
      evidence: [
        "临床账号直接读取运行诊断和国产化自检服务均返回 403",
        "临床账号访问 P1 系统运维页面只展示权限不足",
      ],
    },
  ];
}

function is2xx(status: number) {
  return status >= 200 && status < 300;
}

function minSuccessfulStatus(...statuses: number[]) {
  const successful = statuses.filter(is2xx);
  return successful.length ? Math.min(...successful) : 0;
}
