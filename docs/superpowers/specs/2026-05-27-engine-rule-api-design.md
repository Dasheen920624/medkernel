# 规则引擎 API 设计

## 1. 决策结论

`GA-ENG-API-05` 采用“受控规则资产 + 确定性执行 + 可解释日志”的后端合同。首版建设规则定义、版本、测试用例、发布门禁、执行日志、仿真和解释 API，为后续 `GA-ENG-RULE-01` 可视化规则编辑器、规则包管理和临床嵌入提示提供稳定底座。

首版不追求一次性实现完整医学语义规则引擎，而是实现一个可验证、可审计、可回放的最小闭环：

1. 规则以 JSON DSL 保存，版本化管理。
2. 发布前必须具备阳性、阴性、边界、冲突四类测试用例，并全部通过仿真。
3. 执行只返回命中、风险、建议动作、解释和证据引用，不自动诊断、不自动开医嘱、不自动写病历。
4. 所有仿真、发布和真实执行都记录 traceId、租户、规则版本、输入摘要和解释快照。

## 2. 目标

- 提供规则定义、版本、测试、发布、执行、解释的 REST API。
- 建立五方言迁移表：`rule_definition`、`rule_version`、`rule_test_case`、`rule_execution_log`。
- 复用现有 `rule.read`、`rule.write`、`rule.publish` 权限和租户数据范围。
- 复用统一 `ApiResult`、错误码、审计事件、状态历史和诊断响应。
- 提供确定性 JSON DSL 执行器，覆盖首批常用条件算子。
- 为规则命中统计、临床嵌入提示、MCP/CLI 规则解释预留执行事实合同。

## 3. 非目标

- 不实现前端可视化规则编辑器。
- 不实现完整医学术语、路径、医保、语义、时序推理算子库。
- 不接入大模型生成规则或自动改写规则。
- 不执行外部回调、任务派发或消息通知，只返回结构化动作。
- 不把规则结果直接写入病历、医嘱或诊断。
- 不新增绕过权限、审计和发布门禁的调试接口。

## 4. 业务边界

### 4.1 规则生命周期

| 状态 | 含义 | 允许动作 |
|---|---|---|
| `DRAFT` | 草稿，可编辑和补测试用例 | 修改、仿真、提交发布 |
| `PUBLISHED` | 内容已通过审核并写保护，尚未证明运行生效 | 查看影响、全量激活、回滚 |
| `OFFLINE` | 已下线，不再参与默认执行 | 查询、解释历史执行 |
| `ARCHIVED` | 归档，仅保留审计与追溯 | 查询历史 |

规则运行状态以统一资产版本为唯一权威；只有对应统一版本为 `ACTIVE` 时才参与执行。

### 4.2 发布门禁

规则版本发布必须同时满足：

- `sourceRef` 不为空，用于说明指南、制度、路径、医保或院内规范来源。
- DSL 可解析，且至少包含 `trigger`、`when`、`then`、`explain`。
- 测试用例覆盖 `POSITIVE`、`NEGATIVE`、`BOUNDARY`、`CONFLICT` 四类。
- 全部测试用例仿真结果与期望一致。
- 高风险动作返回 `requiresPhysicianConfirmation=true`。

详细规范 §4.7 提到的“缺失值”测试在首版作为边界或冲突测试的输入场景表达；后续可在规则编辑器任务中拆成独立 `MISSING` 门禁类型。

### 4.3 医疗安全

- 规则执行结果是提示或阻断建议，不是医疗最终结论。
- `BLOCK`、`STRONG_REMINDER`、`RECOMMEND_NEXT` 等高影响动作必须要求医师确认。
- 输入上下文按最小必要原则处理；执行日志只保存摘要和解释快照，不保存完整患者上下文。
- 发布、执行、解释均进入审计链。

## 5. API 合同

| API | 权限 | 用途 |
|---|---|---|
| `POST /api/v1/engine/rule/rules` | `rule.write` | 创建规则定义和初始草稿版本 |
| `GET /api/v1/engine/rule/rules` | `rule.read` | 按状态、类型、风险级别分页查询 |
| `GET /api/v1/engine/rule/rules/{ruleId}` | `rule.read` | 查看规则定义、当前版本和测试覆盖 |
| `POST /api/v1/engine/rule/rules/{ruleId}/test-cases` | `rule.write` | 新增规则测试用例 |
| `POST /api/v1/engine/rule/rules/{ruleId}/test` | `rule.write` | 执行当前版本全部测试用例 |
| `POST /api/v1/engine/rule/rules/{ruleId}/simulate` | `rule.write` | 对指定上下文或测试用例仿真 |
| `GET /api/v1/engine/rule/rules/{ruleId}/impact` | `rule.read` | 查看发布前影响分析 |
| `POST /api/v1/engine/rule/rules/{ruleId}/publish` | `rule.publish` | 执行发布门禁并发布 |
| `POST /api/v1/engine/rule/rules/evaluate` | `rule.read` | 按触发点和上下文执行统一版本已激活规则 |
| `GET /api/v1/engine/rule/rules/executions/{executionId}/explain` | `rule.read` | 查看一次执行的客户面解释 |

## 6. 数据模型

### 6.1 `rule_definition`

规则稳定身份，保存租户、规则编码、名称、类型、模式、风险级别、当前状态和当前版本引用。

关键字段：`rule_id`、`tenant_id`、`rule_code`、`name`、`rule_type`、`authoring_mode`、`risk_level`、`status`、`active_version_id`、`package_version`、`created_at`、`updated_at`、`trace_id`。

### 6.2 `rule_version`

规则版本内容，保存 JSON DSL、解释模板、来源、版本号和发布人。

关键字段：`version_id`、`tenant_id`、`rule_id`、`version_no`、`source_ref`、`change_summary`、`dsl_json`、`explanation_json`、`status`、`published_at`、`published_by`、`trace_id`。

### 6.3 `rule_test_case`

规则发布门禁用例，保存输入、期望和最近一次执行结果。

关键字段：`case_id`、`tenant_id`、`rule_id`、`version_id`、`case_type`、`input_payload`、`expected_hit`、`expected_severity`、`expected_action_code`、`last_hit`、`last_status`、`last_run_at`。

### 6.4 `rule_execution_log`

规则真实执行和仿真执行事实，保存摘要、命中结果、动作和解释。

关键字段：`execution_id`、`tenant_id`、`rule_id`、`version_id`、`trigger_point`、`event_id`、`input_digest`、`hit`、`severity`、`actions_json`、`explanation_json`、`status`、`error_code`、`executed_at`、`trace_id`。

## 7. JSON DSL 首版

### 7.1 结构

```json
{
  "trigger": "order-sign",
  "when": {
    "all": [
      {"fact": "patient.age", "operator": "gte", "value": 18},
      {"fact": "order.drugClass", "operator": "equals", "value": "ANTICOAGULANT"}
    ]
  },
  "then": [
    {
      "actionCode": "STRONG_REMINDER",
      "severity": "HIGH",
      "message": "抗凝用药需确认出血风险",
      "requiresPhysicianConfirmation": true
    }
  ],
  "explain": {
    "title": "抗凝风险提示",
    "reason": "患者年龄和医嘱药品类别满足规则条件",
    "sourceRef": "院内抗凝用药管理规范 2026"
  }
}
```

### 7.2 条件算子

首版算子只覆盖确定性、易验证场景：

| 算子 | 含义 |
|---|---|
| `exists` | 字段存在且非空 |
| `equals` / `not_equals` | 等于 / 不等于 |
| `contains` | 数组或字符串包含 |
| `gt` / `gte` / `lt` / `lte` | 数值比较 |
| `in` / `not_in` | 枚举集合判断 |

输入字段路径使用点号路径，例如 `patient.age`。缺失字段不抛内部异常，而是产生未命中或门禁失败原因。

## 8. 错误码

新增错误码：

| 错误码 | HTTP | 含义 |
|---|---|---|
| `ENG-RULE-001` | 400 | 规则 DSL 校验失败 |
| `ENG-RULE-002` | 404 | 规则不存在 |
| `ENG-RULE-003` | 404 | 规则版本不存在 |
| `ENG-RULE-004` | 409 | 发布门禁失败 |
| `ENG-RULE-005` | 500 | 规则执行失败 |
| `ENG-RULE-006` | 409 | 当前规则状态不允许该操作 |

## 9. 验收标准

- H2、PostgreSQL、Oracle、达梦、人大金仓迁移版本均增加到 V11。
- 后端单元测试覆盖 DSL 命中、未命中、缺失字段、发布门禁、执行日志和诊断。
- MockMvc 安全测试覆盖读、写、发布权限。
- `mvn -f medkernel-backend/pom.xml test` 通过；无 Docker 环境下已有数据库烟测可按既有机制跳过。
- `docs/backlog.md` 将 `GA-ENG-API-05` 标记为完成，并保留 `GA-ENG-RULE-01` 为后续可视化规则任务。
