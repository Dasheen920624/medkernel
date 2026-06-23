// 前端 ESLint flat config（ESLint 9 风格）。
// 与 docs/CONSTITUTION.md 和 docs/EXPERIENCE_CONTRACT.md 对齐。

import js from "@eslint/js";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import globals from "globals";

// MedKernel 自定义规则（产品宪法 + 产品体验固定规范）。
import noHardcodedColor from "./eslint-rules/no-hardcoded-color.js";
import requireSourceInfo from "./eslint-rules/require-source-info-for-medical.js";
import forbidDeprecatedNaming from "./eslint-rules/forbid-deprecated-naming.js";
import noInlineStyle from "./eslint-rules/no-inline-style.js";
import noPageMock from "./eslint-rules/no-page-mock.js";

export default tseslint.config(
  {
    ignores: ["dist", "node_modules", "coverage", "public", "**/*.config.{js,ts}"],
  },
  {
    files: ["src/**/*.{ts,tsx}"],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: { ...globals.browser, ...globals.es2022 },
      parserOptions: {
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
      // MedKernel 自定义规则集
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
      // === TypeScript ===
      "@typescript-eslint/no-explicit-any": ["warn", { ignoreRestArgs: true }],
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
      ],
      "@typescript-eslint/consistent-type-imports": [
        "warn",
        { prefer: "type-imports" },
      ],
      "@typescript-eslint/no-non-null-assertion": "warn",
      "@typescript-eslint/ban-ts-comment": [
        "error",
        {
          "ts-ignore": true,        // 完全禁止
          "ts-expect-error": "allow-with-description",
          minimumDescriptionLength: 5,
        },
      ],

      // === React Hooks ===
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],

      // === 反模式（产品体验固定规范 §12）===
      "no-console": "error",
      "no-debugger": "error",
      "no-alert": "error",
      "no-eval": "error",
      "no-implied-eval": "error",
      "no-restricted-syntax": [
        "error",
        {
          selector:
            "MemberExpression[object.name=/^(localStorage|sessionStorage)$/], MemberExpression[object.property.name=/^(localStorage|sessionStorage)$/]",
          message:
            "禁止在生产代码直接访问 localStorage/sessionStorage。UI 偏好请使用 @/shared/lib/browserStorage，敏感数据禁止写入浏览器存储。",
        },
      ],
      "no-restricted-imports": [
        "error",
        {
          paths: [
            {
              name: "axios",
              importNames: ["default"],
              message:
                "禁止直接 import axios 默认导出。请使用 @/shared/api/client。",
            },
          ],
        },
      ],

      // === 可读性 ===
      eqeqeq: ["error", "always"],
      "prefer-const": "error",
      "no-var": "error",
      "object-shorthand": "warn",
      "no-nested-ternary": "warn",

      // === MedKernel 自定义规则（产品宪法 + 体验门禁） ===
      "medkernel/no-hardcoded-color": "error",
      "medkernel/require-source-info-for-medical": "warn",
      "medkernel/forbid-deprecated-naming": "error",
      // inline style 已归零；新代码必须使用 CSS Modules 或统一 mk-* 样式类。
      "medkernel/no-inline-style": "error",
      // GA-ENG-BASE-09：业务页 / 功能组件不得新增 MOCK/DEPTS/ITEMS/LINKS/PROVIDERS 等内联硬编码数组常量。
      "medkernel/no-page-mock": "error",
    },
  },
  {
    files: ["src/**/*.test.{ts,tsx}", "src/test/**/*.{ts,tsx}", "src/mocks/**/*.{ts,tsx}"],
    languageOptions: {
      globals: { ...globals.browser, ...globals.node, ...globals.es2022 },
    },
    rules: {
      "no-console": "off",
      "no-restricted-syntax": "off",
      "@typescript-eslint/no-explicit-any": "off",
    },
  },
  {
    files: ["src/shared/lib/browserStorage.ts"],
    rules: {
      "no-restricted-syntax": "off",
    },
  },
  {
    files: ["src/shared/api/client.ts"],
    rules: {
      "no-restricted-imports": "off",
    },
  },
);
