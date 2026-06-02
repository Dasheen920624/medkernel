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
提供路径引擎 **B0 真实**：**三层配置**（L1 模板 / L2 节点画布 X6/G6 / L3 DSL）+ **7 步流**发布 + **仿真** + **关键时钟**（绑定质控时限）+ **变异管理** + **随访接续**。让专科专家/科主任配置专病路径，患者入径后节点推进/变异/超时由引擎驱动，供 D3 临床运行消费。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/pathway` **后端已实质建成**，本卡＝**契约化 + 三层前端 + 仿真/随访接续补全**：
- 已有（后端）：`PathwayTemplate`(+`Level`/`Status`/`Detail`)、`PathwayNode`/`PathwayEdge`/`PathwayGraph`(+`NodeType`/`EdgeType`)、`PatientPathway`(+`Status`/`Enter`/`Detail`)、`PathwayProgressor`/`PathwayProgressDecision`/`PathwayAdvanceRequest`、`PathwayVariance`、`ClinicalClock`(+`Status`)、`SpecialtyPackage`、`SpecialtyMetricBinding`、`PathwaySimulateRequest`/`Response`、`PathwayEngineService`/`Controller`(+ 安全测试)。
- 缺口（本卡补）：① **三层前端**（L1 模板 / L2 节点画布 X6/G6 / L3 DSL）；② **仿真**接 [API-01](API-01.md) 真实快照；③ 发布 **7 步流 + §3 状态机** 接 [SYS-04](SYS-04.md)；④ **关键时钟绑定质控**（[SYS-04](SYS-04.md)/D4 评估消费）；⑤ **随访接续**（出径 → 随访计划交接 D3 FOLLOW）。
- 2026-06-02 PR1 进展（`codex/d2-path-01-backend-contracts`）：后端仿真与实时推进新增 `snapshotId`，通过 `ContextSnapshotService.findById` 消费 [API-01](API-01.md) 真实快照，返回 `contextQualityStatus`、`missingFields`、`mappingStatus`、`contextResourceCounts`；`PathwayProgressor` 支持从快照事实评估 `condition_json`（如检验值条件），默认边仅作 fallback，不再抢走已满足条件边；`PathwayProgressDecision` / `PathwayAdvanceResponse` 携带命中边与事实证据；有时窗节点发布时必须绑定质控指标，缺失返回 `PATHWAY_CLOCK_MISSING`，入径 / 推进创建的 `ClinicalClock.metricCode` 来自真实 `SpecialtyMetricBinding`。未冒领三层前端、7 步发布、随访接续和 D4 超时信号闭环。

## 功能要求（原子可测条目）
- [ ] **FR-1 三层配置**：L1 模板实例化 / L2 节点画布（节点/边/分支/并行）/ L3 DSL；三层产出同一 `PathwayTemplate`，互转无损。
- [ ] **FR-2 患者入径与推进**：患者入径 `PatientPathway` → `PathwayProgressor` 按上下文/规则推进节点；产 `PathwayProgressDecision` 可解释。
- [ ] **FR-3 变异管理**：偏离路径记 `PathwayVariance`（原因/节点/时点），不静默跳过。
- [ ] **FR-4 关键时钟**：节点绑 `ClinicalClock`（如"术后 24h 内 X"）；超时触发待办/质控信号（D4 消费）。
- [ ] **FR-5 仿真 + 7 步流**：仿真选真实快照走路径（不写库）；发布走 7 步流（[SYS-04](SYS-04.md)）。
- [ ] **FR-6 随访接续**：患者出径 → 生成随访计划交接 D3 随访（FOLLOW），不断链。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：推进/仿真/变异/时钟能力，REST 客户面在 [API-06](API-06.md)。
- DTO：复用 `PathwayTemplate`/`PatientPathway`/`PathwayAdvanceRequest`/`PathwayVariance`/`ClinicalClock`/`PathwaySimulateRequest`。
- 状态机：路径版本核心 §3 配置类 + 变更类；患者路径运行态 `PatientPathwayStatus`；**禁自创**。
- 幂等 / 错误码 / traceId：节点推进按 `(patient_pathway, node, event)` 幂等；超时未配置时钟 → `PATHWAY_CLOCK_MISSING`；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡 —— 路径配置页，S6）
- 路由元数据：sectionKey `pilot` / menuKey `pathway-config` / requiredPermissions 路径配置 / requiredRoles 专科专家·科主任。
- 结构：PageShell（[BASE-08](../D0/BASE-08.md)）+ 路径列表 + 节点画布（X6/G6）+ DSL/模板编辑 + 仿真 + 7 步流（[INFRA-09](../D1/INFRA-09.md)）+ 六态。
- 主按钮 ≤1 / 默认筛选 ≤3（专科/状态/版本）/ 默认角色视图。
- 五维 RBAC：菜单 / 动作（发布权）/ 数据（org）/ 资产（路径包）/ 环境。
- 样式：仅引用 [BASE-10](../D0/BASE-10.md) token + [体验契约](../../EXPERIENCE_CONTRACT.md)；禁硬编码。

## 数据与迁移
- 表族（已有）：`pathway_template`/`pathway_node`/`pathway_edge`/`patient_pathway`/`pathway_variance`/`clinical_clock`/`specialty_package`/`specialty_metric_binding`。
- 主键 ULID；唯一约束：`(pathway_identity, org_scope, version)` ACTIVE 唯一（[SYS-04](SYS-04.md)）；索引：`status`、`specialty`、`org_path`。
- 5 方言迁移一致 + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：路径三层配置 + 运行驱动 + 随访接续，配置外置。
2. **产品体验**：★节点画布（X6/G6）+ 三层 + 仿真 + 7 步流，六态；国产浏览器/老旧分辨率可读。
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
- [ ] **AC-1（FR-1）**：L2 节点画布编辑 ↔ L3 DSL 互转无损，产出同一模板。
- [ ] **AC-2（FR-2/3）**：患者入径推进节点、解释正确；偏离记 `PathwayVariance` 不静默。
- [ ] **AC-3（FR-4）**：节点绑关键时钟，超时触发待办/质控信号；未配时钟的超时节点发布 → `PATHWAY_CLOCK_MISSING` 告警。
- [ ] **AC-4（FR-5/6）**：仿真不写库；7 步流灰度→全量→回滚；出径生成随访计划交接 D3。
- 关联 A1–A9 剧本：A3 路径配置、A4 发布回滚、A7 随访接续。
- T-GATE：前后端真实性门禁全绿（仿真/时钟真实、无写死流程）。
- B0 验收：三层配置 + 确定性推进，**天然 B0**；关模型行为不变。

## 完工证据
- 代码 permalink：节点画布前端 + `PathwayProgressor` 接 [API-01](API-01.md) + 关键时钟触发 + 仿真 + 7 步流接 [SYS-04](SYS-04.md) + 随访接续 + 5 方言迁移。
- 测试：三层互转 + 推进/变异 + 关键时钟超时 + 仿真真实快照 + 随访接续 + 灰度回滚 E2E。
- 当前 PR1 本地证据：红灯测试先失败于缺 `ContextSnapshotService` 依赖、`snapshotId` 仿真 / 实时推进合同、快照证据响应、条件事实证据和 `PATHWAY_CLOCK_MISSING` 错误码；默认边 fallback 红灯先失败于旧逻辑返回 `PLAN` 而非满足条件的 `FOLLOWUP`。随后聚焦后端 `mvn -q -Dtest=PathwayProgressorTest,PathwayEngineServiceTest,PathwayEngineApiContractTest,PathwayEngineControllerSecurityTest,PathwayRepositoryTest,ErrorCodeTest test` 通过；后端全量 `mvn -q test` 通过，含 H2、PostgreSQL 15.18、Oracle 21.3 迁移至 V53；前端 `npm run verify`（43 文件 / 226 测试）与 `npm run build` 通过；T-GATE `node --test scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs scripts/authenticity-guard.test.mjs` 34/34 通过，`authenticity-guard --mode=all` 扫描 791 文件通过，配置边界 inventory 扫描 733 文件通过，changed 迁移扫描 0 文件通过，`scripts/check-comment-zh.sh --mode=full` 引擎 / shared Javadoc 100% 且历史 COMMENT gap 继续归 `DEFER-006`，`git diff --check` 通过。`DEFER-003` 前端测试 / 构建噪声、`DEFER-006` 历史迁移 COMMENT gap、`DEFER-016` 历史迁移 inventory 债务保持 open，不冒领清零。PR1 仅覆盖后端真实快照仿真 / 推进、条件推进证据、变异 / 时钟基础和发布关键时钟门禁，不勾选整卡完成。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（16d，后端 + 三层前端；按 PR 拆分）
- PR1：后端契约 + 推进/变异/关键时钟 + 仿真接 [API-01](API-01.md) → AC-2/3。
- PR2：L1/L2/L3 三层前端（节点画布 X6/G6 + DSL，六态）+ 互转无损 → AC-1。
- PR3：7 步流发布（[SYS-04](SYS-04.md)）+ 随访接续交接 D3 + 高危门禁 → AC-4。
