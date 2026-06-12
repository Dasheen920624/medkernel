# P2 全系统产品信息架构门禁精简上下文快照

> 日期：2026-06-12  
> 用途：下一线程入口。不要回读旧会话流水；除非核查历史证据，只读本快照、`docs/_HANDOFF.md` 和本阶段验收报告。

## 当前状态

- 分支：`codex/global-product-ia-refactor`
- 阶段：P2 全系统产品门禁已通过，待提交。
- 下一阶段：P3 演练前发布准备。进入 134 前必须先备份、留痕、确认恢复路径；不得跳过。

## 已完成

- 全量功能目录和唯一产品裁决：`docs/audit/product-function-catalog.md`
- 五大主域 IA 裁决：`docs/audit/product-ia-matrix.md`
- 14 角色菜单快照与任务旅程：`docs/audit/product-role-journeys.md`
- 前后端菜单、权限、路由、页面命名和角色工作台同源重构。
- 全中文、专家模式隐藏、客户主任务入口和桌面/移动端打开性验收。

## 关键证据

- 后端 `mvn -q test`：2213 tests，0 failures，0 errors，5 skipped。
- 前端 `npm run verify`：89 files / 648 tests 全通过。
- 前端 `npm run build`：生产构建通过，3408 modules transformed。
- E2E：`npx playwright test --project=chromium`，31 passed。
- T-GATE：38 项门禁自测通过；真实性、配置边界、迁移规约 all-mode 均 0 阻断；中文注释 0 fail / 0 warn；`git diff --check` 通过。
- Browser 冒烟：`http://127.0.0.1:5173/login` 可打开，显示中文登录、平台治理切换、所在机构和统一身份入口。

## 风险与未冒领

- `DEFER-023`：本机 Docker socket 不可用，Testcontainers PostgreSQL/Oracle 烟测跳过。P3/P4 必须在目标环境或可用容器环境提交真实备份、恢复和从零迁移证据。
- 当前没有对 `193.112.107.134` 做外向操作。
- wave2 AI、知识生成、15 领域门面和 GA 验收未开始。
- `193.112.107.134` 被指定为后续主平台知识管理服务器；所有平台知识生成必须落在 134，并且只能在全功能完美验收、结构冻结、清库双演练和第一阶段正式验收通过后开始。

## 下一步

1. 提交 P2 成果。
2. 开新线程并归档旧线程后进入 P3。
3. P3 首个动作：核验 134 连接、部署目录、数据库和当前版本；生成备份计划、恢复命令和操作留痕清单。
4. P3 未通过前不得清库、发布或启动演练。
5. 进入 P6 前确认 134 的平台知识结构已经冻结，不允许边生成知识边改主结构。
