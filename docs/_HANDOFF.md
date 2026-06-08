# 会话接力

## 唯一执行组织

- 当前分支：`codex/realtime-cds-order-sign`
- 基线：`origin/main` = `66a12384`（P13-4 CKD 专病包端到端已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 已完成 P13-5 本地实现与本地全量验证，待提交、推送、PR、CI 与合入 `main`。
- 新增实时 CDS Hook 入口 `POST /api/v1/engine/cds-hooks:evaluate`，医生动作点使用 `recommendation.accept` 权限；旧 `/api/v1/engine/recommendations:evaluate` 仍保持受控写入权限，不作为开医嘱入口。
- 实时 CDS Hook 门面复用 `RecommendationEngineService.evaluate`，返回现有 CDS Hooks Card 结构；order-sign 默认硬超时 1s，默认实时 CDS 硬超时 2s，均可由配置中心毫秒键覆盖。
- 超时、线程中断、上下文快照不可用或评估异常时返回 critical「CDS 求值不可用」人工核查卡，不产出 systemActions，不静默放过高危医嘱。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- TDD 红灯：`RealtimeCdsHookServiceTest`、`RealtimeCdsHookControllerSecurityTest`、`SystemConfigServiceTest` 先因缺少实时 CDS 服务 / 配置接口 / 配置键编译失败。
- 聚焦绿：`mvn -q -Dtest=RealtimeCdsHookServiceTest,RealtimeCdsHookControllerSecurityTest,SystemConfigServiceTest test`。
- 主链路回归：`mvn -q -Dtest=RealtimeCdsHookServiceTest,RealtimeCdsHookControllerSecurityTest,CdsHookContractTest,RecommendationEngineControllerSecurityTest,ClinicalEventEngineDispatcherTest,ClinicalEventEngineAdapterTest,SystemConfigServiceTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`。
- 后端全量：`mvn -q test`；Surefire 报告 `299` 个 txt，`rg -n "Failures: [1-9]|Errors: [1-9]" target/surefire-reports/*.txt` 无命中。
- 前端全量：`npm run verify`；Vitest `81` 个测试文件、`578` 个测试通过。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict`。
- T-GATE：`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`、`scripts/check-comment-zh.sh`、`git diff --check`。

## 下一步

1. 提交并推送 `codex/realtime-cds-order-sign`，创建 PR，等待 CI 绿后 squash 合入 `main`。
2. 基于最新 `main` 继续 P11-1 / P11-2 标准互操作映射，不恢复并行线。
