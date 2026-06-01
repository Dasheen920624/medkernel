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

test("SYS-05 系统任务表按权威卡允许使用 sys_task 表名", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V41__runtime_task_framework.sql": `
        CREATE TABLE sys_task (
          id BIGSERIAL PRIMARY KEY,
          task_id VARCHAR(64) NOT NULL,
          tenant_id VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          CONSTRAINT uk_sys_task_tenant_task UNIQUE (tenant_id, task_id),
          CONSTRAINT ck_sys_task_status CHECK (status IN ('UNREAD'))
        );

        CREATE INDEX idx_sys_task_status_ts ON sys_task(tenant_id, status);

        COMMENT ON TABLE sys_task IS '任务运行框架表';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V41__runtime_task_framework.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("DROP CONSTRAINT IF EXISTS 不会被误判为约束名 IF", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/h2/V42__runtime_task_retry_dead_letter.sql": `
        -- ROLLBACK: 如需回滚，先恢复旧约束，再删除新增列。
        ALTER TABLE sys_task DROP CONSTRAINT IF EXISTS ck_sys_task_mode;
        ALTER TABLE sys_task DROP CONSTRAINT IF EXISTS ck_sys_task_status;
        ALTER TABLE sys_task ADD CONSTRAINT ck_sys_task_mode CHECK (task_mode IN ('ONLINE','OFFLINE'));
        ALTER TABLE sys_task ADD CONSTRAINT ck_sys_task_status CHECK (status IN ('COMPLETED','NOT_CONNECTED'));
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/h2/V42__runtime_task_retry_dead_letter.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("高风险迁移缺少中文回滚或补偿说明会被阻断", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V32__drop_legacy_shadow.sql": `
        DROP TABLE legacy_audit_shadow;
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V32__drop_legacy_shadow.sql",
      ]);

      assert.equal(hasBlockingViolations(report), true);
      assert.deepEqual(ruleIds(report), ["migration.rollback-plan"]);
    },
  );
});

test("高风险迁移带中文回滚或补偿说明时通过回滚规约门禁", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V32__drop_legacy_shadow.sql": `
        -- ROLLBACK: 如需回退，先从备份表 legacy_audit_shadow_bak 恢复数据，再重放 V32 前的审计快照。
        DROP TABLE legacy_audit_shadow;
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V32__drop_legacy_shadow.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});
