# 幕9 · 第三方对接能力案例集证据

> 结论：幕9已在 134 真实跑通 HIS、LIS、FHIR、嵌入式终端、出站 Webhook、第三方知识运行时 6 个案例。C5 使用 TEST-NET-3 断连地址证明重试与死信链路，不伪造外部厂商成功接收。

## 运行概要

| 项 | 值 |
|---|---|
| 环境 | `https://193.112.107.134` |
| 批次 | `act9-2grf0t4vdy` |
| 租户 | `drill-hospital-20260611` |
| 复用患者 | `mpi-01KTSWK7P3XQQ8VC0JZFZ63AMY` |
| 复用就诊 | `enc-act6-8oh7bn024a` |
| 复用路径 | `pp-9b4c8389-c46e-4a12-bf5f-6d66be5768fc` |
| 复用配置包 | `DRILL.ACT8.CONFIG.ACT8-8SINB347C5@2026.06.11-act8-8sinb347c5` |
| 结果 | C1-C6 全部 `PASSED` |

## 证据清单

| 环节 | 证据 | 验收点 |
|---|---|---|
| 发布修复 | [00-backend-deploy-act9-runtime-fixes.json](00-backend-deploy-act9-runtime-fixes.json) | 嵌套事务隔离、嵌入令牌 SQL 修复已发布到 134，readiness 为 `UP` |
| 角色与集成基线 | [00-readiness-actors-integrations.json](00-readiness-actors-integrations.json) | 信息科、医院管理员、医务处、医生、质控角色均可登录；适配器健康入口可读 |
| 适配器、Webhook、联调单 | [01-adapter-webhook-onboarding.json](01-adapter-webhook-onboarding.json) | HIS/LIS/FHIR/质控出站适配器和联调单创建成功；一次性密钥已脱敏 |
| C1 HIS 入院消息 | [02-case-c1-his-adt-admission.json](02-case-c1-his-adt-admission.json) | Webhook 验签入站、字段映射、临床事件落库、CAP 患者路径可读 |
| C2 LIS 危急值 | [03-case-c2-lis-critical-result.json](03-case-c2-lis-critical-result.json) | LIS 入站替代人工注入，危急值临床事件和推荐卡查询链路返回 200 |
| C3 FHIR R4 | [04-case-c3-fhir-patient-observation.json](04-case-c3-fhir-patient-observation.json) | Patient/Observation create、read、search 均通过 |
| C4 HIS 嵌入临床终端 | [05-case-c4-his-embedded-terminal.json](05-case-c4-his-embedded-terminal.json) | Origin 白名单、一次性启动令牌、launch 兑换患者上下文均通过；令牌值不入库 |
| C5 质控出站 Webhook | [06-case-c5-quality-webhook-retry-dead-letter.json](06-case-c5-quality-webhook-retry-dead-letter.json) | 出站消息断连、重试、进入死信、人工重放闭环可查 |
| C6 第三方知识运行时 | [07-case-c6-third-party-knowledge-runtime.json](07-case-c6-third-party-knowledge-runtime.json) | 有效包查询、标准上下文快照写入、发布对账查询均通过 |
| 总览与 traceId | [08-act9-third-party-case-overview.json](08-act9-third-party-case-overview.json)、[trace-ids.txt](trace-ids.txt) | 6 案例总览、53 条请求 traceId、最终健康状态 |

## 幕8.5 前台复演

幕8.5 第三批补齐幕9客户视角页面证据：信息科管理员在 `/adapter/hub` 前台查看适配器总览、健康诊断、死信重放、数据质量、接入向导与区域来源。截图统一落在 [ui-replay/](ui-replay/)。

| 角色 | 页面路由 | 前台操作 | 截图 |
|---|---|---|---|
| 信息科管理员 | `/adapter/hub` | 查看连接率、未连接数、字段映射覆盖与适配器清单 | [01-adapter-hub-overview.png](ui-replay/01-adapter-hub-overview.png) |
| 信息科管理员 | `/adapter/hub` | 触发一次健康诊断，页面只展示后端真实状态 | [02-adapter-health-diagnosis.png](ui-replay/02-adapter-health-diagnosis.png) |
| 信息科管理员 | `/adapter/hub` | 查看失败、重试或死信重放页 | [03-adapter-dead-letter.png](ui-replay/03-adapter-dead-letter.png) |
| 信息科管理员 | `/adapter/hub` | 查看数据质量看板的必填率、映射率和时效率入口 | [04-adapter-data-quality.png](ui-replay/04-adapter-data-quality.png) |
| 信息科管理员 | `/adapter/hub` | 查看接入向导与必接系统状态 | [05-adapter-onboarding.png](ui-replay/05-adapter-onboarding.png) |
| 信息科管理员 | `/adapter/hub` | 查看区域来源入口和数据接入契约提示 | [06-adapter-regional-source.png](ui-replay/06-adapter-regional-source.png) |

四问结论：适配器状态页能让客户读懂 `NOT_CONNECTED`、`MISCONFIGURED` 与 `DEAD_LETTER`，没有把未接通系统伪装成绿色；但 C1-C6 六案例与页面状态之间仍缺演示视角映射，登记 `UI-ACT9-ADAPTER-01`，后续可增加案例视图分组。

## 真实限制

- 本地脚本访问 134 仍需关闭 TLS 证书校验，原因是演练环境使用自签证书；正式部署必须替换为院方信任证书。
- C5 的 `203.0.113.10` 是 TEST-NET-3 断连目标，只用于证明 `NOT_CONNECTED`、重试、`DEAD_LETTER` 与人工重放，不代表厂商实际接收。
- C3 的 FHIR 出站补偿目标是本地占位地址，主写入、查询和映射链路真实通过；外部推送失败状态按证据如实保留。
- C1 复用幕6已在径 CAP 患者路径作为真实锚点；本幕新增的是第三方 ADT 入站、映射和临床事件证据。
