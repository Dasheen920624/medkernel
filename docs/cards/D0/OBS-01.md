# OBS-01 · 引擎可观测性骨干

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5 引擎服务 API 与页面嵌入统一规范（L170）· §7.8 运行方式与降级 · 落地规划 §9 系统架构。

## 身份
- 卡 ID：OBS-01
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（所有引擎执行的可观测底座）
- 依赖卡：[BASE-03](BASE-03.md)（ErrorCode/traceId 同源）· [BASE-04](BASE-04.md)（审计）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**引擎可观测性骨干**：TraceIdPropagator + MDC + StateTransitionRecorder + PayloadStoragePort + ErrorCode + DiagnoseResponse，使任何引擎执行（规则/路径/CDSS/质控…）的输入、版本、状态流转、输出、耗时全程可追溯、可诊断。

## 功能要求（原子可测条目）

- [x] **FR-1 TraceIdPropagator + MDC**：全链路 traceId 生成/透传/回显（同步 + 异步 + 批量），与 [BASE-03](BASE-03.md) 同源，日志 MDC 注入。
- [x] **FR-2 StateTransitionRecorder**：4 套状态机（核心 §3）流转统一留痕（from→to + who + 原因 + ts），供回溯与审计。
- [x] **FR-3 PayloadStoragePort**：大 payload（规则输入/路径上下文/模型输入输出）经存储抽象落地，不裸存于业务表；可按 traceId 取回。
- [x] **FR-4 ErrorCode 统一字典**：引擎错误码集中枚举（与 BASE-03 同源），分类业务/校验/降级/系统。
- [x] **FR-5 DiagnoseResponse**：引擎执行返回可诊断响应（命中规则/版本/耗时/降级原因），专家模式可见、客户面隐藏（核心 §14）。
- [x] **FR-6 执行可追溯**：给定 traceId 可还原一次引擎执行的输入引用、规则版本、输出引用、耗时和状态流转。

## 接口契约 / 页面契约
### 接口契约
- 端点：`GET /api/v1/engine/diagnose/traces/{traceId}`，按 traceId 取执行轨迹，权限为 `system.read` 或 `audit.read`，供 D6 开发者控制台 / D3 提醒治理消费。
- DTO：`DiagnoseResponse` Record；`TraceDiagnoseResponse` Record；状态流转与 payload 摘要 DTO。
- 响应信封：`ApiResult` / `ProblemDetail`。
- 状态机：N·A —— 本卡记录状态流转，不新增资产状态机。
- 幂等 / 错误码 / traceId：★本卡是 traceId 与 ErrorCode 的骨干提供者。

### 页面契约
N·A —— 诊断数据由 D6 开发者控制台 / D3 临床提醒治理（专家模式）消费。

## 数据与迁移
- 表族：`mk_obs_state_transition`（状态流转）；`mk_obs_payload_store`（大 payload 元数据 + INLINE Base64 / URI 引用）。
- 主键：两表均为 `BIGINT` identity；`mk_obs_payload_store.payload_id` 为业务唯一 ID；索引：`trace_id`、`entity_type/entity_id`、`tenant_id + occurred_at/created_at`。
- 组织字段：带 `tenant_id` + `org_path`；审计字段齐全。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释；当前真实运行保障范围为 PostgreSQL + Oracle，达梦 / 人大金仓真实环境适配登记为 [DEFER-001](../../audit/deferred-issues.md)，不写成已通过。

## 视角清单（11 视角逐条）
1. **产品架构**：可观测骨干是所有引擎的统一追溯底座；禁各引擎自造日志当追溯。
2. **产品体验**：诊断信息默认隐藏（专家模式可见，核心 §14），客户面不暴露 trace/DSL。
3. **系统与数据架构**：★本卡主战场 —— traceId 传播 + 状态流转 + payload 存储 + 诊断响应；高吞吐异步留痕不阻塞。
4. **临床医疗安全**：CDSS/规则执行可追溯到"为何提醒/命中哪条规则版本"，支撑可信解释（核心 §6）。
5. **知识与数据治理**：执行命中的知识版本可追溯（呼应唯一权威知识，核心 §7）。
6. **安全合规与监管**：payload 脱敏存储；诊断不泄敏（核心 §8）。
7. **集团化与多租户治理**：可观测数据带组织维，可按院/科下钻。
8. **集成与互操作**：外部调用 traceId 贯穿（核心 §10）。
9. **运维 / SRE / 国产化**：诊断 + 状态流转支撑故障定位；指标与 [BASE-07](BASE-07.md) 监控同源。
10. **质量与真实性审计**：★诊断响应真实（禁伪造命中/耗时，核心 #18）；执行可追溯是反假闭环的证据基。
11. **AI / 模型治理与可降级**：模型输入输出经 PayloadStoragePort 留痕（可重放/审计，核心 §11）；降级原因进 DiagnoseResponse。

## 适用不变量
- 命中核心约束：**§13 真实性（可追溯）** · **§3 状态机（流转留痕）** · **§14 诊断藏专家模式** · **§11 降级可观测**。
- 本卡落点：traceId + 状态流转 + payload 存储 + 诊断响应四件套，让引擎执行从黑盒变可追溯白盒，是上层所有引擎卡的可观测前提。

## 验收 + 验证
- [x] **AC-1（FR-1）**：一次跨同步+异步的引擎执行，traceId 全链一致可串联。
- [x] **AC-2（FR-2）**：状态机流转产 `mk_obs_state_transition` 记录（from→to+原因+组织+创建人）。
- [x] **AC-3（FR-3/6）**：给定 traceId 取回执行轨迹（状态流转 + payload 摘要 / 取数入口 + 耗时），payload 明文不出现在客户面。
- [x] **AC-4（FR-5）**：DiagnoseResponse 含命中规则版本/耗时/降级原因；客户面默认不可见。
- [x] **AC-5（FR-4）**：引擎错误码集中且与 BASE-03 一致。
- 关联 A1–A9：A3 临床运行（推荐可解释追溯）。
- T-GATE：后端门禁全绿（诊断/耗时不伪造）。
- B0 验收：纯确定性可观测，天然 B0。

## 完工证据
- 代码 permalink：`TraceIdPropagator` / `StateTransitionRecorder` / `DbPayloadStorage` / `PayloadStoragePort` / `ErrorCode` / `DiagnoseResponse` / `TraceDiagnoseResponse` / 迁移。
- 测试：traceId 异步传播测试 + 状态流转留痕测试 + payload 取回测试 + 诊断响应测试 + trace 诊断端点权限测试。
- 本地证据：
  - `mvn -B -q -Dtest=MigrationBaselineContractTest,DbPayloadStorageTest,StateTransitionHistoryRepositoryTest,StateTransitionRecorderTest,DiagnoseResponseAssemblerTest,ObservabilityDiagnoseServiceTest,ObservabilityDiagnoseControllerTest,TraceIdPropagatorTest,MdcEnrichmentFilterTest,RuleEngineServiceTest#diagnoseAssemblesFromExecutionLog,ContextSnapshotTraceEndToEndTest test`
  - `mvn -B -q test`（含 H2 / PostgreSQL / Oracle 迁移验证；达梦 / 人大金仓真实运行见 DEFER-001）
  - `mvn -B -q clean test`（清空旧报告后重新验证，确认删除旧 in-memory 测试后无残留报告）
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（3d，后端）
- PR1：TraceIdPropagator + MDC + ErrorCode + 状态流转记录 → AC-1/2/5。
- PR2：PayloadStoragePort + DiagnoseResponse + 执行追溯端点 → AC-3/4。
