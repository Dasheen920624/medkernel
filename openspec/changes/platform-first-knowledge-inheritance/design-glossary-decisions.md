# 附录 G — 术语 / 枚举总表 与 决策清单（防漂移）

> 所有新引入概念与枚举集中于此，单一真相，避免跨文档漂移；落地时各域以此对齐。

## G1 核心术语
| 术语 | 含义 |
|---|---|
| 权威层 Authority Tier | PLATFORM（高于所有租户的权威只读源）⊃ TENANT |
| 资产身份 asset_identity | 跨平台/租户/机构稳定的资产标识，覆盖按身份"遮蔽"平台 |
| 覆盖 Override | 租户/机构对身份的增量声明（REPLACE/DISABLE/ADD） |
| 传播 Propagation | 覆盖是否向下级生效（INHERITABLE/EXCLUSIVE） |
| 有效版本 Effective | 解析后某机构×维度实际生效的版本 |
| resolution epoch | 一次决策/合成的一致性时点锚（平台包游标+覆盖游标） |
| 横切维度 Dimension | 与组织正交的作用域（专病/场景/人群/角色），经 applicable_scope |

## G2 枚举总表（统一定义）
| 枚举 | 取值 | 说明 / 现状 |
|---|---|---|
| AuthorityTier | PLATFORM, TENANT | 新增；PLATFORM 高于 TENANT |
| OrgLevel（修正） | PLATFORM, TENANT, REGION, FACILITY, CAMPUS, DEPARTMENT, WARD | 现状 TENANT/GROUP/HOSPITAL/CAMPUS/SITE/DEPARTMENT/SPECIALTY → GROUP→REGION、HOSPITAL/SITE→FACILITY+type、加 WARD、SPECIALTY 移出为维度；`canHaveParent` 放宽为"父层级更高即可" |
| facilityType | HOSPITAL, COMMUNITY_CENTER, TOWNSHIP_CLINIC, STATION, … | FACILITY 子类型（替代原 HOSPITAL/SITE 分立） |
| ScopeDimension | SPECIALTY, CARE_SETTING, COHORT, ROLE | 横切维度键；经 applicable_scope 编码（附录 O3b） |
| InheritanceOverrideMode | REPLACE, DISABLE（ADD 以"归属 org 的新身份+无平台基线"表达） | 现有枚举沿用 |
| Propagation（新增列） | INHERITABLE, EXCLUSIVE | 默认 INHERITABLE |
| override_policy（新增） | FREE, REVIEW, LOCKED | 平台版本携带；LOCKED=安全单调 |
| RolloutStrategy | ALL, ORG_SUBTREE, ORG_LIST, CANARY_BED_PERCENT, STAGED | BED_PERCENT 由作用域迁入；与升级通道联动 |
| UpgradeMode | AUTO, NOTIFY, PINNED | 租户对平台包的升级模式（§8.5） |
| LifecycleState | DRAFT, IN_REVIEW, APPROVED, PUBLISHED, DEPRECATED, RETIRED | 平台版本与覆盖共用（附录 L1） |
| sourceTier（解析结果） | PLATFORM, ORG, LEGACY | LEGACY=诚实降级回退 |
| AssetType（归一） | KNOWLEDGE, TERMINOLOGY, RULE, PATHWAY, EVALUATION, FOLLOWUP, FIELD_CATALOG, PACKAGE, RECOMMENDATION, SAFETY, CDSS_RISK | 合并 VersionedAssetType+PackageItemAssetType |

## G3 新增/扩展数据结构（落地）
| 名称 | 类型 | 说明 |
|---|---|---|
| `InheritanceOverride.propagation` | 列 | INHERITABLE/EXCLUSIVE |
| `AssetVersion.override_policy` | 列 | FREE/REVIEW/LOCKED |
| `AssetVersion.lifecycle_state` | 列 | 生命周期 |
| `applicable_scope` 编码 | 约定 | 键值集（附录 O3b） |
| `asset_dependency` | 表 | 依赖图（附录 D6） |
| `mk_pkg_package_entitlement` | 表 | 授权（附录 I5） |
| `RolloutStrategy` / `UpgradeMode` | 枚举 | 策略与通道 |
| `ScopeMatcher` / `SafetyMonotonicityCheck` / `DependencyValidator` | 组件 | 解析层/各域 |

## G4 决策清单（默认值，评审确认）
| # | 决策 | 默认 |
|---|---|---|
| D1 | 平台层实现 | `__platform__` 租户 + 顶层 org_path（迁移最小） |
| D2 | AssetType 归一时机 | 一步合并 + 兼容别名 |
| D3 | StandardTerm 平台化 | 保留 tenant_id 列，平台标准用 `__platform__`（非破坏式） |
| D4 | 解析缓存 | 应用内存 + 失效事件；超大租户二期物化 |
| D5 | TermMappingPackage/SpecialtyPackage 合并 | 先视图桥接，二期物理并入 |
| D6 | 升级模式默认 | 字典/宣教 AUTO、规则/路径 NOTIFY、高风险 PINNED |
| D7 | LOCKED 单调判定 | 各域提供谓词，解析层统一调用 |
| D8 | 组织树修正幅度 | 全量采纳；最小集（PLATFORM+放宽父子+专病维度化+BED_PERCENT 迁出）P0 先行 |

## G5 不变量（契约测试将断言）
1. 解析输入恒含 `tenant_id` + org 闭包；跨租户不可见他租户覆盖/ADD（§10 隔离）。
2. LOCKED 资产不可 DISABLE、不可放宽 REPLACE（附录 S2）。
3. 未覆盖身份的解析结果恒等于平台 ACTIVE 版本（§3）。
4. 同一决策内所有资产取同一 epoch（附录 D4）。
5. 开通不产生平台资产副本（§9）。
6. tie-break 全确定（组织深度 > 维度具体度 > effective_from > override_id 序）。
