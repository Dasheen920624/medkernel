# EMR-LEVEL-02 · 评级数据质量和证据包

> 读卡前置：[核心 CONSTITUTION](../../CONSTITUTION.md) + [D4 域简报](_brief.md)。
> 迁移来源（覆盖矩阵锚点）：详规 S23 电子病历评级支撑 · 详规 数据质量与证据 · 合规监管。

## 身份
- 卡 ID：EMR-LEVEL-02（治理/契约卡；评级证据包单一归属）
- 域：D4 质控改进
- 关联场景：S23 电子病历评级支撑
- 依赖卡：[EMR-LEVEL-01](EMR-LEVEL-01.md) 评级目标 · [EVAL-01](EVAL-01.md) 评估 · [BASE-04](../D0/BASE-04.md) 审计 · [CDSS-01](../D3/CDSS-01.md) 闭环证据
- 工作量：4d
- owner / reviewer：待派单（owner ≠ reviewer）

## 目标
为电子病历评级产出**数据质量与证据包**：应用覆盖率、数据质量指标、CDSS/质控闭环证据、审计证据，**可一键导出、证据真实可追溯**，支撑评审。

## 现状（2026-06-05，以 `medkernel-backend` 为准）
后端 B0 已建：`com.medkernel.engine.emrlevel` 在 [EMR-LEVEL-01](EMR-LEVEL-01.md) 的目标/标准项/差距基础上新增数据质量聚合与证据包导出；证据来源限定为关系库中的评级项、差距、质控问题、整改任务、CDSS 推荐采纳反馈与审计事件，缺证据按 `MISSING_EVIDENCE` / `MISSING` 标记，不拼造证据。V87 五方言新增 `mk_emr_level_evidence_package`，记录幂等键、证据行数、SHA-256、NDJSON 载荷、trace 与审计字段。

## 功能要求（原子可测条目）
- [x] FR-1 应用覆盖：统计各评级标准项的系统应用覆盖率，真实。
- [x] FR-2 数据质量：数据质量指标（完整性/及时性/一致性）真实计算。
- [x] FR-3 闭环证据：CDSS/质控闭环证据（采纳、整改闭环）可追溯。
- [x] FR-4 证据包导出：按评级标准项组织证据包，一键导出（带 traceId/审计链）。
- [x] FR-5 证据真实：证据来自真实运行/审计，不拼造、缺项标缺失。

## 接口契约 / 页面契约
### 接口契约（引擎/API 卡）
- 端点：`GET /api/v1/engine/emr-level/data-quality` · `POST /api/v1/engine/emr-level/evidence-package:export`
- DTO：`EmrLevelDataQualityResponse` / `EmrLevelEvidencePackageExportRequest` / `EmrLevelEvidencePackageExportResponse` 等 Record；信封 `ApiResult`/`ProblemDetail`
- 状态机：N·A（只读聚合 + 导出任务）；trace（[OBS-01](../D0/OBS-01.md)）
- 幂等 / 导出：导出按 `hospitalOrgId + standardVersion + idempotencyKey` 幂等；重复请求返回同一 `packageId` / `payloadSha256`，不重复发布审计。

## 数据与迁移
- 复用 `mk_emr_level_*`、`quality_finding`、`rectification_task`、`recommendation_card`、`recommendation_feedback`、`audit_event` 表族；V87 五方言新增 `mk_emr_level_evidence_package`（证据包幂等、载荷摘要、NDJSON、trace、审计字段）。

## 视角清单（11 视角逐条）
1. 产品架构：评级评审的"证据出厂口"。
2. 产品体验：证据包导出进度可见（驾驶舱 [QCDASH-01](QCDASH-01.md)）。
3. 系统与数据架构：证据聚合大数据量；导出异步；可重出。
4. 临床医疗安全：闭环证据真实反映安全质控成效。
5. 知识与数据治理：★证据可追溯版本与 traceId、不拼造。
6. 安全合规与监管：★证据包作评审/监管材料、全审计链（[BASE-04](../D0/BASE-04.md)）。
7. 集团化与多租户治理：按院作用域导出。
8. 集成与互操作：证据可导出标准格式供评审。
9. 运维 / SRE / 国产化：导出可观测、可重试；国产化离线导出。
10. 质量与真实性审计：★数据质量/覆盖真实计算、证据缺项标缺失、不拼造。
11. AI / 模型治理与可降级：N·A（确定性证据）。

## 适用不变量
- 命中核心约束：**铁律 #1 真实性（证据不拼造）** · **合规监管** · **核心 §13 可追溯**。
- 本卡落点：评级数据质量与证据包，证据真实可追溯可导出。

## 验收 + 验证
- [x] AC-1（FR-1/2）：覆盖/数据质量真实计算。
- [x] AC-2（FR-3/4）：闭环证据可追溯；证据包可导出带审计链。
- [x] AC-3（FR-5）：证据缺项标缺失、不拼造。
- 关联 A1–A9 剧本：A9 评级证据。
- T-GATE：后端真实性门禁全绿（证据真实/缺项标记）。
- B0 验收：证据聚合确定性、与模型无关。

## 完工证据
- 代码 permalink：`EmrLevelController` / `EmrLevelService` / `EmrLevelDataQuality*Response` / `EmrLevelEvidencePackage*` / V87 五方言迁移。
- 测试：`EmrLevelServiceTest`、`EmrLevelControllerSecurityTest`、迁移基线与服务契约治理测试覆盖覆盖率/完整性/及时性/一致性、CDSS 采纳、整改闭环、审计证据、幂等导出、缺项标记。
- 本地验证：TDD 红灯为缺 DTO/方法编译失败；聚焦组合 `mvn -q -Dtest=EmrLevelServiceTest,EmrLevelControllerSecurityTest,MigrationBaselineContractTest,H2BaselineMigrationTest,FlywayMultiDialectSmokeTest,ServiceContractGovernanceTest,DomainOwnershipContractTest test` 已通过；后端全量 `mvn -q test` 已通过（含 H2/PostgreSQL/Oracle 迁移 smoke 至 v87 且重复 migrate no-op）；前端 `npm run verify`（61 files / 375 tests）与 `npm run build` 已通过；changed T-GATE 已通过（真实性 9 文件、迁移规约 5 文件、配置边界 9 文件、中文注释 0 fail/0 warn、空白检查通过）。
- 审计员签字：@<reviewer>（owner ≠ reviewer）。
