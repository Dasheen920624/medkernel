# 互操作与授权（interoperability-entitlement）

## ADDED Requirements

### Requirement: 标准互操作
平台资产 SHALL 支持与开放标准互通：CDS Hooks 服务、FHIR PlanDefinition/ActivityDefinition 路径导入导出、CQL 受控导入、标准编码系统对接；导入入平台前走质量门，导出携带 content_hash 与溯源。

#### Scenario: 路径导出为 PlanDefinition
- **WHEN** 导出一条平台路径
- **THEN** 产出符合 FHIR PlanDefinition 的表示且携带 content_hash 与溯源

### Requirement: 第三方接入契约
系统 SHALL 提供版本化、向后兼容的对外契约：有效解析查询、按平台标准字段目录/字典写入上下文、覆盖管理、包分发对账；契约纳入 ServiceContractCatalog 并产出接口文档。

#### Scenario: 第三方按平台标准传入上下文
- **WHEN** 院内系统以平台标准字段与字典编码传入患者上下文
- **THEN** 系统接受并经 TermMapping 归一；未对照编码触发就绪度告警与诚实降级

### Requirement: 授权许可
受限平台包对租户的可用性 SHALL 受 entitlement 控制；解析/分发前校验授权；无授权不可见不下发，到期降级为只读历史或不可解析（诚实降级）。

#### Scenario: 未授权包不下发
- **WHEN** 租户未获某商业指南包授权
- **THEN** 该包不出现在其有效包中且不下发
