# KNOW-01 · 知识资产引擎

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §8.4 知识资产统一元数据（L1667）· §8.5 标准化资产模型（L1693）· §8.2 来源资产范围与可信分级（L1582）· 落地规划 §6.1 总流程（L285）· 核心 §7 唯一权威知识 / 铁律 #4 B0 先于模型。

## 身份
- 卡 ID：KNOW-01（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S3 AI 知识工厂（B0 人工/确定性侧；AI 抽取生成后移第二波）
- 依赖卡：[SYS-03](../D0/SYS-03.md)（关系库权威 + 图/搜索投影）· [SYS-04](SYS-04.md)（版本框架）· [SYS-08](SYS-08.md)（权威版本约束/替换）· [BASE-04](../D0/BASE-04.md)（来源/审核审计）· [BASE-05](../D0/BASE-05.md)（5 方言）· [OPT-07](OPT-07.md)（来源证据分级）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供知识资产的**来源登记 → 解析 → 内容指纹 → 引用锚点 → 可信分级 → 资产身份/版本**的确定性引擎：把法规/指南/共识/院内/反馈来源沉淀为**可追溯、可引用、可版本化**的标准知识资产，**关系库为唯一权威**、图谱（Neo4j）/搜索仅为可重建投影。本卡是 **B0 人工/规则**侧；AI 自动抽取/生成（KNOWGEN-*）后移第二波。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/knowledge` **已实质建成**，本卡＝**契约化 + 补全（content_hash + 图投影一致 + 可信分级落地）**：
- 已有：`SourceDocument`/`SourceFragment`/`SourceVersion`(+`SourceType`/`SourceAuthorityLevel`/`SourceRegisterRequest`/`SourceVersionRegisterRequest`)、`Citation`/`CitationRelation`、`KnowledgeIdentity`/`KnowledgeAssetVersion`(+`KnowledgeDomain`/`KnowledgeRiskLevel`)、`KnowledgeIdentityService`/`Repository`、`V3__knowledge_asset_baseline` 五方言。
- 缺口（本卡补）：① 来源**内容指纹 `content_hash`** + 重复来源识别基座（去重工作流归 [KNOW-02](KNOW-02.md)）；② **引用锚点**精确到 SourceFragment 偏移；③ **可信分级**接 [OPT-07](OPT-07.md) 的 A/B/C/D/E + GRADE；④ **图/搜索投影**随资产发布失效或重建（关系库权威，无图可降级）。

## 功能要求（原子可测条目）
- [x] **FR-1 来源登记**：登记法规/指南/共识/文献/院内/反馈来源 → `SourceDocument` + `SourceVersion`（版本化、不可变、留原文/出处/发布机构/时点）。
- [x] **FR-2 解析 + 片段**：来源解析为 `SourceFragment`（章节/条款锚点 + 偏移），每片段可被引用。
- [x] **FR-3 内容指纹**：来源版本与资产版本生成 `content_hash`；同指纹重复登记**不产生新可发布候选**（去重判定基座，工作流在 [KNOW-02](KNOW-02.md)）。
- [x] **FR-4 引用锚点**：知识资产的每条断言**必须**绑定 ≥1 `Citation` → SourceFragment；无来源的断言不得标 `ACTIVE`（核心 §7 来源可溯）。
- [x] **FR-5 可信分级**：资产/来源按 [OPT-07](OPT-07.md) 分级（A 法规 > B 国家指南 > C 共识文献 > D 院内 > E 反馈），分级随资产存储、参与冲突仲裁与展示。
- [x] **FR-6 关系库权威 + 投影**：资产权威源在关系库；图谱/搜索为投影，资产发布/替换时**随之失效或重建**；**无图/无搜索可降级**（核心 §7 / 铁律 #5、SYS-03）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：本卡立**引擎能力**（来源登记/解析/指纹/引用/分级/投影），REST 客户面在 [API-03](API-03.md) 承接：`POST /sources`、`POST /sources/{id}/versions`、`GET /identities/{id}`、`GET /identities/{id}/citations`。
- DTO：复用 `SourceRegisterRequest`/`SourceVersionRegisterRequest`/`FragmentCreateRequest`/`DraftVersionCreateRequest`；新增 `ContentHash`（来源/资产指纹）· `CitationAnchor`（fragment + 偏移）。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：知识资产走核心 §3 配置类（草稿→待审核→已发布→生效中→已下线）；版本/替换状态机复用 [SYS-08](SYS-08.md)。
- 幂等 / 错误码 / traceId：来源登记按 `(source_type, external_id, version)` 幂等；无来源断言激活 → `KNOWLEDGE_CITATION_REQUIRED`；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。知识资产管理/来源追溯呈现在 **D6 来源追溯页**与 **D4 AI 知识审核页**；本卡供引擎。

## 数据与迁移
- 表族（已有 V3 基线 + 本卡补字段）：`source_document`/`source_fragment`/`source_version`(+`content_hash`)、`knowledge_identity`/`knowledge_asset_version`(+`content_hash`)、`citation`/`citation_relation`、`knowledge_lineage`。
- 主键 ULID；唯一约束：来源 `(source_type, external_id, version)`；索引：`content_hash`、`authority_level`、`knowledge_domain`、`org_path`。
- 组织字段：`tenant_id` + `org_path` + 审计字段（[BASE-04](../D0/BASE-04.md)）。
- 5 方言迁移：在现有 `V3__knowledge_asset_baseline` 上增量 `content_hash`/分级字段，h2/postgres/oracle/dm/kingbase 一致 + 中文注释。PR1 已补 `V49__knowledge_citation_anchor_offsets.sql`：`source_version(source_document_id, content_hash)` 去重约束、`citation.start_offset/end_offset` 精确偏移、非负与区间顺序校验、中文 COMMENT；PR2 已补 `V50__knowledge_trust_grading.sql`：来源 A/B/C/D/E 权威分级、`authority_basis`、资产版本 `authority_level`/`grade_quality`/`grade_strength`/`conflict_arbitration`、约束、索引与中文 COMMENT；PR3 已补 `V51__knowledge_projection_targets.sql`：扩展 `mk_projection_snapshot` / `mk_projection_sync` 投影目标约束，纳入 `KNOWLEDGE_GRAPH` 与 `KNOWLEDGE_SEARCH`，五方言一致并补中文 COMMENT。

## 视角清单（11 视角逐条）
1. **产品架构**：知识资产的**确定性沉淀层**（来源→片段→资产→引用），是 AI 工厂（第二波）与 D3 临床消费的料源。
2. **产品体验**：N·A —— 无页面；来源追溯/资产树在 D6/D4 页呈现。
3. **系统与数据架构**：★关系库权威 + content_hash + 图/搜索投影随发布失效重建；大资产列表走 [API-13](../D0/API-13.md) 服务端分页；P95 查询 ≤1s。
4. **临床医疗安全**：每断言必带来源引用，无来源不得生效；高危资产分级与替换走 [SYS-08](SYS-08.md)。
5. **知识与数据治理**：★主战场 —— 来源可溯、可信分级、content_hash、替代非覆盖（旧版留存，KNOW-02 工作流）。
6. **安全合规与监管**：来源/解析/引用/分级全留审计（[BASE-04](../D0/BASE-04.md)），证据可导出供监管。
7. **集团化与多租户治理**：资产按 `org_path` 归属，集团资产经 [SYS-04](SYS-04.md) 七层继承下发；跨租户不串。
8. **集成与互操作**：来源可来自外部知识库/适配器（[INTEG-01](INTEG-01.md)），导入不绕引擎直写。
9. **运维 / SRE / 国产化**：5 方言；图/搜索投影可重建；离线知识包导入（[PKG-01](PKG-01.md)）。
10. **质量与真实性审计**：content_hash 真实计算、无伪造来源/分级；无来源断言 CI 拒（铁律 #1）。
11. **AI / 模型治理与可降级**：★B0＝人工/规则登记与分级；AI 抽取/语义解析/自动发现（AIK-*/KNOWGEN-*）后移第二波，关模型本引擎登记/引用/分级行为不变。

## 适用不变量
- 命中核心约束：**§7 唯一权威知识 + 来源可溯** · **铁律 #5 关系库权威 / 投影可重建** · **铁律 #4 B0 先于模型** · **依赖 [SYS-04](SYS-04.md)/[SYS-08](SYS-08.md) 版本与替换**。
- 本卡落点：把来源→片段→资产→引用→分级做成**确定性、可溯、可投影**的知识料源，AI 增强在其上叠加而非取代。

## 验收 + 验证
- [x] **AC-1（FR-1/2/4）**：登记一份指南来源 → 解析出片段 → 创建资产并绑定 ≥1 引用 → 可激活；删去引用再激活 → `KNOWLEDGE_CITATION_REQUIRED`。
- [x] **AC-2（FR-3）**：重复登记同一来源版本（同 content_hash）→ 不新增可发布候选（去重基座；工作流 [KNOW-02](KNOW-02.md)）。
- [x] **AC-3（FR-5）**：A 法规与 D 院内对同一主题冲突 → 仲裁默认取高阶（[OPT-07](OPT-07.md)），分级随资产展示。
- [x] **AC-4（FR-6）**：资产发布 → 图/搜索投影更新；删图重建后查询一致；关图谱 → 引擎仍可登记/查询（降级）。
- 关联 A1–A9 剧本：A2 知识沉淀、A6 合规运维（来源证据导出）。
- T-GATE：前后端真实性门禁全绿（content_hash 真实、无伪造来源/分级）。
- B0 验收：纯人工/规则登记，**天然 B0**；关模型行为不变。

## 完工证据
- 代码 permalink：`SourceDocument/Fragment/Version` + `content_hash` + `Citation(Anchor)` + `knowledge_identity/asset_version` 分级字段 + 投影刷新挂点 + 5 方言增量迁移。
- 测试：来源登记/解析/引用必填测试 + content_hash 去重基座测试 + 分级仲裁测试 + 投影失效重建一致性测试 + 关图降级测试。PR1 证据已覆盖来源/片段/引用锚点、内容指纹、重复来源版本幂等、正文与外部 hash 一致性、无引用激活门禁、V49 五方言静态合同与 PostgreSQL/Oracle/H2 迁移烟测；PR2 证据已覆盖 A/B/C/D/E 来源分级、分级依据必填、GRADE 字段入参与资产版本快照、低阶覆盖高阶显式理由门禁、冲突裁决摘要、V50 五方言静态合同与 PostgreSQL/Oracle/H2 迁移烟测；PR3 证据已覆盖 `KNOWLEDGE_GRAPH` / `KNOWLEDGE_SEARCH` 可重建投影、资产发布后刷新端口、删投影后由关系库重建一致、图/搜索关闭 `NOT_SYNCED` 降级、V51 五方言静态合同与 PostgreSQL/Oracle/H2 迁移烟测。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## 大卡工序（6d，后端引擎；按 PR 拆分）
- PR1：content_hash + 来源/片段/引用锚点补全 + 5 方言增量迁移 → AC-1/2。2026-06-02 已通过 PR #255 合入 `origin/main`（merge `afd9be1d`）。
- PR2：可信分级接 [OPT-07](OPT-07.md) + 冲突仲裁基座 → AC-3。2026-06-02 已通过 PR #256 合入 `origin/main`（merge `416cce14`）。
- PR3：图/搜索投影刷新 + 关系库权威一致 + 降级 → AC-4。2026-06-02 已本地通过聚焦红绿套件与后端全量 `mvn -q test`，待 T-GATE、PR、CI 与合并。
