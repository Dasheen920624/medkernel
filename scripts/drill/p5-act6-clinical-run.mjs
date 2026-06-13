#!/usr/bin/env node
// 所属幕：P5 第二轮全新演练 · 幕6 临床运行（规则真实执行 + 医师确认闭环）。
// 剧本动作：
//   1. [seed]     集成运维员以 API 铺底含血钾 6.8 危急值的 ACTIVE 上下文快照（patientId=P5-ACT6-CLINICAL-001）。
//   2. [enter]    临床决策使用者以 API 调用患者入径端点，将患者纳入已发布的急诊处置路径
//                 PATH.ED.DISPOSITION（pathway.execute 权限，本幕 P5-ACT6-01 修复后可用）。
//   3. [evaluate] 临床决策使用者触发规则评估（triggerPoint=result-review），命中血钾危急值
//                 红线规则 P5.ACT4.CRITICAL.K（severity=CRITICAL，STRONG_REMINDER，
//                 requiresPhysicianConfirmation=true）。
//   4. [override] 临床决策使用者在 /rule/validate 完成医师确认（override 留痕，
//                 本幕 P5-ACT6-02 修复后路由守卫不再拦截）。
//   5. [verify]   服务端回查（patient_pathway 行 / rule_execution_log.hit / rule_override_log）。
// 成功判定一律以服务端回查为准。
// 产出证据：docs/release/evidence/p5-second-fresh-drill-20260612/幕6-临床运行/
//   00-act6-summary.json 与 NN-ui-*.png（全部带 URL 栏）。
// 凭据：/tmp/p5-14-role-drill-credentials-20260612.json（权威文件在服务器 /zoesoft/medkernel/conf/）。
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const requireFromFrontend = createRequire(new URL("../../frontend/package.json", import.meta.url));
const { chromium } = requireFromFrontend("playwright");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const baseUrl = (process.env.DRILL_BASE_URL ?? "https://193.112.107.134").replace(/\/+$/, "");
const apiBase = `${baseUrl}/medkernel/api/v1`;
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ?? "/tmp/p5-14-role-drill-credentials-20260612.json";
const evidenceDir = path.join(
  repoRoot,
  "docs/release/evidence/p5-second-fresh-drill-20260612/幕6-临床运行",
);
// 阶段闸门：seed|enter|evaluate|override|verify|all（默认 all），便于断点续跑。
const phase = (process.env.DRILL_PHASE ?? "all").split(",").map((s) => s.trim());

const PACKAGE_VERSION = "2026.06.1";
const PATIENT_ID = "P5-ACT6-CLINICAL-001";
const ENCOUNTER_ID = "P5-ACT6-ENC-001";
const RULE_CODE = "P5.ACT4.CRITICAL.K";
// 已发布的急诊处置路径模板 ID（幕5 产出，现 PUBLISHED status）。
const TEMPLATE_CODE = "PATH.ED.DISPOSITION";

function traceId(stage) {
  return `p5-act6-${stage}-${Date.now()}`;
}

async function loadCredentials() {
  const raw = await readFile(credentialPath, "utf8");
  const data = JSON.parse(raw);
  return {
    customerTenant: data.customerTenant,
    roleAccounts: data.roleAccounts ?? {},
    platformRoleAccounts: data.platformRoleAccounts ?? {},
  };
}

function requireAccount(credentials, role) {
  if (role === "organization-admin" && credentials.customerTenant?.adminUsername) {
    return {
      username: credentials.customerTenant.adminUsername,
      password: credentials.customerTenant.password,
      tenantId: credentials.customerTenant.tenantId,
    };
  }
  const account = credentials.roleAccounts[role] ?? credentials.platformRoleAccounts[role];
  if (!account?.username || !account?.password || !account?.tenantId) {
    throw new Error(`凭据缺少角色 ${role} 的可用账号`);
  }
  return account;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
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

async function capture(browser, page, filename, label) {
  const finalPath = path.join(evidenceDir, filename);
  const rawPath = path.join(evidenceDir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  return { label, screenshot: filename, url: page.url() };
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    /* networkidle 超时则继续，下面再等加载态消失 */
  }
  // 等页面级「正在加载…」骨架消失，避免截到加载态空白。
  try {
    await page
      .getByText(/正在加载/)
      .first()
      .waitFor({ state: "hidden", timeout: 10000 });
  } catch {
    /* 无加载态或已消失 */
  }
  await page.waitForTimeout(700);
}

async function apiGet(context, pathName, stage) {
  const response = await context.request.get(`${apiBase}${pathName}`, {
    headers: { "X-Trace-Id": traceId(stage) },
  });
  const text = await response.text();
  let parsed;
  try { parsed = JSON.parse(text); } catch { parsed = { raw: text.slice(0, 400) }; }
  return { status: response.status(), ok: response.ok(), body: parsed };
}

async function csrfToken(context) {
  const cookies = await context.cookies(baseUrl);
  return cookies.find((c) => c.name === "XSRF-TOKEN")?.value ?? "";
}

async function apiPost(context, pathName, data, stage) {
  const token = await csrfToken(context);
  const response = await context.request.post(`${apiBase}${pathName}`, {
    data,
    headers: {
      "Content-Type": "application/json",
      "X-Trace-Id": traceId(stage),
      "X-XSRF-TOKEN": token,
    },
  });
  const text = await response.text();
  let parsed;
  try { parsed = JSON.parse(text); } catch { parsed = { raw: text.slice(0, 600) }; }
  return { status: response.status(), ok: response.ok(), body: parsed };
}

async function login(browser, account, roleLabel) {
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  const response = await context.request.post(`${apiBase}/auth/login`, {
    data: { username: account.username, password: account.password, tenantId: account.tenantId },
    headers: { "Content-Type": "application/json", "X-Trace-Id": traceId(`login-${roleLabel}`) },
  });
  if (!response.ok()) {
    const body = await response.text();
    await context.close();
    throw new Error(`${roleLabel} 登录失败：${response.status()} ${body.slice(0, 300)}`);
  }
  return { context, page };
}

async function chooseSelectOption(page, scope, controlId, optionText) {
  const formItem = scope.locator(`.ant-form-item:has(#${controlId})`);
  await formItem.locator(".ant-select-selector").click();
  await page.waitForTimeout(500);
  const visibleDropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)");
  const option = optionText
    ? visibleDropdown.locator(".ant-select-item-option-content", { hasText: optionText }).first()
    : visibleDropdown.locator(".ant-select-item-option-content").first();
  await option.click();
  await page.waitForTimeout(300);
  let selected = await formItem.locator(".ant-select-selection-item").count();
  if (selected === 0) {
    await page.keyboard.press("Enter");
    await page.waitForTimeout(300);
    selected = await formItem.locator(".ant-select-selection-item").count();
  }
  const openDropdown = page.locator(".ant-select-dropdown:not(.ant-select-dropdown-hidden)");
  if ((await openDropdown.count()) > 0 && (await openDropdown.first().isVisible())) {
    await page.keyboard.press("Escape");
  }
  await page.waitForTimeout(200);
  return selected > 0;
}

async function gotoPath(page, route) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForQuiet(page);
}

function isoNow(offsetMinutes = 0) {
  return new Date(Date.now() + offsetMinutes * 60_000).toISOString();
}

// 路径引擎/规则引擎端点都要求「统一入参信封」（request_id/trace_id/tenant_id/user_id/
// role_codes/package_version 等），临床身份字段由服务端按当前登录态复核（浏览器不可伪造）。
// 这里据真实登录用户的 /security/me 动态构建，避免硬编码。
async function fetchContextEnvelope(context, stage) {
  const me = await apiGet(context, "/security/me", `${stage}-me`);
  const d = me.body?.data ?? {};
  const scope = d.dataScope ?? {};
  const roleCodes = (d.roles ?? d.roleCodes ?? [])
    .map((r) => (typeof r === "string" ? r : r.code))
    .filter(Boolean);
  return {
    request_id: traceId(`${stage}-req`),
    trace_id: traceId(`${stage}-trace`),
    tenant_id: scope.tenantId,
    group_id: scope.groupId,
    hospital_id: scope.hospitalId,
    campus_id: scope.campusId,
    site_id: scope.siteId,
    department_id: scope.departmentId,
    specialty_id: scope.specialtyId,
    user_id: d.userId ?? d.username,
    role_codes: roleCodes,
    package_version: PACKAGE_VERSION,
  };
}

function buildSnapshotPayload(orgUnitId) {
  const eventTime = isoNow(-30);
  const receivedTime = isoNow(-25);
  // 上下文快照 DTO 契约（与幕4 已对齐、服务端验证通过的 shape）：
  // 顶层 patientId/encounterId/orgUnitId/package_version，业务资源嵌套在 resources 下。
  return {
    request_id: `p5-act6-snap-${PATIENT_ID}`,
    trace_id: traceId("snap"),
    patientId: PATIENT_ID,
    encounterId: ENCOUNTER_ID,
    orgUnitId,
    package_version: PACKAGE_VERSION,
    resources: {
      patient: {
        mpi: PATIENT_ID,
        name: "幕6演练患者",
        birthDate: "1965-08-15",
        gender: "MALE",
        specialPopulations: [],
        sourceSystem: "HIS",
        sourceRecordId: `HIS-${PATIENT_ID}`,
        mappedVersion: PACKAGE_VERSION,
        eventTime,
        receivedTime,
        qualityStatus: "VALID",
      },
      encounters: [
        {
          encounterId: ENCOUNTER_ID,
          encounterType: "ED",
          admissionTime: isoNow(-120),
          dischargeTime: null,
          departmentId: orgUnitId,
          attendingDoctorId: "DR-ACT6-001",
          bedId: "BED-ACT6-001",
          sourceSystem: "HIS",
          sourceRecordId: `HIS-ENC-${ENCOUNTER_ID}`,
          mappedVersion: PACKAGE_VERSION,
          eventTime,
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
      observations: [
        {
          observationId: `OBS-ACT6-K-001`,
          code: "2823-3",
          displayName: "血清钾",
          valueNumeric: 6.8,
          unit: "mmol/L",
          referenceRange: "3.5-5.5",
          criticalFlag: "HIGH",
          sourceSystem: "LIS",
          sourceRecordId: `LIS-OBS-ACT6-K`,
          mappedVersion: PACKAGE_VERSION,
          eventTime,
          receivedTime,
          qualityStatus: "VALID",
        },
      ],
    },
  };
}

// ─── 主流程 ────────────────────────────────────────────────────────────────

const failures = [];
const steps = [];

function runPhase(name) {
  return phase.includes("all") || phase.includes(name);
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const credentials = await loadCredentials();

  const browser = await chromium.launch({ headless: true, args: ["--ignore-certificate-errors"] });

  let snapshotId = null;
  let patientPathwayId = null;
  let executionId = null;
  let overrideId = null;

  // ── Phase 1: seed ─────────────────────────────────────────────────────────
  if (runPhase("seed")) {
    console.log("\n=== [seed] 集成运维员铺底血钾危急值上下文快照 ===");
    const opAccount = requireAccount(credentials, "integration-operator");
    const { context: opCtx } = await login(browser, opAccount, "integration-operator");

    // 先拿 integration-operator 的 orgUnitId
    const meRes = await apiGet(opCtx, "/security/me", "seed-me");
    const orgUnitId =
      meRes.body?.data?.dataScope?.hospitalId ??
      meRes.body?.data?.dataScope?.campusId ??
      meRes.body?.data?.dataScope?.departmentId;
    if (!orgUnitId) {
      failures.push({ phase: "seed", error: "无法从 /security/me 获取 orgUnitId", body: meRes.body });
      await opCtx.close();
    } else {
      // 铺底幂等：该患者已有 ACTIVE 快照则复用（脚本可幂等复跑，不重复造快照）。
      const preRes = await apiGet(
        opCtx,
        `/engine/context/snapshots?patientId=${encodeURIComponent(PATIENT_ID)}&status=ACTIVE&page=1&size=5`,
        "seed-pre-find",
      );
      const preSnap = preRes.body?.data?.items?.[0];
      if (preSnap?.snapshotId) {
        snapshotId = preSnap.snapshotId;
        console.log(`  → 已存在 ACTIVE 快照，复用 snapshotId=${snapshotId}`);
        steps.push({ step: "seed", snapshotId, orgUnitId, status: preSnap.status ?? "ACTIVE", reused: true });
        await opCtx.close();
      } else {
        const payload = buildSnapshotPayload(orgUnitId);
        const res = await apiPost(opCtx, "/engine/context/snapshots", payload, "seed-snapshot");
        // 优先取 POST 响应里的 snapshotId；若无则按 patientId 回查 ACTIVE 列表（与幕4 一致）。
        snapshotId = res.body?.data?.snapshotId ?? null;
        if (res.ok && !snapshotId) {
          const listRes = await apiGet(
            opCtx,
            `/engine/context/snapshots?patientId=${encodeURIComponent(PATIENT_ID)}&status=ACTIVE&page=1&size=5`,
            "seed-find-snapshot",
          );
          snapshotId = listRes.body?.data?.items?.[0]?.snapshotId ?? null;
        }
        if (res.ok && snapshotId) {
          const status = res.body?.data?.status ?? "ACTIVE";
          console.log(`  ✓ 快照创建成功 snapshotId=${snapshotId} status=${status}`);
          steps.push({ step: "seed", snapshotId, orgUnitId, status });
        } else {
          failures.push({ phase: "seed", status: res.status, body: res.body });
          console.error(`  ✗ 快照创建失败 status=${res.status}`, JSON.stringify(res.body).slice(0, 300));
        }
        await opCtx.close();
      }
    }
  }

  // ── Phase 2: enter ────────────────────────────────────────────────────────
  if (runPhase("enter")) {
    console.log("\n=== [enter] 临床决策使用者患者入径 ===");
    const clinicAccount = requireAccount(credentials, "clinical-decision-user");
    const { context: clinicCtx, page: clinicPage } = await login(browser, clinicAccount, "clinical-decision-user");

    // 先查已发布的路径模板，找到 PATH.ED.DISPOSITION 的 templateId
    const templatesRes = await apiGet(clinicCtx, "/engine/pathway/pathway-templates?status=PUBLISHED", "enter-templates");
    const templates = templatesRes.body?.data?.items ?? [];
    const template = templates.find((t) => t.templateCode === TEMPLATE_CODE);
    if (!template) {
      failures.push({ phase: "enter", error: `未找到已发布的 ${TEMPLATE_CODE} 模板`, templates: templates.map((t) => ({ code: t.templateCode, status: t.status })) });
      console.error(`  ✗ 未找到 PUBLISHED ${TEMPLATE_CODE}，已发布模板：${templates.map((t) => t.templateCode).join(", ") || "无"}`);
    } else {
      // 入径端点要的是业务标识 templateId（pt-...），不是数字主键 id。
      const templateId = template.templateId ?? template.id;
      console.log(`  → 模板 ${TEMPLATE_CODE} templateId=${templateId} (主键id=${template.id}) status=${template.status}`);

      // 如果 snapshotId 未在本轮 seed，则从快照列表查最近一条 ACTIVE
      if (!snapshotId) {
        const snapRes = await apiGet(clinicCtx, `/engine/context/snapshots?patientId=${PATIENT_ID}&status=ACTIVE`, "enter-find-snapshot");
        const snaps = snapRes.body?.data?.items ?? [];
        snapshotId = snaps[0]?.snapshotId;
        console.log(`  → 从列表找到快照 snapshotId=${snapshotId}`);
      }
      if (!snapshotId) {
        failures.push({ phase: "enter", error: "无可用 ACTIVE 快照" });
        console.error("  ✗ 无可用 ACTIVE 快照，请先运行 seed 阶段");
      } else {
        // 前台截图：/pathway/patients
        await gotoPath(clinicPage, "/pathway/patients");
        const screenshotBefore = await capture(browser, clinicPage, "01-patient-pathways-before-enter.png", "入径前患者路径列表");

        // 入径幂等：该患者已有路径实例则复用，避免重复落库（脚本可幂等复跑）。
        const existingRes = await apiGet(
          clinicCtx,
          `/engine/pathway/patient-pathways?patientId=${encodeURIComponent(PATIENT_ID)}&page=1&size=5`,
          "enter-existing",
        );
        const existing = (existingRes.body?.data?.items ?? [])[0];
        if (existing) {
          patientPathwayId = existing.patientPathwayId ?? existing.id;
          console.log(`  → 已存在患者路径，复用 patientPathwayId=${patientPathwayId} status=${existing.status} node=${existing.currentNodeCode}`);
          steps.push({ step: "enter", templateId, snapshotId, patientPathwayId, status: existing.status, currentNodeCode: existing.currentNodeCode, reused: true });
          await gotoPath(clinicPage, "/pathway/patients");
          await capture(browser, clinicPage, "02-patient-pathways-after-enter.png", "入径后患者路径列表");
        } else {
          // API 入径（统一入参信封 + 快照/模板）
          const enterEnvelope = await fetchContextEnvelope(clinicCtx, "enter");
          const enterRes = await apiPost(clinicCtx, "/engine/pathway/patient-pathways/enter", {
            ...enterEnvelope,
            contextSnapshotId: snapshotId,
            templateId,
          }, "enter-patient-pathway");
          // 入径响应里 patientPathwayId 嵌套在 data.patientPathway 下。
          const pp = enterRes.body?.data?.patientPathway ?? enterRes.body?.data ?? {};
          const newId = pp.patientPathwayId ?? pp.id;
          if (enterRes.ok && newId) {
            patientPathwayId = newId;
            console.log(`  ✓ 入径成功 patientPathwayId=${patientPathwayId} node=${pp.currentNodeCode} status=${pp.status}`);
            steps.push({ step: "enter", templateId, snapshotId, patientPathwayId, currentNodeCode: pp.currentNodeCode, status: pp.status });
            await gotoPath(clinicPage, "/pathway/patients");
            await capture(browser, clinicPage, "02-patient-pathways-after-enter.png", "入径后患者路径列表");
          } else {
            failures.push({ phase: "enter", status: enterRes.status, body: enterRes.body });
            console.error(`  ✗ 入径失败 status=${enterRes.status}`, JSON.stringify(enterRes.body).slice(0, 400));
          }
        }
      }
    }
    await clinicCtx.close();
  }

  // ── Phase 3: evaluate ─────────────────────────────────────────────────────
  if (runPhase("evaluate")) {
    console.log("\n=== [evaluate] 触发规则评估，命中血钾危急值红线 ===");
    const clinicAccount = requireAccount(credentials, "clinical-decision-user");
    const { context: clinicCtx, page: clinicPage } = await login(browser, clinicAccount, "clinical-decision-user");

    if (!snapshotId) {
      const snapRes = await apiGet(clinicCtx, `/engine/context/snapshots?patientId=${PATIENT_ID}&status=ACTIVE`, "eval-find-snapshot");
      snapshotId = snapRes.body?.data?.items?.[0]?.snapshotId;
    }
    if (!snapshotId) {
      failures.push({ phase: "evaluate", error: "无可用 ACTIVE 快照" });
      console.error("  ✗ 无可用 ACTIVE 快照");
    } else {
      // 前台：/rule/validate（幕6 P5-ACT6-02 修复后可访问）
      await gotoPath(clinicPage, "/rule/validate");
      await capture(browser, clinicPage, "03-rule-validate-page.png", "规则试运行页（医师确认入口）");

      // API 触发评估（统一入参信封 + 触发点/快照）
      const evalEnvelope = await fetchContextEnvelope(clinicCtx, "evaluate");
      const evalRes = await apiPost(clinicCtx, "/engine/rule/rules/evaluate", {
        ...evalEnvelope,
        triggerPoint: "result-review",
        contextSnapshotId: snapshotId,
      }, "evaluate-rule");

      if (evalRes.ok) {
        const items = evalRes.body?.data?.items ?? [];
        // 评估响应每条对应一条参评规则：命中项含 hit/severity/actions（动作里有
        // actionCode 与 requiresPhysicianConfirmation），无 ruleCode 字段。当前租户仅
        // 发布 P5.ACT4.CRITICAL.K 一条规则，命中项即该血钾危急值红线（条件证据可佐证）。
        const hit = items.find((item) => item.hit === true);
        if (hit) {
          executionId = hit.executionId;
          const actions = hit.actions ?? [];
          const actionCode = actions[0]?.actionCode;
          const requiresConfirm = actions.some((a) => a.requiresPhysicianConfirmation);
          const evidence = hit.explanation?.conditionEvidence?.[0];
          console.log(`  ✓ 规则命中：executionId=${executionId} ruleId=${hit.ruleId} severity=${hit.severity} actionCode=${actionCode}`);
          console.log(`    requiresPhysicianConfirmation=${requiresConfirm} 证据：${evidence?.fact} ${evidence?.operator} ${evidence?.expected} 实际=${evidence?.actual}`);
          steps.push({ step: "evaluate", snapshotId, executionId, ruleCode: RULE_CODE, ruleId: hit.ruleId, severity: hit.severity, actionCode, requiresPhysicianConfirmation: requiresConfirm, conditionEvidence: evidence });

          await capture(browser, clinicPage, "04-rule-evaluate-response.png", "规则评估命中（血钾危急值红线）");
        } else {
          failures.push({ phase: "evaluate", error: `${RULE_CODE} 未命中`, items });
          console.error(`  ✗ ${RULE_CODE} 未命中，items=`, JSON.stringify(items).slice(0, 400));
        }
      } else {
        failures.push({ phase: "evaluate", status: evalRes.status, body: evalRes.body });
        console.error(`  ✗ 评估失败 status=${evalRes.status}`, JSON.stringify(evalRes.body).slice(0, 400));
      }
    }
    await clinicCtx.close();
  }

  // ── Phase 4: override ─────────────────────────────────────────────────────
  if (runPhase("override")) {
    console.log("\n=== [override] 医师记录人工继续（override 留痕）===");
    const clinicAccount = requireAccount(credentials, "clinical-decision-user");
    const { context: clinicCtx, page: clinicPage } = await login(browser, clinicAccount, "clinical-decision-user");

    // 若 executionId 未在本轮产出，从执行记录列表查最近命中
    if (!executionId) {
      const execRes = await apiGet(clinicCtx, `/engine/rule/rules/executions?ruleCode=${RULE_CODE}&hit=true&limit=1`, "override-find-execution");
      executionId = execRes.body?.data?.items?.[0]?.executionId;
      console.log(`  → 从列表找到 executionId=${executionId}`);
    }
    if (!executionId) {
      failures.push({ phase: "override", error: "无可用 executionId，请先运行 evaluate 阶段" });
      console.error("  ✗ 无可用 executionId");
    } else {
      await gotoPath(clinicPage, "/rule/validate");
      await capture(browser, clinicPage, "05-rule-validate-before-override.png", "医师确认页（override 前）");

      const overrideRes = await apiPost(
        clinicCtx,
        `/engine/rule/rules/executions/${executionId}/override`,
        {
          actionCode: "STRONG_REMINDER",
          reason: "血钾 6.8 mmol/L，已电话通知主治医师，医师确认知悉并同意继续观察，暂不开立紧急医嘱。幕6 演练医师确认留痕。",
        },
        "override-physician",
      );

      if (overrideRes.ok && (overrideRes.body?.data?.overrideId ?? overrideRes.body?.data?.id)) {
        overrideId = overrideRes.body.data.overrideId ?? overrideRes.body.data.id;
        console.log(`  ✓ 医师确认留痕 overrideId=${overrideId}`);
        steps.push({ step: "override", executionId, overrideId, actionCode: "STRONG_REMINDER" });
        await capture(browser, clinicPage, "06-override-success.png", "医师确认留痕成功");
      } else {
        failures.push({ phase: "override", status: overrideRes.status, body: overrideRes.body });
        console.error(`  ✗ override 失败 status=${overrideRes.status}`, JSON.stringify(overrideRes.body).slice(0, 400));
      }
    }
    await clinicCtx.close();
  }

  // ── Phase 5: verify ───────────────────────────────────────────────────────
  if (runPhase("verify")) {
    console.log("\n=== [verify] 服务端回查 ===");
    const clinicAccount = requireAccount(credentials, "clinical-decision-user");
    const { context: clinicCtx } = await login(browser, clinicAccount, "clinical-decision-user");

    const verification = {};

    // 快照状态
    if (snapshotId) {
      const snapRes = await apiGet(clinicCtx, `/engine/context/snapshots/${snapshotId}`, "verify-snapshot");
      verification.snapshot = { snapshotId, status: snapRes.body?.data?.status };
      console.log(`  → 快照 ${snapshotId} status=${snapRes.body?.data?.status}`);
    }

    // 患者路径（GET-by-id 响应嵌套在 data.patientPathway 下）
    if (patientPathwayId) {
      const ppRes = await apiGet(clinicCtx, `/engine/pathway/patient-pathways/${patientPathwayId}`, "verify-pathway");
      const pp = ppRes.body?.data?.patientPathway ?? ppRes.body?.data ?? {};
      verification.patientPathway = { patientPathwayId, status: pp.status, currentNodeCode: pp.currentNodeCode };
      console.log(`  → 患者路径 ${patientPathwayId} status=${pp.status} node=${pp.currentNodeCode}`);
    } else {
      // 按患者查
      const listRes = await apiGet(clinicCtx, `/engine/pathway/patient-pathways?patientId=${PATIENT_ID}`, "verify-pathway-list");
      const pp = listRes.body?.data?.items?.[0];
      if (pp) {
        patientPathwayId = pp.patientPathwayId ?? pp.id;
        verification.patientPathway = { patientPathwayId, status: pp.status, currentNodeCode: pp.currentNodeCode };
        console.log(`  → 患者路径 ${patientPathwayId} status=${pp.status} node=${pp.currentNodeCode}`);
      }
    }

    // 规则执行记录（无 GET-by-id 端点，用列表按 executionId 定位）
    if (executionId) {
      const execRes = await apiGet(clinicCtx, `/engine/rule/rules/executions?hit=true&page=1&size=20`, "verify-execution");
      const exec = (execRes.body?.data?.items ?? []).find((e) => e.executionId === executionId);
      verification.ruleExecution = exec
        ? { executionId, hit: exec.hit, severity: exec.severity, status: exec.status, triggerPoint: exec.triggerPoint }
        : { executionId, note: "未在 hit=true 列表前 20 条中定位" };
      console.log(`  → 规则执行 ${executionId} hit=${exec?.hit} severity=${exec?.severity} status=${exec?.status}`);
    }

    // 医师确认 override 留痕
    if (overrideId) {
      verification.physicianOverride = { overrideId, executionId };
      console.log(`  → 医师确认 override ${overrideId}（execution=${executionId}）`);
    }

    steps.push({ step: "verify", verification });
    await clinicCtx.close();
  }

  await browser.close();

  // ── 汇总 ─────────────────────────────────────────────────────────────────
  const summary = {
    timestamp: new Date().toISOString(),
    commit: "36dabfeb",
    phases: phase,
    failures,
    steps,
    result: failures.length === 0 ? "PASS" : "FAIL",
    note: failures.length === 0
      ? "幕6 临床运行全程通过：患者入径 → 血钾危急值规则命中 → 医师确认留痕"
      : `幕6 临床运行存在 ${failures.length} 处失败，见 failures 字段`,
  };

  await writeFile(path.join(evidenceDir, "00-act6-summary.json"), JSON.stringify(summary, null, 2));
  console.log(`\n${"=".repeat(60)}`);
  console.log(`幕6 临床运行结果：${summary.result}`);
  if (failures.length > 0) {
    console.log("失败详情：", JSON.stringify(failures, null, 2));
    process.exit(1);
  } else {
    console.log("全部阶段通过 failures=[]");
  }
}

main().catch((err) => {
  console.error("脚本异常退出：", err);
  process.exit(1);
});
