# 第三方接入契约指南（INTEG-02 + OPT-01 FHIR 门面）

> 适用对象：院内信息科、第三方厂商、实施工程师。本文档描述当前已经存在的 INTEG-01 对接总线端点，以及 OPT-01 挂载在总线下的 FHIR R4/R5 运行门面；FHIR 门面只声明已实现能力，不发布未落地资源或交互。

## 接入边界

所有 HIS、EMR、LIS、PACS、医保、病案、护理、手麻、区域平台和模型服务接入都必须走统一对接链路：适配器、标准上下文、临床事件、嵌入、回调、机构生效版本同步和审计证据。外部系统不绕引擎直写医疗结论、医嘱、病历、法定上报、支付或设备控制。

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

1. 信息科登记适配器或 Webhook 订阅，配置服务机构、组织作用域、协议类型和字段映射。
2. 外部系统发送入站消息或 MedKernel 登记出站同步消息。
3. 服务端校验服务机构、权限、签名、幂等键和字段映射。
4. 服务端锁定当前机构生效版本，字段映射按其中的不可变术语资产生成标准患者载荷。
5. 验签和映射成功后创建可追溯临床事件；异步处理器据此生成上下文快照并触发规则、路径、推荐等真实主链路。
6. 对接日志、traceId、临床事件 ID、审计事件和死信证据保留；外部断连时返回 `NOT_CONNECTED` 或 `NOT_SYNCED`，不得伪造成功。

## 知识运行时稳定契约

第三方临床调用统一使用版本化入口 `/api/v1/engine/integration/knowledge-runtime`，不绕过门面直接拼接规则、路径、评估、术语或资产版本接口。

| 能力 | 端点 | 权限 | 契约要点 |
| --- | --- | --- | --- |
| 当前机构生效版本 | `GET /api/v1/engine/integration/knowledge-runtime/runtime-release/current` | `asset.read` | 不接收离线交付文件、领域或版本参数；服务端按认证服务机构和医院返回完整不可变版本明细、完整性校验码、版本号和精确资产版本。 |
| 标准上下文写入 | `POST /api/v1/engine/integration/knowledge-runtime/context-snapshots` | `context.write` | 使用标准上下文资源；术语不能确定时保留映射告警，不猜测医学编码。 |

契约版本固定为 `v1`。所有 POST 必须携带 `Idempotency-Key`，平台级幂等过滤器会拒绝同键异文并重放首次成功结果。字段契约从 `/api/v1/engine/integration/data-contract` 获取，由服务端自动绑定当前机构生效版本；OpenAPI 从 `/v3/api-docs/medkernel-third-party-integration` 获取。机构覆盖、资产启停和升级只在平台管理面完成，外部临床系统没有运行资产选择权。

## 标准输入与院内字典对照

第三方写入上下文前必须先读取当前机构生效版本的字段契约。资源类型、字段路径和目标字典以平台契约为准，不允许自行扩展同义字段或把院内编码冒充平台标准编码。

| 输入情况 | 必传内容 | 系统行为 |
| --- | --- | --- |
| 已使用平台标准编码 | 标准字段路径、编码系统、标准编码、显示名称 | 按标准上下文写入，并在响应 `mappingStatus` 中保留核验结果 |
| 仍使用院内编码 | `sourceSystem`、目标标准字典、术语分类和来源记录 | 服务端只使用当前机构生效版本中的不可变映射资产归一；不得跨服务机构借用对照，也不得读取当前可变映射 |
| 标准码存在但院内未对照 | 完整原始编码与来源证据 | 标记 `UNMAPPED`，进入映射治理和就绪度告警，不猜测目标编码 |
| 标准字典不存在该编码 | 完整原始编码与来源证据 | 标记 `NO_STANDARD_TERM`；高风险必填字段拒绝，其他字段诚实降级 |

联调前通过 `GET /api/v1/engine/terminology/mappings/coverage?standardSystem={system}&codes={code}` 检查标准编码覆盖度；只有 `COVERED` 表示当前服务机构存在已确认院内对照。标准字典由平台主空间统一维护，`LocalTerm` 与 `TermMapping` 只表达当前服务机构差异；正式映射版本由机构生效版本锁定后参与临床归一。

术语字典页面独立负责标准术语、本地术语、映射确认和下一版本快照的维护。适配器配置不得保存
`termMappingId`；运行配置只声明 `targetDictionaryKey` 与 `category`，由临床事件携带的
实际映射版本由服务端锁定的 `runtimeReleaseId` 及其中精确资产版本决定，调用方不得自行选择。

## 接入生命周期

- `POST /api/v1/engine/integration/onboardings` 创建第三方业务接口接入申请，`accessMode=ADAPTER` 时必须绑定服务机构内真实适配器，`accessMode=FHIR` 时必须声明 `R4` 或 `R5`。
- `POST /api/v1/engine/integration/onboardings/{id}/advance` 只能按 `REQUESTED` → `AUTH_CONFIGURED` → `MAPPING_CONFIGURED` → `ONLINE` 推进，`OFFLINE` 可用于下线；每次推进必须带阶段证据。
- 适配器路线进入 `MAPPING_CONFIGURED` 或 `ONLINE` 前必须已有字段映射；缺字段映射返回 `ENG-INTEG-001`，不得用空映射绕过。
- `ONLINE` 只表示接入配置链路完成，不等于外部系统真实可达；连接器健康验证未通过或外部不可达时响应仍显示 `NOT_CONNECTED` 阻塞项。
- `GET /api/v1/engine/integration/onboardings` 供实施台查看所有接入档案及当前阻塞项，不返回幽灵接入或硬编码样例。

## 区域协同来源

- `POST /api/v1/engine/integration/regional-sources` 登记区域平台、上级医院、医联体等跨组织来源，必须包含来源组织 ID、来源组织名称、组织作用域和证据说明。
- `trustLevel` 只能为 `LOW`、`MEDIUM`、`HIGH`，且必须来自 OPT-07 可信分级证据；空分级返回 `REGIONAL_SOURCE_UNGRADED`，不得默认高可信。
- 来源可关联适配器或接入申请；关联对象不存在时拒绝保存，避免形成无法追溯的区域数据入口。
- `GET /api/v1/engine/integration/regional-sources` 返回当前服务机构来源清单，跨服务机构来源必须隔离。

## FHIR 运行门面

- `GET /api/v1/engine/integration/fhir/{version}/metadata` 返回真实 `CapabilityStatement`，只声明当前已落地的 R4/R5 能力范围。
- `GET /api/v1/engine/integration/fhir/{version}/{resourceType}/{id}` 按 `mk_fhir_resource_mapping` 读取已登记映射并返回对应 FHIR resource；未登记映射返回 `OperationOutcome`，不得临时拼装或编造资源。
- `GET /api/v1/engine/integration/fhir/{version}/{resourceType}` 返回 FHIR `Bundle` searchset；当前只返回已登记映射的标准资源，不把请求中的患者标识回显到响应。
- `POST /api/v1/engine/integration/fhir/{version}/{resourceType}` 接收原始 FHIR JSON resource，请求头必须带 `X-MedKernel-Fhir-Adapter`、`X-MedKernel-Timestamp`、`X-MedKernel-Signature`。适配器配置只能写 `fhir.signatureWebhookId` 引用，禁止内联 `secretKey`；调用方不得选择包或版本。
- 当前受控 create 支持 Patient、Encounter、Condition、Observation、Medication、Procedure、CarePlan、DiagnosticReport、DocumentReference 等标准资源，写入会映射为 `CanonicalResource`、登记 FHIR 映射证据、回流临床事件入口，并把出站补偿交给 INTEG-01；无真实连接器时返回 `NOT_CONNECTED` 证据。
- MedicationRequest / ServiceRequest 属高风险资源，门面只创建 `FHIR_PHYSICIAN_CONFIRMATION` 医师确认任务，不自动写医嘱、不直写申请单、不直写病历、不绕引擎。
- 未连接适配器、签名错误、允许清单不匹配或未实现资源均返回 FHIR `OperationOutcome`；响应不回显患者原始 resource。

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

字段映射模板见 `field-mapping-template.json`，样例见
`field-mapping-example-his-adt.json`。文档、管理页面和运行服务统一使用同一种配置语法：

| 字段 | 规则 |
| --- | --- |
| `sourcePath` | 必填，外部 JSON 载荷中的 JSON Pointer，例如 `/diagnoses/0/code` |
| `targetPath` | 必填，标准患者载荷中的 JSON Pointer，例如 `/diagnoses/0`；数组使用非负整数下标 |
| `targetDictionaryKey` | 可选，目标标准字典稳定键，例如 `ICD-10`、`LOINC` |
| `category` | 可选，术语分类；与 `targetDictionaryKey` 必须同时填写 |

不需要术语归一的字段只填写来源和目标路径。需要归一的字段必须同时填写目标标准字典和术语
分类；运行时用请求的 `sourceSystem`、本地编码和服务端确定的当前机构生效版本定位唯一不可变映射。
无法唯一定位时拒绝该临床事件，不猜测编码。配置中出现 `termMappingId` 将直接返回
`ENG-INTEG-001`。

院内新增字段必须先在“上下文字段目录维护”中定义，并随 `FIELD_CATALOG` 资产发布；接入映射
统一写入 `/extensions/local/<字段键>`。运行时将其保存到不可变患者上下文快照，并以
`extensions.local.<字段键>` 暴露给规则和路径。未发布字段不得临时写入 canonical 资源。

## Webhook 临床事件契约

入站请求除 `payload` 外必须携带 `messageId`、`adapterId`、`sourceSystem`、`eventType`、
`patientId`、`clinicalSetting`、`triggerPoint` 和 `occurredAt`；有就诊时
携带 `encounterId`。签名覆盖完整请求体。

成功响应返回标准化 `mappedPayload`、映射计数、编码归一计数、`clinicalEventId` 和
`clinicalEventStatus`。相同 `messageId` 重放返回原临床事件，不重复生成患者数据或运行副作用。

## 鉴权与签名

- 平台账号或受委托身份完成认证后访问管理端点；接口权限由 `integration.read`、`integration.write`、`integration.execute` 控制。
- Webhook 入站和 FHIR create 必须带 `X-MedKernel-Timestamp` 与 `X-MedKernel-Signature`。
- 签名基于服务机构内 Webhook 密钥和原始请求载荷计算 HMAC-SHA256；服务端常量时间比较，失败拒绝并审计。
- FHIR create 签名基于适配器引用的 Webhook 签名密钥、时间戳和原始 resource 计算；适配器可配置来源 IP 允许清单，但不得在配置 JSON 内存放明文密钥。
- 密钥、访问凭证、患者原始请求载荷不得写入适配器配置、日志或文档样例。

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
- 机构生效版本同步无真实通道：`NOT_SYNCED`。
- 字段映射缺失但可留待质量治理：返回部分成功或质量告警；高风险必填字段缺失必须拒绝。
- 降级不阻断院内主流程，但必须留下日志、traceId、审计和可重试证据。

## 审计

所有创建、更新、健康检查、Webhook 验证、入站消息、出站消息、重试和死信重放都必须写审计。审计记录至少包含服务机构、组织作用域、用户、traceId、动作、目标对象、结果、错误码和时间。读操作通过日志与 traceId 支撑问题定位。

## 验收清单

| 类别 | 检查项 | 通过标准 |
| --- | --- | --- |
| 连通 | 适配器登记后健康检查 | 无真实连接器时为 `NOT_CONNECTED`，不得伪造 `HEALTHY` |
| 字段 | 按统一 JSON Pointer 语法映射到标准患者载荷 | 数组路径可落地；编码按当前机构生效版本锁定的精确映射归一；配置不含可变映射 ID |
| 鉴权 | 缺权限访问管理端点 | 返回 403 ProblemDetail，带 traceId |
| 签名 | Webhook 缺签名或签名错误 | 拒绝入站并保留失败审计 |
| 幂等 | 重放同一 `messageId` | 不重复写副作用，返回原处理状态或幂等冲突 |
| 临床事件 | 验签和映射成功 | 返回并持久化同一个 `clinicalEventId`，后续可追溯到上下文快照和引擎运行 |
| 回调 | 回调测试目标不可达 | 返回失败或 `NOT_CONNECTED` |
| 接入 | 业务接口接入生命周期推进 | 阶段证据完整；字段映射缺失时拒绝进入映射完成或上线；上线仍不伪造外部连接 |
| 区域 | 区域协同来源可信分级 | 未完成 OPT-07 分级返回 `REGIONAL_SOURCE_UNGRADED`；已分级来源保留组织和证据 |
| FHIR | 10 类核心资源 read/search/create | read 返回已登记映射资源；search 返回 `Bundle`；create 落 `CanonicalResource`、映射证据和临床事件；断连只返回 `NOT_CONNECTED`，不伪造同步成功 |
| FHIR | 高风险医嘱类 create | 只登记医师确认任务，禁止自动开嘱或直写病历 |
| 降级 | 外部系统关闭 | 主流程不中断，集成状态为 `NOT_CONNECTED` / `NOT_SYNCED` |
| 审计 | 执行类操作 | 可按 traceId 查到动作、目标、结果和错误码 |
