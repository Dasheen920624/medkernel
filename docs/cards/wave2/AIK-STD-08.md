# AIK-STD-08 · 最新知识探索、差异检测与过期治理

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8.2.1/8.2.2 知识探索 · backlog 第二波 X-AIK · 铁律 #1。

## 身份
- 卡 ID：AIK-STD-08（= backlog `AIK-STD-08`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[LLM-06](LLM-06.md)（来源探索编排）· [KNOW-02](../D2/KNOW-02.md)（版本/差异）· [AIK-STD-09](AIK-STD-09.md)（替换处置）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**最新知识探索 + 差异检测 + 过期治理**：探索受控来源的更新，与现行权威差异检测，过期资产标记并触发复核，不自动替换。

## 现状（核查 2026-06-16）
已接 [LLM-06](LLM-06.md) 来源探索编排：`DiscoveryRequest.targetIdentityId` 绑定现行知识身份后，候选进入 `KnowledgeDiffDetectionService` 做 B0 确定性比对；新增 `knowledge_diff` + `expiry_task` 五方言基线。差异/过期只留台账和复核任务，不自动替换权威版本；同指纹无更新返回诚实空态，若现行版本超期则只建过期复核任务，不伪造修订差异。

## 功能要求（原子可测条目）
- [x] FR-1 探索：触发式探索受控来源更新（接 [LLM-06](LLM-06.md)）。
- [x] FR-2 差异检测：新内容 vs 现行权威差异（新增/修订/废止）。
- [x] FR-3 过期标记：来源废止/版本超期资产触发复核任务。
- [x] FR-4 不自动替换：差异/过期只标记 + 提候选，替换走 [AIK-STD-09](AIK-STD-09.md) 审核。
- [x] FR-5 诚实：无更新诚实空态，不臆造差异。

## 接口 / 数据契约
- `knowledge_diff`（现行/新/差异类型）+ `expiry_task`（过期资产/复核），五方言。
- `POST /api/v1/engine/knowledge/discovery:explore` 请求可带 `targetIdentityId`；响应 `diffs[]` 返回差异检测结果。

## 视角清单（11 视角）
1. 产品架构：知识保鲜层。 2. 产品体验：N·A（差异在审核台）。 3. 系统与数据架构：探索/检测异步。 4. 临床医疗安全：过期高危资产优先复核。 5. 知识与数据治理：★差异 + 过期治理 = 知识不腐。 6. 安全合规与监管：探索/差异留痕。 7. 集团化与多租户治理：按 org。 8. 集成与互操作：受控外部源经 [LLM-03](LLM-03.md)。 9. 运维/SRE/国产化：无外网仅内置源。 10. 质量与真实性审计：★不臆造差异、无更新诚实空。 11. AI/模型治理与可降级：B0 走规则比对。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性** · **#6 唯一权威**（不自动替换）· **核心 §6 过期治理**。
- 本卡落点：探索 + 差异检测 + 过期标记触发复核，不自动替换、诚实。

## 验收 + 验证
- [x] AC-1（FR-1/2）：探索 + 差异检测正确。
- [x] AC-2（FR-3/4/5）：过期标记 + 复核任务；不自动替换；无更新诚实。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★规则比对差异（不依赖模型）。

## 完工证据
- 代码 permalink：本地分支 `codex/knowledge-fullflow-audit-production`，待本地提交。
- 测试：`KnowledgeDiffDetectionServiceTest`、`DiscoveryOrchestrationServiceTest`、`MigrationBaselineContractTest`、`H2BaselineMigrationTest#h2AppliesCompleteAuthoritativeBaselineMigrations`。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
