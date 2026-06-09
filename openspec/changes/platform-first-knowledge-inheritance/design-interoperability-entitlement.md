# 附录 I — 互操作导入导出 / 第三方 API 契约 / 授权许可

> 目标：平台知识可与外部标准互通（导入权威指南、导出共享）；第三方按规范接入；商业/受限内容按授权可用。

## I1 标准互操作（导入 / 导出）
平台资产 SHALL 支持与开放标准双向转换（落地分阶段）：
- **CDS Hooks**：规则/建议卡片对接 `engine/cdshook`，对外提供标准 hook 服务。
- **FHIR PlanDefinition / ActivityDefinition**：路径/医嘱集导入导出（复用 `integration/fhir`、`FhirVersion`）。
- **CQL**：可计算规则逻辑的导入（受控映射到内部 DSL，保留可重放）。
- **术语标准**：ICD-10/ICD-9-CM-3/ATC/LOINC/SNOMED CT 编码系统对接（terminology 域已绑定）。
- **openEHR/模板**（可选，二期）。
导入内容入平台前走质量门（附录 L5）；导出携带 `content_hash` 与溯源。

## I2 第三方接入 API 契约（呼应"第三方按规范传入"）
对外稳定契约面（版本化、向后兼容）：
- **有效解析查询 API**：给定机构 + 专病/场景维度 + 时刻，返回有效规则/路径/字典/字段集（只读，带 sourceTier/content_hash）。
- **上下文写入 API**：第三方/院内系统按 **平台标准字段目录 + 标准字典** 传入患者上下文（canonical 12 资源）；院内编码经 `TermMapping` 归一到标准（对照覆盖已建）。
- **覆盖管理 API**：租户/机构在自身闭包内增删改覆盖（REPLACE/DISABLE/ADD + 传播）。
- **包分发 API**：拉取有效包快照、对账（SyncTarget/离线）。
- 契约纳入 `ServiceContractCatalog` 治理；产出对外接口文档（OpenAPI），含字段目录与字典对照规范。

## I3 院内字典 ↔ 平台标准对照（已部分落地，纳入统一）
- 平台标准字典为权威；院内 `LocalTerm` + `TermMapping` 为租户覆盖层（附录 O 维度/组织覆盖之一）。
- 对照覆盖度分析（`MappingCoverage`，本轮已建）作为接入就绪度门禁：未对照编码 → 接入告警 + 运行期诚实降级。

## I4 授权与许可（entitlement）
- 部分平台包可能受商业许可（外部指南内容）；引入 **entitlement 层**：租户对平台包的可用性受授权控制。
- 解析/分发前校验 entitlement；无授权的平台包对该租户不可见、不下发。
- 授权变更（开通/到期）走审计；到期降级为只读历史或不可解析（诚实降级，非静默消失）。

## I5 落地对接
- 互操作：`integration/fhir`、`cdshook` 扩展导入导出适配；CQL/PlanDefinition 转换器为独立模块。
- API 契约：各域 Controller 纳入 `ServiceContractCatalog`，统一鉴权（platform/tenant 权限分离，附录 S6）+ 审计。
- entitlement：新增 `mk_pkg_package_entitlement`（tenant_id, package_identity, granted/expires），解析与分发前置校验。
