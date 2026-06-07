# 会话接力

## 唯一执行组织

- 当前分支：`codex/line2-rule-shadow`
- 基线：`origin/main` = `1b1e5903`
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- PR #467 已合入主线，提交 `1b1e5903`，P8-1 八阶段规则治理为当前基线。
- 当前收口 P8-2：`SHADOW` 阶段规则在真实求值流量中只写 `SHADOW_RECORDED` 执行日志，不返回临床动作、不参与主动规则抑制；规则详情页展示影子执行总数、命中、未命中、命中率、误报和误报率。
- 影子误报来自人工复核反馈：仅影子命中记录可写 `TRUE_POSITIVE / FALSE_POSITIVE`，误报必须填写原因，重复反馈拒绝并留审计。
- 五方言 V11 已加入 `rule_shadow_feedback` 与 `SHADOW_RECORDED`；H2 空库启动可跑到 V97。
- 线2 / 线3 / 线4尚未全部完成；历史 `done` 仍按真实使用链路逐项复验，不把文档状态当作可用证据。

## 当前证据

- 后端：`RuleEngineControllerSecurityTest`、P8-2 `RuleEngineServiceTest` 聚焦用例、`MigrationBaselineContractTest#v11...` 均通过；全量 `mvn -q test` 为 `1852` 项、`0` failure、`0` error、`3` 项按本机无 Docker 环境跳过。
- 前端：P8-2 聚焦 `hooks.test.ts` + `RuleDefinitions.test.tsx` 为 `90` 项通过；完整 `npm run verify` 为 `78` 文件 / `534` 项通过；`npm run build` 成功，`3397` 模块构建。
- 浏览器：后端 `18180` 健康 `UP`，`/api/v1/auth/login-tenants` 200；前端 `5174` 真实登录页 → 首次改密 → MFA → `/dashboard` → `/rule/definitions` 均可渲染，控制台 error 为 `0`。
- T-GATE 尚待本分支收尾运行；当前分支尚未提交、创建 PR 或合入远程 `main`。

## 下一步

1. 运行 changed-mode T-GATE、`git diff --check` 和中文注释门禁。
2. 提交 P8-2 PR，等待 CI 全绿并确认远程 `main` 含合并提交。
3. 从最新 `origin/main` 继续 P8-3 历史回测 / 灵敏度特异度 / 漂移监测，再顺序承接 P9+ 线2/3/4未完成项。

文档只维护本文件、backlog、域简报和对应卡片，不新增施工说明。
