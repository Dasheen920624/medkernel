# AIK-STD-04 · 规则/路径/推荐/指标/随访候选生成（设计）

> 卡：[AIK-STD-04](../../cards/wave2/AIK-STD-04.md) · 上游：[AIK-STD-02](../../cards/wave2/AIK-STD-02.md)（带锚点来源片段）· 复用：[AIK-STD-01](../../cards/wave2/AIK-STD-01.md)（资产信封+校验闸）/ [AIK-STD-12](../../cards/wave2/AIK-STD-12.md)（专业资产模板）/ [AIK-STD-13](../../cards/wave2/AIK-STD-13.md)（生产编排 job+intake）。
> 设计日期：2026-06-16 · 阶段：第二阶段 P2-C 工厂流水线（内容管线中枢）。

## 1. 目标与边界

**目标**：从 AIK-STD-02 解析后的受控来源（带真实锚点的 `source_fragment`）**确定性（B0）生成规则/路径/推荐/指标/随访五类知识候选**，每候选带来源锚点、走既有版本/审核链，无模型即可跑。

**本卡边界（PR1 = 编排核心 + 类型无关生成器 + 全 5 类）**：
- 落「来源片段 → 模板桩候选 → 既有审核链」这条确定性主链路，覆盖 `RULE/PATHWAY/RECOMMENDATION/EVALUATION/FOLLOWUP` 五类。
- **非目标**：模型填充逻辑（P6 闸控）、11 项安全门禁（[AIK-STD-05](../../cards/wave2/AIK-STD-05.md)）、8 态去重分流（[AIK-STD-10](../../cards/wave2/AIK-STD-10.md)）、前端生产工作台 UI。

## 2. 关键核查结论（落卡前核既有 infra，避免重复建设）

| 维度 | 既有承载 | 结论 |
|---|---|---|
| 候选信封 + 校验闸 | `engine.factory.KnowledgeAssetEnvelope` + `KnowledgeAssetSchemaValidator`（AIK-STD-01） | 复用，生成器产此信封 |
| 候选 job + 审核链物化 | `mk_knowledge_production_job` + `KnowledgeCandidateIntake`/`submitCandidate`（AIK-STD-13） | 复用，**不新建 `generation_job` 表** |
| 专业资产模板骨架 | `ProfessionalAssetTemplateRegistry`（AIK-STD-12，5 类 structural 模板齐） | 复用作 B0 模板桩 |
| 带锚点来源片段 | `source_fragment` + `SourceFragmentRepository`（KNOW-01/AIK-STD-02） | 复用作生成输入与锚点 |
| 生产器分类 | `KnowledgeProducer.MANUAL`（javadoc 已定「确定性产物=B0 路径」） | 复用 MANUAL/B0，**不加枚举值、零迁移** |

**净新增**：`source_fragment → typed KnowledgeAssetEnvelope` 的确定性生成器 + 编排循环 + 1 个触发端点。**零新表、零迁移、零新权限码、零新域**。

## 3. 架构

新增子包 `com.medkernel.engine.knowledge.production.generation`（归 engine-knowledge，复用 `knowledge.write/read`）。生成器为**纯确定性 B0**，归 `KnowledgeProducer.MANUAL` 路径（`aiGenerated=false` 诚实——B0 模板桩本非 AI）。

```
AIK-STD-02 parse → source_version + source_fragment(锚点)
        │ sourceVersionId + [(assetType, target)…]   (jobCode 在路径)
        ▼
CandidateGenerationOrchestrationService.generate(jobCode, req)
        │ 逐 (assetType, target):
        ▼
SourceCandidateGenerator.generate(sourceVersionId, assetType, orgScope)
        │  取锚点片段 + 取 structural 模板骨架 → 组 payload(模板桩) + 绑 AssetSourceRef + 真 SHA-256
        ▼ KnowledgeAssetEnvelope(DRAFT)
        │  既有
        ▼
KnowledgeProductionOrchestrationService.submitCandidate(jobCode, envelope, target)
        │  → AIK-STD-01 校验闸 → §9 双形态隔离守卫 → PR3 会签路由 → KnowledgeCandidateIntake 物化
        ▼
既有版本/审核链 (PENDING_REPLACEMENT_REVIEW) + ReviewAssignment + 血缘
```

五类候选共用这一条路径（**类型无关**），差异只在「取哪张模板 + 枚举哪个 `VersionedAssetType`」。

## 4. 组件（单一职责）

### 4.1 `SourceCandidateGenerator`（新，B0 核心，类型无关）
- 入参：`sourceVersionId`、`VersionedAssetType assetType`、`String orgScope`。
- 出参：`KnowledgeAssetEnvelope`（`DRAFT`）。
- 逻辑：
  1. `SourceFragmentRepository.findByTenantIdAndSourceVersionIdOrderByAnchorPathAsc` 取该源版本全部带锚点片段；**空→诚实抛 `DocumentParse`-风格结构化错误（无源不生成，铁律 #1）**。
  2. `ProfessionalAssetTemplateRegistry.findByAssetTypeAndDomain(assetType, null)` 取 structural 模板骨架；缺模板诚实结构化报错（理论上 5 类皆有）。
  3. 组 **payload（JSON）**：模板各 `TemplateSection` 落为 payload 字段；其中「来源依据」聚合真实锚点摘要（`anchor_path` + `text_excerpt`，逐片段）；**逻辑/触发/动作等内容字段填诚实占位 `待编著`**（标记为模型/人工待填的槽位——见 §6）。
  4. 绑 `sources`：每锚点片段 → 1 条 `AssetSourceRef`（来源标识 + 权威级），`sources≥1`。
  5. `contentHash = SHA-256(payload)`（真实，过 AIK-STD-01 hash 一致性校验）。
  6. `lifecycleStatus = DRAFT`、`subject` 取源版本主题、`riskLevel` 保守默认、`trustLevel` 取来源权威级。

### 4.2 `CandidateGenerationOrchestrationService`（新，编排）
- `GenerationSummary generate(String jobCode, CandidateGenerationRequest req)`：
  - 对 `req.items()`（每项 = `assetType` + `MaterializationTarget target`）逐项调生成器 → 调既有 `submitCandidate(jobCode, envelope, target)`。
  - 汇总每类候选引用 + PR3 路由 + 计数；**某类型源片段为空 → 该类计 0 并在 summary 标明，不整批失败、不伪造候选**。
  - job 非法/终态 → 复用 AIK-STD-13 生命周期 409；跨租户/源不存在 → 既有 `notFound`。

### 4.3 `KnowledgeProductionController` 加 1 端点
- `POST /api/v1/engine/knowledge-production/jobs/{jobCode}/generate`（`knowledge.write`，`@Valid CandidateGenerationRequest`）→ `GenerationSummary`。挂既有控制器**零新治理面**（仿 AIK-STD-12）；产品功能目录重生成端点 +1。

### 4.4 DTO
- `CandidateGenerationRequest(Long sourceVersionId, @NotEmpty List<GenerationItem> items)`；`GenerationItem(@NotNull VersionedAssetType assetType, @NotNull @Valid MaterializationTarget target)`。
- `GenerationSummary(String jobCode, List<GeneratedCandidate> candidates, List<SkippedType> skipped)`；`GeneratedCandidate(VersionedAssetType, candidateRef, ReviewRoutingDecision)`；`SkippedType(VersionedAssetType, reason)`。

## 5. 数据契约
- **零新表、零迁移**：复用 `mk_knowledge_production_job`、`mk_knowledge_production_candidate`（血缘）、`source_*`、审核链既有表。
- domain 取自 job（AIK-STD-13 job 已带 `domain`），供 PR3 路由；structural 模板查找用 `domain=null`。

## 6. 诚实边界 / 铁律落点
- **无源不生成**（FR-4/铁律 #1）：源版本无 `source_fragment` → 该类型不产候选、诚实计 0。
- **不凭空造逻辑**（铁律 #1）：触发/动作/路径节点等逻辑字段一律占位 `待编著`，**B0 不填实质医学逻辑**。payload schema 的这些占位字段即「模型槽位」——未来 P6 闸控的模型步骤直接填它们；**PR1 不预建未使用的 enricher 接口**（最直接设计，不留未用件）。
- **候选态不入库**（FR-5）：`lifecycleStatus=DRAFT` → intake 落 `PENDING_REPLACEMENT_REVIEW`，审核确认才生效。
- **带锚点**（FR-4）：每候选 `sources≥1` 真实锚点。
- **P6 阻断**：模型填充逻辑受 P6 闸控，本卡不接真实模型；缺模型走 B0 模板桩诚实降级。

## 7. 错误处理
| 场景 | 处理 |
|---|---|
| 源版本不存在/跨租户 | 既有 `ApiException.notFound`（不泄漏存在性） |
| job 非法/终态 | 复用 AIK-STD-13 生命周期 409 |
| 模板缺失 | 诚实结构化报错，不产无模板候选 |
| 部分类型有源/部分无源 | 有源产候选、无源计 0 入 `skipped`，不整批失败 |
| 候选过不了 AIK-STD-01/§9 隔离 | 既有校验闸/隔离守卫结构化拒（4xx/422），不绕过 |

## 8. 测试（TDD 红绿）
- `SourceCandidateGeneratorTest`：模板桩绑锚点、逻辑字段占位不伪造、真 hash、无片段诚实拒收、5 类各产正确 `assetType`、`sources≥1`。
- `CandidateGenerationOrchestrationServiceTest`：多类型逐条喂 `submitCandidate`、汇总计数、无源诚实入 `skipped`、§9 隔离透传、job 终态拒。
- `KnowledgeProductionControllerSecurityTest` 增量：generate 端点 `knowledge.write` 鉴权 + 越权 403。
- `CandidateGenerationIntegrationTest`（真实 H2）：AIK-STD-02 解析 → 生成 → 候选真实落审核链端到端 + 血缘可查。
- 门禁：真实性/配置/中文注释 changed 全绿 + 五方言 Flyway smoke（无迁移）+ `git diff --check` + 前端 `productCatalog.test.ts`（端点 +1，重生成无漂移）+ 全量 `mvn test` 不回归。

## 9. 验收对齐（卡 AIK-STD-04）
- **AC-1（FR-1~3）**：五类候选可生成 → §4.1 类型无关生成器 + §8 各类型测试。
- **AC-2（FR-4/5）**：带锚点 + 候选态不入库 → §6 诚实边界。
- **B0 验收**：无模型时确定性模板出候选 → 模板桩主链路。
- **T-GATE**：后端真实性门禁全绿 → §8。

## 10. 续接（本卡之后）
- **AIK-STD-05**：11 项安全门禁 + 冲突仲裁，前置于本卡候选提审。
- **AIK-STD-10**：8 态身份识别/去重/分流。
- 模型填充逻辑：P6 闸齐备（文献库根地址 + 独立验收）后，经 LLM-08 gateway + LLM-03 出域闸 + LLM-07 评测闸接入，填 payload 占位字段。
