import assert from "node:assert/strict";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";

import {
  hasBlockingViolations,
  scanSigningSecretBoundary,
} from "./signing-secret-inventory.mjs";

const VALID_INVENTORY = {
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
  allowedPublicMetadata: [
    "authorityId",
    "issuerInstanceId",
    "keyId",
    "rootFingerprint",
    "certificateChainPem",
    "publicKeyFingerprint",
    "notBefore",
    "notAfter",
    "signature",
  ],
  testException: {
    path: "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/authority/InMemorySigningAdapter.java",
    storage: "EPHEMERAL_JVM_MEMORY_ONLY",
  },
};

async function withFixture(files, run) {
  const root = await mkdtemp(join(tmpdir(), "medkernel-signing-secret-"));
  try {
    for (const [file, content] of Object.entries(files)) {
      const fullPath = join(root, file);
      await mkdir(dirname(fullPath), { recursive: true });
      await writeFile(fullPath, content, "utf8");
    }
    return await run(root, Object.keys(files));
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

test("公开元数据、外置签名句柄与测试 JVM 临时密钥通过秘密清单", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/authority/SigningKey.java": `
        @Table("mk_knowledge_signing_key")
        public record SigningKey(
          @Column("key_id") String keyId,
          @Column("certificate_chain_pem") String certificateChainPem) {}
      `,
      "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json":
        JSON.stringify({
          tables: [
            {
              name: "mk_knowledge_signing_key",
              columns: [{ name: "key_id" }, { name: "certificate_chain_pem" }],
            },
          ],
        }),
      "deploy/onprem/ordinary-backup.sh": "pg_dump medkernel > database.dump\n",
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/packageio/Manifest.java":
        "public record Manifest(String keyId, String signature) {}\n",
      "medkernel-backend/src/test/java/com/medkernel/engine/knowledge/authority/InMemorySigningAdapter.java":
        "final class InMemorySigningAdapter { PrivateKey ephemeralSigningKey; }\n",
    },
    async (root, files) => {
      const report = await scanSigningSecretBoundary(
        root,
        files,
        VALID_INVENTORY,
      );
      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
      assert.equal(
        report.inventory.inventoryId,
        "authority.key.external-boundary",
      );
    },
  );
});

test("数据库、普通备份、包、日志和生产内存适配器中的私钥痕迹分别阻断", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/authority/SigningKey.java": `
        @Table("mk_knowledge_signing_key")
        public record SigningKey(@Column("private_key") String privateKey) {
          void leak() {
            log.info(
              "signing private key={}",
              privateKey);
          }
        }
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/authority/InMemorySigningAdapter.java":
        "final class InMemorySigningAdapter { PrivateKey privateKey; }\n",
      "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json":
        JSON.stringify({
          tables: [
            {
              name: "mk_knowledge_signing_key",
              columns: [{ name: "private_key" }],
            },
          ],
        }),
      "deploy/onprem/ordinary-backup.sh":
        "cp /secure/hsm/private-key ./archive/private-key\n",
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/packageio/Manifest.java":
        "public record Manifest(byte[] privateKey) {}\n",
    },
    async (root, files) => {
      const report = await scanSigningSecretBoundary(
        root,
        files,
        VALID_INVENTORY,
      );
      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(
        [
          ...new Set(report.violations.map((violation) => violation.ruleId)),
        ].sort(),
        [
          "signing-secret.application-log",
          "signing-secret.database-column",
          "signing-secret.database-schema",
          "signing-secret.in-memory-production",
          "signing-secret.medical-package",
          "signing-secret.ordinary-backup",
          "signing-secret.production-private-type",
        ],
      );
    },
  );
});

test("清单若允许导出或进入任一普通区域则自身阻断", async () => {
  const report = await scanSigningSecretBoundary(process.cwd(), [], {
    ...VALID_INVENTORY,
    exportable: true,
    ordinaryBackup: "ALLOWED",
  });

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(
    report.violations.map((violation) => violation.ruleId),
    ["signing-secret.inventory-contract"],
  );
});

test("清单必须是精确公开合同，不得遗漏公开句柄或追加秘密定位字段", async () => {
  const report = await scanSigningSecretBoundary(process.cwd(), [], {
    ...VALID_INVENTORY,
    allowedPublicMetadata: VALID_INVENTORY.allowedPublicMetadata.filter(
      (field) => field !== "keyId",
    ),
    privateKeyPath: "/forbidden/location",
  });

  assert.equal(hasBlockingViolations(report), true);
  assert.deepEqual(
    report.violations.map((violation) => violation.ruleId),
    ["signing-secret.inventory-contract"],
  );
});

test("移动权威实体、泄密日志或改名部署脚本均不得绕过扫描", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/other/MovedSigningKey.java": `
        @Table("mk_knowledge_signing_key")
        public record MovedSigningKey(@Column("private_key") String privateKey) {}
      `,
      "medkernel-backend/src/main/java/com/medkernel/engine/other/SigningLeak.java": `
        final class SigningLeak {
          void leak(String privateKey) {
            logger.warn("platform signing private key={}", privateKey);
          }
        }
      `,
      "deploy/onprem/export-signing-material.sh":
        "tar -cf /tmp/signing-material.tar /secure/kms/key-material\n",
    },
    async (root, files) => {
      const report = await scanSigningSecretBoundary(
        root,
        files,
        VALID_INVENTORY,
      );
      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(
        [
          ...new Set(report.violations.map((violation) => violation.ruleId)),
        ].sort(),
        [
          "signing-secret.application-log",
          "signing-secret.database-column",
          "signing-secret.ordinary-backup",
          "signing-secret.production-private-type",
        ],
      );
    },
  );
});

test("生产 HSM 驱动移到其它包后仍不得持有可导出私钥", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/vendor/hsm/VendorSigningClient.java": `
        final class VendorSigningClient implements HsmKmsSigningClient {
          private final PrivateKey privateKey = loadPrivateKey();
        }
      `,
    },
    async (root, files) => {
      const report = await scanSigningSecretBoundary(
        root,
        files,
        VALID_INVENTORY,
      );
      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(
        report.violations.map((violation) => violation.ruleId),
        ["signing-secret.production-private-type"],
      );
    },
  );
});

test("现行医疗包 delivery 目录禁止新增私钥或凭据字段", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/java/com/medkernel/engine/knowledge/delivery/UnsafePackage.java": `
        public record UnsafePackage(String deliveryId, String privateKeyMaterial) {}
      `,
    },
    async (root, files) => {
      const report = await scanSigningSecretBoundary(
        root,
        files,
        VALID_INVENTORY,
      );
      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(
        report.violations.map((violation) => violation.ruleId),
        ["signing-secret.medical-package"],
      );
    },
  );
});
