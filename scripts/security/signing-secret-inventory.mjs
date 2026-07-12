import { existsSync, readFileSync } from "node:fs";
import { relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { listAllCurrentFiles } from "../git-scan-files.mjs";

const INVENTORY_FILE = "scripts/security/signing-secret-inventory.v1.json";
const BACKEND_MAIN = /^medkernel-backend\/src\/main\/java\/.+\.java$/;
const AUTHORITY_MAIN =
  /^medkernel-backend\/src\/main\/java\/com\/medkernel\/engine\/knowledge\/authority\/.+\.java$/;
const PACKAGE_MAIN =
  /^medkernel-backend\/src\/main\/java\/com\/medkernel\/engine\/knowledge\/(?:packageio|delivery)\/.+\.java$/;
const DATABASE_SCHEMA =
  "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json";
const AUTHORITY_TABLES = new Set([
  "mk_knowledge_authority",
  "mk_knowledge_issuer_instance",
  "mk_knowledge_trust_root",
  "mk_knowledge_signing_key",
  "mk_knowledge_authority_handover",
  "mk_knowledge_key_revocation",
  "mk_knowledge_package_registration",
]);
const REQUIRED_INVENTORY = Object.freeze({
  schemaVersion: "1.0",
  inventoryId: "authority.key.external-boundary",
  secretClass: "PLATFORM_KNOWLEDGE_SIGNING_PRIVATE_KEY",
  custody: "EXTERNAL_HSM_KMS_ONLY",
  applicationAccess: "NON_EXPORTABLE_SIGN_OPERATION_ONLY",
  exportable: false,
  database: "FORBIDDEN",
  ordinaryBackup: "FORBIDDEN",
  medicalPackage: "FORBIDDEN",
  applicationLog: "FORBIDDEN",
  controlledBackup: "EXTERNAL_KEY_FACILITY_ONLY",
});
const REQUIRED_PUBLIC_METADATA = Object.freeze([
  "authorityId",
  "issuerInstanceId",
  "keyId",
  "rootFingerprint",
  "certificateChainPem",
  "publicKeyFingerprint",
  "notBefore",
  "notAfter",
  "signature",
]);
const INVENTORY_KEYS = new Set([
  ...Object.keys(REQUIRED_INVENTORY),
  "allowedPublicMetadata",
  "testException",
]);
const FORBIDDEN_TYPE = /\b(?:PrivateKey|KeyPair|SecretKey)\b/;
const DECLARATION =
  /\b(?:String|byte\s*\[\s*\]|char\s*\[\s*\]|PrivateKey|KeyPair|SecretKey|Key)\s+([A-Za-z][A-Za-z0-9_]*)/g;
const LOG_CALL =
  /\b(?:log|logger)\.(?:trace|debug|info|warn|error)\s*\([\s\S]{0,2000}?\)\s*;|System\.(?:out|err)\.[A-Za-z]+\s*\([\s\S]{0,2000}?\)\s*;/g;
const EXTERNAL_SIGNING_IMPLEMENTATION =
  /\bimplements\s+[^{]*\b(?:HsmKmsSigningClient|SigningKeyPort)\b/;
const BACKUP_COMMAND =
  /\b(?:cp|install|tar|zip|rsync|pg_dump|archive|backup)\b/i;
const SECRET_PATH =
  /(?:private[-_.]?key|key[-_.]?material|\/hsm(?:\/|$)|\/kms(?:\/|$)|pkcs11|keystore)/i;

function normalizePath(file, root) {
  return relative(root, resolve(root, file)).replaceAll("\\", "/");
}

function lineOf(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

function looksLikePrivateMaterial(name) {
  const normalized = String(name)
    .toLowerCase()
    .replaceAll(/[^a-z0-9]/g, "");
  return (
    normalized.includes("privatekey") ||
    normalized.includes("secret") ||
    normalized.includes("credential") ||
    normalized.includes("keymaterial")
  );
}

function stripCommentsAndStrings(content) {
  return content
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .replace(/\/\/[^\r\n]*/g, " ")
    .replace(/"(?:\\.|[^"\\])*"/g, '""')
    .replace(/'(?:\\.|[^'\\])*'/g, "''");
}

function violation(file, line, ruleId, message) {
  return { file, line, ruleId, message };
}

function inventoryViolations(inventory) {
  const problems = [];
  const unknownKeys = Object.keys(inventory ?? {}).filter(
    (key) => !INVENTORY_KEYS.has(key),
  );
  if (unknownKeys.length > 0) {
    problems.push(`清单包含未授权字段：${unknownKeys.sort().join(",")}`);
  }
  for (const [key, expected] of Object.entries(REQUIRED_INVENTORY)) {
    if (inventory?.[key] !== expected) {
      problems.push(`${key} 必须为 ${String(expected)}`);
    }
  }
  if (
    !Array.isArray(inventory?.allowedPublicMetadata) ||
    JSON.stringify(inventory.allowedPublicMetadata) !==
      JSON.stringify(REQUIRED_PUBLIC_METADATA)
  ) {
    problems.push(
      "allowedPublicMetadata 必须与固定公开元数据集合及顺序完全一致",
    );
  } else if (inventory.allowedPublicMetadata.some(looksLikePrivateMaterial)) {
    problems.push(
      "allowedPublicMetadata 不得包含私钥、秘密、凭据或密钥材料字段",
    );
  }
  if (
    inventory?.testException?.path !==
      "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/authority/InMemorySigningAdapter.java" ||
    inventory?.testException?.storage !== "EPHEMERAL_JVM_MEMORY_ONLY"
  ) {
    problems.push("测试例外只能是指定 src/test 适配器的 JVM 临时内存");
  }
  const testExceptionKeys = Object.keys(inventory?.testException ?? {}).sort();
  if (
    JSON.stringify(testExceptionKeys) !== JSON.stringify(["path", "storage"])
  ) {
    problems.push("testException 只允许 path 与 storage 两个固定字段");
  }
  return problems;
}

function scanProductionAuthority(file, content, violations) {
  for (const match of content.matchAll(/@Column\(\s*"([^"]+)"\s*\)/g)) {
    if (looksLikePrivateMaterial(match[1])) {
      violations.push(
        violation(
          file,
          lineOf(content, match.index),
          "signing-secret.database-column",
          `权威实体列 ${match[1]} 禁止保存平台签名私钥或凭据`,
        ),
      );
    }
  }

  const executable = stripCommentsAndStrings(content);
  if (FORBIDDEN_TYPE.test(executable)) {
    violations.push(
      violation(
        file,
        1,
        "signing-secret.production-private-type",
        "生产权威代码不得声明 PrivateKey、KeyPair 或 SecretKey；签名只能经外置句柄完成",
      ),
    );
  } else {
    for (const match of executable.matchAll(DECLARATION)) {
      if (looksLikePrivateMaterial(match[1])) {
        violations.push(
          violation(
            file,
            lineOf(executable, match.index),
            "signing-secret.production-private-type",
            `生产权威声明 ${match[1]} 越过外置密钥边界`,
          ),
        );
        break;
      }
    }
  }
}

function mapsAuthorityTable(content) {
  return [...content.matchAll(/@Table\(\s*"([^"]+)"\s*\)/g)].some((match) =>
    AUTHORITY_TABLES.has(match[1]),
  );
}

function scanApplicationLogs(file, content, violations) {
  for (const match of content.matchAll(LOG_CALL)) {
    if (looksLikePrivateMaterial(match[0])) {
      violations.push(
        violation(
          file,
          lineOf(content, match.index),
          "signing-secret.application-log",
          "应用日志不得输出平台签名私钥、凭据或可恢复密钥材料",
        ),
      );
    }
  }
}

function scanDatabaseSchema(file, content, violations) {
  try {
    const schema = JSON.parse(content);
    for (const table of schema.tables ?? []) {
      if (!AUTHORITY_TABLES.has(table.name)) {
        continue;
      }
      for (const column of table.columns ?? []) {
        if (looksLikePrivateMaterial(column.name)) {
          violations.push(
            violation(
              file,
              1,
              "signing-secret.database-schema",
              `权威表 ${table.name}.${column.name} 禁止保存平台签名私钥或凭据`,
            ),
          );
        }
      }
    }
  } catch (error) {
    violations.push(
      violation(
        file,
        1,
        "signing-secret.database-schema",
        `无法解析数据库单一 schema：${error.message}`,
      ),
    );
  }
}

function isDeployShell(file) {
  return (
    file.startsWith("deploy/") &&
    file.endsWith(".sh") &&
    !file.includes("/tests/")
  );
}

function scanOrdinaryBackup(file, content, violations) {
  content.split(/\r?\n/).forEach((line, index) => {
    const executable = line.replace(/#.*/, "").trim();
    if (BACKUP_COMMAND.test(executable) && SECRET_PATH.test(executable)) {
      violations.push(
        violation(
          file,
          index + 1,
          "signing-secret.ordinary-backup",
          "普通备份命令不得读取或归档 HSM/KMS 私钥、密钥材料或设施密钥库",
        ),
      );
    }
  });
}

function scanMedicalPackage(file, content, violations) {
  for (const match of content.matchAll(/@JsonProperty\(\s*"([^"]+)"\s*\)/g)) {
    if (looksLikePrivateMaterial(match[1])) {
      violations.push(
        violation(
          file,
          lineOf(content, match.index),
          "signing-secret.medical-package",
          `医疗包字段 ${match[1]} 禁止携带签名私钥或凭据`,
        ),
      );
    }
  }
  const executable = stripCommentsAndStrings(content);
  for (const record of executable.matchAll(
    /\brecord\s+[A-Za-z][A-Za-z0-9_]*\s*\(([\s\S]*?)\)\s*\{/g,
  )) {
    for (const field of record[1].matchAll(DECLARATION)) {
      if (looksLikePrivateMaterial(field[1]) || FORBIDDEN_TYPE.test(field[0])) {
        violations.push(
          violation(
            file,
            lineOf(executable, record.index),
            "signing-secret.medical-package",
            `医疗包记录字段 ${field[1]} 禁止携带签名私钥或凭据`,
          ),
        );
        return;
      }
    }
  }
}

export async function scanSigningSecretBoundary(root, files, inventory) {
  const violations = [];
  const problems = inventoryViolations(inventory);
  if (problems.length > 0) {
    violations.push(
      violation(
        INVENTORY_FILE,
        1,
        "signing-secret.inventory-contract",
        problems.join("；"),
      ),
    );
  }

  const scannedFiles = [];
  const testExceptionPath = inventory?.testException?.path;
  for (const rawFile of files) {
    const file = normalizePath(rawFile, root);
    const fullPath = resolve(root, file);
    if (!existsSync(fullPath)) {
      continue;
    }
    const relevant =
      BACKEND_MAIN.test(file) ||
      AUTHORITY_MAIN.test(file) ||
      PACKAGE_MAIN.test(file) ||
      file === DATABASE_SCHEMA ||
      isDeployShell(file) ||
      file.endsWith("/InMemorySigningAdapter.java");
    if (!relevant) {
      continue;
    }
    scannedFiles.push(file);
    const content = readFileSync(fullPath, "utf8");

    if (
      file.endsWith("/InMemorySigningAdapter.java") &&
      file !== testExceptionPath
    ) {
      violations.push(
        violation(
          file,
          1,
          "signing-secret.in-memory-production",
          "进程内签名适配器只允许位于指定 src/test 路径，不得进入生产源码",
        ),
      );
    }
    if (BACKEND_MAIN.test(file)) {
      scanApplicationLogs(file, content, violations);
    }
    if (
      AUTHORITY_MAIN.test(file) ||
      mapsAuthorityTable(content) ||
      (BACKEND_MAIN.test(file) && EXTERNAL_SIGNING_IMPLEMENTATION.test(content))
    ) {
      scanProductionAuthority(file, content, violations);
    }
    if (file === DATABASE_SCHEMA) {
      scanDatabaseSchema(file, content, violations);
    }
    if (isDeployShell(file)) {
      scanOrdinaryBackup(file, content, violations);
    }
    if (PACKAGE_MAIN.test(file)) {
      scanMedicalPackage(file, content, violations);
    }
  }

  return {
    inventory,
    scannedFiles: [...new Set(scannedFiles)].sort(),
    violations,
  };
}

export function hasBlockingViolations(report) {
  return report.violations.length > 0;
}

function printReport(report) {
  console.log(
    `签名秘密 inventory：${report.inventory.inventoryId}，扫描文件 ${report.scannedFiles.length} 个。`,
  );
  console.log(
    `边界：custody=${report.inventory.custody}，database=${report.inventory.database}，` +
      `ordinaryBackup=${report.inventory.ordinaryBackup}，medicalPackage=${report.inventory.medicalPackage}，` +
      `applicationLog=${report.inventory.applicationLog}。`,
  );
  for (const item of report.violations) {
    console.log(`${item.file}:${item.line} [${item.ruleId}] ${item.message}`);
  }
  if (!hasBlockingViolations(report)) {
    console.log(
      "签名秘密 inventory 通过：私钥仅归外置设施托管，应用仅持有公开句柄与元数据。",
    );
  }
}

async function main() {
  const root = process.cwd();
  const inventory = JSON.parse(
    readFileSync(resolve(root, INVENTORY_FILE), "utf8"),
  );
  const files = listAllCurrentFiles(root, [
    "medkernel-backend/src/main/java",
    "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json",
    "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/authority/InMemorySigningAdapter.java",
    "deploy",
  ]);
  const report = await scanSigningSecretBoundary(root, files, inventory);
  printReport(report);
  if (hasBlockingViolations(report)) {
    process.exit(1);
  }
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await main();
}
