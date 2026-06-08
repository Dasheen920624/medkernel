# MedKernel

## Purpose

定义 MedKernel 集团医疗智能中枢的稳定产品身份、仓库边界、文档权威顺序、中文文档要求和变更规划要求。

## Requirements

### Requirement: 产品身份

系统 SHALL 将 MedKernel 呈现为面向医疗集团和多级医疗网络的集团医疗智能中枢。

#### Scenario: 新贡献者接手

- **GIVEN** 贡献者打开仓库
- **WHEN** 阅读根目录 README、`AGENTS.md` 或本规格
- **THEN** 能识别产品名称、业务使命、当前权威文档和中文协作要求。

### Requirement: 两层模型

系统 SHALL 按「基础底座 + 引擎服务能力」两层理解。

#### Scenario: 架构评审

- **GIVEN** 评审人需要理解平台结构
- **WHEN** 查看项目文档
- **THEN** 能区分组织、权限、审计、部署等共享底座能力，以及知识、字典、规则、路径、推荐、评估、随访、发布、嵌入和模型网关等引擎能力。

### Requirement: 第三方对接能力

系统 SHALL 将第三方系统对接统一归入适配器、标准上下文、临床事件、嵌入、回调、包发布同步、模型能力网关和审计证据链，不得让业务模块绕过引擎直接连接第三方系统。

#### Scenario: 对接院内系统

- **GIVEN** 医院需要接入 HIS、EMR、LIS、PACS、手麻、输血、护理、医保、公卫、区域平台或模型 Provider
- **WHEN** 团队设计接口或业务服务包
- **THEN** 应明确接入方向、字段映射、幂等、权限、审计、降级、证据和 `API 归类`，并复用当前底座与引擎能力。

#### Scenario: 第三方接口文档准入

- **GIVEN** 第三方接口进入联调
- **WHEN** 团队提交对接方案
- **THEN** 必须同时提交接入概览、OpenAPI 或事件 schema、字段映射、鉴权签名、幂等重试、回调、降级、边界和验收证据文档。

#### Scenario: 外部系统故障

- **GIVEN** 第三方系统、模型、Dify、图投影或回调目标不可用
- **WHEN** 引擎处理临床、质控、随访、包同步或证据任务
- **THEN** 医生主流程不得被阻断，系统应记录失败、重试、死信、人工补偿入口和可导出的审计证据。

### Requirement: 仓库边界

系统 SHALL 将实现边界划分为后端、前端、文档和部署四类区域。

#### Scenario: 代码导航

- **GIVEN** 开发者查找某个功能实现
- **WHEN** 查看仓库结构
- **THEN** 能在 `medkernel-backend/` 找到后端服务，在 `frontend/` 找到界面，在 `deploy/` 找到部署资产，在 `docs/` 找到当前权威文档。

### Requirement: 文档权威

系统 SHALL 以当前产品、实施和任务文档作为行为、约束、上线顺序和中文协作规则的权威来源。

#### Scenario: 规划变更

- **GIVEN** 团队准备规划新变更
- **WHEN** 查看项目上下文
- **THEN** 能通过权威文档确认范围、约束、当前执行顺序、中文书写要求和远程 `main` 合并门禁。

### Requirement: 可审计变更流

系统 SHALL 将拟议工作记录在 `openspec/changes/` 目录，将当前稳定行为保存在 `openspec/specs/`。已完成变更 SHALL 先把稳定行为同步至主规格，再按当前项目清洁策略清理活跃变更目录；只有明确需要保留阶段记录时，才归档到 `openspec/archive/`。

#### Scenario: 准备功能

- **GIVEN** 出现新的变更请求
- **WHEN** 团队开始 OpenSpec 规划
- **THEN** 应创建包含提案、设计、任务和规格增量的变更目录，并用中文描述实施与验证要求。

#### Scenario: 查阅已完成变更

- **GIVEN** 贡献者需要理解已完成变更
- **WHEN** 查看当前项目状态
- **THEN** 应以 `openspec/specs/` 中的稳定规格为准
- **AND** 如需追溯实施过程，应查看 Git 历史或显式保留的归档记录，不得把已清理的活跃变更目录当作当前任务来源。

以下要求定义规则与路径创作引擎的稳定行为。

### Requirement: 规则与路径必须共用统一条件求值内核

系统 SHALL 提供共享条件求值组件 `ConditionEvaluator`，以统一 `Group`（all/any/not + 叶子）文法对 canonical 上下文求值。规则 `when` 与路径边 `guard` SHALL 共用该内核，保证「可视化能构建的条件，规则与路径求值语义一致」。路径推进流程仍由 `PathwayProgressor` 决策，但边条件 SHALL 由统一内核求值，上下文来源 SHALL 与规则一致（canonical 上下文，而非独立 facts map）。

#### Scenario: 同一条件在规则与路径求值一致

- **GIVEN** 同一段 `Group` 条件分别用于规则 `when` 与路径边 `guard`
- **WHEN** 对同一 canonical 上下文快照求值
- **THEN** 两处 SHALL 得到一致的命中结果与证据链。

#### Scenario: 路径边使用统一条件树

- **GIVEN** 配置人员为路径边配置流转守卫
- **WHEN** 保存路径 DSL
- **THEN** 系统 SHALL 只持久化统一 `Group` 条件树
- **AND** SHALL NOT 写入独立扁平条件格式或并行 facts map。

### Requirement: 上下文模型必须覆盖过敏/不耐受资源

系统 SHALL 在 canonical 上下文模型中提供过敏/不耐受资源 `CanonicalAllergyIntolerance`（含编码、编码系统、致敏物质、类别、严重性、反应、发生时间、质量状态等），并纳入字段目录、对外数据契约与字典对照，以支持药物-过敏核查类规则。

#### Scenario: 药物-过敏核查可表达

- **GIVEN** 患者上下文含既往青霉素类过敏记录
- **WHEN** 配置「开具与已知过敏交叉反应药物时阻断」规则
- **THEN** 字段目录 SHALL 提供过敏资源字段供选择
- **AND** 规则 SHALL 能基于过敏资源与值集交叉反应判断命中。

### Requirement: 新增引擎工件必须满足确定性、租户隔离与可重放

规则与路径相关工件 SHALL 携带 `tenant_id` 并纳入租户隔离，携带 `package_version` 以支持版本锁定与回滚。规则/路径求值 SHALL 在给定快照、`package_version` 与 DSL 下确定性可重放，并复用平台求值幂等键与审计链。

#### Scenario: 求值可确定性重放

- **GIVEN** 一次求值的快照、packageVersion、DSL 与幂等键已留存
- **WHEN** 以相同输入重放
- **THEN** 系统 SHALL 产出一致的结果与证据链。

#### Scenario: 跨租户不可见

- **GIVEN** 租户 A 的字段目录/值集/规则/路径工件
- **WHEN** 租户 B 查询
- **THEN** 系统 SHALL NOT 返回租户 A 的工件。


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


### Requirement: 规则必须支持区间与参考范围比较

规则叶子条件 SHALL 支持区间比较（`between`/`not_between`）与参考范围比较（`above_ref`/`below_ref`/`within_ref`）。参考范围 SHALL 取自上下文字段（如 `CanonicalObservation.referenceRange`），不要求配置人员手填阈值。这些算子 SHALL 可在 L2 可视化结构化配置，L3 DSL 仅供专家核查。

#### Scenario: 按检验参考范围判断异常

- **GIVEN** 配置人员需要表达「检验结果高于该检验项参考范围上限」
- **WHEN** 在 L2 选择字段并选用 `above_ref` 算子
- **THEN** 系统 SHALL 基于该 Observation 的参考范围求值，无需手填阈值
- **AND** 参考范围缺失时 SHALL 按缺失数据策略处理而非误判。

### Requirement: 数值比较必须单位感知

涉及带单位字段的数值比较，系统 SHALL 在比较前按 UCUM 子集与字段元数据换算因子归一单位。单位不可换算时系统 SHALL 拒绝求值并明示，不得在单位不一致时静默比较。

#### Scenario: 跨单位比较自动归一

- **GIVEN** 字段单位为 mmol/L，配置的比较值单位为 mg/dL
- **WHEN** 执行比较
- **THEN** 系统 SHALL 按物质摩尔质量换算后比较
- **AND** 若两单位无换算关系，系统 SHALL 拒绝求值并提示单位不匹配。

### Requirement: 规则必须支持聚合、量词与时间窗表达式

规则表达式 SHALL 支持对资源集合的聚合与量词（`latest`/`first`/`max`/`min`/`avg`/`sum`/`count`，可带 `where` 过滤）以及时间窗 `over(window)`（相对就诊或评估时刻的相对时间）。多记录求值顺序 SHALL 确定（按 `eventTime`），保证可重放。

#### Scenario: 取最近一次指定检验值

- **GIVEN** 患者有多条肌酐 Observation
- **WHEN** 表达式为「最近一次 code=肌酐 的数值」
- **THEN** 系统 SHALL 按 eventTime 取最新一条求值
- **AND** 集合为空时 SHALL 返回 UNKNOWN 并按缺失策略处理。

#### Scenario: 时间窗内计数

- **GIVEN** 需要「24 小时内抗生素使用次数 > 2」
- **WHEN** 用 `count` + `where(类别=抗生素)` + `over(PT24H)`
- **THEN** 系统 SHALL 仅统计窗口内记录并比较。

### Requirement: 规则必须支持时序趋势与持续性算子

系统 SHALL 支持时序算子：`trend(rising|falling|stable, n)`（连续 n 次趋势）、`sustained(op,value,window)`（窗口内持续满足）、`delta(op,value,window)`（变化量）、`frequency(op,n,window)`（窗口内次数），以表达临床的连续监测判断。

#### Scenario: 连续上升趋势

- **GIVEN** 需要「肌酐连续 3 次上升」
- **WHEN** 选用 `trend(rising,3)`
- **THEN** 系统 SHALL 基于按时间排序的序列判定趋势
- **AND** 有效记录不足 3 次时 SHALL 返回 UNKNOWN。

### Requirement: 受控临床公式必须来自白名单函数库

系统 SHALL 提供受控临床公式库（如 eGFR、CrCl(Cockcroft-Gault)、BSA、BMI 等），每个公式声明入参字段、单位要求、适用人群与文献来源，并经金标准单测覆盖。公式 SHALL 通过白名单 `ClinicalFunctionRegistry` 注册，系统 SHALL NOT 允许运行期注入任意计算表达式或脚本。

#### Scenario: 用 eGFR 作为比较操作数

- **GIVEN** 配置「eGFR < 60」作为肾功能受限判断
- **WHEN** 选择受控公式 eGFR 并提供所需入参字段
- **THEN** 系统 SHALL 用注册公式计算结果参与比较
- **AND** 入参缺失或单位不符时 SHALL 拒绝计算并明示，不 SHALL 用默认值估算。

### Requirement: 缺失与陈旧数据必须按三值逻辑诚实处理

规则求值 SHALL 采用 TRUE/FALSE/UNKNOWN 三值逻辑：字段缺失、`QualityStatus=INVALID` 或超龄（is_stale）得 UNKNOWN；`QualityStatus=PARTIAL` 仍参与求值但 SHALL 在证据链标注。每条规则 SHALL 声明缺失数据策略（`UNKNOWN_AS_FALSE` 默认不误报，或 `UNKNOWN_AS_BLOCK` 高危场景缺数据即提示人工核查）。系统 SHALL NOT 以默认值或随机值伪造命中，缺失/陈旧/部分质量 SHALL 在证据链明示。

#### Scenario: 高危规则缺关键数据

- **GIVEN** 一条高危用药规则声明 `UNKNOWN_AS_BLOCK`，且关键检验缺失
- **WHEN** 求值
- **THEN** 系统 SHALL 产出人工核查动作而非静默放过
- **AND** 证据链 SHALL 标明缺失字段。


### Requirement: 规则条件树必须支持任意深度嵌套

规则创作界面 SHALL 支持「条件组（全部满足/任一满足）」与「叶子条件」的任意深度嵌套，能表达 `A 且 (B 或 C)` 等多层级临床判断。前端产出的嵌套 `when` 结构 SHALL 与后端 `RuleDslEvaluator` 的递归 `all`/`any` 求值完全一致，保证「可视化能画出的逻辑后端必能正确求值」。

#### Scenario: 配置多层级临床规则

- **GIVEN** 配置人员在 L2 条件树需要表达「全部满足：年龄 ≥ 65 且（肌酐 > 上限 或 eGFR < 下限）」
- **WHEN** 通过「+子条件组」在顶层组内新增一个「任一满足」组并放入两个叶子
- **THEN** 系统 SHALL 生成 `when:{all:[ {fact:age...}, {any:[{fact:creatinine...},{fact:egfr...}]} ]}`
- **AND** 该 DSL 经后端 `RuleDslEvaluator` 求值 SHALL 得到与可视化一致的命中结果。

#### Scenario: 条件树作为唯一 DSL

- **GIVEN** 配置人员保存一条规则
- **WHEN** 前端生成 DSL
- **THEN** 系统 SHALL 使用统一条件树表达所有叶子与条件组
- **AND** SHALL NOT 生成或保留并行扁平规则入口。

### Requirement: 规则条件字段必须来自上下文字段目录

叶子条件的字段路径 SHALL 从上下文字段目录选择，不得要求配置人员手敲裸路径。选中字段后系统 SHALL 自动带出数据类型并据此约束比较值类型；编码类字段的比较值 SHALL 从绑定的标准字典候选中选择。

#### Scenario: 选择字段自动带出类型

- **GIVEN** 配置人员在叶子条件选择字段「检验结果数值」
- **WHEN** 选中该字段
- **THEN** 比较值类型 SHALL 自动设为数值
- **AND** 算子候选 SHALL 限定为对数值有意义的集合（如大于/大于等于等）。

#### Scenario: 编码字段比较值来自标准字典

- **GIVEN** 叶子条件字段为「诊断编码」且绑定 ICD-10 字典
- **WHEN** 配置「属于集合」比较值
- **THEN** 比较值 SHALL 提供标准字典可搜索候选
- **AND** 不 SHALL 要求手敲编码字符串。

### Requirement: 规则创作必须有可解释性与复杂度护栏

系统 SHALL 限制条件树最大嵌套深度与单规则叶子总数，并禁止提交未解析的字段占位符，以保证规则可解释、可审计。

#### Scenario: 超出深度护栏

- **GIVEN** 配置人员尝试新增超过最大深度（默认 5 层）的子条件组
- **WHEN** 触发新增
- **THEN** 系统 SHALL 阻止并提示拆分规则，不 SHALL 生成不可解释的超深结构。

#### Scenario: 提交未解析字段

- **GIVEN** 条件树中存在仍为占位符 `<字段路径>` 的叶子
- **WHEN** 提交创建
- **THEN** 系统 SHALL 拦截并定位到该叶子，不 SHALL 提交。

### Requirement: L3 专家 DSL 为渐进式可选层且同步不打断流程

规则创作 SHALL 默认仅展示 L1 模板与 L2 可视化条件树；L3 专家 DSL SHALL 仅在显式开启专家模式后出现。「同步到 DSL」动作 SHALL 在后台更新 DSL 文本并轻量提示，不得强制切换到 L3 视图。L3 提交前 SHALL 仍能无损回填到 L2，禁止提交无法解释的裸 DSL。

#### Scenario: 同步不强制跳转

- **GIVEN** 配置人员在 L2 条件树点击「同步到 DSL」
- **WHEN** 同步完成
- **THEN** 系统 SHALL 保持当前 L2 视图并提示同步成功
- **AND** 不 SHALL 自动切换到 L3 专家视图。

#### Scenario: 未开专家模式时隐藏 L3

- **GIVEN** 专家模式开关处于关闭
- **WHEN** 查看创作界面
- **THEN** L3 DSL 标签 SHALL 不可见
- **AND** 同步动作 SHALL 不触发 L3 弹出。


### Requirement: 规则动作必须分级且对齐卡片式输出

规则命中动作 SHALL 支持按严重度分级，并以卡片式结构输出（summary、detail、indicator(info/warning/critical)、source(指南来源+证据等级)、suggestions(可执行建议)、overrideReasons(可选越权理由集合)），对齐 CDS Hooks Card 语义。动作类型 SHALL 覆盖提示/提醒/强提醒/阻断/建议医嘱/自动留痕。高危动作 SHALL 要求医师确认。

#### Scenario: 不同严重度产出不同动作

- **GIVEN** 一条规则在不同阈值下应产出提醒或阻断
- **WHEN** 命中达到阻断阈值
- **THEN** 系统 SHALL 输出 BLOCK 卡片并要求医师确认
- **AND** 卡片 SHALL 携带指南来源与证据等级。

### Requirement: 系统必须管理规则交互与告警疲劳

系统 SHALL 支持规则优先级、抑制（`suppressedBy`）、窗口内去重，并在发布前静态检测互斥/重叠规则冲突。被阻断/强提醒越权时系统 SHALL 强制捕获越权理由，并将越权率回流为质量指标。

#### Scenario: 高阶规则抑制低阶提醒

- **GIVEN** 同一触发点已产生 BLOCK，且存在一条更低阶 REMIND
- **WHEN** 编排执行
- **THEN** 系统 SHALL 抑制低阶提醒避免告警叠加。

#### Scenario: 发布前检出冲突规则

- **GIVEN** 两条规则对同一字段设置相反阈值动作
- **WHEN** 提交发布
- **THEN** 系统 SHALL 在发布门禁明示冲突并要求处置。

#### Scenario: 越权必须留痕

- **GIVEN** 医师越权一条 BLOCK 规则
- **WHEN** 执行越权
- **THEN** 系统 SHALL 强制要求选择/填写越权理由并审计留存。

### Requirement: 规则必须声明适用域并先判定再求值

每条规则 SHALL 声明适用域：人群（纳入/排除标准）、组织（集团/医院/科室）、场景（住院/门诊/急诊/随访）、生效期与灰度比例。运行期系统 SHALL 先判定适用域，不适用则不求值、不触发。

#### Scenario: 不适用人群不触发

- **GIVEN** 规则适用域排除妊娠人群
- **WHEN** 对妊娠患者评估
- **THEN** 系统 SHALL 跳过该规则不产生动作。

### Requirement: 规则必须遵循临床知识治理生命周期

系统 SHALL 在草稿与发布之间扩展临床知识治理：同行评审 → 临床委员会会签（高危规则多签）→ 影子/静默运行（monitor-only，只记录不动作）→ 灰度 → 全量 → 监测 → 退役。退役 SHALL 封存而非删除，保证医legal 可追溯。

#### Scenario: 高危规则需会签与影子验证

- **GIVEN** 一条高危规则准备上线
- **WHEN** 推进发布
- **THEN** 系统 SHALL 要求临床委员会多签
- **AND** SHALL 先在影子模式只记录不动作，达标后才进入灰度。

### Requirement: 规则上线必须可历史回测并监测漂移

系统 SHALL 支持对历史脱敏快照集回测规则，产出灵敏度/特异度等指标，并在上线后监测命中率漂移。回测与监测 SHALL 基于真实快照，不得使用伪造数据。

#### Scenario: 上线前回测

- **GIVEN** 已有标注金标准的历史快照集
- **WHEN** 对草稿规则回测
- **THEN** 系统 SHALL 报告灵敏度/特异度与误报样例供评审。


### Requirement: 路径节点与边编码必须自动生成且可改

路径 L2 节点画布 SHALL 为节点与流转边自动生成默认编码（如 `N1/N2`、`E1/E2`），配置人员可手改但不必从零手填。系统 SHALL 即时校验编码唯一性。

#### Scenario: 新增节点自动带编码

- **GIVEN** 配置人员在节点画布点击「添加节点」
- **WHEN** 节点创建
- **THEN** 系统 SHALL 自动填入唯一节点编码
- **AND** 配置人员可修改该编码，修改为重复值时 SHALL 即时报错。

### Requirement: 流转边必须通过选择已建节点连接

边的源节点与目标节点 SHALL 通过下拉从当前已建节点中选择（展示「名称(编码)」），不得要求手敲节点编码。起始节点 SHALL 同样通过下拉从已建节点选择。

#### Scenario: 边连接已建节点

- **GIVEN** 配置人员已创建节点「评估」与「诊断」
- **WHEN** 新增一条流转边
- **THEN** 源/目标节点 SHALL 只能从已建节点下拉中选择
- **AND** 不 SHALL 出现因手敲编码不一致导致的断链。

#### Scenario: 删除被引用节点

- **GIVEN** 某节点已被一条边引用为目标
- **WHEN** 配置人员删除该节点
- **THEN** 系统 SHALL 提示其关联边将失效并要求确认或同步处理。

### Requirement: 路径创建校验必须前移且明确

路径创建的拓扑与时窗校验 SHALL 在前端提交前以字段级或画布级提示呈现失败原因，不得仅在后端返回「创建失败」而不指明原因。设置时窗的节点 SHALL 即时要求填写时钟指标编码。

#### Scenario: 时窗未配指标

- **GIVEN** 某节点设置了时窗分钟但未填时钟指标编码
- **WHEN** 配置人员尝试提交
- **THEN** 系统 SHALL 即时标红该字段并阻止提交
- **AND** 不 SHALL 返回无定位信息的「创建路径模板失败」。

#### Scenario: 拓扑不闭环

- **GIVEN** 路径存在孤立节点、断链边或缺少终止节点
- **WHEN** 提交前
- **THEN** 系统 SHALL 在画布级明示具体问题节点/边
- **AND** 后端校验 SHALL 仍作为最终门禁保留，防止前端被绕过。

### Requirement: 路径边条件应复用规则条件构建器

流转边的条件 SHALL 复用规则的递归条件构建器进行可视化配置，产出与 `PathwayProgressor` 对齐的条件结构，替代手写 JSON 文本。

#### Scenario: 可视化配置边条件

- **GIVEN** 配置人员需要为「条件流转」边设置触发条件
- **WHEN** 编辑该边条件
- **THEN** 系统 SHALL 提供与规则一致的可视化条件构建器
- **AND** 产出的条件 SHALL 能被 `PathwayProgressor` 正确评估。


### Requirement: 路径必须支持真实入径与出径人群标准

路径的入径标准（entryCriteria）与出径标准（exitCriteria）SHALL 支持真实的纳入/排除条件树（复用规则递归条件构建器），不得为空占位。入径 SHALL 支持自动建议或人工确认。

#### Scenario: 按纳入排除标准入径

- **GIVEN** 路径定义了纳入标准（特定诊断）与排除标准（特定禁忌）
- **WHEN** 评估某患者上下文快照
- **THEN** 系统 SHALL 依据标准判断是否建议入径
- **AND** 排除标准命中的患者 SHALL NOT 被建议入径。

### Requirement: 路径必须支持阶段、里程碑与天序结构

路径 SHALL 支持在节点之上组织阶段（Phase）与里程碑（Milestone），并提供天序（如术后第 N 天）视图。里程碑 SHALL 携带预期完成时点与达成判定。

#### Scenario: 天序里程碑视图

- **GIVEN** 一条外科路径包含术前/术中/术后阶段
- **WHEN** 查看路径
- **THEN** 系统 SHALL 按阶段与天序展示节点与里程碑及其达成状态。

### Requirement: 路径必须支持富节点类型

路径节点类型 SHALL 扩展支持：决策点（DECISION，多守卫边分流）、并行（PARALLEL，fork/join 并发与汇合）、等待/计时（WAIT/TIMER）、子路径（SUBPATHWAY）、人工闸门（MANUAL_GATE，需角色确认推进）、医嘱套餐（ORDER_SET，绑定可下达医嘱/护理套餐）。各节点语义 SHALL 能被 `PathwayProgressor` 正确推进。

#### Scenario: 决策点按守卫分流

- **GIVEN** 一个 DECISION 节点有两条带守卫条件的出边
- **WHEN** 患者状态满足其中一条守卫
- **THEN** 系统 SHALL 推进到对应分支
- **AND** 守卫均不满足时 SHALL 按默认边或停留并明示。

#### Scenario: 并行汇合

- **GIVEN** 一个 PARALLEL fork 派生多条并发活动
- **WHEN** 所有并发活动到达 join
- **THEN** 系统 SHALL 在汇合后才继续推进。

### Requirement: 路径必须支持临床时钟与 SLA 升级

里程碑/时窗节点 SHALL 声明目标/最早/最晚时限与基准事件（入院、手术开始等），支撑「门球时间 <90min」「抗生素入院 1h 内」等。超时 SHALL 分级升级（提醒→上报→质控记录），并与时钟指标绑定。

#### Scenario: 时限超时升级

- **GIVEN** 节点声明「入院后 1 小时内给药」
- **WHEN** 超过时限未达成
- **THEN** 系统 SHALL 触发分级升级并记录质控时钟指标。

### Requirement: 路径流转边必须以守卫条件分支

流转边的守卫条件 SHALL 复用规则递归条件构建器进行可视化配置，产出与 `PathwayProgressor` 对齐的条件结构，替代手写 JSON。多守卫边 SHALL 按优先级与守卫求值确定推进。

#### Scenario: 可视化配置守卫边

- **GIVEN** 配置人员为条件流转边设置触发条件
- **WHEN** 编辑该边
- **THEN** 系统 SHALL 提供与规则一致的可视化条件构建器
- **AND** 产出条件 SHALL 能被 `PathwayProgressor` 正确评估。

### Requirement: 路径必须支持变异管理

患者偏离路径时系统 SHALL 捕获变异：分类（临床/系统/患者/家属）、原因码、责任角色、再入径或终止决策。变异统计 SHALL 可回流为路径优化与质控指标。

#### Scenario: 偏离路径产生变异

- **GIVEN** 患者实际处置偏离当前路径节点
- **WHEN** 记录偏离
- **THEN** 系统 SHALL 生成带分类与原因的变异记录
- **AND** SHALL 支持再入径或终止决策。

### Requirement: 路径节点必须声明角色并生成工作清单

路径节点 SHALL 声明 RACI 角色（Responsible/Accountable/Consulted/Informed）。推进时系统 SHALL 为对应角色生成工作清单，对接现有待办中心。

#### Scenario: 推进生成角色待办

- **GIVEN** 节点声明责任角色为主管护师
- **WHEN** 路径推进到该节点
- **THEN** 系统 SHALL 在待办中心为该角色生成任务。

### Requirement: 路径必须支持多级模板继承与差异合并

路径模板 SHALL 支持 STANDARD→HOSPITAL→DEPARTMENT→SPECIALTY 四级继承：下级可覆盖/新增/禁用上级节点。系统 SHALL 提供继承差异（diff）视图与合并解析，避免重复维护。

#### Scenario: 科室级覆盖标准节点

- **GIVEN** 科室级模板覆盖标准模板的某节点时窗
- **WHEN** 查看合并结果与差异
- **THEN** 系统 SHALL 正确合并并以 diff 视图明示覆盖项。

### Requirement: 路径必须绑定结局指标并支持实例状态机与多路径协调

路径/里程碑 SHALL 可绑定质量与结局指标（LOS、再入院率、并发症、成本等，对接 `EvaluationIndicator`）。系统 SHALL 维护患者路径实例状态机（当前/已完成/待办逾期/变异/完成退出），支持队列回放与历史快照时光机仿真。一名患者并发多路径时系统 SHALL 检测路径间医嘱/时窗冲突并提示协调，且 SHALL NOT 自动修改医嘱。

#### Scenario: 多路径冲突仅提示

- **GIVEN** 患者同时在两条路径且存在医嘱时窗冲突
- **WHEN** 系统检测到冲突
- **THEN** 系统 SHALL 提示需人工协调
- **AND** SHALL NOT 自动改写任一路径的医嘱。


### Requirement: 条件与路径必须提供自然语言预览

规则条件树与路径守卫 SHALL 实时渲染为可读中文句子，与可视化和 DSL 并排展示，使不懂代码的临床人员可核对逻辑正误。预览 SHALL 反映嵌套逻辑、算子、单位、值集、动作与来源，不得丢失语义。

#### Scenario: 临床专家核对复杂规则

- **GIVEN** 一条含嵌套 all/any 与 eGFR 公式的规则
- **WHEN** 查看自然语言预览
- **THEN** 系统 SHALL 输出准确反映该逻辑的中文句子
- **AND** 与最终求值语义一致。

### Requirement: 创作必须提供向导与智能默认以降低门槛

系统 SHALL 提供按临床原型（阈值提醒/药物相互作用/药物过敏/剂量核查/危急值/医保核查等）驱动的创建向导，并在选择字段后智能带出算子、值类型、单位与默认动作。高级项（单位换算、缺失策略、适用域、治理）SHALL 默认折叠，简单场景无需触碰。

#### Scenario: 按原型向导建规则

- **GIVEN** 用户选择「危急值回报」原型
- **WHEN** 按槽位填入检验项、阈值与时限
- **THEN** 系统 SHALL 生成结构正确的草稿规则
- **AND** 未展开高级项时仍能用合理默认提交。

### Requirement: 创作必须支持即配即试与就地定位校验

系统 SHALL 允许在编辑过程中选择真实脱敏快照一键试运行，就地显示命中/未命中与证据链；校验错误 SHALL 以字段级或画布级就地呈现并可跳转定位。即配即试 SHALL NOT 绕过后端校验与门禁，且 SHALL NOT 使用伪造数据。

#### Scenario: 编辑中即时验证

- **GIVEN** 用户正在编辑规则并选定一份 ACTIVE 真实快照
- **WHEN** 点击即配即试
- **THEN** 系统 SHALL 就地返回命中结果与证据
- **AND** 缺失/单位换算/公式输入 SHALL 在证据中可见。

### Requirement: 规则必须支持参数化配置

系统 SHALL 支持参数化规则模板：把阈值、值集、目标时限、适用范围等暴露为参数，普通用户仅填参数表单即可生成可用规则而不触碰逻辑结构。参数 schema SHALL 随 DSL 定义，实例参数值 SHALL 落库且可审计。

#### Scenario: 仅填参数生成规则

- **GIVEN** 一个「危急值回报」参数化模板（参数=值集/阈值/时限）
- **WHEN** 用户仅填三个参数
- **THEN** 系统 SHALL 生成完整可用的草稿规则。

### Requirement: 系统必须支持批量创建、导入导出与分发

系统 SHALL 支持规则/路径/值集/字段目录/字典对照的批量导入导出（复用知识包离线导入与校验），支持「模板 + 参数表」批量生成，支持批量发布/下线/启停（先出聚合影响摘要），并支持把知识包批量分发到多组织（复用同步目标）。批量高危操作 SHALL 逐条确认，禁止一键批量确认高危。批量作业 SHALL 有进度、结果与审计，可回滚。

#### Scenario: 模板加参数表批量生成

- **GIVEN** 一个参数化模板与一张 N 行参数表
- **WHEN** 执行批量生成
- **THEN** 系统 SHALL 生成 N 条草稿规则并返回逐条结果
- **AND** 失败项 SHALL 明示原因不影响成功项。

#### Scenario: 批量分发到多家医院

- **GIVEN** 一个含规则与路径的知识包与多个分发目标
- **WHEN** 执行批量分发
- **THEN** 系统 SHALL 复用同步目标分发并对不可达目标诚实降级
- **AND** SHALL 记录每个目标的结果与回滚凭据。

#### Scenario: 批量确认高危映射被拒

- **GIVEN** 一批字典对照候选含高危项
- **WHEN** 尝试一键批量确认
- **THEN** 系统 SHALL 拒绝并要求高危项逐条二次确认。

### Requirement: 资产必须可复用、可编目、可克隆

系统 SHALL 提供条件片段库：把命名条件组在多条规则 `when` 与路径 `guard` 中按引用复用，片段更新经影响分析向引用处传播；用户可选择「引用（联动）」或「拷贝为本地副本（脱钩）」。系统 SHALL 提供统一资产库（规则/路径/片段/值集/医嘱套餐/动作卡片/子路径）支持分类、标签、搜索、收藏，并支持任意资产克隆/另存为。集团级权威资产 SHALL 可被下级组织订阅或克隆后本地覆盖。

#### Scenario: 复用条件片段

- **GIVEN** 已保存命名片段「肾功能受限」
- **WHEN** 在新规则与某路径守卫中按引用复用
- **THEN** 两处 SHALL 引用同一片段定义
- **AND** 片段更新 SHALL 经影响分析提示受影响资产。

#### Scenario: 克隆资产作为起点

- **GIVEN** 一条已有规则
- **WHEN** 用户克隆/另存为
- **THEN** 系统 SHALL 生成可独立编辑的新草稿，不影响原资产。


### Requirement: 院内系统接入必须经适配器且健康状态诚实

院内异构系统 SHALL 经 `IntegrationAdapter` 接入（声明协议类型与配置），健康自检 SHALL 返回真实状态（`NOT_CONNECTED`/`MISCONFIGURED`/`HEALTHY`）与真实 RTT。系统 SHALL NOT 伪造连通或心跳。回写院内系统不可达时 SHALL 诚实降级，不假成功。

#### Scenario: 未接真实连接器

- **GIVEN** 一个尚未接入真实连接器的适配器
- **WHEN** 执行健康自检
- **THEN** 系统 SHALL 返回 `NOT_CONNECTED`
- **AND** SHALL NOT 返回伪造的成功状态或随机 RTT。

### Requirement: 引擎求值必须由临床事件触发并消费归一快照

规则、路径、推荐求值 SHALL 由临床事件（`ClinicalEventType`，对应 CDS Hooks 触发点）经 `ClinicalEventEngineDispatcher` 分发触发，且 SHALL 消费经投影归一后的 canonical 快照。入站 SHALL 幂等；求值前 SHALL 完成字典对照归一与质量门（`QualityStatus=INVALID` 拒绝），不得带病求值。

#### Scenario: 开医嘱触发规则求值

- **GIVEN** 院内下达医嘱产生 `ORDER` 事件
- **WHEN** 事件投影归一为快照并分发
- **THEN** 规则引擎 SHALL 在该快照上求值并产出动作卡片
- **AND** 院内编码 SHALL 已归一为标准编码后再求值。

#### Scenario: 重复上报幂等

- **GIVEN** 同一临床事件被重复上报
- **WHEN** 入站处理
- **THEN** 系统 SHALL 依幂等键避免重复快照与重复动作。

### Requirement: 引擎产出必须经可靠分发流向下游且可审计

规则动作、路径推进/工作清单、推荐 SHALL 经 `Outbox` 可靠分发到下游（待办中心/通知中心/临床提醒治理/质控驾驶舱/回写），分发 SHALL 可重放，全链路 SHALL 携带 `trace_id`/`package_version` 可审计。

#### Scenario: 分发可重放

- **GIVEN** 一次引擎产出已写入 Outbox
- **WHEN** 下游消费失败后重放
- **THEN** 系统 SHALL 依事件可靠重放且不产生重复副作用。

### Requirement: 专病包必须作为规则/路径/值集的复用与分发载体

专病诊疗场景 SHALL 以 `KnowledgePackage`（专病包）封装路径模板、规则集、值集、字段绑定与受控公式，支持跨组织分发（复用 `SyncTarget`）、版本锁定与回滚；下级组织 SHALL 可订阅或克隆后本地覆盖，且继承与版本 SHALL 可追溯。

#### Scenario: CKD 专病包跨院分发与本地覆盖

- **GIVEN** 集团发布「CKD 专病」知识包
- **WHEN** 下级医院订阅并本地覆盖院内剂量阈值
- **THEN** 系统 SHALL 保留继承关系与版本可追溯
- **AND** SHALL 支持回滚到分发前状态。


### Requirement: 事件触发求值必须满足时延预算并诚实降级

事件触发（如 order-sign / patient-view）的规则求值 SHALL 满足可配置时延预算（默认 p95 ≤ 800ms、硬超时 ≤ 2s）。超时或上下文/术语服务不可用时系统 SHALL 诚实降级：返回「求值不可用」并按规则缺失策略处理，SHALL NOT 静默放过，SHALL NOT 阻断医生正常操作而不提示。

#### Scenario: 求值超时

- **GIVEN** 一次 order-sign 触发的求值超过硬超时
- **WHEN** 超时发生
- **THEN** 系统 SHALL 返回求值不可用状态
- **AND** 高危 `UNKNOWN_AS_BLOCK` 规则 SHALL 产出人工核查提示而非静默放过。

### Requirement: 创作治理必须强制职责分离

系统 SHALL 对创作生命周期实施基于真实角色的职责分离：高危规则的会签人 SHALL 不同于作者，发布人 SHALL 不为该高危规则的唯一会签人；运行期 BLOCK 越权 SHALL 强制捕获理由。所有创作、治理、批量与越权动作 SHALL 留不可篡改审计并跨租户隔离。

#### Scenario: 作者不能自我会签高危规则

- **GIVEN** 某用户创建了一条高危规则
- **WHEN** 同一用户尝试作为唯一会签人通过committee
- **THEN** 系统 SHALL 拒绝并要求独立会签人。

### Requirement: 测试与回测数据必须为真实脱敏数据

即配即试、批量测试、回测与影子运行所用快照 SHALL 为真实脱敏数据；系统 SHALL NOT 内置示例病例或伪造数据，PHI SHALL NOT 进入未授权环境。

#### Scenario: 试运行需真实快照

- **GIVEN** 用户进行即配即试
- **WHEN** 未提供真实脱敏快照
- **THEN** 系统 SHALL 拒绝并提示读取真实快照，不 SHALL 用示例数据替代。

### Requirement: 复用与路径结构必须做环与边界检测

系统 SHALL 在保存/发布时检测条件片段循环引用、子路径循环引用与路径成环，并拒绝非法结构；运行期 SHALL 设最大步数护栏防止无限推进。值集展开超上限、单位不可换算、公式入参非法/除零等 SHALL 以确定性失败语义处理（UNKNOWN + 明确错误码），SHALL NOT 估算或随机放过。

#### Scenario: 片段循环引用被拒

- **GIVEN** 片段 A 引用 B、B 又引用 A
- **WHEN** 保存
- **THEN** 系统 SHALL 检测环并拒绝保存。

#### Scenario: 体重缺失的按体重剂量

- **GIVEN** dosePerKg 公式所需体重缺失或为 0
- **WHEN** 求值
- **THEN** 系统 SHALL 返回 UNKNOWN 并给出错误码证据，不 SHALL 除零或估算。

### Requirement: 引用资产与包版本必须保持一致性

规则/路径引用的字段目录、值集、CodeSystem、条件片段、受控公式 SHALL 与其 `package_version` 一致；发布门禁与运行期 SHALL 校验版本一致，跨版本引用 SHALL 拒绝。引用型复用资产变更 SHALL 经影响分析明示受影响资产。

#### Scenario: 跨版本引用被拒

- **GIVEN** 一条锁定某 packageVersion 的规则引用了另一版本的值集
- **WHEN** 发布或运行期校验
- **THEN** 系统 SHALL 拒绝并提示版本不一致。
