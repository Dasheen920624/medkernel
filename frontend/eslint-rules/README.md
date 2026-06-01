# MedKernel 自定义 ESLint 规则

本目录是项目自定义的 ESLint 规则，配合 `frontend/eslint.config.js` 使用。

## 规则清单

| 规则名                               | 类别   | 严重度 | 说明                                                                            |
| ------------------------------------ | ------ | :----: | ------------------------------------------------------------------------------- |
| `no-hardcoded-color.js`              | 视觉   | error  | 禁止硬编码颜色（必须用 token）                                                  |
| `no-inline-style.js`                 | 视觉   | error  | 禁止 JSX 内联样式；必须抽取到 CSS Modules 或统一 `mk-*` 样式类                  |
| `no-page-mock.js`                    | 真实性 | error  | 禁止生产页面 / 功能 / 组件路径引入 mock、包装假数据、写死医学常量和裸露技术对象 |
| `require-source-info-for-medical.js` | 业务   |  warn  | 含医学语义的组件必须有 `<SourceInfo>`                                           |
| `forbid-deprecated-naming.js`        | 命名   | error  | 禁用重启前品牌、路径和环境变量标识                                              |

内置 ESLint 规则还会阻断生产代码中的直接 `console.*`、直接 `localStorage/sessionStorage` 访问，以及组件内 axios 直连。

## 集成方式

在 `frontend/eslint.config.js`：

```js
import noHardcodedColor from "./eslint-rules/no-hardcoded-color.js";
import requireSourceInfo from "./eslint-rules/require-source-info-for-medical.js";
import forbidDeprecatedNaming from "./eslint-rules/forbid-deprecated-naming.js";
import noInlineStyle from "./eslint-rules/no-inline-style.js";
import noPageMock from "./eslint-rules/no-page-mock.js";

export default [
  // ... 其它配置
  {
    plugins: {
      medkernel: {
        rules: {
          "no-hardcoded-color": noHardcodedColor,
          "require-source-info-for-medical": requireSourceInfo,
          "forbid-deprecated-naming": forbidDeprecatedNaming,
          "no-inline-style": noInlineStyle,
          "no-page-mock": noPageMock,
        },
      },
    },
    rules: {
      "medkernel/no-hardcoded-color": "error",
      "medkernel/require-source-info-for-medical": "warn",
      "medkernel/forbid-deprecated-naming": "error",
      "medkernel/no-inline-style": "error",
      "medkernel/no-page-mock": "error",
    },
  },
];
```

## `no-page-mock` 范围

`no-page-mock` 作用于 `src/pages/**`、`src/features/**`、`src/widgets/**` 生产 `.ts/.tsx` 文件，阻断 mock/fixture/MockAdapter 引入、`vi.mock` / `jest.mock`、`mok/demo/dem/fixture` 包装假数据、写死医学常量、`font-mono` 和 `<pre>{JSON.stringify(...)}</pre>` 技术对象裸露，以及 `eslint-disable medkernel/*` 绕门禁注释。

白名单：`*.test.*`、`*.spec.*`、`*.stories.*`、`src/test/**`、`src/mocks/**`。主题 token 文件继续由 `no-hardcoded-color` 规则的白名单控制。

## 自检命令

```bash
cd frontend
npm run lint                    # 跑全部规则
npm run test:lint-rules         # 跑自定义规则与 stylelint 配置测试
npm run lint -- --rule medkernel/no-hardcoded-color  # 跑单条
```

## 新增规则流程

1. 在本目录新建 `规则名.js`
2. 实现 ESLint Rule API（`create(context) { return { ... } }`）
3. 在 `eslint.config.js` 注册
4. 在本 README 表格登记
5. 配套写单元测试 `eslint-rules/规则名.test.js`
