# AIK-STD-11 · 待审新版共存与替换提醒

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8 待审共存 · backlog 第二波 X-AIK · 铁律 #6 唯一权威知识。

## 身份
- 卡 ID：AIK-STD-11（= backlog `AIK-STD-11`）
- 域：wave2（X-AIK）
- 关联场景：S3、S15
- 依赖卡：[KNOW-02](../D2/KNOW-02.md)/[SYS-08](../D2/SYS-08.md)（版本/替换）· [AIK-STD-10](AIK-STD-10.md)（分流）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**待审新版与现行权威共存 + 替换提醒**：待审新版只可审不可执行（铁律 #6），与现行对比共存，审过提醒替换。

## 现状（核查 2026-06-16）
承载＝D2 [KNOW-02](../D2/KNOW-02.md) 待审 + [SYS-08](../D2/SYS-08.md) 唯一有效约束已建。本卡＝**新建共存视图 + 替换提醒**。

2026-06-16 本地进展（`codex/wave2-knowledge-model-readiness`）：新增后端 B0 共存读模型
`CandidateCoexistenceService` + `CandidateCoexistenceView`，按 `kv:{identityId}:{versionNo}` 候选引用返回：
待审候选摘要、现行 `ACTIVE` 摘要、`CandidateClassification.diffSummary`、生产血缘、`candidateExecutable=false`、
`activeExecutable=true/false` 与替换提醒。该读模型只读、不发布、不调用模型，非 `PENDING_REPLACEMENT_REVIEW`
引用拒绝伪装成共存态。前端左右对照/差异高亮仍留后续生产中心收尾。

## 功能要求（原子可测条目）
- [x] FR-1 只审不执行：待审新版不进执行链（仅现行 `ACTIVE` 执行）。后端共存视图固定 `candidateExecutable=false`。
- [ ] FR-2 共存对比：待审新版 vs 现行权威并列对比（差异高亮）。后端已提供并列摘要与 diff；前端高亮待补。
- [ ] FR-3 替换提醒：审过待审版生成替换提醒（接 [AIK-STD-09](AIK-STD-09.md)）。后端已提供审核前替换提醒；审后通知/任务化提醒待补。
- [x] FR-4 不旁路：待审版禁任何旁路执行（铁律 #6）。共存端点仅接受 `PENDING_REPLACEMENT_REVIEW`，其它状态拒绝伪装成待审共存。

## 接口 / 数据契约
- 复用 KNOW-02 待审表 + 对比视图查询，五方言。
- 后端读模型：`GET /api/v1/engine/knowledge-production/candidates/coexistence?candidateRef=kv:{identityId}:{versionNo}`（`knowledge.read`）。

## 视角清单（11 视角）
1. 产品架构：待审/现行共存治理。 2. 产品体验：共存对比在审核台。 3. 系统与数据架构：N·A。 4. 临床医疗安全：待审不执行、临床只用现行。 5. 知识与数据治理：★唯一执行版本（核心 §6）。 6. 安全合规与监管：审核留痕。 7. 集团化与多租户治理：按 org。 8. 集成与互操作：N·A。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：★待审不旁路执行。 11. AI/模型治理与可降级：与产出方式无关。

## 适用不变量
- 命中核心约束：**铁律 #6 唯一权威知识**（待审只审不执行）· **#5 关系库权威**。
- 本卡落点：待审新版共存只审不执行 + 替换提醒，禁旁路。

## 验收 + 验证
- [x] AC-1（FR-1/4）：待审不执行、不旁路。证据：`CandidateCoexistenceServiceTest.pendingCandidateShowsActiveVersionAndBlocksCandidateExecution`、`nonPendingCandidateCannotBePresentedAsCoexistence`。
- [ ] AC-2（FR-2/3）：共存对比 + 替换提醒。后端 B0 已有；前端左右高亮与审后任务化提醒待补。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★共存机制确定性。

## 完工证据
- 代码 permalink：共存视图 + 替换提醒。
- 测试：只审不执行 / 共存对比 / 提醒 / 不旁路。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
