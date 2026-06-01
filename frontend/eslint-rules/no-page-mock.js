/**
 * ESLint 规则：禁止生产页面 / 功能组件中出现 mock 假闭环、医学常量和技术对象裸露。
 *
 * 上下文：GA-ENG-BASE-09 净化把所有内联 MOCK / DEPTS / ITEMS / LINKS / PROVIDERS 等
 * 数组常量从业务页面与功能组件中清出；新代码必须改走 API hook，缺失时由
 * PageState / PageShell 六态诚实展示空、错误、无权限或部分成功状态。
 *
 * 触发位置：
 *   - mock / fixture / MockAdapter import
 *   - eslint-disable medkernel/* 绕门禁注释
 *   - 形如 `const MOCK = [{ ... }]` 的 SHOUTY-CASE 对象数组常量
 *   - 形如 `getMokRows()` / `demoRows` 的包装式本地假数据
 *   - 医学病种 / 药品 / 编码硬编码
 *   - `font-mono` 与 `<pre>{JSON.stringify(...)}</pre>` 技术对象裸露
 *
 * 错误等级：error（与 medkernel/no-page-mock 一起锁住视觉债与假闭环不回潮）
 *
 * 范围：
 *   仅作用于 `src/pages/**`、`src/features/**` 与 `src/widgets/**` 下的 .ts / .tsx 文件。
 *   测试文件、Storybook、src/test、src/mocks 等不在控制范围内。
 */

const APPLICABLE_PATH = /\/src\/(?:pages|features|widgets)\/.+\.(?:tsx|ts)$/;
const ALLOWLIST_PATH =
  /\.(?:test|spec|stories)\.(?:tsx|ts)$|\/src\/(?:test|mocks)\//;
const NAME_PATTERN = /^[A-Z][A-Z0-9_]*$/;
const MOCK_IMPORT_PATTERN = /\b(?:mock|mocks|fixture|fixtures)\b|MockAdapter/i;
const WRAPPED_MOCK_NAME_PATTERN = /(?:mock|mok|fixture|demo|dem)/i;
const MEDICAL_CONSTANT_PATTERN =
  /高血压|糖尿病|DRUG-001|DRUG-CODE|DX-CODE|PT-CAP-01|PKG-COP-001|J44|I10|E11|J18|肺炎|心梗|脑卒中|卒中|急性脑梗死|阿替普酶|静脉溶栓|社区获得性|抗感染化疗|低分子肝素|强力阿司匹林|老年患者/;

function normalizedFilename(context) {
  return (context.filename ?? context.getFilename?.() ?? '').replaceAll('\\', '/');
}

function isObjectArray(node) {
  return node?.type === 'ArrayExpression' &&
    node.elements.some((element) => element?.type === 'ObjectExpression');
}

function isObjectData(node) {
  return node?.type === 'ObjectExpression' || isObjectArray(node);
}

function returnsObjectData(functionNode) {
  if (!functionNode) return false;
  if (functionNode.type === 'ArrowFunctionExpression' && functionNode.body.type !== 'BlockStatement') {
    return isObjectData(functionNode.body);
  }
  if (!functionNode.body || functionNode.body.type !== 'BlockStatement') return false;
  return functionNode.body.body.some((statement) =>
    statement.type === 'ReturnStatement' && isObjectData(statement.argument));
}

function isMockCall(node) {
  if (node.callee?.type === 'Identifier') {
    return node.callee.name.toLowerCase() === 'mock';
  }
  if (node.callee?.type !== 'MemberExpression') return false;
  const objectName = node.callee.object?.name;
  const propertyName = node.callee.property?.name;
  return (objectName === 'vi' || objectName === 'jest') && propertyName === 'mock';
}

function isPreElement(node) {
  const parent = node.parent;
  if (parent?.type !== 'JSXElement') return false;
  const name = parent.openingElement?.name;
  return name?.type === 'JSXIdentifier' && name.name === 'pre';
}

/** @type {import('eslint').Rule.RuleModule} */
export default {
  meta: {
    type: 'problem',
    docs: {
      description:
        '禁止 src/pages、src/features 与 src/widgets 中出现页面级 mock / 硬编码数据数组常量；改走 API hook + PageState 六态',
      recommended: true,
    },
    messages: {
      noPageMock:
        '业务页 / 功能组件禁止内联 mock 或硬编码数据数组常量（如 MOCK/DEPTS/ITEMS/LINKS/PROVIDERS）。' +
        '请改走 API hook，缺失时使用 PageState / PageShell 六态诚实占位（GA-ENG-BASE-09）。',
      mockImport:
        '前端生产路径禁止引入 mock / mocks / fixture / fixtures / MockAdapter。',
      disableBypass:
        '前端生产路径禁止用 eslint-disable 关闭 medkernel 真实性门禁。',
      wrappedMock:
        '前端生产路径禁止用 mock/mok/demo/dem/fixture 命名包装本地假数据。',
      medicalConstant:
        '前端生产路径禁止写死疾病、药品、编码等医学常量。',
      technicalObject:
        '客户面默认视图禁止裸露 JSON / font-mono 等技术对象。',
    },
    schema: [],
  },

  create(context) {
    const filename = normalizedFilename(context);
    if (!APPLICABLE_PATH.test(filename) || ALLOWLIST_PATH.test(filename)) {
      return {};
    }
    const sourceCode = context.sourceCode ?? context.getSourceCode();

    function checkMedicalString(node, value) {
      if (typeof value === 'string' && MEDICAL_CONSTANT_PATTERN.test(value)) {
        context.report({ node, messageId: 'medicalConstant' });
      }
    }

    return {
      Program() {
        for (const comment of sourceCode.getAllComments()) {
          if (/eslint-disable(?:-next-line|-line)?\s+[^]*medkernel\//.test(comment.value)) {
            context.report({ loc: comment.loc, messageId: 'disableBypass' });
          }
        }
      },

      ImportDeclaration(node) {
        const source = String(node.source?.value ?? '');
        const hasBadSource = MOCK_IMPORT_PATTERN.test(source);
        const hasBadSpecifier = node.specifiers.some((specifier) =>
          MOCK_IMPORT_PATTERN.test(specifier.local?.name ?? '') ||
          MOCK_IMPORT_PATTERN.test(specifier.imported?.name ?? ''));
        if (hasBadSource || hasBadSpecifier) {
          context.report({ node, messageId: 'mockImport' });
        }
      },

      CallExpression(node) {
        if (isMockCall(node)) {
          context.report({ node, messageId: 'mockImport' });
        }
      },

      VariableDeclarator(node) {
        if (node.id.type !== 'Identifier') return;
        if (WRAPPED_MOCK_NAME_PATTERN.test(node.id.name) &&
          (isObjectData(node.init) || returnsObjectData(node.init))) {
          context.report({ node, messageId: 'wrappedMock' });
          return;
        }
        // 仅拦截 SHOUTY-CASE 命名的硬编码数据数组常量（MOCK/DEPTS/ITEMS/LINKS/PROVIDERS 等）。
        // antd 的 const columns = [{...}] / const items = [...] 等驼峰命名结构不在拦截范围，
        // 避免误伤合法页面被迫整文件 eslint-disable，从而架空本门禁（修复 R1 门禁失效）。
        if (!NAME_PATTERN.test(node.id.name)) return;
        if (!isObjectArray(node.init)) return;
        context.report({ node, messageId: 'noPageMock' });
      },

      FunctionDeclaration(node) {
        if (!node.id?.name || !WRAPPED_MOCK_NAME_PATTERN.test(node.id.name)) return;
        if (returnsObjectData(node)) {
          context.report({ node, messageId: 'wrappedMock' });
        }
      },

      JSXAttribute(node) {
        if (node.name?.name !== 'className') return;
        if (node.value?.type === 'Literal') {
          if (String(node.value.value ?? '').split(/\s+/).includes('font-mono')) {
            context.report({ node, messageId: 'technicalObject' });
          }
          checkMedicalString(node.value, node.value.value);
        }
      },

      JSXExpressionContainer(node) {
        if (!isPreElement(node)) return;
        const text = sourceCode.getText(node.expression);
        if (/JSON\.stringify\s*\(/.test(text)) {
          context.report({ node, messageId: 'technicalObject' });
        }
      },

      Literal(node) {
        checkMedicalString(node, node.value);
      },

      TemplateElement(node) {
        checkMedicalString(node, node.value?.raw);
      },
    };
  },
};
