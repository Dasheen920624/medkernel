#!/usr/bin/env node
// 全流程演练/P1：使用指南与培训材料对齐最新体验证据的可复跑验收。
// 产出：
// - docs/release/evidence/v1.0-drill-20260611/指南验收/00-guide-acceptance-proof.json
// - docs/release/evidence/v1.0-drill-20260611/指南验收/README.md
import { execFileSync } from "node:child_process";
import { mkdir, readFile, stat, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import path from "node:path";
import { fileURLToPath } from "node:url";

const requireFromFrontend = createRequire(new URL("../../frontend/package.json", import.meta.url));
const prettier = requireFromFrontend("prettier");

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, "../..");
const evidenceDir = path.join(repoRoot, "docs/release/evidence/v1.0-drill-20260611/指南验收");

const requiredDocs = [
  "docs/handbook/user-guides/README.md",
  "docs/handbook/user-guides/tenant-readiness.md",
  "docs/handbook/user-guides/clinical-runtime.md",
  "docs/handbook/user-guides/quality-improvement.md",
  "docs/handbook/user-guides/compliance-operations.md",
  "docs/handbook/user-guides/third-party-cases.md",
  "docs/handbook/training/README.md",
  "docs/handbook/training/clinician.md",
  "docs/handbook/training/operator.md",
  "docs/handbook/training/quality-officer.md",
  "docs/glossary.md",
];

const requiredEvidence = [
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/推荐中枢-134复验/README.md",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/推荐中枢-134复验/02-desktop-recommendation-drawer.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/规则可读路径-134复验/README.md",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/规则可读路径-134复验/02-desktop-rule-readable-drawer.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/路径可读化-134复验/README.md",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/路径可读化-134复验/02-desktop-patient-readonly-graph.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/路径可读化-134复验/07-desktop-template-copy-dialog.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/README.md",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/01-desktop-audit-trace-search.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/审计traceId诊断链-134复验/03-desktop-trace-diagnosis-state.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/README.md",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/02-desktop-data-permission-result.png",
  "docs/release/evidence/v1.0-drill-20260611/P1-体验重构/安全基线试算预览-134复验/04-desktop-masking-result.png",
];

const contentChecks = [
  {
    id: "guide-index-p1-evidence",
    file: "docs/handbook/user-guides/README.md",
    includes: [
      "P1 第一批 134 复验证据",
      "规则可读路径 P1 复验证据",
      "Trace ID 诊断链",
      "权限试算和脱敏预览",
    ],
  },
  {
    id: "clinical-runtime-p1-path-and-recommendation",
    file: "docs/handbook/user-guides/clinical-runtime.md",
    includes: [
      "P1 已补提醒与推荐中枢",
      "提醒与推荐中枢",
      "按患者、traceId 或来源对象检索推荐卡",
      "医生只读路径图",
      "复制为新版本",
      "P1-体验重构/推荐中枢-134复验/02-desktop-recommendation-drawer.png",
      "P1-体验重构/路径可读化-134复验/02-desktop-patient-readonly-graph.png",
      "OPT-WORKFLOW-01",
    ],
    excludes: [
      "后续将升级为“提醒与推荐中枢”",
      "医生端仍需只读全图增强",
      "没有“整条路径图 + 当前患者位置”视图，因此 `OPT-VIS-02` 继续成立",
      "后续由 `OPT-PATH-UI-01` 增加“复制为新版本”主入口",
      "OPT-IA-01`、`OPT-TRACE-01` 和 `OPT-WORKFLOW-01` 继续进入体验重构",
    ],
  },
  {
    id: "tenant-readiness-rule-readable",
    file: "docs/handbook/user-guides/tenant-readiness.md",
    includes: [
      "P1 规则可读路径",
      "自然语言摘要和只读流程节点",
      "规则可读路径 134 复验",
      "质控或专科角色打开规则详情的“规则可读路径”",
    ],
    excludes: [
      "规则需要给非配置者看的自然语言摘要和流程图只读视图",
      "现有可读预览仍偏技术",
      "后续通过 `OPT-VIS-01` 增加",
    ],
  },
  {
    id: "compliance-trace-and-security-baseline",
    file: "docs/handbook/user-guides/compliance-operations.md",
    includes: [
      "按 Trace ID",
      "诊断链入口",
      "数据权限试算",
      "脱敏预览面板",
      "P1-体验重构/审计traceId诊断链-134复验/01-desktop-audit-trace-search.png",
      "P1-体验重构/安全基线试算预览-134复验/02-desktop-data-permission-result.png",
      "P1-体验重构/安全基线试算预览-134复验/04-desktop-masking-result.png",
    ],
    excludes: [
      "L2 暂无前台试算器",
      "幕10 L2 已登记 traceId 直搜、权限试算和脱敏预览面板三个后续点",
      "页面能看到规则；L1 接口证明 patientName 与 idNo 被遮罩",
    ],
  },
  {
    id: "clinician-training-p1",
    file: "docs/handbook/training/clinician.md",
    includes: ["医生只读路径图", "提醒与推荐中枢", "七段推荐链路", "traceId 或来源对象"],
    excludes: ["后续会补患者 / trace 检索", "记录为体验问题，不当作医生培训失败"],
  },
  {
    id: "operator-training-p1",
    file: "docs/handbook/training/operator.md",
    includes: ["Trace ID 直搜", "诊断链", "数据权限试算", "脱敏预览"],
    excludes: [
      "审计页不能直接按 traceId 搜",
      "安全基线页不能直接试算权限和脱敏",
      "使用接口证据佐证",
    ],
  },
  {
    id: "quality-training-rule-readable",
    file: "docs/handbook/training/quality-officer.md",
    includes: ["规则可读路径", "自然语言摘要", "技术字段只用于专家排错"],
    excludes: ["本轮已登记 `OPT-VIS-01`", "后续补自然语言回显和只读流程图"],
  },
];

function relativeToRepo(filePath) {
  return path.relative(repoRoot, filePath).split(path.sep).join("/");
}

async function exists(relativePath) {
  try {
    await stat(path.join(repoRoot, relativePath));
    return true;
  } catch {
    return false;
  }
}

function gitValue(args) {
  return execFileSync("git", args, { cwd: repoRoot, encoding: "utf8" }).trim();
}

function unique(values) {
  return [...new Set(values)];
}

async function readDoc(relativePath) {
  return readFile(path.join(repoRoot, relativePath), "utf8");
}

async function checkRequiredFiles(label, files) {
  const checks = await Promise.all(
    files.map(async (file) => ({
      file,
      exists: await exists(file),
    })),
  );
  return {
    id: label,
    pass: checks.every((item) => item.exists),
    checks,
  };
}

async function checkContent() {
  const checks = [];
  for (const check of contentChecks) {
    const content = await readDoc(check.file);
    const includeResults = (check.includes ?? []).map((needle) => ({
      needle,
      present: content.includes(needle),
    }));
    const excludeResults = (check.excludes ?? []).map((needle) => ({
      needle,
      absent: !content.includes(needle),
    }));
    checks.push({
      id: check.id,
      file: check.file,
      pass:
        includeResults.every((item) => item.present) && excludeResults.every((item) => item.absent),
      includes: includeResults,
      excludes: excludeResults,
    });
  }
  return {
    id: "content-anchors",
    pass: checks.every((item) => item.pass),
    checks,
  };
}

function extractMarkdownLinks(content) {
  const links = [];
  const pattern = /!?\[[^\]]*]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;
  let match;
  while ((match = pattern.exec(content)) !== null) {
    links.push(match[1]);
  }
  return links;
}

async function checkMarkdownLinks() {
  const checks = [];
  for (const file of requiredDocs.filter((item) => item.endsWith(".md"))) {
    const content = await readDoc(file);
    const links = unique(extractMarkdownLinks(content))
      .filter((link) => !link.startsWith("http://"))
      .filter((link) => !link.startsWith("https://"))
      .filter((link) => !link.startsWith("mailto:"))
      .filter((link) => !link.startsWith("#"));
    for (const link of links) {
      const [target] = link.split("#");
      if (!target) continue;
      const resolved = path.resolve(repoRoot, path.dirname(file), decodeURI(target));
      checks.push({
        file,
        link,
        target: relativeToRepo(resolved),
        exists: await exists(relativeToRepo(resolved)),
      });
    }
  }
  return {
    id: "markdown-links",
    pass: checks.every((item) => item.exists),
    checks,
  };
}

async function buildReadme(proof) {
  const status = proof.pass ? "通过" : "未通过";
  const rows = proof.sections
    .map(
      (section) =>
        `| ${section.id} | ${section.pass ? "通过" : "未通过"} | ${section.checks?.length ?? 0} |`,
    )
    .join("\n");
  const failed = proof.sections
    .flatMap((section) => {
      if (section.pass) return [];
      if (section.id === "content-anchors") {
        return section.checks
          .filter((item) => !item.pass)
          .map((item) => `- ${section.id} / ${item.id}：${item.file}`);
      }
      if (section.id === "markdown-links") {
        return section.checks
          .filter((item) => !item.exists)
          .map((item) => `- ${section.id}：${item.file} -> ${item.link}`);
      }
      return section.checks
        .filter((item) => item.exists === false)
        .map((item) => `- ${section.id}：${item.file}`);
    })
    .join("\n");

  const markdown = `# 使用指南验收证据

> 日期：${proof.runAt}
> 分支：\`${proof.git.branch}\`
> 提交：\`${proof.git.commit}\`
> 结论：${status}

## 1. 验收口径

本脚本只验证“使用指南与培训材料是否已经对齐到最新可演示事实”，不把代理检查冒充真人验收。它覆盖三类输入：

| 类别 | 范围 |
| --- | --- |
| 文档完整性 | 5 本用户手册、3 本角色培训、培训 README、术语表 |
| P1 证据锚点 | 推荐中枢、规则可读路径、路径可读化、审计 Trace ID 诊断链、安全基线试算预览 |
| 内容一致性 | 新入口必须出现，已销项旧限制不得回流，文档内相对链接必须存在 |

## 2. 检查结果

| 检查 | 结论 | 明细数 |
| --- | --- | --- |
${rows}

${failed ? `## 3. 未通过项\n\n${failed}\n` : "## 3. 未通过项\n\n无。\n"}
## 4. 证据文件

- [JSON 明细](00-guide-acceptance-proof.json)
`;
  return prettier.format(markdown, { parser: "markdown" });
}

async function main() {
  await mkdir(evidenceDir, { recursive: true });
  const sections = [
    await checkRequiredFiles("required-docs", requiredDocs),
    await checkRequiredFiles("required-p1-evidence", requiredEvidence),
    await checkContent(),
    await checkMarkdownLinks(),
  ];
  const proof = {
    runAt: new Date().toISOString(),
    pass: sections.every((section) => section.pass),
    git: {
      branch: gitValue(["branch", "--show-current"]),
      commit: gitValue(["rev-parse", "--short=12", "HEAD"]),
    },
    scope: {
      docs: requiredDocs,
      evidence: requiredEvidence,
    },
    sections,
  };
  await writeFile(
    path.join(evidenceDir, "00-guide-acceptance-proof.json"),
    `${JSON.stringify(proof, null, 2)}\n`,
  );
  await writeFile(path.join(evidenceDir, "README.md"), await buildReadme(proof));
  if (!proof.pass) {
    console.error(`指南验收未通过，证据已写入：${relativeToRepo(evidenceDir)}`);
    process.exit(1);
  }
  console.log(
    `指南验收通过：${relativeToRepo(path.join(evidenceDir, "00-guide-acceptance-proof.json"))}`,
  );
}

await main();
