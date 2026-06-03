# 规格增量：上下文字段目录与字典对照

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`

## ADDED Requirements

### Requirement: 系统必须提供上下文字段目录

系统 SHALL 提供上下文字段目录能力，以 `engine.context.canonical.*` 为权威派生，记录每个可用字段的资源类型、字段路径、中文展示名、数据类型、单位、绑定标准字典与说明，并按标准上下文包版本进行版本化。字段目录 SHALL 通过只读接口供规则与路径的字段选择器消费。

#### Scenario: 字段选择器读取目录

- **GIVEN** 配置人员在规则或路径条件中选择字段
- **WHEN** 打开字段下拉
- **THEN** 系统 SHALL 通过 `GET /context/field-catalog` 返回当前包版本下的可用字段
- **AND** 字段 SHALL 携带数据类型与（如适用）绑定字典。

#### Scenario: 目录字段必须真实存在

- **GIVEN** 字段目录初始数据由迁移脚本从 canonical 模型派生
- **WHEN** CI 校验目录
- **THEN** 每个目录字段路径 SHALL 能在 canonical 模型中找到对应字段
- **AND** 维护接口 SHALL 不允许新增 canonical 中不存在的字段路径。

### Requirement: 字段目录必须可前台维护且受控

系统 SHALL 提供受 RBAC 与审计约束的维护接口与界面，允许在派生字段集合上补充展示名、说明与标准字典绑定。维护操作 SHALL 留审计记录，数据源不可达时 SHALL 诚实降级而非伪造目录。

#### Scenario: 维护字段字典绑定

- **GIVEN** 有权限的配置人员为「诊断编码」字段绑定 ICD-10 字典
- **WHEN** 保存
- **THEN** 系统 SHALL 通过维护接口持久化绑定并记录审计
- **AND** 该字段在条件比较值处 SHALL 提供 ICD-10 标准候选。

### Requirement: 字段目录必须支持派生字段

字段目录 SHALL 支持登记派生字段（`derived=true`）：非 canonical 原始列、而由求值期计算的临床常用量，如 `patient.age`（由 `patient.birthDate` 计算）、`patient.bodyWeightKg`（取最近一次体重 Observation）。派生字段在 DSL 中与普通字段一致引用，由 `ConditionEvaluator` 在求值期解析；派生定义 SHALL 声明依赖的原始字段，缺依赖时按缺失数据策略处理。

#### Scenario: 按年龄判断成人

- **GIVEN** 规则需要「年龄 ≥ 18」而 Patient 仅有 birthDate
- **WHEN** 选择派生字段 `patient.age`
- **THEN** 系统 SHALL 在求值期由 birthDate 与评估时刻计算年龄
- **AND** birthDate 缺失时 SHALL 返回 UNKNOWN 并按策略处理。

### Requirement: 编码字段必须绑定术语值集而非单一字典

系统 SHALL 支持编码类字段绑定术语值集（ValueSet，对齐 FHIR）。值集 SHALL 支持外延定义（显式编码列表）与内涵定义（按 CodeSystem + 过滤规则），并提供 `$expand`（展开成员）、`$validate-code`（成员校验）、`$subsumes`（上下位判定）。CodeSystem（ICD-10/LOINC/ATC/SNOMED CT 子集等）SHALL 版本化并随标准上下文包版本锁定，保证可回滚与可重放。

#### Scenario: 用值集做集合判断

- **GIVEN** 规则需要「诊断属于头孢类过敏相关编码集」
- **WHEN** 比较值绑定一个内涵值集
- **THEN** 系统 SHALL 通过 `$expand` 展开成员后判断
- **AND** 支持 `$subsumes` 时上位编码 SHALL 覆盖其下位具体编码。

#### Scenario: 值集随包版本锁定

- **GIVEN** 一条规则锁定某 packageVersion
- **WHEN** 运行期展开其引用的值集
- **THEN** 系统 SHALL 使用该版本对应的 CodeSystem，不 SHALL 受后续字典升级影响。

### Requirement: 院内字典必须可对照到系统标准字典

系统 SHALL 复用 `engine.terminology` 域维护院内编码到标准编码的对照映射，不另起并行事实源。规则与路径条件中引用的编码 SHALL 以标准字典编码为准，运行期由上下文投影完成院内→标准归一。对照的医学语义匹配 SHALL 以同义词典、编码交叉表、来源权重（可选模型嵌入）为主，字符相似度仅作低权重召回，不得作为自动确认依据。

#### Scenario: 院内编码归一为标准编码

- **GIVEN** 第三方按字段契约传入院内诊断编码
- **WHEN** 上下文投影执行
- **THEN** 系统 SHALL 依据对照映射归一为标准编码供规则命中
- **AND** 未对照的编码 SHALL 被明示而非静默丢弃。

#### Scenario: 上线前对照覆盖检查

- **GIVEN** 一条规则引用了绑定院内字典的编码字段
- **WHEN** 提交发布门禁
- **THEN** 系统 SHALL 检查相关院内编码对照覆盖率并明示未对照项
- **AND** 存在关键未对照项时 SHALL 阻断上线。

### Requirement: 字段目录必须沉淀为对外数据接入契约

系统 SHALL 基于字段目录生成机器可读的对外字段契约（含资源类型、字段路径、数据类型、单位、绑定字典、是否必填）与中文接入说明，并随标准上下文包版本版本化。第三方按契约传入的数据 SHALL 经投影与对照归一后被规则/路径直接消费。

#### Scenario: 第三方按契约接入

- **GIVEN** 第三方依据当前 packageVersion 的字段契约组织数据
- **WHEN** 传入系统
- **THEN** 数据 SHALL 通过投影与字典对照归一
- **AND** 现有规则/路径 SHALL 无需为该第三方定制即可消费。
