# 组织与作用域模型（org-scope-model）

## ADDED Requirements

### Requirement: 四正交轴分离
继承解析轴 SHALL 分离为四个正交轴：权威层（PLATFORM⊃TENANT）、组织树（层级可选可跳级）、横切维度（专病/场景/人群/角色，经 applicableScope）、发布策略（含床位比例灰度）。三套既有作用域枚举 SHALL 收敛到该模型。

#### Scenario: 床位比例从作用域迁为发布策略
- **WHEN** 配置"按床位比例灰度发布"
- **THEN** 使用 RolloutStrategy.CANARY_BED_PERCENT，而非组织作用域取值

### Requirement: 组织层级可选可跳级
组织树 SHALL 允许节点挂在任一更高层级之下（跳过可选层），SHALL NOT 强制仅紧邻上一层。`canHaveParent` SHALL 仅要求父层级严格高于子层级。

#### Scenario: 单院区医院科室直挂医院
- **WHEN** 某医院无独立院区，科室直接挂在 FACILITY 之下
- **THEN** 组织校验通过（跳过 CAMPUS）

#### Scenario: 独立基层机构直挂租户
- **WHEN** 一个独立社区卫生服务中心无 REGION，直接挂在 TENANT 之下
- **THEN** 组织校验通过

### Requirement: 专病为横切维度而非组织节点
专病（SPECIALTY，如房颤/脓毒症）SHALL 建模为横切作用域维度（applicableScope），可与任意组织节点组合，SHALL NOT 作为组织树叶子强绑单一科室。

#### Scenario: 跨科室专病覆盖
- **WHEN** 平台发布"房颤抗凝"包（specialty=AF 维度），FACILITY 在 specialty=AF 维度做覆盖
- **THEN** 该覆盖对该机构内所有涉及房颤的科室（心内、急诊…）生效，不受单一科室树位限制

### Requirement: 平台层高于租户
组织根 SHALL 为 TENANT；PLATFORM SHALL 为高于所有租户的独立权威层，不再由 TENANT 兼任"平台根"。

#### Scenario: 平台与租户分离
- **WHEN** 解析任意租户机构的资产
- **THEN** 权威起点为 PLATFORM 层版本，TENANT 仅为该租户组织树根
