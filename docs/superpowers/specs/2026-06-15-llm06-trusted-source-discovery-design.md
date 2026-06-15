# LLM-06 可信来源探索编排 · 设计

> 卡片：[docs/cards/wave2/LLM-06.md](../../cards/wave2/LLM-06.md)。批次 P2-B。日期 2026-06-15。
> 权威读序：核心 CONSTITUTION §6/§7（权威与投影、来源可溯）· 铁律 #1（真实性，无源不出/禁臆造）· #4（B0 先于模型）· #5（AI 只产候选）。

## 1. 关键核查结论（写给下个 AI：别建重复表）

LLM-06 卡片预设要建 `knowledge_discovery_source`（受控源）+ `discovery_run`，但核查既有地基后**大半已成**：

| 卡片预设 | 既有地基 | 裁决 |
|---|---|---|
| `knowledge_discovery_source` 受控源表 | **`source_document`（KNOW-01）已是受控源注册表**：`source_code` / `source_type`（GUIDELINE/DRUG_LABEL/STANDARD/POLICY/HOSPITAL_PROTOCOL/TCM_CLASSIC/LITERATURE/CONSENSUS）/ `authority_level`（A–E）/ publisher / license / language。下挂 `source_version`（`content_hash` 真实核验 + `version_no`）+ `source_fragment`（`anchor_path` 引用锚点 + `text_excerpt` 正文 + `content_hash`） | **复用，不新建表** |
| `knowledge.discovery` 能力码 | V18 `model_capability_definition` + V127 `mk_llm_enhancement_matrix` 已登记 `ACTIVE`，B0 路径＝「确定性知识检索与关联基线」 | 复用 |
| 来源可信分级 | `SourceAuthorityLevel` A–E（rank 越小越可信）已建 | 复用 |
| 候选产出契约 | AIK-STD-01 `KnowledgeAssetEnvelope` + `AssetSourceRef` + `KnowledgeAssetSchemaValidator`（无源拒收 / 只产候选态 / 真实 hash） | 复用作产出 |

**真正新增（非重复）**：① 探索编排服务（受控源确定性检索 B0）；② `mk_knowledge_discovery_run` 检索时点存证表（FR-2「可复查当时看到什么」，现无）。

## 2. 边界决策（已定）

- **落点边界**：止于「校验候选 + 运行存证」。探索产出经 AIK-STD-01 校验闸通过的 DRAFT 候选信封 + 写 `discovery_run` 时点存证，**返回候选**，落候选池/审核队列交 **AIK-STD-13**（统一生产编排，pending）。不抢 AIK-STD-13 的活、不写权威库。
- **模型介入**：纯确定性 B0 检索（受控 `source_fragment.text_excerpt` 关键词匹配）。无模型即可跑、绝不臆造来源。模型增强排序留缝（走既有网关 + LLM-03 出域闸 + LLM-08 provider），**本卡不实现**（恒守 B0 先于模型 + P6 阻断）。
- **域归属**：新包 `com.medkernel.engine.knowledge.discovery`（读 KNOW-01 源注册表 + 产 AIK-STD-01 信封 + 用 `knowledge.*` 权限）→ 归 `engine-knowledge` 域。
- **权限**：复用 `knowledge.write`（探索＝产候选草稿，权限描述已含「新增知识候选草稿与来源登记」）+ `knowledge.read`（看运行台账）。**不新增权限码**（省两处策略测试维护）。

## 3. 组件设计（单一职责 + 可独立测试）

### 3.1 数据（唯一新表）`mk_knowledge_discovery_run`（V129 五方言 + 中文 COMMENT）

检索时点存证：每次探索记一行。

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | PK | |
| `tenant_id` | VARCHAR(64) NOT NULL | 租户隔离 |
| `run_code` | VARCHAR(64) NOT NULL | 业务键（UUID），唯一 |
| `query_text` | VARCHAR(512) NOT NULL | 探索查询词 |
| `capability_code` | VARCHAR(64) NOT NULL | 恒 `knowledge.discovery`（绑定能力码） |
| `executed_at` | TIMESTAMP NOT NULL | **检索时点**（FR-2） |
| `source_snapshot` | VARCHAR(4000) NOT NULL | **当时检索的源版本快照** JSON（`[{sourceCode,versionNo,versionContentHash}]`），复查「当时看到什么」 |
| `hit_count` | INTEGER NOT NULL | 命中受控片段数 |
| `candidate_count` | INTEGER NOT NULL | 产出候选数 |
| `result_hash` | VARCHAR(64) NOT NULL | 结果集 SHA-256（候选指纹有序拼接的真实 hash，B0 确定性可复算核验） |
| `status` | VARCHAR(16) NOT NULL | `SUCCEEDED` / `EMPTY` / `DEGRADED` |
| `degraded` | BOOLEAN NOT NULL | 上游不可用诚实标记 |
| `created_by` | VARCHAR(64) NULL | 执行人 |
| `created_at` | TIMESTAMP NOT NULL DEFAULT | |

- 约束：`uk_mk_knowledge_discovery_run_code UNIQUE(run_code)`；`ck_mk_knowledge_discovery_run_status CHECK(status IN (...))`。
- 索引：`idx_mk_knowledge_discovery_run_tenant (tenant_id, executed_at)`。
- **不复制 fragment 正文**：候选正文流向审核链（AIK-STD-13 落库），存证只留源版本快照 + result_hash，避免重复存储。

`DiscoveryRun` record 实体 + `DiscoveryRunRepository`（save / `findByTenantIdAndId` / `pageByTenantId` / `countByTenantId`）。

### 3.2 受控源检索 `ControlledSourceSearchRepository`（discovery 包，读 engine-knowledge 自有 source_* 表）

`@Query` 跨表 JOIN `source_fragment f → source_version v → source_document d`（强租户隔离），按关键词匹配 `LOWER(f.text_excerpt) LIKE`，返回投影 `DiscoveryFragmentHit`：

```
record DiscoveryFragmentHit(
    Long fragmentId, String anchorPath, String anchorLabel, String textExcerpt, String fragmentContentHash,
    Long sourceVersionId, String versionNo, String versionContentHash,
    Long sourceDocumentId, String sourceCode, String sourceTitle,
    SourceType sourceType, SourceAuthorityLevel authorityLevel)
```

`ORDER BY d.authority_level ASC, f.id ASC`（A_REGULATION…E_FEEDBACK 字母序＝权威 rank 序，高权威优先）+ `OFFSET/FETCH NEXT` 服务端分页。

### 3.3 编排服务 `DiscoveryOrchestrationService`

`explore(DiscoveryRequest)`：
1. 解析 tenant（`RequestContext.currentOrgScope().tenantId()`）+ orgScope + 执行人（`currentUserId()`）。
2. query 空白 → 结构化 400（`ApiException.BAD_REQUEST`）。
3. **FR-1** 仅检索已登记受控源（`ControlledSourceSearchRepository`，limit 默认 20 / 上限 50）；不开全网。
4. 上游检索异常 → **DEGRADED** run（空候选，`degraded=true`，诚实不伪装，铁律 #1）。
5. **FR-3** 每命中片段产 1 个候选 `KnowledgeAssetEnvelope`：
   - `assetType=KNOWLEDGE`，`assetIdentity=discovery:<sourceCode>:<versionNo>:<anchorPath>`，`subject=query + anchorLabel`，`versionLabel=run_code`。
   - `sources=[AssetSourceRef(sourceRef="<sourceCode>:<versionNo>:<anchorPath>", authorityLevel)]`（≥1，无源不出）。
   - `trustLevel=authorityLevel`，`riskLevel=KnowledgeRiskLevel`（默认中性级），`orgScope`，`payload=textExcerpt`，`contentHash=sha256(payload)` 真实，`lifecycleStatus=DRAFT`。
   - 经 `KnowledgeAssetSchemaValidator.validate()` 校验通过（证审核就绪）。
6. **FR-2** 写 `discovery_run`：query / capability_code / executed_at / source_snapshot（去重的源版本快照）/ hit_count / candidate_count / result_hash（候选 contentHash 有序拼接的 sha256）/ status / degraded / created_by。
7. **FR-5** 无受控匹配 → **EMPTY** run（空候选，`degraded=false`，诚实空态非报错非降级）。
8. 写审计事件（`EXECUTE mk_knowledge_discovery_run`，含 query / hit_count / result_hash）。
9. 返回 `DiscoveryResponse`。

`listRuns(PageRequest)`：`pageByTenantId` 返回运行台账（FR-2 复查）。

### 3.4 DTO

- `DiscoveryRequest(@NotBlank String query, Integer limit)`。
- `DiscoveryResponse(String runCode, Instant executedAt, String status, boolean degraded, int hitCount, int candidateCount, String resultHash, List<KnowledgeAssetEnvelope> candidates)`。
- `DiscoveryRunResponse`（台账行视图，从 `DiscoveryRun` 映射）。

### 3.5 控制器 `DiscoveryController`

- `@RestController @RequestMapping("/api/v1/engine/knowledge/discovery") @DataScope(requireTenant=true)`。
- `POST /api/v1/engine/knowledge/discovery:explore`（`@perm.has('knowledge.write')`）→ 运行探索，返回候选 + 运行存证。
- `GET /api/v1/engine/knowledge/discovery/runs`（`@perm.has('knowledge.read')`）→ 运行台账分页。

## 4. 配套登记

- **契约**：新增 `knowledge-discovery` 契约（controller / path / `permissions(knowledge.read, knowledge.write)` / `audits(audit(EXECUTE, "mk_knowledge_discovery_run", "运行可信来源探索并记录检索时点存证"))`）。
- **域归属**：`DomainOwnershipCatalog` engine-knowledge `tables(...)` 加 `mk_knowledge_discovery_run`。
- **迁移基线**：`MigrationBaselineContractTest` 加 V129、`mk_knowledge_discovery_run`、`idx_mk_knowledge_discovery_run_tenant`、两约束名；`H2BaselineMigrationTest` + `FlywayMultiDialectSmokeTest` 两处 `LATEST_MIGRATION_VERSION` 128→129。
- **产品功能目录**：新控制器须重生成 `product-function-catalog` 并跑前端 `productCatalog.test.ts`。

## 5. FR/AC 映射

| 条目 | 落点 |
|---|---|
| FR-1 受控源 | `ControlledSourceSearchRepository` 仅检索已登记 source_*，强租户隔离，不开全网 |
| FR-2 检索时点 | `mk_knowledge_discovery_run`：executed_at + source_snapshot + result_hash |
| FR-3 来源核验 | 每候选带 `AssetSourceRef`（锚点 + A–E）；经校验闸无源拒收 |
| FR-4 候选交付 | 产 DRAFT 候选信封返回交 AIK-STD-13，不写权威库 |
| FR-5 不臆造 | 无受控匹配诚实 EMPTY；上游不可用诚实 DEGRADED；来源恒为真实注册片段 |
| AC-1（FR-1~3） | 受控源检索 + 时点存证 + 来源核验 |
| AC-2（FR-4/5） | 候选校验就绪 + 无源诚实空态 |
| T-GATE | 四门禁（真实性/配置/迁移/中文注释）changed 全绿 |
| B0 验收 | 空库 / 无模型 / 无外网 → 诚实空态，不臆造引文 |

## 6. 验证清单

- TDD 红绿：`DiscoveryOrchestrationServiceTest`（命中产候选 / 空态 EMPTY / DEGRADED / result_hash 确定性 / 校验闸拒无源）+ `ControlledSourceSearchRepository` H2 集成测试（JOIN 投影 + 租户隔离 + 权威序）+ `DiscoveryControllerSecurityTest`（权限矩阵）+ `DiscoveryRunRepository` 测试。
- 全量 `mvn test` 不回归 + 四门禁 changed + `git diff --check` + 前端 `productCatalog.test.ts`。
- 合并 main 逐 PR 授权（用户手动合）。

## 7. 显式不做（YAGNI / 边界）

- 不建 `knowledge_discovery_source`（复用 `source_document`）。
- 不接模型排序 / 不接外网检索（B0 先于模型；外部源走 LLM-08/LLM-03，本卡留缝不实现）。
- 不落候选池 / 不写审核队列（AIK-STD-13 职责）。
- 不复制 fragment 正文入存证表（只留源版本快照 + result_hash）。
