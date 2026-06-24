# 数据库模式与迁移

## 1. 单一真相源

首次上线结构只维护一份规范化模型：

`medkernel-backend/src/main/resources/db/schema/medkernel.schema.json`

当前模型版本为 `1`，包含 207 张终态表。固定职责、权限包、模型目录和安全策略由应用播种器
维护，不在迁移中重复维护业务目录。

上线后的每个版本只新增一份规范化变更清单：

`medkernel-backend/src/main/resources/db/schema/migrations/V2__<name>.json`

V2、V3……按版本连续递增；变更清单使用数据库无关的表、列、约束、索引和重命名操作，
不接受五份手写 SQL。

## 2. 一源维护、五方言部署

执行：

```bash
node scripts/db/generate-migrations.mjs
```

生成：

- `db/migration/postgres/V1__baseline.sql`
- `db/migration/kingbase/V1__baseline.sql`
- `db/migration/oracle/V1__baseline.sql`
- `db/migration/dm/V1__baseline.sql`
- `db/migration/h2/V1__baseline.sql`

每个版本的五份 SQL 只能由生成器产生，不手工修改。生成器集中处理标识列、布尔值、时间戳、
文本、默认值、约束、索引和中文注释差异。没有规范变更源的 V2 及后续 SQL 会被直接拒绝，
不会被静默删除。

CI 使用只读检查验证五份产物与单一模式源逐字一致：

```bash
node scripts/db/generate-migrations.mjs --check
```

因此 V1 只修改一份模式模型，后续每个版本只修改一份规范变更；五份 SQL 是 Flyway 可直接
执行、可审阅的部署产物，不是五份独立维护的源文件。

## 3. 版本递增规则

- 本次首次正式上线的每个方言只有一个 `V1__baseline.sql`。
- 不兼容历史迁移链，不执行旧版本升级。
- 项目尚未上线时，结构变化直接更新模式模型并重新生成 V1。
- 部署前必须备份并验证可恢复；清库后从空库执行 V1。
- 首次正式上线完成即冻结 V1；之后任何结构变化只新增 V2、V3、V4……，禁止改写或重新压缩已执行版本。
- 同一迁移生成器同时生成 V1 和后续全部版本；版本号必须连续，五方言文件名和内容必须同源。
- 已上线的规范变更清单和生成 SQL 均永久保留；新增版本不会删除或重写旧版本。

## 4. 验证

```bash
cd medkernel-backend
mvn -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest test
```

验证范围：

- 模式模型与五方言表、列、约束、索引一致；
- 所有表和字段包含中文说明；
- H2、PostgreSQL、Oracle 空库真实执行；
- Kingbase、达梦执行静态方言合同；
- `--check` 保证模式源与五方言产物无差异；
- V2/V3 夹具保证一份规范变更可生成五方言同版本 SQL；
- 无规范源的手写迁移会失败且不被删除；
- 旧角色覆盖、人员会签和来源审批表列不得重新进入模式。
