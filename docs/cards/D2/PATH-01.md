# PATH-01 · 路径引擎（后端 + 三层前端）

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md) + [体验契约](../../EXPERIENCE_CONTRACT.md)（含节点画布前端）。
> 迁移来源（覆盖矩阵锚点）：详规 §5 路径引擎详细规范（L1128 / 5.1 对象 L1130 / 5.2 节点 L1142 / 5.3 易用配置 L1156 / 5.4 运行状态 L1168 / 5.5 专科路径 L1181 / 5.6 中医路径 L1198 / 5.7 任务 L1210）· 落地规划 §8.3 路径引擎（L468）· 核心 §4 7 步流 / §3 状态机。

## 身份

- 卡 ID：PATH-01（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S6 路径引擎配置
- 依赖卡：[SYS-04](SYS-04.md)（路径版本与发布）· [API-01](API-01.md)（标准上下文）· [API-06](API-06.md)（路径 API）· [RULE-01](RULE-01.md)（节点触发规则）· [BASE-06](../D0/BASE-06.md)/[BASE-08](../D0/BASE-08.md)/[BASE-10](../D0/BASE-10.md)（前端底座）· [INFRA-09](../D1/INFRA-09.md)（StepFlow）
- 工作量：16d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

提供路径引擎 **B0 真实**：**三层配置**（L1 模板 / L2 React Flow 节点画布 / L3 DSL）+ **多级模板继承差异合并** + **7 步流**发布 + **单快照/队列/时光机仿真** + **关键时钟**（绑定质控时限）+ **RACI 工作清单** + **变异管理** + **结局指标绑定** + **多路径协调提示** + **随访接续**。让专科专家/科主任配置专病路径，患者入径后节点推进/变异/超时/结局闭环由引擎驱动，供 D3 临床运行消费。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）

`engine/pathway` **后端已实质建成**，本卡＝**契约化 + 三层前端 + 仿真/随访接续补全**：

- 已有（后端）：`PathwayTemplate`(+`Level`/`Status`/`Detail`)、`PathwayNode`/`PathwayEdge`/`PathwayGraph`(+`NodeType`/`EdgeType`)、`PatientPathway`(+`Status`/`Enter`/`Detail`)、`PathwayProgressor`/`PathwayProgressDecision`/`PathwayAdvanceRequest`、`PathwayVariance`、`ClinicalClock`(+`Status`)、`SpecialtyPackage`、`SpecialtyMetricBinding`、`PathwaySimulateRequest`/`Response`、`PathwayEngineService`/`Controller`(+ 安全测试)。
- 缺口（本卡补）：① **三层前端**（L1 模板 / L2 React Flow 节点画布 / L3 DSL）；② **仿真**接 [API-01](API-01.md) 真实快照；③ 发布 **7 步流 + §3 状态机** 接 [SYS-04](SYS-04.md)；④ **关键时钟绑定质控**（[SYS-04](SYS-04.md)/D4 评估消费）；⑤ **随访接续**（出径 → 随访计划交接 D3 FOLLOW）。
- 2026-06-02 PR1 进展（`codex/d2-path-01-backend-contracts`）：后端仿真与实时推进新增 `snapshotId`，通过 `ContextSnapshotService.findById` 消费 [API-01](API-01.md) 真实快照，返回 `contextQualityStatus`、`missingFields`、`mappingStatus`、`contextResourceCounts`；`PathwayProgressor` 支持从快照事实评估 `condition_json`（如检验值条件），默认边仅作 fallback，不再抢走已满足条件边；`PathwayProgressDecision` / `PathwayAdvanceResponse` 携带命中边与事实证据；有时窗节点发布时必须绑定质控指标，缺失返回 `PATHWAY_CLOCK_MISSING`，入径 / 推进创建的 `ClinicalClock.metricCode` 来自真实 `SpecialtyMetricBinding`。未冒领三层前端、7 步发布、随访接续和 D4 超时信号闭环。
- 2026-06-02 PR2 与 2026-06-07 统一验收：`PathwayTemplates` 已清理旧 `DEFAULT_NODES_JSON` / `DEFAULT_EDGES_JSON`、伪画布与粘贴式快照入口，形成 L1 模板、L2 React Flow 节点画布、L3 DSL 三层编辑；支持节点拖拽 / 键盘移动、连线、节点 / 边删除和布局持久化，所有操作回写同一份 `PathwayTemplate` 表单与 DSL。详情抽屉展示 L1 / L2 / L3 与真实快照试运行，仿真从 [API-01](API-01.md) ACTIVE 快照列表 / 详情选择 `snapshotId`，展示后端返回的快照质量、映射状态和节点轨迹；`DEFER-017` 已关闭。
- 2026-06-03 PR3 进展（`codex/d2-path-01-pr3-release-followup`）：路径发布前新增真实影响摘要接口 `GET /pathway-templates/{templateId}/impact`，摘要只来自路径拓扑、关键时钟绑定和患者路径实例事实，不把生命周期状态写入 digest，确保灰度后可基于同一影响摘要继续全量确认；草稿发布必须携带当前 `impactDigest` 与审核说明，进入 `canary_release` 默认 10% 灰度；已发布模板支持院级管理员 `full_rollout` 全量确认；当前已发布模板可回滚到同编码 `OFFLINE` 历史版本，当前版本下线、目标版本恢复发布并写 `ROLLBACK` 审计；列表接口补 `templateCode` 过滤，前端回滚候选来自同编码历史版本查询，不再依赖当前分页。患者路径 `COMPLETED` 后通过路径→随访端口生成 / 复用随访计划，`PathwayAdvanceResponse` 返回 `followupPlanId`、`followupTaskCount` 和交接状态。前端 `PathwayTemplates` 新增“7 步流发布”页签，标题栏按钮只跳页签，不再绕过影响摘要；页签展示 digest、灰度、全量和回滚操作。未冒领通用 SYS-04 泛化 `ReleasePort/ReplayPort`。
- 2026-06-07 P10-3 进展：`PathwayTemplate.parentTemplateId` 与 `PathwayNode.disabledFlag` 已进入 5 方言迁移；后端提供继承解析与 `GET /api/v1/engine/pathway/pathway-templates/{templateId}/inheritance-diff`，支持下级覆盖、下级新增和禁用父级节点，返回差异项与合并后的有效节点/边；前端路径详情新增“继承差异”页签，创建模板可选择父级模板，禁用继承节点不进入画布、发布拓扑、指标绑定校验、仿真、患者入径和推进主链路。
- 2026-06-08 P10-4 进展：新增 `pathway_outcome_binding` 五方言迁移，路径模板可按模板/阶段/里程碑绑定 ACTIVE `EvaluationIndicator`；创建、详情、发布影响、患者入径/详情/推进均返回结局闭环信息。仿真请求扩展为单快照、队列回放和时光机模式，回放只读不写库；患者多路径并行时检测 `ORDER_SET` 同医嘱引用冲突，仅在路径详情/推进响应提示协调，绝不自动改医嘱。
- 2026-06-08 H-1 进展：路径富节点纳入统一 authoring 能力开关，支持系统默认与租户覆盖；关闭时模板发布门禁和患者路径推进均拒绝富节点，不继续解释高级节点语义；配置中心页面可配置租户覆盖。
- 2026-06-08 H-2 进展：临床事件触发路径推进与规则、CDSS 共用 `medkernel.events.sync-timeout-ms` 同步求值预算；预算耗尽或下游不可用时事件进入 `FAILED/ENG-SYS-002` 人工核查，不继续误推进路径。
- 2026-06-08 H-4 进展：路径发布门禁拒绝可达有向环；`SUBPATHWAY` 不能引用当前路径模板；仿真超过节点数最大步数即拒绝，避免旧图无限推进。条件片段库当前尚无运行模型，片段环检测随 P12-5 接入，不伪造实现。
- 2026-06-08 H-5 进展：路径发布门禁统一校验模板入 / 出径条件、节点配置、边条件和结局指标绑定的显式包版本；入径、推进、单快照仿真和队列回放按模板所属专病包版本校验上下文快照；引用字段、子路径、医嘱集和结局指标摘要进入影响 `digest`。
- 2026-06-08 H-6 进展：路径变异发出 `PathwayVarianceRecorded` 并进入待办 / 通知；关键时钟 SLA 从 RUNNING 投影到 TIMEOUT 时发出 `ClockSlaBreached` 并进入待办 / 通知 / 质控驾驶舱，事件统一携带 `tenantId`、`traceId`、`packageVersion`。

## 功能要求（原子可测条目）

- [x] **FR-1 三层配置（B0）**：L1 模板实例化 / L2 React Flow 节点画布（节点/边/分支）/ L3 DSL；三层产出同一 `PathwayTemplate`，L2↔L3 互转无损，图形编辑布局随 DSL 持久化。
- [x] **FR-2 患者入径与推进**：患者入径 `PatientPathway` → `PathwayProgressor` 按上下文/规则推进节点；产 `PathwayProgressDecision` 可解释。
- [x] **FR-3 变异管理**：偏离路径记 `PathwayVariance`（原因/节点/时点），不静默跳过。
- [x] **FR-4 关键时钟**：节点绑 `ClinicalClock`（如"术后 24h 内 X"）；超时触发待办/质控信号（D4 消费）。
- [x] **FR-5 仿真 + 7 步流**：仿真选真实快照走路径（不写库）；发布走 7 步流（[SYS-04](SYS-04.md)）。
- [x] **FR-6 随访接续**：患者出径 → 生成随访计划交接 D3 随访（FOLLOW），不断链。
- [x] **FR-7 多级模板继承差异合并**：STANDARD→HOSPITAL→DEPARTMENT→SPECIALTY 下级可覆盖/新增/禁用上级节点；系统提供 diff 视图和有效图解析，避免重复维护。
- [x] **FR-8 结局与多路径协调**：模板/阶段/里程碑可绑定结局指标；患者路径实例返回结局闭环；队列回放/时光机只读仿真；多路径 `ORDER_SET` 冲突仅提示协调不自动改医嘱。
- [x] **FR-9 能力开关灰度**：路径富节点按系统默认 / 租户覆盖灰度；开关关闭时发布与推进均诚实拒绝富节点，不误算高级语义。
- [x] **FR-10 事件触发硬超时**：临床事件触发路径推进必须在配置预算内完成；超时或下游不可用时不推进节点，事件转人工核查。
- [x] **FR-11 环与步数护栏**：路径模板发布拒绝可达有向环和当前模板自引用子路径；仿真运行期有最大步数护栏，旧坏图不能无限推进。
- [x] **FR-12 引用包版本一致性**：路径模板引用的字段、条件、子路径、医嘱集和结局指标必须与模板所属专病包版本一致；发布门禁与运行期同时拒绝跨包版本引用；引用资产变更进入影响分析。
- [x] **FR-13 领域事件协同**：路径变异与 SLA 超时只从后端真实路径事实发出，统一对接现有待办、通知与质控驾驶舱，不由前端伪造提醒。

## 接口契约 / 页面契约

### 接口契约（引擎/API 卡）

- 端点：推进/仿真/变异/时钟能力，REST 客户面在 [API-06](API-06.md)；继承差异 `GET /api/v1/engine/pathway/pathway-templates/{templateId}/inheritance-diff`。
- DTO：复用 `PathwayTemplate`/`PatientPathway`/`PathwayAdvanceRequest`/`PathwayVariance`/`ClinicalClock`/`PathwaySimulateRequest`；新增结局绑定、回放步骤和协调提示响应字段。
- 状态机：路径版本核心 §3 配置类 + 变更类；患者路径运行态 `PatientPathwayStatus`；**禁自创**。
- 幂等 / 错误码 / traceId：节点推进按 `(patient_pathway, node, event)` 幂等；超时未配置时钟 → `PATHWAY_CLOCK_MISSING`；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。

### 页面契约（页面卡 —— 路径配置页，S6）

- 路由元数据：sectionKey `pilot` / menuKey `pathway-config` / requiredPermissions 路径配置 / requiredRoles 专科专家·科主任。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 路径列表 + React Flow 节点画布 + DSL/模板编辑 + 继承差异 + 仿真 + 7 步流（[INFRA-09](../D1/INFRA-09.md)）+ 六态。
- 主按钮 ≤1 / 默认筛选 ≤3（专科/状态/版本）/ 默认角色视图。
- 五维 RBAC：菜单 / 动作（发布权）/ 数据（org）/ 资产（路径包）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码。

## 数据与迁移

- 表族：`pathway_template`（含 `parent_template_id`）/`pathway_node`（含 `disabled_flag`）/`pathway_edge`/`patient_pathway`/`pathway_variance`/`clinical_clock`/`specialty_package`/`specialty_metric_binding`/`pathway_outcome_binding`。
- 主键 ULID；唯一约束：`(pathway_identity, org_scope, version)` ACTIVE 唯一（[SYS-04](SYS-04.md)）；索引：`status`、`specialty`、`org_path`。
- 5 方言迁移一致 + 中文注释。

## 视角清单（11 视角逐条）

1. **产品架构**：路径三层配置 + 运行驱动 + 随访接续，配置外置。
2. **产品体验**：★React Flow 节点画布 + 三层 + 仿真 + 7 步流，六态；国产浏览器/老旧分辨率可读。
3. **系统与数据架构**：推进幂等；关键时钟到点触发；大路径列表分页；仿真不写库。
4. **临床医疗安全**：变异不静默跳过；关键时钟超时升级；高危路径发布门禁（同 [RULE-01](RULE-01.md)）。
5. **知识与数据治理**：路径版本化可回滚；节点引用规则/知识/字典标准码。
6. **安全合规与监管**：配置/发布/变异/超时留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：专病路径七层继承覆盖；集团模板 + 院内定制。
8. **集成与互操作**：路径消费标准上下文（[API-01](API-01.md)）；随访接续交接 D3。
9. **运维 / SRE / 国产化**：5 方言；画布国产浏览器兼容；灰度/回滚。
10. **质量与真实性审计**：仿真真实快照；关键时钟真实计时非伪造；无写死路径常量（铁律 #1）。
11. **AI / 模型治理与可降级**：★**B0＝人工三层配置 + 确定性推进**；AI 辅助路径生成（第二波）必经审核仿真，关模型路径引擎照常。

## 适用不变量

- 命中核心约束：**§4 7 步流** · **§3 状态机** · **铁律 #11 配置外置** · **§9 继承覆盖** · **§13 高危门禁** · **依赖 [SYS-04](SYS-04.md)/[API-01](API-01.md)/[RULE-01](RULE-01.md)**。
- 本卡落点：路径从"写死流程"变为"三层配置 + 时钟 + 变异 + 随访接续"的可运行可回滚资产。

## 验收 + 验证

- [x] **AC-1（FR-1）**：L2 节点画布编辑 ↔ L3 DSL 互转无损，产出同一模板；节点拖拽、键盘移动、连线和删除均同步到同一表单状态。
- [x] **AC-2（FR-2/3）**：患者入径推进节点、解释正确；偏离记 `PathwayVariance` 不静默。
- [x] **AC-3（FR-4）**：节点绑关键时钟，超时触发待办/质控信号；未配时钟的超时节点发布 → `PATHWAY_CLOCK_MISSING` 告警。
- [x] **AC-4（FR-5/6）**：仿真不写库；7 步流灰度→全量→回滚；出径生成随访计划交接 D3。
- [x] **AC-5（FR-7）**：下级模板覆盖/新增/禁用父级节点后，diff 与合并后的有效节点/边一致；被禁用节点不进入画布、发布拓扑、指标绑定校验、仿真、患者入径和推进主链路。
- [x] **AC-6（FR-8）**：创建模板可绑定结局指标并写入资产内容；患者路径详情/推进返回结局绑定与多路径协调提示；队列回放/时光机不写运行实例。
- [x] **AC-7（FR-11）**：发布含 `A -> B -> A` 有向环的模板被拒；`SUBPATHWAY` 自引用当前模板被拒；旧图仿真超过最大推进步数返回 `ENG_PATHWAY_004`。
- [x] **AC-8（FR-12）**：模板节点、边、入 / 出径条件或结局绑定显式引用其他 `packageVersion` 时发布被拒；患者入径、推进和仿真快照包版本与模板所属专病包不一致时不继续执行；引用资产集合变化会改变影响摘要。
- 关联 A1–A9 剧本：A3 路径配置、A4 发布回滚、A7 随访接续。
- T-GATE：前后端真实性门禁全绿（仿真/时钟真实、无写死流程）。
- B0 验收：三层配置 + 确定性推进，**天然 B0**；关模型行为不变。

## 完工证据

- 代码 permalink：节点画布前端 + `PathwayProgressor` 接 [API-01](API-01.md) + 关键时钟触发 + 仿真 + 7 步流接 [SYS-04](SYS-04.md) + 随访接续 + 5 方言迁移。
- 测试：三层互转 + 推进/变异 + 关键时钟超时 + 仿真真实快照 + 随访接续 + 灰度回滚 E2E。
- 当前 PR1 本地证据：红灯测试先失败于缺 `ContextSnapshotService` 依赖、`snapshotId` 仿真 / 实时推进合同、快照证据响应、条件事实证据和 `PATHWAY_CLOCK_MISSING` 错误码；默认边 fallback 红灯先失败于旧逻辑返回 `PLAN` 而非满足条件的 `FOLLOWUP`。随后聚焦后端 `mvn -q -Dtest=PathwayProgressorTest,PathwayEngineServiceTest,PathwayEngineApiContractTest,PathwayEngineControllerSecurityTest,PathwayRepositoryTest,ErrorCodeTest test` 通过；后端全量 `mvn -q test` 通过，含 H2、PostgreSQL 15.18、Oracle 21.3 迁移至 V53；前端 `npm run verify`（43 文件 / 226 测试）与 `npm run build` 通过；T-GATE `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 34/34 通过，`authenticity-guard --mode=all` 扫描 791 文件通过，配置边界 inventory 扫描 733 文件通过，changed 迁移扫描 0 文件通过，`scripts/check-comment-zh.sh --mode=full` 引擎 / shared Javadoc 100% 且历史 COMMENT gap 继续归 `DEFER-006`，`git diff --check` 通过。`DEFER-003` 前端测试 / 构建噪声、`DEFER-006` 历史迁移 COMMENT gap、`DEFER-016` 历史迁移 inventory 债务保持 open，不冒领清零。PR1 仅覆盖后端真实快照仿真 / 推进、条件推进证据、变异 / 时钟基础和发布关键时钟门禁，不勾选整卡完成。
- 当前 PR2 与 2026-06-07 统一验收证据：原三层编辑、真实快照仿真证据保持有效；新增 React Flow 模型 / 组件 / 页面测试覆盖布局持久化、连线和删除。`npm run verify` 79文件 / 528测试、`npm run build` 3396模块通过；路径图Playwright桌面与390px窄屏2/2通过，覆盖连线、删除、拖拽、DSL布局持久化、Escape不误关弹窗和根级无横向溢出；生产依赖审计0漏洞；T-GATE 38/38、真实性1294文件、配置边界1214文件、中文注释0 fail / 0 warn、`git diff --check`通过。`DEFER-015`、`DEFER-017`均已关闭。
- 当前 PR3 本地证据：红灯测试先失败于缺 `PathwayTemplateImpactResponse` / `templateImpact` / 发布 digest 合同、缺结径随访交接口、缺前端“7 步流发布”页签，随后补真实影响摘要、`impactDigest + reason` 门禁、`canary_release` 10% 灰度、`full_rollout` 院级确认、`evidence_rollback` 回滚到同编码已下线版本、路径结径随访交接端口和前端发布页签。新增红灯再暴露全量 / 回滚缺服务合同后已补齐；提交前评审又补 `templateImpactDigestIgnoresLifecycleStatusChanges` 红绿回归，防止生命周期状态改变导致灰度后全量确认 digest 失配；本轮审查发现回滚候选依赖当前分页的体验缺口，已补 `templateCode` 列表过滤和前端同编码历史版本查询。验证：聚焦后端 `mvn -q -Dtest=PathwayEngineServiceTest,PathwayRepositoryTest,PathwayEngineApiContractTest,FollowupEngineServiceTest test` 通过；后端全量 `mvn -q test` 通过，含 PostgreSQL 15.18 与 Oracle 21.3 迁移至 V53；前端目标测试 `npm test -- src/pages/tenant/PathwayTemplates.test.tsx src/pages/tenant/RulePathwayCleanliness.test.ts` 通过 2 文件 / 8 测试；`npm run typecheck`、`npm run verify`（44 文件 / 231 测试）、`npm run build` 通过；T-GATE `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 34/34 通过，`node scripts/authenticity-guard.mjs --mode=all` 扫描 791 文件通过，配置边界 inventory 扫描 733 文件通过，`scripts/check-comment-zh.sh` 0 fail / 0 warn，`git diff --check` 通过。生产依赖 `npm audit --omit=dev` 为 0 漏洞，开发工具链 Vite / Vitest 审计项继续归 `DEFER-002`，不冒领清零。Browser 插件仍无可用 `iab`，按 `DEFER-004` 改用项目 Playwright 访问 `http://127.0.0.1:5175/pathway/templates` 验证发布页签展示 `impactDigest`、灰度发布 / 全量确认 / 回滚三类请求体均携带 `impactDigest`、审核说明、`releaseStep` 与标准上下文，同编码历史查询 `status=OFFLINE&templateCode=PATH.CARDIO.REVIEW&page=1&size=100`，非预期控制台错误 0，截图 `/tmp/medkernel-pathway-pr3-release-rerun.png`。
- 当前 P10-4 本地证据：新增红灯覆盖结局绑定、无效/非 ACTIVE 指标拒绝、队列回放只读、多路径 `ORDER_SET` 冲突仅提示协调；后端 `mvn -q test` 通过，Surefire 汇总 281 文件 / 1889 测试 / 0 failures / 0 errors / 3 skipped；前端 `npm run verify` 通过，78 文件 / 549 测试，并新增结局指标创建 payload 与队列回放 payload/轨迹展示用例；项目 Playwright `E2E_API_BASE_URL=http://localhost:18080/medkernel/api/v1 VITE_API_PROXY_TARGET=http://localhost:18080 npx playwright test e2e/pathway-graph-editor.spec.ts --project=chromium` 3/3 通过。首次 Playwright 未配置代理目标被 Vite 配置边界拒绝，补真实后端代理环境后通过。OpenSpec strict 通过；T-GATE 三 guard 38/38 通过；changed-mode 真实性 / 配置边界 / 迁移规约分别扫描 19 / 16 / 5 个文件且 0 阻断；中文注释 0 fail / 0 warn；`git diff --check` 通过。
- 当前 H-4 本地证据：路径边界红灯先失败于发布未拒绝有向环 / 当前模板自引用子路径、旧图仿真未触发最大步数护栏；`PathwayEngineServiceTest` 聚焦与后端全量 `1911` tests / 0 failures / 0 errors / 3 skipped 通过；OpenSpec strict、T-GATE 38/38、changed-mode 真实性 / 配置边界 / 迁移规约分别扫描 4 / 4 / 0 个文件且 0 阻断，中文注释与空白检查均通过。
- 当前 H-5 本地证据：红灯先失败于路径仿真未按模板包版本校验快照、发布门禁未拒绝跨包节点引用；随后 `PathwayEngineServiceTest` 聚焦通过，后端全量 `mvn -q test` 汇总 `1916` tests / 0 failures / 0 errors / 3 skipped。
- 当前 H-6 本地证据：红灯先失败于缺少路径领域事件出口与消费 adapter；随后 `PathwayEngineServiceTest` 变异 / SLA 聚焦通过，协同 adapter 测试验证 `PATHWAY_EVENT` 待办通知与 `CLOCK_SLA_BREACH` 质控告警落库；后端全量 `mvn -q test` 汇总 `1920` tests / 0 failures / 0 errors / 3 skipped；OpenSpec strict、T-GATE 38/38、changed-mode 真实性 / 配置边界 / 迁移规约分别扫描 15 / 15 / 5 个文件且 0 阻断。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（16d，后端 + 三层前端；按 PR 拆分）

- PR1：后端契约 + 推进/变异/关键时钟 + 仿真接 [API-01](API-01.md) → AC-2/3。
- PR2：L1/L2/L3 三层前端（React Flow 节点画布 + DSL，六态）+ 互转无损 → AC-1；`DEFER-017`已关闭。
- PR3：7 步流发布（[SYS-04](SYS-04.md)）+ 随访接续交接 D3 + 高危门禁 → AC-4。
