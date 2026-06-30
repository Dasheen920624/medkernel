# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 最新远端主线：`origin/main` 与本地 `main` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；
  本阶段只做本地提交，不推送远程，不直接改写远端 `main`。
- 当前阶段产品代码提交为 `57eec00c742bc3b8a7981521e09b1ddc93b3b40a`
  （`fix: 优化演练数据列表可辨识性`）。该提交已随交接提交
  `5cc9faddee2bebbf626908c9d33e08b14f3eb8b3` 完整发布到 134。
- 134 当前完整部署 `5cc9faddee2bebbf626908c9d33e08b14f3eb8b3`；远端备份
  `/zoesoft/medkernel/backups/deploy-20260630-233935`，manifest
  `deployedAt=2026-06-30T23:39:37+08:00`，
  `jarSha256=81ad71ad224a088a460ed893b84981f3cc8c7b85ebcf4e522f405592d6cf3546`，
  readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1568905`、`NRestarts=0`。
- 本阶段后续本地提交只补强接力与 E2E 演练脚本，不改变 134 已部署应用包；除非后续代码再改应用行为，否则无需为此重发 134。
- 当前上线 E2E 职责账号契约：`E2E_ROLE_CREDENTIALS_FILE` 必须指向 READY 状态
  `schemaVersion=1.0.0` 文件；平台治理与平台知识生产显式读取 canonical `platform.accounts`，
  真实前台、客户职责旅程与机构业务链路默认读取 canonical `rehearsal.accounts`。
  `rehearsal` 是当前 1.0 契约中的完整上线演练机构块，不是旧兼容入口；
  `roleAccounts`、`platformRoleAccounts`、`customerTenant` 等旧 root 账号字段不得作为登录权威。
- 当前用户约束：全程按最优决策执行，不中途咨询；每阶段更新接力并提交到本地分支；
  最终统一确认前不推送远程 `main`。
- `.codex/config.toml` 为未跟踪本地配置，不提交。

## 最新阶段交接（2026-06-30 全视角真实前台体验优化第三批）

- 基于 134 真实前台截图继续体验，发现多轮演练后两类重复数据难辨认：
  - 值集维护页出现大量同名“值集资产已登记”草稿，缺少维护时间与分页，平台运营、实施、信息科长无法快速辨认最新前台提交。
  - 系统接入页“接入向导”已有服务端分页，但同类接入申请只显示“接入申请已登记 / 字段映射已配置 / 未接通”，缺少最近更新时间和总量说明。
- 已本地修复并提交 `57eec00c742bc3b8a7981521e09b1ddc93b3b40a`
  （`fix: 优化演练数据列表可辨识性`）：
  - 配置资产维护表按最近维护时间倒序展示，补“维护时间”列，显示“最新维护 yyyy年MM月dd日 HH:mm”，并开启每页 10 条分页与总量说明。
  - 接入向导表补“最近更新”列，显示“最近更新 yyyy年MM月dd日 HH:mm”，并在分页上显示“共 N 条接入申请，当前显示 x-y 条”。
  - 未新增后端契约，不删除真实演练数据；用已有 `updatedAt` 让重复前台数据可区分，保留证据详情开关原有行为。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- DeclarativeAssetWorkbench.test.tsx -t "keeps repeated"`
    失败于首行找不到“最新维护 2026年06月30日 23:18”；
    `npm --prefix frontend test -- AdapterHub.test.tsx -t "keeps repeated onboarding"`
    失败于首行找不到“最近更新 2026年06月30日 23:18”。
  - 绿灯：上述两个目标用例均通过；
    `npm --prefix frontend test -- DeclarativeAssetWorkbench.test.tsx AdapterHub.test.tsx`
    通过，`2` 个文件 / `25` 项。
  - 完整前端验证：`npm --prefix frontend run verify` 通过，`113` 个测试文件 / `904` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 5cc9faddee2bebbf626908c9d33e08b14f3eb8b3`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260630-233935`，
    readiness HTTP 200 / `{"status":"UP"}`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-5cc9fadd-deployed-direct134`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    `12` 个职责动作均 `actions=1`，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-5cc9fadd-onboarding-e2e-fixed4`
    直接访问 134 运行补强后的 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.9s)`；
    运行记录从 `9` 段扩展为 `10` 段，新增“前台创建系统接入申请”，全部
    `browserErrors/serverErrors/networkFailures=0`。
  - 截图复核：`real-frontdesk-value-set.png` 显示“维护时间”列、按最新维护时间降序，以及
    “共 30 条配置资产，当前显示 1-10 条”；`real-frontdesk-onboarding.png` 显示“最近更新”列、
    “共 1 条接入申请，当前显示 1-1 条”，接入申请确由前台弹窗提交产生。
- 本阶段继续补强 E2E 演练脚本：`frontend/e2e/real-frontdesk-rehearsal.spec.ts`
  在平台接入后新增前台“接入申请”创建步骤，复用真实组织目录与前台 Select 搜索。
  调试中确认 134 组织名称不同于单测示例、Ant Select 下拉 role 不稳定，已按真实 DOM 改为可见文本选择。
  该补强只影响本地测试/演练证据，不改变 134 已部署应用行为。
- E2E 补强验证：补强后的 `real-frontdesk-rehearsal.spec.ts --project=chromium` 直接访问 134 通过；
  补强后再次运行 `npm --prefix frontend run verify` 通过，`113` 个测试文件 / `904` 项；`git diff --check` 通过。

## 下一步

1. 提交本阶段接力与 E2E 演练补强到当前本地分支；暂不推送远程 `main`。
2. 继续第四批全视角真实操作与产品体验优化：医生、护士、患者/代理、药师、医技、质控、信息科长、
   实施工程师、审计员、院长等视角都要提交真实表单、触发真实状态变化；优先看随访日期中文化、
   质量报告长明细阅读负担、患者/医生/护士/药师/医技剩余入口操作负担。
3. 继续保留大模型双模式安全边界：公网模型可使用患者信息但必须严格屏蔽核心敏感信息；
   院内本地模型可按授权使用患者信息，但仍要记录用途、边界和敏感信息处理。
4. 目标环境上线前仍需补跑 `DEFER-002` 中的 Docker/Testcontainers 或目标库迁移 smoke，并保留脱敏 surefire 证据。
