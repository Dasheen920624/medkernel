/**
 * ESLint 规则：含医学/医保/质控/规则语义的组件必须使用 SourceInfo 组件。
 *
 * 强制 ADR-0004。
 *
 * 检测：
 *   - 文件路径含 medical / rule / quality / insurance / pathway / aik / graph 关键词的 .tsx 文件
 *   - 如果文件渲染了"规则名 / 路径名 / 知识 / 医保 / 质控"等业务对象，必须包含 <SourceInfo /> 组件
 *
 * 检测方式（启发式）：
 *   1. 文件路径属于上述模块
 *   2. JSX 中出现了"ruleName / pathwayName / knowledgeTitle / insuranceItem"等业务字段
 *   3. 但未找到 SourceInfo / <SourceInfo
 *   → 报警
 *
 * 错误示例（在 pages/rule/RuleDetail.tsx）：
 *   <Card>
 *     <h2>{rule.name}</h2>
 *     <p>规则内容: {rule.content}</p>
 *   </Card>
 *
 * 正确示例：
 *   <Card>
 *     <h2>{rule.name}</h2>
 *     <p>规则内容: {rule.content}</p>
 *     <SourceInfo source={rule.source} review={rule.review} version={rule.version} />
 *   </Card>
 */

const MEDICAL_DOMAINS = [
  "rule",
  "quality",
  "insurance",
  "pathway",
  "aik",
  "graph",
  "medical",
  "cdss",
];

const MEDICAL_FIELD_PATTERNS = [
  /\bruleName\b/,
  /\brule\.name\b/,
  /\bpathwayName\b/,
  /\bpathway\.name\b/,
  /\bknowledgeTitle\b/,
  /\bdiagnos[ie]s\b/,
  /\binsuranceItem\b/,
  /\bmedicalGuideline\b/,
];

export default {
  meta: {
    type: "suggestion",
    docs: {
      description: "医学/医保/质控/规则相关页面必须使用 <SourceInfo> 组件展示来源",
      recommended: true,
    },
    messages: {
      missingSourceInfo:
        '文件 "{{filename}}" 渲染了医学/规则内容（命中字段：{{field}}），但未使用 <SourceInfo> 组件。' +
        '医学内容必须有来源（ADR-0004）。请引入 import { SourceInfo } from "@/shared/ui/SourceInfo"。',
    },
    schema: [],
  },

  create(context) {
    const filename = context.getFilename();
    const lowerName = filename.toLowerCase();

    // 1. 只检测医学相关文件
    const isMedicalFile = MEDICAL_DOMAINS.some(
      (d) => lowerName.includes(`/${d}/`) || lowerName.includes(`\\${d}\\`),
    );
    if (!isMedicalFile) return {};

    // 2. 跳过测试/样式/类型文件
    if (/\.(test|spec|types|stories)\.(t|j)sx?$/.test(filename)) return {};

    let hasSourceInfo = false;
    let medicalFieldHit = null;

    return {
      // 检测是否引入了 SourceInfo
      ImportDeclaration(node) {
        const src = node.source.value;
        if (typeof src !== "string") return;
        if (src.includes("SourceInfo")) {
          hasSourceInfo = true;
        }
        for (const spec of node.specifiers) {
          if (spec.imported && spec.imported.name === "SourceInfo") {
            hasSourceInfo = true;
          }
        }
      },

      // 检测是否使用了 <SourceInfo>
      JSXElement(node) {
        const open = node.openingElement;
        if (open && open.name && open.name.name === "SourceInfo") {
          hasSourceInfo = true;
        }
      },

      // 在源文件全文中查找医学字段
      "Program:exit"(node) {
        if (hasSourceInfo) return;
        const src = context.getSourceCode().getText();
        for (const pattern of MEDICAL_FIELD_PATTERNS) {
          const m = src.match(pattern);
          if (m) {
            medicalFieldHit = m[0];
            break;
          }
        }
        if (medicalFieldHit) {
          context.report({
            node,
            messageId: "missingSourceInfo",
            data: {
              filename: filename.split(/[\\/]/).pop(),
              field: medicalFieldHit,
            },
          });
        }
      },
    };
  },
};
