# 平台权威层（platform-authority）

## ADDED Requirements

### Requirement: 平台版本为默认权威
所有医疗资产（知识/字典/规则/路径/字段目录/评估/随访）SHALL 以平台发布的 `AssetVersion`（归属 PLATFORM 作用域、`asset_identity` 唯一、`content_hash` 锁定）为默认权威。任意租户/机构对某身份的有效版本解析，起点 SHALL 恒为该身份的平台 ACTIVE 版本。

#### Scenario: 未覆盖租户读取得到平台版本
- **WHEN** 租户 T 下机构 O 未对身份 A 创建任何覆盖
- **THEN** `resolve(A, O)` 返回平台 ACTIVE 版本，`sourceTier=PLATFORM`

#### Scenario: 平台缺失时诚实降级
- **WHEN** 身份 A 无平台基线且机构 O 无独有版本
- **THEN** 解析返回 NOT_FOUND（或回退本租户遗留版本并标注 `sourceTier=LEGACY`），不得静默伪造

### Requirement: 平台版本更新自动惠及未覆盖方
平台发布并激活某身份的新版本后，系统 SHALL 使所有引用该身份且未对其 REPLACE 的租户/机构在下次解析时自动获得新版本，无需任何租户级复制或同步动作。

#### Scenario: 平台升级规则后未定制分院自动跟随
- **WHEN** 平台将身份 A 激活到 v2，分院 B 未对 A 做 REPLACE
- **THEN** 分院 B 下次 `resolve(A, B)` 返回 v2

### Requirement: 引用而非复制
租户开通与日常使用 SHALL NOT 为租户预先复制平台资产副本；租户对平台资产 SHALL 以 `asset_identity` 引用持有。

#### Scenario: 开通不产生副本
- **WHEN** 新租户开通完成
- **THEN** 不存在任何由开通动作生成的平台资产副本记录；租户仅持有引用与覆盖能力
