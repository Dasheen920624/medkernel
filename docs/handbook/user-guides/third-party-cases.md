# MedKernel · 第三方对接案例集

> 状态：已由全流程演练幕9激活
> 适用：医院信息科 / HIS、EMR、LIS、FHIR 网关、质控平台厂商工程师 / 乙方实施
> 证据：幕9真实演练归档在 [第三方对接能力案例集证据](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/README.md)

---

## 1. 对接前确认

| 项 | 口径 |
|---|---|
| API 基路径 | `/medkernel/api/v1` |
| 前端入口 | `/adapter/hub` 适配器中心 |
| 权限角色 | 信息科管理员负责适配器、Webhook、联调单；医生负责嵌入式临床上下文；质控角色查看出站结果 |
| 验签 | 入站 Webhook 与 FHIR 写入使用时间戳 + HMAC 签名；一次性密钥只展示一次，证据不得保存明文 |
| 追踪 | 每次请求带 `X-MedKernel-Trace-Id`，响应 traceId 与审计链一起归档 |
| 降级 | 外部系统断连时显示 `NOT_CONNECTED`、`FAILED` 或 `DEAD_LETTER`，不得展示为成功 |

幕9批次 `act9-2grf0t4vdy` 在 134 上完成 6 个案例，复用幕6患者 `mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY`、就诊 `enc-act6-8oh7bn024a`、CAP 患者路径 `pp-9b4c8389-c46e-4a12-bf5f-6d66be5768fc`，并复用幕8配置包 `DRILL.ACT8.CONFIG.ACT8-8SINB347C5@2026.06.11-act8-8sinb347c5`。

## 2. 六个通过案例

| # | 场景 | 对接方式 | 验收信号 | 证据 |
|---|---|---|---|---|
| C1 | HIS 入院消息触发患者路径锚点 | HL7 v2 ADT / Webhook / 字段映射 | 入站 `200`，临床事件 `201`，事件详情 `200`，患者路径 `200` | [02-case-c1-his-adt-admission.json](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/02-case-c1-his-adt-admission.json) |
| C2 | LIS 危急值结果回流并触发推荐链 | REST/Webhook 入站 | 入站 `200`，临床事件 `201`，推荐卡查询 `200` | [03-case-c2-lis-critical-result.json](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/03-case-c2-lis-critical-result.json) |
| C3 | 第三方通过 FHIR R4 读写患者和检验 | FHIR R4 Facade | Patient/Observation create `201`，read/search `200` | [04-case-c3-fhir-patient-observation.json](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/04-case-c3-fhir-patient-observation.json) |
| C4 | HIS 页面内嵌临床终端 | Embed launch token | Origin `200`，令牌签发 `200`，launch `200` 且上下文 `CONNECTED` | [05-case-c4-his-embedded-terminal.json](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/05-case-c4-his-embedded-terminal.json) |
| C5 | 质控预警出站推送断连重试 | Webhook 出站 / 死信 | 出站 `200`，第二次重试 `DEAD_LETTER`，重放 `200` | [06-case-c5-quality-webhook-retry-dead-letter.json](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/06-case-c5-quality-webhook-retry-dead-letter.json) |
| C6 | 第三方读取知识运行时 | REST API | 有效包 `200`，上下文快照 `201`，发布对账 `200` | [07-case-c6-third-party-knowledge-runtime.json](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/07-case-c6-third-party-knowledge-runtime.json) |

幕8.5 补齐了 `/adapter/hub` 的前台页面证据。给厂商演示时，先用 [适配器总览](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/01-adapter-hub-overview.png) 解释连接率和必接系统，再用 [健康诊断](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/02-adapter-health-diagnosis.png)、[死信重放](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/03-adapter-dead-letter.png)、[数据质量](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/04-adapter-data-quality.png)、[接入向导](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/05-adapter-onboarding.png) 和 [区域来源](../../release/evidence/v1.0-drill-20260611/幕9-第三方对接能力案例集/ui-replay/06-adapter-regional-source.png) 回答“断连、配置非法、死信和字段质量怎么处理”。页面状态必须按真实后端结果展示，未接通就是 `NOT_CONNECTED` 或 `MISCONFIGURED`。

## 3. C1 HIS 入院消息

HIS 发送 ADT 入院消息，MedKernel 先做 Webhook 验签，再按字段映射把院内字段转成标准上下文，最后登记临床事件并关联已有 CAP 患者路径。

| 步骤 | 操作 | 通过信号 |
|---|---|---|
| 1 | 在适配器中心创建 HIS ADT 入站适配器 | 适配器 ID `his-adt-act9-2grf0t4vdy` 创建成功 |
| 2 | 绑定字段映射模板 | 模板口径见 [field-mapping-example-his-adt.json](../../contracts/integration/field-mapping-example-his-adt.json) |
| 3 | 创建 HIS 入站 Webhook 和联调单 | Webhook ID `his-inbound-act9-2grf0t4vdy`，联调单 `onboard-his-act9-2grf0t4vdy` |
| 4 | HIS 发送 ADT_A01 | `/engine/integration/webhooks/{webhookId}/inbound` 返回 `200`，`status=SUCCESS` |
| 5 | 创建入院临床事件 | `/engine/clinical-events` 返回 `201`，事件详情可查 |
| 6 | 读取患者路径 | `/engine/pathway/patient-pathways/{id}` 返回 `200` |

报文形态：

```json
{
  "messageId": "msg-act9-...-his-adt",
  "adapterId": "his-adt-act9-...",
  "sourceSystem": "HIS",
  "eventType": "ADT_A01",
  "payload": {
    "patient": { "mpi": "mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY", "name": "脱敏-张建国" },
    "encounter": { "encounterId": "enc-act6-8oh7bn024a", "encounterType": "INPATIENT" },
    "diagnosis": { "code": "J15.9", "displayName": "细菌性肺炎，未特指" }
  }
}
```

## 4. C2 LIS 危急值结果

LIS 发送血钾危急值，MedKernel 通过同一入站机制生成临床事件，并让医生端可以查询到对应推荐卡。这条链路替代幕6的人工注入，用于证明检验系统真实可接入。

| 步骤 | 操作 | 通过信号 |
|---|---|---|
| 1 | 创建 LIS 入站适配器与 Webhook | `lis-result-act9-2grf0t4vdy`、`lis-inbound-act9-2grf0t4vdy` |
| 2 | LIS 发送危急值 | 入站接口返回 `200`，映射字段数大于 0 |
| 3 | 登记 `REPORT` 临床事件 | 事件创建 `201`，详情查询 `200` |
| 4 | 医生查询推荐卡 | `/engine/recommendations/clinical-cards` 返回 `200` |

核心观察值为 LOINC `2823-3` 血钾，值 `6.9 mmol/L`，危急标识 `CRITICAL`。

## 5. C3 FHIR R4 门面

FHIR 网关或第三方系统通过统一 FHIR R4 门面写入和读取 Patient、Observation。写入需要携带适配器 ID、时间戳、签名和包版本，查询通过资源类型和资源 ID 读取。

| 动作 | 路径 | 幕9结果 |
|---|---|---|
| 创建 Patient | `POST /engine/integration/fhir/R4/Patient` | `201` |
| 创建 Observation | `POST /engine/integration/fhir/R4/Observation` | `201` |
| 读取 Patient | `GET /engine/integration/fhir/R4/Patient/{id}` | `200` |
| 读取 Observation | `GET /engine/integration/fhir/R4/Observation/{id}` | `200` |
| 搜索 Observation | `GET /engine/integration/fhir/R4/Observation?page=1&size=10` | `200` |

Observation 示例对应血钾 `6.9 mmol/L`，subject 指向幕6患者，encounter 指向幕6就诊。

## 6. C4 HIS 内嵌临床终端

HIS 页面通过 Origin 白名单和一次性启动令牌拉起 MedKernel 临床终端。令牌只展示一次，不能写入文档、日志或工单。

| 步骤 | 操作 | 通过信号 |
|---|---|---|
| 1 | 登记可信 Origin | `/engine/embed/origins` 返回 `200` |
| 2 | 医生签发启动令牌 | `/engine/embed/launch-tokens` 返回 `200`，证据只保留 traceId |
| 3 | HIS 调用 launch | `/engine/embed/launch` 返回 `200` |
| 4 | 读取上下文 | 返回 patientId、encounterId、`connectionStatus=CONNECTED`、`modelStatus=MODEL_DISABLED` |

`MODEL_DISABLED` 是诚实状态，表示当前演练没有把模型 Provider 伪装成在线；主链路仍可运行。

## 7. C5 质控告警出站 Webhook

质控平台可以接收 MedKernel 出站事件。幕9故意把目标设为 TEST-NET-3 断连地址，验证断连时的重试、死信和人工重放。

| 步骤 | 操作 | 通过信号 |
|---|---|---|
| 1 | 创建出站适配器 | `quality-dead-act9-2grf0t4vdy` |
| 2 | 登记质控告警出站消息 | `/engine/integration/messages/outbound` 返回 `200` |
| 3 | 执行健康检查 | 适配器不伪装成功，返回断连状态 |
| 4 | 执行两次重试 | 第一次 `NOT_CONNECTED`，第二次 `DEAD_LETTER` |
| 5 | 人工重放 | `/engine/integration/dead-letter/{messageId}/replay` 返回 `200` |

事件契约见 [integration-outbound-queued-event.v1.json](../../contracts/events/integration-outbound-queued-event.v1.json)。

## 8. C6 第三方知识运行时

第三方系统不直接读数据库，也不复制规则表；应通过稳定运行时查询当前有效包、写入标准上下文快照，并查询发布对账。

| 动作 | 路径 | 幕9结果 |
|---|---|---|
| 查询有效包 | `GET /engine/integration/knowledge-runtime/effective-package` | `200` |
| 写入上下文快照 | `POST /engine/integration/knowledge-runtime/context-snapshots` | `201` |
| 查询包对账 | `GET /engine/integration/knowledge-runtime/packages/{packageId}/reconciliation` | `200` |

上下文快照采用标准患者、就诊、诊断、观察值结构，来源系统字段保留 HIS/LIS 等原始来源，便于后续审计和问题追踪。

## 9. 联调验收单

正式交付给厂商时，按 [onboarding-acceptance-checklist.md](../../contracts/integration/onboarding-acceptance-checklist.md) 补齐：

| 检查项 | 通过口径 |
|---|---|
| 账号权限 | 只能操作自己负责的适配器、Webhook、联调单和日志 |
| 报文验签 | 无签名、签名错误、时间戳过期均被拒绝并留 traceId |
| 字段映射 | 必填字段缺失时返回可读错误，不写入半成品 |
| 健康状态 | `HEALTHY`、`NOT_CONNECTED`、`MISCONFIGURED`、`ERROR` 均可读 |
| 重试死信 | 失败不吞错，超限进入死信，可人工重放 |
| 证据归档 | 每个联调步骤都有请求、响应、traceId 和操作者 |

## 10. 找谁

- 适配器、Webhook、联调单、重试死信：信息科集成管理员。
- FHIR 资源映射与包版本：信息科 + 知识包负责人。
- 嵌入式终端 Origin、启动令牌、患者上下文：HIS 厂商 + 临床运行负责人。
- 质控告警出站：质控平台厂商 + 医务处质控员。
- 运行时有效包、上下文快照、发布对账：乙方实施或平台 SRE。
