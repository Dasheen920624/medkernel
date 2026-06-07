import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";

import { listAllCurrentFiles, listChangedFiles } from "./git-scan-files.mjs";

function git(root, ...args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8" }).trim();
}

async function write(root, file, content) {
  const fullPath = join(root, file);
  await mkdir(dirname(fullPath), { recursive: true });
  await writeFile(fullPath, content, "utf8");
}

test("文件发现同时覆盖分支提交、工作区修改和未跟踪文件", async () => {
  const root = await mkdtemp(join(tmpdir(), "medkernel-git-scan-"));
  try {
    git(root, "init");
    git(root, "config", "user.email", "test@medkernel.local");
    git(root, "config", "user.name", "MedKernel Test");
    await write(root, "tracked.txt", "base\n");
    await write(root, "committed.txt", "base\n");
    git(root, "add", ".");
    git(root, "commit", "-m", "基线");
    const base = git(root, "rev-parse", "HEAD");

    await write(root, "committed.txt", "branch\n");
    git(root, "add", "committed.txt");
    git(root, "commit", "-m", "分支改动");
    await write(root, "tracked.txt", "working tree\n");
    await write(root, "untracked/new.txt", "new\n");

    assert.deepEqual(listChangedFiles(root, base), [
      "committed.txt",
      "tracked.txt",
      "untracked/new.txt",
    ]);
    assert.deepEqual(listAllCurrentFiles(root), [
      "committed.txt",
      "tracked.txt",
      "untracked/new.txt",
    ]);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test("路径过滤只返回指定目录内的当前文件", async () => {
  const root = await mkdtemp(join(tmpdir(), "medkernel-git-scan-"));
  try {
    git(root, "init");
    git(root, "config", "user.email", "test@medkernel.local");
    git(root, "config", "user.name", "MedKernel Test");
    await write(root, "backend/Main.java", "class Main {}\n");
    await write(root, "frontend/App.tsx", "export const App = null;\n");
    git(root, "add", ".");
    git(root, "commit", "-m", "基线");
    await write(root, "backend/New.java", "class New {}\n");

    assert.deepEqual(listAllCurrentFiles(root, ["backend"]), [
      "backend/Main.java",
      "backend/New.java",
    ]);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
