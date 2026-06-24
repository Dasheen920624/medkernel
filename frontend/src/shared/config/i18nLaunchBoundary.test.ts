import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const packageJson = JSON.parse(readFileSync(resolve(process.cwd(), "package.json"), "utf8")) as {
  dependencies?: Record<string, string>;
  devDependencies?: Record<string, string>;
};

describe("i18n launch boundary", () => {
  it("does not ship unused multilingual runtime dependencies in the Chinese launch candidate", () => {
    const declaredRuntimeDependencies = Object.keys(packageJson.dependencies ?? {});

    expect(declaredRuntimeDependencies).not.toEqual(
      expect.arrayContaining(["i18next", "i18next-browser-languagedetector", "react-i18next"]),
    );
  });
});
