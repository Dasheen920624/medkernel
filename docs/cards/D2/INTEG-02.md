# INTEG-02 · 第三方接口文档与契约模板

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.4 嵌入组件与载体（L271）· 落地规划 §11.5 引擎对接能力结论与详细规范引用（L786）· 核心 §10 集成边界 / §13 真实性。

## 身份
- 卡 ID：INTEG-02（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S2 院内系统接入（对接文档与契约）
- 依赖卡：[INTEG-01](INTEG-01.md)（对接总线，本卡为其产出标准化接入文档）· [OPT-01](OPT-01.md)（FHIR 门面契约）· [BASE-03](../D0/BASE-03.md)（API 契约规范）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
为第三方接入提供**标准化接口文档与契约模板**：接入概览 + OpenAPI 规范 + 字段映射模板 + 鉴权 + 幂等 + 回调 + 降级 + 审计要求。让外部厂商/院内信息科按**一份模板**完成对接，减少口口相传与隐性约定。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
对接能力（[INTEG-01](INTEG-01.md)）已建，但**缺统一对外接入文档/OpenAPI/契约模板**：
- 已有：`IntegrationController`/`IntegrationService` 端点、`Adapter`/`Webhook` DTO（实现侧）。
- 缺口（本卡补）：① **接入概览**（协议/数据流/边界）；② **OpenAPI** 规范（端点/DTO/错误码导出）；③ **字段映射模板**（外部 ↔ 标准上下文，接 [TERM-01](TERM-01.md)）；④ **鉴权/幂等/回调/降级/审计**对接要求清单；⑤ FHIR 门面接入指引（[OPT-01](OPT-01.md)）。

## 功能要求（原子可测条目）
- [x] **FR-1 接入概览**：文档化协议矩阵（FHIR / Webhook / 适配器）+ 数据流 + 集成边界（不绕引擎直写）。
- [x] **FR-2 OpenAPI**：从 [INTEG-01](INTEG-01.md)/[OPT-01](OPT-01.md) 端点生成/维护 OpenAPI，含 DTO + 错误码 + 示例。
- [x] **FR-3 字段映射模板**：标准化外部字段 ↔ 标准上下文（[API-01](API-01.md)）映射表模板，编码经 [TERM-01](TERM-01.md)。
- [x] **FR-4 对接要求清单**：鉴权（签名/令牌）+ 幂等键 + 回调约定 + 降级（`NOT_CONNECTED`/`NOT_SYNCED`）+ 审计字段要求。
- [x] **FR-5 验收模板**：第三方对接验收清单（连通/字段/幂等/降级/审计），对齐落地 §11.4。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：本卡**不新增运行端点**——产出 OpenAPI（`/v3/api-docs` 或导出文件）+ 文档；约束对象是 [INTEG-01](INTEG-01.md)/[OPT-01](OPT-01.md) 既有端点。
- DTO：以文档形式标准化既有 DTO，不新增实体。
- 响应信封：沿用 `ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）规范。
- 状态机：N·A（文档/契约卡）。
- 幂等 / 错误码 / traceId：文档化统一错误码字典 + 幂等键约定 + traceId 透传要求。
### 页面契约（页面卡）
N·A —— 本卡无页面。接入文档可在 **D6 开发者控制台**/适配器中心页链接呈现。

## 数据与迁移
- N·A —— 文档与契约模板卡，不落库。OpenAPI 与模板随仓库文档维护，CI 校验 OpenAPI 与实现一致。

## 视角清单（11 视角逐条）
1. **产品架构**：对接的"契约即文档"，降低集成熵。
2. **产品体验**：N·A —— 文档面向信息科/厂商；可读、可检索。
3. **系统与数据架构**：OpenAPI 与实现一致（CI 校验）；字段映射模板标准化。
4. **临床医疗安全**：文档明确"不绕引擎直写医嘱/病历/上报/支付"边界（核心 §10）。
5. **知识与数据治理**：字段映射经 [TERM-01](TERM-01.md) 编码归一可溯。
6. **安全合规与监管**：鉴权/审计要求文档化；对接留证清单。
7. **集团化与多租户治理**：多租户对接约定（org 作用域透传）文档化。
8. **集成与互操作**：★主战场 —— 标准接入文档 + OpenAPI + 映射模板（核心 §10）。
9. **运维 / SRE / 国产化**：降级约定（`NOT_CONNECTED`/`NOT_SYNCED`）文档化；国产中间件接入指引。
10. **质量与真实性审计**：★OpenAPI 与实现一致 CI 校验，**文档不得描述不存在的端点**（铁律 #1）。
11. **AI / 模型治理与可降级**：N·A —— 文档卡，无模型。

## 适用不变量
- 命中核心约束：**§10 集成边界** · **§13 真实性（文档=实现）** · **依赖 [INTEG-01](INTEG-01.md)/[OPT-01](OPT-01.md)/[BASE-03](../D0/BASE-03.md)**。
- 本卡落点：把对接从隐性约定变为一份可验收、与实现一致的标准契约文档。

## 验收 + 验证
- [x] **AC-1（FR-2）**：OpenAPI 导出覆盖 [INTEG-01](INTEG-01.md)/[OPT-01](OPT-01.md) 端点；CI 校验与实现一致（无幽灵端点）。
- [x] **AC-2（FR-3）**：字段映射模板可套用真实外部样例映射到标准上下文。
- [x] **AC-3（FR-1/4/5）**：接入概览 + 对接要求清单 + 验收清单完整，覆盖鉴权/幂等/回调/降级/审计。
- 关联 A1–A9 剧本：A1 接入（按文档完成对接验收）。
- T-GATE：真实性门禁全绿（OpenAPI=实现，无虚构端点）。
- B0 验收：N·A（文档卡，无模型；天然 B0）。

## 完工证据
- 文档 permalink：接入概览 + OpenAPI + 字段映射模板 + 对接要求/验收清单 + CI 一致性校验配置。
- 测试：OpenAPI 与实现一致性 CI 校验 + 映射模板样例校验。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

## PR1 交付记录（Codex · 2026-06-03）

- 接入契约包：新增 `docs/contracts/integration/third-party-integration-guide.md`、`integration-openapi.paths.json`、`field-mapping-template.json`、`field-mapping-example-his-adt.json`、`onboarding-acceptance-checklist.md`。
- OpenAPI：新增 `medkernel-third-party-integration` 分组，运行路径为 `/v3/api-docs/medkernel-third-party-integration`；快照只覆盖当前真实存在的 `IntegrationController` 端点，FHIR R4/R5 运行门面明确仍归 [OPT-01](OPT-01.md)，不在本卡伪造 URL。
- 一致性校验：新增 `IntegrationContractDocumentationTest`，反射比对 `IntegrationController` 端点与契约快照，并校验接入指南、字段映射模板和 HIS ADT 脱敏样例。
- TDD 红灯：`mvn -q -Dtest=IntegrationContractDocumentationTest test` 先失败于缺少 `third-party-integration-guide.md` / `integration-openapi.paths.json` / `field-mapping-template.json`；`mvn -q -Dtest=OpenApiContractConfigurationTest test` 先失败于缺少 `medkernelThirdPartyIntegrationOpenApi()`。
- 本地验证：`mvn -q -Dtest=IntegrationContractDocumentationTest,OpenApiContractConfigurationTest test`；`mvn -q -Dtest=IntegrationContractDocumentationTest,OpenApiContractConfigurationTest,ServiceContractGovernanceTest,IntegrationControllerSecurityTest test`；`mvn -q test`（Surefire XML 汇总 174 files / 1036 tests / 0 failures / 0 errors / 0 skipped，H2 + Docker PostgreSQL 15.18 / Oracle 21.3 均迁移至 v62，二次 migrate 无新迁移）；`npm run verify`（44 files / 236 tests，既有 React Router / act 警告归 `DEFER-003`）；`npm run build`（既有 `vendor-antd` 大 chunk 提示归 `DEFER-003`）；`npm audit --omit=dev --audit-level=moderate`（0 vulnerabilities；`npm ci` 的 dev 依赖告警仍归 `DEFER-002`）；changed-mode T-GATE：真实性 0 文件 / 配置边界 0 文件 / 迁移规约 0 文件均通过；`scripts/check-comment-zh.sh`；`git diff --check`。
