# 编辑生命周期与治理（authoring-lifecycle-governance）

## ADDED Requirements

### Requirement: 统一生命周期状态机
平台版本与租户覆盖 SHALL 共用生命周期 DRAFT→IN_REVIEW→APPROVED→PUBLISHED→DEPRECATED→RETIRED；仅 PUBLISHED 参与解析，RETIRED 不解析但保留可重放；高风险（LOCKED/REVIEW）资产 PUBLISH SHALL 需电子签名。

#### Scenario: 草稿不参与解析
- **WHEN** 某覆盖处于 DRAFT
- **THEN** 解析不应用该覆盖

#### Scenario: 高风险发布需签名
- **WHEN** 发布一个 LOCKED 资产新版本
- **THEN** 缺少电子签名时发布被拒并审计

### Requirement: 循证溯源与复审
平台知识资产 SHALL 携带来源、证据等级与复审周期；临近/超期 SHALL 进待复审队列并预警。

#### Scenario: 过期知识预警
- **WHEN** 资产超过下次复审日期
- **THEN** 进入待复审队列并在看板预警

### Requirement: 弃用不断链
资产 SHALL NOT 物理删除；弃用/退役 SHALL 提供后继指针与宽限期，引用方收到迁移引导。

#### Scenario: 退役引导迁移
- **WHEN** 平台退役身份 A 并指向后继 A'
- **THEN** 引用 A 的租户覆盖被悬置并引导迁移到 A'，不静默失效
