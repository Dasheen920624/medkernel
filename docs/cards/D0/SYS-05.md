# SYS-05 · 在线 / 异步 / 批量 / 离线运行框架

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D0 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §7.8 运行方式与降级（L1442）· 落地规划 §9 系统架构 · 核心 §10 集成（重试/死信/回放）/ §12 离线。

## 身份
- 卡 ID：SYS-05
- 域：D0 登录域 / 平台脊柱
- 关联场景：横切（四类运行模式底座）
- 依赖卡：[BASE-03](BASE-03.md)（幂等）· [OBS-01](OBS-01.md)（状态/追溯）· [BASE-04](BASE-04.md)（审计）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

交付**四类运行框架**：在线（同步）/ 异步（任务队列）/ 批量（大规模处理）/ 离线（本地包 + 本地执行器），含故障重试 + 死信 + 回放，使引擎能力在不同负载与网络形态下都可运行、可重试、可降级。

## 功能要求（原子可测条目）

- [x] **FR-1 在线模式**：同步请求执行（实时 CDSS/规则校验等），超时不阻断主流程（核心 §10）。PR1 已交付 `ONLINE` 模式，超时返回 `ESCALATED` + 诚实提示，不抛 5xx 阻断调用方。
- [x] **FR-2 异步模式**：任务队列 + 状态轮询（**待办类**状态机，核心 §3）；长任务异步化。PR1 已交付 `ASYNC` 入队为 `UNREAD`，通过 `/api/v1/system/tasks/{taskId}` 轮询状态。
- [x] **FR-3 批量模式**：大规模批处理 + 进度 + **部分成功**（成功数/失败数/失败明细/可重试，呼应六态部分成功态）。PR1 已交付 `BATCH` 结果计数、失败明细和可重试数量。
- [x] **FR-4 离线模式**：内网使用本地包、本地 payload 引用和本地执行器运行，无外网依赖主链路可用。当前产品不设置未经商业模型定义的许可服务闸门。
- [x] **FR-5 故障重试 + 死信 + 回放**：失败任务重试 → 死信队列 → 人工回放/补偿（核心 §10）。PR2 已交付 `retryTask`、`replayDeadLetter`、`sys_task_dead_letter` 和审计链路。
- [x] **FR-6 四模式诚实降级**：外部依赖断开时诚实状态（`NOT_CONNECTED`/`NOT_SYNCED`），不伪造完成（核心 §11/#18）。PR2 已交付 `NOT_CONNECTED` 终态，成功数保持 0、错误码可追踪。

## 接口契约 / 页面契约
### 接口契约
- 端点：任务提交/状态查询/重试/回放端点。
- DTO：任务提交 Record（模式 + payload 引用）+ 任务状态 Record。
- 响应信封：`ApiResult`；部分成功返回成功/失败明细。
- 状态机：异步/批量任务用**待办类**状态机（未读→处理中→已完成→已升级，核心 §3）。
- 幂等 / 错误码 / traceId：任务提交幂等键；重试幂等；`DEAD_LETTER`/`RETRY_EXHAUSTED`；traceId 贯穿（[OBS-01](OBS-01.md)）。

### 页面契约
N·A —— 任务/死信管理在 D6 开发者控制台 / D5 运维消费。

## 数据与迁移
- 表族：`sys_task`（任务）/ `sys_task_dead_letter`（死信）；payload 经 [OBS-01](OBS-01.md) `PayloadStoragePort`。
- 主键：ULID；索引：`status`、`mode`、`org_path`、`ts`。
- 组织字段：带 `tenant_id` + `org_path` + 审计字段。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：四类运行模式是引擎执行的统一承载；各能力按负载选模式，不各自造队列。
2. **产品体验**：批量部分成功态（成功/失败明细/可重试）对齐六态（核心 §16）。
3. **系统与数据架构**：★本卡主战场 —— 在线/异步/批量/离线 + 重试/死信/回放；高吞吐 + 故障韧性。
4. **临床医疗安全**：在线 CDSS 超时不阻断医生主流程（核心 §6/§10）；批量不自动执行医疗动作。
5. **知识与数据治理**：知识工厂批量生成/同步走批量模式（核心 §7，wave2 AIK 消费）。
6. **安全合规与监管**：任务/死信留审计（[BASE-04](BASE-04.md)）；离线数据不出院内边界。
7. **集团化与多租户治理**：任务带组织维隔离调度。
8. **集成与互操作**：★外部同步/回调走异步 + 重试 + 死信 + 回放（核心 §10）。
9. **运维 / SRE / 国产化**：★离线模式 + 国产化内网形态（核心 §12）；死信人工补偿运维闭环。
10. **质量与真实性审计**：★失败不伪造成功（禁 catch 吞错，核心 #18）；重试/死信真实。
11. **AI / 模型治理与可降级**：模型调用走异步 + 降级（模型断开 `MODEL_DISABLED` 诚实，核心 §11）。

## 适用不变量
- 命中核心约束：**§10 重试/死信/回放/超时不阻断** · **§12 离线模式** · **§11 诚实降级** · **§3 待办状态机** · **#18 失败不伪造**。
- 本卡落点：四类运行模式 + 故障韧性框架，让引擎能力在高负载/弱网/内网离线下都真实可运行、失败诚实可补偿。

## 验收 + 验证
- [x] **AC-1（FR-1）**：在线 CDSS 超时 → 主流程不阻断 + 诚实降级提示。PR1 `RuntimeTaskServiceTest.onlineTimeoutReturnsEscalatedWithoutThrowingAndAudits` 已红绿覆盖。
- [x] **AC-2（FR-2/3）**：长任务异步化可轮询；批量返回部分成功（成功/失败/可重试明细）。PR1 `RuntimeTaskServiceTest.asyncSubmitPersistsUnreadTaskAndStatusCanBePolled` / `batchPartialSuccessPersistsCountsAndRetryableFailures` 已红绿覆盖。
- [x] **AC-3（FR-4）**：断外网/离线形态下本地执行主链路可运行。PR2 `RuntimeTaskServiceTest.offlineModeRunsWithLocalExecutorAndNoExternalDependency` 已覆盖。
- [x] **AC-4（FR-5）**：失败任务重试耗尽进死信 → 人工回放成功。PR2 `RuntimeTaskServiceTest.retryExhaustionMovesTaskToDeadLetterAndReplayCreatesNewCompletedTask` 已覆盖。
- [x] **AC-5（FR-6）**：外部依赖断开任务返回 `NOT_CONNECTED`，不伪造完成。PR2 `RuntimeTaskServiceTest.notConnectedResultIsPersistedHonestlyWithoutSuccess` 已覆盖。
- 关联 A1–A9：A2 知识工厂（批量）、A6 合规运维（离线/降级）。
- T-GATE：后端门禁全绿（失败不伪造成功）。
- B0 验收：★离线 + 关闭模型/外部后四模式主链路真实通过。

## 完工证据
- 代码 permalink：任务框架 / 重试-死信-回放 / 离线运行 / `sys_task`+`sys_task_dead_letter` 迁移。
- 测试：异步轮询测试 + 批量部分成功测试 + 离线运行测试 + 死信回放测试 + 断连诚实降级测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

### PR1 证据（在线 / 异步 / 批量）
- 代码范围：`com.medkernel.shared.runtime.task` 运行任务框架、`/api/v1/system/tasks` 提交/查询端点、V41 `sys_task` 五方言迁移、服务契约和领域 owner。
- 测试：`RuntimeTaskServiceTest` 覆盖在线超时升级、异步入队轮询、批量部分成功、批量完成计数归一化；`RuntimeTaskMigrationContractTest` 覆盖 V41 五方言中文注释与关键索引。
- 已运行：`mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest,MigrationBaselineContractTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`；`mvn -B -q test`（Surefire：`tests=756 failures=0 errors=0 skipped=0`，含 PostgreSQL 15 + Oracle 21 Testcontainers 迁移至 V41）。
- T-GATE：提交后 changed 模式真实性 / 配置边界 / 迁移规约 / 中文注释 / 空白门禁均通过；`node --test scripts/migration-convention-guard.test.mjs` 5/5 通过。

### PR2 证据（离线 / 重试 / 死信 / 回放 / 断连诚实）
- 代码范围：`RuntimeTaskMode.OFFLINE`、`RuntimeTaskStatus.NOT_CONNECTED/DEAD_LETTER`、`RuntimeTaskService.retryTask`、`RuntimeTaskService.replayDeadLetter`、`/api/v1/system/tasks/{taskId}/retry`、`/api/v1/system/tasks/dead-letters/{deadLetterId}/replay`、V42 `sys_task` 重试字段与 `sys_task_dead_letter` 五方言迁移。
- 测试：`RuntimeTaskServiceTest` 覆盖离线本地执行、断连诚实终态、重试耗尽入死信与人工回放；`RuntimeTaskMigrationContractTest` 覆盖 V42 五方言、`OFFLINE`、`NOT_CONNECTED`、`DEAD_LETTER` 和中文注释；`MigrationBaselineContractTest` 覆盖 V42 权威序列、表/索引/约束一致性。
- 已运行：`mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest test`；`mvn -B -q -Dtest=RuntimeTaskServiceTest,RuntimeTaskMigrationContractTest,MigrationBaselineContractTest,H2BaselineMigrationTest,DomainOwnershipContractTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test`（H2 已应用 V1–V42 且重复 migrate 为 0）；`mvn -B -q -Dtest=FlywayMultiDialectSmokeTest,MigrationBaselineContractTest,RuntimeTaskMigrationContractTest test`（PostgreSQL / H2 / Oracle 均迁移至 V42 且重复 migrate 为 0）；`mvn -B -q test`（Surefire：`tests=760 failures=0 errors=0 skipped=0`，含 PostgreSQL 15 + Oracle 21 Testcontainers 迁移至 V42）。
- 迁移修复证据：首次全量暴露 Oracle `ORA-01408` 重复索引问题，根因为 `uk_sys_task_dead_task (tenant_id, task_id)` 已隐式建索引；已将五方言 `idx_sys_task_dead_task` 调整为 `(task_id, tenant_id)`，避免冗余索引并保留按任务 ID 查死信能力。
- T-GATE：`node --test scripts/authenticity-guard.test.mjs` 20/20 pass；真实性 changed 扫描 12 个文件无阻断项；`node --test scripts/config-boundary-guard.test.mjs` 2/2 pass；配置边界 changed 扫描 12 个 Java 文件无阻断项；`node --test scripts/migration-convention-guard.test.mjs` 6/6 pass；迁移规约 changed 扫描 5 个 V42 文件无阻断项；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check origin/main...HEAD` 通过。

## 大卡工序（4d，后端）
- PR1：在线/异步/批量模式 + 待办状态机 + 部分成功 → AC-1/2（#226 已合入）。
- PR2：离线模式 + 重试/死信/回放 + 诚实降级 → AC-3/4/5（本地红绿、聚焦契约、后端全量、PostgreSQL / Oracle V42 迁移与本地 T-GATE 已通过，待 PR/CI）。
