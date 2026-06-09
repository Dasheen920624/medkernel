# 第三方接入契约指南（INTEG-02 + OPT-01 FHIR 门面）

> 适用对象：院内信息科、第三方厂商、实施工程师。本文档描述当前已经存在的 INTEG-01 对接总线端点，以及 OPT-01 挂载在总线下的 FHIR R4/R5 运行门面；FHIR 门面只声明已实现能力，不发布未落地资源或交互。

## 接入边界

所有 HIS、EMR、LIS、PACS、医保、病案、护理、手麻、区域平台和 Provider 接入都必须走统一对接链路：适配器、标准上下文、临床事件、嵌入、回调、包发布同步和审计证据。外部系统不绕引擎直写医疗结论、医嘱、病历、法定上报、支付或设备控制。

## 协议矩阵

| 协议 | 当前入口 | 说明 | 断连状态 |
| --- | --- | --- | --- |
| Webhook | `/api/v1/engine/integration/webhooks/{id}/inbound` | 使用 `X-MedKernel-Timestamp` 和 `X-MedKernel-Signature` 做 HMAC-SHA256 验签 | `NOT_CONNECTED` |
| REST / WebService / HL7 适配器 | `/api/v1/engine/integration/adapters` | 先登记适配器、字段映射和真实连接配置；没有真实连接器时只登记诚实状态 | `NOT_CONNECTED` |
| 业务接口接入生命周期 | `/api/v1/engine/integration/onboardings`、`/api/v1/engine/integration/onboardings/{id}/advance` | 按申请、鉴权、字段映射、联调上线、下线推进，接入路线可为适配器或 FHIR 门面 | `NOT_CONNECTED` |
| 区域协同来源 | `/api/v1/engine/integration/regional-sources` | 登记来源组织、区域网络和 OPT-07 可信分级证据；未分级来源必须拒绝 | `NOT_CONNECTED` |
| 出站异步同步 | `/api/v1/engine/integration/messages/outbound` | 主流程不等待外部回执；失败进入日志、重试或死信 | `NOT_CONNECTED` / `NOT_SYNCED` |
| FHIR R4/R5 资源门面 | `/api/v1/engine/integration/fhir/{version}/metadata`、`/api/v1/engine/integration/fhir/{version}/{resourceType}`、`/api/v1/engine/integration/fhir/{version}/{resourceType}/{id}` | OPT-01 所有，挂 INTEG-01 总线；开放 10 类核心资源 read/search/create，高风险 ServiceRequest 只登记医师确认任务 | `NOT_CONNECTED` |

## 数据流

1. 信息科登记适配器或 Webhook 订阅，配置租户、组织作用域、协议类型和字段映射。
2. 外部系统发送入站消息或 MedKernel 登记出站同步消息。
3. 服务端校验租户、权限、签名、幂等键和字段映射。
4. 字段映射进入 API-01 标准上下文；临床编码经 TERM-01 字典映射归一，无法确定时进入人工待裁或质量告警。
5. 对接日志、traceId、审计事件和死信证据保留；外部断连时返回 `NOT_CONNECTED` 或 `NOT_SYNCED`，不得伪造成功。

## 知识运行时稳定契约

第三方知识调用统一使用版本化入口 `/api/v1/engine/integration/knowledge-runtime`，不绕过门面直接拼接规则、路径、评估、术语或知识包内部接口。

| 能力 | 端点 | 权限 | 契约要点 |
| --- | --- | --- | --- |
| 有效解析 | `GET /api/v1/engine/integration/knowledge-runtime/effective-package` | `package.read` | 必传包编码、版本和目标组织；可传 `specialty`、`scenario`、`careSetting`、`cohort`、`role` 与 `effectiveAt`。返回统一快照、`sourceTier` 和 `contentHash`。 |
| 标准上下文写入 | `POST /api/v1/engine/integration/knowledge-runtime/context-snapshots` | `context.write` | 使用标准上下文资源；术语不能确定时保留映射告警，不猜测医学编码。 |
| 覆盖登记 | `POST /api/v1/engine/integration/knowledge-runtime/overrides` | `tenant.override` | 只接受 `REPLACE`、`DISABLE`、`ADD` 语义，复用唯一组织继承与安全门禁。 |
| 覆盖退役 | `POST /api/v1/engine/integration/knowledge-runtime/overrides/{overrideId}:retire` | `tenant.override` | 关闭覆盖生命周期并保留审计证据，不物理删除历史。 |
| 包分发 | `POST /api/v1/engine/integration/knowledge-runtime/packages/{packageId}:distribute` | `package.publish` | 复用包发布同步主链路；未连接真实通道时返回诚实状态。 |
| 对账查询 | `GET /api/v1/engine/integration/knowledge-runtime/packages/{packageId}/reconciliation` | `package.read` | 状态只取 `NOT_DISTRIBUTED`、`IN_PROGRESS`、`NOT_SYNCED`、`FAILED`、`SUCCESS`，并返回真实同步日志。 |

契约版本固定为 `v1`。所有 POST 必须携带 `Idempotency-Key`，平台级幂等过滤器会拒绝同键异文并重放首次成功结果。作用域维度采用严格键值语义，未知维度或畸形值直接拒绝；同一查询优先选择维度更具体且在 `effectiveAt` 生效窗口内的版本。字段契约从 `/api/v1/engine/integration/data-contract?packageVersion={packageVersion}` 获取，OpenAPI 从 `/v3/api-docs/medkernel-third-party-integration` 获取。

## 接入生命周期

- `POST /api/v1/engine/integration/onboardings` 创建第三方业务接口接入申请，`accessMode=ADAPTER` 时必须绑定租户内真实适配器，`accessMode=FHIR` 时必须声明 `R4` 或 `R5`。
- `POST /api/v1/engine/integration/onboardings/{id}/advance` 只能按 `REQUESTED` → `AUTH_CONFIGURED` → `MAPPING_CONFIGURED` → `ONLINE` 推进，`OFFLINE` 可用于下线；每次推进必须带阶段证据。
- 适配器路线进入 `MAPPING_CONFIGURED` 或 `ONLINE` 前必须已有字段映射；缺字段映射返回 `ENG-INTEG-001`，不得用空映射绕过。
- `ONLINE` 只表示接入配置链路完成，不等于外部系统真实可达；未接入真实连接器时响应仍显示 `NOT_CONNECTED` 阻塞项。
- `GET /api/v1/engine/integration/onboardings` 供实施台查看所有接入档案及当前阻塞项，不返回幽灵接入或硬编码样例。

## 区域协同来源

- `POST /api/v1/engine/integration/regional-sources` 登记区域平台、上级医院、医联体等跨组织来源，必须包含来源组织 ID、来源组织名称、组织作用域和证据说明。
- `trustLevel` 只能为 `LOW`、`MEDIUM`、`HIGH`，且必须来自 OPT-07 可信分级证据；空分级返回 `REGIONAL_SOURCE_UNGRADED`，不得默认高可信。
- 来源可关联适配器或接入申请；关联对象不存在时拒绝保存，避免形成无法追溯的区域数据入口。
- `GET /api/v1/engine/integration/regional-sources` 返回当前租户来源清单，跨租户来源必须隔离。

## FHIR 运行门面

- `GET /api/v1/engine/integration/fhir/{version}/metadata` 返回真实 `CapabilityStatement`，只声明当前已落地的 R4/R5 能力范围。
- `GET /api/v1/engine/integration/fhir/{version}/{resourceType}/{id}` 按 `mk_fhir_resource_mapping` 读取已登记映射并返回对应 FHIR resource；未登记映射返回 `OperationOutcome`，不得临时拼装或编造资源。
- `GET /api/v1/engine/integration/fhir/{version}/{resourceType}` 返回 FHIR `Bundle` searchset；当前只返回已登记映射的标准资源，不把请求中的患者标识回显到响应。
- `POST /api/v1/engine/integration/fhir/{version}/{resourceType}` 接收原始 FHIR JSON resource，请求头必须带 `X-MedKernel-Fhir-Adapter`、`X-MedKernel-Timestamp`、`X-MedKernel-Signature`；可选 `X-MedKernel-Package-Version`。适配器配置只能写 `fhir.signatureWebhookId` 引用，禁止内联 `secretKey`。
- 当前受控 create 支持 Patient、Encounter、Condition、Observation、Medication、Procedure、CarePlan、DiagnosticReport、DocumentReference 等标准资源，写入会映射为 `CanonicalResource`、登记 FHIR 映射证据、回流临床事件入口，并把出站补偿交给 INTEG-01；无真实连接器时返回 `NOT_CONNECTED` 证据。
- MedicationRequest / ServiceRequest 属高风险资源，门面只创建 `FHIR_PHYSICIAN_CONFIRMATION` 医师确认任务，不自动写医嘱、不直写申请单、不直写病历、不绕引擎。
- 未连接适配器、签名错误、白名单不匹配或未实现资源均返回 FHIR `OperationOutcome`；响应不回显患者原始 resource。

## 标准互操作映射

- `POST /api/v1/engine/interoperability/rules/cds-hooks:export` 将规则草稿 DSL 导出为 CDS Hooks 服务声明、Card、CQL 与 Arden 概念映射。
- `POST /api/v1/engine/interoperability/rules/cds-hooks:import` 从映射回导规则草稿，完整语义以 MedKernel DSL 扩展为准。
- `POST /api/v1/engine/interoperability/pathways/plan-definition:export` 将路径模板草稿导出为 FHIR PlanDefinition 与 GLIF 概念映射。
- `POST /api/v1/engine/interoperability/pathways/plan-definition:import` 从映射回导路径模板草稿；回导后仍走既有路径创建、校验和发布流程。

## OpenAPI

- 统一服务契约组：`/v3/api-docs/medkernel-service-contracts`
- 第三方接入独立组：`/v3/api-docs/medkernel-third-party-integration`
- 本仓库快照：`docs/contracts/integration/integration-openapi.paths.json`
- CI 一致性：`IntegrationContractDocumentationTest` 通过反射比对 `IntegrationController`、FHIR 门面和知识运行时控制器的真实端点与快照，防止幽灵端点和遗漏端点。

## 字段映射

字段映射模板见 `field-mapping-template.json`，样例见 `field-mapping-example-his-adt.json`。所有映射必须写明：

- 外部字段路径和外部字段名；
- API-01 标准资源与标准路径；
- 是否必填、缺失行为和原始字段证据；
- 临床编码是否经 TERM-01 归一；
- 无法确认的高风险编码不得自动猜测，必须进入人工复核。

## 鉴权与签名

- 平台账号或受委托身份完成认证后访问管理端点；接口权限由 `integration.read`、`integration.write`、`integration.execute` 控制。
- Webhook 入站和 FHIR create 必须带 `X-MedKernel-Timestamp` 与 `X-MedKernel-Signature`。
- 签名基于租户内 Webhook 密钥和原始 payload 计算 HMAC-SHA256；服务端常量时间比较，失败拒绝并审计。
- FHIR create 签名基于适配器引用的 Webhook 签名密钥、时间戳和原始 resource 计算；适配器可配置来源 IP 白名单，但不得在配置 JSON 内存放明文密钥。
- 密钥、令牌、患者原始 payload 不得写入适配器配置、日志或文档样例。

## 幂等

- 写操作建议传 `Idempotency-Key`，同一操作重试不得产生重复副作用。
- 入站 Webhook 使用 `messageId` 作为业务幂等键。
- 出站同步使用 `messageId` 作为业务幂等键。
- 人工重试和死信重放必须保留原消息证据，新建补偿消息也要有 traceId。

## 回调约定

- 回调目标必须登记在 Webhook 订阅内，事件列表必须可审计。
- 回调测试失败只能返回失败或 `NOT_CONNECTED`，不能写成成功。
- 第三方系统重放同一消息时必须复用同一 `messageId`。
- 回调死信可通过 `/api/v1/engine/integration/callbacks/dead-letter/{id}/replay` 人工重放；该入口复用集成死信补偿链路，原死信证据必须保留。

## 降级

- 外部系统断连：`NOT_CONNECTED`。
- 包发布或同步无真实通道：`NOT_SYNCED`。
- 字段映射缺失但可留待质量治理：返回部分成功或质量告警；高风险必填字段缺失必须拒绝。
- 降级不阻断院内主流程，但必须留下日志、traceId、审计和可重试证据。

## 审计

所有创建、更新、健康检查、Webhook 测试、入站消息、出站消息、重试和死信重放都必须写审计。审计记录至少包含租户、组织作用域、用户、traceId、动作、目标对象、结果、错误码和时间。读操作通过日志与 traceId 支撑问题定位。

## 验收清单

| 类别 | 检查项 | 通过标准 |
| --- | --- | --- |
| 连通 | 适配器登记后健康检查 | 无真实连接器时为 `NOT_CONNECTED`，不得伪造 `HEALTHY` |
| 字段 | 按模板映射到 API-01 标准上下文 | 必填字段有来源证据，编码经 TERM-01 或进入人工待裁 |
| 鉴权 | 缺权限访问管理端点 | 返回 403 ProblemDetail，带 traceId |
| 签名 | Webhook 缺签名或签名错误 | 拒绝入站并保留失败审计 |
| 幂等 | 重放同一 `messageId` | 不重复写副作用，返回原处理状态或幂等冲突 |
| 回调 | 回调测试目标不可达 | 返回失败或 `NOT_CONNECTED` |
| 接入 | 业务接口接入生命周期推进 | 阶段证据完整；字段映射缺失时拒绝进入映射完成或上线；上线仍不伪造外部连接 |
| 区域 | 区域协同来源可信分级 | 未完成 OPT-07 分级返回 `REGIONAL_SOURCE_UNGRADED`；已分级来源保留组织和证据 |
| FHIR | 10 类核心资源 read/search/create | read 返回已登记映射资源；search 返回 `Bundle`；create 落 `CanonicalResource`、映射证据和临床事件；断连只返回 `NOT_CONNECTED`，不伪造同步成功 |
| FHIR | 高风险医嘱类 create | 只登记医师确认任务，禁止自动开嘱或直写病历 |
| 降级 | 外部系统关闭 | 主流程不中断，集成状态为 `NOT_CONNECTED` / `NOT_SYNCED` |
| 审计 | 执行类操作 | 可按 traceId 查到动作、目标、结果和错误码 |
