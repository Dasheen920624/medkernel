# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 最新远端主线：`origin/main` 与本地 `main` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；
  本阶段只做本地提交，不推送远程，不直接改写远端 `main`。
- 当前 134 已发布应用为 `936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
  （`fix: 统一前台临床日期时间表达`）；第四批前台日期时间体验优化已发布并复演。
- 134 当前完整部署 `936aa955b998a0912fa4c569f7bb6bc1dd3d4598`；远端备份
  `/zoesoft/medkernel/backups/deploy-20260701-120414`，manifest
  `deployedAt=2026-07-01T12:04:20+08:00`，
  `jarSha256=7c0381b3dfb2249abef7c8d6b400c3ea8addae5ed9d9dbedd4c84433cc5d7b43`，
  readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1950067`、`NRestarts=0`。
- 后续只有应用代码再次变更时才需要按新提交版本重发 134；当前第四批版本已完成真实前台与全职责 E2E。
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

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第四批·本地验证与134复演）

- 基于第三批 134 真实前台截图与全角色复演，继续按医生、护士、患者/代理、药师、医技、
  质控、信息科、实施工程师、审计员、院长等视角核查，发现日期时间表达仍有全局体验风险：
  部分页面直接使用浏览器默认 `toLocaleString()` 或本地 `Intl.DateTimeFormat`，在不同浏览器/地区可能显示为
  `6/4/2026`、`2026/6/4` 或同页混合格式；随访截止、路径准入、CDSS 决策审计、系统接入、
  模型供应商、来源血缘、审计详情、租户上线和发布治理都会受影响。
- 已新增统一前台显示 helper `frontend/src/shared/lib/dateTimeText.ts`：
  - 业务/临床默认使用 `Asia/Shanghai`、`yyyy年MM月dd日 HH:mm`。
  - 纯日期使用 `yyyy年MM月dd日`。
  - 审计与运行诊断保留秒级 `yyyy年MM月dd日 HH:mm:ss`。
- 已将日期时间显示统一接入医生/护士临床链路、患者路径与随访、CDSS、工作台、系统接入、
  配置资产、模型供应商、知识来源、图谱/血缘、系统保障、安全基线、身份绑定、运行诊断、
  租户开通、规则定义与发布治理等入口；高级信息仍通过既有证据详情机制展开，不新增“专家模式/技术细节”式孤立入口。
- 已补强前端断言：
  - `Followup.test.tsx` 覆盖随访截止日期为中文日期，且不出现 `6/8/2026`。
  - `PatientPathways.test.tsx` 覆盖路径准入与变异时间为中文临床时间，且不出现斜杠日期。
  - `CdssFatigue.test.tsx` 覆盖医生反馈审计时间为中文临床时间。
  - `AdapterHub.test.tsx` 覆盖系统接入、主数据同步和回调通道时间为中文临床时间。
  - `vitestRuntimeBudget.test.ts` 覆盖完整 `verify` 门禁使用既有 CI 测试预算运行全量 Vitest，
    保留普通 `npm test` 的本地 5 秒快速反馈。
- 验证证据：
  - 目标页面集合：`npm --prefix frontend test -- WorkbenchPanel.test.tsx ... SourceInfo.test.tsx`
    通过，`22` 个测试文件 / `209` 项。
  - 首轮 `npm --prefix frontend run verify` 在裸 `npm test` 的本地 5 秒预算下暴露
    `Followup` 与 `KnowledgeGovernance` 两个复杂交互用例全量并发超时；两个用例单独复现分别
    `1.327s`、`0.881s` 通过。根因是完整门禁未启用项目已有 CI 预算，不是功能断言失败。
  - 已将 `frontend/package.json` 的 `verify` 末尾改为 `CI=true npm test`，并用红绿测试固化：
    `npm --prefix frontend test -- vitestRuntimeBudget.test.ts` 先红后绿。
  - 重新运行 `npm --prefix frontend run verify` 通过，`113` 个测试文件 / `905` 项；过程中仍有既有
    `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
  - 反查 `rg "toLocaleString\\(|toLocaleDateString\\(|new Intl\\.DateTimeFormat"`：
    日期时间格式仅剩统一 helper；其余 `toLocaleString` 均为金额/计数数字格式。
- 本地提交 `936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
  （`fix: 统一前台临床日期时间表达`）已生成，暂不推送远程。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 936aa955b998a0912fa4c569f7bb6bc1dd3d4598`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-120414`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-936aa955-deployed-direct134`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    运行记录 `12` 个职责视角均有真实动作，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-936aa955-date-e2e`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.4s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，浏览器错误、服务端错误、网络失败均为 `0`。
  - 关键截图复核：`real-frontdesk-onboarding.png` 显示“最近更新 2026年07月01日 12:06”；
    `real-frontdesk-value-set.png` 显示“最新维护 2026年07月01日 12:06”；
    `real-frontdesk-followup-plan-questionnaire-abnormal.png` 的随访截止日期均为中文日期；
    可见入口未再出现浏览器地区化斜杠日期，表格换行可读且未发现重叠。

## 下一步

1. 继续第五批全视角真实操作与产品体验优化：医生、护士、患者/代理、药师、医技、质控、信息科长、
   实施工程师、审计员、院长等视角都要提交真实表单、触发真实状态变化；继续关注质量报告长明细阅读负担、
   患者/医生/护士/药师/医技剩余入口操作负担、模型双模式安全配置与实际前台交互。
2. 继续保留大模型双模式安全边界：公网模型可使用患者信息但必须严格屏蔽核心敏感信息；
   院内本地模型可按授权使用患者信息，但仍要记录用途、边界和敏感信息处理。
3. 目标环境上线前仍需补跑 `DEFER-002` 中的 Docker/Testcontainers 或目标库迁移 smoke，并保留脱敏 surefire 证据。
4. 当前分支继续只做本地提交；最终全链路确认无问题后再统一处理远程 `main`。
