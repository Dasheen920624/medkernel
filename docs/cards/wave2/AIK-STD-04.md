# AIK-STD-04 · 规则/路径/推荐/指标/随访候选生成

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8.6 生成流水线 · backlog 第二波 X-AIK · 铁律 #4 B0 先于模型。

## 身份
- 卡 ID：AIK-STD-04（= backlog `AIK-STD-04`）
- 域：wave2（X-AIK）
- 关联场景：S3、S5、S6、S11、S12
- 依赖卡：[RULE-01](../D2/RULE-01.md)/[PATH-01](../D2/PATH-01.md)/[CDSS-01](../D3/CDSS-01.md)/[EVAL-01](../D4/EVAL-01.md)/[FOLLOW-01](../D3/FOLLOW-01.md)（各引擎草稿能力）· [AIK-STD-02](AIK-STD-02.md)（来源）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
从解析后的来源**生成规则/路径/推荐/指标/随访候选**：调各引擎草稿能力产候选，带来源锚点、入审核链，无模型走确定性模板。

## 现状（核查 2026-05-31，不深调研）
承载＝各引擎已建（RULE-01/PATH-01/CDSS-01/EVAL-01/FOLLOW-01）+ [LLM-01](LLM-01.md) 能力码 `rule.draft`/`pathway.draft`（当前 B0 模板）。本卡＝**新建生成编排**（来源 → 各类候选），复用引擎草稿能力。

## 功能要求（原子可测条目）
- [x] FR-1 规则候选：从来源条款生成规则草稿（B0 模板桩，触发/动作章节留白待编著/待模型；逻辑填充受 P6 闸）。
- [x] FR-2 路径候选：生成路径草稿（准入/分支/阶段/退出结构骨架，同 B0 模板桩）。
- [x] FR-3 推荐/指标/随访候选：复用类型无关模板桩生成器（推荐/指标/随访结构模板齐）。
- [x] FR-4 带锚点：每候选 `sources≥1` 真实锚点（`sourceCode:versionNo:anchorPath`，[AIK-STD-02](AIK-STD-02.md)）；无源诚实跳过不生成。
- [x] FR-5 候选不入库：一律 `DRAFT`、经既有 [AIK-STD-13](AIK-STD-13.md) intake 落 `PENDING_REPLACEMENT_REVIEW` 审核链，不直接执行。

## 实现进度（PR1，B0 模板桩生成）
- 新包 `engine.knowledge.production.generation`：`SourceCandidateGenerator`（类型无关 B0 模板桩，复用 [AIK-STD-12](AIK-STD-12.md) `ProfessionalAssetTemplateRegistry` + 锚点绑定 + 真 SHA-256，逻辑字段留白不伪造）+ `CandidateGenerationOrchestrationService`（逐资产类型建 `MANUAL` 生产 job → 既有 `submitCandidate`，无源诚实跳过）+ `POST .../knowledge-production/generate`（`knowledge.write`）。
- **复用 [AIK-STD-13](AIK-STD-13.md) job+intake / [AIK-STD-12](AIK-STD-12.md) 模板 / [AIK-STD-01](AIK-STD-01.md) 信封，零新表零迁移零新权限码**。
- **B0 分寸 / P6 阻断**：模型填充逻辑（触发/动作实质内容）受 P6 闸控，本卡只产带锚点的 DRAFT 模板桩候选；缺模型走确定性模板桩诚实降级。
- 续接：**[AIK-STD-05](AIK-STD-05.md)** 11 项门禁（校验本卡候选）/ **[AIK-STD-10](AIK-STD-10.md)** 8 态去重分流。

## 接口 / 数据契约
- 经 [API-12](API-12.md) 能力码调用各引擎草稿；`generation_job`（来源/类型/候选/状态），五方言。

## 视角清单（11 视角）
1. 产品架构：知识候选生成编排。 2. 产品体验：N·A（候选在审核台）。 3. 系统与数据架构：批量异步生成。 4. 临床医疗安全：候选审过 + 医师确认才用；过 [AIK-STD-05](AIK-STD-05.md) 门禁。 5. 知识与数据治理：★候选走版本/审核/替换链，不直接成权威。 6. 安全合规与监管：生成留痕。 7. 集团化与多租户治理：候选按 org。 8. 集成与互操作：复用引擎契约。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：★无源不生成、不伪造候选。 11. AI/模型治理与可降级：★模型生成，无模型走确定性模板（B0）。

## 适用不变量
- 命中核心约束：**铁律 #4 B0 先于模型** · **#5 关系库权威**（候选不入权威）· **#1 真实性**。
- 本卡落点：来源→各类候选生成 + 带锚点 + 入审核链，复用引擎、不直接执行。

## 验收 + 验证
- [x] AC-1（FR-1~3）：五类候选可生成（`SourceCandidateGeneratorTest` 五类各产 + 集成端到端）。
- [x] AC-2（FR-4/5）：带锚点；候选态不入库（集成 `PENDING_REPLACEMENT_REVIEW` + 锚点串引用）。
- T-GATE：后端真实性门禁全绿（authenticity/config/comment-zh changed 全过）。
- B0 验收：★无模型时确定性模板出候选（模板桩主链路，不依赖模型）。

## 完工证据
- 代码 permalink：生成编排 + 各引擎接入。
- 测试：五类候选 / 锚点 / 候选态。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
