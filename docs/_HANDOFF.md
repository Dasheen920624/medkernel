# 会话接力

## 当前执行

- 分支：`codex/lifecycle-state-56`；基线：`origin/main` = `29a1959c`（PR #506 / 5.5 已合入）。
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发或维护重复实现。
- 项目未上线：保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 5.6 状态

- 已统一资产生命周期为 `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → DEPRECATED → RETIRED`，仅 `PUBLISHED` 参与新请求解析。
- 规则、路径、知识包、术语、评估指标统一通过 `VersionReleaseService` 发布；高风险发布强制电子签名，平台发布强制六项质量门和签名，证据随发布计划持久化。
- 已补 V108 五方言迁移、规则/路径状态展示与 API 发布证据契约；移除旧激活旁路和旧状态引用。
- 本地证据：后端 `mvn -q test`、前端 `npm run verify`（81 文件 / 581 用例）、OpenSpec strict、真实性/配置边界/迁移/中文注释门禁及 `git diff --check` 均通过。
- 待补：提交、远端 CI、合并。

## 下一步

1. 完成 5.6 PR、CI 与合并。
2. 从最新 `main` 继续 5.7：循证溯源、复审周期、弃用后继与资产身份治理。
