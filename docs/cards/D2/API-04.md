# API-04 · 字典映射 API

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 §1.5.2 当前/后续 API 清单·字典行（L185）· 落地规划 §7.2 映射闭环（L408）· 核心 §1.4 统一入参 / 铁律 #2 高危不自动。

## 身份
- 卡 ID：API-04（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S4 字典映射（客户面 API）
- 依赖卡：[TERM-01](TERM-01.md)（字典映射引擎 + 高危判别）· [SYS-04](SYS-04.md)（映射包发布）· [BASE-03](../D0/BASE-03.md)（API 契约）· [API-13](../D0/API-13.md)（大列表）· [OBS-01](../D0/OBS-01.md)（traceId）
- 工作量：3d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供字典映射的**统一 REST 客户面**：标准/院内字典查询 · 候选生成 · 冲突列举 · 高危标注 · 映射包发布（灰度/全量/回滚）。本卡只立 **API 契约**，能力在 [TERM-01](TERM-01.md)、发布在 [SYS-04](SYS-04.md)。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/terminology` **控制器已建**，本卡＝**契约化 + 高危返回 + 统一入参**：
- 已有：`TerminologyController`(+ 安全测试)、`TerminologyService`、`TerminologyRequests`/`Filters`/`Enums`、`MappingCandidate`/`MappingConflict`/`TermMappingPackage(Release)` 各 Repository。
- 已补：① `POST /mappings/candidates` 返回 `semanticMatchScore` + `highRiskFlag`，且确定性 B0 候选来源为 `RULE`；② 高危候选批量确认拒绝 `MAPPING_HIGH_RISK_BATCH_DENIED`，批量重复候选 ID 只确认一次，逐条确认缺二次确认拒绝 `MAPPING_HIGH_RISK_AUTOCONFIRM_DENIED`；③ 映射包路由对齐 `/mapping-packages/**`；④ 写接口补 12 字段统一入参与租户一致性校验；⑤ 清理旧 `auto-recommend` / `standard-terms` / `local-terms` / `packages/**` / `candidates/{id}/confirm` 客户面入口。
- 未在本卡伪造：真实 10 万级标准 / 院内字典压测归 [API-13](../D0/API-13.md) / [SYS-07](../ga/SYS-07.md) / GA 总验收关闭，登记 `DEFER-010`。

## 功能要求（原子可测条目）
- [x] **FR-1 字典查询**：`GET /terms/standard`（ICD-10/ICD-9-CM-3/药品本位码/LOINC）、`GET /terms/local`，分页/筛选走 [API-13](../D0/API-13.md)。
- [x] **FR-2 候选 + 高危**：`POST /mappings/candidates` 返回候选 + `SemanticMatchScore` + `HighRiskFlag`；高危候选**禁批量确认**（`MAPPING_HIGH_RISK_BATCH_DENIED`）。
- [x] **FR-3 映射确认/冲突**：`POST /mappings/{id}/confirm`（高危逐条 + 二次确认）、`GET /mappings/conflicts`。
- [x] **FR-4 映射包发布**：`POST /mapping-packages/{id}/publish`（草稿→审核→灰度→全量→回滚，委托 [SYS-04](SYS-04.md)）。
- [x] **FR-5 统一入参/信封**：12 字段统一入参 + `ApiResult`/`ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/terminology/**`（terms、mappings、candidates、conflicts、mapping-packages）。
- DTO：复用 `TerminologyRequests`/`Filters` + `MappingCandidate`/`MappingConflict`/`TermMappingPackage`；新增 `HighRiskFlag` 返回字段。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）；大列表 `PageResult`（[API-13](../D0/API-13.md)）。
- 状态机：映射包走核心 §3 配置类 + 变更类（[SYS-04](SYS-04.md)）。
- 幂等 / 错误码 / traceId：确认幂等键；高危批量/自动 → `MAPPING_HIGH_RISK_BATCH_DENIED`/`..._AUTOCONFIRM_DENIED`；全链路 traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡无页面。被 **D2 字典映射页**消费（高危红标 + 禁批量 UI）。

## 数据与迁移
- 无独立表族——复用 [TERM-01](TERM-01.md) 表族。不落新库（API 契约卡）。

## 视角清单（11 视角逐条）
1. **产品架构**：字典能力统一对外契约口。
2. **产品体验**：N·A —— D2 字典映射页消费；高危禁批量在 API 层兜底。
3. **系统与数据架构**：统一入参/信封/分页；大字典 10 万级稳定（[API-13](../D0/API-13.md)）。
4. **临床医疗安全**：★API 层兜底高危禁批量/禁自动确认（即便前端绕过也拒，铁律 #2）。
5. **知识与数据治理**：映射版本化发布、可溯（[SYS-04](SYS-04.md)）。
6. **安全合规与监管**：确认/发布留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：按 `OrgContext` 作用域；标准字典集团统一、院内本地。
8. **集成与互操作**：外部系统经本 API 取标准码归一。
9. **运维 / SRE / 国产化**：药品本位码一等支持；离线字典包。
10. **质量与真实性审计**：无伪造候选分/确认；高危禁批量 API 层校验（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：候选含 AI 分（第二波）时仍标 B0/置信；关模型退回确定性候选，端点不变。

## 适用不变量
- 命中核心约束：**§1.4 统一入参** · **铁律 #2 高危不批量/不自动** · **依赖 [TERM-01](TERM-01.md)/[SYS-04](SYS-04.md)/[API-13](../D0/API-13.md)**。
- 本卡落点：字典能力以统一契约对外，高危安全约束在 API 层再兜一层。

## 验收 + 验证
- [x] **AC-1（FR-2）**：请求高危候选批量确认 → API 返回 `MAPPING_HIGH_RISK_BATCH_DENIED`（即便前端发批量）。
- [x] **AC-2（FR-1/3）**：标准/院内字典分页查询稳定；冲突列举返回待裁项。真实 10 万级压测不在本卡伪造，归 `DEFER-010` 在 [SYS-07](../ga/SYS-07.md) / GA 验收关闭。
- [x] **AC-3（FR-4）**：映射包发布灰度→全量→回滚经 [SYS-04](SYS-04.md)，状态机正确。
- [x] **AC-4（FR-5）**：缺统一入参 → `ProblemDetail`；越权访问 → 0 + 审计。
- 关联 A1–A9 剧本：A3 字典映射。
- T-GATE：前后端真实性门禁全绿。
- B0 验收：确定性候选 + 人工确认，**天然 B0**。

## 完工证据
- 代码 permalink：`/api/v1/engine/terminology/**` 端点 + `HighRiskFlag` 返回 + 禁批量校验 + 发布接 [SYS-04](SYS-04.md)。
- 本地验证：`TerminologyApiContractTest`、`TerminologyServiceTest`、`TerminologyControllerSecurityTest`、`EngineEndToEndIntegrationTest`、`ServiceContractGovernanceTest`、`OpenApiContractConfigurationTest` 聚焦通过；`FlywayMultiDialectSmokeTest` 覆盖 H2 / PostgreSQL / Oracle 迁移至 V48；后端全量 `mvn -q test` 通过；T-GATE 规则测试 34/34；真实性全仓扫描通过；中文注释扫描仅剩 `DEFER-006` 历史迁移 COMMENT GAP。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
