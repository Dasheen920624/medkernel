# FOLLOW-01 · 随访引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D3 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S12 智能随访 · 核心 §11 B0 先于模型 · 详规 §1.4 受控事实驱动。

## 身份
- 卡 ID：FOLLOW-01（引擎卡；`FollowupEvent`/计划生成单一归属）
- 域：D3 临床运行
- 关联场景：S12 智能随访
- 依赖卡：[PATH-01](../D2/PATH-01.md) 路径（随访接续）· [API-01](../D2/API-01.md) 上下文 · [SYS-08](../D2/SYS-08.md) 权威版本 · [API-09](API-09.md) 对外契约 · [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 协同
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
把随访做成 **B0 真实**：依据**受控事实**（路径节点/诊断/事件）生成随访计划与任务 → 问卷 → 异常事件回流回院，**不写死人群、不伪造作答、关模型仍可跑**。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend` 为准）
已有实质基础：`engine/followup/` 下 `FollowupEngineService` / `FollowupEngineController` + `FollowupEvent(+Repository)` + `FollowupAbnormalReportRequest` + 安全/契约测试。本卡＝把"受控事实驱动的计划生成 + 任务/问卷/异常回流"框架化为引擎核心，对外契约归 [API-09](API-09.md)。

## 功能要求（原子可测条目）
- [x] FR-1 计划生成：按路径节点/诊断/事件等**受控事实**生成随访计划（规则可解释，不写死人群）。（PR1：路径/诊断/风险分层受控事实；事件触发归 PR2）
- [x] FR-2 任务下发：计划展开为带时点的任务（关键时钟 `ClinicalClock`），到期触发。（PR1：计划生成时绑定已有关键时钟；调度执行归 PR2/PR3）
- [x] FR-3 问卷：模板化问卷下发 + 结构化回收。（PR2：问卷模板 / 作答载荷必须为 JSON 对象，保存前归一化，拒绝数组 / 字符串等非结构化载荷）
- [x] FR-4 异常回流：异常作答/事件（`FollowupAbnormalReportRequest`）触发回院 + 通知 + 上下文回流。（PR2：异常载荷结构化；返院通知保留来源异常载荷；结果回流按幂等键复用 `RESULT_INFLOW` 事件与上下文快照）
- [ ] FR-5 B0 降级：智能分层/生成为挂点，关闭按确定性规则计划 `MODEL_DISABLED`。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 对外端点契约归 [API-09](API-09.md)；本卡负责引擎内计划生成算法 + `FollowupEvent` 状态机（待执行→进行中→完成/异常回院）。
- 幂等 / traceId：计划生成对同一受控事实集幂等可复现；trace（[OBS-01](../D0/OBS-01.md)）。

## 数据与迁移
- 表族：`followup_event` + 计划/任务/问卷/作答表 + 组织字段 + 审计；五方言（[BASE-05](../D0/BASE-05.md)）
- 唯一约束：患者+计划+时点去重；索引：患者/状态/到期时间

## 视角清单（11 视角逐条）
1. 产品架构：随访闭环核心引擎，接路径随访接续。
2. 产品体验：N·A（页面 [FUP-01](FUP-01.md)）。
3. 系统与数据架构：到期任务调度 10万级；P95 列表 ≤1s；时钟驱动。
4. 临床医疗安全：异常回院不漏派；随访不替代诊疗判断。
5. 知识与数据治理：计划绑定路径/知识版本（[SYS-08](../D2/SYS-08.md)），可追溯。
6. 安全合规与监管：问卷隐私最小化；异常/回流留审计（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：随访按 `OrgContext`/随访团队作用域。
8. 集成与互操作：异常回院经 [SVC-CLINICAL-03](SVC-CLINICAL-03.md) 通知；结果回流标准化到 [API-01](../D2/API-01.md)。
9. 运维 / SRE / 国产化：时钟/队列可观测、可重试。
10. 质量与真实性审计：★人群受控事实驱动、不写死；无伪造作答。
11. AI / 模型治理与可降级：★智能分层为挂点，关闭 `MODEL_DISABLED` 用确定性规则计划。

## 适用不变量
- 命中核心约束：**核心 §11 B0** · **§13 真实性** · **§7 权威版本** · **铁律 #1**。
- 本卡落点：受控事实驱动的确定性随访计划 + 异常回流闭环。

## 验收 + 验证
- [x] AC-1（FR-1/2）：计划由受控事实生成、可解释；任务按时点触发。（PR1：受控事实生成解释 + 任务绑定 `ClinicalClock` 到期时间）
- [x] AC-2（FR-3/4）：问卷回收结构化；异常触发回院 + 回流。（PR2：结构化问卷 / 异常 / 回流载荷校验，回流幂等不重复创建快照）
- [ ] AC-3（FR-5）：关模型确定性计划仍可跑。
- 关联 A1–A9 剧本：A7 随访接续。
- T-GATE：后端真实性门禁全绿（不写死人群 / 无伪造作答）。
- B0 验收：★关模型随访计划/任务/异常回流全可用。

## 大卡工序（5d）
- PR1：受控事实计划生成 + 时钟任务 + 门禁 → 验收
- PR2：问卷 + 异常回院 + 回流 → 验收
- PR3：B0 降级 + 复现测试 → 验收

## 完工证据
- PR1 实施记录（2026-06-04）：`FollowupEngineService.generatePlan` 先校验路径 / 诊断 / 风险分层受控事实，再复用幂等或患者路径计划；新计划持久化 `source_fact_type`、`source_fact_id`、`generation_rule_code`、`generation_explanation`，无 `taskTypes` 时按受控事实确定性派生 `QUESTIONNAIRE`，高风险追加 `OUTPATIENT`；存在患者路径 `ClinicalClock` 时任务绑定 `clinical_clock_id` 并继承 `due_at`。
- PR1 迁移：V71 五方言新增随访计划来源事实 / 规则解释字段与随访任务 `clinical_clock_id`，索引 `idx_followup_plan_fact`、`idx_followup_task_clock`，保留中文 COMMENT 与回滚说明。
- PR1 测试：新增 `generatePlanRejectsTaskTypesWithoutControlledFacts`、`generatePlanRejectsIdempotencyReplayWithoutControlledFacts`、`generatePlanDerivesTasksFromControlledFactsAndBindsClinicalClock`，覆盖无受控事实拒绝、幂等重放不可绕过红线、时钟绑定。
- PR1 本地验证：`mvn -q -Dtest=FollowupEngineServiceTest#generatePlanRejectsIdempotencyReplayWithoutControlledFacts test` 先失败后通过；`mvn -q -Dtest=FollowupEngineServiceTest,FollowupEngineControllerTest,FollowupEngineControllerSecurityTest,PathwayEngineServiceTest,MigrationBaselineContractTest,H2BaselineMigrationTest test` 通过；`mvn -q -Dtest=FlywayMultiDialectSmokeTest test` 通过（H2 / PostgreSQL 15.18 / Oracle 21.3 迁移至 V71 并二次 no-op）；`mvn -q test` 通过；前端 `npm run verify` 51 文件 / 311 测试通过；T-GATE 脚本自测 34 项、真实性全量、配置边界 inventory、V71 迁移 files-mode、中文注释、`git diff --check` 均通过。
- PR2 实施记录（2026-06-04）：`FollowupEngineService` 对问卷模板 / 作答、异常上报、结果回流载荷统一执行 JSON 对象校验并归一化保存；`submitQuestionnaire` 不再把数组 / 字符串等非结构化内容当作真实作答；异常返院通知事件携带来源异常结构化载荷；`backflowResult` 对同一 `RESULT_INFLOW` 幂等键复用已有事件与 `contextSnapshotId`，避免重复创建上下文快照和重复回流事件。
- PR2 测试：新增 `submitQuestionnaireRejectsNonJsonObjectAnswer`、`backflowResultReusesExistingResultInflowEventByIdempotencyKey`；红灯先分别失败于旧实现返回 `NOT_FOUND` 和重复查计划创建回流，修复后目标用例通过。
- PR2 本地验证：`mvn -q -Dtest=FollowupEngineServiceTest#submitQuestionnaireRejectsNonJsonObjectAnswer+backflowResultReusesExistingResultInflowEventByIdempotencyKey test` 通过；`mvn -q -Dtest=FollowupEngineServiceTest,FollowupEngineControllerTest,FollowupEngineControllerSecurityTest test` 通过；`mvn -q test` 通过（H2 / PostgreSQL 15.18 / Oracle 21.3 迁移至 V71 并二次 no-op）；前端新 worktree 首次 `npm run verify` 因未安装依赖出现 `eslint: command not found`，执行 `npm ci --prefer-offline --no-audit` 后 `npm run verify` 51 文件 / 311 测试通过，既有 React Router / act warning 仍归 [DEFER-003](../../audit/deferred-issues.md)；T-GATE 脚本自测 34 项、真实性全量扫描 953 文件、配置边界 inventory 扫描 891 文件、中文注释 0 fail / 0 warn、`git diff --check` 均通过。
- PR3 剩余：FR-5 的完整 B0 降级 / 复现测试仍未关闭；不得把 FOLLOW-01 整卡写成 done。
- 代码 permalink：`engine/followup` 计划生成 + 异常回流 + B0 降级。
- 测试：计划复现 / 异常回院 / 回流 / 关模型 B0。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
