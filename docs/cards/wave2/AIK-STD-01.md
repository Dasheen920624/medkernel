# AIK-STD-01 · 来源与全类资产 schema + 统一元数据

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [wave2 域简报](_brief.md)。
> 迁移来源：详规 §8 AI 工厂 · backlog 第二波 X-AIK · 核心 §6 唯一权威知识。

## 身份
- 卡 ID：AIK-STD-01（= backlog `AIK-STD-01`）
- 域：wave2（X-AIK）
- 关联场景：S3 AI 知识工厂、S15
- 依赖卡：[KNOW-01](../D2/KNOW-01.md)（来源登记）· [KNOW-02](../D2/KNOW-02.md)（版本）· [OPT-07](../D2/OPT-07.md)（来源分级）
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
定义 AI 工厂**全类知识资产的统一 schema + 元数据骨架**：术语/规则/路径/推荐/指标/随访/护理/报告等所有资产共用一套结构与元数据，后续 AIK 卡产出皆遵此。

## 现状（核查 2026-06-15，地基已实质建成）
**统一资产骨架已建**（卡 2026-05-31 口径过时）：① `engine.versioning` `AssetVersion`/`AssetVersionService`（port `VersionedAssetPort`，已发布不可原地改）+ `mk_version_asset_version` 统一版本注册表（asset_type/identity/version/org_path/content_hash SHA-256/status/source_ref/安全·覆盖·继承，**仅元数据无 payload**，内容在 per-engine 表经 hash 关联）；② `VersionedAssetType` 17 类（已含术语/规则/路径/推荐/指标(EVALUATION)/随访，护理/报告为 KNOWLEDGE 下领域）；③ KNOW-01/OPT-07（done）来源/引用/hash + 可信分级 A–E + GRADE。**AIK-STD-13 明确「不另起资产表（候选走既有链）」**。故本卡 ≠ 新建 `knowledge_asset` 表（会重复 `mk_version_asset_version`），**＝统一资产信封 schema + 校验闸**（生产器统一产出契约，入既有链前校验；无新表/端点/权限/迁移）。

## 功能要求（原子可测条目）
- [x] FR-1 资产类型枚举：覆盖术语/规则/路径/推荐/指标/随访/护理/报告/床旁/医保等全类（复用 `VersionedAssetType` 17 类；护理/报告为 KNOWLEDGE 领域，床旁/医保非独立资产类型）。
- [x] FR-2 统一元数据：来源 + 版本 + 可信分级（[OPT-07](../D2/OPT-07.md)）+ org 作用域 + 内容 hash + 生命周期状态（`KnowledgeAssetEnvelope` 信封字段，复用既有枚举）。
- [x] FR-3 schema 校验：资产入库前过 `KnowledgeAssetSchemaValidator` 校验闸，不合格拒收（无源/缺元数据/越级状态/伪造 hash），收集全部违规一次性结构化抛出。
- [x] FR-4 可扩展：校验类型无关，新增 `VersionedAssetType` 不改校验码、不破既有。

## 接口 / 数据契约
- **不新建表**（复用 `mk_version_asset_version` + per-engine + KNOW-01/OPT-07）；`KnowledgeAssetEnvelope`（信封 DTO）+ `AssetSourceRef`（来源绑定）+ `KnowledgeAssetSchemaValidator`（校验闸），包 `com.medkernel.engine.factory`（X-AIK 域，供 AIK-STD-13/14 同域）。持久化映射（信封→既有版本/审核/替换链）由 [AIK-STD-13](AIK-STD-13.md) 编排候选 job 时落地。

## 视角清单（11 视角）
1. 产品架构：AI 工厂全类资产的统一数据骨架。 2. 产品体验：N·A（资产在审核台 [AIK-STD-12](AIK-STD-12.md) 呈现）。 3. 系统与数据架构：资产表按 type+org 索引。 4. 临床医疗安全：高危资产元数据标风险级。 5. 知识与数据治理：★统一元数据 = 治理基础（可信级/版本/状态）。 6. 安全合规与监管：资产变更审计。 7. 集团化与多租户治理：元数据含 org 作用域、可继承。 8. 集成与互操作：schema 兼容 FHIR/标准资产。 9. 运维/SRE/国产化：N·A。 10. 质量与真实性审计：★资产必带真实来源 + hash，禁无源资产。 11. AI/模型治理与可降级：schema 与产出方式无关（B0/模型同结构）。

## 适用不变量
- 命中核心约束：**核心 §6 唯一权威知识** · **铁律 #1 真实性**（无源拒收）· **核心 §9 组织继承**。
- 本卡落点：全类资产统一 schema + 元数据，治理/版本/可信级有结构承载。

## 验收 + 验证
- [x] AC-1（FR-1/2）：全类资产可按统一信封表达 + 元数据齐全（assetType 全类 + source/trust/org/hash/status）；「登记」经既有链由 AIK-STD-13 落地。
- [x] AC-2（FR-3/4）：不合格拒收（无源/伪造 hash/越级状态…）；扩展新类型类型无关不破既有。
- T-GATE：后端真实性门禁全绿。
- B0 验收：★schema 与产出方式无关，B0 人工/规则产物同样过校验。

## 完工证据
- 代码 permalink：`engine.factory` `KnowledgeAssetEnvelope` + `AssetSourceRef` + `KnowledgeAssetSchemaValidator`（无新表，复用 versioning + KNOW-01/OPT-07）。
- 测试：`KnowledgeAssetSchemaValidatorTest`(15：合法 + 逐拒收〔无源/缺元数据/越级状态/hash 格式/hash 不符〕+ 多违规一次抛 + 跨类型扩展)。
- 设计：[`docs/superpowers/specs/2026-06-15-aikstd01-asset-envelope-design.md`](../../superpowers/specs/2026-06-15-aikstd01-asset-envelope-design.md)。

## 实现进度（2026-06-15，已落地）
- **已实现 = 统一资产信封 schema + 校验闸**（PR 待提）：`KnowledgeAssetEnvelope`（assetType `VersionedAssetType` + assetIdentity/subject/versionLabel + sources `List<AssetSourceRef>` + trustLevel `SourceAuthorityLevel` + GRADE + riskLevel + orgScope + contentHash + payload + lifecycleStatus `AssetVersionStatus`）；`KnowledgeAssetSchemaValidator`（FR-3/4：无源拒收〔铁律 #1〕/ 生命周期须候选态 DRAFT·IN_REVIEW〔铁律 #5 只产候选〕/ contentHash 须 SHA-256 格式且真实等于 hash(payload)〔禁伪造，视角 10〕/ 全违规一次结构化抛 BAD_REQUEST / 类型无关可扩展）。**无新表/端点/权限/迁移**；持久化映射交 AIK-STD-13。
- **诚实分寸**：FR/AC 机制达成（信封 + 校验）；「资产登记落库」走既有版本/审核/替换链，由 AIK-STD-13 编排候选 job 时接入（本卡只定 schema + 校验，不重复 `mk_version_asset_version`）。验证：`mvn test` `KnowledgeAssetSchemaValidatorTest` 15 绿 + 四门禁。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
