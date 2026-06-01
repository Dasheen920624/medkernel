import { RuleTester } from "eslint";
import test from "node:test";

import noHardcodedColor from "./no-hardcoded-color.js";
import noInlineStyle from "./no-inline-style.js";

RuleTester.setDefaultConfig({
  languageOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
    parserOptions: { ecmaFeatures: { jsx: true } },
  },
});

const tester = new RuleTester();

test("medkernel visual rules block inline hex styles in production JSX", () => {
  tester.run("no-hardcoded-color", noHardcodedColor, {
    valid: [
      {
        filename: "/repo/frontend/src/shared/config/theme.ts",
        code: 'export const brand = "#1565c0";',
      },
      {
        filename: "/repo/frontend/src/pages/Good.tsx",
        code: 'export function Good() { return <div className="mk-page" />; }',
      },
    ],
    invalid: [
      {
        filename: "/repo/frontend/src/pages/BadColor.tsx",
        code: 'export function BadColor() { return <div style={{ color: "#1565c0" }} />; }',
        errors: [{ messageId: "hardcodedColor" }],
      },
    ],
  });

  tester.run("no-inline-style", noInlineStyle, {
    valid: [
      {
        filename: "/repo/frontend/src/pages/Good.tsx",
        code: 'export function Good() { return <div className="mk-page" />; }',
      },
    ],
    invalid: [
      {
        filename: "/repo/frontend/src/pages/BadInline.tsx",
        code: 'export function BadInline() { return <div style={{ color: "var(--ant-color-text)" }} />; }',
        errors: [{ messageId: "noInlineStyle" }],
      },
    ],
  });
});
