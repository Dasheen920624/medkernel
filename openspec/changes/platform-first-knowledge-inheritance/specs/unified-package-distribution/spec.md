# 统一知识包分发（unified-package-distribution）

## ADDED Requirements

### Requirement: 知识包为唯一权威分发容器
`KnowledgePackage`+`PackageItem(asset_type, asset_identity, version_no)` SHALL 作为平台→租户唯一权威分发载体；`TermMappingPackage`、`SpecialtyPackage` SHALL 收敛为按 assetType 过滤的专域视图，底层数据并入 `PackageItem`，API 外观保持兼容。

#### Scenario: 专科包作为知识包视图
- **WHEN** 查询某专科包内容
- **THEN** 返回 `PackageItem` 中 assetType=PATHWAY 的子集，数据来源同一张表

### Requirement: 有效知识包惰性合成
租户/机构看到的有效知识包 SHALL = 平台包基线（逐条经 `InheritanceResolver` 解析）∪ 本组织闭包内 ADD 的独有资产，按需合成，不预先落地副本；REPLACE 替换版本、DISABLE 从包剔除。

#### Scenario: 机构有效包含定制项
- **WHEN** 平台包含 A、B、C，FACILITY 对 A REPLACE、对 B DISABLE、ADD 独有 X
- **THEN** 机构有效包 = {A(机构版), C(平台版), X}

### Requirement: 分发与离线下发解析后快照
`SyncTarget`/离线导入 SHALL 下发解析后的有效包快照（带 `content_hash` 与来源版本指针），供断网机构本地执行；权威源仍为平台+覆盖增量，快照可追溯、可回滚。

#### Scenario: 断网机构获取有效包快照
- **WHEN** 向机构 SyncTarget 下发其有效包
- **THEN** 下发内容为解析后的快照且携带 content_hash 与来源版本指针
