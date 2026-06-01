import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const FRONTEND_DIR = dirname(fileURLToPath(import.meta.url));

async function withCss(content, run) {
  const dir = await mkdtemp(join(tmpdir(), "medkernel-stylelint-"));
  const file = join(dir, "Bad.module.css");
  try {
    await writeFile(file, content, "utf8");
    return await run(file);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

function runStylelint(file) {
  return execFileSync("npx", ["stylelint", "--config", "stylelint.config.mjs", file], {
    cwd: FRONTEND_DIR,
    encoding: "utf8",
    stdio: "pipe",
  });
}

test("stylelint blocks hardcoded color and px tokens", async () => {
  await withCss(".bad { color: #1565c0; border-radius: 8px; font-size: 14px; }", (file) => {
    assert.throws(() => runStylelint(file));
  });
});

test("stylelint allows token variables", async () => {
  await withCss(
    ".good { color: var(--ant-color-text); border-radius: var(--ant-border-radius); font-size: var(--ant-font-size); }",
    (file) => {
      assert.doesNotThrow(() => runStylelint(file));
    },
  );
});
