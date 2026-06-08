# 实施任务（分阶段，先设计后实现）

> 每阶段独立可交付、可门禁、可回退。落地时各阶段拆为独立 PR，逐项过 ServiceContract/Migration/DomainOwnership/guard-rules/comment-language 门禁。

## P0 底座补齐（解析可用，不接业务）
- [x] 1.0 **组织/作用域模型修正（附录 O，最小集先行）**：加 PLATFORM 层（与 TENANT 分离）；放宽 `OrgLevel.canHaveParent` 允许跳级；SPECIALTY 专病从树迁为横切维度（applicableScope）；BED_PERCENT 从作用域迁为 `RolloutStrategy`；发布作用域枚举只保留组织层级
- [x] 1.0b（二期）组织树补全：GROUP→REGION 泛化、HOSPITAL/SITE→FACILITY+facilityType 归一、WARD 病区层、可选 DAG 次级归属
- [x] 1.1 `InheritanceOverride` 增 `propagation`（INHERITABLE/EXCLUSIVE，默认 INHERITABLE）+ `override_policy`（FREE/REVIEW/LOCKED）+ 多方言迁移
- [x] 1.2 升级 `InheritanceResolver.resolve()`：平台基线 → org 闭包覆盖链（最具体优先、尊重传播、维度 applicableScope 命中、tie-break 确定性、LOCKED 护栏），返回 `ResolvedAsset{effective, sourceTier, overrideId, contentHash}`
- [x] 1.3 平台空间约定（D1=a）：`__platform__` 租户 + 顶层 org_path；AssetVersion 平台 ACTIVE 读取
- [x] 1.4 `VersionedAssetType`/`PackageItemAssetType` 归一为单一枚举（补 FIELD_CATALOG/FOLLOWUP/RECOMMENDATION/SAFETY/CDSS_RISK）+ 兼容别名
- [x] 1.5 Resolver/传播/平台基线/维度/tie-break/LOCKED 单测（REPLACE/DISABLE/ADD × INHERITABLE/EXCLUSIVE × 组织层 × 专病维度 矩阵）
- [x] 1.6 安全单调性 `SafetyMonotonicityCheck`（各域谓词，解析层统一调用，附录 S2）

## P1 单域试点：terminology（最贴近本轮对照覆盖）
- [x] 2.1 `TerminologyVersionedAssetAdapter implements VersionedAssetPort`
- [x] 2.2 平台标准字典 = PLATFORM 归属；院内 `LocalTerm`/`TermMapping` 表达为租户覆盖
- [x] 2.3 读路径（标准字典/对照覆盖）改走 `InheritanceResolver`，旧路径诚实降级桥
- [x] 2.4 `TermMappingPackage(+Release)` 视图桥接到 `PackageItem`（assetType=TERMINOLOGY）
- [x] 2.5 端到端验证：平台标准下发→REGION INHERITABLE 定制→FACILITY REPLACE→基层 FACILITY DISABLE

## P2 rule + pathway
- [x] 3.1 `RuleVersionedAssetAdapter` / `PathwayVersionedAssetAdapter`
- [x] 3.2 rule/path publish 改走 `VersionReleaseService`（统一 release/activation/replay/rollback），双写过渡
- [x] 3.3 `SpecialtyPackage` 视图桥接到 `PackageItem`（assetType=PATHWAY）
- [x] 3.4 路径边 guard / 规则条件树解析按机构有效版本

## P3 字段目录 + knowledge + evaluation + followup
- [x] 4.1 `FieldCatalogVersionedAssetAdapter`：平台字段目录条目（source=PLATFORM）+ 租户覆盖（替换本轮 tenant 平铺）
- [x] 4.2 `KnowledgeVersionedAssetAdapter`（收敛 KnowledgeVersion/SourceVersion）
- [x] 4.3 evaluation/followup 适配器

## P4 统一分发与运行期
- [x] 5.1 `KnowledgePackage` 有效包解析（平台包基线 ∪ 组织闭包覆盖增量，lazy）
- [x] 5.2 `SyncTarget`/离线导入下发"解析后有效包快照"（content_hash + 来源版本指针 + 回滚）
- [x] 5.3 ClinicalEvent/cdss/cdshook/recommendation 运行期按 `encounter.orgPath` 解析有效资产集
- [x] 5.4 解析来源（平台/覆盖 + content_hash）落审计与 trace

## P5 开通 + 治理 + 影响
- [x] 6.1 租户开通改引用制（不实例化副本）；`PilotPackageTemplate` 改为推荐引用 + 可选初始覆盖
- [ ] 6.2 权限分离：`platform.publish` / `tenant.override`（限自身 org 闭包）/ 高风险覆盖强制评审
- [ ] 6.3 上游变更影响计算 + 继承差异视图 + rebase 提示（复用 PackageDiff/diff_summary）
- [ ] 6.4 前端：平台/租户视角切换、覆盖编辑（REPLACE/DISABLE/ADD + 复用/独有）、有效版本来源标识、继承差异

## P4.5 完整性 / 生命周期 / 模拟（横切能力，随各域接入逐步启用）
- [ ] 5.5 资产依赖图 + 引用完整性校验 + 协同解析 + resolution epoch 一致性快照（附录 D）
- [ ] 5.6 统一生命周期状态机（DRAFT…RETIRED）+ 高风险电子签名 + 平台发布质量门（附录 L1/L5）
- [ ] 5.7 循证溯源/证据等级/复审周期 + 弃用后继（KnowledgeSupersession）+ 资产身份治理（附录 L2/L4/L6）
- [ ] 5.8 发布前影响模拟（what-if 历史回放）+ 灰度放量(RolloutStrategy)+ 批量/模板/克隆（附录 R）

## P5 开通 + 治理 + 影响 + 互操作
- [ ] 6.5 互操作：CDS Hooks / FHIR PlanDefinition 导入导出 / CQL 受控导入（附录 I1）
- [ ] 6.6 第三方接入 API 契约 + OpenAPI 文档（有效解析查询/上下文写入/覆盖管理/包分发，附录 I2）
- [ ] 6.7 entitlement 授权层（受限平台包按租户授权可见/下发，附录 I4）

## P6 收口
- [ ] 7.1 旧并行版本表语义下线（读切底座稳定后）；TermMappingPackage/SpecialtyPackage 物理并入（D5 二期）；组织树二期补全（1.0b）
- [ ] 7.2 全门禁登记 + 多方言迁移校验 + ArchUnit 依赖约束（含跨租户隔离、解析无 N+1 断言）
- [ ] 7.3 文档：API 规范（第三方按平台标准传入）、院内字典↔平台标准对照、运维手册、可观测看板（附录 N7）
