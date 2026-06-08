# 会话接力

## 唯一执行组织

- 当前分支：`codex/harden-cycle-boundary-guards`
- 基线：`origin/main` = `fa085d10`（H-3 `feat: 增强规则治理分权与审计` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- P9-1 至 H-4 已按线1统一路径承接；当前 OpenSpec `pathway-rule-authoring-overhaul` 的 P-HARDEN 已完成 H-1/H-2/H-3/H-4。
- H-4 收口路径 / 规则边界护栏：路径发布拒绝可达有向环；`SUBPATHWAY` 不能引用当前路径模板；仿真超过节点数最大步数即拒绝，避免旧图无限推进。
- 规则 DSL 对值集展开设置 10,000 成员上限；值集缺展开、单位不可换算、公式非法入参、参考范围缺失、时间戳缺失等确定性失败均返回 `UNKNOWN` 条件证据，并携带错误码 / 错误消息，不估算、不随机放过。
- 条件片段库当前只有 OpenSpec 设计与能力开关，无 `condition_fragment` 运行模型；片段环检测随 P12-5 条件片段库落地接入，不在 H-4 伪造实现。
- OpenSpec H-4 已勾选；RULE-01 / PATH-01 只补最小进度、FR 与 AC，不新增施工文档。

## 当前证据

- 后端红灯：H-4 聚焦测试先失败于单位 / 公式失败直接抛异常、路径发布未拒绝环 / 子路径自引用、旧仿真未触发最大步数护栏，以及值集对象未展开。
- 后端聚焦：`mvn -q -Dtest=RuleDslEvaluatorTest,PathwayEngineServiceTest test` 通过；值集边界聚焦 `mvn -q -Dtest=RuleDslEvaluatorTest#inOperatorAcceptsBoundedExpandedValueSetMembers+inOperatorReturnsUnknownEvidenceWhenExpandedValueSetExceedsLimit test` 通过。
- 后端全量：`mvn -q test` 通过，Surefire 汇总 1911 tests / 0 failures / 0 errors / 3 skipped；Docker 不可用仅触发 Testcontainers 探测日志，最终退出码 0。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 通过，38 tests / 0 fail。
- T-GATE changed-mode：authenticity 4 files、config-boundary 4 files、migration 0 files，全部通过。
- 中文注释与空白：`bash scripts/check-comment-zh.sh` 通过，0 fail / 0 warn；`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/harden-cycle-boundary-guards`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 H-5，不恢复并行线。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
