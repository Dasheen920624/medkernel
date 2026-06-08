# 会话接力

## 唯一执行组织

- 当前分支：`codex/permission-separation`
- 基线：`origin/main` = `8fb5b9fe`（6.1 租户开通引用制已通过 PR #501 合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- P13-5 开医嘱实时 CDS、P11 标准互操作映射器、规则/路径创作收尾、运行期资产继承解析、组织作用域二期、租户开通引用制已分别通过 PR #496 / #497 / #498 / #499 / #500 / #501 合入 `main`。
- 当前推进 OpenSpec `platform-first-knowledge-inheritance`：6.2 权限分离已实现并勾选，平台版本发布/激活要求 `platform.publish`，租户版本发布/机构覆盖要求 `tenant.override`，且拒绝跨当前请求租户。
- 高风险覆盖（REVIEW / LOCKED / SAFETY_REDLINE）进入 `IN_REVIEW`，运行期继承解析只采纳 `PUBLISHED` 覆盖；平台基线可被租户引用覆盖，不复制平台版本。

## 当前证据

- 后端：`mvn -q test` 已通过；focused `mvn -q -Dtest=VersionReleaseServiceTest,InheritanceOverrideServiceTest,InheritanceResolverTest,InheritanceOverrideRepositoryTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 已通过；`mvn -q -Dtest=FlywayMultiDialectSmokeTest test` 返回 0（本机无 Docker socket，容器方言由 CI 继续验证）。
- 前端：`npm run verify` 已通过（81 files / 578 tests）。
- OpenSpec：`openspec validate platform-first-knowledge-inheritance --strict`、`openspec validate --all --strict` 已通过。
- 门禁：`git diff --check`、`scripts/check-comment-zh.sh`、真实性/配置边界/迁移规约 all 模式已通过。

## 下一步

1. 推送 6.2 分支，创建 PR，远端 CI 绿后 squash 合入 `main`。
2. 回到最新 `main` 后继续 `platform-first-knowledge-inheritance` 6.3（上游变更影响计算 + 继承差异视图 + rebase 提示），并继续按登录后主流程核查全部已 done 能力。
