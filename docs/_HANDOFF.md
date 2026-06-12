# 会话接力

## 2026-06-12 P2 全系统产品信息架构门禁

- 当前分支：`codex/global-product-ia-refactor`。P2 全系统产品门禁已通过，待提交。
- 第一阶段体系检查点已完成，权威报告：[第一阶段代码检查点](audit/knowledge-personnel-governance-checkpoint-20260612.md)。
- 本阶段产品门禁已完成，权威报告：[全系统产品信息架构门禁验收报告](audit/global-product-ia-acceptance.md)。
- 下一线程入口：[P2 精简上下文快照](audit/context-snapshots/2026-06-12-p2-global-product-ia.md)。除非核查历史证据，不要回读旧长流水。

## 已完成

- 全量盘点前端路由、页面、后端菜单、控制器、批量任务、第三方接口和专家能力，并生成唯一产品裁决：[全系统功能目录](audit/product-function-catalog.md)。
- 锁定五大客户主域：工作台、机构治理、知识配置、临床协同、质量与运营；高级工具默认隐藏在所属主域专家模式；外部系统接口能力不进客户菜单。
- 前后端菜单、路由、权限、面包屑、页面名称、客户可见术语和 14 个职责角色默认工作台已同源重构。
- 14 个客户职责角色菜单快照、唯一主动作、高频任务和桌面/平板/移动旅程已锁定：[角色旅程报告](audit/product-role-journeys.md)。

## 验证证据

- 后端：`mvn -q test`，Surefire 340 份报告，2213 tests，0 failures，0 errors，5 skipped。
- 五方言：`MigrationBaselineContractTest` 102 tests，0 failures，0 errors，覆盖 h2/postgres/oracle/dm/kingbase 静态迁移合同。
- 前端：`npm run verify` 通过，lint、stylelint、规则测试、format、typecheck、Vitest 89 files / 648 tests 全绿。
- 构建：`npm run build` 通过，Vite production build 3408 modules transformed。
- E2E：`E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 E2E_BASE_URL=http://127.0.0.1:5173 MEDKERNEL_API_PROXY_TARGET=http://localhost:18080 npx playwright test --project=chromium`，31 passed。
- T-GATE：38 项门禁自测通过；`authenticity-guard --mode=all` 扫描 1582 文件 0 阻断；`config-boundary-guard --mode=all` 扫描 1492 文件 0 阻断；`migration-convention-guard --mode=all` 扫描 575 文件 0 阻断；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check` 通过。
- 产品目录门禁：`node scripts/audit/export-product-capabilities.mjs --check` 退出码 0。
- 本地浏览器：Browser 打开 `http://127.0.0.1:5173/login`，可见中文机构登录、平台治理切换、所在机构、统一身份入口和安全提示。

## 未冒领与延期

- `DEFER-023`：本机 Docker socket 不可用，Testcontainers PostgreSQL/Oracle 迁移烟测跳过。当前只声明五方言静态合同和 H2 当前运行链路通过；P3/P4 必须提交目标环境真实备份、恢复和从零迁移证据。
- 尚未对 `193.112.107.134` 执行任何外向操作；不得跳过 P3 直接清库或发布。
- wave2 模型网关、AI 知识工厂、平台首发知识生成、15 领域门面和 GA 总验收未开始。
- `193.112.107.134` 是后续主平台知识管理服务器；所有平台知识生成必须落在 134，且必须等全功能完美验收、结构冻结、清库双演练和第一阶段正式验收通过后开始，禁止边生成知识边改主结构。

## 下一步

1. 提交 P2 成果。
2. 切换干净线程并归档旧线程；新线程只读本文件和 P2 精简快照。
3. 进入 P3 演练前发布准备：先核验 `193.112.107.134` 主机、部署目录、数据库、当前版本和回退路径。
4. 对 134 做任何外向操作前，必须先生成数据库、配置、制品和关键证据备份，记录摘要、时间、操作者和恢复命令。
5. P3 未通过前不得清库、发布、首轮演练或进入 wave2。
6. P6 开始前必须证明 134 平台知识结构冻结；平台首发知识资产只能在 134 上生成和发布。
