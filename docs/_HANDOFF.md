# 会话接力

> 开工先读本文件。这里只保留当前可执行事实；产品范围见
> [PRODUCT_SCOPE](PRODUCT_SCOPE.md)，历史过程使用 Git 追溯，不在当前工作树保留第二套事实源。

## 当前主线

- 最新远端主线：`origin/main` 与本地 `main` 均为
  `1561ba6bef8777dcef76432696f43de4277fdd3f`
  （`完善全角色上线演练与134复演闭环 (#653)`）。
- 当前本地工作分支：`codex/final-handoff-product-optimization`，从 `1561ba6b` 创建；
  本阶段只做本地提交，不推送远程，不直接改写远端 `main`。
- 当前已发布应用代码提交为 `c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
  （`fix: 办理随访时收起背景列表`）；已包含第七批 `mimoModel` 发布脚本能力、第八批质量报告体验优化、第九批协同任务摘要优化、第十批随访患者过滤器脱敏体验与第十一批随访办理焦点收敛，
  后续文档交接提交不改变 134 应用包。
- 当前 134 已发布应用为 `c9ac9ea2226952aa6133e72925a5fc11bda8bff4`；第十一批随访办理背景列表收敛已发布并复演。
- 134 当前完整部署 `c9ac9ea2226952aa6133e72925a5fc11bda8bff4`；远端备份
  `/zoesoft/medkernel/backups/deploy-20260701-151012`，manifest
  `deployedAt=2026-07-01T15:10:14+08:00`，
  `jarSha256=19633f0c98db574fee758a24a7bab7bce1365e74bff03e65cf4415cc5ca10dac`，
  readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2050289`、`NRestarts=0`。
- 后续只有应用代码再次变更时才需要按新提交版本重发 134；当前第十一批版本已完成真实前台与全职责 E2E。
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

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第五批·质量整改截止时间）

- 基于第四批 134 复演继续按质控、医保审核、院长质量下钻和实施验收视角核查，发现
  `a1a55176d45518d01a132593f8e35a303e5c79e1` 虽已把整改截止时间从裸 ISO 文本改为日期输入，
  但浏览器原生 `datetime-local` 在 134 可见渲染为 `07/15/2026, 08:30 AM`，会造成院内中文时间口径
  与前台真实操作不一致。坏例截图已留在
  `/tmp/medkernel-e2e-codex3/evidence-quality-dueat-field-a1a55176/insurance-dueat-field.png`，
  该提交不得作为最终交付证据引用。
- 已本地修复并提交 `ce36f55c3e4b4e88e0f83ee23472ab3d9132f6ba`
  （`fix: 使用中文院内时间填写整改截止`）：
  - `RectificationDueAtField` 改为受控中文院内时间输入，提示为“例如 2026年07月15日 08:30”，
    统一校验“yyyy年MM月dd日 HH:mm”格式，避免浏览器按地区显示斜杠日期或 AM/PM。
  - `dateTimeText` 将整改截止时间输入格式统一为中文临床时间，提交时换算为 UTC ISO；
    保留旧 `YYYY-MM-DDTHH:mm` 解析兜底，避免已有表单状态或自动填充破坏后端契约。
  - 质控预警、评价结果派发整改和医保审核派整改三条流程均继续向后端提交同一 UTC ISO，
    前台不再展示技术 ISO 或浏览器地区化时间。
- 红绿验证：
  - 红灯：`npm --prefix frontend test -- dateTimeText.test.ts QcAlerts.test.tsx QcEvalResults.test.tsx InsuranceAudit.test.tsx`
    在旧实现下失败，暴露 `formatClinicalDateTimeInputValue` 仍输出 `2026-06-08T08:00`，
    `clinicalDateTimeInputToIso` 对中文院内时间原样透传。
  - 绿灯：`npm --prefix frontend test -- dateTimeText.test.ts QcAlerts.test.tsx QcEvalResults.test.tsx InsuranceAudit.test.tsx QcDashboard.test.tsx`
    通过，`5` 个文件 / `21` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `907` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source ce36f55c3e4b4e88e0f83ee23472ab3d9132f6ba`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-123246`，
    manifest `deployedAt=2026-07-01T12:32:48+08:00`，
    `jarSha256=4f4a8098720bab50a2e7319731b63c5869eee7ae017f9d8541c2c6747b5e060d`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1965458`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-ce36f55c-quality-dueat-cn`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    运行记录 `12` 个职责视角均有真实动作，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-ce36f55c-quality-dueat-cn`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (37.5s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，浏览器错误、服务端错误、网络失败均为 `0`。
  - 针对问题本身的人工式浏览器核查：
    `/tmp/medkernel-e2e-codex3/evidence-quality-dueat-field-ce36f55c/insurance-dueat-field-cn.png`
    显示医保审核页“整改截止时间”为 `2026年07月15日 08:30`，输入 `type=text`，
    placeholder 为 `例如 2026年07月15日 08:30`，旧斜杠日期 / ISO 可见值为空，字段周边未发现遮挡。

## 最新阶段交接（2026-07-01 全视角真实前台体验优化第六批·模型安全边界）

- 基于用户补充的院外/院内大模型双模式要求，以及第五批 134 复演后的模型能力页截图继续核查，发现原页面把
  无模型规则链路、院内本地模型和公网外部模型都归入“外调安全”，会误导医疗引擎运营员、信息科长、
  实施工程师和审计员：无模型状态应是预设边界，院内本地模型应体现授权边界，公网模型应体现严格脱敏边界。
  后端 `ModelEgressGuard` 当前仍会对直接核心标识做强处理，本批前台不宣称院内模型可原文外泄核心敏感信息。
- 已本地修复并提交：
  - `609842c7f4947c3b3cc1519a3beaa30fdf55fb77`（`fix: 区分模型安全边界配置语义`）：
    模型能力页改为按运行方式展示“安全边界已预设 / 院内授权已配置 / 公网安全已配置”，对应操作为
    “预设安全边界 / 配置或调整院内授权 / 配置或调整公网安全”，弹窗标题与保存按钮同步区分。
  - `fe59c5732ce3007be79646c6e25b0e3d80f62cd6`（`test: 适配模型能力真实安全边界`）：
    单测与 D6 验收改为断言真实安全边界语义，不再寻找旧“外调安全”入口。
  - `767fa18f50fc02408f7ecc58a9fb57caa20e50a7`（`test: 更新真实演练模型安全边界流程`）：
    真实前台与全角色演练脚本改用“模型安全边界”流程，并修复 Ant Select 隐藏选项命中导致的真实浏览器不稳定。
- 体验口径：
  - 公网模型：可在授权用途内使用患者上下文，但姓名、证件号、手机号、地址、患者编号等核心标识字段先遮蔽。
  - 院内本地模型：按授权使用必要患者信息，日志与证据不保留患者明文，并保留敏感信息处理边界。
  - 无模型规则链路：只预设未来切换模型前的安全字段与责任确认，不改变当前 B0 规则链路。
  - 旧“外调结果 / 外调允许字段 / 外调安全”前台文案已替换为“模型使用结果 / 模型允许字段 / 模型安全边界”。
- 本地验证证据：
  - 红灯：旧实现下 `npm --prefix frontend test -- AiWorkflows.test.tsx` 失败于旧“配置外调安全”入口与旧弹窗标题。
  - 绿灯：`npm --prefix frontend test -- AiWorkflows.test.tsx` 通过，`10` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `908` 项；
    `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 767fa18f50fc02408f7ecc58a9fb57caa20e50a7`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-130501`，
    manifest `deployedAt=2026-07-01T13:05:03+08:00`，
    `jarSha256=3e5d581263aed00fda2a01d9ea46edf325b7e2981e82da6f7cd750dd314dfe81`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=1983099`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-d6-ai-workflows-767fa18f-safety-boundary`
    直接访问 134 运行 `d6-ai-workflows.spec.ts --project=chromium` 通过，`2 passed (17.5s)`；
    report stats 为 `expected=2`、`unexpected=0`、`flaky=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-767fa18f-model-boundary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    运行记录 `12` 类视角（医生、护士、药师、医技、质控、患者代理、平台管理员、医疗引擎运营员、
    审计员、信息科长、实施工程师、院长）均有 `actions=1`，浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-767fa18f-model-boundary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (35.0s)`；
    运行记录 `10` 段真实前台链路均由页面提交产生，包含“前台配置模型安全边界策略”，全部
    `browserErrors/serverErrors/networkFailures=0`。
  - 截图复核：`ai-workflows-desktop.png` 与 `ai-workflows-mobile.png` 显示“患者上下文模型使用边界”
    说明，移动端纵向可读；`real-frontdesk-model-safety-boundary.png` 显示保存反馈
    “模型安全边界已保存”，列表列名为“模型安全边界”，未再出现旧“外调安全”误导文案。

## 最新阶段交接（2026-07-01 第七批·134 mimoModel 运行配置接入）

- 基于用户补充“院外支持的大模型信息维护在 134 服务器 `/zoesoft/mimoModel`”继续主线核查。
  该文件是受控运行配置，不提交仓库；本批只记录脱敏形态与运行结果，不输出或保存真实密钥。
- 已本地修复并提交 `07ff36c3de53433f43ea3bad281e1ff5827667b3`
  （`fix: 接入mimo模型运行配置`）：
  - `scripts/release/model-provider-launch-lib.mjs` 支持 `LAUNCH_MODEL_PROFILE_FILE` 从仓库外读取
    `mimoModel` 风格配置，兼容三行裸值、`key=value`、`key: value`、键值与裸凭据混合格式。
  - Provider 类型从只允许 `OLLAMA` 扩展到 `OLLAMA / OPENAI_COMPATIBLE / CLAUDE / DIFY`；
    公网 Provider 默认要求 HTTPS，凭据走 `/model-providers/{code}/credential` 受管保存。
  - OpenAI 兼容端点归一化为后端适配器需要的根地址：配置文件若给出 `/v1/chat/completions`、
    `/v1/models` 或 `/v1`，发布脚本保存为后端拼接 `/v1/...` 前的根路径，避免重复 `/v1`。
  - 公网模型能力策略使用 `EXTERNAL_MODEL` + `MASK_ALL` + `EXTERNAL_MASKED` 证据描述；
    院内/本地模型保持 `LOCAL_MODEL` + `LOCAL_ONLY` 路线，不把凭据写入上线证据。
- 真实 134 配置解析证据（脱敏）：
  - `/zoesoft/mimoModel` 已复制到本机临时受控路径并设为 `0600`；文件内容未打印。
  - 解析结果为 `providerCode=mimo-public`、`providerType=OPENAI_COMPATIBLE`、
    `endpointProtocol=https:`、`endpointPath=/`、`credentialPresent=true`；
    模型版本只确认已解析，不记录真实值。
- 真实 134 Provider 上线结果：
  - 使用 `node --use-system-ca scripts/release/model-provider-launch.mjs` 访问
    `https://193.112.107.134/medkernel/api/v1`，脚本按预期先登记 Provider 并保存受管凭据，
    但后端健康检查后停止，错误为“Provider 状态必须为 enabled=false, status=HEALTHY”。
  - 134 关系库脱敏视图：`mimo-public` 当前 `enabled=false`、`status=NOT_CONNECTED`、
    `credentialConfigured=true`、`endpointPath=/`；Provider 未启用，未进入医学回归、策略发布或版本组合发布。
  - 根因复核：修正前后端日志显示旧路径为 HTTP 404；本批端点修正后上游返回 HTTP 401。
    使用同一解析器直接探测上游 `/v1/models` 与 `/v1/chat/completions` 均为 HTTP 401，
    响应体错误为 `Invalid API Key`。因此当前阻断是 134 文件中的外部模型凭据无效或已过期，
    不是发布脚本端点解析、后端 Provider 保存或前台模型安全边界问题。
  - 已登记外部环境待处理项 `DEFER-003`；拿到有效凭据前不得强行启用公网 Provider，也不得伪造模型上线通过。
- 验证证据：
  - 红灯：`node --test scripts/release/model-provider-launch.test.mjs` 在旧端点归一化下失败，
    三种 `mimoModel` 输入均错误保留 `/v1`。
  - 绿灯：`node --test scripts/release/model-provider-launch.test.mjs` 通过，`9` 项。
  - 回归组合：`node --test scripts/release/model-provider-launch.test.mjs scripts/release/full-system-rehearsal.test.mjs scripts/release/runtime-resilience-rehearsal.test.mjs`
    通过，`19` 项。
  - `git diff --check` 通过；敏感扫描只命中 `example.com` 测试假数据和环境变量名。
- 第七批没有变更后端/前端应用包，因此当时未重新部署 134；随后第八批应用变更已重新发布，
  当前 134 版本以本文“当前主线”和第八批交接为准。

## 最新阶段交接（2026-07-01 第八批·质量报告处理摘要与复演）

- 基于第七批后的“质量报告长明细阅读负担”和全角色体验继续核查，发现两类上线级体验缺口：
  - 质量管理概览在部分指标不可计算时只给低层指标说明，质控、院长和信息科长难以快速判断当前筛选页应该先处理什么。
  - 系统接入的数据质量报告生成后只有指标和缺口明细，实施工程师和信息科长需要自行推断是否暂缓上线、先处理断连还是字段映射。
- 已本地修复并提交：
  - `190dcddb3b46796ac6d959a624bb61fea85a77c1`（`fix: 强化质量报告处理摘要`）：
    `QcDashboard` 增加“当前页处理摘要”，按当前页严重程度、状态和科室筛选给出处理顺序；
    `AdapterHub` 在数据质量报告中增加“上线判断：暂缓上线 / 可继续接入验收”和先后处理顺序。
  - `662a19b373e1fb6025cb4d84e482db3892e301b4`（`fix: 避免质量报告摘要标签冲突`）：
    修正 `190dcddb` 引入的行动摘要文案冲突；报告详情字段继续叫“缺口摘要”，上方行动判断改为“报告缺口”，避免用户和严格 E2E 都被同一卡片内的重复标签误导。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- QcDashboard.test.tsx AdapterHub.test.tsx`
    在旧实现下失败于找不到“当前页处理摘要”和“上线判断：暂缓上线”。
  - 绿灯：上述目标测试通过，`2` 个文件 / `24` 项。
  - 标签冲突红灯：`npm --prefix frontend test -- AdapterHub.test.tsx -t "默认展示业务接入摘要"`
    在旧文案下失败于找不到“报告缺口：HIS 断连，LIS 配置非法”。
  - 标签冲突绿灯：同一目标用例通过；`npm --prefix frontend test -- AdapterHub.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `908` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 曾用 `190dcddb3b46796ac6d959a624bb61fea85a77c1` 发布到 134；
    备份 `/zoesoft/medkernel/backups/deploy-20260701-134031`，
    manifest `deployedAt=2026-07-01T13:40:33+08:00`，
    `jarSha256=33d1e466e5eedd08a68c33c2d85529244068b1a97db4cd055f794be2db73ccd0`，
    readiness HTTP 200 / `{"status":"UP"}`。该版本在全职责 E2E 暴露“缺口摘要”文案重复导致的严格定位歧义，
    只作为根因证据，不作为最终交付证据。
  - 已用 `deploy/onprem/mk-publish.sh --source 662a19b373e1fb6025cb4d84e482db3892e301b4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-134901`，
    manifest `deployedAt=2026-07-01T13:49:03+08:00`，
    `jarSha256=522522403195c6e8b94a7a5c75cc1b2536d69b98a24cfbbd10e77733561f8b37`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2006750`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-662a19b3-quality-summary-fixed`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.4m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有 `actions=1`，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-662a19b3-quality-summary-fixed`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均为页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-it_manager.png` 显示数据质量报告行动区使用“报告缺口”，详情字段仍保留“缺口摘要”；
    `stakeholder-quality_controller.png` 与 `stakeholder-hospital_executive.png` 显示质量概览处理摘要可见且未遮挡；
    `real-frontdesk-onboarding.png` 显示前台新增接入申请与最近更新时间；
    `real-frontdesk-value-set.png` 显示前台新增值集按最新维护时间排序；
    `real-frontdesk-model-safety-boundary.png` 显示模型安全边界仍按患者上下文脱敏 / 院内授权口径呈现。

## 最新阶段交接（2026-07-01 第九批·协同任务技术摘要收敛）

- 基于第八批 134 全职责截图继续按医技、医生、护士、患者代理、药师等真实角色体验核查，发现
  `stakeholder-medical_technician.png` 的协同任务默认列表直接展示
  `runtime-*` 运行版本与 `result-review` 触发点。该信息对医技/医生处理报告待办没有决策价值，
  会把低频追溯对象暴露到主任务摘要，违反“技术对象收进高级信息 / 证据详情”的体验契约。
- 已本地修复并提交 `0ecfe3eca3412e7470314c1ed6a96d176a2ad6e0`
  （`fix: 收起协同任务技术摘要`）：
  - `WorkflowTodos` 默认按来源类型给出业务摘要；当原始摘要含 `runtime-*`、触发点或 trigger 标识时，
    默认显示“报告结果需要结合患者上下文完成辅助解读，处理结论需由医技或医生确认”等业务语句。
  - 证据详情打开后仍显示原始摘要、追踪号、来源对象、患者和流转证据，保证审计与实施排障可追溯，
    不新增旧式孤立“专家模式 / 技术细节”入口。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- WorkflowTodos.test.tsx -t "keeps runtime release"`
    在旧实现下失败于默认列表找不到业务摘要。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- WorkflowTodos.test.tsx`
    通过，`19` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `909` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 0ecfe3eca3412e7470314c1ed6a96d176a2ad6e0`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-143550`，
    manifest `deployedAt=2026-07-01T14:35:52+08:00`，
    `jarSha256=34a7d07b34535be3e2488b358cb8b824b66af9cdbb6487679e3b5f9b014c71d7`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2031483`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-0ecfe3ec-workflow-summary`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-0ecfe3ec-workflow-summary`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.0s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`stakeholder-medical_technician.png` 的协同任务默认列表已显示业务报告解读摘要，
    未再裸露 `runtime-*` 或 `result-review`；`real-frontdesk-model-safety-boundary.png` 仍按公网患者上下文脱敏、
    院内授权使用必要信息的模型安全边界呈现；`real-frontdesk-followup-plan-questionnaire-abnormal.png`
    暴露出下一批待处理问题：随访页患者过滤器仍显示 `mpi-*` 原始患者标识。

## 最新阶段交接（2026-07-01 第十批·随访患者过滤器原始标识收敛）

- 基于第九批真实前台复演继续按护士、患者代理、医生和随访实施视角核查，发现
  `real-frontdesk-followup-plan-questionnaire-abnormal.png` 的“按患者线索检索”输入框在生成随访计划后显示
  `mpi-*` 原始患者标识。该标识虽然用于后端精确过滤，但不应作为默认前台可见文本暴露给普通临床操作路径。
- 已本地修复并提交 `89a641fabd5b5e48c326b2183cde8dfd43232dfd`
  （`fix: 隐藏随访患者过滤原始标识`）：
  - `Followup` 将患者过滤器拆为真实查询值与可见业务文本；生成随访计划后继续用后端返回的真实 `patientId`
    查询计划和统计，但输入框显示“已筛选刚生成计划的患者”。
  - 手工输入仍按用户输入作为患者线索查询；清空输入会同步清空真实查询值。
  - 表格和抽屉继续沿用“患者已关联 / 就诊已关联”的默认业务摘要，证据详情打开后仍可追溯计划、患者、模板和任务原始标识。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "keeps generated plan patient identifiers"`
    在旧实现下失败，输入框实际值为 `mpi-01KWE6CK9MD11VREALFILTER`，而不是业务文本。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`16` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `910` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source 89a641fabd5b5e48c326b2183cde8dfd43232dfd`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-144721`，
    manifest `deployedAt=2026-07-01T14:47:23+08:00`，
    `jarSha256=84d2c8bc2d1b461309577b41336a3e653c0cb6043a0f9cd3b9a2d944d5cadd37`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2037866`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-89a641fa-followup-filter`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 类视角均有真实动作，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-89a641fa-followup-filter`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.6s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-plan-questionnaire-abnormal.png` 中患者过滤器显示
    “已筛选刚生成计划的患者”，不再显示 `mpi-*`；随访计划列表仍正常展示患者/就诊已关联、病种、方案、
    任务进度与办理入口。

## 最新阶段交接（2026-07-01 第十一批·随访办理焦点收敛）

- 基于第十批真实前台截图继续按护士、患者代理、医生和随访实施视角体验，发现
  `real-frontdesk-followup-plan-questionnaire-abnormal.png` 中“随访计划办理”抽屉打开后，背景随访计划表仍透过遮罩清晰可读。
  护士登记异常回院、患者代理代填问卷时会被底部计划列表抢视线，也容易误解为当前表单的一部分。
- 处理中曾本地提交并短暂发布 `3ffeaa61a11a18b2adcf66ad6307421258bbeb7c`
  （`fix: 提升随访办理抽屉层级`），但 134 截图复核显示背景计划表仍然可读；该提交只保留为根因证据，
  **不是完成态，不作为后续接力依据**。
- 最终已本地修复并提交 `c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
  （`fix: 办理随访时收起背景列表`）：
  - `Followup` 统一计算随访办理抽屉打开状态，并在抽屉打开时隐藏背景“随访计划列表”区域。
  - 背景计划列表新增明确 `aria-label="随访计划列表"`，方便无障碍边界与自动化验收；抽屉关闭后列表恢复。
  - 抽屉仍保留显式层级 `1200`，但完成态以“背景列表不可见”为验收标准，而不是仅调层级。
- 红绿与本地验证：
  - 红灯：`npm --prefix frontend test -- Followup.test.tsx -t "办理抽屉打开后收起背景计划列表"`
    在旧实现下失败，页面没有可识别的“随访计划列表”边界，也无法保证抽屉打开时背景列表不可见。
  - 绿灯：同一目标用例通过；`npm --prefix frontend test -- Followup.test.tsx`
    通过，`17` 项。
  - 完整前端门禁：`npm --prefix frontend run verify` 通过，`114` 个测试文件 / `911` 项；
    过程中仍只有既有 `antd Timeline.Item` 弃用警告。
  - `npm --prefix frontend run build` 通过；`git diff --check` 通过。
- 134 发布与复演：
  - 已用 `deploy/onprem/mk-publish.sh --source c9ac9ea2226952aa6133e72925a5fc11bda8bff4`
    完整发布到 134；备份 `/zoesoft/medkernel/backups/deploy-20260701-151012`，
    manifest `deployedAt=2026-07-01T15:10:14+08:00`，
    `jarSha256=19633f0c98db574fee758a24a7bab7bce1365e74bff03e65cf4415cc5ca10dac`，
    readiness HTTP 200 / `{"status":"UP"}`，服务 `active/enabled`、`MainPID=2050289`、`NRestarts=0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-stakeholder-full-actions-c9ac9ea2-followup-drawer-focus`
    直接访问 134 运行 `stakeholder-view-rehearsal.spec.ts --project=chromium` 通过，`1 passed (1.3m)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `12` 条职责视角记录、`4` 类登录职责覆盖，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - `E2E_EVIDENCE_DIR=/tmp/medkernel-e2e-codex3/evidence-real-frontdesk-deep-c9ac9ea2-followup-drawer-focus`
    直接访问 134 运行 `real-frontdesk-rehearsal.spec.ts --project=chromium` 通过，`1 passed (34.1s)`；
    report stats 为 `expected=1`、`unexpected=0`、`flaky=0`；运行记录 `10` 段真实前台链路均由页面提交产生，
    浏览器错误、服务端错误、网络失败均为 `0`。
  - 截图复核：`real-frontdesk-followup-plan-questionnaire-abnormal.png` 中办理抽屉打开后不再显示底部随访计划表，
    左侧背景仅保留弱化的页面上下文；患者过滤器继续显示“已筛选刚生成计划的患者”，未回退到 `mpi-*`。

## 下一步

1. 继续全视角真实操作与产品体验优化：重点回看患者/医生/护士/药师/医技剩余入口操作路径、
   宽表默认可读性、高级信息呈现方式、质量管理下钻与整改闭环，发现不合理产品设计直接按当前权威标准优化。
2. 继续复核真实前台演练截图里的质量下钻、长表格和随访办理抽屉在窄屏 / 多任务量下的滚动与按钮可达性，
   发现遮挡、重复识别困难、操作路径过长或职责边界不清时直接按现行体验契约修复。
3. `DEFER-003` 关闭前，公网 `mimo-public` 只保留“已受管登记但未启用”的诚实状态；拿到有效外部凭据后，
   重新运行 Provider 上线脚本并补充全知识生产真实模型证据。
4. 目标环境上线前仍需补跑 `DEFER-002` 中的 Docker/Testcontainers 或目标库迁移 smoke，并保留脱敏 surefire 证据。
5. 当前分支继续只做本地提交；最终全链路确认无问题后再统一处理远程 `main`。
