# INTEG-01 · 第三方对接总线

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.3 第三方对接能力全景（L211，门面归 [OPT-01](OPT-01.md)）· §1.5.4 嵌入组件与载体（L271）· 落地规划 §11.2 当前核心 API 域（L716）/ §11.4 第三方对接验收（L764）· FOUNDATION §3.1 第三方对接能力（L95）· 核心 §10 集成互操作边界。

## 身份
- 卡 ID：INTEG-01（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S2 院内系统接入
- 依赖卡：[OPT-01](OPT-01.md)（FHIR 门面，挂在本总线上）· [API-01](API-01.md)（标准上下文落地）· [BASE-03](../D0/BASE-03.md)（契约）· [BASE-04](../D0/BASE-04.md)（对接审计）· [OBS-01](../D0/OBS-01.md)（健康/trace）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供第三方对接**总线**：适配器目录 + FHIR/CDS Hooks 风格门面挂载 + Webhook 签名 + 字段映射 + 健康检查 + 重试死信。让 HIS/EMR/LIS/PACS/医保/病案等经**统一适配器链路**接入；**断连诚实标 `NOT_CONNECTED`、不伪造连接、同步超时不阻断医生主流程**（核心 §10）。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/integration` **已建适配器/Webhook/消息日志**，本卡＝**契约化 + 健康检查/重试死信/字段映射补全**：
- 已有：`IntegrationAdapter`、`IntegrationMessageLog`、`IntegrationWebhookConfig`、`AdapterCreateDto`/`UpdateDto`、`WebhookCreateDto`/`TestDto`/`TestResultDto`、`IntegrationService`、`IntegrationController`、各 Repository（含 `protocol_type` 枚举提及 `FHIR`）。
- 缺口（本卡补）：① **适配器目录**统一登记/状态（连通/断连）；② **健康检查** + `NOT_CONNECTED` 诚实标；③ **重试 + 死信**队列；④ **字段映射**接 [TERM-01](TERM-01.md) 编码归一；⑤ FHIR 门面（[OPT-01](OPT-01.md)）挂载本总线、CDS Hooks 门面后移 D3（OPT-02）。

## 功能要求（原子可测条目）
- [x] **FR-1 适配器目录**：登记/启停适配器（HIS/EMR/LIS/PACS/医保/病案…），统一 `IntegrationAdapter` + 连通状态。PR1：`adapter_id` 收敛为租户内唯一，清理全局仓储查找，前后端目录继续只按当前租户读取。
- [ ] **FR-2 健康检查**：周期探活 → 断连标 `NOT_CONNECTED`（不伪造连接）；状态供 [OBS-01](../D0/OBS-01.md)/工作台。PR1 已完成手动健康检查与健康目录汇总：`GET /api/v1/engine/integration/health`、`POST /api/v1/engine/integration/adapters/{id}/health-check`；周期调度待真实连接器接入后继续补齐，不伪造成已完成。
- [x] **FR-3 Webhook 签名**：入站 Webhook 验签（`IntegrationWebhookConfig`）；验签失败拒绝 + 记 `IntegrationMessageLog`。PR2：新增 `POST /api/v1/engine/integration/webhooks/{id}/inbound`，按 `timestamp + "." + canonical request` 做 HMAC-SHA256 验签，并校验 5 分钟时间窗口防重放；失败拒绝并写 `FAILED` 入站日志，成功后按 `message_id + tenant_id` 幂等。
- [x] **FR-4 字段映射**：外部字段 ↔ 标准上下文（[API-01](API-01.md)）字段映射，编码经 [TERM-01](TERM-01.md) 归一。PR2：适配器 `configJson.fieldMappings` 使用 JSON Pointer 映射到标准上下文载荷 `mappedPayload`；带 `termMappingId` 的字段只允许 TERM-01 `CONFIRMED` 映射 + `ACTIVE` 标准术语归一，不猜测标准码、不直写医嘱/病历。
- [ ] **FR-5 重试 + 死信**：失败消息按策略重试，超限入死信队列可人工重放；不静默丢。
- [ ] **FR-6 不阻断主流程**：同步超时/断连**不阻断**医生临床主流程，降级标记 + 异步补偿（核心 §10）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/integration/**`（adapters、webhooks、webhooks/{id}/inbound、health、message-logs、dead-letter、replay）。
- DTO：复用 `AdapterCreateDto`/`UpdateDto`/`WebhookCreateDto`/`TestDto`/`TestResultDto`/`IntegrationMessageLog`；PR2 新增 `WebhookInboundRequestDto` / `WebhookInboundResultDto`。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：消息走核心 §3 待办/任务态 + 死信；适配器连通态。
- 幂等 / 错误码 / traceId：入站按 `tenant_id + message_id` 幂等；断连 → `NOT_CONNECTED`；验签失败 → `ENG-INTEG-004`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。呈现在 **D2 适配器中心页**（适配器状态/健康/死信/字段映射）；本卡供引擎。

## 数据与迁移
- 表族（已有）：`integration_adapter`/`integration_message_log`/`integration_webhook_config`；字段映射规则随适配器 `configJson.fieldMappings` 存储，入站处理结果写 `integration_message_log.payload`。
- 主键 ULID；PR1/PR2 已把适配器、消息、Webhook 唯一性收敛为租户内唯一：`(tenant_id, adapter_id)`、`(tenant_id, message_id)`、`(tenant_id, webhook_id)`。
- 5 方言迁移一致 + 中文注释；PR2 新增 V60/V61（H2/PostgreSQL/Oracle/DM/Kingbase 静态一致，真实运行当前保障 PostgreSQL + Oracle，DM/Kingbase 真实环境按 `DEFER-001` 后续适配）。

## 视角清单（11 视角逐条）
1. **产品架构**：外部系统统一接入总线；FHIR 门面（[OPT-01](OPT-01.md)）+ 院内适配器双路。
2. **产品体验**：N·A —— 适配器中心页（D2）呈现连通/健康/死信。
3. **系统与数据架构**：重试死信不丢消息；健康探活；消息幂等；高吞吐可观测。
4. **临床医疗安全**：★同步超时/断连**不阻断主流程**；外部数据经标准上下文不绕引擎直写医嘱/病历。
5. **知识与数据治理**：字段映射经 [TERM-01](TERM-01.md) 编码归一可溯。
6. **安全合规与监管**：Webhook 验签；对接消息全留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：适配器按 org 作用域；集团统一协议、院内实例。
8. **集成与互操作**：★主战场 —— 统一适配器链路 + 门面 + 健康 + 重试死信（核心 §10）；断连 `NOT_CONNECTED`。
9. **运维 / SRE / 国产化**：5 方言；死信重放；内外网；国产中间件兼容。
10. **质量与真实性审计**：★断连不伪造连接、超时不假成功（铁律 #2）；消息日志真实。
11. **AI / 模型治理与可降级**：总线 B0 确定性；无模型依赖；关模型对接不受影响。

## 适用不变量
- 命中核心约束：**§10 集成边界 / 不绕引擎直写 / 不阻断主流程** · **铁律 #2 断连不伪造** · **依赖 [OPT-01](OPT-01.md)/[API-01](API-01.md)/[TERM-01](TERM-01.md)**。
- 本卡落点：把第三方对接做成统一、可观测、诚实降级、不丢消息的总线。

## 验收 + 验证
- [ ] **AC-1（FR-1/2）**：登记适配器 + 健康探活；断连 → `NOT_CONNECTED`（不伪造）。PR1 已红绿覆盖跨租户同名适配器、租户内冲突、健康汇总不串租户和不伪造 `HEALTHY`；周期探活未冒领完成。
- [x] **AC-2（FR-3/4）**：Webhook 验签失败拒绝 + 记日志；外部字段映射到标准上下文、编码归一。PR2 已覆盖验签失败日志、验签成功映射、TERM-01 确认映射归一、配置错误失败日志和租户内幂等。
- [ ] **AC-3（FR-5/6）**：失败消息重试 → 超限入死信可重放；同步超时不阻断主流程（降级标记）。
- 关联 A1–A9 剧本：A1 接入、A6 合规（对接审计）。
- T-GATE：真实性门禁全绿（断连/超时不伪造）。
- B0 验收：确定性总线，**天然 B0**。

## 完工证据
- 代码 permalink：`IntegrationService` 健康/重试/死信 + Webhook 验签 + 字段映射接 [TERM-01](TERM-01.md) + `NOT_CONNECTED` + 5 方言迁移。
- 测试：健康探活/断连测试 + 验签测试 + 字段映射测试 + 重试死信/重放测试 + 不阻断主流程测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

### PR1 证据（适配器目录 / 健康检查 / NOT_CONNECTED）
- 代码范围：`IntegrationService.checkAdapterHealth`、`getAdapterHealthSummary`、`IntegrationController` 的 `/health` 与 `/health-check`、前端 `AdapterHub` 健康检查调用、V59 五方言 `integration_adapter` 租户内唯一约束。
- 测试：`IntegrationServiceTest.adapterCodeIsUniqueOnlyInsideTenantScope`、`healthSummaryIsTenantScopedAndNeverFakesConnectivity`、`healthCheckHonestlyReportsConfigStateNeverFakesHealthy`；`IntegrationControllerSecurityTest` 覆盖健康目录匿名拒绝与缺租户拒绝；`MigrationBaselineContractTest.integrationAdapterUniqueConstraintIsTenantScopedInEveryDialect` 覆盖五方言 V59 约束。
- 红绿证据：先补 `IntegrationServiceTest` / `MigrationBaselineContractTest` 期望，红灯暴露缺少健康检查服务、健康汇总 DTO 与 V59 租户内唯一约束；实现后目标套件转绿。
- 已运行：`mvn -q -Dtest=IntegrationServiceTest,IntegrationControllerSecurityTest,MigrationBaselineContractTest,ServiceContractGovernanceTest test`；`mvn -q -Dtest=FlywayMultiDialectSmokeTest,MigrationBaselineContractTest,H2BaselineMigrationTest test`；`mvn -q test`（Docker Testcontainers PostgreSQL 15.18 / Oracle 21.3 + H2 均迁移至 v59，二次 migrate 无新迁移）；`npm run verify`（44 files / 236 tests）；`npm run build`；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`；`scripts/check-comment-zh.sh`；`git diff --check`。
- 提交后 changed-mode 守卫：`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（扫描 12 文件）、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`（扫描 10 文件）、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（扫描 5 文件）均通过。PR1 合入后继续 PR2 Webhook 入站验签 + 字段映射。

### PR2 证据（Webhook 入站验签 / 字段映射 / TERM-01 归一）
- 代码范围：`IntegrationService.ingestWebhook`、`IntegrationController` 的 `/webhooks/{id}/inbound`、`WebhookInboundRequestDto` / `WebhookInboundResultDto`、`StandardTerm.statusName`、V60/V61 五方言租户内唯一约束；同时删除 `IntegrationWebhookConfigRepository.findByWebhookId` 全局查找旧口径。
- 测试：`IntegrationServiceTest.inboundWebhookRejectsInvalidSignatureAndStoresFailedMessageLog`、`inboundWebhookRejectsStaleTimestampEvenWhenSignatureMatches`、`inboundWebhookVerifiesSignatureMapsFieldsAndNormalizesCodesByConfirmedTermMapping`、`inboundWebhookStoresFailedLogWhenFieldMappingConfigurationIsInvalid`、`inboundMessageIdempotencyIsScopedByTenant`、`webhookIdIsUniqueOnlyInsideTenantScope`；`IntegrationControllerSecurityTest` 覆盖匿名拒绝和缺租户拒绝；`MigrationBaselineContractTest` 覆盖 V60/V61 五方言约束。
- 红绿证据：先补入站验签 / 字段映射 / 租户内唯一约束测试，红灯暴露缺少入站 DTO、端点、V60/V61、映射失败未落日志以及签名正确但时间戳过旧仍会继续处理；实现后目标套件转绿。
- 已运行：`mvn -q -Dtest=IntegrationServiceTest#inboundWebhookStoresFailedLogWhenFieldMappingConfigurationIsInvalid test`；`mvn -q -Dtest=IntegrationServiceTest#inboundWebhookRejectsStaleTimestampEvenWhenSignatureMatches test`；`mvn -q -Dtest=IntegrationServiceTest,IntegrationControllerSecurityTest,MigrationBaselineContractTest,ServiceContractGovernanceTest test`；`mvn -q -Dtest=FlywayMultiDialectSmokeTest,H2BaselineMigrationTest,MigrationBaselineContractTest test`；`mvn -q test`（Docker Testcontainers PostgreSQL 15.18 / Oracle 21.3 + H2 均迁移至 v61，二次 migrate 无新迁移）；`node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs`；`scripts/check-comment-zh.sh`；`git diff --check`；提交后 changed-mode T-GATE：`node scripts/authenticity-guard.mjs --mode=changed --base=origin/main`（扫描 8 文件）、`node scripts/config-boundary-guard.mjs --mode=changed --base=origin/main`（扫描 8 文件）、`node scripts/migration-convention-guard.mjs --mode=changed --base=origin/main`（扫描 10 文件）均通过。

## 大卡工序（6d，后端引擎；按 PR 拆分）
- PR1：适配器目录 + 手动健康检查 + 健康汇总 + `NOT_CONNECTED` → AC-1 地基（#291 已合入；周期探活不冒领）。
- PR2：Webhook 验签 + 字段映射接 [TERM-01](TERM-01.md) → AC-2（本分支实施，本地验证与 T-GATE 已通过，待 PR / CI）。
- PR3：重试死信 + 重放 + 不阻断主流程降级 → AC-3。
