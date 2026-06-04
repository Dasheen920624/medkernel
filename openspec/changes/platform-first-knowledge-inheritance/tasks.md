# 实施任务（分阶段，先设计后实现）

> 每阶段独立可交付、可门禁、可回退。落地时各阶段拆为独立 PR，逐项过 ServiceContract/Migration/DomainOwnership/guard-rules/comment-language 门禁。

## P0 底座补齐（解析可用，不接业务）
- [ ] 1.1 `InheritanceOverride` 增 `propagation`（INHERITABLE/EXCLUSIVE，默认 INHERITABLE）+ 多方言迁移
- [ ] 1.2 升级 `InheritanceResolver.resolve()`：平台基线 → org 闭包覆盖链（最具体优先、尊重传播、生效期/applicableScope 命中），返回 `ResolvedAsset{effective, sourceTier, overrideId, contentHash}`
- [ ] 1.3 平台空间约定（D1=a）：`__platform__` 租户 + 顶层 org_path；AssetVersion 平台 ACTIVE 读取
- [ ] 1.4 `VersionedAssetType`/`PackageItemAssetType` 归一为单一枚举（补 FIELD_CATALOG/FOLLOWUP/RECOMMENDATION/SAFETY/CDSS_RISK）+ 兼容别名
- [ ] 1.5 Resolver/传播/平台基线单测（含 REPLACE/DISABLE/ADD × INHERITABLE/EXCLUSIVE × 七层矩阵）

## P1 单域试点：terminology（最贴近本轮对照覆盖）
- [ ] 2.1 `TerminologyVersionedAssetAdapter implements VersionedAssetPort`
- [ ] 2.2 平台标准字典 = PLATFORM 归属；院内 `LocalTerm`/`TermMapping` 表达为租户覆盖
- [ ] 2.3 读路径（标准字典/对照覆盖）改走 `InheritanceResolver`，旧路径诚实降级桥
- [ ] 2.4 `TermMappingPackage(+Release)` 视图桥接到 `PackageItem`（assetType=TERMINOLOGY）
- [ ] 2.5 端到端验证：平台标准下发→集团 INHERITABLE 定制→分院 REPLACE→卫生院 DISABLE

## P2 rule + pathway
- [ ] 3.1 `RuleVersionedAssetAdapter` / `PathwayVersionedAssetAdapter`
- [ ] 3.2 rule/path publish 改走 `VersionReleaseService`（统一 release/activation/replay/rollback），双写过渡
- [ ] 3.3 `SpecialtyPackage` 视图桥接到 `PackageItem`（assetType=PATHWAY）
- [ ] 3.4 路径边 guard / 规则条件树解析按机构有效版本

## P3 字段目录 + knowledge + evaluation + followup
- [ ] 4.1 `FieldCatalogVersionedAssetAdapter`：平台字段目录条目（source=PLATFORM）+ 租户覆盖（替换本轮 tenant 平铺）
- [ ] 4.2 `KnowledgeVersionedAssetAdapter`（收敛 KnowledgeVersion/SourceVersion）
- [ ] 4.3 evaluation/followup 适配器

## P4 统一分发与运行期
- [ ] 5.1 `KnowledgePackage` 有效包解析（平台包基线 ∪ 组织闭包覆盖增量，lazy）
- [ ] 5.2 `SyncTarget`/离线导入下发"解析后有效包快照"（content_hash + 来源版本指针 + 回滚）
- [ ] 5.3 ClinicalEvent/cdss/cdshook/recommendation 运行期按 `encounter.orgPath` 解析有效资产集
- [ ] 5.4 解析来源（平台/覆盖 + content_hash）落审计与 trace

## P5 开通 + 治理 + 影响
- [ ] 6.1 租户开通改引用制（不实例化副本）；`PilotPackageTemplate` 改为推荐引用 + 可选初始覆盖
- [ ] 6.2 权限分离：`platform.publish` / `tenant.override`（限自身 org 闭包）/ 高风险覆盖强制评审
- [ ] 6.3 上游变更影响计算 + 继承差异视图 + rebase 提示（复用 PackageDiff/diff_summary）
- [ ] 6.4 前端：平台/租户视角切换、覆盖编辑（REPLACE/DISABLE/ADD + 复用/独有）、有效版本来源标识、继承差异

## P6 收口
- [ ] 7.1 旧并行版本表语义下线（读切底座稳定后）；TermMappingPackage/SpecialtyPackage 物理并入（D5 二期）
- [ ] 7.2 全门禁登记 + 多方言迁移校验 + ArchUnit 依赖约束
- [ ] 7.3 文档：API 规范（第三方按平台标准传入）、院内字典↔平台标准对照、运维手册
