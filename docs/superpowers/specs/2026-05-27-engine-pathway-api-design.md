# 路径引擎 API 设计

## 1. 决策结论

`GA-ENG-API-06` 采用“专病路径资产 + 患者路径实例 + 确定性推进 + 关键时钟”的后端合同。首版建设专病包、专病画像、路径模板、节点、边、患者路径、变异、关键时钟和指标绑定的 API 与数据底座，为后续 `GA-ENG-PATH-01` 可视化路径画布、复杂分型分支、路径仿真工作台和临床嵌入式提醒提供稳定基础。

首版不把路径引擎做成医嘱或病历来源，而是实现一个可审计、可解释、可回放的最小运行闭环：

1. 路径模板以结构化节点和边保存，发布后才能创建患者路径实例。
2. 患者路径实例只记录当前节点、状态、变异和关键时钟，不覆盖原始病历、检查、检验或医嘱事实。
3. 节点推进只返回下一节点、变异、随访或完成状态，不自动诊断、不自动开医嘱、不自动写病历。
4. 关键时钟记录开始、到期、完成、超时和关联质控指标，作为路径执行事实。
5. 仿真和真实推进都输出可诊断 traceId、路径版本、节点轨迹和安全提示。

## 2. 目标

- 提供专病包、路径模板、发布、仿真、患者入径、节点推进、变异、关键时钟和诊断解释 API。
- 建立五方言迁移表：`specialty_package`、`specialty_profile`、`pathway_template`、`pathway_node`、`pathway_edge`、`patient_pathway`、`pathway_variance`、`clinical_clock`、`specialty_metric_binding`。
- 复用现有 `pathway.read`、`pathway.write`、`pathway.publish` 权限和租户数据范围。
- 复用统一 `ApiResult`、`PageResponse`、错误码、审计事件、状态历史和诊断响应。
- 提供确定性路径推进器，覆盖顺序节点、默认边、条件边、变异边和退出节点。
- 为路径画布、质控指标、随访延续、路径执行统计和 MCP/CLI 解释预留事实合同。

## 3. 非目标

- 不实现前端路径画布、拖拽编排或可视化仿真工作台。
- 不实现完整医学分型规则、复杂时序推理、自动入径推荐或自动医嘱生成。
- 不接入外部随访系统、消息通知、任务派发或大模型生成路径。
- 不把路径节点结果直接写入病历、医嘱、诊断、费用或医保结算。
- 不新增绕过权限、审计、发布门禁和租户隔离的调试接口。
- 不实现跨模板灰度、回滚、批量迁移患者路径或多版本并行运行。

## 4. 业务边界

### 4.1 路径模板生命周期

| 状态 | 含义 | 允许动作 |
|---|---|---|
| `DRAFT` | 草稿，可编辑专病包、节点、边、准入和退出条件 | 修改、仿真、发布 |
| `PUBLISHED` | 已通过发布门禁，可创建患者路径实例 | 入径、仿真、查询、下线 |
| `OFFLINE` | 已下线，不允许新患者入径 | 查询历史实例、诊断解释 |
| `ARCHIVED` | 归档，仅保留追溯和审计 | 查询历史 |

首版路径模板只有一个当前发布版本。后续可扩展为版本灰度、回滚和多机构差异化模板。

### 4.2 患者路径运行状态

| 状态 | 含义 |
|---|---|
| `ENTERED` | 已入径，尚未开始第一个节点 |
| `NODE_EXECUTING` | 当前节点执行中 |
| `VARIANCE` | 当前节点存在变异，需人工确认或继续推进 |
| `COMPLETED` | 路径按模板完成 |
| `EXITED` | 因退出条件、转科、患者选择或医生决策退出 |

路径推进事件包括：入径、节点完成、节点超时、变异登记、路径完成、路径退出、随访交接。

### 4.3 节点和边

节点类型覆盖首版规范要求：

| 类型 | 含义 |
|---|---|
| `SCREENING` | 筛查 |
| `ASSESSMENT` | 评估 |
| `EXAM` | 检查 |
| `LAB` | 检验 |
| `MEDICATION` | 用药 |
| `SURGERY` | 手术 |
| `NURSING` | 护理 |
| `REHAB` | 康复 |
| `DISCHARGE` | 出院 |
| `FOLLOWUP` | 随访 |
| `QUALITY` | 质控 |

边类型覆盖默认推进、条件分支、风险分层、患者选择、资源不可用、医生决策和异常回退。首版条件分支只做显式事件匹配，不做医学语义自动判定。

### 4.4 发布门禁

路径模板发布必须同时满足：

- 模板编码、名称、病种、版本、来源和专病包信息完整。
- 至少包含一个起始节点和一个终止节点。
- 节点编码在模板内唯一，节点顺序非负。
- 每条边的源节点和目标节点都存在。
- 非终止节点至少存在一条出边。
- 时间窗不能为负数。

### 4.5 医疗安全

- 路径推进结果是流程建议和执行事实，不是医疗最终结论。
- 医生决策、患者选择、资源不可用和异常回退必须以变异记录保存。
- 路径实例只引用 `patientId`、`encounterId`、模板和节点，不保存完整病历原文。
- 关键时钟可关联质控指标，但不能替代质控结果或医疗质量判定。
- 发布、入径、推进、变异、退出和仿真均进入审计链。

## 5. API 合同

| API | 权限 | 用途 |
|---|---|---|
| `POST /api/v1/engine/pathway/specialty-packages` | `pathway.write` | 创建专病包和专病画像 |
| `GET /api/v1/engine/pathway/specialty-packages` | `pathway.read` | 查询专病包 |
| `POST /api/v1/engine/pathway/pathway-templates` | `pathway.write` | 创建路径模板、节点和边 |
| `GET /api/v1/engine/pathway/pathway-templates` | `pathway.read` | 按状态、病种、专病包分页查询模板 |
| `GET /api/v1/engine/pathway/pathway-templates/{templateId}` | `pathway.read` | 查看模板详情、节点和边 |
| `POST /api/v1/engine/pathway/pathway-templates/{templateId}/publish` | `pathway.publish` | 执行发布门禁并发布模板 |
| `POST /api/v1/engine/pathway/pathway-templates/{templateId}/simulate` | `pathway.write` | 使用示例事件仿真节点轨迹 |
| `POST /api/v1/engine/pathway/patient-pathways/enter` | `pathway.write` | 为患者创建路径实例 |
| `GET /api/v1/engine/pathway/patient-pathways/{patientPathwayId}` | `pathway.read` | 查看患者路径详情、当前节点、变异和时钟 |
| `POST /api/v1/engine/pathway/patient-pathways/{patientPathwayId}/advance` | `pathway.write` | 推进节点、登记变异、完成或退出路径 |
| `GET /api/v1/engine/pathway/patient-pathways/{patientPathwayId}/variances` | `pathway.read` | 查询变异事实 |
| `GET /api/v1/engine/pathway/patient-pathways/{patientPathwayId}/clocks` | `pathway.read` | 查询关键时钟 |

## 6. 数据模型

### 6.1 `specialty_package`

专病包稳定身份，保存租户、病种编码、名称、版本、状态、来源和发布信息。

关键字段：`package_id`、`tenant_id`、`package_code`、`disease_code`、`name`、`package_version`、`status`、`source_ref`、`published_at`、`trace_id`。

### 6.2 `specialty_profile`

专病画像，保存分型、风险分层、准入摘要、退出摘要和随访摘要。

关键字段：`profile_id`、`tenant_id`、`package_id`、`profile_code`、`name`、`stratification_json`、`entry_criteria_json`、`exit_criteria_json`、`followup_plan_json`。

### 6.3 `pathway_template`

路径模板主表，保存模板编码、病种、专病包、版本、层级、来源、状态和当前起始节点。

关键字段：`template_id`、`tenant_id`、`package_id`、`template_code`、`name`、`disease_code`、`template_level`、`status`、`start_node_code`、`source_ref`。

### 6.4 `pathway_node`

路径节点，保存节点编码、类型、顺序、责任角色、依赖、时间窗和节点配置。

关键字段：`node_id`、`tenant_id`、`template_id`、`node_code`、`node_type`、`sort_order`、`responsible_role`、`time_window_minutes`、`config_json`。

### 6.5 `pathway_edge`

路径边，保存源节点、目标节点、边类型、条件摘要和优先级。

关键字段：`edge_id`、`tenant_id`、`template_id`、`from_node_code`、`to_node_code`、`edge_type`、`condition_json`、`priority`。

### 6.6 `patient_pathway`

患者路径实例，保存患者、就诊、模板、当前节点、状态、入径、完成或退出时间。

关键字段：`patient_pathway_id`、`tenant_id`、`patient_id`、`encounter_id`、`template_id`、`current_node_code`、`status`、`entered_at`、`completed_at`、`exit_reason`。

### 6.7 `pathway_variance`

路径变异记录，保存变异类型、节点、原因、处置动作和是否继续推进。

关键字段：`variance_id`、`tenant_id`、`patient_pathway_id`、`node_code`、`variance_type`、`reason`、`resolution_action`、`created_at`。

### 6.8 `clinical_clock`

关键时钟，保存路径实例节点的开始、到期、完成、状态和质控指标关联。

关键字段：`clock_id`、`tenant_id`、`patient_pathway_id`、`node_code`、`metric_code`、`started_at`、`due_at`、`completed_at`、`status`。

### 6.9 `specialty_metric_binding`

专病指标绑定，保存专病包、模板、节点和质控指标之间的关联。

关键字段：`binding_id`、`tenant_id`、`package_id`、`template_id`、`node_code`、`metric_code`、`required`。

## 7. 路径推进首版规则

1. 入径时只能选择已发布模板。
2. 入径后定位到模板起始节点，创建节点关键时钟。
3. 节点完成时优先选择请求指定的目标节点；未指定时选择优先级最高的默认边。
4. 如果目标节点为空且当前节点没有出边，则路径完成。
5. 变异事件必须写入 `pathway_variance`；如果请求指定继续节点，则进入该节点，否则路径状态为 `VARIANCE`。
6. 退出事件将路径状态改为 `EXITED`，完成当前关键时钟。
7. 每次推进都返回当前状态、上一节点、下一节点、变异、关键时钟和 traceId。

## 8. 错误码

新增错误码：

| 错误码 | HTTP | 含义 |
|---|---|---|
| `ENG-PATHWAY-001` | 400 | 路径模板校验失败 |
| `ENG-PATHWAY-002` | 404 | 路径模板不存在 |
| `ENG-PATHWAY-003` | 404 | 患者路径不存在 |
| `ENG-PATHWAY-004` | 409 | 路径模板发布门禁失败 |
| `ENG-PATHWAY-005` | 409 | 当前路径状态不允许该操作 |
| `ENG-PATHWAY-006` | 400 | 路径推进事件不合法 |
| `ENG-PATHWAY-007` | 404 | 专病包不存在 |

## 9. 验收标准

- H2、PostgreSQL、Oracle、达梦、人大金仓迁移版本均增加到 V12。
- 后端单元测试覆盖模板发布门禁、入径、顺序推进、变异、退出、关键时钟和诊断解释。
- MockMvc 安全测试覆盖读、写、发布权限。
- `mvn -f medkernel-backend/pom.xml test` 通过；无 Docker 环境下已有数据库烟测可按既有机制跳过。
- `docs/backlog.md` 将 `GA-ENG-API-06` 标记为完成，并保留 `GA-ENG-PATH-01` 为后续可视化路径引擎任务。
