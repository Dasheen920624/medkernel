# SVC-PILOT-02 · 接入与数据质量服务包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D2 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：落地规划 §11.3 院内系统对接（L741，消费）· 详规 §S2 院内系统接入（L489）· §6 病历内涵质控·数据质量（间接）· FOUNDATION §3.1 第三方对接能力（L95）· 核心 §10 集成边界。

## 身份
- 卡 ID：SVC-PILOT-02（= backlog 任务 ID）
- 域：D2 试点准备
- 关联场景：S2 院内系统接入（数据质量侧）
- 依赖卡：[INTEG-01](INTEG-01.md)（对接总线）· [API-01](API-01.md)（标准上下文）· [TERM-01](TERM-01.md)（字段/编码归一）· [BASE-04](../D0/BASE-04.md)（数据质量审计）
- 工作量：5d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
提供**院内系统接入 + 数据质量**服务包：HIS/EMR/LIS/PACS/医保/病案/随访适配编排（AdapterHub）+ 字段映射 + **患者主索引（MPI）匹配/合并/去重** + 数据质量核查。让试点医院的真实数据**可信地**流入标准上下文，供 D3 临床消费。本卡编排 [INTEG-01](INTEG-01.md) + `engine/mpi`，为 [ADAPTER-01](ADAPTER-01.md) 适配器中心页供服务。

## 现状（搬迁时核查 2026-05-30，以 `medkernel-backend/src` 为准）
`engine/integration` + `engine/mpi` **已建**，本卡＝**接入编排 + 数据质量核查补全**：
- 已有：`engine/integration`（`IntegrationAdapter`/`MessageLog`/`WebhookConfig` + `IntegrationService`，详见 [INTEG-01](INTEG-01.md)）；`engine/mpi`（`MpiPatient`、`MpiMergeRequest`、`MpiService`、`MpiStatsResponse`）。
- 缺口（本卡补）：① **AdapterHub** 编排多适配器接入向导；② **MPI 匹配/合并去重**（同患者跨系统归一，高危合并人工确认）；③ **数据质量核查**（必填率/编码映射率/时效，诚实暴露缺口）；④ 字段映射接 [TERM-01](TERM-01.md)。

## 功能要求（原子可测条目）
- [x] **FR-1 接入编排**：登记并启停 HIS/EMR/LIS/PACS/医保/病案/随访适配器（经 [INTEG-01](INTEG-01.md)）；向导式逐源接入 + 连通核查。
- [x] **FR-2 MPI 匹配/合并**：跨系统患者按规则匹配 `MpiPatient`；**高危合并（不同患者疑似同人）人工确认**，不自动合并。
- [x] **FR-3 字段映射**：外部字段 ↔ 标准上下文（[API-01](API-01.md)）+ 编码归一（[TERM-01](TERM-01.md)）。
- [x] **FR-4 数据质量核查**：核心字段必填率/编码映射率/时效统计（`MpiStatsResponse` 扩展），**缺口诚实暴露**，不伪造达标。
- [x] **FR-5 不阻断主流程**：接入/同步异常降级标记（`NOT_CONNECTED`/`NOT_SYNCED`），不阻断临床。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`/api/v1/engine/mpi/**` + 编排接口；复用 [INTEG-01](INTEG-01.md) `/integration/**`。
- DTO：复用 `MpiPatient`/`MpiMergeRequest`/`MpiStatsResponse`；新增 `DataQualityReport`（必填/映射/时效）· `AdapterHubStatus`。
- 响应信封：`ApiResult` / `ProblemDetail`（[BASE-03](../D0/BASE-03.md)）。
- 状态机：合并审核走核心 §3 待办态；适配器连通态（[INTEG-01](INTEG-01.md)）。
- 幂等 / 错误码 / traceId：合并按 `(mpi_id, candidate_id)` 幂等；高危自动合并 → `MPI_MERGE_REQUIRES_REVIEW`；traceId（[OBS-01](../D0/OBS-01.md)）。
### 页面契约（页面卡）
N·A —— 本卡为服务包后端。页面在 [ADAPTER-01](ADAPTER-01.md)（适配器中心，含数据质量看板）。

## 数据与迁移
- 表族（已有）：`mpi_patient`、`integration_adapter`/`message_log`；本卡补 `mk_integration_data_quality_report`/`mk_mpi_merge_review`。
- 主键 ULID；索引：`mpi_id`、`adapter_code`、`org_path`。
- 5 方言迁移一致 + 中文注释。

## 视角清单（11 视角逐条）
1. **产品架构**：真实数据"进得来、对得上、查得清"的服务包。
2. **产品体验**：N·A —— 适配器中心页（[ADAPTER-01](ADAPTER-01.md)）呈现接入/质量。
3. **系统与数据架构**：MPI 匹配确定性；数据质量统计真实；大数据量分页。
4. **临床医疗安全**：★高危患者合并人工确认（错合=张冠李戴）；数据质量缺口诚实暴露不误导临床。
5. **知识与数据治理**：编码经 [TERM-01](TERM-01.md) 归一可溯。
6. **安全合规与监管**：接入/合并/质量核查留审计（[BASE-04](../D0/BASE-04.md)）。
7. **集团化与多租户治理**：MPI 按租户隔离；集团级患者归一受控。
8. **集成与互操作**：★主战场 —— 多源适配编排（[INTEG-01](INTEG-01.md)）+ 字段映射 + 不阻断主流程（核心 §10）。
9. **运维 / SRE / 国产化**：5 方言；离线接入；国产 HIS/EMR 适配。
10. **质量与真实性审计**：数据质量真实统计、无伪造达标；高危合并不自动（铁律 #1/#2）。
11. **AI / 模型治理与可降级**：MPI 匹配 **B0＝规则**；AI 辅助匹配（第二波）只提候选、高危不自动合并，关模型退回规则匹配。

## 适用不变量
- 命中核心约束：**§10 集成边界 / 不阻断主流程** · **铁律 #2 高危合并不自动** · **§13 数据质量不伪造** · **依赖 [INTEG-01](INTEG-01.md)/[API-01](API-01.md)/[TERM-01](TERM-01.md)**。
- 本卡落点：把"接入 + 患者归一 + 数据质量"编排为可信、可核查、安全的服务包。

## 验收 + 验证
- [x] **AC-1（FR-1/3）**：向导式接入一源 + 字段映射 + 连通核查；断连 `NOT_CONNECTED`。
- [x] **AC-2（FR-2）**：高危疑似同人 → `MPI_MERGE_REQUIRES_REVIEW`，人工确认后合并。
- [x] **AC-3（FR-4）**：数据质量报告必填/映射/时效真实统计，缺口暴露不伪造。
- 关联 A1–A9 剧本：A1 接入、A6 合规（数据质量证据）。
- T-GATE：真实性门禁全绿。
- B0 验收：规则匹配 + 确定性核查，**天然 B0**。

## 完工证据
- 代码：`AdapterHubStatus` + `DataQualityReport` + `MpiMergeReview` / `MpiMergeResult` + `/api/v1/engine/mpi/**` 高危审核接口 + 5 方言 V64 迁移。
- 后端聚焦：`mvn -q -Dtest=MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,DomainOwnershipContractTest test` 通过；H2 / PostgreSQL 15.18 / Oracle 21.3 应用 64 版迁移并二次 no-op。
- 后端业务：`mvn -q -Dtest=MpiServiceTest,MpiServiceIntegrationTest,MpiControllerContractTest,IntegrationServiceTest,IntegrationControllerSecurityTest,ServiceContractGovernanceTest,OpenApiContractConfigurationTest,IntegrationContractDocumentationTest test` 通过；覆盖高危合并审核、人工确认、AdapterHub 状态、数据质量报告和契约文档。
- 后端全量：`mvn -q test` 通过；Surefire 汇总 `files=186 tests=1106 failures=0 errors=0 skipped=0`。
- 前端：`npm run verify` 通过（44 文件 / 242 测试）；`npm audit --omit=dev --audit-level=moderate` 0 漏洞；`npm run build` 通过，保留既有 `vendor-antd` chunk 提示，归 `DEFER-003`。
- T-GATE：脚本自测 34/34；真实性 inventory 扫描 889 文件 0 阻断；配置边界 inventory 扫描 831 文件 0 阻断；V64 五方言迁移规约 files 扫描 5 文件 0 阻断；提交后 changed-mode 真实性 21 文件 / 配置边界 18 文件 / 迁移 5 文件均 0 阻断；中文注释门禁 0 fail / 0 warn；`git diff --check origin/main...HEAD` 通过。
- 审计员签字：待 PR reviewer（owner ≠ reviewer）。
