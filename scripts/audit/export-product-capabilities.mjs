#!/usr/bin/env node

import { createRequire } from "node:module";
import {
  existsSync,
  readFileSync,
  readdirSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { basename, dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, "../..");
const requireFromFrontend = createRequire(
  resolve(repositoryRoot, "frontend/package.json"),
);
const ts = requireFromFrontend("typescript");

const routesPath = resolve(
  repositoryRoot,
  "frontend/src/shared/config/routes.ts",
);
const routerPath = resolve(repositoryRoot, "frontend/src/app/router.tsx");
const menuCatalogPath = resolve(
  repositoryRoot,
  "medkernel-backend/src/main/java/com/medkernel/engine/security/MenuPermissionCatalog.java",
);
const pagesRoot = resolve(repositoryRoot, "frontend/src/pages");
const backendRoot = resolve(repositoryRoot, "medkernel-backend/src/main/java");
const outputPath = resolve(
  repositoryRoot,
  "docs/audit/product-function-catalog.md",
);

const routeDecisions = {
  "/login": {
    decision: "KEEP",
    targetDomain: "认证入口",
    targetEntry: "登录",
    task: "按平台治理或医疗机构身份进入职责工作台",
  },
  "/bootstrap": {
    decision: "KEEP",
    targetDomain: "部署接管",
    targetEntry: "首次部署接管",
    task: "仅在首次部署时创建内置超级管理员并完成安全接管",
  },
  "/": {
    decision: "REMOVE",
    targetDomain: "认证入口",
    targetEntry: "登录",
    task: "删除无业务意义的根路径能力，仅保留路由重定向",
  },
  "/dashboard": {
    decision: "KEEP",
    targetDomain: "工作台",
    targetEntry: "工作台",
    task: "查看当前职责风险、待办和高频任务入口",
  },
  "/workbench/readiness-validation": {
    decision: "MERGE",
    targetDomain: "工作台",
    targetEntry: "验收自检（工作台页内）",
    task: "由实施和管理角色核查阻塞项与验收状态",
  },
  "/onboarding/guide": {
    decision: "MOVE",
    targetDomain: "系统运维",
    targetEntry: "实施与验收",
    task: "完成机构开通、初始化、联调和交付验收",
  },
  "/tenant/onboarding": {
    decision: "MOVE",
    targetDomain: "机构与人员",
    targetEntry: "服务机构",
    task: "维护服务机构、稳定组织层级和机构类型",
  },
  "/config/releases": {
    decision: "MERGE",
    targetDomain: "知识治理",
    targetEntry: "机构生效版本",
    task: "维护平台标准版本、机构生效版本、发布影响和回滚证据",
  },
  "/authoring/assets": {
    decision: "MERGE",
    targetDomain: "知识治理",
    targetEntry: "知识资产",
    task: "在统一知识资产页内编目、复用和批量处理资产",
  },
  "/pathway/templates": {
    decision: "MOVE",
    targetDomain: "知识治理",
    targetEntry: "临床路径库",
    task: "维护、审核、发布和回滚临床路径版本",
  },
  "/rule/definitions": {
    decision: "MOVE",
    targetDomain: "知识治理",
    targetEntry: "临床规则",
    task: "配置、试运行、审核和发布临床规则",
  },
  "/terminology/mapping": {
    decision: "MOVE",
    targetDomain: "知识治理",
    targetEntry: "术语字典",
    task: "维护院内术语映射、冲突和高风险确认",
  },
  "/adapter/hub": {
    decision: "MOVE",
    targetDomain: "系统运维",
    targetEntry: "系统接入",
    task: "由集成和实施角色维护外部系统接入及失败补偿",
  },
  "/mpi": {
    decision: "MOVE",
    targetDomain: "临床协同",
    targetEntry: "患者索引",
    task: "在授权范围内核查患者主索引和合并拆分问题",
  },
  "/pathway/patients": {
    decision: "MOVE",
    targetDomain: "临床协同",
    targetEntry: "患者路径",
    task: "查看并处理患者路径节点、时钟和变异",
  },
  "/cdss/fatigue": {
    decision: "RENAME",
    targetDomain: "临床协同",
    targetEntry: "提醒与推荐",
    task: "查看推荐、来源、处置状态和反馈闭环",
  },
  "/rule/validate": {
    decision: "MERGE",
    targetDomain: "知识治理",
    targetEntry: "临床规则 / 试运行",
    task: "并入规则试运行与提醒详情，不保留客户独立菜单",
  },
  "/workflow/todos": {
    decision: "RENAME",
    targetDomain: "临床协同",
    targetEntry: "协同任务",
    task: "处理、转派、升级和完成职责范围内任务",
  },
  "/notifications": {
    decision: "MOVE",
    targetDomain: "工作台",
    targetEntry: "消息通知（页头入口）",
    task: "查看未读消息并跳转到对应业务任务",
  },
  "/clinical/followup": {
    decision: "RENAME",
    targetDomain: "临床协同",
    targetEntry: "随访协同",
    task: "生成随访计划、处理任务和异常回院事件",
  },
  "/sandbox": {
    decision: "KEEP",
    targetDomain: "临床协同",
    targetEntry: "全真体验沙盘",
    task: "以院内业务系统视角复演真实医疗智能链路、嵌入终端与人工反馈闭环",
  },
  "/qc/dashboard": {
    decision: "RENAME",
    targetDomain: "质量管理",
    targetEntry: "质量管理概览",
    task: "查看质量风险、运营趋势并下钻到责任问题",
  },
  "/qc/alerts": {
    decision: "RENAME",
    targetDomain: "质量管理",
    targetEntry: "质量问题与整改",
    task: "确认问题、派发整改、复核并闭环",
  },
  "/qc/insurance": {
    decision: "RENAME",
    targetDomain: "质量管理",
    targetEntry: "医保审核",
    task: "核查医保问题、依据和处置结果",
  },
  "/qc/eval/sets": {
    decision: "RENAME",
    targetDomain: "质量管理",
    targetEntry: "评价指标",
    task: "维护评价指标、影响分析和发布状态",
  },
  "/qc/eval/results": {
    decision: "MERGE",
    targetDomain: "质量管理",
    targetEntry: "质量问题与整改",
    task: "评估结果作为问题发现和整改页的来源视图",
  },
  "/knowledge/governance": {
    decision: "MOVE",
    targetDomain: "知识治理",
    targetEntry: "知识审核发布中心",
    task: "审核统一候选池中的平台主源或机构派生差异并发布、退修或驳回",
  },
  "/knowledge/institution": {
    decision: "SPLIT",
    targetDomain: "知识治理",
    targetEntry: "机构知识库",
    task: "从平台标准派生机构版本、查看机构覆盖血缘并恢复平台标准",
  },
  "/knowledge/diagnosis": {
    decision: "SPLIT",
    targetDomain: "知识治理",
    targetEntry: "诊断知识库",
    task: "维护诊断身份、诊断标准、鉴别诊断、验证病例与来源证据",
  },
  "/knowledge/production": {
    decision: "SPLIT",
    targetDomain: "知识生产",
    targetEntry: "知识生产工作台",
    task: "核查知识生产 readiness、生产 job、候选血缘、门禁、8 态、影子证据和高敏患者上下文用途确认重试",
  },
  "/admin/users": {
    decision: "MOVE",
    targetDomain: "机构与人员",
    targetEntry: "人员与账号",
    task: "维护自然人、任职、账号、职责和组织范围",
  },
  "/security/identity-binding": {
    decision: "MOVE",
    targetDomain: "机构与人员",
    targetEntry: "身份来源",
    task: "维护统一身份、员工号和证书的单个或批量绑定",
  },
  "/admin/audit": {
    decision: "MOVE",
    targetDomain: "合规安全",
    targetEntry: "审计与证据",
    task: "按人员、对象、动作和时间追溯并受控导出证据",
  },
  "/security/baseline": {
    decision: "MOVE",
    targetDomain: "合规安全",
    targetEntry: "安全与配置",
    task: "维护安全基线、系统配置、数据权限和脱敏策略",
  },
  "/system/providers": {
    decision: "RENAME",
    targetDomain: "系统运维",
    targetEntry: "运行保障",
    task: "查看外部依赖、备份恢复、降级和部署健康状态",
  },
  "/notifications/settings": {
    decision: "MOVE",
    targetDomain: "工作台",
    targetEntry: "通知偏好（个人菜单）",
    task: "维护个人通知偏好和有权限的机构默认策略",
  },
  "/advanced/provenance": {
    decision: "MOVE",
    targetDomain: "知识治理",
    targetEntry: "来源与血缘",
    task: "按来源、版本和引用锚点追溯知识证据",
  },
  "/advanced/graph": {
    decision: "MOVE",
    targetDomain: "知识治理",
    targetEntry: "知识关系",
    task: "查询可重建的知识关系投影",
  },
  "/advanced/ai-workflows": {
    decision: "MOVE",
    targetDomain: "知识生产",
    targetEntry: "模型能力",
    task: "查看模型能力、任务和诚实降级状态",
  },
  "/advanced/domestic": {
    decision: "MOVE",
    targetDomain: "系统运维",
    targetEntry: "国产化适配自检",
    task: "核查国产化适配与部署证据",
  },
  "/system/runtime-diagnostics": {
    decision: "MOVE",
    targetDomain: "系统运维",
    targetEntry: "运行诊断",
    task: "由信息科和实施角色执行受控诊断",
  },
  "/embed/launch": {
    decision: "KEEP",
    targetDomain: "临床协同",
    targetEntry: "院内系统嵌入终端",
    task: "在受信来源内承载临床嵌入并回传人工反馈",
  },
  "*": {
    decision: "KEEP",
    targetDomain: "系统反馈",
    targetEntry: "未找到页面",
    task: "为无效路径提供可恢复的中文错误状态",
  },
};

function propertyName(node) {
  if (ts.isIdentifier(node) || ts.isStringLiteral(node)) {
    return node.text;
  }
  return node.getText();
}

function literalValue(node) {
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return node.text;
  }
  if (node.kind === ts.SyntaxKind.TrueKeyword) return true;
  if (node.kind === ts.SyntaxKind.FalseKeyword) return false;
  return node.getText().replace(/\s+/g, " ");
}

function objectProperties(node) {
  const values = {};
  for (const property of node.properties) {
    if (!ts.isPropertyAssignment(property)) continue;
    values[propertyName(property.name)] = literalValue(property.initializer);
  }
  return values;
}

function extractRoutes() {
  const sourceText = readFileSync(routesPath, "utf8");
  const sourceFile = ts.createSourceFile(
    routesPath,
    sourceText,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS,
  );
  let routeArray;

  sourceFile.forEachChild((node) => {
    if (!ts.isVariableStatement(node)) return;
    for (const declaration of node.declarationList.declarations) {
      if (
        ts.isIdentifier(declaration.name) &&
        declaration.name.text === "routeMetaInputs" &&
        declaration.initializer &&
        ts.isArrayLiteralExpression(declaration.initializer)
      ) {
        routeArray = declaration.initializer;
      }
    }
  });

  if (!routeArray) {
    throw new Error("未找到 routeMetaInputs 路由目录");
  }

  return routeArray.elements
    .filter(ts.isObjectLiteralExpression)
    .map((element) => objectProperties(element))
    .map((route) => {
      const decision = routeDecisions[route.path];
      if (!decision) {
        throw new Error(`路由 ${route.path} 缺少显式产品裁决`);
      }
      return {
        ...route,
        hidden: route.hidden === true,
        placement:
          route.placement ??
          (route.menuKey
            ? "primary"
            : route.hidden === true
              ? "hidden"
              : "hidden"),
        ...decision,
      };
    });
}

function extractMenus() {
  const source = readFileSync(menuCatalogPath, "utf8");
  return Array.from(
    source.matchAll(
      /menu\("([^"]+)",\s*"([^"]+)",\s*"([^"]+)",\s*([A-Z0-9_]+),\s*MenuPlacement\.([A-Z]+)\)/g,
    ),
    (match) => ({
      sectionKey: match[1],
      menuKey: match[2],
      displayName: match[3],
      permission: match[4],
      placement: match[5].toLowerCase(),
    }),
  );
}

function extractPageMappings() {
  const source = readFileSync(routerPath, "utf8");
  const imports = new Map(
    Array.from(
      source.matchAll(
        /const\s+([A-Za-z0-9_]+)\s*=\s*lazy\(\(\)\s*=>\s*import\("@\/pages\/([^"]+)"\)\)/g,
      ),
      (match) => [match[1], `frontend/src/pages/${match[2]}.tsx`],
    ),
  );
  const routeComponents = new Map(
    Array.from(
      source.matchAll(
        /<Route\s+path="([^"]+)"\s+element=\{<([A-Za-z0-9_]+)\s*\/>\}/g,
      ),
      (match) => [match[1], match[2]],
    ),
  );
  return { imports, routeComponents };
}

function walkFiles(directory, predicate) {
  const files = [];
  for (const entry of readdirSync(directory)) {
    const fullPath = resolve(directory, entry);
    const stats = statSync(fullPath);
    if (stats.isDirectory()) {
      files.push(...walkFiles(fullPath, predicate));
    } else if (predicate(fullPath)) {
      files.push(fullPath);
    }
  }
  return files.sort();
}

function extractPages(routes) {
  const { imports, routeComponents } = extractPageMappings();
  const routeByPath = new Map(routes.map((route) => [route.path, route]));
  const routeByFile = new Map();

  for (const [path, component] of routeComponents) {
    const file = imports.get(component);
    if (file) routeByFile.set(file, routeByPath.get(path));
  }

  return walkFiles(
    pagesRoot,
    (file) => file.endsWith(".tsx") && !file.endsWith(".test.tsx"),
  ).map((file) => {
    const repositoryPath = relative(repositoryRoot, file).replaceAll("\\", "/");
    const linkedRoute = routeByFile.get(repositoryPath);
    return {
      file: repositoryPath,
      route: linkedRoute?.path ?? "页内组件",
      decision: linkedRoute?.decision ?? "MERGE",
      targetDomain: linkedRoute?.targetDomain ?? "对应父页面",
      targetEntry: linkedRoute?.targetEntry ?? "页内组件",
    };
  });
}

function joinEndpoint(basePath, suffix) {
  if (!suffix) return basePath || "/";
  return `${basePath ?? ""}${suffix}`.replace(/\/+/g, "/");
}

function controllerDecision(className, endpoints) {
  const text = `${className} ${endpoints.join(" ")}`;
  if (
    /Fhir|CdsHook|Embed|Interoperability|ThirdPartyKnowledge|Webhook|integration\/knowledge-runtime/i.test(
      text,
    )
  ) {
    return {
      decision: "API_ONLY",
      target: "第三方接口与嵌入契约",
      rationale: "仅服务外部系统或嵌入链路，不进入客户主菜单",
    };
  }
  if (/DataMinimizationPolicyController/i.test(className)) {
    return {
      decision: "KEEP",
      target: "对应客户任务页面",
      rationale: "策略维护归外调治理，当前任务用途确认可由知识生产操作者完成并落审计",
    };
  }
  if (
    /Developer|Projection|Diagnose|Model(Gateway|Egress|Provider|Eval|Enhancement)|PluginSecurity/i.test(
      text,
    )
  ) {
    if (/ModelEgressController/i.test(className)) {
      return {
        decision: "KEEP",
        target: "所属业务域专业能力",
        rationale: "外调策略由实施/运营维护，本次脱敏载荷用途确认由业务上下文承载",
      };
    }
    return {
      decision: "KEEP",
      target: "所属业务域专业能力",
      rationale: "按普通功能归入所属业务域，由权限控制，低频证据和诊断信息在业务上下文内渐进展示",
    };
  }
  if (/Batch|Export|RuntimeTask|AsyncSuffix/i.test(text)) {
    return {
      decision: "MERGE",
      target: "对应业务页内任务或导出流程",
      rationale: "异步和批量能力作为主任务步骤，不单列客户菜单",
    };
  }
  return {
    decision: "KEEP",
    target: "对应客户任务页面",
    rationale: "保留真实后端能力，由目标页面、权限和审计边界承载",
  };
}

function extractControllers() {
  return walkFiles(backendRoot, (file) => file.endsWith("Controller.java")).map(
    (file) => {
      const source = readFileSync(file, "utf8");
      const className = basename(file, ".java");
      const basePath =
        source.match(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"/)?.[1] ??
        "";
      const mappings = Array.from(
        source.matchAll(
          /@(Get|Post|Put|Delete|Patch)Mapping(?:\(\s*(?:(?:value|path)\s*=\s*)?"([^"]*)"?[^)]*\))?/g,
        ),
        (match) =>
          `${match[1].toUpperCase()} ${joinEndpoint(basePath, match[2] ?? "")}`,
      );
      const endpoints =
        mappings.length > 0 ? mappings : [`HTTP ${basePath || "类内映射"}`];
      return {
        file: relative(repositoryRoot, file).replaceAll("\\", "/"),
        className,
        endpoints,
        ...controllerDecision(className, endpoints),
      };
    },
  );
}

function extractBatchCapabilities() {
  return walkFiles(
    backendRoot,
    (file) =>
      file.endsWith(".java") &&
      /(Batch|Import|Export|Task)/.test(basename(file)) &&
      /(Controller|Service|Config|Executor)\.java$/.test(file),
  ).map((file) => {
    const className = basename(file, ".java");
    return {
      className,
      file: relative(repositoryRoot, file).replaceAll("\\", "/"),
      decision: "MERGE",
      target: /PersonnelImport/.test(className)
        ? "机构与人员 / 人员与账号"
        : /AuthoringBatch/.test(className)
          ? "知识治理 / 知识资产"
          : /Export/.test(className)
            ? "对应页面的受控导出"
            : "对应业务页的异步任务",
    };
  });
}

function escapeCell(value) {
  return String(value ?? "—")
    .replaceAll("|", "\\|")
    .replace(/\s+/g, " ")
    .trim();
}

function capabilityMarker(type, id, decision) {
  return `<!-- capability:${type}:${type}@${encodeURIComponent(id)} decision=${decision} -->`;
}

function countByDecision(items) {
  const counts = new Map();
  for (const item of items) {
    counts.set(item.decision, (counts.get(item.decision) ?? 0) + 1);
  }
  return Array.from(counts.entries()).sort(([left], [right]) =>
    left.localeCompare(right),
  );
}

function renderCatalog({ routes, menus, pages, controllers, batches }) {
  const routeByMenu = new Map(
    routes
      .filter((route) => route.menuKey)
      .map((route) => [route.menuKey, route]),
  );
  const menuRows = menus.map((menu) => {
    const route = routeByMenu.get(menu.menuKey);
    if (!route) {
      throw new Error(`后端菜单 ${menu.menuKey} 没有对应前端路由裁决`);
    }
    return { ...menu, ...route };
  });
  const allCapabilities = [
    ...routes,
    ...menuRows,
    ...pages,
    ...controllers,
    ...batches,
  ];
  const decisionSummary = countByDecision(allCapabilities)
    .map(([decision, count]) => `| ${decision} | ${count} |`)
    .join("\n");

  const routeRows = routes
    .map(
      (route) =>
        `${capabilityMarker("route", route.path, route.decision)}\n<!-- route:${route.path} -->\n| \`${escapeCell(route.path)}\` | ${escapeCell(route.title)} | ${escapeCell(route.sectionKey)} | ${escapeCell(route.menuKey)} | ${escapeCell(route.placement)} | ${route.decision} | ${escapeCell(route.targetDomain)} | ${escapeCell(route.targetEntry)} | ${escapeCell(route.task)} |`,
    )
    .join("\n");

  const menuTable = menuRows
    .map(
      (menu) =>
        `${capabilityMarker("menu", menu.menuKey, menu.decision)}\n<!-- menu:${menu.menuKey} -->\n| \`${escapeCell(menu.menuKey)}\` | ${escapeCell(menu.displayName)} | \`${escapeCell(menu.sectionKey)}\` | ${escapeCell(menu.placement)} | \`${escapeCell(menu.permission)}\` | ${menu.decision} | ${escapeCell(menu.targetDomain)} | ${escapeCell(menu.targetEntry)} |`,
    )
    .join("\n");

  const pageRows = pages
    .map(
      (page) =>
        `${capabilityMarker("page", page.file, page.decision)}\n| \`${escapeCell(page.file)}\` | \`${escapeCell(page.route)}\` | ${page.decision} | ${escapeCell(page.targetDomain)} | ${escapeCell(page.targetEntry)} |`,
    )
    .join("\n");

  const controllerRows = controllers
    .map(
      (controller) =>
        `${capabilityMarker("controller", controller.className, controller.decision)}\n| \`${escapeCell(controller.className)}\` | ${escapeCell(controller.endpoints.slice(0, 8).join("<br>"))}${controller.endpoints.length > 8 ? `<br>其余 ${controller.endpoints.length - 8} 项` : ""} | ${controller.decision} | ${escapeCell(controller.target)} | ${escapeCell(controller.rationale)} |`,
    )
    .join("\n");

  const batchRows = batches
    .map(
      (batch) =>
        `${capabilityMarker("batch", batch.className, batch.decision)}\n| \`${escapeCell(batch.className)}\` | \`${escapeCell(batch.file)}\` | ${batch.decision} | ${escapeCell(batch.target)} |`,
    )
    .join("\n");

  return `# 全系统功能目录与唯一产品裁决

> 本目录由 \`node scripts/audit/export-product-capabilities.mjs\` 从前端路由、后端菜单、页面组件、控制器和批量任务源码确定性生成。任何新增能力若没有显式裁决，生成器直接失败。
>
> 裁决口径：\`KEEP\` 保留、\`RENAME\` 重命名、\`MOVE\` 移位、\`MERGE\` 合并、\`SPLIT\` 拆分、\`API_ONLY\` 接口化、\`REMOVE\` 移除。每项能力只允许一个主裁决；目标名称与目标归属同时记录，不通过兼容入口保留旧结构。专业能力与普通功能一样归类，不使用独立专家裁决。

## 1. 库存结论

- 前端路由：${routes.length} 项。
- 后端菜单：${menus.length} 项。
- 页面与页内组件：${pages.length} 项。
- 后端控制器：${controllers.length} 项。
- 批量、导入、导出和异步任务承载类：${batches.length} 项。
- 目标客户业务域：工作台、机构与人员、知识治理、临床协同、质量管理、合规安全、系统运维。
- 专业能力按普通功能归入所属业务域并由权限控制；仅服务外部系统的能力只保留接口契约。

| 裁决 | 数量 |
|---|---:|
${decisionSummary}

## 2. 前端路由与客户任务裁决

| 当前路径 | 当前名称 | 当前分组 | 当前菜单键 | 承载方式 | 裁决 | 目标域 | 目标入口 | 唯一客户任务 |
|---|---|---|---|---|---|---|---|---|
${routeRows}

## 3. 后端菜单目录裁决

| 菜单键 | 当前名称 | 当前分组 | 承载方式 | 权限 | 裁决 | 目标域 | 目标入口 |
|---|---|---|---|---|---|---|---|
${menuTable}

## 4. 页面与页内组件归属

| 文件 | 当前路由 | 裁决 | 目标域 | 目标入口 |
|---|---|---|---|---|
${pageRows}

## 5. 后端能力与第三方接口裁决

| 控制器 | 接口摘要 | 裁决 | 目标承载 | 原因 |
|---|---|---|---|---|
${controllerRows}

## 6. 批量任务与异步流程裁决

| 承载类 | 文件 | 裁决 | 目标承载 |
|---|---|---|---|
${batchRows}

## 7. 强制收口动作

1. 目标信息架构必须以本目录的唯一任务和目标归属为输入，比较领域型、角色任务型、生命周期型和混合型方案后写入产品权威。
2. \`MOVE\`、\`RENAME\`、\`MERGE\` 和 \`REMOVE\` 必须同步修改菜单、路由、权限、面包屑、页面、客户手册和自动化测试。
3. \`API_ONLY\` 能力不得进入客户菜单，只能出现在第三方接口、嵌入契约、实施联调或专业诊断材料中。
4. 页面组件不是独立客户能力；没有独立任务的组件统一 \`MERGE\` 到父页面。
5. 目录通过不等于产品门禁通过；必须继续完成 四职责旅程、全中文、六态、桌面与移动端、八视角评审和全量测试。
`;
}

function generateCatalog() {
  const routes = extractRoutes();
  const menus = extractMenus();
  const pages = extractPages(routes);
  const controllers = extractControllers();
  const batches = extractBatchCapabilities();
  return renderCatalog({ routes, menus, pages, controllers, batches });
}

function main() {
  const generated = generateCatalog();
  const checkOnly = process.argv.includes("--check");

  if (checkOnly) {
    if (!existsSync(outputPath)) {
      console.error(`功能目录不存在：${relative(repositoryRoot, outputPath)}`);
      process.exitCode = 1;
      return;
    }
    const current = readFileSync(outputPath, "utf8");
    if (current !== generated) {
      console.error(
        "功能目录与当前源码不一致，请运行 node scripts/audit/export-product-capabilities.mjs 重新生成。",
      );
      process.exitCode = 1;
    }
    return;
  }

  writeFileSync(outputPath, generated, "utf8");
  console.log(`已生成 ${relative(repositoryRoot, outputPath)}`);
}

main();
