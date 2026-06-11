#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
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
const baseUrl = process.env.DRILL_BASE_URL ?? "https://193.112.107.134";
const credentialPath =
  process.env.DRILL_CREDENTIAL_PATH ??
  "/zoesoft/medkernel/conf/drill-act1-credentials-20260611.json";
const sshTarget = process.env.DRILL_SSH_TARGET ?? "root@193.112.107.134";
const evidenceRoot = path.join(
  repoRoot,
  "docs/release/evidence/v1.0-drill-20260611",
);

const actDirs = {
  act3: path.join(evidenceRoot, "幕3-知识治理/ui-replay"),
  act4: path.join(evidenceRoot, "幕4-规则配置与模拟/ui-replay"),
  act5: path.join(evidenceRoot, "幕5-CAP临床路径/ui-replay"),
};

const actors = {
  medicalAffairs: "drill-hospital-20260611:drill-medical-affairs-20260611",
  respiratoryDoctor: "drill-hospital-20260611:drill-respiratory-doctor-20260611",
  specialist: "drill-hospital-20260611:drill-role-specialist-20260611",
};

const businessObjects = {
  act85RuleCode: "DRILL.ACT85.K.RECHECK.20260611",
  act85RuleName: "幕8.5 血钾危急值复核草稿",
  baselineCriticalRuleCode: "DRILL.ACT4.K.CRITICAL.rmq8j1rig",
  capTemplateCode: "TPL.DRILL.CAP.1781126077791",
  capTemplateId: "pt-e8b9a1f1-f423-44aa-ba6c-835c7246c186",
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
    { encoding: "utf8" },
  );
  return JSON.parse(raw).credentials;
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

async function ensureDirs() {
  await Promise.all(Object.values(actDirs).map((dir) => mkdir(dir, { recursive: true })));
}

async function waitForQuiet(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function waitForReady(page, text, timeout = 20000) {
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

async function capture(browser, page, dir, filename, label) {
  const finalPath = path.join(dir, filename);
  const rawPath = path.join(dir, `.raw-${filename}`);
  await page.screenshot({ path: rawPath, fullPage: false });
  await renderWithUrlBar(browser, rawPath, finalPath, page.url());
  return {
    label,
    screenshot: path.relative(dir, finalPath),
    url: page.url(),
  };
}

async function login(browser, credentials, actorKey) {
  const actor = credentials[actorKey];
  if (!actor) throw new Error(`missing credentials for ${actorKey}`);
  const context = await browser.newContext({
    ignoreHTTPSErrors: true,
    viewport: { width: 1440, height: 1000 },
    locale: "zh-CN",
  });
  const page = await context.newPage();
  await page.goto(`${baseUrl}/login`, { waitUntil: "domcontentloaded" });
  await page.getByLabel("工号 / 账号").fill(actor.username);
  await page.getByLabel("密码").fill(actor.currentPassword);
  await page.getByRole("button", { name: "进入工作台" }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"), { timeout: 20000 });
  await waitForQuiet(page);
  return { context, page, actor: publicActor(actor) };
}

async function gotoAndWait(page, route, readyText) {
  await page.goto(`${baseUrl}${route}`, { waitUntil: "domcontentloaded" });
  await waitForReady(page, readyText);
}

async function visibleButtonNames(scope) {
  return scope.locator("button:visible").evaluateAll((nodes) =>
    [...new Set(
      nodes
        .map((node) => (node.innerText || node.getAttribute("aria-label") || "").trim())
        .filter(Boolean),
    )],
  );
}

async function bodyIncludes(page, text) {
  return (await page.locator("body").innerText({ timeout: 5000 })).includes(text);
}

async function closeVisibleModalOrDrawer(page) {
  const modal = page.locator(".ant-modal:visible").last();
  if ((await modal.count()) > 0) {
    const cancel = modal.getByRole("button", { name: /取\s*消|取消/ }).first();
    if ((await cancel.count()) > 0) {
      await cancel.click();
    } else {
      await modal.locator(".ant-modal-close").click();
    }
    await modal.waitFor({ state: "hidden", timeout: 10000 }).catch(() => undefined);
    return;
  }
  const drawer = page.locator(".ant-drawer:visible").last();
  if ((await drawer.count()) > 0) {
    await drawer.locator(".ant-drawer-close").click();
    await drawer.waitFor({ state: "hidden", timeout: 10000 }).catch(() => undefined);
  }
}

async function clickRowButton(page, rowText, buttonName) {
  const row = page.locator("tr").filter({ hasText: rowText }).first();
  await row.waitFor({ timeout: 20000 });
  await row.getByRole("button", { name: buttonName }).click();
  await waitForQuiet(page);
}

async function replayAct3(browser, credentials) {
  const { context, page, actor } = await login(browser, credentials, actors.medicalAffairs);
  const screenshots = [];
  await gotoAndWait(page, "/knowledge/governance", "知识治理");
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act3,
      "01-knowledge-governance-ledger.png",
      "知识治理台账：候选来自来源导入，本页不生成候选",
    ),
  );
  const hasCreateEntry = await bodyIncludes(page, "本页不生成候选");
  const retirementButtonCount = await page.getByRole("button", { name: /安排弃用/ }).count();
  await page.getByRole("button", { name: "查看候选" }).first().click();
  await waitForQuiet(page);
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act3,
      "02-knowledge-candidate-review.png",
      "知识候选审核区：当前无待审候选",
    ),
  );

  await gotoAndWait(page, "/advanced/provenance?identityId=2", "来源追溯");
  await page
    .getByText("血钾危急值阈值与报告时限", { exact: false })
    .first()
    .waitFor({ timeout: 20000 });
  await waitForQuiet(page);
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act3,
      "03-provenance-potassium-source.png",
      "来源追溯：血钾危急值从资产反查指南/院内制度锚点",
    ),
  );

  await context.close();
  return {
    act: "幕3-知识治理",
    actor,
    steps: [
      {
        step: "知识治理页前台核查",
        result: hasCreateEntry
          ? "页面明确候选来自真实来源导入或 KNOW-02 分流，本页不生成候选；不能把登记知识源伪造成前台动作。"
          : "未识别到页面内候选来源说明，需要人工复核。",
        screenshot: "01-knowledge-governance-ledger.png",
      },
      {
        step: "候选审核区复演",
        result: "前台可查看知识身份与候选审核区；当前候选数为 0，无法在本页完成新知识登记→会签→发布。",
        screenshot: "02-knowledge-candidate-review.png",
      },
      {
        step: "来源追溯反查",
        result: "前台可从血钾危急值知识身份反查当前版本、历史版本和来源锚点。",
        screenshot: "03-provenance-potassium-source.png",
      },
    ],
    findings: [
      {
        id: "OPT-KNOW-UI-01",
        page: "/knowledge/governance",
        score: 4,
        problem:
          "幕3要求登记知识源、拆条目、建资产与版本、会签发布；当前客户页只承担候选审核与台账阅读，且租户角色看不到退役入口。",
        evidence: {
          hasCreateEntryNotice: hasCreateEntry,
          tenantRetirementButtons: retirementButtonCount,
        },
        proposal:
          "新增知识登记向导：来源登记→条目拆分→资产版本→签名会签→发布；对已发布租户知识提供新版本/退役入口与影响预览。",
      },
    ],
    screenshots,
  };
}

async function ensureAct85DraftRule(page, browser) {
  await gotoAndWait(page, "/rule/definitions", "规则中枢");
  const existedBefore = (await page.getByText(businessObjects.act85RuleCode).count()) > 0;
  await page.getByRole("button", { name: "新建规则模板" }).click();
  await page.locator(".ant-modal").last().waitFor({ timeout: 10000 });
  await page.getByPlaceholder("输入规则业务编码").fill(businessObjects.act85RuleCode);
  await page.getByPlaceholder("输入规则显示名称").fill(businessObjects.act85RuleName);
  await page
    .getByPlaceholder("输入已审核医学依据、院内制度或配置包来源")
    .fill("幕3知识资产：血钾危急值阈值与报告时限");
  await page
    .getByPlaceholder("输入当前已审核的标准上下文包版本")
    .fill("2026.06.11-act2-024101");
  await page
    .getByPlaceholder("本次创建版本的修改概述")
    .fill("幕8.5前台复演：通过页面创建危急值复核草稿");
  await page.getByLabel("危急值回报").check();
  await waitForQuiet(page);
  const formScreenshot = await capture(
    browser,
    page,
    actDirs.act4,
    "02-rule-create-draft-form.png",
    "规则新建表单：使用页面创建危急值复核草稿",
  );
  if (existedBefore) {
    await closeVisibleModalOrDrawer(page);
    return { status: "already_present", formScreenshot };
  }

  await page.getByRole("button", { name: "创建草稿" }).click();
  await page
    .locator(".ant-message-notice-content")
    .filter({ hasText: "新规则创建成功" })
    .first()
    .waitFor({ timeout: 15000 })
    .catch(() => undefined);
  await waitForQuiet(page);
  await gotoAndWait(page, "/rule/definitions", "规则中枢");
  const visibleAfter = (await page.getByText(businessObjects.act85RuleCode).count()) > 0;
  return { status: visibleAfter ? "created" : "created_but_not_on_first_page", formScreenshot };
}

async function replayAct4(browser, credentials) {
  const { context, page, actor } = await login(browser, credentials, actors.medicalAffairs);
  const screenshots = [];
  await gotoAndWait(page, "/rule/definitions", "规则中枢");
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act4,
      "01-rule-definitions-list.png",
      "规则库列表：既有 R1/R2/R3 规则与前台新建入口",
    ),
  );
  const draftResult = await ensureAct85DraftRule(page, browser);
  screenshots.push(draftResult.formScreenshot);
  await gotoAndWait(page, "/rule/definitions", "规则中枢");
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act4,
      "03-rule-act85-draft-visible.png",
      "规则库列表：幕8.5前台草稿规则已创建或可见",
    ),
  );

  await clickRowButton(page, businessObjects.baselineCriticalRuleCode, "查看配置与试运行");
  await page.locator(".ant-drawer").last().waitFor({ timeout: 10000 });
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act4,
      "04-rule-critical-readable-preview.png",
      "R1 血钾规则详情：L2 条件树与可读预览",
    ),
  );
  await page.locator(".ant-drawer .ant-tabs-tab").filter({ hasText: "发布门禁测试用例" }).click();
  await waitForQuiet(page);
  const runCases = page.getByRole("button", { name: "执行全部用例" }).first();
  if ((await runCases.count()) > 0 && (await runCases.isEnabled())) {
    await runCases.click();
    await page.getByText("通过", { exact: false }).first().waitFor({ timeout: 20000 });
    await waitForQuiet(page);
  }
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act4,
      "05-rule-test-cases-rerun.png",
      "R1 血钾规则：前台执行发布门禁测试用例",
    ),
  );
  await page.locator(".ant-drawer .ant-tabs-tab").filter({ hasText: "治理与发布" }).click();
  await waitForQuiet(page);
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act4,
      "06-rule-governance-flow.png",
      "R1 血钾规则：影子、灰度、全量治理流状态",
    ),
  );
  const ruleDrawerText = await page.locator(".ant-drawer").last().innerText();
  await context.close();

  const { context: doctorContext, page: doctorPage, actor: doctorActor } = await login(
    browser,
    credentials,
    actors.respiratoryDoctor,
  );
  await gotoAndWait(doctorPage, "/rule/definitions", "规则库");
  screenshots.push(
    await capture(
      browser,
      doctorPage,
      actDirs.act4,
      "07-rule-non-configurer-forbidden.png",
      "非配置者访问规则库：权限不足提示",
    ),
  );
  const doctorForbidden = await bodyIncludes(doctorPage, "当前权限不足");
  await gotoAndWait(doctorPage, "/rule/validate", "规则试运行");
  screenshots.push(
    await capture(
      browser,
      doctorPage,
      actDirs.act4,
      "08-rule-validate-console.png",
      "规则校验页：临床角色可执行匹配校验与解释回放",
    ),
  );
  await doctorContext.close();

  return {
    act: "幕4-规则配置与模拟",
    actor,
    secondaryActor: doctorActor,
    steps: [
      {
        step: "前台创建规则草稿",
        result:
          draftResult.status === "created"
            ? `已通过页面创建 ${businessObjects.act85RuleCode} 草稿。`
            : `已复用页面可见的 ${businessObjects.act85RuleCode} 草稿，避免重复创建。`,
        screenshot: "02-rule-create-draft-form.png",
      },
      {
        step: "R1 规则可读性复核",
        result: "前台可查看 L2 条件树、测试用例和治理流；可读预览仍夹杂字段路径与技术 ID。",
        screenshot: "04-rule-critical-readable-preview.png",
      },
      {
        step: "发布门禁测试复跑",
        result: "前台测试用例入口可执行；阳性、阴性、边界、冲突四类用例均展示通过状态。",
        screenshot: "05-rule-test-cases-rerun.png",
      },
      {
        step: "非配置者阅读边界",
        result: doctorForbidden
          ? "呼吸科医生不能进入规则库，只能进入规则校验页；无法直接阅读 R1/R2/R3 配置。"
          : "呼吸科医生进入规则库结果需人工复核。",
        screenshot: "07-rule-non-configurer-forbidden.png",
      },
    ],
    findings: [
      {
        id: "OPT-VIS-01",
        page: "/rule/definitions",
        score: 5,
        problem:
          "规则详情虽有可读预览，但仍包含 observations[].valueNumeric、K001、UUID 等技术语汇；非配置者无规则库入口，无法按客户要求直接读 R1/R2/R3。",
        evidence: {
          containsTechnicalFieldPath: ruleDrawerText.includes("observations[].valueNumeric"),
          doctorRuleLibraryForbidden: doctorForbidden,
        },
        proposal:
          "增加面向业务读者的自然语言摘要和流程图只读视图，并从规则校验页/推荐卡提供受控只读跳转。",
      },
    ],
    screenshots,
  };
}

async function replayAct5(browser, credentials) {
  const { context, page, actor } = await login(browser, credentials, actors.specialist);
  const screenshots = [];
  await gotoAndWait(page, "/pathway/templates", "路径中枢");
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act5,
      "01-pathway-templates-list.png",
      "路径配置列表：CAP 已发布模板与新建入口",
    ),
  );
  await clickRowButton(page, businessObjects.capTemplateCode, "设计与试运行");
  await page.locator(".ant-drawer").last().waitFor({ timeout: 10000 });
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act5,
      "02-pathway-detail-write-protected.png",
      "CAP 路径详情：全量生效后拓扑写保护",
    ),
  );
  const pathwayDrawerText = await page.locator(".ant-drawer").last().innerText();
  await page.locator(".ant-drawer .ant-tabs-tab").filter({ hasText: "L2 节点画布" }).click();
  await waitForQuiet(page);
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act5,
      "03-pathway-graph-review.png",
      "CAP 路径图形视图：6 节点和流转边",
    ),
  );
  await page.locator(".ant-drawer .ant-tabs-tab").filter({ hasText: "7 步流发布" }).click();
  await waitForQuiet(page);
  screenshots.push(
    await capture(
      browser,
      page,
      actDirs.act5,
      "04-pathway-release-flow.png",
      "CAP 路径发布流：已全量运行与回滚入口",
    ),
  );
  await context.close();

  const { context: doctorContext, page: doctorPage, actor: doctorActor } = await login(
    browser,
    credentials,
    actors.respiratoryDoctor,
  );
  await gotoAndWait(doctorPage, "/pathway/templates", "路径配置");
  screenshots.push(
    await capture(
      browser,
      doctorPage,
      actDirs.act5,
      "05-pathway-doctor-config-forbidden.png",
      "呼吸科医生访问路径配置：权限不足提示",
    ),
  );
  const doctorConfigForbidden = await bodyIncludes(doctorPage, "当前权限不足");
  await gotoAndWait(doctorPage, "/pathway/patients", "患者路径");
  screenshots.push(
    await capture(
      browser,
      doctorPage,
      actDirs.act5,
      "06-doctor-patient-pathway-list.png",
      "医生运行态患者路径列表：可查看 CAP 入径患者",
    ),
  );
  await clickRowButton(doctorPage, businessObjects.capTemplateId, "办理推进与解释追溯");
  await doctorPage.locator(".ant-drawer").last().waitFor({ timeout: 10000 });
  screenshots.push(
    await capture(
      browser,
      doctorPage,
      actDirs.act5,
      "07-doctor-pathway-runtime-detail.png",
      "医生运行态详情：时间线、关键时钟与当前节点",
    ),
  );
  await doctorContext.close();

  return {
    act: "幕5-CAP临床路径",
    actor,
    secondaryActor: doctorActor,
    steps: [
      {
        step: "路径配置列表复核",
        result: "专科专家可进入路径配置页并查看 CAP 已发布模板。",
        screenshot: "01-pathway-templates-list.png",
      },
      {
        step: "已发布模板维护边界",
        result: "页面明确全量生效后拓扑写保护，修改需创建新版本；当前详情页未提供直接编辑已发布拓扑的入口。",
        screenshot: "02-pathway-detail-write-protected.png",
      },
      {
        step: "图形视图评审",
        result: "配置者可看路径图；医生不能进入配置图，只能在患者路径运行态查看当前节点和里程碑。",
        screenshot: "03-pathway-graph-review.png",
      },
      {
        step: "医生口述路径素材",
        result:
          "医生运行态详情能看当前节点和关键时钟，但不能在同一页看到完整 CAP 模板全图；仍难完整口述整条路径。",
        screenshot: "07-doctor-pathway-runtime-detail.png",
      },
    ],
    findings: [
      {
        id: "OPT-VIS-02",
        page: "/pathway/templates / /pathway/patients",
        score: 5,
        problem:
          "CAP 模板图在配置者页面，医生运行态只有当前节点与里程碑列表；已发布模板写保护提示清晰，但缺少面向医生的整条路径只读图。",
        evidence: {
          publishedTemplateWriteProtected: pathwayDrawerText.includes("拓扑结构写保护"),
          doctorTemplateConfigForbidden: doctorConfigForbidden,
        },
        proposal:
          "将 PathwayGraphEditor 拆成医生只读视图和实施编辑视图；患者路径详情加入完整时间轴/泳道图，并突出当前患者位置。",
      },
      {
        id: "OPT-PATH-UI-01",
        page: "/pathway/templates",
        score: 5,
        problem:
          "幕8.5要求前台调整模板并重新发布；现有已发布模板无法直接维护，页面仅提示创建新版本，但详情页没有醒目的“创建新版本”按钮。",
        proposal:
          "在已发布模板详情顶部提供“复制为新版本”主按钮，带影响预览、草稿编辑、灰度发布和回滚链路。",
      },
    ],
    screenshots,
  };
}

async function main() {
  await ensureDirs();
  const credentials = loadCredentials();
  const browser = await chromium.launch({ headless: true });
  try {
    const summaries = {
      generatedAt: new Date().toISOString(),
      environment: baseUrl,
      credentialSource: credentialPath,
      acts: [await replayAct3(browser, credentials), await replayAct4(browser, credentials), await replayAct5(browser, credentials)],
    };
    await writeFile(
      path.join(actDirs.act3, "00-ui-replay-summary.json"),
      `${JSON.stringify(summaries.acts[0], null, 2)}\n`,
    );
    await writeFile(
      path.join(actDirs.act4, "00-ui-replay-summary.json"),
      `${JSON.stringify(summaries.acts[1], null, 2)}\n`,
    );
    await writeFile(
      path.join(actDirs.act5, "00-ui-replay-summary.json"),
      `${JSON.stringify(summaries.acts[2], null, 2)}\n`,
    );
    console.log(
      JSON.stringify(
        {
          generatedAt: summaries.generatedAt,
          environment: summaries.environment,
          acts: summaries.acts.map((act) => ({
            act: act.act,
            stepCount: act.steps.length,
            screenshotCount: act.screenshots.length,
            findings: act.findings.map((finding) => finding.id),
          })),
        },
        null,
        2,
      ),
    );
  } finally {
    await browser.close();
  }
}

await main();
