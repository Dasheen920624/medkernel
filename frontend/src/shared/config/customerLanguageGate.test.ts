import { readFileSync, readdirSync } from "node:fs";
import { extname, join, relative, resolve } from "node:path";
import ts from "typescript";
import { describe, expect, it } from "vitest";

import {
  customerDisplayText,
  customerEnumLabel,
  customerSafeDisplayText,
  hasTechnicalDetailText,
  orgLevelLabel,
  sourceAuthorityLabel,
} from "./customerLabels";

const sourceRoot = resolve(process.cwd(), "src");
const scanRoots = ["features", "pages", "shared/config", "shared/lib", "shared/ui", "widgets"];
const visibleAttributeNames = new Set([
  "aria-label",
  "content",
  "description",
  "emptyText",
  "label",
  "message",
  "placeholder",
  "title",
]);
const visiblePropertyNames = new Set([
  "aria-label",
  "content",
  "description",
  "emptyText",
  "evidence",
  "goal",
  "label",
  "message",
  "placeholder",
  "title",
  "defaultView",
]);
const visibleArrayPropertyNames = new Set(["evidenceDetailContent"]);
const forbiddenCustomerTokens =
  /(?:\/api\/|发布包|运行包|证据包|知识包|配置包|运行修订|运行发布|运行制品|运行快照|发布制品|制品|发布容器|包发布|清单摘要|资产清单|平台基线|权威基线|运行版本|冻结基线|快照运行标识|运行标识|医院当前运行|医院运行|修订 #|租户|令牌|白名单|生产闸|技术放行|三元组|门禁|质量门|运行底座|出域|回归|未识别|B0|ACTIVE|MFA|context\.write|tenant id|\b(?:traceId|TraceId|Trace ID|Trace|Payload|Provider|provider|Schema|API|OpenAPI|Key|readiness|job|endpoint|token|NOT_CONNECTED|TENANT|ASSET|MEDIUM)\b)/;
const technicalFormatTokens = /\b(?:JSON|DSL)\b/;

describe("customer language gate", () => {
  it("translates standalone and embedded service enum values", () => {
    expect(customerEnumLabel("NOT_CONNECTED")).toBe("未接通");
    expect(customerDisplayText("HIS 适配器仍为 NOT_CONNECTED")).toBe("HIS 适配器仍为 未接通");
    expect(customerDisplayText("权限维度 ASSET 缺少授权")).toBe("权限维度 治理资产 缺少授权");
    expect(customerDisplayText("未配置专属策略，使用系统 B0 基线")).toBe(
      "未配置专属策略，使用系统无模型规则链路",
    );
    expect(customerDisplayText("请先选择一个 ACTIVE 上下文快照")).toBe(
      "请先选择一个已生效上下文快照",
    );
    expect(customerDisplayText("MEDIUM")).toBe("中风险");
  });

  it("uses confirmable business fallback instead of unidentified-state wording", () => {
    expect(customerEnumLabel("UNEXPECTED_ENGINE_STATUS")).toBe("状态待确认");
    expect(customerDisplayText("UNEXPECTED_ENGINE_STATUS")).toBe("状态待确认");
    expect(orgLevelLabel("UNEXPECTED_SCOPE_LEVEL")).toBe("状态待确认");
    expect(customerEnumLabel("UNEXPECTED_ENGINE_STATUS")).not.toContain("未识别");
  });

  it("uses the same five source authority levels as the medical engine", () => {
    expect(sourceAuthorityLabel("A_REGULATION")).toBe("法规与强制规范");
    expect(sourceAuthorityLabel("B_GUIDELINE")).toBe("权威指南");
    expect(sourceAuthorityLabel("C_CONSENSUS_LITERATURE")).toBe("共识与医学文献");
    expect(sourceAuthorityLabel("D_HOSPITAL")).toBe("院内制度");
    expect(sourceAuthorityLabel("E_FEEDBACK")).toBe("反馈与其他低阶来源");
    expect(sourceAuthorityLabel("C_CONSENSUS")).toBe("来源未分级");
    expect(sourceAuthorityLabel("E_LITERATURE")).toBe("来源未分级");
    expect(sourceAuthorityLabel("F_EXPERIENCE")).toBe("来源未分级");
  });

  it("replaces raw technical details with customer-safe fallback text", () => {
    const rawError = "GET /api/v1/terminology failed: ECONNREFUSED 127.0.0.1:8080";

    expect(hasTechnicalDetailText(rawError)).toBe(true);
    expect(customerSafeDisplayText(rawError, "数据读取失败，请联系信息科。")).toBe(
      "数据读取失败，请联系信息科。",
    );
    expect(customerSafeDisplayText("HIS 适配器仍为 NOT_CONNECTED", "数据读取失败")).toBe(
      "HIS 适配器仍为 未接通",
    );
  });

  it("keeps raw technical tokens out of customer-visible source strings", () => {
    const violations = customerSourceFiles().flatMap(scanVisibleStrings);
    expect(violations).toEqual([]);
  });
});

function customerSourceFiles(): string[] {
  return scanRoots.flatMap((root) => walk(resolve(sourceRoot, root)));
}

function walk(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) return walk(path);
    if (
      ![".ts", ".tsx"].includes(extname(entry.name)) ||
      entry.name.endsWith(".test.ts") ||
      entry.name.endsWith(".test.tsx") ||
      entry.name.endsWith(".d.ts")
    ) {
      return [];
    }
    return [path];
  });
}

function scanVisibleStrings(path: string): string[] {
  const sourceText = readFileSync(path, "utf8");
  const source = ts.createSourceFile(
    path,
    sourceText,
    ts.ScriptTarget.Latest,
    true,
    path.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
  );
  const violations: string[] = [];
  const sourcePath = relative(sourceRoot, path);

  const record = (node: ts.Node, text: string) => {
    const hasForbiddenToken = forbiddenCustomerTokens.test(text);
    const hasForbiddenFormat = technicalFormatTokens.test(text);
    if (!hasForbiddenToken && !hasForbiddenFormat) return;
    const line = source.getLineAndCharacterOfPosition(node.getStart(source)).line + 1;
    violations.push(`${sourcePath}:${line} ${text.trim()}`);
  };

  const recordLiteralExpression = (node: ts.Expression | undefined, location: ts.Node) => {
    if (
      !node ||
      (!ts.isStringLiteral(node) &&
        !ts.isNoSubstitutionTemplateLiteral(node) &&
        !ts.isTemplateExpression(node))
    ) {
      return;
    }
    record(location, literalVisibleText(node));
  };

  const visit = (node: ts.Node) => {
    if (ts.isJsxText(node)) {
      record(node, node.text);
    } else if (ts.isJsxAttribute(node) && visibleAttributeNames.has(node.name.getText(source))) {
      if (node.initializer && ts.isStringLiteral(node.initializer)) {
        record(node, node.initializer.text);
      } else if (node.initializer && ts.isJsxExpression(node.initializer)) {
        recordLiteralExpression(node.initializer.expression, node);
      }
    } else if (
      ts.isJsxAttribute(node) &&
      node.name.getText(source) === "value" &&
      isVisibleValueAttribute(node, source)
    ) {
      if (node.initializer && ts.isStringLiteral(node.initializer)) {
        record(node, node.initializer.text);
      } else if (node.initializer && ts.isJsxExpression(node.initializer)) {
        recordLiteralExpression(node.initializer.expression, node);
      }
    } else if (
      (ts.isStringLiteral(node) ||
        ts.isNoSubstitutionTemplateLiteral(node) ||
        ts.isTemplateExpression(node)) &&
      isVisibleJsxExpression(node)
    ) {
      record(node, literalVisibleText(node));
    } else if (
      ts.isPropertyAssignment(node) &&
      visiblePropertyNames.has(node.name.getText(source))
    ) {
      if (
        ts.isStringLiteral(node.initializer) ||
        ts.isNoSubstitutionTemplateLiteral(node.initializer)
      ) {
        record(node, node.initializer.text);
      } else if (ts.isTemplateExpression(node.initializer)) {
        record(node, literalVisibleText(node.initializer));
      }
    } else if (
      ts.isPropertyAssignment(node) &&
      visibleArrayPropertyNames.has(node.name.getText(source)) &&
      ts.isArrayLiteralExpression(node.initializer)
    ) {
      node.initializer.elements.forEach((element) => {
        if (ts.isStringLiteral(element) || ts.isNoSubstitutionTemplateLiteral(element)) {
          record(element, element.text);
        } else if (ts.isTemplateExpression(element)) {
          record(element, literalVisibleText(element));
        }
      });
    } else if (
      ts.isCallExpression(node) &&
      ts.isPropertyAccessExpression(node.expression) &&
      ["error", "info", "success", "warning"].includes(node.expression.name.text)
    ) {
      const firstArgument = node.arguments[0];
      if (
        firstArgument &&
        (ts.isStringLiteral(firstArgument) || ts.isNoSubstitutionTemplateLiteral(firstArgument))
      ) {
        record(firstArgument, firstArgument.text);
      }
    }

    ts.forEachChild(node, visit);
  };

  visit(source);
  return violations;
}

function literalVisibleText(
  node: ts.StringLiteral | ts.NoSubstitutionTemplateLiteral | ts.TemplateExpression,
) {
  if (ts.isTemplateExpression(node)) {
    return node.head.text + node.templateSpans.map((span) => span.literal.text).join("");
  }
  return node.text;
}

function isVisibleJsxExpression(node: ts.Node) {
  const parent = node.parent;
  if (!parent || !ts.isJsxExpression(parent)) return false;
  return !parent.parent || !ts.isJsxAttribute(parent.parent);
}

function isVisibleValueAttribute(node: ts.JsxAttribute, source: ts.SourceFile): boolean {
  const parent = node.parent?.parent;
  if (!parent || (!ts.isJsxOpeningElement(parent) && !ts.isJsxSelfClosingElement(parent))) {
    return false;
  }
  return parent.tagName.getText(source).startsWith("Input");
}
