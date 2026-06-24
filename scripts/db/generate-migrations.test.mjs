import assert from "node:assert/strict";
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import test from "node:test";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const generatorPath = join(scriptDir, "generate-migrations.mjs");
const dialects = ["postgres", "kingbase", "oracle", "dm", "h2"];

function createFixture() {
  const root = mkdtempSync(join(tmpdir(), "medkernel-schema-generator-"));
  const fixtureScript = join(root, "scripts/db/generate-migrations.mjs");
  const schemaDirectory = join(root, "medkernel-backend/src/main/resources/db/schema");
  const changesDirectory = join(schemaDirectory, "migrations");
  const migrationRoot = join(root, "medkernel-backend/src/main/resources/db/migration");
  mkdirSync(dirname(fixtureScript), { recursive: true });
  mkdirSync(changesDirectory, { recursive: true });
  copyFileSync(generatorPath, fixtureScript);
  writeFileSync(
    join(schemaDirectory, "medkernel.schema.json"),
    JSON.stringify({
      version: 1,
      description: "生成器测试模式",
      tables: [
        {
          name: "sample_record",
          comment: "生成器测试表",
          columns: [
            {
              name: "id",
              type: "int64",
              nullable: false,
              identity: false,
              default: null,
              comment: "主键",
            },
          ],
          primaryKey: { name: "pk_sample_record", columns: ["id"] },
          uniqueConstraints: [],
          checkConstraints: [],
          foreignKeys: [],
          indexes: [],
        },
      ],
    }),
    "utf8",
  );
  writeFileSync(
    join(changesDirectory, "V2__add_sample_label.json"),
    JSON.stringify({
      version: 2,
      name: "add_sample_label",
      description: "为样例记录增加名称",
      operations: [
        {
          type: "addColumn",
          table: "sample_record",
          column: {
            name: "label",
            type: "string",
            length: 100,
            nullable: true,
            identity: false,
            default: null,
            comment: "样例名称",
          },
        },
      ],
    }),
    "utf8",
  );
  writeFileSync(
    join(changesDirectory, "V3__index_sample_label.json"),
    JSON.stringify({
      version: 3,
      name: "index_sample_label",
      description: "增加样例名称索引",
      operations: [
        {
          type: "addIndex",
          table: "sample_record",
          index: {
            name: "idx_sample_record_label",
            unique: false,
            columns: [{ name: "label", order: "ASC" }],
          },
        },
      ],
    }),
    "utf8",
  );
  for (const dialect of dialects) {
    const directory = join(migrationRoot, dialect);
    mkdirSync(directory, { recursive: true });
    writeFileSync(join(directory, "V1__baseline.sql"), "-- old baseline\n", "utf8");
  }
  return { root, fixtureScript, migrationRoot, changesDirectory };
}

function runGenerator(script, args = []) {
  return spawnSync(process.execPath, [script, ...args], { encoding: "utf8" });
}

test("一份 V2/V3 规范变更为五个方言生成同版本迁移", () => {
  const fixture = createFixture();
  try {
    const result = runGenerator(fixture.fixtureScript);
    assert.equal(result.status, 0, result.stderr);
    for (const dialect of dialects) {
      const migrationDirectory = join(fixture.migrationRoot, dialect);
      const v1 = readFileSync(join(migrationDirectory, "V1__baseline.sql"), "utf8");
      const v2 = readFileSync(join(migrationDirectory, "V2__add_sample_label.sql"), "utf8");
      const v3 = readFileSync(join(migrationDirectory, "V3__index_sample_label.sql"), "utf8");
      assert.match(v1, /CREATE TABLE sample_record/);
      assert.doesNotMatch(v1, /\n\n$/);
      assert.doesNotMatch(v2, /\n\n$/);
      assert.doesNotMatch(v3, /\n\n$/);
      assert.match(v2, /ALTER TABLE sample_record ADD label/);
      assert.match(v2, ["oracle", "dm"].includes(dialect) ? /VARCHAR2\(100\)/ : /VARCHAR\(100\)/);
      assert.match(v3, /CREATE INDEX idx_sample_record_label ON sample_record \(label\)/);
    }
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("没有规范变更源的手写 V2 及后续 SQL 被拒绝且不删除", () => {
  const fixture = createFixture();
  try {
    const manual = join(fixture.migrationRoot, "postgres/V4__manual.sql");
    writeFileSync(manual, "-- manual\n", "utf8");

    const result = runGenerator(fixture.fixtureScript);

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /缺少规范变更源/);
    assert.equal(readFileSync(manual, "utf8"), "-- manual\n");
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("--check 只校验生成产物，不改写不一致的 V1", () => {
  const fixture = createFixture();
  try {
    assert.equal(runGenerator(fixture.fixtureScript).status, 0);
    const postgresV1 = join(fixture.migrationRoot, "postgres/V1__baseline.sql");
    writeFileSync(postgresV1, "-- manually changed\n", "utf8");

    const result = runGenerator(fixture.fixtureScript, ["--check"]);

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /生成产物与单一模式源不一致/);
    assert.equal(readFileSync(postgresV1, "utf8"), "-- manually changed\n");
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("规范迁移版本缺号或文件名不规范时直接失败", () => {
  const fixture = createFixture();
  try {
    rmSync(join(fixture.changesDirectory, "V2__add_sample_label.json"));
    let result = runGenerator(fixture.fixtureScript);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /必须从 V2 连续递增/);

    rmSync(join(fixture.changesDirectory, "V3__index_sample_label.json"));
    writeFileSync(join(fixture.changesDirectory, "V2__Bad-Name.json"), "{}", "utf8");
    result = runGenerator(fixture.fixtureScript);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /文件名不规范/);
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("addTable 只维护 definition，不要求重复声明 table", () => {
  const fixture = createFixture();
  try {
    rmSync(fixture.changesDirectory, { recursive: true, force: true });
    mkdirSync(fixture.changesDirectory, { recursive: true });
    writeFileSync(
      join(fixture.changesDirectory, "V2__add_audit_note.json"),
      JSON.stringify({
        version: 2,
        name: "add_audit_note",
        description: "增加审计备注表",
        operations: [
          {
            type: "addTable",
            definition: {
              name: "audit_note",
              comment: "审计备注表",
              columns: [
                {
                  name: "id",
                  type: "int64",
                  nullable: false,
                  identity: true,
                  default: null,
                  comment: "主键",
                },
              ],
              primaryKey: { name: "pk_audit_note", columns: ["id"] },
              uniqueConstraints: [],
              checkConstraints: [],
              foreignKeys: [],
              indexes: [],
            },
          },
        ],
      }),
      "utf8",
    );

    const result = runGenerator(fixture.fixtureScript);
    assert.equal(result.status, 0, result.stderr);
    for (const dialect of dialects) {
      const sql = readFileSync(
        join(fixture.migrationRoot, dialect, "V2__add_audit_note.sql"),
        "utf8",
      );
      assert.match(sql, /CREATE TABLE audit_note/);
      assert.match(sql, /COMMENT ON TABLE audit_note IS '审计备注表'/);
    }
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("--check 发现缺失的后续版本时失败且不创建文件", () => {
  const fixture = createFixture();
  try {
    assert.equal(runGenerator(fixture.fixtureScript).status, 0);
    const missing = join(fixture.migrationRoot, "postgres/V3__index_sample_label.sql");
    rmSync(missing);

    const result = runGenerator(fixture.fixtureScript, ["--check"]);

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /生成产物与单一模式源不一致/);
    assert.equal(readFileSync(join(fixture.migrationRoot, "postgres/V2__add_sample_label.sql"), "utf8").length > 0, true);
    assert.equal(existsSync(missing), false);
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("规范列缺少类型参数、默认值或中文说明时拒绝生成无效 SQL", () => {
  const fixture = createFixture();
  try {
    writeFileSync(
      join(fixture.changesDirectory, "V2__add_sample_label.json"),
      JSON.stringify({
        version: 2,
        name: "add_sample_label",
        description: "增加样例名称",
        operations: [
          {
            type: "addColumn",
            table: "sample_record",
            column: {
              name: "label",
              type: "string",
              nullable: true,
              identity: false,
              comment: "label",
            },
          },
        ],
      }),
      "utf8",
    );

    const result = runGenerator(fixture.fixtureScript);

    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /列定义不完整|中文说明/);
    assert.equal(existsSync(join(fixture.migrationRoot, "postgres/V2__add_sample_label.sql")), false);
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});

test("后续迁移在生成前校验跨版本表列状态", () => {
  const fixture = createFixture();
  try {
    writeFileSync(
      join(fixture.changesDirectory, "V2__add_sample_label.json"),
      JSON.stringify({
        version: 2,
        name: "add_sample_label",
        description: "向不存在的表增加样例名称",
        operations: [
          {
            type: "addColumn",
            table: "missing_record",
            column: {
              name: "label",
              type: "string",
              length: 100,
              nullable: true,
              identity: false,
              default: null,
              comment: "样例名称",
            },
          },
        ],
      }),
      "utf8",
    );

    let result = runGenerator(fixture.fixtureScript);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /引用不存在表.*missing_record/);

    writeFileSync(
      join(fixture.changesDirectory, "V2__add_sample_label.json"),
      JSON.stringify({
        version: 2,
        name: "add_sample_label",
        description: "重复增加样例主键",
        operations: [
          {
            type: "addColumn",
            table: "sample_record",
            column: {
              name: "id",
              type: "int64",
              nullable: false,
              identity: false,
              default: null,
              comment: "重复主键",
            },
          },
        ],
      }),
      "utf8",
    );

    result = runGenerator(fixture.fixtureScript);
    assert.notEqual(result.status, 0);
    assert.match(`${result.stdout}${result.stderr}`, /重复增加列.*sample_record\.id/);
  } finally {
    rmSync(fixture.root, { recursive: true, force: true });
  }
});
