# 规格增量：集成与运行时落地

> 日期：2026-06-03
> 状态：规划中
> 关联 OpenSpec：`pathway-rule-authoring-overhaul`；详见 `design-integration-landing.md`（附录 I）。
> 复用既有：`engine.integration`（适配器/Webhook）、`engine.context.ClinicalEvent*`（事件/投影/Outbox）、`engine.pkg`（专病包分发）。

## ADDED Requirements

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
