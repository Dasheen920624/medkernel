/**
 * ESLint 规则：禁止 JSX 内联 style={{}}。
 *
 * 上下文：产品宪法和体验契约要求页面使用统一 token、CSS Modules 和组件外壳。
 * `style={{ ... }}` 是 UI 风格杂乱的核心来源。
 * 新代码必须把静态样式放进同名 `.module.css` 或统一 `mk-*` 样式类。
 *
 * 触发位置：JSX 属性 style={...}
 * 错误等级：error（生产代码 inline style 已归零，后续不得新增）
 *
 * 豁免：
 *   1) 仅当 style 值是变量引用且名字含 dynamic / motion / transform，可以行尾 `// eslint-disable-next-line medkernel/no-inline-style`
 *   2) 测试 / mock 文件免（eslint.config.js overrides 已配）
 *   3) embed/ 嵌入器（受集成方约束，必要时允许内联）
 *
 * 正确做法：
 *   // 错误
 *   <div style={{ padding: 16, marginBottom: 24 }}>
 *
 *   // 正确（同名 module）
 *   import styles from "./MyPage.module.css";
 *   <div className={styles.container}>
 *
 *   // 正确（必须动态）
 *   // eslint-disable-next-line medkernel/no-inline-style
 *   <div style={{ transform: `rotate(${deg}deg)` }}>
 */

export default {
  meta: {
    type: 'suggestion',
    docs: {
      description: '禁止 JSX 内联 style={{}}，鼓励使用 CSS Modules / tokens',
      recommended: true,
    },
    messages: {
      noInlineStyle:
        'JSX 内联 style={{}} 被禁止。' +
        '请把静态样式抽取到同名 `.module.css`（CSS Modules）或使用 `var(--mk-*)` 类名。' +
        '若必须动态（transform / motion），请添加 `// eslint-disable-next-line medkernel/no-inline-style` 并说明理由。' +
        '详见 docs/EXPERIENCE_CONTRACT.md。',
    },
    schema: [],
  },

  create(context) {
    return {
      JSXAttribute(node) {
        if (node.name && node.name.name === 'style' && node.value) {
          // 仅当值是对象字面量时报错（{{ ... }}）
          if (
            node.value.type === 'JSXExpressionContainer' &&
            node.value.expression &&
            node.value.expression.type === 'ObjectExpression'
          ) {
            context.report({
              node,
              messageId: 'noInlineStyle',
            });
          }
        }
      },
    };
  },
};
