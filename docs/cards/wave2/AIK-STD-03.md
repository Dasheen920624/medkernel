# AIK-STD-03 · 术语编码与院内映射流水线

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8 术语流水线 · backlog 第二波 X-AIK · 铁律 #3 医疗安全。

## 身份
- 卡 ID：AIK-STD-03（= backlog `AIK-STD-03`）
- 域：wave2（X-AIK）
- 关联场景：S3、S4 字典映射
- 依赖卡：[TERM-01](../D2/TERM-01.md)（字典映射引擎 + 高危近似判别）· [AIK-STD-01](AIK-STD-01.md)
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
**术语编码与院内映射的自动流水线**：批量把术语映射到标准编码 + 院内字典候选，高危近似走判别器、产候选交审核，不自动确认。

## 现状（核查 2026-06-17）
承载＝D2 [TERM-01](../D2/TERM-01.md) 字典映射引擎已建，本卡不重造判别器。当前闭环：
- 后端 `TerminologyCandidateGenerationJob` + `mk_term_candidate_generation_job` 已提供异步批量生成任务；候选明细写入 `mapping_candidate`，用 `generation_job_code` 分页追溯。
- `POST /api/v1/engine/terminology/mappings/candidates` 只返回任务，不同步返回大批量候选；`GET /mappings/candidate-generation-jobs/{jobCode}` 查询任务状态；`GET /mappings/candidates?generationJobCode=...` 进入审核队列。
- 生成逻辑复用 TERM-01：精确编码、同义词/缩写、编码族确定性匹配；高危近似由 `mk_term_high_risk_rule` 判别并强制 `HIGH`。
- 前端 `/terminology/mapping` 已补「生成候选」入口和最近任务状态追踪，可从术语工作台提交来源系统、阈值和语义辅助开关，再进入候选审核/冲突裁决/包发布。

## 功能要求（原子可测条目）
- [x] FR-1 批量映射：批量术语 → 标准编码候选（带置信/多候选）。
- [x] FR-2 院内映射：标准码 → 院内字典候选。
- [x] FR-3 高危拦截：钾/钠、左/右、剂量量级等高危近似走 [TERM-01](../D2/TERM-01.md) 判别器强制 HIGH，**禁批量自动确认**。
- [x] FR-4 候选交审核：映射候选入审核链（[KNOW-02](../D2/KNOW-02.md)），人工确认才生效。
- [x] FR-5 无模型确定性：规则/同义词表可出基线候选（B0）。

## 接口 / 数据契约
- 复用 TERM-01 映射表 + `mk_term_candidate_generation_job`（批次/候选/状态），五方言。
- 客户面入口：`POST /api/v1/engine/terminology/mappings/candidates`、`GET /api/v1/engine/terminology/mappings/candidate-generation-jobs/{jobCode}`、`GET /api/v1/engine/terminology/mappings/candidates?generationJobCode=...`。

## 视角清单（11 视角）
1. 产品架构：术语标准化自动化层。 2. 产品体验：N·A（候选在审核台）。 3. 系统与数据架构：批量异步。 4. 临床医疗安全：★高危近似禁批量自动确认（钾钠/左右/剂量）。 5. 知识与数据治理：候选走审核去重链。 6. 安全合规与监管：映射变更审计。 7. 集团化与多租户治理：院内字典按 org。 8. 集成与互操作：标准编码（ICD/LOINC/本位码）兼容。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：★不伪造映射置信、低置信不自动过。 11. AI/模型治理与可降级：模型增强映射，B0 走规则/同义词表。

## 适用不变量
- 命中核心约束：**铁律 #3 医疗安全**（高危近似）· **#4 B0** · **核心 §6 审核后生效**。
- 本卡落点：批量映射产候选 + 高危判别器拦截 + 审核后生效，不自动确认。

## 验收 + 验证
- [x] AC-1（FR-1/2）：批量映射出候选。
- [x] AC-2（FR-3/4）：高危近似 HIGH 不批量过；候选入审核。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★无模型时规则/同义词表出基线候选。

## 完工证据
- 代码 permalink：`TerminologyService#generateCandidates` / `executeCandidateGenerationJob` / `generateCandidateRowsForJob`、`TerminologyController` 候选生成与任务查询、`TerminologyMapping` 页面生成入口。
- 测试：`TerminologyServiceTest` 批量/分页/高危拦截/审核分流，`TerminologyApiContractTest` API-04 任务入口，`TerminologyMapping.test.tsx` 前端生成任务与追踪。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
