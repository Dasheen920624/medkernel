#!/usr/bin/env node

import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { basename, dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const root = resolve(scriptDir, "../..");
const migrationRoot = join(root, "medkernel-backend/src/main/resources/db/migration");
const schemaPath = join(root, "medkernel-backend/src/main/resources/db/schema/medkernel.schema.json");
const changeRoot = join(root, "medkernel-backend/src/main/resources/db/schema/migrations");
const dialects = ["postgres", "kingbase", "oracle", "dm", "h2"];
const checkOnly = process.argv.includes("--check");
const forbiddenTables = new Set([
  "role_permission",
  "sys_role",
  "sys_permission",
  "rule_signoff",
  "mk_knowledge_source_version_approval",
]);
const forbiddenEvalColumns = new Set(["reviewer", "signed_at", "review_comment"]);
const forbiddenAcquisitionSourceColumns = new Set(["approved_by", "approved_at"]);

const model = JSON.parse(readFileSync(schemaPath, "utf8"));
validateModel(model);
const changeSets = loadChangeSets(changeRoot);
validateChangeSetSequence(model, changeSets);

const artifacts = [];
for (const dialect of dialects) {
  artifacts.push({
    path: join(migrationRoot, dialect, "V1__baseline.sql"),
    content: renderBaseline(model, dialect),
  });
  for (const changeSet of changeSets) {
    artifacts.push({
      path: join(migrationRoot, dialect, `V${changeSet.version}__${changeSet.name}.sql`),
      content: renderChangeSet(changeSet, dialect),
    });
  }
}

const expectedNamesByDialect = new Map(
  dialects.map((dialect) => [
    dialect,
    new Set(
      artifacts
        .filter((artifact) => dirname(artifact.path) === join(migrationRoot, dialect))
        .map((artifact) => basename(artifact.path)),
    ),
  ]),
);
const orphanArtifacts = [];
for (const dialect of dialects) {
  const directory = join(migrationRoot, dialect);
  const expected = expectedNamesByDialect.get(dialect);
  for (const entry of readdirSync(directory)) {
    if (/^V\d+__.+\.sql$/.test(entry) && !expected.has(entry)) {
      orphanArtifacts.push(join(directory, entry));
    }
  }
}
if (orphanArtifacts.length > 0) {
  throw new Error(`版本迁移缺少规范变更源，禁止手写五方言 SQL：\n${orphanArtifacts.join("\n")}`);
}

const inconsistentArtifacts = [];
for (const artifact of artifacts) {
  if (checkOnly) {
    if (!existsSync(artifact.path) || readFileSync(artifact.path, "utf8") !== artifact.content) {
      inconsistentArtifacts.push(artifact.path);
    }
  } else {
    writeFileSync(artifact.path, artifact.content, "utf8");
  }
}

if (inconsistentArtifacts.length > 0) {
  throw new Error(
    `生成产物与单一模式源不一致，请执行 node scripts/db/generate-migrations.mjs：\n${inconsistentArtifacts.join("\n")}`,
  );
}

function loadChangeSets(directory) {
  if (!existsSync(directory)) return [];
  const entries = readdirSync(directory)
    .map((file) => ({ file, match: /^V(\d+)__([a-z][a-z0-9_]*)\.json$/.exec(file) }));
  const invalidFiles = entries.filter((entry) => !entry.match).map((entry) => entry.file);
  if (invalidFiles.length > 0) {
    throw new Error(`规范迁移文件名不规范，必须使用 V版本__小写名称.json：${invalidFiles.join(", ")}`);
  }
  entries
    .sort((left, right) => Number(left.match[1]) - Number(right.match[1]));
  let expectedVersion = 2;
  return entries.map(({ file, match }) => {
    const fileVersion = Number(match[1]);
    const fileName = match[2];
    if (fileVersion !== expectedVersion) {
      throw new Error(`规范迁移版本必须从 V2 连续递增，期望 V${expectedVersion}，实际 ${file}`);
    }
    const changeSet = JSON.parse(readFileSync(join(directory, file), "utf8"));
    if (changeSet.version !== fileVersion || changeSet.name !== fileName) {
      throw new Error(`规范迁移文件名与内容不一致：${file}`);
    }
    if (!hasChineseText(changeSet.description)
        || !Array.isArray(changeSet.operations)
        || changeSet.operations.length === 0) {
      throw new Error(`规范迁移必须包含中文说明和非空 operations：${file}`);
    }
    for (const operation of changeSet.operations) validateOperation(operation, file);
    expectedVersion += 1;
    return changeSet;
  });
}

function validateOperation(operation, file) {
  if (!operation || typeof operation !== "object") throw new Error(`迁移操作格式错误：${file}`);
  const tableTypes = new Set([
    "addTable",
    "addColumn",
    "addPrimaryKey",
    "addUniqueConstraint",
    "addCheckConstraint",
    "addForeignKey",
    "addIndex",
    "renameTable",
    "renameColumn",
    "dropIndex",
    "dropConstraint",
    "dropColumn",
    "dropTable",
  ]);
  if (!tableTypes.has(operation.type)) throw new Error(`不支持的规范迁移操作：${operation.type}`);
  if (operation.type === "addTable") {
    validateTable(operation.definition, file);
  } else {
    validateIdentifier(operation.table, `${file} 表`);
  }
  if (operation.type === "addColumn") validateColumn(operation.column, file);
  if (["addPrimaryKey", "addUniqueConstraint"].includes(operation.type)) {
    validateNamedColumns(operation.constraint, file);
  }
  if (operation.type === "addCheckConstraint") {
    validateIdentifier(operation.constraint?.name, `${file} 检查约束`);
    validateCheckExpression(operation.constraint?.expression, file);
  }
  if (operation.type === "addForeignKey") validateForeignKey(operation.foreignKey, file);
  if (operation.type === "addIndex") validateIndex(operation.index, file);
  if (["renameTable", "renameColumn"].includes(operation.type)) {
    validateIdentifier(operation.to, `${file} 重命名目标`);
  }
  if (operation.type === "renameColumn") validateIdentifier(operation.from, `${file} 原列`);
  if (["dropIndex", "dropConstraint", "dropColumn"].includes(operation.type)) {
    validateIdentifier(operation.name, `${file} 删除对象`);
  }
}

function validateChangeSetSequence(schema, changeSets) {
  const tables = new Map();
  const objectOwners = new Map();
  for (const table of schema.tables) {
    const state = createTableState(table);
    tables.set(table.name, state);
    for (const name of [...state.constraints.keys(), ...state.indexes.keys()]) {
      reserveStateObject(objectOwners, name, table.name, "V1");
    }
  }

  for (const changeSet of changeSets) {
    const version = `V${changeSet.version}`;
    for (const operation of changeSet.operations) {
      applyOperationState(tables, objectOwners, operation, version);
    }
  }
}

function createTableState(table) {
  const constraints = new Map();
  if (table.primaryKey) constraints.set(table.primaryKey.name, stateConstraint("PRIMARY_KEY", table.primaryKey));
  for (const item of table.uniqueConstraints ?? []) {
    constraints.set(item.name, stateConstraint("UNIQUE", item));
  }
  for (const item of table.checkConstraints ?? []) {
    constraints.set(item.name, { kind: "CHECK", columns: [], expression: item.expression });
  }
  for (const item of table.foreignKeys ?? []) {
    constraints.set(item.name, stateConstraint("FOREIGN_KEY", item));
  }
  return {
    columns: new Set(table.columns.map((column) => column.name)),
    constraints,
    indexes: new Map((table.indexes ?? []).map((index) => [index.name, index.columns.map((column) => column.name)])),
  };
}

function stateConstraint(kind, value) {
  return {
    kind,
    columns: [...value.columns],
    referencedTable: value.referencedTable,
    referencedColumns: value.referencedColumns ? [...value.referencedColumns] : undefined,
  };
}

function applyOperationState(tables, objectOwners, operation, version) {
  if (operation.type === "addTable") {
    const table = operation.definition;
    if (tables.has(table.name)) throw new Error(`${version} 重复增加表：${table.name}`);
    const state = createTableState(table);
    tables.set(table.name, state);
    validateTableStateReferences(tables, table.name, state, version);
    for (const name of [...state.constraints.keys(), ...state.indexes.keys()]) {
      reserveStateObject(objectOwners, name, table.name, version);
    }
    return;
  }

  const state = requireStateTable(tables, operation.table, version);
  switch (operation.type) {
    case "addColumn":
      if (state.columns.has(operation.column.name)) {
        throw new Error(`${version} 重复增加列：${operation.table}.${operation.column.name}`);
      }
      state.columns.add(operation.column.name);
      return;
    case "addPrimaryKey":
    case "addUniqueConstraint": {
      const kind = operation.type === "addPrimaryKey" ? "PRIMARY_KEY" : "UNIQUE";
      if (kind === "PRIMARY_KEY" && [...state.constraints.values()].some((item) => item.kind === kind)) {
        throw new Error(`${version} 表已存在主键：${operation.table}`);
      }
      addStateConstraint(state, objectOwners, operation.table, kind, operation.constraint, tables, version);
      return;
    }
    case "addCheckConstraint":
      reserveStateObject(objectOwners, operation.constraint.name, operation.table, version);
      state.constraints.set(operation.constraint.name, {
        kind: "CHECK",
        columns: [],
        expression: operation.constraint.expression,
      });
      return;
    case "addForeignKey":
      addStateConstraint(state, objectOwners, operation.table, "FOREIGN_KEY", operation.foreignKey, tables, version);
      return;
    case "addIndex":
      ensureStateColumns(state, operation.index.columns.map((column) => column.name), operation.table, version);
      reserveStateObject(objectOwners, operation.index.name, operation.table, version);
      state.indexes.set(operation.index.name, operation.index.columns.map((column) => column.name));
      return;
    case "renameTable":
      if (tables.has(operation.to)) throw new Error(`${version} 表重命名目标已存在：${operation.to}`);
      tables.delete(operation.table);
      tables.set(operation.to, state);
      for (const [name, owner] of objectOwners) {
        if (owner === operation.table) objectOwners.set(name, operation.to);
      }
      for (const candidate of tables.values()) {
        for (const constraint of candidate.constraints.values()) {
          if (constraint.referencedTable === operation.table) constraint.referencedTable = operation.to;
        }
      }
      return;
    case "renameColumn":
      if (!state.columns.has(operation.from)) {
        throw new Error(`${version} 重命名不存在列：${operation.table}.${operation.from}`);
      }
      if (state.columns.has(operation.to)) {
        throw new Error(`${version} 列重命名目标已存在：${operation.table}.${operation.to}`);
      }
      state.columns.delete(operation.from);
      state.columns.add(operation.to);
      replaceStateColumnReferences(tables, operation.table, operation.from, operation.to);
      return;
    case "dropIndex":
      requireOwnedStateObject(state.indexes, objectOwners, operation.name, operation.table, version, "索引");
      state.indexes.delete(operation.name);
      objectOwners.delete(operation.name);
      return;
    case "dropConstraint":
      requireOwnedStateObject(state.constraints, objectOwners, operation.name, operation.table, version, "约束");
      state.constraints.delete(operation.name);
      objectOwners.delete(operation.name);
      return;
    case "dropColumn":
      dropStateColumn(tables, state, operation.table, operation.name, version);
      return;
    case "dropTable":
      for (const [candidateName, candidate] of tables) {
        if (candidateName !== operation.table && [...candidate.constraints.values()]
          .some((constraint) => constraint.referencedTable === operation.table)) {
          throw new Error(`${version} 表仍被外键引用，不能删除：${operation.table}`);
        }
      }
      for (const name of [...state.constraints.keys(), ...state.indexes.keys()]) objectOwners.delete(name);
      tables.delete(operation.table);
      return;
    default:
      throw new Error(`不支持的规范迁移操作：${operation.type}`);
  }
}

function addStateConstraint(state, objectOwners, tableName, kind, value, tables, version) {
  ensureStateColumns(state, value.columns, tableName, version);
  if (kind === "FOREIGN_KEY") {
    const referenced = requireStateTable(tables, value.referencedTable, version);
    ensureStateColumns(referenced, value.referencedColumns, value.referencedTable, version);
  }
  reserveStateObject(objectOwners, value.name, tableName, version);
  state.constraints.set(value.name, stateConstraint(kind, value));
}

function validateTableStateReferences(tables, tableName, state, version) {
  for (const constraint of state.constraints.values()) {
    ensureStateColumns(state, constraint.columns, tableName, version);
    if (constraint.kind === "FOREIGN_KEY") {
      const referenced = requireStateTable(tables, constraint.referencedTable, version);
      ensureStateColumns(referenced, constraint.referencedColumns, constraint.referencedTable, version);
    }
  }
  for (const columns of state.indexes.values()) ensureStateColumns(state, columns, tableName, version);
}

function requireStateTable(tables, tableName, version) {
  const state = tables.get(tableName);
  if (!state) throw new Error(`${version} 引用不存在表：${tableName}`);
  return state;
}

function ensureStateColumns(state, columns, tableName, version) {
  for (const column of columns ?? []) {
    if (!state.columns.has(column)) throw new Error(`${version} 引用不存在列：${tableName}.${column}`);
  }
}

function reserveStateObject(objectOwners, name, tableName, version) {
  if (objectOwners.has(name)) {
    throw new Error(`${version} 约束或索引名称重复：${name}（已有表 ${objectOwners.get(name)}）`);
  }
  objectOwners.set(name, tableName);
}

function requireOwnedStateObject(objects, objectOwners, name, tableName, version, label) {
  if (!objects.has(name) || objectOwners.get(name) !== tableName) {
    throw new Error(`${version} 删除不存在${label}：${tableName}.${name}`);
  }
}

function replaceStateColumnReferences(tables, tableName, from, to) {
  const own = tables.get(tableName);
  for (const constraint of own.constraints.values()) {
    constraint.columns = constraint.columns.map((column) => column === from ? to : column);
  }
  for (const [name, columns] of own.indexes) {
    own.indexes.set(name, columns.map((column) => column === from ? to : column));
  }
  for (const candidate of tables.values()) {
    for (const constraint of candidate.constraints.values()) {
      if (constraint.referencedTable === tableName) {
        constraint.referencedColumns = constraint.referencedColumns
          ?.map((column) => column === from ? to : column);
      }
    }
  }
}

function dropStateColumn(tables, state, tableName, columnName, version) {
  if (!state.columns.has(columnName)) throw new Error(`${version} 删除不存在列：${tableName}.${columnName}`);
  const localDependency = [...state.constraints.entries()]
    .find(([, item]) => item.columns.includes(columnName)
      || (item.kind === "CHECK" && new RegExp(`\\b${columnName}\\b`, "u").test(item.expression)));
  const indexDependency = [...state.indexes.entries()].find(([, columns]) => columns.includes(columnName));
  const foreignDependency = [...tables.entries()].flatMap(([name, candidate]) =>
    [...candidate.constraints.entries()].map(([constraintName, item]) => ({ name, constraintName, item })))
    .find(({ item }) => item.referencedTable === tableName && item.referencedColumns?.includes(columnName));
  if (localDependency || indexDependency || foreignDependency) {
    throw new Error(`${version} 列仍被约束、索引或外键引用，不能删除：${tableName}.${columnName}`);
  }
  state.columns.delete(columnName);
}

function validateTable(table, file) {
  if (!table || !Array.isArray(table.columns) || table.columns.length === 0) {
    throw new Error(`新增表必须包含列：${file}`);
  }
  validateIdentifier(table.name, `${file} 新增表`);
  if (!hasChineseText(table.comment)) throw new Error(`新增表缺中文说明：${file}`);
  for (const column of table.columns) validateColumn(column, file);
  if (table.primaryKey) validateNamedColumns(table.primaryKey, file);
  for (const item of table.uniqueConstraints ?? []) validateNamedColumns(item, file);
  for (const item of table.checkConstraints ?? []) {
    validateIdentifier(item.name, `${file} 检查约束`);
    validateCheckExpression(item.expression, file);
  }
  for (const item of table.foreignKeys ?? []) validateForeignKey(item, file);
  for (const item of table.indexes ?? []) validateIndex(item, file);
}

function validateColumn(column, file) {
  validateIdentifier(column?.name, `${file} 列`);
  const supportedTypes = new Set([
    "int64", "int32", "int16", "decimal", "float64", "boolean", "timestamp",
    "timestampTz", "date", "char", "string", "text",
  ]);
  if (!supportedTypes.has(column?.type)
      || typeof column.nullable !== "boolean"
      || typeof column.identity !== "boolean"
      || !Object.hasOwn(column ?? {}, "default")) {
    throw new Error(`规范列定义不完整：${file}.${column?.name ?? "unknown"}`);
  }
  if (!hasChineseText(column.comment)) {
    throw new Error(`规范列必须包含中文说明：${file}.${column.name}`);
  }
  if (["char", "string"].includes(column.type)
      && (!Number.isInteger(column.length) || column.length <= 0)) {
    throw new Error(`字符列缺少正整数 length：${file}.${column.name}`);
  }
  if (column.type === "decimal"
      && (!Number.isInteger(column.precision)
          || !Number.isInteger(column.scale)
          || column.precision <= 0
          || column.scale < 0
          || column.scale > column.precision)) {
    throw new Error(`小数列 precision/scale 不合法：${file}.${column.name}`);
  }
  if (column.default !== null
      && !["string", "number", "boolean"].includes(typeof column.default)) {
    throw new Error(`列 default 只允许字符串、数字、布尔值或 null：${file}.${column.name}`);
  }
}

function validateNamedColumns(value, file) {
  validateIdentifier(value?.name, `${file} 约束`);
  if (!Array.isArray(value?.columns) || value.columns.length === 0) throw new Error(`约束缺列：${file}`);
  for (const column of value.columns) validateIdentifier(column, `${file} 约束列`);
}

function validateForeignKey(value, file) {
  validateNamedColumns(value, file);
  validateIdentifier(value?.referencedTable, `${file} 外键目标表`);
  if (!Array.isArray(value?.referencedColumns) || value.referencedColumns.length !== value.columns.length) {
    throw new Error(`外键目标列数量不一致：${file}`);
  }
  for (const column of value.referencedColumns) validateIdentifier(column, `${file} 外键目标列`);
  const actions = new Set(["CASCADE", "SET NULL", "RESTRICT", "NO ACTION"]);
  if (value.onDelete != null && !actions.has(value.onDelete)) throw new Error(`外键 onDelete 不合法：${file}`);
  if (value.onUpdate != null && !actions.has(value.onUpdate)) throw new Error(`外键 onUpdate 不合法：${file}`);
}

function validateIndex(value, file) {
  validateIdentifier(value?.name, `${file} 索引`);
  if (typeof value?.unique !== "boolean") throw new Error(`索引 unique 必须为布尔值：${file}`);
  if (!Array.isArray(value?.columns) || value.columns.length === 0) throw new Error(`索引缺列：${file}`);
  for (const column of value.columns) {
    validateIdentifier(column?.name, `${file} 索引列`);
    if (!new Set(["ASC", "DESC"]).has(column.order)) throw new Error(`索引排序方向不合法：${file}`);
  }
}

function hasText(value) {
  return typeof value === "string" && value.trim().length > 0;
}

function hasChineseText(value) {
  return hasText(value) && /[\u3400-\u9fff]/u.test(value);
}

function validateCheckExpression(expression, file) {
  if (!hasText(expression)) throw new Error(`检查约束缺表达式：${file}`);
  const forbiddenDialectSyntax = /(::|\bNVL\s*\(|\bDECODE\s*\(|\bIFNULL\s*\(|\bAUTO_INCREMENT\b|\bSERIAL\b|`|\[|\])/iu;
  if (forbiddenDialectSyntax.test(expression) || /;|--|\/\*/u.test(expression)) {
    throw new Error(`检查约束必须使用五方言公共表达式子集：${file}`);
  }
}

function validateModel(schema) {
  if (schema.version !== 1 || !Array.isArray(schema.tables) || schema.tables.length === 0) {
    throw new Error("模式模型必须是非空 version=1 模型");
  }
  const tableNames = new Set();
  const globalNames = new Set();
  for (const table of schema.tables) {
    validateIdentifier(table.name, "表");
    if (tableNames.has(table.name)) throw new Error(`重复表：${table.name}`);
    if (forbiddenTables.has(table.name)) throw new Error(`全新基线禁止旧权限/会签表：${table.name}`);
    tableNames.add(table.name);
    const columnNames = new Set();
    for (const column of table.columns) {
      validateIdentifier(column.name, `${table.name} 列`);
      if (columnNames.has(column.name)) throw new Error(`重复列：${table.name}.${column.name}`);
      if (table.name === "mk_llm_eval_run" && forbiddenEvalColumns.has(column.name)) {
        throw new Error(`医学评测旧签署列不得进入基线：${column.name}`);
      }
      if (table.name === "mk_knowledge_acquisition_source"
          && forbiddenAcquisitionSourceColumns.has(column.name)) {
        throw new Error(`公域来源旧审批列不得进入基线：${column.name}`);
      }
      columnNames.add(column.name);
    }
    validateConstraintColumns(table, table.primaryKey, columnNames);
    for (const constraint of table.uniqueConstraints) validateConstraintColumns(table, constraint, columnNames);
    const keyColumnSignatures = new Set([
      ...(table.primaryKey ? [table.primaryKey.columns.join(",")] : []),
      ...table.uniqueConstraints.map((constraint) => constraint.columns.join(",")),
    ]);
    for (const constraint of table.checkConstraints) reserveName(globalNames, constraint.name, table.name);
    for (const foreignKey of table.foreignKeys) {
      validateConstraintColumns(table, foreignKey, columnNames);
      reserveName(globalNames, foreignKey.name, table.name);
    }
    for (const index of table.indexes) {
      reserveName(globalNames, index.name, table.name);
      const indexColumnSignature = index.columns.map((column) => column.name).join(",");
      if (keyColumnSignatures.has(indexColumnSignature)) {
        throw new Error(`冗余索引重复主键或唯一约束：${table.name}.${index.name}`);
      }
      for (const column of index.columns) {
        if (!columnNames.has(column.name)) throw new Error(`索引引用不存在列：${table.name}.${column.name}`);
      }
    }
  }
  for (const table of schema.tables) {
    for (const foreignKey of table.foreignKeys) {
      const referenced = schema.tables.find((candidate) => candidate.name === foreignKey.referencedTable);
      if (!referenced) throw new Error(`外键引用不存在表：${foreignKey.referencedTable}`);
      const referencedColumns = new Set(referenced.columns.map((column) => column.name));
      for (const column of foreignKey.referencedColumns) {
        if (!referencedColumns.has(column)) {
          throw new Error(`外键引用不存在列：${foreignKey.referencedTable}.${column}`);
        }
      }
    }
  }
}

function validateConstraintColumns(table, constraint, columnNames) {
  if (!constraint) return;
  for (const column of constraint.columns) {
    if (!columnNames.has(column)) throw new Error(`约束引用不存在列：${table.name}.${column}`);
  }
}

function reserveName(names, name, table) {
  validateIdentifier(name, `${table} 对象`);
  if (names.has(name)) throw new Error(`跨表对象名称重复：${name}`);
  names.add(name);
}

function validateIdentifier(identifier, label) {
  if (typeof identifier !== "string" || !/^[a-z][a-z0-9_]*$/.test(identifier)) {
    throw new Error(`${label}标识符不规范：${identifier}`);
  }
}

function renderChangeSet(changeSet, dialect) {
  const lines = [
    `-- MedKernel 数据库迁移 V${changeSet.version}（${dialectLabel(dialect)}）`,
    "-- 本文件由 scripts/db/generate-migrations.mjs 根据 db/schema/migrations 中的规范变更生成，请勿手工修改。",
    `-- ${changeSet.description}`,
    "",
  ];
  for (const operation of changeSet.operations) {
    lines.push(...renderOperation(operation, dialect), "");
  }
  return `${lines.join("\n").trimEnd()}\n`;
}

function renderOperation(operation, dialect) {
  switch (operation.type) {
    case "addTable":
      return renderAddedTable(operation.definition, dialect);
    case "addColumn":
      return [
        `ALTER TABLE ${operation.table} ADD ${renderColumn(operation.column, dialect)};`,
        `COMMENT ON COLUMN ${operation.table}.${operation.column.name} IS ${sqlString(operation.column.comment)};`,
      ];
    case "addPrimaryKey":
      return [renderKeyConstraint(operation.table, "PRIMARY KEY", operation.constraint)];
    case "addUniqueConstraint":
      return [renderKeyConstraint(operation.table, "UNIQUE", operation.constraint)];
    case "addCheckConstraint":
      return [
        `ALTER TABLE ${operation.table} ADD CONSTRAINT ${operation.constraint.name} CHECK (${renderCheck(operation.constraint.expression, dialect)});`,
      ];
    case "addForeignKey":
      return [renderForeignKey(operation.table, operation.foreignKey, dialect)];
    case "addIndex":
      return [renderIndex(operation.table, operation.index)];
    case "renameTable":
      return [`ALTER TABLE ${operation.table} RENAME TO ${operation.to};`];
    case "renameColumn":
      return [`ALTER TABLE ${operation.table} RENAME COLUMN ${operation.from} TO ${operation.to};`];
    case "dropIndex":
      return [`DROP INDEX ${operation.name};`];
    case "dropConstraint":
      return [`ALTER TABLE ${operation.table} DROP CONSTRAINT ${operation.name};`];
    case "dropColumn":
      return [`ALTER TABLE ${operation.table} DROP COLUMN ${operation.name};`];
    case "dropTable":
      return [`DROP TABLE ${operation.table};`];
    default:
      throw new Error(`不支持的规范迁移操作：${operation.type}`);
  }
}

function renderAddedTable(table, dialect) {
  const lines = [`CREATE TABLE ${table.name} (`];
  table.columns.forEach((column, index) => {
    lines.push(`    ${renderColumn(column, dialect)}${index + 1 < table.columns.length ? "," : ""}`);
  });
  lines.push(");");
  if (table.primaryKey) lines.push(renderKeyConstraint(table.name, "PRIMARY KEY", table.primaryKey));
  for (const constraint of table.uniqueConstraints ?? []) {
    lines.push(renderKeyConstraint(table.name, "UNIQUE", constraint));
  }
  for (const constraint of table.checkConstraints ?? []) {
    lines.push(
      `ALTER TABLE ${table.name} ADD CONSTRAINT ${constraint.name} CHECK (${renderCheck(constraint.expression, dialect)});`,
    );
  }
  for (const foreignKey of table.foreignKeys ?? []) lines.push(renderForeignKey(table.name, foreignKey, dialect));
  for (const index of table.indexes ?? []) lines.push(renderIndex(table.name, index));
  lines.push(`COMMENT ON TABLE ${table.name} IS ${sqlString(table.comment)};`);
  for (const column of table.columns) {
    lines.push(`COMMENT ON COLUMN ${table.name}.${column.name} IS ${sqlString(column.comment)};`);
  }
  return lines;
}

function renderForeignKey(table, foreignKey, dialect) {
  let sql = `ALTER TABLE ${table} ADD CONSTRAINT ${foreignKey.name} FOREIGN KEY (${foreignKey.columns.join(", ")}) REFERENCES ${foreignKey.referencedTable} (${foreignKey.referencedColumns.join(", ")})`;
  if (foreignKey.onDelete) sql += ` ON DELETE ${foreignKey.onDelete}`;
  if (foreignKey.onUpdate && !["oracle", "dm"].includes(dialect)) sql += ` ON UPDATE ${foreignKey.onUpdate}`;
  return `${sql};`;
}

function renderIndex(table, index) {
  const columns = index.columns
    .map((column) => `${column.name}${column.order === "DESC" ? " DESC" : ""}`)
    .join(", ");
  return `CREATE ${index.unique ? "UNIQUE " : ""}INDEX ${index.name} ON ${table} (${columns});`;
}

function renderBaseline(schema, dialect) {
  const lines = [
    `-- MedKernel 全新上线数据库基线（${dialectLabel(dialect)}）`,
    "-- 本文件由 scripts/db/generate-migrations.mjs 根据 db/schema/medkernel.schema.json 生成，请勿手工修改。",
    "-- 仅包含终态结构；固定职责、权限包与模型能力目录由应用代码播种。",
    "",
  ];

  for (const table of schema.tables) {
    lines.push(`CREATE TABLE ${table.name} (`);
    table.columns.forEach((column, index) => {
      const suffix = index + 1 < table.columns.length ? "," : "";
      lines.push(`    ${renderColumn(column, dialect)}${suffix}`);
    });
    lines.push(");", "");
  }

  lines.push("-- 主键、唯一约束与检查约束", "");
  for (const table of schema.tables) {
    if (table.primaryKey) {
      lines.push(renderKeyConstraint(table.name, "PRIMARY KEY", table.primaryKey));
    }
    for (const constraint of table.uniqueConstraints) {
      lines.push(renderKeyConstraint(table.name, "UNIQUE", constraint));
    }
    for (const constraint of table.checkConstraints) {
      lines.push(
        `ALTER TABLE ${table.name} ADD CONSTRAINT ${constraint.name} CHECK (${renderCheck(constraint.expression, dialect)});`,
      );
    }
  }
  lines.push("");

  lines.push("-- 外键", "");
  for (const table of schema.tables) {
    for (const foreignKey of table.foreignKeys) {
      let sql = `ALTER TABLE ${table.name} ADD CONSTRAINT ${foreignKey.name} FOREIGN KEY (${foreignKey.columns.join(", ")}) REFERENCES ${foreignKey.referencedTable} (${foreignKey.referencedColumns.join(", ")})`;
      if (foreignKey.onDelete) sql += ` ON DELETE ${foreignKey.onDelete}`;
      if (foreignKey.onUpdate && !["oracle", "dm"].includes(dialect)) sql += ` ON UPDATE ${foreignKey.onUpdate}`;
      lines.push(`${sql};`);
    }
  }
  lines.push("");

  lines.push("-- 查询索引", "");
  for (const table of schema.tables) {
    for (const index of table.indexes) {
      const columns = index.columns
        .map((column) => `${column.name}${column.order === "DESC" ? " DESC" : ""}`)
        .join(", ");
      lines.push(`CREATE ${index.unique ? "UNIQUE " : ""}INDEX ${index.name} ON ${table.name} (${columns});`);
    }
  }
  lines.push("");

  lines.push("-- 中文结构说明", "");
  for (const table of schema.tables) {
    lines.push(`COMMENT ON TABLE ${table.name} IS ${sqlString(table.comment || `业务表：${table.name}`)};`);
    for (const column of table.columns) {
      lines.push(
        `COMMENT ON COLUMN ${table.name}.${column.name} IS ${sqlString(column.comment || commonColumnComment(column.name))};`,
      );
    }
  }
  lines.push("");
  return `${lines.join("\n").trimEnd()}\n`;
}

function renderColumn(column, dialect) {
  const parts = [column.name, renderType(column, dialect)];
  if (column.identity) parts.push(renderIdentity(dialect));
  if (column.default !== null && !column.identity) parts.push(`DEFAULT ${renderDefault(column.default, dialect)}`);
  if (!column.nullable) parts.push("NOT NULL");
  return parts.join(" ");
}

function renderType(column, dialect) {
  const oracleFamily = dialect === "oracle" || dialect === "dm";
  switch (column.type) {
    case "int64":
      return oracleFamily ? "NUMBER(19)" : "BIGINT";
    case "int32":
      return oracleFamily ? "NUMBER(10)" : "INTEGER";
    case "int16":
      return oracleFamily ? "NUMBER(5)" : "SMALLINT";
    case "decimal":
      return `NUMERIC(${column.precision},${column.scale})`;
    case "float64":
      return oracleFamily ? "BINARY_DOUBLE" : "DOUBLE PRECISION";
    case "boolean":
      return oracleFamily ? "NUMBER(1)" : "BOOLEAN";
    case "timestamp":
      return "TIMESTAMP";
    case "timestampTz":
      return oracleFamily ? "TIMESTAMP WITH TIME ZONE" : "TIMESTAMPTZ";
    case "date":
      return "DATE";
    case "char":
      return oracleFamily ? `CHAR(${column.length})` : `CHAR(${column.length})`;
    case "string":
      if (dialect === "oracle" && column.length > 4000) return "CLOB";
      return oracleFamily ? `VARCHAR2(${column.length})` : `VARCHAR(${column.length})`;
    case "text":
      return oracleFamily ? "CLOB" : "TEXT";
    default:
      throw new Error(`未知规范类型：${column.type}`);
  }
}

function renderIdentity(dialect) {
  if (dialect === "dm") return "IDENTITY";
  return "GENERATED BY DEFAULT AS IDENTITY";
}

function renderDefault(value, dialect) {
  if (dialect === "oracle" || dialect === "dm") {
    if (value === "TRUE") return "1";
    if (value === "FALSE") return "0";
  }
  return value;
}

function renderCheck(expression, dialect) {
  let normalized = expression.replace(
    /CAST\(([-+]?\d+(?:\.\d+)?) AS (?:BIGINT|INTEGER|NUMERIC\(\d+(?:,\s*\d+)?\))\)/gi,
    "$1",
  );
  if (dialect === "oracle" || dialect === "dm") {
    normalized = normalized.replace(/\bTRUE\b/g, "1").replace(/\bFALSE\b/g, "0");
  }
  return normalized;
}

function renderKeyConstraint(table, kind, constraint) {
  return `ALTER TABLE ${table} ADD CONSTRAINT ${constraint.name} ${kind} (${constraint.columns.join(", ")});`;
}

function sqlString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function commonColumnComment(name) {
  const exact = {
    id: "数据库主键",
    tenant_id: "租户标识",
    status: "当前状态",
    active_flag: "是否启用：Y/N",
    created_at: "创建时间",
    created_by: "创建人",
    updated_at: "更新时间",
    updated_by: "更新人",
    trace_id: "追踪号",
    version: "并发版本号",
    lock_version: "并发锁版本号",
  };
  return exact[name] || `业务字段：${name}`;
}

function dialectLabel(dialect) {
  return {
    postgres: "PostgreSQL",
    kingbase: "人大金仓 KingbaseES",
    oracle: "Oracle",
    dm: "达梦 DM",
    h2: "H2 测试方言",
  }[dialect];
}
