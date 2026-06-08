# 会话接力

## 唯一执行组织

- 当前分支：`codex/asset-dependency-55`
- 基线：`origin/main` = `4236e559`（PR #505 / 6.5 互操作 CQL 受控导入已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- P13-5 开医嘱实时 CDS、P11 标准互操作映射器、规则/路径创作收尾、运行期资产继承解析、组织作用域二期、租户开通引用制、平台/租户治理权限分离、继承影响分析、继承治理前端与 ADD 覆盖、6.5 互操作 CQL 受控导入已合入 `main`。
- 当前推进 OpenSpec `platform-first-knowledge-inheritance` 5.5：版本底座已新增 `mk_version_asset_dependency` 依赖图；版本登记/草稿更新可声明依赖；发布/激活前校验依赖在同作用域或平台基线可解析且版本兼容；DISABLE 覆盖会阻断仍被 PUBLISHED/ACTIVE 资产依赖的目标；`InheritanceResolver.resolveWithDependencies(...)` 返回根资产、依赖资产、epoch bindings 与 SHA-256 resolution epoch。
- 5.5 任务已勾选；本地全量门禁已通过，等待提交、PR、远端 CI 与合并。

## 当前证据

- 5.5 红测：`mvn -q -Dtest=AssetDependencyServiceTest,InheritanceResolverTest,VersionReleaseServiceTest,MigrationBaselineContractTest test` 曾按预期因缺少依赖图类型/服务/迁移失败。
- 5.5 聚焦绿测：`mvn -q -Dtest=AssetDependencyServiceTest,InheritanceResolverTest,VersionReleaseServiceTest,MigrationBaselineContractTest test` 已通过。
- 5.5 相邻回归：`mvn -q -Dtest=AssetVersionServiceTest,InheritanceOverrideServiceTest test` 已通过。
- 失败根因回归：`mvn -q -Dtest=FlywayMultiDialectSmokeTest,H2BaselineMigrationTest,EvaluationEngineIntegrationTest test` 已通过。
- 后端全量：`mvn -q test` 已通过；本机无 Docker，PostgreSQL / Oracle Testcontainers 用例按测试假设跳过，H2 全迁移烟测通过 107 条迁移。
- 前端全量：`npm run verify` 已通过（81 个测试文件 / 581 个用例）。
- OpenSpec：`openspec validate platform-first-knowledge-inheritance --strict`、`openspec validate --all --strict` 已通过。
- T-GATE：`git diff --check`、`scripts/check-comment-zh.sh`、`node scripts/authenticity-guard.mjs --mode=all`、`node scripts/config-boundary-guard.mjs --mode=all`、`node scripts/migration-convention-guard.mjs --mode=all` 已通过。
- 待补：远端 CI。

## 下一步

1. 提交并推送 `codex/asset-dependency-55`，创建 PR。
2. 远端 CI 绿后合入 `main`。
3. 回到最新 `main` 后继续同一 OpenSpec 的最早未完成项 5.6 统一生命周期状态机 + 高风险电子签名 + 平台发布质量门。
