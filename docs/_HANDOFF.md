# 会话接力

## 唯一执行组织

- 当前分支：`codex/authoring-batch-jobs`
- 基线：`origin/main` = `b205c893`（P12-6 统一资产库已合入）
- 执行原则：线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不做旧方案兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 完成 P12-7：批量导入/导出、模板参数表生成、批量发布、批量分发与 `batch_job` 进度审计。
- 新增唯一批量任务模型：`mk_engine_authoring_batch_job`、`mk_engine_authoring_batch_item`，覆盖 H2/PostgreSQL/Kingbase/Oracle/DM 五方言迁移。
- 后端新增 `/api/v1/engine/authoring/batch` 统一接口，复用现有规则引擎、包引擎、离线导入导出、发布影响分析与同步目标，不另起并行链路。
- 批量发布按规则逐条影响分析；高危/危急规则必须逐条确认，未确认时拒绝创建发布任务。
- 批量分发复用 `SyncTarget` 与发布计划；连接不可达时返回 `NOT_CONNECTED`，不伪造成功。
- 前端在「统一资产库」内新增「批量处理」抽屉，覆盖规则生成、规则发布、包导入/导出、包分发、任务记录；默认走同行评审与灰度分发，任务状态/风险/类型用中文呈现。
- 登录链路同步修复：登录前匿名端点忽略失效访问令牌；改密/MFA 流程可在存在旧路径 XSRF cookie 时接受当前匹配 token。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- 红灯：批量处理前端测试曾失败于 raw enum 显示、批量发布默认直达全量、包分发默认全量；已改为中文状态/风险、默认 `PEER_REVIEW`、默认 `GRAYSCALE`。
- 后端全量已通过：`mvn test`（1965 tests / 0 failures / 0 errors / 3 skipped；3 skipped 为本机无 Docker 的 Testcontainers 多方言 smoke）。
- 前端全量已通过：`npm run verify`（81 files / 575 tests / lint / stylelint / lint-rules / format / typecheck 全绿）。
- 前端构建已通过：`npm run build`（Vite 3403 modules transformed）。
- T-GATE 已通过：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`（38 tests）；`node scripts/authenticity-guard.mjs --mode=all`（1439 files）；`node scripts/config-boundary-guard.mjs --mode=all`（1354 files）；`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（5 files）；`bash scripts/check-comment-zh.sh`；`git diff --check`。
- OpenSpec 已通过：`openspec validate pathway-rule-authoring-overhaul --strict`。
- 浏览器冒烟已通过：登录页首次改密 + MFA 绑定后进入 `/authoring/assets`；可见「统一资产库」与「批量处理」；抽屉全部页签可打开；控制台无业务错误。临时服务已停。

## 下一步

1. 提交并推送 `codex/authoring-batch-jobs`，创建 PR，等待 CI 绿后 squash 合入 `main`。
2. 合入 `main` 后继续 P13-1，不恢复并行线。
