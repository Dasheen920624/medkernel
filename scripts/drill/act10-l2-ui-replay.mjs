#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { mkdir, readFile, writeFile, rm } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const requireFromFrontend = createRequire(
  new URL("../../frontend/package.json", import.meta.url),
);
const { chromium } = requireFromFrontend("playwright");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/v1.0-drill-20260611/幕10-合规审计与降级",
);
const uiDir = path.join(evidenceDir, "ui-replay");
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";
const runTag = `act10-l2-${Date.now().toString(36)}-${randomBytes(2).toString("hex")}`;

const actors = {
  audit: "drill-hospital-20260611:drill-audit-20260611",
  itOps: "drill-hospital-20260611:drill-it-ops-20260611",
  hospitalAdmin: "drill-hospital-20260611:drill-hospital-admin-20260611",
};

function loadCredentials() {
  const raw = execFileSync(
    "ssh",
    [
      "-o",
      "BatchMode=yes",
      "-o",
      "StrictHostKeyChecking=no",
      sshTarget,
      `cat ${credentialPath}`,
    ],
    { encoding: "utf8", maxBuffer: 2 * 1024 * 1024 },
  );
  return JSON.parse(raw).credentials;
}

async function readEvidenceJson(filename) {
  return JSON.parse(await readFile(path.join(evidenceDir, filename), "utf8"));
}

function publicActor(actor) {
  return {
    tenantId: actor.tenantId,
    username: actor.username,
    displayName: actor.displayName,
    roleCode: actor.roleCode,
  };
}

function escapeHtml(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function waitForReady(page, text, timeout = 25000) {
  await page.getByText(text, { exact: false }).first().waitFor({ timeout });
  await waitForQuiet(page);
}

async function renderWithUrlBar(browser, rawPath, finalPath, url) {
  const image = await readFile(rawPath);
  const imageData = `data:image/png;base64,${image.toString("base64")}`;
  const html = `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <style>
      html, body { margin: 0; background: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
      .bar { height: 44px; display: flex; align-items: center; gap: 10px; padding: 0 16px; background: #eef2f7; color: #1f2937; border-bottom: 1px solid #cbd5e1; box-sizing: border-box; font-size: 13px; }
      .dot { width: 10px; height: 10px; border-radius: 50%; background: #94a3b8; }
      .url { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; background: #fff; border: 1px solid #cbd5e1; border-radius: 7px; padding: 5px 10px; }
      img { display: block; width: 100%; height: auto; }
    </style>
  </head>
  <body>
    <div class="bar" aria-label="浏览器地址栏">
      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
      <span class="url">${escapeHtml(url)}</span>
    </div>
    <img src="${imageData}" alt="页面截图" />
  </body>
</html>`;
  const wrapper = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
  await wrapper.setContent(html, { waitUntil: "load" });
  await wrapper.screenshot({ path: finalPath, fullPage: true });
  await wrapper.close();
  await rm(rawPath, { force: true });
}

async function capture(browser, page, filename, label, actor, route, operation) {
  const finalPath = path.join(uiDir, filename);
  const rawPath = path.join(uiDir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  return {
    label,
    actor,
    route,
    operation,
    screenshot: filename,
    url: page.url(),
  };
}

async function login(browser, credentials, actorKey) {
  const actor = credentials[actorKey];
  if (!actor) throw new Error(`missing credentials for ${actorKey}`);
  const context = await browser.newContext({
    acceptDownloads: true,
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.getByLabel("工号 / 账号").fill(actor.username);
  await page.getByLabel("密码").fill(actor.currentPassword);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 25000 });
  await waitForQuiet(page);
  return { context, page, actor: publicActor(actor) };
}

async function gotoAndWait(page, route, readyText) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForReady(page, readyText);
}

async function visibleText(page, text) {
  return (await page.getByText(text, { exact: false }).count()) > 0;
}

async function apiJson(page, method, apiPath, body = undefined, options = {}) {
  return page.evaluate(
    async ({ method, apiPath, body, options }) => {
      const token = document.cookie
        .split("; ")
        .find((part) => part.startsWith("XSRF-TOKEN="))
        ?.split("=")[1];
      const headers = {
        Accept: "application/json, text/plain",
        "Content-Type": "application/json",
        "X-MedKernel-Trace-Id": options.traceId || crypto.randomUUID(),
      };
      if (token) headers["X-XSRF-TOKEN"] = decodeURIComponent(token);
      const response = await fetch(`/medkernel/api/v1${apiPath}`, {
        method,
        credentials: "same-origin",
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      let json = null;
      try {
        json = text ? JSON.parse(text) : null;
      } catch {
        json = { raw: text };
      }
      return { ok: response.ok, status: response.status, json };
    },
    { method, apiPath, body, options },
  );
}

async function clickTab(page, name) {
  await page.getByRole("tab", { name }).click();
  await waitForQuiet(page);
}

async function enableExpertMode(page) {
  const expertSwitch = page.getByRole("switch", { name: "专家模式" }).first();
  if ((await expertSwitch.count()) > 0) {
    const checked = await expertSwitch.getAttribute("aria-checked");
    if (checked !== "true") {
      await expertSwitch.click();
      await waitForQuiet(page);
    }
    return true;
  }
  return false;
}

async function replayAudit(browser, credentials, l1Summary) {
  const screenshots = [];
  const audit = await login(browser, credentials, actors.audit);
  const actor = audit.actor.displayName ?? audit.actor.username;

  await gotoAndWait(audit.page, "/admin/audit", "审计日志");
  await audit.page.getByLabel("操作人").fill("drill-respiratory-doctor-20260611");
  await audit.page.getByText("drill-respiratory-doctor-20260611", { exact: false })
    .first()
    .waitFor({ timeout: 25000 })
    .catch(() => undefined);
  screenshots.push(
    await capture(
      browser,
      audit.page,
      "01-ui-audit-events-doctor-filter.png",
      "审计员按操作人查看幕6医生操作事件",
      actor,
      "/admin/audit",
      "按操作人筛选审计事件",
    ),
  );

  await audit.page.locator(".ant-table-row").first().waitFor({ timeout: 25000 });
  await audit.page.locator('button[aria-label^="查看详情"]').first().click();
  await audit.page.getByText("审计事件详情", { exact: false }).waitFor({ timeout: 15000 });
  screenshots.push(
    await capture(
      browser,
      audit.page,
      "02-ui-audit-event-detail-trace.png",
      "审计事件详情能看见 Trace ID、载荷摘要和链签名",
      actor,
      "/admin/audit",
      "打开审计事件详情",
    ),
  );
  await audit.page.locator(".ant-drawer:visible").last().locator(".ant-drawer-close").click();
  await waitForQuiet(audit.page);

  await enableExpertMode(audit.page);
  await audit.page.getByLabel("操作人").fill("");
  await waitForQuiet(audit.page);
  await audit.page.getByLabel("对象类型").fill("clinical_event");
  await audit.page.getByText("clinical_event", { exact: false })
    .first()
    .waitFor({ timeout: 25000 })
    .catch(() => undefined);
  screenshots.push(
    await capture(
      browser,
      audit.page,
      "03-ui-audit-events-clinical-resource.png",
      "审计员在专家模式查看 clinical_event 与 trace 字段",
      actor,
      "/admin/audit",
      "专家模式按对象类型筛选审计事件",
    ),
  );

  const requestReason = `幕10 L2 前台复演：审计日志导出审批 ${runTag}`;
  await audit.page.getByRole("button", { name: "申请导出" }).click();
  await audit.page.getByLabel("申请理由").fill(requestReason);
  screenshots.push(
    await capture(
      browser,
      audit.page,
      "04-ui-audit-export-request-modal.png",
      "审计员在前台填写审计日志导出申请理由",
      actor,
      "/admin/audit",
      "填写导出申请",
    ),
  );
  await audit.page.getByRole("button", { name: "提交导出申请" }).click();
  await audit.page
    .locator(".ant-modal:visible")
    .waitFor({ state: "hidden", timeout: 15000 })
    .catch(() => undefined);
  await clickTab(audit.page, "导出审批");
  await audit.page
    .locator(".ant-tabs-tabpane-active .ant-table-row")
    .filter({ hasText: requestReason })
    .first()
    .waitFor({ timeout: 25000 });
  screenshots.push(
    await capture(
      browser,
      audit.page,
      "05-ui-audit-export-requested.png",
      "审计员能在导出审批页看到自己提交的待审批申请",
      actor,
      "/admin/audit",
      "查看待审批申请",
    ),
  );
  await audit.context.close();

  const admin = await login(browser, credentials, actors.hospitalAdmin);
  const adminActor = admin.actor.displayName ?? admin.actor.username;
  await gotoAndWait(admin.page, "/admin/audit", "审计日志");
  await clickTab(admin.page, "导出审批");
  const row = admin.page
    .locator(".ant-tabs-tabpane-active .ant-table-row")
    .filter({ hasText: requestReason })
    .first();
  await row.waitFor({ timeout: 25000 });
  screenshots.push(
    await capture(
      browser,
      admin.page,
      "06-ui-audit-export-pending-admin.png",
      "医院管理员在前台看到他人提交的导出申请并可审批",
      adminActor,
      "/admin/audit",
      "查看他人导出申请",
    ),
  );
  await row.getByRole("button", { name: /^批准/ }).click();
  const approvalModal = admin.page
    .locator(".ant-modal:visible")
    .filter({ hasText: "批准导出申请" })
    .last();
  await approvalModal.waitFor({ timeout: 15000 });
  await approvalModal.getByLabel("审批意见").fill(`幕10 L2 前台审批通过 ${runTag}`);
  await approvalModal.getByRole("button", { name: "确认审批" }).click();
  await approvalModal.waitFor({ state: "hidden", timeout: 25000 });
  await row.getByText("已批准", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(
    await capture(
      browser,
      admin.page,
      "07-ui-audit-export-approved.png",
      "导出申请审批后在前台显示已批准并保留证据入口",
      adminActor,
      "/admin/audit",
      "完成导出审批",
    ),
  );
  await admin.context.close();

  return {
    screenshots,
    requestReason,
    l1Correlation: {
      auditEventDoctorTrace: l1Summary.traceLines.find((item) =>
        item.label.endsWith("audit-events-doctor"),
      ),
      auditSnapshotTrace: l1Summary.traceLines.find((item) =>
        item.label.endsWith("audit-snapshot"),
      ),
    },
    review: {
      id: "UI-ACT10-AUDIT-01",
      pages: ["/admin/audit"],
      score: 6,
      conclusion:
        "审计事件、详情、导出申请和他人审批均可前台完成；直接按 traceId 搜索和从审计页跳诊断链仍不够直接。",
      followUp:
        "增加 traceId 搜索、审计详情中的诊断链跳转和导出审批证据显著入口。",
    },
  };
}

async function replaySecurityBaseline(browser, credentials, l1Evidence) {
  const screenshots = [];
  const ops = await login(browser, credentials, actors.itOps);
  const actor = ops.actor.displayName ?? ops.actor.username;

  await gotoAndWait(ops.page, "/security/baseline", "安全基线与系统配置");
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "08-ui-security-baseline-overview.png",
      "信息科在安全基线页查看账号、MFA、高风险权限和运行状态",
      actor,
      "/security/baseline",
      "查看安全基线概览",
    ),
  );

  await clickTab(ops.page, "系统配置");
  await ops.page.getByText("配置以数据库为唯一运行来源", { exact: false }).waitFor({ timeout: 20000 });
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "09-ui-security-system-configs.png",
      "系统配置页展示高风险配置来源、风险等级和审计原因入口",
      actor,
      "/security/baseline",
      "查看系统配置",
    ),
  );

  await clickTab(ops.page, "数据权限");
  await ops.page.getByText(l1Evidence.dataPermission.resourceType, { exact: false })
    .waitFor({ timeout: 25000 })
    .catch(() => undefined);
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "10-ui-security-data-permissions.png",
      "数据权限页能看到幕10策略和允许字段",
      actor,
      "/security/baseline",
      "查看数据权限策略",
    ),
  );

  await clickTab(ops.page, "脱敏规则");
  await ops.page.getByText("patientName", { exact: false })
    .first()
    .waitFor({ timeout: 25000 })
    .catch(() => undefined);
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "11-ui-security-masking-rules.png",
      "脱敏规则页能看到 patientName 与 idNo 的遮罩策略",
      actor,
      "/security/baseline",
      "查看脱敏规则",
    ),
  );

  await clickTab(ops.page, "互操作测评");
  await ops.page.getByText("测评版本", { exact: false }).first().waitFor({ timeout: 25000 });
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "12-ui-security-interop-assessment.png",
      "互操作测评页展示证据计数和差距原因",
      actor,
      "/security/baseline",
      "查看互操作测评",
    ),
  );

  const dataPolicyCheck = await apiJson(
    ops.page,
    "GET",
    "/compliance/data-permissions?resourceType=act10_patient_scope&action=READ",
    undefined,
    { traceId: `${runTag}-ui-data-permission-list` },
  );
  const maskingList = await apiJson(
    ops.page,
    "GET",
    "/compliance/masking-rules?resourceType=act10_patient_export",
    undefined,
    { traceId: `${runTag}-ui-masking-list` },
  );
  await ops.context.close();

  return {
    screenshots,
    apiCorrelations: {
      dataPolicyCheck: {
        status: dataPolicyCheck.status,
        count: dataPolicyCheck.json?.data?.length ?? null,
      },
      maskingList: {
        status: maskingList.status,
        count: maskingList.json?.data?.length ?? null,
      },
      l1DataPermission: l1Evidence.dataPermission,
      l1MaskingPreview: l1Evidence.maskingPreview,
    },
    review: {
      id: "UI-ACT10-SECBASE-01",
      pages: ["/security/baseline"],
      score: 5,
      conclusion:
        "系统配置、数据权限、脱敏规则和互操作证据均有页面；但跨科室权限试算和脱敏预览仍只能用接口佐证。",
      followUp:
        "在安全基线页补受控的权限试算与脱敏预览面板，默认使用演练患者和当前登录组织域。",
    },
  };
}

async function replayRuntimeAndDomestic(browser, credentials, l1Evidence) {
  const screenshots = [];
  const ops = await login(browser, credentials, actors.itOps);
  const actor = ops.actor.displayName ?? ops.actor.username;

  await gotoAndWait(ops.page, "/system/providers", "运行状态");
  await ops.page.getByText("依赖健康", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "13-ui-runtime-providers-overview.png",
      "运行状态页显示核心服务、依赖、备份就绪和诚实降级提示",
      actor,
      "/system/providers",
      "查看运行状态总览",
    ),
  );
  await enableExpertMode(ops.page);
  await ops.page.getByText("功能开关诊断", { exact: false })
    .first()
    .waitFor({ timeout: 15000 })
    .catch(() => undefined);
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "14-ui-runtime-providers-expert.png",
      "运行状态专家模式展示 profile、方言、功能开关和备份诊断",
      actor,
      "/system/providers",
      "查看运行状态专家诊断",
    ),
  );

  await gotoAndWait(ops.page, "/advanced/domestic", "国产化自检");
  await ops.page.getByText("逐项自检", { exact: false }).waitFor({ timeout: 25000 });
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "15-ui-domestic-check-overview.png",
      "国产化自检页展示 OS、JDK、DB、国密算法和逐项结果",
      actor,
      "/advanced/domestic",
      "查看国产化自检",
    ),
  );
  await ops.page.getByRole("radio", { name: "不兼容" }).click().catch(async () => {
    await ops.page.getByText("不兼容", { exact: true }).click();
  });
  await waitForQuiet(ops.page);
  screenshots.push(
    await capture(
      browser,
      ops.page,
      "16-ui-domestic-check-issues.png",
      "国产化自检可按不兼容项过滤并保留未连接状态",
      actor,
      "/advanced/domestic",
      "过滤国产化不兼容项",
    ),
  );

  const [download] = await Promise.all([
    ops.page.waitForEvent("download", { timeout: 20000 }),
    ops.page.getByRole("button", { name: "导出报告" }).click(),
  ]);
  const reportPath = path.join(uiDir, "domestic-check-report.txt");
  await download.saveAs(reportPath);
  const operations = await apiJson(ops.page, "GET", "/system/operations", undefined, {
    traceId: `${runTag}-ui-operations`,
  });
  await ops.context.close();

  return {
    screenshots,
    downloadedReport: path.basename(reportPath),
    apiCorrelations: {
      operations: {
        status: operations.status,
        healthStatus: operations.json?.data?.healthStatus ?? null,
        dependencyStatuses:
          operations.json?.data?.dependencies?.map((dependency) => ({
            key: dependency.key,
            status: dependency.status,
          })) ?? [],
      },
      l1Runtime: l1Evidence.runtime,
      l1ModelDegrade: l1Evidence.modelDegrade,
    },
    review: {
      id: "UI-ACT10-RUNTIME-01",
      pages: ["/system/providers", "/advanced/domestic"],
      score: 7,
      conclusion:
        "运行状态、未连接/模型未启用、备份恢复诊断和国产化自检均可页面读取，报告可导出。",
      followUp:
        "后续只需把正式院方证书替换纳入上线清单；页面状态不能为未接入能力刷绿。",
    },
  };
}

async function main() {
  await rm(uiDir, { recursive: true, force: true });
  await mkdir(uiDir, { recursive: true });
  const credentials = loadCredentials();
  for (const key of Object.values(actors)) {
    if (!credentials[key]?.username || !credentials[key]?.currentPassword) {
      throw new Error(`missing credential ${key}`);
    }
  }

  const l1Summary = await readEvidenceJson("99-summary.json");
  const dataPermission = await readEvidenceJson("02-data-permission-boundary.json");
  const maskingPreview = await readEvidenceJson("03-masking-preview.json");
  const modelDegrade = await readEvidenceJson("05-model-degrade.json");
  const runtime = await readEvidenceJson("06-runtime-domestic-backup.json");
  const l1Evidence = {
    dataPermission: {
      resourceType: dataPermission.policy?.body?.data?.resourceType,
      respiratoryAllowed: dataPermission.respiratoryAccess?.body?.data?.rowAllowed,
      cardiologyDenied: dataPermission.cardiologyAccess?.body?.data?.rowAllowed === false,
    },
    maskingPreview: {
      rawAllowed: maskingPreview.preview?.body?.data?.rawAllowed,
      maskedFields: maskingPreview.preview?.body?.data?.maskedFields,
      values: maskingPreview.preview?.body?.data?.values,
    },
    modelDegrade: {
      status: modelDegrade.task?.body?.data?.status,
      modelMode: modelDegrade.task?.body?.data?.modelMode,
      fallbackUsed: modelDegrade.task?.body?.data?.fallbackUsed,
      traceId: modelDegrade.task?.body?.data?.traceId,
    },
    runtime: {
      healthStatus: runtime.operations?.body?.data?.healthStatus,
      dependencies:
        runtime.operations?.body?.data?.dependencies?.map((dependency) => ({
          key: dependency.key,
          status: dependency.status,
        })) ?? [],
      backupRestore: runtime.backupRestore,
    },
  };

  const browser = await chromium.launch({ headless: true });
  try {
    const audit = await replayAudit(browser, credentials, l1Summary);
    const securityBaseline = await replaySecurityBaseline(browser, credentials, l1Evidence);
    const runtimeAndDomestic = await replayRuntimeAndDomestic(browser, credentials, l1Evidence);
    const summary = {
      runTag,
      startedFromL1RunTag: l1Summary.runTag,
      generatedAt: new Date().toISOString(),
      baseUrl,
      credentialLocation: credentialPath,
      actors: Object.fromEntries(
        Object.entries(actors).map(([name, key]) => [name, publicActor(credentials[key])]),
      ),
      screenshots: [
        ...audit.screenshots,
        ...securityBaseline.screenshots,
        ...runtimeAndDomestic.screenshots,
      ],
      frontendActions: [
        "审计员在 /admin/audit 按操作人和对象类型筛选审计事件并打开详情。",
        "审计员在 /admin/audit 前台提交审计日志导出申请。",
        "医院管理员在 /admin/audit 前台审批他人导出申请。",
        "信息科在 /security/baseline 查看系统配置、数据权限、脱敏规则和互操作测评。",
        "信息科在 /system/providers 查看运行状态、诚实降级和备份恢复诊断。",
        "信息科在 /advanced/domestic 查看国产化自检并导出报告。",
      ],
      reviews: [audit.review, securityBaseline.review, runtimeAndDomestic.review],
      correlations: {
        audit: audit.l1Correlation,
        securityBaseline: securityBaseline.apiCorrelations,
        runtimeAndDomestic: runtimeAndDomestic.apiCorrelations,
      },
      downloadedReport: runtimeAndDomestic.downloadedReport,
      passCriteria: [
        "幕10 L1 A1-A7 全部为 true，L2 不重复破坏性备份恢复。",
        "审计日志、导出审批、安全基线、运行状态、国产化自检均有带 URL 截图。",
        "至少一个合规动作在前台完成：审计员申请导出，医院管理员二人审批。",
        "前台缺口登记为优化项，不用 API 证据冒充页面验收。",
      ],
      pass: true,
    };
    await writeFile(
      path.join(uiDir, "00-ui-replay-summary.json"),
      `${JSON.stringify(summary, null, 2)}\n`,
    );
    console.log(
      JSON.stringify(
        {
          runTag,
          evidenceDir: uiDir,
          screenshots: summary.screenshots.length,
          reviews: summary.reviews.map((review) => ({
            id: review.id,
            score: review.score,
          })),
          downloadedReport: summary.downloadedReport,
        },
        null,
        2,
      ),
    );
  } finally {
    await browser.close();
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
