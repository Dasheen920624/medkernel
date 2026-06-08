# 设计：平台优先知识继承与统一分发底座

> 本文是权威架构蓝图，供 AI 团队分阶段实现。所有类型/表名均与现有代码对齐，落地时直接对号入座。

## 1. 设计原则（不可妥协）

1. **平台权威优先**：知识/字典/规则/路径/字段目录/评估/随访的默认权威 = 平台发布版本。
2. **引用而非复制**：租户/机构默认按 `asset_identity` 引用平台版本；**绝不为每租户预同步副本**。
3. **Copy-on-write**：仅"需要改"的资产才产生覆盖（定制/停用/新增独有），其余永远跟随平台。
4. **传播可声明**：每个覆盖声明"下级复用（INHERITABLE）"或"仅本节点独有（EXCLUSIVE）"。
5. **惰性解析**：有效版本在读取/运行期按机构实时解析合成，不落地中间副本。
6. **统一底座**：一套版本（version）、一套发布（release/activation/replay/rollback）、一套分发（package）、一套解析（inheritance）。各域只出薄适配，不再各造轮子。
7. **诚实降级**：平台基线缺失/解析失败时显式回退并标注，绝不静默伪造。

## 2. 四层概念模型

```
身份 Identity   asset_identity（跨平台/租户/机构稳定，覆盖按身份"遮蔽"平台）
   └─ 版本 Version   AssetVersion（不可变内容快照 + content_hash + 生效期）
        └─ 归属 Scope   权威层 PLATFORM ↑ 租户组织树（org_path，层级可变，附录 O）
        └─ 维度 Dimension  专病/场景/人群/角色（applicable_scope，与组织正交，附录 O）
   分发 Package   KnowledgePackage（一组 asset_identity@version 的权威束 + 发布/分发/回滚）
   覆盖 Override   InheritanceOverride（REPLACE/DISABLE/ADD + propagation + org_path）
```

- **身份**：一条规则"成人房颤 CHA₂DS₂-VASc 抗凝建议"在平台、REGION、FACILITY 看到的都是同一 `asset_identity`；机构定制是同身份的 override，而非新对象。
- **版本**：内容不可变快照，`content_hash` 保证可重放（复用现有 `VersionContentHash`/`VersionReplayService`）。
- **归属**：版本挂在平台权威层或某 `org_path`（组织树，层级可变可跳级，见附录 O）；横切维度（专病等）经 `applicable_scope` 表达，与组织正交。
- **包**：平台以知识包发布一束资产版本，作为下发与回滚的原子单位。
- **覆盖**：租户/机构对身份的增量声明。

## 3. 平台权威层

### 3.1 平台空间
- 新增保留作用域 **PLATFORM**（高于所有租户）。实现选型（待决项 D1，倾向 a）：
  - (a) 保留租户号 `__platform__` + 顶层 `org_path='/'`，复用现有 `tenant_id`/`org_path` 列，迁移最小；
  - (b) `AssetVersion` 增 `scope_tier`（PLATFORM/TENANT）列，语义更显式。
- 平台版本：`AssetVersion{ scope=PLATFORM, asset_identity, version_no, active_scope_key, content_hash }`，由平台管理员发布、激活。
- **唯一权威读法**：任意租户/机构对某身份的解析，**起点恒为平台 ACTIVE 版本**，再叠加覆盖。

### 3.2 权威保持
- 平台版本更新（发新 version 并激活）后，**所有未覆盖该身份的租户/机构自动获得新版本**（因为它们只引用身份，不存副本）。
- 已覆盖者保持其覆盖，但收到"上游平台已变更"影响信号（见 §11），可选择重新基线（rebase）。

## 4. Copy-on-write 覆盖层

### 4.1 覆盖模式（扩展 `InheritanceOverrideMode`）
| 模式 | 语义 | override_version_id |
|---|---|---|
| REPLACE | 以租户/机构自有版本替换平台同身份资产（定制副本） | 指向租户版本 |
| DISABLE | 在该作用域停用平台资产（不适用本机构） | 空（墓碑） |
| **ADD（新增）** | 新增平台没有的租户/机构独有资产（新 `asset_identity`，归属该 org_path） | 指向独有版本 |

> 实现层以 `ADD` 覆盖模式显式记录"无平台基线 + 指向本级独有版本"的语义；注册时拒绝已有平台基线的 ADD，并通过 `propagation` 区分下级复用或本级独有。

### 4.2 传播语义（新增列 `propagation`）
为 `InheritanceOverride` 增列 `propagation`：
- **INHERITABLE（复用）**：覆盖对本节点 **及其所有下级** 生效，直到某下级进一步覆盖。
- **EXCLUSIVE（独有）**：覆盖 **仅本节点** 生效；下级回退到上一层适用版本（通常平台基线）。

这正是"租户维护的版本可设定下级复用还是独有"。示例：
- 区域/医联体发"区域抗凝标准" → REGION 作用域、INHERITABLE → 各机构默认复用。
- 某机构"仅本机构的镇痛路径" → FACILITY 作用域、EXCLUSIVE → 其下科室/病区不继承。

### 4.3 最小化原则
覆盖只存"差异"，不复制整包；未覆盖部分恒指向平台。这保证平台权威统一、租户数据极简、治理清晰。

### 4.4 覆盖策略护栏（权威与临床安全）
平台版本 SHALL 携带 `override_policy`，约束下游可覆盖的程度——这是"权威不被弱化、患者安全不被破坏"的硬护栏：

| 策略 | 含义 | 典型资产 |
|---|---|---|
| FREE | 可自由 REPLACE/DISABLE | 一般推荐、宣教、随访模板 |
| REVIEW | 覆盖需走评审方可生效 | 给药剂量阈值、经验性抗菌、路径关键节点 |
| **LOCKED** | **禁止 DISABLE，且只能"收紧不能放宽"**（safety floor） | 给药禁忌、过敏核查、绝对禁忌、安全红线（`safety` 域） |

- **Safety floor 语义**：LOCKED 资产的 DISABLE 一律拒绝；REPLACE 仅在"更严格"方向被允许（如把禁忌阈值调更保守），放宽性修改被拒并审计。判定下沉到各域的 `SafetyMonotonicityCheck`（落地阶段定义）。
- LOCKED 与平台红线（`engine/safety`）联动：平台红线即天然 LOCKED。

## 5. 惰性解析算法（核心）

> 现有 `InheritanceResolver.resolve(InheritanceResolveQuery)→ResolvedAssetVersion` 已调用 `hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId)`，但**仅在单租户内**走闭包、**无平台层、无传播、无维度、无 policy**。本设计在此基础上扩展（保留方法形态，扩 `InheritanceResolveQuery` 入参 + 前置平台基线）。

扩展后的解析（`resolve(InheritanceResolveQuery{assetType, assetIdentity, tenantId, targetOrgUnitId, dimensions, at})` → `ResolvedAssetVersion`）：

```
1. base ← 平台(__platform__) ACTIVE AssetVersion(assetType, assetIdentity)   // 权威起点（新增层）；可空（ADD 类身份）
2. chain ← hierarchy.findAncestorsAndSelf(tenantId, targetOrgUnitId)          // 租户内 ROOT→…→target，最一般到最具体
3. effective ← base
   for node in chain (一般→具体):
       ov ← 查 InheritanceOverride(assetIdentity, org_path=node, applicable_scope 命中 dimensions, at 生效)
       if ov 为空: continue
       // 传播判定：node==target 时任意 propagation 适用；node 为祖先时仅 INHERITABLE 向下适用
       if node != target and ov.propagation == EXCLUSIVE: continue
       switch ov.mode:
           REPLACE → effective ← AssetVersion(ov.override_version_id)
           DISABLE → effective ← DISABLED（墓碑，调用方跳过该资产）
           ADD     → effective ← AssetVersion(ov.override_version_id)   // 独有身份
4. if effective == null and base == null: NOT_FOUND（诚实降级）
5. return ResolvedAssetVersion{ effective, sourceTier(PLATFORM/ORG/LEGACY), overrideId?, contentHash }
```

- **最具体优先**：链上越靠近 target 的可适用覆盖最终覆盖前者（含 descendant REPLACE 可"重新启用"祖先 DISABLE 的资产）。
- **可适用 = org_path 命中 + applicableScope 命中 + 生效期命中 + 传播允许 + 未违反 override_policy**。
- **同节点 tie-break**（同一 org_path 多条可适用覆盖）：① `applicableScope` 更具体者优先（具体科室/专科 > 通用）；② 仍并列时 `effective_from` 更晚者优先；③ 再并列取 `override_id` 字典序最大者（确定性）。tie-break 全程确定，保证可重放。
- **LOCKED 护栏**：解析阶段对 LOCKED 资产忽略 DISABLE 覆盖、拒绝放宽性 REPLACE（§4.4），并发审计告警。
- **批量解析**：对"有效知识包"做集合解析（§7.2），一次性 resolve 包内全部身份，避免 N+1（一次取闭包 + 一次批量取覆盖）。
- **可缓存**：以 `(tenantId, orgPath, packageActiveVersion, asset_identity)` 为键缓存解析结果；平台发布/覆盖变更/红线变更发"失效事件"精确失效。床旁 SLA 见附录 N。
- **复用现有**：`org_closure`/`findAncestorsAndSelf`、`VersionContentHash`、`VersionReplayService`、`VersionReplayBinding` 直接复用。

## 6. 统一版本底座（收敛四套并行）

### 6.1 适配器模式
各域不再自存版本表语义，改为实现 `VersionedAssetPort`，把领域内容登记为 `AssetVersion`：

| 域 | 现状（并行） | 收敛后适配器 | VersionedAssetType |
|---|---|---|---|
| rule | `RuleVersion`/`RuleVersionStatus` | `RuleVersionedAssetAdapter` | RULE |
| knowledge | `KnowledgeVersion`/`SourceVersion` | `KnowledgeVersionedAssetAdapter` | KNOWLEDGE |
| terminology | 自有发布 | `TerminologyVersionedAssetAdapter` | TERMINOLOGY |
| pathway | 自有发布/SpecialtyPackage | `PathwayVersionedAssetAdapter` | PATHWAY |
| context-field | 本轮 `mk_context_field_catalog`（平铺） | `FieldCatalogVersionedAssetAdapter` | **FIELD_CATALOG（新增枚举）** |
| evaluation | — | `EvaluationVersionedAssetAdapter` | EVALUATION |
| followup | — | `FollowupVersionedAssetAdapter` | **FOLLOWUP（新增枚举）** |

- `VersionedAssetType` 与 `PackageItemAssetType` **统一为一个枚举**（合并 KNOWLEDGE/TERMINOLOGY/RULE/PATHWAY/EVALUATION/FOLLOWUP/FIELD_CATALOG/PACKAGE/RECOMMENDATION/SAFETY/CDSS_RISK），消除两套不一致。
- 旧域版本表保留为"内容存储"，但"哪个版本在某机构生效"统一由底座解析；逐域迁移期以适配器双写过渡。

### 6.2 统一发布管线
一套：`VersionReleaseService.release(VersionReleaseCommand)` → `VersionActivationTransaction`（激活）→ `VersionReplayService`（重放/确定性）→ `VersionRollbackCommand`（回滚）。各域 publish 入口改为构造命令调用底座，废弃域内私有 publish 状态机。

## 7. 统一分发容器（收敛三套包）

### 7.1 KnowledgePackage 为唯一容器
- `KnowledgePackage` + `PackageItem(asset_type, asset_identity, version_no)` 成为平台→租户唯一权威分发载体。
- `TermMappingPackage(+Release)`、`SpecialtyPackage` **收敛**为：
  - 数据上并入 `PackageItem`（term-mapping 项 assetType=TERMINOLOGY，专科包项 assetType=PATHWAY）；
  - API 上保留专域"视图"外观（按 assetType 过滤的子集）以兼容现有前端，内部同一张表。
- `PackageItem.asset_id` → 语义即 `asset_identity`；`asset_version` → `version_no`。

### 7.2 有效知识包解析（平台优先）
租户/机构看到的"有效包" = 平台包基线 ∪ 本组织闭包覆盖增量，lazy 合成：
```
effectivePackage(tenant, orgPath, packageIdentity):
  base ← 平台 ACTIVE KnowledgePackage(packageIdentity) 的 items
  for item in base:
      item.effective ← InheritanceResolver.resolve(item.assetType, item.asset_identity, orgPath)
      # REPLACE→替换版本；DISABLE→从包剔除；保留平台版本则原样
  adds ← 该 orgPath 闭包内 ADD 的独有身份（propagation 命中）
  return base(解析后) + adds
```

### 7.3 分发与离线
- `SyncTarget`/`PackageOfflineImport` 下发的是 **解析后的有效包快照**（供断网机构本地执行），但 **权威源永远是平台 + 覆盖增量**；下发快照带 `content_hash` 与来源版本指针，可追溯、可回滚（复用现有 `PackageRollbackRequest`）。
- `ReleasePlan`/`ReleaseStrategy`/`ReleaseScopeType`/`VersionReleaseScopeType` 收敛到附录 O 的统一模型：组织层级 `OrgLevel` + 横切维度 `ScopeDimension` + 发布策略 `RolloutStrategy`（不再混用单一枚举）。

## 8. 运行期解析（ClinicalEvent）

- ClinicalEvent 分发时，按就诊 `encounter.orgPath` 解析 **有效规则集/路径集/字典/字段目录**：对相关 `asset_identity` 批量 `resolve`，得到该机构当前权威+定制的有效资产，再执行评估。
- `cdss`/`cdshook`/`recommendation` 读取统一走解析结果，确保"平台标准 + 本机构定制"在床旁一致生效。
- 解析结果随 `traceId` 落审计：本次用了平台版本还是某机构覆盖版本、content_hash 多少，保证可解释、可重放。
- **决策固化**：对一次具体临床决策，解析出的有效资产集 SHALL 以 `VersionReplayBinding` 钉定（asset_identity→content_hash 快照），供事后法律级重放，即使其后平台/覆盖再变也能还原当时依据。

### 8.5 升级通道：权威跟随 vs 受控升级（平台权威 ↔ 平稳）
为兼顾"平台权威自动下发"与"医院不被未预期变更打扰"，平台包按 **通道（channel）** 发布，租户/机构对每个平台包声明 **升级模式**：

| 升级模式 | 行为 | 适用 |
|---|---|---|
| AUTO（默认） | 平台新版本自动生效（权威优先） | 字典、宣教、低风险 |
| NOTIFY | 自动生效但强提醒 + 可一键回退到上一钉点 | 一般规则/路径 |
| PINNED | 钉在指定平台版本，新版本仅产生"可升级"信号，人工确认后升 | 高风险/严监管科室 |

- **安全例外**：LOCKED/红线类安全更新 **无视 PINNED 强制下发**（安全不可被钉旧），仅记录告知。
- 钉点切换、升级、回退全部走统一 `VersionActivationTransaction` + 审计，平稳可回退。

## 9. 租户开通改为引用制

- 开通租户：仅创建租户 org 根 + 授予对平台包的 **引用**与 **覆盖能力**，**不实例化任何副本**。
- `PilotPackageTemplate` 改义为"开通向导推荐勾选哪些平台包/作用域"，落库的是引用+（可选）初始覆盖，而非整包复制。
- 多机构开通：建 REGION 节点 + 各机构 FACILITY（以 facilityType 区分医院/卫生院/服务站等）、可选 CAMPUS、DEPARTMENT、WARD；专病通过 applicableScope 横切表达，全部引用平台，定制按 §4 覆盖。

## 10. 治理 / 权限 / 审计

- **权限分离**（security 域）：
  - 平台版本发布/激活：`platform.publish`（平台管理员）。
  - 租户/机构覆盖：`tenant.override`（租户/机构管理员），且只能在自身 org 闭包内。
  - 高风险资产（安全红线、给药剂量、禁忌）覆盖：强制评审（复用现有评审/红线 `safety` 域）。
- **跨租户隔离**：解析 SHALL 严格限定在"平台基线 + 本租户自身组织闭包"内；任一租户/机构 **绝不可见** 他租户的覆盖、ADD 独有资产或差异。平台层为唯一共享只读源。
- **审计**：版本、覆盖、传播变更、分发、回滚全部进审计链（含 `trace_id`、`content_hash`、before/after）。
- **门禁**（落地阶段逐项登记）：ServiceContractCatalog（新控制器+权限+审计点）、MigrationBaselineContract（新表/列/索引/约束/manifest/版本号）、DomainOwnership（前缀归属）、guard-rules（表名/租户索引/中文注释）、comment-language-check。

## 11. 影响分析与继承差异

- **上游变更影响**：平台发新版本时，计算受影响集合 = 引用该身份且 **未** REPLACE 的所有租户/机构（自动获新）+ 已 REPLACE 者（产生"上游已变更，建议 rebase"信号）。
- **继承差异视图**：任意机构可查"我相对平台/上级改了什么"（REPLACE/DISABLE/ADD 清单 + diff，复用现有 `PackageDiff*`/`diff_summary`）。
- **Rebase**：已覆盖者可基于平台新版本重做覆盖（三方合并提示），保持权威跟随。

## 12. 数据模型变更（对号入座）

| 变更 | 现有 | 动作 |
|---|---|---|
| 平台层 | `AssetVersion.tenant_id`/`org_path` | 约定 `__platform__`+`/`（或加 `scope_tier`，D1） |
| 传播 | `InheritanceOverride` | 增列 `propagation`（INHERITABLE/EXCLUSIVE，默认 INHERITABLE） |
| 资产类型归一 | `VersionedAssetType` vs `PackageItemAssetType` | 合并为单一枚举，补 FIELD_CATALOG/FOLLOWUP/RECOMMENDATION/SAFETY/CDSS_RISK |
| 包条目身份 | `PackageItem.asset_id/asset_version` | 语义对齐 `asset_identity/version_no`，加索引 |
| 字段目录归一 | `mk_context_field_catalog`（tenant 平铺） | 平台条目 source=PLATFORM；租户覆盖走 override |
| 标准字典平台化 | `StandardTerm.tenant_id` | 平台标准 = PLATFORM 归属；院内 `LocalTerm`+`TermMapping` 为租户覆盖 |
| 端口实现 | `VersionedAssetPort`（无实现） | 各域适配器实现 |

## 13. 多方言迁移（落地阶段）

- 一支 Flyway 迁移族（dm/h2/kingbase/oracle/postgres）：加 `propagation` 列、平台层支持、`PackageItem` 身份索引、枚举值落地。
- 中文 `COMMENT ON`（pg/oracle/kingbase）、租户索引、表名规范遵循 guard-rules。
- 迁移版本号 **合并前再定**（避免与其他 AI 抢号，见 _HANDOFF.md 教训）。

## 14. 兼容与分阶段迁移

- **诚实降级桥**：解析时若平台基线缺失，回退"本租户现存版本"并标注 `sourceTier=LEGACY`，使旧数据平滑过渡，不阻断业务。
- **双写过渡**：各域 publish 同时写旧版本表 + 底座 AssetVersion；读路径先切底座解析，稳定后下线旧表语义。
- **分阶段**见 tasks.md（P0 底座补齐 → P1 单域试点 terminology → P2 rule/path → P3 pkg 收敛 → P4 运行期 → P5 开通/治理）。

## 15. 多层级场景走查

| 场景 | 建模 |
|---|---|
| 区域/医联体统一标准 | 平台包基线 + REGION 作用域 INHERITABLE 覆盖（区域统一项） |
| 机构在区域基础上定制 | FACILITY/CAMPUS REPLACE 覆盖（基于区域/平台版本），propagation 按需 |
| 机构某项不适用 | FACILITY DISABLE 覆盖 |
| 独立卫生院（自成一套） | FACILITY 节点（facilityType=TOWNSHIP_CLINIC）：对不需要项 DISABLE + ADD 本机构独有，或整体引用平台另配 |
| 科室/病区/专病细化 | DEPARTMENT/WARD 覆盖；专病经 applicableScope 叠加，EXCLUSIVE 表达仅本节点 |
| **房颤抗凝** | 平台发"CHA₂DS₂-VASc + HAS-BLED 抗凝建议"规则+路径包；区域统一首选药 INHERITABLE；某机构因肾功能人群调整剂量阈值 REPLACE |
| **脓毒症** | 平台发"qSOFA/SOFA + 1h bundle"路径+规则；机构按本机构抗菌谱 REPLACE 经验性用药节点，propagation=INHERITABLE 下沉到其急诊科 |

## 16. 与现有代码精确对接表

| 设计构件 | 落点（现有） |
|---|---|
| 解析引擎 | `engine/versioning/InheritanceResolver`（升级，接 org_closure） |
| 覆盖记录 | `engine/versioning/InheritanceOverride`(+propagation)/`InheritanceOverrideMode` |
| 版本/激活/回放/回滚 | `VersionReleaseService`/`VersionActivationTransaction`/`VersionReplayService`/`VersionRollbackCommand`/`VersionContentHash` |
| 端口 | `VersionedAssetPort`（各域实现）/`ReleasePort` |
| 作用域 | `OrgLevel`(组织树)+`applicable_scope`(横切维度)+`RolloutStrategy`(策略)，收敛 `VersionReleaseScopeType`/`ReleaseScopeType`（附录 O） |
| 组织层级 | `shared/context/OrgLevel`（PLATFORM/TENANT/REGION/FACILITY/CAMPUS/DEPARTMENT/WARD，FACILITY 以 facilityType 区分机构类型，附录 O） |
| 弃用后继 | `engine/knowledge/KnowledgeSupersession`/`SupersessionType`（附录 L4） |
| 分发容器 | `engine/pkg/KnowledgePackage`/`PackageItem`/`SyncTarget`/`ReleasePlan`/`PackageDiff*`/`PackageRollbackRequest` |
| 字典收敛 | `engine/terminology/TermMappingPackage(+Release)` → PackageItem 视图 |
| 路径包收敛 | `engine/pathway/SpecialtyPackage` → PackageItem 视图 |
| 字段目录 | `engine/context/*FieldCatalog*`（平台条目 + override） |
| 组织闭包 | `engine/org/*`（org_closure/findAncestorsAndSelf） |
| 开通 | `engine/tenant/*`/`PilotPackageTemplate`（改引用制） |
| 运行期 | `engine/clinical|cdss|cdshook|recommendation/*`（按机构解析有效集） |

## 17. 待决项（我已给默认决策，评审时确认）

> 完整决策清单（D1–D8）见 **附录 G（G4）**，此处保留摘要。

- **D1 平台层实现**：默认 (a) `__platform__` 租户号 + 顶层 org_path（迁移最小）；(b) 加 `scope_tier` 列更显式。→ **默认 a**。
- **D2 枚举归一时机**：`VersionedAssetType`/`PackageItemAssetType` 合并是否一步到位。→ **默认一步合并 + 兼容别名**。
- **D3 字典平台化**：`StandardTerm` 是否物理移除 `tenant_id`。→ **默认保留列，平台标准用 `__platform__`，避免破坏式迁移**。
- **D4 解析缓存层**：放应用内存 vs 独立缓存表。→ **默认应用内存 + 失效事件**，量大再加表。
- **D5 SpecialtyPackage/TermMappingPackage 物理合并 vs 视图桥接**：→ **默认先视图桥接（不破坏前端），后台数据并入 PackageItem，二期物理下线旧表**。
- **D6 升级模式默认值**：→ **字典/宣教 AUTO，规则/路径 NOTIFY，高风险/严监管 PINNED**（§8.5）。
- **D7 LOCKED 单调性判定**：放解析层统一判定 vs 各域自定 `SafetyMonotonicityCheck`。→ **默认各域提供单调性谓词，解析层统一调用**（§4.4）。

## 18. 价值与可度量结果

- **权威**：单一平台真相 + 不可弱化的安全护栏（LOCKED）+ 版本不可变 + content_hash 可重放 → 临床内容一致、可审计、可追责。
- **易用**：三视角授权 + 有效来源徽标 + 与平台 diff + 批量覆盖 + 开通向导（附录 M）→ 配置门槛低、心智清晰。
- **平稳**：惰性解析零副本 + 升级通道(AUTO/NOTIFY/PINNED) + 双写过渡 + 诚实降级桥 + 可回退 → 升级不惊扰、迁移不停机。
- **价值/度量**：可度量目标——平台升级到达未覆盖租户"零操作"；存量重复内容收敛率；新租户开通时长（副本实例化→引用，数量级下降）；床旁解析 P99 延迟（附录 N SLA）；覆盖与平台差异可视化覆盖率 100%。

## 附录
- **附录 M — 授权体验设计（易用）**：`design-authoring-experience.md`
- **附录 S — 安全与权威护栏（权威）**：`design-safety-authority.md`
- **附录 N — NFR / 运维 / 存量回填（平稳）**：`design-stability-operations.md`
- **附录 O — 组织与作用域模型修正（继承轴正确性）**：`design-org-scope-model.md` ⭐ 含七层结构纠偏
- **附录 D — 资产依赖与引用完整性 / 一致性快照**：`design-asset-dependency-integrity.md`
- **附录 L — 编辑生命周期 / 循证溯源 / 弃用后继 / 质量门**：`design-lifecycle-governance.md`
- **附录 R — 发布前模拟 / 灰度 / 批量复用**：`design-simulation-rollout.md`
- **附录 I — 互操作导入导出 / 第三方 API 契约 / 授权许可**：`design-interoperability-entitlement.md`
- **附录 E — 端到端走查（房颤抗凝，贯穿全机制）**：`design-worked-example.md`
- **附录 G — 术语/枚举总表 与 决策清单（防漂移，单一真相）**：`design-glossary-decisions.md` ⭐ 落地以此对齐

> 重要：附录 O 修正了"七层组织树"的实质缺陷——PLATFORM 层缺失、SPECIALTY(专病) 应为横切维度而非树叶、层级须可跳级、BED_PERCENT 应迁为发布策略。本设计的解析轴以附录 O 修正模型为准。
