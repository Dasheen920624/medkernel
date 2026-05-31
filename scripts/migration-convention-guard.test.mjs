import assert from "node:assert/strict";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";

import { hasBlockingViolations, scanSqlFiles } from "./migration-convention-guard.mjs";

async function withFixture(files, run) {
  const root = await mkdtemp(join(tmpdir(), "medkernel-migration-guard-"));
  try {
    for (const [file, content] of Object.entries(files)) {
      const fullPath = join(root, file);
      await mkdir(dirname(fullPath), { recursive: true });
      await writeFile(fullPath, content, "utf8");
    }
    return await run(root);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
}

function ruleIds(report) {
  return report.violations.map((violation) => violation.ruleId).sort();
}

test("新增生产方言迁移会阻断缺中文注释、命名不合规和缺租户索引", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V31__bad_audit_table.sql": `
        CREATE TABLE bad_audit_table (
          id VARCHAR(64) PRIMARY KEY,
          tenant_id VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          CONSTRAINT badStatus CHECK (status IN ('ACTIVE'))
        );

        CREATE INDEX bad_tenant_idx ON bad_audit_table(status);
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V31__bad_audit_table.sql",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), [
        "migration.constraint-name",
        "migration.index-name",
        "migration.table-comment",
        "migration.table-name",
        "migration.tenant-index",
      ]);
    },
  );
});

test("合规迁移通过中文注释、命名规约和租户索引门禁", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V31__good_audit_table.sql": `
        CREATE TABLE mk_audit_guard_sample (
          sample_id VARCHAR(64) PRIMARY KEY,
          tenant_id VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          CONSTRAINT ck_mk_audit_guard_sample_status CHECK (status IN ('ACTIVE', 'DISABLED'))
        );

        CREATE INDEX idx_mk_audit_guard_sample_tenant ON mk_audit_guard_sample(tenant_id, status);

        COMMENT ON TABLE mk_audit_guard_sample IS '审计迁移规约示例表';
        COMMENT ON COLUMN mk_audit_guard_sample.tenant_id IS '租户 ID';
        COMMENT ON COLUMN mk_audit_guard_sample.status IS '状态：ACTIVE 启用 / DISABLED 停用';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V31__good_audit_table.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});
