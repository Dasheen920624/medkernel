# 平台优先知识继承与统一分发底座（platform-first-knowledge-inheritance）

## Why

系统的设计原则是 **平台版本权威优先**：所有医疗知识、字典、规则、路径默认以平台发布的版本为准；租户只有在需要时才以 **副本修改 / 新增独有** 的方式覆盖，覆盖版本可声明 **下级复用还是独有**；平台版本始终保持统一权威性。绝不为每个租户预先同步一份（会导致后续治理混乱）。

但当前实现与该原则存在结构性偏差：

1. **无平台权威层**。`AssetVersion`、各引擎表均为 `tenant_id` 平铺，没有"高于租户的平台基线版本"概念。`StandardTerm` 也带 `tenant_id`，不存在平台级标准。
2. **继承底座已建但未接线**。`InheritanceResolver`（`findAncestorsAndSelf` 单租户内组织闭包解析，**无平台层、无传播、无维度、无 policy**）、`InheritanceOverride`（REPLACE/DISABLE）、`VersionedAssetPort`、`VersionReleaseService`（含 activation/replay/rollback/contentHash）**已存在**，但 **没有任何引擎实现 `VersionedAssetPort`，没有任何运行路径调用 `InheritanceResolver.resolve()`**。继承解析是死代码。
3. **版本机制四套并行**：`rule/RuleVersion`、`knowledge/KnowledgeVersion`+`SourceVersion`、`terminology` 自有发布、`pathway` 自有发布，各自为政，无法统一解析/回滚/重放。
4. **分发容器三套并行**：`pkg/KnowledgePackage`、`terminology/TermMappingPackage(+Release)`、`pathway/SpecialtyPackage`，三套打包/发布逻辑互不相通。
5. **覆盖缺"复用 vs 独有"语义**。`InheritanceOverride` 有 `orgPath`+`mode`，但没有"传播到下级 / 仅本节点独有"的声明，无法表达"分院定制可被其下卫生院复用，或仅分院独有"。
6. **租户开通是预同步思路**。`PilotPackageTemplate` 倾向实例化副本，与"引用平台、按需覆盖、不预同步"相悖。
7. **字段目录（context 域）也是 tenant 平铺**（本轮新建），未纳入平台优先继承，需一并归一。

若不统一，平台权威无法真正下发、租户定制无法受控继承、多层级（集团总院/分院/卫生院/科室）关系无法全链路打通。

## What Changes

引入 **统一资产版本与分发底座**，把上述并行机制收敛到 `engine/versioning` + `engine/pkg`，并叠加平台优先继承语义：

- **平台权威层**：新增"平台空间（PLATFORM）"作为高于所有租户的权威基线持有者。所有资产以平台 `AssetVersion`（`asset_identity` 唯一）为默认权威；租户/机构默认 **按身份引用**，不复制。
- **Copy-on-write 覆盖层**：租户/机构仅对需要改动的资产创建 `InheritanceOverride`（REPLACE 定制 / DISABLE 停用）或 **新增独有资产**（ADD，新 identity）。为 `InheritanceOverride` 增加 **传播语义 `propagation = INHERITABLE(复用) | EXCLUSIVE(独有)`**。
- **惰性解析**：`InheritanceResolver` 升级为"平台基线 → 组织闭包覆盖链（最具体优先、尊重传播）"的有效版本解析；运行期（ClinicalEvent 分发、规则/路径/字典/字段目录读取）按就诊机构实时解析，**不预同步、不落副本**。
- **统一版本底座**：`rule/knowledge/terminology/pathway/context-field/evaluation/followup` 各域通过 **`VersionedAssetPort` 适配器** 注册版本，统一走 `VersionReleaseService` 的 release/activation/replay/rollback/contentHash。四套并行版本机制改为底座之上的薄适配。
- **统一分发容器**：`KnowledgePackage` 成为 **唯一** 平台→租户权威分发载体；`TermMappingPackage`、`SpecialtyPackage` 收敛为知识包的专域视图/子集（保留兼容外观）。`PackageItem` 引用统一 `asset_identity`+`version_no`。
- **平台优先的包解析**：租户看到的"有效知识包"= 平台包基线 + 本组织闭包覆盖增量（lazy 合成）。`SyncTarget`/离线导入下发的是 **解析后的有效包**，但权威源仍是平台 + 覆盖增量。
- **租户开通改为引用制**：开通仅授予对平台包的引用与覆盖能力，不实例化副本。
- **治理与权限分离**：平台版本由平台管理员发布；租户/机构覆盖由租户管理员发布；高风险覆盖（安全红线/给药/剂量）走评审。
- **影响与差异**：平台版本更新自动惠及未覆盖的租户/机构；对已覆盖者产生"上游已变更"影响信号 + 继承差异视图。

本变更聚焦 **架构设计与分阶段落地蓝图**（先设计后实现），不在本 PR 落代码。

## Impact

- Affected specs（新增能力）：`platform-authority`、`copy-on-write-inheritance`、`inheritance-resolution`、`unified-asset-versioning`、`unified-package-distribution`、`tenant-onboarding-reference`
- Affected code（落地阶段，本 PR 不改）：
  - `engine/versioning/*`（AssetVersion 增平台层；InheritanceOverride 增 propagation；Resolver 升级；Port 落实现）
  - `engine/pkg/*`（KnowledgePackage 收敛为统一容器；有效包解析；SyncTarget 下发解析结果）
  - `engine/rule|knowledge|terminology|pathway|context|evaluation|followup/*`（各出一个 VersionedAssetPort 适配器；读路径改走解析）
  - `engine/clinical|cdss|cdshook/*`（运行期按机构解析有效资产集）
  - `engine/tenant/*`（开通改引用制）
  - `engine/security/*`（平台/租户/机构发布权限分离）
  - Flyway 迁移（平台层列、propagation 列、identity 归一），多方言
- 治理门禁：ServiceContract/Migration/DomainOwnership/guard-rules 在落地阶段逐项登记
- 兼容：分阶段，旧引擎读路径以"平台基线缺失→回退本租户版本"honest degradation 平滑迁移
