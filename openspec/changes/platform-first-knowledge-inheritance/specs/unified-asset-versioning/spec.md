# 统一资产版本底座（unified-asset-versioning）

## ADDED Requirements

### Requirement: 各域通过统一端口登记版本
rule/knowledge/terminology/pathway/context-field/evaluation/followup 各域 SHALL 实现 `VersionedAssetPort`，将领域内容登记为统一 `AssetVersion`（`asset_identity`+`version_no`+`content_hash`），不再各自维护独立的"哪个版本生效"语义。

#### Scenario: 规则版本登记到统一底座
- **WHEN** 规则域发布一个规则版本
- **THEN** 底座存在对应 `AssetVersion(assetType=RULE, asset_identity, version_no, content_hash)`

### Requirement: 统一资产类型枚举
`VersionedAssetType` 与 `PackageItemAssetType` SHALL 合并为单一枚举，覆盖 KNOWLEDGE/TERMINOLOGY/RULE/PATHWAY/EVALUATION/FOLLOWUP/FIELD_CATALOG/PACKAGE/RECOMMENDATION/SAFETY/CDSS_RISK，消除两套不一致。

#### Scenario: 包条目与版本类型一致
- **WHEN** 知识包条目引用某字段目录资产
- **THEN** 其 assetType 与底座 `AssetVersion.assetType` 取值同源（FIELD_CATALOG）

### Requirement: 统一发布管线
版本的发布/激活/重放/回滚 SHALL 统一走 `VersionReleaseService`/`VersionActivationTransaction`/`VersionReplayService`/`VersionRollbackCommand`；各域私有 publish 状态机 SHALL 逐步废弃，迁移期以适配器双写过渡。

#### Scenario: 跨域统一回滚
- **WHEN** 对任意 assetType 的某次激活执行回滚
- **THEN** 走同一 `VersionRollbackCommand` 路径并落审计
