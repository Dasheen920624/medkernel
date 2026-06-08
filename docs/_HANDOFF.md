# 会话接力

## 唯一执行组织

- 当前分支：`codex/inheritance-governance-ui`
- 基线：`origin/main` = `0402eb06`（6.3 继承影响分析与 rebase 提示已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- P13-5 开医嘱实时 CDS、P11 标准互操作映射器、规则/路径创作收尾、运行期资产继承解析、组织作用域二期、租户开通引用制、平台/租户治理权限分离、继承影响分析已分别通过 PR #496 / #497 / #498 / #499 / #500 / #501 / #502 / #503 合入 `main`。
- 当前推进 OpenSpec `platform-first-knowledge-inheritance` 6.4：配置包中心已接入平台/租户/机构视角过滤与统计、继承影响展示、有效版本来源标识、ADD/REPLACE/DISABLE 初始覆盖例外编辑，以及条件片段/术语包资产类型选择的可用性修复。
- 为避免前端假入口，后端同步落地 `InheritanceOverrideMode.ADD`：ADD 不要求继承版本，要求本级 ACTIVE 覆盖版本，且平台基线已存在时拒绝 ADD；五方言 V55 同步允许 `inherited_version_id` 为空并纳入 ADD 约束。

## 当前证据

- 6.4 前端聚焦：`npm test -- --run src/shared/api/hooks.test.ts src/pages/tenant/ConfigPackages.test.tsx` 已通过（2 files / 105 tests）。
- 6.4 前端全量：`npm run verify` 已通过（lint / stylelint / lint-rules / format / typecheck / 81 files / 581 tests）。
- 6.4 后端聚焦：`mvn -q -Dtest=InheritanceOverrideServiceTest,InheritanceResolverTest test` 已通过。
- 6.4 后端全量：`mvn -q test` 已通过。
- 6.4 OpenSpec：`openspec validate platform-first-knowledge-inheritance --strict`、`openspec validate --all --strict` 已通过。
- 6.4 门禁：`git diff --check`、`scripts/check-comment-zh.sh`、真实性/配置边界/迁移规约 all 模式已通过。

## 下一步

1. 提交并推送 `codex/inheritance-governance-ui`，创建 PR，远端 CI 绿后合入 `main`。
2. 回到最新 `main` 后继续 `platform-first-knowledge-inheritance` 6.5 互操作项，或按主流程复验已 done 页面与线2/3/4承接项。
