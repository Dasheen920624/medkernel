# SVC-PILOT-03 资产准备服务包实施记录

> 日期：2026-06-03
> 分支：`codex/d2-svc-pilot-03`

## 范围

- 新增试点首发配置包模板模型：`PilotPackageTemplate`、`PilotPackageTemplateItem`，五方言 V65 迁移建立 `mk_pkg_pilot_package_template` / `mk_pkg_pilot_template_item`。
- 新增配置包服务端点：模板列表、模板实例化、资产就绪快照。
- 模板实例化在保存草稿前完成模板存在性、包编码版本唯一性、必需资产真实存在与已发布状态校验；缺失依赖返回 `PACKAGE_DEPENDENCY_MISSING`，不保存半成品。
- 配置包中心新增“首发资产准备”区和“从首发模板创建”弹窗；状态、阻塞项和灰度证据来自后端事实复算。
- 清理当前触碰范围旧口径：`PhysicalSha256` 测试名与“物理推进”Javadoc。

## 不冒领

- 不在迁移里写假首发模板或假医学资产 seed；模板必须由真实配置 / 导入产生。
- 不宣称达梦 / 人大金仓真实运行已通过；仍按 `DEFER-001` 在最终国产化适配阶段处理。
- 不关闭 `DEFER-002` / `DEFER-003` / `DEFER-004`；生产依赖审计和浏览器验收继续按本轮实际证据陈述。

## 已跑验证

- 后端目标：`mvn -q -Dtest=PackageEngineServiceTest,PackageEngineControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test`
- 后端全量：`mvn -q test`（退出码 0；H2 / PostgreSQL 15.18 / Oracle 21.3 迁移至 v65 并二次 no-op）
- 迁移门禁：`node scripts/migration-convention-guard.mjs --mode=files medkernel-backend/src/main/resources/db/migration/{h2,postgres,oracle,dm,kingbase}/V65__pilot_package_template.sql`
- 前端目标：`npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx src/pages/tenant/RulePathwayCleanliness.test.ts`
- 前端全量：`npm run verify`（第二次 rebase 后 47 files / 268 tests，退出码 0）
- 前端生产依赖审计：`npm audit --omit=dev --audit-level=moderate`（0 vulnerabilities）
- 前端构建：`npm run build`（退出码 0；仍有已登记的 `vendor-antd` 大 chunk 警告，归 `DEFER-003`，不冒领清零）
- T-GATE：`node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs`（34/34 通过）
- T-GATE inventory：`node scripts/authenticity-guard.mjs --mode=inventory`、`node scripts/config-boundary-guard.mjs --mode=inventory` 均通过。
- 中文注释：`scripts/check-comment-zh.sh --self-test`（6/6 通过）、`scripts/check-comment-zh.sh`（0 fail / 0 warn）。
- 提交后 changed-mode：`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（17 文件）、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`（15 文件）、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（5 文件）均通过。
- 空白检查：`git diff --check` 与 `git diff --check origin/main..HEAD` 均通过。

PR、远端 CI 与合并状态以 `_HANDOFF.md` 后续更新为准。
