# engine-data 异步导出后端 · 设计（DATASVC-01 PR2 续切片）

> 上游卡：[DATASVC-01](../../cards/wave2/DATASVC-01.md)（FR-1 异步导出 / FR-3 CLI 不绕审批 / FR-6 全审计）。
> 母规范：`docs/superpowers/specs/2026-05-26-engine-data-service-mcp-cli-clinical-design.md`（§7 数据分级、line 147 不绕导出审批、line 258 小样本阈值+导出审批共同控制、line 178 导出任务幂等键）。
> 现状：CLI `exports` 命令域当前诚实返回 `implemented:false`；后端无 engine-data 异步导出。本切片补全该缺口。

## 目标

把三组已建 **D2 去标识聚合**读模型（rule-usage / knowledge-usage / clinical-signals）经 **SYS-06 导出审批闸控制的异步 CSV 导出**对外开放，复用 `KnowledgeExportJob` 执行骨架，并把 SYS-06 审批产物来源从 `LargeListEngineService` 泛化以接纳 engine-data 作业。CLI `exports` 域接真实端点，缺口闭合。

**不做（诚实标）**：D3/D4 字段级加密落库（独立缺口，本切片三读模型均 D2 不落患者字段，不触发）；按科室/时间细分下钻。

## 决策（已与用户确认）

| 决策点 | 选定 | 理由 |
|---|---|---|
| 审批闸 | 复用 SYS-06（泛化 artifact provider） | 忠实卡 FR-3「不绕审批」+ 规范 line 258 + 验收矩阵「导出审批失败」用例 |
| 导出范围 | 三组 D2 读模型全纳入 | 一次补齐 FR-1 导出维度，exports 缺口彻底填上 |
| 权限 | 新增 `engine-data.export`（MEDIUM） | 导出比只读敏感，独立授质量/医保治理员；审批仍由 `audit.export` |
| 产物格式 | CSV + SM3 摘要 | 对齐 SYS-06 审批闸控的 LargeList 先例 + 分析师可消费 |
| 小样本抑制 | 纳入真实抑制（阈值 10） | 忠实规范 line 258；计数列 `<10`→`suppressed`+标志，保留分组键 |
| 产物命名 | `LargeListExportArtifact`→中性 `ExportArtifact` + `ExportArtifactProvider` | 内部投影改名安全；消除 engine-data 复用「LargeList」命名异味 |
| 导出状态枚举 | datasvc 本地 `ExportJobStatus` | 保持域边界，不耦合 datasvc→knowledge |

## 架构与数据流

```
[质量/医保治理员 engine-data.export]            [合规/审计员 audit.export]
        │                                                │
   ① 提交导出作业 ──须先有──▶ ② SYS-06 审批（申请→复核，申请人≠审批人）
        │  POST /api/v1/engine-data/exports             │  /compliance/exports:request → :approve
        │  （校验 approvalId=APPROVED + resourceType/范围匹配，否则结构化拒=「导出审批失败」）
        ▼
   PENDING ─事务提交后投递线程池 worker─▶ RUNNING ─查读模型(分页 500)+小样本抑制+写CSV─▶ SUCCEEDED
        │                                                                       result_uri + item_count
        ▼ 下载 GET /api/v1/engine-data/exports/{jobCode}/download (engine-data.export)
   ③ 登记完成 POST /api/v1/compliance/exports/{approvalId}:complete-from-job (audit.export)
        └─ ExportApprovalService 按 resourceType 解析 EngineData provider → 校验产物一致 → 算 SM3 → EXPORTED + 证据
```

**B0/真实性**：纯读已建 D2 读模型（关模型可跑）；无 APPROVED 审批不出文件；空上游诚实空 CSV 不伪造；SM3 摘要按真实文件字节算（铁律 #1/#10）。

## 组件（新建 `com.medkernel.engine.datasvc.export`）

- `EngineDataExportType`（枚举 `RULE_USAGE`/`KNOWLEDGE_USAGE`/`CLINICAL_SIGNALS`）：每型携 resourceType 串（`engine_data_rule_usage` 等）+ CSV 表头/抽取器路由。
- `ExportJobStatus`（枚举 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED/EXPIRED`，`isTerminal()`）。
- `EngineDataExportJob`（record，`@Table("engine_data_export_job")`）+ `EngineDataExportJobRepository`（Spring Data JDBC，按 tenant+jobCode 查、近 100 列表）。
- `EngineDataExportService`：
  - `submit(type, filters, approvalId, idempotencyKey)`：校验 SYS-06 审批 APPROVED + resourceType 匹配 + request_snapshot==审批 scope；写 PENDING；事务提交后投递 worker（`RequestContext.snapshot` 恢复租户上下文，同 KnowledgeExportService）。
  - `get/listRecent/cancel`：同骨架。
  - `executeJob(jobCode)`（worker）：PENDING→RUNNING→查读模型分页+抑制+写 CSV→SUCCEEDED（result_uri/item_count/expires_at TTL 7d）；异常→FAILED + 诚实 error_message。
  - `downloadFile(jobCode)`：仅 SUCCEEDED 可下；文件缺失结构化 404。
  - `completedExportArtifact(jobCode)`（实现 `ExportArtifactProvider`）：仅 SUCCEEDED；按真实 CSV 文件算 `sm3:` 摘要；返回 `ExportArtifact`(jobId/resourceType/requestSnapshot/idempotencyKey/downloadUri/exportDigest)。
- `EngineDataExportAsyncConfig`：`engineDataExportExecutor` 线程池（同 `KnowledgeExportAsyncConfig` 形态）。
- CSV 写：UTF-8（含 BOM 便于 Excel 中文）、中文表头；分页批 500 拉既有读模型仓储；逐行写。
- 小样本抑制：行主计数 `<10` → 计数列写 `suppressed`、加 `suppressed` 列=`true`，分组键保留。

## SYS-06 审批产物来源泛化（跨域改造）

- 抽 `ExportArtifactProvider`：`boolean supports(String resourceType)` + `ExportArtifact completedExportArtifact(String jobId)`（中性包，建议 `com.medkernel.compliance.exportapproval`）。
- `LargeListExportArtifact` record 改名 `ExportArtifact`（内部投影，非 API 序列化字段，改名安全）；更新 `LargeListEngineService`、`ExportApprovalService` 引用。
- `LargeListEngineService implements ExportArtifactProvider`（`supports` 归一后匹配 `audit_event`/`terminology_mapping`）。
- `EngineDataExportService implements ExportArtifactProvider`（`supports` 匹配 `engine_data_*`）。
- `ExportApprovalService` 构造注入 `List<ExportArtifactProvider>`，`completeExportFromJob` 按 `current.resourceType()` 解析唯一 provider（零/多匹配 → 结构化错）。审批流端点/状态机/证据/权限 `audit.export` **全不变**。

## 数据模型 + 迁移（V128，5 方言）

`engine_data_export_job`：`id`/`tenant_id`/`job_code`(UUID 对外)/`requested_by`/`export_type`/`status`/`progress`/`result_uri`/`item_count`/`error_message`/`created_at`/`started_at`/`completed_at`/`expires_at` + 审批锚 `approval_id`/`idempotency_key`/`request_snapshot`。
- 5 方言（h2/postgres/oracle/dm/kingbase）一致 + 生产方言中文 `COMMENT ON` + 索引（`tenant_id,job_code` 唯一；`tenant_id,created_at`）。
- `MigrationBaselineContractTest`：EXPECTED_MIGRATIONS+V128、REQUIRED_TABLES/INDEXES、MUTABLE_AUDITED_TABLES/LIFECYCLE_FIELDS（按表性质）、两 `LATEST_MIGRATION_VERSION`(smoke/h2baseline) 127→128。

## 端点（`EngineDataController`，均 `engine-data.export`）

- `POST /api/v1/engine-data/exports`（提交，体 exportType/filters/approvalId/idempotencyKey，幂等键）
- `GET /api/v1/engine-data/exports/{jobCode}`（状态）
- `GET /api/v1/engine-data/exports`（近期列表）
- `POST /api/v1/engine-data/exports/{jobCode}/cancel`（取消）
- `GET /api/v1/engine-data/exports/{jobCode}/download`（下载 CSV）

契约 `engine-data` 补声明 `engine_data_export_job` 审计点；产品功能目录重生成。

## CLI 接线（`cli/src/commands.mjs`）

`exports` 域替换诚实缺口桩为真实动作：`submit`（exportType/filters/approvalId/idempotencyKey）/`status <jobCode>`/`list`/`download <jobCode>`/`complete <approvalId> <jobCode>`（打 compliance 端点，perm 服务端管）。CLI 仍不直连库、不绕审批；后端不可达诚实报错。补 `cli/test` 用例。

## 权限

- `PermissionCode` 加 `ENGINE_DATA_EXPORT("engine-data.export", Risk.MEDIUM, "提交/下载引擎数据服务层异步导出（D2 去标识聚合，审批闸控、字段脱敏、小样本抑制）")`。
- `DefaultPermissionPolicy`：授**质量治理员 + 医保治理员**（同 `engine-data.read` 归属）；经 `allNonEmergencyPermissions` 自动并入超管/平台治理/机构管理员；临床决策用户无。
- `DefaultPermissionPolicyTest`：精确授权断言（镜像 `engineDataReadRestsWithManagementAndQualityRoles`）+ 快照/不变量两处。

## 错误处理 + 降级

- 审批失败：无 approvalId / 非 APPROVED / resourceType 或 scope 不匹配 → 结构化 4xx + traceId。
- 上游读模型不可用 → 作业 FAILED + 诚实 error_message，不出半真文件。
- 文件 TTL 过期 → EXPIRED，可重发新作业。
- 越权：无 `engine-data.export` → 403；`申请人≠审批人` 由 SYS-06 守。

## 测试矩阵（TDD 红绿）

- `EngineDataExportServiceTest`：提交写 PENDING / 审批未过结构化拒 / resourceType 不匹配拒 / worker PENDING→SUCCEEDED 写文件 / 小样本抑制 / 上游降级 FAILED / 取消终态冲突 / completedExportArtifact 算真实 SM3。
- `EngineDataExportJobRepositoryIntegrationTest`（真实 H2）：保存/按 tenant+jobCode 查/近期列表/租户隔离。
- `EngineDataControllerSecurityTest`：5 导出端点 engine-data.export 正/负。
- `ExportApprovalServiceTest` 增量：按 resourceType 解析 engine-data provider 完成登记；多/零 provider 结构化错。
- `MigrationBaselineContractTest`：V128 相关全套。
- CLI `exports` node test：submit/status/list/complete 走 apiClient、缺参结构化、后端不可达诚实报错。

## 验收对照

- AC-1（导出部分）：规则/知识/临床信号真实异步导出、服务端分页、CSV 真实、SM3 真实；小样本抑制；D3/D4 字段级加密仍诚实标缺口（三读模型 D2 不落患者字段，不在本切片）。
- AC-2（FR-3）：CLI `exports` 走后端鉴权可用、不直连库、不绕审批。
- AC-3（FR-6/7）：导出提交/下载/完成全审计；审批失败/上游降级/TTL 过期各诚实降级不伪装。
- B0：关模型 + 无 CLI 时，REST 导出与读模型仍真实可运行。

## 验证门禁（PR 前本地全跑）

- 全量 `mvn test`（新增测试 + 既有 2409 基线不回归）。
- 四门禁：`authenticity-guard --mode=changed` / config-boundary / migration / 中文注释。
- `git diff --check`。
- 前端 `npx vitest run src/shared/config/productCatalog.test.ts`（新端点重生成目录）。
- CLI `node --test cli/test/*.test.mjs`。
