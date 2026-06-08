# 会话接力

## 唯一执行组织

- 当前分支：`codex/org-scope-second-phase`
- 基线：`origin/main` = `aa56ee3a`（运行期资产继承解析已合入，PR #499）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- P13-5 开医嘱实时 CDS、P11 标准互操作映射器、规则/路径创作收尾、运行期资产继承解析已分别通过 PR #496 / #497 / #498 / #499 合入 `main`。
- 当前推进 OpenSpec `platform-first-knowledge-inheritance`：1.0b 组织树二期已实现并勾选。
- 组织模型已统一为 `PLATFORM/TENANT/REGION/FACILITY/CAMPUS/DEPARTMENT/WARD`；`GROUP` 归一为 `REGION`，`HOSPITAL/SITE` 归一为 `FACILITY + facilityType`，专病保留为 `applicableScope` 横切维度。
- 新增 `mk_org_secondary_membership` 表与 API，支持同租户内可选次级归属；`InheritanceResolver` 读取主链 + 次级归属闭包，运行期解析保持唯一组织轴。
- 发布作用域已同步收敛为 `ALL/REGION/FACILITY/CAMPUS/DEPARTMENT/WARD`，OpenSpec 文档中的旧组织层级例子已同步改为新口径。

## 当前证据

- 后端：`mvn -q test` 已通过；组织/迁移焦点复跑 `mvn -q -Dtest=OrgLevelTest,OrgHierarchyIntegrationTest,OrgUnitServiceTest,OrgUnitRepositoryTest,MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest test` 已通过。
- 前端：`npm run verify` 已通过（81 files / 578 tests）。
- OpenSpec：`openspec validate platform-first-knowledge-inheritance --strict`、`openspec validate --all --strict` 已通过。
- 门禁：`git diff --check`、`scripts/check-comment-zh.sh`、真实性/配置边界/迁移规约 changed+all 模式已通过。

## 下一步

1. 最终复跑全量门禁后提交、推送、创建 PR；远端 CI 绿后 squash 合入 `main`。
2. 回到最新 `main` 后继续 `platform-first-knowledge-inheritance` 下一项 6.1（租户开通引用制 + PilotPackageTemplate 推荐引用），并继续按登录后主流程核查全部已 done 能力。
