#!/usr/bin/env node

import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const repositoryRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const sourceRelativePath =
  "docs/contracts/product/product-entry-catalog.v1.json";
const backendRelativePath =
  "medkernel-backend/src/main/resources/catalog/menu-permission-catalog.generated.json";
const frontendRelativePath =
  "frontend/src/shared/contracts/productEntryCatalog.generated.ts";
const sourcePath = path.join(repositoryRoot, sourceRelativePath);
const checkOnly = process.argv.includes("--check");

const unknownArguments = process.argv
  .slice(2)
  .filter((argument) => argument !== "--check");
if (unknownArguments.length > 0) {
  throw new Error(`不支持的参数：${unknownArguments.join(", ")}`);
}

const sourceBytes = readFileSync(sourcePath);
const sourceSha256 = createHash("sha256").update(sourceBytes).digest("hex");
const catalog = JSON.parse(sourceBytes.toString("utf8"));
validateCatalog(catalog);

const artifacts = [
  {
    relativePath: backendRelativePath,
    content: renderBackendResource(catalog, sourceSha256),
  },
  {
    relativePath: frontendRelativePath,
    content: await renderFrontendContract(catalog, sourceSha256),
  },
];

if (checkOnly) {
  const missing = [];
  const drifted = [];
  for (const artifact of artifacts) {
    const targetPath = path.join(repositoryRoot, artifact.relativePath);
    if (!existsSync(targetPath)) {
      missing.push(artifact.relativePath);
    } else if (readFileSync(targetPath, "utf8") !== artifact.content) {
      drifted.push(artifact.relativePath);
    }
  }
  if (missing.length > 0 || drifted.length > 0) {
    const details = [
      ...missing.map((relativePath) => `生成消费者缺失：${relativePath}`),
      ...drifted.map((relativePath) => `生成消费者漂移：${relativePath}`),
    ];
    throw new Error(
      `${details.join("\n")}\n请执行 node scripts/release/generate-product-entry-consumers.mjs`,
    );
  }
  console.log(`VERIFIED product-entry-consumers sourceSha256=${sourceSha256}`);
} else {
  for (const artifact of artifacts) {
    const targetPath = path.join(repositoryRoot, artifact.relativePath);
    writeAtomicallyIfChanged(targetPath, artifact.content);
    console.log(`GENERATED ${artifact.relativePath}`);
  }
}

function validateCatalog(value) {
  if (
    value?.schemaVersion !== "1.0.0" ||
    value?.catalogId !== "medkernel-product-entry-catalog"
  ) {
    throw new Error("产品入口合同版本或目录标识无效");
  }
  if (!Array.isArray(value.entries) || value.entries.length === 0) {
    throw new Error("产品入口合同 entries 不能为空");
  }
  const entryCodes = new Set();
  for (const entry of value.entries) {
    if (
      typeof entry.entryCode !== "string" ||
      entryCodes.has(entry.entryCode)
    ) {
      throw new Error(
        `产品入口编码缺失或重复：${entry.entryCode ?? "<missing>"}`,
      );
    }
    entryCodes.add(entry.entryCode);
    if (!entry.requiredPermissions?.includes(`menu.${entry.entryCode}`)) {
      throw new Error(`产品入口缺少菜单权限：${entry.entryCode}`);
    }
  }
}

function renderBackendResource(value, digest) {
  const resource = {
    schemaVersion: value.schemaVersion,
    catalogId: "medkernel-menu-permission-catalog",
    generatedFrom: sourceRelativePath,
    sourceCatalogId: value.catalogId,
    sourceCatalogSha256: digest,
    menus: value.entries.map((entry) => ({
      sectionKey: entry.sectionCode,
      menuKey: entry.entryCode,
      displayName: entry.displayName,
      permissionCode: `menu.${entry.entryCode}`,
      placement: entry.placement.toUpperCase(),
      route: entry.route,
      responsibilityRoles: entry.responsibilityRoles,
    })),
  };
  return `${JSON.stringify(resource, null, 2)}\n`;
}

async function renderFrontendContract(value, digest) {
  const unformatted = `// 本文件由 ${sourceRelativePath} 通过 scripts/release/generate-product-entry-consumers.mjs 生成，禁止手工修改。\n\nexport const productEntryCatalogSourceSha256 = ${JSON.stringify(digest)} as const;\n\nexport const productEntryCatalogContract = ${JSON.stringify(value, null, 2)} as const;\n\nexport const productEntryCatalog = productEntryCatalogContract.entries;\n\nexport type ProductEntry = (typeof productEntryCatalog)[number];\nexport type ProductEntryCode = ProductEntry["entryCode"];\nexport type ProductResponsibilityRole = (typeof productEntryCatalogContract.responsibilityRoles)[number];\nexport type ProductSixState = (typeof productEntryCatalogContract.sixStates)[number];\n`;
  const prettierPath = path.join(
    repositoryRoot,
    "frontend/node_modules/prettier/index.mjs",
  );
  if (!existsSync(prettierPath)) {
    throw new Error("缺少前端锁定版 Prettier，请先在 frontend 执行 npm ci");
  }
  const prettier = await import(pathToFileURL(prettierPath).href);
  return prettier.format(unformatted, {
    parser: "typescript",
    endOfLine: "lf",
    printWidth: 100,
  });
}

function writeAtomicallyIfChanged(targetPath, content) {
  if (existsSync(targetPath) && readFileSync(targetPath, "utf8") === content) {
    return;
  }
  mkdirSync(path.dirname(targetPath), { recursive: true });
  const temporaryPath = `${targetPath}.tmp-${process.pid}`;
  try {
    writeFileSync(temporaryPath, content, "utf8");
    renameSync(temporaryPath, targetPath);
  } finally {
    rmSync(temporaryPath, { force: true });
  }
}
