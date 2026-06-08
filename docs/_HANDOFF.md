# 会话接力

## 唯一执行组织

- 当前分支：`codex/harden-authoring-feature-flags`
- 基线：`origin/main` = `3b2bf75b`（P10-4 `feat: 完成路径结局指标与回放协调闭环` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 至 P10-4 已合入主线；当前继续 OpenSpec `pathway-rule-authoring-overhaul` 的 P-HARDEN。
- H-1 已在当前分支完成本地实现：递归条件树、临床算子、条件片段库、批量创作、路径富节点统一进入 `medkernel.runtime.feature-flags.*.enabled` 运行配置。
- 已交付能力默认保持可用：递归条件树、临床算子、路径富节点默认启用；后续尚未实现的片段库、批量创作默认关闭。
- 运行期按当前租户读取覆盖配置；租户未配置或配置中心不可达时回到系统安全默认；未知 feature flag 返回 false。
- 规则/路径共用 `ConditionEvaluator`：临床算子关闭时在取值和计算前诚实拒绝；递归树关闭时单层 `all/any` 继续可用，嵌套组拒绝；未知算子仍返回 DSL 算子错误。
- 路径富节点关闭时，模板发布门禁与患者路径推进都会拒绝 DECISION/PARALLEL/WAIT_TIMER/SUBPATHWAY/MANUAL_GATE/ORDER_SET，不继续误算。
- 系统配置中心新增租户覆盖读写：前端“系统配置”页可在系统默认 / 租户覆盖间切换；首次保存租户覆盖会从系统元数据种子，不继承系统版本锁；仅审计持久化和国密增强保持界面硬锁，其余高风险配置走确认和后端门禁。
- OpenSpec H-1 已勾选；不新增施工文档。

## 当前证据

- 后端红灯：`mvn -q -Dtest=ConditionEvaluatorFeatureGateTest,PathwayProgressorFeatureGateTest,SystemConfigServiceTest test` 先因缺 `engine.authoring` 开关入口、租户读取方法和推进器构造器失败。
- 后端聚焦：同命令已通过。
- 前端聚焦：`npm test -- --run src/shared/api/hooks.test.ts src/pages/compliance/SecurityBaseline.test.tsx` 通过，2 files / 83 tests。
- 后端全量：`mvn -q test` 通过，Surefire 汇总 1896 tests / 0 failures / 0 errors / 3 skipped；Docker 不可用仅触发 Testcontainers 探测日志，最终退出码 0。
- 前端全量：`npm run verify` 通过，Vitest 78 files / 550 tests，lint / stylelint / lint-rules / format / typecheck 同步通过。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 通过，38 tests / 0 fail。
- T-GATE changed-mode：authenticity 12 files、config-boundary 10 files、migration 0 files，全部通过。
- 中文注释与空白：`bash scripts/check-comment-zh.sh` 通过；`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/harden-authoring-feature-flags`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 H-2，不恢复并行线。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
