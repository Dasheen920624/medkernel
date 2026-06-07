# OPT-01 · 标准临床模型与 FHIR R4/R5 门面

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.1 对接方式·FHIR 门面（L174）· §1.5.3 第三方对接能力全景·标准互操作门面（L211）· §7.4 标准临床模型（L1378）· 落地规划 §11.3 院内系统对接（L741）· 核心 §10 集成互操作边界。

## 身份
- 卡 ID：OPT-01（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S2 院内系统接入（标准互操作门面）
- 依赖卡：[SYS-01](../D0/SYS-01.md)（12 标准对象）· [API-01](API-01.md)（共用 `CanonicalResource` 上下文）· [INTEG-01](INTEG-01.md)（适配器总线承载门面）· [BASE-03](../D0/BASE-03.md)（API 契约）· [TERM-01](TERM-01.md)（编码字典映射）
- 工作量：6d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标

提供 **FHIR R4/R5 标准互操作门面**：把 10 类核心 FHIR 资源（Patient / Encounter / Condition / Observation / Medication / Procedure / CarePlan / ServiceRequest / DiagnosticReport / DocumentReference）**双向映射**到院内 `CanonicalResource`，让标准化程度高的医院/集团平台经 FHIR 接入，**同时保留院内私有适配器**；门面是**协议转换层**，不替代院内适配、不绕引擎直写医疗结论（核心 §10）。

## 现状（搬迁时核查 2026-05-30；2026-06-03 PR4 施工复核）

本卡仍按 PR 拆分推进，现状以 `medkernel-backend/src` 为准：

- 已有 [SYS-01](../D0/SYS-01.md) 标准临床模型与 `StandardClinicalFhirMappingRegistry`，可声明 12 类 `CanonicalResourceType` 到 FHIR R4 资源类型的参考映射；本卡必须复用该模型，**不得重定义第二份临床模型**。
- 已有自有 `CanonicalResource(+Type/Repository)` 与 `engine/context/canonical/` 12 子类型（[API-01](API-01.md)/[SYS-01](../D0/SYS-01.md)）——这是门面的映射目标。
- PR1 新增 `com.medkernel.engine.integration.fhir` 下的 R4 映射层与映射证据仓储，先覆盖 Patient 出站与 Observation 入站的确定性映射。
- PR2 新增 R5 映射器、R4/R5 共用映射支持、CapabilityStatement 映射能力声明、TERM-01 真实字典映射端口和 FHIR `OperationOutcome` JSON 工厂。
- PR3 新增运行门面 `/api/v1/engine/integration/fhir/{version}/metadata` 与 `/api/v1/engine/integration/fhir/{version}/{resourceType}`，挂 INTEG-01 总线，完成 Observation 受控 create、`NOT_CONNECTED`、签名 / 白名单 / 脱敏响应和 MedicationRequest / ServiceRequest 医师确认任务。
- PR4 补齐 10 类核心资源 read/search/create：`GET /{version}/{resourceType}/{id}` 通过 `mk_fhir_resource_mapping` 读取已登记映射，`GET /{version}/{resourceType}` 返回 FHIR `Bundle` searchset，`POST /{version}/{resourceType}` 对 Patient / Encounter / Condition / Observation / Medication / Procedure / CarePlan / DiagnosticReport / DocumentReference 受控落 `CanonicalResource` 并回流临床事件；ServiceRequest 仍只登记医师确认任务，不直写申请单。

## 功能要求（原子可测条目）

- [x] **FR-1 10 类 FHIR 资源门面**：暴露受控 FHIR 端点覆盖 Patient/Encounter/Condition/Observation/Medication/Procedure/CarePlan/ServiceRequest/DiagnosticReport/DocumentReference 的 read/search/受控 create；MedicationRequest 作为高风险医嘱类兼容入口仅登记医师确认任务，MedicationStatement 不在本卡 10 类核心范围。
- [x] **FR-2 双向映射到 CanonicalResource**：FHIR 资源 ↔ `engine/context/canonical` 12 类**映射**（入：FHIR → Canonical；出：Canonical → FHIR），映射规则版本化、可追溯；**不重定义院内模型**。
- [x] **FR-3 R4/R5 双版本**：同一资源支持 R4 与 R5 输出（version-aware 序列化），经 `CapabilityStatement` 声明支持的资源与交互范围。
- [x] **FR-4 门面不绕引擎**：FHIR 写入一律经[标准上下文](API-01.md)/临床事件入引擎，**不直写医嘱/病历/法定上报/支付/设备控制**（核心 §10/#10）；高风险写触发医师确认链（核心 §6/#10）。
- [x] **FR-5 字段映射可追溯 + 缺失诚实**：字段映射率/缺失率/转换规则可统计；未映射字段**诚实标记**（`OperationOutcome` warning），不伪造、不静默丢弃。
- [x] **FR-6 保留院内适配器并存**：FHIR 门面与院内私有适配器**并存**，不要求医院一次性标准化改造；门面/外部断连诚实标 `NOT_CONNECTED`，不阻断院内适配链路。
- [x] **FR-7 编码经字典映射**：FHIR `CodeableConcept`/`Coding` ↔ 标准字典（ICD/LOINC/药品本位码）经 [TERM-01](TERM-01.md) 映射，**禁字符 LCS**，高危近似强制 HIGH（核心 §7）。

## 接口契约 / 页面契约

### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/integration/fhir/{version}/metadata`（CapabilityStatement）、`GET /api/v1/engine/integration/fhir/{version}/{resourceType}/{id}`（read）、`GET /api/v1/engine/integration/fhir/{version}/{resourceType}`（search Bundle）、`POST /api/v1/engine/integration/fhir/{version}/{resourceType}`（受控 create）；门面挂 [INTEG-01](INTEG-01.md) 适配器总线下。
- DTO：对外 FHIR 资源经**映射层**转/自 `CanonicalResource`；对内仍 Record DTO + Bean Validation（核心 #7）；FHIR 资源校验经 profile（可选对齐中国核心数据集）。
- 响应信封：门面层用 FHIR `Bundle`/`OperationOutcome`；对内仍 `ApiResult`/`ProblemDetail`，二者**映射可逆**（[BASE-03](../D0/BASE-03.md)）。
- 状态机：N·A —— 门面是**协议转换层**，资产状态机在被映射的实体（上下文/知识/规则），门面自身无状态流转。
- 幂等 / 错误码 / traceId：FHIR conditional create 按 `(tenant, identifier)` 幂等；映射失败/未映射 → `OperationOutcome`（issue 级别 error/warning）；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。

### 页面契约（页面卡）
N·A —— 本卡无独立页面。门面健康/字段映射率在 **D2 适配器中心页**（[INTEG-01](INTEG-01.md) 承接）只读呈现；CDS Hooks 风格事件归 [OPT-02](../D3/OPT-02.md)（D3）。

## 数据与迁移
- 表族：`mk_fhir_resource_mapping`（FHIR `(version, resourceType, id)` ↔ `canonical_resource_id` 映射证据）· `mk_fhir_mapping_rule`（字段映射规则 + 版本 + R4/R5）· 复用 `canonical_resource`（[SYS-01](../D0/SYS-01.md)/[API-01](API-01.md)，本卡读/映射）。表名前缀遵守 `mk_<域>_<实体>` 迁移规约，替代搬迁草案中的裸表名。
- 主键：关系库自增主键；唯一约束：`(tenant_id, fhir_version, fhir_resource_type, fhir_id)`、`(tenant_id, canonical_resource_id, fhir_version)`；索引：`canonical_resource_id`、`fhir_resource_type`。
- 组织字段：`tenant_id` + `org_path` + 审计字段（映射动作留痕，[BASE-04](../D0/BASE-04.md)）。
- 5 方言迁移：h2/postgres/oracle/dm/kingbase 一致 + 中文注释 + 映射唯一约束。

## 视角清单（11 视角逐条）
1. **产品架构**：FHIR 门面 = 标准互操作**单一入口**，全部映射到 `CanonicalResource` 单一源；不因"对外标准化"而养出第二份临床模型。
2. **产品体验**：N·A —— 本卡无客户面页面；门面健康与映射率在适配器中心页（[INTEG-01](INTEG-01.md)）呈现，技术字段进专家模式。
3. **系统与数据架构**：★R4/R5 双版本 version-aware 映射 + `CapabilityStatement`；映射层无状态可水平扩展；`CanonicalResource` 关系库权威，门面非权威可重建。
4. **临床医疗安全**：★FHIR 写**不绕引擎、不直写**医嘱/病历/上报/支付/设备（核心 §10/#10）；高风险写经医师确认链（核心 §6）；门面不自动产生临床结论。
5. **知识与数据治理**：FHIR `CodeableConcept` ↔ 标准字典经 [TERM-01](TERM-01.md) 语义映射（禁 LCS、高危 HIGH，核心 §7）；映射规则来源可追溯、版本化。
6. **安全合规与监管**：FHIR 端点签名/白名单/最小字段/脱敏/审计（核心 §8）；SMART on FHIR / launch token 受控（详规 §1.5.5）；数据出境经合规评估。
7. **集团化与多租户治理**：门面经 `OrgContext` 作用域（[BASE-01](../D0/BASE-01.md)）；集团平台跨院 FHIR 访问按租户隔离，不串院。
8. **集成与互操作**：★主战场 —— FHIR R4/R5 资源门面（核心 §10）；与院内私有适配器**并存不替代**；CDS Hooks 风格事件由 [OPT-02](../D3/OPT-02.md) 承接、适配器总线由 [INTEG-01](INTEG-01.md) 承载，本卡专注资源门面与映射。
9. **运维 / SRE / 国产化**：门面非权威、可关；断连诚实标 `NOT_CONNECTED` 不伪造；内外网双形态（内网院内 FHIR、外网 SaaS）；5 方言。
10. **质量与真实性审计**：无伪造 FHIR 资源、无假映射；字段映射率/缺失率可统计；未映射**诚实 `OperationOutcome`**（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：门面**纯确定性映射、天然 B0**；语义辅助映射建议（B1）整体后移第二波，B0 用确定性字段映射规则，关模型不影响门面。

## 适用不变量
- 命中核心约束：**§10 FHIR 门面 + 不绕引擎 + 断连诚实 + 门面不替代院内适配** · **#5 关系库权威（`CanonicalResource`）** · **§7 字典语义映射** · **#8 安全脱敏加密** · **#10 医师确认**。
- 本卡落点：FHIR R4/R5 资源门面只做**协议↔CanonicalResource 的可逆映射**，标准化接入与院内适配两路并存，所有写入回流引擎，杜绝"FHIR 直写"与"第二份临床模型"。

## 验收 + 验证
- [x] **AC-1（FR-1/2）**：经 FHIR 门面 read 一个 Patient/Condition/Observation，返回 FHIR 资源且字段与院内 `CanonicalResource` 一致；create 一个 Observation → 正确映射落 `canonical_resource` 并经引擎入口。
- [x] **AC-2（FR-3）**：同一资源分别请求 R4 与 R5 → 两版结构正确、`/metadata` CapabilityStatement 声明范围与实际一致。
- [x] **AC-3（FR-4）**：经 FHIR create 一条高风险医嘱类资源 → 系统**不自动写医嘱/病历**，回流引擎并要求医师确认（核心 §10/#10）。
- [x] **AC-4（FR-5/7）**：含未映射本地编码的资源 → `OperationOutcome` warning 列出未映射项，字段映射率可统计，**无伪造映射**。
- [x] **AC-5（FR-6）**：关闭 FHIR 门面/外部 → 门面标 `NOT_CONNECTED`，院内私有适配器链路与医生主流程不受影响。
- 关联 A1–A9 剧本：A2 院内接入（FHIR 与院内适配两路）、A7 标准互操作/区域共享。
- T-GATE：前后端真实性门禁全绿（无假 FHIR 资源/无伪造映射哈希；迁移 5 方言一致）。
- B0 验收：门面纯确定性映射、无模型依赖，**天然 B0**（关闭全部模型后映射不变）。

## 完工证据
- 代码 permalink：10 类 FHIR 资源门面端点 + R4/R5 映射层 + `CapabilityStatement` + `mk_fhir_resource_mapping`/`mk_fhir_mapping_rule` 迁移（×5 方言）+ 引擎回流入口。
- 测试：FHIR↔Canonical 双向映射往返测试 + R4/R5 双版本测试 + "FHIR 写不绕引擎"安全测试 + 未映射 `OperationOutcome` 测试 + 断连 `NOT_CONNECTED` 测试。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。

### PR1 阶段证据（R4 映射地基，不代表整卡完成）
- 新增 `FhirR4CanonicalMapper`：复用 `CanonicalPatient` / `CanonicalObservation`，Patient 出站不臆造缺失字段，Observation 入站对非 LOINC 本地编码返回 OperationOutcome 风格 warning 与 `PARTIAL` 质量状态。
- 新增 `mk_fhir_resource_mapping` / `mk_fhir_mapping_rule` V63 五方言迁移、仓储与 owner 前缀 `mk_fhir_`。
- 本地红绿证据：`mvn -q -Dtest=FhirR4CanonicalMapperTest,FhirResourceMappingRepositoryTest test`；聚焦回归：`mvn -q -Dtest=FhirR4CanonicalMapperTest,FhirResourceMappingRepositoryTest,StandardClinicalFhirMappingRegistryTest,StandardClinicalModelContractTest,CanonicalResourceRepositoryTest,ContextSnapshotRepositoryTest,DomainOwnershipContractTest test`。
- 本地全量证据：`mvn -q test`（Surefire XML 汇总 176 files / 1057 tests / 0 failures / 0 errors / 0 skipped；H2/PostgreSQL 15.18/Oracle 21.3 均验证 63 个迁移、应用到 v63 且二次 migrate 无新迁移）；`npm run verify`（44 files / 236 tests）；`npm audit --omit=dev --audit-level=moderate`（0 vulnerabilities）；`npm run build`（既有 `vendor-antd` 大 chunk 提示归 `DEFER-003`）。
- 提交后 changed-mode T-GATE：真实性门禁扫描 12 个文件、配置边界门禁扫描 12 个文件、迁移规约门禁扫描 5 个 SQL，均无阻断项；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check HEAD~1..HEAD` 通过。

### PR2 阶段证据（R5 + 能力声明 + TERM 字典映射，不代表整卡完成）
- 新增 `FhirR5CanonicalMapper` 与 `FhirCanonicalMapperSupport`：R4/R5 复用同一套 Patient 出站与 Observation 入站确定性映射，不复制第二套临床模型；R5 Patient 带 R5 profile 元数据，Observation 入站写 `FHIR_R5:Observation`。
- 新增 `FhirCapabilityStatementService`：只声明 PR2 已落地的映射能力面（Patient 出站、Observation 入站），不声明未开放的 unsafe create；运行 read/search/create、医师确认链、INTEG-01 总线和安全边界留 PR3。
- 新增 `TerminologyMappingPortAdapter`：使用 `standard_term` ACTIVE 和 `term_mapping` CONFIRMED 判断编码映射状态，返回 `VALID` / `PARTIAL` / `UNKNOWN`；FHIR Observation 的本地编码经 TERM-01 端口评估，未映射返回 `OperationOutcome` warning，禁止字符近似兜底。
- 新增 `FhirOperationOutcomeFactory`：从 issue 列表生成 FHIR JSON `OperationOutcome`，用于未映射 / 不支持项的诚实响应。
- 本地红绿证据：`mvn -q -Dtest=FhirR4CanonicalMapperTest,FhirR5CanonicalMapperTest,FhirCapabilityStatementServiceTest,FhirOperationOutcomeFactoryTest,TerminologyMappingPortAdapterTest test` 先红灯于缺少 PR2 生产类型与仓储查询方法，补实现后退出码 0。
- 聚焦回归：`FhirR4CanonicalMapperTest`、`FhirR5CanonicalMapperTest`、`TerminologyMappingPortAdapterTest`、`ContextSnapshotServiceTest` 等真实映射与上下文测试通过。
- 后端全量：`mvn -q test`（Surefire XML 汇总 180 files / 1065 tests / 0 failures / 0 errors / 0 skipped；H2 / PostgreSQL 15.18 / Oracle 21.3 均验证 63 个迁移、应用到 v63 且二次 migrate no-op）。
- 前端验证：首次 `npm run verify` 因新 worktree 缺 `node_modules` 停在 `eslint: command not found`，经 `npm ci` 恢复依赖后重跑通过（44 files / 236 tests；既有 React Router / act 噪声归 `DEFER-003`）；`npm audit --omit=dev --audit-level=moderate` 0 vulnerabilities；`npm run build` 通过（既有 `vendor-antd` 大 chunk 提示归 `DEFER-003`）。
- 提交后 changed-mode T-GATE：真实性门禁扫描 10 个文件、配置边界门禁扫描 10 个文件、迁移规约门禁扫描 0 个 SQL，均无阻断项；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check HEAD~1..HEAD` 通过。

### PR3 阶段证据（受控 create + 总线 + 安全边界，不代表整卡完成）
- 新增 `FhirFacadeController` / `FhirFacadeService`：FHIR 运行端点挂 `/api/v1/engine/integration/fhir`，`GET {version}/metadata` 返回运行 CapabilityStatement，`POST {version}/{resourceType}` 接收原始 FHIR JSON resource；HTTP 入参使用 `FhirFacadeCreateRequest` Record DTO + Bean Validation，保持 BASE-03 契约，不暴露裸 `JsonNode` 请求体。
- 安全边界：FHIR create 读取租户内 `IntegrationAdapter` 配置，要求 `X-MedKernel-Fhir-Adapter`、`X-MedKernel-Timestamp`、`X-MedKernel-Signature`；适配器仅保存 `fhir.signatureWebhookId` 密钥引用，实际 HMAC-SHA256 密钥由既有 Webhook 安全配置仓储提供；可配置来源 IP 白名单和默认 packageVersion；缺适配器 / 断连 / 签名错误 / 白名单不匹配均返回 FHIR `OperationOutcome`，不写假成功。
- Observation create：R4/R5 确定性映射到 `CanonicalResource`，登记 `mk_fhir_resource_mapping`，经 `ClinicalEventService.receiveAsync` 回流引擎，并通过 `IntegrationService.enqueueOutboundMessage` 登记 INTEG-01 异步补偿；当前无真实连接器时保留 `NOT_CONNECTED` 证据。
- 高风险 create：MedicationRequest / ServiceRequest 不直写医嘱 / 病历，不自动生成临床结论，只创建 `FHIR_PHYSICIAN_CONFIRMATION` 运行任务等待医师确认。
- 契约同步：`ServiceContractCatalog` 新增 `fhir-facade` 服务契约；`docs/contracts/integration` 从“FHIR 待交付”改为真实运行门面，并由 `IntegrationContractDocumentationTest` 反射比对控制器端点。
- 本地红绿证据：新增 PR3 测试先红灯于缺少 `FhirFacadeService`、`FhirFacadeResponse`、`FhirFacadeCreateCommand` 与运行 CapabilityStatement；实现后 `mvn -q -Dtest=FhirCapabilityStatementServiceTest,FhirFacadeServiceTest,FhirFacadeControllerSecurityTest test` 退出码 0。
- 聚焦回归：`mvn -q -Dtest=FhirR4CanonicalMapperTest,FhirR5CanonicalMapperTest,FhirCapabilityStatementServiceTest,FhirOperationOutcomeFactoryTest,FhirResourceMappingRepositoryTest,FhirFacadeServiceTest,FhirFacadeControllerSecurityTest,TerminologyMappingPortAdapterTest,IntegrationServiceTest,IntegrationControllerSecurityTest,ClinicalEventServiceTest,ClinicalEventControllerSecurityTest,RuntimeTaskServiceTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest test` 退出码 0。
- 后端全量：`mvn -q test`（Surefire XML 汇总 182 files / 1075 tests / 0 failures / 0 errors / 0 skipped；H2 / PostgreSQL / Oracle 21.3 均验证 63 个迁移、应用到 v63 且二次 migrate no-op）。首次全量曾被 `ApiContractGovernanceTest` 抓到裸 `JsonNode` 请求体，已改为 Record DTO 并重跑通过。
- 前端验证：新 worktree 先 `npm ci` 恢复依赖；`npm run verify` 通过（44 files / 236 tests；既有 React Router / act 噪声归 `DEFER-003`）；`npm audit --omit=dev --audit-level=moderate` 0 vulnerabilities；`npm run build` 通过（既有 `vendor-antd` 大 chunk 提示归 `DEFER-003`）。
- 提交后 changed-mode T-GATE：真实性门禁、配置边界门禁、迁移规约门禁、中文注释门禁与 `git diff --check HEAD~1..HEAD` 均已通过。
- PR3 当时剩余的 10 类资源 read/search/create、Patient / Condition / Encounter 等 read/search 运行端点与 Bundle 响应，已由 PR4 阶段收口；PR3 本身不冒领整卡完成。

### PR4 阶段证据（10 类 read/search/create，全卡 FR/AC 收口）
- 新增 FHIR read/search 运行端点：`GET /api/v1/engine/integration/fhir/{version}/{resourceType}/{id}` 通过 `mk_fhir_resource_mapping` 定位 `CanonicalResource` 并输出对应 FHIR resource；`GET /api/v1/engine/integration/fhir/{version}/{resourceType}` 返回 FHIR `Bundle` searchset；读 / 搜只做映射读取，不重放临床事件。
- 扩展 R4/R5 共用映射支持：Patient / Encounter / Condition / Observation / Medication / Procedure / CarePlan / DiagnosticReport / DocumentReference 可在同一 `FhirCanonicalMapperSupport` 内双向映射；ServiceRequest read/search 复用已登记映射并以医师确认边界处理 create；不新增第二份临床模型。
- 扩展受控 create：Patient / Encounter / Condition / Observation / Medication / Procedure / CarePlan / DiagnosticReport / DocumentReference 均落 `CanonicalResource`、登记 FHIR 映射证据、回流 `ClinicalEventService` 并交 INTEG-01 总线补偿；ServiceRequest / MedicationRequest 仍只创建 `FHIR_PHYSICIAN_CONFIRMATION` 任务，不自动写医嘱 / 申请单 / 病历。
- 契约同步：`FhirCapabilityStatementService` runtime 版本更新为 `OPT-01-PR4`，声明 10 类核心资源 read/search/create 与高风险 ServiceRequest 医师确认；`docs/contracts/integration` 更新 read/search/create runtime paths、`Bundle` 响应与 supportedCreates，并由 `IntegrationContractDocumentationTest` 反射比对控制器端点。
- 本地红绿证据：PR4 新增测试先红灯于缺少 `FhirFacadeReadCommand`、`FhirFacadeSearchCommand` 和映射仓储 search 方法；补实现后 `mvn -q -Dtest=FhirCapabilityStatementServiceTest,FhirCanonicalMapperPr4Test,FhirFacadeServiceTest,IntegrationContractDocumentationTest test` 退出码 0。随后新增 10 类标准 create、Patient/Condition/Observation read 和未映射 Condition 编码 warning 覆盖并重跑同一目标套件退出码 0。
- 后端全量：`mvn -q test` 退出码 0（Surefire XML 汇总 183 files / 1082 tests / 0 failures / 0 errors / 0 skipped；H2 / PostgreSQL 15.18 / Oracle 21.3 均验证 63 个迁移、应用到 v63 且二次 migrate no-op）。
- 前端验证：新 worktree 初次 `npm run verify` 因缺 `node_modules` 停在 `eslint: command not found`，经 `npm ci` 恢复依赖后 `npm run verify` 退出码 0（44 files / 238 tests；既有 React Router / act 噪声归 `DEFER-003`）；`npm audit --omit=dev --audit-level=moderate` 0 vulnerabilities；`npm run build` 退出码 0（既有 `vendor-antd` 大 chunk 提示归 `DEFER-003`）。
- 提交后 T-GATE：门禁脚本自测 `node --test scripts/authenticity-guard.test.mjs scripts/config-boundary-guard.test.mjs scripts/migration-convention-guard.test.mjs` 34/34 通过；changed-mode 真实性门禁扫描 10 个文件、配置边界门禁扫描 10 个文件、迁移规约门禁扫描 0 个 SQL，均无阻断项；`scripts/check-comment-zh.sh` 0 fail / 0 warn；`git diff --check HEAD~1..HEAD` 通过。

## 大卡工序（6d，后端为主；按 PR 拆分）
- PR1：FHIR 资源映射数据模型 + `mk_fhir_mapping_rule` + 5 方言迁移 + Canonical↔FHIR 映射层（R4）→ AC-1 地基。
- PR2：R5 双版本 + CapabilityStatement + 编码经 TERM 字典映射 + 未映射诚实 `OperationOutcome` → AC-2/4。
- PR3：受控 create 回流引擎（不绕引擎 + 医师确认）+ 门面挂 INTEG-01 总线 + 断连 `NOT_CONNECTED` + 安全（签名/白名单/脱敏）→ AC-3/5。
- PR4：10 类资源 read/search/create 运行覆盖、FHIR `Bundle` 响应、剩余资源映射与整卡 AC-1/2/4/FR-1/2 补齐。
