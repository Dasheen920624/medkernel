# 会话接力

## 唯一执行组织

- 当前分支：`codex/authoring-preview-run`
- 基线：`origin/main` = `40847d01`（P12-2 `feat: 增加创作原型向导` 已合入）
- 线1统一承接全部任务；线2 / 线3 / 线4不再独立开发、抢合并或维护重复实现。
- 项目未上线：只保留唯一模型、唯一接口、唯一主流程，不新增兼容层。

## 当前状态

- OpenSpec `pathway-rule-authoring-overhaul` 已完成 P12-3：规则与路径创建流程统一接入 `POST /api/v1/engine/authoring/preview-run`。
- 规则创建弹窗新增「即配即试」页签：只允许选择 ACTIVE 真实上下文快照，草稿 DSL 在后端执行并返回命中、严重度、证据链、快照质量与资源计数。
- 路径创建弹窗新增「即配即试」页签：草稿起点、候选下一节点和边守卫在真实快照上运行，返回选中边、节点轨迹、终态和条件证据。
- 危急值原型的数组字段统一生成 `expr.select=latest`，避免裸数组比较导致人工配置不可用。
- 只同步本文件与 OpenSpec 任务清单，不新增施工文档。

## 当前证据

- 红灯：后端曾失败于缺少 preview-run 请求/响应/服务/控制器；前端曾失败于创建弹窗缺少即配即试页签与 API hook 契约。
- 后端聚焦：`mvn -Dtest=AuthoringPreviewRunServiceTest,AuthoringPreviewControllerTest test` 通过，7 tests。
- 后端全量：`mvn test` 通过，1929 tests，0 failures，0 errors，3 个 Docker 依赖 smoke 按本机环境跳过。
- 前端聚焦：`npm test -- --run src/pages/tenant/RuleDefinitions.test.tsx src/pages/tenant/PathwayTemplates.test.tsx` 通过，41 tests；`npm test -- --run src/shared/api/hooks.test.ts` 通过，81 tests。
- 前端全量：`npm test` 通过，79 files / 557 tests。
- 前端静态与构建：`npm run typecheck`、`npm run lint`、`npm run stylelint`、`npm run format:check`、`npm run build` 通过。
- OpenSpec：`openspec validate pathway-rule-authoring-overhaul --strict` 通过；任务进度 57/68。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 通过，38 tests；`authenticity-guard --mode=all` 扫描 1380 个文件通过；`config-boundary-guard --mode=all` 扫描 1299 个文件通过；`migration-convention-guard --mode=changed --base=origin/main` 无迁移文件、通过。
- 注释与空白：`bash scripts/check-comment-zh.sh`、`git diff --check` 通过。

## 下一步

1. 提交、推送 `codex/authoring-preview-run`，创建 PR。
2. 远端 CI 通过后合入 `main`。
3. 从最新 `origin/main` 继续 P12-4 参数化规则，不恢复并行线。
