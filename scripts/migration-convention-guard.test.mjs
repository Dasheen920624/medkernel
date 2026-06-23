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

test("V30 及以前的权威基线表名不被后续 mk 命名规则误判", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V30__legacy_authority.sql": `
        CREATE TABLE context_snapshot (
          id BIGSERIAL PRIMARY KEY,
          tenant_id VARCHAR(64) NOT NULL
        );

        CREATE INDEX idx_context_snapshot_tenant ON context_snapshot(tenant_id);
        COMMENT ON TABLE context_snapshot IS '标准临床上下文权威快照';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V30__legacy_authority.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
    },
  );
});

test("V96 tenant_user 作为安全域唯一用户目录允许使用稳定权威表名", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V96__tenant_user_directory.sql": `
        CREATE TABLE tenant_user (
          id BIGSERIAL PRIMARY KEY,
          tenant_id VARCHAR(64) NOT NULL,
          user_id VARCHAR(64) NOT NULL
        );

        CREATE INDEX idx_tenant_user_directory ON tenant_user(tenant_id, user_id);
        COMMENT ON TABLE tenant_user IS '租户用户唯一目录';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V96__tenant_user_directory.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
    },
  );
});

test("tenant_id 前缀唯一约束可作为真实租户索引", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_unique.sql": `
        CREATE TABLE mk_rule_sample (
          id BIGSERIAL PRIMARY KEY,
          tenant_id VARCHAR(64) NOT NULL,
          sample_code VARCHAR(64) NOT NULL,
          CONSTRAINT uk_mk_rule_sample_tenant_code UNIQUE (tenant_id, sample_code)
        );

        COMMENT ON TABLE mk_rule_sample IS '租户规则样例表';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_unique.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
    },
  );
});

test("tenant_id 主键已具备唯一索引，不要求重复创建租户索引", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_primary_key.sql": `
        CREATE TABLE mk_audit_chain_head (
          tenant_id VARCHAR(64) PRIMARY KEY,
          last_event_id VARCHAR(64)
        );

        COMMENT ON TABLE mk_audit_chain_head IS '每租户审计链头';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_primary_key.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("ALTER TABLE 中的 tenant_id 主键可作为真实租户索引", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_alter_primary_key.sql": `
        CREATE TABLE mk_audit_chain_head (
          tenant_id VARCHAR(64) NOT NULL,
          last_event_id VARCHAR(64)
        );

        ALTER TABLE mk_audit_chain_head
          ADD CONSTRAINT pk_mk_audit_chain_head PRIMARY KEY (tenant_id);

        COMMENT ON TABLE mk_audit_chain_head IS '每租户审计链头';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_alter_primary_key.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("ALTER TABLE 中以 tenant_id 开头的唯一约束可作为真实租户索引", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_alter_unique.sql": `
        CREATE TABLE mk_rule_sample (
          id BIGSERIAL NOT NULL,
          tenant_id VARCHAR(64) NOT NULL,
          sample_code VARCHAR(64) NOT NULL
        );

        ALTER TABLE mk_rule_sample
          ADD CONSTRAINT pk_mk_rule_sample PRIMARY KEY (id);
        ALTER TABLE mk_rule_sample
          ADD CONSTRAINT uk_mk_rule_sample_tenant_code UNIQUE (tenant_id, sample_code);

        COMMENT ON TABLE mk_rule_sample IS '租户规则样例表';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V31__tenant_alter_unique.sql",
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

test("AUTH-03 登录失败状态表按权威卡允许使用 sys_login_attempt 表名", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V45__credential_login_attempt.sql": `
        CREATE TABLE sys_login_attempt (
          id BIGSERIAL PRIMARY KEY,
          attempt_id VARCHAR(64) NOT NULL,
          tenant_id VARCHAR(64) NOT NULL,
          username VARCHAR(128) NOT NULL,
          failed_count INTEGER NOT NULL DEFAULT 0,
          locked_until TIMESTAMPTZ,
          CONSTRAINT uk_sys_login_attempt_tenant_user UNIQUE (tenant_id, username),
          CONSTRAINT ck_sys_login_attempt_failed_count CHECK (failed_count >= 0)
        );

        CREATE INDEX idx_sys_login_attempt_locked_until
          ON sys_login_attempt (tenant_id, locked_until);

        COMMENT ON TABLE sys_login_attempt IS 'AUTH-03 登录失败计数与锁定限流状态表';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V45__credential_login_attempt.sql",
      ]);

      assert.equal(hasBlockingViolations(report), false);
      assert.deepEqual(report.violations, []);
    },
  );
});

test("AUTH-03 密码重置 token 表按权威卡允许使用 sys_password_reset_token 表名", async () => {
  await withFixture(
    {
      "medkernel-backend/src/main/resources/db/migration/postgres/V46__auth_mfa_sm3_reset.sql": `
        CREATE TABLE sys_password_reset_token (
          id BIGSERIAL PRIMARY KEY,
          reset_id VARCHAR(64) NOT NULL,
          tenant_id VARCHAR(64) NOT NULL,
          user_id VARCHAR(64) NOT NULL,
          token_hash VARCHAR(128) NOT NULL,
          expires_at TIMESTAMPTZ NOT NULL,
          used_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
          CONSTRAINT uk_password_reset_token_id UNIQUE (reset_id),
          CONSTRAINT ck_password_reset_token_expiry CHECK (expires_at > created_at)
        );

        CREATE INDEX idx_pwd_reset_token_lookup
          ON sys_password_reset_token (tenant_id, user_id, token_hash, used_at);

        COMMENT ON TABLE sys_password_reset_token IS 'AUTH-03 受控密码重置一次性 token 表';
      `,
    },
    async (root) => {
      const report = await scanSqlFiles(root, [
        "medkernel-backend/src/main/resources/db/migration/postgres/V46__auth_mfa_sm3_reset.sql",
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
