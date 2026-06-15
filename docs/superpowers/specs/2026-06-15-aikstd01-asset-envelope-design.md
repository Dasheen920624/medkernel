# AIK-STD-01 · 统一资产信封 schema + 校验闸 · 设计

> 上游卡：[AIK-STD-01](../../cards/wave2/AIK-STD-01.md)（来源与全类资产 schema + 统一元数据）。
> 母规范：核心 §6 唯一权威知识 · §7 来源可溯 · 铁律 #1 真实性（无源拒收）· 铁律 #5（AI 只产候选不产事实）。

## 背景与现状校正（核查 2026-06-15）

AIK-STD-01 卡（2026-05-31）假设「各引擎资产结构分散，需从零建统一骨架」。核查后**地基已实质建成**：

- `engine.versioning`：`AssetVersion`/`AssetVersionService`（port `VersionedAssetPort`，已发布版本不可原地改）+ `mk_version_asset_version` 统一版本注册表（asset_type / asset_identity / version_no / org_path / content_hash SHA-256 / status DRAFT→PUBLISHED→ACTIVE / source_ref / 安全·覆盖策略 / 继承），**仅版本元数据、无内容 payload**（内容在 per-engine 表，经 content_hash 关联）。
- `VersionedAssetType`：17 类统一枚举（KNOWLEDGE/TERMINOLOGY/RULE/PATHWAY/EVALUATION/FOLLOWUP/RECOMMENDATION/SAFETY/CDSS_RISK/VALUE_SET/FORMULA/ORDER_SET/ACTION_CARD/SUBPATHWAY/FIELD_CATALOG/PACKAGE/CONDITION_FRAGMENT），已含卡 FR-1 的 术语/规则/路径/推荐/指标(EVALUATION)/随访；护理/报告为 KNOWLEDGE 下的领域（`KnowledgeDomain` NURSING/REPORT）。
- KNOW-01/OPT-07（done）：来源登记/引用锚点/content_hash + 可信分级（`SourceAuthorityLevel` A_REGULATION…E_FEEDBACK）+ GRADE（`GradeEvidenceQuality`/`GradeRecommendationStrength`）。
- **AIK-STD-13 明确「不另起资产表（候选走既有版本/审核/替换链）」**。

故新建 `knowledge_asset` 表会**重复** `mk_version_asset_version` 并与 AIK-STD-13 冲突。**本卡正确落法 = 统一资产信封 schema + 校验闸**（无新表、无端点、无新权限、无迁移），给生产器一个真实可产出的统一契约。

## 目标

定义 AI 工厂全类资产的**统一产出信封**（生产器 ① API / ② Agent / ③ 本地模型 / ④ 人工 统一产出）+ **入库前 schema 校验闸**（不合格拒收）。复用既有枚举与版本注册表，不新建存储。

## 组件（新包 `com.medkernel.engine.factory`，X-AIK 域落脚，供 AIK-STD-13/14 同域）

### 1. `AssetSourceRef`（record）
来源/引用绑定：`sourceRef`（来源标识，非空）+ `authorityLevel`（`SourceAuthorityLevel`，非空）。

### 2. `KnowledgeAssetEnvelope`（record）
| 字段 | 类型 | FR |
|---|---|---|
| `assetType` | `VersionedAssetType`（复用） | FR-1 |
| `assetIdentity` / `subject` / `versionLabel` | String | 身份/主题/版本标签 |
| `sources` | `List<AssetSourceRef>` | FR-2 来源 + 引用 |
| `trustLevel` | `SourceAuthorityLevel`（复用 OPT-07） | FR-2 可信分级 |
| `gradeQuality` / `gradeStrength` | `GradeEvidenceQuality`/`GradeRecommendationStrength`（可空） | GRADE 兼容 |
| `riskLevel` | `KnowledgeRiskLevel`（复用） | 高危标风险 |
| `orgScope` | String | FR-2 org 作用域 |
| `contentHash` | String | FR-2 内容 SHA-256 指纹 |
| `payload` | String | 类型化内容（信封类型无关） |
| `lifecycleStatus` | `AssetVersionStatus`（复用） | FR-2 生命周期状态 |

构造紧凑式：`sources` 防御性 `List.copyOf`（null→空）。

### 3. `KnowledgeAssetSchemaValidator`（@Service）
`validate(KnowledgeAssetEnvelope)`：**收集全部违规**，非空则一次性抛结构化 `ApiException(BAD_REQUEST, "资产信封校验不合格：…")`（指明字段，不泄漏内部）。规则：
- `assetType` 非空（Java 枚举保证值合法，FR-1）。
- `assetIdentity` / `subject` / `orgScope` / `payload` / `versionLabel` 非空白。
- **`sources` 非空且 ≥1**，每条 `sourceRef` 非空白 + `authorityLevel` 非空 →「无源拒收」（铁律 #1 / KNOW-01 FR-4 先例）。
- `trustLevel` / `riskLevel` 非空。
- **`lifecycleStatus` ∈ {DRAFT, IN_REVIEW}**（铁律 #5「AI 只产候选不产事实」——禁生产器直接产 PUBLISHED/APPROVED/ACTIVE）。
- **`contentHash` 合 `^[0-9a-f]{64}$` 且 == 真实重算 `Sha256ContentHash.sha256(payload)`**（真实指纹，禁伪造，视角 10）。
- GRADE：可空；若任一存在不强制成对（弱约束，留扩展）。
- **FR-4 可扩展**：validator 不按 `assetType` 分支（类型无关），新增枚举值不改校验码、不破既有。

## 不做（诚实标 / 交下游）

- 不新建资产表、不碰 `mk_version_asset_version`（复用）；无迁移。
- 不建 REST 端点、不加权限码（生产器经 AIK-STD-13 在进程内调 validator；外部 Agent 经 AIK-STD-14 协议接入时再加端点/权限）。
- 不做 envelope→`AssetVersionRegisterCommand` 持久化映射（AIK-STD-13 编排候选 job 时落地，本卡只定 schema + 校验）。

## 测试（TDD 全矩阵）

`KnowledgeAssetSchemaValidatorTest`：
- 合法信封通过（无异常）。
- 逐拒收：缺 assetIdentity / 缺 subject / **sources 空（无源）** / source 缺 sourceRef / source 缺 authorityLevel / 缺 trustLevel / 缺 orgScope / 空 payload / 缺 riskLevel / **lifecycleStatus=PUBLISHED（铁律 #5）** / contentHash 格式错 / **contentHash 与 payload 不符**。
- 一次抛出**含多条违规**（收集全部）。
- **FR-4 扩展性**：对多种 `VersionedAssetType`（KNOWLEDGE/RULE/PATHWAY/RECOMMENDATION）同形信封均通过，证类型无关。

## 验收对照

- AC-1（FR-1/2）：全类资产按统一信封表达 + 元数据齐全（assetType 全类 + source/trust/org/hash/status）。
- AC-2（FR-3/4）：不合格拒收（无源/伪造 hash/越级状态…）；扩展新类型不破既有。
- B0：schema 与产出方式无关（B0 人工/规则产物同样过校验）。
- 真实性门禁全绿；卡现状更新为「地基已实质建成，本卡=统一信封 schema + 校验闸」，backlog 据实。

## 验证门禁（PR 前本地全跑）

全量 `mvn test`（新增 validator 测试 + 不回归）+ 四门禁（authenticity/config/migration/comment-zh，changed）+ `git diff --check`。无后端控制器/路由/迁移改动，**预期不触发**前端 productCatalog 与迁移契约测试漂移（仍跑一遍确认）。
