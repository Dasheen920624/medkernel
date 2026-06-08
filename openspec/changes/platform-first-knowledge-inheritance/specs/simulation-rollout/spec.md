# 发布前模拟与灰度（simulation-rollout）

## ADDED Requirements

### Requirement: 发布前影响模拟
平台发布或租户覆盖生效前 SHALL 提供只读影响预演：受影响组织/维度、与现状 diff、历史病例回放的决策变化、安全（LOCKED）与依赖完整性校验，确认后方可进入发布/评审。

#### Scenario: 覆盖前看清影响
- **WHEN** 机构提交一条剂量阈值覆盖
- **THEN** 系统返回受影响病例数与方向、是否触发 LOCKED，不落库

### Requirement: 灰度与可逆放量
高风险发布 SHALL 支持按机构清单/子树/床位比例/分批灰度放量，每批可观察并自动暂停，且可一键回退到上一钉点。

#### Scenario: 分批放量异常自动暂停
- **WHEN** 灰度某批次关键指标越阈值
- **THEN** 自动暂停放量并通知，不继续扩面

### Requirement: 批量与复用
系统 SHALL 支持覆盖模板、批量应用/撤销、跨机构克隆（带 diff），REGION INHERITABLE 覆盖一次配置下级默认复用。

#### Scenario: 模板批量应用
- **WHEN** 将"儿科剂量包"模板应用到多个机构儿科
- **THEN** 各目标生成对应覆盖，附统一预演
