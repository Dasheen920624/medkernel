# 惰性继承解析（inheritance-resolution）

## ADDED Requirements

### Requirement: 按组织闭包惰性解析有效版本
`InheritanceResolver` SHALL 在读取/运行期按目标机构实时解析有效版本：以平台基线为起点，沿组织闭包（平台→GROUP→…→目标，最一般到最具体）应用可适用覆盖，最具体者最终生效，不得落地中间副本。

#### Scenario: 七层链最具体优先
- **WHEN** 平台 v0、集团 INHERITABLE v1、分院 REPLACE v2 同时存在，目标为分院下某科室且科室无覆盖
- **THEN** 解析返回 v2

### Requirement: 可适用性判定
覆盖被应用 SHALL 同时满足：`org_path` 命中闭包节点、`applicableScope` 命中、生效期（effective_from/to）命中给定时刻、传播允许（祖先节点仅 INHERITABLE 向下适用）。

#### Scenario: 过期覆盖不生效
- **WHEN** 分院覆盖 effective_to 已过
- **THEN** 解析忽略该覆盖，回退上一层适用版本

### Requirement: 解析结果可解释可重放
解析结果 SHALL 返回来源层级（PLATFORM/ORG/LEGACY）、覆盖标识与 `content_hash`，并随 `trace_id` 落审计，使每次床旁决策可追溯用的是平台还是某机构定制版本，并可按 `content_hash` 重放。

#### Scenario: 运行期解析落审计
- **WHEN** ClinicalEvent 在机构 O 触发规则评估
- **THEN** 审计记录每条命中资产的 sourceTier 与 content_hash
