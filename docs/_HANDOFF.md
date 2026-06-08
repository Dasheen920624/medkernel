# 会话接力

## 唯一执行组织

- 当前分支：`codex/tenant-onboarding-reference`
- 基线：`origin/main` = `8008ebf4`（组织作用域二期已合入，PR #500）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- P13-5 开医嘱实时 CDS、P11 标准互操作映射器、规则/路径创作收尾、运行期资产继承解析、组织作用域二期已分别通过 PR #496 / #497 / #498 / #499 / #500 合入 `main`。
- 当前推进 OpenSpec `platform-first-knowledge-inheritance`：6.1 租户开通引用制已实现、勾选并完成本地全量门禁，待提交 PR、远端 CI 绿后合入。
- 组织模型已统一为 `PLATFORM/TENANT/REGION/FACILITY/CAMPUS/DEPARTMENT/WARD`；`GROUP` 归一为 `REGION`，`HOSPITAL/SITE` 归一为 `FACILITY + facilityType`，专病保留为 `applicableScope` 横切维度。
- 新增 `mk_org_secondary_membership` 表与 API，支持同租户内可选次级归属；`InheritanceResolver` 读取主链 + 次级归属闭包，运行期解析保持唯一组织轴。
- 发布作用域已同步收敛为 `ALL/REGION/FACILITY/CAMPUS/DEPARTMENT/WARD`，OpenSpec 文档中的旧组织层级例子已同步改为新口径。
- 首发模板从“实例化租户草案包”改为“应用平台包引用”；新增 `mk_pkg_tenant_package_reference`，配置包中心按钮改为“应用首发引用”，租户向导 ASSETS 步骤认 ACTIVE 平台包引用。

## 当前证据

- 后端：`mvn -q test` 已通过；focused `mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,TenantPilotServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest test` 已通过。
- 前端：`npm run verify` 已通过（81 files / 578 tests）；focused `npm test -- src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx` 已通过（102 tests）。
- OpenSpec：`openspec validate platform-first-knowledge-inheritance --strict`、`openspec validate --all --strict` 已通过。
- 门禁：`git diff --check`、`scripts/check-comment-zh.sh`、真实性/配置边界/迁移规约 all 模式已通过；当前 6.1 触碰范围旧 `instantiate` 入口扫描无命中。

## 下一步

1. 提交、推送、创建 PR；远端 CI 绿后 squash 合入 `main`。
2. 回到最新 `main` 后继续 `platform-first-knowledge-inheritance` 下一项 6.2（权限分离），并继续按登录后主流程核查全部已 done 能力。
