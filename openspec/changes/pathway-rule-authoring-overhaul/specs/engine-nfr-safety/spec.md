# 规格增量：非功能、安全与硬化

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`；详见 `design-nfr-operations.md`（附录 H）。

## ADDED Requirements

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
