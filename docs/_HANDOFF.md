# 会话接力

## 唯一执行组织

- 当前分支：`codex/harden-governance-separation-audit`
- 基线：`origin/main` = `aa678c7b`（H-2 `feat: 增强事件触发求值超时降级` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 至 H-3 已按线1统一路径承接；当前 OpenSpec `pathway-rule-authoring-overhaul` 的 P-HARDEN 已完成 H-1/H-2/H-3。
- H-3 收口职责分离门禁：作者不能自审，当前会签人不能发布，发布人与作者/会签人必须分离。
- 人工越权仅绑定当前租户真实命中执行记录；跨租户执行记录不可见且不会写入 override 或审计。
- 审计哈希链把摘要与租户纳入 canonical 签名口径；摘要或租户被篡改时验签失败。
- OpenSpec H-3 已勾选；RULE-01 只补最小进度、FR 与 AC，不新增施工文档。

## 当前证据

- 后端红灯：`mvn -q -Dtest=AuditChainWriterTest#tamperingSummaryBreaksVerification test` 先失败于篡改 summary 后验签仍为 true。
- 后端聚焦：`mvn -q -Dtest=RuleGovernanceServiceTest,RuleEngineServiceTest,AuditChainWriterTest test` 通过。
- 后端全量：`mvn -q test` 通过，Surefire 汇总 1905 tests / 0 failures / 0 errors / 3 skipped；Docker 不可用仅触发 Testcontainers 探测日志，最终退出码 0。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 通过，38 tests / 0 fail。
- T-GATE changed-mode：authenticity 1 file、config-boundary 1 file、migration 0 files，全部通过。
- 中文注释与空白：`bash scripts/check-comment-zh.sh` 通过，0 fail / 0 warn；`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/harden-governance-separation-audit`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 H-4，不恢复并行线。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
