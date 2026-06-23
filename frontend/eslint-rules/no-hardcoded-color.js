/**
 * ESLint 规则：禁止硬编码颜色（必须用 Design Token）。
 *
 * 强制 ADR-0003。
 *
 * 检测：
 *   - JSX style 属性中的颜色值
 *   - 字符串字面量含 hex/rgb/rgba/hsl
 *
 * 例外：
 *   - frontend/src/shared/config/theme.ts 是品牌色 token 定义文件
 *
 * 错误示例：
 *   <div style={{ color: '#1890ff' }}>
 *   <Button style={{ background: 'rgb(255,0,0)' }}>
 *
 * 正确示例：
 *   <div style={{ color: 'var(--mk-primary)' }}>
 *   <Button className="mk-button-danger">
 */

const COLOR_PATTERNS = [
  /#[0-9a-fA-F]{3}(?![0-9a-fA-F])/,   // #abc
  /#[0-9a-fA-F]{6}(?![0-9a-fA-F])/,   // #aabbcc
  /#[0-9a-fA-F]{8}(?![0-9a-fA-F])/,   // #aabbccdd（带 alpha）
  /\brgb\s*\(/,                        // rgb(...)
  /\brgba\s*\(/,                       // rgba(...)
  /\bhsl\s*\(/,                        // hsl(...)
  /\bhsla\s*\(/,                       // hsla(...)
];

function isHardcodedColor(str) {
  if (typeof str !== 'string') return false;
  return COLOR_PATTERNS.some(p => p.test(str));
}

function isTokenFile(filename) {
  const normalized = filename.replaceAll('\\', '/');
  return normalized.includes('tokens.css') ||
    normalized.includes('tokens.ts') ||
    normalized.endsWith('src/shared/config/theme.ts');
}

function isInsideJsxStyleObject(node) {
  const property = node.parent;
  const objectExpression = property?.parent;
  const expressionContainer = objectExpression?.parent;
  const attribute = expressionContainer?.parent;
  return property?.type === 'Property' &&
    objectExpression?.type === 'ObjectExpression' &&
    expressionContainer?.type === 'JSXExpressionContainer' &&
    attribute?.type === 'JSXAttribute' &&
    attribute.name?.name === 'style';
}

export default {
  meta: {
    type: 'problem',
    docs: {
      description: '禁止硬编码颜色，必须使用 Design Token',
      recommended: true,
    },
    messages: {
      hardcodedColor:
        '禁止硬编码颜色 "{{value}}"。请改用 CSS 变量如 var(--mk-primary)。' +
        '完整 token 见 docs/CONSTITUTION.md 与 docs/EXPERIENCE_CONTRACT.md。',
    },
    schema: [],
  },

  create(context) {
    const filename = context.getFilename();

    // token 定义文件本身允许
    if (isTokenFile(filename)) return {};

    function check(node, value) {
      if (isHardcodedColor(value)) {
        const found = COLOR_PATTERNS.find(p => p.test(value));
        const match = value.match(found);
        context.report({
          node,
          messageId: 'hardcodedColor',
          data: { value: match ? match[0] : value },
        });
      }
    }

    return {
      // 检测 JSX 内联样式
      JSXAttribute(node) {
        if (node.name.name !== 'style') return;
        if (!node.value || node.value.type !== 'JSXExpressionContainer') return;
        const expr = node.value.expression;
        if (expr.type !== 'ObjectExpression') return;
        for (const prop of expr.properties) {
          if (prop.type !== 'Property') continue;
          if (prop.value.type === 'Literal') {
            check(prop.value, prop.value.value);
          }
        }
      },

      // 检测普通字符串字面量
      Literal(node) {
        if (typeof node.value !== 'string') return;
        if (isInsideJsxStyleObject(node)) return;
        // 跳过 import / require 路径
        const parent = node.parent;
        if (
          parent &&
          (parent.type === 'ImportDeclaration' ||
            (parent.type === 'CallExpression' &&
              parent.callee.name === 'require'))
        ) {
          return;
        }
        check(node, node.value);
      },

      // 检测模板字符串
      TemplateLiteral(node) {
        for (const q of node.quasis) {
          check(node, q.value.raw);
        }
      },
    };
  },
};
