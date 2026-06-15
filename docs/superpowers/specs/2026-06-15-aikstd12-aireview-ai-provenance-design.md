# AIK-STD-12 设计 · AiReview 审核台接 AI 生成候选（来源溯源 + 标识 + 全专业模板）

> 卡：[AIK-STD-12](../../cards/wave2/AIK-STD-12.md)（7d 大卡，前端重）。前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 _brief](../../cards/wave2/_brief.md)。
> 续接从最新 `origin/main` 起。本卡多 PR 分期。

## 1. 关键核查（写给下个 AI：地基已成熟，勿重复建审核台）

核查 2026-06-15（`frontend/src` + `engine.knowledge` 为准）：

- **审核台后端已完整**：`KnowledgeVersionController`（`com.medkernel.engine.knowledge`）已有全套审核 API——`GET /review-queue`、`GET /identities/{id}/candidates`、`GET /candidates/{id}/diff`、`POST /candidates/{id}/review`、版本 `submit/activate/withdraw`。审核链 `KnowledgeVersionService`/`CandidateClassification`/`ReviewAssignment`/8 态去重（KNOW-02）均成熟（AIREVIEW-01 已 done）。
- **前端审核台页已真实化**：卡称 `pages/quality/AiReview.tsx`，**实际为 `pages/quality/KnowledgeGovernance.tsx`**（消费 `useKnowledgeIdentities`/review-queue/`useKnowledgeCandidates`/`useKnowledgeCandidateDiff`/`useReviewKnowledgeCandidate`，路由 `/aik/review`）。AIREVIEW-01 显式**未含 AI 生成来源标识**（AI 生成留 wave2）。
- **AI 候选已能真实落审核链**：AIK-STD-13 PR4 物化（`MaterializingCandidateIntake`）使生产候选真实落 `KnowledgeAssetVersion(PENDING_REPLACEMENT_REVIEW)` + `CandidateClassification` + 路由 `ReviewAssignment`；`submitCandidate` 把物化版本引用 `kv:{identityId}:{versionNo}` 写回 `mk_knowledge_production_candidate.candidate_ref`，并连 `mk_knowledge_production_job`（producer/pipeline/model_strategy/domain）。

**结论**：AIK-STD-12 **不重建审核台**，而是在成熟审核台上补三件事——① AI 生成**来源溯源 + 标识**（接生产血缘）；② 前端 AI 标识 + 署名 + 退修动作；③ 全专业标准资产模板（FR-1）。

## 2. 数据链接点（已存在，PR1 仅反查）

```
审核候选(KnowledgeAssetVersion: identityId+versionNo)
        │  确定性键 kv:{identityId}:{versionNo}
        ▼
mk_knowledge_production_candidate.candidate_ref ── job_code ──► mk_knowledge_production_job
                                                                 (producer / target_pipeline / model_strategy / domain)
```

- 生产候选行 ⇒ 该候选**经 AI 工厂生产**；其 job 的 `producer`（`API_MODEL`/`AGENT_TOOL`/`LOCAL_MODEL`/`MANUAL`）决定 **aiGenerated = producer ≠ MANUAL**。
- 直接经版本控制器手建的版本（非经生产 job）**无血缘行** ⇒ 诚实标为「非工厂候选」（无来源溯源），不臆造 AI 标识（铁律 #1）。

## 3. PR 切片（多 PR 大卡）

### PR1（本切片，纯后端，最可 TDD）：AI 生产来源溯源接入审核台读模型
- `KnowledgeProductionCandidateRepository` 加**反查** `findByTenantIdAndCandidateRefIn(tenant, refs)`（强租户隔离）。
- 新 `CandidateProvenanceService`（@Service，`com.medkernel.engine.knowledge.production`，只读）：给定一组候选版本引用 → join 血缘行 + job → 产 `CandidateProvenanceView`（`candidateRef` / `aiGenerated` / `producer` / `jobCode` / `targetPipeline` / `modelStrategy` / `domain` / `riskLevel` / `producedAt` / `producedBy`）；无血缘行的引用诚实**不返回**（前端据缺省判「非工厂候选」）。
- 新只读端点 `POST .../knowledge-production/candidates/provenance`（body=候选引用列表，复用 `knowledge.read`；旁挂查询，**不改既有 `KnowledgeCandidateResponse`** ⇒ 零前端破坏 / 零现有契约漂移）。
- 强隔离：仅返回当前租户血缘；跨租户引用静默不命中（不泄漏存在性）。
- **诚实分寸**：门禁/评测结果（FR-2「门禁结果」）若 AIK-STD-01 校验闸结论未持久化，PR1 **不臆造**，仅返回已落库的生产来源；门禁结果展示留 PR2/后续按真实落库字段补。

### PR2（前端）：AiReview/KnowledgeGovernance 接 AI 候选审核展示
- `KnowledgeGovernance.tsx` 候选列表/详情接 PR1 provenance：**AI 标识徽标**（aiGenerated）+ 来源（job/producer/pipeline）+ 审核人署名留痕（复用既有 review 动作的 actor）+ **退修**动作（若后端 review decision 已支持 RETURN/退修态则接，否则随后端补）。样式走 BASE-10 token，禁硬编码。
- no-page-mock 真实性门禁：无假审/假发、AI 标识来源真实。

### PR3（全专业资产模板，FR-1）
- 术语/规则/路径/推荐/指标/随访/护理/报告/中医/医保等标准资产模板注册（按既有 `VersionedAssetType` + domain 维度，**不新建资产类型**）。形态待 PR3 细化（模板＝结构化建议骨架，非新表优先）。

## 4. 恒守红线
- **B0**：无 AI 时审核台纯人工审/发照常可用（AIREVIEW-01 壳），provenance 仅为旁挂增强，缺生产血缘不阻断人工审。
- **P6 阻断**：本卡不触发真实知识生产（生产侧受 P6，AIK-STD-13 FR-2 阻断）；审核台消费的是已落审核链的候选，不开生产。
- **铁律 #1/#3/#6**：AI 标识来源真实不臆造；AI 内容明显标识 + 审核人署名；审过才发走替换链。
- TDD 红绿 + 四门禁 changed + 五方言（PR1 无新迁移）+ 前端 productCatalog（PR1 新增端点须重生成）+ 合并 main 逐 PR 授权。
