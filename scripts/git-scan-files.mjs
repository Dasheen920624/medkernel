import { execFileSync } from "node:child_process";

function git(root, args) {
  return execFileSync("git", args, { cwd: root, encoding: "utf8" }).trim();
}

function lines(output) {
  return output ? output.split(/\r?\n/).filter(Boolean) : [];
}

function pathspecArgs(pathspecs) {
  return pathspecs.length > 0 ? ["--", ...pathspecs] : [];
}

function collect(target, output) {
  lines(output).forEach((file) => target.add(file.replaceAll("\\", "/")));
}

export function listTrackedFiles(root, pathspecs = []) {
  return lines(git(root, ["ls-files", ...pathspecArgs(pathspecs)])).sort();
}

export function listUntrackedFiles(root, pathspecs = []) {
  return lines(
    git(root, [
      "ls-files",
      "--others",
      "--exclude-standard",
      ...pathspecArgs(pathspecs),
    ]),
  ).sort();
}

export function listAllCurrentFiles(root, pathspecs = []) {
  return [
    ...new Set([
      ...listTrackedFiles(root, pathspecs),
      ...listUntrackedFiles(root, pathspecs),
    ]),
  ].sort();
}

export function listChangedFiles(root, base, pathspecs = []) {
  const files = new Set();
  try {
    const mergeBase = git(root, ["merge-base", base, "HEAD"]);
    collect(
      files,
      git(root, [
        "diff",
        "--name-only",
        "--diff-filter=ACMR",
        `${mergeBase}...HEAD`,
        ...pathspecArgs(pathspecs),
      ]),
    );
  } catch {
    // 无可用基线时仍继续扫描当前工作区。
  }

  collect(
    files,
    git(root, [
      "diff",
      "--name-only",
      "--diff-filter=ACMR",
      "HEAD",
      ...pathspecArgs(pathspecs),
    ]),
  );
  listUntrackedFiles(root, pathspecs).forEach((file) => files.add(file));
  return [...files].sort();
}
