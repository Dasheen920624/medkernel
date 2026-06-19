# AIK-STD-05 · 候选安全校验与冲突仲裁（11 项门禁）设计

> 卡：[AIK-STD-05](../../cards/wave2/AIK-STD-05.md) · 校验对象＝[AIK-STD-04](../../cards/wave2/AIK-STD-04.md) 候选 · 复用 [OPT-04](../../cards/D3/OPT-04.md)（红线）+ [OPT-07](../../cards/D2/OPT-07.md)（来源仲裁）+ [AIK-STD-01](../../cards/wave2/AIK-STD-01.md)（信封校验）。
> 设计日期：2026-06-16 · 阶段：第二阶段 P2-C 工厂流水线（候选提审前的安全闸）。

## 1. 目标与边界

**目标**：生成候选**过 11 项安全门禁 + 冲突仲裁才可提审**：来源缺失/红线冲突/高危近似/剂量越界等任一不过即拦截、诚实报因、留痕可审计，不静默放行。

**本卡分寸**：门禁本身**确定性（B0），不依赖模型**（卡视角 11）。B0 模板桩候选逻辑字段留白，**深层临床逻辑校验（红线 logic/剂量数值）须逻辑在场**——逻辑填充受 P6 闸；门禁对留白逻辑诚实判「逻辑未填、深层校验待逻辑」而非伪造通过/拦截（铁律 #1）。

## 2. 关键核查结论（落卡前核既有 infra，第 5 次防重复）

| 门禁项 | 既有承载 | 结论 |
|---|---|---|
| 来源真实性/锚点/可信级/格式/审核要素 | [AIK-STD-01](../../cards/wave2/AIK-STD-01.md) `KnowledgeAssetSchemaValidator` + `SourceReferenceResolver` | 复用/拆为逐项门禁 |
| 红线不冲突/剂量边界/高危近似 | [OPT-04](../../cards/D3/OPT-04.md) `engine.safety.ClinicalRedlineService`/`ClinicalRedlineMatcher`（DDI/危急值/剂量/抗菌/禁忌类目） | 已接 ACTIVE 目录 readiness；逐条命中以候选 payload 具备结构化临床逻辑为前提。严格封闭、无医学逻辑的 B0 待编著结构候选允许先进入人工审核，解除基础知识与红线目录的启动环依赖；其他缺目录或缺逻辑场景诚实阻断 |
| 冲突仲裁/可信级仲裁 | [OPT-07](../../cards/D2/OPT-07.md) 来源 A–E 可信级 + 现行版本作用域 | 已接低阶覆盖高阶现行版本阻断；AIK-STD-10/09 已承接分流、替换与回滚链路 |
| 去重 | [AIK-STD-10](../../cards/wave2/AIK-STD-10.md)（8 态） | 已接生成期分流，重复候选落 `DUPLICATE/SKIP_DUPLICATE` |
| 门禁结果存储 | **无**（V108 是发布期质量闸摘要，非候选提审前逐项结果） | **新建 `mk_aik_gate_result`（V136）** |

**净新增**：门禁编排器 + 逐项门禁结果表 + 接入 AIK-STD-04 生成链（前置于提审）。

## 3. 架构

新增子包 `com.medkernel.engine.knowledge.production.gate`（归 engine-knowledge，复用 `knowledge.write/read`）。

```
AIK-STD-04 生成 envelope → CandidateSafetyGateService.evaluate(envelope, context)
        │ 跑有序 List<CandidateGate>：逐项 GateItemResult(门禁码, passed, reason)
        ▼ 持久化 mk_aik_gate_result（候选指纹/门禁码/通过/原因/时点）
GateOutcome(passed = 全项通过, items)
        │ passed=false → 拦截不提审，候选入 GenerationSummary.blocked（诚实报因）
        ▼ passed=true → 既有 submitCandidate（落审核链）
```

门禁链类型无关、确定性、可扩展（新增门禁不破框架）。

## 4. 组件（单一职责）

### 4.1 `CandidateGate`（接口）
- `GateItemResult evaluate(KnowledgeAssetEnvelope candidate, GateContext context)`；`String code()`（门禁稳定码）。

### 4.2 PR1 确定性信封门禁（@Component，纯函数无 I/O）
| 码 | 门禁 | 判定 |
|---|---|---|
| `SOURCE_PRESENT` | 来源真实性（结构层） | `sources≥1` 且每条 `sourceRef` 非空 |
| `ANCHOR_COMPLETE` | 锚点完整 | 每条 `sourceRef` 形如 `code:ver:anchor` 三段非空 |
| `AUTHORITY_LEVEL` | 可信级 | `trustLevel` 非空（A–E）；每条来源 `authorityLevel` 非空 |
| `CONTENT_FORMAT` | 格式 | `assetType`/`payload` 非空 + `contentHash` 64 位 hex 且 `== sha256(payload)` |
| `REVIEW_ELEMENTS` | 审核要素 | `lifecycleStatus∈{DRAFT,IN_REVIEW}` + `subject`/`versionLabel` 非空 |
| `APPLICABLE_SCOPE` | 适用域 | `orgScope` 非空 |

### 4.3 `CandidateSafetyGateService`（@Service，编排）
- `GateOutcome evaluate(KnowledgeAssetEnvelope candidate, GateContext context)`：注入 `List<CandidateGate>`，逐项跑 → 收集 `GateItemResult` → 持久化 `mk_aik_gate_result`（按候选 `contentHash` + job 关联）→ 返回 `GateOutcome(passed, items)`。**任一 FAIL → passed=false**。
- `GateContext(tenantId, jobCode, targetIdentityId)`：门禁上下文。`targetIdentityId` 供 PR2 权威冲突门禁定位现行版本；两参构造保留给无目标身份的测试/调用。

### 4.4 接入 AIK-STD-04（修改 `CandidateGenerationOrchestrationService`）
- 生成 envelope 后、`submitCandidate` 前调 `gateService.evaluate`；**FAIL → 不提审**，入新 `GenerationSummary.blocked: List<BlockedCandidate(assetType, gateItems)>`，诚实报因；PASS → 照旧 submit。

### 4.5 查询端点 + 实体
- `mk_aik_gate_result`（append-only：tenant/job_code/content_hash/gate_code/passed/reason/created_at）+ `AikGateResult` 实体 + repo。
- `GET /api/v1/engine/knowledge-production/jobs/{jobCode}/gate-results`（`knowledge.read`，挂既有控制器零新治理面）。

## 5. 数据契约
- **新表 `mk_aik_gate_result` V136 五方言**（append-only，非 mutable-audited）+ 中文 COMMENT；索引 `idx_mk_aik_gate_result_job`（tenant_id, job_code）。`LATEST_MIGRATION_VERSION` 135→136。
- 无新权限码（复用 `knowledge.read/write`）；归 engine-knowledge 域。

## 6. 诚实边界 / 铁律
- **不过不放行**（FR-4）：任一门禁 FAIL → 候选不提审、诚实报因，不静默绕过。
- **门禁可审计**（FR-5）：每候选每门禁项结果落 `mk_aik_gate_result` 可查。
- **确定性**（B0 验收）：门禁纯确定性，不依赖模型；关模型照常跑。
- **P6 分寸**：深层临床逻辑校验（红线 logic/剂量数值，PR2）须逻辑在场；B0 留白逻辑诚实标「待逻辑」不伪造深判。

## 7. 错误处理
| 场景 | 处理 |
|---|---|
| 门禁 FAIL | 候选不提审，结果落库 + summary.blocked 诚实报因 |
| job 不存在/跨租户 | 既有 `notFound` |
| 门禁项内部异常 | 该项判 FAIL（reason 含真实原因），不吞错放行 |

### 4.6 PR2 增量门禁（本地分支）
| 码 | 门禁 | 判定 |
|---|---|---|
| `SOURCE_LICENSE` | 来源许可 | `sourceRef` 必须可解析到受控来源，且来源登记 `license` 非空 |
| `CLINICAL_REDLINE` | 红线 readiness | 包含医学逻辑的候选要求 OPT-04 五类必需红线均有 ACTIVE 配置；空库/缺类目拒收。仅 `SourceCandidateGenerator` 产出的严格 B0 待编著结构候选可在目录未配置时进入人工编著审核 |
| `AUTHORITY_CONFLICT` | 权威冲突第一刀 | 指向已有身份时，低阶来源候选不得覆盖高阶来源现行版本；裸租户 ID 归一为 `tenant:<id>` 查作用域 |

## 8. 测试（TDD 红绿）
- 各门禁单测：6 项 pass/fail 用例（无源/锚点缺段/可信级空/hash 不符/非候选态/orgScope 空）。
- PR2 增量单测：来源许可缺失/不可解析、红线目录未配置/缺类目、低阶来源覆盖高阶现行版本。
- `CandidateSafetyGateServiceTest`：全过 → passed；任一 FAIL → blocked + 结果持久化。
- `CandidateGenerationOrchestrationServiceTest` 增量：FAIL 候选不 submit、入 blocked。
- 控制器安全：gate-results 端点 `knowledge.read`。
- 集成（真实 H2）：坏候选被拦不入审核链 + 结果可查；好候选过闸入链。
- 迁移：`MigrationBaselineContractTest`（V136/表/索引）+ H2BaselineMigration + 五方言 Flyway smoke。
- 门禁：真实性/配置/迁移/中文注释 changed + 产品目录重生成 + 前端 productCatalog。

## 9. 验收对齐（卡 AIK-STD-05）
- **AC-1（FR-1/2）**：11 项门禁逐条 + 红线拦截 → PR1 6 项确定性 + PR2 3 项增量（红线/剂量/高危 readiness、许可、权威冲突第一刀）；去重由 AIK-STD-10 生成期分流承接，结构化临床深判以候选 payload 具备逻辑为前提。
- **AC-2（FR-3/4）**：冲突仲裁 + 不过拦截诚实报因 → PR2 已覆盖低阶覆盖高阶阻断 + PR1 拦截框架。
- **B0 验收**：门禁确定性、不依赖模型 → PR1 主链路。

## 10. 续接
- **PR2 已落本地分支**：OPT-04 红线目录 readiness + 权威冲突第一刀 + 许可（源解析）门禁。
- **去重门禁**：AIK-STD-10（8 态）已接生成期分流与重复跳过。
- 升级态候选：AIK-STD-09 已接原子替换、影响处置任务与 `SUPERSEDED` 回滚。
