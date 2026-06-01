import { RuleTester } from "eslint";
import test from "node:test";

import rule from "./no-page-mock.js";

RuleTester.setDefaultConfig({
  languageOptions: {
    ecmaVersion: 2022,
    sourceType: "module",
    parserOptions: { ecmaFeatures: { jsx: true } },
  },
  linterOptions: {
    reportUnusedDisableDirectives: "off",
  },
  plugins: {
    medkernel: {
      rules: {
        "no-page-mock": rule,
      },
    },
  },
});

const tester = new RuleTester();

test("medkernel/no-page-mock blocks production mock and authenticity debt", () => {
  tester.run("no-page-mock", rule, {
    valid: [
      {
        filename: "/repo/frontend/src/pages/Login.test.tsx",
        code: 'import MockAdapter from "axios-mock-adapter"; const value = Math.random();',
      },
      {
        filename: "/repo/frontend/src/features/Sample.stories.tsx",
        code: "const DEMO_ROWS = [{ id: 1 }]; export const Basic = {};",
      },
      {
        filename: "/repo/frontend/src/pages/GoodPage.tsx",
        code: 'export function GoodPage() { return <section className="mk-page">真实接口返回后展示</section>; }',
      },
    ],
    invalid: [
      {
        filename: "/repo/frontend/src/pages/BadImport.tsx",
        code: 'import MockAdapter from "axios-mock-adapter"; export function BadImport() { return null; }',
        errors: [{ messageId: "mockImport" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadDisable.tsx",
        code: '/* eslint-disable medkernel/no-page-mock */ export function BadDisable() { return null; }',
        errors: [{ messageId: "disableBypass" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadFixture.tsx",
        code: 'import { rows } from "../fixtures/patient"; export function BadFixture() { return rows.length; }',
        errors: [{ messageId: "mockImport" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadWrapped.tsx",
        code: "function getMokRows() { return [{ id: 1 }]; } export function BadWrapped() { return getMokRows().length; }",
        errors: [{ messageId: "wrappedMock" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadMedical.tsx",
        code: 'export function BadMedical() { return <input defaultValue="高血压 DRUG-001 I10" />; }',
        errors: [{ messageId: "medicalConstant" }],
      },
      {
        filename: "/repo/frontend/src/pages/BadTechnical.tsx",
        code: 'export function BadTechnical() { return <pre className="font-mono">{JSON.stringify({ traceId: "t" })}</pre>; }',
        errors: [{ messageId: "technicalObject" }, { messageId: "technicalObject" }],
      },
    ],
  });
});
